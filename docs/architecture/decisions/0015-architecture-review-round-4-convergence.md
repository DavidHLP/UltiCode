# ADR-0015：架构复审第四轮收敛（C1-C13）

- 状态：`Accepted — in progress`（services 批已实现并验证；前端批 C5-C11 落地后置 `implemented`）
- 日期：2026-09-17
- 输入：2026-09-17 架构复审报告（round 4，scope: `services/` · `apps/` · `packages/`；报告副本存于 `.local/architecture-review/architecture-review-20260917-121144.html`）
- 关联决策：[`ADR-0011`](0011-topology-contract-module-convergence.md)、[`ADR-0013`](0013-architecture-review-module-convergence.md)、[`ADR-0014`](0014-architecture-review-round-3-convergence.md)（本 ADR 对 ADR-0014 决定 1 作出 amendment）
- 默认拓扑：`distributed`

## Context

第四轮复审确认 13 张收敛卡片（C1-C13），全部为 in-process / ports & adapters 深化，不新增
进程、数据库或消息基础设施。审查聚焦 round-3 新 seam 与 parked 项：receipt 构造面、
cutover 决策面、audit retry 契约、judge outbox payload、intake provider 依赖，以及前端
列表/取消状态、搜索 staleness、http-client 类型与去重所有权。

services 批（C1-C4、C12、C13）已实现并经目标单测、闭包测试与全量编译验证；前端批
（C5-C11）与批级 `full` 验证尚在进行中，其决定与证据在合并后随本 ADR 更新。

## Decisions

1. **C1 receipt store 按协议拆分。** `CommandReceiptStore` 收敛为 base（`insert`/`findByKey`），
   claim 协议经新增 `ClaimCommandReceiptStore` 扩展（`markSuccess`/`deleteClaim`）；
   `ReceiptExecutor` 改为 `mutateThenRecord` / `claimMutateFinalize` 两个静态工厂（构造器
   私有），Auth 的 no-op 实现被删除、只实现 base。模式误配不可再表达。
2. **C2 owner receipt 构造面收敛（对 ADR-0014 决定 1 的 amendment）。** 新增
   `platform/receipt`（artifact `backend-receipt`）：`JacksonReceiptPayloadCodec`、
   `ReceiptExecutorFactory.claim`（claim profile：共享指纹/metadata/委托校验）、以及
   `CommandReceiptStoreBridge` / `ClaimCommandReceiptStoreBridge` 参数化 store 桥（owner 提供
   entity 转换与 mapper 方法引用，桥只持有接口一致性与缺失实体守卫）。共享校验谓词落
   `platform/common` 的 `ReceiptCommandValidation`。
   **Amendment 记录**：ADR-0014 决定 1 曾将 codec 放置表述为“owner adapter 内是边界而非
   重复”；本轮证据（三家 adapter 的 `JacksonPayloadCodec`/`validCommand`/store 桥样板近乎
   逐字相同）支持把 codec、构造 profile 与参数化桥收敛到独立平台 module。`backend-common`
   的 dependency-free 契约（含 `BackendCommonArchTest`）不变；owner 的表、entity 转换、
   错误命名空间与 Auth 的 legacy fingerprint/投影保持 owner-local。
3. **C12 intake provider 依赖收窄。** `SubmissionIntakeProvider` 的字段改依赖
   `SubmissionIntakePort`（不再依赖宽实现 `DefaultSubmissionWritePort`）；provider 单测可注入
   纯端口 fake。已实证 Spring 6.2 `findAutowireCandidates` 在候选收集阶段排除
   self-reference，接口注入唯一解析到 owner 实现。
4. **C13 judge outbox payload typed 化（parked 项转正）。** 新增 owner-private
   `JudgeOutboxPayload`：拥有持久化 key 集与顺序、producer 形状、legacy Map/String 解码与
   数字/字符串强转规则；`JudgeJobEnvelopeTranslator` 只消费 validated 值，`JudgeJobEnvelope`
   V1/V2 wire 契约与已存 JSON 不变（`"null"` 等坏形态按 malformed 处理）。
5. **C3 audit outbox retry 契约 typed 化。** `AuditOutboxOutcome`
   （`RECORDED`/`FAILED_RETRYABLE`/`FAILED_TERMINAL`/`LOST_CLAIM`）替代 processor seam 的裸
   affected-row 计数；`processRecordInNewTx` 对丢失 claim 返回 `LOST_CLAIM`（由 publisher 转为
   publish 失败信号，共享 dispatcher 协议与 SQL 不变）；`markFailedInNewTx` 接收 row 并在
   记录成功时给出 retryable/terminal 结论。
6. **C4 cutover 决策面。** `OwnerCutoverGate.decide()` 返回 `OwnerCutoverDecision`
   （`REMOTE`/`LOCAL`/`DENY`），替换两个布尔公式；三条写路径统一消费：contest `DENY` 即
   fail closed，moderation `LOCAL` 走本地、`DENY` 拒绝、`REMOTE` 走 provider，notification
   写入口新增 `DENY` 拒绝分支。`OwnerCutoverRegistry` 的五个 domain metadata 保留给源码
   契约门禁。
7. **前端批 C5-C11（进行中）。** Management 列表迁移补完与取消生命周期、list-state 收缩、
   moderation filter adapter、Console contest 单状态拥有者与 search staleness、
   http-client 包自有类型与 dedup race 修复；完成时在本节追加决定与证据。

## Deliberate non-decisions

- 不引入共享 receipt entity 接口或反射映射桥：删除测试显示 SQL 表名无法共享、entity 访问器
  属于 owner 事实；共享接口会把 owner entity 耦合到平台层，故取参数化 bridge 形态。
- `platform/receipt` 的 Jackson 依赖不进入 `backend-common`；后者保持 dependency-free。
- 不改变任一 owner 的表、mapper SQL、error namespace、Redis value bytes、receipt 持久形态与
  judge wire envelope；无 migration。
- 前端批的范围不包含新 UI 功能或路由行为变化（沿用 round-3 既定边界）。

## Consequences

- 显式行为变化点：C3 的丢 claim 路径由 processor 内抛出改为 `LOST_CLAIM` 返回值 + publisher
  翻译（dispatcher 的 `last_error` 与计数语义不变）；C4 的 notification/moderation 新增
  `DENY` 拒绝分支（当前 registry 配置下不可达，属 fail-closed 保险）。
- 共享 seam 只收敛生命周期与构造样板；业务事实、表所有权与错误语义仍在各 Owner。
- 兼容性由测试钉住：receipt key/顺序、judge payload keys、Redis bytes、japicmp 契约门禁均未
  触碰。

## Evidence anchors

- [`ReceiptExecutor`](../../../services/platform/common/src/main/java/com/ulticode/common/command/ReceiptExecutor.java)、[`ClaimCommandReceiptStore`](../../../services/platform/common/src/main/java/com/ulticode/common/command/ClaimCommandReceiptStore.java)、[`ReceiptCommandValidation`](../../../services/platform/common/src/main/java/com/ulticode/common/command/ReceiptCommandValidation.java)
- [`ReceiptExecutorFactory`](../../../services/platform/receipt/src/main/java/com/ulticode/receipt/ReceiptExecutorFactory.java)、[`JacksonReceiptPayloadCodec`](../../../services/platform/receipt/src/main/java/com/ulticode/receipt/JacksonReceiptPayloadCodec.java)、[`CommandReceiptStoreBridge`](../../../services/platform/receipt/src/main/java/com/ulticode/receipt/CommandReceiptStoreBridge.java)、[`ClaimCommandReceiptStoreBridge`](../../../services/platform/receipt/src/main/java/com/ulticode/receipt/ClaimCommandReceiptStoreBridge.java)
- [`SubmissionIntakeProvider`](../../../services/submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionIntakeProvider.java)、[`SubmissionProviderContractTest`](../../../services/submission/src/test/java/com/ulticode/submission/provider/SubmissionProviderContractTest.java)
- [`JudgeOutboxPayload`](../../../services/submission/src/main/java/com/ulticode/modules/submission/outbox/JudgeOutboxPayload.java)、[`JudgeJobEnvelopeTranslator`](../../../services/submission/src/main/java/com/ulticode/modules/queue/outbox/dispatcher/JudgeJobEnvelopeTranslator.java)
- [`AuditOutboxOutcome`](../../../services/admin/src/main/java/com/ulticode/modules/admin/outbox/AuditOutboxOutcome.java)、[`AuditOutboxProcessor`](../../../services/admin/src/main/java/com/ulticode/modules/admin/outbox/AuditOutboxProcessor.java)、[`AdminAuditOutboxPublisher`](../../../services/admin/src/main/java/com/ulticode/modules/admin/outbox/AdminAuditOutboxPublisher.java)
- [`OwnerCutoverGate`](../../../services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/OwnerCutoverGate.java)、[`OwnerCutoverDecision`](../../../services/admin/src/main/java/com/ulticode/modules/admin/port/adapter/OwnerCutoverDecision.java)、[`ContentModerationCutoverService`](../../../services/admin/src/main/java/com/ulticode/modules/admin/service/ContentModerationCutoverService.java)、[`ContestCutoverService`](../../../services/admin/src/main/java/com/ulticode/modules/admin/service/ContestCutoverService.java)、[`AdminNotificationServiceImpl`](../../../services/admin/src/main/java/com/ulticode/modules/admin/service/impl/AdminNotificationServiceImpl.java)
