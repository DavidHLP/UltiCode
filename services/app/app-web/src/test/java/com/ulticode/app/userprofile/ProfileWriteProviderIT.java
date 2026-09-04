package com.ulticode.app.userprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.app.userprofile.provider.ProfileWriteProvider;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

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
                UserProfileMapper.class,
                AppCommandReceiptMapper.class,
                DataSourceAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                JacksonAutoConfiguration.class
        },
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        }
)
@MapperScan({"com.ulticode.app.userprofile.mapper", "com.ulticode.app.idempotency.mapper"})
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
                    "/docker-entrypoint-initdb.d/V20260801000000__Create_App_Command_Receipt.sql");

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

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private ProfileWriteService profileWriteService;

    @MockBean
    private AdminActorAuthorizer adminActorAuthorizer;

    @BeforeEach
    void configureActorAuthorizer() {
        when(adminActorAuthorizer.isAuthorized(any())).thenReturn(true);
    }

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private AppCommandReceiptMapper receiptMapper;

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
        return new UpdateProfileCommand(
                UUID.randomUUID().toString(),
                new IdMetadata(idempotencyKey, null, null),
                testActor(),
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
}
