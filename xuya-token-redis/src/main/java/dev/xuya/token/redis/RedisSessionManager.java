package dev.xuya.token.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.token.core.exception.ForbiddenException;
import dev.xuya.token.core.exception.XuYaTokenException;
import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.session.Session;
import dev.xuya.token.core.session.SessionManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * 基于 Redis 的分布式 {@link SessionManager},多节点部署时共享会话。
 * <p>过期语义:会话以 Redis TTL 承载空闲超时,每次命中刷新 TTL。
 * <p>key 结构(体系标识为开放字符串,体系数量不限):
 * <ul>
 *   <li>会话:{@code {prefix}{体系}-{token随机}}</li>
 *   <li>体系用户索引:{@code {prefix}user:{体系}:{userId}}(集合)</li>
 *   <li>用户体系登记:{@code {prefix}realms:{userId}}(集合,记录该用户出现过的体系)</li>
 * </ul>
 * 并发会话限制与踢人按体系独立计算;全部体系共用同一空闲超时配置。
 *
 * @author 青衣
 */
public class RedisSessionManager implements SessionManager {

    /** token 生成的安全随机数源。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Redis 操作模板。 */
    private final StringRedisTemplate redisTemplate;

    /** JSON 序列化器。 */
    private final ObjectMapper objectMapper;

    /** Redis key 前缀,如 "xuya:token:"。 */
    private final String keyPrefix;

    /** 空闲超时时间,单位毫秒(全部体系共用)。 */
    private final long timeoutMillis;

    /** 单用户最大并发会话数(按体系独立计),<=0 表示不限制。 */
    private final int maxSessionsPerUser;

    /** 超出并发上限时是否踢掉最旧会话;false 表示拒绝新登录。 */
    private final boolean evictOldestOnExceed;

    /**
     * 构造 Redis 会话管理器,使用默认前缀且不限并发会话数。
     *
     * @param redisTemplate Redis 操作模板
     * @param timeoutMillis 空闲超时时间(毫秒),即 Redis key 的 TTL
     */
    public RedisSessionManager(StringRedisTemplate redisTemplate, long timeoutMillis) {
        this(redisTemplate, new ObjectMapper(), "xuya:token:", timeoutMillis, 0, true);
    }

    /**
     * 构造 Redis 会话管理器。
     *
     * @param redisTemplate       Redis 操作模板
     * @param objectMapper        JSON 序列化器
     * @param keyPrefix           Redis key 前缀
     * @param timeoutMillis       空闲超时时间(毫秒)
     * @param maxSessionsPerUser  单用户最大并发会话数(按体系独立计),<=0 表示不限制
     * @param evictOldestOnExceed 超限时踢掉最旧会话(true)或拒绝新登录(false)
     */
    public RedisSessionManager(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                               String keyPrefix, long timeoutMillis,
                               int maxSessionsPerUser, boolean evictOldestOnExceed) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.timeoutMillis = timeoutMillis;
        this.maxSessionsPerUser = maxSessionsPerUser;
        this.evictOldestOnExceed = evictOldestOnExceed;
    }

    /** 为默认体系用户创建会话,委托给 {@link #create(String, String)}。 */
    @Override
    public Session create(String userId) {
        return create(UserType.DEFAULT, userId);
    }

    /**
     * 为指定体系用户创建会话并写入体系用户索引;并发限制按体系独立计算,
     * 超限按策略踢最旧会话或抛出 {@code ForbiddenException} 拒绝。
     */
    @Override
    public Session create(String userType, String userId) {
        String type = UserType.normalize(userType);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = type + "-"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        SessionData data = new SessionData(token, userId, type, now.toEpochMilli(), timeoutMillis);
        String userKey = userKey(type, userId);
        try {
            redisTemplate.opsForValue().set(key(token), objectMapper.writeValueAsString(data),
                    Duration.ofMillis(timeoutMillis));
            redisTemplate.opsForSet().add(userKey, token);
            redisTemplate.expire(userKey, Duration.ofMillis(timeoutMillis));
            redisTemplate.opsForSet().add(realmsKey(userId), type);
        } catch (XuYaTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new XuYaTokenException("Failed to persist session to Redis", e);
        }
        if (maxSessionsPerUser > 0 && !enforceLimit(type, userKey, token)) {
            // 被拒绝登录:回滚刚写入的会话与索引
            redisTemplate.delete(key(token));
            redisTemplate.opsForSet().remove(userKey, token);
            throw new ForbiddenException("Concurrent login limit reached for user: " + userId);
        }
        return new Session(token, userId, now, timeoutMillis, type);
    }

    /** 按 token 解析会话;key 不存在(已过期或未登录)返回 {@code null},命中时刷新 TTL 实现空闲续期。 */
    @Override
    public Session get(String token) {
        if (token == null) {
            return null;
        }
        SessionData data = readSession(token);
        if (data == null) {
            return null;
        }
        redisTemplate.expire(key(token), Duration.ofMillis(data.timeoutMillis));
        return new Session(data.token, data.userId, Instant.ofEpochMilli(data.createdAtMillis),
                data.timeoutMillis, data.userType);
    }

    /** 按 token 删除会话 key,并从体系用户索引中移除该成员。 */
    @Override
    public void invalidate(String token) {
        if (token == null) {
            return;
        }
        SessionData data = readSession(token);
        redisTemplate.delete(key(token));
        if (data != null) {
            redisTemplate.opsForSet().remove(userKey(data.userType, data.userId), token);
        }
    }

    /** 注销该用户在全部体系的会话:逐体系清理索引后删除体系登记。 */
    @Override
    public void invalidateByUserId(String userId) {
        for (String type : realmsOf(userId)) {
            invalidateByUserId(type, userId);
        }
        redisTemplate.delete(realmsKey(userId));
    }

    /** 注销该用户在指定体系内的会话;其他体系登录态不受影响。 */
    @Override
    public void invalidateByUserId(String userType, String userId) {
        String type = UserType.normalize(userType);
        String userKey = userKey(type, userId);
        for (String token : readMembers(userKey)) {
            redisTemplate.delete(key(token));
        }
        redisTemplate.delete(userKey);
    }

    /** 该用户当前未过期的 token 集合(跨全部体系):以会话 key 是否存在为准过滤。 */
    @Override
    public Set<String> listActiveTokens(String userId) {
        Set<String> result = new HashSet<>();
        for (String type : realmsOf(userId)) {
            for (String token : readMembers(userKey(type, userId))) {
                if (readSession(token) != null) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    /**
     * 执行并发限制策略:先剔除索引中已失效的成员,仍超限时按配置
     * 踢掉最旧会话或返回 false 拒绝新会话。
     */
    private boolean enforceLimit(String type, String userKey, String newToken) {
        Set<String> members = readMembers(userKey);
        members.remove(newToken);
        members.removeIf(token -> readSession(token) == null);
        members.add(newToken);
        while (members.size() > maxSessionsPerUser) {
            if (!evictOldestOnExceed) {
                return false;
            }
            String oldest = null;
            long oldestAt = Long.MAX_VALUE;
            for (String token : members) {
                SessionData data = readSession(token);
                if (data == null) {
                    continue;
                }
                if (data.createdAtMillis < oldestAt) {
                    oldestAt = data.createdAtMillis;
                    oldest = token;
                }
            }
            if (oldest == null) {
                break;
            }
            redisTemplate.delete(key(oldest));
            redisTemplate.opsForSet().remove(userKey, oldest);
            members.remove(oldest);
        }
        return true;
    }

    /** 读取并反序列化会话数据;不存在或数据损坏返回 {@code null}(旧数据缺体系字段归为默认体系)。 */
    private SessionData readSession(String token) {
        String json = redisTemplate.opsForValue().get(key(token));
        if (json == null) {
            return null;
        }
        try {
            SessionData data = objectMapper.readValue(json, SessionData.class);
            if (data.userType == null) {
                data.userType = UserType.DEFAULT;
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取集合的全部成员;集合不存在返回空集。 */
    private Set<String> readMembers(String setKey) {
        Set<String> members = redisTemplate.opsForSet().members(setKey);
        return members == null ? Set.of() : members;
    }

    /** 该用户出现过的体系集合(依据体系登记 key)。 */
    private Set<String> realmsOf(String userId) {
        return readMembers(realmsKey(userId));
    }

    /** 会话 key:{@code {prefix}{体系}-{随机}}。 */
    private String key(String token) {
        return keyPrefix + token;
    }

    /** 体系用户索引 key:{@code {prefix}user:{体系}:{userId}}。 */
    private String userKey(String userType, String userId) {
        return keyPrefix + "user:" + UserType.normalize(userType) + ":" + userId;
    }

    /** 用户体系登记 key:{@code {prefix}realms:{userId}}。 */
    private String realmsKey(String userId) {
        return keyPrefix + "realms:" + userId;
    }

    /** Redis 中存储的会话数据(避开 java.time 的序列化问题,统一用毫秒时间戳)。 */
    static final class SessionData {

        /** 会话令牌(含体系前缀)。 */
        public String token;

        /** 用户 ID。 */
        public String userId;

        /** 所属用户体系标识。 */
        public String userType;

        /** 创建时间(毫秒时间戳)。 */
        public long createdAtMillis;

        /** 空闲超时时间(毫秒)。 */
        public long timeoutMillis;

        /** 供 Jackson 反序列化使用。 */
        public SessionData() {
        }

        SessionData(String token, String userId, String userType,
                    long createdAtMillis, long timeoutMillis) {
            this.token = token;
            this.userId = userId;
            this.userType = userType;
            this.createdAtMillis = createdAtMillis;
            this.timeoutMillis = timeoutMillis;
        }
    }
}
