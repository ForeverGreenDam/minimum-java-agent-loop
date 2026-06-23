package com.greendam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Chat Completion 中的一个选项 (choice).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Choice {

    /**
     * 选项序号，从 0 开始
     */
    private Integer index;

    /**
     * 模型返回的消息
     */
    private Message message;

    /**
     * 增量消息（仅流式模式下使用）
     */
    private Message delta;

    /**
     * 停止原因.
     * <ul>
     *   <li>"stop" — 自然结束或命中 stop 词</li>
     *   <li>"length" — 达到 max_tokens 上限</li>
     *   <li>"tool_calls" — 模型请求工具调用</li>
     *   <li>"content_filter" — 内容过滤触发</li>
     * </ul>
     */
    @JsonProperty("finish_reason")
    private String finishReason;

    /**
     * logprobs 信息（请求时开启 logprobs 才返回）
     */
    private Object logprobs;
}
