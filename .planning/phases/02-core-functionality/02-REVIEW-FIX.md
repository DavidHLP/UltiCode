---
phase: 02-core-functionality
reviewed: 2026-04-15T20:15:00+08:00
findings_in_scope: 8
fixed: 8
skipped: 0
iteration: 1
status: all_fixed
---

# Phase 2: Code Review Fix Report

**Reviewed:** 2026-04-15T20:15:00+08:00
**Fix Scope:** critical_warning
**Iteration:** 1
**Status:** all_fixed

## Summary

All 8 findings (2 critical, 6 warning) from the code review have been resolved in a single fix pass. Changes span 3 files: `CodeExecutionService.java`, `AdminSubmissionServiceImpl.java`, and `SubmissionMapper.java`.

## Fixes Applied

### Critical

| ID | Finding | Fix | Commit |
|----|---------|-----|--------|
| CR-01 | Command injection via user code in Java sandbox | Replaced `echo 'code'` shell embedding with base64 encoding to prevent shell metacharacter injection | f97e73f94 |
| CR-02 | Insecure direct process execution fallback | Removed `executeDirect()` and `buildDirectCommand()` methods entirely; sandbox mode now required | f97e73f94 |

### Warnings

| ID | Finding | Fix | Commit |
|----|---------|-----|--------|
| WR-01 | getAllSubmissions loads up to 10,000 rows | Added `countByStatus()`, `countByLanguage()`, `findDistinctLanguages()` aggregate SQL queries to SubmissionMapper; removed `getAllSubmissions()` | f97e73f94 |
| WR-02 | Pagination count incorrect after in-memory filtering | Changed total to use filtered list size instead of database total | f97e73f94 |
| WR-03 | batchRejudge null guard missing | Added null/empty check returning empty BatchRejudgeResponse | f97e73f94 |
| WR-04 | getStatistics double-loads all submissions | Replaced two `getAllSubmissions()` calls with single aggregate SQL query pass | f97e73f94 |
| WR-05 | N+1 queries in toAdminVO | Added batch user/problem loading via `selectBatchIds()` with Map lookups | f97e73f94 |
| WR-06 | Thread.currentThread().interrupt() in combined catch | Separated `InterruptedException` and `IOException` into individual catch blocks | f97e73f94 |

## Skipped Findings

None. All 8 findings in scope were fixed.

## Verification

- Backend compilation: PASSED (`./mvnw compile` succeeds)
- No new imports required beyond `java.util.Base64` and `java.util.HashMap`
- Deleted code: 119 lines (executeDirect, buildDirectCommand, escapeSingleQuote, getAllSubmissions, LANGUAGE_RUNNERS)

---

_Generated: 2026-04-15T20:15:00+08:00_
_Fixer: Claude (gsd-code-fixer)_
