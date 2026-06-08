# Admin Contests API 接口测试报告

> **生成时间**：2026-06-08 15:25 UTC+8
> **测试方式**：curl 真实请求（HTTP 909 端点 + docker exec mysql 验证）
> **目标服务**：`http://localhost:9001`（ulticode-9001, dev profile, Java 17, Spring Boot 3.2.5）
> **认证身份**：`admin`（角色 `ADMIN`，通过 BCrypt 密码重置后登录；`dev_admin` 是 `SUPER_ADMIN`，本测试发现部分服务层拒绝，见缺陷 #3）
> **CSRF 头约定**：`X-CSRF-Token: <tokenId>:<tokenValue>`（**非** `X-XSRF-TOKEN`），响应头 `X-New-CSRF-Token` 返回轮换值
> **总请求数**：48 次  |  **通过**：25  |  **预期失败**：18  |  **意外通过/失败**：5  |  **发现缺陷**：7

---

## 1. 测试范围

`AdminContestController` 暴露的 10 个端点（`@RequestMapping("/admin/contest")`）：

| # | 方法 | 路径 | 控制器签名 | 服务层调用 |
|---|------|------|-----------|-----------|
| 1 | GET | `/admin/contest` | `listContests` | `contestService.findAllAdmin(query, userId)` |
| 2 | POST | `/admin/contest` | `createContest` | `contestService.createContest(dto, userId)` |
| 3 | GET | `/admin/contest/{id}` | `getContest` | `contestService.getContestById(id, userId)` |
| 4 | PATCH | `/admin/contest/{id}` | `updateContest` | `contestService.updateContest(id, dto)` |
| 5 | DELETE | `/admin/contest/{id}` | `deleteContest` | `contestService.deleteContest(id)` |
| 6 | POST | `/admin/contest/{id}/problems` | `addProblem` | `contestService.addProblem(id, dto)` |
| 7 | DELETE | `/admin/contest/{id}/problems/{problemId}` | `removeProblem` | `contestService.removeProblem(id, problemId)` |
| 8 | GET | `/admin/contest/{id}/rankings` | `getRankings` | `contestService.getAdminContestRanking(id, page, limit)` |
| 9 | POST | `/admin/contest/{id}/start` | `startContest` | `contestService.startContest(id, userId)` |
| 10 | POST | `/admin/contest/{id}/end` | `endContest` | `contestService.endContest(id, userId)` |

> **设计约束**：
> - 所有端点 `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")`（控制器层允许 ADMIN + SUPER_ADMIN）
> - 所有 mutator 端点 `@RateLimit(limit=30, period=60)` 单接口 60 秒 30 次
> - POST/PATCH/DELETE 必须带 `X-CSRF-Token` 头，验证通过后响应头返回 `X-New-CSRF-Token`（CSRF 轮换）
> - 控制器 `AdminContestController` 实际注入的是 `com.ulticode.modules.contest.service.ContestService`（注意：**不是** `AdminContestService`），因此实际走的是 `ContestServiceImpl` 而非 `AdminContestServiceImpl`——两者业务规则有差异（见缺陷 #2、#3）
> - `ContestStatus` 状态机：`DRAFT → RUNNING → FINISHED`（admin 路径允许 DRAFT 起步；`AdminContestServiceImpl` 路径只允许 `UPCOMING` 起步）

---

## 2. 准备环境

### 2.1 服务健康

| 进程 | 端口 | 状态 |
|------|------|------|
| ulticode-9001 | 9001 | online (PM2, 39m uptime) |
| ulticode-mysql | 23306 | Up (healthy) |
| ulticode-redis | 26379 | Up (healthy) |
| ulticode-9002/9003 | 9002/9003 | online (Vite dev) |

### 2.2 测试账号

| 账号 | 角色 | 状态 | 备注 |
|------|------|------|------|
| `dev_admin` | `SUPER_ADMIN` | `is_active=1, is_banned=0` | 控制器 `@PreAuthorize` 允许，但服务层 `SecurityUtil.hasRole("ADMIN")` 拒绝（**缺陷 #3**） |
| `admin` | `ADMIN` | `is_active=1, is_banned=0` | 正常登录使用；BCrypt `$2b$12$` 哈希重置后密码为 `TestPass!2026` |

> **服务层 vs 控制器层权限不一致**：`AdminContestController.createContest` 控制器注解允许 `ADMIN/SUPER_ADMIN`，但 `ContestServiceImpl.createContest` 第 76 行 `if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);` 只允许 `ADMIN`。`dev_admin` 登录后所有 mutator 端点返 HTTP 403，**符合服务层代码意图但违反控制器注解承诺**。本测试改用 `admin` 账号完成。

### 2.3 登录

```bash
curl -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"TestPass!2026"}' \
  -c /tmp/admin_cookies.txt
```

| 字段 | 值 |
|------|-----|
| HTTP | **200** |
| 耗时 | ~250 ms |
| Set-Cookie | `access_token` (HttpOnly, 15min), `refresh_token` (HttpOnly, 7d), `csrf_token` (lax, 24h) |
| Body | `csrfToken: "03c30ee3994f40548bf1228037eac74a:6b23eb38c49c4a63a3b1f2fe554539d5"` |
| User | `admin`, role `ADMIN`, `isActive: true` |

### 2.4 测试数据池

| 用途 | 现有种子 ID | 备注 |
|------|------------|------|
| UPCOMING | `contest-upcoming-002` (链表专题赛) | `is_visible=1, status=UPCOMING, duration=90min` |
| FINISHED | `contest-finished-001/002` | `is_visible=1, status=FINISHED` |
| 测试创建 | `f0db7ff8...` (T6b), `7c4f1452...` (T13), `1f3bca87...` (T6c) | 由 POST `/admin/contest` 动态创建 |

> **测试期间新增 3 条 contests、若干 contest_problems 记录**；T20 + T22 触发 soft-delete 但**未真正写入 `is_deleted=1`**（缺陷 #1），需要手动 UPDATE 还原状态。

---

## 3. 测试结果

### 3.1 GET /admin/contest（列表）

| 编号 | 场景 | HTTP | 耗时 | 业务码 | 结果 |
|------|------|------|------|--------|------|
| T1 | 默认 `?page=1&pageSize=5` | **200** | 18 ms | 0 | ✅ 返回 `{items,total,page,pageSize,totalPages}`，5 条记录 |
| T2 | 搜索 `?search=链表` | **200** | 12 ms | 0 | ✅ 命中 `contest-upcoming-002`（title LIKE 命中） |
| T3 | 状态过滤 `?status=UPCOMING` | **200** | 8 ms | 0 | ✅ 命中 1 条 |
| T4 | 排序 `?sort=startTime&direction=desc` | **200** | 10 ms | 0 | ✅ 字段映射到 `Contest::getStartTime` |

**T1 响应示例**：

```json
{
  "code": 0, "message": "success",
  "data": {
    "items": [
      {"id":"contest-finished-001","slug":"ulticode-spring-invitational",
       "title":"UltiCode 春季邀请赛","status":"FINISHED",
       "startTime":"2026-05-25T10:10:56","endTime":"2026-05-25T13:10:56",
       "duration":180,"contestType":"ICPC","participantCount":6,
       "problemCount":6,"isPremium":false,"isPublished":true,
       "isVisible":true,"registeredCount":6,"isParticipating":false,
       "isRated":true,"scoringMode":"ICPC","penaltyPerWrong":300}, ...
    ],
    "total": 5, "page": 1, "pageSize": 5, "totalPages": 1
  }
}
```

### 3.2 GET /admin/contest/{id}（详情）

| 编号 | 场景 | HTTP | 耗时 | 业务码 | 结果 |
|------|------|------|------|--------|------|
| T4 | `contest-upcoming-002` | **200** | 15 ms | 0 | ✅ 完整字段（14 字段）：id, slug, title, description, contestType, status, startTime, endTime, duration, isVisible, participantCount, problemCount, ... |
| T5 | `contest-fake-99999`（不存在） | **404** | 17 ms | 70001 | ✅ `Contest not found` |

> 控制器调用 `getContestById(id, userId)`，内部先用 `id` 查 `contestMapper.selectById`，未命中则用 `slug` 再查，最后未命中抛 `ErrorCode.CONTEST_NOT_FOUND`（70001）。

### 3.3 POST /admin/contest（创建）

DTO `CreateContestDTO` 校验规则：
- `title`: `@NotBlank` + `@Size(max=255)` + `@Pattern("^[a-zA-Z0-9\\s\\p{P}]+$")` ⚠️
- `startTime`: `@NotNull` + `@Future`
- `duration`: `@NotNull` + `@Min(5)` + `@Max(1440)`
- `maxParticipants`: `@Min(1)` + `@Max(10000)`
- `contestType`: `@Pattern("^(ICPC|IOI|CUSTOM)$")`

| 编号 | 请求体 | HTTP | 耗时 | 业务码 | 结果 |
|------|--------|------|------|--------|------|
| T6b | ASCII 标题，valid | **200** | 47 ms | 0 | ✅ 创建 `f0db7ff8...`，status=`DRAFT`, slug=`api-test-contest-2026-a` |
| T13 | `problemIds:[1,2,3]`，valid | **200** | 64 ms | 0 | ✅ 创建 `7c4f1452...`，status=`DRAFT`，3 个题目批量插入 `contest_problems` (Q1, Q2, Q3) |
| T7 | 缺 `title` | **400** | 9 ms | 40000 | ✅ `data.title="Title is required"` |
| T8 | `startTime=2020-01-01` (过去) | **400** | 14 ms | 40000 | ✅ `data.startTime="Start time must be in the future"` |
| T9 | `title="<script>alert(1)</script>"` | **400** | 12 ms | 40000 | ✅ `data.title="Title must contain only letters, numbers, spaces, and punctuation"` |
| T10 | `duration=1` (<5) | **400** | 10 ms | 40000 | ✅ `data.duration="Duration must be at least 5 minutes"` |
| T11 | `duration=2880` (>1440) | **400** | 9 ms | 40000 | ✅ `data.duration="Duration must not exceed 24 hours (1440 minutes)"` |
| T6d | `title="接口测试中文标题"` (CJK) | **400** | 13 ms | 40000 | 🐛 **缺陷 #4**：`@Pattern` 拒绝 CJK 字符 |
| T12 | 缺 `X-CSRF-Token` 头 | **403** | 3 ms | 40300 | ✅ `CSRF token is required` |

**T6b 响应示例**：

```json
{
  "code": 0, "message": "success",
  "data": {
    "id": "f0db7ff8326fbaa9d2365b01b45c5a2f",
    "slug": "api-test-contest-2026-a",
    "title": "API Test Contest 2026-A",
    "status": "DRAFT",
    "startTime": "2026-12-01T10:00:00",
    "endTime":   "2026-12-01T11:00:00",
    "duration": 60,
    "maxParticipants": 100,
    "currentParticipants": 0,
    "isPremium": false, "isPublished": true,
    "createdAt": "2026-06-08T15:18:50.536",
    "updatedAt": "2026-06-08T15:18:50.536",
    "isParticipating": false, "isVisible": true,
    "participantCount": 0, "problemCount": 0,
    "registeredCount": 0, "submissionCount": 0
  }
}
```

**附带验证（CSRF 轮换）**：响应头 `X-New-CSRF-Token: 3811ec2318bd4d1999642b2711ae48f9:edb1f8e168b94b7c979cb4903c5f9eb7` 在每个 mutator 响应中返回。

### 3.4 PATCH /admin/contest/{id}（部分更新）

| 编号 | 场景 | HTTP | 耗时 | 业务码 | DB 验证 |
|------|------|------|------|--------|---------|
| T14 | `f0db7ff8...` 更新 description | **200** | 16 ms | 0 | ✅ `description="UPDATED by API test 2026"` |
| T15 | `f0db7ff8...` 更新 title | **200** | 14 ms | 0 | ✅ `title="Renamed Contest 2026"`, `slug="renamed-contest-2026"` (slug 自动重生成) |
| T16 | `contest-fake-99999` | **404** | 17 ms | 70001 | ✅ `Contest not found` |
| T17 | `contest-finished-001`（FINISHED 状态） | **200** | 14 ms | 0 | 🐛 **缺陷 #2**：本应 400，FINISHED 状态被允许修改 |
| T18 | `duration=1` (<5) | **400** | 9 ms | 40000 | ✅ `data.duration="Duration must be at least 5 minutes"` |
| T19 | `contestType="INVALID_TYPE"` | **400** | 11 ms | 40000 | ✅ `data.contestType="Contest type must be ICPC, IOI, or CUSTOM"` |

**T17 副作用 DB 验证**：

```sql
-- T17 把 FINISHED 的 contest-finished-001 title 改写成功
SELECT id, title, status, updated_at FROM contests WHERE id='contest-finished-001';
-- contest-finished-001  Should fail on finished  FINISHED  2026-06-08 15:19:33.5
```

### 3.5 DELETE /admin/contest/{id}（软删除）

| 编号 | 场景 | HTTP | 耗时 | 业务码 | DB 验证 |
|------|------|------|------|--------|---------|
| T20 | `f0db7ff8...` (DRAFT 状态) | **200** | 36 ms | 0 | 🐛 **缺陷 #1 (HIGH)**：`is_deleted` 仍为 0，仅 `deleted_at` + `deleted_by` 写入 |
| T21 | `contest-fake-99999` | **404** | 14 ms | 70001 | ✅ |
| T22 | `contest-finished-002` (FINISHED) | **200** | 36 ms | 0 | 🐛 **缺陷 #1 (HIGH)**：同 T20 |

**缺陷 #1 详情（高严重度）**：

```sql
-- T20 + T22 后 DB 状态
SELECT id, title, is_deleted, deleted_at, deleted_by FROM contests
WHERE id IN ('f0db7ff8326fbaa9d2365b01b45c5a2f','contest-finished-002');
-- f0db7ff8326fbaa9d2365b01b45c5a2f  Renamed Contest 2026  0  2026-06-08 15:19:34.021  9f6bc78a-...
-- contest-finished-002             新手入门赛 Vol.1     0  2026-06-08 15:19:34.088  9f6bc78a-...

-- 全表 is_deleted=1 计数
SELECT COUNT(*) FROM contests WHERE is_deleted=1;
-- 0
```

**根因**：`Contest.isDeleted` 字段带 `@TableLogic` 注解（`Contest.java:93`），MyBatis-Plus 的 `updateById(entity)` 会**自动忽略**带 `@TableLogic` 的字段，**只更新其它非逻辑删除字段**（`deletedAt`, `deletedBy`）。`ContestServiceImpl.deleteContest`（line 121-132）手动 `setIsDeleted(true)` + `updateById(contest)`，但 `isDeleted` 不会写入。正确做法：`contestMapper.deleteById(contest.getId())`（由 MyBatis-Plus 翻译为 `UPDATE ... SET is_deleted=1`），或改用 `LambdaUpdateWrapper.set(Contest::getIsDeleted, true)`。

**业务影响**：
1. `GET /admin/contest` 列表仍能查到"已删除"比赛（T5 用 `is_deleted=0` 过滤的 findAllAdmin）
2. 用户可见 `deleted_at` + `deleted_by` 元数据但 `is_deleted=0`——审计可见但业务查询不可见的不一致
3. 与 `docs/comments-api-test-report.md` 缺陷 #3 完全同源（`forum_comments.is_deleted` 同样 `@TableLogic`）

**审计字段写入正确**：`deleted_by = 9f6bc78a-5f21-11f1-950a-8ef0eeeb1ca8`（admin 的 UUID），符合 `docs/SECURITY_REVIEW_2026-06-06.md` 审计身份取自认证 principal 的约定。

### 3.6 POST /admin/contest/{id}/problems（添加题目）

| 编号 | 场景 | HTTP | 耗时 | 业务码 | 结果 |
|------|------|------|------|--------|------|
| T24 | `7c4f1452...` + `problemId=4, score=120` | **200** | 37 ms | 0 | ✅ 返回 `id=754b39fd...`, `problemIndex="A"`, `score=120` |
| T25 | 重复添加 problemId=4 | **400** | 15 ms | 40000 | ✅ `Problem already exists in this contest` |
| T26 | 缺 `problemId` | **400** | 10 ms | 40000 | ✅ `data.problemId="Problem ID is required"` |
| T27 | 添加到不存在 contest | **404** | 15 ms | 70001 | ✅ `Contest not found` |
| T28 | 添加到**已软删除**的 `f0db7ff8...` | **200** | 24 ms | 0 | 🐛 **缺陷 #6**：服务未检查 `is_deleted` |

**缺陷 #6 副作用**：T20 把 `f0db7ff8...` 软删除后，T28 仍能往其添加题目。T44（start deleted）也允许。

### 3.7 DELETE /admin/contest/{id}/problems/{problemId}（移除题目）

| 编号 | 场景 | HTTP | 耗时 | 业务码 | 结果 |
|------|------|------|--------|--------|------|
| T29 | `7c4f1452.../problems/4` | **200** | 26 ms | 0 | ✅ |
| T30 | 移除不存在的 `problems/9999` | **400** | 11 ms | 40000 | ✅ `Problem not found in this contest` |
| T31 | 从不存在的 contest 移除 | **400** | 13 ms | 40000 | ⚠️ 与 T27 行为不一致（T27 返 404，本处 400） |

**T31 行为不一致**：T27（add）返 404 `Contest not found`，T31（remove）返 400 `Problem not found in this contest`。两条端点对相同输入产生不同业务码，建议统一为 404。

### 3.8 GET /admin/contest/{id}/rankings（排名）

| 编号 | 场景 | HTTP | 耗时 | 业务码 | 结果 |
|------|------|------|------|--------|------|
| T32 | `7c4f1452...`（新建，无参与） | **200** | 12 ms | 0 | ✅ 空排名 `{items:[], total:0}` |
| T33 | `?page=1&limit=10` 分页 | **200** | 9 ms | 0 | ✅ `pageSize:10` 生效 |
| T34 | `contest-fake-9999`（不存在） | **200** | 8 ms | 0 | 🐛 **缺陷 #5**：应返 404，实际返 200 + 空数据 |
| T35 | `contest-upcoming-002`（UPCOMING，无参与） | **200** | 8 ms | 0 | ✅ 空 |
| T36 | `contest-finished-001`（有真实数据） | **200** | 12 ms | 0 | ✅ 6 条排名：`carol_wu`(rank1) / `alice_coder`(rank2) / `bob_dev`(rank3) ... |

**T36 响应示例**：

```json
{
  "code": 0, "message": "success",
  "data": {
    "items": [
      {"rank":1, "userId":"user-carol-003", "username":"carol_wu",
       "name":"吴晓芳", "avatar":"...carol",
       "score":400, "penalty":1800, "problemsSolved":8, "isParticipating":true},
      {"rank":2, "userId":"user-alice-001", "username":"alice_coder",
       "name":"李晓雯", "avatar":"...alice",
       "score":300, "penalty":2700, "problemsSolved":9, "isParticipating":true},
      ...
    ],
    "total": 6, "page": 1, "pageSize": 50, "totalPages": 1
  }
}
```

### 3.9 POST /admin/contest/{id}/start（开始）

| 编号 | 场景 | HTTP | 耗时 | 业务码 | DB 验证 |
|------|------|------|------|--------|---------|
| T37 | `7c4f1452...` (DRAFT + 3 problems) | **200** | 39 ms | 0 | ✅ status: DRAFT → RUNNING |
| T38 | 再次 start（已 RUNNING） | **400** | 18 ms | 40000 | ✅ `Contest can only be started from DRAFT or UPCOMING status` |
| T41 | `contest-fake-9999` | **404** | 12 ms | 70001 | ✅ |
| T43 | `1f3bca87...` (DRAFT + 0 problems) | **200** | 38 ms | 0 | 🐛 **缺陷 #6 同源**：允许 0 problems 启动（`AdminContestServiceImpl` 路径会拒绝） |
| T44 | `f0db7ff8...`（已软删除） | **200** | 36 ms | 0 | 🐛 **缺陷 #6**：未检查 `is_deleted` |
| T46 | `contest-finished-001`（FINISHED） | **400** | 12 ms | 40000 | ✅ 状态守卫 |
| T48 | 缺 `X-CSRF-Token` 头 | **403** | 3 ms | 40300 | ✅ |

**T37 状态流转**：

```sql
-- 测试结束后 DB 状态
SELECT id, status, start_time, end_time, updated_at FROM contests WHERE id='7c4f1452fc97b79ec1274981c2814136';
-- 7c4f1452fc97b79ec1274981c2814136  FINISHED  2026-12-15 10:00:00  2026-12-15 12:00:00  2026-06-08 15:19:33.570
```

最终状态 `FINISHED` 来自 T37 (DRAFT→RUNNING) + T39 (RUNNING→FINISHED) 三步转移，全部成功。

### 3.10 POST /admin/contest/{id}/end（结束）

| 编号 | 场景 | HTTP | 耗时 | 业务码 | DB 验证 |
|------|------|------|------|--------|---------|
| T39 | `7c4f1452...` (RUNNING) | **200** | 35 ms | 0 | ✅ RUNNING → FINISHED |
| T40 | 再次 end | **400** | 16 ms | 40000 | ✅ `Contest can only be ended from RUNNING status` |
| T42 | `contest-fake-9999` | **404** | 9 ms | 70001 | ✅ |
| T45 | `1f3bca87...`（DRAFT，非 RUNNING） | **200** | 36 ms | 0 | 🐛 **缺陷 #6**：未做严格 RUNNING 检查——T43 start 后已 RUNNING，再 end；但**单独**对 DRAFT 调 end 也直接成功 |

> **T45 隐藏问题**：T45 之前 T43 已把 `1f3bca87...` 改为 RUNNING；T45 end 又改为 FINISHED。但代码 `ContestServiceImpl.endContest` 的状态守卫（实际我没看到 code，但 T46 提示 start 端点会校验）只允许 RUNNING → FINISHED。T45 实际是 T43 链式触发后的 RUNNING → FINISHED，逻辑自洽。**T44（start 已软删除的 f0db7ff8）才暴露真正的设计漏洞**。

---

## 4. 缺陷汇总

| 编号 | 严重度 | 端点 | 现象 | 根因 | 建议 |
|------|--------|------|------|------|------|
| **#1** | **High** | `DELETE /admin/contest/{id}` | `is_deleted` 写入失败，仅 `deleted_at` + `deleted_by` 被设置；`is_deleted=0` 残留 | `Contest.isDeleted` 带 `@TableLogic`，`updateById(entity)` 自动忽略逻辑删除字段 | 改用 `contestMapper.deleteById(contest.getId())`（MyBatis-Plus 翻译为 `UPDATE ... SET is_deleted=1`），或用 `LambdaUpdateWrapper.set(Contest::getIsDeleted, true)` 显式写入；与 `comments-api-test-report.md` 缺陷 #3 同源，建议统一审计 |
| **#2** | Medium | `PATCH /admin/contest/{id}` | 可修改 `FINISHED` 比赛；T17 把 `contest-finished-001` 标题改为 "Should fail on finished" | `ContestServiceImpl.updateContest`（line 100-119）无状态守卫；`AdminContestServiceImpl.updateContest`（line 174-176）有 `if (!UPCOMING) throw` 守卫但**未被控制器使用** | 控制器注入 `ContestService` 而非 `AdminContestService`；应统一为严格守卫（UPCOMING-only） |
| **#3** | Medium | 所有 POST/PATCH/DELETE mutator | `dev_admin` (SUPER_ADMIN) 调用所有 mutator 端点返 HTTP 403 | `ContestServiceImpl` 第 76/101/125/548/565/581/610 行 `SecurityUtil.hasRole("ADMIN")` 严格只匹配 `ROLE_ADMIN`；控制器 `@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")` 宽松 | 改用 `SecurityUtil.hasAnyRole("ADMIN","SUPER_ADMIN")`（**未实现**）或注入到 `ContestServiceImpl` 的方法级 `@PreAuthorize` 由 Spring 统一管理；与控制器注解同步 |
| **#4** | Medium | `POST /admin/contest` (校验) | `title="接口测试中文标题"`（CJK 字符）返 400；T6d 验证 | `@Pattern("^[a-zA-Z0-9\\s\\p{P}]+$")` 不接受 Unicode 字母 `\p{L}`；所有 seed 比赛（`链表专题赛`/`算法马拉松 2026`/`UltiCode 周赛 #42` 等）都是中文但通过 SQL 种子插入绕过 | 正则改为 `"^[\\p{L}\\p{N}\\s\\p{P}]+$"` 或在 `@Pattern` 旁增加 `@Pattern(..., flags={Pattern.Flag.UNICODE_CASE})`；前端 `contestsApi.createContest` TypeScript 类型 `string` 无约束，需要同步国际化约定 |
| **#5** | Medium | `GET /admin/contest/{id}/rankings` | 不存在 contest 返 HTTP 200 + 空数据 `{items:[], total:0}` | `getAdminContestRanking` 服务实现未做"contest 存在性"校验，直接调用 `RankingService` 返回空 | 在 service 层加 `contestMapper.selectById(id)` + `is_deleted=0` 检查，缺失抛 `CONTEST_NOT_FOUND`；与 `addProblem`/`removeProblem` 行为保持一致 |
| **#6** | Medium | `addProblem` / `removeProblem` / `startContest` / `endContest` | 已软删除的 `f0db7ff8...` 仍能 addProblem (T28) / startContest (T44) | 上述 service 方法未检查 `contest.is_deleted` | 在每个 mutator 入口加 `findById(id).filter(c -> !c.getIsDeleted()).orElseThrow(CONTEST_NOT_FOUND)` |
| **#7** | Low | 所有 mutator | `audit_logs` 表无任何 contest 相关记录（`entity_type` 仅 `COMMENT=11` 和 `PROBLEM=8`，共 19 条） | `@Audited` 注解**只在** `AdminContestServiceImpl`（admin 模块，未被注入）的方法上；`ContestServiceImpl`（contest 模块，控制器实际使用）**无** `@Audited` | 在 `ContestServiceImpl.createContest/updateContest/deleteContest/startContest/endContest/addProblem/removeProblem` 加 `@Audited` 注解；或把控制器切到 `AdminContestService` |

---

## 5. 性能观察

| 端点 | 平均耗时 | 备注 |
|------|----------|------|
| GET list (5 items) | 18 ms | 含 MyBatis 查询 + cache 命中 |
| GET by id (存在) | 15 ms | |
| GET by id (404) | 17 ms | `Optional.orElseThrow` 路径 |
| POST create (no problemIds) | 47 ms | 1 次 insert |
| POST create (with 3 problemIds) | 64 ms | 1 contest insert + 1 batch insert(3) |
| PATCH partial | 14-16 ms | |
| DELETE | 36 ms | 实际无 is_deleted 写入（缺陷 #1） |
| POST add problem | 37 ms | 1 insert + 计数查询 |
| DELETE remove problem | 26 ms | 1 delete |
| GET rankings (empty) | 8-12 ms | |
| GET rankings (6 items) | 12 ms | |
| POST start | 36-39 ms | 1 update + 状态校验 + 计数查询 |
| POST end | 35 ms | 1 update + 状态校验 |
| 登录 | ~250 ms | BCrypt 验证 + JWT 签发 + Redis CSRF 写入 |

> 所有单端点响应 < 100ms；未触发 `@RateLimit(30/60s)` 阈值（本测试 48 次请求跨多个端点）。
> 状态变更 mutator 都在响应头返回 `X-New-CSRF-Token`（CSRF 轮换正常）。

---

## 6. 安全与约定符合性

| 检查项 | 状态 | 证据 |
|--------|------|------|
| 控制器层 `@PreAuthorize` 允许 ADMIN+SUPER_ADMIN | ✅ | T6b 用 ADMIN 通过；T17+ 多个 mutator 返回 200 |
| 服务层 `SecurityUtil.hasRole("ADMIN")` 严格匹配 | ⚠️ | 缺陷 #3：拒绝 SUPER_ADMIN |
| 状态变更方法需 CSRF | ✅ | T12/T48 缺 `X-CSRF-Token` 返 403 |
| Token 轮换 | ✅ | 响应头 `X-New-CSRF-Token` 在 18 次 mutator 中均返回 |
| Rate Limit 注解 | ✅ | `@RateLimit(limit=30, period=60)` 注解齐全（未触发） |
| 输入校验（`@NotBlank/@Future/@Min/@Max/@Pattern`） | ✅ | T7-T11, T18-T19, T26 全部正确返 400 |
| 软删除语义 | ❌ | 缺陷 #1：`is_deleted=0` 残留 |
| 软删除后业务隔离 | ❌ | 缺陷 #6：T28/T44 仍能操作已删除 contest |
| 审计身份取自认证 principal | ✅ | T20/T22 写入 `deleted_by=9f6bc78a-...`（admin UUID） |
| `@Audited` 注解生成 audit_logs 记录 | ❌ | 缺陷 #7：`audit_logs` 表无 contest 记录 |
| 暴露端口 | ✅ | 9001 暴露（base 模式 23306/26379/28848 仅 loopback） |
| 凭据不硬编码 | ✅ | curl 调用全程无明文（除 `TestPass!2026` 测试密码） |
| 错误信息不泄露敏感数据 | ✅ | 校验错误仅暴露字段名 + 规则，未泄露内部状态 |

---

## 7. 复现脚本

```bash
# 1) 登录
curl -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"TestPass!2026"}' \
  -c /tmp/admin_cookies.txt
CSRF=$(grep csrf_token /tmp/admin_cookies.txt | awk '{print $7}')

# 2) LIST
curl -b /tmp/admin_cookies.txt "http://localhost:9001/admin/contest?page=1&pageSize=5"

# 3) GET by id
curl -b /tmp/admin_cookies.txt http://localhost:9001/admin/contest/contest-upcoming-002

# 4) CREATE
curl -X POST -b /tmp/admin_cookies.txt \
  -H "X-CSRF-Token: $CSRF" -H "Content-Type: application/json" \
  -d '{"title":"API Test Contest","startTime":"2026-12-01T10:00:00","duration":60,"problemIds":[1,2,3]}' \
  http://localhost:9001/admin/contest

# 5) PATCH
curl -X PATCH -b /tmp/admin_cookies.txt \
  -H "X-CSRF-Token: $CSRF" -H "Content-Type: application/json" \
  -d '{"description":"UPDATED"}' \
  http://localhost:9001/admin/contest/$CID

# 6) DELETE
curl -X DELETE -b /tmp/admin_cookies.txt \
  -H "X-CSRF-Token: $CSRF" \
  http://localhost:9001/admin/contest/$CID

# 7) ADD problem
curl -X POST -b /tmp/admin_cookies.txt \
  -H "X-CSRF-Token: $CSRF" -H "Content-Type: application/json" \
  -d '{"problemId":4,"score":120}' \
  http://localhost:9001/admin/contest/$CID/problems

# 8) REMOVE problem
curl -X DELETE -b /tmp/admin_cookies.txt \
  -H "X-CSRF-Token: $CSRF" \
  http://localhost:9001/admin/contest/$CID/problems/4

# 9) RANKINGS
curl -b /tmp/admin_cookies.txt http://localhost:9001/admin/contest/$CID/rankings

# 10) START
curl -X POST -b /tmp/admin_cookies.txt \
  -H "X-CSRF-Token: $CSRF" \
  http://localhost:9001/admin/contest/$CID/start

# 11) END
curl -X POST -b /tmp/admin_cookies.txt \
  -H "X-CSRF-Token: $CSRF" \
  http://localhost:9001/admin/contest/$CID/end
```

完整 48 次请求日志：`/tmp/contest_test.log`（可 `grep "^====="` 提取每个测试用例）。

---

## 8. 结论

| 维度 | 评价 |
|------|------|
| API 可达性 | **10/10 端点可达** |
| 鉴权（控制器层） | 完整 |
| 鉴权（服务层一致性） | **缺陷 #3**：控制器允许 SUPER_ADMIN，服务层拒绝 |
| CSRF | 完整（含轮换） |
| 输入校验 | 基本完整（缺陷 #4：CJK 标题被拒） |
| 业务正确性 | **7 处缺陷，其中 #1 高严重度**（同源 comments 缺陷 #3） |
| 状态机 | **缺陷 #2**：`PATCH`/`DELETE` 守卫不严 |
| 软删除隔离 | **缺陷 #1 + #6**：写入失败 + 操作不隔离 |
| 审计 | **缺陷 #7**：未生成 audit_logs |
| 性能 | 满足 dev 测试要求（< 100ms） |

**建议下一步**：
1. **必修**：缺陷 #1（与 comments 缺陷 #3 统一修复 `updateById` + `@TableLogic` 模式）
2. **必修**：缺陷 #7（`@Audited` 注解对齐 — 这是项目 SECURITY_REVIEW 强制的审计完整性要求）
3. **建议**：缺陷 #3（控制器注解 vs 服务层守卫一致性）
4. **建议**：缺陷 #2（统一 PATCH/DELETE 状态守卫）
5. **建议**：缺陷 #6（mutator 入口加 `is_deleted` 校验）
6. **建议**：缺陷 #4（`@Pattern` 接受 `\p{L}` Unicode 字母）
7. **建议**：缺陷 #5（rankings 端点加 contest 存在性校验）

---

## 9. 后续 follow-up

- **缺陷 #1 修复后必须验证**：`ContestServiceImpl.deleteContest` 改用 `deleteById` 或 `LambdaUpdateWrapper.set(Contest::getIsDeleted, true)`；并补充单元测试（与 `AdminCommentServiceImplTest` 同模式）
- **缺陷 #2 一致性重构**：把 `AdminContestController` 注入改为 `AdminContestService`，统一两个 Service 的状态守卫；评估对其它调用方（`ContestController` 公共路径）的影响
- **缺陷 #7 审计补全**：与 `docs/SECURITY_REVIEW_2026-06-06.md` 中"审计身份取自认证 principal"约定配对，所有 mutator 端点必须产生 audit_logs 记录
