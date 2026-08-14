package dev.xuya.token.core.session;

import dev.xuya.token.core.model.UserType;

import java.util.Set;

/**
 * SPI:会话存储。可实现以支持 Redis / JDBC 跨节点共享。
 * 支持多体系:会话按体系标识(开放字符串,见 {@link UserType})隔离,
 * 并发限制、踢人、索引均按体系独立计算。
 *
 * @author 青衣
 */
public interface SessionManager {

    /**
     * 为用户创建会话(默认体系)。
     *
     * @param userId 用户 ID
     */
    Session create(String userId);

    /**
     * 为指定体系的用户创建会话;体系可携带独立超时与并发策略。
     * 默认委托给 {@link #create(String)},单体系实现无需改动。
     *
     * @param userType 用户体系标识,如 "B"、"C"、"OPEN"
     * @param userId   用户 ID
     */
    default Session create(String userType, String userId) {
        return create(userId);
    }

    /** 按 token 解析会话;不存在或已过期返回 {@code null}(同时刷新活跃时间)。 */
    Session get(String token);

    /** 移除会话,即注销。 */
    void invalidate(String token);

    /**
     * 注销该用户的全部会话(踢人下线,跨全部体系)。
     * 默认空实现,不支持用户级索引的存储可静默忽略。
     *
     * @param userId 用户 ID
     */
    default void invalidateByUserId(String userId) {
    }

    /**
     * 注销该用户在指定体系内的全部会话(按体系踢人,互不影响)。
     * 默认委托给 {@link #invalidateByUserId(String)}。
     *
     * @param userType 用户体系标识
     * @param userId   用户 ID
     */
    default void invalidateByUserId(String userType, String userId) {
        invalidateByUserId(userId);
    }

    /**
     * 该用户当前有效(未过期)的 token 集合(跨全部体系),用于在线列表。
     * 默认返回空集,表示该实现不支持在线查询。
     *
     * @param userId 用户 ID
     */
    default Set<String> listActiveTokens(String userId) {
        return Set.of();
    }
}
