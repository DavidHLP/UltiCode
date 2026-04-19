# Phase 19: Rate Limiting Infrastructure - Research

**Researched:** 2026-04-20
**Domain:** Spring Boot AOP + Redis distributed rate limiting
**Confidence:** HIGH

## Summary

The rate limiting infrastructure is substantially implemented. The `RateLimitAspect` and `@RateLimit` annotation are in place with atomic Redis Lua script operations, X-Real-IP client detection, and coverage across all four required endpoint groups. The only gap is the `Retry-After` HTTP response header -- `ErrorCode.TOO_MANY_REQUESTS` (HTTP 429) exists and `GlobalExceptionHandler` already returns 429 status, but the handler does not set the `Retry-After` header before writing the response. Adding this header to the exception handler is the single task that completes the phase.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Rate limit annotation + aspect | API/Backend | -- | AOP @Aspect in `common/aspect/` |
| Redis atomic counter (Lua) | API/Backend | -- | Redisson-backed StringRedisTemplate executes Lua script |
| Client IP detection | API/Backend | -- | X-Real-IP (nginx) preferred; fallback to remoteAddr |
| HTTP 429 response | API/Backend | -- | GlobalExceptionHandler maps BusinessException(TOO_MANY_REQUESTS) to 429 |
| Retry-After header | API/Backend | -- | Missing from GlobalExceptionHandler -- only gap |
| Redis key prefix | API/Backend | -- | All keys prefixed with `rate-limit:` in RateLimitAspect |

## User Constraints (from CONTEXT.md)

No CONTEXT.md exists for this phase. All three requirements are newly introduced in v1.5.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| RATE-01 | @RateLimit annotation implementation via Redisson AOP aspect with atomic tryAcquire() | RateLimitAspect uses StringRedisTemplate with Lua script (INCR + EXPIRE). Atomic via Redis single-threaded execution. [VERIFIED: source code inspection] |
| RATE-02 | Rate limit applied to all public API endpoints (auth, problem, submission, contest) | 20 @RateLimit annotations across 4 endpoint groups. Auth: 6, Problem: 3, Submission: 4, Contest: 7. [VERIFIED: grep across all controllers] |
| RATE-03 | Rate limit returns 429 with Retry-After header when exceeded | BusinessException(TOO_MANY_REQUESTS) returns HTTP 429 via GlobalExceptionHandler. **Retry-After header is MISSING.** [VERIFIED: source code inspection] |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-aop` | (managed by Spring Boot 3.5) | AOP aspect weaving for @RateLimit | Auto-configured; enables @Aspect/@Around |
| `spring-boot-starter-data-redis` | (managed) | StringRedisTemplate for Lua script execution | Native RedisTemplate, no extra deps |
| `Redisson` (via redisson-spring-boot-starter) | [ASSUMED] | Atomic Redis operations | Used by aspect; RedisConfig uses StringRedisTemplate not RedissonClient directly |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-validation` | (managed) | @Valid on DTOs | Already present; not directly related to rate limiting |
| `jackson-datatype-jsr310` | (managed) | Java 8 date/time JSON serialization | Configured in RedisConfig ObjectMapper |

**No additional installation required.** All dependencies are already in the project.

## Architecture Patterns

### System Architecture Diagram

```
HTTP Request
     |
     v
[Spring Security Filter Chain] --> [RateLimitAspect @Around]
                                          |
                                          | (Lua script: INCR + EXPIRE)
                                          v
                                   [Redis] (rate-limit:{key}:{ip})
                                          |
                                     count > limit?
                                          |
                    +---------------------+---------------------+
                    |                                           |
                   YES                                         NO
                    |                                           |
                    v                                           v
[throw BusinessException(TOO_MANY_REQUESTS)]         [joinPoint.proceed()]
                    |                                           |
                    v                                           v
[GlobalExceptionHandler]                              [Controller method]
     | (HTTP 429, Retry-After header missing)               |
     v                                                    v
[ResponseEntity<Result<Void>>]                      [Result<T>]
     |
     v
HTTP 429 + Result + (no Retry-After yet)
```

### Recommended Project Structure

Rate limiting lives entirely in `common/` (already implemented):

```
backend-spring/src/main/java/com/ulticode/common/
├── annotation/
│   └── RateLimit.java          # Already exists: key, limit, period
├── aspect/
│   └── RateLimitAspect.java   # Already exists: Lua script, X-Real-IP
├── exception/
│   ├── BusinessException.java  # Already exists: used by aspect
│   ├── ErrorCode.java          # Already exists: TOO_MANY_REQUESTS (42900)
│   └── GlobalExceptionHandler.java  # ALREADY EXISTS: returns 429, MISSING Retry-After header
└── config/
    └── RedisConfig.java        # Already exists: StringRedisTemplate configured
```

### Pattern 1: AOP Rate Limiting with Lua Script

The established pattern uses an `@Around` advice that executes a Lua script atomically before the target method:

```java
// Source: RateLimitAspect.java (already implemented)
private static final String RATE_LIMIT_SCRIPT =
        "local count = redis.call('INCR', KEYS[1]) " +
        "redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
        "return count";

@Around("@annotation(com.ulticode.common.annotation.RateLimit)")
public Object enforceRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
    // ... extract @RateLimit params, generate key with IP
    DefaultRedisScript<Long> script = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
    Long count = redisTemplate.execute(script, List.of(redisKey), String.valueOf(rateLimit.period()));
    if (count != null && count > rateLimit.limit()) {
        throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Rate limit exceeded...");
    }
    return joinPoint.proceed();
}
```

### Pattern 2: X-Real-IP Client Detection

```java
// Source: RateLimitAspect.java getClientIp() (already implemented)
private String getClientIp() {
    // Prefer X-Real-IP (set by nginx, not spoofable by clients)
    String ip = request.getHeader("X-Real-IP");
    if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
        return ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }
    // Fallback to remote address
    return request.getRemoteAddr();
}
```

### Pattern 3: GlobalExceptionHandler with Custom Headers

```java
// MISSING pattern -- needs to be added to GlobalExceptionHandler
@ExceptionHandler(BusinessException.class)
public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex,
        HttpServletResponse response) {  // inject response to set headers
    // ...
    if (ex.getHttpStatus() == HttpStatus.TOO_MANY_REQUESTS) {
        Long ttl = extractRetryAfterFromException(ex); // from error message or annotation
        response.setHeader("Retry-After", String.valueOf(ttl != null ? ttl : 60));
    }
    return ResponseEntity.status(ex.getHttpStatus()).body(result);
}
```

### Anti-Patterns to Avoid

- **Non-atomic check-then-act:** Do NOT use `if (get() > limit) throw; increment()` -- race condition between check and increment. The Lua script prevents this by combining INCR and EXPIRE atomically.
- **Per-user limits without IP fallback:** Do not require authenticated user for rate limiting -- anonymous endpoints (login, problem list) need IP-based limiting. Current implementation handles this correctly.
- **Long TTL on rate limit keys:** Do not set TTL longer than the window period. The Lua script sets TTL equal to `period` -- correct.
- **Hardcoding Retry-After:** Do not return a fixed Retry-After value -- extract from Redis TTL (`getExpire`) for accuracy.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Atomic rate limiting | Custom synchronized blocks or DB counters | Redis Lua script | Redis is single-threaded; Lua scripts execute atomically without explicit locks |
| Per-IP rate limiting | Application-level IP tracking | Redis keys with IP suffix | Distributed across instances; no memory leak from stale IPs |
| Rate limit config per endpoint | Hardcoded constants | @RateLimit annotation parameters | Already implemented with `limit()` and `period()` |
| Global exception mapping | Duplicate exception handlers | GlobalExceptionHandler | Already exists and handles all BusinessException uniformly |

**Key insight:** The hard part (atomic Redis operations, AOP weaving, client IP detection) is already done. Only the Retry-After header addition remains.

## Common Pitfalls

### Pitfall 1: Missing Retry-After Header
**What goes wrong:** Clients cannot determine when to retry, leading to blind retries that immediately hit the limit again.
**Why it happens:** GlobalExceptionHandler returns 429 status correctly but uses `ResponseEntity.status(ex.getHttpStatus()).body(result)` which only sets status -- it does not add response headers.
**How to avoid:** Add `HttpServletResponse` parameter to `handleBusinessException` and call `response.setHeader("Retry-After", ...)` when the status is TOO_MANY_REQUESTS. The TTL value should come from the exception message or from Redis TTL lookup.
**Warning signs:** Client logs showing immediate 429 re-requests; no `Retry-After` in response headers.

### Pitfall 2: IP Spoofing via X-Forwarded-For
**What goes wrong:** Clients set `X-Forwarded-For` header to bypass IP-based rate limits.
**Why it happens:** X-Forwarded-For is client-controlled; trusting it directly allows spoofing.
**How to avoid:** Only trust `X-Real-IP` (set by nginx, not the client). Current implementation does this correctly -- do NOT change to X-Forwarded-For.

### Pitfall 3: Lua Script Not Atomic Under Cluster
**What goes wrong:** In Redis Cluster mode, Lua scripts may not be atomic if keys hash to different slots.
**Why it happens:** Redis Cluster divides keys by slot; a Lua script touching multiple keys may not be atomic across nodes.
**How to avoid:** All rate limit keys use a single key (`rate-limit:{key}`) so they always hash to the same slot. No multi-key Lua script needed.

## Endpoint Coverage Analysis

All four required endpoint groups have `@RateLimit` coverage. Here is the verified inventory:

| Group | Controller | Endpoints with @RateLimit | Missing |
|--------|-----------|---------------------------|---------|
| **Auth** | `AuthController` | login(10/60s), register(5/60s), refresh(20/60s), logout(20/60s), forgot-password(5/60s), reset-password(5/60s) | None |
| **Problem** | `ProblemController` | create(30/60s), update(30/60s), delete(30/60s) -- admin operations | None |
| **Submission** | `ProblemSubmissionController` + `SubmissionController` | problem-submit(20/60s), problem-run(30/60s), best-submission(20/60s), create(20/60s) | None |
| **Contest** | `ContestController` | create(30/60s), update(30/60s), delete(30/60s), register(20/60s), unregister(20/60s), virtual-start(20/60s), virtual-finish(20/60s) | None |

**RATE-02 verification:** All four required endpoint groups have active `@RateLimit` annotations. [VERIFIED: grep @RateLimit across all controller files in modules/]

## Code Examples

### Current (incomplete) GlobalExceptionHandler
```java
// Source: GlobalExceptionHandler.java lines 35-42
@ExceptionHandler(BusinessException.class)
public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
    Result<Void> result = Result.error(ex.getCode(), ex.getMessage(), ex.getTraceId());
    return ResponseEntity.status(ex.getHttpStatus()).body(result);
    // MISSING: response.setHeader("Retry-After", ...) for 429
}
```

### Desired (complete) GlobalExceptionHandler
```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex,
        HttpServletResponse response) {
    if (ex.getHttpStatus() == HttpStatus.TOO_MANY_REQUESTS) {
        // Extract TTL from exception message or default to 60
        // Message format: "Rate limit exceeded. Please try again in {N} seconds."
        String msg = ex.getMessage();
        long retryAfter = 60;
        if (msg != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("try again in (\\d+) seconds?").matcher(msg);
            if (m.find()) retryAfter = Long.parseLong(m.group(1));
        }
        response.setHeader("Retry-After", String.valueOf(retryAfter));
    }
    Result<Void> result = Result.error(ex.getCode(), ex.getMessage(), ex.getTraceId());
    return ResponseEntity.status(ex.getHttpStatus()).body(result);
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|-----------------|--------------|--------|
| No rate limiting | @RateLimit AOP aspect with Redis Lua | v1.0 era (Apr 12-14, 2026) | All public endpoints protected |
| Redis INCR then EXPIRE (separate) | Atomic Lua script (INCR + EXPIRE in one call) | Apr 12, 2026 -- atomicity fix | Eliminates race condition between check and expire |
| X-Forwarded-For IP detection | X-Real-IP only (nginx-trusted) | Apr 12, 2026 -- IP spoofing fix | Prevents clients spoofing their IP |

**Deprecated/outdated:**
- Non-atomic Redis check-then-act patterns -- replaced by Lua script (Apr 12, 2026)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `spring-boot-starter-aop` is already in pom.xml | Standard Stack | If missing, add dependency. Low risk -- @Aspect is being picked up already. |
| A2 | GlobalExceptionHandler is the only place needing Retry-After | Architecture Patterns | If RateLimitAspect writes the response directly, this changes. Confirmed aspect throws exception, not writes response. |

**If this table is empty:** All claims in this research were verified or cited -- no user confirmation needed.

## Open Questions

1. **Should Retry-After value come from the exception message or a direct Redis TTL lookup?**
   - What we know: Exception message contains computed TTL; GlobalExceptionHandler has access to the exception.
   - What's unclear: Whether the exception message format is stable enough to parse.
   - Recommendation: Parse from message (simple regex) as a pragmatic approach. If format changes, update parser accordingly.

2. **Should there be separate rate limits for authenticated vs anonymous users?**
   - What we know: Current implementation uses IP-based limiting for all.
   - What's unclear: Whether authenticated users should have higher limits per IP.
   - Recommendation: Out of scope for v1.5. Current IP-based approach is sufficient.

## Environment Availability

Step 2.6: SKIPPED (no external dependencies identified beyond existing Redis and Spring Boot infrastructure which are already running).

## Validation Architecture

`nyquist_validation` is explicitly `false` in `.planning/config.json` -- this section is omitted.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V4 Access Control | yes | Rate limiting is access control (denial of service prevention) |
| V5 Input Validation | no | Not applicable to this phase |
| V3 Session Management | indirect | Rate limiting prevents credential stuffing attacks |

### Known Threat Patterns for Rate Limiting

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Credential stuffing (rapid login attempts) | Denial of Service | Auth group rate limited (10/min per IP) |
| API scraping / enumeration | Information Disclosure | All endpoint groups rate limited |
| DDoS via burst requests | Denial of Service | Redis Lua script enforces atomic sliding window |
| IP spoofing to bypass limits | Tampering | Only trust X-Real-IP (nginx-set), not client-controlled headers |

**No new security concerns introduced by this phase.** The Retry-After header addition is a standard HTTP compliance improvement.

## Sources

### Primary (HIGH confidence)
- `RateLimitAspect.java` -- Lua script pattern, X-Real-IP detection, exception throwing
- `GlobalExceptionHandler.java` -- current 429 handling (no Retry-After)
- `ErrorCode.java` -- TOO_MANY_REQUESTS(42900, ..., HttpStatus.TOO_MANY_REQUESTS)
- `@RateLimit` annotation -- key, limit, period parameters
- `RedisConfig.java` -- StringRedisTemplate configuration

### Secondary (MEDIUM confidence)
- Grep `@RateLimit` across all controllers -- endpoint coverage verified

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- existing implementation, no new libraries needed
- Architecture: HIGH -- correct AOP + Redis Lua pattern, IP spoofing fix applied
- Pitfalls: HIGH -- single gap identified (Retry-After header)

**Research date:** 2026-04-20
**Valid until:** 2026-05-20 (rate limiting patterns are stable; only HTTP header gap varies)
