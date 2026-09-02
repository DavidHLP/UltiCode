# P3-ADMIN-001 Admin RPC / Latency / Freshness Budget Manifest

> status: DEFINED
> owner: ADMIN
> baseline: [`P0-BASELINE-003-admin-rpc-graph.md`](P0-BASELINE-003-admin-rpc-graph.md)
> policy_source: `services/platform/common/src/main/java/com/ulticode/common/rpc/RpcPolicy.java`
> scope: repository Admin adapters, projections, cutover services, and reconciliation caller
> implementation_change: none (manifest only)
> evidence_level: Repository source + disposable validation target; no production evidence

This manifest turns the P0 Admin call graph into explicit, reviewable budgets. It is a
repository contract for later implementation work, not a measured latency objective.
Owner boundaries remain unchanged: Auth owns accounts/authorization, App owns profile and
App domains, Submission owns submission facts, Notification owns notification facts, and
Admin owns orchestration, projections, audit, and reconciliation run state.

## 1. Budget vocabulary and policy constants

The tables use the following machine-readable-like fields:

- `L` (`max_logical_rpcs`) is the maximum number of owner-contract calls made by one
  use-case. A retry attempt is **not** another logical RPC.
- `R` (`serial_rounds`) is the maximum number of sequential call groups. A parallel
  fan-out is one round; calls inside it are concurrent. A serial loop has one round per
  logical call.
- `wall_budget_ms` is the retry-inclusive arithmetic envelope:
  `sum(max(logical_budget_ms) for each serial round)`. It is intentionally conservative
  and excludes local CPU/JSON work and production queueing.
- `attempts_max` is derived from the policy: each query logical call has at most two
  transport attempts; each write has one.
- `current_shape` records what the current source does. `target_*` is the bounded target
  this manifest requires. `FAIL_*` means the current source does not yet meet that target;
  this document does not silently treat the source as compliant.

| policy | timeout_ms per attempt | retries | attempts per logical call | logical_budget_ms | use |
| --- | ---: | ---: | ---: | ---: | --- |
| `Q` query | 800 | 1 | 2 | 1,600 | read-only owner RPC |
| `W` write | 3,000 | 0 | 1 | 3,000 | mutating owner RPC |
| `X` execution | 190,000 | 0 | 1 | 190,000 | not used by the Admin use-cases below |
| `P` local | n/a | n/a | n/a | 0 RPC budget | Admin-local store/mapper only |

`Q` is `RpcPolicy.QUERY_TIMEOUT_MS=800`, `QUERY_RETRIES=1`, and
`QUERY_TOTAL_BUDGET_MS=1600`; `W` is `WRITE_TIMEOUT_MS=3000`, `WRITE_RETRIES=0`, and
`WRITE_TOTAL_BUDGET_MS=3000`. These are source constants, not SLOs
(`RpcPolicy.java:63-97`). The dependency filter's bulkhead (`32`), circuit threshold
(`5`), and open interval (`30s`) reject or isolate calls but do not enlarge a use-case
wall budget (`RpcPolicy.java:99-106`, `DubboDependencyResilienceFilter.java:20-27`).

The dashboard and analytics fan-out executors have bounded pools of `4` and `6`
respectively, and cancel pending work on timeout (`CancellableQueryExecutor.java:18-27,51-65`).
Their current outer `CompletableFuture` wait is `800ms`
(`DefaultAdminDashboardReadAdapter.java:78-113`,
`DefaultAdminAnalyticsPortAdapter.java:165-210`), which can fail a caller before the
retry-inclusive `Q=1600ms` arithmetic envelope. The manifest records the policy envelope,
not that implementation detail as a production guarantee.

## 2. Freshness and degradation vocabulary

| code | meaning |
| --- | --- |
| `REQ` | Direct owner read at request time; no cache or cross-owner snapshot is claimed. |
| `NOW` | Dashboard App/Submission calls receive the same `now`; Auth summary is read at invocation time and has no supplied observation timestamp. |
| `WM` | Incremental reconciliation uses the caller-supplied inclusive `createdSince` watermark. |
| `CRON` | Full reconciliation is scheduled at `02:00` (`0 0 2 * * *`). |
| `WRB` | A successful owner write is followed by a read-back; owner commit is authoritative and a missing/stale read-back is not retried. |
| `LOCAL` | Admin-owned store or mapper read at request time. |
| `OK` / `EMPTY` | All required sources answered; an empty result is business-empty, not an outage. |
| `PARTIAL` | A non-critical enrichment source failed while a usable subset remains; the source must carry `DegradationStatus.PARTIAL` or an item-level reason. |
| `UNAVAILABLE` | A required owner failed, returned null/invalid data, or all enrichment sources failed; surface the typed Admin owner-unavailable error. |
| `ITEM_PARTIAL` | A bounded batch continues after an item failure and returns per-item success/failure; no automatic retry is added. |
| `FAILED` / `SKIPPED` | Reconciliation run failed closed, or did not run because its fenced lease was busy. |

Current source is authoritative where it is stricter or less expressive than the P0
summary. In particular, `DefaultAdminDashboardReadAdapter` throws `Dashboard owner
unavailable` when any required stats source fails; it does not currently return a typed
partial stats object (`.../DefaultAdminDashboardReadAdapter.java:84-113`).
`AdminUserEnricher.enrichWithStatus` does provide `OK/PARTIAL/UNAVAILABLE`, while
`enrich` throws when both sources are unavailable (`AdminUserEnricher.java:188-245`).

## 3. Interactive read manifest

`wall_budget_ms` values below use the target call shape. `current_shape` is included so
N+1 and unbounded scans remain visible instead of being hidden by a target number.

| id | HTTP/use-case | target L / R / wall_budget_ms | current_shape | policy / freshness | partial and unavailable semantics | source |
| --- | --- | ---: | --- | --- | --- | --- |
| `I-DASH-STATS` | `GET /admin/dashboard/stats` | `3 / 1 / 1600` | `3` parallel calls: App dashboard, Submission dashboard, Auth account summary | `Q`; `NOW` | Any required owner failure, null, or timeout is `UNAVAILABLE`; current response is all-or-nothing, not partial | `[E-DASH]` |
| `I-DASH-CHART-OWNER` | Dashboard chart for `submissions`, `problems`, `contests`, `solutions`, or `forum_posts` | `1 / 1 / 1600` | One owner chart RPC | `Q`; `REQ` | Null rows or owner failure is `UNAVAILABLE`; valid empty buckets are `OK/EMPTY` | `[E-DASH]` |
| `I-DASH-CHART-USERS` | Dashboard `users` chart / user trend | **`1 / 1 / 1600`** | `N=ceil(account_count/100)` Auth page RPCs, serial; `N` is unbounded | `Q`; `REQ` | Auth failure is `UNAVAILABLE`; successful zero accounts is `OK/EMPTY` | `[E-TREND]` |
| `I-USER-LIST` | `GET /admin/users` | `2 / 2 / 3200` | Auth account page, then one App profile batch | `Q`; `REQ` | Empty Auth page is `OK/EMPTY`; profile failure is `PARTIAL`; account failure is `UNAVAILABLE` | `[E-USER]` |
| `I-USER-DETAIL` | `GET /admin/users/{id}` and current-admin profile | `8 / 8 / 12800` | Account + profile + 4 user-stat calls + role-template + direct-permission calls, serial | `Q`; `REQ` | Account not-found maps to `USER_NOT_FOUND`; account failure `UNAVAILABLE`; profile is `PARTIAL`; current stats provider failures can propagate while role/direct-permission failures may be omitted without a typed status, so they remain a P3-ADMIN-004 follow-up | `[E-USER]` |
| `I-WS-AUTH` | Admin WebSocket account-state authentication lookup | `1 / 1 / 1600` | One Auth `IdentityQueryService.getIdentity` lookup | `Q`; `REQ` | Missing/unavailable/invalid identity returns empty and denies authentication; it is not a successful empty business result | `[E-WS]` |
| `I-CONTEST-LIST` | `GET /admin/contest` | `2 / 2 / 3200` | **Current `1 + rows` calls**, at most `101` with the shared page cap: page plus one `countProblemsByContestId` per row (`161600ms` arithmetic) | `Q`; `REQ` | Required owner failure `UNAVAILABLE`; target requires one page read plus owner-side/batch problem counts | `[E-CONTEST]` |
| `I-CONTEST-DETAIL` | `GET /admin/contest/{id}` | `2 / 2 / 3200` | Contest read plus problem-count read | `Q`; `REQ` | Missing contest `NOT_FOUND`; owner failure `UNAVAILABLE` | `[E-CONTEST]` |
| `I-CONTEST-RANKINGS` | `GET /admin/contest/{id}/rankings` | `2 / 2 / 3200` | Contest existence read, then live-ranking page | `Q`; `REQ` | Missing contest `NOT_FOUND`; ranking owner failure `UNAVAILABLE` | `[E-CONTEST]` |
| `I-CONTEST-ANNOUNCEMENTS` | Contest announcement list | `1 / 1 / 1600` | One announcement read | `Q`; `REQ` | Owner failure `UNAVAILABLE`; empty list `OK/EMPTY` | `[E-CONTEST]` |
| `I-FORUM-LIST` | `GET /admin/forum/posts` | `3 / 3 / 4800` | App post page, then Auth identity batch and App profile batch via `AdminUserEnricher` | `Q`; `REQ` | Post owner failure `UNAVAILABLE`; author enrichment is `PARTIAL` where the post page remains usable; both enrichment sources unavailable is `UNAVAILABLE` for `enrich` callers | `[E-FORUM]` |
| `I-FORUM-DETAIL` | `GET /admin/forum/posts/{id}` | `4 / 4 / 6400` | App post read plus `enrichOne` worst case: account, identity, profile | `Q`; `REQ` | Missing post `NOT_FOUND`; missing author data may be absent/partial; all required owner failures `UNAVAILABLE` | `[E-FORUM]` |
| `I-FORUM-COMMUNITIES` | Forum community list | `1 / 1 / 1600` | One App community-page read | `Q`; `REQ` | Owner failure `UNAVAILABLE`; empty list `OK/EMPTY` | `[E-FORUM]` |
| `I-NOTIFY-LIST` | `GET /admin/notifications` | `3 / 3 / 4800` | Notification page, then creator identity/profile enrichment | `Q`; `REQ` | Notification page failure `UNAVAILABLE`; creator enrichment `PARTIAL` with rows preserved; both enrichment sources `UNAVAILABLE` | `[E-NOTIFY]` |
| `I-SOLUTION-LIST` | `GET /admin/solutions` and flagged list | `4 / 4 / 6400` | Solution page, Auth identity/profile batches, App problem batch | `Q`; `REQ` | Required page failure `UNAVAILABLE`; enrichment may be partial only if response carries a reason; empty page `OK/EMPTY` | `[E-SOLUTION]` |
| `I-SOLUTION-DETAIL` | `GET /admin/solutions/{id}` | `5 / 5 / 8000` | Solution read, `enrichOne` (up to 3), problem read | `Q`; `REQ` | Missing solution `NOT_FOUND`; author/profile and problem owner failures are unavailable unless the response explicitly marks a partial detail | `[E-SOLUTION]` |
| `I-SUBMISSION-LIST` | `GET /admin/submissions` | `4 / 4 / 6400` | Submission page, Auth identity/profile batches, App problem batch | `Q`; `REQ` | Required page `UNAVAILABLE`; enrichment partial only with an explicit reason; empty page `OK/EMPTY` | `[E-SUBMISSION]` |
| `I-SUBMISSION-DETAIL` | `GET /admin/submissions/{id}` | `5 / 5 / 8000` | Submission read, `enrichOne` (up to 3), problem read | `Q`; `REQ` | Missing submission `NOT_FOUND`; owner failure `UNAVAILABLE` unless typed partial detail is added | `[E-SUBMISSION]` |
| `I-SUBMISSION-STATS` | `GET /admin/submissions/statistics` | `5 / 5 / 8000` | Five Submission aggregation calls, serial | `Q`; `REQ` | Any required aggregate failure `UNAVAILABLE`; no partial statistics contract exists | `[E-SUBMISSION]` |
| `I-SUBMISSION-FILTERS` | Submission status/language options | `1 / 1 / 1600` (`statuses` is `0`) | `languages` is one Submission read; statuses are local enum shaping | `Q` or `P`; `REQ`/`LOCAL` | Language owner failure `UNAVAILABLE`; empty language list `OK/EMPTY` | `[E-SUBMISSION]` |
| `I-PROBLEM-READ` | Problem list, detail, header, description, code, cases, flagged list | `1 / 1 / 1600` per endpoint | One App Problem read per endpoint | `Q`; `REQ` | Missing row `NOT_FOUND`; owner failure `UNAVAILABLE`; empty list `OK/EMPTY` | `[E-PROBLEM]` |
| `I-PROBLEM-SUBMISSIONS` | Problem submission tab | `2 / 2 / 3200` | Problem existence read plus Submission page read | `Q`; `REQ` | Missing problem `NOT_FOUND`; either owner failure `UNAVAILABLE` | `[E-PROBLEM]` |
| `I-TESTCASE-READ` | Test-case list, detail, and export | `2 / 2 / 3200` per endpoint | Problem existence read plus one App Problem read-port call | `Q`; `REQ` | Missing problem/case `NOT_FOUND`; owner failure `UNAVAILABLE`; empty list `OK/EMPTY` | `[E-TESTCASE]` |
| `I-PROBLEM-LIST-LIST` | `GET /admin/problem-lists` | `3 / 3 / 4800` | **Current up to `301` serial calls**: list read plus `enrichOne` (up to 3 calls) for each of 100 rows (`481600ms` arithmetic) | `Q`; `REQ` | List failure `UNAVAILABLE`; target uses one batched author enrichment; current N+1 shape is `OVER_TARGET` | `[E-PROBLIST]` |
| `I-PROBLEM-LIST-DETAIL` | `GET /admin/problem-lists/{id}` | `4 / 4 / 6400` | Problem-list detail read plus `enrichOne` | `Q`; `REQ` | Missing list `NOT_FOUND`; author/owner failure must be typed `UNAVAILABLE` or `PARTIAL`, not an empty list | `[E-PROBLIST]` |
| `I-COMMENT-TYPED` | Comment list/detail with `type=forum` or `solution` | `4 / 4 / 6400` | Comment page, batched author enrichment (2), parent-title batch | `Q`; `REQ` | Missing detail `NOT_FOUND`; page owner failure `UNAVAILABLE`; author enrichment can be `PARTIAL` with rows preserved | `[E-COMMENT]` |
| `I-COMMENT-ALL` | Comment list without a type | `8 / 8 / 12800` | Two moderators, four calls each, serial; each moderator is currently asked for `Integer.MAX_VALUE` rows | `Q`; `REQ` | One moderator failure currently fails the aggregate; target keeps a per-moderator reason and caps each page at 100; current payload bound is `FAIL_PAYLOAD_BOUND` | `[E-COMMENT]` |
| `I-TAG-READ` | Tag list/detail for either Problem or Forum | `1 / 1 / 1600` | One owner read | `Q`; `REQ` | Missing tag `NOT_FOUND`; owner failure `UNAVAILABLE`; empty page `OK/EMPTY` | `[E-TAG]` |
| `I-ANALYTICS-OVERVIEW` | `GET /admin/analytics` | `6 / 1 / 1600` | Six query slices in parallel (Auth, Submission x3, App Contest, App Subscription) | `Q`; `REQ` | Current all-or-nothing `UNAVAILABLE`; system sample is local and does not add RPCs | `[E-ANALYTICS]` |
| `I-ANALYTICS-ACTIVITY` | `GET /admin/analytics/user-activity` | `11 / 11 / 17600` | Daily, weekly, retention x6, hourly, top-users, optional Auth identity batch; serial | `Q`; `REQ` | Submission aggregate failure `UNAVAILABLE`; Auth username failure degrades usernames to `Unknown`; target input window is `1..365` days | `[E-ANALYTICS]` |
| `I-ANALYTICS-PROBLEM` | Problem completion report | `1 / 1 / 1600` | One App analytics read | `Q`; `REQ` | Owner failure `UNAVAILABLE`; empty report is `OK/EMPTY` | `[E-ANALYTICS]` |
| `I-ANALYTICS-CONTEST` | Contest participation report | `2 / 2 / 3200` | App contest list plus one participant batch | `Q`; `REQ` | Owner failure `UNAVAILABLE`; target caps owner result rows at 500; current list/payload cap is not owner-enforced | `[E-ANALYTICS]` |
| `I-ANALYTICS-REVENUE` | Revenue report | `1 / 1 / 1600` | One active-subscription list read; report math is local | `Q`; `REQ` | Owner failure `UNAVAILABLE`; target caps returned subscriptions at 10,000; current owner result cap is not visible | `[E-ANALYTICS]` |
| `I-ANALYTICS-PERFORMANCE` | Performance report | `0 / 0 / 0` | JVM/OS sample only | `P`; `LOCAL` | Local sampling failure is local Admin failure, not an RPC partial | `[E-ANALYTICS]` |
| `I-AUDIT` | Audit logs, stats, and export | `2 / 2 / 3200` | Admin mapper query plus one `AdminUserEnricher.enrich` (2 Q) when actor/user IDs exist | `Q` + `P`; `REQ`/`LOCAL` | Audit rows remain local; user enrichment may be `PARTIAL`; both enrichment sources unavailable is `UNAVAILABLE` | `[E-LOCAL]` |
| `I-SETTINGS` | Admin settings reads/writes and cache/maintenance commands | `0 / 0 / 0` | Admin-owned store only; `GET /all` is one local batched store read | `P`; `LOCAL` | Local store error is an Admin-local error; no owner partial semantics | `[E-LOCAL]` |

### Interactive read notes

1. Shared `PaginationRequest` caps a page at 100 (`PaginationRequest.java:37-84`).
   That bounds page row count but does not make a per-row RPC loop cheap.
2. The P0 graph correctly identifies Dashboard stats as a three-owner parallel fan-out
   and the user trend as serial Auth paging (`P0-BASELINE-003:35-45`). The current
   user-trend value is deliberately marked `FAIL_UNBOUNDED_SCAN`; P3-ADMIN-002 must
   replace it with one Auth-owned aggregate call.
3. `DefaultAdminContestProjection` calls `countProblemsByContestId` while mapping every
   list row (`DefaultAdminContestProjection.java:37-50,63-84`).
   `DefaultAdminProblemListProjection` calls `enrichOne` while mapping every row
   (`DefaultAdminProblemListProjection.java:51-70,108-115`). Their target columns are
   bounded contracts, not claims that the current N+1 implementations already meet them.
4. Analytics controller `days` has no current upper-bound annotation
   (`AdminAnalyticsController.java:27-71`). The target `365`-day input cap is therefore
   a manifest requirement; it is not current validation evidence.

## 4. Interactive owner-write manifest

Every `W` entry below has `timeout_ms=3000` and **`retries=0`**. Query preflights and
read-backs are shown explicitly as `Q`; a write is never made safe by automatic retry.
For a successful write followed by a failed read-back, report the write as potentially
committed and return an explicit ambiguous/read-back-unavailable error rather than
silently retrying the mutation.

| id | operation family | target L / R / wall_budget_ms | current call shape | per-call policy / freshness | partial and unavailable semantics | source |
| --- | --- | ---: | --- | --- | --- | --- |
| `W-ONE-SHOT` | One owner command: Submission rejudge (single/batch), Forum toggle/flag, comment mutation, Forum tag mutation, or one cutover add/remove | `1 / 1 / 3000` | One owner write | `W`; `WRB` only when the HTTP method explicitly reads back | Single command failure is `UNAVAILABLE`/typed owner error; no partial single write | `[E-WRITE]` |
| `W-USER-CREATE` | Create user, including optional profile and response read-back | `12 / 12 / 22000` | Two Auth uniqueness queries + Auth create + optional App profile write + up to eight read-back RPCs | `Q` preflight/read-back + `W` writes; `WRB` | Conflict is validation; owner failure stops the operation; profile/write or read-back failure is not retried and must be explicit | `[E-USER-WRITE]` |
| `W-USER-UPDATE` | Update user credentials/profile/optional role and read back | `14 / 14 / 26600` | Up to three Auth reads, three writes (Auth credentials, App profile, Auth role), then eight read-back RPCs | `Q` + `W`; `WRB` | Any failed required write stops; read-back ambiguity must not replay writes | `[E-USER-WRITE]` |
| `W-USER-DELETE-RESET` | Delete account, reset password, or self password change | `1 / 1 / 3000` | One Auth write | `W`; request-time command | Failure is all-or-nothing at this boundary; no automatic retry | `[E-USER-WRITE]` |
| `W-USER-PERMISSION` | Grant/revoke direct permission and return user detail | `20 / 20 / 33400` | `enrichOne` up to 3 Q + before detail up to 8 Q + one Auth write + after detail up to 8 Q | `Q` + `W`; `WRB` | Missing user `NOT_FOUND`; Auth write failure stops; read-back ambiguity explicit; no write retry | `[E-PERM]` |
| `W-PROFILE` | Direct `ProfileWriteService` adapter call, including avatar URL update | `1 / 1 / 3000` | One App profile write; avatar file persistence is local before one URL write | `W`; request-time command | File cleanup on failed avatar write; App failure `UNAVAILABLE`; no retry | `[E-PROFILE]` |
| `W-CONTEST-READBACK` | Contest create/update/start/end | `3 / 3 / 6200` | One App write + contest read + problem-count read | `W` + `Q` + `Q`; `WRB` | Owner command failure stops; read-back ambiguity explicit | `[E-CONTEST-WRITE]` |
| `W-CONTEST-ONE` | Contest delete/add/remove problem | `1 / 1 / 3000` | One App write | `W`; request-time command | Typed owner failure; no partial single write | `[E-CONTEST-WRITE]` |
| `W-PROBLEM-CREATE` | Problem create and read by slug | `2 / 2 / 4600` | App write + App read | `W` + `Q`; `WRB` | Owner failure/ambiguous read-back explicit | `[E-PROBLEM-WRITE]` |
| `W-PROBLEM-UPDATE-STATE` | Problem update/publish/unpublish | `3 / 3 / 6200` | Version read + App write + read-back | `Q` + `W` + `Q`; `WRB` | Version conflict is typed `CONFLICT`; no write retry | `[E-PROBLEM-WRITE]` |
| `W-PROBLEM-DELETE` | Problem delete | `2 / 2 / 4600` | Version read + App write | `Q` + `W`; request-time command | Missing/version conflict typed; no retry | `[E-PROBLEM-WRITE]` |
| `W-NOTIFY-CREATE` | Notification-owner create and creator-enriched read-back | `4 / 4 / 7800` | Notification write + read + Auth identity/profile enrichment | `W` + `Q` x3; `WRB` | Owner write failure stops; read-back/creator failure explicit partial or unavailable | `[E-NOTIFY-WRITE]` |
| `W-NOTIFY-UPDATE` | Notification-owner update and creator-enriched read-back | `5 / 5 / 9400` | Pre-read + Notification write + read-back + Auth identity/profile enrichment | `Q` + `W` + `Q` x2; `WRB` | Missing row `NOT_FOUND`; no replay after ambiguous write | `[E-NOTIFY-WRITE]` |
| `W-NOTIFY-DELETE` | Notification-owner delete | `2 / 2 / 4600` | Pre-read + Notification write | `Q` + `W`; request-time command | Missing row `NOT_FOUND`; owner failure typed | `[E-NOTIFY-WRITE]` |
| `W-SOLUTION-READBACK` | Solution flag/unflag and return detail | `6 / 6 / 11000` | Solution write + solution read + `enrichOne` up to 3 + problem read | `W` + `Q` x5; `WRB` | Write is authoritative; read-back failure explicit, never mutation-retried | `[E-SOLUTION-WRITE]` |
| `W-SOLUTION-DELETE` | Solution delete | `1 / 1 / 3000` | One App owner write | `W`; request-time command | Typed owner failure; no retry | `[E-SOLUTION-WRITE]` |
| `W-CONTENT-CUTOVER` | Forum/solution delete through ContentModerationService | `1 / 1 / 3000` | Feature-enabled path is one App moderation write; default flag-off forum path dispatches to local Admin code that performs an App post read plus moderation write (`2 / 2 / 4600`), while solution delete remains one App write | `W` or `Q` + `W`; request-time command | Remote path typed owner failure; local dispatch does not remove its downstream RPCs | `[E-CONTENT]` |
| `W-PROBLIST-CREATE` | Problem-list create | `1 / 1 / 3000` | One App owner write | `W`; request-time command | Typed owner failure; no partial single write | `[E-PROBLIST-WRITE]` |
| `W-PROBLIST-PREFLIGHT` | Problem-list update/delete/basic-info/visibility/banner/replace-problems | `2 / 2 / 4600` | Chain preflight read + one App owner write | `Q` + `W`; `WRB` only where response is the write result | Missing list `NOT_FOUND`; owner failure stops; replacement is one owner command | `[E-PROBLIST-WRITE]` |
| `W-TAG-FORUM` | Forum tag create/update/delete/merge | `1 / 1 / 3000` | One Forum tag mutation RPC | `W`; request-time command | Typed owner conflict/not-found; no partial single write | `[E-TAG]` |
| `W-TAG-PROBLEM` | Problem tag create/update/delete/merge | `3 / 3 / 6200` create/merge; `4 / 4 / 7800` update; `2 / 2 / 4600` delete | Problem tag conflict/read preflights plus one owner write | `Q` + `W`; request-time command | Conflict/not-found typed; no mutation retry | `[E-TAG]` |
| `W-TESTCASE-ONE` | Test-case create | `2 / 2 / 4600` | Problem existence read + App owner write | `Q` + `W`; request-time command | Missing problem `NOT_FOUND`; owner failure typed | `[E-TESTCASE]` |
| `W-TESTCASE-UPDATE` | Test-case update/delete/reorder | `3 / 3 / 6200` | Problem/test-case read(s) + one App owner write | `Q` x2 + `W`; request-time command | Validation/missing case typed; no partial single write | `[E-TESTCASE]` |

## 5. Scheduled and batch manifest

Bulk HTTP calls are listed here even when an admin initiates them interactively: their
latency and degradation semantics are batch semantics. Existing bulk loops are serial
and isolate item exceptions through `AdminBulkExecutor` (`AdminBulkExecutor.java:65-81`).
The target caps below are hard input caps. A current source path without the cap is
`FAIL_INPUT_BOUND`, even if its owner call count happens to be one.

| id | scheduled/batch use-case | target input cap | target L / R / wall_budget_ms | current shape | per-call policy / freshness | partial and unavailable semantics | source |
| --- | --- | ---: | ---: | --- | --- | --- | --- |
| `B-USER-BAN` | Bulk ban or unban users | 100 IDs | `1000 / 1000 / 1740000` (`900Q + 100W`) | One account read + state write + up to eight detail reads per ID, serial | `Q` + `W`; `WRB` per item | `ITEM_PARTIAL`: one item failure is recorded and later IDs continue; no retries | `[E-USER-WRITE]`, `[E-BULK]` |
| `B-USER-DELETE` | Bulk delete users | 100 IDs | `100 / 100 / 300000` | One Auth write per ID, serial | `W`; request-time command | `ITEM_PARTIAL`; no retries | `[E-USER-WRITE]`, `[E-BULK]` |
| `B-FORUM-TOGGLE` | Bulk forum pin/unpin/lock/unlock/unflag | 100 IDs | `100 / 100 / 300000` | One App write per ID, serial | `W`; request-time command | `ITEM_PARTIAL`; audit remains per successful item | `[E-FORUM-WRITE]`, `[E-BULK]` |
| `B-FORUM-DELETE` | Bulk forum delete | 100 IDs | `200 / 200 / 460000` | App post read + App moderation write per ID | `Q` + `W`; request-time command | `ITEM_PARTIAL`; no write retry | `[E-FORUM-WRITE]`, `[E-BULK]` |
| `B-COMMENT-DELETE` | Bulk forum/solution comment delete | 100 IDs | `100 / 100 / 300000` | One comment-owner write per ID | `W`; request-time command | `ITEM_PARTIAL`; per-item outcome | `[E-COMMENT]`, `[E-BULK]` |
| `B-COMMENT-UNFLAG` | Bulk comment unflag | 100 IDs | `500 / 500 / 940000` | One write + four-call detail read per ID, serial | `W` + `Q` x4; `WRB` | `ITEM_PARTIAL`; no replay after an ambiguous write | `[E-COMMENT]`, `[E-BULK]` |
| `B-SOLUTION-SIMPLE` | Solution publish/unpublish/delete | 100 IDs | `101 / 101 / 301600` (`1Q` precheck + `100W`) | One batched existence read plus one owner write per existing ID | `Q` + `W`; request-time command | `ITEM_PARTIAL`; missing IDs are not written | `[E-SOLUTION-WRITE]`, `[E-BULK]` |
| `B-SOLUTION-UNFLAG` | Solution unflag with response read-back | 100 IDs | `501 / 501 / 1101600` | One existence read + per ID write and five-call detail read | `Q` + (`W` + `Q` x5) x100; `WRB` | `ITEM_PARTIAL`; no automatic mutation retry | `[E-SOLUTION-WRITE]`, `[E-BULK]` |
| `B-PROBLEM-PUBLISH` | Problem publish/unpublish | 500 IDs | `1500 / 1500 / 3100000` | Three calls per ID (version read, write, read-back), serial | `Q` + `W` + `Q`; `WRB` | `ITEM_PARTIAL`; each item may conflict independently | `[E-PROBLEM-WRITE]`, `[E-BULK]` |
| `B-PROBLEM-DELETE` | Problem delete | 500 IDs | `1000 / 1000 / 2300000` | Version read + write per ID | `Q` + `W`; request-time command | `ITEM_PARTIAL`; no write retry | `[E-PROBLEM-WRITE]`, `[E-BULK]` |
| `B-PROBLEM-RESTORE` | Problem restore | 500 IDs | `500 / 500 / 1500000` | One owner write per ID | `W`; request-time command | `ITEM_PARTIAL`; no retry | `[E-PROBLEM-WRITE]`, `[E-BULK]` |
| `B-PROBLEM-EDIT` | Bulk problem difficulty edit | 500 IDs | `1000 / 1000 / 2300000` | Problem existence read + difficulty write per ID | `Q` + `W`; request-time command | `ITEM_PARTIAL`; invalid item is isolated | `[E-PROBLEM-WRITE]`, `[E-BULK]` |
| `B-PROBLEM-MODERATE` | Batch moderate flagged problems | **500 IDs target** | `1 / 1 / 3000` | Current DTO has no `@Size` cap; one owner batch write but unbounded input payload (`FAIL_INPUT_BOUND`) | `W`; request-time command | Target is one owner atomic batch result; current source does not establish a max input size | `[E-PROBLEM-WRITE]` |
| `B-PROBLEM-IMPORT` | Problem import | 500 items | `2 / 2 / 4600` | One slug lookup batch + one `applyImportedBatch` owner write | `Q` + `W`; request-time command | Per-item results from the owner; read failure marks all items failed; no retry | `[E-IMPORT]` |
| `B-TESTCASE-APPEND` | Append test cases | 500 items | `501 / 501 / 1501600` | Problem existence read + one owner insert per item, serial | `Q` + `W` x500; request-time command | Current service aborts on a thrown insert; target must make this explicit or return item outcomes | `[E-TESTCASE]` |
| `B-TESTCASE-REPLACE` | Replace all test cases | 500 items | `2 / 2 / 4600` | Problem existence read + one atomic owner replacement write | `Q` + `W`; request-time command | Owner-side replacement is atomic; failure leaves the owner transaction failed | `[E-TESTCASE]` |
| `B-TESTCASE-REORDER` | Reorder test cases | 500 IDs target | `3 / 3 / 6200` | Problem read + batched case read + one owner order write; current request has no explicit max ID count (`FAIL_INPUT_BOUND`) | `Q` x2 + `W`; request-time command | Missing/duplicate case fails the operation; no partial reorder | `[E-TESTCASE]` |
| `B-REJUDGE` | Batch submission rejudge | 50 IDs | `1 / 1 / 3000` | One Submission owner batch command | `W`; request-time command | Owner returns per-submission results; `ITEM_PARTIAL`; no automatic retry | `[E-REJUDGE]` |
| `B-PROBLIST-REPLACE` | Replace problem-list entries | **500 entries target** | `2 / 2 / 4600` | Current DTO has no entry-size cap; preflight read + one owner write (`FAIL_INPUT_BOUND`) | `Q` + `W`; request-time command | Target is one owner command; invalid/missing list fails before write | `[E-PROBLIST-WRITE]` |
| `B-PROBLEM-EXPORT` | Problem export | 10,000 output rows | `1 / 1 / 1600` | One `listAllProblems` read is truncated to 10,000 only after the provider returns; provider-side payload cap is not evidenced (`FAIL_PAYLOAD_BOUND`) | `Q`; `REQ` | Owner failure `UNAVAILABLE`; target requires owner-side result cap before transport | `[E-EXPORT]` |

`B-PROBLEM` is capped at 500 by `BulkProblemRequestDTO`; user/forum/comment/solution
bulk requests are capped at 100; batch rejudge is capped at 50; problem import and
bulk test-case import are capped at 500 (`BulkProblemRequestDTO.java:17-31`,
`BulkUserActionRequest.java:12-17`, `BulkActionRequest.java:17-29`,
`BulkCommentActionRequest.java:15-36`, `BulkSolutionActionDto.java:19-37`,
`BatchRejudgeRequest.java:22-43`, `ImportProblemsRequestDTO.java:14-20`,
`BulkImportTestCasesDTO.java:18-30`).

## 6. Scheduled reconciliation budget

P0 identifies reconciliation as an Auth + App + Submission + Notification use-case. The
current implementation has correct owner facts and cursor/page validation, but its full
and incremental loops stop only when the owner returns an empty page. Page size `500`
is bounded; total pages are not (`OwnerReconciler.java:311-465`). This manifest therefore
uses a finite planning cap and fails closed when it is exceeded.

The target constants are deliberately repository/disposable limits, not production SLOs:
`MAX_OWNER_NONEMPTY_PAGES=32` for Submission and Notification, and `MAX_AUDIT_PAGES=32` for
the Admin audit candidate scan. A capped run processes at most 16,000 grouped owner facts per
owner stream and 16,000 audit candidate rows before failing with its last cursor/offset
for a resumable run. Each owner stream also permits one terminal empty-page read; that
terminator is included in the RPC budget below.

| id | scheduled mode | target scan cap | target L / R / wall_budget_ms | current shape | per-call policy / freshness | result semantics | source |
| --- | --- | --- | ---: | --- | --- | --- | --- |
| `S-BOOTSTRAP-ADMIN` | Explicit production-safe one-shot admin bootstrap CLI | one invocation | `5 / 5 / 9400` (`2Q` role counts + `2Q` identity checks + `1W` create) | `AdminBootstrapRunner` performs those checks serially, then one Auth create | `Q` + `W`; `REQ` | Existing admin or identity conflict aborts before write; Auth failure aborts; no write retry | `[E-BOOTSTRAP]` |
| `S-DEV-BOOTSTRAP` | Explicit dev-only create/restore admin runner | one invocation | `8 / 8 / 18400` worst case (`4Q + 4W` restore path) | Username/email checks, then create (`3` calls) or restore (`up to 8` calls), serial; dev profile/property gated | `Q` + `W`; `REQ` | Conflict aborts; restore/write failure aborts; dev-only path is not a production SLO | `[E-BOOTSTRAP]` |
| `S-RECON-FULL` | Nightly full reconciliation | Submission `32` non-empty pages + one terminator, Notification `32` non-empty pages + one terminator, audit `32` pages; page size `500` | **`164 / 164 / 262400`** (`1` Auth orphan aggregate + `1` App orphan aggregate + `65` Submission/Auth calls + `65` Notification/Auth calls + `32` audit/Auth calls) | `1 + 2P_submission + 2P_notification + P_audit` with no finite `P`; current `max_logical_rpcs=UNBOUNDED`, `FAIL_UNBOUNDED_SCAN` | `Q`; `CRON`; facts are full-history (`createdSince=null`) | Owner/null/ordering/lease failures persist `FAILED`; no partial `COMPLETED`; lease busy is `SKIPPED` | `[E-RECON]` |
| `S-RECON-INCREMENTAL` | Manual/invoked incremental reconciliation | Same `32/32/32` non-empty-page caps plus owner terminators; inclusive watermark required | **`164 / 164 / 262400`** | Same unbounded page loops if the watermark window is large; current `FAIL_UNBOUNDED_SCAN` | `Q`; `WM`; caller supplies `createdSince` | Exceeding a cap fails with cursor for retry as a new bounded run; owner failure `FAILED`; no automatic write retry | `[E-RECON]` |
| `S-RECON-LEASE-BUSY` | Scheduled/manual run when fenced lease is held | No owner scan | `0 / 0 / 0` | Lease acquisition returns null and exits | `P`; `CRON` or `WM` | `SKIPPED`, increment skip metric; never report success with fabricated facts | `[E-RECON]` |

`OwnerReconciler` currently calls Auth orphan aggregate, Submission and Notification paged
facts, App orphan aggregate, and Admin-local audit pages in serial order
(`OwnerReconciler.java:162-209,294-445`). The current source already persists `FAILED`,
`SKIPPED`, lease-loss, and completion state; this manifest adds the finite scan envelope
that the source does not yet enforce. `RECONCILIATION_PAIRS` is currently empty
(`OwnerReconciler.java:87-94`); any future pair must reserve additional `Q` calls inside
the same scheduled budget rather than silently expanding it.

## 7. Boundary and evidence rules

- The 800ms timeout, one query retry, 1.6s retry-inclusive logical query budget, 3s
  write budget, and all derived wall values are repository arithmetic. They are **not**
  production SLOs, p95/p99 claims, capacity proof, freshness proof, or availability
  proof. No production traffic, deployment, clock skew, provider queueing, or cross-owner
  snapshot evidence is claimed.
- This is a repository/disposable boundary. Later validation may use source tests and a
  disposable owner stack; production deployment authority, traffic volumes, external
  clocks, and real RPO/RTO/SLO evidence remain outside this manifest.
- `P0-BASELINE-003` reports 61 Admin references (App 33, Auth 18, Submission 6,
  Notification 4) and no Judge reference (`P0-BASELINE-003:7-31`). This manifest does
  not add a Judge dependency, a new transport, a new database owner, or a physical App
  split.
- Read budgets do not authorize cross-owner SQL or entity imports. Each listed call must
  remain behind the current Admin adapter/projection seam; writes remain owner-routed and
  use `W` with retries zero.
- Current failures intentionally remain visible for follow-up: user-trend paging is the
  named P3-ADMIN-002 replacement; N+1 contest/problem-list projections and missing batch
  input caps are implementation work, not silently accepted SLO exceptions.

## 8. Source anchors

| ref | current evidence |
| --- | --- |
| `E-P0` | `docs/architecture/evidence/P0-BASELINE-003-admin-rpc-graph.md:7-64` — 61 references, Dashboard three-owner fan-out, serial user trend, Enricher two serial batches, reconciliation/cutover grouping, and repository-not-production boundary. |
| `E-RPC` | `services/platform/common/src/main/java/com/ulticode/common/rpc/RpcPolicy.java:63-106` — query/write timeout, retry, total logical budgets, bulkhead, circuit. |
| `E-WRITE` | `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/*.java` `@DubboReference` policies; owner command adapters listed in the write-specific anchors below. |
| `E-DASH` | `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DefaultAdminDashboardReadAdapter.java:51-59,78-130,147-250`; `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultDashboardStatsProjection.java:41-75`. |
| `E-TREND` | `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DefaultAdminDashboardReadAdapter.java:162-250` — Auth page size 100 and serial scan until exhaustion. |
| `E-USER` | `services/admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java:74-157,188-245,275-361`; `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminUserProjection.java:37-121,149-223`; `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminUserStatsReadAdapter.java:24-50`. |
| `E-WS` | `services/admin/src/main/java/com/ulticode/admin/security/jwt/AccountReadAdapter.java:17-59` — Auth identity lookup and fail-closed WebSocket denial. |
| `E-USER-WRITE` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/UserManagementServiceImpl.java:70-339`; `services/admin/src/main/java/com/ulticode/modules/admin/controller/AdminAccountController.java:65-105`; `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/UserProvisioningAdapter.java:54-64`; `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminUserProfileAdapter.java:49-169`. |
| `E-BOOTSTRAP` | `services/admin/src/main/java/com/ulticode/modules/admin/bootstrap/AdminBootstrapRunner.java:27-69`; `DevUserBootstrapRunner.java:27-71`; `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/UserProvisioningAdapter.java:47-267`. |
| `E-PERM` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/UserPermissionServiceImpl.java:43-160`. |
| `E-CONTEST` | `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminContestProjection.java:31-84`; `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminContestServiceImpl.java:31-60`; adapters under `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DubboContest*.java`. |
| `E-CONTEST-WRITE` | `services/admin/src/main/java/com/ulticode/modules/admin/service/ContestCutoverService.java:40-182`. |
| `E-PROFILE` | `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminUserProfileAdapter.java:49-169` — App profile write and avatar file/URL sequence. |
| `E-FORUM` | `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminForumProjection.java:44-115`; `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DubboAdminForumReadAdapter.java:33-78`. |
| `E-FORUM-WRITE` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminForumServiceImpl.java:67-177`; `services/admin/src/main/java/com/ulticode/modules/admin/policy/impl/ForumPostFieldToggleImpl.java:35-86`; `ForumFlagPolicyImpl.java:35-106`. |
| `E-NOTIFY` | `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminNotificationProjection.java:63-146`; `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DubboNotificationAdminReadAdapter.java:27-52`. |
| `E-NOTIFY-WRITE` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminNotificationServiceImpl.java:63-196`; `services/admin/src/main/java/com/ulticode/modules/admin/service/NotificationCutoverService.java:49-253`. |
| `E-SOLUTION` | `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminSolutionProjection.java:50-149,193-246`; `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DubboSolution*.java`. |
| `E-SOLUTION-WRITE` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java:36-125`. |
| `E-SUBMISSION` | `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminSubmissionProjection.java:50-189,257-310`; `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DubboSubmissionAdminReadAdapter.java:28-98`. |
| `E-PROBLEM` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemServiceImpl.java:46-200`; `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DubboProblemAdminReadAdapter.java:32-134`. |
| `E-PROBLEM-WRITE` | `services/admin/src/main/java/com/ulticode/modules/admin/service/ProblemCutoverService.java:47-154`; `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemServiceImpl.java:103-189`. |
| `E-PROBLIST` | `services/admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminProblemListProjection.java:41-145`. |
| `E-PROBLIST-WRITE` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java:62-180,218-285,304-373`. |
| `E-COMMENT` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminCommentServiceImpl.java:97-186`; `ForumCommentModerator.java:52-101`; `SolutionCommentModerator.java:52-101`; `AdminCommentReadAdapter.java:35-63`. |
| `E-TAG` | `services/admin/src/main/java/com/ulticode/modules/admin/service/handler/ForumTagHandler.java:46-127`; `ProblemTagHandler.java:32-132`; `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DubboProblemTagOwnerAdapter.java:21-44`. |
| `E-TESTCASE` | `services/admin/src/main/java/com/ulticode/modules/admin/service/AdminTestCaseService.java:45-75,88-199,207-270`; `BulkImportTestCasesDTO.java:18-30`; `DubboTestCaseOwnerAdapter.java:17-56`. |
| `E-ANALYTICS` | `services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/DefaultAdminAnalyticsPortAdapter.java:53-210`; `DefaultUserActivityAnalyticsProjection.java:52-184`; `ContestParticipationReporter.java:35-89`; `RevenueReporter.java:37-169`; `AdminAnalyticsController.java:27-79`. |
| `E-LOCAL` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/SystemSettingsServiceImpl.java:58-162`; `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AuditServiceImpl.java:23-187`. |
| `E-BULK` | `services/admin/src/main/java/com/ulticode/modules/admin/bulk/AdminBulkExecutor.java:52-81`; request caps in `BulkUserActionRequest.java`, `BulkActionRequest.java`, `BulkCommentActionRequest.java`, `BulkSolutionActionDto.java`, `BulkProblemRequestDTO.java`, `BatchRejudgeRequest.java`, `ImportProblemsRequestDTO.java`, and `BulkImportTestCasesDTO.java`. |
| `E-RECON` | `services/admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java:68-209,294-465`; adapters `services/admin/src/main/java/com/ulticode/modules/reconciliation/port/adapter/Dubbo*ReconciliationReadAdapter.java:12-29`; `SubmissionReconciliationReadPort.java:17-30`; `NotificationReconciliationReadPort.java:17-30`. |
| `E-IMPORT` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/ProblemImportServiceImpl.java:56-189`; `ProblemOwnerPort.java:17-46`; `ImportProblemsRequestDTO.java:14-20`. |
| `E-EXPORT` | `services/admin/src/main/java/com/ulticode/modules/admin/service/impl/ProblemExportServiceImpl.java:46-73`; `services/admin/src/main/java/com/ulticode/modules/admin/service/ProblemExportService.java:20-30`. |
| `E-REJUDGE` | `services/admin/src/main/java/com/ulticode/modules/admin/service/SubmissionCutoverService.java:32-117`; `BatchRejudgeRequest.java:22-43`. |
| `E-CONTENT` | `services/admin/src/main/java/com/ulticode/modules/admin/service/ContentModerationCutoverService.java:25-101`. |
