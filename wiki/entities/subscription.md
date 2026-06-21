---
title: Subscription
type: entity
tags: [subscription, billing, vip]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/subscription/
  - backend-spring/src/main/java/com/ulticode/modules/subscription/controller/SubscriptionController.java
  - backend-spring/src/main/java/com/ulticode/modules/subscription/controller/UserSubscriptionController.java
  - backend-spring/src/main/java/com/ulticode/modules/subscription/entity/Subscription.java
aliases: [订阅, 会员]
---

# Subscription

Paid-tier entitlements (VIP / premium) — a single `subscriptions` row per
active plan. Renewals write a new row with the next plan; cancellation
soft-deletes via `@TableLogic`.

## Responsibility

Track who has paid, what plan, when it expires, and whether auto-renew is on.
The module is the bookkeeping layer for paid features; feature-gate checks
(read elsewhere) consult `getActiveSubscription(userId)`.

## Key tables

| Table | Purpose |
|-------|---------|
| `subscriptions` | one row per subscription instance; `plan` and `status` are raw `String` (not enums at the DB layer); soft-delete via `is_deleted` (`@TableLogic`) |

Plan values: `FREE`, `PREMIUM_MONTHLY`, `PREMIUM_YEARLY`. Status values:
`ACTIVE`, `EXPIRED`, `CANCELLED`, `PENDING`.

## Controllers

Two controllers, two surfaces:

| Controller | Prefix | Notes |
|------------|--------|-------|
| `SubscriptionController` | `/admin/subscriptions` | admin-only; `GET /{id}`, `GET /user/{userId}` |
| `UserSubscriptionController` | `/subscription` | self-service; the user-facing subscribe / cancel / renew flow |

`UserSubscriptionController` is the larger of the two — it owns the public
purchase and renewal path. `SubscriptionController` is read-only for admins.

## Flow

user picks a plan → `POST /subscription` → write `subscriptions` row (status
`PENDING`) → payment callback flips to `ACTIVE` with `startedAt` /
`expiresAt`. Cancellation soft-deletes via `is_deleted` and sets
`cancelledAt`. The `active subscription` query is `status = ACTIVE AND
expiresAt > now() AND is_deleted = 0`.

## Source files

- `backend-spring/.../modules/subscription/` (controller, service + impl,
  entity, dto, mapper, annotation, constants).

## Cross-links

- [[entities/user]] (entitlement checks live in user-facing services)
- [[overview/backend-modules-overview]]

## Gotchas

- `plan` and `status` are stored as raw `String` — there is no DB enum. Adding
  a new plan needs a code change in the service-side allow-list *and* a
  migration that documents the value, but no DDL change.
- `@TableLogic` on `is_deleted` means `BaseMapper` queries automatically
  filter soft-deleted rows; a manual SQL count that ignores this will
  over-count active subs.
- `getActiveSubscription` must check `expiresAt > now()`, not just
  `status = ACTIVE` — a row can be `ACTIVE` but past `expiresAt` (race
  between expiry sweep and read).
- The payment callback path is not in this module's controllers — the
  callback is a webhook from a payment provider; the controller is the
  user-facing surface only.
