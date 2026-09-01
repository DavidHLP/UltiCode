# 当前状态

更新时间：2026-09-01

## 总体状态

仓库当前位于 `fix/architecture-remediation`。`.auto-flow/TASKS.yaml` 是机器可读任务账本，架构 remediation 的 42 个任务均为 `DONE`；`.auto-flow/HANDOFF.yaml` 的 `active_task` 为空。这个状态表示 repository implementation 与 disposable validation 完成，不表示生产部署或生产流量已发生。

当前拓扑是五个 Data Owner（Auth、Admin、App、Submission、Notification）与两个 Worker（Judge、Search）。`judge-runtime` 只是共享依赖。Submission/Notification 单写者、Owner facts、Outbox/Inbox/Streams、contract gates、owner migration manifest、backup/restore drill、Redis ACL materialization、production TLS/JWKS、immutable image policy、scheduler isolation、fenced leases、graceful drain、dependency guard、mTLS、network segmentation 和 Judge remote/rootless boundary 已有代码/配置/测试/运行手册承接。

## 验证入口

仓库级验证入口是 `./scripts/dev/test.sh quick|full|integration`；架构、文档、迁移、Compose、Redis Streams、Owner migration、HA/network、Nacos 和 Judge sandbox 的具体门禁由脚本与对应 runbook 维护。证据原件保留在 [`.auto-flow/`](../../.auto-flow/)；此目录由 flow 工具维护，不能当普通临时缓存删除。

验证状态使用 `Repository Implemented`、`Locally Validated`、`Staging Validated`、`Production Applied` 和 `BLOCKED_EXTERNAL` 等明确语义；不以文档删除或 disposable 通过推断生产状态。Graphify 的 `tree_sitter_sql` 缺失只表示工具覆盖缺口，不是 SQL 通过证明。

## 外部边界

本开源项目没有生产环境。以下事项仍需要未来部署方的环境、授权和证据：真实生产 migration/backfill/cutover/traffic drain；Nacos/Dubbo 生产注册与证书轮换；HA promotion/failover；外部 telemetry storage、threshold tuning、SLO 报表；remote Judge endpoint/cert/workspace/image；生产 firewall/DNS/ingress；off-host backup、密钥托管和 restore authority；真实 mixed-version drain。可选脚本缺少 disposable 输入时输出 `BLOCKED_EXTERNAL` 是有意的 fail-closed 行为。

## 权威入口

- Services issue status：[`services/docs/SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md)
- Task/evidence state：[`../../.auto-flow/TASKS.yaml`](../../.auto-flow/TASKS.yaml) 与 [`task-continuation.md`](task-continuation.md)
- Architecture map：[`../architecture/overview.md`](../architecture/overview.md)
- Operational procedures：[`../operations/`](../operations/deployment.md)
