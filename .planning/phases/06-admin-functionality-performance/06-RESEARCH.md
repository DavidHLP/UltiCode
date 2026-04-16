# Phase 6: Admin Functionality & Performance - Research

**Researched:** 2026-04-16
**Domain:** Spring Boot 3.5 / MyBatis-Plus 3.5.5 / Docker sandbox code execution
**Confidence:** HIGH

## Summary

Phase 6 addresses five requirements spanning audit trail accuracy, admin panel TODO stubs, moderation analytics, database query optimization, and Docker-based code execution performance. The codebase is well-structured with established patterns for all required changes: `SecurityUtil.getCurrentUserId()` for user resolution, `@Select` annotation-based SQL for aggregation queries, and `ForumCommunityMapper`/`ProblemListProblemMapper` already providing the query methods needed for stub replacements. The most architecturally significant change is PERF-01 (batch test case execution), which requires refactoring `CodeExecutionService.execute()` from a per-test-case Docker container startup model to a single-container batch execution model with a wrapper script that processes all test cases sequentially inside one container.

**Primary recommendation:** Execute tasks in dependency order -- AUDIT-01 first (trivial, builds confidence), then FUNC-02/FUNC-03 (moderate mapper additions), then PERF-02 (significant query rewrites), and PERF-01 last (architecturally most complex).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Use existing `@CurrentUser` annotation in BackupController to inject authenticated user. Pattern already established in 10+ controllers (AuthController, ModerationController, ContestServiceImpl, etc.). Replace `String userId = "system"` with `@CurrentUser User user` parameter, pass `user.getId()`.
- **D-02:** Extend existing MyBatis-Plus mappers with new query methods rather than creating new mapper classes.
- **D-03:** For forum communities query (AdminForumController.getCommunities L136-145), add a mapper method to query forum categories/communities from existing forum tables. Return paginated results matching AdminForumCommunityVO structure.
- **D-04:** For problem count in ProblemListServiceImpl.toSummaryVO L126, use existing problem_list_item table with COUNT query by list_id. Single aggregation query per list.
- **D-05:** For AdminAnalyticsServiceImpl placeholder values (resource usage, performance metrics, slow endpoints, error breakdown in getPerformanceReport L463-501), replace with real system metrics from Spring Actuator endpoints or JVM Runtime MXBean where possible. Where system metrics aren't available in-application (CPU, disk, response times), document as requiring external monitoring integration and return reasonable defaults from actual JVM metrics.
- **D-06:** Calculate from moderation_queue table using SQL TIMESTAMPDIFF between resolved_at and created_at WHERE status='RESOLVED'. Use MyBatis-Plus selectMaps with raw SQL AVG() query. Replace hardcoded `0.0` in ModerationServiceImpl.getStats() L84.
- **D-07:** Replace full-entity-loading patterns in AdminAnalyticsServiceImpl with MyBatis-Plus aggregation queries (selectMaps, selectCount, groupBy). Key methods: getUserActivityReport, getProblemCompletionReport, getContestParticipationReport.
- **D-08:** For retention rate calculation (calculateRetentionRate L512-541), replace per-day entity loading with a single aggregate query using DATE() grouping.
- **D-09:** Refactor CodeExecutionService.execute() to run all test cases in a single Docker container startup. Build a wrapper script that accepts all test cases as JSON array input via stdin, executes each case sequentially inside the container, and returns an array of results.
- **D-10:** Keep individual test case timeout enforcement within the container script. The outer process timeout (sandboxConfig.timeout) applies to the entire batch. Each case gets timeout/batch_size seconds or a configured per-case limit.

### Claude's Discretion
- Exact SQL query structure for each aggregation (planner decides GROUP BY columns, WHERE clauses)
- Error handling for edge cases (no resolved moderation items, empty test case arrays)
- Whether to add new DTO fields for metrics previously unavailable

### Deferred Ideas (OUT OF SCOPE)
None -- analysis stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUDIT-01 | BackupController uses actual authenticated user ID instead of hardcoded "system" | `SecurityUtil.getCurrentUserId()` is the established utility; `BackupController` L42 and L82 have the two hardcoded "system" strings to replace |
| FUNC-02 | Implement 5 Admin TODO stubs with real data | `ForumCommunityMapper` already has full query support; `ProblemListProblemMapper.countByListId()` exists but is not injected; `AdminForumCommunityVO` inner class already defines the target structure |
| FUNC-03 | Implement moderation average resolution time calculation | `ModerationQueueMapper` has pattern for `@Select` aggregation; `ModerationQueue` entity has `resolved_at` and `created_at` fields; `ModerationStatsVO` has `avgResolutionTimeHours` field |
| PERF-01 | Batch test case execution in single Docker container | `CodeExecutionService.execute()` L54-60 for-loop calls `executeInSandbox` per test case; Docker sandbox config (memory, cpus, seccomp, read-only) is well-defined |
| PERF-02 | Admin analytics with database aggregation queries | `AdminAnalyticsServiceImpl` (553 lines) has multiple N+1 patterns; `AuditLogMapper` already demonstrates `@Select` with GROUP BY pattern; MyBatis-Plus 3.5.5 supports `selectMaps` |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Audit trail user resolution | API / Backend | -- | Controller-layer change to inject authenticated user ID from SecurityContext |
| Admin TODO stubs (communities, counts) | API / Backend | Database | Service layer queries database through existing mappers |
| Moderation resolution time | API / Backend | Database | SQL aggregation query on moderation_queue table |
| Admin analytics optimization | API / Backend | Database | Replace Java-side iteration with SQL GROUP BY/COUNT/SUM |
| Batch test case execution | API / Backend | Docker (external process) | Refactor ProcessBuilder Docker invocation from per-case to batch |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.12 | Application framework | Project standard [VERIFIED: pom.xml] |
| MyBatis-Plus | 3.5.5 | ORM / query builder | Project standard, all mappers use this [VERIFIED: pom.xml] |
| Java | 17 | Runtime language | Project standard [VERIFIED: pom.xml, local java --version] |
| Docker | 29.4.0 | Sandbox execution | Project standard for code execution [VERIFIED: docker --version] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Lombok | (managed) | Boilerplate reduction | Already used throughout; continue pattern |
| Spring Security | (managed) | Authentication context | `SecurityUtil.getCurrentUserId()` reads from SecurityContextHolder |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `SecurityUtil.getCurrentUserId()` | `@CurrentUser` annotation | `@CurrentUser` has no registered `HandlerMethodArgumentResolver` -- annotation exists but is not wired into Spring MVC. Only used in I18nController (which may not actually resolve). `SecurityUtil` is the proven, working pattern used in ContestServiceImpl and other services. |

**Installation:** No new packages needed. All changes use existing dependencies.

**Version verification:** All versions confirmed via pom.xml and local toolchain on 2026-04-16.

## Architecture Patterns

### System Architecture Diagram

```
Admin Request Flow (AUDIT-01, FUNC-02, FUNC-03, PERF-02)
=========================================================

  HTTP Request (admin panel)
       |
       v
  [Spring Security Filter Chain]
       |
       v
  [BackupController / AdminForumController / ModerationController / AdminAnalyticsController]
       |
       +-- SecurityUtil.getCurrentUserId() ---> SecurityContextHolder ---> JWT principal
       |
       v
  [Service Layer]
       |
       +-- BackupService.createBackup(actualUserId, dto)
       +-- AdminForumService.getCommunities() ---> ForumCommunityMapper.selectPage()
       +-- AdminProblemListService.toSummaryVO() ---> ProblemListProblemMapper.countByListId()
       +-- ModerationService.getStats() ---> ModerationQueueMapper.avgResolutionTime()
       +-- AdminAnalyticsService.get*Report() ---> Aggregation @Select queries
       |
       v
  [MyBatis-Plus Mappers]
       |
       v
  [MySQL]  ----  aggregation queries (COUNT, AVG, GROUP BY)


Code Execution Flow (PERF-01)
==============================

  Submission Request
       |
       v
  [CodeExecutionService.execute()]
       |
       +-- [CURRENT] for each testCase:
       |       +-- buildDockerCommand(language, code)
       |       +-- ProcessBuilder --> docker run (new container per case)
       |       +-- compare output
       |
       +-- [TARGET] single batch:
               +-- buildBatchDockerCommand(language, code, allTestCases)
               +-- ProcessBuilder --> docker run (ONE container)
               |       |
               |       v
               |   [Wrapper Script inside container]
               |       +-- reads JSON array from stdin
               |       +-- for each testCase:
               |       |       +-- compile code (once for java/c/cpp)
               |       |       +-- execute with test input
               |       |       +-- collect output + timing
               |       +-- writes JSON results array to stdout
               |
               +-- parse results, compare each expected output
               +-- build RunResultDTO with per-case results
```

### Recommended Project Structure

No structural changes needed. All modifications occur within existing files:

```
backend-spring/src/main/java/com/ulticode/
  common/util/SecurityUtil.java              -- (existing, no change)
  modules/backup/controller/BackupController.java  -- AUDIT-01 target
  modules/admin/controller/AdminForumController.java -- FUNC-02 communities stub
  modules/admin/service/impl/AdminProblemListServiceImpl.java -- FUNC-02 problem count
  modules/admin/service/impl/AdminAnalyticsServiceImpl.java -- FUNC-02 placeholders, PERF-02 optimization
  modules/admin/mapper/AuditLogMapper.java    -- PERF-02 add aggregation methods
  modules/moderation/service/impl/ModerationServiceImpl.java -- FUNC-03 avg resolution
  modules/moderation/mapper/ModerationQueueMapper.java -- FUNC-03 add AVG query
  modules/submission/service/CodeExecutionService.java -- PERF-01 batch execution
  modules/submission/config/DockerSandboxConfig.java -- PERF-01 may need per-case timeout config
```

### Pattern 1: SecurityUtil User Resolution (AUDIT-01)
**What:** Replace hardcoded `"system"` string with actual authenticated user ID from Spring Security context.
**When to use:** Any controller or service that needs the current user's identity.
**Example:**
```java
// Source: [VERIFIED: backend-spring ContestServiceImpl.java L141]
// Established pattern in the codebase:
contest.setDeletedBy(SecurityUtil.getCurrentUserId());

// Apply in BackupController:
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<BackupVO> createBackup(@Valid @RequestBody CreateBackupDTO dto) {
    String userId = SecurityUtil.getCurrentUserId();
    if (userId == null) {
        userId = "anonymous"; // Fallback for edge case
    }
    return Result.success(backupService.createBackup(userId, dto));
}
```

**Important finding:** The `@CurrentUser` annotation (D-01 in CONTEXT.md) has NO registered `HandlerMethodArgumentResolver`. The `WebMvcConfig.java` is empty. The annotation exists but cannot resolve parameters. The correct approach is `SecurityUtil.getCurrentUserId()`, which is the pattern used across the codebase (ContestServiceImpl, etc.). The planner should use `SecurityUtil` instead of `@CurrentUser`.

### Pattern 2: MyBatis-Plus @Select Aggregation Queries
**What:** Use `@Select` annotations with raw SQL for aggregation (COUNT, AVG, GROUP BY) instead of loading entities into Java.
**When to use:** Analytics, statistics, and any query that computes a scalar or grouped aggregate.
**Example:**
```java
// Source: [VERIFIED: AuditLogMapper.java L53-59] -- established pattern
@Select("SELECT DATE(created_at) as date, COUNT(DISTINCT performer_id) as count "
    + "FROM audit_logs "
    + "WHERE created_at >= #{startDate} AND created_at < #{endDate} "
    + "GROUP BY DATE(created_at) ORDER BY date")
List<Map<String, Object>> countDailyActiveUsers(
    @Param("startDate") LocalDateTime startDate,
    @Param("endDate") LocalDateTime endDate);
```

### Pattern 3: MyBatis-Plus Pagination with Aggregation
**What:** Use `Page<T>` with `selectPage()` for paginated queries, or custom `@Select` with LIMIT/OFFSET for complex paginated aggregations.
**When to use:** Admin list endpoints that need both total count and paginated results.
**Example:**
```java
// Source: [VERIFIED: AdminProblemListServiceImpl.java L72-73] -- established pattern
Page<ProblemList> pageResult = new Page<>(page, limit);
Page<ProblemList> result = problemListMapper.selectPage(pageResult, wrapper);
return PageResult.of(voList, result.getTotal(), page, limit);
```

### Pattern 4: Docker Sandbox Execution (Current)
**What:** Per-test-case Docker container startup with ProcessBuilder.
**When to use:** Current implementation (to be replaced by batch model).
**Example:**
```java
// Source: [VERIFIED: CodeExecutionService.java L82-137]
// For each test case, a new docker run command is built and executed
for (RunSubmissionDTO.RunTestCase testCase : testCases) {
    RunResultDTO.RunCaseResult caseResult = executeInSandbox(language, code, testCase, runId, userId);
    results.add(caseResult);
}
```

### Anti-Patterns to Avoid
- **Using `@CurrentUser` annotation without resolver:** The annotation is not wired up. Use `SecurityUtil.getCurrentUserId()` instead.
- **Loading full entity lists for COUNT queries:** Use `selectCount()` or `@Select COUNT(*)` instead of `selectList()` followed by `.size()`.
- **N+1 query loops:** The weekly active users loop (L74-92) and peak hours loop (L104-114) in `AdminAnalyticsServiceImpl` are N+1 anti-patterns. Replace with single GROUP BY query.
- **Per-test-case Docker startup:** Each `docker run` pays container creation overhead (~200-500ms). Batch all test cases into a single container invocation.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| User authentication resolution | Custom JWT parsing in controller | `SecurityUtil.getCurrentUserId()` | Already integrated with Spring Security filter chain, handles all edge cases |
| SQL pagination | Manual LIMIT/OFFSET calculation | MyBatis-Plus `Page<T>` + `selectPage()` | Handles total count, page bounds, and dialect automatically |
| Docker process management | Custom thread pools / timeouts | `ProcessBuilder` with `waitFor(timeout, TimeUnit)` | Already in use, handles stdin/stdout piping correctly |
| Aggregate query results | Manual HashMap construction | `@Select` returning `List<Map<String, Object>>` | MyBatis handles type conversion, null values, and result mapping |

**Key insight:** All five requirements can be implemented using existing infrastructure. No new libraries, frameworks, or external services are needed.

## Common Pitfalls

### Pitfall 1: `@CurrentUser` Annotation Not Wired
**What goes wrong:** Using `@CurrentUser String userId` in BackupController will fail at runtime because no `HandlerMethodArgumentResolver` is registered for this annotation.
**Why it happens:** The annotation exists (`CurrentUser.java`) but `WebMvcConfig` and `WebConfig` are both empty -- no `addArgumentResolvers` override registers a resolver.
**How to avoid:** Use `SecurityUtil.getCurrentUserId()` which reads from `SecurityContextHolder` directly.
**Warning signs:** Compile succeeds but runtime throws `MissingServletRequestParameterException` or similar parameter resolution error.

### Pitfall 2: N+1 Queries Hidden in Stream Operations
**What goes wrong:** Replacing `selectList()` + Java stream processing with aggregation queries, but missing a secondary N+1 inside a `.map()` call.
**Why it happens:** In `getProblemCompletionReport` L175-197, the outer stream over problems triggers a per-problem `selectCount()` for each difficulty level. Similarly, the "trending problems" section (L234-258) loads all submissions into memory then iterates.
**How to avoid:** Audit every `.map()` and `.forEach()` inside report methods for database calls. Replace with JOIN or subquery aggregation.
**Warning signs:** Query count grows linearly with data size; slow response on large datasets.

### Pitfall 3: Batch Docker Script Timing Accuracy
**What goes wrong:** Per-test-case timing inside the batch wrapper script is inaccurate because it includes JVM startup or compilation time.
**Why it happens:** For compiled languages (Java, C, C++), the first test case's timing includes compilation overhead. If compilation is done once per batch, only the first case is affected; if done per-case, all cases include it.
**How to avoid:** Separate compilation timing from execution timing in the wrapper script. Report compilation time separately or only measure execution time.
**Warning signs:** All test cases show similar (inflated) timing; timing is much higher than per-container results.

### Pitfall 4: `selectCount` with `groupBy` Does Not Return Distinct Count
**What goes wrong:** `submissionMapper.selectCount(wrapper.with(groupBy))` returns the count of groups, not the count of distinct users.
**Why it happens:** MyBatis-Plus `selectCount` with a `groupBy` clause generates `SELECT COUNT(*) ... GROUP BY`, which returns multiple rows (one per group), but MyBatis-Plus only returns the first row's count value.
**How to avoid:** Use `@Select("SELECT COUNT(DISTINCT user_id) ...")` for distinct counts, or use `@Select` returning `List<Map<String, Object>>` for grouped counts.
**Warning signs:** Retention rate calculations return 1 or 0 instead of actual user counts. This bug already exists in `calculateRetentionRate` (L521-526).

### Pitfall 5: Docker Read-Only Filesystem with Batch Execution
**What goes wrong:** Batch wrapper script fails to write temporary files because the container filesystem is read-only.
**Why it happens:** `CodeExecutionService.buildDockerCommand()` uses `--read-only` with `--tmpfs /tmp:rw,exec,size=64m`. The wrapper script must use `/tmp` for all file operations.
**How to avoid:** Ensure the batch wrapper script writes all intermediate files (compiled binaries, temporary sources) to `/tmp` which is the only writable location.
**Warning signs:** "Read-only file system" errors in container output.

### Pitfall 6: Empty Result Handling in Aggregation Queries
**What goes wrong:** `AVG()` returns `NULL` when no rows match, causing NPE when unboxing to `double`.
**Why it happens:** SQL `AVG()` over an empty result set returns `NULL`. MyBatis maps this to `null` in `Map<String, Object>`, and `((Number) row.get("avg")).doubleValue()` throws NPE.
**How to avoid:** Use `COALESCE(AVG(...), 0)` in SQL, or handle null in Java with null checks.
**Warning signs:** NPE in moderation stats when no resolved items exist; NPE in analytics when no data in date range.

## Code Examples

### AUDIT-01: Replace hardcoded "system" in BackupController
```java
// Source: [VERIFIED: BackupController.java L36-44]
// BEFORE:
public Result<BackupVO> createBackup(@Valid @RequestBody CreateBackupDTO dto) {
    String userId = "system";
    return Result.success(backupService.createBackup(userId, dto));
}

// AFTER:
public Result<BackupVO> createBackup(@Valid @RequestBody CreateBackupDTO dto) {
    String userId = SecurityUtil.getCurrentUserId();
    return Result.success(backupService.createBackup(userId, dto));
}
```

Same pattern applies to `restoreBackup` at L80-84.

### FUNC-02: Problem count in AdminProblemListServiceImpl
```java
// Source: [VERIFIED: AdminProblemListServiceImpl.java L125-127, ProblemListProblemMapper.java L75-76]
// BEFORE:
vo.setProblemCount(0);

// AFTER (inject ProblemListProblemMapper via constructor):
private final ProblemListProblemMapper problemListProblemMapper;

// In toSummaryVO:
vo.setProblemCount((int) problemListProblemMapper.countByListId(list.getId()));
```

### FUNC-02: Forum communities in AdminForumController
```java
// Source: [VERIFIED: AdminForumController.java L136-145, ForumCommunityMapper.java]
// BEFORE:
return Result.success(PageResult.of(java.util.Collections.emptyList(), 0L, page, limit));

// AFTER: Delegate to service, which uses ForumCommunityMapper:
// Option A (direct in controller, simple):
Page<ForumCommunity> pageResult = new Page<>(page, limit);
Page<ForumCommunity> result = forumCommunityMapper.selectPage(pageResult,
    new LambdaQueryWrapper<ForumCommunity>().orderByDesc(ForumCommunity::getMembers));
List<AdminForumCommunityVO> voList = result.getRecords().stream()
    .map(c -> new AdminForumCommunityVO(c.getId(), c.getName(), c.getSlug(),
        c.getDescription(), c.getPostsCount(), c.getMembers()))
    .collect(Collectors.toList());
return Result.success(PageResult.of(voList, result.getTotal(), page, limit));
```

### FUNC-03: Average resolution time SQL
```java
// Source: [VERIFIED: ModerationQueueMapper.java pattern, ModerationQueue entity]
// Add to ModerationQueueMapper:
@Select("SELECT COALESCE(AVG(TIMESTAMPDIFF(HOUR, created_at, resolved_at)), 0) "
    + "FROM moderation_queue "
    + "WHERE status = 'RESOLVED' AND resolved_at IS NOT NULL")
double avgResolutionTimeHours();

// In ModerationServiceImpl.getStats():
stats.setAvgResolutionTimeHours(queueMapper.avgResolutionTimeHours());
```

### PERF-02: Replace N+1 weekly active users with single query
```java
// Source: [VERIFIED: AuditLogMapper.java pattern]
// BEFORE (AdminAnalyticsServiceImpl L74-92): Per-week loop with selectList + distinct count
// AFTER: Add to SubmissionMapper:
@Select("SELECT YEARWEEK(created_at, 3) as week, COUNT(DISTINCT user_id) as count "
    + "FROM submissions "
    + "WHERE created_at >= #{startDate} "
    + "GROUP BY YEARWEEK(created_at, 3) ORDER BY week")
List<Map<String, Object>> countWeeklyActiveUsers(@Param("startDate") LocalDateTime startDate);
```

### PERF-01: Batch execution wrapper script concept
```bash
#!/bin/sh
# Source: [VERIFIED: CodeExecutionService.java L140-187]
# Wrapper script runs inside Docker container, receives JSON array of test cases via stdin
# For interpreted languages (JavaScript, Python): execute code once per test case
# For compiled languages (Java, C, C++): compile once, execute per test case

# Read all test cases from stdin
TEST_CASES=$(cat)

# For each test case, execute and collect result
# Write results as JSON array to stdout
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Per-test-case Docker container | Batch execution in single container | Phase 6 (PERF-01) | Reduces judging latency by ~N * container_overhead (~200-500ms per container) |
| Java-side N+1 iteration | SQL GROUP BY aggregation | Phase 6 (PERF-02) | Reduces memory usage and query count for analytics |
| `@Select` on mapper interface | Same (still best practice) | -- | MyBatis-Plus 3.5.5 fully supports this pattern |
| `selectList` + Java counting | `@Select COUNT(*)` / `selectCount` | Phase 6 (PERF-02) | Eliminates loading full entity lists for simple counts |

**Deprecated/outdated:**
- `@CurrentUser` annotation: Defined but not wired. Use `SecurityUtil.getCurrentUserId()` instead. This should be flagged for removal or proper registration in a future phase.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `@CurrentUser` annotation has no registered argument resolver | Pattern 1 | LOW -- verified by reading WebMvcConfig.java (empty) and WebConfig.java (empty). No `addArgumentResolvers` override exists. |
| A2 | `ModerationQueue.resolved_at` is populated when status changes to 'RESOLVED' | FUNC-03 | MEDIUM -- entity has the field but we haven't verified the service code that sets it. If `resolved_at` is sometimes NULL for RESOLVED items, the AVG query needs a `WHERE resolved_at IS NOT NULL` filter (already included in recommendation). |
| A3 | `ForumCommunity.members` field is a denormalized counter, not a live query | FUNC-02 | LOW -- entity has `members` field and mapper has `incrementMembers`/`decrementMembers`, confirming it's a maintained counter. |
| A4 | `ProblemListProblemMapper.countByListId()` is accurate | FUNC-02 | LOW -- `@Select COUNT(*)` query, straightforward. |
| A5 | Docker sandbox image supports all 5 languages | PERF-01 | HIGH -- verified by reading Dockerfile which installs nodejs, python3, openjdk-17, gcc, g++. Image must be built before use. |
| A6 | Spring Boot 3.5.12 `RuntimeMXBean` provides memory metrics | FUNC-02 (D-05) | LOW -- `ManagementFactory.getRuntimeMXBean().getUptime()` is already used in the codebase (L468). Memory metrics available via `MemoryMXBean`. |

## Open Questions (RESOLVED)

1. **Should the batch wrapper script be a separate file or embedded in the Java code?**
   - What we know: The wrapper needs to handle 5 languages with different compilation/execution models.
   - What's unclear: Whether to create a shell script file in the Docker image or generate it dynamically from Java.
   - Recommendation: Generate the wrapper script dynamically as a string in Java (similar to existing `buildDockerCommand` pattern) and pass it via `sh -c` in the Docker command. This keeps everything in one file and allows language-specific logic.

2. **How to handle per-test-case timeout in batch mode?**
   - What we know: D-10 says each case gets `timeout/batch_size` seconds. The outer `sandboxConfig.timeout` applies to the entire batch.
   - What's unclear: Whether the `DockerSandboxConfig` record needs a new field for per-case timeout, or if we derive it from the existing timeout.
   - Recommendation: Derive per-case timeout as `sandboxConfig.timeout() / testCases.size()` within the Java code and pass it to the wrapper script. No config change needed.

3. **Should we add `ProblemListProblemMapper` to `AdminProblemListServiceImpl` constructor?**
   - What we know: It's not currently injected. `ProblemListMapper` and `ProblemListService` are injected.
   - What's unclear: Whether the planner should inject it directly or go through `ProblemListService`.
   - Recommendation: Inject `ProblemListProblemMapper` directly -- it already has the `countByListId` method, and the admin service pattern in this codebase injects mappers directly.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 17 | Backend compilation | Yes | openjdk 17.0.2 | -- |
| Maven 4.0.0-rc-5 | Backend build | Yes | 4.0.0-rc-5 | `./mvnw` wrapper |
| Docker 29.4.0 | PERF-01 sandbox execution | Yes | 29.4.0 | -- |
| MySQL | Database queries | No (container stopped) | -- | Start with `docker compose up -d` |

**Missing dependencies with no fallback:**
- MySQL container is not running. Needed for integration testing of aggregation queries. Start with `docker compose up -d` before testing.

**Missing dependencies with fallback:**
- None.

## Validation Architecture

> Nyquist validation is disabled in `.planning/config.json` (`workflow.nyquist_validation: false`). Skipping this section.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | `SecurityUtil.getCurrentUserId()` -- reads from SecurityContext after JWT validation |
| V3 Session Management | no | N/A -- this phase doesn't modify session handling |
| V4 Access Control | yes | `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")` already on all admin endpoints |
| V5 Input Validation | yes | SQL injection prevented by parameterized `@Select` queries with `@Param` bindings |
| V6 Cryptography | no | N/A -- no cryptographic operations in this phase |

### Known Threat Patterns for Spring Boot / MyBatis-Plus

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection in raw @Select | Tampering | Use `@Param` bindings (already done), never concatenate user input into SQL strings |
| Privilege escalation via user ID spoofing | Spoofing | `SecurityUtil` reads from server-side SecurityContext, not from client input |
| Docker container escape | Elevation of Privilege | Existing sandbox: `--cap-drop ALL`, `--read-only`, seccomp profile, `--network none`, `--user 1000:1000` |
| Resource exhaustion via batch execution | Denial of Service | Per-case timeout derived from total timeout; memory/CPU limits unchanged from per-case model |

## Sources

### Primary (HIGH confidence)
- `BackupController.java` -- L42, L82 hardcoded "system" strings (verified by reading file)
- `SecurityUtil.java` -- `getCurrentUserId()` implementation (verified by reading file)
- `WebMvcConfig.java` -- Empty class, no argument resolvers (verified by reading file)
- `CodeExecutionService.java` -- Full 361-line file, per-test-case for-loop (verified by reading file)
- `AdminAnalyticsServiceImpl.java` -- Full 553-line file, all N+1 patterns (verified by reading file)
- `ModerationQueue.java` -- Entity with `resolved_at`, `created_at` fields (verified by reading file)
- `ModerationQueueMapper.java` -- Existing `@Select` aggregation pattern (verified by reading file)
- `ForumCommunity.java` -- Entity with `members`, `postsCount` fields (verified by reading file)
- `ForumCommunityMapper.java` -- Full mapper with all query methods (verified by reading file)
- `ProblemListProblemMapper.java` -- `countByListId()` method (verified by reading file)
- `DockerSandboxConfig.java` -- Record with all sandbox parameters (verified by reading file)
- `docker/sandbox/Dockerfile` -- Debian image with nodejs, python3, jdk-17, gcc, g++ (verified by reading file)
- `pom.xml` -- Spring Boot 3.5.12, MyBatis-Plus 3.5.5 (verified by reading file)
- `REQUIREMENTS.md` -- All 5 requirement IDs and descriptions (verified by reading file)
- `06-CONTEXT.md` -- All 10 locked decisions (verified by reading file)

### Secondary (MEDIUM confidence)
- MyBatis-Plus `selectCount` with `groupBy` behavior -- [ASSUMED] based on framework knowledge; `selectCount` returns first row count when GROUP BY produces multiple rows, not the total number of groups.

### Tertiary (LOW confidence)
- None -- all findings verified against source code.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all versions verified from pom.xml and local toolchain
- Architecture: HIGH - all patterns verified from existing source code
- Pitfalls: HIGH - `@CurrentUser` not wired verified from config; N+1 patterns verified from code reading; `selectCount` + groupBy issue based on framework knowledge (MEDIUM)

**Research date:** 2026-04-16
**Valid until:** 30 days (stable phase, no external dependency changes expected)
