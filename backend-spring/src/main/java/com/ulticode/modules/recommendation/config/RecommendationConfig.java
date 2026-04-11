package com.ulticode.modules.recommendation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the recommendation module.
 *
 * <p>Dubbo RPC is used for service communication (configured via spring.dubbo.*).
 * This class only manages the feature toggle.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "recommendation")
public class RecommendationConfig {

    /**
     * Whether the recommendation feature is enabled.
     */
    private boolean enabled = false;

    /**
     * Cron expression for syncing MySQL data to Redis.
     * Default: daily at 4:00 AM (low-traffic period).
     */
    private String syncCron = "0 0 4 * * ?";

    /**
     * Cron expression for pre-generating daily recommendations.
     * Default: daily at 6:00 AM (after Redis sync).
     */
    private String generateCron = "0 0 6 * * ?";

    /**
     * Number of users to process per batch in daily recommendation generation.
     */
    private int generateBatchSize = 50;

    /**
     * TTL in days for pre-generated recommendations.
     */
    private int recommendationTtlDays = 1;
}
