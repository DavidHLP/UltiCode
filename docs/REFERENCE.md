# API、认证与内部契约

本页汇总外部 API 入口、浏览器认证流程和跨 Owner 的内部契约。实际字段、接口和实现以 `services/api/`、各服务 controller/provider、共享类型与测试为准。

## API 与内部契约
### 外部入口


| API | 地址 | Owner |
| --- | --- | --- |
| Auth | `http://localhost:9101` / `/auth/**` | `backend-auth` |
| Admin | `http://localhost:9102` / `/admin/**`、`/moderation/**` | `backend-admin` |
| App | `http://localhost:9103` / `/users`、`/problems`、`/contests`、`/solutions`、`/forum`、`/learning-plans/**`、`/search`、`/ws/**` | `backend-app` |
| Notification | `http://localhost:9105` / `/notifications/**` | `backend-notification` |
| Submission | internal `9106` / Dubbo `20886` | `backend-submission` |
| Judge | internal Dubbo `20884` | `backend-judge` |

`POST /learning-plans` 为当前已认证、有效且未封禁的用户保存已确认的学习计划。请求必须携带规范 UUID 格式的 `Idempotency-Key`，且来源提交必须属于当前用户。相同 key 和请求内容重试时返回原记录；同一 key 携带不同内容时返回 HTTP 409（业务码 `40900`）。`GET /learning-plans/{id}` 和 `GET /learning-plans/by-key/{key}` 仅返回当前用户拥有的记录。

App 受保护路由对未认证请求返回 HTTP 401；已认证但缺少所需角色时返回 HTTP 403。显式公开路由继续允许匿名访问。

### Agent workflow API

The opt-in Python Agent service is separate from Java `/learning-plans`: it owns private multi-turn conversation history and local LearningPlan workflow drafts, while App Java owns confirmed LearningPlan records. Factory: `agent_service.app.create_app(...)`. Conversation IDs and client turn IDs are UUIDs supplied by the client; server validation binds each conversation to the authenticated owner and one eligible submission. Existing LearningPlan thread IDs remain server-generated. Every operation checks the authenticated owner; a body field cannot select a user, model, graph node, or checkpoint.

| Operation | Request body | Purpose |
| --- | --- | --- |
| `GET /agent/capabilities` | — | Read authenticated analysis/persistence availability and source mode; no model call |
| `POST /agent/threads` | `{"sourceSubmissionId": "<canonical UUID>", "question": "..."}` | Verify caller-owned eligible practice source, create thread and offline draft |
| `GET /agent/threads/{thread_id}` | — | Read caller's canonical workflow and draft |
| `GET /agent/threads/{thread_id}/events?after=N` | — | Read bounded event page; does not resume workflow |
| `POST /agent/threads/{thread_id}/analyze` | `{}` | Explicit analysis request through the shared graph; requires injected offline model factory or live model authorization |
| `PUT /agent/threads/{thread_id}/draft` | `{"draftVersion": N, "title": "...", "content": "..."}` | Compare version, update draft, invalidate confirmation |
| `POST /agent/threads/{thread_id}/confirm` | `{"draftVersion": N, "paramsDigest": "...", "confirm": true}` | Persist expiring confirmation for exact owner/version/payload; no Java write |
| `POST /agent/threads/{thread_id}/save` | `{"confirmationId": "..."}` | Explicitly dispatch the confirmed payload to Java |
| `POST /agent/threads/{thread_id}/recover` | `{"retry": false}` or `{"retry": true}` | Reconcile by original Java idempotency key; retry only when explicitly requested and all guards pass |
| `POST /agent/threads/{thread_id}/cancel` | `{}` | Fence later work; cannot roll back a dispatched Java write |
| `POST /agent/conversations` | `{"conversationId":"<UUID>","sourceSubmissionId":"<UUID>","clientTurnId":"<UUID>","question":"..."}` | Verify owner and eligibility, then create a conversation and its first queued turn |
| `GET /agent/conversations?limit=N&offset=N` | — | Read the caller's paged conversation history; returns `conversations` and nullable `nextOffset` |
| `GET /agent/conversations/{conversation_id}?limit=N&before=N` | — | Read conversation metadata and a bounded ordered turn page; `before` is optional |
| `POST /agent/conversations/{conversation_id}/turns` | `{"clientTurnId":"<UUID>","question":"..."}` | Add an idempotent queued turn after revalidating the bound submission |
| `POST /agent/conversations/{conversation_id}/turns/{turn_id}/run` | `{"sourceConsent":false}` | Explicitly run one turn; `sourceConsent` is required on every run |
| `POST /agent/conversations/{conversation_id}/turns/{turn_id}/cancel` | `{}` | Cancel only this turn and fence its later writes |
| `POST /agent/conversations/{conversation_id}/turns/{turn_id}/recover` | `{}` | Reconcile this turn's interrupted run state |
| `DELETE /agent/conversations/{conversation_id}` | — | Delete only this user's conversation and turns; active turns return HTTP 409 |
| `GET /agent/conversations/{conversation_id}/turns/{turn_id}/events/stream?after=N` | — | Subscribe to bounded SSE for this turn's run |

`GET /agent/capabilities` returns `{analysisAvailable, persistenceAvailable, conversationHistoryAvailable, sourceMode}`. Availability fields are booleans; `sourceMode` is `metadata` or `owner_source` (the existing manual analysis-only mode). `conversationHistoryAvailable` is currently true and independent of LearningPlan persistence. The endpoint checks the authenticated principal and current runtime configuration; it neither dispatches a model nor creates a thread. Persistence requires the explicit deployment opt-in and mounted LearningPlan write routes; manual analysis-only mode never enables it. This is a UI availability hint, not authorization or a reservation of model quota: each operation retains its own runtime, budget and ownership checks.

The owner-scoped Java submission detail includes `contestSubmission`, calculated by the existing
contest-association port, not inferred from browser routing. Agent source preflight requires that
field to be exactly `false` and status to be a known terminal verdict. Missing/null/non-boolean
context, contest or virtual-contest submissions, Pending, Judging, and unknown statuses fail closed.
Ineligible sources return HTTP `409 source_not_eligible` at creation, analysis, confirmation,
save, and explicit write retry; hiding a frontend button is not the enforcement boundary.
Read-only recovery can still reconcile an already dispatched write by its original key when source
eligibility changes, but cannot retry that write. Deploy the updated Java detail provider before enabling the new
Console entry; old providers that omit the field cannot establish eligibility. General metadata
listing tools retain their existing projection and do not gain source code, tests, or owner fields.

LearningPlan thread states include `draft`, `analyzing`, `awaiting_confirmation`, `confirmed`, `saving`, `saved`, `unknown`, `failed`, and `cancelled`. Agent SQLite is canonical for those drafts, thread state, and events; LangGraph checkpoints are resumable workflow control state only. Multi-turn conversation turns use separate SQLite rows and statuses `queued`, `running`, `completed`, `failed`, and `cancelled`; each turn has its own run ID, event sequence, and cancellation fence, and chat analysis does not write a LangGraph checkpoint. `saved` requires an accepted
Java record response. A timeout, lost response, or uncertain process restart remains `unknown`.
recovering an interrupted `saving` state with no Java record and `retry: false` persists `unknown`
without issuing another write. A later explicit retry retains the original business key and guards.
Reconcile by the same idempotency key before any retry. A by-key lookup is treated as not found only
for HTTP 404 with business code `40400`; transport/server errors remain unresolved. Retry is never
automatic: `retry:true` is an explicit action and still requires an eligible unexpired confirmation,
matching payload, no cancellation, and the exact not-found result. Never mint a replacement key.

GET thread responses expose `threadId`, `runId`, `status`, `sourceSubmissionId`, `question`, a
versioned `draft` (`title`, `content`, `facts`, `hypotheses`, `citations`, `citationChecks`,
`sampleScope`), `paramsDigest`, `analysis`, a redacted `confirmation` summary, and `receipt`
(`planId`, `cancelRequested`, `failureReason`). They do not expose owner IDs or the business key.
Draft facts are derived from the validated metadata projection; the general submission-list tools
never expose source code or test data. In explicit local manual analysis-only mode only, analysis may read
the exact owner-verified submission already bound to the thread through a no-argument,
request-scoped tool. Source is untrusted input and capped at 12,000 characters; missing or oversized
source fails before provider dispatch. The source body is not stored in workflow rows, events, or
checkpoints. Source citations are checked in memory and persisted without their quoted source text.
In metadata mode, the initial model prompt includes the server-verified status and user question, but
omits the bound submission ID and source code; it identifies the submission as already selected so
the model does not ask the user for another ID or infer a code-level diagnosis.
`sampleScope` identifies whether an analysis used the checked-in synthetic corpus or the
owner-authorized thread source. Model-answer citations are added only after same-run retrieval,
document-integrity, support, and derivability checks.

Conversation requests use independent conversation/turn storage and do not enter the LearningPlan thread state machine. Each run requires a strict `sourceConsent` boolean; in `owner_source` mode, consent applies to that run only, and the exact bound submission is re-fetched for the request. Fetched source and citation excerpts are not persisted in conversation rows, turns, events, or checkpoints; citation responses keep safe reference fields without excerpt text. Prior context is limited to recent completed turns and a 16,000-character cap. Conversation history remains until the owner deletes it; deletion is rejected while a turn runs and does not cascade into separately saved LearningPlans.
The Agent keeps the business idempotency key private. Its recovery action uses that original key
for owner-scoped by-key reconciliation; a user-facing saved-record readback uses
`GET /learning-plans/{planId}` with the returned `planId`.
Use the GET `paramsDigest` and current `draftVersion` in the confirmation request. The returned
confirmation summary contains its id, action, bound version/digest, and expiry; it never contains
the idempotency key.
Events return `{events, next}` with a bounded page. New events persist the authoritative
run identifier in `detail.runId` in the same transaction as their state transition;
older events without it retain their original payload and must not be assigned the current run.
`GET /agent/threads/{thread_id}/events/stream?after=...` authenticates the owner and
returns SSE for the run current at subscription time. `workflow` events retain their
durable sequence and include tool start/completion metadata without arguments or results.
Model start/completion events associate a server-generated call ID and node with the run;
completion records elapsed milliseconds, a bounded provider model label, budget attempt ID
when available, and nonnegative token counts. Missing usage is `null` with `usageStatus=unavailable`,
never inferred as zero. Errors use fixed reasons, without prompts, answers, credentials or
provider error bodies. The observer delegates to the existing authorized model and budget.
`text` contains only validated, persisted answer text; `terminal` reports canonical status,
failure reason and cancellation intent. SSE IDs bind run, sequence and event kind for replay
deduplication. Run replacement or the 30-second subscription limit emits `stream_closed`;
this is a transport outcome, not business completion. Disconnect ends the subscription,
while explicit cancellation uses the existing cancel endpoint and does not undo Java writes. Conversation SSE binds to the run current at subscription, replays bounded `workflow` events after `after`, and emits validated persisted answer text plus a terminal status. A replaced run or subscription timeout closes the stream; reload the canonical turn before retrying.
`after` must be between 0 and
`2^63 - 1`. Invalid or foreign submission sources return `404 source_not_owned`
during creation and analysis. Authorization failures during Java save leave the outcome
`unknown` and permit explicit recovery after session and ownership checks; payload and
idempotency rejections remain terminal. Learning-plan routes are also registered in the
opt-in Core App context.

Agent responses use `{code,message,data,traceId}` with a server-generated `traceId`; the envelope
is validated by a strict Pydantic response schema before serialization, including JSON-only data
and rejection of non-finite numbers. Upstream response bodies are not exposed. Request bodies require `application/json`, are strict and bounded
to 128 KiB, use canonical UUIDs and exact integer versions, reject extra/duplicate JSON keys and
non-finite numbers, and enforce question length 2–200 plus Java-compatible nonblank title/content
limits of 200/16,000 Unicode code points. Event reads are bounded and read-only.

#### Identity, CSRF, and runtime authorization

`/auth/me` is the sole principal source; accept only nonempty `data.user.id` with `is_active=true`
and `is_banned=false`. Each request gets an isolated client with access cookie held only in memory.
Reads require one access cookie. Malformed access/CSRF cookie values return 401/403 before
client construction. Unsafe calls require exactly one access cookie, one CSRF cookie,
and a constant-time match with `X-CSRF-Token`; ambiguous duplicate Cookie/header input fails before
upstream HTTP. If an unsafe request includes `Origin`, it must match the configured trusted origin.
Request bodies reject identity fields. The service does not refresh sessions, set login cookies, or
configure cross-origin access.

Historical acceptance artifacts, candidate commits and source fingerprints are not runtime
authorization inputs. `ULTICODE_AGENT_PERSISTENCE_ENABLED=1` explicitly mounts confirm/save/recover;
it does not authorize model calls. Live analysis requires `ULTICODE_AGENT_LIVE_ANALYSIS=1` and
provider credentials. With a loopback `ULTICODE_AGENT_ORIGIN` and no
`ULTICODE_AGENT_BUDGET_POLICY`, local development uses the model without the shared cumulative
budget ledger; per-request token and attempt limits remain, but there is no cumulative cost ceiling.
Non-loopback live mode requires an existing active budget period, its shared guard, and
policy-authorized `u03_analysis` and `u03_citation_judge` purposes. Runtime reads existing
accounting and never creates or resets it; missing or invalid authorization fails closed before a
provider request.

The existing manual analysis-only mode remains limited to read-only analysis under its fixed
budget. It may read only the owner-verified thread source through a no-argument request-scoped
tool. The source is capped at 12,000 characters and not stored; citation excerpts are redacted
after in-memory verification. This mode cannot enable confirm/save/recover.

The workflow runs analysis through the shared checkpointed action graph and read-only model/tool
kernel. Answer text must pass bounded source-fact, negation-aware boundary, source-reference, and
tool-trace checks; those safeguards do not guarantee perfect semantic correctness. Nonempty
citations must exactly match evidence retrieved during that run, pass document-integrity checks,
then receive positive support and derivability judgments. A citation that was not retrieved in-run,
fails integrity/judging, or has unknown judge usage rejects analysis. Empty citations are allowed
only when the answer itself satisfies the boundary checks. Offline scripted-model/MockTransport
tests are not live acceptance evidence.

With persistence disabled, confirm/save/recover use the normal not-found response envelope.
Authenticated reads remain available independently of model and persistence switches. Payload
confirmation digests, citation integrity and budget/accounting checks remain in place; they are
not the removed source or acceptance SHA gates.

For startup and retained checks, see [Agent local runtime](DEVELOPMENT.md#agent-本地运行).

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
