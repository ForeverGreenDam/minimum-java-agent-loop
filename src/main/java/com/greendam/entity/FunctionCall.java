package com.greendam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Function Call — 工具调用中的函数参数.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FunctionCall {

    /**
     * 函数名
     */
    private String name;

    /**
     * 函数参数 JSON 字符串.
     * <p>调用方按需反序列化为具体类型.
     */
    private String arguments;
}
