# Problem-Lists 全模块前后端颗粒度对齐分析报告

> 生成日期: 2026-05-21
> 分析范围: `http://localhost:9003/problem-lists` 及所有相关页面
> 涉及模块: console (用户前端) + management (管理后台) + backend-spring (Spring Boot)
> 状态: 待修复

---

## 一、总体架构概览

### 1.1 三端 API 映射总览

#### Console 前端 (用户侧) → 后端 `/problem-lists`

| 前端 API 函数 | HTTP | 后端端点 | 对齐状态 |
|---|---|---|---|
| `fetchProblemListsOverview` | GET | `/problem-lists/overview` | ✅ 对齐 |
| `fetchProblemListOverview` | GET | `/problem-lists/{id}/overview` | ⚠️ 字段不对齐 |
| `createProblemList` | POST | `/problem-lists` | ⚠️ DTO 不对齐 |
| `updateProblemList` | PATCH | `/problem-lists/{id}` | ⚠️ DTO 不对齐 |
| `deleteProblemList` | DELETE | `/problem-lists/{id}` | ✅ 对齐 |
| `forkProblemList` | POST | `/problem-lists/{id}/fork` | ⚠️ 返回类型不对齐 |
| `addProblemToList` | POST | `/problem-lists/{id}/problems` | ✅ 对齐 |
| `removeProblemFromList` | DELETE | `/problem-lists/{id}/problems/{problemId}` | ✅ 对齐 |
| `batchAddProblemToLists` | POST | `/problem-lists/problems/{problemId}/batch-add` | ✅ 对齐 |
| `batchRemoveProblemFromLists` | POST | `/problem-lists/problems/{problemId}/batch-remove` | ✅ 对齐 |
| `getUserListsForProblem` | GET | `/problem-lists/problems/{problemId}/user-lists` | ⚠️ 字段不对齐 |
| `saveList` | POST | `/problem-lists/{id}/save` | ✅ 对齐 |
| `unsaveList` | DELETE | `/problem-lists/{id}/save` | ✅ 对齐 |
| `moveListToCategory` | PATCH | `/problem-lists/{id}/category` | ✅ 对齐 |
| `createCategory` | POST | `/problem-lists/categories` | ⚠️ DTO 不对齐 |
| `updateCategory` | PATCH | `/problem-lists/categories/{categoryId}` | ⚠️ DTO 不对齐 |
| `deleteCategory` | DELETE | `/problem-lists/categories/{categoryId}` | ✅ 对齐 |

#### Management 前端 (管理侧) → 后端 `/admin/problem-lists`

| 前端 API 函数 | HTTP | 后端端点 | 对齐状态 |
|---|---|---|---|
| `getLists` | GET | `/admin/problem-lists` | ✅ 对齐 |
| `getList` | GET | `/admin/problem-lists/{id}` | ✅ 对齐 |
| `createList` | POST | `/admin/problem-lists` | ⚠️ DTO 不对齐 |
| `updateList` | PATCH | `/admin/problem-lists/{id}` | ✅ 对齐（但冗余） |
| `deleteList` | DELETE | `/admin/problem-lists/{id}` | ✅ 对齐 |
| `updateListProblems` | POST | `/admin/problem-lists/{id}/problems` | ✅ 对齐 |
| `updateBasicInfo` | PATCH | `/admin/problem-lists/{id}/basic-info` | ✅ 对齐 |
| `updateVisibility` | PATCH | `/admin/problem-lists/{id}/visibility` | ✅ 对齐 |
| `updateBanner` | PATCH | `/admin/problem-lists/{id}/banner` | ✅ 对齐 |

---

## 二、类型/字段颗粒度对齐分析

### 2.1 ProblemListSummaryVO（后端） vs 前端类型

#### Console 前端 `ProblemList` vs 后端 `ProblemListSummaryVO`

| 后端字段 | 后端类型 | Console 前端字段 | Console 类型 | 状态 |
|---|---|---|---|---|
| `id` | String | `id` | string | ✅ |
| `name` | String | `name` | string | ✅ |
| `description` | String | `description` | string? | ✅ |
| `authorId` | String | `authorId` | string? | ✅ |
| `authorName` | String | ❌ **缺失** | — | 🔴 |
| `authorUsername` | String | ❌ **缺失** | — | 🔴 |
| `isPublic` | Boolean | `isPublic` | boolean? | ✅ |
| `isFeatured` | Boolean | `isFeatured` | boolean? | ✅ |
| `bannerTag` | String | `bannerTag` | string? | ✅ |
| `bannerIcon` | String | `bannerIcon` | string? | ✅ |
| `bannerTheme` | String | `bannerTheme` | string? | ✅ |
| `bannerOrder` | Integer | `bannerOrder` | number? | ✅ |
| `problemCount` | Integer | `problemCount` | number | ✅ |
| `isSaved` | Boolean | `isSaved` | boolean? | ✅ |
| `createdAt` | LocalDateTime | `createdAt` | string? | ✅ |
| `updatedAt` | LocalDateTime | `updatedAt` | string? | ✅ |
| — | — | `favoritesCount` | number | 🔴 后端无此字段 |
| — | — | `categoryId` | string? | 🔴 后端无此字段 |
| — | — | `problems` | Problem[]? | 🔴 仅在 DetailVO 中有 |

#### Management 前端 `ProblemList` vs 后端 `ProblemListSummaryVO`

| 后端字段 | Management 前端字段 | 状态 |
|---|---|---|
| `authorName` | `authorName?` | ✅ 已定义但未渲染 |
| `authorUsername` | `authorUsername?` | ✅ 已定义但未渲染 |
| `isSaved` | `isSaved?` | ✅ 已定义但未渲染 |

### 2.2 ProblemListDetailVO（后端） vs 前端类型

| 后端字段 | Console 前端字段 | Management 前端字段 | 状态 |
|---|---|---|---|
| `isOwner` | ❌ **缺失** | `isOwner?` | 🔴 Console 缺失 |
| `authorName` | ❌ **缺失** | — (继承自 ProblemList) | 🔴 Console 缺失 |
| `authorUsername` | ❌ **缺失** | — (继承自 ProblemList) | 🔴 Console 缺失 |
| `problems` | `Problem[]` (映射后) | `ProblemListProblem[]` | ⚠️ 类型名不同 |

### 2.3 ProblemInListVO（后端） vs 前端类型

| 后端字段 | Console `Problem` | Management `ProblemListProblem` | 状态 |
|---|---|---|---|
| `id` (Long) | `id` (number) | `id` (number) | ✅ |
| `slug` | `slug` | `slug` | ✅ |
| `title` | `title` | `title` | ✅ |
| `difficulty` | `difficulty` | `difficulty` | ✅ |
| `status` | `status?` | `status` | ✅ |
| `sortOrder` | ❌ **缺失** | `sortOrder` | 🔴 Console 缺失 |
| `addedAt` | ❌ **缺失** | `addedAt` | 🔴 Console 缺失 |

### 2.4 UserListsForProblemVO（后端） vs Console 前端

| 后端 `ListStatusVO` | Console `ProblemListWithStatus` | 状态 |
|---|---|---|
| `id` | `id` | ✅ |
| `name` | `name` | ✅ |
| `hasProblem` | `containsProblem` | ⚠️ 字段名不同 |
| — | `problemCount` | 🔴 后端无此字段 |
| — | `favoritesCount` | 🔴 后端无此字段 |
| — | `canEdit` | 🔴 后端无此字段 |

### 2.5 CategorySummaryVO（后端） vs Console 前端

| 后端 `CategorySummaryVO` | Console `ProblemListCategory` | 状态 |
|---|---|---|
| `id` | `id` | ✅ |
| `name` | `name` | ✅ |
| `sortOrder` | `sortOrder` | ✅ |
| `listCount` | ❌ **缺失** | 🔴 Console 缺失 |
| `description` | ❌ **缺失** | 🔴 Console 缺失 |
| `icon` | ❌ **缺失** | 🔴 Console 缺失 |
| `color` | ❌ **缺失** | 🔴 Console 缺失 |
| `lists` (关联查询) | `lists: ProblemList[]` | ⚠️ 后端不直接返回 |

---

## 三、DTO 字段校验不一致

### 3.1 description 长度限制三处不一致

| DTO | `@Size(max)` | 使用场景 |
|---|---|---|
| `CreateProblemListDTO` | **1000** | 创建题单 |
| `UpdateProblemListDTO` | **500** | 通用更新 |
| `UpdateBasicInfoDTO` | **500** | 细粒度更新基本信息 |

**问题**: 创建时允许 1000 字符，但更新时只允许 500 字符。用户创建一个 800 字符描述的题单后，无法通过编辑保存。

### 3.2 Console 前端 `createProblemList` 发送后端不认识的字段

| 前端发送字段 | 后端 `CreateProblemListDTO` | 状态 |
|---|---|---|
| `name` | ✅ | ✅ |
| `description` | ✅ | ✅ |
| `isPublic` | ✅ | ✅ |
| — | `bannerTag` | ⚠️ 前端不发送 |
| — | `bannerIcon` | ⚠️ 前端不发送 |
| — | `bannerTheme` | ⚠️ 前端不发送 |
| — | `bannerOrder` | ⚠️ 前端不发送 |

**注意**: Console 创建题单时只发送 `name, description, isPublic`，不发送 banner 相关字段。这是合理的（用户创建时不需要 banner），但 Management 前端 `CreateProblemListDto` 包含了 banner 字段和 `isFeatured`。

### 3.3 Console 前端 `updateProblemList` vs 后端 `UpdateProblemListDTO`

| 前端发送字段 | 后端字段 | 状态 |
|---|---|---|
| `name?` | `name` | ✅ |
| `description?` | `description` | ✅ |
| `isPublic?` | `isPublic` | ✅ |
| — | `bannerTag` | ⚠️ 前端不发送 |
| — | `bannerIcon` | ⚠️ 前端不发送 |
| — | `bannerTheme` | ⚠️ 前端不发送 |
| — | `bannerOrder` | ⚠️ 前端不发送 |
| — | `isFeatured` | ⚠️ 前端不发送（用户不能设置 featured） |

### 3.4 Console 前端 `createCategory` vs 后端 `CreateCategoryDTO`

| 前端发送字段 | 后端字段 | 状态 |
|---|---|---|
| `name` | `name` | ✅ |
| `sortOrder?` | `sortOrder` | ✅ |
| — | `description` | 🔴 前端不发送 |
| — | `icon` | 🔴 前端不发送 |
| — | `color` | 🔴 前端不发送 |

### 3.5 Console 前端 `updateCategory` vs 后端 `UpdateCategoryDTO`

| 前端发送字段 | 后端字段 | 状态 |
|---|---|---|
| `name?` | `name` | ✅ |
| `sortOrder?` | `sortOrder` | ✅ |
| — | `description` | 🔴 前端不发送 |
| — | `icon` | 🔴 前端不发送 |
| — | `color` | 🔴 前端不发送 |

### 3.6 fork 返回类型不对齐

| | Console 前端期望 | 后端实际返回 |
|---|---|---|
| `forkProblemList` | `{ id: string }` | `ForkResultVO { newListId: String }` |

**问题**: 前端期望 `id`，后端返回 `newListId`。字段名不匹配。

---

## 四、API 端点颗粒度不对齐

### 4.1 Console 侧：缺少细粒度 PATCH 端点

**现状**: Console 前端只有 `updateProblemList(listId, userId, {name?, description?, isPublic?})` 一个通用 PATCH 调用。

**问题**: 后端 `ProblemListService` 已有 `updateBasicInfo()`、`updateVisibility()` 方法，但 Console Controller 没有暴露对应的细粒度端点。Console 前端也无法利用这些细粒度方法。

**建议**: Console 侧暂不需要细粒度端点，因为用户编辑是一次性保存所有字段，不像 Management 有按区块自动保存的需求。保持现状即可。

### 4.2 Management 侧：冗余的通用 PATCH 端点

**现状**: Management 前端有 `updateList(id, data)` 调用通用 `PATCH /admin/problem-lists/{id}`，同时也有 `updateBasicInfo`、`updateVisibility`、`updateBanner` 三个细粒度调用。

**问题**: `updateList` 在 Management 前端代码中已定义但**从未被任何组件调用**。所有编辑操作都走细粒度端点。通用 PATCH 端点成为死代码。

**建议**: 从 Management API 层移除 `updateList` 方法，或保留作为内部工具但标记为 `@Deprecated`。

---

## 五、字段使用浪费分析

### 5.1 后端返回但前端不使用的字段

| 字段 | 来源 | 未使用位置 |
|---|---|---|
| `authorName` | `ProblemListSummaryVO` | Management 列表页、详情页均未渲染 |
| `authorUsername` | `ProblemListSummaryVO` | Management 列表页、详情页均未渲染 |
| `isSaved` | `ProblemListSummaryVO` | Management 未渲染 |
| `ProblemListProblem.status` | `ProblemListDetailVO` | Management ProblemsManager 未渲染 |
| `ProblemListProblem.addedAt` | `ProblemListDetailVO` | Management ProblemsManager 未渲染 |
| `stats` | `ProblemListDetailResponse` | Console ProblemListView 未渲染（客户端自行计算） |

### 5.2 前端定义但后端不返回的字段

| 字段 | 前端位置 | 后端状态 |
|---|---|---|
| `favoritesCount` | Console `ProblemList` | 🔴 后端 `ProblemListSummaryVO` 无此字段 |
| `categoryId` | Console `ProblemList` | 🔴 后端 `ProblemListSummaryVO` 无此字段（仅在 viewer 中返回） |
| `problems` | Console `ProblemList` | 🔴 仅在 DetailVO 中有，SummaryVO 无 |
| `containsProblem` | Console `ProblemListWithStatus` | 🔴 后端 `ListStatusVO` 用 `hasProblem` |
| `canEdit` | Console `ProblemListWithStatus` | 🔴 后端无此字段 |
| `problemCount` | Console `ProblemListWithStatus` | 🔴 后端 `ListStatusVO` 无此字段 |
| `favoritesCount` | Console `ProblemListWithStatus` | 🔴 后端 `ListStatusVO` 无此字段 |

---

## 六、核心不对齐问题清单

### 🔴 CRITICAL

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| **C1** | `description` 校验长度不一致：创建 1000 vs 更新 500 | `CreateProblemListDTO` vs `UpdateProblemListDTO`/`UpdateBasicInfoDTO` | 用户创建 800 字符描述后无法编辑保存 |
| **C2** | Console `ProblemList` 缺少 `authorName`、`authorUsername` | `console/src/types/problem-list.ts` | 前端无法显示题单作者信息 |
| **C3** | Console `ProblemListDetailResponse` 缺少 `isOwner` | `console/src/types/problem-list.ts` | 前端无法判断当前用户是否为所有者 |
| **C4** | Console `Problem` 类型缺少 `sortOrder`、`addedAt` | `console/src/types/problem.ts` | 前端无法显示题目在题单中的排序和添加时间 |
| **C5** | fork 返回字段名不匹配：前端期望 `id`，后端返回 `newListId` | `console/src/api/problem-list.ts:384` | fork 后无法正确获取新题单 ID，跳转失败 |
| **C6** | `getUserListsForProblem` 返回类型严重不对齐 | `console/src/api/problem-list.ts:441-458` | 前端期望 `containsProblem`/`canEdit`/`problemCount`/`favoritesCount`，后端只返回 `hasProblem` |

### 🟡 HIGH

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| **H1** | Console `ProblemList` 有 `favoritesCount` 但后端 `ProblemListSummaryVO` 无此字段 | `console/src/types/problem-list.ts:8` | 前端类型定义与后端不一致，运行时为 undefined |
| **H2** | Console `ProblemList` 有 `categoryId` 但后端 `ProblemListSummaryVO` 无此字段 | `console/src/types/problem-list.ts:19` | 同上，categoryId 仅在 viewer 嵌套对象中返回 |
| **H3** | Console `ProblemListCategory` 缺少 `description`、`icon`、`color`、`listCount` | `console/src/types/problem-list.ts:24-29` | 前端无法渲染分类的图标、颜色和描述 |
| **H4** | Management `updateList` 方法已定义但从未被调用 | `management/src/api/admin/problem-lists.ts:117-120` | 死代码，增加维护负担 |

### 🟢 MEDIUM

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| **M1** | Console `ProblemListDetailResponse.stats` 被获取但从未渲染 | `console/src/views/problem-list/ProblemListView.vue` | 浪费带宽，客户端重复计算 |
| **M2** | Management 列表页未渲染 `authorName`/`authorUsername` | `management/src/views/problem-lists/columns.ts` | 管理员无法在列表中看到作者信息 |
| **M3** | Management ProblemsManager 未渲染 `status`/`addedAt` | `management/src/views/problem-lists/components/ProblemsManager.vue` | 信息不完整 |
| **M4** | Console `createCategory`/`updateCategory` 不发送 `description`/`icon`/`color` | `console/src/api/problem-list.ts:497-517` | 后端支持但前端未利用 |

---

## 七、建议对齐方案

### 7.1 后端修复（优先级 P0）

#### C1: 统一 description 校验长度

```java
// CreateProblemListDTO.java — 改为 500
@Size(max = 500, message = "Description must not exceed 500 characters")
private String description;

// UpdateProblemListDTO.java — 保持 500（已正确）
// UpdateBasicInfoDTO.java — 保持 500（已正确）
```

**理由**: 创建和更新应使用同一限制。选择 500 而非 1000，因为题单描述不需要太长。

#### C5: fork 返回字段对齐

**方案 A（推荐）**: 修改后端 `ForkResultVO`，将 `newListId` 改为 `id`

```java
@Data
public class ForkResultVO {
    private String id;  // 原为 newListId
}
```

**方案 B**: 修改前端适配后端字段名

```typescript
const res = await apiPost<{ newListId: string }>(...)
return res.newListId
```

#### C6: 扩展后端 `ListStatusVO`

```java
@Data
public static class ListStatusVO {
    private String id;
    private String name;
    private Boolean hasProblem;
    private Integer problemCount;   // 新增
    private Boolean canEdit;         // 新增
}
```

### 7.2 Console 前端修复（优先级 P0）

#### C2/C3/C4: 补全类型定义

```typescript
// console/src/types/problem-list.ts
export interface ProblemList {
  // ... 现有字段 ...
  authorName?: string       // 新增
  authorUsername?: string   // 新增
}

export interface ProblemListDetailResponse {
  list: ProblemList | null
  problems: Problem[]
  stats: ProblemListStats | null
  isOwner?: boolean         // 新增（从 detailVO 顶层提取）
  viewer?: {
    isSaved: boolean
    categoryId: string | null
  }
  categories?: ProblemListCategoryOption[]
}
```

#### H1/H2: 移除或标记后端不返回的字段

```typescript
// 方案 A: 移除（如果确认后端不返回）
export interface ProblemList {
  // 移除 favoritesCount（后端无此字段）
  // 移除 categoryId（仅在 viewer 中返回）
}

// 方案 B: 保留为可选字段，但在 mapper 中正确处理
// 当前 mapProblemList 已正确处理（不映射不存在的字段）
```

#### H3: 扩展 ProblemListCategory

```typescript
export interface ProblemListCategory {
  id: string
  name: string
  sortOrder: number
  lists: ProblemList[]
  description?: string   // 新增
  icon?: string           // 新增
  color?: string          // 新增
  listCount?: number      // 新增
}
```

### 7.3 Management 前端修复（优先级 P1）

#### H4: 移除死代码

```typescript
// management/src/api/admin/problem-lists.ts
// 移除 updateList 方法（未被任何组件调用）
```

#### M2: 在列表页添加作者列

```typescript
// management/src/views/problem-lists/columns.ts
// 添加 authorName 列
{
  accessorKey: 'authorName',
  header: () => t('problemLists.columns.author'),
  cell: ({ row }) => row.original.authorName || '-',
}
```

#### M3: 在 ProblemsManager 渲染 status 和 addedAt

```typescript
// management/src/views/problem-lists/components/ProblemsManager.vue
// 在表格列中添加 status 和 addedAt 列
```

### 7.4 Console mapper 修复（优先级 P0）

#### C2/C6: 更新 mapProblemList 和 getUserListsForProblem

```typescript
// console/src/api/problem-list.ts
function mapProblemList(input: unknown): ProblemList {
  // ... 现有逻辑 ...
  return {
    // ... 现有字段 ...
    authorName: typeof raw.authorName === 'string' ? raw.authorName : undefined,
    authorUsername: typeof raw.authorUsername === 'string' ? raw.authorUsername : undefined,
  }
}

// getUserListsForProduct — 适配后端字段名
export async function getUserListsForProblem(...): Promise<ProblemListWithStatus[]> {
  return data.map((item) => {
    const raw = item as BackendProblemList
    return {
      ...mapProblemList(item),
      containsProblem: Boolean(raw.hasProblem),   // 适配 hasProblem → containsProblem
      canEdit: false,                               // 后端不返回，默认 false
    }
  })
}
```

---

## 八、建议行动计划

| 优先级 | 任务 | 工作量 | 文件 |
|-------|------|--------|------|
| P0 | 后端统一 `description` 校验为 500 | 2 分钟 | `CreateProblemListDTO.java` |
| P0 | 后端 `ForkResultVO` 字段 `newListId` → `id` | 5 分钟 | `ForkResultVO.java` + ServiceImpl |
| P0 | 后端 `ListStatusVO` 添加 `problemCount`、`canEdit` | 10 分钟 | `UserListsForProblemVO.java` + ServiceImpl |
| P0 | Console `ProblemList` 类型添加 `authorName`、`authorUsername` | 3 分钟 | `console/src/types/problem-list.ts` |
| P0 | Console `ProblemListDetailResponse` 添加 `isOwner` | 3 分钟 | `console/src/types/problem-list.ts` |
| P0 | Console mapper 适配 `hasProblem` → `containsProblem` | 5 分钟 | `console/src/api/problem-list.ts` |
| P0 | Console mapper 添加 `authorName`/`authorUsername` 映射 | 5 分钟 | `console/src/api/problem-list.ts` |
| P0 | Console `Problem` 类型添加 `sortOrder`、`addedAt` | 3 分钟 | `console/src/types/problem.ts` |
| P1 | Console `ProblemListCategory` 添加 `description`/`icon`/`color`/`listCount` | 5 分钟 | `console/src/types/problem-list.ts` |
| P1 | Console mapper 添加 category 新字段映射 | 5 分钟 | `console/src/api/problem-list.ts` |
| P1 | Management 移除 `updateList` 死代码 | 2 分钟 | `management/src/api/admin/problem-lists.ts` |
| P1 | Management 列表页添加作者列 | 10 分钟 | `management/src/views/problem-lists/columns.ts` |
| P2 | Console `ProblemList` 移除 `favoritesCount`（后端不返回） | 3 分钟 | `console/src/types/problem-list.ts` |
| P2 | Management ProblemsManager 添加 `status`/`addedAt` 列 | 10 分钟 | `ProblemsManager.vue` |
| P2 | Console `createCategory`/`updateCategory` 支持 `description`/`icon`/`color` | 15 分钟 | API + UI |

**预计总工作量**: ~90 分钟

---

## 九、核心文件清单

### 后端

| 文件 | 说明 |
|---|---|
| `backend-spring/.../problemlist/controller/ProblemListController.java` | 用户侧 Controller |
| `backend-spring/.../admin/controller/AdminProblemListController.java` | 管理侧 Controller |
| `backend-spring/.../problemlist/dto/CreateProblemListDTO.java` | 创建 DTO |
| `backend-spring/.../problemlist/dto/UpdateProblemListDTO.java` | 通用更新 DTO |
| `backend-spring/.../problemlist/dto/UpdateBasicInfoDTO.java` | 基础信息更新 DTO |
| `backend-spring/.../problemlist/dto/UpdateVisibilityDTO.java` | 可见性更新 DTO |
| `backend-spring/.../problemlist/dto/UpdateBannerDTO.java` | Banner 更新 DTO |
| `backend-spring/.../problemlist/dto/ProblemListSummaryVO.java` | 列表 VO |
| `backend-spring/.../problemlist/dto/ProblemListDetailVO.java` | 详情 VO |
| `backend-spring/.../problemlist/dto/UserListsForProblemVO.java` | 用户题单状态 VO |
| `backend-spring/.../problemlist/dto/ForkResultVO.java` | Fork 结果 VO |
| `backend-spring/.../problemlist/dto/UserProblemListsVO.java` | 用户题单概览 VO |
| `backend-spring/.../problemlist/dto/CategorySummaryVO.java` | 分类概览 VO |
| `backend-spring/.../problemlist/dto/CreateCategoryDTO.java` | 创建分类 DTO |
| `backend-spring/.../problemlist/dto/UpdateCategoryDTO.java` | 更新分类 DTO |

### Console 前端

| 文件 | 说明 |
|---|---|
| `console/src/api/problem-list.ts` | API 层 + mapper |
| `console/src/types/problem-list.ts` | 类型定义 |
| `console/src/types/problem.ts` | Problem 类型 |
| `console/src/views/problem-list/ProblemListView.vue` | 题单详情页 |
| `console/src/views/personal/ProblemListsView.vue` | 个人题单页 |
| `console/src/features/sider/composables/useSidebarLists.ts` | 侧边栏 composable |
| `console/src/components/edge-operations/ProblemSaveButton.vue` | 保存到题单按钮 |

### Management 前端

| 文件 | 说明 |
|---|---|
| `management/src/api/admin/problem-lists.ts` | API 层 |
| `management/src/stores/admin/problem-lists.ts` | Pinia Store |
| `management/src/views/problem-lists/ProblemListsListView.vue` | 列表页 |
| `management/src/views/problem-lists/ProblemListDetailView.vue` | 详情/编辑页 |
| `management/src/views/problem-lists/columns.ts` | 列定义 |
| `management/src/views/problem-lists/components/BasicInfoSection.vue` | 基础信息区块 |
| `management/src/views/problem-lists/components/VisibilitySection.vue` | 可见性区块 |
| `management/src/views/problem-lists/components/BannerSection.vue` | Banner 区块 |
| `management/src/views/problem-lists/components/ProblemsManager.vue` | 题目管理区块 |

---

## 十、总结

当前 `problem-lists` 模块存在 **6 个 CRITICAL**、**4 个 HIGH**、**4 个 MEDIUM** 级别的前后端不对齐问题。核心矛盾集中在：

1. **类型字段缺失**：Console 前端类型定义缺少后端已返回的 `authorName`/`authorUsername`/`isOwner`/`sortOrder`/`addedAt`
2. **字段名不匹配**：fork 返回 `newListId` vs 前端期望 `id`；`hasProblem` vs `containsProblem`
3. **校验规则不一致**：`description` 创建 1000 vs 更新 500
4. **前端定义了后端不返回的字段**：`favoritesCount`、`categoryId`（在 SummaryVO 中）
5. **死代码**：Management `updateList` 方法未被调用

Management 侧的细粒度 PATCH 端点（`/basic-info`、`/visibility`、`/banner`）已与后端完全对齐，是之前修复的成果。Console 侧仍需补齐类型和映射。