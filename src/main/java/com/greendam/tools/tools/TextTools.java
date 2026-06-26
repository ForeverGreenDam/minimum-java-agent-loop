package com.greendam.tools.tools;

import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 文本处理工具集 — 提供文本统计、查找替换、Base64编解码等常用文本操作.
 *
 * <p>所有方法均为纯函数，无副作用.
 */
public class TextTools {

    /**
     * 统计文本的字符数、单词数、行数等基本信息.
     */
    @Tool(name = "countText", description = "统计文本的字符数（含/不含空格）、单词数、行数，返回统计摘要。")
    public String countText(
            @Param(name = "text", description = "要统计的文本内容") String text
    ) {
        if (text == null || text.isEmpty()) {
            return "字符数: 0\n单词数: 0\n行数: 0\n空格数: 0\n(空文本)";
        }

        int totalChars = text.length();
        int nonSpaceChars = text.replaceAll("\\s+", "").length();
        int spaceChars = totalChars - nonSpaceChars;

        // 按空白分割统计单词数量
        String trimmed = text.trim();
        int wordCount = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;

        // 行数
        int lineCount = text.split("\\r?\\n", -1).length;

        StringBuilder sb = new StringBuilder();
        sb.append("字符数（总计）: ").append(totalChars).append("\n");
        sb.append("字符数（不含空格）: ").append(nonSpaceChars).append("\n");
        sb.append("空格数: ").append(spaceChars).append("\n");
        sb.append("单词数: ").append(wordCount).append("\n");
        sb.append("行数: ").append(lineCount);
        return sb.toString();
    }

    /**
     * 在文本中查找并替换指定内容.
     */
    @Tool(name = "textReplace", description = "在文本中查找并替换指定字符串。支持普通替换和正则表达式替换。返回替换后的文本。【注意】本工具是纯文本处理函数，只返回替换后的字符串，不会修改任何文件。如果需要将替换结果写入文件，请将本工具的返回值作为 writeFile 工具的 content 参数传入。")
    public String textReplace(
            @Param(name = "text", description = "原始文本") String text,
            @Param(name = "search", description = "要查找的字符串或正则表达式") String search,
            @Param(name = "replace", description = "替换为的字符串") String replace,
            @Param(name = "useRegex", description = "是否将search作为正则表达式解析，默认false为普通文本替换", required = false) Boolean useRegex
    ) {
        if (text == null) {
            return "[ERROR] text 不能为空";
        }
        if (search == null || search.isEmpty()) {
            return "[ERROR] search 不能为空";
        }
        if (replace == null) {
            replace = "";
        }

        try {
            boolean regex = useRegex != null && useRegex;
            String result;
            if (regex) {
                result = text.replaceAll(search, replace);
            } else {
                // String.replace(CharSequence, CharSequence) 本身就是字面替换，不识别正则
                // 注意：不要用 Pattern.quote(search)，那会把 "foo" 变成 "\Qfoo\E"，
                // 而 String.replace() 会去字面匹配 "\Qfoo\E" 这串字符，导致匹配不到原文中的 "foo"
                result = text.replace(search, replace);
            }
            return result;
        } catch (Exception e) {
            return "[ERROR] 文本替换失败: " + e.getMessage();
        }
    }

    /**
     * 将文本进行 Base64 编码.
     */
    @Tool(name = "base64Encode", description = "将文本进行Base64编码，返回编码后的字符串。")
    public String base64Encode(
            @Param(name = "text", description = "要编码的原始文本") String text
    ) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        try {
            return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "[ERROR] Base64 编码失败: " + e.getMessage();
        }
    }

    /**
     * 将 Base64 字符串解码为原始文本.
     */
    @Tool(name = "base64Decode", description = "将Base64编码的字符串解码为原始文本（UTF-8）。")
    public String base64Decode(
            @Param(name = "base64Text", description = "Base64编码的字符串") String base64Text
    ) {
        if (base64Text == null || base64Text.isEmpty()) {
            return "";
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Text);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "[ERROR] Base64 解码失败: 输入不是有效的Base64字符串 — " + e.getMessage();
        } catch (Exception e) {
            return "[ERROR] Base64 解码失败: " + e.getMessage();
        }
    }
}
