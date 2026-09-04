# 当前状态

更新时间：2026-09-04

## 总体状态

仓库当前位于 `main`。历史 remediation 与 follow-up
基线均已关闭（见[归档证据入口](../archive/README.md)与
[`history/architecture-remediation-20260830.md`](history/architecture-remediation-20260830.md)，
历史快照保持历史时态，不作为当前事实）。

本轮架构收敛与验证边界（2026-09-03 起）：

- Auth 权限写入改为 Auth-owned `AuthorizationMutationService` 的单条
  direct grant/revoke delta；`expectedVersion`、`expiresAt`、认证 actor 和
  idempotency key 均跨 Seam 传递。Auth 在本地事务中执行 direct-row 变更、
  `authz_version` CAS、审计 outbox 和 receipt；角色权限不会被物化或误删。
- 角色编辑改用独立的 Auth-owned `RoleMutationService` + `ChangeRoleCommand`；
  旧的全量 `ChangeAuthorizationCommand` 已删除，Admin 不再以完整
  `AdminUserDetailQuery` 作为权限写入前置条件。
- App-only 的接口已移到对应 App 逻辑 Module 的私有 Seam；`app-api`
  ownership catalog 与 gate 已同步。跨 Owner contract 仍保留在
  `services/api/`，未共享 Entity、Mapper 或事务。
- App `/run` 继续通过 `InteractiveCodeRunner` → Judge `execute` 走同步
  public preview；Judge provider 将 runtime validation `BusinessException`
  映射为 typed 400。Judge runtime 同时提供受约束的异步
  `submit/poll/cancel`，默认 Docker Adapter；可选 Judge0 Adapter 默认关闭，
  endpoint、凭据、回调认证和外部实例尚未验证。当前 receipt 仅进程内有界；
  跨 Judge 副本与重启的 durable 幂等未完成。Judge0 属于可选开发 profile，
  未配置不阻塞本项目的仓库验收。
- Search Worker 已接入小型 `SearchIndex` Seam；本地/未来 hosted Meili
  使用同一 Adapter，Cloud 实例与负载证据仍缺失。Owner JDBC URL、
  `sslMode` 与每 Owner Redis TLS/CA 配置入口及静态 contract 已补齐；
  托管实例、ACL 复核和恢复演练仍未完成；托管数据层属于可选开发 profile，
  自托管默认路径不受影响。
- 已加入 `.devcontainer/devcontainer.json`：Java 17、Node 22、pnpm、
  mise、PM2、Maven wrapper、Docker-in-Docker 和固定端口。postCreate
  只准备依赖，postStart 只运行 unit gate，不自动启动全栈。
- Core + Judge 已提供 opt-in `core` profile：`services/core` 使用显式
  Core parent、五组 Owner DataSource/SqlSessionFactory/TransactionManager
  与互斥 MapperScan；既有 Owner Implementation 在非 Web child context
  中尝试启动，Core readiness 为 `/api/v1/core/health/ready`，端口为 9108。
  Core parent 不依赖 `backend-judge-runtime`；Judge 仍是独立进程。
  child 启动的 timeout/cancel 交接使用单 CAS ownership handoff 协议，
  每个已创建 context 由调用方、timeout 关闭路径或迟到完成 callable
  三者之一唯一接管（`CoreOwnerContextManagerLifecycleTest` 确定性回归）。
- Core 已落地同进程断言载体与 Auth direct-permission local Adapter：
  signed delegation assertion 在受限 ThreadLocal scope 中传递，Auth
  verifier 缺失断言仍 fail closed。G1/G2 与 parent/readiness/classpath
  静态段已通过本地 smoke；但 enabled-owner exec-jar smoke 在 bean wiring
  阶段因同一 classpath 的跨 Owner package leakage 使六个 child context
  失败。Admin/App/Submission/Notification 的完整 consumer local Adapter
  parity、同进程业务路由、Judge readiness 与 mixed-version/remote TLS
  仍未验证，不能切换默认拓扑。

## 验证入口

仓库级验证入口是 `./scripts/dev/test.sh static|unit|quick|full-local|full|integration`
（`--describe` 输出层级语义；`full-local` 保留原重型覆盖）。架构、文档、
迁移、Compose、Redis Streams、Owner migration、HA/network、Nacos 和 Judge
sandbox 的具体门禁由脚本与对应 runbook 维护；零基础设施自证见
`scripts/test/zero-infra-validation-contract.sh`。历史运行证据退役至 Git
历史，不作为当前工作树证据。

验证状态使用 `Repository Implemented`、`Locally Validated`、
`Staging Validated`、`Production Applied`、`OPTIONAL_PROFILE`、`OUT_OF_SCOPE`
和 `BLOCKED_EXTERNAL` 等明确语义；
不以文档删除或 disposable 通过推断生产状态。Graphify 的
`tree_sitter_sql` 缺失只表示工具覆盖缺口，不是 SQL 通过证明。

## 外部边界

本开源项目没有生产环境，当前验收边界是仓库代码、可复现的本地/容器
disposable 验证和自托管默认路径。以下事项属于 `OUT_OF_SCOPE` 或
`OPTIONAL_PROFILE`，不阻塞当前开发完成度：真实生产
migration/backfill/cutover/traffic drain；Nacos/Dubbo 生产注册与证书轮换；
HA promotion/failover；外部 telemetry storage、threshold tuning、SLO 报表；
remote Judge endpoint/cert/workspace/image；生产 firewall/DNS/ingress；
off-host backup、密钥托管和 restore authority；真实 mixed-version drain；
GitLab runner 仍被外部使用的部署 authority 确认（U-01）。只有部署方明确
要求执行某项外部验证、但缺少输入时，才使用 `BLOCKED_EXTERNAL`；可选脚本
缺少 disposable 输入时仍应 fail closed，但不代表仓库存在未完成缺陷。

## 权威入口

- Services issue status：[`services/docs/SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md)
- Remediation closure：[`history/architecture-remediation-20260830.md`](history/architecture-remediation-20260830.md)
- 归档证据入口：[`../archive/README.md`](../archive/README.md)
- Architecture map：[`../architecture/overview.md`](../architecture/overview.md)
- Operational procedures：[`../operations/`](../operations/deployment.md)
- HA/reference topology 决策：[`../architecture/decisions/0001-deferred-platform-expansion.md`](../architecture/decisions/0001-deferred-platform-expansion.md)
- Core + Judge 收敛阻塞证据：[`ADR-0010`](../architecture/decisions/0010-core-judge-convergence-blockers.md)（SVC-025 仍 OPEN，Core 不是默认拓扑）。
- Follow-up plan：[`../architecture/plans/ulticode-architecture-followup-plan.md`](../architecture/plans/ulticode-architecture-followup-plan.md)（历史规划快照，`COMPLETED`）与 [`../architecture/evidence/P5-GATE-004-final-integration-matrix.md`](../architecture/evidence/P5-GATE-004-final-integration-matrix.md)（已提交证据矩阵）；本轮深化计划的机器任务正本不在仓库内（规划 artifact 位于本机 harness 会话目录 `~/.codex/visualizations/2026/09/02/`，只读，不提交）
