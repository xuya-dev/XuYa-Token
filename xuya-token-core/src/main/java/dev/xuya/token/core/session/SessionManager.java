package dev.xuya.token.core.session;

import java.util.Set;

/**
 * SPI:会话存储。可实现以支持 Redis / JDBC 跨节点共享。
 *
 * @author 青衣
 */
public interface SessionManager {

    /** 为用户创建新会话并返回(含 token)。 */
    Session create(String userId);

    /** 按 token 解析会话;不存在或已过期返回 {@code null}(同时刷新活跃时间)。 */
    Session get(String token);

    /** 移除会话,即注销。 */
    void invalidate(String token);

    /**
     * 注销该用户的全部会话(踢人下线)。
     * 默认空实现,不支持用户级索引的存储可静默忽略。
     *
     * @param userId 用户 ID
     */
    default void invalidateByUserId(String userId) {
    }

    /**
     * 该用户当前有效(未过期)的 token 集合,用于在线列表与管理后台。
     * 默认返回空集,表示该实现不支持在线查询。
     *
     * @param userId 用户 ID
     * @return 有效 token 集合
     */
    default Set<String> listActiveTokens(String userId) {
        return Set.of();
    }
}
