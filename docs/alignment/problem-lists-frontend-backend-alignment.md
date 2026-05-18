# `/problem-lists` 前后端颗粒度对齐分析报告

**目标 URL**: `http://localhost:9003/problem-lists`
**分析日期**: 2026/05/18
**模块**: Admin Problem Lists (题单管理)
**状态**: ✅ 已修复

---

## 一、总体架构概览

| 层 | 技术 | 文件位置 |
|---|---|---|
| **前端 (管理后台)** | Vue 3 + TypeScript + Pinia | `management/src/` |
| **后端** | Spring Boot + MyBatis-Plus | `backend-spring/src/main/java/com/ulticode/modules/` |

### 前后端 API 映射总览

```
前端 GET /admin/problem-lists              → 后端 AdminProblemListController.getProblemLists()
前端 GET /admin/problem-lists/:id         → 后端 AdminProblemListController.getProblemListById()
前端 POST /admin/problem-lists            → 后端 AdminProblemListController.createProblemList()
前端 PATCH /admin/problem-lists/:id      → 后端 AdminProblemListController.updateProblemList()
前端 DELETE /admin/problem-lists/:id      → 后端 AdminProblemListController.deleteProblemList()
前端 POST /admin/problem-lists/:id/problems → 后端 AdminProblemListController.updateListProblems()
```

### 关键文件路径

| 文件 | 说明 |
|------|------|
| `management/src/api/admin/problem-lists.ts` | 前端 API 定义 |
| `management/src/views/problem-lists/` | 页面组件 |
| `management/src/stores/admin/problem-lists.ts` | Pinia Store |
| `backend-spring/.../admin/controller/AdminProblemListController.java` | 后端 Controller |
| `backend-spring/.../admin/service/AdminProblemListService.java` | 后端 Service 接口 |
| `backend-spring/.../admin/service/impl/AdminProblemListServiceImpl.java` | 后端 Service 实现 |
| `backend-spring/.../problemlist/dto/` | 后端 DTO/VO 定义 |

---

## 二、类型/字段对齐分析

### 2.1 列表项类型对齐

**前端 `ProblemList`** vs **后端 `ProblemListSummaryVO`**

| 字段 | 前端类型 | 后端类型 | 状态 |
|------|---------|---------|------|
| `id` | `string` | `String` | ✅ 匹配 |
| `name` | `string` | `String` | ✅ 匹配 |
| `description` | `string` | `String` | ✅ 匹配 |
| `authorId` | `string` | `String` | ✅ 匹配 |
| `authorName` | ❌ 缺失 | ✅ `String` | 🔴 **前端缺失** |
| `authorUsername` | ❌ 缺失 | ✅ `String` | 🔴 **前端缺失** |
| `isPublic` | `boolean` | `Boolean` | ✅ 匹配 |
| `isFeatured` | `boolean` | `Boolean` | ✅ 匹配 |
| `bannerTag` | `string?` | `String` | ✅ 匹配 |
| `bannerIcon` | `string?` | `String` | ✅ 匹配 |
| `bannerTheme` | `string?` | `String` | ✅ 匹配 |
| `bannerOrder` | `number` | `Integer` | ✅ 匹配 |
| `createdAt` | `string` | `LocalDateTime` | ⚠️ 类型名称不同（需验证序列化） |
| `updatedAt` | `string` | `LocalDateTime` | ⚠️ 同上 |
| `problemCount` | `number?` | `Integer` | ✅ 匹配 |
| `isSaved` | ❌ 缺失 | ✅ `Boolean` | 🔴 **前端缺失** |
| `slug` | ❌ 缺失 | ❌ 缺失 | ⚠️ 双方都缺失（但 CreateDto 有） |

### 2.2 详情类型对齐

**前端 `ProblemListDetail`** vs **后端 `ProblemListDetailVO`**

| 字段 | 前端类型 | 后端类型 | 状态 |
|------|---------|---------|------|
| `problems` (继承 ProblemList) | `ProblemListProblem[]` | `ProblemInListVO[]` | ⚠️ 字段名不同 |
| `isOwner` | ❌ 缺失 | ✅ `Boolean` | 🔴 **前端缺失** |
| `ProblemListProblem.id` | `number` | `Long id` | ✅ 匹配 |
| `ProblemListProblem.slug` | `string` | `String slug` | ✅ 匹配 |
| `ProblemListProblem.title` | `string` | `String title` | ✅ 匹配 |
| `ProblemListProblem.difficulty` | `string` | `String difficulty` | ✅ 匹配 |
| `ProblemListProblem.status` | `string` | `String status` | ✅ 匹配 |
| `ProblemListProblem.sortOrder` | `number` | `Integer sortOrder` | ✅ 匹配 |
| `ProblemListProblem.addedAt` | `string` | `LocalDateTime addedAt` | ⚠️ 需验证序列化 |

---

## 三、API 端点缺失分析

### 3.1 前端有但后端无（未实现）

| 前端 API 方法 | 后端对应 | 状态 |
|-------------|---------|------|
| `updateBasicInfo(id, UpdateBasicInfoDto)` | **无独立端点** | 🔴 缺失 |
| `updateVisibility(id, UpdateVisibilityDto)` | **无独立端点** | 🔴 缺失 |
| `updateBanner(id, UpdateBannerDto)` | **无独立端点** | 🔴 缺失 |

这 3 个 API 调用使用的是通用的 `PATCH /admin/problem-lists/{id}` 端点，但它们**发送的是不完整的 DTO**。

### 3.2 DTO 字段不匹配

| 前端 DTO | 后端 DTO | 不匹配字段 |
|---------|---------|-----------|
| `UpdateBasicInfoDto` 有 `slug` | `UpdateBasicInfoDTO` **无** `slug` | `slug` |
| `UpdateBannerDto` 有 `bannerIcon` | `UpdateBannerDTO` **无** `bannerIcon` | `bannerIcon` |
| `UpdateBannerDto` 有 `bannerTheme` | `UpdateBannerDTO` **有** `bannerTheme` | ✅ 已匹配 |

---

## 四、具体问题清单

### 🔴 CRITICAL 问题

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| **C1** | `ProblemListSummaryVO` 有 `authorName`、`authorUsername`、`isSaved` 字段，前端 `ProblemList` 类型缺失 | `problem-lists.ts` 第 6-7 行 | 管理后台列表页无法显示作者信息，无法判断是否已收藏 |
| **C2** | `ProblemListDetailVO` 有 `isOwner` 字段，前端 `ProblemListDetail` 缺失 | `problem-lists.ts` | 详情页无法判断当前用户是否为所有者 |
| **C3** | `UpdateBasicInfoDto` 前端有 `slug`，但后端 `UpdateBasicInfoDTO` 没有该字段 | `UpdateBasicInfoDTO.java` | 前端发送的 `slug` 字段会被后端忽略 |
| **C4** | `UpdateBannerDto` 前端有 `bannerIcon`，但后端 `UpdateBannerDTO` 没有该字段 | `UpdateBannerDTO.java` | 前端发送的 `bannerIcon` 字段会被后端忽略 |
| **C5** | `UpdateProblemListProblemsDTO` 验证 `@NotEmpty`（problems 不能为空） | `UpdateProblemListProblemsDTO.java` 第 16 行 | 前端无法清空题单中的所有题目 |

### ⚠️ MEDIUM 问题

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| **M1** | 前端 `UpdateProblemListProblemsDto` 期望返回 `ProblemList`，但后端 `updateListProblems` 返回 `Result<Void>` | `problem-lists.ts` 第 61 行 | 返回类型不匹配，刷新后数据可能不一致 |
| **M2** | 后端 `ProblemListSummaryVO` 没有 `slug` 字段（前端列表接口 `CreateProblemListDto` 支持 `slug`） | `ProblemListSummaryVO.java` | 无法通过列表 API 获取题单 slug |

---

## 五、修复建议

### 5.1 类型修复优先级

**Step 1: 补全前端 `ProblemList` 类型**

```typescript
// management/src/api/admin/problem-lists.ts
export interface ProblemList {
  // ... 现有字段 ...
  authorName?: string       // 新增
  authorUsername?: string   // 新增
  isSaved?: boolean         // 新增
}
```

**Step 2: 补全前端 `ProblemListDetail` 类型**

```typescript
export interface ProblemListDetail extends ProblemList {
  isOwner?: boolean        // 新增
  // problems 已有
}
```

### 5.2 DTO 字段对齐

**方案 A（推荐）：扩展后端 DTO 以匹配前端需求**

- `UpdateBasicInfoDTO` 添加 `slug` 字段
- `UpdateBannerDTO` 添加 `bannerIcon`、`bannerTheme` 字段

**方案 B：移除前端多余字段**

- 前端 `UpdateBasicInfoDto` 移除 `slug`
- 前端 `UpdateBannerDto` 移除 `bannerIcon`

### 5.3 验证修复

**`UpdateProblemListProblemsDTO`** — 移除 `@NotEmpty` 约束，或改为允许空列表表示清空：

```java
// 当前（有问题的）
@NotEmpty(message = "Problems list cannot be empty")

// 建议修改为
// 如果需要支持清空，则移除该约束
// 或者添加单独的 "清空" 语义（如传入空数组表示清空）
```

### 5.4 返回类型对齐

**`updateListProblems`** 前端期望返回 `ProblemList`，但后端返回 `Result<Void>`。建议前端改为只刷新列表数据，而非依赖返回值：

```typescript
// management/src/api/admin/problem-lists.ts
async updateListProblems(id: string, data: UpdateProblemListProblemsDto): Promise<void> {
  await apiPost(`/admin/problem-lists/${id}/problems`, data)
  // 返回 void，让调用方手动刷新
}
```

---

## 六、建议行动计划

| 优先级 | 任务 | 工作量 | 文件 |
|-------|------|--------|------|
| P0 | 后端 `UpdateBasicInfoDTO` 添加 `slug` 字段 | 5 分钟 | `backend-spring/.../dto/UpdateBasicInfoDTO.java` |
| P0 | 后端 `UpdateBannerDTO` 添加 `bannerIcon`、`bannerTheme` 字段 | 5 分钟 | `backend-spring/.../dto/UpdateBannerDTO.java` |
| P0 | 后端 `UpdateProblemListProblemsDTO` 移除 `@NotEmpty` 约束 | 2 分钟 | `backend-spring/.../dto/UpdateProblemListProblemsDTO.java` |
| P1 | 前端 `ProblemList` 类型添加 `authorName`、`authorUsername`、`isSaved` | 5 分钟 | `management/src/api/admin/problem-lists.ts` |
| P1 | 前端 `ProblemListDetail` 类型添加 `isOwner` | 3 分钟 | `management/src/api/admin/problem-lists.ts` |
| P2 | 前端 `updateListProblems` 返回类型改为 `Promise<void>` | 5 分钟 | `management/src/api/admin/problem-lists.ts` |
| P2 | 后端 `ProblemListSummaryVO` 添加 `slug` 字段（可选，如果前端需要） | 5 分钟 | `backend-spring/.../dto/ProblemListSummaryVO.java` |

**预计总工作量**: ~30 分钟

---

## 七、修复结果

### 已修复问题

| 问题编号 | 描述 | 修复文件 |
|---------|------|---------|
| **C1** | 前端 `ProblemList` 类型缺失 `authorName`、`authorUsername`、`isSaved` | `management/src/api/admin/problem-lists.ts` |
| **C2** | 前端 `ProblemListDetail` 类型缺失 `isOwner` | `management/src/api/admin/problem-lists.ts` |
| **C3** | 后端 `UpdateBasicInfoDTO` 缺失 `slug` 字段 | `backend-spring/.../dto/UpdateBasicInfoDTO.java` |
| **C4** | 后端 `UpdateBannerDTO` 缺失 `bannerIcon` 字段 | `backend-spring/.../dto/UpdateBannerDTO.java` |
| **C5** | 后端 `UpdateProblemListProblemsDTO` 的 `@NotEmpty` 约束阻止清空题单 | `backend-spring/.../dto/UpdateProblemListProblemsDTO.java` |

### 剩余问题 (M2 - 低优先级)

| 问题编号 | 描述 | 建议 |
|---------|------|------|
| **M1** | 前端 `updateListProblems` 返回类型已是 `Promise<void>` | 无需修复 |
| **M2** | 后端 `ProblemListSummaryVO` 没有 `slug` 字段 | 可选，如前端需要可通过列表 API 获取则添加 |

---

## 八、结论

前后端在 **题单管理（problem-lists）** 模块的以下不一致已全部修复：

1. ✅ **类型字段缺失**：前端 `ProblemList` 和 `ProblemListDetail` 已补全后端返回的所有字段
2. ✅ **DTO 字段不匹配**：后端 `UpdateBasicInfoDTO` 已添加 `slug`，`UpdateBannerDTO` 已添加 `bannerIcon`
3. ✅ **验证约束过严**：`@NotEmpty` 约束已移除，支持清空题单操作
4. ✅ **返回类型不匹配**：前端 `updateListProblems` 已返回 `Promise<void>`