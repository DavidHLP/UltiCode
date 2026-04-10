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
}
