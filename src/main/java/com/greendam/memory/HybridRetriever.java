package com.greendam.memory;

import com.greendam.entity.MemoryEntry;
import com.greendam.util.EmbeddingClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 混合检索器 — BM25 关键词 + Embedding 语义，带断崖截断.
 *
 * <h3>检索流程</h3>
 * <pre>
 *   用户查询
 *     │
 *     ├─ ① 生成 query embedding（如 EmbeddingClient 启用）
 *     │
 *     ├─ ② BM25 粗筛 → Top-{@code prefetchK} 候选
 *     │
 *     ├─ ③ 混合打分：
 *     │      hybrid = α × BM25_norm + (1-α) × cosine_sim
 *     │      （BM25 分数做 min-max 归一化，cosine 已天然在 [0,1]）
 *     │
 *     ├─ ④ 按混合分降序排序
 *     │
 *     ├─ ⑤ 断崖检测：
 *     │      阈值 = 平均正向跌幅 × cliffMultiplier
 *     │      找到第一个跌幅超过阈值的断崖点，截断
 *     │
 *     └─ ⑥ 返回截断后的 Top-K
 * </pre>
 *
 * <h3>降级策略</h3>
 * 当 {@link EmbeddingClient#isEnabled()} 为 false 时，
 * 跳过 embedding 步骤，仅使用 BM25 评分 + 断崖检测。
 */
public final class HybridRetriever {

    /**
     * BM25 在混合评分中的权重 (0~1)，剩余给 embedding
     */
    private static double bm25Weight = 0.3;

    /**
     * 断崖检测乘数
     */
    private static double cliffMultiplier = 2.0;

    /**
     * BM25 粗筛候选数
     */
    private static int prefetchK = 50;

    private HybridRetriever() {
    }

    // ==================== 配置 ====================

    public static void configure(double bm25Weight, double cliffMultiplier, int prefetchK) {
        HybridRetriever.bm25Weight = clamp(bm25Weight, 0.0, 1.0);
        HybridRetriever.cliffMultiplier = Math.max(1.0, cliffMultiplier);
        HybridRetriever.prefetchK = Math.max(10, prefetchK);
    }

    public static double getBm25Weight() {
        return bm25Weight;
    }

    public static double getCliffMultiplier() {
        return cliffMultiplier;
    }

    // ==================== 检索入口 ====================

    /**
     * 混合检索：BM25 + Embedding → 断崖截断 → Top-K.
     *
     * @param query 用户查询文本
     * @param topK  期望返回的最大条数
     * @return 按相关性降序的记忆列表（可能因断崖检测少于 topK）
     */
    public static List<MemoryEntry> retrieve(String query, int topK) {
        if (query == null || query.isBlank() || BM25Scorer.isEmpty()) {
            return Collections.emptyList();
        }

        // ── ① 生成 query embedding ──
        float[] queryEmbedding = null;
        if (EmbeddingClient.isEnabled()) {
            queryEmbedding = EmbeddingClient.embed(query);
        }

        // ── ② BM25 粗筛候选集 ──
        List<BM25Scorer.ScoredEntry> bm25Candidates = BM25Scorer.search(query, prefetchK);
        if (bm25Candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // ── ③ 提取候选记忆 ──
        List<MemoryEntry> candidates = new ArrayList<>();
        List<Double> bm25Scores = new ArrayList<>();
        for (BM25Scorer.ScoredEntry se : bm25Candidates) {
            candidates.add(se.entry());
            bm25Scores.add(se.score());
        }

        // ── ④ 归一化 BM25 分数 ──
        double[] bm25Norm = normalize(bm25Scores);

        // ── ⑤ 计算混合分数 ──
        List<ScoredEntry> hybrid = computeHybridScores(candidates, bm25Norm, queryEmbedding);

        // ── ⑥ 排序 ──
        hybrid.sort((a, b) -> Double.compare(b.score, a.score));

        // ── ⑦ 断崖检测 + 截断 ──
        return applyCliffDetection(hybrid, topK);
    }

    // ==================== 内部算法 ====================

    /**
     * Min-max 归一化到 [0, 1].
     */
    private static double[] normalize(List<Double> scores) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (double s : scores) {
            if (s < min) min = s;
            if (s > max) max = s;
        }

        double[] result = new double[scores.size()];
        double range = max - min;
        if (range <= 0) {
            // 所有分数相同 → 全部给 0.5
            for (int i = 0; i < result.length; i++) {
                result[i] = 0.5;
            }
        } else {
            for (int i = 0; i < result.length; i++) {
                result[i] = (scores.get(i) - min) / range;
            }
        }
        return result;
    }

    /**
     * 计算混合分数：α × BM25_norm + (1-α) × cosine.
     *
     * <p>预计算 query 向量的模，循环中复用，避免重复开方.
     */
    private static List<ScoredEntry> computeHybridScores(
            List<MemoryEntry> candidates,
            double[] bm25Norm,
            float[] queryEmbedding) {

        List<ScoredEntry> results = new ArrayList<>();
        boolean useEmbedding = queryEmbedding != null;

        // 预计算 query 向量的 |a|² 和 |a|
        double queryNormSq = 0;
        double queryNorm = 0;
        if (useEmbedding) {
            for (float v : queryEmbedding) {
                queryNormSq += (double) v * v;
            }
            queryNorm = Math.sqrt(queryNormSq);
        }

        for (int i = 0; i < candidates.size(); i++) {
            MemoryEntry entry = candidates.get(i);
            double embeddingScore = 0.0;

            if (useEmbedding && entry.getEmbedding() != null) {
                // 批量友好的余弦计算：query 的 |a| 已预计算好
                embeddingScore = cosineSimilarityFast(
                        queryEmbedding, queryNorm, queryNormSq,
                        entry.getEmbedding());
                if (embeddingScore < 0) embeddingScore = 0;
            }

            double hybrid = bm25Weight * bm25Norm[i]
                    + (1.0 - bm25Weight) * embeddingScore;

            results.add(new ScoredEntry(entry, hybrid));
        }

        return results;
    }

    /**
     * 余弦相似度 — 通用版本（单次调用时使用）.
     */
    static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0, normASq = 0, normBSq = 0;
        for (int i = 0; i < a.length; i++) {
            double av = a[i];
            double bv = b[i];
            dot += av * bv;
            normASq += av * av;
            normBSq += bv * bv;
        }
        if (normASq == 0 || normBSq == 0) return 0.0;
        return dot / (Math.sqrt(normASq) * Math.sqrt(normBSq));
    }

    /**
     * 余弦相似度 — 批量友好版本.
     * <p>query 的 |a| 和 |a|² 由调用方预计算并传入，避免每条候选都重算.
     *
     * @param queryVec     query 向量
     * @param queryNorm    query 向量的模（√Σa²）
     * @param queryNormSq  query 向量的模方（Σa²）
     * @param candidateVec 候选向量
     * @return 余弦相似度，候选向量为 null 或零向量时返回 0
     */
    static double cosineSimilarityFast(float[] queryVec, double queryNorm, double queryNormSq,
                                       float[] candidateVec) {
        if (candidateVec == null || candidateVec.length != queryVec.length) {
            return 0.0;
        }
        if (queryNormSq == 0) return 0.0;

        double dot = 0, normBSq = 0;
        for (int i = 0; i < candidateVec.length; i++) {
            double cv = candidateVec[i];
            dot += (double) queryVec[i] * cv;
            normBSq += cv * cv;
        }
        if (normBSq == 0) return 0.0;
        return dot / (queryNorm * Math.sqrt(normBSq));
    }

    // ==================== 断崖检测 ====================

    /**
     * 对已排序的分数序列做断崖检测，在第一个显著跌幅处截断.
     *
     * <h3>算法</h3>
     * <pre>
     *   1. 计算相邻分数间的跌幅 d[i] = s[i] - s[i+1]
     *   2. 计算正向跌幅的均值 avgDrop
     *   3. 阈值 = avgDrop × cliffMultiplier
     *   4. 找到第一个 d[i] > 阈值 的位置，在 i+1 处截断
     *   5. 若没有断崖，返回全部（不超过 maxK）
     * </pre>
     *
     * @param sorted 按分数降序排列的条目列表
     * @param maxK   最大返回数
     * @return 截断后的条目列表
     */
    static List<MemoryEntry> applyCliffDetection(List<ScoredEntry> sorted, int maxK) {
        if (sorted.size() <= 1) {
            return extractEntries(sorted, Math.min(maxK, sorted.size()));
        }

        // 计算相邻跌幅
        int n = sorted.size();
        double[] drops = new double[n - 1];
        double sumDrop = 0;
        int dropCount = 0;

        for (int i = 0; i < drops.length; i++) {
            drops[i] = sorted.get(i).score - sorted.get(i + 1).score;
            if (drops[i] > 0) {
                sumDrop += drops[i];
                dropCount++;
            }
        }

        // 没有正向跌幅 → 全部返回（不超过 maxK）
        if (dropCount == 0) {
            return extractEntries(sorted, Math.min(maxK, n));
        }

        double avgDrop = sumDrop / dropCount;
        double threshold = avgDrop * cliffMultiplier;

        // 找到第一个断崖
        int cutoff = n; // 默认全部保留
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] > threshold) {
                cutoff = i + 1; // 保留到当前项（含），截断后面的
                break;
            }
        }

        int resultSize = Math.min(cutoff, maxK);

        if (resultSize < maxK && resultSize < n) {
            System.out.println("🔻 断崖检测：在 " + resultSize + "/" + n
                    + " 处截断（阈值=" + String.format("%.4f", threshold)
                    + "，平均跌幅=" + String.format("%.4f", avgDrop) + "）");
        }

        return extractEntries(sorted, resultSize);
    }

    private static List<MemoryEntry> extractEntries(List<ScoredEntry> list, int count) {
        List<MemoryEntry> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(list.get(i).entry);
        }
        return result;
    }

    // ==================== 工具 ====================

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== 结果类型 ====================

    private record ScoredEntry(MemoryEntry entry, double score) {
    }
}
