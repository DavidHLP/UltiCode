# Resume

## Previous completed objective
修复 reviewer model 提出的全部 CR，并完成开发环境真实验证。

## Previous evidence
- Auth contract 16/0/0/0；真实 MySQL role-count mapper 2/0/0/0。
- App HiddenCaseLeakIT + Projection tests 14/0/0/0。
- Submission owner 真实 MySQL page-batching IT 8/0/0/0。
- Search worker unit 11/0/0/0；真实 Redis + Meili HTTP E2E 4/0/0/0。
- Affected reactor test BUILD SUCCESS；./mvnw verify -B BUILD SUCCESS。
- Fresh direct integration selector: 68 reports, 225 tests, 0 failures, 0 errors, 17 skips。
- graphify update . and git diff check passed。

## Current progress
- Initial context recovery completed; `AR20260820-001` is now `in_progress`.
- Implemented `scripts/dev/devstack-manifest.sh` and wired `up.sh` to run the
  deterministic `auth → admin → app → notification → submission` Owner chain,
  provision all local runtime accounts, and verify account connectivity before
  PM2.
- Added canonical DEV-LOCAL bootstrap adoption for shared-chain bootstrap-only
  tables, MySQL 9.1 root grant-shape compatibility, and explicit dev-full gate
  preservation; unknown schema/table shapes still fail closed.
- Focused checks: manifest, migration preflight, owner-safety integration,
  Compose dev/prod config, `bash -n`, `up.sh --help`, default profile
  assertions, fresh disposable MySQL 9.1 smoke, graphify update, and
  `git diff --check` all passed.
- Completion Audit passed for `AR20260820-001`; `AR20260820-002` is now the
  next dependency-free `ready` task.
- Recovery for `AR20260820-002` completed: current Dashboard path is
  `DashboardController → DashboardStatsProjection → DefaultDashboardStatsProjection → DashboardMapper`; the mapper directly selects App/Submission/Forum/Solution/Contest tables while Admin datasource/scan is Admin-owned.
- Planning decision: reuse existing owner read-contract patterns, add one
  Admin-owned Dashboard read seam, move App/Submission dashboard queries into
  their Owners, preserve the Dashboard HTTP VO and chart semantics, and fail
  closed on owner unavailability.
- Implementation complete: `DashboardMapper` removed; App and Submission
  owner contracts/providers now supply entity-free aggregates and chart data;
  Admin projection keeps the HTTP shape and uses one bounded parallel seam
  with an 800ms total deadline and fail-closed unavailable semantics.
- Focused seam tests, affected full reactor unit tests, App/Submission real
  MySQL owner ITs, MapperScan/foreign-SQL boundary checks, and graphify update
  passed. Ready for contextual review.
- Contextual review passed with Confirmed Findings=0. Tier C affected
  `verify` passed with 1399 tests / 0 failures / 0 errors / 13 skips; final
  YAML, boundary and diff checks passed. AR002 is ready for Completion Audit.
- Completion Audit passed for AR20260820-002; the Dashboard read seam task is
  `done` and AR20260820-003 is now `in_progress`.

## Active objective
完成 `/tmp/architecture-review-20260820-164414.html`，使用独立任务 ID `AR20260820-001..006`，顺序为 DevStack → Admin read seam → App Submission seam → Account/Profile schema → Search verification → final audit。

## Active task
`AR20260820-006`（done）：完成架构评审终态审计与开发交付收口。

`AR20260820-001` through `AR20260820-006` are `done` with development-only
evidence; the architecture-review objective is closed. No production
acceptance or external delivery is implied.

## AR20260820-003 progress
Implemented the shared `SubmissionRoutingProperties.select(...)` migration
policy and routed write, fence, and user-read wrappers through it. Focused
routing/property/feature-flag/wiring tests pass; local/remote/legacy adapters
and rollback dispatcher remain. Ready for contextual review.

Contextual review passed with Confirmed Findings=0. Tier C App verify passed
with 1401 tests / 0 failures / 0 errors / 13 skips; routing/config assertions,
graph refresh, YAML and diff checks passed. AR003 is ready for completion audit.
Completion Audit passed for AR20260820-003; AR004 is now `in_progress`.

## AR20260820-004 progress
Implemented the later Auth-owner contract migration
`V20260820180000__Narrow_Auth_Users_To_Account_Ownership.sql`; the original
expand migrations remain unchanged. Updated the owner profile backfill to use
an account-only Auth projection, preserve all App profile fields, include
soft-deleted rows in parity, upgrade manifests to version 2, and require an
explicit quiesced `contract-preflight` before contraction.

Auth schema contract IT (real MySQL 8.0), Auth reactor verify, App profile
writer/provider tests, MySQL 9.1 owner migration safety integration,
migration-preflight, shell syntax, graph refresh and diff checks passed.
Contextual review found Confirmed Findings=0. Completion Audit passed for
AR20260820-004; AR005 is now `in_progress`.

## AR20260820-005 progress
Added the test-only `SearchEventToQueryE2EIT` harness at the existing App
`SearchReadProjection` seam. It drives the existing worker with real Redis 7
and `getmeili/meilisearch:v1.8`, then proves event-to-query indexing, duplicate
UPSERT convergence, DELETE tombstone/stale suppression, full Redis DLQ envelope,
and Meili-unavailable DB fallback. `backend-search` is a test-scope dependency
only; no runtime service or writer changed.

Focused worker tests (15/0/0), App Search tests (32/0/0), real disposable E2E
(3/0/0), affected App verify (1401/0/0/13), Compose config with a disposable
key, control-plane YAML, graph refresh and diff checks passed. Contextual review
found Confirmed Findings=0. Completion Audit passed for AR20260820-005; AR006
is now `in_progress`.

## AR20260820-006 terminal audit
All five Candidate tasks are done with acceptance, rollback, Review=0
Confirmed Findings, validation and Coverage evidence recorded. Full services
`./mvnw verify -B` completed with BUILD SUCCESS; the final audit confirmed no
new runtime module expansion, preserved storage-free judge-runtime and all
rollback seams, and kept development/TEST-TARGET authority explicit. A direct
integration selector had fresh evidence of 68 reports / 225 tests / 0 failures
/ 0 errors / 17 skips; current focused MySQL, Redis, Meili and owner migration
gates are also recorded. The parent objective is complete.

## Authority and limitations
Development/TEST-TARGET only；没有 commit、push、publish、deploy 或生产操作。
官方 wrapper 首次运行暴露了 HiddenCaseLeakIT 的 strict-stubbing 回归，已修复并重跑通过；
宿主对长 integration wrapper 的 Maven exit code 等待超时，但 fresh Surefire XML 独立汇总 exit 0。
