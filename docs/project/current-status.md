# 当前状态

更新时间：2026-09-01

## 总体状态

仓库当前位于 `fix/architecture-remediation`。架构 remediation 的 42 个当前任务全部 `DONE`；`.auto-flow/TASKS.yaml` 仍是机器可读任务账本，`HANDOFF.yaml` 的 `active_task` 为空。这个状态表示 repository implementation 与 disposable validation 完成，不表示生产部署或生产流量已发生。

当前拓扑是五个 Data Owner（Auth、Admin、App、Submission、Notification）与两个 Worker（Judge、Search）。`judge-runtime` 只是共享依赖。Submission/Notification 单写者、Owner facts、Outbox/Inbox/Streams、contract gates、owner migration manifest、backup/restore drill、Redis ACL materialization、production TLS/JWKS、immutable image policy、scheduler isolation、fenced leases、graceful drain、dependency guard、mTLS、network segmentation 和 Judge remote/rootless boundary 已有代码/配置/测试/运行手册承接。

## 最近闭环

- `1e69b5b5eb3a607aba5f5bb5ca7da5729da6a11a`：retire Submission N-1 contracts/provider，contract revision 2.0.0，distinct-revision compatibility proof。
- `cb40a226934ec501b788a1a673fe864d41d35ae0`：disposable verification and gate hardening。
- `cef925a`：flow/evidence synchronization checkpoint。
- `043825793`：当前最近提交，关闭 disposable blocker 状态；工作树另有用户未提交改动，不能在文档清理中覆盖。

## 验证真相

当前记录的 repository checks 包括：`./scripts/dev/test.sh quick`、`full`、Maven compile/test/verify、contract/architecture/docs/migration/Compose gates、Redis Streams crash/reclaim/DLQ、Owner migration/backfill/cutover/rollback、HA reconnect、Nacos registration、network reachability、rootless DinD Judge smoke 和 Graphify。证据原件保留在 [`.auto-flow/`](../../.auto-flow/)；此目录由 flow 工具维护，不能当普通临时缓存删除。

fresh delegated reviewer transport 未返回最终消息时标为 `BLOCKED_TOOL`，不冒充 reviewer PASS；此前具体 CRITICAL/HIGH findings 已由源码、测试、契约和 disposable checks 复核。Graphify 的 `tree_sitter_sql` 缺失是工具覆盖警告，不是 SQL 通过证明。

## 外部边界

本开源项目没有生产环境。以下事项仍需要未来部署方的环境、授权和证据：真实生产 migration/backfill/cutover/traffic drain；Nacos/Dubbo 生产注册与证书轮换；HA promotion/failover；外部 telemetry storage、threshold tuning、SLO 报表；remote Judge endpoint/cert/workspace/image；生产 firewall/DNS/ingress；off-host backup、密钥托管和 restore authority；真实 mixed-version drain。可选脚本缺少 disposable 输入时输出 `BLOCKED_EXTERNAL` 是有意的 fail-closed 行为。

## 权威入口

- Services issue status：[`services/docs/SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md)
- Task/evidence state：[`../../.auto-flow/TASKS.yaml`](../../.auto-flow/TASKS.yaml) 与 [`task-continuation.md`](task-continuation.md)
- Architecture map：[`../architecture/overview.md`](../architecture/overview.md)
- Operational procedures：[`../operations/`](../operations/deployment.md)
