# SECURITY.md -- UltiCode Phase 02 Core Functionality

**Audit Date:** 2026-04-15
**Phase:** 02 -- Core Functionality
**ASVS Level:** 1
**Auditor:** GSD Secure Phase (automated)

---

## Threat Register Verification

### Plan 01: Password Reset Email (SEC-02)

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-02-01 | Tampering | mitigate | CLOSED | `PasswordResetService.java:47-50` -- `LambdaQueryWrapper.eq(User::getEmail, email)` uses parameterized query (MyBatis-Plus). Email validated at controller boundary via DTO. |
| T-02-02 | Info Disclosure | mitigate | CLOSED | `PasswordResetService.java:52-56` -- Silent `return` for non-existent email; only `log.debug()` (no client-facing indication). |
| T-02-03 | Spoofing | mitigate | CLOSED | `PasswordResetService.java:99,116-117` -- Expiry check via `.gt(User::getPasswordResetExpiresAt, LocalDateTime.now())`; token cleared with `setPasswordResetTokenHash(null)` after use. |
| T-02-04 | Elevation | mitigate | CLOSED | `PasswordResetService.java:121` -- `refreshTokenService.revokeAllUserTokens(matchedUser.getId())` called after password change, invalidating all active JWT sessions. |
| T-02-05 | DoS | mitigate | CLOSED | `PasswordResetService.java:45` -- `@RateLimit(key = "'forgot-password:' + #email", limit = 3, period = 3600)` enforces 3 requests per hour per email. |
| T-02-06 | Info Disclosure | accept | CLOSED | Accepted risk documented below. Token is single-use, time-limited (30 min), and transmitted over HTTPS in production. |

### Plan 02: Admin Rejudge (FUNC-01)

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-02-07 | DoS | mitigate | CLOSED | `AdminSubmissionController.java:65` -- `@RateLimit(key = "admin:submission-rejudge", limit = 5, period = 60)`. `AdminSubmissionServiceImpl.java:330` -- batch size cap `ids.size() > 50`. |
| T-02-08 | Elevation | mitigate | CLOSED | `AdminSubmissionController.java:67,77` -- `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")` on both rejudge endpoints. |
| T-02-09 | Info Disclosure | accept | CLOSED | Accepted risk documented below. Error messages from enqueue failures returned only to admin (trusted role); server-side logging for details. |
| T-02-10 | DoS | mitigate | CLOSED | Same evidence as T-02-07. Combined rate limit (5 req/min) + batch cap (50) = max 250 jobs/min worst case. |

### Plan 03: Docker Sandbox Seccomp Hardening (SEC-04)

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-02-11 | Elevation | mitigate | CLOSED | `docker/sandbox/seccomp-profile.json:7` -- `"ptrace"` in blocked syscalls with `"action": "SCMP_ACT_ERRNO"`. Verified via Docker test (EPERM). |
| T-02-12 | Elevation | mitigate | CLOSED | `docker/sandbox/seccomp-profile.json:8` -- `"mount"` blocked by seccomp. `CodeExecutionService.java:144` -- `"--cap-drop", "ALL"` removes CAP_SYS_ADMIN. |
| T-02-13 | Elevation | mitigate | CLOSED | `docker/sandbox/seccomp-profile.json:9` -- `"keyctl"` blocked with SCMP_ACT_ERRNO. |
| T-02-14 | Elevation | mitigate | CLOSED | `docker/sandbox/seccomp-profile.json:10-11` -- `"unshare"`, `"setns"` blocked. `CodeExecutionService.java:144` -- `--cap-drop ALL`. |
| T-02-15 | Elevation | mitigate | CLOSED | `docker/sandbox/seccomp-profile.json:18-28` -- clone blocked via `SCMP_CMP_MASKED_EQ` with value 2080505856 (CLONE_NEWUSER\|CLONE_NEWNS\|CLONE_NEWPID\|CLONE_NEWNET\|CLONE_NEWIPC\|CLONE_NEWUTS). Thread creation (unmasked clone) remains allowed. |
| T-02-16 | Elevation | mitigate | CLOSED | `CodeExecutionService.java:144` -- `"--cap-drop", "ALL"` removes all Linux capabilities. |
| T-02-17 | DoS | mitigate | CLOSED | `docker/sandbox/seccomp-profile.json:2` -- `"defaultAction": "SCMP_ACT_ALLOW"`. Only 6 named dangerous syscall groups blocked. |
| T-02-18 | Tampering | accept | CLOSED | Accepted risk documented below. Profile is on Docker host filesystem, not inside container. Modifying requires host access. |
| T-02-19 | DoS | mitigate | CLOSED | `docker/sandbox/seccomp-profile.json:2,31` -- SCMP_ACT_ALLOW default permits wait4, times, getrusage. D-21 dependency documented in `_comments`. |
| T-02-20 | Info Disclosure | mitigate | CLOSED | `docker/sandbox/seccomp-profile.json:2,32` -- SCMP_ACT_ALLOW default permits openat, write, unlink for temp files. D-22 dependency documented in `_comments`. |

---

## Accepted Risks Log

| Risk ID | Threat ID | Description | Justification | Review Date |
|---------|-----------|-------------|----------------|-------------|
| AR-02-01 | T-02-06 | Password reset token transmitted in URL query parameter | Token is single-use, BCrypt-hashed in DB, expires in 30 minutes. HTTPS encrypts the URL in transit. This is standard practice for password reset flows (RFC 6749 pattern). URL tokens are not logged server-side beyond the debug-level log line. | 2026-04-15 |
| AR-02-02 | T-02-09 | Admin rejudge error messages may contain internal details | Error messages are returned only to authenticated admin users (ADMIN/SUPER_ADMIN role via @PreAuthorize). Detailed errors are logged server-side. Admins are trusted actors in this threat model. | 2026-04-15 |
| AR-02-03 | T-02-18 | Seccomp profile file on host filesystem could be modified | The seccomp profile is on the Docker host, not inside the container. Modifying it requires host-level access, which is already a full compromise scenario. File integrity monitoring can be added in a future phase if needed. | 2026-04-15 |

---

## Unregistered Flags

No unregistered threat flags detected. None of the three execution summaries contained a `## Threat Flags` section.

---

## Summary

| Metric | Value |
|--------|-------|
| Total Threats | 20 |
| Closed | 20 |
| Open | 0 |
| Accepted Risks | 3 |
| Unregistered Flags | 0 |
| ASVS Level | 1 |
| Block Policy | critical, high |

**Result:** All 20 threats from Phase 02 threat register verified and closed.

---

## Files Audited

- `backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java`
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java`
- `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java`
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java`
- `backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java`
- `docker/sandbox/seccomp-profile.json`
- `backend-spring/src/main/resources/application.yml`
