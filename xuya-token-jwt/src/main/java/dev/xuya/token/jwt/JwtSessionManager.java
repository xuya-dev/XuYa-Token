package dev.xuya.token.jwt;

import dev.xuya.token.core.session.Session;
import dev.xuya.token.core.session.SessionManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * 基于 JWT(HS256)的无状态 {@link SessionManager}:服务端不存储会话,
 * 登录签发令牌、校验仅验签与验期,天然支持多节点水平扩展。
 *
 * <p><b>与有状态会话的取舍</b>:过期为签发时刻起的绝对时长
 * ({@code timeoutMillis} 进入 exp 声明,不支持空闲续期);
 * {@code invalidate} / {@code invalidateByUserId} / {@code listActiveTokens}
 * 为空实现 —— 无法服务端注销、无法踢人、无在线列表。
 * 需要这些能力时请使用内存或 Redis 会话模式。
 *
 * @author 青衣
 */
public class JwtSessionManager implements SessionManager {

    /** HS256 签名密钥。 */
    private final SecretKey key;

    /** 令牌绝对有效期,单位毫秒(写入 exp 声明)。 */
    private final long timeoutMillis;

    /**
     * 构造 JWT 会话管理器。
     *
     * @param secret         HS256 密钥,至少 32 字节(UTF-8)
     * @param timeoutMillis  令牌绝对有效期(毫秒)
     * @throws IllegalArgumentException 密钥长度不足 32 字节时抛出
     */
    public JwtSessionManager(String secret, long timeoutMillis) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 32 bytes for HS256, got: " + bytes.length);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.timeoutMillis = timeoutMillis;
    }

    /** 为用户签发 JWT(sub=用户 ID,iat=签发时间,exp=签发时间+绝对有效期)。 */
    @Override
    public Session create(String userId) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(timeoutMillis)))
                .signWith(key)
                .compact();
        return new Session(token, userId, now, timeoutMillis);
    }

    /** 验签并解析令牌;签名无效、已过期或格式非法返回 {@code null},会话剩余时长按 exp 声明推算。 */
    @Override
    public Session get(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remaining <= 0) {
                return null;
            }
            return new Session(token, claims.getSubject(),
                    claims.getIssuedAt().toInstant(), remaining);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 无状态模式无法服务端注销,空实现;调用方删除客户端令牌即可。 */
    @Override
    public void invalidate(String token) {
    }

    /** 无状态模式无法按用户注销,空实现。 */
    @Override
    public void invalidateByUserId(String userId) {
    }

    /** 无状态模式无服务端索引,恒返回空集。 */
    @Override
    public Set<String> listActiveTokens(String userId) {
        return Set.of();
    }
}
