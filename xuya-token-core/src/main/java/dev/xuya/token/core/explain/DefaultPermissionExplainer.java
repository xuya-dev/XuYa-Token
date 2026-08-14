package dev.xuya.token.core.explain;

import dev.xuya.token.core.auth.RoleExpander;
import dev.xuya.token.core.model.Permission;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.spi.PermissionLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 默认 {@link PermissionExplainer} 实现:按用户体系展开角色继承链
 * (记录获得路径),再按角色或权限语义判定并输出解释。
 *
 * @author 青衣
 */
public class DefaultPermissionExplainer implements PermissionExplainer {

    /** 权限数据来源 SPI。 */
    private final PermissionLoader permissionLoader;

    /**
     * 构造解释器。
     *
     * @param permissionLoader 权限数据来源
     */
    public DefaultPermissionExplainer(PermissionLoader permissionLoader) {
        this.permissionLoader = permissionLoader;
    }

    /** 解释一次角色或权限判定,输出继承轨迹、命中依据与结论。 */
    @Override
    public AuthDecision explain(UserInfo user, String expression) {
        if (user == null) {
            return AuthDecision.anonymous(expression);
        }
        Map<String, String> inheritedFrom = new HashMap<>();
        Set<String> expanded = RoleExpander.expand(
                user.getUserType(), permissionLoader, user.getRoleCodes(), inheritedFrom);
        List<RoleTrace> traces = new ArrayList<>();
        for (String code : new TreeSet<>(expanded)) {
            Role role = permissionLoader.loadRole(user.getUserType(), code).orElse(null);
            if (role != null) {
                traces.add(new RoleTrace(code, inheritedFrom.get(code), role.getPermissionStrings()));
            }
        }
        return expression.indexOf(':') < 0
                ? explainRole(expression, expanded, traces)
                : explainPermission(expression, user, expanded, traces);
    }

    /** 角色判定解释:命中编码即放行,拒绝时列出全部已展开角色。 */
    private AuthDecision explainRole(String roleCode, Set<String> expanded, List<RoleTrace> traces) {
        boolean allowed = expanded.contains(roleCode);
        String reason = allowed
                ? "允许:角色 " + roleCode + " 在已展开角色集合中(直接持有或经继承获得)"
                : "拒绝:已展开角色 " + expanded + ",不包含 " + roleCode;
        return new AuthDecision(allowed, roleCode, true, traces,
                allowed ? roleCode : null, reason);
    }

    /** 权限判定解释:在全部已授予权限中寻找蕴含者,通配命中会被点名。 */
    private AuthDecision explainPermission(String expression, UserInfo user,
                                           Set<String> expanded, List<RoleTrace> traces) {
        Permission required = Permission.of(expression);
        Set<Permission> granted = permissionLoader.loadPermissions(user.getUserType(), expanded);
        Permission matched = granted.stream()
                .filter(p -> p.implies(required))
                .findFirst()
                .orElse(null);
        String grantedDesc = granted.stream()
                .sorted(java.util.Comparator.comparing(Permission::toString))
                .map(Permission::toString)
                .collect(Collectors.joining(", ", "[", "]"));
        String reason = matched != null
                ? "允许:已授予权限 " + matched + " 蕴含 " + expression
                : "拒绝:已授予权限 " + grantedDesc + ",无一蕴含 " + expression;
        return new AuthDecision(matched != null, expression, false, traces,
                matched == null ? null : matched.toString(), reason);
    }
}
