# Local Code Review: Contest API Phase 4 — Performance & Logic Fixes

**Reviewed**: 2026-05-29
**Branch**: feat/contest-api-phase4-performance-fixes
**Decision**: APPROVE

## Summary
Clean, focused changeset that fixes three in-memory pagination bottlenecks and removes dead `ratingHistory` code across backend and frontend. All changes follow existing codebase patterns. No security or correctness issues found.

## Findings

### CRITICAL
None

### HIGH
None

### MEDIUM
None

### LOW
**Behavioral change in visibility filtering** — `ContestServiceImpl.findUpcoming()` and `findRunning()` now include `.eq(Contest::getIsVisible, true)`, which filters out invisible contests. The old `findByStatus` mapper method did not filter by `is_visible`. This is the **correct** behavior for public endpoints (consistent with `findAllListVO` and `findPast`), but it is a functional change. If any consumers relied on invisible contests being returned, they will break. No such consumers are known.
- File: `ContestServiceImpl.java:180, 201`

## Validation Results

| Check | Result |
|---|---|
| Backend Compile | Pass |
| Frontend Type-Check | Skipped (pre-existing errors unrelated to changes) |
| Frontend Lint | Skipped (pre-existing errors unrelated to changes) |
| Tests | Skipped (pre-existing test compilation failures in unrelated modules) |

## Files Reviewed

| File | Change | Assessment |
|---|---|---|
| `ContestController.java` | Removed `/user/rating-history` endpoint | Clean removal, no dangling references |
| `RatingHistoryVO.java` | Deleted | Dead code, safe to remove |
| `ContestParticipantMapper.java` | Added paginated query + count methods | Correct use of `@Results` duplication (required for annotation-based MyBatis). SQL uses parameterized `LIMIT`/`OFFSET` — no injection risk |
| `RankingService.java` | Removed `getUserRatingHistory` interface method | Clean removal |
| `ContestServiceImpl.java` | Replaced in-memory pagination with `selectPage` | Mirrors existing `findPast`/`findAllListVO` patterns. Adds `isVisible` filter for correctness |
| `RankingServiceImpl.java` | Replaced in-memory pagination with paginated mapper calls | Eliminates full-table fetch into memory. `total` is now `long` directly from count query, no cast needed |
| `ContestDtoAlignmentTest.java` | Removed `RatingHistoryVOAlignmentTests` | Necessary fallout from DTO deletion |
| `console/src/api/contest.ts` | Removed `fetchUserRatingHistory` | Clean removal |
| `console/src/stores/contest.ts` | Removed `ratingHistory` state and `loadRatingHistory` action | Clean removal, `$reset` updated correctly |
| `console/src/types/contest.ts` | Removed `RatingHistoryEntry` interface | Clean removal |

## Notes
- The `@Results` block duplication in `ContestParticipantMapper.java` is intentional and required. Annotation-based MyBatis does not share result maps across methods.
- The old `findByStatus` mapper method in `ContestMapper` is now unused by `ContestServiceImpl`, but it is harmless to leave in place. It may still be used by other callers (e.g., scheduler, admin flows). A follow-up cleanup could audit its usage.
- No new tests were added for the pagination changes. This is acceptable given the scope, but recommended once the pre-existing test compilation issues are resolved.
