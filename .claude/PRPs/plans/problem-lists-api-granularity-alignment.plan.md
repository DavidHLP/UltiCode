# Plan: Problem Lists 前后端 API 颗粒度对齐

## Summary
将 Problem Lists 模块的前后端 API 颗粒度对齐，涵盖认证方式迁移（@RequestParam → SecurityUtil）、Response 字段名统一、Management 返回值类型修复、DTO 字段精度校正。共 9 个原子任务，按 P0→P1→P2 优先级执行。

## User Story
As a 全栈工程师, I want Problem Lists 前后端 API 完全对齐, so that 类型安全、认证一致、无冗余参数、返回值可利用。

## Problem → Solution
当前 ProblemListController 使用 `@RequestParam userId` 传递用户身份（与其它 Controller 的 SecurityUtil 模式不一致，存在安全风险），Console 前端 Response 字段名与后端 VO 不匹配，Management 前端丢弃后端返回的更新数据。 → 迁移至 SecurityUtil 认证、统一字段名、修复返回值类型。

## Metadata
- **Complexity**: Large
- **Source PRD**: `docs/problem-lists-api-granularity-analysis.md`
- **PRD Phase**: N/A (standalone)
- **Estimated Files**: ~20

---

## UX Design

### Before
```
Console 前端 → GET /problem-lists/overview?userId=xxx
                        ↓
后端 ProblemListController @RequestParam userId → 手动传递
                        ↓
Response: { ownLists, savedLists, featuredLists }
                        ↓
前端 mapper: ownLists→myLists, featuredLists→featured (无编译时类型安全)

Management 前端 → PATCH /admin/problem-lists/:id/basic-info
                        ↓
后端返回 ProblemListSummaryVO → 前端声明 Promise<void> → 丢弃返回值
                        ↓
前端乐观更新本地状态 (可能与后端不一致)
```

### After
```
Console 前端 → GET /problem-lists/overview (无 userId query param)
                        ↓
后端 ProblemListController SecurityUtil.getCurrentUserId() → 从 JWT 提取
                        ↓
Response: { ownLists, savedLists, featuredLists }
                        ↓
前端类型直接映射后端 VO (编译时类型安全)

Management 前端 → PATCH /admin/problem-lists/:id/basic-info
                        ↓
后端返回 ProblemListSummaryVO → 前端接收 ProblemList → 用返回值更新本地状态
                        ↓
前端状态与后端一致 (无需额外 getList 请求)
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Console API 认证 | `?userId=xxx` query param | JWT cookie 自动认证 | 移除冗余参数 |
| Console Response 映射 | `unknown` + 手动 mapper | 强类型 Backend 接口 + mapper | 编译时安全 |
| Management 更新响应 | `Promise<void>` 丢弃 | `Promise<ProblemList>` 利用 | 减少网络请求 |
| Management 问题管理 | 更新后 re-fetch | 更新后 re-fetch (保持) | 已正确实现 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/.../problemlist/controller/ProblemListController.java` | 全文 | 需迁移的核心 Controller |
| P0 | `backend-spring/.../common/util/SecurityUtil.java` | 20-26 | 目标认证模式 |
| P0 | `console/src/api/problem-list.ts` | 全文 | 需修改的 Console API 层 |
| P1 | `management/src/api/admin/problem-lists.ts` | 全文 | 需修改的 Management API 层 |
| P1 | `management/src/views/problem-lists/components/BasicInfoSection.vue` | 100-120 | auto-save 消费模式 |
| P2 | `console/src/types/problem-list.ts` | 全文 | 需清理的 Console 类型 |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| SecurityUtil 模式 | SubmissionController.java, BookmarkController.java | `SecurityUtil.getCurrentUserId()` + null check → `BusinessException(ErrorCode.UNAUTHORIZED)` |
| httpOnly Cookie 认证 | `console/src/utils/request.ts` | `withCredentials: true` + CSRF token，无手动 Authorization header |

---

## Patterns to Mirror

### SECURITY_UTIL_AUTH (目标模式)
// SOURCE: backend-spring/.../submission/controller/SubmissionController.java:52-55
```java
@PostMapping
public Result<SubmissionVO> submit(@Valid @RequestBody CreateSubmissionDTO dto) {
    String userId = SecurityUtil.getCurrentUserId();
    if (userId == null) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    return Result.success(submissionService.submit(userId, dto));
}
```

### REQUEST_PARAM_AUTH (当前模式 - 需替换)
// SOURCE: backend-spring/.../problemlist/controller/ProblemListController.java:50-52
```java
@PostMapping
public Result<ProblemListSummaryVO> createList(
        @RequestParam String userId,
        @Valid @RequestBody CreateProblemListDTO dto) {
```

### OPTIONAL_USER_ID (未认证访问模式)
// SOURCE: backend-spring/.../problemlist/controller/ProblemListController.java:28-34
```java
@GetMapping("/overview")
public Result<UserProblemListsVO> getOverview(
        @RequestParam(required = false) String userId,
        @RequestHeader(value = "Accept-Language", required = false) String locale) {
```

### MANAGEMENT_AUTO_SAVE (目标更新模式)
// SOURCE: management/src/views/problem-lists/components/VisibilitySection.vue:44-50
```typescript
const { saveStatus, save } = useAutoSave<UpdateVisibilityDto>(
  async (data) => {
    if (!props.modelValue) return
    await adminProblemListsApi.updateVisibility(props.modelValue.id, data)
  },
  { debounceMs: 1000, blurTriggers: true },
)
```

### OPTIMISTIC_LOCAL_UPDATE (当前模式 - 需改为利用返回值)
// SOURCE: management/src/views/problem-lists/components/BasicInfoSection.vue:108-115
```typescript
await adminProblemListsApi.updateBasicInfo(props.modelValue!.id, updateData)
emit('update:modelValue', {
  ...props.modelValue!,
  name,
  description: description || '',
})
```

### CONSOLE_MAPPER_PATTERN (当前模式)
// SOURCE: console/src/api/problem-list.ts:222-239
```typescript
function mapUserProblemListsResponse(input: unknown): UserProblemListsResponse {
  if (!input || typeof input !== "object") {
    return { myLists: [], savedLists: [], featured: [], categories: [] };
  }
  const raw = input as BackendUserProblemListsResponse;
  return {
    myLists: Array.isArray(raw.ownLists) ? raw.ownLists.map(mapProblemList) : [],
    savedLists: Array.isArray(raw.savedLists) ? raw.savedLists.map(mapProblemList) : [],
    featured: Array.isArray(raw.featuredLists) ? raw.featuredLists.map(mapProblemList) : [],
    categories: Array.isArray(raw.categories) ? raw.categories.map(mapCategory) : [],
  };
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/.../problemlist/controller/ProblemListController.java` | UPDATE | 迁移 @RequestParam → SecurityUtil |
| `console/src/api/problem-list.ts` | UPDATE | 移除 userId query param，统一字段名 |
| `console/src/types/problem-list.ts` | UPDATE | 对齐 Response 类型与后端 VO |
| `management/src/api/admin/problem-lists.ts` | UPDATE | 修复返回值类型，修正 DTO 字段 |
| `management/src/views/problem-lists/components/BasicInfoSection.vue` | UPDATE | 利用 API 返回值更新状态 |
| `management/src/views/problem-lists/components/VisibilitySection.vue` | UPDATE | 利用 API 返回值更新状态 |
| `management/src/views/problem-lists/components/BannerSection.vue` | UPDATE | 利用 API 返回值更新状态 |
| `management/src/stores/admin/problem-lists.ts` | UPDATE | 添加细粒度更新 actions |
| `console/src/views/problem-list/composables/useProblemListOperations.ts` | UPDATE | 适配移除 userId 后的 API 签名 |
| `console/src/views/personal/composables/useProblemLists.ts` | UPDATE | 适配移除 userId 后的 API 签名 |
| `console/src/features/sider/composables/useSidebarLists.ts` | UPDATE | 适配移除 userId 后的 API 签名 |

## NOT Building
- Console 分页查询功能（当前 overview 全量返回，不在本次对齐范围）
- Console 细粒度更新端点（当前使用通用 PATCH，角色差异合理）
- 后端 ProblemListDetailVO 重构为嵌套结构（影响面太大，前端 mapper 可处理）
- Management 收藏/分类管理功能（仅 Admin 角色，无需此功能）
- 数据库 migration（本次不涉及表结构变更）

---

## Step-by-Step Tasks

---

### TASK-001: [Backend] 重构 ProblemListController 认证方式 — 从 @RequestParam 迁移到 SecurityUtil

**[任务描述]**
将 ProblemListController 中所有使用 `@RequestParam String userId` 的端点迁移为使用 `SecurityUtil.getCurrentUserId()` 获取用户身份，与 SubmissionController、BookmarkController、FollowController 保持一致。对于需要支持未登录用户访问的端点（overview、list overview），使用 `SecurityUtil.getCurrentUserId()` 返回 null 时传递 null 给 service 层。移除所有 `@RequestParam String userId` 参数声明。

**[前置依赖]**
无

**[参考文档]**
- `docs/problem-lists-api-granularity-analysis.md` — P0-1
- `backend-spring/.../submission/controller/SubmissionController.java` — SecurityUtil 使用范例
- `backend-spring/.../common/util/SecurityUtil.java` — getCurrentUserId() 实现参考

**[交付物标准]**
1. ProblemListController.java 中所有端点不再包含 `@RequestParam String userId` 参数
2. 所有需要认证的端点使用 `SecurityUtil.getCurrentUserId()` + null check + `throw new BusinessException(ErrorCode.UNAUTHORIZED)`
3. 可选认证端点（overview、list overview）使用 `SecurityUtil.getCurrentUserId()` 允许返回 null
4. 保留 `@RequestHeader(value = "Accept-Language", required = false) String locale` 参数不变
5. Controller 层方法签名变更后，Service 层无需修改（userId 参数仍作为方法参数传递给 service）
6. 编译通过：`./mvnw compile`

---

# Workflow & DoD (Definition of Done)
为了确保代码质量，本任务必须严格遵循以下质量内循环，直至代码审查（CR）完全通过：

1. 🧪 【初步自测】
   - 开发者完成编码后，必须针对「交付物标准」进行本地功能自测或编写单元测试。

2. 🔍 【提交代码审查 (CR)】
   - 发起 Pull Request (PR) / Merge Request (MR)，邀请核心团队成员进行代码评审，并完整记录 CR 反馈意见。

3. 🛠️ 【修复 CR 意见 (Fix CR)】
   - 针对评审中发现的性能、安全、规范等问题逐项修复，禁止遗漏任何一条 blocking 意见。

4. 🔄 【回归测试】
   - 对修复后的代码再次进行本地回归测试，确保没有引入新的破坏性变更（Regression Bug）。

5. 🏁 【流程终止与提交条件】
   - **准出条件（DoD）**：只有当所有 CR 意见全部闭环、Reviewer 给出 Approved 状态，且回归测试 100% 通过时，方可执行最后一步。
   - **最终操作**：将代码正式 Commit 并合并至主分支，更新任务状态为"已完成"。

---

### TASK-002: [Console 前端] 移除 API 层 userId query parameter

**[任务描述]**
在 Console 前端 `console/src/api/problem-list.ts` 中，移除所有通过 `?userId=${userId}` 传递的 query parameter。将所有需要 userId 的函数签名改为不再接收 userId 参数（由后端从 Security 上下文自动获取）。同步更新调用这些函数的 composable 层（`useProblemListOperations`、`useProblemLists`、`useSidebarLists`），移除 userId 传参。

**[前置依赖]**
TASK-001（后端必须先完成迁移，否则前端移除 userId 后端无法获取用户身份）

**[参考文档]**
- `docs/problem-lists-api-granularity-analysis.md` — P0-1
- `console/src/api/bookmark.ts` — 无 userId 的 API 调用范例
- `console/src/api/submission.ts` — 无 userId 的 API 调用范例

**[交付物标准]**
1. `console/src/api/problem-list.ts` 中所有函数不再接收 userId 参数，不再拼接 `?userId=` query param
2. `fetchProblemListsOverview()` 签名变为 `(): Promise<UserProblemListsResponse>`
3. `fetchProblemListOverview(listId)` 签名变为 `(listId: ProblemListId): Promise<ProblemListDetailResponse>`
4. `createProblemList(data)` 签名变为 `(data: {...}): Promise<ProblemListItem>`
5. 所有 mutation 函数（update、delete、fork、addProblem、removeProblem、save、unsave 等）移除 userId 参数
6. `console/src/views/problem-list/composables/useProblemListOperations.ts` 中所有调用适配新签名
7. `console/src/views/personal/composables/useProblemLists.ts` 中所有调用适配新签名
8. `console/src/features/sider/composables/useSidebarLists.ts` 中所有调用适配新签名
9. ESLint + type-check 通过：`cd console && pnpm lint && pnpm type-check`

---

# Workflow & DoD (Definition of Done)
为了确保代码质量，本任务必须严格遵循以下质量内循环，直至代码审查（CR）完全通过：

1. 🧪 【初步自测】
   - 开发者完成编码后，必须针对「交付物标准」进行本地功能自测或编写单元测试。

2. 🔍 【提交代码审查 (CR)】
   - 发起 Pull Request (PR) / Merge Request (MR)，邀请核心团队成员进行代码评审，并完整记录 CR 反馈意见。

3. 🛠️ 【修复 CR 意见 (Fix CR)】
   - 针对评审中发现的性能、安全、规范等问题逐项修复，禁止遗漏任何一条 blocking 意见。

4. 🔄 【回归测试】
   - 对修复后的代码再次进行本地回归测试，确保没有引入新的破坏性变更（Regression Bug）。

5. 🏁 【流程终止与提交条件】
   - **准出条件（DoD）**：只有当所有 CR 意见全部闭环、Reviewer 给出 Approved 状态，且回归测试 100% 通过时，方可执行最后一步。
   - **最终操作**：将代码正式 Commit 并合并至主分支，更新任务状态为"已完成"。

---

### TASK-003: [Console 前端] 统一 Response 字段名与后端 VO 映射

**[任务描述]**
对齐 Console 前端 `UserProblemListsResponse` 类型与后端 `UserProblemListsVO` 的字段名。将前端类型的 `myLists` 重命名为 `ownLists`，`featured` 重命名为 `featuredLists`，使其与后端 VO 完全一致。更新所有消费这些字段的组件和 composable。同时将 `BackendUserProblemListsResponse` 从 `unknown` 类型改为强类型接口，确保编译时类型安全。

**[前置依赖]**
无（可与 TASK-001/002 并行，无代码依赖）

**[参考文档]**
- `docs/problem-lists-api-granularity-analysis.md` — P0-2, Section 2.2.2
- `backend-spring/.../problemlist/dto/UserProblemListsVO.java` — 后端 VO 定义

**[交付物标准]**
1. `console/src/types/problem-list.ts` 中 `UserProblemListsResponse` 的 `myLists` → `ownLists`，`featured` → `featuredLists`
2. `console/src/api/problem-list.ts` 中 `BackendUserProblemListsResponse` 改为强类型接口（移除 `unknown` 字段类型）
3. `mapUserProblemListsResponse` mapper 简化：字段名一致后无需手动重命名
4. 所有消费 `data.myLists` 的组件改为 `data.ownLists`
5. 所有消费 `data.featured` 的组件改为 `data.featuredLists`
6. 涉及文件：`ProblemListsView.vue`、`MyListsTab.vue`、`SavedListsTab.vue`、`useProblemLists.ts`、`useSidebarLists.ts`
7. ESLint + type-check 通过

---

# Workflow & DoD (Definition of Done)
为了确保代码质量，本任务必须严格遵循以下质量内循环，直至代码审查（CR）完全通过：

1. 🧪 【初步自测】
   - 开发者完成编码后，必须针对「交付物标准」进行本地功能自测或编写单元测试。

2. 🔍 【提交代码审查 (CR)】
   - 发起 Pull Request (PR) / Merge Request (MR)，邀请核心团队成员进行代码评审，并完整记录 CR 反馈意见。

3. 🛠️ 【修复 CR 意见 (Fix CR)】
   - 针对评审中发现的性能、安全、规范等问题逐项修复，禁止遗漏任何一条 blocking 意见。

4. 🔄 【回归测试】
   - 对修复后的代码再次进行本地回归测试，确保没有引入新的破坏性变更（Regression Bug）。

5. 🏁 【流程终止与提交条件】
   - **准出条件（DoD）**：只有当所有 CR 意见全部闭环、Reviewer 给出 Approved 状态，且回归测试 100% 通过时，方可执行最后一步。
   - **最终操作**：将代码正式 Commit 并合并至主分支，更新任务状态为"已完成"。

---

### TASK-004: [后端] ProblemListDetailVO 添加 stats/viewer/categories 字段

**[任务描述]**
后端 `ProblemListDetailVO` 当前是扁平继承结构（继承 `ProblemListSummaryVO` + `problems`），但 Console 前端 `ProblemListDetailResponse` 期望嵌套结构包含 `stats`（解题统计）、`viewer`（当前用户收藏状态）、`categories`（分类选项）。在后端 `ProblemListDetailVO` 中添加这三个字段，并在 `ProblemListServiceImpl.getListOverview()` 中填充数据。

**[前置依赖]**
TASK-001（认证方式迁移后，service 层才能正确获取当前用户信息来填充 viewer 字段）

**[参考文档]**
- `docs/problem-lists-api-granularity-analysis.md` — P1-4, P2-10
- `console/src/types/problem-list.ts` — 前端 ProblemListDetailResponse/ProblemListStats 类型定义
- `console/src/api/problem-list.ts:340-384` — mapProblemListOverview mapper

**[交付物标准]**
1. `ProblemListDetailVO.java` 新增字段：
   - `ProblemListStatsVO stats` — 解题统计（totalCount, solvedCount, attemptedCount, todoCount, progress）
   - `ViewerStateVO viewer` — 当前用户状态（isSaved, categoryId）
   - `List<CategoryOptionVO> categories` — 分类选项列表
2. 创建 `ProblemListStatsVO.java`、`ViewerStateVO.java`、`CategoryOptionVO.java` 三个 VO 类
3. `ProblemListServiceImpl.getListOverview()` 中计算并填充 stats（基于 problems 列表和用户 submission 状态）、viewer（基于 ProblemListBookmark 查询）、categories（基于 ProblemListCategory 查询）
4. 编译通过：`./mvnw compile`
5. Swagger 文档自动更新（SpringDoc）

---

# Workflow & DoD (Definition of Done)
为了确保代码质量，本任务必须严格遵循以下质量内循环，直至代码审查（CR）完全通过：

1. 🧪 【初步自测】
   - 开发者完成编码后，必须针对「交付物标准」进行本地功能自测或编写单元测试。

2. 🔍 【提交代码审查 (CR)】
   - 发起 Pull Request (PR) / Merge Request (MR)，邀请核心团队成员进行代码评审，并完整记录 CR 反馈意见。

3. 🛠️ 【修复 CR 意见 (Fix CR)】
   - 针对评审中发现的性能、安全、规范等问题逐项修复，禁止遗漏任何一条 blocking 意见。

4. 🔄 【回归测试】
   - 对修复后的代码再次进行本地回归测试，确保没有引入新的破坏性变更（Regression Bug）。

5. 🏁 【流程终止与提交条件】
   - **准出条件（DoD）**：只有当所有 CR 意见全部闭环、Reviewer 给出 Approved 状态，且回归测试 100% 通过时，方可执行最后一步。
   - **最终操作**：将代码正式 Commit 并合并至主分支，更新任务状态为"已完成"。

---

### TASK-005: [Console 前端] 简化 ProblemListDetailResponse mapper — 适配后端新增字段

**[任务描述]**
在 TASK-004 后端添加 stats/viewer/categories 字段后，简化 Console 前端 `mapProblemListOverview` mapper。当前 mapper 手动构造 `stats`、`viewer`、`categories` 对象（部分硬编码为 null/undefined），适配后端新增字段后可直接从 Response 中读取。同时将 `BackendProblemListDetailResponse` 从 `unknown` 改为强类型接口。

**[前置依赖]**
TASK-004（后端必须先添加字段，前端才能适配）

**[参考文档]**
- `docs/problem-lists-api-granularity-analysis.md` — P1-4
- `console/src/api/problem-list.ts:340-384` — 当前 mapProblemListOverview 实现

**[交付物标准]**
1. `console/src/api/problem-list.ts` 中 `BackendProblemListDetailResponse` 改为强类型接口
2. `mapProblemListOverview` mapper 直接映射后端返回的 `stats`、`viewer`、`categories`，不再硬编码 null
3. `console/src/types/problem-list.ts` 中 `ProblemListDetailResponse.stats` 类型与后端 `ProblemListStatsVO` 对齐
4. ProblemListAnalytics.vue 正确消费 stats 数据
5. ProblemListView.vue 正确消费 viewer/categories 数据
6. ESLint + type-check 通过

---

# Workflow & DoD (Definition of Done)
为了确保代码质量，本任务必须严格遵循以下质量内循环，直至代码审查（CR）完全通过：

1. 🧪 【初步自测】
   - 开发者完成编码后，必须针对「交付物标准」进行本地功能自测或编写单元测试。

2. 🔍 【提交代码审查 (CR)】
   - 发起 Pull Request (PR) / Merge Request (MR)，邀请核心团队成员进行代码评审，并完整记录 CR 反馈意见。

3. 🛠️ 【修复 CR 意见 (Fix CR)】
   - 针对评审中发现的性能、安全、规范等问题逐项修复，禁止遗漏任何一条 blocking 意见。

4. 🔄 【回归测试】
   - 对修复后的代码再次进行本地回归测试，确保没有引入新的破坏性变更（Regression Bug）。

5. 🏁 【流程终止与提交条件】
   - **准出条件（DoD）**：只有当所有 CR 意见全部闭环、Reviewer 给出 Approved 状态，且回归测试 100% 通过时，方可执行最后一步。
   - **最终操作**：将代码正式 Commit 并合并至主分支，更新任务状态为"已完成"。

---

### TASK-006: [Management 前端] 修复 API 返回值类型 — updateBasicInfo/Visibility/Banner 接收 ProblemListSummaryVO

**[任务描述]**
修改 `management/src/api/admin/problem-lists.ts` 中 `updateBasicInfo`、`updateVisibility`、`updateBanner` 的返回值类型从 `Promise<void>` 改为 `Promise<ProblemList>`。同步修改 `createList` 返回值（类型名对齐 `ProblemList` vs `ProblemListSummaryVO`，结构一致无需改动）。在 Store 层添加对应的 action，利用返回值更新 `currentList` 状态。

**[前置依赖]**
无（纯前端类型修复，后端 API 无需变更）

**[参考文档]**
- `docs/problem-lists-api-granularity-analysis.md` — P1-3, Section 1.2.1
- `backend-spring/.../admin/controller/AdminProblemListController.java:93-100` — updateBasicInfo 返回 Result<ProblemListSummaryVO>

**[交付物标准]**
1. `management/src/api/admin/problem-lists.ts` 中：
   - `updateBasicInfo` 返回 `Promise<ProblemList>`（非 void）
   - `updateVisibility` 返回 `Promise<ProblemList>`（非 void）
   - `updateBanner` 返回 `Promise<ProblemList>`（非 void）
   - `createList` 返回值类型保持 `Promise<ProblemList>`（已正确）
2. `management/src/stores/admin/problem-lists.ts` 新增 actions：
   - `updateBasicInfo(id, data)` — 调用 API 后用返回值更新 `currentList`
   - `updateVisibility(id, data)` — 同上
   - `updateBanner(id, data)` — 同上
3. ESLint + type-check 通过

---

# Workflow & DoD (Definition of Done)
为了确保代码质量，本任务必须严格遵循以下质量内循环，直至代码审查（CR）完全通过：

1. 🧪 【初步自测】
   - 开发者完成编码后，必须针对「交付物标准」进行本地功能自测或编写单元测试。

2. 🔍 【提交代码审查 (CR)】
   - 发起 Pull Request (PR) / Merge Request (MR)，邀请核心团队成员进行代码评审，并完整记录 CR 反馈意见。

3. 🛠️ 【修复 CR 意见 (Fix CR)】
   - 针对评审中发现的性能、安全、规范等问题逐项修复，禁止遗漏任何一条 blocking 意见。

4. 🔄 【回归测试】
   - 对修复后的代码再次进行本地回归测试，确保没有引入新的破坏性变更（Regression Bug）。

5. 🏁 【流程终止与提交条件】
   - **准出条件（DoD）**：只有当所有 CR 意见全部闭环、Reviewer 给出 Approved 状态，且回归测试 100% 通过时，方可执行最后一步。
   - **最终操作**：将代码正式 Commit 并合并至主分支，更新任务状态为"已完成"。

---

### TASK-007: [Management 前端] 重构 Section 组件 — 利用 API 返回值替代乐观更新

**[任务描述]**
将 BasicInfoSection、VisibilitySection、BannerSection 三个组件从当前的"乐观本地更新"模式（手动构造 emit 对象）改为利用 API 返回的 `ProblemListSummaryVO` 更新状态。通过 Store 的 actions 调用 API，利用返回值更新 `store.currentList`，消除本地构造对象可能与后端不一致的风险。

**[前置依赖]**
TASK-006（Store actions 必须先添加，组件才能调用）

**[参考文档]**
- `docs/problem-lists-api-granularity-analysis.md` — P1-3
- `management/src/views/problem-lists/components/BasicInfoSection.vue:108-115` — 当前乐观更新模式
- `management/src/stores/admin/problem-lists.ts:82-95` — updateListProblems 的 re-fetch 模式参考

**[交付物标准]**
1. `BasicInfoSection.vue` — 调用 `store.updateBasicInfo(id, data)` 而非直接调 API，移除手动 `emit('update:modelValue', ...)` 构造
2. `VisibilitySection.vue` — `useAutoSave` 的回调改为调用 `store.updateVisibility(id, data)`，emit 返回的 VO 数据
3. `BannerSection.vue` — 调用 `store.updateBanner(id, data)`，emit 返回的 VO 数据
4. Store actions 内部逻辑：API 返回值 → 合并到 `currentList`（而非 re-fetch）
5. 三个组件在保存成功后，UI 状态从后端返回值刷新（确保与后端一致）
6. 保存失败时不更新本地状态（当前乐观更新模式会在失败时也尝试更新）
7. ESLint + type-check 通过

---

# Workflow & DoD (Definition of Done)
为了确保代码质量，本任务必须严格遵循以下质量内循环，直至代码审查（CR）完全通过：

1. 🧪 【初步自测】
   - 开发者完成编码后，必须针对「交付物标准」进行本地功能自测或编写单元测试。

2. 🔍 【提交代码审查 (CR)】
   - 发起 Pull Request (PR) / Merge Request (MR)，邀请核心团队成员进行代码评审，并完整记录 CR 反馈意见。

3. 🛠️ 【修复 CR 意见 (Fix CR)】
   - 针对评审中发现的性能、安全、规范等问题逐项修复，禁止遗漏任何一条 blocking 意见。

4. 🔄 【回归测试】
   - 对修复后的代码再次进行本地回归测试，确保没有引入新的破坏性变更（Regression Bug）。

5. 🏁 【流程终止与提交条件】
   - **准出条件（DoD）**：只有当所有 CR 意见全部闭环、Reviewer 给出 Approved 状态，且回归测试 100% 通过时，方可执行最后一步。
   - **最终操作**：将代码正式 Commit 并合并至主分支，更新任务状态为"已完成"。

---

### TASK-008: [Management 前端] 修正 DTO 字段不对齐 — CreateProblemListDto/UpdateBasicInfoDto

**[任务描述]**
修正 Management 前端 DTO 类型与后端的不对齐项：
1. `CreateProblemListDto` 移除 `isFeatured` 字段（后端 `CreateProblemListDTO` 无此字段，admin 通过单独的 visibility 端点设置）
2. `UpdateBasicInfoDto.name` 从 `string?` 改为 `string`（后端 `UpdateBasicInfoDTO` 的 `name` 是必填的 `@NotBlank`）
3. 确认前端 `ProblemList.description` 改为 `string | null`（后端可为 null），`bannerOrder` 改为 `number | null`

**[前置依赖]**
无（纯前端类型修复）

**[参考文档]**
- `docs/problem-lists-api-granularity-analysis.md` — P1-5, P1-6, P2-8, P2-9
- `backend-spring/.../problemlist/dto/CreateProblemListDTO.java` — 后端 DTO 定义
- `backend-spring/.../problemlist/dto/UpdateBasicInfoDTO.java` — 后端 DTO 定义

**[交付物标准]**
1. `management/src/api/admin/problem-lists.ts` 中 `CreateProblemListDto` 移除 `isFeatured` 字段
2. `UpdateBasicInfoDto.name` 类型改为 `string`（非 optional）
3. `ProblemList` 接口中 `description` 改为 `string | null`
4. `ProblemList` 接口中 `bannerOrder` 改为 `number | null`
5. `ProblemListDetail` 接口中 `isOwner` 改为 `boolean | null`（后端 Admin 详情未返回此字段，设为 nullable）
6. 更新消费这些字段的组件，确保 null 安全
7. ESLint + type-check 通过

---

# Workflow & DoD (Definition of Done)
为了确保代码质量，本任务必须严格遵循以下质量内循环，直至代码审查（CR）完全通过：

1. 🧪 【初步自测】
   - 开发者完成编码后，必须针对「交付物标准」进行本地功能自测或编写单元测试。

2. 🔍 【提交代码审查 (CR)】
   - 发起 Pull Request (PR) / Merge Request (MR)，邀请核心团队成员进行代码评审，并完整记录 CR 反馈意见。

3. 🛠️ 【修复 CR 意见 (Fix CR)】
   - 针对评审中发现的性能、安全、规范等问题逐项修复，禁止遗漏任何一条 blocking 意见。

4. 🔄 【回归测试】
   - 对修复后的代码再次进行本地回归测试，确保没有引入新的破坏性变更（Regression Bug）。

5. 🏁 【流程终止与提交条件】
   - **准出条件（DoD）**：只有当所有 CR 意见全部闭环、Reviewer 给出 Approved 状态，且回归测试 100% 通过时，方可执行最后一步。
   - **最终操作**：将代码正式 Commit 并合并至主分支，更新任务状态为"已完成"。

---

### TASK-009: [Console 前端] 清理 ProblemList 类型多余字段 — 移除 favoritesCount/categoryId

**[任务描述]**
Console 前端 `ProblemList` 类型中包含 `favoritesCount` 和 `categoryId` 字段，但后端 `ProblemListSummaryVO` 不返回这些字段。移除前端类型中的这两个多余字段，更新 mapper 中对这些字段的映射逻辑，确保前端类型定义与后端 VO 完全一致。

**[前置依赖]**
TASK-003（Response 字段名统一后，再清理多余字段避免冲突）

**[参考文档]**
- `docs/problem-lists-api-granularity-analysis.md` — P2-7
- `backend-spring/.../problemlist/dto/ProblemListSummaryVO.java` — 后端 VO 定义（无 favoritesCount、categoryId）

**[交付物标准]**
1. `console/src/types/problem-list.ts` 中 `ProblemList` 接口移除 `favoritesCount` 和 `categoryId` 字段
2. `console/src/api/problem-list.ts` 中 `BackendProblemList` 移除对应的 snake_case/camelCase 声明
3. `mapProblemList` mapper 移除 `favoritesCount`/`favorites_count` 映射逻辑
4. `mapProblemListItem` mapper 移除 `favoritesCount`/`favorites_count` 映射逻辑
5. 检查所有消费 `favoritesCount` 或 `categoryId` 的组件，移除或替换相关 UI 元素
6. ESLint + type-check 通过

---

# Workflow & DoD (Definition of Done)
为了确保代码质量，本任务必须严格遵循以下质量内循环，直至代码审查（CR）完全通过：

1. 🧪 【初步自测】
   - 开发者完成编码后，必须针对「交付物标准」进行本地功能自测或编写单元测试。

2. 🔍 【提交代码审查 (CR)】
   - 发起 Pull Request (PR) / Merge Request (MR)，邀请核心团队成员进行代码评审，并完整记录 CR 反馈意见。

3. 🛠️ 【修复 CR 意见 (Fix CR)】
   - 针对评审中发现的性能、安全、规范等问题逐项修复，禁止遗漏任何一条 blocking 意见。

4. 🔄 【回归测试】
   - 对修复后的代码再次进行本地回归测试，确保没有引入新的破坏性变更（Regression Bug）。

5. 🏁 【流程终止与提交条件】
   - **准出条件（DoD）**：只有当所有 CR 意见全部闭环、Reviewer 给出 Approved 状态，且回归测试 100% 通过时，方可执行最后一步。
   - **最终操作**：将代码正式 Commit 并合并至主分支，更新任务状态为"已完成"。

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| ProblemListController 认证迁移 — 未登录访问受保护端点 | 无 JWT cookie | 401 UNAUTHORIZED | Yes |
| ProblemListController 认证迁移 — 已登录访问受保护端点 | 有效 JWT | 正常返回数据 | No |
| ProblemListController — overview 未登录 | 无 JWT | 返回公开列表 | Yes |
| Management API 返回值 — updateBasicInfo | { name: "new" } | ProblemList 对象 | No |
| Console mapper — UserProblemListsResponse | { ownLists, featuredLists } | 正确映射字段 | No |
| Console mapper — ProblemListDetailResponse (含 stats) | { stats: { totalCount: 10 } } | stats.totalCount === 10 | No |

### Edge Cases Checklist
- [x] 未登录用户访问 ProblemListController 受保护端点 → 401
- [x] 未登录用户访问 overview → 返回公开列表
- [x] JWT 过期 → 401
- [x] Management updateBasicInfo name 为空 → 后端 400 验证失败
- [x] Management updateBasicInfo name 缺失 → 后端 400 验证失败（name 必填）
- [x] Console ProblemListDetailResponse 中 stats 为 null → 前端不崩溃
- [x] CreateProblemListDto 移除 isFeatured 后，创建表单不显示 isFeatured 开关

---

## Validation Commands

### Static Analysis (Backend)
```bash
cd backend-spring && ./mvnw compile
```
EXPECT: Zero compilation errors

### Static Analysis (Console Frontend)
```bash
cd console && pnpm lint && pnpm type-check
```
EXPECT: Zero lint errors, zero type errors

### Static Analysis (Management Frontend)
```bash
cd management && pnpm lint && pnpm type-check
```
EXPECT: Zero lint errors, zero type errors

### Unit Tests (Backend)
```bash
cd backend-spring && ./mvnw test
```
EXPECT: All tests pass

### Unit Tests (Console Frontend)
```bash
cd console && pnpm test
```
EXPECT: All tests pass

### Unit Tests (Management Frontend)
```bash
cd management && pnpm test
```
EXPECT: All tests pass

### E2E Tests (Management)
```bash
cd management && pnpm exec playwright test --grep "problem-list"
```
EXPECT: All E2E tests pass

### Browser Validation
```bash
# 启动 dev server
pm2 restart ulticode-9001 ulticode-9002 ulticode-9003
```
验证：
1. Console `/personal/problem-lists` — 列表加载正常，CRUD 正常
2. Console `/problemset/list/:id` — 详情页加载正常，stats 显示
3. Management `/problem-lists` — 列表加载正常
4. Management `/problem-lists/:id/edit` — auto-save 正常，保存后状态一致

---

## Acceptance Criteria
- [ ] 所有 9 个 TASK 的交付物标准达成
- [ ] ProblemListController 不再使用 @RequestParam userId
- [ ] Console 前端不再传递 userId query param
- [ ] Console Response 字段名与后端 VO 一致（ownLists, featuredLists）
- [ ] Management updateBasicInfo/Visibility/Banner 返回 ProblemList
- [ ] Management Section 组件利用 API 返回值更新状态
- [ ] Management DTO 字段与后端完全对齐
- [ ] Console ProblemList 类型无后端不返回的字段
- [ ] 所有 validation commands 通过

## Completion Checklist
- [ ] 代码遵循 SecurityUtil 认证模式
- [ ] 错误处理与 SubmissionController 一致（BusinessException + ErrorCode.UNAUTHORIZED）
- [ ] 前端类型与后端 VO/DTO 完全对齐
- [ ] 无 console.log 或调试语句
- [ ] 无硬编码值
- [ ] 无不必要的 scope 增加

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ProblemListController 迁移导致前端暂时无法调用 API | Medium | High | TASK-001 和 TASK-002 必须**同一 PR** 或**紧密衔接**提交 |
| 后端 stats 计算逻辑依赖 submission 数据，可能需要跨表查询 | Medium | Medium | 先实现基本框架（total/solved/todo），后续迭代添加精确统计 |
| Console 组件大量消费 myLists/featured 字段，重命名影响面大 | Low | Medium | 使用 IDE 重命名功能确保不遗漏 |
| Management Store 新增 actions 后，组件需要从直接调 API 改为调 Store | Low | Low | 逐组件迁移，保持向后兼容 |

## Notes
- TASK-001 和 TASK-002 有严格的时序依赖，建议合并在同一 feature branch 中完成
- TASK-003 可与 TASK-001/002 并行执行（无代码依赖）
- TASK-004 的 stats 计算可能需要额外查询 submission 表，需评估性能影响
- TASK-008 是纯前端类型修复，风险最低，可优先完成
- 后端 `ProblemListBookmark` 和 `ProblemListCategory` 对应的表在部分环境可能不存在（Service 层有 catch 块），TASK-004 中 viewer/categories 的填充需要处理这种降级情况
