# Project Research Summary

**Project:** UltiCode v1.2 CI/CD Pipeline
**Domain:** CI/CD pipeline with GitHub Actions, Docker Compose, VPS deployment for existing monorepo
**Researched:** 2026-04-17
**Confidence:** HIGH

## Executive Summary

This milestone adds automated CI/CD to the UltiCode platform -- an existing Spring Boot 3.5 + Vue 3 monorepo that currently deploys manually via PM2 with Docker Compose for infrastructure services only (MySQL, Redis, Nacos). The project already has production-ready multi-stage Dockerfiles for all three application services (backend, console, management) and a `docker-compose.prod.yml` referencing GHCR image patterns. The CI/CD addition is an automation and hardening layer, not a new architecture.

The recommended approach uses GitHub Actions with three core workflows: a path-filtered CI pipeline (`ci.yml`) for PR validation (lint, type-check, test), a Docker build-and-push pipeline (`docker-publish.yml` or `cd.yml`) triggered on main-branch merges, and an SSH-based deploy pipeline (`deploy.yml`) that pulls images on the VPS and runs `docker compose up -d`. Key tooling includes `dorny/paths-filter@v4` for monorepo-aware change detection, `docker/build-push-action@v7` with GHA cache backend for layer caching, `docker/metadata-action@v6` for deterministic image tagging, and `appleboy/ssh-action@v1` for VPS deployment. All four Docker actions v7+ require Actions Runner v2.327.1+ (Node 24 runtime), which is met by `ubuntu-24.04`.

The key risks cluster around three areas: (1) pre-existing build breakage that will surface immediately in CI -- the backend Dockerfile references `ulticode-backend-0.0.1-SNAPSHOT.jar` while `pom.xml` declares `<version>1.0.0</version>`, which will cause every Docker build to fail at the COPY stage; (2) the gap between the current PM2-based dev workflow and Docker Compose production deployment -- 5 different configuration sources (root `.env`, `backend-spring/.env`, frontend `.env`, `ecosystem.config.cjs`, `docker-compose.prod.yml`) with inconsistent variable names and hostnames; and (3) the nginx CSP configuration defaulting to empty `API_ORIGIN`, which will block all API calls in Docker deployment. These must all be resolved in Phase 1 before any workflow files are written.

## Key Findings

### Recommended Stack

All additions are CI/CD tooling with zero new application dependencies. The existing stack (Spring Boot 3.5, Vue 3, MyBatis-Plus, MySQL, Redis, Nacos, Docker) is unchanged.

**Core technologies:**
- **GitHub Actions** (Runner v2.327.1+, `ubuntu-24.04`) -- CI/CD orchestration. Zero infra cost, native PR/GHCR integration. Free tier: 2000 min/month private, unlimited public.
- **Docker Buildx v0.20+** (`docker/setup-buildx-action@v4`) -- Multi-platform builds with GHA cache backend. Required by `build-push-action@v7`.
- **GitHub Container Registry** (`ghcr.io/{owner}/{image}`) -- Image storage. No rate limits for GHA, 500MB free private storage.
- **Docker Compose v2.x** -- Production deployment target on VPS. Same compose files drive both dev and production via env-var overrides.

**Key GitHub Actions (all versions verified 2026-04-17):**
- `actions/checkout@v6.0.2` -- Repository checkout
- `actions/setup-java@v5.2.0` -- JDK 17 (temurin) for backend builds
- `actions/setup-node@v6.3.0` + `pnpm/action-setup@v5.0.0` -- Node 22 + pnpm 9 for frontend builds
- `docker/build-push-action@v7.1.0` -- Build and push images with GHA cache
- `docker/metadata-action@v6.0.0` -- Deterministic tagging (sha, latest, semver)
- `dorny/paths-filter@v4` -- Monorepo change detection (avoid unnecessary builds)
- `appleboy/ssh-action@v1.2.5` -- VPS deployment via SSH

### Expected Features

**Must have (P1 -- table stakes for launch):**
- PR CI workflow (lint, type-check, test, build) with path filtering for all 3 components -- catches regressions before merge
- Docker image build + push to GHCR on main merge -- creates deployable artifacts
- Docker Compose deploy via SSH to VPS -- eliminates manual deployment
- GitHub Secrets configuration + branch protection -- security gate for merges

**Should have (P2 -- add when pipeline is stable):**
- Build caching (GHA cache backend + Maven/pnpm caches) -- 2-3x faster CI runs
- Dependabot for Actions -- automated version updates for security
- Concurrency groups on deploy -- prevent parallel deployments to same environment
- Automatic image tagging (`docker/metadata-action`) -- `sha-<commit>`, `latest` on main, semver on tags
- Rollback strategy -- manual trigger to redeploy previous image tag

**Defer to v2+:**
- Deploy preview environments (ephemeral per-PR) -- requires infrastructure investment
- Multi-environment pipelines (staging + production) -- not needed for single-server deployment
- Self-hosted runners -- only when free tier exhausted (>2000 min/month)
- Blue-green deployment -- overkill; rolling update via Compose healthchecks is sufficient

**Anti-features explicitly rejected:**
- Kubernetes manifests -- massive complexity for single-server deployment
- Monorepo build tools (Nx/Turborepo) -- only 3 independent apps, path filters suffice
- Docker-in-Docker for tests -- security risk; use GitHub Actions `services:` instead
- `latest` tag in production -- non-reproducible; use `sha-<commit>` tags

### Architecture Approach

The CI/CD pipeline follows a standard three-stage architecture: change detection, parallel build/test, and sequential deploy. All three application services (backend, console, management) are independent at build time and build in parallel. Runtime dependencies (MySQL, Redis, Nacos) are already running on the VPS and are not rebuilt.

**Major components:**
1. **CI workflow (`ci.yml`)** -- PR and push validation with `dorny/paths-filter` change detection. Three parallel jobs (backend, console, management), each running lint, type-check, test, and Docker image build. Backend tests use GitHub Actions `services:` for MySQL/Redis (avoids Docker-in-Docker).
2. **Docker publish workflow (`docker-publish.yml`)** -- Triggered on push to main. Three parallel build jobs push tagged images to GHCR. Uses `docker/metadata-action` for deterministic tagging and GHA cache backend for layer reuse.
3. **Deploy workflow (`deploy.yml`)** -- Triggered after successful publish via `workflow_run`. SSH into VPS, pull new images, restart with ordered health check waits (backend first, then frontends).
4. **docker-compose.prod.yml** -- Extends existing `docker-compose.yml` (infrastructure) with application services using GHCR images. Services reference `.env.production` for secrets (never committed to Git).
5. **`.dockerignore`** -- Reduces Docker build context by excluding `.git`, `.planning`, `recommendation/`, `db-manager/`, `node_modules/`.

**Key architectural decisions:**
- Separate CI and deploy workflows (PRs validated without triggering deployments)
- Build images in GitHub Actions (free compute, cached), not on the VPS
- Flyway migrations run on backend startup via Spring Boot auto-config (no separate CI step)
- Frontend `VITE_API_BASE_URL` injected as Docker build arg (Vite requires build-time env vars)
- Recommendation service excluded from CI/CD scope (optional, no Dockerfile exists)

### Critical Pitfalls

1. **Hardcoded JAR name mismatch in backend Dockerfile** -- `Dockerfile` copies `ulticode-backend-0.0.1-SNAPSHOT.jar` but `pom.xml` declares `<version>1.0.0</version>`. Every CI build will fail at COPY stage. Fix: use glob `target/*.jar`. Must be fixed before writing any workflow files.
2. **Monorepo path filtering "skipped but merged" silent failure** -- GitHub native `paths:` filters cannot express cross-service dependencies (e.g., `db-manager/migrations/` change should trigger backend rebuild). Fix: use `dorny/paths-filter` two-stage approach with explicit cross-references. Always include `workflow_dispatch` for manual overrides.
3. **Nginx CSP blocks API calls in Docker network** -- Dockerfile sets `ENV API_ORIGIN="http://localhost:9001"` but Docker Compose uses `http://backend:9001`. CSP `connect-src` will block all API requests. Fix: remove `API_ORIGIN` default from Dockerfile, pass it via `docker-compose.prod.yml`.
4. **Environment variable drift across 5-6 configuration sources** -- Root `.env`, `backend-spring/.env`, frontend `.env`, `ecosystem.config.cjs`, `docker-compose.prod.yml`, and GitHub Secrets all use slightly different variable names and hostnames (e.g., `localhost:23306` vs `mysql:3306`). Fix: create a single source-of-truth mapping document before writing deploy workflow.
5. **No zero-downtime deployment with Docker Compose** -- `docker compose up -d` stops old containers before starting new ones (1-30s downtime per service). Fix for v1: deploy during low-traffic windows with ordered restart (backend first, wait for healthy, then frontends). Zero-downtime via `docker-rollout` or Swarm mode can be a later enhancement.

## Implications for Roadmap

Based on research, suggested phase structure (3 phases):

### Phase 1: Foundation (Fix Pre-existing Issues + CI Workflow)
**Rationale:** Multiple pre-existing issues will cause immediate CI failures if not addressed first. The JAR name mismatch, nginx CSP configuration, environment variable mapping, and Maven wrapper state must all be resolved before writing any workflow files. Once fixed, the CI workflow itself follows well-established patterns (path-filtered parallel jobs with service containers).
**Delivers:** Fixed Dockerfiles, `.dockerignore`, environment mapping document, working `ci.yml` with path-filtered lint/test/build for all 3 components, branch protection rules.
**Addresses:** Features -- PR CI workflow, path-based triggering, build caching, status checks on PR. Pitfalls -- Pitfall 1 (JAR mismatch), Pitfall 2 (path filtering), Pitfall 3 (CSP), Pitfall 5 (Maven wrapper), Pitfall 6 (env drift), Pitfall 7 (build cache).
**Avoids:** Writing workflows that fail on first run due to pre-existing Dockerfile bugs.
**Key tasks:**
- Fix `backend-spring/Dockerfile` JAR name (glob pattern `target/*.jar`)
- Fix frontend Dockerfiles (copy `pnpm-lock.yaml`, add `VITE_API_BASE_URL` build arg)
- Remove `API_ORIGIN` default from frontend Dockerfiles
- Create `.dockerignore` to reduce build context
- Create environment variable mapping document (GitHub Secrets to Docker Compose)
- Write `ci.yml` with `dorny/paths-filter`, parallel jobs, GHA cache
- Configure branch protection (require CI pass before merge)

### Phase 2: Docker Build + Push + Deploy
**Rationale:** Once CI passes reliably on PRs, add the CD pipeline. Docker build and push to GHCR is low-risk (Dockerfiles are already proven from Phase 1 CI runs). SSH deployment adds the VPS integration which requires GitHub Secrets setup and server-side `.env.production` configuration.
**Delivers:** `docker-publish.yml` (build + push to GHCR on main merge), `deploy.yml` (SSH deploy with ordered restart), GitHub Secrets documentation, VPS setup guide.
**Addresses:** Features -- Docker image build/push to GHCR, Docker Compose deploy via SSH, automatic image tagging, concurrency groups. Pitfalls -- Pitfall 4 (zero-downtime, mitigated with ordered restart).
**Uses:** All Docker actions from STACK.md, `appleboy/ssh-action`, `docker/metadata-action`.
**Implements:** SSH-based VPS deployment pattern from ARCHITECTURE.md.
**Key tasks:**
- Write `docker-publish.yml` with parallel build jobs and GHA cache
- Write `deploy.yml` with SSH deploy and health check verification
- Set up GitHub Secrets (VPS_HOST, VPS_USER, VPS_SSH_KEY, VPS_PORT)
- Create VPS setup guide (Docker, Compose, `.env.production`, reverse proxy)
- Add concurrency groups to prevent parallel deployments
- Document recommendation service exclusion decision

### Phase 3: Hardening and Polish
**Rationale:** With the basic pipeline running, add quality-of-life improvements that make the pipeline more robust and self-maintaining. These are all low-effort additions that provide measurable improvement.
**Delivers:** Dependabot for Actions, rollback workflow, composite actions (if needed), deployment notifications.
**Addresses:** Features -- Dependabot, rollback strategy, Slack/Discord notifications, composite actions.
**Key tasks:**
- Add `.github/dependabot.yml` for Actions ecosystem
- Create rollback workflow (manual trigger to redeploy previous image tag)
- Add deployment notification webhook (optional)
- Extract reusable composite actions if a 4th+ workflow is needed
- Document the full CI/CD setup in project README

### Phase Ordering Rationale

- **Foundation first:** Pre-existing Dockerfile bugs, env drift, and CSP misconfiguration will cause every CI run to fail. These must be fixed before workflows are written. The CI workflow itself is the highest-value single artifact (catches bugs before merge).
- **Deploy second:** Docker images are already proven by Phase 1 CI builds. Adding GHCR push and SSH deploy is the logical next step. The deploy phase requires VPS-side setup (`.env.production`, Docker, reverse proxy) which is a one-time operational task.
- **Hardening third:** All polish features (Dependabot, rollback, notifications) are independent of each other and of Phases 1-2. They improve the pipeline but are not required for a working deployment.
- **Recommendation service excluded:** No Dockerfile exists, and it is optional (`RECOMMENDATION_ENABLED=true` can be `false`). Explicitly document this exclusion decision in Phase 2.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1 (Backend tests in CI):** Backend tests currently use Testcontainers which requires Docker-in-Docker. Research whether to restructure to use GitHub Actions `services:` (mysql, redis) instead, or if Testcontainers with Docker socket exposure is acceptable. The FEATURES.md recommends `services:` approach, but the actual test configuration needs inspection.
- **Phase 2 (VPS reverse proxy):** The architecture assumes Nginx or Caddy as a reverse proxy on the VPS for TLS termination, but the exact configuration and Let's Encrypt setup are not covered in research. This is operational, not code, but needs a setup guide.

Phases with standard patterns (skip research-phase):
- **Phase 1 (CI workflow):** Well-documented GitHub Actions patterns. `dorny/paths-filter`, `docker/build-push-action`, and `actions/cache` have extensive official documentation and community examples. The monorepo CI pattern is standard.
- **Phase 2 (Docker build + SSH deploy):** Standard Docker/GitHub Actions integration. `appleboy/ssh-action` is widely used (9k+ stars). Docker Compose deployment is the most common single-server pattern.
- **Phase 3 (Hardening):** All features are well-documented. Dependabot config is a single YAML file. Rollback via image tag is standard Docker practice.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All action versions verified via GitHub Releases API on 2026-04-17. Docker action v7 requirements (Runner v2.327.1+) confirmed. Existing Dockerfiles analyzed directly. |
| Features | HIGH | Feature prioritization based on direct codebase inspection (Dockerfiles, docker-compose files, package.json scripts). MVP definition aligns with standard CI/CD for this stack. Anti-features well-justified with alternatives. |
| Architecture | HIGH | Architecture follows established GitHub Actions + Docker Compose + VPS pattern. Data flow, component responsibilities, and anti-patterns all supported by official documentation (Docker, GitHub). Existing project files (Dockerfiles, nginx.conf, docker-compose.yml) analyzed directly. |
| Pitfalls | HIGH | Most critical pitfalls (JAR mismatch, CSP, env drift) discovered through direct codebase inspection with file:line evidence. Security pitfalls from official GitHub and Docker documentation. Recovery strategies well-documented. |

**Overall confidence:** HIGH

### Gaps to Address

- **Backend test configuration for CI:** Tests use Testcontainers. Decision needed: restructure to use GitHub Actions `services:` (simpler, no Docker-in-Docker) or keep Testcontainers with Docker socket. The `services:` approach is recommended but requires a `application-ci.yml` Spring profile. This should be resolved during Phase 1 planning.
- **pnpm-lock.yaml committed status:** Frontend Dockerfiles use `pnpm install --frozen-lockfile`. If lockfiles are gitignored, builds will fail. Must verify during Phase 1.
- **Recommendation service CI/CD decision:** No Dockerfile exists. Either add a build job (requires Dockerfile creation) or explicitly exclude with documentation. Decision should be made in Phase 2.
- **VPS specifications unknown:** Docker Compose with 5 services (3 apps + MySQL + Redis) requires minimum 2-4 GB RAM. Actual VPS specs should be verified to ensure sufficient resources.
- **Exact GitHub Actions runner disk space:** GHA cache has 10 GB limit per repository. With 3 services using `mode=max` Docker cache plus Maven/pnpm caches, this may fill up. Monitor during Phase 1 and switch to scoped/per-service caches if needed.

## Sources

### Primary (HIGH confidence)
- All action versions verified via GitHub Releases API on 2026-04-17
- GitHub Docs: "Using GitHub Actions" -- https://docs.github.com/en/actions
- Docker Docs: "Build with GitHub Actions / Cache" -- https://docs.docker.com/build/ci/github-actions/cache/
- Docker Docs: "Publishing Docker Images" -- https://docs.github.com/en/actions/packaging-with-github-actions/publishing-and-installing-a-package-with-github-actions-publishing-docker-images
- Direct source code analysis: all 3 Dockerfiles, docker-compose.yml, docker-compose.prod.yml, pom.xml, nginx.conf files, ecosystem.config.cjs, package.json files, .env.example files

### Secondary (MEDIUM confidence)
- GitHub Actions in 2026: Complete Guide to Monorepo CI/CD (dev.to, 2026)
- GitHub Actions Advanced Patterns: Reusable Workflows, Composite Actions & Monorepo (youngju.dev, 2026-03)
- How to Configure GitHub Actions for Monorepos (oneuptime.com, 2026-02)
- Speeding Up Slow Docker Builds in GitHub Actions (Medium)
- Top 5 Mistakes Developers Make When Using GitHub Actions (Medium)

### Tertiary (LOW confidence)
- None -- all findings are supported by either official documentation or direct codebase inspection.

---
*Research completed: 2026-04-17*
*Ready for roadmap: yes*
