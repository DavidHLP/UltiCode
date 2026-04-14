package com.ulticode.common.config;

import org.springframework.context.annotation.Configuration;

/**
 * Web MVC configuration.
 * CORS is handled centrally in SecurityConfig.java.
 * CSRF validation is handled by CsrfValidationFilter in the Spring Security filter chain.
 */
@Configuration
public class WebMvcConfig {
}
