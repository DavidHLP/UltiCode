# CONTEXT — UltiCode Domain Glossary

> Domain language for the UltiCode online-judge platform. Used by
> architecture reviews (see `AGENTS.md` and `.codex/skills/`)
> and by `/grill-with-docs` so discussions name concepts, not implementation
> artifacts ("the Submission intake module", not "the FooBarHandler").
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

- **Submission intake** — the owner-only write path that creates a Submission in `backend-submission` via `SubmissionIntakePort`/`RemoteSubmissionWritePort`, writes the judge outbox row, and asks the contest module to record a `ContestSubmission`.
- **Projection** — a deep module owning entity→VO projection and read-side
  aggregation for one domain, behind a small interface. Pattern:
  `SearchReadProjection`, `SolutionProjection`, `AchievementProjection`,
  `AdminForumProjection`, `AdminSolutionProjection`,
  `AdminSubmissionProjection`, `AdminUserProjection`,
  `AdminContestProjection`, `AdminNotificationProjection`. The
  `AdminXxxProjection` series lifts
  entity→VO shaping and pagination out of the admin orchestration services.
- **Admin projection** — module-owned deep modules holding admin's own
  `AdminXxxVO` projection rules and read-side aggregation
  (`AdminSubmissionProjection`, `AdminUserProjection`, …). Complements
  the [[AdminReadModel seam]] ports: ports are for cross-module reads,
  projections are for admin's own VO shape. See the current Admin projection
  implementations and tests.
- **User Facts View** — the cross-owner read shape that combines Auth account
  facts with App profile facts for Search and moderation reads only; ordinary
  user-facing reads go through the User Directory View below.
  Missing profiles remain nullable; an unavailable account owner fails closed.
- **User Directory View** — the account/profile summary used by ordinary user
  reads and connection-time account checks. It is separate from the narrower
  User Facts View used by Search and Moderation.
- **User Facts Projection** — the deep read module behind the User Facts View.
  It owns bounded account/profile batching, input ordering, freshness fields,
  and missing/unavailable-owner semantics so callers do not assemble the two
  owner reads themselves.
- **Search / SearchReadProjection** — the cross-domain read module that
  fans a query across the problems / users / posts / solutions indices
  (MeiliSearch when configured, database LIKE fallback) and aggregates the
  hits into one `SearchResponseVO`. Replaces the shallow `SearchService`
  facade; only `SearchController` calls it.
- **Port** — an interface owned by the consuming module describing a
  collaboration it needs, implemented by an adapter in the providing module
  (dependency inversion). See [[ContestSubmissionPort]],
  [[AdminReadModel seam]], [[CurrentUserProvider seam]].
  `AdminSubmissionReadPort`, `AdminCommentReadPort`, `AdminSubmissionUserDetailStatsReadPort`,
  `AuthSessionPort`, `ProblemDetailPort`, `TokenBlacklistPort`.
- **TokenBlacklistPort** — read-only revocation seam consulted by the
  WebSocket authentication path before a STOMP CONNECT is accepted. Owned
  by the websocket module (the consumer); the Redis adapter
  (`RedisTokenBlacklistAdapter`) hides SHA-256 fingerprinting and the
  `blacklist:token:<sha256>` key-prefix convention. Fail-closed: storage
  errors propagate so revoked tokens can never slip through on a Redis outage.
- **AdminReadModel seam** — the running series of typed read ports the
  admin module owns to stop reaching across into submission / user /
  forum / solution mappers: `AdminSubmissionReadPort` (dashboard global),
  `AdminCommentReadPort` (comment-view enrichment), and
  `AdminContestReadPort`. The retired per-user stats port
  (`AdminUserStatsReadPort`) was replaced by the single use-case
  `AdminUserDetailQuery` deep module backed by the Auth
  `AuthorizationSnapshotService` and the provider-owned Submission
  `SubmissionUserDetailStatsPort` snapshot.
- **ContestSubmissionPort** — the narrow contract used by Submission-owned
  reads of contest context. Contest-side mutation is the durable
  `SubmissionCreated` outbox → App inbox/event path; it is not a synchronous
  same-transaction callback.
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
  bursts.
- **CurrentUserProvider (sole actor seam)** — the canonical port that
  every service uses to resolve the acting principal's identity and
  role. `SystemSettingsServiceImpl`, `PermissionServiceImpl`,
  `UserPermissionServiceImpl`, and `AuditHelper` use
  `CurrentUserProvider` for `getCurrentUserId` / `hasRole` /
  `hasAuthority`; tests inject the same port.
  See [[CurrentUserProvider seam]].
- **SystemSettingsStore** — the storage seam for the
  `system_settings` table. Owns the five category keys
  (`general` / `email` / `rate-limits` / `uploads` / `features`), the JSON
  encode/decode of the `value` column, the batched read used by
  `GET /admin/settings/all`, and the row upsert/delete paths. The
  service keeps only the business policy (SMTP password masking, the
  "preserve-on-mask" PATCH rule, the all-defaults feature-toggle
  safety check, the audit anchor). One prod adapter
  (`JsonSystemSettingsStore`) + one in-memory test double. The audit
  log's actor flows through the authenticated principal provided by the
  port.
- **PartialUpdate** — the partial-PATCH helper (in `common/util`)
  with four static methods: `setIfPresent(entity, getter, setter)`,
  `setIfPresentText(text-aware entity variant that skips blanks)`,
  `setIfPresentWrapper(LambdaUpdateWrapper<T>)`, and
  `setIfPresentTextWrapper` (text-aware wrapper variant). PATCH services
  delegate to one of the four methods for consistent null/blank handling.
  Applied to: `UserManagementServiceImpl` (12 fields), admin and
  user `ProblemList` services (4 update methods each), admin and
  generic `ContestServiceImpl`, admin and user `NotificationService`
  paths, `AdminTestCaseService.updateTestCase` (8 fields), and
  `AdminProblemServiceImpl.updateFromImport` (5 fields). One
  `common/util` helper keeps those update paths consistent.
- **TimeSource** — the read-only port that hides `System.currentTimeMillis()`
  `SystemTimeSource` (prod `@Component`) and `FakeTimeSource` (test,
  not a bean). Static utility call sites (`TraceIdUtil.current()`)
  reach it through `TimeSourceHolder`, installed at startup by
  `TimeConfig`. Complements the owner-local `Clock` seams in
  `services/auth/src/main/java/com/ulticode/auth/config/AuthClockConfig.java`,
  `services/admin/src/main/java/com/ulticode/admin/config/AdminClockConfig.java`,
  and `services/app/app-web/src/main/java/com/ulticode/app/config/AppClockConfig.java`,
  which cover `LocalDateTime.now()`; wall millis + monotonic nanos were the
  two remaining JVM-time primitives.
- **Notification Delivery worker** — the worker role of the Notification
  Owner that consumes durable notification/integration events, stages them in
  an inbox, and reclaims delivery-ledger leases. App keeps only its own
  achievement, contest, moderation and realtime event bindings; Notification
  remains the sole owner of notification delivery state. This role does not
  create another logical service.

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

- 2026-07-09: the former `wiki/concepts/` ADR + concept-page layer was retired.
  Durable rationale remains in the relevant ADRs, source Javadoc and Flyway
  migration comments; this glossary is the current terminology entry point.
