---
title: Submission-Contest Port
type: concept
tags: [submission, contest, architecture, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/modules/submission/port/ContestSubmissionPort.java
  - backend-spring/src/main/java/com/ulticode/modules/contest/integration/ContestSubmissionAdapter.java
aliases: [ADR-0001, Contest Submission Port, Submission Contest Decoupling]
---

# Submission-Contest Port

> [!note] This page is the landed record of **ADR-0001 — Submission 模块
> 不直接依赖 Contest 模块**. Per [SCHEMA §3](../SCHEMA.md) the project
> keeps no separate `decisions/` dir &mdash; an ADR folds into `concepts/`.

## The problem

Before 2026-07-02, `SubmissionServiceImpl` (1008 lines) held four
contest-module MyBatis mappers as constructor dependencies
(`ContestProblemMapper`, `ContestMapper`, `ContestParticipantMapper`,
`ContestSubmissionMapper`) and inlined all contest-side persistence
effects of a submission:

- `recordContestSubmissionIfNeeded` &mdash; find the RUNNING contest
  containing the problem, verify the user has STARTED participation,
  insert a `ContestSubmission` row with the real-vs-virtual clock
  selection (R6.2 / F-06), mark the ranking dirty.
- the `isVirtual` probe in `updateSubmissionResult` &mdash; skip
  achievement triggers for virtual-contest replays (R6.3 / F-08).

The contest concept appeared 53 times inside the submission service.
The coupling was bidirectional: the contest module already had a
`ContestSubmissionBridgeController` pointing at submission, while
submission reached back into four contest mappers. Testing `submit()`
on a plain (non-contest) submission required mocking four contest
mappers and building `Contest` / `ContestParticipant` /
`ContestProblem` fixtures.

The post-commit **scoring** path was already decoupled:
`SubmissionServiceImpl` publishes `SubmissionJudgedEvent`, consumed by
`ContestScoringListener` (`@TransactionalEventListener(AFTER_COMMIT)`).
Only the **synchronous, same-transaction recording** path was still
coupled.

## The decision

Invert the dependency. The submission module owns a port interface
that describes the collaboration it needs; the contest module supplies
the adapter.

```
submission/port/ContestSubmissionPort         (interface, owned by submission)
  ├── recordSubmissionIfNeeded(submissionId, userId, problemId)
  └── isVirtualParticipation(submissionId) : boolean

contest/integration/ContestSubmissionAdapter   (@Component, owned by contest)
  implements ContestSubmissionPort
  injects: ContestProblemMapper, ContestMapper, ContestParticipantMapper,
           ContestSubmissionMapper, RealtimeService
```

After this change, `com.ulticode.modules.submission.*` has **zero**
`import com.ulticode.modules.contest.*` statements. The contest module
depends on the submission module's port interface (and, as before, on
its `SubmissionJudgedEvent` and `Submission` entity) &mdash; a single,
already-existing dependency direction.

This mirrors the port/adapter deepenings already established in the
codebase (`AdminSubmissionReadPort`, `AuthSessionPort`,
`SubmissionAnalyticsPort`, `JudgeQueue`) and the projection deep
modules (`ProblemProjection`, `SubmissionProjection`).

## Where it lives

- `submission/port/ContestSubmissionPort.java` &mdash; interface, owned
  by the submission module.
- `contest/integration/ContestSubmissionAdapter.java` &mdash; the only
  `@Component` adapter; holds the four contest mappers +
  `RealtimeService`.
- `SubmissionServiceImpl` &mdash; loses 4 contest-mapper fields + the
  `RealtimeService` field (used only by contest recording) + ~50
  lines of contest logic + ~130 lines of stats math (extracted in the
  same pass into `SubmissionPerformanceStats`).
- `ContestScoringListener` &mdash; unchanged, still consumes
  `SubmissionJudgedEvent` AFTER_COMMIT (the post-commit scoring path
  was already decoupled).

## Consequences

**Positive**

- Submission unit tests mock 1 port instead of 4 contest mappers; the
  contest-recording rules are tested in isolation on the adapter side
  with pure contest fixtures.
- Contest recording rules (clock selection, RUNNING/STARTED gating,
  first-match-break) now have a single home &mdash; locality for
  future changes.
- Contest can change its participation model, ranking algorithm, or
  mapper schema without touching submission.

**Negative / neutral**

- One extra interface + one extra class.
- The adapter lives in a new package `contest/integration/` (the
  module previously had no integration package; `listener/` was the
  closest).
- Contest recording remains synchronous and same-transactional &mdash;
  see "Rejected alternatives" for why we did not go further.

## Rejected alternatives

**Event-driven full decoupling** (`SubmissionCreatedEvent` &rarr;
`@EventListener`). Rejected: `submit()` runs inside `@Transactional`
and inserts the `ContestSubmission` synchronously to honour design
invariant **D-04** ("submission + contest record in the same
transaction"). Moving recording to an async `@EventListener` would
publish after commit, breaking the same-transaction guarantee: a crash
between commit and listener firing could leave a submission with no
contest record. The post-commit **scoring** path is already
event-driven (`SubmissionJudgedEvent` &rarr;
`ContestScoringListener`); only the recording path is deliberately
synchronous. This ADR preserves that split.

**In-module adapter** (hide coupling behind a submission-side port).
Rejected: putting the adapter in `submission/port/adapter/` (injected
with the 4 contest mappers) would hide the coupling behind a seam
without removing the `submission &rarr; contest` import dependency.
The dependency inversion (adapter in contest, port in submission) is
strictly better and costs the same number of classes.

## Related

- [[concepts/module-layering]] &mdash; Projection / Port / Inspector
  pattern
- [[concepts/notification-dispatch-and-preferences]] &mdash; ADR-004,
  the consumer-owns-port convention this ADR established
- [[concepts/admin-user-stats-read-port]] &mdash; ADR-0007 (same
  consumer-owns-port pattern in admin)
- [[concepts/realtime-push-port-series]] &mdash; ADR-0009 (six-port
  series mirroring the per-consumer seam rationale)
