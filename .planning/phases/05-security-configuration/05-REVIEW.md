---
status: issues_found
phase: 05-security-configuration
reviewed: 2026-04-16
reviewer: gsd-code-reviewer
---

# Phase 05: Security Configuration — Code Review

**Reviewer:** gsd-code-reviewer
**Date:** 2026-04-16
**Scope:** Plans 05-01 through 05-04 (CORS externalization, XssFilter verification, actuator restriction, Docker credential hardening)

## Changes Summary

| Plan | Description | Files Changed | Lines Changed |
|------|-------------|---------------|---------------|
| 05-01 | Externalize CORS origins to env vars | 7 (1 new, 6 modified) | ~100 |
| 05-02 | Verify XssFilter pass-through | 0 (verification only) | 0 |
| 05-03 | Restrict actuator endpoints in prod | 1 | +10 |
| 05-04 | Remove weak Docker default passwords | 2 | ~12 |

## Findings

### HIGH

```
[HIGH-1] Production CORS fallback defaults to localhost origins
File: backend-spring/src/main/resources/application-prod.yml:5
Issue: The production profile falls back to localhost:9002 and localhost:9003 when
       CORS_ALLOWED_ORIGINS is unset. If a deployment forgets to set this env var,
       the production server will accept cross-origin requests from localhost. While
       this is unlikely to be exploitable in a real production scenario (localhost
       is rarely accessible from the internet), it violates the principle of secure
       defaults for production. The research doc (05-RESEARCH.md, Open Question #3)
       explicitly flagged this and recommended an empty default with a @PostConstruct
       warning, but the implementation kept the dev fallback.

Fix: Remove the fallback default in production so unset env var yields an empty list:

  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:}

Then add a @PostConstruct validation in CorsProperties that logs a WARN when
allowedOrigins is empty and the active profile includes "prod":

  @PostConstruct
  public void validateOrigins() {
      if (allowedOrigins == null || allowedOrigins.isEmpty()) {
          log.warn("cors.allowed-origins is empty. All cross-origin requests will be denied.");
      }
  }
```

```
[HIGH-2] Redis password exposed in docker healthcheck command line
File: docker-compose.yml:30
Issue: The Redis healthcheck passes the password via `redis-cli -a <password>`.
       Docker stores container configuration (including healthcheck commands) in
       plaintext and it is visible via `docker inspect`. The password will appear
       in process listings inside the container. Redis 6+ supports REDISCLI_AUTH
       environment variable to avoid this.

Fix: Use the REDISCLI_AUTH environment variable instead of the -a flag:

  environment:
    REDISCLI_AUTH: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]

Redis 7.x (used here: redis:7-alpine) reads REDISCLI_AUTH automatically.
```

### MEDIUM

```
[MEDIUM-1] @ConfigurationProperties and @Value mixed on the same class
File: backend-spring/src/main/java/com/ulticode/modules/websocket/config/WebSocketProperties.java:12,22
Issue: WebSocketProperties uses @ConfigurationProperties(prefix = "app.websocket") at the
       class level but @Value("${cors.allowed-origins:...}") on the allowedOrigins field.
       While this works functionally, mixing these two mechanisms on the same class is
       a Spring Boot anti-pattern. @ConfigurationProperties binds via setter methods or
       direct field access, while @Value binds via field injection. The @Value annotation
       uses a fully-qualified inline form (org.springframework.beans.factory.annotation.Value)
       instead of a standard import, suggesting it was added as an afterthought.

       Additionally, the field initializer {"http://localhost:9002", "http://localhost:9003"}
       is redundant when @Value provides its own default. If the @Value annotation is
       present, the field initializer is never used.

Fix: Either inject CorsProperties directly into WebSocketConfig (preferred, avoids
     the cross-prefix binding entirely), or import Value properly and remove the
     redundant field initializer:

  import org.springframework.beans.factory.annotation.Value;

  @Value("${cors.allowed-origins:http://localhost:9002,http://localhost:9003}")
  private String[] allowedOrigins;
```

```
[MEDIUM-2] CSP connect-src may block WebSocket connections from browser
File: backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java:126
Issue: The Content Security Policy sets `connect-src 'self'` only. If the frontend
       is served from a different origin than the backend (which is the case in this
       project -- console runs on :9002, backend on :9001), the CSP header on API
       responses will not directly affect frontend-initiated WebSocket connections
       since the frontend has its own CSP. However, if Swagger UI were ever re-enabled
       in production, or if any backend-served HTML page makes fetch/XHR/WebSocket
       calls, the restrictive connect-src would block them. This is informational
       rather than an active bug.

       Note: The CSP in SecurityConfig applies to responses from the backend API
       server. Since the frontend is a separate SPA, this CSP does not govern
       frontend-originated requests. The concern is limited to any backend-served
       HTML content.

Fix: No immediate action required. Document that this CSP applies to backend-served
     responses only. If the backend ever serves HTML templates, connect-src should
     be updated to include the frontend origin.
```

```
[MEDIUM-3] Missing test coverage for CorsProperties
File: backend-spring/src/main/java/com/ulticode/common/config/CorsProperties.java
Issue: CorsProperties is a new class with a toConfigurationSource() method that
       builds the complete CORS policy (credentials, methods, headers, exposed
       headers, max-age). There are no unit tests for this class. While the
       existing test suite has no SecurityConfig or CORS tests either, this new
       class contains security-critical configuration logic that should be tested
       to prevent regressions.

Fix: Add a unit test for CorsProperties that verifies:
  - Default origins are applied when no property is set
  - toConfigurationSource() returns a CorsConfigurationSource with correct settings
  - AllowCredentials is true
  - Allowed methods include all expected HTTP methods
  - Exposed headers include "X-New-CSRF-Token" (required for CSRF flow)
```

### LOW

```
[LOW-1] JwtProperties.validateSecret() logs secret length at INFO level
File: backend-spring/src/main/java/com/ulticode/security/jwt/JwtProperties.java:40
Issue: Not a new issue in this phase, but observed during review context gathering.
       The validateSecret() method logs "JWT secret validated successfully (length: N chars)"
       at INFO level. While the actual secret is not logged, the length is a minor
       information leak that could assist an attacker in brute-forcing the key.
       This was pre-existing and not introduced by Phase 5.

Fix: Consider downgrading to DEBUG level.
```

```
[LOW-2] CorsProperties uses @Data (Lombok) which generates setters for immutable config
File: backend-spring/src/main/java/com/ulticode/common/config/CorsProperties.java:16
Issue: @Data generates setters, equals, hashCode, and toString for all fields.
       For a configuration properties class, the setter is needed for Spring Boot
       binding, but equals/hashCode/toString are unnecessary. More importantly,
       @Data generates a toString that will include the allowedOrigins list, which
       could be verbose in logs. The class-level @Component + @ConfigurationProperties
       pattern is correct, but @Data is broader than needed.

       This matches the existing pattern in JwtProperties.java, so it is consistent
       with the codebase. Flagging for awareness only.

Fix: No action required (consistent with codebase convention).
```

```
[LOW-3] Fully-qualified @Value annotation instead of import
File: backend-spring/src/main/java/com/ulticode/modules/websocket/config/WebSocketProperties.java:22
Issue: The @Value annotation uses its fully qualified class name
       @org.springframework.beans.factory.annotation.Value instead of a standard
       import statement. This makes the code harder to read.

Fix: Add import org.springframework.beans.factory.annotation.Value; and use @Value
     directly. This is related to MEDIUM-1 above.
```

## Positive Observations

1. **Clean separation of concerns**: Moving CORS configuration into a dedicated CorsProperties class with a toConfigurationSource() factory method is well-designed. It keeps SecurityConfig focused on the filter chain.

2. **Correct use of setAllowedOriginPatterns()**: The code uses setAllowedOriginPatterns() instead of the deprecated setAllowedOrigins(), which is required when allowCredentials=true in Spring Security 6.x.

3. **Thorough Docker credential hardening**: The switch from `:-default` to `:?message` for all password env vars is a significant security improvement. Docker Compose will now fail fast with a clear error message instead of silently starting with weak credentials.

4. **Actuator restriction is correctly scoped**: The production profile now exposes only the health endpoint with no details or components, which prevents information disclosure through /actuator/env, /actuator/beans, etc.

5. **Swagger UI correctly disabled in production**: Both springdoc.api-docs.enabled and springdoc.swagger-ui.enabled are set to false.

6. **WebSocket CORS synchronized with HTTP CORS**: The WebSocketProperties.allowedOrigins reads from the same cors.allowed-origins property, preventing CORS mismatches between HTTP and WebSocket endpoints.

7. **.env.example improvements**: The bilingual warning about password changes and CHANGE_ME placeholders are clear and actionable.

## Review Summary

| Severity | Count | Status |
|----------|-------|--------|
| CRITICAL | 0     | pass   |
| HIGH     | 2     | warn   |
| MEDIUM   | 3     | info   |
| LOW      | 3     | note   |

Verdict: WARNING -- 2 HIGH issues should be resolved before merge.

### Priority Actions

1. **HIGH-1**: Remove localhost fallback from production CORS config; add startup validation when origins list is empty in prod profile.
2. **HIGH-2**: Switch Redis healthcheck from `-a <password>` to `REDISCLI_AUTH` environment variable to avoid password exposure in `docker inspect` and process listings.

### Recommended Follow-ups (not blocking)

- MEDIUM-1: Clean up the @ConfigurationProperties / @Value mix in WebSocketProperties.
- MEDIUM-3: Add unit tests for CorsProperties.toConfigurationSource().
- LOW-3: Replace fully-qualified @Value with a proper import.

---
*Phase: 05-security-configuration*
*Reviewed: 2026-04-16*
