---
title: Realtime Push Port Series
type: concept
tags: [websocket, notification, achievement, queue, contest, admin, architecture, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/modules/websocket/port/adapter/
  - backend-spring/src/main/java/com/ulticode/modules/notification/port/NotificationPushPort.java
  - backend-spring/src/main/java/com/ulticode/modules/achievement/port/BadgePushPort.java
  - backend-spring/src/main/java/com/ulticode/modules/queue/port/SubmissionResultPushPort.java
  - backend-spring/src/main/java/com/ulticode/modules/contest/port/ContestRankingMarkDirtyPort.java
  - backend-spring/src/main/java/com/ulticode/modules/contest/port/ContestStatusPushPort.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/port/ContestAnnouncementPushPort.java
aliases: [ADR-0009, RealtimeService Collapse, Per-Consumer Push Ports]
---

# Realtime Push Port Series

> [!note] This page is the landed record of **ADR-0009 — Realtime Push
> Seam Inversion (RealtimeService &rarr; per-consumer ports)**. Per
> [SCHEMA §3](../SCHEMA.md) the project keeps no separate
> `decisions/` dir &mdash; an ADR folds into `concepts/`.

## The problem

`com.ulticode.modules.websocket.service.RealtimeService` had grown
into a ~230-line god service with 10+ emit/send methods and 2
`@Scheduled` tasks. Six non-websocket modules reached across into it
directly:

| Consumer | Module | Call-sites |
|---|---|---|
| `NotificationServiceImpl` | notification | 1 (legacy `createNotification` path) |
| `AchievementTriggerServiceImpl` | achievement | 1 |
| `AchievementNotificationListener` | achievement | 1 (legacy branch) |
| `JudgeWorkerProcessor` | queue | 1 (verdict push) |
| `ContestSubmissionAdapter` | contest | 1 (mark-dirty) |
| `ContestScheduler` | contest | 3 (status x2, mark-dirty x1) |
| `AdminContestServiceImpl` | admin | 1 (announcement) |

Plus `WebSocketNotificationChannel` (notification module) called it
6 times across 6 intent types in a single switch.

Every consumer used exactly **one** of `RealtimeService`'s 10 methods.
The interface was nearly as complex as the implementation (no
leverage), no seam existed between the consumer's domain meaning and
the WebSocket transport, and the only branchless logic (the ranking
throttle + flush + cleanup `@Scheduled` pair) was hidden behind the
same facade as a dozen one-line pass-throughs.

The codebase-design rule "one adapter = hypothetical seam, two = real
seam" was met by a wide margin: six adapters, and the producer-side
abstraction for one of the consumers (`NotificationChannel` with
three implementations) already existed &mdash; the consumer-side port
was the missing mirror.

## The decision

Extract six narrow consumer-owned ports, one per consumer module (or
per concern within a module). Each port is owned by the consumer and
implemented by an adapter in `websocket/port/adapter/`. After all
six land, collapse the `RealtimeService` god service:

1. `notification/port/NotificationPushPort` &mdash;
   `pushToUser(userId, NotificationPayload)`
2. `achievement/port/BadgePushPort` &mdash;
   `pushBadgeEarned(userId, BadgeEarnedPayload)`
3. `queue/port/SubmissionResultPushPort` &mdash;
   `emitSubmissionResult(userId, SubmissionResultPayload)`
4. `contest/port/ContestRankingMarkDirtyPort` &mdash;
   `markDirty(contestId)`
5. `contest/port/ContestStatusPushPort` &mdash;
   `emitStatus(contestId, ContestStatus, Instant, Instant, String)`
6. `admin/port/ContestAnnouncementPushPort` &mdash;
   `emitAnnouncement(contestId, AnnouncementPayload)`

Six adapters in `websocket/port/adapter/`, each delegating to
`SimpMessagingTemplate` directly (or, for the ranking case, to a new
`WebSocketContestRankingFlusher` that owns the throttle + flush +
cleanup logic &mdash; the only producer-side component that remains).

Bonus fix: `WebSocketNotificationChannel` (notification module) now
depends on the consumer-owned `NotificationPushPort` and
`BadgePushPort` instead of leaking into the websocket module. This
completes the notification module's self-containment: the only
`modules.websocket.*` symbols it imports are the wire-format DTOs
(shared language by design).

```
notification/port/NotificationPushPort.java              + adapter (Consumer: NotificationServiceImpl, WebSocketNotificationChannel)
achievement/port/BadgePushPort.java                      + adapter (Consumer: AchievementTriggerServiceImpl, AchievementNotificationListener, WebSocketNotificationChannel)
queue/port/SubmissionResultPushPort.java                 + adapter (Consumer: JudgeWorkerProcessor)
contest/port/ContestRankingMarkDirtyPort.java            + adapter → WebSocketContestRankingFlusher (Consumer: ContestSubmissionAdapter, ContestScheduler)
contest/port/ContestStatusPushPort.java                  + adapter (Consumer: ContestScheduler)
admin/port/ContestAnnouncementPushPort.java              + adapter (Consumer: AdminContestServiceImpl)

websocket/notification/WebSocketContestRankingFlusher.java  (ranking throttle + flush + cleanup @Scheduled; only non-adapter producer component)

DELETED: websocket/service/RealtimeService.java
```

## Why per-consumer ports, not one big port

The codebase-design rule "one adapter = hypothetical seam, two = real"
applies here. With six adapters, the seam is real. The choice is:

- **One big port** (`RealtimePushPort` with 10 methods) &mdash; would
  re-couple the consumers, force every consumer to mock all 10
  methods in unit tests, and bring back the
  "interface nearly as complex as the implementation" smell.
- **Per-consumer ports** &mdash; each consumer sees only the one
  method it uses. Tests mock exactly one method. Future wire-format
  evolution (SSE, FCM) touches only the producer-side adapter, not
  the consumer.

The contest module gets two ports
(`ContestRankingMarkDirtyPort` + `ContestStatusPushPort`) because the
two concerns are orthogonal (flag-for-flush vs broadcast status
transition) and might evolve independently &mdash; e.g. the
mark-dirty could move to a Redis pub-sub for horizontal scalability
while the status push stays on STOMP.

## Why `ContestStatusPushPort` uses the contest module's own enum

The contest module's `entity.enums.ContestStatus` has
`{DRAFT, UPCOMING, RUNNING, FINISHED, CANCELLED}`. The websocket
module's `event.ContestStatusEvent.ContestStatus` has
`{UPCOMING, REGISTRATION, RUNNING, ENDED}`. They are different domains
(persistence vs wire format). The port's method signature accepts
the **contest** enum; the adapter maps:

- `RUNNING` &rarr; `wire.RUNNING`
- `FINISHED` &rarr; `wire.ENDED`
- others &rarr; silently skipped (no wire push)

The translation is the only place that knows about both enums. A
future wire-format change (e.g. add `PAUSED`) or a future contest
lifecycle change touches this one file.

## Why `WebSocketContestRankingFlusher` stays in the websocket module

The throttle logic exists only to protect the WebSocket transport
(limit one push per second per contest to avoid flooding STOMP
subscribers). It is a transport concern, not a contest-domain
concern. If a future migration moves the leaderboard off WebSocket
(SSE polling, HTTP long-poll), the throttle becomes irrelevant and
this class disappears. Keeping it in the websocket module matches
its lifetime to its purpose.

## Where it lives

- `websocket/port/adapter/WebSocketNotificationPushAdapter.java` &mdash;
  implements `NotificationPushPort`.
- `websocket/port/adapter/WebSocketBadgePushAdapter.java` &mdash;
  implements `BadgePushPort`.
- `websocket/port/adapter/WebSocketSubmissionResultPushAdapter.java`
  &mdash; implements `SubmissionResultPushPort`.
- `websocket/port/adapter/WebSocketContestRankingMarkDirtyAdapter.java`
  &mdash; implements `ContestRankingMarkDirtyPort`; delegates to the
  flusher.
- `websocket/port/adapter/WebSocketContestStatusPushAdapter.java` &mdash;
  implements `ContestStatusPushPort`; owns the enum translation.
- `websocket/port/adapter/WebSocketContestAnnouncementPushAdapter.java`
  &mdash; implements `ContestAnnouncementPushPort`.
- `websocket/notification/WebSocketContestRankingFlusher.java` &mdash;
  the throttle + flush + cleanup `@Scheduled` pair.
- `DELETED`: `websocket/service/RealtimeService.java` (was 230 LoC,
  now gone).

## Consequences

### Positive

- **Zero cross-module `modules.websocket.*` imports** in non-websocket
  modules, except the wire-format DTOs (the shared language).
- **Six narrow test seams**: each consumer unit-test mocks exactly
  one method on one port, no STOMP broker required.
- **Transport swap path**: SSE / FCM / push-notification transport
  only requires new adapters implementing the existing ports; no
  consumer changes.
- **`RealtimeService` deleted** (was 230 LoC, now gone). The only
  remaining producer-side component is the ranking flusher (~110
  LoC with extensive Javadoc).
- **Notification module is now self-contained** except for DTOs
  &mdash; bonus fix from `WebSocketNotificationChannel` not using
  `RealtimeService` anymore.

### Negative / risks

- **6 ports is more files than 1 god service** &mdash; but each port
  is one method, very narrow. Net LoC is comparable; cohesion is
  far higher.
- **Sister-adapter risk** &mdash;
  `WebSocketNotificationPushAdapter` and `WebSocketBadgePushAdapter`
  both call
  `convertAndSendToUser(userId, USER_QUEUE_NOTIFICATION, payload)`
  with different payload types. The two ports are kept separate
  because the consumer modules are different (`notification` vs
  `achievement`) and the payload types have different domain
  meanings. If the wire format ever unifies, the two adapters can
  collapse into one.
- **The pre-existing `RealtimeServiceTest` had to be deleted** along
  with the service. The new components (6 adapters + flusher) are
  covered by their own unit tests, so coverage is preserved.

## Compatibility

No API changes, no schema changes, no contract changes. The
`/user/queue/notifications`, `/user/queue/submission`,
`/topic/contest/{id}/ranking`, `/topic/contest/{id}/status`,
`/topic/contest/{id}/announcement` STOMP destinations are unchanged.
The frontends (`console/`, `management/`) consume the same DTOs over
the same WebSocket topics. Behavior is byte-for-byte identical.

## Rollout

Four atomic commits in the established `refactor(...)` convention:

1. `refactor(notification): extract NotificationPushPort deep module`
2. `refactor(achievement): extract BadgePushPort deep module`
3. `refactor(realtime): invert 4 more consumer ports (queue/contest/admin)`
4. `refactor(websocket): collapse RealtimeService god service into per-port adapters`

## Related

- [[concepts/submission-contest-port]] &mdash; ADR-0001, the
  port-pattern precedent
- [[concepts/moderation-projection]] &mdash; ADR-0004, the
  per-domain projection pattern this ADR mirrors for the
  realtime-push side
- [[concepts/problem-detail-port]] / [[concepts/admin-user-stats-read-port]] /
  [[concepts/admin-comment-read-port]] &mdash; recent port extractions
  establishing the consumer-owns-interface convention
- [[entities/websocket]] &mdash; entity page covering the WebSocket
  transport &amp; auth model
