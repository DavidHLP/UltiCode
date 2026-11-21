package com.ulticode.modules.queue.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.queue.port.JudgeJobEnqueuer;
import com.ulticode.modules.queue.port.adapter.RedissonStreamsJudgeJobEnqueuer;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires Submission's judge outbox to the Redis stream written for Judge. */
@Configuration
@org.springframework.context.annotation.Profile("!test")
public class QueueConfig {

    @Bean
    @ConditionalOnProperty(
            name = "app.features.judge-queue.use-port",
            havingValue = "true")
    public JudgeJobEnqueuer redissonStreamsJudgeJobEnqueuer(
            RedissonClient redissonClient,
            ObjectMapper objectMapper) {
        return new RedissonStreamsJudgeJobEnqueuer(redissonClient, objectMapper);
    }
}
