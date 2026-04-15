---
status: complete
phase: 02-core-functionality
source: 02-01-SUMMARY.md, 02-02-SUMMARY.md, 02-03-SUMMARY.md
started: 2026-04-15T12:30:38Z
updated: 2026-04-15T12:46:47Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Kill backend service. Start Spring Boot from scratch. Server boots without errors, Flyway migrations V18/V19/V20 apply cleanly, and health check returns UP.
result: pass

### 2. Password Reset - Send Reset Email
expected: Call POST /api/auth/forgot-password with a registered email. Backend returns 200. User receives a password reset email. For non-existent email, still returns 200 (silent).
result: pass

### 3. Password Reset - Reset Password with Token
expected: Call POST /api/auth/reset-password with a valid token and new password. Backend verifies BCrypt hash, updates password, clears token fields, revokes sessions. Returns 200. Expired/invalid token returns error.
result: pass

### 4. Admin Rejudge - Single Submission
expected: Admin calls POST /api/admin/submissions/{id}/rejudge. Status resets to Pending, retryCount increments, judge job enqueued. Submission gets re-judged.
result: pass

### 5. Admin Batch Rejudge - Validation
expected: Batch rejudge with > 50 IDs returns VALIDATION_FAILED (code 49999). With <= 50 IDs, each submission rejudged individually.
result: pass

### 6. Admin Rejudge Rate Limiting
expected: More than 5 rejudge requests per minute returns HTTP 429. Same for batch-rejudge.
result: pass

### 7. Docker Sandbox - Multi-Language Execution
expected: Code in JavaScript, Python, Java, C, C++ all execute successfully in hardened sandbox with correct results.
result: pass

### 8. Docker Sandbox - Ptrace Blocked
expected: Code using ptrace() syscall returns EPERM (errno=1), confirming dangerous syscalls blocked by seccomp.
result: pass

### 9. Submission Stats DTOs - Type Safety
expected: Admin submission stats APIs return properly typed DTOs (WeeklyProgressDTO, MonthlySubmissionStatsDTO, LanguageStatsDTO). No Object[] serialization issues.
result: pass

## Summary

total: 9
passed: 9
issues: 0
pending: 0
skipped: 0

## Gaps

[none]
