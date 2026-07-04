# ADR-0011: Admin Module Projection Inversion (AdminXxxProjection)

## Status

Accepted · 2026-07-04 · phased rollout. Stage 1 (Candidate 3 — ProblemList
seam fix in admin) lands in this commit. Stages 2 and 3 are next-commit
deliverables documented here so a future architecture review doesn't
re-survey the same debt.

## Context

The UltiCode inversion series (ADR-0001, 0004, 0005, 0006, 0007, 0008,
0009, 0010) has lifted `Projection` deep modules and `Read Port`
interfaces out of seven peer modules (`moderation`, `achievement`,
`solution`, `forum`, `user`, `problemlist`, `problem`). The
`admin` module is the only module left where every read-shape lives
inline inside a `*ServiceImpl`.

Triggered by the architecture review run on 2026-07-04, which produced
`/tmp/architecture-review-1783160099.html`. The review graded 17 admin
`*ServiceImpl` files against the deletion test and the depth-debt scoring
already used in the prior ADRs.

Top nine admin services by depth-debt (full table in the HTML report):

| Service                                  | LoC | Cross-module mappers | Inline VO overloads | Debt |
|------------------------------------------|----:|----------------------|---------------------|-----:|
| AdminSubmissionServiceImpl               | 727 | submission, user, problem (3) | 3 × toAdminVO | 10 |
| AdminUserServiceImpl                     | 611 | user, permission (2) | toVO inline | 9 |
| AdminSolutionServiceImpl                 | 471 | solution, problem, user (3) | 3 × toAdminVO | 9 |
| AdminForumServiceImpl                    | 532 | forum, user, vote (3) | 3 × toAdminVO | 9 |
| AdminAnalyticsServiceImpl                | 324 | contest, problem, subscription (3) | multi-domain | 8 |
| AdminProblemListServiceImpl              | 417 | problemlist, problem, user (2) | cross-service | 7 |
| AdminContestServiceImpl                  | 490 | contest (self) | 1 × toAdminVO | 7 |
| AdminCommentServiceImpl                  | 392 | forum, solution (2) | cross-module | 7 |
| AdminTagServiceImpl                      | 344 | forum, problem (2) | cross-module | 7 |
| AdminNotificationServiceImpl             | 385 | notification, user (2) | cross-module | 7 |

The shallow smell is the same shape in every case: a 300-700 LoC
`*ServiceImpl` that imports entities from other modules
(`com.ulticode.modules.{user,problem,contest}.entity.*`), inlines
3-4 overloads of `toAdminVO(entity, …)`, and hands them to the
controller for reads. The controller ends up reading through the
service because no module-owned `projection/` seam exists in
`admin/`.

### What the HTML report got wrong (and got right)

The HTML review report listed `AdminProblemListServiceImpl.toSummaryVO`
as a "calls `problemListService.toVO()`" smell. On re-reading the file
(417 lines), there is no such call — the seam violation was the
**inline `toSummaryVO`** method (originally L397-416 of that file),
which duplicated the rules that already live in
`problemlist/projection/ProblemListProjection.toSummaryVO`. The
`private final ProblemListService problemListService` field genuinely
exists, but its two call sites are write delegations
(`createList`, `deleteList`) — which is the correct seam for writes.
This ADR's Stage 1 corrects the actual smell rather than the
over-stated one in the report.

## Decision

Invert the same way seven peer modules did:
extract one `AdminXxxProjection` interface + `DefaultAdminXxxProjection`
impl per admin area, controllers depend on the projection for reads,
the service keeps writes only (rejudge, ban/lift, audit).
Mirrors `ModerationProjection` / `AchievementProjection` shape exactly.

The `admin/port/adapter/` subsystem that already exists is **not** to
be merged into the projection package — they cover different concerns:

- `admin/port/` — cross-module reads whose interface is owned by admin
  and implemented by an adapter in the providing module
  (`AdminSubmissionReadPort`, `AdminUserStatsReadPort`,
  `AdminCommentReadPort`).
- `admin/projection/` (new) — admin-internal entity→VO and read-side
  aggregation for VOs that the admin module itself defines
  (`AdminSubmissionVO`, `AdminUserVO`, …).

Projections return typed view objects, never entities or `null`-bearing
wrappers — the same rule ADR-0007/0008 already enforce for ports.

## Why a `projection/` package, not one more `port/`

The peer pattern is consistent: `Projection` is the deep module for
*the same module's own read shape* (interface owned by the subject
module), `Port` is the deep module for *a cross-module typed read*
(interface owned by the consumer). The admin module has both:

- It consumes cross-module reads — already extracted as
  `AdminSubmissionReadPort` etc. (`admin/port/`).
- It produces its own admin-shaped VOs (`AdminSubmissionVO`,
  `AdminUserVO`, `AdminContestVO`, …) — these are the projection
  territory.

Conflating the two would put admin's own VO rules behind a port whose
contract was nominally owned by admin but whose *implementation* would
also live in admin — the seam is the seam only when exactly one side
owns it. Standard Projection-shape keeps the ownership one-way.

## Why not "just keep `toAdminVO` in the service"

The deletion test fails: deleting `AdminSubmissionServiceImpl.toAdminVO`
just moves the duplication to wherever the controller decides to call it.
The projection concentrates it. The leverage comes from the projection's
batch-load helpers (`selectBatchIds` for user-map, problem-map) — those
helpers currently sit as `inline` blocks inside each service method,
re-implemented 3+ times per service.

## Consequences

### Positive

- Controllers depend on a narrow typed seam (`AdminSubmissionProjection`)
  and can be unit-tested with a single stub — no service mock, no
  mapper mocks, no entity construction.
- Adding a new admin field touches one `toAdminVO`, not four.
- Cross-module entity imports (`com.ulticode.modules.user.entity.User`)
  leave the service — admin only depends on typed views + their
  mappers inside the projection.
- Symmetry with `forum`, `user`, `solution`, `moderation`,
  `achievement`, `problemlist`, `problem` — `admin` is no longer the
  outlier.

### Negative / risks

- One extra file per admin area (interface + impl) — but each is
  small and lands next to the service it replaces.
- Per-row enrichment calls (`userMapper.selectById(authorId)`) are
  still N+1. Stage 2 batch-loads them like `DefaultProblemListProjection`
  does.

## Phased rollout

### Stage 1 — this commit (`AdminProblemListServiceImpl`)

- Inject `ProblemListProjection` alongside `ProblemListService`.
- Replace inline `toSummaryVO` (entity→VO copy) with delegation to
  `problemListProjection.toSummaryVO`.
- Keep `ProblemListService` for write delegation (`createList`,
  `deleteList`).

Diff: ~30 lines changed; behaviour preserved for reads, writes unchanged.

### Stage 2 — next commit(s)

For each of `AdminSubmissionServiceImpl`, `AdminUserServiceImpl`,
`AdminSolutionServiceImpl`, `AdminForumServiceImpl`,
`AdminContestServiceImpl`, `AdminCommentServiceImpl`, each landing
as its own commit:

```
admin/projection/AdminSubmissionProjection.java          (interface)
admin/projection/DefaultAdminSubmissionProjection.java   (@Component, ~250 LoC)
admin/service/impl/AdminSubmissionServiceImpl.java        (refactor, deletes ~150 LoC)
admin/controller/AdminSubmissionController.java          (depend on projection for reads)
test/.../admin/projection/AdminSubmissionProjectionTest.java  (shape unit test)
```

Three batches to keep reviewable: (a) Submission + User (highest debt),
(b) Solution + Forum + Contest, (c) Comment + Tag + Notification.

### Stage 3 — after Stage 2 lands

`AdminAnalyticsProjection` + three read ports
(`AdminContestStatsReadPort`, `AdminProblemStatsReadPort`,
`AdminSubscriptionStatsReadPort`). This is the multi-domain aggregator
where both patterns apply at once — read ports for cross-module
read shape, projection for the aggregation rules + VO shape.

## See also

- ADR-0004 (`ModerationProjection` template)
- ADR-0005 (`AchievementProjection` template)
- ADR-0007 (`AdminUserStatsReadPort` — same admin module, parallel seam)
- ADR-0008 (`AdminCommentReadPort`)
- `/tmp/architecture-review-1783160099.html` (architecture review report)
