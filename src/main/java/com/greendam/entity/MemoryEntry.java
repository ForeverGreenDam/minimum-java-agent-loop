package com.greendam.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 长期记忆条目 — 跨会话持久化的单条记忆.
 *
 * <h3>记忆分类</h3>
 * <ul>
 *   <li><b>fact</b> — 客观事实（技术栈、配置值、路径、版本号等）</li>
 *   <li><b>preference</b> — 用户偏好（风格、习惯、喜好等）</li>
 *   <li><b>decision</b> — 做出的决定（方案选择、设计决策等）</li>
 *   <li><b>context</b> — 项目上下文（当前目标、进行中的任务等）</li>
 * </ul>
 *
 * <h3>重要度</h3>
 * 1-10 分，越高越不容易被淘汰。由 {@code MemoryExtractor} 在提取时打分，
 * 后续检索和淘汰策略均以此为参考权重.
 *
 * <h3>Embedding 向量</h3>
 * {@code embedding} 字段存储语义向量（float[]），在 JSON 中序列化为 Base64 编码。
 * 当 {@code EmbeddingClient} 未启用时该字段为 null，
 * 此时 {@code HybridRetriever} 退化为纯 BM25 检索.
 *
 * <h3>来源</h3>
 * {@code source} 字段记录本条记忆产生于哪个会话，使用 {@code SessionContext.getId()}。
 * 同一个会话可以产生多条记忆.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MemoryEntry {

    /**
     * 唯一标识（UUID 前 8 位）
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString().substring(0, 8);

    /**
     * 记忆内容（自然语言）
     */
    private String content;

    /**
     * 关键词列表，用于检索匹配
     */
    private List<String> keywords;

    /**
     * 分类：fact / preference / decision / context
     */
    private String category;

    /**
     * 重要度 1-10，越高越不易被遗忘
     */
    @Builder.Default
    private int importance = 5;

    /**
     * 创建时间戳（epoch millis）
     */
    @Builder.Default
    private long createdAt = System.currentTimeMillis();

    /**
     * 最后一次被检索命中的时间戳
     */
    @Builder.Default
    private long lastAccessedAt = System.currentTimeMillis();

    /**
     * 累计被检索命中的次数
     */
    @Builder.Default
    private int accessCount = 0;

    /**
     * 来源会话 ID（{@code SessionContext.getId()}）
     */
    private String source;

    /**
     * 语义向量（float[]），内存中使用，不参与默认 JSON 序列化.
     * <p>序列化/反序列化由 {@link #getEmbeddingBase64()} / {@link #setEmbeddingBase64(String)} 处理，
     * 在 JSON 中以 Base64 编码的紧凑形式存储.
     */
    @JsonIgnore
    private transient float[] embedding;

    // ==================== Base64 序列化桥接 ====================

    /**
     * Jackson 序列化用 — 将 embedding float[] 转为 Base64 字符串.
     */
    @JsonProperty("embedding")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getEmbeddingBase64() {
        if (embedding == null || embedding.length == 0) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.allocate(embedding.length * 4);
        for (float f : embedding) {
            buf.putFloat(f);
        }
        return Base64.getEncoder().encodeToString(buf.array());
    }

    /**
     * Jackson 反序列化用 — 将 Base64 字符串还原为 float[].
     */
    @JsonProperty("embedding")
    public void setEmbeddingBase64(String b64) {
        if (b64 == null || b64.isEmpty()) {
            this.embedding = null;
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(b64);
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        this.embedding = new float[bytes.length / 4];
        for (int i = 0; i < this.embedding.length; i++) {
            this.embedding[i] = buf.getFloat();
        }
    }
}
