# Contests 模块前后端对齐分析报告

*生成时间: 2026/05/19 | 覆盖: management 前端 ↔ backend-spring contest 模块*

---

## 执行摘要

发现 **4 个 CRITICAL（阻断级）**、**6 个 HIGH（高优先级）**、**5 个 MEDIUM（中优先级）** 和 **2 个 LOW** 问题。最严重的是 **API 路径前缀完全不匹配**——前端所有 contest CRUD 调用都将返回 404，因为后端没有 `/admin` 前缀。

---

## 🔴 CRITICAL — 阻断级问题

### 1. API 路径前缀完全不匹配（前端 100% 404）

| 前端调用 | 前端路径 | 后端实际路径 |
|---------|---------|-------------|
| `GET /admin/contests` | `contestsApi.getContests()` | `/contest/list` |
| `GET /admin/contests/:id` | `contestsApi.getContest()` | `/contest/{id}` |
| `POST /admin/contests` | `contestsApi.createContest()` | `/contest` (无 `/admin`) |
| `PATCH /admin/contests/:id` | `contestsApi.updateContest()` | `/contest/{id}` (后端是 PUT) |
| `DELETE /admin/contests/:id` | `contestsApi.deleteContest()` | `/contest/{id}` |
| `POST /admin/contests/:id/problems` | `contestsApi.addProblem()` | ❌ **不存在** |
| `DELETE /admin/contests/:id/problems/:pid` | `contestsApi.removeProblem()` | ❌ **不存在** |
| `GET /admin/contests/:id/rankings` | `contestsApi.getRankings()` | ❌ **不存在** |
| `POST /admin/contests/:id/start` | `contestsApi.startContest()` | ❌ **不存在** |
| `POST /admin/contests/:id/end` | `contestsApi.endContest()` | ❌ **不存在** |

- **后端 ContestController** 路径: `/contest`（来自 `@RequestMapping("/contest")`），但 scoring-rules 使用 `/admin/scoring-rules` —— 两套风格不统一
- **后端 ScoringRuleController** 路径: `/admin/scoring-rules`（正确匹配前端 `/admin/scoring-rules`）
- **建议**: 将 `ContestController` 的 `@RequestMapping` 改为 `/admin/contest`，并补全缺失的端点

### 2. 前端 ScoringRule 字段名使用蛇形，后端使用驼峰

前端 `management/src/api/admin/scoring-rules.ts`：
```typescript
export interface ScoringRule {
  base_score_per_problem: number      // 蛇形
  time_bonus_per_minute: number      // 蛇形
  wrong_answer_penalty: number       // 蛇形
  time_limit_penalty: number         // 蛇形
  first_solve_bonus: number          // 蛇形
  full_score_bonus: number            // 蛇形
  is_default: boolean                // 蛇形
  is_active: boolean                 // 蛇形
  created_at: string                 // 蛇形
  updated_at: string                 // 蛇形
}
```

后端 `ScoringRuleVO.java`：
```java
private Integer baseScorePerProblem;  // 驼峰
private Integer timeBonusPerMinute;   // 驼峰
private Integer wrongAnswerPenalty;   // 驼峰
private Integer timeLimitPenalty;    // 驼峰
private Integer firstSolveBonus;     // 驼峰
private Integer fullScoreBonus;      // 驼峰
private Boolean isDefault;           // 驼峰
private Boolean isActive;            // 驼峰
private LocalDateTime createdAt;     // 驼峰
private LocalDateTime updatedAt;     // 驼峰
```

**前端所有 scoring rule 字段在接收后端响应时将为 `undefined`**。需要对齐为驼峰命名。

### 3. 前端 ScoringRule DTO 使用蛇形，后端 DTO 使用驼峰

前端 `CreateScoringRuleDto` 和 `UpdateScoringRuleDto` 使用蛇形，后端 `CreateScoringRuleDTO` / `UpdateScoringRuleDTO` 使用驼峰。创建/更新 scoring rule 时请求体字段名不匹配。

### 4. Solutions API 同样存在路径前缀问题

前端 `management/src/api/admin/solutions.ts` 使用 `/admin/solutions`，但后端 `SolutionController.java` 使用 `/api/solutions`。**这不只影响 contests，是管理前端 API 层的系统性问题**。需要确认是否存在其他 admin controller 处理 `/admin` 前缀的路径。

---

## 🟠 HIGH — 高优先级问题

### 5. ContestType 枚举值完全不匹配

| 位置 | 枚举值 |
|------|--------|
| 前端 `ContestType` (`contests.ts:3-7`) | `PUBLIC`, `PRIVATE`, `VIRTUAL` |
| 后端 `ContestType` (`entity/enums/ContestType.java`) | `ICPC`, `IOI`, `CUSTOM` |
| 后端 `Contest` 实体 `contestType` 字段 | 期望 `ICPC/IOI/CUSTOM` |

- 前端 `ContestsListView.vue` 过滤器使用 `PUBLIC/PRIVATE/VIRTUAL`，**后端无法识别**
- 前端 `CreateContestDto.type` 发送 `PUBLIC/PRIVATE/VIRTUAL`，后端 `CreateContestDTO` 没有 `type` 字段
- 前端 `ContestQueryParams.type` 发送 `PUBLIC/PRIVATE/VIRTUAL`，但后端 `ContestQueryDTO` **没有 `type` 过滤字段**

**建议**: 要么后端采用前端的类型（PUBLIC/PRIVATE/VIRTUAL），要么前端适配后端的类型（ICPC/IOI/CUSTOM），需要产品决策。

### 6. ContestStatus 枚举值及过滤参数不一致

| 位置 | 值 |
|------|---|
| 前端 `ContestStatus` (`contests.ts:9-13`) | `UPCOMING`, `RUNNING`, `FINISHED`（缺少 `DRAFT`, `CANCELLED`）|
| 后端 `ContestStatus` (`entity/enums/ContestStatus.java`) | `DRAFT`, `UPCOMING`, `RUNNING`, `FINISHED`, `CANCELLED` |
| 前端过滤器 (`ContestsListView.vue:67-70`) | `'upcoming'`, `'running'`, `'finished'`（**小写字符串**）|
| 后端 `ContestQueryDTO.status` | `'upcoming'`, `'active'`, `'finished'`, `'cancelled'`（后端用 `active` 而前端用 `running`）|

- 前端发送 `status=running`，后端期望 `status=active` 或 `status=RUNNING`
- 前端 `ContestStatus` 缺少 `DRAFT` 和 `CANCELLED` 状态值
- 后端 `ContestQueryDTO` 的 `allowableValues` 是 `"upcoming", "active", "finished", "cancelled"`，用词是 `active` 而非 `running`

### 7. `UpdateContestDTO` 中 `title` 字段使用了 `@NotBlank`

```java
@NotBlank(message = "Title is required")  // ❌ PATCH 请求不需要强制
private String title;
```

`UpdateContestDTO` 是用于**部分更新**的 PATCH 语义，`title` 不应该有 `@NotBlank` 约束（否则无法在不修改标题的情况下更新其他字段）。

### 8. `ContestVO` 缺少前端所需的多个字段

| 前端 `Contest` 接口字段 | 后端 `ContestVO` | 状态 |
|----------------------|-----------------|------|
| `contestType: ContestType` | ❌ 不存在 | 缺失 |
| `isVisible: boolean` | ❌ 不存在（只有 `isPublished`）| 缺失 |
| `participantCount?: number` | ❌ 不存在（只有 `currentParticipants`）| 缺失 |
| `problemCount?: number` | ❌ 不存在（只有 `problemIds`）| 缺失 |
| `participants?: ContestParticipant[]` | ❌ 不存在 | 缺失 |
| `problems?: ContestProblem[]` | ❌ 不存在（只有 `problemIds`）| 缺失 |
| `endTime?: string` | ✅ 存在（字段名 `endTime`）| OK |
| `createdAt: string` | ✅ 存在 | OK |

### 9. `CreateContestDto` 发送前端不存在的后端字段

前端 `CreateContestDto`（`contests.ts:93-103`）：
```typescript
export interface CreateContestDto {
  slug: string          // ❌ 后端 CreateContestDTO 不存在
  type: ContestType    // ❌ 后端不存在（且值不匹配）
  // ...
  scoringRuleId?: string // ❌ 后端 CreateContestDTO 不存在
}
```

后端 `CreateContestDTO` 缺少 `slug`、`type`（前端用 `PUBLIC/PRIVATE/VIRTUAL`）、`scoringRuleId` 字段。

### 10. `UpdateContestDto` 包含后端不存在的 `type` 字段

前端允许通过 `UpdateContestDto.type` 更新竞赛类型，但后端 `UpdateContestDTO` **没有 `type` 字段**，该字段更新会被静默丢弃。

---

## 🟡 MEDIUM — 中优先级问题

### 11. `problemIds` 类型不匹配

- 前端 `CreateContestDto.problemIds` → `string[]`
- 前端 `ContestVO.problemIds` → `List<Long>` → TS 中是 `number[]`
- 后端 `CreateContestDTO.problemIds` → `List<Long>` → Java 中是 `Long[]`

前端应统一为 `number[]` 类型。

### 12. `ContestRankingVO` 字段名与前端不匹配

| 前端 `ContestRanking` 字段 | 后端 `ContestRankingVO` 字段 | 状态 |
|---------------------------|-----------------------------|------|
| `totalScore: number` | `score: Long` | 字段名不同 |
| `totalPenalty: number` | `penalty: Long` | 字段名不同 |
| `user: { id, username, name }` | `userId`, `username`, `name` (扁平) | 结构不同 |

### 13. 前端 `scoringRulesApi` 响应处理错误

```typescript
async getAll(includeInactive = false): Promise<ScoringRule[]> {
  const response = await apiGet<ScoringRule[]>('/admin/scoring-rules', { ... })
  return response  // ❌ 返回的是 axios 拦截器 unwrap 后的 data，但 scoring-rules API 路径正确
}
```

`ScoringRuleController` 的 `/admin/scoring-rules` 路径匹配，`ScoringRuleVO` 返回的驼峰字段会被 axios 响应拦截器解包后返回，但前端的 `ScoringRule` 接口用蛇形接收 —— 全部字段 `undefined`。

### 14. 前端 `scoringRulesApi.getAll` 没有正确处理 `Result<>` 包装

后端所有 API 统一返回 `Result<T>`，axios 拦截器解包为 `{ code, message, data }` 后只返回 `data`。由于 `ScoringRuleController` 路径正确，实际运行时 `ScoringRuleVO` 的驼峰字段 → 前端蛇形接收 = **全部 `undefined`**。

### 15. ContestRanking 的 user 字段结构不匹配

前端 `ContestRanking` 有嵌套的 `user: { id, username, name }`，后端 `ContestRankingVO` 是扁平结构 `userId`, `username`, `name`。前端 `columns.ts` 和 `ContestRankingsTab.vue` 中的表格列绑定会失败。

---

## 🔵 LOW — 低优先级问题

### 16. 前端 `Contest` 实体 `problemCount` 和 `participantCount` 为可选但后端无对应字段

后端 `ContestVO` 有 `currentParticipants`（非 `participantCount`）和 `problemIds`（非 `problemCount`）。

### 17. 前端 `ContestQueryParams.status` 注释与实际值不符

```typescript
status?: string // 'upcoming' | 'running' | 'finished'
```

注释说只支持三个值，但前端实际发送小写字符串而后端期望不同枚举值。

---

## 对比总览表

```
前端                          后端                         状态
─────────────────────────────────────────────────────────
API 路径前缀
  /admin/contests/*           /contest/*                  🔴 不匹配
  /admin/scoring-rules/*     /admin/scoring-rules/*       🟢 匹配

枚举值
  ContestType: PUBLIC/PRIVATE/VIRTUAL  ICPC/IOI/CUSTOM   🔴 不匹配
  ContestStatus: UPCOMING/RUNNING/FINISHED  全部+ DRAFT/CANCELLED  🟠 部分不匹配
  status 过滤器: running          active                 🔴 不匹配

请求 DTO
  CreateContestDto            CreateContestDTO
    .slug                    ❌ 缺失                      🔴
    .type                    ❌ 缺失                      🔴
    .scoringRuleId           ❌ 缺失                      🔴
    .problemIds: string[]    List<Long>                  🟡
  UpdateContestDto            UpdateContestDTO
    .type                    ❌ 缺失                      🟡
  ScoringRule DTO 字段        ScoringRule DTO
    蛇形命名                  驼峰命名                    🔴 CRITICAL

响应 VO
  ContestVO                   缺少 contestType, isVisible 等  🟠
  ContestRankingVO            字段名 totalScore→score 等    🟡
  ScoringRuleVO 驼峰→前端蛇形   全部字段 undefined          🔴 CRITICAL

缺失端点
  POST /admin/contests/:id/start    ❌ 不存在              🔴
  POST /admin/contests/:id/end      ❌ 不存在              🔴
  POST /admin/contests/:id/problems ❌ 不存在              🔴
  DELETE /admin/contests/:id/problems/:pid ❌ 不存在      🔴
  GET /admin/contests/:id/rankings  ❌ 不存在             🔴
```

---

## 修复优先级建议

**Phase 1（立即修复 — 使功能可用）：**
1. 补全后端缺失的 `/admin/contest` 前缀端点 + 补全缺失的管理员操作端点
2. 对齐 `ScoringRule` 前端接口为驼峰命名
3. 对齐 `ContestType` 枚举（需产品决策：ICPC/IOI/CUSTOM 还是 PUBLIC/PRIVATE/VIRTUAL）
4. 对齐 `ContestStatus` 过滤器参数

**Phase 2（完善功能）：**
5. 补全 `ContestVO` 缺失字段
6. 对齐 `ContestRankingVO` 字段名
7. 修复 `UpdateContestDTO` 的 `@NotBlank` 问题

**Phase 3（长期优化）：**
8. 统一前后端命名规范（建议统一用驼峰，与 Java 后端习惯对齐）
9. 审查其他 admin API 模块（solutions 等）是否存在类似路径问题

---

## 相关文件索引

### 前端 (management/)

| 文件 | 说明 |
|------|------|
| `src/api/admin/contests.ts` | Contest API 调用层，包含类型定义 |
| `src/api/admin/scoring-rules.ts` | Scoring Rule API 调用层 |
| `src/stores/admin/contests.ts` | Contest Pinia Store |
| `src/views/contests/ContestsListView.vue` | 竞赛列表页面 |
| `src/views/contests/ContestDetailView.vue` | 竞赛详情页面 |
| `src/views/contests/columns.ts` | 表格列定义 |
| `src/views/contests/wizard/ContestWizard.vue` | 竞赛创建向导 |
| `src/i18n/locales/zh-CN/modules/contests.ts` | 中文国际化 |
| `src/i18n/locales/en-US/modules/contests.ts` | 英文国际化 |
| `src/api/admin/__tests__/contests.spec.ts` | Contest API 单元测试 |

### 后端 (backend-spring/)

| 文件 | 说明 |
|------|------|
| `modules/contest/controller/ContestController.java` | 竞赛 REST 控制器 |
| `modules/contest/controller/ScoringRuleController.java` | 计分规则 REST 控制器 |
| `modules/contest/entity/Contest.java` | 竞赛实体 |
| `modules/contest/entity/enums/ContestType.java` | 竞赛类型枚举 |
| `modules/contest/entity/enums/ContestStatus.java` | 竞赛状态枚举 |
| `modules/contest/dto/CreateContestDTO.java` | 创建竞赛请求 DTO |
| `modules/contest/dto/UpdateContestDTO.java` | 更新竞赛请求 DTO |
| `modules/contest/dto/ContestVO.java` | 竞赛响应 VO |
| `modules/contest/dto/ContestQueryDTO.java` | 竞赛查询参数 DTO |
| `modules/contest/dto/ContestRankingVO.java` | 竞赛排名响应 VO |
| `modules/contest/dto/ScoringRuleVO.java` | 计分规则响应 VO |
| `modules/contest/service/impl/ContestServiceImpl.java` | 竞赛服务实现 |
| `modules/contest/service/impl/ScoringRuleServiceImpl.java` | 计分规则服务实现 |
| `modules/solution/controller/SolutionController.java` | Solution 控制器（参考路径问题）|
