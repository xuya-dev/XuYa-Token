package dev.xuya.token.core.spi;

import dev.xuya.token.core.model.Permission;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的内存版 {@link PermissionLoader}。适用于演示与测试;
 * 生产环境请替换为持久化实现。
 * <p>多体系:默认全部体系共用角色表;通过 {@link #addRole(String, Role)}
 * 可为指定体系注册体系专属角色,查找时先匹配体系专属、再回退共用表,
 * 因此同名角色在不同体系可拥有不同权限。
 *
 * @author 青衣
 */
public class InMemoryPermissionLoader implements PermissionLoader {

    /** 共用角色表:角色编码 → 角色(未声明体系隔离的体系均使用此表)。 */
    private final Map<String, Role> roles = new ConcurrentHashMap<>();

    /** 体系专属角色表:"体系:角色编码" → 角色。 */
    private final Map<String, Role> realmRoles = new ConcurrentHashMap<>();

    /**
     * 添加共用角色(全部体系可见)。
     *
     * @param role 角色对象
     * @return this,支持链式调用
     */
    public InMemoryPermissionLoader addRole(Role role) {
        roles.put(role.getCode(), role);
        return this;
    }

    /**
     * 为指定体系添加专属角色,仅该体系可见,优先级高于同编码的共用角色。
     *
     * @param userType 用户体系标识,如 "C"
     * @param role     角色对象
     * @return this,支持链式调用
     */
    public InMemoryPermissionLoader addRole(String userType, Role role) {
        realmRoles.put(UserType.normalize(userType) + ":" + role.getCode(), role);
        return this;
    }

    /** 按角色编码查找共用角色。 */
    @Override
    public Optional<Role> loadRole(String roleCode) {
        return Optional.ofNullable(roles.get(roleCode));
    }

    /** 按体系查找角色:先体系专属表,后共用表。 */
    @Override
    public Optional<Role> loadRole(String userType, String roleCode) {
        Role realmRole = realmRoles.get(UserType.normalize(userType) + ":" + roleCode);
        return realmRole != null ? Optional.of(realmRole) : loadRole(roleCode);
    }

    /** 加载共用角色表中的权限并集。 */
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

    /** 按体系加载权限并集(体系专属角色优先)。 */
    @Override
    public Set<Permission> loadPermissions(String userType, Set<String> roleCodes) {
        Set<Permission> result = new HashSet<>();
        for (String code : roleCodes) {
            loadRole(userType, code).ifPresent(role -> result.addAll(role.getPermissions()));
        }
        return Collections.unmodifiableSet(result);
    }
}
