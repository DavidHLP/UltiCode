# `/problems` 前后端颗粒度对齐分析报告

**目标 URL**: `http://localhost:9003/problems`
**分析日期**: 2026/05/19
**模块**: Admin Problems (题目管理)
**状态**: 待修复

---

## 一、总体架构概览

| 层 | 技术 | 文件位置 |
|---|---|---|
| **前端 (管理后台)** | Vue 3 + TypeScript + Pinia | `management/src/views/problems/` |
| **后端** | Spring Boot + MyBatis-Plus | `backend-spring/src/main/java/com/ulticode/modules/problem/` + `admin/` |

### 前后端 API 映射总览

```
前端 GET  /admin/problems                    → 后端 AdminProblemController.getProblems()          ✅ 存在
前端 GET  /admin/problems/:id                → 后端 AdminProblemController.getProblemById()       ✅ 存在
前端 POST /admin/problems                    → 后端 AdminProblemController.createProblem()        ✅ 存在
前端 PATCH /admin/problems/:id               → 后端 AdminProblemController.updateProblem()        ✅ 存在
前端 DELETE /admin/problems/:id              → 后端 AdminProblemController.deleteProblem()        ✅ 存在
前端 POST /admin/problems/:id/publish        → 后端 AdminProblemController.publishProblem()       ✅ 存在
前端 POST /admin/problems/:id/unpublish      → 后端 AdminProblemController.unpublishProblem()     ✅ 存在
前端 POST /admin/bulk/problems/publish       → 后端 AdminProblemController.bulkAction()           ❌ 路径不匹配
前端 GET  /admin/problems/export             → 后端 AdminProblemController.exportProblems()       ✅ 存在
前端 GET  /admin/problems/:id/submissions    → 后端 (无)                                          ❌ 端点缺失
前端 POST /admin/problems/:id/flag           → 后端 (无)                                          ❌ 端点缺失
前端 POST /admin/problems/:id/moderate       → 后端 (无)                                          ❌ 端点缺失
前端 GET  /admin/problems/flagged            → 后端 (无)                                          ❌ 端点缺失
前端 POST /admin/problems/flagged/batch-moderate → 后端 (无)                                      ❌ 端点缺失
前端 POST /admin/problems/import             → 后端 (无)                                          ❌ 端点缺失
前端 GET  /admin/problems/:id/versions       → 后端 AdminProblemVersionController (独立)          ✅ 存在
前端 GET  /admin/problems/:id/header         → 后端 AdminProblemController.getHeader()            ✅ 存在
前端 GET  /admin/problems/:id/description    → 后端 AdminProblemController.getDescription()      ✅ 存在
前端 GET  /admin/problems/:id/code           → 后端 AdminProblemController.getCode()              ✅ 存在
前端 GET  /admin/problems/:id/cases          → 后端 AdminProblemController.getCases()             ✅ 存在
```

---

## 二、关键文件索引

### 前端

| 文件 | 用途 |
|---|---|
| `management/src/views/problems/ProblemsListView.vue` | 题目列表主视图 |
| `management/src/api/admin/problems.ts` | API 服务 + 类型定义 |
| `management/src/stores/admin/problems.ts` | Pinia store |
| `management/src/views/problems/composables/useProblemFilters.ts` | 过滤器逻辑 |
| `management/src/views/problems/composables/useProblemColumns.ts` | 表格列定义 |
| `management/src/views/problems/composables/useProblemActions.ts` | 行操作逻辑 |
| `management/src/composables/useDataTable.ts` | 通用数据表格 composable |
| `management/src/lib/entities/problem.ts` | Zod 验证 schema |

### 后端

| 文件 | 用途 |
|---|---|
| `modules/admin/controller/AdminProblemController.java` | Admin API 控制器 |
| `modules/problem/controller/ProblemController.java` | 用户端 API 控制器 |
| `modules/problem/controller/AdminProblemVersionController.java` | 版本管理控制器 |
| `modules/problem/service/impl/ProblemServiceImpl.java` | 核心业务逻辑 |
| `modules/admin/service/impl/AdminProblemServiceImpl.java` | Admin 专用业务逻辑 |
| `modules/problem/dto/ProblemQueryDTO.java` | 查询参数 DTO |
| `modules/problem/dto/ProblemVO.java` | 列表响应 VO |
| `modules/problem/dto/ProblemDetailResponse.java` | 详情响应 VO |
| `modules/admin/dto/AdminProblemListQueryDTO.java` | Admin 查询 DTO (孤立) |

---

## 三、数据模型对比

### 前端 `Problem` interface vs 后端 `ProblemVO`

| 字段 | 前端类型 | 后端类型 | 状态 |
|---|---|---|---|
| `id` | `string` | `Long` | ⚠️ 类型差异 (JSON 序列化后可工作) |
| `slug` | `string` | `String` | ✅ |
| `title` | `string` | `String` | ✅ |
| `difficulty` | `Difficulty` (EASY/MEDIUM/HARD) | `String` | ⚠️ 大小写见 §4.1 |
| `status` | `ProblemStatus` (solved/attempted/todo) | `String` | ✅ |
| `isPremium` | `boolean` | `Boolean` | ✅ |
| `hasSolution` | `boolean` | `Boolean` | ✅ |
| `isPublished` | `boolean` | `Boolean` | ✅ |
| `publishedAt` | `Date?` | `LocalDateTime` | ✅ |
| `publishedBy` | `string?` | `String` | ✅ |
| `isDeleted` | `boolean` | `Boolean` | ✅ |
| `deletedAt` | `Date?` | `LocalDateTime` | ✅ |
| `isFlagged` | `boolean?` | `Boolean` | ✅ |
| `flagReason` | `string?` | `String` | ✅ |
| `flagReportedBy` | `string?` | `String` | ✅ |
| `flagReportedAt` | `Date?` | `LocalDateTime` | ✅ |
| `flagStatus` | `string?` | `String` | ✅ |
| `flagReviewedBy` | `string?` | `String` | ✅ |
| `flagReviewedAt` | `Date?` | `LocalDateTime` | ✅ |
| `flagNotes` | `string?` | `String` | ✅ |
| `createdAt` | `Date` | `LocalDateTime` | ✅ |
| `updatedAt` | `Date` | `LocalDateTime` | ✅ |
| `tags` | `ProblemTag[]` | `List<ProblemTagVO>` | ✅ |
| `submissionCount` | `number?` | `Long` | ⚠️ 后端硬编码 0L |
| `solutionCount` | `number?` | `Long` | ⚠️ 后端硬编码 0L |
| `detail` | `ProblemDetail?` | — | 仅前端定义 (详情页使用) |
| `examples` | `ProblemExample[]?` | — | 仅前端定义 (详情页使用) |
| `languages` | `ProblemLanguage[]?` | — | 仅前端定义 (详情页使用) |
| — | — | `BigDecimal acceptanceRate` | ❌ 前端未定义 |
| — | — | `LocalDateTime completedTime` | ❌ 前端未定义 |

---

## 四、问题详解

### 4.1 `difficulty` 大小写不一致 [MEDIUM]

**位置**: 前端 `Difficulty` 枚举 vs 后端 `ProblemQueryDTO.difficulty`

| 端 | 发送/期望值 |
|---|---|
| 前端 | `EASY` / `MEDIUM` / `HARD` (全大写) |
| 后端 schema | `"Easy"` / `"Medium"` / `"Hard"` (首字母大写) |
| 后端 `buildProblemQueryWrapper` | `eq(Problem::getDifficulty, ...)` 精确匹配 |

**影响**: MySQL `utf8mb4_general_ci` 大小写不敏感，当前碰巧可工作。但若切换到 `utf8mb4_0900_as_cs` 或其他 CS collation，过滤器将失效。

**建议**: 后端统一接收大写枚举值，或在 `buildProblemQueryWrapper` 中做 `toUpperCase()` 规范化。

---

### 4.2 `sortBy` / `sortOrder` 完全无效 [HIGH]

**位置**: 后端 `ProblemServiceImpl.buildProblemQueryWrapper()` line 164-165

```java
// Order by ID ascending — 硬编码，忽略 query.getSortBy() 和 query.getSortOrder()
queryWrapper.orderByAsc(Problem::getId);
```

**前端排序选项** (`useProblemColumns.ts`):

| sortBy 值 | 含义 | 后端支持 |
|---|---|---|
| `title` | 按标题 | ❌ |
| `difficulty` | 按难度 | ❌ |
| `created_at` / `createdAt` | 按创建时间 | ❌ |
| `updated_at` / `updatedAt` | 按更新时间 | ❌ |
| `submissionCount` | 按提交数 | ❌ |

**影响**: 用户选择任何排序方式，列表始终按 ID 升序，排序下拉无实际效果。

**建议**: 在 `buildProblemQueryWrapper` 中根据 `sortBy` 动态构建 `orderBy` 子句，并注意 `submissionCount` 不在 `Problem` 表中需要特殊处理。

---

### 4.3 `tag` 过滤器无效 [MEDIUM]

**位置**: 后端 `ProblemServiceImpl.buildProblemQueryWrapper()`

`ProblemQueryDTO` 定义了 `tag` 字段，但 `buildProblemQueryWrapper` 从未使用 `query.getTag()`。

**前端**: `ProblemQueryParams` 有 `tag?: string`，`useProblemFilters` 中定义了 tag 过滤 UI。

**影响**: 标签筛选不工作，按标签过滤时返回全部数据。

**建议**: 在 `buildProblemQueryWrapper` 中通过子查询或 JOIN `problem_tag_relations` + `problem_tags` 实现标签过滤。

---

### 4.4 `isDeleted` 过滤器无效 [MEDIUM]

**位置**: 后端 `ProblemServiceImpl.buildProblemQueryWrapper()` line 138-139

```java
// Note: Soft delete is handled by @TableLogic, but admin may want to see deleted items
// For now, we don't explicitly filter deleted items
```

**影响**: Admin 无法查看已软删除的题目。`@TableLogic` 自动在所有查询中追加 `WHERE deleted = 0`，即使前端传 `isDeleted=true` 也无法看到已删除记录。

**建议**: Admin 端点需在查询时临时禁用 `@TableLogic`（MyBatis-Plus 的 `@InterceptorIgnore` 或手动 SQL），并根据 `isDeleted` 参数过滤。

---

### 4.5 `status` 过滤器值域不一致 [HIGH]

**位置**: 前端 `useProblemFilters.ts` vs `ProblemStatus` 枚举

| 来源 | 值 |
|---|---|
| 前端过滤器下拉 (status) | `all` / `DRAFT` / `PUBLISHED` / `ARCHIVED` |
| 前端 `ProblemStatus` 枚举 | `solved` / `attempted` / `todo` |
| 后端 `ProblemQueryDTO.status` | `solved` / `attempted` / `todo` |

**影响**: 过滤器下拉显示的是"发布状态"（DRAFT/PUBLISHED/ARCHIVED），但发送给后端和后端期望的是"解题状态"（solved/attempted/todo）。两组概念完全不同。

**建议**: 统一区分 `status`（解题状态）和 `publishStatus`（发布状态），过滤器 UI 发送正确的值。

---

### 4.6 缺失的后端端点 [CRITICAL]

| 端点 | 前端方法 | 功能 | 影响 |
|---|---|---|---|
| `POST /admin/problems/:id/flag` | `flagProblem()` | 标记题目 | Flag 功能不可用 |
| `POST /admin/problems/:id/moderate` | `moderateProblem()` | 审核标记 | 审核功能不可用 |
| `GET /admin/problems/flagged` | `getFlaggedProblems()` | 获取已标记题目列表 | 标记列表不可用 |
| `POST /admin/problems/flagged/batch-moderate` | `batchModerateProblems()` | 批量审核 | 批量审核不可用 |
| `GET /admin/problems/:id/submissions` | `getProblemSubmissions()` | 获取题目提交记录 | 提交记录不可用 |
| `POST /admin/problems/import` | `importProblems()` | 批量导入题目 | 导入功能不可用 |

**建议**: 在 `AdminProblemController` 或新建 `AdminProblemFlagController` 中实现这些端点。

---

### 4.7 批量操作路径不匹配 [HIGH]

| | 前端 | 后端 |
|---|---|---|
| 路径 | `POST /admin/bulk/problems/publish` | `POST /admin/problems/bulk` |
| 请求体 | `{ ids, action: 'publish'|'unpublish'|'delete'|'restore' }` | `BulkProblemRequestDTO` |

**前端** `problemsApi.bulkAction()` 发送到 `/admin/bulk/problems/publish`，但后端注册在 `/admin/problems/bulk`。

**影响**: 批量操作请求 404。

**建议**: 前端改为 `/admin/problems/bulk`，或后端添加 `/admin/bulk/problems/publish` 的映射。

---

### 4.8 `submissionCount` / `solutionCount` 永远为 0 [MEDIUM]

**位置**: 后端 `ProblemServiceImpl`

```java
response.setSubmissionCount(0L);  // line 211-212, 243-244
response.setSolutionCount(0L);
```

**影响**: 前端表格"提交数"列始终显示 0，排序 `submissionCount` 也无意义。

**建议**: 通过子查询或批量 COUNT 查询从 `submission` 表获取真实数据。

---

### 4.9 分页参数差异 [MEDIUM]

| 参数 | 前端 | 后端 |
|---|---|---|
| `page` 起始值 | 0 (0-based) | 1 (1-based) |
| 默认 `pageSize` | 10 | 20 |
| 参数名 | `limit` | `pageSize` 或 `limit` (alias) |

**前端** `useDataTable.ts` 默认 `pageIndex: 0, pageSize: 10`，发送 `page=0`。

**后端** `ProblemServiceImpl.listProblems()` 中 `page <= 0` 会回退到 `page=1`。

**影响**: 第一页数据正确（0 和 1 都会返回第一页），但前端总记录数显示和分页计算可能不一致。默认每页条数不同导致首次加载条数与预期不符。

**建议**: 前端统一使用 1-based 分页，或在后端做 `page = page + 1` 转换。

---

### 4.10 孤立 `AdminProblemListQueryDTO` [LOW]

**位置**: `modules/admin/dto/AdminProblemListQueryDTO.java`

定义了 `isFeatured` 和 `isPublic` 字段，但 `AdminProblemController.getProblems()` 实际使用 `ProblemQueryDTO`。此 DTO 是死代码。

**建议**: 删除此 DTO，或将 admin 端点迁移到使用此 DTO。

---

## 五、前端过滤器 → 后端参数完整映射

| 前端过滤器 | URL 参数 | 前端发送值 | 后端参数 | 后端实际处理 | 状态 |
|---|---|---|---|---|---|
| 搜索 | `search` | 自由文本 | `search` | ✅ 按 ID 或标题搜索 | ✅ |
| 难度 | `difficulty` | `EASY`/`MEDIUM`/`HARD` | `difficulty` | ⚠️ 大小写差异 | ⚠️ |
| 状态 | `status` | `DRAFT`/`PUBLISHED`/`ARCHIVED` | `status` | ❌ 值域不匹配 | ❌ |
| 发布状态 | `published` | `published`/`unpublished` | `isPublished` | ✅ 布尔值映射 | ✅ |
| 标签 | `tag` | 标签名称 | `tag` | ❌ 未使用 | ❌ |
| 排序 | `sortBy` | 见 §4.2 | `sortBy` | ❌ 未使用 | ❌ |
| 排序方向 | `sortOrder` | `asc`/`desc` | `sortOrder` | ❌ 未使用 | ❌ |

---

## 六、修复优先级

| 优先级 | # | 问题 | 修复量 |
|---|---|---|---|
| P0 | 4.6 | 6 个后端端点缺失 | 大 |
| P0 | 4.7 | 批量操作路径不匹配 | 小 (前端改路径) |
| P1 | 4.2 | sortBy/sortOrder 无效 | 中 |
| P1 | 4.5 | status 过滤器值域不一致 | 中 |
| P1 | 4.4 | isDeleted 过滤器无效 | 中 |
| P2 | 4.3 | tag 过滤器无效 | 中 |
| P2 | 4.1 | difficulty 大小写 | 小 |
| P2 | 4.9 | 分页参数差异 | 小 |
| P2 | 4.8 | submissionCount 永远为 0 | 中 |
| P3 | 4.10 | 孤立 DTO 清理 | 小 |
