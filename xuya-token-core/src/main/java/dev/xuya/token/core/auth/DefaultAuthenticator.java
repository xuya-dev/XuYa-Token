package dev.xuya.token.core.auth;

import dev.xuya.token.core.exception.UnauthorizedException;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.session.Session;
import dev.xuya.token.core.session.SessionManager;
import dev.xuya.token.core.spi.UserProvider;

/**
 * 默认 {@link Authenticator} 实现,基于 {@link UserProvider} 与 {@link SessionManager}。
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

    /** 校验凭证并创建会话;失败抛出 {@code UnauthorizedException}。 */
    @Override
    public Session login(String username, String password) {
        UserInfo user = userProvider.authenticate(username, password);
        if (user == null) {
            throw new UnauthorizedException("Invalid username or password");
        }
        return sessionManager.create(user.getId());
    }

    /** 关闭 token 对应的会话。 */
    @Override
    public void logout(String token) {
        sessionManager.invalidate(token);
    }

    /** 按 token 解析当前用户;未认证返回 {@code null},用户已不存在时同时销毁会话。 */
    @Override
    public UserInfo getCurrentUser(String token) {
        Session session = sessionManager.get(token);
        if (session == null) {
            return null;
        }
        UserInfo user = userProvider.findById(session.getUserId());
        if (user == null) {
            sessionManager.invalidate(token);
        }
        return user;
    }
}
