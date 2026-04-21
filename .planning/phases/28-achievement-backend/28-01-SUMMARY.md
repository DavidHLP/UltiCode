---
phase: 28
plan: 28-01
subsystem: achievement-backend
tags: [achievement, websocket, async, backend]
key-files:
  created:
    - "backend-spring/src/main/java/com/ulticode/modules/achievement/listener/AchievementNotificationListener.java"
    - "backend-spring/src/main/java/com/ulticode/modules/achievement/dto/AchievementProgressVO.java"
  modified:
    - "backend-spring/src/main/java/com/ulticode/modules/achievement/constants/AchievementType.java"
    - "backend-spring/src/main/java/com/ulticode/modules/achievement/service/AchievementTriggerService.java"
    - "backend-spring/src/main/java/com/ulticode/modules/achievement/service/impl/AchievementTriggerServiceImpl.java"
    - "backend-spring/src/main/java/com/ulticode/modules/achievement/service/AchievementService.java"
    - "backend-spring/src/main/java/com/ulticode/modules/achievement/service/impl/AchievementServiceImpl.java"
    - "backend-spring/src/main/java/com/ulticode/modules/user/controller/UserController.java"
    - "backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java"
    - "backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java"
    - "backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java"
    - "backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestParticipantMapper.java"
metrics:
  files_created: 2
  files_modified: 10
  commits: 1
---

## Summary

Completed achievement backend system: async WebSocket notification listener, progress tracking endpoint, new achievement types (FIRST_PROBLEM, LANGUAGE_SOLVED), category validation, and trigger wiring.

### What was built

- **AchievementNotificationListener** — @Async @EventListener that consumes AchievementEarnedEvent and pushes WebSocket notifications via NotificationService.sendBadgeEarned()
- **AchievementProgressVO** — DTO with currentValue, targetValue, percentage, nextMilestone for unearned achievements
- **Progress endpoint** — GET /users/me/achievements/progress via UserController
- **New triggers** — onFirstProblemSolved and onLanguageMilestone in AchievementTriggerService
- **Category validation** — validates against [problems, contests, social, streaks, special]
- **Trigger wiring** — SubmissionServiceImpl and ContestServiceImpl now call achievement triggers

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| All | 82240d46b | feat(phase-28): achievement backend completion |

## Deviations

None — implementation matched plan.

## Self-Check

**PASSED**

- Backend compiles without error (`./mvnw compile -q`)
- AchievementNotificationListener has @Async and @EventListener
- AchievementType has FIRST_PROBLEM and LANGUAGE_SOLVED
- UserController has /users/me/achievements/progress endpoint
- Category validation throws BusinessException for unknown categories
- All triggers wired in SubmissionServiceImpl and ContestServiceImpl
