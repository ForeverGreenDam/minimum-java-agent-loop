package com.greendam.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 连接配置 — 保存 API Key、Base URL、模型名等信息.
 * <p>对应 application.yml 中 {@code openai} 节点.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Model {

    /**
     * API 密钥
     */
    @JsonProperty("api-key")
    private String apiKey;

    /**
     * API 基础地址，例如 https://api.openai.com/v1
     */
    @JsonProperty("base-url")
    private String baseUrl;

    /**
     * 模型名称，例如 gpt-4o / gpt-4o-mini
     */
    private String model;

    /**
     * 采样温度 (0~2)
     */
    private Double temperature;

    /**
     * 最大输出 token 数
     */
    @JsonProperty("max-tokens")
    private Integer maxTokens;

    /**
     * 请求超时时间（秒）
     */
    @JsonProperty("timeout-seconds")
    private Integer timeoutSeconds;
}
