---
phase: 11-hardening
plan: 02
subsystem: CI/CD
tags: [ci-cd, rollback, github-actions, deployment, hardening]
dependency_graph:
  requires: []
  provides: ["cd-rollback workflow"]
  affects: ["cd-deploy.yml (shared concurrency group)"]
tech_stack:
  added: ["GitHub Actions workflow_dispatch", "GHCR tag verification via REST API"]
  patterns: ["SSH deployment", "ordered health checks", "concurrency groups"]
key_files:
  created:
    - .github/workflows/cd-rollback.yml
  modified: []
key_decisions: []
metrics:
  duration: "2m 25s"
  completed_date: "2026-04-18"
---

# Phase 11 Plan 02: Rollback Workflow Summary

Manual rollback workflow enabling fast recovery from failed deployments by redeploying a known-good Docker image tag via GitHub Actions `workflow_dispatch`, with GHCR tag verification, SSH deployment, and ordered health checks.

## Tasks Completed

| Task | Name | Commit | Status |
|------|------|--------|--------|
| 1 | Create cd-rollback.yml with tag verification, SSH deploy, and health checks | a6e41624f | Done |
| 2 | Validate YAML syntax and structural correctness | (no changes) | Done |

## Task Details

### Task 1: Create cd-rollback.yml

Created `.github/workflows/cd-rollback.yml` with:

- **workflow_dispatch trigger** with required `image_tag` string input and `services` choice input (all/backend/console/management)
- **verify-tag job** that checks GHCR via GitHub Packages REST API for tag existence across all 3 service images before deployment
- **rollback job** that reuses the exact SSH key setup, docker compose pull/up, and health check patterns from `cd-deploy.yml`
- **No migration step** (Flyway is forward-only; rollback only reverts application code, not schema)
- **Backend health check first** with fail-fast behavior (15 retries, 5s sleep = 75s max wait)
- **Frontend health checks** run after backend passes (console:9002, management:9003)
- **Shared concurrency group** `deploy-production` prevents concurrent rollback + forward deploy
- **environment: production** gate on rollback job
- **Failure notification** step with `if: failure()`

### Task 2: Structural Validation

All structural checks passed:
- YAML parses without errors
- workflow_dispatch with required image_tag and services inputs confirmed
- Concurrency group `deploy-production` with cancel-in-progress: false confirmed
- Two jobs (verify-tag, rollback) with correct dependency chain confirmed
- No migration steps present
- Health check parameters match cd-deploy.yml exactly
- All required secrets referenced (DEPLOY_SSH_KEY, DEPLOY_HOST, DEPLOY_USER, DEPLOY_PORT, DEPLOY_PATH, GITHUB_TOKEN)
- permissions: packages: read set correctly

## Deviations from Plan

None - plan executed exactly as written.

## Threat Flags

No new threat surface introduced beyond what the plan's threat model already covers (T-11-05 through T-11-09).

## Self-Check: PASSED
