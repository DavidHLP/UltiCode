---
title: 0001 — 判题用 Outbox + Generation Fencing
tags: [decision, judging, queue, exactly-once, fence]
status: accepted
updated: 2026-06-21
deciders: architect
sources:
  - init-db/migrations/V20260613100000__Create_Judge_Outbox.sql
  - init-db/migrations/V20260613110000__Add_Submission_Generation_And_Lease.sql
  - backend-spring/src/main/java/com/ulticode/modules/queue/
  - backend-spring/src/main/java/com/ulticode/modules/submission/fence/
---

# 0001 — 判题用 Outbox + Generation Fencing

## 背景（Context）

判题是 **correctness-critical 且有不可重放副作用**的操作——同一道提交被判两次 = 不公平、结果被覆盖 = 数据错乱。纯 broker（Redis Streams）只给「至少一次」，worker 崩溃 / 重启 / 多副本 / 消息重投都会导致重复或丢失。我们需要「恰好一次」的判题语义，且不能靠应用层「记得判过没」。

## 决策（Decision）

在「至少一次」的 Redis Streams 之上叠一层 **DB 真源 + 五道防线**（详见 [[exactly-once-judging]]）：

1. `judge_outbox` 表 `UNIQUE(submission_id, generation)` —— 物理幂等入队。
2. `submissions.generation` fence CAS —— 旧消息的迟到重投写不进终态。
3. lease CAS（`judging_lease_expires_at`，TTL 60s / 心跳 20s）—— 防并发判题。
4. DB `CURRENT_TIMESTAMP(3)` —— 过期/重试判定不受 Java clock 漂移影响。
5. `JudgingLeaseReaper` + `UnackedStreamEntriesReaper` —— 回收崩溃 worker 的 lease 与未 ack 条目。

切采用 **shadow mode**（M3a/M3b/M3c 渐进，见 [[shadow-mode-cutover]]）。

## 替代方案（Alternatives）

- **纯 Redis Streams `at-least-once` + 应用层去重表**：去重表仍需唯一约束，且把「是否判过」的真理分散到两处。否决——不如让 DB outbox 直接做真源。
- **Exactly-once broker（如 Kafka 事务）**：引入重依赖，且判题副作用在 DB 不在 broker。否决。
- **单进程内存队列**：无法水平扩展、重启即丢。否决。

## 后果（Consequences）

- ✅ 恰好一次判题语义，worker 可任意崩溃/重启/扩缩。
- ✅ 幂等下沉到 DB 约束，应用层无需「记得」。
- ⚠️ 三张表 + reaper + 双 worker 路径，复杂度高（用 shadow mode + `OutboxShadowComparator` 比对缓解）。
- ⚠️ `generation` 列回填默认 1，偏离严格 expand-contract（迁移注释已记录该取舍）。

## 参考

- 机制总览 → [[exactly-once-judging]]
- 载体 → [[judge-queue]]、[[submission]]
- 切换 → [[shadow-mode-cutover]]
