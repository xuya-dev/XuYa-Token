package dev.xuya.token.core.spi;

import dev.xuya.token.core.model.Permission;
import dev.xuya.token.core.model.Role;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的内存版 {@link PermissionLoader},适用于演示与测试;
 * 生产环境请替换为持久化实现。
 *
 * @author 青衣
 */
public class InMemoryPermissionLoader implements PermissionLoader {

    /** 角色编码 → 角色 的存储。 */
    private final Map<String, Role> roles = new ConcurrentHashMap<>();

    /**
     * 添加角色定义。
     *
     * @param role 角色对象
     * @return this,支持链式调用
     */
    public InMemoryPermissionLoader addRole(Role role) {
        roles.put(role.getCode(), role);
        return this;
    }

    /** 按角色编码查找角色。 */
    @Override
    public Optional<Role> loadRole(String roleCode) {
        return Optional.ofNullable(roles.get(roleCode));
    }

    /** 加载给定角色集合的全部权限(并集),未定义的角色被忽略。 */
    @Override
    public Set<Permission> loadPermissions(Set<String> roleCodes) {
        Set<Permission> result = new HashSet<>();
        for (String code : roleCodes) {
            Role role = roles.get(code);
            if (role != null) {
                result.addAll(role.getPermissions());
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
