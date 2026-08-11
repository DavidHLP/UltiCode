package com.ulticode.app.security;

import com.ulticode.app.security.jwt.JwksPublicKeyProvider;
import com.ulticode.app.security.jwt.ResourceServerJwtVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security configuration for the backend-app service shell.
 *
 * <p>In production, the backend-app module runs inside the legacy monolith context where
 * {@code com.ulticode.common.config.SecurityConfig} (JWT + method security + full authorization
 * rules) protects all routes. That config lives in backend-legacy, which is NOT on the
 * backend-app test classpath. Without this test config, Spring Boot's default auto-configured
 * security kicks in (form login), blocking the health endpoint that
 * {@link com.ulticode.BackendAppApplicationTest} asserts on.
 *
 * <p>This config mirrors the production public-surface: the app health endpoint is public
 * (it is registered in {@code PublicEndpointRegistry} in production). The test does not exercise
 * authenticated routes, so permitAll is sufficient for the context-load smoke test.
 */
@TestConfiguration
public class AppTestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
    @Bean
    @ConditionalOnMissingBean
    JwksPublicKeyProvider testJwksPublicKeyProvider() {
        return new JwksPublicKeyProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    ResourceServerJwtVerifier testResourceServerJwtVerifier(
            JwksPublicKeyProvider jwksPublicKeyProvider) {
        return new ResourceServerJwtVerifier(jwksPublicKeyProvider);
    }
}
