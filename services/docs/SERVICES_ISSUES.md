# `services/` 问题清单

更新时间：2026-09-04

本文件是 `services/` 微服务架构问题、评审 Finding、修复状态与可选外部运行触发条件的唯一入口。其他文档只能链接到本文件，不得复制问题正文或维护第二份状态。

实现与可执行配置始终优先；事实冲突时按以下顺序核实：Java source/tests → Maven POM → application config → Compose → startup/deploy scripts → 本文件。

状态定义：

- `OPEN`：当前代码或配置仍存在，且可在仓库范围内继续收敛。
- `DEFERRED`：问题存在，但必须等明确的运行指标、生产环境或外部授权触发。
- `CLOSED`：修复机制已落地；后续回归应由测试或可执行门禁阻止。
- `ACCEPTED`：已裁决为当前阶段可接受的取舍，不应重复报为缺陷。
- `OPTIONAL_PROFILE`：仓库提供可选实现或配置面，但它不是本项目默认开发验收的一部分。
- `OUT_OF_SCOPE`：只适用于未来部署方的生产、合规或运营证据，不计入本开源仓库当前完成度。
- `BLOCKED_EXTERNAL`：仅用于已经明确要求执行、但当前缺少 endpoint、凭据、证书或部署权限的外部验证；可选能力不应仅因未启用而标记为此状态。

## 当前结论

当前默认拓扑为五个 Data Owner（Auth、Admin、App、Submission、Notification）
与两个 Worker（Judge、Search）。`backend-core` 是新增的 opt-in parent profile：
它显式组装 Owner child contexts，不改变默认发布拓扑，也不取得业务表写入权。
服务边界、Submission 单写者、Contract 收敛、验证层级、DevStack 场景化、
Admin 用户详情深 Module、协调发布控制面和 App interface locality 已闭环；
Core 的边界与未完成门禁见 SVC-025。

项目当前没有生产环境，是正在开发的开源项目。仓库内的生产 profile 只描述安全边界；凡是可复现的运行行为统一使用短时、隔离、可销毁的 disposable 模拟环境验证，不把模拟结果写成生产证据。不为形式上的“企业级”提前引入 Kubernetes、Service Mesh、新 MQ 或分布式事务框架。

本轮拓扑、Contract 和深 Module 整改的唯一任务计划见[`docs/architecture/plans/ulticode-topology-contract-module-convergence-plan.md`](../../docs/architecture/plans/ulticode-topology-contract-module-convergence-plan.md)。该计划不会把本文件的 issue 状态复制成第二份任务账本；本文件继续只承载当前 issue 状态和 SVC-025 的关闭条件。

## OPEN

Core profile 仍有一个仓库可执行 OPEN 项：SVC-025 的完整 local Adapter parity、
同进程业务路由和启用 Owner 的 disposable journey 尚未闭环；不得切换默认拓扑。

### SVC-025 Core profile local parity（OPEN）

现状：`services/core` 已提供显式 Core parent、五组 Owner 数据源/事务/
MapperScan、非 Web Owner child contexts、9108 readiness 和独立 Judge
classpath 边界。G1/G2、parent smoke、readiness fail-closed 和本地断言
载体已有仓库证据；Auth direct-permission 是当前唯一实现的 Core local
Adapter。Admin/App/Submission/Notification 其余跨 Owner consumer 仍使用
Dubbo Adapter，Core 尚未提供完整业务 HTTP 路由和启用 Owner 的 disposable
journey，因此不能宣称 Core 与 distributed profile 同构。

证据：[`services/core`](../core/)、[`core-profile-contract.sh`](../../scripts/test/core-profile-contract.sh)、
[`CoreApplicationSmokeTest`](../core/src/test/java/com/ulticode/core/CoreApplicationSmokeTest.java)、
[`LocalDelegationAssertionContext`](../platform/common/src/main/java/com/ulticode/common/security/LocalDelegationAssertionContext.java)。

运行时证据（2026-09-04）：启用 Owner child contexts 的 exec-jar smoke
（`CORE_OWNER_CONTEXTS_ENABLED=true`，无基础设施）显示六个 child context
全部在 bean 装配阶段失败——早于任何 DB/Redis 交互，与基础设施无关。
根因是六个 Owner jar 处于同一 classpath，而 child 的包扫描根
（`com.ulticode.common`、`com.ulticode.modules.event.inbox`、
`com.ulticode.modules.submission.*`、`com.ulticode.modules.notification.*`
等）在多个 jar 中同时存在：Auth/Search child 拉入
`backend-admin` 的 `DefaultAuditRecorder`（缺 `AuditSinkPort`）；Admin
child 拉入 `backend-app-web` 的 `SubmissionJudgedInboxBridge`（缺
Achievement consumer）；Submission child 把 `BackendSubmissionApplication`
（位于被扫描的 `com.ulticode.submission` 根下）作为配置类注册，产生
重复 mapper 跳过与跨 jar `SubmissionUserReadPort` 歧义；
App/Notification child 同类泄漏。包扫描排除无法根治（同包跨 jar 类只能
按类型逐一排除），因此启用 Owner 的 journey 需先实现按 Owner jar 隔离的
类加载（或等价隔离），仅排除启动类不够。parent boot、DISABLED/FAILED
fail-closed readiness（503）与 Judge `OPTIONAL` 判定均已 smoke 验证。
`CoreOwnerContextManager` 现在对每个 child 启动使用有界 timeout，且
timeout/cancel 与 child 启动完成之间通过单 CAS ownership handoff 协议
交接：每个已创建的 child context 必然由 startAll 调用方、timeout 关闭
路径或迟到完成的 callable 三者之一唯一接管，close 至多一次（此前
expired-boolean 检查存在"已创建 context 无人接管"的丢结果窗口，已由
`CoreOwnerContextManagerLifecycleTest` 的确定性交错回归覆盖：正常发布、
超时 claim 后迟到完成、lost-result 超时、中断交接、取消中断观察、
停止与发布并发，并断言 startup/slot executor 线程退出与 FAILED 不变为
READY）。parent 关闭期间的 stopping 守卫继续阻止 queued/running
startup 创建后续 child；数据 Owner child 的 DB URL/credentials 与
Redis host/password 仍强制非空（缺失即 fail closed，Search child 不注入
datasource 属性）。

关闭条件：为每个实际跨 Owner consumer 提供 owner-local Adapter 或明确
保留 Dubbo 的理由；先实现并证明 child context 的按 Owner jar 类加载隔离
（或等价机制），再完成同进程 HTTP/WS 路由、Auth/App/Submission/
Notification contract parity、启用所有 Owner 的 disposable smoke，并由
Core profile gate 与 distributed profile 对比验证。外部 Judge remote TLS、
生产 HA 和真实流量属于当前项目范围外，不阻塞本开源仓库的开发验收。


## CLOSED

### SVC-019 Admin 用户详情深 Module（CLOSED）

现状：Admin 用户详情由单一 `AdminUserDetailQuery` 深 Module 负责 Auth account 权威判定、授权快照、App profile/solution facts 与 Submission 单次 stats snapshot，并显式表达 `OK/PARTIAL/UNAVAILABLE`。详情读取失败不会被伪装成空权限成功值；权限写入已经独立到 SVC-024 的 Auth delta Seam。

证据：[`AdminUserDetailQuery.java`](../admin/src/main/java/com/ulticode/modules/admin/query/AdminUserDetailQuery.java)、[`DefaultAdminUserDetailQuery.java`](../admin/src/main/java/com/ulticode/modules/admin/query/DefaultAdminUserDetailQuery.java)、[`AdminUserVO.java`](../admin/src/main/java/com/ulticode/modules/admin/dto/AdminUserVO.java)、[`gate-admin-rpc-budget.sh`](../../scripts/test/gate-admin-rpc-budget.sh)；回归见 `AdminUserDetailQueryTest`。

### SVC-020 后端零基础设施 unit allowlist（U-03）（CLOSED）

修复（2026-09-03）：根 `services/pom.xml` 新增 `<id>unit</id>` Surefire profile，按命名契约排除 `*IT`、`*IntegrationTest` 及嵌套类（`**/*IT$*.java` 等）；`scripts/dev/test.sh unit` 不再传 `-Dtest` 选择器（显式选择器会覆盖 profile 排除并可拉回 Testcontainers 套件），并在调用 Maven 前剥离 `SPRING_PROFILES_ACTIVE`/DB/Redis/Nacos/Meili 凭据（deny 环境）。实证分类：deny 环境全 reactor `mvnw test -Punit` = 5786 测试、零失败/错误、零 Testcontainers/Ryuk 初始化、零 IT/IntegrationTest 类执行（日志核验）；`test.sh unit` 在 deny-shim PATH 下由 `scripts/test/zero-infra-validation-contract.sh` 的 unit deny 阶段自证（含负样本与 tracked-diff 检查）。`*IntegrationTest` 类（如 `JudgeStreamRedisIntegrationTest`）为 guard-skip 的外部服务套件，保留于 full/integration 门禁。

### SVC-021 GitLab runner 使用 authority（CLOSED）

决策已执行：`.gitlab-ci.yml` 旧直连部署 job 于 2026-09-03 退役禁用（`stages: []`，无 reset/build/up 路径），U-01 的 fail-closed 分支（无 active 证据 → 禁用）已完成；任何保留路径必须消费 canonical preflight/descriptor，`deployment-integrity.sh describe/verify-registry` 为只读现状入口。仓库内无 runner 仍被外部使用的证据（属部署 authority 的外部事实，当前项目范围外，不计为开源仓库缺陷）。

重开条件：部署 authority 提供 runner/pipeline 使用证据；有 active 使用则只能通过 canonical 控制面接回，不得恢复 reset/build/up 捷径。

### SVC-022 dev-lite 默认 journey 决策（CLOSED）

P2-DEVLITE-005 的计划退出分支为“默认 journey 通过 → flip；否则保留现默认并只提供 scoped 入口”——后者已执行：`dev-lite` 兼容默认保留，`app-journey`/`admin`/`submission-judge`/`search`/`full-stack` 为规范入口，lifecycle 五操作消费同一 resolver（`devlite-minimal-contract.sh`/`devstack-control-contract.sh` 锁定），`up.sh --only` 子集不再触发 exact-set 收敛。U-02 的 flip 分支属可选路径，未验证即不翻转（保持默认）。

重开条件：贡献者常用 journey 迁移证据出现（disposable app-journey smoke 或使用统计）时按 P2-001 场景矩阵重裁默认 scope。

### SVC-023 Forum/Solution 内部 Module pilot 决策（CLOSED/NO-GO）

条件式 pilot 的判定已完成（U-04）：无真实业务/缺陷变更触发，准入 scorecard（`AppModuleSplitAdmissionGateTest`：deletion test、consumer、事务/数据、依赖方向、测试面、真实变更）必备维度未满足 → NO-GO。App-only seams 已迁入对应私有 Module/内部包，并由 `api-contract-boundary-contract.sh` 锁定唯一权威位置。

重开条件：下一次真实 Forum/Solution 业务或缺陷变更发生时重跑 scorecard；任一必备维度失败即继续 NO-GO。
### SVC-024 Auth direct-permission delta mutation（CLOSED）

Admin 权限写入现在通过 Auth-owned `AuthorizationMutationService.mutatePermission(PermissionMutationCommand)`；角色编辑通过独立的 `RoleMutationService.changeRole(ChangeRoleCommand)`。Auth 在 receipt 事务中验证账号、expectedVersion、认证 actor 和幂等键，只改 direct `user_permissions`，保留 granted provenance/expiry，并在语义变更后执行 `authz_version` CAS、审计 outbox 与事件记录。角色继承权限不会被全量物化或 revoke。

证据：[`PermissionMutationCommand.java`](../api/auth-api/src/main/java/com/ulticode/auth/api/command/PermissionMutationCommand.java)、[`DefaultAuthorizationMutationWorkflow.java`](../auth/src/main/java/com/ulticode/auth/authorization/DefaultAuthorizationMutationWorkflow.java)、[`AuthorizationMutationProvider.java`](../auth/src/main/java/com/ulticode/auth/dubbo/provider/AuthorizationMutationProvider.java)、[`UserPermissionServiceImpl.java`](../admin/src/main/java/com/ulticode/modules/admin/service/impl/UserPermissionServiceImpl.java)；回归见 `DefaultAuthorizationMutationWorkflowTest`、`AuthorizationMutationProviderTest` 与 `UserPermissionServiceImplTest`。

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

### SVC-007 生产多主机 HA（OUT_OF_SCOPE）

现状：生产 Compose 是单机 reference topology；base/prod/HA 不使用固定 `container_name`，MySQL、Redis、MeiliSearch 默认仍是单点，Nacos 的 cluster profile 需要外部节点与故障演练。HA Compose 不等于已完成生产 failover；本仓库没有生产环境，因此该证据不属于当前开源开发验收。

触发条件：出现真实多节点生产环境、明确可用性 SLO，且单机维护窗口不再可接受。届时再实施无状态多副本、反向代理和有状态组件 HA。

### SVC-008 可观测的 Judge 节点隔离（OUT_OF_SCOPE）

现状：生产 Compose 已禁止 `docker.sock`、`DOCKER_GID` 与本机 socket fallback，要求 `JUDGE_DOCKER_HOST` 指向专用 remote/rootless Docker daemon、`DOCKER_TLS_VERIFY=1`、只读 client certificate bundle 与共享 sandbox workspace；开发 socket 仅在显式 `docker-compose.judge-dev.yml --profile judge-socket` 下启用。

触发条件：未来部署方需要真实生产远程 daemon/证书轮换/节点故障演练时，再由部署 authority 提供 endpoint、TLS material、rootless 证明和 shared workspace，并运行 `JUDGE_REMOTE_SMOKE=1`。这不属于当前开源仓库的完成条件。

### SVC-009 可观测运营证据（OPTIONAL_PROFILE）

现状：OTel、Prometheus、Worker SLO 指标、告警规则、Runbook 和故障演练入口已接线；可选 `docker-compose.observability.yml` 提供固定镜像的 Collector、Prometheus、Alertmanager、Grafana、Tempo、Loki overlay，生产 Compose 要求为全部 backend 显式提供外部 OTLP collector 地址。仓库可以验证配置、scrape、规则、路由、dashboard 和 release annotation 接线，但不能替代真实生产 telemetry storage/receiver、阈值调优和 SLO 报表。

触发条件：未来需要真实生产流量、托管 telemetry storage 或 SLO 报表时，由部署方启用该 profile 并执行 HTTP → Dubbo → Redis Streams 链路、积压、PEL、DLQ 与 last-success 恢复演练。当前本地 observability 配置和 disposable contract 已足够，不阻塞本项目开发验收。操作入口见 [`WORKER_SLO_RUNBOOK.md`](WORKER_SLO_RUNBOOK.md)。

### SVC-010 混合版本运行历史（OUT_OF_SCOPE）

现状：per-service version/digest manifest、选择性 host deploy 与 Contract 兼容门禁已经存在；尚未积累真实混合版本并存和独立回滚证据。

触发条件：随真实发布自然积累。破坏性 Contract 变更仍要求 reactor 协同升级；门禁说明见 [`CONTRACT_COMPAT_GATE.md`](CONTRACT_COMPAT_GATE.md)。

### SVC-011 文档收敛残差（DEFERRED）

现状：`docs/` 经 `P5-GATE-001` 前的 `182 .md` 扫描已分流（`docs 100` `services/docs 8` 等），`CONTEXT.md` 的 `Submission intake` 已由 `DefaultSubmissionWritePort` 更正为 `SubmissionIntakePort/RemoteSubmissionWritePort`，`docs/archive/contest/README.md` 的“现行”已更正为“历史归档”。以下历史快照仍保留旧表述，属有意冻结的证据，不视为现行运营真理：

- `docs/architecture/decisions/0007-legacy-compatibility-lifecycle.md` 正文仍保留 `legacy-rollback`/`App Judge adapter` 的 `P4 前` 基线描述，顶部 `Amendment 2026-09-02` 已声明 `006..011 DONE` 且当前二进制 `fail closed`
- `P0-BASELINE-001..005`、`P2-APP-001/002/004`、`P5-GATE-001` 为 `c344` 前冻结基线，已由 `P4-LEGACY-005/011` 与 remediation closure history 覆盖（不再引用易漂移计数）
- `P1-INFRA-001` 中 `legacy queue` 行、`P4-LEGACY-005` 的 `remains until P4-010` 文案、`P5-GATE-004` 的 `status` 与 `current-status` 的 `1 composite FAIL + 单跑 PASS` 差异均为历史快照时态
- `docs/archive/RUNBOOK.md`、`PRIVACY.md`、`DOCS-SPEC.md`、`ADR` 中 `backend-spring/console` 旧路径为归档设计态，非现行 `services/app`/`apps/console` 运营路径

触发条件：`P5-GATE-001` 最终基线刷新（`evidence` 重建）时统一替换上述冻结快照；触发前以 `Amendment` 与本条目为准，不重复报为缺陷。`CONTEXT.md` 与 `archive/contest/README` 的 `1 行` 现行性修正已随 `c0f79f2`/`68cbbdc` 落地，无需再触发。

### SVC-026 Meilisearch Cloud 试点（OPTIONAL_PROFILE）

现状：Search Worker 已通过小型 `SearchIndex` Seam 使用 Meili Adapter；本地与未来 hosted endpoint 共用该 Adapter，索引仍是可由 Owner 事实重建的派生数据（`meilisearch-recovery-contract.sh` 锁定重建语义）。没有已测搜索负载、Cloud 实例或在线 demo 证据。

触发条件：只有在项目需要公开 demo、真实持续搜索负载或明确托管搜索时才启用；部署方提供 Cloud 项目与凭据。接入必须保留本地 Meili 与数据库 fallback，索引仍从 Owner 事实重建，禁止把 Cloud 当作唯一事实源。未启用不计为当前仓库缺陷。

### SVC-027 Judge0 试点（OPTIONAL_PROFILE）

现状：Judge-owned `JudgeRunService` 现在同时提供同步 `execute` 与受约束的异步 `submit/poll/cancel`；runtime 通过 `AsyncSandboxExecutor` Seam 提供默认 `DockerAsyncSandboxAdapter`，可选 `Judge0AsyncSandboxAdapter`。两者都只接受 `PUBLIC_PREVIEW`，保留 bounded timeout、输出上限与进程内幂等 receipt；App `/run` 当前继续走同步 `execute`，runtime validation failure 在 provider 处映射为 typed 400。

Judge0 仍默认关闭，当前没有 endpoint/凭据或真实实例，因此没有外部执行、配额、回调认证和混合版本 drain 证据；生产路径仍是本地 Docker Adapter。Judge0 的供应商 status、language ID、polling 和 token 仅停在 Adapter 内，私有/隐藏测试在进入外部 Adapter 前拒绝。跨 Judge 副本与重启的幂等尚未实现，启用前必须补 durable receipt 或明确单实例路由边界。

触发条件：只有在项目需要公开、非敏感 preview 或 shadow execution 时才启用；部署方提供 endpoint/凭据后，再运行 HTTPS/TLS、语言映射、资源限制、结果语义、回调认证（若启用 webhook）、幂等、配额、取消/超时、回滚和 mixed-version drain 验证。默认关闭且未配置不阻塞当前开源开发验收；不得宣称已接入生产。

### SVC-028 托管 MySQL / Redis / Valkey（OPTIONAL_PROFILE）

现状：自托管默认可用；Owner JDBC URL、`sslMode`、Redis `rediss`/SSL bundle/hostname verification 均有配置入口，静态 TLS contract 已覆盖每个 Owner；Auth 的 Redisson locks/OAuth state 另需 `rediss://` URL、hostname verification 和专用 truststore；公网托管实例、最小权限 ACL 复核与 dump/restore/Flyway checksum 恢复演练尚未完成。

触发条件：只有在项目需要托管数据层时才启用；部署方提供实例后，先完成 JDBC TLS + CA/hostname 验证、Redis TLS/CA/主机名校验、Auth Redisson truststore 验证、每 Owner 独立账号与最小权限 ACL、Streams/PEL/Lua/lease/session/OAuth state 语义验证、dump/restore 与 Flyway checksum 验证，明确 RPO/RTO 并完成恢复演练。默认自托管路径不受影响，未启用不计为当前仓库缺陷。

## CLOSED (historical findings)

下列历史 Finding 已有代码、配置或可执行门禁承接，不应在其他文档重复维护正文：

| 历史问题 | 当前承接证据 |
| --- | --- |
| SVC-001 App 直接复用 Judge Docker 执行实现 | `/run` 通过 App-private `InteractiveCodeRunner` → Judge-owned `JudgeRunService`；Judge runtime 只使用 `SandboxExecutor` 与 runtime-private request/result model，App 不加载 sandbox implementation；provider absent 时映射 HTTP 503/code 30022 |
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
| 验证入口层级与 `quick` 语义漂移 | `test.sh static/unit/quick/full-local/full/integration` 六模式 + `--describe`；`quick` 弃用别名映射 static+unit；零基础设施 deny-shim 自证 `scripts/test/zero-infra-validation-contract.sh`；后端零基础设施 unit 门禁由根 POM `unit` profile + deny 环境实证关闭（见 CLOSED SVC-020），`unit` 不再 fail closed |
| DevStack 服务集合漂移 | `devstack-manifest.sh` 场景 resolver（apps/infra/readiness/ports/features）；up/stop/status/logs/health/doctor 消费同一集合；显式 Compose targets，Search off 不启动 Meili；契约见 `devlite-minimal-contract.sh`/`devstack-control-contract.sh` |
| 旧 GitLab 直连部署控制面 | `.gitlab-ci.yml` 退役禁用（无 reset/build/up 路径）；canonical 控制面唯一；只读 `deployment-integrity.sh describe` + matrix/Compose `verify-registry`；runner authority 见 CLOSED SVC-021 |
| app-api 收纳纯 App 内部 interface | App-only seam 迁入对应私有 Module/内部包；`api-contract-boundary-contract.sh` 校验单一权威位置；跨 Owner contract 仍留在 `app-api` |
| Auth `RoleTemplateService` contract 无消费者 | Admin 详情切至 `AuthorizationSnapshotService` 后无剩余 consumer；Auth provider、测试与 contract interface 已删除 |
| 可选外部 Adapter 配置面（S3/R2、SMTP、OTLP） | App `FileStoragePort` 已有 LocalStorage（自托管默认）与 S3Storage，`APP_STORAGE_S3_*` 只配置 endpoint/bucket/TLS/凭据；Notification `SmtpSenderPort` 保留 Logging 与 JavaMail 两个 Adapter；OTLP 仅标准 endpoint/headers/sampling 配置，`platform/observability` 在配置 authorization 时 fail closed 拒绝非 HTTPS，可选 `docker-compose.observability-managed.yml` 从 secret/env 读 endpoint。Meilisearch Cloud、Judge0、托管数据层分别见 OPTIONAL_PROFILE SVC-026/027/028 |

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
