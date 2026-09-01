# API 与内部契约

## 外部入口

| API | 地址 | Owner |
| --- | --- | --- |
| Auth | `http://localhost:9101` / `/auth/**` | `backend-auth` |
| Admin | `http://localhost:9102` / `/admin/**`、`/moderation/**` | `backend-admin` |
| App | `http://localhost:9103` / `/users`、`/problems`、`/contests`、`/solutions`、`/forum`、`/search`、`/ws/**` | `backend-app` |
| Notification | `http://localhost:9105` / `/notifications/**` | `backend-notification` |
| Submission | internal `9106` / Dubbo `20886` | `backend-submission` |
| Judge | internal Dubbo `20884` | `backend-judge` |

浏览器通常通过前端 Nginx/gateway 访问 `/api`；不要把内部 Dubbo、数据库、Redis、Nacos 或 worker 端口发布到公网。

## Contract modules

`services/api/` 的 provider-owned contract：

- `auth-api`：Identity、Account administration、Authorization snapshot。
- `app-api`：App-owned Problem/Contest/content、facts/recipient 例外。
- `submission-api`：intake、verdict、fence、facts、rejudge administration 和 lifecycle events。
- `notification-api`：notification administration、reconciliation、intent/delivery payload。

Contract 只包含接口、typed DTO、事件、错误码和无状态 metadata；不包含 Entity、Mapper、Repository、Spring Bean 或数据库配置。`Result<T>` 与 `RpcResult` envelope 以及字段映射保持不变。当前 contract artifacts 使用 reactor revision `2.0.0`；不兼容版本和外部消费者 drain 由 `CONTRACT_COMPAT_GATE.md` 门禁。

## 跨 Owner 调用

- App 提交：`RemoteSubmissionWritePort` → `backend-submission`。
- Admin rejudge：带 actor/trace/idempotency 的 delegation command → `backend-submission`。
- Admin 管理：按业务 Owner 调 Auth/App/Submission/Notification 的窄 provider。
- Judge：Redis Judge Stream → Problem facts + Submission fence/verdict。
- Notification：App intent event → Notification Inbox/ledger；WebSocket relay 留在 App。
- Search：Owner `SearchDocumentChanged` → Search worker → MeiliSearch；唯一索引 writer 是 worker。

Provider 校验 audience、签名、deadline、jti/replay、actor 和输入边界；Dubbo attachment 不构成信任边界。写调用自动 retry=0，查询仅使用明确的 timeout/retry/circuit/bulkhead 预算。

## 错误与兼容

业务验证/授权错误与 transport unavailable、timeout、circuit-open、bulkhead saturation 分开映射。重复 command 使用 owner receipt replay；处理中重复和不同 fingerprint 返回冲突。未知 event/schema、非法 aggregate version、坏 payload 和 owner facts 的 null/乱序/超大页 fail closed。

详细兼容规则：[`services/docs/CONTRACT_COMPAT_GATE.md`](../../services/docs/CONTRACT_COMPAT_GATE.md)；服务状态：[`SERVICES_ISSUES.md`](../../services/docs/SERVICES_ISSUES.md)。
