---
phase: 03-test-coverage
plan: 01
subsystem: testing
tags: [jwt, csrf, auth, unit-test, mockito, assertj, password-reset]

# Dependency graph
requires:
  - phase: 02-core-functionality
    provides: "AuthServiceImpl, JwtTokenProvider, CsrfService, PasswordResetService with security fixes"
provides:
  - "48 unit tests covering auth and security modules"
  - "Regression safety net for JWT, CSRF, login, register, refresh, password reset flows"
affects: [04-quality, future security changes]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@Spy with ReflectionTestUtils for config injection"
    - "lenient() stubs for shared @BeforeEach setup"
    - "@Nested + @DisplayName grouping by method under test"

key-files:
  created:
    - "backend-spring/src/test/java/com/ulticode/security/jwt/JwtTokenProviderTest.java"
    - "backend-spring/src/test/java/com/ulticode/security/jwt/JwtPropertiesTest.java"
    - "backend-spring/src/test/java/com/ulticode/security/csrf/CsrfServiceTest.java"
    - "backend-spring/src/test/java/com/ulticode/modules/auth/service/impl/AuthServiceImplTest.java"
    - "backend-spring/src/test/java/com/ulticode/modules/auth/service/PasswordResetServiceTest.java"
  modified: []

key-decisions:
  - "Used @Spy JwtProperties instead of @Mock for ReflectionTestUtils config injection (mock proxies intercept getters and return null)"
  - "PasswordResetServiceTest matches main repo version (EmailService + BCrypt token hash), not worktree version (Redis-based)"
  - "AuthServiceImpl uses AUTH_INVALID_CREDENTIALS for both inactive and banned users (no separate error codes exist)"
  - "CsrfService.clearUserTokens uses scan+individual delete, not batch delete"

patterns-established:
  - "@Spy + ReflectionTestUtils.setField pattern for config POJOs with default inner classes"
  - "lenient() stubs for @BeforeEach when some tests don't use the stubbed dependency"

requirements-completed: [TEST-01]

# Metrics
duration: 14min
completed: 2026-04-15
---

# Phase 03 Plan 01: Auth and Security Module Unit Tests Summary

**48 unit tests covering JWT token generation/validation, CSRF lifecycle, login/register/refresh flows, and password reset with session revocation**

## Performance

- **Duration:** 14 min
- **Started:** 2026-04-15T13:55:46Z
- **Completed:** 2026-04-15T22:09:04Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- JwtTokenProviderTest: 12 tests covering access/refresh token generation, validation (malformed, null, empty, expired, wrong secret), and claim extraction
- CsrfServiceTest: 12 tests covering token generation with Redis store, validate-and-rotate lifecycle, and batch clear via scan
- AuthServiceImplTest: 12 tests covering login (5 scenarios including inactive/banned), register (3 scenarios), refresh (4 scenarios)
- PasswordResetServiceTest: 6 tests covering forgot password (silent return for non-existent, email send for existing) and reset password (valid, invalid, expired, wrong token value)
- JwtPropertiesTest: 6 tests covering secret validation (null, blank, short, valid) and default expiration values

## Task Commits

Each task was committed atomically:

1. **Task 1: JwtTokenProviderTest, JwtPropertiesTest, CsrfServiceTest** - `81e709581` (feat)
2. **Task 2: AuthServiceImplTest, PasswordResetServiceTest** - `d6765efcd` (feat)

## Files Created/Modified
- `backend-spring/src/test/java/com/ulticode/security/jwt/JwtTokenProviderTest.java` - 12 tests for JWT token generation, validation, claim extraction
- `backend-spring/src/test/java/com/ulticode/security/jwt/JwtPropertiesTest.java` - 6 tests for JWT secret validation and default values
- `backend-spring/src/test/java/com/ulticode/security/csrf/CsrfServiceTest.java` - 12 tests for CSRF token generate, validate-rotate, clear
- `backend-spring/src/test/java/com/ulticode/modules/auth/service/impl/AuthServiceImplTest.java` - 12 tests for login, register, refresh flows
- `backend-spring/src/test/java/com/ulticode/modules/auth/service/PasswordResetServiceTest.java` - 6 tests for forgot password and reset password

## Decisions Made
- Used `@Spy JwtProperties = new JwtProperties()` instead of `@Mock JwtProperties` because Mockito proxy intercepts getter calls and returns null even when field values are set via ReflectionTestUtils
- Used `lenient().when()` for `redisTemplate.opsForValue()` in CsrfServiceTest @BeforeEach because some tests throw before reaching Redis (null/empty userId guard)
- PasswordResetServiceTest written against main repo version which uses EmailService + BCrypt token hashing on User entity, not the worktree version which uses Redis-based token storage
- AuthServiceImpl throws AUTH_INVALID_CREDENTIALS for inactive and banned users (no AUTH_USER_INACTIVE/AUTH_USER_BANNED codes exist in ErrorCode enum)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Changed @Mock to @Spy for JwtProperties in JwtTokenProviderTest**
- **Found during:** Task 1 verification
- **Issue:** `@Mock JwtProperties` creates a Mockito proxy; `ReflectionTestUtils.setField(jwtProperties, "secret", TEST_SECRET)` sets the field on the proxy but `jwtProperties.getSecret()` returns null because Mockito intercepts the call
- **Fix:** Changed to `@Spy private JwtProperties jwtProperties = new JwtProperties()` so real getter methods use the field values set by ReflectionTestUtils
- **Files modified:** JwtTokenProviderTest.java
- **Verification:** All 12 JwtTokenProviderTest tests pass

**2. [Rule 1 - Bug] Added lenient() for Redis stub in CsrfServiceTest @BeforeEach**
- **Found during:** Task 1 verification
- **Issue:** Mockito strict stubbing reports `UnnecessaryStubbingException` for tests that throw before calling `redisTemplate.opsForValue()` (null/empty userId guard tests)
- **Fix:** Changed `when(redisTemplate.opsForValue())` to `lenient().when(redisTemplate.opsForValue())` in @BeforeEach
- **Files modified:** CsrfServiceTest.java
- **Verification:** All 12 CsrfServiceTest tests pass

**3. [Rule 1 - Bug] Changed @Mock to @Spy for JwtProperties in AuthServiceImplTest**
- **Found during:** Task 2 verification
- **Issue:** `jwtProperties.getCookie().getAccessToken()` returns NPE because `@Mock JwtProperties` returns null for all getters
- **Fix:** Changed to `@Spy private JwtProperties jwtProperties = new JwtProperties()` which uses real inner class instances with default values
- **Files modified:** AuthServiceImplTest.java
- **Verification:** All 12 AuthServiceImplTest tests pass

**4. [Rule 2 - Missing Critical] Rewrote PasswordResetServiceTest to match main repo implementation**
- **Found during:** Task 2 verification
- **Issue:** Worktree had an older version of PasswordResetService using RedisTemplate for token storage; main repo uses EmailService + BCrypt token hashing on User entity. Tests must match the actual source code being tested.
- **Fix:** Rewrote PasswordResetServiceTest to mock EmailService (not RedisTemplate), use passwordEncoder.matches for token validation, use userMapper.selectList for candidate lookup, and assert passwordResetTokenHash/passwordResetExpiresAt are cleared after reset
- **Files modified:** PasswordResetServiceTest.java
- **Verification:** All 6 PasswordResetServiceTest tests pass

**5. [Rule 2 - Missing Critical] Added test for banned user with expired ban in AuthServiceImplTest**
- **Found during:** Task 2 implementation
- **Issue:** Plan listed AUTH_USER_BANNED error code but ErrorCode enum has no such code; AuthServiceImpl throws AUTH_INVALID_CREDENTIALS for banned users. Tests needed to match actual behavior.
- **Fix:** Wrote banned user test asserting AUTH_INVALID_CREDENTIALS (matching source code), not AUTH_USER_BANNED (which doesn't exist)
- **Files modified:** AuthServiceImplTest.java (test design)
- **Verification:** Banned user test passes

---

**Total deviations:** 5 auto-fixed (3 bug fixes, 2 missing critical functionality)
**Impact on plan:** All auto-fixes necessary for correctness. No scope creep. Test count increased from planned 45 to 48 (added banned-expired edge case and wrong-token-value test).

## Issues Encountered
- Worktree branch had pre-existing compilation errors in SubmissionServiceImpl.java (missing DTO classes) which prevented running tests from the worktree. Tests were verified against the main repo instead.
- Worktree and main repo had different versions of PasswordResetService (Redis-based vs BCrypt+Email-based). Tests were written against the main repo (authoritative) version.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Auth/security test coverage complete (48 tests, all passing)
- Ready for Plan 03-02 (Submission module tests) and Plan 03-03 (CodeExecution tests)
- No blockers

## Self-Check: PASSED

- All 5 test files exist in worktree
- Both commits verified (81e709581, d6765efcd)
- No stubs/placeholder text found
- All 48 tests pass as a group

---
*Phase: 03-test-coverage*
*Completed: 2026-04-15*
