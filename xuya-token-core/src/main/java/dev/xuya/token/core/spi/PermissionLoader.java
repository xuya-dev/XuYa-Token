package dev.xuya.token.core.spi;

import dev.xuya.token.core.model.Permission;
import dev.xuya.token.core.model.Role;

import java.util.Optional;
import java.util.Set;

/**
 * SPI:角色与权限定义的数据来源。
 * 实现此接口可从数据库或缓存加载。
 * <p>多体系:默认两体系共用同一套角色;同名角色在不同体系需要
 * 不同权限时,覆盖体系方法按体系加载(如 C 端 "admin" 与 B 端 "admin" 各自定义)。
 *
 * @author 青衣
 */
public interface PermissionLoader {

    /** 按角色编码解析角色(默认体系)。 */
    Optional<Role> loadRole(String roleCode);

    /**
     * 按体系解析角色;默认委托给 {@link #loadRole(String)}(体系共用角色),
     * 体系隔离时覆写。
     *
     * @param userType 用户体系标识,如 "B"、"C"
     */
    default Optional<Role> loadRole(String userType, String roleCode) {
        return loadRole(roleCode);
    }

    /** 加载给定角色集合的全部权限(并集,默认体系)。 */
    Set<Permission> loadPermissions(Set<String> roleCodes);

    /**
     * 按体系加载角色集合的权限并集;默认委托给 {@link #loadPermissions(Set)}。
     *
     * @param userType 用户体系标识
     */
    default Set<Permission> loadPermissions(String userType, Set<String> roleCodes) {
        return loadPermissions(roleCodes);
    }
}
