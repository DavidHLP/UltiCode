# Phase 14 Plan 01: Contest Engine Throttle Infrastructure - Summary

## Plan Overview

**Plan:** 14-01
**Phase:** 14-contest-engine
**Status:** COMPLETED
**Completed:** 2026-04-19

## Objective

Add throttle infrastructure to RealtimeService (markDirty + flushPendingRankings), wire submission verdict to trigger dirty tracking, and fix SubmissionResultPayload to include contestId so frontend can correlate results.

## Tasks Executed

### Task 1: Add markDirty and flushPendingRankings to RealtimeService

**Files Modified:**
- `backend-spring/src/main/java/com/ulticode/modules/websocket/service/RealtimeService.java`

**Changes:**
- Added `RankingService` dependency via constructor injection
- Added `Set` and `Collectors` imports for throttle logic
- Added `markDirty(String contestId)` method that adds contestId to `pendingRankingUpdates`
- Added `@Scheduled(fixedRate = 1000) flushPendingRankings()` method that:
  - Copies and clears pending ranking keys
  - Checks throttle elapsed time (RANKING_THROTTLE_MS = 1000)
  - Fetches live ranking from `RankingService.getLiveRanking(contestId, 200)`
  - Maps `ContestRankingVO` fields (rank, userId, username, score, penalty, problemsSolved) to `RankingItem`
  - Emits ranking update via `emitRankingUpdate()`
  - Re-marks dirty if throttle not yet elapsed for next flush cycle

**Commit:** `a4c8f3b` - feat(14-contest-engine): add markDirty and flushPendingRankings throttle infrastructure

### Task 2: Wire submission verdict to markDirty and fix contestId in SubmissionResultPayload

**Files Modified:**
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java`
- `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java`

**Changes:**

*SubmissionServiceImpl:*
- Added `RealtimeService` import and field injection
- Added `realtimeService.markDirty(contest.getId())` call after `contestSubmissionMapper.insert(cs)` in `recordContestSubmissionIfNeeded()`

*JudgeWorkerProcessor:*
- Added `ContestSubmissionMapper` and `ContestSubmission` imports
- Added `ContestSubmissionMapper` field injection via constructor
- Added `findContestIdBySubmissionId(String submissionId)` helper using MyBatis-Plus `LambdaQueryWrapper`
- Updated `pushResult()` signature to accept `String contestId` parameter
- Changed `SubmissionResultPayload.of()` call to pass `contestId` variable (not `null`)
- Updated all three call sites:
  - `processJob()` success path: `pushResult(..., contestId)` with lookup
  - `processJob()` System Error path: `pushResult(..., null)`
  - `onFailure()` retry-exhausted path: `pushResult(..., failedContestId)` with lookup

**Commit:** `b7d2e10` - feat(14-contest-engine): wire contest submission verdict to dirty tracking and fix contestId in WebSocket payload

## Key Decisions

| Decision | Rationale |
|----------|-----------|
| Use `pendingRankingUpdates.putIfAbsent()` for marking dirty | Only marks once per dirty cycle, avoids duplicate work |
| Re-mark dirty if throttle not elapsed | Ensures contest is reconsidered on next flush cycle |
| Lookup contestId via `LambdaQueryWrapper` in `pushResult` | Clean MyBatis-Plus pattern, null-safe |
| Pass `null` contestId for non-contest submissions | Graceful degradation for standalone submissions |

## Deviation from Plan

**None** - plan executed exactly as written. All must-haves satisfied.

## Verification Results

| Check | Result |
|-------|--------|
| `flushPendingRankings` and `markDirty` in RealtimeService | PASS (2 matches) |
| `@Scheduled(fixedRate = 1000)` on flushPendingRankings | PASS |
| `realtimeService.markDirty()` in SubmissionServiceImpl | PASS |
| `SubmissionResultPayload.of()` uses contestId variable (not null) | PASS |
| `mvn compile` | PASS (BUILD SUCCESS) |

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| None | - | No new security surface introduced |

## Files Created

- `.planning/phases/14-contest-engine/14-01-SUMMARY.md` (this file)

## Dependencies Satisfied

- REQUIREMENT `JUDGE-04`: Submission verdict WebSocket payload includes contestId
- REQUIREMENT `CONTEST-04`: Dirty contests are flushed after every submission verdict and by 1-second throttle scheduler
