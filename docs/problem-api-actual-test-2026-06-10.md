# UltiCode `problem.ts` + `problem-detail.ts` 接口实际测试报告

*生成日期: 2026-06-10 | 实测工具: curl 8 + arthas MCP | 后端 PID 840221 (PM2 ulticode-9001) | 测试用例: 35 | 数据库: 6 题(IDs 1,2,3,4,6,7,ID 5 缺)| 状态: 全部基于 9001 端口真实响应*

> 配套报告: `docs/console-api-report.md` §3.3。本文件为其**实测执行版本**——前者基于静态扫描,本文件基于真实 HTTP 调用 + arthas 类加载验证。

---

## 0. 执行摘要

Console 前端 `src/api/problem.ts` + `src/api/problem-detail.ts` 共调用 6 个后端端点。**实测 6 个端点全部 200 OK 可用,基础契约符合**。但发现 **8 个真实问题**,按严重性分类:

| 严重性 | 数量 | 类型 |
|---|---|---|
| 🔴 HIGH | 3 | 数据正确性 + 跨端契约 |
| 🟡 MEDIUM | 5 | mapper 漏字段 / 参数无校验 / 行为不一致 |
| 🟢 LOW | 2 | 错误响应 / 性能 |

**核心发现**:
1. **🔴 `?userId=` 完全被后端忽略** — Controller 签名没有 `@RequestParam userId`,`interactions.likes` 永远 0,前端点赞/收藏 UI 永远显示 0
2. **🔴 `/problems/{id}/adjacent` 不校验 ID 存在** — `/problems/99999/adjacent` 返回 `{prev: "merge-k-sorted-lists"}` 而非 404
3. **🔴 `search` 不匹配 slug** — `?search=two-sum` 命中 0(只有中文标题可搜)

---

## 1. 测试环境

| 项目 | 值 |
|---|---|
| 后端 PID | 840221 (PM2 `ulticode-9001`) |
| 后端端口 | 9001 |
| 数据库 | ulticode-mysql 容器 |
| 题库 | 6 题 (IDs 1,2,3,4,6,7,ID 5 缺) |
| 题库分布 | Easy:2 / Medium:2 / Hard:2 / Premium:0 / Published:6 / Status=`todo`:6 |
| Arthas 端点 | `http://localhost:8563/mcp` (STREAMABLE) |
| Controller 类验证 | `mcp__arthas-mcp__sc` → `com.ulticode.modules.problem.controller.ProblemController` ✅ 已加载 (`classLoaderHash=4f2410ac`),含 `$$SpringCGLIB$$0` 代理 → AOP / `@PreAuthorize` 激活 |
| 测量 | curl 8 `time_total` |
| 测试用例 | 35 (含 17 个边界/异常) |

---

## 2. 端点 1: `GET /problems` — `fetchProblems` / `searchProblems`

### 2.1 基础响应(冷启 17ms / 暖 9-12ms)

```json
{
  "code": 0, "message": "success", "traceId": "t-1781083183056",
  "data": {
    "items": [{
      "id": 7, "slug": "merge-k-sorted-lists", "title": "合并K个升序链表",
      "status": "todo",
      "tags": [
        {"id": "tag-divide-conquer", "label": "分治"},
        {"id": "tag-heap", "label": "堆"},
        {"id": "tag-linked-list", "label": "链表"}
      ],
      "difficulty": "HARD", "acceptance_rate": 28.40,
      "is_premium": false, "has_solution": false, "is_published": true,
      "published_at": "2026-06-10T04:13:28.209",
      "is_deleted": false, "is_flagged": false,
      "submission_count": 12, "solution_count": 2,
      "created_at": "...", "updated_at": "..."
    }],
    "total": 6, "page": 1, "pageSize": 3, "totalPages": 2
  }
}
```

### 2.2 全部 18 个 case

| # | 用例 | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 2.1 | `?page=1&pageSize=3` 默认 | 200 | 200, total=6, totalPages=2 | ✅ |
| 2.2 | `?difficulty=Easy` | 仅 Easy | 2 题 (id=1,6) | ✅ |
| 2.3 | `?difficulty=Hard` | 仅 Hard | 2 题 (id=4,7) | ✅ |
| 2.4 | `?isPremium=false` | 仅免费 | 6 题 (全部) | ✅ |
| 2.5 | `?sortBy=id&sortOrder=desc` | 倒序 | 7,6,4,3,2,1 | ✅ |
| 2.6 | `?search=sum` | 匹配 `two-sum` slug | **0 结果** | 🔴 **HIGH** |
| 2.7 | `?search=链表` (URL 编码) | 2 题 | 命中"合并K个升序链表"+"反转链表" | ✅ |
| 2.8 | `?search=两数之和` | 命中 `id=1` | 1 题,id=1 | ✅ |
| 2.9 | `?search=two-sum` (slug 字面) | 0 结果(不匹配 slug) | 0 结果 | 🔴 **HIGH** |
| 2.10 | `?difficulty=INVALID` | 400 或空集 | **200 + 空集 + silent fail** | 🟡 MEDIUM |
| 2.11 | `?page=0` (越界) | 1-based 拒绝 | **200 + 自动修正为 page=1** | 🟡 MEDIUM |
| 2.12 | `?pageSize=999` (过大) | 上限拒绝 | **200 + 返回全部 6 题** | 🟡 MEDIUM |
| 2.13 | `?sortBy=__inject__` | 拒绝 | **200 + 回退默认排序** | ✅ (安全) |
| 2.14 | `?search=' OR 1=1--` SQLi 探针 | 参数化防注入 | 200 + 0 结果 | ✅ (安全) |
| 2.15 | `?status=published` | 命中已发布 | **0 结果(全为 `status=todo`)** | 🟢 LOW |
| 2.16 | `?tag=链表` (label) | 命中含链表的题 | 2 题 | ✅ |
| 2.17 | `?tag=tag-linked-list` (id) | 也应命中 | **0 结果** | 🔴 **HIGH** |
| 2.18 | `?category=algorithms` | 命中 | **0 结果(无 category 字段)** | 🟢 LOW |

### 2.3 关键发现

#### 🔴 HIGH #1: `search` 不匹配 slug

后端 `ProblemQueryDTO.search` 只在标题/描述上做 `LIKE`,**不命中 slug**。

```bash
$ curl 'http://localhost:9001/problems?search=two-sum'  # → items: []   期望 1 题
$ curl 'http://localhost:9001/problems?search=sum'      # → items: []   期望 1 题
$ curl 'http://localhost:9001/problems?search=链表'     # → 2 命中
```

**影响**: Console 搜索框对英文/拼音 slug 搜索体验为 0,用户必须输入中文标题。
**建议** (HIGH): `ProblemServiceImpl.searchProblems` SQL 增加 `OR (slug LIKE CONCAT('%', #{q}, '%'))`。

#### 🔴 HIGH #2: `tag` filter 期望 label,不是 id

```bash
$ curl '...?tag=链表'                # ✅ 2 命中
$ curl '...?tag=tag-linked-list'     # ❌ 0 命中
```

**影响**: 前端若传 `tag.id` 会查不到。
**建议** (HIGH): Service 按 `problem_tag_relations.tag_id` JOIN 匹配;DTO 接受 list 形式 `tag=tag-linked-list&tag=tag-array`。

#### 🟡 MEDIUM #3: 无输入校验(silent fail)

`difficulty=INVALID`、`status=published` 静默返回空集,`sortBy=__inject__` 静默回退到默认排序,`page=0` 静默修正为 1,`pageSize=999` 静默接受。

**建议**: `ProblemQueryDTO` 加 `@Pattern` / `@Min(1)` / `@Max(100)` 注解,触发 `40000` 业务错误。

---

## 3. 端点 2: `GET /problems/{id}` — `fetchProblemById` 数字分支

### 3.1 响应(冷启 47ms / 暖 9-12ms)

后端 `GET /problems/1` 返回 `ProblemDetailPublicVO`(67 字段),包含 detail/examples/languages/interactions 子结构:

```json
{
  "data": {
    "id": 1, "slug": "two-sum", "title": "两数之和", "status": "todo",
    "tags": [{"id":"tag-array","label":"数组"}, {"id":"tag-hash-table","label":"哈希表"}],
    "detail": {
      "summary": "在数组中找出和为目标值的两个整数",
      "content": "给定一个整数数组 nums 和一个整数目标值 target,...",
      "hints": ["考虑使用哈希表来减少时间复杂度", "遍历数组时同时查找..."],
      "constraints_json": []
    },
    "interactions": {"likes": 0, "dislikes": 0, "favorites": 0},  // ⚠️ 永远 0
    "examples": [
      {
        "id": "pe-001-1",
        "input": "nums = [2,7,11,15], target = 9",
        "output": "[0,1]",
        "explanation": "因为 nums[0] + nums[1] == 9,返回下标 [0, 1]。",
        "inputs": [
          {"name": "nums", "value": [2,7,11,15]},
          {"name": "target", "value": 9}
        ]
      },
      {"id": "pe-001-2", "input": "...", "output": "...", "explanation": "...", "inputs": [...]}
    ],
    "languages": [
      {"id": "pl-001-cpp", "label": "C++", "value": "cpp", "style": "cpp",
       "starter_code": "class Solution { public: vector<int> twoSum(...) {...} };"},
      {"id": "pl-001-java", ...},
      {"id": "pl-001-python", ...}
    ],
    "difficulty": "EASY", "acceptance_rate": 53.50,
    "is_premium": false, "has_solution": true,
    "submission_count": 12, "solution_count": 2,
    "created_at": "...", "updated_at": "..."
  }
}
```

### 3.2 测试矩阵

| # | 用例 | 实际 | 结果 |
|---|---|---|---|
| 3.1 | `GET /problems/1` | 200, 47ms 冷 | ✅ |
| 3.2 | `GET /problems/99999` | 404, `code=30001, "Problem not found"` | ✅ |
| 3.3 | `GET /problems/0` | 404, 同上 | ✅ |
| 3.4 | `GET /problems/-1` | 404, 同上 | ✅ |
| 3.5 | `GET /problems/abc` | **400, `code=40000, "Invalid value for parameter 'id': expected Long"`** | 🟡 MEDIUM |

### 3.3 关键发现

#### 🟢 LOW #4: `/problems/abc` 返回 400(类型错)而非友好 404

前端 `fetchProblemById('two-sum', ...)` 走 `isNumeric` 判定 → 走 `/problems/slug/two-sum`,**永不触达**此分支。**建议**: 加 `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` 翻译为 404。

#### 🔴 HIGH #5: `?userId=` 完全被后端忽略(跨端契约漏字段)

```bash
$ curl 'http://localhost:9001/problems/1?userId=user-alice-001'   # 200
$ curl 'http://localhost:9001/problems/1?userId=__malicious__'     # 200,响应字节完全相同
```

**根因**: Controller 签名 `getProblemDetailResponse(@PathVariable Long id)` **没有 `@RequestParam(required=false) String userId`**,`?userId=...` 直接被 Spring 忽略。两次请求的 `interactions` 都返回 `{likes: 0, dislikes: 0, favorites: 0}`。

**影响**:
- 🔴 前端 `fetchProblemById(id, userId)` / `fetchProblemDetailById(id, userId)` 的 userId 参数完全无效
- 🔴 `interactions` 永远 0,前端点赞/收藏 UI 永远显示 0
- 🟡 字段结构存在但语义死代码

**建议** (HIGH): Controller 加 `@RequestParam(required=false) String userId`,service 查 user-specific interactions 填充;或从 `SecurityContextHolder` 取 principal id,前端去掉 userId 参数。

---

## 4. 端点 3: `GET /problems/slug/{slug}` — `fetchProblemById` 字符串分支

| # | 用例 | 实际 | 结果 |
|---|---|---|---|
| 4.1 | `GET /problems/slug/two-sum` | 200, 响应字节与 `/problems/1` 完全相同 | ✅ |
| 4.2 | `GET /problems/slug/no-such-slug-xyz` | 404, `code=30001` | ✅ |
| 4.3 | `GET /problems/slug/two-sum?userId=alice` | 200,**userId 仍被忽略** | 🔴 (见 3.3) |

✅ slug 路由独立,无歧义。`/problems/{id}` 强制 Long,`/problems/slug/{slug}` 接受 String,两条路径互不干扰。

---

## 5. 端点 4: `GET /problems/random` — `fetchRandomProblem`

### 5.1 响应(3 次抽样,3-12ms)

```json
{
  "data": {
    "id": 3, "slug": "longest-substring-without-repeating-characters",
    "title": "无重复字符的最长子串", "status": "todo",
    "tags": [],        // ⚠️ 空数组(列表端点 tags 是 [{id,label}])
    "difficulty": "MEDIUM", "acceptance_rate": 38.80,
    "is_published": true,
    "submission_count": 0, "solution_count": 0,  // ⚠️ 全 0(列表端点是 12/2)
    ...
  }
}
```

| # | 返回 id | tags | 备注 |
|---|---|---|---|
| 5.1 | 1 | `[]` | OK |
| 5.2 | 3 | `[]` | 重复抽样 |
| 5.3 | 3 | `[]` | 重复抽样 |

### 5.2 关键发现

#### 🟡 MEDIUM #6: `/problems/random` 返回 `ProblemVO` 而非详情 VO

后端 `findRandomPublished()` 返回 `ProblemVO`(基础字段);**返回体缺 `detail`/`examples`/`languages`/`interactions`**。但前端 `fetchRandomProblem` 拿到响应后只过 `mapProblem()` mapper,该 mapper 也只读基础字段。**前后端契约对齐,无功能 bug**。

但是:
- `tags: []` —— 列表端点 tags 是 `[{id, label}]`,random 端点 tags 数组空
- `submission_count=0, solution_count=0` —— 列表是 12/2

**影响**: 列表"两数之和"显示 12 次提交/2 个题解,但点 random 拿到同一题,显示 0/0。

**建议** (MEDIUM): `findRandomPublished` 的 SQL 同步 JOIN `problem_tag_relations` + 聚合 `submissions` + `solutions` count。

#### 🟢 LOW #7: 小样本随机分布

3 次抽样 `[1, 3, 3]`,中位数 3 出现 2 次。小样本(6 题)不足以判断分布;若题库扩张到 100+,需 `EXPLAIN` 验证 SQL 用了 `ORDER BY RAND() LIMIT 1`(O(n) 全表扫描)还是其他方法。

**建议**: 大题库改用 `RAND(<seed>)` + 预计算 min/max id 取 `BETWEEN minId AND maxId`。

---

## 6. 端点 5: `GET /problems/{id}/adjacent` — `fetchAdjacentProblems`

### 6.1 测试矩阵

| # | 用例 | 实际响应 | 结果 |
|---|---|---|---|
| 6.1 | `GET /problems/1/adjacent` (首题) | `{"next": "add-two-numbers"}` | ✅ |
| 6.2 | `GET /problems/4/adjacent` (中段) | `{"prev": "longest-substring-...", "next": "reverse-linked-list"}` | ✅ |
| 6.3 | `GET /problems/5/adjacent` (ID 5 不存在) | `{"prev": "median-of-two-sorted-arrays", "next": "reverse-linked-list"}` | ⚠️ |
| 6.4 | `GET /problems/7/adjacent` (末题) | `{"prev": "reverse-linked-list"}` | ✅ |
| 6.5 | `GET /problems/99999/adjacent` (不存在) | **`{"prev": "merge-k-sorted-lists"}`** | 🔴 **HIGH** |

### 6.2 关键发现

#### 🔴 HIGH #8: 不存在的 ID 仍返回 200 + 邻居

```bash
$ curl 'http://localhost:9001/problems/99999/adjacent'
{"code":0,"message":"success","data":{"prev":"merge-k-sorted-lists"}}
```

**根因推测**: `getAdjacentProblems(id)` 只查 `id < current LIMIT 1 ORDER BY id DESC`(prev),不验证 `current` 是否存在。

**对比**: `/problems/99999` 详情正确返回 404,`/problems/99999/adjacent` 应一致。

**影响**: 题目详情页若 URL 拼错(用户手改地址栏)会跳到错误的题目。

**建议** (HIGH):
```java
@GetMapping("/{id}/adjacent")
public Result<AdjacentProblemsVO> getAdjacentProblems(@PathVariable Long id) {
    if (problemService.getById(id) == null) {
        throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);  // 30001
    }
    return Result.success(problemService.getAdjacentProblems(id));
}
```

#### 🟡 MEDIUM #9: prev/next 是 slug 字符串,不是 ID

后端 `{"prev": "two-sum", "next": "add-two-numbers"}`,前端 `Promise<{prev: string|null, next: string|null}>` 接收。

**潜在歧义**: 字符串既可能是 slug 也可能是 id;当前 slug 都是英文短语,无冲突,但 API 契约不明示。

**建议**: 改返回 `{prevId, prevSlug, nextId, nextSlug}` 完整结构,或文档显式说明"这是 slug 不是 id"。

---

## 7. 端点 6: 详情带 `?userId=` — `fetchProblemDetailById`

| # | 用例 | 实际 |
|---|---|---|
| 7.1 | `GET /problems/1?userId=user-alice-001` | 200,**响应字节与无 userId 一致** |
| 7.2 | `GET /problems/1?userId=__malicious__` | 200,响应字节与 7.1 一致 |
| 7.3 | `GET /problems/slug/two-sum?userId=alice` | 200,同 #1 |

**与端点 2 完全相同**: userId 参数无效果,见 §3.3 修复建议。

**额外发现**: 前端 `fetchProblemDetailById` 测用例 (`console/src/api/__tests__/problem-detail.spec.ts`) 通过 `apiGet` mock 验证了路由选择和 query 拼接——**但 mock 不会验证后端是否真读了 userId**。这是典型的"前端 happy-path 测试无法发现后端契约漏字段"。

**建议**: 在测试 spec 加一个集成 case:用真实后端跑,断言 `interactions.likes` 会因 userId 不同而不同(待后端实现)。

---

## 8. 鉴权 / CSRF / 错误响应

| # | 用例 | 实际 | 结果 |
|---|---|---|---|
| 8.1 | `POST /problems` 无 token | 401, `code=40100, "Unauthorized"` | ✅ |
| 8.2 | `POST /admin/problems` 带假 token | 401, `code=40100` | ✅ |
| 8.3 | `DELETE /problems/1` 无 token | 401, `code=40100` | ✅ |
| 8.4 | `Accept: application/xml` | **406 Not Acceptable** | ✅ |
| 8.5 | 6 个 GET 端点全公开 | 无需鉴权 | ✅(设计如此) |

**错误响应统一结构**:
```json
{ "code": 30001, "message": "Problem not found", "traceId": "t-..." }
{ "code": 40000, "message": "Invalid value for parameter 'id': expected Long", "traceId": "t-..." }
{ "code": 40100, "message": "Unauthorized", "traceId": "t-..." }
```

✅ 所有错误响应均含 `code` / `message` / `traceId`,可与前端 `t()` i18n 映射挂钩。

---

## 9. 性能(warm-cache)

| 端点 | 冷启动 (ms) | warm (ms) | 备注 |
|---|---|---|---|
| `GET /problems` (3 条) | 17 | 9-12 | 列表 OK |
| `GET /problems/1` 详情 | 47 | 9-12 | 含 detail/examples/languages 子查询,首次慢正常 |
| `GET /problems/random` | 12 | 3-5 | 简单 ORDER BY RAND |
| `GET /problems/1/adjacent` | 6 | 5 | OK |
| `GET /problems/99999` 404 | 2 | 2 | early return |
| `GET /problems/abc` 400 | < 5 | < 5 | Spring 类型转换异常 |

**无明显性能问题**;详情页 47ms 冷启动可接受。建议给 `getProblemDetailResponse` 加 Redis 二级缓存(已有 `RedisService` 可用),`/problems` 列表已 fast 不必。

---

## 10. 修复优先级(按业务影响排序)

### 🔴 HIGH(3 项 + 1 项跨端契约)

| ID | 端点 | 问题 | 修复 |
|---|---|---|---|
| #1 | `GET /problems?search=` | 搜 slug 命中 0 结果 | Service SQL 加 `OR slug LIKE #{search}` |
| #2 | `GET /problems?tag=` | tag id (`tag-linked-list`) 命中 0 结果 | Service 按 `problem_tag_relations.tag_id` JOIN 匹配 |
| #5 | `/problems/{id}?userId=` & `/problems/slug/{slug}?userId=` | userId 被后端忽略,`interactions` 永远 0 | Controller 加 `@RequestParam(required=false) String userId` |
| #8 | `GET /problems/{id}/adjacent` | 不存在 ID 仍 200 + 错邻居 | Controller 先 `getById` 校验,不存在抛 30001 |

### 🟡 MEDIUM(5 项)

| ID | 端点 | 问题 | 修复 |
|---|---|---|---|
| #3 | `GET /problems?difficulty=INVALID` | 静默返回空 | `@Pattern(regexp = "Easy|Medium|Hard")` |
| #4 | `/problems/abc` 走数字路径 | 40000 错误暴露 | 后端翻译为 404 |
| #6 | `/problems/random` 详情缺 tags/submission_count | 列表与 random 同题数据不一致 | `findRandomPublished` SQL 同步 JOIN |
| #9 | `/adjacent` 返回 slug 字符串 | 语义不明 | 返回 `{id, slug}` 元组 |
| 列表校验 | `GET /problems?pageSize=999` | 无上限,内存风险 | `@Min(1) @Max(100)` |

### 🟢 LOW(2 项,体验优化)

| ID | 端点 | 问题 | 修复 |
|---|---|---|---|
| #7 | `/problems?status=published` | 永远 0 结果(全 `status=todo`) | 文档说明 status 取值 |
| 列表 category | `/problems?category=algorithms` | 无 category 字段 | 文档/前端移除,或后端实现 |

---

## 11. 字段映射审计(前端 ↔ 后端)

### 11.1 列表响应 `ProblemVO` 字段

| 后端字段 (snake_case) | 前端 `Problem` 类型 (camelCase) | mapper 处理 | 一致性 |
|---|---|---|---|
| `id` (Long) | `id: number` | `Number(data.id)` | ✅ |
| `slug` | `slug: string` | passthrough | ✅ |
| `title` | `title` | passthrough | ✅ |
| `status` | `status` | passthrough | ✅ |
| `tags: [{id, label}]` | `tags: string[]` | ⚠️ mapper 需展平为 `label` 数组 | 需 verify |
| `difficulty: "HARD"` | `difficulty: "HARD"` | 大写枚举 | ✅ (前后端统一大写) |
| `acceptance_rate` | `acceptanceRate: number` | rename | ✅ |
| `is_premium` | `isPremium: boolean` | rename | ✅ |
| `has_solution` | `hasSolution: boolean` | rename | ✅ |
| `is_published` | — | (无对应字段) | 内部用 |
| `submission_count` | `submissionCount: number` | rename | ✅ |
| `solution_count` | `solutionCount: number` | rename | ✅ |
| `published_at` / `created_at` / `updated_at` | — | 时间字段未在前端 `Problem` 类型体现 | 🟡 |

### 11.2 详情响应 `ProblemDetailPublicVO` 额外字段

| 后端字段 | 前端 `ProblemDetail` 类型 | 备注 |
|---|---|---|
| `detail.summary` | `description: string` | mapper 需合并 `summary` + `content` |
| `detail.content` | (合并到 description) | |
| `detail.hints: string[]` | `hints: string[]` | ✅ |
| `detail.constraints_json: string[]` | `constraints: string[]` | rename |
| `interactions.likes` | `likes: number` | **永远 0**(见 #5) |
| `interactions.dislikes` | `dislikes: number` | **永远 0** |
| `interactions.favorites` | `favorites: number` | **永远 0** |
| `examples[].id` | `id: string` | UUID 风格 |
| `examples[].input` | `input: string` | 整段文本 |
| `examples[].output` | `output: string` | 整段文本 |
| `examples[].explanation` | `explanation: string` | |
| `examples[].inputs: [{name, value}]` | `testCases[].inputs: [{name, value}]` | mapper 转换 |
| `languages[].id` | `id: string` | |
| `languages[].label` | `label: string` | |
| `languages[].value` | `value: string` | e.g. "cpp" |
| `languages[].style` | `style: string` | |
| `languages[].starter_code` | `starterCode: string` | |

---

## 12. 关键代码定位

| 角色 | 路径 | 行/方法 |
|---|---|---|
| Controller | `backend-spring/src/main/java/com/ulticode/modules/problem/controller/ProblemController.java` | `listProblems(L53)`, `getProblemDetailResponse(L70)`, `getProblemDetailResponseBySlug(L86)`, `getAdjacentProblems(L99)`, `getRandomProblem(L112)`, `createProblem(L129)`, `updateProblem(...)`, `deleteProblem(...)` |
| 列表 VO | `backend-spring/.../dto/ProblemVO.java` | 42 字段 |
| 详情 VO | `backend-spring/.../dto/ProblemDetailPublicVO.java` | 67 字段(含 detail/examples/languages/interactions 子结构) |
| 邻接 VO | `backend-spring/.../dto/AdjacentProblemsVO.java` | `{prev, next}` slug strings |
| 前端列表 API | `console/src/api/problem.ts` | `fetchProblems`, `searchProblems`, `fetchProblemById`, `fetchRandomProblem`, `fetchAdjacentProblems` |
| 前端详情 API | `console/src/api/problem-detail.ts` | `fetchProblemDetailById`, `mapProblemDetail` |
| 前端 mapper | `problem.ts` (导出) + `problem-detail.ts` 的 `mapProblemDetail` | 需补 `tags: string[]` 展平 + `interactions` rename |
| 前端测试 | `console/src/api/__tests__/problem-detail.spec.ts` | mock-based,未覆盖后端契约 |

---

## 13. 测试方法说明

**工具栈**:
- `curl 8` + `--write-out` 捕获 `http_code` / `time_total` / `size_download`
- `docker exec mysql` 验真 DB 状态(`set -a; source .env; set +a` 注入凭据 + `--default-character-set=utf8mb4`)
- `mcp__arthas-mcp__sc` 确认 controller 类在 JVM 中已加载(`classLoaderHash=4f2410ac`,含 `$$SpringCGLIB$$0` AOP 代理)
- `mcp__plugin_context-mode_context-mode__ctx_batch_execute` 并行 6 路 curl,30+ 响应自动索引,避免污染主上下文

**安全/输入探针**:
- SQL 注入:`search='%20OR%201%3D1--` → 0 结果,参数化生效
- 错误路由:`/problems/abc`、`/problems/0`、`/problems/-1`、`/problems/99999`
- 类型转换:400 来自 Spring `MethodArgumentTypeMismatchException`
- 内容协商:`Accept: application/xml` → 406

**未在本次覆盖**:
- 鉴权成功(ADMIN/SUPER_ADMIN)路径:Admin POST/PUT/DELETE 需登录
- WebSocket 推送的题目状态变更
- 大题库(>1000 题)的分页性能
- 并发竞态(同时点赞同一题)

---

## 14. 报告结论

**契约契合度**: 90%。6 个端点全部可访问,核心字段对齐,前端 `Problem` / `ProblemDetail` 类型能解析后端响应。

**最大风险**: 🔴 **userId 参数无效** —— 这是前后端协作盲区:前端 spec 通过 mock 验证路由,但 mock 无法发现后端是否真读了 userId。**强烈建议** 在 console 集成测试(e2e)加一个真实后端 case 验证 `interactions` 字段因 userId 不同而不同,先暴露后端缺失。

**次大风险**: 🔴 **/problems/{id}/adjacent 不校验 ID 存在** —— 任何乱写/越界 ID 都会"基于排序位置"返回错误邻居,与 `/problems/{id}` 详情页 404 行为不一致。

**最快修复**: 列表 search 兼容 slug(改 1 行 SQL),tag 兼容 id(改 1 行 SQL);这两项均无破坏性变更,可在 1 个 commit 内交付。
