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
 * Test-only health policy for the backend-app boot smoke.
 *
 * <p>The production chain lives in {@link AppSecurityConfig}. Controller slices
 * disable filters explicitly; this fallback exposes only the two health probes
 * used by {@code BackendAppApplicationTest} and denies every other route.
 */
@TestConfiguration
public class AppTestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/v1/app/health",
                        "/api/v1/app/health/ready").permitAll()
                .anyRequest().denyAll());
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
