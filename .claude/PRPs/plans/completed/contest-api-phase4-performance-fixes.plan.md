# Plan: Contest API Phase 4 — Performance & Logic Fixes

## Summary
Phase 4 of the Contest API alignment focuses on fixing in-memory pagination bottlenecks and removing dead code. The original analysis document incorrectly identified `getGlobalRankingsPaginated` as using in-memory pagination; in reality, it already uses database-level pagination via `GlobalRankingMapper`. The actual issues are `findUpcoming`, `findRunning` (both in `ContestServiceImpl`), and `getContestRanking` (in `RankingServiceImpl`), which fetch entire datasets into memory before applying Java stream `skip/limit`. Additionally, the `ratingHistory` feature is completely unimplemented on the backend and unused on the frontend — it should be removed to reduce maintenance burden.

## User Story
```
As a user browsing contest listings and rankings,
I want pages to load quickly even when there are many contests or participants,
So that the platform remains responsive at scale.
```

## Problem → Solution
- **Current**: `findUpcoming`/`findRunning` fetch all contests by status then paginate in memory; `getContestRanking` fetches all participants then paginates in memory; `ratingHistory` is dead code everywhere.
- **Desired**: All pagination happens at the database layer via LIMIT/OFFSET or MyBatis-Plus `Page`; `ratingHistory` code is fully removed from backend and frontend.

## Metadata
- **Complexity**: Medium
- **Source PRD**: `docs/contest-api-alignment-analysis.md`
- **PRD Phase**: Phase 4 — 性能与逻辑修复
- **Estimated Files**: 8

---

## UX Design

**Internal change — no user-facing UX transformation.**

The only user-visible impact is faster loading times for upcoming/running contest lists and contest rankings. No UI components or layouts change.

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Upcoming contest list | In-memory pagination | DB pagination | Faster with many contests |
| Running contest list | In-memory pagination | DB pagination | Faster with many contests |
| Contest ranking page | In-memory pagination | DB pagination | Faster with many participants |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java` | 170–220 | `findUpcoming`/`findRunning` in-memory pagination to fix |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RankingServiceImpl.java` | 37–69 | `getContestRanking` in-memory pagination to fix |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestParticipantMapper.java` | 199–221 | Existing joined query pattern to mirror for pagination |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestMapper.java` | 26–27 | `findByStatus` method to be replaced by `selectPage` |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/GlobalRankingMapper.java` | all | DB-level pagination pattern with LIMIT/OFFSET |
| P2 | `console/src/stores/contest.ts` | 46–48, 289–298 | Dead `ratingHistory` state and action |
| P2 | `console/src/api/contest.ts` | 269–275 | Dead `fetchUserRatingHistory` API function |

---

## External Documentation

No external research needed — feature uses established internal patterns (MyBatis-Plus `Page`, annotation-based SQL mappers, Pinia immutable updates).

---

## Patterns to Mirror

### DB_PAGINATION_WITH_MYBATIS_PLUS_PAGE
// SOURCE: `ContestServiceImpl.java:208–219` (findPast method)
```java
@Override
public PageResult<ContestListVO> findPast(Integer page, Integer pageSize, String userId) {
    int p = Math.max(page != null ? page : 1, 1);
    int ps = Math.min(pageSize != null && pageSize > 0 ? pageSize : 10, 50);
    LambdaQueryWrapper<Contest> qw = new LambdaQueryWrapper<>();
    qw.eq(Contest::getIsDeleted, false)
      .eq(Contest::getStatus, ContestStatus.FINISHED.name())
      .orderByDesc(Contest::getEndTime);
    Page<Contest> result = contestMapper.selectPage(new Page<>(p, ps), qw);
    var enrichment = batchEnrich(result.getRecords(), userId);
    List<ContestListVO> items = result.getRecords().stream()
            .map(c -> toListVOInternal(c, userId, enrichment.problemCounts().getOrDefault(c.getId(), 0L), enrichment.participants().get(c.getId())))
            .collect(Collectors.toList());
    return PageResult.of(items, result.getTotal(), p, ps);
}
```

### ANNOTATION_BASED_SQL_PAGINATION
// SOURCE: `GlobalRankingMapper.java`
```java
@Select("SELECT * FROM global_rankings ORDER BY global_rank ASC LIMIT #{limit} OFFSET #{offset}")
List<GlobalRanking> findRankingsPaginated(
        @Param("limit") int limit,
        @Param("offset") int offset
);

@Select("SELECT COUNT(*) FROM global_rankings")
long countTotal();
```

### ERROR_HANDLING_BUSINESS_EXCEPTION
// SOURCE: `RankingServiceImpl.java:39–41`
```java
if (contestId == null || contestId.isBlank()) {
    throw new BusinessException(ErrorCode.BAD_REQUEST, "contestId is required");
}
```

### PINIA_IMMUTABLE_ARRAY_UPDATE
// SOURCE: `console/src/stores/contest.ts:159–163`
```typescript
upcomingContests.value = upcomingContests.value.map((c) =>
  c.id === contestId
    ? { ...c, registeredCount: (c.registeredCount || 0) + 1 }
    : c,
);
```

### TEST_PATTERN_MOCKITO
// SOURCE: `ContestServiceImplTest.java:37–78`
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("ContestServiceImpl")
class ContestServiceImplTest {
    @Mock private ContestMapper contestMapper;
    // ...
    @BeforeEach
    void setUp() {
        contestService = new ContestServiceImpl(/* mocks */);
    }
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/ContestServiceImpl.java` | UPDATE | Fix `findUpcoming`/`findRunning` in-memory pagination |
| `backend-spring/src/main/java/com/ulticode/modules/contest/service/impl/RankingServiceImpl.java` | UPDATE | Fix `getContestRanking` in-memory pagination; remove `getUserRatingHistory` |
| `backend-spring/src/main/java/com/ulticode/modules/contest/mapper/ContestParticipantMapper.java` | UPDATE | Add paginated + count methods for ranked participants |
| `backend-spring/src/main/java/com/ulticode/modules/contest/controller/ContestController.java` | UPDATE | Remove `/user/rating-history` endpoint |
| `backend-spring/src/main/java/com/ulticode/modules/contest/service/RankingService.java` | UPDATE | Remove `getUserRatingHistory` interface method |
| `backend-spring/src/main/java/com/ulticode/modules/contest/dto/RatingHistoryVO.java` | DELETE | Dead DTO — no backend implementation, no frontend consumer |
| `console/src/api/contest.ts` | UPDATE | Remove `fetchUserRatingHistory` import and function |
| `console/src/types/contest.ts` | UPDATE | Remove `RatingHistoryEntry` interface |
| `console/src/stores/contest.ts` | UPDATE | Remove `ratingHistory` state, `loadRatingHistory` action, and related imports |

## NOT Building

- No new database tables or entities (rating history will be removed, not implemented)
- No new UI components or pages
- No changes to `getGlobalRankingsPaginated` — already uses DB pagination correctly
- No changes to `getGlobalRanking` (non-paginated top-N) — it uses `LIMIT` in SQL already
- No changes to `getLiveRanking` — real-time nature justifies in-memory sorting/filtering
- No changes to store immutability patterns — `registerForContest`/`unregisterFromContest` already correctly use `.map()`

---

## Step-by-Step Tasks

### Task 1: Fix `findUpcoming` Database Pagination
- **ACTION**: Replace `contestMapper.findByStatus(...)` + stream `skip/limit` with `contestMapper.selectPage(new Page<>(p, ps), qw)` in `ContestServiceImpl.findUpcoming()`.
- **IMPLEMENT**:
  ```java
  public PageResult<ContestListVO> findUpcoming(String userId, int page, int pageSize) {
      int p = Math.max(page, 1);
      int ps = Math.min(Math.max(pageSize, 1), 50);
      LambdaQueryWrapper<Contest> qw = new LambdaQueryWrapper<>();
      qw.eq(Contest::getIsDeleted, false)
        .eq(Contest::getIsVisible, true)
        .eq(Contest::getStatus, ContestStatus.UPCOMING.name())
        .orderByAsc(Contest::getStartTime);
      Page<Contest> result = contestMapper.selectPage(new Page<>(p, ps), qw);
      var enrichment = batchEnrich(result.getRecords(), userId);
      List<ContestListVO> items = result.getRecords().stream()
              .map(c -> toListVOInternal(c, userId, enrichment.problemCounts().getOrDefault(c.getId(), 0L), enrichment.participants().get(c.getId())))
              .collect(Collectors.toList());
      return PageResult.of(items, result.getTotal(), p, ps);
  }
  ```
  Also update the no-arg overload `findUpcoming(String userId)` to call `findUpcoming(userId, 1, 20)`.
- **MIRROR**: `findPast()` method (`ContestServiceImpl.java:208–219`)
- **IMPORTS**: Already present (`Page`, `LambdaQueryWrapper`)
- **GOTCHA**: Preserve `is_visible = true` filter that `findByStatus` did not explicitly have (though `findByStatus` SQL was `status = #{status} AND is_deleted = 0`). The public endpoint should only show visible contests.
- **VALIDATE**: `ContestServiceImplTest` — add test verifying `contestMapper.selectPage` is called and `findByStatus` is NOT called.

### Task 2: Fix `findRunning` Database Pagination
- **ACTION**: Same transformation as Task 1, but for `ContestServiceImpl.findRunning()`.
- **IMPLEMENT**: Use `ContestStatus.RUNNING.name()` instead of `UPCOMING` in the query wrapper.
- **MIRROR**: `findPast()` method
- **GOTCHA**: Same visibility filter consideration as Task 1.
- **VALIDATE**: Unit test mirroring Task 1's test.

### Task 3: Fix `getContestRanking` Database Pagination
- **ACTION**: Add paginated query + count methods to `ContestParticipantMapper` and refactor `RankingServiceImpl.getContestRanking()` to use them.
- **IMPLEMENT**:
  Add to `ContestParticipantMapper.java`:
  ```java
  @Select("SELECT cp.*, u.username, u.name, u.avatar " +
          "FROM contest_participants cp " +
          "LEFT JOIN users u ON cp.user_id = u.id " +
          "WHERE cp.contest_id = #{contestId} AND cp.final_rank IS NOT NULL " +
          "ORDER BY cp.final_rank ASC, cp.total_score DESC, cp.total_penalty ASC " +
          "LIMIT #{limit} OFFSET #{offset}")
  @Results({ /* same @Results as selectParticipantsWithUserByContestId */ })
  List<ContestParticipantWithUser> selectParticipantsWithUserByContestIdPaginated(
          @Param("contestId") String contestId,
          @Param("limit") int limit,
          @Param("offset") int offset);

  @Select("SELECT COUNT(*) FROM contest_participants " +
          "WHERE contest_id = #{contestId} AND final_rank IS NOT NULL")
  long countRankedParticipantsByContestId(@Param("contestId") String contestId);
  ```
  Then refactor `RankingServiceImpl.getContestRanking()`:
  ```java
  long total = participantMapper.countRankedParticipantsByContestId(contestId);
  int offset = (currentPage - 1) * currentLimit;
  List<ContestParticipantWithUser> rankedParticipants =
          participantMapper.selectParticipantsWithUserByContestIdPaginated(contestId, currentLimit, offset);
  List<ContestRankingVO> rankingList = rankedParticipants.stream()
          .map(this::toRankingVO)
          .collect(Collectors.toList());
  return PageResult.of(rankingList, total, currentPage, currentLimit);
  ```
- **MIRROR**: `GlobalRankingMapper.findRankingsPaginated` + `countTotal`
- **IMPORTS**: None new needed in service; mapper uses existing annotations
- **GOTCHA**: `selectParticipantsWithUserByContestId` is also used by `getLiveRanking` — do NOT modify it. Create a new paginated method. The `@Results` block must be duplicated on the new method because annotation-based mappers do not share result maps automatically.
- **VALIDATE**: Unit test verifying `countRankedParticipantsByContestId` and `selectParticipantsWithUserByContestIdPaginated` are called, and that the returned `PageResult` has correct total/page/pageSize.

### Task 4: Remove Dead `ratingHistory` Backend Code
- **ACTION**: Delete the unimplemented `ratingHistory` endpoint, service method, and DTO.
- **IMPLEMENT**:
  1. In `ContestController.java`: remove the `@GetMapping("/user/rating-history")` method (`lines ~503–512`).
  2. In `RankingService.java`: remove `List<RatingHistoryVO> getUserRatingHistory(String userId);` method and its import.
  3. In `RankingServiceImpl.java`: remove `getUserRatingHistory` method and its `RatingHistoryVO` import.
  4. Delete `backend-spring/src/main/java/com/ulticode/modules/contest/dto/RatingHistoryVO.java`.
- **MIRROR**: N/A — code removal
- **GOTCHA**: None. No other files reference these symbols (verified via grep).
- **VALIDATE**: `./mvnw compile -pl backend-spring` passes with zero errors.

### Task 5: Remove Dead `ratingHistory` Frontend Code
- **ACTION**: Remove `RatingHistoryEntry` type, `fetchUserRatingHistory` API, and `ratingHistory`/`loadRatingHistory` from store.
- **IMPLEMENT**:
  1. In `console/src/types/contest.ts`: remove `RatingHistoryEntry` interface (`lines 346–355`).
  2. In `console/src/api/contest.ts`: remove `fetchUserRatingHistory` function (`lines 269–275`) and `RatingHistoryEntry` from the import block.
  3. In `console/src/stores/contest.ts`:
     - Remove `RatingHistoryEntry` from the type import.
     - Remove `fetchUserRatingHistory` from the API import.
     - Remove `const ratingHistory = ref<RatingHistoryEntry[]>([]);` state.
     - Remove `async function loadRatingHistory() { ... }` action.
     - Remove `ratingHistory` and `loadRatingHistory` from the return object.
     - Remove `ratingHistory.value = [];` from `$reset()`.
- **MIRROR**: N/A — code removal
- **GOTCHA**: None. Zero components or other files reference `ratingHistory` or `loadRatingHistory` (verified via grep).
- **VALIDATE**: `cd console && pnpm type-check` passes with zero errors.

---

## Testing Strategy

### Unit Tests (Backend)

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `findUpcoming uses selectPage` | page=2, pageSize=10 | `contestMapper.selectPage` called with `Page(2,10)`; `findByStatus` never called | No |
| `findRunning uses selectPage` | page=1, pageSize=20 | `contestMapper.selectPage` called with `Page(1,20)` | No |
| `getContestRanking uses paginated mapper` | contestId="abc", page=1, limit=50 | `countRankedParticipantsByContestId` called; `selectParticipantsWithUserByContestIdPaginated` called with limit=50, offset=0 | No |
| `getContestRanking empty result` | contestId="abc", page=1, limit=50, count=0 | `PageResult` with empty items, total=0 | Yes |
| `getContestRanking invalid page` | page=0, limit=50 | Defaults to page=1 | Yes |

### Edge Cases Checklist
- [ ] Empty upcoming/running contest list
- [ ] Page number < 1 defaults to 1
- [ ] Page size > 50 clamped to 50
- [ ] Contest ranking with no ranked participants
- [ ] Backend compiles after `RatingHistoryVO` deletion
- [ ] Frontend type-checks after rating history removal

---

## Validation Commands

### Static Analysis — Backend
```bash
cd backend-spring && ./mvnw compile -DskipTests
```
EXPECT: Zero compilation errors

### Unit Tests — Backend
```bash
cd backend-spring && ./mvnw test -Dtest="ContestServiceImplTest,RankingServiceImplTest"
```
EXPECT: All tests pass

### Static Analysis — Frontend (Console)
```bash
cd console && pnpm type-check
```
EXPECT: Zero type errors

### Lint — Frontend (Console)
```bash
cd console && pnpm lint
```
EXPECT: Zero lint errors

### Full Backend Test Suite
```bash
cd backend-spring && ./mvnw test
```
EXPECT: No regressions

### Manual Validation
- [ ] Call `GET /contest/upcoming` with 20+ upcoming contests in DB → verify only requested page size returned, response contains correct `total`/`page`/`pageSize`
- [ ] Call `GET /contest/running` with 20+ running contests → same verification
- [ ] Call `GET /contest/{id}/ranking` with 100+ participants with `final_rank` set → verify paginated response, total matches count of ranked participants
- [ ] Verify `GET /contest/user/rating-history` returns 404 (endpoint removed)
- [ ] Build console frontend and confirm no runtime errors related to removed rating history code

---

## Acceptance Criteria
- [ ] `findUpcoming` uses MyBatis-Plus `selectPage` for DB-level pagination
- [ ] `findRunning` uses MyBatis-Plus `selectPage` for DB-level pagination
- [ ] `getContestRanking` uses paginated SQL query instead of in-memory stream pagination
- [ ] Dead `ratingHistory` code fully removed from backend (controller, service, DTO)
- [ ] Dead `ratingHistory` code fully removed from frontend (API, types, store)
- [ ] All validation commands pass (compile, type-check, unit tests)
- [ ] No regressions in existing contest-related tests

## Completion Checklist
- [ ] Code follows discovered patterns (`selectPage`, annotation-based SQL pagination)
- [ ] Error handling matches codebase style (`BusinessException` for bad input)
- [ ] No hardcoded values (use `ContestStatus` enum, respect max page size limits)
- [ ] No unnecessary scope additions (did not implement rating history table/feature)
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `findByStatus` used elsewhere | Low | Medium | Grep for `findByStatus` before removal; if unused, the old mapper method can be left in place (harmless) |
| `@Results` duplication error in new mapper method | Medium | Medium | Copy `@Results` exactly from existing `selectParticipantsWithUserByContestId` |
| Frontend type errors from removing shared type | Low | Low | `pnpm type-check` immediately after removal; no components use `RatingHistoryEntry` |
| Breaking external API consumers calling `/user/rating-history` | Very Low | Low | Endpoint always returned empty list — no real consumer existed |

## Notes
- **Analysis doc discrepancy**: The original `docs/contest-api-alignment-analysis.md` incorrectly stated that `getGlobalRankingsPaginated` uses in-memory pagination. In reality, it correctly delegates to `GlobalRankingMapper.findRankingsPaginated(currentLimit, offset)` which uses SQL `LIMIT/OFFSET`. The actual in-memory pagination problems are in `findUpcoming`, `findRunning`, and `getContestRanking`.
- **Store immutability**: The analysis doc also incorrectly claimed that `registerForContest` directly mutates array elements. The actual code already correctly uses immutable `.map()` updates. No store refactoring is needed for Phase 4.
- **Rating history removal vs implementation**: Implementing `getUserRatingHistory` would require creating a new `rating_history` table and entity, wiring up rating change recording across the submission/scoring pipeline, and building UI components. This is far beyond Phase 4 scope. Removing the dead stub is the correct minimal action.
