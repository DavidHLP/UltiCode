# Phase 6: Admin Functionality & Performance - Context

**Gathered:** 2026-04-16 (auto mode)
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace placeholder/TODO stubs in admin panel with real data, fix audit trail to capture actual authenticated user, optimize admin analytics with database aggregation queries, and batch test case execution in single Docker container startup.

Scope covers: AUDIT-01, FUNC-02, FUNC-03, PERF-01, PERF-02
Does NOT cover: New admin features, UI redesign, new API endpoints beyond replacing stubs

</domain>

<decisions>
## Implementation Decisions

### Audit Trail User Resolution
- **D-01:** Use existing `@CurrentUser` annotation in BackupController to inject authenticated user. Pattern already established in 10+ controllers (AuthController, ModerationController, ContestServiceImpl, etc.). Replace `String userId = "system"` with `@CurrentUser User user` parameter, pass `user.getId()`.

### Admin TODO Stub Implementation
- **D-02:** Extend existing MyBatis-Plus mappers with new query methods rather than creating new mapper classes. All stubs are in admin module which already has access to all entity mappers (forum, problem, moderation, etc.).
- **D-03:** For forum communities query (AdminForumController.getCommunities L136-145), add a mapper method to query forum categories/communities from existing forum tables. Return paginated results matching AdminForumCommunityVO structure.
- **D-04:** For problem count in ProblemListServiceImpl.toSummaryVO L126, use existing problem_list_item table with COUNT query by list_id. Single aggregation query per list.
- **D-05:** For AdminAnalyticsServiceImpl placeholder values (resource usage, performance metrics, slow endpoints, error breakdown in getPerformanceReport L463-501), replace with real system metrics from Spring Actuator endpoints or JVM Runtime MXBean where possible. Where system metrics aren't available in-application (CPU, disk, response times), document as requiring external monitoring integration and return reasonable defaults from actual JVM metrics.

### Moderation Average Resolution Time
- **D-06:** Calculate from moderation_queue table using SQL TIMESTAMPDIFF between resolved_at and created_at WHERE status='RESOLVED'. Use MyBatis-Plus selectMaps with raw SQL AVG() query. Replace hardcoded `0.0` in ModerationServiceImpl.getStats() L84.

### Performance Optimization Approach
- **D-07:** Replace full-entity-loading patterns in AdminAnalyticsServiceImpl with MyBatis-Plus aggregation queries (selectMaps, selectCount, groupBy). Key methods: getUserActivityReport, getProblemCompletionReport, getContestParticipationReport. Use COUNT/SUM/GROUP BY at database level instead of loading entities into Java and iterating.
- **D-08:** For retention rate calculation (calculateRetentionRate L512-541), replace per-day entity loading with a single aggregate query using DATE() grouping.

### Batch Test Case Execution
- **D-09:** Refactor CodeExecutionService.execute() to run all test cases in a single Docker container startup. Build a wrapper script that accepts all test cases as JSON array input via stdin, executes each case sequentially inside the container, and returns an array of results. This eliminates the for-loop at L60-66 that calls executeInSandbox per test case.
- **D-10:** Keep individual test case timeout enforcement within the container script. The outer process timeout (sandboxConfig.timeout) applies to the entire batch. Each case gets timeout/batch_size seconds or a configured per-case limit.

### Claude's Discretion
- Exact SQL query structure for each aggregation (planner decides GROUP BY columns, WHERE clauses)
- Error handling for edge cases (no resolved moderation items, empty test case arrays)
- Whether to add new DTO fields for metrics previously unavailable

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Audit & Authentication
- `backend-spring/src/main/java/com/ulticode/common/annotation/CurrentUser.java` — @CurrentUser annotation definition
- `backend-spring/src/main/java/com/ulticode/modules/backup/controller/BackupController.java` — Target for AUDIT-01 fix (L35-44)

### Admin TODO Stubs
- `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminForumController.java` — getCommunities stub (L136-145)
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java` — problemCount stub (L126)
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminAnalyticsServiceImpl.java` — Full 554-line file with multiple placeholders (L442-501)

### Moderation
- `backend-spring/src/main/java/com/ulticode/modules/moderation/service/impl/ModerationServiceImpl.java` — getStats() with hardcoded 0.0 (L84)
- `backend-spring/src/main/java/com/ulticode/modules/moderation/dto/ModerationStatsVO.java` — avgResolutionTimeHours field

### Performance Optimization
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminAnalyticsServiceImpl.java` — All report methods (L52-461)
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/AdminAnalyticsService.java` — Service interface

### Batch Execution
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` — execute() for-loop (L60-66), executeInSandbox(), buildDockerCommand()
- `backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java` — Sandbox config record

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `@CurrentUser` annotation: Already used in 10+ controllers for injecting authenticated User entity
- MyBatis-Plus LambdaQueryWrapper: Established query pattern across all admin services
- `Result<T>` and `PageResult<T>`: Standard response wrappers used everywhere
- Docker sandbox: Already configured with seccomp, cap-drop ALL, network isolation, memory/CPU limits

### Established Patterns
- Service layer pattern: Controller → Service → Mapper, no direct DB access from controllers
- Constructor injection via `@RequiredArgsConstructor`: All services use this pattern
- Rate limiting: `@RateLimit` annotation on admin endpoints (30 req/60s standard)
- Audit logging: ModerationServiceImpl tracks performedById and reviewedById in action records
- MyBatis-Plus pagination: `Page<T>` + `selectPage()` pattern used consistently

### Integration Points
- BackupService.createBackup(String userId, CreateBackupDTO dto): Needs actual user ID instead of "system"
- AdminForumService: Interface for forum admin operations, getCommunities currently returns empty
- ModerationQueueMapper: Has countPending(), countUnderReview(), countResolvedToday() — add avgResolutionTime query
- AdminAnalyticsServiceImpl: Injects 10+ mappers for cross-module queries — all already available
- CodeExecutionService: ProcessBuilder-based Docker execution with stdin/stdout pipe

</code_context>

<specifics>
## Specific Ideas

- D-19 from prior decisions: "Delayed priority queue — FIFO + rate limiting sufficient, revisit at scale" may affect PERF-01 batch execution design. Current design doesn't need priority queue; batch execution within single submission is sufficient.
- Docker sandbox uses `--read-only`, `--tmpfs /tmp`, seccomp profile — batch execution wrapper must respect these constraints
- The `buildDockerCommand` method constructs language-specific run commands (node -e, python3 -c, javac+java, gcc/g++). Batch execution needs a new entrypoint script that handles multiple test cases.

</specifics>

<deferred>
## Deferred Ideas

None — analysis stayed within phase scope.

</deferred>

---

*Phase: 06-admin-functionality-performance*
*Context gathered: 2026-04-16*
