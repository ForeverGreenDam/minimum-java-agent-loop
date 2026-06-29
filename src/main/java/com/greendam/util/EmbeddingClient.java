package com.greendam.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 向量化客户端 — 调用 OpenAI 兼容的 /v1/embeddings 接口.
 *
 * <h3>设计</h3>
 * <ul>
 *   <li>独立于 {@link OpenAiClient}，支持配置不同的 base URL 和 API Key</li>
 *   <li>当 {@code enabled=false} 时，整个系统降级为纯 BM25 检索模式</li>
 *   <li>支持批量请求（单次最多 20 条），减少 HTTP 往返</li>
 * </ul>
 *
 * <h3>支持的 Embedding 服务</h3>
 * <ul>
 *   <li>OpenAI text-embedding-3-small / text-embedding-3-large</li>
 *   <li>任何兼容 /v1/embeddings 接口的服务</li>
 * </ul>
 */
public final class EmbeddingClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    /**
     * 单次批量请求最大条数
     */
    private static final int MAX_BATCH_SIZE = 20;
    private static boolean enabled = false;
    private static String apiKey;
    private static String baseUrl;
    private static String model;
    private static int dimension = 0;

    private EmbeddingClient() {
    }

    // ==================== 配置 ====================

    public static void configure(boolean enabled, String baseUrl, String apiKey, String model) {
        EmbeddingClient.enabled = enabled;
        EmbeddingClient.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : null;
        EmbeddingClient.apiKey = apiKey;
        EmbeddingClient.model = model;
    }

    public static boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank()
                && baseUrl != null && !baseUrl.isBlank();
    }

    public static String getModel() {
        return model;
    }

    public static int getDimension() {
        return dimension;
    }

    // ==================== 公开 API ====================

    /**
     * 对单条文本生成 embedding 向量.
     *
     * @param text 输入文本
     * @return 向量数组，disabled 或失败时返回 null
     */
    public static float[] embed(String text) {
        if (!isEnabled() || text == null || text.isBlank()) {
            return null;
        }

        try {
            float[][] batch = embedBatch(List.of(text));
            return (batch != null && batch.length > 0) ? batch[0] : null;
        } catch (Exception e) {
            System.err.println("⚠️ Embedding 请求失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 批量生成 embedding 向量.
     *
     * @param texts 输入文本列表
     * @return 向量数组（与输入顺序对应），disabled 或全部失败时返回空数组
     */
    public static float[][] embedBatch(List<String> texts) {
        if (!isEnabled() || texts == null || texts.isEmpty()) {
            return new float[0][];
        }

        List<float[]> allResults = new ArrayList<>();

        // 分批请求
        for (int offset = 0; offset < texts.size(); offset += MAX_BATCH_SIZE) {
            int end = Math.min(offset + MAX_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(offset, end);

            try {
                float[][] batchResults = doEmbed(batch);
                if (batchResults != null) {
                    allResults.addAll(List.of(batchResults));
                } else {
                    // 这批失败了，填 null
                    for (int i = 0; i < batch.size(); i++) {
                        allResults.add(null);
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Embedding 批量请求失败 (offset=" + offset + "): "
                        + e.getMessage());
                for (int i = 0; i < batch.size(); i++) {
                    allResults.add(null);
                }
            }
        }

        return allResults.toArray(new float[0][]);
    }

    // ==================== 内部实现 ====================

    private static float[][] doEmbed(List<String> texts) throws Exception {
        // 构造请求体
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);

        ArrayNode input = JSON.createArrayNode();
        for (String text : texts) {
            input.add(text);
        }
        body.set("input", input);

        String bodyStr = JSON.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr))
                .build();

        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": "
                    + response.body());
        }

        JsonNode root = JSON.readTree(response.body());
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            throw new RuntimeException("响应中缺少 data 数组");
        }

        // 按 index 排序后提取 embedding
        float[][] results = new float[texts.size()][];
        for (JsonNode item : data) {
            int idx = item.get("index").asInt();
            JsonNode emb = item.get("embedding");
            if (emb != null && emb.isArray()) {
                float[] vec = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) {
                    vec[i] = emb.get(i).floatValue();
                }
                results[idx] = vec;

                // 记录维度
                if (dimension == 0 && vec.length > 0) {
                    dimension = vec.length;
                }
            }
        }

        return results;
    }
}
