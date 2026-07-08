package com.ulticode.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PublicEndpointRegistry unit tests.
 *
 * <p>Pins the public-access whitelist that previously lived as a 52-string
 * private static array inside SecurityConfig.
 */
@DisplayName("PublicEndpointRegistry")
class PublicEndpointRegistryTest {

    private PublicEndpointRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PublicEndpointRegistry();
    }

    @Nested
    @DisplayName("publicEndpoints() (unauthenticated, all methods)")
    class PublicAllMethods {

        @Test
        @DisplayName("includes auth endpoints (login, register, refresh)")
        void includesAuth() {
            String[] endpoints = registry.publicEndpoints();
            assertTrue(contains(endpoints, "/auth/login"));
            assertTrue(contains(endpoints, "/auth/register"));
            assertTrue(contains(endpoints, "/auth/refresh"));
        }

        @Test
        @DisplayName("includes health and info actuator endpoints")
        void includesHealth() {
            String[] endpoints = registry.publicEndpoints();
            assertTrue(contains(endpoints, "/actuator/health"));
            assertTrue(contains(endpoints, "/actuator/health/**"));
            assertTrue(contains(endpoints, "/actuator/info"));
        }

        @Test
        @DisplayName("includes WebSocket endpoint")
        void includesWebSocket() {
            assertTrue(contains(registry.publicEndpoints(), "/ws/**"));
        }

        @Test
        @DisplayName("includes Swagger/OpenAPI documentation")
        void includesSwagger() {
            String[] endpoints = registry.publicEndpoints();
            assertTrue(contains(endpoints, "/swagger-ui/**"));
            assertTrue(contains(endpoints, "/v3/api-docs/**"));
        }

        @Test
        @DisplayName("does NOT include admin paths")
        void excludesAdmin() {
            assertFalse(contains(registry.publicEndpoints(), "/admin/**"));
        }

        @Test
        @DisplayName("prometheus is explicitly listed (not via wildcard)")
        void prometheusIsExplicit() {
            assertTrue(contains(registry.publicEndpoints(), "/actuator/prometheus"));
        }
    }

    @Nested
    @DisplayName("publicGetEndpoints() (GET-only public reads)")
    class PublicGetOnly {

        @Test
        @DisplayName("includes problem read paths")
        void includesProblemReads() {
            String[] paths = registry.publicGetEndpoints();
            assertTrue(contains(paths, "/problems"));
            assertTrue(contains(paths, "/problems/*"));
        }

        @Test
        @DisplayName("includes edge-operations reads")
        void includesEdgeOps() {
            assertTrue(contains(registry.publicGetEndpoints(), "/edge-operations/**"));
        }
    }

    @Test
    @DisplayName("publicSampleRunPattern() returns the sample-execution POST path")
    void sampleRunPattern() {
        assertEquals("/problems/*/submissions/run", registry.publicSampleRunPattern());
    }

    private static boolean contains(String[] arr, String value) {
        for (String s : arr) {
            if (s.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
