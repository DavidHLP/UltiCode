package com.ulticode.modules.admin.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ulticode.common.audit.AuditSinkPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
        + "org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration,"
        + "com.alibaba.cloud.dubbo.bootstrap.DubboBootstrapAutoConfiguration")
@Testcontainers
class AuditSinkTransactionIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_admin_audit_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private AuditSinkPort auditSink;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void recreateOutbox() throws IOException {
        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_outbox");
        jdbcTemplate.execute(Files.readString(findMigration()));
    }

    @Test
    @DisplayName("caller rollback removes the local outbox insert")
    void callerRollbackLeavesNoOutboxRow() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            writeAudit("ROLLBACK_ACTION");
            throw new IllegalStateException("force caller rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("caller commit creates exactly one pending outbox row")
    void callerCommitCreatesOnePendingRow() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> writeAudit("COMMIT_ACTION"));

        assertThat(rowCount()).isEqualTo(1);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT action, entity_type, entity_id, state, old_values, new_values FROM audit_outbox");
        assertThat(row.get("action")).isEqualTo("COMMIT_ACTION");
        assertThat(row.get("entity_type")).isEqualTo("SETTING");
        assertThat(row.get("entity_id")).isEqualTo("general");
        assertThat(row.get("state")).isEqualTo("PENDING");
        assertThat(row.get("old_values").toString()).contains("before");
        assertThat(row.get("new_values").toString()).contains("after");
    }

    private void writeAudit(String action) {
        auditSink.log(
                "admin-1",
                null,
                action,
                "SETTING",
                "general",
                Map.of("value", "before"),
                Map.of("value", "after"),
                "127.0.0.1",
                "audit-writer-it");
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_outbox", Integer.class);
    }

    private Path findMigration() {
        for (Path candidate : new Path[] {
                Path.of("../../init-db/migrations/V20260728203000__Create_Audit_Outbox.sql"),
                Path.of("../init-db/migrations/V20260728203000__Create_Audit_Outbox.sql"),
                Path.of("init-db/migrations/V20260728203000__Create_Audit_Outbox.sql")
        }) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Canonical audit outbox migration not found");
    }
}
