---
title: Admin Projection Inversion
type: concept
tags: [admin, architecture, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/modules/admin/projection/
  - /tmp/architecture-review-1783160099.html
  - /tmp/architecture-review-1783341079.html
aliases: [ADR-0011, AdminXxxProjection, AdminReadModel, AdminProjection]
---

# Admin Projection Inversion

> [!note] This page is the landed record of **ADR-0011 — Admin Module
> Projection Inversion (AdminXxxProjection)**. Per
> [SCHEMA §3](../SCHEMA.md) the project keeps no separate
> `decisions/` dir &mdash; an ADR folds into `concepts/`.

## The problem

The UltiCode inversion series (ADR-0001, 0004, 0005, 0006, 0007, 0008,
0009, 0010) has lifted `Projection` deep modules and `Read Port`
interfaces out of seven peer modules (`moderation`, `achievement`,
`solution`, `forum`, `user`, `problemlist`, `problem`). The `admin`
module is the only module left where every read-shape lives inline
inside a `*ServiceImpl`.

Triggered by the architecture review run on 2026-07-04, which produced
`/tmp/architecture-review-1783160099.html`. The review graded 17
admin `*ServiceImpl` files against the deletion test and the
depth-debt scoring already used in the prior ADRs.

Top nine admin services by depth-debt (full table in the HTML
report):

| Service | LoC | Cross-module mappers | Inline VO overloads | Debt |
|---|--:|---|--:|--:|
| `AdminSubmissionServiceImpl` | 727 | submission, user, problem (3) | 3 &times; `toAdminVO` | 10 |
| `AdminUserServiceImpl` | 611 | user, permission (2) | `toVO` inline | 9 |
| `AdminSolutionServiceImpl` | 471 | solution, problem, user (3) | 3 &times; `toAdminVO` | 9 |
| `AdminForumServiceImpl` | 532 | forum, user, vote (3) | 3 &times; `toAdminVO` | 9 |
| `AdminAnalyticsServiceImpl` | 324 | contest, problem, subscription (3) | multi-domain | 8 |
| `AdminProblemListServiceImpl` | 417 | problemlist, problem, user (2) | cross-service | 7 |
| `AdminContestServiceImpl` | 490 | contest (self) | 1 &times; `toAdminVO` | 7 |
| `AdminCommentServiceImpl` | 392 | forum, solution (2) | cross-module | 7 |
| `AdminTagServiceImpl` | 344 | forum, problem (2) | cross-module | 7 |
| `AdminNotificationServiceImpl` | 385 | notification, user (2) | cross-module | 7 |

The shallow smell is the same shape in every case: a 300–700-LoC
`*ServiceImpl` that imports entities from other modules
(`com.ulticode.modules.{user,problem,contest}.entity.*`), inlines
3–4 overloads of `toAdminVO(entity, &hellip;)`, and hands them to
the controller for reads. The controller ends up reading through the
service because no module-owned `projection/` seam exists in `admin/`.

### What the HTML report got wrong (and got right)

The HTML review report listed `AdminProblemListServiceImpl.toSummaryVO`
as a "calls `problemListService.toVO()`" smell. On re-reading the
file (417 lines), there is no such call &mdash; the seam violation
was the **inline `toSummaryVO`** method (originally L397–416 of
that file), which duplicated the rules that already live in
`problemlist/projection/ProblemListProjection.toSummaryVO`. The
`private final ProblemListService problemListService` field
genuinely exists, but its two call sites are write delegations
(`createList`, `deleteList`) &mdash; which is the correct seam for
writes. This ADR's Stage 1 corrects the actual smell rather than
the over-stated one in the report.

## The decision

Invert the same way seven peer modules did: extract one
`AdminXxxProjection` interface + `DefaultAdminXxxProjection` impl
per admin area, controllers depend on the projection for reads, the
service keeps writes only (rejudge, ban / lift, audit). Mirrors
`ModerationProjection` / `AchievementProjection` shape exactly.

The `admin/port/adapter/` subsystem that already exists is **not**
to be merged into the projection package &mdash; they cover different
concerns:

- `admin/port/` &mdash; cross-module reads whose interface is owned
  by admin and implemented by an adapter in the providing module
  (`AdminSubmissionReadPort`, `AdminUserStatsReadPort`,
  `AdminCommentReadPort`).
- `admin/projection/` (new) &mdash; admin-internal entity&rarr;VO
  and read-side aggregation for VOs that the admin module itself
  defines (`AdminSubmissionVO`, `AdminUserVO`, &hellip;).

Projections return typed view objects, never entities or
`null`-bearing wrappers &mdash; the same rule ADR-0007/0008 already
enforce for ports.

## Why a `projection/` package, not one more `port/`

The peer pattern is consistent: `Projection` is the deep module for
*the same module's own read shape* (interface owned by the subject
module), `Port` is the deep module for *a cross-module typed read*
(interface owned by the consumer). The admin module has both:

- It consumes cross-module reads &mdash; already extracted as
  `AdminSubmissionReadPort` etc. (`admin/port/`).
- It produces its own admin-shaped VOs (`AdminSubmissionVO`,
  `AdminUserVO`, `AdminContestVO`, &hellip;) &mdash; these are the
  projection territory.

Conflating the two would put admin's own VO rules behind a port
whose contract was nominally owned by admin but read by the
controller &mdash; an unnecessary inversion, and one that would
re-couple the projection to the port's external surface.

## Where it lives

- `admin/projection/AdminProblemListProjection.java` (Stage 1) &mdash;
  interface for the problem-list area.
- `admin/projection/DefaultAdminProblemListProjection.java` (Stage 1)
  &mdash; delegates the entity&rarr;VO shape to
  `problemlist/projection/ProblemListProjection.toSummaryVO` (no
  duplication).
- `admin/projection/AdminSubmissionProjection.java` (Stage 2, landed
  2026-07-06) &mdash; the highest-debt case.
- `admin/projection/DefaultAdminSubmissionProjection.java` (Stage 2)
  &mdash; ~250 LoC, batch-loads cross-module enrichment.
- `admin/projection/AdminAnalyticsProjection.java` (Stage 3) &mdash;
  multi-domain aggregator (analytics only, joins multiple domains).
- Per-stage: a `Default<X>ProjectionTest` shape unit test pins the
  read cluster independently of the write service.

## Phased rollout

### Stage 1 &mdash; landed 2026-07-04 (`AdminProblemListServiceImpl`)

- Inject `ProblemListProjection` alongside `ProblemListService`.
- Replace inline `toSummaryVO` (entity&rarr;VO copy) with delegation
  to `problemListProjection.toSummaryVO`.
- Keep `ProblemListService` for write delegation (`createList`,
  `deleteList`).

Diff: ~30 lines changed; behaviour preserved for reads, writes
unchanged.

### Stage 2 &mdash; landed 2026-07-06 (Submission), 2026-07-07 (User)

> **2026-07-06 update**: Stage 2 Submission (debt 10/10, highest
> priority) has landed. `AdminSubmissionProjection` +
> `DefaultAdminSubmissionProjection` extracted;
> `AdminSubmissionServiceImpl` slimmed from 727 to ~340 LoC;
> cross-module entity imports (`User`, `Problem`) and their mappers
> left the service; `AdminSubmissionServiceImplTest` +
> `AdminSubmissionControllerTest` updated;
> `AdminSubmissionProjectionTest` added.

> **2026-07-07 update**: Stage 2 User (debt 9/10) has landed.
> `AdminUserProjection` + `DefaultAdminUserProjection` extracted;
> `UserManagementServiceImpl` slimmed from 507 to 380 LoC (writes
> only); cross-module entity imports (`RolePermission`,
> `UserPermission`) and the `AdminUserStatsReadPort` /
> `RolePermissionMapper` / `PermissionService` deps left the service;
> `AdminUserController` updated to depend on the projection for reads
> and the service for writes (mirrors `AdminSubmissionController`);
> `UserPermissionServiceImpl` switched from
> `UserManagementService.getUserById` to
> `AdminUserProjection.getUserById` for the post-grant / post-revoke
> VO composition; `UserManagementServiceImplTest` deleted (all 6 read
> tests migrated verbatim to `AdminUserProjectionTest`);
> `UserPermissionServiceImplTest` mock updated. The remaining three
> services below are still next-commit deliverables.

> **2026-07-07 update (Stage 2 Solution)**: Stage 2 Solution (debt
> 9/10) has landed. `AdminSolutionProjection` +
> `DefaultAdminSolutionProjection` extracted;
> `AdminSolutionServiceImpl` slimmed from 472 to ~245 LoC (writes
> only); cross-module entity imports (`User`, `UserMapper`) and the
> `batchLoadUsers` / `batchLoadProblems` / `toListItemVO` /
> `toAdminVO(Solution, Map, Map)` / `toAdminVO(Solution)` helpers left
> the service; `AdminSolutionController` updated to depend on the
> projection for reads and the service for writes (mirrors
> `AdminSubmissionController`); `AdminSolutionServiceImpl` flag /
> unflag write paths now delegate to
> `AdminSolutionProjection.getSolution(id)` for post-write VO
> composition (same pattern `UserPermissionServiceImpl` uses against
> `AdminUserProjection`); `AdminSolutionServiceImplTest` constructor
> updated to `(solutionMapper, problemMapper, solutionProjection)`
> (UserMapper mock dropped, projection mock added; existing 5 write
> tests cover unchanged writes); `AdminSolutionProjectionTest` added
> pinning the read cluster (single-detail `getSolution` happy + throw
> + orphan-enrichment, `getFlaggedSolutions` BUG-Q9 isDeleted=false
> invariant, `getSolutions` isDeleted branch routing both ways,
> empty-page batch-load skip). `Forum`, `Contest`, `Comment` remain
> next-commit deliverables.

For each of `AdminSolutionServiceImpl`, `AdminForumServiceImpl`,
`AdminContestServiceImpl`, `AdminCommentServiceImpl`, each landing as
its own commit:

```
admin/projection/AdminSubmissionProjection.java          (interface)
admin/projection/DefaultAdminSubmissionProjection.java   (@Component, ~250 LoC)
admin/service/impl/AdminSubmissionServiceImpl.java        (refactor, deletes ~150 LoC)
admin/controller/AdminSubmissionController.java          (depend on projection for reads)
test/.../admin/projection/AdminSubmissionProjectionTest.java  (shape unit test)
```

Three batches to keep reviewable: (a) Submission + User (highest
debt), (b) Solution + Forum + Contest, (c) Comment + Tag +
Notification.

### Stage 2 template (reference: `DefaultProblemListProjection`)

For every Stage 2 service the projection follows the same shape:

1. **Interface** in `admin/projection/<Name>Projection.java` declares
   the read-side methods that return typed `Admin*VO` shapes. Never
   returns entities or `null`-bearing wrappers &mdash; same rule
   ADR-0007/0008 enforce for ports.
2. **`Default<Name>Projection`** is `@Component`, depends on the
   mappers whose `selectBatchIds` / `selectById` / `selectPage`
   calls it needs (admin's own + any cross-module mapper the read
   shape needs), and concentrates every `entity &rarr; Admin*VO`
   projection rule. Batch-loads cross-module enrichment via
   `selectBatchIds` like `DefaultProblemListProjection` does &mdash;
   avoids the per-row N+1 the inline `toAdminVO` blocks currently
   incur.
3. **Service** keeps write paths only (rejudge, ban / lift, audit,
   flag / unflag). Read endpoints delegate to the projection.
   Cross-module entity imports leave the service &mdash; admin only
   depends on typed views + their mappers inside the projection.
4. **Controller** depends on the projection for reads, the service
   for writes.

### Stage 3 &mdash; after Stage 2 lands

`AdminAnalyticsProjection` + three read ports
(`AdminContestStatsReadPort`, `AdminProblemStatsReadPort`,
`AdminSubscriptionStatsReadPort`). This is the multi-domain
aggregator where both patterns apply at once &mdash; read ports for
cross-module read shape, projection for the aggregation rules + VO
shape.

### 2026-07-06 architecture review updates

In addition to the admin projection inversion work, the 2026-07-06
review (`/tmp/architecture-review-1783341079.html`) surfaced six
new candidates that landed in this commit range:

- **Card 1 (security)** &mdash; `shared/markdown-utils/` package
  consolidates console + management markdown plumbing and closes
  the management XSS gap (sanitization now baked into
  `renderMarkdown`).
- **Card 2 (leverage)** &mdash; `shared/http-client/` package
  replaces the byte-for-byte duplicate `request.ts` (411 + 440 LoC)
  with a single `createHttpClient(config)` factory.
- **Card 3 (debt)** &mdash; `User` type unified to
  `shared/auth-core/src/types.ts` as the single source of truth;
  both apps re-export.
- **Card 4 (security-adjacent)** &mdash; console's bespoke CSRF
  manager replaced with `createCsrfTokenManager()` from auth-core.
- **Card 5 (correctness)** &mdash; `EdgeOperationsServiceImpl` no
  longer re-queries `EdgeOperationMapper.countByTargetAndOperation`
  for vote counts; uses the counts `VoteService.vote()` already
  produced.
- **Card 6 (compliance)** &mdash; `common.audit.AuditPolicy` catalog
  answers "what does the system audit / ban-check?" in one file;
  coverage test fails CI when the catalog drifts from `@Audited` /
  `@CheckBan` annotations.

Stages 2/3 of this ADR remain next-commit deliverables following
the template above.

## Consequences

- **Locality**: every admin `*VO` projection rule concentrates into
  one module per area, with the write state machine kept out.
- **Leverage**: per-row enrichment batch-loading moves into the
  projection (no N+1; same pattern `DefaultProblemListProjection`
  uses).
- **Test surface**: `Default<X>ProjectionTest` pins the read cluster
  independently of the write service.
- **Behaviour preserved**: each Stage 2 service ships with the
  existing controller test suite green at the new LOC; projection
  rules are byte-for-byte identical to the inline `toAdminVO` they
  replaced.

## Related

- [[concepts/moderation-projection]] &mdash; ADR-0004, the
  `ModerationProjection` template
- [[concepts/achievement-projection]] &mdash; ADR-0005, the
  `AchievementProjection` template
- [[concepts/admin-user-stats-read-port]] &mdash; ADR-0007, the
  AdminReadModel seam user phase
- [[concepts/admin-comment-read-port]] &mdash; ADR-0008
- `/tmp/architecture-review-1783160099.html` &mdash; the
  architecture review report that triggered the inversion
- `/tmp/architecture-review-1783341079.html` &mdash; the 2026-07-06
  follow-up review (Cards 1–6)
