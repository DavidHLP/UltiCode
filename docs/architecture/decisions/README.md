# 架构决策记录

ADR 是 durable history 的权威入口；普通提交不在这里复制。实现状态与当前问题分别见 [`../../project/current-status.md`](../../project/current-status.md) 和 [`../../../services/docs/SERVICES_ISSUES.md`](../../../services/docs/SERVICES_ISSUES.md)。完整 2026-08 remediation 原始决策保留在 [`../../archive/architecture-remediation-2026-08/`](../../archive/architecture-remediation-2026-08/)。

| ADR | 状态 | 决策 |
| --- | --- | --- |
| [0001](0001-deferred-platform-expansion.md) | Accepted | 暂不引入 Kubernetes、Service Mesh、新 MQ、Seata、更多 App 进程或五个独立数据库集群。 |
| [0002](0002-submission-owner-cutover.md) | Accepted | Submission mutation、fence、rejudge 和 outbox 由 `backend-submission` 单写者负责。 |
| [0003](0003-owner-local-audit-outbox.md) | Accepted | 各 Owner 本地写 audit outbox，Admin 通过 Inbox 幂等消费，不跨 Owner 写审计表。 |
| [0004](0004-fenced-singleton-leases.md) | Accepted | migration、backup、reconciliation 等真正 singleton 使用 DB-clock fenced lease；item-level outbox 使用自身 CAS。 |
| [0005](0005-repository-production-boundary.md) | Accepted | 仓库实现与 disposable proof 不等于 production applied；外部 authority 和证据必须显式分开。 |
| [0006](0006-existing-control-planes.md) | Accepted | 在引入新平台前复用 Compose、Dubbo/Nacos、Redis Streams、Outbox/Inbox 和当前 runbooks。 |
| [0007](0007-legacy-compatibility-lifecycle.md) | Accepted — closure `2026-09-02` | legacy compatibility 由 App owner 管理，当前支持范围以 N-1 floor/expiry/cutover gate 约束；`P4-LEGACY-006..011` 已闭环，当前二进制对 `legacy-rollback` fail closed。 |
| [0008](0008-admin-event-read-model.md) | Accepted | Admin 暂不复制 Owner event read model；先使用 bounded synchronous reads、typed degradation 和 metrics，超预算后再复议。 |
