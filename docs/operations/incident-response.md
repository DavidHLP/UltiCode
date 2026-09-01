# 事件响应

## 适用边界

本仓库没有生产环境；本文定义代码、配置和 disposable rehearsal 的响应入口，不替代部署方的 on-call、变更授权、外部 secret store 或生产恢复流程。Services 架构问题、状态和触发条件唯一登记在 [`SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md)。

## 通用步骤

1. 记录 UTC 时间、service/worker、commit/digest、schema checksum、trace ID 和可复现命令；脱敏日志，不记录凭据。
2. 先确定是代码错误、配置漂移、依赖不可用、资源饱和、数据不一致还是外部权限缺失。
3. 对写路径停止扩散：保留 Outbox/Inbox/PEL/lease，避免 flush、手工重复写或绕过 owner。
4. 修复根因后按对应 runbook replay/reclaim/retry；验证幂等、版本 fence、checksum 和健康结果。
5. 需要生产 mutation、credential rotation、traffic drain、cutover、failover、remote Judge 或 TLS 时，转交部署 authority。

## 快速分流

| 症状 | 首查 | 恢复入口 |
| --- | --- | --- |
| 登录/refresh/权限失败 | Cookie/CSRF、JWKS、Auth readiness、authz version | [安全架构](../architecture/security.md) |
| Admin 返回 owner unavailable | RPC timeout/circuit/bulkhead、owner readiness、degradation status | [依赖韧性](../../services/docs/DEPENDENCY_RESILIENCE_RUNBOOK.md) |
| Submission pending 或 stale verdict | owner receipt、generation/attempt、judge PEL/DLQ | [Worker SLO](../../services/docs/WORKER_SLO_RUNBOOK.md) |
| Notification 延迟/重复 | Inbox、delivery ledger、lease、SMTP/Redis relay | [Notification runbook](../../services/docs/OBSERVABILITY_RUNBOOK.md) |
| Search stale/缺失 | event version、tombstone ledger、PEL、Meili health | [Worker SLO](../../services/docs/WORKER_SLO_RUNBOOK.md) |
| scheduler starvation | named executor active/queued/rejected | [Scheduler runbook](../../services/docs/SCHEDULER_RUNBOOK.md) |
| deployment/rollback 不一致 | source/schema/digest descriptor、Compose config | [部署与回滚](deployment.md) |
| migration parity 失败 | checkpoint、TSV conflict、grant、checksum | [数据库迁移](database-migrations.md) |

## 状态语义

`SERVICES_ISSUES.md` 使用 `OPEN`（仓库仍可修）、`DEFERRED`（需真实指标/环境/授权）、`CLOSED`（机制已落地）和 `ACCEPTED`（有意取舍）。当前没有 repository-actionable OPEN；SVC-006–010 的外部触发条件仍需真实环境。不要把删除临时记录、disposable 通过或文档更新宣称为生产问题已解决。

## 参考 runbook

- [Contract compatibility](../../services/docs/CONTRACT_COMPAT_GATE.md)
- [Graceful drain](../../services/docs/GRACEFUL_DRAIN_RUNBOOK.md)
- [Fenced leases](../../services/docs/FENCED_LEASE_RUNBOOK.md)
- [Observability](../../services/docs/OBSERVABILITY_RUNBOOK.md)
- [Worker SLO](../../services/docs/WORKER_SLO_RUNBOOK.md)
