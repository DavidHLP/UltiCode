# Services 架构现状与优化状态

更新时间：2026-08-21

本文以源码、Maven POM、运行配置、Compose 和实际启动脚本为准；历史迁移指南只作为背景。本文的架构盘点部分是只读 inventory，不代表生产部署或生产数据变更已经执行。

## 1. 总体结论

UltiCode 已经完成从单体 JVM 到多进程 Owner/Worker 拓扑的骨架迁移：Maven reactor、Contract Seam、独立服务启动入口、生产 Compose 的七个后端 runtime，以及 Outbox/Inbox/Redis Streams 等异步可靠性基础均已存在。

当前仍是 Strangler migration 收敛阶段，不能称为最终微服务形态。当前需要维护的是：

- 开发环境使用显式 Owner schema/account 配置；生产物理切换和外部 authority 不属于本项目；
- Submission read 通过 bounded batch facts seam 组合，事件化 read projection 仍是未来可选能力；
- App 保留 local/remote Submission 与显式 legacy rollback seam，但正常 dev-lite/dev-full 使用 Judge Streams；
- Admin 查询已收敛为粗粒度 query slices，不再作为拆分更多进程的理由；
- DevStack manifest、源码/POM/config/Compose/PM2 和文档由 executable contract checks 持续对账。

## 2. 模块与运行拓扑

### 2.1 Owner/Worker 清单

| 类型 | 模块 | 数据职责 | 对外/运行方式 |
| --- | --- | --- | --- |
| Data Owner | `backend-auth` | 账号、认证、会话、授权事实 | HTTP/Dubbo |
| Data Owner | `backend-admin` | 管理、治理、审计和运营查询/命令 | HTTP/Dubbo |
| Data Owner | `backend-app` | OJ 与一般用户业务（Problem、Contest、Forum 等） | HTTP/Dubbo |
| Data Owner | `backend-submission` | Submission、Judge/Result/Created outbox、generation/attempt fence | HTTP/Dubbo |
| Data Owner | `backend-notification` | 通知 Inbox、投递 ledger 和重试状态 | HTTP/Dubbo |
| Worker | `backend-judge` | 不拥有业务表；消费 Judge stream，执行 sandbox，写 verdict seam | Background Worker |
| Worker | `backend-search` | 不拥有业务表；消费 SearchDocumentChanged，写 MeiliSearch | Background Worker |

`judge-runtime` 是 Judge 执行逻辑的共享依赖，不是第八个进程。

共享模块是 `platform/*`、`api/*`、`integration-inbox`、`judge-config` 和 `judge-runtime`。`judge-config` 只承载 Submission/Judge 共用的 flag binding/命名模式校验，不是运行时进程；`services/pom.xml` reactor 已登记 platform、五个 API/Contract 方向、五个 Owner、两个 Worker 和 `judge-runtime`；不存在实际模块的 `backend-api` dependency-management 条目已移除。

生产 Compose (`docker-compose.prod.yml`) 定义 `backend-auth`、`backend-admin`、`backend-app`、`backend-submission`、`backend-search`、`backend-notification`、`backend-judge` 七个后端 runtime。`ecosystem.config.cjs` 也提供七个后端 PM2 entry；本地 `scripts/dev/up.sh --mode dev-lite` 是唯一第一类开发 interface，默认启动六个后端并明确排除 Search，`--mode dev-full` 由 `devstack-manifest.sh` 显式加入 Search，以配合 indexed read；`APP_RUNTIME_MODE`、`APP_SUBMISSION_ROUTING_MODE` 和 Search read mode 均由该 manifest 统一导出，直接本地 App/Judge boot 默认与 dev-lite 一致；`--only search` 仍可单独启动 Search。

### 2.2 Contract Seam

Auth、Admin、App、Submission、Notification 的服务边界由各自 `api/*` 合同承载，包含接口、DTO、错误码和事件。`api/*` 不应包含 Entity、Mapper、Repository 或实现模块类型；既有 API contract/architecture tests 持续守住该边界。

### 2.3 Submission 写入回访状态

原写入链是：

```text
App request -> Submission write -> App ProblemFacts / Auth UserExistence -> Submission tables
```

当前 `backend-submission-api` 增加了不可变 `SubmissionFactsSnapshot`。App 本地和远程 request boundary 组装快照，Submission Owner 只校验快照与 command 的 user/problem 是否匹配，不再在写入事务中注入或调用 `ProblemFactsPort`、`UserExistencePort` 的 Dubbo adapter。缺失、错配或非法快照 fail closed。

这只关闭 Submission 写入链。Submission 读侧现在通过 bounded batch facts seam 完成标题/用户 enrichment；没有引入事件化 read database，也不把一次请求拆成逐条跨 Owner 调用。

## 3. 已建立的优势

### 3.1 Contract Module 作为主要边界

Contract module 已成为发布和依赖边界：调用方依赖 provider-owned API，而不是跨 Owner 共享实现。Submission API 的 `SubmissionWritePort`、Notification contract、事件 envelope 及契约 shape tests 证明了这一方向。新 DTO 保持实现无关，避免 Entity/Mapper/Repository 泄漏。

### 3.2 Submission 是边界最清晰的 Owner

Submission 已将复杂逻辑收进 Owner 内部：输入验证、事务、本地 submission/judge/result/created 多个 Outbox、generation/attempt fence、过期 verdict 丢弃、contest event 和 thin external adapters。其外部 Provider 只负责 Contract delegation，存储和并发语义留在 Owner 内，封装深度合适。

### 3.3 异步可靠性

当前可靠性机制覆盖不同故障窗口：

| 模块 | 机制 |
| --- | --- |
| Submission | 多个 Outbox、事务内落盘、generation/attempt fence、lease/retry、过期结果丢弃 |
| Judge | Redis Streams consumer、PEL reclaim、bounded retry、DLQ、ACK-after-write |
| Notification | Inbox、delivery ledger、lease fencing、重试与幂等投递 |
| Search | Redis Streams、PEL 处理、版本控制、幂等 upsert/delete、DLQ、tombstone/replay 语义 |

这些机制使跨进程副作用可重放、可观测，并降低 DB/Redis/SMTP/MeiliSearch 双写造成的丢失风险。

## 4. 未完成的收敛问题

### 4.1 同步回访与单跳原则

长期约束应是“一次请求最多一次业务 Provider hop”。Submission 写入使用不可变 Facts Snapshot；Submission read 使用一次 bounded batch seam 取得本页所需的 User/Problem facts。事件化本地 projection 暂不作为开发环境的必需基础设施。

### 4.2 数据库物理隔离

- 开发环境的 Auth/Admin/App/Notification/Submission runtime 已使用显式 Owner database/account 配置；
- 当前边界仍是一个 MySQL instance 内的 schema/account isolation，不宣称独立物理集群；
- users account/profile ownership 由 Auth account 与 App profile read/write seams 表达；
- production physical cutover、external authority 和 grant retirement 不在当前开发目标内。

后续必须按 expand → backfill → verify → cutover → observe → contract 推进：先建 profile/授权等新表和专用账号，再回填和校验，最后在有权限与回滚证据的窗口撤销旧访问。不要编辑已应用 migration，也不要在没有生产 authority 的情况下执行 revoke 或删除旧列。

### 4.3 App 双轨兼容

App 当前保留 Submission local/remote compatibility、Search fallback 和显式 legacy rollback seams；Judge normal dev-lite/dev-full 使用 Streams，RQueue 只有 legacy-rollback mode 才能装配。不要在没有 external authority、quiesce、观察窗和 rollback artifact 的情况下删除这些 seams。

### 4.4 Admin Seam 已收敛为粗粒度查询切片

Admin 已按可测量的查询垂直切片收敛为粗粒度 Query Seam：Contest 参与、Revenue、Overview 与 Dashboard（含用户图表）分别经 `AdminAnalyticsPort`/`AdminDashboardReadPort` 的 bounded 并行 Owner 组合暴露，Dashboard 用户图表经 `AccountQueryService` 分页扫描并在 `CancellableQueryExecutor` 的 800ms deadline 与 bounded reads 约束下收敛，`BackendAdminApplication` 的 exclusion 随之缩减。剩余细粒度遗留按需再聚合，不再作为阻塞项。

### 4.5 文档和运维入口漂移

历史迁移章节可以保留为历史，但当前状态、启动和领域词汇必须与源码对账。当前权威顺序是：

1. Java source and tests；
2. Maven POM/reactor；
3. application/config files；
4. Compose；
5. actual startup scripts/PM2 entries；
6. documentation。

文档只能追随前五项，不能反向定义实际拓扑；scripts/dev/architecture-contract-test.sh 负责阻止已知漂移回归。

## 5. 后续优先级

优先级高于继续拆分模块：

1. 继续用 bounded read contracts 收敛跨 Owner 读取，必要时再评估事件化 projection；
2. 保持 Owner schema/account isolation 的 development evidence，不伪造 production acceptance；
3. 只在真实稳定性和回滚门禁满足后删除 legacy compatibility；
4. 维护 DevStack、Owner matrix、POM、PM2、Compose、CONTEXT 和 migration guide 的 executable consistency。

## 6. 评审边界

架构盘点本身是只读 inventory：不以文档推断代码，不修改已应用数据库 migration，不声称生产部署或生产数据验证。后续 `ARCH-*` 任务单独记录实现、测试、回滚和外部 authority gate。
