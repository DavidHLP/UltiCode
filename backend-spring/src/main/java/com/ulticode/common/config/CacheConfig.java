package com.ulticode.common.config;

import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spring Cache configuration backed by Redis.
 * Registers cache regions for Problem, User, and Contest services with jittered TTL.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private final RedisConnectionFactory redisConnectionFactory;

    public CacheConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Bean
    public CacheManager cacheManager() {
        long baseTtl = 300L;
        long jitterRange = 30L;
        long ttlSeconds = baseTtl + ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new JdkSerializationRedisSerializer())
                )
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> configs = Map.of(
                "problem", defaultConfig,
                "userStats", defaultConfig,
                "contest", defaultConfig,
                "contestRanking", defaultConfig
        );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
