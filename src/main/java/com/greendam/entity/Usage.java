package com.greendam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Token 用量统计.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Usage {

    /**
     * 提示词消耗的 token 数
     */
    @JsonProperty("prompt_tokens")
    private Integer promptTokens;

    /**
     * 生成内容消耗的 token 数
     */
    @JsonProperty("completion_tokens")
    private Integer completionTokens;

    /**
     * 总 token 数
     */
    @JsonProperty("total_tokens")
    private Integer totalTokens;

    // ---- 以下为 reasoning 模型的额外字段 ----

    /**
     * 推理过程消耗的 token 数
     */
    @JsonProperty("reasoning_tokens")
    private Integer reasoningTokens;
}
