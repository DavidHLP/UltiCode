# Phase 10: CD Pipeline - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-18
**Phase:** 10-cd-pipeline
**Areas discussed:** CD workflow architecture, Deploy trigger & gating, Ordered restart strategy, docker-compose.prod.yml scope
**Mode:** Auto (non-interactive)

---

## CD Workflow Architecture

| Option | Description | Selected |
|--------|-------------|----------|
| Separate docker-publish.yml | CD-01 explicitly calls for this file; keeps CI and CD concerns separated; ci.yml stays focused on validation | ✓ |
| Extend ci.yml with push job | Add push capability after docker-verify job; single workflow for everything | |
| Reuse existing cd-deploy.yml for both publish and deploy | Single workflow that builds, pushes, and deploys | |

**Auto-selected:** Separate docker-publish.yml (matches CD-01 requirement, clean separation of concerns)

---

## Deploy Trigger & Gating

| Option | Description | Selected |
|--------|-------------|----------|
| Auto build+push, manual deploy | Merge to main triggers image build+push; deploy requires manual workflow_dispatch; safest for single VPS | ✓ |
| Fully automatic end-to-end | Merge to main triggers build+push+deploy; fastest but risky for production | |
| Fully manual | Both build+push and deploy require manual trigger; maximum control but slowest | |

**Auto-selected:** Auto build+push, manual deploy (safety-first for single VPS deployment)

---

## Ordered Restart Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Leverage docker-compose depends_on chain | docker-compose.prod.yml already has backend→frontend health check ordering; `docker compose up -d` respects this naturally | ✓ |
| Explicit sequential SSH commands | Stop frontends → restart backend → health check → restart frontends → health check; more control but reinvents Docker Compose | |
| Blue-green deployment | Two sets of containers; switch traffic; overkill for single VPS | |

**Auto-selected:** Leverage docker-compose depends_on chain (already exists in docker-compose.prod.yml, minimal code change)

---

## docker-compose.prod.yml Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Verify and minimal update | File already has GHCR refs and IMAGE_TAG; verify correctness and add any missing vars | ✓ |
| Full rewrite | Start fresh with cleaner structure; high effort for little gain | |
| Keep as-is | No changes needed; CD-05 already satisfied | |

**Auto-selected:** Verify and minimal update (file is mostly complete, just needs verification and minor fixes)

---

## Claude's Discretion

- Exact matrix configuration for docker-publish.yml build job
- Error handling and rollback behavior in deploy workflow
- Health check timeout values and retry intervals
- Migration step integration with deploy workflow

## Deferred Ideas

None — all decisions stayed within phase scope.
