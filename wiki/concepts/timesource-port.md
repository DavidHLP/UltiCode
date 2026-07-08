---
title: TimeSource Port (Wall + Monotonic Time Seam)
type: concept
tags: [backend, architecture, testing, type/concept]
status: living
updated: 2026-07-08
sources:
  - backend-spring/src/main/java/com/ulticode/common/time/
  - backend-spring/src/main/java/com/ulticode/common/metrics/SqlTimingInterceptor.java
  - backend-spring/src/main/java/com/ulticode/modules/monitoring/inspector/DefaultMonitoringInspector.java
aliases: [Time Port, Wall Clock Seam]
---

# TimeSource Port (Wall + Monotonic Time Seam)

## The problem

The backend still had two test-hostile time primitives that the
`Clock` seam (see [[concepts/clock-seam]]) did not cover:

- `System.currentTimeMillis()` — wall clock for trace ids, ranking-flusher
  cutoffs, and the `latency` field in `SystemHealthVO.HealthCheck`.
  Found in 14 sites across 9 files.
- `System.nanoTime()` — monotonic clock for measuring elapsed work
  in `SqlTimingInterceptor`. Found in 10 sites across 2 files.

Both patterns blocked testability:

- `SqlTimingInterceptorTest.slowQueryIncrementsBothCounters` had to
  `Thread.sleep(80)` to make the slow-query branch fire. Slow (80ms per
  test) and flaky on a loaded CI host.
- `DefaultMonitoringInspector.getHealthCheck()` reports a per-service
  `latency` field; tests could not pin the value, so the field was
  read-but-never-asserted.
- `WebSocketContestRankingFlusher` used `System.currentTimeMillis()`
  for both the throttle "elapsed since last push" math and the
  cleanup "entries older than 60s" cutoff. Tests of the throttle could
  not advance the clock without sleeping.

## The decision

A single `TimeSource` port in `common/time/`, exposing the two
primitives the rest of the backend actually needs:

```java
public interface TimeSource {
    long wallMillis();        // System.currentTimeMillis()
    long monotonicNanos();    // System.nanoTime()
}
```

Two adapters justify the seam per the architecture glossary:

| Adapter | Role | Bean? |
|---------|------|-------|
| `SystemTimeSource` | production, delegates to JVM | yes (`@Component`) |
| `FakeTimeSource` | test, deterministic values + `advance` / `advanceNanos` knobs | no, constructed by tests |

`TimeConfig` (parity with `ClockConfig` and `UuidConfig`) installs the
active source into `TimeSourceHolder` so static utility call sites
(`TraceIdUtil.current()`) read through the same port. The default
holder returns a fallback that talks to the JVM directly, so the app
boots even when no source was installed — a defensive posture for the
test paths that forget to install a fake.

## Why not extend `java.time.Clock`

`java.time.Clock` has `millis()` (wall clock) but **no** `nanoTime()`.
The monotonic clock is a different primitive (drift-corrected
high-resolution counter) and the JDK does not unify them. Adding a
second bean for "monotonic" would split one concept into two
injections at every call site, which trades a small testability gain
for a wider API surface. Two methods on one port is the deep-module
shape; two ports is shallow.

## Why a holder, not full DI for `TraceIdUtil`

`TraceIdUtil.current()` is called from 24 sites across 7 files. The
existing test pattern (`assertTrue(id.startsWith("t-"))`) does not
assert on the millis, so converting the utility to a `@Component`
and rewriting all 24 call sites would be 24-site churn for a tiny
testability gain. `TimeSourceHolder` concentrates the wall call in
one place and lets the rare "I need a pinned millis" test install a
fake via the same setter the rest of the seam uses.

## Migration scope (this commit)

Five sites migrated to inject `TimeSource`:

| File | Sites | Why it needed the seam |
|------|------:|------------------------|
| `DefaultMonitoringInspector` | 5 | `latency` field on health checks must be testable |
| `WebSocketContestRankingFlusher` | 3 | throttle + cleanup cutoff, sleep-free tests |
| `SqlTimingInterceptor` | 2 | drop `Thread.sleep(80)` from the slow-query test |
| `ContestWebSocketHandler` | 1 | pong `timestamp` field |
| `InMemoryRateLimiter` | 1 | rate-window expiry, deterministic rate-limit tests |

`TraceIdUtil` was updated to read through `TimeSourceHolder` so the
port is the single producer of `t-<millis>` trace ids.

## Not migrated (intentional)

| Site | Why left |
|------|----------|
| `AdminProblemServiceImpl` slug suffix | `UuidGenerator` (already exists) is the right seam; the millis is a uniqueness hack and the migration is out of scope for this sweep |
| `NotificationMessage.timestamp(System.currentTimeMillis())` | DTO field; rewriting the factory to take a `TimeSource` is a fan-out from the websocket factory and out of scope |
| `SandboxExecutorImpl` (8 sites of `System.nanoTime()`) | the docker subprocess timings are real wall-clock measurements; the unit tests do not exercise the path |

## Where it lives

- `common/time/TimeSource.java` — the port
- `common/time/SystemTimeSource.java` — production `@Component`
- `common/time/FakeTimeSource.java` — test adapter (not `@Component`)
- `common/time/TimeSourceHolder.java` — static accessor for utility sites
- `common/time/TimeConfig.java` — installs the production source at startup

## Trade-offs

- **5 constructor changes per migrated service** — `timeSource` is one
  more mock per `@InjectMocks` test. The Mockito pattern is well
  established in this codebase; the test churn is local.
- **`TimeSourceHolder` is a service-locator escape hatch** — but it is
  scoped to one utility (`TraceIdUtil`) and is the only sensible
  alternative to a 24-site churn.
- **The fallback in `TimeSourceHolder` masks test config mistakes** —
  the `install(null)` guard plus the `get()` fallback mean an app
  starts even if no source was installed. Acceptable; the
  `SystemTimeSource` is auto-discovered and `TimeConfig` installs it.

## Related

[[concepts/clock-seam]] · [[concepts/ratelimiter-port]] ·
[[concepts/module-layering]] · [[entities/monitoring]]
