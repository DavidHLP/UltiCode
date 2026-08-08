package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import com.ulticode.auth.idempotency.mapper.AuthCommandReceiptMapper;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AccountAdministrationTransactionalIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_auth_tx_test")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
    }

    @Autowired
    private AccountAdministrationProvider provider;

    @Autowired
    private AuthCommandReceiptMapper receiptMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpDatabaseSchemaAndData() throws IOException {
        jdbcTemplate.execute("DROP TABLE IF EXISTS user_permissions");
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_command_receipt");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");

        // 1. Create real users table with authz_version
        jdbcTemplate.execute("""
            CREATE TABLE users (
                id VARCHAR(40) PRIMARY KEY,
                username VARCHAR(80) NOT NULL,
                email VARCHAR(120),
                password VARCHAR(120),
                role VARCHAR(30) NOT NULL,
                is_active TINYINT(1) NOT NULL DEFAULT 1,
                is_banned TINYINT(1) NOT NULL DEFAULT 0,
                banned_until DATETIME,
                joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                authz_version BIGINT NOT NULL DEFAULT 0,
                is_deleted TINYINT(1) NOT NULL DEFAULT 0
            )
        """);

        // 2. Create real user_permissions table
        jdbcTemplate.execute("""
            CREATE TABLE user_permissions (
                id VARCHAR(40) PRIMARY KEY,
                user_id VARCHAR(40) NOT NULL,
                action VARCHAR(50) NOT NULL,
                resource VARCHAR(50) NOT NULL,
                granted_by VARCHAR(40),
                granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                expires_at DATETIME
            )
        """);

        // 3. Create real auth_command_receipt table using migration DDL file
        Path migrationPath = Path.of("../../init-db/migrations/auth/V20260730120000__Create_Auth_Command_Receipt.sql");
        if (!Files.exists(migrationPath)) {
            migrationPath = Path.of("../init-db/migrations/auth/V20260730120000__Create_Auth_Command_Receipt.sql");
        }
        if (!Files.exists(migrationPath)) {
            migrationPath = Path.of("init-db/migrations/auth/V20260730120000__Create_Auth_Command_Receipt.sql");
        }
        String sql = Files.readString(migrationPath);
        jdbcTemplate.execute(sql);

        // 4. Seed user-10 into real database
        jdbcTemplate.update("""
            INSERT INTO users (id, username, email, password, role, is_active, is_banned, authz_version)
            VALUES ('user-10', 'bob', 'bob@example.com', 'secret', 'USER', 1, 0, 1)
        """);
    }

    @Test
    @DisplayName("real Testcontainers MySQL database transaction rolls back role and version when permission validation fails, and persists no receipt")
    void transactionRollbackOnPermissionValidationFailure() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "org-1", "reason");
        TraceMetadata trace = new TraceMetadata("t-123", "span-1", null, null);

        // Wildcard action "*:USER" triggers AuthBusinessException in real PermissionServiceImpl
        ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                "cmd-tx-1", IdMetadata.of("key-tx-1", null), actor, trace, "user-10", 1L,
                "ADMIN", Set.of("*:USER"), "invalid permission attempt"
        );

        // Execute provider operation
        var result = provider.changeAuthorization(command);

        // 1. Result should indicate failure
        assertThat(result.success()).isFalse();

        // 2. Query real database: user role must still be 'USER' and authz_version must still be 1
        Map<String, Object> userRow = jdbcTemplate.queryForMap("SELECT role, authz_version FROM users WHERE id = 'user-10'");
        assertThat(userRow.get("role")).isEqualTo("USER");
        assertThat(((Number) userRow.get("authz_version")).longValue()).isEqualTo(1L);

        // 3. Query real database: no command receipt stored for key-tx-1
        AuthCommandReceiptEntity receipt = receiptMapper.findByReceiptKey("AccountAdministrationService", "changeAuthorization", "key-tx-1");
        assertThat(receipt).isNull();
    }
}
