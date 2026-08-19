# Coverage

## Services Owner architecture hardening (2026-08-18)

| Requirement | Task | Evidence |
| --- | --- | --- |
| Submission write path has no App/Auth synchronous callback | ARCH-001 | PASS — immutable `SubmissionFactsSnapshot`, owner fail-closed validation, owner MySQL/Testcontainers suites, App compatibility regression, and full Services Maven tests |
| Submission Contract remains implementation-free | ARCH-001 | PASS — API shape test includes the snapshot; DTO contains no Entity/Mapper/Repository type |
| Auth/Admin/App/Notification isolation preparation | ARCH-002 | BLOCKED — owner-specific datasource/Compose variables, Auth Flyway gate, expand/backfill/verify/rollback plan and disposable four-owner grant IT `22/22` pass; owner migration validate correctly rejects the non-privileged runtime account, while privileged physical cutover/users authority is unavailable |
| App dual-track cleanup | ARCH-003 | BLOCKED — production remote-routing stability, quiesce, observation, and rollback evidence are unavailable in this local-only project |
| Admin coarse analytics seam | ARCH-004 | PASS (local slice) — `AnalyticsOverviewData`, one participant batch, one Auth RPC and one call per overview aggregate, typed result regression, Admin module tests, bounded scan and explicit MySQL/Redis context IT; no production latency claim |
| Seven backend runtime operational topology | ARCH-005 | PASS — root POM validate, PM2 seven backend entries, startup help/static checks, and production/dev Compose config validation |
| Documentation authority and current topology | ARCH-005 | PASS — migration guide, architecture status, database isolation plan, README and startup/config sources synchronized; source/POM/config/Compose/scripts remain authoritative |

## Blocker remediation execution plan (2026-08-19)

| Requirement / user bullet | Task | Required evidence |
| --- | --- | --- |
| 1.1 设计并实现特权 owner migration job | ARCH-002-001 | PASS — explicit `MIGRATION_DB_*`, schema-scoped minimum privilege preflight, direct-grant-only policy, fake regression and real disposable MySQL direct/insufficient grant evidence |
| 1.2 获取真实数据库权限与 cutover window | ARCH-002-002 | DBA/release authority, target/account/host matrix, least-privilege direct `SHOW GRANTS`, backup, watermark, window and rollback owner |
| 1.3 完成 users/profile Owner 职责 | ARCH-002-003 | expand/backfill/verify report, idempotency, rows/checksum/orphan/duplicate report, reader/writer matrix and responsibility sign-off |
| 1.4 解除 Auth MySQL 1044 验证门禁 | ARCH-002-004 | explicit privileged identity validate/migrate success, runtime-account negative proof, Auth Flyway/config evidence and no applied migration edit |
| 1.5 执行物理隔离切换测试 | ARCH-002-005 | real target parity, all-writer quiesce, cross-schema denial, grant/role inspection, smoke, performance, cutover and atomic rollback evidence |
| 2.1 建立 remote-route 稳定性监控 | ARCH-003-001 | route/provider availability, error/timeout/retry, DB/Redis/outbox/PEL, restart, latency, double-writer metrics, thresholds and observation export |
| 2.2 获取全部写入者静默确认 | ARCH-003-002 | complete writer inventory, owner/stop/drain/restart commands, stream/DB watermark, signed quiesce manifest and rehearsal |
| 2.3 制定并执行观察期与 rollback 演练 | ARCH-003-003 | observation timeline, fault injection, reconciliation, route/grant/watermark rollback and all-writers-stopped failure proof |
| 2.4 部署 production 监控与切换机制 | ARCH-003-004 | deployment/change authority, secret references, runtime health, monitoring/alert export, route/grant snapshot and rollback proof |
| 2.5 完成兼容退役功能与性能测试 | ARCH-003-005 | static provider/config/import scan, functional/performance/security/concurrency/soak tests, single-writer proof and review=0 |
| 实施限制：保护 36 tracked + 5 untracked，不执行业务/迁移/远端动作 | ARCH-002-001..ARCH-003-005 | protected worktree map, no reset/discard/production action, no applied migration edit, control-plane-only planning record |
| 验证标准：focused tests、Maven、diff、bash、YAML | ARCH-007 | Submission/API/Facts/Provider tests, Admin adapter/service tests, reactor `BUILD SUCCESS`, `git diff --check`, `bash -n`, YAML parse |
| 关闭条件：所有 blocker 有 Acceptance/Validation/Review/Rollback/Evidence | ARCH-007 | final Tier C/D validation, Coverage closure, external authority bundle and updated Handoff |

Status: `ARCH-002-001 done` — implementation, independent Review `Confirmed=0`, fake/disposable MySQL preflight and Tier C focused validation pass. `ARCH-002-002..005` and `ARCH-003-001..005` remain externally gated; `ARCH-007` is pending until both chains close.

## DEV-LOCAL Blocker Remediation Rehearsals (2026-08-19)

| DEV-LOCAL Task | Title | Local Evidence | External Gate Boundary |
| --- | --- | --- | --- |
| `DEV-LOCAL-001` | 特权 owner migration contract | PASS — container-aware preflight, direct schema grants, fail-closed global capabilities, guarded baseline adoption, regression test `migrate-owner-preflight-test.sh` PASS | `ARCH-002-002/004` external authority remains blocked |
| `DEV-LOCAL-002` | owner Flyway/account/grant 验证 | PASS — direct-grant migration principals and isolated runtime accounts for Auth/Admin/App/Notification/Submission; Auth baseline `20260729140000`, Admin baseline `20260729140100`, all Flyway validated/migrated | `ARCH-002-002/004` external target/sign-off remains blocked |
| `DEV-LOCAL-003` | users/profile 开发环境迁移 | PASS — `owner-user-profile-backfill.sh` preflight/backfill/rollback; full account/profile projection SHA256 checksum match, source 12 active users/profiles, 0 orphans/missing, manifest-scoped rollback verified | `ARCH-002-003` responsibility/sign-off remains blocked |
| `DEV-LOCAL-005` | route monitoring quiesce baseline | PASS — `dev-local-monitoring-baseline.sh`; route mode/cutover marker=false, 7 backend + frontend writer inventory from ecosystem, MySQL/Redis health, DB table snapshots, quiesce preflight safely blocked on existing target | `ARCH-003-001/002` production monitoring remains blocked |
| `DEV-LOCAL-004` | Submission cutover rollback rehearsal | PASS — current non-empty target safety refusal verified (no copy/revoke performed); disposable Testcontainers `SubmissionOwnerCutoverIT` 4/4 verifies copy, grant revoke, failure injection and rollback | `ARCH-002-005` physical cutover gate remains blocked |
| `DEV-LOCAL-006` | observation rollback fault rehearsal | PASS — `dev-local-observation-rehearsal.sh` with fail-closed guard `DEV_LOCAL_OBSERVATION_CONFIRM`; 65 targeted fault/timeout/outage/replay/fencing tests (0 skips/failures/errors); table checksum reconciliation MATCH; PM2 writers labeled UNAVAILABLE, live writer drain/persistent mutation unperformed; rollback route=local/marker=false | `ARCH-003-003/004` production observation remains blocked |
| `DEV-LOCAL-007` | 兼容路径验证与退役裁决 | PASS — static compatibility scan, single-writer invariants verified on `app.submission.routing.mode` (local=App only, remote=Submission owner only), rollback-only whitelist established, retirement candidate documented in DEC-038, routing tests `30/30` pass | `ARCH-003-005` production retirement gate remains blocked |
| `DEV-LOCAL-008` | remediation validation packet | PASS — complete script syntax `8/8` PASS, preflight self-test `5/5` PASS, fail-closed observation rehearsal `5/5` PASS, 65 targeted fault/resilience tests `65/65` PASS, routing tests `30/30` PASS, diff and migrations check clean | `ARCH-007` final gate remains pending external closure |

| Requirement | Task | Evidence |
| --- | --- | --- |
| Solarized light semantic allocation | TASK-001 | PASS — CSS mapping + browser computed-token audit |
| Solarized dark semantic allocation | TASK-001 | PASS — CSS mapping + browser computed-token audit |
| Canonical 16-color palette | TASK-001 | PASS — official values locked by regression test |
| Complete localhost:9002 visual regression | TASK-001 | PASS — type checks, build, focused/full-scope tests, browser audit |
| Public Design ownership in packages/design-system | TASK-002 | PASS — package export, README contract, token interface, package-level test |
| Accessible Light/Dark semantic mappings | TASK-002 | PASS — contrast assertions for text, controls, primary action and status marks |
| Shared chart and status semantics | TASK-002, TASK-003 | PASS — stable 8-series chart adapter, semantic status surfaces and terminal badges |
| Console and Management public consumption | TASK-002, TASK-003 | PASS — public import, shared variants and production CSS assertions |
| Code, Markdown, controls, and feedback states | TASK-003 | PASS — component consumer contract plus focused app tests |
| Dual-app visual and build validation | TASK-003 | PASS — type checks, tests, builds and Light/Dark browser audits |
| Renderer runtime palette bridge and CSS variable resolution | TASK-004 | PASS — public palette/helper, no-DOM fallback test and package contract |
| First-party runtime color literal boundary | TASK-004, TASK-005, TASK-006, TASK-007 | PASS — scanner rejects non-canonical literals and records external/vendor/data exceptions |
| Console semantic consumers and ECharts | TASK-005 | PASS — Console type-check/test/build and computed-token chart assertions |
| Console Monaco and Markdown | TASK-005 | PASS — Monaco semantic mapping test and Markdown token scan |
| Console UserStats/layout header/glow consumers | TASK-005 | PASS — tokenized shadow/glow and no legacy HSL wrapper |
| Console Sidebar/ProblemListAnalytics/Markdown legacy HSL wrappers | TASK-005 | PASS — valid computed CSS declarations and semantic-token assertions |
| Console external rating/community/tag data exceptions | TASK-005 | PASS — explicit scanner exception boundary; canonical fallback remains token-based |
| Console Landing CSS/WebGL/theme | TASK-007 | PASS — Landing tests plus Light/Dark/Reduced Motion browser evidence |
| Management semantic consumers and ECharts/SVG | TASK-006 | PASS — Management type-check/test/i18n/build and chart/editor assertions |
| Management ChartContainer SVG selector seam | TASK-006 | PASS — no old HEX attribute selectors; token-driven structural rule |
| Management problem-list editor controls and local variable hierarchy | TASK-006 | PASS — Light/Dark computed token and button/switch checks |
| Public placeholder/PWA metadata | TASK-005, TASK-006 | PASS — canonical static asset/config scan |
| Landing renderer defaults (fog, particles, MSDF, clear color) | TASK-007 | PASS — canonical bridge defaults and no visible black/white/random fallback |
| Landing alpha-0 transparent render-target sentinel | TASK-007 | PASS — documented non-visible renderer exception |
| Third-party Google OAuth/vendor bundle/shader math exceptions | TASK-004, TASK-005, TASK-007 | PASS — scanner allowlist and ownership documentation |
| State/icon/text redundancy and accessibility | TASK-008 | PASS — contrast, keyboard, 200% zoom, reduced motion and color-vision smoke audit |
| Required screenshot route matrix and no-console-error regression | TASK-008 | PASS — Light/Dark screenshots and browser console-error audit |
| Final review, rollback boundary, and local delivery authority | TASK-009 | PASS — formal review, diff check, complete handoff without commit/push |
| Console header/nav/search/notification shared baseline | TASK-010 | PASS — semantic surfaces, rounded geometry, popover shadow, keyboard focus and Light/Dark browser smoke verified |
| Shared outline/secondary hover feedback in both Solarized modes | TASK-010 | PASS — border-control/primary hover markers plus resolved-token regression assertions |
| Console top-level nav active parity across nested routes | TASK-011 | PASS — /problemset, /forum and /contest path-segment matcher; 16-test consumer contract and type-check |
| Console/Management language menu public baseline and emoji-free locale markers | TASK-012 | PASS — shared Solarized surfaces, ZH/EN semantic markers, browser smoke, contracts, type-checks and builds |
| Notification Delivery candidate ownership and intent seam | NOTIFY-001 | PASS — App sole Owner and no-fourth-service boundary confirmed; sealed intent/channel/port contract matrix, idempotency and payload-redaction assertions pass; focused Notification/Inbox/Outbox suite 90 tests pass |
| Durable notification publication and inbox idempotency | NOTIFY-002 | PASS — Notification/consumer/dispatcher focused suite 51 tests; InboxConsumer/transaction/outbox/ledger integration suites 17 tests with MySQL Testcontainers; Submission result outbox tests 7; diff check and changed-file review pass |
| Delivery ledger reclaim, fencing and bounded retry | NOTIFY-003 | PASS — real MySQL ledger IT (stale claim reclaim, concurrent claim single winner, owner fencing, terminal states, reaper releasing durable CLAIMED) and real Redis bridge IT (staging/group-ACK, duplicate eventId dedup, unavailable tolerance); 63-test focused suite and 103-test Notification/Inbox/Outbox suite pass |
| Notification channel failure isolation | NOTIFY-003 | PASS — SMTP/WebSocket/in_app adapter failure-isolation and sensitive-payload contract tests pass; durable retry logs/persists class-only reasons; no token/cookie/password/hidden testcase in intent, ledger or logs |
| Independent App Notification worker role | NOTIFY-004 | PASS — ulticode.notification.worker.enabled gate + api/worker profiles; bridge/reaper ConditionalOnProperty 3-state contract test, reaper metric/failure-containment test, ledger-lag query + real MySQL IT, App boot test; lease/CAS multi-replica safety preserved with no second writer |
| Shared baseline and App Contest migration compatibility | CONTEST-011 | PASS — conditional DDL converges fields/indexes/FK across shared full and App lightweight baselines; both Flyway runs, replay, field assertions, rollback guard, validate and diff check pass |
| Notification phase terminal review and no backend-notification claim | NOTIFY-005 | PASS — `test.sh quick` passes after Contest migration repair; App Notification worker remains App-owned and no backend-notification was created |

## Submission / Search microservice extraction

| Requirement | Task | Evidence |
| --- | --- | --- |
| Submission/Search shared envelope, owner values, event versions and sensitive-payload contract | SPLIT-001 | PASS — `IntegrationEventEnvelopeContract` is reused by both event contracts; nested map/list redaction and top-level compatibility tests pass; app-api ArchUnit passes |
| Submission has a real network seam and deep existing port | SPLIT-001, SPLIT-002 | PASS for runtime seam — graph/source inventory, independent Submission provider, App local/remote single-writer routing and boot/route tests; storage ownership remains SPLIT-003 |
| Submission aggregate and judge/result outboxes have one Owner/writer | SPLIT-003, SPLIT-004 | PASS for the local target — submission/judge/result/created outboxes have one backend-submission writer; local/remote routing, real-MySQL cutover+rollback, App grant revocation, contest command/inbox, focused suites, and owner-grant evidence pass; the compatibility registrations are retired |
| Submission + judge outbox and verdict fence + result outbox stay locally strong-consistent | SPLIT-003 | PASS for the implemented writer path — real-MySQL writer 5/5, cutover 3/3, dispatcher MySQL+Redis 5/5, focused contract/boot/provider suites pass; crash-window behavior remains represented by outbox/PEL replay and final gate review |
| Judge remains an independent stateless sandbox worker | SPLIT-002, SPLIT-004 | PARTIAL — dependency tree excludes `backend-app-web`, runtime has no HTTP/business tables, Streams atomic enqueue and bounded PEL/DLQ tests pass; final services-wide owner/runtime gate remains SPLIT-005 |
| Judge Streams enqueue/retry failure safety | SPLIT-002 | PASS — Lua `SET NX + XADD` rollback path, bounded delivery attempts, `judge:{judge-stream}:dlq`, ACK ordering, metric/config and focused runtime tests |
| App/Admin/Contest/Notification consumers use typed contract/event/inbox, not cross-service SQL | SPLIT-001, SPLIT-004 | PARTIAL — shared contracts/ArchUnit, App remote read routing, Admin read-group seam, and Contest SubmissionCreated inbox are covered; judged-before-association retries; ranking/moderation/Notification physical ownership remains explicitly out of scope |
| Search source aggregates remain App/Auth-owned; Search worker owns MeiliSearch writes | DEC-011, SEARCH-001, SEARCH-002 | PASS — source writers publish typed events; backend-search owns the allowlisted MeiliSearch write path; SEARCH-003 backfill/version/tombstone/replay and E2E-stub evidence complete |
| Problem/User/Forum/Solution changes publish safe SearchDocumentChanged events | SEARCH-001 | PARTIAL — App 三源（Problem/Forum/Solution）全部 owner writer 已发布（slice-a：service/admin provider/import/moderation/publish 语义 + Forum wiring 测试 + 发布矩阵）；User 源 slice-b：auth outbox（V20260816170000）+ Auth 三写路径 hook + Auth dispatcher（属性门控 XADD）+ App profile 写路径 hook；用户文档 id/username + name/avatar；Auth 事件链路 IT 待 SEARCH-002 消费方验证 |
| Search worker is no HTTP/no business DB and is at-least-once/idempotent | SEARCH-002 | PARTIAL — services/search/ 模块已建（无 web/无业务表，PEL claim/reclaim + XACK-after-write + DLQ + allowlist + metrics），单测 7/7 + boot 契约 + compose 双 PASS；真实 Redis+Meili E2E 受无外网阻塞（gap），SEARCH-003 backfill/watermark 未完成 |
| Four index backfills, delete/replay, watermark and existing `/search` compatibility | SEARCH-003 | PASS — 四切片全 done（a4be5ac58/f5c5e058e/bd2c42291/d09411d19）；backfill 一致性（枚举谓词与 Q-read 一致、watermark/版本/tombstone、重跑幂等）、/search 兼容回归、唯一写者静态扫描、E2E IT（真实 Redis+Meili stub）+ 可观测计数；真实 Meili 镜像 E2E 为外部 gap |
| No new broker, no speculative Contest ranking/Moderation/Notification physical split | SPLIT-001, SPLIT-005 | PASS — explicit out-of-scope and DEC-011 alternatives; final contract gate is closed without claiming those splits |
| Final route, DB grant, Compose, security, concurrency and rollback gate | SPLIT-005, SPLIT-005-env-quick, SPLIT-005-env-sandbox, SPLIT-005-retirement-authority | PASS for the local open-project target — sandbox image, services-wide IT, disposable rollback, local cutover, runtime health and grant evidence pass; no production environment is claimed |
| Local App account, owner credentials and runtime prerequisites | SPLIT-005-runtime-access | PASS — local MySQL/Redis/Nacos plus App/Submission/Judge/Notification/Auth/Admin are running; App source-table DML is zero, owner schema DML is four and credentials are distinct |
| Local remote/local cutover and single-writer observation | SPLIT-005-runtime-cutover-observation, CONTRACT-007 | PASS for local runtime — confirmation-gated copy/revoke, source/target parity, public health, PM2 restarts=0, owner-grant evidence and direct-provider retirement pass |
| Official quick remains runnable with stale persistent MySQL credentials | SPLIT-005-env-quick | PASS — root probe detected existing `ulticode-mysql` ERROR 1045; local `mysql:8.0` disposable container on loopback port 40526 migrated 85 versions and official quick passed; persistent container/volume untouched |
| Sandbox namespace IT reports missing-image prerequisites honestly | SPLIT-005-env-sandbox | PASS — local sandbox image exists; `SandboxNamespaceIsolationIT` + `SandboxForkE2EIT` execute 12/12 assertions with 0 failures/errors/skips; official wrapper post-run aggregate is 823 reports / 2,720/0/0/24 |
| Submission aggregate and judge/result outboxes have one Owner/writer | SPLIT-003, SPLIT-004, CONTRACT-007 | PASS for local remote/local runtime — Submission owns schema DML, App source-table writes are revoked, and only the direct backend-submission provider remains on the regular path |
| Submission + judge outbox and verdict fence + result outbox stay locally strong-consistent | SPLIT-003 | PASS for transaction/fence/outbox and dispatcher evidence; isolated services `test`/`verify` and the official integration wrapper pass; rollback remains route/grant/watermark/reconciliation only |
## Service contract boundary convergence

| Requirement | Task | Evidence |
| Complete app-api owner/exception inventory and matched-release decision | CONTRACT-001 | PASS — `.auto-flow/CONTRACT-001-OWNER-MATRIX.md` covers 219 top-level types plus 66 nested declarations (including `DelegationAssertionContract` and full nested paths); provider/consumer/POM/source evidence, coverage caveats, App fact/recipient exceptions, package roots, Dubbo identities, no-alias rule and DEC-011 guardrails are recorded |
| implementation-free common/security/metadata seams leave app-api | CONTRACT-001-COMMON | PASS — backend-common contract/ArchUnit tests, affected owner reactor tests, old-FQCN/duplicate/forbidden-import/dependency scans and migration-guide update pass; App/Admin/Notification/WebSocket consumers now use common packages |
| app-api contract monolith is reduced to App-owned seams | CONTRACT-001, CONTRACT-001-COMMON, CONTRACT-004, CONTRACT-008 | PASS — owner matrix, App API ArchUnit, migrated FQCN/POM/import scans and affected reactor tests pass; final objective gate is closed |
| Submission contracts live in backend-submission-api | CONTRACT-001, CONTRACT-001-COMMON, CONTRACT-002, CONTRACT-004 | PASS — new module/package compiles; API contract/ArchUnit/event tests pass; all discovered provider/consumer/test imports migrated; old FQCN/re-export scan is empty |
| Notification contracts live in backend-notification-api | CONTRACT-001, CONTRACT-001-COMMON, CONTRACT-003, CONTRACT-005 | PASS — new module/package compiles; Notification contract/ArchUnit tests and Admin/Notification/App callsite tests pass; App fact exceptions remain explicit |
| Judge depends on Submission contract plus bounded App fact seams | CONTRACT-001, CONTRACT-004 | PASS — Judge/Judge-runtime dependency and import audit, Submission owner tests and affected reactor pass; only matrix-allowed App facts remain |
| SubmissionStatusCatalog and TestCaseDetailCodec have one owner | CONTRACT-002, CONTRACT-006 | PASS — one canonical API codec/catalog scan, DTO vectors, null/legacy JSON, storage-edge mapping and MySQL-backed write-port IT pass; no shared Entity/Mapper |
| Submission verdict/write path is single-hop and single-writer | CONTRACT-004, CONTRACT-007 | PASS for local route — App routes remote/local, direct owner providers/reaper/outboxes are registered, App source grants are revoked, runtime observation is stable, and no compat artifact remains |
| Existing HTTP/RpcResult/security/event behavior remains compatible | CONTRACT-001-COMMON, CONTRACT-002, CONTRACT-003, CONTRACT-004, CONTRACT-005, CONTRACT-007 | PASS for the migrated source boundary — contract-shape, redaction, idempotency, Result/RpcResult, timeout/retry and auth/audit regressions pass; no public route/cookie/JWT change was made |
| Final contract-boundary gate and docs are closed | CONTRACT-008 | PASS — direct-provider retirement, focused/module/integration/verify validation, Compose/YAML/runbook checks, formal review and Completion/Coverage Audit pass; local-only scope and explicit out-of-scope boundaries are recorded |
| CR repair: direct-owner routing, owner-first preparation-only bootstrap, explicit cutover marker, exact host/grant/role fail-closed preflight, all-writer quiesce and transactional failure compensation | CONTRACT-007, CONTRACT-008 | PASS — production Compose is remote-only; local `init-env.sh` emits marker=false, `up.sh --prepare-submission-owner` migrates/unlocks without PM2, normal startup is marker-gated; disposable MySQL rejects host mismatch, global/schema/table `ALL`, `IS_GRANTABLE=YES`, column grants, transitive roles and inspection failures; cutover/rollback require a one-time all-writer quiesce confirmation covering App, Submission owner, Judge, dispatchers, reapers, schedulers and direct clients; rollback `copy_back` runs all table replacements in one transaction and failure emits CRITICAL; provider 4/4, services verify, quick/integration pass; real Redis+Meili mirror remains an explicit external gap |
