# ADR-0010：Core + Judge 收敛门禁证据（默认拓扑未切换）

- 状态：In progress（opt-in Core parent 已实现；local parity 与真实 journey 未闭环）
- 日期：2026-09-04

## Context

ADR-0009 Decision #4 记录了七进程 distributed profile 仍是 reference、Core 合并需先过隔离证据。本轮补充源码级门禁证据：逐项检查 Owner 启动壳、数据源、Mapper 扫描、Dubbo 注入面与运行契约，确定哪些门禁**当前无法在同进程内证明**，供后续 Core 试点直接对照。

## Design It Twice（方案 A vs B）

| 维度 | A：保留七进程，统一入口/验证 | B：Core + Judge |
|---|---|---|
| Module Depth | Owner 逻辑 Module 不变；每进程仍是浅启动壳 + 深领域 | 删除远程中转后 Module 边界更接近纯领域；Local Adapter 增 Depth |
| Interface Locality | 跨 Owner 调用仍经 Dubbo Seam，变更波及 provider+consumer 两侧 | 多数调用收敛为同 Module 内 Seam；测试面同进程 |
| Seam ownership | 已有且经 contract gate 验证 | 需重建"本地 Adapter + 同进程委托断言"新 Seam |
| Adapter 数量 | 保持现状（Dubbo Adapter 为主） | 跨 Owner 的 Dubbo Adapter 需本地替代或双模式并存 |
| 独立发布 Leverage | 名义上可独立发布，无独立发布证据 | 发布单元减少，但需先证单进程可独立演进 |
| 启动资源成本 | 7 JVM + Nacos 注册 + 协调启动 | 2 JVM，预期更低（未实测，不得写成事实） |
| 数据故障域 | 5 个 schema 共享同一 MySQL 单点 | 不变（schema 仍独立，MySQL 仍单点） |
| 回滚复杂度 | 低（现状） | 双 profile 并存期间较高；切默认后靠 release descriptor 回滚 |
| 自托管贡献者体验 | 依赖 Nacos/Dubbo 全栈拉起 | 更低运维面，但需双拓扑测试矩阵 |

裁决：B 是结构上更低维护成本的终态，但**不是已完成的事实**；只有本 ADR 门禁清单全部通过才允许提升为默认。

## 阻塞门禁与源码证据

### G1 Owner 启动壳包扫描（本地已证）

- `services/core/CoreApplication.java` 只扫描 `com.ulticode.core`，并排除
  Core nested boot configuration；`CoreOwnerBootConfigurations` 为 Auth、
  Admin、App、Submission、Notification、Search 声明互斥 child-context
  扫描入口，未把 Owner 启动类交给 Core parent 自动扫描。
- `CoreApplicationSmokeTest` 与 `core-profile-contract.sh` 证明 Core parent
  不实例化 `Backend*Application`，且 Core 启动壳独立存在。G1 仅证明
  parent assembly 的扫描边界，不证明所有 Owner 在真实基础设施上的启动成功。

### G2 五组数据源、SqlSessionFactory 与事务（本地已证）

- `CoreOwnerDataSourceConfiguration` 创建五个独立 Owner `DataSource`、
  `SqlSessionFactory` 和 `DataSourceTransactionManager`；顶层 mapper
  configuration 通过 `sqlSessionFactoryRef` 把 mapper 包路由到对应 schema。
- Smoke test 证明五个数据源和事务管理器不是同一实例，且 Core 不加载
  `backend-judge-runtime`。这证明显式 assembly，不等于已证明每个旧
  `@Transactional` 调用在真实业务 journey 中都选对事务管理器。

### G3 跨 Owner 调用的 local Adapter parity（部分实现）

- Core 已加入 `CoreLocalAuthorizationMutationAdapter`，复用 Auth-owned
  `AuthorizationMutationService` contract，不泄漏 Mapper/Entity；它通过
  Admin signer 为当前认证 actor 生成目标绑定断言，再委托 Auth child context。
- 其余 Admin/App/Submission/Notification consumer 仍是 Dubbo Adapter。故
  G3 不能标为完成；当前实现是可回滚的高风险权限写入试点，不是全量替代。

### G4 同进程委托断言（Auth 路径已实现，整体部分）

- `LocalDelegationAssertionContext` 提供受限 ThreadLocal scope；Auth
  `InternalDelegationAssertionVerifier` 优先验证该 signed assertion，缺失
  时回退到 Dubbo attachment，验证仍由同一 fail-closed support 完成。
- Auth verifier test 证明没有 Dubbo attachment 时 scoped assertion 可以通过；
  空断言、无认证 signer 或 child context 不可用时 local Adapter 返回
  `UNAUTHORIZED`。其余跨 Owner consumer 尚未全部接入此 Seam，整体 G4
  仍为部分实现。

### G5 运行契约（parent 已实现；enabled child wiring 被阻塞）

- Core parent 提供 `9108` 与 `/api/v1/core/health/ready`；`devstack-manifest.sh`
  和 PM2 descriptor 有 `core` scope，启动失败以非 200 readiness 暴露，
  并与独立 `ulticode-judge` 同 scope。
- `CoreOwnerContextManager` 对每个 child context 使用有界 startup
  timeout，超时标记 `FAILED` 并继续 fail closed。timeout/cancel 与
  child 启动完成之间的交接使用单 CAS ownership handoff 协议（
  `CoreOwnerContextManagerLifecycleTest` 以确定性交错覆盖：正常发布、
  claim 后迟到完成、lost-result 超时、中断交接、取消中断、停止与发布
  并发；close 至多一次，FAILED 不变为 READY，executor 线程退出）。
- `CoreReadinessService` 当前能区分 owner context、drain 和可选 Judge
  probe；Core scope 默认不把没有 HTTP readiness 的 Judge 当成必需 HTTP
  依赖。所有 Owner HTTP/WS 路由尚未合并到同一入口。
- 2026-09-04 的 enabled-owner exec-jar smoke（
  `CORE_OWNER_CONTEXTS_ENABLED=true`、无基础设施）显示六个 child context
  全部在 bean refresh 阶段失败，早于 DB/Redis 交互：同一 classpath 上的
  Owner jars 使 `com.ulticode.common`、event inbox、submission 和
  notification 等扫描根互相发现 sibling classes，代表性症状包括
  `DefaultAuditRecorder` 缺 `AuditSinkPort`、`SubmissionJudgedInboxBridge`
  缺 Achievement consumer，以及 `SubmissionUserReadPort` 跨 jar 歧义。
  这是真实的 wiring/classloader OPEN blocker，不是基础设施 readiness 结果。
  disabled-owner 与 failed-owner readiness 的 503、Judge `OPTIONAL` 判定已
  smoke 验证；统一业务 journey 仍未验证。

### G6 Judge 隔离（本地已证；远端项外部阻塞）

- `core/pom.xml` 明确不依赖 `backend-judge-runtime`；Judge 继续由
  `ulticode-judge` 独立 PM2 process 运行。Core smoke 的 classpath negative
  assertion 与 `app-judge-runtime-dependency-contract.sh` 锁定这一边界。
- `judge-sandbox-contract.sh` 的本地静态/disposable 段 PASS；remote/rootless
  Docker TLS smoke 需要部署方 endpoint、证书和镜像，属于当前无生产环境项目的
  `OUT_OF_SCOPE` 外部 profile。未来明确启用该 profile 但缺少输入时，才输出
  `BLOCKED_EXTERNAL`，且不作为 Core 门禁通过项。


## 结论与下一步

- 本 ADR 不切换默认拓扑，不把 Core 的性能/HA/成本写成既成事实；本项目没有生产
  环境，真实 HA、远程 TLS 和流量证据属于 `OUT_OF_SCOPE`，不计入当前开发验收。
- 当前仓库已有可启动的 opt-in Core parent shell、readiness 和 Judge
  classpath 隔离，但 enabled Owner child assembly 因同一 classpath 的跨
  Owner package leakage 尚不可运行；另只完成显式 Owner assembly、G1/G2
  本地证据、Auth local Adapter 与同进程断言载体。G3/G4 的其余 consumer
  parity、G5 的统一业务 HTTP/WS 路由与 enabled Owner journey 仍是仓库
  OPEN 项。
- 下一步不是重复写 ADR：应先按 SVC-025 为跨 Owner consumer 逐一落地
  local Adapter/保留 Dubbo 的明确边界，并先实现按 Owner jar 隔离的类加载
  （或等价机制），再运行 Core 与 distributed 的 contract/parity/disposable
  journey 对照；未完成前维持 distributed default。
- 回滚：distributed profile 与现有 release descriptor 保留为唯一回滚路径；
  Core 试点失败即停止新增 local consumers，按 descriptor 切回，不恢复已删除
  的全量权限 writer 或 App Docker fallback。

## Evidence
- Core assembly/runtime：`services/core/src/main/java/com/ulticode/core/`、
  `services/core/src/test/java/com/ulticode/core/CoreApplicationSmokeTest.java`
- Local trust path：`services/platform/common/src/main/java/com/ulticode/common/security/LocalDelegationAssertionContext.java`、
  `services/auth/src/main/java/com/ulticode/auth/security/InternalDelegationAssertionVerifier.java`

- 启动壳与扫描边界：`services/{auth,admin,app/app-web,submission,notification,search}/src/main/java/**/*Application.java`、`services/app/app-web/.../config/MapperScanConfig.java`
- 数据源/映射/事务：各 Owner `src/main/resources/application.yml`、`@MapperScan` 清单
- Dubbo 注入面：`@DubboService`/`@DubboReference` 统计（本 ADR 上文）与代表性 consumer
- 信任通道：`InternalDelegationAssertionVerifier.java`、`ProviderActorTrustGate.java`
- 运行契约：`docker-compose.yml` healthcheck、`scripts/dev/devstack-manifest.sh`
- 既有裁决：`docs/architecture/decisions/0009-authorization-and-runtime-seams.md`
