# Testing Strategy

This document describes the testing strategy and coverage for the UltiCode project.

---

## Test Frameworks

### Backend (Java)

| Framework | Version | Purpose |
|-----------|---------|---------|
| JUnit 5 | (Spring Boot managed) | Unit testing |
| AssertJ | (Spring Boot managed) | Fluent assertions |
| Mockito | (Spring Boot managed) | Mocking dependencies |
| Testcontainers | 1.21.4 | Integration tests with real databases |
| JaCoCo | 0.8.12 | Code coverage reporting |

### Frontend (TypeScript/Vue)

| Framework | Version | Purpose |
|-----------|---------|---------|
| Vitest | 4.x | Unit testing |
| @vue/test-utils | 2.4.x | Vue component testing |
| jsdom | 29.x | DOM simulation |
| @vitest/coverage-v8 | 4.x | Coverage reporting |

### Recommendation Service (Java)

| Framework | Purpose |
|-----------|---------|
| JUnit 5 | Unit testing |
| Maven surefire | Test execution |

---

## Test Organization

### Backend Tests

```
backend-spring/src/test/java/com/ulticode/
├── modules/
│   ├── auth/
│   │   ├── controller/    # AuthControllerTest
│   │   └── service/       # AuthServiceImplTest
│   ├── submission/
│   │   └── service/impl/   # SubmissionServiceImplTest, SandboxNamespaceIsolationTest
│   └── ...
├── security/
│   ├── jwt/               # JwtTokenProviderTest
│   └── csrf/              # CsrfServiceTest
└── common/
    └── response/           # ResultTest
```

**Naming Convention**: `ClassNameTest.java` or `ClassNameImplTest.java`

### Frontend Tests

```
console/test/
├── setup.ts                # Global test configuration
└── src/views/problems/test/test.ts

# Vitest looks for *.test.ts, *.spec.ts files
```

---

## Test Types & Coverage

### Backend Coverage Requirements (JaCoCo)

```xml
<rule>
  <counter>LINE</counter>
  <value>COVEREDRATIO</value>
  <minimum>0.05</minimum>    <!-- 5% minimum line coverage -->
</rule>
<rule>
  <counter>BRANCH</counter>
  <value>COVEREDRATIO</value>
  <minimum>0.02</minimum>    <!-- 2% minimum branch coverage -->
</rule>
```

**Note**: Coverage is quite low (5% line, 2% branch) - this is a minimum bar, not a target.

### Excluded from Coverage

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

### Frontend Test Commands

```bash
# Run tests
pnpm test              # vitest --run --passWithNoTests

# Watch mode
pnpm test:watch        # vitest

# Coverage
pnpm test:coverage    # vitest --coverage
```

---

## CI/CD Testing

### GitHub Actions Workflows

#### CI Pipeline (`.github/workflows/ci.yml`)

**Triggers**: Push to main, PRs to main, manual dispatch

**Jobs**:
1. **changes** - Detect which components changed (backend/console/management/docker)
2. **backend-build** - Maven compile
3. **backend-test** - Run tests with MySQL and Redis services
   - Excludes `*IT` integration tests: `./mvnw test -Dtest='!*IT'`
   - Uses CI Spring profile: `-Dspring.profiles.active=ci`
   - Uploads test results on failure
4. **migrate-validate** - Run Flyway migrations
5. **frontend-lint** - ESLint on changed frontend
6. **frontend-type-check** - TypeScript check on changed frontend
7. **frontend-test** - Vitest on changed frontend
8. **docker-verify** - Build Docker images

**Test Exclusions**: Integration tests (`*IT`) are excluded from CI and run separately.

#### CI Recommendation Pipeline (`.github/workflows/ci-recommendation.yml`)

**Triggers**: Push to main when `recommendation/**` changes

**Jobs**:
- Build all modules: `mvn compile -B`
- Run tests: `mvn test -B`
- Upload results on failure

### Backend Test Configuration

```bash
# CI test command
./mvnw test -Dspring.profiles.active=ci -Dtest='!*IT' -B

# Dev test command (all tests including IT)
./mvnw test
```

### Frontend Test Configuration

```typescript
// vitest.config.ts (from package.json scripts)
vitest --run --passWithNoTests
```

---

## Test Patterns

### Backend Unit Test (Mockito + AssertJ)

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Spy
    private JwtProperties jwtProperties = new JwtProperties();

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

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(...)).thenReturn(ACCESS_TOKEN);

            // Act
            LoginResponse response = authService.login(loginDTO, mockResponse());

            // Assert
            assertThat(response.getCsrfToken()).isEqualTo(CSRF_TOKEN);
            verify(userService).updateLastLoginAt(USER_ID);
        }
    }
}
```

### Frontend Vue Test

```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MyComponent from './MyComponent.vue'

describe('MyComponent', () => {
  it('renders properly', () => {
    const wrapper = mount(MyComponent, {
      props: { msg: 'Hello Vitest' }
    })
    expect(wrapper.text()).toContain('Hello Vitest')
  })
})
```

---

## Test Data & Fixtures

### Backend

- Uses Mockito for mocking data layer
- ReflectionTestUtils for injecting fields
- Testcontainers for integration tests with real MySQL/Redis

### Frontend

- Global setup in `console/test/setup.ts`
- `@vue/test-utils` for component mounting
- jsdom for DOM simulation

---

## Database Testing in CI

### MySQL Service Container

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
```

### Redis Service Container

```yaml
redis:
  image: redis:7-alpine
  ports:
    - 26379:6379
```

### Environment Variables for Tests

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

## Coverage Reports

### Backend (JaCoCo)

Reports generated at: `backend-spring/target/site/jacoco/index.html`

### Frontend (Vitest + V8)

Reports generated at: `coverage/` directory

---

## Migration Testing

Migrations are validated using Flyway:

```bash
# Via db-manager CLI
python -m db_manager.cli migrate
python -m db_manager.cli validate
```

CI validates migrations by applying them against a test database.
