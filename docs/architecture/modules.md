# 模块与数据所有权

## Owner / Worker 划分

| 模块 | 类型 | 主要职责 | 不负责 |
| --- | --- | --- | --- |
| `services/auth` | Data Owner | 登录、注册、OAuth、凭证、refresh、账号状态、RBAC、JWKS | 用户画像、题目、竞赛、通知偏好 |
| `services/admin` | Data Owner | Management BFF、审核、审计、设置、监控、备份和管理读模型 | 业务数据的跨 Owner 持久化 |
| `services/app` | Data Owner | 用户画像、Problem、Contest、Solution、Forum、互动、成就、订阅、WebSocket | Submission 持久化、Notification 投递、Judge 执行、Search 索引写入 |
| `services/submission` | Data Owner | Submission intake、verdict、generation/lease fence、judge/result/created outbox | Problem/TestCase/Contest 业务表 |
| `services/notification` | Data Owner | Notification、偏好、投递 ledger、邮件、Inbox 和重试 | WebSocket endpoint 与 App 业务表 |
| `services/judge` | Worker | 消费 Judge Stream、读取 Problem facts、执行 Docker 沙箱、回写 verdict | 业务表与业务 HTTP |
| `services/search` | Worker | 消费 `SearchDocumentChanged`、维护 MeiliSearch 派生索引 | 业务表与业务 HTTP |

`services/judge-runtime` 是共享 Judge 执行依赖，不是进程；`services/platform/*` 是共享平台层；`services/api/*` 只包含 implementation-free contract。`app-api` 只保留跨 Owner contract；App-only Seam 位于 `app-web` 对应逻辑 Module。

`services/platform/observability` 是所有 Owner/Worker 共用的 OTLP
credential-to-endpoint security guard：配置 authorization 时只允许 HTTPS。

## 服务边界

### Auth

Auth 独占 account、credential、external identity、refresh session、role/permission 和 authorization version。`users` 的 account/authz 字段归 Auth；profile 字段归 App 的 `user_profiles`。其他 Owner 通过窄的 Identity/Account contract 获取必要事实，不直接读取 Auth Mapper 或 Entity。

### Admin

Admin 持有 moderation case/decision、audit、system settings、backup job 和自身 read model。管理页面不是数据所有权依据：创建题目、竞赛、Submission 管理命令仍调用 App/Submission Owner；审计 actor 来自认证或委托 principal，不来自请求 DTO。

### App

App 持有普通用户 profile、Problem/Contest/Forum/Solution/Engagement/Achievement/Subscription 和 WebSocket。它发布 `NotificationIntentCreated`、`SearchDocumentChanged` 等事件；Submission 通过 owner contract，Notification 只接收 intent 并负责投递。

### Submission

Submission 是唯一 mutation/fence owner。App 请求边界组装不可变 `SubmissionFactsSnapshot` 并调用远程 `SubmissionIntakePort`；Judge 通过 `SubmissionFencePort` / `SubmissionVerdictWritePort` 回写。Admin rejudge 只发送带 actor、trace、idempotency 的 command。App 不保留本地 writer、rejudge provider、verdict/fence、read projection、mapper 或 entity；当前 binary/DevStack 拒绝旧 local compatibility mode，生产回滚只能指向部署方保留的上一份完整 release descriptor。

### Notification

Notification 是 notifications、preferences、delivery ledger 和 email 的唯一持久化 owner。App 保留 intent 发布与 WebSocket relay，不保留 Notification SQL。Admin 通过 `NotificationReconciliationReadPort` 消费 500 行上限的 owner facts。

### Judge / Search

Judge 通过 Redis Streams 异步接收 Submission outbox，使用 Problem facts 和沙箱控制执行，失败留在 PEL 或进入 DLQ。Search 只消费 allowlisted、版本化事件并更新 MeiliSearch；删除是 tombstone，业务写路径不得直写索引。

## 依赖规则

```text
platform/common <- api/* <- Owner/Worker provider 或 adapter
backend-admin -> auth-api + app-api + submission-api + notification-api
backend-app -> auth-api + submission-api + notification-api + judge-api
backend-notification -> auth-api + app-api + notification-api
backend-judge -> judge-api + judge-runtime + app-api + submission-api
backend-search -> platform/common + search event contract
backend-auth -X-> app/admin API
```

- 每个请求最多经过一个业务 Provider 单跳；Provider 不形成 A→B→A 链。
- Consumer 依赖 consumer-owned port；Provider 暴露 provider-owned contract。
- 允许共享：DTO、Result/RpcResult、error code 基础类型、trace/deadline/idempotency metadata、无业务语义工具和 contract fixture。
- 禁止共享：Entity、Mapper、Repository、业务 Service/Projection 实现、数据库连接 starter、私钥和隐式全局 Redis 配置。

## 代码分层

每个 Owner 内继续使用 `controller → service/projection/port → mapper → entity`。跨 Owner 的 adapter 位于消费方，负责 transport、超时、错误映射和契约版本；不把远程 DTO 映射成另一 Owner 的持久化 Entity。具体 contract、版本和错误 envelope 见 [`data-flow.md`](data-flow.md)。
