---
title: Contest（比赛）
tags: [entity, contest, scoring, rating, virtual, cancel]
status: living
updated: 2026-06-21
owner: backend
sources:
  - adr/0006-contest-scoring-activation.md
  - adr/0007-virtual-contest-rating-isolation.md
  - adr/0009-israted-gate.md
  - adr/0010-cancel-state-virtual-replay.md
  - CODEMAPS/backend.md
  - CODEMAPS/data.md
  - CODEMAPS/architecture.md
aliases: [比赛, 编程竞赛]
---

# Contest（比赛）

## 概述

contest 是 UltiCode 中用户参加的编程比赛实体。一场比赛在时间窗内聚集一组题目（`contest_problems`），参赛者（`contest_participants`）提交作答（`contest_submissions`），由评分引擎按规则计算排名与等级（rating）。系统区分两类比赛：

- **真实赛（real contest）**：`contests.kind = 'REAL'`，按既定时间表推进 `UPCOMING → LIVE → FINISHED`（或 `CANCELLED`），可影响参赛者的 `global_rankings` 与 `rating_titles`。
- **虚拟赛（virtual contest）**：`contests.kind = 'VIRTUAL'`，用户按需回放过去的真实赛（同题、同时间窗、隔离等级）。虚拟赛的提交被钉到一行 `virtual_contest_sessions`，并链接到父 `real_contest_id`。

比赛的"是否计等级"由 `contests.is_rated` 门控，"如何评分"由 `scoring_rule_id` 引用的规则决定，二者共同决定了排名引擎与 rating job 的行为。

## 架构视角

### 数据模型

`contests` 表的关键字段（综合 CODEMAPS/data.md 与各 ADR）：

| 字段 | 作用 | 来源 |
|------|------|------|
| `kind` | 区分 `'REAL'` / `'VIRTUAL'` | ADR-0007 |
| `status` | 生命周期状态：`UPCOMING` / `LIVE` / `FINISHED` / `CANCELLED` | ADR-0006 / ADR-0010 |
| `scoring_rule_id` | 引用 `scoring_rules` 的一行；`LIVE` 前必须设好 | ADR-0006 |
| `is_rated` | 布尔门控，决定是否更新 `global_rankings` | ADR-0009 |
| `real_contest_id` | 虚拟赛指向的父真实赛；带唯一约束（一个虚拟赛有且仅有一个父） | ADR-0007 |
| `slug` | 比赛唯一短标识（V20260617130000 加入） | CODEMAPS/data.md |
| `cancel_reason` / `cancelled_at` | 取消时必填 | ADR-0010 |

相关表（CODEMAPS/data.md § "Contests"）：`contest_problems` · `contest_participants` · `contest_participant_status` · `contest_submissions` · `contest_problem_results` · `contest_announcements` · `scoring_rules` · `virtual_contest_sessions` · `first_solve_records` · `global_rankings` · `rating_titles`。

> 注：ADR-0009 提到评分 job 在比赛 `FINISHED` 时把 `is_rated` 快照写入 `contest_rating_snapshot` 表；该表名未出现在 CODEMAPS/data.md 的"Contests"表清单中（清单只列了 `global_rankings` / `rating_titles`）。`contest_rating_snapshot` 是否实际建表待核实（见下文"矛盾与未决"）。

### 模块与服务

比赛模块位于 `backend-spring/.../modules/contest/`，挂载三条路由：`/contest`、`/admin/contest`、`/admin/scoring-rules`（CODEMAPS/backend.md）。关键 service 构成完整的比赛流水线：

- `ContestService` — 比赛主逻辑；与 `virtual_contest_sessions` 的 join 在此（ADR-0007）
- `ContestScoringService` — 规则驱动的排名计算；消费 `isRated`（ADR-0006 / ADR-0009）
- `ScoringRuleService` — `scoring_rules` CRUD
- `RankingService` — 排名视图；同样 join `virtual_contest_sessions`（ADR-0007）
- `RatingCalculationService` — 等级计算；总读比赛启动时钉死的规则（ADR-0006 / ADR-0009）
- `ContestSchedulerService` — 后台 worker，推进 `UPCOMING → LIVE → FINISHED` 的定时状态转换（CODEMAPS/backend.md § "Background Workers"），并处理取消态转换（ADR-0010）

### 实时与前端

- **实时推送**：console 的 `useContestSocket` 订阅 STOMP `/topic/contest/{id}`，由后端 `ContestWebSocketService` 在 `contest_submissions` 与排名 diff 时推送，前端不轮询（CODEMAPS/architecture.md § "Data Flow"）。
- **console（:9002）**：路由 `/contest` 下含 browse/list/my/rankings/detailed/:id；store `contest` + `contest/rankingStore`；含 `ContestStatusBadge`、`ContestTimer`、`VirtualContestTimer` 等组件（CODEMAPS/frontend.md）。
- **management（:9003）**：路由 `/contests` + `/contests/wizard/*`（CRUD + 分步向导 StepProblems / StepReview）；store `admin/contests`；typed API wrapper `api/admin/contests` + `api/admin/scoring-rules`（CODEMAPS/frontend.md）。

### 评分与等级流程时序

1. 比赛创建时引用一个 `scoring_rule_id`（默认规则覆盖老比赛）；`LIVE` 前必须设好，赛中改规则被拒（ADR-0006）。
2. `ContestSchedulerService` 推进 `UPCOMING → LIVE → FINISHED`（CODEMAPS/backend.md）。
3. `FINISHED` 时评分 job 先写入 `is_rated` 快照（ADR-0009 的顺序契约）。
4. `ContestScoringService` 按比赛启动时钉死的规则解析排名；`RatingCalculationService` 计算等级。
5. `global_rankings` / `first_solve_records` 的更新触发器按"快照里的 `is_rated` + 非虚拟回放 + 非 CANCELLED"三重过滤后落地（ADR-0007 / ADR-0009 / ADR-0010）。

## 决策记录

- [[0006-contest-scoring-activation|ADR-0006]] — 评分引擎改为规则驱动：比赛引用 `scoring_rule_id`，`LIVE` 前必须设好、赛中不可改、`RatingCalculationService` 总读启动时钉死的规则。激活标志与校验由 `V20260617120000__Contest_Scoring_Hardening` 落地。
- [[0007-virtual-contest-rating-isolation|ADR-0007]] — 虚拟赛用 `kind = 'VIRTUAL'` + `virtual_contest_sessions` 隔离；`global_rankings` / `first_solve_records` 的触发器以 `virtual_contest_sessions.id IS NULL` 过滤，保证虚拟回放不污染真实等级。`V20260617140000__Contest_Real_Unique_And_Session_Length` 加 `real_contest_id` 唯一约束与 session 时长检查（1h ≤ 时长 ≤ 24h）。
- [[0009-israted-gate|ADR-0009]] — `contests.is_rated` 布尔门控：评分触发器从比赛 `FINISHED` 时的快照读取（不再 join `contests` 当前行，避免 LIVE→FINISHED 间列变化）；虚拟回放即使父赛计等级也不更新等级。新增 `contest_rating_snapshot` 表，FINISHED 时的 job 必须在评分 job 前写入（顺序契约）。
- [[0010-cancel-state-virtual-replay|ADR-0010]] — 取消是一等状态 `status = 'CANCELLED'`，必带 `cancel_reason` / `cancelled_at`；取消赛永不更新 `global_rankings` / `first_solve_records`；其虚拟回放被禁用（`virtual_contest_sessions.disabled_reason = 'PARENT_CANCELLED'`）；通知账本为每个参赛者发一条取消 intent（复用 [[0004-notification-intents-ledger|ADR-0004]]）。状态转换在 `{UPCOMING, LIVE}` 允许，在 `{FINISHED, 已 CANCELLED}` 拒绝。

## 矛盾与未决

> [!warning] `contest_rating_snapshot` 表存在性存疑
> ADR-0009 明确"新增 `contest_rating_snapshot` 表"作为 `is_rated` 门控的实现载体，但 [[data]]（CODEMAPS/data.md）§ "Contests" 表清单未列出该表，Flyway Migration Index（34 条）也未显式标注一个 `contest_rating_snapshot` 的迁移条目。可能该表由现有迁移顺带创建而未在索引中单列，也可能 ADR-0009 的实现尚未完全落地。**待核实**：在 `init-db/migrations/` 中 grep `contest_rating_snapshot` 确认是否真有建表脚本。

## 参考

- 代码：`backend-spring/.../modules/contest/`（`ContestService`、`ContestScoringService`、`ContestSchedulerService`、`RankingService`、`RatingCalculationService`、`ScoringRuleService`）；前端 console `/contest/*`、management `/contests/*`
- 迁移：`init-db/migrations/V20260617120000__Contest_Scoring_Hardening.sql`、`V20260617130000__Contest_Slug_Unique.sql`、`V20260617140000__Contest_Real_Unique_And_Session_Length.sql`
- 相关：[[virtual-contest]]、[[submission]]、[[exactly-once]]
