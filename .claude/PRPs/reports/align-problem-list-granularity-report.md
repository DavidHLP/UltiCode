# Implementation Report: Align Problem-List Frontend-Backend Granularity

## Summary
Added three fine-grained PATCH endpoints to `AdminProblemListController` to match the frontend's section-level auto-save UX. Fixed DTO validation inconsistencies and removed unused/phantom fields (`slug`, `authorId`). Delegated to already-existing fine-grained patterns in the codebase.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Medium | Medium |
| Confidence | High | High |
| Files Changed | 9 | 9 |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Remove phantom `slug` from `UpdateBasicInfoDTO` | Complete | Safe — domain service never used `getSlug()` |
| 2 | Align `UpdateProblemListDTO` description `@Size` | Complete | 1000 -> 500 |
| 3 | Add fine-grained methods to `AdminProblemListService` | Complete | 3 new signatures added |
| 4 | Implement fine-grained methods in `AdminProblemListServiceImpl` | Complete | Admin bypass + `@Audited` + `AuditContext` |
| 5 | Add 3 PATCH endpoints to `AdminProblemListController` | Complete | `/{id}/basic-info`, `/{id}/visibility`, `/{id}/banner` |
| 6 | Update frontend API client paths | Complete | URLs changed to dedicated endpoints |
| 7 | Remove phantom fields from frontend DTOs | Complete | `slug` and `authorId` removed from `CreateProblemListDto`, `UpdateProblemListDto`, `UpdateBasicInfoDto` |
| 8 | Add controller tests | Complete | 8 tests, all passing |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `./mvnw compile` zero errors |
| Unit Tests (Backend) | Pass | 8 new controller tests + 580 existing tests, zero failures |
| Build | Pass | `./mvnw test` BUILD SUCCESS |
| Integration | N/A | Manual integration test recommended before merge |
| Edge Cases | Pass | Not-found (404), blank name (400), null fields ignored |

## Files Changed

| File | Action | Lines |
|---|---|---|
| `AdminProblemListController.java` | UPDATED | +40 |
| `AdminProblemListService.java` | UPDATED | +30 |
| `AdminProblemListServiceImpl.java` | UPDATED | +95 |
| `UpdateProblemListDTO.java` | UPDATED | 1 changed |
| `UpdateBasicInfoDTO.java` | UPDATED | -3 |
| `problem-lists.ts` (frontend) | UPDATED | +9 / -11 |
| `AdminProblemListControllerTest.java` | CREATED | +222 |

## Deviations from Plan
None — implemented exactly as planned.

## Issues Encountered
None.

## Tests Written

| Test File | Tests | Coverage |
|---|---|---|
| `AdminProblemListControllerTest.java` | 8 tests | basic-info (3), visibility (2), banner (3) |

## Next Steps
- [ ] Code review via `code-reviewer` agent
- [ ] Create PR via `/prp-pr`
