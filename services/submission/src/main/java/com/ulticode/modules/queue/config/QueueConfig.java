package com.ulticode.modules.queue.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.queue.port.adapter.RedissonStreamsJudgeQueueAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Configuration for the judge queue port (SPLIT-003 slice-3).
 *
 * <p>Registers only the {@link RedissonStreamsJudgeQueueAdapter} — the M3c-2
 * {@code JudgeQueue} bean. backend-submission has no legacy RQueue producer
 * (DEC-014), so the legacy judge/email/notification RQueue beans from the
 * judge-runtime {@code QueueConfig} are intentionally not created here.
 */
@Configuration
@org.springframework.context.annotation.Profile("!test")
@ConfigurationProperties(prefix = "queue")
public class QueueConfig {

    /** Stable prefix used to identify a Streams consumer instance by runtime role. */
    private String consumerIdPrefix = "ulticode-submission";

    /** Maximum judge processing attempts before a stale Streams entry is dead-lettered. */
    private int maxDeliveryAttempts = 5;

    public String getConsumerIdPrefix() {
        return consumerIdPrefix;
    }

    public void setConsumerIdPrefix(String consumerIdPrefix) {
        this.consumerIdPrefix = consumerIdPrefix;
    }

    public int getMaxDeliveryAttempts() {
        return maxDeliveryAttempts;
    }

    public void setMaxDeliveryAttempts(int maxDeliveryAttempts) {
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    /**
     * ADR-003 M3c-2: the {@link com.ulticode.modules.queue.port.JudgeQueue}
     * port backed by Redisson Streams. Only active when
     * {@code app.features.judge-queue.use-port=true}.
     */
    @Bean
    @ConditionalOnProperty(
            name = "app.features.judge-queue.use-port",
            havingValue = "true")
    public RedissonStreamsJudgeQueueAdapter redissonStreamsJudgeQueue(
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        String consumerId = consumerIdPrefix + "-" + UUID.randomUUID();
        return new RedissonStreamsJudgeQueueAdapter(
                redissonClient,
                objectMapper,
                com.ulticode.modules.queue.redis.JudgeStreamKeys.JUDGE_STREAM_KEY,
                com.ulticode.modules.queue.redis.JudgeStreamKeys.JUDGE_STREAM_GROUP,
                consumerId,
                com.ulticode.modules.queue.redis.JudgeStreamKeys.JUDGE_STREAM_VISIBILITY_TIMEOUT_MS,
                maxDeliveryAttempts,
                meterRegistry);
    }
}
