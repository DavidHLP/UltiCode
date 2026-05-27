# Plan: ProblemSet 前后端颗粒度与逻辑对齐

## Summary
将 `/problemset` 页面从"前端本地筛选第一页20条"改为真正的服务端分页/筛选。修复 tags 形态不匹配、difficulty 大小写不一致、category 参数不闭环、题单详情 problem 粒度过薄等前后端契约不对齐问题。

## User Story
As a user browsing the problem set,
I want filters (difficulty, tags, status, search, category, premium) to work against the full database,
So that I see accurate results and don't miss problems beyond the first page.

## Problem → Solution
**Current state:** Frontend fetches only page 1 (20 items) then filters locally; many filters appear broken because data is incomplete or field shapes mismatch.
**Desired state:** Frontend sends all filter params to backend; backend returns paginated results matching filters; tags/difficulty/category contracts are consistent.

## Metadata
- **Complexity**: XL
- **Source PRD**: `docs/problemset-frontend-backend-alignment-analysis.md`
- **PRD Phase**: standalone
- **Estimated Files**: 15+

---

## UX Design

### Before
```
/problemset
- Load 20 problems from backend
- Search box: filters locally within 20 items
- Tag filter: filters locally; may show [object Object]
- Difficulty filter: fails due to Easy vs EASY mismatch
- Category filter: URL changes to /problemset/algorithms but backend ignores it
- Load More: just increases local slice, no new API call
```

### After
```
/problemset
- Load problems with server-side pagination (pageSize=50)
- Search box: debounced, calls GET /problems?search=...&page=1
- Tag filter: calls GET /problems?tag=数组&page=1
- Difficulty filter: calls GET /problems?difficulty=EASY&page=1
- Category filter: calls GET /problems?category=algorithms&page=1
- Load More: requests next page from server
- All filters reset page to 1
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Search box | Local filter on 20 items | Debounced server search | Reset page to 1 on search |
| Tag select | Local filter; broken on object tags | Server filter via `tag` param | Single tag per request |
| Difficulty | Local filter; case mismatch | Server filter; unified EASY/MEDIUM/HARD | Backend already stores uppercase |
| Category | Local filter via i18n key | Server filter via `category` param | Need category→tag mapping |
| Status | Local filter | Server filter via `status` param | Backend from auth context |
| Premium | Local filter | Server filter via `isPremium` param | New DTO field |
| Load More | Local slice increase | Fetch next page | Track `page` and `hasMore` |
| Problem List detail | Thin ProblemInListVO | Full ProblemVO + sortOrder | Same component renders both |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/src/main/java/com/ulticode/modules/problem/controller/ProblemController.java` | 46-70 | Controller only exposes 5 params; need to expand |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/problem/dto/ProblemQueryDTO.java` | all | DTO already has fields Controller doesn't use |
| P0 | `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java` | 88-228 | Service already supports many filters; `toVO` uppercases difficulty |
| P0 | `console/src/api/problem.ts` | all | Frontend API layer; `mapProblem` and `fetchProblems` need overhaul |
| P0 | `console/src/types/problem.ts` | all | `difficulty` casing and `tags` shape must align with backend |
| P0 | `console/src/components/problem/composables/useProblemExplorer.ts` | all | Core composable; local filter logic → server-driven |
| P1 | `backend-spring/src/main/java/com/ulticode/modules/problem/dto/ProblemVO.java` | all | `tags` is `List<ProblemTagVO>`, not `string[]` |
| P1 | `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java` | 40-76 | PUBLIC_ENDPOINTS missing `/problem-lists/**` |
| P1 | `console/src/constants/problem-categories.ts` | all | Categories use i18n keys, not tag slugs |
| P1 | `console/src/api/problem-list.ts` | 160-215 | `fetchProblemListOverview` maps problems via `mapProblem` |
| P2 | `backend-spring/src/main/java/com/ulticode/modules/problemlist/controller/ProblemListController.java` | 28-46 | Anonymous logic exists but blocked by SecurityConfig |
| P2 | `backend-spring/src/main/java/com/ulticode/modules/problemlist/service/impl/ProblemListServiceImpl.java` | 42-222 | `findAll` for anonymous; `getListOverview` returns thin ProblemInListVO |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| MyBatis-Plus QueryWrapper | Context7 / MyBatis-Plus docs | `apply()` for raw SQL subqueries (tag filter); `orderBy` for dynamic sort |
| Spring @ModelAttribute | Spring docs | Can bind all query params to DTO automatically, cleaner than @RequestParam |
| Vue `watch` debounce | vue docs | Use `watch` + `setTimeout` or `useDebounce` composable for search input |

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: `console/src/api/problem.ts:68-82`
```typescript
export async function fetchProblems(
  userId?: string,
  filters: { category?: string; search?: string } = {},
): Promise<Problem[]> {
```
Pattern: `fetch{Resource}{Plural}` with optional userId first, filters object second.

### ERROR_HANDLING
// SOURCE: `console/src/components/problem/composables/useProblemExplorer.ts:57-67`
```typescript
const loadProblems = async () => {
  try {
    const userId = useAuthStore().fetchCurrentUserId();
    fallbackProblems.value = await fetchProblems(userId ?? undefined, {
      category: selectedCategory.value,
    });
  } catch (error) {
    console.error("Failed to load problems", error);
    fallbackProblems.value = [];
  }
};
```
Pattern: Wrap async data load in try/catch, set fallback to empty array on failure.

### LOGGING_PATTERN
// SOURCE: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:453`
```java
log.info("Problem created: {} by user {}", problem.getId(), operatorId);
```
Pattern: Use slf4j placeholders `{}`, not string concatenation.

### SERVICE_PATTERN
// SOURCE: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:89-137`
```java
public PageResult<ProblemVO> listProblems(ProblemQueryDTO query) {
    int currentPage = ...;
    int currentPageSize = ...;
    LambdaQueryWrapper<Problem> queryWrapper = buildProblemQueryWrapper(query);
    Page<Problem> problemPage = new Page<>(currentPage, currentPageSize);
    Page<Problem> result = problemMapper.selectPage(problemPage, queryWrapper);
    // batch-fetch tags, counts
    return PageResult.of(problemVOList, result.getTotal(), currentPage, currentPageSize);
}
```
Pattern: Default pagination in service, build wrapper, batch fetch related data, return `PageResult`.

### REPOSITORY_PATTERN
// SOURCE: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:154-228`
```java
private LambdaQueryWrapper<Problem> buildProblemQueryWrapper(ProblemQueryDTO query) {
    LambdaQueryWrapper<Problem> queryWrapper = new LambdaQueryWrapper<>();
    if (query.getDifficulty() != null && !query.getDifficulty().isBlank()) {
        queryWrapper.apply("UPPER(difficulty) = UPPER({0})", query.getDifficulty());
    }
    // ...
    return queryWrapper;
}
```
Pattern: Null-safe blank checks before adding query conditions.

### TEST_STRUCTURE
// SOURCE: project uses JUnit 5 + Mockito (see java/testing.md rules)
```java
@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {
    @Mock private ProblemMapper problemMapper;
    private ProblemService problemService;
    @BeforeEach void setUp() { problemService = new ProblemServiceImpl(...); }
}
```

---

## Files to Change

### Backend (Java)

| File | Action | Justification |
|---|---|---|
| `ProblemController.java` | UPDATE | Expand `listProblems` to accept full `ProblemQueryDTO` via `@ModelAttribute` or all RequestParams |
| `ProblemQueryDTO.java` | UPDATE | Add `category` and `isPremium` fields; ensure getters/setters for Lombok |
| `ProblemServiceImpl.java` | UPDATE | Add `category` filter logic in `buildProblemQueryWrapper`; add `isPremium` filter; normalize difficulty handling |
| `ProblemVO.java` | UPDATE | Consider `from()` method difficulty casing; `ProblemTagVO` remains object array |
| `SecurityConfig.java` | UPDATE | Add `/problem-lists/overview` and `/problem-lists/*/overview` to PUBLIC_ENDPOINTS |
| `ProblemListController.java` | UPDATE | Add `@ModelAttribute` or explicit params if changing endpoint signatures |
| `ProblemListServiceImpl.java` | UPDATE | Return full `ProblemVO` for list problems (or richer DTO); compute real stats from user submissions |

### Frontend (TypeScript/Vue)

| File | Action | Justification |
|---|---|---|
| `console/src/types/problem.ts` | UPDATE | Change `difficulty` to `"EASY" \| "MEDIUM" \| "HARD"`; keep `tags: string[]` but ensure mapper normalizes |
| `console/src/api/problem.ts` | UPDATE | Rewrite `fetchProblems` to accept all filter params + page/pageSize; fix `mapProblem` tags normalization |
| `console/src/components/problem/composables/useProblemExplorer.ts` | UPDATE | Replace local filtering with server-driven; add page tracking; debounce search |
| `console/src/constants/problem-categories.ts` | UPDATE | Map each category to tag slugs/labels if category is tag-based |
| `console/src/api/problem-list.ts` | UPDATE | `fetchProblemListOverview` already uses `mapProblem` — ensure it receives rich problems |
| `console/src/views/problem-list/composables/useProblemListOperations.ts` | UPDATE | `handleAddProblem` should re-fetch overview instead of local append to avoid mixed granularity |
| `console/src/components/problem/ProblemFilterPanel.vue` | MAYBE UPDATE | If tag chips expect string[] — should still work after mapper fix |
| `console/src/components/problem/ProblemResultList.vue` | MAYBE UPDATE | Difficulty color class logic may need adjustment for uppercase |

---

## NOT Building

- **New database migrations** — category as independent field is out of scope; use existing tag-based mapping
- **Full-text search engine** — rely on existing title/ID search in Service
- **Redis caching for problem list** — not in scope
- **Real-time updates** — WebSocket or SSE for problem list changes not needed
- **Advanced analytics/stats** — real submission-based stats for problem lists is MEDIUM priority, may be deferred
- **Multi-tag OR/AND filtering** — backend `tag` param supports single tag only; multi-tag is out of scope for this pass
- **Sort UI in frontend** — backend supports `sortBy/sortOrder` but no frontend sort controls exist yet; wiring is out of scope

---

## Step-by-Step Tasks

### Task 1: Fix `mapProblem()` tags normalization
- **ACTION**: Update `console/src/api/problem.ts` `mapProblem()` to handle both `tagRelations` and `tags` object arrays
- **IMPLEMENT**:
  ```typescript
  tags: Array.isArray(p.tags)
    ? p.tags
        .map((tag) => typeof tag === 'string' ? tag : tag?.label)
        .filter((l): l is string => typeof l === 'string')
    : Array.isArray(p.tagRelations)
      ? p.tagRelations
          .map((r: { tag?: { label: string } }) => r.tag?.label)
          .filter((l): l is string => typeof l === 'string')
      : [],
  ```
- **MIRROR**: ERROR_HANDLING pattern from `useProblemExplorer.ts`
- **IMPORTS**: None new
- **GOTCHA**: `ProblemTagVO` has `id` and `label`; we only need `label` for frontend display/filtering
- **VALIDATE**: Type-check passes; tags render as strings not `[object Object]`

### Task 2: Unify difficulty casing in frontend type
- **ACTION**: Update `console/src/types/problem.ts` to use `"EASY" | "MEDIUM" | "HARD"`
- **IMPLEMENT**: Change `difficulty` type; update all components that depend on the casing
- **MIRROR**: NAMING_CONVENTION — string literal union over enum
- **IMPORTS**: None
- **GOTCHA**: `ProblemResultList.vue` and `ProblemFilterPanel.vue` may have `toLowerCase()` or hardcoded `Easy/Medium/Hard` values
- **VALIDATE**: Search codebase for `"Easy"`, `"Medium"`, `"Hard"` strings and update

### Task 3: Expand backend `ProblemQueryDTO`
- **ACTION**: Add `category` and `isPremium` fields to `ProblemQueryDTO.java`
- **IMPLEMENT**:
  ```java
  @Schema(description = "Filter by category", example = "algorithms")
  private String category;

  @Schema(description = "Filter by premium status", example = "false")
  private Boolean isPremium;
  ```
- **MIRROR**: Existing DTO field patterns
- **IMPORTS**: None new
- **GOTCHA**: Lombok `@Data` should generate getters automatically; verify `getIsPremium()` doesn't conflict with boolean naming
- **VALIDATE**: Compile `./mvnw compile`

### Task 4: Expand backend `ProblemController.listProblems()`
- **ACTION**: Replace manual RequestParam extraction with `@ModelAttribute ProblemQueryDTO query`
- **IMPLEMENT**:
  ```java
  @GetMapping
  public Result<PageResult<ProblemVO>> listProblems(@ModelAttribute ProblemQueryDTO query) {
      PageResult<ProblemVO> result = problemService.listProblems(query);
      return Result.success(result);
  }
  ```
- **MIRROR**: SERVICE_PATTERN from existing service methods
- **IMPORTS**: `org.springframework.web.bind.annotation.ModelAttribute`
- **GOTCHA**: `@ModelAttribute` binds query params to DTO by name; ensure field names match exactly. Test that Swagger docs still render correctly.
- **VALIDATE**: `./mvnw compile`; check Swagger UI for parameter docs

### Task 5: Add category and isPremium to `buildProblemQueryWrapper()`
- **ACTION**: Add filter conditions for new DTO fields
- **IMPLEMENT**:
  ```java
  // Filter by premium status
  if (query.getIsPremium() != null) {
      queryWrapper.eq(Problem::getIsPremium, query.getIsPremium());
  }

  // Filter by category (maps to tag set)
  if (query.getCategory() != null && !query.getCategory().isBlank() && !"all".equalsIgnoreCase(query.getCategory())) {
      // Option A: If category is just a tag alias, map to tag label
      // Option B: Subquery against a category mapping table
      // For now, map known categories to tag labels via a static map
      Set<String> categoryTags = CATEGORY_TAG_MAP.get(query.getCategory().toLowerCase());
      if (categoryTags != null && !categoryTags.isEmpty()) {
          queryWrapper.apply("id IN (SELECT ptr.problem_id FROM problem_tag_relations ptr " +
              "LEFT JOIN problem_tags pt ON ptr.tag_id = pt.id " +
              "WHERE pt.label IN ({0}))", String.join(",", categoryTags));
      }
  }
  ```
- **MIRROR**: REPOSITORY_PATTERN from existing tag filter
- **IMPORTS**: `java.util.Set`, `java.util.Map`
- **GOTCHA**: The `apply()` with `IN` clause needs proper SQL parameterization. Consider using `in()` via MyBatis-Plus or a subquery approach. Alternatively, if category maps 1:1 to a single tag, simplify to single tag filter.
- **VALIDATE**: Integration test with `?category=algorithms`

### Task 6: Frontend `fetchProblems()` — server-driven API
- **ACTION**: Rewrite `fetchProblems` to accept all filter params and pagination
- **IMPLEMENT**:
  ```typescript
  export interface ProblemFilters {
    category?: string
    search?: string
    difficulty?: string
    status?: string
    tag?: string
    isPremium?: boolean
    sortBy?: string
    sortOrder?: string
  }

  export async function fetchProblems(
    filters: ProblemFilters = {},
    page: number = 1,
    pageSize: number = 50,
  ): Promise<{ items: Problem[]; total: number; page: number; pageSize: number; totalPages: number }> {
    const params = new URLSearchParams()
    params.append('page', String(page))
    params.append('pageSize', String(pageSize))
    if (filters.search) params.append('search', filters.search)
    if (filters.difficulty) params.append('difficulty', filters.difficulty)
    if (filters.status) params.append('status', filters.status)
    if (filters.tag) params.append('tag', filters.tag)
    if (filters.category && filters.category !== 'all') params.append('category', filters.category)
    if (filters.isPremium !== undefined) params.append('isPremium', String(filters.isPremium))
    if (filters.sortBy) params.append('sortBy', filters.sortBy)
    if (filters.sortOrder) params.append('sortOrder', filters.sortOrder)

    const response = await apiGet<{
      items: unknown[]
      total: number
      page: number
      pageSize: number
      totalPages: number
    }>(`/problems?${params.toString()}`)
    return {
      items: response.items.map(mapProblem),
      total: response.total,
      page: response.page,
      pageSize: response.pageSize,
      totalPages: response.totalPages,
    }
  }
  ```
- **MIRROR**: NAMING_CONVENTION from existing `fetchProblems`
- **IMPORTS**: None new
- **GOTCHA**: Remove `userId` parameter — backend should read from auth context for status. Ensure backward compatibility if other callers pass userId.
- **VALIDATE**: Type-check; grep for all `fetchProblems` callers and update signatures

### Task 7: Rewrite `useProblemExplorer` for server-driven filtering
- **ACTION**: Replace local `filteredProblems` computed with server-fetched state; add debounced search
- **IMPLEMENT**:
  - Remove local filtering logic (`filteredProblems` computed)
  - Add `page`, `total`, `totalPages`, `isLoading` refs
  - `loadProblems()` calls `fetchProblems(allFilters, page.value, pageSize)`
  - Watch `[searchQuery]` with debounce (300ms) → reset page to 1 → call `loadProblems()`
  - Watch `[selectedTags, selectedDifficulty, selectedStatus, showPremium, selectedCategory]` → reset page to 1 → call `loadProblems()`
  - `loadMore()` increments `page` and appends results
- **MIRROR**: ERROR_HANDLING pattern from existing `loadProblems`
- **IMPORTS**: May need a `useDebounce` composable or inline setTimeout
- **GOTCHA**: `selectedTags` is array but backend only supports single `tag`; for multi-tag frontend, either send first tag or do local secondary filter. **Decision**: send first selected tag to backend, keep local filter for additional tags (document this).
- **VALIDATE**: Manual browser test: apply filters, verify network requests contain correct params

### Task 8: Update category constants for tag mapping
- **ACTION**: If category is tag-based, add `tags` field to each category constant
- **IMPLEMENT**:
  ```typescript
  export const PROBLEM_CATEGORIES = [
    {
      // ...
      value: 'algorithms',
      tags: ['数组', '哈希表', '字符串', '数学', '动态规划', '排序', '链表', '递归'],
    },
    // ...
  ] as const;
  ```
- **MIRROR**: N/A — new data shape
- **IMPORTS**: None
- **GOTCHA**: Actual tag labels must match database tag labels exactly. Verify against `problem_tags` table.
- **VALIDATE**: Check that `useProblemExplorer` category filter uses `tags` instead of `name`

### Task 9: Fix SecurityConfig for problem-lists public GET
- **ACTION**: Add public GET endpoints for problem-lists to PUBLIC_ENDPOINTS
- **IMPLEMENT**:
  ```java
  // Problem list public read access
  "/problem-lists/overview",
  "/problem-lists/*/overview",
  ```
  Note: Ant pattern `/problem-lists/*/overview` should work. If not, use a custom `RequestMatcher`.
- **MIRROR**: PUBLIC_ENDPOINTS pattern from SecurityConfig
- **IMPORTS**: None
- **GOTCHA**: Must NOT expose POST/PATCH/DELETE/save/fork endpoints. Only GET `/overview` and `/{id}/overview` should be public.
- **VALIDATE**: Curl unauthenticated `GET /problem-lists/overview` → 200; unauthenticated `POST /problem-lists` → 401

### Task 10: Fix problem list detail problem granularity
- **ACTION**: Backend returns richer problem data in `ProblemListDetailVO`; frontend `mapProblem` handles it
- **IMPLEMENT** (Backend):
  In `ProblemListServiceImpl.getListOverview()`, instead of building `ProblemInListVO`, call `problemService.toVO(problem)` and add `sortOrder`/`addedAt`.
  Or create a new DTO `ProblemListItemVO extends ProblemVO` with extra fields.
- **IMPLEMENT** (Frontend):
  `fetchProblemListOverview` already maps via `mapProblem`, so if backend returns `ProblemVO` shape, it should work automatically.
- **MIRROR**: SERVICE_PATTERN
- **IMPORTS**: `ProblemService` or `ProblemVO` in problemlist module
- **GOTCHA**: Circular dependency between `problem` and `problemlist` modules. Use DTO in `problemlist` module rather than importing service.
- **VALIDATE**: Problem list detail shows acceptance rate, tags, premium status correctly

### Task 11: Fix `handleAddProblem` mixed granularity
- **ACTION**: Re-fetch overview after add/remove instead of local mutation
- **IMPLEMENT**:
  ```typescript
  async function handleAddProblem(problem: Problem) {
    if (!currentUser || problemIdsInList.value.has(problem.id)) return
    try {
      await addProblemToList(listId.value, problem.id)
      await loadProblemList(listId.value)  // Re-fetch instead of local append
      toast.success(...)
    } catch { ... }
  }
  ```
- **MIRROR**: ERROR_HANDLING from existing handlers
- **IMPORTS**: None
- **GOTCHA**: Re-fetch is slightly slower UX but guarantees data consistency
- **VALIDATE**: Add problem, verify list refreshes with correct data shape

### Task 12: Run type-check, lint, and backend compile
- **ACTION**: Verify all changes compile and type-check
- **IMPLEMENT**: Run validation commands (see below)
- **MIRROR**: N/A
- **IMPORTS**: N/A
- **GOTCHA**: Frontend and backend may have cascading type errors after enum casing change
- **VALIDATE**: Zero errors from all validation commands

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `mapProblem` with string tags | `{ tags: ["数组", "链表"] }` | `{ tags: ["数组", "链表"] }` | No |
| `mapProblem` with object tags | `{ tags: [{ id: "数组", label: "数组" }] }` | `{ tags: ["数组"] }` | Yes |
| `mapProblem` with mixed tags | `{ tags: ["数组", { label: "链表" }] }` | `{ tags: ["数组", "链表"] }` | Yes |
| `fetchProblems` builds correct URL | filters `{ difficulty: "EASY", page: 2 }` | URL contains `difficulty=EASY&page=2` | No |
| `buildProblemQueryWrapper` category | `category="algorithms"` | SQL contains category tag subquery | No |
| `buildProblemQueryWrapper` isPremium | `isPremium=true` | `eq(is_premium, true)` | No |

### Edge Cases Checklist
- [ ] Empty search query (should not append `search=` param)
- [ ] Category `"all"` (should not send category param)
- [ ] Page 0 or negative (backend defaults to 1)
- [ ] PageSize > 100 (backend caps at 100)
- [ ] Invalid difficulty string (backend case-insensitive match)
- [ ] Tag that doesn't exist (returns empty result set)
- [ ] Unauthenticated request to `/problem-lists/overview` (returns 200 with public lists)
- [ ] Rapid filter changes (debounce cancels previous request)

---

## Validation Commands

### Static Analysis (Backend)
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw compile -q
```
EXPECT: BUILD SUCCESS

### Static Analysis (Frontend)
```bash
cd /home/david/project/UltiCode-Public-Next/console
pnpm type-check
```
EXPECT: Zero type errors

### Lint (Frontend)
```bash
cd /home/david/project/UltiCode-Public-Next/console
pnpm lint
```
EXPECT: No lint errors

### Backend Tests
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring
./mvnw test -q
```
EXPECT: All tests pass

### Manual API Validation
```bash
# Test basic list
curl -sS 'http://localhost:9001/problems?page=1&pageSize=5'

# Test difficulty filter
curl -sS 'http://localhost:9001/problems?difficulty=EASY&pageSize=5'

# Test tag filter
curl -sS 'http://localhost:9001/problems?tag=数组&pageSize=5'

# Test category filter
curl -sS 'http://localhost:9001/problems?category=algorithms&pageSize=5'

# Test search
curl -sS 'http://localhost:9001/problems?search=链表&pageSize=5'

# Test problem-lists public access
curl -sS 'http://localhost:9001/problem-lists/overview'
```

### Browser Validation
```bash
# Start dev servers
pm2 start ecosystem.config.cjs
# Or individually:
cd /home/david/project/UltiCode-Public-Next/console && pnpm dev
```
- Navigate to `http://localhost:9002/problemset`
- Apply each filter and verify network panel shows correct query params
- Verify "Load More" fetches next page
- Verify difficulty colors display correctly
- Verify tags display as strings not objects
- Navigate to `http://localhost:9002/problemset/list/{id}`
- Verify problem list shows acceptance rate, tags, premium status

---

## Acceptance Criteria
- [ ] All tasks completed
- [ ] All validation commands pass
- [ ] Tests written and passing (or existing tests updated)
- [ ] No type errors
- [ ] No lint errors
- [ ] `/problemset` filters work against full database
- [ ] Tags render as readable strings
- [ ] Difficulty colors display correctly
- [ ] Unauthenticated users can view public problem lists
- [ ] Problem list detail shows full problem information

## Completion Checklist
- [ ] Code follows discovered patterns
- [ ] Error handling matches codebase style
- [ ] Logging follows codebase conventions
- [ ] Tests follow test patterns
- [ ] No hardcoded values (use constants/config)
- [ ] Documentation updated (Swagger annotations accurate)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Category-to-tag mapping inaccurate | Medium | High | Verify against actual `problem_tags` table data before defining mappings |
| `@ModelAttribute` binding issues with boolean `isPremium` | Low | Medium | Test manually; fallback to explicit `@RequestParam` if needed |
| Frontend enum casing change breaks management UI | Medium | High | Search all frontend usages before changing; management may also use `Problem` type |
| Debounce implementation adds complexity | Low | Low | Use simple `setTimeout` pattern or extract `useDebounce` composable |
| ProblemListDetailVO circular dependency | Low | Medium | Use flat DTO fields rather than importing `ProblemService` into `ProblemListService` |
| Public endpoint security regression | Low | High | Carefully test that only GET `/overview` paths are public; write/adjust security tests |

## Notes
- The analysis doc at `docs/problemset-frontend-backend-alignment-analysis.md` contains detailed evidence and curl commands for runtime verification. Refer to it for specific line numbers if something is unclear during implementation.
- `ProblemVO.ProblemTagVO` is an object with `id` and `label`. The list API returns these objects. The frontend `Problem` type uses `tags: string[]`. The `mapProblem` function is the single point where this translation must happen.
- Backend `ProblemServiceImpl.toVO()` uppercases difficulty (`problem.getDifficulty().toUpperCase()`). This means the API returns `"EASY"`, `"MEDIUM"`, `"HARD"`. The frontend type should match this.
- For `category` implementation: if the database does not have a `category` column on problems, the simplest approach is to map category names to sets of tag labels and use the existing tag subquery. This avoids schema changes. If a category should map to a single tag, even simpler.
- The `userId` parameter sent by frontend is ignored by backend. Do NOT add it to the new `fetchProblems` signature. Backend should compute `status` from auth context + submissions table when that feature is ready. For now, backend returns the entity's `status` field.
- `ProblemListServiceImpl.getListOverview()` currently builds thin `ProblemInListVO`. The cleanest fix is to change `ProblemListDetailVO.problems` from `List<ProblemInListVO>` to `List<ProblemVO>` (or a new DTO that extends ProblemVO with `sortOrder` and `addedAt`). This requires updating the DTO class.
