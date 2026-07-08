# CONTEXT — UltiCode Domain Glossary

> Domain language for the UltiCode online-judge platform. Used by
> architecture reviews (see `.claude/skills/improve-codebase-architecture/`)
> and by `/grill-with-docs` so discussions name concepts, not implementation
> artifacts ("the Submission intake module", not "the FooBarHandler").
>
> Append-only. When a term is sharpened during a design conversation, update
> it in place. Cross-link related terms with `[[Term]]`.

## Aggregates & entities

- **Submission** — a user's code submission for a Problem, judged by the
  sandbox. Lifecycle: `Pending → Judging → {Accepted | Wrong Answer | TLE |
  MLE | RE | CE | …}`. Persisted in `submissions`. Core of the submission
  module.
- **Problem** — a coding challenge (title, statement, test cases, languages).
  Persisted in `problems`. Has versions (`ProblemVersion`).
- **Contest** — a time-boxed competition containing Problems. Status:
  `DRAFT | REGISTERED | RUNNING | FINISHED`. Persisted in `contests`.
- **ContestProblem** — the many-to-many join between a Contest and a Problem
  (carries `problem_index`, `score`, `penalty_per_wrong`).
- **ContestParticipant** — a user's participation in a Contest. Status:
  `REGISTERED | STARTED | FINISHED | DISQUALIFIED`. Has `isVirtual` flag for
  replay sessions.
- **ContestSubmission** — the contest-side record linking a Submission to a
  ContestProblem + ContestParticipant. Carries `timeFromStart`, `isAccepted`.

## Module concepts (architecture)

- **Submission intake** — the write path that creates a Submission, writes
  the judge outbox row, and asks the contest module to record a
  ContestSubmission (`SubmissionServiceImpl.submit`).
- **Projection** — a deep module owning entity→VO projection and read-side
  aggregation for one domain, behind a small interface. Pattern:
  `ProblemProjection`, `SubmissionProjection`, `ModerationProjection`,
  `SearchReadProjection`, `SolutionProjection`, `AchievementProjection`,
  `AdminForumProjection`, `AdminSolutionProjection`,
  `AdminSubmissionProjection`, `AdminUserProjection`,
  `AdminContestProjection`, `AdminNotificationProjection`. The
  `AdminXxxProjection` series is the ADR-0011 deepening that lifts
  entity→VO shaping and pagination out of the admin orchestration services.
- **Search / SearchReadProjection** — the cross-domain read module that
  fans a query across the problems / users / posts / solutions indices
  (MeiliSearch when configured, database LIKE fallback) and aggregates the
  hits into one `SearchResponseVO`. Replaces the shallow `SearchService`
  facade; only `SearchController` calls it.
- **Port** — an interface owned by the consuming module describing a
  collaboration it needs, implemented by an adapter in the providing module
  (dependency inversion). See [[ContestSubmissionPort]],
  `SubmissionAnalyticsPort`, `AdminSubmissionReadPort`,
  `AdminUserStatsReadPort`, `AdminCommentReadPort`, `AuthSessionPort`,
  `ProblemDetailPort`, `TokenBlacklistPort`.
- **TokenBlacklistPort** — read-only revocation seam consulted by the
  WebSocket authentication path before a STOMP CONNECT is accepted. Owned
  by the websocket module (the consumer); the Redis adapter
  (`RedisTokenBlacklistAdapter`) hides SHA-256 fingerprinting and the
  `blacklist:token:<sha256>` key-prefix convention. The previous
  `com.ulticode.common.service.TokenBlacklistService` fused the read path
  with three unused write methods (dead code &mdash; runtime revocation is
  owned by `RefreshTokenService`); the port deliberately exposes only the
  read side. Fail-closed: storage errors propagate so revoked tokens can
  never slip through on a Redis outage.
- **AdminReadModel seam** — the running series of typed read ports the
  admin module owns to stop reaching across into submission / user /
  forum / solution mappers: `AdminSubmissionReadPort` (dashboard global),
  `AdminUserStatsReadPort` (per-user stats), `AdminCommentReadPort`
  (comment-view enrichment). Future phases: contest.
- **ContestSubmissionPort** — the port through which submission asks contest
  to record synchronous same-transaction contest effects. See ADR-0001.
- **SubmissionPerformanceStats** — deep module owning the runtime/memory
  percentile + distribution-bin math for an Accepted submission.
- **Judge queue / outbox** — the dispatch path from Submission intake to the
  sandbox (`QueueService`, `JudgeOutboxMapper`, ADR-003).
- **SubmissionJudgedEvent** — domain event published after the verdict-write
  transaction commits; consumed by `ContestScoringListener` (AFTER_COMMIT)
  for post-commit scoring, and by achievement listeners.
- **Realtime push seam** — the six consumer-owned ports that invert the
  WebSocket push path: `NotificationPushPort`, `BadgePushPort`,
  `SubmissionResultPushPort`, `ContestRankingMarkDirtyPort`,
  `ContestStatusPushPort`, `ContestAnnouncementPushPort`. Adapters live
  in `websocket/port/adapter/`. The only producer-side component left is
  `WebSocketContestRankingFlusher` (ranking throttle + flush + cleanup),
  which exists to protect the STOMP transport from leaderboard-flood
  bursts. The old `RealtimeService` god service is deleted. See
  ADR-0009.
- **Admin projection** — module-owned deep modules holding admin's own
  `AdminXxxVO` projection rules and read-side aggregation
  (`AdminSubmissionProjection`, `AdminUserProjection`, …). Complements
  the [[AdminReadModel seam]] ports: ports are for cross-module reads,
  projections are for admin's own VO shape. See ADR-0011.

## Design invariants

- **D-04** — Submission + ContestSubmission are recorded in the **same
  transaction** (synchronous). Post-commit scoring is event-driven; recording
  is not.
- **D-05 / D-06** — a ContestSubmission is recorded only when the contest is
  RUNNING and the participant has STARTED.
- **R6.2 / F-06** — time-from-start uses the contest clock for real
  participants and the participant's own `startedAt` for virtual replays.
- **R6.3 / F-08** — virtual-replay Accepted submissions do **not** trigger
  achievements (they are not earned history).
- **P0-1** — HIDDEN test-case contents are never leaked to users; only
  SAMPLE/legacy cases populate `vo.tests` and the I/O preview.

## Decisions

- See `wiki/concepts/` — ADR-0001 ([Submission-Contest Port](wiki/concepts/submission-contest-port.md)), ADR-0004
  (moderation projection extraction), ADR-0005 (achievement projection
  extraction), ADR-0006 (problem detail port extraction), ADR-0007
  (admin user stats read port), ADR-0008 (admin comment read port),
  ADR-0009 (realtime push seam inversion — six consumer-owned ports +
  `RealtimeService` collapse).
