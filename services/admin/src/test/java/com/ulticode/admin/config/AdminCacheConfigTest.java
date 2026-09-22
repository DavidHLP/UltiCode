package com.ulticode.admin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.ScanOptions;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression for the admin avatar upload 500: the admin role's ACL grants
 * {@code +scan} but not {@code +keys}, so a cache clear backed by Spring's
 * default ({@code KEYS}) writer fails closed with {@code NOPERM}.
 */
class AdminCacheConfigTest {

    @Test
    @DisplayName("cache clear evicts with SCAN inside the cache prefix and never issues KEYS")
    void cacheClearEvictsThroughScan() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.keyCommands()).thenReturn(keyCommands);
        AtomicReference<ScanOptions> scan = new AtomicReference<>();
        when(connection.scan(any(ScanOptions.class))).thenAnswer(call -> {
            scan.set(call.getArgument(0));
            throw new IllegalStateException("scan reached");
        });
        // The role's real reply to the default writer's eviction.
        when(keyCommands.keys(any(byte[].class))).thenThrow(new InvalidDataAccessApiUsageException(
                "NOPERM User ulticode-admin has no permissions to run the 'keys' command"));

        CacheManager cacheManager = new AdminCacheConfig().cacheManager(connectionFactory);

        assertThrows(IllegalStateException.class,
                () -> cacheManager.getCache("userStats").clear(),
                "clear must reach SCAN; KEYS would answer NOPERM here");
        assertEquals("userStats::*", scan.get().getPattern(),
                "the scan pattern must stay inside the cache's own key prefix, which the role's key patterns grant");
        verify(keyCommands, never()).keys(any(byte[].class));
    }
}
