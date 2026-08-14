package dev.xuya.token.core;

import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.security.InMemoryLoginGuard;
import dev.xuya.token.core.spi.CachedUserProvider;
import dev.xuya.token.core.spi.UserProvider;
import dev.xuya.token.core.session.InMemorySessionManager;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceHygieneTest {

    @Test
    void sweepRemovesExpiredSessionsAndEmptyIndex() {
        InMemorySessionManager manager = new InMemorySessionManager(-1);
        manager.create("u1");
        manager.create("u2");

        // 未命中过 get():过期会话仍滞留,靠 sweep 回收
        int removed = manager.sweep();
        assertEquals(2, removed);
        assertEquals(0, manager.listActiveTokens("u1").size());
        assertEquals(0, manager.listActiveTokens("u2").size());
    }

    @Test
    void sweepKeepsActiveSessions() {
        InMemorySessionManager manager = new InMemorySessionManager(60_000);
        manager.create("B", "u1");
        assertEquals(0, manager.sweep());
        assertEquals(1, manager.listActiveTokens("u1").size());
    }

    @Test
    void guardEvictsOldestStateUnderCapacity() {
        // 阈值 2 次,容量 2 个账号:第 3 个账号会把最久未访问的状态挤出去
        InMemoryLoginGuard guard = new InMemoryLoginGuard(2, 60_000, 2);

        guard.record("B", "u1", false);
        guard.record("B", "u2", false);
        guard.record("B", "u3", false); // u1 被淘汰,计数归零

        // u1 只累计了淘汰后的 1 次,未达阈值 → 不锁定(返回而非抛 403)
        guard.record("B", "u1", false);
        guard.check("B", "u1");

        // u2 保持 1 次计数,同样未锁定
        guard.check("B", "u2");
    }

    @Test
    void cachedUserProviderCachesFindByIdOnly() {
        AtomicInteger loads = new AtomicInteger();
        UserProvider delegate = new UserProvider() {
            @Override
            public dev.xuya.token.core.model.UserInfo authenticate(String username, String password) {
                return null;
            }

            @Override
            public dev.xuya.token.core.model.UserInfo findById(String userType, String userId) {
                loads.incrementAndGet();
                return "1".equals(userId)
                        ? new dev.xuya.token.core.model.UserInfo(
                                "1", "alice", null, Set.of("user"), Map.of(), userType)
                        : null;
            }
        };
        CachedUserProvider cached = new CachedUserProvider(delegate, 60_000);

        cached.findById(UserType.B, "1");
        cached.findById(UserType.B, "1");
        assertEquals(1, loads.get());

        // 体系独立缓存键
        cached.findById(UserType.C, "1");
        assertEquals(2, loads.get());

        // authenticate 永远穿透
        cached.authenticate("alice", "pw");
        cached.authenticate(UserType.C, "138", "1234");
        assertEquals(2, loads.get());

        // 失效后强制回源
        cached.invalidate(UserType.B, "1");
        cached.findById(UserType.B, "1");
        assertEquals(3, loads.get());
    }

    @Test
    void cachedUserProviderExpiry() {
        AtomicInteger loads = new AtomicInteger();
        UserProvider delegate = new UserProvider() {
            @Override
            public dev.xuya.token.core.model.UserInfo authenticate(String u, String p) {
                return null;
            }

            @Override
            public dev.xuya.token.core.model.UserInfo findById(String userType, String userId) {
                loads.incrementAndGet();
                return null;
            }
        };
        CachedUserProvider cached = new CachedUserProvider(delegate, 0);
        cached.findById(UserType.B, "1");
        cached.findById(UserType.B, "1");
        assertEquals(2, loads.get());
    }

    @Test
    void guardOriginalBehaviorStillWorks() {
        InMemoryLoginGuard guard = new InMemoryLoginGuard(2, 60_000);
        guard.record(UserType.B, "alice", false);
        guard.record(UserType.B, "alice", false);
        assertThrows(dev.xuya.token.core.exception.ForbiddenException.class,
                () -> guard.check(UserType.B, "alice"));
    }
}
