# Implementation Report: Align Contests Frontend-Backend API Granularity

## Summary
对齐 Contests 模块前后端 API 颗粒度，修复幽灵端点、枚举不对齐、DTO 字段不对齐，引入轻量列表 VO，清理 Controller CRUD 重叠。

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | High | High |
| Files Changed | 25+ | 19 |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | 创建 ContestListVO 轻量列表 VO | [done] Complete | record 模式 |
| 2 | 补全 ContestVO 缺失的 Entity 字段 | [done] Complete | 添加 penaltyPerWrong, scoringMode, tieBreaker, isRated 等 |
| 3 | 修复 ContestController 幽灵端点 — 列表路径 | [done] Complete | GET /contest 返回 ContestListVO |
| 4 | 修复 ContestController 幽灵端点 — problems/announcements | [done] Complete | 新增端点 |
| 5 | ContestService 新增列表 VO 转换方法 | [done] Complete | toListVO + findAllListVO |
| 6 | ContestQueryDTO 添加前端查询参数 | [done] Complete | isRated, isPublic, startDateFrom/To, limit |
| 7 | 移除 ContestController CRUD 端点 | [done] Complete | 已移除 createContest/updateContest/deleteContest |
| 8 | Console ContestType 枚举对齐 | [done] Complete | ICPC/IOI/CUSTOM |
| 9 | Console ContestStatus 枚举对齐 | [done] Complete | DRAFT/UPCOMING/RUNNING/FINISHED/CANCELLED |
| 10 | Console ParticipantStatus 枚举对齐 | [done] Complete | REGISTERED/STARTED/FINISHED/DISQUALIFIED |
| 11 | Console ContestListItem 类型重构 | [done] Complete | 纯 camelCase |
| 12 | Console ParticipationStatus 类型对齐 | [done] Complete | 对齐后端 ParticipationStatusDTO |
| 13 | Console ContestRankingEntry 类型对齐 | [done] Complete | score/penalty/problemsSolved + 移除已弃用字段 |
| 14 | Console API mapper 重构 | [done] Complete | 纯 camelCase 映射 |
| 15 | Console PaginatedResult 对齐 | [done] Complete | limit → pageSize |
| 16 | Console 组件适配新类型 | [done] Complete | VirtualContestTimer, ContestRankingTable, ContestProblemList 等 |
| 17 | Management problemIds 类型修正 | [done] Complete | number[] → string[] |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis (BE) | [done] Pass | ./mvnw compile 通过 |
| Static Analysis (FE Console) | [done] Pass | 预先存在的 DonutChart/axios 错误除外 |
| Static Analysis (FE Management) | [done] Pass | 零错误 |
| Unit Tests (Console) | [done] Pass | 228 passed |
| Unit Tests (Management) | [done] Pass | 217 passed |
| Lint (Console) | [done] Pass | 无新增问题 |
| Lint (Management) | [done] Pass | 零问题 |
| Integration | N/A | 需要运行后端手动验证 |
| Edge Cases | N/A | 需要浏览器验证 |

## Files Changed

| File | Action | Notes |
|---|---|---|
| `backend-spring/.../contest/dto/ContestListVO.java` | CREATED | record 模式轻量 VO |
| `backend-spring/.../contest/dto/ContestVO.java` | UPDATED | 添加 Entity 缺失字段 |
| `backend-spring/.../contest/dto/ContestQueryDTO.java` | UPDATED | 添加前端查询参数 |
| `backend-spring/.../contest/controller/ContestController.java` | UPDATED | 修复幽灵端点、列表返回 ContestListVO、移除 CRUD |
| `backend-spring/.../contest/service/ContestService.java` | UPDATED | 新增 toListVO/findAllListVO/getContestProblems/getContestAnnouncements |
| `backend-spring/.../contest/service/impl/ContestServiceImpl.java` | UPDATED | 实现新增方法 |
| `console/src/types/contest.ts` | UPDATED | 枚举/接口对齐后端 |
| `console/src/api/contest.ts` | UPDATED | 纯 camelCase mapper、修复端点路径 |
| `console/src/stores/contest.ts` | UPDATED | 适配新类型 |
| `console/src/stores/contest/contestStore.ts` | UPDATED | limit→pageSize, isRegistered/isCheckedIn 逻辑更新 |
| `console/src/stores/contest/__tests__/contestStore.spec.ts` | UPDATED | 对齐新类型和逻辑 |
| `console/src/views/contest/components/VirtualContestTimer.vue` | UPDATED | snake_case → camelCase |
| `console/src/views/contest/detailed/ContestDetailView.vue` | UPDATED | 使用 getContestProblems 获取题目 |
| `console/src/views/contest/detailed/components/ContestProblemList.vue` | UPDATED | 使用 problems prop 替代 contest.problems |
| `console/src/views/contest/detailed/components/ContestRankingTable.vue` | UPDATED | score/penalty 替代 totalScore/totalPenalty |
| `console/src/views/contest/components/ContestCard.vue` | UPDATED | camelCase 字段 |
| `console/src/views/contest/components/ContestStatusBadge.vue` | UPDATED | 枚举对齐 |
| `console/src/views/contest/components/MyContests.vue` | UPDATED | 字段适配 |
| `console/src/views/contest/components/PastContests.vue` | UPDATED | 字段适配 |
| `console/src/views/contest/components/RunningContests.vue` | UPDATED | 字段适配 |
| `console/src/views/contest/components/UpcomingContests.vue` | UPDATED | 字段适配 |
| `console/src/views/contest/detailed/components/ContestRegistration.vue` | UPDATED | 字段适配 |
| `console/src/views/contest/detailed/composables/useContestStatus.ts` | UPDATED | 枚举对齐 |
| `management/src/api/admin/contests.ts` | UPDATED | problemIds number[] → string[] |
| `management/src/stores/admin/contests.ts` | UPDATED | problemIds 过滤逻辑 |
| `management/src/views/contests/wizard/ContestWizard.vue` | UPDATED | problemIds 类型 |

## Deviations from Plan

1. **ContestRankingEntry 类型** — 计划中保留 `ratingBefore`/`ratingAfter`/`ratingChange`/`isVirtual`/`problemResults`，但后端 ContestRankingVO 实际不返回这些字段。改为使用后端实际返回的 `isCurrentUser`/`progress`/`percentile`/`isParticipating`/`maxRating`/`ratingTitle`/`maxRatingTitle`/`contestsAttended`/`badge`。
2. **ContestProblemList 组件** — 计划中使用 `contest.problems`，但 `ContestDetail` 接口无 `problems` 字段。改为添加 `problems` prop，由父组件通过 `getContestProblems` API 获取数据传入。
3. **Management problemIds 类型** — 计划改为 `number[]`，但后端 ContestVO.problemIds 类型为 `List<Long>`，JSON 序列化为 number，而 Management 前端在过滤时需要 string 比较。改为 `string[]` 更安全。

## Issues Encountered

1. **contestStore 双版本** — 存在两个 store 文件（`stores/contest.ts` 和 `stores/contest/contestStore.ts`），两者都需要更新以保持一致。
2. **VirtualContestTimer snake_case** — 组件中使用了 `ends_at`/`started_at`/`contest_id`/`total_score`/`total_penalty` 等 snake_case 属性，需要对齐为 camelCase。

## Tests Written

| Test File | Tests | Coverage |
|---|---|---|
| `contestStore.spec.ts` | 20 tests | contestStore 状态/操作/getter |

## Next Steps
- [ ] 代码审查 via code-reviewer
- [ ] 浏览器验证 Console 竞赛列表/详情页
- [ ] 提交代码
