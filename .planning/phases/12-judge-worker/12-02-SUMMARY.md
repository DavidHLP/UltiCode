---
phase: 12-judge-worker
plan: 02
subsystem: judge-worker
tags: [redis-queue, scheduled-worker, verdict-determination, retry-backoff, websocket-push, tdd]

# Dependency graph
requires:
  - phase: 12-01
    provides: "Language validation and memory measurement in CodeExecutionService"
  - phase: 06-submission-system
    provides: "QueueService, CodeExecutionService, SubmissionService, RealtimeService, JobProcessor interface"
provides:
  - "JudgeWorkerProcessor polling Redis judge_queue via @Scheduled"
  - "Verdict determination with priority ordering (RE > MLE > TLE > WA > PE > Accepted)"
  - "Retry with exponential backoff (2s, 4s, 8s) for transient failures"
  - "WebSocket push of submission results via RealtimeService"
  - "QueueConfig.judgeEnabled for conditional activation"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@ConditionalOnProperty for conditional bean activation"
    - "AtomicInteger activeJobs guard for scheduler concurrency control"
    - "Verdict priority map for deterministic worst-case determination"
    - "Top-level try/catch in @Scheduled methods to prevent scheduler death"

key-files:
  created:
    - backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java
    - backend-spring/src/test/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessorTest.java
  modified:
    - backend-spring/src/main/java/com/ulticode/modules/queue/config/QueueConfig.java

key-decisions:
  - "Used @ConditionalOnProperty(matchIfMissing=true) so judge worker is enabled by default"
  - "AtomicInteger activeJobs prevents unbounded concurrent job processing"
  - "Top-level try/catch in pollAndProcess prevents scheduler thread death from unhandled exceptions"
  - "Exponential backoff: 2s * 2^attempts with max 3 retries"
  - "Compile errors and SUBMISSION_LANGUAGE_UNSUPPORTED BusinessException are not retried"

patterns-established:
  - "Scheduled worker pattern: @Scheduled pollAndProcess with activeJobs guard and top-level exception catch"
  - "Verdict priority: deterministic worst-case across all test case results"

requirements-completed: [JUDGE-01]

# Metrics
duration: 11min
completed: 2026-04-18
---

# Phase 12 Plan 02: Judge Worker Implementation Summary

**Judge worker that polls Redis judge_queue, executes code via Docker sandbox, determines verdicts with priority ordering, writes results to submissions, and pushes WebSocket notifications to users**

## Performance

- **Duration:** 11 min
- **Started:** 2026-04-18T13:42:30Z
- **Completed:** 2026-04-18T13:54:27Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- JudgeWorkerProcessor polls Redis judge_queue every 1 second via @Scheduled
- Verdict determination uses priority map: RE(5) > MLE(4) > TLE(3) > WA(2) > PE(1) > Accepted(0)
- Retry with exponential backoff (2s, 4s, 8s) for transient failures; compile errors not retried
- WebSocket push via RealtimeService.emitSubmissionResult after every verdict
- AtomicInteger activeJobs guard prevents unbounded concurrency
- Top-level try/catch in pollAndProcess prevents scheduler thread death
- QueueConfig gains judgeEnabled field for conditional activation
- 27 unit tests cover all behaviors (pollAndProcess, processJob, determineVerdict, shouldRetry, onFailure, parseMemoryMb, parseRuntimeMs)

## Task Commits

Each task was committed atomically (TDD: RED -> GREEN):

1. **Task 1 (RED): Add failing tests for JudgeWorkerProcessor** - `10fa245e2` (test)
2. **Task 1 (GREEN): Implement JudgeWorkerProcessor with verdict logic, retry, and WebSocket push** - `07c474a87` (feat)

## Files Created/Modified
- `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java` - Judge worker that polls Redis queue, executes code, determines verdict, pushes WebSocket
- `backend-spring/src/test/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessorTest.java` - 27 unit tests covering all worker behaviors
- `backend-spring/src/main/java/com/ulticode/modules/queue/config/QueueConfig.java` - Added judgeEnabled field (boolean, default true)

## Decisions Made
- Used @ConditionalOnProperty(matchIfMissing=true) so the judge worker activates by default unless explicitly disabled via queue.judge.enabled=false
- AtomicInteger for activeJobs provides lock-free concurrency counting for the scheduler guard
- maxRuntimeMs tracked as long internally to avoid overflow, cast to int only at API boundaries (updateSubmissionResult, pushResult)
- lenient() stubbing on queueConfig.getMaxConcurrentJobs() in tests to avoid UnnecessaryStubbingException in tests that don't call pollAndProcess

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed type mismatch long/int for runtime values**
- **Found during:** Task 1 (GREEN phase compilation)
- **Issue:** parseRuntimeMs returns long but TestCaseDetail.time is Integer, and maxRuntimeMs was declared as int causing Math.max type mismatch
- **Fix:** Changed maxRuntimeMs to long, added (int) cast when passing to Integer parameters
- **Files modified:** JudgeWorkerProcessor.java
- **Verification:** ./mvnw compile -q exits 0

**2. [Rule 1 - Bug] Fixed UnnecessaryStubbingException in tests**
- **Found during:** Task 1 (GREEN phase test run)
- **Issue:** queueConfig.getMaxConcurrentJobs() stub in @BeforeEach caused UnnecessaryStubbingException in 20 of 27 tests that don't call pollAndProcess
- **Fix:** Changed to lenient().when() for that stub
- **Files modified:** JudgeWorkerProcessorTest.java
- **Verification:** All 27 tests pass

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both auto-fixes necessary for compilation and test correctness. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviations above.

## User Setup Required
None - no external service configuration required. Judge worker is enabled by default.

## Next Phase Readiness
- Judge worker is fully functional and will begin processing submissions from the judge_queue
- No blockers for subsequent phases
- Submissions will transition Pending -> Judging -> final verdict automatically

---
*Phase: 12-judge-worker*
*Completed: 2026-04-18*
