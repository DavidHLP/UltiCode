# Worklog

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
