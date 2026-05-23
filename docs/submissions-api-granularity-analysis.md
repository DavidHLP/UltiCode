# Submissions 管理页面前后端 API 颗粒度对齐分析

> 分析日期: 2026-05-23
> 前端页面: `http://localhost:9003/submissions`
> 前端路径: `management/src/views/submissions/`
> 后端路径: `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java`

---

## 总体结论

**对齐状态: 整体良好，存在 4 处轻微颗粒度差异。**

所有 7 个 API 端点前后端完全对应，DTO 字段覆盖率 > 95%。差异集中在类型枚举值、日期格式和未使用字段上，不影响功能运行。

---

## API 端点对齐表

| 操作 | 前端 API 路径 | 后端 Controller 方法 | 状态 |
|---|---|---|---|
| 获取列表 | `GET /admin/submissions` | `AdminSubmissionController.getSubmissions` | ✅ 对齐 |
| 获取详情 | `GET /admin/submissions/{id}` | `AdminSubmissionController.getSubmission` | ✅ 对齐 |
| 获取统计 | `GET /admin/submissions/statistics` | `AdminSubmissionController.getStatistics` | ✅ 对齐 |
| 获取状态选项 | `GET /admin/submissions/statuses` | `AdminSubmissionController.getStatuses` | ✅ 对齐 |
| 获取语言选项 | `GET /admin/submissions/languages` | `AdminSubmissionController.getLanguages` | ✅ 对齐 |
| 单条重判 | `POST /admin/submissions/{id}/rejudge` | `AdminSubmissionController.rejudge` | ✅ 对齐 |
| 批量重判 | `POST /admin/submissions/batch-rejudge` | `AdminSubmissionController.batchRejudge` | ✅ 对齐 |

---

## DTO 字段对齐详情

### 1. 查询参数 (`SubmissionQueryParams` / `AdminSubmissionQueryDTO`)

| 字段 | 前端类型 | 后端类型 | 状态 | 说明 |
|---|---|---|---|---|
| `page` | `number` | `Integer` | ✅ | 1-based 分页 |
| `limit` | `number` | `Integer` | ✅ | 后端默认 10，上限 100 |
| `userId` | `string` | `String` | ✅ | |
| `problemId` | `number` | `Long` | ✅ | |
| `status` | `string` | `String` | ✅ | |
| `language` | `string` | `String` | ✅ | |
| `search` | `string` | `String` | ✅ | 支持用户名/题目标题模糊搜索 |
| `startDate` | `string` | `LocalDateTime` | ⚠️ | 类型不匹配，依赖 Jackson 自动解析 |
| `endDate` | `string` | `LocalDateTime` | ⚠️ | 类型不匹配，依赖 Jackson 自动解析 |
| `sortBy` | `'created_at' \| 'runtime' \| 'memory' \| 'status'` | `String` | ⚠️ | 值不一致，见差异 #1 |
| `sortOrder` | `'asc' \| 'desc'` | `String` | ✅ | |

### 2. 列表项 (`SubmissionListItem` / `AdminSubmissionVO` 列表视图)

| 字段 | 前端类型 | 后端类型 | UI 使用 | 状态 |
|---|---|---|---|---|
| `id` | `string` | `String` | 是（截断显示） | ✅ |
| `problemId` | `number` | `Long` | 否（内部使用） | ✅ |
| `problemTitle` | `string` | `String` | 是 | ✅ |
| `problemSlug` | `string` | `String` | 是（副标题） | ✅ |
| `userId` | `string` | `String` | 否（内部使用） | ✅ |
| `username` | `string` | `String` | 是 | ✅ |
| `language` | `string` | `String` | 是 | ✅ |
| `status` | `string` | `String` | 是（带颜色标签） | ✅ |
| `runtime` | `number \| null` | `Integer` | 是 | ✅ |
| `memory` | `number \| null` | `Double` | 是 | ✅ |
| `createdAt` | `string` | `LocalDateTime` | 是（相对时间） | ✅ |
| `codeLength` | `number \| null` | `Integer` | **否** | ⚠️ 冗余字段，见差异 #3 |

### 3. 详情项 (`SubmissionDetail` / `AdminSubmissionVO` 详情视图)

| 字段 | 前端类型 | 后端类型 | UI 使用 | 状态 |
|---|---|---|---|---|
| `code` | `string \| null` | `String` | 是（代码块展示） | ✅ |
| `notes` | `string \| null` | `String` | 是（条件展示） | ✅ |
| `runtimePercentile` | `number \| null` | `Double` | 否 | ✅ |
| `memoryPercentile` | `number \| null` | `Double` | 否 | ✅ |
| `testDetails` | `unknown` | `Object` | 否 | ⚠️ 类型过于宽泛，见差异 #4 |
| `memoryDistBinsMb` | `unknown` | `Object` | 否 | ⚠️ 类型过于宽泛，见差异 #4 |
| `runtimeDistBinsMs` | `unknown` | `Object` | 否 | ⚠️ 类型过于宽泛，见差异 #4 |

### 4. 分页结构 (`PageResult`)

前后端 `PageResult<T>` 字段完全一致：

```
items: T[]
total: number
page: number
pageSize: number
totalPages: number
```

**状态: ✅ 完全对齐**

### 5. 统计数据 (`SubmissionStatistics`)

| 字段 | 前端类型 | 后端类型 | 状态 |
|---|---|---|---|
| `total` | `number` | `Long` | ✅ |
| `byStatus` | `{ status: string; count: number }[]` | `List<StatusCount>` | ✅ |
| `byLanguage` | `{ language: string; count: number }[]` | `List<LanguageCount>` | ✅ |
| `last24h` | `number` | `Long` | ✅ |
| `pending` | `number` | `Long` | ✅ |

### 6. 状态选项 (`StatusOption`)

前后端字段完全一致：`key`, `label`, `category`。**状态: ✅ 完全对齐**

### 7. 重判相关 DTO

`RejudgeResult`, `BatchRejudgeResponse`, `RejudgeRequest`, `BatchRejudgeRequest` 前后端字段完全对应。**状态: ✅ 完全对齐**

---

## 颗粒度差异清单

### 差异 #1: `sortBy` 枚举值不一致（低风险）

- **前端位置**: `management/src/api/admin/submissions.ts:13`
- **前端定义**: `sortBy?: 'created_at' | 'runtime' | 'memory' | 'status'`
- **后端位置**: `AdminSubmissionServiceImpl.java:109-116`
- **后端期望**: `sortBy` 值为 `"createdAt"`, `"runtime"`, `"memory"`, `"status"`
- **影响**: 前端传递 `created_at` 时，后端 `switch` 语句落入 `default` 分支，仍按 `createdAt` 排序，功能正常但语义不统一。
- **修复建议**: 前端将 `'created_at'` 改为 `'createdAt'`。

### 差异 #2: 日期字段类型不匹配（低风险）

- **前端位置**: `management/src/api/admin/submissions.ts:10-11`
- **前端类型**: `startDate?: string`, `endDate?: string`
- **后端位置**: `AdminSubmissionQueryDTO.java:32-35`
- **后端类型**: `LocalDateTime startDate`, `LocalDateTime endDate`
- **影响**: 依赖 Jackson 自动将 ISO 字符串解析为 `LocalDateTime`，当前未明确日期格式约定。
- **修复建议**: 前端添加 JSDoc 注释说明格式（如 `@format ISO-8601`），或统一使用 `YYYY-MM-DDTHH:mm:ss`。

### 差异 #3: 前端存在未使用字段（冗余）

- **前端位置**: `management/src/api/admin/submissions.ts:36`
- **字段**: `codeLength: number | null`
- **后端位置**: `AdminSubmissionServiceImpl.java:389` / `AdminSubmissionVO.java:52`
- **情况**: 后端在列表查询中计算并返回 `codeLength`，但 `columns.ts` 表格列定义中未包含该列，详情 Dialog 也未展示。
- **修复建议**: 二选一：(a) 在表格中添加"代码长度"列；(b) 从 `SubmissionListItem` 类型中移除该字段以减少维护负担。

### 差异 #4: 详情 JSON 字段类型过于宽泛（可优化）

- **前端位置**: `management/src/api/admin/submissions.ts:49-51`
- **前端类型**: `testDetails: unknown`, `memoryDistBinsMb: unknown`, `runtimeDistBinsMs: unknown`
- **后端位置**: `AdminSubmissionVO.java:68-75`
- **后端类型**: `Object`
- **影响**: 当前详情 Dialog 仅展示 `code` 和 `notes`，未使用这些字段。若未来需要展示测试详情或分布图，缺乏类型安全。
- **修复建议**: 若短期内不使用，保持 `unknown` 即可；若计划展示，建议前后端协商定义具体结构（如 `TestCaseDetail[]`、分布桶数组等）。

---

## 相关文件索引

### 前端
- `management/src/views/submissions/SubmissionsView.vue` — 主页面
- `management/src/views/submissions/columns.ts` — 表格列定义
- `management/src/api/admin/submissions.ts` — API 封装 + 类型定义
- `management/src/stores/admin/submissions.ts` — Pinia store

### 后端
- `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java` — Admin 控制器
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java` — 服务实现
- `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSubmissionVO.java` — 列表/详情 VO
- `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSubmissionQueryDTO.java` — 查询参数 DTO
- `backend-spring/src/main/java/com/ulticode/modules/admin/dto/SubmissionStatistics.java` — 统计 DTO
- `backend-spring/src/main/java/com/ulticode/modules/admin/dto/StatusOption.java` — 状态选项 DTO
- `backend-spring/src/main/java/com/ulticode/modules/admin/dto/RejudgeResult.java` — 重判结果 DTO
- `backend-spring/src/main/java/com/ulticode/modules/admin/dto/BatchRejudgeResponse.java` — 批量重判响应 DTO
- `backend-spring/src/main/java/com/ulticode/common/response/PageResult.java` — 分页响应结构
