---
phase: 09-foundation-ci
plan: 01
subsystem: infra
tags: [docker, dockerfile, maven, pnpm, nginx, csp]

# Dependency graph
requires: []
provides:
  - "Backend Dockerfile with predictable JAR path via finalName=app"
  - "Frontend Dockerfiles with pnpm-lock.yaml copies for frozen-lockfile installs"
  - ".dockerignore excluding AI tools, planning, recommendation service, and archives"
affects: [09-02, 10-cd]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Maven finalName normalization for Docker build reproducibility"
    - "Lockfile-first COPY pattern for pnpm frozen-lockfile Docker builds"

key-files:
  created: []
  modified:
    - backend-spring/pom.xml
    - backend-spring/Dockerfile
    - console/Dockerfile
    - management/Dockerfile
    - .dockerignore

key-decisions:
  - "Used <finalName>app</finalName> in Maven to normalize JAR name, avoiding SNAPSHOT version drift in Docker COPY paths"
  - "No changes needed to nginx CSP configs - already correctly configured with ${API_ORIGIN:-} template variable"

patterns-established:
  - "Maven finalName normalization: set <finalName>app</finalName> so Dockerfile COPY references never break on version changes"
  - "Lockfile-first Docker pattern: COPY package.json + pnpm-lock.yaml before source code for optimal layer caching"

requirements-completed: [FOUND-01, FOUND-02, FOUND-03, FOUND-04]

# Metrics
duration: 2min
completed: 2026-04-18
---

# Phase 09 Plan 01: Fix Dockerfile bugs and .dockerignore Summary

**Backend JAR name normalized via Maven finalName, frontend Dockerfiles fixed with lockfile copies, .dockerignore trimmed for smaller build context**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-18T04:14:18Z
- **Completed:** 2026-04-18T04:16:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Backend Dockerfile JAR path fixed: `COPY --from=builder /app/target/app.jar` (was broken SNAPSHOT name)
- Maven `<finalName>app</finalName>` added to prevent JAR name drift on version changes
- Console and Management Dockerfiles now copy `pnpm-lock.yaml` before `pnpm install --frozen-lockfile`
- `.dockerignore` updated to exclude `.claude/`, `.planning/`, `recommendation/`, `*.tar.gz`
- Nginx CSP verified correct: `connect-src 'self' ${API_ORIGIN:-}` with `proxy_pass http://backend:9001`

## Task Commits

All changes were pre-applied as prerequisite commits for parallel wave execution:

1. **Task 1: Fix backend Dockerfile JAR name and frontend lockfile copies** - `5ce8cb9c2` (fix)
2. **Task 2: Update .dockerignore and verify nginx CSP** - `574172a56` (feat)
3. **Combined prerequisite commit** - `574172a56` (feat) covered both tasks

**Note:** These changes were committed as prerequisites before the parallel worktree wave launched. The executor verified all acceptance criteria pass against the current codebase.

## Files Created/Modified
- `backend-spring/pom.xml` - Added `<finalName>app</finalName>` in build section (line 204)
- `backend-spring/Dockerfile` - Changed COPY to use `/app/target/app.jar` (line 27)
- `console/Dockerfile` - Added `COPY console/pnpm-lock.yaml ./console/` before install (line 11)
- `management/Dockerfile` - Added `COPY management/pnpm-lock.yaml ./management/` before install (line 11)
- `.dockerignore` - Added `.claude/`, `.planning/`, `recommendation/`, `*.tar.gz` entries

## Decisions Made
- Used `<finalName>app</finalName>` to decouple Docker COPY paths from Maven artifact version, eliminating a class of build failures when versions change
- Verified nginx CSP configs require no changes - both `console/nginx.conf` and `management/nginx.conf` already have correct `connect-src 'self' ${API_ORIGIN:-}` and `proxy_pass http://backend:9001`

## Deviations from Plan

None - plan executed exactly as written. All acceptance criteria verified passing.

## Issues Encountered

None. The changes were pre-applied as prerequisite commits for the parallel execution wave. The executor confirmed all fixes are in place and correct.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- All Dockerfiles are ready for CI build validation (Plan 09-02)
- .dockerignore is optimized for reduced build context
- Nginx CSP correctly configured for Docker Compose internal routing

---
*Phase: 09-foundation-ci*
*Completed: 2026-04-18*
