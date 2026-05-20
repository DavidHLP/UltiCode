# Plan: Align Problem-List Frontend-Backend Granularity

## Summary
Add three fine-grained PATCH endpoints to `AdminProblemListController` (`/{id}/basic-info`, `/{id}/visibility`, `/{id}/banner`) to match the frontend's section-level auto-save UX. Fix DTO validation inconsistencies and remove unused/phantom fields (`slug`, `authorId`). Delegate to the already-existing fine-grained methods in `ProblemListService`.

## User Story
As an admin user editing a problem list,
I want each section (basic info, visibility, banner) to save independently with its own API endpoint,
So that auto-save is semantically correct, field-level validation is accurate, and the backend API reflects the frontend UX.

## Problem → Solution
**Current**: Frontend calls `updateBasicInfo`/`updateVisibility`/`updateBanner` but all hit the same generic `PATCH /admin/problem-lists/{id}`, losing semantic intent and field-level validation. DTOs have mismatched `@Size` constraints. The frontend sends `slug` and `authorId` fields that the backend does not recognise.

**Desired**: Each frontend section maps to a dedicated backend endpoint with its own DTO, consistent validation, and no phantom fields.

## Metadata
- **Complexity**: Medium
- **Source PRD**: `/home/davidhlp/project/UltiCode-Public-Next/docs/analysis/problem-lists-alignment-report.md`
- **PRD Phase**: standalone
- **Estimated Files**: 9

---

## UX Design

### Before
```
Frontend Section Save ──→ PATCH /admin/problem-lists/{id}
   BasicInfo    ─────────→ generic update (fields mixed with others)
   Visibility   ─────────→ generic update (fields mixed with others)
   Banner       ─────────→ generic update (fields mixed with others)
```

### After
```
Frontend Section Save ──→ Dedicated Endpoint
   BasicInfo    ─────────→ PATCH /admin/problem-lists/{id}/basic-info
   Visibility   ─────────→ PATCH /admin/problem-lists/{id}/visibility
   Banner       ─────────→ PATCH /admin/problem-lists/{id}/banner
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| BasicInfo save | `PATCH /{id}` with all fields | `PATCH /{id}/basic-info` with `{name, description}` | DTO now validates `@Size(max=500)` for description |
| Visibility save | `PATCH /{id}` with all fields | `PATCH /{id}/visibility` with `{isPublic, isFeatured}` | Isolated endpoint, no side effects on other fields |
| Banner save | `PATCH /{id}` with all fields | `PATCH /{id}/banner` with `{bannerTag, bannerIcon, bannerTheme, bannerOrder}` | Isolated endpoint, no side effects |
| Frontend `CreateProblemListDto` | Included `slug` and `authorId` | Removes `slug` and `authorId` | Backend never consumed these fields |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend-spring/.../admin/controller/AdminProblemListController.java` | all | Target controller — must follow existing annotation and rate-limit patterns |
| P0 | `backend-spring/.../admin/service/AdminProblemListService.java` | all | Admin service interface — add delegation methods here |
| P0 | `backend-spring/.../admin/service/impl/AdminProblemListServiceImpl.java` | all | Admin service impl — add bypass (no ownership check) delegation logic |
| P0 | `backend-spring/.../problemlist/service/ProblemListService.java` | 174-178 | Already has `updateBasicInfo`, `updateVisibility`, `updateBanner` |
| P0 | `backend-spring/.../problemlist/service/impl/ProblemListServiceImpl.java` | 210-272 | See how domain service implements fine-grained updates with ownership checks |
| P1 | `backend-spring/.../admin/controller/AdminForumController.java` | 60-102 | Pattern for fine-grained admin sub-endpoints (`/{id}/pin`, `/{id}/lock`) |
| P1 | `management/src/api/admin/problem-lists.ts` | all | Frontend API client — update endpoint paths |
| P2 | `backend-spring/.../problemlist/dto/UpdateBasicInfoDTO.java` | all | Existing DTO to reuse |
| P2 | `backend-spring/.../problemlist/dto/UpdateVisibilityDTO.java` | all | Existing DTO to reuse |
| P2 | `backend-spring/.../problemlist/dto/UpdateBannerDTO.java` | all | Existing DTO to reuse |
| P2 | `backend-spring/.../problemlist/dto/UpdateProblemListDTO.java` | all | Align description `@Size` with `UpdateBasicInfoDTO` |

## External Documentation

No external research needed — feature uses established internal patterns.

---

## Patterns to Mirror

### CONTROLLER_FINE_GRAINED_ENDPOINT
// SOURCE: `backend-spring/.../admin/controller/AdminForumController.java:60-69`
```java
@Operation(summary = "Pin post", description = "Pin a post to top")
@RateLimit(key = "admin:forum-pin", limit = 30, period = 60)
@PostMapping("/posts/{id}/pin")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<Void> pinPost(
        @Parameter(description = "Post ID")
        @PathVariable String id) {
    adminForumService.pinPost(id);
    return Result.success();
}
```

### CONTROLLER_PATCH_WITH_BODY
// SOURCE: `backend-spring/.../admin/controller/AdminProblemListController.java:57-66`
```java
@Operation(summary = "Update problem list", description = "Update an existing problem list")
@RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
@PatchMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<ProblemListSummaryVO> updateProblemList(
        @PathVariable String id,
        @Valid @RequestBody UpdateProblemListDTO dto,
        @RequestHeader(value = "X-User-Id", required = false) String userId) {
    return Result.success(adminProblemListService.updateProblemList(id, dto, userId));
}
```

### ADMIN_SERVICE_BYPASS_OWNERSHIP
// SOURCE: `backend-spring/.../admin/service/impl/AdminProblemListServiceImpl.java:104-154`
Admin service methods bypass the ownership check that exists in the domain `ProblemListService`. The admin service loads the entity directly via mapper and updates fields.

### AUDITED_ANNOTATION
// SOURCE: `backend-spring/.../admin/service/impl/AdminProblemListServiceImpl.java:102-104`
```java
@Audited(action = AuditActionUtil.UPDATE_PROBLEM_LIST, entityType = AuditActionUtil.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
```

### RESULT_RESPONSE
// SOURCE: `backend-spring/.../common/response/Result.java:55-67`
```java
public static <T> Result<T> success(T data) { ... }
public static <T> Result<T> success() { ... }
```

### ADMIN_SERVICE_INTERFACE_METHOD
// SOURCE: `backend-spring/.../admin/service/AdminProblemListService.java`
```java
PageResult<ProblemListSummaryVO> getProblemLists(AdminProblemListQueryDTO query);
ProblemListDetailVO getProblemList(String id);
ProblemListSummaryVO createProblemList(CreateProblemListDTO dto, String authorId);
ProblemListSummaryVO updateProblemList(String id, UpdateProblemListDTO dto, String userId);
void deleteProblemList(String id);
void updateListProblems(String id, UpdateProblemListProblemsDTO dto);
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `AdminProblemListController.java` | UPDATE | Add 3 fine-grained PATCH endpoints |
| `AdminProblemListService.java` | UPDATE | Add 3 service interface methods |
| `AdminProblemListServiceImpl.java` | UPDATE | Implement 3 methods with admin bypass + audit |
| `UpdateProblemListDTO.java` | UPDATE | Align `@Size(max=500)` for description |
| `UpdateBasicInfoDTO.java` | UPDATE | Remove `slug` field (entity has no slug column) |
| `problem-lists.ts` (frontend) | UPDATE | Update API paths to dedicated endpoints |
| `CreateProblemListDto` (frontend) | UPDATE | Remove `slug` and `authorId` phantom fields |
| `AdminProblemListControllerTest.java` | CREATE | Test coverage for new endpoints |

## NOT Building

- No new database migrations — entity schema unchanged
- No changes to `ProblemListService` domain layer — methods already exist
- No changes to `ProblemListsListView.vue` or detail page UI
- No new DTOs — reuse existing `UpdateBasicInfoDTO`, `UpdateVisibilityDTO`, `UpdateBannerDTO`
- No changes to `updateListProblems` endpoint — already aligned
- No changes to permission system — `MANAGE_PROBLEMS` role investigation is out of scope

---

## Step-by-Step Tasks

### Task 1: Remove phantom `slug` field from `UpdateBasicInfoDTO`
- **ACTION**: Remove the `slug` field from `UpdateBasicInfoDTO.java`
- **IMPLEMENT**: Delete `private String slug;` and its `@Size(max=100)` annotation. Remove import if unused.
- **MIRROR**: Follow immutability principle — do not leave dead fields in DTOs.
- **GOTCHA**: `UpdateBasicInfoDTO` is also referenced by `ProblemListService.updateBasicInfo()`. That service method does not set `slug` on the entity (confirmed by code read), so removal is safe.
- **VALIDATE**: `./mvnw compile` passes. No references to `getSlug()` / `setSlug()` remain.

### Task 2: Align `UpdateProblemListDTO` description length
- **ACTION**: Change `@Size(max=1000)` to `@Size(max=500)` for the `description` field in `UpdateProblemListDTO.java`
- **IMPLEMENT**: Edit line with `private String description;` so its `@Size` matches `UpdateBasicInfoDTO`.
- **MIRROR**: Keep validation consistent across all DTOs that touch the same column.
- **GOTCHA**: The database column length should already accommodate 500. No migration needed.
- **VALIDATE**: `./mvnw compile` passes.

### Task 3: Add fine-grained methods to `AdminProblemListService` interface
- **ACTION**: Add `updateBasicInfo`, `updateVisibility`, `updateBanner` method signatures
- **IMPLEMENT**:
```java
ProblemListSummaryVO updateBasicInfo(String id, String userId, UpdateBasicInfoDTO dto);
ProblemListSummaryVO updateVisibility(String id, String userId, UpdateVisibilityDTO dto);
ProblemListSummaryVO updateBanner(String id, String userId, UpdateBannerDTO dto);
```
- **MIRROR**: Match the parameter order and naming of existing methods (`id, dto, userId` order in controller, but service takes `id, userId, dto` — check existing `updateProblemList` service signature).
- **GOTCHA**: Existing service interface uses `updateProblemList(String id, UpdateProblemListDTO dto, String userId)`. Maintain the same parameter order style.
- **VALIDATE**: `./mvnw compile` passes. Interface compiles.

### Task 4: Implement fine-grained methods in `AdminProblemListServiceImpl`
- **ACTION**: Implement 3 methods that bypass ownership checks (admin privilege) and delegate directly to mapper updates, similar to existing `updateProblemList`
- **IMPLEMENT**:
```java
@Override
@Transactional(rollbackFor = Exception.class)
@Audited(action = AuditActionUtil.UPDATE_PROBLEM_LIST, entityType = AuditActionUtil.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
public ProblemListSummaryVO updateBasicInfo(String id, String userId, UpdateBasicInfoDTO dto) {
    ProblemList list = problemListMapper.selectById(id);
    if (list == null) {
        throw new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND);
    }
    // Admin bypass — no ownership check
    list.setName(dto.getName());
    list.setDescription(dto.getDescription());
    problemListMapper.updateById(list);
    return toSummaryVO(list);
}

@Override
@Transactional(rollbackFor = Exception.class)
@Audited(action = AuditActionUtil.UPDATE_PROBLEM_LIST, entityType = AuditActionUtil.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
public ProblemListSummaryVO updateVisibility(String id, String userId, UpdateVisibilityDTO dto) {
    ProblemList list = problemListMapper.selectById(id);
    if (list == null) {
        throw new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND);
    }
    if (dto.getIsPublic() != null) {
        list.setIsPublic(dto.getIsPublic());
    }
    if (dto.getIsFeatured() != null) {
        list.setIsFeatured(dto.getIsFeatured());
    }
    problemListMapper.updateById(list);
    return toSummaryVO(list);
}

@Override
@Transactional(rollbackFor = Exception.class)
@Audited(action = AuditActionUtil.UPDATE_PROBLEM_LIST, entityType = AuditActionUtil.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
public ProblemListSummaryVO updateBanner(String id, String userId, UpdateBannerDTO dto) {
    ProblemList list = problemListMapper.selectById(id);
    if (list == null) {
        throw new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND);
    }
    if (dto.getBannerTag() != null) {
        list.setBannerTag(dto.getBannerTag());
    }
    if (dto.getBannerIcon() != null) {
        list.setBannerIcon(dto.getBannerIcon());
    }
    if (dto.getBannerTheme() != null) {
        list.setBannerTheme(dto.getBannerTheme());
    }
    if (dto.getBannerOrder() != null) {
        list.setBannerOrder(dto.getBannerOrder());
    }
    problemListMapper.updateById(list);
    return toSummaryVO(list);
}
```
- **MIRROR**: Copy patterns from existing `updateProblemList` in the same file: null-check before set, `@Transactional`, `@Audited`, `BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND)`.
- **GOTCHA**: Do NOT call `ProblemListService.updateBasicInfo()` because that enforces ownership check. Admin must bypass.
- **GOTCHA**: Use `toSummaryVO(list)` private helper (already exists in the same class).
- **VALIDATE**: `./mvnw compile` passes.

### Task 5: Add 3 fine-grained PATCH endpoints to `AdminProblemListController`
- **ACTION**: Add `@PatchMapping("/{id}/basic-info")`, `@PatchMapping("/{id}/visibility")`, `@PatchMapping("/{id}/banner")`
- **IMPLEMENT**:
```java
@Operation(summary = "Update problem list basic info", description = "Update name and description")
@RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
@PatchMapping("/{id}/basic-info")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<ProblemListSummaryVO> updateBasicInfo(
        @PathVariable String id,
        @Valid @RequestBody UpdateBasicInfoDTO dto,
        @RequestHeader(value = "X-User-Id", required = false) String userId) {
    return Result.success(adminProblemListService.updateBasicInfo(id, userId, dto));
}

@Operation(summary = "Update problem list visibility", description = "Update public and featured status")
@RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
@PatchMapping("/{id}/visibility")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<ProblemListSummaryVO> updateVisibility(
        @PathVariable String id,
        @Valid @RequestBody UpdateVisibilityDTO dto,
        @RequestHeader(value = "X-User-Id", required = false) String userId) {
    return Result.success(adminProblemListService.updateVisibility(id, userId, dto));
}

@Operation(summary = "Update problem list banner", description = "Update banner settings")
@RateLimit(key = "admin:problem-list-update", limit = 30, period = 60)
@PatchMapping("/{id}/banner")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<ProblemListSummaryVO> updateBanner(
        @PathVariable String id,
        @Valid @RequestBody UpdateBannerDTO dto,
        @RequestHeader(value = "X-User-Id", required = false) String userId) {
    return Result.success(adminProblemListService.updateBanner(id, userId, dto));
}
```
- **MIRROR**: Copy exact annotation stack from existing `updateProblemList` method: `@Operation`, `@RateLimit`, `@PatchMapping`, `@PreAuthorize`, parameter style.
- **GOTCHA**: Use the same rate-limit key `admin:problem-list-update` so all update operations share the same bucket. Or use separate keys like `admin:problem-list-update-basic` if you want finer rate-limiting. **Decision**: keep same key for simplicity, matching existing pattern.
- **VALIDATE**: `./mvnw compile` passes. SpringDoc/OpenAPI generates correct spec.

### Task 6: Update frontend API client paths
- **ACTION**: Change `adminProblemListsApi.updateBasicInfo`, `updateVisibility`, `updateBanner` to call dedicated endpoints
- **IMPLEMENT**:
```typescript
async updateBasicInfo(id: string, data: UpdateBasicInfoDto): Promise<void> {
  await apiPatch(`/admin/problem-lists/${id}/basic-info`, data)
}

async updateVisibility(id: string, data: UpdateVisibilityDto): Promise<void> {
  await apiPatch(`/admin/problem-lists/${id}/visibility`, data)
}

async updateBanner(id: string, data: UpdateBannerDto): Promise<void> {
  await apiPatch(`/admin/problem-lists/${id}/banner`, data)
}
```
- **MIRROR**: Keep existing method signatures — only the URL path changes.
- **GOTCHA**: The `updateList` method (general PATCH) can be kept for backward compatibility or removed if unused. Check if any component calls `store.updateList` directly.
- **VALIDATE**: `pnpm type-check` passes in `management/`.

### Task 7: Remove phantom fields from frontend `CreateProblemListDto`
- **ACTION**: Remove `slug?: string` and `authorId?: string` from `CreateProblemListDto` interface
- **IMPLEMENT**: Edit `management/src/api/admin/problem-lists.ts` interface definition.
- **MIRROR**: Match backend DTO exactly.
- **GOTCHA**: Check if any component passes `slug` or `authorId` when calling `createList`. From code read, `BasicInfoSection.vue` only passes `name` and `description`.
- **VALIDATE**: `pnpm type-check` passes. `pnpm lint` passes.

### Task 8: Add controller tests for new endpoints
- **ACTION**: Create or update test class for `AdminProblemListController`
- **IMPLEMENT**: Use MockMvc + `@WebMvcTest(AdminProblemListController.class)` + `@MockBean AdminProblemListService`. Write tests for:
  - `PATCH /admin/problem-lists/{id}/basic-info` with valid body → 200 + Result
  - `PATCH /admin/problem-lists/{id}/visibility` with valid body → 200 + Result
  - `PATCH /admin/problem-lists/{id}/banner` with valid body → 200 + Result
  - Each endpoint with non-existent ID → 404 / error Result
  - Each endpoint with invalid body (e.g., name blank) → 400 / validation error
- **MIRROR**: Follow existing test patterns in the project. If no existing controller tests, follow standard Spring Boot MockMvc pattern with `@AutoConfigureMockMvc(addFilters = false)`.
- **GOTCHA**: `AdminProblemListController` uses `@PreAuthorize` which requires Spring Security context in tests. Use `@WithMockUser(roles = "ADMIN")` or disable security in MockMvc.
- **VALIDATE**: `./mvnw test -Dtest=AdminProblemListControllerTest` passes.

---

## Testing Strategy

### Unit Tests (Backend)

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `updateBasicInfo_success` | valid `UpdateBasicInfoDTO` | `Result.success(vo)`, entity updated | No |
| `updateBasicInfo_notFound` | non-existent ID | `BusinessException(PROBLEM_LIST_NOT_FOUND)` | Yes |
| `updateVisibility_success` | `isPublic=true` | `Result.success(vo)`, only isPublic changed | No |
| `updateBanner_success` | `bannerTag="Hot"` | `Result.success(vo)`, only banner fields changed | No |
| `updateBanner_nullFieldsIgnored` | empty `UpdateBannerDTO` | entity unchanged, still success | Yes |
| `updateBasicInfo_descriptionTooLong` | description 501 chars | 400 Bad Request (Bean Validation) | Yes |

### Edge Cases Checklist
- [ ] Non-existent problem list ID on all 3 new endpoints
- [ ] Blank name on `updateBasicInfo` (`@NotBlank` violation)
- [ ] Description exceeding 500 chars
- [ ] Null DTO fields should be ignored (no unintended overwrites)
- [ ] Admin without role should get 403

### Frontend Validation
- [ ] BasicInfo section auto-save still works
- [ ] Visibility switch toggle still works
- [ ] Banner theme select still works
- [ ] Create list flow still works (after removing slug/authorId)
- [ ] No TypeScript errors (`pnpm type-check`)

---

## Validation Commands

### Static Analysis (Backend)
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/backend-spring
./mvnw compile -q
```
EXPECT: Zero compilation errors

### Unit Tests (Backend)
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/backend-spring
./mvnw test -Dtest="AdminProblemListControllerTest,AdminProblemListServiceImplTest"
```
EXPECT: All tests pass

### Full Backend Test Suite
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/backend-spring
./mvnw test -q
```
EXPECT: No regressions

### Frontend Type Check
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management
pnpm type-check
```
EXPECT: Zero type errors

### Frontend Lint
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management
pnpm lint
```
EXPECT: Zero lint errors

### Integration Test (Manual)
- [ ] Start backend (`pm2 restart ulticode-9001`)
- [ ] Start management frontend (`pm2 restart ulticode-9003`)
- [ ] Navigate to `/problem-lists`
- [ ] Edit an existing list
- [ ] Change Basic Info → verify `PATCH /{id}/basic-info` called in Network tab
- [ ] Toggle Visibility → verify `PATCH /{id}/visibility` called
- [ ] Change Banner → verify `PATCH /{id}/banner` called
- [ ] Verify no `PATCH /{id}` (generic) is called for section saves

---

## Acceptance Criteria
- [ ] All 3 new PATCH endpoints exposed and callable
- [ ] Each endpoint uses its own dedicated DTO with correct field-level validation
- [ ] `UpdateProblemListDTO.description` `@Size` aligned to 500
- [ ] `UpdateBasicInfoDTO.slug` removed
- [ ] Frontend API client calls dedicated endpoints
- [ ] Frontend `CreateProblemListDto` no longer includes `slug` or `authorId`
- [ ] Admin service impl bypasses ownership checks (consistent with existing admin behavior)
- [ ] `@Audited` annotation present on new service methods
- [ ] Controller tests written and passing
- [ ] `./mvnw test` passes with no regressions
- [ ] `pnpm type-check` passes in management

## Completion Checklist
- [ ] Code follows discovered patterns (AdminForumController sub-endpoint style)
- [ ] Error handling matches codebase style (`BusinessException` + `ErrorCode`)
- [ ] Logging follows codebase conventions (Slf4j + Lombok)
- [ ] Tests follow test patterns (MockMvc + MockBean)
- [ ] No hardcoded values
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Removing `slug` from `UpdateBasicInfoDTO` breaks user-facing API if external consumers use it | Low | Medium | `slug` was never persisted to DB (entity has no slug column), so external consumers already got no effect. Safe to remove. |
| `ProblemListService` ownership check vs admin bypass confusion | Medium | Medium | Explicitly document in service impl that admin methods bypass ownership. Do NOT delegate to domain service methods that enforce ownership. |
| Frontend `updateList` store method becomes orphaned | Low | Low | Check if any component calls `store.updateList`. If unused, remove. If used elsewhere, keep for backward compatibility. |
| Rate limit bucket sharing causes unintended throttling | Low | Low | All update endpoints share `admin:problem-list-update` key (30/60s), same as before. No behavior change. |

## Notes
- The domain `ProblemListServiceImpl` already has `updateBasicInfo`, `updateVisibility`, `updateBanner` methods that enforce `list.getAuthorId().equals(userId)`. These are used by the **user-facing** API (console frontend). The **admin** API must bypass this check. Therefore, `AdminProblemListServiceImpl` must NOT delegate to `ProblemListService` for these operations; it must use `problemListMapper` directly.
- The existing generic `PATCH /{id}` endpoint should be kept for backward compatibility until confirmed unused. Mark as `@Deprecated` if desired.
- `bannerIcon` is included in `UpdateBannerDTO` but the frontend `BannerSection.vue` does not expose an icon input field. This is fine — the field is optional and the DTO can stay as-is.
