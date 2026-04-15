---
phase: 02-core-functionality
reviewed: 2026-04-15T20:30:00+08:00
findings_in_scope: 11
fixed: 11
skipped: 0
iteration: 3
status: all_fixed
---

# Phase 2: Code Review Fix Report

**Reviewed:** 2026-04-15T20:30:00+08:00
**Fix Scope:** critical_warning
**Iterations:** 3 (auto mode, cap reached)
**Status:** all_fixed

## Summary

Fixed 11 findings across 3 iterations (2 critical, 9 warning). Original 8 findings (iteration 1) + 3 new warnings from re-review (iteration 2), all resolved by iteration 3. Changes span 3 files: `CodeExecutionService.java`, `AdminSubmissionServiceImpl.java`, and `SubmissionMapper.java`.

## Iteration 1: Original 8 Findings (commit f97e73f94)

### Critical

| ID | Finding | Fix |
|----|---------|-----|
| CR-01 | Command injection via user code in Java sandbox | Replaced `echo 'code'` shell embedding with base64 encoding |
| CR-02 | Insecure direct process execution fallback | Removed `executeDirect()` / `buildDirectCommand()` entirely; sandbox mode required |

### Warnings

| ID | Finding | Fix |
|----|---------|-----|
| WR-01 | getAllSubmissions loads up to 10,000 rows | Added aggregate SQL queries (`countByStatus`, `countByLanguage`, `findDistinctLanguages`); removed `getAllSubmissions()` |
| WR-02 | Pagination count incorrect after in-memory filtering | Initial fix with filtered count (revised in iteration 3) |
| WR-03 | batchRejudge null guard missing | Added null/empty check returning empty response |
| WR-04 | getStatistics double-loads all submissions | Replaced with single aggregate SQL query pass |
| WR-05 | N+1 queries in toAdminVO | Batch user/problem loading via `selectBatchIds()` with Map lookups |
| WR-06 | Thread.currentThread().interrupt() in combined catch | Separated `InterruptedException` and `IOException` catch blocks |

## Iteration 2: Re-Review Found 3 New Warnings

### Re-review verified all 8 original fixes as correct.

### New Warnings Found

| ID | Finding |
|----|---------|
| WR-NEW-01 | WR-02 fix incomplete — in-memory filtering after SQL pagination still breaks total count |
| WR-NEW-02 | Double wildcards in MyBatis-Plus `.like()` — passing `"%" + value + "%"` when `.like()` already adds `%` |
| WR-NEW-03 | Rejudge enqueues job before DB update — orphaned job risk on DB failure |

## Iteration 3: Final Fixes (commit 0d23b10a1)

| ID | Fix |
|----|-----|
| WR-NEW-01 | Moved ALL search filtering to DB level via pre-fetched user/problem IDs with `IN` clauses; removed in-memory filtering entirely |
| WR-NEW-02 | Removed double wildcards — `.like()` receives raw value, MyBatis-Plus adds `%` automatically |
| WR-NEW-03 | Swapped rejudge order — update DB first, then enqueue job |

## Skipped Findings

None. All 11 findings fixed.

## Verification

- Backend compilation: PASSED at each iteration (`./mvnw compile`)
- Deleted code: 161 lines total (executeDirect, buildDirectCommand, escapeSingleQuote, getAllSubmissions, LANGUAGE_RUNNERS, in-memory filter logic)
- Added code: 58 lines (aggregate queries, batch loading, DB-level search)

---

_Generated: 2026-04-15T20:30:00+08:00_
_Fixer: Claude (gsd-code-fixer)_
