package com.greendam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Tool Call — 模型请求调用的工具.
 * <p>出现在 assistant 消息中，表示模型希望执行一个函数.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ToolCall {

    /**
     * 工具调用唯一 ID
     */
    private String id;

    /**
     * 调用类型，目前固定为 "function"
     */
    private String type;

    /**
     * 函数调用详情
     */
    private FunctionCall function;
}
