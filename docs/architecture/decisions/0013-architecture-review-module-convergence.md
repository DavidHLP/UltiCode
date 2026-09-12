# ADR-0013：架构复审后的模块收敛

- 状态：`Accepted — implemented`
- 日期：2026-09-12
- 输入：2026-09-12 架构复审报告
- 关联决策：[`ADR-0011`](0011-topology-contract-module-convergence.md)、[`ADR-0012`](0012-core-topology-three-way-decision.md)
- 默认拓扑：`distributed`

## Context

架构复审确认了几组重复机制，但没有证据支持新增进程、数据库或消息
基础设施。重复主要位于同一 Owner 或共享平台的生命周期、批量等待、分页、
事件投递和前端 API 边界；这些机制应收口，业务事实和失败语义仍由事实拥有者
保留。

## Decisions

1. Admin 使用 `CancellableQueryExecutor` 作为 bounded await/cancel 边界。
   Dashboard、Analytics、用户 enrichment 和用户 detail 复用同一 fan-out
   等待策略；调用方继续负责 typed degradation 和业务错误映射。
2. `RedisStreamInboxBridge` 与 `RedisStreamTransport` 负责 Redis Streams 的
   group/read/reclaim/ack、信封校验、poison staging 和 drain。App、Notification
   与 Admin 只注册 binding 和 handler；Search 复用 transport，但保留自己的
   document lock、version ledger 和 DLQ 语义。
3. `OutboxDispatcher` 负责 claim/reclaim、publish、delivery confirmation、
   retry/DLQ 和 drain。Submission 的两类 outbox、App integration outbox 与
   三类 owner-local audit outbox 通过 adapter 接入；Audit 的共用
   envelope/payload contract 位于 `backend-common`，owner-specific mapper、
   sink 与 audit retry/fencing state 仍留在各 Owner。
4. `OrphanScan` 负责 reconciliation 的 keyset/offset paging、顺序与 null
   校验、有限 page envelope 和分批 parent existence lookup。删除
   `OwnerReconciler` 中无可达实现的 reconciliation-pair 扩展路径；未来 pair
   必须先拥有明确 owner contract 并纳入预算。
5. `@ulticode/domain-types` 只暴露两个 Problem 受众形状：public console
   与 admin management。snake_case/camelCase 兼容、id/date/tag 转换只发生在
   API 边界；现有 acceptance-rate 双 key 兼容保持不变。该包不镜像所有后端
   DTO，也不承担业务状态机。
6. 开发脚本通过 `common.sh` 复用 Compose 参数、env 加载和 Docker 探测；
   `scripts/dev/architecture-contract-test.rules` 是 architecture contract
   child 的权威注册表，`architecture-contract-test.sh --list-qualified static|dynamic`
   提供逐行机器视图，CI 从该视图执行 child，final gate 只选择 static view。
   删除 Garden 的永久 opt-out，保留 static/unit/full 的既有分层。
7. Auth 只保留 `createSessionAuthStore` 这一套共享 session policy；Management
   在其上保留 boolean Pinia view，Console 保留 status-machine view。两端的
   transport、路由和页面权限语义不进入共享工厂。

## Deliberate non-decisions

- 不迁移数百个 Console/Management UI primitive 到 `packages/design-system`。
  现有 design-system 的稳定吸收继续使用；完整 vendoring 收敛等到出现第三个
  稳定 consumer、可量化重复成本或实际 UI 变更时再评估。
- Judge envelope 的 `timeLimitMs`/`memoryLimitKb` 保留为版本化 wire 字段，
  不在本次删除。当前真实执行限制仍由 Judge 的 `ProblemFactsPort` 读取；
  移除字段需要新的 wire compatibility 和所有 reader 证据，不能用静态
  “当前 pipeline 未读取”替代该证明。
- `sandbox-types` 的无前端 consumer、两方法单实现的 `JudgeAttemptExecutor`、
  事务边界内的单 caller outbox writer 不做无收益的抽象或删除。

## Consequences

- 共享 seam 降低重复生命周期策略，但不改变 Owner 数据所有权、delivery
  state、Search 版本保护或 distributed 默认拓扑。
- 新 binding/adapter 必须通过共享生命周期；新增业务语义仍留在 Owner，避免
  shared package 变成 Hub。
- `OrphanScan` 的有限页封装会在异常大扫描或坏页时 fail closed；调用方应以
  新 run 重试，而不是绕过上限。
- UI primitive 和 Judge wire 清理被显式延后，后续工作有明确触发条件和兼容
  责任。

## Evidence anchors

- [`CancellableQueryExecutor`](../../../services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/CancellableQueryExecutor.java)
- [`RedisStreamInboxBridge`](../../../services/platform/integration-inbox/src/main/java/com/ulticode/modules/event/inbox/RedisStreamInboxBridge.java)
- [`OutboxDispatcher`](../../../services/platform/common/src/main/java/com/ulticode/common/outbox/OutboxDispatcher.java)
- [`OrphanScan`](../../../services/admin/src/main/java/com/ulticode/modules/reconciliation/OrphanScan.java)
- [`@ulticode/domain-types`](../../../packages/domain-types/src/index.ts)
- [`createSessionAuthStore`](../../../packages/auth-core/src/createSessionAuthStore.ts)
- [`architecture-contract-test.rules`](../../../scripts/dev/architecture-contract-test.rules)
