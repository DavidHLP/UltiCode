---
title: Contest Live-Ranking Read Port
type: concept
tags: [contest, websocket, admin, architecture, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/modules/contest/port/ContestLiveRankingReadPort.java
  - backend-spring/src/main/java/com/ulticode/modules/contest/port/adapter/DefaultContestLiveRankingReadAdapter.java
aliases: [ADR-0010, ContestLiveRankingReadPort, Live Ranking Seam]
---

# Contest Live-Ranking Read Port

> [!note] This page is the landed record of **ADR-0010 — Contest
> Live-Ranking Read Port** (`RankingService.getLiveRanking` &rarr;
> `ContestLiveRankingReadPort`). Per [SCHEMA §3](../SCHEMA.md) the
> project keeps no separate `decisions/` dir &mdash; an ADR folds into
> `concepts/`.

## The problem

`com.ulticode.modules.contest.service.RankingService` had three
methods:

| Method | Internal callers (contest module) | Cross-module callers |
|---|---|---|
| `getContestRanking(contestId, page, limit)` | `DefaultContestProjection`, `ContestRankingController` | &mdash; |
| `getUserContestHistory(userId)` | `ContestParticipationController` | &mdash; |
| `getLiveRanking(contestId, limit)` | `ContestRankingController` | `WebSocketContestRankingFlusher` (websocket), `AdminContestServiceImpl.getRankings` (admin) |

Two non-contest modules reached across into `RankingService` to pull
the live leaderboard. That same interface also exposed
`getContestRanking` (paginated snapshot) and `getUserContestHistory`
(user-scoped), neither of which the external callers used. The seam
criterion from codebase-design &mdash; "one adapter = hypothetical
seam, two = real" &mdash; was met: two distinct modules
independently crossed the contest module boundary to call exactly
one method.

ADR-0009 had already collapsed the `RealtimeService` god service into
six push ports and explicitly called out that the read side would be
"the next natural seam" once the push series settled. ADR-0010
closes that loop on the read side.

## The decision

Extract one narrow consumer-visible read port, owned by the contest
module:

```
contest/port/ContestLiveRankingReadPort.java                        (interface)
contest/port/adapter/DefaultContestLiveRankingReadAdapter.java      (default adapter, delegates to RankingServiceImpl)

DELETED FROM RANKING SERVICE INTERFACE:
- getLiveRanking(String, Integer)

KEPT ON RANKING SERVICE INTERFACE (internal-only callers, no leak):
- getContestRanking(String, Integer, Integer)
- getUserContestHistory(String)

CHANGED FROM RANKING SERVICE INTERFACE CONSUMERS:
- WebSocketContestRankingFlusher.java          — inject ContestLiveRankingReadPort
- AdminContestServiceImpl.java                 — inject ContestLiveRankingReadPort
- ContestRankingController.java                — inject ContestLiveRankingReadPort (in addition to RankingService)
```

The port method `readLiveRanking(String, int)` takes a primitive
`int` rather than the legacy `Integer`, and the implementation
clamps the limit to a 200-item hard cap (matching the previous
`RankingServiceImpl.getLiveRanking` behaviour).

`RankingServiceImpl` keeps the actual SQL / sort / limit logic; the
adapter is a one-line delegate. `RankingServiceImpl.readLiveRanking`
is `public` (not on the interface) so the adapter can call it
without exposing the port interface from the impl.

## Where it lives

- `contest/port/ContestLiveRankingReadPort.java` &mdash; interface,
  owned by the contest module.
- `contest/port/adapter/DefaultContestLiveRankingReadAdapter.java`
  &mdash; the default `@Component` adapter; one-line delegate to
  `RankingServiceImpl.readLiveRanking`.
- `contest/service/impl/RankingServiceImpl.java` &mdash; `readLiveRanking`
  exposed as `public` (not on any interface), documented as the
  adapter's delegate target.
- `contest/service/RankingService.java` &mdash; interface slimmed:
  `getLiveRanking` removed, `getContestRanking` + `getUserContestHistory`
  retained for internal-only callers.

## Why one read port, not the existing `RankingService`

- **Real seam, not hypothetical**: two external adapters (websocket
  + admin) both call the same one method. One method on a port is
  the narrowest legitimate seam.
- **Test surface**: each external caller mocks exactly one method
  (`readLiveRanking`). Mocks for the full `RankingService` were
  60% unused (only `getContestRanking` was actually mocked in the
  existing `RankingServiceImplTest` &mdash; the unused field was a
  code smell).
- **Future wire-format evolution**: if a future leaderboard variant
  adds "by score only" or "by country" filtering, the new method
  belongs on a new port, not on `RankingService`.

## Why the port is owned by `contest`, not by `websocket` or `admin`

The read is a *contest-domain* concept. The contest module defines
what "live ranking" means (sorted by score desc, then penalty asc,
cap 200). `websocket` and `admin` only consume the result. This
mirrors the ADR-0009 attribution: the producer of the data owns the
port; the consumer of the data injects the adapter.

If the port were owned by `websocket` (because it was the earliest
adapter) then `admin` would have to depend on a websocket-owned port,
which inverts the dependency. Worse, the WebSocket-specific
throttle / flush logic (`WebSocketContestRankingFlusher`) stays in
`websocket` because it is a transport concern (ADR-0009 §3), but
the **read** itself is not a transport concern &mdash; it is a
domain query.

## Why a separate adapter class, not `RankingServiceImpl implements ContestLiveRankingReadPort`

Spring's `@Service`-registered `RankingServiceImpl` bean is registered
under its concrete class name; injecting the interface does not
automatically discover it without an explicit bean alias. The
existing ADR-0009 ports all follow the separate-adapter pattern
(`WebSocketNotificationPushAdapter`,
`WebSocketContestStatusPushAdapter`, &hellip;) and that convention
keeps `RankingServiceImpl` free of port responsibilities &mdash; it
owns the SQL / sort / limit logic and nothing else. A 30-line adapter
class is the cheapest way to wire the port without disturbing the
existing module structure.

## Backward compatibility

- HTTP routes `/contest/{id}/ranking` and
  `/contest/{id}/live-ranking` are unchanged; only the internal
  collaborator changed.
- `RankingService.getContestRanking` and `getUserContestHistory` are
  unchanged; both still work for internal callers.
- `RankingServiceImpl.readLiveRanking(String, int)` is `public` but
  not part of any interface. It is documented as the adapter's
  delegate target. No caller outside the adapter is expected to use
  it; should one emerge, the right move is to switch it back to a
  port method on `ContestLiveRankingReadPort`.

## Related

- [[archive/concepts/realtime-push-port-series]] &mdash; ADR-0009, the
  producer-side collapse whose Javadoc named this seam
- [[archive/concepts/submission-contest-port]] &mdash; ADR-0001, the
  port-pattern precedent
- [[archive/concepts/admin-user-stats-read-port]] / [[archive/concepts/admin-comment-read-port]]
  &mdash; sibling read ports on the admin side
- [[entities/judge-queue]] &mdash; the read-side counterpart of the
  judging-pipeline module
