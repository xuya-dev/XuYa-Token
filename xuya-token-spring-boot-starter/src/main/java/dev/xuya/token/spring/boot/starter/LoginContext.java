package dev.xuya.token.spring.boot.starter;

import dev.xuya.token.core.model.UserInfo;

/**
 * 当前请求用户的 ThreadLocal 持有器。
 *
 * @author 青衣
 */
public final class LoginContext {

    /** 当前请求用户的 ThreadLocal 持有器。 */
    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private LoginContext() {
    }

    /**
     * 设置当前请求的用户。
     *
     * @param user 用户信息
     */
    public static void set(UserInfo user) {
        HOLDER.set(user);
    }

    /** 获取当前用户;白名单路径上可能为 {@code null}。 */
    public static UserInfo getUser() {
        return HOLDER.get();
    }

    /** 清除当前线程的用户绑定,防止线程复用导致的数据串扰。 */
    public static void clear() {
        HOLDER.remove();
    }
}
