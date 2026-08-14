package dev.xuya.token.core;

import dev.xuya.token.core.auth.Authenticator;
import dev.xuya.token.core.auth.DefaultAuthenticator;
import dev.xuya.token.core.exception.UnauthorizedException;
import dev.xuya.token.core.model.UserInfo;
import dev.xuya.token.core.session.InMemorySessionManager;
import dev.xuya.token.core.spi.UserProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultAuthenticatorTest {

    private final UserProvider provider = new UserProvider() {
        @Override
        public UserInfo authenticate(String username, String password) {
            if ("alice".equals(username) && "pw".equals(password)) {
                return new UserInfo("1", "alice", Set.of("user"), Map.of());
            }
            return null;
        }

        @Override
        public UserInfo findById(String userId) {
            return "1".equals(userId) ? new UserInfo("1", "alice", Set.of("user"), Map.of()) : null;
        }
    };

    private final Authenticator authenticator =
            new DefaultAuthenticator(provider, new InMemorySessionManager(60_000));

    @Test
    void loginSuccessReturnsSessionWithUser() {
        String token = authenticator.login("alice", "pw").getToken();
        UserInfo user = authenticator.getCurrentUser(token);
        assertNotNull(user);
        assertEquals("alice", user.getUsername());
    }

    @Test
    void badCredentialsThrowUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> authenticator.login("alice", "wrong"));
    }

    @Test
    void logoutInvalidatesSession() {
        String token = authenticator.login("alice", "pw").getToken();
        authenticator.logout(token);
        assertNull(authenticator.getCurrentUser(token));
    }
}
