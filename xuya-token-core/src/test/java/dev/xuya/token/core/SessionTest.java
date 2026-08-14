package dev.xuya.token.core;

import dev.xuya.token.core.session.InMemorySessionManager;
import dev.xuya.token.core.session.Session;
import dev.xuya.token.core.session.SessionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionTest {

    @Test
    void createGetInvalidate() {
        SessionManager manager = new InMemorySessionManager(60_000);
        Session session = manager.create("u1");
        assertNotNull(manager.get(session.getToken()));
        manager.invalidate(session.getToken());
        assertNull(manager.get(session.getToken()));
    }

    @Test
    void expiredSessionIsNull() {
        SessionManager manager = new InMemorySessionManager(-1);
        Session session = manager.create("u1");
        assertNull(manager.get(session.getToken()));
    }

    @Test
    void unknownTokenIsNull() {
        SessionManager manager = new InMemorySessionManager(60_000);
        assertNull(manager.get("nope"));
    }
}
