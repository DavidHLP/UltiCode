package com.ulticode.auth.security;

import com.ulticode.auth.security.csrf.CsrfService;
import com.ulticode.auth.security.csrf.CsrfValidationFilter;
import com.ulticode.auth.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the auth service.
 *
 * <p>Scope is intentionally narrower than backend-legacy's
 * {@code com.ulticode.common.config.SecurityConfig}: backend-auth only
 * owns the authentication surface, so cross-service rules
 * ({@code /admin/**}, public endpoint registry, public sample run) are
 * not relevant here. The Strangler Fig contract keeps backend-legacy's
 * SecurityConfig unchanged until Phase 4 cutover.
 *
 * <p>CSRF validation is wired only when a {@link CsrfService} bean is
 * available. The service depends on {@code RedisTemplate}, which is
 * excluded from the unit-test slice; the filter is therefore absent in
 * tests and present at runtime.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class AuthSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthAuthenticationEntryPoint authAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain authSecurityFilterChain(
            HttpSecurity http, ObjectProvider<CsrfService> csrfServiceProvider) throws Exception {
        CsrfService csrfService = csrfServiceProvider.getIfAvailable();

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/health",
                                "/auth/login",
                                "/auth/register",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/oauth/**",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/auth/csrf",
                                "/auth/jwks",
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        if (csrfService != null) {
            http.addFilterAfter(new CsrfValidationFilter(csrfService), JwtAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
