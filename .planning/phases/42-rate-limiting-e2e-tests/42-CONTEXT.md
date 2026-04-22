# Phase 42: Rate Limiting E2E Tests - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

E2E tests for rate limiting with Testcontainers Redis. Verify that rate-limited endpoints return 429 after exceeding their limit, using real Redis for integration testing.

</domain>

<decisions>
## Implementation Decisions

### Test Setup
- **D-01:** Test class location — `backend-spring/src/test/java/com/ulticode/modules/auth/controller/RateLimitIntegrationTest.java`
- **D-02:** Test framework — `@SpringBootTest` + `@AutoConfigureMockMvc` (not @WebMvcTest — need full Spring context + real Redis)
- **D-03:** Testcontainers Redis — Shared container via `@Container` static field, reusing pattern from existing MySQL testcontainer

### Redis Management
- **D-04:** Redis key flushing — `@BeforeEach` method flushes relevant rate-limit keys using `StringRedisTemplate` before each test to avoid false 429s from previous test runs
- **D-05:** Key pattern flush — Flush keys matching `rate-limit:*` before each test to ensure clean state

### Endpoint Coverage
- **D-06:** Primary test case — `POST /auth/register` with `@RateLimit(key = "register", limit = 5, period = 60)` — test sends 6 requests, 7th returns 429
- **D-07:** Secondary test case — `POST /auth/login` with `@RateLimit(key = "login", limit = 10, period = 60)` — verify different tier

### Response Verification
- **D-08:** 429 verification — Assert `status().isTooManyRequests()` (HTTP 429)
- **D-09:** Error response structure — Verify `code` is non-zero and `message` contains rate limit text

### Test Data
- **D-10:** Test user data — Use unique usernames per request (timestamp/nano appended) to avoid "user already exists" false failures

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §TEST-01 — Rate Limiting E2E test criteria

### Prior Phase Context
- `.planning/phases/41-dependency-upgrades/41-CONTEXT.md` — Testcontainers Redis dependency added (D-02: testcontainers-redis:2.2.2)

### Backend Conventions
- `.planning/codebase/CONVENTIONS.md` — Java naming and code style conventions
- `backend-spring/src/test/java/com/ulticode/modules/auth/controller/AuthControllerTest.java` — Existing @WebMvcTest pattern to build on
- `backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java` — @RateLimit annotations on auth endpoints
- `backend-spring/src/main/java/com/ulticode/common/aspect/RateLimitAspect.java` — Rate limit implementation (Redis Lua script)

### Test Patterns
- `backend-spring/pom.xml` — testcontainers-bom (1.11.3), testcontainers, junit-jupiter, mysql already configured

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **AuthControllerTest.java**: Existing @WebMvcTest pattern for auth endpoints — different approach needed for E2E
- **RateLimitAspect.java**: Uses Redis Lua script for atomic INCR + EXPIRE — same implementation under test
- **RateLimit.java annotation**: `@RateLimit(key, limit, period)` — test verifies these values

### Established Patterns
- **Testcontainers usage**: MySQL testcontainer already configured in pom.xml — same pattern for Redis
- **Static container**: `@Container static MySQLContainer<?> mysql` pattern — reuse for Redis

### Integration Points
- **AuthController**: Rate-limited endpoints under test
- **StringRedisTemplate**: Available in Spring context — use for key flushing
- **ErrorCode.TOO_MANY_REQUESTS**: BusinessException thrown when limit exceeded

</code_context>

<specifics>
## Specific Ideas

Test sends 6 requests to `POST /auth/register` with unique email/username, then 7th request should return 429. Before each test, flush Redis keys `rate-limit:register:*` and `rate-limit:login:*` to ensure clean state.

</specifics>

<deferred>
## Deferred Ideas

None — Phase scope is clear and focused on rate limiting E2E tests.

</deferred>

---

*Phase: 42-rate-limiting-e2e-tests*
*Context gathered: 2026-04-22*
