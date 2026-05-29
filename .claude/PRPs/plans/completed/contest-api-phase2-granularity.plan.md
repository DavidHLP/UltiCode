# Plan: Contest API Phase 2 — API Granularity Unification

## Summary
Unify all contest list endpoints to return `PageResult<ContestListVO>`, split the overloaded `ContestRankingVO` into scenario-specific DTOs, make admin list endpoints return lightweight VOs, and clean up alias fields in `ContestQueryDTO`. This reduces payload sizes, eliminates N+1 query risks, and establishes consistent API contracts across the contest module.

## User Story
As a frontend developer, I want consistent paginated list responses and purpose-built ranking DTOs, so that I can write simpler mapping code, reduce bandwidth usage, and avoid silent failures when fields are missing in different contexts.

## Problem → Solution
**Current state**: List endpoints return mixed types (`List<ContestVO>`, `PageResult<ContestVO>`, `PageResult<ContestListVO>`). `ContestRankingVO` is reused for 4 unrelated scenarios with 33 fields. Admin lists return 35+ field VOs when only 10 fields are displayed. Query DTOs have aliases (`sort`/`sortBy`, `pageSize`/`limit`).

**Desired state**: All list endpoints return `PageResult<ContestListVO>`. Each ranking scenario has its own focused DTO. Admin lists return lightweight VOs. Query DTOs have a single field per concept.

## Metadata
- **Complexity**: Large
- **Source PRD**: `docs/contest-api-alignment-analysis.md`
- **PRD Phase**: Phase 2 — API Granularity Unification
- **Estimated Files**: 15+ files (backend: 8, frontend: 7+)

---

## UX Design

### Before
```
/contest          → PageResult<ContestListVO>   ✓ OK
/contest/upcoming → List<ContestVO>              ✗ Full VO, no pagination
/contest/running  → List<ContestVO>              ✗ Full VO, no pagination
/contest/past     → PageResult<ContestVO>        ✗ Full VO, unnecessary fields

Ranking endpoints all return ContestRankingVO (33 fields):
  - /contest/:id/ranking        → needs rank, user, score, penalty
  - /contest/:id/live-ranking   → needs rank, user, score, problemsSolved
  - /contest/user/history       → needs contest info, rank, score
  - /contest/user/rating-history→ needs rating, date, contest info
```

### After
```
/contest          → PageResult<ContestListVO>   ✓ OK
/contest/upcoming → PageResult<ContestListVO>   ✓ Unified
/contest/running  → PageResult<ContestListVO>   ✓ Unified
/contest/past     → PageResult<ContestListVO>   ✓ Unified

Scenario-specific ranking DTOs:
  - /contest/:id/ranking        → PageResult<ContestRankingVO>     (lean: 12 fields)
  - /contest/:id/live-ranking   → List<LiveRankingEntryVO>         (live-specific)
  - /contest/user/history       → List<UserContestHistoryVO>       (history-specific)
  - /contest/user/rating-history→ List<RatingHistoryVO>            (rating-specific)
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `fetchUpcomingContests()` | Returns `ContestListItem[]` | Returns `PaginatedResult<ContestListItem>` | Frontend must unwrap `.items` |
| `fetchRunningContests()` | Returns `ContestListItem[]` | Returns `PaginatedResult<ContestListItem>` | Frontend must unwrap `.items` |
| `fetchPastContests()` | Returns `{ data, total }` | Returns `PaginatedResult<ContestListItem>` | Aligns with `getContests()` |
| `fetchUserContestHistory()` | Returns `UserContestHistory[]` (mapped from ContestRankingVO) | Returns `UserContestHistory[]` (from UserContestHistoryVO) | Type fields more accurate |
| `fetchUserRatingHistory()` | Returns `RatingHistoryEntry[]` (mapped from ContestRankingVO) | Returns `RatingHistoryEntry[]` (from RatingHistoryVO) | Type fields more accurate |
| Admin `getContests()` | Returns `PageResult<Contest>` (35+ fields) | Returns `PageResult<ContestListItem>` (~18 fields) | Faster page loads |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `backend-spring/.../ContestController.java` | 55-142 | List endpoints to unify |
| P0 (critical) | `backend-spring/.../ContestServiceImpl.java` | 170-200, 392-448 | Service methods to refactor |
| P0 (critical) | `backend-spring/.../ContestRankingVO.java` | 1-150 | VO to split |
| P0 (critical) | `backend-spring/.../RankingServiceImpl.java` | 1-134 | Ranking mapping logic |
| P1 (important) | `console/src/api/contest.ts` | 135-161 | Frontend API calls to update |
| P1 (important) | `console/src/types/contest.ts` | 97-200 | Frontend types to align |
| P1 (important) | `management/src/api/admin/contests.ts` | 1-154 | Admin API to update |
| P1 (important) | `backend-spring/.../AdminContestController.java` | 35-56 | Admin list endpoint |
| P2 (reference) | `backend-spring/.../ContestQueryDTO.java` | 1-54 | Query DTO to clean |
| P2 (reference) | `backend-spring/.../ContestListVO.java` | 1-36 | Target lightweight VO |

---

## External Documentation

No external research needed — feature uses established internal patterns (MyBatis-Plus `Page<T>`, `PageResult.of()`, `BeanUtils.copyProperties`, record-style DTOs).

---

## Patterns to Mirror

### PAGE_RESULT_PATTERN
// SOURCE: `backend-spring/.../PageResult.java:1-30`
```java
public class PageResult<T> {
    private List<T> items;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Integer totalPages;

    public static <T> PageResult<T> of(List<T> items, Long total, Integer page, Integer pageSize) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PageResult<>(items, total, page, pageSize, totalPages);
    }
}
```

### MYBATIS_PLUS_PAGINATION
// SOURCE: `backend-spring/.../ContestServiceImpl.java:392-416`
```java
Page<Contest> page = contestMapper.selectPage(new Page<>(currentPage, currentPageSize), qw);
var enrichment = batchEnrich(page.getRecords(), userId);
List<ContestListVO> items = page.getRecords().stream()
    .map(c -> toListVO(c, userId, enrichment.problemCounts().getOrDefault(c.getId(), 0L), enrichment.participants().get(c.getId())))
    .collect(Collectors.toList());
return PageResult.of(items, page.getTotal(), currentPage, currentPageSize);
```

### BATCH_ENRICHMENT_PATTERN
// SOURCE: `backend-spring/.../ContestServiceImpl.java:374-389`
```java
private record ContestEnrichment(Map<String, Long> problemCounts, Map<String, ContestParticipant> participants) {}

private ContestEnrichment batchEnrich(List<Contest> contests, String userId) {
    List<String> contestIds = contests.stream().map(Contest::getId).toList();
    Map<String, Long> problemCounts = Map.of();
    Map<String, ContestParticipant> participants = Map.of();
    if (!contestIds.isEmpty()) {
        problemCounts = contestProblemMapper.countByContestIds(contestIds).stream()
            .collect(Collectors.toMap(m -> (String) m.get("contestId"), m -> ((Number) m.get("cnt")).longValue(), (a, b) -> a));
        if (userId != null && !userId.isBlank()) {
            participants = participantMapper.findByContestIdsAndUserId(contestIds, userId).stream()
                .collect(Collectors.toMap(ContestParticipant::getContestId, p -> p, (a, b) -> a));
        }
    }
    return new ContestEnrichment(problemCounts, participants);
}
```

### RECORD_STYLE_VO
// SOURCE: `backend-spring/.../ContestListVO.java:14-36`
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContestListVO(
    @Schema(description = "...") String id,
    @Schema(description = "...") String slug,
    // ... more fields
) {}
```

### RANKING_VO_MAPPING
// SOURCE: `backend-spring/.../RankingServiceImpl.java:100-133`
```java
private ContestRankingVO toRankingVO(ContestParticipant participant) {
    if (participant == null) return null;
    ContestRankingVO vo = new ContestRankingVO();
    vo.setRank(participant.getFinalRank());
    vo.setUserId(participant.getUserId());
    vo.setScore(participant.getTotalScore() != null ? participant.getTotalScore().longValue() : null);
    vo.setPenalty(participant.getTotalPenalty() != null ? participant.getTotalPenalty().longValue() : null);
    vo.setProblemsSolved(participant.getAttemptCount() != null ? participant.getAttemptCount() : 0);
    vo.setIsParticipating(true);
    return vo;
}
```

### FRONTEND_PAGINATED_RESULT
// SOURCE: `console/src/api/contest.ts:176-244`
```typescript
export async function getContests(
  filters?: ContestFilters,
): Promise<PaginatedResult<ContestListItem>> {
  const params = new URLSearchParams();
  // ... build params
  const result = await apiGet<PaginatedResult<Record<string, unknown>>>(url);
  return {
    items: (result.items || []).map((r) => mapContestListItem(r as Record<string, unknown>)),
    total: result.total ?? 0,
    page: result.page ?? 1,
    pageSize: result.pageSize ?? 20,
    totalPages: result.totalPages ?? 0,
  };
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/.../ContestController.java` | UPDATE | Change `/upcoming`, `/running`, `/past` return types |
| `backend-spring/.../AdminContestController.java` | UPDATE | Change admin list return type |
| `backend-spring/.../ContestService.java` | UPDATE | Update interface signatures |
| `backend-spring/.../ContestServiceImpl.java` | UPDATE | Implement unified list methods |
| `backend-spring/.../RankingService.java` | UPDATE | Update ranking method signatures |
| `backend-spring/.../RankingServiceImpl.java` | UPDATE | Implement scenario-specific mappers |
| `backend-spring/.../ContestQueryDTO.java` | UPDATE | Remove alias fields |
| `backend-spring/.../UserContestHistoryVO.java` | CREATE | New DTO for user contest history |
| `backend-spring/.../RatingHistoryVO.java` | CREATE | New DTO for rating history |
| `backend-spring/.../LiveRankingEntryVO.java` | CREATE | New DTO for live ranking |
| `console/src/api/contest.ts` | UPDATE | Adapt to new paginated responses |
| `console/src/types/contest.ts` | UPDATE | Add/adjust frontend types |
| `management/src/api/admin/contests.ts` | UPDATE | Adapt to new admin list response |
| `backend-spring/.../ContestDtoAlignmentTest.java` | UPDATE | Add tests for new DTOs |

## NOT Building

- No database schema changes (all changes are in DTO/VO layer)
- No new API endpoints (only return types change)
- No authentication/authorization changes
- No changes to `ContestVO` or `ContestListVO` field definitions (Phase 1 handled those)
- No frontend component rewrites (only API layer and types change)
- No changes to `getGlobalRanking` or `getGlobalRankingsPaginated` (those are in Phase 4)

---

## Step-by-Step Tasks

### Task 1: Create Scenario-Specific Ranking VOs
- **ACTION**: Create 3 new VO classes in `backend-spring/.../dto/`
- **IMPLEMENT**:
  - `LiveRankingEntryVO` — fields: `rank`, `userId`, `username`, `name`, `avatar`, `score`, `penalty`, `problemsSolved`, `isCurrentUser`
  - `UserContestHistoryVO` — fields: `contestId`, `title`, `slug`, `startTime`, `finishTime`, `rank`, `score`, `penalty`, `problemsSolved`, `totalParticipants`, `isRated`
  - `RatingHistoryVO` — fields: `contestId`, `title`, `slug`, `ratingChange`, `newRating`, `oldRating`, `ratedAt`, `performance`
- **MIRROR**: `ContestListVO.java:14-36` (record-style with `@JsonInclude(JsonInclude.Include.NON_NULL)`)
- **IMPORTS**: `com.fasterxml.jackson.annotation.JsonInclude`, `io.swagger.v3.oas.annotations.media.Schema`, `java.time.LocalDateTime`
- **GOTCHA**: Keep field names camelCase. Do NOT use nested objects — the codebase uses flat structures.
- **VALIDATE**: Compile with `./mvnw compile`

### Task 2: Update RankingService Interface and Implementation
- **ACTION**: Change `RankingService` methods to return new VOs, update `RankingServiceImpl` mappers
- **IMPLEMENT**:
  - `getLiveRanking` → returns `List<LiveRankingEntryVO>`
  - `getUserContestHistory` → returns `List<UserContestHistoryVO>`
  - `getUserRatingHistory` → returns `List<RatingHistoryVO>` (still returns empty list until Phase 4)
- **MIRROR**: `RankingServiceImpl.java:100-133` (mapping pattern)
- **IMPORTS**: New VO classes
- **GOTCHA**: `getUserRatingHistory` currently returns `List.of()` — keep that behavior, just change return type.
- **VALIDATE**: `./mvnw compile`

### Task 3: Update ContestController Ranking Endpoints
- **ACTION**: Change return types of ranking endpoints in `ContestController.java`
- **IMPLEMENT**:
  - `GET /{id}/live-ranking` → `Result<List<LiveRankingEntryVO>>`
  - `GET /user/history` → `Result<List<UserContestHistoryVO>>`
  - `GET /user/rating-history` → `Result<List<RatingHistoryVO>>`
- **MIRROR**: `ContestController.java:265-302` (existing ranking endpoint pattern)
- **GOTCHA**: Ensure `@ApiResponse` `schema` annotations reference the correct VO class.
- **VALIDATE**: `./mvnw compile`

### Task 4: Unify List Endpoints to `PageResult<ContestListVO>`
- **ACTION**: Change `findUpcoming`, `findRunning`, `findPast` to return `PageResult<ContestListVO>`
- **IMPLEMENT**:
  1. In `ContestService.java`: Change signatures:
     - `findUpcoming(String userId)` → `PageResult<ContestListVO>`
     - `findRunning(String userId)` → `PageResult<ContestListVO>`
     - `findPast(Integer page, Integer pageSize, String userId)` → `PageResult<ContestListVO>`
  2. In `ContestServiceImpl.java`: Update implementations to use `toListVO()` and `PageResult.of()`.
  3. In `ContestController.java`: Update `/upcoming` and `/running` to accept optional `page`/`pageSize` params and return `PageResult<ContestListVO>`.
- **MIRROR**: `ContestServiceImpl.java:392-416` (`findAllListVO` pattern)
- **GOTCHA**: `/upcoming` and `/running` previously had no pagination — add defaults (`page=1`, `pageSize=20`, max 50).
- **VALIDATE**: `./mvnw compile`

### Task 5: Update Admin List Endpoint to Lightweight VO
- **ACTION**: Change `AdminContestController.listContests` and `ContestService.findAllAdmin` to return `PageResult<ContestListVO>`
- **IMPLEMENT**:
  1. In `ContestService.java`: `findAllAdmin` → `PageResult<ContestListVO>`
  2. In `ContestServiceImpl.java`: Replace `toVO` with `toListVO` in `findAllAdmin`
  3. In `AdminContestController.java`: Change return type to `Result<PageResult<ContestListVO>>`
- **MIRROR**: `ContestServiceImpl.java:423-448` (existing `findAllAdmin` body, just swap mapper)
- **GOTCHA**: Admin needs `isVisible` field — confirm `ContestListVO` already has it (yes, line 27).
- **VALIDATE**: `./mvnw compile`

### Task 6: Clean Up `ContestQueryDTO` Alias Fields
- **ACTION**: Remove `sortBy`, `limit`, `isPublic` from `ContestQueryDTO`; update all usages
- **IMPLEMENT**:
  1. Remove fields: `sortBy`, `limit`, `isPublic`
  2. In `ContestServiceImpl.findAllListVO`: Use `query.getSort()` only (line 401)
  3. In `ContestServiceImpl.findAllAdmin`: Use `query.getSort()` only (line 433)
  4. In `AdminContestController`: Change `sortBy` param to `sort`
- **MIRROR**: `ContestQueryDTO.java:1-54`
- **GOTCHA**: `AdminContestController` uses `sortBy` as `@RequestParam` — rename to `sort`.
- **VALIDATE**: `./mvnw compile`

### Task 7: Update Console Frontend API Layer
- **ACTION**: Adapt `console/src/api/contest.ts` to new paginated responses and new ranking types
- **IMPLEMENT**:
  1. `fetchUpcomingContests()` → return `PaginatedResult<ContestListItem>` (unwrap `.items`)
  2. `fetchRunningContests()` → return `PaginatedResult<ContestListItem>` (unwrap `.items`)
  3. `fetchPastContests()` → return `PaginatedResult<ContestListItem>` (align with `getContests()`)
  4. Remove `mapGlobalRankingEntry` usage for user history — use direct typing if backend now returns accurate types
- **MIRROR**: `console/src/api/contest.ts:135-161`
- **GOTCHA**: Check all Vue components that call these functions — they may expect arrays. Update to access `.items`.
- **VALIDATE**: `pnpm type-check` in `console/`

### Task 8: Update Management Frontend API Layer
- **ACTION**: Adapt `management/src/api/admin/contests.ts` to new admin list response
- **IMPLEMENT**:
  1. `contestsApi.getContests` currently returns `PageResult<Contest>` — change to `PageResult<ContestListItem>` or create a lightweight `AdminContestListItem` type.
  2. Update `Contest` interface or create separate `ContestListItem` type for admin.
- **MIRROR**: `management/src/api/admin/contests.ts:111-114`
- **GOTCHA**: Management `Contest` type has `currentParticipants` (line 23) while console uses `participantCount` — maintain this naming or decide on unification (out of scope for Phase 2, per analysis doc Phase 6).
- **VALIDATE**: `pnpm type-check` in `management/`

### Task 9: Update Frontend Types
- **ACTION**: Ensure `console/src/types/contest.ts` has correct types for new ranking VOs
- **IMPLEMENT**:
  - `UserContestHistory` should match `UserContestHistoryVO` fields
  - `RatingHistoryEntry` should match `RatingHistoryVO` fields
  - Add `LiveRankingEntry` type if not present
- **MIRROR**: `console/src/types/contest.ts:97-200`
- **GOTCHA**: Keep existing type names to minimize component changes; only adjust field definitions.
- **VALIDATE**: `pnpm type-check` in `console/`

### Task 10: Update DTO Alignment Tests
- **ACTION**: Add tests for new VOs and update existing tests in `ContestDtoAlignmentTest.java`
- **IMPLEMENT**:
  - Add `UserContestHistoryVOAlignmentTests` nested class
  - Add `RatingHistoryVOAlignmentTests` nested class
  - Update `ContestQueryDTOAlignmentTests` to verify `sortBy` is REMOVED (negated test)
- **MIRROR**: `ContestDtoAlignmentTest.java:197-244`
- **GOTCHA**: The existing test `contestQueryDTO_hasSortBy` will fail after Task 6 — either remove it or invert it.
- **VALIDATE**: `./mvnw test -Dtest=ContestDtoAlignmentTest`

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `findUpcoming` returns `PageResult<ContestListVO>` | status=UPCOMING | `items` is `List<ContestListVO>`, not `ContestVO` | Empty list |
| `findRunning` returns `PageResult<ContestListVO>` | status=RUNNING | `items` is `List<ContestListVO>`, not `ContestVO` | Empty list |
| `findPast` returns `PageResult<ContestListVO>` | page=1, pageSize=10 | `items` is `List<ContestListVO>`, not `ContestVO` | Pagination bounds |
| `findAllAdmin` returns `PageResult<ContestListVO>` | admin query | `items` is `List<ContestListVO>` | Includes drafts |
| `getLiveRanking` returns `List<LiveRankingEntryVO>` | contestId | VO has no `percentile` field | Empty ranking |
| `getUserContestHistory` returns `List<UserContestHistoryVO>` | userId | VO has `contestTitle` field | No history |
| `ContestQueryDTO` has no `sortBy` | reflection | `getFieldNames` does not contain `sortBy` | — |
| `ContestQueryDTO` has no `limit` | reflection | `getFieldNames` does not contain `limit` | — |

### Edge Cases Checklist
- [ ] Empty list for upcoming/running → still returns valid `PageResult` with empty `items`
- [ ] Page size > max → clamped to 50/100
- [ ] Admin list with drafts → `ContestListVO` must include `status` field (it does)
- [ ] Frontend components expecting arrays → verify `.items` access

---

## Validation Commands

### Static Analysis (Backend)
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw compile
```
EXPECT: Zero compilation errors

### Unit Tests (Backend)
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw test -Dtest=ContestDtoAlignmentTest
```
EXPECT: All tests pass

### Full Test Suite (Backend)
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw test
```
EXPECT: No regressions

### Type Check (Console Frontend)
```bash
cd /home/david/project/UltiCode-Public-Next/console
pnpm type-check
```
EXPECT: Zero type errors

### Type Check (Management Frontend)
```bash
cd /home/david/project/UltiCode-Public-Next/management
pnpm type-check
```
EXPECT: Zero type errors

### Lint Check (Frontends)
```bash
cd /home/david/project/UltiCode-Public-Next/console
pnpm lint
cd /home/david/project/UltiCode-Public-Next/management
pnpm lint
```
EXPECT: No lint errors

### Manual Validation
- [ ] Call `GET /contest/upcoming` → verify response is `PageResult<ContestListVO>` structure
- [ ] Call `GET /contest/running` → verify response is `PageResult<ContestListVO>` structure
- [ ] Call `GET /contest/past` → verify response is `PageResult<ContestListVO>` structure
- [ ] Call `GET /admin/contest` → verify response is `PageResult<ContestListVO>` structure
- [ ] Call `GET /contest/:id/live-ranking` → verify fields match `LiveRankingEntryVO`
- [ ] Call `GET /contest/user/history` → verify fields match `UserContestHistoryVO`
- [ ] Call `GET /contest/user/rating-history` → verify fields match `RatingHistoryVO` (or empty list)

---

## Acceptance Criteria
- [ ] All list endpoints return `PageResult<ContestListVO>`
- [ ] `ContestRankingVO` is no longer used for history or live ranking scenarios
- [ ] Admin list returns lightweight VO
- [ ] `ContestQueryDTO` has no alias fields (`sortBy`, `limit`, `isPublic` removed)
- [ ] All validation commands pass
- [ ] Frontend type checks pass
- [ ] No backend test regressions

## Completion Checklist
- [ ] Code follows discovered patterns (record-style VOs, `PageResult.of()`, batch enrichment)
- [ ] Error handling matches codebase style (`BusinessException` for not found)
- [ ] Logging follows codebase conventions (`log.info` for mutations)
- [ ] Tests follow test patterns (AssertJ, `@DisplayName`, nested classes)
- [ ] No hardcoded values (use constants for default page sizes)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Frontend components break due to paginated response shape change | Medium | High | Search all `.vue` files for `fetchUpcoming`, `fetchRunning`, `fetchPast` usage; update to unwrap `.items` |
| Management frontend DataTable expects `Contest` fields not in `ContestListVO` | Medium | High | Audit `management/src/views/contests/` for field access; add missing fields to `ContestListVO` if critical |
| Admin sort parameter renamed from `sortBy` to `sort` breaks existing admin requests | Low | Medium | Update admin frontend API call simultaneously |
| `findAllAdmin` needs fields not present in `ContestListVO` (e.g., `createdBy`) | Low | Medium | Check admin DataTable columns; if needed, add minimal fields to `ContestListVO` rather than reverting to `ContestVO` |

## Notes
- Phase 1 (already completed) fixed `contestId` type and `getStats()` logic. Phase 2 builds on a clean foundation.
- `ContestListVO` already contains 18 fields which should cover both console and management list views. If management needs an extra field (like `createdByUsername`), consider adding it to `ContestListVO` rather than creating a third VO type.
- The `getGlobalRanking` and `getGlobalRankingsPaginated` methods still use `ContestRankingVO` — that's correct since they ARE contest rankings. Only the overloaded uses (history, live, rating) are being split.
- `ContestRankingVO` itself should be trimmed after the split to remove fields that only belonged to history/rating scenarios (e.g., `percentile`, `maxRating`, `contestsAttended`).
