package com.greendam.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.greendam.entity.MemoryEntry;
import com.greendam.util.EmbeddingClient;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 长期记忆 JSON 文件存储引擎.
 *
 * <h3>存储格式</h3>
 * 所有记忆条目序列化为单个 JSON 数组文件：
 * <pre>{@code memory/long/index.json}</pre>
 *
 * <h3>检索架构</h3>
 * 委托给 {@link HybridRetriever} 执行 BM25 + Embedding 混合检索 + 断崖截断。
 * 数据变更后自动重建 BM25 索引。
 *
 * <h3>Embedding 管理</h3>
 * 新增/更新记忆时自动生成 embedding 向量（如 {@link EmbeddingClient} 已启用）。
 * 可通过 {@link #rebuildAllEmbeddings()} 批量重建全部向量。
 *
 * <h3>淘汰策略</h3>
 * 当条目数超过 {@code maxEntries} 时，按综合评分淘汰最低分条目.
 */
public final class LongMemoryStore {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 内存中的记忆列表
     */
    private static final List<MemoryEntry> ENTRIES = new ArrayList<>();

    /**
     * 存储目录
     */
    private static String storageDir = "memory/long";

    /**
     * 最大条目数
     */
    private static int maxEntries = 500;

    /**
     * 索引文件路径
     */
    private static Path indexPath;

    /**
     * 是否已初始化
     */
    @Getter
    private static boolean loaded = false;

    private LongMemoryStore() {
    }

    // ==================== 配置 ====================

    public static void configure(String dir, int max) {
        storageDir = dir;
        maxEntries = Math.max(10, max);
        indexPath = Paths.get(storageDir, "index.json");
    }

    public static int size() {
        synchronized (ENTRIES) {
            return ENTRIES.size();
        }
    }

    // ==================== 生命周期 ====================

    /**
     * 从磁盘加载全部记忆条目，并重建 BM25 索引.
     *
     * @return 加载的条目数
     */
    public static int loadAll() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
            indexPath = Paths.get(storageDir, "index.json");

            if (!Files.exists(indexPath)) {
                loaded = true;
                return 0;
            }

            try {
                byte[] data = Files.readAllBytes(indexPath);
                if (data.length == 0) {
                    loaded = true;
                    return 0;
                }
                List<MemoryEntry> list = JSON.readValue(data,
                        new TypeReference<List<MemoryEntry>>() {
                        });
                if (list != null) {
                    ENTRIES.addAll(list);
                }
                loaded = true;
                BM25Scorer.buildIndex(ENTRIES);
                System.out.println("📚 长期记忆已加载：" + ENTRIES.size() + " 条（"
                        + indexPath.toAbsolutePath() + "）"
                        + (EmbeddingClient.isEnabled() ? " [embedding: ON]"
                        : " [BM25-only]"));
                return ENTRIES.size();
            } catch (IOException e) {
                System.err.println("⚠️ 长期记忆加载失败: " + e.getMessage());
                loaded = true;
                return 0;
            }
        }
    }

    /**
     * 将全部记忆条目写回磁盘.
     */
    public static void saveAll() {
        synchronized (ENTRIES) {
            try {
                Path dir = Paths.get(storageDir);
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
                byte[] jsonBytes = JSON.writeValueAsBytes(ENTRIES);
                Files.write(indexPath, jsonBytes);
            } catch (IOException e) {
                System.err.println("⚠️ 长期记忆保存失败: " + e.getMessage());
            }
        }
    }

    // ==================== CRUD ====================

    /**
     * 新增一条记忆，同时生成 embedding 并增量更新索引.
     */
    public static void add(MemoryEntry entry) {
        if (entry == null) return;
        synchronized (ENTRIES) {
            generateEmbedding(entry);
            ENTRIES.add(entry);
            evictIfNeeded();
            BM25Scorer.addEntry(entry);
        }
    }

    /**
     * 批量新增记忆，仅最后重建一次 IDF.
     * <p>适合会话结束时一次性提取多条记忆的场景，比逐条 add 高效.
     */
    public static void addBatch(List<MemoryEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        synchronized (ENTRIES) {
            for (MemoryEntry entry : entries) {
                generateEmbedding(entry);
                ENTRIES.add(entry);
            }
            evictIfNeeded();
            BM25Scorer.addBatch(entries);
        }
    }

    /**
     * 按 ID 更新一条记忆，同时更新 embedding 并增量更新索引.
     *
     * @return true 表示找到并更新
     */
    public static boolean update(String id, MemoryEntry updated) {
        if (id == null || updated == null) return false;
        synchronized (ENTRIES) {
            for (int i = 0; i < ENTRIES.size(); i++) {
                if (id.equals(ENTRIES.get(i).getId())) {
                    updated.setId(id);
                    generateEmbedding(updated);
                    ENTRIES.set(i, updated);
                    BM25Scorer.updateEntry(id, updated);
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 按 ID 删除一条记忆，同时重建索引.
     *
     * @return true 表示找到并删除
     */
    public static boolean delete(String id) {
        if (id == null) return false;
        synchronized (ENTRIES) {
            boolean removed = ENTRIES.removeIf(e -> id.equals(e.getId()));
            if (removed) {
                BM25Scorer.removeEntry(id);
            }
            return removed;
        }
    }

    /**
     * 返回全部记忆的不可变快照.
     */
    public static List<MemoryEntry> getAll() {
        synchronized (ENTRIES) {
            return new ArrayList<>(ENTRIES);
        }
    }

    /**
     * 返回最近 N 条记忆（按创建时间倒序）.
     */
    public static List<MemoryEntry> getRecent(int n) {
        synchronized (ENTRIES) {
            return ENTRIES.stream()
                    .sorted(Comparator.comparingLong(MemoryEntry::getCreatedAt).reversed())
                    .limit(n)
                    .collect(Collectors.toList());
        }
    }

    // ==================== 检索（委托 HybridRetriever） ====================

    /**
     * 混合检索：BM25 + Embedding → 断崖截断 → Top-K.
     *
     * <p>当 {@link EmbeddingClient#isEnabled()} 为 true 时，
     * 使用 BM25 粗筛 + embedding 语义精排；否则退化为纯 BM25。
     * 结果会经过断崖检测自动截断，可能返回少于 topK 条。
     *
     * @param query 查询文本
     * @param topK  期望最大返回条数
     * @return 按相关性降序的记忆列表
     */
    public static List<MemoryEntry> search(String query, int topK) {
        if (!loaded) return Collections.emptyList();

        List<MemoryEntry> results = HybridRetriever.retrieve(query, topK);

        // 更新访问统计
        long now = System.currentTimeMillis();
        for (MemoryEntry e : results) {
            e.setLastAccessedAt(now);
            e.setAccessCount(e.getAccessCount() + 1);
        }

        return results;
    }

    // ==================== Embedding 管理 ====================

    /**
     * 为单条记忆生成 embedding.
     */
    private static void generateEmbedding(MemoryEntry entry) {
        if (!EmbeddingClient.isEnabled() || entry == null) return;

        String text = buildEmbeddingText(entry);
        if (text.isBlank()) return;

        float[] vec = EmbeddingClient.embed(text);
        if (vec != null) {
            entry.setEmbedding(vec);
        }
    }

    /**
     * 批量重建全部记忆的 embedding 向量.
     * <p>适用场景：更换 embedding 模型后、从旧版本升级后.
     *
     * @return 成功更新的条目数
     */
    public static int rebuildAllEmbeddings() {
        if (!EmbeddingClient.isEnabled()) {
            System.out.println("⚠️ Embedding 未启用，跳过重建");
            return 0;
        }

        synchronized (ENTRIES) {
            // 收集需要生成 embedding 的文本
            List<String> texts = new ArrayList<>();
            List<MemoryEntry> toUpdate = new ArrayList<>();

            for (MemoryEntry entry : ENTRIES) {
                String text = buildEmbeddingText(entry);
                if (!text.isBlank()) {
                    texts.add(text);
                    toUpdate.add(entry);
                }
            }

            if (texts.isEmpty()) return 0;

            System.out.println("🔄 正在重建 " + texts.size() + " 条 embedding...");
            float[][] vectors = EmbeddingClient.embedBatch(texts);

            int updated = 0;
            for (int i = 0; i < toUpdate.size(); i++) {
                if (vectors[i] != null) {
                    toUpdate.get(i).setEmbedding(vectors[i]);
                    updated++;
                }
            }

            System.out.println("✅ Embedding 重建完成：" + updated + "/" + texts.size());
            return updated;
        }
    }

    /**
     * 构建用于生成 embedding 的文本.
     */
    private static String buildEmbeddingText(MemoryEntry entry) {
        StringBuilder sb = new StringBuilder();
        if (entry.getContent() != null) {
            sb.append(entry.getContent());
        }
        if (entry.getKeywords() != null && !entry.getKeywords().isEmpty()) {
            sb.append(" [关键词: ");
            sb.append(String.join(", ", entry.getKeywords()));
            sb.append("]");
        }
        if (entry.getCategory() != null) {
            sb.append(" [分类: ").append(entry.getCategory()).append("]");
        }
        return sb.toString();
    }

    // ==================== 淘汰 ====================

    /**
     * 超过上限时淘汰综合评分最低的条目.
     */
    private static void evictIfNeeded() {
        while (ENTRIES.size() > maxEntries) {
            MemoryEntry worst = null;
            double worstScore = Double.MAX_VALUE;

            for (MemoryEntry e : ENTRIES) {
                double score = evictionScore(e);
                if (score < worstScore) {
                    worstScore = score;
                    worst = e;
                }
            }

            if (worst != null) {
                ENTRIES.remove(worst);
                System.out.println("🗑️ 长期记忆淘汰: " + truncate(worst.getContent(), 60));
            }
        }
    }

    private static double evictionScore(MemoryEntry entry) {
        long ageMillis = System.currentTimeMillis() - entry.getCreatedAt();
        double ageDays = ageMillis / (1000.0 * 60 * 60 * 24);

        return entry.getImportance() * 10.0
                + entry.getAccessCount() * 2.0
                - ageDays * 0.1;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
