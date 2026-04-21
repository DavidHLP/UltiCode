# Phase 33: Submission Result Trigger - Context

**Gathered:** 2026-04-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire `SubmissionServiceImpl.updateSubmissionResult()` to `NotificationService.createNotification()`. When a user's submission is judged (AC/WA/TLE/etc.), the user receives an in-app notification persisted to DB and pushed via WebSocket. Depends on Phase 30 (WebSocket push wiring).

</domain>

<decisions>
## Implementation Decisions

### Submission notification creation
- **D-01:** Call `notificationService.createNotification()` from `SubmissionServiceImpl.updateSubmissionResult()` after updating the submission status — reuse the Phase 30 pattern where `createNotification()` handles both DB persist and WebSocket push
- **D-02:** Call notification creation after the status update (fire-and-notify, consistent with D-11 of Phase 30)

### Notification content
- **D-03:** `type = "SUBMISSION"` — already exists in `NotificationType` enum
- **D-04:** `category = "SYSTEM"` — system-generated event, consistent with achievement notifications
- **D-05:** `title = "Submission judged: {status}"` — status is Accepted, Wrong Answer, Time Limit Exceeded, etc.
- **D-06:** `body = ""` (empty) — title is self-explanatory; detailed info available in submission detail page
- **D-07:** `link = "/submissions/{submissionId}"` — deep link to submission detail
- **D-08:** `metadata` includes: `submissionId`, `problemId`, `problemTitle`, `status`, `isAccepted` (boolean)

### When to send
- **D-09:** Send notification for ALL judged statuses (Accepted, Wrong Answer, TLE, MLE, Runtime Error, Compile Error, etc.) — not just Accepted
- **D-10:** Do NOT send notification for "Pending" or "Judging" statuses — only terminal/terminal-ish states

### Error handling
- **D-11:** Notification creation failure is caught and logged — does not affect the submission result update (fire-and-notify, consistent with D-11 Phase 30)

### Testing scope
- **D-12:** Unit test for `updateSubmissionResult()` verifies notification is created for terminal statuses

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Submission service
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java` — Where to add the notification call; `updateSubmissionResult()` at line 222

### Notification service (Phase 30 wired)
- `backend-spring/src/main/java/com/ulticode/modules/notification/service/impl/NotificationServiceImpl.java` — Phase 30-wired service; `createNotification()` handles persist + WebSocket push
- `backend-spring/src/main/java/com/ulticode/modules/notification/service/NotificationService.java` — Service interface

### Notification types
- `backend-spring/src/main/java/com/ulticode/modules/notification/entity/enums/NotificationType.java` — `SUBMISSION` type already defined
- `backend-spring/src/main/java/com/ulticode/modules/notification/entity/enums/NotificationCategory.java` — `SYSTEM` category to use for submission notifications

### WebSocket wiring (Phase 30)
- `.planning/phases/30-websocket-push-wiring/30-CONTEXT.md` — D-11: WebSocket push failure logged but does not fail notification creation

### Follow notification pattern (Phase 31)
- `.planning/phases/31-follow-notification-trigger/31-CONTEXT.md` — Fire-and-notify pattern; notification failure caught and logged

### Contest reminder pattern (Phase 32)
- `.planning/phases/32-contest-reminder-trigger/32-CONTEXT.md` — Fire-and-notify pattern for reference

</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- `NotificationService.createNotification(userId, type, category, title, body, link)` — already wired with RealtimeService from Phase 30; handles persist + WebSocket push
- `NotificationType.SUBMISSION` — already defined in the enum
- `SubmissionServiceImpl` already has `RealtimeService` injected (used for `markDirty` in contest recording)

### Established Patterns
- Fire-and-notify: notification failures are caught and logged, don't affect the main operation
- `updateSubmissionResult()` is called by the judge callback — it already has userId, submissionId, status, runtime, memory
- All notification triggers use the same `createNotification()` pattern (Phases 30, 31, 32)

### Integration Points
- `SubmissionServiceImpl` needs `NotificationService` injected (currently has `AchievementTriggerService`, `RealtimeService`, etc.)
- No new endpoints, no WebSocket changes needed (Phase 30 handled)
- No schema changes required

</codebase_context>

<specifics>
## Specific Ideas

No specific product references — standard submission result notification implementation following existing patterns from Phases 30-32.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 33-submission-result-trigger*
*Context gathered: 2026-04-21*
