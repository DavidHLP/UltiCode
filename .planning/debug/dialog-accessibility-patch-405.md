---
status: resolved
trigger: |
  UsersListView.vue:149 Warning: Missing `Description` or `aria-describedby="undefined"` for DialogContent.
  request.ts:371 PATCH http://localhost:9001/admin/users/user-yuki 405 (Method Not Allowed)
  users.ts:86 Failed to update user: ApiError: Method not allowed: PATCH
symptoms:
  - expected: Dialog components should have proper aria-describedby for accessibility; PATCH /admin/users/{id} should update user
  - actual: Vue warning about missing Description/aria-describedby; Backend returns 405 Method Not Allowed for PATCH
  - errors: "Missing Description or aria-describedby for DialogContent"; "Method not allowed: PATCH"
  - timeline: Unknown, likely recent changes to dialog components or user update flow
  - reproduction: Open user edit/create dialog in management frontend; Try to update user via UserEditDialog
---

## Current Focus

hypothesis: |
  Two independent issues:
  1. Frontend: UserCreateDialog.vue and AlertDialog in UsersListView.vue missing aria-describedby/id attributes
  2. Backend: AdminUserController missing @PatchMapping for user updates

test: |
  Verify fixes by:
  1. Check browser console for no accessibility warnings
  2. Verify PATCH /admin/users/{id} returns 200 OK

expecting: |
  1. No Vue accessibility warnings in browser console
  2. User update via PATCH succeeds with 200 OK

next_action: Apply fixes based on explore agent findings

reasoning_checkpoint:
  - Frontend patterns identified: UserEditDialog.vue has correct implementation
  - Backend endpoint gap confirmed: AdminUserController has no @PatchMapping
  - Fixes needed in 3-4 files

## Evidence

- timestamp: 2026-05-01
  source: explore agent
  finding: UserCreateDialog.vue missing aria-describedby on DialogContent and using <p> instead of <DialogDescription>
  
- timestamp: 2026-05-01
  source: explore agent
  finding: UsersListView.vue AlertDialog (line 444) missing aria-describedby on AlertDialogContent and id on AlertDialogDescription
  
- timestamp: 2026-05-01
  source: explore agent
  finding: AdminUserController.java has no @PatchMapping("/{id}") method; only GET/POST/DELETE supported
  
- timestamp: 2026-05-01
  source: explore agent
  finding: management/src/api/admin/users.ts line 110 uses apiPatch but backend has no matching endpoint

## Eliminated

- hypothesis: Frontend using wrong HTTP method
  reason: Backend genuinely missing PATCH endpoint, not a routing issue

## Resolution

root_cause: |
  1. Frontend dialogs missing aria-describedby accessibility attributes
  2. Backend AdminUserController missing @PatchMapping for user updates

fix: |
  1. Frontend: 
     - UserCreateDialog.vue: Added DialogDescription import, aria-describedby on DialogContent, replaced <p> with <DialogDescription id="create-user-description">
     - UsersListView.vue: Added aria-describedby="bulk-delete-description" to AlertDialogContent and id="bulk-delete-description" to AlertDialogDescription
  
  2. Backend:
     - Created AdminUpdateUserDTO.java with fields: name, email, avatar, bio, company, github, website, location, twitter, preferredLanguage, role, isActive
     - AdminUserService.java: Added updateUser(String id, AdminUpdateUserDTO updateDTO) method
     - AdminUserServiceImpl.java: Implemented updateUser with field-by-field null-safe updates
     - AdminUserController.java: Added @PatchMapping("/{id}") endpoint with RateLimit and PreAuthorize

verification: |
  - Frontend: Dialog components now have proper aria-describedby/id attributes
  - Backend: PATCH /admin/users/{id} endpoint is available and returns Result<AdminUserVO>
  - All changes follow existing codebase patterns (consistent with AdminProblemController PATCH implementation)

files_changed:
  - management/src/views/users/UserCreateDialog.vue
  - management/src/views/users/UsersListView.vue
  - backend-spring/src/main/java/com/ulticode/modules/admin/dto/AdminUpdateUserDTO.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/service/AdminUserService.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java
  - backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminUserController.java
