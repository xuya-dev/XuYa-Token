package dev.xuya.token.core;

import dev.xuya.token.core.explain.AuthDecision;
import dev.xuya.token.core.explain.DefaultPermissionExplainer;
import dev.xuya.token.core.explain.RoleTrace;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.spi.InMemoryPermissionLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionExplainerTest {

    private final InMemoryPermissionLoader loader = new InMemoryPermissionLoader()
            .addRole(Role.builder("user").name("用户").permission("user:read").build())
            .addRole(Role.builder("manager").name("经理")
                    .permission("report:*").parent("user").build())
            .addRole(UserType.C, Role.builder("member").permission("profile:read").build());

    private final DefaultPermissionExplainer explainer = new DefaultPermissionExplainer(loader);

    private final dev.xuya.token.core.model.UserInfo manager =
            new dev.xuya.token.core.model.UserInfo("1", "bob", null, Set.of("manager"), Map.of(), UserType.B);

    @Test
    void allowedPermissionPointsToMatchingGrant() {
        AuthDecision d = explainer.explain(manager, "report:export");
        assertTrue(d.isAllowed());
        assertEquals("report:*", d.getMatchedBy());
        assertTrue(d.getReason().contains("report:*"));
        assertTrue(d.getReason().contains("report:export"));
    }

    @Test
    void inheritedRoleAppearsInTraceWithSource() {
        AuthDecision d = explainer.explain(manager, "user:read");
        assertTrue(d.isAllowed());

        // 轨迹包含继承:user 角色标注"继承自 manager"
        List<RoleTrace> traces = d.getRoles();
        assertEquals(2, traces.size());
        RoleTrace userTrace = traces.stream()
                .filter(t -> t.getCode().equals("user")).findFirst().orElseThrow();
        assertEquals("manager", userTrace.getInheritedFrom());
        assertFalse(userTrace.isDirect());
        assertTrue(userTrace.getPermissions().contains("user:read"));
    }

    @Test
    void deniedPermissionListsAllGrants() {
        AuthDecision d = explainer.explain(manager, "finance:audit");
        assertFalse(d.isAllowed());
        assertNull(d.getMatchedBy());
        // 拒绝原因包含全部已授予权限,便于排错
        assertTrue(d.getReason().contains("user:read"));
        assertTrue(d.getReason().contains("report:*"));
    }

    @Test
    void roleCheckExplainsMembership() {
        AuthDecision allowed = explainer.explain(manager, "user");
        assertTrue(allowed.isRoleCheck());
        assertTrue(allowed.isAllowed());
        assertEquals("user", allowed.getMatchedBy());

        AuthDecision denied = explainer.explain(manager, "admin");
        assertFalse(denied.isAllowed());
        assertTrue(denied.getReason().contains("admin"));
    }

    @Test
    void userTypedRolesOnlyVisibleInThatType() {
        var cMember = new dev.xuya.token.core.model.UserInfo(
                "c1", "alice", null, Set.of("member"), Map.of(), UserType.C);
        assertTrue(explainer.explain(cMember, "profile:read").isAllowed());

        // B 端用户同名角色不可见 → 拒绝
        var bMember = new dev.xuya.token.core.model.UserInfo(
                "1", "bob", null, Set.of("member"), Map.of(), UserType.B);
        assertFalse(explainer.explain(bMember, "profile:read").isAllowed());
    }

    @Test
    void anonymousIsDeniedWithReason() {
        AuthDecision d = explainer.explain(null, "user:read");
        assertFalse(d.isAllowed());
        assertTrue(d.getRoles().isEmpty());
        assertTrue(d.getReason().contains("未登录"));
    }
}
