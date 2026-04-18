# Phase 10: CD Pipeline - Context

**Gathered:** 2026-04-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Every merge to main automatically builds Docker images, pushes them to GHCR, and deploys to the VPS via Docker Compose with ordered service restarts.

**What's IN scope:**
- Create docker-publish.yml workflow for automated Docker image build and push to GHCR on merge to main (CD-01)
- Tag Docker images with git SHA short hash and "latest" for traceability (CD-02)
- Update deploy.yml workflow for SSH deploy to VPS via docker compose pull && up -d (CD-03)
- Implement ordered restart with health check waits (backend first, then frontends) (CD-04)
- Update docker-compose.prod.yml to reference GHCR images with IMAGE_TAG variable (CD-05)

**What's OUT of scope:**
- Dependabot configuration (Phase 11)
- Rollback workflow (Phase 11)
- Deployment notifications to Slack/Discord (v2: MON-01)
- Deployment history log (v2: MON-02)
- Deploy previews for PRs (v2: ADVCI-03)
- Recommendation service Docker image build/push (explicitly out of scope per REQUIREMENTS.md)
- CI pipeline changes (completed in Phase 9)

</domain>

<decisions>
## Implementation Decisions

### CD Workflow Architecture
- **D-01:** Create a separate `docker-publish.yml` workflow for Docker image build and push. CD-01 explicitly calls for this file. Keep ci.yml focused on CI validation (lint, type-check, test) and docker-publish.yml focused on CD (build, tag, push). This separation of concerns means CI and CD can evolve independently and have different trigger conditions.
- **D-02:** docker-publish.yml triggers on `push` to `main` branch only (not on PRs). It builds all 3 service images (backend, console, management) using the same Docker build patterns established in ci.yml's docker-verify job (buildx + GHA cache). This reuses the proven build configuration from Phase 9.
- **D-03:** Keep existing `cd-deploy.yml` as the deployment workflow. Rename or update it to handle the ordered restart requirement (CD-04). The existing workflow already has SSH deploy, GHCR login, health checks, and migration support — it just needs ordered restart logic.

### Deploy Trigger & Gating
- **D-04:** Automatic build+push on merge to main, manual deploy trigger via cd-deploy.yml workflow_dispatch. This is safer for a single VPS deployment — images are always built and available in GHCR, but deployment requires human confirmation. The existing cd-deploy.yml already uses workflow_dispatch with environment selection (staging/production), which supports this pattern.
- **D-05:** Use GitHub Environments with protection rules for production deploy (optional future enhancement). The cd-deploy.yml already references `${{ github.event.inputs.environment }}` with an `environment:` key, so protection rules can be added in GitHub Settings without code changes. This is noted but not required for Phase 10.

### Image Tagging Strategy
- **D-06:** Tag each Docker image with two tags: (1) git SHA short hash (`$(git rev-parse --short HEAD)`) for traceability, and (2) `latest` for convenience. This matches CD-02 exactly. Use `docker/metadata-action` to generate tags automatically — this is the standard pattern and already used in the project's Docker build steps.
- **D-07:** Image naming convention: `ghcr.io/{owner}/{repo}/backend`, `ghcr.io/{owner}/{repo}/console`, `ghcr.io/{owner}/{repo}/management`. The existing docker-compose.prod.yml already uses this pattern with `${GHCR_REGISTRY:-ghcr.io/davidhlp/ulticode-public-next}`.

### Docker Build Pattern
- **D-08:** Reuse the exact same build configuration from ci.yml's docker-verify job: `docker/setup-buildx-action@v3` + `docker/build-push-action@v6` with `cache-from: type=gha, cache-to: type=gha,mode=max`. The only difference is `push: true` instead of `push: false`. This ensures CI and CD build images identically.
- **D-09:** Build all 3 images in a single job using a matrix strategy (same as docker-verify). This is simpler than parallel jobs and the matrix handles the service-specific Dockerfile paths. Images are independent so there's no need for inter-service coordination during build.

### Ordered Restart (CD-04)
- **D-10:** Leverage docker-compose.prod.yml's existing `depends_on` with `condition: service_healthy` for ordered restart. The production compose file already defines: console depends_on backend (healthy), management depends_on backend (healthy). Running `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` naturally starts backend first, waits for health check, then starts frontends.
- **D-11:** Add explicit health check verification AFTER `docker compose up -d` in the deploy workflow. The existing cd-deploy.yml has a health check step that verifies all services via SSH curl. Enhance it to check backend FIRST and fail fast if backend is unhealthy before checking frontends. This provides defense-in-depth beyond Docker Compose's built-in ordering.

### docker-compose.prod.yml Scope (CD-05)
- **D-12:** The file already exists and already references GHCR images with `${GHCR_REGISTRY:-ghcr.io/davidhlp/ulticode-public-next}` and `${IMAGE_TAG:-latest}`. Plan 10-02 is an UPDATE, not a CREATE. Verify the IMAGE_TAG variable is properly supported and add any missing production environment variables.
- **D-13:** Ensure the deploy workflow sets IMAGE_TAG environment variable before running `docker compose pull`. The cd-deploy.yml already has `IMAGE_TAG: ${{ github.event.inputs.image_tag || 'latest' }}` in the env block. Verify this is correctly passed to the remote VPS during SSH deployment.

### Secrets for Deployment
- **D-14:** Deployment requires these GitHub Secrets (already configured for existing cd-deploy.yml): `DEPLOY_SSH_KEY`, `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_PORT`, `DEPLOY_PATH`, `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `GITHUB_TOKEN` (automatic). No new secrets needed for Phase 10.
- **D-15:** docker-publish.yml needs `packages: write` permission for GHCR push. Add `permissions: contents: read, packages: write` at the workflow level.

### Claude's Discretion
- Exact matrix configuration for docker-publish.yml build job
- Error handling and rollback behavior in deploy workflow
- Health check timeout values and retry intervals
- Migration step integration with deploy workflow

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — CD section (CD-01 through CD-05)
- `.planning/ROADMAP.md` §Phase 10 — Phase definition, success criteria, and plan outline

### Existing CD/CI Infrastructure (must read — will be modified)
- `.github/workflows/cd-deploy.yml` — Existing manual deploy workflow (update with ordered restart)
- `.github/workflows/ci.yml` — Unified CI workflow from Phase 9 (reference for Docker build patterns, docker-verify job)
- `.github/workflows/ci-recommendation.yml` — Recommendation service CI (keep as-is)

### Docker Configuration
- `docker-compose.prod.yml` — Production compose with GHCR image refs (update for CD-05)
- `docker-compose.yml` — Base Docker Compose definitions
- `backend-spring/Dockerfile` — Backend Docker build (Phase 9 fixed JAR name to app.jar)
- `console/Dockerfile` — Console Docker build (Phase 9 added pnpm-lock.yaml COPY)
- `management/Dockerfile` — Management Docker build (Phase 9 added pnpm-lock.yaml COPY)

### Documentation
- `docs/secrets-mapping.md` — Secrets and configuration cross-reference (created in Phase 9)
- `CLAUDE.md` — Project overview, service architecture, port reference
- `.planning/phases/09-foundation-ci/09-CONTEXT.md` — Phase 9 decisions (D-01 through D-15)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **ci.yml docker-verify job** (lines 340-373): Already builds all 3 Docker images with buildx + GHA cache. docker-publish.yml can copy this pattern and change `push: false` to `push: true`.
- **cd-deploy.yml**: Already has SSH deploy, GHCR login, health checks, migration support, and environment selection. The ordered restart (CD-04) is the main gap.
- **docker-compose.prod.yml**: Already references GHCR images with `${GHCR_REGISTRY}/service:${IMAGE_TAG}`. Already has `depends_on` with `condition: service_healthy` for ordered startup. Already has health checks for all services.

### Established Patterns
- **Docker build matrix**: ci.yml uses `matrix.service` with name + dockerfile pairs. docker-publish.yml should use the same pattern for consistency.
- **GHCR authentication**: Existing workflows use `docker/login-action@v3` with `registry: ghcr.io` and `password: ${{ secrets.GITHUB_TOKEN }}`.
- **SSH deploy**: cd-deploy.yml uses key-based SSH with `DEPLOY_SSH_KEY` secret, `ssh-keyscan` for known hosts, and configurable port.

### Integration Points
- **ci.yml triggers**: ci.yml already runs on push to main. docker-publish.yml will also trigger on push to main. These run in parallel — ci.yml validates, docker-publish.yml builds and pushes. No dependency between them.
- **docker-compose.prod.yml IMAGE_TAG**: The deploy workflow sets `IMAGE_TAG` env var. The SSH command must export this on the remote VPS before running `docker compose pull`.
- **Health check endpoints**: backend uses `http://localhost:9001/actuator/health`, console/management use `http://localhost:8080/` via wget.

### Key Observations
1. The existing CD infrastructure is VERY mature — cd-deploy.yml already handles most of CD-03 and CD-04 requirements. The main gaps are: (a) no automatic docker-publish workflow, (b) no explicit ordered restart in the deploy step.
2. docker-compose.prod.yml already satisfies CD-05 — it has GHCR image refs, IMAGE_TAG variable, and depends_on health check ordering. Plan 10-02 should be a verification/update, not a full creation.
3. The `docker-verify` job in ci.yml provides the exact template for docker-publish.yml — same build pattern, just add login + push.
4. The deploy workflow's health check step checks all services in a loop. For ordered restart (CD-04), it should check backend first and fail early if unhealthy, rather than checking all services in a single loop.
5. Recommendation service images are NOT included in CD pipeline (explicitly out of scope). docker-compose.prod.yml has recommend-provider and recommend-web but they use `${RECOMMEND_IMAGE_TAG}` not GHCR refs — this is intentional.

</code_context>

<specifics>
## Specific Ideas

- docker-publish.yml can be ~80% copied from ci.yml's docker-verify job with these changes: add GHCR login step, change `push: false` to `push: true`, add `tags` from metadata-action, remove the `if: needs.changes.outputs.docker == 'true'` condition (CD runs on every main push, not path-filtered).
- The deploy workflow's ordered restart is mostly handled by docker-compose.prod.yml's `depends_on` chain. The deploy command `docker compose up -d` naturally respects this ordering. The health check step should be enhanced to verify backend first (fail fast), then verify frontends.
- For IMAGE_TAG injection on VPS, the SSH command should: (1) `export IMAGE_TAG=xxx`, (2) `docker compose pull`, (3) `docker compose up -d`. The existing cd-deploy.yml uses a single SSH command string — ensure IMAGE_TAG is exported in the same shell session.
- GitHub `GITHUB_TOKEN` has automatic GHCR push permissions for public repos. For private repos, `packages: write` permission is needed in the workflow file.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 10-cd-pipeline*
*Context gathered: 2026-04-18*
