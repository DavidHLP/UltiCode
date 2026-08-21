# Decisions

## Architecture review execution direction (2026-08-20)

### Context
评审报告确认 UltiCode services/ 已处于 Owner/Worker 与 Contract seam 成形的 Strangler migration 收敛阶段。当前目标只有 development/TEST-TARGET；无需新增物理服务或基础设施。四项候选分别是 Search DLQ 原子性、Submission 读侧 locality、Admin dashboard 查询经济性和 canonical development 配置。

### Decision
按以下唯一顺序实施：
1. Search worker 将 exhausted retry 的 `XADD DLQ` 与 source `XACK` 收敛到幂等 Redis 原子状态转移，并用 disposable Redis crash-window regression 证明 PEL/DLQ/ACK 状态。
2. Submission Projection 先实现页面级 facts/users 批量读取，本地完成 VO shaping；不引入事件化 read model。
3. 账号统计事实归 Auth，提供 bounded summary seam；Admin 只负责 Dashboard 输出 Projection。
4. runtime 默认只接受显式 Owner 配置，generic `DB_*` 仅保留 migration bootstrap；dev-full 为显式 opt-in，rollback source seam 不删除。
5. 完成 focused/module/integration/security/formal review 与控制面审计后，闭合 ARCH-REVIEW-005。

### Alternatives
- 不拆更多 Contest/Moderation/Notification services：缺乏独立 writer/seam 证据，增加 process/contract/startup 成本。
- 不引入 RocketMQ/Seata/Kubernetes/Service Mesh：已有 Outbox/Inbox/Redis Streams 覆盖当前开发可靠性需求。
- 不把 development evidence 表述为 production acceptance：当前无 production authority，外部部署/观察/回滚责任不在本任务授权内。

### Consequences
- 变更集中在现有 deep modules、contracts、adapters、tests 和 startup/config seams；不改变 writer、schema、migration 或公共 HTTP envelope。
- 失败转移、跨 Owner 读取和 runtime 配置的边界更可测量；Admin/Submission 不再隐藏无界远程 fan-out。
- 开发配置迁移期兼容性会从默认 runtime interface 中收窄，但 migration bootstrap 与 source-level rollback 保留。

### Affected tasks
ARCH-REVIEW-001, ARCH-REVIEW-002, ARCH-REVIEW-003, ARCH-REVIEW-004, ARCH-REVIEW-005.

## Architecture review execution planning addendum (2026-08-20)

### Context
`/tmp/architecture-review-20260820.html` identifies five executable candidates plus two rejected background candidates. The repository already has Owner/Contract seams, but the report finds shallow startup behavior, Admin foreign-table reads, an App Submission compatibility matrix, Account/Profile schema drift, and a split Search verification surface. The target is development/TEST-TARGET only.

### Decision
Execute one ordered DAG: `AR20260820-001` DevStack/dev-lite → `AR20260820-002` Admin Dashboard read seam → `AR20260820-003` App Submission migration seam → `AR20260820-004` Account/Profile schema contract → `AR20260820-005` Search disposable verification → `AR20260820-006` final audit. Implement the smallest complete change at the existing seam; do not add physical services or speculative infrastructure.

### Alternatives
- Do not add more Contest/Moderation/Notification services: the report provides no independent writer/seam evidence.
- Do not introduce RocketMQ/Seata/Kubernetes/Service Mesh: current development reliability primitives are sufficient.
- Do not delete legacy/remote rollback paths: production quiesce, observability and retirement authority are absent.
- Do not make Admin a foreign-data owner: preserve source ownership and use one bounded read seam.

### Consequences
Startup and read boundaries become explicit and testable; migration compatibility remains reversible; verification depth improves without runtime expansion. Development configuration may narrow its default interface while migration bootstrap remains available.

### Affected tasks
AR20260820-001, AR20260820-002, AR20260820-003, AR20260820-004, AR20260820-005, AR20260820-006.

## Report do-not-split constraints (2026-08-20)

### Context
The report explicitly states that convergence must not be achieved by adding more modules, physically splitting the existing storage-free judge-runtime again, deleting rollback paths, or claiming production authority from local rehearsal.

### Decision
Treat each constraint as a mandatory terminal acceptance item for `AR20260820-006`: preserve current service boundaries, preserve judge-runtime's storage-free execution boundary, inventory and retain rollback seams, and label all evidence development-only.

### Affected tasks
AR20260820-006.

## AR20260820-002 Dashboard read seam (2026-08-20)

### Context
`DefaultDashboardStatsProjection` currently preserves the Admin HTTP shape but
uses `DashboardMapper` for direct SELECTs against App-owned problems/contests/
solutions/forum tables and the Submission table. Admin's datasource and mapper
scan are already owner-local, so the implementation depends on hidden grants
and cannot be a real Admin owner boundary.

### Decision
Keep the existing `DashboardStatsProjection` interface and `DashboardStatsVO` /
`ChartStatsVO` contracts. Add one Admin-owned `AdminDashboardReadPort` seam
behind the projection; implement the App-owned aggregates in an entity-free
`DashboardAdminReadPort` provider and extend the existing Submission admin read
contract for submission dashboard stats/charts. The seam owns bounded fan-out,
maps owner DTOs to Admin read data, and converts owner failures to an explicit
unavailable error. Delete the Admin `DashboardMapper`; do not add a service,
schema, writer, event infrastructure, or production route change.

### Alternatives rejected
- Do not let Admin inject App/Submission mappers or widen Admin mapper scanning.
- Do not make the Dashboard controller call several owner ports directly; keep
  aggregation and failure semantics behind one deep Admin interface.
- Do not add an event projection in this task; no existing event/read-model
  authority is available for dashboard freshness, so use bounded owner queries.

### Affected task
AR20260820-002.

## AR20260820-003 Submission migration seam (2026-08-20)

### Decision
Keep the existing `SubmissionWritePort`, `SubmissionFencePort`, and
`SubmissionUserQueryPort` interfaces as the stable App intake/read/fence
surfaces. Centralize local/remote implementation selection and unavailable
provider failure in `SubmissionRoutingProperties.select(...)`, then make all
three routing wrappers delegate through that helper.

### Consequences
`dev-lite/local` has one deterministic local route policy, `remote` remains
cutover-gated, and write/fence/user-read cannot silently diverge or dual-call.
The remote adapters, App-local implementation, legacy outbox/dispatcher and
rollback configuration remain available; production retirement is deferred.

### Rejected alternative
Do not delete the legacy/local implementation or remove judge envelope/cutover
flags in this development-only task: the dispatcher still uses cutover
watermarks and external quiesce/observation authority is absent.

### Affected task
AR20260820-003.

## AR20260820-004 Auth/Profile ownership (2026-08-20)

### Decision
Keep Auth `users` as an account/authz projection and make App
`user_profiles` the only profile storage and writer. Add one later Auth-owner
contract migration to drop the nine legacy profile columns; update the existing
backfill/parity script to project account columns to Auth and profile columns to
App without changing the legacy source.

### Compatibility
Use expand → verify → contract: run the existing preflight/backfill with an
explicit quiesce confirmation, verify full account/profile parity including
soft-deleted accounts, then apply the later migration. Auth Java mappers already
select account-only columns, so no public DTO or RPC contract changes are
needed.

### Rejected alternative
Do not edit the original Auth migration, retain profile columns in Auth as a
second writer, or add cross-owner SQL to the Auth migration. Do not add a new
service or migration framework for a schema-contract gap.

### Affected task
AR20260820-004.

## AR20260820-005 Search disposable verification seam (2026-08-20)

### Decision
Add one test-only integration harness at the App `SearchReadProjection`
boundary. It starts disposable Redis and MeiliSearch, publishes the existing
wire envelope to the existing stream, invokes the existing worker, queries the
existing projection, and asserts idempotency, delete/version behavior, DLQ
envelope transfer, and DB fallback.

### Compatibility
Keep `backend-search` free of App contracts at runtime. Any dependency from
App to the worker is test scope only; no production bean, route, event field,
or ownership boundary changes. Unique stream/group/index identifiers isolate
the test from the local default stack.

### Rejected alternative
Do not add a new runtime service, duplicate the Search projection, or replace
the existing worker E2E stub. Do not report the env-gated external Meili test
as real E2E evidence when the disposable image cannot be pulled.

### Affected task
AR20260820-005.

## AR20260820-006 Terminal architecture audit (2026-08-20)

### Decision
Close the report execution after Candidates 01–05 are evidence-backed. Keep
the existing distributed modular-monolith seams: do not add modules to force
convergence, do not physically split `judge-runtime`, and do not retire any
rollback or local compatibility path.

### Authority
All evidence is development/TEST-TARGET evidence. No production acceptance,
deployment, cutover, grant, commit, push or publish action is authorized or
claimed. The terminal audit records the boundary rather than widening scope.

### Affected task
AR20260820-006.

## AR20260820-001 DevStack implementation decisions (2026-08-20)

### Decision
Keep the existing shared Flyway bootstrap, then apply the declarative Owner
manifest in `auth → admin → app → notification → submission` order. When the
shared chain leaves one of the canonical bootstrap-only tables (`auth` search
outbox, `admin` audit outbox, or `submission` created outbox), `up.sh` may use
the existing DEV-LOCAL baseline guard only after exact shape, empty history,
and zero-row checks; unknown shapes fail closed.

### Compatibility
Treat MySQL 9.1's local `root` explicit static privilege list as the same
local migration superset only when it has direct `GRANT OPTION` and the full
owner-migration capability set. Non-root global capability lists remain
rejected. Notification and Submission migration identities also require
global `GRANT OPTION` because their migrations grant `USAGE ON *.*`.

### Affected task
AR20260820-001.

## Architecture review 2026-08-21 full implementation reopening

### Context
The user explicitly reopened all five candidates in
`/tmp/architecture-review-20260821112953.html` and requested implementation
through terminal state. The previous `AR20260820-006` decision rejected Judge
and user-facts background deepening; that rejection applies only to the prior
objective and is superseded for this new objective. Existing source ownership,
rollback seams and development-only authority remain unchanged.

### Decision
1. `scripts/dev/devstack-manifest.sh` owns the supported dev-lite/dev-full
   mode exports. dev-lite is deterministic local Submission + database Search
   with no Search worker; dev-full is explicit remote/indexed rehearsal and
   remains cutover-gated.
2. Judge-only worker/attempt/reaper/migration wiring is explicitly imported by
   `JudgeRuntimeConfiguration`; the storage-free `judge-runtime` artifact is
   retained and no new process, schema or infrastructure is introduced.
3. `AdminAnalyticsPort` is reduced to query slices (contest, revenue and
   overview); owner adapters retain bounded reads and reporters retain math.
4. `UserReadMapper` gains a bounded batch composition path for Auth account +
   App profile facts; Moderation uses it instead of per-ID reads. Search's
   cursor-specific directory seam remains separate where its ordering/count
   behavior is materially different.
5. Search exposes explicit database/indexed read modes and an explicit DB
   fallback policy. Indexed mode requires an explicitly enabled event-backed
   worker and returns source/freshness/order/total/fallback semantics. The
   existing Search worker remains the sole Meili writer; production Compose
   passes its existing worker gate to App while application defaults preserve
   the current indexed/fallback behavior.

### Invariants
- Owner single-writer and account/profile ownership do not move.
- Public HTTP/contract envelopes remain compatible unless an in-repo caller and
  its tests are updated together.
- No applied migration is edited; no production cutover, grant, deploy or push.
- Existing local/remote/legacy rollback adapters remain available.
- All new mode behavior fails closed on malformed configuration and remains
  testable through the module interface.

### Alternatives rejected
- Do not add a new runtime process, message broker, service mesh or schema.
- Do not physically split the storage-free judge-runtime artifact in this task.
- Do not delete rollback adapters or claim production acceptance from TEST-TARGET.

### Affected tasks
ARCH-20260821-001, ARCH-20260821-002, ARCH-20260821-003,
ARCH-20260821-004, ARCH-20260821-005, ARCH-20260821-006.

## Architecture review 2026-08-21 terminal decisions

- Treat the first-class DevStack interface as the owner of not only mode
  values but also phase timing and per-runtime readiness evidence.
- Keep App Judge compatibility explicit and rollback-only; the adapter polls
  only legacy RQueue and delegates all execution lifecycle to the shared
  attempt executor.
- Treat Dashboard and the existing Admin user list/detail projection as two
  coarse Admin query slices; do not create a universal Admin query service.
- Treat `UserFactsReadPort` plus `UserAccountFact`/`UserFactView` as the single
  owner-composed account/profile composition seam. Search keeps only its
  cursor/count predicates and consumes that seam for facts.
- Keep the new Search worker gate fail-closed for indexed reads. Development
  evidence and production configuration consistency are not production
  acceptance; no external delivery action is authorized.

## Architecture review 2026-08-21 execution packet

### Root cause / capability gap
The prior implementation closed individual migration seams but left the new
report's five developer-facing interfaces partly implicit: mode exports were
split between manifest/up.sh/ecosystem/YAML, Judge-only beans were discovered
from a shared artifact, Admin analytics exposed metric-level methods, user
facts lacked a common batch composition path, and Search silently selected
Meili or DB fallback.

### Ordered implementation
1. Update the existing DevStack manifest, launcher, PM2 environment and docs;
   add mode contract assertions before touching Java behavior.
2. Remove Judge-only component stereotypes from the shared execution classes;
   retain explicit imports in `JudgeRuntimeConfiguration` and add App-absence /
   Judge-wiring tests.
3. Reduce `AdminAnalyticsPort` to contest, revenue and overview query slices;
   keep owner DTO adapters and reporter math, update all tests and Javadocs.
4. Add `UserReadMapper.selectByIds`, implement one Auth batch + one App profile
   batch in `OwnerUserReadAdapter`, and make Moderation use it; preserve
   Search's cursor-specific directory contract.
5. Add `SearchReadProperties` with database/indexed mode and explicit fallback;
   wire dev-lite/dev-full/production Compose, update projection and tests,
   including disposable event-to-query E2E.
6. Review complete call chains and source boundaries, then run Tier C affected
   reactor, real owner/Redis/Meili gates, config/shell/YAML/graph/diff checks.

### Risk and rollback
No schema or migration changes. Public contract changes are additive or
internal to existing owner contracts. Mode changes are reversible via the
existing environment variables; Java wiring changes are source-revertible;
Search falls back to database mode as the safe rollback. Do not remove local,
remote, legacy, shadow, or cutover adapters.

### Required review focus
Spring component discovery after stereotype removal; PM2 environment
precedence; mode defaults and cutover gates; Admin analytics hidden callers;
Auth batch missing/unavailable semantics; Search exact totals, stale index,
strict indexed failure and explicit fallback; test-only worker dependency and
production Compose behavior.
## 2026-08-21 current report architecture convergence decision

### Context

The current report `/tmp/architecture-review-20260821-163916.html` identifies five
remaining convergence gaps that are different from the already-closed
`ARCH-20260821-001..006` ledger: implicit Owner datasource fallback, multiple
development-mode authorities, duplicated Judge Streams contract source, a
boolean migration flag cross-product, and request-time cross-Owner facts
enrichment.

### Decision

Execute the five gaps as `ARCHREV-20260821-001..006` in dependency order. Keep one
MySQL instance for development, make Owner-specific runtime configuration
fail-closed, use the existing DevStack manifest as the single supported mode
policy, centralize only the stable Judge transport contract, remove dead config,
and extract a bounded User Facts Projection seam. Do not add a process, broker,
Kubernetes, Service Mesh, Seata, or production migration.

### Invariants

- Auth owns account/credential/status/authz; App owns `user_profiles` writes.
- Each Owner has one runtime writer and a separate migration identity.
- Submission local/remote routing and Judge legacy/Streams rollback remain explicit and fail-closed.
- Judge wire JSON, Redis keys, ack/nack, dedup and version compatibility do not change.
- Search cursor ordering/count semantics remain separate from generic User Facts Projection semantics.
- Applied Flyway migrations, production routes/grants and external delivery are out of scope.

### Rejected alternatives

- Do not solve contributor friction by removing Submission before proving Admin/worker callers do not require it.
- Do not put Redis adapter, business persistence or sandbox execution into `backend-submission-api`.
- Do not replace the flag matrix with a generic configuration framework or add infrastructure.

### Affected tasks

`ARCHREV-20260821-001..006`; development/TEST-TARGET evidence only.

### Validation-gate repair

## 2026-08-21-221346 architecture transformation

### Context

The new user objective reopens the five strategic candidates in
/tmp/architecture-review-20260821-221346.html. The prior ARCHREV terminal
ledger remains historical evidence and does not prove this report's narrower
DevStack, Submission batch-read, Judge normal-path, UserFacts interface, or
documentation-executable requirements.

### Decision

Implement one reversible development transformation:

1. DevStack manifest remains the sole mode policy; direct runtime defaults and
   PM2/YAML consumers must agree with it and fail closed on drift.
2. Judge Streams becomes the normal local transport in both development modes;
   legacy RQueue remains only behind an explicit rollback mode.
3. Submission exposes an additive bounded batch read seam; App and Submission
   implementations batch user/problem facts and Contest uses one call.
4. User Facts composition remains one implementation, while directory summary
   and User Facts consumers receive separate narrow interfaces.
5. Source/config facts feed executable architecture checks; CONTEXT.md records
   only stable domain concepts and current ownership.

### Invariants

- No new broker, process, schema, migration, production route or grant change.
- Existing public response envelopes and VO shapes remain compatible.
- Owner writers, hidden-case filtering, missing-profile semantics and
  fail-closed unavailable-owner behavior remain unchanged.
- Legacy rollback code/data is preserved and reversible.
- .auto-flow/* remains control-plane bookkeeping and is not delivery scope.

### Affected tasks

ARCHX-20260821-001..006; development/TEST-TARGET evidence only.

The full reactor exposed a race in the existing Admin bounded-query cancellation
test: interrupting the task before cancelling its public future could turn a
cancelled query into an exceptional completion. Reordering the two operations
in `CancellableQueryExecutor.cancel` is in scope as a required validation-gate
repair for the bounded Owner read seam; it changes no public contract or data
behavior and is covered by its focused test plus the final reactor verify.
