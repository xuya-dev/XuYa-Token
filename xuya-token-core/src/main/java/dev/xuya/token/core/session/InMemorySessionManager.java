package dev.xuya.token.core.session;

import dev.xuya.token.core.exception.ForbiddenException;
import dev.xuya.token.core.model.UserType;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的内存版 {@link SessionManager},带空闲超时过期,
 * 支持单用户并发会话数限制:超限时踢掉最旧会话或拒绝新登录。
 * <p>多体系:各体系会话空间完全隔离 —— token 前缀为体系标识、
 * 索引键含体系、超时与并发数可按体系用 {@link UserTypeSettings} 覆盖全局默认,
 * 体系数量不限(B / C / OPEN / MINI / …)。
 *
 * @author 青衣
 */
public class InMemorySessionManager implements SessionManager {

    /** token 生成的安全随机数源。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 全局默认空闲超时时间,单位毫秒。 */
    private final long defaultTimeoutMillis;

    /** 全局默认单用户最大并发会话数,<=0 表示不限制。 */
    private final int defaultMaxSessionsPerUser;

    /** 超出并发上限时是否踢掉最旧会话;false 表示拒绝新登录。 */
    private final boolean evictOldestOnExceed;

    /** 体系标识 → 该体系的策略覆盖(未声明的体系沿用全局默认)。 */
    private final Map<String, UserTypeSettings> settingsByUserType;

    /** token → 会话 的存储。 */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** 体系:用户 ID → 该用户当前 token 集合 的索引(支持踢人与在线列表)。 */
    private final Map<String, Set<String>> userTokens = new ConcurrentHashMap<>();

    /** 自动清扫节流:每 N 次 create 触发一次 sweep,防惰性清理遗漏导致的泄漏。 */
    private static final int SWEEP_EVERY_CREATES = 1024;

    /** create 计数,用于自动清扫节流。 */
    private final java.util.concurrent.atomic.AtomicInteger createCount =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * 构造内存会话管理器,全部体系沿用同一超时且不限并发会话数。
     *
     * @param timeoutMillis 空闲超时时间(毫秒)
     */
    public InMemorySessionManager(long timeoutMillis) {
        this(timeoutMillis, 0, true, Map.of());
    }

    /**
     * 构造内存会话管理器,全部体系沿用同一策略。
     *
     * @param timeoutMillis       空闲超时时间(毫秒)
     * @param maxSessionsPerUser  单用户最大并发会话数,<=0 表示不限制
     * @param evictOldestOnExceed 超限时踢掉最旧会话(true)或拒绝新登录(false)
     */
    public InMemorySessionManager(long timeoutMillis, int maxSessionsPerUser, boolean evictOldestOnExceed) {
        this(timeoutMillis, maxSessionsPerUser, evictOldestOnExceed, Map.of());
    }

    /**
     * 构造内存会话管理器(完整参数,支持按体系覆盖策略)。
     *
     * @param defaultTimeoutMillis      全局默认空闲超时(毫秒)
     * @param defaultMaxSessionsPerUser 全局默认最大并发会话数,<=0 表示不限制
     * @param evictOldestOnExceed       超限时踢掉最旧会话(true)或拒绝新登录(false)
     * @param settingsByUserType        体系 → 策略覆盖,可为 null(全部沿用全局)
     */
    public InMemorySessionManager(long defaultTimeoutMillis, int defaultMaxSessionsPerUser,
                                  boolean evictOldestOnExceed,
                                  Map<String, UserTypeSettings> settingsByUserType) {
        this.defaultTimeoutMillis = defaultTimeoutMillis;
        this.defaultMaxSessionsPerUser = defaultMaxSessionsPerUser;
        this.evictOldestOnExceed = evictOldestOnExceed;
        this.settingsByUserType = settingsByUserType == null ? Map.of() : settingsByUserType;
    }

    /** 为默认体系用户创建会话,委托给 {@link #create(String, String)}。 */
    @Override
    public Session create(String userId) {
        return create(UserType.DEFAULT, userId);
    }

    /**
     * 为指定体系用户创建会话,token 形如 {@code {体系}-{随机}};
     * 超时与并发数按体系策略(无覆盖则用全局),并发限制按体系独立计算,
     * 超限按策略踢最旧或拒绝。
     */
    @Override
    public Session create(String userType, String userId) {
        String type = UserType.normalize(userType);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = type + "-"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session(token, userId, Instant.now(), timeoutOf(type), type);
        sessions.put(token, session);
        String indexKey = indexKey(type, userId);
        Set<String> tokens = userTokens.computeIfAbsent(indexKey, k -> ConcurrentHashMap.newKeySet());
        tokens.add(token);
        if (maxSessionsOf(type) > 0 && !enforceLimit(tokens, token)) {
            // 被拒绝登录:回滚刚写入的会话与索引
            sessions.remove(token);
            tokens.remove(token);
            throw new ForbiddenException("Concurrent login limit reached for user: " + userId);
        }
        if (createCount.incrementAndGet() % SWEEP_EVERY_CREATES == 0) {
            sweep();
        }
        return session;
    }

    /**
     * 主动清扫全部过期会话并清理空索引,返回清除的会话数。
     * <p>过期会话通常在命中时被惰性清理,但永不再次访问的会话
     * (用户关闭页面不再回来)只有靠周期清扫回收,长期运行的服务
     * 应定期调用本方法(或依赖 create 节流触发的自动清扫)防止泄漏。
     */
    public int sweep() {
        Instant now = Instant.now();
        int removed = 0;
        for (Session session : sessions.values()) {
            if (session.isExpired(now) && sessions.remove(session.getToken()) != null) {
                Set<String> tokens = userTokens.get(indexKey(session.getUserType(), session.getUserId()));
                if (tokens != null) {
                    tokens.remove(session.getToken());
                }
                removed++;
            }
        }
        // 顺带清理空索引(避免 userTokens 膨胀)
        userTokens.values().removeIf(Set::isEmpty);
        return removed;
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
            Set<String> tokens = userTokens.get(indexKey(session.getUserType(), session.getUserId()));
            if (tokens != null) {
                tokens.remove(token);
            }
        }
    }

    /** 注销该用户在全部体系的会话。 */
    @Override
    public void invalidateByUserId(String userId) {
        userTokens.keySet().stream()
                .filter(key -> key.endsWith(":" + userId))
                .collect(java.util.stream.Collectors.toSet())
                .forEach(indexKey -> {
                    Set<String> tokens = userTokens.remove(indexKey);
                    if (tokens != null) {
                        tokens.forEach(sessions::remove);
                    }
                });
    }

    /** 注销该用户在指定体系内的会话;其他体系的登录态不受影响。 */
    @Override
    public void invalidateByUserId(String userType, String userId) {
        Set<String> tokens = userTokens.remove(indexKey(UserType.normalize(userType), userId));
        if (tokens != null) {
            tokens.forEach(sessions::remove);
        }
    }

    /** 该用户当前未过期的 token 集合(跨全部体系)。 */
    @Override
    public Set<String> listActiveTokens(String userId) {
        Instant now = Instant.now();
        Set<String> result = new HashSet<>();
        userTokens.forEach((indexKey, tokens) -> {
            if (!indexKey.endsWith(":" + userId)) {
                return;
            }
            for (String token : tokens) {
                Session session = sessions.get(token);
                if (session != null && !session.isExpired(now)) {
                    result.add(token);
                }
            }
        });
        return Set.copyOf(result);
    }

    /** 按体系取空闲超时:有覆盖用覆盖值,否则用全局默认。 */
    private long timeoutOf(String userType) {
        UserTypeSettings settings = settingsByUserType.get(userType);
        return settings != null && settings.getTimeoutMillis() != null
                ? settings.getTimeoutMillis() : defaultTimeoutMillis;
    }

    /** 按体系取最大并发会话数:有覆盖用覆盖值,否则用全局默认。 */
    private int maxSessionsOf(String userType) {
        UserTypeSettings settings = settingsByUserType.get(userType);
        return settings != null && settings.getMaxSessionsPerUser() != null
                ? settings.getMaxSessionsPerUser() : defaultMaxSessionsPerUser;
    }

    /** 体系 + 用户 ID 的索引键。 */
    private static String indexKey(String userType, String userId) {
        return UserType.normalize(userType) + ":" + userId;
    }

    /**
     * 执行并发限制策略:先剔除索引中的失效 token,仍超限时按配置
     * 踢掉最旧会话或返回 false 拒绝新会话。
     */
    private boolean enforceLimit(Set<String> tokens, String newToken) {
        Instant now = Instant.now();
        int maxSessionsPerUser = maxSessionsOf(sessionTypeOf(newToken));
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

    /** 从新写入的 token 中取体系标识。 */
    private String sessionTypeOf(String token) {
        int idx = token.indexOf('-');
        return idx > 0 ? token.substring(0, idx) : UserType.DEFAULT;
    }

    /** 从主存储与用户索引中同时移除会话。 */
    private void removeSession(String token, Session session) {
        sessions.remove(token);
        Set<String> tokens = userTokens.get(indexKey(session.getUserType(), session.getUserId()));
        if (tokens != null) {
            tokens.remove(token);
        }
    }
}
