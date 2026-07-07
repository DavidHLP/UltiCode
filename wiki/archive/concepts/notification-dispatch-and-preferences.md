---
title: Notification Dispatch & Preferences
type: concept
tags: [notification, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/modules/notification/
  - init-db/migrations/V20260602_120000__Create_All_Tables.sql
  - init-db/migrations/V20260613120000__Create_Notification_Delivery_Ledger.sql
  - backend-spring/src/main/java/com/ulticode/common/config/FeatureFlagsProperties.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminNotificationServiceImpl.java
aliases: [ADR-004, Notification Preferences, Notification Dispatcher]
---

# Notification Dispatch & Preferences

> [!note] This page is the landed record of **ADR-004 — Notification System**
> The ADR rationale was previously scattered across 10+ source-file Javadoc
> blocks (§2.1, §2.3, M4a–M4d, F9, findings #7). It is consolidated here. Per
> [SCHEMA §3](../SCHEMA.md) the project keeps no separate `decisions/` dir — an
> ADR folds into `concepts/`.

## The problem
Many event sources (follow, submission, achievement, contest, comment, admin
broadcast) must reach users over many channels (in-app row, WebSocket push,
email), and users must be able to opt out of non-essential categories. A naive
per-caller `notificationMapper.insert` fails three ways: (a) retries and
multi-replica deploys double-deliver; (b) one channel failure kills the
others; (c) there is no chokepoint to enforce per-category opt-out, so a user
who turned off marketing still gets spammed.

## The decision
A central **typed dispatcher** with a preference gate, an idempotency ledger,
and failure-isolated channel fan-out.

**Sealed intent envelope (§2.1).** Every send is a `NotificationIntent` record
exposing `userId()`, `category()`, `intentId()`. The sealed `permits` clause is
the exhaustive type set; adding one is a source-incompatible change so a new
type is planned with its channels together. Six concrete intents today:
`SubmissionCompletedIntent`, `AchievementEarnedIntent`, `ContestStartingIntent`,
`FollowReceivedIntent`, `CommentReplyIntent`, `SystemAlertIntent`.

**Central fan-out (§2.3).** `NotificationDispatcher.dispatch(intent)`:
1. **Preference gate** — look up `NotificationPreference`; if the category
   flag is `false`, drop the intent immediately (no ledger row = "user opted
   out").
2. For each registered channel in bean order: `ledger.tryClaim(intentId,
   channelId)` (atomic INSERT — 0 means already delivered, skip),
   `channel.supports(intent)`, then `channel.send(intent)`. Success →
   DELIVERED; exception → FAILED + counter, **never rethrow** (failure
   isolation so one bad channel can't poison the rest).

**Idempotency ledger.** `notification_delivery_ledger` with
`uk(intent_id, channel_id)` makes retries and multi-replica sends safe
(exactly-once per channel). A `NotificationLedgerReaper` trims old rows. See
[[concepts/notification-idempotency]].

**Preference categories — 1:1 with flags.**

| Category | Preference flag | DDL default | Meaning |
|---|---|---|---|
| `COMMUNICATION` | `communication` | `true` | replies, mentions, follows |
| `MARKETING` | `marketing` | `false` (opt-IN) | promotions, campaigns |
| `SECURITY` | `security` | `true` | password change, suspicious login |
| `SYSTEM` | `system` | `true` | broadcast announcements |

The defaults are kept identical in **four** places so they can't drift: the
DDL, the `NotificationServiceImpl` `DEFAULT_*` constants, the typed
dispatcher's missing-row fallback, and the legacy service's fallback. A missing
preference row resolves to marketing=false, others=true. `CONTEST` was removed
(M4d-1 finding #7): every caller already used `SYSTEM`, so the dead branch was
indistinguishable from a typo.

**Admin broadcast honors preferences.** `AdminNotificationService` force-
delivers `SECURITY`/`SYSTEM` (announcements must reach) but filters
`MARKETING`/`COMMUNICATION` recipients by opt-out **before** `batchInsert`,
applying the same missing-row defaults as the dispatcher — so the manual and
event-driven paths share identical preference semantics.

## Where it lives
- `notification/dispatcher/NotificationDispatcher.java` — central fan-out + gate.
- `notification/intent/` — the sealed intent records + factories.
- `notification/channel/` — `InAppNotificationChannel`, `WebSocketNotificationChannel`,
  `EmailNotificationChannel`.
- `notification/ledger/` — `NotificationDeliveryLedger`, `tryClaim`, `NotificationLedgerReaper`.
- `notification/entity/NotificationPreference.java` + `notification_preferences` table.
- `notification/service/impl/NotificationDispatchServiceImpl.java` — **legacy**
  String-category path (`@Deprecated`, removed at M4d).
- `admin/service/impl/AdminNotificationServiceImpl.java` — preference-aware broadcast.
- `common/config/FeatureFlagsProperties.java` — `useNotificationIntent` flag.

## Trade-offs
- **Two dispatch paths during migration.** The legacy `NotificationDispatchService`
  (String category) and the typed `NotificationDispatcher` coexist behind
  `FeatureFlags.useNotificationIntent` (**default `false`** → production runs the
  legacy path). The preference check is intentionally duplicated in both rather
  than extracted into shared code — extraction would couple callers to a path
  slated for deletion at M4d. The new path activates only after `ContestStarting`/
  `CommentReply` migrate and integration tests pass; then legacy is deleted and
  the duplicate collapses. The three live callers (follow, submission,
  achievement) already inject both and branch on the flag.
- **Reserved channels with no automatic producer.** `SystemAlertIntent`
  (`SECURITY`) and the `MARKETING` category have full channel support but **no
  automatic producer**: the auth module has no suspicious-login / device
  detection, and there is no marketing-automation module. The only live
  `SECURITY`/`MARKETING` emitter is admin broadcast (manual). Wiring automatic
  triggers (e.g. new-device login → `SECURITY`) is future feature work, not a
  defect — these intents are Javadoc-tagged `reserved` so they aren't mistaken
  for dead code.
- **Preference bypass is explicit, not accidental.** `createNotification` /
  `createNotificationRowOnly` persist unconditionally; only the dispatcher
  enforces opt-out. Both carry a "Does NOT consult NotificationPreference"
  Javadoc warning so business code dispatches instead of calling them directly.
  Admin broadcast is the sanctioned bypass for force-delivered `SECURITY`/
  `SYSTEM`.

## Related
[[entities/notification]] · [[concepts/notification-idempotency]] · [[concepts/security-invariants]] · [[overview/judging-pipeline-overview]]
