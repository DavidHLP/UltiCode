package com.ulticode.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spring Cache configuration with two-tier backing:
 *   L1 — Caffeine in-process cache (short TTL, fast reads)
 *   L2 — Redis distributed cache (longer TTL, survives process restart)
 *
 * <p>Queries are checked against L1 first; on miss, L2 is consulted; on
 * a second miss the underlying source is invoked and the result is
 * written back to both tiers. The composite behaviour is implemented
 * via {@link CompositeCacheManager} which iterates delegates in order.
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
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        // L1: 10s TTL, max 10k entries, with hit-rate stats enabled.
        // Monitoring endpoints share this short TTL so health dashboards
        // see near-real-time data while absorbing traffic spikes.
        caffeineCacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(10))
                .maximumSize(10_000)
                .recordStats());

        RedisCacheManager redisCacheManager = buildRedisCacheManager();

        // Composite: read-through L1 → L2 → source. Spring's
        // CompositeCacheManager falls through to the next delegate
        // when a get returns null, so a Caffeine miss cascades to
        // Redis, and a Redis miss cascades to the @Cacheable method.
        return new CompositeCacheManager(caffeineCacheManager, redisCacheManager);
    }

    private RedisCacheManager buildRedisCacheManager() {
        long baseTtl = 300L;
        long jitterRange = 30L;
        long ttlSeconds = baseTtl + ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer)
                )
                .disableCachingNullValues();

        // Note: withInitialCacheConfigurations() is omitted because all
        // existing regions share defaultConfig. Per-region overrides
        // (e.g. a 24h TTL on "contestRanking") can be re-added here when
        // a region needs to diverge from the default.

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .build();
    }
}

