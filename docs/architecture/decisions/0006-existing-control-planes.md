# ADR-0006：复用现有控制面

- Status: Accepted
- Date: 2026-08-28

## Decision

继续扩展当前的 Compose、Dubbo Triple/Nacos、Redis Streams、Outbox/Inbox、Worker SLO、Owner migration manifest、backup runbook、contract gate 和 Admin aggregation deep module。不为形式上的“完整微服务”提前引入新 MQ、Service Mesh、Kubernetes、Seata 或平行 task/architecture system。

## Rationale

当前风险是 ownership、权限、可靠性、部署和证据边界，而不是组件数量。已有 Streams 提供 PEL/claim/replay/DLQ，Owner-local transaction + Outbox/Inbox 避免跨库 2PC，Compose/host-deploy 可以先证明健康、lease、drain、digest 和 rollback 语义。

## Re-evaluation

达到 ADR-0001 的容量、SLO、合规、团队自治或故障域触发条件后，新增平台必须先有独立 ADR、迁移/回滚计划、混合版本兼容、观测和退役证据。
