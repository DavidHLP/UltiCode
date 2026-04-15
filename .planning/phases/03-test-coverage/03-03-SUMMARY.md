---
phase: 03-test-coverage
plan: 03
subsystem: testing
tags: [testcontainers, mybatis-plus, mysql, redis, integration-test, junit5]

# Dependency graph
requires:
  - phase: 03-02
    provides: "Testcontainers BOM 1.21.3 in pom.xml, SubmissionServiceImpl unit tests"
provides:
  - "Testcontainers integration tests for SubmissionServiceImpl with real MySQL + Redis"
  - "Proven manual DataSource + MybatisSqlSessionFactory setup pattern for MyBatis-Plus integration tests"
affects: [future-integration-tests]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Manual MyBatis-Plus SqlSessionFactory with PaginationInnerInterceptor for Testcontainers tests"
    - "SqlSession lifecycle management with @BeforeEach open / @AfterEach close pattern"
    - "DDL schema creation in @BeforeAll against Testcontainers MySQL"

key-files:
  created:
    - backend-spring/src/test/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImplIT.java
  modified: []

key-decisions:
  - "Used manual DataSource + SqlSessionFactory instead of @SpringBootTest to avoid loading Nacos/Dubbo/Redisson"
  - "Kept SqlSession open per test (not try-with-resources) to prevent 'Executor was closed' errors"
  - "Set created_at/updated_at manually on Problem entity since MyBatis-Plus auto-fill handler is not registered without Spring context"
  - "QueueService remained mocked as external service boundary; Redis container provided for future use"

patterns-established:
  - "Testcontainers @Container pattern with MySQLContainer + GenericContainer for Redis"
  - "Integration test DDL matching production Flyway migrations for table accuracy"
  - "@Nested groups for submit() and findById() integration scenarios"

requirements-completed: [TEST-01]

# Metrics
duration: 24min
completed: 2026-04-15
---

# Phase 03 Plan 03: Testcontainers Integration Tests Summary

**5 Testcontainers integration tests for SubmissionServiceImpl verifying persistence to real MySQL and queue failure fallback, with manual MyBatis-Plus SqlSessionFactory setup avoiding full Spring context**

## Performance

- **Duration:** 24 min
- **Started:** 2026-04-15T14:18:12Z
- **Completed:** 2026-04-15T14:42:45Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Created SubmissionServiceImplIT with 5 integration tests using real MySQL 8 and Redis 7 containers
- Verified submission persistence, judge job enqueueing, queue failure fallback, findById retrieval, access control, and not-found behavior against real MySQL
- All 80 phase tests pass together (75 unit + 5 integration)
- Proven manual DataSource + MyBatis-Plus setup pattern for lightweight integration tests

## Task Commits

1. **Task 1: Create SubmissionServiceImplIT with Testcontainers integration tests** - `f88ee0f40` (test)

## Files Created/Modified
- `backend-spring/src/test/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImplIT.java` - 5 integration tests with @Testcontainers, MySQL 8, Redis 7, manual SqlSessionFactory

## Decisions Made
- Used manual DataSource + SqlSessionFactory (not @SpringBootTest) per RESEARCH.md resolved decision -- avoids loading Nacos, Dubbo, Redisson while still having real DB access
- SqlSession kept open per test via @BeforeEach/@AfterEach lifecycle (not try-with-resources) to prevent MyBatis "Executor was closed" errors
- QueueService mocked as external service boundary; Redis container provided for completeness and future use
- DDL derived from V1 + V18 Flyway migrations with foreign key constraints disabled for test data cleanup

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed "Executor was closed" error from try-with-resources SqlSession**
- **Found during:** Task 1 (initial test run)
- **Issue:** Mapper proxies obtained from a SqlSession within try-with-resources became invalid after the block closed the session
- **Fix:** Changed to field-level SqlSession opened in @BeforeEach and closed in @AfterEach
- **Files modified:** SubmissionServiceImplIT.java (setUp/tearDown methods)
- **Verification:** All 5 tests pass after fix

**2. [Rule 3 - Blocking] Fixed "Column 'created_at' cannot be null" on Problem insert**
- **Found during:** Task 1 (second test run)
- **Issue:** MyBatis-Plus FieldFill.INSERT auto-fill handler not registered without Spring context, so created_at and updated_at were null
- **Fix:** Set createdAt and updatedAt explicitly in createTestProblem() helper method
- **Files modified:** SubmissionServiceImplIT.java (createTestProblem method)
- **Verification:** All 5 tests pass after fix

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both auto-fixes necessary for correctness. No scope creep.

## Issues Encountered
- MyBatis-Plus mapper proxies bind to a specific SqlSession/Executor -- closing the session invalidates the proxies (common MyBatis pitfall in non-Spring environments)
- MyBatis-Plus auto-fill (FieldFill.INSERT/INSERT_UPDATE) requires a registered MetaObjectHandler bean, which is only available with Spring context -- manual timestamp setting needed

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 03 (test-coverage) is complete with all 3 plans finished
- TEST-01 requirement satisfied: auth, submission, and code execution modules have unit tests + Testcontainers integration tests
- Manual SqlSessionFactory pattern established for future integration tests
- Testcontainers BOM 1.21.3 actively consumed (was added by 03-02, now used by 03-03)

## Self-Check: PASSED

- `backend-spring/src/test/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImplIT.java`: FOUND
- `f88ee0f40`: FOUND in git log

---
*Phase: 03-test-coverage*
*Completed: 2026-04-15*
