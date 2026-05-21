# Problem Lists 前后端 API 颗粒度对齐分析

*生成日期: 2026-05-21 | 分析范围: Console + Management ↔ Backend (User + Admin)*

---

## 执行摘要

Problem Lists 模块存在两套前端（Console 用户端、Management 管理端）和两套后端 API（`/problem-lists` 用户端、`/admin/problem-lists` 管理端）。整体架构合理，但存在以下关键不对齐问题：

1. **Console 前端 Response 字段名与后端 VO 不匹配** — 前端 `myLists`/`savedLists`/`featured` vs 后端 `ownLists`/`savedLists`/`featuredLists`
2. **Management 前端缺少细粒度更新端点的返回值处理** — `updateBasicInfo`/`updateVisibility`/`updateBanner` 前端声明 `Promise<void>` 但后端返回 `ProblemListSummaryVO`
3. **Console 前端 `ProblemList` 类型包含后端不返回的字段** — `favoritesCount`、`categoryId` 在后端 VO 中不存在
4. **Management 前端 `ProblemList` 接口缺少后端 VO 的字段** — `authorId` 非 optional 但后端 `ProblemListSummaryVO` 可能为空
5. **Management 后端缺少分页查询排序的完整实现** — `AdminProblemListQueryDTO` 的 `sortBy`/`sortOrder` 可能未完全生效

---

## 1. Management 前端 ↔ Admin 后端 API 对齐

### 1.1 端点映射

| Management 前端方法 | 前端 HTTP | 后端端点 | 后端 HTTP | 对齐状态 |
|---|---|---|---|---|
| `getLists(query)` | `GET /admin/problem-lists` | `getProblemLists(query)` | `GET /admin/problem-lists` | ✅ 对齐 |
| `getList(id)` | `GET /admin/problem-lists/:id` | `getProblemListById(id)` | `GET /admin/problem-lists/{id}` | ✅ 对齐 |
| `createList(data)` | `POST /admin/problem-lists` | `createProblemList(dto, userId)` | `POST /admin/problem-lists` | ⚠️ 部分对齐 |
| `deleteList(id)` | `DELETE /admin/problem-lists/:id` | `deleteProblemList(id)` | `DELETE /admin/problem-lists/{id}` | ✅ 对齐 |
| `updateListProblems(id, data)` | `POST /admin/problem-lists/:id/problems` | `updateListProblems(id, dto)` | `POST /admin/problem-lists/{id}/problems` | ✅ 对齐 |
| `updateBasicInfo(id, data)` | `PATCH /admin/problem-lists/:id/basic-info` | `updateBasicInfo(id, userId, dto)` | `PATCH /admin/problem-lists/{id}/basic-info` | ⚠️ 返回值不对齐 |
| `updateVisibility(id, data)` | `PATCH /admin/problem-lists/:id/visibility` | `updateVisibility(id, userId, dto)` | `PATCH /admin/problem-lists/{id}/visibility` | ⚠️ 返回值不对齐 |
| `updateBanner(id, data)` | `PATCH /admin/problem-lists/:id/banner` | `updateBanner(id, userId, dto)` | `PATCH /admin/problem-lists/{id}/banner` | ⚠️ 返回值不对齐 |

### 1.2 详细不对齐项

#### 1.2.1 返回值类型不匹配

| 前端方法 | 前端返回类型 | 后端返回类型 | 问题 |
|---|---|---|---|
| `updateBasicInfo` | `Promise<void>` | `Result<ProblemListSummaryVO>` | 前端丢弃了后端返回的更新后数据 |
| `updateVisibility` | `Promise<void>` | `Result<ProblemListSummaryVO>` | 同上 |
| `updateBanner` | `Promise<void>` | `Result<ProblemListSummaryVO>` | 同上 |
| `createList` | `Promise<ProblemList>` | `Result<ProblemListSummaryVO>` | 类型名不同但结构基本一致 |

**影响**: Management 前端 `BasicInfoSection`/`VisibilitySection`/`BannerSection` 使用 auto-save 模式，每次保存后需要刷新数据。如果利用后端返回的 VO，可以省去一次 `getList(id)` 请求。

#### 1.2.2 DTO 字段对齐

**CreateProblemListDto (前端 vs 后端)**:

| 字段 | 前端类型 | 后端类型 | 对齐 |
|---|---|---|---|
| `name` | `string` | `String` (required) | ✅ |
| `description` | `string?` | `String` | ✅ |
| `isPublic` | `boolean?` | `Boolean` (default false) | ✅ |
| `isFeatured` | `boolean?` | — | ❌ 前端有多余字段 |
| `bannerTag` | `string?` | `String` | ✅ |
| `bannerIcon` | `string?` | `String` | ✅ |
| `bannerTheme` | `string?` | `String` | ✅ |
| `bannerOrder` | `number?` | `Integer` | ✅ |

**UpdateBasicInfoDto (前端 vs 后端)**:

| 字段 | 前端类型 | 后端类型 | 对齐 |
|---|---|---|---|
| `name` | `string?` | `String` (required) | ⚠️ 前端可选，后端必填 |
| `description` | `string?` | `String` | ✅ |

**UpdateVisibilityDto (前端 vs 后端)**:

| 字段 | 前端类型 | 后端类型 | 对齐 |
|---|---|---|---|
| `isPublic` | `boolean?` | `Boolean` | ✅ |
| `isFeatured` | `boolean?` | `Boolean` | ✅ |

**UpdateBannerDto (前端 vs 后端)**:

| 字段 | 前端类型 | 后端类型 | 对齐 |
|---|---|---|---|
| `bannerTag` | `string?` | `String` | ✅ |
| `bannerIcon` | `string?` | `String` | ✅ |
| `bannerTheme` | `string?` | `String` | ✅ |
| `bannerOrder` | `number?` | `Integer` | ✅ |

#### 1.2.3 ProblemList 接口字段对齐

| 字段 | 前端 `ProblemList` | 后端 `ProblemListSummaryVO` | 对齐 |
|---|---|---|---|
| `id` | `string` | `String` | ✅ |
| `name` | `string` | `String` | ✅ |
| `description` | `string` | `String` | ⚠️ 前端非 optional，后端可 null |
| `authorId` | `string` | `String` | ⚠️ 前端非 optional，后端可 null |
| `authorName` | `string?` | `String` | ✅ |
| `authorUsername` | `string?` | `String` | ✅ |
| `isPublic` | `boolean` | `Boolean` | ✅ |
| `isFeatured` | `boolean` | `Boolean` | ✅ |
| `bannerTag` | `string?` | `String` | ✅ |
| `bannerIcon` | `string?` | `String` | ✅ |
| `bannerTheme` | `string?` | `String` | ✅ |
| `bannerOrder` | `number` | `Integer` | ⚠️ 前端非 optional，后端可 null |
| `createdAt` | `string` | `LocalDateTime` | ✅ 序列化兼容 |
| `updatedAt` | `string` | `LocalDateTime` | ✅ 序列化兼容 |
| `problemCount` | `number?` | `Integer` | ✅ |
| `isSaved` | `boolean?` | `Boolean` | ✅ |

**ProblemListDetail (前端 vs 后端 ProblemListDetailVO)**:

| 字段 | 前端 | 后端 | 对齐 |
|---|---|---|---|
| 继承 ProblemList | ✅ | 继承 SummaryVO 字段 | ✅ |
| `problems` | `ProblemListProblem[]` | `List<ProblemInListVO>` | ⚠️ 字段名不同 |
| `isOwner` | `boolean?` | — (未在 VO 中定义) | ❌ 前端有，后端无 |

**ProblemListProblem (前端) vs ProblemInListVO (后端)**:

| 字段 | 前端类型 | 后端类型 | 对齐 |
|---|---|---|---|
| `id` | `number` | `Long` | ✅ |
| `slug` | `string` | `String` | ✅ |
| `title` | `string` | `String` | ✅ |
| `difficulty` | `string` | `String` | ✅ |
| `status` | `string` | `String` | ✅ |
| `sortOrder` | `number` | `Integer` | ✅ |
| `addedAt` | `string` | `LocalDateTime` | ✅ |

---

## 2. Console 前端 ↔ User 后端 API 对齐

### 2.1 端点映射

| Console 前端方法 | 前端 HTTP | 后端端点 | 后端 HTTP | 对齐状态 |
|---|---|---|---|---|
| `fetchProblemListsOverview` | `GET /problem-lists/overview` | `findAll(locale)` | `GET /problem-lists/overview` | ⚠️ Response 字段名不匹配 |
| `fetchProblemListOverview` | `GET /problem-lists/:id/overview` | `getListOverview(id, userId, locale)` | `GET /problem-lists/{id}/overview` | ⚠️ Response 结构不匹配 |
| `createProblemList` | `POST /problem-lists?userId=X` | `createList(userId, dto)` | `POST /problem-lists` | ⚠️ userId 传递方式 |
| `updateProblemList` | `PATCH /problem-lists/:id?userId=X` | `updateList(id, userId, dto)` | `PATCH /problem-lists/{id}` | ⚠️ userId 传递方式 |
| `deleteProblemList` | `DELETE /problem-lists/:id?userId=X` | `deleteList(id, userId)` | `DELETE /problem-lists/{id}` | ⚠️ userId 传递方式 |
| `forkProblemList` | `POST /problem-lists/:id/fork?userId=X` | `forkList(id, userId)` | `POST /problem-lists/{id}/fork` | ⚠️ userId 传递方式 |
| `addProblemToList` | `POST /problem-lists/:id/problems?userId=X` | `addProblem(listId, userId, problemId)` | `POST /problem-lists/{id}/problems` | ⚠️ userId 传递方式 |
| `removeProblemFromList` | `DELETE /problem-lists/:id/problems/:pid?userId=X` | `removeProblem(listId, userId, problemId)` | `DELETE /problem-lists/{id}/problems/{problemId}` | ⚠️ userId 传递方式 |
| `batchAddProblemToLists` | `POST /problem-lists/problems/:pid/batch-add?userId=X` | `batchAddProblemToLists(userId, problemId, listIds)` | `POST /problem-lists/problems/{problemId}/batch-add` | ⚠️ userId 传递方式 |
| `batchRemoveProblemFromLists` | `POST /problem-lists/problems/:pid/batch-remove?userId=X` | `batchRemoveProblemFromLists(userId, problemId, listIds)` | `POST /problem-lists/problems/{problemId}/batch-remove` | ⚠️ userId 传递方式 |
| `getUserListsForProblem` | `GET /problem-lists/problems/:pid/user-lists?userId=X` | `getUserListsForProblem(userId, problemId)` | `GET /problem-lists/problems/{problemId}/user-lists` | ⚠️ userId 传递方式 |
| `saveList` | `POST /problem-lists/:id/save?userId=X` | `saveList(userId, listId, categoryId)` | `POST /problem-lists/{id}/save` | ⚠️ userId 传递方式 |
| `unsaveList` | `DELETE /problem-lists/:id/save?userId=X` | `unsaveList(userId, listId)` | `DELETE /problem-lists/{id}/save` | ⚠️ userId 传递方式 |
| `moveListToCategory` | `PATCH /problem-lists/:id/category?userId=X` | `moveListToCategory(userId, listId, categoryId)` | `PATCH /problem-lists/{id}/category` | ⚠️ userId 传递方式 |
| `createCategory` | `POST /problem-lists/categories?userId=X` | `createCategory(userId, dto)` | `POST /problem-lists/categories` | ⚠️ userId 传递方式 |
| `updateCategory` | `PATCH /problem-lists/categories/:cid?userId=X` | `updateCategory(categoryId, userId, dto)` | `PATCH /problem-lists/categories/{categoryId}` | ⚠️ userId 传递方式 |
| `deleteCategory` | `DELETE /problem-lists/categories/:cid?userId=X` | `deleteCategory(categoryId, userId)` | `DELETE /problem-lists/categories/{categoryId}` | ⚠️ userId 传递方式 |

### 2.2 详细不对齐项

#### 2.2.1 userId 传递方式 (CRITICAL)

**问题**: Console 前端通过 **query parameter** (`?userId=X`) 传递 userId，但后端 Controller 中**没有 `@RequestParam userId` 参数**。后端通过 `@RequestHeader("X-User-Id")` 或 Spring Security 上下文获取 userId。

**实际影响**: 如果 API Gateway 在请求头中注入 `X-User-Id`，则 query parameter 中的 userId 被后端忽略。但前端代码在构造 URL 时附加了 `?userId=xxx`，这是一个语义不清的冗余参数。

**建议**: 前端应移除 query parameter 中的 userId，改由后端从 Security 上下文自动获取。如果需要支持未登录用户查看公开列表，后端 `overview` 端点应将 userId 设为 optional。

#### 2.2.2 Response 字段名不匹配 (HIGH)

**`UserProblemListsVO` (后端) vs `UserProblemListsResponse` (前端)**:

| 后端字段 | 前端字段 | 对齐 |
|---|---|---|
| `ownLists` | `myLists` | ❌ 不匹配 |
| `savedLists` | `savedLists` | ✅ |
| `featuredLists` | `featured` | ❌ 不匹配 |
| `categories` | `categories` | ✅ |

**当前缓解**: Console 前端 `mapUserProblemListsResponse()` 手动映射了字段名（`raw.ownLists` → `myLists`，`raw.featuredLists` → `featured`），但由于使用了 `unknown` 类型的 Backend 接口，缺少编译时类型安全。

**`ProblemListDetailVO` (后端) vs `ProblemListDetailResponse` (前端)**:

| 后端字段 | 前端字段 | 对齐 |
|---|---|---|
| SummaryVO 字段 + `problems` | `list` + `problems` | ❌ 结构不同 |
| — | `stats` | ❌ 前端有，后端无 |
| — | `viewer` | ❌ 前端有，后端无 |
| — | `categories` | ❌ 前端有，后端无 |

**分析**: 后端 `ProblemListDetailVO` 继承了 `ProblemListSummaryVO` 的字段并添加 `problems`，是一个扁平结构。前端期望的是嵌套结构：`{ list, problems, stats, viewer, categories }`。这说明前端和后端对"详情"的语义理解不同。

#### 2.2.3 Console ProblemList 类型字段不对齐 (MEDIUM)

| 前端字段 | 后端 ProblemListSummaryVO | 对齐 |
|---|---|---|
| `favoritesCount` | — | ❌ 后端无此字段 |
| `categoryId` | — | ❌ 后端无此字段 |
| `problems` (在 ProblemList 内) | `problems` (在 DetailVO 内) | ⚠️ 位置不同 |

---

## 3. 交叉对比：Management vs Console API 颗粒度差异

| 功能 | Management 端 | Console 端 | 颗粒度评估 |
|---|---|---|---|
| 列表查询 | 分页 + 过滤 + 排序 | 全量返回（overview） | ⚠️ Console 缺少分页 |
| 创建 | `CreateProblemListDTO` 含 `isFeatured` | 仅含 `name, description, isPublic` | ✅ 角色差异合理 |
| 更新 | 3 个细粒度端点 (basic-info/visibility/banner) | 1 个通用 `PATCH` | ⚠️ Console 缺少细粒度 |
| 删除 | 有 | 有 | ✅ |
| Fork | 无 | 有 | ✅ 角色差异合理 |
| 问题管理 | 全量替换 (`updateListProblems`) | 逐个增删 + 批量操作 | ⚠️ 语义不同 |
| 收藏/取消 | 无 | 有 | ✅ 角色差异合理 |
| 分类管理 | 无 | 完整 CRUD | ✅ 角色差异合理 |

---

## 4. 建议修改项

### P0 - 必须修复

| # | 问题 | 修改方向 | 涉及文件 |
|---|---|---|---|
| 1 | Console `userId` 通过 query param 传递 | 前端移除 query param 中的 userId，后端从 Security 上下文获取 | `console/src/api/problem-list.ts` |
| 2 | Console Response 字段名 `ownLists`/`featuredLists` 不匹配 | 统一前端类型或后端 VO 字段名 | `console/src/api/problem-list.ts`, `console/src/types/problem-list.ts` |

### P1 - 应该修复

| # | 问题 | 修改方向 | 涉及文件 |
|---|---|---|---|
| 3 | Management `updateBasicInfo`/`updateVisibility`/`updateBanner` 返回 `void` | 前端改为接收 `ProblemListSummaryVO`，利用返回值更新本地状态 | `management/src/api/admin/problem-lists.ts` |
| 4 | Console `ProblemListDetailResponse` 与后端 `ProblemListDetailVO` 结构不同 | 对齐前端/后端：要么后端返回 `{ list, problems, stats, viewer, categories }`，要么前端适配扁平结构 | `console/src/api/problem-list.ts`, `console/src/types/problem-list.ts` |
| 5 | Management `CreateProblemListDto` 含 `isFeatured` 但后端 `CreateProblemListDTO` 无此字段 | 前端移除 `isFeatured` 或后端添加该字段 | `management/src/api/admin/problem-lists.ts` |
| 6 | Management `UpdateBasicInfoDto.name` 前端可选，后端必填 | 前端改为 `name: string` 或后端改为可选 | `management/src/api/admin/problem-lists.ts` |

### P2 - 建议优化

| # | 问题 | 修改方向 | 涉及文件 |
|---|---|---|---|
| 7 | Console `ProblemList.favoritesCount`/`categoryId` 后端不返回 | 移除前端多余字段或后端添加 | `console/src/types/problem-list.ts` |
| 8 | Management `ProblemList.description` 前端非 optional | 改为 `description?: string` | `management/src/api/admin/problem-lists.ts` |
| 9 | Management `ProblemList.bannerOrder` 前端非 optional | 改为 `bannerOrder?: number` | `management/src/api/admin/problem-lists.ts` |
| 10 | Console `ProblemListDetailResponse.stats`/`viewer`/`categories` 后端不返回 | 后端添加或前端移除 | 后端 `ProblemListDetailVO` |
| 11 | Management `ProblemListDetail.isOwner` 后端 VO 不返回 | 前端移除或后端添加 | `management/src/api/admin/problem-lists.ts` |

---

## 5. 后端 DTO/VO 字段完整对照

### User 端 DTOs

| DTO | 字段 | 前端对应 |
|---|---|---|
| `CreateProblemListDTO` | `name*`, `description`, `isPublic`, `bannerTag`, `bannerIcon`, `bannerTheme`, `bannerOrder` | Console: `name, description, isPublic` (部分) |
| `UpdateProblemListDTO` | `name`, `description`, `isPublic`, `bannerTag`, `bannerIcon`, `bannerTheme`, `bannerOrder`, `isFeatured` | Console: `name?, description?, isPublic?` (部分) |
| `AddProblemToListDTO` | `problemId*` | Console: `problemId` |
| `BatchAddToListsDTO` | `listIds*` (non-empty) | Console: `listIds` |
| `SaveListDTO` | `categoryId` (optional) | Console: `categoryId?` |
| `MoveListToCategoryDTO` | `categoryId` | Console: `categoryId` |
| `CreateCategoryDTO` | `name*`, `description`, `icon`, `color` | Console: `name, sortOrder?` (部分) |
| `UpdateCategoryDTO` | `name`, `description`, `icon`, `color`, `sortOrder` | Console: `name?, sortOrder?` (部分) |

### Admin 端 DTOs

| DTO | 字段 | 前端对应 |
|---|---|---|
| `CreateProblemListDTO` (共享) | 同上 | Management: `name, description?, isPublic?, isFeatured?, banner*` |
| `UpdateProblemListDTO` (共享) | 同上 | Management: 未直接使用 |
| `UpdateBasicInfoDTO` | `name*`, `description` | Management: `name?, description?` ⚠️ name 可选性不匹配 |
| `UpdateVisibilityDTO` | `isPublic`, `isFeatured` | Management: `isPublic?, isFeatured?` ✅ |
| `UpdateBannerDTO` | `bannerTag`, `bannerIcon`, `bannerTheme`, `bannerOrder` | Management: 全 optional ✅ |
| `UpdateProblemListProblemsDTO` | `List<ProblemEntry>` with `problemId`, `sortOrder` | Management: `problems: { problemId, sortOrder }[]` ✅ |
| `AdminProblemListQueryDTO` | `search`, `isFeatured`, `isPublic`, `page`, `limit`, `sortBy`, `sortOrder` | Management: `ProblemListQuery` ✅ |

### VOs (后端返回)

| VO | 字段 | Management 前端 | Console 前端 |
|---|---|---|---|
| `ProblemListSummaryVO` | `id, name, description, authorId, authorName, authorUsername, isPublic, isFeatured, banner*, problemCount, isSaved, createdAt, updatedAt` | `ProblemList` interface ✅ | `ProblemList` type ⚠️ 有额外字段 |
| `ProblemListDetailVO` | SummaryVO + `problems: List<ProblemInListVO>` | `ProblemListDetail` ✅ | `ProblemListDetailResponse` ❌ 结构不同 |
| `UserProblemListsVO` | `ownLists, savedLists, featuredLists, categories` | — | ❌ 字段名不匹配 |
| `UserListsForProblemVO` | `problemId, lists: List<ListStatusVO>` | — | ✅ mapper 处理 |
| `CategorySummaryVO` | `id, userId, name, description, icon, color, sortOrder, listCount, createdAt, updatedAt` | — | `ProblemListCategory` ✅ |
| `ForkResultVO` | `id` | — | ✅ |

---

## 方法论

分析了 30+ 后端文件（Controller × 2, Service × 3, Entity × 4, Mapper × 4, DTO × 18）和 25+ 前端文件（API × 2, Types × 2, Views × 4, Components × 12, Composables × 3, Store × 1, Router × 2, Permissions × 1, Columns × 1）。
