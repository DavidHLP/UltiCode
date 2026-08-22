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
实施 `/tmp/architecture-review-20260821112953.html` 的全部 1-5 候选并完成
run-development-loop 终态审计；当前只接受 development/TEST-TARGET 证据。

## Active task
无 — `ARCH-20260821-001..006` 已在 `2026-08-21 architecture review terminal` 完成并通过 Completion Audit，当前无活跃任务；`TASKS.yaml` 与 `HANDOFF.yaml` 为终态。

历史 `AR20260820-001..006` 与 `CRFIX-REVIEW-006` 保持 done，不代表本次新
目标已完成；本次用户明确重新开启 Judge、user facts 与 Search read-mode
收敛。禁止将上一目标的 rejected background candidate 当作当前完成证据。

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

## 2026-08-21 post-review closure

Fixed the shared-Flyway Auth contract no-op case, aligned Dashboard App reads
with the canonical owner schema, replaced dashboard mapper maps with typed
`DashboardBucketCount` rows, and ordered the test-only Search worker before
`app` in the Maven reactor. Focused owner/MySQL regressions and fresh
`services/./mvnw verify -B` passed (`MAVEN_EXIT=0`; 829 reports / 2792 tests /
0 failures / 0 errors / 29 skips). The broad integration selector remains
host-timeout-limited at 300 seconds and is not reported as passed.

## 2026-08-21 CRFIX-REVIEW-006 progress

Restored the applied Auth contract migration unchanged and added
`V20260821100000__Guard_Auth_Users_To_Account_Ownership.sql`; the shared Flyway
location now matches only root migrations so owner migrations are not replayed
against the shared schema. Dashboard problem charts use `published_at`, and
derived problem status SQL groups by the CASE expression.

Focused Dashboard, Auth and Search MySQL regressions, fresh services verify,
owner migration preflight/safety, Compose dev/prod config, graphify update and
diff checks passed. Evidence is development/TEST-TARGET only; no external
delivery or production action was performed.

## Authority and limitations
Development/TEST-TARGET only；没有 commit、push、publish、deploy 或生产操作。
官方 wrapper 首次运行暴露了 HiddenCaseLeakIT 的 strict-stubbing 回归，已修复并重跑通过；
宿主对长 integration wrapper 的 Maven exit code 等待超时，但 fresh Surefire XML 独立汇总 exit 0。

## 2026-08-21 current report reopened architecture implementation

Current objective: implement and verify all five candidates in
`/tmp/architecture-review-20260821-163916.html`. The prior
`ARCH-20260821-001..006` terminal ledger is preserved as historical evidence and
does not satisfy this report's distinct duplicate-contract, owner-fail-closed,
named-mode and Projection requirements.

Active task: `ARCHREV-20260821-006` (in_progress); `ARCHREV-20260821-001..005` passed their focused implementation gates.

Execution order: Owner datasource boundary → DevStack policy/defaults → Judge
transport contract → named migration modes/dead flags → User Facts Projection →
Review/Tier-C validation/Completion Audit. Authority remains development/
TEST-TARGET only; do not commit, push, publish, deploy, cut over production,
revoke grants or edit applied migrations.

## 2026-08-21 reopened architecture implementation

Recovered the prior completed ledger without overwriting it. The current
objective adds ARCH-20260821-001..006 for the five candidates in
`/tmp/architecture-review-20260821112953.html`: DevStack mode interface,
explicit Judge-only assembly, Admin query slices, batch owner-composed user
facts, and explicit Search database/indexed semantics. Existing owner,
rollback, migration and development-only invariants remain active.

## 2026-08-21 architecture review terminal

All five reopened candidates are implemented and audited. DevStack manifest
owns mode flags, roles, timeouts and readiness; Judge wiring is explicit with
only a default-off App rollback adapter; Admin Dashboard and user detail are
coarse query slices; owner-composed user facts is shared by App, Search and
Moderation; Search database/indexed reads expose strict worker, source,
freshness, ordering, total and fallback semantics.

Final Review is Confirmed Findings=0. `services/./mvnw verify -B` exited 0
with 834 reports / 2805 tests / 0 failures / 0 errors / 29 skips. Disposable
Redis 7 + Meili v1.8 E2E passed 3/0/0/0; Compose, manifest, YAML, graph and
diff checks passed. Evidence is development/TEST-TARGET only; no commit,
push, publish, deploy, cutover, grant or production action occurred.

## 2026-08-21 current architecture review terminal

`ARCHREV-20260821-001..006` is complete for the current
`/tmp/architecture-review-20260821-163916.html` report. Owner datasource
placeholders are Spring-native fail-closed; dev modes are manifest-backed;
Judge transport and shared flag validator sources are singular; User Facts
Projection covers full user/profile, Search and Moderation callers with
normalized owner failure semantics.

Final Review: Standards APPROVE, Spec APPROVE, Confirmed Findings=0.
Validation: `services/./mvnw verify -B` exit 0, 843 reports / 2827 tests / 0
failures / 0 errors / 29 skips; Judge Redis 4/4; Search E2E 4/4; Owner safety
and preflight PASS; Compose/manifest/YAML/diff PASS; graphify update PASS.
Authority remains development/TEST-TARGET only. No commit, push, publish,
deploy, production cutover, grant revoke or applied migration edit.

## 2026-08-21-221346 architecture transformation

Current objective: implement all five strategic objectives from
/tmp/architecture-review-20260821-221346.html. The older ARCHREV terminal
objective is preserved above and is not completion evidence for this objective.

Active task: none; ARCHX-20260821-001..006 are complete.

Execution order:
ARCHX-001 DevStack mode contract ->
ARCHX-002 Judge Streams normal path ->
ARCHX-003 Submission batch read and ARCHX-004 UserFacts narrow seams
(independent after recovery) ->
ARCHX-005 executable architecture docs ->
ARCHX-006 Review/Tier-C validation/Completion Audit.

Authority remains development/TEST-TARGET only. No commit, push, publish,
deploy, production cutover, grant revoke or applied migration edit.

## 2026-08-21-221346 architecture transformation terminal

All five strategic objectives reached terminal state. DevStack-only startup
aliases now delegate to `scripts/dev/up.sh`; normal dev-lite/dev-full Judge
uses Streams and `legacy-rollback` is explicit; Submission Contest reads use a
bounded batch contract; `UserFactsProjection` is facts-only with a separate
directory seam; CONTEXT/docs and executable architecture checks share current
Owner/Worker terminology.

Final evidence: App affected reactor 1425/0/0/13; services `./mvnw verify -B`
801 reports / 2705 tests / 0 failures / 0 errors / 20 skips; Submission
ReadProviderIT 4/0/0; architecture contract, Compose, YAML, graph and diff
checks PASS. Development/TEST-TARGET only; no production acceptance claim.

## 2026-08-22 Services review findings

Objective: repair all pending items in
`services/docs/SERVICES_REVIEW_FINDINGS_2026-08-22.md`.

Initial active task at recovery was `SVCFIX-20260822-A1` (ready); the ordered
DAG is terminal in the section below. Existing user changes remain protected.
No commit/push/deploy/production action is allowed.

Initial recovery facts (all addressed): WebSocket fell back to `auth` STOMP
headers; OAuth skipped cookie binding when cookieState was blank; AuditOutbox
used an unprotected FOR UPDATE select plus per-record REQUIRES_NEW; role SQL
did not bump authz_version; RBAC emitted only logs; Submission routing seams
lacked exit criteria; judge-runtime packages lacked ownership declarations;
the snapshot test did not assert exact shape; and the named `services/com`
class artifact existed. Graph coverage metadata was unavailable, so source
reads were the authoritative fallback.

## 2026-08-22 terminal state

Active task: none. `SVCFIX-20260822-A1`, A2, B3, B6, B4-B5, D1, D2, D3, D4
and Review/Validation/Completion are done.

Final evidence: compile/test/verify exit 0; final non-IT Surefire aggregate
783 reports / 2638 tests / 0 failures / 0 errors / 16 skips; Admin `*IT`
44/0/0/0; owner migration safety/preflight, architecture/devstack contracts,
wiki, Compose, YAML, diff and graph checks PASS. Root broad `*IT` remained
host-timeout-limited at 300 seconds with no exit status and is not claimed as
pass. Worktree remains uncommitted and development/TEST-TARGET only.

## 2026-08-22 documentation/wiki consolidation

Objective: merge temporary docs/wiki content into root
`PROJECT_DOCUMENTATION.md`, update active references and remove redundant
source documents.

Current active task: `DOCMERGE-20260822-CLOSE` (ready). The
consolidated file is created and source completeness was verified; exact
original docs/wiki content files and the obsolete wiki manifest tool were
removed. Protected rules, `.auto-flow`, source code and unrelated dirty files
remain intact. Reference migration, Review and Validation passed; next: Completion Audit and close.

## 2026-08-22 documentation/wiki terminal state

Active task: none. All `DOCMERGE-20260822-*` tasks are done. The sole merged
temporary documentation file is `PROJECT_DOCUMENTATION.md`; selected docs/wiki
source files and the obsolete wiki manifest tool are removed. Active references,
links, contract scripts, affected test, YAML, diff and graph checks passed.
Worktree remains uncommitted; protected rules, `.auto-flow`, source code and
unrelated prior changes were preserved.
