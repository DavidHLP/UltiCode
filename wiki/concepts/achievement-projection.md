---
title: Achievement Projection
type: concept
tags: [achievement, architecture, type/concept]
status: living
updated: 2026-07-04
sources:
  - backend-spring/src/main/java/com/ulticode/modules/achievement/projection/
  - docs/adr/0005-achievement-projection.md
aliases: [ADR-0005, Achievement Read Projection]
---

# Achievement Projection

> [!note] This page is the landed record of **ADR-0005 — Achievement
> Projection extraction**. Per [SCHEMA §3](../SCHEMA.md) the project keeps
> no separate `decisions/` dir &mdash; an ADR folds into `concepts/`.

## The problem
`AchievementServiceImpl` (393 lines) mixed the achievement CRUD write paths
with five pure-read methods (catalog list/getById, user-progress
getUserProgress/getUserAchievements, points getUserPoints) and their
projection helpers. Two constructor dependencies were load-bearing only for
the reads: `SubmissionMapper` (the progress counters) and a fully **dead**
`ContestParticipantMapper` field. Every progress-rule tweak landed next to
the write state machine, and read-path tests had to stub the cross-module
submission counters through a four-mapper facade.

## The decision
A central **AchievementProjection** deep module owns every entity&rarr;VO
projection and read-side aggregation for the achievement domain. Write
paths stay on `AchievementService` and end with `projection.toVO(entity)`
for the post-action view (mirroring `ModerationProjection#toAppealVO`,
ADR-0004).

**Read cluster (5 + 1 facade).** `getById`, `list` (category/tier/isActive
filters), `getUserProgress` (computed currentValue/target/percentage/
nextMilestone &mdash; the cross-module consumer is
`UserController.getAchievementProgress`), `getUserAchievements`
(earned/earnedAt/progress/target), `getUserPoints` (points+count), plus
`toVO` as the write-path facade.

**getUserProgress vs getUserAchievements are deliberately not merged.** They
return different DTO shapes for different front-end pages
(`AchievementProgressVO` with computed stats vs `AchievementProgressDTO`
with earned state). See LOW #5 in the legacy service-interface Javadoc.

## Where it lives
- `achievement/projection/AchievementProjection.java` &mdash; interface.
- `achievement/projection/DefaultAchievementProjection.java` &mdash; the only
  adapter; injects `AchievementMapper`, `UserAchievementMapper`,
  `SubmissionMapper`.
- `achievement/service/AchievementService.java` &mdash; slimmed to 4 methods
  (create / update / delete / findByKey).
- `achievement/controller/AchievementController.java` &mdash; 6 read
  endpoints delegate to the projection; 3 write endpoints still call the
  service.
- `user/controller/UserController.java` &mdash; `getAchievementProgress` calls
  the projection directly (cross-module read consumer).

## Trade-offs
- **Read-side first, write-side later.** Following ADR-0004's "one
  deepening dimension per change", the trigger / award write path
  (`AchievementTriggerServiceImpl`) is untouched here. Its own
  deepening (a write-side port or award-evaluator module) is a separate,
  motivated change.
- **Constructor dependencies dropped 4 &rarr; 2 on the service.** This is
  real leverage, not a cosmetic move: instantiating the write path in a
  test no longer stubs submission counters, and the dead
  `ContestParticipantMapper` is gone.
- **No dependency-inversion port.** `getUserProgress` has exactly one
  cross-module consumer and it is a controller, not another module's
  service. A port would be a one-adapter seam with no second adapter on the
  horizon (ADR-0001 vs this case).

## Related
[[concepts/module-layering]] · `docs/adr/0005-achievement-projection.md`
