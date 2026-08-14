package dev.xuya.token.core.auth;

import dev.xuya.token.core.model.Permission;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.spi.PermissionLoader;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * 默认 {@link PermissionChecker} 实现:先经 {@link RoleExpander} 沿角色
 * 继承链展开全部角色,再聚合权限做校验。
 *
 * @author 青衣
 */
public class DefaultPermissionChecker implements PermissionChecker {

    /** 权限数据来源 SPI。 */
    private final PermissionLoader permissionLoader;

    /**
     * 构造默认权限校验器。
     *
     * @param permissionLoader 权限数据来源
     */
    public DefaultPermissionChecker(PermissionLoader permissionLoader) {
        this.permissionLoader = permissionLoader;
    }

    /** 判断用户是否拥有指定角色(按用户体系,含继承得到的角色身份)。 */
    @Override
    public boolean hasRole(UserInfo user, String roleCode) {
        return user != null && expand(user).contains(roleCode);
    }

    /** 用户拥有全部给定角色(按用户体系,含继承)时返回 true。 */
    @Override
    public boolean hasAllRoles(UserInfo user, String... roleCodes) {
        return user != null && expand(user).containsAll(Arrays.asList(roleCodes));
    }

    /** 用户拥有任一给定角色(按用户体系,含继承)时返回 true。 */
    @Override
    public boolean hasAnyRole(UserInfo user, String... roleCodes) {
        if (user == null) {
            return false;
        }
        Set<String> expanded = expand(user);
        return Arrays.stream(roleCodes).anyMatch(expanded::contains);
    }

    /** 判断用户是否拥有指定权限(按用户体系,继承角色的权限一并生效,支持通配符)。 */
    @Override
    public boolean hasPermission(UserInfo user, String permissionExpr) {
        if (user == null) {
            return false;
        }
        Permission required = Permission.of(permissionExpr);
        Set<Permission> granted = permissionLoader.loadPermissions(
                user.getUserType(), expand(user));
        return granted.stream().anyMatch(p -> p.implies(required));
    }

    /** 用户拥有任一给定权限时返回 true。 */
    @Override
    public boolean hasAnyPermission(UserInfo user, String... permissionExprs) {
        return Arrays.stream(permissionExprs).anyMatch(expr -> hasPermission(user, expr));
    }

    /** 按用户体系展开其角色继承链。 */
    private Set<String> expand(UserInfo user) {
        return RoleExpander.expand(user.getUserType(), permissionLoader, user.getRoleCodes());
    }
}
