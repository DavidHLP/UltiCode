# F-22 虚拟 session 跨 contest 复用审计

> **作用**：基于 [_archive/EXECUTION_PLAN_R7 §3.1](./_archive/EXECUTION_PLAN_R7_2026-06-17.md)，对虚拟 session 在跨 contest 场景下是否破坏不变量做审计。
> **创建**：2026-06-17（R7.2 实施时审计）
> **结论**：**无 violation**。R3.3 FOR UPDATE 串行化 + 唯一约束已满足需求。

---

## 1. 场景定义

| 场景 | 操作 | 不变量 |
|------|------|--------|
| **A** | 用户在 contest-1 开虚拟 session S1 | `contest_participants` 新行 (contest-1, user, is_virtual=1, virtual_session_id=S1, status=STARTED) |
| **B** | 用户接着开 contest-2 的虚拟 session S2 | 期望：(contest-2, user, is_virtual=1, virtual_session_id=S2, status=STARTED) 新行；A 不被影响 |
| **C** | 用户同时（多 tab）开 contest-1 + contest-2 虚拟 | 期望：两个新行均创建；FOR UPDATE 串行化 |
| **D** | 用户在 A 已经 S1 STARTED 时，再次点"开虚拟赛" | 期望：S1 直接返回（不创建重复行） |

## 2. 当前代码行为

- `ContestSchedulerServiceImpl.startVirtualContest(contestId, userId)` (R3 后) 入口：
  1. 校验 `contest.status == FINISHED`
  2. `findActiveVirtualSessionForUpdate(contestId, userId)` → SELECT ... FOR UPDATE 找该 (contest, user) 的活跃虚拟会话
  3. 存在则返回，不创建新行（场景 D）
  4. 否则 INSERT 新行 (is_virtual=1, status=STARTED, virtual_session_id=新 UUID)
- 唯一约束：`UNIQUE(contest_id, user_id, virtual_session_id)`（V20260602:164）
- `(contest_id, user_id)` 没有"全局活跃"约束 — 但**业务上**每个 contest 自己的"活跃"独立。

## 3. 场景分析

### 场景 A: 单 contest 单 session
- 行为：新行 INSERT，唯一约束允许（virtual_session_id=新 UUID）
- ✅ 符合预期

### 场景 B: 跨 contest 串行
- 行为：contest-1 S1 创建后，contest-2 的 `findActiveVirtualSessionForUpdate(contest-2, user)` 返回空 → 创建 S2
- (contest-1, user) 与 (contest-2, user) 互不干扰
- ✅ 符合预期

### 场景 C: 跨 contest 并发（多 tab）
- 行为：tab1 开 contest-1、tab2 开 contest-2 几乎同时
- 两个事务分别对 `(contest-1, user)` 和 `(contest-2, user)` 加 FOR UPDATE 行锁
- 因为锁的是不同行的不同索引条目，无冲突
- 两个新行均成功创建
- ✅ 符合预期

### 场景 D: 同一 contest 重复点"开虚拟"
- 行为：`findActiveVirtualSessionForUpdate(contest-1, user)` 命中 S1 → 返回 S1，不创建新行
- ✅ 符合预期

## 4. 不变量

- **B.1**: 同一 `(contest_id, user_id, is_virtual=1)` 同一时刻最多一个 STARTED 虚拟会话（场景 D）
  - R3.3 FOR UPDATE 串行化保证 ✅
- **B.2**: 同一 `(contest_id, user_id)` 可在不同 contest 中**各自有**活跃虚拟会话（场景 B/C）
  - schema 未禁止，且不变量在 contest 范围内**自然成立** ✅
  - 注意：没有"全局单活跃"约束。如果业务上希望"同一用户同时只能有一个虚拟会话"，需要新增跨 contest 唯一约束（**当前 PRD 未要求**）

## 5. 结论

✅ **审计结论：无 violation**。R3.3 现有实现已满足 F-22 描述的需求（无需修代码）。

R8 候选（**非阻断**）：如果产品决定加"全局单活跃"约束（同一用户同一时刻只能开一个虚拟赛），需新增跨 contest 唯一约束：
```sql
-- 假设 schema 加 (user_id, is_active_global) generated column
ALTER TABLE contest_participants
  ADD COLUMN is_active_global TINYINT GENERATED ALWAYS AS (
    CASE WHEN is_virtual = 1 AND status = 'STARTED' THEN 1 ELSE NULL END
  ) VIRTUAL;
ALTER TABLE contest_participants ADD UNIQUE KEY uk_active_global (user_id, is_active_global);
```

当前**不实施**——会改变业务语义（多 contest 复用），需要产品 + 业务方共同决定。
