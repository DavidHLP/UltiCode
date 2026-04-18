# Phase 11: Hardening - Context

**Gathered:** 2026-04-18
**Status:** Ready for planning

<domain>
## Phase Boundary

The CI/CD pipeline becomes self-maintaining with automated dependency updates and a manual rollback capability for failed deployments.

**What's IN scope:**
- Configure Dependabot for GitHub Actions version updates and npm/Maven dependency updates (HARD-01)
- Create a rollback workflow that allows manual redeployment of a previous image tag via workflow_dispatch (HARD-02)

**What's OUT of scope:**
- Deployment notifications to Slack/Discord (v2: MON-01)
- Deployment history log (v2: MON-02)
- Concurrency groups for CI runs (v2: ADVCI-01)
- Test result artifacts (v2: ADVCI-02)
- Deploy previews for PRs (v2: ADVCI-03)
- DB migration rollback (too risky for automated workflow — manual only)
- Dependabot auto-merge (manual review enforced)

</domain>

<decisions>
## Implementation Decisions

### Dependabot Configuration (HARD-01)
- **D-01:** Configure Dependabot for all 3 ecosystems: `github-actions`, `npm`, and `maven`. HARD-01 explicitly calls for "GitHub Actions version updates and npm/Maven dependency updates." All three are needed to cover the full stack: Actions workflows use pinned versions (e.g., `actions/checkout@v4`), frontends use npm (console + management), and backend uses Maven.
- **D-02:** Set Dependabot schedule to `weekly` for all ecosystems. Weekly is the standard cadence — frequent enough to catch security patches quickly, but not so frequent that it creates noise. Daily would generate too many PRs for a small team.
- **D-03:** Use Dependabot `groups` to batch updates within each ecosystem. Group npm updates by: production dependencies and development dependencies separately. Group GitHub Actions updates together. Group Maven updates together. This reduces PR volume while keeping review scope manageable.
- **D-04:** No auto-merge — all Dependabot PRs require manual review. The existing CI pipeline (ci.yml) validates every PR automatically, so human review is a safety net for breaking changes that pass CI but break runtime behavior.
- **D-05:** Set `open-pull-requests-limit: 5` per ecosystem to prevent PR flooding. Add standard labels (`dependencies`, `automated`) for filtering. Assign no specific reviewers — the repo's default CODEOWNERS handles that.

### Rollback Workflow (HARD-02)
- **D-06:** Create a separate `cd-rollback.yml` workflow file. The existing cd-deploy.yml is already complex with migration support, health checks, and service selection. A dedicated rollback workflow keeps the concerns separated: cd-deploy.yml for forward deployments, cd-rollback.yml for emergency rollbacks.
- **D-07:** Rollback redeploys a previous Docker image by specifying the git SHA short hash as `IMAGE_TAG`. The docker-publish.yml tags every image with `type=sha` (e.g., `sha-abc1234`), so any previous commit's images are available in GHCR for redeployment.
- **D-08:** Rollback does NOT include database migration rollback. Migrations are forward-only in this project (Flyway). Rolling back Docker images while keeping the DB at a newer schema version is acceptable for minor rollbacks (UI bugs, frontend fixes). For major rollbacks involving schema changes, manual intervention is required.
- **D-09:** Rollback workflow uses `workflow_dispatch` with a required `image_tag` input (the SHA hash to roll back to). It reuses the same SSH deploy + health check pattern from cd-deploy.yml, but skips the migration step entirely. The workflow should confirm the specified tag exists in GHCR before attempting deployment.
- **D-10:** Add `environment: production` with protection rules (optional future enhancement). The rollback workflow should have `concurrency: group: deploy-production, cancel-in-progress: false` to prevent concurrent rollback and deploy operations.

### Claude's Discretion
- Exact Dependabot group naming and configuration details
- Rollback workflow error handling and notification patterns
- Health check timeout values for rollback (can reuse cd-deploy.yml values)
- Whether to add a "verify tag exists" step before rollback deployment

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — Hardening section (HARD-01, HARD-02)
- `.planning/ROADMAP.md` §Phase 11 — Phase definition and success criteria

### Existing CD Infrastructure (must reference)
- `.github/workflows/cd-deploy.yml` — Deploy workflow (reuse SSH + health check pattern for rollback)
- `.github/workflows/docker-publish.yml` — Docker publish workflow (SHA tagging is the rollback mechanism)
- `.github/workflows/ci.yml` — Unified CI workflow (Dependabot PRs will trigger this)

### Docker Configuration
- `docker-compose.prod.yml` — Production compose with GHCR image refs and IMAGE_TAG variable
- `docker-compose.yml` — Base Docker Compose definitions

### Documentation
- `docs/secrets-mapping.md` — Secrets and configuration cross-reference
- `CLAUDE.md` — Project overview, service architecture

### Prior Phase Context
- `.planning/phases/10-cd-pipeline/10-CONTEXT.md` — Phase 10 decisions (CD pipeline, docker-publish, cd-deploy)
- `.planning/phases/09-foundation-ci/09-CONTEXT.md` — Phase 9 decisions (CI workflow structure, Docker patterns)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **cd-deploy.yml SSH + health check pattern**: The rollback workflow can reuse the exact SSH key setup, docker compose pull/up, and ordered health check steps. Only differences: skip migration, use rollback IMAGE_TAG, add tag verification.
- **docker-publish.yml SHA tagging**: Every merge to main produces images tagged with `type=sha` (e.g., `ghcr.io/davidhlp/ulticode-public-next/backend:sha-abc1234`). This is the rollback mechanism — previous SHAs remain in GHCR.
- **cd-deploy.yml `image_tag` input**: Already exists with default `latest`. Rollback just needs to pass a specific SHA hash instead.

### Established Patterns
- **SSH deploy**: cd-deploy.yml uses key-based SSH with `DEPLOY_SSH_KEY` secret, `ssh-keyscan` for known hosts, configurable port. Rollback reuses this exactly.
- **Ordered health checks**: cd-deploy.yml checks backend first (fail fast), then frontends. Rollback should follow same pattern.
- **Workflow concurrency**: Both cd-deploy.yml and docker-publish.yml use concurrency groups. Rollback needs to share the deploy concurrency group to prevent concurrent deployments.

### Integration Points
- **GHCR image availability**: docker-publish.yml pushes on every merge to main. All previous SHA tags remain available in GHCR (images are not overwritten — only `latest` tag moves).
- **docker-compose.prod.yml IMAGE_TAG**: The compose file uses `${IMAGE_TAG:-latest}`. Rollback just sets IMAGE_TAG to a previous SHA hash.
- **CI pipeline triggers**: Dependabot PRs will trigger ci.yml on pull_request. No changes needed to ci.yml — it already handles PR validation.

### Key Observations
1. Rollback is architecturally simple — it's just cd-deploy.yml without migrations and with a specific image tag. The heavy lifting (GHCR images, docker-compose, health checks) is all done.
2. Dependabot config is purely additive — a new `.github/dependabot.yml` file. No existing files need modification (except possibly ci.yml if auto-merge is wanted, but D-04 explicitly rejects that).
3. No new GitHub Secrets needed for either plan — all deployment secrets are already configured from Phase 10.
4. The `ci-recommendation.yml` workflow is out of scope — Dependabot should NOT monitor it (Phase 9 decision D-07 keeps it isolated).

</code_context>

<specifics>
## Specific Ideas

- Dependabot config file path: `.github/dependabot.yml` (standard GitHub location)
- For npm ecosystem, set `directory: /` with separate entries for `console/` and `management/` — or use a single npm entry that covers both (Dependabot detects lockfiles in subdirectories)
- For Maven ecosystem, set `directory: /backend-spring` — this is where `pom.xml` lives
- Rollback workflow should have a `tag-verify` step that checks if the specified SHA tag exists in GHCR before SSH deployment (fails fast with clear error if tag not found)
- Consider adding `rebase-strategy: disabled` to Dependabot config to prevent unnecessary rebases on PRs

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 11-hardening*
*Context gathered: 2026-04-18*
