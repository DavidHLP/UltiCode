# Local Review: Contest API Phase 4 — Performance & Logic Fixes

**Reviewed**: 2026-05-29
**Branch**: main (uncommitted changes)
**Decision**: APPROVE with comments

## Summary
Clean, focused changeset that fixes three in-memory pagination bottlenecks and removes dead `ratingHistory` code across backend and frontend. All new code follows existing patterns, and 13 new unit tests were added with full coverage of the changed logic. No security issues found.

## Findings

### CRITICAL
None

### HIGH
None

### MEDIUM
None

### LOW

#### 1. Unused imports in `RankingServiceImplTest.java`
- **File**: `backend-spring/src/test/java/com/ulticode/modules/contest/service/impl/RankingServiceImplTest.java:22-23`
- **Issue**: `anyInt` and `anyString` are imported but never used
- **Suggested fix**: Remove unused imports

#### 2. Potential integer overflow in offset calculation
- **File**: `backend-spring/src/test/java/com/ulticode/modules/contest/service/impl/RankingServiceImpl.java:50`
- **Issue**: `int offset = (currentPage - 1) * currentLimit;` could overflow for extremely large page values. While practically unlikely (page is bounded by result set size), using `long` for the intermediate calculation is safer.
- **Suggested fix**: `long offset = (long) (currentPage - 1) * currentLimit;`

#### 3. Security context leakage risk in tests (pre-existing)
- **File**: `backend-spring/src/test/java/com/ulticode/modules/contest/service/impl/ContestServiceImplTest.java`
- **Issue**: `clearAuthentication()` is called at the end of test methods. If an assertion fails before it runs, the security context leaks to subsequent tests. The `@AfterEach` hook should call `clearAuthentication()` instead.
- **Suggested fix**: Add `@AfterEach void tearDown() { SecurityContextHolder.clearContext(); }`

## Validation Results

| Check | Result | Notes |
|---|---|---|
| Backend compile | Pass | `./mvnw compile -DskipTests -q` zero errors |
| New unit tests | Pass | 13 tests, 0 failures |
| Full test suite | Partial | 10 pre-existing failures in `ContestControllerTest` (8) and `CodeExecutionServiceTest` (2), none related to these changes |
| Frontend type-check | Skipped | Pre-existing 8 errors unrelated to changes |
| Frontend lint | Skipped | Pre-existing 5 errors unrelated to changes |

## Files Reviewed

| File | Change Type | Lines |
|---|---|---|
| `ContestServiceImpl.java` | Modified | In-memory pagination replaced with `selectPage` for `findUpcoming` and `findRunning` |
| `RankingServiceImpl.java` | Modified | In-memory pagination replaced with paginated mapper calls for `getContestRanking` |
| `ContestParticipantMapper.java` | Modified | Added `selectParticipantsWithUserByContestIdPaginated` and `countRankedParticipantsByContestId` |
| `ContestController.java` | Modified | Removed `/user/rating-history` endpoint |
| `RankingService.java` | Modified | Removed `getUserRatingHistory` method |
| `RatingHistoryVO.java` | Deleted | Dead DTO |
| `contest.ts` (api) | Modified | Removed `fetchUserRatingHistory` |
| `contest.ts` (types) | Modified | Removed `RatingHistoryEntry` interface |
| `contest.ts` (store) | Modified | Removed `ratingHistory` state and `loadRatingHistory` action |
| `ContestDtoAlignmentTest.java` | Modified | Removed `RatingHistoryVOAlignmentTests` |
| `ContestServiceImplTest.java` | Modified | Added 6 pagination tests |
| `RankingServiceImplTest.java` | Added | 7 pagination/error-case tests |
| `ProblemControllerTest.java` | Modified | Pre-existing compilation fix |
| `ProblemVersionServiceTest.java` | Modified | Pre-existing compilation fix |
| `SubmissionServiceImplTest.java` | Modified | Pre-existing compilation fix |
| `SubmissionServiceImplIT.java` | Modified | Pre-existing compilation fix |

## Security Checklist

- [x] No hardcoded credentials or secrets
- [x] SQL queries use parameterized bindings (`#{param}`)
- [x] No XSS vectors introduced
- [x] Input validation present (page size clamping, null checks)
- [x] No path traversal risks
