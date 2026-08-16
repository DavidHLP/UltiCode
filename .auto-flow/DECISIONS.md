# Decisions

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
