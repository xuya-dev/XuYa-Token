package dev.xuya.token.core.spi;

import dev.xuya.token.core.model.UserInfo;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 带 TTL 缓存的 {@link UserProvider} 装饰器:仅缓存
 * {@link #findById(String, String)} 的结果(每请求鉴权的热点路径),
 * {@code authenticate} 永远穿透到真实来源(密码校验不可缓存)。
 * <p>用户的角色/部门变更会在 TTL 后生效;需要立即生效时调用
 * {@link #invalidate(String, String)} 或 {@link #invalidateAll()}。
 * <p>简单实现:条目各自独立过期,并发未命中允许短暂重复回源。
 *
 * @author 青衣
 */
public class CachedUserProvider implements UserProvider {

    /** 被装饰的真实用户来源。 */
    private final UserProvider delegate;

    /** 缓存存活时间,单位毫秒。 */
    private final long ttlMillis;

    /** 缓存键(体系:用户 ID)→ 缓存条目。 */
    private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<>();

    /**
     * 构造缓存装饰器。
     *
     * @param delegate  被装饰的用户来源
     * @param ttlMillis 缓存存活时间(毫秒)
     */
    public CachedUserProvider(UserProvider delegate, long ttlMillis) {
        this.delegate = delegate;
        this.ttlMillis = ttlMillis;
    }

    /** 登录校验:始终穿透到真实来源,不做缓存。 */
    @Override
    public UserInfo authenticate(String username, String password) {
        return delegate.authenticate(username, password);
    }

    /** 登录校验:始终穿透到真实来源,不做缓存。 */
    @Override
    public UserInfo authenticate(String userType, String username, String password) {
        return delegate.authenticate(userType, username, password);
    }

    /** 按用户 ID 查找(带 TTL 缓存,鉴权热路径每请求一次的调用由此兜住)。 */
    @Override
    public UserInfo findById(String userType, String userId) {
        String key = userType + ":" + userId;
        long now = System.currentTimeMillis();
        CachedEntry entry = cache.get(key);
        if (entry != null && !entry.expired(now)) {
            return entry.user;
        }
        UserInfo user = delegate.findById(userType, userId);
        cache.put(key, new CachedEntry(user, now + ttlMillis));
        return user;
    }

    /** 按用户 ID 查找(默认体系,委托体系方法)。 */
    @Override
    public UserInfo findById(String userId) {
        return findById(dev.xuya.token.core.model.UserType.DEFAULT, userId);
    }

    /** 使用给定用户刷新缓存条目(如登录成功后立即更新)。 */
    public void refresh(String userType, String userId, UserInfo user) {
        cache.put(userType + ":" + userId, new CachedEntry(user, System.currentTimeMillis() + ttlMillis));
    }

    /** 失效单个用户的缓存(用户信息变更后调用)。 */
    public void invalidate(String userType, String userId) {
        cache.remove(userType + ":" + userId);
    }

    /** 清空全部缓存。 */
    public void invalidateAll() {
        cache.clear();
    }

    /** 缓存条目:用户 + 过期时间戳。 */
    private static final class CachedEntry {
        final UserInfo user;
        final long expiresAtMillis;

        CachedEntry(UserInfo user, long expiresAtMillis) {
            this.user = user;
            this.expiresAtMillis = expiresAtMillis;
        }

        boolean expired(long now) {
            return now >= expiresAtMillis;
        }
    }
}
