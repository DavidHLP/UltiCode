# Local Code Review: Fix AdminUser Stats and Permissions Population

**Reviewed**: 2026-05-19
**Branch**: main (uncommitted changes)
**Decision**: APPROVE with comments

## Summary
Fixes backend `AdminUserServiceImpl.toVO()` to populate previously-empty `stats` and `permissions` fields by querying `SubmissionMapper`, `SolutionMapper`, `PermissionService`, and `RolePermissionMapper`. All security checks pass. One performance concern (N+1 queries) and one maintainability concern (method length) noted.

## Findings

### CRITICAL
None

### HIGH
**N+1 Query in `getUsers()` Pagination**
- **File**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java:101-103`
- **Issue**: `getUsers()` calls `toVO()` for every record in the page. After this change, `toVO()` executes 4 additional database queries per user (`countByUserId`, `countAcceptedProblemsByUserId`, `countByUserId` for solutions, `calculateStreak`, plus permission queries). For a page size of 50, this becomes 200+ extra queries.
- **Suggested Fix**: Extract stats/permissions population into a separate method and call it only from `getUserById()`. List views rarely need full stats/permissions. If list view needs them, use a batched query (e.g., `IN` clause) or a JOIN.

### MEDIUM
**`toVO()` Exceeds 50 Lines**
- **File**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java:424-486`
- **Issue**: The method is ~62 lines. It now handles basic field mapping, stats population, role-based permissions, and direct permissions.
- **Suggested Fix**: Split into `mapBasicFields()`, `buildStats()`, and `buildPermissions()` private methods.

### LOW
None

## Validation Results

| Check | Result |
|---|---|
| Static Analysis | Pass |
| Unit Tests | Pass (188 tests, 0 failures) |
| Integration Tests | Pass (`./mvnw verify -Pci`) |
| Security Scan | Pass |

## Files Reviewed

| File | Action | Lines |
|---|---|---|
| `backend-spring/src/main/java/com/ulticode/modules/solution/mapper/SolutionMapper.java` | Modified | +9 |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java` | Modified | +35 |
| `backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImplTest.java` | Created | +138 |

## Security Checklist

- [x] No hardcoded credentials
- [x] Parameterized queries (MyBatis `#{...}` and `LambdaQueryWrapper`)
- [x] No XSS vectors (backend-only change)
- [x] Input validation preserved
- [x] Error messages safe (generic `BusinessException`)

## Notes

- The `is_deleted = false` filter in `SolutionMapper.countByUserId` is correct because `@TableLogic` does not automatically apply to native `@Select` queries.
- Tests cover stats population, null defaults, permissions merge, and user-not-found exception. Good null-safety coverage.
- The `countAcceptedProblemsByUserId` returns distinct accepted problem count (not total accepted submissions), which matches frontend expectations per earlier analysis.
