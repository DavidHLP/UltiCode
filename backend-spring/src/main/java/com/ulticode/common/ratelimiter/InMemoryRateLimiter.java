package com.ulticode.common.ratelimiter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link RateLimiter} — second adapter justifying the seam.
 *
 * <p><strong>Not a {@code @Component}.</strong> Production wiring uses
 * {@link RedisRateLimiter}; this class exists so unit tests of the
 * {@link com.ulticode.common.aspect.RateLimitAspect} can exercise the
 * rate-check path without Redis or Testcontainers. Tests construct it
 * directly ({@code new InMemoryRateLimiter()}) or inject it via Mock.
 *
 * <p>Algorithm: fixed-window counter per bucket. Each bucket tracks its
 * current count and the epoch-millis at which it expires. Synchronized
 * (coarse) — sufficient for tests, where throughput is not a concern.
 * Lazy eviction on access — expired buckets are replaced, never
 * actively reaped, so the map may grow in long-running tests; callers
 * that care should construct a fresh instance per test.
 */
public class InMemoryRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public synchronized AcquisitionVerdict tryAcquire(String bucket, int limit, int periodSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = periodSeconds * 1000L;

        Bucket current = buckets.get(bucket);
        if (current == null || now >= current.expiresAt) {
            current = new Bucket(now + windowMillis);
            buckets.put(bucket, current);
        }
        long newCount = current.count.incrementAndGet();

        if (newCount > limit) {
            long retryAfterMillis = current.expiresAt - now;
            long retryAfterSeconds = Math.max(1L, (retryAfterMillis + 999_999L) / 1000L); // ceil
            return AcquisitionVerdict.denied(retryAfterSeconds);
        }
        return AcquisitionVerdict.granted();
    }

    /** Test helper — clear all buckets between test cases. */
    public void reset() {
        buckets.clear();
    }

    private static final class Bucket {
        final AtomicLong count;
        final long expiresAt;

        Bucket(long expiresAt) {
            this.count = new AtomicLong(0L);
            this.expiresAt = expiresAt;
        }
    }
}
