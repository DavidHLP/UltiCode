---
phase: 01-security-filter-chain
plan: 02
subsystem: auth
tags: [spring-security, csrf, servlet-filter, jwt, redis]

# Dependency graph
requires: []
provides:
  - CsrfValidationFilter running after JWT auth in Spring Security filter chain
  - CsrfService (Redis-backed) unchanged, validated by filter instead of interceptor
affects: [01-03, 02-security-policies]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Custom OncePerRequestFilter for CSRF validation placed after JWT auth filter"

key-files:
  created:
    - backend-spring/src/main/java/com/ulticode/security/csrf/CsrfValidationFilter.java
  modified:
    - backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java
    - backend-spring/src/main/java/com/ulticode/common/config/WebMvcConfig.java
  deleted:
    - backend-spring/src/main/java/com/ulticode/security/csrf/CsrfInterceptor.java

key-decisions:
  - "Custom CsrfValidationFilter (not Spring Security built-in CsrfFilter) because built-in runs before JWT auth"
  - "Anonymous/unauthenticated requests skip CSRF validation naturally (no explicit path exemptions needed)"

patterns-established:
  - "Security filter ordering: JWT auth first, then CSRF validation after SecurityContext is populated"

requirements-completed: [SEC-01]

# Metrics
duration: 2min
completed: 2026-04-14
---

# Phase 01 Plan 02: Migrate CSRF from WebMvc Interceptor to Security Filter Summary

**CsrfValidationFilter servlet filter validates CSRF tokens after JWT auth in the Spring Security chain, replacing the WebMvc-layer CsrfInterceptor**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-14T15:19:47Z
- **Completed:** 2026-04-14T15:21:53Z
- **Tasks:** 1
- **Files modified:** 4 (1 created, 2 modified, 1 deleted)

## Accomplishments
- CSRF validation moved from WebMvc interceptor layer to Spring Security filter chain
- CsrfValidationFilter runs after JwtAuthenticationFilter, guaranteeing authenticated user access
- Login/register endpoints are naturally exempt (no JWT = no authenticated principal = skip CSRF)
- CsrfInterceptor.java fully removed from codebase

## Task Commits

Each task was committed atomically:

1. **Task 1: Create CsrfValidationFilter, register in SecurityConfig, remove CsrfInterceptor from WebMvcConfig, delete CsrfInterceptor** - `17859d169` (feat)

## Files Created/Modified
- `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfValidationFilter.java` - New servlet filter extending OncePerRequestFilter; validates X-CSRF-Token header on POST/PUT/DELETE/PATCH for authenticated users; delegates to CsrfService for Redis-backed token validation and rotation; returns new token in X-New-CSRF-Token response header
- `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java` - Added CsrfService dependency injection; registered CsrfValidationFilter after JwtAuthenticationFilter via addFilterAfter
- `backend-spring/src/main/java/com/ulticode/common/config/WebMvcConfig.java` - Removed all CsrfInterceptor references; class now contains only @Configuration annotation (CORS handled by SecurityConfig)
- `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfInterceptor.java` - Deleted (replaced by CsrfValidationFilter)

## Decisions Made
- Custom CsrfValidationFilter (not built-in CsrfFilter): Spring Security's built-in CsrfFilter runs before JwtAuthenticationFilter in the default chain order, so it cannot access the authenticated user. A custom OncePerRequestFilter placed after JWT auth solves this.
- No explicit path exemptions needed: The filter checks authentication state from SecurityContext. Unauthenticated requests (login, register, etc.) have null or anonymous authentication and are skipped automatically. This is cleaner than maintaining an exclude list.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Pre-existing compilation error in SubmissionServiceImpl.java (missing LanguageStatsDTO, MonthlySubmissionStatsDTO, WeeklyProgressDTO). Out of scope for this task. Verified our changed files have no compilation errors.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- CSRF protection is now unified in the security filter chain
- CsrfService API unchanged -- no impact on token generation flow in AuthController
- Frontend behavior unchanged: still reads X-New-CSRF-Token response header, sends X-CSRF-Token request header
- Ready for plan 01-03 and subsequent security policy plans

## Self-Check: PASSED

- `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfValidationFilter.java`: EXISTS
- `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfInterceptor.java`: DELETED (verified via git)
- `17859d169`: COMMIT EXISTS

---
*Phase: 01-security-filter-chain*
*Completed: 2026-04-14*
