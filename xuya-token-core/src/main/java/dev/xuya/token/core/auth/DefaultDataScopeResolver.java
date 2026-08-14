package dev.xuya.token.core.auth;

import dev.xuya.token.core.model.DataScope;
import dev.xuya.token.core.model.DataScopeType;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.spi.DeptProvider;
import dev.xuya.token.core.spi.PermissionLoader;

import java.util.HashSet;
import java.util.Set;

/**
 * 默认 {@link DataScopeResolver} 实现:展开用户全部角色(含继承)
 * 后取最宽的数据权限级别,再按级别解析可见部门集合。
 *
 * @author 青衣
 */
public class DefaultDataScopeResolver implements DataScopeResolver {

    /** 权限数据来源 SPI。 */
    private final PermissionLoader permissionLoader;

    /** 部门层级来源 SPI,可为 null(缺失时 DEPT_AND_CHILD 退化为 DEPT)。 */
    private final DeptProvider deptProvider;

    /**
     * 构造解析器(无部门层级支持)。
     *
     * @param permissionLoader 权限数据来源
     */
    public DefaultDataScopeResolver(PermissionLoader permissionLoader) {
        this(permissionLoader, null);
    }

    /**
     * 构造解析器。
     *
     * @param permissionLoader 权限数据来源
     * @param deptProvider     部门层级来源,可为 null
     */
    public DefaultDataScopeResolver(PermissionLoader permissionLoader, DeptProvider deptProvider) {
        this.permissionLoader = permissionLoader;
        this.deptProvider = deptProvider;
    }

    /** 解析用户有效数据权限:级别取全部角色(含继承,按用户体系)中最宽者,可见部门按级别展开。 */
    @Override
    public DataScope resolve(UserInfo user) {
        if (user == null) {
            return null;
        }
        Set<String> roleCodes = RoleExpander.expand(user.getUserType(), permissionLoader, user.getRoleCodes());
        DataScopeType effective = DataScopeType.SELF;
        for (String code : roleCodes) {
            Role role = permissionLoader.loadRole(user.getUserType(), code).orElse(null);
            if (role != null && role.getDataScopeType().covers(effective)) {
                effective = role.getDataScopeType();
            }
        }
        String deptId = user.getDeptId();
        return new DataScope(effective, user.getId(), deptId,
                resolveVisibleDeptIds(effective, deptId));
    }

    /**
     * 按级别解析可见部门集合:DEPT 仅本部门;DEPT_AND_CHILD 借助
     * {@link DeptProvider} 递归纳入全部子部门(无提供者时退化为本部门);
     * ALL 与 SELF 返回空集,语义由级别本身表达。
     */
    private Set<String> resolveVisibleDeptIds(DataScopeType type, String deptId) {
        if (deptId == null) {
            return Set.of();
        }
        switch (type) {
            case DEPT:
                return Set.of(deptId);
            case DEPT_AND_CHILD:
                Set<String> visible = new HashSet<>();
                visible.add(deptId);
                if (deptProvider != null) {
                    visible.addAll(deptProvider.loadDescendantDeptIds(deptId));
                }
                return visible;
            default:
                return Set.of();
        }
    }
}
