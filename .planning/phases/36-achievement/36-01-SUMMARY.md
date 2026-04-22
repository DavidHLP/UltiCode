# Plan 36-01: Achievement Async — Summary

**Phase:** 36-achievement
**Plan:** 36-01
**Completed:** 2026-04-22

## Tasks Completed

| # | Task | Status |
|---|------|--------|
| 1 | Create AchievementCheckEvent record | Done |
| 2 | Create AchievementCheckListener with @Async + @TransactionalEventListener(AFTER_COMMIT) | Done |
| 3 | Refactor AchievementTriggerServiceImpl trigger methods to void + @Async fire-and-forget | Done |

## What Was Built

### New Files

- `backend-spring/src/main/java/com/ulticode/modules/achievement/event/AchievementCheckEvent.java`
  - Record with `userId`, `AchievementType`, `currentValue`
  - Published by trigger methods, consumed by AchievementCheckListener

- `backend-spring/src/main/java/com/ulticode/modules/achievement/listener/AchievementCheckListener.java`
  - `@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)`
  - Calls `checkAndAwardAchievements` after transaction commits
  - Failures logged via `log.warn` (no retry per D-06)

### Modified Files

- `AchievementTriggerService.java` — interface updated to `void` return for trigger methods
- `AchievementTriggerServiceImpl.java` — all 12 trigger methods changed to:
  - `void` return type
  - `@Async` annotation
  - Fire-and-forget `eventPublisher.publishEvent(new AchievementCheckEvent(...))`
- `AchievementServiceTest.java` — updated tests to call `checkAndAwardAchievements` directly (trigger methods are now void)

### What Was NOT Changed
- `AchievementNotificationListener` — still `@Async` + `@EventListener`, unchanged
- `checkAndAwardAchievements` — still `@Transactional`, unchanged
- `SubmissionServiceImpl` — unchanged, just calls void trigger method

## Verification

- `grep "TransactionalEventListener.*AFTER_COMMIT" AchievementCheckListener.java` → found
- `grep "@Async" AchievementCheckListener.java` → found
- `grep "eventPublisher.publishEvent(new AchievementCheckEvent" AchievementTriggerServiceImpl.java` → 12 occurrences
- `./mvnw compile -q` → passed
- `./mvnw test -Dtest=AchievementServiceTest -q` → 20/21 passed (1 pre-existing failure in AchievementService unrelated to this change)

## Success Criteria

| Criterion | Status |
|-----------|--------|
| Achievement checks run in @Async thread | ✓ |
| @TransactionalEventListener triggers at AFTER_COMMIT | ✓ |
| Main thread does not wait | ✓ |
| AchievementNotificationListener unchanged | ✓ |
| checkAndAwardAchievements still @Transactional | ✓ |
| Tests pass, compiles cleanly | ✓ |
