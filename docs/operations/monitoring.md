# 监控、SLO 与运行证据

## 观测面

可选的 `docker-compose.observability.yml` 提供 loopback-only、digest-pinned 的 OpenTelemetry Collector、Prometheus、Alertmanager、Grafana、Tempo 和 Loki。HTTP Owner 暴露 metrics；无 HTTP 的 Judge/Search worker 通过 Micrometer OTLP 输出。日志携带 trace/span 关联，Grafana 可从 Loki 跳转 Tempo。

生产 telemetry receiver、存储、保留周期、通知 webhook、阈值调优和真实流量 SLO 由外部运维平台负责；仓库 overlay 默认不启动，也不公开 management endpoint 或 secret。

## 关键指标

- HTTP/RPC：延迟、错误、timeout、circuit-open、bulkhead saturation。
- Outbox/Inbox：oldest age、retry、dead、duplicate、lease expired。
- Worker：queue lag、PEL size、PEL oldest age、DLQ size、last success、consume failures。
- Reconciliation/backup：run、skip、failure、checksum/parity 和 lease 状态。
- Scheduler：active、queued、pool size、completed、rejected；pool 仅允许 1–16。
- JVM/resource：CPU、memory、thread、GC、connection pool 和 readiness。

`services/docs/WORKER_SLO_RUNBOOK.md` 是 Worker 指标、告警、DLQ、PEL 和恢复命令的唯一详细入口；初始阈值必须用真实 p50/p95 基线重新调优。

## 操作顺序

1. 先确认服务/worker readiness、依赖健康和最近 release descriptor。
2. 再检查 lag、PEL、DLQ、oldest age、last success 和错误日志的时间窗口。
3. 修复下游依赖、ACL、schema 或 provider，再等待现有 reclaim/retry；不要先删 stream、PEL、ledger 或 version hash。
4. 需要 replay、discard、ledger reset 或 stop-write 时，按 Worker SLO runbook 的门禁执行并保留证据。
5. 记录 trace ID、commit、digest、schema checksum、affected service 和恢复结果；不要记录 token/password/private key。

## 验证

```bash
./scripts/test/observability-contract.sh
```

可选 overlay 的 Compose、Prometheus、Alertmanager、Collector、dashboard 和 release annotation 合约由 `scripts/test/observability-contract.sh` 维护；不要把本地 overlay 通过文档描述为生产 SLO 达标。
