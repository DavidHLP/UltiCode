# C10 Gating — VoteResultUpdated Event Speculative

**Date:** 2026-07-11
**Source candidate:** `/tmp/architecture-review-20260711-143159.html` §10 (Speculative)
**Red team position:** `.claude/reviews/architecture-review-20260711-143159-review.md` (no objection; speculative stays speculative)

## Decision: maintain as Speculative, do not implement

`EdgeOperationsServiceImpl.java:74-161` fuses vote denormalisation (solution
likes/dislikes counter) with the vote write in one caller. The original
review proposed publishing `VoteResultUpdated` after commit so each target
module owns its own denormalisation listener — the write-side echo of
ADR-0011's read-side projection pattern.

## Gating requirement

`VoteResultUpdated` becomes viable when:

1. **A second vote-denormalisation site appears** (comment likes, forum
   thread subscriptions, problem bookmark counters, etc.). One site isn't
   enough to justify a new event channel — the existing inline fuse in
   `EdgeOperationsServiceImpl` works and is small.
2. **The denormalisation logic grows beyond ~50 lines** — at that size the
   inline fuse becomes harder to test in isolation, and the event-channel
   abstraction pays for itself.

`AGENTS.md` already names `VoteResultUpdated` as the future direction, so
the contract name is reserved. **No implementation until either gate
trips.**

## Why C10 stays small

- `VoteServiceImpl.java` is already a deep module (per the original review's
  §10 ADR-flagged note) — its 126 lines hide the vote state machine behind
  a narrow interface. **Leave it untouched.**
- The denormalisation fuse is the only code that needs event extraction, and
  it is only 87 lines (74-161 of `EdgeOperationsServiceImpl`). A new event
  channel costs ~3 listeners + a publisher port + tests; the gain is one
  fuse removed.

## Implementation outline (for when the gate trips)

When triggered, the implementation would be:

```java
// vote module publishes after commit
applicationEventPublisher.publishEvent(new VoteResultUpdated(this,
    vote.targetType(), vote.targetId(), vote.userId(), vote.newState()));

// solution / comment / forum modules each own a listener
@Component
class SolutionVoteDenormListener {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    void on(VoteResultUpdated e) {
        if ("SOLUTION".equals(e.targetType())) {
            solutionMapper.bumpVoteCounter(e.targetId(), e.newState());
        }
    }
}
```

## Status

- [ ] Implementation deferred until second denorm site appears or fuse grows beyond 50 lines
- [ ] AGENTS.md contract reservation preserved
- [ ] revisit on next vote-touching feature