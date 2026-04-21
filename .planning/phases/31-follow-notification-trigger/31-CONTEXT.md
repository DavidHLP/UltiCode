# Phase 31: Follow Notification Trigger - Context

**Gathered:** 2026-04-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Add follow notification trigger to `FollowServiceImpl.follow()`. When User A follows User B, User B receives an in-app notification persisted to DB and pushed via WebSocket. Depends on Phase 30 (WebSocket push wiring).

</domain>

<decisions>
## Implementation Decisions

### Follow notification creation
- **D-01:** Call `notificationService.createNotification()` from `FollowServiceImpl.follow()` after successful follow — reuse the Phase 30 pattern where `createNotification()` handles both DB persist and WebSocket push
- **D-02:** Call notification creation synchronously (not `@Async`) so caller gets confirmation only after notification is persisted — consistent with Phase 30's fire-and-forget-within-createNotification pattern
- **D-03:** No notification on unfollow — only follow creates a notification

### Notification content
- **D-04:** `type = "FOLLOW"` — already exists in `NotificationType` enum
- **D-05:** `category = "social"` — follows the social category established in Phase 28 achievement categories (D-07)
- **D-06:** `title = "{username} followed you"` — username of the follower
- **D-07:** `body = ""` — empty body is acceptable for follow notifications (title is self-explanatory)
- **D-08:** `link = "/profile/{followerUsername}"` — deep link to follower's profile for click-through
- **D-09:** `metadata` includes: `followerUsername`, `followerAvatar` (string URL) — enables frontend to display avatar without additional API call

### Idempotency
- **D-10:** Notification sent only on first follow (when `followMapper.insertIdempotent()` actually inserts) — re-following does NOT send another notification

### RealtimeService dependency
- **D-11:** Phase 30 already wired `NotificationServiceImpl` with `RealtimeService` — no additional WebSocket wiring needed
- **D-12:** `FollowServiceImpl` needs `NotificationService` injected

### Error handling
- **D-13:** Notification creation failure is caught and logged — follow action itself succeeds regardless of notification failure (fire-and-notify)
- **D-14:** Follow notification failures do not roll back the follow itself — consistent with Phase 30's D-11 (WebSocket push failure logged but does not fail notification creation)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Follow system
- `backend-spring/src/main/java/com/ulticode/modules/follow/service/impl/FollowServiceImpl.java` — Where to add the notification call (around line 47-52, after idempotent insert)
- `backend-spring/src/main/java/com/ulticode/modules/follow/service/FollowService.java` — Service interface

### Notification service
- `backend-spring/src/main/java/com/ulticode/modules/notification/service/impl/NotificationServiceImpl.java` — Phase 30-wired service; `createNotification()` at lines 161-190 handles persist + WebSocket push
- `backend-spring/src/main/java/com/ulticode/modules/notification/service/NotificationService.java` — Service interface

### Notification types
- `backend-spring/src/main/java/com/ulticode/modules/notification/entity/enums/NotificationType.java` — `FOLLOW` type already defined at line 11

### Notification entity
- `backend-spring/src/main/java/com/ulticode/modules/notification/entity/Notification.java` — Entity with type, category, title, body, link, metadata fields

### Achievement backend patterns
- `.planning/phases/28-achievement-backend/28-CONTEXT.md` — `D-07` established "social" as a notification category

### WebSocket wiring (Phase 30)
- `.planning/phases/30-websocket-push-wiring/30-CONTEXT.md` — D-11: WebSocket push failure logged but does not fail notification creation

</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- `NotificationService.createNotification(userId, type, category, title, body, link)` — already wired with RealtimeService from Phase 30; handles both DB persist and WebSocket push
- `NotificationType.FOLLOW` — already defined in the enum
- `UserSummaryDTO` has `username` and `avatar` fields that can be used to build notification metadata

### Established Patterns
- Follow is idempotent: `followMapper.exists()` check before `followMapper.insertIdempotent()` — notification should only fire on first follow
- `triggerFollowerAchievement()` is `@Async` — follow notification is synchronous (not async) to keep the follow action responsive
- Achievement notification uses try/catch around async trigger — similar fire-and-notify pattern should apply here

### Integration Points
- `FollowServiceImpl` needs `NotificationService` injected (constructor injection)
- No new endpoints, no WebSocket changes needed (Phase 30 handled)
- Follower's username needed for notification title — available from `User target` already fetched in `follow()` method

</codebase_context>

<specifics>
## Specific Ideas

No specific product references — standard follow notification implementation following existing patterns from Phase 28 and Phase 30.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 31-follow-notification-trigger*
*Context gathered: 2026-04-21*
