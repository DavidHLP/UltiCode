# Services 架构现状与优化状态

更新时间：2026-08-21

本文以源码、Maven POM、运行配置、Compose 和实际启动脚本为准；历史迁移指南只作为背景。本文的架构盘点部分是只读 inventory，不代表生产部署或生产数据变更已经执行。

## 1. 总体结论

UltiCode 已经完成从单体 JVM 到多进程 Owner/Worker 拓扑的骨架迁移：Maven reactor、Contract Seam、独立服务启动入口、生产 Compose 的七个后端 runtime，以及 Outbox/Inbox/Redis Streams 等异步可靠性基础均已存在。

当前仍是 Strangler migration 收敛阶段，不能称为最终微服务形态。主要未收敛项是：

- Owner 的物理数据库和权限隔离还不完整；
- 部分读模型仍通过同步 facts enrichment 回访；
- App 仍保留 local/remote、legacy、shadow 和内置 Judge/Search 兼容路径；
- Admin 的查询 Seam 仍过细；
- 迁移指南、根 POM、PM2/启动入口曾落后于代码，正在同步。

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

共享模块是 `platform/*`、`api/*`、`integration-inbox` 和 `judge-runtime`。`services/pom.xml` reactor 已登记 platform、五个 API/Contract 方向、五个 Owner、两个 Worker 和 `judge-runtime`；不存在实际模块的 `backend-api` dependency-management 条目已移除。

生产 Compose (`docker-compose.prod.yml`) 定义 `backend-auth`、`backend-admin`、`backend-app`、`backend-submission`、`backend-search`、`backend-notification`、`backend-judge` 七个后端 runtime。`ecosystem.config.cjs` 也提供七个后端 PM2 entry；本地 `scripts/dev/up.sh --mode dev-lite` 是唯一第一类开发 interface，默认启动六个后端并明确排除 Search，`--mode dev-full` 由 `devstack-manifest.sh` 显式加入 Search，以配合 indexed read；`--only search` 仍可单独启动 Search。

### 2.2 Contract Seam

Auth、Admin、App、Submission、Notification 的服务边界由各自 `api/*` 合同承载，包含接口、DTO、错误码和事件。`api/*` 不应包含 Entity、Mapper、Repository 或实现模块类型；既有 API contract/architecture tests 持续守住该边界。

### 2.3 Submission 写入回访状态

原写入链是：

```text
App request -> Submission write -> App ProblemFacts / Auth UserExistence -> Submission tables
```

当前 `backend-submission-api` 增加了不可变 `SubmissionFactsSnapshot`。App 本地和远程 request boundary 组装快照，Submission Owner 只校验快照与 command 的 user/problem 是否匹配，不再在写入事务中注入或调用 `ProblemFactsPort`、`UserExistencePort` 的 Dubbo adapter。缺失、错配或非法快照 fail closed。

这只关闭 Submission 写入链。Submission 读侧的标题/用户 enrichment 仍有 App/Auth facts seam，完整消除需要后续事件化 projection，不能由本次快照改造冒充完成。

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

长期约束应是“一次请求最多一次业务 Provider hop”。历史 Submission 写入违反了该原则：为校验 Problem Facts 和用户存在，形成 App → Submission → App/Auth。Facts Snapshot 已消除 Owner 写事务内的回访；长远方案仍有两个选择：

1. App 在请求边界提供经过授权和版本化的不可变 facts snapshot；或
2. Submission 通过事件维护自己的 Problem/User projection，写入只读本地 projection。

Snapshot 是当前低风险实现；projection 仍需定义事件版本、滞后/拒绝策略和回填。

### 4.2 数据库物理隔离

- Submission 已有独立数据库配置和 `submission_rw` Owner 账号；
- Auth、Admin、App、Notification 仍主要使用共同数据库名/账号配置，不能称为完整物理隔离；
- 当前完成度主要是代码级 Owner division，而不是完整数据库 permission isolation；
- `users` 仍混合账户/凭证、权限、封禁和 profile 字段。

后续必须按 expand → backfill → verify → cutover → observe → contract 推进：先建 profile/授权等新表和专用账号，再回填和校验，最后在有权限与回滚证据的窗口撤销旧访问。不要编辑已应用 migration，也不要在没有生产 authority 的情况下执行 revoke 或删除旧列。

### 4.3 App 双轨兼容

App 当前同时维护 Submission、Notification、MeiliSearch 以及 legacy compatibility 实现；Judge 执行 wiring 已收进独立 Judge 配置，App 仅保留默认关闭的显式 legacy RQueue rollback adapter。Submission 路由默认仍是 local，远程路由由生产配置/门禁显式开启；因此这是可切换迁移架构，不是已经删除 legacy path 的最终形态。生产远程稳定窗口、全写入者 quiesce、旧消息/双写对账和可回滚 artifact 缺失时，不删除这些路径。

### 4.4 Admin Seam 已收敛为粗粒度查询切片

Admin 已按可测量的查询垂直切片收敛为粗粒度 Query Seam：Contest 参与、Revenue、Overview 与 Dashboard（含用户图表）分别经 `AdminAnalyticsPort`/`AdminDashboardReadPort` 的 bounded 并行 Owner 组合暴露，Dashboard 用户图表经 `AccountQueryService` 分页扫描并在 `CancellableQueryExecutor` 的 800ms deadline 与 bounded reads 约束下收敛，`BackendAdminApplication` 的 exclusion 随之缩减。剩余细粒度遗留按需再聚合，不再作为阻塞项。

### 4.5 文档和运维入口漂移

历史迁移文本仍出现旧 Owner 数量、旧 `backend-api` 结构和“zero Dubbo implementation”叙述；旧 PM2/启动说明也只覆盖部分运行时。当前权威顺序是：

1. Java source and tests；
2. Maven POM/reactor；
3. application/config files；
4. Compose；
5. actual startup scripts/PM2 entries；
6. documentation。

文档只能追随前五项，不能反向定义实际拓扑。

## 5. 后续优先级

优先级高于继续拆分模块：

1. 消除剩余同步回访，先完成 Submission read projection 的决策和事件契约；
2. 完成 Auth/Admin/App/Notification 的数据库、账号和权限隔离；
3. 在真实稳定性和回滚门禁满足后删除 App 双轨兼容代码；
4. 聚合 Admin 查询接口并消除 fan-out/N+1；
5. 维护 migration guide、Owner matrix、POM、PM2 和 Compose 的一致性。

## 6. 评审边界

架构盘点本身是只读 inventory：不以文档推断代码，不修改已应用数据库 migration，不声称生产部署或生产数据验证。后续 `ARCH-*` 任务单独记录实现、测试、回滚和外部 authority gate。
