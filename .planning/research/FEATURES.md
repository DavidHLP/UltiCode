# Feature Landscape: Critical Security & Core Functionality Fixes

**Domain:** Technical debt remediation for an online judge platform (Spring Boot + Vue 3)
**Researched:** 2026-04-14
**Overall confidence:** HIGH -- findings based on direct codebase inspection, existing CONCERNS.md audit, and source code analysis

## Executive Summary

This document maps the expected behavior, complexity, and dependencies for 9 fixes scoped in Milestone v1.0. These are not new features -- they are corrections to existing code that is either broken, insecure, or a non-functional placeholder. The fixes fall into two categories: **security hardening** (6 items) where the expected behavior is that the platform becomes resistant to known attack vectors, and **functional completion** (3 items) where the expected behavior is that existing stubs/TODOs become working features.

The codebase already has the building blocks for every fix: `spring-boot-starter-mail` and `EmailServiceImpl` exist for SEC-02, `QueueService.enqueueJudgeJob()` exists for FUNC-01, `CsrfService` + `CsrfInterceptor` exist for SEC-01. The work is integration and wiring, not greenfield development.

## Table Stakes (Must-Fix for Security Baseline)

These fixes are non-negotiable. Without them, the platform has known security vulnerabilities or non-functional core features. Missing any one of these means the platform is not production-ready.

| Fix ID | Fix | Why Required | Complexity | Current State | Expected Behavior After Fix |
|--------|-----|-------------|------------|---------------|---------------------------|
| SEC-01 | CSRF Spring Security framework integration | CSRF is disabled at framework level; custom interceptor coverage is unknown; any uncovered endpoint is vulnerable | Medium | `SecurityConfig.java:90` calls `AbstractHttpConfigurer::disable()`. Custom `CsrfInterceptor` + `CsrfService` (Redis-backed token rotation) exist but live outside Spring Security's filter chain | Spring Security's CSRF protection is enabled and integrated with the existing `CsrfService`. Token is generated/validated at framework level. All state-changing endpoints (POST/PUT/PATCH/DELETE) require valid CSRF token. Cookie-based or session-based CSRF token is issued on GET requests and validated on mutating requests. |
| SEC-02 | Password reset email sending | Password reset flow is non-functional; users cannot recover accounts in production | Low | `PasswordResetService.forgotPassword()` generates reset token and logs URL to server logs. `// TODO: Send email (Phase 3 full implementation)`. `EmailServiceImpl` already exists with `sendEmail()`, template rendering, SMTP support, and `JavaMailSender`. | `PasswordResetService.forgotPassword()` calls `EmailServiceImpl.sendEmail()` with a password-reset template containing the reset URL. User receives an email with a clickable link. The `app.email.enabled` flag gates between SMTP and log-only mode. Email log is recorded in `email_log` table with SENT/FAILED status. |
| SEC-03 | UserDetailsServiceImpl resolution | Placeholder `@Service` always throws `UsernameNotFoundException`; if any code path invokes Spring Security's `UserDetailsService`, authentication breaks | Low | `UserDetailsServiceImpl.java` is annotated `@Service` and implements `UserDetailsService`. `loadUserByUsername()` always throws. JWT auth works independently of this class. `UserMapper` exists in `modules/user/mapper/`. | Either: (a) Implement `loadUserByUsername()` to query `UserMapper` and return `UserDetails` with correct authorities, or (b) Remove `@Service` annotation / add `@ConditionalOnProperty` so Spring Security does not register a broken `UserDetailsService`. If JWT-only auth is confirmed, option (b) is preferred to reduce attack surface. |
| SEC-04 | Docker sandbox seccomp isolation | Sandbox container runs arbitrary user code without seccomp profile, AppArmor, or capability restrictions beyond default Docker | Medium | `Dockerfile` installs gcc, g++, openjdk-17-jdk-headless, nodejs, python3. Runs as non-root user `sandbox`. `--network=none` is used. No seccomp, no `--cap-drop`, no resource limits beyond Docker defaults. `CodeExecutionService.java:160-202` builds Docker run commands. | A custom seccomp JSON profile blocks dangerous syscalls (`ptrace`, `mount`, `keyctl`, `clone`, `unshare`, `pivot_root`, etc.) while allowing necessary ones for compilation and execution. Docker commands include `--cap-drop=ALL` and `--security-opt seccomp=profile.json`. Sandbox still compiles and executes C/C++, Java, Python, JavaScript, and Go correctly. |
| SEC-05 | JWT secret startup validation | `jwt.secret: ${JWT_SECRET:}` has empty default; app starts with empty secret, making tokens trivially forgeable | Low | `JwtProperties.java` has `private String secret` with no validation. `application.yml:48` defines `jwt.secret: ${JWT_SECRET:}`. `JwtTokenProvider.java` uses `secret` for HMAC signing. | `JwtProperties` (or `JwtTokenProvider`) has a `@PostConstruct` method that throws `IllegalStateException` if `secret` is null, empty, or shorter than 32 characters (256 bits for HS256). Application fails to start with a clear error message if `JWT_SECRET` is not configured. |
| SEC-06 | XSS protection via output encoding | `XssFilter` uses regex to strip `<script>`, `on*=`, `javascript:`, `eval(` patterns. Regex XSS prevention is fundamentally incomplete. Filter also corrupts legitimate data (code submissions containing `eval`) and sanitizes headers | Medium | `XssFilter.java` wraps `HttpServletRequest`, applies regex replacement on `getParameter()` and `getHeader()` values. Global filter registered for all requests. `dompurify` (v3.3.x) is already a dependency in both `console/` and `management/`. | Backend `XssFilter` is either removed entirely (output encoding is the correct defense) or narrowed to only specific user-content endpoints (forum posts, comments -- not code submissions). Frontend uses `DOMPurify.sanitize()` for all user-generated HTML rendering. No global request sanitization. Headers are never modified by XSS filter. |

## Differentiators (Completing Core Functionality)

These are not typical "differentiators" but rather functional completions. They turn non-functional stubs into working features, which is table stakes for the admin workflow.

| Fix ID | Fix | Value Proposition | Complexity | Current State | Expected Behavior After Fix |
|--------|-----|-------------------|------------|---------------|---------------------------|
| FUNC-01 | Admin Rejudge implementation | Admins can trigger re-evaluation of submissions after updating problem test cases | Medium | `AdminSubmissionServiceImpl.rejudge()` has `// TODO: Implement actual rejudge logic`. Returns success with hardcoded `newStatus="Pending"` but does not enqueue anything. `QueueService.enqueueJudgeJob(submissionId, problemId, userId, language, code)` exists and is fully functional. `Submission` entity has all needed fields. | Admin calls rejudge -> submission status resets to "Pending" -> `QueueService.enqueueJudgeJob()` is called with the original submission's code, language, problemId, userId -> `JudgeWorker` picks up the job and re-executes against current test cases -> submission status/result is updated. Batch rejudge works the same way for multiple submissions. Optional notification is sent to the user. |

## Quality Improvements (Maintainability)

| Fix ID | Fix | Value Proposition | Complexity | Current State | Expected Behavior After Fix |
|--------|-----|-------------------|------------|---------------|---------------------------|
| QUAL-01 | Split oversized Vue components | 14 Vue components exceed 600 lines (2 exceed 1200). Harder to maintain, test, and review. Increases merge conflict likelihood. | High | `ProblemListsView.vue` (1356 lines), `ProblemsListView.vue` (1224 lines), `ContestDetailView.vue` (1039 lines), `SubmissionsDetail.vue` (867 lines), `AnalyticsView.vue` (881 lines), plus 9 more at 600-804 lines. | Each oversized component is split into focused sub-components under `components/<feature>/` directories. Parent component imports and composes sub-components. Composables extract reusable logic. No component exceeds 400-500 lines. Functionality and appearance remain identical. |
| TEST-01 | Backend critical module test coverage | Auth, submission, and CodeExecution modules have zero tests. Regressions from fixes will go undetected. | High | Test directories exist for `achievement`, `backup`, `email`, `queue`, `user`, `vote`, `websocket` etc. but NO test directories for `auth`, `submission`, or `security`. Console has 15 test files (~7% coverage), Management has 1 (~1%), Backend has 22 files (~15%). | `AuthServiceTest.java`: login success/failure, token generation/validation, password reset flow. `SubmissionServiceTest.java`: create submission, status transitions, judge result handling. `CodeExecutionServiceTest.java`: Docker command construction, timeout handling, result parsing, language-specific compilation. `SecurityConfigTest.java`: CSRF token required on POST, JWT authentication on protected endpoints, public endpoints accessible without auth. |

## Anti-Features (Explicitly Do NOT Build)

| Anti-Feature | Why Avoid | What to Do Instead |
|-------------|-----------|-------------------|
| New CSRF token storage mechanism | `CsrfService` with Redis already works correctly | Integrate existing `CsrfService` into Spring Security's `CsrfTokenRepository` interface |
| New email sending library | `spring-boot-starter-mail` + `EmailServiceImpl` already handle templates, SMTP, logging | Wire `PasswordResetService` to use existing `EmailService.sendEmail()` |
| New XSS sanitization library on backend | Output encoding is the correct defense, not input sanitization | Remove/reduce `XssFilter`, rely on frontend `DOMPurify` for HTML content |
| New queue system for rejudge | `QueueService` + `JudgeWorker` + Redis queue already exist | Call existing `enqueueJudgeJob()` from admin rejudge |
| Writing E2E tests for Vue component splits | Refactoring is internal; E2E tests verify existing user flows, not component structure | Write focused unit tests for extracted sub-components |
| Adding AppArmor profiles for sandbox | Adds complexity and OS dependency beyond Docker portability | Seccomp profile alone provides sufficient syscall restriction |

## Feature Dependencies

```
SEC-05 (JWT secret validation) --> SEC-01 (CSRF) (SEC-01 should work first to ensure request safety)
SEC-03 (UserDetailsService) --> SEC-01 (CSRF) (CSRF fix must not depend on broken UserDetailsService)

SEC-02 (Password reset email) --> existing EmailService (no new dependency)
FUNC-01 (Admin Rejudge) --> existing QueueService + JudgeWorker (no new dependency)

SEC-06 (XSS fix) --> QUAL-01 (Vue splits) (simplified XssFilter reduces risk when splitting components)
SEC-04 (Docker seccomp) --> TEST-01 (CodeExecution tests) (seccomp changes must be tested to ensure compilation still works)

TEST-01 should be written ALONGSIDE each fix, not after
```

## Fix Complexity Analysis

### Low Complexity (1-2 files each, clear implementation path)

| Fix | Estimated Files Changed | Risk Level | Why Low |
|-----|------------------------|------------|---------|
| SEC-02 | 2-3 files | Low | `EmailService.sendEmail()` exists. Just wire `PasswordResetService` to call it with template. |
| SEC-03 | 1-2 files | Low | Either delete a file or implement a straightforward DB lookup using existing `UserMapper`. |
| SEC-05 | 1-2 files | Low | Add `@PostConstruct` validation to `JwtProperties` or `JwtTokenProvider`. 5-10 lines of code. |

### Medium Complexity (3-5 files, some architectural consideration)

| Fix | Estimated Files Changed | Risk Level | Why Medium |
|-----|------------------------|------------|-------------|
| SEC-01 | 4-6 files | Medium | Must integrate `CsrfService` with Spring Security's `CsrfTokenRepository`. Need to verify no JWT auth conflicts. Must test all state-changing endpoints. |
| SEC-04 | 3-4 files | Medium | Need to create seccomp JSON profile, test with all 5 supported languages (C, C++, Java, Python, JavaScript, Go), ensure compilation still works with restricted syscalls. |
| SEC-06 | 3-5 files | Medium | Remove/reduce `XssFilter`, add `DOMPurify` usage in frontend components rendering user content. Must audit which endpoints need protection and which (code submissions) must be excluded. |
| FUNC-01 | 3-4 files | Medium | Wire admin rejudge to existing `QueueService`. Need to reset submission state, re-fetch problem test cases, and handle edge cases (already-pending submissions, rate limiting). |

### High Complexity (Many files, structural changes)

| Fix | Estimated Files Changed | Risk Level | Why High |
|-----|------------------------|------------|------------|
| QUAL-01 | 14-30+ files | High | 14 components to split, each requiring extraction of sub-components, composables, and testing. High touch count means high regression risk. Should be done incrementally, one component at a time. |
| TEST-01 | 5-10 new test files | High | Writing meaningful tests for auth flows requires understanding JWT lifecycle, CSRF token flow, and submission lifecycle. Mocking Docker for CodeExecutionService tests requires care. |

## MVP Recommendation (Phase Ordering)

The fixes should be ordered to minimize dependencies and maximize early security wins:

### Phase 1: Startup Guard Rails (foundation)
1. **SEC-05** -- JWT secret validation (prevents running with broken auth, 1 file)
2. **SEC-03** -- UserDetailsService resolution (removes broken placeholder, 1-2 files)

Rationale: These are the simplest fixes and prevent the application from running in a dangerous or confusing state.

### Phase 2: Authentication & Session Security
3. **SEC-01** -- CSRF framework integration (most complex security fix, builds on Phase 1)
4. **SEC-06** -- XSS protection via output encoding (complements CSRF fix)

Rationale: CSRF and XSS are the two primary web app vulnerabilities. Fix both together to establish a complete session security baseline.

### Phase 3: Core Functionality Completion
5. **SEC-02** -- Password reset email (uses existing email infrastructure)
6. **FUNC-01** -- Admin rejudge (uses existing queue infrastructure)
7. **SEC-04** -- Docker sandbox seccomp (independent, but benefits from test infrastructure in Phase 4)

Rationale: These complete non-functional features. Each uses existing infrastructure, reducing implementation risk.

### Phase 4: Quality & Testing
8. **QUAL-01** -- Vue component splitting (structural, no security impact)
9. **TEST-01** -- Backend test coverage (validates all previous fixes)

Rationale: Splitting components after security fixes avoids merge conflicts. Tests should validate the security fixes from Phases 1-3.

**Defer to future milestones:**
- MEDIUM items (11): CORS externalization, header sanitization removal, backup audit trail, performance fixes, dependency cleanup, admin TODO stubs, broad exception catching, large service splitting
- LOW items (8): Production config profile, default credentials, console.log cleanup, SockJS removal, frontend tests, integration tests, moderation metrics

## Sources

- HIGH confidence: Direct codebase inspection of all 9 fix areas
- HIGH confidence: CONCERNS.md audit (2026-04-13) with file-level references
- HIGH confidence: Existing `EmailServiceImpl.java` confirms mail infrastructure readiness
- HIGH confidence: Existing `QueueService.java` interface confirms rejudge infrastructure readiness
- HIGH confidence: `dompurify` v3.3.x confirmed in both `console/package.json` and `management/package.json`
- HIGH confidence: `spring-boot-starter-mail` confirmed in `backend-spring/pom.xml`
- MEDIUM confidence: Seccomp profile syscall list needs testing against all 5 languages (standard syscall restriction patterns well-documented but language-specific requirements need empirical validation)
