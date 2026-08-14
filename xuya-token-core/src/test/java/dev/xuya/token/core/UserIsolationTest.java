package dev.xuya.token.core;

import dev.xuya.token.core.auth.Authenticator;
import dev.xuya.token.core.auth.DefaultAuthenticator;
import dev.xuya.token.core.auth.DefaultPermissionChecker;
import dev.xuya.token.core.model.Permission;
import dev.xuya.token.core.model.Role;
import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.session.InMemorySessionManager;
import dev.xuya.token.core.spi.InMemoryPermissionLoader;
import dev.xuya.token.core.spi.UserProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIsolationTest {

    /** B/C 分表的演示用户源:B 端账号密码,C 端手机号验证码。 */
    private final UserProvider provider = new UserProvider() {
        @Override
        public dev.xuya.token.core.model.UserInfo authenticate(String username, String password) {
            return "alice".equals(username) && "pw".equals(password)
                    ? new dev.xuya.token.core.model.UserInfo(
                            "1", "alice", "d2", Set.of("user"), Map.of(), UserType.B)
                    : null;
        }

        @Override
        public dev.xuya.token.core.model.UserInfo authenticate(String userType, String username, String password) {
            if (UserType.C.equalsIgnoreCase(userType)) {
                return "138".equals(username) && "1234".equals(password)
                        ? new dev.xuya.token.core.model.UserInfo(
                                "c1", "bob", null, Set.of("member"), Map.of(), UserType.C)
                        : null;
            }
            return authenticate(username, password);
        }

        @Override
        public dev.xuya.token.core.model.UserInfo findById(String userType, String userId) {
            if (UserType.B.equalsIgnoreCase(userType) && "1".equals(userId)) {
                return new dev.xuya.token.core.model.UserInfo(
                        "1", "alice", "d2", Set.of("user"), Map.of(), UserType.B);
            }
            if (UserType.C.equalsIgnoreCase(userType) && "c1".equals(userId)) {
                return new dev.xuya.token.core.model.UserInfo(
                        "c1", "bob", null, Set.of("member"), Map.of(), UserType.C);
            }
            return null;
        }
    };

    /** 共用角色 admin + C 端专属角色 member(同名/专属均可)。 */
    private final InMemoryPermissionLoader loader = new InMemoryPermissionLoader()
            .addRole(Role.builder("admin").permission("*:*").build())
            .addRole(UserType.C, Role.builder("member").permission("profile:read").build());

    private final Authenticator authenticator =
            new DefaultAuthenticator(provider, new InMemorySessionManager(60_000));

    @Test
    void typedLoginIssuesMatchingUserTypeTokenAndUser() {
        String cToken = authenticator.login(UserType.C, "138", "1234").getToken();
        assertTrue(cToken.startsWith("C-"));
        assertEquals(UserType.C, authenticator.getCurrentUser(cToken).getUserType());
        assertEquals("bob", authenticator.getCurrentUser(cToken).getUsername());

        String bToken = authenticator.login("alice", "pw").getToken();
        assertTrue(bToken.startsWith("B-"));
        assertEquals(UserType.B, authenticator.getCurrentUser(bToken).getUserType());
    }

    @Test
    void credentialsDoNotCrossUserTypes() {
        // C 端手机号在 B 端登录不了;B 端账号在 C 端也登录不了
        assertNull(provider.authenticate(UserType.B, "138", "1234"));
        assertNull(provider.authenticate(UserType.C, "alice", "pw"));
    }

    @Test
    void sessionOfOneTypeCannotResolveInOtherType() {
        // 同一 userId 在 B/C 各自建档:findById 按体系返回
        assertEquals("alice", provider.findById(UserType.B, "1").getUsername());
        assertNull(provider.findById(UserType.C, "1"));
    }

    @Test
    void userTypedRoleIsolation() {
        DefaultPermissionChecker checker = new DefaultPermissionChecker(loader);
        dev.xuya.token.core.model.UserInfo member =
                new dev.xuya.token.core.model.UserInfo("c1", "bob", null, Set.of("member"), Map.of(), UserType.C);

        // C 端 member 拥有体系专属权限
        assertTrue(checker.hasPermission(member, "profile:read"));
        assertFalse(checker.hasPermission(member, "order:delete"));

        // B 端用户同名角色查不到(体系专属仅 C 可见)
        dev.xuya.token.core.model.UserInfo bUser =
                new dev.xuya.token.core.model.UserInfo("1", "alice", "d2", Set.of("member"), Map.of(), UserType.B);
        assertFalse(checker.hasRole(bUser, "member"));
    }

    @Test
    void sharedRolesWorkForAllUserTypes() {
        InMemoryPermissionLoader shared = new InMemoryPermissionLoader()
                .addRole(Role.builder("vip").permission("shop:discount").build());
        dev.xuya.token.core.model.UserInfo cUser =
                new dev.xuya.token.core.model.UserInfo("c1", "bob", null, Set.of("vip"), Map.of(), UserType.C);
        assertTrue(new DefaultPermissionChecker(shared).hasPermission(cUser, "shop:discount"));
        assertTrue(shared.loadPermissions(UserType.C, Set.of("vip")).contains(Permission.of("shop:discount")));
    }
}
