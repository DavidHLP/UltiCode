> ⚠️ **历史计划（证据链保留）**：v1.0 早期设想，含 HMAC / appSecret 等已被代码现实取代的方案（实际用 UUID，无 HMAC）。
> - 当前权威**执行计划** → **[EXECUTION_PLAN.md](../EXECUTION_PLAN.md)**（R1–R5 可执行计划 + §0 框架说明）
> - 最终**设计决策 / 定档** → **[REVIEW_V3.md](../REVIEW_V3.md)**（审查实际代码，模块 v4.2 完结）
> - **不要沿用本文 Phase 0-9 框架**（见 EXECUTION_PLAN §0）；本文仅作决策溯源保留。

# Contest 与虚拟竞赛完整执行流程及问题修复计划

> **版本**: v1.0
> **日期**: 2026-06-17
> **范围**: UltiCode Contest 模块、Virtual Contest 链路、评分、排行榜、实时同步、前端体验与数据库约束
> **依据**: `docs/CONTEST_MODULE_PRD.md`、`wa1wzn9n8.output` 虚拟比赛审计综合报告、代码抽查结果
> **状态**: 待产品 / 技术评审后进入实施

---

## 0. 结论

本次不应被视为“修几个 contest bug”，而应作为一次 **Contest 模块交易边界、状态机、计分合同、实时通道、数据隔离的补完工程**。

核心原则：

> **真实比赛与虚拟比赛必须从参赛身份、提交作用域、计分结果、排行榜、成就、Elo、实时消息上彻底隔离；前端只负责展示，服务端才是时间与状态的权威。**

当前最致命的问题是：

1. 虚拟赛开始要求比赛 `FINISHED`，但提交要求比赛 `RUNNING`，两者互斥，导致虚拟赛无法提交。
2. 真实参赛与虚拟参赛大量查询仍使用 `contest_id + user_id`，没有区分 `is_virtual` / `virtual_session_id`。
3. 评分字段已经暴露给管理端，但 `scoringMode`、`tieBreaker`、`penaltyPerWrong`、`isRated` 等字段没有完整生效。
4. 虚拟提交会污染真实榜、题目状态、成就系统等全局或正式比赛数据。
5. WebSocket 代码存在，但业务页面没有实际接入，用户得不到实时判题、排行榜、公告、比赛状态更新。
6. 前端倒计时、虚拟 session、状态枚举存在多处不一致与不可恢复问题。

---

## 1. 已确认的关键代码现状

以下为本计划基于当前代码抽查确认的关键问题点。

### 1.1 虚拟赛提交互斥

- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java:239-259`
  - `submitContestProblem` 只允许 `contest.status == RUNNING`。
  - 因此 `FINISHED` 比赛下的虚拟赛无法提交。

- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestSchedulerServiceImpl.java:161-185`
  - `startVirtualContest` 只允许 `contest.status == FINISHED`。
  - `virtualSessionId` 当前使用随机 UUID，导致并发下无法幂等。

### 1.2 虚拟赛结束逻辑不足

- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestSchedulerServiceImpl.java:217-243`
  - `finishVirtualContest` 只翻转 participant 状态。
  - 不推 WebSocket，不清缓存，不做虚拟榜收敛。

- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestScoringServiceImpl.java:218-230`
  - `autoFinishVirtualParticipants` 存在。
  - 但调度链路需要补齐，并且更新范围需改为按 participant 精确更新。

### 1.3 评分字段死代码 / 半死代码

- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestScoringServiceImpl.java:63-202`
  - `applyJudgeResult` 不完整读取 `scoringMode`。
  - 不完整读取 `tieBreaker`。
  - WA penalty 仍是硬编码 `20`。
  - 首杀奖励仍硬编码 `10`。
  - 每次评分后全清 `contestRanking` cache。

- `backend-spring/src/main/java/com/ulticode/modules/contest/scheduler/ContestScheduler.java:218-239`
  - `transitionToFinished` 无条件调用 `ratingService.calculateAndUpdate`。
  - 未检查 `contest.isRated`。

### 1.4 真实 / 虚拟 participant 查询混用

- `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestParticipantMapper.java:42-46`
  - `findByContestIdAndUserId` 不区分真实参赛与虚拟参赛。
  - 这是虚拟污染真实数据的根问题之一。

### 1.5 排行榜未隔离虚拟数据

- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RankingServiceImpl.java:37-79`
  - 真榜查询不排除 `is_virtual`。
  - live ranking 直接拿所有 participant。
  - `problemsSolved` 当前有被 attempts 语义污染的风险。

### 1.6 前端虚拟赛与实时体验不足

- `console/src/views/contest/detailed/ContestDetailView.vue:60-87`
  - 题目状态通过提交记录一次性加载。
  - 不区分虚拟 session。
  - WebSocket 后也不会自动刷新。

- `console/src/composables/contest/useContestSocket.ts`
  - composable 存在，但业务页面未实际接入。

- `console/src/views/contest/components/VirtualContestTimer.vue:51-63`
  - 倒计时依赖本地 `Date.now()` 和 `setInterval`。
  - 没有 `visibilitychange` 兜底。
  - 超时控制必须由服务端做最终裁决。

---

## 2. 默认产品决策

| 决策 | 选择 | 原因 |
|---|---|---|
| 虚拟赛定位 | 真赛副本，但数据完全隔离 | 符合 Codeforces / AtCoder 补打体验 |
| 评分字段策略 | 实现字段，而不是删除字段 | 管理端已暴露配置，继续死字段会伤害运营信任 |
| `penaltyPerWrong` 单位 | 秒 | DTO 与 PRD 示例更接近“秒”语义，例如 `600 => 600s` |
| 虚拟赛是否影响 Elo | 不影响 | 虚拟赛是练习 / 复盘，不应改变真实评级 |
| 虚拟赛是否影响成就 | 不影响全局成就 | 防止通过补打刷全局已解决题数 |
| 虚拟赛是否进真榜 | 不进真榜 | 真榜只代表正式比赛 |
| 是否有虚拟榜 | 有，单独端点 | 满足补打对比需求，但不污染正式榜 |
| CANCELLED 比赛是否可开虚拟 | 默认不可开 | 保持“虚拟赛 = 正式结束比赛副本”的语义 |
| `linked-list-special` seed 状态 | 改为 FINISHED | 作为虚拟赛 E2E 固定样例 |

---

## 3. 完整用户执行流程

## 3.1 管理员创建真实比赛流程

### 用户视角

管理员希望完成：

1. 创建比赛。
2. 设置标题、slug、时间、时长。
3. 添加题目。
4. 配置计分方式：
   - `SCORE` / `ICPC` / `IOI`
   - `penaltyPerWrong`
   - `tieBreaker`
   - `isRated`
   - `freezeTime`
5. 发布比赛。
6. 比赛开始后监控提交、排行榜、公告、clarification。
7. 比赛结束后收榜、发题解、开放复盘。

### 系统流程

```text
Admin 创建比赛
  -> 校验 slug 唯一
  -> 写 contests
  -> 写 contest_problems
  -> 写 scoring 配置
  -> 发布比赛

用户报名
  -> 写 contest_participants(is_virtual=false, status=REGISTERED)

到达 start_time
  -> Scheduler 把 contest.status 改 RUNNING
  -> batchStartParticipants: REGISTERED -> STARTED
  -> WS 推 contest status RUNNING
  -> 排行榜初始脏标记

比赛中提交
  -> 服务端校验 contest.status == RUNNING
  -> 校验 participant.is_virtual=false
  -> 校验当前时间 <= contest.end_time
  -> 创建 submission
  -> 创建 contest_submission，绑定 participant_id
  -> 判题完成
  -> applyJudgeResult
  -> 根据 scoringMode / penaltyPerWrong / tieBreaker 计分
  -> 推 WS submission result
  -> 推 WS ranking dirty/update

到达 end_time
  -> Scheduler 把 contest.status 改 FINISHED
  -> 锁定最终榜
  -> 如果 isRated=true，计算 Elo
  -> 如果 isRated=false，只写 final_rank，不改 Elo
  -> WS 推 contest status ENDED

赛后
  -> 展示最终榜
  -> 展示题解
  -> 展示公开 clarification
  -> 开放讨论 / AC 代码查看
```

---

## 3.2 普通用户参加真实比赛流程

### 用户视角

```text
进入比赛列表
  -> 看到 upcoming / running / finished 状态

进入比赛详情
  -> 看到报名按钮 / 倒计时 / 题目列表 / 榜单

点击报名
  -> 成功后显示“已报名”

比赛开始
  -> 页面实时变成 RUNNING
  -> 题目可提交
  -> 倒计时开始

提交代码
  -> 立刻看到“判题中”
  -> 判题完成后自动收到 AC / WA / TLE 结果
  -> 题目图标自动变 solved / attempted
  -> 排行榜 1-2 秒内刷新

比赛结束
  -> 提交按钮禁用
  -> 页面提示比赛结束
  -> 最终榜展示
  -> 如果 rated，用户 rating 变化
  -> 赛后可以看题解 / clarification / 讨论
```

### 当前缺口

- WS 没接入，用户不会实时看到结果。
- 题目状态只 onMounted 加载一次。
- 计分字段不生效。
- `isRated=false` 仍会算 Elo。
- 排行榜 cache 粗暴全清。
- 前后端状态大小写不统一。

---

## 3.3 用户开启虚拟赛流程

### 目标用户体验

```text
用户错过一场已结束比赛
  -> 进入比赛详情页
  -> 看到“开始虚拟赛”按钮

点击开始虚拟赛
  -> 页面出现 VIRTUAL 徽标
  -> 倒计时从比赛原始时长开始
  -> 题目状态全部以“当前虚拟 session”为准
  -> 历史真实 AC 不显示为已解决

虚拟赛中提交
  -> 服务端用 virtualSessionId 找到当前虚拟 participant
  -> 如果未超时，允许提交
  -> 判题完成后只更新虚拟 participant 的分数
  -> 不影响真榜
  -> 不影响 Elo
  -> 不影响全局成就
  -> 可以看虚拟榜

刷新页面 / 切 tab / 重新打开浏览器
  -> sessionStorage 恢复 sessionId
  -> 后端再次确认 session 是否还有效
  -> 如果已过期，自动标记 FINISHED 并提示用户

时间到
  -> 服务端拒绝后续虚拟提交
  -> Scheduler 自动 finish expired virtual participant
  -> 前端收到状态更新
  -> 展示虚拟赛结果
```

---

## 4. 总体修复原则

### 4.1 参赛身份必须成为所有链路的主键

现在很多逻辑还在使用：

```text
contest_id + user_id
```

这在虚拟赛出现后不够。应升级为：

```text
contest_id + user_id + is_virtual
```

并在提交、查询、计分链路中明确使用：

```text
participant_id
virtual_session_id
```

最终规则：

- 真实比赛：
  - `is_virtual=false`
  - `virtual_session_id=null`
  - 参与真榜、Elo、成就。

- 虚拟比赛：
  - `is_virtual=true`
  - `virtual_session_id` 稳定、可恢复。
  - 不参与真榜、Elo、全局成就。
  - 进入虚拟榜。

### 4.2 服务端是时间权威

前端倒计时只能展示，不能决定能否提交。

后端必须判断：

```text
真实赛：
  now <= contest.end_time

虚拟赛：
  now <= participant.started_at + contest.duration_minutes
```

即使浏览器后台冻结、用户改系统时间、开多个 tab，服务端也必须拒绝超时提交。

### 4.3 排行榜必须分两类

```text
正式排行榜：
  contest_participants.is_virtual = false
  final_rank is not null / 或 live real participants

虚拟排行榜：
  contest_participants.is_virtual = true
  单独 endpoint
```

不要让一个 ranking endpoint 靠前端过滤虚拟数据。

### 4.4 实时消息是增强，不是唯一真相

WS 要接，但前端每次以下事件都应该触发一次轻量 refetch：

- reconnect
- visibilitychange
- route enter
- submission result received

这样可以避免漏消息导致 UI 永久陈旧。

---

## 5. 分阶段修复计划

# Phase 0：建立修复边界和回归基线

## 目标

在改代码前锁定当前坏行为，避免修复过程中引入二次回归。

## 要做

1. 新建测试计划，不先动业务逻辑。
2. 补几类 failing tests：
   - 虚拟赛开始后提交当前失败，作为 bug 复现。
   - 真榜混入虚拟行。
   - `isRated=false` 仍触发 rating。
   - `penaltyPerWrong` 不生效。
   - `useContestSocket` 未被页面使用。
3. 确认当前迁移状态：
   - `init-db/migrations/` 只新增新 migration。
   - 新 migration 使用项目当前要求的 12 位无下划线格式，例如：
     - `V20260617120000__Contest_Virtual_Session_Isolation.sql`

## 验收

- 有测试能稳定复现 F-01 / F-03 / F-11 / F-12 / F-16。
- 不修改旧 migration。
- `git diff --check` 通过。

---

# Phase 1：数据库与身份模型修复

## 目标

先解决数据无法隔离的问题。否则后面 API、计分、排行榜都会继续混乱。

## 1.1 `contests.slug` 增加唯一约束

当前只有普通索引。计划新增：

```text
UNIQUE KEY uk_contests_slug(slug)
```

但不能直接加，必须先 dedupe。

### 处理策略

1. 新 migration 里先检查重复 slug。
2. 如果有重复：
   - 保留最早创建的一条。
   - 其余 slug 改为 `slug-duplicate-{id后缀}` 或软删除，具体看数据情况。
3. 再加唯一索引。

## 1.2 `contest_participants` 增加真实 / 虚拟唯一约束

目标约束：

```text
UNIQUE KEY uk_contest_user_virtual(contest_id, user_id, is_virtual)
```

前置要求：

- 把历史 `is_virtual IS NULL` 统一修成 `false`。
- 清理重复虚拟行。
- 如果同一用户同一比赛已有多个虚拟 participant：
  - 保留最早 `started_at` 的一条。
  - 其他标记为 `FINISHED` 或迁移到归档，避免硬删造成 submission 外键问题。

## 1.3 虚拟 sessionId 改为稳定值

当前：

```text
UUID.randomUUID()
```

建议改成：

```text
HMAC-SHA256(appSecret, contestId + ':' + userId)
```

注意：

- `virtualSessionId` 不作为鉴权边界。
- 真正鉴权仍然来自登录用户 + participant 归属校验。
- sessionId 只用于前后端恢复和幂等识别。

## 1.4 代码改动点

- `ContestSchedulerServiceImpl.startVirtualContest`
  - 从“查询 `contest_id + user_id`”改为“查询 `contest_id + user_id + is_virtual=true`”。
  - 并发下如果 insert 撞唯一键，则重新查询并返回已有 session。
  - `virtualSessionId` 改为稳定生成。

- `ContestParticipantMapper`
  - 新增：
    - `findRealByContestIdAndUserId`
    - `findVirtualByContestIdAndUserId`
    - `findByContestIdAndUserIdAndVirtualSessionId`
    - `existsRealParticipant`
    - `existsVirtualParticipant`
  - 逐步替代裸 `findByContestIdAndUserId`。

## 验收

- 20 并发调用 `/virtual/start` 只产生 1 条虚拟 participant。
- 同一用户可以同时有：
  - 1 条真实 participant。
  - 1 条虚拟 participant。
- 重复 slug 写入被 DB 拒绝。
- 老数据迁移不破坏已有真实比赛记录。

---

# Phase 2：虚拟赛提交链路修复

## 目标

让虚拟赛 happy path 真正可用：

```text
开虚拟赛 -> 进题 -> 提交 -> 判题 -> 题目状态更新 -> 虚拟榜更新 -> 自动结束
```

## 2.1 `submitContestProblem` 改成真实 / 虚拟双分支

当前逻辑：

```text
contest.status 必须 RUNNING
```

新逻辑：

```text
如果是真实赛：
  contest.status == RUNNING
  participant.is_virtual == false
  participant.status == STARTED
  now <= contest.end_time

如果是虚拟赛：
  contest.status == FINISHED
  participant.is_virtual == true
  participant.status == STARTED
  now <= participant.started_at + contest.duration_minutes
```

## 2.2 前端传递虚拟 session

所有 contest submission / submissions query 请求在虚拟赛时带：

```text
X-Virtual-Session-Id: {sessionId}
```

后端逻辑：

```text
如果 header 有 X-Virtual-Session-Id:
  查 virtual participant
否则:
  查 real participant
```

不能仅靠前端 route 或 store 判断。

## 2.3 `contest_submissions` 必须绑定正确 participant

提交创建时必须确保：

- 真实提交绑定真实 participant。
- 虚拟提交绑定虚拟 participant。

## 2.4 查询题目提交记录必须按 session 隔离

当前查询条件是：

```text
contest_id + contest_problem_id + user_id
```

新逻辑：

真实赛：

```text
contest_id + contest_problem_id + user_id + participant.is_virtual=false
```

虚拟赛：

```text
contest_id + contest_problem_id + user_id + virtual_session_id
```

## 2.5 服务端超时守卫

必须在两个地方守：

### 提交前守

```text
虚拟赛 now > startedAt + duration => 409 CONTEST_ENDED
```

### 判题回写前守

判断应基于提交创建时间，而不是判题完成时间：

```text
contest_submission.submitted_at <= virtual_end_time
```

否则慢判题会错误丢分。

## 验收

- 用户在 FINISHED 比赛开启虚拟赛后可以提交。
- 超时后提交返回 409。
- 超时前提交但判题慢，结果仍然计入。
- 历史真实 AC 不污染虚拟题目状态。
- 虚拟 AC 不污染真实题目状态。

---

# Phase 3：自动结束虚拟赛与状态机修复

## 目标

虚拟赛不依赖前端倒计时结束。

## 3.1 增加调度调用

在 `ContestScheduler` 中增加：

```text
@Scheduled(fixedRate = 60_000)
autoFinishVirtualParticipants()
```

## 3.2 修复 auto-finish 更新范围

当前风险：

```text
findVirtualParticipantsToFinish(now)
  -> batchUpdateStatus(contestId, STARTED, FINISHED, now)
```

更新条件只按 contestId 和 status，范围太大。

应新增精确更新：

```text
UPDATE contest_participants
SET status='FINISHED', finished_at=now
WHERE id = #{participantId}
  AND is_virtual = 1
  AND status = 'STARTED'
```

## 3.3 finish 后推送消息

虚拟赛结束后推：

```text
/user/queue/notification
/user/queue/contest-status
```

payload 示例：

```json
{
  "contestId": "...",
  "virtualSessionId": "...",
  "status": "ENDED",
  "message": "虚拟赛已结束"
}
```

## 验收

- 前端不打开页面，虚拟赛到期后也会被服务端自动 finish。
- 多次调度不会重复处理。
- 调度不会错误结束同场其他未过期虚拟用户。
- 用户回到页面能看到“虚拟赛已结束”。

---

# Phase 4：评分正确性修复

## 目标

让 admin 配置真的生效。

## 4.1 建立统一评分入口

建议抽出内部组件：

```text
ContestScoreCalculator
```

职责：

```text
输入：
  contest
  participant
  contestProblem
  contestSubmission
  judgeResult
  existingProblemResult

输出：
  participant aggregate delta
  problem result update
  first solve effect
  cache eviction scope
```

短期不必大重构，但至少要把 `scoringMode` switch 收敛到清晰方法中。

## 4.2 SCORE 模式

规则：

- AC 首次通过：
  - 加题目分。
  - 可选 first solve bonus。
- 重复 AC：
  - 不重复加分。
- WA：
  - 是否扣分由 scoring rule 决定。
  - 默认不扣总分，只记录 attempts。

## 4.3 ICPC 模式

规则：

```text
排名：
  solved desc
  totalPenalty asc
  tieBreaker
```

罚时：

```text
totalPenalty = sum(AC time from start) + wrongBeforeAC * penaltyPerWrong
```

`penaltyPerWrong` 统一为 **秒**：

```text
penaltyPerWrong=600  => 10 分钟
penaltyPerWrong=1200 => 20 分钟
```

## 4.4 IOI 模式

规则：

- `contest_problem_results.score` 支持部分分。
- 总榜按 `totalScore desc`。
- 同分按 `tieBreaker`。

如果 judge result 暂时没有部分分字段，短期降级：

```text
AC = full score
非 AC = 0
```

## 4.5 tieBreaker 实现

| tieBreaker | 行为 |
|---|---|
| `LAST_SOLVE_TIME` | 最后一次 AC 时间更早者优先 |
| `TOTAL_TIME` | 总用时更少者优先 |
| `TOTAL_ATTEMPTS` | 提交次数更少者优先 |
| `NONE` | 保持同分或按稳定字段排序 |

## 4.6 `isRated` 修复

当前 `transitionToFinished` 无条件算 rating。应改为：

```text
总是计算 final_rank
if contest.isRated == true:
  calculateAndUpdateRating
else:
  skip rating
```

推荐把现有 `RatingCalculationServiceImpl.calculateAndUpdate` 拆成：

```text
calculateFinalRanks(contestId)
updateRatings(contestId)
```

## 4.7 虚拟赛不触发成就 / Elo / 真首杀

虚拟提交：

- 不触发全局成就。
- 不触发真实 first solve。
- 不触发 Elo。
- 不进入真榜。

如果未来要展示“虚拟赛内首杀”，应该单独建虚拟维度，不复用真赛 `first_solve_records`。

## 验收

- `penaltyPerWrong=600`，一次 WA 后 AC，罚时增加 600 秒。
- `scoringMode=ICPC`，同 solved 数按 penalty 排。
- `tieBreaker=LAST_SOLVE_TIME` 生效。
- `isRated=false`：
  - final_rank 有。
  - Elo 不变。
- 虚拟 AC：
  - 不增加全局已解决题数。
  - 不产生真实 first solve。
  - 不改 Elo。

---

# Phase 5：排行榜纯净度与缓存修复

## 目标

真榜、虚拟榜、缓存互不污染。

## 5.1 真榜过滤虚拟用户

`RankingServiceImpl.getContestRanking` 应只查：

```text
cp.is_virtual = 0
```

final ranking：

```text
cp.final_rank IS NOT NULL
```

live ranking：

```text
cp.is_virtual = 0
AND cp.status IN ('STARTED', 'FINISHED')
```

## 5.2 新增虚拟榜端点

建议新增：

```http
GET /contest/{contestId}/virtual/ranking
```

权限：

- 登录用户可看。
- 如果产品希望公开，则只在 contest visible 时公开。
- 仍然不能泄露不该泄露的用户敏感字段。

查询：

```text
cp.is_virtual = 1
```

排序按同一 scoringMode，但不写 Elo。

## 5.3 修复 `problemsSolved`

当前 `RankingServiceImpl.toRankingVO` 把：

```text
problemsSolved = attemptCount
```

这是错误语义。

应改为：

```text
contest_problem_results where is_solved=true
```

短期可在 mapper 查询时 join 聚合 solved count。

## 5.4 缓存按 contestId 失效

当前是全清。应改为：

```text
contestRanking:{contestId}:real
contestRanking:{contestId}:virtual
globalRanking
```

提交影响：

| 事件 | 清理 |
|---|---|
| 真实赛提交 | 当前 contest real ranking |
| 虚拟赛提交 | 当前 contest virtual ranking |
| rated 比赛结束 | global ranking |
| 非 rated 比赛结束 | 不清 global ranking |

## 验收

- 真榜不出现虚拟用户。
- 虚拟榜不出现真实用户。
- `final_rank=null` 不会排到榜首。
- `problemsSolved` 不再等于 WA 次数。
- 1000 AC/min 时不发生全局缓存雪崩。

---

# Phase 6：WebSocket 实时链路接通

## 目标

用户不刷新页面也能看到：

- 判题结果。
- 题目状态。
- 排名变化。
- 公告。
- 比赛开始 / 结束。
- 虚拟赛结束。

## 6.1 后端 WS join 权限

`ContestWebSocketHandler.handleJoinContest` 必须校验：

```text
用户已登录
AND (
  是真实 participant
  OR 是当前虚拟 participant
  OR 比赛允许公开旁观
)
```

不要让未注册用户直接 join 私有比赛房间。

## 6.2 前端接入 `useContestSocket`

在 `ContestDetailView.vue` 接：

```text
onMounted:
  connect
  joinContest(contestId)
  onSubmissionResult -> 刷新当前题目状态 + toast
  onRankingUpdate -> 刷新 rankings
  onAnnouncement -> toast + 刷新公告
  onContestStatus -> 更新 contestStore.currentContest.status
```

离开页面：

```text
leaveContest
```

但 submission result 不应该只靠页面局部订阅。

## 6.3 全局 submission result 队列

在 `App.vue` 或全局 store 建：

```text
pendingSubmissionResults
```

行为：

```text
收到 submission result
  -> 写入 Pinia
  -> 如果当前页面相关，立即展示
  -> 如果不相关，通知中心显示
```

## 6.4 reconnect 后强制 refetch

```text
onReconnect:
  loadContestDetail
  loadProblemStatuses
  loadRankings
  loadVirtualSession
```

## 验收

- 用户提交后不刷新页面，题目状态自动变 AC/WA。
- 管理员发公告，比赛详情页立即 toast。
- 排名变化 1-2 秒内可见。
- 离开提交页面后，判题结果不会丢。
- 未注册用户不能 join 私有比赛 WS room。

---

# Phase 7：前端虚拟赛体验修复

## 目标

用户看到的是一个稳定、可信、可恢复的虚拟赛体验。

## 7.1 sessionStorage 持久化

存储 key：

```text
virtual-contest:{contestId}
```

内容：

```json
{
  "contestId": "...",
  "sessionId": "...",
  "startedAt": "...",
  "endsAt": "..."
}
```

页面加载时：

1. 先读 sessionStorage。
2. 再请求后端确认。
3. 后端为准。

## 7.2 多 tab 互锁

使用：

```text
BroadcastChannel('virtual-contest')
```

事件：

```text
virtual-started
virtual-finished
virtual-expired
```

fallback：

```text
localStorage event
```

规则：

- 同一个 contest 只显示一个 active virtual session。
- Tab A 结束，Tab B 同步结束。
- Tab B 不允许误开第二个虚拟 session。

## 7.3 倒计时组件统一

合并：

- `ContestTimer`
- `VirtualContestTimer`
- `useContestStatus` 里的 1s interval

成为统一组件：

```text
ContestCountdownTimer
```

props：

```ts
mode: 'real' | 'virtual'
startsAt
endsAt
serverNow?
status
```

必须监听：

```text
visibilitychange
focus
reconnect
```

每次回前台都重新计算。

## 7.4 状态枚举统一

后端内部可以继续用：

```text
UPCOMING / RUNNING / FINISHED / CANCELLED
```

API 层建议统一输出大写，前端类型也统一大写。

如果要保留小写兼容，需要一个 mapper：

```text
normalizeContestStatus(raw)
```

禁止在业务组件里到处写：

```text
status === 'started'
status === 'IN_PROGRESS'
status === 'running'
```

## 验收

- 刷新页面后虚拟赛还在。
- 关闭 tab 重新打开，后端确认 session 是否有效。
- 多 tab 不会开出两个虚拟 session。
- 后台 10 分钟再回来，倒计时显示正确。
- 超时后提交按钮禁用，并且服务端也拒绝提交。

---

# Phase 8：题解 / Clarification / 封榜

这一阶段不是虚拟赛 P0 的前置，但它是完整 Contest 体验闭环。

## 8.1 Editorial 官方题解

### 用户视角

```text
比赛结束后
  -> 进入题目
  -> 看到 Editorial tab
  -> 阅读官方题解
```

### 管理员视角

```text
管理端
  -> 选择比赛
  -> 选择题目
  -> 编辑 Markdown 题解
  -> 发布
```

### 安全要求

- Markdown / KaTeX 必须走现有 sanitization。
- 比赛结束前不可见。
- admin 写接口必须 `@PreAuthorize` + CSRF。

## 8.2 Clarification

### 用户视角

```text
比赛中对题目有疑问
  -> 点击提问
  -> 选择公开 / 私密
  -> admin 回复后收到通知
```

### 管理员视角

```text
管理端看到问题列表
  -> 回复
  -> 可设置公开给所有参赛者
```

### 权限

| 类型 | 可见范围 |
|---|---|
| 私密 | 提问者 + admin |
| 公开 | 所有参赛者 + admin |
| 赛后公开 | 可配置为所有登录用户可见 |

## 8.3 封榜

规则：

```text
freezeTime < now < endTime:
  榜单只展示 freezeTime 前的状态

now >= endTime:
  展示最终榜
```

实现方式：

- 排行榜查询根据 `contest.freezeTime` 过滤提交影响。
- 或在 freeze 时生成 snapshot。
- 推荐先做查询过滤，后续性能不足再做 snapshot。

---

## 6. Tiny Commits 实施顺序

每个 commit 都应保持可编译、测试可跑。

1. **补测试复现虚拟提交互斥**
   - 增加 `submitContestProblem` 虚拟赛失败测试。
   - 当前测试应红。

2. **新增 participant 查询方法**
   - 增加 real / virtual participant mapper 方法。
   - 暂不替换所有调用。

3. **新增 migration：slug unique + participant unique 前置清理**
   - 修历史 null。
   - 清重复虚拟行。
   - 加 `uk_contests_slug`。
   - 加 `uk_contest_user_virtual`。

4. **startVirtualContest 幂等化**
   - 稳定 sessionId。
   - 并发 insert duplicate 后查询已有记录返回。
   - 单测覆盖 20 并发。

5. **submitContestProblem 支持虚拟分支**
   - header `X-Virtual-Session-Id`。
   - 真实 / 虚拟分别校验。
   - 超时返回 `CONTEST_ENDED`。

6. **提交记录查询按 participant/session 隔离**
   - 修 `findSubmissionsByContestProblemAndUser`。
   - 前端 API 带 virtual session header。
   - 补“历史 AC 不污染虚拟状态”测试。

7. **autoFinishVirtualParticipants 精确更新**
   - 新增按 participant id finish。
   - Scheduler 增加 fixedRate 调用。
   - 推 WS status。

8. **评分读取 penaltyPerWrong**
   - 把硬编码 20 改成 contest 配置。
   - 明确单位为秒。
   - 补测试。

9. **实现 scoringMode 基础分支**
   - SCORE 保持兼容。
   - ICPC 支持 solved + penalty。
   - IOI 先支持 AC full score / non-AC 0。
   - 补 contract tests。

10. **实现 tieBreaker**
    - 排名服务或 rating 服务读取 tieBreaker。
    - 覆盖四种 tieBreaker。

11. **isRated=false 不更新 Elo**
    - 拆 final rank 与 rating update。
    - 非 rated 只写 final rank。
    - 补测试。

12. **虚拟提交不触发成就 / Elo / 真首杀**
    - scoring listener / submission achievement path 加 virtual guard。
    - 补虚拟 AC 不污染成就测试。

13. **真榜过滤虚拟行**
    - mapper SQL 加 `is_virtual=0`。
    - live ranking 修排序和 NULL 问题。
    - `problemsSolved` 改为 solved count。

14. **新增虚拟榜 endpoint**
    - `/contest/{id}/virtual/ranking`
    - 权限校验。
    - 前端展示虚拟榜入口。

15. **排行榜 cache 拆 key**
    - real / virtual / global 分 key。
    - 只 evict 受影响 cache。

16. **接入 useContestSocket**
    - `ContestDetailView` join/leave。
    - ranking / announcement / status / submission result callback。
    - reconnect 后 refetch。

17. **全局 submission result store**
    - 离开页面也能收结果。
    - 通知中心展示。

18. **sessionStorage + BroadcastChannel**
    - 虚拟 session 持久化。
    - 多 tab 同步 start/finish。

19. **统一倒计时组件**
    - `visibilitychange` / focus 修复。
    - 替换真实赛 / 虚拟赛重复计时逻辑。

20. **状态类型统一**
    - 前后端 ContestStatus / SubmissionStatus 统一。
    - 删除 fallback 硬撑逻辑。
    - 补 TS 类型测试。

21. **linked-list-special seed 状态修复**
    - 新 migration 改为 FINISHED。
    - 用于固定虚拟赛 E2E。

22. **E2E happy path**
    - linked-list-special：
      - start virtual
      - submit
      - judge result
      - problem status update
      - virtual ranking update
      - auto finish

---

## 7. 测试计划

### 7.1 后端单元测试

覆盖：

- `ContestSchedulerServiceImpl`
  - start virtual 幂等。
  - session 恢复。
  - finish session 校验。

- `ContestServiceImpl`
  - 真实赛 RUNNING 可提交。
  - 真实赛 FINISHED 不可提交。
  - 虚拟赛 FINISHED + active session 可提交。
  - 虚拟赛 expired 不可提交。
  - header session 不匹配拒绝。

- `ContestScoringServiceImpl`
  - SCORE / ICPC / IOI。
  - penaltyPerWrong 秒级生效。
  - 虚拟 AC 不成就。
  - 虚拟 AC 不首杀。
  - 重复判题事件幂等。

- `RankingServiceImpl`
  - 真榜排除虚拟。
  - 虚拟榜只含虚拟。
  - NULL final_rank 不上正式榜。
  - problemsSolved 不等于 attempts。

- `ContestScheduler`
  - isRated false 不调 Elo。
  - auto finish 精确更新单 participant。

### 7.2 Mapper / 数据库测试

- `uk_contests_slug` 生效。
- `uk_contest_user_virtual` 生效。
- 重复虚拟 session 并发只保留一条。
- `findVirtualParticipantsToFinish` 只找 expired。
- finish update 只更新目标 participant。

### 7.3 前端 Vitest

- `VirtualContestTimer`
  - visibilitychange 后 remaining 正确。
  - expired 后触发 finish。
  - finish 失败显示 toast。

- `contestStore`
  - sessionStorage rehydrate。
  - BroadcastChannel start/finish 同步。
  - start virtual 后保存 session。
  - finish virtual 后清 session。

- `ContestDetailView`
  - WS submission result 后刷新 problem status。
  - announcement 后 toast。
  - status update 后更新状态。

- `useContestSocket`
  - join timeout 清理。
  - reconnect 后回调不重复注册。

### 7.4 E2E

至少 5 条：

1. **真实赛完整流程**
   - admin 创建比赛。
   - 用户报名。
   - 比赛开始。
   - 用户提交 AC。
   - 榜单更新。
   - 比赛结束。
   - final_rank 生成。
   - rated 比赛 Elo 改变。

2. **非 rated 比赛**
   - `isRated=false`。
   - 用户提交 AC。
   - 比赛结束。
   - final_rank 有。
   - Elo 不变。

3. **虚拟赛 happy path**
   - 进入 FINISHED 比赛。
   - start virtual。
   - 提交 AC。
   - 题目状态变 solved。
   - 虚拟榜更新。
   - 真榜不变。
   - 时间到自动结束。

4. **虚拟隔离**
   - 用户真实赛曾 AC 题 A。
   - 开虚拟赛。
   - 题 A 初始状态仍是 todo。
   - 虚拟 AC 后只虚拟状态 solved。
   - 真榜不受影响。

5. **WS 实时**
   - 两个 tab 打开比赛。
   - Tab A 提交。
   - Tab B 榜单刷新。
   - admin 发公告。
   - 两个 tab 都收到。

---

## 8. 最终验收标准

### 8.1 真实赛

- [ ] 用户可报名、参赛、提交。
- [ ] 比赛时间由服务端控制。
- [ ] 判题结果实时更新。
- [ ] 排行榜实时更新。
- [ ] `scoringMode` 生效。
- [ ] `penaltyPerWrong` 生效。
- [ ] `tieBreaker` 生效。
- [ ] `isRated=false` 不改 Elo。
- [ ] 真榜没有虚拟用户。

### 8.2 虚拟赛

- [ ] FINISHED 比赛可开启虚拟赛。
- [ ] 虚拟赛可提交。
- [ ] 虚拟赛超时后服务端拒绝提交。
- [ ] 刷新页面 session 不丢。
- [ ] 多 tab 不开重复 session。
- [ ] 历史真实 AC 不污染虚拟题目状态。
- [ ] 虚拟 AC 不污染真实题目状态。
- [ ] 虚拟 AC 不影响成就。
- [ ] 虚拟 AC 不影响 Elo。
- [ ] 虚拟用户不进真榜。
- [ ] 虚拟榜单独展示。
- [ ] auto finish 不依赖前端。

### 8.3 数据库

- [ ] `contests.slug` 唯一。
- [ ] `(contest_id, user_id, is_virtual)` 唯一。
- [ ] migration 可以在 fresh DB 和已有 dev DB 上跑通。
- [ ] 不修改历史 migration。

### 8.4 实时

- [ ] WS join 有权限校验。
- [ ] submission result 不因页面卸载丢失。
- [ ] reconnect 后状态可恢复。
- [ ] 公告、榜单、比赛状态能实时更新。

---

## 9. 风险与注意事项

### 9.1 最大技术风险：participant 查询替换不完整

所有使用以下方法的地方都要审计：

```text
findByContestIdAndUserId(
```

每处都必须说明为什么不需要 `is_virtual`。

如果漏掉，会继续出现：

- 虚拟拿到真实 participant。
- 真实拿到虚拟 participant。
- 排行榜污染。
- 提交污染。

### 9.2 migration 风险

新增唯一索引前必须 dedupe。

尤其是：

```text
contest_participants
```

因为历史数据可能已经有重复虚拟行。

### 9.3 `penaltyPerWrong` 单位必须一次性统一

建议明确：

```text
penaltyPerWrong 单位 = 秒
```

并同步更新文档，避免后续 admin 配 `600` 却被当成 600 分钟或 600 分。

### 9.4 首杀与虚拟赛

虚拟 AC 绝不能抢真实 first solve。

如果未来要展示虚拟 first solve，应新建虚拟维度，不要复用 `first_solve_records(contest_id, problem_id)`。

### 9.5 WS 不能代替 refetch

WS 是实时增强，不能作为唯一状态来源。

前端必须在以下事件时 refetch 关键状态：

- route enter
- visibilitychange
- reconnect
- submission result received

---

## 10. 建议排期

### 第 1 周：虚拟赛能提交

- schema 约束。
- start virtual 幂等。
- submit virtual 分支。
- session 隔离。
- 基础单测。

交付物：

```text
用户可以 start virtual -> submit -> judge -> 看到虚拟状态
```

### 第 2 周：虚拟赛完整可靠

- auto finish。
- sessionStorage。
- BroadcastChannel。
- 题目状态隔离。
- 虚拟榜。
- 真榜过滤。

交付物：

```text
虚拟赛刷新、多 tab、超时、榜单全部可用
```

### 第 3 周：评分正确性

- SCORE / ICPC / IOI。
- penaltyPerWrong。
- tieBreaker。
- isRated。
- 成就隔离。
- first solve 隔离。

交付物：

```text
admin 配置的评分规则真实生效
```

### 第 4 周：实时同步

- 接入 `useContestSocket`。
- WS join 鉴权。
- 全局 submission result store。
- ranking / announcement / status 实时更新。
- reconnect refetch。

交付物：

```text
用户不用刷新也能看到判题、榜单、公告、状态变化
```

### 第 5 周：E2E + 性能 + 收口

- linked-list-special E2E。
- 1000 AC/min cache 压测。
- migration fresh DB 验证。
- 文档更新。
- 修边角 TS 类型 / a11y。

交付物：

```text
CRITICAL + HIGH findings 全部关闭
```

---

## 11. 建议优先执行的第一个开发切片

建议从最小闭环开始：

```text
S1-切片：虚拟赛可提交且不污染真实数据
```

包含：

1. 新增 participant real/virtual mapper。
2. 新增 migration：
   - slug unique。
   - participant unique。
   - dedupe。
3. `startVirtualContest` 幂等化。
4. `submitContestProblem` 支持虚拟分支。
5. `fetchContestProblemSubmissions` 支持 virtual session 过滤。
6. 前端提交 / 查询带 `X-Virtual-Session-Id`。
7. 单测 + 一个虚拟赛 happy path 测试。

这个切片完成后，用户至少可以真实体验：

```text
进入 linked-list-special
  -> 开虚拟赛
  -> 提交
  -> 判题
  -> 题目状态只在虚拟赛内更新
```

这会先打掉最致命的 F-01 / F-11 / F-12 / F-22 / F-31 / F-50 系列问题。
