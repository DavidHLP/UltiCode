# Plan: Contest API Phase 3 — 数据映射层加固

## Summary
在 Console 前端引入 Zod schema 验证后端 Contest 模块响应，替代脆弱的 `as` 类型断言和 `toNumber()` 手动转换。同时删除冗余的 API 别名函数，统一调用入口。

## User Story
As a developer, I want runtime-validated API response parsing with Zod schemas, so that backend field changes fail fast with clear errors instead of causing silent runtime bugs.

## Problem → Solution
当前 `console/src/api/contest.ts` 使用 90+ 行的手动 mapper 函数和 `as` 类型断言解析后端响应，后端字段变更时前端不会编译报错 → 引入 Zod schema 做运行时验证，删除 `toNumber()` 和别名函数。

## Metadata
- **Complexity**: Medium
- **Source PRD**: `docs/contest-api-alignment-analysis.md`
- **PRD Phase**: Phase 3 — 数据映射层加固
- **Estimated Files**: 5-7 个文件

---

## UX Design

Internal change — no user-facing UX transformation. 行为完全一致，仅增强类型安全与运行时验证。

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `console/src/api/contest.ts` | all | 核心改造目标：mapper + 别名函数 |
| P0 | `console/src/types/contest.ts` | all | 前端类型定义，Zod schema 需对齐 |
| P1 | `management/src/lib/schemas/problem.ts` | 1-40 | 项目内 Zod 使用参考模式 |
| P1 | `backend-spring/.../dto/ContestListVO.java` | all | 确认后端字段名与类型 |
| P1 | `backend-spring/.../dto/ContestVO.java` | all | 确认详情字段名与类型 |
| P2 | `console/src/stores/contest.ts` | all | 检查 store 中对 API 函数的调用 |

---

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Zod Basics | https://zod.dev | `z.infer<typeof schema>` 推导类型；`.parse()` 运行时验证；`.optional()` / `.nullable()` 处理缺失字段 |
| Zod Number | https://zod.dev/?id=numbers | `z.number().int()` 验证整数；`.default(0)` 提供默认值 |
| Zod Enum | https://zod.dev/?id=zod-enums | `z.nativeEnum(TsEnum)` 从 TS enum 创建 schema |

---

## Patterns to Mirror

### ZOD_SCHEMA_DEFINITION
// SOURCE: management/src/lib/schemas/problem.ts:1-38
```typescript
import { z } from 'zod'

export const exampleSchema = z.object({
  id: z.string().optional(),
  input: z.string(),
  output: z.string(),
  explanation: z.string().optional(),
})

export type Example = z.infer<typeof exampleSchema>
```

### API_REQUEST_PATTERN
// SOURCE: console/src/api/contest.ts:147-152
```typescript
export async function fetchUpcomingContests(): Promise<PaginatedResult<ContestListItem>> {
  const result = await apiGet<PaginatedResult<Record<string, unknown>>>("/contest/upcoming")
  return mapPaginatedContestList(result)
}
```

### PAGINATED_RESULT_TYPE
// SOURCE: console/src/types/contest.ts:361-367
```typescript
export interface PaginatedResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `console/src/api/contest.ts` | UPDATE | 删除 mapper 和别名；使用 Zod schema 解析 |
| `console/src/api/contest.schema.ts` | CREATE | 新增 Zod schema 定义文件 |
| `console/package.json` | UPDATE | 添加 `zod` 依赖 |
| `console/src/stores/contest.ts` | UPDATE | 替换别名函数调用为正式函数名 |
| `console/src/views/contest/**/*.vue` | UPDATE | 检查并替换别名函数调用 |
| `console/src/views/contest/ContestDetailView.vue` | UPDATE | 可能调用 `getContest` 别名 |

---

## NOT Building

- 不修改后端 VO/DTO 类型（Phase 1/2 已处理或单独跟踪）
- 不修改 Management 前端（Phase 6 处理）
- 不引入 openapi-generator（超出 Phase 3 范围）
- 不修改 `ContestRankingEntry` / `LiveRankingEntry` 等非 ContestList/Detail 的复杂类型（保持现状，仅对核心列表/详情做 Zod 验证）
- 不修改路由或 Store 逻辑（Phase 4/5 处理）

---

## Step-by-Step Tasks

### Task 1: Install Zod Dependency
- **ACTION**: 在 console 中安装 `zod`
- **IMPLEMENT**: `cd console && pnpm add zod`
- **MIRROR**: Management 已使用 zod 3.25.76，Console 使用相同版本
- **IMPORTS**: N/A
- **GOTCHA**: 确保安装后 `pnpm-lock.yaml` 更新
- **VALIDATE**: `grep zod console/package.json` 应显示依赖

### Task 2: Create Zod Schema File
- **ACTION**: 创建 `console/src/api/contest.schema.ts`，定义所有 Contest 相关 Zod schema
- **IMPLEMENT**:
  ```typescript
  import { z } from "zod"
  import { ContestType, ContestStatus, ContestScoringMode, ContestTieBreaker } from "@/types/contest"

  export const contestStatusSchema = z.nativeEnum(ContestStatus)
  export const contestTypeSchema = z.nativeEnum(ContestType)
  export const contestScoringModeSchema = z.enum(["SCORE", "ICPC", "IOI"])
  export const contestTieBreakerSchema = z.enum(["LAST_SOLVE_TIME", "TOTAL_TIME"])

  export const contestListItemSchema = z.object({
    id: z.string(),
    slug: z.string(),
    title: z.string(),
    status: z.union([contestStatusSchema, z.string()]),
    startTime: z.string(),
    endTime: z.string(),
    duration: z.number().int(),
    contestType: z.union([contestTypeSchema, z.string()]),
    participantCount: z.number().int().default(0),
    problemCount: z.number().int().default(0),
    isPremium: z.boolean().default(false),
    isPublished: z.boolean().default(false),
    isVisible: z.boolean().default(false),
    maxParticipants: z.number().int().default(0),
    registeredCount: z.number().int().default(0),
    isParticipating: z.boolean().default(false),
    userRanking: z.number().int().default(0),
    isRated: z.boolean().default(false),
    scoringMode: contestScoringModeSchema,
    penaltyPerWrong: z.number().int().default(0),
    coverImage: z.string().default(""),
  })

  export const contestDetailSchema = contestListItemSchema.extend({
    description: z.string().default(""),
    isVirtual: z.boolean().default(false),
    submissionCount: z.number().int().default(0),
    rules: z.string().default(""),
    registrationStart: z.string().default(""),
    registrationEnd: z.string().default(""),
    freezeTime: z.string().default(""),
    actualStartTime: z.string().default(""),
    actualEndTime: z.string().default(""),
    tieBreaker: contestTieBreakerSchema,
    scoringRuleId: z.string().default(""),
    createdAt: z.string().default(""),
    updatedAt: z.string().default(""),
    createdById: z.number().int().default(0),
    createdByUsername: z.string().default(""),
    problemIds: z.array(z.number().int()).default([]),
    tags: z.array(z.string()).default([]),
    userScore: z.number().int().default(0),
  })

  export const contestProblemSummarySchema = z.object({
    id: z.string(),
    contestId: z.string(),
    problemId: z.number().int(),
    problemIndex: z.string().default(""),
    score: z.number().int().default(0),
    penaltyPerWrong: z.number().int().default(0),
    title: z.string().default(""),
    slug: z.string().default(""),
    difficulty: z.string().default(""),
    solvedCount: z.number().int().default(0),
    submissionCount: z.number().int().default(0),
    acceptanceRate: z.number().default(0),
  })

  export const globalRankingEntrySchema = z.object({
    rank: z.number().int(),
    userId: z.string(),
    username: z.string(),
    avatar: z.string().nullable().default(null),
    country: z.string().nullable().default(null),
    rating: z.number().int(),
    maxRating: z.number().int(),
    ratingTitle: z.string().default("NEWBIE"),
    maxRatingTitle: z.string().default("NEWBIE"),
    contestsAttended: z.number().int().default(0),
    badge: z.string().nullable().default(null),
  })

  export const paginatedSchema = <T extends z.ZodTypeAny>(itemSchema: T) =>
    z.object({
      items: z.array(itemSchema),
      total: z.number().int().default(0),
      page: z.number().int().default(1),
      pageSize: z.number().int().default(20),
      totalPages: z.number().int().default(0),
    })

  export type ContestListItemInput = z.infer<typeof contestListItemSchema>
  export type ContestDetailInput = z.infer<typeof contestDetailSchema>
  export type ContestProblemSummaryInput = z.infer<typeof contestProblemSummarySchema>
  export type GlobalRankingEntryInput = z.infer<typeof globalRankingEntrySchema>
  ```
- **MIRROR**: `management/src/lib/schemas/problem.ts` 的 Zod 模式
- **IMPORTS**: `zod`, `@/types/contest`
- **GOTCHA**:
  - `ContestStatus` 和 `ContestType` 是 TS enum，用 `z.nativeEnum()`
  - `ContestScoringMode` 和 `ContestTieBreaker` 是 string union type，用 `z.enum()`
  - 后端 `userScore` 是 `Long`，JSON 序列化后是 `number`，前端用 `z.number()`
  - 所有字符串日期字段后端保证返回，但为防御性编程保留 `.default("")`
- **VALIDATE**: TypeScript 编译通过，无类型错误

### Task 3: Refactor API File to Use Zod Schemas
- **ACTION**: 重写 `console/src/api/contest.ts`，删除 `toNumber()` 和手动 mapper，使用 schema 解析
- **IMPLEMENT**:
  1. 删除 `toNumber()` 函数（第 27-34 行）
  2. 删除所有 `mapContestListItem`, `mapContestDetail`, `mapContestProblem`, `mapGlobalRankingEntry`, `mapPaginatedContestList`
  3. 新增辅助函数 `parsePaginated<T>`:
     ```typescript
     import { contestListItemSchema, contestDetailSchema, contestProblemSummarySchema, globalRankingEntrySchema, paginatedSchema } from "./contest.schema"

     function parsePaginated<T extends z.ZodTypeAny>(
       itemSchema: T,
       result: unknown,
     ): z.infer<ReturnType<typeof paginatedSchema<T>>> {
       return paginatedSchema(itemSchema).parse(result)
     }
     ```
  4. 替换各 API 函数实现：
     ```typescript
     export async function fetchUpcomingContests(): Promise<PaginatedResult<ContestListItem>> {
       const result = await apiGet<unknown>("/contest/upcoming")
       return parsePaginated(contestListItemSchema, result)
     }

     export async function fetchContestDetail(contestId: string): Promise<ContestDetail> {
       const raw = await apiGet<unknown>(`/contest/${contestId}`)
       return contestDetailSchema.parse(raw)
     }

     export async function getContestProblems(slug: string): Promise<ContestProblemSummary[]> {
       const data = await apiGet<unknown[]>(`/contest/${slug}/problems`)
       return z.array(contestProblemSummarySchema).parse(data || [])
     }

     export async function fetchGlobalRankings(options?: { ... }): Promise<PaginatedResult<GlobalRankingEntry>> {
       const { page = 1, limit = 50, country } = options || {}
       const result = await apiGet<unknown>("/contest/rankings/global", { params: country ? { page, limit, country } : { page, limit } })
       return parsePaginated(globalRankingEntrySchema, result)
     }
     ```
- **MIRROR**: 保持原有 API 函数签名不变，仅内部实现改为 schema 解析
- **IMPORTS**: `zod`, `./contest.schema`, `@/types/contest`
- **GOTCHA**:
  - `apiGet` 的泛型参数改为 `unknown` 或完全省略，因为类型安全由 Zod 保证
  - `fetchContestRanking` / `fetchLiveRanking` / `getRanking` 等涉及复杂 ranking 类型的函数本次不改实现（不在 Phase 3 范围），但如有 `toNumber` 需删除
  - `fetchUserContests` 返回 `ContestListItem[]`，需用 `z.array(contestListItemSchema).parse(result)`
- **VALIDATE**: 所有 API 函数签名与原文件完全一致；TypeScript 编译通过

### Task 4: Remove Alias Functions
- **ACTION**: 删除以下别名函数：
  - `getContest` (第 238-240 行) → 调用方改用 `fetchContestDetail`
  - `register` (第 335-337 行) → 调用方改用 `registerForContest`
  - `withdraw` (第 343-345 行) → 调用方改用 `unregisterFromContest`
  - `getMyParticipation` (第 347-351 行) → 调用方改用 `fetchParticipationStatus`
- **IMPLEMENT**: 直接从 `contest.ts` 删除这 4 个函数
- **MIRROR**: N/A（删除代码）
- **IMPORTS**: N/A
- **GOTCHA**: 必须先搜索所有调用方并同步修改，否则编译失败
- **VALIDATE**: `grep -r "getContest\|register(\|withdraw(\|getMyParticipation" console/src/ --include="*.ts" --include="*.vue"` 无结果

### Task 5: Update All Callers of Alias Functions
- **ACTION**: 搜索并替换所有调用别名的代码
- **IMPLEMENT**:
  ```bash
  grep -r "getContest(" console/src --include="*.ts" --include="*.vue"
  grep -r "\bregister(" console/src --include="*.ts" --include="*.vue"  # 注意区分其他 register 含义
  grep -r "withdraw(" console/src --include="*.ts" --include="*.vue"
  grep -r "getMyParticipation(" console/src --include="*.ts" --include="*.vue"
  ```
  对每个匹配：
  - `getContest(...)` → `fetchContestDetail(...)`
  - `register(...)` → `registerForContest(...)`
  - `withdraw(...)` → `unregisterFromContest(...)`
  - `getMyParticipation(...)` → `fetchParticipationStatus(...)`
- **MIRROR**: N/A
- **IMPORTS**: 检查调用文件是否已导入正确函数
- **GOTCHA**:
  - `register` 是常见动词，可能在非 contest 上下文中使用（如用户注册），必须确认调用来源是 `@/api/contest`
  - `withdraw` 同理，需确认是 contest 的 withdraw
- **VALIDATE**: `pnpm type-check` 在 console 目录通过

### Task 6: Verify Backend Number Types
- **ACTION**: 确认后端所有相关字段已统一为数字类型（无需前端 toNumber）
- **IMPLEMENT**: 检查以下后端 VO/DTO：
  - `ContestListVO.java`: `duration`, `participantCount`, `problemCount`, `maxParticipants`, `registeredCount`, `userRanking`, `penaltyPerWrong` 都是 `Integer`
  - `ContestVO.java`: 同上，外加 `submissionCount`, `createdById`, `userScore` (`Long`)
  - `ContestProblemVO.java`: `problemId`, `score`, `penaltyPerWrong`, `solvedCount`, `submissionCount`
- **MIRROR**: N/A
- **IMPORTS**: N/A
- **GOTCHA**:
  - 如果后端某字段实际仍返回 `String`（如历史遗留的 `userScore`），Zod `.parse()` 会抛出异常，需要后端同步修复或在 schema 中使用 `z.union([z.number(), z.string().transform(Number)])`
  - 优先确认 Phase 1/2 是否已修复这些类型
- **VALIDATE**: 通过浏览器 DevTools Network 面板观察实际响应，或运行集成测试验证

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `contestListItemSchema.parse` | 合法 ContestListVO JSON | 通过，返回正确类型 | 否 |
| `contestListItemSchema.parse` | 缺少 `coverImage` | 通过，`coverImage` 默认 `""` | 是 |
| `contestListItemSchema.parse` | `participantCount` 为字符串 `"5"` | **抛出 ZodError** | 是 |
| `contestDetailSchema.parse` | 合法 ContestVO JSON | 通过，继承 list 所有字段 | 否 |
| `parsePaginated` | `{ items: [...], total: 10 }` | 通过，page/pageSize 有默认值 | 是 |
| `z.array(contestProblemSummarySchema).parse` | `[{ id: "1", problemId: 2, ... }]` | 通过 | 否 |

### Edge Cases Checklist
- [ ] 后端返回字符串数字（如 `"5"` 代替 `5`）→ Zod 应拒绝，推动后端修复
- [ ] 后端返回 `null` 的字段（如 `userRanking`）→ schema 需处理（`.nullable().default(0)` 或 `.optional()`）
- [ ] 后端返回缺失字段 → `.default()` 应生效
- [ ] 后端返回额外字段 → Zod `.passthrough()` 或 `.strip()`（默认 strip，通常 OK）
- [ ] 空数组响应 → `z.array(...).parse([])` 应通过

---

## Validation Commands

### Static Analysis
```bash
cd console
pnpm install
pnpm type-check
```
EXPECT: Zero type errors

### Unit Tests
```bash
cd console
pnpm test
```
EXPECT: All tests pass（无 Contest 相关测试失败）

### Full Test Suite
```bash
cd console
pnpm lint
```
EXPECT: No lint errors

### Manual Validation
- [ ] 打开 `http://localhost:9002/contest`，确认列表页正常加载
- [ ] 点击一个比赛进入详情页，确认数据正常
- [ ] 打开浏览器 DevTools Network，确认后端返回 JSON 无字符串数字
- [ ] 确认前端控制台无 Zod parse 错误（红色报错）

---

## Acceptance Criteria
- [ ] `zod` 已安装到 console 依赖
- [ ] `console/src/api/contest.schema.ts` 已创建且包含所有核心 schema
- [ ] `console/src/api/contest.ts` 已删除 `toNumber()` 和所有手动 mapper
- [ ] `console/src/api/contest.ts` 已删除 `getContest`, `register`, `withdraw`, `getMyParticipation` 别名
- [ ] 所有别名调用方已更新为正式函数名
- [ ] `pnpm type-check` 通过
- [ ] `pnpm lint` 通过
- [ ] 浏览器中 Contest 列表和详情页功能正常

---

## Completion Checklist
- [ ] Code follows discovered patterns (Zod schema 与 management 一致)
- [ ] Error handling matches codebase style (Zod parse 抛出异常，由调用方 catch)
- [ ] No hardcoded values
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

---

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 后端某字段仍返回字符串数字 | Medium | High | 先在 DevTools 确认实际响应；如有问题，schema 中临时加 `.union([z.number(), z.string().transform(Number)])` |
| 调用方别名遗漏未全部替换 | Low | Medium | 用 `grep` 全量搜索；依赖 `pnpm type-check` 捕获未定义引用 |
| Zod schema 字段遗漏导致运行时错误 | Low | High | 对照 `types/contest.ts` 完整核对字段；用浏览器测试覆盖 |
| `zod` 增加包体积 | Low | Low | zod gzip 后约 10KB，可接受；如敏感可用 `zod` 的 tree-shaking |

---

## Notes
- **Zod 版本**: Management 使用 `zod@^3.25.76`，Console 应安装相同版本保持一致。
- **backend 数字类型**: 根据后端代码，所有 `Integer`/`Long` 字段通过 Jackson 序列化后都是 JSON `number`，理论上不需要字符串转换。如果实测发现例外，需要后端修复而非前端兼容。
- **Enum 处理**: `ContestStatus` 和 `ContestType` 是 TS `enum`，使用 `z.nativeEnum()`；`ContestScoringMode` 和 `ContestTieBreaker` 是 string union type，使用 `z.enum()`。
- **Phase 3 范围边界**: 本阶段仅处理 `ContestListItem`, `ContestDetail`, `ContestProblemSummary`, `GlobalRankingEntry` 的 schema 验证。`ContestRankingEntry`, `LiveRankingEntry`, `RankingEntry` 等更复杂的 ranking 类型留在后续阶段处理，因为涉及嵌套对象和更复杂的字段语义。
