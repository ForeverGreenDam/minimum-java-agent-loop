package com.greendam.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greendam.entity.MemoryEntry;
import com.greendam.entity.Message;
import com.greendam.entity.OpenAiRequest;
import com.greendam.entity.ToolCall;
import com.greendam.util.OpenAiClient;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 长期记忆提取器 — 使用 LLM 从对话中提取值得跨会话保留的信息.
 *
 * <h3>提取策略</h3>
 * <ul>
 *   <li>分析对话内容，识别四类值得记住的信息（fact/preference/decision/context）</li>
 *   <li>自动打分（importance 1-10）、生成关键词</li>
 *   <li>与已有记忆对比去重，避免重复存储相似内容</li>
 * </ul>
 *
 * <h3>调用时机</h3>
 * <ul>
 *   <li><b>主要：</b>会话结束时，对完整对话做一次全面提取</li>
 *   <li><b>可选：</b>摘要压缩时顺便扫描（后续优化）</li>
 * </ul>
 *
 * <h3>容错</h3>
 * LLM 返回格式异常时返回空列表，不影响主流程.
 */
public final class MemoryExtractor {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 提取响应的最大 token 数
     */
    private static final int EXTRACT_MAX_TOKENS = 4000;

    /**
     * 单条消息在提示词中的最大字符数
     */
    private static final int MAX_MSG_CHARS = 1000;

    /**
     * 已有记忆在提示词中的最大条数（太多会撑爆 prompt）
     */
    private static final int MAX_EXISTING_IN_PROMPT = 30;

    /**
     * 最低重要度阈值 — 低于此值的记忆不写入长期存储（从 LongMemoryStore 读取）
     */
    private static int getMinImportance() {
        return LongMemoryStore.getMinImportance();
    }

    private MemoryExtractor() {
    }

    // ==================== 公开 API ====================

    /**
     * 从对话消息中提取长期记忆.
     *
     * @param messages         当前会话的消息列表
     * @param existingMemories 已有的长期记忆（用于去重）
     * @return 新提取的记忆条目列表，提取失败或无值得记忆的内容时返回空列表
     */
    public static List<MemoryEntry> extract(List<Message> messages,
                                            List<MemoryEntry> existingMemories) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        String prompt = buildPrompt(messages, existingMemories);

        try {
            String response = callLLM(prompt);
            return parseResponse(response);
        } catch (Exception e) {
            System.err.println("⚠️ 长期记忆提取失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 提示词构建 ====================

    private static String buildPrompt(List<Message> messages,
                                      List<MemoryEntry> existingMemories) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                分析以下对话内容，提取出值得长期记住的信息。返回纯 JSON 数组。
                
                ## 提取标准
                
                只提取具有长期价值的信息，忽略礼节性问候和一般性闲聊。
                
                四种类型：
                - fact: 客观事实（技术栈、配置、路径、版本号、API 密钥格式等）
                - preference: 用户偏好或习惯（代码风格、回答风格、工具偏好等）
                - decision: 做出的决定或约定（方案选择、架构决策、命名规范等）
                - context: 项目背景（当前正在做什么、目标是什么、遇到的问题等）
                
                重要度 1-10 参考：
                - 10: 必须记住（密码、关键配置、用户明确要求"记住"的东西）
                - 7-9: 很重要（技术选型、偏好设定、项目结构信息）
                - 4-6: 有用（一般性上下文、讨论过的方案）
                - 1-3: 可记可不记（临时性信息、大概率不会再问到的）
                
                每条记忆格式：
                {
                  "content": "一句话描述（务必具体，包含实际的值/名称/路径等）",
                  "category": "fact|preference|decision|context",
                  "importance": 数字1-10,
                  "keywords": ["关键词1", "关键词2", "关键词3"]
                }
                
                """);

        // 已有记忆（用于去重提示）
        if (existingMemories != null && !existingMemories.isEmpty()) {
            sb.append("## 已有记忆（不要重复提取相同内容，但如果新信息与旧信息冲突则提取并标注更高重要度）\n\n");
            int count = 0;
            for (MemoryEntry mem : existingMemories) {
                if (count >= MAX_EXISTING_IN_PROMPT) break;
                sb.append("- [").append(mem.getCategory()).append("] ")
                        .append(mem.getContent()).append("\n");
                count++;
            }
            sb.append("\n");
        }

        sb.append("## 对话内容\n\n");
        sb.append(formatMessages(messages));

        sb.append("\n## 要求\n");
        sb.append("- 只返回 JSON 数组，不要任何解释、不要 markdown 代码块标记\n");
        sb.append("- 如果没有值得记住的信息，返回 []\n");
        sb.append("- content 必须用中文，具体明确，包含实际名称/路径/数值\n");

        return sb.toString();
    }

    // ==================== 消息格式化 ====================

    private static String formatMessages(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            if (msg == null || msg.getRole() == null) continue;
            // 跳过 system 消息
            if ("system".equals(msg.getRole())) continue;

            switch (msg.getRole()) {
                case "user" -> {
                    sb.append("用户: ");
                    sb.append(truncate(msg.getContent()));
                    sb.append("\n");
                }
                case "assistant" -> {
                    // 文本内容
                    if (msg.getContent() != null && !msg.getContent().toString().isBlank()) {
                        sb.append("助手: ");
                        sb.append(truncate(msg.getContent()));
                        sb.append("\n");
                    }
                    // 工具调用
                    if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        sb.append("助手调用了工具: ");
                        for (int i = 0; i < msg.getToolCalls().size(); i++) {
                            if (i > 0) sb.append(", ");
                            ToolCall tc = msg.getToolCalls().get(i);
                            if (tc.getFunction() != null) {
                                sb.append(tc.getFunction().getName());
                                String args = tc.getFunction().getArguments();
                                if (args != null && !args.isBlank() && args.length() < 200) {
                                    sb.append("(").append(args).append(")");
                                }
                            }
                        }
                        sb.append("\n");
                    }
                }
                case "tool" -> {
                    String text = msg.getContent() != null ? msg.getContent().toString() : "";
                    // 工具结果只取前 500 字符（通常关键信息在前面）
                    if (text.length() > 500) {
                        text = text.substring(0, 500) + "...";
                    }
                    sb.append("工具结果: ").append(text).append("\n");
                }
            }
        }
        return sb.toString();
    }

    // ==================== 响应解析 ====================

    /**
     * 从 LLM 响应中提取 JSON 数组.
     * <p>兼容三种格式：
     * <ul>
     *   <li>纯 JSON: [{...}, {...}]</li>
     *   <li>Markdown 代码块: ```json [...] ```</li>
     *   <li>带前后文字: "以下是提取的记忆：\n[{...}]\n共 X 条"</li>
     * </ul>
     */
    static List<MemoryEntry> parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return Collections.emptyList();
        }

        String json = response.trim();

        // 尝试提取 markdown 代码块
        Pattern fence = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher m = fence.matcher(json);
        if (m.find()) {
            json = m.group(1).trim();
        } else {
            // 尝试找 JSON 数组的起止位置
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
        }

        try {
            List<MemoryEntry> entries = JSON.readValue(json,
                    new TypeReference<List<MemoryEntry>>() {
                    });

            // 过滤掉明显无效的条目和低重要度条目
            return entries.stream()
                    .filter(e -> e.getContent() != null && !e.getContent().isBlank())
                    .filter(e -> e.getContent().length() >= 5) // 太短的不是有效记忆
                    .filter(e -> e.getImportance() >= getMinImportance()) // 低于阈值的不值得长期保留
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("⚠️ 记忆提取 JSON 解析失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== LLM 调用 ====================

    private static String callLLM(String prompt) {
        OpenAiClient client = OpenAiClient.get();
        OpenAiRequest request = OpenAiRequest.builder()
                .model(client.getModel())
                .messages(List.of(Message.builder().role("user").content(prompt).build()))
                .temperature(0.2)
                .maxTokens(EXTRACT_MAX_TOKENS)
                .build();

        return client.chat(request).firstContent();
    }

    // ==================== 工具方法 ====================

    private static String truncate(Object content) {
        if (content == null) return "";
        String text = content.toString();
        if (text.length() <= MAX_MSG_CHARS) {
            return text;
        }
        return text.substring(0, MAX_MSG_CHARS) + "...(截断)";
    }
}
