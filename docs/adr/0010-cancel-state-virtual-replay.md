---
title: 取消态 + 虚拟回放边界
tags: [adr, contest, lifecycle, cancel]
status: accepted
updated: 2026-06-19
date: 2026-06-xx
deciders: backend
supersedes: N/A
superseded_by: N/A
---

# 0010 — 取消态 + 虚拟回放边界

## 背景

一场比赛在运行中可以被**取消**（比如评测宕机、内容下架）。2026 之前的行为要么忽略取消（计入部分结果），要么全部回滚（对无法控制的事故惩罚用户）。

## 决策

取消是一个**一等状态**，区别于 `FINISHED`：

- `contests.status = 'CANCELLED'`，必须带 `cancel_reason` 和 `cancelled_at`
- 取消的比赛**永不**更新 `global_rankings`
- `first_solve_records` 也不更新
- 取消比赛的虚拟回放被**禁用**（`virtual_contest_sessions.disabled_reason = 'PARENT_CANCELLED'`）
- 通知账本为每个参赛者发一条"比赛取消"的 intent（复用 ADR-0004 的基础设施）

取消状态转换在 {UPCOMING, LIVE} 中任一允许，在 {FINISHED, 已经是 CANCELLED} 中不允许。

## 备选方案

1. **用 `is_cancelled` 标志软删除** — 拒绝：让 schema 同一个概念带两个布尔
2. **允许取消比赛的虚拟回放** — 拒绝：父记录是历史档案，不是有效的练习轮
3. **给用户退款 / 信用** — 出 scope；那是计费决策

## 影响

**正面** — 取消是可见的、有理由的，且不惩罚参赛者或扭曲等级。

**负面** — 评分 job 在 `WHERE status = 'FINISHED'` 上又多一个过滤条件；改触发器时评审者必须检查。

**运维影响** — 一场取消的比赛必须发一条通知（用 ADR-0004）；`V20260617140000__..._And_Session_Length` 这次迁移加上 disabled_reason 列。

## 参考

- **迁移**：`init-db/migrations/V20260617140000__Contest_Real_Unique_And_Session_Length.sql`
- **代码**：`backend-spring/.../contest/service/ContestSchedulerService.java`
- **CODEMAPS**：[[data]] § "Contests"
- **相关 ADR**：[[0004-notification-intents-ledger]]（取消必发通知，正文复用其账本）、[[0006-contest-scoring-activation]]（评分 — 取消态从不更新 rating）、[[0007-virtual-contest-rating-isolation]]（虚拟隔离）、[[0009-israted-gate]]（`isRated` 门控）
