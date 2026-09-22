package com.ulticode.redis;

import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Owner-neutral Redis cache write policy shared by the owner services
 * (same seam as {@link RedisValueSerializationPolicy}).
 *
 * <p>Every workload role is granted {@code +scan} but not {@code +keys}
 * ({@code docker/redis/generate-users-acl.sh}), so a cache clear must never
 * fall back to {@code KEYS}. Spring's default cache writer clears with
 * {@code KEYS} ({@link BatchStrategies#keys()}), which fails closed with
 * {@code NOPERM} and breaks every {@code @CacheEvict(allEntries = true)} and
 * {@code Cache#clear()} path with a 500.
 *
 * <p>Clearing with {@code SCAN} + {@code DEL} stays inside the granted command
 * set. The scan pattern is the cache's own key prefix ({@code name::*}), so the
 * keys deleted always match the role's key patterns.
 */
public final class RedisCacheWritePolicy {

    /** Keys per SCAN round trip; a clear costs one DEL per returned batch. */
    private static final int SCAN_BATCH_SIZE = 500;

    private RedisCacheWritePolicy() {
    }

    /** Non-locking cache writer whose eviction uses SCAN, never KEYS. */
    public static RedisCacheWriter scanningWriter(RedisConnectionFactory connectionFactory) {
        return RedisCacheWriter.nonLockingRedisCacheWriter(
                connectionFactory, BatchStrategies.scan(SCAN_BATCH_SIZE));
    }
}
