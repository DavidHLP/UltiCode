package com.ulticode.auth.idempotency;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class AuthCommandReceiptSchemaIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_auth_test")
            .withUsername("root")
            .withPassword("root");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void setUp() throws Exception {
        mysql.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        dataSource.setDriverClassName(mysql.getDriverClassName());
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void tearDown() {
        if (mysql != null && mysql.isRunning()) {
            mysql.stop();
        }
    }

    @BeforeEach
    void setUpSchema() throws IOException {
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_command_receipt");

        // Read exact canonical Flyway migration artifact file
        Path migrationPath = Path.of("../../init-db/migrations/auth/V20260730120000__Create_Auth_Command_Receipt.sql");
        if (!Files.exists(migrationPath)) {
            migrationPath = Path.of("../init-db/migrations/auth/V20260730120000__Create_Auth_Command_Receipt.sql");
        }
        if (!Files.exists(migrationPath)) {
            migrationPath = Path.of("init-db/migrations/auth/V20260730120000__Create_Auth_Command_Receipt.sql");
        }
        String sql = Files.readString(migrationPath);
        jdbcTemplate.execute(sql);
    }

    @Test
    @DisplayName("insert command receipt succeeds against real MySQL 8.0 with JSON and TIMESTAMP(3)")
    void insertReceiptSucceeds() {
        int rows = jdbcTemplate.update("""
            INSERT INTO auth_command_receipt (id, command_id, service, operation, idempotency_key, status, actor_type, actor_id, result_payload)
            VALUES ('rcpt-1', 'cmd-100', 'AccountAdministrationService', 'changeState', 'key-abc', 'SUCCESS', 'ADMIN', 'admin-1', '{"state":"ACTIVE"}')
        """);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("insert command receipt throws DuplicateKeyException on duplicate (service, operation, idempotency_key)")
    void insertDuplicateReceiptFails() {
        jdbcTemplate.update("""
            INSERT INTO auth_command_receipt (id, command_id, service, operation, idempotency_key, status, actor_type, actor_id)
            VALUES ('rcpt-1', 'cmd-100', 'AccountAdministrationService', 'changeState', 'key-abc', 'SUCCESS', 'ADMIN', 'admin-1')
        """);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO auth_command_receipt (id, command_id, service, operation, idempotency_key, status, actor_type, actor_id)
            VALUES ('rcpt-2', 'cmd-101', 'AccountAdministrationService', 'changeState', 'key-abc', 'SUCCESS', 'ADMIN', 'admin-1')
        """)).isInstanceOf(DuplicateKeyException.class);
    }
}
