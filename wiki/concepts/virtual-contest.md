---
title: Virtual Contest
type: concept
tags: [contest, type/concept]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/contest/
  - init-db/migrations/V20260617140000__Contest_Real_Unique_And_Session_Length.sql
  - init-db/migrations/V20260617120000__Contest_Scoring_Hardening.sql
aliases: [虚拟比赛]
---

# Virtual Contest

## The problem
A real contest has a fixed window; users who miss it (or want to re-run it under
exam conditions) have no way to participate on their own schedule. Letting them
replay naively would contaminate live standings and global ratings.

## The decision
`ContestType` includes a **virtual** mode: a participant starts an **isolated
session** with its own clock and standings derived from the same problem set.
Their results are fenced off from the original contest's rankings.

## Where it lives
- `modules/contest/` — `ContestType` enum, participant session handling.
- Migration `V20260617140000__Contest_Real_Unique_And_Session_Length` — one live
  session per participant + bounded session length (the fence that makes virtual
  mode safe).
- Companion hardening: `V20260617120000__Contest_Scoring_Hardening`,
  `V20260617130000__Contest_Slug_Unique`.

## Trade-offs
- Extra session state per virtual participant vs. replay-without-rank UX win.
- The `real_unique + session_length` constraint is load-bearing — relaxing it
  reopens ranking contamination.

## Related
[[entities/contest]] · [[entities/submission]] ·
[[overview/judging-pipeline-overview]]
