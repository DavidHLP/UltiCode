# Architecture Research: CI/CD Pipeline for UltiCode Monorepo

**Domain:** CI/CD pipeline with GitHub Actions, Docker Compose, VPS deployment
**Researched:** 2026-04-17
**Confidence:** HIGH

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                     GitHub (Source Control)                         │
│  Push to main / PR                                                  │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ webhook trigger
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│              GitHub Actions Runner (ubuntu-latest)                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐    ┌─────────────────────────────────────────┐   │
│  │ Change Detect │    │        Parallel Build Matrix            │   │
│  │ dorny/paths   │───▶│  ┌─────────┐ ┌─────────┐ ┌──────────┐  │   │
│  │  -filter@v4   │    │  │ backend │ │ console │ │management│  │   │
│  └──────────────┘    │  │  test   │ │  test   │ │  test    │  │   │
│                      │  └────┬────┘ └────┬────┘ └────┬─────┘  │   │
│                      │       │           │           │         │   │
│                      │  ┌────▼────┐ ┌────▼────┐ ┌───▼──────┐  │   │
│                      │  │ backend │ │ console │ │management│  │   │
│                      │  │docker   │ │docker   │ │docker    │  │   │
│                      │  │image    │ │image    │ │image     │  │   │
│                      │  │build    │ │build    │ │build     │  │   │
│                      │  └────┬────┘ └────┬────┘ └───┬──────┘  │   │
│                      └───────┼──────────┼──────────┼─────────┘   │
│                              │          │          │              │
│                      ┌───────▼──────────▼──────────▼─────────┐   │
│                      │        Push to GHCR                    │   │
│                      │   ghcr.io/<owner>/ulticode-<service>   │   │
│                      └──────────────┬────────────────────────┘   │
└─────────────────────────────────────┼────────────────────────────┘
                                      │ docker compose pull
                                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        VPS (Production)                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                   Reverse Proxy (Nginx/Caddy)                 │  │
│  │              TLS termination, port 80/443                    │  │
│  └───────────────────────────┬──────────────────────────────────┘  │
│                              │                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │              docker-compose.prod.yml                         │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐  │  │
│  │  │ backend  │  │ console  │  │management│  │ mysql      │  │  │
│  │  │ :9001    │  │ :8080    │  │ :8080    │  │ :3306      │  │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └────────────┘  │  │
│  │  ┌──────────┐  ┌──────────┐                                  │  │
│  │  │ redis    │  │ nacos    │                                  │  │
│  │  │ :6379    │  │ :8848    │                                  │  │
│  │  └──────────┘  └──────────┘                                  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │              Flyway Migrations (db-manager)                  │  │
│  │         Run on backend startup (Spring Boot auto-config)     │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **GitHub Actions workflows** | CI/CD orchestration: test, build, push, deploy | `.github/workflows/ci.yml`, `deploy.yml` |
| **dorny/paths-filter** | Detect which services changed to skip unnecessary builds | Single job outputs consumed by `if:` conditions |
| **Docker multi-stage builds** | Build application artifacts in builder stage, copy to minimal runtime | Existing Dockerfiles need minor adjustments |
| **GHCR (GitHub Container Registry)** | Store built Docker images; free for public repos, 500MB free for private | `ghcr.io/<owner>/ulticode-backend`, etc. |
| **SSH deploy action** | Connect to VPS, pull new images, restart services | `appleboy/ssh-action@v1` or raw `ssh` step |
| **Reverse proxy** | TLS termination, routing, static asset caching | Nginx or Caddy on the VPS |
| **docker-compose.prod.yml** | Production orchestration of all services on VPS | Extends existing `docker-compose.yml` |
| **db-manager (Flyway)** | Database migration on deploy | Runs on backend startup via Spring Boot auto-config |

## Recommended Project Structure

### New Files to Create

```
.github/
└── workflows/
    ├── ci.yml                      # PR & push: lint, test, build images
    └── deploy.yml                  # Push to main: build + deploy to VPS
docker-compose.prod.yml             # Production service definitions
.dockerignore                       # Reduce Docker build context size
deploy/
└── deploy.sh                       # One-shot deployment script for VPS
```

### Files to Modify

| File | Change | Why |
|------|--------|-----|
| `backend-spring/Dockerfile` | Fix JAR name mismatch; add Maven cache mount | Current JAR name `0.0.1-SNAPSHOT` but `pom.xml` says `1.0.0` |
| `console/Dockerfile` | Copy `pnpm-lock.yaml` before install; add `VITE_API_BASE_URL` build arg | Missing lockfile copy; API URL must be injected at build time |
| `management/Dockerfile` | Same as console | Same issue |
| `console/nginx.conf` | Parameterize API upstream via envsubst | Currently hardcodes `http://backend:9001` -- needs to work with Compose service names |
| `management/nginx.conf` | Parameterize API upstream via envsubst | Same |

### Structure Rationale

- **Separate `ci.yml` and `deploy.yml`**: CI runs on every PR/push (test + lint + build). Deploy runs only on push to `main`. This separation lets PRs be validated without triggering deployments.
- **`docker-compose.prod.yml` alongside existing `docker-compose.yml`**: The existing compose file defines infrastructure only (MySQL, Redis, Nacos). The production file adds application services on top, using images from GHCR instead of local builds.
- **`deploy/` directory**: Encapsulates VPS-side scripts (not mixed into project root).

## Architectural Patterns

### Pattern 1: Path-Aware Monorepo CI

**What:** Use `dorny/paths-filter@v4` to detect which subdirectories changed and conditionally run only the affected build/test jobs. This avoids rebuilding the backend when only frontend CSS changed.

**When to use:** Any monorepo with 3+ independently deployable services. Essential here because backend (Maven), console (pnpm/Vite), and management (pnpm/Vite) have completely separate build pipelines.

**Trade-offs:** Adds a small overhead for the change-detection job itself (~15s). Saves 5-15 minutes per CI run by skipping unchanged services.

**Example:**
```yaml
jobs:
  changes:
    runs-on: ubuntu-latest
    permissions:
      pull-requests: read
    outputs:
      backend: ${{ steps.filter.outputs.backend }}
      console: ${{ steps.filter.outputs.console }}
      management: ${{ steps.filter.outputs.management }}
      infra: ${{ steps.filter.outputs.infra }}
    steps:
      - uses: actions/checkout@v4
      - uses: dorny/paths-filter@v4
        id: filter
        with:
          filters: |
            backend:
              - 'backend-spring/**'
            console:
              - 'console/**'
            management:
              - 'management/**'
            infra:
              - 'docker-compose.yml'
              - 'docker-compose.prod.yml'
              - 'db-manager/**'
```

### Pattern 2: Docker Build with GHA Cache Backend

**What:** Use `docker/build-push-action@v6` with `cache-from: type=gha` and `cache-to: type=gha,mode=max` to cache all intermediate layers in GitHub Actions' built-in cache. The `mode=max` is critical for multi-stage builds -- it caches every stage, not just the final image.

**When to use:** All Docker image builds in GitHub Actions. This is the recommended approach per official Docker docs (verified 2026-02-05).

**Trade-offs:** GitHub imposes a 10 GB total cache limit per repository. With 4 services using `mode=max`, this fills up. Mitigate by scoping caches per workflow and service using the `scope` parameter.

**Important version requirements (per Docker docs, post-April 2025):**
- Docker Buildx >= v0.21.0
- BuildKit >= v0.20.0
- `docker/setup-buildx-action@v3` handles this automatically on GitHub-hosted runners.

**Example:**
```yaml
- uses: docker/setup-buildx-action@v3

- uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}

- uses: docker/build-push-action@v6
  with:
    context: .
    file: backend-spring/Dockerfile
    push: ${{ github.event_name != 'pull_request' }}
    tags: ghcr.io/${{ github.repository }}/backend:${{ github.sha }}
    cache-from: type=gha,scope=backend
    cache-to: type=gha,mode=max,scope=backend
```

### Pattern 3: SSH-Based VPS Deployment

**What:** After building and pushing images to GHCR, SSH into the VPS and run `docker compose pull && docker compose up -d`. This is the simplest reliable deployment method for a single VPS.

**When to use:** Single-server deployments. If you need blue-green or rolling deployments, consider a more advanced tool, but for this project's scale, SSH + Compose is the right choice.

**Trade-offs:** No zero-downtime deployment out of the box (Compose recreates containers sequentially). Acceptable for a programming platform. Can add health-check waits between service restarts for better reliability.

**Example:**
```yaml
- name: Deploy to VPS
  uses: appleboy/ssh-action@v1
  with:
    host: ${{ secrets.VPS_HOST }}
    username: ${{ secrets.VPS_USER }}
    key: ${{ secrets.VPS_SSH_KEY }}
    port: ${{ secrets.VPS_PORT || 22 }}
    script: |
      cd /opt/ulticode
      docker compose -f docker-compose.prod.yml pull
      docker compose -f docker-compose.prod.yml up -d --remove-orphans
      docker image prune -f
```

### Pattern 4: Environment Injection at Build Time vs Runtime

**What:** Frontend apps need `VITE_API_BASE_URL` baked at build time (Vite replaces `import.meta.env.VITE_*` during `vite build`). Backend services read env vars at runtime via Spring's `@Value` or environment variable binding.

**When to use:** This is not optional -- it is how Vite works. The frontend must know the API origin at build time.

**Trade-offs:** Build-time env vars mean you need different Docker image tags for different environments (staging vs production). This is standard practice and acceptable. The backend uses runtime env vars passed through `docker-compose.prod.yml`, so a single image works across environments.

**Implementation for Dockerfiles:**
```dockerfile
# In console/Dockerfile and management/Dockerfile
ARG VITE_API_BASE_URL=http://localhost:9001
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
```

And in the workflow:
```yaml
- uses: docker/build-push-action@v6
  with:
    build-args: |
      VITE_API_BASE_URL=${{ vars.PRODUCTION_API_URL }}
```

## Data Flow

### CI/CD Pipeline Flow

```
[Developer pushes code]
    |
    v
[GitHub webhook triggers CI workflow]
    |
    v
[dorny/paths-filter detects changes]
    |
    +-- backend-spring/** changed?
    |       |
    |       v
    |   [Backend test job]
    |       - mvnw test (with Testcontainers for MySQL/Redis)
    |       - Fail fast if tests fail
    |       |
    |       v
    |   [Backend Docker build job]
    |       - docker build with GHA cache
    |       - Push to GHCR if on main branch
    |
    +-- console/** changed?
    |       |
    |       v
    |   [Console test job]
    |       - pnpm install, pnpm test
    |       - pnpm type-check, pnpm lint
    |       |
    |       v
    |   [Console Docker build job]
    |       - Build with VITE_API_BASE_URL arg
    |       - Push to GHCR if on main branch
    |
    +-- management/** changed?
    |       |
    |       v
    |   [Management test job]
    |       - Same as console
    |       |
    |       v
    |   [Management Docker build job]
    |       - Push to GHCR if on main branch
    |
    v
[Deploy workflow triggered (main branch only)]
    |
    v
[SSH to VPS]
    |
    v
[docker compose pull -- parallel image download]
    |
    v
[docker compose up -d -- controlled restart order]
    - MySQL/Redis/Nacos: already running, skip
    - Backend: restart first (depends on infra)
    - Console/Management: restart after backend healthy
    |
    v
[Health check verification]
    - curl backend:9001/actuator/health
    - curl console:8080/
    - curl management:8080/
```

### Key Data Flows

1. **Code push to deployed containers:** Git push -> GitHub Actions -> Docker build -> GHCR push -> VPS pull -> Compose restart. Latency: ~5-10 minutes total (build dominates).
2. **Environment secrets flow:** GitHub Secrets (encrypted at rest) -> GitHub Actions env vars -> Docker build args (frontend) or Compose env_file (backend on VPS). Secrets never appear in logs or image layers.
3. **Database migrations:** Flyway runs on backend startup (Spring Boot auto-configures `FlywayMigrationStrategy`). No separate migration step needed in CI if the backend container handles it. Alternatively, run `db-manager` as a dedicated init container in Compose.

## Build Order and Dependencies

### Service Dependency Graph

```
console ─────────┐
                 │
management ──────┤───▶ backend-spring ──▶ mysql
                 │         │
                 │         ├───▶ redis
                 │         │
                 │         └───▶ nacos (optional, for recommendation)
                 │
                 └───▶ (no backend dependency at build time;
                       API URL is a build-time string)
```

### Build Order in CI

The three application services (backend, console, management) are **independent at build time** and can be built in parallel. They only have runtime dependencies.

1. **Parallel:** `backend-spring`, `console`, `management` (Docker image builds)
2. **Sequential (deploy only):** MySQL/Redis/Nacos start first, then backend, then frontends

### Dockerfile Adjustments Needed

**backend-spring/Dockerfile:**
- The current Dockerfile copies `pom.xml` and `.mvn/` for dependency caching (good).
- The JAR name in the COPY command is `ulticode-backend-0.0.1-SNAPSHOT.jar` but `pom.xml` declares `<version>1.0.0</version>`. This will **fail at build time**. Fix: use a glob pattern like `target/*.jar`.
- Add `--mount=type=cache,target=/root/.m2/repository` for Maven dependency caching in BuildKit.

**console/Dockerfile and management/Dockerfile:**
- Currently copy only `package.json` but not `pnpm-lock.yaml`. For reproducible builds, copy the lockfile too.
- The `pnpm install --frozen-lockfile` command requires the lockfile to be present. Currently this would fail if the lockfile is not in the Docker context.
- Add build arg for `VITE_API_BASE_URL`.
- The Dockerfile context is the monorepo root (since it references `COPY console/...`), so the lockfile IS available -- but the explicit `COPY console/package.json` without the lockfile means pnpm cannot verify integrity.

**recommendation service:**
- Currently has no Dockerfile. Since it is optional (`RECOMMENDATION_ENABLED=true` can be set to `false`), it can be deferred to a later phase. If needed, it would require a multi-module Maven build.

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Current (development) | PM2 + Docker Compose for infra, direct `vite dev` for frontends |
| Single VPS (1-5k users) | All services in Docker Compose, reverse proxy with Nginx/Caddy, 2-4 GB RAM minimum |
| Multiple VPS / small cluster | Separate app servers from database, add a load balancer, use external managed DB |
| Cloud-native (100k+ users) | Kubernetes, managed DB (RDS), managed Redis (ElastiCache), CDN for static assets |

### Scaling Priorities

1. **First bottleneck:** Database connections under load. Mitigate: increase MySQL `max_connections`, add Redis caching (already in place), use HikariCP pool tuning in Spring Boot.
2. **Second bottleneck:** Code judging sandbox containers. The backend spawns Docker containers for code execution. On a single VPS, this competes with the application for resources. Mitigate: resource limits on judge containers, separate judge host if needed.

## Anti-Patterns

### Anti-Pattern 1: Building All Services on Every Push

**What people do:** A single CI job that builds backend, console, and management images on every push, regardless of what changed.

**Why it's wrong:** A CSS fix in console triggers a full Maven build of the backend (~3-5 minutes), wasting GitHub Actions minutes and delaying feedback.

**Do this instead:** Use `dorny/paths-filter@v4` to detect changes per service directory. Only build and test the services that actually changed.

### Anti-Pattern 2: Building Images Inside the Deploy Step on VPS

**What people do:** SSH into the VPS, `git pull`, and `docker compose build` on the server itself.

**Why it's wrong:** The VPS has limited CPU/RAM. Building 4 Docker images (2 Maven + 2 Node.js) on a small VPS can exhaust memory and take 20+ minutes. It also requires the full source code, build tools (JDK 17, Node 22, Maven), and all dependencies on the VPS.

**Do this instead:** Build images in GitHub Actions (free compute, fast, cached), push to GHCR, then `docker compose pull` on the VPS. The VPS only needs Docker and docker-compose.

### Anti-Pattern 3: Using `latest` Tag in Production

**What people do:** Tag all images as `latest` and deploy that. If a build fails midway, the `latest` tag could point to a broken image.

**Why it's wrong:** No rollback capability. Cannot tell which version is running. A failed build can leave `latest` pointing to a bad image.

**Do this instead:** Tag with git SHA (`${{ github.sha }}`) for traceability. Use a secondary `rolling` tag for production Compose to reference. Keep the previous image for rollback.

### Anti-Pattern 4: Hardcoded Credentials in docker-compose.prod.yml

**What people do:** Put database passwords, JWT secrets, etc. directly in the Compose file committed to Git.

**Why it's wrong:** Security violation. Anyone with repo access has production credentials.

**Do this instead:** Use `env_file: .env.production` reference in `docker-compose.prod.yml`. The `.env.production` file lives only on the VPS, never in Git.

### Anti-Pattern 5: Running Database Migrations as a Separate CI Job

**What people do:** Add a CI step that runs Flyway migrations against the production database before deploying the new application code.

**Why it's wrong:** If the migration fails, the old code is still running and may break. If the migration succeeds but the deploy fails, the new code expects the new schema but the old code is running.

**Do this instead:** Let the Spring Boot application run Flyway migrations on startup (already configured). Migrations run atomically with the deploy. Ensure migrations are backward-compatible (additive columns, not destructive changes).

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| GitHub Container Registry (GHCR) | `docker/login-action@v3` with `GITHUB_TOKEN` | Free for public repos; no extra credentials needed |
| VPS (production server) | SSH via `appleboy/ssh-action@v1` | Needs deploy key in GitHub Secrets; key-based auth only |
| Docker Hub (optional) | `docker/login-action@v3` with secrets | Only if publishing public images; GHCR is simpler |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| CI workflow -> Docker builds | Docker BuildKit via `build-push-action` | GHA cache backend for layer caching |
| CI workflow -> VPS | SSH (encrypted) | Deploy key, never password |
| VPS -> GHCR | Docker pull (authenticated) | Login with PAT or deploy token |
| Backend -> Console/Management | HTTP via Nginx reverse proxy | `proxy_pass http://backend:9001` already configured in nginx.conf |
| Backend -> MySQL/Redis/Nacos | Docker network (internal) | Compose creates a shared network; no port exposure needed |

### GitHub Secrets Required

| Secret | Value | Used By |
|--------|-------|---------|
| `VPS_HOST` | VPS IP address | deploy.yml (ssh-action) |
| `VPS_USER` | SSH username (e.g., `deploy`) | deploy.yml (ssh-action) |
| `VPS_SSH_KEY` | Ed25519 private key | deploy.yml (ssh-action) |
| `VPS_PORT` | SSH port (default 22) | deploy.yml (ssh-action) |

### GitHub Variables (non-secret)

| Variable | Value | Used By |
|----------|-------|---------|
| `PRODUCTION_API_URL` | `https://api.ulticode.com` (or actual domain) | Frontend Docker build args |
| `GHCR_REGISTRY` | `ghcr.io` | All build jobs |
| `IMAGE_PREFIX` | `ghcr.io/<owner>/ulticode` | All build jobs |

### VPS Prerequisites

| Requirement | Details |
|-------------|---------|
| Docker Engine | v28.0+ (for containerd image store support) |
| Docker Compose | v2.33+ (for updated GHA cache API support) |
| Reverse proxy | Nginx or Caddy, configured with TLS (Let's Encrypt) |
| Firewall | UFW: allow 22, 80, 443 only |
| `.env.production` | All secrets (DB passwords, JWT, OAuth, etc.) placed manually |

## Production docker-compose.prod.yml Design

The existing `docker-compose.yml` defines infrastructure only (MySQL, Redis, Nacos). The production compose file adds application services:

```yaml
# Key design decisions:
# - Infrastructure services use the same images as development (mysql:9.1, redis:7-alpine, nacos:v2.3.2)
# - Application services use GHCR images built by CI
# - All app services reference env_file for secrets
# - Health checks ensure ordered startup
# - Networks are internal; only the reverse proxy exposes ports

services:
  # --- Infrastructure (same as docker-compose.yml, with production tuning) ---
  mysql:
    image: mysql:9.1
    # ... (same config, add production memory limits)

  redis:
    image: redis:7-alpine
    # ... (same config)

  nacos:
    image: nacos/nacos-server:v2.3.2
    # ... (same config)

  # --- Application Services ---
  backend:
    image: ghcr.io/${OWNER}/ulticode-backend:${TAG}
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    env_file: .env.production
    # No port exposure -- reverse proxy handles routing

  console:
    image: ghcr.io/${OWNER}/ulticode-console:${TAG}
    depends_on:
      backend:
        condition: service_healthy
    # No port exposure -- reverse proxy handles routing

  management:
    image: ghcr.io/${OWNER}/ulticode-management:${TAG}
    depends_on:
      backend:
        condition: service_healthy
    # No port exposure -- reverse proxy handles routing
```

## Dockerfile-Specific Recommendations

### backend-spring/Dockerfile

The existing Dockerfile is well-structured (multi-stage, non-root user, healthcheck). Required fixes:

1. **JAR name mismatch**: Change `COPY .../ulticode-backend-0.0.1-SNAPSHOT.jar` to match the actual artifact name from `pom.xml` (`ulticode-backend-1.0.0.jar`), or use a glob: `COPY --from=builder /app/target/*.jar ./app.jar`.
2. **Maven cache mount**: Add `--mount=type=cache,target=/root/.m2/repository` to the dependency download step for BuildKit caching (complements GHA cache).
3. **Build arg for Spring profile**: Add `ARG SPRING_PROFILES_ACTIVE=prod` and pass it as `ENV SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE`.

### console/Dockerfile and management/Dockerfile

1. **Copy lockfile**: Add `COPY console/pnpm-lock.yaml ./console/` (and management equivalent) before `pnpm install`.
2. **Build arg for API URL**: Add `ARG VITE_API_BASE_URL` and set `ENV VITE_API_BASE_URL=$VITE_API_BASE_URL` before the build step.
3. **pnpm workspace context**: The current Dockerfile copies from monorepo root context. This is correct but means the full repo is the Docker build context. Consider `.dockerignore` to exclude unnecessary directories (`recommendation/`, `db-manager/`, `.planning/`, `.git/`).

### .dockerignore Recommendation

Create a root `.dockerignore` to reduce build context size:

```
.git
.planning
.github
recommendation/
db-manager/
node_modules/
**/node_modules/
**/dist/
**/target/
*.log
.env
.env.*
!.env.example
```

## Sources

- [Docker Cache Management with GitHub Actions](https://docs.docker.com/build/ci/github-actions/cache/) -- Official Docker documentation, verified 2026-02-05. HIGH confidence.
- [docker/build-push-action](https://github.com/docker/build-push-action) -- Official Docker GitHub Action. HIGH confidence.
- [dorny/paths-filter](https://github.com/dorny/paths-filter) -- Monorepo path-based change detection. HIGH confidence (verified via official README).
- [GitHub Actions in 2026: Complete Guide to Monorepo CI/CD](https://dev.to/pockit_tools/github-actions-in-2026-the-complete-guide-to-monorepo-cicd-and-self-hosted-runners-1jop) -- Community guide, MEDIUM confidence.
- [How to Handle Monorepo Path Filters in GitHub Actions](https://oneuptime.com/blog/post/2025-12-20-monorepo-path-filters-github-actions/view) -- Blog post, MEDIUM confidence.
- [appleboy/ssh-action](https://github.com/appleboy/ssh-action) -- SSH deployment action. HIGH confidence (widely used, 9k+ stars).
- Existing project files: `docker-compose.yml`, `ecosystem.config.cjs`, all three `Dockerfile`s, `nginx.conf` files, `pom.xml`, `package.json` files, `.env.example` files. HIGH confidence (directly inspected).

---
*Architecture research for: CI/CD Pipeline with GitHub Actions + Docker Compose*
*Researched: 2026-04-17*
