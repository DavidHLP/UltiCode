package com.ulticode.auth.security.csrf;

import cn.hutool.core.util.IdUtil;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

/**
 * CSRF Token service backed by Redis.
 *
 * <p>Private copy inside backend-auth of backend-legacy's
 * {@code com.ulticode.security.csrf.CsrfService}. The Strangler Fig
 * contract keeps backend-legacy's copy unchanged until Phase 4 cutover;
 * the only intentional differences are:
 *
 * <ul>
 *   <li>{@link RedisTemplate} is resolved through {@link ObjectProvider}
 *       so the unit-test slice (which excludes
 *       {@code RedisAutoConfiguration}) can still load the Spring context.
 *       At runtime, Redis is required and the provider always yields a
 *       concrete {@code RedisTemplate}; missing at runtime is a wiring
 *       bug surfaced immediately by the first call.
 *   <li>Token format and TTLs are byte-identical to the legacy copy, so
 *       tokens issued by either service are interchangeable on the same
 *       Redis cluster (relevant during cutover overlap).
 * </ul>
 *
 * <p>Security model:
 *
 * <ul>
 *   <li>24-hour TTL on issue.
 *   <li>5-minute grace-period TTL on rotation (the old token still verifies
 *       for a brief window so concurrent browser tabs don't log each other
 *       out).
 *   <li>{@code tokenId:tokenValue} format, regenerated on every rotation.
 *   <li>Clear-all on logout via a SCAN + DEL over the user's CSRF prefix.
 * </ul>
 */
@Slf4j
@Service
public class CsrfService {

    private final ObjectProvider<RedisTemplate<String, String>> redisTemplateProvider;

    private static final String CSRF_PREFIX = "csrf:";
    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final Duration GRACE_TTL = Duration.ofMinutes(5);

    public CsrfService(ObjectProvider<RedisTemplate<String, String>> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    private RedisTemplate<String, String> requireRedis() {
        RedisTemplate<String, String> template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException(
                    "RedisTemplate is required for CSRF token storage; "
                            + "ensure spring-boot-starter-data-redis is on the classpath and "
                            + "RedisAutoConfiguration is not excluded at runtime.");
        }
        return template;
    }

    /**
     * Generate a CSRF token and store it in Redis with a 24-hour TTL.
     */
    public String generateToken(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }

        String tokenId = IdUtil.simpleUUID();
        String token = IdUtil.simpleUUID();
        String key = buildKey(userId, tokenId);

        requireRedis().opsForValue().set(key, token, TOKEN_TTL);

        log.debug("Generated CSRF token for user: {}, tokenId: {}", userId, tokenId);
        return tokenId + ":" + token;
    }

    /**
     * Validate a CSRF token, apply a 5-minute grace period, and return a freshly
     * rotated token.
     *
     * @return the new token, or {@code null} if the submitted token is missing,
     *         malformed, or does not match the stored value.
     */
    public String validateAndRotateToken(String userId, String token) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }
        if (token == null || token.isEmpty()) {
            log.debug("CSRF token is null or empty for user: {}", userId);
            return null;
        }

        String[] parts = token.split(":");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            log.warn("Invalid CSRF token format for user: {}", userId);
            return null;
        }

        String tokenId = parts[0];
        String tokenValue = parts[1];
        String key = buildKey(userId, tokenId);
        RedisTemplate<String, String> redis = requireRedis();

        String storedValue = redis.opsForValue().get(key);
        if (storedValue == null || !storedValue.equals(tokenValue)) {
            log.warn("CSRF token validation failed for user: {}, tokenId: {}", userId, tokenId);
            return null;
        }

        // Rotation: 5-minute grace period on the old key, then issue a new
        // token. The old key still verifies for 5 minutes so concurrent
        // browser tabs do not log each other out mid-action.
        redis.opsForValue().set(key, storedValue, GRACE_TTL);
        log.debug("CSRF token validated and rotated for user: {}", userId);
        return generateToken(userId);
    }

    /**
     * Clear all CSRF tokens for a user. Called on logout.
     */
    public void clearUserTokens(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }

        String pattern = CSRF_PREFIX + userId + ":*";
        RedisTemplate<String, String> redis = requireRedis();
        try (Cursor<String> keys = redis.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            int count = 0;
            while (keys.hasNext()) {
                redis.delete(keys.next());
                count++;
            }
            log.debug("Cleared {} CSRF tokens for user: {}", count, userId);
        }
    }

    private String buildKey(String userId, String tokenId) {
        return CSRF_PREFIX + userId + ":" + tokenId;
    }
}
