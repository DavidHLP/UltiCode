# Phase 02: Core Functionality - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-14
**Phase:** 02-core-functionality
**Mode:** auto (all recommended defaults selected)
**Areas discussed:** Password reset email, Admin rejudge batch, Docker sandbox hardening

---

## Password Reset Email

| Option | Description | Selected |
|--------|-------------|----------|
| Wire EmailServiceImpl | Inject existing email service into PasswordResetService | ✓ |
| New email module | Create separate email sending service | |

**User's choice:** [auto] Wire EmailServiceImpl (recommended — module already exists)
**Notes:** Reset token TTL set to 30 minutes (standard). Use existing EmailTemplate system.

## Admin Rejudge

| Option | Description | Selected |
|--------|-------------|----------|
| Batch with throttle | Max 50 per request, 5 req/min rate limit | ✓ |
| Unlimited rejudge | No restrictions | |

**User's choice:** [auto] Batch with throttle (recommended — prevents queue blocking)
**Notes:** Must not block user submission queue. Reuse QueueService.enqueueJudgeJob().

## Docker Sandbox

| Option | Description | Selected |
|--------|-------------|----------|
| seccomp + cap-drop ALL + network none | Full hardening stack | ✓ |
| Minimal changes | Only add basic restrictions | |

**User's choice:** [auto] Full hardening stack (recommended — matches SEC-04 requirement)
**Notes:** Custom seccomp profile as separate config file. Block ptrace, mount, keyctl, unshare.

## Claude's Discretion

- Seccomp profile syscall whitelist per language runtime
- Email template content and styling
- Admin rejudge UI feedback mechanism

## Deferred Ideas

None
