# Technology Stack -- Security Fixes & Testing Milestone

**Project:** UltiCode Technical Debt v1.0
**Researched:** 2026-04-14
**Mode:** Stack additions/changes for 9 critical/high fixes

## Executive Summary

This milestone requires **zero new runtime dependencies** and **three new test-scoped dependencies**. The 9 fixes are achievable by leveraging already-installed libraries more effectively and adding test infrastructure only. The PROJECT.md constraint "no new dependencies" is largely achievable for production code -- the only addition needed is `org.owasp.encoder:encoder` to replace the regex-based XssFilter, which is a direct security dependency justified by the fix itself.

The existing stack already has `spring-boot-starter-security`, `spring-boot-starter-data-redis`, `jjwt 0.12.5`, `spring-boot-starter-mail`, and `spring-boot-starter-test`+`spring-security-test`. These cover all CSRF, JWT validation, email, and backend testing needs without adding new production dependencies (except the OWASP encoder).

---

## Fix-by-Fix Stack Analysis

### SEC-01: CSRF Spring Security Framework Integration

**Current state:**
- `SecurityConfig.java:90` calls `AbstractHttpConfigurer::disable` -- CSRF completely disabled at the framework layer.
- Custom `CsrfInterceptor` (HandlerInterceptor) registered via `WebMvcConfig` validates `X-CSRF-Token` header against Redis-backed `CsrfService`.
- `CsrfService` already implements token generation, validation, rotation, and cleanup with Redis (24h TTL).
- Frontend sends CSRF via `X-CSRF-Token` header (read from localStorage).

**Recommended approach: No new dependencies.**

Implement a custom `CsrfTokenRepository` that delegates to the existing `CsrfService`. This integrates the existing Redis-backed token logic into Spring Security's CSRF filter chain while preserving the current frontend contract (`X-CSRF-Token` header).

```java
// New class: RedisCsrfTokenRepository implements CsrfTokenRepository
// Delegates to existing CsrfService for load/save/generate/validate
```

**Key integration points:**
- Enable CSRF in `SecurityConfig`: remove `AbstractHttpConfigurer::disable`, configure `.csrf(csrf -> csrf.csrfTokenRepository(redisCsrfTokenRepository()))`
- Remove the separate `CsrfInterceptor` from `WebMvcConfig` (Spring Security's filter handles it)
- Frontend contract unchanged: still sends `X-CSRF-Token` header
- Existing `CsrfService.generateToken()`, `validateAndRotateToken()`, `clearUserTokens()` remain the backing implementation

**Why this works without new deps:** `spring-boot-starter-security` already includes `CsrfTokenRepository`, `DefaultCsrfToken`, `CsrfFilter` -- all the framework plumbing is present. The custom repository is ~80 lines of glue code.

| Technology | Version | Purpose | Source |
|-----------|---------|---------|--------|
| spring-boot-starter-security | 3.5.12 (managed) | CsrfTokenRepository interface, CsrfFilter | Already in pom.xml |

---

### SEC-05: JWT Secret Startup Validation

**Current state:**
- `application.yml:47` defines `jwt.secret: ${JWT_SECRET:}` with empty default.
- `JwtProperties` has no validation -- `secret` can be null or empty string.
- `JwtTokenProvider.getSigningKey()` calls `jwtProperties.getSecret().getBytes()` which throws NPE if secret is null.

**Recommended approach: No new dependencies.**

Add `@PostConstruct` validation in `JwtTokenProvider` (or a dedicated `JwtSecretValidator` component):

```java
@PostConstruct
void validateSecret() {
    String secret = jwtProperties.getSecret();
    if (secret == null || secret.isBlank()) {
        throw new IllegalStateException(
            "JWT secret is not configured. Set JWT_SECRET environment variable. " +
            "Generate one with: openssl rand -base64 32");
    }
    if (secret.length() < 32) {
        throw new IllegalStateException(
            "JWT secret must be at least 256 bits (32 characters). Current length: " + secret.length());
    }
    // Verify the secret actually produces a valid HMAC key
    Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
}
```

| Technology | Version | Purpose | Source |
|-----------|---------|---------|--------|
| jjwt-api/impl | 0.12.5 | `Keys.hmacShaKeyFor()` for key validation | Already in pom.xml |
| javax.annotation (Jakarta) | managed by Spring Boot | `@PostConstruct` | Already available |

---

### SEC-06: XSS Output Encoding (Replace Regex-Based XssFilter)

**Current state:**
- `XssFilter.java` wraps all `HttpServletRequest` objects with regex-based sanitization.
- 6 regex patterns strip `<script>`, `on*=`, `javascript:`, `vbscript:`, `eval(`, `expression(`.
- Sanitizes `getParameter()`, `getParameterValues()`, `getHeader()`, `getQueryString()`.
- Known problems: regex bypass, corrupts code submissions (e.g., `eval` in user code), sanitizes headers unnecessarily.

**Recommended approach: Add ONE dependency: OWASP Java Encoder 1.3.0.**

The fix has two parts:

1. **Replace the XssFilter entirely** with a more targeted approach:
   - Remove global regex sanitization from `XssFilter`.
   - Apply output encoding at the response level using `Encode.forHtml()` for HTML contexts.
   - Exclude code execution endpoints from any input filtering (code submissions contain `eval`, `javascript` legitimately).
   - Stop sanitizing request headers.

2. **Use OWASP Java Encoder** for context-aware output encoding:
   - `Encode.forHtml()` -- HTML body context
   - `Encode.forHtmlAttribute()` -- HTML attribute context
   - `Encode.forJavaScript()` -- JavaScript context
   - `Encode.forUriComponent()` -- URL context

| Technology | Version | Purpose | Why |
|-----------|---------|---------|-----|
| org.owasp.encoder:encoder | 1.3.0 | Context-aware output encoding | OWASP-recommended, zero dependencies, high-performance. Replaces fundamentally broken regex approach. |
| dompurify | 3.3.x | Frontend HTML sanitization | Already in console and management. Frontend should continue using DOMPurify for rich text content rendering. |

**Why OWASP Java Encoder and not ESAPI:**
- ESAPI is heavier, more complex, and provides far more than needed (file upload, crypto, etc.)
- OWASP Java Encoder is focused: just encoding, ~50KB, no transitive dependencies
- Both are OWASP projects, but Encoder is the modern recommendation for output encoding specifically

**Maven addition:**
```xml
<dependency>
    <groupId>org.owasp.encoder</groupId>
    <artifactId>encoder</artifactId>
    <version>1.3.0</version>
</dependency>
```

**Confidence:** HIGH -- OWASP Java Encoder 1.3.0 is confirmed on Maven Central, actively maintained, and specifically designed for this use case.

---

### SEC-04: Docker Seccomp Profile for Java Sandbox

**Current state:**
- `docker/sandbox/Dockerfile` installs nodejs, python3, openjdk-17-jdk-headless, gcc, g++.
- Runs as non-root user (UID 1000).
- `CodeExecutionService.buildDockerCommand()` already applies: `--network none`, `--memory`, `--cpus`, `--pids-limit 128`, `--ulimit nofile=128:128`, `--read-only`, `--tmpfs /tmp:rw,size=64m`, `--user 1000:1000`, `--security-opt no-new-privileges:true`.
- No seccomp profile. No `--cap-drop ALL`.

**Recommended approach: No new dependencies. Pure Docker configuration files.**

Add two files to the project (no code dependencies):

1. **`docker/sandbox/seccomp-profile.json`** -- Custom seccomp profile that:
   - Blocks dangerous syscalls: `ptrace`, `mount`, `umount2`, `pivot_root`, `keyctl`, `acct`, `add_key`, `request_key`, `syslog`, `unshare`, `clone` (new namespaces), `kexec_load`, `reboot`, `swapon`, `swapoff`, `init_module`, `finit_module`, `delete_module`, `iopl`, `ioperm`
   - Allows necessary syscalls for compilation and execution: `execve`, `fork`, `wait4`, `read`, `write`, `open`, `openat`, `close`, `stat`, `fstat`, `mmap`, `munmap`, `brk`, `arch_prctl`, `set_tid_address`, `exit_group`, `exit`, `rt_sigaction`, `rt_sigprocmask`, `access`, `getpid`, `gettid`, `socketpair`, `pipe2`, `dup2`, `fcntl`, `getdents64`, `lseek`, `ioctl` (limited)
   - Default action: `SCMP_ACT_ERRNO` (return EPERM for unlisted syscalls)

2. **Modify `CodeExecutionService.buildDockerCommand()`** to add:
   - `--security-opt seccomp=/path/to/seccomp-profile.json`
   - `--cap-drop ALL`

**Why no external library:** Seccomp profiles are JSON files passed to Docker's runtime. No Java library needed. The profile is a static resource file deployed alongside the application.

**Recommended seccomp approach:** Start with Docker's default seccomp profile (already restrictive) as the base, then add explicit deny rules for dangerous syscalls. This is safer than allowlisting because Docker's default already covers most attack vectors. The default profile blocks ~44 of ~300+ syscalls.

| Technology | Version | Purpose | Source |
|-----------|---------|---------|--------|
| Docker seccomp (JSON profile) | Built-in | Syscall restriction | No dependency -- Docker runtime feature |

**Note on `--cap-drop ALL`:** Can safely be added because the sandbox does not need any Linux capabilities (no network, no device access, no privilege escalation). The `no-new-privileges` flag is already set.

---

### QUAL-01: Vue Component Splitting (14 Components Over 600 Lines)

**Current state:**
- 14 Vue components range from 602 to 1356 lines.
- Worst offenders: `ProblemListsView.vue` (1356 lines), `ProblemsListView.vue` (1224 lines).
- Both frontends use Vue 3 Composition API with `<script setup>`.

**Recommended approach: No new dependencies.**

Use existing Vue 3 patterns already present in the codebase:

1. **Composables (`use*.ts`)** for extracting reactive logic:
   - `useProblemFilters()`, `useSubmissionTable()`, `useContestTimer()`, etc.
   - The project already has composables in `console/src/composables/` (e.g., `useCodeTemplates`, `useMarkdown`).

2. **Sub-components** for template decomposition:
   - Extract table sections, filter bars, detail panels, dialogs into separate `.vue` files.
   - Place in co-located `components/` directories (e.g., `console/src/views/problems/components/`).

3. **Pattern -- Container/Presentational split:**
   - Container component handles data fetching and state.
   - Presentational sub-components receive props and emit events.

| Technology | Version | Purpose | Why |
|-----------|---------|---------|-----|
| Vue 3 Composition API | 3.5.x | Composables, `<script setup>` | Already in use. No new lib needed. |
| @vueuse/core | 14.1.x | Utility composables | Already installed. `useDebounce`, `useStorage`, etc. |

**Target structure for a 1200-line view:**
```
views/problems/
  ProblemListView.vue          (~200 lines - container)
  components/
    ProblemTable.vue           (~200 lines - table)
    ProblemFilters.vue         (~150 lines - filter bar)
    ProblemDetailDrawer.vue    (~200 lines - side panel)
    ProblemStats.vue           (~100 lines - statistics)
  composables/
    useProblemList.ts          (~150 lines - data logic)
    useProblemFilters.ts       (~100 lines - filter state)
```

---

### TEST-01: Backend Testing Stack (auth, submission, CodeExecution)

**Current state:**
- 22 existing test files, all pure unit tests with `@ExtendWith(MockitoExtension.class)`.
- `spring-boot-starter-test` (includes JUnit 5, Mockito, AssertJ, Spring Test) and `spring-security-test` are in pom.xml.
- No test resources directory (`src/test/resources/` does not exist).
- Missing test coverage: `AuthController`, `AuthServiceImpl`, `JwtTokenProvider`, `CsrfService`, `CsrfInterceptor`, `SubmissionServiceImpl`, `CodeExecutionService`, `XssFilter`.
- No `@WebMvcTest` or `@SpringBootTest` anywhere.

**Recommended approach: Add three test-scoped dependencies.**

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| spring-boot-testcontainers | managed by Spring Boot 3.5 BOM | Spring Boot integration with Testcontainers | `@ServiceConnection` auto-wiring for MySQL and Redis containers. Spring Boot 3.1+ native support. |
| org.testcontainers:mysql | 1.20.x (BOM-managed) | MySQL Testcontainer | Integration tests with real MySQL instead of mocking all MyBatis mappers. |
| org.testcontainers:junit-jupiter | 1.20.x (BOM-managed) | JUnit 5 lifecycle management | `@Testcontainers`, `@Container` annotations for container lifecycle. |

**Maven additions:**
```xml
<!-- Test dependencies -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

**Testing strategy per module:**

| Module | Test Type | Tooling | What It Tests |
|--------|-----------|---------|---------------|
| `JwtTokenProvider` | Unit | Mockito, JUnit 5 | Token generation, validation, expiration, key validation |
| `CsrfService` | Unit | Mockito (mock RedisTemplate) | Token generation, validation, rotation, cleanup |
| `CsrfInterceptor` | Unit | MockMvc + Spring Security Test | CSRF enforcement, method filtering, header extraction |
| `AuthServiceImpl` | Unit | Mockito (mock UserMapper, PasswordEncoder) | Login, register, token generation |
| `AuthController` | Integration | `@WebMvcTest` + MockMvc | HTTP request/response, validation, error handling |
| `SubmissionServiceImpl` | Unit | Mockito (mock SubmissionMapper, QueueService) | Submission flow, status management |
| `CodeExecutionService` | Unit | Mockito (mock DockerSandboxConfig, ProcessBuilder) | Docker command building, result parsing |
| `UserDetailsService` | Integration | `@WebMvcTest` | If kept: verify actual DB user loading |

**Testcontainers usage (optional, for deeper integration tests):**
- Spin up real MySQL + Redis for `AuthServiceImpl` integration tests
- Verify actual SQL queries work via MyBatis-Plus
- Test CSRF token persistence in Redis
- Use `@ServiceConnection` (Spring Boot 3.2+) for automatic property mapping

**Why Testcontainers and not H2:**
- H2 has SQL dialect differences from MySQL (e.g., `INSERT ... ON DUPLICATE KEY`, `LIMIT`, date functions)
- MyBatis-Plus queries written for MySQL may behave differently on H2
- Testcontainers gives MySQL-accurate results, catching real query issues
- Spring Boot 3.5 has first-class Testcontainers support -- minimal configuration

**Confidence:** HIGH -- Spring Boot 3.5.12 BOM manages Testcontainers versions. `@ServiceConnection` is stable since 3.2.

---

## Complete Dependency Changes Summary

### Production Dependencies (pom.xml)

| Action | GroupId | ArtifactId | Version | Fix |
|--------|---------|-----------|---------|-----|
| ADD | org.owasp.encoder | encoder | 1.3.0 | SEC-06 (XSS output encoding) |

That is the **only** new production dependency.

### Test Dependencies (pom.xml)

| Action | GroupId | ArtifactId | Version | Fix |
|--------|---------|-----------|---------|-----|
| ADD | org.springframework.boot | spring-boot-testcontainers | BOM-managed | TEST-01 |
| ADD | org.testcontainers | mysql | BOM-managed | TEST-01 |
| ADD | org.testcontainers | junit-jupiter | BOM-managed | TEST-01 |

### New Configuration Files (no code dependency)

| File | Location | Fix |
|------|----------|-----|
| `seccomp-profile.json` | `docker/sandbox/seccomp-profile.json` | SEC-04 |

### New Java Classes (using existing dependencies)

| Class | Location | Fix |
|-------|----------|-----|
| `RedisCsrfTokenRepository` | `security/csrf/RedisCsrfTokenRepository.java` | SEC-01 |
| (Modify) `SecurityConfig` | `common/config/SecurityConfig.java` | SEC-01 |
| (Modify) `WebMvcConfig` | `common/config/WebMvcConfig.java` | SEC-01 |
| (Modify) `JwtTokenProvider` | `security/jwt/JwtTokenProvider.java` | SEC-05 |
| (Modify) `XssFilter` | `common/filter/XssFilter.java` | SEC-06 |
| (Modify) `CodeExecutionService` | `submission/service/CodeExecutionService.java` | SEC-04 |

---

## Alternatives Considered

### CSRF: Spring Security's CookieCsrfTokenRepository

| Criterion | Custom Redis Repository (Recommended) | CookieCsrfTokenRepository (Built-in) |
|-----------|---------------------------------------|--------------------------------------|
| Server-side state | Yes (Redis) -- can invalidate tokens | No -- double-submit cookie only |
| Token rotation | Already implemented in `CsrfService` | Not supported natively |
| Cluster support | Yes (Redis shared state) | Yes (stateless cookies) |
| Frontend changes | None (same X-CSRF-Token header) | Requires frontend changes (X-XSRF-TOKEN header, cookie reading) |
| Logout cleanup | `clearUserTokens()` already works | No server-side cleanup |

**Decision:** Custom `RedisCsrfTokenRepository` wrapping existing `CsrfService`. Zero frontend changes, preserves token rotation and logout cleanup.

### XSS: ESAPI vs OWASP Encoder

| Criterion | OWASP Encoder 1.3.0 (Recommended) | ESAPI 2.5.x |
|-----------|-------------------------------------|-------------|
| Bundle size | ~50KB | ~2MB |
| Dependencies | None | Many (commons-beanutils, etc.) |
| Focus | Output encoding only | Full security toolkit (overkill) |
| Maintenance | Active, lightweight | Slower release cycle |
| Spring Boot compatibility | Perfect (no conflicts) | Known classloading issues |

**Decision:** OWASP Java Encoder. Smaller, focused, no conflicts with Spring Boot 3.5.

### Testing: H2 vs Testcontainers

| Criterion | Testcontainers + MySQL (Recommended) | H2 In-Memory |
|-----------|--------------------------------------|--------------|
| SQL accuracy | Exact MySQL behavior | MySQL compatibility mode (incomplete) |
| MyBatis-Plus compatibility | Full | May differ (LIMIT syntax, date functions) |
| Setup complexity | Docker required (already available) | Zero setup |
| Test speed | Slower (~5-10s container startup) | Fast (~1s) |
| Confidence | HIGH -- tests real DB | MEDIUM -- may miss DB-specific issues |

**Decision:** Testcontainers for integration tests, Mockito for pure unit tests. Use both: unit tests for fast feedback, Testcontainers for critical paths (auth, submission).

### Docker Sandbox: nsjail vs seccomp profile

| Criterion | Custom seccomp profile (Recommended) | nsjail |
|-----------|---------------------------------------|--------|
| Integration effort | Low (JSON file + Docker flag) | High (new binary, complex config) |
| Security | Good (syscall filtering) | Excellent (full isolation with namespaces) |
| Maintenance | Low (static JSON) | High (external dependency, version updates) |
| Compatibility | Works with existing Docker setup | Requires installing nsjail in the container |

**Decision:** Custom seccomp profile. Lower effort, good security improvement for now. nsjail can be a future enhancement if stricter isolation is needed.

---

## Installation

```xml
<!-- Add to backend-spring/pom.xml <dependencies> section -->

<!-- SEC-06: OWASP Java Encoder for output encoding (replaces regex XssFilter) -->
<dependency>
    <groupId>org.owasp.encoder</groupId>
    <artifactId>encoder</artifactId>
    <version>1.3.0</version>
</dependency>

<!-- TEST-01: Testcontainers for integration testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

Testcontainers version is managed by Spring Boot 3.5.12 BOM -- no explicit version needed.

---

## Sources

- [OWASP Java Encoder on Maven Central](https://mvnrepository.com/artifact/org.owasp.encoder/encoder) -- Version 1.3.0 confirmed (HIGH confidence)
- [OWASP Java Encoder GitHub](https://github.com/OWASP/owasp-java-encoder) -- Active project, Java 17 build requirement (HIGH confidence)
- [Spring Boot Testcontainers docs](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) -- Official Spring Boot 3.1+ integration guide (HIGH confidence)
- [Testcontainers Official Site](https://java.testcontainers.org/) -- Latest version ~1.20.x, BOM-managed by Spring Boot 3.5 (HIGH confidence)
- [Spring Security CSRF documentation](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) -- CsrfTokenRepository interface, CookieCsrfTokenRepository (HIGH confidence)
- [Baeldung: Prevent XSS in Spring](https://www.baeldung.com/spring-prevent-xss) -- OWASP Encoder usage patterns (MEDIUM confidence)
- [Docker seccomp profile reference](https://docs.docker.com/engine/security/seccomp/) -- Default profile and custom profiles (HIGH confidence)
- [Stack Overflow: Sandboxing for Online Judges](https://stackoverflow.com/questions/36191589/sandboxing-for-online-judges) -- Community best practices for judge sandboxes (MEDIUM confidence)

---

*Stack research: 2026-04-14*
