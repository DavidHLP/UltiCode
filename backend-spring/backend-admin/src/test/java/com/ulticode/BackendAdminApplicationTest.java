package com.ulticode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * P1-INFRA-005: verify the admin service shell boots and exposes health.
 *
 * <p>Disabled until the admin shell has its own isolated test context
 * (Testcontainers-based IT). With the P7-RELOCATE-ADMIN-001 dependency on
 * backend-legacy the full component scan requires Redis/MySQL infrastructure
 * that is not available in a plain unit test.
 */
@Disabled("Requires Redis/MySQL infrastructure — convert to Testcontainers IT (P7-RELOCATE-ADMIN-001)")
@SpringBootTest(
        classes = UlticodeBackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                        + "org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration,"
                        + "com.alibaba.cloud.dubbo.bootstrap.DubboBootstrapAutoConfiguration",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "spring.datasource.url=jdbc:mysql://localhost:1/unused?connectTimeout=100",
                "spring.datasource.username=unused",
                "spring.datasource.password=",
                "jwt.secret=VGVzdFNlY3JldEZvckJhY2tlbmRBZG1pbkFwcGxpY2F0aW9uVGVzdE9ubHlBdExlYXN0NjQ=",
                "management.health.db.enabled=false"
        })
class BackendAdminApplicationTest {

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
    @DisplayName("placeholder /api/v1/admin/health returns success")
    void placeholderReturnsOk() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/api/v1/admin/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("backend-admin shell up");
    }
}
