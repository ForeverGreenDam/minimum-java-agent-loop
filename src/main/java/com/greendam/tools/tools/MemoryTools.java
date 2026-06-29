package com.greendam.tools.tools;

import com.greendam.entity.MemoryEntry;
import com.greendam.memory.LongMemoryStore;
import com.greendam.memory.SessionContext;
import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆管理工具集 — 让 LLM 能自主管理长期记忆.
 *
 * <p>提供三个工具：
 * <ul>
 *   <li><b>remember</b> — 主动记忆</li>
 *   <li><b>recall</b> — 主动回忆</li>
 *   <li><b>forget</b> — 主动遗忘</li>
 * </ul>
 */
public class MemoryTools {

    // ==================== remember ====================

    @Tool(name = "remember", description = "主动将一条信息存入长期记忆。当用户明确要求记住某信息时调用，或当你认为某信息值得跨会话保留时主动调用。")
    public String remember(
            @Param(name = "content", description = "要记住的内容，一句话描述清楚") String content,
            @Param(name = "category", description = "分类: fact/preference/decision/context", required = false) String category,
            @Param(name = "importance", description = "重要度 1-10, 默认5", required = false) Integer importance,
            @Param(name = "keywords", description = "关键词, 逗号分隔, 用于后续检索", required = false) String keywords
    ) {
        if (!LongMemoryStore.isLoaded()) {
            return "[ERROR] 长期记忆系统未初始化";
        }

        String cat = (category != null && !category.isBlank()) ? category : "fact";
        int imp = (importance != null) ? Math.max(1, Math.min(10, importance)) : 5;

        List<String> kwList = null;
        if (keywords != null && !keywords.isBlank()) {
            kwList = Arrays.stream(keywords.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        MemoryEntry entry = MemoryEntry.builder()
                .content(content)
                .category(cat)
                .importance(imp)
                .keywords(kwList)
                .source(SessionContext.getId())
                .build();

        LongMemoryStore.add(entry);
        LongMemoryStore.saveAll();

        return "OK 已记住 [" + cat + "/重要度" + imp + "]: " + content;
    }

    // ==================== recall ====================

    @Tool(name = "recall", description = "搜索长期记忆。在执行任务前如果觉得可能遗漏了之前的约定或背景信息，应先调用此工具检查。")
    public String recall(
            @Param(name = "query", description = "搜索查询，描述你要找什么信息") String query,
            @Param(name = "topK", description = "返回最多几条结果，默认5", required = false) Integer topK
    ) {
        if (!LongMemoryStore.isLoaded()) {
            return "[ERROR] 长期记忆系统未初始化";
        }

        int k = (topK != null) ? Math.max(1, Math.min(topK, 20)) : 5;
        List<MemoryEntry> results = LongMemoryStore.search(query, k);

        if (results.isEmpty()) {
            return "未找到与 " + query + " 相关的长期记忆。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(results.size()).append(" 条相关记忆:\n\n");
        for (int i = 0; i < results.size(); i++) {
            MemoryEntry m = results.get(i);
            sb.append(i + 1).append(". [").append(m.getCategory())
                    .append("/重要度").append(m.getImportance()).append("] ")
                    .append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    // ==================== forget ====================

    @Tool(name = "forget", description = "删除长期记忆中的某条信息。需要提供足够具体的查询来定位要删除的记忆。")
    public String forget(
            @Param(name = "query", description = "用于定位要删除记忆的搜索词") String query
    ) {
        if (!LongMemoryStore.isLoaded()) {
            return "[ERROR] 长期记忆系统未初始化";
        }
        if (query == null || query.isBlank()) {
            return "[ERROR] 请提供要删除的记忆描述";
        }

        List<MemoryEntry> results = LongMemoryStore.search(query, 1);
        if (results.isEmpty()) {
            return "未找到匹配的记忆。可以用 recall 先查看有哪些记忆。";
        }

        MemoryEntry toDelete = results.get(0);
        String summary = "[" + toDelete.getCategory() + "] " + toDelete.getContent();
        LongMemoryStore.delete(toDelete.getId());
        LongMemoryStore.saveAll();

        return "OK 已删除: " + summary;
    }
}
