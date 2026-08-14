package dev.xuya.token.jwt;

import dev.xuya.token.core.session.Session;
import dev.xuya.token.core.session.SessionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtSessionManagerTest {

    /** 32+ 字节测试密钥。 */
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void createThenGetRoundTrip() {
        SessionManager manager = new JwtSessionManager(SECRET, 60_000);
        Session session = manager.create("u1");

        Session resolved = manager.get(session.getToken());
        assertNotNull(resolved);
        assertEquals("u1", resolved.getUserId());
        assertTrue(resolved.getTimeoutMillis() > 0 && resolved.getTimeoutMillis() <= 60_000);
    }

    @Test
    void tokensAreSelfContainedAcrossInstances() {
        // 无状态:另一个实例(同密钥)即可校验,模拟多节点部署
        Session session = new JwtSessionManager(SECRET, 60_000).create("u1");
        Session resolved = new JwtSessionManager(SECRET, 60_000).get(session.getToken());
        assertNotNull(resolved);
        assertEquals("u1", resolved.getUserId());
    }

    @Test
    void tamperedTokenRejected() {
        SessionManager manager = new JwtSessionManager(SECRET, 60_000);
        String token = manager.create("u1").getToken();
        assertNull(manager.get(token + "x"));
    }

    @Test
    void wrongSecretRejected() {
        String token = new JwtSessionManager(SECRET, 60_000).create("u1").getToken();
        SessionManager other = new JwtSessionManager("ffffffffffffffffffffffffffffffff", 60_000);
        assertNull(other.get(token));
    }

    @Test
    void expiredTokenReturnsNull() {
        SessionManager manager = new JwtSessionManager(SECRET, -1_000);
        String token = manager.create("u1").getToken();
        assertNull(manager.get(token));
    }

    @Test
    void nullAndEmptyTokenReturnNull() {
        SessionManager manager = new JwtSessionManager(SECRET, 60_000);
        assertNull(manager.get(null));
        assertNull(manager.get(""));
    }

    @Test
    void shortSecretRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JwtSessionManager("short", 60_000));
    }

    @Test
    void statelessOperationsAreNoOp() {
        SessionManager manager = new JwtSessionManager(SECRET, 60_000);
        // 不抛异常即可;注销/在线列表在无状态模式下为空实现
        manager.invalidate("any");
        manager.invalidateByUserId("u1");
        assertTrue(manager.listActiveTokens("u1").isEmpty());
    }
}
