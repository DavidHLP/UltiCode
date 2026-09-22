package com.ulticode.admin.config;

import com.ulticode.redis.RedisCacheWritePolicy;
import com.ulticode.redis.RedisValueSerializationPolicy;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Spring Cache wiring owned by the admin shell (P7-RELOCATE).
 *
 * <p>Replaces the deleted legacy {@code com.ulticode.common.config.CacheConfig}
 * in the admin context: beans scanned in from backend-app (e.g.
 * {@code ContestRankingCacheEvictor}) inject a {@link CacheManager}, and the
 * admin-side {@code @CacheEvict("userStats")} on the profile write path keeps
 * its legacy semantics only when caching is enabled here. Mirrors
 * {@code AppCacheConfig} — same Redis store, so an admin-side eviction also
 * clears the entries the app context serves.
 */
@Configuration
@EnableCaching
public class AdminCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer serializer =
                RedisValueSerializationPolicy.valueSerializer();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(300))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        // SCAN-based eviction: the admin role's ACL grants +scan but not +keys.
        return RedisCacheManager.builder(RedisCacheWritePolicy.scanningWriter(connectionFactory))
                .cacheDefaults(config)
                .build();
    }
}
