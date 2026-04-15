---
phase: 02
slug: core-functionality
status: secured
threats_open: 0
asvs_level: 1
created: 2026-04-15
---

# Phase 02 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| client -> API (forgotPassword) | Untrusted email input from reset form | Email address (PII) |
| client -> API (resetPassword) | Untrusted token + new password | Token, password (secret) |
| admin -> API (rejudge) | Trusted authenticated admin | Submission IDs |
| API -> MySQL (users/submissions) | Trusted internal | User data, submission data |
| user code -> Docker sandbox | Untrusted arbitrary code | Code execution |
| application -> Docker CLI | Trusted (app constructs command) | Docker run parameters |
| Docker daemon -> kernel | Enforced by kernel | Syscalls, capabilities |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-02-01 | Tampering | forgotPassword email | mitigate | @Email + parameterized LambdaQueryWrapper | closed |
| T-02-02 | Info Disclosure | user enumeration | mitigate | Silent return for non-existent emails | closed |
| T-02-03 | Spoofing | token replay | mitigate | Token cleared after use + expiry check | closed |
| T-02-04 | Elevation | session persistence | mitigate | revokeAllUserTokens() on password change | closed |
| T-02-05 | DoS | forgotPassword rate limit | mitigate | @RateLimit(limit=3, period=3600) | closed |
| T-02-06 | Info Disclosure | token in URL | accept | Single-use, 30-min expiry, HTTPS | closed |
| T-02-07 | DoS | rejudge endpoint | mitigate | @RateLimit(5/min) + batch cap 50 | closed |
| T-02-08 | Elevation | rejudge auth | mitigate | @PreAuthorize(ADMIN/SUPER_ADMIN) | closed |
| T-02-09 | Info Disclosure | rejudge errors | accept | Admin-only trusted role | closed |
| T-02-10 | DoS | batch rejudge flooding | mitigate | Batch cap + rate limit (max 250 jobs/min) | closed |
| T-02-11 | Elevation | ptrace syscall | mitigate | Seccomp SCMP_ACT_ERRNO | closed |
| T-02-12 | Elevation | mount syscall | mitigate | Seccomp + --cap-drop ALL | closed |
| T-02-13 | Elevation | keyctl syscall | mitigate | Seccomp SCMP_ACT_ERRNO | closed |
| T-02-14 | Elevation | unshare/setns | mitigate | Seccomp + --cap-drop ALL | closed |
| T-02-15 | Elevation | clone namespaces | mitigate | Seccomp masked-EQ (0x7C000000) | closed |
| T-02-16 | Elevation | Linux capabilities | mitigate | --cap-drop ALL | closed |
| T-02-17 | DoS | seccomp too aggressive | mitigate | SCMP_ACT_ALLOW default | closed |
| T-02-18 | Tampering | seccomp file modification | accept | Host filesystem, not container | closed |
| T-02-19 | DoS | /usr/bin/time broken | mitigate | SCMP_ACT_ALLOW default + D-21 documented | closed |
| T-02-20 | Info Disclosure | stderr capture broken | mitigate | SCMP_ACT_ALLOW default + D-22 documented | closed |

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02-01 | T-02-06 | Token in URL — single-use, 30-min expiry, transmitted over HTTPS. Standard practice for password reset flows. | gsd-security-auditor | 2026-04-15 |
| AR-02-02 | T-02-09 | Rejudge error messages returned to admin-only (trusted role). Server-side logging for debugging. | gsd-security-auditor | 2026-04-15 |
| AR-02-03 | T-02-18 | Seccomp profile on Docker host filesystem. Modifying requires host access. Acceptable for this deployment. | gsd-security-auditor | 2026-04-15 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-15 | 20 | 20 | 0 | gsd-security-auditor |
