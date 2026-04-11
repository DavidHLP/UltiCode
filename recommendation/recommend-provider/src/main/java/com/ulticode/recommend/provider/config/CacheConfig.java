package com.ulticode.recommend.provider.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for the recommendation service.
 *
 * <p>Configures Caffeine-based caching with:
 * <ul>
 *   <li>Maximum size of 1000 entries per cache</li>
 *   <li>Time-to-live of 5 minutes after write</li>
 *   <li>Statistics recording for monitoring</li>
 * </ul>
 *
 * <p>Cache configuration targets response time under 200ms by:
 * <ul>
 *   <li>Caching recommendation results for repeated requests</li>
 *   <li>Using high-performance Caffeine cache implementation</li>
 *   <li>Automatic eviction to prevent memory bloat</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Maximum number of entries per cache.
     */
    private static final int MAX_CACHE_SIZE = 1000;

    /**
     * Time-to-live for cache entries in minutes.
     */
    private static final int CACHE_TTL_MINUTES = 5;

    /**
     * Creates and configures the Caffeine cache manager.
     *
     * <p>Cache settings:
     * <ul>
     *   <li>maximumSize: Limits cache to prevent memory issues</li>
     *   <li>expireAfterWrite: TTL-based eviction for data freshness</li>
     *   <li>recordStats: Enables cache statistics for monitoring</li>
     * </ul>
     *
     * @return configured Caffeine cache manager
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }

    /**
     * Creates the Caffeine object with default configuration.
     *
     * <p>This bean allows for customization and testing of cache behavior.
     *
     * @return configured Caffeine instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
