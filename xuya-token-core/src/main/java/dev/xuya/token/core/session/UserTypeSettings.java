package dev.xuya.token.core.session;

/**
 * 单个用户体系(端)的会话策略:超时与并发会话数。
 * 未设置(null)的项继承全局配置,因此只需为与全局不同的体系声明策略。
 *
 * @author 青衣
 */
public final class UserTypeSettings {

    /** 该体系空闲超时时间(毫秒),null 表示继承全局。 */
    private final Long timeoutMillis;

    /** 该体系单用户最大并发会话数,null 表示继承全局。 */
    private final Integer maxSessionsPerUser;

    /**
     * 构造体系策略。
     *
     * @param timeoutMillis      空闲超时(毫秒),可为 null
     * @param maxSessionsPerUser 最大并发会话数,可为 null
     */
    public UserTypeSettings(Long timeoutMillis, Integer maxSessionsPerUser) {
        this.timeoutMillis = timeoutMillis;
        this.maxSessionsPerUser = maxSessionsPerUser;
    }

    /** 仅设置超时的便捷工厂。 */
    public static UserTypeSettings ofTimeout(long timeoutMillis) {
        return new UserTypeSettings(timeoutMillis, null);
    }

    /** 获取该体系空闲超时(毫秒),可能为 null。 */
    public Long getTimeoutMillis() {
        return timeoutMillis;
    }

    /** 获取该体系最大并发会话数,可能为 null。 */
    public Integer getMaxSessionsPerUser() {
        return maxSessionsPerUser;
    }
}
