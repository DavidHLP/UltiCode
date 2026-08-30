# `services/` 问题清单

更新时间：2026-08-30

本文件是 `services/` 微服务架构问题、评审 Finding、修复状态与生产触发条件的唯一入口。其他文档只能链接到本文件，不得复制问题正文或维护第二份状态。

实现与可执行配置始终优先；事实冲突时按以下顺序核实：Java source/tests → Maven POM → application config → Compose → startup/deploy scripts → 本文件。

状态定义：

- `OPEN`：当前代码或配置仍存在，且可在仓库范围内继续收敛。
- `DEFERRED`：问题存在，但必须等明确的运行指标、生产环境或外部授权触发。
- `CLOSED`：修复机制已落地；后续回归应由测试或可执行门禁阻止。
- `ACCEPTED`：已裁决为当前阶段可接受的取舍，不应重复报为缺陷。

## 当前结论

当前拓扑为五个 Data Owner（Auth、Admin、App、Submission、Notification）与两个 Worker（Judge、Search）。问题不在服务数量，而在少数部署 Seam、过宽 Contract Interface、Submission 双轨兼容和发布控制面尚未完全收敛。

项目当前只有开发环境。优先修复可测试、可复现的结构问题；不为形式上的“企业级”提前引入 Kubernetes、Service Mesh、新 MQ 或分布式事务框架。

## OPEN

### SVC-003 P1 Submission ownership contraction remains incomplete

Ordinary and contest intake now always use App's `RemoteSubmissionWritePort` and execute in `backend-submission`. The App-local writer, mutation router, fence adapters, judge/result dispatchers, shadow comparator, and lease reaper are deleted; write ownership is no longer selected by `APP_SUBMISSION_ROUTING_MODE`.

P1-SUB-004 now moves reconciliation to Submission-owned bounded full/incremental facts: Admin calls the `backend-submission` provider, and App no longer issues reconciliation SQL against `submissions`. P1-DATA-001 also routes normal user/contest/admin/statistics/generation reads through Submission-owner facts; App local Submission projections and mapper access remain only behind explicit `legacy-rollback`. The repository proof is complete, while owner registration, database grants, traffic observation, backup and physical contraction remain external.

Evidence:

- [Owner-only intake adapter](../app/app-web/src/main/java/com/ulticode/modules/submission/port/adapter/RemoteSubmissionWritePort.java)
- [Owner writer](../submission/src/main/java/com/ulticode/modules/submission/port/DefaultSubmissionWritePort.java)
- [Owner intake provider](../submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionIntakeProvider.java)
- [Owner rejudge provider](../submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionAdministrationProvider.java)
- [Owner rejudge state machine](../submission/src/main/java/com/ulticode/submission/admin/SubmissionRejudgeService.java)
- [Owner command receipt](../submission/src/main/java/com/ulticode/submission/idempotency/SubmissionCommandReceiptExecutor.java)
- [Owner delegation verifier](../submission/src/main/java/com/ulticode/submission/security/InternalDelegationAssertionVerifier.java)
- [Owner-only architecture regression](../app/app-web/src/test/java/com/ulticode/modules/submission/port/SubmissionPortWiringTest.java)
- [Submission reconciliation contract](../api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionReconciliationReadPort.java)
- [Submission reconciliation provider](../submission/src/main/java/com/ulticode/submission/dubbo/provider/SubmissionReconciliationReadProvider.java)
- [Admin reconciliation aggregator](../admin/src/main/java/com/ulticode/modules/reconciliation/OwnerReconciler.java)

Retirement gates:

| Seam | Repository state | Remaining external/next evidence |
| --- | --- | --- |
| Intake/outbox/fence | App mutation implementation deleted; owner tests and duplicate-writer gate are authoritative | Live registration/traffic observation remains external; rollback uses a prior verified artifact plus the data runbook, not a second current writer |
| Admin rejudge | Admin compatibility service/provider deleted; Admin sends authenticated commands to the owner; owner receipt, generation CAS, lease expiry, and judge outbox tests are authoritative | Live Nacos/Dubbo/Redis/target-database observation remains external; full cross-owner audit outbox is P1-AUDIT-001 |
| User reads | Normal user/contest/admin/statistics/generation reads use bounded Submission-owner facts; local adapters are rollback-only | Production registration/traffic observation and the explicit contraction runbook must complete before App legacy tables/contracts are removed |

Do not close SVC-003 or contract the App schema until the external owner registration/traffic, backup, and bounded-read evidence is complete; the repository-side route gate is now covered by P1-DATA-001.

## DEFERRED

### SVC-006 Admin 事件化用户读模型

现状：跨 Owner 用户聚合已收敛到一个深 Module：`AdminUserEnricher` 统一查询 Auth identity/account、批量合并 App profile 并表达 `OK/PARTIAL/UNAVAILABLE`；`DefaultAdminUserProjection` 只负责 VO、权限与本地统计。静默空列表问题和两套聚合逻辑均已修复，但该同步 Module 仍承担两个 Provider 的 freshness 和可用性组合。

证据：[`DefaultAdminUserProjection.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminUserProjection.java)、[`AdminUserEnricher.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java)。

触发条件：Admin 用户列表延迟明确归因于跨 Owner RPC、Owner 故障使管理面不可用超出目标，或 RPC 补偿成本高于本地 projection 维护成本。深 Module 前置条件已完成；触发前不建设事件表。

### SVC-007 生产多主机 HA

现状：生产 Compose 仍是单机拓扑，固定 `container_name`；MySQL、Redis、MeiliSearch 默认单点，Nacos 已强制 cluster 模式但尚无多节点故障演练。

触发条件：出现真实多节点生产环境、明确可用性 SLO，且单机维护窗口不再可接受。届时再实施无状态多副本、反向代理和有状态组件 HA。

### SVC-008 Judge 节点级强隔离

现状：已支持 `DOCKER_HOST`、TLS 与可覆盖 socket 路径，但默认仍挂载本机 Docker socket。

触发条件：Judge 进入对外多租户生产，或沙箱逃逸进入必须缓解的威胁模型。届时使用专用 Judge 节点、远程 rootless daemon、证书轮换和网络隔离，并移除本机 socket。

### SVC-009 可观测运营证据

现状：OTel、Prometheus、Worker SLO 指标、告警规则、Runbook 和故障演练入口已接线；生产 Compose 要求为全部 backend 显式提供外部 OTLP collector 地址。仓库只提供 instrumentation、rules 和加载说明，不内置 collector/Prometheus 运营平台，也不能生成真实流量下的端到端 trace、阈值调优和 SLO 报表。

触发条件：首次真实生产流量可用于 HTTP → Dubbo → Redis Streams 链路验证，并能执行积压、PEL、DLQ 与 last-success 恢复演练。操作入口见 [`WORKER_SLO_RUNBOOK.md`](WORKER_SLO_RUNBOOK.md)。

### SVC-010 混合版本运行历史

现状：per-service tag、选择性 host deploy 与 Contract 兼容门禁已经存在；尚未积累真实混合版本并存和独立回滚证据。

触发条件：随真实发布自然积累。破坏性 Contract 变更仍要求 reactor 协同升级；门禁说明见 [`CONTRACT_COMPAT_GATE.md`](CONTRACT_COMPAT_GATE.md)。

## CLOSED

下列历史 Finding 已有代码、配置或可执行门禁承接，不应在其他文档重复维护正文：

| 历史问题 | 当前承接证据 |
| --- | --- |
| SVC-001 App 直接复用 Judge Docker 执行实现 | 正常/生产 `/run` 通过 `CodeExecutionPort` 调用 `backend-judge` provider；真实 HTTP→Dubbo→provider IT 覆盖成功与无 provider 时 HTTP 503/code 30022；本地 Docker 仅在 SVC-003 的显式 `legacy-rollback` 激活 |
| SVC-002 跨进程 Contract Interface 过宽 | `SubmissionIntakePort`、`SubmissionVerdictWritePort` 与 `ProblemTitleLookupPort` 按消费语义拆分；旧 `SubmissionWritePort`/provider 仅作 deprecated N-1 兼容窗口且所有方法真实委托 |
| SVC-005 Search 选择性发布/回滚入口不完整 | Search 已进入 deploy choice、rollback whitelist/all 与共享 `host-health`；架构门禁从 services matrix 解析全部 backend 并逐项校验三个控制面 |
| Owner 假健康 | `ReadinessChecks`、各 Owner readiness controller、Compose/host health |
| Search/Judge 静态健康证明 | `SearchWorkerReadinessHeartbeat`、`JudgeWorkerReadinessHeartbeat` |
| Dubbo timeout/retry 漂移 | `RpcPolicy` 与各消费方 `RpcPolicyArchTest` |
| 非法运行模式组合 | `FlagCombinationValidator` 与 `devstack-manifest.sh` |
| Admin 备份本地易失 | `BACKUP_DIR` 与生产持久卷 |
| Redis 共享口令/跨域 key | `docker/redis/users.acl` 与 `generate-users-acl.sh` |
| Admin Owner 故障伪装空数据 | 类型化 `OWNER_QUERY_UNAVAILABLE` 与 `DegradationStatus` |
| Worker Consumer 身份冲突/PEL 接管 | 实例唯一 consumer name、claim/reaper 与 `WorkerSloMeters` |
| tracing/SLO 无代码采集 | OTel/Prometheus 接线、Worker 指标与告警规则 |
| App 头像本地状态无 Seam | `FileStoragePort` 与生产持久卷；对象存储仍按需 |
| Nacos/Judge endpoint 写死 | Nacos cluster 参数与 Judge Docker endpoint/TLS 参数 |
| 服务无法独立标记/发布 | per-service version/tag、host-deploy 子集、Contract 兼容门禁；Search 选择入口残差见 SVC-005 |
| WebSocket 接受 client-controlled STOMP token | CONNECT 仅从 handshake session 读取 cookie token，并有拒绝回归 |
| OAuth callback 缺 state cookie 仍放行 | `OAuthStateModule` 对空 cookie fail closed |
| Audit outbox claim 无并发围栏 | PENDING→PROCESSING CAS claim、lease reclaim、claim-owner fenced completion |
| RBAC 变更无 durable invalidation/version 信号 | `authz_version` 原子递增与 durable `AUTHORIZATION_CHANGED` 记录 |
| Submission 写事务同步回访 App/Auth | request owner 传入不可变 `SubmissionFactsSnapshot` 并 fail closed |
| 游离 `services/com` 编译产物 | 当前 source tree 已清除 |
| Services 问题文档多入口与状态漂移 | 本文件为唯一注册表，`PROJECT_DOCUMENTATION.md` 只保留导航链接 |
| SVC-011 | Replay/DLQ HTTP operations are method-protected with `ADMIN|SUPER_ADMIN` and have MockMvc denied-path coverage for every operation |
| SVC-012 | Admin delegation assertions are target-audience bound; Auth, App profile/problem/contest/list/submission writes reject missing or invalid trust; `changePassword` is self-service only (USER in-process via current-password check, admin self-change via verified assertion) |
| SVC-013 | Production Compose requires RS256/JWKS, dedicated internal-delegation secret, explicit Dubbo namespace, and a least-privilege Nacos DB account |
| SVC-014 | Replay/DLQ mutations use set-based SQL updates/deletes instead of unbounded read-plus-N+1 writes; every mutation is bounded by a per-statement row LIMIT |
| SVC-015 | Notification explicit-recipient resolution uses one Auth batch read and no longer routes through an App provider hop |
| SVC-016 | Search and SubmissionJudged inbox bridges reject events with unsupported owner tags before durable handling |
| SVC-017 | Search versioned index operations use a per-index/document Redis lease; stale/equal DELETE and lock-contention regressions are covered by worker tests; contention defers events without inflating delivery counts (no valid-event dead-lettering), and the ledger write is guarded by a lease-ownership re-check |
| SVC-018 | Bootstrap provisioning uses a dedicated `BOOTSTRAP` actor, scoped assertion secret, explicit runner gate, provider operation allowlist, fail-closed Auth query preflights, and propagated restore RPC failures; the administrator count is role-filtered (`ADMIN`+`SUPER_ADMIN`), not an all-account count |

## ACCEPTED

- SVC-004 App/Submission 同步读 Seam：写事务只接受不可变 `SubmissionFactsSnapshot`，Problem/User facts 读使用窄 Interface 与 bounded batch，空页跳过 RPC。只有真实延迟/可用性指标证明其为瓶颈时才重开并升级事件 projection。
- Access token 即时黑名单 writer 当前不建设：refresh token hash-only revoke、HTTP ban check、WebSocket 实时 account check 与短期 access token TTL 共同限定窗口；只有产品明确要求即时踢下线时才新增 writer-owned revoke Interface。
- Search `dev-lite=database`、`dev-full=indexed` 是 manifest 的显式策略，不是配置漂移。
- `SubmissionFactsSnapshot` 只允许增加 Owner intake 校验所需的最小字段；字段变化必须通过现有 Contract shape test，避免形成第二套隐式 facts Interface。
- Submission compatibility Seam 在 SVC-003 门禁满足前保留；不得因为代码“看似不常用”直接删除。
- 当前阶段不拆更多进程，不引入新 MQ、Service Mesh、Kubernetes 或 Seata。

## 维护规则

1. 新的 Services 架构问题只在本文件新增稳定 ID；其他文档只能链接该 ID。
2. 状态变更必须同时更新证据与关闭条件；没有执行证据不得标记 `CLOSED`。
3. 已关闭问题保留一行历史索引，不保留临时分支名、工作区快照、文件长度或易漂移计数。
4. 迁移步骤属于迁移指南，运行操作属于 Runbook，Contract 规则属于 Contract 文档；不要把这些正文复制回问题清单。
