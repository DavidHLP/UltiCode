# ADR-0005: Achievement 模块提取 Projection 深模块

- **Status**: Accepted
- **Date**: 2026-07-04
- **Scope**: `backend-spring` — achievement, user
- **Supersedes**: none
- **Tags**: architecture, deep-module, projection, locality

## Context

Before 2026-07-04, `AchievementServiceImpl` (393 lines) mixed the achievement
**CRUD write paths** (`create` / `update` / `delete` / `findByKey`) with five
**pure-read** methods and their projection / aggregation helpers:

- the achievement-catalog reads &mdash; `list` (filtered + paginated),
  `getById`;
- the user-progress reads &mdash; `getUserProgress` (computed
  currentValue / target / percentage / nextMilestone, consumed cross-module
  by `UserController.getAchievementProgress`), `getUserAchievements` (earned
  / progress / target), `getUserPoints` (points + count aggregation);
- the entity&rarr;VO projection &mdash; `toVO`;
- the progress math &mdash; `getTypeFromCriteria`, `getTargetFromCriteria`,
  `calculateCurrentValue`, `calculateNextMilestone`, `buildProgressVO`,
  `buildProgressDTO`.

Four MyBatis mappers were constructor dependencies
(`AchievementMapper`, `UserAchievementMapper`, `SubmissionMapper`,
`ContestParticipantMapper`). Two of those were load-bearing only for the read
paths:

- `SubmissionMapper` &mdash; used solely by `getUserProgress` /
  `getUserAchievements` for the `countAcceptedProblemsByUserId` /
  `countByUserId` counters;
- `ContestParticipantMapper` &mdash; **injected but never referenced anywhere
  in the file** (dead dependency).

Every progress-rule tweak landed in the same file as the CRUD state machine.
Testing any read path required stubbing the two cross-module submission
counters even though projection is pure computation over already-loaded
entities.

A `grep` confirmed the five read methods had **no cross-module method
callers** except `UserController.getAchievementProgress` &rarr;
`getUserProgress`. They were therefore pass-through facade methods earning
no locality next to the write paths: deleting them from the service and
serving them from a dedicated module concentrates the projection rules
instead of duplicating them across the service and a future read-side
caller.

This is the same shallow cluster already deepened for `ModerationProjection`
(ADR-0004), `ProblemProjection`, `SubmissionProjection`,
`SubmissionPerformanceStats`, `SearchReadProjection`, `SolutionProjection`,
`ContestProjection`, `ProblemListProjection`: entity&rarr;VO projection and
read-side aggregation sitting next to a state machine.

## Decision

Extract an `AchievementProjection` deep module that owns every projection
rule and read-side aggregation for the achievement domain. Controllers
depend on it directly for reads; the service keeps the write paths,
delegating to the projection for the view shapes those write paths still
return.

```
achievement/projection/AchievementProjection          (interface)
  ├── getById(String) : AchievementVO
  ├── list(AchievementQueryDTO) : PageResult<AchievementVO>
  ├── getUserProgress(String) : List<AchievementProgressVO>
  ├── getUserAchievements(String) : List<AchievementProgressDTO>
  ├── getUserPoints(String) : UserPointsVO
  └── toVO(Achievement)                   // facade for the service write paths

achievement/projection/DefaultAchievementProjection   (@Service, only adapter)
  injects: AchievementMapper, UserAchievementMapper, SubmissionMapper

achievement/service/AchievementService                (interface, slimmed 9→4)
  keeps: create / update / delete / findByKey
  drops: the five pure-read methods above

achievement/service/impl/AchievementServiceImpl       (393 → ~140 lines)
  injects AchievementProjection; write paths end with
  `projection.toVO(achievement)` (post-create / post-update view).
  Constructor mapper dependencies: 4 → 2 (AchievementMapper +
  UserAchievementMapper only).

achievement/controller/AchievementController          (6 read endpoints → projection)
  3 write endpoints still call AchievementService.

user/controller/UserController                        (cross-module read consumer)
  getAchievementProgress now calls `achievementProjection.getUserProgress`
  instead of `achievementService.getUserProgress`.
```

The `getById` NOT_FOUND check, the `list` invalid-category BAD_REQUEST
check, and the `findByKey` duplicate-key check all stay where they were
(findByKey stays on the service &mdash; it serves the write paths'
duplicate-key guard). The projection never owns a write-path authz or
uniqueness decision.

`getUserProgress` vs `getUserAchievements` are deliberately **not** collapsed:
they return different DTO shapes (`AchievementProgressVO` with computed
percentage/nextMilestone vs `AchievementProgressDTO` with earned/earnedAt)
for different front-end pages. This contract is documented in the original
service-interface Javadoc (LOW #5) and preserved verbatim in the projection.

## Consequences

- **Locality**: the three progress builders, the catalog query builders, the
  points aggregation, and the criteria-JSON math now live in one module. A
  progress-rule change is a single-file diff, not a diff that also touches
  the achievement CRUD state machine.
- **Leverage (real, not cosmetic)**: `AchievementServiceImpl` constructor
  dependencies dropped from **4 mappers to 2**. `SubmissionMapper` (cross-
  module read dependency) moved to the projection where the counters are
  actually used; the dead `ContestParticipantMapper` field was removed
  entirely. Instantiating the write path in a test no longer requires
  stubbing submission counters.
- **Interface is the test surface**: the 11 migrated read-path tests
  (`AchievementProjectionTest`) mock three collaborators instead of stubbing
  five service methods through a four-mapper facade. The projection's own
  rules can now be unit-tested with mapper mocks alone.
- **Behaviour is byte-for-byte identical**: same `list` filters and
  `orderByAsc(category, tier)` sort, same `findAllActive` / `findByUserId`
  pre-fetch, same `selectBatchIds` points aggregation, same null-criteria
  fallback (target/currentValue → 0), same milestone tables, same
  `ACHIEVEMENT_NOT_FOUND` + `BAD_REQUEST` "Invalid category" errors.
  Verified by `AchievementProjectionTest` + `AchievementServiceTest` green
  (24/24).
- `AchievementService` interface drops five pure-read methods. The only
  cross-module caller (`UserController.getAchievementProgress`) was rewired
  to the projection; no other module called them, so no further rewiring is
  needed.
- The service still injects `AchievementMapper` + `UserAchievementMapper`
  (the write paths use them: duplicate-key check, cascade delete in
  `delete`, etc.).

## Alternatives considered

- **Split into three services** (`AchievementCrudService` /
  `AchievementProgressService` / keep `AchievementTriggerService`). Rejected:
  it moves the write paths too, multiplying the blast radius (transactions,
  the create&rarr;toVO post-action view, the delete cascade). The
  established rhythm in this codebase (ADR-0004) is one deepening dimension
  per change &mdash; read-side projection first, leaving the write-side
  organisation for a separate, motivated change.
- **Dependency-inversion port for achievement reads** (mirror
  `AdminSubmissionReadPort`). Rejected: `getUserProgress` has exactly one
  cross-module consumer, and that consumer is a controller (`UserController`),
  not another module's service layer. A port here would be a one-adapter
  seam with no second adapter on the horizon &mdash; a hypothetical seam,
  not a real one. A projection injected directly by the consuming
  controller is the established pattern (`ModerationController` &rarr;
  `ModerationProjection`).
- **Keep `getUserProgress` on the service for the cross-module caller**.
  Rejected: it would split the read cluster across two homes (four reads in
  the projection, one in the service), breaking locality &mdash; the very
  win this extraction exists to capture. Cross-module controller consumers
  injecting a projection is the intended usage.

## Verification

- `./mvnw compile -B` &rarr; BUILD SUCCESS
- `./mvnw test -B -Dtest='AchievementProjectionTest,AchievementServiceTest'`
  &rarr; **24/24 green** (read paths migrated; CRUD + trigger + type tests
  unchanged; write-path tests stub `AchievementProjection#toVO` with a
  passthrough answer so they still verify the service's own mutation logic).

## References

- `docs/adr/0004-moderation-projection.md` &mdash; same deepening pattern
- Ousterhout, *A Philosophy of Software Design* &mdash; "deep modules"
- Prior art in repo: `ModerationProjection`, `ProblemProjection`,
  `SubmissionProjection`, `SearchReadProjection`, `SolutionProjection`,
  `ContestProjection`, `ProblemListProjection`
