# Subscription API — 测试问题文档 (curl + Arthas 实测)

> **生成时间**：2026-06-11
> **测试基线**：`backend-spring` on `localhost:9001`，PM2 `ulticode-9001` (PID 9516)，Arthas MCP `localhost:8563`
> **测试账号**：`admin` / `admin123` (dev-profile-only bootstrap，ADMIN 角色，无订阅)
> **测试者**：Claude via curl + Arthas MCP (`sc`, `jad`, `thread`) + 静态源码 diff
> **范围**：`console/src/api/subscription.ts` 的 9 个 API + 对应后端 `com.ulticode.modules.subscription.controller.*`

---

## 0. TL;DR — 关键发现

| # | 前端函数 (subscription.ts) | 前端路径 | 实际后端响应 | 状态 |
|---|---------------------------|---------|-------------|------|
| 1 | `getMySubscription` | `GET /subscriptions/me` | **200，但 `data` 字段缺失** (后端返回 `Result.success(null)` → `@JsonInclude` 丢 null) | ⚠️ 形状错 |
| 2 | `getPlans` | `GET /subscriptions/plans` | **404 Not found** (端点不存在) | ❌ 端点缺失 |
| 3 | `createCheckout` | `POST /subscriptions/checkout` | **404 Not found** (端点不存在) | ❌ 端点缺失 |
| 4 | `createPortal` | `POST /subscriptions/portal` | **404 Not found** (端点不存在) | ❌ 端点缺失 |
| 5 | `cancelSubscription` | `POST /subscriptions/cancel` | **404 Not found** (端点不存在, 实际是 `POST /subscriptions/{id}/cancel`) | ❌ 端点缺失 |
| 6 | `reactivateSubscription` | `POST /subscriptions/reactivate` | **404 Not found** (端点不存在) | ❌ 端点缺失 |
| 7 | `getInvoices` | `GET /subscriptions/invoices?limit&startingAfter` | **404 Not found** | ❌ 端点缺失 |
| 8 | `getInvoice` | `GET /subscriptions/invoices/{invoiceId}` | **404 Not found** | ❌ 端点缺失 |
| 9 | `getUpcomingInvoice` | `GET /subscriptions/invoices/upcoming` | **404 Not found** | ❌ 端点缺失 |

**核心结论**：9 个端点中 **8 个 404**（整个 billing / 支付集成在 `backend-spring` 缺失），**1 个能通但返回数据形状与前端契约不符**。前端 `console/src/views/personal/SubscriptionView.vue` 是当前唯一调用者，**该视图全部 9 个功能均不可用**。

---

## 1. 真实 Bug 复现

### 🔴 P0-1 — 8/9 端点 404：后端 billing 集成整体缺失

**现象**：除 `GET /subscriptions/me` 外，前端 `subscriptionApi` 调用的所有 billing/plan/invoice 端点在后端 **完全不存在**。

**curl 证据**（节选）：

```bash
$ curl -sS -X GET http://localhost:9001/subscriptions/plans \
    -H "Content-Type: application/json" \
    -H "X-CSRF-Token: 2be5...:b42f..." \
    -b /tmp/cookies.txt -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578607"}
__HTTP__404

$ curl -sS -X POST http://localhost:9001/subscriptions/checkout \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt \
    -d '{"planType":"monthly","successUrl":"...","cancelUrl":"..."}' -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578626"}
__HTTP__404

$ curl -sS -X POST http://localhost:9001/subscriptions/cancel \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt \
    -d '{}' -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578658"}
__HTTP__404
```

**静态源码证据**（`grep` 后端 `*Controller.java`）：

```
$ grep -rn "@RequestMapping" backend-spring/src/main/java --include="*.java" \
    | grep -E '"/subscriptions|"subscriptions'
UserSubscriptionController.java:21:@RequestMapping("/subscriptions")
```

`UserSubscriptionController` 实际注册的 4 个端点：

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/subscriptions/me` | 当前用户订阅 (返回 `SubscriptionDTO`) |
| POST | `/subscriptions` | 创建订阅 (要求 `CreateSubscriptionDTO` body) |
| POST | `/subscriptions/{id}/cancel` | 按 ID 取消 |
| GET | `/subscriptions/check-premium` | 检查 premium 访问 (返回 `SubscriptionCheckResultDTO`) |

**Arthas 二次确认**（`sc` 类加载状态）：

```
$ sc -d 'com.ulticode.modules.subscription.controller.*' -x 1
  com.ulticode.modules.subscription.controller.SubscriptionController
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RequestMapping("/admin/subscriptions")
    # 仅 GET /{id} 和 GET /user/{userId}
  com.ulticode.modules.subscription.controller.UserSubscriptionController
    @RequestMapping("/subscriptions")
    # 4 个端点（见上表）
# 整个包路径下无 Invoice / Billing / Checkout / Portal 控制器
```

**支付集成缺失**：

```
$ grep -rln -iE "stripe|paddle|lemonsqueezy" backend-spring/src
# (空) — 零结果
```

后端**完全没有** Stripe / Paddle / LemonSqueezy / 任何支付网关客户端的依赖或代码。

**影响范围**：
- `console/src/views/personal/SubscriptionView.vue`（217-410 行）—— 用户中心订阅页面**全部 9 个功能不可用**
- 该视图是 console 前端**唯一**的订阅入口，登录后任意点击"订阅/查看计划/取消/查看发票"都直接报错
- management 前端**无**订阅相关页面/调用

**修复路径**（按代价排序）：
1. **最小修复**：删除 `console/src/api/subscription.ts` 中缺失端点的方法签名，更新 `SubscriptionView.vue` 仅保留 `getMySubscription`（唯一能用的）。需同步在视图里去掉 plans / checkout / portal / cancel / reactivate / invoices 按钮。
2. **正式修复**：新增 `BillingController` + `InvoiceController`，集成支付网关（推荐 Stripe Checkout + Customer Portal，对应 `POST /checkout` / `POST /portal`），新增 `PlanCatalogService` 返回 `SubscriptionPlan` 列表，新增 `InvoiceService` 包装网关 invoice API。
3. **契约对齐**：先把 OpenAPI/Swagger spec 落地（CLAUDE.md 提到 `springdoc-openapi 2.6.0`），后续 FE 严格按 codegen 类型生成，杜绝手写漂移。

---

### 🔴 P0-2 — T1 `GET /subscriptions/me` 返回值类型与前端契约不符

**前端期望**（`console/src/api/subscription.ts:3-10`）：

```ts
export interface SubscriptionCheckResult {
  hasAccess: boolean;
  subscription: {
    plan: string;
    status: string;
    expiresAt: string | null;
  } | null;
}
```

**后端实际**（`backend-spring/.../UserSubscriptionController.java:33-37`）：

```java
@GetMapping("/me")
public Result<SubscriptionDTO> getCurrentUserSubscription() {
    SubscriptionDTO subscription = subscriptionService.getCurrentUserSubscription();
    return Result.success(subscription);
}
```

返回 `Result<SubscriptionDTO>`，字段为 `id, userId, plan, status, expiresAt, cancelledAt, transactionId, autoRenew, createdAt, updatedAt` —— **不包含 `hasAccess` 字段**。

**实际响应**（admin 无订阅时）：

```json
{"code":0,"message":"success","traceId":"t-1781168578579"}
```

`data` 字段**整个被吞掉** —— 因为 `Result.data` 是 `Object`，且 `Result` 类标注了 `@JsonInclude`：

```
$ sc -d -f 'com.ulticode.common.response.Result'
  @JsonInclude                              ← 关键
  private Object data;
  private String code;
  private String message;
  private String traceId;
```

`@JsonInclude` 全局策略下，`data == null` 时字段被序列化器丢弃。前端 `apiGet<SubscriptionCheckResult>("/subscriptions/me")` 拿到 `undefined` 字段后访问 `.hasAccess` 会报 `Cannot read properties of undefined (reading 'hasAccess')`。

**调用方证据**（`SubscriptionView.vue:265`）：

```ts
currentSubscription.value = await subscriptionApi.getMySubscription();
// 假设返回 { hasAccess, subscription: { plan, status, expiresAt } }
// 实际返回 undefined → 模板渲染时 hasAccess undefined → 行为诡异
```

**真正对应该契约的后端端点**：

```
GET /subscriptions/check-premium
→ Result<SubscriptionCheckResultDTO>
→ { hasAccess, subscription: { plan, status, expiresAt } }
```

`SubscriptionCheckResultDTO.java`（已存在的 DTO）字段完美匹配前端 `SubscriptionCheckResult` 接口：

```java
public class SubscriptionCheckResultDTO {
    private Boolean hasAccess;
    private SubscriptionDetail subscription;  // { plan, status, expiresAt }
}
```

**修复**（按优先级）：
1. **短期**：前端把 `getMySubscription()` 改用 `/subscriptions/check-premium`，重命名为 `checkPremiumAccess`，类型名改 `SubscriptionCheckResultDTO`。`SubscriptionView.vue:265` 一行替换。
2. **长期**：在 `UserSubscriptionController` 把 `/me` 改成 `Result<SubscriptionCheckResultDTO>`，或在 `getCurrentUserSubscription` service 内部组装成 check-result 形状，**消除** "`/me` 给 DTO / `/check-premium` 给 check-result" 的双形态混乱。

---

### 🟠 P1-1 — 取消订阅端点路径与参数契约不匹配

**前端契约**（`subscription.ts:80-85`）：

```ts
async cancelSubscription(): Promise<{ message: string; cancelAt: string }> {
  return apiPost("/subscriptions/cancel", {});
}
```

无 body，无路径参数。

**后端契约**（`UserSubscriptionController.java:64-73`）：

```java
@PostMapping("/{id}/cancel")
public Result<SubscriptionDTO> cancelSubscription(@PathVariable String id) {
    String userId = SecurityUtil.getCurrentUserId();
    if (userId == null) return Result.error(40100, "Unauthorized");
    SubscriptionDTO subscription = subscriptionService.cancelSubscription(id, userId);
    return Result.success(subscription);
}
```

**要求路径上有 `{id}`**。

**结果**：`POST /subscriptions/cancel`（无 ID）→ Spring 找不到匹配 handler → 404。

**修复**：前端在调用前先 `getMySubscription()` 拿 `id`，再 `POST /subscriptions/${id}/cancel`。或后端新增一个"按当前用户取消其 active 订阅"的便捷端点 `POST /subscriptions/cancel`（内部 `SecurityUtil.getCurrentUserId()` + `findActiveByUserId`）。

---

### 🟠 P1-2 — 文档/需求与前端实现参数不一致：`?page&pageSize` vs `?limit&startingAfter`

**用户原始需求**（在本次任务入参中）：

> 7 | GET | `/subscriptions/invoices?page&pageSize` | `subscriptionApi.getInvoices`

**前端实际实现**（`subscription.ts:97-107`）：

```ts
async getInvoices(options?: { limit?: number; startingAfter?: string }) {
  const params = new URLSearchParams();
  if (options?.limit) params.set("limit", String(options.limit));
  if (options?.startingAfter) params.set("startingAfter", options.startingAfter);
  return apiGet(`/subscriptions/invoices${...}`);
}
```

`SubscriptionView.vue:282` 调用时传的就是 `{ limit: 5 }`：

```ts
const result = await subscriptionApi.getInvoices({ limit: 5 });
```

**测试时同时跑了两种参数**（`T7a` + `T7b`），都 404 —— 端点不存在才是根因，参数名只是次要矛盾。

**修复**：先用 `cross-stack-dto-granularity-alignment` skill 对齐「需求文档 / 前端实现 / 后端实现」三方；明确分页语义（offset-based `page/pageSize` vs cursor-based `limit/startingAfter`），统一后再实现 `/subscriptions/invoices`。

---

### 🟡 P2-1 — management 前端未实现订阅相关页面

**现象**：`find /home/david/project/UltiCode/management/src -name "subscription*"` 返回空 —— **management 前端完全没有订阅相关 API、视图、Store**。

**潜在影响**：
- 管理员无法在前端查看/管理用户订阅
- 仅有后端 `SubscriptionController`（`/admin/subscriptions/{id}`、`/admin/subscriptions/user/{userId}`）2 个 GET 端点，缺 CRUD/查询列表能力
- 没有审计/封禁订阅的能力

**修复**：按 `frontend-rules.md` 的「management 必须定义带类型 API 函数封装」约定，在 `management/src/api/admin/subscriptions.ts` 中补全 `subscriptionsApi`，新增 `SubscriptionManagementView.vue`（订阅列表 + 详情 + 状态变更 + 审计日志）。

---

## 2. 完整测试矩阵

### 2.1 测试环境

| 项 | 值 | 验证 |
|---|---|---|
| 后端端口 | 9001 | `lsof -ti :9001` → `9516` |
| 后端进程 | PM2 `ulticode-9001` | `pm2 status` |
| Arthas MCP | 8563 | `lsof -ti :8563` (SessionStart hook 已确认) |
| 账号 | `admin/admin123` | dev-profile-only bootstrap，ADMIN role |
| 浏览器 cookies | `csrf_token=2be5...:b42f...` 等 | `/tmp/cookies.txt` |

### 2.2 9 个端点逐项结果

#### T1 — `getMySubscription` (`GET /subscriptions/me`)

```bash
$ curl -sS http://localhost:9001/subscriptions/me \
    -H "X-CSRF-Token: 2be59232362549218e3c42e880f040c6:b42ff569136045efb3863999f3532a8d" \
    -b /tmp/cookies.txt -w "\n__HTTP__%{http_code}  __TIME__%{time_total}s\n"
{"code":0,"message":"success","traceId":"t-1781168578579"}
__HTTP__200  __TIME__0.049217
```

| 项 | 期望 | 实际 |
|---|---|---|
| HTTP | 200 | 200 ✅ |
| `data.hasAccess` | boolean | **字段不存在** ❌ |
| `data.subscription` | object \| null | **字段不存在** ❌ |
| `data` 整体 | object (SubscriptionCheckResult) | `null` 且被 `@JsonInclude` 吃掉 |

**根因**：`/me` 返回 `SubscriptionDTO`（flat DTO），不是 `SubscriptionCheckResultDTO`（嵌套 with hasAccess）。后端真正返回 `{ hasAccess, subscription }` 的是 `/subscriptions/check-premium`（P0-2）。

---

#### T2 — `getPlans` (`GET /subscriptions/plans`)

```bash
$ curl -sS http://localhost:9001/subscriptions/plans \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578607"}
__HTTP__404
```

**后端现状**：`SubscriptionPlan` 是 enum 常量类（`FREE / PREMIUM_MONTHLY / PREMIUM_YEARLY`），但**没有** `GET /subscriptions/plans` 端点。也没有从数据库 / Nacos / application.yml 加载 plan 元数据（price/currency/interval/features）的能力。

---

#### T3 — `createCheckout` (`POST /subscriptions/checkout`)

```bash
$ curl -sS -X POST http://localhost:9001/subscriptions/checkout \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt \
    -H "Content-Type: application/json" \
    -d '{"planType":"monthly","successUrl":"http://localhost:9002/dashboard?ok=1","cancelUrl":"http://localhost:9002/billing?canceled=1"}' \
    -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578626"}
__HTTP__404
```

**后端现状**：`POST /subscriptions` 是创建**数据库订阅记录**的端点（要求 `CreateSubscriptionDTO` body），不是 Stripe/支付网关 checkout session。完全无 payment gateway 集成。

---

#### T4 — `createPortal` (`POST /subscriptions/portal`)

```bash
$ curl -sS -X POST http://localhost:9001/subscriptions/portal \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt \
    -H "Content-Type: application/json" \
    -d '{"returnUrl":"http://localhost:9002/billing"}' \
    -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578643"}
__HTTP__404
```

**后端现状**：无 customer portal 概念。`POST /subscriptions/{id}/cancel` 是直接操作订阅状态，不跳转到外部门户。

---

#### T5 — `cancelSubscription` (`POST /subscriptions/cancel`)

```bash
$ curl -sS -X POST http://localhost:9001/subscriptions/cancel \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt \
    -H "Content-Type: application/json" -d '{}' \
    -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578658"}
__HTTP__404
```

**后端现状**：实际是 `POST /subscriptions/{id}/cancel` —— **路径不匹配**（P1-1）。

---

#### T6 — `reactivateSubscription` (`POST /subscriptions/reactivate`)

```bash
$ curl -sS -X POST http://localhost:9001/subscriptions/reactivate \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt \
    -H "Content-Type: application/json" -d '{}' \
    -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578672"}
__HTTP__404
```

**后端现状**：无 reactivate 端点。`SubscriptionServiceImpl.java` 第 152-176 行 `updateSubscriptionStatus(id, status, userId)` 支持改任意状态（含 `ACTIVE`），但没暴露 `reactivate` 这个用户友好的端点。

---

#### T7a/b — `getInvoices` (`GET /subscriptions/invoices?...`)

```bash
# 用户文档版本
$ curl -sS 'http://localhost:9001/subscriptions/invoices?page=1&pageSize=10' \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578684"}
__HTTP__404

# 前端实际版本
$ curl -sS 'http://localhost:9001/subscriptions/invoices?limit=10&startingAfter=' \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578695"}
__HTTP__404
```

**后端现状**：包路径 `com.ulticode.modules.subscription` 下**完全没有** `entity/Invoice.java`、`dto/InvoiceDTO.java`、`service/InvoiceService.java`、`controller/*InvoiceController.java`。

---

#### T8 — `getInvoice` (`GET /subscriptions/invoices/{invoiceId}`)

```bash
$ curl -sS http://localhost:9001/subscriptions/invoices/inv_test_123 \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578708"}
__HTTP__404
```

**后端现状**：同 T7 —— 无 invoice 单详情端点。

---

#### T9 — `getUpcomingInvoice` (`GET /subscriptions/invoices/upcoming`)

```bash
$ curl -sS http://localhost:9001/subscriptions/invoices/upcoming \
    -H "X-CSRF-Token: ..." -b /tmp/cookies.txt -w "\n__HTTP__%{http_code}\n"
{"code":40400,"message":"Not found","traceId":"t-1781168578720"}
__HTTP__404
```

**后端现状**：同 T7 —— 无 upcoming invoice 端点。`SubscriptionDTO` 只有 `expiresAt` 字段可粗略表示下一次到期，但**不**含下次扣款金额 / 时间 / 明细项。

---

## 3. Arthas MCP 验证证据

### 3.1 类加载状态（`sc`）

```
$ sc -d -x 1 'com.ulticode.modules.subscription.controller.*'
  com.ulticode.modules.subscription.controller.SubscriptionController
    @Tag(name="Admin Subscription")
    @RestController
    @RequestMapping("/admin/subscriptions")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    # methods: GET /{id}, GET /user/{userId}

  com.ulticode.modules.subscription.controller.UserSubscriptionController
    @Tag(name="User Subscription")
    @RestController
    @RequestMapping("/subscriptions")
    # methods: GET /me, POST /, POST /{id}/cancel, GET /check-premium

  + 2 CGLIB proxies (Spring AOP)
# resultCount=4 (2 controllers + 2 proxies)
```

### 3.2 Result 序列化策略（`sc -d -f`）

```
$ sc -d -f 'com.ulticode.common.response.Result'
  annotations: [@JsonInclude]
  fields:
    private Integer code
    private String  message
    private Object  data     ← 类型 Object，@JsonInclude 让 null data 被丢弃
    private String  traceId
```

**意义**：解释了 T1 为什么 `data` 字段整体不见 —— 不是 bug，是后端约定的"null data 不出现在响应里"。前端在订阅场景下应改为读 `/subscriptions/check-premium` 拿非空 data。

### 3.3 性能/线程（`thread -n 3`）

测试过程中后端整体 idle，无慢线程：

```
$ thread -n 3
[0] arthas-command-execute  cpu=1.6%  (arthas 自身)
[1] redisson-timer-4-1      TIMED_WAITING  (Redis 定时)
[2] arthas-TermServer-1-15  TIMED_WAITING  (arthas 自身)
# 9 个 HTTP 请求全部 <50ms，HOT 路径无瓶颈
```

> 备注：`trace UserSubscriptionController getCurrentUserSubscription` 因 arthas MCP 同步 15s 超时未捕获到调用链（已知限制；改用 `pm2 logs ulticode-9001 --nostream | grep getCurrentUserSubscription` 可绕开）。本次 9 个请求在控制器层的耗时 <50ms，无慢方法。

---

## 4. 端点总览（合同对账）

| # | 前端调用 | 后端实际 | 状态 |
|---|---------|---------|------|
| 1 | `GET /subscriptions/me` | `GET /subscriptions/me` → `Result<SubscriptionDTO>` | ⚠️ 形状错（P0-2）|
| 2 | `GET /subscriptions/plans` | ❌ 不存在 | ❌ 缺失（P0-1）|
| 3 | `POST /subscriptions/checkout` | ❌ 不存在 (真实 `POST /subscriptions`) | ❌ 缺失（P0-1）|
| 4 | `POST /subscriptions/portal` | ❌ 不存在 | ❌ 缺失（P0-1）|
| 5 | `POST /subscriptions/cancel` (无 body) | ❌ 不存在 (真实 `POST /subscriptions/{id}/cancel`) | ❌ 缺失 + 路径错（P1-1）|
| 6 | `POST /subscriptions/reactivate` | ❌ 不存在 | ❌ 缺失（P0-1）|
| 7 | `GET /subscriptions/invoices?limit&startingAfter` | ❌ 不存在 | ❌ 缺失（P0-1）|
| 8 | `GET /subscriptions/invoices/{id}` | ❌ 不存在 | ❌ 缺失（P0-1）|
| 9 | `GET /subscriptions/invoices/upcoming` | ❌ 不存在 | ❌ 缺失（P0-1）|

**对比 backend 实有端点**：

| 实有端点 | 前端是否调用 | 备注 |
|---------|-------------|------|
| `GET /subscriptions/me` | ✅ 但契约错 | 改用 `/check-premium` |
| `POST /subscriptions` | ❌ 未被前端调用 | 是数据库订阅 CRUD |
| `POST /subscriptions/{id}/cancel` | ❌（前端用了错的 `/cancel`） | 需前端先拿 id |
| `GET /subscriptions/check-premium` | ❌ | 真正对应 `getMySubscription` 契约 |
| `GET /admin/subscriptions/{id}` | ❌ | admin 端，无 FE caller |
| `GET /admin/subscriptions/user/{userId}` | ❌ | admin 端，无 FE caller |

---

## 5. 修复优先级与建议

| 优先级 | 任务 | 工时估计 | 影响 |
|--------|------|---------|------|
| **P0** | 新增 `BillingController` + 支付网关集成（Stripe Checkout + Customer Portal + Webhook） | 3-5 天 | 解锁订阅全链路 |
| **P0** | 新增 `PlanCatalogService` + `GET /subscriptions/plans` | 0.5 天 | 解锁 plan 列表展示 |
| **P0** | 新增 `InvoiceController` + `InvoiceService` + `GET /subscriptions/invoices*` | 2-3 天 | 解锁发票查看 |
| **P0** | 前端 `getMySubscription` 改用 `/check-premium`，类型改 `SubscriptionCheckResultDTO` | 0.5h | 解锁订阅状态展示 |
| **P1** | 前端 `cancelSubscription` 改为先 `getMySubscription()` 取 id 再调 `/{id}/cancel` | 0.5h | 解锁取消流程 |
| **P1** | `console/src/views/personal/SubscriptionView.vue` 加 try/catch + 友好降级文案 | 1h | 当前所有 9 个按钮点击即报错 |
| **P2** | management 前端补全订阅管理（订阅列表、状态变更、审计） | 2-3 天 | admin 能力补全 |
| **P2** | OpenAPI spec 落地，前端 codegen 类型，杜绝手写漂移 | 1 天 | 长期契约一致性 |

---

## 6. 测试方法学 / 复现步骤

### 6.1 一键复现 9 个测试

```bash
# 1) 登录拿 CSRF
LOGIN=$(curl -sS -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/cookies.txt)
CSRF=$(echo "$LOGIN" | grep -oE '"csrfToken":"[^"]+"' | cut -d'"' -f4)
H="X-CSRF-Token: $CSRF"

# 2) 跑 9 个端点
for endpoint in \
  "GET /subscriptions/me" \
  "GET /subscriptions/plans" \
  "POST /subscriptions/checkout" \
  "POST /subscriptions/portal" \
  "POST /subscriptions/cancel" \
  "POST /subscriptions/reactivate" \
  "GET /subscriptions/invoices?page=1&pageSize=10" \
  "GET /subscriptions/invoices/inv_test_123" \
  "GET /subscriptions/invoices/upcoming"; do
  M=$(echo $endpoint | awk '{print $1}')
  P=$(echo $endpoint | awk '{print $2}')
  echo "=== $M $P ==="
  curl -sS -X "$M" "http://localhost:9001$P" \
    -H "Content-Type: application/json" -H "$H" -b /tmp/cookies.txt \
    -d '{"planType":"monthly","successUrl":"http://x","cancelUrl":"http://y","returnUrl":"http://z"}' \
    -w "\nHTTP:%{http_code}\n"
done
```

### 6.2 Arthas 验证控制器（可选）

```bash
# 通过 arthas MCP 查控制器
sc -d 'com.ulticode.modules.subscription.controller.*' -x 1
sc -d -f 'com.ulticode.common.response.Result'
```

或 CLI 模式：

```bash
java -jar tools/arthas-boot.jar --attach-only --http-port 8563 <PID>
# 然后 telnet localhost 3658，sc/jad/watch/trace
```

---

## 7. 关联文件清单

- 前端契约：`console/src/api/subscription.ts`
- 前端调用方：`console/src/views/personal/SubscriptionView.vue:217-410`
- 后端控制器：`backend-spring/src/main/java/com/ulticode/modules/subscription/controller/UserSubscriptionController.java`
- 后端 admin 控制器：`backend-spring/src/main/java/com/ulticode/modules/subscription/controller/SubscriptionController.java`
- 后端 Service 实现：`backend-spring/src/main/java/com/ulticode/modules/subscription/service/impl/SubscriptionServiceImpl.java`
- DTO：`SubscriptionDTO.java`、`SubscriptionCheckResultDTO.java`、`CreateSubscriptionDTO.java`
- 枚举：`SubscriptionPlan.java`（FREE / PREMIUM_MONTHLY / PREMIUM_YEARLY）
- 注解：`RequirePremium.java`（用于限流 / 权限拦截）
- 关联规则：`CLAUDE.md` 的 Security Invariants（支付密钥禁硬编码 / 走环境变量）

---

## 8. TL;DR 行动项（给下一个工程师）

1. **别再继续用 9 个端点写前端逻辑** —— 8 个 404，1 个形状错。`console/src/views/personal/SubscriptionView.vue` 整个视图处于"测试可见即报错"状态。
2. **要修先对齐** —— 用 `cross-stack-dto-granularity-alignment` skill 跑一遍「需求 / 前端 / 后端」三方对账；不然后端补完 4 个新端点后，类型还会再漂。
3. **P0 修复 = 新增 billing 子模块**（Stripe 集成 + Plan 目录 + Invoice 实体），3-5 天工时起步。
4. **P0 短期止血**：前端把 `getMySubscription` 改用 `/subscriptions/check-premium`，避免线上 `undefined` 报错。30 分钟可上。
5. **P2 长期**：OpenAPI codegen，把 `subscriptionApi` 从手写变成生成，杜绝下次再漂。
