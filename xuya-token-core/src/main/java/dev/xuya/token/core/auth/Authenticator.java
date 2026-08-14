package dev.xuya.token.core.auth;

import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.session.Session;

/**
 * 门面接口:登录 / 注销 / 当前用户解析。
 * <p>多体系:登录时指定体系标识(如 "B"、"C"、"OPEN"),
 * 会话、用户来源、角色解析均按体系隔离。
 *
 * @author 青衣
 */
public interface Authenticator {

    /**
     * 校验凭证并创建会话(默认体系)。
     *
     * @param username 用户名
     * @param password 密码
     * @return 新建的会话(含 token)
     * @throws dev.xuya.token.core.exception.UnauthorizedException 凭证无效时抛出
     */
    Session login(String username, String password);

    /**
     * 按体系校验凭证并创建会话;默认委托给 {@link #login(String, String)}。
     *
     * @param userType 用户体系标识,如 "B"、"C"
     * @param username 用户名
     * @param password 密码
     * @return 新建的会话(含 token)
     * @throws dev.xuya.token.core.exception.UnauthorizedException 凭证无效时抛出
     */
    default Session login(String userType, String username, String password) {
        return login(username, password);
    }

    /**
     * 关闭 token 对应的会话。
     *
     * @param token 会话令牌
     */
    void logout(String token);

    /**
     * 按 token 解析当前用户(体系由会话决定)。
     *
     * @param token 会话令牌
     * @return 用户信息;未认证返回 {@code null}
     */
    UserInfo getCurrentUser(String token);
}
