# Contest 模块前后端接口颗粒度对齐分析报告

> 分析页面: `http://localhost:9002/contest`
> 分析范围: Console 前端 (Vue 3) + Backend Spring Boot + Management 前端 (Vue 3)
> 生成日期: 2026-05-29

---

## 一、执行摘要

Contest 模块是当前 UltiCode 中**接口定义最复杂、类型映射最脆弱、前后端颗粒度差异最大**的模块之一。本次分析共发现 **8 大类问题**，涉及 **23 个具体缺陷**，涵盖类型系统不对齐、API 颗粒度混乱、数据映射层脆弱、业务逻辑错误、前端架构冗余等多个层面。

**最严重的问题（需立即修复）:**
1. `ParticipationStatusDTO.contestId` 为 `Long` 类型，与 Contest 实体的 `String` (UUID) 主键不一致，存在运行时类型转换风险
2. `ContestServiceImpl.getStats()` 实现逻辑错误，用状态计数冒充参与人数
3. `getGlobalRankingsPaginated` 在内存中手动分页，大数据量时性能极差
4. 前端 mapper 层大量使用 `as` 类型断言，缺乏运行时验证，数据格式变化时静默失败

---

## 二、架构概览

### 2.1 路由结构

```
/contest                    -> ContestListView.vue   (Tabbed: ongoing/upcoming/finished)
/contest/past               -> ContestListView.vue   (同 /contest，无差异化处理)
/contest/my                 -> ContestView.vue       (tab="my")
/contest/global-ranking     -> ContestView.vue       (tab="ranking")
/contest/local-ranking      -> ContestView.vue       (tab="ranking")
/contest/:slug              -> ContestDetailView.vue (详情页)
```

### 2.2 API 端点矩阵

| 端点 | 返回类型 | 鉴权 | 调用方 |
|------|---------|------|--------|
| `GET /contest` | `PageResult<ContestListVO>` | 公开 | Console |
| `GET /contest/upcoming` | `List<ContestVO>` | 公开 | Console |
| `GET /contest/running` | `List<ContestVO>` | 公开 | Console |
| `GET /contest/past` | `PageResult<ContestVO>` | 公开 | Console |
| `GET /contest/:id` | `ContestVO` | 公开 | Console |
| `GET /contest/:id/problems` | `List<ContestProblemVO>` | 公开 | Console |
| `GET /contest/:id/ranking` | `PageResult<ContestRankingVO>` | 公开 | Console |
| `GET /contest/rankings/global` | `PageResult<ContestRankingVO>` | 公开 | Console |
| `POST /contest/:id/register` | `Void` | 需登录 | Console |
| `GET /admin/contest` | `PageResult<ContestVO>` | ADMIN | Management |
| `GET /admin/contest/:id` | `ContestVO` | ADMIN | Management |
| `POST /admin/contest` | `ContestVO` | ADMIN | Management |
| `PATCH /admin/contest/:id` | `ContestVO` | ADMIN | Management |

### 2.3 类型映射链路

```
Backend Entity (Contest)
    -> Service.toVO() / toListVO()
    -> ContestVO / ContestListVO
    -> Jackson 序列化 (camelCase)
    -> HTTP Response
    -> 前端 apiGet()
    -> mapContestListItem() / mapContestDetail()   <-- 脆弱的手动映射层
    -> ContestListItem / ContestDetail (前端类型)
    -> Vue Components
```

---

## 三、详细问题分析

### 3.1 类型系统不对齐 (Type Mismatch)

#### P1-1: 主键类型不一致 [CRITICAL]

- **文件**: `ParticipationStatusDTO.java:18`, `ContestRankingVO.java:19`
- **问题**: `contestId` 字段类型为 `Long`，但 `Contest` 实体主键为 `String` (UUID)
- **影响**: 当前端用 String UUID 查询时，后端可能尝试 Long 解析导致 400 错误或数据丢失
- **修复**: 统一改为 `String` 类型

#### P1-2: 用户分数字段命名与类型混乱 [HIGH]

- **文件**: `ContestVO.java:125`, `console/src/types/contest.ts:114,143`
- **问题**: 后端 `ContestVO.userScore` 为 `Long`，前端 `ContestListItem` 中该字段缺失，仅在 `ContestDetail` 中有 `userScore: number`。同时 `ContestListItem` 有 `userRanking: number` 但后端 VO 中对应 `userRanking: Integer`
- **影响**: 列表页和详情页用户相关字段不一致，导致数据展示混乱
- **修复**: 统一 `userScore` / `userRanking` 的定义，明确列表项和详情项的字段边界

#### P1-3: 双前端类型定义分裂 [HIGH]

- **文件**: `console/src/types/contest.ts`, `management/src/api/admin/contests.ts`
- **问题**: Console 和 Management 分别维护两套 Contest 类型，字段命名不一致
  - Console: `participantCount`, `problemCount`, `isPublished`
  - Management: `currentParticipants`, `problemCount`, `isPublished`（相同字段名但语义有差异）
- **影响**: 同一概念在不同端有不同命名，增加维护成本，容易引入 bug
- **修复**: 建立共享类型包或统一命名约定

#### P1-4: 枚举类型不一致 [MEDIUM]

- **文件**: `console/src/types/contest.ts:5-16`, `backend/entity/enums/*.java`
- **问题**: 前端使用 TypeScript `enum`，后端存储为 raw `String`。`ContestType` 在前端是 enum，在 `ContestQueryDTO` 中是 `String`
- **影响**: 类型安全边界模糊，拼写错误在运行时才能发现
- **修复**: 后端 DTO 使用枚举类型（已在 `entity/enums/` 中定义但未在 DTO 中使用）

---

### 3.2 API 颗粒度混乱 (API Granularity)

#### P2-1: 列表接口返回类型不统一 [HIGH]

- **端点**: `GET /contest`, `GET /contest/upcoming`, `GET /contest/running`, `GET /contest/past`
- **问题**:
  - `/contest` 返回 `PageResult<ContestListVO>`（轻量，20 字段）
  - `/upcoming` 和 `/running` 返回 `List<ContestVO>`（完整，35+ 字段）
  - `/past` 返回 `PageResult<ContestVO>`（完整，带分页）
- **影响**: 同一"列表"场景使用了三种不同的返回结构和 VO 类型，前端需要写三套解析逻辑
- **修复**: 统一列表返回 `PageResult<ContestListVO>`，详情返回 `ContestVO`

#### P2-2: 存在冗余查询方法 [MEDIUM]

- **文件**: `ContestServiceImpl.java:128-152`, `ContestServiceImpl.java:414-438`
- **问题**: `findAll()` 和 `findAllListVO()` 几乎完全相同（只差返回类型 `ContestVO` vs `ContestListVO`）
- **影响**: 代码重复，维护时容易只改一处漏改另一处
- **修复**: 删除 `findAll()`，统一使用 `findAllListVO()`

#### P2-3: 排行榜 VO 过度复用 [HIGH]

- **文件**: `ContestRankingVO.java`
- **问题**: 同一个 `ContestRankingVO` 被用于：
  1. 竞赛排行榜 (`GET /contest/:id/ranking`)
  2. 实时排行榜 (`GET /contest/:id/live-ranking`)
  3. 用户竞赛历史 (`GET /contest/user/history`)
  4. 用户 rating 历史 (`GET /contest/user/rating-history`)
- **影响**: 不同场景需要不同字段，但共用 VO 导致字段语义混乱。例如 `ContestRankingVO.percentile` 在历史记录场景中无意义；`ContestRankingVO.country` 在虚拟比赛场景中可能为 null
- **修复**: 拆分专用 DTO
  - `ContestRankingVO` -> 竞赛排行
  - `UserContestHistoryVO` -> 用户参赛历史
  - `RatingHistoryVO` -> Rating 变更历史

#### P2-4: Admin 列表返回完整 VO [MEDIUM]

- **端点**: `GET /admin/contest`
- **问题**: 管理端列表返回 `PageResult<ContestVO>`（完整 35+ 字段），但列表页实际只展示 8-10 个字段
- **影响**: 网络传输冗余数据，影响加载速度
- **修复**: 管理端列表也应返回 `PageResult<ContestListVO>`，详情再查完整 `ContestVO`

---

### 3.3 数据映射层脆弱 (Fragile Mapping Layer)

#### P3-1: 大量手动 Mapper 函数 [HIGH]

- **文件**: `console/src/api/contest.ts:36-129`
- **问题**: 4 个手动 mapper 函数（`mapContestListItem`, `mapContestDetail`, `mapContestProblem`, `mapGlobalRankingEntry`），共 90+ 行，使用 `as` 类型断言
- **示例**:
  ```typescript
  id: raw.id as string,
  status: raw.status as ContestListItem["status"],
  ```
- **影响**: 后端字段名或类型变化时，前端不会编译报错，导致运行时静默失败
- **修复**: 使用 Zod schema 验证 + 类型推断，或 openapi-generator 自动生成客户端

#### P3-2: 数字类型转换逻辑分散 [MEDIUM]

- **文件**: `console/src/api/contest.ts:27-34`
- **问题**: `toNumber()` 函数处理后端可能返回的 String 数字，分散在各 mapper 中
- **影响**: 后端如果改了字段类型，需要在前端多个 mapper 中同步修改
- **修复**: 后端统一返回数字类型，前端删除 `toNumber()` 转换逻辑

#### P3-3: 前端别名函数增加认知负担 [LOW]

- **文件**: `console/src/api/contest.ts:246-248, 343-358`
- **问题**: `getContest(slug)` 只是 `fetchContestDetail(slug)` 的别名；`register(slug)` 只是 `registerForContest(slug)` 的别名
- **影响**: 同一操作有多个入口，增加代码阅读和维护成本
- **修复**: 删除别名函数，统一命名

---

### 3.4 查询参数映射问题 (Query Parameter Mapping)

#### P4-1: DTO 中存在别名字段 [MEDIUM]

- **文件**: `ContestQueryDTO.java`
- **问题**:
  - `sort` 和 `sortBy` 同时存在（语义相同）
  - `pageSize` 和 `limit` 同时存在（语义相同）
  - `isPublic` 和 `isVisible` 同时存在（语义相同）
- **影响**: 前端传不同参数名后端都能接受，但增加了文档复杂度和维护成本
- **修复**: 每个概念只保留一个字段名，废弃别名

#### P4-2: `findAll` 未处理 `contestType` 和 `isRated` 过滤 [MEDIUM]

- **文件**: `ContestController.java:58-87`, `ContestServiceImpl.java:128-152`
- **问题**: `ContestQueryDTO` 支持 `contestType` 和 `isRated` 过滤，但 `ContestController.getContestList` 没有将这些参数传递给 DTO
- **影响**: 前端传了这些过滤条件但后端不生效
- **修复**: 在 Controller 中补全参数映射

---

### 3.5 业务逻辑错误 (Business Logic Bugs)

#### P5-1: `getStats()` 实现逻辑完全错误 [CRITICAL]

- **文件**: `ContestServiceImpl.java:226-236`
- **问题**:
  ```java
  long upcoming = contestMapper.countByStatus(ContestStatus.UPCOMING.name());
  long running = contestMapper.countByStatus(ContestStatus.RUNNING.name());
  long finished = contestMapper.countByStatus(ContestStatus.FINISHED.name());
  stats.setRegisteredParticipants((int) upcoming);
  stats.setActiveParticipants((int) running);
  stats.setCompletedParticipants((int) finished);
  stats.setTotalSubmissions(upcoming + running + finished);
  ```
- **影响**: `RegisteredParticipants` 被赋值为"即将开始的比赛数量"，`TotalSubmissions` 被赋值为"比赛总数"，数据完全失真
- **修复**: 使用 `ContestParticipantMapper` 查询真实的参与人数和 submission 统计

#### P5-2: 全局排行榜内存分页 [HIGH]

- **文件**: `ContestServiceImpl.java:247-261`
- **问题**: `getGlobalRankingsPaginated` 先查询全部数据到内存，再 `skip().limit()` 手动分页
- **影响**: 用户量大时 OOM 风险，且每次分页都全量查询数据库
- **修复**: 使用数据库分页查询（MyBatis-Plus `Page`）

#### P5-3: Rating 历史未实现 [MEDIUM]

- **文件**: `RankingServiceImpl.java:91-95`
- **问题**: `getUserRatingHistory()` 直接返回 `List.of()`
- **影响**: 前端调用该接口永远拿不到数据
- **修复**: 实现 rating 历史记录查询（或前端移除该功能入口）

---

### 3.6 前端架构问题 (Frontend Architecture)

#### P6-1: 两个列表视图组件职责不清 [HIGH]

- **文件**: `ContestView.vue`, `ContestListView.vue`
- **问题**:
  - `ContestListView.vue`: Tabbed 导航（ongoing/upcoming/finished），有分页
  - `ContestView.vue`: 也根据 `tab` prop 渲染不同内容（my/ranking），同时复用于 `/contest/past` 等路由
  - 两者共用同一个 `contestStore`，但展示逻辑不同
- **影响**: 路由 `/contest/past` 映射到 `ContestListView.vue`，但该组件没有针对 `past` 的特殊处理；`ContestView.vue` 的 `tab` prop 设计不够直观
- **修复**: 明确拆分或合并为一个统一列表视图

#### P6-2: Store 中存在可变状态修改 [MEDIUM]

- **文件**: `console/src/stores/contest.ts:155-173`
- **问题**: `registerForContest` action 直接修改数组元素：
  ```typescript
  contest.registeredCount = (contest.registeredCount || 0) + 1;
  ```
- **影响**: 违反 Pinia + Vue 的响应式不可变性最佳实践，可能导致响应式追踪失效
- **修复**: 使用不可变更新模式（`map()` 返回新数组）

#### P6-3: `loadUserContests` 过度请求 [MEDIUM]

- **文件**: `console/src/stores/contest.ts:270-286`
- **问题**: 一次调用同时请求 registered/participated/virtual 三种类型的比赛
- **影响**: 用户只看一个 tab 时，另外两种数据也全部加载，浪费带宽和服务器资源
- **修复**: 按需加载，进入对应 tab 时才请求

#### P6-4: 路由未区分 global/local ranking [LOW]

- **文件**: `console/src/router/index.ts:79-89`
- **问题**: `/contest/global-ranking` 和 `/contest/local-ranking` 都映射到同一个组件和同一个 `tab="ranking"`，没有差异化处理
- **修复**: 传入不同 prop 区分，或合并为一个 ranking 页面

---

### 3.7 管理端与用户端不一致 (Management vs Console)

#### P7-1: 管理端 API 缺少 `findAllListVO` 对应端点 [MEDIUM]

- **问题**: Admin 列表接口 `GET /admin/contest` 调用 `findAllAdmin()`，返回完整 `ContestVO`。管理端 DataTable 实际上也只需要列表字段
- **修复**: Admin 列表也应返回轻量 VO

#### P7-2: 创建/更新 DTO 字段不对齐 [MEDIUM]

- **文件**: `CreateContestDTO.java`, `UpdateContestDTO.java`, `management/src/api/admin/contests.ts:76-104`
- **问题**: 前端 `CreateContestDto` 有 `slug` 字段，但后端 `CreateContestDTO` 中没有（后端自动生成 slug）
- **影响**: 前端可能误传 slug 但后端忽略，造成困惑
- **修复**: 对齐 DTO 字段，前端去掉后端自动生成的字段

---

### 3.8 国际化与文案问题 (i18n)

#### P8-1: 部分 i18n key 不存在于组件中 [LOW]

- **文件**: `console/src/i18n/locales/en-US/contest.ts`
- **问题**: `contest.types.weekly` 等 key 存在，但 `RunningContests.vue:144` 使用 `getContestTypeLabel(contest.contestType || "weekly")`，fallback 到 "weekly" 时文案存在，但如果后端返回 "ICPC" 则前端没有 `contest.types.ICPC` 的翻译
- **修复**: 确保所有枚举值都有对应翻译，或使用通用翻译策略

---

## 四、修复与迭代计划

### Phase 1: 关键类型修复 (1-2 天)

| 任务 | 优先级 | 涉及文件 |
|------|--------|---------|
| 统一 `contestId` 主键类型为 `String` (UUID) | P0 | `ParticipationStatusDTO`, `ContestRankingVO`, 相关 mapper |
| 修复 `getStats()` 逻辑错误 | P0 | `ContestServiceImpl.java` |
| 删除 `findAll()` 冗余方法 | P1 | `ContestService.java`, `ContestServiceImpl.java` |
| 补全 `getContestList` 缺失的参数映射 (`contestType`, `isRated`) | P1 | `ContestController.java` |

### Phase 2: API 颗粒度统一 (2-3 天) — **in-progress**

> **Implementation Plan**: `.claude/PRPs/plans/contest-api-phase2-granularity.plan.md`

| 任务 | 优先级 | 涉及文件 |
|------|--------|---------|
| 统一列表接口返回 `PageResult<ContestListVO>` | P1 | `ContestController.java`, `ContestServiceImpl.java` |
| 拆分 `ContestRankingVO` 为场景专用 DTO | P1 | `ContestRankingVO`, `RankingServiceImpl.java`, `ContestController.java` |
| 管理端列表返回轻量 VO | P2 | `AdminContestController.java` |
| 清理 `ContestQueryDTO` 别名字段 | P2 | `ContestQueryDTO.java`, 前端 filters |

### Phase 3: 数据映射层加固 (2-3 天) — **complete**

> **Implementation Report**: `.claude/PRPs/reports/contest-api-phase3-mapping-validation-report.md`

| 任务 | 优先级 | 涉及文件 |
|------|--------|---------|
| 引入 Zod schema 验证后端响应 | P1 | `console/src/api/contest.ts`, 新增 `contest.schema.ts` |
| 删除 `toNumber()` 转换，要求后端统一返回数字 | P2 | `console/src/api/contest.ts`, 后端相关 VO |
| 删除别名函数 (`getContest`, `register`, `withdraw` 等) | P3 | `console/src/api/contest.ts` |

### Phase 4: 性能与逻辑修复 (1-2 天)

| 任务 | 优先级 | 涉及文件 |
|------|--------|---------|
| 修复全局排行榜数据库分页 | P1 | `ContestServiceImpl.java`, `GlobalRankingMapper.java` |
| 实现或移除 `getUserRatingHistory` | P2 | `RankingServiceImpl.java`, 前端 store |
| Store 不可变更新重构 | P2 | `console/src/stores/contest.ts` |

### Phase 5: 前端架构优化 (3-5 天) — **complete**

> **Implementation Plan**: `.claude/PRPs/plans/contest-frontend-architecture-optimization.plan.md`
> **Implementation Report**: `.claude/PRPs/reports/contest-frontend-architecture-optimization-report.md`

| 任务 | 优先级 | 涉及文件 |
|------|--------|---------|
| 统一列表视图（合并 `ContestView.vue` 和 `ContestListView.vue`） | P2 | `ContestView.vue`, `ContestListView.vue`, `router/index.ts` |
| `loadUserContests` 按需加载 | P2 | `console/src/stores/contest.ts` |
| 区分 global/local ranking 路由 | P3 | `router/index.ts`, `ContestView.vue` |

### Phase 6: 管理端对齐 (2-3 天)

| 任务 | 优先级 | 涉及文件 |
|------|--------|---------|
| 管理端创建 DTO 对齐后端 | P2 | `management/src/api/admin/contests.ts` |
| 统一枚举类型使用 | P3 | `management/src/api/admin/contests.ts`, 后端 DTO |

---

## 五、建议的技术债务跟踪

建议在项目根目录 `docs/` 下维护以下文档：

1. `contest-api-contract.md` — 维护 Contest 模块的前后端契约文档
2. `contest-type-mapping.md` — 维护 Entity -> VO -> 前端类型的完整映射表
3. `contest-enum-reference.md` — 维护所有枚举值的前后端对照表

---

## 六、附录：类型映射对照表

| 概念 | 后端 Entity | 后端 VO (列表) | 后端 VO (详情) | Console 前端 | Management 前端 |
|------|------------|---------------|---------------|-------------|----------------|
| 主键 | `String id` | `String id` | `String id` | `string id` | `string id` |
| 参与人数 | `Integer participantCount` | `Integer participantCount` | `Integer participantCount` | `number participantCount` | `number currentParticipants` |
| 是否可见 | `Boolean isVisible` | `Boolean isVisible` | `Boolean isVisible` | `boolean isVisible` | `boolean isVisible` |
| 用户分数 | - | - | `Long userScore` | `number userScore` | `number userScore` |
| 比赛类型 | `String contestType` | `String contestType` | `String contestType` | `ContestType enum` | `ContestFormat union` |
| 评分模式 | `String scoringMode` | `String scoringMode` | `String scoringMode` | `ContestScoringMode union` | (未定义) |

*注：Management 前端部分字段命名与 Console 不一致，需统一。*

---

*报告结束*
