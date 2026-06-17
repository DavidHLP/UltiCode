# UltiCode Contest 模块：运作思路与漏洞分析

> **类型**：跨模块架构分析报告
> **日期**：2026-06-16
> **范围**：`backend-spring/modules/contest/` + `modules/submission/` 联动 + 数据库 schema（`init-db/migrations/`）
> **分析方法**：静态代码审计（CodeGraph + 源码精读 + 全局 grep 证据链）
> **状态**：经对抗性审查（2026-06-16），20 条 claim 中 17 条完全属实、2 条部分属实、1 条误判（详见附录 A）。结论待运行时验证（见文末「快速验证清单」）

---

## 一、设计运作思路（设计意图）

### 1. 状态机

**Contest 实体状态**（`backend-spring/.../contest/entity/Contest.java:44`）：`DRAFT → UPCOMING → RUNNING → FINISHED`（+ `CANCELLED`）

**Participant 状态**（`backend-spring/.../contest/entity/enums/ContestParticipantStatus.java`）：`REGISTERED → STARTED → FINISHED`（+ `DISQUALIFIED`）

```
                  ┌──────────────────────────────────────────┐
                  │ 路径分叉：用户自建 vs 管理员后台           │
                  └──────────────────────────────────────────┘
        用户路径 (ContestServiceImpl.createContest:91)        管理员路径 (AdminContestServiceImpl.createContest:127)
        ─────────────────────────────────────                  ─────────────────────────────────────
              createContest → DRAFT                                  createContest → UPCOMING（直接）
                              ↓ (应有 publish 端点，实际缺失 ⚠️ P0-3)             ↓
                            ✗ 死锁                                                    UPCOMING ← 用户 register（要求此状态）
                                                                                        ↓ ContestScheduler.run() 每 10s 轮询 startTime 到点
                                                                                      RUNNING ← 提交代码（要求 contest=RUNNING 且 participant=STARTED）
                                                                                        ↓ ContestScheduler 轮询 endTime 到点
                                                                                      FINISHED → RatingCalculationService 算 Elo + 写 finalRank
```

### 2. 核心数据表关系

| 表 | 作用 | 关键唯一约束 |
|---|---|---|
| `contests` | 比赛元数据（时间窗、scoringMode、freezeTime、maxParticipants） | — |
| `contest_participants` | 参赛者 + 计分（totalScore/totalPenalty/finalRank） | `(contest_id, user_id, virtual_session_id)` ⚠️ P1-3 |
| `contest_problems` | 比赛-题目关联 + problemIndex(A/B/C) | `(contest_id, problem_id)` |
| `contest_submissions` | 比赛提交快照 | — |
| `contest_problem_results` | 每题结果（死表 ⚠️ P0-1） | `(participant_id, contest_problem_id)` |
| `global_rankings` | 全局 Elo 段位 | — |

### 3. 关键流程

- **提交流**：`ContestController.submitContestProblem` → 校验 RUNNING + STARTED → `submissionService.submit` → `recordContestSubmissionIfNeeded` 反查 `contest_problems` 写 `contest_submissions`
- **排名流**：`RankingServiceImpl` 直接读 `contest_participants` 的 `totalScore/totalPenalty/attemptCount` 排序，**不做实时聚合**
- **评分流**：`ContestScheduler.transitionToFinished` 同步触发 `RatingCalculationService` → 按 score 排序赋 `finalRank` → CF-Elo 变体更新 `global_rankings`
- **虚拟比赛流**：`startVirtualContest`（仅 FINISHED 比赛）直接建一条 `isVirtual=true` 的 participant 记录，`status=STARTED`，`virtualSessionId=UUID`

---

## 二、致命缺陷（P0 — 主管线断裂）

### 🔴 P0-1：比赛计分回写完全缺失（排名/评分体系是空的）

**证据**：全局搜索 `setTotalScore / setTotalPenalty / setAttemptCount / setTotalTime / setLastSolveTime`，**唯一命中是 `RatingCalculationServiceImpl.java:54` 写 `finalRank`**，五个计分字段零写入点。

`SubmissionServiceImpl.recordContestSubmissionIfNeeded`（`backend-spring/.../submission/service/impl/SubmissionServiceImpl.java:1302-1331`）只做两件事：
1. 检查 participant 是否 `STARTED`
2. `contestSubmissionMapper.insert(cs)` 插一条快照

**它从不聚合"通过/罚时"到 `contest_participants`**。`JudgeWorkerProcessor` 判题完成后也无回写。

**后果链**：
- `RankingServiceImpl.getContestRanking / getLiveRanking` 读 `totalScore/totalPenalty/attemptCount` → 全是 `null/0`
- 实时榜单、最终榜单 **所有人 0 分**
- `RatingCalculationServiceImpl:41-49` 按 totalScore 排序 → 所有人并列，finalRank 失真 → Elo 计算的 actual/expected 全错 → **全局段位污染**
- `contest_problem_results`、`first_solve_records`、`contest_problems.solvedCount` 这些表/字段**无任何写入点**（死表）

### 🔴 P0-2：正式比赛参赛者永远卡在 REGISTERED，无法提交

**证据**：全模块唯一的 `setStatus(STARTED)` 在 `ContestSchedulerServiceImpl.java:161`（虚拟比赛）。正式比赛 `registerForContest`（`:60`）置 `REGISTERED` 后，**没有任何代码转 `STARTED`**：
- `check-in` 端点是 register 的别名（`ContestController.java:468-476` + 注释明说 "alias for register"），不转状态
- `ContestScheduler.transitionToRunning`（`ContestScheduler.java:176`）只改 `contest.status`，不动 participant
- `submitContestProblem`（`ContestServiceImpl.java:231`）硬要求 `participant.status == STARTED`，否则抛 `CONTEST_NOT_STARTED`

**后果**：正式比赛注册用户提交必然 403/400；只有虚拟比赛能提交。结合 P0-1，contest 正式场景完全不可用。

### 🔴 P0-3：用户自建比赛卡 DRAFT 无法发布（DRAFT → UPCOMING 状态转换缺失）

**证据（修正后）**：grep `setStatus.*UPCOMING` 在 contest 模块**有 1 处命中**：`AdminContestServiceImpl.java:127`（管理员创建比赛时**直接置 UPCOMING**，跳过 DRAFT）。**因此本 bug 仅作用于"用户自建比赛"路径**，管理员路径无此问题。

- 用户路径 `ContestServiceImpl.createContest:91` 硬编码 `status=DRAFT`
- 管理员路径 `AdminContestServiceImpl.createContest:127` 直接置 `status=UPCOMING`（一步到位）
- 用户路径闭环矛盾（`ContestServiceImpl.java` + `ContestSchedulerServiceImpl.java`）：
  - `registerForContest:44` 要求 `status == UPCOMING`
  - `updateContest:113` 要求 `status == UPCOMING`（DRAFT 时连改 startTime 都不行）
  - `startContest:583` 允许 `DRAFT/UPCOMING → RUNNING`
  - **没有 publish 端点**把 `DRAFT → UPCOMING`

**后果（修正后）**：
- 管理员后台建比赛 → 直接 UPCOMING → register/update/start 全部正常 ✓
- 用户自建比赛 → 永远 DRAFT → register 报 `CONTEST_ONLY_REGISTER_UPCOMING`、update 报 `CONTEST_ONLY_UPDATE_UPCOMING`、只能被管理员 `startContest` 强跳 RUNNING（仍无法 register）→ **用户路径下"发布报名"流程走不通**
- 实际场景中，运营/种子数据下多数比赛由管理员后台建 → **本 bug 在生产主路径上影响有限**，但若产品允许用户自建比赛则必须修复

---

## 三、高危缺陷（P1）

### 🟠 P1-1：`freezeTime`（封榜）字段完全未使用

`Contest.freezeTime` 存在，但 `RankingServiceImpl.getContestRanking / getLiveRanking` 和 `RatingCalculationService` **都不检查**。封榜后实时排名照常暴露 → 赛中策略泄漏。`getLiveRanking` 还是公开无鉴权端点（`ContestController.java:311`）。

### 🟠 P1-2：提交无时间窗口校验（超时可继续提交）

`submitContestProblem`（`ContestServiceImpl.java:221-237`）只校验 `contest.status == RUNNING`，**不校验**：
- `now <= contest.endTime`（管理员若忘 `endContest`，超时仍可提交）
- 虚拟比赛 `now <= startedAt + duration`（`getVirtualSession` 的 endTime 纯前端展示，后端不强制）

`ContestScheduler` 只推进 `contests` 表，**不处理虚拟 participant 超时** → 虚拟比赛永不自动 FINISHED，超时提交无阻拦。

### 🟠 P1-3：并发重复注册（唯一约束被 NULL 击穿）

表唯一键是 `(contest_id, user_id, virtual_session_id)`（`init-db/migrations/V20260530...sql:164`）。正式比赛注册时 `virtual_session_id = NULL`，而 **MySQL 唯一约束对 NULL 不去重**（多 NULL 不冲突）。

`registerForContest`（`ContestSchedulerServiceImpl.java:51-63`）先 `existsByContestIdAndUserId`（应用层检查）再 insert，是典型 **TOCTOU**：两并发请求都过检查 → 都 `tryIncrementRegisteredCount` → 都 insert（唯一约束不拦）→ `registeredCount` 多算 + 重复 participant 记录。

而 `findByContestIdAndUserId` 返回 `Optional`（单条），多条记录时 MyBatis `selectOne` 行为不确定 → 排名重复计入 / 抛异常。

### 🟠 P1-4：虚拟比赛与正式参赛记录同表混存

`startVirtualContest`（`ContestSchedulerServiceImpl.java:152-166`）：若 `existing.isPresent()` 但 `isVirtual=false`（正式参赛过），条件 `existing.isPresent() && isVirtual==true` 为 false → **插入第二条 participant 记录**。一个用户在一个 contest 出现两条记录：

- `findByContestIdAndUserId`（`finishVirtualContest:202`、`getParticipationStatus`、`toVO`）取回哪条不确定 → 可能 `finish` 到正式记录、排名取到虚拟记录
- `RatingCalculationServiceImpl` 按 `contestId + status=STARTED` 拉所有 participant，**虚拟记录会被当成正式参赛者计入 rating**（虚拟本不应影响 rating）

### 🟠 P1-5：Rating 计算 O(n²) DB 查询 + 同步阻塞调度器

`RatingCalculationServiceImpl.calculateNewRating`（`:94-114`）对每个参赛者遍历所有对手，每个对手 `globalRankingMapper.findByUserId`（`:102`）→ **O(n²) 次单行查询**。n=500 时 25 万次查询。且在 `transitionToFinished`（`ContestScheduler.java:219`）**同步**执行，阻塞每 10s 轮询的调度线程。

---

## 四、中危缺陷（P2）

| ID | 问题 | 位置 |
|---|---|---|
| P2-1 | **赛中可加/删题**：`addProblem`/`removeProblem` 不检查 contest.status，RUNNING/FINISHED 比赛仍可改题库；`problemIndex` 用 `count` 算字母（`'A'+count`），删题后索引错乱/重复 | `ContestServiceImpl.java:620-665` |
| P2-2 | **虚拟比赛不计 rating**：`finishVirtualContest` 不触发 rating，且 scheduler 不自动 finish 虚拟 participant → 虚拟参赛者永远拿不到 finalRank | `ContestSchedulerServiceImpl.java:201` |
| P2-3 | **`participantCount`/`submissionCount` 无写入点**：与 P0-1 同源，统计字段恒为 0 | `Contest.java:69-71` |
| P2-4 | **`getStats` 失真**：统计 STARTED 数，但正式 participant 永远 REGISTERED（P0-2）→ active 恒 0 | `ContestServiceImpl.java:322-333` |
| P2-5 | **`deleteContest` 不清理关联**：软删 contest，participants/problems/submissions 残留，污染统计与排名 | `ContestServiceImpl.java:144-161` |
| P2-6 | **公开排名无鉴权**：`getContestRanking`/`getLiveRanking` 公开，可拉全量用户排名（含 userId/username），叠加 P1-1 封榜泄漏 | `ContestController.java:285,311` |
| P2-7 | **`unregister` 不校验时间窗**：只查 status==UPCOMING，不查 registrationEnd | `ContestSchedulerServiceImpl.java:76` |

---

## 五、低危 / 设计异味（P3）

- **`tryIncrementRegisteredCount` 与 insert 顺序执行**（`ContestSchedulerServiceImpl.java:54-63`）：**当前不是 bug**。`registerForContest:36` 方法级 `@Transactional`，Spring 默认 RuntimeException 回滚——insert 抛 `BusinessException` / `DataIntegrityViolationException` 时 count 也会回滚。**真实风险场景**（目前不存在）：① 显式 try-catch 吞掉异常；② 抛 checked Exception；③ 异步上下文丢失事务。若未来重构破坏 `@Transactional` 边界，此 claim 才成立
- **`toVO` 里 `Long.parseLong(createdBy)`**（`ContestServiceImpl.java:430`）：createdBy 是 String(UUID) 强转 Long → **已被 try-catch 兜住**（catch NFE 后 set null），不会冒异常；但 `createdById` **功能上恒为 null**（UUID 字符串无法 parseLong），前端拿不到创建者 ID。形式与原文档描述略有不同：原文档说"注定 NFE"——实际 NFE 被吞，结果是 `createdById` 静默丢失
- **`Map.of()` 用于审计上下文**（L98/135/158/589/590/610/611/638/663 共 9 处）：**反模式但当前 NPE 风险低**。9 处调用的 value 都是必填字段（title/slug/status/problemId/problemIndex），**当前不存在 NPE 路径**。但违反项目 `java-map-of-null-safety` skill——若未来审计上下文添加可空字段会立即 NPE，建议改用 `HashMap` / `requireNonNullElse`
- **`resolveContestId` 兜底返回原始输入**（`ContestController.java:667-671`）：slug 解析失败不报错，把脏值传下游，掩盖问题
- **`@Cacheable("contestRanking")`**（`ContestServiceImpl.java:336,343`）：排名缓存与 P0-1 结合，会缓存错误的 0 分榜单

---

## 六、快速验证清单（确认严重度）

```bash
set -a; source .env; set +a

# 1. 确认 P0-1：查实际计分字段是否全空
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql mysql --default-character-set=utf8mb4 \
  -u "$DB_USER" "$DB_NAME" -e \
  "SELECT status, COUNT(*) AS cnt, SUM(total_score) AS score, SUM(total_penalty) AS penalty, SUM(attempt_count) AS attempts FROM contest_participants GROUP BY status;"

# 2. 确认 P0-2/P0-3：正式比赛 participant 状态分布 + 是否有 UPCOMING 比赛
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql mysql --default-character-set=utf8mb4 \
  -u "$DB_USER" "$DB_NAME" -e \
  "SELECT status, COUNT(*) FROM contest_participants WHERE is_virtual=0 GROUP BY status;"
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql mysql --default-character-set=utf8mb4 \
  -u "$DB_USER" "$DB_NAME" -e \
  "SELECT status, COUNT(*) FROM contests WHERE is_deleted=0 GROUP BY status;"

# 3. 确认死表：contest_problem_results / first_solve_records 是否有数据
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql mysql --default-character-set=utf8mb4 \
  -u "$DB_USER" "$DB_NAME" -e \
  "SELECT (SELECT COUNT(*) FROM contest_problem_results) AS results, (SELECT COUNT(*) FROM first_solve_records) AS firsts;"
```

**判读**：若验证 1 显示 `total_score/penalty` 全 0、验证 2 显示正式 participant 全 `REGISTERED`、验证 3 显示空表，则 P0 三连确认。

---

## 七、结论与修复优先级

架构分层清晰、rating 算法实现规范、虚拟比赛设计完整，**但存在一条贯穿性的断裂**：`提交 → 判题 → 计分聚合 → participant 字段更新` 这条链**完全缺失**（P0-1），叠加状态机两处缺口：
- **P0-2 正式比赛无法 STARTED**（影响全部正式比赛流程，与创建路径无关）
- **P0-3 用户自建比赛卡 DRAFT**（**仅影响用户自建路径**，管理员路径不经过 DRAFT，直接置 UPCOMING；当前生产主路径是管理员建比赛，影响有限）

P0-1 + P0-2 导致**正式比赛从注册到排名的主路径整体不通**。P0-3 在用户自建场景下阻断 P0-2 修复（用户卡 DRAFT → 永远进不到 STARTED）。目前只有**虚拟比赛**能部分运作（且仍受 P1-2/4/5 影响）。

**建议修复顺序**：

1. **P0-1 计分聚合**（必修，影响全部比赛）：在 `JudgeWorkerProcessor` 判题完成回调里，根据 `contestId + problemId` 更新对应 participant 的 `totalScore/totalPenalty/attemptCount` + 写 `contest_problem_results`（首次 AC 加分、WA 累计罚时、first_solve 记录）
2. **P0-2 正式比赛 STARTED**（必修，影响全部正式比赛）：在 `ContestScheduler.transitionToRunning` 时，把该 contest 所有 `REGISTERED` participant 批量转 `STARTED`（或新增独立的"进入比赛"端点）
3. **P0-3 publish 端点**（**条件必修**：若产品允许用户自建比赛则必修；否则可降级为 P3）：补 `ContestController` 的 `POST /{id}/publish`：`DRAFT → UPCOMING`，并在用户路径 `ContestServiceImpl.createContest` 中按 `isPublished` 决定初始状态

P0 三项修复后 contest 才具备可用性，再处理 P1 的时间窗/封榜/并发/虚拟隔离，最后清理 P2/P3。

---

## 附录 A：对抗性审查结论（2026-06-16）

经 CodeGraph + 全局 grep + 源码精读 + SQL schema 对照，文档 20 条 claim 的核实结果：

| 类别 | 真 | 部分真 | 假 |
|------|---|--------|-----|
| P0 (3) | 2 | 1（P0-3 用户路径真，管理员路径不真） | 0 |
| P1 (5) | 5 | 0 | 0 |
| P2 (7) | 7 | 0 | 0 |
| P3 (5) | 3 | 1（P3-3 反模式但当前 NPE 风险低） | 1（P3-1 在 @Transactional 下非 bug） |
| **合计** | **17** | **2** | **1** |

**总体可信度**: 85-90%。**主要修正项**已应用至上文 P0-3 / P3-1 / P3-3 段。**残留运行时不验证项**: 见「快速验证清单」(L154-170) 4 个 SQL 验证点 — 静态分析已可下结论，建议在跑修复前用 `docker exec` 做最终确认（注意按 CLAUDE.md 字符集规范加 `--default-character-set=utf8mb4`）。

---

## 附录：证据索引（关键源码位置）

| 关注点 | 文件:行 |
|---|---|
| 提交入口校验 | `ContestServiceImpl.java:221-237` |
| 计分回写（缺失） | `SubmissionServiceImpl.java:1302-1331` |
| 排名读取 | `RankingServiceImpl.java:36-80` |
| Rating 计算 | `RatingCalculationServiceImpl.java:29-92` |
| 状态机调度 | `ContestScheduler.java:44-65, 176-222` |
| 注册/虚拟/取消 | `ContestSchedulerServiceImpl.java:38-227` |
| 管理员端点 | `AdminContestController.java`（全文件 `@PreAuthorize` 已覆盖） |
| 用户端点 | `ContestController.java` |
| 表结构 | `init-db/migrations/V20260530130501__Baseline.sql`（contest_* 系列表） |
