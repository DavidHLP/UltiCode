package com.ulticode.app.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the backend-app shell (P7-RELOCATE).
 *
 * <p>Mirrors {@code AdminSecurityConfig}: the shell excludes Boot's
 * {@code SecurityAutoConfiguration}, so without an owned
 * {@code @EnableWebSecurity} there is no {@link HttpSecurity} bean and the
 * actuator {@code ManagementWebSecurityAutoConfiguration} fails at startup.
 * Lives in the app-owned security package which the admin shell excludes,
 * so each context keeps exactly one security configuration.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AppSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/v1/health").permitAll()
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
