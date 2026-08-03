package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import com.ulticode.auth.idempotency.mapper.AuthCommandReceiptMapper;
import com.ulticode.common.rpc.RpcResult;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AccountManagementTransactionalIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_auth_mgmt_tx_test")
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
    private AccountManagementProvider provider;

    @Autowired
    private AuthCommandReceiptMapper receiptMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpDatabaseSchemaAndData() throws IOException {
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_command_receipt");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");

        jdbcTemplate.execute("""
            CREATE TABLE users (
                id VARCHAR(40) PRIMARY KEY,
                username VARCHAR(120) NOT NULL,
                email VARCHAR(255),
                password VARCHAR(255),
                role VARCHAR(30) NOT NULL DEFAULT 'USER',
                is_active TINYINT(1) NOT NULL DEFAULT 1,
                is_banned TINYINT(1) NOT NULL DEFAULT 0,
                banned_until DATETIME(3),
                joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                authz_version BIGINT NOT NULL DEFAULT 0,
                password_reset_token_hash VARCHAR(255),
                password_reset_expires_at DATETIME(3),
                updated_by VARCHAR(40),
                is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                deleted_at DATETIME(3),
                deleted_by VARCHAR(40),
                UNIQUE KEY uk_users_username (username),
                UNIQUE KEY uk_users_email (email)
            )
        """);

        Path migrationPath = Path.of("../../init-db/migrations/V20260730120000__Create_Auth_Command_Receipt.sql");
        if (!Files.exists(migrationPath)) {
            migrationPath = Path.of("../init-db/migrations/V20260730120000__Create_Auth_Command_Receipt.sql");
        }
        if (!Files.exists(migrationPath)) {
            migrationPath = Path.of("init-db/migrations/V20260730120000__Create_Auth_Command_Receipt.sql");
        }
        String sql = Files.readString(migrationPath);
        jdbcTemplate.execute(sql);

        jdbcTemplate.update("""
            INSERT INTO users (id, username, email, password, role, is_active, is_banned, authz_version, is_deleted)
            VALUES ('user-200', 'charlie', 'charlie@example.com', '$2a$10$xyz', 'USER', 1, 0, 1, 0)
        """);
    }

    @Test
    @DisplayName("atomic createAccount creates user and records receipt in same transaction")
    void createAccountTransactionalSuccess() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "test create");
        TraceMetadata trace = new TraceMetadata("t-tx-1", null, null, null);

        CreateAccountCommand command = new CreateAccountCommand(
                "cmd-tx-create-1", IdMetadata.of("key-tx-create-1", null), actor, trace,
                "dave", "dave@example.com", "secret123", "USER");

        RpcResult<AccountMutationDTO> result = provider.createAccount(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().username()).isEqualTo("dave");

        // Verify users row inserted in DB
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'dave'", Integer.class);
        assertThat(userCount).isEqualTo(1);

        // Verify receipt row inserted in DB
        AuthCommandReceiptEntity receipt = receiptMapper.findByReceiptKey(
                "AccountManagementService", "createAccount", "key-tx-create-1");
        assertThat(receipt).isNotNull();
        assertThat(receipt.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("idempotency key conflict returns error when key is reused with different request payload")
    void idempotencyConflictDifferentPayload() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "test create");
        TraceMetadata trace = new TraceMetadata("t-tx-1", null, null, null);

        CreateAccountCommand command1 = new CreateAccountCommand(
                "cmd-tx-create-1", IdMetadata.of("key-tx-shared", null), actor, trace,
                "eve", "eve@example.com", "secret123", "USER");

        RpcResult<AccountMutationDTO> firstResult = provider.createAccount(command1);
        assertThat(firstResult.success()).isTrue();

        CreateAccountCommand command2 = new CreateAccountCommand(
                "cmd-tx-create-2", IdMetadata.of("key-tx-shared", null), actor, trace,
                "eve_modified", "eve@example.com", "secret123", "USER");

        RpcResult<AccountMutationDTO> secondResult = provider.createAccount(command2);
        assertThat(secondResult.success()).isFalse();
        assertThat(secondResult.error().code()).isEqualTo(AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT.code());
    }

    @Test
    @DisplayName("deleteAccount soft-deletes user and stamps deleted_by")
    void deleteAccountSoftDelete() {
        ActorDelegation actor = new ActorDelegation("ADMIN", "admin-100", "admin-100", "del");
        TraceMetadata trace = new TraceMetadata("t-tx-del", null, null, null);

        DeleteAccountCommand command = new DeleteAccountCommand(
                "cmd-tx-del-1", IdMetadata.of("key-tx-del-1", null), actor, trace,
                "user-200", "soft delete user-200");

        RpcResult<AccountMutationDTO> result = provider.deleteAccount(command);
        assertThat(result.success()).isTrue();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT is_deleted, deleted_by FROM users WHERE id = 'user-200'");
        assertThat((Boolean) row.get("is_deleted")).isTrue();
        assertThat(row.get("deleted_by")).isEqualTo("admin-100");
    }
}
