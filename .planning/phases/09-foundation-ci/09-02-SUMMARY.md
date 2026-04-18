---
phase: 09-foundation-ci
plan: 02
subsystem: infra
tags: [spring-boot, ci, github-actions, secrets, configuration, flyway]

# Dependency graph
requires:
  - phase: []
    provides: []
provides:
  - application-ci.yml Spring profile for GitHub Actions CI test runs
  - docs/secrets-mapping.md cross-referencing all 6 configuration sources
affects: [09-03, 10-*, cd-deploy]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CI profile with env var defaults matching GHA service containers"
    - "Comprehensive secrets mapping across 6 config sources"

key-files:
  created:
    - backend-spring/src/main/resources/application-ci.yml
    - docs/secrets-mapping.md
  modified: []

key-decisions:
  - "CI profile uses localhost:23306/26379 port mappings matching GHA services: containers"
  - "CI profile disables Testcontainers and enables Flyway with baseline-on-migrate"
  - "JWT test secret has 32+ character default so CI works without GitHub Secrets"
  - "Secrets mapping document references variable names only, never actual values"

patterns-established:
  - "Spring profile per environment (dev, prod, ci, example) with env var defaults"
  - "Centralized secrets documentation for onboarding and CI setup"

requirements-completed: [FOUND-05, FOUND-06]

# Metrics
duration: 1min
completed: 2026-04-18
---

# Phase 09 Plan 02: CI Profile and Secrets Mapping Summary

**application-ci.yml Spring profile for GitHub Actions CI tests with service containers, and comprehensive secrets mapping document covering all 6 configuration sources**

## Performance

- **Duration:** 1 min
- **Started:** 2026-04-18T04:18:08Z
- **Completed:** 2026-04-18T04:18:29Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Created application-ci.yml that replaces Testcontainers Docker lifecycle with GitHub Actions service containers
- Built comprehensive secrets mapping document cross-referencing all 6 configuration sources with 133 lines of documentation
- CI profile has sensible defaults so tests run with just `-Dspring.profiles.active=ci` without extra env vars

## Task Commits

Each task was committed atomically:

1. **Task 1: Create application-ci.yml Spring profile for GitHub Actions** - `d7858845f` (feat)
2. **Task 2: Create secrets mapping document (FOUND-06)** - `d1f92edd9` (feat)

## Files Created/Modified
- `backend-spring/src/main/resources/application-ci.yml` - CI-specific Spring Boot configuration with MySQL 23306, Redis 26379, Testcontainers disabled, Flyway enabled, ddl-auto none
- `docs/secrets-mapping.md` - Cross-reference of all 6 configuration sources (GitHub Secrets, Docker Compose, Spring profiles, Vite, PM2, Backend .env) with 30+ variables mapped

## Decisions Made
- **localhost:23306/26379 for CI**: Matches GitHub Actions services: port mappings (23306:3306 MySQL, 26379:6379 Redis) so no extra env var configuration needed
- **Testcontainers disabled**: `spring.testcontainers.enabled: false` prevents SubmissionServiceImplIT from trying Docker-in-Docker
- **Flyway with baseline-on-migrate**: CI databases are created by GHA services without Flyway history, so baseline-on-migrate provides clean starting point
- **32+ char JWT default**: CI profile has a default JWT secret so tests run without setting GitHub Secrets
- **No actual secrets in mapping doc**: Threat T-09-03 mitigation -- document only references variable names and default values

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None - both files were verified against all acceptance criteria and passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- application-ci.yml is ready for ci-backend.yml workflow to use `-Dspring.profiles.active=ci`
- secrets-mapping.md provides reference for Phase 10 CD deployment secret configuration
- No blockers or concerns

---
*Phase: 09-foundation-ci*
*Completed: 2026-04-18*
