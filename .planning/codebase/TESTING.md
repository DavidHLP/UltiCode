# Testing Strategy

## Overview

UltiCode follows a multi-layer testing strategy: unit tests, integration tests, and E2E tests.

## Backend Testing (Spring Boot)

### Technology Stack

- **Framework**: Spring Boot Test with JUnit 5
- **Test Containers**: org.testcontainers (MySQL, general)
- **Security Testing**: spring-security-test

### Unit Tests

Location: `backend-spring/src/test/java/com/ulticode/`

**Coverage Areas**:
- Service layer business logic
- Utility functions
- DTO validation
- WebSocket handlers

**Example Test Structure**:
```java
@SpringBootTest
class UserServiceTest {
    @Autowired
    private UserService userService;

    @Test
    void findById_existingUser_returnsUser() {
        // Arrange
        Long userId = 1L;
        // Act
        User result = userService.findById(userId);
        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getId());
    }
}
```

### Integration Tests

- Use `@SpringBootTest` with embedded or testcontainer databases
- Test controller endpoints with `@WebMvcTest` or full context
- Verify database operations and transactions

**Example**:
```java
@SpringBootTest
@AutoConfigureMockMvc
class SubmissionControllerIntegrationTest {
    @Test
    void submitCode_validRequest_returnsAccepted() {
        // Full HTTP flow with MockMvc
    }
}
```

### Test Dependencies in pom.xml

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
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

## Frontend Testing (Vitest)

### Technology Stack

- **Test Runner**: Vitest 4.1.4
- **Environment**: jsdom
- **Vue Testing**: @vue/test-utils
- **Coverage**: vitest --coverage

### Unit Tests

Location: `console/src/**/*.test.ts` or `console/src/**/*.spec.ts`

**Configuration** (`vitest.config.ts`):
```typescript
test: {
  environment: 'jsdom',
  globals: true,
  root: './src',
}
```

**Running Tests**:
```bash
pnpm test              # Run all tests (--passWithNoTests)
pnpm test:watch       # Watch mode
pnpm test:coverage    # With coverage report
```

### Coverage Target

- **Minimum**: 80% coverage
- **Measured**: Statements, branches, functions, lines

## E2E Testing (Playwright)

### Scope

Critical user flows only:
- User authentication (login/logout)
- Problem submission and judging
- Contest participation
- Code execution verification

### Console E2E

Location: `console/e2e/**/*.spec.ts`

Excluded from vitest (`vitest.config.ts`):
```typescript
test: {
  exclude: [...configDefaults.exclude, "e2e/**"],
}
```

### Management E2E

- Admin authentication
- User/content management operations
- Analytics dashboard access

## Test Organization

### Backend Tests
```
backend-spring/src/test/java/com/ulticode/
├── common/response/ResultTest.java
├── modules/
│   ├── auth/service/AuthServiceImplTest.java
│   ├── auth/controller/AuthControllerTest.java
│   ├── user/service/UserServiceTest.java
│   ├── problem/controller/ProblemControllerTest.java
│   ├── submission/service/
│   │   ├── SubmissionServiceImplTest.java
│   │   └── SubmissionServiceImplIT.java  # Integration test
│   └── websocket/
│       ├── contest/ContestRoomManagerTest.java
│       └── util/TokenExtractorTest.java
└── security/
    ├── jwt/JwtTokenProviderTest.java
    └── csrf/CsrfServiceTest.java
```

### Frontend Tests
```
console/src/
├── components/
│   └── __tests__/
├── hooks/
│   └── __tests__/
└── utils/
    └── __tests__/
```

## Best Practices

### AAA Pattern

```typescript
test('calculates similarity correctly', () => {
  // Arrange
  const vector1 = [1, 0, 0]
  const vector2 = [0, 1, 0]

  // Act
  const similarity = calculateCosineSimilarity(vector1, vector2)

  // Assert
  expect(similarity).toBe(0)
})
```

### Descriptive Test Names

```typescript
test('returns empty array when no markets match query', () => {})
test('throws error when API key is missing', () => {})
test('falls back to substring search when Redis is unavailable', () => {})
```

### Test Isolation

- Each test should be independent
- Use mocks for external dependencies
- Clean up state between tests

### Backend Naming

- Test classes: `<ClassName>Test` or `<ClassName>IT` (integration)
- Test methods: `methodName_scenario_expectedBehavior`

## Running Tests

### Backend
```bash
cd backend-spring
./mvnw test
```

### Frontend (Console)
```bash
cd console
pnpm test
pnpm test:coverage
```

### Frontend (Management)
```bash
cd management
pnpm test
pnpm test:coverage
```

### All Tests
```bash
pnpm test  # Root-level (runs for all packages)
```
