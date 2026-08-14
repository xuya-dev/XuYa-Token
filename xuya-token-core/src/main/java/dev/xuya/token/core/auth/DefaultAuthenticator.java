package dev.xuya.token.core.auth;

import dev.xuya.token.core.audit.AuthAuditListener;
import dev.xuya.token.core.exception.UnauthorizedException;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.security.LoginGuard;
import dev.xuya.token.core.session.Session;
import dev.xuya.token.core.session.SessionManager;
import dev.xuya.token.core.spi.UserProvider;

import java.util.List;

/**
 * 默认 {@link Authenticator} 实现,基于 {@link UserProvider} 与 {@link SessionManager}。
 * 登录按体系路由到 {@code UserProvider} 的体系方法,会话按体系创建;
 * 可选接入 {@link LoginGuard} 防爆破守卫与 {@link AuthAuditListener} 审计监听
 * (守卫与监听均为可选组件,缺省不启用)。
 *
 * @author 青衣
 */
public class DefaultAuthenticator implements Authenticator {

    /** 用户来源 SPI。 */
    private final UserProvider userProvider;

    /** 会话管理器。 */
    private final SessionManager sessionManager;

    /** 登录防爆破守卫,可为 null(不启用)。 */
    private final LoginGuard loginGuard;

    /** 审计监听器列表,可为空。 */
    private final List<AuthAuditListener> auditListeners;

    /**
     * 构造默认认证器(无守卫、无审计)。
     *
     * @param userProvider   用户来源
     * @param sessionManager 会话管理器
     */
    public DefaultAuthenticator(UserProvider userProvider, SessionManager sessionManager) {
        this(userProvider, sessionManager, null, List.of());
    }

    /**
     * 构造默认认证器(完整参数)。
     *
     * @param userProvider   用户来源
     * @param sessionManager 会话管理器
     * @param loginGuard     登录守卫,可为 null
     * @param auditListeners 审计监听器列表,可为 null(视为空)
     */
    public DefaultAuthenticator(UserProvider userProvider, SessionManager sessionManager,
                                LoginGuard loginGuard, List<AuthAuditListener> auditListeners) {
        this.userProvider = userProvider;
        this.sessionManager = sessionManager;
        this.loginGuard = loginGuard;
        this.auditListeners = auditListeners == null ? List.of() : List.copyOf(auditListeners);
    }

    /** 校验凭证并创建默认体系会话,委托给 {@link #login(String, String, String)}。 */
    @Override
    public Session login(String username, String password) {
        return login(UserType.DEFAULT, username, password);
    }

    /**
     * 按体系校验凭证并创建会话:先经守卫检查锁定期,凭证无效回记失败并
     * 抛出 {@code UnauthorizedException},成功回记并发布审计事件。
     */
    @Override
    public Session login(String userType, String username, String password) {
        if (loginGuard != null) {
            loginGuard.check(userType, username);
        }
        UserInfo user = userProvider.authenticate(userType, username, password);
        if (user == null) {
            if (loginGuard != null) {
                loginGuard.record(userType, username, false);
            }
            publishFailure(userType, username);
            throw new UnauthorizedException("Invalid username or password");
        }
        if (loginGuard != null) {
            loginGuard.record(userType, username, true);
        }
        publishSuccess(userType, username, user.getId());
        return sessionManager.create(userType, user.getId());
    }

    /** 关闭 token 对应的会话并发布注销事件(尽力而为,不因解析失败阻断注销)。 */
    @Override
    public void logout(String token) {
        String userType = null;
        String userId = null;
        Session session = sessionManager.get(token);
        if (session != null) {
            userType = session.getUserType();
            userId = session.getUserId();
        }
        sessionManager.invalidate(token);
        for (AuthAuditListener listener : auditListeners) {
            try {
                listener.onLogout(userType, userId);
            } catch (Exception ignored) {
                // 审计异常不阻断主流程
            }
        }
    }

    /**
     * 按 token 解析当前用户:体系取自会话,用户查自对应体系的
     * {@code findById};返回用户的体系与会话强制一致,用户已不存在时销毁会话。
     */
    @Override
    public UserInfo getCurrentUser(String token) {
        Session session = sessionManager.get(token);
        if (session == null) {
            return null;
        }
        UserInfo user = userProvider.findById(session.getUserType(), session.getUserId());
        if (user == null) {
            sessionManager.invalidate(token);
            return null;
        }
        if (!session.getUserType().equals(user.getUserType())) {
            // 提供者返回的体系与会话不符时以会话为准,防止跨体系串号
            user = new UserInfo(user.getId(), user.getUsername(), user.getDeptId(),
                    user.getRoleCodes(), user.getAttributes(), session.getUserType());
        }
        return user;
    }

    /** 发布登录成功事件(逐个监听器隔离异常)。 */
    private void publishSuccess(String userType, String username, String userId) {
        for (AuthAuditListener listener : auditListeners) {
            try {
                listener.onLoginSuccess(userType, username, userId);
            } catch (Exception ignored) {
                // 审计异常不阻断主流程
            }
        }
    }

    /** 发布登录失败事件(逐个监听器隔离异常)。 */
    private void publishFailure(String userType, String username) {
        for (AuthAuditListener listener : auditListeners) {
            try {
                listener.onLoginFailure(userType, username);
            } catch (Exception ignored) {
                // 审计异常不阻断主流程
            }
        }
    }
}
