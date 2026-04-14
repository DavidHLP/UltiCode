# Architecture: Fix Integration Analysis

**Project:** UltiCode Technical Debt Remediation
**Researched:** 2026-04-14
**Focus:** How 9 critical/high fixes integrate with existing architecture

## Executive Summary

All 9 fixes are modifications to existing components -- no new architectural layers, no new modules, and no new external dependencies are required. The fixes cluster into three architectural surfaces: (1) the security filter chain and interceptor pipeline (SEC-01, SEC-03, SEC-05, SEC-06), (2) the code execution sandbox (SEC-04), and (3) existing service implementations with TODO stubs (SEC-02, FUNC-01). QUAL-01 and TEST-01 are frontend and testing concerns respectively, orthogonal to the backend security fixes.

The existing modular monolith structure makes these fixes well-scoped: each fix touches 1-3 files within a single module or the shared `common/` layer. The dependency graph between fixes is minimal -- SEC-06 (XSS filter rewrite) should precede SEC-01 (CSRF migration) because both touch the filter chain and SEC-06 removes the header-sanitizing behavior that could interfere with CSRF header handling.

## Component Modification Map

### Modified vs New Components

| Fix | Action | Component | Layer | Files Changed |
|-----|--------|-----------|-------|---------------|
| SEC-01 | **Modify** | `SecurityConfig.java` | `common/config` | 1 |
| SEC-01 | **Modify** | `CsrfInterceptor.java` | `security/csrf` | 1 |
| SEC-02 | **Modify** | `PasswordResetService.java` | `modules/auth` | 1 |
| SEC-02 | **Modify** | `EmailService.java` / `EmailServiceImpl.java` | `modules/email` | 0-1 (add method) |
| SEC-03 | **Delete** | `UserDetailsServiceImpl.java` | `security` | 1 (remove) |
| FUNC-01 | **Modify** | `AdminSubmissionServiceImpl.java` | `modules/admin` | 1 |
| SEC-04 | **Modify** | `Dockerfile` | `docker/sandbox` | 1 |
| SEC-04 | **Modify** | `CodeExecutionService.java` | `modules/submission` | 1 |
| SEC-05 | **Modify** | `JwtProperties.java` or `JwtTokenProvider.java` | `security/jwt` | 1 |
| SEC-06 | **Modify** | `XssFilter.java` | `common/filter` | 1 |
| QUAL-01 | **Split** | 14 Vue components | `console/`, `management/` | 14 split into ~35-45 |
| TEST-01 | **Add** | Test classes | `backend-spring/src/test` | ~8-12 new files |

**Zero new modules. Zero new dependencies. Zero database migrations.**

## Detailed Integration Analysis

### SEC-01: CSRF Migration to Spring Security Framework

**Current state:**
- `SecurityConfig.java` line 90: `.csrf(AbstractHttpConfigurer::disable)` -- CSRF entirely disabled at framework level
- `CsrfInterceptor.java`: Custom `HandlerInterceptor` registered via `WebConfig` (but `WebConfig` is currently empty -- the interceptor must be registered elsewhere or is not active)
- `CsrfService.java`: Redis-backed token storage with `validateAndRotateToken()` method, fully functional

**Key finding:** `WebConfig.java` is empty (just `@Configuration`). The `CsrfInterceptor` is annotated with `@Component` but NOT registered as an interceptor in `WebConfig`. This means the custom CSRF interceptor may NOT be active in the filter chain. The CSRF validation is either happening through some other mechanism or is not actually running.

**Architecture change required:**

Option A (Recommended): Migrate to Spring Security's built-in CSRF by creating a custom `CsrfTokenRepository` backed by the existing `CsrfService`:

```
SecurityConfig.java
  - Remove: .csrf(AbstractHttpConfigurer::disable)
  - Add: .csrf(csrf -> csrf.csrfTokenRepository(new RedisCsrfTokenRepository(csrfService)))
  - Add: .csrf(csrf -> csrf.csrfTokenRequestHandler(new HeaderCsrfTokenRequestHandler()))
```

This requires creating one new class: `RedisCsrfTokenRepository` implementing `CsrfTokenRepository` that delegates to `CsrfService`. This class lives in `security/csrf/` alongside the existing code.

Option B (Simpler, lower risk): Keep CSRF disabled at framework level but properly register `CsrfInterceptor` in `WebConfig`:

```
WebConfig.java
  - Add: implements WebMvcConfigurer
  - Add: @Override addInterceptors() registering CsrfInterceptor
```

**Integration points:**
- `SecurityConfig.java` (core change)
- `CsrfService.java` (used by new repository, no modification needed)
- `CsrfInterceptor.java` (Option B: registered; Option A: can be removed)
- `WebConfig.java` (Option B: add interceptor registration)
- Frontend `request.ts` (no change -- already sends `X-CSRF-Token` header)
- Frontend `shared/auth-core/csrf.ts` (no change -- already handles token rotation)

**Risk:** Option A is architecturally correct but requires careful testing. The existing `CsrfService.validateAndRotateToken()` uses a `tokenId:tokenValue` format, while Spring Security's `CsrfToken` uses `headerValue:tokenValue`. The format difference needs to be bridged in the repository adapter.

### SEC-02: Password Reset Email

**Current state:**
- `PasswordResetService.java`: Has full reset flow (token generation, Redis storage, password update, session revocation) but only logs the reset URL instead of sending email
- `EmailServiceImpl.java`: Fully functional with SMTP support via `JavaMailSender`, template rendering, HTML email support. Has `sendEmail(SendEmailDTO dto)` method.
- `application.yml`: SMTP config already defined (`spring.mail.host`, `port`, `username`, `password`) with environment variable overrides. `app.email.enabled` defaults to `false`.
- `EmailService` interface: No specific "send password reset email" method, but generic `sendEmail(SendEmailDTO)` is sufficient

**Architecture change required:**

Inject `EmailService` into `PasswordResetService` and replace the log-only call:

```
PasswordResetService.java
  - Add field: private final EmailService emailService
  - Replace line 65 (log.info) with:
      emailService.sendEmail(SendEmailDTO.builder()
          .to(email)
          .subject("Reset Your UltiCode Password")
          .html(buildResetEmailHtml(resetUrl))
          .build());
```

**Integration points:**
- `PasswordResetService.java` (add dependency, replace log with email call)
- `EmailService.java` / `EmailServiceImpl.java` (no change -- generic method sufficient)
- No new database migration needed
- No SMTP config changes needed (already in application.yml)
- An email template for password reset should be created via the existing template system (seed data migration), but this is optional -- inline HTML works

**Consideration:** The `app.email.enabled` flag defaults to `false`. When false, `EmailServiceImpl` logs instead of sending. This is acceptable for dev but must be `true` in production. No code change needed for this -- it is an environment variable concern.

### SEC-03: UserDetailsServiceImpl Placeholder

**Current state:**
- `UserDetailsServiceImpl.java`: `@Service` annotated, implements `UserDetailsService`, always throws `UsernameNotFoundException`
- Referenced by: **Nothing**. `grep` shows zero references to `UserDetailsService` outside the implementation file itself
- `JwtAuthenticationFilter.java`: Creates `UserDetails` manually (line 60-64) using `new User(userId, "", authorities)` -- does NOT use `UserDetailsService`
- Authentication flow: Entirely JWT-based, `DaoAuthenticationProvider` is NOT configured in `SecurityConfig`

**Architecture change required:**

Simply delete the file. It is dead code that creates confusion:

```
Delete: security/UserDetailsServiceImpl.java
```

**Verification:** No other file in the codebase imports or references `UserDetailsService`. The JWT filter builds `UserDetails` inline. Spring Security does not auto-wire `UserDetailsService` unless `DaoAuthenticationProvider` is explicitly configured, which it is not in `SecurityConfig`.

**Risk:** None. The class is never called.

### FUNC-01: Admin Rejudge

**Current state:**
- `AdminSubmissionServiceImpl.rejudge()`: Line 275 has `// TODO: Implement actual rejudge logic`. Currently just sets `newStatus = "Pending"` and returns success without actually re-queuing
- `SubmissionServiceImpl.submit()`: Lines 91-114 show the exact pattern for creating a submission and enqueuing a judge job via `queueService.enqueueJudgeJob(submissionId, problemId, userId, language, code)`
- `QueueService.enqueueJudgeJob()`: Takes `(submissionId, problemId, userId, language, code)` -- all available from the existing submission entity
- `JudgeJob`: Has a static factory `JudgeJob.create()` that builds the job with all required fields

**Architecture change required:**

The rejudge needs to: (1) reset submission status to "Pending", (2) clear previous results, (3) re-enqueue via `QueueService`.

```
AdminSubmissionServiceImpl.java
  - Add field: private final QueueService queueService
  - Modify rejudge():
      1. Validate submission exists (already done)
      2. Reset: submission.setStatus("Pending"), clear runtime/memory/testDetails
      3. Save: submissionMapper.updateById(submission)
      4. Enqueue: queueService.enqueueJudgeJob(id, submission.getProblemId(), ...)
      5. Optionally notify via WebSocket (stretch)
```

**Integration points:**
- `AdminSubmissionServiceImpl.java` (add QueueService dependency, implement rejudge logic)
- `QueueService.java` (no change -- existing method sufficient)
- `SubmissionMapper.java` (no change -- standard updateById)
- `JudgeWorker.java` (no change -- processes jobs from queue generically)
- WebSocket notification (optional): If `notifyUser` is true, publish to `/user/queue/notifications`

**Key dependency:** `AdminSubmissionServiceImpl` currently depends on `SubmissionMapper`, `UserMapper`, `ProblemMapper`. Adding `QueueService` is a new dependency injection, but `QueueService` is already a Spring bean.

### SEC-04: Docker Seccomp Hardening

**Current state:**
- `docker/sandbox/Dockerfile`: Debian slim base, installs compilers, creates non-root user `sandbox`, no seccomp profile
- `CodeExecutionService.buildDockerCommand()`: Already applies several security flags:
  - `--network none` (network isolation)
  - `--memory 256m`, `--cpus 1.0`, `--pids-limit 128` (resource limits)
  - `--read-only`, `--tmpfs /tmp:rw,size=64m` (filesystem isolation)
  - `--user 1000:1000`, `--security-opt no-new-privileges:true`
  - Missing: `--cap-drop ALL`, `--security-opt seccomp=profile.json`

**Architecture change required:**

Two changes:

1. **Dockerfile** -- multi-stage build to minimize attack surface:
```dockerfile
# Multi-stage: build stage installs compilers
# Runtime stage copies only necessary binaries
FROM debian:bookworm-slim AS builder
RUN apt-get update && apt-get install -y --no-install-recommends ...
# Runtime stage
FROM debian:bookworm-slim
COPY --from=builder /usr/bin/gcc /usr/bin/g++ /usr/bin/node /usr/bin/python3 ...
```
(Tradeoff: multi-stage increases image size due to compiler runtime dependencies. For C/C++, gcc/g++ runtime libs are needed. Simpler approach: keep single-stage but add `--cap-drop ALL`.)

2. **CodeExecutionService.buildDockerCommand()** -- add seccomp profile and capability drop:
```java
cmd.addAll(List.of(
    "--cap-drop", "ALL",
    "--security-opt", "seccomp=/etc/ulticode/seccomp-default.json",
    // ... existing flags
));
```

A seccomp profile JSON file needs to be created at `docker/sandbox/seccomp-default.json` blocking dangerous syscalls: `ptrace`, `mount`, `keyctl`, `unshare`, `clone` (with certain flags), `acct`, `add_key`, `clock_adjtime`, etc.

**Integration points:**
- `docker/sandbox/Dockerfile` (modify -- optionally multi-stage)
- `CodeExecutionService.java` (add 2 flags to buildDockerCommand)
- New file: `docker/sandbox/seccomp-default.json` (new seccomp profile)
- CI/CD: Rebuild sandbox image after Dockerfile changes
- Application config: Seccomp profile path can be externalized to `code-execution.sandbox.seccomp-profile` in `DockerSandboxConfig`

**Risk:** Seccomp profiles can break legitimate code execution. Python's `os.fork()`, Java threading, and C signal handling all use syscalls that might be restricted. The profile must be tested against all 5 supported languages. Docker's default seccomp already blocks ~44 syscalls; the custom profile should only add a small number of additional restrictions on top.

### SEC-05: JWT Secret Startup Validation

**Current state:**
- `application.yml` line 47: `jwt.secret: ${JWT_SECRET:}` -- empty default
- `JwtProperties.java`: Binds to `jwt.secret` as a plain `String` field. No validation.
- `JwtTokenProvider.getSigningKey()`: Calls `jwtProperties.getSecret().getBytes()` -- if secret is empty string, `Keys.hmacShaKeyFor()` will throw `WeakKeyException` at token generation time, NOT at startup

**Architecture change required:**

Add `@PostConstruct` validation to `JwtProperties`:

```java
// JwtProperties.java
import jakarta.annotation.PostConstruct;

@PostConstruct
void validateSecret() {
    if (secret == null || secret.isBlank()) {
        throw new IllegalStateException(
            "JWT secret is not configured. Set JWT_SECRET environment variable " +
            "(must be at least 32 characters for HS256)");
    }
    if (secret.length() < 32) {
        throw new IllegalStateException(
            "JWT secret must be at least 32 characters (256 bits for HS256). " +
            "Current length: " + secret.length());
    }
}
```

**Integration points:**
- `JwtProperties.java` (add @PostConstruct method, 6 lines)
- No other changes needed
- `JwtTokenProvider.java` (no change)
- `application.yml` (no change)

**Risk:** This will crash the application at startup if `JWT_SECRET` is not set. This is intentional and correct behavior. The `.env.example` file should document the requirement.

### SEC-06: XSS Filter Rewrite

**Current state:**
- `XssFilter.java`: 78 lines, implements `jakarta.servlet.Filter`, runs at `Ordered.HIGHEST_PRECEDENCE + 1`
- Wraps ALL `HttpServletRequest` objects with `XssRequestWrapper`
- `XssRequestWrapper` sanitizes: `getParameter()`, `getParameterValues()`, `getHeader()`, `getQueryString()`
- Uses 6 regex patterns to strip: `<script>`, `on*=`, `javascript:`, `vbscript:`, `eval(`, `expression(`
- Problems: Regex-based is bypassable, sanitizes headers (breaks CSRF token format), sanitizes query strings (corrupts search queries)

**Architecture change required:**

Replace the entire filter approach. Instead of input sanitization (which corrupts data), use response output encoding for user-facing content:

```
Option A (Recommended): Remove XssFilter entirely
  - Delete XssFilter.java
  - Rely on frontend DOMPurify (already a dependency) for output encoding
  - For API responses containing user content, the frontend renders them via
    v-html with DOMPurify.sanitize()
  - Code submission endpoints don't need XSS filtering at all

Option B: Keep filter but fix it
  - Remove header sanitization (getHeader override)
  - Remove query string sanitization
  - Exclude code submission endpoints from filtering
  - Replace regex with OWASP Java HTML Sanitizer for parameter sanitization
```

**Recommendation:** Option A. The filter's input-side sanitization is an anti-pattern that corrupts data (e.g., a user legitimately submitting code containing `eval()` would have it stripped). DOMPurify on the output side is the correct approach and is already available in both frontends.

**Integration points:**
- `XssFilter.java` (delete or gut -- remove header/queryString sanitization at minimum)
- Frontend components that render user content must verify DOMPurify usage
- No API contract changes

### QUAL-01: Component Splitting

**Current state:** 14 Vue components exceed 600 lines (2 exceed 1200 lines). No shared composables exist for the extracted logic.

**Splitting strategy by component:**

| Component | Lines | Splits |
|-----------|-------|--------|
| `ProblemListsView.vue` (console) | 1356 | Extract: filter bar, problem list table, problem card, list detail panel |
| `ProblemsListView.vue` (management) | 1224 | Extract: problem table, problem form/dialog, filter bar, bulk actions |
| `ContestDetailView.vue` | 1039 | Extract: contest header, leaderboard table, problem list, scoreboard |
| `SubmissionsDetail.vue` | 867 | Extract: submission header, test case results, code view panel |
| `AnalyticsView.vue` | 881 | Extract: metric cards, charts, date range picker, export controls |
| `ModerationQueueView.vue` | 768 | Extract: queue table, report detail, action buttons, filter bar |
| `ProblemListView.vue` (console) | 804 | Extract: problem list, difficulty filter, tag filter, sort controls |
| `Calendars.vue` | 790 | Extract: calendar grid, event tooltip, month navigation |
| `SettingsView.vue` | 627 | Extract: setting sections (general, security, email) |
| `HiddenTestCasesEditor.vue` | 602 | Extract: test case row, add button, batch import |
| `moderation.ts` (store) | 600 | Extract: report actions, moderation helpers |
| `ProblemDetailView.vue` | 692 | Extract: problem header, description panel, code panel |
| `PersonalView.vue` | 665 | Extract: profile header, stats section, activity heatmap |
| `ProblemExplorer.vue` | 642 | Extract: search bar, problem card, filter sidebar |

**File naming convention:** Child components go in a co-located directory:
```
console/src/views/personal/problem-lists/
  ProblemListsView.vue          (main view, ~200 lines)
  ProblemListFilter.vue         (filter bar)
  ProblemListTable.vue          (table view)
  ProblemListCard.vue           (card view)
  ProblemListDetail.vue         (detail panel)
  useProblemListFilter.ts       (composable for filter logic)
```

**Integration points:**
- Parent component template: `<component :is="..." />` or direct import
- Composables extracted to `composables/` or co-located
- i18n keys remain in existing locale files (no restructuring needed)
- Router: no changes (parent view stays at same route)

### TEST-01: Backend Test Coverage

**Current state:** 22 existing test files, all unit tests with `@ExtendWith(MockitoExtension.class)`. No integration tests exist. Missing tests for: `AuthServiceImpl`, `SubmissionServiceImpl`, `CodeExecutionService`, `PasswordResetService`, `CsrfService`, `JwtTokenProvider`.

**Existing test pattern** (from `EmailServiceTest.java`):
```java
@ExtendWith(MockitoExtension.class)
class ServiceNameTest {
    @Mock  private DependencyA depA;
    @Mock  private DependencyB depB;
    @InjectMocks  private ServiceImpl service;

    @Nested @DisplayName("methodName") class MethodNameTests {
        @Test @DisplayName("should do X when Y")
        void shouldDoXWhenY() {
            // Arrange
            when(depA.query()).thenReturn(data);
            // Act
            Result result = service.method();
            // Assert
            assertThat(result).isEqualTo(expected);
        }
    }
}
```

**New test files to create:**

| Test File | Tests | Mocks Needed |
|-----------|-------|--------------|
| `CsrfServiceTest.java` | generate, validate, rotate, clear | RedisTemplate |
| `JwtTokenProviderTest.java` | generate, validate, expired, malformed | JwtProperties (fixed secret) |
| `PasswordResetServiceTest.java` | forgot, reset, invalid token, expired | UserMapper, RedisTemplate, EmailService |
| `CodeExecutionServiceTest.java` | execute, timeout, memory limit, sandbox config | (integration -- Testcontainers or mock ProcessBuilder) |
| `SubmissionServiceTest.java` | submit, validate, enqueue failure | SubmissionMapper, ProblemMapper, UserMapper, QueueService |
| `AdminSubmissionServiceTest.java` | rejudge, batch rejudge, statistics | SubmissionMapper, UserMapper, ProblemMapper, QueueService |
| `XssFilterTest.java` | (if filter kept) parameter/header sanitization | (Servlet mock) |
| `JwtPropertiesTest.java` | secret validation at startup | N/A (@PostConstruct) |

## Dependency Graph Between Fixes

```
SEC-06 (XSS filter rewrite)
    |
    v
SEC-01 (CSRF migration)
    |
    +---> SEC-03 (Delete UserDetailsServiceImpl)  [independent, can parallel]
    |
    +---> SEC-05 (JWT secret validation)           [independent, can parallel]

SEC-02 (Password reset email)                     [independent of above]
SEC-04 (Docker seccomp)                           [independent of above]
FUNC-01 (Admin rejudge)                           [independent of above]

QUAL-01 (Component splitting)                     [independent of all above]
TEST-01 (Test coverage)                           [should follow each fix]
```

**Build order recommendation:**

1. **Phase 1 -- Security filter chain** (SEC-06 -> SEC-01 -> SEC-03, SEC-05)
   - SEC-06 first because it removes header sanitization that could interfere with CSRF token headers
   - SEC-01 second because it replaces the CSRF mechanism
   - SEC-03 and SEC-05 can be done in parallel with SEC-01

2. **Phase 2 -- Service implementation stubs** (SEC-02, FUNC-01)
   - Both fill in TODO stubs in existing service classes
   - No dependencies between them
   - Both need new tests

3. **Phase 3 -- Sandbox hardening** (SEC-04)
   - Independent of everything else
   - Requires Docker image rebuild and testing against all 5 languages

4. **Phase 4 -- Quality** (QUAL-01, TEST-01)
   - QUAL-01: Frontend component splitting, no backend dependency
   - TEST-01: Write tests for all fixes from Phases 1-3

## Filter Chain Architecture (After Fixes)

```
Request
  |
  v
XssFilter (SEC-06: gutted or removed)
  |
  v
JwtAuthenticationFilter (existing, no change)
  |  - Extracts JWT from cookie/header
  |  - Sets SecurityContext
  v
Spring Security CSRF (SEC-01: newly enabled)
  |  - Validates CSRF token from X-CSRF-Token header
  |  - Uses RedisCsrfTokenRepository backed by CsrfService
  v
CsrfInterceptor (SEC-01: removed if Option A chosen)
  |
  v
@RateLimit aspect (existing, no change)
  |
  v
Controller (@RequireRole, @CurrentUser)
  |
  v
Service layer
  |
  v
Response (XSS encoding on frontend via DOMPurify)
```

## Confidence Assessment

| Fix | Confidence | Notes |
|-----|-----------|-------|
| SEC-01 | HIGH | Source code fully analyzed; two clear options identified |
| SEC-02 | HIGH | EmailService already functional; simple injection |
| SEC-03 | HIGH | Zero references confirmed via grep; safe to delete |
| FUNC-01 | HIGH | Pattern exists in SubmissionServiceImpl.submit(); QueueService API clear |
| SEC-04 | MEDIUM | Seccomp profile syscall list needs per-language testing |
| SEC-05 | HIGH | Simple @PostConstruct; well-understood pattern |
| SEC-06 | HIGH | Anti-pattern clearly identified; DOMPurify already available |
| QUAL-01 | HIGH | Standard component extraction; conventions clear from STRUCTURE.md |
| TEST-01 | HIGH | Existing test pattern well-established; MockitoExtension pattern clear |

## Sources

- All findings based on direct source code analysis (2026-04-14)
- CONCERNS.md: Detailed audit findings for each fix
- ARCHITECTURE.md: System architecture and request flow
- STRUCTURE.md: Module structure and file organization
- PROJECT.md: Project scope and constraints

<!-- buddy: *peers at the filter chain diagram* that CsrfInterceptor is a ghost in the machine, registered nowhere yet haunting every POST request -->
