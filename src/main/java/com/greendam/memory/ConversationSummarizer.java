package com.greendam.memory;

import com.greendam.entity.Message;
import com.greendam.entity.OpenAiRequest;
import com.greendam.entity.OpenAiResponse;
import com.greendam.entity.ToolCall;
import com.greendam.util.OpenAiClient;

import java.util.List;

/**
 * 对话摘要器 — 使用 LLM 将对话历史压缩为简洁的要点列表.
 *
 * <h3>用途</h3>
 * 当短期记忆的 token 数接近上下文窗口上限时，对早期对话轮次进行摘要压缩，
 * 替代直接丢弃的滑动窗口策略，减少信息丢失。
 *
 * <h3>两种工作模式</h3>
 * <ul>
 *   <li><b>首次摘要</b> — 将一批消息直接总结为要点</li>
 *   <li><b>增量合并</b> — 将已有摘要与新消息合并，生成更新后的摘要</li>
 * </ul>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>摘要调用不使用工具（纯聊天），temperature=0.3 以保证一致性</li>
 *   <li>摘要响应的 maxTokens 限制为 2000，保持精炼</li>
 *   <li>单条消息内容在提示词中截断至 3000 字符，防止提示词本身超限</li>
 * </ul>
 */
public final class ConversationSummarizer {

    /**
     * 摘要消息的标记前缀，用于在 ShortMemory 中定位摘要消息
     */
    public static final String SUMMARY_MARKER = "📋 历史对话摘要";
    /**
     * 摘要响应的最大 token 数
     */
    private static final int SUMMARY_MAX_TOKENS = 2000;
    /**
     * 单条消息在提示词中的最大字符数
     */
    private static final int MAX_MSG_CHARS = 3000;

    private ConversationSummarizer() {
    }

    // ==================== 公开 API ====================

    /**
     * 将消息列表总结为要点文本.
     *
     * @param messages        需要总结的消息列表
     * @param existingSummary 已有的摘要文本（增量合并时使用），为 null 或空字符串时执行首次摘要
     * @return 摘要文本，格式为 "📋 历史对话摘要:\n- 要点1\n- 要点2..."
     */
    public static String summarize(List<Message> messages, String existingSummary) {
        if (messages == null || messages.isEmpty()) {
            return existingSummary != null ? existingSummary : "";
        }

        String prompt;
        if (existingSummary != null && !existingSummary.isBlank()) {
            prompt = buildMergePrompt(messages, existingSummary);
        } else {
            prompt = buildSummarizePrompt(messages);
        }

        try {
            String result = callLLM(prompt);
            return SUMMARY_MARKER + ":\n" + (result != null ? result : "（摘要生成失败）");
        } catch (Exception e) {
            System.err.println("⚠️ 对话摘要生成失败: " + e.getMessage());
            // 降级：返回手动拼接的简单摘要
            return SUMMARY_MARKER + ":\n- （自动摘要失败: " + e.getMessage() + "）";
        }
    }

    /**
     * 判断一条消息是否是摘要消息（以 {@link #SUMMARY_MARKER} 开头）.
     */
    public static boolean isSummaryMessage(Message msg) {
        if (msg == null || !"system".equals(msg.getRole())) {
            return false;
        }
        Object content = msg.getContent();
        return content != null && content.toString().startsWith(SUMMARY_MARKER);
    }

    // ==================== 提示词构建 ====================

    private static String buildSummarizePrompt(List<Message> messages) {
        return """
                请将以下对话历史总结为简洁的要点列表。每条要点应包含：
                
                - 用户的关键问题或需求
                - AI 的回答要点和结论
                - 工具调用的重要结果（文件路径、关键数据、配置信息、命令输出要点等）
                - 对后续对话可能有用的上下文信息
                
                要求：
                - 用中文总结，每条要点一行，以 "- " 开头
                - 保留具体的技术细节（路径、数字、配置值、命令等）
                - 不需要总结礼节性问候语，聚焦于信息性内容
                - 如果对话中包含错误或问题排查过程，记录问题和解决方案
                
                ---
                
                对话历史：
                
                """
                + formatMessages(messages);
    }

    private static String buildMergePrompt(List<Message> newMessages, String existingSummary) {
        return """
                请将以下"已有摘要"和"新增对话"合并为一份更新后的简洁要点列表。
                
                要求：
                - 保留所有关键信息，对相同主题的要点进行去重合并
                - 如果新增对话中的信息与已有摘要冲突或更新，以新增对话为准
                - 每条要点一行，以 "- " 开头
                - 保留具体的技术细节（路径、数字、配置值等）
                - 用中文总结
                
                ---
                
                已有摘要：
                
                """
                + existingSummary + "\n\n"
                + "---\n\n新增对话：\n\n"
                + formatMessages(newMessages);
    }

    // ==================== 消息格式化 ====================

    /**
     * 将消息列表格式化为摘要提示词中的可读文本.
     * <p>对超长内容进行截断以避免提示词本身超出上下文限制.
     */
    static String formatMessages(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            if (msg == null || msg.getRole() == null) {
                continue;
            }
            // 跳过已有的摘要消息（不应被重复摘要）
            if (isSummaryMessage(msg)) {
                continue;
            }

            switch (msg.getRole()) {
                case "user" -> {
                    sb.append("👤 用户: ");
                    sb.append(truncateContent(msg.getContent()));
                    sb.append("\n\n");
                }
                case "assistant" -> {
                    sb.append("🤖 助手");
                    // 工具调用
                    if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                        sb.append(" [调用了工具: ");
                        for (int i = 0; i < msg.getToolCalls().size(); i++) {
                            if (i > 0) sb.append(", ");
                            ToolCall tc = msg.getToolCalls().get(i);
                            if (tc.getFunction() != null) {
                                sb.append(tc.getFunction().getName());
                            }
                        }
                        sb.append("]");
                    }
                    sb.append(": ");
                    // content 可能为 null（纯工具调用消息）
                    if (msg.getContent() != null && !msg.getContent().toString().isBlank()) {
                        sb.append(truncateContent(msg.getContent()));
                    }
                    sb.append("\n\n");
                }
                case "tool" -> {
                    sb.append("🔧 工具结果");
                    if (msg.getToolCallId() != null) {
                        sb.append(" (").append(msg.getToolCallId()).append(")");
                    }
                    sb.append(": ");
                    sb.append(truncateContent(msg.getContent()));
                    sb.append("\n\n");
                }
                case "system" -> {
                    // 非摘要的 system 消息（如原始 system prompt）— 保留但标记
                    if (!isSummaryMessage(msg)) {
                        sb.append("⚙️ 系统: ");
                        sb.append(truncateContent(msg.getContent()));
                        sb.append("\n\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    // ==================== 工具方法 ====================

    /**
     * 截断过长的内容，避免提示词超限.
     */
    private static String truncateContent(Object content) {
        if (content == null) {
            return "";
        }
        String text = content.toString();
        if (text.length() <= MAX_MSG_CHARS) {
            return text;
        }
        return text.substring(0, MAX_MSG_CHARS) + "\n...（内容过长，已截断，原长度: "
                + text.length() + " 字符）";
    }

    /**
     * 调用 LLM 执行摘要生成.
     * <p>使用低温度（0.3）和有限的 maxTokens（2000），不使用工具.
     */
    private static String callLLM(String prompt) {
        OpenAiClient client = OpenAiClient.get();
        OpenAiRequest request = OpenAiRequest.builder()
                .model(client.getModel())
                .messages(List.of(Message.builder().role("user").content(prompt).build()))
                .temperature(0.3)
                .maxTokens(SUMMARY_MAX_TOKENS)
                .build();

        OpenAiResponse response = client.chat(request);
        return response.firstContent();
    }
}
