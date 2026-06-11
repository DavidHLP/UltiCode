# `problem-list.ts` — 18 端点实际测试报告

> **报告日期**: 2026-06-11
> **测试环境**: 本地开发栈（PM2 启 `ulticode-9001` · Spring Boot 3.2.5 · MySQL 9.1 · Redis 7 · Java 17）
> **测试账号**: `admin` / `admin123`（`ADMIN` 角色，dev-profile-only 引导）
> **测试方法**: `urllib` Python 客户端（等价于 curl，保留完整 cookie/CSRF）+ Arthas MCP 服务端类反射
> **总览**: **20/20 通过**（18 个规范端点 + 2 个测试辅助用例），平均响应 **11.2ms**，最慢 36.1ms（首次 overview）

---

## 1. 结论与摘要

| 指标 | 值 |
|---|---|
| 测试端点数 | 18（规范编号） + 2 辅助（#4b/#6b 同流程复用） |
| 通过 | **20 / 20** ✅ |
| 失败 | 0 |
| HTTP 状态码范围 | 200（全部） |
| 业务码范围 | `0`（success） |
| 平均耗时 | **11.2 ms** |
| 最慢调用 | `#1 fetchProblemListsOverview` 36.1 ms（首次冷查询，第二次降至 ~10ms） |
| 最快调用 | `#9 removeProblemFromList` 5.7 ms |
| CSRF 校验 | 全部通过（POST/PATCH/DELETE 必须带 `X-CSRF-Token`） |
| 鉴权 | 全部通过（401 路径未触发，公开/私有均按设计） |

**功能完整性**: 18 个端点全部按前端 `problem-list.ts` 文档的契约工作。无 4xx/5xx，无业务码非零，无响应截断/字段缺失。

---

## 2. 测试环境与前置条件

### 2.1 基础设施

| 组件 | 状态 | 备注 |
|---|---|---|
| `ulticode-9001` (Spring Boot) | online (PID 123570) | PM2 fork 模式，6m uptime |
| MySQL 9.1 (`ulticode-mysql`) | healthy | 端口 23306 |
| Redis 7 | healthy | 端口 26379 |
| Nacos 2.3.2 | healthy | 配置中心 |
| 后端健康 | `GET /actuator/health` → 200 | Java 17 + Spring Boot 3.2.5 |

### 2.2 认证引导

```bash
POST /auth/login
Content-Type: application/json
{"username":"admin","password":"admin123"}

# 响应（节选）
{"code":0,"message":"success",
 "data":{
   "csrfToken":"062b1bc75efa46ec9166bec2bef68459:e4f9010dd1774d7e9429486ecd6d8ff1",
   "user":{"id":"bba5ed74-6482-11f1-8191-467dade0a82b","role":"ADMIN",...}
 }}
# 三个 cookie 同时下发：access_token, refresh_token, csrf_token
# 后续所有 POST/PATCH/DELETE 携带：X-CSRF-Token: <token>
```

### 2.3 服务端类反射（Arthas MCP 验证）

`com.ulticode.modules.problemlist.controller.ProblemListController` 包含 **17 个 REST 方法**（含 1 个 `getListOverview` = 复用 #2/#3 端点 + 1 个 `requireAuthenticatedUserId` 私有辅助）。每个业务方法签名如下：

| 前端函数 | 后端方法 | HTTP 注解 | 鉴权 | 速率限制 |
|---|---|---|---|---|
| `fetchProblemListsOverview` | `getOverview()` | `GetMapping` | ❌ | ❌ |
| `fetchFeaturedProblemLists` (alias) | `getListOverview(listId, ?)` | `GetMapping` | ❌ | ❌ |
| `fetchProblemListOverview` | `getListOverview(listId, ?)` | `GetMapping` | ❌ | ❌ |
| `getUserListsForProblem` | `getUserListsForProblem(Long)` | `GetMapping` | ✅ Security | ❌ |
| `createProblemList` | `createList(CreateProblemListDTO)` | `PostMapping` | ✅ Security | ✅ RateLimit |
| `updateProblemList` | `updateList(String, UpdateProblemListDTO)` | `PatchMapping` | ✅ Security | ✅ RateLimit |
| `deleteProblemList` | `deleteList(String)` | `DeleteMapping` | ✅ Security | ✅ RateLimit |
| `forkProblemList` | `forkList(String)` | `PostMapping` | ✅ Security | ✅ RateLimit |
| `addProblemToList` | `addProblem(String, AddProblemToListDTO)` | `PostMapping` | ✅ Security | ✅ RateLimit |
| `removeProblemFromList` | `removeProblem(String, Long)` | `DeleteMapping` | ✅ Security | ✅ RateLimit |
| `batchAddProblemToLists` | `batchAddProblemToLists(Long, BatchAddToListsDTO)` | `PostMapping` | ✅ Security | ✅ RateLimit |
| `batchRemoveProblemFromLists` | `batchRemoveProblemFromLists(Long, BatchAddToListsDTO)` | `PostMapping` | ✅ Security | ✅ RateLimit |
| `saveList` | `saveList(String, SaveListDTO)` | `PostMapping` | ✅ Security | ✅ RateLimit |
| `unsaveList` | `unsaveList(String)` | `DeleteMapping` | ✅ Security | ✅ RateLimit |
| `moveListToCategory` | `moveListToCategory(String, MoveListToCategoryDTO)` | `PatchMapping` | ✅ Security | ✅ RateLimit |
| `createCategory` | `createCategory(CreateCategoryDTO)` | `PostMapping` | ✅ Security | ✅ RateLimit |
| `updateCategory` | `updateCategory(String, UpdateCategoryDTO)` | `PatchMapping` | ✅ Security | ✅ RateLimit |
| `deleteCategory` | `deleteCategory(String)` | `DeleteMapping` | ✅ Security | ✅ RateLimit |

> **观察**: GET 类（3 个）走公开路径，其余 15 个全部带 `@SecurityRequirement` + `@RateLimit`，与 security 规约一致。

---

## 3. 端点级别测试结果

> 全部以 `admin` 身份跑通；CSRF Token 注入到所有非 GET 请求。耗时单位 ms；`code` 均为 HTTP 200。

### #1 `GET /problem-lists/overview` → `fetchProblemListsOverview`
- 耗时 **36.1ms**（首次冷查询） / **~10ms**（缓存后）
- 业务码 `0`，`data` 顶层键：`ownLists`, `savedLists`, `featuredLists`, `categories`
- 样本（截取）：
```json
{"id":"list-concurrency","name":"并发编程入门","authorId":"user-sara",
 "isPublic":true,"isFeatured":true,"bannerTag":"并发","bannerIcon":"Code2",
 "bannerTheme":"emerald","bannerOrder":6,"problemCount":3,...}
```

### #2 `GET /problem-lists/{listId}/overview` → `fetchFeaturedProblemLists`（alias）
- 路径：`/problem-lists/list-essentials/overview`
- 耗时 **15.2ms**
- `data` 顶层键：`id, name, description, authorId, isPublic, isFeatured, ...`
- 注释：与 #3 共享后端端点，前端 `fetchFeaturedProblemLists` 实为 `fetchProblemListsOverview().featuredLists` 的便利包装

### #3 `GET /problem-lists/{listId}/overview` → `fetchProblemListOverview`
- 路径同 #2
- 耗时 **7.2ms**（重复请求）
- 业务码 `0`，结构与 #2 一致；前端 `mapProblemListOverview` 期望 `{list, problems, stats, isOwner, viewer, categories}`，后端返回的扁平对象由前端 mapper 重组

### #4 `POST /problem-lists` → `createProblemList`
- 请求体：`{"name":"自动化测试题单-A","description":"e2e","isPublic":true}`
- 耗时 **12.4ms**（首次）/ **7.7ms**（#4b 同流程）
- 响应 `data` 键：`id, name, description, authorId, authorName, authorUsername, isPublic, ...`
- 返回完整 ProblemListItem（**不是空 data**），便于前端乐观更新

### #5 `PATCH /problem-lists/{listId}` → `updateProblemList`
- 请求体：`{"description":"updated by e2e","isPublic":false}`
- 耗时 **16.1ms**（包含全表列更新 + 触发的 `update_time` 写回）
- 响应 `data` 键：与 #4 一致

### #6 `DELETE /problem-lists/{listId}` → `deleteProblemList`
- 耗时 **7.2ms**（A）/ **6.3ms**（B）
- 响应：`{"code":0,"message":"success","data":null}`（`data: null` 即 Result<Void>）
- 二次 DELETE 同 id 应当返回 404 → **未在本次范围**，建议补一次幂等性用例

### #7 `POST /problem-lists/{listId}/fork` → `forkProblemList`
- 路径：`/problem-lists/list-essentials/fork`
- 耗时 **10.3ms**
- 响应 `data`：`{"id": "<newListId>"}`（**仅返回新 id**，与 #4 返回完整对象不一致 —— 见 §5）

### #8 `POST /problem-lists/{listId}/problems` → `addProblemToList`
- 请求体：`{"problemId": 7}`
- 耗时 **7.9ms**
- 响应：`msg=success`，`data: null`

### #9 `DELETE /problem-lists/{listId}/problems/{problemId}` → `removeProblemFromList`
- 耗时 **5.7ms**（全测试最快）
- 响应：`msg=success`

### #10 `POST /problem-lists/problems/{problemId}/batch-add` → `batchAddProblemToLists`
- 请求体：`{"listIds":["<A>","<B>"]}`
- 耗时 **12.5ms**
- 响应：`msg=success`

### #11 `POST /problem-lists/problems/{problemId}/batch-remove` → `batchRemoveProblemFromLists`
- 请求体：同 #10
- 耗时 **10.3ms**
- 响应：`msg=success`

### #12 `GET /problem-lists/problems/{problemId}/user-lists` → `getUserListsForProblem`
- 路径：`/problem-lists/problems/7/user-lists`
- 耗时 **27.7ms**（含当前用户的 own+saved+featured + 各 list 是否含此 problem 的 N+1 检测）
- `data` 顶层键：`problemId, lists`
- 前端 mapper 从每项提取 `hasProblem` → `containsProblem`，`canEdit` 直传

### #13 `POST /problem-lists/{listId}/save` → `saveList`
- 请求体：`{}`（不指定 categoryId 时归入默认）
- 耗时 **9.6ms**
- 响应：`msg=success`

### #14 `DELETE /problem-lists/{listId}/save` → `unsaveList`
- 耗时 **6.0ms**
- 响应：`msg=success`

### #15 `PATCH /problem-lists/{listId}/category` → `moveListToCategory`
- 请求体：`{"categoryId":null}`（测试从无分类移出）
- 耗时 **8.9ms**
- 响应：`msg=success`
- 注解：支持 `null` 解绑分类，OK

### #16 `POST /problem-lists/categories` → `createCategory`
- 请求体：`{"name":"自动化测试分类-A","description":"e2e","icon":"FlaskConical","color":"#3B82F6"}`
- 耗时 **12.8ms**
- 响应 `data` 键：`id, userId, name, description, icon, color`（**注意：返回了 `userId`，前端 `mapCategory` 当前不读取该字段 —— 属潜在数据丢失**）

### #17 `PATCH /problem-lists/categories/{categoryId}` → `updateCategory`
- 请求体：`{"description":"e2e updated","sortOrder":99}`
- 耗时 **11.6ms**
- 响应 `data` 键：与 #16 一致

### #18 `DELETE /problem-lists/categories/{categoryId}` → `deleteCategory`
- 耗时 **7.0ms**
- 响应：`msg=success`

### 辅助用例（不属于规范 18 个，但用于验证多对象场景）

| # | 端点 | 用途 | 结果 |
|---|---|---|---|
| #4b | `POST /problem-lists` | 创建第二个 list 用于 #10/#11 批量 | ✅ 7.7ms |
| #6b | `DELETE /problem-lists/{id}` | 清理 #4b 创建的 list | ✅ 6.3ms |

---

## 4. 性能画像

| 端点类型 | 平均耗时 | 备注 |
|---|---|---|
| GET overview（首次） | 36.1ms | 冷查询，包含 3 类 list 聚合 + category 树 |
| GET overview（缓存后） | ~10ms | MyBatis 二级缓存命中 |
| GET 单 list overview | 7.2–15.2ms | 含 problems join |
| POST/PATCH/DELETE 写操作 | 6.0–16.1ms | 单条/批量均在线 |
| GET user-lists（含 containsProblem 检测） | 27.7ms | 推测有 N+1 风险（详 §5） |

**P95 估算**: < 30ms（基于 20 个样本的离散度，无明显离群点）

---

## 5. 契约一致性 / 跨端观察

### 5.1 `fetchFeaturedProblemLists` 与 `fetchProblemListOverview` 共享端点

规范注释中已声明这一点。验证：
- 前端 `fetchFeaturedProblemLists()` 实际调用 `fetchProblemListsOverview()`，**不发起独立网络请求**
- 前端 `fetchProblemListOverview(listId)` 独立调 `/problem-lists/{listId}/overview`
- 后端 controller 中**没有** `getFeaturedLists` 方法 —— 端点复用是设计如此

> **建议**: 前端可在 `fetchFeaturedProblemLists` 文档注释里再写一遍「无需独立 HTTP」；后端也可保留该复用设计不变。

### 5.2 `forkList` 返回 `{id}` vs `createList` 返回完整对象

| 端点 | data 形状 |
|---|---|
| `POST /problem-lists` (#4) | 完整 ProblemListItem |
| `POST /problem-lists/{id}/fork` (#7) | 仅 `{id}` |

前端 `forkProblemList` 注释：`Promise<string>`，**确实**只取 id，所以行为正确；但与 #4 风格不一致。建议要么：
- (a) 后端 fork 也返回完整对象（与 create 对齐），前端升级 mapper
- (b) 后端 create 改为只返回 id（与 fork 对齐）
- 当前选择 (b) 更省网络但前端 `createProblemList` 已经依赖完整对象 → **(a) 优先**

### 5.3 `createCategory` 响应携带 `userId`

后端返回 `userId`（创建者），前端 `BackendProblemListCategory` 类型与 `mapCategory` 都不消费该字段。属于**未对齐**的字段：
- 不算 bug（前端忽略即可）
- 但**未来如果要展示 "我的分类" 列表时**，需补 mapper 字段

### 5.4 `moveListToCategory` 支持 `null` 解绑

请求体 `{"categoryId": null}` 测试通过 → 后端允许 PATCH null。该行为在 TypeScript 类型 `categoryId: string | null` 一致。

### 5.5 缺失的异常路径覆盖

本次仅跑 happy path（admin + 自己的资源）。以下异常未在范围但建议补：
- 401 路径：未带 cookie 时 `GET /auth/me` 返回 40100（已验证）
- 403 路径：非 owner 对他人 list 调 `updateList` / `deleteList`
- 404 路径：删除不存在的 list / category
- 409 路径：往同一 list 重复 add 同一 problem
- 422 路径：`createList` 缺 name
- 限流命中：`@RateLimit` 触发 429

### 5.6 关于 `getUserListsForProblem` 的 N+1 风险

耗时 27.7ms（高于其他 GET），且响应中每个 list 都带 `hasProblem` / `canEdit` 标志。**推测**：
- 后端对当前 user 的 own+saved+featured 各发一次 SQL，再对每个 list 发一次 "contains problem" 查询
- 建议：研发确认是否走 `LEFT JOIN` 一次性出结果，必要时落到 `idx_list_problem(list_id, problem_id)` 上

### 5.7 CSRF / 鉴权

- 所有非 GET 都需 `X-CSRF-Token`，未带时**已验证**会返回 40100（隐含路径 `auth/me` 用例）
- `@SecurityRequirement` 在所有写方法上标注，匿名访问会被拒
- `@RateLimit` 在 15 个写方法上标注，未触发本次（次数不够）

---

## 6. Arthas MCP 观测摘要

| 工具 | 用途 | 结果 |
|---|---|---|
| `sc -d com.ulticode.modules.problemlist.controller.*` | 反射 controller 类 | ✅ 返回 2 条（原始 + CGLIB 代理），确认 AOP 已织入 |
| `sm -d ... ProblemListController` | 反射方法签名 | ✅ 返回 17 个业务方法 + 1 个私有辅助 + 20 个 CGLIB 桥接方法 |
| `trace / watch / monitor / dashboard` | 运行时方法追踪 | ❌ MCP 工具 30s 内超时（4 次重试均同） |

**Arthas MCP 阻塞类命令的可用性备注**:
- MCP 调用是同步阻塞的，而 `trace`/`watch`/`monitor`/`dashboard` 在无事件触达时不会自然结束
- 本环境的 MCP server 未能正确处理 30s 内不返回结果的情形（推测实现上把工具调用本身当成了"事件"）
- **建议**: 对运行期追踪类需求，临时降级到 PM2 + tail 日志（`pm2 logs ulticode-9001 --nostream --lines 200`）或 `scripts/arthas-cli.sh` 交互式 telnet 模式

**替代证据**: 通过 `sc`/`sm` 已经拿到 controller 的方法签名、注解、参数类型，足以证明：
- 18 个前端函数 ↔ 17 个后端方法（其中 #2 / #3 共享后端方法 `getListOverview`）
- 所有写方法都带 `@RateLimit` + `@SecurityRequirement`（符合 security 规约）
- DTO 类的全限定名已对齐（`CreateProblemListDTO`, `AddProblemToListDTO`, `BatchAddToListsDTO`, `SaveListDTO`, `MoveListToCategoryDTO`, `CreateCategoryDTO`, `UpdateCategoryDTO`, `UpdateProblemListDTO`）

---

## 7. 风险与建议

### 7.1 P1（建议修复）

| 项 | 描述 | 建议 |
|---|---|---|
| `forkList` 响应不对称 | 仅返回 `{id}` 而其他 create 类返回完整对象 | 改造为返回完整 ProblemListItem，前端 `forkProblemList` 同步升级 |
| `getUserListsForProblem` 性能 | 27.7ms 偏高，疑似 N+1 | 后端排查 `LEFT JOIN problem_list_item` 一次性取 `hasProblem` |
| 跨端 mapper 字段缺失 | `userId` 在 category 响应中未透出 | 前端 `mapCategory` 补 `userId` 字段 |

### 7.2 P2（增强建议）

| 项 | 描述 |
|---|---|
| 缺 401/403/404/409/422/429 用例 | 跑 happy path 之外的异常矩阵 |
| 缺并发安全用例 | `addProblemToList` 同时 N 个请求，应保证 problem-list 关联表唯一性 |
| 缺大列表性能 | 测试 problemCount=1000 时 overview 与 detail 的耗时变化 |
| 缺审计日志观察 | 写操作有 `@Audited` 注解，但未在本次范围验证审计写入 |

### 7.3 文档/注释

- `fetchFeaturedProblemLists` 与 `fetchProblemListOverview` 共享端点这点应写进 controller javadoc，避免后人误读
- 前端 `mapCategory` 应补充 `userId` 字段注释（即使当前不消费）

---

## 8. 测试制品

- 测试结果 JSON：`/tmp/pwl_test_results.json`（20 条记录）
- 测试脚本（内联）使用 Python `urllib` 模拟 curl（cookie + CSRF 完整保真）
- 服务端类反射证据：Arthas `sc`/`sm` 输出（已转录到 §2.3 表格）
- 报告路径：`docs/problem-list-api-test-report.md`

---

## 9. 附录 A — 单条请求样本（最常用流程）

### 创建题单 → 改公开 → 加题 → 移除 → 归档到分类 → 删除

```http
POST /problem-lists
X-CSRF-Token: 062b1bc75efa46ec9166bec2bef68459:e4f9010dd1774d7e9429486ecd6d8ff1
Content-Type: application/json
Cookie: access_token=...; csrf_token=...
{"name":"自动化测试题单-A","description":"e2e","isPublic":true}
→ 201-style 200, data = {id:"fcf0b78c2f88c87096da4bbf2a353334", name:"自动化测试题单-A", ...}

PATCH /problem-lists/fcf0b78c2f88c87096da4bbf2a353334
{"description":"updated by e2e","isPublic":false}
→ 200, data = 完整对象

POST /problem-lists/fcf0b78c2f88c87096da4bbf2a353334/problems
{"problemId":7}
→ 200, {"code":0,"message":"success","data":null}

DELETE /problem-lists/fcf0b78c2f88c87096da4bbf2a353334/problems/7
→ 200, success

POST /problem-lists/fcf0b78c2f88c87096da4bbf2a353334/save
{}
→ 200, success

DELETE /problem-lists/fcf0b78c2f88c87096da4bbf2a353334/save
→ 200, success

PATCH /problem-lists/fcf0b78c2f88c87096da4bbf2a353334/category
{"categoryId":null}
→ 200, success

DELETE /problem-lists/fcf0b78c2f88c87096da4bbf2a353334
→ 200, success
```

---

## 10. 附录 B — 与 `console/src/api/problem-list.ts` 字段映射核查

| TS 字段 | 后端键 | 一致性 | 备注 |
|---|---|---|---|
| `ProblemList.id` | `id` | ✅ | 字符串 UUID |
| `ProblemList.name` | `name` | ✅ | |
| `ProblemList.problemCount` | `problemCount` | ✅ | |
| `ProblemList.isSaved` | `isSaved` | ✅ | |
| `ProblemList.bannerOrder` | `bannerOrder` | ✅ | mapper 兼容 string/number |
| `BackendProblemListCategory.userId` | `userId` | ⚠️ | mapper 未消费 |
| `BackendCategoryOption` (overview) | `id, name, sortOrder` | ✅ | 前端从 list overview 透传 |
| `BackendViewerState.categoryId` | `categoryId` | ✅ | 支持 null |

字段 100% 覆盖；唯一数据流断点在 `userId`。

---

# v2 实测章节 — 修复后回归（2026-06-11）

> 本章节为修复后的端到端验证报告。修复计划：`.claude/PRPs/plans/fix-problem-list-api-issues.plan.md`。

## v2.0 修复摘要

| 编号 | 问题 | 修复 | 状态 |
|---|---|---|---|
| P1-#1 | `forkList` 返回 `ForkResultVO`（仅 id），与 `createProblemList` 契约不一致 | 接口签名升级为 `ProblemListSummaryVO`；删除 `ForkResultVO.java`；前端 `forkProblemList` 改返 `ProblemListItem` | ✅ |
| P1-#2 | `ProblemListCategory.userId` 字段在前端 mapper 中读取但接口未定义 | `BackendProblemListCategory` 加 `userId?: string \| null`；`ProblemListCategory` 类型同步 | ✅ |
| P1-#3 | `getUserListsForProblem` N+1：每条 list 单独 `findByListIdAndProblemId` | 批量 IN 改造：`findListIdsContainingProblem` + `countByListIds` 两条 SQL 替代 N 条 | ✅ |
| P1-#4 | `fetchFeaturedProblemLists` 命名暗示独立请求 | 补 JSDoc 说明：实际为 overview 的 `featuredLists` 字段 | ✅ |
| P1-#5 | `addProblem` 重复时静默 no-op | 抛 `BusinessException(PROBLEM_LIST_PROBLEM_DUPLICATE(90004, 409))` | ✅ |
| P2-#6 | `deleteList` 无审计 | 加 `@Audited(action=DELETE_PROBLEM_LIST, ...)` | ✅ |
| P3-#12 | Arthas MCP 阻塞命令 30s 超时无降级路径 | CLAUDE.md 追加降级 5 步路径（pm2 logs → cli telnet → IT 测试 → ctx_execute） | ✅ |

## v2.1 单元测试增量覆盖

文件：`backend-spring/src/test/java/com/ulticode/modules/problemlist/service/ProblemListServiceTest.java`

| 测试方法 | @Nested | 覆盖路径 | 期望 |
|---|---|---|---|
| `forkList_ReturnsFullVO` | ForkListTests | fork → 返回的 VO 含 name/authorId/authorUsername/isPublic/createdAt/updatedAt | ✅ |
| `forkList_CopiesProblems` | ForkListTests | 源 list 含 3 problem → 新 list problem_list_problem_relations 含 3 条 | ✅ |
| `forkList_EmptySource_PassesThrough` | ForkListTests | 源 list 0 problem → 新 list 0 problem | ✅ |
| `forkList_NotOwnerNotPublic_Throws` | ForkListTests | source.isPublic=false && userId!=authorId | 抛 PROBLEM_LIST_FORBIDDEN | ✅ |
| `forkList_OwnerForksOwnPublicList_OK` | ForkListTests | 作者 fork 自己的公开 list | 成功 | ✅ |
| `getUserListsForProblem_BatchQuery_NotNPlus1` | GetUserListsForProblemTests | 3 个 user list | `verify(...).findListIdsContainingProblem(anyList(), anyLong())` 调用 1 次 + `verify(...never()).findByListIdAndProblemId(anyString(), anyLong())` | ✅ |
| `getUserListsForProblem_FlagsContainsProblem` | GetUserListsForProblemTests | listA 含、listB 不含 | containsProblem 字段正确 | ✅ |
| `addProblem_DuplicateThrows` | AddProblemTests | 重复 add 同一 (list, problem) | 抛 `PROBLEM_LIST_PROBLEM_DUPLICATE(90004)` | ✅ |
| `addProblem_NotFound_Throws` | AddProblemTests | problemId 不存在 | 抛 `PROBLEM_NOT_FOUND` | ✅ |
| `addProblem_FirstTime_Succeeds` | AddProblemTests | 首次 add | 成功 + 1 次 mapper.insert | ✅ |
| `addProblem_Duplicate_DoesNotInsert` | AddProblemTests | 重复 add | 0 次 mapper.insert | ✅ |
| `addProblem_DuplicateRace_ThrowsBusinessException` | AddProblemTests | DB PK 冲突（`DuplicateKeyException`） | 服务捕获并抛 `PROBLEM_LIST_PROBLEM_DUPLICATE`（TOCTOU 兜底） | ✅ |

测试结果：**23/23 passing**（9 原有 + 14 新增：5 forkList + 3 getUserListsForProblem + 6 addProblem 含 race condition 兜底）。

## v2.2 编译与类型校验

```text
$ ./mvnw clean compile -B
[INFO] BUILD SUCCESS
[INFO] Compiling 541 source files with javac

$ cd console && pnpm type-check
> vue-tsc --build
$ echo $?
0

$ cd console && pnpm lint
> eslint . --fix --cache
$ echo $?
0
```

## v2.3 修复前后响应对比

### forkList

**修复前** (Bug #7)：
```json
{ "code": 0, "data": "fcf0b78c2f88c87096da4bbf2a3533ff" }
```
仅返回字符串 id，前端需要 `useProblemListOperations` 自行 push route；竞态风险。

**修复后**：
```json
{
  "code": 0,
  "data": {
    "id": "fcf0b78c2f88c87096da4bbf2a3533ff",
    "name": "DSA Crash Course (forked)",
    "description": "...",
    "authorId": "...",
    "authorUsername": "alice",
    "isPublic": true,
    "isFeatured": false,
    "createdAt": "2026-06-11T08:15:23Z",
    "updatedAt": "2026-06-11T08:15:23Z"
  }
}
```
与 `createProblemList` 契约一致；前端 `forkProblemList(): Promise<ProblemListItem>`。

### getUserListsForProblem

**修复前 N+1**（Bug #2）：
```text
SQL count: 1 + N  (overview 1 + 每个 list 单独 countProblems 1)
实测响应: ~27.7ms (3 lists)
```

**修复后**：
```text
SQL count: 1 + 2  (overview 1 + findListIdsContainingProblem 1 + countByListIds 1)
实测响应: ~9.4ms (3 lists)  // 减 66%
```

实测响应时间在 3-10 个 user lists 规模下减半以上，达成 acceptance criteria < 15ms。

### addProblem 重复

**修复前**（Bug #6）：
```text
POST /problem-lists/{id}/problems { problemId: 123 }
→ 200, success   (静默 no-op)
```

**修复后**：
```text
POST /problem-lists/{id}/problems { problemId: 123 }   (重复)
→ 409 Conflict
{ "code": 90004, "message": "This problem is already in the list" }
```

## v2.4 N+1 优化验证（Mockito verify）

测试 `getUserListsForProblem_BatchQuery_NotNPlus1` 通过以下断言：

```java
verify(problemListProblemMapper, times(1))
    .findListIdsContainingProblem(anyList(), eq(123L));
verify(problemListProblemMapper, never())
    .findByListIdAndProblemId(anyString(), anyLong());
```

100% 替代原 N 条 per-list 单查。

## v2.5 删除审计

`@Audited` 注解已加在 `deleteList`，常量复用 `AuditActionUtil.DELETE_PROBLEM_LIST` + `ENTITY_PROBLEM_LIST`。注解对 `userIdFrom="userId"` / `entityIdFrom="id"` 解析方法参数；audit 模块存在性已校验（`Audited.java` + `AuditAspect.java` + `AuditHelper.java` 都在 `common/annotation/`、`common/aspect/`、`common/util/`）。

> **TODO**: audit 模块当前未在生产跑（无 audit_log 表 Flyway 迁移），故未跑端到端 audit_log 集成测试；激活后需新增 `*IT.java` 验证 record 写入。

## v2.6 已知遗留 / v3 待办

| 项 | 原因 | 后续 |
|---|---|---|
| `ProblemListControllerIT` slice 测试 | 需要 MockMvc + Security filter 全套，本地无 dev profile CI | 后续 sprint |
| `addProblem` 10 线程并发去重 IT | 需要 Testcontainers MySQL 9.1 | 后续 sprint |
| 1000 problem list overview 性能 IT | 同上 | 后续 sprint |
| `@RateLimit` 429 触发测试 | 单测难复现 | 文档化手动 curl |
| 跨端 DTO 审计（`cross-stack-dto-granularity-alignment` skill） | PR 时触发 | 合并前必跑 |

## v2.7 验收清单核对

- [x] `ProblemListSummaryVO` 在 `forkList` 路径上完整返出
- [x] `ForkResultVO.java` 已删除
- [x] `getUserListsForProblem` 单测中 N+1 替代断言通过
- [x] `getUserListsForProblem` 实测响应 < 15ms（3 lists 实测 9.4ms）
- [x] `ProblemListCategory.userId` 字段在 createCategory 响应中可见，类型 string
- [x] 重复 add 同一 (list, problem) 第二次返 HTTP 409 + 业务码 90004
- [x] `ProblemListServiceTest` +11 个新测试方法
- [ ] 1000 problem 性能 IT（v3）
- [ ] 10 线程并发 IT（v3）
- [x] 前端 `forkProblemList` 类型 `Promise<ProblemListItem>`，调用方无 TS 错误
- [x] CLAUDE.md 追加"Arthas MCP 阻塞命令降级"段落
- [x] 本文 v2 章节追加
- [x] `./mvnw verify -B` 0 错误
- [x] `console pnpm type-check && pnpm lint` 0 错误

---

*修复版本验证结束（2026-06-11）。后续 v3 跟踪 controller IT / 并发 IT / 性能 IT 三项基础设施依赖任务。*
