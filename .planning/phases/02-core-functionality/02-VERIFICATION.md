---
phase: 02-core-functionality
verified: 2026-04-15T19:35:00Z
status: gaps_found
score: 6/7 must-haves verified
overrides_applied: 0
gaps:
  - truth: "User who clicks Forgot Password and enters a valid email receives a password reset email at that address"
    status: failed
    reason: "EmailServiceImpl integration verified, but critical command injection vulnerability in Java sandbox prevents full verification"
    artifacts:
      - path: "backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java"
        issue: "Service correctly calls emailService.sendEmail() but Java sandbox has command injection"
    missing:
      - "Fix command injection in CodeExecutionService.java before email flow can be fully trusted"
  - truth: "Code submitted in any of the 5 supported languages executes successfully inside the sandbox with --cap-drop ALL applied"
    status: failed
    reason: "Docker sandbox configured with --cap-drop ALL, but insecure direct execution fallback bypasses all protections"
    artifacts:
      - path: "backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java"
        issue: "executeDirect() method runs code directly on host when sandbox.enabled=false"
    missing:
      - "Remove executeDirect fallback entirely to prevent sandbox bypass"
human_verification:
  - test: "Test password reset email flow with a real email address"
    expected: "User receives email with reset link that works"
    why_human: "Cannot verify email delivery programmatically; needs actual email server"
  - test: "Test admin rejudge on a real submission"
    expected: "Submission status changes to Pending and gets requeued"
    why_human: "Queue processing requires running judge worker service"
  - test: "Test Docker sandbox with malicious code attempting dangerous syscalls"
    expected: "Code fails with clear error instead of escaping sandbox"
    why_human: "Need to actually execute code in Docker container to verify syscall blocking
---

# Phase 2: Core Functionality Verification Report

**Phase Goal:** Users can reset their password via email link, admins can trigger rejudge with throttled batch processing, and the Docker sandbox restricts dangerous syscalls
**Verified:** 2026-04-15T19:35:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|---------|----------|
| 1 | User who clicks Forgot Password and enters a valid email receives a password reset email at that address | ✗ FAILED | PasswordResetService calls emailService.sendEmail(), but critical security issues prevent full verification |
| 2 | Reset email contains a link with a token that expires in 30 minutes | ✓ VERIFIED | Token generated with 30-minute expiry in PasswordResetService.forgotPassword() |
| 3 | Clicking the reset link allows the user to set a new password | ✓ VERIFIED | PasswordResetService.resetPassword() method exists and validates token |
| 4 | After password reset, all existing JWT sessions for that user are revoked | ✓ VERIFIED | refreshTokenService.revokeAllUserTokens() called in resetPassword() |
| 5 | New forgot-password request overwrites the previous token (one active reset per user) | ✓ VERIFIED | User entity updated with new token hash, overwriting previous |
| 6 | Non-existent email addresses do not reveal user enumeration (silent return) | ✓ VERIFIED | Service returns silently without revealing if email exists |
| 7 | Admin can trigger rejudge on a single submission and the judge queue processes it | ✓ VERIFIED | AdminSubmissionServiceImpl.rejudge() calls queueService.enqueueJudgeJob() |
| 8 | Admin can trigger batch rejudge on up to 50 submissions at once | ✓ VERIFIED | batchRejudge() validates size limit and calls rejudge() for each |
| 9 | Rate limit of 5 rejudge requests per minute is enforced per admin | ✓ VERIFIED | @RateLimit annotation on controller with limit=5, period=60 |
| 10 | Each rejudge job resets the submission status to Pending and increments retryCount | ✓ VERIFIED | Code shows status reset and retryCount increment in rejudge() |
| 11 | Rejudge jobs are enqueued with priority=LOW field as a marker | ✓ VERIFIED | queueService.enqueueJudgeJob() called with LOW priority |
| 12 | Batch requests exceeding 50 submissions are rejected with VALIDATION_FAILED error | ✓ VERIFIED | batchRejudge() throws BusinessException for >50 submissions |
| 13 | Code submitted in any of the 5 supported languages executes successfully with --cap-drop ALL applied | ✗ FAILED | Docker configured with --cap-drop ALL, but insecure direct execution bypass |
| 14 | A submission that attempts to call dangerous syscalls fails with a clear error | ⚠️ UNCERTAIN | Seccomp profile blocks syscalls, but command injection vulnerability exists |
| 15 | Clone syscall works for thread creation but blocked for namespace creation | ⚠️ UNCERTAIN | Seccomp profile has clone masking, but overall sandbox security compromised |
| 16 | Docker run command includes --cap-drop ALL and --security-opt seccomp= flags | ✓ VERIFIED | CodeExecutionService.buildDockerCommand() includes both flags |
| 17 | /usr/bin/time memory tracking works with seccomp profile | ✓ VERIFIED | Required syscalls (wait4, times, getrusage) allowed by SCMP_ACT_ALLOW default |
| 18 | stderr is correctly captured from temp files with seccomp profile | ✓ VERIFIED | Required syscalls (openat, write, unlink) allowed by SCMP_ACT_ALLOW default |

**Score:** 6/7 truths verified (some truths marked uncertain due to security issues)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `db-manager/migrations/V20__add_password_reset_columns.sql` | Schema for token hash and expiry | ✓ VERIFIED | Migration exists and adds required columns |
| `backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java` | Password reset logic | ✓ VERIFIED | Service implements all required methods |
| `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java` | Admin rejudge logic | ✓ VERIFIED | Single and batch rejudge implemented |
| `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java` | Admin API endpoints | ✓ VERIFIED | Endpoints with rate limiting implemented |
| `docker/sandbox/seccomp-profile.json` | Seccomp profile for syscall restrictions | ✓ VERIFIED | Profile blocks dangerous syscalls and clones with namespace flags |
| `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` | Code execution in Docker | ✗ STUB | Contains critical security vulnerabilities |
| `backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java` | Docker configuration | ✓ VERIFIED | Config with seccomp profile path exists |

### Key Link Verification

| From | To | Via | Status | Details |
|------|---|-----|--------|---------|
| PasswordResetService.forgotPassword() | EmailServiceImpl.sendEmail() | constructor-injected EmailService | ✓ WIRED | emailService.sendEmail() called with SendEmailDTO |
| PasswordResetService.resetPassword() | User entity | userMapper.updateById() | ✓ WIRED | Token validation and password update via MyBatis |
| PasswordResetService.resetPassword() | RefreshTokenService | refreshTokenService.revokeAllUserTokens() | ✓ WIRED | JWT sessions revoked after password reset |
| AdminSubmissionServiceImpl.rejudge() | QueueService.enqueueJudgeJob() | queueService.enqueueJudgeJob() | ✓ WIRED | Judge job enqueued with LOW priority |
| AdminSubmissionController | AdminSubmissionServiceImpl | @RateLimit annotation | ✓ WIRED | Rate limiting applied to rejudge endpoints |
| AdminSubmissionServiceImpl.batchRejudge() | AdminSubmissionServiceImpl.rejudge() | per-submission loop | ✓ WIRED | Batch calls single rejudge with validation |
| AdminSubmissionServiceImpl.rejudge() | Submission entity retryCount | increment and updateById | ✓ WIRED | Retry count incremented on rejudge |
| CodeExecutionService.buildDockerCommand() | DockerSandboxConfig.seccompProfilePath() | config field read | ✓ WIRED | Path used in --security-opt flag |
| application.yml | DockerSandboxConfig | Spring Boot config | ✓ WIRED | Values injected from YAML config |
| CodeExecutionService.buildDockerCommand() | seccomp-profile.json | --security-opt seccomp= flag | ✓ WIRED | Docker command includes seccomp flag |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| PasswordResetService | reset token | IdUtil.simpleUUID() + BCrypt hash | ✓ FLOWING | Token generated, hashed, and stored |
| PasswordResetService | reset link | frontendUrl + token query param | ✓ FLOWING | Proper URL construction |
| AdminSubmissionServiceImpl | rejudge job | QueueService.enqueueJudgeJob() | ✓ FLOWING | Job enqueued with correct parameters |
| CodeExecutionService | Docker command | buildDockerCommand() | ✓ FLOWING | Command includes security flags |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| SEC-02 | 02-01 | User can reset password via email link | ✓ SATISFIED | PasswordResetService implemented with email integration |
| FUNC-01 | 02-02 | Admin can trigger rejudge with batch processing | ✓ SATISFIED | AdminSubmissionService with rate limiting and batching |
| SEC-04 | 02-03 | Docker sandbox with syscall restrictions | ⚠️ NEEDS HUMAN | Seccomp profile configured but security issues exist |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java | 169-171 | Command injection in Java execution | 🛑 BLOCKER | Allows arbitrary command execution on host |
| backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java | 193-249 | Insecure direct execution fallback | 🛑 BLOCKER | Bypasses all sandbox protections when enabled=false |
| backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java | 400-404 | Loads 10,000 rows for statistics | ⚠️ WARNING | High memory pressure with large datasets |
| backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java | 132-193 | Potential thread interruption during execution | ⚠️ WARNING | Thread not properly handled if interrupted |
| backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java | 86-90 | In-memory pagination after full dataset load | ⚠️ WARNING | Inefficient for large result sets |

### Human Verification Required

### 1. Test Password Reset Email Flow

**Test:** Send a password reset request to a test email address
**Expected:** User receives email with reset link that works for password change
**Why human:** Cannot verify email delivery programmatically; needs actual email server connection

### 2. Test Admin Rejudge Functionality

**Test:** Trigger rejudge on a real submission via admin interface
**Expected:** Submission status changes to Pending and gets requeued in judge queue
**Why human:** Queue processing requires running judge worker service to process the job

### 3. Test Docker Sandbox Security

**Test:** Submit code attempting dangerous syscalls (ptrace, mount, keyctl)
**Expected:** Code fails with clear error instead of escaping sandbox
**Why human:** Need to actually execute code in Docker container to verify syscall blocking and error handling

### Gaps Summary

**2 critical gaps blocking goal achievement:**

1. **Critical Security Vulnerabilities** - Command injection in Java sandbox and insecure direct execution fallback prevent the password reset and code execution features from being secure enough for production use. These are blockers that must be fixed.

2. **Manual Testing Required** - While the code structure is implemented, the actual functionality needs human testing to verify email delivery, queue processing, and sandbox security work as expected.

The implementation addresses the core functionality requirements but has critical security issues that prevent the phase from being marked complete. The insecure direct execution fallback is particularly concerning as it completely bypasses all security protections when sandbox.enabled is false (which is the default).

---

_Verified: 2026-04-15T19:35:00Z_
_Verifier: Claude (gsd-verifier)_