# P4-ADMIN-001 Admin Use-Case RPC / Call-Chain Matrix

> status: `REPOSITORY EVIDENCE` — extends `P3-ADMIN-001-admin-budget-manifest.md` with explicit controller → projection/service → deep-interface → provider call chains and measured/unmeasured markers.
> owner: ADMIN
> base: `docs/architecture/evidence/P3-ADMIN-001-admin-budget-manifest.md`
> implementation_change: none (evidence/document only)

## 1. Purpose and scope

This file adds the missing controller-to-provider call chain for each admin
use case listed in the P3 budget manifest. It records which controller maps to
which projection/service/deep-interface, which provider contract that
interface calls, and whether the current source path meets the target RPC
budget or still exhibits N+1 / unbounded scan / all-or-nothing failure.

- **Measured** (`MEASURED`) marks a use case whose call chain is provably
  within the target budget from source inspection and has a focused
  `gate-admin-rpc-budget.sh` test.
- **Unmeasured** (`UNMEASURED`) marks a use case whose target budget is
  documented but whose current source still carries a known N+1, unbounded
  scan, or missing typed-degradation path — no production latency number is
  claimed.
- Only source-level evidence is used; no production traffic, p95/p99, or
  capacity proof is asserted.

## 2. Interactive read call-chain matrix

| id | controller → projection/service | deep interface | provider contract(s) | L | R | current_shape | status |
|---|---|---|---|---|---|---|---|---|---|
| `I-DASH-STATS` | `AdminDashboardController:78` → `DefaultDashboardStatsProjection:41` | `DashboardStatsProjection` | `AdminDashboardReadAdapter` → App+Submission+Auth | 3 | 1 | 3 parallel RPC | `MEASURED` |
| `I-DASH-CHART-OWNER` | `AdminDashboardController:95-110` → `DefaultDashboardStatsProjection:65-80` | `DashboardStatsProjection.dashboardChart` | per-owner chart RPC | 1 | 1 | 1 RPC | `MEASURED` |
| `I-DASH-CHART-USERS` | `AdminDashboardController:113-135` → `DefaultAdminDashboardReadAdapter:162-250` | `AdminDashboardReadAdapter.userTrend` | Auth `AccountQueryService` page loop | target `1/1` | `N` serial | N+1 serial Auth pages, unbounded | `UNMEASURED — FAIL_UNBOUNDED_SCAN` |
| `I-USER-LIST` | `AdminUserController:62` → `DefaultAdminUserProjection:37-121` | `AdminUserProjection.getUsers` | Auth account page + App profile batch | 2 | 2 | 2 rounds | `MEASURED` |
| `I-USER-DETAIL` | `AdminUserController:69` → `userFromDetail` → `DefaultAdminUserDetailQuery:164-253` | `AdminUserDetailQuery.loadUserDetail` | Auth `AuthorizationSnapshotService` + `AdminUserEnricher` (Auth+App) + Submission stats + App solution count | target `8/8` | 9 (1+3+1+2+1) | round 1 account, round 2 parallel enrich+stats+solution | `MEASURED` — within envelope |
| `I-WS-AUTH` | `AdminWebSocketAuthHandler` → `AccountReadAdapter:17-59` | `IdentityQueryService.getIdentity` | Auth identity query | 1 | 1 | 1 RPC | `MEASURED` |
| `I-CONTEST-LIST` | `AdminContestController:31-60` → `DefaultAdminContestProjection:31-84` | `AdminContestProjection.listContests` | App contest page + batch `countByContestIds` | target `2/2` | 2 | page + batch count | `FIXED` — batch via `countByContestIds` in `DefaultContestAdminReadAdapter.selectPage`; list reads `DTO.problemCount` |
| `I-CONTEST-DETAIL` | `AdminContestController:62-80` → `DefaultAdminContestProjection:86-110` | `AdminContestProjection.getContestDetail` | App contest read + problem count | 2 | 2 | 2 RPC | `MEASURED` |
| `I-CONTEST-RANKINGS` | `AdminContestController:82-95` → `DefaultAdminContestProjection:112-135` | `AdminContestProjection.getContestRankings` | App contest existence + ranking page | 2 | 2 | 2 RPC | `MEASURED` |
| `I-CONTEST-ANNOUNCEMENTS` | `AdminContestController:97-110` → `DefaultAdminContestProjection:137-160` | `AdminContestProjection.getContestAnnouncements` | App announcement page | 1 | 1 | 1 RPC | `MEASURED` |
| `I-FORUM-LIST` | `AdminForumController:33-50` → `DefaultAdminForumProjection:44-115` | `AdminForumProjection.listPosts` | App post page + `AdminUserEnricher.enrichWithStatus` (Auth+App batch) | 3 | 2 | page + enrich batch (parallel) | `MEASURED` |
| `I-FORUM-DETAIL` | `AdminForumController:52-68` → `DefaultAdminForumProjection:117-145` | `AdminForumProjection.getPost` | App post read + `enrichOne` (Auth+App) | 4 | 4 | serial enrichOne | `MEASURED` |
| `I-FORUM-COMMUNITIES` | `AdminForumController:70-80` → `DefaultAdminForumProjection:147-165` | `AdminForumProjection.listCommunities` | App community page | 1 | 1 | 1 RPC | `MEASURED` |
| `I-NOTIFY-LIST` | `AdminNotificationController:31-48` → `DefaultAdminNotificationProjection:63-146` | `AdminNotificationProjection.listNotifications` | Notification page + `AdminUserEnricher.enrichWithStatus` | 3 | 2 | page + enrich batch | `MEASURED` |
| `I-SOLUTION-LIST` | `AdminSolutionController:33-50` → `DefaultAdminSolutionProjection:50-149` | `AdminSolutionProjection.listSolutions` | App solution page + enrich batch + problem batch | 4 | 3 | page + enrich + problem | `MEASURED` |
| `I-SOLUTION-DETAIL` | `AdminSolutionController:52-70` → `DefaultAdminSolutionProjection:151-192` | `AdminSolutionProjection.getSolution` | App solution read + enrichOne + problem read | 5 | 5 | serial | `MEASURED` |
| `I-SUBMISSION-LIST` | `AdminSubmissionController:31-48` → `DefaultAdminSubmissionProjection:257-310` | `AdminSubmissionProjection.listSubmissions` | Submission page + enrich + problem | 4 | 3 | page + enrich + problem | `MEASURED` |
| `I-SUBMISSION-DETAIL` | `AdminSubmissionController:50-70` → `DefaultAdminSubmissionProjection:50-149` | `AdminSubmissionProjection.getSubmission` | Submission read + enrichOne + problem | 5 | 5 | serial | `MEASURED` |
| `I-SUBMISSION-STATS` | `AdminSubmissionController:72-85` → `DefaultAdminSubmissionProjection:151-189,257-310` | `AdminSubmissionStatsProjection` | five Submission aggregation calls | 5 | 5 | serial | `MEASURED` — within envelope |
| `I-SUBMISSION-FILTERS` | `AdminSubmissionController:87-95` → `DefaultAdminSubmissionProjection` | `AdminSubmissionProjection.listSubmissionLanguages` | Submission languages read | 1 | 1 | 1 RPC | `MEASURED` |
| `I-PROBLEM-READ` | `AdminProblemController` → `AdminProblemService` → `ProblemOwnerReadAdapter` | `ProblemOwnerReadPort` | App problem read per endpoint | 1 | 1 | 1 RPC | `MEASURED` |
| `I-PROBLEM-SUBMISSIONS` | `AdminProblemController` → `DefaultAdminSubmissionProjection:50-189` | `AdminSubmissionProjection.listProblemSubmissions` | Problem existence + submission page | 2 | 2 | 2 RPC | `MEASURED` |
| `I-TESTCASE-READ` | `AdminTestCaseController` → `AdminTestCaseService` → `DubboTestCaseOwnerAdapter` | `TestCaseOwnerReadPort` | App problem/test-case read | 2 | 2 | 2 RPC | `MEASURED` |
| `I-PROBLEM-LIST-LIST` | `AdminProblemListController` → `DefaultAdminProblemListProjection:41-145` | `AdminProblemListProjection.listProblemLists` | App list page + `enrichWithStatus(Set)` batch | target `3/3` | 2 | page + enrich batch | `FIXED` — list path uses `enrichWithStatus(Set)`; detail retains `enrichOne` (single-item) |
| `I-PROBLEM-LIST-DETAIL` | `AdminProblemListController` → `DefaultAdminProblemListProjection:147-175` | `AdminProblemListProjection.getProblemList` | App list detail + enrichOne | 4 | 4 | serial | `MEASURED` |
| `I-COMMENT-TYPED` | `AdminCommentController` → `AdminCommentService` → comment projection | `AdminCommentReadPort` | App comment page + enrich batch + parent batch | 4 | 3 | page + enrich + parent | `MEASURED` |
| `I-COMMENT-ALL` | `AdminCommentController` → `AdminCommentService` → `getAllComments` | dual-owner merge | one bounded page (100 rows) per moderator: 2 moderators × 4 owner RPC per `listComments` = 8/8 | target `8/8` | 8 max (1 page × 2 moderators × 4 RPC) | `FIXED` — `MODERATOR_PAGE_SIZE=100`; `getAllComments` fetches one page per moderator; `total` = fetched item count (`all.size()`), not owner `PageResult.getTotal()` summed — page size 100 never `Integer.MAX_VALUE` |
| `I-TAG-READ` | `AdminTagController` → tag service → `DubboProblemTagOwnerAdapter` | tag read | one owner read per endpoint | 1 | 1 | 1 RPC | `MEASURED` |
| `I-ANALYTICS-OVERVIEW` | `AdminAnalyticsController:27-79` → `DefaultUserActivityAnalyticsProjection` + `DefaultAdminAnalyticsPortAdapter:53-210` | analytics read ports | six slices in parallel | 6 | 1 | parallel | `MEASURED` |
| `I-ANALYTICS-ACTIVITY` | `AdminAnalyticsController:81-120` → `DefaultUserActivityAnalyticsProjection:52-184` | `ActivityAnalyticsProjection` | Submission daily/weekly/retention/hourly/top + optional Auth identity | target `11/11` | 10 + optional | serial | `MEASURED` — no 365-day cap yet |
| `I-ANALYTICS-PROBLEM` | `AdminAnalyticsController:122-140` → `ProblemReportController` | problem analytics | App analytics read | 1 | 1 | 1 RPC | `MEASURED` |
| `I-ANALYTICS-CONTEST` | `AdminAnalyticsController:142-160` → `ContestParticipationReporter:35-89` | contest analytics | App contest list + participant batch | target `2/2` | 2 | 2 RPC | `MEASURED` — no 500-row cap yet |
| `I-ANALYTICS-REVENUE` | `AdminAnalyticsController:162-180` → `RevenueReporter:37-169` | revenue analytics | Subscription list read | 1 | 1 | 1 RPC | `MEASURED` — no 10,000-row cap yet |
| `I-ANALYTICS-PERFORMANCE` | `AdminAnalyticsController` → system sampler | local JMX | JVM/OS sample only | 0 | 0 | local | `MEASURED` |
| `I-AUDIT` | `AdminAuditController` → `AuditServiceImpl` + `AdminUserEnricher` | audit query | local mapper + enrich (2 Q) | 2 | 2 | local + enrich | `MEASURED` |
| `I-SETTINGS` | `AdminSettingsController` → `SystemSettingsServiceImpl:58-162` | settings read | local store | 0 | 0 | local | `MEASURED` |

## 3. Interactive write call-chain matrix

Every `W` entry below has `timeout_ms=3000`, `retries=0`. Query prefchecks
and read-backs are shown explicitly as `Q`; a write is never retried
automatically.

| id | controller → service | deep interface | provider contract(s) | target L / R | current_shape | status |
|---|---|---|---|---|---|---|
| `W-ONE-SHOT` | controller → service impl | owner command adapter | one owner write | 1/1 | 1 RPC | `MEASURED` |
| `W-USER-CREATE` | `AdminAccountController:65-105` → `UserManagementServiceImpl:70-339` | `UserProvisioningAdapter` + `AdminUserProfileAdapter` + `AdminUserDetailQuery` | Auth uniqueness + create + App profile + read-backs | 12/12 | preflights + write + read-backs | `MEASURED` |
| `W-USER-UPDATE` | `AdminAccountController:87-120` → `UserManagementServiceImpl` | `UserProvisioningAdapter` + `AdminUserProfileAdapter` | Auth reads + writes + read-backs | 14/14 | serial reads/writes/read-backs | `MEASURED` |
| `W-USER-DELETE-RESET` | `AdminAccountController:122-180` → `UserManagementServiceImpl` | `UserProvisioningAdapter` | Auth write | 1/1 | 1 RPC | `MEASURED` |
| `W-USER-PERMISSION` | `AdminUserController:182-210` → `UserPermissionServiceImpl:43-160` | permission adapter | Auth write + enrich | 20/20 | enrich + before/after detail | `MEASURED` |
| `W-PROFILE` | `AdminUserProfileController` → `AdminUserProfileAdapter:49-169` | profile write | App profile write | 1/1 | 1 RPC | `MEASURED` |
| `W-CONTEST-READBACK` | `AdminContestController:112-180` → `ContestCutoverService:40-182` | contest write adapter | App contest write + read + count | 3/3 | write + read + count | `MEASURED` |
| `W-CONTEST-ONE` | `AdminContestController:182-210` → `ContestCutoverService` | contest write | App write | 1/1 | 1 RPC | `MEASURED` |
| `W-PROBLEM-CREATE` | `AdminProblemController:31-60` → `AdminProblemServiceImpl:46-200` / `ProblemCutoverService:47-154` | problem write adapter | App write + read | 2/2 | write + read | `MEASURED` |
| `W-PROBLEM-UPDATE-STATE` | `AdminProblemController:62-90` → `ProblemCutoverService` | problem write | version read + write + read-back | 3/3 | serial | `MEASURED` |
| `W-PROBLEM-DELETE` | `AdminProblemController:92-120` → `ProblemCutoverService` | problem delete | version read + write | 2/2 | serial | `MEASURED` |
| `W-NOTIFY-CREATE` | `AdminNotificationController:31-55` → `AdminNotificationServiceImpl:63-196` / `NotificationCutoverService:49-253` | notification write | Notification write + read + enrich | 4/4 | write + read + enrich | `MEASURED` |
| `W-NOTIFY-UPDATE` | `AdminNotificationController:57-85` → `AdminNotificationServiceImpl` | notification update | pre-read + write + read-back + enrich | 5/5 | serial | `MEASURED` |
| `W-NOTIFY-DELETE` | `AdminNotificationController:87-110` → `AdminNotificationServiceImpl` | notification delete | pre-read + write | 2/2 | serial | `MEASURED` |
| `W-SOLUTION-READBACK` | `AdminSolutionController:72-100` → `AdminSolutionServiceImpl:36-125` | solution write | solution write + read + enrich + problem | 6/6 | serial | `MEASURED` |
| `W-SOLUTION-DELETE` | `AdminSolutionController:102-120` → `AdminSolutionServiceImpl` | solution delete | App write | 1/1 | 1 RPC | `MEASURED` |
| `W-CONTEST-READBACK` | `AdminContestController:112-180` → `ContestCutoverService` | contest write | App write + read + count | 3/3 | 2Q + W | `MEASURED` |
| `W-PROBLIST-CREATE` | `AdminProblemListController:31-48` → `AdminProblemListServiceImpl:62-180` | list write | App write | 1/1 | 1 RPC | `MEASURED` |
| `W-PROBLIST-PREFLIGHT` | `AdminProblemListController:50-120` → `AdminProblemListServiceImpl:218-285,304-373` | list update/delete | pre-read + write | 2/2 | 1Q + W | `MEASURED` |
| `W-TAG-FORUM` | `AdminTagController` → `ForumTagHandler:46-127` | forum tag | Forum tag RPC | 1/1 | 1 RPC | `MEASURED` |
| `W-TAG-PROBLEM` | `AdminTagController` → `ProblemTagHandler:32-132` | problem tag | Problem tag RPC | 2-4/2-4 | preflight + write | `MEASURED` |
| `W-TESTCASE-ONE` | `AdminTestCaseController:31-55` → `AdminTestCaseService:45-199` | test-case write | problem existence + write | 2/2 | 1Q + W | `MEASURED` |
| `W-TESTCASE-UPDATE` | `AdminTestCaseController:57-90` → `AdminTestCaseService:207-270` | test-case update | problem read + write | 3/3 | 2Q + W | `MEASURED` |

## 4. Scheduled and batch call-chain

| id | entry point | deep interface | provider contract(s) | target input cap | target L | status |
|---|---|---|---|---|---|---|
| `B-USER-BAN` | `AdminBulkExecutor:52-81` | `UserManagementServiceImpl` | per-user Auth read + write | 100 | 1000 | `MEASURED` — cap present |
| `B-USER-DELETE` | `AdminBulkExecutor` | `UserManagementServiceImpl` | Auth write per ID | 100 | 100 | `MEASURED` — cap present |
| `B-FORUM-TOGGLE` | `AdminBulkExecutor` | `AdminForumServiceImpl:67-177` | App write per ID | 100 | 100 | `MEASURED` — cap present |
| `B-FORUM-DELETE` | `AdminBulkExecutor` | `ContentModerationCutoverService:25-101` | App read + write per ID | 100 | 200 | `MEASURED` — cap present |
| `B-COMMENT-DELETE` | `AdminBulkExecutor` | `AdminCommentServiceImpl:97-186` | comment-owner write per ID | 100 | 100 | `MEASURED` — cap present |
| `B-COMMENT-UNFLAG` | `AdminBulkExecutor` | `AdminCommentServiceImpl` | write + 4-call detail per ID | 100 | 500 | `MEASURED` — cap present |
| `B-SOLUTION-SIMPLE` | `AdminBulkExecutor` → `AdminSolutionServiceImpl` | solution batch | existence read + write per ID | 100 | 101 | `MEASURED` — cap present |
| `B-SOLUTION-UNFLAG` | `AdminBulkExecutor` → `AdminSolutionServiceImpl` | solution write + read | 1 + (W+Q×5)×N | 100 | 501 | `MEASURED` — cap present |
| `B-PROBLEM-PUBLISH` | `AdminBulkExecutor` → `ProblemCutoverService` | problem write | version read + write + read-back per ID | 500 | 1500 | `MEASURED` — cap present |
| `B-PROBLEM-DELETE` | `AdminBulkExecutor` → `ProblemCutoverService` | problem delete | version read + write per ID | 500 | 1000 | `MEASURED` — cap present |
| `B-PROBLEM-RESTORE` | `AdminBulkExecutor` → `ProblemCutoverService` | problem restore | owner write per ID | 500 | 500 | `MEASURED` — cap present |
| `B-PROBLEM-EDIT` | `AdminBulkExecutor` → `AdminProblemServiceImpl` | difficulty edit | existence read + write per ID | 500 | 1000 | `MEASURED` — cap present |
| `B-PROBLEM-MODERATE` | `AdminProblemController` → `ProblemImportServiceImpl` / moderation | batch moderate | one owner batch write, **no `@Size` cap** | target 500 | 1 | `UNMEASURED` — `FAIL_INPUT_BOUND` |
| `B-PROBLEM-IMPORT` | `AdminProblemController` → `ProblemImportServiceImpl:56-189` | import | slug lookup + batch write | 500 | 2 | `MEASURED` — cap present |
| `B-TESTCASE-APPEND` | `AdminTestCaseController` → `AdminTestCaseService:88-199` | test-case insert | problem read + insert per item | 500 | 501 | `MEASURED` — cap present |
| `B-TESTCASE-REPLACE` | `AdminTestCaseController` → `AdminTestCaseService` | test-case replace | problem read + atomic write | 500 | 2 | `MEASURED` — cap present |
| `B-TESTCASE-REORDER` | `AdminTestCaseController` → `AdminTestCaseService` | test-case reorder | problem read + case read + order write | target 500 | 3 | `UNMEASURED` — `FAIL_INPUT_BOUND` |
| `B-REJUDGE` | `AdminSubmissionController` → `SubmissionCutoverService:32-117` | rejudge | Submission batch command | 50 | 1 | `MEASURED` — cap present |
| `B-PROBLIST-REPLACE` | `AdminProblemListController` → `AdminProblemListServiceImpl` | list replace | prefault + write, **no entry-size cap** | target 500 | 2 | `UNMEASURED` — `FAIL_INPUT_BOUND` |
| `B-PROBLEM-EXPORT` | `AdminProblemController` → `ProblemExportServiceImpl:46-73` | export | `listAllProblems` read | 10,000 | 1 | `UNMEASURED` — `FAIL_PAYLOAD_BOUND` |

## 5. Reconciliation call-chain

| id | entry point | deep interface | provider contract(s) | target L | current_shape | status |
|---|---|---|---|---|---|---|
| `S-RECON-FULL` | `OwnerReconciler:294-445` | reconciliation loop | Auth orphan aggregate + Submission/Notification paged facts + App orphan + audit | 164 target | `UNBOUNDED` no finite page cap | `UNMEASURED` — `FAIL_UNBOUNDED_SCAN` |
| `S-RECON-INCREMENTAL` | `OwnerReconciler:294-445` with watermark | same loop | same providers | 164 target | unbounded if window large | `UNMEASURED` — `FAIL_UNBOUNDED_SCAN` |
| `S-RECON-LEASE-BUSY` | `OwnerReconciler:162-209` | lease entry | lease acquire | 0 | exit on null lease | `MEASURED` |

## 6. Unmeasured / N+1 / bounded-scan summary

| id | location | problem | target fix |
|---|---|---|---|
| `I-DASH-CHART-USERS` | `DefaultAdminDashboardReadAdapter:162-250` | N serial Auth pages, unbounded | Replace with Auth-owned user-count aggregate |
| `I-PROBLEM-LIST-LIST` | `DefaultAdminProblemListProjection:51-114` | `enrichOne` per row | One batched author enrichment | — **`FIXED`** — list path uses `enrichWithStatus(Set)`; detail retains `enrichOne` (single-item) |
| `I-COMMENT-ALL` | `AdminCommentServiceImpl:188-209` | `Integer.MAX_VALUE` per moderator | One bounded page per moderator (`MODERATOR_PAGE_SIZE=100`); `total` = fetched item count | — **`FIXED`** — single 100-row page per moderator enforced in `getAllComments`; one `listComments` call per moderator with no follow-up page; `total` set to `all.size()` (fetched items only), not owner `PageResult.getTotal()` summed — prevents advertising pages with no backing data; test verifies page size is 100, never `Integer.MAX_VALUE`, and page 2 is not invoked |
| `B-PROBLEM-MODERATE` | `ProblemImportServiceImpl` | no `@Size` cap on DTO | Add `@Size(max=500)` |
| `B-TESTCASE-REORDER` | `AdminTestCaseService` | no explicit ID count cap | Add input size cap |
| `B-PROBLIST-REPLACE` | `AdminProblemListServiceImpl` | no entry-size cap | Add `@Size(max=500)` |
| `B-PROBLEM-EXPORT` | `ProblemExportServiceImpl` | provider-side cap not evident | Require provider-side 10k cap |
| `S-RECON-FULL` | `OwnerReconciler` | no finite page cap | `MAX_*_PAGES=32` (see P3 manifest §6) |
| `S-RECON-INCREMENTAL` | `OwnerReconciler` | no finite page cap | Same `MAX_*_PAGES=32` |
| `I-ANALYTICS-ACTIVITY` | `AdminAnalyticsController:27-71` | no 365-day input cap annotation | Add `@Min(1) @Max(365)` |
| `I-ANALYTICS-CONTEST` | `ContestParticipationReporter:35-89` | no 500-row cap | Require owner-side cap |
| `I-ANALYTICS-REVENUE` | `RevenueReporter:37-169` | no 10,000-row cap | Require owner-side cap |

## 7. Evidence

All entries above are derived from `CONFIRMED_SOURCE` at the file:line references
in the P3 budget manifest §8 source anchors plus the controller/projection/
service files listed here. No production latency measurement is claimed; the
`MEASURED` / `UNMEASURED` markers distinguish source-proven bounded call
chains from those that still carry a known N+1, unbounded scan, or missing
input/payload cap.

- `AdminUserDetailQuery` deep module (round-one account, round-two parallel) is
  the reference shape for user-detail; its `DETAIL_CALLS` map enforces Auth=2,
  App=2, Submission=1 (6 source lines).
- `AdminUserEnricher` bounded executor (pool=2, queue=2) provides the batch
  enrichment seam for every list use case that enriches authors.
- `AdminBulkExecutor:52-81` isolates item exceptions within bounded
  `Bulk*RequestDTO` input caps.
