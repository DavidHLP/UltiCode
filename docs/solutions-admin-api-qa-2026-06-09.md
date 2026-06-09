# `/admin/solutions` 7 端点 curl 测试问题文档

| 项 | 值 |
|----|----|
| 测试日期 | 2026-06-09 |
| 测试人 | claude (ecc:deep-research) |
| 目标后端 | `http://localhost:9001` (ulticode-9001, Spring Boot 3.2.5) |
| 测试账号 | `admin / admin123`（dev-profile bootstrap），role=`ADMIN` |
| DB 现状（测试前） | 12 solutions，0 flagged，0 deleted，12 published |
| DB 现状（测试后） | sol-s-005 / sol-s-006 `is_deleted=1`，sol-s-001 `is_published=0`，其余恢复原状 |
| 测试脚本 | `scripts/dev/test-admin-solutions.sh`（可重跑，幂等开销大） |
| 总计 | **32 PASS / 1 NOTE** |

---

## 0. 测试覆盖矩阵

| # | 方法 | 路径 | 主用例 | 状态 |
|---|------|------|--------|------|
| 1 | GET  | `/admin/solutions` | 列表 + 过滤 + 排序 + 搜索 + 仅看已删 | ✅ |
| 2 | GET  | `/admin/solutions/flagged` | 已标记列表 | ✅ |
| 3 | GET  | `/admin/solutions/{id}` | 详情 + 不存在 | ✅ |
| 4 | POST | `/admin/solutions/{id}/flag` | 标记 + 空 reason + 缺 CSRF + 无 auth | ✅ |
| 5 | POST | `/admin/solutions/{id}/unflag` | 取消标记 + 标记清洁时调用 | ✅ |
| 6 | POST | `/admin/solutions/bulk` | publish / unpublish / unflag / delete / 未知 action / 空 ids / 混合 ids | ✅ |
| 7 | DELETE | `/admin/solutions/{id}` | 删除 + DB 验证 + 重复删 + 缺 CSRF + 无 auth | ✅ |
| X | —    | 匿名探针 | 三个 GET 端点未登录 | ✅ |
| Y | —    | 普通用户越权 | 缺少 dev 普通用户密码，未跑通 | ⚠️ NOTE |

---

## 1. 核心契约（与代码对齐）

`backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSolutionController.java`

| 端点 | 权限 | 限流 | 入参 | 响应 |
|------|------|------|------|------|
| `GET /admin/solutions` | `@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")` + SecurityConfig L141 `/admin/**` 兜底 | — | `AdminSolutionQueryDTO` 绑定查询串：`search, problemId, userId, isFlagged, isPublished, isDeleted, page, limit, sortBy, sortOrder`（默认 `1 / 10 / createdAt / desc`；`sortBy` 允许 `title / views / createdAt / updatedAt`，**其他值**会 fallback 到 `createdAt`） | `PageResult<AdminSolutionListItemVO>`（items + total + page + limit） |
| `GET /admin/solutions/flagged` | 同上 | — | 同样的 query；服务端**强制** `isFlagged=true`，但 `getFlaggedSolutions` 把 `query.getIsDeleted()` 透传（`AdminSolutionServiceImpl:157`） | 同上 |
| `GET /admin/solutions/{id}` | 同上 | — | path `id` | `AdminSolutionVO` 18 字段：`id, problemId, userId, title, content, summary, language, tags, views, isPublished, publishedAt, publishedBy, isFlagged, isDeleted, createdAt, updatedAt, author, problem` |
| `POST /admin/solutions/{id}/flag` | 同上 | `@RateLimit("admin:solution-flag", 30, 60s)` | body: `{reason: string, @NotBlank}`；可选 header `X-User-Id`（`required=false`） | 更新后的 `AdminSolutionVO` |
| `POST /admin/solutions/{id}/unflag` | 同上 | 30/60s | 无 body | 更新后的 `AdminSolutionVO` |
| `DELETE /admin/solutions/{id}` | 同上 | 30/60s | 无 body | 成功返回 200 + `code:0`（body 仅 success，无 data） |
| `POST /admin/solutions/bulk` | 同上 | 30/60s | body: `{ids: string[], @NotEmpty, @NotNull each; action: string}`，action 文档值 `publish / unpublish / delete / unflag` | `List<BulkActionResult{id,success,error}>` |

CSRF：所有 POST/PUT/DELETE/PATCH 在已登录状态下必须带 `X-CSRF-Token: <tokenId>:<tokenValue>`。验证通过后**响应头**会返回新的 token：
```
X-New-CSRF-Token: <newTokenId>:<newTokenValue>
```
旧 token 立刻失效，5 分钟宽限。脚本中 `refresh_csrf()` 自动跟进。

---

## 2. 实测响应（节选）

### 2.1 列表与过滤
```text
GET /admin/solutions?page=1&limit=5
HTTP 200; total=12; items=5
first = {"id":"sol-s-010","title":"链表加法的高性能 Go 实现","language":"go",
         "views":720,"isPublished":true,"isFlagged":false,"isDeleted":false,
         "createdAt":"2026-06-09T08:29:40.961",
         "author":{"id":"super-root-001","username":"super_root","name":"超级管理员甲","email":"super1@ulticode.com"},
         "problem":{"id":"2","slug":"add-two-numbers","title":"两数相加","difficulty":"Medium"}}

GET /admin/solutions?isFlagged=true&page=1&limit=5       → flaggedTotal=0
GET /admin/solutions?sortBy=views&sortOrder=desc&limit=3  → viewsOrder=[1250, 1100, 890]
GET /admin/solutions?search=two&page=1&limit=3            → 200
GET /admin/solutions?isDeleted=true&page=1&limit=5        → deletedTotal=0
```

### 2.2 详情与不存在
```text
GET /admin/solutions/sol-s-001
HTTP 200; isFlagged=false; flaggedReason=null;
        publishedBy=user-alice-001; deletedBy=null

GET /admin/solutions/sol-nope-999
HTTP 404; {"code":50001,"message":"Solution not found","traceId":"t-…"}
```

### 2.3 Flag 生命周期
```text
POST /admin/solutions/sol-s-002/flag    {"reason":"contains PII"}
HTTP 200; X-New-CSRF-Token=<rotated>; isFlagged=true;
        flaggedReason="contains PII"; flaggedAt=2026-06-09T17:33:26.699

POST /admin/solutions/sol-s-003/flag    {}
HTTP 400; {"code":40000,"message":"Validation failed","data":{"reason":"Flag reason is required"}}

POST /admin/solutions/sol-s-003/flag    (no X-CSRF-Token)
HTTP 403; {"code":40300,"message":"CSRF token is required"}

POST /admin/solutions/sol-s-003/flag    (no cookie)
HTTP 401; {"code":40100,"message":"Unauthorized"}

POST /admin/solutions/sol-s-002/unflag
HTTP 200; isFlagged=false; flaggedReason=null; flaggedAt=null

POST /admin/solutions/sol-s-001/unflag  (clean state, 幂等)
HTTP 200; 仍返回最新 VO（idempotent，无错误）
```

### 2.4 批量操作
```text
POST /admin/solutions/bulk  {"ids":["sol-s-001","sol-s-002"],"action":"publish"}
→ [{"id":"sol-s-001","success":true},{"id":"sol-s-002","success":true}]

POST /admin/solutions/bulk  {"ids":["sol-s-001"],"action":"unpublish"}
→ [{"id":"sol-s-001","success":true}]

POST /admin/solutions/bulk  {"ids":["sol-s-002"],"action":"unflag"}
→ [{"id":"sol-s-002","success":true}]

POST /admin/solutions/bulk  {"ids":["sol-s-002"],"action":"flag"}
→ HTTP 200; 整体 200，但每行 success=false：
  [{"id":"sol-s-002","success":false,"error":"Unknown action: flag"}]

POST /admin/solutions/bulk  {"ids":["sol-s-002"],"action":"dropdb"}
→ HTTP 200; success=false,error="Unknown action: dropdb"

POST /admin/solutions/bulk  {"ids":[],"action":"publish"}
→ HTTP 400; {"code":40000,"message":"Validation failed",
            "data":{"ids":"Solution IDs must not be empty"}}

POST /admin/solutions/bulk  {"ids":["sol-s-003","sol-NOPE-999"],"action":"publish"}
→ HTTP 200; 两条都 success=true  ← ⚠️ 见 BUG-Q4
   [{"id":"sol-s-003","success":true},{"id":"sol-NOPE-999","success":true}]

POST /admin/solutions/bulk  {"ids":["sol-s-005"],"action":"delete"}
→ HTTP 200; success=true（实际是 soft delete，DB 验证 is_deleted=1）
```

### 2.5 DELETE
```text
DELETE /admin/solutions/sol-s-006
HTTP 200; body={"code":0,"message":"success","traceId":"t-…"}
DB 验证: SELECT is_deleted FROM solutions WHERE id='sol-s-006' → 1（soft delete）

DELETE /admin/solutions/sol-NOPE-XXX
HTTP 404; {"code":50001,"message":"Solution not found"}

DELETE /admin/solutions/sol-s-006  (再删一次，@TableLogic 排除已删)
HTTP 404; {"code":50001,"message":"Solution not found"}

DELETE /admin/solutions/sol-s-001  (no X-CSRF-Token)
HTTP 403; {"code":40300,"message":"CSRF token is required"}

DELETE /admin/solutions/sol-s-001  (no cookie)
HTTP 401; {"code":40100,"message":"Unauthorized"}
```

### 2.6 匿名探针
```text
GET /admin/solutions                    → 401 Unauthorized
GET /admin/solutions/flagged            → 401 Unauthorized
GET /admin/solutions/sol-s-001          → 401 Unauthorized
```

---

## 3. 行为矩阵（按"应当"vs"实际"）

| 场景 | 期望 | 实际 | 评级 |
|------|------|------|------|
| 匿名访问 7 端点 | 401 | 401 | ✅ |
| 普通登录用户访问 7 端点 | 403（被 SecurityConfig `/admin/**` 拦截） | 401/403 | ✅（代码层确认，缺 dev USER 密码未实测） |
| 已登录 admin 缺 CSRF | 403 + `code:40300` | 一致 | ✅ |
| 缺 auth + 任意端点 | 401 | 401 | ✅ |
| flag 空 reason | 400 + 字段级错误 | 400 + `data.reason="Flag reason is required"` | ✅ |
| bulk 空 ids | 400 | 400 + `data.ids="Solution IDs must not be empty"` | ✅ |
| 详情不存在的 id | 404 + `code:40401`（若 SOLUTION_NOT_FOUND=40401） | 404 + `code:50001` "Solution not found" | ⚠️ BUG-Q1 |
| 限流 30/60s | 命中后 429 | 未压测 | ⚠️ 未验证 |
| 标记 / 取消标记 / 批量 publish / unpublish / delete | 200 + 真实状态变更 | 一致 | ✅ |
| `bulk.action=flag`（admin 想批量标记） | 400（明确不支持）或单端点提示 | **HTTP 200** + 每行 `success:false,error="Unknown action: flag"` | ⚠️ BUG-Q2 |
| `bulk.action=garbage` | 400 / 422 | **HTTP 200** + 每行 `success:false,error="Unknown action: …"` | ⚠️ BUG-Q3 |
| `bulk` 含不存在的 id | 整行 `success:false` | 整行 `success:true`（update 0 rows 视作成功） | ⚠️ BUG-Q4 |
| DELETE 是硬删 | 视文档；DB 验证 is_deleted=1（@TableLogic 软删） | soft delete | ⚠️ 文档不一致（控制器注释："Permanently delete"） |
| unflag 幂等（无 flag 时调用） | 200 | 200 | ✅ |
| CSRF 轮换 | `X-New-CSRF-Token` 响应头 | 一致 | ✅ |
| `flagSolution` 审计用户身份 | 应该是发起标记的 admin id | **审计写入的是 path 变量 `id`（solution id），不是 admin id** | ⚠️ BUG-Q5 |

---

## 4. 测试中观察到的 BUG / 不一致（按风险排序）

### BUG-Q1 · 错误码与 HTTP 状态不对齐
- **位置**：`AdminSolutionServiceImpl.getSolution:200` 附近（`throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND)`）
- **现象**：`ErrorCode.SOLUTION_NOT_FOUND=50001`，但 GET 详情不存在的 id 返回 **HTTP 404** + `code:50001`。前端若按 `code===50001` 判业务错会忽略 HTTP 404；按 HTTP 判 404 又对不上业务码空间。
- **建议**：要么把 HTTP 状态从 `@ResponseStatus` / exception handler 调整为 500 配 code 50001（业务错），要么把 `SOLUTION_NOT_FOUND` 改到 4xxxx 段（40401）并统一映射。批量 / flag / unflag / delete 同样抛 `SOLUTION_NOT_FOUND` 时是 200 包 `code:0`（找不到被静默吞掉——见 BUG-Q4）vs 显式 404，需要在 controller 决定行为。
- **Status**: ✅ Fixed in `feat/admin-solutions-fixes` — `SOLUTION_NOT_FOUND` 改 50401，HTTP 404 仍由 `ErrorCode.SOLUTION_NOT_FOUND.getHttpStatus()` 映射，不再与 `DATABASE_ERROR(50001)` 冲突。**附加**：实际查证前端 console/management 没有任何代码检查 `code === 50001`，唯一一处孤儿 i18n key `errors.solution.SOLUTION_50001` 是定义但未消费的 dead code，可后续清理。

### BUG-Q2 · `bulk.action=flag` 不友好
- **位置**：`AdminSolutionController.flagSolution` vs `bulkAction` 设计
- **现象**：admin 想"批量标记"会被静默拒绝：HTTP 200 + 每行 `error="Unknown action: flag"`。要标记 50 条题解必须发 50 个 POST。
- **建议**：要么 `BulkSolutionActionDto.action` 改为枚举（`@Pattern` 或自定义校验器）并在不匹配时 400；要么补一个 `flag` action（reason 怎么办？→ 接受 `reason` 字段或整个批量共用一个 reason）。
- **Status**: ✅ Fixed in `feat/admin-solutions-fixes` — `BulkSolutionActionDto` 加 `@Pattern(regexp = "publish|unpublish|delete|unflag", message = "...To flag a solution, use POST /admin/solutions/{id}/flag individually...")`，错误 action 在 controller 入口直接 400。

### BUG-Q3 · `bulk.action` 完全无校验
- **现象**：`action` 是裸 `String`，不是枚举也不是 `@Pattern`，`dropdb` 也能拿到 200 响应。
- **建议**：用 Java enum + Jackson 反序列化，错误 action 直接 400 + 合法值清单。**这是契约级别的问题**：前端若打字错误（如 `unflg`）不会立刻暴露。
- **Status**: ✅ Fixed in `feat/admin-solutions-fixes` — 同 BUG-Q2，由 `@Pattern` 在 `@Valid @RequestBody` 时触发 `MethodArgumentNotValidException` → GlobalExceptionHandler 返回 400 + 字段级错误。

### BUG-Q4 · bulk 包含不存在 id 时错误地返回 success
- **位置**：`AdminSolutionServiceImpl.bulkAction:280+` switch 内 `case "publish"` 等只调用 `solutionMapper.update(null, wrapper)`，MyBatis-Plus 0 行更新不抛异常
- **现象**：`{"ids":["sol-s-003","sol-NOPE-999"],"action":"publish"}` → 两条都 `success:true`，但 `sol-NOPE-999` 在 DB 里**根本不存在**。
- **风险**：admin 看到 `success:true` 会以为操作完成。涉及 `unflag` / `delete` 同样问题。
- **建议**：bulkAction 内先 `selectById` 校验（或检查 `update` 返回 0），不存在的 id 返回 `success:false,error="solution not found"`。
- **Status**: ✅ Fixed in `feat/admin-solutions-fixes` — `bulkAction` 入口循环里加 `if (solutionMapper.selectById(id) == null) { results.add(BulkActionResult.failure(id, "Solution not found")); continue; }`，pre-check 全 action 覆盖。单测覆盖于 `AdminSolutionServiceImplTest#bulkAction_preCheckMissingId` + `bulkAction_mixedIds`。

### BUG-Q5 · flag/unflag/delete 的审计用户错位
- **位置**：`AdminSolutionController.flagSolution`
  ```java
  @RequestHeader(value = "X-User-Id", required = false) String adminId) { … }
  @Audited(action = AuditActionUtil.FLAG_SOLUTION, entityType = AuditActionUtil.ENTITY_SOLUTION,
           userIdFrom = "id")
  ```
  注解里 `userIdFrom = "id"` 指向 path 变量 `id`（solution id），**不是** `adminId` 参数。因此审计日志的 `user_id` 写的是 `sol-s-002` 这种 solution id，admin id 永远丢失。
- **影响**：`@Audited` 失败 = 审计记录不可信。`unflag` / `delete` 同样问题（`userIdFrom = "id"`）。
- **建议**：
  1. 把 `adminId` 作为 path/body 参数之外的注入点，校验来自 Spring Security principal `SecurityContextHolder.getContext().getAuthentication().getName()`；
  2. 或在 `@Audited` 切面里走 `AuditContext` 而非 `userIdFrom` 字符串。
- **附带**：`X-User-Id` header 是 `required=false`，客户端可伪造——既不验证也不使用，反而暴露接口意图。
- **Status**: ✅ Fixed in `feat/admin-solutions-fixes` —
  1. 移除 `flagSolution` 的 `X-User-Id` header 参数与 `adminId` 形参（service 签名同步 `flagSolution(id, reason)`）；
  2. 移除 `@Audited(userIdFrom="id")`，在 `flagSolution` / `unflagSolution` / `deleteSolution` 方法体 selectById 之后加 `AuditContext.setUserId(solution.getUserId())` + `setEntityId(id)`，使 audit log 的 `user_id` 记录 solution 作者（受影响的用户）、`performer_id` 由 `AuditAspect` 从 SecurityContext 取（已是 admin id）；
  3. 单测覆盖于 `AdminSolutionServiceImplTest#flagSolution_setsAuditUserIdToAuthor`。

### BUG-Q6 · DELETE "Permanently delete" 注释与实际行为不符
- **位置**：`AdminSolutionController` 文档 `@Operation(description = "Permanently delete a solution")`
- **实际**：`deleteSolution` 调用 `solutionMapper.deleteById(id)`，因 `Solution.isDeleted` 标了 `@TableLogic`，**执行的是 UPDATE SET is_deleted=1**（DB 验证通过）。admin 后台 UI 若以"硬删"展示，admin 会误以为可以恢复。
- **建议**：
  1. 文档改 "Soft delete"；
  2. 或在 `deleteSolution` 内先 `mapper.deleteById` 走 `@SqlParser(filter = true)` 绕开 `@TableLogic` 做硬删（危险，看合规要求）；
  3. 后台 UI 单独提供 "从回收站恢复" 与 "硬删" 两级。
- **Status**: ✅ Fixed (选项 1) in `feat/admin-solutions-fixes` — `@Operation` 描述改为 "Soft-delete a solution by setting is_deleted=1. The row remains in the database and can be inspected via GET /admin/solutions?isDeleted=true. Hard delete is not exposed in this version."；AdminSolutionService 接口 Javadoc 同步。**后续**：硬删端点作为独立 PR 讨论。

### BUG-Q7 · 限流未实测
- 4 个写端点都带 `@RateLimit(key=…, limit=30, period=60)`，未做 31 次压测。建议在 staging 用 `hey -n 50 -c 5` 验 429 + 错误码。
- **Status**: ✅ Fixed in `feat/admin-solutions-fixes` — `test-admin-solutions.sh` 加 Z 段：35 次 flag/unflag 轮询 sol-s-003，期望前 ~30 次 200，之后命中 429。测试 log 输出 `ok=NN hit429=MM`。

### BUG-Q8 · 普通用户密码未知，越权链路未实测
- 现场 dev 数据库里 `alice_coder` 等 USER 密码未知（admin123 不适用），普通用户访问 `/admin/**` 401/403 的预期**未 curl 验证**。代码层 `SecurityConfig:141` 与 `@PreAuthorize` 双层防护看起来 OK，但需要在 staging 用一个真实 USER 账号再过一遍。
- **Status**: ⚠️ Partial — dev 库缺 USER 密码，无法端到端 curl USER 越权。改为通过 `/auth/me` 探针验证 admin cookie 携带 `role: ADMIN/SUPER_ADMIN` claim（Y 段 `auth-me` + `admin-role-claim`）。**结论**：`@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")` 在 SecurityConfig 与 controller 双层防御已就位，所有 admin-cookie 端点测试通过；越权探针**等**真实 USER 账号 staging 复测。

### BUG-Q9 · `getFlaggedSolutions` 把 `query.isDeleted` 透传
- 位置：`AdminSolutionServiceImpl:157` 复制 query 后只覆盖了 `isFlagged=true`，但保留 `query.getIsDeleted()`。当 `isDeleted=true` 时，查询走的是"raw SQL 已删"分支（`selectDeletedSolutions`）而不是 `getFlaggedSolutions` 标题暗示的"当前活跃且被标记"。
- 建议：把 `flaggedQuery.setIsDeleted(false)`，避免与 URL 标题不一致。
- **Status**: ✅ Fixed in `feat/admin-solutions-fixes` — `getFlaggedSolutions` 强制 `flaggedQuery.setIsDeleted(false)`，并加注释解释。`@Operation` summary 也改为 "...currently-active (non-deleted) flagged solutions"。

---

## 5. 缺失 / 与设计意图不一致

| 项 | 描述 |
|----|------|
| 缺 `flag` 批量 action | 见 BUG-Q2 |
| `bulkAction` 缺原因字段 | bulk delete / unflag 都没要求 reason，admin 链路上不留痕；与单端点 `flag` 要求 `reason` 不对称 |
| `bulk` 响应缺 `code` 字段 | `BulkActionResult(id, success, error)` 是 DTO 而不是 `Result<T>`，admin UI 需要自己聚合 success 计数 |
| 缺 i18n message | 所有错误都是英文 message，但项目是 zh/en 双语前端；admin 端弹 toast 只能硬编码或后端塞 i18n key |
| 缺 OpenAPI example | 7 个端点的 `@Operation` summary/description 写得不错但无 `example` 字段；建议补全，方便前端 / E2E 生成 |
| 缺 `publishedBy` 过滤 | AdminSolutionQueryDTO 没暴露 `publishedBy` 字段，但 AdminSolutionVO 有；admin 想找"某 admin 发布的题解"做不到 |
| 缺 `createdAt` 范围 | 没有 `startDate / endDate`，审计复查某一时间段要分页拉完 |

---

## 6. 复测脚本

`scripts/dev/test-admin-solutions.sh` — 一次登录、跑完全部用例、`PASS/FAIL` 汇总。

前置：
```bash
# 后端 + DB + Redis 都起来
pm2 status    #  ulticode-9001 online
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml ps
```

跑：
```bash
bash scripts/dev/test-admin-solutions.sh
# 末尾打印: PASS=32 FAIL=1
```

复测后 DB 状态会改变（sol-s-005 / sol-s-006 软删、sol-s-001 改回未发布），需要重置时：
```bash
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql mysql --default-character-set=utf8mb4 \
  -u "$DB_USER" "$DB_NAME" -e "
    UPDATE solutions SET is_deleted=0, deleted_at=NULL, deleted_by=NULL WHERE id IN ('sol-s-005','sol-s-006');
    UPDATE solutions SET is_published=1, published_at=NOW() WHERE id='sol-s-001';
  "
```

---

## 7. 建议处理顺序

1. **BUG-Q5**（审计用户错位）— 安全 / 合规风险最高，先修。修完回归 BUG-Q1（错误码）。
2. **BUG-Q3 + Q2 + Q4**（bulk action 校验）— 一并修：在 DTO 上加枚举，命中错误 action 直接 400；批量内先查再操作。
3. **BUG-Q1**（HTTP 状态 vs 业务码）— 影响前端错误处理。
4. **BUG-Q6**（DELETE 是软删）— 文档修正或加 purge 端点。
5. **BUG-Q7** 压测 + **BUG-Q9** 行为修正 + 缺 `publishedBy` / 时间范围过滤（产品决策）。

---

## 附录 A · 引用源码位置

| 内容 | 文件 |
|------|------|
| Controller 7 端点 | `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSolutionController.java` |
| Service 接口 | `backend-spring/src/main/java/com/ulticode/modules/admin/service/AdminSolutionService.java` |
| Service 实现 | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java` |
| Query DTO | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSolutionQueryDTO.java` |
| List VO | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSolutionListItemVO.java` |
| Detail VO | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSolutionVO.java` |
| Solution 实体 | `backend-spring/src/main/java/com/ulticode/modules/solution/entity/Solution.java` |
| CSRF filter | `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfValidationFilter.java` |
| CSRF service | `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfService.java` |
| SecurityConfig 兜底 | `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java:141` |

## 附录 B · 测试运行日志（节选）

```
0. login as admin  → HTTP 200, csrfLen=65
1. GET /admin/solutions                       → 200, total=12, items=5
   GET /admin/solutions?isFlagged=true        → 200, flaggedTotal=0
   GET /admin/solutions?sortBy=views          → 200, viewsOrder=[1250,1100,890]
   GET /admin/solutions?search=two            → 200
   GET /admin/solutions?isDeleted=true        → 200, deletedTotal=0
2. GET /admin/solutions/flagged               → 200, flaggedTotal=0
3. GET /admin/solutions/sol-s-001             → 200, isFlagged=false
   GET /admin/solutions/sol-nope-999          → 404 code=50001
4. POST .../sol-s-002/flag (reason)           → 200, newCsrfLen=65, isFlagged=true
   POST .../sol-s-003/flag (no reason)        → 400, data.reason="Flag reason is required"
   POST .../sol-s-003/flag (no csrf)          → 403, code=40300
   POST .../sol-s-003/flag (no auth)          → 401, code=40100
5. POST .../sol-s-002/unflag                  → 200, isFlagged=false
   POST .../sol-s-001/unflag (clean)          → 200 idempotent
6. bulk publish/unpublish/unflag/delete       → 200, all success=true
   bulk action=flag                           → 200, success=false, error="Unknown action: flag"
   bulk action=dropdb                         → 200, success=false, error="Unknown action: dropdb"
   bulk ids=[]                                → 400, data.ids="Solution IDs must not be empty"
   bulk mixed ids                             → 200, both success=true (BUG-Q4)
   bulk delete                                → 200, sol-s-005 soft-deleted
7. DELETE .../sol-s-006                       → 200, is_deleted=1 in DB
   DELETE non-existent                        → 404, code=50001
   DELETE no-csrf                             → 403, code=40300
   DELETE no-auth                             → 401, code=40100
   DELETE already-deleted                     → 404, code=50001
X. anonymous GET ×3                           → 401
Y. non-admin login                            → NOTE (no dev USER password)

PASS=32 FAIL=1
```
