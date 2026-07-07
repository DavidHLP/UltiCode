---
title: RateLimiter Port (Deep Module Extraction)
type: concept
tags: [backend, architecture, rate-limiting, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/common/ratelimiter/
  - backend-spring/src/main/java/com/ulticode/common/aspect/RateLimitAspect.java
  - /tmp/architecture-review-1783420414.html
aliases: [Rate Limiting Seam]
---

# RateLimiter Port (Deep Module Extraction)

## The problem

`@RateLimit` annotation carried 3 fields (key/limit/period), but the
enforcing aspect embedded:
- the Redis Lua script (`INCR` + `EXPIRE`),
- the `"rate-limit:"` key prefix,
- direct `StringRedisTemplate` injection.

155 `@RateLimit` sites across 33 controllers could not see what they
were getting. The aspect could not be unit-tested without Redis or
Testcontainers.

## The decision

Split the aspect's two responsibilities along the natural fault line:

1. **Request-context key generation** (placeholder substitution, user/IP
   detection) STAYS in `RateLimitAspect`. It legitimately needs
   `ProceedingJoinPoint` and `HttpServletRequest` — it cannot move
   behind a port.

2. **Rate-check mechanism** MOVES behind a port:
   - `common/ratelimiter/RateLimiter` — interface, single method
     `tryAcquire(bucket, limit, periodSeconds)` returning
     `AcquisitionVerdict{ allowed, retryAfterSeconds }`.
   - `common/ratelimiter/RedisRateLimiter` — production adapter, atomic
     `INCR`+`EXPIRE` Lua script. Preserves the pre-refactor sliding-window
     behavior exactly.
   - `common/ratelimiter/InMemoryRateLimiter` — test adapter, synchronized
     `ConcurrentHashMap`. Not a `@Component`; tests construct it directly.

The aspect now injects `RateLimiter` and delegates the actual check. The
`"rate-limit:"` key prefix lives inside `RedisRateLimiter.KEY_PREFIX`,
not in the aspect.

## Why two adapters justify the seam

Per the architecture-review glossary: "one adapter means a hypothetical
seam, two adapters means a real one." Here:
- **`RedisRateLimiter`** — production, must be atomic, must persist.
- **`InMemoryRateLimiter`** — tests, must be fast, must be deterministic.

Both implement the same interface. The seam is real.

## Why not move key generation into the port

The aspect's `substitutePlaceholders` reads method parameters via
reflection (`params[i].getAnnotation(PathVariable.class)`) and resolves
them against `joinPoint.getArgs()`. This requires the `ProceedingJoinPoint`
and the `Method` — neither of which a port should accept. Moving key
generation behind the port would force every adapter to reimplement
placeholder substitution.

The split is: **port owns storage-facing rate check; aspect owns
request-facing key generation.**

## Where it lives

- `common/ratelimiter/` — the port + 2 adapters + verdict
- `common/aspect/RateLimitAspect.java` — the consumer, now a thin delegator

## Trade-offs

- **InMemoryRateLimiter is not a `@Component`** — tests must construct
  it manually or wire it via test config. This is deliberate: production
  must always use `RedisRateLimiter`; auto-registering the in-memory
  version would risk accidental activation.
- **The sliding-window-via-EXPIRE quirk is preserved** — the Lua script
  refreshes TTL on every `INCR`, making the effective window "sliding
  since last activity" rather than "fixed since first request." Changing
  this is out of scope for the deep-module extraction.

## Related

[[concepts/module-layering]] · [[entities/auth]]
