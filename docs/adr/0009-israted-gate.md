---
title: isRated 门控 + 虚拟等级隔离
tags: [adr, contest, rating]
status: accepted
updated: 2026-06-19
date: 2026-06-xx
deciders: backend
supersedes: N/A
superseded_by: N/A
---

# 0009 — `isRated` 门控 + 虚拟等级隔离

## 背景

部分比赛（如训练轮、娱乐轮）明确**不计等级**。2026 之前的一次回归，让某些不计等级的比赛更新了恰好在虚拟 session 上的用户的 `global_rankings` — 两件事都错了。

## 决策

`contests.is_rated` 是一个**布尔门控**，由评分 job 求值：

- 默认：所有真实比赛为 `true`
- `false` 用于：训练轮、练习赛、内部娱乐赛
- 评分触发器从比赛在 **FINISHED 时**的快照读 `is_rated`，不是通过 join
- 虚拟回放双重门控：即使父比赛计等级，虚拟回放也不更新等级（见 ADR-0007）

第一版这个门控是 join；那是错的，因为比赛列在 LIVE 和 FINISHED 之间可能变化。新代码在 FINISHED 时把快照存到 `contest_rating_snapshot` 表。

## 备选方案

1. **去掉列；改用单独的 `unrated_contests` 表** — 拒绝：让临时查询更困难；布尔已经够用
2. **给用户打标签（`users.allow_rating_update`）** — 拒绝：关键的是比赛意图，不是用户偏好

## 影响

**正面** — 等级更新可以靠读一列来审计。

**负面** — 新增 `contest_rating_snapshot` 表；FINISHED 时的 job 必须在评分 job 跑之前写入（顺序契约）。

**运维影响** — 当"用户 X 在比赛 Y 上的等级变化"被质疑时，答案在快照里，不是 `contests` 当前行。

## 参考

- **代码**：`backend-spring/.../contest/service/RatingCalculationService.java`
- **CODEMAPS**：[[data]] § "Contest Rating Isolation"
- **相关 ADR**：[[0006-contest-scoring-activation]]（评分 — `isRated` 被 `ContestScoringService` 消费）、[[0007-virtual-contest-rating-isolation]]（虚拟隔离）、[[0010-cancel-state-virtual-replay]]（取消态）
