package com.ulticode.core;

import com.ulticode.admin.security.DelegationAssertionSigner;
import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthUserTrendAggregateQuery;
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
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import org.flywaydb.core.Flyway;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * Opt-in proof of real Auth/Admin child wiring and its bounded HTTP journey.
 *
 * <p>The test creates no container unless the shell gate supplies the explicit system property.
 * It applies the repository-owned Auth/Admin migrations to disposable schemas, boots the real
 * {@link CoreOwnerBootConfigurations.Admin} configuration through {@link CoreOwnerContextManager},
 * and exercises the original HTTP controllers/security plus local contract seams.
 */
class CoreEnabledOwnerJourneyIT {
    private static final String GATE_PROPERTY = "core.enabled.owner.journey";
    private static final long OWNER_STARTUP_TIMEOUT_MS = 120_000L;
    private static final LocalDateTime PERMISSION_EXPIRY =
            LocalDateTime.of(2099, 1, 1, 0, 0);
    private static int fixtureSequence;
    private static final Map<String, String> ORIGINAL_SYSTEM_PROPERTIES = new HashMap<>();

    private static MySQLContainer<?> mysql;
    private static GenericContainer<?> redis;
    private static CoreOwnerContextManager ownerContexts;
    private static ConfigurableApplicationContext parentContext;
    private static URI httpRoot;
    private static String userPassword;
    private static String adminPassword;
    private static Path repositoryRoot;

    private static String mysqlPassword;
    private static String authOwnerPassword;
    private static String adminOwnerPassword;
    private static String authRedisPassword;
    private static String adminRedisPassword;
    private static String jwtSecret;
    private static Path redisAclFile;

    @BeforeAll
    static void startJourney() throws Exception {
        assumeTrue(Boolean.getBoolean(GATE_PROPERTY), "Core enabled-owner gate was not requested");

        repositoryRoot = findRepositoryRoot();
        requireRepositoryInput(repositoryRoot.resolve("init-db/migrations/auth"));
        requireRepositoryInput(repositoryRoot.resolve("init-db/migrations/admin"));

        mysqlPassword = fixtureSecret();
        jwtSecret = fixtureSecret() + fixtureSecret();
        authRedisPassword = randomSecret();
        adminRedisPassword = randomSecret();
        redisAclFile = generateRedisAclFile();
        KeyPair delegationKeyPair = generateDelegationKeys();

        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("auth")
                .withUsername("root")
                .withPassword(mysqlPassword);
        redis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(redisAclFile),
                        "/usr/local/etc/redis/users.acl")
                .withCommand("redis-server", "--aclfile", "/usr/local/etc/redis/users.acl",
                        "--save", "", "--appendonly", "no")
                .waitingFor(Wait.forListeningPort());

        try {
            mysql.start();
            redis.start();
            deleteRedisAclFile();
            assertRedisAclContract();
            configureSystemProperties();
            prepareSchemasAndFixtures();
            startOwnerContexts(delegationKeyPair);
        } catch (Exception failure) {
            closeJourney();
            throw failure;
        }
    }


    @Test
    void bootsReadyOwnersReadsIdentityGrantsPermissionAndFailsClosedWithoutSigner() throws Exception {
        assertThat(ownerContexts.allReady()).isTrue();
        assertThat(ownerContexts.states())
                .containsEntry("auth", CoreOwnerContextManager.State.READY)
                .containsEntry("admin", CoreOwnerContextManager.State.READY);
        RedissonClient authRedisson = ownerContexts.bean("auth", RedissonClient.class);
        RBucket<String> redissonProbe = authRedisson.getBucket("auth:core-redisson-probe");
        redissonProbe.set("ok", 30, TimeUnit.SECONDS);
        assertThat(redissonProbe.get()).isEqualTo("ok");
        verifyHttpJourney();

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

            IdentityQueryService authIdentity = ownerContexts.bean("auth", IdentityQueryService.class);
            Set<String> accountIds = Set.of("core-user");
            assertThat(identity).isEqualTo(authIdentity.getIdentity("core-user"));
            assertThat(identityQuery.batchGetIdentity(accountIds))
                    .isEqualTo(authIdentity.batchGetIdentity(accountIds));
            assertThat(identityQuery.findActiveAccountIds())
                    .isEqualTo(authIdentity.findActiveAccountIds());
            assertThat(identityQuery.getIdentity("missing-core-user"))
                    .isEqualTo(authIdentity.getIdentity("missing-core-user"));
            assertThat(identityQuery.getIdentity("missing-core-user").success()).isFalse();

            AccountQueryService authAccounts = ownerContexts.bean("auth", AccountQueryService.class);
            assertThat(accountQuery.getAccountById("core-user").data().accountId()).isEqualTo("core-user");
            assertThat(accountQuery.getAccountById("core-user"))
                    .isEqualTo(authAccounts.getAccountById("core-user"));
            assertThat(accountQuery.getAccountByUsername("core-user"))
                    .isEqualTo(authAccounts.getAccountByUsername("core-user"));
            assertThat(accountQuery.getAccountByEmail("core-user@example.invalid"))
                    .isEqualTo(authAccounts.getAccountByEmail("core-user@example.invalid"));
            AccountQueryDTO query = new AccountQueryDTO(
                    "core-user", "USER", true, false, 1, 10, "username", "asc", true);
            assertThat(accountQuery.queryAccounts(query)).isEqualTo(authAccounts.queryAccounts(query));
            assertThat(accountQuery.queryAccounts(query).page().total()).isEqualTo(1L);
            assertThat(accountQuery.getAccountsByIds(accountIds))
                    .isEqualTo(authAccounts.getAccountsByIds(accountIds));
            assertThat(accountQuery.countAccountsByIdsExcludingUsernameMatch(accountIds, "not-core-user"))
                    .isEqualTo(authAccounts.countAccountsByIdsExcludingUsernameMatch(accountIds, "not-core-user"));
            assertThat(accountQuery.countAccountsByIdsExcludingUsernameMatch(accountIds, "not-core-user").data())
                    .isEqualTo(1L);
            assertThat(accountQuery.getDashboardStatsSummary()).isEqualTo(authAccounts.getDashboardStatsSummary());
            AuthUserTrendAggregateQuery trend = new AuthUserTrendAggregateQuery(
                    LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(2100, 1, 1, 0, 0), "year", 101);
            assertThat(accountQuery.getUserTrend(trend)).isEqualTo(authAccounts.getUserTrend(trend));
            assertThat(accountQuery.getUserTrend(trend).data()).isNotEmpty();
            assertThat(accountQuery.getAccountById("missing-core-user"))
                    .isEqualTo(authAccounts.getAccountById("missing-core-user"));
            assertThat(accountQuery.getAccountById("missing-core-user").success()).isFalse();
            assertThat(accountQuery.getUserTrend(null)).isEqualTo(authAccounts.getUserTrend(null));
            assertThat(accountQuery.getUserTrend(null).success()).isFalse();

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
            if (parentContext != null) {
                parentContext.close();
            }
            if (ownerContexts != null) {
                if (parentContext == null) {
                    ownerContexts.onContextClosed(new ContextClosedEvent(mock(ApplicationContext.class)));
                }
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
                        try {
                            SecurityContextHolder.clearContext();
                        } finally {
                            deleteRedisAclFile();
                        }
                    }
                }
            }
        }
    }

    private static void startOwnerContexts(KeyPair delegationKeyPair) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("AUTH_DB_URL", jdbcUrl("auth"))
                .withProperty("ADMIN_DB_URL", jdbcUrl("admin"))
                .withProperty("AUTH_DB_USER", "auth_rw")
                .withProperty("ADMIN_DB_USER", "admin_rw")
                .withProperty("AUTH_DB_PASSWORD", authOwnerPassword)
                .withProperty("ADMIN_DB_PASSWORD", adminOwnerPassword)
                .withProperty("REDIS_HOST", redis.getHost())
                .withProperty("REDIS_PORT", Integer.toString(redis.getMappedPort(6379)))
                .withProperty("REDIS_USERNAME", "ulticode-auth")
                .withProperty("REDIS_PASSWORD", authRedisPassword)
                .withProperty("AUTH_REDIS_USERNAME", "ulticode-auth")
                .withProperty("ADMIN_REDIS_USERNAME", "ulticode-admin")
                .withProperty("AUTH_REDIS_PASSWORD", authRedisPassword)
                .withProperty("ADMIN_REDIS_PASSWORD", adminRedisPassword)
                .withProperty("INTERNAL_DELEGATION_PRIVATE_KEY", encode(delegationKeyPair.getPrivate().getEncoded()))
                .withProperty("INTERNAL_DELEGATION_PUBLIC_KEY", encode(delegationKeyPair.getPublic().getEncoded()))
                .withProperty("INTERNAL_DELEGATION_KEY_ID", "core-disposable-" + fixtureSecret().substring(0, 16))
                .withProperty("INTERNAL_DELEGATION_ISSUER", "backend-admin")
                // Hermetic journey-only object-storage inputs: valid loopback S3
                // settings with the startup probe explicitly disabled, so this
                // gate never contacts RustFS. Production Core still requires
                // real APP_STORAGE_* values and keeps the probe enabled by default.
                .withProperty("APP_STORAGE_TYPE", "s3")
                .withProperty("APP_STORAGE_S3_ENDPOINT", "http://127.0.0.1:9000")
                .withProperty("APP_STORAGE_S3_REGION", "us-east-1")
                .withProperty("APP_STORAGE_S3_BUCKET", "core-enabled-owner-journey")
                .withProperty("APP_STORAGE_S3_ACCESS_KEY", "core-enabled-owner-access")
                .withProperty("APP_STORAGE_S3_SECRET_KEY", "core-enabled-owner-secret")
                .withProperty("RUSTFS_ADMIN_ACCESS_KEY", "core-enabled-admin-access")
                .withProperty("RUSTFS_ADMIN_SECRET_KEY", "core-enabled-admin-secret")
                .withProperty("APP_STORAGE_S3_TLS_ENABLED", "false")
                .withProperty("APP_STORAGE_S3_CA_CERTIFICATE", "")
                .withProperty("APP_STORAGE_S3_CONNECT_TIMEOUT_MS", "200")
                .withProperty("APP_STORAGE_S3_REQUEST_TIMEOUT_MS", "300")
                .withProperty("APP_STORAGE_S3_MAX_CONCURRENT_REQUESTS", "1")
                .withProperty("APP_STORAGE_STARTUP_PROBE_ENABLED", "false")
                .withProperty("APP_STORAGE_STARTUP_PROBE_ATTEMPTS", "1")
                .withProperty("APP_STORAGE_STARTUP_PROBE_DELAY_MS", "0")
                .withProperty("app.storage.s3.endpoint", "${APP_STORAGE_S3_ENDPOINT}")
                .withProperty("app.storage.s3.region", "${APP_STORAGE_S3_REGION}")
                .withProperty("app.storage.s3.bucket", "${APP_STORAGE_S3_BUCKET}")
                .withProperty("app.storage.s3.access-key", "${APP_STORAGE_S3_ACCESS_KEY}")
                .withProperty("app.storage.s3.secret-key", "${APP_STORAGE_S3_SECRET_KEY}")
                .withProperty("app.storage.s3.tls-enabled", "${APP_STORAGE_S3_TLS_ENABLED}")
                .withProperty("app.storage.startup-probe.enabled", "false")
                .withProperty("spring.main.lazy-initialization", "true")
                .withProperty("server.port", "0")
                .withProperty("core.owner-contexts.enabled", "true")
                .withProperty("core.judge.required", "false")
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("ulticode.app.inbox.enabled", "false")
                .withProperty("spring.autoconfigure.exclude",
                        "org.springframework.boot.micrometer.metrics.autoconfigure.system.SystemMetricsAutoConfiguration");
        environment.setActiveProfiles("test");
        parentContext = new SpringApplicationBuilder(CoreApplication.class)
                .environment(environment).web(WebApplicationType.SERVLET).run();
        ownerContexts = parentContext.getBean(CoreOwnerContextManager.class);
        int port = ((ServletWebServerApplicationContext) parentContext).getWebServer().getPort();
        httpRoot = URI.create("http://127.0.0.1:" + port);
        awaitOwnerStartup();
    }

    private record HttpSession(HttpClient client, CookieManager cookies) {
    }

    private static HttpSession login(String username, String password) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).build();
        String body = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(Map.of("username", username, "password", password));
        HttpResponse<String> response = send(client, "/auth/login", "POST", body, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body())
                .path("code").asInt(-1)).isZero();
        assertThat(cookies.getCookieStore().getCookies().stream()
                .anyMatch(cookie -> cookie.getName().equals("access_token"))).isTrue();
        return new HttpSession(client, cookies);
    }

    private static HttpResponse<String> send(HttpClient client, String path, String method,
                                             String body, String csrf) throws Exception {
        var builder = HttpRequest.newBuilder(httpRoot.resolve(path)).timeout(Duration.ofSeconds(15));
        if (csrf != null) {
            builder.header("X-CSRF-Token", csrf);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String csrf(HttpSession session) {
        return session.cookies().getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("csrf_token"))
                .findFirst().orElseThrow().getValue();
    }

    private static long directHttpPermissionRows() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("auth"), mysql.getUsername(), mysqlPassword);
             PreparedStatement query = connection.prepareStatement(
                     "SELECT COUNT(*) FROM user_permissions WHERE user_id = ? AND action = ? AND resource = ?")) {
            query.setString(1, "core-user");
            query.setString(2, "CREATE");
            query.setString(3, "PROBLEM");
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }

    private static void verifyHttpJourney() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        HttpSession user = login("core-user", userPassword);
        HttpSession admin = login("core-admin", adminPassword);
        HttpResponse<String> identity = send(user.client(), "/auth/me", "GET", null, null);
        assertThat(identity.statusCode()).isEqualTo(200);
        assertThat(json.readTree(identity.body()).at("/data/user/id").asText()).isEqualTo("core-user");
        assertThat(send(HttpClient.newHttpClient(), "/auth/me", "GET", null, null).statusCode()).isEqualTo(401);
        String grant = "{\"action\":\"CREATE\",\"resource\":\"PROBLEM\",\"expiresAt\":\"2099-01-01T00:00:00\"}";
        String endpoint = "/admin/users/core-user/permissions";
        assertThat(directHttpPermissionRows()).isZero();
        assertThat(send(user.client(), endpoint, "POST", grant, csrf(user)).statusCode()).isEqualTo(403);
        assertThat(send(admin.client(), endpoint, "POST", grant, null).statusCode()).isEqualTo(403);
        assertThat(directHttpPermissionRows()).isZero();
        HttpResponse<String> granted = send(admin.client(), endpoint, "POST", grant, csrf(admin));
        assertThat(granted.statusCode()).isEqualTo(200);
        assertThat(json.readTree(granted.body()).path("code").asInt(-1)).isZero();
        assertThat(json.readTree(granted.body()).at("/data/changed").asBoolean()).isTrue();
        assertThat(directHttpPermissionRows()).isEqualTo(1L);
        HttpSession refreshedUser = login("core-user", userPassword);
        HttpResponse<String> readback = send(refreshedUser.client(), "/auth/permissions", "GET", null, null);
        assertThat(readback.statusCode()).isEqualTo(200);
        assertThat(json.readTree(readback.body()).path("data").toString()).contains("CREATE:PROBLEM");
    }

    private static void prepareSchemasAndFixtures() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("auth"), mysql.getUsername(), mysqlPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `auth` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            statement.execute("CREATE DATABASE IF NOT EXISTS `admin` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            statement.execute("CREATE DATABASE IF NOT EXISTS `app` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            authOwnerPassword = provisionOwnerAccount(statement, "auth_rw", "auth");
            adminOwnerPassword = provisionOwnerAccount(statement, "admin_rw", "admin");
            provisionOwnerAccount(statement, "app_rw", "app");
        }
        applyMigrations("auth");
        applyMigrations("admin");
        try (Connection connection = DriverManager.getConnection(jdbcUrl("auth"), mysql.getUsername(), mysqlPassword);
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO users (id, username, email, password, role, is_active, is_banned,
                                        is_deleted, authz_version)
                     VALUES (?, ?, ?, ?, ?, 1, 0, 0, 0)
                     """)) {
            insert.setString(1, "core-user");
            insert.setString(2, "core-user");
            insert.setString(3, "core-user@example.invalid");
            userPassword = UUID.randomUUID().toString();
            adminPassword = UUID.randomUUID().toString();
            BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
            insert.setString(4, passwords.encode(userPassword));
            insert.setString(5, "USER");
            insert.executeUpdate();
            insert.setString(1, "core-admin");
            insert.setString(2, "core-admin");
            insert.setString(3, "core-admin@example.invalid");
            insert.setString(4, passwords.encode(adminPassword));
            insert.setString(5, "SUPER_ADMIN");
            insert.executeUpdate();
        }
    }

    private static String provisionOwnerAccount(Statement statement, String username, String schema)
            throws Exception {
        String password = randomSecret();
        String user = sqlLiteral(username);
        String escapedPassword = sqlLiteral(password);
        statement.execute("CREATE USER IF NOT EXISTS '" + user + "'@'%'");
        statement.execute("ALTER USER '" + user + "'@'%' IDENTIFIED BY '" + escapedPassword + "'");
        statement.execute("REVOKE ALL PRIVILEGES, GRANT OPTION FROM '" + user + "'@'%'");
        statement.execute("GRANT USAGE ON *.* TO '" + user + "'@'%'");
        statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `" + schema
                + "`.* TO '" + user + "'@'%'");
        return password;
    }

    private static String sqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private static String randomSecret() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    private static Path generateRedisAclFile() throws Exception {
        String temporaryDirectory = System.getenv("CORE_ENABLED_OWNER_JOURNEY_TMP_DIR");
        Path aclFile = temporaryDirectory == null || temporaryDirectory.isBlank()
                ? Files.createTempFile("ulticode-core-redis-", ".acl")
                : Files.createTempFile(
                        Path.of(temporaryDirectory), "ulticode-core-redis-", ".acl");
        Map<String, String> credentials = new HashMap<>();
        for (String owner : List.of(
                "AUTH", "ADMIN", "APP", "SUBMISSION", "SEARCH",
                "NOTIFICATION", "JUDGE", "OPS", "HEALTH")) {
            credentials.put(owner + "_REDIS_PASSWORD", randomSecret());
        }
        credentials.put("REDIS_REPLICATION_PASSWORD", randomSecret());
        credentials.put("REDIS_SENTINEL_PASSWORD", randomSecret());
        credentials.put("AUTH_REDIS_PASSWORD", authRedisPassword);
        credentials.put("ADMIN_REDIS_PASSWORD", adminRedisPassword);

        ProcessBuilder builder = new ProcessBuilder(
                "bash",
                repositoryRoot.resolve("docker/redis/generate-users-acl.sh").toString(),
                aclFile.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().putAll(credentials);
        Process process = builder.start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            Files.deleteIfExists(aclFile);
            throw new IllegalStateException("Redis ACL generator timed out");
        }
        if (process.exitValue() != 0) {
            Files.deleteIfExists(aclFile);
            throw new IllegalStateException("Redis ACL generator failed");
        }
        return aclFile;
    }

    private static void assertRedisAclContract() throws Exception {
        assertRedisOutput("ulticode-auth", authRedisPassword, "PONG", "PING");
        assertRedisOutput("ulticode-admin", adminRedisPassword, "PONG", "PING");
        assertRedisOutput("default", authRedisPassword, "WRONGPASS", "PING");
        assertRedisOutput(
                "ulticode-auth", authRedisPassword, "OK",
                "SET", "auth:core-contract-probe", "ok", "EX", "30");
        assertRedisOutput(
                "ulticode-admin", adminRedisPassword, "OK",
                "SET", "contest:core-contract-probe", "ok", "EX", "30");
        assertRedisOutput(
                "ulticode-auth", authRedisPassword, "NOPERM",
                "SET", "contest:core-contract-probe", "forbidden");
        assertRedisOutput(
                "ulticode-admin", adminRedisPassword, "NOPERM",
                "SET", "auth:core-contract-probe", "forbidden");
    }

    private static void assertRedisOutput(
            String username, String password, String expected, String... command) throws Exception {
        String[] redisCommand = new String[5 + command.length];
        redisCommand[0] = "redis-cli";
        redisCommand[1] = "--user";
        redisCommand[2] = username;
        redisCommand[3] = "-a";
        redisCommand[4] = password;
        System.arraycopy(command, 0, redisCommand, 5, command.length);
        Container.ExecResult result = redis.execInContainer(redisCommand);
        String output = result.getStdout() + result.getStderr();
        assertThat(output).as("Redis ACL command failed for " + username)
                .contains(expected);
    }

    private static void deleteRedisAclFile() {
        Path aclFile = redisAclFile;
        if (aclFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(aclFile);
            redisAclFile = null;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Redis ACL cleanup failed", failure);
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
        setSystemProperty("REDIS_USERNAME", "ulticode-auth");
        setSystemProperty("REDIS_PASSWORD", authRedisPassword);
        setSystemProperty("AUTH_REDIS_USERNAME", "ulticode-auth");
        setSystemProperty("ADMIN_REDIS_USERNAME", "ulticode-admin");
        setSystemProperty("AUTH_REDIS_PASSWORD", authRedisPassword);
        setSystemProperty("ADMIN_REDIS_PASSWORD", adminRedisPassword);
        setSystemProperty("AUTH_REDIS_URL", redisUrl("ulticode-auth", authRedisPassword));
        setSystemProperty("ADMIN_REDIS_URL", redisUrl("ulticode-admin", adminRedisPassword));
        setSystemProperty("APP_STORAGE_TYPE", "s3");
        setSystemProperty("APP_STORAGE_S3_ENDPOINT", "http://127.0.0.1:9000");
        setSystemProperty("APP_STORAGE_S3_REGION", "us-east-1");
        setSystemProperty("APP_STORAGE_S3_BUCKET", "core-enabled-owner-journey");
        setSystemProperty("APP_STORAGE_S3_ACCESS_KEY", "core-enabled-owner-access");
        setSystemProperty("APP_STORAGE_S3_SECRET_KEY", "core-enabled-owner-secret");
        setSystemProperty("RUSTFS_ADMIN_ACCESS_KEY", "core-enabled-admin-access");
        setSystemProperty("RUSTFS_ADMIN_SECRET_KEY", "core-enabled-admin-secret");
        setSystemProperty("APP_STORAGE_S3_TLS_ENABLED", "false");
        setSystemProperty("APP_STORAGE_S3_CA_CERTIFICATE", "");
        setSystemProperty("APP_STORAGE_S3_CONNECT_TIMEOUT_MS", "200");
        setSystemProperty("APP_STORAGE_S3_REQUEST_TIMEOUT_MS", "300");
        setSystemProperty("APP_STORAGE_S3_MAX_CONCURRENT_REQUESTS", "1");
        setSystemProperty("APP_STORAGE_STARTUP_PROBE_ENABLED", "false");
        setSystemProperty("APP_STORAGE_STARTUP_PROBE_ATTEMPTS", "1");
        setSystemProperty("APP_STORAGE_STARTUP_PROBE_DELAY_MS", "0");
        setSystemProperty("JWT_SECRET", jwtSecret);
        setSystemProperty("JWT_RSA_ENABLED", "false");
        setSystemProperty("JWT_COOKIE_SECURE", "false");
        setSystemProperty("AUTH_AUDIT_OUTBOX_DISPATCHER_ENABLED", "false");
        setSystemProperty("AUTH_SEARCH_OUTBOX_DISPATCHER_ENABLED", "false");
        setSystemProperty("ADMIN_AUDIT_INBOX_ENABLED", "false");
        setSystemProperty("MANAGEMENT_TRACING_SAMPLING_PROBABILITY", "0");
    }

    private static void awaitOwnerStartup() {
        try {
            // Match the runtime owner budget and allow each sequential attempt its fresh drain budget.
            long startupBudgetMs = 2L * OWNER_STARTUP_TIMEOUT_MS
                    * new CoreModuleRegistry().enabledModules().size();
            ownerContexts.startupCompletion().toCompletableFuture().get(startupBudgetMs, TimeUnit.MILLISECONDS);
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

    private static String redisUrl(String username, String password) {
        return "redis://" + username + ":" + password + "@" + redis.getHost() + ":"
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
}
