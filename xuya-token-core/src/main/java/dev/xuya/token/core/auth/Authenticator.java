package dev.xuya.token.core.auth;

import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.session.Session;

/**
 * 门面接口:登录 / 注销 / 当前用户解析。
 *
 * @author 青衣
 */
public interface Authenticator {

    /**
     * 校验凭证并创建会话。
     *
     * @param username 用户名
     * @param password 密码
     * @return 新建的会话(含 token)
     * @throws dev.xuya.token.core.exception.UnauthorizedException 凭证无效时抛出
     */
    Session login(String username, String password);

    /**
     * 关闭 token 对应的会话。
     *
     * @param token 会话令牌
     */
    void logout(String token);

    /**
     * 按 token 解析当前用户。
     *
     * @param token 会话令牌
     * @return 用户信息;未认证返回 {@code null}
     */
    UserInfo getCurrentUser(String token);
}
