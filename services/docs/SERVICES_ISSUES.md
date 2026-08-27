# `services/` 问题清单

更新时间：2026-08-28

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

### SVC-001 P0 App 直接复用 Judge Docker 执行实现

`ProblemSubmissionController` 的同步 `/problems/{problemId}/submissions/run` 直接注入 `judge-runtime` 的 `CodeExecutionService`；`backend-app` 因此编译依赖整个 Judge 执行实现。该共享实现仍位于 App 私有域式包名并消费 `app-api` 的执行 DTO。默认 `SandboxExecutorImpl` 调用本机 `docker` CLI，但发布矩阵只给 `backend-judge` 镜像安装该能力。

影响：App 与 Judge 没有真实部署 Seam；按默认生产镜像，`/run` 无法获得其声明的执行能力。给公开 HTTP App 增加 Docker socket 只会扩大宿主机控制面，不是正确修复。

证据：

- [`ProblemSubmissionController.java`](../app/app-web/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java)
- [`app-web/pom.xml`](../app/app-web/pom.xml)
- [`CodeExecutionService.java`](../judge-runtime/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java)
- [`SandboxExecutorImpl.java`](../judge-runtime/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java)
- [`.github/services-matrix.json`](../../.github/services-matrix.json)

关闭条件：App 只依赖窄的预执行 Interface；生产 Adapter 在 Judge 执行或显式关闭该能力；`backend-app` 不再依赖 Docker CLI/socket；同步接口与失败语义有端到端回归。

### SVC-002 P1 跨进程 Contract Interface 过宽

`SubmissionWritePort` 同时承载 intake 与 verdict 写入，Judge Adapter 只支持结果写入，其余提交方法直接抛 `UnsupportedOperationException`。`ProblemAdminReadPort` 同时承载完整 Admin problem 读面，Submission Adapter 只需要标题搜索，其余方法同样抛异常。

影响：Adapter 被迫实现不会使用的 Interface，调用方能看到不属于自身能力的操作；Contract 改动的编译与发布影响面大于真实依赖面。

证据：

- [`SubmissionWritePort.java`](../api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionWritePort.java)
- [`RemoteSubmissionWritePort.java`](../judge/src/main/java/com/ulticode/judge/adapter/RemoteSubmissionWritePort.java)
- [`ProblemAdminReadPort.java`](../api/app-api/src/main/java/com/ulticode/app/api/service/ProblemAdminReadPort.java)
- [`ProblemAdminReadDubboAdapter.java`](../submission/src/main/java/com/ulticode/submission/port/adapter/ProblemAdminReadDubboAdapter.java)

关闭条件：按消费语义拆成窄 Interface（至少分离 Submission intake/verdict 与 Problem title lookup）；生产和测试 Adapter 只实现实际能力；不再用“不支持”异常填充正常 Contract。

### SVC-003 P1 Submission single-writer 仍由运行模式决定

默认 `dev-lite` 使用 App-local Submission；只有 `dev-full` 且 cutover gate 完成后才使用独立 Submission Owner。App 与 Submission 仍各自保留 writer、projection、mapper、dispatcher 和 reaper 的实现，部分核心实现已经分叉。

影响：single-writer 不是代码结构事实；日常开发主要验证 local 分支，修复可能只落在其中一份实现，降低 Locality 并延长 Strangler migration。

证据：

- [`SubmissionRoutingProperties.java`](../app/app-web/src/main/java/com/ulticode/modules/submission/config/SubmissionRoutingProperties.java)
- [`devstack-manifest.sh`](../../scripts/dev/devstack-manifest.sh)
- [App-local `DefaultSubmissionWritePort`](../app/app-web/src/main/java/com/ulticode/modules/submission/port/DefaultSubmissionWritePort.java)
- [Owner `DefaultSubmissionWritePort`](../submission/src/main/java/com/ulticode/modules/submission/port/DefaultSubmissionWritePort.java)

退役门禁：

| Seam | 最小观察证据 | 必须为零的错误 | 必须保留的回滚能力 |
| --- | --- | --- | --- |
| Write routing | 远程 writer 连续 14 天并覆盖至少一个高峰周期；无 App-local writer/outbox/reaper 活动 | 丢失或重复 intake、错误 Owner 写入、不可恢复路由错误均为零；可用性错误预算不超过既有基线的 0.1% | routing config、local/remote Adapter、写入与 outbox 对账、切回 local 的已验证命令 |
| Fence routing | Judge、Submission 与 lease/reaper writer 全部 drain；连续 14 天无未完成 generation/attempt lease | stale generation、split-brain lease、丢失 verdict 均为零 | generation/attempt Contract tests 与 legacy rollback 配置 |
| User read routing | 远程分页、详情、最佳提交连续观察 14 天；无活跃 local read caller | 跨用户泄漏、顺序/总数/VO 语义回归、静默错误数据均为零；读取错误预算不超过既有基线的 0.1% | local/remote read Adapter、read Contract tests 与路由快照 |

所有门禁、in-flight drain、数据/事件 checksum 与回滚 artifact 均有证据后，才能删除 App-local Submission 副本。

### SVC-004 P1 App 与 Submission 存在同步依赖环

App 实现依赖 `submission-api`，Submission 实现依赖 `app-api`。写入已经通过 `SubmissionFactsSnapshot` 避免在 intake 事务内回访，但 Submission 的读侧仍通过 App Problem/User facts Interface 完成聚合。

影响：没有跨库 JOIN，但部署和可用性仍互相牵连；过宽的 App read Contract 会进一步放大该环。

证据：

- [`app-web/pom.xml`](../app/app-web/pom.xml)
- [`submission/pom.xml`](../submission/pom.xml)
- [`ProblemFactsDubboAdapter.java`](../submission/src/main/java/com/ulticode/submission/port/adapter/ProblemFactsDubboAdapter.java)
- [`SubmissionUserReadDubboAdapter.java`](../submission/src/main/java/com/ulticode/submission/port/adapter/SubmissionUserReadDubboAdapter.java)

关闭条件：先完成 SVC-002 的窄 Interface；写事务继续只接受不可变 facts；读路径保持 bounded batch。只有真实延迟或可用性指标证明同步 read 是瓶颈时，才升级为事件 projection。

### SVC-005 P2 Search 选择性发布/回滚入口不完整

`backend-search` 已进入镜像矩阵和生产 Compose，但 `cd-deploy` 的手动服务选项遗漏它；`cd-rollback` 的说明包含它，实际白名单数组却遗漏它。

影响：整栈部署可以带上 Search，单服务发布/回滚 Interface 却无法选择 Search，独立发布承诺不完整。

证据：

- [`.github/services-matrix.json`](../../.github/services-matrix.json)
- [`docker-compose.prod.yml`](../../docker-compose.prod.yml)
- [`cd-deploy.yml`](../../.github/workflows/cd-deploy.yml)
- [`cd-rollback.yml`](../../.github/workflows/cd-rollback.yml)

关闭条件：deploy 选项、rollback 白名单和 `all` 集合均包含 `backend-search`，并由现有 workflow/manifest 检查阻止再次漂移。

## DEFERRED

### SVC-006 Admin 事件化用户读模型

现状：`DefaultAdminUserProjection` 与 `AdminUserEnricher` 同步组合 Auth identity/account 与 App profile，并分别表达 `OK/PARTIAL/UNAVAILABLE`。静默空列表问题已经修复，但调用方仍承担两个 Provider 的 freshness 和可用性组合。

证据：[`DefaultAdminUserProjection.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminUserProjection.java)、[`AdminUserEnricher.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java)。

触发条件：Admin 用户列表延迟明确归因于跨 Owner RPC、Owner 故障使管理面不可用超出目标，或 RPC 补偿成本高于本地 projection 维护成本。触发前先把两套聚合逻辑合并成一个深 Module；不提前建设事件表。

### SVC-007 生产多主机 HA

现状：生产 Compose 仍是单机拓扑，固定 `container_name`；MySQL、Redis、MeiliSearch 默认单点，Nacos 默认 standalone。

触发条件：出现真实多节点生产环境、明确可用性 SLO，且单机维护窗口不再可接受。届时再实施无状态多副本、反向代理和有状态组件 HA。

### SVC-008 Judge 节点级强隔离

现状：已支持 `DOCKER_HOST`、TLS 与可覆盖 socket 路径，但默认仍挂载本机 Docker socket。

触发条件：Judge 进入对外多租户生产，或沙箱逃逸进入必须缓解的威胁模型。届时使用专用 Judge 节点、远程 rootless daemon、证书轮换和网络隔离，并移除本机 socket。

### SVC-009 可观测运营证据

现状：OTel、Prometheus、Worker SLO 指标、告警规则、Runbook 和故障演练入口已接线；仓库不能生成真实流量下的端到端 trace、阈值调优和 SLO 报表。

触发条件：首次真实生产流量可用于 HTTP → Dubbo → Redis Streams 链路验证，并能执行积压、PEL、DLQ 与 last-success 恢复演练。操作入口见 [`WORKER_SLO_RUNBOOK.md`](WORKER_SLO_RUNBOOK.md)。

### SVC-010 混合版本运行历史

现状：per-service tag、选择性 host deploy 与 Contract 兼容门禁已经存在；尚未积累真实混合版本并存和独立回滚证据。

触发条件：随真实发布自然积累。破坏性 Contract 变更仍要求 reactor 协同升级；门禁说明见 [`CONTRACT_COMPAT_GATE.md`](CONTRACT_COMPAT_GATE.md)。

## CLOSED

下列历史 Finding 已有代码、配置或可执行门禁承接，不应在其他文档重复维护正文：

| 历史问题 | 当前承接证据 |
| --- | --- |
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

## ACCEPTED

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
