# Project Research Summary

**Project:** UltiCode Technical Debt v1.0
**Domain:** Security hardening and functionality completion for an existing online judge platform (Spring Boot + Vue 3)
**Researched:** 2026-04-14
**Confidence:** HIGH

## Executive Summary

This is a technical debt remediation milestone for an existing online judge platform -- not greenfield development. The 9 scoped fixes are corrections to code that is either broken, insecure, or a non-functional placeholder. Crucially, the codebase already contains the building blocks for every fix: `CsrfService` with Redis-backed token rotation exists for SEC-01, `EmailServiceImpl` with SMTP support exists for SEC-02, `QueueService.enqueueJudgeJob()` exists for FUNC-01, and DOMPurify is already a frontend dependency for SEC-06. The work is integration and wiring, not new architecture.

The recommended approach requires only one new production dependency (OWASP Java Encoder 1.3.0 for output encoding) and three test-scoped dependencies (Testcontainers for MySQL/Redis integration tests). Zero new modules, zero new database migrations, and zero architectural layers are needed. All fixes modify existing components within their current module boundaries (1-5 files each), which limits blast radius and simplifies code review.

The key risk is in the security filter chain: SEC-01 (CSRF migration) and SEC-06 (XSS filter rewrite) both modify the request processing pipeline, and getting either wrong causes silent 403 errors across the entire application. The mitigation is strict ordering -- SEC-06 must precede SEC-01 because the current XssFilter corrupts request headers (including CSRF tokens), and dual CSRF protection from parallel mechanisms must be avoided at all costs.

## Key Findings

### Recommended Stack

**Minimal additions.** The existing stack covers all 9 fixes. Only one new production dependency is needed:

- **OWASP Java Encoder 1.3.0** -- context-aware output encoding to replace the fundamentally broken regex-based XssFilter. Zero transitive dependencies, ~50KB, OWASP-maintained. Added to `backend-spring/pom.xml` as a compile-scoped dependency.

- **Testcontainers (BOM-managed by Spring Boot 3.5)** -- MySQL and Redis integration tests for auth, submission, and security modules. Three test-scoped artifacts: `spring-boot-testcontainers`, `testcontainers:mysql`, `testcontainers:junit-jupiter`. Chosen over H2 because MySQL-specific SQL (ON DUPLICATE KEY, LIMIT, date functions) must match production.

- **Docker seccomp profile (JSON file)** -- static configuration file at `docker/sandbox/seccomp-profile.json`, not a code dependency. Extends Docker's default profile (~44 blocked syscalls) with explicit deny rules for dangerous syscalls (ptrace, mount, keyctl, clone, unshare, etc.).

All other fixes use existing dependencies: `spring-boot-starter-security` provides `CsrfTokenRepository` for SEC-01, `spring-boot-starter-mail` + existing `EmailServiceImpl` covers SEC-02, `jjwt 0.12.5` provides `Keys.hmacShaKeyFor()` for SEC-05 validation, and Vue 3 Composition API + existing composables cover QUAL-01 splitting.

### Expected Features

**Must have (table stakes -- security baseline):**
- SEC-01: CSRF Spring Security framework integration -- prevents CSRF attacks on all state-changing endpoints
- SEC-02: Password reset email sending -- makes account recovery functional in production
- SEC-03: UserDetailsService placeholder resolution -- removes a broken service that could cause auth failures if auto-wired
- SEC-04: Docker sandbox seccomp isolation -- syscall-level restriction for user-submitted code execution
- SEC-05: JWT secret startup validation -- prevents the app from running with a forgeable signing key
- SEC-06: XSS output encoding -- replaces regex-based input sanitization with correct output-side encoding

**Should have (core functionality completion):**
- FUNC-01: Admin rejudge implementation -- wires existing `QueueService.enqueueJudgeJob()` into the admin rejudge endpoint
- TEST-01: Backend critical module test coverage -- tests for auth, submission, code execution, and security modules

**Defer to v2+:**
- QUAL-01: Vue component splitting (14 oversized components) -- high file count (14-30+ files), high regression risk, no security impact. Should be done incrementally, one component at a time, not as a single phase.
- 11 MEDIUM-priority items from CONCERNS.md (CORS externalization, header sanitization removal, backup audit trail, performance fixes, etc.)
- 8 LOW-priority items (production config profile, default credentials, console.log cleanup, etc.)

### Architecture Approach

**All fixes are modifications to existing components.** The modular monolith structure keeps changes well-scoped: each fix touches 1-5 files within a single module or the shared `common/` layer. Zero new modules, zero new external dependencies (except OWASP encoder), and zero database migrations are required.

**Major integration surfaces:**

1. **Security filter chain** (SEC-01, SEC-03, SEC-05, SEC-06) -- The request processing pipeline in `SecurityConfig.java`, `XssFilter.java`, and the JWT authentication filter. This is the highest-risk surface because changes here affect every request in the application. SEC-06 must be resolved first (remove header corruption), then SEC-01 (migrate CSRF to Spring Security), while SEC-03 (delete dead code) and SEC-05 (startup validation) can be done in parallel.

2. **Code execution sandbox** (SEC-04) -- `Dockerfile` and `CodeExecutionService.buildDockerCommand()` in `docker/sandbox/` and `modules/submission/`. Independent of all other fixes. Requires empirical testing against all 5 supported languages (C, C++, Java, Python, JavaScript/Go).

3. **Service implementation stubs** (SEC-02, FUNC-01) -- Existing service classes with TODO stubs. `PasswordResetService` needs `EmailService` injection; `AdminSubmissionServiceImpl` needs `QueueService` injection. Both follow existing patterns already visible in the codebase.

4. **Frontend quality** (QUAL-01) -- 14 Vue components exceeding 600 lines (worst: 1356 lines). Container/presentational split with co-located composables. Orthogonal to backend security fixes.

### Critical Pitfalls

1. **Dual CSRF protection causes silent 403 errors** -- Enabling Spring Security's CSRF filter while the existing custom interceptor remains active creates double validation. Both reject legitimate requests with identical 403 responses. Prevention: remove the custom interceptor entirely when enabling Spring Security CSRF, and test every state-changing endpoint before and after.

2. **XssFilter removal exposes unprotected content during transition** -- Removing the regex filter before output encoding is in place creates an XSS window. The filter is broken but catches basic vectors. Prevention: add DOMPurify output encoding to all user-content rendering points before removing the input filter. Exempt code submission endpoints first (they are currently corrupted by the filter stripping `eval`).

3. **Docker seccomp profile breaks language compilation** -- Blocking `execve`, `fork`, `clone`, or `mprotect` prevents C/C++ and Java compilation. Conversely, allowing all of them may be too permissive. Prevention: start with Docker's default seccomp profile (already blocks ~44 syscalls), test all 5 languages, then add explicit deny rules only for clearly dangerous syscalls (ptrace, mount, keyctl, unshare). Use `strace` to profile actual syscall usage before writing restrictions.

4. **JWT secret validation rejects valid production sessions** -- If the current production secret is shorter than the new minimum length, the app refuses to start. Prevention: validate non-empty at minimum, WARN (not crash) if shorter than recommended, and support dual-key verification during secret rotation (accept tokens signed by either old or new key for 24-72 hours).

5. **Admin rejudge triggers thundering herd on judge queue** -- Rejudging thousands of submissions enqueues them all at once, blocking new user submissions. Prevention: rate-limited enqueueing (5-10/second), batch processing via `@Scheduled` task, optimistic locking on submission records, and a confirmation dialog showing affected submission count.

## Implications for Roadmap

Based on research, suggested phase structure (4 phases):

### Phase 1: Security Filter Chain Foundations
**Rationale:** The security filter chain is the highest-risk surface. Fixing it first eliminates the most dangerous vulnerabilities (CSRF disabled at framework level, broken XssFilter corrupting data, JWT running without secret validation). SEC-06 must come before SEC-01 because the XssFilter corrupts request headers including CSRF tokens. SEC-03 and SEC-05 are independent and can be done in parallel.
**Delivers:** CSRF protection integrated into Spring Security, XSS filter removed/replaced with output encoding, JWT secret validated at startup, dead UserDetailsService code removed.
**Addresses:** SEC-06, SEC-01, SEC-03, SEC-05
**Avoids:** Pitfall 1 (dual CSRF), Pitfall 3 (XSS transition window)
**Key dependencies:**
- SEC-06 removes header sanitization from XssFilter first (prevents CSRF token corruption)
- SEC-01 creates `RedisCsrfTokenRepository` wrapping existing `CsrfService` and removes custom `CsrfInterceptor`
- SEC-03 deletes `UserDetailsServiceImpl.java` (zero references, safe to delete)
- SEC-05 adds `@PostConstruct` validation to `JwtProperties` (WARN for short secrets, crash for empty)

### Phase 2: Core Functionality Completion
**Rationale:** After securing the filter chain, complete the non-functional stubs. Both SEC-02 and FUNC-01 are low-to-medium complexity (3-5 files each) and use existing infrastructure. SEC-04 (sandbox seccomp) is independent but benefits from being grouped with testing that follows in Phase 3.
**Delivers:** Working password reset email flow, functional admin rejudge, hardened Docker sandbox with seccomp profile.
**Addresses:** SEC-02, FUNC-01, SEC-04
**Uses:** Existing `EmailServiceImpl`, existing `QueueService`, Docker seccomp JSON profile
**Avoids:** Pitfall 5 (email spam -- add rate limiting), Pitfall 6 (thundering herd -- batch enqueue), Pitfall 4 (seccomp breaks compilation -- test all 5 languages)

### Phase 3: Test Coverage for Phases 1-2
**Rationale:** Tests should validate the security fixes from Phases 1-2. Writing tests alongside fixes is ideal, but grouping test creation as a phase ensures nothing is missed. Testcontainers provides real MySQL/Redis for integration tests of auth, CSRF, and submission flows.
**Delivers:** Test coverage for `AuthServiceImpl`, `JwtTokenProvider`, `CsrfService`, `SubmissionServiceImpl`, `CodeExecutionService`, `AdminSubmissionServiceImpl`, and `SecurityConfig`.
**Addresses:** TEST-01
**Uses:** Testcontainers (MySQL, Redis), existing Mockito patterns, `@WebMvcTest` for controller tests
**Avoids:** Pitfall 9 (flaky tests -- use Testcontainers, `@Transactional` rollback, avoid shared state)

### Phase 4: Frontend Quality (Incremental)
**Rationale:** Component splitting has the highest file count (14-30+ files) and highest regression risk but zero security impact. Doing it last avoids merge conflicts with security fixes. Should be done incrementally -- one component at a time, not as a batch.
**Delivers:** No component exceeds 400-500 lines. Co-located sub-components and composables for all 14 oversized views.
**Addresses:** QUAL-01
**Avoids:** Pitfall 7 (broken reactive state -- use composables, write tests before splitting, avoid prop mutation)
**Note:** This phase is the strongest candidate for deferral to v1.1 if timeline pressure exists. It has no security or functionality impact.

### Phase Ordering Rationale

- **Filter chain first:** SEC-06 -> SEC-01 -> SEC-03/SEC-05. The XssFilter currently corrupts request headers; this must stop before CSRF migration can work correctly. SEC-01 is the most complex security fix and benefits from a clean filter chain. SEC-03 and SEC-05 are independent one-file changes that can parallelize.
- **Functionality second:** SEC-02, FUNC-01, SEC-04 are independent of each other and of Phase 1. They complete non-functional stubs using existing infrastructure (EmailService, QueueService, Docker config). Grouping them together avoids spreading service-layer changes across multiple phases.
- **Tests third:** Tests validate Phases 1-2. Writing them as a phase (rather than scattered) ensures comprehensive coverage and avoids conflicting test setups during security fix development.
- **Quality last:** QUAL-01 has the highest touch count and no security impact. Doing it last avoids merge conflicts with security changes. Incremental approach (one component per sub-phase) reduces regression risk.
- **Cross-phase deployment risk:** SEC-01 and SEC-05 should NOT deploy in the same release cycle. Both affect the authentication pipeline, and simultaneous failure produces overlapping 403/401 symptoms that are extremely difficult to debug. Deploy with at least 24 hours of monitoring between them.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 2 (SEC-04):** Seccomp profile syscall list needs empirical validation. Use `strace` to profile actual syscalls used by each of the 5 supported languages in the Docker sandbox before writing deny rules. Current production JWT secret length must be checked before enforcing SEC-05's minimum length.
- **Phase 3 (TEST-01):** Exact `QueueService.enqueueJudgeJob()` implementation needs inspection to understand queue capacity and timeout behavior for test mocking.

Phases with standard patterns (skip research-phase):
- **Phase 1 (SEC-06, SEC-01, SEC-03, SEC-05):** Well-documented Spring Security patterns. `CsrfTokenRepository` interface, `@PostConstruct` validation, and filter ordering are standard Spring Boot practices.
- **Phase 2 (SEC-02, FUNC-01):** Simple dependency injection and method calls. Both follow existing patterns visible in the codebase.
- **Phase 4 (QUAL-01):** Standard Vue 3 Composition API refactoring with container/presentational split.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All findings based on direct source code analysis and verified Maven Central artifacts. OWASP Java Encoder 1.3.0 and Testcontainers BOM management confirmed. |
| Features | HIGH | All 9 fixes scoped by direct codebase inspection with file-level references. CONCERNS.md audit provides file:line evidence for every issue. |
| Architecture | HIGH | Zero new modules or layers required. Every fix modifies 1-5 existing files within current module boundaries. Dependency graph between fixes is simple (one strict ordering: SEC-06 before SEC-01). |
| Pitfalls | MEDIUM | Security filter chain pitfalls (CSRF double-validation, XSS transition window) are well-documented in Spring Security and OWASP literature. Docker seccomp syscall requirements per language need empirical validation. JWT secret rotation behavior depends on current production state (unknown). |

**Overall confidence:** HIGH

### Gaps to Address

- **Current JWT secret length in production:** SEC-05's minimum-length check (32 chars) could block startup if the existing secret is shorter. Must verify before enforcing hard minimum. Mitigation: log WARN for short-but-non-empty secrets initially, upgrade to ERROR in a future release.
- **Seccomp syscall profile per language:** The list of syscalls needed by C/C++ (gcc/g++), Java (JVM), Python (CPython), and JavaScript (Node.js) compilation and execution chains requires `strace` profiling on the actual Docker image. Docker's default seccomp profile is the safe starting point; custom deny rules should be added incrementally with per-language testing.
- **User-generated content encoding state in database:** The current XssFilter has been running for some time. User content in the database may contain HTML-entity-encoded strings (`&lt;script&gt;`). If output encoding is added on top, double-encoding will corrupt display. Mitigation: audit database for encoded entities before removing the input filter; write a one-time decode migration if needed.
- **QueueService implementation details for rejudge:** The exact `enqueueJudgeJob()` signature and queue capacity need inspection to determine appropriate rate limiting for batch rejudge operations. Mitigation: inspect `QueueService.java` and Redis queue configuration during Phase 2 planning.

## Sources

### Primary (HIGH confidence)
- Direct source code analysis of all 9 fix areas (file:line references in STACK.md, FEATURES.md, ARCHITECTURE.md)
- CONCERNS.md audit (2026-04-13) with file-level references
- Spring Security CSRF documentation -- https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
- OWASP XSS Prevention Cheat Sheet -- https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html
- Docker seccomp profile reference -- https://docs.docker.com/engine/security/seccomp/
- Spring Boot Testcontainers docs -- https://docs.spring.io/spring-boot/reference/testing/testcontainers.html
- OWASP Java Encoder on Maven Central (v1.3.0 confirmed)
- Testcontainers official site (v1.20.x, BOM-managed by Spring Boot 3.5)

### Secondary (MEDIUM confidence)
- Baeldung: Prevent XSS in Spring -- OWASP Encoder usage patterns
- Docker seccomp practical guide (Behrad Taher) -- Sandbox syscall profiling methodology
- Spring Boot 2->3 CSRF migration (Reddit) -- Real-world filter chain issues
- Spring Boot Testing Pitfalls (Baeldung) -- Test isolation patterns
- Vinted Engineering: Over-Mocking Downsides -- Integration test best practices
- Vue Prop Drilling Solutions (alexop.dev) -- Component splitting patterns

### Tertiary (LOW confidence)
- JWT Mistakes in Spring Boot (BuildBaseKit) -- General JWT patterns
- Online Judge System Architecture (ResearchGate) -- Domain-specific patterns

---
*Research completed: 2026-04-14*
*Ready for roadmap: yes*
