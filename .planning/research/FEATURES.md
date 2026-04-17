# Feature Research: CI/CD Pipeline with GitHub Actions + Docker Compose

**Domain:** CI/CD Pipeline for Spring Boot + Vue 3 Monorepo
**Researched:** 2026-04-17
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features any CI/CD pipeline for this stack must have. Missing these = the pipeline is not production-viable.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **PR Lint/Type-Check** | Catches regressions before merge | LOW | Both frontends already have `pnpm lint` + `vue-tsc --build` scripts. Backend has Maven. Path-filter so only changed components run checks. |
| **PR Test Execution** | Validates correctness before merge | MEDIUM | Backend uses Testcontainers (MySQL) requiring service containers in CI. 34 test classes exist. Frontends use Vitest. |
| **Docker Image Build** | Creates deployable artifacts from multi-stage Dockerfiles | LOW | Three Dockerfiles already exist (backend-spring, console, management). All use multi-stage builds with non-root users and healthchecks. |
| **Image Push to GHCR** | Stores versioned images for deployment | LOW | `docker-compose.prod.yml` already references `${GHCR_REGISTRY:-ghcr.io/davidhlp/ulticode-public-next}/backend:${IMAGE_TAG:-latest}` pattern. Use `docker/build-push-action` with `type=gha` cache. |
| **Path-Based Triggering** | Avoids running backend CI when only console changed | LOW | Use `paths:` filters on workflow triggers. Three source trees: `backend-spring/`, `console/`, `management/`. |
| **Environment Secrets** | Credentials never in repo | LOW | GitHub Secrets for JWT_SECRET, DB_PASSWORD, GHCR_TOKEN. `.env.example` files already exist as templates. `.env` and `backend-spring/.env` are gitignored. |
| **Build Caching** | CI runs under 10 min, not 30+ | MEDIUM | Maven `dependency:go-offline` layer in backend Dockerfile. pnpm store cache for frontends. `actions/cache` or Docker BuildKit `type=gha` cache. |
| **Status Checks on PR** | Branch protection gates merge on passing CI | LOW | Set `ci.yml` as required status check. Separate lint, test, and build jobs for granular failure diagnosis. |

### Differentiators (Competitive Advantage)

Features that go beyond basics. Valuable but not required for v1.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Reusable Composite Actions** | Shared setup logic across workflows, single source of truth for pnpm/Maven/JDK versions | MEDIUM | `.github/actions/setup-node/action.yml` and `.github/actions/setup-java/action.yml`. Reduces duplication when adding new workflows. |
| **Docker Compose Deploy via SSH** | One-command production deployment to remote server | MEDIUM | `appleboy/ssh-action` to run `docker compose pull && docker compose up -d` on remote host. `docker-compose.prod.yml` already structured for this. |
| **Automatic Image Tagging** | Every build gets `sha-<short>`, `latest` on main, semver on tags | LOW | `docker/metadata-action@v5` handles this. Already standard pattern for GHCR. |
| **Concurrency Groups** | Prevents parallel deploys to same environment | LOW | `concurrency: { group: deploy-production, cancel-in-progress: false }` on deploy workflow. |
| **Deploy Preview on PR** | Spin up ephemeral environment per PR for visual review | HIGH | Requires a staging server or ephemeral containers. Overkill for v1 but valuable later. |
| **Dependabot for Actions** | Auto-updates GitHub Actions versions | LOW | `.github/dependabot.yml` with `github-actions` ecosystem. Security patch automation. |
| **Slack/Discord Notification** | Team visibility into deploy status | LOW | Webhook on workflow completion. Nice but not critical. |
| **Rollback Strategy** | Revert to previous image tag on failed deploy | MEDIUM | Tag each deploy with timestamp, keep last N images, `docker compose up --force-recreate` with previous tag. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Blue-Green Deployment** | Zero-downtime deploys sound professional | Requires load balancer (nginx/traefik), double resources, complex health check orchestration | Rolling update via `docker compose up -d` with healthcheck delays is sufficient for this scale |
| **Kubernetes Manifests** | "Cloud-native" sounds good | Massive complexity for a single-server deployment. Adds Helm/Kustomize/K8s knowledge burden for no practical benefit | Docker Compose on a VPS is the right tool for this scale |
| **Self-Hosted GitHub Runner** | Avoids 2000 min/month free tier limits | Requires VM maintenance, security patching, Docker socket exposure. Not justified until >2000 min/month usage | Stay on `ubuntu-latest` hosted runners. Free tier is 2000 min/month private repos, unlimited public. |
| **Monorepo Build Tool (Nx/Turborepo)** | "Smart caching" and "affected detection" | Adds dependency, learning curve, and lock-in. The repo only has 3 independent apps, not 50. Path filters in GitHub Actions handle this fine | Simple `paths:` filters on workflow triggers. No extra tooling needed. |
| **Docker-in-Docker for Tests** | Run Testcontainers in CI | Privileged mode security risk, layering complexity. Docker-in-Docker has known isolation issues | Use GitHub Actions `services:` with MySQL/Redis containers directly. Run backend tests with service containers, not Testcontainers. |
| **Separate CI and CD Repos** | "Separation of concerns" | Double the repo management, version drift between pipeline code and app code | Keep `.github/workflows/` in the same repo. Single source of truth. |

## Feature Dependencies

```
[GitHub Actions Workflows]
    |---requires---> [Dockerfiles (EXISTING)]
    |---requires---> [docker-compose.yml (EXISTING)]
    |---requires---> [docker-compose.prod.yml (EXISTING)]
    |---requires---> [.env.example templates (EXISTING)]
    |---requires---> [GitHub Secrets configured]

[PR CI Pipeline]
    |---requires---> [Lint scripts (EXISTING: pnpm lint, ./mvnw checkstyle)]
    |---requires---> [Test commands (EXISTING: pnpm test, ./mvnw test)]
    |---requires---> [Build commands (EXISTING: pnpm build, ./mvnw package)]
    |---requires---> [Service containers (MySQL, Redis) for backend tests]

[Docker Build + Push]
    |---requires---> [PR CI Pipeline (builds first)]
    |---requires---> [GHCR package write permissions]
    |---enhances---> [Build caching (GHA cache type)]

[Docker Compose Deploy]
    |---requires---> [Docker Build + Push (images exist)]
    |---requires---> [SSH access to production server]
    |---requires---> [docker-compose.prod.yml on server]
    |---requires---> [Production .env on server]
    |---enhances---> [Concurrency groups (prevent parallel deploys)]
    |---enhances---> [Automatic image tagging]
    |---enhances---> [Rollback strategy]
```

### Dependency Notes

- **Dockerfiles already exist:** `backend-spring/Dockerfile`, `console/Dockerfile`, `management/Dockerfile` all use multi-stage builds with non-root users, healthchecks, and proper layering. No Dockerfile creation needed.
- **docker-compose.prod.yml already references GHCR images:** The pattern `${GHCR_REGISTRY:-ghcr.io/davidhlp/ulticode-public-next}/backend:${IMAGE_TAG:-latest}` is already defined. The deploy workflow just needs to set `IMAGE_TAG` and run `docker compose pull && docker compose up -d`.
- **Backend tests use Testcontainers:** This means backend tests in CI need Docker service containers OR a restructured test config. Testcontainers with `docker.sock` in GitHub Actions requires `docker:dind` service which has security implications. The better approach is to use GitHub Actions `services:` (mysql, redis) and configure Spring profiles to use them directly, bypassing Testcontainers in CI.
- **Frontend builds are independent:** Console and management have zero dependency on the backend. They can be linted, tested, and built in parallel.

## MVP Definition

### Launch With (v1)

Minimum viable CI/CD -- what prevents manual deployment pain.

- [ ] **PR CI workflow** -- Lint, type-check, test, and build for all three components on every PR. Path-filtered. This is the highest-value single workflow.
- [ ] **Docker build + push on main merge** -- Build and push images to GHCR on push to `main`. Triggered after CI passes.
- [ ] **Docker Compose deploy via SSH** -- Deploy to production server using `docker-compose.prod.yml` on main merge. Single target server.
- [ ] **GitHub Secrets configuration** -- Document required secrets (SSH key, server host, JWT_SECRET, DB credentials). Provide setup guide.
- [ ] **Branch protection rules** -- Require CI pass before PR merge. Document in CONTRIBUTING.md or README.

### Add After Validation (v1.x)

Features to add once the basic pipeline is running reliably.

- [ ] **Dependabot for Actions** -- Auto-update GitHub Actions versions. Low effort, high security value.
- [ ] **Concurrency groups on deploy** -- Prevent parallel deployments to same environment.
- [ ] **Rollback workflow** -- Manual trigger or failed-deploy auto-rollback to previous image tag.
- [ ] **Composite actions for shared setup** -- Extract JDK/pnpm setup into reusable actions when a 4th+ workflow is needed.
- [ ] **Slack/Discord notifications** -- Deploy success/failure alerts.

### Future Consideration (v2+)

Features to defer until the project outgrows single-server deployment.

- [ ] **Deploy preview environments** -- Ephemeral environments per PR. Requires infrastructure investment.
- [ ] **Multi-environment support** -- Staging + production pipelines with promotion workflow.
- [ ] **Self-hosted runners** -- Only when free tier is exhausted (2000 min/month).
- [ ] **Container registry cleanup** -- Automated old-image pruning via GitHub Actions cron or GHCR retention policies.

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| PR CI (lint/test/build) | HIGH -- catches bugs before merge | MEDIUM -- 3 components, Testcontainers complication | P1 |
| Docker build + push to GHCR | HIGH -- enables automated deployment | LOW -- Dockerfiles exist, standard actions | P1 |
| Docker Compose deploy via SSH | HIGH -- eliminates manual deploy | MEDIUM -- SSH setup, env management on server | P1 |
| GitHub Secrets + branch protection | HIGH -- security gate | LOW -- documentation + GitHub UI config | P1 |
| Build caching | MEDIUM -- 2-3x faster CI | LOW -- `actions/cache` or Docker GHA cache | P2 |
| Dependabot for Actions | MEDIUM -- security hygiene | LOW -- single YAML file | P2 |
| Concurrency groups | MEDIUM -- deploy safety | LOW -- single `concurrency:` block | P2 |
| Automatic image tagging | MEDIUM -- version tracking | LOW -- `docker/metadata-action` | P2 |
| Rollback strategy | MEDIUM -- deploy recovery | MEDIUM -- tag management, manual trigger workflow | P2 |
| Composite actions | LOW -- reduces duplication | MEDIUM -- YAML abstraction, testing overhead | P3 |
| Deploy preview environments | HIGH -- but premature | HIGH -- infra provisioning per PR | P3 |
| Multi-environment pipelines | MEDIUM -- not needed yet | HIGH -- promotion workflows, separate envs | P3 |

**Priority key:**
- P1: Must have for launch (first milestone)
- P2: Should have, add when pipeline is stable
- P3: Nice to have, future consideration

## Workflow Architecture Recommendation

### File Structure

```
.github/
  workflows/
    ci.yml                    # P1: PR checks (lint, test, build)
    docker-publish.yml        # P1: Build + push images to GHCR
    deploy.yml                # P1: Deploy via SSH to production
  dependabot.yml              # P2: Auto-update actions
```

### ci.yml -- PR Checks

Three parallel jobs with path filters:

```
ci.yml
  on: pull_request
  jobs:
    backend-check:
      paths: backend-spring/**
      steps: setup JDK 17 -> mvnw test (with services: mysql, redis)

    console-check:
      paths: console/**
      steps: setup Node 22 + pnpm -> lint -> type-check -> test -> build

    management-check:
      paths: management/**
      steps: setup Node 22 + pnpm -> lint -> type-check -> test -> build
```

Key design decisions:
- **Backend tests need service containers** (mysql:9.1, redis:7-alpine) via `services:` key. This avoids Docker-in-Docker. Requires a `application-ci.yml` Spring profile pointing to `localhost:3306` and `localhost:6379`.
- **Frontend tests run headless** (Vitest, no browser). No E2E tests yet.
- **All three jobs run in parallel** when their respective paths change. A change to `console/` does NOT trigger backend CI.

### docker-publish.yml -- Image Build + Push

Triggered on push to `main` (after merge) and optionally on tag creation:

```
docker-publish.yml
  on: push to main
  jobs:
    build-backend:    # Build backend-spring/Dockerfile -> ghcr.io/.../backend:sha-xxx
    build-console:    # Build console/Dockerfile -> ghcr.io/.../console:sha-xxx
    build-management: # Build management/Dockerfile -> ghcr.io/.../management:sha-xxx
```

Key design decisions:
- **Three separate build jobs** (not one monolith) for parallel execution and independent failure isolation.
- **`docker/metadata-action@v5`** for automatic tagging: `sha-<short>` always, `latest` on main, semver on tags.
- **`cache-from: type=gha, cache-to: type=gha,mode=max`** for BuildKit layer caching.
- **`permissions: contents: read, packages: write`** for GHCR push via `GITHUB_TOKEN`.
- **No build on PR** (wastes resources). Build only on merge to main.

### deploy.yml -- Production Deploy

Triggered after successful docker-publish:

```
deploy.yml
  on: workflow_run (docker-publish, completed: success)
  or: workflow_dispatch (manual trigger)
  jobs:
    deploy:
      steps: SSH to server -> docker compose pull -> docker compose up -d
```

Key design decisions:
- **`workflow_run` trigger** ensures deploy only happens after images are successfully pushed.
- **`concurrency: deploy-production`** prevents parallel deploys.
- **SSH via `appleboy/ssh-action@v1`** with key from GitHub Secrets.
- **Server must have `docker-compose.prod.yml` + `.env` pre-configured.** The deploy workflow does NOT copy files -- it only pulls new images and restarts.
- **`docker compose pull` then `docker compose up -d`** is the standard rolling update pattern.

## Competitor Feature Analysis

| Feature | LeetCode-style Platform (Typical) | Our Approach |
|---------|-----------------------------------|--------------|
| PR CI | Lint + unit test + build | Same. Lint + type-check + test + build for all 3 components. |
| Docker Build | Multi-stage Dockerfiles, push to registry | Same. 3 separate images to GHCR. |
| Deployment | K8s or Docker Compose on VPS | Docker Compose on VPS via SSH. Matches current `docker-compose.prod.yml` design. |
| Environment Management | Multiple environments (staging, prod) | Single production environment initially. Add staging later. |
| Secrets | GitHub Secrets + vault integration | GitHub Secrets only. Sufficient for this scale. |
| Rollback | Manual image tag revert | Manual rollback via SSH with previous tag. Automate in v1.x. |

## Sources

- [GitHub Actions in 2026: The Complete Guide to Monorepo CI/CD](https://dev.to/pockit_tools/github-actions-in-2026-the-complete-guide-to-monorepo-cicd-and-self-hosted-runners-1jop) -- MEDIUM confidence, current year monorepo patterns
- [GitHub Actions CI/CD Best Practices (Official GitHub Guide)](https://github.com/github/awesome-copilot/blob/main/instructions/github-actions-ci-cd-best-practices.instructions.md) -- HIGH confidence, official source
- [GitHub Actions Advanced Patterns: Reusable Workflows, Composite Actions & Monorepo](https://www.youngju.dev/blog/devops/2026-03-12-github-actions-reusable-workflows-composite-actions-monorepo.en) -- MEDIUM confidence, 2026-03 publication
- [How to Configure GitHub Actions for Monorepos](https://oneuptime.com/blog/post/2026-02-02-github-actions-monorepos/view) -- MEDIUM confidence, 2026-02 publication
- [GitHub Docs: Publishing Docker Images](https://docs.github.com/en/actions/packaging-with-github-actions/publishing-and-installing-a-package-with-github-actions-publishing-docker-images) -- HIGH confidence, official source
- [appleboy/ssh-action](https://github.com/appleboy/ssh-action) -- HIGH confidence, well-maintained action
- [docker/build-push-action](https://github.com/docker/build-push-action) -- HIGH confidence, official Docker action
- [docker/metadata-action](https://github.com/docker/metadata-action) -- HIGH confidence, official Docker action
- Existing project files: `docker-compose.prod.yml`, `docker-compose.yml`, `backend-spring/Dockerfile`, `console/Dockerfile`, `management/Dockerfile`, `ecosystem.config.cjs`, `console/package.json`, `management/package.json`, `backend-spring/pom.xml`, `.env.example` -- HIGH confidence, direct inspection

---
*Feature research for: CI/CD Pipeline with GitHub Actions + Docker Compose*
*Researched: 2026-04-17*
