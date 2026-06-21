---
title: Exactly-Once（精确一次交付）
tags: [concept, queue, exactly-once, notification]
status: living
updated: 2026-06-21
owner: backend
sources:
  - adr/0003-queue-outbox-fencing.md
  - adr/0004-notification-intents-ledger.md
  - CODEMAPS/architecture.md
aliases: [精确一次, 恰好一次]
---

# Exactly-Once（精确一次交付）

## 概述

**exactly-once（精确一次交付）** 是一种消息/副作用交付语义：无论下游 worker 重启、崩溃、水平扩缩，同一条消息（一次评测、一条通知）在系统里**恰好被处理并生效一次** —— 既不重复，也不丢失。

UltiCode 并未引入外部 broker 或 2PC，而是把这一语义实现为**「至少一次 + 幂等」的组合**：消息写到与业务数据同一事务里的 outbox 行（保证至少一次），消费侧用 fencing token / lease / 唯一键去重（保证幂等），从而在单 MySQL 实例上得到事实上的 exactly-once。该横切模式被两条异步副作用管线共享：**判题**（[[0003-queue-outbox-fencing|ADR-0003]]）与**通知投递**（[[0004-notification-intents-ledger|ADR-0004]]）。

## 架构视角

### 涉及模块

- **queue（判题管线）** —— `backend-spring/.../modules/queue/`、`.../submission/service/SubmissionService.java`、`.../infrastructure/JudgeOutboxPoller.java`。提交写入后，后台轮询器从 `judge_outbox` 认领、fork 沙箱容器、回写 verdict。
- **notification（通知投递）** —— `backend-spring/.../modules/notification/`、`.../notification/service/NotificationService.java`、`.../infrastructure/NotificationDeliveryWorker.java`（含按通道的 `EmailQueueWorker`、`PushDeliveryWorker`）。通知事件扇出到多个通道（邮件 / 站内 / 推送）与多个收件人，由定时投递 worker 排空账本表。

### 触发条件

exactly-once 关注的是**异步副作用** —— 即「请求线程不直接完成、交给后台 worker 处理」的那类操作：

- 用户提交代码 → 判题（请求线程只写 `submissions` + `judge_outbox`，verdict 由后台产出）
- 领域事件发生（评测就绪、比赛开始、比赛取消） → 通知扇出（请求线程只写 `notification_intents`，投递由后台 worker 完成）

这些场景的共性是：worker 可能重启、崩溃、被水平扩缩；如果不做 fencing，就会重复消费（verdict 写两次、邮件发两遍）或消费丢失（提交永远卡在 PENDING、通知无声丢弃）。

### 不变量

整个模式由三条不变量共同支撑，跨判题与通知两条管线复用：

1. **outbox 写库 + 轮询（至少一次）** —— 副作用记录与业务变更在**同一事务**写入：`judge_outbox` 行与 `submissions.insert` 同事务（[[0003-queue-outbox-fencing|ADR-0003]]）；`notification_intents` 行与领域变更同事务（[[0004-notification-intents-ledger|ADR-0004]]）。后台 worker 用 `SELECT … FOR UPDATE SKIP LOCKED` 认领行，保证每行至少被一个 worker 拿到。
2. **fencing token / lease 防并发消费** —— 判题侧用 `submission_generation` 列作围栏 token：worker 认领时 `generation=generation+1`，verdict 写入对 `(submission_id, generation)` 幂等；`submission_lease` + `lease_expires_at` 记录当前持有者与 TTL，过期 lease 由 TTL 清理器回收，避免崩溃 worker 永久阻塞提交（[[0003-queue-outbox-fencing|ADR-0003]]）。通知侧用唯一键代 fencing：`notification_delivery_ledger` 的 `(intent_id, channel, recipient_id)` 是唯一键，重试即空操作（[[0004-notification-intents-ledger|ADR-0004]]）。
3. **业务侧防重列（幂等）** —— 判题侧的 `submission_generation` 单调递增，使得旧 worker 在 lease 失效后的回写被天然拒绝（generation 不匹配），verdict 对同一 `(submission_id, generation)` 永远只生效一次（[[0003-queue-outbox-fencing|ADR-0003]]；[[submission]]）。

> 这套模式的运维剧本（lease 过期、TTL 清理器、exactly-once 投递）在判题与通知之间**共享** —— 一条管线吃过的亏，另一条直接复用结论（[[0004-notification-intents-ledger|ADR-0004]] 原文）。

## 决策记录

- [[0003-queue-outbox-fencing|ADR-0003]] — 评测 outbox 围栏：`judge_outbox` + `submission_generation` / `submission_lease` / `lease_expires_at`，单 SQL 实例下的 exactly-once verdict。
- [[0004-notification-intents-ledger|ADR-0004]] — 通知 intents + 投递账本：镜像评测的 outbox 模式，`notification_intents` → `notification_delivery_ledger`，按通道隔离重试。

## 矛盾与未决

- **投递账本归档（待核实，截至 2026-06-19 尚未实现）** —— [[0004-notification-intents-ledger|ADR-0004]] 提出超过 90 天的 `notification_delivery_ledger` 行应由 `BackupService` 加独立清理 job 归档，但该 job 截至 ADR 更新时尚未实现。若 hot table 持续膨胀，`idx_(channel, status, created_at)` 索引上的 EXPLAIN 需重点核对。
- **轮询间隔是一个可调参数** —— `JudgeOutboxPoller` 的轮询间隔（当前 1s）调小会增加数据库读 QPS（[[0003-queue-outbox-fencing|ADR-0003]]）。规模上涨到某个临界点后，是否切到外部 broker（已两次拒绝的备选方案）需重新评估。

## 参考

- **代码**：
  - `backend-spring/.../modules/queue/`（判题 outbox 写入与轮询）
  - `backend-spring/.../infrastructure/JudgeOutboxPoller.java`、`.../submission/service/SubmissionService.java`
  - `backend-spring/.../modules/notification/`、`.../notification/service/NotificationService.java`、`.../infrastructure/NotificationDeliveryWorker.java`
- **迁移**：`V20260613100000__Create_Judge_Outbox.sql`、`V20260613110000__Add_Submission_Generation_And_Lease.sql`、`V20260613120000__Create_Notification_Delivery_Ledger.sql`
- **相关**：[[submission]]（判题实体与完整链路）、[[sandbox-dform]]（沙箱执行侧）
