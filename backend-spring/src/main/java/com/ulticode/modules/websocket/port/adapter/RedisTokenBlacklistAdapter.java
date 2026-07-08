package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.websocket.port.TokenBlacklistPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed adapter of {@link TokenBlacklistPort}.
 *
 * <p>Owns three implementation concerns that the consumer
 * ({@link com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor})
 * must not see:
 *
 * <ol>
 *   <li><strong>SHA-256 fingerprinting.</strong> Raw JWTs are never stored as
 *       Redis keys &mdash; the key is the hex SHA-256 of the token. This
 *       limits the value of a Redis dump to an attacker and keeps key length
 *       uniform. Mirrors the hash-only storage discipline already used by
 *       {@code RefreshTokenService} for refresh tokens (see
 *       {@code wiki/concepts/refresh-token-hash-only-storage.md}).</li>
 *   <li><strong>Key-prefix convention.</strong> Every key is namespaced as
 *       {@code blacklist:token:<sha256>} so the keyspace is self-describing
 *       when inspected via {@code KEYS} / RedisInsight.</li>
 *   <li><strong>Storage I/O.</strong> The single {@code hasKey} round-trip
 *       and any resulting {@link DataAccessException} propagation.</li>
 * </ol>
 *
 * <p><strong>Read-only by design.</strong> The previous
 * {@code com.ulticode.common.service.TokenBlacklistService} also exposed
 * {@code blacklistToken(...)} and {@code removeFromBlacklist(...)}; a repo-wide
 * audit found zero callers (runtime revocation is owned by
 * {@link com.ulticode.modules.refreshtoken.service.RefreshTokenService}).
 * Those write methods were dead code and are deliberately not ported &mdash;
 * see {@link TokenBlacklistPort}'s class Javadoc for the rationale and the
 * path for a future admin instant-revoke feature to add its own writer port.
 *
 * <p><strong>Fail-closed.</strong> On Redis connection failure the underlying
 * {@code RedisConnectionFailureException} (a subclass of
 * {@link DataAccessException}) propagates to the caller &mdash; the WS
 * interceptor rejects the CONNECT. This matches the pre-refactor behaviour
 * and the security contract on {@link TokenBlacklistPort}.
 *
 * @author ulticode
 */
@Component
public class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    /** Key prefix for the blacklist keyspace. Visible for tests. */
    static final String TOKEN_BLACKLIST_PREFIX = "blacklist:token:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenBlacklistAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isBlacklisted(String token) {
        // SECURITY: do NOT add a null-check-returns-false here. Returning
        // false on null would fail-OPEN on this auth seam. The port contract
        // guarantees non-null; the MessageDigest NPE below is the intended
        // fail-closed signal if a caller ever violates that contract.
        String key = getBlacklistKey(token);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Build the Redis key for a token. Combines the prefix convention with
     * the SHA-256 fingerprint so raw JWTs never reach Redis.
     */
    private String getBlacklistKey(String token) {
        return TOKEN_BLACKLIST_PREFIX + sha256Hex(token);
    }

    /**
     * SHA-256 hash the token and hex-encode the result.
     *
     * <p>SHA-256 is mandated by the platform JCA on every compliant JVM
     * ({@code java.security.MessageDigest} documents it as a required
     * algorithm), so {@link NoSuchAlgorithmException} is unreachable on any
     * supported platform. Rethrowing as {@link IllegalStateException} keeps
     * the signature honest without misleading callers into writing a
     * recovery branch that can never execute.
     */
    private static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                "SHA-256 not available on this JVM — required algorithm per JCA spec", e);
        }
    }
}
