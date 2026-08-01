package com.ulticode.common.config;

import org.springframework.stereotype.Component;

/**
 * Single source of truth for which HTTP paths bypass authentication.
 *
 * <p><strong>Deep module</strong> &mdash; extracted from the fused
 * {@code SecurityConfig} (205 LOC), which previously held a 52-string
 * {@code PUBLIC_ENDPOINTS} array of business knowledge alongside transport
 * policy (CORS, CSRF, headers, filter wiring). Adding or auditing a public
 * path no longer requires reading a 200-line config file.
 *
 * <p>Three categories of public access:
 * <ul>
 *   <li>{@link #publicEndpoints()} &mdash; unauthenticated, all HTTP methods.</li>
 *   <li>{@link #publicGetEndpoints()} &mdash; GET-only public reads.</li>
 *   <li>{@link #publicSampleRunPattern()} &mdash; the single POST public path.</li>
 * </ul>
 *
 * @see SecurityConfig#securityFilterChain
 */
@Component
public class PublicEndpointRegistry {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/github",
            "/auth/github/callback",
            "/auth/google",
            "/auth/google/callback",
            "/contest/**",
            "/submissions/statuses",
            "/api/solutions",
            "/api/solutions/**",
            "/api/views/solution/**",
            "/forum/posts",
            "/forum/posts/**",
            "/forum/communities",
            "/forum/communities/**",
            "/forum/tags",
            "/forum/quick-filters",
            "/problem-lists/overview",
            "/problem-lists/*/overview",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/v3/api-docs/**",
            "/ws/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            // Prometheus: publicly readable by design for in-cluster scraper.
            // MUST be gated by network policy / nginx allow-deny / management port.
            "/actuator/prometheus",
            // App service-shell health endpoint (container readiness probe).
            "/api/v1/app/health"
    };

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/problems",
            "/problems/*",
            "/problems/slug/**",
            "/problems/*/adjacent",
            "/api/problems/*/solutions",
            "/edge-operations/**",
            // i18n translations and locales are needed before login (UI bootstrap).
            // The bulk-upsert POST endpoint is gated by @PreAuthorize independently.
            "/i18n/translations",
            "/i18n/locales"
    };

    private static final String PUBLIC_SAMPLE_RUN_PATTERN = "/problems/*/submissions/run";

    /**
     * @return unauthenticated endpoints (all methods permitted)
     */
    public String[] publicEndpoints() {
        return PUBLIC_ENDPOINTS.clone();
    }

    /**
     * @return GET-only public read paths
     */
    public String[] publicGetEndpoints() {
        return PUBLIC_GET_ENDPOINTS.clone();
    }

    /**
     * @return the sample-execution POST path pattern
     */
    public String publicSampleRunPattern() {
        return PUBLIC_SAMPLE_RUN_PATTERN;
    }
}
