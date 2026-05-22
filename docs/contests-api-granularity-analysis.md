# Contests API 颗粒度对齐分析报告

> 生成时间: 2026-05-21 | 分析范围: ContestController + AdminContestController + ScoringRuleController ↔ Console Frontend + Management Frontend

## 执行摘要

Contest 模块前后端 API 存在 **7 个关键颗粒度不对齐问题**，主要集中在：枚举值不一致、DTO 字段缺失/冗余、幽灵端点（前端调用但后端不存在）、以及 ContestVO 返回数据过重（列表和详情共用同一 VO）。Management 前端对齐较好，Console 前端问题较多。

---

## 1. 枚举值不对齐

### 1.1 ContestType 枚举（严重）

| 位置 | 值 |
|------|-----|
| **后端** `ContestType` enum | `ICPC`, `IOI`, `CUSTOM` |
| **Console 前端** `ContestType` enum | `WEEKLY`, `BIWEEKLY`, `MONTHLY`, `THEMED`, `CORPORATE`, `CAMPUS` |
| **Management 前端** `ContestFormat` type | `'ICPC' \| 'IOI' \| 'CUSTOM'` |

**问题**: Console 前端定义了 6 种竞赛类型（频率/场景分类），后端只支持 3 种（赛制分类）。两者语义完全不同。Management 前端与后端一致。

**影响**: Console 的 `ContestType` 枚举值永远不会从后端返回，所有 `type`/`contestType` 字段在前端显示为原始字符串而非枚举标签。

**建议**: Console 前端 `ContestType` 应与后端对齐为 `ICPC | IOI | CUSTOM`。如需频率分类，应作为独立字段（如 `frequency` 或 `category`）。

### 1.2 ContestStatus 枚举（中等）

| 位置 | 值 |
|------|-----|
| **后端** `ContestStatus` enum | `DRAFT`, `UPCOMING`, `RUNNING`, `FINISHED`, `CANCELLED` |
| **Console 前端** `ContestStatus` enum | `DRAFT`, `PUBLISHED`, `REGISTERING`, `UPCOMING`, `ONGOING`, `RUNNING`, `FREEZING`, `FINISHED`, `ARCHIVED` |
| **Management 前端** `ContestStatus` type | `'DRAFT' \| 'UPCOMING' \| 'RUNNING' \| 'FINISHED' \| 'CANCELLED'` |

**问题**: Console 前端定义了 9 种状态（包含 `PUBLISHED`, `REGISTERING`, `ONGOING`, `FREEZING`, `ARCHIVED`），后端只有 5 种。Console 多出的状态在当前后端不会返回。

**建议**: Console 前端 `ContestStatus` 应与后端对齐。如需更细粒度状态（如 `FREEZING`），应先在后端添加对应枚举值。

### 1.3 ParticipantStatus 枚举（轻微）

| 位置 | 值 |
|------|-----|
| **后端** `ContestParticipantStatus` | `REGISTERED`, `STARTED`, `FINISHED`, `DISQUALIFIED` |
| **Console 前端** `ParticipantStatus` | `REGISTERED`, `CHECKED_IN`, `STARTED`, `PARTICIPATING`, `FINISHED`, `DISQUALIFIED` |

**问题**: Console 多出 `CHECKED_IN` 和 `PARTICIPATING` 状态。后端无 `CHECKED_IN` 枚举值，但前端有 `checkIn` API 调用。

---

## 2. DTO 字段不对齐

### 2.1 ContestVO vs Console ContestListItem（严重）

后端 `ContestVO` 返回的字段与 Console 前端 `ContestListItem` 期望的字段存在显著差异：

| 字段 | 后端 ContestVO | Console ContestListItem | 状态 |
|------|---------------|----------------------|------|
| `id` | `String` | `string` | ✅ 对齐 |
| `slug` | `String` | `string` | ✅ 对齐 |
| `title` | `String` | `string` | ✅ 对齐 |
| `description` | `String` | `string?` | ✅ 对齐 |
| `status` | `String` | `ContestStatus \| string` | ⚠️ 枚举不对齐 |
| `startTime` | `LocalDateTime` | `string` (camelCase alias) | ✅ 对齐（需 mappers） |
| `endTime` | `LocalDateTime` | `string?` (camelCase alias) | ✅ 对齐 |
| `duration` | `Integer` | `number` (as `durationMinutes`) | ❌ **字段名不同** |
| `maxParticipants` | `Integer` | — | ❌ 前端缺失 |
| `currentParticipants` | `Integer` | — | ❌ 前端缺失 |
| `isPremium` | `Boolean` | — | ❌ 前端缺失 |
| `isPublished` | `Boolean` | — | ❌ 前端缺失 |
| `publishedAt` | `LocalDateTime` | — | ❌ 前端缺失 |
| `createdAt` | `LocalDateTime` | — | ❌ 前端缺失 |
| `updatedAt` | `LocalDateTime` | — | ❌ 前端缺失 |
| `createdById` | `Long` | — | ❌ 前端缺失 |
| `createdByUsername` | `String` | — | ❌ 前端缺失 |
| `problemIds` | `List<Long>` | — | ❌ 前端缺失 |
| `tags` | `List<String>` | — | ❌ 前端缺失 |
| `isParticipating` | `Boolean` | — | ❌ 前端缺失 |
| `userRanking` | `Integer` | — | ❌ 前端缺失 |
| `userScore` | `Long` | — | ❌ 前端缺失 |
| `contestType` | `String` | `ContestType \| string` | ⚠️ 枚举不对齐 |
| `isVisible` | `Boolean` | — | ❌ 前端缺失 |
| `participantCount` | `Integer` | `number` | ✅ 对齐 |
| `problemCount` | `Integer` | — | ❌ 前端缺失 |
| `scoringRuleId` | `String` | — | ❌ 前端缺失 |
| — | — | `start_time` (snake_case) | ⚠️ 后端返回 camelCase |
| — | — | `duration_minutes` | ❌ 后端用 `duration` |
| — | — | `penalty_per_wrong` | ❌ 后端 ContestVO 无此字段 |
| — | — | `scoring_mode` | ❌ 后端 ContestVO 无此字段 |
| — | — | `tie_breaker` | ❌ 后端 ContestVO 无此字段 |
| — | — | `registered_count` | ❌ 后端 ContestVO 无此字段 |
| — | — | `is_rated` | ❌ 后端 ContestVO 无此字段 |
| — | — | `cover_image` | ❌ 后端 ContestVO 无此字段 |
| — | — | `rules` | ❌ 后端 ContestVO 无此字段 |
| — | — | `canRegister` | ❌ 后端 ContestVO 无此字段（前端自行计算） |
| — | — | `canStart` | ❌ 后端 ContestVO 无此字段（前端自行计算） |

**核心问题**:
1. **ContestVO 字段名 `duration` vs 前端 `duration_minutes`/`durationMinutes`** — 后端用 `duration`，前端期望 `duration_minutes`
2. **前端期望多个后端 ContestVO 不存在的字段** — `penalty_per_wrong`, `scoring_mode`, `tie_breaker`, `registered_count`, `is_rated`, `cover_image`, `rules` — 这些字段在 `Contest` entity 中存在但 ContestVO 未暴露
3. **ContestVO 返回了列表页不需要的过多字段** — 列表页不需要 `description`, `problemIds`, `tags`, `isParticipating`, `userRanking`, `userScore` 等

### 2.2 ContestVO vs Management Contest（良好）

Management 前端的 `Contest` 接口与后端 `ContestVO` 对齐较好：

| 字段 | 后端 ContestVO | Management Contest | 状态 |
|------|---------------|-------------------|------|
| `id` | `String` | `string` | ✅ |
| `slug` | `String` | `string` | ✅ |
| `title` | `String` | `string` | ✅ |
| `description` | `String` | `string?` | ✅ |
| `contestType` | `String` | `ContestFormat` | ✅ |
| `startTime` | `LocalDateTime` | `string` | ✅ |
| `endTime` | `LocalDateTime` | `string?` | ✅ |
| `duration` | `Integer` | `number` | ✅ |
| `status` | `String` | `ContestStatus` | ✅ |
| `isVisible` | `Boolean` | `boolean` | ✅ |
| `isPremium` | `Boolean` | `boolean` | ✅ |
| `isPublished` | `Boolean` | `boolean` | ✅ |
| `participantCount` | `Integer` | `number` | ✅ |
| `problemCount` | `Integer` | `number` | ✅ |
| `maxParticipants` | `Integer` | `number?` | ✅ |
| `currentParticipants` | `Integer` | `number?` | ✅ |
| `scoringRuleId` | `String` | `string?` | ✅ |
| `problemIds` | `List<Long>` | `string[]?` | ⚠️ 类型不同 (Long vs string) |
| `tags` | `List<String>` | `string[]?` | ✅ |
| `createdAt` | `LocalDateTime` | `string` | ✅ |
| `updatedAt` | `LocalDateTime` | `string` | ✅ |
| `createdById` | `Long` | `number?` | ✅ |
| `createdByUsername` | `String` | `string?` | ✅ |
| `isParticipating` | `Boolean` | `boolean?` | ✅ |
| `userRanking` | `Integer` | `number?` | ✅ |
| `userScore` | `Long` | `number?` | ✅ |

**唯一问题**: `problemIds` 类型 — 后端 `List<Long>` vs 前端 `string[]`，JSON 序列化时 Long 可能超出 JS Number 精度。

### 2.3 ParticipationStatusDTO vs Console ParticipationStatus（严重）

| 字段 | 后端 ParticipationStatusDTO | Console ParticipationStatus | 状态 |
|------|---------------------------|--------------------------|------|
| `contestId` | `Long` | — | ❌ 前端缺失 |
| `title` | `String` | — | ❌ 前端缺失 |
| `status` | `String` | `ParticipantStatus \| string \| null` | ⚠️ 枚举不对齐 |
| `registeredAt` | `LocalDateTime` | — | ❌ 前端缺失 |
| `startedAt` | `LocalDateTime` | `string \| null` | ✅ |
| `completedAt` | `LocalDateTime` | — | ❌ 前端缺失 |
| `startTime` | `LocalDateTime` | — | ❌ 前端缺失 |
| `endTime` | `LocalDateTime` | — | ❌ 前端缺失 |
| `ranking` | `Integer` | — | ❌ 前端缺失 |
| `score` | `Long` | — | ❌ 前端缺失 |
| `problemsSolved` | `Integer` | — | ❌ 前端缺失 |
| `totalProblems` | `Integer` | — | ❌ 前端缺失 |
| `hasStarted` | `Boolean` | — | ❌ 前端缺失 |
| `isActive` | `Boolean` | — | ❌ 前端缺失 |
| `isCompleted` | `Boolean` | — | ❌ 前端缺失 |
| `canParticipate` | `Boolean` | — | ❌ 前端缺失 |
| — | — | `isRegistered` | ❌ 后端无此字段 |
| — | — | `participantId` | ❌ 后端无此字段 |
| — | — | `virtualSessionId` | ❌ 后端无此字段 |
| — | — | `totalScore` | ❌ 后端用 `score` |
| — | — | `totalPenalty` | ❌ 后端无此字段 |

**核心问题**: Console 前端的 `ParticipationStatus` 接口与后端 `ParticipationStatusDTO` 几乎完全不对齐。前端期望的字段（`isRegistered`, `participantId`, `virtualSessionId`, `totalScore`, `totalPenalty`）后端不返回；后端返回的字段（`contestId`, `title`, `hasStarted`, `isActive`, `isCompleted`, `canParticipate` 等）前端不使用。

### 2.4 ContestRankingVO vs Console ContestRankingEntry（中等）

| 字段 | 后端 ContestRankingVO | Console ContestRankingEntry | 状态 |
|------|----------------------|---------------------------|------|
| `rank` | `Integer` | `number` | ✅ |
| `userId` | `String` | `string` | ✅ |
| `username` | `String` | `string` | ✅ |
| `avatar` | `String` | `string \| null` | ✅ |
| `score` | `Long` | `number` (as `totalScore` alias) | ⚠️ 字段名不同 |
| `penalty` | `Long` | `number` (as `totalPenalty` alias) | ⚠️ 字段名不同 |
| `problemsSolved` | `Integer` | `number` (as `solvedCount`) | ⚠️ 字段名不同 |
| `country` | `String` | `string?` | ✅ |
| `isCurrentUser` | `Boolean` | — | ❌ 前端缺失 |
| `progress` | `BigDecimal` | — | ❌ 前端缺失 |
| `percentile` | `BigDecimal` | — | ❌ 前端缺失 |
| `isParticipating` | `Boolean` | — | ❌ 前端缺失 |
| `maxRating` | `Integer` | — | ❌ 前端缺失 |
| `ratingTitle` | `String` | — | ❌ 前端缺失 |
| `contestsAttended` | `Integer` | — | ❌ 前端缺失 |
| `badge` | `String` | — | ❌ 前端缺失 |
| — | — | `totalScore` | ❌ 后端用 `score` |
| — | — | `totalPenalty` | ❌ 后端用 `penalty` |
| — | — | `solvedCount` | ❌ 后端用 `problemsSolved` |
| — | — | `ratingBefore` | ❌ 后端无此字段 |
| — | — | `ratingAfter` | ❌ 后端无此字段 |
| — | — | `ratingChange` | ❌ 后端无此字段 |
| — | — | `isVirtual` | ❌ 后端无此字段 |
| — | — | `problemResults` | ❌ 后端无此字段 |

---

## 3. 幽灵端点（前端调用但后端不存在）

| # | 前端 | API 调用 | 后端状态 | 严重度 |
|---|------|---------|---------|--------|
| 1 | Console | `GET /contest` (getContests) | ❌ 不存在。后端只有 `GET /contest/list` | **严重** |
| 2 | Console | `GET /contest/{slug}/problems` | ❌ 不存在。后端无 problems 子资源端点 | **严重** |
| 3 | Console | `GET /contest/{slug}/announcements` | ❌ 不存在。后端无 announcements 端点 | **严重** |
| 4 | Console | `POST /contest/{slug}/check-in` | ❌ 不存在。后端无 check-in 端点 | **中等** |
| 5 | Console | `POST /contest/{contestId}/problems/{problemId}/submissions` | ❌ 不存在。后端 ContestController 无提交端点 | **严重** |
| 6 | Console | `GET /contest/{contestId}/problems/{problemId}/submissions` | ❌ 不存在。后端 ContestController 无提交查询端点 | **严重** |

**说明**: Console 新版 API (Task 4.1) 的 `getContests` 调用 `GET /contest`，但后端 ContestController 的列表端点是 `GET /contest/list`。这会导致 404 错误。

---

## 4. ContestVO 颗粒度问题（列表 vs 详情共用同一 VO）

### 4.1 问题描述

后端所有竞赛查询端点（列表、详情、upcoming、running、past）都返回 `ContestVO`，包含 20+ 个字段。但不同页面需要的数据颗粒度差异很大：

| 端点 | 页面用途 | 实际需要的字段 | 不需要的字段 |
|------|---------|--------------|------------|
| `GET /contest/upcoming` | 列表卡片 | id, title, slug, status, startTime, endTime, duration, contestType, participantCount | description, problemIds, tags, isParticipating, userRanking, userScore, scoringRuleId, publishedAt, createdById |
| `GET /contest/running` | 列表卡片 | 同上 | 同上 |
| `GET /contest/past` | 列表卡片 | 同上 + isParticipating, userRanking | description, problemIds, tags, scoringRuleId |
| `GET /contest/{id}` | 详情页 | 所有字段 | — |
| `GET /contest/list` | 通用列表 | id, title, slug, status, startTime, endTime, duration, contestType, participantCount, isPremium | isParticipating, userRanking, userScore, problemIds, tags |
| `GET /admin/contest` | 管理列表 | id, title, slug, status, startTime, endTime, duration, contestType, isPublished, isVisible, participantCount, problemCount | isParticipating, userRanking, userScore, problemIds, tags, description |

### 4.2 建议

引入轻量级列表 DTO：

```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContestListVO {
    private String id;
    private String slug;
    private String title;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer duration;
    private String contestType;
    private Integer participantCount;
    private Integer problemCount;
    private Boolean isPremium;
    private Boolean isPublished;
    private Boolean isVisible;
}
```

列表端点返回 `ContestListVO`，详情端点返回 `ContestVO`。

---

## 5. Controller 职责重叠

### 5.1 ContestController vs AdminContestController

两个 Controller 都暴露了 CRUD 操作，且都需要 ADMIN 权限：

| 操作 | ContestController | AdminContestController |
|------|------------------|----------------------|
| Create | `POST /contest` | `POST /admin/contest` |
| Update | `PUT /contest/{id}` | `PATCH /admin/contest/{id}` |
| Delete | `DELETE /contest/{id}` | `DELETE /admin/contest/{id}` |

**问题**:
1. **重复端点** — 同一操作有两个入口，增加维护成本
2. **HTTP 方法不一致** — ContestController 用 `PUT` 更新，AdminContestController 用 `PATCH` 更新
3. **Management 前端使用 Admin 路径** — `contestsApi.updateContest` 调用 `PATCH /admin/contest/{id}`，与 AdminContestController 对齐
4. **Console 前端不调用 CRUD** — Console 不创建/更新/删除竞赛，只查询和参与

**建议**: 移除 `ContestController` 中的 CRUD 端点（`POST /contest`, `PUT /contest/{id}`, `DELETE /contest/{id}`），统一由 `AdminContestController` 管理。ContestController 只保留公共查询和用户参与端点。

---

## 6. 前端 API 参数不对齐

### 6.1 列表查询参数

| 参数 | 后端 ContestQueryDTO | Console getContests | Management getContests | 状态 |
|------|---------------------|--------------------|-----------------------|------|
| `page` | `Integer` | `number` | `number` | ✅ |
| `pageSize` | `Integer` | — (用 `limit`) | `number` | ⚠️ Console 用 `limit` |
| `status` | `String` | `ContestStatus \| ContestStatus[]` | `string` | ⚠️ Console 支持数组 |
| `search` | `String` | `string` | `string` | ✅ |
| `sort` | `String` | — | — | ❌ Console 不传 |
| `direction` | `String` | — | `'asc' \| 'desc'` | ⚠️ |
| `contestType` | `String` | `ContestType \| ContestType[]` | `ContestFormat` | ⚠️ Console 支持数组 |
| `sortBy` | `String` (alias for sort) | `string` | `string` | ✅ |
| `isPremium` | `Boolean` | — | — | ❌ 前端不传 |
| — | — | `isRated` | — | ❌ 后端无此参数 |
| — | — | `isPublic` | — | ❌ 后端无此参数 |
| — | — | `startDateFrom` | — | ❌ 后端无此参数 |
| — | — | `startDateTo` | — | ❌ 后端无此参数 |
| — | — | `sortOrder` | — | ❌ 后端用 `direction` |

**问题**: Console `getContests` 发送的多个参数（`isRated`, `isPublic`, `startDateFrom`, `startDateTo`, `sortOrder`, `limit`）后端 `ContestQueryDTO` 不支持。且 Console 调用 `GET /contest` 而非 `GET /contest/list`。

### 6.2 排名查询参数

| 参数 | 后端 `GET /contest/{id}/ranking` | Console fetchContestRanking | 状态 |
|------|-------------------------------|---------------------------|------|
| `page` | `Integer` (default 1) | `number` (default 1) | ✅ |
| `limit` | `Integer` (default 50) | `number` (default 50) | ✅ |
| `include_virtual` | ❌ 不支持 | `boolean` (default true) | ❌ 前端传但后端忽略 |

---

## 7. 分页响应格式不对齐

| 位置 | 格式 |
|------|------|
| **后端** `PageResult<T>` | `{ items: T[], total: number, page: number, pageSize: number, totalPages: number }` |
| **Console** `PaginatedResult<T>` | `{ items: T[], total: number, page: number, limit: number, totalPages: number }` |
| **Management** `PageResult<T>` | `{ items: T[], total: number, page: number, pageSize: number, totalPages: number }` |

**问题**: Console 用 `limit` 而非 `pageSize`，与后端不一致。Management 与后端一致。

---

## 8. 完整 API 端点对照表

### 8.1 Console 前端 ↔ 后端 ContestController

| Console API 函数 | HTTP | 前端路径 | 后端端点 | 对齐状态 |
|-----------------|------|---------|---------|---------|
| `fetchUpcomingContests` | GET | `/contest/upcoming` | `GET /contest/upcoming` | ✅ |
| `fetchRunningContests` | GET | `/contest/running` | `GET /contest/running` | ✅ |
| `fetchPastContests` | GET | `/contest/past` | `GET /contest/past` | ✅ |
| `fetchContestDetail` | GET | `/contest/{id}` | `GET /contest/{id}` | ✅ |
| `fetchContestRanking` | GET | `/contest/{id}/ranking` | `GET /contest/{id}/ranking` | ⚠️ `include_virtual` 参数后端不支持 |
| `fetchLiveRanking` | GET | `/contest/{id}/live-ranking` | `GET /contest/{id}/live-ranking` | ✅ |
| `fetchGlobalRankings` | GET | `/contest/rankings/global` | `GET /contest/rankings/global` | ⚠️ `country` 参数后端不支持 |
| `registerForContest` | POST | `/contest/{id}/register` | `POST /contest/{id}/register` | ✅ |
| `unregisterFromContest` | DELETE | `/contest/{id}/register` | `DELETE /contest/{id}/register` | ✅ |
| `fetchParticipationStatus` | GET | `/contest/{id}/participation` | `GET /contest/{id}/participation` | ✅ |
| `startVirtualContest` | POST | `/contest/{id}/virtual/start` | `POST /contest/{id}/virtual/start` | ✅ |
| `fetchVirtualSession` | GET | `/contest/{id}/virtual/session` | `GET /contest/{id}/virtual/session` | ✅ |
| `finishVirtualContest` | POST | `/contest/{id}/virtual/finish` | `POST /contest/{id}/virtual/finish` | ✅ |
| `fetchUserContests` | GET | `/contest/user/my-contests` | `GET /contest/user/my-contests` | ✅ |
| `fetchUserContestHistory` | GET | `/contest/user/history` | `GET /contest/user/history` | ✅ |
| `fetchUserRatingHistory` | GET | `/contest/user/rating-history` | `GET /contest/user/rating-history` | ✅ |
| **`getContests`** | GET | **`/contest`** | ❌ **应为 `/contest/list`** | ❌ |
| **`getContestProblems`** | GET | **`/contest/{slug}/problems`** | ❌ **不存在** | ❌ |
| **`getAnnouncements`** | GET | **`/contest/{slug}/announcements`** | ❌ **不存在** | ❌ |
| **`checkIn`** | POST | **`/contest/{slug}/check-in`** | ❌ **不存在** | ❌ |
| **`submitContestProblem`** | POST | **`/contest/{id}/problems/{pid}/submissions`** | ❌ **不存在** | ❌ |
| **`fetchContestProblemSubmissions`** | GET | **`/contest/{id}/problems/{pid}/submissions`** | ❌ **不存在** | ❌ |

### 8.2 Management 前端 ↔ 后端 AdminContestController

| Management API 函数 | HTTP | 前端路径 | 后端端点 | 对齐状态 |
|--------------------|------|---------|---------|---------|
| `contestsApi.getContests` | GET | `/admin/contest` | `GET /admin/contest` | ✅ |
| `contestsApi.getContest` | GET | `/admin/contest/{id}` | `GET /admin/contest/{id}` | ✅ |
| `contestsApi.createContest` | POST | `/admin/contest` | `POST /admin/contest` | ✅ |
| `contestsApi.updateContest` | PATCH | `/admin/contest/{id}` | `PATCH /admin/contest/{id}` | ✅ |
| `contestsApi.deleteContest` | DELETE | `/admin/contest/{id}` | `DELETE /admin/contest/{id}` | ✅ |
| `contestsApi.addProblem` | POST | `/admin/contest/{id}/problems` | `POST /admin/contest/{id}/problems` | ✅ |
| `contestsApi.removeProblem` | DELETE | `/admin/contest/{id}/problems/{pid}` | `DELETE /admin/contest/{id}/problems/{pid}` | ✅ |
| `contestsApi.getRankings` | GET | `/admin/contest/{id}/rankings` | `GET /admin/contest/{id}/rankings` | ✅ |
| `contestsApi.startContest` | POST | `/admin/contest/{id}/start` | `POST /admin/contest/{id}/start` | ✅ |
| `contestsApi.endContest` | POST | `/admin/contest/{id}/end` | `POST /admin/contest/{id}/end` | ✅ |

**Management 前端与后端对齐良好。**

---

## 9. 对齐行动计划

### Phase 1: 修复幽灵端点（高优先级）

1. **后端添加 `GET /contest` 端点** — 作为 `GET /contest/list` 的别名，或修改 ContestController 的 `@GetMapping("/list")` 为 `@GetMapping`
2. **后端添加 `GET /contest/{id}/problems` 端点** — 返回竞赛题目列表
3. **后端添加 `GET /contest/{id}/announcements` 端点** — 返回竞赛公告列表
4. **后端添加竞赛提交端点** — `POST /contest/{id}/problems/{pid}/submissions` 和 `GET /contest/{id}/problems/{pid}/submissions`

### Phase 2: 对齐枚举值（高优先级）

1. **Console `ContestType` 对齐为 `ICPC | IOI | CUSTOM`** — 与后端和 Management 一致
2. **Console `ContestStatus` 对齐为后端值** — 移除 `PUBLISHED`, `REGISTERING`, `ONGOING`, `FREEZING`, `ARCHIVED`（或后端添加这些状态）
3. **Console `ParticipantStatus` 对齐** — 移除 `CHECKED_IN`, `PARTICIPATING`（或后端添加）

### Phase 3: 对齐 DTO 字段（中优先级）

1. **后端 ContestVO 添加缺失字段** — `penaltyPerWrong`, `scoringMode`, `tieBreaker`, `registeredCount`, `isRated`, `coverImage`, `rules`（这些在 Entity 中存在但 VO 未暴露）
2. **Console ParticipationStatus 对齐** — 与后端 `ParticipationStatusDTO` 字段对齐
3. **Console ContestRankingEntry 对齐** — 字段名对齐（`score` vs `totalScore`, `penalty` vs `totalPenalty`, `problemsSolved` vs `solvedCount`）
4. **后端 ContestQueryDTO 添加前端参数** — `isRated`, `isPublic`, `startDateFrom`, `startDateTo`, `limit`（作为 pageSize 别名）

### Phase 4: 引入轻量列表 DTO（低优先级）

1. **后端引入 `ContestListVO`** — 列表端点返回轻量 DTO
2. **后端引入 `ContestDetailVO`** — 详情端点返回完整 DTO（含 problems, announcements）
3. **前端类型同步更新**

### Phase 5: 清理 Controller 重叠（低优先级）

1. **移除 ContestController 中的 CRUD 端点** — `POST /contest`, `PUT /contest/{id}`, `DELETE /contest/{id}`
2. **统一由 AdminContestController 管理 CRUD**

---

## 10. 风险评估

| 问题 | 影响范围 | 修复风险 | 优先级 |
|------|---------|---------|--------|
| 幽灵端点 (GET /contest) | Console 列表页无法加载 | 低 — 添加端点别名 | P0 |
| 幽灵端点 (problems/announcements) | Console 详情页功能缺失 | 中 — 需新增 Service 方法 | P0 |
| ContestType 枚举不对齐 | Console 竞赛类型显示异常 | 低 — 前端修改 | P1 |
| ParticipationStatus 不对齐 | Console 参与状态显示异常 | 中 — 前后端同时修改 | P1 |
| ContestVO 字段缺失 | Console 列表数据不完整 | 低 — 后端添加字段 | P1 |
| ContestVO 过重 | 性能（列表返回过多数据） | 低 — 引入 ListVO | P2 |
| Controller CRUD 重叠 | 代码维护成本 | 低 — 移除重复端点 | P2 |
| 分页格式不对齐 | Console 分页逻辑异常 | 低 — 前端修改 | P2 |
