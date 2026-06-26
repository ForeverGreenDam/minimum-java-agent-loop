package com.greendam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI Chat Message — 对应一条对话消息.
 * <p>
 * role 取值: system / user / assistant / tool
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Message {

    /**
     * 消息角色: system, user, assistant, tool
     */
    private String role;

    /**
     * 消息内容.
     * <p>纯文本时为 String；多模态（vision）时可以是 List&lt;ContentPart&gt;.
     * 使用 Object 兼容两种形态.
     *
     * <p>使用 {@code ALWAYS} 确保 content 始终序列化到 JSON，
     * 即使为 {@code null} 或空字符串。部分 API（如 DeepSeek）要求
     * assistant(tool_calls) 消息中 content 字段必须存在。
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Object content;

    /**
     * 可选的名字，用于区分同一角色的不同参与者
     */
    private String name;

    /**
     * assistant 消息中的工具调用列表
     */
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    /**
     * tool 消息对应的 tool_call_id
     */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /**
     * 推理/思考内容（assistant 的"内心独白"）.
     *
     * <p>非 OpenAI 官方字段，但已被多家厂商采用：
     * <ul>
     *   <li>DeepSeek — 开启 thinking 后返回</li>
     *   <li>Qwen QwQ — 开启思考模式后返回</li>
     *   <li>Claude — thinking blocks（扩展形式）</li>
     * </ul>
     * <p><b>重要：</b>仅响应中存在，回填历史时必须清除，否则白白占用上下文窗口。
     * 使用 {@link #toHistoryMessage()} 获取适合存历史的安全副本.
     */
    @JsonProperty("reasoning_content")
    private String reasoningContent;

    /**
     * 创建一个适合存入消息历史的"干净"副本.
     *
     * <p>会清除 {@code reasoningContent}，因为：
     * <ul>
     *   <li>reasoning 是模型的草稿纸，新 turn 不需要看到</li>
     *   <li>reasoning 通常数倍于 content，占用大量上下文窗口</li>
     *   <li>DeepSeek 等厂商明确建议不要回传 reasoning_content</li>
     * </ul>
     *
     * @return 清理后的 Message 副本（新对象，不影响原对象）
     */
    public Message toHistoryMessage() {
        return Message.builder()
                .role(this.role)
                .content(this.content)
                .name(this.name)
                .toolCalls(this.toolCalls)
                .toolCallId(this.toolCallId)
                // reasoningContent 刻意不复制 — 不应用于历史
                .build();
    }
}
