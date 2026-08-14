package dev.xuya.token.core.auth;

import dev.xuya.token.core.exception.UnauthorizedException;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.session.Session;
import dev.xuya.token.core.session.SessionManager;
import dev.xuya.token.core.spi.UserProvider;

/**
 * 默认 {@link Authenticator} 实现,基于 {@link UserProvider} 与 {@link SessionManager}。
 * 登录按体系路由到 {@code UserProvider} 的体系方法,会话按体系创建。
 *
 * @author 青衣
 */
public class DefaultAuthenticator implements Authenticator {

    /** 用户来源 SPI。 */
    private final UserProvider userProvider;

    /** 会话管理器。 */
    private final SessionManager sessionManager;

    /**
     * 构造默认认证器。
     *
     * @param userProvider   用户来源
     * @param sessionManager 会话管理器
     */
    public DefaultAuthenticator(UserProvider userProvider, SessionManager sessionManager) {
        this.userProvider = userProvider;
        this.sessionManager = sessionManager;
    }

    /** 校验凭证并创建默认体系会话,委托给 {@link #login(String, String, String)}。 */
    @Override
    public Session login(String username, String password) {
        return login(UserType.DEFAULT, username, password);
    }

    /** 按体系校验凭证并创建会话;失败抛出 {@code UnauthorizedException}。 */
    @Override
    public Session login(String userType, String username, String password) {
        UserInfo user = userProvider.authenticate(userType, username, password);
        if (user == null) {
            throw new UnauthorizedException("Invalid username or password");
        }
        return sessionManager.create(userType, user.getId());
    }

    /** 关闭 token 对应的会话。 */
    @Override
    public void logout(String token) {
        sessionManager.invalidate(token);
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
}
