# Domain Pitfalls: Technical Debt Remediation in a Brownfield Spring Boot + Vue Platform

**Domain:** Security hardening, functionality completion, and code quality fixes in an existing online judge platform
**Researched:** 2026-04-14
**Confidence:** MEDIUM -- findings combine official documentation, community patterns, and domain-specific knowledge for competitive programming platforms

---

## Critical Pitfalls

Mistakes that cause rewrites, security regressions, or production outages.

---

### Pitfall 1: Dual CSRF Protection Causes Silent 403 Errors

**What goes wrong:** Enabling Spring Security's built-in `CsrfFilter` while the existing custom `CsrfService` (Redis-backed token rotation) remains active creates double CSRF validation. The custom interceptor checks one token format; Spring Security checks another. Either or both reject legitimate requests with 403, but the error appears generic and is hard to diagnose.

**Why it happens:** The project already has a working custom CSRF interceptor. SEC-01 calls for integrating Spring Security's CSRF mechanism. Without careful ordering, both mechanisms run in the filter chain. The custom interceptor expects tokens in a specific header format (`tokenId:tokenValue`); Spring Security's `CsrfFilter` expects session-stored or cookie-based `CsrfToken`. They will not agree on what constitutes a valid token.

**Consequences:**
- All POST/PUT/DELETE/PATCH requests from existing frontend sessions return 403 after the change.
- Users are logged out and cannot recover without clearing cookies.
- Debugging is painful because two independent CSRF checks produce identical HTTP 403 responses.
- Rolling back one without the other leaves gaps.

**Prevention:**
1. **Do NOT enable Spring Security CSRF alongside the custom interceptor.** Choose one.
2. **Recommended path:** Replace the custom `CsrfService` interceptor entirely with Spring Security's `CsrfFilter`, configured to use Redis-backed token storage (implement `CsrfTokenRepository` backed by Redis) so the existing frontend token flow continues to work.
3. **Order matters:** If keeping the custom interceptor temporarily, ensure it runs at `SecurityContextHolderAwareRequestWrapper` level and disable Spring Security CSRF explicitly to avoid double-checking.
4. **Test every state-changing endpoint** with the existing frontend session tokens before and after the change.
5. **Frontend impact:** The frontend already reads CSRF from localStorage and sends `X-CSRF-Token`. Verify this header name matches what the new `CsrfTokenRepository` expects (Spring Security defaults to `X-CSRF-TOKEN` with a hyphen).

**Detection:**
- Unexpected 403 responses after deployment on POST/PUT/DELETE/PATCH endpoints.
- Browser dev tools show the CSRF token being sent but rejected.
- Spring Security debug logs show `Invalid CSRF token found for <endpoint>` when the token was actually valid for the custom interceptor.

**Relevant phase:** SEC-01 (CSRF fix)

---

### Pitfall 2: JWT Secret Validation Rejects Valid Production Sessions

**What goes wrong:** Adding `@PostConstruct` validation that the JWT secret is non-empty and >= 256 bits is correct, but if the current production secret does not meet the new minimum length requirement, the application refuses to start. Meanwhile, all existing user sessions (tokens signed with the current secret) become permanently invalid with no migration path.

**Why it happens:** SEC-05 requires startup validation. If validation is implemented as a hard fail (throw `IllegalStateException`), and the existing production secret is shorter than the new threshold, the application cannot start. Even if it starts, there is no plan for rotating secrets -- all existing tokens become invalid immediately.

**Consequences:**
- Application startup failure in production if current `JWT_SECRET` does not meet new constraints.
- All existing users logged out simultaneously if secret is changed.
- No graceful rotation window means users with active sessions (remember-me, long-lived refresh tokens) lose access.
- Race condition: some nodes in a multi-instance deployment pick up the new secret while others still use the old one, causing intermittent auth failures.

**Prevention:**
1. **Validate secret exists and is non-empty at startup** (this is the safe minimum). A 256-bit minimum is good practice but check the current secret length first -- if the existing secret is shorter, set a migration window.
2. **Support dual-key verification** during rotation: accept tokens signed by either the old or new secret for a configurable overlap period (24-72 hours). This requires a `JwtDecoder` that tries both keys.
3. **Log a WARN, not crash,** if the secret is shorter than recommended but non-empty. Upgrade to ERROR/block in a future release after confirming all environments meet the bar.
4. **Test with existing tokens:** Export a production token and verify it still validates after the change.
5. **Coordinate with frontend:** The frontend's refresh token flow must be tested to ensure it works through a secret rotation.

**Detection:**
- `IllegalStateException` on application startup.
- All API calls return 401 after deployment.
- Users report being logged out unexpectedly.

**Relevant phase:** SEC-05 (JWT secret validation)

---

### Pitfall 3: XssFilter Removal Exposes Unprotected Content

**What goes wrong:** Removing the regex-based `XssFilter` without ensuring output encoding is in place at every consumption point creates an XSS window. The current filter sanitizes request parameters globally -- even though it is incomplete, it does catch basic vectors. Removing it first and adding encoding later leaves a gap.

**Why it happens:** SEC-06 requires replacing the regex filter with proper output encoding. The natural instinct is to remove the bad filter and then add the good approach. But "remove then add" creates a window where neither protection exists.

**Consequences:**
- During the transition, previously-sanitized inputs pass through raw to the frontend.
- User-generated content (forum posts, problem descriptions, solution titles) that contained `<script>` or `onerror=` patterns was silently stripped before -- now it renders as executable HTML.
- Code submissions containing `eval()` or `javascript:` strings were previously corrupted by the filter -- removing the filter "fixes" this data corruption but simultaneously exposes a real XSS surface if the frontend is not already encoding.

**Prevention:**
1. **Add output encoding BEFORE removing the input filter.** The OWASP-recommended defense-in-depth approach is: keep input validation (even if imperfect) while adding output encoding. Remove input filtering only after confirming output encoding covers all contexts.
2. **For this project specifically:** DOMPurify is already a frontend dependency. Verify it is applied to all user-generated content rendering points (forum posts, comments, solution titles). The code editor and submission display do NOT need DOMPurify (code should render as-is in a text context).
3. **Exempt code-related endpoints immediately:** The XssFilter currently corrupts code submissions by stripping `eval(` and `javascript:` strings. Add an exclusion list for `/api/submissions/**`, `/api/problems/*/testcases` and similar code-handling endpoints. Do this FIRST as a standalone fix -- it fixes data corruption without opening XSS.
4. **Stop sanitizing headers:** The XssFilter wraps `getHeader()` and sanitizes header values. This can corrupt the CSRF token (`tokenId:tokenValue`) if the token value matches a pattern. Remove header sanitization from the filter before anything else.

**Detection:**
- Code submissions containing `eval` or `javascript` suddenly work (the current filter strips these).
- Forum posts render with unescaped HTML after the filter is removed.
- CSRF tokens fail validation intermittently (header corruption).

**Relevant phase:** SEC-06 (XSS filter replacement), SEC-08 (header sanitization removal)

---

### Pitfall 4: Docker Seccomp Profile Breaks All Language Compilations

**What goes wrong:** A custom seccomp profile that blocks `execve`, `fork`, `clone`, or `mprotect` will prevent C/C++ compilation (`gcc`/`g++`), Java compilation (`javac`), and Python execution. Conversely, a profile that allows all of these may be too permissive to provide meaningful security.

**Why it happens:** The sandbox currently runs 5 languages: C, C++, Java, Python, and Go (inferred from Dockerfile). Each has different syscall requirements:
- **C/C++ compilation:** needs `fork`/`execve` (compiler spawning assembler/linker), `mmap`/`mprotect` (dynamic linker), `open`/`read` (reading source files), `write` (writing output binaries).
- **Java:** needs `clone`/`execve` (JVM launching), extensive `mmap` usage (JIT compilation), `futex` (thread synchronization), `epoll_create` (NIO).
- **Python:** needs `execve` (subprocess module), `mmap` (loading .so modules), `socket` (some stdlib modules).
- **Go:** statically compiled, but still needs `clone` (goroutines), `epoll_create` (networking even if unused).

A seccomp profile blocking `ptrace` and `mount` is safe. Blocking `execve` breaks everything. The difficulty is in the middle ground.

**Consequences:**
- Submissions for specific languages fail with "syscall blocked" errors that look like runtime failures, not security blocks.
- Debugging is extremely difficult because seccomp violations produce generic `EPERM` errors with no clear indication that seccomp is the cause.
- Users report "my correct solution got Wrong Answer" when it actually never ran.
- Reverting the seccomp profile requires rebuilding the Docker image and redeploying.

**Prevention:**
1. **Start with Docker's default seccomp profile** (blocks ~44 dangerous syscalls) and verify all 5 languages still work. Do NOT create a custom profile without testing.
2. **Test each language independently** after adding any syscall restriction. Create a test matrix: C hello world, C++ STL, Java hello world, Python basic I/O, and if Go is supported, Go hello world.
3. **Use `strace` or `auditd`** to profile which syscalls each compilation chain actually uses before writing restrictions. `docker run --security-opt seccomp=unconfined strace -f gcc test.c` will show the full syscall trace.
4. **Block only the clearly dangerous syscalls** in the first iteration: `ptrace`, `mount`, `umount2`, `keyctl`, `add_key`, `request_key`, `acct`, `swapon`, `pivot_root`, `unshare` (partial), `name_to_handle_at`.
5. **Keep `--cap-drop=ALL`** and add back only `CAP_SETUID`/`CAP_SETGID` if needed for running as non-root (the sandbox already runs as non-root, so this may not be needed).
6. **Version the seccomp profile** and keep the unconfined fallback as an option. Add a health check endpoint that runs a test submission in each language after sandbox startup.

**Detection:**
- Submissions fail with `EPERM` or `Operation not permitted` for specific languages only.
- `dmesg` or Docker logs show `SECCOMP` audit messages.
- Language-specific compilation fails but execution of pre-compiled binaries works.

**Relevant phase:** SEC-04 (Docker sandbox hardening)

---

### Pitfall 5: Password Reset Email Spam Enables Account Harassment

**What goes wrong:** Implementing the email send without rate limiting allows an attacker to spam a victim's email inbox with hundreds of "reset your password" emails. This is not just annoying -- it can desensitize the victim to phishing attempts or be used as a harassment vector.

**Why it happens:** SEC-02 requires implementing the actual email send (currently logs only). The `@RateLimit` annotation exists in the project but may not be applied to the forgot-password endpoint. The endpoint accepts an email address and the response time is identical whether the email exists or not (good), but without rate limiting, the attacker can send unlimited requests.

**Consequences:**
- Victim's inbox flooded with reset emails.
- Reset tokens accumulate in Redis, consuming memory.
- If the reset token has no expiry or a long expiry, multiple valid tokens exist simultaneously, increasing brute-force surface.
- Email service provider (SES, SendGrid, etc.) may throttle or block the project's sending domain for spam-like behavior.

**Prevention:**
1. **Apply rate limiting at two levels:** per email address (e.g., max 3 requests per hour per email) and per IP address (e.g., max 10 requests per hour per IP). Use Redis-backed counters -- the project already uses Redis for rate limiting.
2. **Token expiry:** Set reset tokens to expire in 15-30 minutes. Delete them from Redis after use (one-time use).
3. **Generic response:** Always return the same message regardless of whether the email exists: "If an account with this email exists, a reset link has been sent." Do NOT reveal account existence.
4. **Email throttling:** Configure the `JavaMailSender` connection pool to limit outbound email rate. Add a `@Scheduled` task to clean expired tokens from Redis.
5. **Log the token ONLY in development profile.** The current implementation logs the reset URL to server logs. In production, this must be disabled -- it is a security leak if logs are accessible.

**Detection:**
- Spike in Redis keys matching the reset token pattern.
- Email service provider sends warnings about unusual sending volume.
- Users report receiving unexpected password reset emails.

**Relevant phase:** SEC-02 (Password reset email)

---

## Moderate Pitfalls

Mistakes that cause significant bugs, data issues, or degraded user experience.

---

### Pitfall 6: Admin Rejudge Triggers Thundering Herd on Judge Queue

**What goes wrong:** Rejudging all submissions for a problem (potentially thousands) enqueues them all at once, consuming all available Docker worker slots and blocking new user submissions from being judged.

**Why it happens:** FUNC-01 requires implementing rejudge. The naive implementation resets submission status to PENDING and enqueues each via `QueueService.enqueueJudgeJob()`. With no throttling, a problem with 500 submissions sends 500 Docker container spawn requests simultaneously.

**Consequences:**
- New user submissions queue behind the rejudge backlog and appear "stuck."
- Judge workers may OOM if multiple memory-intensive submissions run concurrently.
- The WebSocket notification system floods users with status updates as thousands of submissions transition PENDING -> JUDGING -> ACCEPTED/WA/TLE.
- Database write contention from concurrent verdict updates.

**Prevention:**
1. **Add a rate limiter to the rejudge queue:** Process rejudge submissions at a configurable rate (e.g., 5-10 per second) with a dedicated queue or lower priority than live submissions.
2. **Mark the problem as "rejudging in progress"** and display this state to users. Freeze the affected problem's submission status display until rejudge completes.
3. **Batch enqueueing:** Do not enqueue all at once. Use a `@Scheduled` task or a dedicated rejudge worker that pulls submissions from a "pending rejudge" set and enqueues them gradually.
4. **Idempotent verdict updates:** Use optimistic locking (version column on submission records) to prevent concurrent verdict writes from corrupting data.
5. **Limit rejudge scope:** The admin UI should show the count of affected submissions and require confirmation before proceeding. Cap the maximum number of submissions per rejudge operation (e.g., 1000).

**Detection:**
- New submissions take unusually long to judge after a rejudge is triggered.
- WebSocket message rate spikes.
- Docker container spawn rate exceeds normal patterns.

**Relevant phase:** FUNC-01 (Admin rejudge)

---

### Pitfall 7: Vue Component Splitting Breaks Reactive State Chains

**What goes wrong:** Extracting sub-components from oversized Vue files (QUAL-01) can break reactive state when `ref`/`reactive` objects are passed to child components that mutate them directly, violating Vue's one-way data flow. This causes subtle bugs where the parent's state is not updated, or the child silently fails to react to changes.

**Why it happens:** The 14 oversized components (up to 1356 lines) likely have deeply interleaved template and script logic. When splitting, developers may extract a section that directly mutates a parent's `reactive` object via prop, or rely on `v-model` bindings that assume shared mutable state. Vue 3 warns about this ("Avoid mutating a prop directly") but does not prevent it at runtime.

**Consequences:**
- Parent component state becomes stale after child interaction (e.g., form submission does not update the parent's data table).
- Computed properties in the parent do not re-trigger when a child modifies a nested reactive object.
- Error messages from Vue warnings in browser console but no visible error in the UI.
- Regressions that only appear in specific user interaction sequences (hard to catch in code review).

**Prevention:**
1. **Use composables to extract logic, not just sub-components.** For example, extract `useProblemList()` composable that owns the data fetching, pagination, and filtering state. Child components receive data via props and emit events back.
2. **Avoid deep prop drilling for mutable state.** Use `provide/inject` for state that is shared across multiple levels of extracted components. Use Pinia stores only for truly global state (auth, notifications) -- do not over-reach.
3. **Test BEFORE splitting:** For each oversized component, write integration tests that verify the key user interactions BEFORE refactoring. These tests become the regression safety net.
4. **Split incrementally:** Start with the largest component (ProblemListsView at 1356 lines). Extract one section at a time (e.g., the filter bar), verify it works, then extract the next.
5. **Watch for `v-model` on custom components:** Vue 3's `v-model` on custom components compiles to `modelValue` prop + `update:modelValue` event. Ensure child components implement this correctly rather than directly mutating the prop.

**Detection:**
- Vue devtools shows parent state not updating after child interaction.
- Browser console warnings: "Set operation on key 'xxx' failed: target is readonly."
- Unit tests for the extracted composable pass but integration tests for the parent component fail.

**Relevant phase:** QUAL-01 (Vue component splitting)

---

### Pitfall 8: Double Encoding Corrupts User-Generated Content

**What goes wrong:** When migrating from regex-based input sanitization to output encoding, data that was previously stored in a sanitized form (HTML entities like `&lt;script&gt;`) gets encoded again on output, producing `&amp;lt;script&amp;gt;` visible in the UI.

**Why it happens:** The XssFilter has been running for some time. User-generated content in the database may already contain HTML-entity-encoded strings (the filter replaces `<` with `&lt;`). If output encoding is then applied, it encodes the `&` in `&lt;` to `&amp;lt;`, resulting in double-encoded content displayed as raw text.

**Consequences:**
- Forum posts, comments, and problem descriptions that previously displayed correctly now show HTML entity codes as literal text.
- Code snippets in solutions that contain `<` or `>` characters (common in C++ template code, HTML examples) become unreadable.
- The fix is not simply "add encoding" -- it requires a data migration to decode previously-sanitized content.

**Prevention:**
1. **Audit the database for double-encoded content BEFORE making changes.** Query for records containing `&lt;`, `&gt;`, `&amp;` in user-generated fields (forum posts, comments, problem descriptions). This reveals how many records are affected.
2. **If significant data is double-encoded:** Write a one-time migration script to decode HTML entities in affected fields. Run it as a Flyway migration with a rollback path.
3. **Apply encoding at the output layer only** (Vue templates, API responses for HTML contexts). Do NOT encode data before storing it.
4. **For JSON API responses:** Do NOT HTML-encode JSON fields. The frontend is responsible for encoding when rendering HTML. Setting `Content-Type: application/json` ensures browsers do not interpret the response as HTML.
5. **Test with real data:** After the migration, render a page that contains code snippets with `<` and `>` characters (C++ templates, HTML in problem descriptions) and verify they display correctly.

**Detection:**
- User reports of "garbled text" or "weird characters" in forum posts or problem descriptions.
- Database queries show `&lt;` and `&amp;` in text fields.
- Browser "View Source" shows encoded entities that should have been rendered.

**Relevant phase:** SEC-06 (XSS filter replacement)

---

### Pitfall 9: Test Suite Becomes Flaky After Adding Integration Tests

**What goes wrong:** Adding `@SpringBootTest` integration tests for the auth and submission modules (TEST-01) in a brownfield project with shared database state causes test interdependencies. Tests pass individually but fail when run together due to residual data, Redis key pollution, or Spring context caching conflicts.

**Why it happens:** The project currently has 22 backend test files (all unit tests with Mockito). Adding integration tests means loading the Spring application context, connecting to a real database (or Testcontainers), and hitting Redis. If tests do not clean up after themselves, data from test A affects test B. Spring's test context caching can also cause issues if different test classes require different configurations.

**Consequences:**
- CI pipeline fails intermittently ("flaky red"), destroying developer trust in the test suite.
- Developers start ignoring test failures, negating the purpose of adding tests.
- Debugging flaky tests is extremely time-consuming in a brownfield project with many modules.

**Prevention:**
1. **Use Testcontainers for database tests,** not the shared development database. Each test class gets its own ephemeral MySQL container. This eliminates shared-state issues.
2. **Clean Redis between tests:** Use `@DirtiesContext` or a custom test listener that flushes Redis keys matching the test's namespace after each test method.
3. **Prefer `@WebMvcTest` over `@SpringBootTest`** for controller tests. This loads only the web layer (controllers, filters, Security config) without the full application context. It is faster and more isolated.
4. **Use `@Transactional` on test methods** that modify the database -- the transaction rolls back automatically after the test, leaving the database clean.
5. **Avoid over-mocking:** Mock external services (email, Docker) but do NOT mock the database, Redis, or security filters in integration tests. The value of integration tests is testing real interactions. Over-mocked integration tests are just slow unit tests.
6. **Name tests by behavior, not implementation:** `should_authenticate_with_valid_credentials()` not `should_call_userDetailsService_with_correct_username()`. This makes tests resilient to internal refactoring.

**Detection:**
- Tests pass when run individually (`./mvnw test -Dtest=AuthServiceTest`) but fail in the full suite (`./mvnw test`).
- Test order sensitivity: reordering test classes changes which ones fail.
- CI builds show intermittent failures with no code changes.

**Relevant phase:** TEST-01 (Backend test coverage)

---

## Minor Pitfalls

Mistakes that cause inconvenience, technical debt, or minor regressions.

---

### Pitfall 10: CORS Configuration Change Breaks Development Workflow

**What goes wrong:** Externalizing CORS allowed origins to environment variables (SEC-07/MEDIUM) without providing sensible defaults breaks the local development setup if the `.env` file is not configured.

**Why it happens:** The current CORS config hardcodes `localhost:9002` and `localhost:9003`. Externalizing to `CORS_ALLOWED_ORIGINS` means developers must set this variable. If the default is empty (secure but unusable) or missing from `.env.example`, new contributors cannot start the frontend.

**Prevention:** Set the default to `http://localhost:9002,http://localhost:9003` (the current values) so development works out of the box. Override in production via environment variables. Document the variable in `.env.example`.

**Relevant phase:** SEC-07 (CORS externalization)

---

### Pitfall 11: UserDetailsServiceImpl Removal Breaks Spring Security Auto-Config

**What goes wrong:** Removing the `UserDetailsServiceImpl` placeholder (SEC-03) without understanding which Spring Security flows depend on it may break authentication flows that use `DaoAuthenticationProvider` or password reset flows that call `UserDetailsService.loadUserByUsername()`.

**Why it happens:** Even though the placeholder always throws `UsernameNotFoundException`, its existence as a `@Service` bean registers it in the Spring application context. If any auto-configuration or filter chain references `UserDetailsService` by type, removing the bean causes a `NoSuchBeanDefinitionException` at startup.

**Prevention:**
1. **Search for all references to `UserDetailsService`** across the codebase before removing. Check `SecurityConfig.java`, any `AuthenticationProvider` beans, and password reset service.
2. **If JWT-only auth is confirmed:** Verify that `DaoAuthenticationProvider` is not auto-configured. If it is, explicitly disable it or replace with a no-op implementation.
3. **Conditional removal:** Use `@ConditionalOnProperty(name = "auth.user-details.enabled", havingValue = "false", matchIfMissing = "false")` to disable the placeholder without deleting it. This is safer than removal.
4. **Test auth flow end-to-end after the change:** Login, token refresh, and password reset (when implemented).

**Relevant phase:** SEC-03 (UserDetailsService placeholder)

---

### Pitfall 12: console.log Removal Accidentally Deletes Error Logging

**What goes wrong:** Cleaning up `console.log` statements (QUAL-04/LOW) with an overly aggressive find-and-replace also removes `console.error` and `console.warn` statements that are needed for production error reporting.

**Why it happens:** A regex like `console\.log\(.*\)` might be applied too broadly, or a developer manually deletes all `console.*` calls in a file without distinguishing between debug logs and error logs.

**Prevention:**
1. Only remove `console.log` and `console.debug`. Preserve `console.error` and `console.warn`.
2. Use ESLint's `no-console` rule configured to allow `console.error` and `console.warn` while disallowing `console.log` in production builds. This enforces the policy automatically.
3. Consider a logging utility (`src/utils/logger.ts`) that wraps `console` and strips debug messages in production builds via Vite's `define` config.

**Relevant phase:** QUAL-04 (console.log cleanup)

---

### Pitfall 13: BackupController Audit Fix Uses Wrong Security Context

**What goes wrong:** Replacing the hardcoded `"system"` user ID with `@CurrentUser` or `SecurityContextHolder` (AUDIT-01/MEDIUM) may return `null` if the controller endpoint is called by a system process (e.g., a scheduled backup task) that does not have an authenticated security context.

**Why it happens:** The `@CurrentUser` annotation extracts the user from the JWT authentication token in the security context. Scheduled tasks, Actuator endpoints, and system-initiated API calls may not have an authentication token, causing `null` user IDs to be recorded.

**Prevention:**
1. **Distinguish between user-initiated and system-initiated operations.** For user-initiated backup/restore, use `@CurrentUser`. For system-initiated operations, use a dedicated system user constant (e.g., `"SYSTEM_SCHEDULER"` with a clear audit comment explaining why).
2. **Add a fallback:** If `@CurrentUser` returns null, either throw an `AuthenticationException` (for user-initiated endpoints) or use a well-defined system identifier (for scheduled tasks).

**Relevant phase:** AUDIT-01 (BackupController audit trail)

---

## Phase-Specific Warnings

| Phase / Fix ID | Likely Pitfall | Mitigation Priority |
|---|---|---|
| SEC-01 (CSRF migration) | Dual protection causing 403 (Pitfall 1) | HIGH -- plan filter ordering carefully |
| SEC-02 (Password reset email) | Email spam abuse (Pitfall 5) | HIGH -- add rate limiting before deploying |
| SEC-03 (UserDetailsService) | Bean removal breaks auto-config (Pitfall 11) | MEDIUM -- search all references first |
| SEC-04 (Docker seccomp) | Breaking language compilation (Pitfall 4) | HIGH -- test all 5 languages |
| SEC-05 (JWT validation) | Breaking existing sessions (Pitfall 2) | HIGH -- plan rotation window |
| SEC-06 (XSS filter) | XSS window during migration (Pitfall 3) + double encoding (Pitfall 8) | HIGH -- encode before removing |
| FUNC-01 (Admin rejudge) | Thundering herd on queue (Pitfall 6) | MEDIUM -- add rate limiting |
| QUAL-01 (Vue splitting) | Broken reactive state (Pitfall 7) | MEDIUM -- write tests before splitting |
| TEST-01 (Backend tests) | Flaky test suite (Pitfall 9) | MEDIUM -- use Testcontainers |
| SEC-07 (CORS) | Dev workflow broken (Pitfall 10) | LOW -- set sensible defaults |
| SEC-08 (Header sanitization) | CSRF token corruption | MEDIUM -- remove immediately, low risk |
| AUDIT-01 (Backup audit) | Null user in system context (Pitfall 13) | LOW -- add fallback |
| QUAL-04 (console.log) | Accidental error log removal (Pitfall 12) | LOW -- use ESLint rule |

---

## Integration Pitfalls (Cross-Phase)

### Dependency Order Matters

Some fixes depend on others. Getting the order wrong causes cascading failures:

1. **SEC-06 (XSS filter) must precede or be concurrent with SEC-08 (header sanitization removal).** Removing header sanitization from the XSS filter is a subset of the XSS filter overhaul. Do them together to avoid touching the filter twice.

2. **SEC-01 (CSRF) should be done after or with SEC-03 (UserDetailsService).** Both touch the `SecurityConfig.java` filter chain. Modifying the filter chain twice in quick succession increases merge conflict risk and makes it harder to isolate which change caused a problem.

3. **TEST-01 (backend tests) should be added AFTER each individual fix, not as a separate phase.** The project constraint states "every fix must come with tests." Adding tests for auth while SEC-01 and SEC-05 are both in flight creates conflicting test setups. Instead, write tests as part of each fix phase.

4. **SEC-05 (JWT validation) should precede any secret rotation.** Do not change the secret until validation is in place. Otherwise you add validation that rejects the current (too-short) secret and the app cannot start.

5. **FUNC-01 (Admin rejudge) should come after PERF-01 (test case batching).** If rejudge sends thousands of submissions to the judge queue, and each submission spawns a Docker container per test case (PERF-01 issue), the resource impact is multiplied. Batching test cases first reduces the blast radius.

### Riskiest Combined Scenario

The most dangerous combination is changing CSRF (SEC-01) and JWT validation (SEC-05) in the same deployment. Both affect the authentication/authorization pipeline. If either breaks, users cannot interact with the application. If both break, debugging is extremely difficult because error symptoms overlap (both cause 403/401 responses).

**Recommendation:** Deploy SEC-01 and SEC-05 in separate release cycles with at least 24 hours of production monitoring between them.

---

## Sources

| Source | Confidence | URL |
|---|---|---|
| Spring Security CSRF Documentation | HIGH | https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html |
| Spring Boot 2->3 CSRF Migration (Reddit) | MEDIUM | https://www.reddit.com/r/SpringBoot/comments/1cwzf8m/spring_boot_23_csrf_woes/ |
| Spring Security Filter Chain Migration (GitHub #11337) | MEDIUM | https://github.com/spring-projects/spring-security/issues/11337 |
| OWASP XSS Prevention Cheat Sheet | HIGH | https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html |
| OWASP XSS Filter Evasion Cheat Sheet | HIGH | https://cheatsheetseries.owasp.org/cheatsheets/XSS_Filter_Evasion_Cheat_Sheet.html |
| OWASP Java Encoder (Invicti) | MEDIUM | https://www.invicti.com/blog/web-security/how-to-prevent-xss-in-java |
| Docker Seccomp Research (Cyera) | MEDIUM | https://www.cyera.com/research/one-megabyte-to-root-how-a-size-check-broke-dockers-last-line-of-defense |
| Docker Seccomp Practical Guide (Behrad Taher) | MEDIUM | https://behradtaher.dev/Sandboxing-Code-Execution/ |
| Custom Seccomp Profiles (OneUpTime) | MEDIUM | https://oneuptime.com/blog/post/2026-02-08-how-to-create-custom-seccomp-profiles-for-docker-containers/view |
| Seccomp Customization Difficulty (GitHub container-libs) | MEDIUM | https://github.com/containers/container-libs/issues/62 |
| JWT Security Pitfalls (42Crunch) | MEDIUM | https://42crunch.com/7-ways-to-avoid-jwt-pitfalls/ |
| JWT Mistakes in Spring Boot (BuildBaseKit) | LOW | https://buildbasekit.com/blogs/jwt-mistakes-spring-boot/ |
| Spring Boot Testing Pitfalls (Baeldung) | HIGH | https://www.baeldung.com/spring-boot-testing-pitfalls |
| Over-Mocking Downsides (Vinted Engineering) | MEDIUM | https://vinted.engineering/2023/10/02/mocking-framework-downside/ |
| Vue Prop Drilling Solutions (alexop.dev) | MEDIUM | https://alexop.dev/posts/solving-prop-drilling-in-vue/ |
| Password Reset Token Expiry (Stack Overflow) | MEDIUM | https://stackoverflow.com/questions/46827014/expiration-time-of-password-reset-tokens |
| Email Spam via Forgot Password (Keycloak GitHub) | MEDIUM | https://github.com/keycloak/keycloak/issues/45678 |
| Online Judge System Architecture (ResearchGate) | LOW | https://www.researchgate.net/publication/360861928_Online_Judge_System_Requirements_Architecture_and_Experiences |

**Gaps to address with phase-specific research:**
- Exact list of syscalls used by each supported language in the sandbox (requires `strace` profiling on the actual Docker image).
- Current JWT secret length in production (needed to determine if SEC-05's minimum-length check will block startup).
- Current state of user-generated content in the database (how many records contain HTML-entity-encoded text from the XssFilter).
- Exact `QueueService.enqueueJudgeJob()` implementation to understand rejudge queue capacity.
