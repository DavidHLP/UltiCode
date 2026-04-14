# Phase 02: Core Functionality - Context

**Gathered:** 2026-04-14
**Updated:** 2026-04-15 (auto-refined: added D-17..D-20, corrected language list, clarified queue priority)
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire existing service stubs to real functionality: password reset emails actually send via EmailServiceImpl, admin rejudge uses batch enqueue with throttling, and Docker sandbox gets seccomp + capability hardening. No new modules — all changes within existing module boundaries.

</domain>

<decisions>
## Implementation Decisions

### Password Reset Email (SEC-02)
- **D-01:** PasswordResetService already has EmailServiceImpl injectable — wire it directly, no new email infrastructure needed
- **D-02:** Reset token TTL: 30 minutes (standard for password reset flows)
- **D-03:** Email template: use existing EmailTemplate system in the email module
- **D-12:** Token storage: add `password_reset_token_hash` and `password_reset_expires_at` columns to existing `users` table via Flyway migration (simplest approach, follows existing pattern, no new table needed)
- **D-13:** Token hash uses BCrypt (same hasher already in codebase for passwords), plain token sent in email link, only hash stored in DB
- **D-17:** Session invalidation on password reset: revoke all active JWT sessions for the user via Redis after successful password change (security best practice, prevents stale sessions)
- **D-18:** Concurrent reset token handling: new forgot-password request overwrites previous token hash and expiry (simplest approach, one active reset per user at a time)

### Admin Rejudge (FUNC-01)
- **D-04:** Reuse existing QueueService.enqueueJudgeJob() — don't create a parallel submission path
- **D-05:** Batch size limit: max 50 submissions per rejudge request
- **D-06:** Rate limit: max 5 rejudge requests per admin per minute (throttled enqueue)
- **D-14:** Queue strategy: same judge queue with lower priority for rejudge jobs (reuses existing QueueService infrastructure, D-04 confirms no parallel path)
- **D-07:** Rejudge must not block the user submission queue — implemented via throttled enqueue (D-05, D-06) rather than priority-aware polling
- **D-19:** Queue priority implementation: rejudge jobs enqueued with `priority=LOW` field in same FIFO RQueue. Worker does NOT support priority-aware polling (RQueue.poll() is FIFO-only). Priority-aware worker modification is DEFERRED to a future queue enhancement. Current throttled enqueue provides sufficient protection against blocking user submissions.

### Docker Sandbox Hardening (SEC-04)
- **D-08:** Add custom seccomp profile blocking dangerous syscalls: ptrace, mount, keyctl, unshare, clone (unprivileged), setns
- **D-09:** Apply --cap-drop ALL to remove all Linux capabilities
- **D-10:** Network isolation: --network none for code execution containers (no network access from submitted code) — ALREADY IMPLEMENTED in buildDockerCommand()
- **D-20:** Language support clarification: ROADMAP mentions "JavaScript/Go" but actual SUPPORTED_LANGUAGES in CodeExecutionService are: `javascript`, `python`, `java`, `c`, `cpp` (5 languages, Go is NOT supported). Seccomp testing scope covers only these 5 languages.
- **D-11:** Existing 5 supported languages must all continue to work after hardening
- **D-15:** Seccomp profile strategy: start from Docker's default seccomp profile (~44 blocked syscalls), then add additional syscall blocks on top (incremental approach, lower risk of breaking language runtimes)
- **D-16:** Resource limits review: review and tighten existing memory/CPU limits as part of hardening pass (comprehensive approach, reduce attack surface)

### Claude's Discretion
- Exact seccomp profile syscall additions beyond the listed dangerous ones — determine which syscalls each language runtime needs via strace profiling
- Email template content and styling for password reset
- Admin rejudge UI feedback (toast vs notification vs inline)
- Specific memory/CPU limit values after review — set reasonable defaults per language runtime
- Password reset email link URL format (frontend routes `/forgot-password` and `/reset-password` already exist in console router)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend Modules
- `backend-spring/src/main/java/com/ulticode/modules/email/service/` — EmailServiceImpl and email sending infrastructure
- `backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java` — Existing password reset service (stub)
- `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java` — Admin submission endpoints
- `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java` — Admin submission service (contains rejudge TODO stub)
- `backend-spring/src/main/java/com/ulticode/modules/queue/service/QueueService.java` — Judge job enqueue logic
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` — Sandbox command builder (buildDockerCommand, SUPPORTED_LANGUAGES, LANGUAGE_RUNNERS)
- `docker/sandbox/Dockerfile` — Current sandbox Docker configuration

### Frontend Routes (already exist)
- `console/src/router/index.ts` lines 292-298 — `/forgot-password` and `/reset-password` routes
- `console/src/views/auth/ForgotPasswordView.vue` — Existing forgot password page
- `console/src/views/auth/ResetPasswordView.vue` — Existing reset password page

### Database
- `db-manager/migrations/` — Flyway migration files (check latest version number for new migration naming)
- `backend-spring/src/main/java/com/ulticode/modules/user/entity/User.java` — User entity (password_reset columns will be added here)

### Queue Infrastructure
- `backend-spring/src/main/java/com/ulticode/modules/queue/config/QueueConfig.java` — Queue configuration (max concurrent jobs, queue names)
- `backend-spring/src/main/java/com/ulticode/modules/queue/constants/` — Queue constant definitions

### Phase 1 Context
- `.planning/phases/01-security-filter-chain/01-SUMMARY.md` — Security filter chain changes (dependency)

### Requirements
- `.planning/REQUIREMENTS.md` — SEC-02, FUNC-01, SEC-04 acceptance criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `EmailServiceImpl` — Full email module with controller, service, entity, mapper, templates, and status tracking
- `QueueService.enqueueJudgeJob()` — Existing judge job submission mechanism with Redis-backed queue
- `AdminSubmissionController` — Existing admin endpoints for submission management
- `AdminSubmissionServiceImpl.rejudge()` — TODO stub that currently only logs
- `PasswordResetService` — Existing service structure, needs EmailServiceImpl wired in
- `BCryptPasswordEncoder` — Already available in security config for password hashing, can reuse for token hashing
- `Redisson RQueue` — Job queue infrastructure with configurable max concurrent jobs (default 10)
- Frontend routes `/forgot-password` and `/reset-password` already exist with corresponding Vue views

### Established Patterns
- MyBatis-Plus for data access with `@TableName` annotations
- Redis for caching, rate limiting, and job queues
- Docker for code execution isolation
- Result<T> wrapper for all API responses
- Flyway migrations via db-manager CLI for schema changes
- Constructor injection via @RequiredArgsConstructor (Lombok)
- @PostConstruct for startup validation (established in Phase 1 JWT validation)
- @RateLimit annotation for endpoint throttling
- `SUPPORTED_LANGUAGES` static Set in CodeExecutionService defines the 5 sandbox languages

### Integration Points
- Password reset: auth module -> email module (inject EmailServiceImpl)
- Password reset: auth module -> user entity (new columns for token hash + expiry)
- Password reset: auth module -> Redis (session invalidation after password change)
- Admin rejudge: admin module -> queue/submission modules
- Sandbox: submission module -> docker sandbox config (Dockerfile, CodeExecutionService)
- Flyway: new migration for users table columns

### Key Technical Notes
- RQueue is FIFO-only: `queue.poll()` has no priority awareness. The JudgeJob has a `priority` field but it is stored but never consumed during polling. Priority-aware worker polling is a future enhancement.
- `--network none` is ALREADY implemented in buildDockerCommand() — no change needed for network isolation
- `--security-opt no-new-privileges:true` is ALREADY implemented
- `--cap-drop ALL` is safe for C/C++ compilation: Docker capabilities != syscalls. Capabilities like CAP_SYS_ADMIN are removed but compilation syscalls (fork, execve, etc.) are allowed by the default seccomp profile. The custom seccomp profile adds defense-in-depth but should not block compilation.

</code_context>

<specifics>
## Specific Ideas

- Password reset flow: generate unique token -> store BCrypt hash in users table with 30min expiry -> send email with link containing plain token -> verify on click -> invalidate token after use -> revoke all user sessions via Redis
- Admin rejudge: allow filtering by submission status, problem ID, or user ID before batch selection
- Seccomp profile: separate JSON file in docker/sandbox/ for easy maintenance, layered on top of Docker default
- Token cleanup: no scheduled cleanup needed — tokens expire via expiry timestamp check on validation (expired tokens are simply rejected); new forgot-password requests overwrite previous tokens (D-18)
- Rejudge priority: enqueue with priority=LOW field; defer worker-side priority polling to future enhancement (D-19)

</specifics>

<deferred>
## Deferred Ideas

- **Queue worker priority-aware polling**: The judge worker (QueueService consumer) currently uses FIFO polling and ignores the JudgeJob.priority field. Implementing dual-queue architecture or scan-based priority polling is deferred to a future phase. Current throttled enqueue provides adequate protection.
- **Go language support**: Not in current SUPPORTED_LANGUAGES set. If added in the future, seccomp profile will need validation for Go runtime syscalls.

</deferred>

---
*Phase: 02-core-functionality*
*Context gathered: 2026-04-14*
*Auto-refined: 2026-04-14, 2026-04-15*
