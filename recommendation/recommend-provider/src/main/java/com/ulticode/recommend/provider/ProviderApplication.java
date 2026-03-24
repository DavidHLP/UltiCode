package com.ulticode.recommend.provider;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the recommendation service provider.
 *
 * <p>This application exposes Dubbo3 services for the recommendation system.
 *
 * <p>Features:
 * <ul>
 *   <li>Dubbo3 RPC service exposure</li>
 *   <li>Caffeine-based caching for sub-200ms response times</li>
 *   <li>Spring Boot auto-configuration</li>
 * </ul>
 *
 * @see com.ulticode.recommend.provider.config.CacheConfig
 */
@SpringBootApplication
@EnableDubbo
public class ProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProviderApplication.class, args);
    }
}
