package dev.xuya.token.core.security;

import dev.xuya.token.core.exception.ForbiddenException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认的内存版 {@link LoginGuard}:按"体系:账号"累计连续失败次数,
 * 达到阈值后锁定固定时长,锁定期内直接拒绝登录(403);
 * 登录成功即清零。适用于单实例或反代统一入口的场景,
 * 分布式精确计数请自行实现并接入 Redis。
 *
 * @author 青衣
 */
public class InMemoryLoginGuard implements LoginGuard {

    /** 体系:账号 → 失败计数。 */
    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();

    /** 体系:账号 → 锁定截止时间戳(System.currentTimeMillis)。 */
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

    /** 触发锁定的连续失败次数阈值,<=0 表示不启用。 */
    private final int maxFailures;

    /** 锁定时长,单位毫秒。 */
    private final long lockMillis;

    /**
     * 构造守卫。
     *
     * @param maxFailures 连续失败阈值(如 5),<=0 表示不拦截
     * @param lockMillis  锁定时长(毫秒,如 300000 = 5 分钟)
     */
    public InMemoryLoginGuard(int maxFailures, long lockMillis) {
        this.maxFailures = maxFailures;
        this.lockMillis = lockMillis;
    }

    /** 登录前检查:锁定期内抛出 403,过期自动清除锁。 */
    @Override
    public void check(String userType, String username) {
        String key = key(userType, username);
        Long until = lockedUntil.get(key);
        if (until == null) {
            return;
        }
        if (System.currentTimeMillis() < until) {
            long remainSeconds = (until - System.currentTimeMillis()) / 1000;
            throw new ForbiddenException(
                    "Account temporarily locked, retry in " + remainSeconds + "s");
        }
        lockedUntil.remove(key);
        failures.remove(key);
    }

    /** 回记登录结果:成功清零;失败累加,达到阈值即锁定。 */
    @Override
    public void record(String userType, String username, boolean success) {
        if (maxFailures <= 0) {
            return;
        }
        String key = key(userType, username);
        if (success) {
            failures.remove(key);
            lockedUntil.remove(key);
            return;
        }
        int count = failures.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        if (count >= maxFailures) {
            lockedUntil.put(key, System.currentTimeMillis() + lockMillis);
        }
    }

    /** 体系:账号 的计数键。 */
    private static String key(String userType, String username) {
        return userType == null ? username : userType + ":" + username;
    }
}
