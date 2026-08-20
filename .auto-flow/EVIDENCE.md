# Evidence

- `mcp__codebase_memory_mcp__index_repository`: project `UltiCode` indexed successfully before repository discovery.
- `scripts/arthas-cli.sh status`: endpoint was initially down; PM2 `ulticode-app` was online on `9103`, not `9001`.
- `java -jar tools/arthas-boot.jar --attach-only --http-port 8563 529384`: attach succeeded; active agent reported `4.3.2`.
- MCP `initialize`: HTTP `200`, `application/json`, no session id.
- MCP `tools/list`: one page, 31 unique tools, no pagination cursor.
- Safe probe matrix: `classloader`, `dashboard`, `getstatic`, `jad`, `jvm`, `mbean`, `ognl` (read-only expression), `options`, `perfcounter`, `profiler status`, `sc`, `sm`, bounded `stack/trace/watch/monitor`, `sysenv`, `sysprop`, `thread`, `tt list`, `version`, `viewfile` allow-list miss, `vmoption`, and bounded `vmtool getInstances`.
- Observed caveats: enhancement probes can wait for invocation; some calls returned structured command errors or closed the socket; endpoint remained listening afterward.
- Artifact validation: Skill validator passed; TOML parsed; Node syntax passed; client `--list` returned 31 and `--call version` returned Arthas 4.3.2; live/reference tool names matched 31/31; `git diff --check` passed.
- Forward-test note: an independent read-only subagent did not return within two waits and was shut down; no files or high-risk tools were touched. Direct runtime smoke tests remain the terminal evidence.
- Completion Audit (2026-08-09): all terminal conditions passed against the objective packet; no Confirmed Findings or unresolved objective-scoped evidence gaps remain.
- Final static checks: `node --check /home/davidhlp/.codex/skills/arthas-mcp-diagnostics/scripts/arthas_mcp_client.mjs`; Python `tomllib` parse of `.codex/agents/arthas-mcp-diagnostics.toml`; required Skill artifacts `4/4` present.
- Final live checks: client `--list` returned `endpoint=http://localhost:8563/mcp tools=31` and `catalog_entries=31`; client `--call version` returned HTTP 200, `isError=false`, and Arthas `4.3.2`.
- Runtime safety: `scripts/arthas-cli.sh status` confirmed MCP port `8563` listening while the legacy Spring Boot `9001` check remained absent; the documented `9103` PM2 target-port caveat was retained. No high-impact operation was executed during close.
- Worktree audit: scoped status showed only the new project Agent; `git diff --check` passed; no business-source diff was introduced by this close pass.
- Delivery: no commit, push, merge, release, deploy, or remote write was authorized or performed.

## Contest Review / Planning Evidence (2026-08-10)

- Code graph project `UltiCode-current` was ready at the archived review generation; the recorded contest evidence covered `services/app`, `services/admin`, `services/api` and `init-db`.
- Current worktree was clean when this plan resumed; no contest business source changes were introduced in this planning pass.
- Current source re-check: `services/admin/pom.xml` still depends on `backend-app`; `AdminContestController` imports `com.ulticode.modules.contest.*`; `ContestCutoverService` is guarded by `app.features.contest-dubbo-cutover` and contains the remote mutation adapter.
- Current schema re-check: `contest_participants` uses `UNIQUE(contest_id,user_id,virtual_session_id)` with nullable `virtual_session_id`; `contest_submissions` has no unique key on `submission_id`; `contests.tie_breaker` SQL enum lacks `TOTAL_TIME` while the Java enum declares it; `CreateSubmissionDTO` has no `contestId`.
- Current source re-check: `ContestSubmissionAdapter` scans active contests and stops at the first match; `ContestServiceImpl` requires parent `RUNNING`; ranking projection maps `problemsSolved` from `attemptCount`; public list/detail paths have different visibility predicates.
- Existing review execution evidence remains: contest domain focused tests passed; app-web contest tests failed at Spring context creation because `ResourceServerJwtVerifier` was missing; Mockito seam coverage does not prove SQL/concurrency/retry/Dubbo/cascade behavior.
- This planning pass did not run implementation tests, migrations, services, commits, pushes, or cutover changes. Those are acceptance evidence for `CONTEST-001..009` only.

## CONTEST-002 Implementation Evidence (2026-08-10)

- `./mvnw -pl app/app-web -am -Dtest=ContestAdjudicationServiceImplTest,SubmissionJudgedContestConsumerTest,SubmissionJudgedInboxBridgeTest,ContestAdjudicationReceiptIT -Dsurefire.failIfNoSpecifiedTests=false test -B`: `BUILD SUCCESS`; 25 tests, 0 failures, 0 errors, 0 skipped.
- MySQL Testcontainers reported Docker 29.7.1 and MySQL 8.0 startup; `ContestAdjudicationReceiptIT` passed duplicate receipt, concurrent same-generation attempt once, and first-solve race using production `problem_id` plus `UNIQUE(contest_id,problem_id)` with two users.
- Correctness/security review agents re-read the scoped implementation after fixes; both returned no remaining confirmed finding.
- `git diff --check` passed before and after the implementation slice.
- Limitation retained for CONTEST-008: the current IT directly exercises the production SQL/concurrency shape rather than booting the full Spring/MyBatis contest service graph.


## CONTEST-003 Implementation Evidence (2026-08-10)

- Added `FINISHING` to the contest state machine and append-only migration `V20260810120000__Add_Contest_Finishing_And_Rating_Receipt.sql`; conditional `RUNNING→FINISHING` and `FINISHING→FINISHED` claims preserve the invariant that published `FINISHED` has completed finalization.
- `ContestLifecycleServiceImplTest`, `ContestSchedulerTest`, and `DefaultContestOwnerPortTest` cover state claims, failure retry, startup recovery delegation, and admin end-command claim-loss behavior. `RatingCalculationServiceImplTest` covers successful receipt then retry as a single application; `BackendAppApplicationTest` confirms the new mapper dependencies wire in the app context.
- Focused command: `./mvnw -pl app/app-web -am -Dtest=ContestSchedulerTest,ContestLifecycleServiceImplTest,DefaultContestOwnerPortTest,RatingCalculationServiceImplTest,ContestAdjudicationServiceImplTest,BackendAppApplicationTest -Dsurefire.failIfNoSpecifiedTests=false test -B` — `BUILD SUCCESS`; app context: 4 tests passed; lifecycle: 12; scheduler: 2; rating: 7; adjudication: 12; owner nested suite included in 55 selected tests.
- Full app-web unit run remains a pre-existing blocker: `1283` tests, `55` errors from WebMvc contexts missing `ResourceServerJwtVerifier`; no failure originated in the contest lifecycle changes. The full Spring/DB integration matrix remains deferred to `CONTEST-008`.
- Review finding R1 was fixed: Admin `endContest` now enters the same conditional `FINISHING` claim and cannot overwrite a concurrent finalization row. Residual design notes: start-side post-claim retry and FINISHING public-list/delete policy remain explicit follow-up scope.


## CONTEST-004 Implementation Evidence (2026-08-10)

- `V20260810130000__Harden_Contest_Admission_And_Registration.sql` remains in `init-db/migrations/`, the current shared-schema Flyway chain. It adds a non-NULL real-registration key, a distinct `uk_virtual_active_admission`, and the `submission_id` uniqueness constraint.
- `ContestSubmissionAdapter` now requires explicit contest context, resolves real/virtual participant identity under row locks, and uses the participant's effective contest clock; ordinary submissions do not scan active contests.
- `ContestParticipationServiceImpl` locks contest then participant for registration/unregistration and only decrements `registered_count` after a successful participant delete.
- `./mvnw -pl app/app-web -am -Dtest=ContestParticipationServiceImplVirtualSessionTest,ContestAdmissionIT -Dsurefire.failIfNoSpecifiedTests=false test -B`: `BUILD SUCCESS`; 22 tests, 0 failures, 0 errors, 0 skipped.
- `ContestAdmissionIT` used MySQL 8.0 Testcontainers and included the legacy `uk_virtual_active` key before applying the new migration; all 4 tests passed, including concurrent real registration and migration composition assertions.
- Review closure: correctness/security/concurrency reviewers reported no remaining confirmed finding for CONTEST-004. Rating updates that span contests remain explicitly mapped to CONTEST-005.
- Scope limitation: a fresh `MIGRATION_SCHEMA=app` database still lacks the complete contest base-table chain; this task does not claim per-owner schema cutover readiness.


## CONTEST-006 Implementation Evidence (2026-08-10)

- `ContestLifecycleServiceImpl.deleteContestCascade` now owns one parent-locked transaction and deletes every contest-owned relation through `ContestCascadeMapper`, including legacy `virtual_contest_sessions`; participant deletion precedes virtual-session cleanup.
- `V20260810150000__Add_Contest_Relational_Guards.sql` audits parent and same-contest child references, adds app-owned FK/composite-key guards (including `ranking_id` and adjudication receipts), and uses an `information_schema`-guarded procedure so Flyway repair/retry does not duplicate already-applied constraints.
- `ContestServiceImpl.removeProblem` rejects referenced contest problems with the established `BAD_REQUEST` contract before `RESTRICT` FKs can surface a generic 5xx.
- `./mvnw -pl app/app-web -am -Dtest='ContestServiceImplTest,ContestLifecycleServiceImplTest,DefaultContestOwnerPortTest,RatingCalculationServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test -B`: `BUILD SUCCESS`; 43 tests, 0 failures, 0 errors.
- `./mvnw -pl app/app-web -am -Dtest=ContestDeletionIT -Dsurefire.failIfNoSpecifiedTests=false test -B`: `BUILD SUCCESS`; 5 MySQL 8.0 Testcontainers tests, including cascade/repeat-delete, migration replay, parent orphan rejection, and cross-contest composite-FK rejection.
- `git diff --check`: passed. Production migration execution, service restart, cutover enablement, commit, and push were not performed.


## CONTEST-008 Integration and Wiring Evidence (2026-08-10)

- Added `ContestAdjudicationWiringIT`: real Spring/MyBatis contest service graph against MySQL 8.0, with real scoring strategies/resolver; only external ports/cache/UUID seams are mocked. It covers accepted persistence, duplicate generation no-op, stale generation no-score, aggregate counters and first-solve state.
- Added `ContestAdministrationWiringIT`: real `ContestAdministrationProvider` → `DefaultContestOwnerPort` → contest mappers and durable `app_command_receipt`; full create command fields, problem batch insert, actor/trace receipt and replay no-op are asserted.
- Fixed `DefaultContestOwnerPort.buildScoredContestProblems` so custom problem batch writes assign UUID and `created_at`/`updated_at`; the real wiring test would otherwise fail on the production `NOT NULL` schema.
- `./mvnw -pl app/app-web -am -Dtest=... -Dsurefire.failIfNoSpecifiedTests=false test -B`: `BUILD SUCCESS`; 112 tests, 0 failures, 0 errors, 0 skipped across contest adjudication, lifecycle, admission, deletion, rating, provider and wiring paths.
- `./mvnw -pl app/app-web -am -Dtest=ContestAdjudicationWiringIT -Dsurefire.failIfNoSpecifiedTests=false test -B`: `BUILD SUCCESS`; 3 MySQL/Spring wiring tests. `ContestAdministrationWiringIT`: `BUILD SUCCESS`; 1 MySQL/Spring/Dubbo-provider wiring test.
- `./mvnw -pl app/app-web -am -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false test -B`: failed only at unrelated `SandboxForkE2EIT.seccompProfile_cloneRulesUseSCMPCMPEQ` because it resolves the missing path `services/app/app-web/docker/sandbox/seccomp-profile.json`; 90 tests, 1 error, 12 skipped. Excluding only that unrelated test (`-Dtest='*IT,!SandboxForkE2EIT'`) passed.
- `./mvnw -pl app/app-web,admin -am verify -B`: blocked before app modules by existing `backend-admin` compilation errors for removed App-private `problem`, `problem-list`, and `submission` packages. This is a CONTEST-009 readiness blocker, not a contest wiring test failure.
- Scoped review found no remaining patch-anchored finding; `git diff --check` passed. Cutover remains disabled and no commit/push/release/service action was performed.


## CONTEST-007 Scope Blocker (2026-08-10)

- Contest Admin HTTP/read/mutation code no longer imports `com.ulticode.modules.contest`; provider/consumer ArchUnit sanity rules are present.
- Removing the aggregate `backend-app` dependency exposes a pre-existing Admin migration gap outside contest: 212 App-private imports across 57 Admin source/test files (`problem`, `problemlist`, `submission`, `solution`, `forum`, `user`, and `vote`).
- `./mvnw -pl admin -am -DskipTests compile -B` fails in `backend-admin`; restoring the old aggregate dependency still fails because the current `backend-app` reactor POM is an aggregate without those implementation classes.
- Focused owner/contest validation after the DTO import fix: `./mvnw -pl app/app-web -am -Dtest=ContestAdministrationProviderTest,ContestPublicControllerTest,DefaultContestOwnerPortTest,ContestServiceImplTest,ContestLifecycleServiceImplTest,RankingServiceImplTest,RatingCalculationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test -B` passed, 60 tests, 0 failures/errors/skips; `git diff --check` passed.
- CONTEST-007 cannot honestly claim its required full Admin compile without either expanding scope to migrate those non-contest consumers or accepting a transitional dependency/legacy module.

## ADMIN-001 Inventory Evidence (2026-08-10)

- Deterministic scan root: `services/admin/src/**/*.java`
- modules.* App-private import count: **213 imports across 61 files** (`main`: 144 imports/42 files; `test`: 69 imports/19 files).
- Domain counts: `forum`=24 imports/10 files, `notification`=13 imports/5 files, `problem`=70 imports/26 files, `problemlist`=55 imports/8 files, `solution`=10 imports/5 files, `submission`=32 imports/19 files, `user`=5 imports/3 files, `vote`=4 imports/2 files; plus 6 non-app-api App-private imports across 3 overlapping files.
- The earlier 57/212 blocker snapshot is stale; `notification` contributes 13 imports across 5 files.

### Full non-public import inventory (modules.* + non-app-api app.*)

| File | Private symbols by family | | Operation / public seam / metadata mapping |
| --- | --- | --- |
| `services/admin/src/main/java/com/ulticode/admin/port/UserProfilePort.java` | `user:UpdateUserDTO`, `user:UserVO` | `profile read/write/avatar` → UserProfileQueryService/ProfileWriteService; Auth AccountQueryService/AccountAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/admin/security/jwt/AccountReadAdapter.java` | `app-private:UserReadMapper`, `app-private:UserSummaryView` | `Auth account/user summary query` → UserProfileQueryService/ProfileWriteService; Auth AccountQueryService/AccountAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/controller/AdminProblemController.java` | `problem:CreateProblemDTO`, `problem:ProblemQueryDTO`, `problem:ProblemVO`, `problem:UpdateProblemDTO`, `problem:ProblemProjection`, `problem:ProblemService`, `submission:Submission` | `owner read/write projection` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/controller/AdminProblemListController.java` | `problemlist:ProblemListDetailVO`, `problemlist:ProblemListSummaryVO`, `problemlist:CreateProblemListDTO`, `problemlist:UpdateBasicInfoDTO`, `problemlist:UpdateBannerDTO`, `problemlist:UpdateProblemListDTO`, `problemlist:UpdateProblemListProblemsDTO`, `problemlist:UpdateVisibilityDTO` | `problem-list read/write` → ProblemListReadPort/ProblemListAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java` | `submission:BatchRejudgeResponse`, `submission:RejudgeResult` | `submission read/rejudge/statistics` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/controller/AdminTestCaseController.java` | `problem:TestCase` | `test-case read/write` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/dto/problem/AdminProblemMapper.java` | `problem:*`, `problem:ProblemDetailPublicVO` | `owner read/write projection` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/dto/problem/CasesDataVO.java` | `problem:ProblemDetailPublicVO` | `owner read/write projection` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/dto/problem/ProblemExampleVO.java` | `problem:ProblemDetailPublicVO` | `owner read/write projection` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/policy/ForumPostFieldToggle.java` | `forum:ForumPost` | `forum/comment moderation/read` → ForumPostReadPort/ForumAdminReadPort; ForumCommentOwnerPort/ForumOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/port/AdminProblemPort.java` | `problem:ProblemVO`, `problem:Problem`, `submission:Submission` | `owner read/write projection` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/port/AdminSubmissionReadPort.java` | `submission:Submission` | `submission read/rejudge/statistics` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminCommentReadAdapter.java` | `forum:ForumPost`, `forum:ForumPostMapper`, `solution:Solution`, `solution:SolutionMapper` | `forum/comment moderation/read` → ForumPostReadPort/ForumAdminReadPort; ForumCommentOwnerPort/ForumOwnerPort; SolutionReadPort/SolutionAdminReadPort; SolutionCommentOwnerPort/SolutionOwnerPort; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminProblemAdapter.java` | `problem:ProblemVO`, `problem:Problem`, `problem:ProblemService`, `submission:Submission`, `submission:SubmissionMapper` | `owner read/write projection` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminSubmissionMapperReadAdapter.java` | `problem:Problem`, `problem:ProblemMapper`, `submission:Submission`, `submission:SubmissionMapper` | `submission read/rejudge/statistics` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminUserProfileAdapter.java` | `app-private:UserProfile`, `app-private:UserProfileMapper`, `user:UpdateUserDTO`, `user:UserVO` | `profile read/write/avatar` → UserProfileQueryService/ProfileWriteService; Auth AccountQueryService/AccountAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DefaultAdminAnalyticsPortAdapter.java` | `submission:Submission`, `submission:SubmissionMapper` | `analytics/user-summary query` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/projection/AdminNotificationProjection.java` | `notification:Notification` | `notification read/write/broadcast` → NotificationAdminReadPort/NotificationAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/projection/AdminProblemListProjection.java` | `problemlist:ProblemListDetailVO`, `problemlist:ProblemListSummaryVO` | `problem-list read/write` → ProblemListReadPort/ProblemListAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminForumProjection.java` | `forum:ForumCommunity`, `forum:ForumPost`, `forum:ForumCommentMapper`, `forum:ForumCommunityMapper`, `forum:ForumPostMapper`, `vote:EdgeOperationTargetType`, `vote:EdgeOperationType`, `vote:EdgeOperationMapper` | `forum/comment moderation/read` → ForumPostReadPort/ForumAdminReadPort; ForumCommentOwnerPort/ForumOwnerPort; ForumVoteReadPort/SolutionVoteReadPort; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminNotificationProjection.java` | `notification:Notification`, `notification:NotificationMapper` | `notification read/write/broadcast` → NotificationAdminReadPort/NotificationAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminProblemListProjection.java` | `problem:ProblemVO`, `problem:Problem`, `problem:ProblemMapper`, `problemlist:ProblemListDetailVO`, `problemlist:ProblemListSummaryVO`, `problemlist:ProblemList`, `problemlist:ProblemListProblemRelation`, `problemlist:ProblemListMapper`, `problemlist:ProblemListProblemMapper` | `problem-list read/write` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; ProblemListReadPort/ProblemListAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminSolutionProjection.java` | `problem:Problem`, `problem:ProblemMapper`, `solution:Solution`, `solution:SolutionMapper` | `solution/comment read/moderation` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; SolutionReadPort/SolutionAdminReadPort; SolutionCommentOwnerPort/SolutionOwnerPort; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminSubmissionProjection.java` | `problem:Problem`, `problem:ProblemMapper`, `submission:Submission` | `submission read/rejudge/statistics` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultUserActivityAnalyticsProjection.java` | `app-private:UserReadMapper`, `app-private:UserSummaryView` | `analytics/user-summary query` → UserProfileQueryService/ProfileWriteService; Auth AccountQueryService/AccountAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/AdminProblemListService.java` | `problemlist:ProblemListDetailVO`, `problemlist:ProblemListSummaryVO`, `problemlist:CreateProblemListDTO`, `problemlist:UpdateBasicInfoDTO`, `problemlist:UpdateBannerDTO`, `problemlist:UpdateProblemListDTO`, `problemlist:UpdateProblemListProblemsDTO`, `problemlist:UpdateVisibilityDTO` | `problem-list read/write` → ProblemListReadPort/ProblemListAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/AdminProblemService.java` | `problem:ProblemVO`, `submission:Submission` | `owner read/write projection` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/AdminSubmissionService.java` | `submission:BatchRejudgeResponse`, `submission:RejudgeResult` | `submission read/rejudge/statistics` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/AdminTestCaseService.java` | `problem:TestCase`, `problem:ProblemMapper`, `problem:TestCaseMapper` | `test-case read/write` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/ProblemCutoverService.java` | `problem:CreateProblemDTO`, `problem:ProblemVO`, `problem:UpdateProblemDTO`, `problem:ProblemService` | `owner read/write projection` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/ProblemExportService.java` | `problem:ProblemQueryDTO` | `problem export read` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/SubmissionCutoverService.java` | `submission:BatchRejudgeResponse`, `submission:RejudgeResult` | `submission read/rejudge/statistics` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/comment/ForumCommentModerator.java` | `forum:ForumComment`, `forum:ForumCommentMapper` | `forum/comment moderation/read` → ForumPostReadPort/ForumAdminReadPort; ForumCommentOwnerPort/ForumOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/comment/SolutionCommentModerator.java` | `solution:SolutionComment`, `solution:SolutionCommentMapper` | `solution/comment read/moderation` → SolutionReadPort/SolutionAdminReadPort; SolutionCommentOwnerPort/SolutionOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/handler/ForumTagHandler.java` | `forum:ForumTag`, `forum:ForumTagMapper` | `tag read/write` → ForumPostReadPort/ForumAdminReadPort; ForumCommentOwnerPort/ForumOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/handler/ProblemTagHandler.java` | `problem:ProblemTag`, `problem:ProblemTagRelation`, `problem:ProblemTagMapper`, `problem:ProblemTagRelationMapper` | `tag read/write` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java` | `forum:ForumPost`, `forum:ForumPostMapper` | `forum/comment moderation/read` → ForumPostReadPort/ForumAdminReadPort; ForumCommentOwnerPort/ForumOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminNotificationServiceImpl.java` | `notification:AnnouncementBroadcaster`, `notification:Notification`, `notification:NotificationCategory`, `notification:NotificationMapper` | `notification read/write/broadcast` → NotificationAdminReadPort/NotificationAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java` | `problemlist:ProblemListDetailVO`, `problemlist:ProblemListSummaryVO`, `problemlist:CreateProblemListDTO`, `problemlist:UpdateBasicInfoDTO`, `problemlist:UpdateBannerDTO`, `problemlist:UpdateProblemListDTO`, `problemlist:UpdateProblemListProblemsDTO`, `problemlist:UpdateVisibilityDTO`, `problemlist:ProblemList`, `problemlist:ProblemListAdminService`, `problemlist:ProblemListService` | `problem-list read/write` → ProblemListReadPort/ProblemListAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemServiceImpl.java` | `problem:ProblemVO`, `problem:Problem`, `problem:ProblemDetail`, `problem:ProblemExample`, `problem:ProblemLanguage`, `problem:ProblemDetailMapper`, `problem:ProblemExampleMapper`, `problem:ProblemLanguageMapper`, `problem:ProblemMapper`, `problem:ProblemTagMapper`, `problem:ProblemTagRelationMapper`, `submission:Submission` | `owner read/write projection` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java` | `submission:BatchRejudgeResponse`, `submission:RejudgeResult`, `submission:Submission` | `submission read/rejudge/statistics` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/ProblemExportServiceImpl.java` | `problem:ProblemQueryDTO`, `problem:ProblemVO`, `problem:ProblemProjection` | `problem export read` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/ProblemImportServiceImpl.java` | `problem:Problem` | `problem import/write` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/UserManagementServiceImpl.java` | `user:UpdateUserDTO` | `profile read/write/avatar` → UserProfileQueryService/ProfileWriteService; Auth AccountQueryService/AccountAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/controller/AdminProblemListControllerTest.java` | `problemlist:ProblemListSummaryVO`, `problemlist:UpdateBasicInfoDTO`, `problemlist:UpdateBannerDTO`, `problemlist:UpdateVisibilityDTO` | `problem-list read/write` → ProblemListReadPort/ProblemListAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/controller/AdminSubmissionControllerTest.java` | `submission:BatchRejudgeResponse`, `submission:RejudgeResult` | `submission read/rejudge/statistics` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/port/adapter/AdminCommentReadAdapterTest.java` | `forum:ForumPost`, `forum:ForumPostMapper`, `solution:Solution`, `solution:SolutionMapper` | `forum/comment moderation/read` → ForumPostReadPort/ForumAdminReadPort; ForumCommentOwnerPort/ForumOwnerPort; SolutionReadPort/SolutionAdminReadPort; SolutionCommentOwnerPort/SolutionOwnerPort; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/projection/AdminForumProjectionTest.java` | `forum:ForumPost`, `forum:ForumCommentMapper`, `forum:ForumCommunityMapper`, `forum:ForumPostMapper`, `vote:EdgeOperationMapper` | `forum/comment moderation/read` → ForumPostReadPort/ForumAdminReadPort; ForumCommentOwnerPort/ForumOwnerPort; ForumVoteReadPort/SolutionVoteReadPort; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/projection/AdminNotificationProjectionTest.java` | `notification:Notification`, `notification:NotificationMapper` | `notification read/write/broadcast` → NotificationAdminReadPort/NotificationAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/projection/AdminSolutionProjectionTest.java` | `problem:Problem`, `problem:ProblemMapper`, `solution:Solution`, `solution:SolutionMapper` | `solution/comment read/moderation` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; SolutionReadPort/SolutionAdminReadPort; SolutionCommentOwnerPort/SolutionOwnerPort; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/projection/AdminSubmissionProjectionTest.java` | `problem:Problem`, `problem:ProblemMapper`, `submission:Submission` | `submission read/rejudge/statistics` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/projection/DefaultAdminProblemListProjectionTest.java` | `problemlist:ProblemListDetailVO`, `problemlist:ProblemListSummaryVO`, `problemlist:ProblemList`, `problemlist:ProblemListMapper`, `problemlist:ProblemListProblemMapper`, `problem:ProblemMapper` | `problem-list read/write` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; ProblemListReadPort/ProblemListAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/AdminTestCaseServiceTest.java` | `problem:Problem`, `problem:TestCase`, `problem:ProblemMapper`, `problem:TestCaseMapper` | `test-case read/write` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/ContestSubmissionCutoverServiceTest.java` | `submission:RejudgeResult` | `submission read/rejudge/statistics` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/NotificationCutoverServiceTest.java` | `submission:BatchRejudgeResponse`, `submission:RejudgeResult` | `notification read/write/broadcast` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/ProblemCutoverServiceTest.java` | `problem:CreateProblemDTO`, `problem:ProblemVO`, `problem:UpdateProblemDTO`, `problem:ProblemService` | `owner read/write projection` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImplTest.java` | `forum:ForumPost`, `forum:ForumPostMapper` | `forum/comment moderation/read` → ForumPostReadPort/ForumAdminReadPort; ForumCommentOwnerPort/ForumOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/impl/AdminNotificationServiceImplTest.java` | `notification:AnnouncementBroadcaster`, `notification:Notification`, `notification:NotificationCategory`, `notification:NotificationMapper` | `notification read/write/broadcast` → NotificationAdminReadPort/NotificationAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImplTest.java` | `problemlist:ProblemListDetailVO`, `problemlist:ProblemListSummaryVO`, `problemlist:CreateProblemListDTO`, `problemlist:UpdateBannerDTO`, `problemlist:UpdateBasicInfoDTO`, `problemlist:UpdateProblemListDTO`, `problemlist:UpdateProblemListProblemsDTO`, `problemlist:UpdateVisibilityDTO`, `problemlist:ProblemList`, `problemlist:ProblemListAdminService`, `problemlist:ProblemListService` | `problem-list read/write` → ProblemListReadPort/ProblemListAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImplTest.java` | `submission:BatchRejudgeResponse`, `submission:RejudgeResult`, `submission:Submission` | `submission read/rejudge/statistics` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/impl/AdminTagServiceImplTest.java` | `forum:ForumTag`, `forum:ForumTagMapper`, `problem:ProblemTag`, `problem:ProblemTagMapper`, `problem:ProblemTagRelationMapper` | `tag read/write` → ForumPostReadPort/ForumAdminReadPort; ForumCommentOwnerPort/ForumOwnerPort; ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/impl/ProblemImportServiceImplTest.java` | `problem:Problem` | `problem import/write` → ProblemFactsPort/ProblemAdminReadPort; ProblemAdministrationService; TestCaseOwnerPort; read: RpcPolicy query; write: commandId + idempotencyKey + actor/creator/trace; errors: explicit RpcResult mapping |
| `services/admin/src/test/java/com/ulticode/modules/admin/service/impl/RejudgeConcurrencyIT.java` | `submission:Submission`, `submission:SubmissionMapper` | `submission read/rejudge/statistics` → SubmissionReadPort/SubmissionAnalyticsPort/SubmissionGenerationReadPort; SubmissionAdministrationService; read: bounded typed query + RpcPolicy; no Entity/Mapper; errors: explicit owner error mapping |

### Contract coverage ledger

| Family | Existing public seams | Required gap / implementation seam |
| --- | --- | --- |
| `user` | `UserProfileQueryService`, `ProfileWriteService`, `UserProfileDTO`, `UpdateProfileCommand`, Auth account contracts | Replace `UserProfilePort`/legacy DTOs; replace `UserReadMapper`, `UserSummaryView`, `UserProfileMapper`, `UserProfile` with typed public queries; preserve principal-derived audit identity |
| `problem` | `ProblemAdministrationService`, `ProblemFactsPort`, `ProblemTagReadPort`, `ProblemExampleReadPort`, `ProblemJudgingCaseReadPort`, `ProblemAnalyticsReadPort`, `ProblemSearchReadPort`, `TestCaseOwnerPort` | Add bounded `ProblemAdminReadPort`/missing operation providers for read, export, tags and test cases |
| `submission` | `SubmissionAdministrationService`, `SubmissionReadPort`, `SubmissionAnalyticsPort`, `SubmissionUserStatsPort`, `SubmissionGenerationReadPort` | Add Admin read/provider seam and replace direct `Submission`/`SubmissionMapper` usage |
| `problemlist` | `ProblemListReadPort` | Add provider-owned `ProblemListAdministrationService` and `ProblemListAdminReadPort` |
| `solution` | `SolutionReadPort`, `SolutionOwnerPort`, `SolutionCommentOwnerPort`, `SolutionVoteReadPort` | Add bounded `SolutionAdminReadPort` where existing ports do not cover Admin projection |
| `forum`/`vote` | `ForumOwnerPort`, `ForumPostReadPort`, `ForumCommentOwnerPort`, `ForumVoteReadPort`, `SolutionVoteReadPort` | Add `ForumAdminReadPort`/tag operations; remove `EdgeOperationMapper` vote bypass |
| `notification` | `NotificationAdministrationService`, `NotificationAdminViewDTO`, `NotificationPayload` | Add `NotificationAdminReadPort` and provider; move broadcast/read off private mapper |

### Call-site metadata rule

- Every row above is a source call-site inventory, not an implementation claim. Read operations must use typed query DTOs, `RpcPolicy.QUERY_TIMEOUT_MS/QUERY_RETRIES`, bounded batch methods and explicit owner error mapping.
- Every write operation must carry stable `commandId`/idempotency key, `ActorDelegation`, creator and trace metadata; Admin must not wrap remote writes in a local transaction.
- Account/profile identity must come from Auth/App public contracts and authenticated principal; request body user IDs are data targets only.

- ADMIN-001 focused check: deterministic Python scan asserted 61 modules.* files/213 imports with 42 main and 19 test files; supplemental scan asserted 6 non-app-api imports and a 63-file/219-import full boundary; full file/call-site mapping, route/DTO snapshot and dependency baseline recorded; YAML parse passed; `git diff --check` passed.

- ADMIN-001 boundary correction: full scan also found 6 non-app-api `com.ulticode.app.*` imports across 3 overlapping files (`AccountReadAdapter`, `AdminUserProfileAdapter`, `DefaultUserActivityAnalyticsProjection`); full owner-isolation target is 63 files/219 non-public imports.

### Route/DTO compatibility snapshot

- Controller files: **16**; route annotation lines: **147**; Admin DTO files under `modules/admin/dto`: **95**; `*VO.java` files under Admin: **36**.
- Snapshot source is the current controller/DTO/VO tree; migration changes only internal seams, not route annotations or external field names.
- Route annotations:
- `AdminAccountController.java:43` @RequestMapping("/admin/account")
- `AdminAccountController.java:56` @GetMapping("/profile")
- `AdminAccountController.java:64` @PatchMapping("/profile")
- `AdminAccountController.java:73` @PostMapping("/change-password")
- `AdminAccountController.java:102` @GetMapping("/subscription")
- `AdminAnalyticsController.java:20` @RequestMapping("/admin/analytics")
- `AdminAnalyticsController.java:30` @GetMapping
- `AdminAnalyticsController.java:39` @GetMapping("/user-activity")
- `AdminAnalyticsController.java:48` @GetMapping("/problem-completion")
- `AdminAnalyticsController.java:57` @GetMapping("/contest-participation")
- `AdminAnalyticsController.java:66` @GetMapping("/revenue")
- `AdminAnalyticsController.java:75` @GetMapping("/performance")
- `AdminCommentController.java:25` @RequestMapping("/admin/comments")
- `AdminCommentController.java:34` @GetMapping
- `AdminCommentController.java:41` @GetMapping("/{type}/{id}")
- `AdminCommentController.java:53` @PatchMapping("/{type}/{id}/flag")
- `AdminCommentController.java:66` @PatchMapping("/{type}/{id}/unflag")
- `AdminCommentController.java:78` @DeleteMapping("/{type}/{id}")
- `AdminCommentController.java:91` @PostMapping("/bulk")
- `AdminContestController.java:39` @RequestMapping("/admin/contest")
- `AdminContestController.java:51` @GetMapping
- `AdminContestController.java:74` @GetMapping("/{id}")
- `AdminContestController.java:82` @PostMapping
- `AdminContestController.java:94` @PatchMapping("/{id}")
- `AdminContestController.java:106` @DeleteMapping("/{id}")
- `AdminContestController.java:119` @PostMapping("/{id}/problems")
- `AdminContestController.java:130` @DeleteMapping("/{id}/problems/{problemId}")
- `AdminContestController.java:142` @GetMapping("/{id}/rankings")
- `AdminContestController.java:153` @PostMapping("/{id}/start")
- `AdminContestController.java:163` @PostMapping("/{id}/end")
- `AdminForumController.java:41` @RequestMapping("/admin/forum")
- `AdminForumController.java:51` @GetMapping("/posts")
- `AdminForumController.java:58` @GetMapping("/posts/{id}")
- `AdminForumController.java:67` @GetMapping("/posts/{id}/audit")
- `AdminForumController.java:77` @PostMapping("/posts/{id}/pin")
- `AdminForumController.java:88` @PostMapping("/posts/{id}/unpin")
- `AdminForumController.java:99` @PostMapping("/posts/{id}/lock")
- `AdminForumController.java:110` @PostMapping("/posts/{id}/unlock")
- `AdminForumController.java:121` @DeleteMapping("/posts/{id}")
- `AdminForumController.java:132` @PostMapping("/bulk")
- `AdminForumController.java:141` @PostMapping("/posts/{id}/flag")
- `AdminForumController.java:153` @PostMapping("/posts/{id}/unflag")
- `AdminForumController.java:163` @GetMapping("/communities")
- `AdminNotificationController.java:23` @RequestMapping("/admin/notifications")
- `AdminNotificationController.java:33` @GetMapping
- `AdminNotificationController.java:41` @PostMapping
- `AdminNotificationController.java:49` @DeleteMapping("/{id}")
- `AdminNotificationController.java:60` @PutMapping("/{id}")
- `AdminProblemController.java:38` @RequestMapping("/admin/problems")
- `AdminProblemController.java:51` @GetMapping
- `AdminProblemController.java:58` @GetMapping("/export")
- `AdminProblemController.java:78` @GetMapping("/{id}")
- `AdminProblemController.java:86` @PostMapping
- `AdminProblemController.java:94` @PatchMapping("/{id}")
- `AdminProblemController.java:104` @DeleteMapping("/{id}")
- `AdminProblemController.java:113` @PostMapping("/{id}/publish")
- `AdminProblemController.java:121` @PostMapping("/{id}/unpublish")
- `AdminProblemController.java:129` @PostMapping("/bulk")
- `AdminProblemController.java:137` @PostMapping("/{id}/flag")
- `AdminProblemController.java:145` @PostMapping("/{id}/moderate")
- `AdminProblemController.java:153` @GetMapping("/flagged")
- `AdminProblemController.java:164` @PostMapping("/flagged/batch-moderate")
- `AdminProblemController.java:172` @GetMapping("/{id}/submissions")
- `AdminProblemController.java:183` @PostMapping("/import")
- `AdminProblemController.java:192` @GetMapping("/{id}/header")
- `AdminProblemController.java:199` @GetMapping("/{id}/description")
- `AdminProblemController.java:206` @GetMapping("/{id}/code")
- `AdminProblemController.java:213` @GetMapping("/{id}/cases")
- `AdminProblemController.java:220` @GetMapping("/{id}/audit")
- `AdminProblemListController.java:30` @RequestMapping("/admin/problem-lists")
- `AdminProblemListController.java:38` @GetMapping
- `AdminProblemListController.java:45` @GetMapping("/{id}")
- `AdminProblemListController.java:53` @PostMapping
- `AdminProblemListController.java:64` @PatchMapping("/{id}")
- `AdminProblemListController.java:76` @DeleteMapping("/{id}")
- `AdminProblemListController.java:85` @PostMapping("/{id}/problems")
- `AdminProblemListController.java:97` @PatchMapping("/{id}/basic-info")
- `AdminProblemListController.java:109` @PatchMapping("/{id}/visibility")
- `AdminProblemListController.java:121` @PatchMapping("/{id}/banner")
- `AdminSettingsController.java:38` @RequestMapping("/admin/settings")
- `AdminSettingsController.java:49` @GetMapping("/all")
- `AdminSettingsController.java:55` @GetMapping
- `AdminSettingsController.java:61` @GetMapping("/email")
- `AdminSettingsController.java:67` @GetMapping("/rate-limits")
- `AdminSettingsController.java:73` @GetMapping("/uploads")
- `AdminSettingsController.java:79` @GetMapping("/features")
- `AdminSettingsController.java:87` @PatchMapping
- `AdminSettingsController.java:93` @PatchMapping("/email")
- `AdminSettingsController.java:99` @PatchMapping("/rate-limits")
- `AdminSettingsController.java:105` @PatchMapping("/uploads")
- `AdminSettingsController.java:111` @PatchMapping("/features")
- `AdminSettingsController.java:119` @PostMapping("/maintenance")
- `AdminSettingsController.java:125` @PostMapping("/cache/clear")
- `AdminSettingsController.java:131` @PostMapping("/reset")
- `AdminSolutionController.java:36` @RequestMapping("/admin/solutions")
- `AdminSolutionController.java:46` @GetMapping
- `AdminSolutionController.java:54` @GetMapping("/flagged")
- `AdminSolutionController.java:61` @GetMapping("/{id}")
- `AdminSolutionController.java:69` @PostMapping("/{id}/flag")
- `AdminSolutionController.java:79` @PostMapping("/{id}/unflag")
- `AdminSolutionController.java:90` @DeleteMapping("/{id}")
- `AdminSolutionController.java:102` @PostMapping("/bulk")
- `AdminSubmissionController.java:30` @RequestMapping("/admin/submissions")
- `AdminSubmissionController.java:40` @GetMapping
- `AdminSubmissionController.java:47` @GetMapping("/{id}")
- `AdminSubmissionController.java:54` @GetMapping("/statistics")
- `AdminSubmissionController.java:61` @GetMapping("/statuses")
- `AdminSubmissionController.java:68` @GetMapping("/languages")
- `AdminSubmissionController.java:76` @PostMapping("/{id}/rejudge")
- `AdminSubmissionController.java:86` @PostMapping("/batch-rejudge")
- `AdminTagController.java:34` @RequestMapping("/admin/tags")
- `AdminTagController.java:42` @GetMapping
- `AdminTagController.java:49` @GetMapping("/{id}")
- `AdminTagController.java:62` @PostMapping
- `AdminTagController.java:69` @PatchMapping("/{id}")
- `AdminTagController.java:76` @DeleteMapping("/{id}")
- `AdminTagController.java:90` @PostMapping("/merge")
- `AdminTestCaseController.java:33` @RequestMapping("/admin/problems/{problemId}/test-cases")
- `AdminTestCaseController.java:41` @GetMapping
- `AdminTestCaseController.java:53` @GetMapping("/{testCaseId}")
- `AdminTestCaseController.java:61` @PostMapping
- `AdminTestCaseController.java:71` @PutMapping("/{testCaseId}")
- `AdminTestCaseController.java:82` @DeleteMapping("/{testCaseId}")
- `AdminTestCaseController.java:92` @PostMapping("/bulk")
- `AdminTestCaseController.java:102` @PutMapping("/reorder")
- `AdminTestCaseController.java:113` @GetMapping("/export")
- `AdminUserController.java:46` @RequestMapping("/admin/users")
- `AdminUserController.java:56` @GetMapping
- `AdminUserController.java:63` @GetMapping("/{id}")
- `AdminUserController.java:71` @PostMapping
- `AdminUserController.java:79` @PatchMapping("/{id}")
- `AdminUserController.java:89` @DeleteMapping("/{id}")
- `AdminUserController.java:98` @PostMapping("/{id}/ban")
- `AdminUserController.java:110` @PostMapping("/{id}/unban")
- `AdminUserController.java:118` @PostMapping("/{id}/reset-password")
- `AdminUserController.java:131` @PostMapping("/{id}/permissions")
- `AdminUserController.java:147` @DeleteMapping("/{id}/permissions")
- `AdminUserController.java:176` @PostMapping("/bulk-ban")
- `AdminUserController.java:184` @PostMapping("/bulk-unban")
- `AdminUserController.java:192` @DeleteMapping("/bulk-delete")
- `AuditController.java:27` @RequestMapping("/admin/audit")
- `AuditController.java:36` @GetMapping("/logs")
- `AuditController.java:43` @GetMapping("/stats")
- `AuditController.java:50` @GetMapping("/export")
- `DashboardController.java:22` @RequestMapping("/admin/dashboard")
- `DashboardController.java:50` @GetMapping("/stats")
- `DashboardController.java:57` @GetMapping("/charts")

### Dependency-boundary baseline

- Deterministic import scan: `modules.*` private imports = **213/61 files**; non-app-api `com.ulticode.app.*` imports = **6/3 overlapping files**; total full boundary = **219 imports/63 files**.
- Legal public API imports remain under `com.ulticode.app.api.*` and `com.ulticode.auth.api.*`; these are excluded from the private baseline.
- This is a baseline for ADMIN-001, not a zero-import claim; ADMIN-009 owns the zero-private-import gate.

- ADMIN-001 review recheck: 63 inventory rows, 219 private imports, 147 route annotation lines, 95 Admin DTO files and 36 VO files matched source; Confirmed Findings=0.

## ADMIN-002 User/Profile Evidence (2026-08-11)

- Admin user/profile consumers now use Auth/App public contracts; no App-private user DTO, Entity, Mapper or Service import remains.
- Actor/audit identity remains sourced from the authenticated principal; request user IDs are data targets only.
- Focused evidence: `UserManagementServiceImplTest`, `AdminAccountControllerTest`, `UserProvisioningAdapterTest`, `AdminUserProjectionTest`.
- App owner evidence: `ProfileWriteProviderIT` passed with 10 tests; `BackendAppApplicationTest` passed with 4 tests.
- Boundary scan after the slice reports zero forbidden `modules.*` and non-`app.api` `com.ulticode.app.*` imports under `services/admin/src`.

## ADMIN-003 Problem/TestCase Evidence (2026-08-11)

- Entity-free Problem/TestCase public contracts compile and `ProblemApiContractShapeTest` passed with 5 tests.
- `ProblemAdministrationProviderTest` passed with 21 provider tests, including command/error mapping.
- `./mvnw -pl admin -am -DskipTests compile -B` passed; the full Admin module test suite passed.
- `BackendAppApplicationTest` passed with 4 tests and `AppSingleHopArchTest` passed with 5 tests.
- Admin projections, export, tag and test-case paths retain the existing Result/VO/pagination boundary.

## Readiness Focused Checks (2026-08-11)

- Admin compile: passed.
- Admin module tests: passed, 88+21+23+6+23 tests across reactor modules; no failures/errors.
- Admin owner boundary ArchUnit: passed, 5 tests.
- App context and single-hop architecture checks: passed, 4 and 5 tests.
- `SandboxForkE2EIT`: passed, 5 tests; canonical `docker/sandbox/seccomp-profile.json` path resolved.

## ADMIN-004 Submission Admin Evidence (2026-08-11)

- App API contract shape remains Entity/Mapper-free; `BackendAppApiContractShapeTest` passed with 10 tests.
- Submission read adapter, provider and receipt executor focused checks passed: 12 tests.
- `SubmissionCutoverServiceTest` passed with 4 tests, covering null/legacy RPC payload mapping and provider transport error mapping.
- `AdminSubmissionControllerTest` passed with 13 tests, including `Idempotency-Key` forwarding.
- Admin module suite passed: `./mvnw -pl admin -am test -B`.
- App provider and single-hop checks passed: `BackendAppApplicationTest` and `AppSingleHopArchTest` passed.
- Review finding on direct Problem mapper access was fixed: title search now uses the App Problem owner adapter; Admin has no submission/problem private imports.
- `app.features.contest-dubbo-cutover=false` remains unchanged; no second writer, commit, push, release or production cutover was performed.

## ADMIN-005 ProblemList Admin Evidence (2026-08-11)

- `ProblemListApiContractShapeTest` passed with 3 tests; public commands, DTOs and read/write ports remain entity-free.
- `ProblemListAdministrationProviderTest` passed with 5 tests, including admin rejection, owner error mapping, duplicate mapping and command replay paths.
- `ProblemListAdministrationWiringIT` passed with 1 MySQL-backed wiring test.
- App ProblemList focused service/projection/provider checks passed with 58 tests.
- Admin ProblemList controller/service/projection focused checks passed with 38 tests; the controller authorization regression requires only `ADMIN` or `SUPER_ADMIN`.
- The migrated Admin ProblemList consumer has no `problem`/`problemlist` private imports; list/search reads use `ProblemListSearchReadPort`, detail/chain reads use `ProblemListChainReadPort`, and writes use the provider-owned command contract. This is the intentional split read seam; no duplicate `ProblemListAdminReadPort` is needed.
- `git diff --check` passed; `app.features.contest-dubbo-cutover=false` remains unchanged and no commit, push, release or production cutover was performed.

## ADMIN-006 Solution Admin Evidence (2026-08-11)

- `SolutionApiContractShapeTest` passed with 2 tests; Solution read/owner contracts remain typed and Entity/Mapper-free.
- App Solution owner/read checks passed: `DefaultSolutionOwnerPortTest` (4), `DefaultSolutionProjectionTest` (9), `DefaultSolutionCommentOwnerPortTest` (25), `SolutionCommentMapperSQLGuardTest` (1), and `BackendAppApplicationTest` (4).
- Admin Solution/comment checks passed: `AdminSolutionProjectionTest` plus `AdminCommentReadAdapterTest` (24), `AdminSolutionServiceImplTest` (4), `CommentModeratorRouterTest` (6), and `SolutionCommentModeratorTest` (2).
- App providers export `SolutionAdminReadPort`, `SolutionReadPort`, `SolutionOwnerPort`, `SolutionCommentReadPort`, and `SolutionCommentOwnerPort`; Admin consumers use only typed adapters and no `solution`/`vote`/cross-owner entity or mapper imports.
- Remote Solution/comment mutations no longer run inside an Admin-local transaction; owner-side transactions remain authoritative. `updatedAt` comment sorting maps to the schema's `updated_at` column, guarded by `SolutionCommentMapperSQLGuardTest`.
- Admin compile passed with `./mvnw -pl admin -am -DskipTests compile -B`; forbidden-import scan found 0 matches; `git diff --check` passed. No commit, push, release, service restart, or production cutover was performed.
## ADMIN-007 Forum/Vote Admin Evidence (2026-08-11)

- `ForumApiContractShapeTest` passed with 2 tests; Forum/Vote commands, DTOs and owner/read ports remain typed and Entity/Mapper-free.
- App Forum/Vote focused matrix passed with 68 tests, including owner ports, provider receipt/auth guards, projections, read adapters, vote-count batch reads, and SQL guards.
- `ForumPostPaginationIT` passed with 3 MySQL 8.0 Testcontainers tests covering deterministic `viewCount`, default created-at, and correlated `commentCount` ordering across pages.
- Admin Forum/Vote focused consumer, projection, policy, handler and controller checks passed with 94 tests.
- Admin import scan found no `forum`/`vote` Entity, Mapper or `EdgeOperation` imports; `DefaultAdminForumProjection` uses the typed vote-count owner seam.
- App owns Forum/Vote mutation transactions. Provider actor identity is verified fail-closed before `CommandReceiptExecutor` claims a durable receipt; ForumTag UPDATE/DELETE/MERGE lock owner rows and reject zero-row writes.
- `git diff --check` passed; `app.features.contest-dubbo-cutover=false` remains unchanged. No commit, push, release, restart or production cutover was performed.

## ADMIN-008 Notification Admin Evidence (2026-08-11)

- `NotificationApiContractShapeTest` passed with 2 tests; the App/API notification focused matrix passed 3 contract/domain tests and 27 provider/adapter/mapper tests.
- `DefaultNotificationAdministrationWriteAdapterTest` passed with 9 tests, including the regression that blank optional `type` is treated as unchanged; `AdminNotificationServiceImplTest` passed with 25 tests.
- `NotificationCutoverServiceTest` passed with 8 tests, including keyed update replay from the durable App receipt when the Admin read row is gone.
- `./mvnw -pl admin -am test -B` and the current `./mvnw -pl admin -am -DskipTests compile -B` both passed; no failures or errors.
- Review PASS after closing keyed-update replay and whitespace-type findings; the notification forbidden-import scan and `git diff --check` passed.
- `app.features.contest-dubbo-cutover=false` remains unchanged; no commit, push, release, restart or production cutover was performed.

## ADMIN-009 Owner Isolation and Readiness Evidence (2026-08-11)

- Forbidden-import scan over `services/admin/src` returned no matches for `modules.*` owner internals or non-`app.api` `com.ulticode.app.*` imports.
- `./mvnw -pl admin -am dependency:tree -Dincludes=com.ulticode:backend-app -Dverbose -B` passed with no `backend-app` dependency node.
- `AdminBoundaryArchTest` passed with 5 tests, keeping positive/negative owner-boundary fixtures active.
- `BackendAdminDatasourceContextIT` passed with 1 test using real MySQL 8.0 and Redis 7.2 Testcontainers; the Redis container keeps `AdminCacheConfig` in the exercised context, and bean overriding is test-only.
- `./mvnw -pl admin -am -DskipTests compile -B` and `./mvnw -pl admin -am test -B` passed with no failures or errors; `git diff --check` passed.
- RpcPolicy review and migrated endpoint Result/VO/permission/error regression evidence are closed by ADMIN-002/004/005/007/008.
- `app.features.contest-dubbo-cutover=false` remains unchanged; no commit, push, release, restart or production cutover was performed.

## ADMIN-010 Readiness Infrastructure Evidence (2026-08-11)

- `BackendAppApplicationTest` passed with 5 tests, including the real `ResourceServerJwtVerifier` bean wiring assertion; app-web full tests passed with no failures or errors.
- `SandboxForkE2EIT` passed with 6 tests, 0 failures, 0 errors and 0 skipped; the `sleep 60` timeout regression executed against the local sandbox image.
- `SandboxForkE2EIT` now resolves the canonical root `docker/sandbox/seccomp-profile.json`; the timeout path uses UUID container names, asynchronous output draining, client-first teardown, bounded `docker rm --force`/`docker ps` retries and exact-name absence verification.
- `docker ps -a --filter name=ulticode-sandbox-test- --format '{{.Names}} {{.Status}}'` returned no output after the runtime timeout test; seccomp flags and profile assertions remain unchanged.
- `BackendAdminDatasourceContextIT` passed with 1 real MySQL 8.0/Redis 7.2 Testcontainers test after the test-only Redis password override; the full Admin suite passed with no failures or errors.
- Security review closed the ResourceServerJwtVerifier context, Redis test-container credential and APP-SANDBOX-001 findings. `git diff --check` passed.
- `app.features.contest-dubbo-cutover=false` remains unchanged; no commit, push, release, restart or production cutover was performed.

## ADMIN-011 Readiness Gate Evidence (2026-08-11)

- `./mvnw -pl app/modules/contest,app/app-web,admin -am verify -B` passed after the final Sandbox cleanup change; Admin ran 556 tests (3 skipped) and app-web ran 1388 tests (14 skipped), with no failures or errors.
- Focused owner/contest IT passed for `ContestAdjudicationWiringIT`, `ContestAdmissionIT`, `ContestDeletionIT`, `RatingCalculationConcurrencyIT`, `ContestAdjudicationReceiptIT`, `ContestAdministrationWiringIT`, `BackendAdminDatasourceContextIT`, and `SandboxForkE2EIT`; Admin reported 1 test and app-web 24 tests with no failures or errors.
- `./mvnw verify -B` passed for the full 15-module services reactor at 2026-08-11T14:20:56+08:00; no module failed.
- The broad `-Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false` sweep passed after adding test-scoped Redis 7.2 Testcontainers to `AuditSinkTransactionIT` and `BackupRepositoryIT`: Admin ran 39 ITs with 0 failures, 0 errors and 0 skipped; the app IT modules completed with reactor BUILD SUCCESS.
- The targeted `AuditSinkTransactionIT,BackupRepositoryIT` command passed 6 tests with no failures or errors, exercising `AdminCacheConfig` against real Redis rather than excluding Redis auto-configuration.
- The final focused `./mvnw -pl app/modules/contest,app/app-web,admin -am verify -B` passed at 2026-08-11T15:16:20+08:00 and full `./mvnw verify -B` passed at 2026-08-11T15:17:30+08:00; `git diff --check` and control-plane YAML parsing passed.
- Authorized disposable runtime smoke passed: PM2 showed all three owner services online, 9101/9102/9103 were listening, Auth login returned 200 with cookies, and six read-only Admin owner endpoints returned HTTP 200.
- `app.features.contest-dubbo-cutover=false` remains unchanged; no commit, push, release, migration or production cutover was performed. CONTEST-009 remains blocked by the independent App schema-chain and release-authority constraints.

## CONTEST-010 App Owner Schema Closure Evidence (2026-08-11)

- Added `init-db/migrations/app/V20260811180000__Create_App_Contest_Schema.sql` as a later self-contained App migration; no applied migration was edited.
- In a disposable MySQL 8.0 container, dropped the `app` schema and ran `ENV_FILE=/tmp/ulticode-contest-schema.env ./scripts/dev/migrate.sh migrate`; Flyway applied 4 App migrations through `v20260811180000` with `BUILD SUCCESS`.
- `ENV_FILE=/tmp/ulticode-contest-schema.env ./scripts/dev/migrate.sh validate` passed with 5 migrations.
- Fresh schema inventory passed: 14 contest tables; `contests.status` includes `FINISHING`; receipt key is `(submission_id,generation)`; real-registration and virtual-admission generated unique guards exist; all 3 submission generation/lease columns exist; 17 contest-related FKs point within App and 0 point outside App, including `fk_app_contests_scoring_rule` with `ON DELETE SET NULL ON UPDATE CASCADE`.
- Contest MySQL/Spring/Dubbo matrix passed: `ContestAdjudicationWiringIT`, `ContestAdmissionIT`, `ContestDeletionIT`, `RatingCalculationConcurrencyIT`, `ContestAdjudicationReceiptIT` and `ContestAdministrationWiringIT`; 18 tests, 0 failures, 0 errors, 0 skipped.
- Ranking/projection focused tests passed after replacing contest participant user-table joins with App-owned `SubmissionUserReadPort` batch enrichment; SQL constructor mapping remains 17 columns/components.
- `git diff --check` passed. `app.features.contest-dubbo-cutover=false` remains unchanged; no commit, push, release, production migration or cutover was performed.

## CONTEST-010 Review Fix Evidence (2026-08-11)

- Added the required manual rollback at `init-db/rollback/V20260811180000__Create_App_Contest_Schema.rollback.sql`; executing it on the disposable schema restored the baseline tables and `contests`/`submissions` columns, then a fresh migrate/validate passed again.
- Mode-specific participant reads now use explicit real/virtual mapper seams, ranking usernames preserve null for missing identities, and the focused regressions passed: RankingServiceImplTest (35), ContestParticipationServiceImplVirtualSessionTest (20), DefaultContestProjectionVisibilityTest (4), DefaultContestProjectionUserContestsTest (8), DefaultSubmissionUserReadAdapterTest (1), and GlobalRankingMapperSQLGuardTest (2).
- An earlier full reactor test rerun was blocked by the pre-existing `backend-app-api` `ProblemApiContractShapeTest` AssertJ generic compilation errors; the baseline was resolved in the final rerun below without changing App production code or weakening the test.

## CONTEST-010 Advisory Follow-up Evidence (2026-08-11)

- Fixed the confirmed real/virtual selection gap in `ContestParticipantMapper.findByContestIdAndUserId` and `findByContestIdsAndUserId`; both generic reads now require `is_virtual = 0`, protecting projection and subscription paths from selecting a newer virtual row.
- `./mvnw -pl app/app-web -am -Dtest=GlobalRankingMapperSQLGuardTest,DefaultContestProjectionVisibilityTest,DefaultContestProjectionUserContestsTest,ContestAdmissionIT -Dsurefire.failIfNoSpecifiedTests=false test -B` passed: 19 tests, 0 failures, 0 errors, 0 skipped.

## CONTEST-009 Final Readiness Rerun and Release Approval Record (2026-08-11)

- Root cause of the `backend-app-api` baseline was AssertJ wildcard capture for mixed `Class<?>` record-component types in `ProblemApiContractShapeTest`. The test now uses explicit `Assertions.<Class<?>>assertThat(...)` at the two affected assertions; all original record type/order and contract-shape checks remain active.
- `./mvnw -pl api/app-api -am -Dtest=ProblemApiContractShapeTest -Dsurefire.failIfNoSpecifiedTests=false test -B` passed: 5 tests, 0 failures, 0 errors, 0 skipped.
- `./mvnw -pl api/app-api -am test -B` passed: 33 tests, 0 failures, 0 errors, 0 skipped; no test was excluded or weakened.
- Final focused readiness verify passed: `./mvnw -pl app/modules/contest,app/app-web,admin -am verify -B` completed with BUILD SUCCESS; app-web reported 1394 tests, 0 failures, 0 errors, 14 skipped, and JaCoCo reported all coverage checks met.
- Final full reactor verify passed: `./mvnw verify -B` completed with BUILD SUCCESS for all 15 services modules; no module failed and JaCoCo checks were met.
- Required contest/Admin integration matrix passed: `ContestAdjudicationWiringIT`, `ContestAdmissionIT`, `ContestDeletionIT`, `RatingCalculationConcurrencyIT`, `ContestAdjudicationReceiptIT`, `ContestAdministrationWiringIT`, `BackendAdminDatasourceContextIT` and `SandboxForkE2EIT`; 24 tests, 0 failures, 0 errors, 0 skipped.
- Release approval record `CONTEST-009-RELEASE-20260811` is `APPROVED` for this development project. The explicit approval is recorded in Git at `services/docs/CONTEST-009-RELEASE-APPROVAL.md`; there is no concrete production environment or external deployment target.
- Approval scope is readiness closure only, with the condition that `app.features.contest-dubbo-cutover=false` remains unchanged. A future real production environment requires a new environment-specific approval.
- Safety invariant remains active; no production enablement, migration, restart or external release was performed.

## Solarized Theme Design v1.0 Public Rollout Evidence (2026-08-11)

- `packages/design-system` owns the canonical 16-color palette, accessible Light/Dark mappings, shared variants, status/difficulty semantics, eight chart series and the normative README contract.
- `packages/theme` exports typography through a declared CSS subpath; both apps consume `@ulticode/design-system/style.css` through the package boundary.
- Theme tests passed 31/31; design-system contract tests passed 7/7; design-system, theme, Console and Management type checks passed.
- Console full tests passed when excluding the protected dirty `VirtualContestTimer.spec.ts`; Management full tests passed. Both app production builds and targeted ESLint passed.
- Built CSS for both apps contains `text-link-foreground`, `decoration-link-decoration` and `bg-status-error-surface`; Management also contains `text-rank-first`.
- `localhost:9002/problemset` Light computed background/foreground were base3/base01; Dark were base03/base1. Primary selected dates used inverted monotones with a blue border, and difficulty badges used neutral text with semantic surfaces and accent borders.
- Browser error log was empty. Standards and Spec reviews each closed with 0 confirmed findings. `git diff --check` passed.
- Existing contest timer and landing work remained protected. No commit, push, deployment or external mutation was performed.

## CONTRACT-001 Owner Matrix and Common-Prerequisite Closure (2026-08-17)

- `CONTRACT-001-OWNER-MATRIX.md` is the authoritative inventory for `backend-app-api`: 93 service, 31 command, 87
  DTO, 6 event, 1 error and 1 security top-level Java files (219 total), plus 66 nested declarations. The matrix
  records App, Submission, Notification, common/security, App fact/recipient and Judge-runtime ownership, full nested
  paths, target package roots, Dubbo identities, matched-release/no-alias rules and DEC-011 guardrails.
- Validation caught and fixed the omitted `DelegationAssertionContract.java`; the later nested-path check confirmed
  `RunResultDTO.RunCaseResult.InputParam` was already present under its complete nesting path. The matrix now has no
  missing top-level or nested owner entry.
- `CONTRACT-001-COMMON` was added as a bounded prerequisite for the eight implementation-free common/security/
  metadata/value types; `CONTRACT-002` and `CONTRACT-003` depend on it. `SubmissionNotificationPort` has a
  deterministic dead-contract removal disposition unless a separately planned caller is discovered.
- `cd services && ./mvnw -pl api/app-api -am test -B` passed with backend-common 93 tests and backend-app-api 39 tests,
  zero failures. SnakeYAML parsing for the active control-plane files, `git diff --check`, and matrix whitespace checks
  passed. No business source/POM/runtime/migration/route/grant/database/provider-registration file changed.
- The formal review workers timed out; the parent completed the bounded local standards/spec fallback against HEAD and
  the untracked matrix with zero confirmed findings. `CONTRACT-001` is closed; the common slice was then implemented
  and closed below.

## CONTRACT-001-COMMON completion evidence

- Added eight implementation-free contracts to `backend-common`: `common.command.ActorDelegation`,
  `common.command.WriteCommand`, `common.dto.DifficultyCountDTO`, `common.auth.AccountInfo`,
  `common.auth.JwtPayload`, `common.security.AccountReadPort`, `common.security.JwtValidationPort`, and
  `common.security.DelegationAssertionContract`. Removed their old `backend-app-api` declarations without aliases.
- Migrated App, Admin, Notification, WebSocket/security consumers and tests to the common packages while leaving
  Auth API's independent provider-owned command types unchanged; removed the unused app-private duplicate
  `DifficultyCountDTO`.
- TDD evidence: the new common contract test first failed at test compilation before the eight production types were
  added, then `cd services && ./mvnw -pl api/app-api -am test -B` passed with backend-common 96 tests and app-api 39
  tests; the affected App/Admin/Notification reactor test also passed.
- Static evidence: old-FQCN and duplicate-declaration scans, common forbidden-import scan, backend-common dependency
  tree scan, changed-file whitespace and `git diff --check` passed. `graphify update .` rebuilt 26,499 nodes and
  77,037 edges. New files are not tracked by the codebase-memory generation, so direct source checks remain the
  authoritative evidence for those paths.
- Review/validation: formal review workers timed out and were shut down; the parent completed the bounded local
  standards/spec/security fallback with no confirmed findings. Migration-guide ownership and matched-release/no-alias
  documentation is current. CONTRACT-002 and CONTRACT-003 are now ready.

## 2026-08-17 (CONTRACT-002 through CONTRACT-006 completion evidence)

- Added `backend-submission-api` and `backend-notification-api` to the Maven reactor; moved the owner-matrix
  Submission/Notification contracts, migrated all discovered callers/providers/tests/POMs, and removed old FQCNs and
  the dead `SubmissionNotificationPort`. App API architecture tests now cover only App-owned contracts plus explicit
  fact/recipient exceptions.
- Notification ownership was completed without changing delivery runtime behavior: `NotificationErrorCode` retains
  the notification namespace and numeric values, and the command receipt key now names
  `NotificationAdministrationService`. Notification provider/reference tests preserve group/version, idempotency,
  audit actor and `RpcResult` behavior.
- Added the single DTO-based Submission `TestCaseDetailCodec` and `SubmissionStatusCatalog` to submission-api. App and
  Submission map DTOs to their private entities at storage edges; the separate Judge codec remains untouched. The
  canonical-file scan finds one API codec/catalog/meta set plus the independent Judge codec.
- TDD red/green and validation passed: backend-common 96, submission-api 15, notification-api 7, app-api 28;
  affected 20-module Maven reactor BUILD SUCCESS; Submission owner `*IT` 28/0/0, including
  `DefaultSubmissionWritePortIT` 6/0/0. App wire-compatibility focused tests passed 13/13.
- Boundary scans found no new API imports of App/Auth/Admin/modules implementation packages, no Notification
  `AppErrorCode` use, and no backend-app-api dependency from either new API. A review scan caught and removed one
  implementation-only `SubmissionMapper` Javadoc link from `UserBestStats`; the API reactor was rerun successfully.
- `graphify update .` completed at 26,513 nodes / 77,128 edges; migration guide §6.2/§13.2, `git diff --check`,
  duplicate scans and control-plane reconciliation are current. No runtime default, route, grant, migration, commit,
  push or deployment action was performed.
- CONTRACT-007 remains blocked: direct Submission provider handoff, compat retirement, grant/cutover and
  single-writer evidence still require explicit release authority plus the existing SPLIT-005 sandbox/Testcontainers
  gate. CONTRACT-008 remains pending behind it.

## CRFIX-001 Search CR closure evidence (2026-08-20)

- Real-Meili IT is explicitly opt-in and reports two skips when `MEILI_E2E_HOST`/`MEILI_E2E_KEY` are absent. Both `scripts/dev/test.sh integration` and the documented reactor `-Dtest='*IT'` command exit 0.
- Meili counts use `page=1,hitsPerPage=0`; a count reaching `pagination.maxTotalHits`, an unreadable setting or any backend failure triggers whole-request database fallback. All-index reads count every index first and fetch hits only from page contributors.
- Problem, ForumPost and Solution DB fallback queries order by owner `id ASC` before `LIMIT/OFFSET`; extreme page offsets saturate instead of wrapping to the first page.
- User union count uses a bounded Auth-owner `NOT LIKE` count over each 100-ID profile page. `AuthAccountQueryMapperCollationIT` passed against MySQL 8 `utf8mb4_0900_ai_ci` for accent/case-insensitive and soft-delete behavior.
- Validation: focused Auth/Search 40/0/0/2; provider plus real-collation 11/0/0/0; real Meili v1.8 2/0/0/0 with 1,500 matching documents; final Surefire XML 822 reports / 2,769 tests / 0 failures / 0 errors / 29 skips; affected reactor verify and JaCoCo passed.
- General and Java formal reviews ended PASS with no remaining high-confidence finding. `graphify update .` rebuilt 27,987 nodes / 81,739 edges; codebase-memory coverage reported no recorded gaps; `git diff --check` passed.
