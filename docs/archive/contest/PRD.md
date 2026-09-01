# Contest 模块 PRD 重构建议

> **版本**: v1.0
> **日期**: 2026-06-17
> **作者**: 自动化审计（PM 视角 + 漏洞扫描 + 流程跟踪综合输出）
> **状态**: 草案 · 待产品/技术评审
> **范围**: UltiCode `backend-spring/.../contest/` + `console/src/views/contest/` + `console/src/views/contest/detailed/` + 管理端相关模块

> **📌 验收标准状态说明（2026-06-18）**：本文档 §5 列出的 `[ ]` 验收标准是**原始 spec 基线**（v1.0 草案），状态跟踪已迁移至 **[REVIEW_V3.md](./REVIEW_V3.md)** 与各 R 计划（[EXECUTION_PLAN.md](./EXECUTION_PLAN.md) / [_archive/EXECUTION_PLAN_R{6,7,8,9,10}_*.md](./_archive/)）。R1–R10 落地后当前权威裁决：模块 **v4.2 完结**（详见 [REVIEW_V3 §12](./REVIEW_V3.md)）。PRD §5 的 `[ ]` 保留为产品视角的"应有行为"原始基线，不在 R 计划中逐条改写。

---

## 0. TL;DR

- **现状**: Contest 模块"骨架完成，灵魂缺失"。CRUD/报名/提交/排名/虚拟赛 五大流程都有代码，但 5 大评分字段是死代码、虚拟赛链路完全不可达（F-01）、WS 通道写好不接（F-04）、虚拟数据污染真榜（F-12）、缺题解/Clarification/赛后讨论三大核心 OJ 体验。
- **审计规模**: 49 个 finding（6 CRITICAL / 12 HIGH / 17 MEDIUM / 12 LOW / 2 INFO），覆盖 4 个主题集群（状态机缺失、评分字段未消费、WS 死代码、数据库约束缺失）。
- **核心抓手**: 3 个待拍板的产品决策（P1 虚拟赛定位、P2 评分字段策略、P3 题解季度优先级）。
- **路线图**: 4 个季度。当前季度止血（让虚拟赛可用 + 接 WS + 评分正确性），下季度内容闭环（题解 + Clarification + 封榜），Q3 社交属性，Q4 商业化能力。
- **资源估算**: 当前季度 P0 共 ~16 个 sprint story，约 6-8 周工程投入。

---

## 1. 背景与上下文

### 1.1 业务背景

UltiCode 是一个在线评测（OJ）平台，提供编程题目练习和比赛功能。Contest 模块是平台核心，承担以下价值：

1. **用户粘性**: 比赛是周期性回访用户的最大驱动力（CF/AtCoder 经验）
2. **学习闭环**: 赛后题解 + 排名反馈是用户能力提升的关键路径
3. **品牌建设**: 高质量比赛是平台差异化的核心

### 1.2 技术现状（基于代码审计）

| 维度 | 现状 | 评估 |
|------|------|------|
| 后端 endpoint 数 | 25 个 | ✅ 充足 |
| 后端 entity 数 | 9 个 | ⚠️ 字段定义多但消费少 |
| 后端 service 数 | 5 个 | ⚠️ 职责交叉，ScoringRule 是死代码 |
| 前端 view 数 | 11 个 + 13 个 component + 2 个 composable | ✅ 拆得清晰 |
| Flyway migration 数 | 9+ 个 contest 相关 | ⚠️ slug 无 UNIQUE 索引 |
| 测试覆盖 | 含 ScoringListener/Scheduler/PublicController/Submission 4 套测试 | ⚠️ 缺虚拟赛端到端测试 |
| 集成测试（*IT.java） | 未发现 | ❌ 关键流程无 IT |

### 1.3 49 个 Finding 概览

| 严重度 | 数量 | 主要集群 |
|--------|------|---------|
| **CRITICAL** | 6 | F-01 状态机互斥 / F-02 评分模式死代码 / F-03 isRated 无视 / F-04 WS 死代码 / F-05 autoFinish 无调度 / F-06 提交不读虚拟时间 |
| **HIGH** | 12 | F-07 服务端时间窗缺失 / F-08 成就污染 / F-09 slug 无 UNIQUE / F-10 finishVirtual 不重算 / F-11 虚拟 session 非幂等 / F-12 真榜混入虚拟 / F-13 客户端计时器无 visibilitychange / F-14 session 纯内存 / F-15 TS 类型错配 / F-16 penaltyPerWrong 死字段 / F-17 WS join 不校验注册 / F-18 WS 卸载即丢回调 |
| **MEDIUM** | 17 | 计分类（F-19/20/21/23/24）、并发/状态机（F-22/26/31）、前端（F-28/29/32/33）、其他 |
| **LOW** | 12 | 文档/UX（F-35/38/39/40/41/42）、WS（F-43/44/46/47） |
| **INFO** | 2 | F-48/F-49，与 F-03/F-08 同因 |

### 1.4 竞品对照（详见 7.1）

| 能力 | UltiCode | Codeforces | AtCoder | LeetCode |
|------|----------|------------|---------|----------|
| 可用虚拟赛 | ❌ | ✅ | ✅ | ✅ |
| 题解（官方） | ❌ | ✅ | ✅ | ✅ |
| Clarification | ❌ | ✅ Public Q&A | ✅ 官方答 | ❌ |
| 封榜 | 字段有逻辑无 | ✅ | ✅ | ❌ |
| 队伍赛 | ❌ | ✅ | ✅ | ❌ |
| 防作弊 | ❌ | ✅ Plagiarism | ⚠️ 弱 | ⚠️ 弱 |

---

## 2. 目标与非目标

### 2.1 目标

**G1. 让虚拟赛链路可用**（业务底线，错过比赛的用户能补打）

**G2. 让评分配置真生效**（admin 配 ICPC/IOI 字段不再是无用字段）

**G3. 让实时同步真的实时**（选手切 tab 回来能看到判题结果、看到榜变化）

**G4. 让数据彼此隔离**（虚拟 AC 不污染真榜、不污染成就、不影响 Elo）

**G5. 让用户认知一致**（同一字段在前端的语义全平台统一，例如 "AC" 的边界）

**G6. 补全"赛后"体验**（题解 + Clarification + 赛后讨论三大 OJ 标配）

### 2.2 非目标（本期不做）

- **NG1. 队伍赛**（M7，路线图 Q4）
- **NG2. 防作弊/查重**（M8，路线图 Q4）
- **NG3. 复盘/路径回放**（M10，路线图 Q3）
- **NG4. 重构整个评分引擎**（做增量修复，不重写）
- **NG5. 迁移到事件驱动架构**（基于现有 `@TransactionalEventListener` 渐进演进）
- **NG6. 全平台国际化重构**（仅做必要术语翻译）

---

## 3. 用户画像

| 画像 | 描述 | 关键场景 | 核心痛点 |
|------|------|---------|---------|
| **A. 报名参赛者** | 在校学生 / 竞赛选手 | 浏览 → 报名 → 比赛期间做题 → 看榜 → 赛后看题解 | 切 tab 看不到判题结果；赛中突发公告收不到 |
| **B. 错过比赛的选手** | 想"补打"的活跃用户 | 找 past 比赛 → 开虚拟赛 → 重做 | 当前完全不通（F-01）；即使通了也会被历史 AC 污染（F-50） |
| **C. 出题人/管理员** | 教练 / 平台运营 | 创比赛 → 配题目 / 公告 / 评分规则 → 监控 → 收榜 → 发题解 | 配 ICPC/penaltyPerWrong 完全不生效；赛后发题解无入口 |
| **D. 旁观者** | 路过 / 学习者 | 浏览公开比赛 → 看题 → 学题解 | 无题解、无讨论区；赛后页面"空" |
| **E. 旁观好友** | 同一学校 / 同班 / 同事 | 看好友是否参赛 → 看好友排名 | 无好友榜、无机构榜 |

---

## 4. 用户故事（按主题分组）

### 主题 A：虚拟赛可用性（P0）

| ID | 角色 | 故事 | 优先级 |
|----|------|------|--------|
| US-A1 | B | 作为错过比赛的选手，我能在 past 比赛详情页一键开虚拟赛，并在虚拟赛中正常提交、得到与真实赛一致的计分和判题反馈。 | P0 |
| US-A2 | B | 作为虚拟赛选手，我能看到清晰的虚拟赛状态（VIRTUAL 徽标），不会被历史 contest AC 污染题目显示状态。 | P0 |
| US-A3 | B | 作为虚拟赛选手，我的虚拟 AC 不影响其他真实赛选手的真榜、不影响我的全局 Elo、不影响我的成就计数。 | P0 |
| US-A4 | B | 作为虚拟赛选手，我退出浏览器 / 切 tab 回来后，虚拟会话能正确恢复或正确提示已过期。 | P0 |
| US-A5 | B | 作为虚拟赛选手，即使我系统时钟被修改 / 浏览器节流 setInterval，服务端仍能在 startedAt+durationMinutes 之后拒绝我的提交。 | P0 |

### 主题 B：评分正确性（P0）

| ID | 角色 | 故事 | 优先级 |
|----|------|------|--------|
| US-B1 | C | 作为管理员，我配 `scoringMode=ICPC, tieBreaker=LAST_SOLVE_TIME` 的比赛，选手同分时按末题 AC 时间排序。 | P0 |
| US-B2 | C | 作为管理员，我配 `penaltyPerWrong=600` 的比赛，WA 一次扣 600 秒（按字段生效）。 | P0 |
| US-B3 | C | 作为管理员，我配 `isRated=false` 的友谊赛，比赛结束后选手的全局 Elo 不变化。 | P0 |
| US-B4 | C | 作为管理员，我配 `scoringRuleId` 的规则时，首杀加分按规则值生效（不是硬编码 10）。 | P1 |
| US-B5 | 平台 | 作为平台，缓存击穿可控——某场比赛 1000 人 AC 不导致所有比赛排行榜重算。 | P1 |

### 主题 C：实时同步（P0）

| ID | 角色 | 故事 | 优先级 |
|----|------|------|--------|
| US-C1 | A | 作为比赛中选手，我提交代码后立即看到"判题中"状态，判题完成自动更新为 AC/WA，并刷新该题的图标。 | P0 |
| US-C2 | A | 作为比赛中选手，我在排行榜页能看到 1-2 秒内刷新的排名变化，不需要手动刷新。 | P0 |
| US-C3 | A | 作为比赛中选手，admin 发公告后我能立刻收到提示（无需刷新页面）。 | P0 |
| US-C4 | A | 作为比赛中选手，我切到非比赛页面，回来时仍能收到漏掉的判题结果通知。 | P1 |

### 主题 D：数据库完整性（P0）

| ID | 角色 | 故事 | 优先级 |
|----|------|------|--------|
| US-D1 | 平台 | 作为平台，`contests.slug` 列有 UNIQUE 索引，重名 slug 被 DB 拒绝。 | P0 |
| US-D2 | 平台 | 作为平台，(contest_id, user_id, is_virtual) 有部分唯一索引，杜绝 F-11 多虚拟行。 | P0 |
| US-D3 | 平台 | 作为平台，`contest_problem_results` 隔离真实与虚拟记录（按 `is_virtual` 维度）。 | P1 |
| US-D4 | 平台 | 作为平台，限流 key 包含 contestId 防单用户对多比赛造数据。 | P1 |

### 主题 E：排行榜纯净度（P0）

| ID | 角色 | 故事 | 优先级 |
|----|------|------|--------|
| US-E1 | A | 作为真实赛选手，我的排行榜只显示真实参与者，不混入虚拟行。 | P0 |
| US-E2 | B | 作为虚拟赛选手，我能进入"虚拟赛专属排行榜"看其他虚拟选手的对比。 | P2 |
| US-E3 | C | 作为管理员，我能区分 "final_rank 已计算" 和 "final_rank 未计算" 行（NULL 不出现在排行榜）。 | P0 |

### 主题 F：前端体验一致性（P1）

| ID | 角色 | 故事 | 优先级 |
|----|------|------|--------|
| US-F1 | 所有 | 作为用户，所有倒计时（真实赛 + 虚拟赛 + 提交结果等待）共享同一组件，避免多处实现。 | P1 |
| US-F2 | B | 作为虚拟赛选手，我的虚拟 sessionId 通过 sessionStorage 持久化，刷新不丢失。 | P1 |
| US-F3 | B | 作为虚拟赛选手，我开多个 tab 不会出现两个独立虚拟会话。 | P1 |
| US-F4 | 所有 | 作为用户，状态字段（ContestStatus / SubmissionStatus / ProblemStatus）跨端大小写/语义统一。 | P1 |
| US-F5 | 所有 a11y 用户 | 状态图标有 `aria-label`，色盲/屏幕阅读器可辨识。 | P2 |

### 主题 G：内容闭环（P1）

| ID | 角色 | 故事 | 优先级 |
|----|------|------|--------|
| US-G1 | C | 作为管理员，比赛结束后我能上传/编辑题解，前端赛后页面展示。 | P1 |
| US-G2 | A | 作为比赛选手，我能看官方题解 + 看其他用户的 AC 代码（可设置隐私）。 | P1 |
| US-G3 | A | 作为比赛选手，我对某题有疑问可提交 Clarification（公开/私密），管理员回复后我能收到通知。 | P1 |
| US-G4 | A | 作为比赛选手，`freezeTime` 之后排行榜被冻结（看到的是冻结前的最后状态），解除后我能看到最终榜。 | P1 |

### 主题 H：社交属性（P2）

| ID | 角色 | 故事 | 优先级 |
|----|------|------|--------|
| US-H1 | E | 作为用户，我能看好友/同校参赛者的实时排名。 | P2 |
| US-H2 | 所有 | 作为用户，比赛结束后我能进入赛后讨论区交流思路。 | P2 |
| US-H3 | 所有 | 作为用户，我能订阅某场比赛/某个选手，错过后能收到通知。 | P3 |

### 主题 I：团队与防作弊（P3）

| ID | 角色 | 故事 | 优先级 |
|----|------|------|--------|
| US-I1 | C | 作为管理员，我能创建队伍赛，多人共用一支队伍计分。 | P3 |
| US-I2 | C | 作为管理员，赛后我能导出提交记录 + 跑相似度比对，标记可疑选手。 | P3 |

### 主题 J：复盘与教学（P3）

| ID | 角色 | 故事 | 优先级 |
|----|------|------|--------|
| US-J1 | A | 作为比赛选手，我能点击某题进入"复盘模式"，看到我所有错在哪里、最早 AC 在第几分钟。 | P3 |
| US-J2 | A | 作为学习者，我能看其他选手的 AC 代码（脱敏后），学习思路。 | P3 |

---

## 5. 功能需求（按主题，含验收标准）

### 主题 A：虚拟赛可用性（P0 · 当前季度）

#### A1. 修状态机互斥（F-01）

- **现状**: `submitContestProblem` 要求 `contest.status==RUNNING`；`startVirtualContest` 要求 `contest.status==FINISHED`，两者互斥导致任何虚拟提交都失败。
- **方案**: 在 `submitContestProblem` 加虚拟分支判断：`if (participant.isVirtual && status==STARTED && now < startedAt+durationMinutes) 允许`。
- **验收标准**:
  - [ ] 单元测试：`submitContestProblem` 在 `(contest=FINISHED, participant.isVirtual=true, status=STARTED, now<endsAt)` 时返回 200
  - [ ] 单元测试：在 `now>endsAt` 时返回 409 `CONTEST_ENDED`
  - [ ] E2E：开虚拟赛 → 提交 → 收到判题结果 → 排行榜刷新
  - [ ] 真实赛不受影响：现有所有真实赛单测仍通过

#### A2. 自动 finish 调度（F-05）

- **现状**: `autoFinishVirtualParticipants` 有方法有测试但没有 `@Scheduled` 调用。
- **方案**: 在 `ContestScheduler` 加 `@Scheduled(fixedRate = 60_000)` 调 `contestScoringService.autoFinishVirtualParticipants()`。
- **验收标准**:
  - [ ] 集成测试：开虚拟赛 → 等 60s + duration → 行 status 自动变 FINISHED
  - [ ] 多次重入不重复处理（idempotent）
  - [ ] WS 推 `ContestStatusEvent.ENDED` 到 `/user/queue/notification`

#### A3. 服务端时间窗校验（F-07）

- **现状**: `submitContestProblem` 和 `applyJudgeResult` 都不读 `participant.startedAt+durationMinutes`。
- **方案**: 在 `applyJudgeResult` 加虚拟窗守卫。
- **验收标准**:
  - [ ] 单元测试：超过 endsAt 后调用 `/api/submissions` 仍能创建，但 `applyJudgeResult` 抛错或不累加
  - [ ] 前端 `/api/submissions` 公共端点对 `contestId` 注入做白名单

#### A4. 虚拟会话作用域隔离（F-50/51/52）

- **现状**: `findSubmissionsByContestProblemAndUser` 查所有 `contest_submissions` 行，导致历史真实赛 AC 在虚拟赛中显示为 ✓。
- **方案**: 引入 `participantId / virtualSessionId` 维度，虚拟赛中只查当前 session 的提交。
- **验收标准**:
  - [ ] mapper SQL 增加 `AND cs.virtual_session_id = #{sessionId}` 当虚拟模式
  - [ ] 前端通过 `X-Virtual-Session-Id` header 透传
  - [ ] 单元测试：用户先真实赛 AC 题 A，再开虚拟赛，虚拟赛中题 A 显示为 todo
  - [ ] 当前虚拟赛 AC 后立即显示 ✓（不延迟到下次刷新）

#### A5. 虚拟 session 幂等（F-11）

- **现状**: `virtualSessionId = UUID.randomUUID()` 每次新发，导致 `(contest, user, virtual_session_id)` UK 失效。
- **方案**: 改 `virtualSessionId = SHA1(contest_id || user_id)`，加 `(contest_id, user_id, is_virtual)` 部分唯一索引。
- **验收标准**:
  - [ ] 单元测试：20 并发 `POST /virtual/start` 只产生 1 行
  - [ ] DB migration：ALTER TABLE 加 `(contest_id, user_id, is_virtual)` UNIQUE 索引
  - [ ] 已有重复行需先 dedupe（写脚本）

### 主题 B：评分正确性（P0 · 当前季度）

#### B1. 实现 ICPC / IOI 评分分支（F-02）

- **现状**: `scoringMode` 字段定义但 `ContestScoringServiceImpl` 不读。
- **方案**: 在 `applyJudgeResult` 加 switch：
  - ICPC: `(solved desc, totalPenalty asc, lastSolveTime asc)`
  - IOI: 用 `contest_problem_results.score` 部分分
  - SCORE: 纯 totalScore
- **验收标准**:
  - [ ] 单元测试覆盖三种模式
  - [ ] 真实赛 + 单元测试全通过

#### B2. 实现 tieBreaker（F-02）

- **方案**: `RatingCalculationServiceImpl` 的排序读取 `contest.getTieBreaker()`。
- **验收标准**:
  - [ ] 覆盖 LAST_SOLVE_TIME / TOTAL_TIME / TOTAL_ATTEMPTS / NONE 四种

#### B3. penaltyPerWrong 生效（F-16）

- **现状**: `applyJudgeResult` 硬编码 `int penalty = 20`。
- **方案**: 改为 `int penalty = contest.getPenaltyPerWrong() != null ? contest.getPenaltyPerWrong() * 60 : 1200`（秒）。
- **验收标准**:
  - [ ] 单元测试：penaltyPerWrong=600 → WA 后 AC 累加 600s

#### B4. isRated 守卫（F-03）

- **现状**: `transitionToFinished` 无视 `isRated`。
- **方案**: `if (Boolean.TRUE.equals(contest.getIsRated())) ratingService.calculateAndUpdate(contestId)`。
- **验收标准**:
  - [ ] 单元测试：isRated=false 时 final_rank 写但 Elo 不变
  - [ ] 单元测试：isRated=true 时 final_rank + Elo 都更新

#### B5. 成就系统排除虚拟 AC（F-08）

- **现状**: `SubmissionServiceImpl.java:333-349` `countAcceptedProblemsByUserId` 不排除 `virtual_session_id`。
- **方案**: 加 `if (contestSubmission != null && contestSubmission.getVirtualSessionId() != null) 跳过成就`。
- **验收标准**:
  - [ ] 单元测试：虚拟 AC 不累加到 "已解决题目" 计数

#### B6. 缓存击穿防护（F-21）

- **现状**: `cacheManager.getCache("contestRanking").clear()` 每次 AC 全清。
- **方案**: cache key 按 contestId 拆分；或去抖 1s 合并 evict。
- **验收标准**:
  - [ ] 性能测试：1000 AC/min 期间排行榜响应 < 500ms p99

### 主题 C：实时同步（P0 · 当前季度）

#### C1. useContestSocket 真正接入（F-04/13/17/18/29/33）

- **现状**: `useContestSocket.ts` 实现完整但 0 处实例化。
- **方案**:
  - `ContestDetailView` 调用 `useContestSocket().joinContest(contestId)` 后订阅 `ranking/firstSolve/announcement/status`
  - `ContestWebSocketHandler.handleJoinContest` 加 `participantMapper.existsByUserAndContest` 校验
- **验收标准**:
  - [ ] 单元测试：WS 加入需要 participant 校验，未注册的 join 拒绝
  - [ ] E2E：发公告 → ContestDetailView 收到实时通知
  - [ ] E2E：AC → ContestProblemList 图标刷新（F-33 修）

#### C2. 全局判题结果订阅（F-18）

- **方案**: `App.vue` 维持全局 `onSubmissionResult` → Pinia `pendingSubmissions` 队列。
- **验收标准**:
  - [ ] 单元测试：离开 SubmissionsView 提交，结果回来 → Pinia 暂存 → 回 SubmissionsView 展示

#### C3. 客户端计时器 visibilitychange（F-13）

- **方案**: `VirtualContestTimer` + `ContestTimer` 都加 `document.addEventListener('visibilitychange', () => updateTimer())`。
- **验收标准**:
  - [ ] 单元测试：tab 隐藏 5 分钟回来时 remaining 显示正确

### 主题 D：数据库完整性（P0 · 当前季度）

#### D1. slug UNIQUE 索引（F-09）

- **方案**: 新增 migration `ALTER TABLE contests ADD UNIQUE KEY uk_contests_slug (slug)`。
- **前置**: 写 dedupe 脚本，先 dedupe 重复行（保留最早一条）。
- **验收标准**:
  - [ ] migration 在 dev / staging / prod 都能 dry-run 通过
  - [ ] 测试：插入重复 slug 被 DB 拒绝

#### D2. (contest_id, user_id, is_virtual) UNIQUE（F-11，见 A5）

### 主题 E：排行榜纯净度（P0 · 当前季度）

#### E1. 排行榜过滤虚拟行（F-12/19）

- **现状**: `RankingServiceImpl.getContestRanking/getLiveRanking` 不加 `is_virtual=0`。
- **方案**: SQL 加 `AND cp.is_virtual = 0`。
- **验收标准**:
  - [ ] 单元测试：混合真实 + 虚拟行 → 真榜不含虚拟
  - [ ] 虚拟榜通过单独端点 `/contest/{id}/virtual-leaderboard`

### 主题 F：前端体验一致性（P1 · 下季度）

#### F1. 倒计时组件统一（F-14）

- **方案**: 合并 `ContestTimer.vue` + `VirtualContestTimer.vue` 为一个组件 + `mode: 'real' | 'virtual'` prop。

#### F2. virtualSession 持久化（F-14）

- **方案**: `sessionStorage.setItem('virtual-contest-${contestId}', JSON.stringify({sessionId, endsAt}))`。
- **验证**: Pinia 插件 rehydrate。

#### F3. 多 tab 互锁（F-28）

- **方案**: `BroadcastChannel('virtual-contest')` 跨 tab 广播 start/finish。

#### F4. TS 类型统一（F-15/30）

- **方案**: 接口中 `status: string` + `isActive: boolean` 必填；后端改用大写枚举。

#### F5. a11y 标注（F41）

- **方案**: 状态图标加 `aria-label`。

### 主题 G：内容闭环（P1 · 下季度）

#### G1. 题解（Editorial）

- **后端**: 新增 `editorial` 表（id, contest_id, problem_id, content_md, created_by, created_at），仅 admin 写。
- **前端**: 赛后页面 `ContestDetailView` + `ProblemDetailView` 加 "Editorial" tab。
- **验收标准**:
  - [ ] admin 在管理端能上传题解
  - [ ] 比赛结束前题解不可见（根据 contest.endTime 判断）
  - [ ] 用户赛后看题解有"发布时间"标识

#### G2. Clarification

- **后端**: `clarifications` 表（id, contest_id, problem_id, user_id, content_md, reply_md, is_public, created_at, replied_at, replied_by）。
- **前端**: 题目页"提问"按钮；admin 端"回复"按钮；公开问题所有参赛者可见。
- **验收标准**:
  - [ ] 选手提问 → admin 回复 → 选手收到通知
  - [ ] 公开问题所有参赛者实时看到（WS）
  - [ ] 私密问题仅 admin + 提问者可见

#### G3. 封榜（Scoreboard Freeze）

- **后端**: `getContestRanking` 在 `freezeTime < now < endTime` 时不返回冻结后的提交影响。
- **验收标准**:
  - [ ] 单元测试：freezeTime 后排行榜不变
  - [ ] endTime 后排行榜更新到最终状态

### 主题 H：社交属性（P2 · 再下季度）

#### H1. 好友榜 / 同校榜

- 后端加 `friends` 关系表；`getContestRanking` 加 `?filter=friends|school` 参数。

#### H2. 赛后讨论区

- 复用现有 `forum` 模块；admin 创建"赛后讨论"专区。

### 主题 I：团队与防作弊（P3）

仅作为未来能力占位，本期不做。

---

## 6. 非功能需求

### 6.1 性能

- **NFR-P1**: 单场比赛 1000 人 AC 同时发生，排行榜响应 < 500ms p99（F-21）
- **NFR-P2**: 启动期冷启动（`up.sh` + `pm2 start`）到 9001 就绪 < 90s
- **NFR-P3**: 提交响应（`POST /problems/{pid}/submissions`）< 200ms（不计判题）

### 6.2 安全

- **NFR-S1**: 所有特权接口（admin contest CRUD / 题解上传 / 公告发布）需 `@PreAuthorize` + CSRF（沿用项目约定）
- **NFR-S2**: WS 鉴权沿用 `JwtChannelInterceptor`，**不**支持 query token / URL token
- **NFR-S3**: 限流 key 必须包含 contestId 维度（防单用户对多比赛造数据 F-27）

### 6.3 可用性

- **NFR-A1**: 关键端点（list/detail/ranking）可用率 99.9%
- **NFR-A2**: 后端 `ContestScheduler` 失效应有降级：手动 admin 触发 / 重试机制

### 6.4 可维护性

- **NFR-M1**: 评分相关字段（`scoringMode/tieBreaker/penaltyPerWrong/isRated/ScoringRule`）必须有 contract test 覆盖
- **NFR-M2**: `ContestStatus` 枚举大小写、字段命名、TS 类型必须有 lint 规则保护
- **NFR-M3**: 所有 P0 finding 必须有自动化回归测试（F-01/05/11/12 等）

### 6.5 可观测性

- **NFR-O1**: 关键事件（startVirtual / submitContestProblem / finishContest）打 log 含 `contestId, userId, traceId`
- **NFR-O2**: 评分失败/扣分异常必须 warn log

---

## 7. 季度路线图（Sprint 计划）

### Q1（当前季度 · 8 周）— 止血

**核心目标**: 让虚拟赛可用 + 评分正确性 + 排行榜纯净 + 实时同步

| Sprint | 主题 | Stories |
|--------|------|---------|
| S1 (1-2 周) | 虚拟赛可用性 | A1 (F-01 状态机)、A5 (F-11 唯一索引)、A2 (F-05 autoFinish) |
| S2 (1-2 周) | 虚拟赛数据隔离 | A3 (F-07 时间窗)、A4 (F-50/51/52 作用域) |
| S3 (1-2 周) | 评分正确性 | B1 (F-02 ICPC/IOI)、B2 (F-02 tieBreaker)、B3 (F-16 penalty)、B4 (F-03 isRated)、B5 (F-08 成就) |
| S4 (1-2 周) | 排行榜 + 实时 + DB | D1 (F-09 slug UK)、E1 (F-12/19 过滤)、C1 (F-04/13/17/18 WS 接通)、B6 (F-21 缓存) |

**Sprint S1 验收**: 能完整跑通虚拟赛 happy path（开 → 提交 → AC → 计时归零 → finish → 看到虚拟榜）
**Sprint S4 验收**: 49 finding 中所有 CRITICAL + HIGH 关闭

### Q2（下季度 · 8 周）— 内容闭环

| Sprint | 主题 | Stories |
|--------|------|---------|
| S5 (1-2 周) | 题解 Editorial | G1 全套 |
| S6 (1-2 周) | Clarification | G2 全套 |
| S7 (1-2 周) | 封榜 | G3 全套 |
| S8 (1-2 周) | 前端体验 | F1 (倒计时统一)、F2 (sessionStorage)、F3 (BroadcastChannel)、F4 (类型统一)、F5 (a11y) |

**Sprint S8 验收**: 49 finding 全部关闭

### Q3（再下季度 · 8 周）— 社交属性

| Sprint | 主题 | Stories |
|--------|------|---------|
| S9 | 好友榜 | H1 全套 |
| S10 | 赛后讨论 | H2 全套 |
| S11 | 复盘 | J1 全套 |
| S12 | AC 代码展示 | J2 全套 |

### Q4（未来）— 商业化能力

| Sprint | 主题 | Stories |
|--------|------|---------|
| S13-S14 | 队伍赛 | I1 |
| S15-S16 | 防作弊 | I2 |

---

## 8. 验收标准（按主题）

### 主题 A 综合验收

- [ ] 单元测试 + 集成测试 + E2E 全覆盖
- [ ] 端到端：在 linked-list-special 真实跑一遍虚拟赛开 → 提交 → AC → 完成 → 看到虚拟榜
- [ ] linked-list-special seed 状态改为 FINISHED（如果产品决定虚拟赛可用）
- [ ] F-01/05/07/11/50/51/52 全部关闭

### 主题 B 综合验收

- [ ] admin 配 ICPC + penaltyPerWrong=600 + isRated=false 三种比赛，行为符合配置
- [ ] F-02/03/08/16/20 全部关闭

### 主题 C 综合验收

- [ ] 模拟 5 个 tab + 100 个并发 AC，排行榜在 2s 内刷新到所有 tab
- [ ] 选手离开 SubmissionsView 后回来看提交历史，能看到完整判题结果
- [ ] F-04/13/17/18/29/33 全部关闭

### 主题 D 综合验收

- [ ] DB 重复 slug 写入被拒绝
- [ ] DB 重复 (contest, user, is_virtual) 写入被拒绝

### 主题 E 综合验收

- [ ] 真榜页面不出现虚拟行
- [ ] 虚拟榜通过独立端点可见
- [ ] F-12/19 关闭

### 主题 F 综合验收

- [ ] 真实赛 / 虚拟赛倒计时共用一个组件
- [ ] sessionStorage 持久化生效
- [ ] 多 tab 同时开虚拟赛被拒绝（只能一个）
- [ ] a11y 评测 100% 通过

---

## 9. 成功指标

### 9.1 业务指标

| 指标 | 当前 | Q1 目标 | Q2 目标 | Q4 目标 |
|------|------|---------|---------|---------|
| 虚拟赛日活 | 0（不可用） | 50+ | 200+ | 500+ |
| 比赛后 7 日留存 | n/a | +20% | +50% | +100% |
| 题解查看率 | 0 | — | 60% | 80% |
| 比赛中 WS 推送接收率 | 0 | 95% | 99% | 99.9% |

### 9.2 技术指标

| 指标 | 当前 | Q1 目标 |
|------|------|---------|
| Contest 相关 bug 数（周均） | 5+ | <1 |
| Contest 模块测试覆盖率 | ~30% | 80% |
| Contest 模块 *IT 测试数 | 0 | 5+ |
| 49 finding 关闭率 | 0% | 100% |

---

## 10. 不在本期范围内

参见 §2.2 非目标（NG1-NG6）。

特别说明：

- **队伍赛 (M7/I1)**: Q4 启动，需要单独架构设计
- **防作弊 (M8/I2)**: 需要法务 + 算法双重评估，启动时间待定
- **复盘模式 (J1)**: Q3，依赖虚拟赛可用性先完成

---

## 11. 待决问题（产品决策）

### P1. 虚拟赛的产品定位

| 选项 | 含义 | 推荐 | 影响 |
|------|------|------|------|
| **甲**：虚拟赛 = 真赛副本（数据完全隔离） | 独立表 / 独立榜 / 不污染真数据 | ⭐⭐⭐⭐⭐ | A1-A5 + E1 全部需求 |
| **乙**：虚拟赛 = 纯练习模式（不计分不上榜） | 提交但不计分 | ⭐⭐⭐ | 减少一半实现 |
| **丙**：保持现状 | — | ❌ | 永远不修 |

**默认推荐甲**。如选乙则简化 A1/E1/B5 实现。

### P2. 评分字段策略

| 选项 | 含义 | 推荐 | 影响 |
|------|------|------|------|
| **甲**：实现真正的 ICPC/IOI/tieBreaker/penaltyPerWrong/isRated/ScoringRule | 全部生效 | ⭐⭐⭐⭐ | B1-B5 全部需求 |
| **乙**：删除字段，文档化"仅支持 SCORE 模式 + 固定 penalty 20min + 全 isRated=true" | 减法 | ⭐⭐⭐⭐⭐ | 大幅减少代码复杂度 |
| **丙**：保持现状（字段是死代码） | — | ❌ | 永久 tech debt |

**默认推荐甲**。如团队人手紧或选乙作为"先减法后加法"路线，可分两步：先乙减字段，再择机实现甲。

### P3. 题解 + Clarification 优先级

| 选项 | 含义 | 影响 |
|------|------|------|
| **甲**：下季度 P0 | 排进 Q2 S5-S6 | 与封榜同步交付 |
| **乙**：P1 | 资源允许时做 | 可能推到 Q3 |
| **丙**：不做 | 平台定位偏评测不偏学习 | — |

**默认推荐甲**。OJ 行业惯例 + 用户反馈高频项。

### P4. linked-list-special seed 状态

| 选项 | 含义 | 影响 |
|------|------|------|
| **甲**：改为 FINISHED（允许开虚拟） | 需要修复 F-35 | 利于 E2E 测试 |
| **乙**：保持 UPCOMING（不允许开虚拟） | 文档化 | 维持现状 |
| **丙**：删除该 slug | — | 测试数据丢失 |

**默认推荐甲**。便于 E2E 测试 + 真实复现。

### P5. 是否允许 `CANCELLED` 比赛开虚拟（F-38）

| 选项 | 含义 |
|------|------|
| **甲**：不允许（当前） | 维护"虚拟 = 真赛副本"语义 |
| **乙**：允许 admin 覆盖 | 灵活但需 admin override 端点 |

**默认推荐甲**。

---

## 12. 附录 A：Finding → Requirement 映射表

| Finding | 严重度 | 主题 | Sprint | 需求 ID |
|---------|--------|------|--------|---------|
| F-01 | CRITICAL | A 虚拟赛可用性 | S1 | A1 |
| F-02 | CRITICAL | B 评分正确性 | S3 | B1, B2 |
| F-03 | CRITICAL | B 评分正确性 | S3 | B4 |
| F-04 | CRITICAL | C 实时同步 | S4 | C1 |
| F-05 | CRITICAL | A 虚拟赛可用性 | S1 | A2 |
| F-06 | CRITICAL | A 虚拟赛可用性 | S2 | A1 + A3 |
| F-07 | HIGH | A 虚拟赛可用性 | S2 | A3 |
| F-08 | HIGH | B 评分正确性 | S3 | B5 |
| F-09 | HIGH | D 数据库完整性 | S4 | D1 |
| F-10 | HIGH | E 排行榜纯净度 | S3 | E1 |
| F-11 | HIGH | A 虚拟赛可用性 | S1 | A5 |
| F-12 | HIGH | E 排行榜纯净度 | S4 | E1 |
| F-13 | HIGH | C 实时同步 | S4 | C1, C3 |
| F-14 | HIGH | F 前端一致性 | S8 | F2 |
| F-15 | HIGH | F 前端一致性 | S8 | F4 |
| F-16 | HIGH | B 评分正确性 | S3 | B3 |
| F-17 | HIGH | C 实时同步 | S4 | C1 |
| F-18 | HIGH | C 实时同步 | S4 | C2 |
| F-19 | MEDIUM | E 排行榜纯净度 | S4 | E1 |
| F-20 | MEDIUM | B 评分正确性 | S3 | B4 (rule) |
| F-21 | MEDIUM | B 评分正确性 | S4 | B6 |
| F-22 | MEDIUM | A 虚拟赛可用性 | S2 | A4 |
| F-23 | MEDIUM | B 评分正确性 | S3 | B5 (related) |
| F-24 | MEDIUM | D 数据库完整性 | S3 | D3 |
| F-25 | MEDIUM | A 虚拟赛可用性 | S1 | A2 (related) |
| F-26 | MEDIUM | E 排行榜纯净度 | S3 | E1 |
| F-27 | MEDIUM | D 数据库完整性 | S4 | D4 |
| F-28 | MEDIUM | F 前端一致性 | S8 | F3 |
| F-29 | MEDIUM | C 实时同步 | S4 | C1 |
| F-30 | MEDIUM | F 前端一致性 | S8 | F4 |
| F-31 | MEDIUM | A 虚拟赛可用性 | S2 | A4 |
| F-32 | MEDIUM | F 前端一致性 | S8 | F1 |
| F-33 | MEDIUM | C 实时同步 | S4 | C1 |
| F-34 | MEDIUM | E 排行榜纯净度 | S3 | E1 |
| F-35 | LOW | D 数据库完整性 | S1 | P4 决策 |
| F-36 | LOW | A 虚拟赛可用性 | S2 | A1 (related) |
| F-37 | LOW | A 虚拟赛可用性 | S2 | A4 |
| F-38 | LOW | A 虚拟赛可用性 | — | P5 决策 |
| F-39 | LOW | F 前端一致性 | S8 | F4 |
| F-40 | LOW | F 前端一致性 | S8 | F4 |
| F-41 | LOW | F 前端一致性 | S8 | F5 |
| F-42 | LOW | F 前端一致性 | S8 | F1 |
| F-43 | LOW | C 实时同步 | S4 | C1 |
| F-44 | LOW | C 实时同步 | S4 | C1 |
| F-45 | LOW | — | — | 文档化 |
| F-46 | LOW | F 前端一致性 | S8 | F3 |
| F-47 | LOW | C 实时同步 | S4 | C1 |
| F-48 | INFO | B 评分正确性 | S3 | B4 (重复) |
| F-49 | INFO | B 评分正确性 | S3 | B5 (重复) |
| F-50 | NEW (MEDIUM) | A 虚拟赛可用性 | S2 | A4 |
| F-51 | NEW (MEDIUM) | A 虚拟赛可用性 | S2 | A4 |
| F-52 | NEW (MEDIUM) | A 虚拟赛可用性 | S2 | A4 |

---

## 13. 附录 B：风险登记册

| ID | 风险 | 概率 | 影响 | 缓解 |
|----|------|------|------|------|
| R1 | F-01 修复改动大，影响真实赛既有代码 | 中 | 高 | 单元测试覆盖 + 灰度发布 |
| R2 | WS 接通后端性能压力 | 中 | 中 | 限流 + 消息合并 + 单连接多 topic |
| R3 | slug UNIQUE migration 需要 dedupe | 低 | 中 | 写 dedupe 脚本 + dry-run |
| R4 | Q1 排期满，挤占下季度功能 | 高 | 中 | 优先 P0，P1 推 Q2 |
| R5 | 题解审核工作流缺失 | 中 | 中 | Q2 S5 时设计审核流 |
| R6 | Clarification 公开/私密权限控制易错 | 中 | 高 | 写专门的权限 contract test |
| R7 | 多 tab BroadcastChannel 在某些浏览器不支持 | 低 | 低 | fallback 到 localStorage 心跳 |
| R8 | F-50 作用域修复改变真实赛用户体验（如果改 SQL） | 中 | 高 | 真实赛模式保留原 SQL，虚拟模式才加 session 过滤 |

---

## 14. 附录 C：关键代码定位速查

| 关注点 | 路径 | 行号 |
|--------|------|------|
| `startVirtualContest` | `backend-spring/.../contest/service/impl/ContestSchedulerServiceImpl.java` | 161-186 |
| `submitContestProblem` | `backend-spring/.../contest/service/impl/ContestServiceImpl.java` | 239-260 |
| `finishVirtualContest` | `backend-spring/.../contest/service/impl/ContestSchedulerServiceImpl.java` | 215-243 |
| `applyJudgeResult` | `backend-spring/.../contest/service/impl/ContestScoringServiceImpl.java` | 63-200 |
| `autoFinishVirtualParticipants` | `backend-spring/.../contest/service/impl/ContestScoringServiceImpl.java` | 218-230 |
| `ContestScoringListener` | `backend-spring/.../contest/listener/ContestScoringListener.java` | 整体 |
| `calculateAndUpdate` | `backend-spring/.../contest/service/impl/RatingCalculationServiceImpl.java` | 36-127 |
| `transitionToFinished` | `backend-spring/.../contest/scheduler/ContestScheduler.java` | 218-240 |
| `getContestRanking/getLiveRanking` | `backend-spring/.../contest/service/impl/RankingServiceImpl.java` | 37-80 |
| `findByContestIdAndUserId` | `backend-spring/.../contest/mapper/ContestParticipantMapper.java` | 42-46 |
| `findSubmissionsByContestProblemAndUser` | `backend-spring/.../contest/mapper/ContestSubmissionMapper.java` | 33-41 |
| `useContestSocket` (前端) | `console/src/composables/contest/useContestSocket.ts` | 整体 |
| `VirtualContestTimer` | `console/src/views/contest/components/VirtualContestTimer.vue` | 整体 |
| `ContestProblemList` | `console/src/views/contest/detailed/components/ContestProblemList.vue` | 整体 |
| `contestStore` virtualSession | `console/src/stores/contest.ts` | 40, 240-272 |
| `loadProblemStatuses` | `console/src/views/contest/detailed/ContestDetailView.vue` | 61-87 |
| `ContestWebSocketHandler` join | `backend-spring/.../websocket/contest/ContestWebSocketHandler.java` | 整体 |
| `RealtimeService` 推送 | `backend-spring/.../websocket/service/RealtimeService.java` | 70, 89, 110, 126, 167-194 |
| Seed `linked-list-special` | `init-db/migrations/V20260604120000__Seed_Contests_Test_Data.sql` | 170-193 |
| `contests.slug` 索引（非唯一） | `init-db/migrations/V20260602_120000__Create_All_Tables.sql` | 310 |
| 限流 key | `backend-spring/.../common/aspect/RateLimitAspect.java` | 63-80 |
| SubmissionServiceImpl 成就 | `backend-spring/.../submission/service/impl/SubmissionServiceImpl.java` | 333-349 |

---

## 15. 附录 D：术语表

| 术语 | 含义 |
|------|------|
| **Contest** | 平台上的编程比赛，分 UPCOMING/RUNNING/FINISHED/CANCELLED 四态 |
| **Virtual Contest** | 已结束比赛的"补打"模式，独立 session、相同题面、隔离计分 |
| **isVirtual** | `ContestParticipant.isVirtual` 标志位，true 表示该行由虚拟赛产生 |
| **virtualSessionId** | UUID 标识一个虚拟会话（当前实现非幂等，PRD 要求改为 `SHA1(contest_id\|\|user_id)`） |
| **ContestParticipantStatus** | REGISTERED / STARTED / FINISHED / DISQUALIFIED |
| **scoringMode** | SCORE（总分制）/ ICPC（题数+罚时）/ IOI（部分分） |
| **tieBreaker** | LAST_SOLVE_TIME / TOTAL_TIME / TOTAL_ATTEMPTS / NONE |
| **penaltyPerWrong** | ICPC 模式下 WA 一次扣多少秒 |
| **isRated** | 该比赛是否计入全局 Elo |
| **freezeTime** | 排行榜冻结时间（ICPC 末段防冲榜惯例） |
| **firstSolveBonus** | 首杀奖励分数（当前硬编码 10，PRD 要求从 ScoringRule 读取） |
| **applyJudgeResult** | 评分服务入口，`@TransactionalEventListener(AFTER_COMMIT)` 触发 |
| **autoFinishVirtualParticipants** | 自动 finish 过期虚拟会话（当前无调度，PRD 要求 Q1 S1 加） |
| **Editorial** | 官方题解，赛后展示 |
| **Clarification** | 赛中选手向 admin 提问 |
| **final_rank** | 真实榜排名，由 RatingCalculationService 计算，虚拟行被过滤永远 NULL |

---

## 16. 附录 E：流程总览图

### 虚拟赛 happy path（A1-A5 全部修完后）

```
[用户进入 /contest/linked-list-special]
  └─ ContestDetailView onMounted
     ├─ GET /contest/{id}                          (查询比赛信息)
     ├─ GET /contest/{id}/virtual/session           (查询是否已有虚拟 session)
     └─ GET /contest/{id}/problems                  (查询题目)

[用户点击 "Start Virtual"]
  └─ POST /contest/{id}/virtual/start               (限流 20/60s + contestId 维度)
     ├─ 校验 contest.status == FINISHED
     ├─ 校验 SHA1(contest_id||user_id) 唯一         (D2)
     ├─ 写 contest_participants 行
     └─ 返回 ParticipationStatusDTO { sessionId, startedAt, endsAt }

[前端 Pinia store 更新 + sessionStorage 持久化]
  └─ VirtualContestTimer 启动 setInterval(1000) + visibilitychange 监听

[用户进题目 + 提交]
  └─ POST /contest/{id}/problems/{pid}/submissions
     ├─ 校验: (contest.status==RUNNING)
     │        OR (participant.isVirtual && status==STARTED && now<endsAt)  ← A1
     └─ 写 submissions + contest_submissions 行

[异步判题]
  └─ SubmissionJudgedEvent
     └─ ContestScoringListener AFTER_COMMIT
        └─ ContestScoringServiceImpl.applyJudgeResult
           ├─ 校验虚拟窗: now < endsAt                                      ← A3
           ├─ 按 scoringMode 分支计算                                       ← B1
           ├─ 按 penaltyPerWrong 加 penalty                                 ← B3
           ├─ 按 contest_problem_results 幂等
           ├─ 跳过成就系统（虚拟 AC）                                        ← B5
           └─ 清 contest-ranking 缓存（按 contestId key）                    ← B6

[WS 推送]
  └─ SubmissionResultPayload → /user/queue/submission
     └─ ContestDetailView onSubmissionResult → loadProblemStatuses 重跑     ← C1/F-50

[用户计时归零]
  └─ VirtualContestTimer 触发 → POST /contest/{id}/virtual/finish
     └─ finishVirtualContest: 仅翻转 status                                 ← E1 决策虚拟不上榜

[或：autoFinishVirtualParticipants 调度 60s tick]                          ← A2
  └─ 找 expiresAt < now 的虚拟行 → 翻转 status → WS 推 ContestStatusEvent.ENDED
```

---

## 17. 文档元信息

- **依赖**:
  - `docs/AGENTS.md`（仓库级权威指南）
  - `docs/SECURITY_REVIEW_2026-06-06.md`（变更鉴权/CSRF/WS 前必读）
  - `.claude/rules/database/01-flyway-migrations.md`（迁移规范）
  - `.claude/rules/backend/`（后端编码规则）
- **下一步**: 产品/技术评审 → 拍板 P1-P5 → 拆分 Sprint S1 任务 → 进入实施
- **维护**: 本文档随实现进度更新，状态变更需在 §0 TL;DR 标注

---

> **致评审者**：本文档同时承担"产品 PRD"和"技术改造路线图"双重角色。如产品/技术决策出现分歧，请优先明确 §11 决策项，再回填到对应章节。所有 finding 关闭需在 sprint review 时勾选附录 A 表格的对应行。
