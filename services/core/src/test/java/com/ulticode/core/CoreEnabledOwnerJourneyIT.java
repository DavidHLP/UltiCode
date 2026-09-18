package com.ulticode.core;

import com.ulticode.admin.security.DelegationAssertionSigner;
import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.security.LocalDelegationAssertionContext;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.admin.service.UserPermissionService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.flywaydb.core.Flyway;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * Opt-in proof of real Auth/Admin child wiring; it is intentionally not part of the Core smoke.
 *
 * <p>The test creates no container unless the shell gate supplies the explicit system property.
 * It applies the repository-owned Auth/Admin migrations to disposable schemas, boots the real
 * {@link CoreOwnerBootConfigurations.Admin} configuration through {@link CoreOwnerContextManager},
 * and exercises only local contract seams.
 */
class CoreEnabledOwnerJourneyIT {
    private static final String GATE_PROPERTY = "core.enabled.owner.journey";
    private static final LocalDateTime PERMISSION_EXPIRY =
            LocalDateTime.of(2099, 1, 1, 0, 0);
    private static int fixtureSequence;
    private static final Map<String, String> ORIGINAL_SYSTEM_PROPERTIES = new HashMap<>();

    private static MySQLContainer<?> mysql;
    private static GenericContainer<?> redis;
    private static CoreOwnerContextManager ownerContexts;
    private static Path repositoryRoot;

    private static String mysqlPassword;
    private static String redisPassword;
    private static String jwtSecret;

    @BeforeAll
    static void startJourney() throws Exception {
        assumeTrue(Boolean.getBoolean(GATE_PROPERTY), "Core enabled-owner gate was not requested");

        repositoryRoot = findRepositoryRoot();
        requireRepositoryInput(repositoryRoot.resolve("init-db/migrations/auth"));
        requireRepositoryInput(repositoryRoot.resolve("init-db/migrations/admin"));

        mysqlPassword = fixtureSecret();
        redisPassword = fixtureSecret();
        jwtSecret = fixtureSecret() + fixtureSecret();
        KeyPair delegationKeyPair = generateDelegationKeys();

        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("auth")
                .withUsername("root")
                .withPassword(mysqlPassword);
        redis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)
                .withCommand("redis-server", "--requirepass", redisPassword,
                        "--save", "", "--appendonly", "no");

        try {
            mysql.start();
            redis.start();
            configureSystemProperties();
            prepareSchemasAndFixtures();
            startOwnerContexts(delegationKeyPair);
        } catch (Exception failure) {
            closeJourney();
            throw failure;
        }
    }

    @Test
    void bootsReadyOwnersReadsIdentityGrantsPermissionAndFailsClosedWithoutSigner() {
        assertThat(ownerContexts.allReady()).isTrue();
        assertThat(ownerContexts.states())
                .containsEntry("auth", CoreOwnerContextManager.State.READY)
                .containsEntry("admin", CoreOwnerContextManager.State.READY);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "core-admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        try {
            IdentityQueryService identityQuery = ownerContexts.bean("admin", IdentityQueryService.class);
            assertThat(identityQuery).isInstanceOf(CoreLocalIdentityQueryAdapter.class);
            AccountQueryService accountQuery = ownerContexts.bean("admin", AccountQueryService.class);
            assertThat(accountQuery).isInstanceOf(CoreLocalAccountQueryAdapter.class);
            RpcResult<UserIdentityDTO> identity = identityQuery.getIdentity("core-user");
            assertThat(identity.success()).isTrue();
            assertThat(identity.data()).extracting(UserIdentityDTO::accountId, UserIdentityDTO::username)
                    .containsExactly("core-user", "core-user");

            UserPermissionService permissionService = ownerContexts.bean("admin", UserPermissionService.class);
            AuthorizationMutationDTO grant = permissionService.assignUserPermission(
                    "core-user", "READ", "PROBLEM", PERMISSION_EXPIRY);
            assertThat(grant).isNotNull();
            assertThat(grant.accountId()).isEqualTo("core-user");
            assertThat(grant.operation()).isEqualTo("GRANT");
            assertThat(grant.changed()).isTrue();

            CoreOwnerContextManager missingSignerOwnerContexts = spy(ownerContexts);
            doReturn(null).when(missingSignerOwnerContexts)
                    .bean("admin", DelegationAssertionSigner.class);
            PermissionMutationCommand command = new PermissionMutationCommand(
                    "missing-signer-command",
                    IdMetadata.of("missing-signer-key", null),
                    new ActorDelegation("SUPER_ADMIN", "core-admin", "core-admin", "test"),
                    new TraceMetadata("missing-signer-trace", null, null, null),
                    "core-user", PermissionMutationCommand.Operation.GRANT,
                    "READ", "PROBLEM", null, 0L, "missing signer proof");
            RpcResult<AuthorizationMutationDTO> missingSigner =
                    new CoreLocalAuthorizationMutationAdapter(missingSignerOwnerContexts)
                            .mutatePermission(command);
            assertThat(missingSigner.success()).isFalse();
            assertThat(missingSigner.error().code())
                    .isEqualTo(com.ulticode.common.error.BaseErrorCode.UNAUTHORIZED.code());
            assertThat(LocalDelegationAssertionContext.current()).isNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @AfterAll
    static void closeJourney() {
        try {
            if (ownerContexts != null) {
                ownerContexts.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));
                assertThat(ownerContexts.contextsSnapshot()).isEmpty();
                assertThat(ownerContexts.states())
                        .containsEntry("auth", CoreOwnerContextManager.State.STOPPED)
                        .containsEntry("admin", CoreOwnerContextManager.State.STOPPED);
            }
        } finally {
            try {
                if (redis != null) {
                    redis.stop();
                }
            } finally {
                try {
                    if (mysql != null) {
                        mysql.stop();
                    }
                } finally {
                    try {
                        restoreSystemProperties();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }
            }
        }
    }

    private static void startOwnerContexts(KeyPair delegationKeyPair) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("AUTH_DB_URL", jdbcUrl("auth"))
                .withProperty("ADMIN_DB_URL", jdbcUrl("admin"))
                .withProperty("AUTH_DB_USER", mysql.getUsername())
                .withProperty("ADMIN_DB_USER", mysql.getUsername())
                .withProperty("AUTH_DB_PASSWORD", mysqlPassword)
                .withProperty("ADMIN_DB_PASSWORD", mysqlPassword)
                .withProperty("REDIS_HOST", redis.getHost())
                .withProperty("REDIS_PORT", Integer.toString(redis.getMappedPort(6379)))
                .withProperty("REDIS_USERNAME", "default")
                .withProperty("REDIS_PASSWORD", redisPassword)
                .withProperty("AUTH_REDIS_USERNAME", "default")
                .withProperty("ADMIN_REDIS_USERNAME", "default")
                .withProperty("AUTH_REDIS_PASSWORD", redisPassword)
                .withProperty("ADMIN_REDIS_PASSWORD", redisPassword)
                .withProperty("INTERNAL_DELEGATION_PRIVATE_KEY", encode(delegationKeyPair.getPrivate().getEncoded()))
                .withProperty("INTERNAL_DELEGATION_PUBLIC_KEY", encode(delegationKeyPair.getPublic().getEncoded()))
                .withProperty("INTERNAL_DELEGATION_KEY_ID", "core-disposable-" + fixtureSecret().substring(0, 16))
                .withProperty("INTERNAL_DELEGATION_ISSUER", "backend-admin");
        ownerContexts = new CoreOwnerContextManager(new CoreModuleRegistry(), environment, true, 60_000L);
        ownerContexts.startOwnerModules();
        awaitOwnerStartup();
    }

    private static void prepareSchemasAndFixtures() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("auth"), mysql.getUsername(), mysqlPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `admin` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        applyMigrations("auth");
        applyMigrations("admin");
        try (Connection connection = DriverManager.getConnection(jdbcUrl("auth"), mysql.getUsername(), mysqlPassword);
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO users (id, username, email, password, role, is_active, is_banned,
                                        is_deleted, authz_version)
                     VALUES (?, ?, ?, ?, 'USER', 1, 0, 0, 0)
                     """)) {
            insert.setString(1, "core-user");
            insert.setString(2, "core-user");
            insert.setString(3, "core-user@example.invalid");
            insert.setString(4, fixtureSecret());
            insert.executeUpdate();
        }
    }

    private static void applyMigrations(String schema) {
        Path migrationDirectory = repositoryRoot.resolve("init-db/migrations").resolve(schema);
        Flyway.configure()
                .dataSource(jdbcUrl(schema), mysql.getUsername(), mysqlPassword)
                .locations("filesystem:" + migrationDirectory.toAbsolutePath())
                .defaultSchema(schema)
                .schemas(schema)
                .table("flyway_schema_history")
                .encoding(StandardCharsets.UTF_8)
                .baselineOnMigrate(false)
                .outOfOrder(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    private static void configureSystemProperties() {
        setSystemProperty("REDIS_HOST", redis.getHost());
        setSystemProperty("REDIS_PORT", Integer.toString(redis.getMappedPort(6379)));
        setSystemProperty("REDIS_DB", "0");
        setSystemProperty("REDIS_USERNAME", "default");
        setSystemProperty("REDIS_PASSWORD", redisPassword);
        setSystemProperty("AUTH_REDIS_USERNAME", "default");
        setSystemProperty("ADMIN_REDIS_USERNAME", "default");
        setSystemProperty("AUTH_REDIS_PASSWORD", redisPassword);
        setSystemProperty("ADMIN_REDIS_PASSWORD", redisPassword);
        setSystemProperty("AUTH_REDIS_URL", redisUrl());
        setSystemProperty("ADMIN_REDIS_URL", redisUrl());
        setSystemProperty("JWT_SECRET", jwtSecret);
        setSystemProperty("JWT_RSA_ENABLED", "false");
        setSystemProperty("AUTH_AUDIT_OUTBOX_DISPATCHER_ENABLED", "false");
        setSystemProperty("AUTH_SEARCH_OUTBOX_DISPATCHER_ENABLED", "false");
        setSystemProperty("ADMIN_AUDIT_INBOX_ENABLED", "false");
        setSystemProperty("MANAGEMENT_TRACING_SAMPLING_PROBABILITY", "0");
    }

    private static void awaitOwnerStartup() {
        try {
            ownerContexts.startupCompletion().toCompletableFuture().get(120, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for Core owner readiness", interrupted);
        } catch (java.util.concurrent.ExecutionException failure) {
            throw new AssertionError("Core enabled-owner child failed to start", failure.getCause());
        } catch (java.util.concurrent.TimeoutException timeout) {
            throw new AssertionError("Core enabled-owner child readiness timed out", timeout);
        }
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("init-db/migrations/auth"))
                    && Files.isDirectory(current.resolve("init-db/migrations/admin"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository migration inputs are unavailable");
    }

    private static void requireRepositoryInput(Path path) {
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("Required Core disposable input is unavailable: " + path);
        }
    }

    private static String jdbcUrl(String schema) {
        String base = mysql.getJdbcUrl();
        int databaseStart = base.indexOf('/', "jdbc:mysql://".length());
        int queryStart = base.indexOf('?', databaseStart);
        String suffix = queryStart < 0 ? "" : base.substring(queryStart);
        return base.substring(0, databaseStart + 1) + schema + suffix;
    }

    private static String redisUrl() {
        return "redis://default:" + redisPassword + "@" + redis.getHost() + ":"
                + redis.getMappedPort(6379) + "/0";
    }

    private static KeyPair generateDelegationKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed("core-enabled-owner-journey".getBytes(StandardCharsets.UTF_8));
        generator.initialize(2048, random);
        return generator.generateKeyPair();
    }

    private static String fixtureSecret() {
        return "core-disposable-fixture-" + ++fixtureSequence + "-value";
    }

    private static String encode(byte[] bytes) {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    private static void setSystemProperty(String key, String value) {
        ORIGINAL_SYSTEM_PROPERTIES.putIfAbsent(key, System.getProperty(key));
        System.setProperty(key, value);
    }

    private static void restoreSystemProperties() {
        for (Map.Entry<String, String> entry : ORIGINAL_SYSTEM_PROPERTIES.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
        ORIGINAL_SYSTEM_PROPERTIES.clear();
    }
