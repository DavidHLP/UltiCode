# Roadmap: UltiCode Technical Debt Remediation v1.0

## Overview

Secure the platform's filter chain (CSRF, XSS, JWT validation), complete non-functional service stubs (password reset email, admin rejudge), harden the Docker code-execution sandbox, and improve code quality through test coverage and component splitting. All 9 fixes modify existing components within current module boundaries -- zero new modules, zero new architectural layers, and only one new production dependency (OWASP Java Encoder).

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Security Filter Chain** - Fix CSRF bypass, replace broken XSS filter, validate JWT secret, remove dead auth code
- [ ] **Phase 2: Core Functionality** - Complete password reset email, admin rejudge, and Docker sandbox hardening
- [ ] **Phase 3: Test Coverage** - Add Testcontainers integration tests for auth, submission, and code execution modules
- [ ] **Phase 4: Frontend Quality** - Split 14 oversized Vue components into smaller composable pieces

## Phase Details

### Phase 1: Security Filter Chain
**Goal**: All state-changing endpoints are protected by Spring Security CSRF, XSS is handled via correct output encoding instead of broken input filtering, JWT secret is validated at startup, and dead auth code is removed
**Depends on**: Nothing (first phase)
**Requirements**: SEC-06, SEC-01, SEC-05, SEC-03
**Success Criteria** (what must be TRUE):
  1. POST/PUT/PATCH/DELETE endpoints reject requests without valid CSRF tokens (Spring Security CsrfFilter active, custom CsrfInterceptor removed)
  2. User-submitted content containing `eval()`, `javascript:`, or HTML tags passes through the backend uncorrupted (XssFilter no longer sanitizes parameters, headers, or query strings)
  3. Application refuses to start when JWT_SECRET environment variable is empty or missing, and logs a warning when it is shorter than 32 characters
  4. UserDetailsServiceImpl.java no longer exists in the codebase and the application starts without errors
**Plans**: 3 plans

Plans:
- [x] 01-01: Replace XssFilter input sanitization with output encoding (SEC-06)
- [x] 01-02: Migrate CSRF protection to Spring Security framework (SEC-01)
- [x] 01-03: Add JWT secret startup validation and remove UserDetailsServiceImpl placeholder (SEC-05, SEC-03)

### Phase 2: Core Functionality
**Goal**: Users can reset their password via email link, admins can trigger rejudge with throttled batch processing, and the Docker sandbox restricts dangerous syscalls
**Depends on**: Phase 1
**Requirements**: SEC-02, FUNC-01, SEC-04
**Success Criteria** (what must be TRUE):
  1. User who clicks "Forgot Password" and enters a valid email receives a password reset email at that address within 30 seconds
  2. Admin can trigger rejudge on a single submission or a batch of submissions, and the judge queue processes them at a controlled rate without blocking new user submissions
  3. Code submitted in any of the 5 supported languages (C, C++, Java, Python, JavaScript/Go) executes successfully inside the sandbox with `--cap-drop ALL` and a custom seccomp profile applied
  4. A submission that attempts to call dangerous syscalls (ptrace, mount, keyctl) fails with a clear error rather than escaping the sandbox
**Plans**: 3 plans

Plans:
- [x] 02-01: Wire password reset to actual email sending via EmailServiceImpl (SEC-02)
- [x] 02-02: Implement admin rejudge with batch size limits and throttled enqueue (FUNC-01)
- [x] 02-03: Harden Docker sandbox with seccomp profile and capability drop (SEC-04)

### Phase 3: Test Coverage
**Goal**: Auth, submission, and code execution critical modules have integration tests using real MySQL and Redis via Testcontainers, providing regression safety for the security fixes from Phases 1-2
**Depends on**: Phase 2
**Requirements**: TEST-01
**Success Criteria** (what must be TRUE):
  1. AuthServiceImpl, JwtTokenProvider, and CsrfService have unit tests covering login, token generation/validation, and CSRF token lifecycle
  2. SubmissionServiceImpl and CodeExecutionService have integration tests that spin up real MySQL and Redis containers (Testcontainers) and verify submission creation, judge job enqueueing, and sandbox command building
  3. PasswordResetService and AdminSubmissionServiceImpl have tests covering the new email send and rejudge flows implemented in Phase 2
  4. All new tests pass both individually and as a full suite (no flaky tests from shared state)
**Plans**: 3 plans

Plans:
- [ ] 03-01: Auth and security module tests (AuthService, JwtTokenProvider, CsrfService, JwtProperties, PasswordResetService)
- [ ] 03-02: Submission and code execution module tests + Testcontainers infrastructure (SubmissionService, CodeExecutionService, AdminSubmissionService verification)
- [ ] 03-03: Testcontainers integration tests for SubmissionServiceImpl (real MySQL + Redis)

### Phase 4: Frontend Quality
**Goal**: No Vue component exceeds 500 lines; all 14 oversized components are split into smaller co-located sub-components and composables with no behavioral regressions
**Depends on**: Phase 3
**Requirements**: QUAL-01
**Success Criteria** (what must be TRUE):
  1. None of the 14 previously-oversized Vue components exceed 500 lines
  2. Every split component renders and functions identically to the original (same user-visible behavior for all views: problem lists, contest detail, submissions, analytics, settings, etc.)
  3. Extracted composables own their data fetching and state, child components receive data via props and emit events back (no prop mutation)
  4. The console and management frontends both build successfully with no TypeScript errors
**Plans**: 2 plans

Plans:
- [ ] 04-01: Split console oversized components (ProblemListsView, ProblemListView, ContestDetailView, SubmissionsDetail, PersonalView, ProblemDetailView, ProblemExplorer, Calendars, SettingsView, HiddenTestCasesEditor)
- [ ] 04-02: Split management oversized components (ProblemsListView, AnalyticsView, ModerationQueueView) and moderation store

## Progress


| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Security Filter Chain | 0/3 | Not started | - |
| 2. Core Functionality | 3/3 | Complete with critical issues | - |
| 3. Test Coverage | 0/3 | Not started | - |
| 4. Frontend Quality | 0/2 | Not started | - |
