---
title: Submission（提交）
tags: [entity, judging, lifecycle, fence, lease]
status: living
updated: 2026-06-21
owner: judging
aliases: [提交, 判题结果, submission]
sources:
  - backend-spring/src/main/java/com/ulticode/modules/submission/entity/Submission.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/enums/SubmissionStatus.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/fence/SubmissionStateMachine.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/fence/LeaseConstants.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/event/SubmissionJudgedEvent.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/reaper/JudgingLeaseReaper.java
  - backend-spring/src/main/java/com/ulticode/modules/submission/codec/SubmissionStatusCodec.java
  - init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql
---

# Submission（提交）

> 一次「用户提交代码 → 判题 → 终态 verdict」的完整记录。本页综合提交实体的**状态机、generation fence、lease、终态分类、判题完成事件**。

这是判题子系统的核心实体。它和 [[judge-queue]]、[[sandbox-d-form]] 一起构成端到端链路（见 [[codemap/judging-pipeline]]）；幂等机制见 [[exactly-once-judging]]，决策背景见 [[0001-judge-outbox-and-generation-fencing]]。

## 状态机（`SubmissionStatus`）

提交状态分四个 `Kind`（见 `submission/enums/SubmissionStatus.java`，每个状态带 `displayName / uiStyle / terminal / severity / Kind`）：

| Kind | 状态 | 说明 |
| --- | --- | --- |
| `IN_FLIGHT`（在途） | `PENDING`、`JUDGING` | 未终态，前端统一显示「pending」 |
| `TERMINAL_GOOD`（成功） | `ACCEPTED` | 通过 |
| `TERMINAL_BAD`（用户错误） | `PRESENTATION_ERROR`、`WRONG_ANSWER`、`TIME_LIMIT_EXCEEDED`、`MEMORY_LIMIT_EXCEEDED`、`OUTPUT_LIMIT_EXCEEDED`、`RUNTIME_ERROR`、`COMPILE_ERROR` | severity 1–6 |
| `TERMINAL_INFRA`（基础设施错误） | `SANDBOX_ERROR`、`SYSTEM_ERROR` | severity 7–8，不是用户的锅 |

终态 verdict 与沙箱 envelope 的 verdict 集对齐（见 [[sandbox-d-form]]）。

## 双通道状态转换（`SubmissionStateMachine`）

`submission/fence/SubmissionStateMachine.java` 是**纯函数**状态机（无副作用，决策背景 [[0001-judge-outbox-and-generation-fencing]] §2.5）。两条独立转换通道：

- **系统通道** `SYSTEM_ALLOWED` —— 判题 worker / lease reaper 可走的转换。`canSystemTransition(from, to)` 判定。注意 `JUDGING → PENDING` 是**唯一合法的「回退」**，专用于 lease 过期后的重判路径。
- **管理员重判通道** `ADMIN_REJUDGE_FROM` —— 管理员手动重判可从哪些状态发起。`canAdminRejudgeFrom(from)` 判定。

> 设双通道的原因：把「自动恢复」和「人工干预」的合法转换集合分开，互不污染；`EnumMap` 还带来按 enum 键的类型安全且避免装箱。

## Generation fence + lease（防并发 / 防重复）

迁移 `init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql` 给 `submissions` 表加的三列（[[0001-judge-outbox-and-generation-fencing]] §2.2–2.3）：

| 列 | 作用 |
| --- | --- |
| `generation BIGINT NOT NULL DEFAULT 1` | fence CAS 的版本号。历史行由列默认值回填为 1；只有 M3b fence 开启后才有代码读它 |
| `current_attempt_id varchar(40)` | 仅 JUDGING 时填充，标识当前判题尝试 |
| `judging_lease_expires_at datetime(3)` | 仅 JUDGING 时填充，lease 过期时间 |

索引 `idx_lease_expiry(status, judging_lease_expires_at)` 支撑 reaper 按「JUDGING 且 lease 过期」反查。

**Lease 调参**（`submission/fence/LeaseConstants.java`，决策背景 §3.3）：

- `LEASE_TTL_SECONDS = 60` —— JUDGING lease 无心跳的有效期。
- `HEARTBEAT_INTERVAL_SECONDS = 20` —— TTL/3，给 worker ~2 次续租余量。
- `REAPER_BATCH_SIZE = 20`，reaper 每 5s 扫一批。

> 取舍：TTL 短 → 崩溃 worker 恢复快但心跳写多；TTL 长 → 写少但恢复慢。60s/20s 是当前平衡点。

## Lease 回收（`JudgingLeaseReaper`）

`submission/reaper/JudgingLeaseReaper.java` 周期扫 `idx_lease_expiry`，把 lease 过期的 JUDGING 行回收——走 `JUDGING → PENDING` 唯一合法回退，让该提交重新入队（见 [[judge-queue]]）。这是 worker 崩溃 / 重启后不丢提交的关键。

## 判题完成事件（`SubmissionJudgedEvent`）

提交进入终态后发布 `submission/event/SubmissionJudgedEvent.java`，驱动下游：比赛排名更新（[[contest]]）、成就、通知（[[notification-idempotency]]）等。事件解耦让判题与副作用互不阻塞。

## Verdict 编解码（`SubmissionStatusCodec`）

`submission/codec/SubmissionStatusCodec.java` 负责沙箱 envelope verdict ↔ `SubmissionStatus` 的映射。沙箱 D-form 的 verdict 集与 legacy Form A 表述在 backend 统一映射到同一组 `SubmissionStatus`，前端无需区分两种沙箱（见 [[sandbox-d-form]] 的 verdict 契约）。

## 关联

- **怎么保证不重复 / 不丢失判题** → [[exactly-once-judging]]
- **提交怎么被消费** → [[judge-queue]]（worker poll → fence claim → sandbox → 写终态）
- **沙箱怎么跑代码** → [[sandbox-d-form]]
- **端到端链路** → [[codemap/judging-pipeline]]
- **为什么这么设计** → [[0001-judge-outbox-and-generation-fencing]]
