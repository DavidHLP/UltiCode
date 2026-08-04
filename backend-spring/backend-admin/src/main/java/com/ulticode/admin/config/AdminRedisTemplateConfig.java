package com.ulticode.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisTemplate wiring owned by the admin shell (P7-RELOCATE).
 *
 * <p>Mirrors {@code AppRedisTemplateConfig}: the queue-domain beans scanned
 * in from backend-app (e.g. {@code QueueServiceImpl#jobStatusRedisTemplate},
 * the monitoring inspector) require a {@code RedisTemplate<String, Object>},
 * which the Boot-default {@code RedisTemplate<Object, Object>} does not
 * satisfy.
 */
@Configuration
public class AdminRedisTemplateConfig {

    @Bean(name = "adminRedisTemplate")
    public RedisTemplate<String, Object> adminRedisTemplate(RedisConnectionFactory connectionFactory) {
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
