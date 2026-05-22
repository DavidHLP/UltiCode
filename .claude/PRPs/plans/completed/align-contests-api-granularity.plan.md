# Plan: Align Contests Frontend-Backend API Granularity

## Summary
对齐 Contests 模块前后端 API 颗粒度，修复 6 个幽灵端点、3 组枚举不对齐、4 组 DTO 字段不对齐，引入轻量列表 VO，清理 Controller CRUD 重叠。参照已完成对齐的 Solutions 和 Problem Lists 模块模式。

## User Story
As a Console 前端用户, I want 竞赛列表和详情页能正确加载和显示数据, so that 我可以浏览、参与和管理竞赛。

## Problem → Solution
**当前状态**: Console 前端调用 6 个后端不存在的端点（404），枚举值与后端完全不一致，DTO 字段大量缺失/冗余，列表和详情共用同一重量级 VO。
**目标状态**: 所有前端 API 调用均有对应后端端点，枚举值与后端一一对应，DTO 字段精确匹配，列表端点返回轻量 VO。

## Metadata
- **Complexity**: Large
- **Source PRD**: `docs/contests-api-granularity-analysis.md`
- **PRD Phase**: N/A
- **Estimated Files**: 25+

---

## UX Design

### Before
```
Console 列表页 → GET /contest → 404 错误
Console 详情页 → GET /contest/{slug}/problems → 404 错误
Console 竞赛类型 → 显示 "ICPC" 原始字符串（无枚举标签）
Console 参与状态 → 字段全部 undefined（后端返回不同字段名）
```

### After
```
Console 列表页 → GET /contest → 200 (ContestListVO 轻量响应)
Console 详情页 → GET /contest/{id}/problems → 200 (ContestProblemVO[])
Console 竞赛类型 → 显示 "ICPC" 枚举标签（中文/英文）
Console 参与状态 → 字段完整对齐后端 ParticipationStatusDTO
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| 列表页加载 | 404 错误 | 正常加载 | 修复幽灵端点 |
| 竞赛类型显示 | 原始字符串 | 枚举标签 | 对齐 ContestType |
| 参与状态 | undefined 字段 | 完整数据 | 对齐 ParticipationStatus |
| 列表响应大小 | ~2KB/条目 | ~0.5KB/条目 | ContestListVO |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/contests-api-granularity-analysis.md` | all | 完整分析报告，所有不对齐问题的详细对照 |
| P0 | `backend-spring/.../contest/controller/ContestController.java` | all | 需修改的公共 Controller |
| P0 | `backend-spring/.../contest/controller/AdminContestController.java` | all | 需修改的管理 Controller |
| P0 | `console/src/api/contest.ts` | all | 需重构的 Console API 层 |
| P0 | `console/src/types/contest.ts` | all | 需对齐的 Console 类型定义 |
| P1 | `backend-spring/.../contest/dto/ContestVO.java` | all | 需添加字段的 VO |
| P1 | `backend-spring/.../contest/dto/ParticipationStatusDTO.java` | all | 需对齐的参与状态 DTO |
| P1 | `backend-spring/.../contest/entity/Contest.java` | all | Entity 字段参考 |
| P1 | `backend-spring/.../admin/dto/AdminSolutionListItemVO.java` | all | 轻量列表 VO 参考模式 |
| P1 | `console/src/api/problem-list.ts` | all | 对齐后的 API mapper 参考模式 |
| P2 | `management/src/api/admin/contests.ts` | all | Management API（对齐良好，仅需微调） |
| P2 | `backend-spring/.../contest/service/ContestService.java` | all | Service 接口（需新增方法） |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Spring Boot Jackson camelCase | 项目约定 | 后端默认 camelCase 序列化，前端应统一使用 camelCase |
| Java record for DTO | `AdminSolutionListItemVO.java` | 项目已采用 record 模式创建轻量 VO |

---

## Patterns to Mirror

### LIGHTWEIGHT_LIST_VO (record 模式)
// SOURCE: `backend-spring/.../admin/dto/AdminSolutionListItemVO.java:1-42`
```java
@Schema(description = "Admin solution list item view object")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminSolutionListItemVO(
        @Schema(description = "...") String id,
        @Schema(description = "...") String title,
        // ... 仅包含列表需要的字段
        @Schema(description = "Author information") AuthorInfo author
) {
    @Schema(description = "Author information")
    public record AuthorInfo(...) {}
}
```

### FRONTEND_MAPPER (纯 camelCase 模式)
// SOURCE: `console/src/api/problem-list.ts:62-92`
```typescript
function mapProblemList(input: unknown): ProblemList {
  if (!input || typeof input !== 'object') {
    return { id: '', name: '', description: undefined, problemCount: 0 }
  }
  const raw = input as BackendProblemList
  return {
    id: String(raw.id ?? ''),
    name: String(raw.name ?? ''),
    // ... 直接映射 camelCase 字段，无需 snake_case 兼容
  }
}
```

### FRONTEND_TYPE (纯 camelCase 接口)
// SOURCE: `console/src/types/problem-list.ts:3-20`
```typescript
export interface ProblemList {
  id: string
  name: string
  description?: string
  problemCount: number
  // ... 纯 camelCase，无 snake_case 别名
}
```

### CONTEST_ENTITY_FIELDS (Entity → VO 映射参考)
// SOURCE: `backend-spring/.../contest/entity/Contest.java:1-99`
ContestVO 缺失的 Entity 字段: `penaltyPerWrong`, `scoringMode`, `tieBreaker`, `isRated`, `registeredCount`, `submissionCount`, `isVirtual`, `coverImage`, `rules`, `registrationStart`, `registrationEnd`, `freezeTime`, `actualStartTime`, `actualEndTime`
字段名不一致: Entity `durationMinutes` → VO `duration`, Entity `createdBy` → VO `createdById`

### MANAGEMENT_API_PATTERN (对齐良好的参考)
// SOURCE: `management/src/api/admin/contests.ts:111-153`
Management 前端与后端 AdminContestController 完全对齐，10 个端点全部匹配。Console 应参照此模式重构。

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/.../contest/dto/ContestListVO.java` | CREATE | 轻量列表 VO (record 模式) |
| `backend-spring/.../contest/dto/ContestVO.java` | UPDATE | 添加 Entity 缺失字段 |
| `backend-spring/.../contest/dto/ContestQueryDTO.java` | UPDATE | 添加前端需要的查询参数 |
| `backend-spring/.../contest/controller/ContestController.java` | UPDATE | 修复幽灵端点、列表返回 ContestListVO、移除 CRUD |
| `backend-spring/.../contest/controller/AdminContestController.java` | UPDATE | 列表返回 ContestListVO |
| `backend-spring/.../contest/service/ContestService.java` | UPDATE | 新增 problems/announcements 查询方法、toListVO 方法 |
| `backend-spring/.../contest/service/impl/ContestServiceImpl.java` | UPDATE | 实现新增方法 |
| `backend-spring/.../contest/dto/ParticipationStatusDTO.java` | UPDATE | 添加前端需要的字段 |
| `backend-spring/.../contest/dto/ContestRankingVO.java` | UPDATE | 添加前端需要的字段 |
| `console/src/types/contest.ts` | UPDATE | 对齐枚举值、重构接口定义 |
| `console/src/api/contest.ts` | UPDATE | 重构 mapper、修复端点路径、移除 snake_case 兼容 |
| `console/src/stores/contest/contestStore.ts` | UPDATE | 适配新类型 |
| `console/src/stores/contest/rankingStore.ts` | UPDATE | 适配新类型 |
| `console/src/views/contest/**/*.vue` | UPDATE | 适配新类型（字段名变更） |
| `management/src/api/admin/contests.ts` | UPDATE | problemIds 类型修正 |

## NOT Building
- 后端不添加 `FREEZING`, `ARCHIVED`, `PUBLISHED`, `REGISTERING`, `ONGOING` 等新 ContestStatus 枚举值（需独立 PRD）
- 后端不添加 `CHECKED_IN`, `PARTICIPATING` 等 ParticipantStatus 枚举值（需独立 PRD）
- 后端不实现竞赛提交端点（`POST /contest/{id}/problems/{pid}/submissions`）— 提交逻辑复杂，需独立 PRD
- 后端不实现 check-in 端点（`POST /contest/{slug}/check-in`）— 需独立 PRD
- 前端不添加 `frequency`/`category` 独立字段（超出当前对齐范围）
- 不修改 WebSocket/STOMP 相关代码

---

## Step-by-Step Tasks

### Task 1: 创建 ContestListVO 轻量列表 VO
- **ACTION**: 在 `backend-spring/.../contest/dto/` 创建 `ContestListVO.java`，使用 record 模式
- **IMPLEMENT**: 参照 `AdminSolutionListItemVO` record 模式，包含列表页需要的字段：`id`, `slug`, `title`, `status`, `startTime`, `endTime`, `duration`, `contestType`, `participantCount`, `problemCount`, `isPremium`, `isPublished`, `isVisible`, `maxParticipants`, `currentParticipants`, `isParticipating`, `userRanking`, `registeredCount`, `isRated`, `scoringMode`, `penaltyPerWrong`
- **MIRROR**: `LIGHTWEIGHT_LIST_VO` 模式
- **IMPORTS**: `com.fasterxml.jackson.annotation.JsonInclude`, `io.swagger.v3.oas.annotations.media.Schema`, `java.time.LocalDateTime`
- **GOTCHA**: 使用 `@JsonInclude(JsonInclude.Include.NON_NULL)` 避免返回 null 字段；`duration` 字段名与 ContestVO 保持一致
- **VALIDATE**: 编译通过 `./mvnw compile`；ContestListVO 字段数 < ContestVO 字段数

### Task 2: 补全 ContestVO 缺失的 Entity 字段
- **ACTION**: 在 `ContestVO.java` 中添加 Entity 存在但 VO 未暴露的字段
- **IMPLEMENT**: 添加 `penaltyPerWrong` (Integer), `scoringMode` (String), `tieBreaker` (String), `isRated` (Boolean), `registeredCount` (Integer), `submissionCount` (Integer), `isVirtual` (Boolean), `coverImage` (String), `rules` (String)；将 `duration` 重命名为 `durationMinutes`（与 Entity 一致，或添加 `durationMinutes` 别名字段）
- **MIRROR**: 现有 ContestVO 的 `@Data` + `@JsonInclude` 模式
- **IMPORTS**: 无新增
- **GOTCHA**: `duration` vs `durationMinutes` 命名变更需同步修改 ContestServiceImpl 中的 toVO 映射逻辑；Management 前端 `Contest.duration` 需同步更新
- **VALIDATE**: 编译通过；ContestVO 包含所有 Entity 的业务字段（除软删除字段）

### Task 3: 修复 ContestController 幽灵端点 — 列表端点路径
- **ACTION**: 将 `@GetMapping("/list")` 修改为 `@GetMapping`，使 `GET /contest` 可用；同时保留 `/list` 作为兼容别名
- **IMPLEMENT**: 在 ContestController 中添加 `@GetMapping` 方法（或修改现有 `@GetMapping("/list")` 为 `@GetMapping`），返回类型改为 `PageResult<ContestListVO>`；调用 Service 的 `findAllListVO` 方法
- **MIRROR**: 现有 ContestController 的 `@Operation` + `@ApiResponse` 注解模式
- **IMPORTS**: 无新增
- **GOTCHA**: 需确保 `GET /contest` 不与 `GET /contest/{id}` 冲突（Spring MVC 路径匹配规则：`/list` 子路径不会冲突，但根路径 `/` 可能与 `/{id}` 冲突 — 需测试）
- **VALIDATE**: `curl http://localhost:9001/contest?page=1&pageSize=10` 返回 200；`curl http://localhost:9001/contest/list?page=1&pageSize=10` 仍返回 200

### Task 4: 修复 ContestController 幽灵端点 — problems 和 announcements
- **ACTION**: 添加 `GET /contest/{id}/problems` 和 `GET /contest/{id}/announcements` 端点
- **IMPLEMENT**: 在 ContestController 中添加两个 GET 端点；problems 端点调用 ContestService 获取竞赛题目列表（返回 `List<ContestProblemVO>`）；announcements 端点调用 ContestAnnouncementMapper 查询（返回 `List<ContestAnnouncementVO>`，需新建此 VO 或复用 Entity）
- **MIRROR**: 现有 ContestController 的公共端点模式（无权限注解、可选 userId）
- **IMPORTS**: `ContestProblemMapper`, `ContestAnnouncementMapper`
- **GOTCHA**: ContestProblemVO 当前只有 4 个字段（id, contestId, problemId, problemIndex, score），前端期望更多字段（title, slug, difficulty, solvedCount, submissionCount）— 需扩展 ContestProblemVO 或新建 ContestProblemDetailVO
- **VALIDATE**: `curl http://localhost:9001/contest/{id}/problems` 返回 200；`curl http://localhost:9001/contest/{id}/announcements` 返回 200

### Task 5: ContestService 新增列表 VO 转换方法
- **ACTION**: 在 ContestService 接口和实现中添加 `toListVO` 方法和 `findAllListVO` 方法
- **IMPLEMENT**: `ContestListVO toListVO(Contest contest, String userId)` — 将 Entity 转换为轻量 VO；修改 `findAll`, `findUpcoming`, `findRunning`, `findPast` 返回 `ContestListVO`（或新增对应方法）
- **MIRROR**: 现有 `toVO` 方法的映射模式
- **IMPORTS**: `ContestListVO`
- **GOTCHA**: 列表端点切换返回类型会影响所有调用方 — 需同步修改 Controller 返回类型；AdminContestController 的列表端点也应切换
- **VALIDATE**: 编译通过；列表端点返回的 JSON 字段数 < 详情端点

### Task 6: ContestQueryDTO 添加前端查询参数
- **ACTION**: 在 ContestQueryDTO 中添加 Console 前端 `getContests` 发送但后端不支持的参数
- **IMPLEMENT**: 添加 `isRated` (Boolean), `isPublic` (Boolean, 映射到 isVisible), `startDateFrom` (String/LocalDateTime), `startDateTo` (String/LocalDateTime), `limit` (Integer, 作为 pageSize 别名)；在 Controller 中处理 `limit` → `pageSize` 映射
- **MIRROR**: 现有 ContestQueryDTO 的 `@Schema` 注解模式
- **IMPORTS**: 无新增
- **GOTCHA**: `isPublic` 在 Entity 中对应 `isVisible`，需在 Service 层做字段映射；`limit` 和 `pageSize` 需在 Controller 中合并处理
- **VALIDATE**: `curl "http://localhost:9001/contest?isRated=true&limit=10"` 返回 200

### Task 7: 移除 ContestController 中的重复 CRUD 端点
- **ACTION**: 移除 ContestController 中的 `POST /contest`, `PUT /contest/{id}`, `DELETE /contest/{id}` 端点
- **IMPLEMENT**: 删除 ContestController 中的 `createContest`, `updateContest`, `deleteContest` 方法及其注解；确认 AdminContestController 中有对应端点
- **MIRROR**: AdminContestController 已有完整 CRUD，无需额外添加
- **IMPORTS**: 无变更
- **GOTCHA**: 需确认没有其他客户端直接调用 `POST /contest` 等（检查前端代码 — Console 不调用，Management 使用 `/admin/contest`）
- **VALIDATE**: `curl -X POST http://localhost:9001/contest` 返回 404；`curl -X POST http://localhost:9001/admin/contest` 仍返回 200/400

### Task 8: Console ContestType 枚举对齐
- **ACTION**: 将 Console `ContestType` 枚举从 6 值改为与后端一致的 3 值
- **IMPLEMENT**: 修改 `console/src/types/contest.ts` 中 `ContestType` enum 为 `ICPC = "ICPC", IOI = "IOI", CUSTOM = "CUSTOM"`；更新所有引用 `ContestType.WEEKLY` 等的组件
- **MIRROR**: Management 前端的 `ContestFormat = 'ICPC' | 'IOI' | 'CUSTOM'` 模式
- **IMPORTS**: 无变更
- **GOTCHA**: 需搜索所有使用 `ContestType.WEEKLY` 等的组件并更新；i18n 翻译文件需同步更新
- **VALIDATE**: `pnpm type-check` 通过；无 ContestType.WEEKLY 引用残留

### Task 9: Console ContestStatus 枚举对齐
- **ACTION**: 将 Console `ContestStatus` 枚举从 9 值改为与后端一致的 5 值
- **IMPLEMENT**: 修改 `ContestStatus` enum 为 `DRAFT, UPCOMING, RUNNING, FINISHED, CANCELLED`；移除 `PUBLISHED, REGISTERING, ONGOING, FREEZING, ARCHIVED`；更新所有引用的组件和 i18n
- **MIRROR**: Management 前端的 `ContestStatus = 'DRAFT' | 'UPCOMING' | 'RUNNING' | 'FINISHED' | 'CANCELLED'` 模式
- **IMPORTS**: 无变更
- **GOTCHA**: `ContestStatusBadge.vue` 和 `ContestTimer.vue` 可能依赖 `ONGOING`/`FREEZING` 状态做 UI 判断 — 需改为使用 `RUNNING` + 额外条件
- **VALIDATE**: `pnpm type-check` 通过；无已移除状态的引用残留

### Task 10: Console ParticipantStatus 枚举对齐
- **ACTION**: 将 Console `ParticipantStatus` 枚举从 6 值改为与后端一致的 4 值
- **IMPLEMENT**: 修改 `ParticipantStatus` enum 为 `REGISTERED, STARTED, FINISHED, DISQUALIFIED`；移除 `CHECKED_IN, PARTICIPATING`；更新引用组件
- **MIRROR**: 后端 `ContestParticipantStatus` 枚举
- **IMPORTS**: 无变更
- **GOTCHA**: `ContestRegistration.vue` 可能依赖 `CHECKED_IN` 状态 — 需改为使用 `STARTED` 或移除 check-in 相关 UI
- **VALIDATE**: `pnpm type-check` 通过

### Task 11: Console ContestListItem 类型重构
- **ACTION**: 重构 `ContestListItem` 接口，移除 snake_case 别名，对齐后端 ContestVO 字段
- **IMPLEMENT**: 将 `ContestListItem` 改为纯 camelCase 接口，字段与后端 ContestVO 一致；移除 `start_time`, `duration_minutes`, `penalty_per_wrong`, `scoring_mode`, `tie_breaker`, `registered_count`, `participant_count`, `is_rated`, `cover_image`, `rules`, `contest_type` 等 snake_case 字段；添加 `contestType`, `isPremium`, `isPublished`, `createdAt`, `updatedAt`, `tags`, `isParticipating`, `userRanking`, `userScore`, `problemCount`, `scoringRuleId` 等缺失字段
- **MIRROR**: `FRONTEND_TYPE` 模式（纯 camelCase，参照 `problem-list.ts`）
- **IMPORTS**: 无变更
- **GOTCHA**: `ContestDetail extends ContestListItem` — 修改 ContestListItem 会影响 ContestDetail；`ContestCard.vue` 等组件使用 snake_case 字段 — 需同步更新
- **VALIDATE**: `pnpm type-check` 通过；接口字段与后端 ContestVO 一一对应

### Task 12: Console ParticipationStatus 类型对齐
- **ACTION**: 重构 `ParticipationStatus` 接口，对齐后端 `ParticipationStatusDTO` 字段
- **IMPLEMENT**: 替换为后端字段：`contestId`, `title`, `status`, `registeredAt`, `startedAt`, `completedAt`, `startTime`, `endTime`, `ranking`, `score`, `problemsSolved`, `totalProblems`, `hasStarted`, `isActive`, `isCompleted`, `canParticipate`；移除 `isRegistered`, `participantId`, `virtualSessionId`, `totalScore`, `totalPenalty`
- **MIRROR**: 后端 `ParticipationStatusDTO` 字段定义
- **IMPORTS**: 无变更
- **GOTCHA**: `ContestRegistration.vue` 和 `ContestDetailView.vue` 使用 `isRegistered`, `totalScore` 等旧字段 — 需改为使用 `status === 'REGISTERED'`, `score` 等
- **VALIDATE**: `pnpm type-check` 通过

### Task 13: Console ContestRankingEntry 类型对齐
- **ACTION**: 重构 `ContestRankingEntry` 接口，对齐后端 `ContestRankingVO` 字段名
- **IMPLEMENT**: 将 `totalScore` → `score`, `totalPenalty` → `penalty`, `solvedCount` → `problemsSolved`；添加 `isCurrentUser`, `progress`, `percentile`, `isParticipating`, `maxRating`, `ratingTitle`, `contestsAttended`, `badge`；移除 `ratingBefore`, `ratingAfter`, `ratingChange`, `isVirtual`, `problemResults`（后端不返回）
- **MIRROR**: 后端 `ContestRankingVO` 字段定义
- **IMPORTS**: 无变更
- **GOTCHA**: `ContestRankingTable.vue` 使用 `totalScore`, `totalPenalty`, `solvedCount` — 需改为 `score`, `penalty`, `problemsSolved`
- **VALIDATE**: `pnpm type-check` 通过

### Task 14: Console API mapper 重构
- **ACTION**: 重构 `console/src/api/contest.ts` 中的 mapper 函数，移除 snake_case 兼容逻辑
- **IMPLEMENT**: 参照 `problem-list.ts` 的 `mapProblemList` 模式，重写 `mapContestListItem` 为纯 camelCase 映射；移除 `BackendContestListItem` 中的 snake_case 字段声明；简化 `toNumber` 辅助函数；修复 `getContests` 的 URL 从 `/contest` 到 `/contest`（Task 3 已修复后端路径）
- **MIRROR**: `FRONTEND_MAPPER` 模式
- **IMPORTS**: 无变更
- **GOTCHA**: `mapContestListItem` 当前计算 `endTime`（从 startTime + durationMinutes 推算）— 后端 ContestVO 已有 `endTime` 字段，可移除此计算逻辑
- **VALIDATE**: `pnpm type-check` 通过；`pnpm test` 通过

### Task 15: Console PaginatedResult 对齐
- **ACTION**: 将 Console `PaginatedResult<T>` 的 `limit` 字段改为 `pageSize`，与后端 `PageResult` 一致
- **IMPLEMENT**: 修改 `PaginatedResult<T>` 接口：`limit` → `pageSize`；更新所有使用 `result.limit` 的代码为 `result.pageSize`
- **MIRROR**: Management 前端的 `PageResult<T>` 格式
- **IMPORTS**: 无变更
- **GOTCHA**: `contestStore.ts` 中 `meta.limit` 需改为 `meta.pageSize`
- **VALIDATE**: `pnpm type-check` 通过

### Task 16: Console 组件适配新类型
- **ACTION**: 更新所有使用旧字段名的 Vue 组件
- **IMPLEMENT**: 搜索并替换：`contest.start_time` → `contest.startTime`, `contest.duration_minutes` → `contest.duration`, `contest.contest_type` → `contest.contestType`, `contest.is_rated` → `contest.isRated`, `ranking.totalScore` → `ranking.score`, `ranking.totalPenalty` → `ranking.penalty`, `ranking.solvedCount` → `ranking.problemsSolved`, `participation.isRegistered` → `participation.status === 'REGISTERED'`, `participation.totalScore` → `participation.score`
- **MIRROR**: Management 前端组件的字段使用模式
- **IMPORTS**: 无变更
- **GOTCHA**: 部分组件可能在 template 和 script 中都使用旧字段 — 需全面搜索
- **VALIDATE**: `pnpm type-check` 通过；`pnpm dev` 页面正常渲染

### Task 17: Management 前端 problemIds 类型修正
- **ACTION**: 将 Management `Contest.problemIds` 从 `string[]` 改为 `number[]`
- **IMPLEMENT**: 修改 `management/src/api/admin/contests.ts` 中 `Contest` 接口的 `problemIds` 类型为 `number[]`；同步修改 `CreateContestDto.problemIds` 和 `UpdateContestDto.problemIds`
- **MIRROR**: 后端 `ContestVO.problemIds` 类型为 `List<Long>`
- **IMPORTS**: 无变更
- **GOTCHA**: JSON 序列化 Long 可能超出 JS Number 精度 — 但竞赛题目 ID 通常在安全范围内
- **VALIDATE**: `pnpm type-check` 通过

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| ContestListVO 序列化 | Contest entity with all fields | JSON 只包含列表字段 | ✅ |
| ContestVO 序列化 | Contest entity with all fields | JSON 包含所有业务字段 | ✅ |
| GET /contest?page=1 | 有效分页参数 | 200 + PageResult<ContestListVO> | ❌ |
| GET /contest/{id}/problems | 有效竞赛 ID | 200 + List<ContestProblemVO> | ❌ |
| GET /contest/{id}/announcements | 有效竞赛 ID | 200 + List<ContestAnnouncement> | ❌ |
| ContestType 枚举映射 | "ICPC" | ContestType.ICPC | ✅ |
| ContestStatus 枚举映射 | "RUNNING" | ContestStatus.RUNNING | ✅ |
| mapContestListItem | 后端 camelCase 响应 | 正确映射所有字段 | ✅ |
| ParticipationStatus 对齐 | 后端 ParticipationStatusDTO | 前端接口字段完整 | ✅ |

### Edge Cases Checklist
- [ ] ContestListVO 中 null 字段不序列化
- [ ] GET /contest 无参数时使用默认分页
- [ ] ContestType 无效值处理（显示原始字符串）
- [ ] ContestStatus 无效值处理
- [ ] ParticipationStatus status 为 null 时前端处理
- [ ] ContestRankingEntry score/penalty 为 0 时显示
- [ ] 空竞赛列表（upcoming/running/past 返回空数组）

---

## Validation Commands

### Static Analysis
```bash
cd backend-spring && ./mvnw compile -DskipTests
```
EXPECT: Zero compilation errors

### Backend Tests
```bash
cd backend-spring && ./mvnw test -pl . -Dtest="Contest*Test"
```
EXPECT: All contest tests pass

### Frontend Type Check
```bash
cd console && pnpm type-check
cd management && pnpm type-check
```
EXPECT: Zero type errors

### Frontend Lint
```bash
cd console && pnpm lint
cd management && pnpm lint
```
EXPECT: Zero lint errors

### Frontend Unit Tests
```bash
cd console && pnpm test
cd management && pnpm test
```
EXPECT: All tests pass

### Manual Validation
- [ ] Console 竞赛列表页正常加载（GET /contest 返回 200）
- [ ] Console 竞赛详情页题目列表正常显示
- [ ] Console 竞赛详情页公告正常显示
- [ ] Console 竞赛类型显示为枚举标签（ICPC/IOI/CUSTOM）
- [ ] Console 参与状态正确显示（使用后端字段）
- [ ] Console 排名表正确显示（score/penalty/problemsSolved）
- [ ] Management 竞赛列表正常（无回归）
- [ ] Management 竞赛详情正常（无回归）

---

## Acceptance Criteria
- [ ] 所有 17 个 Task 完成
- [ ] Console 前端无幽灵端点调用（所有 API 调用后端均有对应端点）
- [ ] Console ContestType 枚举与后端一致（ICPC/IOI/CUSTOM）
- [ ] Console ContestStatus 枚举与后端一致（5 值）
- [ ] Console ParticipationStatus 与后端 ParticipationStatusDTO 字段对齐
- [ ] Console ContestRankingEntry 与后端 ContestRankingVO 字段名对齐
- [ ] 后端列表端点返回 ContestListVO（轻量）
- [ ] 后端详情端点返回 ContestVO（完整）
- [ ] ContestController 无重复 CRUD 端点
- [ ] 所有 validation commands 通过
- [ ] 无类型错误、无 lint 错误
- [ ] Management 前端无回归

## Completion Checklist
- [ ] 代码遵循项目已有对齐模式（参照 Solutions/Problem Lists）
- [ ] 错误处理与项目风格一致
- [ ] 前端 mapper 遵循纯 camelCase 模式
- [ ] 后端 VO 遵循 record 模式（新文件）
- [ ] 无硬编码值
- [ ] i18n 翻译文件同步更新
- [ ] 无不必要的范围扩展
- [ ] 自包含 — 实施期间无需额外搜索代码库

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| GET /contest 与 GET /contest/{id} 路径冲突 | Medium | High | 测试 Spring MVC 路径匹配；如有冲突保留 /list 路径并修改前端 |
| ContestVO duration → durationMinutes 重命名破坏 Management | Medium | Medium | Management 前端同步更新 Contest.duration → Contest.durationMinutes |
| ContestStatus 移除 ONGOING/FREEZING 破坏 UI 逻辑 | Medium | Medium | 审查 ContestTimer/ContestStatusBadge 组件，用 RUNNING 替代 |
| ParticipationStatus 字段重命名破坏 ContestDetailView | High | Medium | 逐个组件搜索并更新字段引用 |
| snake_case 移除破坏仍在使用旧字段的组件 | Medium | Medium | 全面搜索 `contest.start_time` 等旧字段引用 |

## Notes
- 参照已完成对齐的 Solutions（commit 51643e95f）和 Problem Lists（commit 73f77bfff）模块模式
- Contest 模块比 Solutions/Problem Lists 更复杂（22 个端点 vs 10 个），需更谨慎的增量修改
- 竞赛提交端点（submissions）和 check-in 端点不在本次对齐范围内，需独立 PRD
- 后端 Contest entity 有 `isPremium`/`isPublished`/`publishedAt` 字段但 ContestVO 中无对应 Entity 字段 — 这些可能是 VO 附加字段或 Entity 缺失字段，需在实施时确认
