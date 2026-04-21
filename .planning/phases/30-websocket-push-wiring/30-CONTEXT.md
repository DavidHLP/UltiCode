# Phase 30: WebSocket Push Wiring - Context

**Gathered:** 2026-04-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire `NotificationServiceImpl.createNotification()` to push notifications via `RealtimeService.sendNotification()`. Fix `AchievementNotificationListener` to also push via WebSocket when achievements are earned. All notification creations push to connected clients via WebSocket.

</domain>

<decisions>
## Implementation Decisions

### WebSocket push integration
- **D-01:** `NotificationServiceImpl.createNotification()` must call `RealtimeService.sendNotification(userId, payload)` after persisting the notification
- **D-02:** Push payload uses `NotificationPayload` record — already exists at `modules/websocket/notification/dto/NotificationPayload.java`
- **D-03:** Destination: `USER_QUEUE_NOTIFICATION` (`/queue/notification`) — already defined in `WebSocketConstants`
- **D-04:** No new WebSocket endpoints required — existing STOMP infrastructure handles delivery

### AchievementNotificationListener fix
- **D-05:** `AchievementNotificationListener` must ALSO call `RealtimeService.sendNotification()` with a `BadgeEarnedPayload` after calling `notificationService.createNotification()`
- **D-06:** `BadgeEarnedPayload` record already exists at `modules/websocket/notification/dto/BadgeEarnedPayload.java`

### Payload design
- **D-07:** Notification payload includes: event type, notification ID, type, title, body, metadata map, createdAt, read flag
- **D-08:** Achievement payload includes: event, badgeId, badgeName, badgeDescription, badgeIcon, badgeTier, userId, earnedAt

### RealtimeService dependency
- **D-09:** `NotificationServiceImpl` needs `RealtimeService` injected — constructor injection follows existing pattern
- **D-10:** No cyclic dependency: `RealtimeService` uses `SimpMessagingTemplate`, not `NotificationService`

### Error handling
- **D-11:** WebSocket push failure is logged but does not fail the notification creation (fire-and-forget with logging)
- **D-12:** Achievement notification failure is caught and logged (existing try/catch already present)

### Testing scope
- **D-13:** Unit test for `NotificationServiceImpl.createNotification()` verifies WebSocket push is called
- **D-14:** Unit test for `AchievementNotificationListener` verifies both DB persist and WebSocket push

### Claude's Discretion
- Exact exception handling strategy for WebSocket push failures
- Test mocking approach (Mockito vs manual)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### WebSocket infrastructure
- `backend-spring/src/main/java/com/ulticode/modules/websocket/service/RealtimeService.java` — Existing realtime service with `sendNotification(userId, notification)` method
- `backend-spring/src/main/java/com/ulticode/modules/websocket/constants/WebSocketConstants.java` — `USER_QUEUE_NOTIFICATION` destination constant
- `backend-spring/src/main/java/com/ulticode/modules/websocket/notification/dto/NotificationPayload.java` — Notification payload record
- `backend-spring/src/main/java/com/ulticode/modules/websocket/notification/dto/BadgeEarnedPayload.java` — Badge earned payload record
- `backend-spring/src/main/java/com/ulticode/modules/websocket/notification/NotificationWebSocketHandler.java` — Existing notification handler

### Notification service
- `backend-spring/src/main/java/com/ulticode/modules/notification/service/impl/NotificationServiceImpl.java` — Service to wire; `createNotification()` at line 158

### Achievement listener
- `backend-spring/src/main/java/com/ulticode/modules/achievement/listener/AchievementNotificationListener.java` — Listener to fix; currently only calls `notificationService.createNotification()` without WebSocket push

### Existing patterns
- `backend-spring/src/main/java/com/ulticode/modules/websocket/service/RealtimeService.java` lines 148-152 — `emitSubmissionResult()` pattern for user-targeted push

</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- `RealtimeService.sendNotification(userId, notification)` — already exists, sends to `/queue/notification`
- `NotificationPayload` record — already has factory methods: `of()`, `system()`, `mention()`, `reply()`
- `BadgeEarnedPayload` record — already has factory methods: `of()`, `bronze()`, `silver()`, `gold()`
- `WebSocketConstants.USER_QUEUE_NOTIFICATION` — destination constant already defined

### Established Patterns
- User-targeted push: `messagingTemplate.convertAndSendToUser(userId, USER_QUEUE_NOTIFICATION, payload)` — used in `RealtimeService.emitSubmissionResult()`
- `NotificationWebSocketHandler` handles subscription but delivery comes through `RealtimeService`
- Achievement listener is `@Async` with try/catch — existing error handling pattern

### Integration Points
- `NotificationServiceImpl` needs `RealtimeService` injected
- `AchievementNotificationListener` needs `RealtimeService` injected
- No new endpoints or channels needed

</codebase_context>

<specifics>
## Specific Ideas

No specific product references — standard WebSocket push implementation following existing RealtimeService patterns.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 30-websocket-push-wiring*
*Context gathered: 2026-04-21*
