# ADR-010: contest state machine boundary (CANCELLED / F-35 P4 / F-50-52)

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Accepted** (R7.5 决策记录，2026-06-17) |
| **日期 (Date)** | 2026-06-17 |
| **作者 (Author)** | DavidHLP |
| **来源** | [PRD §P4 / P5](../contest/PRD.md) + [REVIEW_V3 §3 P0-2 / P0-4](../contest/REVIEW_V3.md) + [EXECUTION_PLAN_R7 §6](../contest/_archive/EXECUTION_PLAN_R7_2026-06-17.md) |
| **关联代码** | `contest/scheduler/ContestScheduler.java` (transitionToFinished), `contest/service/impl/ContestSchedulerServiceImpl.java` (startVirtualContest) |

---

## 1. Context

PRD P4/P5 决策 + REVIEW_V3 标注的"未实现状态"：

- **F-35 (P4)**：transitionToFinished 之后，真实 participant 应保持 `FINISHED`（已被 R3.1 实现），但 FINISHED 集合与 REGISTERED 集合的边界（哪些字段保留）需显式记录。
- **F-38 (P5)**：是否允许 `CANCELLED` 比赛开虚拟 session？
- **F-50/51/52 (A4)**：虚拟赛 submission 的"作用域"——是否污染全局 / 跨 user 隔离 / 跨 contest 隔离。

## 2. Decision

### 2.1 F-35: FINISHED participant 状态机边界

**决策**：维持当前实现：
- 真实 participant（`is_virtual=0`）从 `STARTED → FINISHED`（R3.1 `finishStartedRealParticipants` 触发）
- 虚拟 participant（`is_virtual=1`）从 `STARTED → FINISHED`（R3.1 `autoFinishVirtualParticipants` 触发）
- `REGISTERED` 保持：未启动的用户（`is_started=false`）保留在 `REGISTERED` 状态；不影响 `FINISHED` 集合
- `attempt_count` 在 `STARTED` 期间累加；`FINISHED` 后冻结
- `total_score` / `total_penalty` / `final_rank` 在 `FINISHED` 之前持续更新；之后冻结

**影响**：无代码改动。注释记录于 `ContestParticipant` 字段 Javadoc。

### 2.2 F-38: CANCELLED 是否允许开虚拟

**决策**：**不允许**。理由：
- `CANCELLED` 是终止态（admin 主动取消），语义上竞赛未"完成"
- 虚拟 replay 的前提是"竞赛历史成绩"（用于练习）；CANCELLED 比赛无真实成绩可言
- 当前 `startVirtualContest` 入口已隐式拒绝（要求 `ContestStatus.FINISHED`）；此 ADR 显式记录决策

**影响**：无代码改动（已实现）。

### 2.3 F-50/51/52: 虚拟赛数据作用域

**决策**：
- **F-50（不污染全局）**：虚拟 AC 不写入 `user_achievements`（R6.3 `findIsVirtualBySubmissionId` gate 收口）
- **F-51（跨 user 隔离）**：`contest_participants UNIQUE(contest_id, user_id, virtual_session_id)`（V20260602:164）已保证
- **F-52（跨 contest 隔离）**：见 [F-22 audit doc](../contest/F-22-VIRTUAL-SESSION-CROSS-CONTEST-AUDIT.md) — R3.3 FOR UPDATE 串行化 + 三元组唯一约束覆盖

**影响**：无代码改动（已实现 + 审计落地）。

## 3. Consequences

### 3.1 Positive
- 状态机边界显式化，PRD P4/P5 决策有 ADR 记录
- F-50/51/52 三个"作用域"问题全部确认无 violation
- R8 候选明确：per-contest 排行榜 evict（CANCELLED 状态不计入排行榜入口需另议）

### 3.2 Negative
- 无（纯文档化决策）

## 4. Validation

- [x] F-35 维持当前实现 + 注释
- [x] F-38 `startVirtualContest` 隐式拒绝 CANCELLED（已存在）
- [x] F-50 R6.3 成就 gate
- [x] F-51 schema 唯一约束
- [x] F-52 R3.3 FOR UPDATE 串行化 + F-22 audit doc

## 5. References

- [REVIEW_V3.md §3 P0-2 / P0-4](../contest/REVIEW_V3.md)
- [EXECUTION_PLAN_R7 §6](../contest/_archive/EXECUTION_PLAN_R7_2026-06-17.md)
- [F-22 audit doc](../contest/F-22-VIRTUAL-SESSION-CROSS-CONTEST-AUDIT.md)
- [ADR-007 §6 / §7](../adr/ADR-007-virtual-contest-lifecycle-and-rating-isolation.md)
