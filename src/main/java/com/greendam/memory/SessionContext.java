package com.greendam.memory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 会话上下文 — 当前程序实例的统一标识.
 *
 * <p>在程序启动时自动生成会话 ID（基于启动时间戳），
 * 供 {@link ConversationLogger}、{@link LongMemoryStore} 等组件使用。
 *
 * <h3>会话 ID 格式</h3>
 * {@code yyyy-MM-dd_HH-mm-ss}，例如 {@code 2026-06-29_17-30-00}。
 * 与 {@link ConversationLogger} 的日志文件命名保持一致。
 */
public final class SessionContext {

    /**
     * 会话 ID 的时间格式
     */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * 当前会话的唯一标识
     */
    private static final String SESSION_ID;

    /**
     * 会话启动时间
     */
    private static final LocalDateTime STARTED_AT;

    static {
        STARTED_AT = LocalDateTime.now();
        SESSION_ID = STARTED_AT.format(FMT);
    }

    private SessionContext() {
    }

    /**
     * 当前会话的唯一标识.
     */
    public static String getId() {
        return SESSION_ID;
    }

    /**
     * 会话启动时间.
     */
    public static LocalDateTime getStartedAt() {
        return STARTED_AT;
    }
}
