# Plan: Problem-Lists 全模块前后端颗粒度对齐

## Summary
对齐 problem-lists 模块中 console 前端、management 前端与 Spring Boot 后端之间的类型定义、DTO 校验规则、API 返回字段名和 mapper 映射。修复 6 个 CRITICAL、4 个 HIGH、4 个 MEDIUM 级别的不对齐问题。

## User Story
As a 全栈工程师, I want 前后端 API 类型与字段完全对齐, so that 运行时不会出现 undefined 字段、校验不一致导致的保存失败、或字段名不匹配导致的功能异常。

## Problem → Solution
**Current**: Console 前端类型缺少后端已返回字段、fork 返回字段名不匹配、description 校验长度不一致、getUserListsForProblem 返回类型严重不对齐
**Desired**: 三端类型定义完全对齐，DTO 校验规则统一，API 返回字段名一致，mapper 正确映射所有后端字段

## Metadata
- **Complexity**: Large
- **Source PRD**: `docs/alignment/problem-lists-full-frontend-backend-alignment.md`
- **PRD Phase**: N/A
- **Estimated Files**: 14

---

## UX Design

### Before
N/A — 内部 API 对齐，无用户可见 UX 变化（但修复后 fork 跳转、作者信息显示等功能将正常工作）

### After
N/A — 内部 API 对齐

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Management 题单列表 | 无作者列 | 显示 authorName 列 | 管理员可看到题单作者 |
| Management ProblemsManager | 无 status/addedAt 列 | 显示 status 和 addedAt 列 | 信息更完整 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/.../problemlist/dto/CreateProblemListDTO.java` | all | 需修改 description @Size |
| P0 | `backend-spring/.../problemlist/dto/ForkResultVO.java` | all | 需修改 newListId → id |
| P0 | `backend-spring/.../problemlist/dto/UserListsForProblemVO.java` | all | 需扩展 ListStatusVO |
| P0 | `backend-spring/.../problemlist/controller/ProblemListController.java` | 77-86 | 需修改 fork 返回构造 |
| P0 | `backend-spring/.../problemlist/service/impl/ProblemListServiceImpl.java` | 449-467 | 需扩展 getUserListsForProblem |
| P0 | `console/src/types/problem-list.ts` | all | 需补全类型字段 |
| P0 | `console/src/types/problem.ts` | all | 需添加 sortOrder/addedAt |
| P0 | `console/src/api/problem-list.ts` | all | 需更新 mapper |
| P1 | `management/src/api/admin/problem-lists.ts` | all | 需移除死代码 |
| P1 | `management/src/views/problem-lists/columns.ts` | all | 需添加作者列 |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| 无需外部文档 | — | 所有修改基于已分析的项目内部模式 |

---

## Patterns to Mirror

### DTO_VALIDATION_PATTERN
// SOURCE: backend-spring/.../problemlist/dto/UpdateBasicInfoDTO.java
```java
@NotBlank(message = "Name is required")
@Size(max = 100, message = "Name must not exceed 100 characters")
private String name;

@Size(max = 500, message = "Description must not exceed 500 characters")
private String description;
```

### VO_FIELD_PATTERN
// SOURCE: backend-spring/.../problemlist/dto/ProblemListSummaryVO.java
```java
@Data
public class ProblemListSummaryVO {
    private String id;
    private String name;
    // ... all fields with private + getters/setters via Lombok
}
```

### CONSOLE_MAPPER_PATTERN
// SOURCE: console/src/api/problem-list.ts:88-171
```typescript
function mapProblemList(input: unknown): ProblemList {
  if (!input || typeof input !== "object") {
    return { id: "", name: "", description: undefined, problemCount: 0, favoritesCount: 0 };
  }
  const raw = input as BackendProblemList;
  // ... type narrowing with typeof checks for each field
  return {
    id: String(raw.id ?? ""),
    name: String(raw.name ?? ""),
    // ... camelCase + snake_case dual mapping
  };
}
```

### CONSOLE_TYPE_PATTERN
// SOURCE: console/src/types/problem-list.ts
```typescript
export interface ProblemList {
  id: string;
  name: string;
  description?: string;  // optional fields use ? suffix
  // ...
}
```

### MANAGEMENT_API_PATTERN
// SOURCE: management/src/api/admin/problem-lists.ts:102-141
```typescript
export const adminProblemListsApi = {
  async updateBasicInfo(id: string, data: UpdateBasicInfoDto): Promise<void> {
    await apiPatch(`/admin/problem-lists/${id}/basic-info`, data)
  },
}
```

### TEST_PATTERN
// SOURCE: management/src/views/problem-lists/components/BasicInfoSection.test.ts
```typescript
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
// ... uses vi.mock for API calls, mount for component rendering
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/.../dto/CreateProblemListDTO.java` | UPDATE | 统一 description @Size 为 500 |
| `backend-spring/.../dto/ForkResultVO.java` | UPDATE | newListId → id |
| `backend-spring/.../dto/UserListsForProblemVO.java` | UPDATE | ListStatusVO 添加 problemCount、canEdit |
| `backend-spring/.../controller/ProblemListController.java` | UPDATE | fork 构造适配新 ForkResultVO |
| `backend-spring/.../service/impl/ProblemListServiceImpl.java` | UPDATE | getUserListsForProblem 填充新字段 |
| `console/src/types/problem-list.ts` | UPDATE | 补全 authorName/authorUsername/isOwner/category 新字段 |
| `console/src/types/problem.ts` | UPDATE | 添加 sortOrder/addedAt |
| `console/src/api/problem-list.ts` | UPDATE | 更新 mapper + 适配 hasProblem/fork 返回 |
| `management/src/api/admin/problem-lists.ts` | UPDATE | 移除 updateList 死代码 |
| `management/src/views/problem-lists/columns.ts` | UPDATE | 添加作者列 |
| `management/src/views/problem-lists/components/ProblemsManager.vue` | UPDATE | 添加 status/addedAt 列 |
| `management/src/i18n/locales/en-US/modules/problemLists.ts` | UPDATE | 添加 author/status/addedAt 列 i18n |
| `management/src/i18n/locales/zh-CN/modules/problemLists.ts` | UPDATE | 添加 author/status/addedAt 列 i18n |
| `management/src/i18n/locales/en-US/modules/table.ts` | UPDATE | 添加 DataTable 列名 i18n |
| `management/src/i18n/locales/zh-CN/modules/table.ts` | UPDATE | 添加 DataTable 列名 i18n |

## NOT Building

- Console 侧细粒度 PATCH 端点（用户编辑是一次性保存，不需要按区块自动保存）
- Console createCategory/updateCategory 发送 description/icon/color（当前 UI 无对应输入，P2 延后）
- 后端 ProblemListSummaryVO 添加 favoritesCount 字段（需新增 bookmark_count 查询，P2 延后）
- 后端 ProblemListDetailVO 移除 stats 字段（Console 客户端自行计算，保留不影响）

---

## Step-by-Step Tasks

### Task 1: 后端统一 description 校验长度为 500
- **ACTION**: 修改 `CreateProblemListDTO.java` 的 `description` 字段 `@Size(max)` 从 1000 改为 500
- **IMPLEMENT**: 将 `@Size(max = 1000, message = "Description must not exceed 1000 characters")` 改为 `@Size(max = 500, message = "Description must not exceed 500 characters")`
- **MIRROR**: DTO_VALIDATION_PATTERN
- **IMPORTS**: 无变更
- **GOTCHA**: 确认前端 Console 和 Management 的 description 输入框 maxlength 是否需要同步调整
- **VALIDATE**: `./mvnw compile` 通过；确认 UpdateProblemListDTO 和 UpdateBasicInfoDTO 已是 500

### Task 2: 后端 ForkResultVO 字段名对齐
- **ACTION**: 将 `ForkResultVO.newListId` 重命名为 `id`，同步修改 Controller 中的构造调用
- **IMPLEMENT**:
  1. `ForkResultVO.java`: `private String newListId;` → `private String id;`
  2. `ProblemListController.java:85`: `new ForkResultVO(newListId)` → `new ForkResultVO(newListId)` （Lombok @AllArgsConstructor 按字段顺序，改名后构造参数语义不变）
- **MIRROR**: VO_FIELD_PATTERN
- **IMPORTS**: 无变更
- **GOTCHA**: ForkResultVO 使用 @AllArgsConstructor，字段改名后构造函数参数名变化，但位置不变。确认无其他代码引用 `newListId` 字段名
- **VALIDATE**: `./mvnw compile` 通过；`grep -rn "newListId" backend-spring/` 返回 0 结果

### Task 3: 后端 ListStatusVO 扩展字段
- **ACTION**: 在 `UserListsForProblemVO.ListStatusVO` 中添加 `problemCount` (Integer) 和 `canEdit` (Boolean) 字段，并在 ServiceImpl 中填充
- **IMPLEMENT**:
  1. `UserListsForProblemVO.java` ListStatusVO 添加:
     ```java
     private Integer problemCount;
     private Boolean canEdit;
     ```
  2. `ProblemListServiceImpl.java` getUserListsForProblem 方法中，在 map lambda 内添加:
     ```java
     status.setProblemCount((int) problemListProblemMapper.countByListId(list.getId()));
     status.setCanEdit(list.getAuthorId().equals(userId));
     ```
- **MIRROR**: VO_FIELD_PATTERN + ProblemListServiceImpl 现有 problemCount 查询模式（line 599）
- **IMPORTS**: 无新增（problemListProblemMapper 已注入）
- **GOTCHA**: `countByListId` 方法需确认存在于 `ProblemListProblemMapper`。如不存在需添加。`canEdit` 逻辑：仅作者可编辑
- **VALIDATE**: `./mvnw compile` 通过；调用 GET `/problem-lists/problems/{problemId}/user-lists` 验证返回包含 problemCount 和 canEdit

### Task 4: Console 类型补全 — ProblemList 和 ProblemListDetailResponse
- **ACTION**: 在 `console/src/types/problem-list.ts` 中补全缺失字段
- **IMPLEMENT**:
  1. `ProblemList` 接口添加: `authorName?: string; authorUsername?: string;`
  2. `ProblemList.favoritesCount` 从 `number` 改为 `number?`（后端不返回，改为可选）
  3. `ProblemListDetailResponse` 接口添加: `isOwner?: boolean;`
  4. `ProblemListCategory` 接口添加: `description?: string; icon?: string; color?: string; listCount?: number;`
- **MIRROR**: CONSOLE_TYPE_PATTERN
- **IMPORTS**: 无变更
- **GOTCHA**: `favoritesCount` 改为可选后，需检查所有使用处是否处理了 undefined。当前 `ProblemSaveButton.vue:405` 直接渲染 `list.favoritesCount`，需添加 fallback
- **VALIDATE**: `pnpm type-check` 通过

### Task 5: Console 类型补全 — Problem 添加 sortOrder/addedAt
- **ACTION**: 在 `console/src/types/problem.ts` 的 `Problem` 接口添加 `sortOrder` 和 `addedAt`
- **IMPLEMENT**:
  ```typescript
  export interface Problem {
    // ... 现有字段 ...
    sortOrder?: number;
    addedAt?: string;
  }
  ```
- **MIRROR**: CONSOLE_TYPE_PATTERN
- **IMPORTS**: 无变更
- **GOTCHA**: Problem 类型在多处使用（题目列表、搜索结果等），sortOrder/addedAt 仅在题单详情中有值，其他场景为 undefined，使用 ? 可选标记
- **VALIDATE**: `pnpm type-check` 通过

### Task 6: Console mapper 更新 — mapProblemList 和 mapProblemListItem
- **ACTION**: 更新 `console/src/api/problem-list.ts` 中的 mapper 函数，添加 authorName/authorUsername 映射，适配 fork 返回字段名
- **IMPLEMENT**:
  1. `BackendProblemList` 接口添加: `authorName?: unknown; author_username?: unknown; authorUsername?: unknown;`
  2. `mapProblemList` 函数返回值添加:
     ```typescript
     authorName: typeof raw.authorName === 'string' ? raw.authorName : undefined,
     authorUsername:
       typeof raw.authorUsername === 'string'
         ? raw.authorUsername
         : typeof raw.author_username === 'string'
           ? raw.author_username
           : undefined,
     ```
  3. `mapProblemListItem` 同样添加 authorName/authorUsername 映射
  4. `forkProblemList` 函数修改返回值适配:
     ```typescript
     const res = await apiPost<{ id: string }>(`/problem-lists/${listId}/fork?userId=${userId}`)
     return res.id
     ```
     （后端 ForkResultVO 已改为 `id` 字段，前端原本期望 `id`，现在对齐）
- **MIRROR**: CONSOLE_MAPPER_PATTERN
- **IMPORTS**: 无变更
- **GOTCHA**: fork 返回类型从 `{ id: string }` 不变（因为后端改了字段名来对齐前端），但需确认 API 响应结构是 `Result<ForkResultVO>`，实际 data 是 `{ id: "xxx" }`
- **VALIDATE**: `pnpm type-check` 通过；`pnpm test` 通过

### Task 7: Console mapper 更新 — getUserListsForProblem 适配 hasProblem
- **ACTION**: 修改 `getUserListsForProblem` 函数，适配后端 `hasProblem` → 前端 `containsProblem`，以及新增的 `problemCount`/`canEdit`
- **IMPLEMENT**:
  ```typescript
  export async function getUserListsForProblem(
    userId: string,
    problemId: number,
  ): Promise<ProblemListWithStatus[]> {
    const data = await apiGet<unknown>(
      `/problem-lists/problems/${problemId}/user-lists?userId=${userId}`,
    )
    if (!data || typeof data !== 'object') {
      return []
    }
    const raw = data as { lists?: unknown[] }
    const lists = Array.isArray(raw.lists) ? raw.lists : []
    return lists.map((item: unknown) => {
      const obj = item as Record<string, unknown>
      return {
        ...mapProblemList(item),
        containsProblem: Boolean(obj.hasProblem),
        canEdit: Boolean(obj.canEdit),
        problemCount: typeof obj.problemCount === 'number' ? obj.problemCount : 0,
      }
    })
  }
  ```
- **MIRROR**: CONSOLE_MAPPER_PATTERN
- **IMPORTS**: 无变更
- **GOTCHA**: 后端 `UserListsForProblemVO` 顶层有 `problemId` 和 `lists` 数组。当前前端直接将整个响应当数组处理，需改为从 `data.lists` 提取。同时 `ProblemListWithStatus` 接口需添加 `problemCount` 字段
- **VALIDATE**: `pnpm type-check` 通过；`pnpm test` 通过

### Task 8: Console mapper 更新 — mapCategory 补全字段
- **ACTION**: 更新 `mapCategory` 函数，映射 `description`/`icon`/`color`/`listCount`
- **IMPLEMENT**:
  1. `BackendProblemListCategory` 接口添加: `description?: unknown; icon?: unknown; color?: unknown; listCount?: unknown; list_count?: unknown;`
  2. `mapCategory` 返回值添加:
     ```typescript
     description: typeof raw.description === 'string' ? raw.description : undefined,
     icon: typeof raw.icon === 'string' ? raw.icon : undefined,
     color: typeof raw.color === 'string' ? raw.color : undefined,
     listCount: typeof raw.listCount === 'number' ? raw.listCount : typeof raw.list_count === 'number' ? raw.list_count : undefined,
     ```
- **MIRROR**: CONSOLE_MAPPER_PATTERN
- **IMPORTS**: 无变更
- **GOTCHA**: 后端 `CategorySummaryVO` 有 `userId`、`createdAt`、`updatedAt` 字段，Console 不需要，不映射
- **VALIDATE**: `pnpm type-check` 通过

### Task 9: Console mapper 更新 — fetchProblemListOverview 提取 isOwner
- **ACTION**: 在 `fetchProblemListOverview` 的 mapper 中，从 `ProblemListDetailVO` 顶层提取 `isOwner` 字段到 `ProblemListDetailResponse`
- **IMPLEMENT**:
  ```typescript
  // 在 fetchProblemListOverview 函数中
  return {
    list: raw.list ? mapProblemList(raw.list) : null,
    problems: Array.isArray(raw.problems) ? raw.problems.map(mapProblem) : [],
    stats: raw.stats && typeof raw.stats === 'object' ? (raw.stats as ProblemListStats) : null,
    isOwner: typeof (raw as Record<string, unknown>).isOwner === 'boolean' ? (raw as Record<string, unknown>).isOwner as boolean : undefined,
    viewer: ...,
    categories: ...,
  }
  ```
- **MIRROR**: CONSOLE_MAPPER_PATTERN
- **IMPORTS**: 无变更
- **GOTCHA**: 后端 `ProblemListDetailVO` 的 `isOwner` 在顶层，不在 `list` 嵌套对象中。需从 raw 顶层提取
- **VALIDATE**: `pnpm type-check` 通过

### Task 10: Console ProblemSaveButton favoritesCount fallback
- **ACTION**: 在 `ProblemSaveButton.vue` 中为 `favoritesCount` 添加 undefined fallback
- **IMPLEMENT**: 将 `{{ list.favoritesCount }}` 改为 `{{ list.favoritesCount ?? 0 }}`
- **MIRROR**: Vue 模板中的可选字段处理模式
- **IMPORTS**: 无变更
- **GOTCHA**: `favoritesCount` 现为 `number?`，直接渲染可能显示空白
- **VALIDATE**: `pnpm type-check` 通过

### Task 11: Management 移除 updateList 死代码
- **ACTION**: 从 `management/src/api/admin/problem-lists.ts` 中移除 `updateList` 方法和 `UpdateProblemListDto` 接口（如无其他引用）
- **IMPLEMENT**:
  1. 移除 `updateList` 方法
  2. 检查 `UpdateProblemListDto` 是否被其他文件引用，如无则移除
  3. 检查 store 中是否有 `updateList` action，如有则移除
- **MIRROR**: MANAGEMENT_API_PATTERN
- **IMPORTS**: 移除后需确认无编译错误
- **GOTCHA**: 先 `grep -rn "updateList\|UpdateProblemListDto" management/src/` 确认无引用
- **VALIDATE**: `pnpm type-check` 通过

### Task 12: Management 列表页添加作者列
- **ACTION**: 在 `management/src/views/problem-lists/columns.ts` 中添加 `authorName` 列
- **IMPLEMENT**: 在 `isPublic` 列之前插入:
  ```typescript
  {
    accessorKey: 'authorName',
    header: () =>
      h(
        'span',
        { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
        t('problemLists.columns.author'),
      ),
    cell: ({ row }) => {
      const name = row.original.authorName
      return h(
        'span',
        { class: 'font-data text-xs text-[var(--silver-400)]' },
        name || '-',
      )
    },
  },
  ```
- **MIRROR**: columns.ts 中现有列定义模式
- **IMPORTS**: 无新增
- **GOTCHA**: 需同步添加 i18n key `problemLists.columns.author`
- **VALIDATE**: 页面渲染正常，作者列显示

### Task 13: Management ProblemsManager 添加 status/addedAt 列
- **ACTION**: 在 `ProblemsManager.vue` 的表格中添加 `status` 和 `addedAt` 列
- **IMPLEMENT**: 在现有表格列定义中添加 status 和 addedAt 列，使用 formatDate 格式化 addedAt
- **MIRROR**: ProblemsManager.vue 中现有列定义模式
- **IMPORTS**: 需 import `formatDate` from `@/lib/format/date`
- **GOTCHA**: status 字段为字符串（"solved"/"attempted"/"todo"），需渲染为 badge
- **VALIDATE**: 页面渲染正常，新列显示

### Task 14: Management i18n 添加新列翻译
- **ACTION**: 在 en-US 和 zh-CN 的 problemLists.ts 和 table.ts 中添加新列翻译 key
- **IMPLEMENT**:
  1. `problemLists.ts` en-US: `columns.author: 'Author'`, `columns.status: 'Status'`, `columns.addedAt: 'Added'`
  2. `problemLists.ts` zh-CN: `columns.author: '作者'`, `columns.status: '状态'`, `columns.addedAt: '添加时间'`
  3. `table.ts` en-US/zh-CN: 添加 `authorName`/`authorName` 等 DataTable columnNames key
- **MIRROR**: 现有 i18n 文件结构
- **IMPORTS**: 无新增
- **GOTCHA**: DataTable 使用 `t(\`table.columnNames.${column.id}\`)` 渲染列头，需确保 table.ts 中有对应 key
- **VALIDATE**: 页面列头显示正确翻译

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| mapProblemList 含 authorName | `{ authorName: "Alice" }` | `authorName: "Alice"` | ✅ |
| mapProblemList 无 authorName | `{}` | `authorName: undefined` | ✅ |
| mapProblemList 含 author_username (snake_case) | `{ author_username: "alice" }` | `authorUsername: "alice"` | ✅ |
| forkProblemList 返回 { id: "123" } | `{ id: "123" }` | `"123"` | ✅ |
| getUserListsForProblem 适配 hasProblem | `{ lists: [{ hasProblem: true }] }` | `containsProblem: true` | ✅ |
| getUserListsForProblem 适配 problemCount | `{ lists: [{ problemCount: 5 }] }` | `problemCount: 5` | ✅ |
| CreateProblemListDTO description max 500 | 501 字符 | 校验失败 | ✅ |

### Edge Cases Checklist
- [x] authorName/authorUsername 为 null → undefined
- [x] favoritesCount 为 undefined → 显示 0
- [x] fork 返回空 id → 空字符串
- [x] getUserListsForProblem 响应无 lists 数组 → 返回空数组
- [x] category 无 description/icon/color → undefined
- [x] description 恰好 500 字符 → 校验通过
- [x] description 501 字符 → 校验失败

---

## Validation Commands

### Static Analysis
```bash
cd console && pnpm type-check
cd management && pnpm type-check
cd backend-spring && ./mvnw compile -q
```
EXPECT: Zero type errors, zero compilation errors

### Unit Tests
```bash
cd console && pnpm test
cd management && pnpm test
cd backend-spring && ./mvnw test -q
```
EXPECT: All tests pass

### Full Test Suite
```bash
cd console && pnpm test:coverage
cd management && pnpm test
cd backend-spring && ./mvnw test
```
EXPECT: No regressions

### Manual Validation
- [ ] Console: fork 题单后正确跳转到新题单详情页
- [ ] Console: ProblemSaveButton 显示题单列表时 containsProblem 状态正确
- [ ] Console: 题单详情页 isOwner 判断正确（显示/隐藏编辑按钮）
- [ ] Management: 题单列表页显示作者列
- [ ] Management: ProblemsManager 显示 status 和 addedAt 列
- [ ] 后端: 创建题单 description 超 500 字符返回 400

---

## Acceptance Criteria
- [ ] 所有 14 个 Task 完成
- [ ] 所有 validation commands 通过
- [ ] Console `pnpm type-check` 零错误
- [ ] Management `pnpm type-check` 零错误
- [ ] Backend `./mvnw compile` 零错误
- [ ] 无 lint 错误
- [ ] 对齐报告中的 6 个 CRITICAL 问题全部修复
- [ ] 对齐报告中的 4 个 HIGH 问题全部修复

## Completion Checklist
- [ ] 代码遵循已发现的 patterns
- [ ] Error handling 与 codebase 风格一致
- [ ] mapper 使用 typeof 类型守卫
- [ ] i18n 双语完整
- [ ] 无硬编码值
- [ ] 无不必要的范围扩展
- [ ] 自包含 — 实施期间无需额外搜索 codebase

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `problemListProblemMapper.countByListId` 方法不存在 | Low | Medium | Task 3 中先 grep 确认，如不存在需添加 mapper 方法 |
| Console `Problem` 添加 sortOrder/addedAt 影响其他使用 Problem 的页面 | Low | Low | 使用可选字段 `?`，不影响现有渲染 |
| fork 返回字段名修改影响已部署客户端 | Medium | High | ForkResultVO 是新功能，使用率低；如需兼容可同时支持 id 和 newListId |
| favoritesCount 改为可选后 ProblemSaveButton 显示异常 | Low | Low | Task 10 添加 ?? 0 fallback |

## Notes
- 后端 `ProblemListServiceImpl.forkList()` 返回 String，Controller 层包装为 ForkResultVO。修改 ForkResultVO 字段名不影响 Service 层。
- 后端 `getUserListsForProblem` 当前返回 `UserListsForProblemVO`（含 problemId + lists 数组），但 Console 前端直接将整个响应当数组处理。这是一个潜在 bug，Task 7 将修复。
- Management 的 `updateList` 死代码移除后，如未来需要通用 PATCH 可重新添加。
- `favoritesCount` 在后端完全不存在（非数据库字段、非 VO 字段），Console mapper 始终 fallback 为 0。这是设计缺失而非对齐问题，P2 延后。

---

## WBS 任务分解（TPM 标准化输出）

---

### TASK-001: [Backend-DTO] 统一 description 校验长度为 500

**[任务描述]**
修改 `CreateProblemListDTO.java` 的 `description` 字段 `@Size(max)` 从 1000 改为 500，与 `UpdateProblemListDTO` 和 `UpdateBasicInfoDTO` 保持一致。解决用户创建 800 字符描述后无法编辑保存的 CRITICAL 问题 C1。

**[前置依赖]**
无

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第三节 3.1

**[交付物标准]**
- `CreateProblemListDTO.java` 的 `description` 字段 `@Size(max=500)`
- `./mvnw compile` 通过
- 创建题单 description 超 500 字符时返回 400 Bad Request

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

### TASK-002: [Backend-VO] ForkResultVO 字段名对齐 newListId → id

**[任务描述]**
将 `ForkResultVO.newListId` 重命名为 `id`，同步修改 `ProblemListController` 中的构造调用。解决 CRITICAL 问题 C5：fork 后前端无法正确获取新题单 ID 导致跳转失败。

**[前置依赖]**
无

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第三节 3.6

**[交付物标准]**
- `ForkResultVO.java` 字段名为 `id`
- `ProblemListController.java` fork 端点构造 `new ForkResultVO(id)` 编译通过
- `grep -rn "newListId" backend-spring/` 返回 0 结果
- `./mvnw compile` 通过

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

### TASK-003: [Backend-VO] ListStatusVO 扩展 problemCount 和 canEdit 字段

**[任务描述]**
在 `UserListsForProblemVO.ListStatusVO` 中添加 `problemCount` (Integer) 和 `canEdit` (Boolean) 字段，并在 `ProblemListServiceImpl.getUserListsForProblem` 方法中填充这两个字段的值。解决 CRITICAL 问题 C6。

**[前置依赖]**
无

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第二节 2.4

**[交付物标准]**
- `UserListsForProblemVO.java` ListStatusVO 含 `problemCount` 和 `canEdit` 字段
- `ProblemListServiceImpl.java` getUserListsForProblem 方法正确填充新字段
- `./mvnw compile` 通过
- GET `/problem-lists/problems/{problemId}/user-lists` 响应包含 `problemCount` 和 `canEdit`

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

### TASK-004: [Console-Types] 补全 ProblemList 和 ProblemListDetailResponse 类型

**[任务描述]**
在 `console/src/types/problem-list.ts` 中补全缺失字段：`ProblemList` 添加 `authorName`/`authorUsername`，`favoritesCount` 改为可选，`ProblemListDetailResponse` 添加 `isOwner`，`ProblemListCategory` 添加 `description`/`icon`/`color`/`listCount`。解决 CRITICAL 问题 C2、C3 和 HIGH 问题 H1、H2、H3。

**[前置依赖]**
无

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第二节 2.1、2.2、2.5

**[交付物标准]**
- `ProblemList` 接口含 `authorName?: string`、`authorUsername?: string`、`favoritesCount?: number`
- `ProblemListDetailResponse` 接口含 `isOwner?: boolean`
- `ProblemListCategory` 接口含 `description?`/`icon?`/`color?`/`listCount?`
- `pnpm type-check` 通过

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

### TASK-005: [Console-Types] Problem 添加 sortOrder 和 addedAt

**[任务描述]**
在 `console/src/types/problem.ts` 的 `Problem` 接口添加 `sortOrder?: number` 和 `addedAt?: string`。解决 CRITICAL 问题 C4。

**[前置依赖]**
无

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第二节 2.3

**[交付物标准]**
- `Problem` 接口含 `sortOrder?: number` 和 `addedAt?: string`
- `pnpm type-check` 通过

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

### TASK-006: [Console-Mapper] 更新 mapProblemList/mapProblemListItem 和 forkProblemList

**[任务描述]**
更新 `console/src/api/problem-list.ts` 中的 mapper 函数：添加 `authorName`/`authorUsername` 映射，适配 fork 返回字段名（后端已改为 `id`），更新 `ProblemListWithStatus` 接口添加 `problemCount`。解决 C2、C5、C6 的 mapper 部分。

**[前置依赖]**
TASK-002, TASK-004

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第七节 7.4

**[交付物标准]**
- `mapProblemList` 返回值含 `authorName`/`authorUsername`
- `mapProblemListItem` 返回值含 `authorName`/`authorUsername`
- `forkProblemList` 正确从 `{ id }` 提取返回值
- `ProblemListWithStatus` 接口含 `problemCount`
- `pnpm type-check` 通过，`pnpm test` 通过

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

### TASK-007: [Console-Mapper] getUserListsForProblem 适配后端响应结构

**[任务描述]**
修改 `getUserListsForProblem` 函数，从后端 `UserListsForProblemVO` 的 `lists` 数组提取数据（而非将整个响应当数组），适配 `hasProblem` → `containsProblem`，映射新增的 `problemCount`/`canEdit`。解决 C6 的 mapper 部分。

**[前置依赖]**
TASK-003, TASK-004

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第二节 2.4

**[交付物标准]**
- `getUserListsForProblem` 从 `data.lists` 提取列表
- `containsProblem` 正确映射自 `hasProblem`
- `canEdit` 正确映射自后端 `canEdit`
- `problemCount` 正确映射自后端 `problemCount`
- `pnpm type-check` 通过，`pnpm test` 通过

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

### TASK-008: [Console-Mapper] mapCategory 补全字段映射

**[任务描述]**
更新 `mapCategory` 函数，映射 `description`/`icon`/`color`/`listCount` 字段。解决 HIGH 问题 H3 的 mapper 部分。

**[前置依赖]**
TASK-004

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第二节 2.5

**[交付物标准]**
- `mapCategory` 返回值含 `description`/`icon`/`color`/`listCount`
- `BackendProblemListCategory` 接口含对应 unknown 类型字段
- `pnpm type-check` 通过

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

### TASK-009: [Console-Mapper] fetchProblemListOverview 提取 isOwner

**[任务描述]**
在 `fetchProblemListOverview` 的 mapper 中，从 `ProblemListDetailVO` 顶层提取 `isOwner` 字段到 `ProblemListDetailResponse`。解决 CRITICAL 问题 C3 的 mapper 部分。

**[前置依赖]**
TASK-004

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第二节 2.2

**[交付物标准]**
- `fetchProblemListOverview` 返回值含 `isOwner?: boolean`
- 从后端响应顶层 `isOwner` 字段正确提取
- `pnpm type-check` 通过

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

### TASK-010: [Console-UI] ProblemSaveButton favoritesCount fallback

**[任务描述]**
在 `ProblemSaveButton.vue` 中为 `favoritesCount` 添加 undefined fallback，将 `{{ list.favoritesCount }}` 改为 `{{ list.favoritesCount ?? 0 }}`。解决 favoritesCount 改为可选后的显示问题。

**[前置依赖]**
TASK-004

**[参考文档]**
`console/src/components/edge-operations/ProblemSaveButton.vue:405`

**[交付物标准]**
- `ProblemSaveButton.vue` 中 `favoritesCount` 渲染含 `?? 0` fallback
- `pnpm type-check` 通过

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

### TASK-011: [Management-API] 移除 updateList 死代码

**[任务描述]**
从 `management/src/api/admin/problem-lists.ts` 中移除 `updateList` 方法和 `UpdateProblemListDto` 接口（如无其他引用）。同步检查 store 中是否有 `updateList` action。解决 HIGH 问题 H4。

**[前置依赖]**
无

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第四节 4.2

**[交付物标准]**
- `updateList` 方法从 API 层移除
- `UpdateProblemListDto` 接口从 API 层移除（如无引用）
- Store 中 `updateList` action 移除（如存在）
- `pnpm type-check` 通过

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

### TASK-012: [Management-UI] 列表页添加作者列

**[任务描述]**
在 `management/src/views/problem-lists/columns.ts` 中添加 `authorName` 列，显示在 `isFeatured` 列之前。同步添加 i18n key。解决 MEDIUM 问题 M2。

**[前置依赖]**
TASK-014

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第五节 5.1

**[交付物标准]**
- `columns.ts` 含 `authorName` 列定义
- en-US i18n 含 `problemLists.columns.author: 'Author'`
- zh-CN i18n 含 `problemLists.columns.author: '作者'`
- DataTable 列名 i18n 含 `authorName` key
- 页面渲染正常

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

### TASK-013: [Management-UI] ProblemsManager 添加 status 和 addedAt 列

**[任务描述]**
在 `ProblemsManager.vue` 的表格中添加 `status` 和 `addedAt` 列。status 渲染为 badge（solved=绿色，attempted=黄色，todo=灰色），addedAt 使用 formatDate 格式化。解决 MEDIUM 问题 M3。

**[前置依赖]**
TASK-014

**[参考文档]**
`docs/alignment/problem-lists-full-frontend-backend-alignment.md` 第五节 5.1

**[交付物标准]**
- `ProblemsManager.vue` 表格含 `status` 列（badge 渲染）
- `ProblemsManager.vue` 表格含 `addedAt` 列（formatDate 渲染）
- 页面渲染正常

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

### TASK-014: [Management-i18n] 添加新列翻译 key

**[任务描述]**
在 en-US 和 zh-CN 的 `problemLists.ts` 和 `table.ts` 中添加新列翻译 key：`author`/`status`/`addedAt`。

**[前置依赖]**
无

**[参考文档]**
`management/src/i18n/locales/en-US/modules/problemLists.ts`

**[交付物标准]**
- en-US `problemLists.ts` 含 `columns.author`/`columns.status`/`columns.addedAt`
- zh-CN `problemLists.ts` 含对应中文翻译
- en-US `table.ts` 含 `columnNames.authorName`/`columnNames.status`/`columnNames.addedAt`
- zh-CN `table.ts` 含对应中文翻译
- `pnpm type-check` 通过

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
