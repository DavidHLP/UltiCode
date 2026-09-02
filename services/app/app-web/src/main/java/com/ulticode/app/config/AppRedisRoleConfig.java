package com.ulticode.app.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * Names the App Redis workload seams without changing the current topology.
 *
 * <p>Aliases are registered after auto-configuration bean definitions exist.
 * Defining aliases as additional {@code RedisConnectionFactory} beans would
 * make Redis auto-configuration back off before creating the Boot factory.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class AppRedisRoleConfig {

    private static final List<String> REDIS_ROLE_ALIASES = List.of(
            "redisStreamsConnectionFactory",
            "redisCacheConnectionFactory",
            "redisRateLimitConnectionFactory",
            "redisReplayConnectionFactory",
            "redisQueueConnectionFactory",
            "redisJudgeConnectionFactory",
            "redisPubsubConnectionFactory");

    @Bean
    public static BeanFactoryPostProcessor redisWorkloadRoleAliases() {
        return beanFactory -> {
            String primaryCandidate = null;
            if (beanFactory.containsBeanDefinition("redisConnectionFactory")) {
                primaryCandidate = "redisConnectionFactory";
            } else if (beanFactory.containsBeanDefinition("redissonConnectionFactory")) {
                primaryCandidate = "redissonConnectionFactory";
            } else {
                // Fallback: any RedisConnectionFactory bean will be the primary;
                // try the standard name first, otherwise the Redisson name.
                primaryCandidate = "redisConnectionFactory";
            }
            final String primary = primaryCandidate;
            REDIS_ROLE_ALIASES.forEach(alias -> {
                if (!alias.equals(primary) && !beanFactory.containsBeanDefinition(alias)) {
                    beanFactory.registerAlias(primary, alias);
                }
            });
            // Ensure the canonical Lettuce name also resolves when Redisson is primary.
            if (primary.equals("redissonConnectionFactory")
                    && !beanFactory.containsBeanDefinition("redisConnectionFactory")) {
                beanFactory.registerAlias(primary, "redisConnectionFactory");
            }
        };
    }
}
