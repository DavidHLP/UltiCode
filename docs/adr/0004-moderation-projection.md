# ADR-0004: Moderation 模块提取 Projection 深模块

- **Status**: Accepted
- **Date**: 2026-07-02
- **Scope**: `backend-spring` — moderation
- **Supersedes**: none
- **Tags**: architecture, deep-module, projection, locality

## Context

Before 2026-07-02, `ModerationServiceImpl` (760 lines) mixed the moderation
**state machine** (claim / assign / unassign / `performAction` /
`batchAction` / `createReport` / `createAppeal` / `reviewAppeal`) with ten
**pure-read** methods and their five projection helpers:

- the queue read paths — `getQueueItems` (filter + sort + paginated
  list), `getQueueItem`, `findByEntity`, `getStats` (eight counts +
  group-by distributions);
- the report read paths — `getReportsForEntity`, `getReports`,
  `getReport`;
- the appeal read paths — `getAppeals`, `getMyAppeals`, `getAppealStats`;
- the entity→VO projections — `toQueueVO` (with the batched user map and
  the `solution_comment` parent-id resolution), `toReportVO`, `toAppealVO`;
- the read aggregations — `toCountMap` (SQL group-by adapter),
  `buildUserMap` (batched author/assignee/reviewer fetch).

Twelve MyBatis mappers were constructor dependencies. Every projection tweak
landed in the same file as the write paths that mutate queue/report/appeal
state. Testing any read path required mocking all twelve collaborators even
though projection is pure computation over already-loaded entities.

A `grep` confirmed the ten read methods had **no cross-module callers** —
only `ModerationController` invoked them. They were therefore pass-through
facade methods earning no locality next to the state machine: deleting them
from the service and serving them from a dedicated module concentrates the
projection rules instead of duplicating them across the service and a future
read-side caller.

This is the same shallow cluster already deepened for `SubmissionProjection`
(a58e5fa9d), `ProblemProjection` (32b17a4a0) and `SubmissionPerformanceStats`
(aac536fbc): entity→VO projection and read-side aggregation sitting next to
a state machine.

## Decision

Extract a `ModerationProjection` deep module that owns every projection rule
and read-side aggregation for the moderation domain. Controllers depend on
it directly for reads; the service keeps the write paths and the
authorisation-guarded appeal lookup, delegating to the projection for the
view shapes those write paths still return.

```
moderation/projection/ModerationProjection          (interface)
  ├── listQueueItems / queueItemById / queueItemByEntity / stats
  ├── reportsForEntity / listReports / reportById
  ├── listAppeals / myAppeals / appealStats
  └── toAppealVO(Appeal)                  // facade for the service write paths

moderation/projection/DefaultModerationProjection   (@Service, only adapter)
  injects: ModerationQueueMapper, ReportMapper, AppealMapper,
           UserMapper, SolutionCommentMapper

moderation/service/ModerationService                (interface, slimmed 19→9)
  keeps: claimItem / assignItem / unassignItem / performAction / batchAction /
         createReport / createAppeal / getAppeal / reviewAppeal
  drops: the ten pure-read methods above

moderation/service/impl/ModerationServiceImpl       (760 → ~390 lines)
  injects ModerationProjection; write paths end with
  `projection.queueItemById(id)` (post-action reload) and
  `projection.toAppealVO(appeal)` (post-create / post-review view).
  ActionContext still holds `this`; the package-private
  createUserWarning / createUserBan / updateContentFlagStatus helpers stay.

moderation/controller/ModerationController          (10 read endpoints → projection)
```

The `getAppeal(id, currentUserId)` authorisation check (isOwner / isModerator,
null-safe via `Objects.equals`) stays in the service. The service reads the
appeal row for the check, then hands the entity to
`projection.toAppealVO`; the projection never owns the authz decision, and
an unauthorised caller does not trigger the projection's user-name lookups.

## Consequences

- **Locality**: the three entity→VO projections, the three list query
  builders (shared sort-default policy), and the two statistics
  aggregations now live in one module. A projection rule change is a
  single-file diff, not a diff that also touches the moderation state
  machine.
- **Leverage**: the queue endpoints share `buildUserMap` + `toQueueVO`
  inside the module instead of duplicating batched-user-fetch policy across
  call sites.
- **Interface is the test surface**: `ModerationControllerTest` (48 tests)
  mocks a single `ModerationProjection` for the read paths instead of
  stubbing ten service methods through a twelve-collaborator facade. The
  projection's own rules can now be unit-tested with mapper mocks alone.
- **Behaviour is byte-for-byte identical**: same filters, same sort
  defaults, same `solution_comment` parent-id resolution, same
  `buildUserMap` batched fetch, same stats counts (including
  `appealMapper.countPending()` for `pendingAppealsCount`), same
  `MODERATION_QUEUE_NOT_FOUND` + `"Report not found: "` legacy error on
  `reportById`. Verified by `ModerationControllerTest` green (48/48).
- The service interface drops ten pure-read methods. There were no
  cross-module callers, so no external rewiring is needed.
- The service still injects all twelve mappers (the write paths use them:
  `resolveAuthorId`, `updateContentFlagStatus`, `createUserBan`, etc.).
  Mapper count is unchanged; the win is locality and test surface, not
  dependency count.

## Alternatives considered

- **Split into three services** (`QueueService` / `ReportService` /
  `AppealService`). Rejected: it moves the write paths too, multiplying the
  blast radius (transactions, `ActionContext` coupling, the
  report↔queue linkage in `createReport`). The established rhythm in this
  codebase is one deepening dimension per change — read-side projection
  first, leaving the write-side organisation for a separate, motivated
  change.
- **Extract `ProblemListServiceImpl.getListOverview` instead** (145-line
  single method). Rejected: it is a monolithic single-method read, not a
  cluster of projections + aggregations. The deletion test favours
  moderation: deleting the moderation projection concentrates three VO
  mappings + two stats aggregations + three query builders; deleting a
  `getListOverview` projection moves one method's complexity into one other
  method. Lower leverage.
- **Dependency-inversion port for moderation** (mirror `ContestSubmissionPort`).
  Rejected: no other module synchronously calls into moderation reads, so
  there is no coupling to invert. A port here would be a one-adapter seam
  with no second adapter on the horizon — a hypothetical seam, not a real
  one.

## Verification

- `./mvnw compile -B` → BUILD SUCCESS
- `./mvnw test-compile -B` → BUILD SUCCESS
- `./mvnw test -B -Dtest=ModerationControllerTest` → 48/48 green
  (read paths migrated to `@MockBean ModerationProjection`; write paths
  unchanged).
