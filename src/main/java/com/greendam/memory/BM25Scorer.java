package com.greendam.memory;

import com.greendam.entity.MemoryEntry;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BM25 关键词打分器 — 支持增量索引更新，避免频繁全量重建.
 *
 * <h3>BM25 公式</h3>
 * <pre>
 *   score(D, Q) = Σ IDF(qi) × (f(qi,D) × (k1+1)) / (f(qi,D) + k1 × (1-b + b×|D|/avgdl))
 * </pre>
 *
 * <h3>增量策略</h3>
 * <ul>
 *   <li>增/删/改条目时只更新相关词条的 posting list，不重建整个索引</li>
 *   <li>IDF 标记为 dirty，在下次检索时懒计算</li>
 *   <li>启动时仍使用 {@link #buildIndex} 做一次性全量构建</li>
 * </ul>
 */
public final class BM25Scorer {

    /**
     * TF 饱和参数
     */
    private static final double K1 = 1.5;

    /**
     * 长度归一化参数
     */
    private static final double B = 0.75;

    /**
     * 倒排索引：term → {entryId → termFrequency}
     */
    private static final Map<String, Map<String, Integer>> INVERTED_INDEX = new HashMap<>();

    /**
     * 文档长度：entryId → docLength
     */
    private static final Map<String, Integer> DOC_LENGTHS = new HashMap<>();

    /**
     * 每条 entry 的 token 集合：entryId → Set<term>，用于删除时快速定位涉及的词条
     */
    private static final Map<String, Set<String>> ENTRY_TERMS = new HashMap<>();

    /**
     * IDF 缓存：term → idf
     */
    private static final Map<String, Double> IDF_CACHE = new HashMap<>();

    /**
     * 检索用 entry 缓存
     */
    private static final Map<String, MemoryEntry> ENTRY_CACHE = new HashMap<>();

    /**
     * 平均文档长度
     */
    private static double avgDocLength = 1.0;

    /**
     * 总文档数
     */
    private static int totalDocs = 0;

    /**
     * IDF 是否需要重新计算
     */
    private static boolean idfDirty = false;

    private BM25Scorer() {
    }

    // ==================== 全量构建（启动时使用） ====================

    /**
     * 从记忆条目列表全量重建索引.
     */
    public static void buildIndex(List<MemoryEntry> entries) {
        INVERTED_INDEX.clear();
        DOC_LENGTHS.clear();
        ENTRY_TERMS.clear();
        IDF_CACHE.clear();
        ENTRY_CACHE.clear();
        idfDirty = false;

        if (entries == null || entries.isEmpty()) {
            totalDocs = 0;
            avgDocLength = 1.0;
            return;
        }

        totalDocs = entries.size();
        long totalLength = 0;

        for (MemoryEntry entry : entries) {
            String id = entry.getId();
            ENTRY_CACHE.put(id, entry);
            List<String> tokens = tokenize(buildDocText(entry));
            DOC_LENGTHS.put(id, tokens.size());
            totalLength += tokens.size();

            Set<String> termSet = new HashSet<>();
            Map<String, Integer> termFreq = new HashMap<>();
            for (String token : tokens) {
                termFreq.merge(token, 1, Integer::sum);
                termSet.add(token);
            }
            ENTRY_TERMS.put(id, termSet);

            for (Map.Entry<String, Integer> tf : termFreq.entrySet()) {
                INVERTED_INDEX
                        .computeIfAbsent(tf.getKey(), k -> new HashMap<>())
                        .put(id, tf.getValue());
            }
        }

        avgDocLength = (double) totalLength / totalDocs;
        rebuildIDF();
    }

    // ==================== 增量更新 ====================

    /**
     * 增量添加一条记忆.
     */
    public static void addEntry(MemoryEntry entry) {
        if (entry == null) return;
        String id = entry.getId();

        // 如果 ID 已存在，先移除旧的
        if (ENTRY_CACHE.containsKey(id)) {
            removeEntry(id);
        }

        ENTRY_CACHE.put(id, entry);
        List<String> tokens = tokenize(buildDocText(entry));
        DOC_LENGTHS.put(id, tokens.size());
        totalDocs++;

        // 更新平均文档长度
        long totalLen = (long) ((totalDocs - 1) * avgDocLength) + tokens.size();
        avgDocLength = (double) totalLen / totalDocs;

        // 统计词频
        Set<String> termSet = new HashSet<>();
        Map<String, Integer> termFreq = new HashMap<>();
        for (String token : tokens) {
            termFreq.merge(token, 1, Integer::sum);
            termSet.add(token);
        }
        ENTRY_TERMS.put(id, termSet);

        // 更新倒排索引
        for (Map.Entry<String, Integer> tf : termFreq.entrySet()) {
            INVERTED_INDEX
                    .computeIfAbsent(tf.getKey(), k -> new HashMap<>())
                    .put(id, tf.getValue());
        }

        idfDirty = true;
    }

    /**
     * 增量删除一条记忆.
     */
    public static void removeEntry(String id) {
        if (id == null || !ENTRY_CACHE.containsKey(id)) return;

        // 从倒排索引中移除该文档的 posting
        Set<String> terms = ENTRY_TERMS.remove(id);
        if (terms != null) {
            for (String term : terms) {
                Map<String, Integer> postings = INVERTED_INDEX.get(term);
                if (postings != null) {
                    postings.remove(id);
                    if (postings.isEmpty()) {
                        INVERTED_INDEX.remove(term);
                    }
                }
            }
        }

        // 更新文档长度统计
        Integer docLen = DOC_LENGTHS.remove(id);
        if (docLen != null && totalDocs > 1) {
            long totalLen = (long) (totalDocs * avgDocLength) - docLen;
            avgDocLength = (double) totalLen / (totalDocs - 1);
        }

        ENTRY_CACHE.remove(id);
        totalDocs--;
        idfDirty = true;
    }

    /**
     * 增量更新一条记忆（先删后增）.
     */
    public static void updateEntry(String oldId, MemoryEntry newEntry) {
        if (oldId == null || newEntry == null) return;
        removeEntry(oldId);
        newEntry.setId(oldId); // 保持 ID 不变
        addEntry(newEntry);
    }

    // ==================== 批量操作 ====================

    /**
     * 批量添加记忆，全部添加完成后仅重建一次 IDF.
     * <p>比逐条 add 快 O(N) 倍，适合会话结束时的批量写入.
     */
    public static void addBatch(List<MemoryEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        for (MemoryEntry entry : entries) {
            addEntry(entry);
        }
        // 批量结束后统一重建 IDF
        rebuildIDF();
        idfDirty = false;
    }

    // ==================== 检索 ====================

    /**
     * 检索，返回按 BM25 分数降序排列的结果.
     */
    public static List<ScoredEntry> search(String query, int topK) {
        if (query == null || query.isBlank() || totalDocs == 0) {
            return Collections.emptyList();
        }

        // 懒计算 IDF
        if (idfDirty) {
            rebuildIDF();
            idfDirty = false;
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Double> docScores = new HashMap<>();

        for (String token : queryTokens) {
            Map<String, Integer> postings = INVERTED_INDEX.get(token);
            if (postings == null) continue;

            double idf = IDF_CACHE.getOrDefault(token, 0.0);
            if (idf <= 0) continue;

            for (Map.Entry<String, Integer> posting : postings.entrySet()) {
                String docId = posting.getKey();
                int tf = posting.getValue();
                int docLen = DOC_LENGTHS.getOrDefault(docId, 1);

                double numerator = tf * (K1 + 1.0);
                double denominator = tf + K1 * (1.0 - B + B * docLen / avgDocLength);
                docScores.merge(docId, idf * numerator / denominator, Double::sum);
            }
        }

        return docScores.entrySet().stream()
                .map(e -> new ScoredEntry(ENTRY_CACHE.get(e.getKey()), e.getValue()))
                .filter(se -> se.entry != null)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .collect(Collectors.toList());
    }

    // ==================== 分词 ====================

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCJK(c)) {
                if (buf.length() > 0) {
                    tokens.add(buf.toString().toLowerCase());
                    buf.setLength(0);
                }
                tokens.add(String.valueOf(c));
                if (i + 1 < text.length() && isCJK(text.charAt(i + 1))) {
                    tokens.add(text.substring(i, i + 2));
                }
            } else if (Character.isLetterOrDigit(c)) {
                buf.append(c);
            } else {
                if (buf.length() > 0) {
                    tokens.add(buf.toString().toLowerCase());
                    buf.setLength(0);
                }
            }
        }
        if (buf.length() > 0) {
            tokens.add(buf.toString().toLowerCase());
        }

        return tokens;
    }

    private static boolean isCJK(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0xF900 && c <= 0xFAFF);
    }

    // ==================== 内部方法 ====================

    private static String buildDocText(MemoryEntry entry) {
        StringBuilder sb = new StringBuilder();
        if (entry.getContent() != null) {
            sb.append(entry.getContent());
        }
        if (entry.getKeywords() != null) {
            for (String kw : entry.getKeywords()) {
                sb.append(' ').append(kw);
            }
        }
        return sb.toString();
    }

    private static void rebuildIDF() {
        IDF_CACHE.clear();
        for (String term : INVERTED_INDEX.keySet()) {
            int docFreq = INVERTED_INDEX.get(term).size();
            double idf = Math.log((totalDocs - docFreq + 0.5) / (docFreq + 0.5) + 1.0);
            IDF_CACHE.put(term, idf);
        }
    }

    public static boolean isEmpty() {
        return totalDocs == 0;
    }

    public static int getTotalDocs() {
        return totalDocs;
    }

    // ==================== 结果类型 ====================

    public record ScoredEntry(MemoryEntry entry, double score) {
    }
}
