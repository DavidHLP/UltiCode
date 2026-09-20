# API、认证与内部契约

本页汇总外部 API 入口、浏览器认证流程和跨 Owner 的内部契约。实际字段、接口和实现以 `services/api/`、各服务 controller/provider、共享类型与测试为准。

## API 与内部契约
### 外部入口


| API | 地址 | Owner |
| --- | --- | --- |
| Auth | `http://localhost:9101` / `/auth/**` | `backend-auth` |
| Admin | `http://localhost:9102` / `/admin/**`、`/moderation/**` | `backend-admin` |
| App | `http://localhost:9103` / `/users`、`/problems`、`/contests`、`/solutions`、`/forum`、`/search`、`/ws/**` | `backend-app` |
| Notification | `http://localhost:9105` / `/notifications/**` | `backend-notification` |
| Submission | internal `9106` / Dubbo `20886` | `backend-submission` |
| Judge | internal Dubbo `20884` | `backend-judge` |

浏览器通常通过前端 Nginx/gateway 访问 `/api`；不要把内部 Dubbo、数据库、Redis、Nacos 或 worker 端口发布到公网。

浏览器侧统一经 `packages/http-client` 发起调用，成功结果只暴露业务 payload：`Result.code` 为 0 返回 `data`，非 0 以 `ApiError` 拒绝，非 `Result` envelope 返回 payload 本身（含 Blob），不提供原始 transport response。`apiDownload` 返回 `Promise<void>`，以 object URL 与临时 `<a download>` 元素触发浏览器保存；URL 创建后无论 DOM 步骤成功与否都会移除元素并 revoke URL，清理失败不覆盖原始错误。

### Contract modules

`services/api/` 的 provider-owned contract：

- `auth-api`：Identity、Account administration、Authorization snapshot。
- `app-api`：App-owned Problem/Contest/content、facts/recipient 例外。
- `judge-api`：Judge-owned execution contract；不包含 provider implementation 或 sandbox dependency。
- `submission-api`：intake、verdict、fence、facts、rejudge administration 和 lifecycle events。
- `notification-api`：notification administration、reconciliation、intent/delivery payload。

Contract 只包含接口、typed DTO、事件、错误码和无状态 metadata；不包含 Entity、Mapper、Repository、Spring Bean 或数据库配置。`Result<T>` 与 `RpcResult` envelope 以及字段映射保持不变。当前 contract artifacts 使用 reactor revision `2.0.0`；不兼容版本和外部消费者 drain 由 `CONTRACT_COMPAT_GATE.md` 门禁。

### 跨 Owner 调用

- App 提交：`RemoteSubmissionWritePort` → `backend-submission`。
- Admin rejudge：带 actor/trace/idempotency 的 delegation command → `backend-submission`。
- Admin 管理：按业务 Owner 调 Auth/App/Submission/Notification 的窄 provider。
- Judge：Redis Judge Stream → Problem facts + Submission fence/verdict。
- Notification：App intent event → Notification Inbox/ledger；WebSocket relay 留在 App。
- Search：Owner `SearchDocumentChanged` → Search worker → MeiliSearch；唯一索引 writer 是 worker。

Provider 校验 audience、签名、deadline、jti/replay、actor 和输入边界；Dubbo attachment 不构成信任边界。写调用自动 retry=0，查询仅使用明确的 timeout/retry/circuit/bulkhead 预算。

### 错误与兼容

业务验证/授权错误与 transport unavailable、timeout、circuit-open、bulkhead saturation 分开映射。重复 command 使用 owner receipt replay；处理中重复和不同 fingerprint 返回冲突。未知 event/schema、非法 aggregate version、坏 payload 和 owner facts 的 null/乱序/超大页 fail closed。

详细兼容规则：[`services/docs/CONTRACT_COMPAT_GATE.md`](../services/docs/CONTRACT_COMPAT_GATE.md)；服务状态：[`SERVICES_ISSUES.md`](../services/docs/SERVICES_ISSUES.md)。

## 认证流程

本文只描述浏览器认证请求的顺序；Cookie、JWT/JWKS、CSRF、WebSocket、委托身份和吊销边界的唯一权威说明是[安全架构](ARCHITECTURE.md#安全架构与信任边界)。

### Cookie 流程

1. `POST /auth/login`、`POST /auth/register` 或 OAuth callback 在 Auth 本地验证 account、credential 和 state。
2. Auth 创建 hash-only refresh session，并返回 HttpOnly `access_token`、`refresh_token` 与可读 `csrf_token`。
3. 浏览器调用其他 mutation、`POST /auth/refresh` 或 logout 时，按[安全架构](ARCHITECTURE.md#安全架构与信任边界)提交 Cookie 与 CSRF header。
4. refresh 只使用 refresh cookie；Auth 条件 revoke 旧 session 并插入新 session。

### 服务入口

- Auth HTTP owner：`/auth/**`。
- 浏览器通常经前端 Nginx 的 `/api/auth/**` 访问 Auth；不要把内部服务端口当作公网 API。
- WebSocket 使用 App 的 `/ws/**` relay；其握手认证规则见[安全架构](ARCHITECTURE.md#安全架构与信任边界)。

### 相关契约

- [API 与内部契约](REFERENCE.md#api-与内部契约)
- [安全架构与信任边界](ARCHITECTURE.md#安全架构与信任边界)
- [Services issue registry](../services/docs/SERVICES_ISSUES.md)
