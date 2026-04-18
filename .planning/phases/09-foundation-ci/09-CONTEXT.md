# Phase 9: Foundation + CI - Context

**Gathered:** 2026-04-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix all pre-existing Dockerfile and configuration bugs (FOUND-01 through FOUND-06), and create a unified `ci.yml` GitHub Actions workflow that validates every PR with lint, type-check, and test across all 3 services using path-filtered parallel jobs and build caching.

**What's IN scope:**
- Fix backend Dockerfile JAR name mismatch (FOUND-01)
- Fix frontend Dockerfiles to copy pnpm-lock.yaml before install (FOUND-02)
- Verify/fix nginx CSP connect-src for Docker Compose (FOUND-03)
- Review/update root .dockerignore (FOUND-04)
- Create application-ci.yml Spring profile (FOUND-05)
- Create secrets mapping document (FOUND-06)
- Create unified ci.yml with dorny/paths-filter (CI-01 through CI-06)

**What's OUT of scope:**
- CD pipeline (Phase 10)
- Dependabot / rollback workflows (Phase 11)
- Monitoring / advanced CI features (v2 requirements)
- Recommendation service CI/CD (explicitly out of scope per REQUIREMENTS.md)
- Branch protection rules (manual GitHub settings, not code)

</domain>

<decisions>
## Implementation Decisions

### Dockerfile Fixes
- **D-01:** Use Maven `<finalName>app</finalName>` in backend pom.xml to generate a predictable `app.jar`, permanently eliminating version-matching issues between Dockerfile COPY and Maven build output. This is better than hardcoding the version string because it survives pom.xml version bumps.
- **D-02:** Frontend Dockerfiles must COPY `pnpm-lock.yaml` before `pnpm install --frozen-lockfile`. The lockfiles exist at `console/pnpm-lock.yaml` and `management/pnpm-lock.yaml` respectively. Add COPY step between package.json copy and install step for proper Docker layer caching.
- **D-03:** Nginx CSP `connect-src` is already correct — uses `'self' ${API_ORIGIN:-}` which covers both same-origin API proxy and configurable external origins. The `proxy_pass http://backend:9001` handles Docker Compose internal routing. No changes needed unless testing reveals issues.

### .dockerignore
- **D-04:** Root `.dockerignore` already exists with reasonable content. Review and add missing entries: `.claude/`, `.planning/`, `recommendation/`, `*.tar.gz`. The existing file correctly avoids excluding Dockerfile* and docker-compose*.yml which are needed by multi-stage builds.

### CI Workflow Structure
- **D-05:** Create a single unified `ci.yml` workflow that replaces existing separate `ci-backend.yml` and `ci-frontend.yml` files. The requirement explicitly calls for "ci.yml workflow triggers on pull_request and push to main, with dorny/paths-filter for monorepo path detection." Archive (delete) old separate files after unified workflow is validated.
- **D-06:** Use `dorny/paths-filter` action for path-based job gating rather than native GitHub `paths:` triggers. This enables a single workflow file with fine-grained conditional job execution and outputs that downstream jobs can reference.
- **D-07:** Keep `ci-recommendation.yml` as-is — the recommendation service is explicitly out of scope per REQUIREMENTS.md. Only modify if it breaks during consolidation.

### Backend CI Configuration
- **D-08:** Create `application-ci.yml` Spring profile in `backend-spring/src/main/resources/`. This profile should:
  - Set `spring.datasource.url` to use `localhost:23306/ulticode_test` (GitHub Actions services: containers)
  - Set `spring.data.redis.host` to `localhost` and `spring.data.redis.port` to `26379`
  - Disable Testcontainers auto-configuration if present
  - Set `spring.jpa.hibernate.ddl-auto` to `none` (Flyway handles migrations)
  - Enable `spring.flyway.enabled=true` with clean baseline
- **D-09:** Backend test job uses `./mvnw test -Dspring.profiles.active=ci -B` to activate the CI profile. This replaces the current approach of passing env vars directly, making the CI configuration self-documenting and reproducible locally.

### Frontend CI
- **D-10:** Console and management CI jobs each run lint + type-check + test independently when their respective paths change. Use the existing pattern (pnpm/action-setup + setup-node with pnpm cache). Each app has its own `pnpm-lock.yaml` for cache isolation.

### Docker Build Verification
- **D-11:** Add a `docker-verify` job that builds all 3 Docker images (backend, console, management) WITHOUT pushing. This job is path-filtered — only runs when `Dockerfile*`, `docker-compose*.yml`, `.dockerignore`, or `nginx*.conf` files change. Purpose: catch Dockerfile breakage before merge.

### Build Caching
- **D-12:** Maven cache: continue using `setup-java` with `cache: 'maven'` (already works well)
- **D-13:** pnpm cache: continue using `setup-node` with `cache: 'pnpm'` per app's lockfile
- **D-14:** Docker layer cache: use `cache-from: type=gha, cache-to: type=gha,mode=max` for Docker build verification job

### Secrets Mapping
- **D-15:** Create `docs/secrets-mapping.md` as a Markdown table document. Cross-reference all 6 configuration sources:
  1. GitHub Actions Secrets (repository-level)
  2. Docker Compose environment variables (docker-compose.yml / docker-compose.prod.yml)
  3. Spring Boot profiles (application-*.yml)
  4. Vite environment variables (VITE_* in .env files)
  5. PM2 ecosystem config (ecosystem.config.cjs)
  6. Backend .env file (local development)
  Each row maps: variable name → source → used by → notes

### Claude's Discretion
- Exact job dependency graph in ci.yml (which jobs run in parallel vs sequential)
- Error handling and notification patterns in CI workflows
- Test artifact upload configuration
- Concurrency group naming conventions

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — CI/CD pipeline requirements (FOUND-01 through CI-06)
- `.planning/ROADMAP.md` §Phase 9 — Phase definition, success criteria, and plan outline

### Existing CI/CD Infrastructure (must read to understand current state)
- `.github/workflows/ci-backend.yml` — Existing backend CI (build, test, migrate-validate, docker push)
- `.github/workflows/ci-frontend.yml` — Existing frontend CI (lint, type-check, test, build, docker push)
- `.github/workflows/ci-recommendation.yml` — Recommendation service CI (keep as-is)
- `.github/workflows/cd-deploy.yml` — Existing CD deploy workflow (Phase 10 scope, read-only reference)

### Dockerfiles to Fix
- `backend-spring/Dockerfile` — JAR name mismatch on line with COPY from builder stage
- `console/Dockerfile` — Missing pnpm-lock.yaml COPY before install
- `management/Dockerfile` — Missing pnpm-lock.yaml COPY before install

### Configuration Files
- `console/nginx.conf` — CSP connect-src verification
- `management/nginx.conf` — CSP connect-src verification
- `.dockerignore` — Review and update
- `backend-spring/pom.xml` — Add `<finalName>app</finalName>` in build section
- `backend-spring/src/main/resources/application-dev.yml` — Reference for CI profile structure
- `backend-spring/src/main/resources/application-prod.yml` — Reference for CI profile structure
- `docker-compose.yml` — Docker Compose service definitions
- `docker-compose.prod.yml` — Production Docker Compose with GHCR images

### Project Documentation
- `CLAUDE.md` — Project overview, port reference, service architecture, PM2 commands
- `ecosystem.config.cjs` — PM2 service configuration (env vars reference)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Existing CI workflows** (`ci-backend.yml`, `ci-frontend.yml`): Well-structured with path filtering, concurrency groups, build caching, and Docker push. Large portions can be ported directly into the unified `ci.yml`.
- **Backend CI services configuration**: Already uses GitHub Actions `services:` for MySQL 9.1 and Redis 7-alpine with health checks. This pattern should be preserved in the unified workflow.
- **Frontend CI matrix pattern**: Uses `matrix: app: [console, management]` to run lint/type-check/test for both apps. This pattern should be preserved.
- **Docker build-push pattern**: Already uses `setup-buildx-action`, `login-action`, `metadata-action`, and `build-push-action` with GHA cache. The `docker-verify` job can reuse this pattern without the push step.

### Established Patterns
- **Path filtering**: Current CI uses native GitHub `paths:` triggers. New unified workflow should switch to `dorny/paths-filter` for more granular control within a single workflow.
- **Build caching**: Maven via setup-java, pnpm via setup-node, Docker via GHA cache backend. All established patterns.
- **Spring profiles**: Dev, prod, and example profiles already exist. CI profile follows same naming convention (`application-ci.yml`).

### Integration Points
- **Backend pom.xml**: Adding `<finalName>app</finalName>` in `<build>` section changes the JAR output name — Dockerfile COPY must match
- **Frontend Dockerfiles**: Adding pnpm-lock.yaml COPY changes Docker layer caching — must be placed between package.json COPY and pnpm install
- **Nginx configs**: Already use envsubst templates (`${API_ORIGIN:-}`) — compatible with Docker Compose env variable injection
- **PM2 ecosystem**: References env vars that should be documented in secrets mapping

### Key Observations
1. The existing CI infrastructure is MATURE — most CI-01 through CI-06 requirements are already met by current workflows. The main change is consolidation into a single file with dorny/paths-filter.
2. FOUND-01 is the most impactful Dockerfile bug — it would cause backend Docker build to fail since `ulticode-backend-0.0.1-SNAPSHOT.jar` doesn't exist (actual artifact is `ulticode-backend-1.0.0.jar`).
3. FOUND-02 would cause frontend Docker builds to fail with `--frozen-lockfile` since pnpm-lock.yaml isn't available in the Docker context at install time.
4. The nginx CSP (FOUND-03) appears already correct based on code review.
5. `.dockerignore` (FOUND-04) exists but could be more comprehensive.

</code_context>

<specifics>
## Specific Ideas

- Maven `<finalName>app</finalName>` is the cleanest solution for FOUND-01 — it makes the JAR path predictable regardless of pom.xml version changes. Both Dockerfile and CI can reference `app.jar` confidently.
- The unified `ci.yml` should use `dorny/paths-filter@v3` with a `changes` job that outputs boolean flags (`backend`, `console`, `management`, `docker`) used by downstream job `if:` conditions.
- Docker build verification (CI-05) should be a separate job that ONLY runs when Docker-related files change — building all 3 images on every PR would waste ~10 minutes of CI time.
- The secrets mapping document should include a "GitHub Secrets Name" column for variables that need to be configured in repository settings — this makes onboarding new contributors easier.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 09-foundation-ci*
*Context gathered: 2026-04-18*
