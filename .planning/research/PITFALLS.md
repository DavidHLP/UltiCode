# Pitfalls Research: CI/CD Pipeline with GitHub Actions + Docker Compose

**Domain:** CI/CD pipeline for existing Spring Boot + Vue 3 monorepo
**Researched:** 2026-04-17
**Confidence:** HIGH

## Executive Summary

This document catalogs pitfalls specific to adding GitHub Actions CI/CD with Docker Compose deployment to the UltiCode platform -- an existing Java 17 / Spring Boot 3.5 backend, two Vue 3 + Vite frontends (console + management), a Dubbo recommendation service, MySQL, Redis, and Nacos. The project currently deploys manually via PM2 with Docker Compose for infrastructure services only. The research covers Dockerfile build issues, workflow trigger design, deployment automation gaps, secrets management, and Docker Compose production deployment traps.

The most critical finding is that the existing backend Dockerfile references `ulticode-backend-0.0.1-SNAPSHOT.jar` while the `pom.xml` declares `<version>1.0.0</version>` -- this mismatch will cause every CI build to fail silently in the COPY stage. Several other pitfalls stem from the gap between the current PM2-based dev workflow and what Docker Compose production deployment requires.

---

## Critical Pitfalls

Mistakes that cause CI build failures, broken deployments, or security breaches.

---

### Pitfall 1: Hardcoded JAR Name Mismatch in Backend Dockerfile

**What goes wrong:**
The backend `Dockerfile` line 27 copies:
```
COPY --from=builder /app/target/ulticode-backend-0.0.1-SNAPSHOT.jar ./app.jar
```
But `pom.xml` declares `<version>1.0.0</version>`. The actual built artifact is `ulticode-backend-1.0.0.jar`. The COPY instruction will fail with "file not found" and the entire Docker build will fail.

**Why it happens:**
The Dockerfile was written during early prototyping when the version was `0.0.1-SNAPSHOT`. The pom.xml version was later bumped to `1.0.0` for release but the Dockerfile was never updated. This is a classic drift between build definition and artifact version.

**How to avoid:**
Use a glob pattern or a Maven property to avoid hardcoding the version:
```dockerfile
COPY --from=builder /app/target/*.jar ./app.jar
```
Or better, use Spring Boot's layered JAR support with `spring-boot-maven-plugin` and copy the extracted layers. For multi-module safety, use:
```dockerfile
RUN ./mvnw package -DskipTests -B && \
    mv target/*.jar app.jar
```

**Warning signs:**
- Any version change in `pom.xml` requires checking the Dockerfile
- Local Docker builds fail with "no source files" or "file not found" in COPY step
- CI build passes Maven compile but fails at Docker image build stage

**Phase to address:**
CI/CD Phase 1 (Foundation) -- fix this before writing any workflow files, or all builds will fail.

---

### Pitfall 2: Monorepo Path Filtering "Skipped but Merged" Silent Failure

**What goes wrong:**
In a monorepo with 5+ services (backend, console, management, recommend-provider, recommend-web), using GitHub Actions `paths` filters on `on.push` can cause workflows to be silently skipped when a PR is merged. If path filtering determines that no relevant files changed, GitHub skips the workflow entirely -- even if you need it to run (e.g., a shared dependency change or a workflow file change).

For UltiCode specifically: a change to `db-manager/migrations/` should trigger a backend rebuild (the schema changed), but `paths: ['backend-spring/**']` would miss it. A change to `.env.example` should trigger redeployment validation but no path filter catches it.

**Why it happens:**
GitHub's native `paths` filter evaluates file changes in isolation. It cannot express "if file A changes, also rebuild service B." This is a well-known GitHub Actions limitation for monorepos.

**How to avoid:**
Use a two-stage approach with `dorny/paths-filter` action:
```yaml
jobs:
  detect-changes:
    outputs:
      backend: ${{ steps.filter.outputs.backend }}
      console: ${{ steps.filter.outputs.console }}
      management: ${{ steps.filter.outputs.management }}
    steps:
      - uses: actions/checkout@v4
      - uses: dorny/paths-filter@v3
        id: filter
        with:
          filters: |
            backend:
              - 'backend-spring/**'
              - 'db-manager/migrations/**'
              - 'docker-compose*.yml'
              - '.github/workflows/**'
            console:
              - 'console/**'
              - 'docker-compose*.yml'
            management:
              - 'management/**'
              - 'docker-compose*.yml'
```
Always include `workflow_dispatch` for manual hotfix builds.

**Warning signs:**
- Merged PRs don't trigger builds even though they should
- Changes to shared configs (docker-compose, env) don't rebuild services
- Workflow file changes use the old version, not the new one

**Phase to address:**
CI/CD Phase 1 (Foundation) -- the workflow trigger architecture must be designed correctly from the start.

---

### Pitfall 3: Nginx CSP Blocks API Calls in Docker Network

**What goes wrong:**
Both `console/nginx.conf` and `management/nginx.conf` have:
```nginx
proxy_pass http://backend:9001;
```
This uses the Docker Compose service name `backend` which only resolves inside the Docker network. This is correct for production Docker Compose deployment. However, the CSP header uses `${API_ORIGIN:-}` which defaults to empty string, meaning `connect-src` will not allow API calls in production.

Additionally, the Dockerfile sets `ENV API_ORIGIN="http://localhost:9001"` but in Docker Compose the backend is at `http://backend:9001`. The CSP will block all API requests from the browser because the actual origin (`http://backend:9001`) is not in the `connect-src` directive.

**Why it happens:**
The nginx.conf was written for a mixed setup where the frontend might be served locally (PM2/Vite dev server) against localhost, or in Docker where the service name is different. The CSP environment variable substitution happens at container start via nginx templates, but the Dockerfile sets the wrong default for the Docker Compose context.

**How to avoid:**
In the Dockerfile, do not set `API_ORIGIN` -- let docker-compose.prod.yml pass it:
```dockerfile
# Remove this line from Dockerfile:
# ENV API_ORIGIN="http://localhost:9001"
```
In `docker-compose.prod.yml`, pass the correct internal origin:
```yaml
console:
  environment:
    - API_ORIGIN=http://backend:9001
management:
  environment:
    - API_ORIGIN=http://backend:9001
```

**Warning signs:**
- Browser console shows CSP violations for API calls
- Frontend loads but all API requests are blocked
- Works in dev (localhost) but not in Docker deployment

**Phase to address:**
CI/CD Phase 1 (Foundation) -- fix before first production deployment.

---

### Pitfall 4: Docker Compose Has No Native Zero-Downtime Deployment

**What goes wrong:**
`docker compose up -d` stops old containers before starting new ones, causing 1-30 seconds of downtime per service during deployment. For UltiCode with 5 services, a full deployment means sequential downtime across backend, console, management, and recommendation services.

Users in the middle of a coding session (the core use case of a LeetCode-like platform) will lose their work or see errors during deployment.

**Why it happens:**
Docker Compose is designed for development and single-node orchestration. It does not have built-in rolling update support like Docker Swarm or Kubernetes. The `restart: unless-stopped` policy helps with crashes but not with planned updates.

**How to avoid:**
For the initial CI/CD setup, accept brief downtime with a maintenance page strategy:
1. Deploy during low-traffic windows
2. Use `docker compose up -d --no-deps backend` to update services independently
3. Order: backend first, then frontends (frontends show errors gracefully while backend restarts)

For later improvement, consider:
- `docker-rollout` CLI plugin for rolling updates on single node
- Or migrate to Docker Swarm mode which has native rolling updates

**Warning signs:**
- Users report errors during deployment windows
- Health checks fail during container restart
- WebSocket connections (used for real-time features) drop unexpectedly

**Phase to address:**
CI/CD Phase 2 (Deployment) -- implement ordered deployment with health check waits. Zero-downtime can be a later enhancement.

---

### Pitfall 5: Maven `.mvn/` Wrapper Not Fully Committed, Breaking CI Build

**What goes wrong:**
The backend Dockerfile copies `backend-spring/.mvn/` and `backend-spring/mvnw` for the build, but the backend `.gitignore` includes `.mvn/wrapper/maven-wrapper.jar`. If the Maven wrapper files are not committed properly, the CI runner cannot execute `./mvnw` and the build fails immediately.

**Why it happens:**
The `.mvn/wrapper/maven-wrapper.jar` is gitignored (line 3 of backend-spring/.gitignore), which is correct for the JAR but the wrapper properties file and shell scripts must be present. If a developer ran `mvn wrapper:wrapper` locally and only the JAR was generated, the shell script and properties may not be committed.

**How to avoid:**
Verify that `backend-spring/.mvn/wrapper/maven-wrapper.properties` and `backend-spring/mvnw` are committed. In CI, bypass the wrapper entirely by using `actions/setup-java` which installs Maven automatically:
```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    java-version: '17'
    distribution: 'temurin'
    cache: 'maven'
```

**Warning signs:**
- CI fails with "mvnw: permission denied" or "mvnw: No such file or directory"
- Local builds work but CI fails
- `.mvn/wrapper/maven-wrapper.properties` is missing from git

**Phase to address:**
CI/CD Phase 1 (Foundation) -- use `actions/setup-java` instead of the Maven wrapper in CI.

---

### Pitfall 6: Environment Variable Drift Between Dev (PM2) and Prod (Docker)

**What goes wrong:**
The current PM2 `ecosystem.config.cjs` reads from `.env` and passes variables like `NACOS_PORT=28848` and `REDIS_PASSWORD` to processes. Docker Compose production uses different hostnames (`mysql` instead of `localhost`, `redis` instead of `localhost`, port `3306` instead of `23306`). Secrets in GitHub Actions secrets are a flat key-value store that must be mapped correctly to each service's environment.

If the CI/CD workflow passes the wrong variable names or values, the backend will fail to connect to MySQL, Redis, or Nacos. The health check will pass (because Spring Boot starts even without DB connection) but the application will be non-functional.

**Why it happens:**
There are currently 5 different environments for configuration:
1. `.env` (root) -- Docker Compose infrastructure
2. `backend-spring/.env` -- Spring Boot application
3. `console/.env` / `management/.env` -- Vite frontends
4. `ecosystem.config.cjs` -- PM2 process manager
5. `docker-compose.prod.yml` -- Production Docker Compose

Each uses slightly different variable names and default values. The CI/CD workflow introduces a 6th source (GitHub Secrets).

**How to avoid:**
Create a single source-of-truth mapping document and enforce it:
```yaml
# .github/env-mapping.yml (reference, not committed)
# GitHub Secret Name -> Docker Compose Environment Variable
GHCR_TOKEN -> GHCR_REGISTRY auth
DB_PASSWORD -> DB_PASSWORD (shared across mysql + backend)
JWT_SECRET -> backend JWT_SECRET
REDIS_PASSWORD -> REDIS_PASSWORD (shared across redis + backend)
```
In the CI workflow, map GitHub secrets to a `.env.prod` file on the runner, then pass it to `docker compose --env-file .env.prod`.

**Warning signs:**
- Backend starts but cannot connect to MySQL/Redis
- Nacos registration fails
- "Connection refused" errors in logs
- Health check passes but API returns 500

**Phase to address:**
CI/CD Phase 1 (Foundation) -- create the environment mapping before any deployment workflow.

---

### Pitfall 7: Docker Build Cache Not Leveraged in CI, Causing 15+ Minute Builds

**What goes wrong:**
Without build caching, every CI run downloads all Maven dependencies (~500MB for Spring Boot + MyBatis-Plus + Dubbo) and all npm dependencies for two frontends. The Spring Boot backend alone can take 8-10 minutes for a cold build. Total CI build time without caching: 15-25 minutes per run.

**Why it happens:**
GitHub Actions runners start with a clean environment. The existing Dockerfiles have multi-stage builds with dependency caching in the Docker layer (copy pom.xml first, run `dependency:go-offline`), but GitHub Actions discards Docker layer cache between runs unless explicitly configured.

**How to avoid:**
Use `docker/build-push-action` with GitHub Actions cache backend:
```yaml
- uses: docker/build-push-action@v6
  with:
    context: .
    file: backend-spring/Dockerfile
    push: true
    tags: ghcr.io/${{ github.repository }}/backend:${{ github.sha }}
    cache-from: type=gha,scope=backend
    cache-to: type=gha,mode=max,scope=backend
```
Also use `actions/setup-java` with Maven cache and `actions/setup-node` with pnpm cache for frontends.

**Warning signs:**
- CI builds consistently take 15+ minutes
- Maven dependency download logs show full downloads (not cached)
- GitHub Actions minutes burn through free tier quickly

**Phase to address:**
CI/CD Phase 1 (Foundation) -- configure caching in the first workflow version.

---

## Technical Debt Patterns

Shortcuts that seem reasonable when adding CI/CD to an existing project but create long-term problems.

| Shortcut | Immediate Benefit | Long-Term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Single monolithic workflow for all services | Simple to set up, one file to maintain | Every change triggers full build; slow feedback; hard to debug | First PR only, refactor immediately after |
| Hardcoded image tags (`latest`) | No tag management needed | Cannot rollback; cache poisoning; non-deterministic deployments | Never in production |
| Using `repository` secrets instead of `environment` secrets | Simpler setup | No deployment protection rules, no staging/prod separation | Dev/staging only, never for production secrets |
| SSH-based deployment (`ssh deploy@server docker compose up`) | No registry needed, simple mental model | Server credentials in CI secrets, no audit trail, no rollback | MVP/demo only, migrate to GHCR + pull-based deployment |
| Skipping integration tests in CI to save time | Faster builds | Bugs reach production, false confidence in CI | Never -- run at least smoke tests |
| Using `docker compose` without health check waits | Simpler deployment script | Services start before dependencies are ready, intermittent failures | Never in production |
| Duplicating `.env` values as GitHub Secrets | Quick to set up | Drift between sources, hard to audit, no single source of truth | First setup only, must create mapping document |

## Integration Gotchas

Common mistakes when connecting CI/CD components.

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| GHCR (GitHub Container Registry) | Not setting `packages: write` permission in workflow | Add `permissions: packages: write` to the workflow job |
| GHCR | Using `GITHUB_TOKEN` without logging in | Run `echo $GITHUB_TOKEN \| docker login ghcr.io -u ${{ github.actor }} --password-stdin` |
| Docker Buildx | Not creating a builder instance | Use `docker/setup-buildx-action` before build-push-action |
| docker-compose.prod.yml | Missing `networks:` definition for recommend-provider/recommend-web | Define `app-network` in docker-compose.yml or add `networks: [default]` |
| Flyway migrations | Running migrations in CI without a database | Use Testcontainers in backend tests; run Flyway as a separate deployment step on the production DB |
| Nacos service discovery | Backend registers with Nacos but CI-deployed containers cannot reach Nacos | Ensure all services share the same Docker network; use `depends_on` with health checks |
| Recommendation service | `RECOMMEND_IMAGE_TAG` and `RECOMMEND_WEB_IMAGE_TAG` in docker-compose.prod.yml don't reference GHCR | Either build recommendation images in CI or use a separate registry reference |
| Spring Boot Actuator | Health endpoint returns `DOWN` because DB is not yet accessible | Use `start_period` in healthcheck to give Spring Boot time to initialize connections |
| Frontend Dockerfile | Missing `pnpm-lock.yaml` in COPY step, causing `--frozen-lockfile` to fail | Copy both `package.json` and `pnpm-lock.yaml` before running `pnpm install` |
| nginx.conf template | CSP uses `${API_ORIGIN:-}` but nginx templates only substitute `$API_ORIGIN` (no braces) | Use `envsubst` or change nginx template syntax to `$API_ORIGIN` without braces |

## Performance Traps

Patterns that work in dev but fail as CI/CD usage grows.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| No parallel job execution | Sequential builds take 25+ minutes | Use `needs:` DAG to build independent services in parallel | Immediately -- backend and frontends have no build dependency |
| Not using `concurrency` groups | Multiple PR pushes create duplicate CI runs | Add `concurrency: { group: ${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: true }` | Active development with frequent pushes |
| Full rebuild on every push | CI minutes exhausted, slow feedback | Use path filtering + Docker layer caching + Maven/npm caches | After 10+ pushes per day |
| Pulling base images without caching | Alpine/JDK base image pulls add 2-3 minutes per build | Use `cache-from: type=registry` to cache base image layers | Always in CI |
| Not limiting log output | Giant Maven dependency download logs consume storage | Use `--batch-mode` and `--quiet` flags in Maven | Large dependency trees |
| Maven `dependency:go-offline` on every build | Downloads transitive dependencies even when unchanged | Rely on Docker layer cache + `cache-from: type=gha` instead of re-downloading | Multi-module projects with large dependency trees |

## Security Mistakes

Domain-specific security issues for CI/CD pipelines.

| Mistake | Risk | Prevention |
|---------|------|------------|
| Using `::add-mask::` incorrectly or not at all | Secrets leak into GitHub Actions logs visible to all repo collaborators | Mask all secrets before use; never `echo ${{ secrets.X }}` directly |
| Pinning actions to tags instead of SHA | Supply chain attack -- tag can be reassigned to malicious commit | Pin to full commit SHA: `uses: actions/checkout@b4ffde65f46336ab88eb53be808477a3936bae11` |
| Over-permissive `GITHUB_TOKEN` | Compromised workflow can push to any branch, modify secrets | Scope permissions minimally: `permissions: { contents: read, packages: write }` |
| `.env` file with real secrets committed to git | Secrets in git history, accessible forever | Ensure `.env` is in `.gitignore`; use `git filter-branch` or BFG if leaked |
| Docker Compose with default Nacos credentials (`nacos/nacos`) | Nacos exposed with known credentials on production | docker-compose.prod.yml uses `${NACOS_USERNAME}` and `${NACOS_PASSWORD}` -- always override in production |
| Passing secrets as Docker build args | Secrets visible in `docker history` | Use Docker BuildKit secret mounts: `--secret id=mysecret,src=./secret.txt` |
| Not rotating GHCR push token | Long-lived token can be compromised if runner is compromised | Use `GITHUB_TOKEN` which auto-rotates per workflow run |
| Exposing Docker socket in CI | Container escape, root access on runner | Never mount `/var/run/docker.sock` in CI; use DinD (Docker-in-Docker) only if necessary with security constraints |
| Workflow triggers on all branches with deployment | Feature branch pushes trigger production deployment | Use branch filters: `branches: [main]` for deployment; `branches: ['**']` for CI only |
| `pull_request_target` with untrusted checkout | Arbitrary code execution from fork PRs | Never use `pull_request_target` with `persist-credentials: true` for public repos |

## "Looks Done But Isn't" Checklist

Things that appear complete when CI/CD is added but are missing critical pieces.

- [ ] **Docker build succeeds locally but fails in CI:** Verify Maven wrapper files are committed, check that `actions/setup-java` version matches local JDK, confirm Docker BuildKit is enabled on runner.
- [ ] **CI workflow triggers on push:** Verify it also triggers on `pull_request`; test with a non-main branch push; confirm path filters don't silently skip needed builds.
- [ ] **Docker image pushed to GHCR:** Verify the image is actually runnable (`docker pull && docker run`); check that the correct architecture is built (CI runs on `linux/amd64`, local might be `arm64`).
- [ ] **Docker Compose deployment works:** Verify all services pass health checks; test with `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` to validate composition; test actual API calls, not just container startup.
- [ ] **Frontend can reach backend API:** Test from a browser, not just `curl localhost`; CSP headers must allow the actual API origin; check browser console for CORS or CSP errors.
- [ ] **Database migrations run before backend starts:** Flyway migrations must complete before Spring Boot connects; either run migrations as a separate CI step or use Spring Boot's built-in Flyway auto-migration with `spring.flyway.enabled=true`.
- [ ] **Secrets are not in git history:** Run `git log --all --full-history -- '*.env'` to check; use `trufflehog` or `gitleaks` to scan history.
- [ ] **Rollback is possible:** Verify you can deploy a previous image tag; test `IMAGE_TAG=sha-abc123 docker compose up -d`; ensure database migrations are backward-compatible.
- [ ] **Workflow file changes are tested:** Push a workflow change to a feature branch and verify it runs correctly; remember that the old version of a workflow runs on the push that modifies it.
- [ ] **Recommendation service is included or explicitly excluded:** docker-compose.prod.yml references `${RECOMMEND_IMAGE_TAG:-latest}` but there is no CI job building this image; either add a build job or document that it is excluded from CI/CD.
- [ ] **pnpm-lock.yaml is committed:** The frontend Dockerfiles use `pnpm install --frozen-lockfile` which requires the lock file to be present; if it is gitignored, builds will fail.
- [ ] **Health checks use correct internal hostnames:** The healthcheck in docker-compose.prod.yml uses `curl -f http://localhost:9001/actuator/health` which is correct (container-local), but nginx proxy_pass uses `http://backend:9001` which requires Docker network resolution.

## Recovery Strategies

When pitfalls occur despite prevention, how to recover.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| JAR name mismatch in Dockerfile | LOW | Update Dockerfile to use glob `*.jar`; rebuild |
| Workflow silently skipped due to path filter | LOW | Add `workflow_dispatch` trigger; manually trigger; then fix path filters |
| CSP blocks API calls in production | MEDIUM | Update `API_ORIGIN` env var in docker-compose.prod.yml; restart frontend containers |
| Deployed broken image to production | MEDIUM | Re-tag previous known-good image; `docker compose pull` and `up -d` with old tag |
| Secrets leaked in git history | HIGH | Rotate all leaked secrets immediately; use BFG Repo Cleaner to rewrite history; force push (coordinate with team) |
| Database migration fails mid-deployment | HIGH | Connect to MySQL directly; check Flyway schema history; manually fix or revert migration; `FLYWAY_REPAIR=true` |
| GHCR authentication fails | LOW | Regenerate PAT; update GitHub secret; re-run workflow |
| Docker Compose network isolation breaks | MEDIUM | Verify all services are on the same network; check `docker network inspect`; add `networks:` definition |
| CI runner out of disk space | LOW | Clean Docker images and caches; add `docker system prune` step to workflow |
| pnpm-lock.yaml missing from repo | LOW | Run `pnpm install` locally to regenerate; commit the lock file |

## Pitfall-to-Phase Mapping

How CI/CD roadmap phases should address these pitfalls.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| JAR name mismatch (Pitfall 1) | Phase 1: Foundation | Local `docker build` succeeds before any CI workflow is written |
| Monorepo path filtering (Pitfall 2) | Phase 1: Foundation | Push changes to individual service dirs; verify only affected jobs run |
| Nginx CSP/API proxy (Pitfall 3) | Phase 1: Foundation | Full Docker Compose stack starts; browser DevTools shows no CSP errors |
| Zero-downtime deployment (Pitfall 4) | Phase 2: Deployment | Monitor health checks during deployment; measure downtime |
| Maven wrapper in CI (Pitfall 5) | Phase 1: Foundation | CI workflow runs `./mvnw` or `mvn` successfully |
| Environment variable drift (Pitfall 6) | Phase 1: Foundation | Create env mapping doc; test with a fresh `.env.prod` from GitHub Secrets |
| Docker build cache (Pitfall 7) | Phase 1: Foundation | Second CI run is significantly faster than first; check cache hit logs |
| Recommendation service excluded | Phase 2: Deployment | Decision documented: include in CI or exclude with explicit rationale |
| Rollback capability | Phase 2: Deployment | Test deploying previous image tag; verify it works |

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Workflow trigger design | Silent skips on monorepo path filters | Use `dorny/paths-filter` two-stage approach |
| Docker image build | JAR name mismatch, missing lock files | Fix Dockerfile before writing workflows |
| Secrets setup | Environment variable name drift | Create mapping document; validate against docker-compose.prod.yml |
| GHCR push | Missing `packages: write` permission | Add explicit `permissions` block to workflow |
| Docker Compose deployment | No zero-downtime, health check ordering | Deploy backend first, wait for healthy, then frontends |
| Frontend Docker build | CSP template substitution failure | Remove `API_ORIGIN` default from Dockerfile; test nginx template rendering |
| Recommendation service | Image tag not built by CI | Either add build job or document exclusion decision |
| Flyway migrations | Running against wrong database | Use Testcontainers for CI tests; separate migration step for prod |

---

## Sources

| Source | Confidence | URL |
|--------|------------|-----|
| GitHub Community: Common Problems with GitHub Actions and Docker | HIGH | https://github.com/orgs/community/discussions/118365 |
| GitHub Community: Docker & CI Security Mistakes | HIGH | https://github.com/orgs/community/discussions/184822 |
| Docker Build Cache Management for GitHub Actions (Official) | HIGH | https://docs.docker.com/build/ci/github-actions/cache/ |
| docker/build-push-action (Official GitHub Action) | HIGH | https://github.com/docker/build-push-action |
| dorny/paths-filter (Community Action) | HIGH | https://github.com/dorny/paths-filter |
| docker-rollout: Zero Downtime Deployment for Docker Compose | MEDIUM | https://github.com/wowu/docker-rollout |
| Stack Overflow: Zero-Downtime Deployments Using Docker Compose | HIGH | https://stackoverflow.com/questions/59483549/zero-down-time-deployments-using-docker-compose |
| Baeldung: Multi-Module Maven Projects in Docker | HIGH | https://www.baeldung.com/docker-maven-build-multi-module-projects |
| Top 5 Mistakes Developers Make When Using GitHub Actions | MEDIUM | https://javascript.plainenglish.io/top-5-mistakes-developers-make-when-using-github-actions-and-how-i-fixed-them-ec28a836f78e |
| Resolving Docker Multi-stage Build Errors on GitHub Actions | MEDIUM | https://thadaw.com/posts/resolving-docker-multi-stage-build-errors-on-github-actions/ |
| 15 Common Docker Mistakes (Stackademic) | MEDIUM | https://blog.stackademic.com/15-common-docker-mistakes-and-how-to-avoid-them-525b803d00f9 |
| GitHub Actions Troubleshooting (Official) | HIGH | https://docs.github.com/en/actions/how-tos/troubleshoot-workflows |
| GitHub Actions CI/CD Best Practices (GitHub) | HIGH | https://github.com/github/awesome-copilot/blob/main/instructions/github-actions-ci-cd-best-practices.instructions.md |
| Speeding Up Slow Docker Builds in GitHub Actions | MEDIUM | https://medium.com/@FrankGoortani/speeding-up-slow-docker-builds-in-github-actions-24ca574fac45 |
| Docker Multi-stage Build Errors on GitHub Actions (Thadaw) | MEDIUM | https://thadaw.com/posts/resolving-docker-multi-stage-build-errors-on-github-actions/ |
| UltiCode codebase analysis (direct inspection) | HIGH | Dockerfiles, docker-compose.yml, docker-compose.prod.yml, pom.xml, nginx configs, ecosystem.config.cjs, .gitignore, .env.example |

**Gaps to address with phase-specific research:**
- Exact `docker compose config` validation output for the combined docker-compose.yml + docker-compose.prod.yml to catch missing network definitions before deployment.
- Current state of pnpm-lock.yaml files -- are they committed or gitignored?
- Whether the recommendation service should be included in CI/CD scope or excluded as a separate deployment concern.
- Exact GitHub Actions runner specs (disk, memory) to determine if Maven dependency caching fits within the 14GB free tier storage.

---
*Pitfalls research for: CI/CD Pipeline with GitHub Actions + Docker Compose*
*Researched: 2026-04-17*
