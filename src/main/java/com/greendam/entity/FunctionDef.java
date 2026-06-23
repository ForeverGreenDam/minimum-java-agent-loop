package com.greendam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Function 定义 — 描述一个可调用函数的名称、描述和参数 schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FunctionDef {

    /**
     * 函数名称
     */
    private String name;

    /**
     * 函数描述
     */
    private String description;

    /**
     * 参数 JSON Schema.
     * <p>Java 端可以使用 Map&lt;String, Object&gt; 或 String 来承载.
     */
    private Object parameters;
}
