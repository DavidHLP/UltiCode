package com.ulticode.app.userprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.app.storage.StorageCleanupOutbox;
import com.ulticode.app.storage.StorageCleanupOutboxMapper;
import com.ulticode.app.storage.StorageCleanupOutboxRecord;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.app.userprofile.provider.ProfileWriteProvider;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.search.port.UserDirectoryQueryPort;
import com.ulticode.modules.search.port.UserDirectoryRow;
import com.ulticode.modules.search.port.UserSearchRow;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Real MySQL CRUD round-trip IT for {@link ProfileWriteProvider}.
 *
 * <p>Uses an isolated Spring context that loads ONLY the provider, mappers,
 * DataSource, MyBatis-Plus, and Jackson auto-configuration — not the full
 * {@code @SpringBootApplication} scan.
 *
 * <p>Tests cover: (1) basic CRUD, (2) partial-field null-skip update,
 * (3) command validation, (4) replay dedup via receipt, (5) reordered
 * retry idempotency, (6) fingerprint conflict detection.
 */
@SpringBootTest(
        classes = {
                ProfileWriteProvider.class,
                ProfileMutationModule.class,
                com.ulticode.app.idempotency.CommandReceiptExecutor.class,
                UserProfileMapper.class,
                AppCommandReceiptMapper.class,
                StorageCleanupOutbox.class,
                StorageCleanupOutboxMapper.class,
                ProfileReceiptTestConfig.class,
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                TransactionAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                JacksonAutoConfiguration.class
        },
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        }
)
@MapperScan({"com.ulticode.app.userprofile.mapper", "com.ulticode.app.idempotency.mapper",
        "com.ulticode.app.storage"})
@Testcontainers
@DisplayName("ProfileWriteProviderIT — Real MySQL CRUD + idempotency for user_profiles")
class ProfileWriteProviderIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_app_test")
            .withUsername("test")
            .withPassword("test")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(userProfilesMigrationPath().toString()),
                    "/docker-entrypoint-initdb.d/V20260729140400__Create_User_Profiles_Table.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(receiptMigrationPath().toString()),
                    "/docker-entrypoint-initdb.d/V20260801000000__Create_App_Command_Receipt.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(storageCleanupMigrationPath().toString()),
                    "/docker-entrypoint-initdb.d/V20260924120000__Create_Storage_Cleanup_Outbox.sql");

    private static Path findMigration(String filename) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("init-db/migrations/app/" + filename);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(filename + " not found from user.dir=" + System.getProperty("user.dir"));
    }

    private static Path userProfilesMigrationPath() {
        return findMigration("V20260729140400__Create_User_Profiles_Table.sql");
    }

    private static Path receiptMigrationPath() {
        return findMigration("V20260801000000__Create_App_Command_Receipt.sql");
    }
    private static Path storageCleanupMigrationPath() {
        return findMigration("V20260924120000__Create_Storage_Cleanup_Outbox.sql");
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private ProfileWriteService profileWriteService;

    @MockitoBean
    private AdminActorAuthorizer adminActorAuthorizer;



    @MockitoBean
    private UserDirectoryQueryPort userDirectoryQueryPort;

    @MockitoBean
    private SearchDocumentChangedPublisher searchPublisher;

    @BeforeEach
    void configureActorAuthorizer() {
        when(adminActorAuthorizer.isAuthorized(any())).thenReturn(true);
    }

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private AppCommandReceiptMapper receiptMapper;
    @Autowired
    private StorageCleanupOutboxMapper storageCleanupOutboxMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static UserDirectoryRow directoryRow(
            String id, String username, String name, String avatar) {
        UserSearchRow row = new UserSearchRow();
        row.setId(id);
        row.setUsername(username);
        row.setName(name);
        row.setAvatar(avatar);
        return UserDirectoryRow.from(row);
    }

    private static ActorDelegation testActor() {
        String uuid = UUID.randomUUID().toString();
        return new ActorDelegation("USER", uuid, uuid, "test");
    }

    private static ActorDelegation testActor(String actorType) {
        String uuid = UUID.randomUUID().toString();
        return new ActorDelegation(actorType, uuid, uuid, "test");
    }

    private static UpdateProfileCommand command(String accountId, String name, String bio) {
        return new UpdateProfileCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                testActor(),
                TraceMetadata.EMPTY,
                accountId, name, null, bio,
                null, null, null, null, null, null);
    }

    private static UpdateProfileCommand commandWithKey(
            String idempotencyKey, String accountId, String name, String bio) {
        return commandWithKey(idempotencyKey, accountId, name, bio, testActor());
    }

    private static UpdateProfileCommand commandWithKey(
            String idempotencyKey, String accountId, String name, String bio,
            ActorDelegation actor) {
        return new UpdateProfileCommand(
                UUID.randomUUID().toString(),
                new IdMetadata(idempotencyKey, null, null),
                actor,
                TraceMetadata.EMPTY,
                accountId, name, null, bio,
                null, null, null, null, null, null);
    }

    // ===== Existing CRUD tests =====

    @Test
    @DisplayName("INSERT new profile via provider → read-back via mapper → verify fields persisted")
    void insertNewProfileAndReadBack() {
        String accountId = UUID.randomUUID().toString();

        RpcResult<ProfileWriteResult> result =
                profileWriteService.updateProfile(command(accountId, "Jane Doe", "Software engineer"));

        assertThat(result.success()).isTrue();
        assertThat(result.data().accountId()).isEqualTo(accountId);
        assertThat(result.data().name()).isEqualTo("Jane Doe");
        assertThat(result.data().bio()).isEqualTo("Software engineer");

        UserProfile persisted = userProfileMapper.selectById(accountId);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getName()).isEqualTo("Jane Doe");
        assertThat(persisted.getBio()).isEqualTo("Software engineer");
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("UPDATE existing profile partial fields via provider → null fields unchanged")
    void updateExistingProfilePartialFields() {
        String accountId = UUID.randomUUID().toString();

        profileWriteService.updateProfile(new UpdateProfileCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), testActor(), TraceMetadata.EMPTY,
                accountId, "John", "/avatars/john.png", "Developer",
                "Acme", "johngh", "NYC", "@john", "john.dev", "java"));

        UpdateProfileCommand partialUpdate = new UpdateProfileCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), testActor(), TraceMetadata.EMPTY,
                accountId, "John Updated", null, "Senior Developer",
                null, null, null, null, null, null);

        RpcResult<ProfileWriteResult> result = profileWriteService.updateProfile(partialUpdate);

        assertThat(result.success()).isTrue();
        assertThat(result.data().name()).isEqualTo("John Updated");
        assertThat(result.data().bio()).isEqualTo("Senior Developer");
        assertThat(result.data().avatar()).isEqualTo("/avatars/john.png");
        assertThat(result.data().company()).isEqualTo("Acme");
        assertThat(result.data().preferredLanguage()).isEqualTo("java");
    }

    @Test
    @DisplayName("Overlong name is rejected by command validation before reaching DB")
    void overlongNameRejectedByValidation() {
        String accountId = UUID.randomUUID().toString();
        String tooLongName = "x".repeat(121);

        assertThatThrownBy(() -> new UpdateProfileCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), testActor(), TraceMetadata.EMPTY,
                accountId, tooLongName, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not exceed 120");
    }

    @Test
    @DisplayName("Blank accountId is rejected by command validation")
    void blankAccountIdRejectedByValidation() {
        assertThatThrownBy(() -> new UpdateProfileCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), testActor(), TraceMetadata.EMPTY,
                "  ", null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accountId is required");
    }

    // ===== New idempotency / replay-dedup tests =====

    @Test
    @DisplayName("Same-command retry (same idempotencyKey) → replays stored result, no double-write")
    void sameCommandRetryReplaysStoredResult() {
        String accountId = UUID.randomUUID().toString();
        String key = "retry-key-" + UUID.randomUUID();

        UpdateProfileCommand cmdA = commandWithKey(key, accountId, "Alice", "Engineer");

        // First execution
        RpcResult<ProfileWriteResult> result1 = profileWriteService.updateProfile(cmdA);
        assertThat(result1.success()).isTrue();
        assertThat(result1.data().name()).isEqualTo("Alice");

        // Retry with same key + same payload → should replay, not re-execute
        RpcResult<ProfileWriteResult> result2 = profileWriteService.updateProfile(cmdA);
        assertThat(result2.success()).isTrue();
        assertThat(result2.data().name()).isEqualTo("Alice");
        assertThat(result2.data().accountId()).isEqualTo(accountId);

        // Verify only one row in user_profiles (no double-write)
        UserProfile profile = userProfileMapper.selectById(accountId);
        assertThat(profile).isNotNull();
        assertThat(profile.getName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Reordered retry A→B→retry-A → replay-A does NOT overwrite B's newer value")
    void reorderedRetryDoesNotOverwriteNewerValue() {
        String accountId = UUID.randomUUID().toString();
        String keyA = "cmd-A-" + UUID.randomUUID();
        String keyB = "cmd-B-" + UUID.randomUUID();

        // Command A: name=Alice
        UpdateProfileCommand cmdA = commandWithKey(keyA, accountId, "Alice", "Engineer");
        profileWriteService.updateProfile(cmdA);

        // Command B: name=Bob (different idempotency key, newer write)
        UpdateProfileCommand cmdB = commandWithKey(keyB, accountId, "Bob", "Senior Engineer");
        RpcResult<ProfileWriteResult> resultB = profileWriteService.updateProfile(cmdB);
        assertThat(resultB.success()).isTrue();
        assertThat(resultB.data().name()).isEqualTo("Bob");

        // Retry Command A (same keyA, same payload as original A)
        // Without dedup: would overwrite name back to "Alice"
        // With dedup: replays A's stored result, user_profiles stays "Bob"
        RpcResult<ProfileWriteResult> replayA = profileWriteService.updateProfile(cmdA);
        assertThat(replayA.success()).isTrue();
        // Replay returns A's original result
        assertThat(replayA.data().name()).isEqualTo("Alice");

        // But the database still has B's value — A's retry did NOT overwrite
        UserProfile profile = userProfileMapper.selectById(accountId);
        assertThat(profile.getName()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("Different payload with same idempotencyKey → IDEMPOTENCY_KEY_CONFLICT")
    void differentPayloadSameKeyReturnsConflict() {
        String accountId = UUID.randomUUID().toString();
        String key = "shared-key-" + UUID.randomUUID();

        // First request with this key: name=Alice
        UpdateProfileCommand cmd1 = commandWithKey(key, accountId, "Alice", "Engineer");
        RpcResult<ProfileWriteResult> result1 = profileWriteService.updateProfile(cmd1);
        assertThat(result1.success()).isTrue();

        // Second request reusing same key but DIFFERENT payload: name=Charlie
        UpdateProfileCommand cmd2 = commandWithKey(key, accountId, "Charlie", "Manager");
        RpcResult<ProfileWriteResult> result2 = profileWriteService.updateProfile(cmd2);

        // Should fail with IDEMPOTENCY_KEY_CONFLICT
        assertThat(result2.success()).isFalse();
        assertThat(result2.error().code()).isEqualTo(AppErrorCode.IDEMPOTENCY_KEY_CONFLICT.code());
    }

    @Test
    @DisplayName("uploadAvatar sets avatar column via dedicated command")
    void uploadAvatarSetsAvatarColumn() {
        String accountId = UUID.randomUUID().toString();
        String avatarUrl = "/avatars/" + accountId + ".png";

        UpdateProfileCommand initial = command(accountId, "Alice", "Engineer");
        profileWriteService.updateProfile(initial);

        UpdateProfileCommand cmdWithAvatar = command(accountId, null, null);
        RpcResult<ProfileWriteResult> result = profileWriteService.uploadAvatar(
                new UploadAvatarCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        testActor(),
                        TraceMetadata.EMPTY,
                        accountId,
                        avatarUrl));

        assertThat(result.success()).isTrue();
        assertThat(result.data().avatar()).isEqualTo(avatarUrl);

        UserProfile persisted = userProfileMapper.selectById(accountId);
        assertThat(persisted.getAvatar()).isEqualTo(avatarUrl);
        // Name and bio from initial update should be preserved
        assertThat(persisted.getName()).isEqualTo("Alice");
        assertThat(persisted.getBio()).isEqualTo("Engineer");
    }

    @Test
    @DisplayName("search publication failure rolls back avatar mutation through the Spring proxy")
    void searchPublicationFailureRollsBackAvatarMutation() {
        String accountId = UUID.randomUUID().toString();
        profileWriteService.updateProfile(command(accountId, "Alice", "Engineer"));

        when(userDirectoryQueryPort.findById(accountId))
                .thenReturn(directoryRow(accountId, "alice", "Alice", "app/avatars/" + accountId + "/new.png"));
        doThrow(new IllegalStateException("search unavailable"))
                .when(searchPublisher).publishUser(any(), any(), any(), any(), anyBoolean());

        String key = "rollback-avatar-" + UUID.randomUUID();
        UploadAvatarCommand avatarCommand = new UploadAvatarCommand(
                UUID.randomUUID().toString(),
                new IdMetadata(key, null, null),
                testActor(),
                TraceMetadata.EMPTY,
                accountId,
                "app/avatars/" + accountId + "/new.png");
        RpcResult<ProfileWriteResult> result = profileWriteService.uploadAvatar(avatarCommand);
        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.UNEXPECTED_APP_STATE.code());

        UserProfile persisted = userProfileMapper.selectById(accountId);
        assertThat(persisted.getAvatar()).isNull();
        AppCommandReceiptEntity receipt = receiptMapper.findByReceiptKey(
                "ProfileWriteService", "uploadAvatar", key);
        assertThat(receipt).isNull();
    }
    @Test
    @DisplayName("cleanup outbox failure rolls back profile mutation and receipt claim")
    void cleanupFailureRollsBackProfileAndReceipt() {
        String accountId = UUID.randomUUID().toString();
        String originalAvatar = "app/avatars/" + accountId + "/original.png";
        UserProfile seed = new UserProfile();
        seed.setAccountId(accountId);
        seed.setAvatar(originalAvatar);
        assertThat(userProfileMapper.insert(seed)).isEqualTo(1);

        String key = "cleanup-failure-" + UUID.randomUUID();
        String trigger = "reject_cleanup_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("CREATE TRIGGER `" + trigger + "` "
                + "BEFORE INSERT ON `storage_cleanup_outbox` FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced cleanup failure'");
        try {
            UploadAvatarCommand command = new UploadAvatarCommand(
                    UUID.randomUUID().toString(),
                    new IdMetadata(key, null, null),
                    testActor(),
                    TraceMetadata.EMPTY,
                    accountId,
                    "app/avatars/" + accountId + "/replacement.png");

            RpcResult<ProfileWriteResult> result = profileWriteService.uploadAvatar(command);

            assertThat(result.success()).isFalse();
            UserProfile persisted = userProfileMapper.selectById(accountId);
            assertThat(persisted).isNotNull();
            assertThat(persisted.getAvatar()).isEqualTo(originalAvatar);
            assertThat(receiptMapper.findByReceiptKey(
                    "ProfileWriteService", "uploadAvatar", key)).isNull();
            assertThat(storageCleanupOutboxMapper.selectList(
                    new QueryWrapper<StorageCleanupOutboxRecord>()
                            .eq("object_key", originalAvatar))).isEmpty();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER `" + trigger + "`");
        }
    }

    @Test
    @DisplayName("receipt finalize failure rolls back profile mutation and receipt claim")
    void receiptFinalizeFailureRollsBackProfileAndReceipt() {
        String accountId = UUID.randomUUID().toString();
        String key = "finalize-failure-" + UUID.randomUUID();
        String trigger = "reject_finalize_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("CREATE TRIGGER `" + trigger + "` "
                + "BEFORE UPDATE ON `app_command_receipt` FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced finalize failure'");
        try {
            RpcResult<ProfileWriteResult> result = profileWriteService.updateProfile(
                    commandWithKey(key, accountId, "Finalize", "must roll back"));

            assertThat(result.success()).isFalse();
            assertThat(userProfileMapper.selectById(accountId)).isNull();
            assertThat(receiptMapper.findByReceiptKey(
                    "ProfileWriteService", "updateProfile", key)).isNull();
        } finally {
            jdbcTemplate.execute("DROP TRIGGER `" + trigger + "`");
        }
    }


    @Test
    @DisplayName("uploadAvatar replay with same idempotencyKey returns stored result")
    void uploadAvatarReplayWithSameKey() {
        String accountId = UUID.randomUUID().toString();
        String key = "avatar-key-" + UUID.randomUUID();
        String avatarUrl = "/avatars/replay.png";

        UploadAvatarCommand cmd = new UploadAvatarCommand(
                UUID.randomUUID().toString(),
                new IdMetadata(key, null, null),
                testActor(),
                TraceMetadata.EMPTY,
                accountId,
                avatarUrl);

        RpcResult<ProfileWriteResult> result1 = profileWriteService.uploadAvatar(cmd);
        assertThat(result1.success()).isTrue();

        // Retry with same key → replay
        RpcResult<ProfileWriteResult> result2 = profileWriteService.uploadAvatar(cmd);
        assertThat(result2.success()).isTrue();
        assertThat(result2.data().avatar()).isEqualTo(avatarUrl);
    }

    @Test
    @DisplayName("idempotency receipts preserve delegated actor identity")
    void receiptsPreserveDelegatedActorIdentity() {
        for (String actorType : new String[]{"ADMIN", "MODERATOR", "SERVICE"}) {
            String accountId = UUID.randomUUID().toString();
            ActorDelegation actor = testActor(actorType);
            String updateKey = "profile-actor-" + actorType + "-" + UUID.randomUUID();
            UpdateProfileCommand update = new UpdateProfileCommand(
                    UUID.randomUUID().toString(),
                    new IdMetadata(updateKey, null, null),
                    actor,
                    TraceMetadata.EMPTY,
                    accountId,
                    "Delegated",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

            assertThat(profileWriteService.updateProfile(update).success()).isTrue();
            AppCommandReceiptEntity profileReceipt = receiptMapper.findByReceiptKey(
                    "ProfileWriteService", "updateProfile", updateKey);
            assertThat(profileReceipt).isNotNull();
            assertThat(profileReceipt.getActorType()).isEqualTo(actorType);
            assertThat(profileReceipt.getActorId()).isEqualTo(actor.actorId());

            String avatarKey = "avatar-actor-" + actorType + "-" + UUID.randomUUID();
            UploadAvatarCommand avatar = new UploadAvatarCommand(
                    UUID.randomUUID().toString(),
                    new IdMetadata(avatarKey, null, null),
                    actor,
                    TraceMetadata.EMPTY,
                    accountId,
                    "/avatars/" + actorType.toLowerCase() + ".png");

            assertThat(profileWriteService.uploadAvatar(avatar).success()).isTrue();
            AppCommandReceiptEntity avatarReceipt = receiptMapper.findByReceiptKey(
                    "ProfileWriteService", "uploadAvatar", avatarKey);
            assertThat(avatarReceipt).isNotNull();
            assertThat(avatarReceipt.getActorType()).isEqualTo(actorType);
            assertThat(avatarReceipt.getActorId()).isEqualTo(actor.actorId());
        }
    }
    @Test
    @DisplayName("legacy update fingerprint replays without re-running profile mutation")
    void legacyUpdateReceiptReplaysWithoutMutation() {
        String accountId = UUID.randomUUID().toString();
        String key = "legacy-update-" + UUID.randomUUID();
        UpdateProfileCommand command = commandWithKey(
                key, accountId, "Legacy Name", "Legacy Bio", fixedActor());
        insertReceipt(
                command,
                "updateProfile",
                legacyUpdateFingerprint(command),
                "{\"accountId\":\"" + accountId
                        + "\",\"name\":\"Legacy Name\",\"bio\":\"Legacy Bio\"}");

        RpcResult<ProfileWriteResult> result = profileWriteService.updateProfile(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().name()).isEqualTo("Legacy Name");
        assertThat(userProfileMapper.selectById(accountId)).isNull();
    }

    @Test
    @DisplayName("legacy avatar fingerprint replays without re-running profile mutation")
    void legacyAvatarReceiptReplaysWithoutMutation() {
        String accountId = UUID.randomUUID().toString();
        String key = "legacy-avatar-" + UUID.randomUUID();
        UploadAvatarCommand command = new UploadAvatarCommand(
                UUID.randomUUID().toString(),
                new IdMetadata(key, null, null),
                fixedActor(),
                TraceMetadata.EMPTY,
                accountId,
                "app/avatars/" + accountId + "/legacy.png");
        insertReceipt(
                command,
                "uploadAvatar",
                legacyAvatarFingerprint(command),
                "{\"accountId\":\"" + accountId
                        + "\",\"avatar\":\"app/avatars/" + accountId + "/legacy.png\"}");

        RpcResult<ProfileWriteResult> result = profileWriteService.uploadAvatar(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().avatar()).isEqualTo(command.avatarUrl());
        assertThat(userProfileMapper.selectById(accountId)).isNull();
    }

    @Test
    @DisplayName("concurrent same-key commands claim one receipt and perform one mutation")
    void concurrentSameKeyClaimsOnlyOnce() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String key = "concurrent-" + UUID.randomUUID();
        ActorDelegation actor = fixedActor();
        UpdateProfileCommand firstCommand = commandWithKey(
                key, accountId, "Concurrent", "Only once", actor);
        UpdateProfileCommand secondCommand = commandWithKey(
                key, accountId, "Concurrent", "Only once", actor);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RpcResult<ProfileWriteResult>> first = pool.submit(() -> {
                start.await();
                return profileWriteService.updateProfile(firstCommand);
            });
            Future<RpcResult<ProfileWriteResult>> second = pool.submit(() -> {
                start.await();
                return profileWriteService.updateProfile(secondCommand);
            });
            start.countDown();

            assertThat(first.get().success()).isTrue();
            assertThat(second.get().success()).isTrue();
        } finally {
            pool.shutdownNow();
        }

        AppCommandReceiptEntity receipt = receiptMapper.findByReceiptKey(
                "ProfileWriteService", "updateProfile", key);
        assertThat(receipt).isNotNull();
        assertThat(receipt.getStatus()).isEqualTo("SUCCESS");
        UserProfile persisted = userProfileMapper.selectById(accountId);
        assertThat(persisted).isNotNull();
        assertThat(persisted.getName()).isEqualTo("Concurrent");
    }

    @Test
    @DisplayName("concurrent avatar replacements lock the row and clean the original plus loser key")
    void concurrentAvatarReplacementsCleanOriginalAndLoserKey() throws Exception {
        assertConcurrentAvatarReplacement("first.png", "second.png");
    }

    @Test
    @DisplayName("reverse concurrent avatar replacements still clean the original plus loser key")
    void reverseConcurrentAvatarReplacementsCleanOriginalAndLoserKey() throws Exception {
        assertConcurrentAvatarReplacement("second.png", "first.png");
    }

    private void assertConcurrentAvatarReplacement(
            String firstObjectName, String secondObjectName) throws Exception {
        String accountId = UUID.randomUUID().toString();
        String originalKey = "app/avatars/" + accountId + "/original.png";
        UserProfile seed = new UserProfile();
        seed.setAccountId(accountId);
        seed.setAvatar(originalKey);
        assertThat(userProfileMapper.insert(seed)).isEqualTo(1);

        String firstKey = "app/avatars/" + accountId + "/" + firstObjectName;
        String secondKey = "app/avatars/" + accountId + "/" + secondObjectName;
        UploadAvatarCommand firstCommand = avatarReplacement(accountId, firstKey);
        UploadAvatarCommand secondCommand = avatarReplacement(accountId, secondKey);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RpcResult<ProfileWriteResult>> first = pool.submit(() -> {
                start.await();
                return profileWriteService.uploadAvatar(firstCommand);
            });
            Future<RpcResult<ProfileWriteResult>> second = pool.submit(() -> {
                start.await();
                return profileWriteService.uploadAvatar(secondCommand);
            });
            start.countDown();

            assertThat(first.get().success()).isTrue();
            assertThat(second.get().success()).isTrue();
        } finally {
            pool.shutdownNow();
        }

        UserProfile persisted = userProfileMapper.selectById(accountId);
        assertThat(persisted).isNotNull();
        assertThat(List.of(firstKey, secondKey)).contains(persisted.getAvatar());
        String loserKey = firstKey.equals(persisted.getAvatar()) ? secondKey : firstKey;
        List<String> cleanupKeys = storageCleanupOutboxMapper
                .selectList(new QueryWrapper<StorageCleanupOutboxRecord>())
                .stream()
                .map(StorageCleanupOutboxRecord::getObjectKey)
                .toList();
        assertThat(cleanupKeys).contains(originalKey, loserKey);
        assertThat(cleanupKeys).doesNotContain(persisted.getAvatar());
    }

    private static UploadAvatarCommand avatarReplacement(String accountId, String avatarReference) {
        return new UploadAvatarCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                fixedActor(),
                TraceMetadata.EMPTY,
                accountId,
                avatarReference);
    }

    private void insertReceipt(
            com.ulticode.common.command.WriteCommand command,
            String operation,
            String fingerprint,
            String payload) {
        AppCommandReceiptEntity receipt = new AppCommandReceiptEntity();
        receipt.setId(UUID.randomUUID().toString());
        receipt.setCommandId(command.commandId());
        receipt.setService("ProfileWriteService");
        receipt.setOperation(operation);
        receipt.setIdempotencyKey(command.idempotency().idempotencyKey());
        receipt.setRequestFingerprint(fingerprint);
        receipt.setStatus("SUCCESS");
        receipt.setResultPayload(payload);
        receipt.setActorType(command.actor().actorType());
        receipt.setActorId(command.actor().actorId());
        receipt.setTraceId("legacy-test");
        receipt.setCreatedAt(java.time.LocalDateTime.now());
        assertThat(receiptMapper.insert(receipt)).isEqualTo(1);
    }

    private static ActorDelegation fixedActor() {
        return new ActorDelegation("USER", "concurrent-user", "concurrent-user", "test");
    }

    private static String legacyUpdateFingerprint(UpdateProfileCommand command) {
        return sha256(String.join("|",
                nullSafe(command.accountId()),
                nullSafe(command.name()),
                nullSafe(command.avatar()),
                nullSafe(command.bio()),
                nullSafe(command.company()),
                nullSafe(command.github()),
                nullSafe(command.location()),
                nullSafe(command.twitter()),
                nullSafe(command.website()),
                nullSafe(command.preferredLanguage())));
    }

    private static String legacyAvatarFingerprint(UploadAvatarCommand command) {
        return sha256(command.accountId() + "|" + command.avatarUrl());
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    @TestConfiguration
    static class ProfileReceiptTestConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

}
