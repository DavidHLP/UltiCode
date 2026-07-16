---
title: Notification
type: entity
tags: [notification, core, type/entity]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/modules/notification/
  - init-db/migrations/V20260613120000__Create_Notification_Delivery_Ledger.sql
aliases: [通知]
---

# Notification

How the system tells a user something happened — judged submission, reply, badge,
moderation action, contest signal. Built on an **intent + delivery ledger** so
at-least-once delivery becomes exactly-once observation.

> Push transport: [[entities/websocket]].

## Responsibility

Owns notification creation (as an **intent**), per-user preferences, delivery
through a deduplicating **ledger**, and real-time push.

## Key tables

| Table | Purpose |
|-------|---------|
| `notifications` | the notification row (type, category, payload, refs) |
| `notification_preferences` | per-user on/off by category/system |
| `notification_delivery_ledger` | exactly-once delivery record (dedup key) |

## Enums

`NotificationType` · `NotificationCategory`.

## Key flow (intent → ledger → push)

```
producer (judging/forum/moderation/…)
   │  creates Notification (intent) + ledger key
   ▼
delivery attempt → ledger check (already delivered?)
   │  no → deliver, mark ledger
   │  yes → skip (idempotent)
   ▼
websocket push to open tab  +  persisted for later read
```

The ledger turns at-least-once transport (WebSocket reconnects, retries) into
exactly-once user-visible delivery.

## Key flows (user-facing)

- List/read/unread via `NotificationController` → `/notifications`.
- Admin view `/admin/notifications`.
- Preferences updated by category; the `system` column was renamed in
  `V20260611120000__Rename_Notification_Pref_System_Column`.
- Soft delete added in `V20260611130000__Add_Notifications_Is_Deleted`.

## Source files

- `backend-spring/.../modules/notification/` (controller, service, entity, ledger/).
- `backend-spring/.../modules/notification/ledger/entity/NotificationDeliveryLedger.java`.
- `init-db/migrations/V20260613120000__Create_Notification_Delivery_Ledger.sql`.

## Cross-links

- [[entities/websocket]] · [[entities/email]] · [[entities/submission]]
- [[overview/judging-pipeline-overview]] (notify stage)

## Gotchas

- For a claimed delivery, the ledger `tryClaim` row is written before the channel
  fan-out so a retry sees the claim and skips; the claim is the idempotency fence
  (there is no separate "intent + ledger same transaction" rule).
- Preferences gate the whole delivery — an opted-out category returns before any
  ledger or notification row is written, so a suppressed intent leaves **no** audit
  row (matches `NotificationDispatcher.isCategoryEnabled`, ADR-004 §2.3).
- Email ([[entities/email]]) and in-app notification are separate channels; an
  event may fan out to both.
