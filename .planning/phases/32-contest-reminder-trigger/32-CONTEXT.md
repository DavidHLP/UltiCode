# Phase 32: Contest Reminder Trigger - Context

**Gathered:** 2026-04-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire contest reminder notifications to `NotificationService`. Users who registered for a contest receive notifications 24 hours and 1 hour before the contest starts. A new `@Scheduled` method in `ContestScheduler` queries for upcoming contests and sends reminders via `NotificationService.createNotification()`. Depends on Phase 30 (WebSocket push wiring).

</domain>

<decisions>
## Implementation Decisions

### Scheduling strategy
- **D-01:** New `@Scheduled` method in `ContestScheduler` — separate from the existing status-transition `run()` job (which handles UPCOMING→RUNNING→FINISHED)
- **D-02:** Two reminder checks: one for T-24h (`startTime` within 24-25h from now) and one for T-1h (`startTime` within 1-2h from now) — both in the same `@Scheduled` method, differentiated by time window
- **D-03:** `NotificationService.createNotification()` handles both DB persist and WebSocket push (Phase 30 pattern)

### Notification content
- **D-04:** T-24h notification: `title = "Contest '{contestTitle}' starts in 24 hours"`, `type = "CONTEST_REMINDER"`, `category = "CONTEST"`
- **D-05:** T-1h notification: `title = "Contest '{contestTitle}' starts in 1 hour"`, `type = "CONTEST_REMINDER"`, `category = "CONTEST"`
- **D-06:** `body = ""` (empty) — title is self-explanatory
- **D-07:** `link = "/contest/{contestId}"` — deep link to contest page
- **D-08:** `metadata` includes: `contestId`, `contestTitle`, `startTime` (ISO string)

### Duplicate prevention
- **D-09:** Use `NotificationService.createNotification()` deduplication — the notification service already handles idempotency checks internally; if the exact same notification was already created for this user/contest/time combination, it should not be sent again
- **D-10:** Fallback deduplication key: `notification_key = "{userId}:{contestId}:{reminderType}"` (24h or 1h) — pass as metadata so `createNotification()` can check

### Query strategy
- **D-11:** Query `ContestParticipantMapper` to find all participants for contests with `status = UPCOMING` and `startTime` in the target window
- **D-12:** Iterate participants and call `notificationService.createNotification()` for each (with dedup key in metadata)

### Error handling
- **D-13:** Reminder notification failure is caught and logged — does not affect other reminders or the contest scheduler's main status-transition job

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Contest scheduler (existing)
- `backend-spring/src/main/java/com/ulticode/modules/contest/scheduler/ContestScheduler.java` — Where to add the new @Scheduled reminder method
- `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestSchedulerServiceImpl.java` — Registration service; `registerForContest()` at line 39

### Notification service (Phase 30 wired)
- `backend-spring/src/main/java/com/ulticode/modules/notification/service/impl/NotificationServiceImpl.java` — Phase 30-wired service; `createNotification()` handles persist + WebSocket push
- `backend-spring/src/main/java/com/ulticode/modules/notification/service/NotificationService.java` — Service interface

### Notification types
- `backend-spring/src/main/java/com/ulticode/modules/notification/entity/enums/NotificationType.java` — Check if `CONTEST_REMINDER` type exists (create if not)
- `backend-spring/src/main/java/com/ulticode/modules/notification/entity/enums/NotificationCategory.java` — Check if `CONTEST` category exists (create if not)

### Participant mapper
- `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestParticipantMapper.java` — `findByContestIdAndUserId()` for registration lookups

### WebSocket wiring (Phase 30)
- `.planning/phases/30-websocket-push-wiring/30-CONTEXT.md` — D-11: WebSocket push failure logged but does not fail notification creation

### Follow notification pattern (Phase 31)
- `.planning/phases/31-follow-notification-trigger/31-CONTEXT.md` — D-13: Fire-and-notify pattern, notification creation failure caught and logged

</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- `NotificationService.createNotification(userId, type, category, title, body, link)` — already wired with RealtimeService from Phase 30; handles persist + WebSocket push
- `ContestScheduler` already has `@Scheduled(fixedRate = 10_000)` infrastructure — new method can follow the same pattern
- `ContestParticipantMapper` has `findByContestIdAndUserId()` and `findByUserId()` methods available

### Established Patterns
- Existing `run()` in `ContestScheduler` polls every 10s — new reminder logic should be a separate `@Scheduled` method to keep concerns separated
- Fire-and-notify: notification failures are caught and logged, don't affect main job
- Notification type and category enums already exist for most types — may need to add `CONTEST_REMINDER` type and `CONTEST` category

### Integration Points
- `ContestScheduler` needs `NotificationService` injected (currently only has `ContestMapper`, `RealtimeService`, `RatingCalculationService`)
- No new endpoints needed
- No schema changes required if using existing notification table

</codebase_context>

<specifics>
## Specific Ideas

No specific product references — standard contest reminder notification implementation following existing patterns from Phase 30 and Phase 31.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 32-contest-reminder-trigger*
*Context gathered: 2026-04-21*
