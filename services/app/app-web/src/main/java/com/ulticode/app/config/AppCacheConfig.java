package com.ulticode.app.config;

import com.ulticode.redis.RedisCacheWritePolicy;
import com.ulticode.redis.RedisValueSerializationPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Spring Cache wiring owned by the app service (P7-RELOCATE).
 *
 * <p>Replaces the deleted legacy {@code com.ulticode.common.config.CacheConfig}
 * in the app context: {@code ContestRankingCacheEvictor} injects a
 * {@link CacheManager}, and the {@code @Cacheable} projections
 * ({@code userStats}, {@code contestRanking}, monitoring) need
 * {@code @EnableCaching} to take effect.
 *
 * <p>The legacy two-tier Caffeine+Redis composite is reduced to the Redis
 * tier: Caffeine is not on this module's classpath. The 300s default TTL
 * matches the legacy L2 base TTL (the legacy per-start jitter is dropped
 * for simplicity). Value serialization mirrors the legacy Jackson
 * default-typing setup so cached DTOs round-trip to their concrete types.
 */
@Configuration
@Profile("!test")
@EnableCaching
public class AppCacheConfig {
    @Bean
    public CacheManager cacheManager(
            @Qualifier("redisCacheConnectionFactory") RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer serializer =
                RedisValueSerializationPolicy.valueSerializer();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(300))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        // SCAN-based eviction: the app role's ACL grants +scan but not +keys.
        return RedisCacheManager.builder(RedisCacheWritePolicy.scanningWriter(connectionFactory))
                .cacheDefaults(config)
                .build();
    }
}
