package com.ulticode.common.config;

import com.ulticode.security.AuthenticationEntryPointImpl;
import com.ulticode.security.csrf.CsrfService;
import com.ulticode.security.csrf.CsrfValidationFilter;
import com.ulticode.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security configuration.
 * Configures JWT-based stateless authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationEntryPointImpl authenticationEntryPoint;
    private final CsrfService csrfService;
    private final CorsProperties corsProperties;

    /**
     * Define public endpoints that don't require authentication.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            // Auth endpoints
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/github",
            "/auth/google",
            // Problem endpoints (public read access)
            "/problems",
            "/problems/**",
            // Contest endpoints (public read access)
            "/contest/**",
            // Submission status endpoints (public read access)
            "/submissions/statuses",
            // Solution endpoints (public read access)
            "/api/solutions",
            "/api/solutions/**",
            "/api/views/solution/**",
            // Forum endpoints (public read access)
            "/forum/posts",
            "/forum/posts/**",
            "/forum/communities",
            "/forum/communities/**",
            "/forum/tags",
            "/forum/quick-filters",
            // Swagger/OpenAPI documentation
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/v3/api-docs/**",
            // WebSocket endpoint
            "/ws/**",
            // Health check endpoint (for startup scripts and monitoring)
            "/actuator/health"
    };

    /**
     * Configure the security filter chain.
     *
     * @param http the HttpSecurity object
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enable CORS (must be before other security rules)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Disable CSRF (using JWT, not cookies for session)
                .csrf(AbstractHttpConfigurer::disable)

                // Set session management to stateless
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Configure exception handling
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                )

                // Configure authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )

                // Configure security headers
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .preload(true)
                        )
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; "
                                                + "script-src 'self'; "
                                                + "style-src 'self' 'unsafe-inline'; "
                                                + "img-src 'self' data: https:; "
                                                + "font-src 'self' https://fonts.gstatic.com; "
                                                + "connect-src 'self'"
                                )
                        )
                        .frameOptions(fo -> fo.deny())
                        .xssProtection(xss -> xss.headerValue(
                                XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK
                        ))
                        .contentTypeOptions(contentType -> {})
                        .permissionsPolicy(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=()"
                        ))
                )

                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Add CSRF validation filter after JWT authentication
                .addFilterAfter(new CsrfValidationFilter(csrfService), JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration source for Spring Security.
     * Delegates to CorsProperties for externalized origin configuration.
     *
     * @return the CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return corsProperties.toConfigurationSource();
    }

    /**
     * Password encoder bean using BCrypt.
     *
     * @return the BCryptPasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
