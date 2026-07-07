---
title: Clock Seam (Time Abstraction)
type: concept
tags: [backend, architecture, testing, type/concept]
status: living
updated: 2026-07-07
sources:
  - backend-spring/src/main/java/com/ulticode/common/config/ClockConfig.java
  - /tmp/architecture-review-1783420414.html
  - wiki/concepts/module-layering.md
aliases: [Clock Bean, TimeService]
---

# Clock Seam (Time Abstraction)

## The problem

84 sites across 30 service implementations called `LocalDateTime.now()`
directly. Every time-sensitive test — subscription expiry, contest end,
queue retry-backoff, audit timestamp, achievement streak — fought the JVM
wall clock with no injectable seam. Tests had to either:
- mock the system clock via JVM flags (`-Dnow=...` doesn't exist),
- reflection-hack `System.currentTimeMillis()`,
- or skip time-sensitive test cases entirely.

The single existing `Clock` usage was in `ContestSubmissionAdapter` — the
pattern was right but never propagated.

## The decision

A single `@Bean Clock systemClock()` in `common/config/ClockConfig.java`,
returning `Clock.systemDefaultZone()`. Services inject `Clock clock` and
call `LocalDateTime.now(clock)` instead of `LocalDateTime.now()`.

Production behavior is unchanged — `Clock.systemDefaultZone()` is what
`LocalDateTime.now()` was using implicitly. Tests inject
`Clock.fixed(Instant, ZoneId)` for determinism.

## Why a `Clock` bean, not a custom `TimeService` interface

- **Standard library** — `java.time.Clock` is JDK-blessed, well-understood,
  and integrates with `LocalDateTime.now(clock)`, `Instant.now(clock)`,
  `OffsetDateTime.now(clock)`, etc.
- **No new vocabulary** — engineers already know `Clock`.
- **Mockito-friendly** — `Clock.fixed(...)` is immutable and trivially
  injected via `@MockBean` or `@Bean @Primary` in test config.
- **No abstraction cost** — a `TimeService` interface with `now()` would
  wrap `Clock` for no additional capability.

## Migration status

- **Phase 1 (committed `8e63050e2`):** `ClockConfig.systemClock()` bean
  exists, javadoc documents the migration pattern and lists all 84 sites.
- **Phase 2 (pending, mechanical per service):** migrate the 84 sites.
  Per service: add `Clock clock` to `@RequiredArgsConstructor`, replace
  `LocalDateTime.now()` with `LocalDateTime.now(clock)`, update
  `@InjectMocks` tests with `@Mock Clock`.

Top targets by site count: `ContestServiceImpl` (×6), `QueueServiceImpl`
(×6), `AdminAnalyticsServiceImpl` (×5-6), `DashboardServiceImpl` (×5),
`SolutionServiceImpl` (×5).

## Where it lives

- `backend-spring/.../common/config/ClockConfig.java` — the bean
- All migrated services inject `java.time.Clock`

## Trade-offs

- **One-line migration per call site** — cheap individually, 84 sites
  collectively. Each site also requires a Mockito test update (see
  `.agents/skills/mockito5-lombok-constructor-injection`).
- **Constructor signature churn** — services gain a `Clock` parameter.
  Acceptable; the alternative (no injectable time) blocks more tests.

## Related

[[concepts/module-layering]] · [[entities/contest]] · [[entities/submission]]
