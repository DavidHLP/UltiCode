---
title: Admin User-Stats Read Port
type: concept
tags: [admin, architecture, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/modules/admin/port/AdminUserStatsReadPort.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/port/adapter/AdminUserStatsReadAdapter.java
aliases: [ADR-0007, AdminUserStatsReadPort, AdminReadModel User Phase]
---

# Admin User-Stats Read Port

> [!note] This page is the landed record of **ADR-0007 — Admin 用户统计
> Read Port 提取 (AdminUserStatsReadPort)**. Per [SCHEMA §3](../SCHEMA.md)
> the project keeps no separate `decisions/` dir &mdash; an ADR folds
> into `concepts/`.

## The problem

`AdminUserServiceImpl.populateStats()` is the per-user stats aggregator
behind the admin user-detail page. Before this ADR it injected two
cross-module mappers directly:

- `submission.mapper.SubmissionMapper` &mdash; `countByUserId` /
  `countAcceptedProblemsByUserId` / `calculateStreak`
- `solution.mapper.SolutionMapper` &mdash; `countByUserId`

This is the second cross-module direct-connection that the
AdminReadModel seam (`AdminSubmissionReadPort` for the dashboard
global stats) left behind: the admin user-detail page had to reach
into two other modules' mappers just to compute 4 per-user counters.
The Javadoc on `AdminSubmissionReadPort` already states
*"Future phases add admin reads for user, contest, and forum"* &mdash;
this ADR lands the user dimension.

The submission module's existing `SubmissionAnalyticsPort` only
exposes dashboard-global methods (`countByStatus` /
`countByLanguage`); forcing the 4 per-user methods through it would
break that port's cohesion (a violation of ISP).

The deletion test confirms the seam is real: deleting the port call
in `populateStats` forces `AdminUserServiceImpl` to reach back into
the two mappers + re-write 4 null guards &mdash; complexity is
concentrated into the adapter rather than shifted.

## The decision

Extract **`AdminUserStatsReadPort`** as a deep module, encapsulating
"aggregate cross-module stats for one user" on the read side:

```
admin/port/AdminUserStatsReadPort.java              // interface (4 methods)
admin/port/adapter/AdminUserStatsReadAdapter.java   // the only @Component adapter
```

Interface shape (returns primitive types, never boxed):

```java
long countSubmissionsByUserId(String userId);
long countAcceptedProblemsByUserId(String userId);
long countSolutionsByUserId(String userId);
int  calculateSubmissionStreak(String userId);
```

- **Returns `long` / `int`, not `Long` / `Integer`**: the adapter owns
  the null→0 fallback, the interface guarantees non-null, and the
  caller (`AdminUserServiceImpl`) never writes another null guard
  &mdash; this is the deep module's leverage.
- **The adapter is the only place in admin that touches those two
  mappers**: cross-module dependencies move from `AdminUserServiceImpl`
  to a single adapter. The rest of admin sees only the typed port.

`AdminUserServiceImpl` becomes:

- 2 dependencies removed (`SubmissionMapper` / `SolutionMapper`);
- 1 new `userStatsReadPort` dependency;
- `populateStats` shrinks from 12 lines (with 4 null guards) to 6;
- constructor parameter count: 7 → 6.

Test responsibility re-divide:

- **Adapter unit test** (new `AdminUserStatsReadAdapterTest`):
  inherits the null→0 fallback verification (the old
  `AdminUserServiceImplTest.nullMapperReturns_defaultsToZero` null
  path migrates here) + value pass-through.
- **ServiceImpl unit test**: mocks `AdminUserStatsReadPort` (primitive
  returns, never null), verifies only port return → VO assembly; no
  longer stands up two mapper mocks.

## Where it lives

- `admin/port/AdminUserStatsReadPort.java` &mdash; interface, owned
  by admin.
- `admin/port/adapter/AdminUserStatsReadAdapter.java` &mdash; the only
  adapter; injects `SubmissionMapper` + `SolutionMapper`.
- `admin/service/impl/AdminUserServiceImpl.java` &mdash;
  `populateStats` delegates to the port; constructor parameter count
  7 → 6.
- `admin/service/impl/AdminUserServiceImplTest.java` &mdash; 7
  scattered mapper stubs collapse to a single `stubStats` helper.

## Consequences

**Positive**

- `AdminUserServiceImpl` no longer imports
  `submission.mapper` / `solution.mapper`; cross-module coupling
  moves from the ServiceImpl to a single adapter. The
  AdminReadModel seam closes on the user dimension.
- Null handling is centralized in the adapter (4
  `n == null ? 0 : n` collapses), the interface narrows, and
  call-sites become clean. The service test's 7 scattered mapper
  stubs collapse into one `stubStats` helper.
- per-user stats get an independent test surface: the adapter covers
  the null fallback, the ServiceImpl covers the port→VO assembly, no
  more entanglement.
- Naming / package / test conventions are byte-for-byte aligned with
  `AdminSubmissionReadPort`, so the next problem / contest / forum
  dimension can be lifted the same way with zero copying cost.

**Negative / trade-offs**

- The adapter holds `SubmissionMapper` + `SolutionMapper` (two
  modules). Acceptable: the adapter is a boundary class, its job is
  to translate admin's typed read request into mapper calls; mappers
  are stateless tools.
- Single consumer (only `AdminUserServiceImpl.populateStats`).
  Accepted anyway: value comes from concentration + test surface +
  AdminReadModel seam progress, not from removing duplication. If
  the user profile / dashboard ever needs per-user stats too, the
  port is in place.
- The interface returns `long` but
  `AdminUserVO.UserStatsInfo` is an `int`; the call site does
  `(int)`. Acceptable: the counters never exceed `Integer.MAX_VALUE`,
  and the pre-existing code already did `Long.intValue()`.

## Rejected alternatives

- **Push the 4 per-user methods into the existing
  `SubmissionAnalyticsPort`** (admin keeps using
  `AdminSubmissionReadPort extends SubmissionAnalyticsPort`).
  Rejected: ① `SubmissionAnalyticsPort` is currently dashboard-global
  semantics, mixing per-user would break the port's cohesion (ISP);
  ② `solution.countByUserId` has no natural home (the solution
  module has no `port/`); ③ interface bloat violates the deep-module
  concentration rule.
- **Create a `UserStatsProjection` inside the user module** (DDD
  says user stats belong to the user domain). Rejected: ① admin is
  the only current consumer, so the port belongs to admin (the
  consumer side) &mdash; matches the established
  `AdminSubmissionReadPort` convention; ② the user module currently
  does not hold submission / solution mappers, so a projection would
  reverse-depend on them and risk a cycle. Defer until the user
  module evolves its own stats read model.
- **Route `populateStats` through the existing
  `AdminSubmissionReadPort`**. Rejected: that port is
  dashboard-global (`findById` / `countAll` / `countByStatus` /
  `countByLanguage`) and contains no per-user methods. Extending it
  with 4 per-user methods would break its single-responsibility
  shape.

## Related

- [[concepts/module-layering]] &mdash; Projection / Port / Inspector
  pattern
- `admin/port/AdminSubmissionReadPort.java` &mdash; AdminReadModel
  seam phase 1 (its Javadoc self-documents user / contest / forum as
  the upcoming phases)
- `submission/port/SubmissionAnalyticsPort.java` &mdash; dashboard
  global stats port (this ADR deliberately does **not** pollute its
  semantics)
- [[archive/concepts/admin-comment-read-port]] &mdash; ADR-0008, the
  forum-dimension sibling
- [[archive/concepts/achievement-projection]] / [[archive/concepts/moderation-projection]] /
  [[archive/concepts/problem-detail-port]] &mdash; sibling port / projection
  extractions
