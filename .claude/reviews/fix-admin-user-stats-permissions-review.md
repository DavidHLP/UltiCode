# Local Code Review: Fix AdminUser Stats and Permissions Population

**Reviewed**: 2026-05-19
**Branch**: main (uncommitted changes)
**Decision**: APPROVE with comments

## Summary
Fixes backend `AdminUserServiceImpl.toVO()` to populate previously-empty `stats` and `permissions` fields by querying `SubmissionMapper`, `SolutionMapper`, `PermissionService`, and `RolePermissionMapper`. All security checks pass. One performance concern (N+1 queries) and one maintainability concern (method length) noted.

**Post-Review Fix Applied**: Extracted `populateStats()` and `populatePermissions()` so `getUsers()` no longer triggers N+1 queries. `toVO()` now handles basic field mapping only (~25 lines). All 572 unit tests pass.

## Findings

### CRITICAL
None

### HIGH
**N+1 Query in `getUsers()` Pagination** — *FIXED*
- **File**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java:101-103`
- **Issue**: `getUsers()` called `toVO()` for every record, which executed 4 additional database queries per user.
- **Fix**: Extracted `populateStats()` and `populatePermissions()` private methods. `getUsers()` calls `toVO()` only (basic mapping). `getUserById()` calls `toVO()` + the two populate methods. Eliminates N+1 in list view while preserving full detail in single-user view.

### MEDIUM
**`toVO()` Exceeds 50 Lines** — *FIXED*
- **File**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java:424-486`
- **Issue**: The method was ~62 lines, handling basic mapping, stats, and permissions.
- **Fix**: Split into `toVO()` (~25 lines, basic mapping), `populateStats()` (~12 lines), and `populatePermissions()` (~20 lines).

### LOW
None

## Validation Results

| Check | Result |
|---|---|
| Static Analysis | Pass |
| Unit Tests | Pass (572 tests, 0 failures) |
| Integration Tests | Pass (`./mvnw verify -Pci`) |
| Security Scan | Pass |

## Files Reviewed

| File | Action | Lines |
|---|---|---|
| `backend-spring/src/main/java/com/ulticode/modules/solution/mapper/SolutionMapper.java` | Modified | +9 |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java` | Modified | +35/-15 |
| `backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImplTest.java` | Modified | +18 |

## Security Checklist

- [x] No hardcoded credentials
- [x] Parameterized queries (MyBatis `#{...}` and `LambdaQueryWrapper`)
- [x] No XSS vectors (backend-only change)
- [x] Input validation preserved
- [x] Error messages safe (generic `BusinessException`)

## Notes

- The `is_deleted = false` filter in `SolutionMapper.countByUserId` is correct because `@TableLogic` does not automatically apply to native `@Select` queries.
- Tests cover stats population, null defaults, permissions merge, user-not-found exception, and list-view N+1 prevention. Good null-safety coverage.
- The `countAcceptedProblemsByUserId` returns distinct accepted problem count (not total accepted submissions), which matches frontend expectations per earlier analysis.
