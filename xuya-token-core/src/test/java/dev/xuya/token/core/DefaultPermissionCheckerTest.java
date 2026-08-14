package dev.xuya.token.core;

import dev.xuya.token.core.auth.DefaultPermissionChecker;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.spi.InMemoryPermissionLoader;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPermissionCheckerTest {

    private final InMemoryPermissionLoader loader = new InMemoryPermissionLoader()
            .addRole(Role.builder("admin").name("管理员").permission("*:*").build())
            .addRole(Role.builder("user").name("用户").permission("user:read").permission("profile:*").build());

    private final DefaultPermissionChecker checker = new DefaultPermissionChecker(loader);

    private final UserInfo admin = new UserInfo("1", "admin", Set.of("admin"), Map.of());
    private final UserInfo user = new UserInfo("2", "alice", Set.of("user"), Map.of());

    @Test
    void roleChecks() {
        assertTrue(checker.hasRole(admin, "admin"));
        assertFalse(checker.hasRole(user, "admin"));
        assertTrue(checker.hasAnyRole(user, "admin", "user"));
        assertFalse(checker.hasAllRoles(user, "admin", "user"));
    }

    @Test
    void wildcardPermission() {
        assertTrue(checker.hasPermission(admin, "anything:anything"));
        assertTrue(checker.hasPermission(user, "user:read"));
        assertTrue(checker.hasPermission(user, "profile:update"));
        assertFalse(checker.hasPermission(user, "user:delete"));
    }

    @Test
    void nullUserNeverPasses() {
        assertFalse(checker.hasRole(null, "admin"));
        assertFalse(checker.hasPermission(null, "user:read"));
    }

    /** 经理继承普通用户:身份与权限均向下传递。 */
    @Test
    void inheritedRoleGrantsParentIdentityAndPermissions() {
        InMemoryPermissionLoader hierarchy = new InMemoryPermissionLoader()
                .addRole(Role.builder("user").name("用户").permission("user:read").build())
                .addRole(Role.builder("manager").name("经理")
                        .permission("report:*").parent("user").build());
        DefaultPermissionChecker hierarchyChecker = new DefaultPermissionChecker(hierarchy);
        UserInfo manager = new UserInfo("3", "bob", Set.of("manager"), Map.of());

        assertTrue(hierarchyChecker.hasRole(manager, "user"));
        assertTrue(hierarchyChecker.hasAllRoles(manager, "manager", "user"));
        assertTrue(hierarchyChecker.hasPermission(manager, "report:export"));
        assertTrue(hierarchyChecker.hasPermission(manager, "user:read"));
        assertFalse(hierarchyChecker.hasRole(manager, "admin"));
    }

    /** 多级继承:admin → manager → user,孙角色仍能获得祖先权限。 */
    @Test
    void multiLevelInheritance() {
        InMemoryPermissionLoader hierarchy = new InMemoryPermissionLoader()
                .addRole(Role.builder("user").permission("user:read").build())
                .addRole(Role.builder("manager").permission("report:read").parent("user").build())
                .addRole(Role.builder("admin").permission("*:*").parent("manager").build());
        DefaultPermissionChecker hierarchyChecker = new DefaultPermissionChecker(hierarchy);
        UserInfo admin = new UserInfo("1", "root", Set.of("admin"), Map.of());

        assertTrue(hierarchyChecker.hasRole(admin, "user"));
        assertTrue(hierarchyChecker.hasPermission(admin, "user:read"));
        assertTrue(hierarchyChecker.hasPermission(admin, "report:read"));
    }

    /** 环形继承 c1 → c2 → c1 不得死循环,直接权限仍可校验。 */
    @Test
    void cyclicInheritanceDoesNotLoopForever() {
        InMemoryPermissionLoader cyclic = new InMemoryPermissionLoader()
                .addRole(Role.builder("c1").permission("a:read").parent("c2").build())
                .addRole(Role.builder("c2").permission("b:read").parent("c1").build());
        DefaultPermissionChecker cyclicChecker = new DefaultPermissionChecker(cyclic);
        UserInfo user = new UserInfo("9", "loop", Set.of("c1"), Map.of());

        assertTrue(cyclicChecker.hasRole(user, "c2"));
        assertTrue(cyclicChecker.hasPermission(user, "a:read"));
        assertTrue(cyclicChecker.hasPermission(user, "b:read"));
    }

    /** 指向不存在的父角色时安全忽略,不影响直接角色校验。 */
    @Test
    void missingParentRoleIsIgnored() {
        InMemoryPermissionLoader loader = new InMemoryPermissionLoader()
                .addRole(Role.builder("solo").permission("x:read").parent("ghost").build());
        DefaultPermissionChecker soloChecker = new DefaultPermissionChecker(loader);
        UserInfo user = new UserInfo("8", "solo", Set.of("solo"), Map.of());

        assertTrue(soloChecker.hasPermission(user, "x:read"));
        assertFalse(soloChecker.hasRole(user, "ghost"));
    }
}
