package com.greendam.entity;

import com.fasterxml.jackson.annotation.*;
import lombok.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 请求体.
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Chat API</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OpenAiRequest {

    // ==================== 必填 ====================

    /**
     * 模型 ID，例如 gpt-4o / gpt-4o-mini
     */
    private String model;

    /**
     * 对话消息列表
     */
    private List<Message> messages;

    // ==================== 采样控制 ====================

    /**
     * 温度 (0~2)，越高越随机.
     * <p>建议与 top_p 二选一调整，不要同时修改.
     */
    private Double temperature;

    /**
     * 核心采样 (0~1)，只从累积概率达到该值的 token 中采样.
     */
    @JsonProperty("top_p")
    private Double topP;

    /**
     * 为每个 prompt 生成的 completion 数量，默认 1
     */
    private Integer n;

    /**
     * 是否流式返回，默认 false
     */
    private Boolean stream;

    /**
     * 流式返回的额外选项
     */
    @JsonProperty("stream_options")
    private Map<String, Object> streamOptions;

    // ==================== 长度控制 ====================

    /**
     * 最大输出 token 数.
     * <p>大多数模型推荐使用 max_completion_tokens.
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /**
     * 最大输出 token 数（推荐，适用于支持 reasoning 的模型）
     */
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;

    // ==================== 停止条件 ====================

    /**
     * 停止词，可为单个字符串或字符串数组；命中后立即停止
     */
    private Object stop;

    // ==================== 惩罚项 ====================

    /**
     * 存在惩罚 (-2.0~2.0)，正值降低重复话题的概率.
     */
    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    /**
     * 频率惩罚 (-2.0~2.0)，正值降低逐字重复的概率.
     */
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    // ==================== 偏差 ====================

    /**
     * Logit 偏差，token_id → bias (-100~100) 的映射.
     */
    @JsonProperty("logit_bias")
    private Map<String, Integer> logitBias;

    // ==================== 用户标识 ====================

    /**
     * 终端用户唯一标识，用于 OpenAI 监控滥用
     */
    private String user;

    // ==================== 输出控制 ====================

    /**
     * 输出格式：text / json_object / json_schema
     */
    @JsonProperty("response_format")
    private ResponseFormat responseFormat;

    /**
     * 随机种子，用于可复现输出（Beta）
     */
    private Integer seed;

    // ==================== 工具调用 ====================

    /**
     * 可用工具列表
     */
    private List<Tool> tools;

    /**
     * 工具选择策略.
     * <p>可为 "none" / "auto" / "required" 字符串，或指定具体工具的对象.
     */
    @JsonProperty("tool_choice")
    private Object toolChoice;

    /**
     * 是否并行调用工具，默认 true
     */
    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    // ==================== 高级 ====================

    /**
     * 存储此请求以便以后检索
     */
    private Object metadata;

    // ==================== 扩展参数 ====================

    /**
     * 厂商扩展参数 — 序列化时平铺到 JSON 顶层.
     *
     * <p>用于传递 OpenAI 标准之外的参数，例如 DeepSeek 的 thinking：
     * <pre>{@code
     *   OpenAiRequest req = OpenAiRequest.builder()
     *       .model("deepseek-chat")
     *       .messages(messages)
     *       .entry("thinking", Map.of("type", "enabled"))
     *       .build();
     *   // → JSON: {"model":"deepseek-chat", ..., "thinking":{"type":"enabled"}}
     * }</pre>
     */
    @JsonIgnore
    @Singular("entry")
    private Map<String, Object> extra = new HashMap<>();

    @JsonAnyGetter
    private Map<String, Object> any() {
        return extra.isEmpty() ? null : extra;
    }

    @JsonAnySetter
    private void any(String key, Object value) {
        this.extra.put(key, value);
    }
}
