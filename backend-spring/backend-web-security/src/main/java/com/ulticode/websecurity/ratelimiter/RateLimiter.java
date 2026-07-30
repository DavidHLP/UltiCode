package com.ulticode.websecurity.ratelimiter;

/**
 * Rate-limiter port — owns the "is this bucket allowed?" mechanism behind a
 * single method, hiding the storage (Redis in prod, in-memory in tests) and
 * the algorithm (atomic INCR+EXPIRE Lua script, sliding-window, etc.).
 *
 * <p><strong>Seam justification — two adapters justify it:</strong>
 * <ul>
 *   <li>{@link RedisRateLimiter} — production, atomic INCR+EXPIRE Lua script</li>
 *   <li>{@link InMemoryRateLimiter} — unit tests, synchronized {@code ConcurrentHashMap}</li>
 * </ul>
 *
 * <p>The aspect ({@link com.ulticode.websecurity.aspect.RateLimitAspect})
 * retains request-context key generation (placeholder substitution, user/IP
 * detection); this port owns only the storage-facing rate check.
 * Callers pass a fully-resolved bucket name; the adapter owns any
 * storage-specific key prefix.
 */
public interface RateLimiter {

    /**
     * Atomically increment the bucket counter and check the limit.
     *
     * @param bucket         fully-resolved bucket name (caller owns naming;
     *                       adapter owns any storage-specific key prefix)
     * @param limit          max acquisitions allowed in the window; must be &gt; 0
     * @param periodSeconds  window length in seconds; must be &gt; 0
     * @return verdict — {@code allowed=true} if the caller may proceed;
     *         otherwise {@code allowed=false} with {@code retryAfterSeconds}
     */
    AcquisitionVerdict tryAcquire(String bucket, int limit, int periodSeconds);
}
