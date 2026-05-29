# Code Review: Contest API Phase 2 — API Granularity Unification

**Reviewed**: 2026-05-29
**Branch**: feat/contest-phase1-type-fixes
**Decision**: APPROVE (after fixes applied)

## Summary

Comprehensive review of 19 changed files across backend Java, console TypeScript/Vue, and management TypeScript. Found 1 CRITICAL and 3 HIGH issues introduced by this change. All have been fixed. Multiple MEDIUM/LOW issues were pre-existing and documented for future cleanup.

## Issues Fixed During Review

### CRITICAL

**[FIXED] RankingServiceImpl: null contestId passed to MyBatis mapper**
- File: `RankingServiceImpl.java:36`, `RankingServiceImpl.java:66`
- Issue: `getContestRanking` and `getLiveRanking` accepted null/blank `contestId` and passed it directly to `selectParticipantsWithUserByContestId`, risking a full table scan.
- Fix: Added null/blank validation throwing `BusinessException(ErrorCode.BAD_REQUEST)` at both method entry points.

### HIGH

**[FIXED] ContestServiceImpl: findUpcoming/findRunning returned fake pagination**
- File: `ContestServiceImpl.java:171-188`
- Issue: Methods returned `PageResult.of(items, size, 1, size)` where pageSize equaled total count, providing no real pagination.
- Fix: Added overloaded methods accepting `page` and `pageSize` params with proper skip/limit logic.

**[FIXED] MyContests.vue: displayed non-existent ratingChange field**
- File: `MyContests.vue:170-192`
- Issue: Template cast `history as any` to access `ratingChange`, but `UserContestHistoryVO` no longer provides this field after the split. Would always render 0.
- Fix: Replaced the rating change display block with an `isRated` badge.

**[FIXED] stores/contest.ts: direct mutation of reactive array items**
- File: `stores/contest.ts:159-165`, `stores/contest.ts:180-189`
- Issue: `registerForContest` and `unregisterFromContest` mutated `contest.registeredCount` in-place, violating immutability and risking Vue reactivity edge cases.
- Fix: Replaced with immutable `map()` updates returning new array references.

## Pre-existing Issues Documented (Not Fixed in This PR)

### HIGH (existed before this change)
- `getGlobalRankingsPaginated` loads all rankings into memory then paginates in Java
- `AdminContestServiceImpl.deleteContest/startContest/endContest` missing `@Transactional`
- `AdminContestServiceImpl` reuses `CONTEST_NOT_FOUND` error code for validation failures
- `ContestServiceImpl.toVO/toListVO` public overloads perform N+1 queries when called outside batch paths

### MEDIUM (existed before this change)
- `ContestQueryDTO.isPremium` and `isRated` defined but never used in query logic
- `useContestRankings` composable has `console.error` and missing `contestId` in watch deps
- Management `Contest` interface conflates list and detail VO shapes

## Validation Results

| Check | Result |
|---|---|
| Backend compile | Pass |
| Backend tests (ContestDtoAlignmentTest) | Pass (33 tests, 0 failures) |
| Console type-check | Pass (0 contest-related errors) |
| Management type-check | Pass (0 contest-related errors) |
| Console lint | Pass (0 contest-related errors) |

## Files Reviewed

- `backend-spring/.../dto/LiveRankingEntryVO.java` — Added
- `backend-spring/.../dto/UserContestHistoryVO.java` — Added
- `backend-spring/.../dto/RatingHistoryVO.java` — Added
- `backend-spring/.../service/RankingService.java` — Modified
- `backend-spring/.../service/impl/RankingServiceImpl.java` — Modified
- `backend-spring/.../controller/ContestController.java` — Modified
- `backend-spring/.../service/ContestService.java` — Modified
- `backend-spring/.../service/impl/ContestServiceImpl.java` — Modified
- `backend-spring/.../controller/AdminContestController.java` — Modified
- `backend-spring/.../dto/ContestQueryDTO.java` — Modified
- `backend-spring/.../AdminContestService.java` — Modified
- `backend-spring/.../AdminContestServiceImpl.java` — Modified
- `backend-spring/.../ContestDtoAlignmentTest.java` — Modified
- `console/src/api/contest.ts` — Modified
- `console/src/types/contest.ts` — Modified
- `console/src/stores/contest.ts` — Modified
- `console/src/views/contest/components/MyContests.vue` — Modified
- `console/src/views/contest/detailed/components/ContestRankingTable.vue` — Modified
- `console/src/views/contest/detailed/composables/useContestRankings.ts` — Modified
- `management/src/api/admin/contests.ts` — Modified
