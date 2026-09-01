# F-01 状态机互斥审计（F-01 / R6.2）

> **作用**：基于 [PRD §1.3 F-01](./PRD.md) + [REVIEW_V3 §1-2](./REVIEW_V3.md) 的"待复核"标注，对 contest 模块所有 status transition 做一次系统性审计。
> **创建**：2026-06-17（R6.2 实施时审计）
> **R10 销项（2026-06-17）**：§3.1 `finishVirtualContest` + §6.4 F-06 `timeFromStart` 两项 R6.2 待复核已 R10 复核通过，无 violation。详见 [EXECUTION_PLAN_R10 R10.6/10.7](./_archive/EXECUTION_PLAN_R10_2026-06-18.md)
> **方法**：逐方法枚举所有 status 转换点 + 标注 transition 保护机制 + 列出已发现的不变量 violation
> **关联**：[_archive/EXECUTION_PLAN_R6 §3.1](./_archive/EXECUTION_PLAN_R6_2026-06-17.md#round-62--状态机--虚拟时间审计--服务端时间窗-f-01--f-06--f-07)

---

## 1. 审计范围

两个状态机 + 1 个跨状态机组合：

| 状态机 | 实体 | 状态集 | 转移 |
|--------|------|--------|------|
| **A. ContestStatus** | Contest | `DRAFT / UPCOMING / RUNNING / FINISHED / CANCELLED` | `DRAFT→UPCOMING`（admin 发布） / `UPCOMING→RUNNING`（scheduler） / `RUNNING→FINISHED`（scheduler） / `*→CANCELLED`（admin） |
| **B. ContestParticipantStatus** | ContestParticipant | `REGISTERED / STARTED / FINISHED` | `REGISTERED→STARTED`（开赛 / 开虚拟） / `STARTED→FINISHED`（AC / timeout / auto-finish） |
| **C. 跨状态机** | Virtual Session | 实体：ContestParticipant（`is_virtual=1`） | 跨 A+B：在 Contest=FINISHED 之后创建；B 仍走 `REGISTERED→STARTED→FINISHED` |

---

## 2. ContestStatus 转移点审计

| # | 转移 | 入口 | 保护机制 | 审计 |
|---|------|------|----------|------|
| 1 | `UPCOMING→RUNNING` | `ContestScheduler.run()` (`:44-65`) | `findByStatus("UPCOMING")` + `if (!startTime.isAfter(now))` 条件；幂等检查 `if (RUNNING.equals(contest.getStatus())) return;` | ✅ R3 已加；条件更新 + 幂等 |
| 2 | `RUNNING→FINISHED` | `ContestScheduler.run()` + `transitionToFinished` | 同上；`@Scheduled` 10s tick；幂等检查 | ✅ R3 已加 |
| 3 | `DRAFT→UPCOMING` | `ContestServiceImpl.createContest`（`isPublished=true`）/ `AdminContestServiceImpl.createContest` | 无显式 transition 校验；直接 `setStatus(UPCOMING)` | ⚠️ 弱保护：依赖调用方不传 `isPublished=true` 给已存在的 contest（但 `createContest` 路径只能 insert 新行，所以无 violation） |
| 4 | `*→CANCELLED` | 未实现 | 无入口 | 🟡 缺：PRD 提及但代码无 `cancelContest` 方法 |
| 5 | `*→DRAFT` | 无 | 无 | 🟡 缺：但与 4 同样未在 PRD P0/P1 中强制 |

### 2.1 发现的不变量

- **不变量 A.1**：`status` 转移只能由 scheduler 或 admin 触发
  - 现状：✅ 由 `ContestScheduler.run()` (`@Scheduled`) + `ContestServiceImpl` 持有角色检查 (`hasAnyRole("ADMIN", "SUPER_ADMIN")`)
  - 风险：web 层其他端点直接改 `contest.status` 的可能 → 审计无（所有改 status 的路径都通过 `updateById`，**没有** 公开 PATCH `/contests/{id}/status` 端点）

---

## 3. ContestParticipantStatus 转移点审计

| # | 转移 | 入口 | 保护机制 | 审计 |
|---|------|------|----------|------|
| 1 | `REGISTERED→STARTED` | `ContestScoringServiceImpl.batchStartParticipants` (`:206-214`) | `UPDATE ... WHERE status='REGISTERED'` 条件更新 | ✅ R0 + R3 验证 |
| 2 | `REGISTERED→STARTED` (virtual) | `ContestSchedulerServiceImpl.startVirtualContest` (`:160-186`) | 不走 batch：直接 `insert` 新行（status=STARTED, is_virtual=1） | ✅ 不与 #1 冲突：虚拟新建而非转移 |
| 3 | `STARTED→FINISHED` (real) | `ContestScoringServiceImpl.finishStartedRealParticipants` (R3.1 新增) | `UPDATE ... WHERE status='STARTED' AND is_virtual=0` | ✅ R3 已加 |
| 4 | `STARTED→FINISHED` (virtual) | `ContestScoringServiceImpl.autoFinishVirtualParticipants` / `bulkFinishByIds` (R3.1+R6.M2) | `findVirtualParticipantsToFinish` + `bulkFinishByIds` | ✅ R3+R6 已加 |
| 5 | `STARTED→FINISHED` (user trigger) | `ContestSchedulerServiceImpl.finishVirtualContest` (前端调) | 不直接改 participant.status（应通过 #4 的 service 路径） | ✅ **已复核（R10 2026-06-17）**：走 `bulkFinishByIds`，与 R3 集中调度原则一致。见 §3.1 复核结果 |

### 3.1 F-01 关注：#5 与 #4 的协调

`finishVirtualContest` 由前端 `VirtualContestTimer.vue:79` 在用户点"完成"时调用。**正确的实现**：应该也走 `autoFinishVirtualParticipants` 路径（让 scheduler 在个 tick 结算）或直接 `bulkFinishByIds(participantId)`（**不**绕过调度）。

需要审计 `ContestSchedulerServiceImpl.finishVirtualContest` 实际实现。**若** 它直接 `updateStatus(participantId, "FINISHED")` 而不走 #4 的 service 路径，**则**与 R3 的"auto-finish 集中调度"原则冲突。

> **审计动作**：R6.2 第二步复核 `finishVirtualContest` 实际路径；如有 violation，加固到 `bulkFinishByIds` 调用。
>
> **✅ 复核结果（R10 2026-06-17，R10.6）**：代码 `ContestSchedulerServiceImpl.finishVirtualContest:251-255` 走 `participantMapper.bulkFinishByIds(List.of(participant.getId()), LocalDateTime.now())`，与 R3 的"auto-finish 集中调度"原则一致。**无 violation**。

### 3.2 不变量

- **不变量 B.1**：同一 (contest_id, user_id) 同一时刻最多一个 `is_virtual=0` STARTED 真实参赛者
  - 现状：依赖应用层逻辑（`addParticipant` 插入前不检查是否已存在）
  - 风险：⚠️ 用户重复点击"开始虚拟赛"前已注册真实赛 → 可能产生 STARTED+STARTED 重复（schema 未禁止）
  - **R6.5 缓解**：CRIT-2 generated column 部分唯一索引

- **不变量 B.2**：同一 (contest_id, user_id, is_virtual=1) 同一时刻最多一个 STARTED 虚拟会话
  - 现状：✅ R3 `findActiveVirtualSessionForUpdate` + FOR UPDATE 行锁串行化

- **不变量 B.3**：virtual session 仅当 contest.status=FINISHED 时可创建
  - 现状：✅ `ContestSchedulerServiceImpl.startVirtualContest` 入口校验 `ContestStatus.FINISHED`

---

## 4. 跨状态机组合（C. Virtual Session）

| 场景 | 路径 | 不变量 | 审计 |
|------|------|--------|------|
| 真实赛未结束 → 试图开虚拟赛 | `startVirtualContest` 入口 `!FINISHED.equals(...)` 拒绝 | ✅ | 正确拒绝 |
| 真实赛结束后 → 真实 participant 已 FINISHED（R3.1） → 用户开虚拟 → 新行 status=STARTED, is_virtual=1 | 跨表/跨状态机 | ✅ | 真实 participant 终态不受影响（不同行） |
| 真实赛结束后 → 同一用户开多个虚拟会话 | `findActiveVirtualSessionForUpdate` 短瞬拒绝新建 | ✅ R3 | 同一时刻只一个活跃 |

---

## 5. F-01 审计结论

| 子项 | 状态 |
|------|------|
| ContestStatus 转移保护 | ✅ 大部分 OK；`CANCELLED` 状态未实现但不在 P0/P1 |
| ContestParticipantStatus 转移保护 | ✅ R3 + R6.1 + R10.6 覆盖；#5 (`finishVirtualContest`) **已复核通过**，见 §3.1 复核结果 |
| 跨状态机不变量 | ✅ R3 覆盖 |
| 不变量 B.1 (CRIT-2) | ⚠️ **R6.5 通过 generated column 部分唯一索引收口** |
| 不变量 B.2 (R3 FOR UPDATE) | ✅ |
| 不变量 B.3 (FINISHED-only virtual) | ✅ |

### 5.1 必须修复（仅 1 项）

- **#5 `finishVirtualContest` 实际实现**（R6.2.1 → R10.6 已复核通过，2026-06-17）：代码走 `bulkFinishByIds`，与 R3 集中调度原则一致。**无 violation，无需修复**。

### 5.2 R6.5 收口

- **不变量 B.1**：通过 R6.5 §6.2 的 generated column partial unique index 收口
- **CANCELLED / DRAFT 转移**：不在 P0/P1，留 R7

---

## 6. R6.2.2 F-06 提交读虚拟时间审计（同步产出）

`timeFromStart` 字段在 `contest_submissions` 表，记录"自参赛者开赛以来的秒数"。

### 6.1 真实赛路径

- `ContestScoringServiceImpl.applyJudgeResult` 入口前：`contestSubmissionMapper.findBySubmissionId` 读出 `cs.timeFromStart`
- 写入来源：`submissionService.submit` 路径 → `contestSubmissionMapper.insert(cs)` 时 `cs.timeFromStart = now - contest.actualStartTime` 秒数
- **审计**：✅ 真实赛时间基于 `actualStartTime`（即开赛时钟）

### 6.2 虚拟赛路径

- `startVirtualContest` 创建 participant 时 `startedAt = now`
- `submitContestProblem` 路径未传 `timeFromStart` —— 推测 backend 写入时按"now - participant.startedAt"算
- **审计**：✅ **已复核（R10 2026-06-17，R10.7）**：代码 `SubmissionServiceImpl.recordContestSubmissionIfNeeded:1387-1395` 使用三元 `Boolean.TRUE.equals(p.getIsVirtual()) ? virtualClock : contestClock`，其中 `virtualClock = p.getStartedAt()`、`contestClock = contest.getActualStartTime() ?: startTime()`。**无 violation**。

### 6.3 F-06 风险

如果 §6.2 确认是 bug：
- 虚拟赛 `timeFromStart` 显示用户"开赛"后秒数会从 0 开始正确
- 真实赛 `timeFromStart` 显示用户"在竞赛中"秒数（基于 actualStartTime）
- 但若两个混用 → 排行榜、罚时计算、首杀判定会错

### 6.4 复核结果（R10 2026-06-17，R10.7）

- ✅ 实际：读 `SubmissionServiceImpl.recordContestSubmissionIfNeeded:1387-1395`（路径：`submissionService.submit` → `recordContestSubmissionIfNeeded` → `contestSubmissionMapper.insert`）
- ✅ `timeFromStart` 三元分支正确：虚拟赛用 `p.getStartedAt()`，真实赛用 `contest.getActualStartTime()`（回退 `startTime`）
- ✅ **无 violation，无需 1 行修复**；R6.2 实施时已加分支（注释锚定 "R6.2 / F-06: pick the right clock per participant type"）

---

## 7. 行动项（汇总）

| 行动 | 优先级 | 归属 | 状态 |
|------|--------|------|------|
| 复核 `finishVirtualContest` 实际实现 | P0 | R6.2.1 → R10.6 | ✅ **已复核通过（R10 2026-06-17）** |
| 复核 `submissionService.submit` 中 `timeFromStart` 来源 | P0 | R6.2.2 → R10.7 | ✅ **已复核通过（R10 2026-06-17）** |
| 不变量 B.1 generated column | P1 | R6.5 | ✅ R6.5 已收口 |
| `CANCELLED` / `DRAFT` 状态机完整性 | P2 | R7 | 🟡 留 R11 业务决策 |
