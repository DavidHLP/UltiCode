---
title: Exactly-once Judging（判题幂等）
tags: [concept, judging, queue, exactly-once, fence, lease]
status: living
updated: 2026-06-21
owner: judging
aliases: [判题幂等, exactly-once]
sources:
  - init-db/migrations/V20260613100000__Create_Judge_Outbox.sql
  - init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql
  - backend-spring/src/main/java/com/ulticode/modules/queue/
  - backend-spring/src/main/java/com/ulticode/modules/submission/fence/
---

# Exactly-once Judging（判题幂等）

> 判题要同时满足「**不丢**」（worker 崩了提交不能消失）和「**不重**」（同题不能被判两次、结果不能被覆盖）。UltiCode 用 **DB outbox 物理幂等 + generation fence CAS + lease CAS + DB 时钟 + reaper** 五件套实现。

判题在「至少一次」的 Redis Streams 之上叠了一层 DB 真源，把语义抬到 exactly-once。决策背景 [[0001-judge-outbox-and-generation-fencing]]，载体见 [[judge-queue]] / [[submission]]。

## 五道防线

### 1. Outbox 物理幂等（防重复入队）

`judge_outbox` 表 `UNIQUE(submission_id, generation)` —— **数据库唯一约束**让「同 generation 入队两次」物理不可能，不依赖应用层检查。`state` 走 `PENDING → SENT → DEAD/ARCHIVED`。

### 2. Generation fence CAS（防重复派发 / 重复判决）

`submissions.generation BIGINT`。每次（重）判递增 generation；worker 写回结果时带 generation 做条件更新——旧 generation 的迟到的重复消息写不进终态（CAS 失败）。

### 3. Lease CAS（防并发判题）

`judging_lease_expires_at` + `current_attempt_id`。提交进 `JUDGING` 时占 lease（TTL 60s，心跳 20s 续租，见 [[submission]] 的 `LeaseConstants`）。两个 worker 不会同时判同一题——第二个的 fence claim 失败。

### 4. DB 时钟（防 Java clock 漂移）

`judge_outbox.created_at` / `next_retry_at` 用 **DB `CURRENT_TIMESTAMP(3)`**，不用 Java clock。多实例 / NTP 漂移下，过期与重试判定仍一致。

### 5. Reaper（防丢失）

- `JudgingLeaseReaper` —— 扫 `idx_lease_expiry(status, judging_lease_expires_at)`，把 lease 过期的 `JUDGING` 行回退到 `PENDING`（唯一合法的 `JUDGING→PENDING` 转换，见 [[submission]] 状态机），重新入队。
- `UnackedStreamEntriesReaper` —— 回收 Redis Streams 未 ack 条目。

## 组合语义

```
重复入队  → outbox UNIQUE 挡住
重复派发  → generation fence CAS 挡住
并发判题  → lease CAS 挡住
worker 崩 → lease 过期，reaper 回退重判
时钟漂移  → DB 时钟兜底
```

结果：每道提交**恰好被判一次**，终态由最高 generation 的合法判决写入。

## 关联

- 载体 → [[judge-queue]]、[[submission]]
- 新旧派发路径切换 → [[shadow-mode-cutover]]
- 决策为什么 → [[0001-judge-outbox-and-generation-fencing]]
