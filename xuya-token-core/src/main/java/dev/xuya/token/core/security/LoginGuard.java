package dev.xuya.token.core.security;

/**
 * SPI:登录防爆破守卫。登录前检查账号是否被锁定,
 * 登录后回记成功/失败,连续失败达到阈值即锁定一段时间。
 *
 * @author 青衣
 */
public interface LoginGuard {

    /**
     * 登录尝试前置检查。
     *
     * @param userType 用户体系标识
     * @param username 登录账号
     * @throws dev.xuya.token.core.exception.ForbiddenException 账号处于锁定期时抛出
     */
    void check(String userType, String username);

    /**
     * 登录结果回记:成功清零计数,失败累加并可能触发锁定。
     *
     * @param userType 用户体系标识
     * @param username 登录账号
     * @param success  是否成功
     */
    void record(String userType, String username, boolean success);
}
