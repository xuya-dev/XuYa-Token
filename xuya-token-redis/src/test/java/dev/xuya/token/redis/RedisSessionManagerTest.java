package dev.xuya.token.redis;

import dev.xuya.token.core.exception.ForbiddenException;
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

    private static String sessionJson(String token, String userId, long createdAtMillis) {
        return "{\"token\":\"" + token + "\",\"userId\":\"" + userId
                + "\",\"createdAtMillis\":" + createdAtMillis + ",\"timeoutMillis\":60000}";
    }

    @Test
    void createStoresJsonWithTtlAndIndexesUser() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        Session session = manager.create("u1");

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("xuya:token:" + session.getToken()), value.capture(),
                eq(Duration.ofMillis(60_000)));
        assertTrue(value.getValue().contains("\"userId\":\"u1\""));

        verify(setOps).add("xuya:token:user:u1", session.getToken());
        verify(redisTemplate).expire("xuya:token:user:u1", Duration.ofMillis(60_000));
    }

    @Test
    void getResolvesStoredSessionAndRefreshesTtl() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(valueOps.get("xuya:token:t1")).thenReturn(sessionJson("t1", "u1", 1000));

        Session session = manager.get("t1");
        assertNotNull(session);
        assertEquals("u1", session.getUserId());
        verify(redisTemplate).expire("xuya:token:t1", Duration.ofMillis(60_000));
    }

    @Test
    void missingKeyReturnsNull() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(valueOps.get(anyString())).thenReturn(null);
        assertNull(manager.get("nope"));
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void nullTokenReturnsNullWithoutRedisCall() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        assertNull(manager.get(null));
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void invalidateDeletesKeyAndIndexMember() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(valueOps.get("xuya:token:t1")).thenReturn(sessionJson("t1", "u1", 1000));

        manager.invalidate("t1");
        verify(redisTemplate).delete("xuya:token:t1");
        verify(setOps).remove("xuya:token:user:u1", "t1");
    }

    @Test
    void corruptedJsonReturnsNull() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(valueOps.get("xuya:token:bad")).thenReturn("{not-json");
        assertNull(manager.get("bad"));
    }

    @Test
    void evictsOldestWhenLimitExceeded() {
        SessionManager manager = new RedisSessionManager(redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper(),
                "xuya:token:", 60_000, 1, true);
        // 索引中已有一个更旧的会话
        when(setOps.members("xuya:token:user:u1")).thenReturn(new HashSet<>(java.util.Set.of("old")));
        when(valueOps.get("xuya:token:old")).thenReturn(sessionJson("old", "u1", 1000));

        manager.create("u1");

        // 最旧的 "old" 被删除并移出索引
        verify(redisTemplate).delete("xuya:token:old");
        verify(setOps).remove("xuya:token:user:u1", "old");
    }

    @Test
    void rejectsNewLoginWhenEvictDisabled() {
        SessionManager manager = new RedisSessionManager(redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper(),
                "xuya:token:", 60_000, 1, false);
        when(setOps.members("xuya:token:user:u1")).thenReturn(new HashSet<>(java.util.Set.of("existing")));
        when(valueOps.get("xuya:token:existing")).thenReturn(sessionJson("existing", "u1", 1000));

        assertThrows(ForbiddenException.class, () -> manager.create("u1"));
    }

    @Test
    void invalidateByUserIdKicksAllSessions() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(setOps.members("xuya:token:user:u1")).thenReturn(new HashSet<>(java.util.Set.of("t1", "t2")));

        manager.invalidateByUserId("u1");
        verify(redisTemplate).delete("xuya:token:t1");
        verify(redisTemplate).delete("xuya:token:t2");
        verify(redisTemplate).delete("xuya:token:user:u1");
    }

    @Test
    void listActiveTokensFiltersByExistence() {
        SessionManager manager = new RedisSessionManager(redisTemplate, 60_000);
        when(setOps.members("xuya:token:user:u1")).thenReturn(new HashSet<>(java.util.Set.of("t1", "gone")));
        when(valueOps.get("xuya:token:t1")).thenReturn(sessionJson("t1", "u1", 1000));
        when(valueOps.get("xuya:token:gone")).thenReturn(null);

        assertEquals(java.util.Set.of("t1"), manager.listActiveTokens("u1"));
    }
}
