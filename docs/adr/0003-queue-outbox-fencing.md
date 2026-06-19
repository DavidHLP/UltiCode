---
title: 评测 outbox 围栏（judge_outbox、lease）
tags: [adr, sandbox, queue, exactly-once]
status: accepted
updated: 2026-06-19
date: 2026-06-13
deciders: architect, java-reviewer
supersedes: N/A
superseded_by: N/A
---

# 0003 — 评测 outbox 围栏（`judge_outbox`、lease）

## 背景

原版评测管线用进程内队列 + 单后端实例。后端水平扩缩或重启时出现两个问题：

1. **重复评测** — 重启的 worker 会重轮询一行本来已在旧实例上 in-flight 的数据
2. **提交丢失** — worker 在 fork 和 verdict 写之间崩溃，`submissions.status` 永远卡在 `PENDING`

外部消息系统（RabbitMQ、Kafka）评估过但被否：多一个运维面，加上 verdict 写需要 2PC。

## 决策

使用**事务性 outbox 模式**加**行级 lease**：

- `judge_outbox` 行与 `submissions.insert` 在同一事务里写入
- 后台轮询 `JudgeOutboxPoller` 通过 `SELECT … FOR UPDATE SKIP LOCKED` 认领行，并 `UPDATE … SET generation=generation+1, lease=<worker_id>, lease_expires_at=now+ttl`
- verdict 写入对 `(submission_id, generation)` 是幂等的 — `submission_generation` 列就是围栏 token
- 过期 lease 由 TTL 清理器回收

这给我们**单 SQL 实例**下的 exactly-once verdict 语义 — 没有外部 broker、没有 2PC、没有 dedup 表。

## 备选方案

1. **RabbitMQ / Kafka + 幂等消费者** — 拒绝：多一个基础设施，部署后的 day-2 负担
2. **每行 DB advisory lock** — 拒绝：突发提交压力下会死锁
3. **不带 lease 的轮询** — 拒绝：原版的问题；重启即重复

## 影响

**正面** — 单 MySQL 实例即可应对 10k 提交/天。失败模式（worker 崩溃、MySQL 故障转移）由 `lease_expires_at` 兜底。

**负面** — 每行 `submissions` 加 2 列 + 一个轮询循环。轮询间隔（当前 1s）是一个可调参数；调小会增加数据库读 QPS。

**运维影响** — 迁移 `V20260613110000__Add_Submission_Generation_And_Lease` 是 schema 的真源。如果迁移被前滚，轮询器会停滞；用标准 Flyway 前向修复模式回退。

## 参考

- **迁移**：
  - `init-db/migrations/V20260613100000__Create_Judge_Outbox.sql`
  - `init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql`
- **代码**：`backend-spring/.../infrastructure/JudgeOutboxPoller.java`、
  `backend-spring/.../submission/service/SubmissionService.java`
- **CODEMAPS**：[`data.md`](../CODEMAPS/data.md) § "Submissions & Judging"、
  [`backend.md`](../CODEMAPS/backend.md) § "Background Workers"
- **相关 ADR**：`0001`（评测编解码）、`0002`（沙箱）、`0004`（通知账本 — 同一模式）
