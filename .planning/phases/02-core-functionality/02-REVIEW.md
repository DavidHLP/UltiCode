---
phase: 02-core-functionality
reviewed: 2026-04-15T19:28:00+08:00
depth: standard
files_reviewed: 15
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
  - backend-spring/src/main/java/com/ulticode/modules/submission/service/SubmissionService.java
  - backend-spring/src/main/resources/application.yml
  - backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImplTest.java
  - db-manager/migrations/V18__submission_retry_count.sql
  - docker/sandbox/seccomp-profile.json
findings:
  critical: 2
  warning: 6
  info: 5
  total: 13
status: issues_found
---

# Phase 2: Code Review Report

**Reviewed:** 2026-04-15T19:28:00+08:00
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found

## Summary

Reviewed 15 files spanning the admin submission management, code execution sandbox, submission entity/mapper, configuration, database migration, and test files. Two critical security issues were found in `CodeExecutionService.java` -- a command injection vulnerability in the Java language path and an insecure direct code execution fallback. Six warnings cover data-loading patterns that could cause out-of-memory conditions, incorrect pagination counts after in-memory filtering, and interrupted thread handling. Five informational items address code style and minor improvements.

## Critical Issues

### CR-01: Command injection via user code in Java sandbox execution

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java:169-171`
**Issue:** User-supplied Java code is embedded into a shell command string using `echo '...' > /tmp/Main.java`. The `escapeSingleQuote` method only escapes single quotes via `s.replace("'", "'\\''")`. A user can craft code containing a backslash followed by a single quote (`\'`) to break out of the echo quoting context and execute arbitrary commands on the host. The `sh -c` invocation amplifies this -- the entire string is interpreted by a shell, so any shell metacharacter that survives the escaping (e.g., backtick substitution, `$()` command substitution) could be exploited.

**Fix:**
```java
case "java" -> {
    // Write code to a temp file, then compile and run.
    // Pass code via stdin instead of embedding in shell command.
    String wrapped = wrapJava(code);
    // Option 1: Use base64 encoding to avoid shell interpretation
    String b64 = Base64.getEncoder().encodeToString(
        wrapped.getBytes(StandardCharsets.UTF_8));
    cmd.addAll(List.of("sh", "-c",
        "echo '" + b64 + "' | base64 -d > /tmp/Main.java && "
            + "javac /tmp/Main.java && java -cp /tmp Main"));
}
```
Or better, pipe the code via stdin (similar to C/C++ pattern using `cat > /tmp/Main.java`) instead of embedding it in the command string.

### CR-02: Insecure direct process execution fallback (executeDirect)

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java:193-249`
**Issue:** When `sandboxConfig.enabled()` is `false`, the `executeDirect` method runs user-submitted code directly on the host via `ProcessBuilder` with no containerization, resource limits, or seccomp restrictions. This means arbitrary user code executes with the full privileges of the backend process. The `buildDirectCommand` method constructs commands like `node -e <user_code>` and `python3 -c <user_code>` directly on the host. While the config default is `sandbox.enabled=true`, any misconfiguration or intentional toggle exposes the host to arbitrary code execution.

**Fix:**
```java
// In execute() method, remove the ternary fallback:
// BEFORE:
RunResultDTO.RunCaseResult caseResult = sandboxConfig.enabled()
    ? executeInSandbox(...) : executeDirect(...);

// AFTER:
if (!sandboxConfig.enabled()) {
    throw new BusinessException(ErrorCode.SANDBOX_ERROR,
        "Code execution is disabled: sandbox mode is required");
}
RunResultDTO.RunCaseResult caseResult = executeInSandbox(...);
```
Remove the `executeDirect` and `buildDirectCommand` methods entirely, or gate them behind an explicit development-only profile that cannot be accidentally enabled in production.

## Warnings

### WR-01: getAllSubmissions loads up to 10,000 rows into memory for statistics

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java:400-404`
**Issue:** The `getAllSubmissions()` method loads up to 10,000 submission records into memory (without any field projection -- full `SELECT *`) to compute status and language statistics. This is called from both `getStatistics()` and `getLanguages()`. As the dataset grows, this will cause high memory pressure and slow response times. The code comment acknowledges this but defers the fix.

**Fix:** Use aggregate SQL queries instead:
```java
// Replace getAllSubmissions() for statistics:
@Select("SELECT status, COUNT(*) as count FROM submissions GROUP BY status")
List<Map<String, Object>> countByStatus();

@Select("SELECT language, COUNT(*) as count FROM submissions GROUP BY language ORDER BY count DESC")
List<Map<String, Object>> countByLanguage();

@Select("SELECT DISTINCT language FROM submissions ORDER BY language")
List<String> findDistinctLanguages();
```

### WR-02: Pagination count becomes incorrect after in-memory search filtering

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java:108-138`
**Issue:** When a search query is provided, results are first fetched from the database with pagination (e.g., 10 items from page 1), then filtered in-memory by username/problem title. The `PageResult` is constructed with `result.getTotal()` (the database count), not the filtered count. This means the total count is incorrect and pagination navigation will be broken -- users may see fewer items than expected per page, and total pages will be wrong.

**Fix:** Either:
1. Perform the search filter at the database level using JOINs on users/problems tables.
2. If in-memory filtering is kept, recalculate the total by performing an unpaginated query first, then apply pagination to the filtered list.

### WR-03: batchRejudge processes sequentially with no null guard on ids parameter

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java:314-316`
**Issue:** `ids.size() > 50` is called without a null check. If `ids` is `null`, this throws `NullPointerException`. Additionally, each ID triggers a separate `selectById` + `updateById` + `enqueueJudgeJob` call (N+1 pattern), but this is a performance concern and out of v1 scope.

**Fix:**
```java
@Override
public BatchRejudgeResponse batchRejudge(List<String> ids, boolean notifyUsers) {
    if (ids == null || ids.isEmpty()) {
        BatchRejudgeResponse response = new BatchRejudgeResponse();
        response.setTotal(0);
        response.setSuccessful(0);
        response.setFailed(0);
        response.setResults(new ArrayList<>());
        return response;
    }
    if (ids.size() > 50) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
            "Batch size exceeds maximum of 50");
    }
    // ... rest of method
}
```

### WR-04: getStatistics double-loads all submissions

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java:151-198`
**Issue:** `getStatistics()` calls `getAllSubmissions()` twice -- once at line 160 for status counts and once at line 172 for language counts. Each call issues a separate `selectPage` query loading up to 10,000 full submission records. This doubles the already significant memory and query cost.

**Fix:** Call `getAllSubmissions()` once and reuse the result, or better, replace with aggregate SQL queries as suggested in WR-01.

### WR-05: N+1 queries in toAdminVO for list views

**File:** `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java:345-377`
**Issue:** The `toAdminVO` method calls `userMapper.selectById()` and `problemMapper.selectById()` for every submission in the result set. For a page of 10 submissions, this produces 20 additional database queries. This is the classic N+1 query problem.

**Fix:** Batch-load users and problems before the mapping loop:
```java
// After fetching submissions, collect unique IDs
Set<String> userIds = submissions.stream().map(Submission::getUserId).collect(Collectors.toSet());
Set<Long> problemIds = submissions.stream().map(Submission::getProblemId).collect(Collectors.toSet());

// Batch fetch
Map<String, User> userMap = userMapper.selectBatchIds(userIds).stream()
    .collect(Collectors.toMap(User::getId, u -> u));
Map<Long, Problem> problemMap = problemMapper.selectBatchIds(problemIds).stream()
    .collect(Collectors.toMap(Problem::getId, p -> p));

// Use maps in toAdminVO
```

### WR-06: Thread.currentThread().interrupt() called unconditionally in catch blocks

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java:127-128` and `234-235`
**Issue:** Both `executeInSandbox` and `executeDirect` catch `InterruptedException` in a combined `catch (IOException | InterruptedException e)` block and then call `Thread.currentThread().interrupt()`. However, the interrupt flag is only set for `InterruptedException`, not for `IOException`. When an `IOException` occurs, calling `interrupt()` is harmless but misleading -- it sets the interrupt flag on a thread that was not actually interrupted. The code should differentiate the exception types.

**Fix:**
```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    log.error("Sandbox execution interrupted for language={}", language, e);
    throw new BusinessException(ErrorCode.SANDBOX_ERROR, "Sandbox execution interrupted");
} catch (IOException e) {
    log.error("Sandbox execution I/O failed for language={}", language, e);
    // ... existing error handling
}
```

## Info

### IN-01: DTOs use @Data (mutable) instead of records

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/dto/LanguageStatsDTO.java:9`, `MonthlySubmissionStatsDTO.java:9`, `WeeklyProgressDTO.java:9`
**Issue:** These DTOs are simple value containers with no behavior. Per project Java coding style rules, records should be preferred for immutable value types. However, MyBatis-Plus result mapping may require setter methods depending on the mapper configuration, so this may be a deliberate choice.

**Fix:** Consider converting to records if MyBatis `@Results` mapping supports constructor-based injection, or add a comment explaining why mutable DTOs are used.

### IN-02: Submission entity uses @Data (mutable) with no field validation

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/entity/Submission.java:14-16`
**Issue:** The `Submission` entity uses Lombok `@Data` which generates setters for all fields. Fields like `runtime` (Integer) and `memory` (Double) have no validation -- negative values or absurdly large values could be stored. For a JPA/MyBatis entity this is common, but the service layer should validate before persistence.

**Fix:** Add validation in the service layer when updating submission results (in `updateSubmissionResult`).

### IN-03: application.yml contains database password default in plaintext

**File:** `backend-spring/src/main/resources/application.yml:23`
**Issue:** `password: ${DB_PASSWORD:ulticode}` exposes the default database password `ulticode` in version-controlled source code. While this is a development default and the production value should come from environment variables, having the password in source code is a hygiene concern.

**Fix:** Use an empty default or require the env var to be set: `password: ${DB_PASSWORD:}`

### IN-04: WeeklyProgressDTO.timeSpentHours is derived from runtime but semantically misleading

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java:214`
**Issue:** The SQL for `findWeeklyProgress` calculates `time_spent_hours` as `SUM(runtime) / 3600000.0` where `runtime` is in milliseconds. This sums all submission runtimes (which are code execution times, not user time spent) and calls it "time spent hours." The naming is misleading -- it represents total code execution time, not actual time the user spent solving problems.

**Fix:** Rename the field to `totalRuntimeHours` or `executionTimeHours` to accurately reflect what is being measured.

### IN-05: DockerSandboxConfig is a record but may need @ConfigurationPropertiesScan

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java:5-6`
**Issue:** `DockerSandboxConfig` is a Java record annotated with `@ConfigurationProperties`. This works in Spring Boot 3.x but requires that `@ConfigurationPropertiesScan` or `@EnableConfigurationProperties` is set on a configuration class. If neither is present, the bean will not be registered and injection will fail at startup.

**Fix:** Verify that `@ConfigurationPropertiesScan` is present on the main application class or a configuration class. If it is already present, no action needed.

---

_Reviewed: 2026-04-15T19:28:00+08:00_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
