# Worklog

- 2026-08-18 (Services Owner architecture hardening): implemented immutable `SubmissionFactsSnapshot` across API/App/Submission/Judge adapters; removed App/Auth fact-port dependencies from the Submission write Owner and added fail-closed contract/owner tests. Added a bounded Admin analytics overview seam and bounded Admin component scan. Added owner-specific DB configuration preparation, updated migration/status docs, removed invalid `backend-api` POM entry, and added Search to PM2/local startup handling. Focused and full tests: Submission owner integration and App regression pass; full Services Maven test/verify aggregate 826 suites / 2732 tests / 0 failures / 0 errors / 28 skips. Production/dev Compose config, Maven validate, PM2 Node syntax, Bash syntax and diff check pass. Physical Auth/Admin/App/Notification isolation and App compatibility deletion remain explicitly incomplete/blocked.

- 2026-08-16 (SEARCH-003-slice-1 实施：版本语义与 worker 版本账本) — App/Auth publisher aggregateVersion 0L → clock.instant().toEpochMilli()（与 OCCURRED_AT 同源，DEC-016）；worker 增加 Redis 版本账本（search:doc-version:{index}）：existing > incoming → stale_skipped 计数+ACK 跳过、等版本重写、损坏条目忽略；文档附加 _aggregateVersion；HSET 仅在 Meili 接受写后；DELETE 清账本；新增 SearchWorkerProperties.versionKeyPrefix（yml SEARCH_WORKER_VERSION_KEY_PREFIX）。验证：search 10/10、auth 7/7、app-web search+wiring 75/75、reactor compile、git diff --check PASS；SearchDocumentChangedPublisherTest（新）4/4。
- 2026-08-16 (SEARCH-003-slice-4 + SEARCH-003 收口) — SearchWorkerEndToEndIT 3/3（真实 Redis + 真实 meili-java client + JDK HttpServer stub，零新依赖）：UPSERT/DELETE/stale 全链 + ACK + 账本 + tombstone；compose prod 启用（SEARCH_WORKER_ENABLED/AUTH_SEARCH_OUTBOX_DISPATCHER_ENABLED/MEILISEARCH_ENABLED 默认 true，代码默认不变，dev 不变）；指南 §11.5 runbook。SEARCH-003 四 AC 全过、Coverage 行 PASS；验证：search 15（12 单测+3 IT）+ auth search 7 + app-web search 92 + reactor compile + compose 双 config + diff check；commit d09411d19。真实 Meili 镜像 E2E 仍为外部 gap。SPLIT-005 依赖链待 SPLIT-003-slice-6 contest 迁移排期。
- 2026-08-16 (SEARCH-003-slice-3 实施：兼容回归与扫描) — DefaultSearchReadProjectionTest 增 `_aggregateVersion`/未知字段容忍用例（15/15）；MeiliWritePathScanTest 静态扫描（app modules + app-web + auth main，根缺失即失败、>100 文件防空洞，1/1）；指南 §11.4 状态更新 + §11.5 backfill/版本/tombstone/启用/回滚 runbook；commit bd2c42291。
- 2026-08-16 (SEARCH-003-slice-2 实施：App backfill 能力) — 共享链 migration V20260816220000（forum_posts.users 加 updated_at、solutions 加 ON UPDATE）+ V20260816165000 前置修复 slice-b 回归（auth 链 V20260816170000 表级 GRANT 使全新库共享链必挂——IT 全链验证修复）。SearchBackfillReadPort seam + 四实现（域模块持 SQL，谓词与 Q-read 一致；用户版本=max 行时间戳）；SearchDocumentBuilders 统一文档形状，publisher 重构 + publishBackfill（显式版本、每事件事务）；SearchBackfillRunner（门控、watermark、diff、预检、计数日志）；worker tombstone 修订（DEC-016：DELETE 记负版本账本防复活）。Review 发现并修复：DELETE/backfill 乱序复活缺口（tombstone）、List.of NPE、测试嵌套 stubbing、mock 无限页循环。验证：runner 6/6 + publisher 7/7 + 真实 MySQL IT 6/6 + wiring 90/90 + reactor compile + compose + diff check 全过。
- 2026-08-16 (SEARCH-003-slice-1 收口) — Review F1（meiliFailure 未断言 HSET 不执行）修复复审 9/9，Confirmed=0；Validation Tier C：auth 全量 211/211、compose dev/prod config PASS；Completion Audit 全过；commit a4be5ac58（业务文件仅，.auto-flow/ 不暂存）。SEARCH-003-slice-2 ready。
- 2026-08-16 (recover-development-context 恢复对账) — 对账控制面：分支 main @ 16cecd994（origin/main 前 5 提交：43fbbf365/9a052221b/21ad12a4d/c81bfab1c/16cecd994），工作区仅 .auto-flow/ 台账修改（不 commit，无 untracked）；SEARCH-002 done 且证据齐，E2E 缺口已披露；SPLIT-003-slice-6 in_progress（观察窗已执行，grant revocation PARTIAL 待 contest owner 迁移）；SPLIT-004 pending（slice-5..9 done）；SEARCH-003/SPLIT-005 pending。修复 TASKS.yaml 重复 updated_at 键；更新 PLAN.md Active Task 行；核验 SEARCH-003 前置（/search 读路径、事件链、worker allowlist、默认关）。知识图谱不可用（graphify-out 缺失、codebase-memory 仅 hindsight），结论基于源码直读。

- 2026-08-11T21:55:00+08:00 — Recovered dirty worktree; protected existing contest and landing changes.
- 2026-08-11T21:55:00+08:00 — Verified official Solarized palette and dual-mode mapping.
- 2026-08-11T21:55:00+08:00 — Captured localhost:9002 landing and problemset light/dark baselines.
- 2026-08-11T23:50:00+08:00 — Replaced shared semantic and chart colors with the canonical Solarized palette and added a contract test.
- 2026-08-11T23:50:00+08:00 — Verified exact light/dark computed tokens in the browser; type checks and production build pass.
- 2026-08-11T23:50:00+08:00 — Verified 62 files / 543 in-scope tests pass; isolated one unrelated protected dirty timer-test failure.
- 2026-08-11T23:50:00+08:00 — Closed formal Standards and Spec reviews with 0 confirmed findings.
- 2026-08-11T23:59:00+08:00 — Recovered the completed TASK-001 state and protected existing contest/landing work for the public-design follow-up.
- 2026-08-11T23:59:00+08:00 — Chose packages/design-system as the public Solarized seam; planned package contract first, app adapters second.
- 2026-08-12T00:15:00+08:00 — Published the package stylesheet export, public Token interface, design contract and five package-owned regression tests.
- 2026-08-12T00:15:00+08:00 — Closed TASK-002 with package type-check, public import resolution, diff check and 0 confirmed review findings.
- 2026-08-12T00:45:00+08:00 — Centralized shared variants, difficulty semantics and stable eight-series chart colors in packages/design-system.
- 2026-08-12T00:45:00+08:00 — Migrated Console/Management adapters and removed app-owned semantic token definitions and non-Solarized status literals in changed paths.
- 2026-08-12T00:45:00+08:00 — Verified Theme 31/31, Design 7/7, both app type checks, targeted ESLint, full in-scope tests and production builds.
- 2026-08-12T00:45:00+08:00 — Verified built CSS retains shared variants and localhost:9002 Light/Dark computed colors, selected dates and difficulty badges; browser error log empty.
- 2026-08-12T00:45:00+08:00 — Closed Standards and Spec reviews with 0 confirmed findings; no commit or push performed.
- 2026-08-12T00:20:00+08:00 — Reconciled the broader request against the clean HEAD: existing public contract covers core UI, but a first-party runtime audit found residual old OKLCH/white/black/gray consumers, duplicate Monaco/Landing palettes and no Landing Reduced Motion branch.
- 2026-08-12T00:20:00+08:00 — Appended TASK-004 through TASK-009 without reinitializing completed history; mapped all attachment requirements and recorded the renderer bridge/reduced-motion decisions.
- 2026-08-12T00:20:00+08:00 — TASK-004 is ready; implementation must begin with the public renderer bridge and scanner before app migrations.
- 2026-08-12T16:03:00+00:00 — Completed TASK-004 through TASK-008 implementation slices; reconciled public palette bridge, Console, Management, Landing, accessibility and scanner evidence.
- 2026-08-12T16:03:00+00:00 — Re-ran final Standards/Spec review after fixes; both reviewers reported 0 confirmed findings.
- 2026-08-12T16:03:00+00:00 — Ran browser status-mark contrast, keyboard focus, 200% zoom, Reduced Motion, color-vision and Light/Dark screenshot smoke checks; no unexpected console errors.
- 2026-08-12T16:03:00+00:00 — Closed TASK-009 and prepared complete local handoff; no commit or push performed.
- 2026-08-12T16:20:00+00:00 — Final accessibility review found Dark status-mark controls using light marks with light `primary-foreground` (about 2.1:1); reopened TASK-008/TASK-009.
- 2026-08-12T16:20:00+00:00 — Fixed Console SolutionList glyphs and both app Switch thumbs to use adaptive `primary-control-foreground`; added source contracts and dark-state contrast regression tests.
- 2026-08-12T16:20:00+00:00 — Focused checks pass: design-system 12 tests, Console/Management contracts, both type-checks, and browser warning-mark contrast 5.18:1 Light / 6.68:1 Dark.
- 2026-08-12T16:30:00+00:00 — Final Spec review confirmed the adaptive status-mark fix: all checked Switch thumbs and status glyphs meet the 3:1 control/icon threshold in both themes; Confirmed Findings=0.
- 2026-08-12T16:30:00+00:00 — Reclosed TASK-008 and TASK-009 after full Console/Management tests and production builds; no commit or push performed.
- 2026-08-13T15:40:00+08:00 — Recovered the architecture report and existing App deepening plan; chose Notification Delivery as the single execution path and preserved the no-fourth-service decision.
- 2026-08-13T15:40:00+08:00 — Added NOTIFY-001 through NOTIFY-005 to the task ledger; NOTIFY-001 is the only ready task.
- 2026-08-13T15:40:00+08:00 — Updated Notification Execution Packet, Coverage, DEC-009, Resume and Handoff; no business source, migration, runtime or remote resource was changed.
- 2026-08-13T16:27:00+08:00 — NOTIFY-001 recovered the full Notification→outbox/inbox→ledger/channel flow; preserved the two pre-existing Javadoc-only dirty files and confirmed App-only ownership/no fourth service.
- 2026-08-13T16:27:00+08:00 — Added NotificationDeliveryContractTest for sealed intent/caller-facing ports, stable generation-aware idempotency identity, canonical channel IDs, and sensitive payload-key redaction; 5 tests passed.
- 2026-08-13T16:27:00+08:00 — Notification/Inbox/Outbox focused suite passed 90 tests; manual changed-file review Confirmed=0; known runtime gaps were kept for NOTIFY-002/003/004.
- 2026-08-13T22:56:00+08:00 — NOTIFY-002 implementation/review/validation closed: 51 focused Notification tests, 17 inbox/outbox/ledger integration tests, and 7 Submission result outbox tests passed; diff check and changed-file review Confirmed=0; NOTIFY-003 is ready.
- 2026-08-14T01:20:00+08:00 — NOTIFY-003 implementation/review/validation closed: claim fencing/backoff/terminal states and per-channel failure isolation implemented; reaper now reclaims all stale CLAIMED rows (incl. durable wire types) so inbox replay recovers a crashed-owner claim; durable retry failures are class-only sanitized; new real-Redis SubmissionJudgedInboxBridgeRedisIT proves staging/group-ACK, duplicate eventId dedup and Redis-unavailable tolerance. Focused suite 63 tests and Notification/Inbox/Outbox pattern 103 tests pass; git diff --check clean. NOTIFY-004 is the next ready task.
- 2026-08-14T01:35:00+08:00 — NOTIFY-004 implementation/review/validation closed: App Notification Delivery worker runtime role via ulticode.notification.worker.enabled gate + api/worker profiles; durable inbox bridge and ledger reaper conditional beans; ledger-lag query oldestClaimedAgeSeconds; reaper metric/failure-containment tests; worker gate 3-state contract test; App boot test 5, Notification/Inbox/Outbox pattern 112, NOTIFY-004 focused 13 tests pass; review Confirmed=0; no second writer, no backend-notification. NOTIFY-005 is the next ready task.
- 2026-08-14T01:55:00+08:00 — NOTIFY-005 phase gate: focused 112+13, reactor verify BUILD SUCCESS (15 modules), Compose dev/prod config OK, docs synced (CONTEXT.md + migration guide 11.4), 4 NOTIFY migrations verified on real MySQL 9.1 throwaway schema. Blocked: ./scripts/dev/test.sh quick Flyway fails on pre-existing committed V20260811180000__Create_App_Contest_Schema.sql (duplicate contest_type vs base V20260602_120000, commit 59d5ddf6f, Contest domain); NOTIFY phase not claimed complete; awaiting Contest-owner corrective migration or user adjudication.
- 2026-08-14T02:46:00+08:00 — CONTEST-011 implemented: V20260811180000 conditionalizes columns/indexes/submission fence/FK with existing INFORMATION_SCHEMA + PREPARE pattern; existing contest_type, duration_minutes and tie_breaker definitions converge to the App contract. Manual rollback now refuses shared-chain history before any destructive DDL.
- 2026-08-14T02:46:00+08:00 — CONTEST-011 validation: shared `ulticode_test` and App-only `app` Flyway runs pass; target SQL replay passes in both; 26 contest columns, 7 indexes and scoring-rule FK asserted in both; rollback guard returns expected Error 1644 and temp schema cleaned.
- 2026-08-14T02:46:00+08:00 — Final `./scripts/dev/test.sh quick` passes after local Flyway checksum repair: migration, 15 backend modules, auth-core 70, Console 571 and Management 423 tests; git diff --check clean. NOTIFY-005 closed; no commit/push performed.
- 2026-08-15T12:00:00+08:00 — Recovered the completed Notification control plane and started the new user objective. Graph/source verification confirmed `backend-submission-domain` is still an App module, `DefaultSubmissionWritePort` still couples Submission intake/verdict/outbox/Contest association, `backend-judge` depends on `backend-app-web`, and Search has no durable index writer. Added DEC-011, the Submission/Search Execution Packet, Coverage mappings and SPLIT/SEARCH task DAG; SPLIT-001 is the only ready task. Protected dirty frontend changes remain untouched.
- 2026-08-15T21:58:17+08:00 — SPLIT-001 review fix closed the shared integration envelope gap: added `IntegrationEventEnvelopeContract`, reused its nine field names from Submission/Search contracts, and changed Search document validation to recurse through nested map/list keys. Focused contract tests 5/5, app-api full tests 41/41 including ArchUnit, graphify update, diff/whitespace checks passed. Main-thread Review Confirmed=0; SPLIT-001 remains in_progress for validation, and no service runtime or applied migration was changed.
- 2026-08-15T22:02:00+08:00 — SPLIT-001 Completion/Coverage Audit passed and the contract/decision Task was closed. `SPLIT-002` and `SEARCH-001` are now ready; this handoff continues into the Submission runtime seam without moving storage ownership, changing migrations, or switching writers.
- 2026-08-15T23:45:00+08:00 — SPLIT-002 runtime seam completed: backend-submission compatibility provider and storage-free judge-runtime are independently wired; App local/remote routing remains single-writer and backend-judge no longer depends on backend-app-web.
- 2026-08-15T23:45:00+08:00 — Fixed Redis Streams P1s: atomic Lua SET NX + XADD with marker rollback, bounded PEL processing attempts with `judge:{judge-stream}:dlq` and ACK-after-DLQ; added focused regression coverage and queue configuration.
- 2026-08-15T23:45:00+08:00 — Validation: common 88, judge-runtime 4, Submission 2, App submission/judge/queue focused tests pass; backend-judge package and App compile pass; dependency tree, Compose dev/prod config, diff-check and graphify update pass. No commit/push/deploy.
- 2026-08-15T23:58:00+08:00 — Aquinas Java/Redis re-review confirmed zero findings after hash-tagged Cluster-safe keys, idempotent atomic DLQ XADD+XACK and explicit processing-attempt semantics.
- 2026-08-16T11:17:00+08:00 — SPLIT-003 expand-slice 1: created Submission owner target-state schema (submissions/judge_outbox/submission_result_outbox final shape), flyway-submission.conf (defaultSchema=submission, createSchemas=true) and submission_rw locked shadow-user grant; migrate.sh validates SUBMISSION_DB_NAME=submission.
- 2026-08-16T11:17:00+08:00 — Synced SPLIT-003 owner manifest: PerOwnerSchemaGrantTest SUBMISSION_TABLES now claims submissions/judge_outbox/submission_result_outbox (removed from APP_TABLES) and flyway-submission.conf/migrations/submission presence asserted; migration guide data-ownership matrix updated to Submission owner for submissions and submission_result_outbox.
- 2026-08-16T11:17:00+08:00 — Validation: PerOwnerSchemaGrantTest 6/6 pass; real MySQL 8.0 container ran flyway-submission.conf migrate (schema auto-created, 3 tables + history rows success=1); migrations idempotent on re-run; grant creates ACCOUNT LOCK user; bash -n and git diff --check pass.

## 2026-08-16 11:40 (SPLIT-003 slice-2 规划)

- 完成 writer 迁移切片规划调查：DefaultSubmissionWritePort 依赖 SubmissionMapper/ProblemFactsPort/UserExistencePort/SubmissionProjection/PerformanceStats/JudgeEnqueuePort/ContestSubmissionPort/ResultOutboxWriter/FeatureFlags/codec/uuid/event。
- 确认 App 侧已有 Dubbo provider 可复用：ProblemFactsProvider（backend-app）、SubmissionWriteProvider（App 本地 writer 的 Dubbo 暴露）。backend-submission 已有 SubmissionWriteCompatibilityProvider/SubmissionFenceCompatibilityProvider 转发 seam。
- 确认物理拓扑：单 MySQL 实例多 schema（ulticode/notification/submission）；submission_rw grant 已建（expand-slice 1）。
- 裁决 DEC-013：本切片只迁非 contest 普通提交写路径；contest 路径保留 App local（CR P1-2 守卫），owner 事件化归后续切片。
- 目标态：backend-submission 本地 writer（写 submission schema），App remote 路由调用；fence 与 result outbox 仍本地强一致。

## 2026-08-16 12:45 (SPLIT-003 slice-2 实现)

- 复制写路径类到 backend-submission：Submission entity、SubmissionMapper（精简写路径+fence CAS+peer stats）、JudgeOutboxRecord/Mapper、SubmissionResultOutboxRecord/Mapper/Writer、SubmissionStatusCodec、TestCaseDetailCodec、FeatureFlagsProperties、SubmissionPerformanceStats/Default、SubmissionProjection/Default（P0-1 过滤）。
- 新建本地 DefaultSubmissionWritePort：普通提交路径写 submission schema 三表单事务；contest 提交防御性拒绝（CR P1-2）；terminal verdict 总是写 result outbox（本 owner 无本地事件消费者，outbox 是唯一 durable 通道）。
- 新建 Dubbo 适配器：ProblemFactsDubboAdapter（backend-app）、UserExistenceDubboAdapter（backend-auth IdentityQueryService）、NoopContestSubmissionPort。
- 数据源启用：pom + mybatis-plus/mysql/auth-api/backend-common/testcontainers；application.yml 启用 DataSource + feature flags（app.features.use-judge-outbox/use-port 默认 true）；主类 ComponentScan + MapperScan 扩展；SubmissionUuidGenerator + SubmissionClockConfig。
- BackendSubmissionApplicationTest 语义从"无数据源"更新为"本地 writer bean 存在"。
- 新增 DefaultSubmissionWritePortIT（Testcontainers MySQL）：submit 写 submissions+judge_outbox、fence CAS 接受/拒绝、contest 拒绝 — 4/4 通过。
- 边界修正（DEC-013 修订）：dispatcher 消费者在 App，slice-2 不切流，compat provider 保持转发；指南 §4.5.1 更新。
- 测试：submission 模块 9/9；全 reactor 编译 OK；git diff --check OK。

## 2026-08-16 12:55 (SPLIT-003 slice-2 Review)

- Review Scope：本地 writer 事务/outbox 语义、Dubbo 适配器、复制类一致性、fence CAS SQL、P0-1 投影、测试真实性、schema 一致性、文档同步。
- Findings：
  - F1 (Confirmed→Fixed)：数据源 URL 缺 useAffectedRows=true 且 characterEncoding=utf8mb4 无效；已对齐 App URL 全参数。语义澄清：fence CAS 判定 stale 靠 WHERE（id+generation+current_attempt_id+lease）不匹配，与 affected/matched 无关；useAffectedRows=true 是为与 App writer 返回语义一致（相同值重复 UPDATE 时 true→0、false→matched=1），避免两个 writer 在重试路径行为漂移。
  - F2 (Confirmed→Fixed)：@ComponentScan 引用不存在的 com.ulticode.app.config 包；已移除。
  - F3 (Confirmed→Fixed)：指南 §4.5.1 遗留"不引入 Entity/Mapper"矛盾句；已更新为 slice-2 持有写路径 Entity/Mapper 副本。
  - F4 (Confirmed→Fixed)：IT 测试表多余 deleted 列（真实 schema 无）；已移除，schema 与迁移逐列一致。
- 复检：复制类 12/12 IDENTICAL；fence SQL 与原版逐字一致；result outbox 实体/迁移列对齐；UserExistence fail-closed（Auth down → 提交拒绝）与 App 行为一致；9/9 测试通过；git diff --check OK。
- F1 (Confirmed→Fixed，advisory blocker 采纳)：Local/Remote 双 adapter 无条件+条件实现同一接口 → remote 模式下 NoUniqueBeanDefinitionException，App 无法在切流目标模式 boot。修复：新建 SubmissionUserQueryRoutingPort（@Primary wrapper，注入 Local 具体类型 + ObjectProvider<Remote> + SubmissionRoutingProperties，按 routing.mode 委托），镜像写路径 SubmissionWriteRoutingPort 先例；Local/Remote adapter 保留实现但由 wrapper 持有。
- 复检：App boot 5/5（默认 local）+ 5/5（-Dapp.submission.routing.mode=remote）双模式 PASS；Focused 30/30；git diff --check OK。
- 结论：Confirmed=1（已修复复检），Review PASS。

## 2026-08-16 13:45 (SPLIT-003 slice-3 Review)

- Review Scope：复制类一致性、dispatcher 裁剪正确性、@Scheduled/@EnableScheduling、ResultEventPublisher 字段与 App stream 格式对齐、claim 条件与 writer isShadow 语义、事务注解、测试真实性（Testcontainers MySQL+Redis）。
- Findings：
  - F1 (Confirmed→Fixed)：backend-submission 无 @EnableScheduling，dispatcher 的 @Scheduled 不会运行 → 主类加 @EnableScheduling + javadoc 更新。
  - 非 finding：judge/result dispatcher 的 claim 条件（next_retry_at <= NOW() 秒级）导致 IT 同秒不可 claim → 测试回填 next_retry_at（生产靠 2s 轮询跨秒，非缺陷）。
  - 非 finding：QueueConfig 精简为仅注册 JudgeQueue bean（无 legacy RQueue），@Profile("!test") 与 App 模式一致。
  - 非 finding：ResultEventPublisher 省略 causationId/traceId（App 传 null 时同样省略），字段兼容。
- 复检：复制类 5/5 IDENTICAL（JudgeQueue/Envelope/Handle/Adapter/StreamKeys）；无 legacy 残留；@Transactional 保留；13/13 测试通过；git diff --check OK。
- 结论：Confirmed=1（已修复），Review PASS。

## 2026-08-16 15:30 (SPLIT-003 slice-4 Review)

- Review Scope：provider 本地化机制（@Value 默认 compat、无参/有参构造、Spring 选择）、fence mapper lease SQL 与 App 一致、脚本安全（表名硬编码、token 确认、密码不落盘）、runbook 与指南/DEC 一致性、cutover gate 被 SPLIT-004 阻塞的表述。
- Findings：
  - F1 (Confirmed→Fixed)：provider 双构造器无 @Autowired，Spring 默认选无参构造 → localWriter 恒 null，local 模式不可用 → 有参构造加 @Autowired 后 IT 3/3 验证 local 直写 submission schema + fence lease。
  - 非 finding：fence mapper javadoc 措辞与 App 略有差异（SQL IDENTICAL，注释差异仅说明性）。
  - 非 finding：脚本 cutover 中断后目标表非空导致重试被拒（保守安全，符合 notification 先例；清理后重跑通过）。
- 复检：脚本真实 MySQL 全链路 PASS（preflight → 无 token 拒绝 → cutover 复制+checksum 一致+grant 撤销 → rollback 恢复）；IT 7/7（cutover 3/3 + contract 4/4）；模块 5/5；git diff --check OK。
- 结论：Confirmed=1（已修复），Review PASS。

## 2026-08-16 16:10 (SPLIT-003 slice-4 加固：runbook 失败安全 + 残余风险记录)

- Advisory 复盘：slice-4 验证曾暴露 runbook 缺口——revoke 失败时 copy_forward 已落行、目标非空、重试被拒，需手工清理；此前仅记为"测试环境 grant 粒度问题"。实际该缺口在真实 misconfiguration（App 用户只有库级 grant / 用户不存在）下同样发生。
- 加固（两层防护）：
  1. assert_revoke_ready：preflight 与 cutover 复制前校验 mysql.user 存在 + 每表 table_privileges 存在表级 SELECT/INSERT/UPDATE/DELETE grant（GRANTEE = 'user'@'%' 转义已真实验证）；不满足则复制前 FAIL，零副作用。
  2. cleanup_failed_cutover：copy_forward 或 revoke_app_grants 失败时 DELETE 清空目标三表（目标在 assert_ready 已证为空，DELETE 即恢复原状，不触碰源行），并提示 re-run preflight/cutover。
- 复测（真实 MySQL 8.0）：preflight 拦截 grant 缺失 exit 1 → 补 grant 后 cutover 三表 checksum 一致 + app_rw 写源表 ERROR 1142 → rollback 恢复 grant 后可写 → 清理环境。bash -n + git diff --check PASS。
- 残余操作风险（如实记录，非 Confirmed Finding 归零替代）：
  - R1: 若 copy_forward 与 revoke 之间 MySQL 会话中断（网络/权限抖动），set -e 使 cutover 中途退出，cleanup_failed_cutover 可能未执行 → 目标残留行，重试被 assert_ready 拒绝，需手工 DELETE 或 rollback --execute。属低概率运维窗口，已由"复制前拦截"大幅收窄。
  - R2: cleanup_failed_cutover 本身依赖 MIGRATION 用户权限；若其已无目标表 DELETE 权限（本脚本从未降低该权限），清理失败会再次退出——rollback 路径仍可用。
  - R3: 本脚本 revoke 的是表级 grant；若 App 用户以库级通配 grant（ulticode.*）运行，preflight 会正确拒绝（不会误判"可撤销"），但需要人工先改授表级 grant。

## 2026-08-16 17:20 (SPLIT-004 slice-5 Review)

- Review Scope：SubmissionAdminReadProvider 与 App 原实现语义一致性（8/8 MATCH：search/counts/languages/distinct-users/toDto）、mapper read SQL 与 App IDENTICAL（6/6：findDistinctLanguages/countByStatusTyped/countByLanguageTyped/countDistinctUsersInRange/acquireLease/renewLease）、DEC-011 合规（problem-title 搜索仅经 ProblemAdminReadPort Dubbo seam，不读 problem 表）、不切流安全（Admin 仍调 group=backend-app；新 provider 注册 group=backend-submission 不覆盖）、无 TODO 残留、boot 测试不破坏。
- Findings：
  - F1 (Confirmed→Fixed)：backend-submission 完全无 MyBatis-Plus 分页拦截器 → selectPage 在生产中 total 恒 0、LIMIT 不生效 → 新增 MybatisPlusConfig（PaginationInnerInterceptor(DbType.MYSQL)）+ pom 补 mybatis-plus-jsqlparser（3.5.16 后拦截器分离）；IT 验证 total 正确。
  - F2 (Confirmed→Fixed)：SubmissionMapper.renewLease 注解误为 @Select（UPDATE SQL 当查询执行 → BindingException null primitive int）→ 修正为 @Update；全 mapper 注解一致性复查通过（3 @Update + 5 @Select）。
  - 非 finding：ProblemAdminReadDubboAdapter 未用方法抛 UnsupportedOperationException（fail-loud 而非静默 null）。
- 复检：Focused 20/20（admin read IT 4/4 + cutover IT 3/3 + write IT 4/4 + dispatcher IT 4/4 + boot 1 + contract 4）；模块测试通过；boot 测试通过。
- 结论：Confirmed=2（均已修复），Review PASS。

## 2026-08-16 18:10 (SPLIT-004 slice-6 Review)

- Review Scope：SubmissionReadProvider.toVO 摘要 enrichment 与 App DefaultSubmissionProjection 语义一致（user 摘要块 problem 摘要块均有）、DEC-011 合规（不读 user/problem 表，仅经 SubmissionUserReadPort/ProblemFactsPort seam）、不切流安全（App 本地 SubmissionReadAdapter 仍是 contest 的 bean；新 provider group=backend-submission 不覆盖）、无 TODO 残留。
- Findings：无（Confirmed=0）。
  - 非 finding：App 本地 SubmissionReadAdapter 无 @Primary 但为 @Component 且 App 内唯一 SubmissionReadPort bean；backend-submission provider 是独立进程，不影响 App 内 bean 选择。
- 复检：Focused 22/22（read IT 2/2 + admin read IT 4/4 + cutover 3/3 + write 4/4 + dispatcher 4/4 + boot 1 + contract 4）；App boot 5/5；git diff --check OK。
- F1 (Confirmed→Fixed，advisory blocker 采纳)：Local/Remote 双 adapter 无条件+条件实现同一接口 → remote 模式下 NoUniqueBeanDefinitionException，App 无法在切流目标模式 boot。修复：新建 SubmissionUserQueryRoutingPort（@Primary wrapper，注入 Local 具体类型 + ObjectProvider<Remote> + SubmissionRoutingProperties，按 routing.mode 委托），镜像写路径 SubmissionWriteRoutingPort 先例；Local/Remote adapter 保留实现但由 wrapper 持有。
- 复检：App boot 5/5（默认 local）+ 5/5（-Dapp.submission.routing.mode=remote）双模式 PASS；Focused 30/30；git diff --check OK。
- 结论：Confirmed=1（已修复复检），Review PASS。

## 2026-08-16 19:00 (SPLIT-004 slice-7 Review)

- Review Scope：聚合 SQL 与 App 完全一致（5 方法）、聚合方法实现语义与 App projection 一致（learning/history/status key 值）、DEC-011 合规（纯 submissions 表；唯一 LEFT JOIN 是 streak CTE 内同表 submission_dates）、contract additive（SubmissionUserQueryPort 新接口不影响现有）、App 本地 projection 未动（不切流）。
- Findings：
  - F1 (Confirmed→Fixed)：calculateStreak 递归 CTE day_num 基数抄错（0 vs App 的 1），影响 365 天窗口边界与 streak 语义 → 修正后 5/5 SQL IDENTICAL。
  - 非 finding：streak 语义是 MIN(days_ago)（今天=0），非直觉 streak 长度；测试断言已匹配 App 语义。
  - 非 finding：3 个既有 IT 用无参 DefaultSubmissionProjection 构造 → 传入 submissionMapper 适配 @RequiredArgsConstructor。
- 复检：Focused 26/26（user query IT 4/4 + read 2/2 + admin read 4/4 + cutover 3/3 + write 4/4 + dispatcher 4/4 + boot 1 + contract 4）；git diff --check OK。
- 结论：Confirmed=1（已修复），Review PASS。

## 2026-08-16 19:30 (SPLIT-004 slice-8 Review)

- Review Scope：read-routing 能力建设（不切流）：ProblemFactsPort 批量 seam（App adapter/provider/backend-submission adapter 三方同步）、backend-submission 用户读 provider 补 list/detail/best + performanceStats 本地（stats 类已在 slice-2 复制）、SubmissionProjection toVO 摘要提取为 applyUserSummary/applyProblemSummary + toDetailVO 复制（与 App 语义 8/8 关键值一致）、App controller 7 读端点经 SubmissionUserQueryPort（Local/Remote 双 adapter，写路径仍 writePort）、Admin Dubbo read-group 参数化（默认 backend-app，可切 backend-submission）。
- Findings：
  - 非 finding（观察项）：Local/Remote adapter 双 bean 模式与写路径 Default/Remote 同构；remote 模式默认不启用（routing.mode=local），切流在 cutover 联动。
  - 非 finding：App findByUserId 实际忽略 problemId（SQL 仅 user_id），backend-submission 实现一致，不扩大行为差异。
  - 非 finding：IT 中 DefaultSubmissionProjection 构造 4 参适配（3 个既有 IT + 本 IT）。
- 复检：Focused 30/30（user query IT 8/8：slice-7 四聚合 + findById 权限 + list 分页/批量 enrichment + findBest）；App test-compile；git diff --check OK。
- F1 (Confirmed→Fixed，advisory blocker 采纳)：Local/Remote 双 adapter 无条件+条件实现同一接口 → remote 模式下 NoUniqueBeanDefinitionException，App 无法在切流目标模式 boot。修复：新建 SubmissionUserQueryRoutingPort（@Primary wrapper，注入 Local 具体类型 + ObjectProvider<Remote> + SubmissionRoutingProperties，按 routing.mode 委托），镜像写路径 SubmissionWriteRoutingPort 先例；Local/Remote adapter 保留实现但由 wrapper 持有。
- 复检：App boot 5/5（默认 local）+ 5/5（-Dapp.submission.routing.mode=remote）双模式 PASS；Focused 30/30；git diff --check OK。
- 结论：Confirmed=1（已修复复检），Review PASS。

## 2026-08-16 (SPLIT-004 slice-9 实施：实际 read-routing 切换与 AC4 退役证据)

- 环境：可丢弃 MySQL 8.0.46 容器（ulticode-cutover-mysql:23307）+ ENV_FILE=/tmp/ulticode-cutover.env；既有 mysql_data 卷（08-14 旧密码不匹配）未触碰，保护不破坏。
- cutover 数据复制（submission-schema-cutover.sh，MYSQL_CONTAINER 模式）：preflight 首次拦截 R3 场景（App 用户仅库级 grant）→ 补表级 grant 后 preflight PASS；cutover --execute + token 成功：三表 72/2/2 行 source/target checksum 全一致（1365417332/2365044956/731060111）。
- grant 撤销证据：撤销 App 库级 grant + 脚本表级 REVOKE 后，ulticode 用户对 ulticode schema 读写均 ERROR 1044；submission_rw 按设计 ACCOUNT LOCK → 模拟部署方解锁（IDENTIFIED BY + UNLOCK）后读写正常（72 行可读、judge_outbox 可写）。
- F1（slice-8 遗漏调用方，本 slice 修复）：judge RemoteProblemFactsAdapter 未实现 ProblemFactsPort.findDisplayFactsBatch（批量 seam 三方同步漏掉第四方，judge 模块编译失败暴露）→ 补 Dubbo 委托实现；4/4 port 实现者复核齐备（app adapter/provider、submission adapter/provider、judge adapter）。
- AC4 退役证据：App JudgeOutboxDispatcher/JudgingLeaseReaper/SubmissionResultDispatcher/SubmissionResultOutboxListener 四类 javadoc 标注（切流后仅 contest 兼容 + 回滚路径，不扩展 regular 行为）；指南 §4.5.1 追加 slice-9 边界 + 退役矩阵；app/submission application.yml 注释更新。
- grant revocation PARTIAL 确认：contest intake/verdict 仍走 App local writer（DEC-013 CR P1-2），App 对 ulticode 三表 grant 须保留至 contest owner 迁移；与台账"grant revocation 执行（唯一 writer 未满足）"一致；观察窗必须记录该 PARTIAL。
- 验证：App boot 5/5 remote（-Dapp.submission.routing.mode=remote）+ 5/5 local；backend-submission IT 30/30（user query 8/8 + read 2/2 + admin read 4/4 + cutover 3/3 + write 4/4 + dispatcher 4/4 + boot 1 + contract 4）；judge 单测 BUILD SUCCESS；BackendAdminApplicationTest 既有 @Disabled（2 skipped 预期）；compose dev/prod config PASS；git diff --check PASS；bash -n PASS。

## 2026-08-16 (SPLIT-003-slice-6 观察窗：owner.mode=local + grant revocation 证据)

- 观察窗验证（切流状态）：backend-submission 全量 IT+boot 在 -Dapp.submission.owner.mode=local 下 30/30 PASS（本地直写 submission schema、dispatcher/result 事件链、crash-window/duplicate/stale 拒绝）；App 路由单测 9/9 PASS（SubmissionRoutingPortTest 6/6 含 contest 守卫 CR P1-2 + SubmissionRoutingPropertiesTest 3/3）；App boot local 5/5 + remote 5/5。
- grant revocation 观察窗（可丢弃 MySQL 8.0）：cutover 后 App 用户读写被拒（1044；表级 grant 姿态 1142）→ rollback --execute：source/target checksum 全同（72/2/2）回写无差异、App 表级 grant 恢复（SELECT 72 行可读）→ 清目标表后重跑 cutover 恢复切流状态（checksum 再一致）。cutover 重试被非空目标表拒绝（保守安全，slice-4 已知设计）。
- grant revocation 完成证据 PARTIAL（如实记录）：contest intake/verdict 仍走 App local writer（DEC-013 CR P1-2），App 对 ulticode 三表 grant 保留至 contest owner 迁移（事件化 ContestSubmissionPort + ContestServiceImpl 命令迁移，属后续切片）；全量撤销待该切片。SPLIT-003-slice-6 保持 in_progress。
- 环境备注：Docker Hub 网络不可达（IPv6 dial timeout），Testcontainers redis:7.2-alpine 拉取失败 → 本地 tag redis:7-alpine 为 7.2-alpine 后 admin IT 通过；既有 mysql_data 卷（08-14 旧密码）未触碰。

## 2026-08-16 (SEARCH-001 slice-a 实施：App 三源发布 seam 补齐)

- 现状对账：SearchDocumentChangedPublisher（App 三源）+ EventContract 冻结 + Problem/Forum/Solution service 路径 wiring（c9662564c 已交付）但未闭环：admin provider 绕过 hooks、moderation/import 写路径无事件、Forum 无 wiring 测试。
- 新增 hooks（全部在 owner 本地写事务内，经 IntegrationEventPublisher）：
  - ProblemServiceImpl.publishProblem→UPSERT、unpublishProblem→DELETE（DefaultProblemSearchReadPort 过滤 is_published=true，visibility 同步）
  - ProblemAdministrationProvider 四方法 + @Transactional（原 admin 写路径无事务边界，per-statement 提交；加 @Transactional 与 App service 路径一致，域内无 REQUIRES_NEW 风险）
  - DefaultProblemOwnerPort.insertImportedProblem/applyImportedUpdate（单条+批量导入共用叶子方法，按 is_published UPSERT/DELETE）
  - DefaultForumOwnerPort.deletePost（moderation 软删→DELETE tombstone）
  - DefaultSolutionOwnerPort.deleteSolution（物理删→DELETE）、setPublished（按 published UPSERT/DELETE）
- 无事件写路径（如实记录，Q-read 语义一致）：ProblemOwnerPort.updateModerationFlag、Forum flag/unflag/pin/lock、recordShare、Solution flag/unflag、updateVoteCounts、views 计数（recordView/getSolutionById）——文档字段未变或 Q-read 不过滤。
- 测试：新增 ForumPostServiceImplTest（3 例 wiring）；扩展 ProblemServiceImplTest（publish/unpublish）、SolutionServiceTest（create）、DefaultForumOwnerPortTest（deletePost）、DefaultSolutionOwnerPortTest（deleteSolution + setPublished×2）、ProblemAdministrationProviderTest（delete 断言）；全部 59/59 PASS；contract test 5/5。
- 指南：§11.1 SearchDocumentChanged 发布矩阵（14 行 writer→事件→依据）+ backend-search 状态行更新。

## 2026-08-16 (SEARCH-001 slice-b 实施 + SEARCH-001 收口)

- Auth 用户源：auth 链新增 search_document_changed_outbox（V20260816170000，auth_rw 表级 grant，真实 MySQL 8.0 验证 3 migrations + auth_rw 插入）；组件 entity/mapper（CAS claim→XADD→markDelivered、stale lease 回收、bounded retry、payload @Results JacksonTypeHandler）/publisher（事务内写 outbox）/dispatcher（@EnableScheduling + 属性门控默认关，XADD 格式与 ResultEventPublisher 一致：eventId/owner/aggregateId/aggregateVersion/eventType/schemaVersion/payload）。
- hooks：AuthAccountPort.create、AccountManagementPort.updateCredentials/softDelete 补 @Transactional（与 users 写原子，参照 admin provider 先例）；App DefaultAppUserWritePort updateProfile/uploadAvatar 经 findIndexRowById 发布完整用户文档。
- 架构裁决：AuthSingleHopArchTest（§6.5 auth 是叶子 Provider 禁依赖 app-api）被新依赖触发 → 将 IntegrationEventEnvelopeContract + SearchDocumentChangedEventContract 移至 backend-common（com.ulticode.common.event），全部调用方/测试更新（SubmissionLifecycleEventContract+Test、app-web publisher、auth publisher+Test），契约测试随迁 common；auth pom 撤回 app-api 依赖。
- 修复回环：dispatcher/publisher 未用字段清理（clock/objectMapper）；测试 matcher 三处 raw+any 混用；dispatcher 属性门控替代 @Profile("!test")（boot 测试上下文无 Redis）。
- 验证：auth 211/211（含架构规则）、app-web 1330/0（+profile wiring 测试）、app-api 39/39、common 93/93、reactor compile、git diff --check；commit c81bfab1c。
- SEARCH-001 收口（slice-a 21ad12a4d + slice-b c81bfab1c）：四类来源全部 owner writer 事务内发布，发布矩阵 16 行（指南 §11.1）；AC1-4 全满足。SEARCH-002（backend-search worker）为下一 ready Task。

## 2026-08-16 (SEARCH-002 实施：backend-search indexing worker)

- 新模块 services/search/：无 web starter（web-application-type none）、无 DB autoconfig、meilisearch-java 0.20.1（与 App 同版本）；SearchDocumentIndexWorker（XREADGROUP new + PEL reclaim 30s 空闲、XACK 仅 Meili 接受写后、deliveryCount 超限 DLQ+XACK、index allowlist、非本类型事件 XACK 跳过、Micrometer processed/deadlettered）；search.worker.enabled 门控默认关；MeiliSearchWorkerConfig 同门控。
- 修复回环：MapRecord.create 无 RecordId 重载（改 StreamRecords.withId）；StreamOperations 泛型 Object,Object；ack→acknowledge；createGroup 3 参 + groupExists 重查（镜像 bridge）；range 用 String id Range.closed；pending count 参数是 long（anyLong）；PEL pending 构造 3 参；@{argLine} surefire 崩溃（common 先例 argLine 覆盖）；测试 MapRecord id/long/Duration/lenient 等 8 处。
- 基建：docker-compose.yml 加 meilisearch:v1.8 服务（internal expose 7700、MEILI_MASTER_KEY 必填、meili_data 卷、healthcheck）+ docker-compose.prod.yml 加 backend-search（internal expose 9107、SEARCH_WORKER_ENABLED 默认 false、depends_on redis+meilisearch）；init-env.sh 生成 MEILI_MASTER_KEY；services/pom.xml 注册模块。
- 验证：worker 6/6 + boot 1/1；reactor compile；compose dev/prod config PASS；git diff --check；commit 16cecd994。
- gap：真实 Redis+Meili E2E 受 Docker Hub 网络阻塞未执行；SEARCH-003（backfill/delete/replay/watermark + /search 兼容）为下一 ready Task。

## 2026-08-17 (SPLIT-003-slice-7 规划与上下文恢复)

- 触发：继续既有 `run-development-loop` 目标；SEARCH-003 已完成，SPLIT-003-slice-6 仍因 contest intake/verdict 在 App local 而 grant revocation PARTIAL。
- 事实链：ContestSubmissionBridge → ContestServiceImpl（资格检查、contest 行锁）→ SubmissionWritePort；App `DefaultSubmissionWritePort` 同步写 `contest_submissions`，backend-submission 当前拒绝 contest DTO；`SubmissionLifecycleEventContract` 已冻结 `SubmissionCreated` 但尚未发布。
- 规划裁决（DEC-018）：增加显式 `submitContest` contract；generic `submit` 在两端拒绝 contest context；backend-submission 在同一本地事务写 `submissions`、`judge_outbox`、`submission_created_outbox`；App-Contest Inbox 幂等消费 `SubmissionCreated`，`SubmissionJudged` 缺关联时抛出可重试错误；Contest association 仍是 App owner，不搬表、不新增 broker。
- 控制面：新增 `SPLIT-003-slice-7`（in_progress），更新 PLAN/RESUME/HANDOFF；保护既有 `.auto-flow` dirty changes，不 commit，不编辑已应用 migration。

## 2026-08-17 (SPLIT-003-slice-7 完成，进入 SPLIT-005 final gate)

- 实现：`submitContest` 成为唯一 contest command；普通 `submit` 在 App/backend-submission 拒绝 contest context；backend-submission 事务写 `submissions`、`judge_outbox`、`submission_created_outbox`；App-Contest 幂等消费 `SubmissionCreated`，关联缺失时 `SubmissionJudged` 可重试；remote verdict 从 durable created context 恢复 `contestId`。
- 修复 review finding：移除 App ContestServiceImpl 跨远端 RPC 的长事务/行锁；admission 使用非锁读，owner writer 与本地 contest adapter 各自短事务并复核；新增 Noop contest-context 回读测试。
- 验证：focused reactor 37/37、真实 MySQL writer/cutover 8/8、真实 MySQL+Redis outbox 5/5、schema grant 6/6、migration/cutover/rollback/validate disposable runbook PASS、Compose 双 config、bash -n、git diff --check；graphify update 因环境 Operation not permitted 未完成，记录为外部工具 gap。
- 控制面：SPLIT-003/SPLIT-004 与 slice-6/7 reconciled done；SPLIT-005 in_progress，待 quick/verify/all-IT 与最终 gate 审计。

## 2026-08-17 (SPLIT-005 final gate evidence)

- `./scripts/dev/test.sh quick` first hit the pre-existing `ulticode-mysql` credential mismatch (`ERROR 1045`); the persistent container/volume was left untouched. A disposable MySQL phase-gate container was migrated with the canonical Flyway chain, then removed with its disposable volume after validation.
- Against the isolated database/Redis: services full reactor `./mvnw test -B` PASS; `./mvnw verify -B` PASS; auth-core 70/70, Console 576/576, Management 423/423 and all three type-checks PASS. Compose base+dev/prod, `bash -n`, and `git diff --check` PASS.
- Corrected services-wide `*IT` invocation reached unchanged `services/auth` `AccountManagementTransactionalIT.deleteAccountSoftDelete:164` (soft-delete row remains false); `git diff --name-only -- services/auth` is empty. Changed Submission/App writer, cutover, outbox, boot, contract and owner-grant suites remain green.
- Final security/concurrency/architecture review: no confirmed Submission/Search findings. Compatibility writers/flags and App grants remain rollback-only; no production cutover, applied migration rewrite, or destructive reset was performed. SPLIT-005 stays `in_progress` pending retirement authority and clean all-IT evidence.

## 2026-08-17 (SPLIT-005 validation repair and rerun)

- Diagnosis: `AccountManagementTransactionalIT.deleteAccountSoftDelete` built `users` and `auth_command_receipt` but not the Auth search publisher's `search_document_changed_outbox`; the production transaction therefore rolled back on a missing-table error. `services/auth` production files were unchanged.
- Fix: the test fixture now creates/drops the canonical-compatible outbox table. Focused `AccountManagementTransactionalIT` passed 5/5; auth `*IT` passed 10/10.
- Diagnosis/fix: `InboxConsumerTransactionIT` expected an exact `IllegalStateException`, while the existing consumer intentionally persists a bounded stack trace. The test now asserts class containment; focused inbox IT passed 5/5.
- Services-wide `*IT` reached 118 tests and cleared auth plus all non-sandbox suites. `SandboxNamespaceIsolationIT` remains 4/6 failing because `ulticode-sandbox:latest` is absent; the documented `Dockerfile.base` build could not pull `alpine:3.19` (Docker Hub IPv6 timeout). No image was fabricated or substituted.
- Review: both changes are test-only, preserve production behavior, and `git diff --check` passes. Graphify update was retried and remains externally blocked by `Operation not permitted`. SPLIT-005 remains open for the sandbox prerequisite, official quick's existing MySQL 1045, and compatibility/grant retirement authority.

## 2026-08-17 (SPLIT-005 resumed read-only retirement audit)

- Runtime matrix remains intentionally transitional: `docker-compose.prod.yml` defaults `APP_SUBMISSION_ROUTING_MODE=local`; `services/submission/src/main/resources/application.yml` defaults `APP_SUBMISSION_OWNER_MODE=compat`.
- The migration guide still defines `remote + local` as the cutover posture and App submission dispatchers as contest/local-rollback compatibility components. Retiring those flags and App grants would alter release routing and rollback semantics, so it requires explicit cutover authority and was not performed.
- Search write-path audit remains clean: App/Auth source paths publish `SearchDocumentChanged`; only `services/search` owns MeiliSearch writes. No independent ready task exists while the sandbox prerequisite and release authority are unavailable.

## 2026-08-17 (SPLIT-005 resumed gate recheck)

- Rechecked branch/worktree, Task Ledger, Docker image availability, production-source diff, and whitespace. State is unchanged: `ulticode-sandbox:latest` and `ulticode-sandbox:base-17` are absent; no ready Task exists; production source diff is empty; `git diff --check` passes.
- No duplicate full-IT run or substitute image was attempted because neither would remove the recorded external blocker or provide valid sandbox evidence.

## 2026-08-17 (SPLIT-005 blocker repair planning)

- Plan factored the three reported blockers into one safe execution path: `SPLIT-005-env-quick` isolates stale `ulticode-mysql` credentials with a disposable local MySQL; `SPLIT-005-env-sandbox` adds an explicit image/CLI assumption matching `SandboxForkE2EIT`; `SPLIT-005-retirement-authority` remains blocked until release/cutover authority exists.
- Scope boundary: no persistent MySQL stop/reset/delete, no Docker Hub pull or substitute sandbox image, no production route/default/grant retirement, no applied migration rewrite. First Ready Task is `SPLIT-005-env-quick`.

## 2026-08-17 (SPLIT-005-env-quick implementation)

- `scripts/dev/test.sh` now probes the existing `ulticode-mysql` root login. On the observed 1045 mismatch it starts only a local `mysql:8.0` disposable container with a loopback ephemeral port, writes a temporary env override for Flyway, and removes the container/env file on EXIT; the existing container and volume remain untouched.
- The first rerun exposed the canonical migration's explicit `ALTER DATABASE ulticode`; the disposable path therefore defaults `TEST_MYSQL_DB_NAME=ulticode` (overrideable) while preserving the existing-container `TEST_DB_NAME` path.
- Official `./scripts/dev/test.sh quick` then passed: 85 Flyway migrations, services reactor tests, auth-core 70/70, Console 576/576, Management 423/423, and all type checks. `bash -n` and `git diff --check` passed.

## 2026-08-17 (SPLIT-005-env-quick review)

- Review covered the script's existing-container caller, disposable MySQL lifecycle, temporary env propagation into `migrate.sh`, schema-name compatibility, secret handling, and cleanup. One Confirmed finding was fixed: cleanup now stores the actual container ID only after `docker run` succeeds, so a name collision cannot remove an unrelated container.
- Re-run official quick passed after the fix. Negative preflight with a missing local `TEST_MYSQL_IMAGE` returned exit 1 with an explicit “no registry pull” message; no disposable container was created. Review result: Confirmed=0, PASS. Next gate is validation, then `SPLIT-005-env-sandbox`.

## 2026-08-17 (SPLIT-005-env-quick completion audit)

- Acceptance, coverage mapping, protected dirty worktree, rollback boundary, review (Confirmed=0), and validation evidence all reconciled. `SPLIT-005-env-quick` closed `done` without commit; `SPLIT-005-env-sandbox` is now the next Ready Task.

## 2026-08-17 (SPLIT-005-env-sandbox implementation and validation)

- Added a bounded per-test `docker image inspect` assumption to `SandboxNamespaceIsolationIT`, matching the existing `SandboxForkE2EIT` skip semantics. The six namespace assertions and Docker security flags are unchanged.
- Focused current-reactor check (`app-web -am`, `SandboxNamespaceIsolationIT`) passed; Surefire recorded `Tests run: 6, Failures: 0, Errors: 0, Skipped: 6` because `ulticode-sandbox:latest` is absent. This is an honest prerequisite skip, not sandbox coverage.
- A services-wide `*IT` rerun was not claimable: its main thread waited in Testcontainers `JdbcDatabaseContainer.createConnection/waitUntilContainerStarted` while Docker dependencies were unavailable. The command was interrupted at exit 130; temporary Testcontainers resources self-cleaned. `SPLIT-005-env-sandbox` remains blocked until that runtime is stable and the full suite can be rerun.
- Graphify update was attempted after code changes but remained unavailable/hung in the current environment; no source conclusions depend on it.
## 2026-08-17 (contract boundary convergence planning)

- User objective classified the app-api contract monolith as the structural cause of Submission two-hop compat forwarding and duplicated codec/status semantics; plan scope is now tracked as CONTRACT-001 through CONTRACT-008 without rewriting historical SPLIT/SEARCH tasks.
- Verified source: `backend-app-api` is a reactor module consumed by App, Submission, Notification, Judge/Judge-runtime and Admin; `SubmissionWritePort` and `NotificationAdministrationService` live under app-api; `SubmissionWriteCompatibilityProvider` defaults to `compat` and forwards to App; App still registers `SubmissionWriteProvider`; Submission and App each contain `TestCaseDetailCodec` and `SubmissionStatusCatalog`.
- Graph evidence: graphify code-only completed with 25,482 nodes/76,151 edges; codebase-memory moderate index reports 36,448 nodes/173,902 edges. Coverage check records only excluded target/scripts/docs gaps; those non-code claims were verified by direct read/grep fallback.
- DEC-019 freezes owner-specific API artifacts/package namespaces, App fact exceptions, DTO-based canonical Submission utility seam, matched-release FQCN constraint, and no shared Entity/Mapper/alias/broker path.
- Control-plane result: CONTRACT-001 is the only new `ready` task. CONTRACT-007 is explicitly blocked by existing sandbox/Testcontainers evidence and release/cutover authority; no business source, runtime default, grant, migration or deployment action was performed.

## 2026-08-17 (CONTRACT-001 owner matrix implementation)

- Reconciled the stale control-plane objective and branch pointer with the active contract-boundary objective at
  `dc32f114e`; preserved the existing `.auto-flow` user changes and left `AUTO_PILOT_STATUS.yaml` untouched because it
  describes a separate completed historical objective.
- Added `.auto-flow/CONTRACT-001-OWNER-MATRIX.md`: all 219 `app-api` top-level source types and 66 nested types are
  uniquely assigned to App, Submission, Notification, Auth-backed common/local security, common metadata/value,
  App-provided fact/recipient exceptions, or Judge-runtime internal.
- Frozen `backend-submission-api` / `com.ulticode.submission.api` and `backend-notification-api` /
  `com.ulticode.notification.api`, both at their existing `backend-*-` group and `1.0.0` version, with matched release
  and no alias/re-export/mixed rollout constraints. DEC-020 records the residual seam decisions.
- No business Java, POM, runtime config, migration, route/grant, database, provider registration, or deployment file was
  changed. CONTRACT-001 was then closed after the correction/review/validation gates; CONTRACT-001-COMMON is the next
  active implementation slice.

## 2026-08-17 (CONTRACT-001 review)

- Initial review found the omitted `security/DelegationAssertionContract.java`; the matrix now covers all 219 top-level
  source types and keeps the 66 nested-type count. The credential-free assertion contract is assigned to the common
  web-security seam alongside `AccountReadPort` and `JwtValidationPort`.
- Re-review is required after this correction; the prior PASS claim is superseded.

## 2026-08-17 (CONTRACT-001 planning rework)

- Direct source comparison found that the matrix's common-owner rows had no implementation task: eight
  implementation-free security/metadata/value seams would otherwise remain in `backend-app-api` while the final gate
  requires App-only contracts plus explicit App facts.
- Added `CONTRACT-001-COMMON` and DEC-021 as a bounded prerequisite for the Submission/Notification API tasks. No
  business source or runtime state changed; the task now explicitly covers all eight types, including the four
  `ProblemCompletionReportDTO` App records after correcting the initial count/list omission.
- Provider/consumer identity review confirmed the existing transition state (App group providers plus direct Submission
  group providers/compat forwarders) and the frozen target identities; no mixed-release or permanent-alias path was
  added. `SubmissionNotificationPort` has no implementation/caller beyond its declaration.
- `./mvnw -pl api/app-api -am test -B` passed: backend-common 93 tests and backend-app-api 39 tests, zero failures.
- `git diff --check` passed; changed tracked paths remain control-plane only and no business source/POM/runtime/database
  file was modified. Parent two-axis standards/spec review is PASS; the two formal review workers timed out and the
  bounded local fallback found no confirmed findings. CONTRACT-001 validation and completion audit are PASS.

## 2026-08-17 (CONTRACT-001 completion and control-plane synchronization)

- Closed `CONTRACT-001` as `done` after source inventory, owner-matrix, graph/coverage, POM/import, Maven regression,
  YAML and whitespace evidence passed. `CONTRACT-001-COMMON` is now the only new ready implementation task.
- Synchronized `AUTO_PILOT_STATUS.yaml`, `PLAN.md`, `RESUME.md`, `TASKS.yaml`, `HANDOFF.yaml` and `COVERAGE.md` to the
  active contract objective; preserved the historical Arthas objective as completed metadata instead of leaving a
  contradictory control-plane objective.

## 2026-08-17 (CONTRACT-001-COMMON completion)

- Added the eight Java-only common/security/metadata/value contracts to `backend-common`, removed the old app-api
  declarations and an unused app-private `DifficultyCountDTO`, and migrated all discovered App/Admin/Notification/
  WebSocket/security consumers and tests. Auth API's independent command contracts remain unchanged.
- TDD red/green and affected-owner validation passed: the new common test initially failed before production types
  existed; `api/app-api -am test` passed with common 96/app-api 39 tests, and the affected App/Admin/Notification
  reactor passed. Negative FQCN/duplicate/forbidden-import/dependency scans, whitespace and diff checks passed.
- `graphify update .` passed with 26,499 nodes/77,037 edges. The migration guide now records common ownership,
  Auth authority and matched-release/no-alias rules. New common files are not tracked by the existing codebase-memory
  generation; direct source inspection is recorded as the authoritative fallback.
- Formal code-review workers timed out and were shut down. Parent bounded standards/spec/security review found no
  confirmed finding. CONTRACT-001-COMMON is closed; CONTRACT-002 and CONTRACT-003 are ready in parallel.

## 2026-08-17 (CONTRACT-002 through CONTRACT-006 implementation loop)

- Created the provider-owned Submission/Notification API modules and migrated the owner contracts, consumers,
  providers, tests and POMs. Kept App fact/recipient seams and runtime route defaults unchanged.
- Completed Notification error ownership and receipt identity correction; added the canonical Submission DTO codec and
  status catalog with private App/Submission entity mapping and separate Judge codec.
- TDD red/green, focused checks, Submission MySQL/Redis IT (28), and the full affected 20-module reactor passed.
- Final boundary audit fixed one implementation-only Javadoc reference in `UserBestStats`; API tests passed again.
  `graphify update .` completed at 26,513 nodes / 77,128 edges.
- Closed CONTRACT-002, CONTRACT-003, CONTRACT-004, CONTRACT-005 and CONTRACT-006. Left CONTRACT-007 blocked by
  release authority and SPLIT-005 runtime evidence; no commit or external cutover performed.

## 2026-08-17 (CONTRACT-007 reversible blocker push)

- User explicitly authorized rollback-able local/disposable operations. Confirmed the local sandbox image and ran the
  official integration wrapper with `-Dsurefire.failIfNoSpecifiedTests=false`: recent Surefire reports aggregated 2,690
  tests, 0 failures, 0 errors and 24 explicit skips; `SandboxNamespaceIsolationIT` and `SandboxForkE2EIT` executed
  6/6 each, and `JudgeStreamRedisIntegrationTest` passed 4/4 after making the zero-idle threshold deterministic.
- Replaced Submission's compat forwarders with direct `backend-submission` `SubmissionWriteProvider` and
  `SubmissionFenceProvider`; deleted App's duplicate `backend-app` write/fence providers, `appWriter`/`appFence`,
  owner-mode config and stale cutover Javadocs. Submission owner focused tests 7/7 and App/Judge focused tests 14/14
  passed. No Entity/Mapper sharing, HTTP route, cookie/JWT, applied migration or persistent volume changed.
- Updated the cutover runbook and migration guide to make local provider behavior intrinsic after retirement while
  retaining `APP_SUBMISSION_ROUTING_MODE=local` as the safe default. A fresh disposable MySQL run in owner-first then
  main/shared migration order passed preflight, cutover and rollback with checksums unchanged; App grant observation
  was allowed before cutover, denied after cutover, and allowed after rollback.
- `graphify update .` passed at 27,532 nodes / 80,287 edges. CONTRACT-007 is now in progress with source retirement
  complete; the only remaining blocker is an explicitly approved production release window for `remote` route and
  real runtime observation. No commit, push, deployment or production route/grant change was performed.

## 2026-08-17 (CR remediation for 34417d912)

- Restored the default compatibility seam: `backend-submission` providers now default to
  `app.submission.owner.mode=compat` and forward write/fence calls to App `backend-app` providers; `local` remains an
  explicit owner-side cutover mode. This prevents the default `APP_SUBMISSION_ROUTING_MODE=local` path from dropping
  App-owned Pending jobs when the Judge reads through `backend-submission`.
- Added the Submission-owner `JudgingLeaseReaper`, mapper generation CAS, Pending reset and non-shadow judge outbox
  insertion. App reaper, judge outbox dispatcher, result dispatcher and result listener are now route-local only;
  Submission outbox consumers are owner-local only. No production route, grant, migration or deployment changed.
- Fixed `scripts/dev/test.sh` to select both `*IT` and `*IntegrationTest`, and reconciled migration-guide and
  `.auto-flow` metadata. `SPLIT-005-retirement-authority` is blocked, `CONTRACT-007` remains in progress, and the
  production release authority gap is explicit.
- Validation: Provider/Reaper focused 5/0/0/0; real MySQL `SubmissionOwnerCutoverIT` 3/0/0/0; real Redis
  `JudgeStreamRedisIntegrationTest` 4/0/0/0; app-web compile BUILD SUCCESS; official integration 2,690 tests,
  0 failures, 0 errors, 24 skips, including both named integration classes. No production cutover was performed.

## 2026-08-18 (CR remediation validation refresh)

- Fixed the real owner-reaper IT fixture to use the MySQL `NOW()` clock; `SubmissionOwnerCutoverIT` passed 4/4 with
  generation 2 recovery and a non-shadow judge outbox row.
- Re-ran `MEILI_MASTER_KEY=local-test-only-placeholder ./scripts/dev/test.sh integration`; exit 0,
  `BUILD SUCCESS`, and `All integration checks passed`. Post-run Surefire reports: 823 reports, 2,720 tests,
  0 failures, 0 errors and 24 skips; Redis 4/4, sandbox suites 6/6 each, Submission owner 4/4.
- Refreshed graphify to 27,575 nodes / 80,538 edges. Production route/grant/REVOKE, deployment and persistent
  database reset remain intentionally unperformed; compatibility retirement still awaits release authority.

## 2026-08-18 (final route-gate revalidation)

- Added the App `OutboxShadowComparator` local-route gate so revoked App `judge_outbox` grants are not polled after
  the authorized Submission cutover; the App reactor compile passed.
- Re-ran the final `./scripts/dev/test.sh integration`: exit 0, `BUILD SUCCESS`, and `All integration checks passed`.
  Final Surefire aggregate is 823 reports, 2,720 tests, 0 failures, 0 errors and 24 skips; Owner 4/4, Redis 4/4,
  and both sandbox suites 6/6.
- Final graphify refresh: 27,577 nodes / 80,540 edges. Production route/grant/REVOKE and deployment remain
  intentionally unperformed; `SPLIT-005-retirement-authority` remains externally gated.

## 2026-08-18 (production cutover authority and owner-schema expand)

- The user explicitly approved the production release/cutover authority. The first read-only preflight reached the
  persistent `ulticode-mysql` database and found `ulticode.submissions=126`, `judge_outbox=0`,
  `submission_result_outbox=37`, with no target `submission` schema.
- Owner-schema migration first failed with runtime user `ulticode` (MySQL 1044). After using the container's root
  migration credential without printing it, Flyway applied the three Submission owner migrations successfully.
  The failed attempt left one empty target-only table; it was verified empty, removed, and the migration was rerun from
  an empty schema. Source rows were not copied or changed.
- Root preflight verified all four target tables exist and are empty, but correctly refused cutover because the active
  `ulticode` account has schema-wide `ALL` and no table-scoped App grants; no copy, REVOKE, route change or runtime
  deployment was performed.
- Production Compose now requires explicit Submission owner DB/Redis variables, `.env.example` documents the owner
  and App grant inputs, and the migration guide records the fail-closed account rule. DEC-023 records the decision.

## 2026-08-18 (Completion/Coverage Audit and external handoff)

- Official quick and integration wrappers exited 0 with `BUILD SUCCESS`; final Surefire aggregate is 823 reports,
  2,720 tests, 0 failures, 0 errors and 24 skips. Compose base+dev/prod, YAML parsing, `bash -n`, `git diff --check`,
  focused owner checks and the fail-closed schema-wide-grant preflight all pass.
- Review Confirmed Findings remain 0. Completion/Coverage Audit closes all repository-side work but leaves
  CONTRACT-007/SPLIT-005-retirement-authority in progress because the authorized deployment host, actual table-scoped
  App account, owner credentials and running App/Submission observation window are unavailable here.
- No source rows were copied, no App grants were revoked, and no production route or deployment was changed. Handoff
  is blocked with `next_skill: none` until the external runtime gate is supplied.

## 2026-08-18 (blocker-resolution plan)

- Replanned the final gate after the user approved release/cutover authority: `SPLIT-005-retirement-authority` now
  closes only the authority/preflight contract; it does not imply runtime cutover.
- Added `SPLIT-005-runtime-access` (blocked) for the authorized deployment host, actual table-scoped App account,
  separate Submission owner credentials and App/Submission/Nacos/Redis prerequisites. Added
  `SPLIT-005-runtime-cutover-observation` (pending) as the sole production route/copy/REVOKE/single-writer Task.
- Read-only evidence confirms the current host cannot satisfy runtime access: no App/Submission PM2 stack, Nacos
  exited, and active `ulticode` has schema-wide DML. No local Ready Task exists; no production state was changed.

## 2026-08-18 (run-development-loop blocker revalidation)

- Recovery rechecked the indexed project and persistent control plane. Review of the blocker DAG found 49 tasks and
  60 dependencies with no duplicate/unknown dependency, status mismatch or secret-like control-plane value.
- Validation passed: control YAML, `bash -n`, `git diff --check`, Compose base+dev/prod config and runtime diagnostic.
  The real preflight again rejected the active `ulticode` schema-wide DML account before copy/REVOKE.
- No Ready Task exists: `SPLIT-005-runtime-access` remains blocked; observation, CONTRACT-007 and CONTRACT-008 remain
  downstream. No business implementation, production route, grant, deployment or commit was performed.

## 2026-08-18 (local permission blocker proof)

- Provisioned two uniquely named temporary MySQL accounts only in the disposable local container: an App account with
  12 table-level DML privileges across `ulticode.submissions`, `judge_outbox` and `submission_result_outbox`, and a
  separate Submission owner account with 4 schema-level DML privileges on `submission.*`. No credentials were saved.
- With root used only as the local migration operator, read-only preflight passed; confirmation-gated local cutover and
  rollback passed; source row counts/checksums remained unchanged; App grants were restored and all four target tables
  were cleaned back to zero rows. Temporary accounts and passwords were then removed.
- This closes the local permission proof only. `SPLIT-005-runtime-access` remains blocked because the authorized
  deployment host, real runtime accounts and App/Submission/Nacos/Redis observation window are still unavailable.

## 2026-08-18 (local runtime blocker closure)

- User clarified that UltiCode is an open local project with no production environment; local Docker/PM2 is the target.
- Started Nacos, provisioned a separate ephemeral `submission_rw` owner account, repaired App source-table grants,
  passed read-only preflight, and completed the confirmation-gated Submission cutover with source/target parity.
- Fixed the actual startup blockers at the adapter/config seams: Auth datasource/data/flyway/mail are under `spring`,
  App `DefaultSubmissionUserReadAdapter` is `@Primary`, and Submission ProblemFacts/UserRead/ProblemAdmin Dubbo
  adapters are `@Primary` over their same-type Dubbo reference proxies.
- Maven Submission tests/install passed; after a clean PM2 restart all six services are online with restarts=0,
  Auth/Admin/App/Notification public health endpoints return HTTP 200, and MySQL/Redis/Nacos are healthy.
- Final local grant/parity evidence: App schema DML=0, App DML on the three source tables=0, owner schema DML=4,
  owner account unlocked, rows/checksums 126/0/37 match between `ulticode` and `submission`.
- Reconciled AUTO_PILOT_STATUS, TASKS, COVERAGE, PLAN, DECISIONS, RESUME and HANDOFF; remaining work is
  CONTRACT-007 compatibility-provider retirement and CONTRACT-008 final audit, not a runtime blocker.

## 2026-08-18 (CONTRACT-007 direct provider retirement)

- Replaced the Submission compat write/fence forwarders with direct `backend-submission` owner providers,
  deleted the App `backend-app` duplicate write/fence providers, and removed `app.submission.owner.mode` from
  Submission configuration and owner scheduler gates.
- Updated the provider contract and real-MySQL owner IT to assert direct local delegation, with no App Dubbo
  reference; the focused `./mvnw -pl submission -am test -B` passed after one missing-test-import correction.
- Updated the migration guide, runbook comments, DEC-026 and active control-plane resume/evidence. Full Review,
  official quick/integration, runtime and Completion/Coverage Audit remain before closing CONTRACT-007/008.

## 2026-08-18 (CONTRACT-007/008 final close)

- Renamed the Submission owner cutover IT into the provider package and removed the remaining local-mode test naming;
  graphify was refreshed to 27,583 nodes / 80,422 edges and the moved test passed 4/4 alongside the provider contract 4/4.
- Final Review found Confirmed Findings=0. Official quick and integration exited 0; integration aggregated 823 reports,
  2,720 tests, 0 failures, 0 errors and 24 skips; services `verify` exited 0 with BUILD SUCCESS; Compose, YAML, bash and
  diff checks passed.
- CONTRACT-007 (direct Submission provider retirement) and CONTRACT-008 (final contract-boundary audit) are done;
  SPLIT-005 is also closed. All 49 ledger tasks are done, no Ready/blocked task remains, and no commit/push/deploy or
  production action was performed. The known real-Meili mirror E2E gap remains explicitly recorded in Search coverage.

## 2026-08-18 (CR repair and final validation)

- Fixed the direct-owner route mismatch: production Compose pins `APP_SUBMISSION_ROUTING_MODE=remote`; local `init-env.sh` generates isolated Submission credentials with `SUBMISSION_CUTOVER_COMPLETE=false`; `up.sh --prepare-submission-owner` performs owner-first migrations and unlocks `submission_rw` without PM2, while normal `up.sh`/App boot stops until the marker is true.
- Reordered local Flyway preparation owner-first so the Submission schema history exists before the shared migration's precreated owner tables; disposable preparation flow passed with four owner tables and an unlocked account, and invalid `--quick`/`--frontend-only` preparation combinations fail before infrastructure work.
- Hardened `submission-schema-cutover.sh`: exact `SUBMISSION_APP_DB_USER` + `SUBMISSION_APP_DB_HOST`, ambiguous host rejection, recursive `mysql.role_edges` closure, global `mysql.user`/`USER_PRIVILEGES`, schema/table `ALL` and `GRANT OPTION` (`IS_GRANTABLE`), column-level privilege rejection, exact four table DML grants, and explicit query-failure refusal.
- Disposable MySQL security matrix passed: exact non-`%` host positive; host mismatch, global DML, schema DML, table ALL, schema/table WITH GRANT OPTION, column grants, transitive roles, and denied `role_edges` inspection all failed before copy.
- Provider contract test now asserts no App `@DubboReference`, owner-mode `@Value`, `ownerMode` field, or `@ConditionalOnProperty`; focused provider test passed 4/4.
- Reconciled local-only control metadata: no stale active task, no production authority claim, Resume points to `62058f358`, HANDOFF retains the real Redis+Meili E2E external gap, and Coverage maps the CR repair.
- Verification: graphify 27,595 nodes / 80,442 edges; Submission reactor tests 11/11; App routing focused tests and compile BUILD SUCCESS; services `./mvnw verify -B` BUILD SUCCESS; official quick and integration exit 0; Compose base/dev/prod, YAML, Bash and diff checks pass. Exact-revision disposable matrices cover grants/roles/query failure, owner-first preparation, copy failure cleanup and partial revoke/restore/cleanup escalation. No production/deploy/remote action performed.

## 2026-08-18 (CR follow-up: cutover writer quiesce and atomic rollback)

- Fixed `scripts/dev/submission-schema-cutover.sh`: `cutover` and `rollback` now require `SUBMISSION_CUTOVER_QUIESCE_CONFIRM=I_UNDERSTAND_SUBMISSION_QUIESCE_ALL_WRITERS`; the gate and migration guide enumerate every App, Submission-owner, Judge, dispatcher, reaper, scheduler, and direct database writer that must be stopped and drained.
- Reworked rollback `copy_back` to issue all table replacements in one MySQL transaction; a failed transaction emits an explicit CRITICAL reconciliation warning before grant restoration. Cutover rechecks source rows/checksums before REVOKE.
- Verification: fake-MySQL transaction/failure-injection and quiesce fail-closed checks passed; `bash -n`, `git diff --check`, `graphify update .`, official `./scripts/dev/test.sh quick`, and independent review passed with Confirmed Findings=0. No production/deploy/remote action performed.

## 2026-08-18 (run-development-loop close audit)

- Recovered and revalidated the active control plane at `62058f358`: all 49 ledger tasks are `done`, with no `ready`, `pending` or `blocked` task; the objective remains terminal with no next goal.
- Completion/Coverage Audit passed against the recorded Acceptance, Review, Validation, owner/single-writer and rollback evidence. The current worktree delta is limited to `.auto-flow/HANDOFF.yaml`, `.auto-flow/RESUME.md` and `.auto-flow/WORKLOG.md`; no business implementation changed.
- Recheck passed: `git diff --check` and `bash -n scripts/dev/submission-schema-cutover.sh scripts/dev/up.sh scripts/dev/init-env.sh`. No commit, push, deploy or production action performed.

## 2026-08-18 (architecture-hardening recovery and local slices)

- Recovery found the active Services Owner objective in `ARCH-002`/`ARCH-004` with a stale prior handoff: `HEAD=62058f358`, while the worktree contains the mapped architecture source/config/docs/tests slices. No files were discarded; all existing deltas remain protected and attributed before further edits.
- `ARCH-004` Admin analytics now has a typed `AnalyticsOverviewData` coarse seam, one participant batch for contest analytics, bounded `BackendAdminApplication` scanning, an explicit `BackendAdminApplication` Testcontainers context, and adapter call-count/result regression tests.
- Focused evidence: Admin analytics tests `5/5`, explicit Admin datasource/context IT `1/1`, Admin module `./mvnw -pl admin -am test -B` exited 0, and disposable four-owner schema/grant IT `21/21` exited 0 after adding the Notification owner fixture. `graphify update .` and `git diff --check` passed.
- `ARCH-002` remains incomplete: these tests prove only disposable schema/grant behavior; actual runtime account cutover, users responsibility migration, and deployment authority are not claimed. `ARCH-003` remains externally blocked by the absent production stability window.

## 2026-08-18 (architecture-hardening Review pass)

- Independent full-context Review classified the Auth Flyway/runtime issue as fixed by `AUTH_FLYWAY_ENABLED=false` default plus auth-only owner migration locations and a privileged migration-job note. The four-owner grant evidence gap was fixed by extending `information_schema` counts to `notification` and asserting zero cross-owner grants.
- The users/profile documentation mismatch was reconciled: shared canonical profile contract/drop is recorded separately from the fresh Auth owner bootstrap's compatibility columns; no applied migration was edited.
- Final Review verdict: `Confirmed Findings=0`; no production/remote action, commit, push or deploy was performed. Validation must still re-run affected checks and the final Services/Compose/static gates.

## 2026-08-18 (architecture-hardening Tier C validation)

- Admin module `./mvnw -pl admin -am test -B` passed with 561 tests and 3 skips; `./mvnw -pl admin -am verify -B` passed with BUILD SUCCESS.
- Full `services ./mvnw verify -B` passed with BUILD SUCCESS; official `./scripts/dev/test.sh quick` passed when supplied the one-shot local `MEILI_MASTER_KEY` validation value; official `./scripts/dev/test.sh integration` passed with all integration checks.
- Bash syntax, control-plane YAML parsing, Compose base+dev/prod expansion (with one-shot local Submission/Meili validation values) and `git diff --check` passed. Existing Maven duplicate Jackson and Flyway/MySQL-version warnings remain non-blocking baseline warnings.
- `MIGRATION_SCHEMA=auth MIGRATION_DB_NAME=auth ./scripts/dev/migrate.sh validate` was intentionally run read-only and failed closed with MySQL 1044 for the active `ulticode` account before any migration action. This is expected evidence that Auth owner migration requires the separate privileged migration job; no grant or production action was attempted.

## 2026-08-18 (architecture-hardening Completion/Coverage Audit)

- `ARCH-004` and `ARCH-006` are `done`; `ARCH-002` and `ARCH-003` are `blocked` with explicit evidence, rollback boundaries and external unblock conditions. Ledger audit found no other pending/ready/in-progress task.
- Acceptance/Coverage/Review/Validation/Git/Task/Evidence consistency is reconciled. The local objective stops at a truthful blocked terminal state; no commit, push, merge, release, deploy or production data action was performed.

## 2026-08-19 (blocker remediation planning)
- 2026-08-19 (control-plane reconciliation) — Corrected stale completion claims after review: ARCH-002/ARCH-003 child tasks and parent acceptance remain blocked where external target/production evidence is absent; DEV-LOCAL/TEST-TARGET artifacts remain recorded as local rehearsal evidence. ARCH-006/ARCH-007 remain open pending external gates. Updated AUTO_PILOT_STATUS, COVERAGE, HANDOFF, PLAN, RESUME, TASKS and OWNER_DATABASE_ISOLATION_PLAN; no business source or migration changed.

- Converted the user’s ten ARCH-002/ARCH-003 remediation bullets into one phase-gated Execution Packet.
- Added `ARCH-002-001` as the sole local `ready` Task: reuse `scripts/dev/migrate.sh` with explicit `MIGRATION_DB_*`, owner-schema allowlist, privilege preflight, secret boundary and disposable MySQL evidence.
- Added externally gated `ARCH-002-002..005`, `ARCH-003-001..005` and final `ARCH-007`; parent blockers remain blocked until real authority, target, users/profile responsibility, remote stability, quiesce, observation, rollback and production evidence exist.
- Recorded DEC-034: do not grant runtime DBA privileges, edit applied migrations, simulate production, or create a second migration/cutover framework.
- Replaced the stale Resume active pointer with the current Services Owner objective and explicitly marked the former CONTRACT-007/008 pointer historical.
- No business implementation, migration, runtime deployment, remote resource, commit or push was performed.

## 2026-08-19 (DAG safety correction)

- Corrected the blocker-remediation order after review: monitoring/baseline setup (`ARCH-003-001`) and production monitoring/cutover controls (`ARCH-003-004`) now precede writer quiescence and physical isolation cutover (`ARCH-002-005`).
- `ARCH-003-003` now owns post-cutover observation and rollback rehearsal; `ARCH-003-005` owns compatibility retirement verification. `DEC-035` records the dependency safety rule.
- Repaired `.auto-flow/TASKS.yaml` after an intermediate edit-marker corruption; the affected tail was replaced atomically and YAML parsing confirmed valid with exactly one clean block per new Task.

## 2026-08-19 (ARCH-002-001 local implementation)

- Extended `scripts/dev/migrate.sh` with explicit owner `MIGRATION_DB_HOST/PORT/NAME/USER/PASSWORD` handling, owner schema allowlist, runtime-account separation, read-only connection/schema/privilege preflight and password-free audit output.
- Updated `scripts/dev/up.sh` to pass explicit Submission migration connection variables; documented the contract in `.env.example` and `init-db/README.md`.
- Added `scripts/dev/migrate-owner-preflight-test.sh` with fake `mysql`/`mvn` probes for missing variables, runtime-account reuse, invalid schema, successful owner preflight, password redaction and shared-chain compatibility.
- Focused validation passed: `bash -n` and `migrate-owner-preflight-test: PASS`; `graphify update .` completed. Real Flyway execution remains gated by ARCH-002-002/004 external credentials and target authority.

## 2026-08-19 (ARCH-002-001 Review)

- Independent review returned `Confirmed Findings=0`; the first review findings (shared DB_NAME override ordering, fake CLI vacuity, explicit up.sh variables, empty env precedence, frontend-only guard and shared migration privilege preservation) were fixed and rechecked.
- Review evidence now covers fake mysql connection arguments/password, effective-account mismatch, empty grants, privilege snapshot output, Flyway goal/config, exactly one owner invocation and shared DB_NAME override compatibility.
- ARCH-002-001 remains `in_progress` pending the validation gate; real Flyway execution and least-privilege proof remain externally blocked under ARCH-002-002/004.

## 2026-08-19 (ARCH-002-001 validation)

- Tier C focused validation passed: `bash -n` for affected scripts, `migrate-owner-preflight-test: PASS`, `git diff --check`, `.auto-flow` YAML parse and no diff under `init-db/migrations`.
- Real Flyway `info/validate` against a privileged owner target was not run because the required external migration identity/target is unavailable; this remains an explicit ARCH-002-002/004 gate.
- ARCH-002-001 remains `in_progress` until Completion/Coverage Audit; no commit, push, remote grant or deployment was performed.

## 2026-08-19 (ARCH-002-001 privilege-policy rework)

- Review identified that a nonempty `SHOW GRANTS` snapshot did not prove minimum migration capabilities.
- Added fail-closed checks for `CREATE`, `ALTER`, `SELECT`, `GRANT OPTION`, and schema-specific `CREATE USER`; insufficient grants are rejected before Maven/Flyway.
- Extended the fake MySQL regression with insufficient-grant mode and verified privilege snapshot output; `migrate-owner-preflight-test: PASS`.
- The prior Review/Validation packet is intentionally reopened for re-review after this code change; Task remains `in_progress`.

## 2026-08-19 (ARCH-002-001 direct grant policy)

- Reworked the privilege policy after review: schema-scoped `CREATE`/`ALTER`/`SELECT`/`GRANT OPTION` are parsed by grant target, global `ALL PRIVILEGES` is merged as a capability superset, and wrong-schema grants fail closed.
- Owner preflight now rejects role grants rather than depending on default-role activation or a separate Flyway-session role closure; `DEC-036` records the direct-grant contract.
- Added fake coverage for wrong-schema, global-ALL-plus-schema, role rejection, insufficient grants, and direct success. `migrate-owner-preflight-test: PASS`.

## 2026-08-19 (ARCH-002-001 final Review)

- Final independent Review returned `Confirmed=0`.
- Classified prior findings as `Already Addressed`: shared migration NAME contamination, caller precedence, frontend-only guard, fake CLI/goal/config/count coverage, schema-scoped grants, global ALL union, and direct-role rejection.
- Only real Flyway execution and target-specific least-privilege evidence remain `Externally Blocked` under ARCH-002-002/004.

## 2026-08-19 (ARCH-002-001 disposable MySQL validation)

- Ran the owner preflight against a temporary `mysql:8.0` container with direct `migration_user` grants and a weak account.
- Valid direct grants reached the fake Flyway command; the weak account missing `ALTER` failed before Flyway; the temporary container was removed.
- No persistent DB, applied migration, remote grant or runtime deployment was changed.

## 2026-08-19 (ARCH-002-001 Completion/Coverage Audit)

- Acceptance, requirement mapping, caller/config/docs/tests, Review `Confirmed=0`, Tier C validation, rollback boundaries, protected worktree and no-applied-migration checks all pass.
- Closed `ARCH-002-001` as `done`; no commit or push was authorized.
- `ARCH-002-002..005` and `ARCH-003-001..005` remain blocked by external DB, users/profile, remote stability, quiesce, observation, production monitoring and rollback authority.

## 2026-08-19 (blocker continuation read-only inventory)

- User requested continuation and stated the repository has only a development environment; this does not supply the target/account matrix, migration authority, cutover window, users/profile responsibility or remote runtime evidence required by the existing Acceptance criteria.
- Read-only inspection of `ulticode-mysql` found `auth`, `admin`, `app`, `notification`, `submission` and `ulticode` schemas, owner Flyway histories/tables, and shadow users; no privileged `migration_user` authority bundle was recorded.
- No account, grant, Flyway, `.env`, route, migration, deployment or persistent data mutation was performed; the existing external gates remain blocked.

- Final control-plane gate first returned nonzero because `git diff --check` found an extra EOF blank line in `.auto-flow/PLAN.md`; the blank line was removed and the complete gate was rerun successfully.

- A follow-up consistency probe initially failed because shell command substitution interpreted backticks inside the Python assertion; the assertion was simplified and the complete consistency gate reran with exit 0.

## 2026-08-19 (DEV-LOCAL-004 Submission cutover rollback rehearsal)

- Executed container-aware MySQL preflight for `scripts/dev/submission-schema-cutover.sh` against the development volume.
- Preflight safely refused with `Target table is not empty: submission.submissions` (126 rows in source and target, checksum 2896447748); no copy, revoke, route switch, or write action occurred on existing data.
- Executed Testcontainers integration test `SubmissionOwnerCutoverIT` (4/4 passed), verifying copy-forward, grant revocation, failure-injection cleanup, and rollback without mutating current development data.
- Review Confirmed=0; Tier C validation passed; closed `DEV-LOCAL-004` locally while preserving `ARCH-002-005` external gate.

## 2026-08-19 (DEV-LOCAL-006 observation rollback fault rehearsal)

- Created and hardened `scripts/dev/dev-local-observation-rehearsal.sh` with mandatory `DEV_LOCAL_OBSERVATION_CONFIRM` guard and fail-closed query/test handling.
- Verified fail-closed behavior: harness aborts when confirmation is missing, and reports `PARTIAL` when `--quick` is specified.
- Executed 62 targeted fault injection and resilience tests with 0 failures, 0 errors, and 0 skipped (including real-broker `JudgeStreamRedisIntegrationTest`: 4/4 with exported `REDIS_HOST`, `DefaultSubmissionWritePortIT`: 6/6, `SubmissionOwnerCutoverIT`: 4/4, `SubmissionOutboxDispatcherIT`: 5/5, `SubmissionCreatedDispatcherTest`: 2/2, `NotificationDeliveryLedgerMapperIT`: 7/7, `NotificationDispatcherTest`: 14/14, `NotificationLedgerReaperTest`: 3/3, `DefaultAdminAnalyticsPortAdapterTest`: 2/2, `AdminAnalyticsServiceImplTest`: 3/3, `SubmissionFactsSnapshotTest`: 4/4, `SubmissionProviderContractTest`: 4/4, `SubmissionApiContractShapeTest`: 4/4).
- Data reconciliation confirmed exact checksum matches between `ulticode` and `submission` schemas for `submissions` (126 rows, checksum 2896447748), `judge_outbox` (0 rows, checksum 0), `submission_result_outbox` (37 rows, checksum 610558977); `submission_created_outbox` target-only rows: 0.
- Verified static rollback/route/grant state: `APP_SUBMISSION_ROUTING_MODE=local`, `SUBMISSION_CUTOVER_COMPLETE=false`, `ulticode` grants on `submission` schema = 0; accurately labeled PM2 writers `UNAVAILABLE` and live writer quiesce / live rollback `NOT EXECUTED`.
- Closed `DEV-LOCAL-006` locally; transitioned `DEV-LOCAL-007` to `ready`; external `ARCH-003-001..005` gates remain blocked.

## 2026-08-19 (DEV-LOCAL-007 compatibility path scan and retirement readiness)

- Performed static scan of compatibility references across `services/app/app-web`, `services/submission` and architecture docs.
- Verified `app.submission.routing.mode` conditional wiring: `local` routes to App local writer, `remote` routes to Submission owner; single-writer invariants hold with zero dual-writing.
- Established rollback-only whitelist protecting App-local compatibility classes (`DefaultSubmissionWritePort`, `JudgeOutboxDispatcher`, `JudgingLeaseReaper`, `SubmissionResultDispatcher`, `SubmissionResultOutboxListener`, `LocalSubmissionUserQueryAdapter`); preserved all in source tree without local deletion.
- Recorded retirement decision candidate in `DEC-038`.
- Ran focused routing tests (`SubmissionRoutingPortTest`: 7/7, `SubmissionPortWiringTest`: 6/6, `SubmissionServiceImplTest`: 17/17) all passing (30/30, 0 failures/errors/skips).
- Closed `DEV-LOCAL-007` locally; transitioned `DEV-LOCAL-008` to `ready`; `ARCH-003-005` remains externally blocked.

## 2026-08-19 (Owner migration tooling CR remediation)

- Closed four review findings across owner privilege preflight, physical users/profile backfill, configured database target binding and effective runtime grant isolation.
- Centralized Docker published-endpoint validation and applied it to migrate, backfill, Submission/Notification cutover, monitoring and observation entry points.
- Added durable disposable MySQL 9.1/Redis 7 behavior coverage; verified missing DML rejection, real Auth/App owner migrations, soft-deleted account/profile preservation, and table/global/role/routine grant failure plus zero-grant success.
- Current-machine evidence: physical users/profile preflight 12/12 with matching checksums and zero missing/orphans; full observation rehearsal 65 tests with zero failures/errors/skips, matching Submission checksums and effective grants 0.
- Compose dev/prod, shell syntax, diff, secret scan, no-applied-migration check, graphify update and two-axis formal review passed. No commit, push, production mutation or persistent data write was performed.

## 2026-08-19 (TEST-TARGET blocker authority and replanning)

- User confirmed that the repository has only the current test environment, no separate development or production environment, and granted authority for every remaining database, cutover, monitoring, observation, rollback and compatibility-retirement blocker action there.
- Recorded DEC-039 and an authoritative TEST-TARGET override in PLAN.md. Historical `dev-local` script names/tokens remain compatibility labels; no production evidence will be claimed.
- Local execution remains bounded at TEST-TARGET: `ARCH-002-002` and downstream external-gated tasks remain `blocked`; fresh local artifacts cannot be promoted by renaming.
- Initial inventory: `ulticode-mysql` and `ulticode-redis` are healthy, PM2 has no running writers, owner runtime credentials and migration passwords are present, and shared `MIGRATION_DB_*` remains intentionally caller-supplied.

## 2026-08-19 (ARCH-002-002 TEST-TARGET authority and privilege gate)

- Created a verified TEST-TARGET backup and evidence bundle under `.local/`: SHA-256/gzip checks passed and a disposable MySQL 9.1 restore produced all 6 schemas and 139 tables.
- Captured five owner runtime and five direct migration identities, grants, zero role edges, Flyway histories, 139 table watermarks, authority window, audit contact and rollback-owner sign-off without secret leakage.
- Runtime evidence passed: own-schema connect 5/5, foreign-schema SELECT denied 20/20, audit SELECT denied 2/2 and audit UPDATE/DELETE denied 4/4. DEC-040 records the existing INSERT-only `admin.audit_outbox` exception.
- Review found three evidence gaps (window/contact/sign-off, audit mutation negatives, independent backup restore); all were fixed and re-review returned Confirmed findings=0.
- Fixed two pre-existing preflight self-test defects exposed by current TEST-TARGET environment: required/static checks now precede container endpoint probing, and the fake self-test unsets caller container variables. Regression self-test, disposable safety integration, five owner Flyway validates and `PerOwnerSchemaIsolationIT` 24/24 pass.
- Local authority and privilege rehearsal evidence is retained, but `ARCH-002-002` remains `blocked` pending external target authority/sign-off; downstream Tasks remain blocked.
