package com.ulticode.modules.recommendation.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for RestTemplate used by the recommendation service.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Create a RestTemplate bean for making HTTP requests to the recommendation service.
     *
     * @param builder               the RestTemplateBuilder
     * @param recommendationConfig  the recommendation configuration
     * @return configured RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, RecommendationConfig recommendationConfig) {
        return builder
                .connectTimeout(Duration.ofMillis(recommendationConfig.getTimeout()))
                .readTimeout(Duration.ofMillis(recommendationConfig.getTimeout()))
                .build();
    }
}
