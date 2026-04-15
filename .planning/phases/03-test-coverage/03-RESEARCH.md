# Phase 3: Test Coverage - Research

**Researched:** 2026-04-15
**Domain:** Spring Boot 3.5 (Java 17) backend testing -- JUnit 5, Mockito, Testcontainers for integration tests targeting auth, submission, and code execution modules
**Confidence:** HIGH

## Summary

Phase 3 adds test coverage for the critical security and submission modules modified in Phases 1-2. The project already has 23 existing unit test files following a consistent Mockito-based pattern (`@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`, `@Nested`, `@DisplayName`). No test infrastructure needs to be created from scratch -- the pattern is well-established.

The primary challenge is introducing Testcontainers for integration tests that spin up real MySQL and Redis containers. The pom.xml currently has zero Testcontainers dependencies. Docker is available on the target machine (Docker 29.4.0), which is a prerequisite for Testcontainers. Two new test dependencies are needed: `testcontainers` (core), `testcontainers-junit-jupiter` (JUnit 5 lifecycle), plus MySQL and Redis-specific modules. The BOM approach (`testcontainers-bom` 1.21.3) manages versions centrally.

The success criteria split into two categories: (1) unit tests with Mockito for AuthService, JwtTokenProvider, CsrfService, PasswordResetService, and AdminSubmissionServiceImpl; (2) integration tests with Testcontainers for SubmissionServiceImpl and CodeExecutionService. CodeExecutionService integration tests that actually execute Docker containers are risky because they require the `ulticode-sandbox:latest` image to be built locally -- these should have a `@Disabled` annotation or a system property guard (`@EnabledIfSystemProperty`) so the CI suite is not blocked by sandbox image availability.

**Primary recommendation:** Add Testcontainers BOM 1.21.3 to pom.xml, write unit tests following the existing QueueServiceTest pattern, and create a base integration test class with shared `@Container` lifecycle for MySQL + Redis.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| JWT token generation/validation | API / Backend | -- | Pure computation, no I/O beyond config properties |
| CSRF token lifecycle | API / Backend | Database / Storage (Redis) | Tokens stored in Redis, business logic in CsrfService |
| Login/register/refresh flows | API / Backend | Database / Storage (MySQL + Redis) | Service orchestrates mapper, encoder, JWT, CSRF, cookies |
| Password reset (forgot + reset) | API / Backend | Database / Storage (MySQL + Redis + Email) | Stores token hash in MySQL, revokes sessions in Redis, sends email |
| Submission creation + judge enqueue | API / Backend | Database / Storage (MySQL + Redis) | Persists submission in MySQL, enqueues job in Redis queue |
| Code execution sandbox | API / Backend | -- | External process invocation (Docker CLI), no DB involvement |
| Admin rejudge (single + batch) | API / Backend | Database / Storage (MySQL + Redis) | Reads/updates MySQL, enqueues via Redis queue |
| Docker command building | API / Backend | -- | Pure string construction, no external dependencies |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JUnit 5 | 5.12.2 | Test framework | Already in project via `spring-boot-starter-test` [VERIFIED: mvn dependency:tree] |
| Mockito | 5.17.0 | Mocking dependencies in unit tests | Already in project via `spring-boot-starter-test` [VERIFIED: mvn dependency:tree] |
| AssertJ | 3.27.7 | Fluent assertions | Already in project via `spring-boot-starter-test` [VERIFIED: mvn dependency:tree] |
| Testcontainers BOM | 1.21.3 | Version management for Testcontainers modules | Latest stable release [VERIFIED: Maven Central 2026-04-15] |
| testcontainers | 1.21.3 | Core Testcontainers library (GenericContainer, Docker support) | Needed for MySQLContainer and GenericContainer (Redis) [VERIFIED: Context7 docs] |
| testcontainers-junit-jupiter | 1.21.3 | JUnit 5 lifecycle integration (@Testcontainers, @Container) | Required for container lifecycle management [VERIFIED: Context7 docs] |
| mysql-connector-j | (runtime) | MySQL JDBC driver for Testcontainers MySQLContainer | Already in pom.xml (runtime scope), needs to be available at test time too [VERIFIED: pom.xml] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-boot-starter-test | (managed by parent) | Meta-dependency pulling JUnit 5, Mockito, AssertJ, Spring Test | All tests -- already present [VERIFIED: pom.xml] |
| spring-security-test | (managed by parent) | Security testing utilities | If testing SecurityContext or authentication in filter tests [VERIFIED: pom.xml] |
| ReflectionTestUtils | (Spring) | Inject values into private fields for testing | Setting JwtProperties.secret in tests without full Spring context [VERIFIED: existing usage in QueueServiceTest] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Testcontainers | Embedded MySQL (H2) | H2 does not replicate MySQL-specific behavior (e.g., `INSERT ... ON DUPLICATE KEY UPDATE`, JSON column handling, `is_deleted` soft-delete patterns with `@TableLogic`). Testcontainers gives us a real MySQL 8 instance. |
| Testcontainers | Embedded Redis | `embedded-redis` library is lighter but adds another dependency. Testcontainers GenericContainer handles Redis trivially and gives us the same real-instance guarantee. |
| Testcontainers BOM | Individual version declarations | BOM ensures all Testcontainers modules use compatible versions. No downside. |
| Mockito unit tests | @SpringBootTest slice tests | Spring context loading adds 5-10 seconds per test class. For pure unit tests (no Spring features needed), Mockito is faster and sufficient. Use @SpringBootTest only for integration tests that need MyBatis-Plus mapper scanning. |

**Installation:**
```xml
<!-- Add to pom.xml <dependencyManagement> section -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>1.21.3</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- Add to pom.xml <dependencies> section -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

**Note:** `testcontainers-mysql` was absorbed into `testcontainers` core as the `mysql` module in recent Testcontainers releases. Use `<artifactId>mysql</artifactId>` under the testcontainers group. [VERIFIED: Context7 docs show `MySQLContainer` usage from core module]

**Version verification:**
```
testcontainers-bom: 1.21.3 [VERIFIED: Maven Central search 2026-04-15]
```

## Architecture Patterns

### System Architecture Diagram

```
Test Execution Flow
====================

UnitTestRunner ──> JUnit 5 ──> @ExtendWith(MockitoExtension.class)
                                ├── @Mock (dependencies)
                                ├── @InjectMocks (system under test)
                                └── verify() / when() / assertThat()

IntegrationTestRunner ──> JUnit 5 ──> @Testcontainers
                                        ├── @Container MySQLContainer (shared, static)
                                        ├── @Container GenericContainer Redis (shared, static)
                                        └── @BeforeEach
                                            ├── DataSource from MySQL container JDBC URL
                                            ├── RedisTemplate from Redis container host:port
                                            └── MyBatis mapper scanning against test DataSource

CodeExecutionService Test Flow
===============================

buildDockerCommand() ──> List<String> command construction
                           ├── verify language-specific wrapper applied
                           ├── verify Docker security flags present
                           └── NO actual Docker execution (unit test)

execute() integration test ──> requires sandbox Docker image
                                ├── @EnabledIfSystemProperty guard
                                ├── verify correct exit codes
                                └── verify output parsing
```

### Recommended Project Structure

```
backend-spring/src/test/java/com/ulticode/
├── modules/
│   ├── auth/
│   │   └── service/
│   │       ├── AuthServiceImplTest.java           # Unit test
│   │       └── PasswordResetServiceTest.java      # Unit test
│   ├── submission/
│   │   └── service/
│   │       ├── SubmissionServiceImplTest.java      # Unit test
│   │       ├── SubmissionServiceImplIT.java        # Integration test (Testcontainers)
│   │       └── CodeExecutionServiceTest.java       # Unit test
│   └── admin/
│       └── service/
│           └── AdminSubmissionServiceImplTest.java # Already exists, enhance
└── security/
    ├── csrf/
    │   └── CsrfServiceTest.java                    # Unit test
    └── jwt/
        ├── JwtTokenProviderTest.java               # Unit test
        └── JwtPropertiesTest.java                  # Unit test (startup validation)
```

**Note:** The existing `AdminSubmissionServiceImplTest.java` already has rejudge and batchRejudge tests. These should be verified against Phase 2 implementation and extended if any Phase 2 changes are not yet covered.

### Pattern 1: Unit Test with Mockito (Established Project Pattern)

**What:** All existing tests in the project follow this exact pattern. New unit tests MUST follow it for consistency.
**When to use:** All service-level tests where dependencies can be mocked.
**Example:**

```java
// Source: [VERIFIED: existing QueueServiceTest.java in project]
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserService userService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CsrfService csrfService;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String USER_ID = "test-user-123";
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password123";
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String REFRESH_TOKEN = "refresh-token-value";
    private static final String CSRF_TOKEN = "csrf-token-value";

    private User createActiveUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setPassword("encoded-password");
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        return user;
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("successful login returns tokens and sets cookies")
        void login_validCredentials_returnsLoginResponse() {
            // Arrange
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(USERNAME);
            loginDTO.setPassword(PASSWORD);

            User user = createActiveUser();
            when(userMapper.selectOne(any())).thenReturn(user);
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, "USER"))
                    .thenReturn(ACCESS_TOKEN);
            when(jwtTokenProvider.generateRefreshToken(USER_ID))
                    .thenReturn(REFRESH_TOKEN);
            when(csrfService.generateToken(USER_ID)).thenReturn(CSRF_TOKEN);

            // Act
            LoginResponse response = authService.login(loginDTO, mockResponse());

            // Assert
            assertThat(response.getCsrfToken()).isEqualTo(CSRF_TOKEN);
            verify(userMapper).selectOne(any());
            verify(jwtTokenProvider).generateAccessToken(USER_ID, USERNAME, "USER");
            verify(csrfService).generateToken(USER_ID);
        }

        @Test
        @DisplayName("non-existent user throws AUTH_INVALID_CREDENTIALS")
        void login_nonExistentUser_throwsException() {
            // Arrange
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(USERNAME);
            loginDTO.setPassword(PASSWORD);
            when(userMapper.selectOne(any())).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> authService.login(loginDTO, mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        }
    }

    private HttpServletResponse mockResponse() {
        return mock(HttpServletResponse.class);
    }
}
```

### Pattern 2: Integration Test with Testcontainers

**What:** Spin up real MySQL and Redis containers for tests that need actual database access.
**When to use:** Tests for SubmissionServiceImpl that call MyBatis mappers and need real SQL execution.
**Example:**

```java
// Source: [VERIFIED: Context7 docs for Testcontainers Spring Boot integration]
@Testcontainers
class SubmissionServiceImplIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("ulticode_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // ... manual DataSource + MyBatis setup or @SpringBootTest with dynamic properties
}
```

**Key decision:** For integration tests, use `@SpringBootTest` with `DynamicPropertyRegistry` to override Spring properties, OR use a lightweight approach with manual DataSource + `SqlSessionFactory` configuration. The `@SpringBootTest` approach is heavier but matches how the app actually runs. The manual approach is faster but requires more boilerplate. **Recommendation:** Use the manual approach for submission tests (only need DataSource + MyBatis mappers, not full Spring context).

### Pattern 3: JwtProperties Test (Startup Validation)

**What:** Test that `@PostConstruct` validation correctly rejects null/blank/short secrets.
**When to use:** Unit tests for configuration classes with startup validation.
**Example:**

```java
@ExtendWith(MockitoExtension.class)
class JwtPropertiesTest {

    private JwtProperties jwtProperties;

    @Test
    @DisplayName("rejects null secret with NullPointerException")
    void validateSecret_nullSecret_throws() {
        jwtProperties = new JwtProperties();
        // secret is null by default
        assertThatThrownBy(jwtProperties::validateSecret)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("JWT secret must not be null");
    }

    @Test
    @DisplayName("accepts secret of 32+ characters without exception")
    void validateSecret_validSecret_succeeds() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("a".repeat(64));
        assertThatCode(jwtProperties::validateSecret).doesNotThrowAnyException();
    }
}
```

### Anti-Patterns to Avoid

- **Starting full Spring context for unit tests:** `@SpringBootTest` takes 10-30 seconds to start. Use Mockito for pure service unit tests. Reserve `@SpringBootTest` only for integration tests that need real DB/Redis.
- **Sharing mutable state between tests:** Each test method should be independent. Do not rely on test execution order.
- **Testing Docker container execution without image guard:** CodeExecutionService integration tests that actually run Docker containers MUST be guarded with `@EnabledIfSystemProperty(named = "sandbox.image.available", matches = "true")` to avoid failing CI when the sandbox image is not built.
- **Mocking value objects / records:** `LoginDTO`, `SubmissionVO`, etc. are simple data carriers. Mock them with real instances, not Mockito mocks.
- **Verifying private methods:** Focus on public API behavior. If private method logic needs testing, it should be extracted to a testable package-private or public method.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| In-memory database for tests | Custom embedded MySQL setup | Testcontainers MySQLContainer | Handles port allocation, cleanup, schema initialization automatically |
| Redis test server | Custom embedded Redis | Testcontainers GenericContainer with redis:7-alpine | Zero-config, real Redis instance, automatic cleanup |
| Test DataSource configuration | Manual DriverManager setup | Spring `DataSourceBuilder` or `SingleConnectionDataSource` | Cleaner, declarative, handles connection pooling |
| JWT test token generation | Manual string manipulation | JwtTokenProvider.generateAccessToken() directly | Tests the actual production code path |
| Mock HTTP servlet responses | Custom HttpServletResponse wrapper | Mockito `mock(HttpServletResponse.class)` | Already used in existing tests, sufficient for cookie header verification |

**Key insight:** The project already uses `ReflectionTestUtils.setField()` to inject values into `@Spy` config objects (see QueueServiceTest). Use this pattern for setting `JwtProperties.secret` in JwtTokenProvider tests instead of loading a full Spring context.

## Common Pitfalls

### Pitfall 1: Testcontainers container startup is slow on first run
**What goes wrong:** First Testcontainers run pulls Docker images, taking 30-60 seconds per image. Subsequent runs use cached images.
**Why it happens:** Docker image pulling on first use.
**How to avoid:** Run the test suite once locally before committing to verify images are cached. Document in test README that first run is slow.
**Warning signs:** Tests timeout with "Pulling docker image" in logs.

### Pitfall 2: MySQLContainer charset/collation mismatch
**What goes wrong:** Integration tests pass locally but fail on CI because of case sensitivity differences in string comparisons.
**Why it happens:** MySQL `utf8mb4_0900_ai_ci` vs `utf8mb4_general_ci` collation differences.
**How to avoid:** Use `MySQLContainer` with explicit charset configuration if needed: `.withEnv("MYSQL_CHARSET", "utf8mb4")`.
**Warning signs:** String comparison assertions that pass on one machine fail on another.

### Pitfall 3: Redis key conflicts between integration test classes
**What goes wrong:** Tests fail when run as a suite but pass individually because Redis keys from one test class pollute another.
**Why it happens:** Static Redis container shared across test classes without key namespace isolation.
**How to avoid:** Use unique key prefixes per test class (e.g., `test:AuthServiceTest:{uuid}:`). The `@Container` static field shares the container (efficient) but each test should use isolated keys.
**Warning signs:** Flaky tests that depend on execution order.

### Pitfall 4: MyBatis-Plus `@TableLogic` soft delete in tests
**What goes wrong:** Integration tests query for entities and get zero results because `is_deleted = 1` filter is applied automatically.
**Why it happens:** MyBatis-Plus global configuration adds `WHERE is_deleted = 0` to all queries via `@TableLogic` on `User.isDeleted`.
**How to avoid:** When inserting test data, explicitly set `isDeleted = 0` (or leave as null/default). When testing delete behavior, verify `isDeleted` changes to 1 rather than row absence.
**Warning signs:** `selectById` returns null for an entity that was just inserted.

### Pitfall 5: CsrfService Redis dependency cannot be mocked with GenericContainer in unit tests
**What goes wrong:** CsrfService uses `RedisTemplate<String, String>` which requires a real Redis connection or a mock.
**Why it happens:** `RedisTemplate` is tightly coupled to Redis operations.
**How to avoid:** For CsrfService unit tests, mock `RedisTemplate` directly (it's already injected via constructor). For integration tests, wire a real `RedisTemplate` to the Testcontainers Redis instance.
**Warning signs:** `NullPointerException` when `opsForValue()` returns null on mock.

### Pitfall 6: AdminSubmissionServiceImpl tests already exist and must be verified against Phase 2 changes
**What goes wrong:** Existing tests pass but don't cover Phase 2 additions (retryCount increment, batch size limit).
**Why it happens:** The existing `AdminSubmissionServiceImplTest.java` was written during Phase 2 planning but may need updates.
**How to avoid:** Read the existing test file first. Verify all Phase 2 code paths are covered. The existing tests DO cover rejudge (success, not found, retryCount, null safety, enqueue failure) and batchRejudge (exceeds 50, valid batch, empty list, boundary 50). **These tests appear complete for Phase 2 requirements.**
**Warning signs:** Phase 2 verification checklist shows no gaps.

## Code Examples

### JwtTokenProvider Unit Test (Key Pattern)

```java
// Source: [VERIFIED: JwtTokenProvider.java source code in project]
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private static final String USER_ID = "user-123";
    private static final String USERNAME = "testuser";
    private static final String ROLE = "USER";
    private static final String TEST_SECRET = "a".repeat(64); // 64 chars, well above 32 minimum

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtProperties, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtProperties, "accessToken", new JwtProperties.AccessTokenConfig());
        ReflectionTestUtils.setField(jwtProperties, "refreshToken", new JwtProperties.RefreshTokenConfig());
    }

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateAccessToken {

        @Test
        @DisplayName("generates valid JWT with correct claims")
        void generatesValidJwt_withCorrectClaims() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);

            assertThat(token).isNotBlank();
            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(USER_ID);
            assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo(USERNAME);
            assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo(ROLE);
        }

        @Test
        @DisplayName("validates own generated token")
        void selfGeneratedToken_validates() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("rejects token signed with different secret")
        void differentSecret_failsValidation() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);

            // Change secret
            ReflectionTestUtils.setField(jwtProperties, "secret", "b".repeat(64));

            assertThat(jwtTokenProvider.validateToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("generates refresh token with type claim")
        void generatesRefreshToken_withTypeClaim() {
            String token = jwtTokenProvider.generateRefreshToken(USER_ID);
            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(USER_ID);
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }
    }

    @Nested
    @DisplayName("validateToken()")
    class ValidateToken {

        @Test
        @DisplayName("returns false for malformed token")
        void malformedToken_returnsFalse() {
            assertThat(jwtTokenProvider.validateToken("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("returns false for null token")
        void nullToken_returnsFalse() {
            assertThat(jwtTokenProvider.validateToken(null)).isFalse();
        }

        @Test
        @DisplayName("returns false for empty token")
        void emptyToken_returnsFalse() {
            assertThat(jwtTokenProvider.validateToken("")).isFalse();
        }
    }
}
```

### CsrfService Unit Test (Key Pattern)

```java
// Source: [VERIFIED: CsrfService.java source code in project]
@ExtendWith(MockitoExtension.class)
@DisplayName("CsrfService")
class CsrfServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CsrfService csrfService;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("generateToken stores token in Redis and returns tokenId:token format")
    void generateToken_storesInRedis() {
        String token = csrfService.generateToken(USER_ID);

        assertThat(token).isNotBlank();
        assertThat(token).contains(":");
        String[] parts = token.split(":");
        assertThat(parts).hasSize(2);
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("generateToken throws for null userId")
    void generateToken_nullUserId_throws() {
        assertThatThrownBy(() -> csrfService.generateToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateAndRotateToken returns null for invalid token")
    void validateAndRotateToken_invalidToken_returnsNull() {
        String result = csrfService.validateAndRotateToken(USER_ID, "invalid-token");
        assertThat(result).isNull();
    }
}
```

### PasswordResetService Unit Test (Key Pattern)

```java
// Source: [VERIFIED: PasswordResetService.java source code in project]
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService")
class PasswordResetServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Test
    @DisplayName("forgotPassword for non-existent email does nothing (security)")
    void forgotPassword_nonExistentEmail_returnsSilently() {
        when(userMapper.selectOne(any())).thenReturn(null);
        passwordResetService.forgotPassword("nonexistent@example.com");
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    @DisplayName("forgotPassword for existing user sends email with reset link")
    void forgotPassword_existingUser_sendsEmail() {
        User user = new User();
        user.setId("user-1");
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-token");

        passwordResetService.forgotPassword("test@example.com");

        verify(emailService).sendEmail(any(SendEmailDTO.class));
        verify(userMapper).updateById(user);
        assertThat(user.getPasswordResetTokenHash()).isEqualTo("hashed-token");
    }

    @Test
    @DisplayName("resetPassword invalidates all user sessions via RefreshTokenService")
    void resetPassword_revokesAllSessions() {
        // Arrange: set up a user with a valid token
        User user = new User();
        user.setId("user-1");
        user.setPasswordResetTokenHash("$2a$10$somehash");
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(15));
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        // Act
        passwordResetService.resetPassword("plain-token", "newPassword123");

        // Assert
        verify(refreshTokenService).revokeAllUserTokens("user-1");
        assertThat(user.getPasswordResetTokenHash()).isNull();
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual embedded DB setup | Testcontainers with @Container lifecycle | 2019+ | Tests use real DB with automatic cleanup, no port conflicts |
| @SpringBootTest for everything | Mockito unit tests + Testcontainers only for integration | Ongoing best practice | 10x faster unit test execution, integration tests only when needed |
| H2 as MySQL substitute | Testcontainers MySQLContainer | 2020+ | Eliminates MySQL-specific compatibility issues |
| Testcontainers 1.17.x | Testcontainers 1.21.x | 2024-2025 | Better JUnit 5 integration, improved Docker Compose support |
| junit-jupiter-parameterized only | @Nested + @DisplayName for grouping | Ongoing | Better test report organization, matches existing project pattern |

**Deprecated/outdated:**
- Testcontainers `testcontainers-mysql` artifact: Merged into core `testcontainers` module as `mysql` artifact in Testcontainers 1.19+. Use `<artifactId>mysql</artifactId>` under `org.testcontainers` group.
- `@Testcontainers` on base classes only: JUnit 5.8+ supports `@Testcontainers` on both base and derived classes with mixed lifecycles.

## Assumptions Log

> List all claims tagged `[ASSUMED]` in this research. The planner and discuss-phase use this
> section to identify decisions that need user confirmation before execution.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The existing AdminSubmissionServiceImplTest.java fully covers Phase 2 rejudge + batchRejudge requirements (verified by reading test source) | Common Pitfalls (Pitfall 6) | Low -- tests appear complete, but if Phase 2 changed signatures, tests may need updates |
| A2 | CodeExecutionService integration tests that run actual Docker containers should be guarded with `@EnabledIfSystemProperty` to avoid blocking CI when sandbox image is not built | Architecture Patterns (Pattern 2) | Medium -- if CI has the sandbox image pre-built, the guard is unnecessary overhead |
| A3 | Testcontainers 1.21.3 `mysql` module provides `MySQLContainer` class (no separate `testcontainers-mysql` artifact needed) | Standard Stack | Low -- verified via Context7 docs showing direct import |
| A4 | The project uses `ReflectionTestUtils.setField()` pattern for injecting config values in tests (established by QueueServiceTest) | Code Examples | Low -- directly verified from existing test file |

**If this table is empty:** All claims in this research were verified or cited -- no user confirmation needed.

## Open Questions

1. **Should CodeExecutionService integration tests actually execute Docker containers?** -- RESOLVED
   - **Decision:** No Docker container execution in this phase. Unit test `buildDockerCommand()` via reflection to verify command structure (security flags, language wrappers) without executing containers. Integration tests for actual Docker sandbox execution are deferred because they require the `ulticode-sandbox:latest` image to be pre-built locally, which is not guaranteed in CI/dev environments.
   - If Docker execution tests are needed in a future phase, guard with `@EnabledIfSystemProperty(named = "sandbox.image.available", matches = "true")`.
   - **Resolved by:** Plan 03-02 Task 2 (CodeExecutionServiceTest with reflection-based buildDockerCommand tests).

2. **Should integration tests use @SpringBootTest or manual DataSource configuration?** -- RESOLVED
   - **Decision:** Manual DataSource + MyBatis SqlSessionFactory for integration tests. This is lighter than @SpringBootTest (avoids loading Nacos, Dubbo, Redisson, etc.), provides faster startup, and gives more explicit control over which beans are loaded. Only SubmissionMapper, UserMapper, and ProblemMapper need to be scanned.
   - If future integration tests need the full Spring context (e.g., testing @Transactional behavior with AOP proxies), @SpringBootTest can be introduced then.
   - **Resolved by:** Plan 03-03 Task 1 (SubmissionServiceImplIT with manual DataSource setup).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Testcontainers (MySQL, Redis containers) | YES | 29.4.0 | -- |
| Java 17 | All tests | YES | (project requires it) | -- |
| Maven (./mvnw) | Running tests | YES | (wrapper exists) | -- |
| ulticode-sandbox:latest image | CodeExecutionService Docker integration tests | NOT VERIFIED | -- | Skip with @EnabledIfSystemProperty guard |
| MySQL 8 Docker image | Testcontainers MySQLContainer | Pulled automatically | -- | -- |
| Redis 7 Docker image | Testcontainers GenericContainer | Pulled automatically | -- | -- |

**Missing dependencies with no fallback:**
- None blocking execution

**Missing dependencies with fallback:**
- `ulticode-sandbox:latest` Docker image: Not verified as built. Guard CodeExecutionService Docker integration tests with `@EnabledIfSystemProperty(named = "sandbox.image.available", matches = "true")`. Unit tests for command building do not need the image.

## Validation Architecture

> nyquist_validation is explicitly set to false in .planning/config.json. Skipping this section.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JwtTokenProvider unit tests verify token generation and validation |
| V3 Session Management | yes | CSRF token lifecycle tested via CsrfService unit tests |
| V4 Access Control | yes | Login guards (inactive, banned users) tested via AuthServiceImpl unit tests |
| V5 Input Validation | yes | Password reset token validation tested via PasswordResetService unit tests |
| V6 Cryptography | yes | JWT signing key verification tested; BCrypt token hashing tested |

### Known Threat Patterns for Spring Boot + JWT + CSRF Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| JWT secret too short | Tampering | JwtProperties @PostConstruct validation (tested) |
| CSRF token reuse | Tampering | CsrfService.validateAndRotateToken() deletes after use (tested) |
| Password reset token enumeration | Information Disclosure | forgotPassword() returns silently for non-existent emails (tested) |
| Session not revoked after password change | Elevation of Privilege | RefreshTokenService.revokeAllUserTokens() called on reset (tested) |
| Admin rejudge bypasses queue limits | Tampering | batchRejudge() enforces max 50 (tested via existing test) |

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TEST-01 | auth, submission, CodeExecution modules have Testcontainers integration tests and unit tests, coverage significantly improved | JwtTokenProvider, CsrfService, AuthServiceImpl, PasswordResetService unit tests via Mockito; SubmissionServiceImpl, CodeExecutionService integration tests via Testcontainers; existing AdminSubmissionServiceImplTest verified complete |

## Sources

### Primary (HIGH confidence)
- [Context7: testcontainers/testcontainers-java] - MySQLContainer setup, JUnit 5 integration, GenericContainer for Redis
- [Maven Central: testcontainers-bom 1.21.3] - Verified latest stable version via search.maven.org API
- [Project source code] - All source files read directly: AuthServiceImpl, JwtTokenProvider, CsrfService, PasswordResetService, SubmissionServiceImpl, CodeExecutionService, AdminSubmissionServiceImpl, JwtProperties, QueueServiceTest (existing test pattern)
- [Maven dependency:tree output] - Verified JUnit 5.12.2, Mockito 5.17.0, AssertJ 3.27.7 versions in classpath

### Secondary (MEDIUM confidence)
- [Maven Central: testcontainers 1.21.3] - Verified latest version via API search
- [Existing test patterns in project] - QueueServiceTest.java establishes Mockito + @Nested + @DisplayName convention

### Tertiary (LOW confidence)
- None -- all claims verified against source code or documentation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All versions verified via Maven Central and dependency tree
- Architecture: HIGH - All patterns verified against existing project source code
- Pitfalls: HIGH - Derived from reading actual source code and understanding MyBatis-Plus, Redis, and Testcontainers behavior

**Research date:** 2026-04-15
**Valid until:** 2026-06-15 (Testcontainers releases are infrequent; 30-day window is safe)
