---
title: 通知 intents + 投递账本
tags: [adr, notification, queue, exactly-once]
status: accepted
updated: 2026-06-19
date: 2026-06-13
deciders: backend
supersedes: N/A
superseded_by: N/A
---

# 0004 — 通知 intents + 投递账本

## 背景

通知要扇出到多个通道（邮件、站内、推送）以及多个收件人（每用户、每参赛者）。原版设计是把每条通知直接内联发送，意味着：

- 慢的 SMTP 中继会阻塞请求线程
- 失败的推送通知会丢失 — 没有重试，没有审计
- 按通道去重逻辑散在 `EmailService`、`NotificationService`、`PushService` 里

## 决策

通知采用**和 ADR-0003 同样的 outbox 模式**：

- `notification_intents` — *事件*（"评测结果就绪"、"比赛开始"），与领域变更同一事务写入
- `notification_delivery_ledger` — 每条 (intent, channel, recipient) 一行；**投递 worker** 按通道排空这张表
- 幂等性：`(intent_id, channel, recipient_id)` 是唯一键；重试是空操作
- 按通道的 worker（`EmailQueueWorker`、`PushDeliveryWorker`）是定时任务，不是请求时工作

这镜像了评测的 outbox 设计，所以运维剧本（lease 过期、TTL 清理器、exactly-once 投递）是共享知识。

## 备选方案

1. **Spring Events + 异步监听器** — 拒绝：没有持久化重试，没有合规账本
2. **外部消息 broker**（RabbitMQ topic exchange） — 出于与 ADR-0003 同样的理由拒绝；当前系统规模撑不起运维成本
3. **按通道同步发送** — 原版的问题

## 影响

**正面** — 统一的重试语义；"这个用户收到邮件了吗？"一个地方就能查；按通道失败相互隔离。

**负面** — 新增 2 张表；投递 worker 是一张热表上的热循环。`idx_(channel, status, created_at)` 索引至关重要 — `EXPLAIN` 评审时务必核对。

**运维影响** — 超过 90 天的 `notification_delivery_ledger` 行可归档；由 `BackupService` 加一个独立的清理 job 覆盖（截至 2026-06-19 尚未实现）。

## 参考

- **迁移**：`init-db/migrations/V20260613120000__Create_Notification_Delivery_Ledger.sql`
- **代码**：`backend-spring/.../notification/service/NotificationService.java`、
  `backend-spring/.../infrastructure/NotificationDeliveryWorker.java`
- **CODEMAPS**：[[data]] § "Notifications & Delivery"
- **相关 ADR**：[[0003-queue-outbox-fencing]]（同一 outbox 模式）
