---
title: 0004 — 通知用 Intent + Delivery Ledger 解耦
tags: [decision, notification, exactly-once]
status: accepted
updated: 2026-06-21
deciders: architect
sources:
  - init-db/migrations/V20260613120000__Create_Notification_Delivery_Ledger.sql
  - backend-spring/src/main/java/com/ulticode/modules/notification/
---

# 0004 — 通知用 Intent + Delivery Ledger 解耦

## 背景（Context）

通知要推到多个渠道（站内 / 邮件 / WebSocket…），且在多副本、pm2 reload、渠道异步重试下**不能重发**。把「业务事件」和「投递动作」混在一张表，会导致「该不该再投一次」无处权威判断——重试与去重纠缠。

## 决策（Decision）

1. **分离两张表**：`Notification`（业务意图：「点赞了谁」「比赛开始了」）与 `notification_delivery_ledger`（投递审计：「某渠道投了没」）。
2. **账本物理幂等**：`UNIQUE(intent_id, channel_id)`；`tryClaim` 用 `INSERT ... ON DUPLICATE KEY UPDATE id=id` 使 claim **原子**；`delivery_state` 经 `markDelivered`/`markFailed` 迁移。
3. **账本 append-only、无逻辑删除、无 FK 到 Notification**——它是投递轨迹审计，不是业务状态的引用镜像。`updated_at` 镜像状态迁移时刻。

## 替代方案（Alternatives）

- **单表 + `delivered` 布尔列**：多渠道时「哪个渠道投了」无法表达，重试与首次投递竞争同一行。否决。
- **渠道自己负责去重**：每加一个渠道重造一遍去重，且跨渠道无法审计。否决。
- **账本加 FK 到 Notification 并随业务删除**：业务删通知会丢投递审计。否决——刻意无 FK。

## 后果（Consequences）

- ✅ 多副本 / 重启 / 重试下每个 (意图,渠道) 恰好投递一次。
- ✅ 投递全审计、可查、append-only。
- ✅ 加渠道 = 加 ledger 行，不改业务表。
- ⚠️ 两表模型，需理解「意图 vs 投递」边界。

## 参考

- 概念 → [[notification-idempotency]]
- 同源思想 → [[exactly-once-judging]]
