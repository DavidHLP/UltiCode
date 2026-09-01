package com.ulticode.app.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Names the App Redis workload seams without changing the current topology.
 *
 * <p>All aliases intentionally delegate to the Boot connection factory today.
 * A future resource split changes this configuration, not business callers.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class AppRedisRoleConfig {

    @Bean(name = {
            "redisStreamsConnectionFactory",
            "redisCacheConnectionFactory",
            "redisRateLimitConnectionFactory",
            "redisReplayConnectionFactory",
            "redisQueueConnectionFactory",
            "redisJudgeConnectionFactory",
            "redisPubsubConnectionFactory"
    })
    @Primary
    public RedisConnectionFactory roleConnectionFactory(
            @Qualifier("redisConnectionFactory") RedisConnectionFactory delegate) {
        return delegate;
    }
}
