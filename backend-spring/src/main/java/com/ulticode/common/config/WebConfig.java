package com.ulticode.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Web configuration for CORS settings.
 * Allows frontend applications on ports 9002 and 9003 to access the backend.
 */
@Configuration
public class WebConfig {

    /**
     * Allowed frontend origins.
     * Includes localhost and 127.0.0.1 variants for ports 9002 and 9003.
     */
    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
            "http://localhost:9002",
            "http://localhost:9003",
            "http://127.0.0.1:9002",
            "http://127.0.0.1:9003"
    );

    /**
     * Configure CORS filter.
     *
     * @return the CORS filter
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow credentials (cookies, authorization headers)
        config.setAllowCredentials(true);

        // Allowed origins
        config.setAllowedOriginPatterns(ALLOWED_ORIGINS);

        // Allowed HTTP methods
        config.setAllowedMethods(Arrays.asList(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "PATCH",
                "OPTIONS"
        ));

        // Allowed headers (all)
        config.setAllowedHeaders(List.of("*"));

        // Exposed headers
        config.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Set-Cookie",
                "Content-Disposition"
        ));

        // Max age for preflight cache (1 hour)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
