---
title: 虚拟赛生命周期 + 等级隔离
tags: [adr, contest, rating, virtual]
status: accepted
updated: 2026-06-19
date: 2026-06-17
deciders: backend
supersedes: N/A
superseded_by: N/A
---

# 0007 — 虚拟赛生命周期 + 等级隔离

## 背景

**虚拟赛**让用户按需回放过去的比赛（同样的题、同样的时间窗、隔离的等级）。第一个实现忘了隔离 `global_rankings` 更新 — 虚拟跑在悄悄挪动真实用户的等级。

## 决策

虚拟赛有一个**独立的状态列**（`contests.kind = 'VIRTUAL'`），它们的提交被钉到一张 `virtual_contest_sessions` 行，链接到父 `real_contest_id`。

**等级隔离**：

- 只有当父比赛为 `FINISHED` **且** `contests.is_rated = true` **且**该 session **不是**虚拟回放时，才更新 `global_rankings`
- 更新 `global_rankings` 的触发器以 `virtual_contest_sessions.id IS NULL` 过滤
- `first_solve_records` 同样门控

`V20260617140000__Contest_Real_Unique_And_Session_Length` 这次迁移加上 `real_contest_id` 上的唯一约束（一个虚拟赛必须有且仅有一个父）和一个 session 时长合理性检查（1h ≤ 时长 ≤ 24h）。

## 备选方案

1. **单独的 `virtual_global_rankings` 表** — 拒绝：多数查询不在乎；`IS NULL` 过滤更便宜
2. **彻底禁掉虚拟赛** — 拒绝：是已文档化的功能；资深用户依赖它练习
3. **在不同的 schema 跑虚拟赛** — 拒绝：跨 schema 查询会很痛

## 影响

**正面** — 虚拟回放天然安全；这里出回归也不太可能静默影响真实用户。

**负面** — 新增 1 张表；评分 job 必须同时读 `contests` 和 `virtual_contest_sessions` — 一个小 join，不是热路径。

**运维影响** — 真实用户档案里出现虚拟赛带来的等级变动，说明触发器过滤被绕过；按 SEV-2 处理。

## 参考

- **迁移**：`init-db/migrations/V20260617140000__Contest_Real_Unique_And_Session_Length.sql`
- **代码**：`backend-spring/.../contest/service/RatingCalculationService.java`，
  虚拟 session join 在 `ContestService` 和 `RankingService` 中
- **CODEMAPS**：[`data.md`](../CODEMAPS/data.md) § "Contests"
- **相关 ADR**：`0006`（评分）、`0009`（`isRated` 门控）、`0010`（取消态）
