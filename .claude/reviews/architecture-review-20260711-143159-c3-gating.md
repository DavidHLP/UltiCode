# C3 Gating — STOMP Channel Factory

**Date:** 2026-07-11
**Source candidate:** `/tmp/architecture-review-20260711-143159.html` §3 (Strong)
**Red team position:** `.claude/reviews/architecture-review-20260711-143159-review.md` §3.2 + §A.8 + §A.9

## Decision: GATED on auth/topic verification, deferred

The original review proposed a `createStompChannel(endpoint)` factory
collapsing the two STOMP singletons into one shared connection. **Red team
CR §3.2 / §A.8 / §A.9** verified the proposal rests on a partially false
premise: the actual code is **one shared singleton** (`console/src/lib/socket.ts`,
the global STOMP manager) + one thin adapter composable (`console/src/composables/useSocket.ts`,
153 lines, wraps the singleton) + **one** domain composable that builds its
own connection (`console/src/composables/contest/useContestSocket.ts`,
688 lines).

The "two parallel connection stacks" framing is wrong. The cost-benefit math
of the factory abstraction therefore changes: only the contest composable
carries self-managed connection plumbing; the notifications path already
delegates to the shared singleton.

## Gating requirement

A unified `createStompChannel(endpoint)` factory becomes viable only when:

1. **Auth/subscription semantics verified** — `subscribeToContest` /
   `unsubscribeFromContest` in `useContestSocket.ts` carry
   user-identity-scoped topic routes that may not be safely multiplexed
   onto a shared connection with the notifications endpoint. If a shared
   connection exposes the contest topic to any subscriber on the same
   connection, **potential privilege escalation**. Verify before merging
   connections.
2. **Adapter consolidation first** — refactor `useContestSocket.ts` to
   wrap `lib/socket.ts` (thin adapter pattern), so the contest path also
   delegates to the shared singleton. This is the safe half of C3 and
   can proceed regardless of the connection-multiplexing decision.
3. **Then evaluate the factory** — only after both adapter consolidation
   and auth verification should the factory abstraction be introduced.

## What was NOT done (deferred)

- `createStompChannel(endpoint)` factory: not created.
- `/ws/notifications` ↔ `/ws/contest` connection merging: not done.
- `useContestSocket.ts` adapter consolidation: not done.

## What the architecture review got right

- The two endpoints **do** share a backend STOMP broker (`backend-spring/.../WebSocketConstants.java:15-16`).
- The notifications path **does** go through `lib/socket.ts` → thin adapter.
- Contest path **does** self-manage a connection.

The review correctly identified the connection-plumbing duplication, but
the proposed factory abstraction conflates two separate refactors:
adapter consolidation (safe) and connection multiplexing (requires auth
verification).

## Status

- [ ] Implementation deferred until (1) auth/topic verification + (2) adapter consolidation complete
- [ ] revisit when useContestSocket.ts is touched next