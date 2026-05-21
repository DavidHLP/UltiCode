# Plan: Solutions API Granularity Alignment

## Summary
Align the admin solutions management frontend-backend API contract to eliminate over-fetching in list views and fix the unimplemented `isDeleted` filter. Introduce a lightweight list-item VO for paginated responses, bypass `@TableLogic` when querying deleted records, and surface deletion metadata in the detail view.

## User Story
As an admin user managing solutions,
I want the solutions list to load quickly without downloading unnecessary large fields,
So that I can efficiently browse, filter (including deleted items), and review solution metadata.

## Problem → Solution
**Current state:**
- `GET /admin/solutions` returns full `AdminSolutionVO` including `content` (markdown body), causing significant over-fetching for list views.
- `isDeleted=true` query param is ignored because `@TableLogic` on `Solution` entity auto-filters deleted rows, and no bypass logic exists.
- `SolutionDetailView.vue` does not display `deletedAt` or `deletedBy` even though the backend returns them.

**Desired state:**
- List endpoint returns a slim `AdminSolutionListItemVO` excluding `content`, `summary`, `tags`, `publishedAt`, `flaggedReason`, etc.
- `isDeleted=true` correctly queries all rows including soft-deleted ones using raw SQL mapper methods.
- Detail view shows deletion metadata when applicable.

## Metadata
- **Complexity**: Medium
- **Source PRD**: `docs/solutions-api-granularity-analysis.md`
- **PRD Phase**: standalone
- **Estimated Files**: 12

---

## UX Design

### Before
```
Solutions List
- Loads full solution objects (content field included, often large markdown)
- Network payload ~5-10x larger than necessary
- isDeleted filter in toolbar does nothing (backend returns no deleted rows)

Solution Detail
- Status ticker shows: status, author, views
- No deletion info visible even for deleted solutions
```

### After
```
Solutions List
- Loads slim list-item objects (no content/summary/tags)
- Network payload reduced significantly
- isDeleted filter works correctly, showing deleted rows when enabled

Solution Detail
- Status ticker shows: status, author, views
- When isDeleted=true: also shows deletedAt and deletedBy
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Solutions list API | Returns `AdminSolutionVO` with `content` | Returns `AdminSolutionListItemVO` without `content` | Reduces payload |
| isDeleted filter | Backend ignores param | Backend queries including soft-deleted via raw SQL | Fixes filter |
| Detail status ticker | No deletion info | Shows `deletedAt`/`deletedBy` when deleted | Uses existing VO fields |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java` | 48-132 | Core pattern to follow for query logic and VO conversion |
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSolutionController.java` | 30-45 | Controller pattern and return types |
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSolutionVO.java` | all | Existing VO structure to slim down |
| P1 (important) | `backend-spring/src/main/java/com/ulticode/modules/solution/mapper/SolutionMapper.java` | all | Mapper pattern for adding raw SQL methods |
| P1 (important) | `management/src/api/admin/solutions.ts` | all | Frontend API types and contracts |
| P1 (important) | `management/src/views/solutions/SolutionsListView.vue` | 85-112 | useDataTable param transform and filters |
| P2 (reference) | `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java` | 1-50 | Reference for bypassing @TableLogic with raw SQL |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| MyBatis-Plus @TableLogic bypass | MyBatis-Plus docs | Custom @Select/@Update methods in mapper bypass automatic logic-delete injection |
| MyBatis-Plus raw SQL pagination | MyBatis-Plus docs | Use `Page<T>` as parameter and return type in custom mapper methods for pagination |

---

## Patterns to Mirror

### NAMING_CONVENTION
// SOURCE: `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSolutionVO.java:1-15`
```java
package com.ulticode.modules.admin.dto;

@Data
@Schema(description = "Admin solution view object")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminSolutionVO { ... }
```
- DTOs/VOs: PascalCase, end with `DTO` or `VO`
- Packages: `com.ulticode.modules.admin.dto`

### ERROR_HANDLING
// SOURCE: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java:142-148`
```java
Solution solution = solutionMapper.selectById(id);
if (solution == null) {
    throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
}
```
- Use `BusinessException` with `ErrorCode` enum for domain errors

### LOGGING_PATTERN
// SOURCE: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java:174`
```java
log.info("Solution flagged: {} by admin {}, reason: {}", id, adminId, reason);
```
- Use SLF4J `log.info/log.error` with placeholder formatting

### REPOSITORY_PATTERN
// SOURCE: `backend-spring/src/main/java/com/ulticode/modules/solution/mapper/SolutionMapper.java:14-27`
```java
@Mapper
public interface SolutionMapper extends BaseMapper<Solution> {
    @Update("UPDATE solutions SET ... WHERE id = #{id}")
    int updateFlagStatus(@Param("id") String id, ...);

    @Select("SELECT COUNT(*) FROM solutions WHERE user_id = #{userId} AND is_deleted = false")
    Long countByUserId(@Param("userId") String userId);
}
```
- Mapper interface extends `BaseMapper<Entity>`
- Raw SQL via `@Select`/`@Update` annotations
- Use `@Param` for parameter binding

### SERVICE_PATTERN
// SOURCE: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java:48-132`
```java
public PageResult<AdminSolutionVO> getSolutions(AdminSolutionQueryDTO query) {
    int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
    int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;
    LambdaQueryWrapper<Solution> wrapper = new LambdaQueryWrapper<>();
    // ... filters
    Page<Solution> pageResult = new Page<>(page, limit);
    Page<Solution> result = solutionMapper.selectPage(pageResult, wrapper);
    // batch load users/problems
    // map to VO
    return PageResult.of(voList, result.getTotal(), page, limit);
}
```

### TABLE_LOGIC_BYPASS_PATTERN
// SOURCE: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java`
```java
// Handle ARCHIVED (soft-deleted) queries via raw SQL to bypass @TableLogic
List<Problem> deletedProblems = problemMapper.selectDeletedProblems(query.getSearch(), currentPageSize, offset);
long total = problemMapper.countDeletedProblems(query.getSearch());
```

### TEST_STRUCTURE
// SOURCE: `backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImplTest.java:1-50`
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSubmissionServiceImpl")
class AdminSubmissionServiceImplTest {
    @Mock private SubmissionMapper submissionMapper;
    private AdminSubmissionServiceImpl adminSubmissionService;

    @BeforeEach
    void setUp() {
        adminSubmissionService = new AdminSubmissionServiceImpl(submissionMapper, ...);
    }
}
```
- MockitoExtension, @Mock, manual constructor injection in setUp
- AssertJ assertions, nested @Nested classes per method

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSolutionListItemVO.java` | CREATE | New slim VO for list responses |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/AdminSolutionService.java` | UPDATE | Change `getSolutions` return type to `PageResult<AdminSolutionListItemVO>` |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java` | UPDATE | Implement slim VO conversion, fix `isDeleted` filter with raw SQL bypass |
| `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSolutionController.java` | UPDATE | Update `getSolutions` return type to `PageResult<AdminSolutionListItemVO>` |
| `backend-spring/src/main/java/com/ulticode/modules/solution/mapper/SolutionMapper.java` | UPDATE | Add `selectPageWithDeleted` and `countWithDeleted` raw SQL methods |
| `backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImplTest.java` | CREATE | Unit tests for getSolutions with isDeleted and list VO mapping |
| `management/src/api/admin/solutions.ts` | UPDATE | Add `SolutionListItem` type, update `getSolutions` return type |
| `management/src/stores/admin/solutions.ts` | UPDATE | Update `fetchSolutions` to use `SolutionListItem` type |
| `management/src/views/solutions/columns.ts` | UPDATE | Change `ColumnDef<Solution>` to `ColumnDef<SolutionListItem>` |
| `management/src/views/solutions/SolutionsListView.vue` | UPDATE | Update `useDataTable` generic to `SolutionListItem` |
| `management/src/views/solutions/SolutionDetailView.vue` | UPDATE | Add `deletedAt`/`deletedBy` display in status ticker |
| `management/src/i18n/locales/zh-CN/modules/solutions.ts` | UPDATE | Add `deletedAt`/`deletedBy` i18n keys |
| `management/src/i18n/locales/en-US/modules/solutions.ts` | UPDATE | Add `deletedAt`/`deletedBy` i18n keys |
| `management/src/api/admin/__tests__/solutions.spec.ts` | UPDATE | Update mock types and assertions for list endpoint |

## NOT Building

- `getFlaggedSolutions` endpoint will NOT be removed (out of scope per analysis recommendation)
- `problemId` type mismatch (TS `number` vs Java `Long`) will NOT be changed (JSON serialization handles it; explicit type alignment is out of scope)
- No database schema changes (no migrations needed)
- No changes to `AdminSolutionVO` structure (detail endpoint continues using it)
- No new frontend filters added (toolbar remains the same; only `isDeleted` backend logic is fixed)

---

## Step-by-Step Tasks

### Task 1: Create `AdminSolutionListItemVO` (Backend)
- **ACTION**: Create new slim VO class for list responses
- **IMPLEMENT**: `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminSolutionListItemVO.java`
  - Fields: `id`, `title`, `language`, `views`, `isPublished`, `isFlagged`, `isDeleted`, `createdAt`, `author` (nested `AuthorInfo`), `problem` (nested `ProblemInfo`)
  - Exclude: `content`, `summary`, `tags`, `publishedAt`, `flaggedReason`, `flaggedAt`, `deletedAt`, `deletedBy`, `updatedAt`, `problemId`, `userId`, `publishedBy`
  - Use `@Data`, `@Schema`, `@JsonInclude(JsonInclude.Include.NON_NULL)`
  - Reuse inner classes `AuthorInfo` and `ProblemInfo` from `AdminSolutionVO` (or duplicate if needed — note: Java does not allow importing static inner classes from another file cleanly; either duplicate them or extract to standalone classes. For simplicity and following existing patterns, duplicate the nested classes in this VO.)
- **MIRROR**: `AdminSolutionVO.java:1-121` naming and annotation pattern
- **IMPORTS**: `com.fasterxml.jackson.annotation.JsonInclude`, `io.swagger.v3.oas.annotations.media.Schema`, `lombok.Data`, `java.time.LocalDateTime`
- **GOTCHA**: Do NOT reference `AdminSolutionVO.AuthorInfo` from outside — nested classes are not static in the importable sense. Duplicate the nested `AuthorInfo` and `ProblemInfo` classes inside `AdminSolutionListItemVO`.
- **VALIDATE**: Compile passes: `./mvnw compile -pl backend-spring`

### Task 2: Add Raw SQL Mapper Methods for Deleted Queries (Backend)
- **ACTION**: Extend `SolutionMapper` with methods to bypass `@TableLogic`
- **IMPLEMENT**: In `backend-spring/src/main/java/com/ulticode/modules/solution/mapper/SolutionMapper.java`, add:
  ```java
  @Select("<script>SELECT * FROM solutions WHERE is_deleted = true <if test='search != null and search != \"\"'> AND (title LIKE CONCAT('%',#{search},'%') OR content LIKE CONCAT('%',#{search},'%')) </if> ORDER BY ${sortBy} ${sortOrder} LIMIT #{limit} OFFSET #{offset}</script>")
  List<Solution> selectPageWithDeleted(@Param("search") String search, @Param("sortBy") String sortBy, @Param("sortOrder") String sortOrder, @Param("limit") int limit, @Param("offset") int offset);

  @Select("<script>SELECT COUNT(*) FROM solutions WHERE is_deleted = true <if test='search != null and search != \"\"'> AND (title LIKE CONCAT('%',#{search},'%') OR content LIKE CONCAT('%',#{search},'%')) </if></script>")
  long countWithDeleted(@Param("search") String search);
  ```
  Wait — using `${}` for sortBy/sortOrder is SQL injection risk. Use explicit whitelisting in Service layer before passing to mapper.
  Alternative: Use a simpler approach — query ALL (including deleted) with a custom method that bypasses logic delete by using `solutionMapper.selectList` with a wrapper on a non-`@TableLogic` entity? No, `@TableLogic` is global.
  
  Better approach matching ProblemServiceImpl pattern:
  ```java
  @Select("<script>SELECT * FROM solutions WHERE is_deleted = true <if test='search != null and search != \"\"'> AND (title LIKE CONCAT('%',#{search},'%') OR content LIKE CONCAT('%',#{search},'%')) </if> LIMIT #{limit} OFFSET #{offset}</script>")
  List<Solution> selectDeletedSolutions(@Param("search") String search, @Param("limit") int limit, @Param("offset") int offset);

  @Select("<script>SELECT COUNT(*) FROM solutions WHERE is_deleted = true <if test='search != null and search != \"\"'> AND (title LIKE CONCAT('%',#{search},'%') OR content LIKE CONCAT('%',#{search},'%')) </if></script>")
  long countDeletedSolutions(@Param("search") String search);
  ```
  Sorting for deleted queries: apply in-memory after fetching, or add ORDER BY with a fixed safe column. For simplicity, fetch without ORDER BY and sort in Service using stream (acceptable for admin deleted-items view which is low traffic). Or add `ORDER BY created_at DESC` directly in SQL as a safe default.
  
  Refined implementation:
  ```java
  @Select("<script>SELECT * FROM solutions WHERE is_deleted = true <if test='search != null and search != \"\"'> AND (title LIKE CONCAT('%',#{search},'%') OR content LIKE CONCAT('%',#{search},'%')) </if> ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}</script>")
  List<Solution> selectDeletedSolutions(@Param("search") String search, @Param("limit") int limit, @Param("offset") int offset);

  @Select("<script>SELECT COUNT(*) FROM solutions WHERE is_deleted = true <if test='search != null and search != \"\"'> AND (title LIKE CONCAT('%',#{search},'%') OR content LIKE CONCAT('%',#{search},'%')) </if></script>")
  long countDeletedSolutions(@Param("search") String search);
  ```
- **MIRROR**: `ProblemMapper.java` raw SQL pattern for `selectDeletedProblems`
- **IMPORTS**: `org.apache.ibatis.annotations.Param`, `org.apache.ibatis.annotations.Select`
- **GOTCHA**: XML `<script>` requires `search != null and search != ""` check. Ensure `<` is not interpreted as XML tag by IDE/build.
- **VALIDATE**: `./mvnw compile -pl backend-spring` passes

### Task 3: Update `AdminSolutionService` Interface (Backend)
- **ACTION**: Change `getSolutions` return type from `PageResult<AdminSolutionVO>` to `PageResult<AdminSolutionListItemVO>`
- **IMPLEMENT**: In `backend-spring/src/main/java/com/ulticode/modules/admin/service/AdminSolutionService.java`:
  - Update import and return type of `getSolutions`
  - Keep `getFlaggedSolutions` returning `PageResult<AdminSolutionVO>`? No — analysis says it's redundant but we keep it. However for consistency it should probably also return the slim VO since it calls `getSolutions`. Actually `getFlaggedSolutions` delegates to `getSolutions`, so if `getSolutions` returns `AdminSolutionListItemVO`, then `getFlaggedSolutions` should too. Update both.
  - Wait, the flagged endpoint is a list endpoint too — it should also return the slim VO.
- **MIRROR**: Existing interface pattern
- **IMPORTS**: `com.ulticode.modules.admin.dto.AdminSolutionListItemVO`
- **GOTCHA**: `getSolution(String id)` (single item) keeps `AdminSolutionVO`
- **VALIDATE**: `./mvnw compile -pl backend-spring` passes

### Task 4: Refactor `AdminSolutionServiceImpl.getSolutions()` (Backend)
- **ACTION**: Implement slim VO conversion and fix `isDeleted` filter
- **IMPLEMENT**: In `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImpl.java`:
  1. Add private method `toListItemVO(Solution, Map<String,User>, Map<Long,Problem>)` mapping only list fields
  2. In `getSolutions`:
     - When `query.getIsDeleted() != null && query.getIsDeleted()`:
       - Use `solutionMapper.selectDeletedSolutions(search, limit, (page-1)*limit)` and `solutionMapper.countDeletedSolutions(search)`
       - Batch-load users/problems
       - Map via `toListItemVO`
       - Return `PageResult.of(listItemVOList, total, page, limit)`
     - Otherwise:
       - Use existing `selectPage` logic (which auto-excludes deleted via @TableLogic)
       - Map via `toListItemVO`
       - Return `PageResult.of(listItemVOList, result.getTotal(), page, limit)`
  3. Remove the old comment `// Include soft-deleted - need to use native query...`
  4. Update `getFlaggedSolutions` to call the updated `getSolutions` which now returns `PageResult<AdminSolutionListItemVO>`
- **MIRROR**: `ProblemServiceImpl.java` raw SQL bypass pattern; existing `toAdminVO` conversion pattern
- **GOTCHA**: When `isDeleted` is null or false, `@TableLogic` auto-filters. Do NOT add `wrapper.eq(Solution::getIsDeleted, false)` explicitly — MyBatis-Plus does this automatically.
- **GOTCHA**: `selectDeletedSolutions` returns `List<Solution>`, not `Page<Solution>`. Compute pagination manually with offset/limit.
- **VALIDATE**: `./mvnw compile -pl backend-spring` passes; `./mvnw test -pl backend-spring -Dtest=AdminSolutionServiceImplTest` passes after Task 5

### Task 5: Update `AdminSolutionController` (Backend)
- **ACTION**: Update `getSolutions` and `getFlaggedSolutions` return types
- **IMPLEMENT**: In `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSolutionController.java`:
  - Change `getSolutions` return: `Result<PageResult<AdminSolutionListItemVO>>`
  - Change `getFlaggedSolutions` return: `Result<PageResult<AdminSolutionListItemVO>>`
  - Add import for `AdminSolutionListItemVO`
- **MIRROR**: Existing controller pattern
- **VALIDATE**: `./mvnw compile -pl backend-spring` passes

### Task 6: Write `AdminSolutionServiceImplTest` (Backend)
- **ACTION**: Create unit tests for the refactored service
- **IMPLEMENT**: `backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminSolutionServiceImplTest.java`
  - Mock `SolutionMapper`, `UserMapper`, `ProblemMapper`
  - Test `getSolutions` with `isDeleted=null` (normal query, @TableLogic excludes deleted)
  - Test `getSolutions` with `isDeleted=true` (calls `selectDeletedSolutions`)
  - Test `getSolutions` with `isDeleted=false` (normal query)
  - Test pagination params (page/limit defaults)
  - Test `toListItemVO` mapping (assert content is not present, author/problem are populated)
- **MIRROR**: `AdminSubmissionServiceImplTest.java` Mockito + AssertJ pattern
- **GOTCHA**: `selectDeletedSolutions` is a mapper method with `@Select` — mock it normally with Mockito
- **VALIDATE**: `./mvnw test -pl backend-spring -Dtest=AdminSolutionServiceImplTest` passes

### Task 7: Update Frontend API Types (`solutions.ts`)
- **ACTION**: Add `SolutionListItem` type and update `getSolutions` return type
- **IMPLEMENT**: In `management/src/api/admin/solutions.ts`:
  ```typescript
  export interface SolutionListItem {
    id: string
    title: string
    language: string
    views: number
    isPublished: boolean
    isFlagged: boolean
    isDeleted: boolean
    createdAt: string
    author: { id: string; username: string; name: string; email?: string }
    problem: { id: string; slug: string; title: string; difficulty: string }
  }
  ```
  - Update `getSolutions` return: `Promise<PageResult<SolutionListItem>>`
  - Update `getFlaggedSolutions` return: `Promise<PageResult<SolutionListItem>>`
  - Keep `Solution` interface unchanged (used by detail and mutations)
- **MIRROR**: Existing `Solution` interface pattern
- **VALIDATE**: `pnpm type-check` in `management/` passes

### Task 8: Update Frontend Store (`stores/admin/solutions.ts`)
- **ACTION**: Update `fetchSolutions` and `fetchFlaggedSolutions` to use `SolutionListItem`
- **IMPLEMENT**: In `management/src/stores/admin/solutions.ts`:
  - Change `solutions` ref type: `ref<SolutionListItem[]>([])`
  - Update `fetchSolutions` param types if needed
  - Keep `currentSolution` as `Solution | null` (detail uses full object)
  - Note: `flagSolution` and `unflagSolution` return `Solution` from API — when updating local list, since `solutions` is now `SolutionListItem[]`, we need to decide: either refresh the entire list after flag/unflag, or map the returned `Solution` to a partial update. The simplest approach: after flag/unflag success, call `fetchSolutions()` to refresh the list (this is what `bulkAction` already does). Update `flagSolution` and `unflagSolution` to call `fetchSolutions()` after mutating instead of direct array replacement. OR keep direct replacement but only set known fields. For simplicity and type safety, refresh the list.
  
  Actually, looking at the store, `flagSolution` returns a `Solution` and tries to replace `solutions.value[index] = solution`. This will cause a type error if `solutions` is `SolutionListItem[]`. Two options:
  1. After flag/unflag/delete, call `fetchSolutions()` to refresh
  2. Keep `solutions` as `Solution[]` and accept the over-fetching (defeats the purpose)
  
  Option 1 is correct. Update `flagSolution` and `unflagSolution` to call `fetchSolutions()` on success (remove direct array mutation). `deleteSolution` already removes from array — but since `solutions` is `SolutionListItem[]` and we only have the id, `splice` still works fine.
- **MIRROR**: Existing store pattern
- **GOTCHA**: Ensure `fetchSolutions()` is awaited in flag/unflag handlers so UI updates correctly
- **VALIDATE**: `pnpm type-check` in `management/` passes

### Task 9: Update Frontend Columns and List View
- **ACTION**: Update type references from `Solution` to `SolutionListItem` in list components
- **IMPLEMENT**:
  - `management/src/views/solutions/columns.ts`: Change `ColumnDef<Solution>` to `ColumnDef<SolutionListItem>`, `SolutionActions` parameter types, and `renderStatusBadge` parameter
  - `management/src/views/solutions/SolutionsListView.vue`: Update `useDataTable` generic from `Solution` to `SolutionListItem`
- **MIRROR**: Existing columns/list patterns
- **VALIDATE**: `pnpm type-check` in `management/` passes

### Task 10: Update Solution Detail View with Deletion Info
- **ACTION**: Add `deletedAt`/`deletedBy` display in status ticker when `isDeleted=true`
- **IMPLEMENT**: In `management/src/views/solutions/SolutionDetailView.vue`:
  - In the status ticker section (lines 149-186), after the `views` div, add a conditional div:
    ```vue
    <div v-if="solution.isDeleted" class="flex items-center gap-2">
      <span class="terminal-label">deleted:</span>
      <span class="font-data text-[var(--terminal-red)] tabular-nums">{{ formatDate(solution.deletedAt) }}</span>
      <span v-if="solution.deletedBy" class="font-data text-[var(--silver-400)]">by {{ solution.deletedBy }}</span>
    </div>
    ```
  - Import `formatDate` from `@/lib/format/date` if not already imported
- **MIRROR**: Existing status ticker pattern (lines 154-185)
- **GOTCHA**: `solution.deletedAt` may be undefined — use `formatDate` which likely handles undefined. Check `formatDate` signature.
- **VALIDATE**: `pnpm type-check` in `management/` passes

### Task 11: Add i18n Keys for Deletion Metadata
- **ACTION**: Add translation keys for `deletedAt` and `deletedBy` labels
- **IMPLEMENT**:
  - `management/src/i18n/locales/zh-CN/modules/solutions.ts`: Add `detail.deletedAt: '删除时间'`, `detail.deletedBy: '删除者'`
  - `management/src/i18n/locales/en-US/modules/solutions.ts`: Add `detail.deletedAt: 'Deleted At'`, `detail.deletedBy: 'Deleted By'`
  - Update `SolutionDetailView.vue` to use `t('solutions.detail.deletedAt')` and `t('solutions.detail.deletedBy')` instead of hardcoded labels
- **MIRROR**: Existing `detail.*` i18n pattern
- **VALIDATE**: `pnpm type-check` in `management/` passes; verify no missing key warnings at runtime

### Task 12: Update Frontend API Tests
- **ACTION**: Update `solutions.spec.ts` to use `SolutionListItem` for list mocks
- **IMPLEMENT**: In `management/src/api/admin/__tests__/solutions.spec.ts`:
  - Add `mockSolutionListItem` with only list fields
  - Update `mockPageResult` to use `PageResult<SolutionListItem>`
  - Keep `mockSolution` (full) for detail/mutation tests
  - Ensure all `getSolutions` tests use the slim mock
- **MIRROR**: Existing test pattern with `vi.mock`, `beforeEach`, `vi.clearAllMocks`
- **VALIDATE**: `pnpm test` in `management/` passes

---

## Testing Strategy

### Unit Tests (Backend)

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `getSolutions_normalQuery_excludesDeleted` | `isDeleted=null`, search="two" | Calls `selectPage`, returns `PageResult<AdminSolutionListItemVO>` | No |
| `getSolutions_isDeletedTrue_queriesDeleted` | `isDeleted=true` | Calls `selectDeletedSolutions`, returns deleted rows | Yes |
| `getSolutions_isDeletedFalse_normalQuery` | `isDeleted=false` | Calls `selectPage` (auto-excludes deleted) | Yes |
| `getSolutions_listItemVO_hasNoContent` | Any valid query | VO list items have `content=null` or field absent | Yes |
| `getSolutions_paginationDefaults` | `page=null, limit=null` | Defaults to page=1, limit=10 | Yes |
| `getSolutions_batchLoadedAuthorAndProblem` | Result has 2 records | Author and problem nested objects populated | No |

### Edge Cases Checklist
- [ ] Empty search with `isDeleted=true`
- [ ] `isDeleted=true` with no deleted records (returns empty list)
- [ ] Page size > 100 capped at 100
- [ ] Sort by unknown field falls back to `createdAt`
- [ ] Frontend `formatDate` called with `undefined` deletedAt

### Integration Tests
- Start backend dev server, call `GET /admin/solutions?isDeleted=true` — verify response contains only deleted items, no `content` field
- Call `GET /admin/solutions` — verify no deleted items, no `content` field
- Verify `GET /admin/solutions/{id}` still returns full `AdminSolutionVO` with `content`

---

## Validation Commands

### Static Analysis
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw compile
```
EXPECT: Zero compilation errors

### Unit Tests (Backend)
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw test -Dtest=AdminSolutionServiceImplTest
```
EXPECT: All tests pass

### Full Test Suite (Backend)
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw test
```
EXPECT: No regressions

### Frontend Type Check
```bash
cd /home/david/project/UltiCode-Public-Next/management && pnpm type-check
```
EXPECT: Zero type errors

### Frontend Tests
```bash
cd /home/david/project/UltiCode-Public-Next/management && pnpm test
```
EXPECT: All tests pass

### Frontend Lint
```bash
cd /home/david/project/UltiCode-Public-Next/management && pnpm lint
```
EXPECT: No lint errors

### Browser Validation
```bash
cd /home/david/project/UltiCode-Public-Next/management && pnpm dev
```
- Navigate to Solutions list
- Verify list loads without `content` in network response
- Apply `isDeleted` filter (if available in toolbar) or test via direct API call
- Open a deleted solution detail, verify `deletedAt`/`deletedBy` appears

---

## Acceptance Criteria
- [ ] `AdminSolutionListItemVO` created and used by list endpoints
- [ ] `GET /admin/solutions` returns slim VO (no `content` field)
- [ ] `GET /admin/solutions?isDeleted=true` returns soft-deleted records
- [ ] `GET /admin/solutions/{id}` continues returning full `AdminSolutionVO`
- [ ] Frontend list uses `SolutionListItem` type
- [ ] Frontend detail view displays `deletedAt`/`deletedBy` for deleted solutions
- [ ] i18n keys added for deletion metadata
- [ ] Backend unit tests written and passing
- [ ] Frontend API tests updated and passing
- [ ] No type errors, no lint errors, no regressions

## Completion Checklist
- [ ] Code follows discovered patterns (VO naming, mapper raw SQL, service batch loading)
- [ ] Error handling matches codebase style (`BusinessException` for not-found)
- [ ] Logging follows codebase conventions (SLF4J placeholders)
- [ ] Tests follow test patterns (MockitoExtension, AssertJ, nested classes)
- [ ] No hardcoded values (use constants or config)
- [ ] No mutation (immutable patterns used where applicable)
- [ ] Documentation updated (not needed — inline code is self-documenting)
- [ ] No unnecessary scope additions (flagged endpoint kept, problemId type unchanged)
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `@TableLogic` bypass via raw SQL behaves differently in tests vs production | Low | High | Integration test with real database (Testcontainers) to verify |
| Frontend `SolutionListItem` vs `Solution` type mismatch in shared components | Medium | Medium | Type-check after every file change; `columns.ts` and `SolutionsListView.vue` both updated |
| `formatDate` does not handle `undefined` gracefully | Low | Low | Test manually; add fallback if needed |
| Sorting on deleted query is inconsistent with normal query | Low | Low | Use `created_at DESC` as safe default in raw SQL; acceptable for low-traffic admin view |

## Notes
- The `ProblemServiceImpl` raw SQL bypass pattern (`selectDeletedProblems`) was used as the primary reference for implementing `isDeleted` filtering. This is a well-established pattern in this codebase.
- `AdminSolutionListItemVO` intentionally duplicates `AuthorInfo` and `ProblemInfo` nested classes rather than extracting them to shared top-level classes. This follows the existing `AdminSolutionVO` pattern and avoids refactoring unrelated code.
- `getFlaggedSolutions` endpoint is kept but its return type changes to `PageResult<AdminSolutionListItemVO>` for consistency. If a future task removes this endpoint, the change is trivial.
- The frontend store's direct array mutation for `flagSolution`/`unflagSolution` was replaced with a full list refresh to maintain type safety with `SolutionListItem[]`. This is a minor UX tradeoff (one extra API call) for correctness.
