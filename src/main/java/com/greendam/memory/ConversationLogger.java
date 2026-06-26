package com.greendam.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greendam.config.ConfigLoader;
import com.greendam.entity.Message;
import com.greendam.entity.ToolCall;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 对话日志器 — 将完整对话保存为 Markdown 文件到 log/ 目录.
 *
 * <h3>输出格式</h3>
 * 每个文件包含: 元信息头 + 按时间顺序排列的消息，不同角色使用不同图标区分.
 * Assistant 的思考内容折叠在 &lt;details&gt; 块中，工具调用和结果使用代码块展示.
 */
public final class ConversationLogger {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ConversationLogger() {
    }

    /**
     * 将消息列表保存为 log/ 目录下的 Markdown 文件.
     *
     * @param messages 对话消息列表（来自 ShortMemory）
     */
    public static void saveToFile(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            System.out.println("没有对话内容需要保存。");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String fileName = "conversation_" + now.format(FILE_DATE_FMT) + ".md";

        // 确保 log 目录存在
        File logDir = new File("log");
        if (!logDir.exists() && !logDir.mkdirs()) {
            System.err.println("无法创建 log 目录: " + logDir.getAbsolutePath());
            return;
        }

        File file = new File(logDir, fileName);
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(file), StandardCharsets.UTF_8))) {

            // ========== 文件头 ==========
            w.write("# 对话记录 — " + now.format(DISPLAY_DATE_FMT));
            w.newLine();
            w.newLine();

            ConfigLoader cfg = ConfigLoader.get();
            w.write("**模型**: " + cfg.getString("openai.model", "unknown"));
            w.write(" | **温度**: " + cfg.getDouble("openai.temperature", 0.7));
            w.write(" | **消息数**: " + messages.size());
            w.write(" | **Profile**: " + cfg.activeProfile());
            w.newLine();
            w.newLine();
            w.write("---");
            w.newLine();
            w.newLine();

            // ========== 消息列表 ==========
            for (Message msg : messages) {
                writeMessage(w, msg);
            }

            System.out.println("📄 对话已保存到: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ 保存对话失败: " + e.getMessage());
        }
    }

    // ==================== 消息渲染 ====================

    private static void writeMessage(BufferedWriter w, Message msg) throws IOException {
        String role = msg.getRole();
        if (role == null) return;

        switch (role) {
            case "user" -> writeUserMessage(w, msg);
            case "assistant" -> writeAssistantMessage(w, msg);
            case "tool" -> writeToolMessage(w, msg);
            case "system" -> writeSystemMessage(w, msg);
        }
    }

    private static void writeUserMessage(BufferedWriter w, Message msg) throws IOException {
        w.write("### 👤 User");
        w.newLine();
        w.newLine();
        writeTextContent(w, msg.getContent());
        w.newLine();
    }

    private static void writeAssistantMessage(BufferedWriter w, Message msg) throws IOException {
        w.write("### 🤖 Assistant");
        w.newLine();

        // 思考内容 — 折叠起来
        String reasoning = msg.getReasoningContent();
        if (reasoning != null && !reasoning.isBlank()) {
            w.newLine();
            w.write("<details>");
            w.newLine();
            w.write("<summary>💭 Thinking</summary>");
            w.newLine();
            w.newLine();
            w.write(escapeMd(reasoning));
            w.newLine();
            w.write("</details>");
            w.newLine();
        }

        // 文本回复
        Object content = msg.getContent();
        if (content != null && !content.toString().isBlank()) {
            w.newLine();
            writeTextContent(w, content);
        }

        // 工具调用
        List<ToolCall> toolCalls = msg.getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (ToolCall tc : toolCalls) {
                if (tc.getFunction() == null) continue;
                w.newLine();
                w.write("**🔧 调用工具**: `" + tc.getFunction().getName() + "`");
                w.newLine();
                w.newLine();
                w.write("```json");
                w.newLine();
                // 格式化 JSON 参数
                String args = tc.getFunction().getArguments();
                if (args != null && !args.isBlank()) {
                    try {
                        Object parsed = JSON.readValue(args, Object.class);
                        w.write(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(parsed));
                    } catch (Exception e) {
                        w.write(args);
                    }
                }
                w.newLine();
                w.write("```");
                w.newLine();
            }
        }
        w.newLine();
    }

    private static void writeToolMessage(BufferedWriter w, Message msg) throws IOException {
        String callId = msg.getToolCallId();
        w.write("### 🔧 Tool Result");
        if (callId != null && !callId.isBlank()) {
            w.write(" (`" + callId + "`)");
        }
        w.newLine();
        w.newLine();

        String text = msg.getContent() != null ? msg.getContent().toString() : "";
        if (text.isBlank()) {
            w.write("*(无输出)*");
            w.newLine();
        } else if (text.contains("\n") || text.startsWith("{") || text.startsWith("[")) {
            // 多行或结构化输出 → 代码块
            w.write("```");
            w.newLine();
            w.write(text);
            w.newLine();
            w.write("```");
        } else {
            // 单行 → 引用块
            w.write("> ");
            w.write(text);
        }
        w.newLine();
        w.newLine();
    }

    private static void writeSystemMessage(BufferedWriter w, Message msg) throws IOException {
        w.write("### ⚙️ System");
        w.newLine();
        w.newLine();
        writeTextContent(w, msg.getContent());
        w.newLine();
    }

    // ==================== 工具方法 ====================

    private static void writeTextContent(BufferedWriter w, Object content) throws IOException {
        if (content == null) return;
        String text = content instanceof String ? (String) content : content.toString();
        if (text.isBlank()) return;
        w.write(text);
        w.newLine();
    }

    /**
     * 转义 Markdown 特殊字符，防止思考内容中的字符破坏格式.
     */
    private static String escapeMd(String text) {
        if (text == null) return "";
        // 对 `<details>` 内可能冲突的 HTML 标签做转义
        return text.replace("</details>", "&lt;/details&gt;")
                .replace("<details>", "&lt;details&gt;");
    }
}
