# ADR-009: isRated gate + virtual-rating isolation

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Accepted** (R6.1 / R6.5 已实施，2026-06-17) |
| **日期 (Date)** | 2026-06-17 |
| **作者 (Author)** | DavidHLP |
| **来源** | [REVIEW_V3.md](../contest/REVIEW_V3.md) §3 P0-1 / F-03 / F-10 |
| **执行计划** | [_archive/EXECUTION_PLAN_R6 Round 6.1](../contest/_archive/EXECUTION_PLAN_R6_2026-06-17.md#round-61--评分正确性收尾f-03--f-10-决策) |
| **关联代码** | `contest/service/impl/RatingCalculationServiceImpl.java` (L36-49 isRated gate), `docs/adr/ADR-007-virtual-contest-lifecycle-and-rating-isolation.md` §7 (F-10 决策) |

---

## 1. Context

PRD §1.3 把 F-03 (isRated 守卫缺失) 列为 CRITICAL、F-10 (finishVirtual 不重算) 列为 HIGH。R1-R5 修了"虚拟不影响 rating"（R3.2 `is_virtual=0` 过滤），但 R6 复审时发现：

- `RatingCalculationServiceImpl.calculateAndUpdate` 入口**没有**读 `contest.isRated`，意味着**练习赛 / 私人赛**（isRated=false）也会被算进 Elo 积分。用户在练习赛 AC 100 题会得到 100 个 fake Elo gain，污染真实榜。
- F-10 在 R3.2 之后**自然不重算**（虚拟 participant 不进入 rating 池），但这个隐式行为没有显式记录，未来读者会困惑"finishVirtual 是否应该触发 rating recalculation"。

## 2. Decision

### 2.1 F-03 isRated gate（一行业务代码）

`RatingCalculationServiceImpl.calculateAndUpdate(contestId)` 入口先 load `Contest`，若 `!isRated` 则 log + return：

```java
Contest contest = contestMapper.selectById(contestId);
if (contest == null || !Boolean.TRUE.equals(contest.getIsRated())) {
    log.info("R6.1: contest {} isRated=false, skip rating update", contestId);
    return;
}
```

**为什么是单独的查询（而不是复用 #1 的 findRealParticipantsByContestId 查询）**：
- `findRealParticipantsByContestId` 返回的是 participant 列表，要拿 `isRated` 还得在 service 端再查一次 contest；提前 load contest + 走 `is_virtual=0` 过滤**同一个查询**即可，节省 1 次 SELECT。

### 2.2 F-10 决策（虚拟 session 不重算 rating）

引用 ADR-007 §7：
- 语义边界：虚拟 session 是 per-user replay，**不是**真实竞赛表现的一部分
- R3.2 已强制：`is_virtual=0` 在 SQL 层过滤；finishVirtual 路径天然不进入 rating 池
- R6.1 守卫加强：`isRated=false` 提前 return

## 3. Consequences

### 3.1 Positive

- 练习赛 / 私人赛不再污染 Elo 积分
- finishVirtual 路径**显式不重算** rating（未来读者可查 ADR-007 §7）
- isRated + is_virtual 双层防护：业务层（isRated）+ 数据层（is_virtual=0）

### 3.2 Negative

- 多一次 `contestMapper.selectById` 查询（每次 `calculateAndUpdate` 调用）。**优化空间**：cache contest scoring config（isRated / penaltyPerWrong / scoringMode）在 submission 写入路径（如 R6.5 的 CRIT-2 generated column 同样思路），但本轮不收。

## 4. Validation

- [x] `RatingCalculationServiceImplTest.calculateAndUpdate_isRatedFalse_skipsUpdate` 验证 noop
- [x] 既有 4 个测试回归（isRated=true 路径行为不变）
- [x] ADR-007 §7 F-10 决策段落记录

## 5. References

- [REVIEW_V3.md](../contest/REVIEW_V3.md) §3 P0-1
- [_archive/EXECUTION_PLAN_R6 Round 6.1](../contest/_archive/EXECUTION_PLAN_R6_2026-06-17.md#round-61--评分正确性收尾f-03--f-10-决策)
- [ADR-007 §7](../adr/ADR-007-virtual-contest-lifecycle-and-rating-isolation.md) F-10 决策
