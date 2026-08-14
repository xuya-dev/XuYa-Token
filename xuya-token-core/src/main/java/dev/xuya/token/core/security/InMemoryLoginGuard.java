package dev.xuya.token.core.security;

import dev.xuya.token.core.exception.ForbiddenException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认的内存版 {@link LoginGuard}:按"体系:账号"累计连续失败次数,
 * 达到阈值后锁定固定时长,锁定期内直接拒绝登录(403);登录成功即清零。
 * <p>内部为<b>有界 LRU</b>(访问序,默认容量 65536):防止攻击者遍历
 * 不同账号名造成计数表无限膨胀,最久未访问的账号状态会被优先淘汰。
 * 登录频率通常远低于并发瓶颈,方法级同步即可满足。
 * 分布式精确计数请自行实现并接入 Redis。
 *
 * @author 青衣
 */
public class InMemoryLoginGuard implements LoginGuard {

    /** 默认容量:最多保留多少个账号的失败/锁定状态。 */
    public static final int DEFAULT_MAX_ENTRIES = 65536;

    /** 账号状态:连续失败次数 + 锁定截止时间。 */
    private static final class State {
        final String key;
        int failures;
        Long lockedUntil;

        State(String key) {
            this.key = key;
        }
    }

    /** 有界 LRU:key → 账号状态(访问序淘汰,容量受 maxEntries 限制)。 */
    private final LinkedHashMap<String, State> states;

    /** 触发锁定的连续失败次数阈值,<=0 表示不启用。 */
    private final int maxFailures;

    /** 锁定时长,单位毫秒。 */
    private final long lockMillis;

    /**
     * 构造守卫(默认容量)。
     *
     * @param maxFailures 连续失败阈值(如 5),<=0 表示不拦截
     * @param lockMillis  锁定时长(毫秒,如 300000 = 5 分钟)
     */
    public InMemoryLoginGuard(int maxFailures, long lockMillis) {
        this(maxFailures, lockMillis, DEFAULT_MAX_ENTRIES);
    }

    /**
     * 构造守卫(完整参数)。
     *
     * @param maxFailures 连续失败阈值,<=0 表示不拦截
     * @param lockMillis  锁定时长(毫秒)
     * @param maxEntries  状态表容量(有界 LRU),<=0 使用默认值
     */
    public InMemoryLoginGuard(int maxFailures, long lockMillis, int maxEntries) {
        this.maxFailures = maxFailures;
        this.lockMillis = lockMillis;
        int capacity = maxEntries <= 0 ? DEFAULT_MAX_ENTRIES : maxEntries;
        this.states = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, State> eldest) {
                return size() > capacity;
            }
        };
    }

    /** 登录前检查:锁定期内抛出 403,过期自动清除锁。 */
    @Override
    public synchronized void check(String userType, String username) {
        State state = states.get(key(userType, username));
        if (state == null || state.lockedUntil == null) {
            return;
        }
        if (System.currentTimeMillis() < state.lockedUntil) {
            long remainSeconds = (state.lockedUntil - System.currentTimeMillis()) / 1000;
            throw new ForbiddenException(
                    "Account temporarily locked, retry in " + remainSeconds + "s");
        }
        // 锁已过期:清除该账号状态
        state.lockedUntil = null;
        state.failures = 0;
    }

    /** 回记登录结果:成功清零;失败累加,达到阈值即锁定。 */
    @Override
    public synchronized void record(String userType, String username, boolean success) {
        if (maxFailures <= 0) {
            return;
        }
        String key = key(userType, username);
        if (success) {
            states.remove(key);
            return;
        }
        State state = states.computeIfAbsent(key, State::new);
        state.failures++;
        if (state.failures >= maxFailures) {
            state.lockedUntil = System.currentTimeMillis() + lockMillis;
        }
    }

    /** 体系:账号 的计数键。 */
    private static String key(String userType, String username) {
        return userType == null ? username : userType + ":" + username;
    }
}
