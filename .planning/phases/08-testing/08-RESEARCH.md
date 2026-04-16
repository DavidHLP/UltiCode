# Phase 8: Testing - Research

**Researched:** 2026-04-16
**Domain:** Frontend vitest (Vue 3 + Pinia) and backend @WebMvcTest (Spring Boot 3.5)
**Confidence:** HIGH

## Summary

This phase adds key-path tests to three areas: Console frontend (API layer + auth/problem stores), Management frontend (admin API layer + one admin store with CRUD), and backend (AuthController + ProblemController @WebMvcTest integration tests). The project already has established test patterns in all three areas -- vitest with jsdom for frontends, Mockito + JUnit 5 for backend -- so the primary challenge is correctly mocking dependencies without duplicating existing patterns.

The frontend API layer tests must mock `axios` (or the centralized `request.ts` module) to verify correct HTTP methods, paths, and parameter passing. Store tests follow the Pinia + `setActivePinia(createPinia())` pattern already established in `editorSettings.spec.ts` and `moderation.spec.ts`. Backend @WebMvcTest tests require careful exclusion of security filter chain components (`JwtAuthenticationFilter`, `CsrfValidationFilter`, `RateLimitAspect`) since they are `@Component` beans that @WebMvcTest would try to load. The existing codebase has zero @WebMvcTest tests, so this is a new pattern to introduce.

**Primary recommendation:** Mock at the module boundary (axios for API tests, API modules for store tests, service layer for controller tests) rather than testing internals, following the established patterns exactly.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Test the API layer by mocking `axios` via vitest's `vi.mock` -- verify correct HTTP methods, paths, and parameter passing
- **D-02:** Test auth store login/refresh flow -- verify token storage state transitions and API call sequencing
- **D-03:** Test problem store data fetching -- verify loading states, error handling, and data transformation
- **D-04:** Follow existing test patterns from `__tests__/` directories -- co-located with source files
- **D-05:** Test admin API layer (`management/src/api/admin/`) -- verify CRUD endpoints for at least one admin resource
- **D-06:** Test at least one admin store with CRUD operations -- verify state management patterns
- **D-07:** Follow existing vitest patterns -- only 1 existing test (`moderation.spec.ts`) establishes the pattern
- **D-08:** Use `@WebMvcTest` for controller integration tests -- loads only the web layer, not the full context
- **D-09:** Mock service dependencies with `@MockBean` -- test request/response contracts in isolation
- **D-10:** Test AuthController: login endpoint authentication, token response format, validation errors
- **D-11:** Test ProblemController: problem listing, single problem retrieval, authentication requirements
- **D-12:** Use Testcontainers pattern established in Phase 3 -- existing test infrastructure at `backend-spring/src/test/`

### Claude's Discretion
- Exact test case details -- planner can decide based on API endpoints
- Mock data specifics -- follow existing patterns
- Test file naming conventions -- follow project conventions

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TEST-02 | Console frontend key-path tests (API layer, auth store, problem store) | API layer: mock axios; Auth store: mock apiGet/apiPost + csrfManager; Problem store: mock problem-detail API |
| TEST-03 | Management frontend key-path tests (API layer, admin store) | Admin problems API: mock request.ts helpers; Problems store: mock problemsApi module |
| TEST-04 | Backend Controller @WebMvcTest (AuthController, ProblemController) | @WebMvcTest with @MockBean; exclude security filters; test request/response contracts |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Backend: Spring Boot 3.5 (Java 17), MyBatis-Plus, MySQL, Redis
- Frontend: Vue 3 + Vite + Tailwind CSS, Vitest for testing
- Test commands: `cd console && pnpm test`, `cd management && pnpm test:coverage`, `cd backend-spring && ./mvnw test`
- All API responses use `Result<T>` wrapper: `{ code: 0, message: "success", data: {...} }`
- Frontend `request.ts` auto-unwraps `Result<T>`, returning `response.data` directly
- Authentication: JWT in httpOnly cookies + CSRF token via non-httpOnly cookie
- Color space: OKLCH only, no hex/HSL
- File organization: `__tests__/` directories co-located with source files
- Management has no vitest.config.ts -- uses vite.config.ts (no `test` block), needs vitest.config.ts added

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| API layer mocking (axios interceptors) | Browser / Client (vitest) | -- | Tests verify HTTP method/path/params at the boundary |
| Auth store state transitions | Browser / Client (vitest) | -- | Pinia store logic is client-side state management |
| Problem store data fetching | Browser / Client (vitest) | -- | Loading/error/data transformation is client-side |
| Admin API layer CRUD verification | Browser / Client (vitest) | -- | Same axios boundary testing for management |
| Admin store CRUD state management | Browser / Client (vitest) | -- | Pinia store CRUD operations are client-side |
| AuthController request/response contracts | API / Backend (@WebMvcTest) | -- | Controller layer is the API tier boundary |
| ProblemController request/response contracts | API / Backend (@WebMvcTest) | -- | Controller layer is the API tier boundary |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| vitest | ^4.0.15 | Test runner (both frontends) | Already configured in console; management needs vitest.config.ts |
| @vue/test-utils | ^2.4.6 | Vue component testing (console) | Already in console devDependencies |
| jsdom | ^27.2.0 | DOM environment for tests (console) | Already configured in console vitest.config.ts |
| pinia | ^3.0.4 | State management (both frontends) | Required for `setActivePinia(createPinia())` in store tests |
| JUnit 5 | (managed by Spring Boot BOM) | Backend test framework | All 31 existing backend tests use it |
| Mockito | (managed by spring-boot-starter-test) | Mocking framework for backend | All existing tests use `@ExtendWith(MockitoExtension.class)` |
| AssertJ | (managed by spring-boot-starter-test) | Fluent assertions for backend | All existing tests use `assertThat()` style |
| spring-boot-starter-test | (managed by parent POM) | Test starter including @WebMvcTest | Includes MockMvc, @MockBean, etc. |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Testcontainers | 1.21.3 | Integration test containers | NOT for @WebMvcTest (these are slice tests, no DB) |
| @vitest/coverage-v8 | ^4.0.15 | Code coverage (management) | Already in management devDependencies |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| @WebMvcTest | @SpringBootTest with MockMvc | @SpringBootTest loads full context (slower, needs Redis/MySQL). @WebMvcTest is slice testing -- faster, focused. CONTEXT.md D-08 locks @WebMvcTest. |
| vi.mock('axios') | MSW (Mock Service Worker) | MSW is overkill for unit-level API layer tests. vi.mock is simpler and matches existing project patterns. |
| @MockBean | @Mock + @InjectMocks | @MockBean is required for @WebMvcTest to inject mocks into the Spring test context. @Mock alone works for unit tests but not for MockMvc-based integration tests. |

**Installation:**
No new packages needed. All dependencies already present:
```bash
# Console -- already has vitest, @vue/test-utils, jsdom, pinia
cd console && pnpm test

# Management -- has vitest, pinia but MISSING vitest.config.ts
cd management && pnpm test

# Backend -- has spring-boot-starter-test, mockito, assertj
cd backend-spring && ./mvnw test
```

**Version verification:**
- vitest ^4.0.15: [VERIFIED: management/package.json, console/package.json]
- @vue/test-utils ^2.4.6: [VERIFIED: console/package.json]
- jsdom ^27.2.0: [VERIFIED: console/package.json]
- pinia ^3.0.4: [VERIFIED: management/package.json]
- spring-boot-starter-test: [VERIFIED: backend-spring/pom.xml]
- testcontainers 1.21.3: [VERIFIED: backend-spring/pom.xml BOM]

## Architecture Patterns

### System Architecture Diagram

```
                         FRONTEND TESTS (Vitest + jsdom)
                         ================================

    Console API Layer Tests              Management API Layer Tests
    ┌──────────────────────┐            ┌──────────────────────────┐
    │ vi.mock('@/utils/    │            │ vi.mock('@/utils/        │
    │   request')          │            │   request')              │
    │        │             │            │        │                 │
    │        ▼             │            │        ▼                 │
    │ authApi.login()  ────┼── verify ──┼──► POST /auth/login     │
    │ authApi.getCurrentUser()           │ problemsApi.create()    │
    │ fetchProblemDetailById()           │ problemsApi.getProblems()│
    └──────────────────────┘            └──────────────────────────┘
                    │                              │
                    ▼                              ▼
    Console Store Tests                 Management Store Tests
    ┌──────────────────────┐            ┌──────────────────────────┐
    │ vi.mock('@/api/auth')│            │ vi.mock('@/api/admin/    │
    │ vi.mock('@/utils/    │            │   problems')             │
    │   csrf')             │            │                          │
    │        │             │            │        │                 │
    │        ▼             │            │        ▼                 │
    │ useAuthStore()       │            │ useProblemsStore()       │
    │  login() ─► status   │            │  fetchProblems() ─► data │
    │  init()  ─► ready    │            │  createProblem()         │
    └──────────────────────┘            └──────────────────────────┘


                         BACKEND TESTS (@WebMvcTest)
                         ==========================

    ┌──────────────────────────────────────────────────┐
    │ @WebMvcTest(AuthController.class)                 │
    │                                                  │
    │  @MockBean AuthService                           │
    │  @MockBean CsrfService                           │
    │  @MockBean UserService                           │
    │  @MockBean PasswordResetService                   │
    │  @MockBean OAuthService                          │
    │  @MockBean PermissionService                     │
    │  excludeFilters: JwtAuthenticationFilter,         │
    │    CsrfValidationFilter, RateLimitAspect          │
    │                                                  │
    │  MockMvc ──► POST /auth/login ──► Result<T>      │
    │  MockMvc ──► GET  /auth/me   ──► Result<T>      │
    └──────────────────────────────────────────────────┘

    ┌──────────────────────────────────────────────────┐
    │ @WebMvcTest(ProblemController.class)              │
    │                                                  │
    │  @MockBean ProblemService                        │
    │  excludeAutoConfiguration: SecurityAutoConfig     │
    │                                                  │
    │  MockMvc ──► GET  /problems     ──► Result<T>    │
    │  MockMvc ──► GET  /problems/1   ──► Result<T>    │
    │  MockMvc ──► POST /problems     ──► 403 (no auth)│
    └──────────────────────────────────────────────────┘
```

### Recommended Project Structure

```
console/src/
├── api/
│   ├── __tests__/
│   │   ├── auth.spec.ts              # NEW: authApi tests
│   │   └── problem-detail.spec.ts    # NEW: fetchProblemDetailById tests
│   ├── auth.ts
│   └── problem-detail.ts
├── stores/
│   ├── __tests__/
│   │   ├── auth.spec.ts              # NEW: useAuthStore tests
│   │   └── editorSettings.spec.ts    # EXISTING
│   └── auth.ts

management/src/
├── api/
│   └── admin/
│       └── __tests__/
│           └── problems.spec.ts      # NEW: problemsApi CRUD tests
├── stores/
│   └── admin/
│       └── __tests__/
│           ├── problems.spec.ts      # NEW: useProblemsStore CRUD tests
│           └── moderation.spec.ts    # EXISTING
├── vitest.config.ts                  # NEW: required for management tests

backend-spring/src/test/java/com/ulticode/modules/
├── auth/controller/
│   └── AuthControllerTest.java       # NEW: @WebMvcTest
├── problem/controller/
│   └── ProblemControllerTest.java    # NEW: @WebMvcTest
```

### Pattern 1: Frontend API Layer Mocking (axios)

**What:** Mock the centralized `request.ts` module (which wraps axios) to verify API functions call the correct HTTP methods, paths, and parameters.

**When to use:** Testing all API layer functions in `console/src/api/` and `management/src/api/admin/`.

**Key insight:** The API functions use `apiGet<T>()`, `apiPost<T>()`, etc. from `@/utils/request`. These are thin wrappers around the axios instance. The cleanest mock strategy is to mock the `@/utils/request` module itself and verify the exported functions are called with correct args.

```typescript
// Source: established pattern from CONTEXT.md D-01 + request.ts analysis
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { authApi } from '../auth'

// Mock the request utility that auth.ts imports from
vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}))

// Import the mocked functions
import { apiGet, apiPost } from '@/utils/request'

describe('authApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('login', () => {
    it('should call apiPost with correct path and credentials', async () => {
      const mockResponse = { csrfToken: 'test-csrf', user: { id: '1', username: 'test' } }
      vi.mocked(apiPost).mockResolvedValue(mockResponse)

      const result = await authApi.login({ username: 'testuser', password: 'pass123' })

      expect(apiPost).toHaveBeenCalledWith('/auth/login', { username: 'testuser', password: 'pass123' })
      expect(result).toEqual(mockResponse)
    })
  })

  describe('getCurrentUser', () => {
    it('should call apiGet with /auth/me and extract user from response', async () => {
      const mockUser = { id: '1', username: 'test' }
      const mockResponse = { user: mockUser, csrfToken: 'test-csrf' }
      vi.mocked(apiGet).mockResolvedValue(mockResponse)

      const result = await authApi.getCurrentUser()

      expect(apiGet).toHaveBeenCalledWith('/auth/me')
      expect(result).toEqual(mockUser)
    })
  })
})
```

### Pattern 2: Pinia Store Testing

**What:** Create an isolated Pinia instance, mock API dependencies, then test store actions and state transitions.

**When to use:** All store tests. Two sub-patterns exist:

**Sub-pattern A: localStorage-dependent stores** (from `editorSettings.spec.ts`):
- Mock `window.localStorage`
- Mock `window.matchMedia`
- Use `setActivePinia(createPinia())` in `beforeEach`

**Sub-pattern B: API-dependent stores** (from `moderation.spec.ts`):
- Mock the API module with `vi.mock('@/api/admin/moderation', ...)`
- Mock all API functions with `vi.fn()`
- Test initial state, computed properties, filter/pagination operations

```typescript
// Source: management moderation.spec.ts pattern
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../auth'

// Mock dependencies
vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}))

vi.mock('@/utils/csrf', () => ({
  csrfManager: {
    clearToken: vi.fn(),
    refreshFromResponse: vi.fn(),
  },
  getCsrfToken: vi.fn(() => 'test-csrf-token'),
}))

import { apiGet, apiPost } from '@/utils/request'

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('login', () => {
    it('should set status to loading, then ready on success', async () => {
      const mockUser = { id: '1', username: 'test', role: 'USER' }
      vi.mocked(apiPost).mockResolvedValue({
        csrfToken: 'test-csrf',
        user: mockUser,
      })

      const store = useAuthStore()
      expect(store.status).toBe('idle')

      await store.login({ username: 'testuser', password: 'pass123' })

      expect(store.user).toEqual(mockUser)
      expect(store.status).toBe('ready')
      expect(store.isAuthenticated).toBe(true)
    })

    it('should set status to error on failure', async () => {
      vi.mocked(apiPost).mockRejectedValue(new Error('Invalid credentials'))

      const store = useAuthStore()
      await expect(store.login({ username: 'test', password: 'wrong' }))
        .rejects.toThrow('Invalid credentials')

      expect(store.status).toBe('error')
      expect(store.user).toBeNull()
      expect(store.isAuthenticated).toBe(false)
    })
  })

  describe('initialize', () => {
    it('should transition idle -> loading -> ready', async () => {
      const mockUser = { id: '1', username: 'test', role: 'USER' }
      vi.mocked(apiGet).mockResolvedValue({ user: mockUser, csrfToken: 'csrf-123' })

      const store = useAuthStore()
      expect(store.status).toBe('idle')

      await store.initialize()

      expect(store.status).toBe('ready')
    })

    it('should skip /auth/me when no CSRF token exists', async () => {
      // Override the default mock to return null
      const { getCsrfToken } = await import('@/utils/csrf')
      vi.mocked(getCsrfToken).mockReturnValue(null)

      const store = useAuthStore()
      await store.initialize()

      expect(apiGet).not.toHaveBeenCalled()
      expect(store.status).toBe('ready')
      expect(store.user).toBeNull()
    })
  })

  describe('clearUser', () => {
    it('should reset user, permissions, and csrf token', () => {
      const store = useAuthStore()
      store.login({ username: 'test', password: 'pass' }) // set state (fire-and-forget for test)

      store.clearUser()

      expect(store.user).toBeNull()
      expect(store.permissions.size).toBe(0)
      expect(store.status).toBe('ready')
    })
  })
})
```

### Pattern 3: @WebMvcTest Controller Testing

**What:** Use Spring's `@WebMvcTest` to test controllers in isolation, loading only the MVC layer with MockMvc.

**When to use:** Testing AuthController and ProblemController request/response contracts.

**Critical challenge:** The SecurityConfig imports several `@Component` beans that @WebMvcTest would try to instantiate:
- `JwtAuthenticationFilter` (depends on `JwtTokenProvider`, `JwtProperties`)
- `CsrfValidationFilter` (depends on `CsrfService`)
- `AuthenticationEntryPointImpl` (depends on `ObjectMapper`)
- `CorsProperties` (depends on `@ConfigurationProperties`)
- `RateLimitAspect` (depends on `StringRedisTemplate`)

**Solution:** Use `@WebMvcTest` with `excludeAutoConfiguration` and `excludeFilters` to prevent loading security components. Alternatively, use `@WebMvcTest(value = ..., excludeFilters = @ComponentScan.Filter(...))`.

```java
// Source: Spring Boot 3.x testing docs + existing backend test patterns
@WebMvcTest(AuthController.class)
@Import(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private CsrfService csrfService;

    @MockBean
    private UserService userService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private OAuthService oauthService;

    @MockBean
    private PermissionService permissionService;

    // Exclude security-related beans that @WebMvcTest would try to load
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtProperties jwtProperties;

    @MockBean
    private AuthenticationEntryPointImpl authenticationEntryPoint;

    @MockBean
    private CorsProperties corsProperties;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("POST /auth/login - success returns user and CSRF token")
    void login_success() throws Exception {
        UserVO userVO = new UserVO();
        userVO.setId("user-1");
        userVO.setUsername("testuser");

        LoginResponse response = LoginResponse.builder()
                .csrfToken("test-csrf-token")
                .user(userVO)
                .build();

        when(authService.login(any(LoginDTO.class), any(HttpServletResponse.class)))
                .thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.csrfToken").value("test-csrf-token"))
                .andExpect(jsonPath("$.data.user.username").value("testuser"));
    }

    @Test
    @DisplayName("POST /auth/login - validation error when username is blank")
    void login_validationError_blankUsername() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /auth/me - returns current user with CSRF token")
    void getCurrentUser_success() throws Exception {
        // @WithMockUser provides the Principal that /auth/me requires
        User user = new User();
        user.setId("user-1");
        user.setUsername("testuser");

        UserVO userVO = new UserVO();
        userVO.setId("user-1");

        when(userService.findById("user-1")).thenReturn(Optional.of(user));
        when(userService.toVO(user)).thenReturn(userVO);
        when(csrfService.generateToken("user-1")).thenReturn("csrf-token-123");

        mockMvc.perform(get("/auth/me")
                        .with(user("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.id").value("user-1"))
                .andExpect(jsonPath("$.data.csrfToken").value("csrf-token-123"));
    }
}
```

### Anti-Patterns to Avoid

- **Mocking internal implementation details of request.ts:** Mock the exported `apiGet`/`apiPost` functions, not the internal axios interceptors. The interceptors are tested implicitly through the request.ts module.
- **Testing axios directly in API layer tests:** The API modules use `apiGet`/`apiPost`, not raw axios. Mock at the same level the code imports from.
- **Using @SpringBootTest for controller tests:** Too slow, requires Redis/MySQL. @WebMvcTest is the correct choice per D-08.
- **Forgetting @MockBean for all SecurityConfig dependencies:** @WebMvcTest scans for @Controller but SecurityConfig is @Configuration with @Component dependencies. All must be @MockBean'd or excluded.
- **Testing Pinia stores without setActivePinia:** Pinia stores require an active Pinia instance. Always call `setActivePinia(createPinia())` in `beforeEach`.
- **Not clearing mocks between tests:** Always call `vi.clearAllMocks()` in `beforeEach` to prevent test pollution.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP mocking | Custom fetch/axios spy | `vi.mock('@/utils/request')` | Module mocking is vitest-native, handles async, clean setup |
| Pinia test isolation | Manual store creation | `setActivePinia(createPinia())` | Official Pinia testing pattern, handles reactivity |
| Backend MockMvc setup | Manual MockMvc builder | `@WebMvcTest` + `@MockBean` | Spring Boot auto-configures MockMvc, less boilerplate |
| Assertion library | Custom assertions | AssertJ (backend), vitest expect (frontend) | Rich fluent API, already in project |
| Controller security mocking | Custom filter chains | `@WithMockUser` + `@MockBean` for filters | Spring Security test support handles Principal injection |

**Key insight:** All three areas (console API, management API, backend controller) are boundary tests -- they verify the contract at the edge of each layer. The mocking strategy is always "mock the dependency, verify the boundary behavior."

## Common Pitfalls

### Pitfall 1: Management has no vitest.config.ts
**What goes wrong:** Running `pnpm test` in management will fail or use default config, missing jsdom environment and path aliases.
**Why it happens:** Management's `vite.config.ts` has no `test` block (unlike console which has a full vitest config). The test script exists in package.json but the config does not.
**How to avoid:** Create `management/vitest.config.ts` before writing any tests. Base it on console's config, adapting for management's specifics (no PWA mock needed, no jsx plugin needed).
**Warning signs:** `pnpm test` fails with "Cannot find module" or tests run in Node environment instead of jsdom.

### Pitfall 2: @WebMvcTest loading SecurityConfig components
**What goes wrong:** `@WebMvcTest(AuthController.class)` fails with `NoSuchBeanDefinitionException` or `UnsatisfiedDependencyException` for `JwtTokenProvider`, `StringRedisTemplate`, etc.
**Why it happens:** `SecurityConfig` is a `@Configuration` class that depends on `JwtAuthenticationFilter` (@Component), `CsrfValidationFilter` (instantiated inline with `new`), `AuthenticationEntryPointImpl` (@Component), `CorsProperties` (@Component), and `RateLimitAspect` (@Component/@Aspect). @WebMvcTest loads the web slice including these beans.
**How to avoid:** Add `@MockBean` for every bean that SecurityConfig depends on, OR use `@WebMvcTest(value = AuthController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)`. The @MockBean approach is more reliable because it lets you still test security annotations like `@PreAuthorize`.
**Warning signs:** Test context fails to load with bean creation errors mentioning security classes.

### Pitfall 3: CsrfValidationFilter is NOT a @Component
**What goes wrong:** Trying to @MockBean CsrfValidationFilter fails because it is not a Spring bean.
**Why it happens:** `CsrfValidationFilter` is instantiated directly in `SecurityConfig` with `new CsrfValidationFilter(csrfService)`. It has no `@Component` annotation. [VERIFIED: source code read]
**How to avoid:** When disabling SecurityAutoConfiguration, this is not an issue. When using @MockBean approach, only mock the beans that SecurityConfig actually injects (jwtAuthenticationFilter, authenticationEntryPoint, csrfService, corsProperties).

### Pitfall 4: Auth store initialize() has side effects on module state
**What goes wrong:** Tests for `initialize()` fail because `_initializationPromise` is a module-level closure variable that persists between tests.
**Why it happens:** The auth store uses a closure `_initializationPromise` that is not reset by `setActivePinia(createPinia())`. The `reset()` action clears it, but tests must call `reset()` or create a fresh store.
**How to avoid:** Always call `store.reset()` in `beforeEach` after creating the store, or create the store after mocking.
**Warning signs:** Second test in a describe block fails because initialize() returns the stale promise from the first test.

### Pitfall 5: request.ts has module-level side effects
**What goes wrong:** Mocking `@/utils/request` causes test failures because the module creates an axios instance at import time.
**Why it happens:** `request.ts` creates a `service` axios instance at module level, sets up interceptors, and reads `import.meta.env`. In vitest with jsdom, `import.meta.env` works but `document.cookie` and `window.location` may not behave as expected.
**How to avoid:** Mock the module before importing the API file that depends on it. Use `vi.mock('@/utils/request', () => ({ apiGet: vi.fn(), apiPost: vi.fn(), ... }))` at the top of the test file. For auth store tests that import `@/utils/csrf`, mock that too.
**Warning signs:** "Cannot read property 'cookie' of undefined" or similar DOM errors.

## Code Examples

### Console Auth API Layer Test
```typescript
// console/src/api/__tests__/auth.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { authApi } from '../auth'

vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}))

import { apiGet, apiPost } from '@/utils/request'

describe('authApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('login calls POST /auth/login with credentials', async () => {
    const mockResponse = { csrfToken: 'csrf', user: { id: '1', username: 'u' } }
    vi.mocked(apiPost).mockResolvedValue(mockResponse)
    const result = await authApi.login({ username: 'u', password: 'p' })
    expect(apiPost).toHaveBeenCalledWith('/auth/login', { username: 'u', password: 'p' })
    expect(result).toEqual(mockResponse)
  })

  it('getCurrentUser calls GET /auth/me and returns user', async () => {
    const mockResponse = { user: { id: '1' }, csrfToken: 'c' }
    vi.mocked(apiGet).mockResolvedValue(mockResponse)
    const result = await authApi.getCurrentUser()
    expect(apiGet).toHaveBeenCalledWith('/auth/me')
    expect(result).toEqual({ id: '1' })
  })

  it('logout calls POST /auth/logout', async () => {
    vi.mocked(apiPost).mockResolvedValue(undefined)
    await authApi.logout()
    expect(apiPost).toHaveBeenCalledWith('/auth/logout')
  })
})
```

### Console Problem Detail API Test
```typescript
// console/src/api/__tests__/problem-detail.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchProblemDetailById } from '../problem-detail'

vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
}))

vi.mock('@/api/problem', () => ({
  mapProblem: vi.fn(() => ({
    id: 1,
    title: 'Test Problem',
    slug: 'test-problem',
    difficulty: 'EASY',
  })),
}))

import { apiGet } from '@/utils/request'

describe('fetchProblemDetailById', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('fetches by numeric ID using /problems/{id}', async () => {
    vi.mocked(apiGet).mockResolvedValue({
      summary: 'Test summary',
      examples: [{ id: '1', explanation: 'ex', outputText: '42' }],
    })
    await fetchProblemDetailById(1)
    expect(apiGet).toHaveBeenCalledWith('/problems/1')
  })

  it('fetches by slug using /problems/slug/{slug}', async () => {
    vi.mocked(apiGet).mockResolvedValue({
      summary: 'Test summary',
      examples: [],
    })
    await fetchProblemDetailById('two-sum')
    expect(apiGet).toHaveBeenCalledWith('/problems/slug/two-sum')
  })

  it('appends userId query parameter when provided', async () => {
    vi.mocked(apiGet).mockResolvedValue({
      summary: 'Test',
      examples: [],
    })
    await fetchProblemDetailById(1, 'user-123')
    expect(apiGet).toHaveBeenCalledWith('/problems/1?userId=user-123')
  })
})
```

### Backend ProblemController @WebMvcTest
```java
// backend-spring/src/test/java/com/ulticode/modules/problem/controller/ProblemControllerTest.java
@WebMvcTest(ProblemController.class)
@Import(ProblemController.class)
class ProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProblemService problemService;

    // Security beans that @WebMvcTest needs mocked
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtProperties jwtProperties;
    @MockBean private AuthenticationEntryPointImpl authenticationEntryPoint;
    @MockBean private CorsProperties corsProperties;
    @MockBean private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("GET /problems - returns paginated problem list")
    void listProblems_success() throws Exception {
        PageResult<ProblemVO> pageResult = new PageResult<>();
        pageResult.setItems(List.of());
        pageResult.setTotal(0);

        when(problemService.listProblems(any(ProblemQueryDTO.class)))
                .thenReturn(pageResult);

        mockMvc.perform(get("/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /problems/{id} - returns problem detail")
    void getProblemById_success() throws Exception {
        ProblemDetailResponse detail = new ProblemDetailResponse();
        detail.setId(1L);
        detail.setTitle("Two Sum");

        when(problemService.getProblemDetailResponse(1L)).thenReturn(detail);

        mockMvc.perform(get("/problems/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("Two Sum"));
    }

    @Test
    @DisplayName("POST /problems - requires ADMIN role (403 when unauthenticated)")
    void createProblem_requiresAdmin() throws Exception {
        mockMvc.perform(post("/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test\",\"slug\":\"test\"}"))
                .andExpect(status().isForbidden());
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@SpringBootTest` for controller tests | `@WebMvcTest` slice tests | Spring Boot 2.x+ | Faster tests, no need for full context |
| `@Mock` + `@InjectMocks` | `@MockBean` for Spring context | Spring Boot test framework | Required when using @WebMvcTest MockMvc |
| `@RunWith(SpringRunner.class)` | `@ExtendWith(MockitoExtension.class)` / no runner | JUnit 5 migration | JUnit 5 native, no runner needed for unit tests |
| `vi.fn()` without module mock | `vi.mock()` for module-level mocking | Vitest 0.x+ | Clean module isolation, handles hoisting |

**Deprecated/outdated:**
- JUnit 4 `@RunWith`: Use JUnit 5 `@ExtendWith` [VERIFIED: all existing tests use JUnit 5]
- Manual MockMvc setup: Use `@AutoConfigureMockMvc` or `@WebMvcTest` auto-config [VERIFIED: Spring Boot 3.x standard]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `@WebMvcTest` with `@MockBean` for all SecurityConfig dependencies will work | Backend Testing | Medium - if Spring Boot 3.5 has changed component scanning, may need `excludeAutoConfiguration` instead |
| A2 | Management `pnpm test` currently fails because no vitest.config.ts exists | Management Setup | Low - easy to create, but the test runner might fall back to Vitest defaults |
| A3 | `CsrfValidationFilter` does not need @MockBean because it is not a @Component | Backend Testing | Low - verified by source code read |
| A4 | Auth store's `_initializationPromise` closure can be reset via `store.reset()` | Auth Store Tests | Low - verified by source code read (reset sets `_initializationPromise = null`) |
| A5 | The `@RateLimit` aspect will not fire in @WebMvcTest because we mock StringRedisTemplate | Backend Testing | Medium - if aspect proxying still occurs, tests may fail with NPE |

## Open Questions

1. **Management vitest.config.ts missing**
   - What we know: Management has `pnpm test` script and vitest dependency but no vitest.config.ts
   - What's unclear: Whether management tests currently run at all (the package.json has `--passWithNoTests` flag)
   - Recommendation: Create vitest.config.ts in Wave 0 before writing any tests. Base on console's config minus PWA mock and jsx plugin.

2. **@WebMvcTest security approach**
   - What we know: SecurityConfig has many @Component dependencies that @WebMvcTest would try to load
   - What's unclear: Whether `excludeAutoConfiguration = SecurityAutoConfiguration.class` or `@MockBean` approach is more reliable for this project
   - Recommendation: Start with `@MockBean` approach (lets us test @PreAuthorize annotations). Fall back to excluding security auto-config if bean loading issues arise.

## Environment Availability

> Step 2.6: SKIPPED (no external dependencies identified -- all test tools are project dependencies)

## Validation Architecture

> SKIPPED: `workflow.nyquist_validation` is explicitly `false` in `.planning/config.json`.

## Security Domain

> Not applicable for a testing phase. No new security controls are introduced. Tests verify existing security contracts (auth flow, CSRF tokens, role-based access) but do not implement security features.

## Sources

### Primary (HIGH confidence)
- `console/src/stores/__tests__/editorSettings.spec.ts` -- established Pinia store test pattern
- `management/src/stores/admin/__tests__/moderation.spec.ts` -- established API-mock store test pattern
- `backend-spring/src/test/java/com/ulticode/modules/auth/service/impl/AuthServiceImplTest.java` -- established backend test pattern (Mockito + JUnit 5 + AssertJ)
- `console/src/utils/request.ts` -- API layer architecture (axios wrapper, interceptor behavior)
- `console/src/api/auth.ts` -- Auth API function signatures
- `console/src/stores/auth.ts` -- Auth store state machine and actions
- `backend-spring/src/main/java/.../AuthController.java` -- Controller endpoints and dependencies
- `backend-spring/src/main/java/.../ProblemController.java` -- Controller endpoints and dependencies
- `backend-spring/src/main/java/.../SecurityConfig.java` -- Security filter chain configuration
- `backend-spring/src/main/java/.../common/annotation/RateLimit.java` -- Rate limit annotation
- `backend-spring/src/main/java/.../common/aspect/RateLimitAspect.java` -- Rate limit aspect (@Component)
- `backend-spring/src/main/java/.../security/csrf/CsrfValidationFilter.java` -- NOT @Component (instantiated inline)
- `console/vitest.config.ts` -- Vitest configuration for console
- `console/package.json`, `management/package.json`, `backend-spring/pom.xml` -- dependency versions

### Secondary (MEDIUM confidence)
- [ASSUMED] Spring Boot 3.x @WebMvcTest + @MockBean pattern for security bean exclusion -- standard pattern but untested in this specific project

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all versions verified from package.json/pom.xml
- Architecture: HIGH - all source files read and patterns extracted from existing tests
- Pitfalls: HIGH - identified by reading SecurityConfig dependencies and auth store closure variables

**Research date:** 2026-04-16
**Valid until:** 60 days (testing patterns and Spring Boot annotations are stable)
