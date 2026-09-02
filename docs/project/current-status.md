# 当前状态

更新时间：2026-09-02

## 总体状态

仓库当前位于 `fix/architecture-remediation`。架构 remediation 基线已关闭，
`ulticode-architecture-followup` 的 P0–P6、7 batches、43/43 任务已全部完成（`plan.status DONE 2026-09-02`，实施闭环 `9e5d917ce`；文档修正 `8626d33`）；
`GATE-FINAL` 有明确的 `PASS/FAIL/BLOCKED_EXTERNAL` 语义，当前唯一一次 composite 运行 `gate-final-integration.sh` 在 `P2/infra-isolation` 止损 `FAIL`（artifact 738，disposable-infra 瞬态争用，单跑该门禁 PASS），按设计止损已演示，周期重跑即为验证入口；仅 `Repository Implemented + Disposable` 证据，不表示生产部署或流量已发生。

当前拓扑是五个 Data Owner（Auth、Admin、App、Submission、Notification）与
两个 Worker（Judge、Search）。`judge-runtime` 是 Judge worker 使用的共享
执行依赖，不再进入 App 的 compile tree。Submission/Notification 单写者、
Owner facts、Outbox/Inbox/Streams、Redis role/ACL、Admin bounded owner reads、
typed degradation、fixed use-case metrics、owner migration manifest、
backup/restore drill、Nacos/Meili recovery contracts、scheduler isolation、
fenced leases、graceful drain、mTLS、network segmentation 和 Judge
remote/rootless boundary 均有代码、配置、测试或运行手册承接。

## 验证入口

仓库级验证入口是 `./scripts/dev/test.sh quick|full|integration`；架构、文档、迁移、Compose、Redis Streams、Owner migration、HA/network、Nacos 和 Judge sandbox 的具体门禁由脚本与对应 runbook 维护。历史运行证据已退役至 Git 历史，不再作为当前工作树证据；验证结果可通过上方入口重新生成。

验证状态使用 `Repository Implemented`、`Locally Validated`、`Staging Validated`、`Production Applied` 和 `BLOCKED_EXTERNAL` 等明确语义；不以文档删除或 disposable 通过推断生产状态。Graphify 的 `tree_sitter_sql` 缺失只表示工具覆盖缺口，不是 SQL 通过证明。

## 外部边界

本开源项目没有生产环境。以下事项仍需要未来部署方的环境、授权和证据：真实生产 migration/backfill/cutover/traffic drain；Nacos/Dubbo 生产注册与证书轮换；HA promotion/failover；外部 telemetry storage、threshold tuning、SLO 报表；remote Judge endpoint/cert/workspace/image；生产 firewall/DNS/ingress；off-host backup、密钥托管和 restore authority；真实 mixed-version drain。可选脚本缺少 disposable 输入时输出 `BLOCKED_EXTERNAL` 是有意的 fail-closed 行为。

## 权威入口

- Services issue status：[`services/docs/SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md)
- Remediation closure：[`history/architecture-remediation-20260830.md`](history/architecture-remediation-20260830.md)
- 归档证据入口：[`../archive/README.md`](../archive/README.md)
- Architecture map：[`../architecture/overview.md`](../architecture/overview.md)
- Operational procedures：[`../operations/`](../operations/deployment.md)
- Follow-up plan：[`../architecture/plans/ulticode-architecture-followup-plan.md`](../architecture/plans/ulticode-architecture-followup-plan.md)（历史规划快照，`COMPLETED`）与 [`../architecture/evidence/P5-GATE-004-final-integration-matrix.md`](../architecture/evidence/P5-GATE-004-final-integration-matrix.md)（已提交证据矩阵）；机器任务正本 `.agent/tasks/ulticode-architecture-followup/TASKS.yaml` 为本机 ignored、仅本地可读（`AGENTS.md` 约定，不提交）
