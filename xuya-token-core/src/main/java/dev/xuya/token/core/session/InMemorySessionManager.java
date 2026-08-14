package dev.xuya.token.core.session;

import dev.xuya.token.core.exception.ForbiddenException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的内存版 {@link SessionManager},带空闲超时过期,
 * 并支持单用户并发会话数限制:超限时踢掉最旧会话或拒绝新登录。
 *
 * @author 青衣
 */
public class InMemorySessionManager implements SessionManager {

    /** token 生成的安全随机数源。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 空闲超时时间,单位毫秒。 */
    private final long timeoutMillis;

    /** 单用户最大并发会话数,<=0 表示不限制。 */
    private final int maxSessionsPerUser;

    /** 超出并发上限时是否踢掉最旧会话;false 表示拒绝新登录。 */
    private final boolean evictOldestOnExceed;

    /** token → 会话 的存储。 */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** 用户 ID → 该用户当前 token 集合 的索引(支持踢人与在线列表)。 */
    private final Map<String, Set<String>> userTokens = new ConcurrentHashMap<>();

    /**
     * 构造内存会话管理器,不限制并发会话数。
     *
     * @param timeoutMillis 空闲超时时间(毫秒)
     */
    public InMemorySessionManager(long timeoutMillis) {
        this(timeoutMillis, 0, true);
    }

    /**
     * 构造内存会话管理器。
     *
     * @param timeoutMillis       空闲超时时间(毫秒)
     * @param maxSessionsPerUser  单用户最大并发会话数,<=0 表示不限制
     * @param evictOldestOnExceed 超限时踢掉最旧会话(true)或拒绝新登录(false)
     */
    public InMemorySessionManager(long timeoutMillis, int maxSessionsPerUser, boolean evictOldestOnExceed) {
        this.timeoutMillis = timeoutMillis;
        this.maxSessionsPerUser = maxSessionsPerUser;
        this.evictOldestOnExceed = evictOldestOnExceed;
    }

    /**
     * 为用户创建新会话;启用并发限制时先清理失效索引,
     * 超限按策略踢最旧会话或抛出 {@code ForbiddenException} 拒绝。
     */
    @Override
    public Session create(String userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session(token, userId, Instant.now(), timeoutMillis);
        sessions.put(token, session);
        Set<String> tokens = userTokens.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        tokens.add(token);
        if (maxSessionsPerUser > 0 && !enforceLimit(userId, tokens, token)) {
            // 被拒绝登录:回滚刚写入的会话与索引
            sessions.remove(token);
            tokens.remove(token);
            throw new ForbiddenException("Concurrent login limit reached for user: " + userId);
        }
        return session;
    }

    /** 按 token 解析会话;不存在或已超时返回 {@code null},命中时刷新活跃时间并顺带清理索引。 */
    @Override
    public Session get(String token) {
        if (token == null) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.isExpired(Instant.now())) {
            removeSession(token, session);
            return null;
        }
        session.touch();
        return session;
    }

    /** 按 token 移除会话,并同步清理用户索引。 */
    @Override
    public void invalidate(String token) {
        if (token == null) {
            return;
        }
        Session session = sessions.remove(token);
        if (session != null) {
            Set<String> tokens = userTokens.get(session.getUserId());
            if (tokens != null) {
                tokens.remove(token);
            }
        }
    }

    /** 注销该用户的全部会话(踢人下线)。 */
    @Override
    public void invalidateByUserId(String userId) {
        Set<String> tokens = userTokens.remove(userId);
        if (tokens != null) {
            tokens.forEach(sessions::remove);
        }
    }

    /** 该用户当前未过期的 token 集合(在线列表)。 */
    @Override
    public Set<String> listActiveTokens(String userId) {
        Set<String> tokens = userTokens.get(userId);
        if (tokens == null) {
            return Set.of();
        }
        Instant now = Instant.now();
        Set<String> result = new HashSet<>();
        for (String token : tokens) {
            Session session = sessions.get(token);
            if (session != null && !session.isExpired(now)) {
                result.add(token);
            }
        }
        return Set.copyOf(result);
    }

    /**
     * 执行并发限制策略:先剔除索引中的失效 token,仍超限时按配置
     * 踢掉最旧会话或返回 false 拒绝新会话。
     */
    private boolean enforceLimit(String userId, Set<String> tokens, String newToken) {
        Instant now = Instant.now();
        tokens.removeIf(token -> {
            Session session = sessions.get(token);
            return session == null || session.isExpired(now);
        });
        while (tokens.size() > maxSessionsPerUser) {
            if (!evictOldestOnExceed) {
                return false;
            }
            String oldest = null;
            Instant oldestAt = null;
            for (String token : tokens) {
                Session session = sessions.get(token);
                if (session == null) {
                    continue;
                }
                if (oldestAt == null || session.getCreatedAt().isBefore(oldestAt)) {
                    oldestAt = session.getCreatedAt();
                    oldest = token;
                }
            }
            if (oldest == null) {
                break;
            }
            sessions.remove(oldest);
            tokens.remove(oldest);
        }
        return true;
    }

    /** 从主存储与用户索引中同时移除会话。 */
    private void removeSession(String token, Session session) {
        sessions.remove(token);
        Set<String> tokens = userTokens.get(session.getUserId());
        if (tokens != null) {
            tokens.remove(token);
        }
    }
}
