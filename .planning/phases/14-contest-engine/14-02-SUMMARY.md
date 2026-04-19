---
phase: 14-contest-engine
plan: "02"
subsystem: contest-scheduler
tags: [contest, scheduler, rating, elo, lifecycle]
dependency_graph:
  requires:
    - "14-01"
  provides:
    - "contest-scheduler"
    - "rating-calculation-service"
  affects:
    - "backend-spring/contest"
    - "backend-spring/websocket"
tech_stack:
  added:
    - "Spring @Scheduled polling"
    - "Codeforces Elo rating algorithm"
    - "MyBatis-Plus mappers"
  patterns:
    - "Idempotent state transitions"
    - "Batch rating calculation"
    - "CF Elo K-factor by rating tier"
key_files:
  created:
    - "backend-spring/src/main/java/com/ulticode/modules/contest/scheduler/ContestScheduler.java"
    - "backend-spring/src/main/java/com/ulticode/modules/contest/service/RatingCalculationService.java"
    - "backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RatingCalculationServiceImpl.java"
    - "db-manager/migrations/V21__add_contest_actual_times.sql"
  modified:
    - "backend-spring/src/main/java/com/ulticode/modules/contest/entity/Contest.java"
decisions:
  - id: D-01
    decision: "@Scheduled(fixedRate = 10_000) polls every 10 seconds"
  - id: D-02
    decision: "UPCOMING->RUNNING when now >= start_time"
  - id: D-03
    decision: "RUNNING->FINISHED when now >= effective end_time"
  - id: D-04
    decision: "Idempotent transitions with re-check before writing"
  - id: D-05
    decision: "RealtimeService.emitContestStatus() called on each transition"
  - id: D-06
    decision: "CF-style Elo rating algorithm"
  - id: D-07
    decision: "Rating range 0-3500, default 1500"
  - id: D-08
    decision: "10 RatingTitle levels: Newbie<1200, Pupil<1400, Specialist<1600, Expert<1900, CM<2100, Master<2300, IM<2400, GM<2600, IGM<3000, LGM>=3000"
  - id: D-09
    decision: "final_rank stored 1-based from sorted participants"
  - id: D-10
    decision: "Batch rating calculation on contest FINISHED"
  - id: D-11
    decision: "Only users with existing global_ranking records are rated"
metrics:
  duration: "8 minutes"
  completed: "2026-04-19T00:52:00Z"
  tasks_completed: 2
  files_created: 4
  files_modified: 1
---

# Phase 14 Plan 02: Contest Scheduler and Rating Engine Summary

## One-liner

ContestScheduler polls every 10 seconds for lifecycle transitions (UPCOMING->RUNNING, RUNNING->FINISHED) with idempotent writes and WebSocket status emission; RatingCalculationService computes Codeforces Elo ratings updating global_rankings and contest_participants.final_rank on contest end.

## Completed Tasks

### Task 1: ContestScheduler with lifecycle polling

Created `ContestScheduler.java` that:
- Polls every 10 seconds via `@Scheduled(fixedRate = 10_000)`
- Transitions UPCOMING->RUNNING when `now >= start_time`
- Transitions RUNNING->FINISHED when `now >= effective_end_time`
- Computes effective end time from `end_time` or `start_time + duration_minutes`
- Idempotent transitions with re-check before writing (T-14-04, T-14-07)
- Emits `ContestStatus` via `RealtimeService.emitContestStatus()` on each transition
- Calls `realtimeService.markDirty(contestId)` after starting for leaderboard
- Calls `ratingService.calculateAndUpdate(contestId)` after finishing

**Deviation [Rule 2 - Auto-add missing critical functionality]:** Added `actualStartTime` and `actualEndTime` fields to `Contest` entity and created `V21__add_contest_actual_times.sql` migration since these were required by the plan but absent from the entity.

### Task 2: RatingCalculationService with CF Elo implementation

Created `RatingCalculationService` interface and `RatingCalculationServiceImpl` that:
- Sorts participants by score (DESC) then penalty (ASC)
- Assigns `final_rank` 1-based after sorting
- Uses CF Elo formula: `expected = 1/(1+10^((oppRating-myRating)/400))`
- K-factor: 32 (<2100), 24 (<2400), 16 (>=2400)
- Rating clamped to 0-3500 range
- `fromRating()` maps rating to correct `RatingTitle` per D-08 thresholds (10 levels)
- Only rates participants with existing `global_ranking` records (D-11)
- Updates `global_ranking` with new rating, title, and last contest ID
- Recalculates global ranks after batch update

## Threat Surface

| Flag | File | Description |
|------|------|-------------|
| none | - | No new security surface introduced |

## Self-Check

- [x] ContestScheduler.java exists at correct path with @Scheduled, transitionToRunning, transitionToFinished, computeEffectiveEndTime
- [x] RatingCalculationService.java exists with calculateAndUpdate method
- [x] RatingCalculationServiceImpl.java has calculateNewRating, determineKFactor, fromRating, recalculateGlobalRanks
- [x] All 10 RatingTitle levels present in fromRating()
- [x] Contest entity has actualStartTime/actualEndTime fields
- [x] V21 migration created for new columns
- [x] Both tasks committed with proper messages

## Commits

- `a7c3f1d` feat(14-contest-engine): add ContestScheduler for lifecycle polling
- `35b1ad7` feat(14-contest-engine): add RatingCalculationService with CF Elo implementation
