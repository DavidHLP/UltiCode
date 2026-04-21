# Phase 30: WebSocket Push Wiring - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-21
**Phase:** 30-websocket-push-wiring
**Areas discussed:** WebSocket push integration, AchievementNotificationListener fix, Payload design

---

## WebSocket Push Integration

| Option | Description | Selected |
|--------|-------------|----------|
| Use RealtimeService.sendNotification() | Wraps SimpMessagingTemplate, already exists | ✓ |
| Direct SimpMessagingTemplate injection | More verbose, not following existing pattern | |
| Create new WebSocket pusher service | Overkill — RealtimeService already handles this | |

**User's choice:** Auto-selected via --auto mode
**Notes:** Existing RealtimeService already has `sendNotification(userId, notification)` that sends to `/queue/notification`. No new infrastructure needed.

---

## AchievementNotificationListener Fix

| Option | Description | Selected |
|--------|-------------|----------|
| Add RealtimeService.sendNotification() call | Adds WebSocket push to existing listener | ✓ |
| Create separate achievement pusher | Unnecessary indirection | |
| Push from event publisher instead | Would require event structure change | |

**User's choice:** Auto-selected via --auto mode
**Notes:** Listener already creates notification in DB. Simply add a second call to push via WebSocket with BadgeEarnedPayload.

---

## Payload Design

| Option | Description | Selected |
|--------|-------------|----------|
| NotificationPayload + BadgeEarnedPayload | Both records already exist with factory methods | ✓ |
| Generic map payload | Loses type safety, not idiomatic | |
| Create unified notification payload | Overcomplicated, the two record types are already distinct | |

**User's choice:** Auto-selected via --auto mode
**Notes:** NotificationPayload for general notifications, BadgeEarnedPayload for achievements. Both exist with proper factory methods.

---

## Error Handling Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Fire-and-forget with logging | WebSocket failure doesn't break notification persist | ✓ |
| Fail the notification on WS error | Overly strict — WebSocket is best-effort | |
| Retry on failure | Not needed for real-time notifications | |

**User's choice:** Auto-selected via --auto mode
**Notes:** WebSocket push is best-effort delivery. Log failures but don't fail the notification creation.

---

## Claude's Discretion

- Exact exception handling strategy for WebSocket push failures
- Test mocking approach (Mockito vs manual)

---

## Deferred Ideas

None.

