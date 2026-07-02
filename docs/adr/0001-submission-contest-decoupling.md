# ADR-0001: Submission 模块不直接依赖 Contest 模块

- **Status**: Accepted
- **Date**: 2026-07-02
- **Scope**: `backend-spring` — submission, contest
- **Supersedes**: none
- **Tags**: architecture, deep-module, dependency-inversion

## Context

Before 2026-07-02, `SubmissionServiceImpl` (1008 lines) held four
contest-module MyBatis mappers as constructor dependencies
(`ContestProblemMapper`, `ContestMapper`, `ContestParticipantMapper`,
`ContestSubmissionMapper`) and inlined all contest-side persistence effects
of a submission:

- `recordContestSubmissionIfNeeded` — find the RUNNING contest containing
  the problem, verify the user has STARTED participation, insert a
  `ContestSubmission` row with the real-vs-virtual clock selection
  (R6.2 / F-06), mark the ranking dirty.
- the `isVirtual` probe in `updateSubmissionResult` — skip achievement
  triggers for virtual-contest replays (R6.3 / F-08).

The contest concept appeared 53 times inside the submission service. The
coupling was bidirectional: the contest module already had a
`ContestSubmissionBridgeController` pointing at submission, while submission
reached back into four contest mappers. Testing `submit()` on a plain
(non-contest) submission required mocking four contest mappers and building
`Contest` / `ContestParticipant` / `ContestProblem` fixtures.

The post-commit **scoring** path was already decoupled:
`SubmissionServiceImpl` publishes `SubmissionJudgedEvent`, consumed by
`ContestScoringListener` (`@TransactionalEventListener(AFTER_COMMIT)`). Only
the **synchronous, same-transaction recording** path was still coupled.

## Decision

Invert the dependency. The submission module owns a port interface that
describes the collaboration it needs; the contest module supplies the
adapter.

```
submission/port/ContestSubmissionPort         (interface, owned by submission)
  ├── recordSubmissionIfNeeded(submissionId, userId, problemId)
  └── isVirtualParticipation(submissionId) : boolean

contest/integration/ContestSubmissionAdapter   (@Component, owned by contest)
  implements ContestSubmissionPort
  injects: ContestProblemMapper, ContestMapper, ContestParticipantMapper,
           ContestSubmissionMapper, RealtimeService
```

After this change, `com.ulticode.modules.submission.*` has **zero** `import
com.ulticode.modules.contest.*` statements. The contest module depends on
the submission module's port interface (and, as before, on its
`SubmissionJudgedEvent` and `Submission` entity) — a single, already-existing
dependency direction.

This mirrors the port/adapter deepenings already established in the codebase
(`AdminSubmissionReadPort`, `AuthSessionPort`, `SubmissionAnalyticsPort`,
`JudgeQueue`) and the projection deep modules (`ProblemProjection`,
`SubmissionProjection`).

## Consequences

**Positive**

- Submission module no longer knows contest internals; contest can change
  its participation model, ranking algorithm, or mapper schema without
  touching submission.
- `SubmissionServiceImpl` lost 4 contest-mapper fields + the
  `RealtimeService` field (used only by contest recording) + ~50 lines of
  contest logic + ~130 lines of stats math (extracted in the same pass into
  `SubmissionPerformanceStats`).
- Submission unit tests mock 1 port instead of 4 contest mappers; the
  contest-recording rules are tested in isolation on the adapter side with
  pure contest fixtures.
- Contest recording rules (clock selection, RUNNING/STARTED gating,
  first-match-break) now have a single home — locality for future changes.

**Negative / neutral**

- One extra interface + one extra class.
- The adapter lives in a new package `contest/integration/` (the module
  previously had no integration package; `listener/` was the closest).
- Contest recording remains synchronous and same-transactional — see
  "Rejected alternatives" for why we did not go further.

## Rejected alternatives

### Event-driven full decoupling (SubmissionCreatedEvent → @EventListener)

**Rejected.** `submit()` runs inside `@Transactional` and inserts the
`ContestSubmission` synchronously to honour design invariant **D-04**
("submission + contest record in the same transaction"). Moving recording
to an async `@EventListener` would publish after commit, breaking the
same-transaction guarantee: a crash between commit and listener firing
could leave a submission with no contest record. It would also introduce
ordering and retry semantics that the synchronous port does not need.

The post-commit **scoring** path is already event-driven
(`SubmissionJudgedEvent` → `ContestScoringListener`); only the recording
path is deliberately synchronous. This ADR preserves that split.

### In-module adapter (hide coupling behind a submission-side port)

**Rejected.** Putting the adapter in `submission/port/adapter/` (injected
with the 4 contest mappers) would hide the coupling behind a seam without
removing the `submission → contest` import dependency. The dependency
inversion (adapter in contest, port in submission) is strictly better and
costs the same number of classes.

## References

- `docs/architecture-review-20260702-122248.html` (local report) — candidate #1
- Ousterhout, *A Philosophy of Software Design* — "deep modules"
- Prior art in repo: `AdminSubmissionReadPort`, `AuthSessionPort`,
  `SubmissionAnalyticsPort`, `JudgeQueue`, `ProblemProjection`,
  `SubmissionProjection`
