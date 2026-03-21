package com.ulticode.modules.recommendation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the recommendation service.
 * Connects to an external recommendation microservice.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "recommendation")
public class RecommendationConfig {

    /**
     * Whether the recommendation service is enabled
     */
    private boolean enabled = false;

    /**
     * URL of the recommendation microservice
     */
    private String serviceUrl;

    /**
     * Request timeout in milliseconds
     */
    private int timeout = 5000;

    /**
     * Whether Nacos service discovery is enabled
     */
    private boolean nacosEnabled = false;

    /**
     * Fallback URL if primary service is unavailable
     */
    private String fallbackUrl;
}
