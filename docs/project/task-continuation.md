# 任务延续与验证索引

## 当前指针

本页只说明如何恢复工作，不复制任务数量、提交哈希或状态。机器可读状态以 [`../../.auto-flow/TASKS.yaml`](../../.auto-flow/TASKS.yaml) 和 [`../../.auto-flow/HANDOFF.yaml`](../../.auto-flow/HANDOFF.yaml) 为准。

## 继续工作时的顺序

1. 阅读 [`current-status.md`](current-status.md)、[`known-issues.md`](known-issues.md) 和任务相关 ADR。
2. 阅读 `.auto-flow/TASKS.yaml` 的对应 task entry 与 evidence path；不要重新发明第二套任务状态。
3. 核对 source、POM、config、Compose、脚本和测试；文档不是执行真相。
4. 修改代码后运行对应模块完整 gate，并运行 `rtk graphify update .`。
5. 只有真实验证后才更新 task/evidence/handoff；生产、远程、凭据和外部流量状态必须保持 `BLOCKED_EXTERNAL` 或未声明。

## 证据索引

- 任务证据目录：`.auto-flow/evidence/architecture-remediation-20260830/`
- 详细决策：`.auto-flow/DECISIONS.md`
- 当前验证汇总：`.auto-flow/EVIDENCE.md`、`.auto-flow/COVERAGE.md`
- 详细 Services runbook：[`../../services/docs/`](../../services/docs/SERVICES_ISSUES.md)

这些 `.auto-flow` 文件由现有 flow 工具维护，属于当前可追溯状态，不在普通文档变更中删除、移动或复制。
