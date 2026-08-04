package com.ulticode.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisTemplate wiring owned by the app service.
 *
 * <p>Redisson supplies the default Redis beans, but its generic template does
 * not satisfy app consumers that require {@code RedisTemplate<String, Object>}
 * for job-status and monitoring data. Keep this app-owned template explicit so
 * those consumers do not depend on generic-type inference from another owner.
 */
@Configuration
@Profile("!test")
public class AppRedisTemplateConfig {

    @Bean(name = "appRedisTemplate")
    public RedisTemplate<String, Object> appRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
