---
title: 比赛评分引擎激活
tags: [adr, contest, scoring]
status: accepted
updated: 2026-06-19
date: 2026-06-17
deciders: backend, java-reviewer
supersedes: N/A
superseded_by: N/A
---

# 0006 — 比赛评分引擎激活

## 背景

`scoring_rules` 这张表存在了数月，但比赛排名引擎仍在用老的硬编码评分（AC 用时求和）。这造成两个实际问题：

- 资深用户发现部分比赛排名和公布的规则对不上（"ICPC 罚时"没生效）
- 新的规则类型（比如 IOI 风格的部分分）无处安放

## 决策

评分引擎改为**规则驱动**：一场比赛引用一个 `scoring_rule_id`，引擎在排名时解析规则。`V20260617120000__Contest_Scoring_Hardening` 这次迁移加上激活标志、校验、以及老比赛的默认规则。

**门控规则**：

- 一场比赛的 `scoring_rule_id` 必须在 `status = LIVE` 之前设好
- 比赛中途换规则会被拒绝（会让 `first_solve_records` 失效）
- `RatingCalculationService` 总是读比赛启动时的那个规则，即使之后规则被改

## 备选方案

1. **让 `scoring_rules` 不可变**（编辑时 fork） — 拒绝：表会膨胀；我们改成启动时钉死
2. **在引擎里硬编码 ICPC + IOI** — 原版的问题
3. **外部规则引擎（Drools）** — 拒绝：以当前比赛量级，运维成本远大于收益

## 影响

**正面** — 新评分规则是数据而非代码；运维改 ICPC 罚时不用发版。

**负面** — `scoring_rules` 成为比赛的依赖；规则就地编辑可能悄无声息地影响未来比赛。由上面"启动时钉死"规则缓解。

**运维影响** — 排名看着不对时，检查 `contests.scoring_rule_id` 和该规则的 `valid_from` / `valid_to`。

## 参考

- **迁移**：`init-db/migrations/V20260617120000__Contest_Scoring_Hardening.sql`
- **代码**：`backend-spring/.../contest/service/ContestScoringService.java`、
  `backend-spring/.../contest/service/ScoringRuleService.java`
- **CODEMAPS**：[`data.md`](../CODEMAPS/data.md) § "Contests"、
  [`backend.md`](../CODEMAPS/backend.md) § "Key Services"
- **相关 ADR**：`0007`（虚拟赛隔离）、`0009`（`isRated` 门控）
