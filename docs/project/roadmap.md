# 路线图

本页只描述从当前 repository-complete 状态到真实部署采用所需的 Planned 顺序；不会把未来触发条件写成已完成。仓库内任务状态见 [`current-status.md`](current-status.md)。

## Planned：外部采用顺序

1. 由部署方提供真实环境、source/schema/digest authority 和变更窗口。
2. 先执行 backup/restore drill、owner migration preflight、权限和 checksum 对账。
3. 在停止并 drain 所有旧 writer 后执行 owner backfill/cutover；保留 rollback descriptor。
4. 以部署 authority 提供的 registry、TLS/mTLS、remote Judge、firewall/DNS/ingress 和 telemetry 输入完成环境验证。
5. 先 canary/观察，再按 route family 发布；积累真实 mixed-version、SLO、PEL/DLQ 和恢复证据。
6. 达到 schema/contract/traffic drain 门禁后，才执行 legacy columns、tables、routes 或 providers 的 contract retirement。

## Planned：Search 启用顺序

dev-lite 使用数据库 Search 且不启动 Search worker；dev-full/生产启用 indexed read 前必须满足 `APP_SUBMISSION_ROUTING_MODE=remote` 与 `SUBMISSION_CUTOVER_COMPLETE=true`。顺序为：启动 MeiliSearch → 启用 Search worker 和 Auth dispatcher → 对每个 index 执行 watermark backfill → 观察 lag、DLQ 和 `_aggregateVersion` 单调性 → 开放全量。索引是派生数据，不是备份。

## Not implemented：有意延后

Kubernetes、Service Mesh、Kafka/新 MQ、Seata、进一步拆 App、五个独立数据库集群均由 [`../architecture/decisions/0001-deferred-platform-expansion.md`](../architecture/decisions/0001-deferred-platform-expansion.md) 延后。只有 ADR 中列出的容量、SLO、合规、团队边界或故障域证据出现时才重开。
