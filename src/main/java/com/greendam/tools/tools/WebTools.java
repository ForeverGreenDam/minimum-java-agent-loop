package com.greendam.tools.tools;

import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 网络请求工具集 — 提供 HTTP GET/POST 请求能力，用于获取网页内容或调用外部 API.
 *
 * <p>使用 Java 11+ 内置的 {@link java.net.http.HttpClient}，无需额外依赖.
 */
public class WebTools {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 简单解析 JSON 格式的 headers 字符串并设置到请求构建器.
     * 期望格式: {"key1":"value1","key2":"value2"}
     */
    private static void parseHeaders(String headers, HttpRequest.Builder builder) {
        // 去除首尾空格和花括号
        String trimmed = headers.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        // 按逗号分割（简单实现，不支持值中包含逗号的复杂情况）
        String[] pairs = trimmed.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("^\"|\"$", "");
                String value = kv[1].trim().replaceAll("^\"|\"$", "");
                if (!key.isEmpty()) {
                    builder.header(key, value);
                }
            }
        }
    }

    /**
     * 格式化 HTTP 响应.
     */
    private static String formatResponse(HttpResponse<String> response) {
        StringBuilder sb = new StringBuilder();
        sb.append("状态码: ").append(response.statusCode()).append("\n");
        sb.append("----------------------------------------\n");
        String body = response.body();
        // 限制返回体长度，避免过长
        if (body.length() > 10000) {
            sb.append(body, 0, 10000);
            sb.append("\n... (响应体过长，已截断至10000字符，共 ").append(body.length()).append(" 字符)");
        } else {
            sb.append(body);
        }
        return sb.toString();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 发送 HTTP GET 请求并返回响应体.
     */
    @Tool(name = "httpGet", description = "向指定URL发送HTTP GET请求，返回响应状态码和响应体文本。可用于获取网页内容或调用REST API。")
    public String httpGet(
            @Param(name = "url", description = "请求的URL地址，需包含协议(http/https)") String url,
            @Param(name = "headers", description = "自定义请求头，JSON格式的键值对，例如 {\"Authorization\":\"Bearer xxx\"}", required = false) String headers
    ) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET();

            // 解析并设置自定义请求头
            if (headers != null && !headers.trim().isEmpty()) {
                parseHeaders(headers, builder);
            }

            HttpResponse<String> response = HTTP_CLIENT.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            return formatResponse(response);
        } catch (Exception e) {
            return "[ERROR] HTTP GET 请求失败: " + e.getMessage();
        }
    }

    /**
     * 发送 HTTP POST 请求并返回响应体.
     */
    @Tool(name = "httpPost", description = "向指定URL发送HTTP POST请求，可附带请求体。返回响应状态码和响应体文本。常用于提交表单或调用API。")
    public String httpPost(
            @Param(name = "url", description = "请求的URL地址，需包含协议(http/https)") String url,
            @Param(name = "body", description = "POST请求体内容（JSON字符串或表单数据）", required = false) String body,
            @Param(name = "contentType", description = "Content-Type请求头，默认为application/json", required = false) String contentType
    ) {
        try {
            String ct = (contentType != null && !contentType.trim().isEmpty())
                    ? contentType.trim()
                    : "application/json";
            String requestBody = body != null ? body : "";

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", ct)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            HttpResponse<String> response = HTTP_CLIENT.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            return formatResponse(response);
        } catch (Exception e) {
            return "[ERROR] HTTP POST 请求失败: " + e.getMessage();
        }
    }
}
