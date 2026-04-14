# Phase 02: Core Functionality - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-14
**Phase:** 02-core-functionality
**Mode:** auto (all recommended defaults selected)
**Areas discussed:** Password reset email, Admin rejudge batch, Docker sandbox hardening
**Refinement pass:** Token storage, Queue strategy, Seccomp approach, Resource limits

---

## Password Reset Email (Initial)

| Option | Description | Selected |
|--------|-------------|----------|
| Wire EmailServiceImpl | Inject existing email service into PasswordResetService | ✓ |
| New email module | Create separate email sending service | |

**User's choice:** [auto] Wire EmailServiceImpl (recommended — module already exists)
**Notes:** Reset token TTL set to 30 minutes (standard). Use existing EmailTemplate system.

## Admin Rejudge (Initial)

| Option | Description | Selected |
|--------|-------------|----------|
| Batch with throttle | Max 50 per request, 5 req/min rate limit | ✓ |
| Unlimited rejudge | No restrictions | |

**User's choice:** [auto] Batch with throttle (recommended — prevents queue blocking)
**Notes:** Must not block user submission queue. Reuse QueueService.enqueueJudgeJob().

## Docker Sandbox (Initial)

| Option | Description | Selected |
|--------|-------------|----------|
| seccomp + cap-drop ALL + network none | Full hardening stack | ✓ |
| Minimal changes | Only add basic restrictions | |

**User's choice:** [auto] Full hardening stack (recommended — matches SEC-04 requirement)
**Notes:** Custom seccomp profile as separate config file. Block ptrace, mount, keyctl, unshare.

## Password Reset Token Storage (Refinement)

| Option | Description | Selected |
|--------|-------------|----------|
| Users table columns | Add password_reset_token_hash + password_reset_expires_at via Flyway | ✓ |
| Redis with TTL | Store in Redis with auto-expiry, no migration | |
| Separate tokens table | New password_reset_tokens table | |

**User's choice:** [auto] Users table columns (recommended — simplest, follows existing pattern)
**Notes:** BCrypt already available for hashing. No scheduled cleanup needed — expired tokens rejected on validation.

## Admin Rejudge Queue Strategy (Refinement)

| Option | Description | Selected |
|--------|-------------|----------|
| Same queue, lower priority | Rejudge jobs with priority flag, processed after user submissions | ✓ |
| Separate admin queue | Isolated admin-only queue | |

**User's choice:** [auto] Same queue, lower priority (recommended — aligns with D-04)
**Notes:** Avoids new queue infrastructure. Priority ordering sufficient to prevent blocking.

## Seccomp Profile Strategy (Refinement)

| Option | Description | Selected |
|--------|-------------|----------|
| Docker default + additions | Start from Docker default (~44 blocked), add extra blocks | ✓ |
| Fully custom whitelist | Complete whitelist from scratch per language | |

**User's choice:** [auto] Docker default + additions (recommended — incremental, lower risk)
**Notes:** STATE.md risk assessment recommends incremental approach. Custom whitelist higher regression risk.

## Sandbox Resource Limits (Refinement)

| Option | Description | Selected |
|--------|-------------|----------|
| Review and tighten | Audit existing memory/CPU limits as part of hardening | ✓ |
| Leave unchanged | Focus only on syscall/capability isolation | |

**User's choice:** [auto] Review and tighten (recommended — comprehensive hardening)
**Notes:** Resource limits part of defense-in-depth strategy.

---

## Claude's Discretion

- Exact seccomp syscall additions beyond listed dangerous ones
- Email template content and styling
- Admin rejudge UI feedback mechanism
- Specific memory/CPU limit values per language

## Deferred Ideas

None
