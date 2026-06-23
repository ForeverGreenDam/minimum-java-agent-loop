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
 * OpenAI Chat Completions 响应体.
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/chat/object">OpenAI Chat Completion Object</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OpenAiResponse {

    /**
     * 本次 completion 的唯一 ID
     */
    private String id;

    /**
     * 对象类型，固定为 "chat.completion"
     */
    private String object;

    /**
     * 创建时间戳（Unix 秒）
     */
    private Long created;

    /**
     * 实际使用的模型 ID
     */
    private String model;

    /**
     * 返回的选项列表
     */
    private List<Choice> choices;

    /**
     * Token 用量统计
     */
    private Usage usage;

    /**
     * 系统指纹，用于调试模型侧变更
     */
    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    // ==================== 便捷方法 ====================

    /**
     * 提取第一个 choice 的 message content 文本.
     *
     * @return content 字符串，若无则返回 null
     */
    public String firstContent() {
        Message msg = firstMessage();
        if (msg == null || msg.getContent() == null) {
            return null;
        }
        Object content = msg.getContent();
        return content instanceof String ? (String) content : content.toString();
    }

    /**
     * 提取第一个 choice 的 reasoning_content（思考过程）.
     *
     * @return reasoning 文本，若无则返回 null
     */
    public String firstReasoning() {
        Message msg = firstMessage();
        return msg != null ? msg.getReasoningContent() : null;
    }

    /**
     * 获取第一个 choice 的 message，无则返回 null
     */
    private Message firstMessage() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).getMessage();
    }
}
