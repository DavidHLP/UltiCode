# Decisions

## DEC-029 — Submission intake receives immutable facts at the request boundary

- Context: Submission writes previously validated Problem and user facts through synchronous App/Auth callbacks inside the Owner path.
- Decision: App local/remote boundaries create or forward `SubmissionFactsSnapshot`; the Submission Owner validates identity/problem matching and never injects `ProblemFactsPort` or `UserExistencePort` into its write transaction. The legacy no-snapshot owner overload fails closed until all callers migrate.
- Consequences: the write chain has one business Provider hop; read-side enrichment and eventual projection are still separate work. Rollback keeps the App local route and does not restore Owner callbacks.

## DEC-030 — Database isolation starts with reversible configuration, not a false cutover claim

- Context: Submission already has an independent runtime connection, while Auth/Admin/App/Notification still default to the shared database account/name.
- Decision: add owner-specific `*_DB_*` variables with explicit shared fallback and document expand/backfill/verify/cutover/rollback. Do not create/revoke production credentials or remove mixed `users` columns without a real deployment authority and migration evidence.
- Consequences: ARCH-002 remains in progress; configuration readiness is not physical isolation.

## DEC-031 — App compatibility removal is gated by production evidence

- Context: App source defaults to local Submission routing and retains local/remote/legacy compatibility paths; production-only remote routing cannot be validated here.
- Decision: retain the switchable compatibility architecture and mark cleanup blocked until a real remote stability window, all-writer quiesce, observation, and rollback artifact exist.
- Consequences: no default-route or legacy-path deletion is performed in this local run.

## DEC-032 — Owner runtime never executes the shared Flyway history

- Context: `AUTH_DB_*` can point `backend-auth` at an isolated `auth` schema, but the previous runtime Flyway locations included canonical root migrations that create/revoke cross-owner schemas and require a privileged migration account.
- Decision: default Auth runtime Flyway off with `AUTH_FLYWAY_ENABLED=false` and restrict any explicit owner migration location to `init-db/migrations/auth`; run owner migrations separately through `MIGRATION_SCHEMA=auth` and a privileged migration job before enabling an isolated runtime.
- Consequences: owner-specific datasource variables remain preparation-only; no runtime account can silently apply root DDL/GRANT history, and physical account/cutover authority remains a separate ARCH-002 gate.
- Affected tasks: ARCH-002, ARCH-006.

## DEC-033 — Admin analytics uses one bounded coarse query seam

- Context: `AdminAnalyticsServiceImpl` composed six aggregate reads directly and the contest participation adapter previously risked per-contest participant queries; the Admin boot class also relied on a growing regex exclusion list.
- Decision: keep one analytics-overview vertical slice behind `AdminAnalyticsPort.AnalyticsOverviewData`, batch participant lookup by contest IDs, and bound `BackendAdminApplication`/`MapperScan` to Admin-owned packages. Do not rewrite other Admin reports or claim production latency evidence.
- Consequences: the caller has one typed seam with explicit query/RPC call-count regression and a real context IT; broader seam aggregation remains a future scoped task.
- Affected tasks: ARCH-004, ARCH-006.

## DEC-001 — Reuse the shared design-system token seam

- Context: both Vue apps import `packages/design-system/style.css`; localhost:9002 is console.
- Decision: correct canonical colors and semantic light/dark mappings at that existing seam.
- Alternatives: app-local overrides rejected because they duplicate theme ownership.
- Consequences: management receives the same shared-token correction; no new abstraction.
- Affected tasks: TASK-001

## DEC-002 — Make the design-system package the public Solarized seam

- Context: both apps consume the same shared stylesheet, while chart/status semantics and several state colors remain app-local.
- Decision: export the stylesheet from @ulticode/design-system, keep canonical palette and semantic mappings inside that package, and make app code consume only the public Token interface.
- Alternatives: a second theme package or per-app token copies were rejected because they reduce locality and create parallel ownership.
- Consequences: both apps declare the workspace dependency; app adapters retain only renderer- or domain-specific behavior.
- Affected tasks: TASK-002, TASK-003

## DEC-003 — Publish shared component states as scanned package source

- Context: runtime constants alone are invisible to an app-local Tailwind content scan.
- Decision: own Button, Badge, menu and difficulty classes in `packages/design-system/src/variants.ts` and register that directory with `@source "./src"`.
- Consequences: both production bundles retain the same public classes without per-app copies.
- Affected tasks: TASK-003

## DEC-004 — Keep status text neutral and accents structural

- Context: Solarized accent colors do not all reach 4.5:1 as small text in both modes.
- Decision: status surfaces use neutral foreground text; accents are borders, icons or decorations. Primary text-bearing controls use inverted monotone surfaces.
- Consequences: terminal badges, difficulty badges and shared controls satisfy the public accessibility contract.
- Affected tasks: TASK-002, TASK-003

## DEC-005 — Use one public runtime bridge for non-CSS renderers

- Context: ECharts canvas/SVG, Monaco and WebGL cannot reliably consume unresolved CSS `var()` values, and the previous implementation duplicated Solarized HEX/OKLCH literals in each adapter.
- Decision: extend `@ulticode/design-system` with a canonical 16-color runtime palette bridge plus a small `readCssColor` helper. CSS remains the semantic/public owner; adapters read computed semantic tokens first and use the canonical palette bridge only as a renderer fallback. Add a scanner contract for first-party runtime literals.
- Alternatives: keep per-app palette copies (rejected: drift), resolve colors independently in every chart (rejected: duplicate logic), parse generated CSS at build time (rejected: unnecessary infrastructure).
- Consequences: renderer adapters remain thin and theme-aware; canonical values are duplicated only in the public runtime bridge and are locked against `style.css`; third-party vendor, external brand marks and user/API-provided colors remain explicit exceptions.
- Affected tasks: TASK-004, TASK-005, TASK-006, TASK-007

## DEC-006 — Reduced Motion freezes continuous scene motion at the MainScene seam

- Context: Landing WebGL currently updates time, particle uniforms and camera rotation every animation frame and has no `prefers-reduced-motion` branch.
- Decision: `MainScene` owns the media-query state; reduced motion keeps the scene renderable for scroll/theme updates but freezes continuous time-driven particle drift and mouse/camera motion, while owned CSS transitions are disabled. Media-query changes update the same scene without creating a second theme or lifecycle.
- Alternatives: stop the renderer completely (rejected: scroll-driven content would not repaint), duplicate reduced-motion state in each subsystem (rejected: fragmented lifecycle), ignore WebGL and only add CSS media rules (rejected: does not meet the design requirement).
- Consequences: one bounded change at the existing scene seam; cleanup removes the media listener and renderer remains resource-safe.
- Affected tasks: TASK-007, TASK-008


## DEC-007 — Keep canonical accents stable and derive contrast-safe status marks

- Context: Solarized accent semantics must remain stable across Light/Dark, while yellow/green small status text can miss the product contrast threshold.
- Decision: keep the eight raw accent tokens unchanged; expose separate semantic `*-mark` tokens using theme-specific Solarized color mixes for text, icons, borders and chart marks. Status surfaces retain neutral readable text and redundant icon/label cues.
- Alternatives: redefine accent HEX values (rejected: violates the 16-color contract), lower opacity (rejected: worsens contrast), or use arbitrary new colors (rejected: creates a second palette).
- Consequences: raw accents remain canonical for renderer fallbacks; status-mark consumers meet measured Light/Dark contrast without changing semantic hue ownership.
- Affected tasks: TASK-004, TASK-005, TASK-006, TASK-008, TASK-009


## DEC-008 — Status-mark controls use the adaptive primary-control foreground

- Context: Dark `*-mark` tokens are deliberately light for readable standalone marks. Reusing Dark `primary-foreground` on a status-mark background produced a low-contrast light-on-light glyph/thumb.
- Decision: any control or glyph rendered directly on a semantic status mark uses `primary-control-foreground`, whose Light/Dark values invert with the control surface. Switch thumbs use this adaptive token for checked state; status surfaces retain neutral text and redundant cues.
- Alternatives: darken the Dark status marks (rejected: reduces standalone mark contrast), redefine `primary-foreground` globally (rejected: breaks primary action contract), or add per-component arbitrary colors (rejected: violates shared ownership).


## DEC-009 — Deepen Notification Delivery as an App worker before any fourth service

- Context: the architecture report identified Notification Delivery as the strongest extraction candidate. The repository already has `NotificationIntent`, `NotificationChannel`, per-channel `notification_delivery_ledger`, durable inbox/outbox infrastructure, SMTP/WebSocket adapters, and a migration guide that assigns Notification to App. At the same time, notifications, preferences, email templates/logs, and delivery ledger are still App-owned, while Submission verdict effects require durable post-commit handling.
- Decision: keep App as the sole business-data Owner and first deepen Notification Delivery behind its existing intent/channel/ledger seams. Implement durable event publication, inbox idempotency, reclaimable ledger state, bounded retry, per-channel failure isolation, and an independently runnable App worker profile. Do not create `backend-notification`, a fourth logical service, an independent database, or a new broker in this phase.
- Alternatives: immediately create `backend-notification` rejected because it would move storage and runtime ownership at once, amplify the existing outbox/inbox and WebSocket/SMTP failure surface, and risk a distributed monolith. Splitting Contest/Submission intake rejected by D-04 same-transaction behavior. Splitting Search rejected as a first write-owner extraction because its leverage currently comes from a local projection and event-fed index, not remote fan-out.
- Consequences: callers learn one typed intent interface; retry, claim fencing, terminal states, and channel adapters gain locality; worker CPU/I/O can scale independently while HTTP App remains stable. A future physical Notification service remains possible only after a new ADR proves data ownership, event transport, and WebSocket/auth semantics.
- Affected tasks: NOTIFY-001, NOTIFY-002, NOTIFY-003, NOTIFY-004, NOTIFY-005.

## DEC-010 — Make App Contest schema expansion tolerate both baselines

- Context: the shared Flyway location recursively discovers `migrations/app`. The shared baseline already has the full `contests` shape, while `V20260811180000__Create_App_Contest_Schema.sql` is designed to expand the App lightweight baseline; the same `ADD COLUMN` then fails with Error 1060 during `test.sh quick`.
- Decision: keep the migration in its existing owner path and make only its non-idempotent DDL conditional through `INFORMATION_SCHEMA` + `PREPARE`. Always converge the existing contest definitions, conditionally add missing columns/indexes, conditionally add submission fence columns/index, and conditionally add the scoring-rule foreign key. Keep `CREATE TABLE IF NOT EXISTS` and ownership boundaries unchanged. Because the fresh shared chain stops at this version, use the explicitly requested idempotent refactor rather than a later-only corrective migration; deployment owners must repair any pre-existing checksum history during rollout.
- Alternatives: a later corrective migration cannot repair a fresh run because Flyway stops before reaching it; moving migration locations would hide the shared/App boundary mismatch while leaving the target migration unsafe. Editing the already successful baseline is prohibited.
- Consequences: fresh shared-chain runs and dedicated App runs converge to the same contest contract; the migration gains a small, explicit dynamic-DDL surface that is validated against both baselines. The manual rollback now refuses shared-chain history and remains available only for the isolated App-owner chain.
- Affected tasks: CONTEST-011, NOTIFY-005.

## DEC-011 — Extract Submission lifecycle and Search indexing into two physical services

- Context: the current migration guide intentionally kept `submissions + judge_outbox` in App and kept Search as an App-local MeiliSearch projection. The repository has since exposed the relevant seams: `SubmissionWritePort`/`DefaultSubmissionWritePort`, `judge_outbox`, generation fence, lease reaper, result outbox, and an independent `backend-judge`; Search has four source adapters and a MeiliSearch read projection, but no durable index writer. The new user objective explicitly changes the target and requires real network boundaries.
- Decision: create `backend-submission` as the business-data Owner for `submissions`, `submission_test_details`, `judge_outbox`, and `submission_result_outbox`; keep `backend-judge` as a stateless execution worker that consumes Streams and calls the Submission owner contract. Create `backend-search` as a no-HTTP/no-business-DB indexing worker that consumes `SearchDocumentChanged` events and is the sole MeiliSearch index writer. App/Auth/Problem/Forum/Solution/Contest remain source-data Owners; App keeps the public `/search` read compatibility and DB fallback during cutover.
- Transaction boundary: Submission intake + judge outbox and verdict fence + result outbox remain local strong-consistency transactions inside Submission. Contest association, Notification, Achievement, WebSocket, audit and Search index updates become durable at-least-once events/inboxes with idempotency and reconciliation; no 2PC, cross-service SQL, or synchronous long transaction is introduced.
- Contract decision: reuse the existing provider-owned `backend-app-api` artifact for the first migration seam to avoid copying the large existing Submission DTO/port surface. Add only dependency-free event contract types under `com.ulticode.app.api.event`; an owner-specific API artifact is a later cleanup only if the dependency graph proves it necessary, and is not a second implementation path.
- Alternatives: (1) keep Submission in App and only split Docker/Judge rejected because it preserves the high-load boundary without moving the data Owner; (2) split each SearchSource/content domain rejected because those are shallow shared-data seams; (3) introduce Kafka/RabbitMQ rejected because existing integration outbox + Redis Streams already provides the needed at-least-once transport; (4) Big-bang schema cutover rejected because it cannot safely preserve rollback or reconcile in-flight verdicts.
- Consequences: two deployable units, independent scaling and failure domains, plus DB grant/routing/observability and eventual-consistency work. Existing HTTP Result/auth/cookie behavior is a compatibility invariant; a single explicit local/remote flag is allowed only until the final gate and double writers are forbidden.
- Affected tasks: SPLIT-001, SPLIT-002, SPLIT-003, SPLIT-004, SEARCH-001, SEARCH-002, SEARCH-003, SPLIT-005.

## DEC-012 — Bound judge Stream retries and make dispatch failure-safe

- Context: the compatibility runtime uses Redis Streams PEL for at-least-once judge delivery. A separate dedup `SET NX` followed by `XADD` could leave a marker without a job after a process crash, while an unacknowledged entry could otherwise be reclaimed forever.
- Decision: submit the dedup marker and Stream append through one Redis Lua operation; on XADD failure the script deletes the marker. Reclaimed entries may be processed up to `queue.max-delivery-attempts`; the next stale claim uses a shared-hash-tag, idempotent Lua `XADD + XACK` into `judge:{judge-stream}:dlq`. DLQ failure leaves the source entry retryable.
- Alternatives: keep two Redis calls (rejected: lost-job crash window); infinite PEL retry (rejected: permanent poison/failing RPC loop); re-enqueue a new Stream entry (rejected: duplicates and broken delivery accounting).
- Consequences: judge jobs remain at-least-once, bounded, and observable through the DLQ and `judge.streams.dlq`; storage ownership and Submission outbox cutover remain SPLIT-003 scope.
- Affected tasks: SPLIT-002, SPLIT-004, SPLIT-005.

## DEC-013: SPLIT-003 writer 迁移切片范围（Contest 路径处理）

- **Context**: SPLIT-003 需要 submissions/judge_outbox/submission_result_outbox 唯一 Submission writer。App 的 `SubmissionWriteRoutingPort` 在 remote 模式下已强制 contest 提交走 App local（CR P1-2：ContestServiceImpl 持有 contest 行 FOR UPDATE，远端回环会死锁）。`DefaultSubmissionWritePort.submit` 在本地事务中调用 `ContestSubmissionPort.recordSubmissionIfNeeded`（写 App contest_submissions 表）与 `JudgeEnqueuePort`（写 Redis 队列）。
- **Decision**: writer 迁移切片分两步。本切片（SPLIT-003-slice-2）只迁移**非 contest 普通提交**的存储写路径到 backend-submission：backend-submission 本地实现 `SubmissionWritePort`（直接写 submission schema 的 submissions/judge_outbox/submission_result_outbox），App 侧 `APP_SUBMISSION_ROUTING_MODE=remote` 时普通提交走 Dubbo 到 backend-submission 本地 writer，contest 提交继续由 CR P1-2 守卫走 App local。Contest 路径的 owner 迁移（事件化 ContestSubmissionPort + ContestServiceImpl 命令迁移）属后续切片，不得在本切片将 ContestSubmissionPort 作为第二个 writer 搬进 Submission 库。
- **Alternatives**: 一次迁移全部提交路径（拒绝：需同时重构 Contest 命令与事件化，跨 SPLIT-003/004 边界过大）；backend-submission 继续转发 App writer（拒绝：没有达成 AC1 的本地强一致事务）。
- **Consequences**: AC1/AC3 对普通提交路径满足、对 contest 路径 PARTIAL；SPLIT-003 保持 in_progress，COVERAGE 记录 PARTIAL；backend-submission 需要 mybatis-plus + mysql 数据源 + Submission 实体/mapper/codec/stats/outbox 相关类；`submission_rw` grant 已具备。
- **Affected Tasks**: SPLIT-003（本切片）、SPLIT-004（contest 事件化消费者）。

## DEC-013 修订：slice-2 边界修正为能力建设（不切流）

- **Context（新增事实）**: `JudgeOutboxDispatcher`（App，读 ulticode schema 的 judge_outbox）、`SubmissionResultDispatcher`（App，读 submission_result_outbox）与 `SubmissionJudgedInboxBridge` 等事件消费者都在 App。若 slice-2 把 App 路由切到 backend-submission 本地 writer（写 submission schema），App 侧 dispatcher/消费者读不到新 outbox 行，判题/事件链断裂。
- **修订**: slice-2 只交付 **backend-submission 本地写能力**：数据源（submission schema）+ 实体/mapper/codec/stats/result-outbox writer 复制 + 本地 `SubmissionWritePort` 实现（普通提交路径）+ Testcontainers/集成测试验证三表强一致写入。**不切换** App 路由（`APP_SUBMISSION_ROUTING_MODE` 保持 local 默认），`SubmissionWriteCompatibilityProvider` 保持转发 App。dispatcher/result 消费者迁移、backfill、cutover 属后续切片（slice-3+）。
- **先例**: judge-runtime 复制 `com.ulticode.modules.submission.*` 类保持包名（FeatureFlagsProperties、SubmissionStatusCodec），backend-submission 沿用同模式复制写路径类。
- **Consequences**: AC1/AC3 对"backend-submission 具备本地强一致写路径"满足、对"唯一 writer 生效"仍 PARTIAL；Task 保持 in_progress。

## DEC-014: slice-3 消费者迁移的事件发布路径（backend-submission 直接 XADD stream:integration）

- **Context**: slice-3 需把 `JudgeOutboxDispatcher`（App，读 App 库 judge_outbox → JudgeQueue enqueue）与 `SubmissionResultDispatcher`（App，读 App 库 submission_result_outbox → `IntegrationEventPublisher` 写 App 库 integration_outbox 表 → `IntegrationOutboxDispatcher` XADD `stream:integration`）迁移到 backend-submission。但 backend-submission 不得写 App 库 integration_outbox 表（DEC-011：跨服务 SQL 禁止；App 库表非 Submission owner）。
- **Decision**: backend-submission 复制 `SubmissionResultDispatcher` 时**不引入 integration outbox 表**，dispatcher 直接 XADD `stream:integration`，字段格式与 App `IntegrationOutboxDispatcher.publishToStream` 完全一致（eventId/owner/aggregateId/aggregateVersion/eventType/schemaVersion/causationId/traceId/payload-JSON）。result outbox 行自身即 durable 通道（CLAIMED→DELIVERED 状态机、lease、重试），无需第二层 outbox。`JudgeOutboxDispatcher` 复制时**去掉 legacy shadow/legacy enqueue 路径**（backend-submission 无 legacy RQueue 概念），只保留 M3c-2 real dispatch（`JudgeQueue.enqueue` + markSent/markRetry）。Redis 依赖：backend-submission 增加 `redisson-spring-boot-starter` + `spring-boot-starter-data-redis`，配置指向共享 Redis（judge stream + integration stream）。
- **Alternatives**: (1) 复制 integration_outbox 表到 submission schema + 复制 IntegrationOutboxDispatcher（拒绝：双重 outbox、重复基础设施、owner 边界仍混）；(2) backend-submission 依赖 judge-runtime 的 JudgeQueue port（拒绝：backend-submission 只依赖 app-api/auth-api/common，引入 judge-runtime 会拖入 sandbox 等执行依赖，且 judge-runtime 已依赖 app-api 而非共享模块；沿用"复制而非共享"先例）。
- **Consequences**: App `SubmissionJudgedInboxBridge` 继续消费 `stream:integration`，事件格式不变（owner 字段仍为 "App" 以兼容现有 consumer 绑定——bridge 只按 eventType 分派，不校验 owner）；cutover 后 backend-submission 的 result dispatcher 是唯一事件发布者，App 的 IntegrationOutboxDispatcher 不再有 Submission 事件（旧行 drain 后停止）；judge stream 的 dedup 语义（SETNX+XADD 原子脚本）随 adapter 复制保持。
- **Affected Tasks**: SPLIT-003（slice-3）、SPLIT-004（消费者切换）。

## DEC-015: slice-4 cutover 能力建设（backfill runbook + provider 本地化机制，不切流）

- **Context**: slice-3 完成 outbox 消费者迁移后，SPLIT-003 剩余工作是 backfill/verify/cutover + grant revocation。但 App 读路径（`SubmissionReadAdapter`/`DefaultSubmissionUserReadAdapter` 等）仍直读 App schema 的 submissions 表，若先切 `APP_SUBMISSION_ROUTING_MODE=remote` + `app.submission.owner.mode=local`，新提交写入 submission schema 后 App 读列表/详情不可见，用户功能断裂。
- **Decision**: slice-4 只交付 cutover 能力（与 DEC-013 修订的"能力建设不切流"模式一致）：① `scripts/dev/submission-schema-cutover.sh`（preflight 列形状/空目标核对 → cutover 复制三表+撤销 App 表级 grant → rollback 回写+恢复 grant，需 `--execute` + `SUBMISSION_CUTOVER_CONFIRM` token，仿 notification 先例）；② `app.submission.owner.mode=compat|local`：默认 compat 保持 provider 转发 App（单一 writer），local 时 provider 委托进程内 `DefaultSubmissionWritePort`/`DefaultSubmissionFencePort` 直写 submission schema（并补齐本地 `DefaultSubmissionFencePort` + mapper acquireLease/renewLease）。实际切流（remote+local 同时启用）的 gate 在 SPLIT-004 读路径迁移完成后的观察窗口。
- **Alternatives**: 现在直接切流（拒绝：App 读路径未迁，生产用户提交不可见）；backfill 双向持续同步（拒绝：双写，违反唯一 writer）。
- **Consequences**: AC1/AC3 对普通提交路径能力齐备、仍 PARTIAL（唯一 writer 未生效）；SPLIT-003 保持 in_progress；`submission-schema-cutover.sh` 与 `app.submission.owner.mode=local` 已具备、未启用；指南 4.5.1 同步。
- **Affected Tasks**: SPLIT-003（本切片）、SPLIT-004（读路径迁移后解锁切流 gate）。

## DEC-016: Search 索引文档版本语义与 worker 版本账本（SEARCH-003-slice-1）

- **Context**: SEARCH-002 worker 已就绪但 envelope aggregateVersion 恒为 0L（`SearchDocumentChangedPublisher.publish` 传 0L），Meili 文档不含版本；backfill 与 live 事件并发时无法判断谁更新（AC：不覆盖新版本）。problems/solutions 有 `updated_at`（problems 带 ON UPDATE，solutions 无），forum_posts/users 无任何更新时间列；user 文档还依赖 App-owned `user_profiles.updated_at`（ON UPDATE 自动）。发布方在写事务内持有实体，DB 自动更新的列不会回填到内存实体。
- **Decision**: 版本 = 事件发布时刻（live）或行最后变更时刻（backfill）的 epoch 毫秒，同一墙钟域可比较：① live publisher（App/Auth 两处）把 envelope aggregateVersion 从 0L 改为与 OCCURRED_AT 同源的 `LocalDateTime.now(clock)` epoch 毫秒；② backfill（slice-2）版本 = 行 `updated_at`（users 取 GREATEST(users.updated_at, user_profiles.updated_at, deleted_at, joined_at)）epoch 毫秒；③ worker 为每索引维护 Redis 版本账本 hash（`search:doc-version:{index}`，field=docId），UPSERT 前 HGET 比较：existing > incoming 则跳过写（计数 `search.worker.stale_skipped`）并照常 ACK；否则写 Meili（文档附加 `_aggregateVersion` 字段，供 diff watermark/可观测）成功后 HSET；DELETE 时 deleteDocument + HDEL。skip 规则只跳过 strictly-older（等版本允许重写：同版本内容一致或同秒竞态自然收敛）。
- **Alternatives**: 用 Meili getDocument 读回比较（拒绝：addDocuments 是异步任务，任务队列滞后使读回看到旧状态，stale 事件可覆盖新版本）；给四表加 version 计数器列并由全部 writer 维护（拒绝：8+ 写路径，迁移与维护成本高）；仅靠事件顺序无保护（拒绝：违反 AC1）。
- **Consequences**: 账本是 worker 进程内写入（HSET 仅在 Meili 接受写后），单副本 worker 下顺序一致；多副本并发 HGET/HSET 非原子，文档标注 worker 按单副本运行，账本为 best-effort 排序辅助而非分布式锁；外部重建 Meili 索引后需按 runbook 清对应账本 key 再 backfill。事件契约字段不变（envelope 已有 aggregateVersion）。
- **修订（SEARCH-003-slice-2）**: DELETE 不再清空账本，改记 tombstone（存负版本 `-V`）：后续 UPSERT 仅当版本严格大于 tombstone 版本才写，否则跳过——backfill 快照与 unpublish 事件乱序时不会复活已删文档（equal-or-older 一律 skip；re-create 新版本正常写）。
- **Affected Tasks**: SEARCH-003-slice-1、SEARCH-003-slice-2、SEARCH-003-slice-4。

## DEC-017: backfill 协议——App 枚举 + diff 收敛，worker 保持唯一写者（SEARCH-003-slice-2）

- **Context**: 四索引当前为空（worker 未启用、App 无 Meili 写路径、`meilisearch.enabled` 默认 false）；SEARCH-003 需要把 owner 库现状灌入索引且后续可重放/重建。DEC-011 禁止 worker 读业务库、禁止 App 写 Meili（worker 唯一写者）。索引没有 RESET 操作可用（SUPPORTED_OPERATIONS 冻结为 UPSERT/DELETE，不扩契约）。
- **Decision**: backfill 由 App 侧 `SearchBackfillRunner`（属性门控 `app.search.backfill.enabled` + index 选择，默认关）执行，所有变更经 IntegrationEventPublisher → stream → worker，保持唯一写者：① watermark W=now()（epoch 毫秒）；② 按源枚举全量快照（谓词与 Q-read 适配器一致：problem is_published=1 AND is_deleted=0、post is_deleted=0、solution is_published=1 AND is_deleted=0、user users LEFT JOIN user_profiles is_deleted=0），文档 shape 与 live publisher 完全一致（复用文档构建）；③ 读 Meili 现有文档 id+`_aggregateVersion`；④ 对快照发布 UPSERT（aggregateVersion=行版本）；⑤ 对 `_aggregateVersion < W` 且不在快照的 id 发布 DELETE（≥W 的跳过：backfill 期间新建/更新的文档由 live 事件或快照自身负责）；⑥ 每索引输出计数日志。快照 vs 并发 live 的竞态由 DEC-016 账本守卫（snapshot 版本 ≤ 已索引版本时被跳过）。重跑幂等收敛；Meili 不可达时 preflight 失败不半跑。
- **Alternatives**: 扩契约加 RESET_INDEX 操作（拒绝：动冻结契约，且 reset+全量重灌期间读路径空洞更大）；backfill 直写 Meili（拒绝：违反 DEC-011 唯一写者）；双写兼容层（拒绝：第二套并行写路径）。
- **Consequences**: 首次 backfill 与索引重建/丢失重放共用同一命令；diff 删除依赖文档 `_aggregateVersion`（slice-1 写入）；枚举需要 forum_posts/users 新增 `updated_at` 列（共享链 additive migration）与四源枚举 seam（SearchSource 扩展，additive）。真实 Meili 不可用，验证用 mock/stub。
- **Affected Tasks**: SEARCH-003-slice-1、SEARCH-003-slice-2、SEARCH-003-slice-4。

## DEC-018: Contest 提交使用显式命令，关联由 SubmissionCreated inbox 幂等写回 App

- **Context**: `ContestServiceImpl` 已完成资格检查后调用通用 `SubmissionWritePort.submit`；remote 模式因此只能用 CR P1-2 守卫把 contest 写留在 App。若直接放开通用 DTO，客户端可伪造 `contestId` 进入 Submission owner，且 `contest_submissions` 关联仍会被第二个服务同步写入。Contest association 必须继续由 App 拥有。
- **Decision**: 在 `SubmissionWritePort` 增加显式 `submitContest` 命令。普通 `submit` 在 App 与 Submission owner 边界拒绝带 `contestId` 的上下文；ContestServiceImpl 资格检查后调用显式命令。backend-submission 在同一本地事务写入 submission、judge outbox 和 `SubmissionCreated` durable outbox；App-Contest inbox 按 submission/generation 幂等创建 `contest_submissions`。延迟 `SubmissionJudged` 若尚无关联必须重试，不能静默 no-op；关联消费不重新以当前时间重判已完成的 admission deadline。
- **Alternatives**: 保留 App local contest writer（拒绝：grant revocation 永远 PARTIAL）；把 contest_submissions 搬入 Submission schema（拒绝：违反 Contest owner 边界）；用同步 RPC/跨服务 SQL 写关联（拒绝：长事务/2PC 与 DEC-011 冲突）；让 judged consumer 丢弃 missing mapping（拒绝：Created/Judged 乱序会丢分）。
- **Consequences**: remote+local 观察窗可以让 backend-submission 成为 submissions/judge/result/created 四张 Submission 表的唯一 writer；contest association、ranking 与 scoring 仍由 App inbox/consumer 拥有。事件使用既有 Redis Stream 与 Inbox 设施，不新增 broker；local rollback 继续同步调用 ContestSubmissionPort。
- **Affected Tasks**: SPLIT-003-slice-6、SPLIT-003-slice-7、SPLIT-004、SPLIT-005。

## DEC-019: Contract ownership follows service boundaries; Submission utilities stay on the Submission contract seam

- **Context**: `backend-app-api` currently contains Submission, Notification, Contest, Problem, Forum, Solution and Moderation contracts. The verified graph/source inventory shows `SubmissionWritePort` and `NotificationAdministrationService` in `app-api`, five service/runtime consumers of the artifact, two `SubmissionWritePort` implementations, a default Submission compat provider that forwards `backend-submission` to `backend-app`, and byte-identical `SubmissionStatusCatalog`/`TestCaseDetailCodec` copies in App and Submission. Existing `auth-api` and `admin-api` already demonstrate provider-owned contract modules. DEC-011 forbids shared business Entity/Mapper and cross-owner SQL.
- **Decision**: create `backend-submission-api` under `com.ulticode.submission.api` and `backend-notification-api` under `com.ulticode.notification.api`. Move every type proven by the owner matrix to the service that owns its provider/behavior; keep App-provided Problem/user/recipient fact seams in `backend-app-api` with explicit exceptions. Move the entity-free `SubmissionTestCaseDetailDTO`, a DTO-based `TestCaseDetailCodec`, and `SubmissionStatusCatalog` to the Submission contract seam so App rollback/projection code consumes one implementation without sharing Submission Entity/Mapper. After the existing SPLIT-004 cutover gate and explicit release authority, register only the local Submission provider in `backend-submission`, remove the App provider and compat forwarder, and keep Judge/App/Admin on one direct `backend-submission` group.
- **Alternatives**: keep all contracts in app-api (rejected: ownership drift and future self-reinforcing coupling); add new artifacts while retaining old package aliases/re-exports (rejected: second contract path with no exit condition and hidden dependency leakage); put codec/catalog/entity/mapper in `backend-common` (rejected: widens shared implementation surface and violates DEC-011 locality); delete compat before cutover authority (rejected: unsafe writer/grant transition).
- **Consequences**: package/FQCN changes require a matched provider/consumer release; no mixed-version rollout is allowed. App-api becomes smaller and service consumers learn only their owner contract. Submission keeps one pure DTO utility seam while each owner retains private entity mapping. Runtime cutover and provider deletion remain separately gated by release authority, checksum/grant evidence and rollback runbook.
- **Affected Tasks**: CONTRACT-001, CONTRACT-002, CONTRACT-003, CONTRACT-004, CONTRACT-005, CONTRACT-006, CONTRACT-007, CONTRACT-008.

## DEC-020: CONTRACT-001 owner matrix resolves app-api residual seams

- **Context**: The complete `backend-app-api` inventory contains App business contracts mixed with Submission,
  Notification, Auth-backed WebSocket seams, generic command metadata, Judge-runtime execution DTOs, and a small
  generic difficulty/count value shape. Empty `CALLS` traces on interfaces are not evidence of no consumers; field/type
  usage, implementations, providers and direct source were used instead.
- **Decision**: Keep App Problem/user/recipient facts and App-local Contest/WebSocket collaboration in `backend-app-api`.
  Move Submission contracts to `backend-submission-api` and Notification contracts to `backend-notification-api` under
  the namespaces and matched release rules in `.auto-flow/CONTRACT-001-OWNER-MATRIX.md`. Put the credential-free local
  WebSocket security seam and generic command metadata in `backend-common`; keep Auth as the authority for the facts.
  Keep queue/sandbox/execution contracts private to Judge-runtime, and classify `ProblemSubmissionStatsPort` as
  Submission-owned because its provider reads Submission storage. Classify the unused `SubmissionNotificationPort` as a
  Notification-owned legacy/dead seam for bounded cleanup, never as an App contract.
- **Alternatives**: Leave all types in app-api (rejected: owner drift); put all cross-service DTOs in common (rejected:
  leaks business ownership); treat empty interface call traces as no-consumer evidence (rejected: graph edge type is
  insufficient); keep old FQCN aliases (rejected by DEC-019 matched release/no-alias rule).
- **Consequences**: CONTRACT-002/003 have a complete migration list; App fact exceptions remain an explicit, bounded
  dependency; common receives only implementation-free metadata/value shapes; runtime cutover remains separately gated.
- **Affected Tasks**: CONTRACT-001, CONTRACT-001-COMMON, CONTRACT-002, CONTRACT-003, CONTRACT-004, CONTRACT-005, CONTRACT-006.

## DEC-021: Common extraction is a bounded prerequisite for owner API convergence

- **Context**: CONTRACT-001 found eight implementation-free types in `backend-app-api` that are consumed by more than
  one owner: command metadata (`ActorDelegation`, `WriteCommand`), generic `DifficultyCountDTO`, credential-free
  account/JWT projections and ports, and `DelegationAssertionContract`. Leaving them in app-api would contradict the
  final App-only contract gate; putting business DTOs or persistence types in common would violate DEC-011.
- **Decision**: Add `CONTRACT-001-COMMON` before Submission/Notification API creation. Move only those eight types into
  `backend-common` with stable concern packages (`common.command`, `common.dto`, `common.auth`, `common.security`),
  migrate their App/Admin/Notification/WebSocket consumers and tests, and retain Auth API's provider-owned command
  copies where its own public identity requires them. The common module remains Java-only and implementation-free.
- **Alternatives**: leave the seams in app-api (rejected: final owner gate cannot close); put all App/Submission/Notification
  DTOs in common (rejected: leaks ownership); rewrite Auth API's provider contract in the same task (rejected: expands
  the boundary beyond the app-api objective).
- **Consequences**: API artifact tasks consume one canonical common shape; FQCN migration still requires matched release,
  but no new runtime route, database, JWT key, cookie, broker or provider behavior is introduced.
- **Affected Tasks**: CONTRACT-001-COMMON, CONTRACT-002, CONTRACT-003, CONTRACT-004, CONTRACT-005, CONTRACT-008.

## DEC-022: Source-boundary completion does not authorize runtime Submission cutover

- **Context**: CONTRACT-002 through CONTRACT-006 now provide the owner-specific API artifacts, migrated callers,
  provider/reference contracts, and the canonical DTO codec/catalog. The App rejudge/administration providers still
  depend on App-owned entities, mappers, Contest/Judge adapters and outbox behavior; Submission write/fence
  compatibility providers and the current Admin backend-app route preserve rollback behavior.
- **Decision**: Close CONTRACT-004 and CONTRACT-005 for source/POM/provider-reference convergence and close
  CONTRACT-006 for the canonical utility seam. Do not change runtime defaults, provider registration, grants or
  compatibility forwarding here. Direct Submission provider handoff, compat deletion and single-hop/single-writer
  verification remain CONTRACT-007 and require explicit release/cutover authority plus SPLIT-005 evidence.
- **Consequences**: The API boundary can be consumed at the next matched release, while the current runtime remains
  reversible. Coverage row 76 stays PARTIAL; CONTRACT-008 cannot close until CONTRACT-007 is authorized and verified.
- **Affected Tasks**: CONTRACT-004, CONTRACT-005, CONTRACT-006, CONTRACT-007, CONTRACT-008.

## DEC-023: Production cutover requires explicit owner credentials and table-scoped App grants

- **Status**: Historical external-host scenario; superseded for this open local project by DEC-025.

- **Context**: The release owner explicitly authorized the production cutover on 2026-08-18. The available
  persistent MySQL target has an empty, successfully migrated `submission` schema, but its active `ulticode` user
  owns schema-wide `ALL` privileges and has no table-scoped grant posture. No App or Submission runtime is active on
  this host, so revoking only named table grants cannot prove single-writer behavior.
- **Decision**: Keep the cutover runbook fail-closed until the real App runtime account is identified and has only
  table-scoped grants for the source Submission tables. Production Compose must require explicit
  `SUBMISSION_DB_*` and Redis owner credentials rather than falling back to App `DB_*` or container `localhost`.
  Do not revoke schema-wide privileges or cut over an inactive/non-production account.
- **Alternatives**: revoke named tables from `ulticode` (rejected: schema-wide `ALL` still permits writes); use
  `app_rw` without changing the active App runtime (rejected: it would revoke grants from the wrong account); proceed
  with route-only remote/local (rejected: data ownership and grant gate would remain unproven).
- **Consequences**: owner schema expansion is complete and source data is unchanged; actual copy/REVOKE/route switch
  waits for the deployment host's App account and running App/Submission observation window.
- **Affected Tasks**: SPLIT-005-retirement-authority, CONTRACT-007, CONTRACT-008.

## DEC-024: Split authority approval from runtime access and cutover observation

- **Status**: Historical external-host scenario; superseded for this open local project by DEC-025.

- **Context**: The release owner explicitly approved production route/grant retirement, and the authority/preflight contract is now recorded. The remaining failure is operational: the available host has no App/Submission runtime, Nacos is stopped, and its active `ulticode` account has schema-wide DML, so it cannot safely prove or execute a single-writer cutover.
- **Decision**: Close only `SPLIT-005-retirement-authority`. Add `SPLIT-005-runtime-access` for deployment-host, account, owner-credential and runtime prerequisite evidence; keep it `blocked` until those external inputs exist. Add `SPLIT-005-runtime-cutover-observation` as the sole route/copy/REVOKE/single-writer execution task, then let CONTRACT-007 and CONTRACT-008 consume its evidence.
- **Alternatives**: use the current `ulticode` account (rejected because schema-wide DML defeats table-only REVOKE); provision a local substitute account/runtime (rejected because it is not the authorized deployment target); execute route-only (rejected because data ownership and single-writer evidence would remain false).
- **Consequences**: No additional business code, migration, grant, route or deployment change is required locally. The next action is an external deployment-host inventory; secrets remain outside `.auto-flow`.
- **Affected Tasks**: SPLIT-005, SPLIT-005-runtime-access, SPLIT-005-runtime-cutover-observation, CONTRACT-007, CONTRACT-008.

## DEC-025: This open project uses the local runtime as the cutover target

- **Context**: The user clarified on 2026-08-18 that UltiCode is an open local project with no production environment. The prior external deployment-host blocker therefore does not apply to the requested work.
- **Decision**: Treat the local Docker/PM2 stack, local MySQL accounts and local Nacos/Redis as the authoritative runtime target. Keep credentials ephemeral and out of `.auto-flow`; do not infer or claim any production deployment.
- **Evidence**: Local preflight, confirmation-gated Submission schema cutover, App source-table grant revocation, source/target row/checksum parity, six PM2 services with zero restarts, and public Auth/Admin/App/Notification health checks all pass.
- **Consequences**: `SPLIT-005-runtime-access` and `SPLIT-005-runtime-cutover-observation` are done locally. `CONTRACT-007` remains open only for physical compatibility-provider retirement, and `CONTRACT-008` remains the final source/review audit.
- **Affected Tasks**: SPLIT-005-runtime-access, SPLIT-005-runtime-cutover-observation, CONTRACT-007, CONTRACT-008.

## DEC-026: Local Submission cutover retires the compatibility registration

- **Context**: The local target has completed the confirmation-gated schema cutover, App grant revocation and remote-route observation. `backend-submission` still exposed a provider that selected either an App Dubbo reference or a local writer through `app.submission.owner.mode`, while App still exposed the duplicate `backend-app` write/fence provider.
- **Decision**: Make `backend-submission`'s write/fence providers direct delegates to its local writer/fence, delete the App duplicate providers and remove the owner-mode configuration/conditional gates. Keep App's local adapters only as the explicit route rollback path; do not add an alias or a second compatibility registration.
- **Alternatives**: Keep the compat provider disabled by property (rejected: the retired two-hop registration and owner-mode branch would remain in the source); delete the local writer (rejected: it is the Submission owner and required by the direct provider); modify schema/migrations (rejected: no schema change is needed).
- **Consequences**: The regular remote path has one `backend-submission` provider and one Submission writer; rollback requires the prior verified artifact plus the existing route/grant/watermark/reconciliation runbook. No applied migration or remote state changes.
- **Affected Tasks**: CONTRACT-007, CONTRACT-008.

## DEC-027: Direct Submission startup requires explicit cutover evidence

- **Context**: `init-env.sh` can provision an isolated owner credential, but it cannot safely infer that existing App submission rows, grants, outboxes and rollback watermark have been copied and observed. Automatically setting remote on an existing local volume would silently hide App-owned history.
- **Decision**: Generate `APP_SUBMISSION_ROUTING_MODE=remote` with `SUBMISSION_CUTOVER_COMPLETE=false`; `up.sh --prepare-submission-owner` runs owner-first Flyway migration and unlocks `submission_rw` without starting PM2. The normal `up.sh` path and App `SubmissionRoutingProperties` fail closed until the operator completes the confirmation-gated cutover/grant observation and sets the marker true.
- **Evidence**: Disposable owner-first preparation passed schema-history/table/account checks; preparation flag rejects `--quick`/`--frontend-only`; exact cutover failure-injection tests preserve source/grants and report partial cleanup/escalation.
- **Consequences**: Local startup is intentionally two-phase; no automatic copy/REVOKE or silent data loss. Rollback of the direct artifact still requires the previous verified compatibility artifact.
- **Affected Tasks**: CONTRACT-007, CONTRACT-008.

## DEC-028: Submission schema cutover requires all-writer quiescence

- **Context**: Copying or rolling back `submissions`, `judge_outbox` and `submission_result_outbox` while App, Submission-owner, Judge, dispatcher, reaper, scheduler or direct database writers are active can produce a mixed source/target state; a later table copy failure must not leave source tables partially restored.
- **Decision**: `cutover` and `rollback` require the one-time `SUBMISSION_CUTOVER_QUIESCE_CONFIRM=I_UNDERSTAND_SUBMISSION_QUIESCE_ALL_WRITERS` assertion after every source/target writer is stopped and drained. Cutover compares source rows/checksums before and after copy before REVOKE; rollback `copy_back` replaces all tables in one MySQL transaction and emits CRITICAL reconciliation guidance on failure.
- **Consequences**: The runbook remains confirmation-gated and operator-dependent; no new production service protocol is introduced. A failed transaction must keep all writers stopped until source/grant reconciliation is complete.
- **Affected Tasks**: CONTRACT-007, CONTRACT-008.

## DEC-034: Blocker remediation is phase-gated with local preparation first

- **Context**: The recovery packet identified two real blockers. Auth `1044` is caused by a runtime account correctly lacking owner DDL/GRANT privileges, while ARCH-003 needs remote stability/quiesce/observation evidence that cannot exist in this local-only project. The user requests all ten blocker sub-items but also protects the worktree and forbids unapproved business, migration, runtime or remote changes.
- **Decision**: Use one sequential DAG: first make the existing `scripts/dev/migrate.sh` an explicit, fail-closed privileged migration job with `MIGRATION_DB_*`, owner-schema allowlist and privilege preflight; then require external DB/cutover/users evidence; then require remote monitoring/quiesce/observation/rollback/deployment evidence; only then test and retire compatibility paths. Keep `ARCH-002`/`ARCH-003` blocked until their external Acceptance and Validation evidence exists, and use `ARCH-007` as the new final gate.
- **Alternatives**: Grant DBA privileges to the runtime `ulticode` account (rejected: violates least privilege and hides the 1044 boundary); treat disposable MySQL as physical isolation (rejected: synthetic evidence cannot prove target ownership); edit applied migrations (rejected: irreversible history drift); simulate production monitoring or deployment locally (rejected: would create a false production claim); add a second migration/cutover framework (rejected: existing `migrate.sh` and cutover helper are the authoritative seams).
- **Consequences**: `ARCH-002-001` is the only local `ready` Task. Every subsequent task has explicit external prerequisites, owner, evidence and rollback. No parent blocker can be marked `done` from local tests alone. `.auto-flow/RESUME.md` must be reduced to the current Services Owner objective and explicitly mark the old CONTRACT pointer historical.
- **Affected Tasks**: `ARCH-002`, `ARCH-003`, `ARCH-002-001` through `ARCH-002-005`, `ARCH-003-001` through `ARCH-003-005`, `ARCH-007`.

## DEC-035: Monitoring and cutover controls precede physical isolation

- **Context**: The first blocker plan made `ARCH-003-001` depend on `ARCH-002-005`, while `ARCH-002-005` required route/health/latency observation. That order would require the physical cutover to execute before its monitoring evidence existed.
- **Decision**: Split the evidence by timing. `ARCH-003-001` establishes monitoring/baseline before cutover; `ARCH-003-004` deploys production monitoring and cutover controls before the switch; `ARCH-003-002` confirms quiescence; `ARCH-002-005` performs the physical cutover; `ARCH-003-003` owns post-cutover observation and rollback rehearsal; `ARCH-003-005` owns compatibility retirement verification.
- **Alternatives**: Let cutover create monitoring after the fact (rejected: blind window); make monitoring a post-cutover task (rejected: circular dependency); combine all tasks in one opaque runbook (rejected: cannot review or prove preconditions independently).
- **Consequences**: The DAG is safe to execute: no physical cutover can start without monitoring readiness, deployed controls and writer quiescence, and no compatibility deletion can start without post-cutover observation/rollback evidence.
- **Affected Tasks**: `ARCH-002-005`, `ARCH-003-001`, `ARCH-003-002`, `ARCH-003-003`, `ARCH-003-004`, `ARCH-003-005`, `ARCH-007`.

## DEC-036: Privileged migration principals use direct grants only

- **Context**: A role-based migration principal can show role assignments without proving that the same role is active/default on the separate Flyway JDBC session. Expanding `mysql.role_edges` would add another privilege and session-activation contract to the migration job.
- **Decision**: `scripts/dev/migrate.sh` rejects role grants during owner preflight. The privileged migration principal must use direct, schema-scoped DDL/SELECT/GRANT privileges; global `ALL PRIVILEGES` remains accepted as a capability superset but is still subject to the external least-privilege gate.
- **Alternatives**: Recursively expand `mysql.role_edges` and activate roles in the Flyway session (rejected for this slice: separate-session/default-role ambiguity and extra system-table privilege); silently accept roles (rejected: preflight/Flyway effective privileges could differ).
- **Consequences**: Direct accounts have a smaller, deterministic contract and do not need `mysql.role_edges` access. Role-based migration requires a separate scoped task/ADR and cannot pass this job’s preflight.
- **Affected Tasks**: `ARCH-002-001`, `ARCH-002-002`, `ARCH-002-004`.

## DEC-037: Fail-closed observation and fault rehearsal in development environment

- **Context**: `DEV-LOCAL-006` required observation timeline, fault-injection test battery, data reconciliation, and rollback runbook verification in the authorized development environment without claiming production stability or mutating external resources.
- **Decision**: Implement `scripts/dev/dev-local-observation-rehearsal.sh` with a mandatory `DEV_LOCAL_OBSERVATION_CONFIRM` guard, fail closed on broken DB/Redis queries (no fallback hiding errors), export Redis environment variables to execute real-broker `JudgeStreamRedisIntegrationTest` with 0 skips, assert exact table row/checksum parity between `ulticode` and `submission` schemas, label local writer states accurately as `UNAVAILABLE (processes not running locally)`, and report `PARTIAL` status when `--quick` skips tests.
- **Alternatives**: Silently treat skipped tests as PASS (rejected: hides unexercised integration coverage); render query failures as `N/A` (rejected: masks connection/privilege issues); simulate production observation (rejected: would fabricate external stability claims).
- **Consequences**: Local fault resilience and reconciliation are strictly verified and reproducible with zero test skips, while external `ARCH-003-001..005` production acceptance remains explicitly blocked.
- **Affected Tasks**: `DEV-LOCAL-006`, `DEV-LOCAL-007`, `DEV-LOCAL-008`, `ARCH-003-003`.

## DEC-038: Compatibility path retention and retirement readiness evaluation

- **Context**: `DEV-LOCAL-007` requires static compatibility scan, single-writer invariant verification, and retirement decision candidate creation without prematurely deleting rollback-needed compatibility paths in the development environment.
- **Decision**: Retain App-local compatibility implementations (`DefaultSubmissionWritePort`, `JudgeOutboxDispatcher`, `JudgingLeaseReaper`, `SubmissionResultDispatcher`, `SubmissionResultOutboxListener`, `LocalSubmissionUserQueryAdapter` in `services/app/app-web`) as a dedicated rollback-only whitelist. Do not physically delete these classes during local development remediation; deletion requires actual production cutover and observation sign-off under `ARCH-003-005`. Single-writer invariants are enforced via `app.submission.routing.mode`: `local` routes writes strictly to App `ulticode` schema; `remote` routes writes strictly to `backend-submission` owner schema with `SubmissionFactsSnapshot`.
- **Alternatives**: Delete App-local submission writer immediately (rejected: eliminates rollback capability if production cutover fails); leave routing uncontrolled (rejected: risks dual-writing).
- **Consequences**: Rollback safety is preserved across all environments; compatibility retirement is documented and verified as ready, while external `ARCH-003-005` production retirement gate remains explicitly blocked.
- **Affected Tasks**: `DEV-LOCAL-007`, `DEV-LOCAL-008`, `ARCH-003-005`.

## DEC-039: Current test environment is the authoritative cutover target

- **Context**: The previous blocker plan separated authorized `DEV-LOCAL` rehearsal from an unavailable production target. The user now confirms that this project has only the current test environment, no separate development or production environment, and grants full authority for all remaining blocker operations there.
- **Decision**: Treat the current `.env`/Docker/PM2 environment as the sole `TEST-TARGET`. Existing `dev-local` script names and confirmation tokens remain unchanged for compatibility, but their rerun evidence may close `ARCH-002-002..005` and `ARCH-003-001..005` when each original least-privilege, parity, quiesce, cutover, observation, rollback and compatibility-retirement criterion passes. No production claim is made or required.
- **Alternatives**: Keep waiting for a nonexistent production environment (rejected: creates an impossible terminal condition); rename every script/token before execution (rejected: unrelated churn); waive runtime evidence (rejected: authority does not replace Acceptance or safety proof).
- **Consequences**: `ARCH-002-002` becomes executable immediately. Downstream Tasks advance only through the existing DAG and fresh TEST-TARGET evidence. Persistent test data may be changed only after backup, watermarks and all-writer quiesce; secrets remain unprinted and uncommitted.
- **Affected Tasks**: `ARCH-002`, `ARCH-003`, `ARCH-002-002..005`, `ARCH-003-001..005`, `ARCH-007`.

## DEC-040: Audit append-only grant is an explicit owner-contract exception

- **Context**: Fresh TEST-TARGET grant inspection found `auth_rw` and `app_rw` each have table-scoped `INSERT` on `admin.audit_outbox`. The initial ARCH wording rejected every cross-owner DML, but applied migration `V20260729140000`, `PerOwnerSchemaIsolationIT`, `AuthAuditOutboxMapper` and `AppAuditOutboxMapper` deliberately define this append-only audit seam.
- **Decision**: Preserve this single registered exception. It is not general foreign-schema access: only `INSERT` is allowed, Admin remains the sole claimant/processor, and SELECT/UPDATE/DELETE must fail. All other cross-owner runtime grants remain prohibited.
- **Alternatives**: Revoke the grants now (rejected: breaks transaction-bound audit writes and contradicts the applied migration); create a new remote audit path (rejected: changes transaction semantics and adds a second architecture); ignore the grant (rejected: would falsify least-privilege evidence).
- **Consequences**: ARCH-002-002/005 privilege checks use an explicit allowlist rather than a blanket no-cross-schema rule. Existing negative tests and fresh target probes must prove the seam remains append-only.
- **Affected Tasks**: `ARCH-002-002`, `ARCH-002-005`, `ARCH-007`.

## DEC-041 — Services autonomy convergence remains local-only

- Decision: record TASK-001 through TASK-005 as locally validated; retain TASK-006 as blocked because four Judge integration tests were skipped and no disposable Redis/MeiliSearch convergence ledger exists.
- Decision: retain TASK-007/TASK-008 blocked until external ARCH-002/ARCH-003 authority, quiesce, observation, rollback and retirement evidence arrives.
- Consequence: no production grant revoke, cutover, deployment or legacy-path deletion is authorized by this packet.
