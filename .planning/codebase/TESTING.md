# Testing Patterns

**Analysis Date:** 2026-04-22

## Test Framework Overview

### Backend (Java - Spring Boot)

**Framework:** JUnit 5 with Mockito

**Key Dependencies:**
- `junit-jupiter` - Test runner
- `mockito-core` - Mocking framework
- `mockito-junit-jupiter` - MockitoExtension for JUnit 5

**Location:** `backend-spring/src/test/java/com/ulticode/`

### Frontend (TypeScript/Vue - Vite)

**Framework:** Vitest with Vue Test Utils

**Key Dependencies:**
- `vitest` - Test runner
- `@vue/test-utils` - Vue component testing
- `jsdom` - DOM environment

**Location:**
- `console/src/**/*.spec.ts` and `console/src/**/__tests__/*.spec.ts`
- `management/src/**/*.spec.ts` and `management/src/**/__tests__/*.spec.ts`

### Recommendation Service (Java)

**Framework:** JUnit 5 with Maven

**Location:** `recommendation/**/src/test/java/`

## Run Commands

### Backend

```bash
cd backend-spring && ./mvnw test
```

### Frontend - Console

```bash
cd console && pnpm test                  # Run all tests
cd console && pnpm test:watch            # Watch mode
cd console && pnpm test:coverage         # With coverage
```

### Frontend - Management

```bash
cd management && pnpm test                  # Run all tests
cd management && pnpm test:watch            # Watch mode
cd management && pnpm test:coverage         # With coverage
```

### Root Level

```bash
pnpm test                  # Run all frontend tests
pnpm quality               # lint + type-check + test
```

## Test Structure

### Backend Unit Tests

**Pattern:** `@ExtendWith(MockitoExtension.class)` with `@Mock` and `@InjectMocks`

**Example from `backend-spring/src/test/java/com/ulticode/modules/user/service/UserServiceTest.java`:**

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("test-user-id");
        testUser.setUsername("testuser");
        // ...
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            // Arrange
            when(userMapper.selectById("test-user-id")).thenReturn(testUser);

            // Act
            Optional<User> result = userService.findById("test-user-id");

            // Assert
            assertTrue(result.isPresent());
            assertEquals("test-user-id", result.get().getId());
        }
    }
}
```

**Key Patterns:**
- `@Nested` for grouping related tests
- `@DisplayName` for human-readable test names
- Arrange/Act/Assert pattern
- `try (MockedStatic<SecurityUtil.class>)` for static mocking

### Frontend Unit Tests (Vitest)

**Pattern:** `describe` blocks with `it` or `test`, `beforeEach` for setup

**Example from `console/src/api/__tests__/auth.spec.ts`:**

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { apiGet, apiPost } from "@/utils/request";
import { authApi } from "@/api/auth";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}));

describe("authApi", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("login", () => {
    it("calls apiPost with /auth/login and credentials", async () => {
      const credentials = { username: "testuser", password: "password123" };
      const loginResponse = { csrfToken: "csrf-123", user: mockUser };
      vi.mocked(apiPost).mockResolvedValue(loginResponse);

      const result = await authApi.login(credentials);

      expect(apiPost).toHaveBeenCalledWith("/auth/login", credentials);
      expect(result).toEqual(loginResponse);
    });
  });
});
```

### Frontend Component Tests

**Example from `console/src/components/common/loading/__tests__/ErrorBoundary.spec.ts`:**

```typescript
import { describe, it, expect, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import ErrorBoundary from "../ErrorBoundary.vue";

vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}));

describe("ErrorBoundary", () => {
  it("should render children when no error", () => {
    const wrapper = mount(ErrorBoundary, {
      slots: {
        default: () => h(NormalComponent),
      },
    });

    expect(wrapper.text()).toContain("Normal content");
  });
});
```

## Mocking Patterns

### Backend

**Mocking Mappers/Services:**
```java
@Mock
private UserMapper userMapper;

when(userMapper.selectById(anyString())).thenReturn(testUser);
when(userMapper.selectById("non-existent")).thenReturn(null);
```

**Mocking Static Methods:**
```java
try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
    securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
    // test code
}
```

### Frontend

**Mocking Modules:**
```typescript
vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}));

vi.mocked(apiPost).mockResolvedValue(mockData);
```

**Mocking Vue i18n:**
```typescript
vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}));
```

## Vitest Configuration

### Console (`console/vitest.config.ts`)

```typescript
export default defineConfig({
  plugins: [vue(), vueJsx()],
  test: {
    environment: "jsdom",
    exclude: [...configDefaults.exclude, "e2e/**"],
    globals: true,
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
```

### Management (`management/vitest.config.ts`)

```typescript
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: "jsdom",
    globals: true,
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
```

## Test File Organization

### Backend

```
backend-spring/src/test/java/com/ulticode/
├── common/
│   └── response/ResultTest.java
├── modules/
│   ├── auth/
│   │   ├── controller/AuthControllerTest.java
│   │   └── service/
│   │       ├── impl/AuthServiceImplTest.java
│   │       └── PasswordResetServiceTest.java
│   ├── user/
│   │   └── service/UserServiceTest.java
│   └── ...
```

**Naming:** `{ClassName}Test.java`

**Structure mirrors:** `src/main/java/com/ulticode/modules/`

### Frontend

```
console/src/
├── api/__tests__/auth.spec.ts
├── components/common/loading/__tests__/
│   ├── ErrorBoundary.spec.ts
│   ├── LoadingOverlay.spec.ts
│   └── RetryButton.spec.ts
├── composables/__tests__/
│   ├── useEditorThemes.spec.ts
│   ├── useLoading.spec.ts
│   └── useRetry.spec.ts
└── stores/__tests__/
    ├── auth.spec.ts
    └── editorSettings.spec.ts
```

**Naming:** `*.spec.ts` or `*.test.ts`

## Coverage

### Frontend Coverage Commands

```bash
# Console
cd console && pnpm test:coverage

# Management
cd management && pnpm test:coverage
```

### Backend Coverage

JaCoCo is integrated via Maven:
```bash
cd backend-spring && ./mvnw test
# Coverage report in target/site/jacoco/
```

## E2E Testing

**Framework:** Not currently implemented in this codebase

**Note:** The vitest config excludes `e2e/**` pattern, indicating E2E tests would be placed there if added.

## Test Best Practices Observed

1. **Arrange-Act-Assert** - Clear separation of test phases
2. **Descriptive Names** - `@DisplayName` for Java, clear `it()` descriptions for TypeScript
3. **Nested Groups** - `@Nested` classes in Java group related tests
4. **Mock Cleanup** - `vi.clearAllMocks()` in `beforeEach`
5. **Test Isolation** - Each test sets up its own mocks
6. **Meaningful Assertions** - Specific assertions, not just truthy checks

---

*Testing analysis: 2026-04-22*
