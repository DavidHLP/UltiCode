package com.ulticode.app.security;

import com.ulticode.app.security.jwt.JwtAuthenticationFilter;
import com.ulticode.websecurity.csrf.CookieCsrfFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the backend-app shell (P7-RELOCATE).
 *
 * <p>Registers {@link JwtAuthenticationFilter} so the app shell can
 * authenticate requests from the access_token cookie (issued by
 * backend-auth) and populate the SecurityContext needed by
 * {@code @PreAuthorize} method security.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AppSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/v1/health",
                        "/api/v1/app/health", "/api/v1/app/health/ready").permitAll()
                .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/moderation/reports").authenticated()
                .requestMatchers("/moderation/**").hasAnyRole("MODERATOR", "ADMIN", "SUPER_ADMIN")
                .requestMatchers("/monitoring/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.GET,
                        "/achievements/my", "/achievements/points", "/achievements/user/me/**",
                        "/contest/user/**", "/contest/*/participation", "/contest/*/virtual/session",
                        "/contest/*/problems/*/submissions", "/forum/me/**",
                        "/problem-lists/problems/*/user-lists", "/problems/*/note",
                        "/problems/*/submissions/**", "/submissions/**", "/users/me/**",
                        "/users/*/follow/status", "/vote/**", "/subscriptions/**",
                        "/bookmarks/**", "/edge-operations/**").authenticated()
                .requestMatchers(HttpMethod.GET,
                        "/achievements", "/achievements/*", "/achievements/user/*",
                        "/contest/**", "/forum/**", "/problem-lists/overview",
                        "/problem-lists/*/overview", "/problems", "/problems/random",
                        "/problems/slug/*", "/problems/*", "/problems/*/adjacent",
                        "/search", "/solution-topics/**", "/api/problems/*/solutions",
                        "/api/solutions/**", "/users", "/users/**").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/problems/*/submissions/run", "/api/views/solution/*").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new CookieCsrfFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }
}
