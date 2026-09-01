# 任务延续与验证索引

## 当前指针

当前架构 remediation 已闭环：`active_task: none`、42/42 current tasks `DONE`、0 TODO、0 BLOCKED，最新 repository implementation checkpoint 为 `1e69b5b5eb3a607aba5f5bb5ca7da5729da6a11a`，flow checkpoint 为 `cef925a`。机器可读状态不在本页重写，以 [`../../.auto-flow/TASKS.yaml`](../../.auto-flow/TASKS.yaml) 和 [`../../.auto-flow/HANDOFF.yaml`](../../.auto-flow/HANDOFF.yaml) 为准。

## 任务分组

| 组 | IDs | 当前结果 |
| --- | --- | --- |
| Context/trace | `CTX-001`, `TRACE-001` | 完成 baseline、Finding 映射和证据入口 |
| P0 security | `P0-SEC-001..008` | Cookie、CSRF、route、JWT/JWKS、delegation、Redis ACL、SSH、Nacos 完成 |
| P1 ownership | `P1-SUB-001..004`, `P1-NOT-001`, `P1-DATA-001`, `P1-AUDIT-001`, `P1-SEAM-001` | Submission/Notification 单写者、owner facts、migration/contraction、audit outbox 和 dead seams 完成 |
| P2 control plane | `P2-MIG-001`, `P2-BACKUP-001`, `P2-REDIS-001`, `P2-TLS-001`, `P2-SC-001`, `P2-OBS-001`, `P2-DEPLOY-001` | migration、backup、ACL、TLS、immutable supply chain、observability、deploy/rollback 完成 |
| P3 resilience | `P3-SCHED-001`, `P3-LEASE-001`, `P3-GRACE-001`, `P3-RES-001`, `P3-STREAM-001`, `P3-SCALE-001`, `P3-HA-001`, `P3-IDENTITY-001`, `P3-NET-001`, `P3-JUDGE-001` | scheduler、fence、drain、dependency guard、Streams、scale、HA reference、mTLS、network、Judge isolation 完成 |
| Architecture/review | `ARCH-CONTRACT-001`, `ARCH-DUBBO-001`, `ARCH-SEC-001`, `TEST-COV-001`, `REVIEW-001`, `REVIEW-002`, `CLOSURE-001` | contract/provider inventory、security、coverage、两轮复核和 closure 完成 |

Services issue ledger 的 SVC-003 gate 已关闭；SVC-006–010 仍是外部触发型 deferred 项，见 [`../../services/docs/SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md)。

## 继续工作时的顺序

1. 阅读 [`current-status.md`](current-status.md)、[`known-issues.md`](known-issues.md) 和任务相关 ADR。
2. 阅读 `.auto-flow/TASKS.yaml` 的对应 task entry 与 evidence path；不要重新发明第二套任务状态。
3. 核对 source、POM、config、Compose、脚本和测试；文档不是执行真相。
4. 修改代码后运行对应模块完整 gate，并运行 `rtk graphify update .`。
5. 更新 task/evidence/handoff 只有在真实验证后；生产、远程、凭据和外部流量状态必须保持 `BLOCKED_EXTERNAL` 或未声明。

## 证据索引

- 当前 evidence packet：`.auto-flow/evidence/architecture-remediation-20260830/blocked-external-closure-20260901.result`
- 任务证据目录：`.auto-flow/evidence/architecture-remediation-20260830/`
- 详细决策：`.auto-flow/DECISIONS.md`
- 当前验证汇总：`.auto-flow/EVIDENCE.md`、`.auto-flow/COVERAGE.md`
- 详细 Services runbook：[`../../services/docs/`](../../services/docs/SERVICES_ISSUES.md)

这些 `.auto-flow` 文件由现有 flow 工具维护，属于当前可追溯状态，不在本次清理中删除或移动。
