package dev.xuya.token.core;

import dev.xuya.token.core.auth.DefaultDataScopeResolver;
import dev.xuya.token.core.model.DataScope;
import dev.xuya.token.core.model.DataScopeType;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.spi.InMemoryPermissionLoader;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDataScopeResolverTest {

    /** 部门层级:d2 → {d3},其余无子部门。 */
    private static final Set<String> CHILDREN_OF_D2 = Set.of("d3");

    private final InMemoryPermissionLoader loader = new InMemoryPermissionLoader()
            .addRole(Role.builder("admin").permission("*:*").dataScope(DataScopeType.ALL).build())
            .addRole(Role.builder("manager").permission("report:*")
                    .dataScope(DataScopeType.DEPT_AND_CHILD).build())
            .addRole(Role.builder("user").permission("profile:read")
                    .dataScope(DataScopeType.DEPT).build());

    private UserInfo user(String id, String deptId, String... roles) {
        return new UserInfo(id, "u-" + id, deptId, Set.of(roles), Map.of());
    }

    @Test
    void widestScopeAcrossRolesWins() {
        DefaultDataScopeResolver resolver = new DefaultDataScopeResolver(loader);
        // manager(DEPT_AND_CHILD) 与 user(DEPT) 并存,取更宽者
        DataScope scope = resolver.resolve(user("1", "d1", "user", "manager"));
        assertEquals(DataScopeType.DEPT_AND_CHILD, scope.getType());
    }

    @Test
    void deptScopeSeesOnlyOwnDept() {
        DefaultDataScopeResolver resolver = new DefaultDataScopeResolver(loader);
        DataScope scope = resolver.resolve(user("2", "d2", "user"));
        assertEquals(DataScopeType.DEPT, scope.getType());
        assertEquals(Set.of("d2"), scope.getVisibleDeptIds());
    }

    @Test
    void deptAndChildIncludesDescendantsViaProvider() {
        DefaultDataScopeResolver resolver = new DefaultDataScopeResolver(loader,
                deptId -> "d2".equals(deptId) ? CHILDREN_OF_D2 : Set.of());
        DataScope scope = resolver.resolve(user("3", "d2", "manager"));
        assertEquals(DataScopeType.DEPT_AND_CHILD, scope.getType());
        assertEquals(Set.of("d2", "d3"), scope.getVisibleDeptIds());
    }

    @Test
    void deptAndChildDegradesToDeptWithoutProvider() {
        DefaultDataScopeResolver resolver = new DefaultDataScopeResolver(loader);
        DataScope scope = resolver.resolve(user("4", "d2", "manager"));
        assertEquals(DataScopeType.DEPT_AND_CHILD, scope.getType());
        assertEquals(Set.of("d2"), scope.getVisibleDeptIds());
    }

    @Test
    void allScopeHasNoDeptFilter() {
        DefaultDataScopeResolver resolver = new DefaultDataScopeResolver(loader);
        DataScope scope = resolver.resolve(user("5", "d1", "admin"));
        assertEquals(DataScopeType.ALL, scope.getType());
        assertTrue(scope.getVisibleDeptIds().isEmpty());
    }

    @Test
    void inheritedRoleCarriesDataScope() {
        InMemoryPermissionLoader hierarchy = new InMemoryPermissionLoader()
                .addRole(Role.builder("user").dataScope(DataScopeType.DEPT).build())
                .addRole(Role.builder("manager").parent("user").dataScope(DataScopeType.SELF).build());
        DefaultDataScopeResolver resolver = new DefaultDataScopeResolver(hierarchy);
        // manager 继承 user 的 DEPT 数据权限
        DataScope scope = resolver.resolve(user("6", "d1", "manager"));
        assertEquals(DataScopeType.DEPT, scope.getType());
    }

    @Test
    void nullDeptYieldsEmptyVisibleSet() {
        DefaultDataScopeResolver resolver = new DefaultDataScopeResolver(loader);
        DataScope scope = resolver.resolve(user("7", null, "user"));
        assertEquals(DataScopeType.DEPT, scope.getType());
        assertTrue(scope.getVisibleDeptIds().isEmpty());
    }

    @Test
    void nullUserReturnsNull() {
        assertNull(new DefaultDataScopeResolver(loader).resolve(null));
    }
}
