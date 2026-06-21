---
title: Notification Idempotency（通知幂等）
tags: [concept, notification, exactly-once]
status: living
updated: 2026-06-21
owner: notification
aliases: [通知幂等, notification ledger]
sources:
  - init-db/migrations/V20260613120000__Create_Notification_Delivery_Ledger.sql
  - backend-spring/src/main/java/com/ulticode/modules/notification/
---

# Notification Idempotency（通知幂等）

> 通知要在多副本 / pm2 reload / 重试下**不重发**。UltiCode 把「业务意图」与「投递账本」分离：intent 表达「该通知谁什么事」，delivery ledger 记录「每个渠道投递了没」，靠 `UNIQUE(intent_id, channel_id)` 实现物理幂等。决策背景 [[0004-notification-intent-and-delivery-ledger]]。

## 表分离

- **Notification**（业务表）——「发生了什么」（点赞、回复、比赛开始…）。
- **notification_delivery_ledger**（投递账本，`V20260613120000`）——「投递审计」，**故意无 FK 到 Notification**——它是投递轨迹，不是业务状态的引用镜像。

## 物理幂等

`UNIQUE(intent_id, channel_id)` —— 同一意图、同一渠道，账本里**最多一行**。多副本 / 重启 / 重试下，第二次 `tryClaim` 命中唯一键，`INSERT ... ON DUPLICATE KEY UPDATE id=id` 使 claim 本身**原子**；后续 `markDelivered` / `markFailed` 走 `delivery_state` 状态机。

## Append-only 审计

- 账本**只追加，无逻辑删除**——每次状态迁移（DELIVERED / FAILED）改 `updated_at`，不删行。
- `updated_at` 镜像状态迁移时刻，**不是**原始 intent 创建时间。

这样即使投递渠道异步、乱序、重试，最终「某意图是否已成功投递到某渠道」永远可查、且不会被重复投递。

## 与判题幂等的共性

和 [[exactly-once-judging]] 一样，本质都是「**把幂等下沉到 DB 唯一约束 + 原子 claim**」，不依赖应用层「记得发没发过」。区别：判题防「判两次」，通知防「发两次」。

## 关联

- 决策为什么 → [[0004-notification-intent-and-delivery-ledger]]
- 同源思想 → [[exactly-once-judging]]
