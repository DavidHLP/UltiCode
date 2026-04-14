# Phase 02: Core Functionality - Context

**Gathered:** 2026-04-14
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

### Admin Rejudge (FUNC-01)
- **D-04:** Reuse existing QueueService.enqueueJudgeJob() — don't create a parallel submission path
- **D-05:** Batch size limit: max 50 submissions per rejudge request
- **D-06:** Rate limit: max 5 rejudge requests per admin per minute (throttled enqueue)
- **D-07:** Rejudge must not block the user submission queue — use separate admin queue or priority system

### Docker Sandbox Hardening (SEC-04)
- **D-08:** Add custom seccomp profile blocking dangerous syscalls: ptrace, mount, keyctl, unshare, clone (unprivileged), setns
- **D-09:** Apply --cap-drop ALL to remove all Linux capabilities
- **D-10:** Network isolation: --network none for code execution containers (no network access from submitted code)
- **D-11:** Existing 5 supported languages (C, C++, Java, Python, JavaScript/Go) must all continue to work after hardening

### Claude's Discretion
- Exact seccomp profile syscall whitelist/blacklist — determine which syscalls each language runtime needs and block the rest
- Email template content and styling for password reset
- Admin rejudge UI feedback (toast vs notification vs inline)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend Modules
- `backend-spring/src/main/java/com/ulticode/modules/email/service/` — EmailServiceImpl and email sending infrastructure
- `backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java` — Existing password reset service (stub)
- `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java` — Admin submission endpoints
- `backend-spring/src/main/java/com/ulticode/modules/queue/service/QueueService.java` — Judge job enqueue logic
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java` — Sandbox command builder
- `docker/sandbox/Dockerfile` — Current sandbox Docker configuration

### Requirements
- `.planning/REQUIREMENTS.md` — SEC-02, FUNC-01, SEC-04 acceptance criteria

### Phase 1 Context
- `.planning/phases/01-security-filter-chain/01-SUMMARY.md` — Security filter chain changes (dependency)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `EmailServiceImpl` — Full email module with controller, service, entity, mapper, templates, and status tracking
- `QueueService.enqueueJudgeJob()` — Existing judge job submission mechanism
- `AdminSubmissionController` — Existing admin endpoints for submission management
- `PasswordResetService` — Existing service structure, needs EmailServiceImpl wired in

### Established Patterns
- MyBatis-Plus for data access
- Redis for caching and rate limiting
- Docker for code execution isolation
- Result<T> wrapper for all API responses

### Integration Points
- Password reset: auth module → email module
- Admin rejudge: admin module → queue/submission modules
- Sandbox: submission module → docker sandbox config

</code_context>

<specifics>
## Specific Ideas

- Password reset flow should generate a unique token, store hash in database, send email with link containing token, verify on click
- Admin rejudge should allow filtering by submission status, problem ID, or user ID before batch selection
- Seccomp profile should be a separate JSON/config file in docker/sandbox/ for easy maintenance

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---
*Phase: 02-core-functionality*
*Context gathered: 2026-04-14*
