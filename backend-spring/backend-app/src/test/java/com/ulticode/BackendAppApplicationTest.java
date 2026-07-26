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
 * P1-INFRA-005: verify the app service shell boots and exposes health.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendAppApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("context loads and /actuator/health is UP")
    void healthEndpointReturnsUp() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("placeholder /api/v1/app/health returns success")
    void placeholderReturnsOk() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/api/v1/app/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("backend-app shell up");
    }
}
