package com.ulticode.websecurity.ratelimiter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Production {@link RateLimiter} — backed by Redis via an atomic
 * INCR+EXPIRE Lua script.
 *
 * <p>Algorithm: fixed-window counter per (bucket, period). The first
 * request in a window {@code INCR}s the key from nonexistent to {@code 1}
 * and sets {@code EXPIRE}. Subsequent requests in the same window
 * {@code INCR} without re-setting {@code EXPIRE} (the Lua script always
 * calls EXPIRE — see note). When {@code count > limit}, the request is
 * denied.
 *
 * <p><strong>Note on EXPIRE:</strong> the Lua script calls
 * {@code redis.call('EXPIRE', KEYS[1], ARGV[1])} on every INCR. This
 * refreshes the TTL on every request, which means the effective window
 * is "sliding since last activity" rather than "fixed since first
 * request in window". This is the original behavior preserved by the
 * deep-module extraction — changing the algorithm is out of scope.
 *
 * <p>The {@code "rate-limit:"} key prefix lives here, not in the aspect.
 */
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    /** Key prefix in Redis — owned by this adapter, not by callers. */
    public static final String KEY_PREFIX = "rate-limit:";

    /**
     * Atomic INCR+EXPIRE — both operations run as a single Redis
     * transaction, so two concurrent requests cannot slip through the
     * limit by both observing the pre-increment count.
     */
    private static final String RATE_LIMIT_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
            "redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "return count";

    private final StringRedisTemplate redisTemplate;

    @Override
    public AcquisitionVerdict tryAcquire(String bucket, int limit, int periodSeconds) {
        String redisKey = KEY_PREFIX + bucket;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
        Long count = redisTemplate.execute(script, List.of(redisKey), String.valueOf(periodSeconds));

        if (count != null && count > limit) {
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            long retryAfter = (ttl != null && ttl > 0) ? ttl : periodSeconds;
            return AcquisitionVerdict.denied(retryAfter);
        }
        return AcquisitionVerdict.granted();
    }
}
