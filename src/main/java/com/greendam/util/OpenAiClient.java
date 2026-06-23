package com.greendam.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greendam.config.ConfigLoader;
import com.greendam.entity.Message;
import com.greendam.entity.OpenAiRequest;
import com.greendam.entity.OpenAiResponse;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * OpenAI 兼容的 HTTP 客户端工具类.
 *
 * <p>支持任意兼容 OpenAI Chat Completions API 的服务（DeepSeek / Qwen / etc.）.
 *
 * <h3>快速使用</h3>
 * <pre>{@code
 *   // 单轮对话
 *   String reply = OpenAiClient.get().chat("你好");
 *
 *   // 多轮对话
 *   List<Message> history = new ArrayList<>();
 *   history.add(Message.builder().role("user").content("1+1=?"));
 *   String reply = OpenAiClient.get().chat(history);
 *
 *   // 完整控制
 *   OpenAiRequest req = OpenAiRequest.builder()
 *       .model("deepseek-chat")
 *       .messages(history)
 *       .temperature(0.3)
 *       .build();
 *   OpenAiResponse resp = OpenAiClient.get().chat(req);
 * }</pre>
 *
 * <p>配置来自 {@code application.yml} 的 {@code openai} 节点.
 */
public final class OpenAiClient {

    private static final Object LOCK = new Object();
    private static volatile OpenAiClient INSTANCE;
    private final HttpClient httpClient;

    // ==================== 内部字段 ====================
    private final ObjectMapper jsonMapper;
    @Getter
    private String apiKey;
    @Getter
    private String baseUrl;
    @Getter
    private String model;
    @Getter
    private double temperature;
    @Getter
    private int maxTokens;
    @Getter
    private int timeoutSeconds;
    private String chatEndpoint;

    private OpenAiClient() {
        this.jsonMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        reloadConfig();
    }

    /**
     * 获取全局单例
     */
    public static OpenAiClient get() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new OpenAiClient();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 先查环境变量，再查 config
     */
    private static String envOrConfig(String envKey, String configKey) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v;
        return ConfigLoader.get().getString(configKey);
    }

    // ==================== 公开方法 ====================

    /**
     * 重新从 ConfigLoader 加载配置（hot-reload）
     */
    public void reloadConfig() {
        ConfigLoader cfg = ConfigLoader.get();
        this.apiKey = envOrConfig("OPENAI_API_KEY", "openai.api-key");
        this.baseUrl = cfg.getString("openai.base-url", "https://api.openai.com/v1");
        this.model = cfg.getString("openai.model", "gpt-4o");
        this.temperature = cfg.getDouble("openai.temperature", 0.7);
        this.maxTokens = cfg.getInt("openai.max-tokens", 4096);
        this.timeoutSeconds = cfg.getInt("openai.timeout-seconds", 60);
        this.chatEndpoint = baseUrl.replaceAll("/+$", "") + "/chat/completions";
    }

    /**
     * 单轮对话 — 发送一条 user 消息，返回 assistant 的文本回复.
     *
     * @param userMessage 用户消息
     * @return assistant 的文本内容
     * @throws OpenAiException 请求失败时抛出
     */
    public String chat(String userMessage) {
        OpenAiRequest request = buildRequest(
                List.of(Message.builder().role("user").content(userMessage).build()));
        OpenAiResponse response = chat(request);
        return response.firstContent();
    }

    /**
     * 多轮对话 — 发送消息列表，返回 assistant 的文本回复.
     *
     * @param messages 消息历史
     * @return assistant 的文本内容
     * @throws OpenAiException 请求失败时抛出
     */
    public String chat(List<Message> messages) {
        OpenAiRequest request = buildRequest(messages);
        OpenAiResponse response = chat(request);
        return response.firstContent();
    }

    /**
     * 发送完整请求，获取完整响应对象.
     *
     * @param request 请求体
     * @return 响应体
     * @throws OpenAiException 请求失败时抛出
     */
    public OpenAiResponse chat(OpenAiRequest request) {
        return send(request, false);
    }

    /**
     * 流式对话 — 每收到一个 content delta 回调 consumer.
     * <p>不处理 reasoning_content；如需同时接收思考内容，请使用
     * {@link #chatStream(OpenAiRequest, Consumer, Consumer)}.
     *
     * @param request 请求体（会自动设置 stream=true）
     * @param onChunk 每块 delta content 的回调
     * @return 最终拼接的完整文本
     * @throws OpenAiException 请求失败时抛出
     */
    public String chatStream(OpenAiRequest request, Consumer<String> onChunk) {
        return doStream(request, null, onChunk, null, null);
    }

    /**
     * 流式对话 — 分别回调思考内容和最终回复.
     *
     * @param request     请求体（会自动设置 stream=true）
     * @param onReasoning 每块 reasoning_content delta 的回调（可为 null）
     * @param onContent   每块 content delta 的回调（可为 null）
     * @return 最终拼接的 content 文本
     * @throws OpenAiException 请求失败时抛出
     */
    public String chatStream(OpenAiRequest request,
                             Consumer<String> onReasoning,
                             Consumer<String> onContent) {
        StringBuilder reasoningBuf = new StringBuilder();
        StringBuilder contentBuf = new StringBuilder();
        String content = doStream(request, reasoningBuf, onReasoning, contentBuf, onContent);
        return content;
    }

    // ==================== 内部方法 ====================

    /**
     * 流式对话内部实现.
     *
     * @param reasoningBuf 收集 reasoning 的 buffer（非 null 时启用 reasoning 收集）
     * @param onReasoning  reasoning 回调
     * @param contentBuf   收集 content 的 buffer（非 null 时启用 content 收集）
     * @param onContent    content 回调
     */
    private String doStream(OpenAiRequest request,
                            StringBuilder reasoningBuf, Consumer<String> onReasoning,
                            StringBuilder contentBuf, Consumer<String> onContent) {
        request.setStream(true);
        StringBuilder effectiveContentBuf = contentBuf != null ? contentBuf : new StringBuilder();
        boolean captureReasoning = reasoningBuf != null || onReasoning != null;
        StringBuilder effectiveReasoningBuf = captureReasoning ? (reasoningBuf != null ? reasoningBuf : new StringBuilder()) : null;

        try {
            String body = jsonMapper.writeValueAsString(request);
            HttpRequest httpReq = buildHttpRequest(body, Duration.ofSeconds(timeoutSeconds));

            HttpResponse<InputStream> resp = httpClient.send(httpReq,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (resp.statusCode() != 200) {
                String errBody = readAll(resp.body());
                throw new OpenAiException(resp.statusCode(), errBody);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        try {
                            OpenAiResponse chunk = jsonMapper.readValue(data, OpenAiResponse.class);
                            if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
                                continue;
                            }
                            Message delta = chunk.getChoices().get(0).getDelta();
                            if (delta == null) {
                                continue;
                            }

                            // reasoning_content
                            String reasoning = delta.getReasoningContent();
                            if (reasoning != null && captureReasoning) {
                                effectiveReasoningBuf.append(reasoning);
                                if (onReasoning != null) {
                                    onReasoning.accept(reasoning);
                                }
                            }

                            // content
                            Object c = delta.getContent();
                            if (c != null) {
                                String text = c instanceof String ? (String) c : c.toString();
                                effectiveContentBuf.append(text);
                                if (onContent != null) {
                                    onContent.accept(text);
                                }
                            }
                        } catch (Exception ignored) {
                            // 跳过无法解析的 chunk
                        }
                    }
                }
            }
        } catch (OpenAiException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAiException("Stream request failed: " + e.getMessage(), e);
        }
        return effectiveContentBuf.toString();
    }

    private OpenAiRequest buildRequest(List<Message> messages) {
        return OpenAiRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }

    private OpenAiResponse send(OpenAiRequest request, boolean stream) {
        request.setStream(stream);
        try {
            String body = jsonMapper.writeValueAsString(request);
            HttpRequest httpReq = buildHttpRequest(body, Duration.ofSeconds(timeoutSeconds));

            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new OpenAiException(resp.statusCode(), resp.body());
            }

            return jsonMapper.readValue(resp.body(), OpenAiResponse.class);
        } catch (OpenAiException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAiException("Request failed: " + e.getMessage(), e);
        }
    }

    private HttpRequest buildHttpRequest(String body, Duration timeout) {
        return HttpRequest.newBuilder()
                .uri(URI.create(chatEndpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String readAll(InputStream in) {
        return new BufferedReader(new InputStreamReader(in))
                .lines().reduce("", (a, b) -> a + b);
    }

    // ==================== 异常类 ====================

    /**
     * OpenAI 请求异常.
     */
    public static class OpenAiException extends RuntimeException {
        private final int statusCode;

        public OpenAiException(int statusCode, String responseBody) {
            super("HTTP " + statusCode + ": " + responseBody);
            this.statusCode = statusCode;
        }

        public OpenAiException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = 0;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
