# Moderation API 实际接口测试报告

| 项目 | 值 |
| --- | --- |
| 生成时间 | 2026-06-07 23:05 UTC+8 |
| 测试环境 | `localhost:9001` (Spring Boot 3.2.5 / Java 17) — PM2 进程 `ulticode-9001` |
| 测试账号 | `admin` / `admin123` (dev-profile 一次性 ADMIN) |
| 认证方式 | HttpOnly Cookie `access_token` + `refresh_token`，写接口附加 `X-CSRF-Token` 头 |
| 总接口数 | 18 (用户列表) + 2 负向鉴权用例 |
| 通过率 | **18 / 18 主路径 = 100%**；2 / 2 负向鉴权用例 = 100% |
| 后端 DB | `ulticode` (MySQL 9.1, 容器 `ulticode-mysql`, UTF-8) |

---

## 1. 执行总览

| # | 方法 | 路径 | 权限 | 期望 | 实际 HTTP | 时延 | 结果 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | GET | `/moderation/queue` | MOD/ADMIN | 200 | **200** | 0.100s | ✅ |
| 2 | GET | `/moderation/queue/{id}` | MOD/ADMIN | 200 | **200** | 0.009s | ✅ |
| 3 | GET | `/moderation/queue/entity/{type}/{id}` | MOD/ADMIN | 200 | **200** | 0.007s | ✅ |
| 4 | GET | `/moderation/queue/stats` | MOD/ADMIN | 200 | **200** | 0.012s | ✅ |
| 5 | POST | `/moderation/queue/{id}/claim` | MOD/ADMIN | 200 | **200** | 0.068s | ✅ |
| 6 | POST | `/moderation/queue/{id}/assign` | ADMIN | 200 | **200** | 0.015s | ✅ |
| 7 | PATCH | `/moderation/queue/{id}/unassign` | MOD/ADMIN | 200 | **200** | 0.013s | ✅ |
| 8 | POST | `/moderation/queue/{id}/action` | MOD/ADMIN | 200 | **200** | 0.028s | ✅ |
| 9 | POST | `/moderation/queue/batch-action` | ADMIN | 200 | **200** | 0.022s | ✅ |
| 10 | POST | `/moderation/reports` | isAuthenticated | 200 | **200** | 0.017s | ✅ |
| 11 | GET | `/moderation/reports/{id}` | MOD/ADMIN | 200 | **200** | 0.011s | ✅ |
| 12 | GET | `/moderation/reports/entity/{type}/{id}` | MOD/ADMIN | 200 | **200** | 0.008s | ✅ |
| 13 | GET | `/moderation/appeals` | MOD/ADMIN | 200 | **200** | 0.053s | ✅ |
| 14 | GET | `/moderation/appeals/my` | isAuthenticated | 200 | **200** | 0.006s | ✅ |
| 15 | GET | `/moderation/appeals/{id}` | isAuthenticated | 200 | **200** | 0.007s | ✅ |
| 16 | GET | `/moderation/appeals/stats` | MOD/ADMIN | 200 | **200** | 0.009s | ✅ |
| 17 | POST | `/moderation/appeals` | isAuthenticated | 200 | **200** | 0.015s | ✅ |
| 18 | POST | `/moderation/appeals/{id}/review` | MOD/ADMIN | 200 | **200** | 0.014s | ✅ |

**结论**：18 个接口主路径全部以 2xx 返回，响应时延均 < 200ms（最慢 100ms / queue 列表分页，最快 6ms / my 列表）。

---

## 2. 负向鉴权测试

| # | 场景 | 调用 | 期望 | 实际 | 结论 |
| --- | --- | --- | --- | --- | --- |
| N1 | 未携带 `access_token` 调 `/moderation/queue` | `curl http://localhost:9001/moderation/queue` | 401 | **401 Unauthorized** `code=40100` | ✅ |
| N2 | 普通 USER 角色调 `/moderation/queue/stats` | 注册新用户 → 调管理端点 | 403 | **403 Forbidden** `code=40300` | ✅ |

权限注解 `@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")` 与 `isAuthenticated()` 均按预期生效。

---

## 3. 各接口实测样本

### 3.1 Queue (9 个)

#### 1) `GET /moderation/queue?page=1&limit=5`
```http
HTTP 200 (99ms)
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      { "id":"mq-test-001", "entityType":"SOLUTION", "entityId":"test-sol-001",
        "authorName":"Development Administrator", "authorUsername":"admin", ... }
    ],
    "total": 1, "page": 1, "limit": 5
  },
  "traceId": "t-1780844395055"
}
```

#### 2) `GET /moderation/queue/mq-test-001` —— 详情
```http
HTTP 200 (9ms)  → 返回完整 ModerationQueueVO，含 authorName/authorUsername 联表字段
```

#### 3) `GET /moderation/queue/entity/SOLUTION/test-sol-001` —— 按实体反查
```http
HTTP 200 (7ms)  → 返回 mq-test-001 单条；`findByEntity` 业务逻辑生效
```

#### 4) `GET /moderation/queue/stats` —— 仪表盘统计
```json
{
  "pendingCount": 0, "underReviewCount": 0,
  "resolvedCount": 0, "dismissedCount": 0,
  "resolvedToday": 2,
  "avgResolutionTimeHours": 8.0,
  "pendingAppealsCount": 1
}
```

#### 5) `POST /moderation/queue/mq-test-002/claim` —— 原子认领
```http
HTTP 200 (68ms)  → 200 OK，状态自动更新为 UNDER_REVIEW
                  → 内部走 `assignToModeratorIfUnassigned` 条件 UPDATE (并发安全)
```

#### 6) `POST /moderation/queue/mq-test-001/assign` —— ADMIN 分配
```http
HTTP 200 (15ms)  → 校验 target user 存在 → 更新 assigned_to_id
                  → 注: `assignedTo` 字段接受 userId (非 username)
```

#### 7) `PATCH /moderation/queue/mq-test-001/unassign` —— 取消分配
```http
HTTP 200 (13ms)  → 已正确清空 assigned_to_id / assigned_at
```

#### 8) `POST /moderation/queue/mq-test-001/action` —— 执审 (DISMISSED)
```http
HTTP 200 (28ms)
请求体: {"action":"DISMISSED","note":"curl test dismissed"}
DB 状态: status=RESOLVED, resolution=DISMISSED, reviewed_by_id=<admin>, resolution_note='curl test dismissed'
         同时写入 moderation_actions 表 (id=59a51157bbd739ae1f446db4398419ca)
```

#### 9) `POST /moderation/queue/batch-action` —— 批量执审
```http
HTTP 200 (22ms)
请求体: {"queueIds":["mq-test-002"],"action":"DISMISSED","note":"curl batch test"}
响应: { "successCount": 1, "failureCount": 0, "errors": [] }
DB 状态: mq-test-002 → RESOLVED/DISMISSED
```

> **注意点**：`batch-action` 在 `successCount==0 && !errors.isEmpty()` 时抛 `BAD_REQUEST "All batch operations failed"` (HTTP 400)。这意味着 **批量接口对单条失败是有弹性的**——只要有一条成功就会返回 200 + 部分错误。

---

### 3.2 Reports (3 个)

#### 10) `POST /moderation/reports` —— 创建举报
```http
HTTP 200 (17ms)  → 成功路径 (指向 test-sol-002)
请求体: {"entityType":"SOLUTION","entityId":"test-sol-002","category":"HARASSMENT","reason":"curl test report 2","evidence":"evidence 2"}
DB 状态: id=4a08eee97396a9774a72ef7b7ca310a9, status=PENDING, queue_id=mq-test-002 (自动创建/复用队列)

# 同一用户对同一实体重复举报 → 409 Conflict (业务去重)
HTTP 409 (177ms)
{"code":100003,"message":"You have already reported this content","traceId":"t-..."}
```

#### 11) `GET /moderation/reports/rep-test-001` —— 详情
```http
HTTP 200 (11ms)  → 返回 ReportVO，含 reporterName / reporterUsername 联表
```

#### 12) `GET /moderation/reports/entity/SOLUTION/test-sol-001` —— 实体全部举报
```http
HTTP 200 (8ms)  → 列表返回 (1 条)
```

---

### 3.3 Appeals (6 个)

#### 13) `GET /moderation/appeals?page=1&limit=5` —— 分页列表
```http
HTTP 200 (53ms)  → 1 条 PENDING 申诉
```

#### 14) `GET /moderation/appeals/my` —— 当前用户自己的申诉
```http
HTTP 200 (6ms)  → 1 条 (admin 用户作为 appellant)
```

#### 15) `GET /moderation/appeals/appeal-test-001` —— 详情
```http
HTTP 200 (7ms)
```
> ⚠️ 注意：此接口权限为 `isAuthenticated()`，即**只要登录就能看任意申诉**——本测试账号是 appellant 本人故正常返回；若以其他账号调用则等同于越权读取。

#### 16) `GET /moderation/appeals/stats` —— 仪表盘统计
```json
{
  "totalPending": 1, "totalUnderReview": 0,
  "totalApproved": 1, "totalRejected": 0,
  "avgReviewTimeHours": null
}
```

#### 17) `POST /moderation/appeals` —— 创建申诉
```http
HTTP 200 (15ms)
请求体: {"queueId":"mq-test-002","reason":"curl test appeal","evidence":"evidence 1"}
响应: { "id":"b97368a969e776a9de41d711173b829a", "status":"PENDING", ... }
```

#### 18) `POST /moderation/appeals/{id}/review` —— 审核申诉
```http
HTTP 200 (14ms)
请求体: {"decision":"APPROVED","response":"appeal approved by curl test"}
DB 状态: status=APPROVED, response='appeal approved by curl test', reviewed_by_id=<admin>, reviewed_at=2026-06-07 23:05:03
```

> ⚠️ **接口字段命名不一致 (代码契约发现)**：
> `ReviewAppealDTO` 实际字段是 **`decision`** (`@NotBlank String decision`)，**不是** `status`。
> Controller 的 Swagger summary 写为 "Review an appeal"，但未明确字段名。
> 第一次测试用 `{"status":"APPROVED",...}` 收到 40000 Validation failed。
> **建议**：在 OpenAPI `@Operation` 描述里补 `decision` 字段说明，或为前端管理端 `appealsApi.reviewAppeal` 类型补一行 JSDoc。

---

## 4. 数据库一致性验证 (after-test)

```sql
SELECT 'moderation_queue' tbl, COUNT(*) cnt FROM moderation_queue
UNION ALL SELECT 'reports', COUNT(*) FROM reports
UNION ALL SELECT 'appeals', COUNT(*) FROM appeals
UNION ALL SELECT 'moderation_actions', COUNT(*) FROM moderation_actions;
```

| 表 | 行数 | 备注 |
| --- | --- | --- |
| `moderation_queue` | 2 | mq-test-001 / mq-test-002 均为 RESOLVED + DISMISSED |
| `reports` | 2 | rep-test-001 (DISMISSED) + curl 新建 4a08eee9… (PENDING) |
| `appeals` | 2 | appeal-test-001 (PENDING) + curl 新建 b97368a9… (APPROVED) |
| `moderation_actions` | 4 | 1 个手工 seed + 3 个 curl 操作流水 |

`moderation_actions` 流水时间线：

```
2026-06-07 23:03:32.324  mq-test-002  DISMISSED  curl batch test
2026-06-07 23:03:32.293  mq-test-001  DISMISSED  curl test dismissed
2026-06-07 23:01:15.230  mq-test-001  DISMISSED  curl test dismissed   ← 上一轮
2026-06-07 15:00:28.?    mq-test-001  DISMISSED  (seed 阶段)
```

> ✅ **可观察副作用**：
> - `claim` → `assigned_to_id` 写入，状态变为 UNDER_REVIEW
> - `assign` → 校验 user 存在；`assigned_to_id` / `assigned_at` 写入
> - `unassign` → `assigned_to_id` / `assigned_at` 清空
> - `action` / `batch-action` → 状态转 RESOLVED / 写入 `resolution` / `reviewed_by_id` / `reviewed_at` / `moderation_actions` 流水
> - `appeals.review` → `status` 转 APPROVED / `response` / `reviewed_by_id` / `reviewed_at` 写入

---

## 5. 风险与改进建议

| 等级 | 位置 | 描述 | 建议 |
| --- | --- | --- | --- |
| MEDIUM | `ModerationController.reviewAppeal` | Swagger summary 没有声明 `decision` 字段；DTO 字段名 `decision` 不直观（前端很容易传成 `status`） | 在 `@Operation` 加 `@Parameter` 或在 response schema 注明；考虑重命名为 `decision` ↔ `outcome` 让命名更清晰 |
| MEDIUM | `GET /moderation/appeals/{id}` | 注解为 `isAuthenticated()`，未做 appellant/reviewer/admin 校验 — **理论上普通用户可以遍历其他人的申诉 ID** | 增加 service 层断言：仅 appellant 本人 / 已分配审核员 / MOD 角色可读 |
| LOW | `GET /moderation/queue/stats.byCategory` / `byEntityType` | 当前响应中 `byCategory:null, byEntityType:null`，统计维度尚未实现 | 后续可补 group by SQL |
| LOW | `GET /moderation/appeals/{id}` | 缺少 rate-limit 注解 | 防止批量爬取申诉内容，可加 `@RateLimit` |
| LOW | `POST /moderation/queue/batch-action` | 全部失败时仅返回 `code=40000 "All batch operations failed"`，没有指明哪条 ID 失败 | 在 `errors[]` 数组里返回每条 ID 的错误（当前 `BatchError` VO 已具备，但 ALL-FAILED 抛异常时未透出） |

---

## 6. 测试用例模板 (复现步骤)

完整可重放脚本位于 `/tmp/moderation_*.txt`、`/tmp/last_body.json` 缓存与本仓库暂存的 cookie 文件 `~/.claude/...`。复现：

```bash
# 1) 登录
curl -c /tmp/c.txt -X POST http://localhost:9001/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'

# 2) 拉 csrf
CSRF=$(curl -b /tmp/c.txt http://localhost:9001/auth/me | jq -r .data.csrfToken)

# 3) 调任意接口
curl -b /tmp/c.txt -H "X-CSRF-Token: $CSRF" \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:9001/moderation/queue/mq-test-001/action \
  -d '{"action":"DISMISSED","note":"repro"}'
```

---

## 7. 结论

✅ **18/18 主路径接口功能可用**，HTTP 2xx 全部返回，业务副作用与 DB 状态一致。
✅ 鉴权 / 角色注解按设计生效（401 / 403 行为正确）。
✅ 所有接口时延 < 200ms，可投入运营使用。

⚠️ 上线前建议修复上述 MEDIUM 级问题（`appeals/{id}` 越权读取、`reviewAppeal` 字段命名一致）。
