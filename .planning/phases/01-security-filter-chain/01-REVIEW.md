---
phase: 01-security-filter-chain
reviewed: 2026-04-14T23:31:00+08:00
depth: standard
files_reviewed: 6
files_reviewed_list:
  - backend-spring/pom.xml
  - backend-spring/src/main/java/com/ulticode/common/filter/XssFilter.java
  - backend-spring/src/main/java/com/ulticode/security/csrf/CsrfValidationFilter.java
  - backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java
  - backend-spring/src/main/java/com/ulticode/common/config/WebMvcConfig.java
  - backend-spring/src/main/java/com/ulticode/security/jwt/JwtProperties.java
findings:
  critical: 1
  warning: 2
  info: 3
  total: 6
status: issues_found
---

# Phase 01: Security Filter Chain - Code Review Report

**Reviewed:** 2026-04-14T23:31:00+08:00
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

This phase covers three parallel changes to the security filter chain:
1. **XssFilter removal** (01-01): Broken regex-based input sanitization replaced with a pass-through filter + OWASP Encoder dependency added to pom.xml. The old filter was corrupting legitimate user content (e.g., code submissions containing `<script>` in problem descriptions).
2. **CSRF migration** (01-02): CsrfInterceptor (WebMvc interceptor) migrated to CsrfValidationFilter (Spring Security filter). The filter is correctly positioned after JwtAuthenticationFilter in the security chain.
3. **JWT secret validation** (01-03): `@PostConstruct` validation added to JwtProperties, checking for null/blank/short secrets at startup.

The overall architecture is sound -- filter ordering is correct, CSRF service integration is clean, and the dead CsrfInterceptor/UserDetailsServiceImpl are fully removed. However, there is one critical issue with JWT secret validation error messaging, and a few warnings/info items to address.

## Critical Issues

### CR-01: JWT secret validation produces confusing NullPointerException

**File:** `backend-spring/src/main/java/com/ulticode/security/jwt/JwtProperties.java:33`
**Issue:** `Objects.requireNonNull(secret, "...")` throws a `NullPointerException`, which is unexpected and confusing for operators diagnosing a missing JWT secret. The `application.yml` config uses `${JWT_SECRET:}` which defaults to an empty string (not null). This means the NPE path will never actually fire -- Spring property binding will always set `secret` to `""` when the env var is missing. The subsequent `isBlank()` check on line 34 is the one that catches the real failure case, which correctly throws `IllegalStateException`. The NPE check on line 33 is dead code for this configuration and misleading if someone changes the config default to omit the colon.

Additionally, logging the secret's length on line 40 (`log.info("JWT secret validated successfully (length: {} chars)"`) leaks the length of the secret in logs. While not the secret itself, this is unnecessary information disclosure.

**Fix:**
```java
@PostConstruct
public void validateSecret() {
    if (secret == null || secret.isBlank()) {
        throw new IllegalStateException(
            "JWT secret must not be null or blank. Set the 'jwt.secret' property or JWT_SECRET environment variable.");
    }
    if (secret.length() < 32) {
        throw new IllegalStateException(
            "JWT secret must be at least 32 characters for HS256. Current length: " + secret.length()
            + " chars. Set a longer value via 'jwt.secret' property or JWT_SECRET environment variable.");
    }
    log.info("JWT secret validated successfully");
}
```

Key changes:
- Combine null and blank checks into a single guard with a consistent `IllegalStateException`.
- Upgrade the short-secret check from `log.warn` to `throw` -- a short secret is a real security risk and should prevent startup, not just warn.
- Remove secret length from the success log message.

## Warnings

### WR-01: CsrfValidationFilter instantiated via `new` bypasses Spring bean lifecycle

**File:** `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java:143`
**Issue:** The CsrfValidationFilter is instantiated with `new CsrfValidationFilter(csrfService)` directly inside the `securityFilterChain` method. This bypasses Spring's bean lifecycle -- the filter will not be a Spring-managed bean, which means:
- No AOP proxying (e.g., `@Transactional`, custom annotations) would work on it.
- If CsrfValidationFilter ever needs additional injected dependencies in the future, they must be manually passed through the constructor here rather than auto-wired.
- The pattern is inconsistent with `JwtAuthenticationFilter` which is injected as a `final` field and is a `@Component`.

This is not a correctness issue today because CsrfValidationFilter only depends on CsrfService and uses no Spring lifecycle features. However, it creates a maintenance trap.

**Fix:**
Register CsrfValidationFilter as a Spring bean (either via `@Component` or a `@Bean` method), then inject it like JwtAuthenticationFilter:

```java
// Option A: Add @Component to CsrfValidationFilter
@Component
@RequiredArgsConstructor
public class CsrfValidationFilter extends OncePerRequestFilter {
    // ... unchanged
}

// In SecurityConfig:
private final CsrfValidationFilter csrfValidationFilter;

// In securityFilterChain():
.addFilterAfter(csrfValidationFilter, JwtAuthenticationFilter.class);
```

### WR-02: CSRF filter skips validation for requests with `authentication.isAuthenticated() == false` but non-null authentication

**File:** `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfValidationFilter.java:47`
**Issue:** The check `authentication == null || !authentication.isAuthenticated()` skips CSRF validation for any unauthenticated request. However, Spring Security may populate the SecurityContext with an unauthenticated `Authentication` object (e.g., `AnonymousAuthenticationToken` when `anonymous()` is enabled, or a pre-auth token). The subsequent `anonymousUser` check on line 53 catches the Spring anonymous case, but if a custom authentication provider sets `authenticated = false` on a token that still has a real principal (username), the CSRF check would be silently skipped for a user who is partially authenticated.

In the current configuration, `sessionManagement` is `STATELESS` and Spring Security's default `anonymous()` is disabled when `.anyRequest().authenticated()` is used with stateless sessions, so this is unlikely to trigger. But the defensive check could be tightened.

**Fix:**
The `anonymousUser` check already covers the main edge case. For robustness, consider combining the checks:

```java
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
if (authentication == null || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getName())) {
    filterChain.doFilter(request, response);
    return;
}
// Remove the separate anonymousUser check below (lines 52-56)
```

This is a minor structural improvement -- the current code is functionally correct for the existing configuration.

## Info

### IN-01: Pass-through XssFilter should be removed entirely rather than retained as dead code

**File:** `backend-spring/src/main/java/com/ulticode/common/filter/XssFilter.java:1-32`
**Issue:** The Javadoc states "This filter is retained as a pass-through to preserve its position in the filter chain ordering. It will be removed in a future cleanup." However, there is no reason to preserve filter chain ordering for a no-op filter. The `@Order(Ordered.HIGHEST_PRECEDENCE + 1)` was only meaningful when the filter was doing actual work. Retaining it adds confusion for future developers who may wonder why a no-op filter exists. The cleanup should happen now as part of this phase rather than being deferred.

**Fix:** Delete `XssFilter.java` entirely. If filter ordering is needed for future XSS-related filters, add it at that time with a clear purpose.

### IN-02: PUBLIC_ENDPOINTS inconsistency -- auth endpoints lack `/api` prefix

**File:** `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java:42-49`
**Issue:** Auth endpoints in PUBLIC_ENDPOINTS use paths without the `/api` prefix (e.g., `"/auth/login"`), while solution endpoints include the prefix (e.g., `"/api/solutions"`). This is not necessarily a bug -- it depends on the servlet context path or prefix mapping configured elsewhere. However, the inconsistency makes it harder to verify correctness without checking the controller path mappings. A uniform prefix (or no prefix) would improve readability.

This was previously flagged in project observations (observation 4036: "PUBLIC_ENDPOINTS missing /api prefix for authentication paths").

**Fix:** Verify whether the application uses a context path or prefix mapping. If all endpoints are under `/api`, add the prefix to auth endpoints for consistency. If some controllers map without the prefix, document this explicitly in a comment.

### IN-03: CsrfService uses `IdUtil.simpleUUID()` which is UUID without hyphens -- not cryptographically random

**File:** `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfService.java:46-47`
**Issue:** `IdUtil.simpleUUID()` from Hutool uses `java.util.UUID.randomUUID()` internally, which is cryptographically random. However, it strips hyphens, producing a 32-character hex string. The token format is `tokenId:tokenValue` where both parts are 32 hex chars. This is 64 hex characters total = 256 bits of entropy, which is adequate for CSRF protection. No action required, but noting that the comment "CSRF Token" does not clarify the entropy budget for future maintainers.

This is informational only -- the entropy is sufficient.

---

_Reviewed: 2026-04-14T23:31:00+08:00_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
