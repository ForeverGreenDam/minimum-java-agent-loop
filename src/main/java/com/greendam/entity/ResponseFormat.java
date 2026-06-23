package com.greendam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Response Format — 控制模型输出格式.
 * <p>例如设置为 JSON 模式: type = "json_object" 或 "json_schema".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ResponseFormat {

    /**
     * 输出类型.
     * <ul>
     *   <li>"text" — 默认文本</li>
     *   <li>"json_object" — JSON 模式</li>
     *   <li>"json_schema" — 结构化 JSON 模式</li>
     * </ul>
     */
    private String type;

    /**
     * 当 type 为 "json_schema" 时的 schema 定义
     */
    @JsonProperty("json_schema")
    private Object jsonSchema;
}
