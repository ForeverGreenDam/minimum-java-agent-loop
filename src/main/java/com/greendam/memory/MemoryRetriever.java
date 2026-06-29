package com.greendam.memory;

import com.greendam.entity.MemoryEntry;
import com.greendam.entity.Message;

import java.util.List;

/**
 * 记忆检索注入器 — 每轮对话前搜索长期记忆，注入到 ShortMemory 上下文.
 *
 * <h3>工作流程</h3>
 * <pre>
 *   用户提问
 *     ↓
 *   ① LongMemoryStore.search(query, topK)
 *     → 混合检索(BM25+Embedding) + 断崖截断
 *     ↓
 *   ② 清除 ShortMemory 中上一轮的旧记忆上下文
 *     ↓
 *   ③ 构建 system 消息: "📚 相关历史记忆:\n- ..."
 *     ↓
 *   ④ 注入到 ShortMemory（原始 system prompt 之后）
 * </pre>
 *
 * <h3>消息标记</h3>
 * 记忆上下文消息以 {@value #CONTEXT_MARKER} 开头，
 * 检索时通过此标记定位并替换旧消息.
 */
public final class MemoryRetriever {

    /**
     * 记忆上下文消息的标记前缀
     */
    public static final String CONTEXT_MARKER = "📚 相关历史记忆";

    /**
     * 默认检索条数
     */
    private static int topK = 5;

    /**
     * 是否自动注入
     */
    private static boolean autoInject = true;

    private MemoryRetriever() {
    }

    // ==================== 配置 ====================

    public static void configure(int topK, boolean autoInject) {
        MemoryRetriever.topK = Math.max(1, Math.min(topK, 20));
        MemoryRetriever.autoInject = autoInject;
    }

    public static int getTopK() {
        return topK;
    }

    // ==================== 检索 + 注入 ====================

    /**
     * 根据用户查询检索相关长期记忆，并注入到 ShortMemory.
     *
     * @param query 用户输入文本
     * @return 检索到的记忆列表（可能为空）
     */
    public static List<MemoryEntry> retrieveAndInject(String query) {
        if (!LongMemoryStore.isLoaded() || query == null || query.isBlank()) {
            return List.of();
        }

        // ① 检索
        List<MemoryEntry> results = LongMemoryStore.search(query, topK);

        if (!autoInject) {
            return results;
        }

        // ② 清除旧的记忆上下文
        removeOldContexts();

        // ③ 注入新的
        if (!results.isEmpty()) {
            Message ctxMsg = buildContextMessage(results);
            injectContext(ctxMsg);

            System.out.println("📚 长期记忆召回：" + results.size() + " 条（query: \""
                    + truncate(query, 50) + "\"）");
        }

        return results;
    }

    // ==================== 内部方法 ====================

    /**
     * 构建记忆上下文的 system 消息.
     */
    private static Message buildContextMessage(List<MemoryEntry> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append(CONTEXT_MARKER).append(":\n");
        for (MemoryEntry m : memories) {
            sb.append("- [").append(m.getCategory()).append("] ")
                    .append(m.getContent()).append("\n");
        }
        return Message.builder()
                .role("system")
                .content(sb.toString())
                .build();
    }

    /**
     * 清除 ShortMemory 中所有旧的记忆上下文消息.
     */
    private static void removeOldContexts() {
        ShortMemory.removeIf(msg ->
                "system".equals(msg.getRole())
                        && msg.getContent() != null
                        && msg.getContent().toString().startsWith(CONTEXT_MARKER));
    }

    /**
     * 将记忆上下文消息注入到 ShortMemory.
     * <p>插入位置：所有 system 消息之后、第一个非 system 消息之前。
     * 确保它在原始 prompt 和摘要之后，但在对话内容之前.
     */
    private static void injectContext(Message ctxMsg) {
        List<Message> all = ShortMemory.getAll();
        int insertAt = 0;
        for (int i = 0; i < all.size(); i++) {
            if (!"system".equals(all.get(i).getRole())) {
                insertAt = i;
                break;
            }
            insertAt = i + 1; // 如果全是 system，插到最后
        }
        ShortMemory.add(insertAt, ctxMsg);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
