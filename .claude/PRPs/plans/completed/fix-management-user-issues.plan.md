# Plan: Fix Management User Dialog Issues

## Summary
Fix console errors and API failures in the management frontend user management module. Issues include missing i18n keys, DialogContent accessibility warnings, missing backend CRUD endpoints (create/update/delete user), and reset password validation failures.

## User Story
As an admin user,
I want to create, edit, and delete users without console errors or API failures,
So that I can manage users reliably through the management dashboard.

## Problem → Solution
Multiple console warnings (missing i18n keys, Dialog a11y) and API errors (405 PATCH, validation failed) → Add missing translations, DialogDescription components, backend CRUD endpoints, and frontend validation.

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 8

---

## UX Design

### Before
- Dialogs show i18n key names instead of translated text
- Console warnings about missing Dialog descriptions
- Edit user fails with "Method not allowed: PATCH"
- Reset password fails with "Validation failed" for short passwords
- Create/delete user endpoints missing on backend

### After
- All dialog text properly translated
- No console warnings
- All CRUD operations work end-to-end
- Clear validation feedback before submitting

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `management/src/views/users/UserEditDialog.vue` | all | Dialog missing Description + i18n keys |
| P0 | `management/src/views/users/UserResetPasswordDialog.vue` | all | Dialog missing Description + validation |
| P0 | `management/src/views/users/UserCreateDialog.vue` | all | Dialog missing Description |
| P0 | `management/src/i18n/locales/zh-CN/modules/users.ts` | all | Missing i18n keys |
| P0 | `management/src/i18n/locales/en-US/modules/users.ts` | all | Missing i18n keys |
| P0 | `backend-spring/.../admin/controller/AdminUserController.java` | all | Missing CRUD endpoints |
| P0 | `backend-spring/.../admin/service/AdminUserService.java` | all | Missing service methods |
| P0 | `backend-spring/.../admin/service/impl/AdminUserServiceImpl.java` | all | Missing implementations |
| P1 | `backend-spring/.../admin/dto/AdminUpdateUserDTO.java` | all | DTO for updates |
| P1 | `backend-spring/.../admin/dto/AdminCreateUserDTO.java` | all | DTO for creation |
| P1 | `management/src/components/ui/dialog/DialogContent.vue` | all | Understand a11y requirement |

---

## Patterns to Mirror

### DIALOG_DESCRIPTION
```vue
// SOURCE: management/src/views/moderation/components/BatchActionDialog.vue
<DialogDescription class="font-data text-xs text-[var(--silver-400)]">
  {{ t('moderation.batch.description') }}
</DialogDescription>
```
Use `DialogDescription` inside `DialogHeader` after `DialogTitle`. Style with `sr-only` if visually hidden.

### BACKEND_ENDPOINT_PATTERN
```java
// SOURCE: backend-spring/.../admin/controller/AdminUserController.java:65-74
@Operation(summary = "Reset user password", description = "Reset a user's password")
@RateLimit(key = "admin:user-reset-password", limit = 30, period = 60)
@PostMapping("/{id}/reset-password")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public Result<Void> resetPassword(
        @PathVariable String id,
        @Valid @RequestBody ResetPasswordRequest request) {
    adminUserService.resetPassword(id, request.getPassword());
    return Result.success();
}
```

### SERVICE_IMPL_PATTERN
```java
// SOURCE: backend-spring/.../admin/service/impl/AdminUserServiceImpl.java:171-191
@Override
@Transactional
@Audited(action = AuditActionUtil.RESET_PASSWORD, entityType = AuditActionUtil.ENTITY_USER, userIdFrom = "id")
public void resetPassword(String id, String newPassword) {
    User user = userMapper.selectById(id);
    if (user == null) {
        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    // ... business logic
}
```

### I18N_MODULE_PATTERN
```typescript
// SOURCE: management/src/i18n/locales/zh-CN/modules/users.ts
export default {
  title: '用户管理',
  // ... nested keys
} as const
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `management/src/i18n/locales/zh-CN/modules/users.ts` | UPDATE | Add missing i18n keys |
| `management/src/i18n/locales/en-US/modules/users.ts` | UPDATE | Add missing i18n keys |
| `management/src/views/users/UserEditDialog.vue` | UPDATE | Add DialogDescription import + usage |
| `management/src/views/users/UserResetPasswordDialog.vue` | UPDATE | Add DialogDescription + password validation |
| `management/src/views/users/UserCreateDialog.vue` | UPDATE | Add DialogDescription import + usage |
| `backend-spring/.../admin/service/AdminUserService.java` | UPDATE | Add createUser, updateUser, deleteUser signatures |
| `backend-spring/.../admin/service/impl/AdminUserServiceImpl.java` | UPDATE | Implement createUser, updateUser, deleteUser |
| `backend-spring/.../admin/controller/AdminUserController.java` | UPDATE | Add POST, PATCH, DELETE endpoints |
| `backend-spring/.../admin/dto/AdminUpdateUserDTO.java` | UPDATE | Add username field |

## NOT Building
- No changes to user-facing console frontend
- No database schema changes
- No changes to existing ban/unban/bulk operations
- No changes to authentication/authorization logic beyond adding PreAuthorize

---

## Step-by-Step Tasks

### Task 1: Add Missing i18n Keys
- **ACTION**: Add missing translation keys to both zh-CN and en-US users modules
- **IMPLEMENT**: Add `editDescription`, `editUser` to root; add `saveChanges`, `saving` under `form`
- **MIRROR**: Follow existing nested structure in users.ts
- **IMPORTS**: N/A
- **GOTCHA**: Must add to BOTH locale files
- **VALIDATE**: Search for keys in both files, ensure no duplicates

### Task 2: Fix Dialog Accessibility Warnings
- **ACTION**: Import and use `DialogDescription` in all three user dialog components
- **IMPLEMENT**: In each `<DialogHeader>` after `<DialogTitle>`, add `<DialogDescription class="sr-only">` with appropriate text (or visible description matching the existing `<p class="terminal-comment">`)
- **MIRROR**: management/src/views/moderation/components/BatchActionDialog.vue pattern
- **IMPORTS**: Add `DialogDescription` to the dialog component imports
- **GOTCHA**: Must be inside DialogContent, typically inside DialogHeader. Use `class="sr-only"` to hide visually if design doesn't want visible description
- **VALIDATE**: Component renders without "Missing Description" warning

### Task 3: Add Backend Service Interface Methods
- **ACTION**: Add createUser, updateUser, deleteUser to AdminUserService interface
- **IMPLEMENT**:
  - `AdminUserVO createUser(AdminCreateUserDTO dto)`
  - `AdminUserVO updateUser(String id, AdminUpdateUserDTO dto)`
  - `void deleteUser(String id)`
- **MIRROR**: Existing method signatures in AdminUserService.java
- **IMPORTS**: Add AdminCreateUserDTO, AdminUpdateUserDTO imports
- **GOTCHA**: N/A
- **VALIDATE**: Interface compiles

### Task 4: Implement Backend Service Methods
- **ACTION**: Implement the three methods in AdminUserServiceImpl
- **IMPLEMENT**:
  - **createUser**: Validate username uniqueness, encode password if provided, insert via userMapper, return toVO
  - **updateUser**: Fetch user, throw if not found, use LambdaUpdateWrapper to set provided fields, update via userMapper, return toVO
  - **deleteUser**: Fetch user, throw if not found, delete via userMapper, log action
- **MIRROR**: Existing resetPassword/banUser patterns in AdminUserServiceImpl.java
- **IMPORTS**: N/A
- **GOTCHA**: For updateUser, need to check and encode password if AdminUpdateUserDTO gets a password field (it doesn't currently). Only update non-null fields.
- **VALIDATE**: `./mvnw compile` passes

### Task 5: Add Backend Controller Endpoints
- **ACTION**: Add POST /admin/users, PATCH /admin/users/{id}, DELETE /admin/users/{id} to AdminUserController
- **IMPLEMENT**:
  - `@PostMapping` for createUser with `@Valid @RequestBody AdminCreateUserDTO`
  - `@PatchMapping("/{id}")` for updateUser with `@Valid @RequestBody AdminUpdateUserDTO`
  - `@DeleteMapping("/{id}")` for deleteUser
  - All with `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")`
  - Add appropriate `@RateLimit` annotations
- **MIRROR**: Existing endpoint patterns in AdminUserController.java
- **IMPORTS**: Add AdminCreateUserDTO, AdminUpdateUserDTO imports
- **GOTCHA**: PATCH endpoint is what frontend expects (`apiPatch`). Return `Result.success(...)` consistently.
- **VALIDATE**: `./mvnw compile` passes

### Task 6: Add username to AdminUpdateUserDTO
- **ACTION**: Add username field to AdminUpdateUserDTO
- **IMPLEMENT**: Add `@Size(max = 50) private String username;` to match frontend expectations
- **MIRROR**: Existing field patterns in AdminUpdateUserDTO.java
- **IMPORTS**: N/A
- **GOTCHA**: Frontend sends username in UpdateUserDto. Without this field, it's silently ignored.
- **VALIDATE**: DTO compiles

### Task 7: Add Frontend Password Validation
- **ACTION**: Add client-side password length validation to UserResetPasswordDialog
- **IMPLEMENT**: Before calling `usersStore.resetPassword`, check `newPassword.value.length >= 8`. If not, show toast error with i18n key.
- **MIRROR**: Existing toast patterns in the component
- **IMPORTS**: N/A
- **GOTCHA**: Backend requires 8-128 chars. Should match backend `@Size(min = 8)`
- **VALIDATE**: Enter short password, verify toast appears and API is not called

---

## Testing Strategy

### Unit Tests
N/A — no existing unit tests for these specific components.

### Manual Validation
- [ ] Open UserEditDialog → no console i18n warnings
- [ ] Open UserCreateDialog → no console a11y warnings
- [ ] Open UserResetPasswordDialog → no console warnings
- [ ] Edit user → PATCH succeeds, user updated
- [ ] Create user → POST succeeds, user created
- [ ] Delete user → DELETE succeeds, user removed
- [ ] Reset password with < 8 chars → validation toast, no API call
- [ ] Reset password with >= 8 chars → succeeds

---

## Validation Commands

### Frontend Type Check
```bash
cd management && pnpm type-check
```
EXPECT: Zero type errors

### Backend Compile
```bash
cd backend-spring && ./mvnw compile -q
```
EXPECT: Build success

### Frontend Lint
```bash
cd management && pnpm lint
```
EXPECT: No lint errors in changed files

---

## Acceptance Criteria
- [ ] All i18n keys present in both locales
- [ ] No DialogContent a11y warnings
- [ ] Backend create/update/delete endpoints exist and compile
- [ ] Frontend password validation prevents short passwords
- [ ] All validation commands pass

## Completion Checklist
- [ ] Code follows discovered patterns
- [ ] Error handling matches codebase style
- [ ] No hardcoded values
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Username uniqueness check missing in createUser | Low | Medium | Add explicit check before insert |
| Partial update logic incorrect | Low | Medium | Only set non-null fields in wrapper |

## Notes
- The backend DTOs `AdminCreateUserDTO` and `AdminUpdateUserDTO` already exist but are unused in the controller.
- Frontend `UpdateUserDto` includes `username`, `email`, `name`, `role`, `isActive` — matching these in backend DTO.
