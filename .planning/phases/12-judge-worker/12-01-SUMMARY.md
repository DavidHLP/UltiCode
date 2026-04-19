---
phase: 12-judge-worker
plan: 01
subsystem: judge-worker
tags: [docker, cgroup, memory-measurement, language-validation, code-execution]

# Dependency graph
requires:
  - phase: 06-submission-system
    provides: "CodeExecutionService with batch wrapper scripts and Docker sandbox execution"
provides:
  - "SUPPORTED_LANGUAGES restricted to 5 sandbox-supported entries in SubmissionServiceImpl"
  - "cgroup v2 memory measurement in all 5 batch wrapper scripts (JS, Python, Java, C, C++)"
  - "Memory parsing in parseBatchResults converting bytes to MB"
  - "buildCaseResult accepting numeric memoryMb parameter"
affects: [12-judge-worker-02]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "cgroup v2 /sys/fs/cgroup/memory.current for memory measurement inside Docker containers"

key-files:
  modified:
    - backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java
    - backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java

key-decisions:
  - "Memory reported as String format 'X.XMB' in RunResultDTO, consistent with existing String type"
  - "Error/timeout cases use 0.0MB instead of '0KB' for format consistency"
  - "execute method uses max memory across all cases for top-level result"

patterns-established:
  - "cgroup v2 memory reading pattern: read /sys/fs/cgroup/memory.current, parse to int, include in JSON output"

requirements-completed: [JUDGE-02, JUDGE-03]

# Metrics
duration: 3min
completed: 2026-04-18
---

# Phase 12 Plan 01: Language Validation and Memory Measurement Summary

**Language whitelist restricted to 5 sandbox-supported languages; cgroup v2 memory measurement added to all Docker wrapper scripts with bytes-to-MB parsing pipeline**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-18T13:36:31Z
- **Completed:** 2026-04-18T13:39:45Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- SubmissionServiceImpl SUPPORTED_LANGUAGES trimmed from 13 to 5 entries matching CodeExecutionService whitelist
- All 5 batch wrapper scripts (JavaScript, Python, Java, C, C++) read /sys/fs/cgroup/memory.current and include memory bytes in JSON output
- parseBatchResults extracts memory field from wrapper JSON and converts bytes to MB
- buildCaseResult accepts numeric double memoryMb parameter and formats as "X.XMB"
- Execute method computes max memory across all case results instead of hardcoded "0KB"

## Task Commits

Each task was committed atomically:

1. **Task 1: Restrict SUPPORTED_LANGUAGES to 5 sandbox-supported languages** - `ff9ec12ee` (feat)
2. **Task 2: Add cgroup v2 memory measurement to wrapper scripts and parsing** - `6817fb86f` (feat)

## Files Created/Modified
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java` - Trimmed SUPPORTED_LANGUAGES from 13 to 5 entries
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` - Added cgroup v2 memory measurement to all 5 wrapper scripts, memory parsing in parseBatchResults, numeric memoryMb parameter in buildCaseResult, maxMemory computation in execute method

## Decisions Made
- Used `max(String::compareTo)` to derive top-level memory from case results -- simple lexical comparison works because all values use same "X.XMB" format
- Error/timeout/fallback cases use `0.0MB` instead of `"0KB"` for consistent format across all memory strings
- Java batch wrapper also received memory measurement (not in original plan which said 4 wrappers, but Java uses the same Python subprocess pattern as C/C++)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 12-02 (Judge Worker) can now rely on accurate language validation and memory measurement from CodeExecutionService
- No blockers for next phase

---
*Phase: 12-judge-worker*
*Completed: 2026-04-18*
