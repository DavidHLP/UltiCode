# Plan: Problems 模块前后端 API 颗粒度对齐

## Summary
对齐 management 前端 `/problems` 页面与后端 `/admin/problems` 端点之间的 DTO 字段类型、返回数据结构和序列化格式，消除 3 处 HIGH 级别和 1 处 MEDIUM 级别颗粒度差异，确保前后端契约一致。

## User Story
作为前端开发人员，我希望 problems 模块的 API 类型定义与后端实际行为完全一致，以便在编码时获得准确的类型提示，避免运行时序列化/反序列化错误。

## Problem → Solution
**当前状态**: 前端 DTO 类型定义与后端不一致——`examples`/`constraints`/`hints` 在 Create 和 Update DTO 之间类型不统一；`getProblem` 返回类型包含后端不返回的字段；前端在编辑视图中手动 `JSON.stringify()` 但创建视图中不 stringify。
**目标状态**: 前后端 DTO 字段类型完全对齐，序列化逻辑在 API 层统一处理，业务组件无需关心序列化细节。

## Metadata
- **Complexity**: Medium
- **Source PRD**: `docs/problems-api-granularity-analysis.md`
- **PRD Phase**: N/A (standalone)
- **Estimated Files**: 12

---

## UX Design

### Before
```
┌──────────────────────────────────────────────┐
│  ProblemCreateView: 传递 raw array           │
│  → 后端 CreateProblemDTO 期望 String         │
│  → 请求失败或数据丢失                        │
│                                              │
│  EditDescriptionView: 手动 JSON.stringify     │
│  EditCasesView:       手动 JSON.stringify     │
│  → 序列化逻辑散落在组件中，不可复用          │
│                                              │
│  Problem 接口声明 detail?/examples?/languages?│
│  → 后端 Admin 端点不返回这些字段             │
│  → 类型不安全，容易误用                      │
└──────────────────────────────────────────────┘
```

### After
```
┌──────────────────────────────────────────────┐
│  API 层统一序列化:                           │
│  createProblem() → API 层自动 stringify      │
│  updateProblem() → API 层自动 stringify      │
│  → 组件只传递结构化数据，无需关心序列化      │
│                                              │
│  Problem 接口精确反映后端返回:               │
│  → Problem (列表项，无 detail/examples)      │
│  → ProblemDetail (完整数据，仅公开端点)      │
│  → 类型安全，IDE 提示准确                    │
└──────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| ProblemCreateView 提交 | 传递 raw array，后端期望 String | 传递结构化对象，API 层自动序列化 | 消除手动 stringify |
| EditDescriptionView 提交 | 手动 JSON.stringify 3 个字段 | 传递结构化对象，API 层自动序列化 | 序列化逻辑集中 |
| EditCasesView 提交 | 手动 JSON.stringify 3 个字段 | 传递结构化对象，API 层自动序列化 | 序列化逻辑集中 |
| TypeScript 类型 | Problem 含后端不返回的字段 | 精确反映后端返回数据 | 类型安全 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `management/src/api/admin/problems.ts` | 1-493 | 前端 API 定义，需修改类型和序列化 |
| P0 (critical) | `backend-spring/.../admin/controller/AdminProblemController.java` | 1-266 | 后端 Admin 端点定义 |
| P0 (critical) | `backend-spring/.../problem/dto/CreateProblemDTO.java` | all | 后端 Create DTO 字段类型 |
| P0 (critical) | `backend-spring/.../problem/dto/UpdateProblemDTO.java` | all | 后端 Update DTO 字段类型 |
| P1 (important) | `management/src/views/problems/edit/EditDescriptionView.vue` | all | 编辑视图，需移除手动 stringify |
| P1 (important) | `management/src/views/problems/edit/EditCasesView.vue` | all | 编辑视图，需移除手动 stringify |
| P1 (important) | `management/src/views/problems/ProblemCreateView.vue` | all | 创建视图，需统一序列化 |
| P1 (important) | `management/src/stores/admin/problems.ts` | 194-328 | Store 中 create/update 方法 |
| P2 (reference) | `backend-spring/.../problem/service/impl/ProblemServiceImpl.java` | 420-634 | 后端处理 examples/constraints/hints 的逻辑 |
| P2 (reference) | `management/src/api/admin/__tests__/problems.spec.ts` | all | 前端 API 测试 |
| P2 (reference) | `management/src/stores/admin/__tests__/problems.spec.ts` | all | 前端 Store 测试 |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Jackson `@JsonDeserialize` | Spring Boot 内置 | 后端 UpdateProblemDTO.languages 已使用 `@JsonDeserialize(contentAs = LanguageConfigDTO.class)` |
| MyBatis-Plus JSON 存储 | 项目内部约定 | examples/constraints/hints 以 JSON 字符串存储在 problem_details 表中 |

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: `management/src/api/admin/problems.ts:3-13`
```typescript
export enum Difficulty {
  EASY = 'EASY',
  MEDIUM = 'MEDIUM',
  HARD = 'HARD',
}
export enum ProblemStatus {
  SOLVED = 'solved',
  ATTEMPTED = 'attempted',
  TODO = 'todo',
}
```
枚举使用 UPPER_SNAKE_CASE，Status 值使用小写。

### ERROR_HANDLING
// SOURCE: `management/src/utils/request.ts:216-228`
```typescript
// 后端响应解包: code === 0 → 返回 data，否则抛出 ApiError
if (responseData.code === 0) {
  return responseData.data
}
throw new ApiError(message, code, response)
```

### SERVICE_PATTERN
// SOURCE: `backend-spring/.../problem/service/impl/ProblemServiceImpl.java:505-607`
```java
// updateProblemDetail: 根据 DTO 字段是否为 null 决定是否更新
// examples: JSON 字符串 → objectMapper.readValue() → 删除旧记录 → 插入新记录
// constraintsJson/hints: 直接存入 problem_details 表
// languages: 删除旧记录 → 按模板创建新记录
```

### TEST_STRUCTURE
// SOURCE: `management/src/api/admin/__tests__/problems.spec.ts`
```typescript
// Mock @/utils/request 中的 apiGet/apiPost/apiPatch/apiDelete
// 验证调用参数和返回值
vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  // ...
}))
```

### STORE_PATTERN
// SOURCE: `management/src/stores/admin/problems.ts:194-227`
```typescript
// Store 方法: loading → try/finally → 调用 API → 更新本地状态
async function createProblem(data: CreateProblemDto) {
  loading.value = true
  try {
    const problem = await problemsApi.createProblem(data)
    return problem
  } finally { loading.value = false }
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `management/src/api/admin/problems.ts` | UPDATE | 修正 DTO 类型定义，添加 API 层序列化逻辑 |
| `management/src/views/problems/edit/EditDescriptionView.vue` | UPDATE | 移除手动 JSON.stringify，传递结构化数据 |
| `management/src/views/problems/edit/EditCasesView.vue` | UPDATE | 移除手动 JSON.stringify，传递结构化数据 |
| `management/src/views/problems/ProblemCreateView.vue` | UPDATE | 统一传递结构化数据 |
| `management/src/stores/admin/problems.ts` | UPDATE | store 方法签名适配新 DTO 类型 |
| `management/src/api/admin/__tests__/problems.spec.ts` | UPDATE | 更新 API 测试覆盖序列化逻辑 |
| `management/src/stores/admin/__tests__/problems.spec.ts` | UPDATE | 更新 Store 测试适配新类型 |
| `management/src/lib/schemas/problem.ts` | UPDATE | Zod schema 适配新类型定义 |

## NOT Building

- 不修改后端 Java 代码（后端 API 行为不变，仅调整前端类型和序列化逻辑）
- 不修改 `ProblemController`（公开端点）
- 不修改 `AdminProblemController`（Admin 端点）
- 不修改数据库表结构
- 不修改 Tab-specific API（已对齐，无需修改）
- 不修改 Version API（已对齐，无需修改）

---

## Step-by-Step Tasks

### Task 1: 修正前端 DTO 类型定义
- **ACTION**: 在 `management/src/api/admin/problems.ts` 中修正 `CreateProblemDto` 和 `UpdateProblemDto` 的字段类型，使其精确反映后端期望的 JSON 格式
- **IMPLEMENT**:
  1. 修正 `CreateProblemDto`:
     - `examples`: `string` (JSON 字符串，后端 `String` 类型)
     - `constraints`: `string` (JSON 字符串，后端 `String` 类型)
     - `hints`: `string` (JSON 字符串，后端 `String` 类型)
     - `languages`: `string[]` (保持不变，后端 `List<String>`)
     - `tags`: `string[]` (保持不变，后端 `List<String>`)
  2. 修正 `UpdateProblemDto`:
     - `examples`: `string` (JSON 字符串，与后端 `String` 一致)
     - `constraintsJson`: `string` (JSON 字符串，与后端 `String` 一致)
     - `hints`: `string` (JSON 字符串，与后端 `String` 一致)
     - `languages`: `LanguageConfig[]` (保持不变，后端 `List<LanguageConfigDTO>`)
     - `tags`: `string[]` (保持不变)
  3. 新增序列化辅助类型 `ProblemCreateInput` 和 `ProblemUpdateInput`，用于组件层传递结构化数据:
     ```typescript
     export interface ProblemCreateInput {
       slug: string
       title: string
       difficulty: Difficulty
       status?: ProblemStatus
       isPremium?: boolean
       isPublished?: boolean
       summary?: string
       content?: string
       examples?: ProblemExample[]    // 结构化数组
       constraints?: string[]         // 字符串数组
       hints?: string[]               // 字符串数组
       languages?: string[]
       tags?: string[]
     }

     export interface ProblemUpdateInput {
       slug?: string
       title?: string
       difficulty?: Difficulty
       status?: ProblemStatus
       isPremium?: boolean
       hasSolution?: boolean
       summary?: string
       content?: string
       constraintsJson?: string[]     // 字符串数组（组件层）
       hints?: string[]               // 字符串数组（组件层）
       examples?: ProblemExample[]    // 结构化数组（组件层）
       tags?: string[]
       languages?: LanguageConfig[]
     }
     ```
  4. 新增序列化函数:
     ```typescript
     function serializeCreateInput(input: ProblemCreateInput): CreateProblemDto {
       return {
         ...input,
         examples: input.examples ? JSON.stringify(input.examples) : undefined,
         constraints: input.constraints ? JSON.stringify(input.constraints) : undefined,
         hints: input.hints ? JSON.stringify(input.hints) : undefined,
       }
     }

     function serializeUpdateInput(input: ProblemUpdateInput): UpdateProblemDto {
       return {
         ...input,
         examples: input.examples ? JSON.stringify(input.examples) : undefined,
         constraintsJson: input.constraintsJson ? JSON.stringify(input.constraintsJson) : undefined,
         hints: input.hints ? JSON.stringify(input.hints) : undefined,
       }
     }
     ```
- **MIRROR**: 遵循 `NAMING_CONVENTION` 模式，使用 `Problem` 前缀 + `Input`/`Dto` 后缀
- **IMPORTS**: `import type { ProblemExample, LanguageConfig, Difficulty, ProblemStatus } from './problems'`
- **GOTCHA**: `UpdateProblemDto.constraintsJson` 在组件层是 `string[]`，序列化后变为 `string`（JSON 字符串），字段名保持 `constraintsJson` 不变以匹配后端
- **VALIDATE**: TypeScript 编译无错误，类型定义与后端 DTO 字段一一对应

---

### Task 2: 修正 `Problem` 接口，移除后端不返回的字段
- **ACTION**: 修正 `Problem` 接口定义，使其精确反映 `AdminProblemController.getProblemById` 返回的 `ProblemVO` 结构
- **IMPLEMENT**:
  1. 从 `Problem` 接口中移除 `detail?: ProblemDetail`、`examples?: ProblemExample[]`、`languages?: ProblemLanguage[]` 字段（Admin 端点不返回这些字段）
  2. 保留 `submissionCount?: number` 和 `solutionCount?: number`（后端 ProblemVO 包含这些字段）
  3. 保留 `ProblemDetail`、`ProblemExample`、`ProblemLanguage` 类型定义（其他地方仍在使用）
  4. 确认 `Problem` 接口的所有字段与后端 `ProblemVO` 的 JSON 属性名一致:
     - `isPremium` ↔ `is_premium`
     - `hasSolution` ↔ `has_solution`
     - `isPublished` ↔ `is_published`
     - `isDeleted` ↔ `is_deleted`
     - `isFlagged` ↔ `is_flagged`
     - `flagReason` ↔ `flag_reason`
     - `flagStatus` ↔ `flag_status`
     - `submissionCount` ↔ `submission_count`
     - `solutionCount` ↔ `solution_count`
     - `publishedAt` ↔ `published_at`
     - `createdAt` ↔ `created_at`
     - `updatedAt` ↔ `updated_at`
- **MIRROR**: 遵循后端 `ProblemVO.java` 的 `@JsonProperty` 注解定义
- **IMPORTS**: 无新增
- **GOTCHA**: 公开端点 `ProblemController.getProblemById` 返回 `ProblemDetailResponse`（包含 detail/examples/languages），但前端 management 不直接使用此端点获取完整数据，而是使用 tab-specific API
- **VALIDATE**: `pnpm type-check` 通过，无类型错误

---

### Task 3: 修改 API 层方法，集成序列化逻辑
- **ACTION**: 修改 `problemsApi.createProblem` 和 `problemsApi.updateProblem` 方法，在发送请求前自动序列化结构化字段
- **IMPLEMENT**:
  1. 修改 `createProblem` 方法签名和实现:
     ```typescript
     async createProblem(input: ProblemCreateInput): Promise<Problem> {
       const data = serializeCreateInput(input)
       return apiPost<Problem>('/admin/problems', data)
     }
     ```
  2. 修改 `updateProblem` 方法签名和实现:
     ```typescript
     async updateProblem(id: string, input: ProblemUpdateInput): Promise<Problem> {
       const data = serializeUpdateInput(input)
       return apiPatch<Problem>(`/admin/problems/${id}`, data)
     }
     ```
  3. 保持其他方法不变（Tab-specific API、Version API 等已对齐）
- **MIRROR**: 遵循 `STORE_PATTERN`，API 方法负责数据转换，Store 只做状态管理
- **IMPORTS**: `import { serializeCreateInput, serializeUpdateInput, type ProblemCreateInput, type ProblemUpdateInput } from './problems'`
- **GOTCHA**: `updateProblemWithPublish` 在 Store 中调用 `problemsApi.updateProblem`，需确保 Store 传递的是 `ProblemUpdateInput` 类型
- **VALIDATE**: API 测试覆盖序列化逻辑

---

### Task 4: 修改 Store 方法签名
- **ACTION**: 修改 `useProblemsStore` 中 `createProblem` 和 `updateProblem` 的参数类型
- **IMPLEMENT**:
  1. `createProblem(data: CreateProblemDto)` → `createProblem(data: ProblemCreateInput)`
  2. `updateProblem(id: string, data: UpdateProblemDto)` → `updateProblem(id: string, data: ProblemUpdateInput)`
  3. `updateProblemWithPublish` 中 `serializedData` 的类型改为 `ProblemUpdateInput`，移除手动序列化逻辑:
     ```typescript
     async function updateProblemWithPublish(id: string, data: ProblemUpdateInput, targetPublishedState: boolean) {
       let problem = await problemsApi.updateProblem(id, data)
       // ... publish/unpublish toggle logic remains unchanged
     }
     ```
- **MIRROR**: 遵循 `STORE_PATTERN`
- **IMPORTS**: `import type { ProblemCreateInput, ProblemUpdateInput } from '@/api/admin/problems'`
- **GOTCHA**: Store 中的 `updateProblem` 方法内部有缓存更新和 tab cache invalidation 逻辑，不要破坏这些逻辑
- **VALIDATE**: `pnpm type-check` 通过

---

### Task 5: 修改 ProblemCreateView 传递结构化数据
- **ACTION**: 修改 `ProblemCreateView.vue` 中 `createProblem` 调用，传递 `ProblemCreateInput` 类型的结构化数据
- **IMPLEMENT**:
  1. 移除 `examples` 字段的 `.map()` 转换（`order` 字段在 API 层序列化时处理）
  2. 确保传递 `examples: ProblemExample[]`、`constraints: string[]`、`hints: string[]`
  3. 示例:
     ```typescript
     const problem = await problemsStore.createProblem({
       ...data,
       difficulty: data.difficulty,
       status: data.status,
       examples: data.examples,
       // constraints 和 hints 已在 formData 中为 string[]
     })
     ```
- **MIRROR**: 组件只传递业务数据，序列化由 API 层处理
- **IMPORTS**: `import type { ProblemCreateInput } from '@/api/admin/problems'`
- **GOTCHA**: `examples` 中的 `order` 字段需要在 API 层序列化函数中添加，而非在组件中:
  ```typescript
  // 在 serializeCreateInput 中:
  examples: input.examples
    ? JSON.stringify(input.examples.map((ex, idx) => ({ ...ex, order: idx })))
    : undefined
  ```
- **VALIDATE**: 创建新题目，验证 examples/constraints/hints 正确存储到数据库

---

### Task 6: 修改 EditDescriptionView 移除手动序列化
- **ACTION**: 修改 `EditDescriptionView.vue` 中 `updateProblemWithPublish` 调用，移除手动 `JSON.stringify`，传递结构化数据
- **IMPLEMENT**:
  1. 将:
     ```typescript
     constraintsJson: JSON.stringify(formData.constraints),
     hints: JSON.stringify(formData.hints),
     examples: JSON.stringify(formData.examples),
     ```
     改为:
     ```typescript
     constraintsJson: formData.constraints,
     hints: formData.hints,
     examples: formData.examples,
     ```
- **MIRROR**: 组件只传递业务数据
- **IMPORTS**: 无新增
- **GOTCHA**: 字段名 `constraintsJson` 在 Input 类型中是 `string[]`，在 Dto 类型中序列化为 `string`（JSON 字符串），API 层自动处理
- **VALIDATE**: 编辑题目描述，验证 constraints/hints/examples 正确更新

---

### Task 7: 修改 EditCasesView 移除手动序列化
- **ACTION**: 修改 `EditCasesView.vue` 中 `updateProblem` 调用，移除手动 `JSON.stringify`，传递结构化数据
- **IMPLEMENT**:
  1. 将:
     ```typescript
     examples: JSON.stringify(formData.examples.map((ex, idx) => ({
       id: ex.id || crypto.randomUUID(),
       input: ex.input, output: ex.output,
       explanation: ex.explanation, order: idx,
     }))),
     constraintsJson: JSON.stringify(formData.constraints),
     hints: JSON.stringify(formData.hints),
     ```
     改为:
     ```typescript
     examples: formData.examples.map((ex, idx) => ({
       id: ex.id || crypto.randomUUID(),
       input: ex.input, output: ex.output,
       explanation: ex.explanation, order: idx,
     })),
     constraintsJson: formData.constraints,
     hints: formData.hints,
     ```
  2. 注意: `examples` 的 `map` 转换（添加 `id` 和 `order`）仍保留在组件中，因为这是业务逻辑而非序列化逻辑。`JSON.stringify` 由 API 层处理。
- **MIRROR**: 组件保留业务数据转换，序列化由 API 层处理
- **IMPORTS**: 无新增
- **GOTCHA**: `crypto.randomUUID()` 调用保留在组件中，这是业务逻辑（为新示例生成 ID），不是序列化逻辑
- **VALIDATE**: 编辑测试用例，验证 examples/constraints/hints 正确更新

---

### Task 8: 更新 Zod Schema
- **ACTION**: 更新 `management/src/lib/schemas/problem.ts` 中的 `problemFormSchema`，使其与 `ProblemCreateInput` 类型一致
- **IMPLEMENT**:
  1. 确认 `problemFormSchema` 中 `examples`、`constraints`、`hints` 的验证规则与 `ProblemCreateInput` 的类型定义一致
  2. `examples`: `z.array(exampleSchema)` (结构化数组)
  3. `constraints`: `z.array(z.string())` (字符串数组)
  4. `hints`: `z.array(z.string())` (字符串数组)
- **MIRROR**: 遵循 `TEST_STRUCTURE` 中的 Zod 验证模式
- **IMPORTS**: 无新增
- **GOTCHA**: Zod schema 定义的是表单输入（结构化数据），不是 API 传输格式（JSON 字符串），两者不应混淆
- **VALIDATE**: `pnpm test` 通过

---

### Task 9: 更新前端 API 测试
- **ACTION**: 更新 `management/src/api/admin/__tests__/problems.spec.ts`，覆盖序列化逻辑
- **IMPLEMENT**:
  1. 新增 `serializeCreateInput` 测试:
     - 验证 `examples: [{input, output}]` → 序列化为 JSON 字符串
     - 验证 `constraints: ['c1']` → 序列化为 JSON 字符串
     - 验证 `hints: ['h1']` → 序列化为 JSON 字符串
     - 验证 `undefined` 字段不序列化
  2. 新增 `serializeUpdateInput` 测试:
     - 验证 `examples` → JSON 字符串
     - 验证 `constraintsJson` → JSON 字符串
     - 验证 `hints` → JSON 字符串
     - 验证 `languages: LanguageConfig[]` 保持不变
  3. 更新 `createProblem` 测试: 传入 `ProblemCreateInput`，验证 `apiPost` 接收到序列化后的 `CreateProblemDto`
  4. 更新 `updateProblem` 测试: 传入 `ProblemUpdateInput`，验证 `apiPatch` 接收到序列化后的 `UpdateProblemDto`
- **MIRROR**: 遵循 `TEST_STRUCTURE` 模式
- **IMPORTS**: `import { serializeCreateInput, serializeUpdateInput, type ProblemCreateInput, type ProblemUpdateInput } from '@/api/admin/problems'`
- **GOTCHA**: 测试需验证序列化后的 JSON 字符串可以被 `JSON.parse()` 还原为原始结构
- **VALIDATE**: `pnpm test` 通过

---

### Task 10: 更新前端 Store 测试
- **ACTION**: 更新 `management/src/stores/admin/__tests__/problems.spec.ts`，适配新的 `ProblemCreateInput` 和 `ProblemUpdateInput` 类型
- **IMPLEMENT**:
  1. 更新 `createProblem` 测试数据: 使用 `ProblemCreateInput` 类型（结构化数据）
  2. 更新 `updateProblem` 测试数据: 使用 `ProblemUpdateInput` 类型（结构化数据）
  3. 验证 Store 调用 `problemsApi.createProblem` 时传递的是 `ProblemCreateInput`
  4. 验证 Store 调用 `problemsApi.updateProblem` 时传递的是 `ProblemUpdateInput`
- **MIRROR**: 遵循现有 Store 测试模式
- **IMPORTS**: `import type { ProblemCreateInput, ProblemUpdateInput } from '@/api/admin/problems'`
- **GOTCHA**: Store mock 需要更新返回值类型
- **VALIDATE**: `pnpm test` 通过

---

### Task 11: 端到端验证
- **ACTION**: 启动前后端服务，执行完整的 CRUD 流程验证
- **IMPLEMENT**:
  1. 启动 backend: `pm2 restart ulticode-9001`
  2. 启动 frontend: `pm2 restart ulticode-9003`
  3. 验证创建题目: 填写包含 examples/constraints/hints 的表单，提交，检查数据库记录
  4. 验证编辑描述: 修改 constraints/hints/examples，保存，检查数据库更新
  5. 验证编辑测试用例: 修改 examples，保存，检查数据库更新
  6. 验证编辑代码: 修改 languages，保存，检查数据库更新
  7. 验证列表页: 确认题目列表正常显示
  8. 验证详情页各 Tab: header/description/code/cases 数据正常加载
- **MIRROR**: 遵循项目 CLAUDE.md 中的闭环管理流程
- **IMPORTS**: 无
- **GOTCHA**: 确保后端服务已启动且数据库连接正常
- **VALIDATE**: 所有 CRUD 操作正常，无 400/500 错误

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `serializeCreateInput` - examples | `{ examples: [{input: "1", output: "2"}] }` | `{ examples: '[{"input":"1","output":"2"}]' }` | No |
| `serializeCreateInput` - constraints | `{ constraints: ["n <= 10"] }` | `{ constraints: '["n <= 10"]' }` | No |
| `serializeCreateInput` - hints | `{ hints: ["Think about DP"] }` | `{ hints: '["Think about DP"]' }` | No |
| `serializeCreateInput` - undefined fields | `{ examples: undefined }` | `{ examples: undefined }` | Yes |
| `serializeUpdateInput` - examples | `{ examples: [{input: "1", output: "2"}] }` | `{ examples: '[{"input":"1","output":"2"}]' }` | No |
| `serializeUpdateInput` - constraintsJson | `{ constraintsJson: ["n <= 10"] }` | `{ constraintsJson: '["n <= 10"]' }` | No |
| `serializeUpdateInput` - languages unchanged | `{ languages: [{language: "python", starterCode: ""}] }` | `{ languages: [{language: "python", starterCode: ""}] }` | No |
| `serializeUpdateInput` - empty arrays | `{ constraintsJson: [] }` | `{ constraintsJson: '[]' }` | Yes |
| `serializeCreateInput` - adds order to examples | `{ examples: [{input: "1", output: "2"}] }` | `examples` 字符串中包含 `"order":0` | Yes |

### Edge Cases Checklist
- [ ] 空数组 `[]` → 序列化为 `'[]'`
- [ ] undefined 字段 → 不序列化，保持 undefined
- [ ] examples 包含特殊字符（引号、换行符）
- [ ] constraints 包含数学公式（如 `1 <= n <= 10^4`）
- [ ] 并发编辑同一题目（乐观锁）

---

## Validation Commands

### Static Analysis
```bash
cd management && pnpm type-check
```
EXPECT: Zero type errors

### Unit Tests
```bash
cd management && pnpm test
```
EXPECT: All tests pass

### Lint
```bash
cd management && pnpm lint
```
EXPECT: No lint errors

### Full Test Suite
```bash
cd management && pnpm test:coverage
```
EXPECT: No regressions, coverage >= 80%

### Browser Validation
```bash
pm2 restart ulticode-9001 && pm2 restart ulticode-9003
```
EXPECT: 前后端服务正常启动，CRUD 操作正常

### Manual Validation
- [ ] 创建题目: examples/constraints/hints 正确存储
- [ ] 编辑描述: constraints/hints/examples 正确更新
- [ ] 编辑测试用例: examples/constraints/hints 正确更新
- [ ] 编辑代码: languages 正确更新
- [ ] 列表页: 数据正常显示
- [ ] 详情页各 Tab: 数据正常加载

---

## Acceptance Criteria
- [ ] 所有 11 个任务完成
- [ ] `CreateProblemDto` 和 `UpdateProblemDto` 类型与后端完全对齐
- [ ] `Problem` 接口精确反映后端 `ProblemVO` 返回结构
- [ ] 序列化逻辑集中在 API 层，组件中无手动 `JSON.stringify`
- [ ] 所有验证命令通过
- [ ] 测试覆盖序列化逻辑
- [ ] 无类型错误
- [ ] 无 lint 错误

## Completion Checklist
- [ ] 代码遵循项目现有模式
- [ ] 错误处理与 `request.ts` 的 `ApiError` 模式一致
- [ ] 测试遵循现有测试模式
- [ ] 无硬编码值
- [ ] 无不必要的范围扩展
- [ ] 自包含——实施时无需额外搜索代码库

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 序列化函数遗漏字段 | Low | High | 在测试中覆盖所有 DTO 字段 |
| `updateProblemWithPublish` 签名变更导致调用方编译错误 | Medium | Medium | Task 4 中同步更新所有调用方 |
| 后端 `constraintsJson` vs 前端 `constraints` 命名不一致 | Low | Low | 保持前端使用 `constraintsJson` 与后端一致 |
| Tab-specific API 返回的数据格式与前端 `DescriptionData.detail.constraintsJson` 类型不匹配 | Low | Medium | Tab API 已对齐，`constraintsJson` 返回 `List<String>`，前端类型为 `string[]`，一致 |

## Notes
- 本次修改仅涉及前端 TypeScript 代码，不修改后端 Java 代码
- 核心设计原则: **组件层传递结构化数据，API 层负责序列化**
- `ProblemCreateInput` 和 `ProblemUpdateInput` 是组件层使用的类型，`CreateProblemDto` 和 `UpdateProblemDto` 是 API 传输层使用的类型
