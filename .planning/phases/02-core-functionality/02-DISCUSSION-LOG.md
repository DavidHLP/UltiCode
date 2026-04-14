# Phase 02: Core Functionality - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-14 (initial), 2026-04-15 (refinement)
**Phase:** 02-core-functionality
**Areas discussed:** Password reset email, Admin rejudge, Docker sandbox hardening, Session invalidation, Queue priority, Language support

---

## Session 1: 2026-04-14 (initial auto-discuss)

### Password Reset Email

| Option | Description | Selected |
|--------|-------------|----------|
| Wire EmailServiceImpl directly | Inject existing email service into PasswordResetService | ✓ |
| New email abstraction layer | Create wrapper around EmailServiceImpl | |
| External email service (SendGrid etc) | Add new dependency | |

**User's choice:** Wire EmailServiceImpl directly (D-01)

| Option | Description | Selected |
|--------|-------------|----------|
| 30-minute TTL | Standard for password reset flows | ✓ |
| 15-minute TTL | More restrictive | |
| 1-hour TTL | More lenient | |

**User's choice:** 30-minute TTL (D-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Existing EmailTemplate system | Use the email module's template rendering | ✓ |
| Inline HTML string | Build email body in PasswordResetService | |

**User's choice:** Existing EmailTemplate system (D-03)

| Option | Description | Selected |
|--------|-------------|----------|
| DB columns on users table | Add password_reset_token_hash + expires_at to users | ✓ |
| Separate password_resets table | New table with user_id, token_hash, expires_at | |
| Redis-only storage | Keep current Redis approach | |

**User's choice:** DB columns on users table (D-12)

| Option | Description | Selected |
|--------|-------------|----------|
| BCrypt hash | Same hasher as passwords, proven in codebase | ✓ |
| SHA-256 hash | Faster but weaker | |
| Plain token in DB | No hashing, direct comparison | |

**User's choice:** BCrypt hash (D-13)

### Admin Rejudge

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse QueueService.enqueueJudgeJob() | Single queue path, proven infrastructure | ✓ |
| Separate rejudge queue | Independent queue for admin jobs | |
| Direct Docker execution | Skip queue, run immediately | |

**User's choice:** Reuse QueueService.enqueueJudgeJob() (D-04)

| Option | Description | Selected |
|--------|-------------|----------|
| Max 50 per request | Reasonable batch size | ✓ |
| Max 100 per request | Larger batches | |
| Max 10 per request | More conservative | |

**User's choice:** Max 50 per request (D-05)

| Option | Description | Selected |
|--------|-------------|----------|
| 5 requests/min per admin | Throttled enqueue | ✓ |
| 10 requests/min per admin | More permissive | |
| 1 request/min per admin | Very conservative | |

**User's choice:** 5 requests/min (D-06)

### Docker Sandbox Hardening

| Option | Description | Selected |
|--------|-------------|----------|
| Custom seccomp JSON profile | Block ptrace, mount, keyctl, unshare, clone, setns | ✓ |
| Docker default seccomp only | ~44 blocked syscalls, no custom additions | |
| Strict seccomp (whitelist) | Only allow known-needed syscalls | |

**User's choice:** Custom seccomp profile on top of Docker default (D-08, D-15)

| Option | Description | Selected |
|--------|-------------|----------|
| --cap-drop ALL | Remove all Linux capabilities | ✓ |
| --cap-drop specific caps | Targeted capability removal | |
| No capability changes | Keep current state | |

**User's choice:** --cap-drop ALL (D-09)

---

## Session 2: 2026-04-15 (auto-refinement)

### Password Reset - Session Invalidation

| Option | Description | Selected |
|--------|-------------|----------|
| Revoke all sessions via Redis | Security best practice, prevents stale sessions | ✓ |
| Keep sessions active | Less disruptive, user stays logged in on other devices | |

**Auto-selected:** Revoke all sessions via Redis (D-17)

### Password Reset - Concurrent Tokens

| Option | Description | Selected |
|--------|-------------|----------|
| New request overwrites previous | Simplest, one active reset per user | ✓ |
| Allow multiple active tokens | More flexible but more complex | |
| Reject if active token exists | Force user to wait | |

**Auto-selected:** New request overwrites previous (D-18)

### Admin Rejudge - Queue Priority

| Option | Description | Selected |
|--------|-------------|----------|
| Defer priority polling; rely on throttled enqueue | Research confirms RQueue is FIFO-only; throttling sufficient | ✓ |
| Dual-queue architecture | High-priority + low-priority queues with weighted polling | |
| Worker scan-and-reorder | Worker scans queue for high-priority jobs first | |

**Auto-selected:** Defer priority polling (D-19)

### Docker Sandbox - Language Support

| Option | Description | Selected |
|--------|-------------|----------|
| 5 languages only (code is authoritative) | javascript, python, java, c, cpp — Go not in SUPPORTED_LANGUAGES | ✓ |
| Add Go support | Would require new sandbox configuration | |

**Auto-selected:** 5 languages only, no Go (D-20)

## Claude's Discretion

- Exact seccomp profile syscall additions beyond listed dangerous ones
- Email template content and styling
- Admin rejudge UI feedback mechanism
- Specific memory/CPU limit values per language
- Password reset email link URL parameter format

## Deferred Ideas

- Queue worker priority-aware polling (future enhancement)
- Go language support (not in current scope)
