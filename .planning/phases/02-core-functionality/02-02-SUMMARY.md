---
phase: 02-core-functionality
plan: 02
subsystem: api
tags: [java, spring-boot, mybatis-plus, queue, admin, rejudge]

# Dependency graph
requires:
  - phase: 01-security
    provides: "Working QueueService with enqueueJudgeJob(), Submission entity, admin controller scaffolding"
provides:
  - "Admin rejudge/batch-rejudge endpoints that enqueue LOW-priority judge jobs"
  - "retryCount field on Submission entity (D-23) with DB migration"
  - "VALIDATION_FAILED error code for batch size enforcement"
  - "Fixed pre-existing compilation errors in SubmissionServiceImpl"
affects: [02-03, any plan using admin submission management]

# Tech tracking
tech-stack:
  added: [ErrorCode.VALIDATION_FAILED, WeeklyProgressDTO, MonthlySubmissionStatsDTO, LanguageStatsDTO]
  patterns: [admin-rejudge-via-queue-service]

key-files:
  created:
    - backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImplTest.java
    - backend-spring/src/main/java/com/ulticode/modules/submission/dto/WeeklyProgressDTO.java
    - backend-spring/src/main/java/com/ulticode/modules/submission/dto/MonthlySubmissionStatsDTO.java
    - backend-spring/src/main/java/com/ulticode/modules/submission/dto/LanguageStatsDTO.java
    - db-manager/migrations/V18__submission_retry_count.sql
  modified:
    - backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java
    - backend-spring/src/main/java/com/ulticode/modules/submission/entity/Submission.java
    - backend-spring/src/main/java/com/ulticode/modules/submission/service/SubmissionService.java
    - backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java
    - backend-spring/src/main/java/com/ulticode/common/exception/ErrorCode.java

key-decisions:
  - "Used 5-arg enqueueJudgeJob() overload matching SubmissionServiceImpl pattern for consistent default limits"
  - "D-24 satisfied by existing worker pipeline; rejudge only resets status and re-enqueues"
  - "VALIDATION_FAILED error code added as generic code 49999 to avoid collision with module-specific codes"

patterns-established:
  - "Admin rejudge: reset status to Pending, increment retryCount, enqueue via QueueService"
  - "Rate limiting on admin operations: 5 req/min for rejudge endpoints"

requirements-completed: [FUNC-01]

# Metrics
duration: 9min
completed: 2026-04-15
---

# Phase 2 Plan 02: Admin Rejudge Summary

**Admin rejudge/batch-rejudge endpoints enqueue LOW-priority judge jobs with retryCount tracking, batch size limit of 50, and 5 req/min rate limiting**

## Performance

- **Duration:** 9 min
- **Started:** 2026-04-15T11:08:24Z
- **Completed:** 2026-04-15T11:18:19Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- rejudge() enqueues judge jobs via QueueService, resets status to Pending, increments retryCount (D-23)
- batchRejudge() validates batch size <= 50 with VALIDATION_FAILED error, iterates per-submission
- Rate limits reduced from 30 to 5 req/min on both rejudge endpoints (T-02-07, T-02-10)
- Fixed 4 pre-existing compilation errors blocking the entire backend from compiling

## Task Commits

Each task was committed atomically:

1. **Task 1: RED - Failing tests** - `8b22cf85b` (test)
2. **Task 1: GREEN - Implementation** - `f5b4ce895` (feat)
3. **Task 2: Rate limit update** - `a2c388710` (feat)

## Files Created/Modified
- `backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImplTest.java` - 9 unit tests for rejudge/batchRejudge
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java` - Working rejudge() and batchRejudge() with QueueService integration
- `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java` - Rate limit reduced to 5/min
- `backend-spring/src/main/java/com/ulticode/modules/submission/entity/Submission.java` - Added retryCount field (D-23)
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/SubmissionService.java` - Added updateSubmissionResult() to interface
- `backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java` - Fixed mapper return types from Object[] to DTOs
- `backend-spring/src/main/java/com/ulticode/modules/submission/dto/WeeklyProgressDTO.java` - New DTO for weekly progress queries
- `backend-spring/src/main/java/com/ulticode/modules/submission/dto/MonthlySubmissionStatsDTO.java` - New DTO for monthly stats queries
- `backend-spring/src/main/java/com/ulticode/modules/submission/dto/LanguageStatsDTO.java` - New DTO for language stats queries
- `backend-spring/src/main/java/com/ulticode/common/exception/ErrorCode.java` - Added VALIDATION_FAILED(49999)
- `db-manager/migrations/V18__submission_retry_count.sql` - Adds retry_count INT DEFAULT 0 column

## Decisions Made
- Used 5-arg `enqueueJudgeJob(String, String, String, String, String)` overload matching the pattern in `SubmissionServiceImpl` (line 109) for consistent default time/memory limits
- `VALIDATION_FAILED` assigned code 49999 (in the generic 4xxxx range but above all module-specific codes to avoid collisions)
- D-24 satisfied by the existing worker pipeline -- rejudge only resets status and re-enqueues; the judge worker calls `updateSubmissionResult()` automatically after processing

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed 4 pre-existing compilation errors in SubmissionServiceImpl**
- **Found during:** Task 1 (before tests could compile)
- **Issue:** Backend would not compile at all due to: (a) missing DTO classes `WeeklyProgressDTO`, `MonthlySubmissionStatsDTO`, `LanguageStatsDTO`, (b) `updateSubmissionResult()` not declared in `SubmissionService` interface, (c) mapper methods returning `List<Object[]>` while service expected typed DTOs
- **Fix:** Created 3 DTO stubs, added method to interface, changed mapper return types with `@Results` annotations
- **Files modified:** SubmissionService.java, SubmissionMapper.java, WeeklyProgressDTO.java, MonthlySubmissionStatsDTO.java, LanguageStatsDTO.java
- **Verification:** `./mvnw compile -q` passes with no errors
- **Committed in:** `f5b4ce895` (part of Task 1 GREEN commit)

**2. [Rule 3 - Blocking] Added retryCount field to Submission entity and DB migration**
- **Found during:** Task 1 (plan assumes field exists but it was missing)
- **Issue:** Plan's `<interfaces>` section shows `retryCount` field and D-23 requires incrementing it, but neither the entity field nor the DB column existed
- **Fix:** Added `@TableField("retry_count") private Integer retryCount = 0` to Submission entity; created V18 migration adding `retry_count INT DEFAULT 0` column
- **Files modified:** Submission.java, V18__submission_retry_count.sql
- **Verification:** Entity compiles, migration SQL is valid
- **Committed in:** `f5b4ce895` (part of Task 1 GREEN commit)

**3. [Rule 2 - Missing Critical] Added VALIDATION_FAILED error code to ErrorCode enum**
- **Found during:** Task 1 (plan references `ErrorCode.VALIDATION_FAILED` which did not exist)
- **Issue:** Plan's batchRejudge implementation uses `throw new BusinessException(ErrorCode.VALIDATION_FAILED, ...)` but this enum value was not defined
- **Fix:** Added `VALIDATION_FAILED(49999, "Validation failed", HttpStatus.BAD_REQUEST)` to ErrorCode enum
- **Files modified:** ErrorCode.java
- **Verification:** Code compiles, test `batchRejudge_exceeds50_throwsValidationFailed` passes
- **Committed in:** `f5b4ce895` (part of Task 1 GREEN commit)

---

**Total deviations:** 3 auto-fixed (2 blocking, 1 missing critical)
**Impact on plan:** All auto-fixes necessary for correctness. The DTO/mapper/interface fixes were pre-existing issues from another wave that blocked any compilation. retryCount and VALIDATION_FAILED were assumed by the plan but missing from the codebase. No scope creep.

## Issues Encountered
- Pre-existing compilation errors in SubmissionServiceImpl prevented the entire backend from compiling. These were type mismatches between mapper return types (`List<Object[]>`) and service expectations (typed DTOs), plus a missing interface method. Fixed as blocking issues to enable test execution.

## TDD Gate Compliance
- RED commit exists: `8b22cf85b` (test)
- GREEN commit exists: `f5b4ce895` (feat)
- All 9 tests pass after GREEN phase

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Admin rejudge endpoints fully functional and tested
- QueueService integration verified via unit tests with mocks
- DB migration V18 must be run before rejudge retryCount tracking works in production

---
*Phase: 02-core-functionality*
*Completed: 2026-04-15*
