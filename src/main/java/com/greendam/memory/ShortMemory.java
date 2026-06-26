package com.greendam.memory;

import com.greendam.entity.Message;
import com.greendam.util.TokenCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 短期记忆（工作记忆） — 当前会话的完整对话历史.
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>存储当前会话中 user / assistant / tool / system 消息</li>
 *   <li>提供安全的只读访问接口</li>
 *   <li>自动管理上下文窗口：超出上限时对早期轮次进行 LLM 摘要压缩</li>
 * </ul>
 *
 * <h3>上下文窗口管理策略（摘要模式）</h3>
 * <p>当消息总 token 数接近 {@code maxTokens - reserveTokens} 时，
 * 保留最近 {@code keepTurns} 轮完整对话（含工具调用结果），
 * 对更早的轮次调用 LLM 生成<b>增量摘要</b>：
 *
 * <pre>
 * 摘要前:
 *   [system: 原始 prompt]
 *   [user: Q1] [assistant: A1+tool] [tool: result] [assistant: A1']
 *   [user: Q2] [assistant: A2]
 *   [user: Q3] [assistant: A3]    ← keepTurns=2, 保留 Q2-Q3，摘要 Q1
 *
 * 摘要后:
 *   [system: 原始 prompt]
 *   [system: 📋 历史对话摘要: ...]   ← Q1 的压缩结果
 *   [user: Q2] [assistant: A2]
 *   [user: Q3] [assistant: A3]
 * </pre>
 *
 * <p>后续触发摘要时，会将新轮次合并进已有摘要，实现增量压缩。
 * system 消息（包括摘要）永远不会被移除。
 *
 * <h3>线程安全</h3>
 * <p>当前为单线程设计（与 Agent Loop 一致）。
 */
public final class ShortMemory {

    private static final List<Message> SHORT_MEMORY = new ArrayList<>();

    /**
     * 上下文窗口最大 token 数（可从配置文件覆盖）
     */
    private static int maxTokens = 128000;

    /**
     * 预留给模型响应的 token 数
     */
    private static int reserveTokens = 8000;

    /**
     * 保留最近 N 轮完整对话不被摘要压缩.
     * 一轮 = 一条 user 消息 + 后续所有 assistant/tool 消息（直到下一条 user 消息前）.
     */
    private static int keepTurns = 3;

    private ShortMemory() {
    }

    // ==================== 基本 CRUD ====================

    /**
     * 追加一条消息到记忆末尾.
     */
    public static void add(Message message) {
        SHORT_MEMORY.add(message);
    }

    /**
     * 批量追加消息（用于追加工具调用结果等）.
     *
     * @param messages 消息列表，为 null 或空列表时无操作
     */
    public static void addAll(List<Message> messages) {
        if (messages != null && !messages.isEmpty()) {
            SHORT_MEMORY.addAll(messages);
        }
    }

    /**
     * 移除指定消息（按 equals 匹配）.
     */
    public static void remove(Message message) {
        SHORT_MEMORY.remove(message);
    }

    /**
     * 按索引移除消息.
     *
     * @throws IndexOutOfBoundsException 索引越界时抛出
     */
    public static Message remove(int index) {
        return SHORT_MEMORY.remove(index);
    }

    /**
     * 清空全部记忆（包括 system 消息和摘要）.
     */
    public static void clear() {
        SHORT_MEMORY.clear();
    }

    // ==================== 查询 ====================

    /**
     * 返回当前消息列表的不可变快照.
     * <p>调用方无法通过返回的列表修改内部状态 — 修改操作请使用 {@link #add}/{@link #addAll}/{@link #remove}.
     *
     * @return 不可变的消息列表副本
     */
    public static List<Message> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(SHORT_MEMORY));
    }

    /**
     * 返回当前消息数量.
     */
    public static int size() {
        return SHORT_MEMORY.size();
    }

    /**
     * 返回最近的 n 条消息（不可变视图）.
     *
     * @param n 要获取的最近消息数量
     */
    public static List<Message> getRecent(int n) {
        if (n <= 0) {
            return Collections.emptyList();
        }
        int start = Math.max(0, SHORT_MEMORY.size() - n);
        return Collections.unmodifiableList(new ArrayList<>(
                SHORT_MEMORY.subList(start, SHORT_MEMORY.size())));
    }

    /**
     * 检查记忆是否为空.
     */
    public static boolean isEmpty() {
        return SHORT_MEMORY.isEmpty();
    }

    // ==================== Token 估算 ====================

    /**
     * 估算当前消息列表的 token 总数.
     */
    public static int estimateTokens() {
        return TokenCounter.count(SHORT_MEMORY);
    }

    /**
     * 计算可用的上下文窗口余量（可能为负）.
     */
    public static int availableTokens() {
        return maxTokens - reserveTokens - estimateTokens();
    }

    // ==================== 配置 ====================

    public static int getMaxTokens() {
        return maxTokens;
    }

    public static void setMaxTokens(int maxTokens) {
        ShortMemory.maxTokens = maxTokens;
    }

    public static int getReserveTokens() {
        return reserveTokens;
    }

    public static void setReserveTokens(int reserveTokens) {
        ShortMemory.reserveTokens = reserveTokens;
    }

    public static int getKeepTurns() {
        return keepTurns;
    }

    /**
     * 设置保留的最近轮次数（不被摘要压缩）.
     */
    public static void setKeepTurns(int keepTurns) {
        ShortMemory.keepTurns = Math.max(1, keepTurns);
    }

    // ==================== 上下文窗口管理（摘要模式） ====================

    /**
     * 确保当前消息总 token 数不超过上下文窗口上限.
     *
     * <p>超出时，保留最近 {@code keepTurns} 轮完整对话，
     * 对其余更早的轮次调用 LLM 进行<b>增量摘要压缩</b>。
     *
     * <p>每次调用最多压缩一轮，可多次调用直到满足限制或无法继续压缩。
     * 建议在每次构造 LLM 请求前调用此方法。
     *
     * @return 本轮被压缩的轮次数，0 表示无需压缩
     */
    public static int ensureCapacity() {
        int limit = maxTokens - reserveTokens;
        int totalSummarized = 0;

        while (estimateTokens() > limit) {
            // --- 找到所有 user 消息的位置（轮次边界） ---
            List<Integer> turnStarts = getTurnStarts();

            if (turnStarts.isEmpty()) {
                break;
            }

            // 只剩保留轮次，无法继续压缩
            if (turnStarts.size() <= keepTurns) {
                if (estimateTokens() > limit) {
                    System.err.println("⚠️ 已达到最少保留轮次（" + keepTurns
                            + "），仍超出上下文限制！当前 tokens: " + estimateTokens()
                            + " / 上限: " + limit);
                }
                break;
            }

            // --- 确定本轮要摘要的范围 ---
            int batchStart = findContentStart();   // 跳过 system 消息（原始 prompt + 已有摘要）
            int batchEnd = turnStarts.get(1);       // 第一个 user 轮次的结束位置

            if (batchStart >= batchEnd) {
                break;
            }

            // --- 收集待摘要的消息和已有摘要 ---
            List<Message> toSummarize = new ArrayList<>();
            String existingSummary = null;

            for (int i = batchStart; i < batchEnd; i++) {
                Message msg = SHORT_MEMORY.get(i);
                if (ConversationSummarizer.isSummaryMessage(msg)) {
                    existingSummary = msg.getContent().toString();
                } else {
                    toSummarize.add(msg);
                }
            }

            if (toSummarize.isEmpty()) {
                // 只有摘要消息没有实际内容 — 移除摘要消息后重试
                for (int i = batchEnd - 1; i >= batchStart; i--) {
                    if (ConversationSummarizer.isSummaryMessage(SHORT_MEMORY.get(i))) {
                        SHORT_MEMORY.remove(i);
                    }
                }
                continue;
            }

            // --- 调用 LLM 生成摘要 ---
            String newSummary = ConversationSummarizer.summarize(toSummarize, existingSummary);

            // --- 替换：删除旧消息，插入新摘要 ---
            for (int i = batchEnd - 1; i >= batchStart; i--) {
                SHORT_MEMORY.remove(i);
            }
            SHORT_MEMORY.add(batchStart, Message.builder()
                    .role("system")
                    .content(newSummary)
                    .build());

            totalSummarized++;
        }

        if (totalSummarized > 0) {
            System.out.println("📋 对话摘要：已将 " + totalSummarized + " 轮历史对话压缩为摘要"
                    + "（当前 tokens: " + estimateTokens() + " / 上限: " + limit + "）");
        }

        return totalSummarized;
    }

    /**
     * 查找所有 user 消息的索引位置（轮次起始边界）.
     */
    private static List<Integer> getTurnStarts() {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < SHORT_MEMORY.size(); i++) {
            if ("user".equals(SHORT_MEMORY.get(i).getRole())) {
                starts.add(i);
            }
        }
        return starts;
    }

    /**
     * 查找"正文内容"的起始位置 — 跳过所有开头的 system 消息.
     * system 消息（原始 prompt + 摘要）始终保留在列表最前面，不应被纳入摘要范围.
     */
    private static int findContentStart() {
        for (int i = 0; i < SHORT_MEMORY.size(); i++) {
            if (!"system".equals(SHORT_MEMORY.get(i).getRole())) {
                return i;
            }
        }
        return SHORT_MEMORY.size();
    }
}
