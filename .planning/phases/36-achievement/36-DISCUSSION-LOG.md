# Phase 36: Achievement 异步化 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-22
**Phase:** 36-achievement
**Areas discussed:** Achievement Check Async Strategy, Listener scope, Error handling

---

## Achievement Check Async Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| TransactionalEventListener | @TransactionalEventListener(phase = AFTER_COMMIT) + @Async，在主事务提交后才执行 Achievement 检查 | ✓ |
| CompletableFuture.runAsync | 在 trigger 调用方用 CompletableFuture.runAsync() 包装，@Async 在新线程执行 | |

**User's choice:** TransactionalEventListener
**Notes:** 在主事务提交后才执行检查，确保数据一致性

---

## Listener scope

| Option | Description | Selected |
|--------|-------------|----------|
| 只移动检查逻辑 | 把 checkAndAwardAchievements 的逻辑移到新的 AFTER_COMMIT listener，只处理 DB 检查和写入，不发通知 | ✓ |
| 检查+通知都在 async 执行 | 将 checkAndAwardAchievements 移到新 listener，同时把 AchievementNotificationListener 的通知也合并进来 | |

**User's choice:** 只移动检查逻辑
**Notes:** 最小改动，通知逻辑保持不变

---

## Error handling

| Option | Description | Selected |
|--------|-------------|----------|
| 静默失败 | Achievement 检查失败只记 warn log，不重试 | ✓ |
| Spring Retry | 使用 @Retryable 在检查失败时自动重试 1-2 次 | |

**User's choice:** 静默失败
**Notes:** 成就检查对用户体验影响小，简化实现

---

## Claude's Discretion

All decisions made by user — no areas deferred to Claude.

## Deferred Ideas

None
