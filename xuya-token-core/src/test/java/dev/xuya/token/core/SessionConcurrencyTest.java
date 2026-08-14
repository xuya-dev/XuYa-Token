package dev.xuya.token.core;

import dev.xuya.token.core.exception.ForbiddenException;
import dev.xuya.token.core.session.InMemorySessionManager;
import dev.xuya.token.core.session.Session;
import dev.xuya.token.core.session.SessionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionConcurrencyTest {

    @Test
    void evictsOldestWhenLimitExceeded() {
        SessionManager manager = new InMemorySessionManager(60_000, 2, true);
        Session first = manager.create("u1");
        Session second = manager.create("u1");
        Session third = manager.create("u1");

        // 三个会话同毫秒创建时"最旧"存在并列,只断言确定性结果:
        // 会话数收敛到上限,且新会话必定存活
        int alive = 0;
        if (manager.get(first.getToken()) != null) {
            alive++;
        }
        if (manager.get(second.getToken()) != null) {
            alive++;
        }
        assertNotNull(manager.get(third.getToken()));
        assertEquals(2, manager.listActiveTokens("u1").size());
        // 两个旧会话中被顶替掉一个,恰剩一个存活
        org.junit.jupiter.api.Assertions.assertEquals(1, alive);
    }

    @Test
    void rejectsNewLoginWhenEvictDisabled() {
        SessionManager manager = new InMemorySessionManager(60_000, 1, false);
        Session first = manager.create("u1");

        assertThrows(ForbiddenException.class, () -> manager.create("u1"));
        // 原会话不受影响
        assertNotNull(manager.get(first.getToken()));
        assertEquals(1, manager.listActiveTokens("u1").size());
    }

    @Test
    void limitIsPerUser() {
        SessionManager manager = new InMemorySessionManager(60_000, 1, true);
        assertNotNull(manager.create("u1"));
        assertNotNull(manager.create("u2"));
        assertEquals(1, manager.listActiveTokens("u1").size());
        assertEquals(1, manager.listActiveTokens("u2").size());
    }

    @Test
    void invalidateByUserIdKicksAllSessions() {
        SessionManager manager = new InMemorySessionManager(60_000);
        Session a = manager.create("u1");
        Session b = manager.create("u1");
        manager.create("u2");

        manager.invalidateByUserId("u1");
        assertNull(manager.get(a.getToken()));
        assertNull(manager.get(b.getToken()));
        assertEquals(0, manager.listActiveTokens("u1").size());
        assertEquals(1, manager.listActiveTokens("u2").size());
    }

    @Test
    void expiredSessionsDroppedFromIndex() {
        SessionManager manager = new InMemorySessionManager(-1, 1, true);
        manager.create("u1");
        // 旧会话已过期,索引清理后新登录不应被并发上限拒绝
        Session fresh = manager.create("u1");
        assertNotNull(fresh);
        // timeout=-1 时所有会话立即过期,在线列表应为空
        assertEquals(0, manager.listActiveTokens("u1").size());
    }
}
