# UltiCode 测试策略 (TESTING)

本文档描述 UltiCode 项目的测试策略、框架和覆盖率要求。

---

## 1. 测试框架

### 1.1 后端 (Java)

| 框架 | 版本 | 用途 |
|------|------|------|
| JUnit 5 | Spring Boot 管理 | 单元测试 |
| AssertJ | Spring Boot 管理 | 流式断言 |
| Mockito | Spring Boot 管理 | 依赖模拟 |
| Testcontainers | 1.21.4 | 集成测试 (真实数据库) |
| JaCoCo | 0.8.12 | 代码覆盖率报告 |

### 1.2 前端 (TypeScript/Vue)

| 框架 | 版本 | 用途 |
|------|------|------|
| Vitest | 4.x | 单元测试 |
| @vue/test-utils | 2.4.x | Vue 组件测试 |
| jsdom | 29.x | DOM 模拟 |
| @vitest/coverage-v8 | 4.x | 覆盖率报告 |

### 1.3 推荐服务 (Java)

| 框架 | 用途 |
|------|------|
| JUnit 5 | 单元测试 |
| Maven surefire | 测试执行 |

---

## 2. 测试组织结构

### 2.1 后端测试目录

```
backend-spring/src/test/java/com/ulticode/
├── modules/
│   ├── auth/
│   │   ├── controller/    # AuthControllerTest
│   │   └── service/       # AuthServiceImplTest, PasswordResetServiceTest
│   │   └── service/impl/  # AuthServiceImplTest
│   ├── submission/
│   │   └── service/impl/   # SubmissionServiceImplTest, SandboxNamespaceIsolationTest
│   ├── problem/
│   │   └── controller/     # ProblemControllerTest
│   ├── achievement/
│   │   ├── service/        # AchievementServiceTest
│   │   └── listener/       # AchievementNotificationListenerTest
│   ├── websocket/
│   │   ├── service/        # RealtimeServiceTest
│   │   ├── util/           # WebSocketUtilsTest, TokenExtractorTest
│   │   ├── notification/   # UserSessionManagerTest
│   │   ├── interceptor/    # JwtChannelInterceptorTest
│   │   └── contest/        # ContestWebSocketHandlerTest, ContestRoomManagerTest
│   ├── queue/
│   │   ├── processor/      # JudgeWorkerProcessorTest
│   │   └── constants/      # QueueConstantsTest
│   └── ...
├── security/
│   ├── jwt/               # JwtTokenProviderTest, JwtPropertiesTest
│   └── csrf/              # CsrfServiceTest
└── common/
    └── response/           # ResultTest
```

**命名规范**: `ClassNameTest.java` 或 `ClassNameImplTest.java`

### 2.2 前端测试目录

```
console/src/
├── stores/__tests__/           # Pinia store 测试
│   ├── auth.spec.ts
│   ├── editorSettings.spec.ts
│   ├── recommendation.spec.ts
│   └── contest/
│       ├── contestStore.spec.ts
│       └── rankingStore.spec.ts
├── api/__tests__/              # API 客户端测试
│   ├── auth.spec.ts
│   └── problem-detail.spec.ts
├── composables/__tests__/      # Composables 测试
│   ├── useRetry.spec.ts
│   ├── useCodeTemplates.spec.ts
│   ├── usePWA.spec.ts
│   ├── useNetworkStatus.spec.ts
│   ├── useLoading.spec.ts
│   ├── useEditorThemes.spec.ts
│   └── contest/
│       └── useContestSocket.spec.ts
└── components/                 # 组件测试
    └── common/loading/__tests__/
        ├── RetryButton.spec.ts
        ├── LoadingOverlay.spec.ts
        └── ErrorBoundary.spec.ts

management/src/
├── api/admin/__tests__/        # Admin API 测试
│   └── problems.spec.ts
└── stores/admin/__tests__/     # Admin Store 测试
    ├── problems.spec.ts
    └── moderation.spec.ts
```

---

## 3. 测试统计

### 3.1 后端测试文件

- **测试文件总数**: 40 个 Java 测试文件
- **覆盖模块**: auth, submission, problem, achievement, websocket, queue, vote, i18n, search, monitoring, backup, email, edgeoperations, subscription, follow, admin, security

### 3.2 前端测试文件

**Console**:
- 测试文件总数: 18 个 `.spec.ts` 文件
- 覆盖: stores, api, composables, components

**Management**:
- 测试文件总数: 3 个 `.spec.ts` 文件
- 覆盖: admin api, admin stores

---

## 4. 测试覆盖率要求

### 4.1 后端覆盖率 (JaCoCo)

```xml
<rule>
  <counter>LINE</counter>
  <value>COVEREDRATIO</value>
  <minimum>0.05</minimum>    <!-- 5% 最低行覆盖率 -->
</rule>
<rule>
  <counter>BRANCH</counter>
  <value>COVEREDRATIO</value>
  <minimum>0.02</minimum>    <!-- 2% 最低分支覆盖率 -->
</rule>
```

### 4.2 覆盖率排除项

以下文件/类型不计入覆盖率:

```xml
<excludes>
  <exclude>**/*Mapper.java</exclude>
  <exclude>**/*Mapper.xml</exclude>
  <exclude>**/entity/*.java</exclude>
  <exclude>**/*DTO.java</exclude>
  <exclude>**/*VO.java</exclude>
  <exclude>**/*BO.java</exclude>
  <exclude>**/*Response.java</exclude>
  <exclude>**/*Request.java</exclude>
  <exclude>**/*Config.java</exclude>
  <exclude>**/*Properties.java</exclude>
  <exclude>**/*Application.java</exclude>
</excludes>
```

---

## 5. 测试命令

### 5.1 后端

```bash
# 运行所有测试 (开发环境)
cd backend-spring && ./mvnw test

# 运行单元测试 (CI 环境，排除集成测试)
./mvnw test -Dtest='!*IT'

# 运行特定测试类
./mvnw test -Dtest=AuthServiceImplTest

# 生成覆盖率报告
./mvnw test jacoco:report
```

### 5.2 前端 Console

```bash
cd console

# 运行测试
pnpm test              # vitest --run --passWithNoTests

# 监视模式
pnpm test:watch       # vitest

# 覆盖率报告
pnpm test:coverage    # vitest --coverage

# 验证 Mock 数据
pnpm validate:mocks
pnpm validate:mocks:verbose
pnpm validate:mocks:strict
```

### 5.3 前端 Management

```bash
cd management

# 运行测试
pnpm test              # vitest --run --passWithNoTests

# 监视模式
pnpm test:watch        # vitest --passWithNoTests

# 覆盖率报告
pnpm test:coverage     # vitest --coverage --passWithNoTests
```

### 5.4 根目录 (全项目)

```bash
# 运行所有测试
pnpm test

# Lint 所有代码
pnpm lint

# 类型检查
pnpm type-check

# 质量检查 (lint + type-check + test)
pnpm quality
```

---

## 6. 测试模式

### 6.1 后端单元测试 (Mockito + AssertJ + Nested)

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserService userService;

    @Spy
    private JwtProperties jwtProperties = new JwtProperties();

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String USER_ID = "test-user-123";
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password123";

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
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, "USER"))
                    .thenReturn(ACCESS_TOKEN);
            when(csrfService.generateToken(USER_ID)).thenReturn(CSRF_TOKEN);
            when(userService.toVO(user)).thenReturn(mock(UserVO.class));

            // Act
            LoginResponse response = authService.login(loginDTO, mockResponse());

            // Assert
            assertThat(response.getCsrfToken()).isEqualTo(CSRF_TOKEN);
            assertThat(response.getUser()).isNotNull();
            verify(jwtTokenProvider).generateAccessToken(USER_ID, USERNAME, "USER");
            verify(csrfService).generateToken(USER_ID);
            verify(userService).updateLastLoginAt(USER_ID);
        }

        @Test
        @DisplayName("non-existent user throws AUTH_INVALID_CREDENTIALS")
        void login_nonExistentUser_throwsException() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> authService.login(loginDTO, mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        }
    }

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
}
```

### 6.2 后端集成测试 (Testcontainers)

```java
@SpringBootTest
@Testcontainers
@DisplayName("SubmissionServiceImpl Integration")
class SubmissionServiceImplIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:9.1")
            .withDatabaseName("ulticode_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Test
    @DisplayName("submit code and receive result")
    void submitCode_receivesResult() {
        // Integration test with real MySQL and Redis
    }
}
```

### 6.3 前端 Vitest 测试

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useAuthStore } from "@/stores/auth";
import { apiGet, apiPost } from "@/utils/request";
import { csrfManager, getCsrfToken } from "@/utils/csrf";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}));

vi.mock("@/utils/csrf", () => ({
  csrfManager: {
    clearToken: vi.fn(),
    refreshFromResponse: vi.fn(),
    getToken: vi.fn(() => "test-csrf-token"),
    setToken: vi.fn(),
  },
  getCsrfToken: vi.fn(() => "test-csrf-token"),
}));

const mockUser: User = {
  id: "1",
  username: "testuser",
  name: "Test User",
  email: "test@example.com",
  role: "USER",
  isActive: true,
  joinedAt: "2026-01-01T00:00:00Z",
};

describe("useAuthStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  describe("login", () => {
    it("transitions idle -> loading -> ready on success", async () => {
      const store = useAuthStore();
      expect(store.status).toBe("idle");

      vi.mocked(apiPost).mockResolvedValue({
        user: mockUser,
        csrfToken: "csrf-new",
      });

      const loginPromise = store.login({
        username: "testuser",
        password: "password123",
      });
      expect(store.status).toBe("loading");

      await loginPromise;

      expect(store.status).toBe("ready");
      expect(store.user).toEqual(mockUser);
      expect(store.isAuthenticated).toBe(true);
    });

    it("calls refreshFromResponse with CSRF token from login", async () => {
      const store = useAuthStore();

      vi.mocked(apiPost).mockResolvedValue({
        user: mockUser,
        csrfToken: "csrf-new",
      });

      await store.login({ username: "testuser", password: "password123" });

      expect(csrfManager.refreshFromResponse).toHaveBeenCalledWith({
        csrfToken: "csrf-new",
      });
    });
  });
});
```

---

## 7. CI/CD 测试配置

### 7.1 GitHub Actions 工作流

**CI Pipeline** (`.github/workflows/ci.yml`):

- **触发**: push 到 main, PR 到 main, 手动触发
- **Jobs**:
  1. `changes` - 检测变更的组件
  2. `backend-build` - Maven 编译
  3. `backend-test` - 运行测试 (排除 `*IT`)
  4. `migrate-validate` - Flyway 迁移验证
  5. `frontend-lint` - ESLint 检查
  6. `frontend-type-check` - TypeScript 检查
  7. `frontend-test` - Vitest 测试
  8. `docker-verify` - Docker 镜像构建

### 7.2 测试数据库配置

```yaml
mysql:
  image: mysql:9.1
  env:
    MYSQL_ROOT_PASSWORD: root
    MYSQL_DATABASE: ulticode_test
    MYSQL_USER: ulticode
    MYSQL_PASSWORD: ulticode
  ports:
    - 23306:3306

redis:
  image: redis:7-alpine
  ports:
    - 26379:6379
```

### 7.3 测试环境变量

```bash
DB_HOST: localhost
DB_PORT: 23306
DB_USER: ulticode
DB_PASSWORD: ulticode
DB_NAME: ulticode_test
REDIS_HOST: localhost
REDIS_PORT: 26379
JWT_SECRET: test-jwt-secret-key-for-ci-minimum-32-characters-long
```

---

## 8. Vitest 配置

### 8.1 Console Vitest 配置

```typescript
// console/vitest.config.ts
export default defineConfig({
  plugins: [
    vue(),
    vueJsx(),
    // Mock virtual:pwa-register for tests
    {
      name: "virtual-pwa-register-mock",
      resolveId(id) {
        if (id === "virtual:pwa-register") {
          return "\0virtual:pwa-register";
        }
      },
      load(id) {
        if (id === "\0virtual:pwa-register") {
          return `export function registerSW(options = {}) { ... }`;
        }
      },
    },
  ],
  test: {
    environment: "jsdom",
    exclude: [...configDefaults.exclude, "e2e/**"],
    root: fileURLToPath(new URL("./src", import.meta.url)),
    globals: true,
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
```

### 8.2 Management Vitest 配置

```typescript
// management/vitest.config.ts
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: "jsdom",
    globals: true,
    root: fileURLToPath(new URL("./src", import.meta.url)),
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
```

---

## 9. 测试数据与Fixtures

### 9.1 后端

- 使用 Mockito 模拟数据层
- 使用 `ReflectionTestUtils` 注入字段
- 使用 Testcontainers 进行真实数据库集成测试

### 9.2 前端

- Console: 使用 PWA register mock
- `@vue/test-utils` 进行组件挂载
- jsdom 进行 DOM 模拟

---

## 10. 覆盖率报告

### 10.1 后端 (JaCoCo)

报告生成位置: `backend-spring/target/site/jacoco/index.html`

### 10.2 前端 (Vitest + V8)

报告生成位置: `coverage/` 目录

---

## 11. 迁移测试

使用 Flyway 验证迁移:

```bash
# 通过 db-manager CLI
python -m db_manager.cli migrate
python -m db_manager.cli validate
python -m db_manager.cli repair
python -m db_manager.cli info
```

CI 在测试数据库上应用迁移进行验证。
