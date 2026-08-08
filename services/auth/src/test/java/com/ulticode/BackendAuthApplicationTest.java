package com.ulticode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * P1-INFRA-005: verify the populated auth service boots and exposes health.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendAuthApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private com.ulticode.common.audit.AuditSinkPort auditSinkPort;

    @Test
    @DisplayName("context loads and /actuator/health is UP")
    void healthEndpointReturnsUp() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("service health endpoint /api/v1/auth/health returns success")
    void serviceHealthEndpointReturnsOk() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/api/v1/auth/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("backend-auth shell up");
    }

    @Test
    @DisplayName("AuditSinkPort resolves to the Auth-local outbox adapter (P7-AUDIT-SINK-OWNER-BINDING-001)")
    void auditSinkPortResolvesToAuthAdapter() {
        assertThat(auditSinkPort)
                .isInstanceOf(com.ulticode.auth.audit.AuthAuditSinkAdapter.class);
    }
}
