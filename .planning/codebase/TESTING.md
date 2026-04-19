# Testing Patterns

**Analysis Date:** 2026-04-19

## Test Frameworks

**Frontend (Console & Management):**
- **Framework:** Vitest 4.x
- **Environment:** jsdom
- **Component Testing:** @vue/test-utils
- **Configuration:** `vitest.config.ts` in project root

**Backend (Spring Boot):**
- **Framework:** JUnit 5 (`@Test`, `@Nested`, `@DisplayName`)
- **Assertions:** AssertJ for fluent assertions
- **Mocking:** Mockito with `@ExtendWith(MockitoExtension.class)`
- **Integration:** Testcontainers 1.11.3 for MySQL integration tests

## Test File Organization

**Frontend:**
- Tests co-located in `__tests__/` subdirectories
- Pattern: `src/stores/__tests__/auth.spec.ts`
- Pattern: `src/composables/__tests__/useRetry.spec.ts`
- Pattern: `src/components/common/loading/__tests__/ErrorBoundary.spec.ts`

**Backend:**
- Mirror main source structure in `src/test/java/`
- Pattern: `src/test/java/com/ulticode/modules/user/service/UserServiceTest.java`
- Pattern: `src/test/java/com/ulticode/common/response/ResultTest.java`

## Run Commands

**Frontend:**
```bash
pnpm test                  # Run all tests (vitest --run --passWithNoTests)
pnpm test:watch           # Watch mode (vitest)
pnpm test:coverage        # Coverage report (vitest --coverage)
```

**Backend:**
```bash
cd backend-spring && ./mvnw test                    # Run all tests
cd backend-spring && ./mvnw test -Dtest=UserServiceTest  # Run specific test
```

## Test Structure

**Frontend (Vitest - AAA Pattern):**
```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";

describe("useRetry", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("should return result on first successful attempt", async () => {
    // Arrange
    const fn = vi.fn().mockResolvedValue("success");

    // Act
    const result = await retry(fn);

    // Assert
    expect(result).toBe("success");
    expect(fn).toHaveBeenCalledTimes(1);
  });
});
```

**Backend (JUnit 5 - Nested Classes):**
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("successful login returns response with csrf token")
        void login_validCredentials_returnsLoginResponse() {
            // Arrange
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(USERNAME);
            loginDTO.setPassword(PASSWORD);
            User user = createActiveUser();
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

            // Act
            LoginResponse response = authService.login(loginDTO, mockResponse());

            // Assert
            assertThat(response.getCsrfToken()).isEqualTo(CSRF_TOKEN);
        }
    }
}
```

## Mocking Patterns

**Frontend (Vitest):**
```typescript
// Mock entire module
vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}));

// Mock specific function
vi.mocked(apiPost).mockResolvedValue({ user: mockUser });

// Mock with return value
vi.mocked(getCsrfToken).mockReturnValue("test-csrf-token");

// Clear mocks between tests
beforeEach(() => {
  vi.clearAllMocks();
});
```

**Backend (Mockito):**
```java
@Mock
private UserMapper userMapper;

// When-then pattern
when(userMapper.selectById("test-user-id")).thenReturn(testUser);
when(userMapper.selectById("non-existent")).thenReturn(null);

// Argument matchers
when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

// Verify interactions
verify(userMapper).updateById(any(User.class));
verify(userMapper, never()).updateById(any(User.class));

// Static mocking
try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
    securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
    // test code
}
```

## Test Data Fixtures

**Frontend:**
```typescript
const mockUser: User = {
  id: "1",
  username: "testuser",
  name: "Test User",
  email: "test@example.com",
  role: "USER",
  isActive: true,
  joinedAt: "2026-01-01T00:00:00Z",
};
```

**Backend:**
```java
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

## Async Testing

**Frontend (Vitest with fake timers):**
```typescript
beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

it("should retry with exponential backoff", async () => {
  vi.useRealTimers(); // Use real timers for async test

  const fn = vi.fn()
    .mockRejectedValueOnce(new Error("Error 1"))
    .mockRejectedValueOnce(new Error("Error 2"))
    .mockResolvedValue("success");

  const result = await retry(fn);
  expect(result).toBe("success");

  vi.useFakeTimers(); // Restore fake timers
});
```

**Backend:**
- Standard async/await with JUnit 5

## Coverage

**Frontend:**
- Vitest coverage via `@vitest/coverage-v8`
- Command: `pnpm test:coverage`
- No enforced coverage threshold observed in config

**Backend:**
- JaCoCo for coverage (via spring-boot-starter-test)
- Target: Not explicitly enforced in pom.xml

## Test Naming

**Frontend:**
- `describe("useAuthStore")` for the thing being tested
- `it("transitions idle -> loading -> ready on success")` for behavior

**Backend:**
- `@DisplayName("AuthServiceImpl")` on class
- `@DisplayName("login()")` on nested class
- `void login_validCredentials_returnsLoginResponse()` for method name pattern

## Integration Testing

**Backend with Testcontainers:**
```java
@Testcontainers
class MyRepositoryIT {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Test
    void save_and_findById() {
        // Integration test with real MySQL
    }
}
```

## E2E Testing

- No E2E framework currently configured
- Manual testing with curl examples in CLAUDE.md

---

*Testing analysis: 2026-04-19*
