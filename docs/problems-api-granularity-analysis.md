# Management Problems 页面前后端接口颗粒度分析报告

> 生成日期: 2026-05-20 | 分析范围: management 前端 `/problems` 页面全部接口

## 一、接口映射总览

25 个前端 API 调用中，**20 个路径和 HTTP 方法完全对齐**，5 个存在颗粒度差异。

| # | 前端 API 方法 | 前端路径 | 后端端点 | 后端路径 | 状态 |
|---|---|---|---|---|---|
| 1 | `getProblems` | `GET /admin/problems` | `AdminProblemController.getProblems` | `GET /admin/problems` | 对齐 |
| 2 | `getProblem` | `GET /admin/problems/:id` | `AdminProblemController.getProblemById` | `GET /admin/problems/{id}` | **颗粒度差异** |
| 3 | `createProblem` | `POST /admin/problems` | `AdminProblemController.createProblem` | `POST /admin/problems` | 对齐 |
| 4 | `updateProblem` | `PATCH /admin/problems/:id` | `AdminProblemController.updateProblem` | `PATCH /admin/problems/{id}` | 对齐 |
| 5 | `deleteProblem` | `DELETE /admin/problems/:id` | `AdminProblemController.deleteProblem` | `DELETE /admin/problems/{id}` | 对齐 |
| 6 | `publishProblem` | `POST /admin/problems/:id/publish` | `AdminProblemController.publishProblem` | `POST /admin/problems/{id}/publish` | 对齐 |
| 7 | `unpublishProblem` | `POST /admin/problems/:id/unpublish` | `AdminProblemController.unpublishProblem` | `POST /admin/problems/{id}/unpublish` | 对齐 |
| 8 | `bulkAction` | `POST /admin/problems/bulk` | `AdminProblemController.bulkAction` | `POST /admin/problems/bulk` | 对齐 |
| 9 | `bulkEdit` | `POST /admin/problems/bulk` (action=edit) | 同上 bulkAction | `POST /admin/problems/bulk` | 复用同一端点 |
| 10 | `exportProblems` | `GET /admin/problems/export` | `AdminProblemController.exportProblems` | `GET /admin/problems/export` | 对齐 |
| 11 | `importProblems` | `POST /admin/problems/import` | `AdminProblemController.importProblems` | `POST /admin/problems/import` | 对齐 |
| 12 | `flagProblem` | `POST /admin/problems/:id/flag` | `AdminProblemController.flagProblem` | `POST /admin/problems/{id}/flag` | 对齐 |
| 13 | `moderateProblem` | `POST /admin/problems/:id/moderate` | `AdminProblemController.moderateProblem` | `POST /admin/problems/{id}/moderate` | 对齐 |
| 14 | `getFlaggedProblems` | `GET /admin/problems/flagged` | `AdminProblemController.getFlaggedProblems` | `GET /admin/problems/flagged` | 对齐 |
| 15 | `batchModerateProblems` | `POST /admin/problems/flagged/batch-moderate` | `AdminProblemController.batchModerateProblems` | `POST /admin/problems/flagged/batch-moderate` | 对齐 |
| 16 | `getProblemSubmissions` | `GET /admin/problems/:id/submissions` | `AdminProblemController.getProblemSubmissions` | `GET /admin/problems/{id}/submissions` | 对齐 |
| 17 | `getHeader` | `GET /admin/problems/:id/header` | `AdminProblemController.getProblemHeader` | `GET /admin/problems/{id}/header` | 对齐 |
| 18 | `getDescription` | `GET /admin/problems/:id/description` | `AdminProblemController.getProblemDescription` | `GET /admin/problems/{id}/description` | 对齐 |
| 19 | `getCode` | `GET /admin/problems/:id/code` | `AdminProblemController.getProblemCode` | `GET /admin/problems/{id}/code` | 对齐 |
| 20 | `getCases` | `GET /admin/problems/:id/cases` | `AdminProblemController.getProblemCases` | `GET /admin/problems/{id}/cases` | 对齐 |
| 21 | `getProblemVersions` | `GET /admin/problems/:id/versions` | `AdminProblemVersionController` | `GET /admin/problems/{id}/versions` | 对齐 |
| 22 | `getProblemVersion` | `GET /admin/problems/:id/versions/:versionId` | 同上 | `GET /admin/problems/{id}/versions/{versionId}` | 对齐 |
| 23 | `getVersionDiff` | `GET /admin/problems/:id/versions/:from/diff/:to` | 同上 | `GET /admin/problems/{id}/versions/{from}/diff/{to}` | 对齐 |
| 24 | `rollbackToVersion` | `POST /admin/problems/:id/versions/:versionId/rollback` | 同上 | `POST /admin/problems/{id}/versions/{versionId}/rollback` | 对齐 |
| 25 | `createInitialVersion` | `POST /admin/problems/:id/versions/create-initial` | 同上 | `POST /admin/problems/{id}/versions/create-initial` | 对齐 |

---

## 二、关键颗粒度差异

### 差异 1: `getProblem` 返回数据深度不同 (HIGH)

| | 前端 `Problem` 接口 | 后端 `AdminProblemController` 返回 |
|---|---|---|
| 端点 | `GET /admin/problems/:id` | `GET /admin/problems/{id}` |
| 返回类型 | `Problem`（含 `detail?`, `examples?`, `languages?`） | `ProblemVO`（仅基础字段 + tags） |

前端类型声明了 `detail?: ProblemDetail`、`examples?: ProblemExample[]`、`languages?: ProblemLanguage[]`，但后端 Admin 端点返回 `ProblemVO` 不包含这些字段。而公开端点 `ProblemController` 返回 `ProblemDetailResponse` 才包含完整数据。

**实际影响**: 前端详情页不能依赖 `getProblem` 获取完整数据，必须使用 tab-specific API。这是设计意图（按需加载各 tab），但类型定义不反映这一点。

---

### 差异 2: `CreateProblemDto` 字段类型序列化不一致 (HIGH)

| 字段 | 前端类型 | 后端类型 | 差异 |
|------|---------|---------|------|
| `examples` | `ProblemExample[]` (对象数组) | `String` (JSON 字符串) | **结构化 vs 字符串化** |
| `constraints` | `string[]` (字符串数组) | `String` (单个字符串) | **数组 vs 字符串** |
| `hints` | `string[]` (字符串数组) | `String` (JSON 字符串) | **数组 vs 字符串** |
| `languages` | `string[]` (字符串数组) | `List<String>` | 基本对齐 |
| `tags` | `string[]` | `List<String>` | 对齐 |

**问题**: 前端将 `examples` 定义为结构化对象数组，但后端 `CreateProblemDTO` 期望 JSON 字符串。前端发送请求前必须 `JSON.stringify()`，否则后端反序列化失败。`constraints` 和 `hints` 同理。

---

### 差异 3: `UpdateProblemDto` 与 `CreateProblemDto` 字段类型不统一 (MEDIUM)

| 字段 | Create (前端) | Update (前端) | 后端 Update |
|------|-------------|-------------|------------|
| `examples` | `ProblemExample[]` | `string` | `String` |
| `constraints` / `constraintsJson` | `string[]` | `string` | `String` |
| `hints` | `string[]` | `string` | `String` |
| `languages` | `string[]` | `LanguageConfig[]` | `List<LanguageConfigDTO>` |

同一字段在创建和更新 DTO 之间类型定义不一致，增加了前端使用的心智负担和出错概率。

---

### 差异 4: Tab-specific API 类型对齐情况 (LOW - 已对齐)

| Tab | 前端类型 | 后端 VO | 对齐状态 |
|-----|---------|---------|---------|
| Header | `HeaderData` | `HeaderDataVO` | **对齐** - 字段完全匹配 |
| Description | `DescriptionData` | `DescriptionDataVO` | **对齐** - 嵌套结构一致 (`detail.summary`, `detail.content`, `detail.constraintsJson`, `detail.hints`) |
| Code | `CodeData` | `CodeDataVO` | **对齐** - `languages` 数组结构一致 (`id`, `language`, `value`, `style`, `starterCode`) |
| Cases | `CasesData` | `CasesDataVO` | **对齐** - `examples` + `detail` + `tags` 结构一致 |

Tab-specific API 是前后端颗粒度最一致的部分。

---

### 差异 5: 后端存在但前端未调用的端点 (LOW)

| 后端端点 | 方法 | 前端是否调用 | 说明 |
|---------|------|------------|------|
| `GET /problems/slug/{slug}` | 公开 | 否 | management 不需要 slug 查询 |
| `GET /problems/{id}/adjacent` | 公开 | 否 | management 不需要前后导航 |
| `GET /problems/random` | 公开 | 否 | management 不需要随机题目 |
| `PUT /problems/{id}` | 公开 | 否 | Admin 用 PATCH |

这些是公开 API 端点，management 前端不需要调用，**合理**。

---

## 三、修复建议优先级

| 优先级 | 问题 | 建议 |
|--------|------|------|
| **P0** | `CreateProblemDto` 字段类型 (examples/constraints/hints) | 统一为结构化类型，后端改为接收 `List<ExampleData>` / `List<String>`，或前端在发送前做 `JSON.stringify()` |
| **P0** | `UpdateProblemDto` 与 `CreateProblemDto` 类型不统一 | 统一两者的字段类型定义 |
| **P1** | `Problem` 接口包含后端不返回的字段 | 移除 `detail?`/`examples?`/`languages?`，或标注为可选仅用于列表页 |
| **P2** | Tab API 类型已对齐 | 无需修改 |

---

## 四、后端文件索引

| 类别 | 文件路径 |
|------|---------|
| Admin Controller | `backend-spring/.../modules/admin/controller/AdminProblemController.java` |
| Version Controller | `backend-spring/.../modules/problem/controller/AdminProblemVersionController.java` |
| Public Controller | `backend-spring/.../modules/problem/controller/ProblemController.java` |
| Header VO | `backend-spring/.../modules/admin/dto/problem/HeaderDataVO.java` |
| Description VO | `backend-spring/.../modules/admin/dto/problem/DescriptionDataVO.java` |
| Code VO | `backend-spring/.../modules/admin/dto/problem/CodeDataVO.java` |
| Cases VO | `backend-spring/.../modules/admin/dto/problem/CasesDataVO.java` |
| Create DTO | `backend-spring/.../modules/problem/dto/CreateProblemDTO.java` |
| Update DTO | `backend-spring/.../modules/problem/dto/UpdateProblemDTO.java` |
| Problem VO | `backend-spring/.../modules/problem/dto/ProblemVO.java` |
| Detail Response | `backend-spring/.../modules/problem/dto/ProblemDetailResponse.java` |

## 五、前端文件索引

| 类别 | 文件路径 |
|------|---------|
| API 定义 | `management/src/api/admin/problems.ts` |
| Store | `management/src/stores/admin/problems.ts` |
| 列表页 | `management/src/views/problems/ProblemsListView.vue` |
| 详情页 | `management/src/views/problems/ProblemDetailView.vue` |
| 列定义 | `management/src/views/problems/columns.ts` |
| Zod Schema | `management/src/lib/schemas/problem.ts` |

---

## 六、总结

- **路径和 HTTP 方法**: 25/25 完全对齐
- **Tab-specific API**: 4/4 完全对齐（颗粒度最佳实践）
- **DTO 字段类型**: 存在 3 处 HIGH 级别差异（`examples`/`constraints`/`hints` 的序列化格式）
- **返回类型**: 1 处 HIGH 级别差异（`getProblem` 前端期望 vs 后端返回）

核心问题集中在 **DTO 字段的序列化格式**：后端将 `examples`/`constraints`/`hints` 存储为 JSON 字符串，DTO 也定义为 `String` 类型接收；而前端定义为结构化数组。需要在前后端之间统一约定：要么后端改为接收结构化对象，要么前端在 API 层做 `JSON.stringify()` 转换。
