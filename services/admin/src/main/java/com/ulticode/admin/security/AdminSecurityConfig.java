package com.ulticode.admin.security;

import com.ulticode.admin.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for backend-admin resource server shell.
 *
 * <p>P7-RELOCATE: also owns the {@link PasswordEncoder} bean — the legacy
 * {@code SecurityConfig} that used to supply it was deleted by
 * P7-LEGACY-DEAD-INFRA-DELETE-001, and the relocated
 * {@code UserProvisioningAdapter} injects it.
 *
 * <p>Registers {@link JwtAuthenticationFilter} so the admin shell can
 * authenticate requests from the access_token cookie (issued by
 * backend-auth) and populate the SecurityContext needed by
 * {@code @PreAuthorize} method security.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AdminSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/v1/admin/health",
                        "/api/v1/admin/health/ready").permitAll()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
