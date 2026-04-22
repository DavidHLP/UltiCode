# Phase 36: Achievement 异步化 - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

将 Achievement 检查从同步阻塞改为异步执行，主线程不再等待 Achievement 检查完成。

**目标：** Achievement 检查不再阻塞主线程（提交、判题等核心流程）

</domain>

<decisions>
## Implementation Decisions

### Achievement Check Async Strategy
- **D-01:** Achievement 检查使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` 在主事务提交后异步执行
- **D-02:** 新建独立的 achievement check listener，只处理 `checkAndAwardAchievements`（DB 检查和写入），不发通知
- **D-03:** `AchievementNotificationListener` 保持现有 `@Async` + `@EventListener` 实现，职责不变（只发通知）

### Trigger Methods → Fire-and-Forget
- **D-04:** `AchievementTriggerService.onProblemSolved()` 等 trigger 方法改为 `void` + `@Async`，SubmissionService 等调用方不等待结果
- **D-05:** SubmissionServiceImpl 等调用方直接调用 `achievementTriggerService.onProblemSolved()`，无需 `CompletableFuture` 包装

### Error Handling
- **D-06:** Achievement 检查失败静默失败（log.warn），不重试
- **D-07:** 理由：成就检查对用户体验影响小，简化实现

### No Scope Changes
- 通知逻辑保持不变，不合并到新的 check listener 中
- 只改异步，不改业务逻辑

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Context
- `.planning/phases/35-flyway-url/35-CONTEXT.md` — Phase 35 context (recent prior)
- `.planning/STATE.md` — v1.8 milestone context
- `.planning/REQUIREMENTS.md` — PITFALL-01 requirement

### Backend Code (for reference)
- `backend-spring/src/main/java/com/ulticode/modules/achievement/listener/AchievementNotificationListener.java` — existing async listener pattern
- `backend-spring/src/main/java/com/ulticode/modules/achievement/service/impl/AchievementTriggerServiceImpl.java` — checkAndAwardAchievements implementation
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java` — calls onProblemSolved synchronously (line ~243)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `@EnableAsync` already configured in `UlticodeBackendApplication.java`
- `AchievementNotificationListener` — existing `@Async` + `@EventListener` pattern to follow
- `AchievementEarnedEvent` — existing event record for achievement earned
- `@Transactional` already on `checkAndAwardAchievements`

### Established Patterns
- `@Async` methods return `void` for fire-and-forget
- Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)` for post-commit execution
- Constructor injection via `@RequiredArgsConstructor`

### Integration Points
- `SubmissionServiceImpl.onSubmit()` — calls `achievementTriggerService.onProblemSolved()` (同步调用，需改)
- `FollowServiceImpl` — also calls achievement triggers
- `ContestServiceImpl` — also calls achievement triggers

</code_context>

<specifics>
## Specific Ideas

无特殊要求，按标准 Spring `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` 模式实现即可。

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 36-achievement*
*Context gathered: 2026-04-22*
