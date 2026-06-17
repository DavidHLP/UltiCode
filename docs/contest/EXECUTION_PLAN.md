# Contest 模块 P0 多轮执行计划

> **作用**：基于 [REVIEW_V3.md](./REVIEW_V3.md) 的 5 项 P0 + P1 技术债务，给出**可独立部署、可独立回滚**的多轮实施方案。
> **裁决依据**：实际代码状态（非 PLAN.md 的历史设想）。`PLAN.md` 的 HMAC/appSecret 等设想已被代码现实（UUID 方案）取代，本计划**不沿用 PLAN.md 的 Phase 0-9 框架**，从 V3 发现重新推导。
> **创建**：2026-06-17
> **预计 P0 工期**：6.5–8.5 人日 ≈ 1.5–2 周（与 REVIEW_V3 §1 吻合）
> **实施状态**：**R1–R5 全部落地**（2026-06-17）。源码在 main 工作区未提交；详见 [.claude/reviews/contest-r1-r5-local-review.md](../../.claude/reviews/contest-r1-r5-local-review.md)。本计划末尾的 §"实施记录"段落记录实际改动与本计划的偏差。

---

## 0. 设计原则

| 原则 | 含义 |
|------|------|
| **每轮可独立部署** | 任一轮可单独上线、单独回滚，不强制与其他轮打包 |
| **每轮可独立验证** | 有明确的验收用例，不依赖下一轮才能测 |
| **低风险在前** | 纯约束 / 读路径收紧 → 行为变更 → 核心引擎 |
| **耦合集原子上线** | 同一轮内的改动若存在"中间态崩溃"，必须同 commit / 同部署窗口 |
| **迁移仅加性** | Flyway 只新增 `V{timestamp}` 迁移，绝不编辑已应用迁移（见 CLAUDE.md Database Rules） |

### 关键依赖图

```
Round 1 (slug UNIQUE 约束)      ─── 独立，纯 SQL
Round 2 (真/虚读隔离 is_virtual) ─── 独立，读路径收紧；为 R3 隔离语义铺垫
Round 3 (生命周期 + 评级隔离)    ─── 原子（auto-finish ↔ 评级查询改造 必须同轮）
Round 4 (评分引擎激活)           ─── 依赖 R3 的参赛者状态正确（FINISHED 才结算）
Round 5 (P1 工程债务)            ─── 非阻断，合入后迭代
```

**合入门槛**：Round 1–4 全部完成 + 各轮验收通过 = 可重新定档合入。Round 5 不阻断。

---

## Round 1 — 数据库约束硬化（slug UNIQUE）

**目标**：消除 slug 重复导致的 URL/标识歧义（P0-5）。
**风险**：低（纯加性约束，零运行时行为变更）。
**工期**：0.5 人日。

### 改动

| 文件 | 改动 |
|------|------|
| `init-db/migrations/V20260617130000__Contest_Slug_Unique.sql`（新增） | 去重 + `ALTER TABLE contests ADD UNIQUE KEY uk_contest_slug (slug)` |
| `ContestServiceImpl`（创建/更新路径） | 确认已有 `DuplicateKeyException` 捕获 → 转为友好错误码；若无需补 |

### 去重预检（迁移脚本内做，幂等）

```sql
-- V20260617130000__Contest_Slug_Unique.sql
SET NAMES utf8mb4;

-- 1. 预检：找出重复 slug（保留最早创建的，其余追加 -{id前8位}）
UPDATE contests c
JOIN (
  SELECT slug
  FROM contests
  WHERE is_deleted = 0
  GROUP BY slug
  HAVING COUNT(*) > 1
) dup ON c.slug = dup.slug
SET c.slug = CONCAT(c.slug, '-', LEFT(c.id, 8))
WHERE c.id <> (
  SELECT min_id FROM (
    SELECT MIN(id) AS min_id FROM contests c2 WHERE c2.slug = c.slug
  ) t
);

-- 2. 加唯一约束
ALTER TABLE contests ADD UNIQUE KEY uk_contest_slug (slug);
```

> ⚠️ 去重 UPDATE 在 MySQL 上对自引用子查询有限制，**上线前先在 staging 跑 `SELECT slug, COUNT(*) ... HAVING COUNT(*)>1` 确认重复规模**；若重复量大，改用脚本逐条去重后再加约束。

### 验收

- [ ] `./scripts/dev/migrate.sh migrate` 成功（新 MySQL 上 idempotent）
- [ ] 创建重复 slug 的 contest → 返回明确错误（非 500）
- [ ] 历史数据无丢失（去重后 slug 仍可访问）
- [ ] `flyway_schema_history` 新增一行 checksum 正常

### 回滚

- 迁移不可逆（UNIQUE 约束），但可新增 `V{later}__Drop_Contest_Slug_Unique.sql` 回退约束；去重数据变更需评估。

---

## Round 2 — 真实/虚拟参赛者读隔离

**目标**：真实排行榜不混入虚拟参赛者（P0-3），同时保留虚拟参赛者个人页可见性。
**风险**：低–中（读路径行为变更，需回归"我的竞赛"列表）。
**工期**：1 人日。

### 改动

`ContestParticipantMapper.java`（逐方法收紧）：

| 行 | 方法 | 改动 |
|----|------|------|
| `:32` | `findByContestIdOrderByRank` | 加 `AND is_virtual = 0`（真榜刷新） |
| `:271` | `selectParticipantsWithUserByContestId` | 加 `AND cp.is_virtual = 0` |
| `:306` | `selectParticipantsWithUserByContestIdPaginated` | 加 `AND cp.is_virtual = 0` |
| `:175` | `status='FINISHED' leaderboard` | 显式加 `AND is_virtual = 0`（防御性） |
| `:42` | `findByContestIdAndUserId LIMIT 1` | 加 `ORDER BY registered_at DESC`（多虚拟会话时确定返回最新；为 R3 session 稳定铺路） |

**保留不过滤**（这些**应**包含虚拟）：
- `:54 findByUserId`（"我的竞赛"列表，含虚拟赛）
- `:216 findByVirtualSessionId`（虚拟赛个人页）
- 虚拟赛排行榜入口（前端虚拟榜单独查询 `is_virtual = 1`）

### 验收

- [ ] 真实竞赛排行榜**不含** `is_virtual=1` 记录
- [ ] 虚拟参赛者打开自己的竞赛个人页**仍可见**自己的记录
- [ ] "我的竞赛"列表**仍含**虚拟赛记录
- [ ] 虚拟赛排行榜（前端虚拟榜 tab）仍正常

### 回滚

- 纯查询条件还原（git revert mapper 改动）。

---

## Round 3 — 虚拟竞赛生命周期 + 评级隔离（耦合集，原子上线）

**目标**：接通 auto-finish（P0-2）、真实赛结束全员结算、修复评级查询（P0-2）、虚拟开赛并发幂等（P0-4）、前端 session 持久化。
**风险**：中–高（存在"中间态 rating 崩"的耦合，必须原子）。
**工期**：2–3 人日。

### ⚠️ 为什么必须原子

当前 `RatingCalculationServiceImpl:42` 用 `findByContestIdAndStatus(contestId, "STARTED")` 取参赛者——它能"碰巧工作"的唯一原因是**真实参赛者永远是 STARTED**（从无人调 FINISHED 批量更新）。一旦本轮接上 auto-finish（真实/虚拟都会变 FINISHED），这个查询会返回空集 → rating 计算崩溃。

→ **auto-finish 接线** 与 **评级查询改造（status → is_virtual）** 必须同一 commit / 同一部署窗口。

### 改动

#### 3.1 auto-finish 接线（P0-2）
- `ContestScheduler.run()`（`:44`，`@Scheduled(fixedRate=10_000)`）新增 **Step 3**：
  - 调用 `contestScoringService.autoFinishVirtualParticipants()`（已实现于 `ContestScoringServiceImpl:218`，当前零调用者 → 接上）
  - 确认其内部查询 `:157 WHERE cp.is_virtual = 1 AND cp.status = 'STARTED' AND virtual_end_time <= NOW()` 语义正确
- scheduler **Step 2**（RUNNING→FINISHED 竞赛）扩展：竞赛结束时，批量结算该竞赛下 `is_virtual = 0 AND status = 'STARTED'` 的真实参赛者 → `FINISHED`

#### 3.2 评级查询改造（P0-2）
- `RatingCalculationServiceImpl:42`：删除误导性注释 `// P1-4 fix: filter is_virtual = 0`（计划修但没真改的痕迹），落实成真过滤
- 新增 mapper 方法 `findRealParticipantsByContestId(contestId)`：`WHERE contest_id = #{contestId} AND is_virtual = 0`
- 评级查询改用此方法（按 `is_virtual` 而非 `status` 区分真实参赛者）

#### 3.3 虚拟开赛并发幂等（P0-4）
- `ContestSchedulerServiceImpl.startVirtualContest`（`:168-182`）当前只有应用层 `if` + INSERT，无并发原语
- **决策**：允许同一用户对同一竞赛**多次重播**（多个虚拟会话），但同一时刻只允许**一个活跃（STARTED）虚拟会话**
- 实现：开赛前先 `SELECT ... WHERE contest_id=? AND user_id=? AND is_virtual=1 AND status='STARTED'` → 存在则**返回该会话**（幂等），否则新建
- 加 Redis 分布式锁 `contest:virtual:start:{contestId}:{userId}` 防 check-then-act 竞态（短锁，覆盖 SELECT+INSERT）
- 依赖既有 DB 约束 `UNIQUE(contest_id, user_id, virtual_session_id)`（`:164`）兜底

#### 3.4 前端 session 持久化（P0-4）
- `console/src/stores/contest.ts:40`：virtualSession 当前纯内存引用 → 写入 `sessionStorage`（key 含 contestId），刷新后恢复
- 保障 `VirtualContestTimer.vue:79` 的 `/virtual/finish` 完成路径在页面刷新后仍可达

### 验收

- [ ] 虚拟赛到 `virtual_end_time` → 10s 内自动 `FINISHED`（无需用户在线）
- [ ] 真实赛结束 → 所有真实参赛者 `FINISHED`
- [ ] 评级计算返回**非空**真实参赛者集（修复后）
- [ ] rating **只算** `is_virtual=0` 参赛者
- [ ] 同一用户并发点"开始虚拟赛" → 返回同一活跃会话（幂等），无重复记录
- [ ] 虚拟赛页面刷新 → 会话不丢，完成路径正常
- [ ] `autoFinishVirtualParticipants` 不再是零调用者死代码

### 回滚

- 原子回滚（git revert 整个轮次 commit）；评级查询若回滚到 status=STARTED，必须同时回滚 auto-finish，否则 rating 崩。

### 关联 ADR

- [ADR-007 虚拟竞赛生命周期调度与评级隔离](../adr/ADR-007-virtual-contest-lifecycle-and-rating-isolation.md)

---

## Round 4 — 评分引擎激活（核心，最大变更）

**目标**：让评分真正生效（P0-1）——`penaltyPerWrong` 配置化 + SCORE/ICPC/IOI 三模式正确分支。
**风险**：高（评分是竞赛命脉，影响排名/积分）。
**工期**：3–4 人日（含测试）。

### 改动

#### 4.1 penalty 配置化
- `ContestScoringServiceImpl:176`：`int penalty = 20;` →
  ```java
  int penalty = contest.getPenaltyPerWrong() != null
      ? contest.getPenaltyPerWrong()
      : 20; // null 兜底，向后兼容（字段为 Integer 无默认值）
  ```

#### 4.2 评分模式三分支
- 追溯评分入口 `ContestServiceImpl:512 contest.getScoringMode()` → 确认 `ContestScoringMode.{SCORE, ICPC, IOI}` 分支：
  - **ICPC**：罚时 = `错误提交数 × penaltyPerWrong` + AC 耗时（分钟）；排名按解题数降序、罚时升序
  - **IOI**：每题取最高分；排名按总分降序
  - **SCORE**：按题目分值累加（**语义需在 ADR-006 定义**；推荐 = 按 `problem.score` 累加的简化总分模式，区别于 IOI 的"每题最高分"）
- 确认既有保护生效：
  - 重复 AC 保护（`ContestScoringServiceImpl:111-114`）
  - 首杀原子性（`:165-175`，条件更新）
- `final_rank` 计算的 tieBreaker（同分按 AC 时间 / 首杀排序）

#### 4.3 单元测试（强制，竞赛命脉）
- 覆盖矩阵：`{ICPC, IOI, SCORE}` × `{首杀, 重复 AC, 罚时累计, 同分 tiebreak, penaltyPerWrong=null 兜底, penaltyPerWrong=自定义}`
- 对每种模式构造提交序列，断言 `total_score` / `total_penalty` / `final_rank`

### 决策点（已在 ADR-006 定档）

- **历史数据是否复算**：✅ 推荐**不做**历史复算（口径变更可能扰动已结算排名），仅对新提交生效；若需复算，提供可选脚本，默认不跑。
- **penaltyPerWrong null**：✅ 兜底 20（与当前硬编码一致，零行为回归）。

### 验收

- [ ] 三种 scoringMode 的提交序列，排名/罚时/总分符合预期
- [ ] `penaltyPerWrong` 配置值真实生效（非恒为 20）
- [ ] `penaltyPerWrong=null` 时兜底 20，无 NPE
- [ ] 重复提交不重复计 AC；首杀标记正确
- [ ] 同分参赛者 tieBreaker 确定且稳定

### 回滚

- feature flag 控制（若引入 `scoring.engine.v2`）或 git revert；历史数据未复算 → 回滚无数据风险。

### 关联 ADR

- [ADR-006 评分引擎激活：penalty 配置化与 SCORE 分支定义](../adr/ADR-006-contest-scoring-engine-activation.md)

---

## Round 5 — P1 工程债务（非阻断合入）

**目标**：清理技术债务、补测试覆盖。合入后迭代，不阻塞 Round 1–4 的定档。
**风险**：低。**工期**：2–3 人日。

| 项 | 改动 |
|----|------|
| **Feature flags 死代码** | `FeatureFlagsProperties.useNewContestSystem` / `realtimeRankingEnabled` / `anticheatEnabled` 竞赛模块零消耗 → **推荐移除**（YAGNI），或接线 |
| **核心组件测试覆盖** | `ContestDetailView` / `Registration` / `ProblemList` / `ContestTimer` 当前覆盖率为零 → 补关键路径单测 |
| **零调用者清理** | `ContestParticipantMapper:128 findByContestIdAndUserIdAndVirtualSessionId`（若 R3 未用则删） |
| **virtual_session_id 稳定值** | 若 PRD 要求同一用户重播同一竞赛 session 稳定 → 改为 `UUID(contestId+userId)` 派生（需 PRD 确认） |
| **i18n / 边界打磨** | 竞赛相关错误码、空状态文案 |

---

## 验收总表（重新定档 checklist）

对应 [REVIEW_V3.md §9](./REVIEW_V3.md)：

| 轮次 | P0 项 | 验收命令/方法 | 状态 |
|------|-------|--------------|------|
| R1 | P0-5 slug UNIQUE | `migrate.sh migrate` + 创建重复 slug 报错 | ✅ done（`V20260617130000__Contest_Slug_Unique.sql`，业务侧 `DataIntegrityViolationException` 兜底） |
| R2 | P0-3 真榜隔离 | 排行榜查询 `is_virtual=0`；个人页仍可见 | ✅ done（`ContestParticipantMapper` 5 处收紧 + `findByContestIdAndUserId` 加 `ORDER BY registered_at DESC`） |
| R3 | P0-2 auto-finish + 评级；P0-4 并发 | 虚拟赛自动 FINISHED；rating 非空且仅真实；并发幂等 | ✅ done（`ContestScheduler` Step 3 接线 + `findRealParticipantsByContestId` 替代 status 过滤 + `findActiveVirtualSessionForUpdate FOR UPDATE` + 前端 `sessionStorage` 持久化） |
| R4 | P0-1 评分生效 | 三模式单测全绿；penalty 配置生效 | ✅ done（`penaltyPerWrong` 配置化 + SCORE/ICPC/IOI 三分支按 ADR-006 §2.2；4 个新单测） |
| R5 | P1 债务 | feature flags 清理；零调用者清理 | ✅ done（3 个零调用 feature flag 移除 + `findByContestIdAndUserIdAndVirtualSessionId` 移除） |

**R1–R4 全绿 → 可重新定档合入。** ✅ 全部 5 轮完成，等待用户显式批准 commit + push。

---

## 实施记录

执行时的偏差与补充（与原计划的差异）：

| 项 | 原计划 | 实际改动 | 原因 |
|----|--------|----------|------|
| H2 | `catch (DuplicateKeyException)` | 改为 `catch (DataIntegrityViolationException)` | Code review：父类 catch 覆盖 mysql-connector-j 与 MariaDB / 旧 driver 的差异（`ReviewFinding H2`） |
| M2 | `autoFinishVirtualParticipants` N+1 UPDATE | 改为单条 `bulkFinishByIds(ids, now)` IN-list UPDATE | Code review：避免 scheduler 10s tick 下的 N+1（`ReviewFinding M2`） |
| M3 | startVirtualContest 行为变更无文档 | OpenAPI `@Operation.description` 补充 idempotent 语义 | Code review：行为变更需要 API 层可发现（`ReviewFinding M3`） |
| M5 | 测试 per-test mock override 模式脆弱 | 提取 `mockContest()` + `runWrongSubmissionWithContest()` helper | Code review：避免后续测试漏改 mock 导致静默错判（`ReviewFinding M5`） |
| L1 | "Elininates" 拼写 | 修正为 "Eliminates" | Code review：typo |
| L2 | 魔法数字 300 | `private static final int CUSTOM_PENALTY = 300` | Code review：测试可读性 |
| L3 | `VIRTUAL_SESSION_PREFIX` 闭包局部未导出 | 保留闭包，注释中说明 key 形状供未来跨 store 消费 | 设计选择：单 store 使用 closure-local 即可；export 留给未来需要时再做 |
| M1 | （未列在原计划，code review 派生） | **deferred** | `contestMapper.selectById` 多一次查询的优化需要改 submission 模块；独立 PR 处理 |

### Code review 完整结论

详见 [.claude/reviews/contest-r1-r5-local-review.md](../../.claude/reviews/contest-r1-r5-local-review.md)：

- **Decision**: APPROVE（所有 HIGH 已 FIXED；MEDIUM M1 跨模块 deferred；LOW L3 设计选择 deferred）
- **测试**: 33/33 contest 模块单测全绿（含 4 个新 R4 / ADR-006 §4 评分模式测试）
- **预存失败**（非本变更）: `ContestPublicControllerTest$ContestProblemSubmissionsTests` (2 errors) — `WebMvcTest` 缺 `ContestProblemMapper` bean；与本变更无关

### 部署清单（生产前必做）

1. **H1**（[deployment-checklist]）: `./scripts/dev/migrate.sh migrate` 在 staging 跑一次（用生产数据快照），确认无大量重复 slug。
2. `git diff --check` + Conventional commit `<type>(contest): <desc>`（建议 4 个 commit：R1+R5 / R2 / R3 / R4，对应 4 个独立部署窗口）
3. `git push` / merge 需用户显式批准（CLAUDE.md 护栏）
4. R3 部署务必同窗口原子上线（`autoFinishVirtualParticipants` ↔ `findRealParticipantsByContestId` 不可拆分）

---

## 部署顺序建议

1. **R1**（迁移）→ 单独部署，验证约束生效
2. **R2**（读隔离）→ 部署 + 回归"我的竞赛"列表
3. **R3**（生命周期+评级，**原子**）→ 同一部署窗口，重点验证 rating 非空
4. **R4**（评分引擎）→ 部署 + 跑评分单测 + 灰度一个真实竞赛验证
5. **R5**（债务）→ 后续迭代

> 每轮部署前：`git diff --check` + Conventional commit `<type>(contest): <desc>`。`git push` / 合并需用户显式批准（见 CLAUDE.md 护栏）。
