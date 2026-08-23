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

## Services review findings 2026-08-22 execution packet

### Context

The current Services review report identifies two authentication contract
violations, two state/concurrency defects, a durable RBAC event gap, a token
revocation documentation decision, and four bounded architecture/hygiene
items. Existing owner seams and rollback adapters are already in place.

### Decision

Execute `SVCFIX-20260822-A1..D4` in strict report order using the smallest
existing seams: remove the WebSocket header fallback; require the OAuth state
cookie; use PROCESSING CAS for Admin audit outbox claiming; increment
`authz_version` in the role update SQL; emit RBAC changes through the existing
Auth insert-only audit outbox seam while retaining synchronous version
invalidation; document the revocation window; add routing exit criteria and
judge-runtime package ownership declarations; lock the snapshot shape with a
contract test; and delete only the named stray class artifact.

### Invariants

- WebSocket authentication accepts only the `access_token` cookie-derived session attribute.
- OAuth state is atomically consumed from Redis and must match a nonblank HttpOnly cookie.
- Auth owns account/authz writes; App owns profile writes; no blacklist writer is added.
- Audit outbox state transitions are single-winner and terminal updates are guarded.
- Existing public envelopes, routing modes, rollback adapters, schema migrations and seven-runtime topology remain unchanged.
- Evidence is development/TEST-TARGET only; `.auto-flow/*` is bookkeeping and not delivery scope.

### Rejected alternatives

- Do not hold the existing Admin `FOR UPDATE` lock across per-record `REQUIRES_NEW` processing; use the smaller CAS state machine.
- Do not move the trial DTOs or physically split `judge-runtime` in this bounded review fix; package ownership documentation is sufficient.
- Do not add a blacklist writer, new broker/process, or edit an applied migration.

### Affected tasks

`SVCFIX-20260822-A1..D4` and `SVCFIX-20260822-REVIEW-VALIDATE-CLOSE`.

## Services review findings 2026-08-22 terminal validation decision

### Validation-gate repair

The required Admin integration selector initially failed before exercising the
outbox tests because `DefaultAdminDashboardReadAdapter` has two constructors
without an explicit Spring constructor selection. A single `@Autowired` on the
public production constructor repaired only that wiring baseline; no dashboard
behavior or contract changed. Admin `*IT` then passed 44/0/0/0.

### Terminal evidence boundary

The root `./mvnw -Dtest='*IT' test -B` was run again after the repair but the host
waited 300 seconds without returning an exit status. It remains unavailable,
not passed. Affected Admin `*IT`, focused security/concurrency/RBAC/snapshot
tests, owner migration safety, final `verify`, architecture/manifest/wiki/
Compose/YAML/diff checks, and graph refresh provide the recorded development
evidence. No production acceptance is inferred.

## Documentation/wiki consolidation 2026-08-22 execution packet

### Decision

Consolidate all current content files under `services/docs/`,
`apps/management/docs/`, and `wiki/` into the root `PROJECT_DOCUMENTATION.md`.
Organize the file by current architecture, Owner/migration, security, release,
frontend i18n, and review history; retain source provenance markers and the full
source sections so the deletion is lossless.

### Protected scope

Do not delete `AGENTS.md`, nested `AGENTS.md`, `CLAUDE.md`,
`.claude/rules/`, `.auto-flow/`, README files outside the selected docs/wiki
directories, source/configuration, or unrelated dirty-worktree changes.
Update active references and executable checks to the new root path. The
obsolete wiki manifest generator is removed because there is no longer a
separate wiki page set to manifest.

## 2026-08-23 App Owner DEV-LOCAL problemset seed

### Context

The App Owner migrations create the problemset tables, while the immutable
legacy problemset seed SQL remains on the shared migration chain. The local App
Owner schema therefore starts structurally complete but empty; public list and
random-problem reads return no data / 404.

### Decision

Add `init-db/scripts/app-owner-seed.sh` as a DEV-LOCAL seed Adapter invoked by
`scripts/dev/up.sh` after Owner migrations. Reuse the immutable historical
problemset seed sources, execute them in one transaction against `app`, and
only seed when the problemset tables are empty. Existing complete data is
preserved, while partial/incomplete data is rejected fail-closed.

### Alternatives rejected

- Do not edit applied migrations or add demo rows to the production Owner
  Flyway chain.
- Do not make the frontend hide a missing-data contract or change the public
  random endpoint to mask an empty database.
- Do not copy data across Owner schemas at runtime; the Adapter executes the
  canonical seed SQL directly against the DEV-LOCAL App schema.

### Rollback and authority

`--skip-seed-data` disables the local step; no schema migration, production
Compose path, grant, cutover, commit or deploy is changed. Evidence is
development/TEST-TARGET only.

## 2026-08-23 App Owner Forum schema repair

### Context

`app.forum_posts` was created by the old six-column App bootstrap migration.
The current Forum entity, mapper and projection use soft-delete, sort counters,
JSON payloads, excerpt, update and moderation fields, so the public read path
failed with `Unknown column 'is_deleted'` before it could return an empty page.

### Decision

Add the later `app/V20260823170000__Align_Forum_Posts_With_Runtime_Contracts.sql`
as an additive, baseline-compatible repair. It preserves the legacy `content`
column and rows, backfills `excerpt`, adds the missing runtime columns/indexes,
and uses guarded dynamic DDL so both stale and already-expanded schemas pass.
The App migration preflight explicitly requires `DROP`, `CREATE ROUTINE` and
`ALTER ROUTINE` because existing App repair migrations rebuild stale tables and
the new migration uses a transient DDL procedure; the App runtime account does
not receive these privileges.

### Rejected alternatives

- Do not edit the applied six-column migration or rebuild/drop `forum_posts`.
- Do not remove `is_deleted` predicates from Forum code; they are part of the
  soft-delete interface and are used by multiple callers.
- Do not broaden runtime App grants; migration-only capabilities stay on the
  explicit migration principal and are covered by fresh safety rehearsal.

## 2026-08-23 DEV-LOCAL Forum seed

### Decision

Extend the existing `app-owner-seed.sh` Adapter with a separate Forum seed
group using immutable `V20260603_120700__Seed_Forum_Posts_Per_User.sql`. The
problemset and Forum domains seed in separate transactions; each requires an
empty domain and skips only a complete domain, while partial data fails closed.

The legacy seed's single `users.admin` lookup is transformed at the DEV-LOCAL
Adapter seam to the stable `forum_users` admin fixture. This preserves Owner
database isolation and avoids restoring an App-to-Auth SQL dependency.

### Consequences

The standard `up.sh` path now supplies 12 forum posts, 3 communities, 6 tags
and 12 forum users in a fresh DEV-LOCAL App schema. Production Compose and
Owner Flyway paths remain unchanged; `--skip-seed-data` remains the rollback/
opt-out seam.

## 2026-08-23 DEV-LOCAL Contest seed

### Context

The App Owner contest schema was migrated but its `contests`, related contest
tables and `global_rankings` were empty. The public Contest endpoints correctly
returned HTTP 200 with empty pages, so the missing data was a bootstrap seam
gap rather than a frontend or route failure. The Contest home page consumes
both the catalog and global ranking projection.

### Decision

Extend the existing `app-owner-seed.sh` Adapter with one atomic Contest domain
transaction that executes the immutable contest and global-ranking sources
against the `app` schema. The group seeds only when all required data tables are
empty, skips only a complete fixture set, and fails closed on partial or
incomplete data. The canonical fixture IDs are retained; no Auth `users` table
read is introduced. Set the MySQL session timezone to `+08:00` inside this
DEV-LOCAL transaction so `NOW()`-relative contest windows match the App's
`Asia/Shanghai` clock without changing global DB or production configuration.

The frontend schema normalizes the backend `ContestRankingVO.score` wire field
to the stable `rating` field consumed by ranking components.

### Consequences and rollback

The supported full `up.sh` path populates the Contest home page while preserving
existing complete App data. `--skip-seed-data` remains the opt-out/rollback
seam; no applied migration, production Compose path or runtime cross-Owner
query changes.

## 2026-08-23 Console avatar fallback

### Context

The App-owned `user_profiles` table was empty for the local administrator, so
the profile read returned no avatar and the personal page fell back to plain
initials. Auth correctly owns account identity only; adding avatar columns or
reading Auth profile fields would violate the current ownership split.

### Decision

Reuse the existing frontend `useAvatar` seam. A stored App profile avatar stays
authoritative; when absent, the seam creates a deterministic local SVG data URL
from the username. Personal profile and sidebar surfaces consume the same
fallback, so the UI has a concrete image without a third-party network request,
new storage, or cross-Owner read.

### Consequences and rollback

Custom uploads remain unchanged and continue to override the fallback. The
change is frontend-only and reversible by removing the fallback integration;
no migration or Auth data mutation is required.

## 2026-08-23 Ranking avatar ownership

### Context

The immutable global-ranking fixture stored DiceBear URLs directly in
`global_rankings.avatar`, while the real profile source is App
`user_profiles(account_id, avatar)`. This made ranking display data diverge from
the profile ownership contract.

### Decision

Keep ranking rows focused on rating facts and make all public ranking display
queries explicitly project `name` and `avatar` from App `user_profiles`. The
legacy applied seed remains unchanged; the DEV-LOCAL Adapter strips its known
DiceBear placeholders and clears existing local fixture placeholders. Console
uses a stored profile avatar when present and initials when absent, never a
fabricated remote ranking image.

### Consequences and rollback

A real App profile avatar becomes visible automatically in all global-ranking
surfaces. If no profile exists, initials accurately represent missing data.
Rollback is limited to the projection/DEV-LOCAL adapter changes; no Auth table,
applied migration or runtime cross-Owner query is introduced.

## 2026-08-23 DEV-LOCAL Solution seed

### Context

The App solution schema existed but contained no rows. The public solution feed
returned a successful empty page, so the Console rendered its editorial
fallback instead of Markdown solution content.

### Decision

Extend the existing App Owner seed Adapter with a separate Solution transaction
using immutable `V20260603_120400__Seed_Solutions_Test_Data.sql`. Seed only an
empty `solutions` table, preserve a complete set of 12 rows, and fail closed on
partial data. Transform the legacy admin lookup to the stable local fixture
identity at the Adapter seam; do not restore a runtime or App-to-Auth SQL
lookup.

### Consequences and rollback

The supported DEV-LOCAL startup path now exposes real seeded Markdown solution
cards and detail content. `--skip-seed-data` remains the opt-out seam; no
applied migration or production path changes.
