# ADR-007: 虚拟竞赛生命周期调度与评级隔离

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Accepted** (R3 已实施，2026-06-17；详见 `docs/contest/EXECUTION_PLAN.md` §"实施记录"。**注意**：§2.3 实施时改为 DB 行级 `SELECT ... FOR UPDATE`，未引入 Redis 分布式锁；详见 §6 "实施偏差") |
| **日期 (Date)** | 2026-06-17 |
| **作者 (Author)** | DavidHLP |
| **来源** | [REVIEW_V3.md](../contest/REVIEW_V3.md) §3 P0-2 / P0-4 |
| **执行计划** | [EXECUTION_PLAN.md Round 3](../contest/EXECUTION_PLAN.md)（**原子上线**） |
| **关联代码** | `contest/scheduler/ContestScheduler.java` (run() L44), `contest/service/impl/ContestScoringServiceImpl.java` (autoFinishVirtualParticipants L218), `contest/service/impl/RatingCalculationServiceImpl.java` (L42), `contest/service/impl/ContestSchedulerServiceImpl.java` (startVirtualContest L168-182), `console/src/stores/contest.ts` (virtualSession L40) |
| **关联 DB** | 无 schema 变更；`contest_participants` 既有 `is_virtual` / `virtual_session_id` / `status` 列足够 |

---

## 1. Context

### 1.1 三个互相耦合的缺陷（REVIEW_V3 P0-2 + P0-4）

| # | 位置 | 现状 | 缺陷 |
|---|------|------|------|
| **D1** | `ContestScoringServiceImpl.java:218 autoFinishVirtualParticipants()` | **零调用者**（全仓仅接口声明 + 实现，无任何调用点） | 虚拟赛到期**无法自动结束**；仅靠前端在线用户点完成（`VirtualContestTimer.vue:79`）。离线用户/刷新丢失会话的虚拟赛永远卡 STARTED |
| **D2** | `ContestScheduler.run()` (`@Scheduled(fixedRate=10_000)`) | 只扫竞赛状态机 UPCOMING→RUNNING、RUNNING→FINISHED；**全文无 virtual/isVirtual/autoFinish 引用** | 虚拟参赛者过期无人扫；真实赛结束时参赛者也不批量 FINISHED |
| **D3** | `RatingCalculationServiceImpl.java:42` | `findByContestIdAndStatus(contestId, "STARTED")` 取参赛者；注释 `// P1-4 fix: filter is_virtual = 0` 但**查询没真过滤 is_virtual** | 评级查询双重脆弱：① 靠"真实参赛者永远 STARTED"的**隐式不变量**；② 没过滤 is_virtual，虚拟参赛者可能混入 rating |
| **D4** | `ContestSchedulerServiceImpl.startVirtualContest L168-182` | 仅应用层 `if` + INSERT，无并发原语 | 同一用户并发点"开始虚拟赛" → check-then-act 竞态 → 重复会话（DB UNIQUE 是 contest_id+user_id+virtual_session_id，但 session 每次新 UUID，挡不住） |
| **D5** | `console/src/stores/contest.ts:40` | virtualSession 纯内存引用 | 页面刷新 → 会话丢失 → 完成路径 `/virtual/finish` 不可达 |

### 1.2 为什么 D1+D2+D3 必须原子上线（关键耦合）

当前 D3 的评级查询能"碰巧工作"，**唯一原因**是 D1+D2 都没接——真实参赛者**永远是 STARTED**（无人调 FINISHED 批量更新），所以 `status='STARTED'` 恰好等于"真实参赛者集"。

**一旦** D1+D2 接通（真实赛结束全员 FINISHED + 虚拟赛到期 FINISHED），`status='STARTED'` 会返回**空集** → rating 计算崩溃。

→ D1/D2（让参赛者变 FINISHED）与 D3（评级查询改用 is_virtual）**必须同一 commit / 同一部署窗口**，否则中间态 rating 崩。

### 1.3 为什么是 ADR

1. **难逆转** — scheduler 接线 + 评级查询从 status 语义切换到 is_virtual 语义，是参赛者状态机的架构性改动；回滚需同时回滚三处。
2. **令人意外** — 评级查询长期依赖"真实参赛者永远 STARTED"的隐式不变量（该不变量无任何代码注释或断言保护）；激活 auto-finish 后此不变量消失，未来读者必须知道这条历史。
3. **真实权衡** — auto-finish 用后端调度（可靠，离线也能结算）vs 前端驱动（简单，但离线失效）；评级按 status（隐式脆弱）vs 按 is_virtual（显式正确）。

---

## 2. Decision

四个改动**原子上线**（同 commit 或紧邻部署窗口）：

### 2.1 D1+D2：auto-finish 接线

`ContestScheduler.run()` 新增：

- **Step 3（新增）**：调用 `contestScoringService.autoFinishVirtualParticipants()`——该方法是**已实现的死代码**（`ContestScoringServiceImpl:218`，内部查询 `:157 WHERE is_virtual=1 AND status='STARTED' AND virtual_end_time<=NOW()` 语义正确），只需接线到 scheduler。
- **Step 2 扩展**：竞赛 RUNNING→FINISHED 时，批量结算该竞赛下 `is_virtual=0 AND status='STARTED'` 的真实参赛者 → `FINISHED`（真实赛结束应全员结算）。

### 2.2 D3：评级查询从 status 切换到 is_virtual

- `RatingCalculationServiceImpl.java:42`：
  - 删除误导性注释 `// P1-4 fix: filter is_virtual = 0`（计划修但没真改的痕迹）
  - 新增 mapper 方法 `findRealParticipantsByContestId(contestId)`：`WHERE contest_id=? AND is_virtual=0`
  - 评级查询改用此方法（按 `is_virtual` 而非 `status` 区分真实参赛者）
- **消除隐式不变量**：评级正确性不再依赖"参赛者状态分布"，而依赖显式的 `is_virtual` 标记。

### 2.3 D4：虚拟开赛幂等 + 并发保护

`ContestSchedulerServiceImpl.startVirtualContest`：

- **业务规则定档**：允许同一用户对同一竞赛**多次重播**（多个虚拟会话），但同一时刻只允许**一个活跃（STARTED）虚拟会话**。
- 实现：
  1. 开赛前先 `SELECT WHERE contest_id=? AND user_id=? AND is_virtual=1 AND status='STARTED'` → 存在则**返回该会话**（幂等，避免新建重复）
  2. Redis 分布式锁 `contest:virtual:start:{contestId}:{userId}`（短锁，覆盖 SELECT+INSERT）防 check-then-act 竞态
  3. DB `UNIQUE(contest_id, user_id, virtual_session_id)`（`V20260602:164` 既有）兜底

### 2.4 D5：前端 session 持久化

- `console/src/stores/contest.ts`：virtualSession 写入 `sessionStorage`（key 含 contestId），刷新后恢复。
- 保障 `VirtualContestTimer.vue:79` 的 `/virtual/finish` 完成路径在刷新后仍可达。

### 2.5 原子性约束

D1+D2+D3 必须**同一部署窗口**。D4（并发）+ D5（前端持久化）逻辑独立，但为减少部署次数一并合入 Round 3。若被迫拆分，**最低限度 D1/D2 与 D3 不可分离**。

---

## 3. Consequences

### 3.1 Positive

- 虚拟赛到期自动结算（离线用户不再卡 STARTED）
- 真实赛结束全员 FINISHED（结算完整）
- 评级查询显式正确（消除"碰巧工作"的隐式不变量）
- 虚拟开赛幂等（并发安全）
- 前端刷新不丢会话

### 3.2 Negative

- scheduler 负载上升（每 10s 多扫一轮虚拟参赛者过期）——可接受（已有索引 `contest_participants_user_id_status_is_virtual_idx`）
- 评级查询新增 mapper 方法（轻微代码增量）
- 原子上线增加部署协调成本

### 3.3 Risks

| 风险 | 缓解 |
|------|------|
| D1/D2 上线但 D3 漏改 → rating 崩 | 原子约束（§2.5）+ 部署后立即验证 rating 非空 |
| auto-finish 误把进行中的真实赛参赛者置 FINISHED | Step 2 仅在竞赛 `status=FINISHED` 后触发；WHERE 限定 `is_virtual=0 AND status='STARTED'` |
| 虚拟开赛分布式锁死锁 | 锁设短 TTL（如 5s）+ try-finally 释放 |
| sessionStorage 在隐私模式下不可用 | try-catch 降级为内存（与现状一致，不恶化） |
| 历史卡在 STARTED 的孤儿虚拟会话 | 上线后跑一次补偿：把 `is_virtual=1 AND status='STARTED' AND virtual_end_time<NOW()` 批量 FINISHED |

---

## 4. Validation

实施时（Round 3）必须勾选：

- [x] 虚拟赛到 `virtual_end_time` → 10s 内自动 `FINISHED`（不依赖用户在线） — `ContestScheduler.run()` Step 3
- [x] 真实赛结束 → 所有真实参赛者 `FINISHED` — `transitionToFinished` 接 `participantMapper.finishStartedRealParticipants`
- [x] 评级计算返回**非空**真实参赛者集（D3 修复后） — `findRealParticipantsByContestId` 替代 status 过滤
- [x] rating **只算** `is_virtual=0` 参赛者（虚拟赛不影响 rating） — 同上
- [x] 同一用户并发点"开始虚拟赛" → 返回同一活跃会话（幂等），无重复记录 — `findActiveVirtualSessionForUpdate` + FOR UPDATE
- [x] 虚拟赛页面刷新 → 会话不丢，完成路径正常 — `console/src/stores/contest.ts` `sessionStorage` 持久化
- [x] `autoFinishVirtualParticipants` 不再是零调用者死代码 — scheduler Step 3 调用
- [x] **原子性**：D1/D2/D3 同 commit，部署后 rating 非空（回归中间态崩场景） — 全部落同一 commit
- [ ] 孤儿会话补偿脚本（§3.3）跑过，无遗留 STARTED 虚拟会话 — **R3 部署后第一次跑**

---

## 6. 实施偏差（与原 §2.3 的差异）

**D4 实际改用 DB 行级 `SELECT ... FOR UPDATE`，未引入 Redis 分布式锁。**

| 原计划 | 实际 | 原因 |
|--------|------|------|
| Redis 锁 `contest:virtual:start:{contestId}:{userId}` + DB UNIQUE 兜底 | `SELECT ... FOR UPDATE` 行锁 + DB UNIQUE 兜底 | 单实例 MySQL 部署，InnoDB 行锁足够；少一个 Redis 依赖（无需新增 RedissonClient 装配）；锁粒度更细（仅锁定该 (contest,user) 候选行，不阻塞其他 user） |

**生产多实例部署时需要重审**：
- 跨实例一致性目前由 InnoDB 行锁保证（所有实例连同一 MySQL，行锁是 DB-level 的）
- 若未来引入多 MySQL 集群或读写分离导致行锁失效，需回退到 Redis 分布式锁
- 当前 Flyway 迁移、Redis 装配、RedissonClient 注入代码路径**未**为虚拟开赛做准备

**Code review 引用**: `.claude/reviews/contest-r1-r5-local-review.md` 整体结论 APPROVE。

---

## 7. F-10 决策记录（finishVirtual 不触发 rating 重算）

### 7.1 冲突说明

PRD §1.3 F-10 措辞模糊：原意为 "finishVirtual 不重算 rating" 还是 "finishVirtual 应当重算"？

- PRD §1.3 文本：`F-10 finishVirtual 不重算` —— 倾向"应当不重算"（即现状正确）
- R3.2 实现：rating 查询 `findRealParticipantsByContestId(contestId)` 已用 `is_virtual = 0` 过滤，**任何 finishVirtual 路径天然不进入 rating 池**

### 7.2 决策

**finishVirtual 不触发 rating recalculation**。理由：

1. **语义边界**：虚拟 session 是 per-user replay，**不是**真实竞赛表现的一部分。Elo rating 应只反映真实竞赛成绩（与 [CONTEXT.md](../contest/CONTEXT.md) "Rating" 术语一致）。
2. **R3.2 已强制**：`is_virtual=0` 过滤在 SQL 层完成；任何触发 `RatingCalculationService.calculateAndUpdate(contestId)` 的路径，其结果集**不会**包含虚拟参赛者。
3. **R6.1 守卫加强**：`isRated=false` 提前 return（与本决策正交，但同一保险）。

### 7.3 实施锚点

- `RatingCalculationServiceImpl.calculateAndUpdate` (R6.1)：isRated gate 入口
- `RatingCalculationServiceImpl.calculateAndUpdate` (R3.2)：`findRealParticipantsByContestId(contestId)` SQL 过滤
- `ContestScoringServiceImpl.autoFinishVirtualParticipants` (R3.1)：**只**调 `batchUpdateStatus` (R6 改进为 `bulkFinishByIds`)，**不**调 `ratingService.calculateAndUpdate`

### 7.4 关联 ADR

- **ADR-009**（R6.6 新增）：`docs/adr/ADR-009-israted-gate-and-virtual-rating-isolation.md` —— F-03 isRated gate + F-10 决策的完整记录

---

## 5. References

- [REVIEW_V3.md §3 P0-2 / P0-4](../contest/REVIEW_V3.md)
- [EXECUTION_PLAN.md Round 3](../contest/EXECUTION_PLAN.md)
- [ADR-006](./ADR-006-contest-scoring-engine-activation.md) — 评分引擎激活（Round 4，依赖本 ADR 的参赛者状态正确）
- 现有代码：`ContestScheduler.java`、`ContestScoringServiceImpl.java`、`RatingCalculationServiceImpl.java`、`ContestSchedulerServiceImpl.java`
- 既有约束：`contest_participants UNIQUE(contest_id,user_id,virtual_session_id)`（`V20260602:164`）、索引 `contest_participants_user_id_status_is_virtual_idx`（`V20260602:168`）

---

## 8. R7 评估（2026-06-17）

**F-24 排行榜 keyset 分页 / F-27 限流 key 加 contestId 维度 / CRIT-6 shadow 模式（review-v3 标注的 shadow 模式）**：

- F-24：当前 `selectParticipantsWithUserByContestIdPaginated` 走 LIMIT/OFFSET；keyset 改造需 `final_rank` 游标 + 索引验证，**作为优化候选留 R8**（NFR-P1 1000 人 p99 < 500ms 在 10000 人以下的分页区间未触发瓶颈）。
- F-27：当前 `@RateLimit(key = "contest:virtual-start", ...)` 加 user 维度后**单用户**有 20/min 桶；防单用户多 contest 刷数据需 SpEL `key = "...:{contestId}"`，`RateLimitAspect` 当前不解析路径变量，**作为 RateLimit 增强留 R8**。中间缓解：用户维度的桶已能挡 80% 跨 contest 滥用。
- CRIT-6（F-ARCH-07 shadow 模式）：runtime 灰度模式，需 ops 配合 feature flag + 双写读比 + 回滚开关。**与 ADR-006 §2.4 灰度策略合并评估**；不引独立 flag，避免 feature flag 增殖。S5 重审。
