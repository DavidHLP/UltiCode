---
phase: 02-core-functionality
reviewed: 2026-04-15T20:19:00+08:00
depth: standard
files_reviewed: 14
files_reviewed_list:
  - backend-spring/src/main/java/com/ulticode/common/exception/ErrorCode.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/dto/LanguageStatsDTO.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/dto/MonthlySubmissionStatsDTO.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/dto/WeeklyProgressDTO.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/entity/Submission.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java
  - backend-spring/src/main/resources/application.yml
  - backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImplTest.java
  - db-manager/migrations/V18__submission_retry_count.sql
  - docker/sandbox/seccomp-profile.json
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
previous_findings:
  total: 8
  verified_fixed: 8
  partially_fixed: 0
  not_fixed: 0
status: issues_found
---

# Phase 2: Code Review Report (Re-Review)

**Reviewed:** 2026-04-15T20:19:00+08:00
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found
**Previous Review:** 8 findings (2 critical, 6 warning) -- all marked as fixed

## Summary

Re-review of 14 files after fixes were applied for 2 critical and 6 warning findings from the initial review. All 8 previous findings have been verified as properly fixed. The codebase is significantly improved: the dangerous `executeDirect` fallback is gone, command injection via user code is mitigated by base64 encoding, N+1 queries are eliminated in the list view, aggregate SQL replaces full-table loads, and `InterruptedException` is handled correctly.

Three new warnings were identified during re-review. The most significant is a pagination breakage introduced by the WR-02 fix (in-memory search filtering after paginated SQL query produces incorrect totals). The other two are pre-existing issues uncovered during deeper inspection: a double-wildcard bug in MyBatis-Plus `.like()` usage, and a potential partial-state problem in the rejudge flow where the queue job is enqueued before the database update.

## Previous Findings Verification

| ID | Original Issue | Fix Status | Verification |
|----|---------------|------------|-------------|
| CR-01 | Command injection via user code in Java sandbox | FIXED | Base64 encoding at lines 168-173 of CodeExecutionService.java prevents shell metacharacter injection. The base64 alphabet (`A-Za-z0-9+/=`) contains no single quotes, so the `echo '...'` wrapper is safe. |
| CR-02 | Insecure `executeDirect` fallback | FIXED | No trace of `executeDirect`, `buildDirectCommand`, `escapeSingleQuote`, or `LANGUAGE_RUNNERS` anywhere in the codebase. Sandbox mode is now required (line 49-52 throws if disabled). |
| WR-01 | `getAllSubmissions` memory issue (up to 10,000 rows) | FIXED | Method removed. `getStatistics()` now uses `countByStatus()`, `countByLanguage()` aggregate SQL, and `selectCount()` for scalars. No full-table loads remain. |
| WR-02 | Pagination count incorrect after in-memory filtering | PARTIALLY FIXED | Total now uses filtered list size, but this introduces a new pagination breakage. See WR-NEW-01 below. |
| WR-03 | `batchRejudge` null guard missing | FIXED | Lines 328-335 check `ids == null \|\| ids.isEmpty()` and return empty `BatchRejudgeResponse`. Test coverage at lines 188-196 of test file confirms. |
| WR-04 | `getStatistics` double-loads all submissions | FIXED | Single pass using aggregate queries. `selectCount(null)` for total, `countByStatus()` and `countByLanguage()` for groupings, targeted `selectCount` with wrappers for last24h and pending. |
| WR-05 | N+1 queries in `toAdminVO` | FIXED | List view (lines 104-129) batch-loads via `selectBatchIds()` into maps. Single-record view (line 400-432) still uses individual `selectById`, which is acceptable for single-record fetch. |
| WR-06 | `InterruptedException` handling (combined catch) | FIXED | Lines 122-127 separate `InterruptedException` (with `Thread.currentThread().interrupt()`) from `IOException`. |

## Warnings

### WR-NEW-01: Search filter breaks pagination across pages

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java:100-159`
**Issue:** The WR-02 fix replaced the database total with `(long) vos.size()` (line 156), but the search filtering on username/problem title happens in-memory AFTER the SQL query already paginated the results (lines 132-151). This means:
1. SQL returns page-size results (e.g., 10 rows)
2. In-memory filter further reduces those 10 rows to maybe 3
3. Total is reported as 3, totalPages as 1
4. The client cannot navigate to page 2 because totalPages is wrong -- there may be hundreds of matching results on subsequent database pages that were never fetched.

The root cause is that search by username/problem title cannot be done in SQL without a JOIN, so it is done post-pagination. The WR-02 fix is correct in spirit (use filtered count) but the architecture of post-pagination filtering is fundamentally incompatible with multi-page navigation.

**Fix:** Two options:
- **Option A (recommended):** Add a JOIN in the SQL query so username/problem title search happens at the database level, eliminating the need for post-pagination filtering entirely. Then use the database total count.
- **Option B:** When a search query is present, disable pagination (set limit to a large number or use `Integer.MAX_VALUE`), filter in-memory, then manually slice the result list for the requested page. This is simpler but does not scale.

### WR-NEW-02: Double-wildcard in MyBatis-Plus `.like()` call

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java:56`
**Issue:** The `.like()` method in MyBatis-Plus already wraps the value with `%` on both sides. Passing `"%" + query.getSearch() + "%"` produces SQL like `LIKE '%%searchterm%%'`. While this still matches (double `%` is equivalent to single `%`), it indicates the developer may not understand the framework behavior. More importantly, searching submission IDs with LIKE is semantically wrong -- UUIDs should be matched exactly with `.eq()`, not with a LIKE wildcard.

**Fix:**
```java
// Line 56: Use .like() without manual wildcards, or better yet, use .eq() for ID
wrapper.and(w -> w
        .eq(Submission::getId, query.getSearch())  // Exact match for ID
        .or()
        .eq(Submission::getLanguage, query.getSearch()));
```

### WR-NEW-03: Rejudge enqueues job before persisting status change

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java:293-311`
**Issue:** In the `rejudge` method, the queue job is enqueued first (line 296-302), then the submission status is updated to "Pending" (lines 305-311). If the `updateById` call fails (e.g., database error), the job is already in the queue and the worker may process it against a submission still in its old status. While the worker likely re-reads the submission data, the inconsistency between queue state and database state could cause confusion in error scenarios.

**Fix:** Swap the order -- update the database first, then enqueue:
```java
// Update submission status to Pending FIRST
submission.setStatus("Pending");
submission.setRetryCount(
    submission.getRetryCount() != null ? submission.getRetryCount() + 1 : 1
);
submissionMapper.updateById(submission);

// THEN enqueue the judge job
queueService.enqueueJudgeJob(
    submission.getId(),
    String.valueOf(submission.getProblemId()),
    submission.getUserId(),
    submission.getLanguage(),
    submission.getCode()
);
```

## Info

### IN-01: `toAdminVO(Submission)` single-record overload still does N+1 queries

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java:400-432`
**Issue:** The single-argument `toAdminVO` method (used by `toAdminVOWithDetails` for the detail endpoint at line 438) still calls `userMapper.selectById` and `problemMapper.selectById` individually. This is acceptable for a single-record fetch but means two extra queries per detail view. The list-view batch approach is correctly applied in the overloaded version.
**Fix:** Low priority. The batch approach could be applied here too for consistency, but the performance impact is negligible for a single record.

### IN-02: `parseInputValue` manual JSON construction is fragile

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java:266-281`
**Issue:** The `parseInputValue` method manually constructs JSON by appending strings with escaping. While it handles backslashes and double quotes, it does not handle other control characters (newlines, tabs, null bytes) in string values. A value containing `\n` or `\t` would produce invalid JSON.
**Fix:** Use `ObjectMapper` or a JSON library for serialization:
```java
private String parseInputValue(String value) {
    if (value == null) return "null";
    value = value.trim();
    if (value.equals("true") || value.equals("false")) return value;
    if (value.startsWith("[") && value.endsWith("]")) return value;
    try {
        Double.parseDouble(value);
        return value;
    } catch (NumberFormatException e) {
        return new ObjectMapper().writeValueAsString(value);
    }
}
```

### IN-03: `extractFunctionName` returns "solution" fallback silently

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java:231-248`
**Issue:** If no function is found in the user's code, `extractFunctionName` returns "solution" as a fallback. This means the wrapper will call `solution(...)` which will fail at runtime with a "solution is not defined" error. The user will see a confusing runtime error rather than a clear message about the function not being found.
**Fix:** Consider throwing a `BusinessException` with a descriptive message when no function name is detected, or at minimum document the fallback behavior clearly.

### IN-04: `wrapJava` input parsing is naive

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java:212-229`
**Issue:** The Java wrapper parses input by removing the first and last character (assumed brackets) and splitting on comma (line 222-223: `input.substring(1, input.length() - 1)` then `input.split(",")`). This breaks for inputs containing commas inside strings (e.g., `["hello, world"]`). The other language wrappers use proper JSON parsing.
**Fix:** Use `JSONArray` or similar JSON parsing for Java input as well, consistent with the Python and JavaScript wrappers.

---

_Reviewed: 2026-04-15T20:19:00+08:00_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
