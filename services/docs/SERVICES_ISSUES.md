# `services/` 问题清单

更新时间：2026-09-03

本文件是 `services/` 微服务架构问题、评审 Finding、修复状态与可选外部运行触发条件的唯一入口。其他文档只能链接到本文件，不得复制问题正文或维护第二份状态。

实现与可执行配置始终优先；事实冲突时按以下顺序核实：Java source/tests → Maven POM → application config → Compose → startup/deploy scripts → 本文件。

状态定义：

- `OPEN`：当前代码或配置仍存在，且可在仓库范围内继续收敛。
- `DEFERRED`：问题存在，但必须等明确的运行指标、生产环境或外部授权触发。
- `CLOSED`：修复机制已落地；后续回归应由测试或可执行门禁阻止。
- `ACCEPTED`：已裁决为当前阶段可接受的取舍，不应重复报为缺陷。

## 当前结论

当前拓扑为五个 Data Owner（Auth、Admin、App、Submission、Notification）与两个 Worker（Judge、Search）。仓库范围内的服务边界、Submission 单写者、Contract 收敛、验证层级（`static/unit/quick/full-local/full/integration`）、DevStack 场景化（`--scope` + 统一 lifecycle resolver）、Admin 用户详情深 Module（`AdminUserDetailQuery`，权限写 fail closed、Submission 单次 snapshot）、协调发布控制面（matrix 分类、`describe`、GitLab 直连路径退役）与 App interface locality（10 个 App-only interface 移出 `app-api`）已闭环；后端零基础设施 unit 门禁（SVC-020/U-03）已以 `-Punit` profile + deny 环境实证关闭，剩余 DEFERRED 项触发条件明确（见下）。

项目当前没有生产环境，是正在开发的开源项目。仓库内的生产 profile 只描述安全边界；凡是可复现的运行行为统一使用短时、隔离、可销毁的 disposable 模拟环境验证，不把模拟结果写成生产证据。不为形式上的“企业级”提前引入 Kubernetes、Service Mesh、新 MQ 或分布式事务框架。

## OPEN

当前没有剩余的仓库可执行 OPEN 项。


## CLOSED

### SVC-019 Admin 用户详情权限丢失与空集回写风险（CLOSED）

现状：Admin 用户详情路径原在权限读取失败（null/transport/exception）时把 permissions 当作空集返回，写路径可将空集构造成全量替换 `ChangeAuthorizationCommand`，造成授权数据丢失；详情聚合对 Submission 逐项三次 RPC 且 nullable 伪装为 0。现已收口为单 use-case `AdminUserDetailQuery` 深 Module：第一轮 Auth account 权威判定（不可用 → UNAVAILABLE），第二轮在有界 executor 与共同 wall budget 内并行获取 Auth `AuthorizationSnapshotService`（删除冗余 RoleTemplate RPC）、App profile/solution facts 与 Submission 单次 stats snapshot（`SubmissionUserDetailStatsPort`，一次返回 submission count/accepted count/streak）；顶层 `OK/PARTIAL/UNAVAILABLE` + 区块状态/原因；权限写前要求 permission section=OK 且 snapshot 版本完整，读取失败零写入，proven-empty grant 与 proven-absent revoke no-op 保持合法；旧 `AdminUserStatsReadPort`/`AdminUserStatsReadAdapter` 及其测试已删除（全仓库无 consumer）。REST wire shape 加性扩展，Management `User` 类型与详情 UI 消费区块状态，权限写控件在 permission section 不可证明时禁用。

证据：[`AdminUserDetailQuery.java`](../admin/src/main/java/com/ulticode/modules/admin/query/AdminUserDetailQuery.java)、[`DefaultAdminUserDetailQuery.java`](../admin/src/main/java/com/ulticode/modules/admin/query/DefaultAdminUserDetailQuery.java)、[`UserPermissionServiceImpl.java`](../admin/src/main/java/com/ulticode/modules/admin/service/impl/UserPermissionServiceImpl.java)、[`AdminUserVO.java`](../admin/src/main/java/com/ulticode/modules/admin/dto/AdminUserVO.java)、[`SubmissionUserDetailStatsPort.java`](../api/submission-api/src/main/java/com/ulticode/submission/api/service/SubmissionUserDetailStatsPort.java)、[`gate-admin-rpc-budget.sh`](../../scripts/test/gate-admin-rpc-budget.sh)；回归见 `AdminUserDetailQueryTest`/`UserPermissionServiceImplTest`/`AdminUserProjectionTest`（权限 null/failure/exception 零写入断言）。事件化用户读模型仍是 No-Go（触发条件见 SVC-006）。残余验证缺口：optional 扇出在 executor 拒绝时取消兄弟任务的查询级回归测试无法从外部确定性构造（account 与 optional 共用同一 executor，worker 启动竞态使预占不可复现）；生产实现按 allOf 语义在任何子任务异常时即抛并进入取消分支，executor 级拒绝/取消语义由 `CancellableQueryExecutorCrTest` 覆盖。

### SVC-020 后端零基础设施 unit allowlist（U-03）（CLOSED）

修复（2026-09-03）：根 `services/pom.xml` 新增 `<id>unit</id>` Surefire profile，按命名契约排除 `*IT`、`*IntegrationTest` 及嵌套类（`**/*IT$*.java` 等）；`scripts/dev/test.sh unit` 不再传 `-Dtest` 选择器（显式选择器会覆盖 profile 排除并可拉回 Testcontainers 套件），并在调用 Maven 前剥离 `SPRING_PROFILES_ACTIVE`/DB/Redis/Nacos/Meili 凭据（deny 环境）。实证分类：deny 环境全 reactor `mvnw test -Punit` = 5786 测试、零失败/错误、零 Testcontainers/Ryuk 初始化、零 IT/IntegrationTest 类执行（日志核验）；`test.sh unit` 在 deny-shim PATH 下由 `scripts/test/zero-infra-validation-contract.sh` 的 unit deny 阶段自证（含负样本与 tracked-diff 检查）。`*IntegrationTest` 类（如 `JudgeStreamRedisIntegrationTest`）为 guard-skip 的外部服务套件，保留于 full/integration 门禁。

### SVC-021 GitLab runner 使用 authority（CLOSED）

决策已执行：`.gitlab-ci.yml` 旧直连部署 job 于 2026-09-03 退役禁用（`stages: []`，无 reset/build/up 路径），U-01 的 fail-closed 分支（无 active 证据 → 禁用）已完成；任何保留路径必须消费 canonical preflight/descriptor，`deployment-integrity.sh describe/verify-registry` 为只读现状入口。仓库内无 runner 仍被外部使用的证据（属部署 authority 的外部事实，非仓库可收敛项，记为 `BLOCKED_EXTERNAL` 备注而非缺陷）。

重开条件：部署 authority 提供 runner/pipeline 使用证据；有 active 使用则只能通过 canonical 控制面接回，不得恢复 reset/build/up 捷径。

### SVC-022 dev-lite 默认 journey 决策（CLOSED）

P2-DEVLITE-005 的计划退出分支为“默认 journey 通过 → flip；否则保留现默认并只提供 scoped 入口”——后者已执行：`dev-lite` 兼容默认保留，`app-journey`/`admin`/`submission-judge`/`search`/`full-stack` 为规范入口，lifecycle 五操作消费同一 resolver（`devlite-minimal-contract.sh`/`devstack-control-contract.sh` 锁定），`up.sh --only` 子集不再触发 exact-set 收敛。U-02 的 flip 分支属可选路径，未验证即不翻转（保持默认）。

重开条件：贡献者常用 journey 迁移证据出现（disposable app-journey smoke 或使用统计）时按 P2-001 场景矩阵重裁默认 scope。

### SVC-023 Forum/Solution 内部 Module pilot 决策（CLOSED/NO-GO）

条件式 pilot 的判定已完成（U-04）：无真实业务/缺陷变更触发，准入 scorecard（`AppModuleSplitAdmissionGateTest`：deletion test、consumer、事务/数据、依赖方向、测试面、真实变更）必备维度未满足 → NO-GO。App locality 部分已收敛：71-interface consumer catalog（45 cross-process/26 internal），10 个 App-only seam 迁入私有 Module/内部包并由 `api-contract-boundary-contract.sh` 锁定单一权威位置。

重开条件：下一次真实 Forum/Solution 业务或缺陷变更发生时重跑 scorecard；任一必备维度失败即继续 NO-GO。

### SVC-003 P1 Submission ownership contraction (CLOSED)

Ordinary and contest intake now always use App's `RemoteSubmissionWritePort` and execute in `backend-submission`. The App-local writer, mutation router, fence adapters, judge/result dispatchers, shadow comparator, and lease reaper are deleted; write ownership is no longer selected by `APP_SUBMISSION_ROUTING_MODE`.

P1-SUB-004 now moves reconciliation to Submission-owned bounded full/incremental facts: Admin calls the `backend-submission` provider, and App no longer issues reconciliation SQL against `submissions`. P1-DATA-001 also routes normal user/contest/admin/statistics/generation reads through Submission-owner facts; App Submission projections, mapper access, private persistence, and Judge runtime compile dependency are now removed. The current binary and DevStack reject the former local compatibility mode; production rollback uses the deployment-owned previous release descriptor. The repository proof and authorized disposable migration/backfill/cutover/rollback rehearsal are complete. This repository has no production registry or traffic plane; a virtual 14-day compatibility ledger is explicitly not production evidence.

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
| Intake/outbox/fence | App mutation implementation deleted; owner tests, duplicate-writer gate, and disposable cutover rehearsal are authoritative | No deployed production plane exists in this repository; future adopters must perform their own rollout validation |
| Admin rejudge | Admin compatibility service/provider deleted; Admin sends authenticated commands to the owner; owner receipt, generation CAS, lease expiry, and judge outbox tests are authoritative | Repository-only and disposable owner verification is complete; no production traffic claim is made |
| User reads | Normal user/contest/admin/statistics/generation reads use bounded Submission-owner facts; local adapters are rollback-only | Disposable parity/checksum and rollback rehearsal is complete; production migration is outside this repository's scope |

The repository-side SVC-003 gate is closed by source inventory, major-version contract retirement, virtual drain/error-budget evidence, and disposable owner migration proof. No external production state is inferred.

## DEFERRED

### SVC-006 Admin 事件化用户读模型

现状：跨 Owner 用户聚合已收敛到一个深 Module：`AdminUserEnricher` 统一查询 Auth identity/account、批量合并 App profile 并表达 `OK/PARTIAL/UNAVAILABLE`；`DefaultAdminUserProjection` 只负责 VO、权限与本地统计。静默空列表问题和两套聚合逻辑均已修复，但该同步 Module 仍承担两个 Provider 的 freshness 和可用性组合。

证据：[`DefaultAdminUserProjection.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/DefaultAdminUserProjection.java)、[`AdminUserEnricher.java`](../admin/src/main/java/com/ulticode/modules/admin/projection/AdminUserEnricher.java)。

触发条件：Admin 用户列表延迟明确归因于跨 Owner RPC、Owner 故障使管理面不可用超出目标，或 RPC 补偿成本高于本地 projection 维护成本。深 Module 前置条件已完成；触发前不建设事件表。

### SVC-007 生产多主机 HA

现状：生产 Compose 是单机 reference topology；base/prod/HA 不使用固定 `container_name`，MySQL、Redis、MeiliSearch 默认仍是单点，Nacos 的 cluster profile 需要外部节点与故障演练。HA Compose 不等于已完成生产 failover。

触发条件：出现真实多节点生产环境、明确可用性 SLO，且单机维护窗口不再可接受。届时再实施无状态多副本、反向代理和有状态组件 HA。

### SVC-008 可观测的 Judge 节点隔离

现状：生产 Compose 已禁止 `docker.sock`、`DOCKER_GID` 与本机 socket fallback，要求 `JUDGE_DOCKER_HOST` 指向专用 remote/rootless Docker daemon、`DOCKER_TLS_VERIFY=1`、只读 client certificate bundle 与共享 sandbox workspace；开发 socket 仅在显式 `docker-compose.judge-dev.yml --profile judge-socket` 下启用。

触发条件：需要真实生产远程 daemon/证书轮换/节点故障演练时，由部署 authority 提供 endpoint、TLS material、rootless 证明和 shared workspace，并运行 `JUDGE_REMOTE_SMOKE=1`。仓库只证明配置边界、沙箱约束和 disposable contract，不替代生产节点授权。

### SVC-009 可观测运营证据

现状：OTel、Prometheus、Worker SLO 指标、告警规则、Runbook 和故障演练入口已接线；可选 `docker-compose.observability.yml` 提供固定镜像的 Collector、Prometheus、Alertmanager、Grafana、Tempo、Loki overlay，生产 Compose 要求为全部 backend 显式提供外部 OTLP collector 地址。仓库可以验证配置、scrape、规则、路由、dashboard 和 release annotation 接线，但不能替代真实生产 telemetry storage/receiver、阈值调优和 SLO 报表。

触发条件：首次真实生产流量可用于 HTTP → Dubbo → Redis Streams 链路验证，并能执行积压、PEL、DLQ 与 last-success 恢复演练。操作入口见 [`WORKER_SLO_RUNBOOK.md`](WORKER_SLO_RUNBOOK.md)。

### SVC-010 混合版本运行历史

现状：per-service version/digest manifest、选择性 host deploy 与 Contract 兼容门禁已经存在；尚未积累真实混合版本并存和独立回滚证据。

触发条件：随真实发布自然积累。破坏性 Contract 变更仍要求 reactor 协同升级；门禁说明见 [`CONTRACT_COMPAT_GATE.md`](CONTRACT_COMPAT_GATE.md)。

### SVC-011 文档收敛残差（DEFERRED）

现状：`docs/` 经 `P5-GATE-001` 前的 `182 .md` 扫描已分流（`docs 100` `services/docs 8` 等），`CONTEXT.md` 的 `Submission intake` 已由 `DefaultSubmissionWritePort` 更正为 `SubmissionIntakePort/RemoteSubmissionWritePort`，`docs/archive/contest/README.md` 的“现行”已更正为“历史归档”。以下历史快照仍保留旧表述，属有意冻结的证据，不视为现行运营真理：

- `docs/architecture/decisions/0007-legacy-compatibility-lifecycle.md` 正文仍保留 `legacy-rollback`/`App Judge adapter` 的 `P4 前` 基线描述，顶部 `Amendment 2026-09-02` 已声明 `006..011 DONE` 且当前二进制 `fail closed`
- `P0-BASELINE-001..005`、`P2-APP-001/002/004`、`P5-GATE-001` 为 `c344` 前冻结基线，已由 `P4-LEGACY-005/011` 与 remediation closure history 覆盖（不再引用易漂移计数）
- `P1-INFRA-001` 中 `legacy queue` 行、`P4-LEGACY-005` 的 `remains until P4-010` 文案、`P5-GATE-004` 的 `status` 与 `current-status` 的 `1 composite FAIL + 单跑 PASS` 差异均为历史快照时态
- `docs/archive/RUNBOOK.md`、`PRIVACY.md`、`DOCS-SPEC.md`、`ADR` 中 `backend-spring/console` 旧路径为归档设计态，非现行 `services/app`/`apps/console` 运营路径

触发条件：`P5-GATE-001` 最终基线刷新（`evidence` 重建）时统一替换上述冻结快照；触发前以 `Amendment` 与本条目为准，不重复报为缺陷。`CONTEXT.md` 与 `archive/contest/README` 的 `1 行` 现行性修正已随 `c0f79f2`/`68cbbdc` 落地，无需再触发。

## CLOSED (historical findings)

下列历史 Finding 已有代码、配置或可执行门禁承接，不应在其他文档重复维护正文：

| 历史问题 | 当前承接证据 |
| --- | --- |
| SVC-001 App 直接复用 Judge Docker 执行实现 | 正常/生产 `/run` 通过 `CodeExecutionPort` 调用 `backend-judge` provider；真实 HTTP→Dubbo→provider IT 覆盖成功与无 provider 时 HTTP 503/code 30022；当前 App 不包含本地 Judge execution/compatibility poller，旧模式由当前 binary fail closed |
| SVC-002 跨进程 Contract Interface 过宽 | `SubmissionIntakePort`、`SubmissionVerdictWritePort` 与 `ProblemTitleLookupPort` 按消费语义拆分；旧 composite `SubmissionWritePort`/provider 和无消费者的 analytics contract 已在 2.0.0 major contract release 中删除 |
| SVC-005 Search 选择性发布/回滚入口不完整 | Search 已进入 deploy choice、rollback whitelist/all 与共享 `host-health`；架构门禁从 services matrix 解析全部 backend 并逐项校验三个控制面 |
| Owner 假健康 | `ReadinessChecks`、各 Owner readiness controller、Compose/host health |
| Search/Judge 静态健康证明 | `SearchWorkerReadinessHeartbeat`、`JudgeWorkerReadinessHeartbeat` |
| Dubbo timeout/retry 漂移 | `RpcPolicy` 与各消费方 `RpcPolicyArchTest` |
| 非法运行模式组合 | `FlagCombinationValidator` 与 `devstack-manifest.sh` |
| Admin 备份本地易失 | `BACKUP_DIR` 与生产持久卷 |
| Redis 共享口令/跨域 key | runtime `REDIS_ACL_DIR`、`generate-users-acl.sh` 与 `scripts/runbooks/redis-acl-rotation.sh` |
| Admin Owner 故障伪装空数据 | 类型化 `OWNER_QUERY_UNAVAILABLE` 与 `DegradationStatus` |
| Worker Consumer 身份冲突/PEL 接管 | 实例唯一 consumer name、claim/reaper 与 `WorkerSloMeters` |
| tracing/SLO 无代码采集 | OTel/Prometheus 接线、Worker 指标与告警规则 |
| App 头像本地状态无 Seam | `FileStoragePort` 与生产持久卷；对象存储仍按需 |
| Nacos/Judge endpoint 写死 | Nacos cluster 参数与 Judge Docker endpoint/TLS 参数 |
| 服务无法独立标记/发布 | per-service version/digest manifest、host-deploy 子集、Contract 兼容门禁；Search 选择入口残差见 SVC-005 |
| WebSocket 接受 client-controlled STOMP token | CONNECT 仅从 handshake session 读取 cookie token，并有拒绝回归 |
| OAuth callback 缺 state cookie 仍放行 | `OAuthStateModule` 对空 cookie fail closed |
| Audit outbox claim 无并发围栏 | PENDING→PROCESSING CAS claim、lease reclaim、claim-owner fenced completion |
| RBAC 变更无 durable invalidation/version 信号 | `authz_version` 原子递增与 durable `AUTHORIZATION_CHANGED` 记录 |
| Submission 写事务同步回访 App/Auth | request owner 传入不可变 `SubmissionFactsSnapshot` 并 fail closed |
| 游离 `services/com` 编译产物 | 当前 source tree 已清除 |
| Services 问题文档多入口与状态漂移 | 本文件为唯一注册表，当前状态导航见 `docs/project/known-issues.md`；旧的 `PROJECT_DOCUMENTATION.md` 已归档，不再作为入口 |
| SVC-011 | Replay/DLQ HTTP operations are method-protected with `ADMIN|SUPER_ADMIN` and have MockMvc denied-path coverage for every operation |
| SVC-012 | Admin delegation assertions are target-audience bound; Auth, App profile/problem/contest/list/submission writes reject missing or invalid trust; `changePassword` is self-service only (USER in-process via current-password check, admin self-change via verified assertion) |
| SVC-013 | Production Compose requires RS256/JWKS, dedicated internal-delegation secret, explicit Dubbo namespace, and a least-privilege Nacos DB account |
| SVC-014 | Replay/DLQ mutations use set-based SQL updates/deletes instead of unbounded read-plus-N+1 writes; every mutation is bounded by a per-statement row LIMIT |
| SVC-015 | Notification explicit-recipient resolution uses one Auth batch read and no longer routes through an App provider hop |
| SVC-016 | Search and SubmissionJudged inbox bridges reject events with unsupported owner tags before durable handling |
| SVC-017 | Search versioned index operations use a per-index/document Redis lease; stale/equal DELETE and lock-contention regressions are covered by worker tests; contention defers events without inflating delivery counts (no valid-event dead-lettering), and the ledger write is guarded by a lease-ownership re-check |
| SVC-018 | Bootstrap provisioning uses a dedicated `BOOTSTRAP` actor, scoped assertion secret, explicit runner gate, provider operation allowlist, fail-closed Auth query preflights, and propagated restore RPC failures; the administrator count is role-filtered (`ADMIN`+`SUPER_ADMIN`), not an all-account count |
| 验证入口层级与 `quick` 语义漂移 | `test.sh static/unit/quick/full-local/full/integration` 六模式 + `--describe`；`quick` 弃用别名映射 static+unit；零基础设施 deny-shim 自证 `scripts/test/zero-infra-validation-contract.sh`；后端 unit 分级未解前 fail closed（见 OPEN SVC-020） |
| DevStack 服务集合漂移 | `devstack-manifest.sh` 场景 resolver（apps/infra/readiness/ports/features）；up/stop/status/logs/health/doctor 消费同一集合；显式 Compose targets，Search off 不启动 Meili；契约见 `devlite-minimal-contract.sh`/`devstack-control-contract.sh` |
| 旧 GitLab 直连部署控制面 | `.gitlab-ci.yml` 退役禁用（无 reset/build/up 路径）；canonical 控制面唯一；只读 `deployment-integrity.sh describe` + matrix/Compose `verify-registry`；runner authority 见 CLOSED SVC-021 |
| app-api 收纳纯 App 内部 interface | 71-interface consumer catalog；10 个 App-only seam 迁入私有 Module/内部包（contest push/subscription、problem 内部 read ports）；`api-contract-boundary-contract.sh` 校验单一权威位置；内部 Module 准入由 `AppModuleSplitAdmissionGateTest` 固守 |
| Auth `RoleTemplateService` provider 无消费者 | Admin 详情切至 `AuthorizationSnapshotService` 后无剩余 consumer；Auth provider 与其测试已删除，contract interface 保留（无 provider 引用） |

## ACCEPTED

- SVC-004 App/Submission 同步读 Seam：写事务只接受不可变 `SubmissionFactsSnapshot`，Problem/User facts 读使用窄 Interface 与 bounded batch，空页跳过 RPC。只有真实延迟/可用性指标证明其为瓶颈时才重开并升级事件 projection。
- Access token 即时黑名单 writer 当前不建设：refresh token hash-only revoke、HTTP ban check、WebSocket 实时 account check 与短期 access token TTL 共同限定窗口；只有产品明确要求即时踢下线时才新增 writer-owned revoke Interface。
- Search `dev-lite=database`、`dev-full=indexed` 是 manifest 的显式策略，不是配置漂移。
- `SubmissionFactsSnapshot` 只允许增加 Owner intake 校验所需的最小字段；字段变化必须通过现有 Contract shape test，避免形成第二套隐式 facts Interface。
- Submission compatibility lifecycle is governed by ADR-0007; the current binary/DevStack reject the former local mode, and production rollback points only to the deployment-owned previous full release descriptor.
- 当前阶段不拆更多进程，不引入新 MQ、Service Mesh、Kubernetes 或 Seata。

## 维护规则

1. 新的 Services 架构问题只在本文件新增稳定 ID；其他文档只能链接该 ID。
2. 状态变更必须同时更新证据与关闭条件；没有执行证据不得标记 `CLOSED`。
3. 已关闭问题保留一行历史索引，不保留临时分支名、工作区快照、文件长度或易漂移计数。
4. 迁移步骤属于迁移指南，运行操作属于 Runbook，Contract 规则属于 Contract 文档；不要把这些正文复制回问题清单。
