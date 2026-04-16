---
phase: 06-admin-functionality-performance
plan: 05
subsystem: api
tags: [docker, sandbox, batch-execution, code-judging, performance]

# Dependency graph
requires: []
provides:
  - Batch Docker execution model for multi-test-case judging
  - Language-specific wrapper scripts (JS, Python, Java, C, C++)
  - Per-case timeout derived from total timeout / batch size
affects: [submission-judging, performance-optimization]

# Tech tracking
tech-stack:
  added: [jackson-objectmapper]
  patterns: [batch-sandbox-execution, wrapper-script-generation]

key-files:
  created: []
  modified:
    - backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java

key-decisions:
  - "Single test case uses existing per-case path (no overhead difference)"
  - "Compiled languages compile once then use Python3 subprocess runner with per-case timeout"
  - "Java source base64-encoded to avoid shell escaping issues in wrapper script"
  - "Batch JSON results extracted via lastIndexOf to handle compilation output prefix"

patterns-established:
  - "Batch wrapper pattern: compile once, execute per case via inline Python3 subprocess runner"
  - "Per-case timeout budget: totalTimeout / batchSize with outer total timeout as safety net"

requirements-completed: [PERF-01]

# Metrics
duration: 4min
completed: 2026-04-16
---

# Phase 6 Plan 05: Batch Docker Execution Summary

**Single-container batch execution for all test cases in CodeExecutionService, eliminating N * container startup overhead**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-16T14:23:02Z
- **Completed:** 2026-04-16T14:27:34Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- Added `executeBatch` method that runs all test cases in a single Docker container via generated wrapper scripts
- Added `buildBatchDockerCommand` preserving all existing security flags (--read-only, --cap-drop ALL, seccomp, --network none, --user 1000:1000)
- Implemented 5 language-specific batch wrapper builders: JavaScript, Python, Java, C, C++
- Compiled languages (Java, C, C++) compile once then use Python3 subprocess runner with per-case timeout enforcement
- Interpreted languages (JavaScript, Python) execute per case within wrapper script with timing
- Modified `execute()` to dispatch: single test case uses existing per-case path, multiple test cases use batch

## Task Commits

Each task was committed atomically:

1. **Task 1: Add batch execution infrastructure methods to CodeExecutionService** - `f412aba74` (feat)
2. **Task 2: Add language-specific wrapper builders and wire execute() to batch mode** - `45349544b` (feat)

## Files Created/Modified
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` - Batch execution infrastructure, language-specific wrapper builders, execute() dispatch

## Decisions Made
- Single test case path preserved via existing `executeInSandbox` method -- no performance gain from batching 1 case
- Compiled languages use Python3 subprocess runner inside the container for per-case timeout enforcement, since the container already has Python3 available
- Java source code is base64-encoded in the wrapper script to avoid shell escaping issues with user code
- Batch results parsed via `lastIndexOf('[')` to handle compilation output that may precede the JSON results array

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added buildWrapperScript stub to enable compilation in Task 1**
- **Found during:** Task 1 (batch execution infrastructure)
- **Issue:** `executeBatch` calls `buildWrapperScript` which was planned for Task 2, causing compilation failure
- **Fix:** Added a temporary stub that throws `SUBMISSION_LANGUAGE_UNSUPPORTED`, replaced with full implementation in Task 2
- **Files modified:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java`
- **Committed in:** `f412aba74` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Minimal -- stub was replaced by full implementation in Task 2. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Batch execution is ready for integration testing with real Docker sandbox
- All existing Docker security constraints preserved in batch path
- Per-case timeout budget mechanism prevents single test case from consuming entire batch time

---
*Phase: 06-admin-functionality-performance*
*Completed: 2026-04-16*
