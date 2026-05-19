# Plan: Fix AdminUser Stats and Permissions Population

## Summary
完善 `/users` 页面前后端颗粒度对齐，修复后端 `AdminUserServiceImpl.toVO()` 中 `stats`（用户统计）和 `permissions`（权限列表）字段未被填充的问题。确保前端 `UserDetailDrawer.vue` 展示的数据能正确从后端获取。

## User Story
As an admin user viewing the user management dashboard,
I want to see accurate user statistics (submissions, solutions, streak) and permissions in the user detail drawer,
So that I can make informed decisions about user accounts.

## Problem → Solution
Current state: `AdminUserServiceImpl.toVO()` returns null for `stats` and `permissions` fields, leaving the frontend detail drawer empty for those sections.

Desired state: `toVO()` queries the actual database to populate `stats` (total submissions, accepted submissions, total solutions, streak) and `permissions` (role-based + direct user permissions).

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: standalone
- **Estimated Files**: 5

---

## UX Design

### Before
N/A — internal change with no user-facing UI change. The frontend already renders the stats section conditionally (`v-if="entity.stats"`), so the only visible change is that the section will now appear populated instead of hidden.

### After
N/A — internal change.

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| User detail drawer stats section | Hidden (stats is null) | Displays total submissions, accepted submissions, total solutions, streak, acceptance rate | Data now populated from backend |
| User detail drawer permissions section | Hidden (permissions is null) | Displays merged role + direct permissions | Data now populated from backend |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java` | 414-435 | Core `toVO()` method to modify |
| P0 (critical) | `backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminUserVO.java` | 54-76 | Stats and permissions DTO structure |
| P1 (important) | `backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java` | 86-99, 165-186 | Existing count queries for submissions and streak |
| P1 (important) | `backend-spring/src/main/java/com/ulticode/modules/permission/service/PermissionService.java` | 41-80 | Permission lookup methods |
| P2 (reference) | `backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImplTest.java` | 1-211 | Test pattern to mirror |

---

## External Documentation

No external research needed — feature uses established internal patterns.

---

## Patterns to Mirror

### SERVICE_INJECTION_PATTERN
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java:41-45
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditHelper auditHelper;
```

### ERROR_HANDLING_PATTERN
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java:99-105
```java
User user = userMapper.selectById(id);
if (user == null) {
    throw new BusinessException(ErrorCode.USER_NOT_FOUND);
}
```

### MAPPER_COUNT_QUERY_PATTERN
// SOURCE: backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java:89,98
```java
@Select("SELECT COUNT(DISTINCT problem_id) FROM submissions WHERE user_id = #{userId} AND status = 'Accepted'")
Long countAcceptedProblemsByUserId(@Param("userId") String userId);

@Select("SELECT COUNT(*) FROM submissions WHERE user_id = #{userId}")
Long countByUserId(@Param("userId") String userId);
```

### TEST_STRUCTURE
// SOURCE: backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImplTest.java:26-48
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSubmissionServiceImpl")
class AdminSubmissionServiceImplTest {
    @Mock
    private SubmissionMapper submissionMapper;

    private AdminSubmissionServiceImpl adminSubmissionService;

    @BeforeEach
    void setUp() {
        adminSubmissionService = new AdminSubmissionServiceImpl(submissionMapper, ...);
    }
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `backend-spring/src/main/java/com/ulticode/modules/solution/mapper/SolutionMapper.java` | UPDATE | Add `countByUserId` query for total solutions count |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java` | UPDATE | Inject new dependencies and populate `stats` + `permissions` in `toVO()` |
| `backend-spring/src/test/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImplTest.java` | CREATE | New unit tests for stats/permissions population logic |

## NOT Building

- Do NOT create a new endpoint — existing `/admin/users/{id}` already returns `AdminUserVO`
- Do NOT modify frontend code — frontend already handles the data structure correctly
- Do NOT modify `AdminUserVO` DTO — fields already exist and match frontend expectations
- Do NOT implement permission expiration logic — `UserPermission` entity has no `expiresAt` field; set to null
- Do NOT add caching for stats/permissions — out of scope for this fix

---

## Step-by-Step Tasks

### Task 1: Add Solution Count Query
- **ACTION**: Add a count-by-user query to `SolutionMapper`
- **IMPLEMENT**: Add `@Select("SELECT COUNT(*) FROM solutions WHERE user_id = #{userId} AND is_deleted = false") Long countByUserId(@Param("userId") String userId);` to `SolutionMapper.java`
- **MIRROR**: MAPPER_COUNT_QUERY_PATTERN from SubmissionMapper
- **IMPORTS**: `org.apache.ibatis.annotations.Param`, `org.apache.ibatis.annotations.Select`
- **GOTCHA**: Must filter `is_deleted = false` because `Solution` entity uses `@TableLogic` soft delete
- **VALIDATE**: `./mvnw compile` passes

### Task 2: Inject Dependencies into AdminUserServiceImpl
- **ACTION**: Add `SubmissionMapper`, `SolutionMapper`, and `PermissionService` as constructor-injected dependencies
- **IMPLEMENT**: Add three new `private final` fields to `AdminUserServiceImpl` and ensure `@RequiredArgsConstructor` picks them up
- **MIRROR**: SERVICE_INJECTION_PATTERN
- **IMPORTS**:
  - `com.ulticode.modules.submission.mapper.SubmissionMapper`
  - `com.ulticode.modules.solution.mapper.SolutionMapper`
  - `com.ulticode.modules.permission.service.PermissionService`
- **GOTCHA**: Do NOT use `@Autowired` field injection; the project uses constructor injection via `@RequiredArgsConstructor`
- **VALIDATE**: `./mvnw compile` passes

### Task 3: Populate Stats in toVO()
- **ACTION**: Modify `toVO()` to query and populate the `stats` field
- **IMPLEMENT**: After setting basic fields on `AdminUserVO`, query:
  1. `submissionMapper.countByUserId(user.getId())` → `totalSubmissions` (default 0 if null)
  2. `submissionMapper.countAcceptedProblemsByUserId(user.getId())` → `acceptedSubmissions` (default 0 if null)
  3. `solutionMapper.countByUserId(user.getId())` → `totalSolutions` (default 0 if null)
  4. `submissionMapper.calculateStreak(user.getId())` → `streak` (default 0 if null)
  5. Build `AdminUserVO.UserStatsInfo` and set it on the VO
- **MIRROR**: The existing `UserServiceImpl.getUserStatsById()` pattern at `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java:203-284`
- **IMPORTS**: `com.ulticode.modules.admin.dto.AdminUserVO.UserStatsInfo`
- **GOTCHA**: `countAcceptedProblemsByUserId` counts DISTINCT problems, not total accepted submissions. For `acceptedSubmissions`, the frontend label says "Accepted" which aligns with distinct solved problems, not total accepted submission count. Keep this semantic.
- **VALIDATE**: `./mvnw compile` passes; run existing integration tests

### Task 4: Populate Permissions in toVO()
- **ACTION**: Modify `toVO()` to query and populate the `permissions` field
- **IMPLEMENT**:
  1. Call `permissionService.getUserPermissions(userId)` to get direct user permissions
  2. Call `rolePermissionMapper.selectList(...)` to get role-based permissions for `user.getRole()`
  3. Map both lists to `AdminUserVO.PermissionInfo` objects, setting `source` as `"direct"` for user permissions and `"role"` for role permissions
  4. Set `expiresAt` to null (no expiration field exists in the schema)
  5. Combine both lists and set on VO
- **MIRROR**: PermissionService.getUserPermissionStrings() pattern
- **IMPORTS**:
  - `com.ulticode.modules.permission.entity.UserPermission`
  - `com.ulticode.modules.permission.entity.RolePermission`
  - `com.ulticode.modules.permission.mapper.RolePermissionMapper`
  - `com.ulticode.modules.admin.dto.AdminUserVO.PermissionInfo`
- **GOTCHA**: `RolePermissionMapper` may not be available in `AdminUserServiceImpl` yet; inject it alongside the other new dependencies
- **VALIDATE**: `./mvnw compile` passes

### Task 5: Write Unit Tests
- **ACTION**: Create `AdminUserServiceImplTest.java` covering stats and permissions population
- **IMPLEMENT**:
  - Mock all dependencies (`UserMapper`, `PasswordEncoder`, `AuditHelper`, `SubmissionMapper`, `SolutionMapper`, `PermissionService`, `RolePermissionMapper`)
  - Test `getUserById` returns VO with stats populated correctly
  - Test `getUserById` returns VO with permissions populated correctly
  - Test null safety when mappers return null
- **MIRROR**: TEST_STRUCTURE from `AdminSubmissionServiceImplTest`
- **IMPORTS**: Standard JUnit 5 + Mockito + AssertJ imports
- **GOTCHA**: Ensure tests are placed in the correct package-mirrored directory under `src/test/java`
- **VALIDATE**: `./mvnw test -Dtest=AdminUserServiceImplTest` passes

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `getUserById_populatesStatsCorrectly` | User with 10 submissions, 5 accepted, 3 solutions, streak=7 | VO.stats has totalSubmissions=10, acceptedSubmissions=5, totalSolutions=3, streak=7 | No |
| `getUserById_populatesPermissionsCorrectly` | User with role=ADMIN and 2 direct permissions | VO.permissions contains role perms (source=role) + direct perms (source=direct) | No |
| `getUserById_nullMapperReturns_defaultsToZero` | User with null returns from all count mappers | VO.stats has all zeros | Yes |
| `getUserById_userNotFound_throwsBusinessException` | Non-existent user ID | BusinessException with ErrorCode.USER_NOT_FOUND | Yes (existing behavior) |

### Edge Cases Checklist
- [ ] Null user entity passed to `toVO()` — returns null (existing behavior)
- [ ] Mapper returns null for counts — default to 0
- [ ] User has no permissions — empty permissions list
- [ ] User has no role — no role permissions added
- [ ] Streak calculation returns null — default to 0

---

## Validation Commands

### Static Analysis
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw compile
```
EXPECT: Zero compilation errors

### Unit Tests
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw test -Dtest=AdminUserServiceImplTest
```
EXPECT: All tests pass

### Full Test Suite
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw test
```
EXPECT: No regressions

### Integration Tests
```bash
cd /home/david/project/UltiCode-Public-Next/backend-spring && ./mvnw verify -Pci
```
EXPECT: All integration tests pass

### Manual Validation
- [ ] Start backend server (`pm2 restart ulticode-9001`)
- [ ] Start management frontend (`pm2 restart ulticode-9003`)
- [ ] Log in as admin, navigate to `/users`
- [ ] Click on a user row to open `UserDetailDrawer`
- [ ] Verify "performance_stats" section shows total submissions, accepted, solutions, streak
- [ ] Verify acceptance rate progress bar calculates correctly

---

## Acceptance Criteria
- [ ] All tasks completed
- [ ] All validation commands pass
- [ ] Tests written and passing
- [ ] No type errors
- [ ] No lint errors
- [ ] User detail drawer displays populated stats and permissions

## Completion Checklist
- [ ] Code follows discovered patterns (constructor injection, `@RequiredArgsConstructor`)
- [ ] Error handling matches codebase style (null checks, default values)
- [ ] Logging follows codebase conventions (log.info for significant operations)
- [ ] Tests follow test patterns (`@ExtendWith(MockitoExtension.class)`, `@DisplayName`)
- [ ] No hardcoded values (use mapper methods, not literal counts)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Adding too many DB queries per `toVO()` call causes N+1 in `getUsers()` list endpoint | Medium | High | Stats/permissions only needed for detail view; consider lazy loading or a separate endpoint if performance degrades |
| `RolePermissionMapper` table is empty / unused | Low | Low | If no role permissions exist, the list will simply contain direct user permissions only |
| Streak SQL query is slow for large datasets | Low | Medium | The existing query is already in use in `UserServiceImpl`; monitor if needed |

## Notes
- The `getUsers()` paginated list calls `toVO()` for every user record. Populating stats/permissions for every list item may cause performance issues. If observed, consider extracting stats/permissions population into a separate method called only from `getUserById()` rather than the shared `toVO()`.
- `UserPermission` entity has no `expiresAt` field, so `PermissionInfo.expiresAt` will always be null.
- `totalSolutions` counts all non-deleted solutions for the user. If soft-deleted solutions should be excluded, the `@TableLogic` on `Solution` entity ensures MyBatis-Plus handles this automatically for `BaseMapper` methods, but native `@Select` queries require explicit `is_deleted = false`.
