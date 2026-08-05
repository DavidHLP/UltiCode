package com.ulticode.app.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configures the Redisson client to use a {@link JsonJacksonCodec} that
 * supports Java 8 date/time types (e.g. {@code LocalDateTime}).
 *
 * <p>The default Redisson {@code JsonJacksonCodec} does not register
 * {@link JavaTimeModule}, so objects containing {@code LocalDateTime}
 * fields (such as {@code JudgeJob.createdAt}) fail to deserialize during
 * {@code RQueue.poll()} — the failure is silently swallowed by Redisson
 * and {@code poll()} returns {@code null}, causing every job to be lost.
 *
 * <p>This customizer replaces the default codec with one that has
 * {@code JavaTimeModule} registered, matching the configuration already
 * applied to {@code appRedisTemplate} in {@link AppRedisTemplateConfig}.
 */
@Slf4j
@Configuration
@Profile("!test")
public class RedissonCodecConfig {

    @Bean
    public RedissonAutoConfigurationCustomizer redissonCodecCustomizer() {
        return config -> {
            log.warn("RedissonCodecConfig: applying JsonJacksonCodec with JavaTimeModule");
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
            objectMapper.activateDefaultTyping(
                    LaissezFaireSubTypeValidator.instance,
                    ObjectMapper.DefaultTyping.NON_FINAL);
            config.setCodec(new JsonJacksonCodec(objectMapper));
        };
    }
}
