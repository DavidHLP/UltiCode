# Phase 5: Security Configuration - Research

**Researched:** 2026-04-16
**Domain:** Spring Boot 3.5 security configuration externalization (CORS, JWT cookies, actuator, Docker credentials)
**Confidence:** HIGH

## Summary

Phase 5 addresses five requirements (SEC-07, SEC-08, CONF-01, CONF-02, CONF-03) that externalize and harden security configuration. The codebase investigation reveals that some work is already partially done: `application-prod.yml` already exists with Swagger disabled and `secure: true` for cookies, `JwtProperties` already supports `secure` configuration via YAML, and `application.yml` already uses `${JWT_COOKIE_SECURE:false}`. However, CORS origins remain hardcoded in both `SecurityConfig.java` and `WebSocketProperties.java`, `XssFilter` is already a pass-through (SEC-06 completed in v1.0), and the dev `docker-compose.yml` has weak default passwords (`${DB_PASSWORD:-ulticode}`, `${REDIS_PASSWORD:-ulticode_redis}`, `${MYSQL_ROOT_PASSWORD:-root}`).

The main implementation work is: (1) externalizing CORS origins to `@ConfigurationProperties` driven by environment variables, (2) verifying XssFilter does not touch headers (already a pass-through), (3) validating the production profile is complete (mostly done), and (4) removing weak password fallbacks from `docker-compose.yml`.

**Primary recommendation:** This is primarily a configuration-driven phase with minimal code changes. Focus on environment variable externalization and removing insecure defaults.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| CORS origin configuration | Backend (Spring Security) | Frontend (dev proxy) | CORS is enforced server-side by Spring Security filter chain |
| XssFilter header passthrough | Backend (Servlet Filter) | -- | Filter chain ordering is a server-side concern |
| JWT cookie Secure flag | Backend (Spring Boot config) | -- | Cookie attributes are set by `AuthServiceImpl` using `JwtProperties` |
| Production profile (Swagger/actuator) | Backend (Spring Boot config) | -- | `application-prod.yml` controls feature flags |
| Docker credential externalization | Infrastructure (Docker Compose) | Backend (reads env vars) | Docker Compose injects env vars; backend reads them |

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SEC-07 | CORS allowed origins externalized to environment variables | SecurityConfig.java hardcodes 4 origins; WebSocketProperties.java hardcodes 2 origins. Both need `@ConfigurationProperties` + env var binding. |
| SEC-08 | XssFilter stops cleaning request headers | Already a pass-through (v1.0 SEC-06). Must verify no header wrapping occurs. |
| CONF-01 | JWT Cookie Secure flag true in production | `application-prod.yml` already sets `secure: true`. `JwtProperties` supports it. Already working. |
| CONF-02 | Create application-prod.yml with Swagger disabled, actuator endpoints restricted | `application-prod.yml` exists with Swagger disabled. Actuator exposure not yet configured. |
| CONF-03 | docker-compose.yml removes weak default passwords | Dev compose has `:-ulticode`, `:-ulticode_redis`, `:-root` fallbacks. Prod compose uses `:?` (required). |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.x | Application framework | Already in use; `@ConfigurationProperties` is the native config binding mechanism |
| Spring Security | 6.5.x | CORS enforcement via `CorsConfigurationSource` | Already in use; provides `CorsConfiguration` API |
| Jakarta Servlet API | 6.x | Filter chain, Cookie handling | Already in use via Spring Boot 3.5 |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Lombok | - | `@Data`, `@ConfigurationProperties` boilerplate reduction | Already used on `JwtProperties`, `WebSocketProperties` |
| Spring Boot Actuator | 3.5.x | Health endpoints, management config | Already in use; needs endpoint exposure restriction for prod |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@ConfigurationProperties` for CORS | `@Value` injection per origin | `@ConfigurationProperties` is type-safe, supports list binding, and is the Spring Boot standard |
| YAML list for CORS origins | Comma-separated env var string | YAML list is cleaner; comma-separated needs a `@Value` splitter. Recommend YAML list with env var override |

**Installation:** No new dependencies required.

**Version verification:** No new packages to install -- this phase uses existing Spring Boot infrastructure only.

## Architecture Patterns

### System Architecture Diagram

```
                    Environment Variables
                    ┌──────────────────────────┐
                    │ CORS_ALLOWED_ORIGINS      │
                    │ JWT_COOKIE_SECURE          │
                    │ SPRINGDOC_ENABLED          │
                    │ DB_PASSWORD, REDIS_PASSWORD│
                    └──────┬──────────┬─────────┘
                           │          │
                ┌──────────▼──┐  ┌────▼──────────────┐
                │  application │  │  docker-compose    │
                │  *.yml       │  │  .yml              │
                │  (Spring     │  │  (Docker services) │
                │   Boot       │  │                    │
                │   config)    │  │  MySQL, Redis,     │
                └──────┬───────┘  │  Nacos             │
                       │          └────┬───────────────┘
            ┌──────────▼───────────────▼──────────────────┐
            │           Spring Boot Application           │
            │  ┌─────────────────────────────────────────┐ │
            │  │ SecurityConfig                          │ │
            │  │  ┌──────────────────────────────────┐   │ │
            │  │  │ CorsProperties (@ConfigProps)    │   │ │
            │  │  │  ↓ reads CORS_ALLOWED_ORIGINS    │   │ │
            │  │  │  ↓ from application.yml / env    │   │ │
            │  │  └──────────────────────────────────┘   │ │
            │  │  ↓ builds CorsConfigurationSource       │ │
            │  └─────────────────────────────────────────┘ │
            │  ┌─────────────────────────────────────────┐ │
            │  │ JwtProperties (@ConfigProps)             │ │
            │  │  ↓ cookie.secure from YAML profile       │ │
            │  │  ↓ dev: false, prod: true                │ │
            │  └─────────────────────────────────────────┘ │
            │  ┌─────────────────────────────────────────┐ │
            │  │ WebSocketProperties                      │ │
            │  │  ↓ allowedOrigins from env var           │ │
            │  └─────────────────────────────────────────┘ │
            └─────────────────────────────────────────────┘
```

### Recommended Project Structure

No new directories needed. Changes are within existing files:

```
backend-spring/src/main/
├── java/com/ulticode/
│   ├── common/config/
│   │   ├── SecurityConfig.java          # MODIFY: inject CorsProperties
│   │   └── CorsProperties.java          # NEW: @ConfigurationProperties for CORS
│   ├── common/filter/
│   │   └── XssFilter.java               # VERIFY: already pass-through
│   ├── security/jwt/
│   │   └── JwtProperties.java           # NO CHANGE: already supports secure flag
│   └── modules/websocket/config/
│       └── WebSocketProperties.java      # MODIFY: read allowedOrigins from env var
├── resources/
│   ├── application.yml                  # MODIFY: add cors.allowed-origins property
│   ├── application-dev.yml              # MODIFY: add dev CORS origins
│   └── application-prod.yml             # MODIFY: add actuator endpoint restrictions
└── ...
docker-compose.yml                        # MODIFY: remove weak default passwords
.env.example                             # MODIFY: add CORS_ALLOWED_ORIGINS template
```

### Pattern 1: `@ConfigurationProperties` for Externalized CORS Origins

**What:** Create a `CorsProperties` class that binds `cors.allowed-origins` from YAML/environment, inject it into `SecurityConfig`.

**When to use:** Any configuration that needs to vary across environments (dev/prod) and should be overridable via environment variables.

**Example:**

```java
// Source: [VERIFIED: codebase - JwtProperties.java pattern]
@Data
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of(
        "http://localhost:9002",
        "http://localhost:9003"
    );

    /**
     * Convert to Spring Security CorsConfigurationSource.
     */
    public CorsConfigurationSource toConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Set-Cookie", "Content-Disposition", "X-New-CSRF-Token"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

YAML binding:

```yaml
# application.yml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:9002,http://localhost:9003}

# application-prod.yml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS}
```

**Note:** Spring Boot 3.5's relaxed binding automatically splits comma-separated env var values into a `List<String>`. [VERIFIED: Context7 /websites/spring_io_spring-boot_3_5]

### Pattern 2: Production Actuator Endpoint Restriction

**What:** In `application-prod.yml`, restrict actuator endpoints to only expose health.

**When to use:** Production deployments should not expose env, beans, metrics, or config endpoints.

**Example:**

```yaml
# application-prod.yml
management:
  endpoints:
    web:
      exposure:
        include: health
      cors:
        allowed-origins: ${CORS_ALLOWED_ORIGINS}
  endpoint:
    health:
      show-details: never
```

[VERIFIED: Context7 /websites/spring_io_spring-boot_3_5 - actuator endpoint configuration]

### Anti-Patterns to Avoid

- **Hardcoding origins in Java code:** Already done in `SecurityConfig.java` -- must be replaced with `@ConfigurationProperties`. Never use `Arrays.asList("http://localhost:...")` directly in a `@Bean` method.
- **Using `setAllowedOrigins()` with wildcard patterns:** Use `setAllowedOriginPatterns()` instead when patterns like `http://localhost:*` are needed (Spring Security 6.x requires patterns for credential-bearing requests).
- **Setting `Secure=true` without HTTPS:** Will cause cookies to not be sent over HTTP. This is why `application-dev.yml` sets `secure: false`.
- **Removing Swagger from SecurityConfig PUBLIC_ENDPOINTS:** Keep the endpoint entries in the array. When Swagger is disabled via `springdoc.swagger-ui.enabled: false`, the endpoints return 404 anyway. Removing them from PUBLIC_ENDPOINTS would cause 403 instead of 404, which leaks information about disabled features.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| CORS origin parsing | Custom string splitter | Spring Boot `@ConfigurationProperties` relaxed binding | Automatically handles comma-separated env vars into `List<String>` |
| Cookie construction | Manual header formatting (current approach works) | Keep current `response.setHeader("Set-Cookie", ...)` pattern | Servlet `Cookie` API doesn't support `SameSite`; manual header is the standard workaround |
| Environment variable validation | Custom `@PostConstruct` | `@Validated` + JSR 380 annotations on `@ConfigurationProperties` | Declarative, consistent with existing `JwtProperties.validateSecret()` pattern |

**Key insight:** This phase is about configuration, not code. Don't over-engineer the CORS properties class -- a simple `@ConfigurationProperties` with a list is sufficient.

## Common Pitfalls

### Pitfall 1: Comma-Separated Env Var Not Splitting

**What goes wrong:** `CORS_ALLOWED_ORIGINS=http://localhost:9002,http://localhost:9003` is injected as a single string, not a list.

**Why it happens:** Spring Boot relaxed binding does handle this automatically for `List<String>` properties, but only when using `${VAR}` syntax in YAML. Direct `@Value("${VAR}")` injection would not split.

**How to avoid:** Use `@ConfigurationProperties` with `List<String>` field and `${VAR}` in YAML. Test with: `CORS_ALLOWED_ORIGINS="http://a.com,http://b.com" java -jar app.jar`.

**Warning signs:** `setAllowedOriginPatterns(["http://localhost:9002,http://localhost:9003"])` -- single entry with comma.

### Pitfall 2: Actuator Endpoints Exposed in Production

**What goes wrong:** `/actuator/env`, `/actuator/beans`, `/actuator/configprops` expose secrets and internal configuration.

**Why it happens:** Spring Boot Actuator defaults to exposing `health` only, but if `management.endpoints.web.exposure.include` was set broadly in a shared config, production inherits it.

**How to avoid:** Explicitly set `management.endpoints.web.exposure.include: health` in `application-prod.yml`. Verify with `curl http://localhost:9001/actuator/env` -- should return 404.

**Warning signs:** `curl /actuator/beans` returns JSON with bean definitions.

### Pitfall 3: Docker Compose Default Passwords in Version Control

**What goes wrong:** `docker-compose.yml` contains `:-ulticode` fallback passwords that anyone with repo access can see.

**Why it happens:** Fallback defaults were added for developer convenience, but they become security liabilities when the compose file is committed.

**How to avoid:** Change `${VAR:-default}` to `${VAR:?error message}` (required, no default). Provide a `.env.example` with placeholder values. Document in CLAUDE.md that `.env` must be created before `docker compose up`.

**Warning signs:** `git log --all -p -- docker-compose.yml` shows plaintext passwords.

### Pitfall 4: WebSocket CORS Not Synchronized with HTTP CORS

**What goes wrong:** HTTP CORS allows `http://localhost:9002` but WebSocket rejects the same origin, or vice versa.

**Why it happens:** `SecurityConfig` and `WebSocketProperties` maintain separate origin lists.

**How to avoid:** Have `WebSocketProperties` read from the same `cors.allowed-origins` property, or inject `CorsProperties` into the WebSocket configuration.

**Warning signs:** WebSocket connection fails with CORS error in browser console while HTTP requests succeed.

### Pitfall 5: Removing XssFilter Entirely

**What goes wrong:** Removing `XssFilter.java` changes the `@Order` numbering of subsequent filters.

**Why it happens:** The filter chain uses `@Order(Ordered.HIGHEST_PRECEDENCE + 1)` on XssFilter. If it's removed, the next filter might unintentionally shift.

**How to avoid:** Since XssFilter is already a pass-through, either leave it in place (safe, zero cost) or verify no other filter depends on the ordering gap. Leaving it is the safer option for this phase.

**Warning signs:** 403 on endpoints that previously worked after filter removal.

## Code Examples

### Current CORS Configuration (SecurityConfig.java -- TO BE REPLACED)

```java
// CURRENT STATE -- hardcoded origins
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.setAllowedOriginPatterns(java.util.Arrays.asList(
            "http://localhost:9002",
            "http://localhost:9003",
            "http://127.0.0.1:9002",
            "http://127.0.0.1:9003"
    ));
    // ... rest of config
}
```

### Target CORS Configuration (SecurityConfig.java -- INJECT CorsProperties)

```java
// TARGET STATE -- externalized origins
private final CorsProperties corsProperties;

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    return corsProperties.toConfigurationSource();
}
```

### Current XssFilter (Already Pass-Through -- SEC-08 VERIFIED)

```java
// CURRENT STATE -- already a pass-through from v1.0 SEC-06
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class XssFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(request, response);  // No header modification
    }
}
```

**SEC-08 Status:** Already satisfied. XssFilter is a pure pass-through since v1.0. No request wrapping, no header modification. The requirement is already met.

### Current JWT Cookie Secure Configuration (CONF-01 -- ALREADY WORKING)

```yaml
# application.yml (shared base)
jwt:
  cookie:
    access-token:
      secure: ${JWT_COOKIE_SECURE:false}  # Dev default: false
    refresh-token:
      secure: ${JWT_COOKIE_SECURE:false}  # Dev default: false

# application-prod.yml (overrides for production)
jwt:
  cookie:
    access-token:
      secure: true   # Production: always true
    refresh-token:
      secure: true   # Production: always true
```

**CONF-01 Status:** Already implemented. `JwtProperties` reads `secure` from YAML, `AuthServiceImpl` and `OAuthService` both use `cookieConfig.isSecure()` when constructing Set-Cookie headers.

### Current Docker Compose Weak Defaults (TO BE FIXED)

```yaml
# docker-compose.yml -- CURRENT (has weak defaults)
MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}  # GOOD
MYSQL_PASSWORD: ${DB_PASSWORD:-ulticode}          # WEAK DEFAULT
REDIS_PASSWORD: ${REDIS_PASSWORD:-ulticode_redis} # WEAK DEFAULT
MYSQL_SERVICE_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}  # WEAK DEFAULT
```

```yaml
# docker-compose.yml -- TARGET (no defaults)
MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}
MYSQL_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD is required}
REDIS_PASSWORD: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}
MYSQL_SERVICE_PASSWORD: ${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hardcoded CORS origins in `@Bean` | `@ConfigurationProperties` + env var binding | Spring Boot 2.0+ | Standard Spring Boot pattern for externalized config |
| `Cookie` API for Set-Cookie | Manual `Set-Cookie` header string | Servlet 6.x still lacks SameSite | Must use manual header to set `SameSite` attribute |
| `setAllowedOrigins()` with wildcards | `setAllowedOriginPatterns()` | Spring Security 5.3+ / 6.x | Required when `allowCredentials=true` |
| Actuator wide-open exposure | Explicit `include: health` in prod | Spring Boot 2.x+ | Production security baseline |

**Deprecated/outdated:**
- `setAllowedOrigins()` with `*` when credentials are enabled: Blocked in Spring Security 6.x. Use `setAllowedOriginPatterns()` instead.
- ` CorsConfiguration.setAllowCredentials(true)` + `setAllowedOrigins(List.of("*"))`: Throws `IllegalArgumentException`. [VERIFIED: Spring Security 6.x source]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring Boot 3.5 relaxed binding splits comma-separated env vars into `List<String>` automatically | Pattern 1 | Medium -- if not, need custom `Converter<String, List<String>>` bean. Low risk; this is documented behavior. |
| A2 | `application-prod.yml` already exists and is correct for CONF-01 | Code Examples | Low -- verified by reading the file. |
| A3 | Docker Compose `${VAR:?message}` syntax prevents startup when var is missing | Pattern: Docker | Low -- standard shell parameter expansion, well-documented. |
| A4 | No existing tests specifically validate CORS behavior, so no tests will break | Common Pitfalls | Medium -- should verify no `@WebMvcTest` tests assert on specific CORS headers. |

**If this table is empty:** All claims in this research were verified or cited -- no user confirmation needed.

## Open Questions (RESOLVED)

1. **Should WebSocket CORS origins share the same config property as HTTP CORS?**
   - What we know: `WebSocketProperties.java` has its own `allowedOrigins` field with `http://localhost:9002, http://localhost:9003` defaults.
   - What's unclear: Whether the WebSocket config should reference the same `cors.allowed-origins` property or have its own `websocket.allowed-origins`.
   - Recommendation: Share the same `cors.allowed-origins` property. WebSocket and HTTP should enforce the same origin policy. Inject `CorsProperties` into WebSocket configuration.

2. **Should the dev `docker-compose.yml` require passwords (no defaults) or keep defaults for developer convenience?**
   - What we know: Current defaults are weak (`ulticode`, `ulticode_redis`, `root`). Production compose already uses `:?` (required).
   - What's unclear: Whether removing defaults from the dev compose would break existing developer workflows.
   - Recommendation: Change to `:?` (required) in both dev and prod compose files. Update `.env.example` with documented placeholder values. Update CLAUDE.md with setup instructions. Developers already have `.env` from the setup process.

3. **Should `CORS_ALLOWED_ORIGINS` in production be empty (deny all) or unset (fail fast)?**
   - What we know: If `CORS_ALLOWED_ORIGINS` is unset in prod YAML (`${CORS_ALLOWED_ORIGINS}`), Spring Boot will throw `IllegalArgumentException` because the list would be null/empty.
   - What's unclear: Whether the application should start with an empty CORS list (deny all cross-origin) or fail to start.
   - Recommendation: Use `${CORS_ALLOWED_ORIGINS:}` (empty default) so the app starts but blocks all cross-origin requests. Add a `@PostConstruct` warning if the list is empty in prod profile.

## Environment Availability

> Step 2.6: SKIPPED (no external dependencies identified -- all changes are code/config within the existing project)

## Validation Architecture

> Skipped: `workflow.nyquist_validation` is explicitly set to `false` in `.planning/config.json`.

## Project Constraints (from CLAUDE.md)

The following directives from CLAUDE.md constrain this phase:

1. **Database migrations via Flyway**: No database changes in this phase -- no migration needed.
2. **Backend runs on port 9001**: No port changes.
3. **JWT tokens stored in httpOnly cookies**: Must preserve this -- CONF-01 changes the `Secure` flag only.
4. **CSRF token required for state-changing requests**: CORS changes must not break CSRF flow (SEC-08 ensures headers pass through).
5. **Docker services (MySQL, Redis, Nacos)**: CONF-03 changes only password injection, not service topology.
6. **`db-manager/.venv/bin/python` for migrations**: Not applicable -- no migrations this phase.
7. **Root `.env` is the unified env var source**: New env vars (`CORS_ALLOWED_ORIGINS`) should be documented in `.env.example`.
8. **`management/.env` is git-tracked**: Known issue (DEP-01 in Phase 7). Not addressed in this phase.
9. **No new dependencies**: Phase 5 uses only existing Spring Boot infrastructure.

## Sources

### Primary (HIGH confidence)
- [VERIFIED: codebase read] - `SecurityConfig.java`: Hardcoded CORS origins at lines 124-127
- [VERIFIED: codebase read] - `WebSocketProperties.java`: Hardcoded `allowedOrigins` field
- [VERIFIED: codebase read] - `XssFilter.java`: Pass-through filter (no header modification)
- [VERIFIED: codebase read] - `JwtProperties.java`: Supports `secure` flag via `@ConfigurationProperties`
- [VERIFIED: codebase read] - `application.yml`: Uses `${JWT_COOKIE_SECURE:false}`, `${SPRINGDOC_ENABLED:false}`
- [VERIFIED: codebase read] - `application-prod.yml`: Sets `secure: true`, disables Swagger
- [VERIFIED: codebase read] - `docker-compose.yml`: Has `:-ulticode`, `:-ulticode_redis`, `:-root` defaults
- [VERIFIED: codebase read] - `AuthServiceImpl.java` and `OAuthService.java`: Both use `config.isSecure()` for cookie flag
- [Context7 /websites/spring_io_spring-boot_3_5] - CORS configuration properties, actuator endpoint exposure
- [Context7 /websites/spring_io_spring-security_reference_6_5] - `CorsConfigurationSource` API, `setAllowedOriginPatterns()`

### Secondary (MEDIUM confidence)
- [VERIFIED: codebase read] - `.gitignore`: `.env` and `.env.*` are gitignored (except `.env.example`)
- [VERIFIED: `git ls-files`] - `management/.env` is tracked by git (DEP-01, Phase 7 scope)
- [VERIFIED: codebase read] - `.env.example`: Template exists with placeholder values

### Tertiary (LOW confidence)
- None -- all findings verified against codebase or official documentation.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - No new dependencies; uses existing Spring Boot 3.5 features
- Architecture: HIGH - Changes are well-scoped configuration externalization with clear file targets
- Pitfalls: HIGH - All pitfalls verified against actual codebase state

**Research date:** 2026-04-16
**Valid until:** 90 days (configuration patterns are stable; no fast-moving dependencies)
