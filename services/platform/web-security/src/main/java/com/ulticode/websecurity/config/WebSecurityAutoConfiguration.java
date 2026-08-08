package com.ulticode.websecurity.config;

import com.ulticode.websecurity.aspect.RateLimitAspect;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.websecurity.ratelimiter.RateLimiter;
import com.ulticode.websecurity.ratelimiter.RedisRateLimiter;
import com.ulticode.websecurity.util.ClientIpResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configuration for canonical rate-limit Web infrastructure.
 *
 * <p>Service shells remain responsible for their {@link CurrentUserProvider}
 * adapter and {@code SecurityFilterChain}.
 */
@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class WebSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ClientIpResolver clientIpResolver() {
        return new ClientIpResolver();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter rateLimiter(StringRedisTemplate redisTemplate) {
        return new RedisRateLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnBean({StringRedisTemplate.class, CurrentUserProvider.class})
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(
            RateLimiter rateLimiter,
            ClientIpResolver clientIpResolver,
            CurrentUserProvider currentUserProvider) {
        return new RateLimitAspect(rateLimiter, clientIpResolver, currentUserProvider);
    }
}
