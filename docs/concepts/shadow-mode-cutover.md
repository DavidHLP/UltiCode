---
title: Shadow-mode Cutover（影子模式切换）
tags: [concept, queue, shadow, deploy, judging]
status: living
updated: 2026-06-21
owner: queue
aliases: [影子模式, shadow mode, cutover]
sources:
  - init-db/migrations/V20260613100000__Create_Judge_Outbox.sql
  - backend-spring/src/main/java/com/ulticode/modules/queue/outbox/shadow/OutboxShadowComparator.java
  - backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java
---

# Shadow-mode Cutover（影子模式切换）

> 把判题派发从**旧路径**（`QueueService.enqueueJudgeJob` / `RQueue.add`）切到**新路径**（`JudgeQueue` port + outbox dispatcher）时，用**影子模式**渐进切换：先双写比对、再以 watermark 切真源，避免大爆炸式上线。

载体见 [[judge-queue]]，幂等背景见 [[exactly-once-judging]]。

## 三阶段（M3a / M3b / M3c）

迁移注释（`V20260613100000` / `V20260613110000`）与代码里的 M 标记对应：

| 阶段 | outbox 角色 | 语义 |
| --- | --- | --- |
| **M3a** | `is_shadow = 1` | outbox 表已建、双写，但**不是活跃生产者**——只记录「如果由新路径派发会怎样」 |
| **M3b** | fence 开启 | `submissions` 加 `generation` / lease 列，worker 走 fenced 路径，但仍走旧入队 |
| **M3c** | `is_shadow = 0`（cutover） | 新路径成为真源。watermark：`created_at > cutover_at` 的 outbox 行才由 dispatcher 派发 |

## 一致性比对（`OutboxShadowComparator`）

`queue/outbox/shadow/OutboxShadowComparator.java` 在 shadow 期比对 **broker 实际派发** 与 **outbox 记录**，发现偏差即告警——让新路径在「只读」状态下被验证与旧路径一致，再切。

## Worker 双路径并行

`JudgeWorkerProcessor` 同时跑两条 `@Scheduled`（见 [[judge-queue]]）：legacy `pollAndProcess` 与新 port `pollAndProcessFromPort`。cutover 期间「哪个 port 活跃哪个驱动」，可灰度回退。

## 为什么这么做

判题是** correctness-critical + 不可重放副作用**（判两次 = 不公平）。直接换路径风险极高；shadow mode 让新路径在不影响线上判题的前提下被观察、比对、再以 watermark 切换，出错可在 watermark 内回退。

## 关联

- 载体 → [[judge-queue]]
- 幂等基础 → [[exactly-once-judging]]
- 切换依据的迁移 → `V20260613100000` / `V20260613110000`
