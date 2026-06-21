---
title: Virtual Contest（虚拟比赛）
tags: [concept, contest, virtual, rating, cancel]
status: living
updated: 2026-06-21
owner: backend
sources:
  - adr/0006-contest-scoring-activation.md
  - adr/0007-virtual-contest-rating-isolation.md
  - adr/0009-israted-gate.md
  - adr/0010-cancel-state-virtual-replay.md
  - CODEMAPS/data.md
aliases: [虚拟比赛, 虚拟参赛]
---

# Virtual Contest（虚拟比赛）

## 概述

**virtual contest（虚拟比赛 / 虚拟参赛）** 是 UltiCode 让用户在正式比赛结束后按原题集「按需回放」的参赛模式：用户以同一组题目、同样的时间窗进行一场隔离的练习，体验接近真实赛。它的核心张力是**评级隔离** —— 虚拟回放绝不能污染真实用户的正式 rating 与排名。

在数据上，一场虚拟赛是 `contests.kind = 'VIRTUAL'` 的记录，其提交被钉到一行 `virtual_contest_sessions`，并通过 `real_contest_id` 链接到唯一父真实赛。评级 / first_solve / global_rankings 的更新路径以多层门控显式排除虚拟回放，这是这一概念的设计要害。

## 架构视角

### 涉及模块

- **contest 模块** —— `backend-spring/.../modules/contest/`，包括 `ContestService`（处理虚拟 session 的 join 逻辑）、`RatingCalculationService`（评级计算）、`ContestSchedulerService`（取消态转换）、`RankingService`（排名）。评分 job 必须同时读 `contests` 与 `virtual_contest_sessions`，一个小 join，不是热路径（[[0007-virtual-contest-rating-isolation|ADR-0007]]）。

### 触发条件

- **用户发起虚拟参加** —— 用户对一个已 `FINISHED` 的真实赛发起虚拟回放，系统创建一行 `virtual_contest_sessions`、挂上 `real_contest_id`，并在与父赛相同的题集和时间窗内推进该用户的提交与排名（[[0007-virtual-contest-rating-isolation|ADR-0007]]）。
- **父赛被取消** —— 取消是独立于 `FINISHED` 的一等状态；父赛一旦 `CANCELLED`，其虚拟回放必须被禁用（见下「不变量」），并通过 [[0004-notification-intents-ledger|ADR-0004]] 的通知账本向参赛者广播（[[0010-cancel-state-virtual-replay|ADR-0010]]）。

### 不变量

虚拟赛的合法性由四条相互配合的不变量支撑：

1. **`virtual_contest_sessions` 钉住提交 + 链接父赛** —— 虚拟赛的提交被钉到一行 `virtual_contest_sessions`，该行通过 `real_contest_id` 指向父真实赛。`real_contest_id` 上带唯一约束：**一个虚拟赛必须有且仅有一个父真实赛**（[[0007-virtual-contest-rating-isolation|ADR-0007]]；[[data]] § "Contests"）。
2. **评级隔离（rating isolation）** —— 只有满足「父赛 `status = FINISHED` **且** `contests.is_rated = true` **且** 该 session **不是**虚拟回放」三个条件，才更新 `global_rankings`。更新 `global_rankings` 与 `first_solve_records` 的触发器都以 `virtual_contest_sessions.id IS NULL` 过滤，从而天然排除虚拟跑（[[0007-virtual-contest-rating-isolation|ADR-0007]]）。
3. **`isRated` 门控 + FINISHED 快照** —— `contests.is_rated` 是布尔门控，由评分 job 求值；默认真实赛为 `true`，训练轮 / 练习赛 / 内部娱乐赛为 `false`。关键约束：**评分触发器从比赛 FINISHED 时的快照读 `is_rated`**，不是通过 join 读当前 `contests` 行 —— 因为比赛列在 LIVE 与 FINISHED 之间可能变化。快照写入 `contest_rating_snapshot` 表，是评分 job 跑之前必须完成的顺序契约（[[0009-israted-gate|ADR-0009]]）。
4. **取消态可回放边界** —— 取消（`status = 'CANCELLED'`，必带 `cancel_reason` / `cancelled_at`）是一等状态，**永不**更新 `global_rankings` / `first_solve_records`。取消比赛的虚拟回放被禁用：`virtual_contest_sessions.disabled_reason = 'PARENT_CANCELLED'`。状态转换在 `{UPCOMING, LIVE}` 中允许进入 `CANCELLED`，在 `{FINISHED, 已 CANCELLED}` 中不允许（[[0010-cancel-state-virtual-replay|ADR-0010]]）。

> 虚拟赛还带一条 session 时长合理性检查：1h ≤ 时长 ≤ 24h（[[0007-virtual-contest-rating-isolation|ADR-0007]]，迁移 `V20260617140000__Contest_Real_Unique_And_Session_Length.sql`）。

## 决策记录

- [[0006-contest-scoring-activation|ADR-0006]] — 评分引擎激活：`scoring_rule_id` 在 `LIVE` 前设好、启动时钉死，是真实赛与虚拟赛共用排名规则的基础设施。
- [[0007-virtual-contest-rating-isolation|ADR-0007]] — 虚拟赛生命周期 + 等级隔离：`virtual_contest_sessions` / `real_contest_id` / `IS NULL` 触发器过滤，虚拟回放不挪动真实用户 rating。
- [[0009-israted-gate|ADR-0009]] — `isRated` 门控 + 虚拟等级隔离：布尔门控、FINISHED 时快照读、虚拟回放双重门控。
- [[0010-cancel-state-virtual-replay|ADR-0010]] — 取消态 + 虚拟回放边界：取消是一等状态、取消赛永不更新 rating、虚拟回放以 `PARENT_CANCELLED` 禁用。

## 矛盾与未决

- **真实用户档案出现虚拟赛带来的 rating 变动 = SEV-2** —— [[0007-virtual-contest-rating-isolation|ADR-0007]] 明确：若真实用户档案出现虚拟赛带来的等级变动，说明触发器的 `IS NULL` 过滤被绕过，按 SEV-2 处理。这是该概念最关键的回归信号。
- **触发器评审纪律** —— [[0010-cancel-state-virtual-replay|ADR-0010]] 指出，评分 job 在 `WHERE status = 'FINISHED'` 上又多了一个过滤条件；改触发器时评审者必须同时检查取消态过滤与虚拟过滤是否都还在位。
- **审计入口在快照而非当前行** —— 当「用户 X 在比赛 Y 上的等级变化」被质疑时，答案在 `contest_rating_snapshot` 里，不是 `contests` 当前行（[[0009-israted-gate|ADR-0009]]）。这一查证路径需在 runbook 中固化。

## 参考

- **代码**：`backend-spring/.../modules/contest/`，含 `ContestService`、`RatingCalculationService`、`ContestSchedulerService`、`RankingService`、`ScoringRuleService`。
- **迁移**：`V20260617120000__Contest_Scoring_Hardening.sql`（ADR-0006）、`V20260617130000__Contest_Slug_Unique.sql`、`V20260617140000__Contest_Real_Unique_And_Session_Length.sql`（ADR-0007 / ADR-0010 的 `disabled_reason`、唯一 `real_contest_id`、session 时长检查）。
- **相关**：[[contest]]（比赛实体，真实赛与虚拟赛的状态机与 rating 流）
