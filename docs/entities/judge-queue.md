---
title: Judge Queue（判题队列）
tags: [entity, queue, judging, exactly-once, shadow]
status: living
updated: 2026-06-21
owner: queue
aliases: [判题队列, judge queue]
sources:
  - backend-spring/src/main/java/com/ulticode/modules/queue/port/JudgeQueue.java
  - backend-spring/src/main/java/com/ulticode/modules/queue/port/adapter/RedissonStreamsJudgeQueueAdapter.java
  - backend-spring/src/main/java/com/ulticode/modules/queue/port/adapter/InMemoryJudgeQueueAdapter.java
  - backend-spring/src/main/java/com/ulticode/modules/queue/outbox/
  - backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java
  - init-db/migrations/V20260613100000__Create_Judge_Outbox.sql
---

# Judge Queue（判题队列）

> 判题任务的异步队列。**Hexagonal ports & adapters**：一个 `JudgeQueue` 端口，Redis Streams（生产）/ InMemory（测试）双 adapter，外加 DB outbox 做派发真源。幂等机制见 [[exactly-once-judging]]，切换路径见 [[shadow-mode-cutover]]，决策背景 [[0001-judge-outbox-and-generation-fencing]]。

## 端口（`queue/port/JudgeQueue.java`）

ADR-003 §2.4 M3c 的 hex-arch 端口：

| 方法 | 语义 |
| --- | --- |
| `enqueue(JudgeJobEnvelope)` | 入队，**幂等于 `(submissionId, generation)`** —— 同 key 永不重复派发 |
| `poll(timeoutMillis) → Optional<JudgeJobHandle>` | 取一个 job（`<=0` 非阻塞） |
| `ack(handle)` | 处理完，标记派发可置 SENT |
| `nack(handle, reason)` | 处理失败，归还队列 |

adapter 用 broker 自带去重原语（Redis Streams 自动 ID + `(submissionId, generation)` 业务 key）实现幂等；未 ack 的条目由 reaper 回收。

## Adapter（`queue/port/adapter/`）

- `RedissonStreamsJudgeQueueAdapter` —— 生产实现，Redis Streams。
- `InMemoryJudgeQueueAdapter` —— 测试用。

## Outbox（`queue/outbox/`）—— 派发真源

`init-db/migrations/V20260613100000__Create_Judge_Outbox.sql` 建的 `judge_outbox` 表是**派发真源**（shadow-mode dispatch truth）：

| 列/约束 | 作用 |
| --- | --- |
| `UNIQUE(submission_id, generation)` | ★ 同 generation 最多入队一次 —— **物理幂等** |
| `state` | `PENDING / SENT / DEAD / ARCHIVED` |
| `is_shadow` | M3a/M3b shadow = 1（非活跃生产者），M3c cutover 翻 0（watermark: `created_at > cutover_at`） |
| `created_at` / `next_retry_at` | 用 **DB `CURRENT_TIMESTAMP(3)`**，不用 Java clock（防时钟漂移） |
| `idx_state_retry` | dispatcher 按 `(state, next_retry_at)` 扫待发 |

组件：

- `JudgeOutboxDispatcher` —— 扫 PENDING、入队、置 SENT。
- `JudgeOutboxRecord` / `JudgeOutboxMapper` —— 实体 + MyBatis 映射。
- `UnackedStreamEntriesReaper` —— 回收 Redis Streams 未 ack 条目（M3c-2）。
- `OutboxShadowComparator` —— shadow 期比对 broker 与 outbox 一致性（见 [[shadow-mode-cutover]]）。

## Worker（`queue/processor/JudgeWorkerProcessor.java`）

`implements JobProcessor<JudgeJob>`，两条并行 `@Scheduled` 轮询路径：

- `pollAndProcess()` —— **legacy 路径**，经 `QueueService.pollJob`，`queue.poll-interval-ms:1000`。
- `pollAndProcessFromPort()` —— **M3c-3a 新 port 路径**，`judge.port.poll-interval-ms:1000`，500ms 短 poll。与 legacy 并行，「哪个 port 活跃哪个驱动」。

worker 拿到 job → 经 [[submission]] 的 fence claim（generation CAS）→ 跑 [[sandbox-d-form]] → 写终态 → 发 `SubmissionJudgedEvent`。

## 关联

- **幂等怎么落地** → [[exactly-once-judging]]
- **新旧路径怎么切** → [[shadow-mode-cutover]]
- **worker 怎么判一道题** → [[submission]] + [[codemap/judging-pipeline]]
- **为什么** → [[0001-judge-outbox-and-generation-fencing]]
