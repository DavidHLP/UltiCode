# ADR-0014：架构复审第三轮收敛（C1-C11）

- 状态：`Accepted — implemented`
- 日期：2026-09-16
- 输入：2026-09-16 架构复审报告（round 3，scope: `services/` · `apps/` · `packages/`）
- 关联决策：[`ADR-0013`](0013-architecture-review-module-convergence.md)、[`ADR-0011`](0011-topology-contract-module-convergence.md)
- 默认拓扑：`distributed`

## Context

第三轮架构复审确认 11 张收敛候选卡片（C1-C11）：收据执行器协议、cutover 双份写入路径、
audit outbox 失败契约、notification ledger int 协议、audit emission 双实现、RPC 模块构造、
Redis 值序列化、rejudge wire 映射、Management 远程表查询状态机、Console 历史赛事分页、
http-client 去重状态。全部为 in-process / ports & adapters 收敛，不新增进程、数据库或消息
基础设施。

## Decisions

1. `platform/common/command` 提供 owner-neutral 的 plain receipt executor 与 store port，并显式区分 `MUTATE_THEN_RECORD` 和 `CLAIM_MUTATE_FINALIZE`。
   Auth 使用前者，保留 legacy fingerprint hook 与 null-mapper bypass；App、Submission、Notification 使用后者。各 Owner 的 adapter、mapper、table、fingerprint 和 error namespace 不变，Contest/Profile direct-write paths 继续作为后续项。
2. 删除 `NotificationCutoverService`，由 `AdminNotificationServiceImpl` 保留单一通知写路径和 surviving entry points 上的 `@Audited`。
   `OwnerCutoverGate`/`OwnerCutoverRegistry` 明确五个 domain：contest `FAIL_CLOSED`、moderation `DELEGATE_LOCAL`、notification `CUTOVER_REMOVED`、submission/problem `ALWAYS_REMOTE`；同时删除 dead submission key 并更新 source-contract rules。
3. Admin audit outbox 的失败语义采用完整 retry contract：记录 `attempts`、`last_error`、`next_retry_at`，使用 30 秒 backoff，在 `MAX_ATTEMPTS=5` 进入 terminal；retryable 与 terminal 的 `FAILED` 可区分。
   通过 additive migration `V20260916120000__Add_Audit_Outbox_Retry_Fields.sql` 落地，shared `OutboxDispatcher` contract 不变。
4. Notification delivery-attempt module 由 `DeliveryAttemptCoordinator` 解释 affected-row 协议，输出 typed `ACQUIRED`、`IN_FLIGHT`、`TERMINAL`、`BACKOFF` 状态和 typed confirmations。
   Dispatcher 仍负责 fan-out/preference，reaper 仍负责 scheduling，mapper SQL 不变。
5. `web-security` 持有非 Bean 的 `AuditEmissionPolicy`，以显式 ports 同时服务 `AuditAspect` 与 Admin `DefaultAuditRecorder`。
   统一 performer fallback、entity null/empty → `N/A`、经 `ClientIpResolver` 取 IP、UA empty → `null`；现有 failure/clear semantics 保持，fail-closed improvement 继续作为后续项。
6. `AdminDubboReferenceRegistry` 收口为 34 个 one-hop references；五个 RPC-backed Admin module 均只有一个 constructor，所有 RPC seam 可见。
   可选的非 RPC metrics 注入不变，`SubmissionUserDetailStatsPort` 仍作为后续项。
7. 新增聚焦的 `services/platform/redis`（artifact `backend-redis`），由其持有 `RedisValueSerializationPolicy`，并让五份 Redis config copy 收敛到同一策略。
   Owner 保留 connection、TTL、key/hash、bean wiring；default typing + JavaTime 的 byte compatibility 保持并由测试 pin 住。
8. Submission owner 返回 sealed `RejudgeOutcome`；`SubmissionAdministrationProvider` 成为唯一的 wire-DTO builder 和 `AppErrorCode → RpcResult` mapper。
   `RejudgeResultDTO` 的 wire shape（含 nullable `success`）不变，null-lenient compatibility readers 保留。
9. `useRemoteTable` 持有 search、filters、pagination、refresh 的 query state machine，并通过显式 Problems route adapter 接入路由。
   dead helpers 删除，`DataTable` 继续保持 controlled，13 个 consumers 完成迁移。
10. `usePastContestsPager` 成为 Browse/Home 共用的单一 pager；store 将 `loadingPastContests` 与 `loadingContests` 分离。
    避免历史赛事分页和当前赛事列表互相覆盖 loading 状态。
11. http-client 的 dedup map 移入 `createHttpClient` closure，使每个 client instance 隔离。
    补充 cross-instance isolation test。

工具性修复：C5b 补齐 audit aspect test stubs，C6b 固化 registry count contract，C13/C13b 保持 docs-contract 的 README pin；这些修复不改变上述运行时边界。

## Deliberate non-decisions

- 复审报告中的 parked 项（Management 集合取消、post-mutation reconciliation、Auth HTTP
  write gateway、Submission redispatch transition、Judge outbox payload typing、owner-startup
  handoff、Judge async lifecycle、Submission admission coordinator、Outbox owner binding、
  Permission flat-key ownership、Backup port width、SMTP mask + AllSettingsVO copy 等）
  本轮不实现，保留给后续轮次评估。

## Consequences

- 共享 seam 降低重复生命周期策略，但不改变 Owner 数据所有权、delivery state、Search 版本保护或 distributed 默认拓扑。
- Receipt、cutover、outbox/ledger、audit、RPC、Redis、rejudge 和前端查询状态的边界现在都有单一的实现入口；仍需把业务语义留在事实拥有者内，避免共享层成为新的 Hub。
- 兼容性边界被显式保留：Auth 的历史 receipt 顺序、Redis value bytes、rejudge wire DTO 和前端受控表格/路由行为不因收敛而改变。
- Contest/Profile direct-write、fail-closed audit improvement、SubmissionUserDetailStatsPort 及 parked 项仍是后续评估项；本 ADR 不宣称生产切流或新增基础设施。

## Evidence anchors

- [Receipt protocol and store](../../../services/platform/common/src/main/java/com/ulticode/common/command/ReceiptExecutor.java)、[`CommandReceiptStore`](../../../services/platform/common/src/main/java/com/ulticode/common/command/CommandReceiptStore.java)、[`ReceiptExecutionMode`](../../../services/platform/common/src/main/java/com/ulticode/common/command/ReceiptExecutionMode.java)
- [`OwnerCutoverGate`](../../../services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/OwnerCutoverGate.java)、[`OwnerCutoverRegistry`](../../../services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/OwnerCutoverRegistry.java)、[`AdminNotificationServiceImpl`](../../../services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminNotificationServiceImpl.java)、[`owner-architecture-source-contract.rules`](../../../scripts/test/owner-architecture-source-contract.rules)
- [`AuditOutboxMapper`](../../../services/admin/src/main/java/com/ulticode/modules/admin/outbox/mapper/AuditOutboxMapper.java)、[`AuditOutboxProcessor`](../../../services/admin/src/main/java/com/ulticode/modules/admin/outbox/AuditOutboxProcessor.java)、[`V20260916120000__Add_Audit_Outbox_Retry_Fields.sql`](../../../init-db/migrations/admin/V20260916120000__Add_Audit_Outbox_Retry_Fields.sql)
- [`DeliveryAttemptCoordinator`](../../../services/notification/src/main/java/com/ulticode/modules/notification/ledger/DeliveryAttemptCoordinator.java)、[`DeliveryAttemptCoordinatorTest`](../../../services/notification/src/test/java/com/ulticode/modules/notification/ledger/DeliveryAttemptCoordinatorTest.java)
- [`AuditEmissionPolicy`](../../../services/platform/web-security/src/main/java/com/ulticode/audit/AuditEmissionPolicy.java)、[`AuditAspect`](../../../services/platform/web-security/src/main/java/com/ulticode/audit/AuditAspect.java)、[`DefaultAuditRecorder`](../../../services/admin/src/main/java/com/ulticode/common/audit/DefaultAuditRecorder.java)
- [`AdminDubboReferenceRegistry`](../../../services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/AdminDubboReferenceRegistry.java)、[`admin-rpc-adapter-contract.sh`](../../../scripts/test/admin-rpc-adapter-contract.sh)
- [`RedisValueSerializationPolicy`](../../../services/platform/redis/src/main/java/com/ulticode/redis/RedisValueSerializationPolicy.java)、[`RedisValueSerializationPolicyTest`](../../../services/platform/redis/src/test/java/com/ulticode/redis/RedisValueSerializationPolicyTest.java)
- `services/submission/src/main/java/com/ulticode/submission/admin/SubmissionRejudgeService.java`、`services/submission/src/main/java/com/ulticode/submission/admin/RejudgeOutcome.java`、[`SubmissionAdministrationProvider`](../../../services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionAdministrationProvider.java)、[`RejudgeResultDTO`](../../../services/api/submission-api/src/main/java/com/ulticode/submission/api/dto/RejudgeResultDTO.java)
- [`useRemoteTable`](../../../apps/management/src/composables/useRemoteTable.ts)、[`useProblemFilters`](../../../apps/management/src/views/problems/composables/useProblemFilters.ts)、[`usePastContestsPager`](../../../apps/console/src/composables/contest/usePastContestsPager.ts)、[`contestBrowse`](../../../apps/console/src/stores/contestBrowse.ts)
- [`createHttpClient`](../../../packages/http-client/src/index.ts)、[`createHttpClient.test`](../../../packages/http-client/src/__tests__/createHttpClient.test.ts)、[`docs-contract-test.sh`](../../../scripts/dev/docs-contract-test.sh)
