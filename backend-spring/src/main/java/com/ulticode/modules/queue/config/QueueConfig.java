package com.ulticode.modules.queue.config;

import com.ulticode.modules.queue.constants.QueueConstants;
import lombok.Data;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the queue system.
 * Configures Redisson queues and queue properties.
 */
@Configuration
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
}
