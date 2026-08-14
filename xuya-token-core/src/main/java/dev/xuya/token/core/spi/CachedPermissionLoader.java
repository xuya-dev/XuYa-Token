package dev.xuya.token.core.spi;

import dev.xuya.token.core.model.Permission;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserType;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 带 TTL 缓存的 {@link PermissionLoader} 装饰器,适用于数据库等
 * 加载成本较高的实现。角色/权限数据变更后调用 {@link #invalidateAll()}
 * 可立即失效,否则等待 TTL 自然过期。
 * <p>多体系:缓存键含体系标识,各体系缓存互不干扰;未指定体系的
 * 方法按默认体系处理。
 * <p>简单实现:缓存条目各自独立过期,并发未命中时允许短暂重复回源,
 * 不做互斥加载。
 *
 * @author 青衣
 */
public class CachedPermissionLoader implements PermissionLoader {

    /** 被装饰的真实数据来源。 */
    private final PermissionLoader delegate;

    /** 缓存存活时间,单位毫秒。 */
    private final long ttlMillis;

    /** 角色缓存:"体系:角色编码" → 缓存条目。 */
    private final ConcurrentHashMap<String, CachedEntry<Role>> roleCache = new ConcurrentHashMap<>();

    /** 权限缓存:"体系:角色编码集合的排序拼接键" → 缓存条目。 */
    private final ConcurrentHashMap<String, CachedEntry<Set<Permission>>> permissionCache =
            new ConcurrentHashMap<>();

    /**
     * 构造缓存装饰器。
     *
     * @param delegate  被装饰的数据来源
     * @param ttlMillis 缓存存活时间(毫秒)
     */
    public CachedPermissionLoader(PermissionLoader delegate, long ttlMillis) {
        this.delegate = delegate;
        this.ttlMillis = ttlMillis;
    }

    /** 按角色编码加载角色(默认体系,带 TTL 缓存)。 */
    @Override
    public Optional<Role> loadRole(String roleCode) {
        return loadRole(UserType.DEFAULT, roleCode);
    }

    /** 按体系加载角色(带 TTL 缓存,未命中角色的空结果同样缓存以防穿透)。 */
    @Override
    public Optional<Role> loadRole(String userType, String roleCode) {
        String key = UserType.normalize(userType) + ":" + roleCode;
        long now = System.currentTimeMillis();
        CachedEntry<Role> entry = roleCache.get(key);
        if (entry != null && !entry.expired(now)) {
            return Optional.ofNullable(entry.value);
        }
        Role role = delegate.loadRole(userType, roleCode).orElse(null);
        roleCache.put(key, new CachedEntry<>(role, now + ttlMillis));
        return Optional.ofNullable(role);
    }

    /** 加载默认体系的权限并集(带 TTL 缓存)。 */
    @Override
    public Set<Permission> loadPermissions(Set<String> roleCodes) {
        return loadPermissions(UserType.DEFAULT, roleCodes);
    }

    /** 按体系加载角色集合的权限并集(以 体系+排序角色编码 为缓存键)。 */
    @Override
    public Set<Permission> loadPermissions(String userType, Set<String> roleCodes) {
        long now = System.currentTimeMillis();
        String key = UserType.normalize(userType) + ":" + cacheKey(roleCodes);
        CachedEntry<Set<Permission>> entry = permissionCache.get(key);
        if (entry != null && !entry.expired(now)) {
            return entry.value;
        }
        Set<Permission> permissions = delegate.loadPermissions(userType, roleCodes);
        permissionCache.put(key, new CachedEntry<>(permissions, now + ttlMillis));
        return permissions;
    }

    /** 清空全部体系缓存,下次访问强制回源。 */
    public void invalidateAll() {
        roleCache.clear();
        permissionCache.clear();
    }

    /** 将角色编码集合转为稳定的缓存键(排序后拼接,与集合顺序无关)。 */
    private static String cacheKey(Set<String> roleCodes) {
        return roleCodes == null ? "" : roleCodes.stream().sorted().collect(Collectors.joining(","));
    }

    /** 缓存条目:值 + 过期时间戳,各自独立过期。 */
    private static final class CachedEntry<T> {

        /** 缓存值,可为 null(表示"该角色不存在"的负缓存)。 */
        final T value;

        /** 过期时间戳(System.currentTimeMillis)。 */
        final long expiresAtMillis;

        CachedEntry(T value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }

        /** 判断在给定时刻是否已过期。 */
        boolean expired(long now) {
            return now >= expiresAtMillis;
        }
    }
}
