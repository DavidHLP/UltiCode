# Contest 模块 R6 多轮执行计划 — 剩余 11 项修复

> **作用**：基于 `REVIEW_V3.md §9` + `PRD.md §1.3` 中 R1-R5 未关闭的 9 项 finding + 2 项"待复核"，给出**可独立部署、可独立回滚**的多轮实施方案。
> **裁决依据**：R1-R5 已落地 9 项（slug UNIQUE / 真榜隔离 / 评分引擎 / 虚拟生命周期 / 并发幂等 / rating isolation / 前端 sessionStorage / 死代码清理），本计划覆盖剩余全部未关闭项。
> **创建**：2026-06-17
> **预计 R6 工期**：8–13 人日（与 PRD §6 Sprint S2-S4 残量吻合）
> **不沿用** R1-R5 的 Round 编号；R6 用更细粒度（按原子性 / 风险隔离），落地后 PR review 可整组合并。

---

## 0. 范围盘点（来自 PRD §1.3 + REVIEW_V3）

| 编号 | 严重度 | finding | R1-R5 状态 | R6 归属 |
|------|--------|---------|-----------|--------|
| F-01 | CRITICAL | 状态机互斥（virtual session） | ⚠️ R3 部分覆盖（RACE 防重），未专门审计状态机 gate | **R6.2** |
| F-03 | CRITICAL | isRated 守卫缺失 | ❌ 完全未修 | **R6.1** |
| F-04 | CRITICAL | WS 死代码（`useContestSocket.joinContest` 零调用） | ❌ 完全未修 | **R6.4** |
| F-06 | CRITICAL | 提交不读虚拟时间 | ⚠️ 待复核 | **R6.2** |
| F-07 | HIGH | 服务端时间窗缺失（虚拟赛无硬截止） | ❌ 完全未修 | **R6.2** |
| F-08 | HIGH | 成就污染（虚拟 AC 计入成就） | ❌ 完全未修 | **R6.3** |
| F-10 | HIGH | finishVirtual 不重算 | ⚠️ 与 ADR-007 冲突 | **R6.6 ADR** |
| F-13 | HIGH | 客户端计时器 `visibilitychange` | ❌ 完全未修 | **R6.4** |
| F-15 | HIGH | TS 类型错配（VirtualContestStatus） | ❌ 完全未修 | **R6.5** |
| F-17 | HIGH | WS join 不校验注册 | ❌ 完全未修 | **R6.4** |
| F-18 | HIGH | WS 卸载即丢回调 | ❌ 完全未修 | **R6.4** |

外加历史债：
- CRIT-2 (DB-2 NULL 多行无强约束) 🟡 未修
- CRIT-3 (DB-3 varchar(40) UUID 兼容) 🟡 未修
- CRIT-6 (F-ARCH-07 shadow) 🟡 未修

R6 不收 CRIT-2/3/6（**R7 候选**），与 PRD §6 Sprint 安排一致。

---

## 1. 设计原则

| 原则 | 含义 |
|------|------|
| **每轮可独立部署** | R6.x 顺序无强依赖；除 R6.4 ↔ R6.6 文档（WS 接通影响 review checklist）外，可任意 shuffle |
| **每轮可独立回滚** | 回滚 R6.4 不影响 R6.1 / R6.3 |
| **安全优先** | F-17 WS 鉴权与 F-04 实时一起做（同一文件区域） |
| **审计先于修复** | F-01 / F-06 标"待复核"，R6.2 第一步是审计，必要时再修 |

---

<a id="round-61--评分正确性收尾f-03--f-10-决策"></a>
## 2. Round 6.1 — 评分正确性收尾（F-03 + F-10 决策）

**目标**：关闭 F-03 isRated 守卫；明确 F-10 与 ADR-007 的冲突决策。
**风险**：低（一行代码 + 一段 ADR 文字）。
**工期**：0.5–1 人日。

### 改动

#### 2.1 F-03 isRated gate（一行）
- `RatingCalculationServiceImpl.calculateAndUpdate(contestId)` 入口加：
  ```java
  Contest contest = contestMapper.selectById(contestId);
  if (contest == null || !Boolean.TRUE.equals(contest.getIsRated())) {
      log.info("R6.1: contest {} isRated=false, skip rating update", contestId);
      return;
  }
  ```
- 这与 R3.2 已加的 `contestMapper.selectById` 同一个查询，**零额外查询**。
- 测试：`RatingCalculationServiceImplTest` 加 `calculateAndUpdate_isRatedFalse_skipsUpdate`（mock `contest.isRated=false`，verify `updateRating` never called）。

#### 2.2 F-10 决策记录（不修，文档化）
- **冲突说明**：
  - PRD §A3 `B4 isRated 守卫` 与 ADR-007 §2.2 都明确**虚拟 session 不影响 rating**。
  - PRD `F-10 finishVirtual 不重算` 措辞模糊，可读作"finishVirtual 触发时是否重算 rating"——按 plan 决策**不重算**（虚拟 session 终态不影响真实榜）。
- 行动：在 ADR-007 追加 §7 "F-10 决策记录"：明确 finishVirtual 不会触发 rating recalculation（与 R3.2 `findRealParticipantsByContestId(contestId)` 的 `is_virtual=0` 过滤一致）。

### 验收

- [ ] `contest.isRated=false` 时 `ratingService.calculateAndUpdate` no-op（log + return）
- [ ] `contest.isRated=true` 时行为不变（R1-R5 回归测试全绿）
- [ ] ADR-007 §7 新增 F-10 决策段落，引用 PRD §1.3 F-10

### 回滚
- 一行 gate 注释掉；ADR 段落可保留（无副作用）。

---

<a id="round-62--状态机--虚拟时间审计--服务端时间窗-f-01--f-06--f-07"></a>
## 3. Round 6.2 — 状态机 / 虚拟时间审计 + 服务端时间窗（F-01 + F-06 + F-07）

**目标**：先把 F-01 / F-06 的"待复核"项真正审计落地；然后补 F-07 虚拟赛硬截止。
**风险**：中（可能涉及新增审计发现的修复；与 R3 的 FOR UPDATE 互补）。
**工期**：1–2 人日（审计 0.5，修复 1.5）。

### 改动

#### 3.1 F-01 状态机互斥审计
- 审计对象：
  - `ContestParticipantStatus` 状态机（REGISTERED → STARTED → FINISHED）的所有 transition 点
  - `ContestStatus` 状态机（UPCOMING → RUNNING → FINISHED）的所有 transition 点
  - virtual session 跨状态机（virtual 创建 / virtual 结束）是否破坏不变量
- 审计产出：`docs/contest/F-01-STATE_MACHINE_AUDIT.md`，列出：
  - 所有 transition 入口
  - 是否每个 transition 都有状态前置断言 / SQL 条件保护
  - 发现的任何 invariant violation
- **如发现 violation**：新增迁移 / mapper 方法 / service 校验；按问题规模决定是否独立 commit

#### 3.2 F-06 提交读虚拟时间审计
- 审计对象：`ContestServiceImpl.submitContestProblem` 中 `cs.timeFromStart` 的写入路径（`ContestSubmissionMapper.insert`）
- 检查项：
  - 真实赛：`timeFromStart` 应为 `(now - contest.actual_start_time)` 秒数
  - 虚拟赛：`timeFromStart` 应为 `(now - virtual_participant.started_at)` 秒数（**虚拟时钟**）
- **如发现 violation**：修正写入路径，audit doc 记录

#### 3.3 F-07 服务端时间窗校验
- 真实赛：`ContestServiceImpl.submitContestProblem:246` 已校验 `endTime`
- 虚拟赛新增校验：在 `submitContestProblem` 中，根据 `participant.isVirtual` 分支：
  - 真实 → `now > contest.endTime` 拒绝
  - 虚拟 → `now > virtualEndTime (participant.started_at + contest.duration_minutes)` 拒绝
- 新增 mapper 方法 `findVirtualEndTime(participantId)` 或在 participant 上加 `virtual_end_time` 列

### 验收

- [ ] F-01 审计 doc 落地（即使无 finding 也是有价值的记录）
- [ ] F-06 审计 doc + 修正（如有）
- [ ] F-07 单元测试：虚拟赛到 `started_at + duration` 后提交被拒；真实赛到 `endTime` 后提交被拒
- [ ] 虚拟赛 AC 后服务端时间窗外提交返回 `CONTEST_ENDED` 409

### 回滚
- 时间窗校验注释掉（保留为文档化约束）；审计 doc 永久保留

---

## 4. Round 6.3 — 成就模块 is_virtual 过滤（F-08）

**目标**：虚拟 AC 不计入成就系统。
**风险**：低（数据层过滤 + 单元测试）。
**工期**：0.5 人日。

### 改动

- 审计 `achievement` 模块所有路径，找出哪些 achievement 触发条件读 `submission` / `contest_problem_results`
- 对每个读点，添加 `is_virtual = 0` 过滤（与 R2/R3 一致）
- 单元测试：虚拟 AC 触发 0 个新成就；真实 AC 触发正常成就

### 验收

- [ ] 虚拟赛 100% AC 完成后不发放任何新成就
- [ ] 真实赛 AC 仍正常触发成就
- [ ] 历史已发放成就（若 is_virtual=1 误触发）回滚脚本（运营可选）

### 回滚
- 注释掉过滤（不推荐；F-08 是 PRD 强制项）

---

<a id="round-64--ws-全栈f-04--f-13--f-17--f-18"></a>
## 5. Round 6.4 — 实时同步全栈（最大块，F-04 + F-13 + F-17 + F-18）

**目标**：接通 `useContestSocket` + 排行榜实时推流 + WS 鉴权 + 卸载清理。
**风险**：中–高（跨前后端，WebSocket 是新引入的运行时依赖；F-17 是安全问题）。
**工期**：3–5 人日。

### 改动

#### 5.1 F-04 WS 接入（C1 主体）
- `console/src/views/contest/ContestDetailView.vue` 等关键视图调 `useContestSocket().joinContest(contestId)`
- `useContestSocket` 修复：
  - 建立 STOMP 连接时携带 HttpOnly `access_token` cookie（CLAUDE.md 强调：WebSocket 鉴权**只接受** `access_token` cookie）
  - 订阅 `/topic/contest/{id}/ranking` → 推到 Pinia store
  - 重连 / 心跳
- 新增 backend `WebSocketAuthChannelInterceptor`（CLAUDE.md 已有 WebSocketAuthInterceptor 雏形）：校验 STOMP CONNECT 时携带 cookie token

#### 5.2 F-17 WS join 鉴权（**安全**）
- 拦截器：用户 STOMP 订阅 `/topic/contest/{id}/ranking` 时校验：
  - token 有效
  - 用户已注册该 contest（REGISTERED / STARTED / FINISHED 之一）
  - `is_virtual` flag 决定能否订阅虚拟赛 channel
- 失败 → STOMP ERROR frame，连接关闭

#### 5.3 F-13 visibilitychange（C3）
- `console/src/components/contest/VirtualContestTimer.vue`：
  - 监听 `document.visibilitychange` 事件
  - 切到 hidden：记录 `pausedAt = Date.now()`
  - 切回 visible：若 `pausedAt` 距 `endsAt` 已过，自动 `/virtual/finish`；否则把 `endsAt` 后延 `Date.now() - pausedAt`（不消耗暂停时间）
  - 服务端仍按 `started_at + duration_minutes` 硬截止（与 R6.2 的 F-07 互为补充）

#### 5.4 F-18 WS 卸载清理
- `useContestSocket` 暴露 `dispose()`：组件 `onUnmounted` 调用
  - `disconnect()` STOMP
  - 清空订阅 / 计时器
  - 移除 store 监听
- `RealtimeService` 内部维护 `Map<contestId, subscriptionCount>` 引用计数 → 最后一个用户离开时主动关闭 WS

### 验收

- [ ] 打开 contest 详情 → 实时排行榜推送到达
- [ ] 第二个用户提交 → 第一个用户 1s 内看到分数变化
- [ ] 未报名用户 STOMP 订阅 → 连接被拒（403-style error frame）
- [ ] 切后台 30s 回前台 → 计时器后延，未过期
- [ ] 切后台 1h 回前台 → 计时器自动结束，调用 `/virtual/finish`
- [ ] 关闭标签页 → 后端 subscription 计数递减，连接正常关闭

### 回滚
- WS 接入：保留 `useContestSocket` 但 views 不调 → 实时功能降级为轮询（与 R5 前一致）
- F-17 鉴权：注释掉（**不推荐，安全降级**）

---

## 6. Round 6.5 — TS 类型统一 + 历史债（F-15 + CRIT-2/3/6）

**目标**：跨端 DTO enum 对齐；收口历史 schema 债。
**风险**：低（数据层可加可加；前端 enum 化是渐进式）。
**工期**：1–2 人日。

### 改动

#### 6.1 F-15 TS enum 化（CLAUDE.md 优先项）
- 后端 DTO enum 化：`ParticipationStatusDTO.action` 等 `String` → `enum`（CLAUDE.md 标注"优先推进后端 enum 化"）
- 前端 `VirtualContestStatus` 等 enum 与后端对齐（删 `console/src/stores/contest.ts:73-80` 的 `as string` 兜底）
- `cross-stack-dto-granularity-alignment` skill 审计两端

#### 6.2 CRIT-2 NULL 多行约束
- 加 `is_virtual = 0` 部分唯一索引（MySQL 8 不直接支持 partial index；用 generated column + unique index）
  ```sql
  ALTER TABLE contest_participants
    ADD COLUMN is_real_active TINYINT GENERATED ALWAYS AS (
      CASE WHEN is_virtual = 0 AND status = 'STARTED' THEN 1 ELSE NULL END
    ) VIRTUAL;
  ALTER TABLE contest_participants ADD UNIQUE KEY uk_real_active (contest_id, user_id, is_real_active);
  ```
- 业务层 catch `DataIntegrityViolationException`（同 H2 模式）

#### 6.3 CRIT-3 varchar(40) 扩到 64
- 迁移：`ALTER TABLE contest_participants MODIFY virtual_session_id VARCHAR(64) NOT NULL;`
- 兼容性：当前 UUID 36 字符 < 64 字符无破坏

#### 6.4 CRIT-6 shadow 模式
- 不引（`V20260617120000__Contest_Scoring_Hardening.sql` 已加索引；shadow 是运行时模式，需 ops 配合）
- 在 ADR-007 注释：shadow 模式留待 S5 评估

### 验收

- [ ] 前端 `contest store` 不再 `as string`（grep 验证）
- [ ] 后端 DTO enum 字段类型 `String` → enum 类型（selectively；不改 `scoringMode` 这种已有 enum 的）
- [ ] CRIT-2：业务层 addParticipant 时如果同一 user 已 STARTED 真实参赛 → 409
- [ ] CRIT-3：schema 同步

### 回滚
- CRIT-2 generated column 移除（迁移新加 `V{later}` 即可回退）

---

## 7. Round 6.6 — 文档 + ADR 收口

**目标**：把 R6 决策沉淀为 ADR / 文档。
**风险**：低。
**工期**：0.5–1 人日。

### 改动

#### 7.1 新增 ADR
- `docs/adr/ADR-008-websocket-auth-and-realtime-push.md`：F-04 / F-17 / F-18 决策（STOMP cookie auth + 引用计数清理 + 鉴权失败 STOMP ERROR 语义）
- `docs/adr/ADR-009-israted-gate-and-virtual-rating-isolation.md`：F-03 / F-10 决策（isRated=false 跳过 rating；finishVirtual 不重算；语义边界）

#### 7.2 更新现有文档
- `docs/contest/REVIEW_V3.md §9`：R6 6 个 P0 关闭（F-03 / F-04 / F-07 / F-08 / F-13 / F-17）
- `docs/contest/EXECUTION_PLAN.md §"实施记录"`：append R6 实施记录
- `docs/contest/EXECUTION_PLAN_R6.md`：标"已实施"并归档到 `docs/contest/completed/`
- `docs/contest/CONTEXT.md`：新增"Virtual End Time" / "Realtime Channel" 术语（如需要）

#### 7.3 audit doc
- `docs/contest/F-01-STATE_MACHINE_AUDIT.md`（R6.2 产出）合并到 ADR-008 或独立保留

### 验收

- [ ] ADR-008 / ADR-009 落地（Accepted 状态）
- [ ] REVIEW_V3 §9 全部 P0 关闭（F-01 待审计结论由 F-01 审计 doc 引用）
- [ ] EXECUTION_PLAN_R6.md 标 done

---

## 8. 部署顺序建议

1. **R6.1**（一行业务代码 + ADR 文字）→ 独立部署
2. **R6.2**（审计 + 时间窗）→ 部署 + 跑 F-07 单元测试
3. **R6.3**（成就隔离）→ 部署 + 跑成就触发回归
4. **R6.4**（WS 全栈）→ 部署 + 灰度 1 个 contest 验证实时推送 + 鉴权
5. **R6.5**（TS enum + 历史债）→ 跨端部署 + 全量测试
6. **R6.6**（文档）→ 最后

> **耦合集**：
> - R6.1 ↔ R6.6：ADR-009 的 F-10 决策须在 R6.6 文档化（不影响运行）
> - R6.2 ↔ R6.3：F-08 成就隔离可能依赖 F-06 修复（成就触发读 `contest_problem_results`）
> - R6.4 内部：F-04 / F-13 / F-17 / F-18 须同一 commit（同一文件区域）

---

## 9. 验收总表（R6 重新定档 checklist）

| Round | Finding | 验收命令/方法 | 估时 |
|-------|---------|--------------|------|
| R6.1 | F-03, F-10 | isRated=false noop + ADR-007 §7 | 0.5–1 |
| R6.2 | F-01, F-06, F-07 | 审计 doc + 虚拟赛时间窗测试 | 1–2 |
| R6.3 | F-08 | 虚拟 AC 不触发成就测试 | 0.5 |
| R6.4 | F-04, F-13, F-17, F-18 | 实时推流 + 鉴权 + 卸载测试 | 3–5 |
| R6.5 | F-15, CRIT-2/3/6 | grep `as string` + generated column 迁移 | 1–2 |
| R6.6 | 文档 | ADR-008/009 + REVIEW_V3 §9 关闭 | 0.5–1 |
| **合计** | **11 finding + 文档** | | **6.5–11.5 人日** |

**R6.1–R6.6 全绿 → REVIEW_V3 §9 全部 P0 关闭，可最终定档合入。**

---

## 10. 与历史报告的关系

- `REVIEW.md` / `REVIEW_V2.md` / `SECURITY_REVIEW.md` / `FINDINGS_RAW.md` 作为 v1/v2 历史证据保留；本计划是 v3 → v4 收口的"修复执行"
- `REVIEW_V3.md` 仍是事实裁决的最终权威（待 v3.2 更新）
- R6 完成后所有 CRITICAL + HIGH 关闭，Sprint S1-S4 全部签收

> 每轮部署前：`git diff --check` + Conventional commit `<type>(contest): <desc>`。`git push` / 合并需用户显式批准。
