# Phase 10: CD Pipeline - Research

**Researched:** 2026-04-18
**Domain:** GitHub Actions CD, Docker image publishing to GHCR, SSH-based deployment with Docker Compose
**Confidence:** HIGH

## Summary

This phase creates the continuous deployment pipeline: a `docker-publish.yml` workflow that automatically builds and pushes Docker images to GHCR on every merge to `main`, and updates the existing `cd-deploy.yml` to support ordered restart with health check waits. The existing infrastructure is very mature -- `cd-deploy.yml` already has SSH deploy, GHCR login, health checks, migration support, and environment selection. `docker-compose.prod.yml` already references GHCR images with `${IMAGE_TAG:-latest}` and has `depends_on` with `condition: service_healthy` for ordered startup. The main work is: (a) creating `docker-publish.yml` by adapting ci.yml's `docker-verify` job pattern, (b) enhancing cd-deploy.yml's health check step for ordered verification (backend first, fail fast), and (c) verifying docker-compose.prod.yml correctness.

The phase has 3 plans: 10-01 (docker-publish.yml with GHCR push and tagging), 10-02 (docker-compose.prod.yml verification/update), and 10-03 (deploy.yml with ordered restart). Plan 10-01 is the largest effort; Plans 10-02 and 10-03 are primarily verification and enhancement of existing infrastructure.

**Primary recommendation:** Adapt ci.yml's `docker-verify` job into a new `docker-publish.yml` workflow (change `push: false` to `push: true`, add GHCR login and `docker/metadata-action` for tagging). Enhance cd-deploy.yml's health check step to verify backend first with fail-fast behavior. Verify docker-compose.prod.yml -- it likely already satisfies CD-05.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Create a separate `docker-publish.yml` workflow for Docker image build and push. CD-01 explicitly calls for this file. Keep ci.yml focused on CI validation and docker-publish.yml focused on CD (build, tag, push).
- **D-02:** docker-publish.yml triggers on `push` to `main` branch only (not on PRs). Builds all 3 service images using the same Docker build patterns established in ci.yml's docker-verify job (buildx + GHA cache).
- **D-03:** Keep existing `cd-deploy.yml` as the deployment workflow. Rename or update it to handle the ordered restart requirement (CD-04). The existing workflow already has SSH deploy, GHCR login, health checks, and migration support.
- **D-04:** Automatic build+push on merge to main, manual deploy trigger via cd-deploy.yml workflow_dispatch. Images are always built and available in GHCR, but deployment requires human confirmation.
- **D-05:** Use GitHub Environments with protection rules for production deploy (optional future enhancement). Not required for Phase 10.
- **D-06:** Tag each Docker image with two tags: (1) git SHA short hash, and (2) `latest`. Use `docker/metadata-action` to generate tags automatically.
- **D-07:** Image naming convention: `ghcr.io/{owner}/{repo}/backend`, `ghcr.io/{owner}/{repo}/console`, `ghcr.io/{owner}/{repo}/management`. Existing docker-compose.prod.yml already uses `${GHCR_REGISTRY:-ghcr.io/davidhlp/ulticode-public-next}`.
- **D-08:** Reuse the exact same build configuration from ci.yml's docker-verify job: `docker/setup-buildx-action@v3` + `docker/build-push-action@v6` with `cache-from: type=gha, cache-to: type=gha,mode=max`. Only difference is `push: true`.
- **D-09:** Build all 3 images in a single job using a matrix strategy (same as docker-verify).
- **D-10:** Leverage docker-compose.prod.yml's existing `depends_on` with `condition: service_healthy` for ordered restart.
- **D-11:** Add explicit health check verification AFTER `docker compose up -d` in the deploy workflow. Check backend FIRST and fail fast if unhealthy before checking frontends.
- **D-12:** docker-compose.prod.yml already exists and already references GHCR images with `${GHCR_REGISTRY:-ghcr.io/davidhlp/ulticode-public-next}` and `${IMAGE_TAG:-latest}`. Plan 10-02 is an UPDATE, not a CREATE.
- **D-13:** Ensure the deploy workflow sets IMAGE_TAG environment variable before running `docker compose pull`. The cd-deploy.yml already has `IMAGE_TAG: ${{ github.event.inputs.image_tag || 'latest' }}` in the env block.
- **D-14:** Deployment requires these GitHub Secrets (already configured): `DEPLOY_SSH_KEY`, `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_PORT`, `DEPLOY_PATH`, `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `GITHUB_TOKEN` (automatic). No new secrets needed.
- **D-15:** docker-publish.yml needs `packages: write` permission for GHCR push. Add `permissions: contents: read, packages: write` at the workflow level.

### Claude's Discretion
- Exact matrix configuration for docker-publish.yml build job
- Error handling and rollback behavior in deploy workflow
- Health check timeout values and retry intervals
- Migration step integration with deploy workflow

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CD-01 | docker-publish.yml workflow builds and pushes Docker images to GHCR on merge to main | Can be ~80% adapted from ci.yml docker-verify job (lines 344-373); add GHCR login, metadata-action for tags, change push to true |
| CD-02 | Docker images tagged with git SHA short hash and "latest" for traceability | `docker/metadata-action` with `type=sha` produces `sha-abc1234` tag; `type=raw,value=latest` adds latest tag |
| CD-03 | deploy.yml workflow deploys to VPS via SSH with docker compose pull && up -d | Existing cd-deploy.yml already has this pattern (lines 72-82); just needs ordered health check enhancement |
| CD-04 | Deployment uses ordered restart with health check waits (backend starts first, then frontends) | docker-compose.prod.yml already has `depends_on` with `condition: service_healthy` (lines 75-81, 102-104, 129-131); deploy health check needs backend-first ordering |
| CD-05 | docker-compose.prod.yml created/updated to reference GHCR images with IMAGE_TAG variable | File already exists with `${GHCR_REGISTRY:-ghcr.io/davidhlp/ulticode-public-next}/service:${IMAGE_TAG:-latest}` pattern; needs verification only |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Docker image build and push to GHCR | CI/CD (GitHub Actions) | -- | Build infrastructure is a CI/CD concern; images are published to a container registry |
| Image tagging (SHA + latest) | CI/CD (GitHub Actions) | -- | Tags are generated at build time from Git metadata |
| SSH deploy to VPS | CI/CD (GitHub Actions) | -- | Deployment orchestration runs in GHA, connects to VPS via SSH |
| Docker Compose ordered restart | Docker / Runtime | CI/CD (GitHub Actions) | Docker Compose handles the ordering; GHA triggers it and verifies health |
| GHCR image references in compose | Docker / Runtime | -- | docker-compose.prod.yml defines which images the VPS runs |
| Health check verification | CI/CD (GitHub Actions) | Docker / Runtime | GHA SSH step verifies services are healthy after restart |
| Database migration during deploy | CI/CD (GitHub Actions) | Database | Flyway runs as a Docker container on the VPS via SSH |

## Standard Stack

### Core

| Action | Version | Purpose | Why Standard |
|--------|---------|---------|--------------|
| `docker/setup-buildx-action` | v3 | Docker Buildx setup | Required for GHA cache backend; matches ci.yml pattern [VERIFIED: ci.yml line 364] |
| `docker/build-push-action` | v6 | Docker build with push and GHA caching | `cache-from: type=gha, cache-to: type=gha,mode=max`; matches ci.yml [VERIFIED: ci.yml line 367-373] |
| `docker/login-action` | v3 | GHCR authentication | Already used in cd-deploy.yml for remote VPS GHCR login [VERIFIED: cd-deploy.yml line 77] |
| `docker/metadata-action` | v5 | Generate Docker image tags (SHA + latest) | Standard action for tag generation; produces `sha-abc1234` and `latest` tags [CITED: github.com/docker/metadata-action] |
| `actions/checkout` | v4 | Checkout repository | Standard GitHub action; matches ci.yml [VERIFIED: ci.yml line 34] |

### Supporting

| Action | Version | Purpose | When to Use |
|--------|---------|---------|-------------|
| None additional | -- | -- | All needed actions are already in use in existing workflows |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `docker/metadata-action` | Manual `git rev-parse --short HEAD` in shell step | metadata-action is more maintainable and supports multiple tag types out of the box |
| `docker/login-action@v3` | `echo $TOKEN | docker login` shell step | login-action is cleaner and handles registry URL formatting |
| Matrix strategy for 3 images | Separate jobs per image | Matrix is simpler and allows sharing the same checkout/cache steps; images are independent |

**Installation:** No new packages needed. All actions are GitHub Actions used in workflow YAML files.

**Version verification:** All GitHub Actions versions verified from existing workflow files. Latest available versions checked via GitHub API:
- `docker/build-push-action`: v7.1.0 available (ci.yml uses v6 -- stick with v6 for consistency with ci.yml, D-08)
- `docker/setup-buildx-action`: v4.0.0 available (ci.yml uses v3 -- stick with v3 for consistency)
- `docker/login-action`: v4.1.0 available (cd-deploy.yml uses v3 -- stick with v3 for consistency)
- `docker/metadata-action`: v6.0.0 available (Phase 9 research references v5 -- use v5 for stability)
- `actions/checkout`: v6.0.2 available (ci.yml uses v4 -- stick with v4 for consistency)

**Decision: Match existing workflow versions (D-08)** -- do NOT upgrade action versions in this phase. Upgrades should happen in Phase 11 (Dependabot/Hardening) to avoid mixing version bumps with feature changes.

## Architecture Patterns

### System Architecture Diagram

```
Push to main branch
        |
        v
+-------------------+     +--------------------+
|     ci.yml        |     | docker-publish.yml |
| (existing CI)     |     | (NEW - Phase 10)   |
|                   |     |                    |
| lint              |     | Login to GHCR      |
| type-check        |     | Build 3 images     |
| test              |     | (matrix strategy)  |
| docker-verify     |     | Tag: sha + latest  |
|                   |     | Push to GHCR       |
+-------------------+     +---------+----------+
     (parallel)                    |
                                   | Images available in GHCR
                                   v
                         +-------------------+
                         |  cd-deploy.yml    |
                         |  (manual trigger) |
                         |                   |
                         | workflow_dispatch |
                         | with inputs:      |
                         |  - environment    |
                         |  - services       |
                         |  - image_tag      |
                         |  - skip_migrations|
                         +--------+----------+
                                  |
                     SSH to VPS   |
                                  v
                    +-----------------------------+
                    |       VPS Deployment        |
                    |                             |
                    | 1. GHCR docker login        |
                    | 2. (optional) Flyway migrate|
                    | 3. docker compose pull      |
                    |    (uses IMAGE_TAG env var) |
                    | 4. docker compose up -d     |
                    |    (respects depends_on     |
                    |     chain automatically)    |
                    | 5. Health check verify:     |
                    |    - Backend (fail fast)    |
                    |    - Console                |
                    |    - Management             |
                    +-----------------------------+
```

### Recommended Project Structure

```
.github/
  workflows/
    ci.yml                      # Existing CI (Phase 9, read-only reference)
    docker-publish.yml          # NEW: Build and push to GHCR on push to main
    cd-deploy.yml               # UPDATE: Add ordered health check (CD-04)
    ci-recommendation.yml       # Unchanged (out of scope)

docker-compose.prod.yml         # VERIFY/UPDATE: GHCR refs + IMAGE_TAG (CD-05)
docker-compose.yml              # Unchanged (base definitions)
```

### Pattern 1: docker-publish.yml (NEW)

**What:** A GitHub Actions workflow that builds and pushes Docker images to GHCR on every push to `main`.

**When to use:** Every merge to `main` automatically triggers image build and push.

**Example:**
```yaml
# Source: Adapted from ci.yml docker-verify job (lines 344-373)
# Key changes: add GHCR login, metadata-action for tags, push: true

name: Docker Publish

on:
  push:
    branches: [main]

permissions:
  contents: read
  packages: write

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-push:
    name: Build & Push (${{ matrix.service.name }})
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        service:
          - name: backend
            dockerfile: ./backend-spring/Dockerfile
          - name: console
            dockerfile: ./console/Dockerfile
          - name: management
            dockerfile: ./management/Dockerfile
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata (tags, labels)
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/${{ matrix.service.name }}
          tags: |
            type=sha
            type=raw,value=latest

      - name: Build and push Docker image
        uses: docker/build-push-action@v6
        with:
          context: .
          file: ${{ matrix.service.dockerfile }}
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### Pattern 2: Ordered Health Check in Deploy Workflow

**What:** The deploy workflow's health check step verifies services in dependency order: backend first (fail fast), then frontends.

**When to use:** After `docker compose up -d` completes, to verify all services started correctly.

**Example:**
```yaml
# Source: Enhanced from cd-deploy.yml health check step (lines 84-104)
# Key change: check backend FIRST with fail-fast, then check frontends

- name: Health check - backend (fail fast)
  run: |
    SSH_CMD="ssh -i ~/.ssh/deploy_key -o StrictHostKeyChecking=no ${{ secrets.DEPLOY_USER }}@${{ secrets.DEPLOY_HOST }} -p ${{ secrets.DEPLOY_PORT || 22 }}"
    echo "Checking backend..."
    for i in $(seq 1 15); do
      if $SSH_CMD "curl -sf http://localhost:9001/actuator/health > /dev/null 2>&1"; then
        echo "  Backend is healthy"
        break
      fi
      if [ "$i" -eq 15 ]; then
        echo "::error::Backend failed health check after 75 seconds"
        exit 1
      fi
      echo "  Waiting for backend... ($i/15)"
      sleep 5
    done

- name: Health check - frontends
  run: |
    SSH_CMD="ssh -i ~/.ssh/deploy_key -o StrictHostKeyChecking=no ${{ secrets.DEPLOY_USER }}@${{ secrets.DEPLOY_HOST }} -p ${{ secrets.DEPLOY_PORT || 22 }}"
    SERVICES=("console:8080" "management:8080")
    for svc in "${SERVICES[@]}"; do
      NAME="${svc%%:*}"
      ENDPOINT="${svc#*:}"
      echo "Checking $NAME..."
      for i in $(seq 1 15); do
        if $SSH_CMD "curl -sf http://localhost:$ENDPOINT > /dev/null 2>&1"; then
          echo "  $NAME is healthy"
          break
        fi
        if [ "$i" -eq 15 ]; then
          echo "::error::$NAME failed health check after 75 seconds"
          exit 1
        fi
        echo "  Waiting for $NAME... ($i/15)"
        sleep 5
      done
    done
```

### Pattern 3: IMAGE_TAG Injection on Remote VPS

**What:** The deploy workflow must export `IMAGE_TAG` in the remote shell session before running `docker compose pull`.

**When to use:** Every deploy step that runs `docker compose` on the remote VPS.

**Example:**
```bash
# Source: Adapted from cd-deploy.yml pull step (lines 80-82)
# IMAGE_TAG must be exported in the same SSH session as docker compose commands

SSH_CMD="ssh -i ~/.ssh/deploy_key -o StrictHostKeyChecking=no $USER@$HOST -p $PORT"

# Login to GHCR on remote
$SSH_CMD "echo $GITHUB_TOKEN | docker login ghcr.io -u $ACTOR --password-stdin"

# Pull and deploy with IMAGE_TAG
$SSH_CMD "cd $DEPLOY_PATH && \
          export IMAGE_TAG=${{ github.event.inputs.image_tag || 'latest' }} && \
          docker compose -f docker-compose.yml -f docker-compose.prod.yml pull && \
          docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --remove-orphans"
```

**Critical detail:** `export IMAGE_TAG=...` must be in the same shell invocation as the `docker compose` commands. Docker Compose variable interpolation reads environment variables from the shell that invokes `docker compose`, NOT from a `.env` file in the project directory (unless explicitly named `.env`).

### Anti-Patterns to Avoid

- **Building on PRs and pushing to GHCR:** Docker-publish.yml triggers only on `push` to `main`. PRs should only build-verify (ci.yml docker-verify job). Pushing PR images to GHCR would waste storage and create confusing image tags.
- **Using `GITHUB_TOKEN` in `docker compose pull` on VPS:** The VPS needs its own GHCR authentication via `docker login` with a token/PAT. The GHA `GITHUB_TOKEN` is ephemeral and only valid within the GHA runner. The existing cd-deploy.yml handles this correctly (line 77).
- **Separate `docker compose pull` and `docker compose up -d` without IMAGE_TAG export:** If IMAGE_TAG is not exported in the same shell session, `docker compose pull` will use the default (`latest`), which may not match the intended deployment tag.
- **Checking health endpoints in the wrong order:** Always verify backend before frontends. Frontends depend on backend for API calls; checking frontends first would produce misleading results (frontend health check succeeds but backend may be down).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Docker image tagging logic | Shell script with `git rev-parse --short HEAD` | `docker/metadata-action@v5` | Handles multiple tag types, labels, and tag formatting out of the box; produces consistent `sha-abc1234` format |
| GHCR authentication | Shell `curl` with token | `docker/login-action@v3` | Handles registry URL, username/password formatting, and error handling |
| Docker Buildx setup | Manual `docker buildx create` | `docker/setup-buildx-action@v3` | Creates and uses buildx builder with correct driver for GHA |
| Ordered service restart | Manual SSH commands: stop frontend, restart backend, start frontend | `docker compose up -d` with `depends_on` chain | Docker Compose already handles dependency ordering via `depends_on` with `condition: service_healthy` |

**Key insight:** The ordered restart problem is already solved by docker-compose.prod.yml's `depends_on` chain. The deploy workflow only needs to (1) set IMAGE_TAG, (2) run `docker compose pull`, (3) run `docker compose up -d`, and (4) verify health checks in order. There is no need to manually stop/start services in sequence.

## Common Pitfalls

### Pitfall 1: IMAGE_TAG not reaching docker compose on remote VPS
**What goes wrong:** `docker compose pull` on the VPS uses `latest` instead of the intended tag because IMAGE_TAG is not set in the remote shell environment.
**Why it happens:** Docker Compose variable interpolation reads from the invoking shell's environment variables and `.env` files, not from GitHub Actions env blocks that exist only on the runner.
**How to avoid:** Always `export IMAGE_TAG=xxx` in the same SSH command string as the `docker compose` commands (see Pattern 3).
**Warning signs:** Deploy succeeds but pulls `latest` instead of the SHA-tagged image; running `docker compose config` on VPS shows `latest` instead of the intended tag.

### Pitfall 2: Health check port mismatch between Dockerfile and deploy script
**What goes wrong:** Deploy health check fails because it checks the wrong port.
**Why it happens:** Dockerfiles expose port 8080 (nginx inside container) but docker-compose.prod.yml maps container port 8080 to host port 9002 (console) and 9003 (management). Health checks on the VPS must use host ports.
**How to avoid:** The existing cd-deploy.yml checks `console:80` and `management:80` (line 87). These should be `console:9002` and `management:9003` to match docker-compose.prod.yml port mappings. **This is a bug in the existing cd-deploy.yml that should be fixed in Plan 10-03.**
**Warning signs:** Deploy health check fails with connection refused on port 80.

### Pitfall 3: Backend health check using /actuator/health before Spring Boot is ready
**What goes wrong:** Backend health check fails immediately because Spring Boot hasn't started yet.
**Why it happens:** The backend Dockerfile has `start_period: 30s` for its internal healthcheck, but the deploy workflow's health check loop starts immediately after `docker compose up -d`. Docker Compose's `depends_on: condition: service_healthy` handles the ordering, but the deploy workflow's external health check is defense-in-depth and must account for startup time.
**How to avoid:** The existing retry loop (15 iterations x 5s sleep = 75s total) provides adequate time. Backend's internal healthcheck has `start_period: 30s`, so the first 30 seconds will naturally fail. The 75s budget allows for this.
**Warning signs:** Health check fails consistently at iteration 6 (30s mark) then succeeds at iteration 7-8.

### Pitfall 4: Matrix strategy fail-fast cancels other image builds
**What goes wrong:** If one Docker image build fails, the matrix `fail-fast: true` default cancels the other two builds, leaving incomplete deployment.
**Why it happens:** GitHub Actions matrix strategy defaults to `fail-fast: true`.
**How to avoid:** Explicitly set `fail-fast: false` in the matrix strategy. This ensures all 3 images are always built even if one fails, making debugging easier.
**Warning signs:** Only 2 of 3 images appear in GHCR after a push to main.

### Pitfall 5: Docker Compose GHA cache collision between CI and CD
**What goes wrong:** docker-publish.yml's cache overwrites ci.yml's cache or vice versa, causing unexpected build behavior.
**Why it happens:** Both workflows use `type=gha` cache with the same cache keys (derived from Dockerfile content + build context).
**How to avoid:** This is actually NOT a problem. GHA cache is scoped by branch, and both workflows run on `main`. The cache is shared intentionally -- CD reuses CI's cached layers, which is exactly what we want. The cache key includes the full Dockerfile content hash, so it naturally invalidates when Dockerfiles change.
**Warning signs:** None -- this is expected behavior.

## Code Examples

### docker/metadata-action Tag Configuration

```yaml
# Source: [CITED: github.com/docker/metadata-action]
# Produces two tags: sha-abc1234 (7-char short SHA) and latest

- name: Extract metadata
  id: meta
  uses: docker/metadata-action@v5
  with:
    images: ghcr.io/davidhlp/ulticode-public-next/backend
    tags: |
      type=sha              # e.g., sha-860c190 (7-char default)
      type=raw,value=latest  # Always tag as latest
```

### GHCR Login on GHA Runner

```yaml
# Source: [CITED: docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry]
# GITHUB_TOKEN has automatic read/write for GHCR on the same repo

- name: Log in to GHCR
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}
```

### SSH Deploy with IMAGE_TAG Export

```yaml
# Source: Adapted from cd-deploy.yml (lines 72-82)
# IMAGE_TAG must be exported in the remote shell session

- name: Pull and deploy services
  env:
    IMAGE_TAG: ${{ github.event.inputs.image_tag || 'latest' }}
  run: |
    SSH_CMD="ssh -i ~/.ssh/deploy_key -o StrictHostKeyChecking=no \
      ${{ secrets.DEPLOY_USER }}@${{ secrets.DEPLOY_HOST }} -p ${{ secrets.DEPLOY_PORT || 22 }}"

    # Login to GHCR on remote
    $SSH_CMD "echo ${{ secrets.GITHUB_TOKEN }} | docker login ghcr.io -u ${{ github.actor }} --password-stdin"

    # Pull and deploy with IMAGE_TAG
    $SSH_CMD "cd ${{ secrets.DEPLOY_PATH }} && \
              export IMAGE_TAG=${IMAGE_TAG} && \
              docker compose -f docker-compose.yml -f docker-compose.prod.yml pull && \
              docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --remove-orphans"
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `docker push` via shell commands | `docker/build-push-action@v6` with buildx | GHA standard for 2+ years | GHA cache integration, multi-platform builds |
| Manual tag management | `docker/metadata-action` | GHA standard for 2+ years | Declarative tag configuration |
| Separate build and push jobs | Single job with matrix + push | Current best practice | Simpler workflow, fewer artifacts |
| `docker-compose up` ordering via scripts | `depends_on: condition: service_healthy` | Docker Compose v2.0+ | Native ordering, no custom scripts needed |

**Deprecated/outdated:**
- `docker/build-push-action@v2`: Very old, lacks buildx v0.10+ features. Project uses v6.
- `docker/setup-buildx-action@v1`: Old. Project uses v3.
- `type=gha` cache scope: Still current and recommended. No replacement.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | GitHub repo is public (so GITHUB_TOKEN has automatic GHCR push permissions) | Standard Stack | If private, `packages: write` permission in workflow is still sufficient -- D-15 already accounts for this |
| A2 | The existing cd-deploy.yml health check ports (80 for console/management) are a bug -- should be 9002/9003 | Common Pitfalls #2 | If the VPS has port forwarding or a reverse proxy on port 80, port 80 checks would be correct. Need to verify VPS network setup |
| A3 | docker-compose.prod.yml already satisfies CD-05 and only needs verification, not creation | Phase Requirements (CD-05) | If GHCR image paths are wrong or IMAGE_TAG interpolation fails, Plan 10-02 scope increases |

## Open Questions (RESOLVED)

1. **VPS network configuration -- are frontend ports exposed as 80 or 9002/9003?** (RESOLVED: No reverse proxy assumed. Fixed to host ports 9002/9003 in Plan 10-03 health check step.)
   - What we know: cd-deploy.yml health check uses `console:80` and `management:80`. docker-compose.prod.yml maps console to `9002:8080` and management to `9003:8080`.
   - Resolution: Plan 10-03 fixes health check endpoints to use host-mapped ports (9002 for console, 9003 for management) matching docker-compose.prod.yml port mappings.

2. **Should docker-publish.yml add a concurrency group?** (RESOLVED: Concurrency group added in Plan 10-01 with `cancel-in-progress: true`.)
   - What we know: ci.yml has `concurrency: group: ${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: true`. cd-deploy.yml has `concurrency: group: deploy-${{ github.event.inputs.environment }}, cancel-in-progress: false`.
   - Resolution: Plan 10-01 adds `concurrency: group: docker-publish-${{ github.ref }}, cancel-in-progress: true` to docker-publish.yml. Only the latest push to main should produce images.

## Environment Availability

> Step 2.6: SKIPPED (no external dependencies identified beyond what's already configured in GitHub Secrets per D-14)

All deployment dependencies are GitHub Actions (runs on GitHub-hosted runners) and SSH access to the VPS (via configured secrets). No local tools or runtimes are needed to implement this phase.

## Validation Architecture

> SKIPPED: `workflow.nyquist_validation` is explicitly set to false in .planning/config.json.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | -- |
| V3 Session Management | no | -- |
| V4 Access Control | no | -- |
| V5 Input Validation | no | -- |
| V6 Cryptography | yes (minimal) | SSH key for VPS access stored in GitHub Secrets (`DEPLOY_SSH_KEY`); GHCR auth via `GITHUB_TOKEN` (automatic, ephemeral) |
| V7 Error Handling | yes | Deploy workflow uses `if: failure()` step for error notification; health check exits with code 1 on failure |
| V8 Data Protection | yes | `StrictHostKeyChecking=no` in SSH commands (acceptable for ephemeral GHA runners with keyscan); `--remove-orphans` prevents stale containers |

### Known Threat Patterns for GitHub Actions CD

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Secret leakage in logs | Information Disclosure | GitHub Actions automatically masks secrets; avoid `echo ${{ secrets.* }}` in logs |
| SSH key compromise | Tampering | SSH key stored in GitHub Secrets (encrypted at rest); deploy uses key-based auth only |
| Supply chain attack via base images | Tampering | Pin base image versions in Dockerfiles (already done: `eclipse-temurin:17-jdk-alpine`, `node:22-alpine`, `nginx:alpine`) |
| Unauthorized deploy | Elevation of Privilege | Manual workflow_dispatch trigger (D-04); GitHub Environments protection rules available as future enhancement (D-05) |
| Malicious image tag overwrite | Tampering | Only `push` to `main` triggers build; `latest` tag always points to latest main commit |

## Sources

### Primary (HIGH confidence)
- [ci.yml (lines 344-373)](file:///home/davidhlp/project/UltiCode-Public-Next/.github/workflows/ci.yml) -- docker-verify job pattern for docker-publish.yml adaptation
- [cd-deploy.yml](file:///home/davidhlp/project/UltiCode-Public-Next/.github/workflows/cd-deploy.yml) -- Existing deploy workflow with SSH, GHCR login, health checks
- [docker-compose.prod.yml](file:///home/davidhlp/project/UltiCode-Public-Next/docker-compose.prod.yml) -- Production compose with GHCR refs, IMAGE_TAG, depends_on chain
- [docker-compose.yml](file:///home/davidhlp/project/UltiCode-Public-Next/docker-compose.yml) -- Base compose definitions
- [CONTEXT.md decisions D-01 through D-15](file:///home/davidhlp/project/UltiCode-Public-Next/.planning/phases/10-cd-pipeline/10-CONTEXT.md) -- Locked implementation decisions

### Secondary (MEDIUM confidence)
- [Docker Compose startup order docs](https://docs.docker.com/compose/how-tos/startup-order/) -- Confirms `depends_on` with `condition: service_healthy` is the standard pattern for ordered restart
- [Docker Compose variable interpolation docs](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/) -- Confirms `${IMAGE_TAG}` works in `image:` directive when set as shell env var
- [docker/metadata-action](https://github.com/docker/metadata-action) -- Confirms `type=sha` produces `sha-abc1234` tags and `type=raw,value=latest` adds latest tag

### Tertiary (LOW confidence)
- [Docker Compose issue #6187](https://github.com/docker/compose/issues/6187) -- Mentioned potential issues with env vars in image directive; verified via official docs that `${VAR}` interpolation works correctly when VAR is in shell environment

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All actions verified from existing workflow files and official docs
- Architecture: HIGH - Existing infrastructure is mature; patterns copied from working ci.yml and cd-deploy.yml
- Pitfalls: HIGH - Identified by analyzing existing code (found port mismatch bug in cd-deploy.yml health check)

**Research date:** 2026-04-18
**Valid until:** 30 days (GitHub Actions and Docker APIs are stable; action versions verified)
