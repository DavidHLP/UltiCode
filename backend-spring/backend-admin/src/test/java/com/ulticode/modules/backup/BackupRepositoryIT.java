package com.ulticode.modules.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import com.ulticode.modules.backup.mapper.BackupMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Live MySQL CRUD round-trip integration test for the relocated backups slice.
 * <p>
 * Verifies the canonical migration {@code V20260724162738__Create_Backups_Table.sql}
 * is the additive source-of-truth (DDL ↔ {@link Backup} entity column parity) by
 * applying it once, then exercising INSERT → SELECT → UPDATE → DELETE through
 * MyBatis-Plus {@link BackupMapper} against a real MySQL 8.0 container.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
        + "org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration,"
        + "com.alibaba.cloud.dubbo.bootstrap.DubboBootstrapAutoConfiguration")
@Testcontainers
@DisplayName("BackupRepositoryIT — Real MySQL CRUD round-trip for /admin/backups")
class BackupRepositoryIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_admin_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/migration/V20260724162738__Create_Backups_Table.sql");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private BackupMapper backupMapper;

    @Test
    @DisplayName("INSERT → SELECT → UPDATE status → SELECT by id → DELETE round-trip")
    void insertSelectUpdateSelectDeleteRoundTrip() {
        // 1. INSERT
        Backup backup = new Backup();
        backup.setFilename("ulticode_admin_test_" + System.currentTimeMillis() + ".sql");
        backup.setSize(0L);
        backup.setType(BackupType.FULL);
        backup.setStatus(BackupStatus.PENDING);
        backup.setCreatedBy("admin-it-user");
        // com.ulticode.admin.config.MybatisPlusConfig; the IT does NOT set it
        // explicitly so the auto-fill is exercised.
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("trigger", "it-test");
        metadata.put("host", "test-container");
        backup.setMetadata(metadata);

        int inserted = backupMapper.insert(backup);
        assertThat(inserted).isEqualTo(1);
        assertThat(backup.getId()).as("@TableId ASSIGN_UUID must populate id on insert").isNotNull();
        assertThat(backup.getCreatedAt()).as("MetaObjectHandler auto-fill must populate createdAt").isNotNull();
        String id = backup.getId();

        try {
            // 2. SELECT (read-back)
            Backup readBack = backupMapper.selectById(id);
            assertThat(readBack).isNotNull();
            assertThat(readBack.getFilename()).isEqualTo(backup.getFilename());
            assertThat(readBack.getType()).isEqualTo(BackupType.FULL);
            assertThat(readBack.getStatus()).isEqualTo(BackupStatus.PENDING);
            assertThat(readBack.getCreatedBy()).isEqualTo("admin-it-user");
            assertThat(readBack.getMetadata()).as("JacksonTypeHandler must round-trip JSON").isNotNull();
            assertThat(readBack.getMetadata().get("trigger")).isEqualTo("it-test");
            assertThat(readBack.getCompletedAt()).isNull();
            assertThat(readBack.getError()).isNull();

            // 3. UPDATE status PENDING -> COMPLETED with size
            readBack.setStatus(BackupStatus.COMPLETED);
            readBack.setSize(2048L);
            int updated = backupMapper.updateById(readBack);
            assertThat(updated).isEqualTo(1);

            // 4. SELECT by id again
            Backup completed = backupMapper.selectById(id);
            assertThat(completed).isNotNull();
            assertThat(completed.getStatus()).isEqualTo(BackupStatus.COMPLETED);
            assertThat(completed.getSize()).isEqualTo(2048L);

            // 5. SELECT by (status, created_at) — idx_status_created_at
            LambdaQueryWrapper<Backup> byStatus = new LambdaQueryWrapper<>();
            byStatus.eq(Backup::getStatus, BackupStatus.COMPLETED)
                    .orderByDesc(Backup::getCreatedAt);
            List<Backup> completedRows = backupMapper.selectList(byStatus);
            assertThat(completedRows).extracting(Backup::getId).contains(id);

            // 6. SELECT by created_by — idx_created_by
            LambdaQueryWrapper<Backup> byCreator = new LambdaQueryWrapper<>();
            byCreator.eq(Backup::getCreatedBy, "admin-it-user");
            List<Backup> byCreatorRows = backupMapper.selectList(byCreator);
            assertThat(byCreatorRows).extracting(Backup::getId).contains(id);
        } finally {
            // 7. DELETE
            int deleted = backupMapper.deleteById(id);
            assertThat(deleted).isEqualTo(1);
            assertThat(backupMapper.selectById(id)).isNull();
        }
    }

    @Test
    @DisplayName("Enum mapping and default size=0 insert+read-back")
    void insertWithIncrementalType() {
        String uniqueId = UUID.randomUUID().toString();
        Backup backup = new Backup();
        backup.setId(uniqueId);
        backup.setFilename("incremental_" + uniqueId + ".sql");
        backup.setType(BackupType.INCREMENTAL);
        backup.setStatus(BackupStatus.IN_PROGRESS);
        backup.setCreatedBy("admin-it-user");
        // createdAt is auto-filled by the MetaObjectHandler bean.

        try {
            int inserted = backupMapper.insert(backup);
            assertThat(inserted).isEqualTo(1);
            assertThat(backup.getCreatedAt()).as("MetaObjectHandler auto-fill must populate createdAt").isNotNull();

            Backup readBack = backupMapper.selectById(uniqueId);
            assertThat(readBack).isNotNull();
            assertThat(readBack.getType()).isEqualTo(BackupType.INCREMENTAL);
            assertThat(readBack.getStatus()).isEqualTo(BackupStatus.IN_PROGRESS);
            assertThat(readBack.getSize()).as("DDL default size = 0").isEqualTo(0L);
        } finally {
            backupMapper.deleteById(uniqueId);
        }
    }

    @Test
    @DisplayName("FAILED status with error message round-trip")
    void insertFailedWithErrorMessage() {
        String uniqueId = UUID.randomUUID().toString();
        Backup backup = new Backup();
        backup.setId(uniqueId);
        backup.setFilename("failed_" + uniqueId + ".sql");
        backup.setType(BackupType.FULL);
        backup.setStatus(BackupStatus.FAILED);
        backup.setCreatedBy("admin-it-user");
        backup.setError("mysqldump returned exit 2: permission denied on /var/backups");

        try {
            backupMapper.insert(backup);
            assertThat(backup.getCreatedAt()).as("MetaObjectHandler auto-fill must populate createdAt").isNotNull();
            Backup readBack = backupMapper.selectById(uniqueId);
            assertThat(readBack).isNotNull();
            assertThat(readBack.getStatus()).isEqualTo(BackupStatus.FAILED);
            assertThat(readBack.getError()).contains("permission denied");
            assertThat(readBack.getCompletedAt()).isNull();
        } finally {
            backupMapper.deleteById(uniqueId);
        }
    }
}
