package dev.xuya.token.core.auth;

import dev.xuya.token.core.model.UserInfo;

/**
 * 基于 {@code PermissionLoader} 的 RBAC 校验接口。
 *
 * @author 青衣
 */
public interface PermissionChecker {

    /** 判断用户是否拥有指定角色。 */
    boolean hasRole(UserInfo user, String roleCode);

    /** 用户拥有全部给定角色时返回 true。 */
    boolean hasAllRoles(UserInfo user, String... roleCodes);

    /** 用户拥有任一给定角色时返回 true。 */
    boolean hasAnyRole(UserInfo user, String... roleCodes);

    /** 判断用户是否拥有指定权限(支持通配符)。 */
    boolean hasPermission(UserInfo user, String permissionExpr);

    /** 用户拥有任一给定权限时返回 true。 */
    boolean hasAnyPermission(UserInfo user, String... permissionExprs);
}
