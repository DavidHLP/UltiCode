---
title: Moderation Projection
type: concept
tags: [moderation, architecture, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/modules/moderation/projection/
aliases: [ADR-0004, Moderation Read Projection, Moderation Deep Module]
---

# Moderation Projection

> [!note] This page is the landed record of **ADR-0004 — Moderation
> 模块提取 Projection 深模块**. Per [SCHEMA §3](../SCHEMA.md) the
> project keeps no separate `decisions/` dir &mdash; an ADR folds into
> `concepts/`.

## The problem

Before 2026-07-02, `ModerationServiceImpl` (760 lines) mixed the
moderation **state machine** (claim / assign / unassign /
`performAction` / `batchAction` / `createReport` / `createAppeal` /
`reviewAppeal`) with ten **pure-read** methods and their five
projection helpers:

- the queue read paths &mdash; `getQueueItems` (filter + sort +
  paginated list), `getQueueItem`, `findByEntity`, `getStats` (eight
  counts + group-by distributions);
- the report read paths &mdash; `getReportsForEntity`, `getReports`,
  `getReport`;
- the appeal read paths &mdash; `getAppeals`, `getMyAppeals`,
  `getAppealStats`;
- the entity&rarr;VO projections &mdash; `toQueueVO` (with the
  batched user map and the `solution_comment` parent-id resolution),
  `toReportVO`, `toAppealVO`;
- the read aggregations &mdash; `toCountMap` (SQL group-by adapter),
  `buildUserMap` (batched author/assignee/reviewer fetch).

Twelve MyBatis mappers were constructor dependencies. Every
projection tweak landed in the same file as the write paths that
mutate queue/report/appeal state. Testing any read path required
mocking all twelve collaborators even though projection is pure
computation over already-loaded entities.

A `grep` confirmed the ten read methods had **no cross-module
callers** &mdash; only `ModerationController` invoked them. They were
therefore pass-through facade methods earning no locality next to the
state machine: deleting them from the service and serving them from a
dedicated module concentrates the projection rules instead of
duplicating them across the service and a future read-side caller.

This is the same shallow cluster already deepened for
`SubmissionProjection` (a58e5fa9d), `ProblemProjection` (32b17a4a0)
and `SubmissionPerformanceStats` (aac536fbc): entity&rarr;VO
projection and read-side aggregation sitting next to a state machine.

## The decision

A central **ModerationProjection** deep module owns every
entity&rarr;VO projection and read-side aggregation for the
moderation domain. Controllers depend on it directly for reads; the
service keeps the write paths and the authorisation-guarded appeal
lookup, delegating to the projection for the view shapes those write
paths still return.

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
  createUserWarning / createUserBan / updateContentFlagStatus
  helpers stay.

moderation/controller/ModerationController          (10 read endpoints → projection)
```

The `getAppeal(id, currentUserId)` authorisation check (isOwner /
isModerator, null-safe via `Objects.equals`) stays in the service.
The service reads the appeal row for the check, then hands the
entity to `projection.toAppealVO`; the projection never owns the
authz decision, and an unauthorised caller does not trigger the
projection's user-name lookups.

## Where it lives

- `moderation/projection/ModerationProjection.java` &mdash; interface.
- `moderation/projection/DefaultModerationProjection.java` &mdash;
  the only adapter; injects the five mappers that the read cluster
  needs.
- `moderation/service/ModerationService.java` &mdash; slimmed from 19
  to 9 methods (claim/assign/unassign/performAction/batchAction/
  createReport/createAppeal/getAppeal/reviewAppeal).
- `moderation/service/impl/ModerationServiceImpl.java` &mdash; 760
  to ~390 lines; keeps the `ActionContext` and the package-private
  helper trio (`createUserWarning` / `createUserBan` /
  `updateContentFlagStatus`).
- `moderation/controller/ModerationController.java` &mdash; 10 read
  endpoints delegate to the projection; write endpoints still call
  the service.

## Trade-offs

- **Read-side first, write-side later.** Following the established
  "one deepening dimension per change" rhythm, the appeal review /
  queue action handler / report creation state machine is untouched
  here. Its own deepening is a separate, motivated change.
- **Service still injects all 12 mappers.** The write paths use them
  (`resolveAuthorId`, `updateContentFlagStatus`, `createUserBan`,
  etc.). Mapper count is unchanged; the win is locality and test
  surface, not dependency count.
- **No dependency-inversion port.** All ten read methods have exactly
  one consumer (`ModerationController`). A port would be a
  one-adapter seam with no second adapter on the horizon (the
  ADR-0001 submission-contest case is different &mdash; there the
  consumer is a different module).

## Consequences

- **Locality**: the three entity&rarr;VO projections, the three list
  query builders (shared sort-default policy), and the two statistics
  aggregations now live in one module. A projection rule change is a
  single-file diff.
- **Leverage**: the queue endpoints share `buildUserMap` + `toQueueVO`
  inside the module instead of duplicating batched-user-fetch policy
  across call sites.
- **Interface is the test surface**: `ModerationControllerTest` (48
  tests) mocks a single `ModerationProjection` for the read paths
  instead of stubbing ten service methods through a
  twelve-collaborator facade. The projection's own rules can now be
  unit-tested with mapper mocks alone.
- **Behaviour is byte-for-byte identical**: same filters, same sort
  defaults, same `solution_comment` parent-id resolution, same
  `buildUserMap` batched fetch, same stats counts (including
  `appealMapper.countPending()` for `pendingAppealsCount`), same
  `MODERATION_QUEUE_NOT_FOUND` + `"Report not found: "` legacy error
  on `reportById`. Verified by `ModerationControllerTest` green
  (48/48).

## Related

- [[concepts/module-layering]] &mdash; Projection / Port / Inspector
  pattern
- [[archive/concepts/achievement-projection]] &mdash; ADR-0005, the same
  pattern applied to the achievement domain
- [[archive/concepts/admin-projection-inversion]] &mdash; ADR-0011, the
  admin module following the same template
- [[archive/concepts/notification-dispatch-and-preferences]] &mdash; ADR-004
  (the original notification-system landing record)
