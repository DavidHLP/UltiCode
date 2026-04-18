# Phase 9: Foundation + CI - Research

**Researched:** 2026-04-18
**Domain:** Docker build configuration, GitHub Actions CI/CD, Spring Boot CI profiles
**Confidence:** HIGH

## Summary

This phase fixes pre-existing Dockerfile bugs (JAR name mismatch, missing lockfile COPY), creates a Spring Boot CI profile for GitHub Actions service containers, produces a secrets mapping document, and consolidates the existing two-workflow CI pipeline into a single unified `ci.yml` using `dorny/paths-filter@v4`.

The project already has mature CI infrastructure (`ci-backend.yml`, `ci-frontend.yml`, `ci-recommendation.yml`, `cd-deploy.yml`). The main work is consolidation and bug fixes rather than building from scratch. The existing workflows already demonstrate the correct patterns for GitHub Actions services (MySQL 9.1 + Redis 7-alpine with health checks), Maven caching via `setup-java`, pnpm caching via `setup-node`, and Docker layer caching via GHA cache backend. The unified `ci.yml` should port these established patterns into a single file with `dorny/paths-filter` for monorepo path gating.

The most impactful bug is FOUND-01: the backend Dockerfile references `ulticode-backend-0.0.1-SNAPSHOT.jar` but `pom.xml` declares `<version>1.0.0</version>`, which would cause `docker build` to fail at the COPY stage. The fix via `<finalName>app</finalName>` is a standard Maven pattern that decouples the Dockerfile from pom.xml version changes.

**Primary recommendation:** Port existing CI patterns into a single `ci.yml` with `dorny/paths-filter@v4` changes job, fix the three Dockerfile bugs (JAR name, two missing lockfile COPYs), and create the CI Spring profile based on `application-example.yml` structure.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Use Maven `<finalName>app</finalName>` in backend pom.xml to generate a predictable `app.jar`, permanently eliminating version-matching issues between Dockerfile COPY and Maven build output.
- **D-02:** Frontend Dockerfiles must COPY `pnpm-lock.yaml` before `pnpm install --frozen-lockfile`. The lockfiles exist at `console/pnpm-lock.yaml` and `management/pnpm-lock.yaml` respectively.
- **D-03:** Nginx CSP `connect-src` is already correct — uses `'self' ${API_ORIGIN:-}` which covers both same-origin API proxy and configurable external origins. No changes needed unless testing reveals issues.
- **D-04:** Root `.dockerignore` already exists with reasonable content. Review and add missing entries: `.claude/`, `.planning/`, `recommendation/`, `*.tar.gz`.
- **D-05:** Create a single unified `ci.yml` workflow that replaces existing separate `ci-backend.yml` and `ci-frontend.yml` files. Archive (delete) old separate files after unified workflow is validated.
- **D-06:** Use `dorny/paths-filter` action for path-based job gating rather than native GitHub `paths:` triggers.
- **D-07:** Keep `ci-recommendation.yml` as-is — the recommendation service is explicitly out of scope per REQUIREMENTS.md.
- **D-08:** Create `application-ci.yml` Spring profile in `backend-spring/src/main/resources/`. Set `spring.datasource.url` to use `localhost:23306/ulticode_test`, `spring.data.redis.host` to `localhost` and `spring.data.redis.port` to `26379`, disable Testcontainers auto-configuration if present, set `spring.jpa.hibernate.ddl-auto` to `none`, enable `spring.flyway.enabled=true` with clean baseline.
- **D-09:** Backend test job uses `./mvnw test -Dspring.profiles.active=ci -B` to activate the CI profile.
- **D-10:** Console and management CI jobs each run lint + type-check + test independently when their respective paths change.
- **D-11:** Add a `docker-verify` job that builds all 3 Docker images WITHOUT pushing. Only runs when `Dockerfile*`, `docker-compose*.yml`, `.dockerignore`, or `nginx*.conf` files change.
- **D-12:** Maven cache: continue using `setup-java` with `cache: 'maven'`.
- **D-13:** pnpm cache: continue using `setup-node` with `cache: 'pnpm'` per app's lockfile.
- **D-14:** Docker layer cache: use `cache-from: type=gha, cache-to: type=gha,mode=max` for Docker build verification job.
- **D-15:** Create `docs/secrets-mapping.md` as a Markdown table document. Cross-reference all 6 configuration sources: GitHub Actions Secrets, Docker Compose env vars, Spring Boot profiles, Vite env vars, PM2 ecosystem config, Backend .env file.

### Claude's Discretion
- Exact job dependency graph in ci.yml (which jobs run in parallel vs sequential)
- Error handling and notification patterns in CI workflows
- Test artifact upload configuration
- Concurrency group naming conventions

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FOUND-01 | Backend Dockerfile JAR name references corrected to match pom.xml version | `<finalName>app</finalName>` pattern documented in Standard Stack; pom.xml build section currently has no finalName (verified at line 203-241) |
| FOUND-02 | Frontend Dockerfiles copy pnpm-lock.yaml before install step | Both console/Dockerfile and management/Dockerfile confirmed missing pnpm-lock.yaml COPY; lockfiles exist at console/pnpm-lock.yaml and management/pnpm-lock.yaml (verified via glob) |
| FOUND-03 | Nginx CSP connect-src allows API calls via Docker Compose internal hostname | Both nginx.conf files confirmed: `connect-src 'self' ${API_ORIGIN:-}` with `proxy_pass http://backend:9001` — already correct per D-03 |
| FOUND-04 | Root .dockerignore file created to reduce Docker build context size | File exists at `.dockerignore` with 66 lines; needs additions for `.claude/`, `.planning/`, `recommendation/`, `*.tar.gz` per D-04 |
| FOUND-05 | application-ci.yml Spring profile created for backend tests in GHA | `application-example.yml` provides template structure; GHA services config from existing ci-backend.yml (MySQL 9.1 on port 23306, Redis 7 on port 26379) |
| FOUND-06 | Secrets mapping document created cross-referencing all config sources | 6 sources identified: GitHub Secrets, Docker Compose, Spring profiles (4 YAML files), Vite env vars (root .env), PM2 ecosystem (ecosystem.config.cjs), backend .env |
| CI-01 | ci.yml workflow triggers on pull_request and push to main with dorny/paths-filter | `dorny/paths-filter@v4` is latest version; documented pattern for changes job with outputs consumed by downstream jobs |
| CI-02 | Backend CI job runs mvnw compile + test with application-ci.yml profile using GHA services | Existing ci-backend.yml already has correct services config (MySQL 9.1 + Redis 7-alpine with health checks); port mapping 23306 and 26379 confirmed |
| CI-03 | Console frontend CI job runs lint + type-check + test when console/ paths change | Existing ci-frontend.yml matrix pattern can be ported; pnpm/action-setup@v4 + setup-node@v4 with cache-dependency-path |
| CI-04 | Management frontend CI job runs lint + type-check + test when management/ paths change | Same pattern as CI-03, separate path filter for management/ |
| CI-05 | Docker build verification job builds all 3 Docker images | Existing Docker build pattern from ci-backend.yml docker job (setup-buildx-action + build-push-action without push); context is project root `.` |
| CI-06 | Build caching for Maven, pnpm store, and Docker layers via GHA cache | All three caching strategies already in use: setup-java cache:maven, setup-node cache:pnpm, build-push-action cache-from/to type=gha |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Docker build correctness (FOUND-01/02/04) | Build / CI | -- | Dockerfiles are build-time artifacts; fixes prevent CI failures |
| Nginx CSP configuration (FOUND-03) | CDN / Static (nginx) | -- | CSP is a response header set by nginx at serving time |
| Spring Boot CI profile (FOUND-05) | API / Backend | -- | application-ci.yml configures how backend tests connect to services |
| Secrets mapping document (FOUND-06) | Cross-cutting | -- | Documentation that maps all configuration sources |
| Path-filtered CI gating (CI-01) | CI / Automation | -- | GitHub Actions workflow file configuration |
| Backend test execution (CI-02) | API / Backend | CI / Automation | Tests run backend code; CI provides the execution environment |
| Frontend lint/type-check/test (CI-03/04) | Browser / Client | CI / Automation | Frontend quality checks; CI provides the execution environment |
| Docker build verification (CI-05) | Build / CI | -- | Validates Dockerfile correctness in CI |
| Build caching (CI-06) | CI / Automation | -- | Caching is a CI infrastructure concern |

## Standard Stack

### Core

| Library / Action | Version | Purpose | Why Standard |
|------------------|---------|---------|--------------|
| `dorny/paths-filter` | v4 | Monorepo path-based job gating in GitHub Actions | Latest version; used by ~11% of GHA workflows; supports PR, push, and merge_group triggers; outputs consumed by downstream job `if:` conditions [CITED: github.com/dorny/paths-filter] |
| `actions/checkout` | v4 | Checkout repository | Standard GitHub action |
| `actions/setup-java` | v4 | JDK 17 setup with Maven cache | `cache: 'maven'` provides built-in `.m2` caching [VERIFIED: existing ci-backend.yml line 34-38] |
| `actions/setup-node` | v4 | Node.js 22 setup with pnpm cache | `cache: 'pnpm'` with `cache-dependency-path` for per-app lockfile isolation [VERIFIED: existing ci-frontend.yml line 46-50] |
| `pnpm/action-setup` | v4 | pnpm package manager setup | Required before setup-node for pnpm cache integration [VERIFIED: existing ci-frontend.yml line 41-43] |
| `docker/setup-buildx-action` | v3 | Docker Buildx setup | Required for GHA cache backend [VERIFIED: existing ci-backend.yml line 181] |
| `docker/build-push-action` | v6 | Docker build with GHA caching | `cache-from: type=gha, cache-to: type=gha,mode=max` [VERIFIED: existing ci-backend.yml line 199-208] |

### Supporting

| Library / Action | Version | Purpose | When to Use |
|------------------|---------|---------|-------------|
| `actions/upload-artifact` | v4 | Upload test results on failure | Backend test job failure path [VERIFIED: existing ci-backend.yml line 100-106] |
| `docker/metadata-action` | v5 | Extract Docker image tags | CD workflow (Phase 10); not needed for CI-05 verify job |
| `actions/setup-python` | v5 | Python setup for db-manager | Migration validation job [VERIFIED: existing ci-backend.yml line 137-139] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `dorny/paths-filter@v4` | Native GitHub `paths:` triggers | Native only works at workflow level, not job level; can't share change detection outputs between jobs in same workflow |
| `dorny/paths-filter@v4` | `tj-actions/changed-files` | More complex API; dorny/paths-filter is simpler for boolean "changed or not" gating |
| `<finalName>app</finalName>` | `${project.artifactId}-${project.version}` in Dockerfile | Defeats the purpose; version changes would still break the Dockerfile. `finalName=app` is version-independent |
| Single `ci.yml` | Separate workflow files per service | Separate files work but can't share path-filter outputs; single file with dorny/paths-filter enables cross-job gating |

**Installation:** No new packages needed. All actions are GitHub Actions used in workflows. Maven `<finalName>` is a pom.xml configuration change.

**Version verification:** All GitHub Actions versions verified from existing workflow files. `dorny/paths-filter@v4` confirmed as latest via web search (no v5 exists).

## Architecture Patterns

### System Architecture Diagram

```
Pull Request / Push to main
         |
         v
  +------------------+
  |   changes job    |  dorny/paths-filter@v4
  |  (fast, ~10s)    |  Outputs: backend, console, management, docker
  +--------+---------+
           |
     +-----+------+-------+
     |            |       |
     v            v       v
+---------+  +--------+  +----------+
| backend |  | console|  |management|
| CI job  |  | CI job |  | CI job   |
|         |  |        |  |          |
| compile |  | lint   |  | lint     |
| test    |  | type-ck|  | type-ck  |
| (MySQL/ |  | test   |  | test     |
|  Redis  |  |        |  |          |
|  svcs)  |  +--------+  +----------+
+---------+
     |
     v  (if docker files changed)
+--------------+
| docker-verify|
| Build 3 imgs |
| No push      |
+--------------+
```

### Recommended Project Structure

```
.github/
  workflows/
    ci.yml                      # Unified CI (replaces ci-backend.yml + ci-frontend.yml)
    ci-recommendation.yml       # Kept as-is (out of scope)
    cd-deploy.yml               # CD workflow (Phase 10, read-only reference)
  filters.yml                   # Optional: external filter definitions for dorny/paths-filter

backend-spring/
  src/main/resources/
    application.yml             # Main config (unchanged)
    application-dev.yml         # Dev profile (unchanged)
    application-prod.yml        # Prod profile (unchanged)
    application-example.yml     # Example profile (unchanged)
    application-ci.yml          # NEW: CI profile for GitHub Actions

docs/
  secrets-mapping.md            # NEW: Configuration sources cross-reference

console/
  Dockerfile                    # FIX: Add pnpm-lock.yaml COPY
  nginx.conf                    # VERIFY: CSP connect-src (no change expected)

management/
  Dockerfile                    # FIX: Add pnpm-lock.yaml COPY
  nginx.conf                    # VERIFY: CSP connect-src (no change expected)

backend-spring/
  Dockerfile                    # FIX: JAR name -> app.jar
  pom.xml                       # FIX: Add <finalName>app</finalName>

.dockerignore                   # UPDATE: Add .claude/, .planning/, recommendation/, *.tar.gz
```

### Pattern 1: dorny/paths-filter Changes Job

**What:** A lightweight job that runs `dorny/paths-filter` to detect which parts of the monorepo changed, then outputs boolean flags consumed by downstream jobs.

**When to use:** Monorepo with multiple services where you want to skip unrelated CI jobs.

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
      docker: ${{ steps.filter.outputs.docker }}
    steps:
      - uses: dorny/paths-filter@v4
        id: filter
        with:
          filters: |
            backend:
              - 'backend-spring/**'
              - 'db-manager/migrations/**'
            console:
              - 'console/**'
              - 'shared/**'
            management:
              - 'management/**'
              - 'shared/**'
            docker:
              - 'Dockerfile*'
              - 'backend-spring/Dockerfile'
              - 'console/Dockerfile'
              - 'management/Dockerfile'
              - 'docker-compose*.yml'
              - '.dockerignore'
              - 'console/nginx.conf'
              - 'management/nginx.conf'
```

Source: [CITED: github.com/dorny/paths-filter - Conditional execution job-level example]

### Pattern 2: Maven finalName for Predictable Docker Builds

**What:** Set `<finalName>` in the Maven `<build>` section to generate a version-independent JAR name.

**When to use:** Any Spring Boot project built into Docker where the Dockerfile needs a predictable COPY target.

**Example:**
```xml
<build>
    <finalName>app</finalName>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <!-- existing configuration -->
        </plugin>
    </plugins>
</build>
```

Dockerfile reference:
```dockerfile
COPY --from=builder /app/target/app.jar ./app.jar
```

Source: [CITED: Spring Boot Maven Plugin docs, Stack Overflow 37698473]

### Pattern 3: Spring Boot CI Profile with GHA Services

**What:** A dedicated `application-ci.yml` profile that configures database and Redis to use GitHub Actions service container endpoints (localhost with mapped ports).

**When to use:** When backend tests need real MySQL and Redis but can't use Testcontainers (Docker-in-Docker not available on GitHub-hosted runners).

**Example:**
```yaml
# application-ci.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:23306/ulticode_test?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
    username: ulticode
    password: ulticode

  data:
    redis:
      host: localhost
      port: 26379
      password: ""

  jpa:
    hibernate:
      ddl-auto: none

  flyway:
    enabled: true
    baseline-on-migrate: true

server:
  port: 0  # Random port for tests

jwt:
  secret: test-jwt-secret-key-for-ci-minimum-32-characters-long

recommendation:
  enabled: false
```

The CI profile values match the existing GHA services configuration in ci-backend.yml:
- MySQL 9.1 mapped to port 23306 with `MYSQL_DATABASE: ulticode_test`, `MYSQL_USER: ulticode`, `MYSQL_PASSWORD: ulticode` [VERIFIED: ci-backend.yml lines 51-64]
- Redis 7-alpine mapped to port 26379 [VERIFIED: ci-backend.yml lines 65-73]

### Pattern 4: Frontend Dockerfile with pnpm-lock.yaml COPY

**What:** Copy `pnpm-lock.yaml` before `pnpm install --frozen-lockfile` to enable proper Docker layer caching and satisfy `--frozen-lockfile` requirement.

**When to use:** Any Node.js/pnpm Dockerfile that uses `--frozen-lockfile`.

**Example:**
```dockerfile
# Copy package files (both json and lockfile for layer caching)
COPY console/package.json ./console/
COPY console/pnpm-lock.yaml ./console/

# Install dependencies (cached layer - only rebuilds if package.json or lockfile changes)
RUN corepack enable && corepack prepare pnpm@9 --activate && pnpm install --frozen-lockfile
```

Source: [CITED: pnpm Docker best practices]

### Anti-Patterns to Avoid

- **Anti-pattern: Using `paths:` trigger + `dorny/paths-filter` in same workflow.** The native `paths:` trigger prevents the entire workflow from running. Use `dorny/paths-filter` alone inside a workflow that triggers on all pushes/PRs, and let the action handle gating at the job level.
- **Anti-pattern: Docker COPY without lockfile before install.** Without the lockfile present, `--frozen-lockfile` fails. Always copy lockfile alongside package.json.
- **Anti-pattern: Hardcoding version in Dockerfile COPY.** `COPY target/ulticode-backend-1.0.0.jar` breaks on every version bump. Use `<finalName>` for a version-independent name.
- **Anti-pattern: Using Testcontainers on GitHub-hosted runners.** Docker-in-Docker is not available. Use GitHub Actions `services:` containers instead. Note: one integration test file (`SubmissionServiceImplIT.java`) uses Testcontainers — it will need to be skipped or reconfigured for CI.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Path-based job gating | Custom git diff logic | `dorny/paths-filter@v4` | Handles PR detection, merge-base finding, merge_group events, and file listing out of the box |
| Maven dependency caching | Custom `.m2` cache mount | `actions/setup-java` with `cache: 'maven'` | Built-in, well-tested, handles key hashing automatically |
| pnpm store caching | Custom cache mount | `actions/setup-node` with `cache: 'pnpm'` | Integrated with pnpm/action-setup, supports per-lockfile isolation |
| Docker layer caching | Custom cache logic | `docker/build-push-action` with `cache-from: type=gha` | Uses GitHub Actions cache backend, `mode=max` caches all layers |
| Spring Boot executable JAR naming | Script to find JAR file | Maven `<finalName>` in pom.xml | Standard Maven feature, zero custom code |

**Key insight:** All five problems in this phase have established GitHub Actions / Maven solutions. The only "custom" work is the `application-ci.yml` profile, which is a standard Spring profile following existing patterns.

## Common Pitfalls

### Pitfall 1: dorny/paths-filter Returns True for First Push
**What goes wrong:** On the first push to a new branch, `dorny/paths-filter` may return `true` for all filters because there's no merge-base to compare against, causing all jobs to run.
**Why it happens:** When there's no common ancestor with the base branch, all files are considered "added."
**How to avoid:** This is actually correct behavior for the first push. Not a problem — just be aware that initial setup will run all jobs.
**Warning signs:** All jobs run on a PR that only changed documentation.

### Pitfall 2: Testcontainers Integration Test Fails in CI
**What goes wrong:** The `SubmissionServiceImplIT.java` test uses `@Testcontainers` annotation with `MySQLContainer` and `GenericContainer`. On GitHub-hosted runners without Docker-in-Docker, these tests fail because they can't start containers.
**Why it happens:** Testcontainers requires Docker daemon access. GitHub-hosted runners don't provide Docker-in-Docker by default (they use Docker via the `docker` command, not the daemon socket).
**How to avoid:** Either (a) exclude this test from CI runs using `@Tag("integration-docker")` and `!integration-docker` in Maven surefire config, or (b) reconfigure the test to use GHA services instead. Option (a) is simpler and doesn't require rewriting the test.
**Warning signs:** Backend test job fails with "Cannot connect to Docker daemon" or "Docker not available."

### Pitfall 3: pnpm-lock.yaml COPY Path Mismatch
**What goes wrong:** After adding `COPY console/pnpm-lock.yaml ./console/`, the install command runs in `/app/console/` but `pnpm install --frozen-lockfile` may not find the lockfile.
**Why it happens:** The existing Dockerfile uses `WORKDIR /app/console` after the install step. The COPY must place the lockfile where pnpm expects it relative to the working directory.
**How to avoid:** Verify the COPY destination matches the WORKDIR structure. The existing pattern copies `package.json` to `./console/`, so `pnpm-lock.yaml` should go to the same path.
**Warning signs:** `pnpm install --frozen-lockfile` fails with "ERR_PNPM_NO_LOCKFILE" or lockfile checksum mismatch.

### Pitfall 4: .dockerignore Excludes Files Needed by Multi-Stage Builds
**What goes wrong:** Adding entries to `.dockerignore` accidentally excludes files needed during the Docker build (e.g., migration files, shared code).
**Why it happens:** The project root is the Docker build context for all services. Overly aggressive `.dockerignore` rules can break builds.
**How to avoid:** The existing `.dockerignore` has a comment warning: "Do NOT exclude Dockerfile* or docker-compose*.yml here." Verify new entries don't exclude files referenced by COPY instructions in any Dockerfile.
**Warning signs:** `docker build` fails with "COPY failed: file not found" for files that exist in the repo.

### Pitfall 5: application-ci.yml Profile Properties Not Loaded
**What goes wrong:** Backend tests in CI fail because they still use the default `application.yml` properties (localhost:23306) instead of CI-specific ones.
**Why it happens:** The Maven test command must explicitly activate the CI profile: `-Dspring.profiles.active=ci`. If the profile isn't activated, Spring falls back to the default profile.
**How to avoid:** Always pass `-Dspring.profiles.active=ci` in the CI test command. The existing ci-backend.yml currently passes env vars directly (DB_HOST, DB_PORT, etc.) which override Spring properties — the CI profile replaces this approach.
**Warning signs:** Tests fail with connection refused to wrong host/port.

## Code Examples

Verified patterns from existing codebase and official sources:

### dorny/paths-filter Changes Job (verified from official docs)
```yaml
# Source: https://github.com/dorny/paths-filter - job-level conditional execution
jobs:
  changes:
    runs-on: ubuntu-latest
    permissions:
      pull-requests: read
    outputs:
      backend: ${{ steps.filter.outputs.backend }}
      console: ${{ steps.filter.outputs.console }}
      management: ${{ steps.filter.outputs.management }}
      docker: ${{ steps.filter.outputs.docker }}
    steps:
      - uses: dorny/paths-filter@v4
        id: filter
        with:
          filters: |
            backend:
              - 'backend-spring/**'
              - 'db-manager/migrations/**'
            console:
              - 'console/**'
              - 'shared/**'
            management:
              - 'management/**'
              - 'shared/**'
            docker:
              - '**/Dockerfile'
              - 'docker-compose*.yml'
              - '.dockerignore'
              - '**/nginx.conf'

  backend:
    needs: changes
    if: needs.changes.outputs.backend == 'true'
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:9.1
        env:
          MYSQL_ROOT_PASSWORD: root
          MYSQL_DATABASE: ulticode_test
          MYSQL_USER: ulticode
          MYSQL_PASSWORD: ulticode
        ports:
          - 23306:3306
        options: >-
          --health-cmd="mysqladmin ping -h localhost -u root -proot"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=5
      redis:
        image: redis:7-alpine
        ports:
          - 26379:6379
        options: >-
          --health-cmd="redis-cli ping"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'maven'
      - run: chmod +x backend-spring/mvnw
      - run: cd backend-spring && ./mvnw test -Dspring.profiles.active=ci -B
```

### Backend Dockerfile Fix (verified from existing Dockerfile)
```dockerfile
# Source: existing backend-spring/Dockerfile, fixed version
# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app
RUN apk add --no-cache curl

# BEFORE (broken): COPY --from=builder /app/target/ulticode-backend-0.0.1-SNAPSHOT.jar ./app.jar
# AFTER (fixed): predictable name via <finalName>app</finalName> in pom.xml
COPY --from=builder /app/target/app.jar ./app.jar
```

### Frontend Dockerfile Fix (verified from existing Dockerfile)
```dockerfile
# Source: existing console/Dockerfile, fixed version
# Build stage
FROM node:22-alpine AS builder

WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9 --activate

# BEFORE (broken): only copies package.json
# COPY console/package.json ./console/

# AFTER (fixed): copies both package.json AND pnpm-lock.yaml
COPY console/package.json ./console/
COPY console/pnpm-lock.yaml ./console/

RUN corepack enable && corepack prepare pnpm@9 --activate && pnpm install --frozen-lockfile

COPY console ./console
WORKDIR /app/console
RUN pnpm build
```

### application-ci.yml (based on existing application-example.yml)
```yaml
# Source: based on backend-spring/src/main/resources/application-example.yml structure
# Port values match existing GHA services config in ci-backend.yml
server:
  port: 0  # Random port for tests

spring:
  datasource:
    url: jdbc:mysql://localhost:23306/ulticode_test?useUnicode=true&characterEncoding=utf-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
    username: ulticode
    password: ulticode

  data:
    redis:
      host: localhost
      port: 26379
      password: ""

  jpa:
    hibernate:
      ddl-auto: none

  flyway:
    enabled: false  # db-manager handles migrations separately

jwt:
  secret: test-jwt-secret-key-for-ci-minimum-32-characters-long

recommendation:
  enabled: false  # No Nacos in CI

dubbo:
  consumer:
    check: false
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Native GitHub `paths:` triggers | `dorny/paths-filter@v4` for job-level gating | ~2023-2024 | Single workflow can gate multiple jobs independently |
| `dorny/paths-filter@v2/v3` | `dorny/paths-filter@v4` (Node 24 runtime) | 2025-2026 | v4 requires Node 24; major version bump with breaking change |
| Hardcoded JAR version in Dockerfile | Maven `<finalName>` | Long-established pattern | Eliminates version mismatch bugs entirely |
| Testcontainers for CI DB | GitHub Actions `services:` containers | ~2020-2021 | Avoids Docker-in-Docker requirement on hosted runners |

**Deprecated/outdated:**
- `dorny/paths-filter@v3`: Still works but uses older Node runtime. v4 is the current release.
- Testcontainers on GitHub-hosted runners: Not supported without Docker-in-Docker. Use `services:` instead.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `spring.flyway.enabled` can be set to `false` in CI profile since db-manager handles migrations separately (Flyway is NOT a dependency in pom.xml) | application-ci.yml Pattern | LOW -- verified by grepping pom.xml: no Flyway dependency found. Removing the flyway config block avoids confusion. |
| A2 | The `SubmissionServiceImplIT.java` Testcontainers test will fail on GitHub-hosted runners without Docker-in-Docker | Pitfall 2 | MEDIUM -- need to verify. If GHA runners support Docker socket access, Testcontainers may work. However, the safer approach is to exclude this test from CI and run it only in local development. |
| A3 | `dorny/paths-filter@v4` works with `pull_request` event on this repository (requires `pull-requests: read` permission) | CI-01 | LOW -- standard GitHub Actions permission, already used in many public repos. |
| A4 | No `spring.jpa.hibernate.ddl-auto` configuration exists in current profiles (no JPA/Hibernate in use -- project uses MyBatis-Plus) | application-ci.yml Pattern | LOW -- verified from application.yml: no JPA config. The CI profile should NOT include JPA settings. MyBatis-Plus handles its own schema. |

## Open Questions

1. **SubmissionServiceImplIT.java Testcontainers test in CI**
   - What we know: This integration test uses `@Testcontainers` with MySQL and Redis containers. It's the only test file using Testcontainers.
   - What's unclear: Whether GitHub-hosted runners provide Docker socket access that Testcontainers can use (they do provide Docker CLI, but Testcontainers needs the daemon socket).
   - Recommendation: Tag the test with `@Tag("integration-docker")` and exclude it from CI using Maven surefire configuration: `<excludedGroups>integration-docker</excludedGroups>` in the CI profile. This avoids CI failures without deleting the test.

2. **Flyway configuration in CI profile**
   - What we know: Flyway is NOT a dependency in the backend pom.xml. Migrations are managed by the separate `db-manager` Python tool.
   - What's unclear: Whether the CI profile needs any database schema initialization for tests to pass.
   - Recommendation: The existing CI backend test job (ci-backend.yml) does NOT run Flyway migrations before tests. Tests should either use `@Sql` annotations for schema setup or be unit tests that don't need a real schema. Don't add Flyway to the CI profile.

3. **db-manager migration validation in unified ci.yml**
   - What we know: The existing `ci-backend.yml` has a `migrate-validate` job that installs db-manager, installs Flyway CLI, and runs migrations against a MySQL service container.
   - What's unclear: Whether this job should be included in the unified `ci.yml` or remain separate.
   - Recommendation: Include it in the unified `ci.yml` gated by the `backend` path filter. It's a backend-related validation step.

## Environment Availability

> This phase modifies GitHub Actions workflow files and Dockerfiles. The actual CI execution happens on GitHub-hosted runners, not locally. Local environment only needed for verifying Docker builds.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker (local) | Docker build verification | NEED CHECK | -- | Skip local Docker verification; CI validates builds |
| Maven / JDK 17 | Backend build | NEED CHECK | -- | CI uses setup-java action |
| Node.js 22 + pnpm 9 | Frontend build | NEED CHECK | -- | CI uses setup-node + pnpm/action-setup |
| GitHub Actions runner | CI execution | N/A (remote) | ubuntu-latest | -- |

**Note:** Local environment checks are not blocking because all CI runs on GitHub-hosted runners. The planner should include a local `docker build` verification step as optional validation.

## Security Domain

> Phase 9 modifies CI workflows and Dockerfiles. No new security-critical code is introduced. The existing security configurations (JWT, CSRF, CSP) are preserved as-is.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No change | Existing JWT + CSRF configuration preserved |
| V3 Session Management | No change | Existing session management preserved |
| V4 Access Control | No change | Existing access control preserved |
| V5 Input Validation | No change | Existing validation preserved |
| V6 Cryptography | No change | Existing JWT secret handling preserved |

### Security Considerations for CI

| Pattern | Risk | Standard Mitigation |
|---------|------|---------------------|
| JWT secret in CI profile | Test secret in code | Use a clearly-labeled test-only secret; never reuse production secrets |
| GITHUB_TOKEN permissions | Excessive permissions | Use minimal `permissions:` blocks (e.g., `contents: read` for CI, `packages: write` only for push) |
| .dockerignore coverage | Secrets leaked into Docker context | Add `.claude/`, `.planning/`, `.env*` to .dockerignore (`.env*` already excluded) |
| Secrets mapping document | Documents secret names | Keep as internal documentation; do not commit actual secret values |

## Sources

### Primary (HIGH confidence)
- [dorny/paths-filter GitHub repository](https://github.com/dorny/paths-filter) -- Full documentation for v4, including job-level gating pattern, filter syntax, outputs, and examples
- Existing `ci-backend.yml` (208 lines) -- Verified GHA services config (MySQL 9.1, Redis 7-alpine, health checks, port mappings)
- Existing `ci-frontend.yml` (198 lines) -- Verified pnpm/action-setup + setup-node caching pattern, matrix strategy
- `backend-spring/pom.xml` -- Verified `<version>1.0.0</version>`, no `<finalName>` in `<build>` section (lines 203-241)
- `backend-spring/Dockerfile` -- Verified JAR name mismatch: line 27 references `ulticode-backend-0.0.1-SNAPSHOT.jar`
- `console/Dockerfile`, `management/Dockerfile` -- Verified missing `pnpm-lock.yaml` COPY before install
- `application-example.yml`, `application-dev.yml`, `application-prod.yml`, `application.yml` -- Verified Spring profile structure, property names, env var patterns
- `console/nginx.conf`, `management/nginx.conf` -- Verified CSP `connect-src 'self' ${API_ORIGIN:-}` pattern
- `.dockerignore` -- Verified existing 66 lines, identified missing entries

### Secondary (MEDIUM confidence)
- [Spring Boot Maven Plugin - Repackage Name](https://docs.spring.io/spring-boot/docs/2.1.13.RELEASE/maven-plugin/examples/repackage-name.html) -- Confirmed `<finalName>` pattern for predictable JAR output
- [Stack Overflow: Spring Boot control target JAR file name](https://stackoverflow.com/questions/37698473) -- Community-validated `<finalName>` approach
- `.env` root file -- Verified all env var names and values (100 lines) for secrets mapping document
- `ecosystem.config.cjs` -- Verified PM2 service configuration and env var references
- `docker-compose.yml`, `docker-compose.prod.yml` -- Verified Docker Compose service definitions and env var usage

### Tertiary (LOW confidence)
- [OneUptime: Monorepo Path Filters in GitHub Actions](https://oneuptime.com/blog/post/2025-12-20-monorepo-path-filters-github-actions/view) -- General monorepo CI patterns (December 2025)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All actions and versions verified from existing workflow files and official documentation
- Architecture: HIGH - Existing CI infrastructure is mature; this phase consolidates rather than builds new
- Pitfalls: HIGH - Identified from codebase analysis (Testcontainers test, JAR name mismatch, lockfile COPY) and verified against existing files
- Security: HIGH - No new security-critical code; existing configurations preserved

**Research date:** 2026-04-18
**Valid until:** 60 days (stable domain - GitHub Actions and Spring Boot patterns change slowly)
