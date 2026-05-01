---
status: resolved
trigger: |
  POST http://localhost:9001/admin/users/user-yuki/reset-password 400 (Bad Request)
  [API Error] req_1777611758638_3vcq7a8 
  {status: 400, message: 'Request failed with status code 400', data: {…}}
  Failed to reset password: ApiError: Validation failed
    at ApiError.fromAxiosError (request.ts:58:12)
    at request.ts:336:36
    at async Object.resetPassword (users.ts:164:5)
    at async Proxy.resetPassword (users.ts:187:7)
    at async handleReset (UserResetPasswordDialog.vue:48:5)
created: 2026-05-01
updated: 2026-05-01
---

## Symptoms

1. **Expected behavior**: Admin should be able to reset a user's password via the management panel
2. **Actual behavior**: POST request to `/admin/users/{id}/reset-password` returns 400 Bad Request with "Validation failed"
3. **Error messages**: ApiError: Validation failed (status: 400)
4. **Timeline**: Unknown - issue reported on 2026-05-01
5. **Reproduction**: 
   - Login as admin
   - Go to Users list
   - Click "Reset Password" on any user
   - Enter a password (possibly short)
   - Submit → 400 error

## Context

- **Frontend**: management/src/views/users/UserResetPasswordDialog.vue
- **API Client**: management/src/api/admin/users.ts (resetPassword function)
- **Request Handler**: management/src/utils/request.ts
- **Backend Controller**: backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminUserController.java
- **Backend DTO**: backend-spring/src/main/java/com/ulticode/modules/admin/dto/ResetPasswordRequest.java
- **Backend Service**: backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminUserServiceImpl.java

## Current Focus

**hypothesis**: The password entered by admin fails backend validation (@Size(min=8, max=128)), but frontend shows generic error instead of specific validation message

**test**: Check if frontend properly displays validation errors from backend response

**expecting**: Frontend should show user-friendly validation messages (e.g., "Password must be at least 8 characters")

**next_action**: Investigate frontend error handling in UserResetPasswordDialog.vue and request.ts interceptor

## Evidence

- timestamp: 2026-05-01
  observation: Backend ResetPasswordRequest has @Size(min=8, max=128) validation on password field
  source: backend-spring/src/main/java/com/ulticode/modules/admin/dto/ResetPasswordRequest.java

- timestamp: 2026-05-01
  observation: Frontend UserResetPasswordDialog has no client-side validation for password length
  source: management/src/views/users/UserResetPasswordDialog.vue

- timestamp: 2026-05-01
  observation: GlobalExceptionHandler returns validation errors in data field as Map<String, String>, but frontend only uses message field
  source: backend-spring/src/main/java/com/ulticode/common/exception/GlobalExceptionHandler.java:78-83

## Eliminated

## Resolution

**root_cause**: Frontend UserResetPasswordDialog had no client-side validation and showed generic error messages when backend validation failed. Users could submit passwords shorter than 8 characters, triggering backend @Size validation and displaying an unhelpful "Validation failed" error.

**fix**: 
1. Added client-side password validation (min 8, max 128 chars) in UserResetPasswordDialog.vue
2. Improved error handling to extract and display specific backend validation messages from the response data field
3. Added visual feedback (red border) and password hint text
4. Added i18n translations for validation messages (en-US and zh-CN)

**verification**: 
- ESLint passes on modified files
- Passwords shorter than 8 characters now show client-side error before submitting
- Backend validation errors are properly displayed in toast notifications

**files_changed**:
- management/src/views/users/UserResetPasswordDialog.vue
- management/src/i18n/locales/en-US/modules/users.ts
- management/src/i18n/locales/zh-CN/modules/users.ts
