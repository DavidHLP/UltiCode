# Phase 28: Achievement Backend - Context

**Gathered:** 2026-04-21
**Status:** Ready for planning

## Phase Boundary

Achievement system is complete with triggers, progress tracking, categories, and real-time notifications.

## Requirements (from ROADMAP.md)

| REQ | Description | Phase |
|-----|-------------|-------|
| ACHV-01 | Achievement Trigger Completion | Phase 28 |
| ACHV-02 | Achievement Progress Indicators | Phase 28 |
| ACHV-03 | Achievement Categories and Filtering | Phase 28 |
| ACHV-04 | Real-time Achievement Notification | Phase 28 |

## Implementation Decisions

### Async Event Wiring
- **D-01:** `AchievementEarnedEvent` published via `ApplicationEventPublisher` → `@Async` `@EventListener` method dispatches to `NotificationService.sendBadgeEarned(userId, payload)` → WebSocket push via `SimpMessagingTemplate`

### Progress Endpoint
- **D-02:** `GET /users/me/achievements/progress` returns current count, percentage, and next milestone target for each unearned achievement
- **D-03:** Progress calculated from `UserAchievement` + current stats (submission counts from SubmissionMapper, follow counts from FollowMapper)

### Missing Achievement Triggers
- **D-04:** Add `FIRST_PROBLEM` achievement type for first AC problem solved
- **D-05:** Add `LANGUAGE_SOLVED` (or language-specific types) for 1/10/50/100 milestone per language
- **D-06:** AchievementType enum extended; trigger methods added to `AchievementTriggerService`

### Category Filtering
- **D-07:** Categories: `problems`, `contests`, `social`, `streaks`, `special` (per REQUIREMENTS.md)
- **D-08:** `AchievementController.list()` category param validated against known categories; returns 400 if unknown

### WebSocket Notification Payload
- **D-09:** `BadgeEarnedPayload` includes: achievement name, description, icon, tier (1-4), rarity label

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

- `.planning/ROADMAP.md` §Phase 28 — phase goal, success criteria
- `.planning/REQUIREMENTS.md` §Achievement System — ACHV-01 through ACHV-04
- `.planning/STATE.md` — current milestone state
- `backend-spring/src/main/java/com/ulticode/modules/achievement/event/AchievementEarnedEvent.java` — event record
- `backend-spring/src/main/java/com/ulticode/modules/achievement/service/impl/AchievementTriggerServiceImpl.java` — existing triggers
- `backend-spring/src/main/java/com/ulticode/modules/achievement/constants/AchievementType.java` — type enum
- `backend-spring/src/main/java/com/ulticode/websocket/NotificationService.java` — sendBadgeEarned method
- `backend-spring/src/main/java/com/ulticode/modules/achievement/controller/AchievementController.java` — existing endpoints
- `backend-spring/src/main/java/com/ulticode/UlticodeBackendApplication.java` — @EnableAsync confirmed

## Existing Code Insights

### Reusable Assets
- `AchievementEarnedEvent` record — already designed for async publishing
- `NotificationService.sendBadgeEarned(userId, payload)` — WebSocket dispatch ready
- `BadgeEarnedPayload` (in websocket notification dto) — WebSocket payload exists
- `AchievementTriggerService` interface — 10 trigger methods already defined

### Established Patterns
- `ApplicationEventPublisher` for domain events (already in use)
- `@Async` on service methods (FollowServiceImpl.onFollowCountUpdated uses @Async)
- Result/PageResult wrapper for REST responses
- `@RateLimit` annotation on controller endpoints

### Integration Points
- Triggers called from: SubmissionServiceImpl (onProblemSolved), FollowServiceImpl (onFollowCountUpdated), ContestService
- Stats pulled from: SubmissionMapper, FollowMapper, UserStatsMapper
- WebSocket push via: SimpMessagingTemplate → /user/{userId}/queue/notifications

## Specific Ideas

- First-problem trigger: call `onProblemSolved` in SubmissionServiceImpl after successful judging
- Language milestone: store language per submission; aggregate count per language in stats query
- Async event listener: new `@Async` `@EventListener` method in a `@Service` component (not in AchievementTriggerServiceImpl to keep concerns separated)

## Deferred Ideas

None — all ACHV requirements are in scope for Phase 28.

## Folded Todos

None.

---

*Phase: 28-achievement-backend*
*Context gathered: 2026-04-21*
