package dev.xuya.token.redis;

import dev.xuya.token.core.exception.ForbiddenException;
import dev.xuya.token.core.model.UserType;
import dev.xuya.token.core.session.Session;
import dev.xuya.token.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSessionManagerTest {

    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> setOps = mock(SetOperations.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    private static String sessionJson(String token, String userId, String userType, long createdAtMillis) {
        return "{\"token\":\"" + token + "\",\"userId\":\"" + userId + "\",\"userType\":\"" + userType
                + "\",\"createdAtMillis\":" + createdAtMillis + ",\"timeoutMillis\":60000}";
    }

    @Test
    void createStoresJsonWithTtlAndIndexesRealm() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        Session session = manager.create("u1");

        // 默认体系 B:token 前缀 B-,索引键 user:B:u1,体系登记 realms:u1
        assertTrue(session.getToken().startsWith("B-"));
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("xuya:token:" + session.getToken()), value.capture(),
                eq(Duration.ofMillis(60_000)));
        assertTrue(value.getValue().contains("\"userType\":\"B\""));

        verify(setOps).add("xuya:token:user:B:u1", session.getToken());
        verify(redisTemplate).expire("xuya:token:user:B:u1", Duration.ofMillis(60_000));
        verify(setOps).add("xuya:token:realms:u1", "B");
    }

    @Test
    void typedCreateUsesRealmSpecificIndex() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        Session session = manager.create(UserType.C, "u1");

        assertTrue(session.getToken().startsWith("C-"));
        verify(setOps).add("xuya:token:user:C:u1", session.getToken());
        verify(setOps).add("xuya:token:realms:u1", "C");
    }

    @Test
    void getResolvesStoredSessionAndRefreshesTtl() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(valueOps.get("xuya:token:B-t1")).thenReturn(sessionJson("B-t1", "u1", "B", 1000));

        Session session = manager.get("B-t1");
        assertNotNull(session);
        assertEquals("u1", session.getUserId());
        assertEquals("B", session.getUserType());
        verify(redisTemplate).expire("xuya:token:B-t1", Duration.ofMillis(60_000));
    }

    @Test
    void legacySessionWithoutUserTypeDefaultsToB() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        String legacy = "{\"token\":\"t1\",\"userId\":\"u1\",\"createdAtMillis\":1000,\"timeoutMillis\":60000}";
        when(valueOps.get("xuya:token:t1")).thenReturn(legacy);
        assertEquals("B", manager.get("t1").getUserType());
    }

    @Test
    void missingKeyReturnsNull() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(valueOps.get(anyString())).thenReturn(null);
        assertNull(manager.get("nope"));
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void invalidateDeletesKeyAndRealmIndexMember() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(valueOps.get("xuya:token:C-t1")).thenReturn(sessionJson("C-t1", "u1", "C", 1000));

        manager.invalidate("C-t1");
        verify(redisTemplate).delete("xuya:token:C-t1");
        verify(setOps).remove("xuya:token:user:C:u1", "C-t1");
    }

    @Test
    void evictsOldestWhenLimitExceeded() {
        SessionManager manager = new RedisSessionManager(redisTemplate,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                "xuya:token:", 60_000, 1, true);
        when(setOps.members("xuya:token:user:B:u1")).thenReturn(new HashSet<>(java.util.Set.of("B-old")));
        when(valueOps.get("xuya:token:B-old")).thenReturn(sessionJson("B-old", "u1", "B", 1000));

        manager.create("u1");
        verify(redisTemplate).delete("xuya:token:B-old");
        verify(setOps).remove("xuya:token:user:B:u1", "B-old");
    }

    @Test
    void rejectsNewLoginWhenEvictDisabled() {
        SessionManager manager = new RedisSessionManager(redisTemplate,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                "xuya:token:", 60_000, 1, false);
        when(setOps.members("xuya:token:user:B:u1")).thenReturn(new HashSet<>(java.util.Set.of("B-existing")));
        when(valueOps.get("xuya:token:B-existing")).thenReturn(sessionJson("B-existing", "u1", "B", 1000));

        assertThrows(ForbiddenException.class, () -> manager.create("u1"));
    }

    @Test
    void invalidateByUserTypeOnlyCleansThatRealm() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(setOps.members("xuya:token:user:C:u1")).thenReturn(new HashSet<>(java.util.Set.of("C-t1")));

        manager.invalidateByUserId(UserType.C, "u1");
        verify(redisTemplate).delete("xuya:token:C-t1");
        verify(redisTemplate).delete("xuya:token:user:C:u1");
        // B 端索引不被触碰
        verify(redisTemplate, never()).delete("xuya:token:user:B:u1");
    }

    @Test
    void invalidateByUserIdCleansAllRealms() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(setOps.members("xuya:token:realms:u1")).thenReturn(new HashSet<>(java.util.Set.of("B", "C")));
        when(setOps.members("xuya:token:user:B:u1")).thenReturn(new HashSet<>(java.util.Set.of("B-t1")));
        when(setOps.members("xuya:token:user:C:u1")).thenReturn(new HashSet<>(java.util.Set.of("C-t1")));

        manager.invalidateByUserId("u1");
        verify(redisTemplate).delete("xuya:token:B-t1");
        verify(redisTemplate).delete("xuya:token:C-t1");
        verify(redisTemplate).delete("xuya:token:user:B:u1");
        verify(redisTemplate).delete("xuya:token:user:C:u1");
        verify(redisTemplate).delete("xuya:token:realms:u1");
    }

    @Test
    void listActiveTokensFiltersByExistence() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(setOps.members("xuya:token:realms:u1")).thenReturn(new HashSet<>(java.util.Set.of("C")));
        when(setOps.members("xuya:token:user:C:u1")).thenReturn(new HashSet<>(java.util.Set.of("C-t1", "C-gone")));
        when(valueOps.get("xuya:token:C-t1")).thenReturn(sessionJson("C-t1", "u1", "C", 1000));
        when(valueOps.get("xuya:token:C-gone")).thenReturn(null);

        assertEquals(java.util.Set.of("C-t1"), manager.listActiveTokens("u1"));
    }

    @Test
    void corruptedJsonReturnsNull() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(valueOps.get("xuya:token:bad")).thenReturn("{not-json");
        assertNull(manager.get("bad"));
    }
}
