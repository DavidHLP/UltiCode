# 当前状态

更新时间：2026-09-03

## 总体状态

仓库当前位于 `main`。历史 remediation 与 follow-up
基线均已关闭（见[归档证据入口](../archive/README.md)与
[`history/architecture-remediation-20260830.md`](history/architecture-remediation-20260830.md)，
历史快照保持历史时态，不作为当前事实）。

本轮运行/验证/Admin 深化收敛（2026-09-03，仓库内完成）：

- 验证入口分为 `static` / `unit` / `quick` / `full-local` / `full` /
  `integration` 六层：`static` 零基础设施并可用 deny-shim 自证；
  `quick` 是 `static + unit` 的弃用兼容别名；原 `quick` 重型语义更名为
  `full-local`。后端 `unit` 走根 POM `unit` profile 与 deny 环境门禁（无 Docker/DB/Redis/Nacos/Meili，`*IT`/`*IntegrationTest` 含嵌套类排除；deny 运行 5786 测试零失败、零 Testcontainers/IT），由 `zero-infra-validation-contract.sh` 的 unit deny 阶段自证。
- `dev-lite` 兼容默认保留，新增 `app-journey`/`admin`/`submission-judge`/
  `search`/`full-stack` 场景与 `--scope`；`up`/`stop`/`status`/`logs`/
  `health`/`doctor` 消费同一 resolver；Search off 时不创建 Meili 容器。
- Admin 用户详情收口为单 use-case `AdminUserDetailQuery` 深 Module：Auth
  account 权威判定、Auth snapshot 复用、Submission 单次 stats snapshot、
  ≤5 逻辑 RPC / ≤2 轮、区块级 `OK/PARTIAL/UNAVAILABLE`；权限写 fail closed
  （读取失败零写入，不再把空集当全量回写）；旧逐项 stats path 已删除。
- 发布控制面：九个 deployable 为默认协调发布 set（services matrix 带
  role/release_group/health 分类）；`deployment-integrity.sh` 新增只读
  `describe` 与 `verify-registry`；`.gitlab-ci.yml` 旧直连部署路径已退役
  禁用（仓库内无 GitLab runner 授权证据，U-01）。
- 单机 reference topology 接受风险与 HA 重开触发器记录于
  [`ADR-0001`](../architecture/decisions/0001-deferred-platform-expansion.md)；
  共享 MySQL/Redis/Nacos 是共同故障域，schema/ACL 隔离不等于物理故障隔离。
- App locality：`app-api` 71 个 interface 完成 consumer catalog，10 个
  App-only internal seam 迁入对应私有 Module/内部包（contest push/
  subscription、problem 内部 read ports），app-api 不再收纳纯内部
  interface；Forum/Solution 内部 Module pilot 无真实业务变更触发，
  记录 NO-GO（触发条件见问题注册表）。

当前拓扑保持五个 Data Owner（Auth、Admin、App、Submission、Notification）
与两个 Worker（Judge、Search）。`judge-runtime` 是 Judge worker 使用的共享
执行依赖，不进入 App 的 compile tree。本轮没有新增物理进程、数据 Owner、
事件读模型、Kubernetes/Kafka/Service Mesh/Seata 或第五套基础设施；没有
生产部署、流量、HA failover、SLO 或外部运维证据被制造或声称。

## 验证入口

仓库级验证入口是 `./scripts/dev/test.sh static|unit|quick|full-local|full|integration`
（`--describe` 输出层级语义；`full-local` 保留原重型覆盖）。架构、文档、
迁移、Compose、Redis Streams、Owner migration、HA/network、Nacos 和 Judge
sandbox 的具体门禁由脚本与对应 runbook 维护；零基础设施自证见
`scripts/test/zero-infra-validation-contract.sh`。历史运行证据退役至 Git
历史，不作为当前工作树证据。

验证状态使用 `Repository Implemented`、`Locally Validated`、
`Staging Validated`、`Production Applied` 和 `BLOCKED_EXTERNAL` 等明确语义；
不以文档删除或 disposable 通过推断生产状态。Graphify 的
`tree_sitter_sql` 缺失只表示工具覆盖缺口，不是 SQL 通过证明。

## 外部边界

本开源项目没有生产环境。以下事项仍需要未来部署方的环境、授权和证据：真实
生产 migration/backfill/cutover/traffic drain；Nacos/Dubbo 生产注册与证书
轮换；HA promotion/failover；外部 telemetry storage、threshold tuning、
SLO 报表；remote Judge endpoint/cert/workspace/image；生产
firewall/DNS/ingress；off-host backup、密钥托管和 restore authority；真实
mixed-version drain；GitLab runner 仍被外部使用的部署 authority 确认
（U-01）。可选脚本缺少 disposable 输入时输出 `BLOCKED_EXTERNAL` 是有意的
fail-closed 行为。

## 权威入口

- Services issue status：[`services/docs/SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md)
- Remediation closure：[`history/architecture-remediation-20260830.md`](history/architecture-remediation-20260830.md)
- 归档证据入口：[`../archive/README.md`](../archive/README.md)
- Architecture map：[`../architecture/overview.md`](../architecture/overview.md)
- Operational procedures：[`../operations/`](../operations/deployment.md)
- HA/reference topology 决策：[`../architecture/decisions/0001-deferred-platform-expansion.md`](../architecture/decisions/0001-deferred-platform-expansion.md)
- Follow-up plan：[`../architecture/plans/ulticode-architecture-followup-plan.md`](../architecture/plans/ulticode-architecture-followup-plan.md)（历史规划快照，`COMPLETED`）与 [`../architecture/evidence/P5-GATE-004-final-integration-matrix.md`](../architecture/evidence/P5-GATE-004-final-integration-matrix.md)（已提交证据矩阵）；本轮深化计划的机器任务正本不在仓库内（规划 artifact 位于本机 harness 会话目录 `~/.codex/visualizations/2026/09/02/`，只读，不提交）
