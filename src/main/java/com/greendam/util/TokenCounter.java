package com.greendam.util;

import com.greendam.entity.Message;
import com.greendam.entity.ToolCall;

import java.util.List;

/**
 * Token 估算工具 — 基于字符类型比例的启发式 token 计数.
 *
 * <p>不引入 tiktoken 等外部依赖，使用字符类型比例估算 token 数：
 * <ul>
 *   <li>CJK 字符（中文/日文/韩文）: ~1.5 字符/token</li>
 *   <li>ASCII 字母数字符号: ~4 字符/token</li>
 *   <li>其他 Unicode 字符: ~3 字符/token</li>
 * </ul>
 *
 * <p>估算误差通常在 ±20% 以内，足够用于上下文窗口管理和截断决策。
 * <p>每条消息额外计入 4 tokens 的 role/格式开销（与 OpenAI 计费方式对齐）.
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 *   int tokens = TokenCounter.count(messages);
 *   if (tokens > limit) {
 *       ShortMemory.ensureCapacity();
 *   }
 * }</pre>
 */
public final class TokenCounter {

    private TokenCounter() {
    }

    // ==================== CJK 检测 ====================

    /**
     * 判断一个字符是否属于 CJK（中日韩）范围.
     */
    private static boolean isCJK(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)    // CJK Unified Ideographs
                || (c >= 0x3400 && c <= 0x4DBF)    // CJK Unified Ideographs Extension A
                || (c >= 0x20000 && c <= 0x2A6DF)  // Extension B
                || (c >= 0xF900 && c <= 0xFAFF)    // CJK Compatibility Ideographs
                || (c >= 0x3000 && c <= 0x303F)    // CJK Symbols and Punctuation
                || (c >= 0xFF00 && c <= 0xFFEF)    // Halfwidth and Fullwidth Forms
                || (c >= 0x3040 && c <= 0x309F)    // Hiragana
                || (c >= 0x30A0 && c <= 0x30FF)    // Katakana
                || (c >= 0xAC00 && c <= 0xD7AF);   // Hangul Syllables
    }

    // ==================== 字符串估算 ====================

    /**
     * 估算纯文本字符串的 token 数.
     *
     * @param text 文本内容，为 null 或空字符串时返回 0
     * @return 估算的 token 数（至少为 0）
     */
    public static int estimateString(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int cjkChars = 0;
        int asciiChars = 0;
        int otherChars = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCJK(c)) {
                cjkChars++;
            } else if (c < 128) {
                asciiChars++;
            } else {
                otherChars++;
            }
        }

        // CJK: ~1.5 字符/token, ASCII: ~4 字符/token, Other: ~3 字符/token
        return (int) Math.ceil(cjkChars / 1.5 + asciiChars / 4.0 + otherChars / 3.0);
    }

    // ==================== 消息/列表估算 ====================

    /**
     * 估算单条消息的 token 数.
     * <p>包含 role 字段等元数据开销（约 4 tokens）和所有字段内容.
     *
     * @param msg 消息对象，为 null 时返回 0
     * @return 估算的 token 数
     */
    public static int count(Message msg) {
        if (msg == null) {
            return 0;
        }

        int tokens = 4; // role + JSON 格式开销

        // content 字段
        if (msg.getContent() != null) {
            tokens += estimateString(msg.getContent().toString());
        }

        // reasoning_content（历史消息中应该已被清除，但以防万一）
        if (msg.getReasoningContent() != null) {
            tokens += estimateString(msg.getReasoningContent());
        }

        // name 字段
        if (msg.getName() != null) {
            tokens += estimateString(msg.getName()) + 1;
        }

        // tool_calls 列表
        if (msg.getToolCalls() != null) {
            for (ToolCall tc : msg.getToolCalls()) {
                tokens += countToolCall(tc);
            }
        }

        // tool_call_id
        if (msg.getToolCallId() != null) {
            tokens += estimateString(msg.getToolCallId()) + 2;
        }

        return tokens;
    }

    /**
     * 估算 ToolCall 的 token 数.
     */
    private static int countToolCall(ToolCall tc) {
        if (tc == null) {
            return 0;
        }
        int tokens = 6; // id + type + function 结构开销
        if (tc.getId() != null) {
            tokens += estimateString(tc.getId());
        }
        if (tc.getFunction() != null) {
            tokens += estimateString(tc.getFunction().getName());
            tokens += estimateString(tc.getFunction().getArguments());
        }
        return tokens;
    }

    /**
     * 估算整个消息列表的总 token 数.
     *
     * @param messages 消息列表，为 null 或空列表时返回 0
     * @return 估算的总 token 数
     */
    public static int count(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Message msg : messages) {
            total += count(msg);
        }
        return total;
    }
}
