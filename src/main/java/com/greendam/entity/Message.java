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
     */
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
     * <p>仅响应中存在，不需要在请求中发送。nullable，未启用时不出现.
     */
    @JsonProperty("reasoning_content")
    private String reasoningContent;
}
