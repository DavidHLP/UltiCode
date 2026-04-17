# Technology Stack: CI/CD Pipeline Additions

**Project:** UltiCode v1.2 CI/CD Pipeline
**Researched:** 2026-04-17
**Confidence:** HIGH

## Recommended Stack

This document covers **only new additions** required for the v1.2 CI/CD milestone. The existing validated stack (Spring Boot 3.5, Vue 3, MyBatis-Plus, MySQL, Redis, Nacos, PM2, Docker) is not re-researched here.

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| GitHub Actions | Runner v2.327.1+ (Node 24 runtime) | CI/CD orchestration | Already hosting the repo on GitHub; zero additional infra cost; native integration with PRs, branches, and GHCR. Free tier: 2000 min/month for private repos, unlimited for public. |
| Docker Buildx | v0.20+ (via setup-buildx-action v4.0.0) | Multi-platform builds, layer caching | Required by `docker/build-push-action` v7. Enables BuildKit features: cache export/import, multi-stage parallelism, attestations. |
| GitHub Container Registry (GHCR) | `ghcr.io/{owner}/{image}` | Docker image storage | Free for public images, 500MB storage for private. Tight integration with GitHub Actions -- no separate credentials. Packages tab in repo for image management. |
| Docker Compose | v2.x (deploy target) | Production deployment on VPS | Already used for local dev (`docker-compose.yml`). Same compose files can drive production deployment with env-var overrides. |

### GitHub Actions

| Action | Version | Purpose | Notes |
|--------|---------|---------|-------|
| `actions/checkout` | v6.0.2 | Clone repository | Default for all workflows |
| `actions/setup-java` | v5.2.0 | Install JDK for backend build | Use `distribution: 'temurin'`, `java-version: '17'` to match existing Dockerfile |
| `actions/setup-node` | v6.3.0 | Install Node.js for frontend builds | Use `node-version: '22'` to match existing Dockerfiles. Supports `cache: 'pnpm'` natively. |
| `pnpm/action-setup` | v5.0.0 | Install pnpm package manager | Use `version: 9` to match existing Dockerfiles. Run before `actions/setup-node` with `cache` disabled (let setup-node handle caching). |
| `actions/cache` | v5.0.5 | Arbitrary caching (Maven, Testcontainers) | Use for Maven local repo (`~/.m2/repository`) and Testcontainers images. |
| `docker/login-action` | v4.1.0 | Authenticate to GHCR | Use `registry: ghcr.io`, token: `${{ secrets.GITHUB_TOKEN }}` |
| `docker/metadata-action` | v6.0.0 | Generate Docker image tags | Auto-tags: `sha-<commit>`, `latest` on main, semver on tags. Avoids tag collisions with `sep:` and `prefix:` options. |
| `docker/setup-buildx-action` | v4.0.0 | Create Buildx builder | **Requires Actions Runner v2.327.1+** (Node 24 runtime). Use `driver-opts: network=host` for local registry access during caching. |
| `docker/build-push-action` | v7.1.0 | Build and push Docker images | **Requires Actions Runner v2.327.1+**. Use `context: .`, `file: ./path/to/Dockerfile`, `push: true`, `cache-from/to: type=gha`. |
| `actions/upload-artifact` | v8.0.1 | Share files between jobs | Use for passing build reports, coverage artifacts between CI jobs. |
| `actions/download-artifact` | v7.0.1 | Retrieve shared files | Merged into `actions/upload-artifact` v5+ ecosystem; use `github` namespace. |
| `softprops/action-gh-release` | v3.0.0 | Create GitHub releases | Optional: for tagged releases with image manifests. |

### Deployment Actions

| Action | Version | Purpose | Notes |
|--------|---------|---------|-------|
| `appleboy/ssh-action` | v1.2.5 | SSH-based remote deployment | Execute `docker compose pull && docker compose up -d` on VPS. Use `env:` for secrets injection. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `act` | Local GitHub Actions testing | Run workflows locally before pushing. Supports most actions but not Docker layer caching. Use for smoke testing workflow syntax. |
| `actionlint` | Workflow linting | Catch YAML errors, shellcheck issues, and typos in workflow files. Install via `brew install actionlint` or Go. |

## Installation

No new project dependencies to install. CI/CD runs entirely in GitHub Actions runners using existing Dockerfiles.

### Workflow Files (to be created)

```
.github/
  workflows/
    ci.yml              # Lint, type-check, test on PR/push
    cd.yml              # Build images, push to GHCR, deploy
```

## Alternatives Considered

| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| GitHub Actions | GitLab CI | Repo is on GitHub; moving to GitLab CI would require hosting migration. GitLab CI is excellent but only relevant if the repo moves. |
| GitHub Actions | Jenkins | Heavyweight for this project size. Requires self-hosted server, plugin maintenance. Overkill for a 3-service monorepo. |
| GHCR | Docker Hub | Docker Hub has rate limits (200 pulls/6h for free). GHCR has no rate limits for GitHub Actions. GHCR integrates natively with `GITHUB_TOKEN`. |
| GHCR | AWS ECR / GCP Artifact Registry | Adds cloud provider dependency. GHCR is sufficient and free for this scale. Revisit if deploying to cloud later. |
| `docker/build-push-action` | Kaniko | Kaniko runs in-cluster (Kubernetes). This project uses Docker Compose on a VPS -- Buildx is the natural fit. |
| SSH deploy via `appleboy/ssh-action` | Self-hosted runner on VPS | Self-hosted runners add maintenance burden (updates, security, disk cleanup). SSH deploy is simpler and more secure for a single VPS. |
| GHA caching (`type=gha`) | Registry-based caching (`type=registry`) | GHA caching is free, simpler to configure, and automatically managed. Registry caching requires a separate cache registry. Use registry caching only if GHA cache fills up (>10GB). |
| `docker/metadata-action` | Manual tagging | Manual tags are error-prone and inconsistent. `metadata-action` generates deterministic tags from git context (branch, SHA, semver). |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `latest` tag in production | Non-reproducible; rolling back requires knowing the previous SHA | `sha-<commit>` tags always; `latest` only on `main` for dev environments |
| Docker Hub free tier | 200 pulls/6h rate limit will hit with 3 services pulling on every deploy | GHCR (no rate limit for GHA, 500MB private storage) |
| Self-hosted runners initially | Adds infra complexity before the pipeline is proven | GitHub-hosted runners (`ubuntu-24.04`). Add self-hosted later if build times exceed 6min or need persistent Docker layer cache. |
| `ubuntu-latest` as pinned version | It can change without notice (currently 24.04, was 22.04) | Pin explicitly: `ubuntu-24.04` for reproducibility |
| `main` branch as sole deployment target | Accidental deploys from force-pushes or history rewrites | Use tag-based deployment (`on: push: tags: ['v*']`) or explicit `workflow_dispatch` with branch input |
| Nested workflows (`workflow_call`) for initial setup | Adds indirection that complicates debugging during first iteration | Single-file workflows first. Extract reusable actions via `workflow_call` once patterns stabilize. |
| Branch protection via workflow | Workflow-level checks can be bypassed by repo admins | Use GitHub UI Settings > Branches > Branch protection rules. Mention in docs but do not implement in code. |
| Large ephemeral test containers in CI | Docker Compose for infra + app containers in CI is slow and resource-heavy | Run backend tests with H2/Testcontainers (existing pattern). Frontend tests are Vitest unit tests (no containers needed). |

## Stack Patterns by Variant

### CI Pipeline (ci.yml)

**Trigger:** `pull_request` + `push` to `main` with path filters.

```yaml
on:
  pull_request:
    paths:
      - 'backend-spring/**'
      - 'console/**'
      - 'management/**'
  push:
    branches: [main]
    paths:
      - 'backend-spring/**'
      - 'console/**'
      - 'management/**'
```

**Pattern:**
- Path-based triggers prevent unnecessary runs when only docs change.
- Three parallel jobs: `backend-lint-test`, `console-lint-test`, `management-lint-test`.
- Backend job: `actions/setup-java` + `./mvnw verify` (runs Checkstyle if configured, unit tests, integration tests).
- Frontend jobs: `pnpm/action-setup` + `actions/setup-node` + `pnpm install --frozen-lockfile` + `pnpm lint` + `pnpm test`.
- Maven cache via `actions/cache` key: `maven-${{ runner.os }}-${{ hashFiles('backend-spring/pom.xml') }}`.
- pnpm cache via `actions/setup-node` with `cache: 'pnpm'`.

### CD Pipeline (cd.yml)

**Trigger:** `push` to `main` (after CI passes) or `workflow_dispatch`.

**Pattern:**
- Single job with sequential steps: build all 3 images, push to GHCR, deploy via SSH.
- Three `docker/build-push-action` steps, one per service, using existing Dockerfiles.
- GHA cache (`cache-from: type=gha,scope=backend`, `cache-to: type=gha,mode=max,scope=backend`) for layer reuse.
- `docker/metadata-action` per service for deterministic tagging.
- Concurrency group per environment to prevent overlapping deployments:
  ```yaml
  concurrency:
    group: deploy-production
    cancel-in-progress: false  # Never cancel a deploy in progress
  ```
- Deploy step: `appleboy/ssh-action` to run `docker compose pull && docker compose up -d` on VPS.

### Production Docker Compose Override

The existing `docker-compose.yml` defines infrastructure (MySQL, Redis, Nacos). Production deployment needs an **override file** (`docker-compose.prod.yml`) that adds application services:

```yaml
# docker-compose.prod.yml (to be created)
services:
  backend:
    image: ghcr.io/{owner}/ulticode-backend:sha-${{ github.sha }}
    ports: ["9001:9001"]
    env_file: .env.production
    depends_on: [mysql, redis, nacos]

  console:
    image: ghcr.io/{owner}/ulticode-console:sha-${{ github.sha }}
    ports: ["9002:8080"]
    depends_on: [backend]

  management:
    image: ghcr.io/{owner}/ulticode-management:sha-${{ github.sha }}
    ports: ["9003:8080"]
    depends_on: [backend]
```

**Critical integration note:** The nginx configs in `console/nginx.conf` and `management/nginx.conf` proxy to `http://backend:9001` using Docker network hostnames. The production compose file must name the backend service `backend` (not `ulticode-9001`) for this to work, or update the nginx configs to use the production service name.

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| `docker/build-push-action@v7.x` | Actions Runner v2.327.1+ | Node 24 runtime required. GitHub-hosted runners updated 2025-08-14. If on older runner, pin to v6.1.0. |
| `docker/setup-buildx-action@v4.x` | Actions Runner v2.327.1+ | Same requirement as build-push-action v7. |
| `docker/metadata-action@v6.x` | Actions Runner v2.327.1+ | Node 24 runtime. |
| `actions/checkout@v6.x` | All runners | Node 24 runtime. Pin to v4 if runner is older than v2.327.1. |
| `actions/setup-java@v5.x` | All runners | Works on all runner versions. |
| `actions/setup-node@v6.x` | All runners | Works on all runner versions. |
| `pnpm/action-setup@v5.x` | All runners | Works on all runner versions. |
| `actions/cache@v5.x` | All runners | Works on all runner versions. |
| `docker/login-action@v4.x` | All runners | Works on all runner versions. |
| JDK 17 (temurin) | Spring Boot 3.5.x | Matches existing `eclipse-temurin:17-jdk-alpine` in Dockerfile. |
| Node 22 | Vue 3 + Vite | Matches existing `node:22-alpine` in frontend Dockerfiles. |
| pnpm 9 | Vue 3 workspaces | Matches existing `corepack prepare pnpm@9` in Dockerfiles. |

### Runner Version Decision Matrix

| GitHub-hosted runner | Version | Docker actions v7 safe? |
|---------------------|---------|------------------------|
| `ubuntu-latest` (current) | v2.327.1+ | YES |
| `ubuntu-24.04` | v2.327.1+ | YES |
| `ubuntu-22.04` (deprecated) | v2.313.0 | NO -- pin Docker actions to v6/v3 |

**Recommendation:** Use `ubuntu-24.04` explicitly. Do not use `ubuntu-22.04` (deprecated, cannot run Docker actions v7).

## Integration with Existing Infrastructure

### Existing Dockerfiles (no changes needed for CI)

The project already has production-ready multi-stage Dockerfiles:

| Service | Dockerfile | Build context | Runtime image | Port |
|---------|-----------|---------------|---------------|------|
| Backend | `backend-spring/Dockerfile` | Project root (`.`) | `eclipse-temurin:17-jre-alpine` | 9001 |
| Console | `console/Dockerfile` | Project root (`.`) | `nginx:alpine` | 8080 |
| Management | `management/Dockerfile` | Project root (`.`) | `nginx:alpine` | 8080 |

All three already include:
- Multi-stage builds (smaller final images)
- Non-root users (security best practice)
- Health checks (Docker native + Kubernetes-ready)
- Dependency caching (Maven `go-offline`, pnpm `--frozen-lockfile`)

### Existing docker-compose.yml (extend, don't replace)

The existing `docker-compose.yml` defines infrastructure services only (MySQL, Redis, Nacos). The CD pipeline should use `docker compose -f docker-compose.yml -f docker-compose.prod.yml` to merge infrastructure + application services.

### Existing PM2 config (dev only)

`ecosystem.config.cjs` is for local development via PM2. It should NOT be used in production Docker containers. The CD pipeline deploys via Docker Compose, not PM2.

## Secrets Required

| Secret Name | GitHub Secret? | VPS env file? | Purpose |
|-------------|---------------|---------------|---------|
| `GITHUB_TOKEN` | Built-in | N/A | Push to GHCR, create releases. No configuration needed. |
| `VPS_HOST` | Yes (repo secret) | N/A | VPS IP or hostname for SSH deploy. |
| `VPS_USER` | Yes (repo secret) | N/A | SSH username on VPS. |
| `VPS_SSH_KEY` | Yes (repo secret) | N/A | SSH private key for deployment. |
| `VPS_DEPLOY_PATH` | Yes (repo secret) | N/A | Path to docker-compose files on VPS (e.g., `/opt/ulticode`). |
| `.env.production` | No | Yes (on VPS) | All app credentials (DB, Redis, JWT, Nacos). Never store in GitHub. |

## Sources

- `gh api repos/{owner}/{repo}/releases/latest` -- all action versions verified via GitHub Releases API on 2026-04-17
- GitHub Docs: "Using GitHub Actions" -- https://docs.github.com/en/actions
- Docker Docs: "Build with GitHub Actions" -- https://docs.docker.com/build/ci/github-actions/multi-platform/
- Docker build-push-action v7.0.0 release notes -- Actions Runner v2.327.1+ requirement
- Existing project Dockerfiles (`backend-spring/Dockerfile`, `console/Dockerfile`, `management/Dockerfile`) -- analyzed directly
- Existing `docker-compose.yml` -- analyzed directly
- Existing `ecosystem.config.cjs` -- analyzed directly

---
*Stack research for: UltiCode CI/CD Pipeline (v1.2 milestone)*
*Researched: 2026-04-17*
