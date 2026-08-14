package dev.xuya.token.core;

import dev.xuya.token.core.session.InMemorySessionManager;
import dev.xuya.token.core.session.Session;
import dev.xuya.token.core.session.SessionManager;
import dev.xuya.token.core.session.UserTypeSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiUserTypeTest {

    @Test
    void tokensCarryUserTypePrefix() {
        SessionManager manager = new InMemorySessionManager(60_000);
        assertTrue(manager.create("u1").getToken().startsWith("B-"));
        assertTrue(manager.create("C", "u1").getToken().startsWith("C-"));
        assertTrue(manager.create("OPEN", "u1").getToken().startsWith("OPEN-"));
    }

    @Test
    void invalidUserTypeFallsBackToDefault() {
        SessionManager manager = new InMemorySessionManager(60_000);
        assertTrue(manager.create(null, "u1").getToken().startsWith("B-"));
        assertTrue(manager.create("bad-type", "u1").getToken().startsWith("B-"));
    }

    @Test
    void concurrencyLimitIsPerUserType() {
        // 全局上限 1,B/C 体系各自独立计数
        SessionManager manager = new InMemorySessionManager(60_000, 1, true);
        assertNotNull(manager.create("B", "u1"));
        assertNotNull(manager.create("C", "u1"));
        assertEquals(1, manager.listActiveTokens("u1").stream()
                .filter(t -> t.startsWith("C-")).count());
        assertEquals(1, manager.listActiveTokens("u1").stream()
                .filter(t -> t.startsWith("B-")).count());
    }

    @Test
    void kickByUserTypeLeavesOtherTypeAlone() {
        SessionManager manager = new InMemorySessionManager(60_000);
        Session b = manager.create("B", "u1");
        Session c = manager.create("C", "u1");

        manager.invalidateByUserId("B", "u1");
        assertNull(manager.get(b.getToken()));
        assertNotNull(manager.get(c.getToken()));

        // 不带体系的踢人跨全部体系
        manager.invalidateByUserId("u1");
        assertNull(manager.get(c.getToken()));
    }

    @Test
    void perUserTypeTimeoutAndLimitOverrides() {
        SessionManager manager = new InMemorySessionManager(60_000, 0, true,
                Map.of("C", new UserTypeSettings(120_000L, 5)));
        Session c = manager.create("C", "u1");
        Session b = manager.create("B", "u1");
        assertEquals(120_000L, c.getTimeoutMillis());
        assertEquals(60_000L, b.getTimeoutMillis());
    }

    @Test
    void sameUserDifferentTypesHaveDistinctTokens() {
        SessionManager manager = new InMemorySessionManager(60_000);
        Session b = manager.create("B", "u1");
        Session c = manager.create("C", "u1");
        assertNotEquals(b.getToken(), c.getToken());
        assertEquals(2, manager.listActiveTokens("u1").size());
    }
}
