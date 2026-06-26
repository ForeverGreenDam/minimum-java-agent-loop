package com.greendam.tools.tools;

import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // ==================== Playwright 无头浏览器 ====================

    /**
     * Playwright 实例（全局复用，JVM 退出时需调用 {@link #shutdownPlaywright()} 释放）
     */
    private static volatile Playwright PLAYWRIGHT;
    private static volatile Browser BROWSER;

    /**
     * 获取或创建全局 Browser 实例（懒加载 + 双重检查锁）
     */
    private static Browser getBrowser() {
        if (BROWSER == null) {
            synchronized (WebTools.class) {
                if (BROWSER == null) {
                    PLAYWRIGHT = Playwright.create();
                    BROWSER = PLAYWRIGHT.chromium().launch(new BrowserType.LaunchOptions()
                            .setHeadless(true));
                }
            }
        }
        return BROWSER;
    }

    /**
     * 关闭 Playwright，释放浏览器进程和临时文件.
     * 应在 JVM 退出前调用（Main 中已注册 shutdown hook）.
     */
    public static void shutdownPlaywright() {
        if (BROWSER != null) {
            try {
                BROWSER.close();
            } catch (Exception ignored) {
            }
            BROWSER = null;
        }
        if (PLAYWRIGHT != null) {
            try {
                PLAYWRIGHT.close();
            } catch (Exception ignored) {
            }
            PLAYWRIGHT = null;
        }
    }

    // ==================== 请求解析辅助 ====================

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

    /**
     * 完整的 HTML → 纯文本 清洗流程.
     */
    private static String cleanHtml(String html) {
        // 1) 去掉 <script>、<style>、<noscript> 标签及其内容
        html = removeTagAndContent(html, "script", "style", "noscript");

        // 2) 去掉 HTML 注释 <!-- ... -->
        html = removeComments(html);

        // 3) 将块级标签替换为换行符
        html = blockTagsToNewline(html);

        // 4) 去掉所有剩余 HTML 标签（保留下一个 step 的文本内容）
        html = stripTags(html);

        // 5) 解码 HTML 实体，如 &amp; → &、&#x4E2D; → 中
        html = decodeEntities(html);

        // 6) 规范化空白字符
        html = normalizeWhitespace(html);

        return html.trim();
    }

    // ==================== HTML 清洗管道 ====================

    /**
     * 移除指定标签及其全部内容（包括嵌套）.
     */
    private static String removeTagAndContent(String html, String... tags) {
        for (String tag : tags) {
            // (?s) = DOTALL, 让 . 匹配换行符
            // <tag[^>]*> 匹配开始标签（含属性）
            // .*? 非贪婪匹配内容
            // </tag> 匹配结束标签，不区分大小写
            html = Pattern.compile(
                    "(?s)<" + Pattern.quote(tag) + "[^>]*>.*?</" + Pattern.quote(tag) + "\\s*>",
                    Pattern.CASE_INSENSITIVE
            ).matcher(html).replaceAll("");
        }
        return html;
    }

    /**
     * 移除 HTML 注释 &lt;!-- ... --&gt;.
     */
    private static String removeComments(String html) {
        return Pattern.compile("(?s)<!--.*?-->").matcher(html).replaceAll("");
    }

    /**
     * 将块级元素标签替换为换行符，保留段落结构.
     */
    private static String blockTagsToNewline(String html) {
        // 匹配常见的块级/换行标签（开始/结束/自闭合）
        String blockPattern = "</?(?:p|div|h[1-6]|li|tr|br|hr|article|section|header|footer|main|aside|nav|table|ul|ol|dl|dt|dd|blockquote|pre|figure|figcaption|address)[^>]*/?>";
        html = html.replaceAll("(?i)" + blockPattern, "\n");
        // 也处理 <br/> <br /> <hr/> 等自闭合形式（已被上面捕获，此处兜底）
        html = html.replaceAll("(?i)<br\\s*/?>", "\n");
        html = html.replaceAll("(?i)<hr\\s*/?>", "\n---\n");
        return html;
    }

    /**
     * 移除所有 HTML 标签 &lt;...&gt;.
     */
    private static String stripTags(String html) {
        return html.replaceAll("<[^>]*>", "");
    }

    /**
     * 解码常见的 HTML 实体.
     */
    private static String decodeEntities(String text) {
        // 命名实体
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("&#39;", "'");
        text = text.replace("&apos;", "'");
        text = text.replace("&nbsp;", " ");
        text = text.replace("&ensp;", " ");
        text = text.replace("&emsp;", "  ");
        text = text.replace("&ndash;", "–");
        text = text.replace("&mdash;", "—");
        text = text.replace("&lsquo;", "'");
        text = text.replace("&rsquo;", "'");
        text = text.replace("&ldquo;", "\"");
        text = text.replace("&rdquo;", "\"");

        // 十进制数字实体 &#20013; → 中
        Matcher decMatcher = Pattern.compile("&#(\\d+);").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (decMatcher.find()) {
            int codePoint = Integer.parseInt(decMatcher.group(1));
            decMatcher.appendReplacement(sb, Matcher.quoteReplacement(
                    Character.toString(codePoint)));
        }
        decMatcher.appendTail(sb);
        text = sb.toString();

        // 十六进制数字实体 &#x4E2D; → 中
        Matcher hexMatcher = Pattern.compile("&#x([0-9a-fA-F]+);").matcher(text);
        sb = new StringBuffer();
        while (hexMatcher.find()) {
            int codePoint = Integer.parseInt(hexMatcher.group(1), 16);
            hexMatcher.appendReplacement(sb, Matcher.quoteReplacement(
                    Character.toString(codePoint)));
        }
        hexMatcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 压缩多余空白 — 连续空行→双换行，连续空格→单空格.
     */
    private static String normalizeWhitespace(String text) {
        // 先把 \r\n 统一成 \n
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        // 压缩每行内的空白（但保留换行符）
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(trimmed);
            }
        }
        return sb.toString();
    }

    /**
     * 抓取网页并提取纯文本内容，自动去除 HTML 标签、CSS 样式、JavaScript 代码.
     * 适合阅读新闻文章、博客、文档等网页。
     */
    @Tool(name = "webToText", description = "使用GET方法获取指定URL的网页内容并提取纯文本。自动去除HTML标签、CSS样式、JS代码、注释，保留正文文本。适合抓取新闻、博客、文档等网页内容阅读。不适合调用JSON API（如果你要调用jsonAPI的话，请用httpGet）。")
    public String webToText(
            @Param(name = "url", description = "要抓取的网页URL") String url,
            @Param(name = "maxChars", description = "最大返回字符数，默认8000", required = false) Long maxChars
    ) {
        try {
            long limit = (maxChars != null && maxChars > 0) ? maxChars : 8000L;

            // 1. 发送请求，获取 HTML
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (compatible; MinimumAgent/1.0)")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "[ERROR] HTTP " + response.statusCode() + " — 抓取失败";
            }

            String html = response.body();
            if (html == null || html.isBlank()) {
                return "[WARN] 响应体为空";
            }

            // 2. 清洗 HTML → 纯文本
            String text = cleanHtml(html);
            if (text.isBlank()) {
                return "[WARN] 提取到的文本为空，可能是动态渲染的页面（SPA）";
            }

            // 3. 截断
            int originalLen = text.length();
            if (text.length() > limit) {
                text = text.substring(0, (int) limit);
                text += "\n\n... (文本已截断至 " + limit + " 字符，原始共 " + originalLen + " 字符)";
            }

            return text;
        } catch (Exception e) {
            return "[ERROR] 网页抓取失败: " + e.getMessage();
        }
    }

    /**
     * 使用无头浏览器（Chromium）渲染网页并提取可见文本.
     *
     * <p>与 {@code webToText} 的区别：
     * <ul>
     *   <li>本工具会启动真实浏览器引擎，执行 JavaScript，等待网络空闲后再提取内容</li>
     *   <li>适合 SPA（React/Vue/Angular）、Ajax 动态加载、需要 JS 渲染的页面</li>
     *   <li>速度较慢（首次启动约 2-3 秒），优先使用 {@code webToText}，遇到动态页面再换本工具</li>
     * </ul>
     */
    @Tool(name = "webToTextBrowser", description = "使用无头浏览器(Chromium)渲染网页后提取纯文本。会等待JavaScript执行和网络请求完成，适合动态加载的SPA页面。如果webToText抓到的内容为空或提示动态渲染，请用本工具。速度较慢，优先用webToText。")
    public String webToTextBrowser(
            @Param(name = "url", description = "要抓取的网页URL") String url,
            @Param(name = "maxChars", description = "最大返回字符数，默认8000", required = false) Long maxChars
    ) {
        long limit = (maxChars != null && maxChars > 0) ? maxChars : 8000L;

        Browser browser = getBrowser();
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (compatible; MinimumAgent/1.0)"));
        Page page = context.newPage();
        try {
            // 导航到页面，等待网络空闲（SPA 数据加载完成）
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.NETWORKIDLE)
                    .setTimeout(30000));

            // innerText 只取可见文本，效果比 textContent 干净
            String text = page.innerText("body");
            if (text == null || text.isBlank()) {
                // 降级：有些页面 innerText 无效，改用 textContent + HTML 清洗
                String html = page.content();
                text = cleanHtml(html);
            }

            if (text == null || text.isBlank()) {
                return "[WARN] 渲染后仍未提取到文本内容";
            }

            // 规范化空白
            text = normalizeWhitespace(text);

            // 截断
            int originalLen = text.length();
            if (text.length() > limit) {
                text = text.substring(0, (int) limit);
                text += "\n\n... (文本已截断至 " + limit + " 字符，原始共 " + originalLen + " 字符)";
            }

            return text;
        } catch (Exception e) {
            return "[ERROR] 浏览器抓取失败: " + e.getMessage();
        } finally {
            try {
                page.close();
            } catch (Exception ignored) {
            }
            try {
                context.close();
            } catch (Exception ignored) {}
        }
    }
}
