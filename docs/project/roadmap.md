# 路线图

本页只描述从当前 repository-complete 状态到真实部署采用所需的顺序；不会把未来触发条件写成已完成。

## 已完成的 repository waves

| 波次 | 结果 |
| --- | --- |
| Phase 0 安全/架构基线 | Cookie/CSRF/route/JWT/delegation/Redis/Nacos gates 已落地 |
| Phase 1 Owner/Worker 骨架 | 五 Owner、Judge/Search Worker、Dubbo/Nacos/contract seams 已落地 |
| Phase 2–5 Owner 收敛 | Submission/Notification 单写者、owner facts、profile/migration/contract gates 已落地 |
| Phase 6 可靠副作用 | Outbox/Inbox/Streams/retry/lease/fence/observability 已落地 |
| Phase 7 repository 收尾 | N-1 contract retirement、验证证据和文档状态已闭环；生产收尾仍外部 |

完整历史迁移方案和 checklist 见 [`../archive/architecture-remediation-2026-08/PROJECT_DOCUMENTATION-2026-08-30.md`](../archive/architecture-remediation-2026-08/PROJECT_DOCUMENTATION-2026-08-30.md)。

## 下一阶段（外部采用顺序）

1. 由部署方提供真实环境、source/schema/digest authority 和变更窗口。
2. 先执行 backup/restore drill、owner migration preflight、权限和 checksum 对账。
3. 在停止并 drain 所有旧 writer 后执行 owner backfill/cutover；保留 rollback descriptor。
4. 以部署 authority 提供的 registry、TLS/mTLS、remote Judge、firewall/DNS/ingress 和 telemetry 输入完成环境验证。
5. 先 canary/观察，再按 route family 发布；积累真实 mixed-version、SLO、PEL/DLQ 和恢复证据。
6. 达到 schema/contract/traffic drain 门禁后，才执行 legacy columns、tables、routes 或 providers 的 contract retirement。

## Search 启用顺序

默认 dev-lite 使用 DB fallback；dev-full/生产才按条件启用 indexed read。顺序为：启动 MeiliSearch → 启用 Search worker 和 Auth dispatcher → 对每个 index 执行 watermark backfill → 观察 lag、DLQ 和 `_aggregateVersion` 单调性 → 开放全量。索引是派生数据，不是备份。

## 有意延后

Kubernetes、Service Mesh、Kafka/新 MQ、Seata、进一步拆 App、五个独立数据库集群均由 [`../architecture/decisions/0001-deferred-platform-expansion.md`](../architecture/decisions/0001-deferred-platform-expansion.md) 延后。只有 ADR 中列出的容量、SLO、合规、团队边界或故障域证据出现时才重开。
