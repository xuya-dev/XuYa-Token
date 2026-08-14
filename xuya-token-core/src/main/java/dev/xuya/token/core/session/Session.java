package dev.xuya.token.core.session;

import java.time.Instant;

/**
 * 绑定到不透明 token 的已认证会话。
 *
 * @author 青衣
 */
public class Session {

    /** 会话令牌。 */
    private final String token;

    /** 会话所属用户 ID。 */
    private final String userId;

    /** 会话创建时间。 */
    private final Instant createdAt;

    /** 最近一次活跃时间。 */
    private volatile Instant lastAccessedAt;

    /** 空闲超时时间,单位毫秒。 */
    private final long timeoutMillis;

    /**
     * 构造会话。
     *
     * @param token          会话令牌
     * @param userId         用户 ID
     * @param createdAt      创建时间
     * @param timeoutMillis  空闲超时时间(毫秒)
     */
    public Session(String token, String userId, Instant createdAt, long timeoutMillis) {
        this.token = token;
        this.userId = userId;
        this.createdAt = createdAt;
        this.lastAccessedAt = createdAt;
        this.timeoutMillis = timeoutMillis;
    }

    /** 获取会话令牌。 */
    public String getToken() {
        return token;
    }

    /** 获取用户 ID。 */
    public String getUserId() {
        return userId;
    }

    /** 获取创建时间。 */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 获取最近一次活跃时间。 */
    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    /** 获取空闲超时时间,单位毫秒。 */
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * 判断会话在给定时刻是否已空闲超时。
     *
     * @param now 当前时刻
     * @return 已超时返回 true
     */
    public boolean isExpired(Instant now) {
        return now.toEpochMilli() - lastAccessedAt.toEpochMilli() > timeoutMillis;
    }

    /** 刷新最近活跃时间为当前时刻。 */
    public void touch() {
        this.lastAccessedAt = Instant.now();
    }
}
