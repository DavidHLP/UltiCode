package com.ulticode.modules.queue.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.queue.redis.JudgeStreamKeys;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.port.adapter.RedissonStreamsJudgeQueueAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the queue system.
 * Configures Redisson queues and queue properties.
 */
@Configuration
@org.springframework.context.annotation.Profile("!test")
@ConfigurationProperties(prefix = "queue")
@Data
public class QueueConfig {

    /**
     * Enable job status tracking in Redis.
     */
    private boolean enableStatusTracking = true;

    /**
     * TTL for completed job status in seconds (default: 24 hours).
     */
    private long jobStatusTtlSeconds = 86400;

    /**
     * Maximum concurrent jobs per queue.
     */
    private int maxConcurrentJobs = 10;

    /**
     * Poll interval for job processing in milliseconds.
     */
    private long pollIntervalMs = 1000;

    /**
     * Enable judge worker processing.
     */
    private boolean judgeEnabled = true;

    /**
     * Create the judge queue bean.
     *
     * @param redissonClient the Redisson client
     * @return the judge queue
     */
    @Bean(name = "judgeQueue")
    public RQueue<Object> judgeQueue(RedissonClient redissonClient) {
        return redissonClient.getQueue(QueueConstants.JUDGE_QUEUE);
    }

    /**
     * Create the email queue bean.
     *
     * @param redissonClient the Redisson client
     * @return the email queue
     */
    @Bean(name = "emailQueue")
    public RQueue<Object> emailQueue(RedissonClient redissonClient) {
        return redissonClient.getQueue(QueueConstants.EMAIL_QUEUE);
    }

    /**
     * Create the notification queue bean.
     *
     * @param redissonClient the Redisson client
     * @return the notification queue
     */
    @Bean(name = "notificationQueue")
    public RQueue<Object> notificationQueue(RedissonClient redissonClient) {
        return redissonClient.getQueue(QueueConstants.NOTIFICATION_QUEUE);
    }

    /**
     * ADR-003 M3c-2: the {@link JudgeQueue} port backed by Redisson Streams.
     * Dedup SETNX is delegated to Redisson {@code RBucket} so no separate
     * Redis wrapper dependency is needed here.
     *
     * <p>Only active when {@code app.features.judge-queue.use-port=true}.
     */
    @Bean
    @ConditionalOnProperty(
            name = "app.features.judge-queue.use-port",
            havingValue = "true")
    public JudgeQueue redissonStreamsJudgeQueue(
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        String consumerId = "ulticode-9001-" + ProcessHandle.current().pid();
        return new RedissonStreamsJudgeQueueAdapter(
                redissonClient,
                objectMapper,
                JudgeStreamKeys.JUDGE_STREAM_KEY,
                JudgeStreamKeys.JUDGE_STREAM_GROUP,
                consumerId,
                JudgeStreamKeys.JUDGE_STREAM_VISIBILITY_TIMEOUT_MS,
                meterRegistry);
    }
}
