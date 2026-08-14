package dev.xuya.token.core;

import dev.xuya.token.core.audit.AuthAuditListener;
import dev.xuya.token.core.auth.Authenticator;
import dev.xuya.token.core.auth.DefaultAuthenticator;
import dev.xuya.token.core.exception.ForbiddenException;
import dev.xuya.token.core.exception.UnauthorizedException;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.security.InMemoryLoginGuard;
import dev.xuya.token.core.session.InMemorySessionManager;
import dev.xuya.token.core.spi.UserProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginGuardAndAuditTest {

    /** 记录型审计监听器。 */
    static class RecordingListener implements AuthAuditListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onLoginSuccess(String userType, String username, String userId) {
            events.add("success:" + userType + ":" + username + ":" + userId);
        }

        @Override
        public void onLoginFailure(String userType, String username) {
            events.add("failure:" + userType + ":" + username);
        }

        @Override
        public void onLogout(String userType, String userId) {
            events.add("logout:" + userType + ":" + userId);
        }
    }

    private final UserProvider provider = new UserProvider() {
        @Override
        public UserInfo authenticate(String username, String password) {
            return "alice".equals(username) && "pw".equals(password)
                    ? new UserInfo("1", "alice", null, Set.of("user"), Map.of())
                    : null;
        }

        @Override
        public UserInfo findById(String userId) {
            return "1".equals(userId)
                    ? new UserInfo("1", "alice", null, Set.of("user"), Map.of())
                    : null;
        }
    };

    @Test
    void locksAfterConsecutiveFailures() {
        Authenticator auth = new DefaultAuthenticator(provider,
                new InMemorySessionManager(60_000), new InMemoryLoginGuard(3, 60_000), List.of());

        // 连续失败 3 次触发锁定
        assertThrows(UnauthorizedException.class, () -> auth.login("alice", "wrong"));
        assertThrows(UnauthorizedException.class, () -> auth.login("alice", "wrong"));
        assertThrows(UnauthorizedException.class, () -> auth.login("alice", "wrong"));

        // 锁定期内即使密码正确也被拒绝(403)
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> auth.login("alice", "pw"));
        assertTrue(ex.getMessage().contains("locked"));
    }

    @Test
    void lockExpiresAutomatically() {
        // 锁定时长为 -1:写入即过期,下一次登录自动解锁
        Authenticator auth = new DefaultAuthenticator(provider,
                new InMemorySessionManager(60_000), new InMemoryLoginGuard(2, -1), List.of());
        assertThrows(UnauthorizedException.class, () -> auth.login("alice", "wrong"));
        assertThrows(UnauthorizedException.class, () -> auth.login("alice", "wrong"));
        // 第三次:锁已过期,正确密码可登录
        assertTrue(auth.login("alice", "pw").getToken().startsWith("B-"));
    }

    @Test
    void successResetsFailureCount() {
        Authenticator auth = new DefaultAuthenticator(provider,
                new InMemorySessionManager(60_000), new InMemoryLoginGuard(3, 60_000), List.of());
        assertThrows(UnauthorizedException.class, () -> auth.login("alice", "wrong"));
        assertThrows(UnauthorizedException.class, () -> auth.login("alice", "wrong"));
        auth.login("alice", "pw");
        // 计数已清零:再失败两次(2 < 3)不会触发锁定
        assertThrows(UnauthorizedException.class, () -> auth.login("alice", "wrong"));
        assertThrows(UnauthorizedException.class, () -> auth.login("alice", "wrong"));
        assertTrue(auth.login("alice", "pw").getToken().startsWith("B-"));
    }

    @Test
    void auditEventsArePublished() {
        RecordingListener listener = new RecordingListener();
        Authenticator auth = new DefaultAuthenticator(provider,
                new InMemorySessionManager(60_000), null, List.of(listener));

        String token = auth.login("alice", "pw").getToken();
        assertThrows(UnauthorizedException.class, () -> auth.login("alice", "bad"));
        auth.logout(token);

        assertEquals(List.of(
                "success:B:alice:1",
                "failure:B:alice",
                "logout:B:1"), listener.events);
    }

    @Test
    void auditExceptionDoesNotBreakAuth() {
        Authenticator auth = new DefaultAuthenticator(provider,
                new InMemorySessionManager(60_000), null,
                List.of(new AuthAuditListener() {
                    @Override
                    public void onLoginSuccess(String userType, String username, String userId) {
                        throw new IllegalStateException("boom");
                    }
                }));
        assertTrue(auth.login("alice", "pw").getToken().startsWith("B-"));
    }
}
