---
title: Contest
type: entity
tags: [contest, core, type/entity]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/contest/
  - init-db/migrations/V20260617120000__Contest_Scoring_Hardening.sql
  - init-db/migrations/V20260617130000__Contest_Slug_Unique.sql
  - init-db/migrations/V20260617140000__Contest_Real_Unique_And_Session_Length.sql
aliases: [比赛]
---

# Contest

Timed competitions: a curated problem set, a scoring rule, participants, live
standings, and (for virtual contests) isolated replay sessions. Heavily hardened
in the 06-17 migrations for scoring integrity, slug uniqueness, and virtual
fencing.

> Virtual mode: [[concepts/virtual-contest]]. Where it sits:
> [[overview/architecture-overview]].

## Responsibility

Owns the full contest lifecycle — create/configure, register, run, score,
rank, finish — plus the standalone "virtual contest" replay path.

## Key tables

| Table | Purpose |
|-------|---------|
| `contests` | the contest (type, status, window, slug) |
| `contest_problems` | problems in the contest + per-contest config |
| `contest_participants` | who registered + status |
| `contest_submissions` | submissions made inside the contest |
| `contest_problem_results` | per-participant-per-problem score |
| `contest_scoring_rules` | scoring formula (penalty, tie-break) |
| `first_solve_records` | who solved each problem first |
| `global_rankings` | rating/leaderboard |
| `contest_announcements` | in-contest notices |

## Enums

`ContestStatus` · `ContestType` (incl. virtual) · `ContestScoringMode` ·
`ContestTieBreaker` · `ContestParticipantStatus` · `RatingTitle`.

## Key flows

```
admin creates → configures problems + ScoringRule → publishes
participant registers → at start window, can submit
each in-contest submission → contest_problem_results + first_solve check
on finish → finalize standings → global_rankings / rating update
virtual: participant starts own session → isolated clock + standings
```

## Controllers

- `ContestController` → `/contest` (user-facing: list, detail, register, standings).
- `AdminContestController` → `/admin/contest` (create/edit/publish).
- `ScoringRuleController` → `/admin/scoring-rules`.

## Hardening migrations (2026-06-17)

| Migration | Guarantees |
|-----------|-----------|
| `V…Contest_Scoring_Hardening` | scoring arithmetic integrity |
| `V…Contest_Slug_Unique` | URL slug uniqueness |
| `V…Contest_Real_Unique_And_Session_Length` | one live session per participant + bounded virtual session length |

These are the backbone of [[concepts/virtual-contest]] fencing.

## Source files

- `backend-spring/.../modules/contest/` (controller, service/impl, entity, dto, enums).
- `init-db/migrations/V20260604120000__Seed_Contests_Test_Data.sql` (seed).
- The three 06-17 hardening migrations above.

## Cross-links

- [[entities/submission]] · [[entities/problem]]
- [[concepts/virtual-contest]]
- [[overview/judging-pipeline-overview]] (contest path)

## Gotchas

- Standings recomputation must be idempotent against `SubmissionJudgedEvent`
  replays — derive from `contest_problem_results`, not from live aggregation.
- Virtual sessions depend on the `real_unique + session_length` constraint; don't
  relax it without rethinking [[concepts/virtual-contest]].
- `first_solve_records` is write-once per problem per contest; ties resolve via
  `ContestTieBreaker`.
