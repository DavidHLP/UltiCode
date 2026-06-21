---
title: Notification Idempotency
type: concept
tags: [notification, idempotency]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/notification/ledger/
  - init-db/migrations/V20260613120000__Create_Notification_Delivery_Ledger.sql
aliases: [通知幂等]
---

# Notification Idempotency

## The problem
Delivery transports (WebSocket reconnects, poll retries) are **at-least-once**.
Without dedup, a user sees the same notification multiple times; counters and
"unread" state drift.

## The decision
Separate **intent** from **delivery**, with a **delivery ledger** as the dedup
authorities.

- A producer creates a `Notification` (intent) plus a deterministic **ledger key**.
- Delivery checks the `notification_delivery_ledger`: key already present → skip;
  absent → deliver and record. Same key seen twice → delivered once.
- The ledger row is written **in the same transaction** as the intent.

## Where it lives
- `notification/ledger/entity/NotificationDeliveryLedger`.
- Migration `V20260613120000__Create_Notification_Delivery_Ledger`.

## Trade-offs
- One ledger row per delivery — cheap relative to duplicate-UX cost.
- Preferences gate *delivery*, not *intent* creation — intents stay for audit.
- Push ([[entities/websocket]]) stays simple; correctness lives in the ledger.

## Related
[[entities/notification]] · [[entities/websocket]] ·
[[concepts/exactly-once-judging]] (sibling idempotency pattern) ·
[[overview/judging-pipeline-overview]]
