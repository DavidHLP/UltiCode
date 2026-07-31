package com.ulticode.app.userprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.app.userprofile.provider.ProfileWriteProvider;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * Real MySQL CRUD round-trip IT for {@link ProfileWriteProvider}.
 */
@SpringBootTest(
        classes = {
                ProfileWriteProvider.class,
                UserProfileMapper.class,
                DataSourceAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class
        },
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        }
)
@MapperScan("com.ulticode.app.userprofile.mapper")
@Testcontainers
@DisplayName("ProfileWriteProviderIT — Real MySQL CRUD for user_profiles via ProfileWriteService")
class ProfileWriteProviderIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_app_test")
            .withUsername("test")
            .withPassword("test")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(canonicalMigrationPath().toString()),
                    "/docker-entrypoint-initdb.d/V20260729140400__Create_User_Profiles_Table.sql");

    private static Path canonicalMigrationPath() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("init-db/migrations/app/V20260729140400__Create_User_Profiles_Table.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("user_profiles migration not found from user.dir="
                + System.getProperty("user.dir"));
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

    @Autowired
    private UserProfileMapper userProfileMapper;

    private static ActorDelegation testActor() {
        String uuid = UUID.randomUUID().toString();
        return new ActorDelegation("USER", uuid, uuid, "test");
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

    @Test
    @DisplayName("uploadAvatar sets avatar column via dedicated command")
    void uploadAvatarSetsAvatarColumn() {
        String accountId = UUID.randomUUID().toString();
        String avatarUrl = "/avatars/" + accountId + ".png";

        profileWriteService.updateProfile(command(accountId, "Alice", "Engineer"));

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
        assertThat(persisted.getName()).isEqualTo("Alice");
        assertThat(persisted.getBio()).isEqualTo("Engineer");
    }

    @Test
    @DisplayName("uploadAvatar command rejects blank avatarUrl")
    void uploadAvatarRejectsBlankUrl() {
        assertThatThrownBy(() -> new UploadAvatarCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), testActor(), TraceMetadata.EMPTY,
                UUID.randomUUID().toString(), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("avatarUrl is required");
    }
}
