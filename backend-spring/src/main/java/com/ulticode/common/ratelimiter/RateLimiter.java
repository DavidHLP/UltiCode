package com.ulticode.common.ratelimiter;

/**
 * Rate-limiter port — owns the "is this bucket allowed?" mechanism behind a
 * single method, hiding the storage (Redis in prod, in-memory in tests) and
 * the algorithm (atomic INCR+EXPIRE Lua script, sliding-window, etc.).
 *
 * <p>Prior to this deep module, {@link com.ulticode.common.aspect.RateLimitAspect}
 * embedded the Redis Lua script, the {@code "rate-limit:"} key prefix, and a
 * direct {@code StringRedisTemplate} injection. Callers (155 {@code @RateLimit}
 * sites across 33 controllers) could not see what they were getting; the
 * aspect could not be unit-tested without Redis. See
 * {@code /tmp/architecture-review-1783420414.html} candidate 5.
 *
 * <p><strong>Seam justification — two adapters justify it:</strong>
 * <ul>
 *   <li>{@link RedisRateLimiter} — production, atomic INCR+EXPIRE Lua script</li>
 *   <li>{@link InMemoryRateLimiter} — unit tests, synchronized {@code ConcurrentHashMap}</li>
 * </ul>
 *
 * <p>The aspect retains request-context key generation (placeholder
 * substitution, user/IP detection); this port owns only the storage-facing
 * rate check. Callers pass a fully-resolved bucket name; the adapter owns
 * any storage-specific key prefix.
 */
public interface RateLimiter {

    /**
     * Atomically increment the bucket counter and check the limit.
     *
     * <p>Implementations must be thread-safe and atomic across the
     * increment-and-check pair (no TOCTOU window where two concurrent
     * requests both see "under the limit" and both proceed).
     *
     * @param bucket         fully-resolved bucket name (caller owns naming;
     *                       adapter owns any storage-specific key prefix)
     * @param limit          max acquisitions allowed in the window; must be &gt; 0
     * @param periodSeconds  window length in seconds; must be &gt; 0
     * @return verdict — {@code allowed=true} if the caller may proceed;
     *         otherwise {@code allowed=false} with {@code retryAfterSeconds}
     *         set to the remaining window length
     */
    AcquisitionVerdict tryAcquire(String bucket, int limit, int periodSeconds);
}
