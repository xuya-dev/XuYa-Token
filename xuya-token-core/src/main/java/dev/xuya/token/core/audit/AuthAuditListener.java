package dev.xuya.token.core.audit;

/**
 * SPI:认证审计监听器。登录成功/失败与注销事件回调,
 * 用于审计日志、告警与风控联动。实现需自行保证线程安全,
 * 回调异常不会影响认证主流程。
 *
 * @author 青衣
 */
public interface AuthAuditListener {

    /**
     * 登录成功。
     *
     * @param userType 用户体系标识
     * @param username 登录账号
     * @param userId   用户 ID
     */
    default void onLoginSuccess(String userType, String username, String userId) {
    }

    /**
     * 登录失败(凭证无效)。
     *
     * @param userType 用户体系标识
     * @param username 登录账号
     */
    default void onLoginFailure(String userType, String username) {
    }

    /**
     * 注销。
     *
     * @param userType 用户体系标识
     * @param userId   用户 ID
     */
    default void onLogout(String userType, String userId) {
    }
}
