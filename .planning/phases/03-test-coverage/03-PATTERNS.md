# Phase 3: Test Coverage - Pattern Map

**Mapped:** 2026-04-15
**Files analyzed:** 10
**Analogs found:** 9 / 10

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/test/.../auth/service/impl/AuthServiceImplTest.java` | test (unit) | request-response | `src/test/.../queue/service/QueueServiceTest.java` | exact |
| `src/test/.../security/jwt/JwtTokenProviderTest.java` | test (unit) | transform | `src/test/.../queue/service/QueueServiceTest.java` | role-match |
| `src/test/.../security/jwt/JwtPropertiesTest.java` | test (unit) | config-validation | `src/test/.../queue/constants/QueueConstantsTest.java` | role-match |
| `src/test/.../security/csrf/CsrfServiceTest.java` | test (unit) | request-response | `src/test/.../queue/service/QueueServiceTest.java` | role-match |
| `src/test/.../auth/service/PasswordResetServiceTest.java` | test (unit) | request-response | `src/test/.../email/service/EmailServiceTest.java` | role-match |
| `src/test/.../submission/service/impl/SubmissionServiceImplTest.java` | test (unit) | request-response | `src/test/.../admin/service/impl/AdminSubmissionServiceImplTest.java` | exact |
| `src/test/.../submission/service/impl/SubmissionServiceImplIT.java` | test (integration) | CRUD | No existing analog | no-match |
| `src/test/.../submission/service/CodeExecutionServiceTest.java` | test (unit) | request-response | `src/test/.../admin/service/impl/AdminSubmissionServiceImplTest.java` | role-match |
| `backend-spring/pom.xml` | config | build | Existing pom.xml | exact |
| `src/test/.../admin/service/impl/AdminSubmissionServiceImplTest.java` | test (modify) | request-response | Itself | self |

## Pattern Assignments

### `backend-spring/pom.xml` (config, build)

**Analog:** `backend-spring/pom.xml` (itself -- modify existing)

**Existing test dependencies** (lines 147-153):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

**Add Testcontainers BOM** to `<dependencyManagement>` section:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>1.21.3</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**Add Testcontainers dependencies** to `<dependencies>` section:
```xml
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

---

### `src/test/.../auth/service/impl/AuthServiceImplTest.java` (test, unit, request-response)

**Analog:** `src/test/.../queue/service/QueueServiceTest.java` (lines 1-50, 52-68, 72-130)

**Canonical unit test header pattern** (lines 1-30):
```java
package com.ulticode.modules.auth.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.security.csrf.CsrfService;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
```

**Class declaration + field declarations** (QueueServiceTest lines 55-69):
```java
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
```

**Constants + helper method pattern** (AdminSubmissionServiceImplTest lines 43-57):
```java
    private static final String USER_ID = "test-user-123";
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password123";

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
```

**@Nested test group pattern** (AdminSubmissionServiceImplTest lines 60-85):
```java
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
                    .thenReturn("access-token-value");
            when(jwtTokenProvider.generateRefreshToken(USER_ID))
                    .thenReturn("refresh-token-value");
            when(csrfService.generateToken(USER_ID)).thenReturn("csrf-token-value");

            // Act
            LoginResponse response = authService.login(loginDTO, mockResponse());

            // Assert
            assertThat(response.getCsrfToken()).isEqualTo("csrf-token-value");
            verify(jwtTokenProvider).generateAccessToken(USER_ID, USERNAME, "USER");
            verify(csrfService).generateToken(USER_ID);
        }
    }
```

**Exception assertion pattern** (AdminSubmissionServiceImplTest lines 92-100):
```java
        @Test
        @DisplayName("non-existent user throws AUTH_INVALID_CREDENTIALS")
        void login_nonExistentUser_throwsException() {
            when(userMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> authService.login(loginDTO, mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        }
```

**Key test scenarios for AuthServiceImpl:**
- `login()` -- valid credentials, non-existent user, wrong password, inactive user, banned user, banned-but-expired
- `register()` -- valid registration, duplicate username, duplicate email, null password
- `refreshToken()` -- valid refresh, expired token, invalid token

---

### `src/test/.../security/jwt/JwtTokenProviderTest.java` (test, unit, transform)

**Analog:** `src/test/.../queue/service/QueueServiceTest.java` (lines 1-30, 55-70, 72-85)

**Header + field pattern** -- same canonical unit test structure as above.

**ReflectionTestUtils config injection pattern** (QueueServiceTest lines 72-77):
```java
    private static final String TEST_SECRET = "a".repeat(64);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtProperties, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtProperties, "accessToken", new JwtProperties.AccessTokenConfig());
        ReflectionTestUtils.setField(jwtProperties, "refreshToken", new JwtProperties.RefreshTokenConfig());
    }
```

**Assertion + verify pattern for token generation** (RESEARCH.md verified pattern):
```java
        @Test
        @DisplayName("generates valid JWT with correct claims")
        void generatesValidJwt_withCorrectClaims() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);

            assertThat(token).isNotBlank();
            assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(USER_ID);
            assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo(USERNAME);
            assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo(ROLE);
        }
```

**Validation for null/empty/malformed tokens** (RESEARCH.md verified pattern):
```java
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
```

**Secret rotation test pattern** (RESEARCH.md verified pattern):
```java
        @Test
        @DisplayName("rejects token signed with different secret")
        void differentSecret_failsValidation() {
            String token = jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, ROLE);
            // Change secret
            ReflectionTestUtils.setField(jwtProperties, "secret", "b".repeat(64));
            assertThat(jwtTokenProvider.validateToken(token)).isFalse();
        }
```

**Key test scenarios for JwtTokenProvider:**
- `generateAccessToken()` -- valid claims, self-validation, different secret rejection
- `generateRefreshToken()` -- type claim present, self-validation
- `validateToken()` -- valid token, malformed, null, empty, expired, different secret
- `getUserIdFromToken()`, `getUsernameFromToken()`, `getRoleFromToken()` -- extract correct claims
- `getExpirationDateFromToken()` -- correct expiration time

---

### `src/test/.../security/jwt/JwtPropertiesTest.java` (test, unit, config-validation)

**Analog:** `src/test/.../queue/constants/QueueConstantsTest.java` (lines 1-50)

**Constants test pattern -- no Mockito needed** (QueueConstantsTest lines 27-49):
```java
package com.ulticode.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtProperties")
class JwtPropertiesTest {

    private JwtProperties jwtProperties;

    @Nested
    @DisplayName("validateSecret()")
    class ValidateSecret {

        @Test
        @DisplayName("rejects null secret with NullPointerException")
        void nullSecret_throws() {
            jwtProperties = new JwtProperties();
            assertThatThrownBy(jwtProperties::validateSecret)
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("JWT secret must not be null");
        }

        @Test
        @DisplayName("accepts secret of 32+ characters without exception")
        void validSecret_succeeds() {
            jwtProperties = new JwtProperties();
            jwtProperties.setSecret("a".repeat(64));
            assertThatCode(jwtProperties::validateSecret).doesNotThrowAnyException();
        }
    }
}
```

**Key test scenarios for JwtProperties:**
- `validateSecret()` -- null secret (NullPointerException), blank secret (IllegalStateException), short secret (<32 chars, warns but does not throw), valid secret (32+ chars, no exception)
- Default expiration values -- accessToken 900000ms, refreshToken 604800000ms

---

### `src/test/.../security/csrf/CsrfServiceTest.java` (test, unit, request-response)

**Analog:** `src/test/.../queue/service/QueueServiceTest.java` (lines 1-30, 55-70, 72-85)

**Redis mock pattern** (derived from CsrfService source -- uses `RedisTemplate<String, String>`):
```java
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CsrfService csrfService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
```

**Key test scenarios for CsrfService:**
- `generateToken()` -- stores in Redis with correct TTL, returns `tokenId:token` format, throws for null userId, throws for empty userId
- `validateAndRotateToken()` -- valid token returns new token, invalid format returns null, non-existent tokenId returns null, wrong token value returns null, throws for null userId
- `clearUserTokens()` -- clears all keys matching pattern, throws for null/empty userId

---

### `src/test/.../auth/service/PasswordResetServiceTest.java` (test, unit, request-response)

**Analog:** `src/test/.../email/service/EmailServiceTest.java` (lines 1-50, 55-80)

**Service mock pattern** (EmailServiceTest lines 35-47):
```java
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
```

**Security silent-return pattern** (RESEARCH.md verified):
```java
        @Test
        @DisplayName("forgotPassword for non-existent email does nothing (security)")
        void forgotPassword_nonExistentEmail_returnsSilently() {
            when(userMapper.selectOne(any())).thenReturn(null);
            passwordResetService.forgotPassword("nonexistent@example.com");
            verify(emailService, never()).sendEmail(any());
        }
```

**Session revocation verification pattern** (RESEARCH.md verified):
```java
        @Test
        @DisplayName("resetPassword invalidates all user sessions via RefreshTokenService")
        void resetPassword_revokesAllSessions() {
            // ... arrange user with valid token ...
            passwordResetService.resetPassword("plain-token", "newPassword123");
            verify(refreshTokenService).revokeAllUserTokens("user-1");
            assertThat(user.getPasswordResetTokenHash()).isNull();
        }
```

**Key test scenarios for PasswordResetService:**
- `forgotPassword()` -- non-existent email (silent return), existing user (sends email, stores hash)
- `resetPassword()` -- valid token (resets password, revokes sessions, clears token hash), invalid token (throws), expired token (throws), non-matching token (throws)

---

### `src/test/.../submission/service/impl/SubmissionServiceImplTest.java` (test, unit, request-response)

**Analog:** `src/test/.../admin/service/impl/AdminSubmissionServiceImplTest.java` (lines 1-57)

**Manual constructor injection pattern** (AdminSubmissionServiceImplTest lines 60-66):
```java
    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private QueueService queueService;

    private SubmissionServiceImpl submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new SubmissionServiceImpl(
                submissionMapper, userMapper, problemMapper, queueService);
    }
```

**Helper method pattern** (AdminSubmissionServiceImplTest lines 68-78):
```java
    private Submission createValidSubmission() {
        Submission submission = new Submission();
        submission.setId("sub-123");
        submission.setProblemId(1L);
        submission.setUserId("user-456");
        submission.setLanguage("java");
        submission.setCode("public class Main {}");
        submission.setStatus("Accepted");
        submission.setRetryCount(0);
        return submission;
    }
```

**Validation exception pattern** (AdminSubmissionServiceImplTest lines 163-170):
```java
        @Test
        @DisplayName("rejects batch with more than 50 IDs")
        void batchRejudge_exceeds50_throwsValidationFailed() {
            List<String> ids = java.util.Collections.nCopies(51, "sub-id");
            assertThatThrownBy(() -> adminSubmissionService.batchRejudge(ids, false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    });
        }
```

**Key test scenarios for SubmissionServiceImpl (unit):**
- `submit()` -- valid submission (persists + enqueues), null userId (throws), empty code (throws), unsupported language (throws), problem not found (throws), rate limit check
- `getSubmissionHistory()` -- paginated results, empty results
- `getSubmissionDetail()` -- found, not found

---

### `src/test/.../submission/service/impl/SubmissionServiceImplIT.java` (test, integration, CRUD)

**Analog:** No existing integration test in the codebase. Use RESEARCH.md Pattern 2.

**Testcontainers integration test pattern** (from RESEARCH.md):
```java
package com.ulticode.modules.submission.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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

    @BeforeEach
    void setUp() {
        // Manual DataSource + SqlSessionFactory setup against test containers
        // OR @SpringBootTest with DynamicPropertyRegistry
    }
}
```

**Key test scenarios for SubmissionServiceImpl (integration):**
- `submit()` -- persist to real MySQL, verify entity fields, verify queue job in Redis
- `getSubmissionHistory()` -- real pagination against MySQL
- MyBatis-Plus `@TableLogic` soft delete behavior (pitfall 4 from RESEARCH.md)

---

### `src/test/.../submission/service/CodeExecutionServiceTest.java` (test, unit, request-response)

**Analog:** `src/test/.../admin/service/impl/AdminSubmissionServiceImplTest.java` (lines 1-57)

**Source file dependencies** (CodeExecutionService.java lines 1-20):
```java
// Dependencies to mock:
// - DockerSandboxConfig sandboxConfig
```

**Config mock pattern** (QueueServiceTest `@Spy` pattern on lines 65-67):
```java
    @Mock
    private DockerSandboxConfig sandboxConfig;

    @InjectMocks
    private CodeExecutionService codeExecutionService;
```

**Disabled sandbox exception pattern** (derived from CodeExecutionService.java line 49-51):
```java
        @Test
        @DisplayName("throws SANDBOX_ERROR when sandbox is disabled")
        void execute_sandboxDisabled_throws() {
            when(sandboxConfig.enabled()).thenReturn(false);
            // ... setup request ...
            assertThatThrownBy(() -> codeExecutionService.execute(request, 1L, "user-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SANDBOX_ERROR));
        }
```

**Key test scenarios for CodeExecutionService (unit):**
- `execute()` -- unsupported language (throws), empty test cases (returns empty result), sandbox disabled (throws)
- `buildDockerCommand()` -- verify correct Docker flags, language-specific wrapper, security flags
- Output parsing -- correct runtime extraction, correct verdict calculation
- Integration tests guarded with `@EnabledIfSystemProperty(named = "sandbox.image.available", matches = "true")`

---

### `src/test/.../admin/service/impl/AdminSubmissionServiceImplTest.java` (test, modify, request-response)

**Analog:** Itself -- already exists with 211 lines.

**Existing coverage** (verified complete):
- `rejudge()` -- existing submission enqueues job, non-existent returns not found, increments retryCount, null retryCount sets to 1, enqueue failure returns failed
- `batchRejudge()` -- rejects >50 IDs, valid batch returns counts, empty list returns zeros, boundary 50 accepted

**Action:** Read existing file first (already read). Verify all Phase 2 code paths are covered. Per RESEARCH.md Pitfall 6, existing tests appear complete for Phase 2 requirements. Only modify if gaps found.

---

## Shared Patterns

### Test Class Header (all unit tests)

**Source:** `backend-spring/src/test/java/com/ulticode/modules/queue/service/QueueServiceTest.java` (lines 1-30)

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
```

All unit tests use `@ExtendWith(MockitoExtension.class)`, `@Mock` for dependencies, `@InjectMocks` for the system under test.

### Assertion Style

**Source:** Two styles coexist in the project.

**AssertJ style** (preferred for new tests -- AdminSubmissionServiceImplTest):
```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

assertThat(result.getSuccess()).isTrue();
assertThatThrownBy(() -> service.method())
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
```

**JUnit 5 style** (used in QueueServiceTest, UserServiceTest):
```java
import static org.junit.jupiter.api.Assertions.*;

assertNotNull(jobId);
assertEquals(QueueConstants.JUDGE_QUEUE, stats.getQueueName());
assertThrows(BusinessException.class, () -> service.method());
```

**Decision:** Use AssertJ style (`assertThat`, `assertThatThrownBy`) for all new tests. It is more fluent and the AdminSubmissionServiceImplTest (most recent file) uses it.

### BusinessException + ErrorCode Pattern

**Source:** `backend-spring/src/main/java/com/ulticode/common/exception/BusinessException.java` + `ErrorCode.java`

```java
// Throwing:
throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);

// Asserting in tests:
assertThatThrownBy(() -> service.method())
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
```

**Relevant error codes:**
- `AUTH_INVALID_CREDENTIALS(10001)` -- login failures
- `AUTH_USERNAME_TAKEN(10003)` -- duplicate username
- `AUTH_EMAIL_TAKEN(10004)` -- duplicate email
- `AUTH_INVALID_RESET_TOKEN(10007)` -- bad reset token
- `AUTH_RESET_TOKEN_EXPIRED(10009)` -- expired reset token
- `SUBMISSION_USER_ID_REQUIRED(40002)` -- null userId on submit
- `SUBMISSION_CODE_EMPTY(40004)` -- empty code on submit
- `SUBMISSION_LANGUAGE_UNSUPPORTED(40005)` -- unsupported language
- `SANDBOX_ERROR(40006)` -- sandbox disabled or error
- `VALIDATION_FAILED(49999)` -- general validation failure

### ReflectionTestUtils Config Injection

**Source:** `backend-spring/src/test/java/com/ulticode/modules/queue/service/QueueServiceTest.java` (lines 72-77)

```java
import org.springframework.test.util.ReflectionTestUtils;

@BeforeEach
void setUp() {
    ReflectionTestUtils.setField(jwtProperties, "secret", "a".repeat(64));
    ReflectionTestUtils.setField(queueConfig, "enableStatusTracking", true);
}
```

Use this pattern to inject configuration values into `@Spy` or `@InjectMocks` objects without loading the Spring context.

### @Nested + @DisplayName Grouping

**Source:** All existing tests use this pattern.

```java
@Nested
@DisplayName("login()")
class LoginTests {

    @Test
    @DisplayName("successful login returns tokens and sets cookies")
    void login_validCredentials_returnsLoginResponse() { ... }
}
```

Every test method belongs to a `@Nested` class named after the method under test, with a human-readable `@DisplayName` on each `@Test`.

### Helper / Factory Methods

**Source:** `AdminSubmissionServiceImplTest.java` (lines 68-78), `UserServiceTest.java` (lines 58-83)

```java
private User createActiveUser() {
    User user = new User();
    user.setId(USER_ID);
    user.setUsername(USERNAME);
    // ... set required fields ...
    return user;
}
```

Place factory methods after `setUp()` and before the first `@Nested` class. Use `static final` constants for test data values.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `SubmissionServiceImplIT.java` | test (integration) | CRUD | No Testcontainers integration tests exist in the project yet. Use RESEARCH.md Pattern 2 for the Testcontainers setup pattern with `@Testcontainers`, `@Container`, `MySQLContainer`, `GenericContainer` for Redis. |

## Metadata

**Analog search scope:** `backend-spring/src/test/java/com/ulticode/` (23 existing test files), `backend-spring/src/main/java/com/ulticode/` (source files for classes under test)
**Files scanned:** 23 test files + 10 source files
**Pattern extraction date:** 2026-04-15
