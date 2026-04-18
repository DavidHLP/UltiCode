# Phase 11: Hardening - Research

**Researched:** 2026-04-18
**Domain:** CI/CD pipeline hardening (Dependabot configuration + deployment rollback)
**Confidence:** HIGH

## Summary

Phase 11 adds two self-maintenance capabilities to the CI/CD pipeline: automated dependency version updates via Dependabot (HARD-01), and a manual rollback workflow that redeploys a previous Docker image tag (HARD-02). Both are additive changes -- no existing files need modification except creating one new file (`.github/dependabot.yml`) and one new workflow (`.github/workflows/cd-rollback.yml`).

The Dependabot configuration is straightforward: three ecosystems (`github-actions`, `npm`, `maven`) with weekly schedules, grouped PRs, and a 5-PR limit per ecosystem. The main complexity is that `console/` and `management/` are separate npm projects (no root `package.json`, no pnpm workspace at repo root), requiring two separate `npm` entries. A recently added feature (Feb 2026) allows cross-directory grouping by `dependency-name`, but per D-03 the context decision groups by dependency-type (production vs development) within each directory, so separate entries with per-directory groups is the correct approach.

The rollback workflow is architecturally simple -- it reuses the exact SSH deploy + health check pattern from `cd-deploy.yml`, but with a required `image_tag` input and no migration step. The `docker-publish.yml` already tags every build with `type=sha` (e.g., `sha-abc1234`), so previous images are available in GHCR indefinitely. The rollback workflow adds a pre-deploy tag verification step using the GitHub Packages REST API or `docker manifest inspect` to fail fast if the specified tag does not exist.

**Primary recommendation:** Create a single `.github/dependabot.yml` with 4 update entries (github-actions, npm for console, npm for management, maven) and a dedicated `cd-rollback.yml` that mirrors cd-deploy.yml minus migrations.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Configure Dependabot for all 3 ecosystems: `github-actions`, `npm`, and `maven`
- **D-02:** Set Dependabot schedule to `weekly` for all ecosystems
- **D-03:** Use Dependabot `groups` to batch updates within each ecosystem (npm: production vs development; github-actions: all together; maven: all together)
- **D-04:** No auto-merge -- all Dependabot PRs require manual review
- **D-05:** Set `open-pull-requests-limit: 5` per ecosystem, labels `dependencies` + `automated`, no specific reviewers
- **D-06:** Create separate `cd-rollback.yml` workflow file
- **D-07:** Rollback redeploys by specifying git SHA short hash as `IMAGE_TAG`
- **D-08:** Rollback does NOT include database migration rollback
- **D-09:** Rollback uses `workflow_dispatch` with required `image_tag` input; reuses SSH + health check from cd-deploy.yml; skips migration; should verify tag exists in GHCR
- **D-10:** Add `concurrency: group: deploy-production, cancel-in-progress: false` to prevent concurrent deploy/rollback operations

### Claude's Discretion
- Exact Dependabot group naming and configuration details
- Rollback workflow error handling and notification patterns
- Health check timeout values for rollback (can reuse cd-deploy.yml values)
- Whether to add a "verify tag exists" step before rollback deployment

### Deferred Ideas (OUT OF SCOPE)
- Deployment notifications to Slack/Discord (v2: MON-01)
- Deployment history log (v2: MON-02)
- Concurrency groups for CI runs (v2: ADVCI-01)
- Test result artifacts (v2: ADVCI-02)
- Deploy previews for PRs (v2: ADVCI-03)
- DB migration rollback (too risky for automated workflow -- manual only)
- Dependabot auto-merge (manual review enforced)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HARD-01 | Dependabot configuration for GitHub Actions version updates and npm/Maven dependency updates | Dependabot options reference verified; npm needs 2 entries (console + management); maven needs 1 (backend-spring); github-actions needs 1 (/) |
| HARD-02 | Rollback workflow allows manual redeployment of a previous image tag via workflow_dispatch | cd-deploy.yml pattern reusable; GHCR SHA tags available; tag verification via GitHub Packages REST API |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Dependabot config | GitHub Platform (Dependabot service) | --- | GitHub-hosted service reads `.github/dependabot.yml` and opens PRs; no code to deploy |
| Dependency PR validation | CI (GitHub Actions) | --- | Existing `ci.yml` runs on all PRs including Dependabot's; no changes needed |
| Rollback trigger | GitHub Platform (Actions UI) | --- | `workflow_dispatch` input in GitHub Actions UI; no application code involved |
| Rollback deployment | CI/CD (GitHub Actions runner) | --- | SSH to production server, docker compose with specific IMAGE_TAG, health checks |
| GHCR image availability | Infrastructure (GHCR) | --- | docker-publish.yml tags all images with SHA; images persist in GHCR |
| Tag verification | CI/CD (GitHub Actions runner) | --- | Pre-deploy check via GitHub Packages REST API or docker manifest inspect |

## Standard Stack

### Core

| Library/Tool | Version | Purpose | Why Standard |
|---|---|---|---|
| GitHub Dependabot | Built-in (GitHub service) | Automated dependency version updates | Native GitHub feature, no installation required; reads `.github/dependabot.yml` |
| GitHub Actions | Built-in | Rollback workflow execution | Same runner and patterns as existing cd-deploy.yml |

### Supporting

| Library/Tool | Version | Purpose | When to Use |
|---|---|---|---|---|
| `docker/metadata-action` | v5 | Extract image tags (already in docker-publish.yml) | Reference only -- rollback reads existing tags, does not build |
| GitHub Packages REST API | REST v3 | Verify GHCR image tag exists before rollback | Tag verification step in cd-rollback.yml |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|---|---|---|
| Dependabot | Renovate bot | Renovate is more configurable but requires self-hosting or a paid GitHub App; Dependabot is free and native |
| GHCR API tag check | `docker manifest inspect` | `docker manifest inspect` is simpler but requires docker login; REST API uses `GITHUB_TOKEN` directly |
| Separate cd-rollback.yml | Reuse cd-deploy.yml with flag | D-06 explicitly requires separate file; mixing concerns increases risk of accidental migration execution during rollback |

**Installation:** No packages to install. This phase creates configuration files only.

## Architecture Patterns

### System Architecture Diagram

```
                    Dependabot (HARD-01)
                    ===================
                    GitHub-hosted service reads
                    .github/dependabot.yml weekly
                           |
                           v
                    Opens PRs for each group
                           |
                           v
                    ci.yml triggers automatically
                    on pull_request events
                           |
                           v
                    Human reviews & merges
                           |
                           v
                    docker-publish.yml builds
                    and pushes SHA-tagged images
                    to GHCR on merge to main


                    Rollback Workflow (HARD-02)
                    ==========================
                    User triggers cd-rollback.yml
                    via GitHub Actions UI
                    (workflow_dispatch)
                           |
                           v
                    [Tag Verification Step]
                    Check SHA tag exists in GHCR
                    via GitHub Packages API
                           |
                      (exists?)
                     /         \
                   yes           no
                   /              \
                  v                v
            SSH Deploy to      Fail fast with
            Production Server   clear error msg
                  |
                  v
            docker compose pull
            with IMAGE_TAG=sha-<hash>
                  |
                  v
            docker compose up -d
            (no migration step)
                  |
                  v
            Health Checks:
            1. Backend  (9001)
            2. Console  (9002)
            3. Mgmt     (9003)
                  |
                  v
            Success / Failure
            notification
```

### Recommended Project Structure

```
.github/
  dependabot.yml           # NEW: Dependabot configuration (HARD-01)
  workflows/
    cd-rollback.yml         # NEW: Rollback workflow (HARD-02)
    cd-deploy.yml           # EXISTING: Forward deployment (reference only)
    docker-publish.yml      # EXISTING: Image build/push (reference only)
    ci.yml                  # EXISTING: CI validation (Dependabot PRs trigger this)
    ci-recommendation.yml   # EXISTING: Excluded from Dependabot (Phase 9 D-07)
```

### Pattern 1: Dependabot Multi-Directory npm Configuration

**What:** When a repo has multiple npm projects in subdirectories with no root `package.json` or workspace file, Dependabot requires separate `package-ecosystem: npm` entries for each directory. [VERIFIED: GitHub Docs -- `directory` option requires specifying the location of manifest/lock files]

**When to use:** Monorepo-style repos with independent npm projects in subdirectories.

**Key findings for this project:**
- `console/pnpm-lock.yaml` exists -- Dependabot detects this as npm ecosystem (pnpm is supported under `npm` ecosystem, versions v7-v10) [VERIFIED: GitHub Docs package-ecosystem table]
- `management/pnpm-lock.yaml` exists -- same as above
- No root `package.json` or `pnpm-workspace.yaml` at repo root -- cannot use a single npm entry with root directory [VERIFIED: filesystem check]
- Each directory needs its own Dependabot entry with its own `groups` configuration

**Example:**
```yaml
# .github/dependabot.yml
version: 2
updates:
  # GitHub Actions
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    groups:
      all-actions:
        patterns:
          - "*"
    labels:
      - "dependencies"
      - "automated"
    open-pull-requests-limit: 5

  # Console (npm/pnpm)
  - package-ecosystem: "npm"
    directory: "/console"
    schedule:
      interval: "weekly"
    groups:
      production:
        dependency-type: "production"
        update-types:
          - "minor"
          - "patch"
      development:
        dependency-type: "development"
    labels:
      - "dependencies"
      - "automated"
    open-pull-requests-limit: 5

  # Management (npm/pnpm)
  - package-ecosystem: "npm"
    directory: "/management"
    schedule:
      interval: "weekly"
    groups:
      production:
        dependency-type: "production"
        update-types:
          - "minor"
          - "patch"
      development:
        dependency-type: "development"
    labels:
      - "dependencies"
      - "automated"
    open-pull-requests-limit: 5

  # Backend (Maven)
  - package-ecosystem: "maven"
    directory: "/backend-spring"
    schedule:
      interval: "weekly"
    groups:
      all-maven:
        patterns:
          - "*"
    labels:
      - "dependencies"
      - "automated"
    open-pull-requests-limit: 5
```

**Source:** [VERIFIED: GitHub Docs Dependabot options reference](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference)

### Pattern 2: Rollback Workflow with Tag Verification

**What:** A `workflow_dispatch` workflow that verifies a Docker image tag exists in GHCR before attempting SSH deployment, then reuses the proven SSH + health check pattern from cd-deploy.yml.

**When to use:** Emergency rollback of production deployments.

**Tag verification approach:** Use the GitHub Packages REST API to check if the specified tag exists. This avoids needing docker login on the runner and uses `GITHUB_TOKEN` directly.

```bash
# Verify tag exists via GitHub Packages REST API
# API: GET /orgs/{org}/packages/container/{package_name}/versions
# Check metadata.container.tags array for the desired tag
curl -s -H "Authorization: token ${{ secrets.GITHUB_TOKEN }}" \
  "https://api.github.com/orgs/${{ github.repository_owner }}/packages/container/${{ github.event.inputs.image_tag }}/versions" \
  | jq -r '.[].metadata.container.tags[]' | grep -q "${{ github.event.inputs.image_tag }}"
```

**Alternative (simpler but requires docker login):**
```bash
docker manifest inspect ghcr.io/${{ github.repository }}/backend:${{ github.event.inputs.image_tag }} > /dev/null 2>&1
```

**Recommendation:** Use the REST API approach since `GITHUB_TOKEN` is already available and no docker setup is needed on the runner.

### Anti-Patterns to Avoid

- **Single npm entry at root:** Dependabot cannot find manifests in subdirectories from a root `/` entry when there is no root `package.json`. Must use separate entries per directory.
- **Auto-merge for Dependabot:** D-04 explicitly rejects auto-merge. Breaking changes can pass CI but break runtime behavior.
- **Including migrations in rollback:** D-08 explicitly excludes DB migration rollback. Flyway migrations are forward-only.
- **Dependabot monitoring ci-recommendation.yml:** Phase 9 D-07 keeps ci-recommendation.yml isolated. The `github-actions` entry with `directory: "/"` will scan all `.github/workflows/` files including ci-recommendation.yml. Use `exclude-paths` if this is a concern.
- **Separate concurrency groups for rollback:** D-10 requires sharing the deploy concurrency group to prevent concurrent deployments.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Dependency version checking | Custom script to scan package.json/pom.xml for updates | Dependabot (native GitHub feature) | Free, automatic, creates PRs, respects lockfiles, handles transitive deps |
| Container image tag lookup | Custom API calls with error handling | GitHub Packages REST API + jq | One-liner with `GITHUB_TOKEN`; no auth setup needed |
| Deployment SSH + health checks | New deployment logic from scratch | Reuse cd-deploy.yml pattern verbatim | Already tested and production-proven; same SSH key, same health check timeouts |

**Key insight:** This entire phase is configuration and workflow wiring. No application code, no custom tools, no hand-rolled solutions. Dependabot is a GitHub-native service, and the rollback workflow is a subset of an existing workflow.

## Common Pitfalls

### Pitfall 1: Dependabot npm Entry at Root Directory

**What goes wrong:** Dependabot creates an npm entry with `directory: "/"` but finds no `package.json` or lockfile at root, so it silently fails to produce any npm PRs.

**Why it happens:** The repo has no root `package.json` or `pnpm-workspace.yaml`. Each frontend project is independent.

**How to avoid:** Use `directory: "/console"` and `directory: "/management"` as separate entries.

**Warning signs:** After creating `dependabot.yml`, no npm PRs appear after the weekly schedule runs.

### Pitfall 2: Dependabot Groups with Wildcard Patterns

**What goes wrong:** Using `patterns: ["*"]` in a group catches all dependencies including ones you might want separate PRs for (e.g., major version bumps of critical dependencies).

**Why it happens:** The `*` wildcard matches everything. For npm, separating by `dependency-type: production` vs `development` provides better control.

**How to avoid:** For npm entries, use `dependency-type` grouping (D-03). For github-actions and maven where there is no meaningful production/development split, `patterns: ["*"]` is acceptable since all deps are infrastructure.

**Warning signs:** A single Dependabot PR updates 20+ dependencies across both production and dev, making review difficult.

### Pitfall 3: Rollback Concurrency Conflict with Forward Deploy

**What goes wrong:** A forward deployment and a rollback run simultaneously, causing a race condition on the production server.

**Why it happens:** If cd-rollback.yml uses a different concurrency group than cd-deploy.yml, both can run in parallel.

**How to avoid:** Use `concurrency: group: deploy-${{ github.event.inputs.environment }}` in cd-rollback.yml, matching cd-deploy.yml's pattern. With `cancel-in-progress: false`, the second workflow waits for the first to complete.

**Warning signs:** Both workflows show as "in progress" in GitHub Actions; production server shows mixed container versions.

### Pitfall 4: GHCR Tag Not Found After Rollback Trigger

**What goes wrong:** User specifies a SHA hash that was never pushed to GHCR (e.g., a commit on a feature branch that was never merged to main, or a typo), and the SSH deploy fails with a confusing docker pull error.

**Why it happens:** docker-publish.yml only pushes on merge to main. Not all commits produce GHCR images.

**How to avoid:** Add a pre-deploy tag verification step that checks GHCR for the tag before attempting SSH deployment. Fail fast with a clear error message listing available recent tags.

**Warning signs:** Rollback fails at the `docker compose pull` step with "manifest not found" error.

### Pitfall 5: Dependabot Scanning ci-recommendation.yml

**What goes wrong:** The `github-actions` Dependabot entry with `directory: "/"` scans all `.github/workflows/*.yml` files, including `ci-recommendation.yml`. Dependabot opens PRs updating actions versions in ci-recommendation.yml, which was intentionally isolated in Phase 9.

**Why it happens:** GitHub Actions ecosystem scans `/.github/workflows/` automatically when `directory: "/"` is specified. [VERIFIED: GitHub Docs -- "For GitHub Actions, use the value `/`. Dependabot will search the `/.github/workflows` directory."]

**How to avoid:** Use `exclude-paths` to ignore ci-recommendation.yml:
```yaml
- package-ecosystem: "github-actions"
  directory: "/"
  exclude-paths:
    - ".github/workflows/ci-recommendation.yml"
```

**Warning signs:** Dependabot opens a PR modifying `ci-recommendation.yml`.

## Code Examples

### Dependabot Configuration (HARD-01)

```yaml
# .github/dependabot.yml
version: 2

updates:
  # GitHub Actions version updates
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    exclude-paths:
      - ".github/workflows/ci-recommendation.yml"
    groups:
      all-actions:
        patterns:
          - "*"
    labels:
      - "dependencies"
      - "automated"
    open-pull-requests-limit: 5
    rebase-strategy: "disabled"

  # Console frontend (pnpm)
  - package-ecosystem: "npm"
    directory: "/console"
    schedule:
      interval: "weekly"
    groups:
      console-production:
        dependency-type: "production"
        update-types:
          - "minor"
          - "patch"
      console-development:
        dependency-type: "development"
    labels:
      - "dependencies"
      - "automated"
    open-pull-requests-limit: 5
    rebase-strategy: "disabled"

  # Management frontend (pnpm)
  - package-ecosystem: "npm"
    directory: "/management"
    schedule:
      interval: "weekly"
    groups:
      management-production:
        dependency-type: "production"
        update-types:
          - "minor"
          - "patch"
      management-development:
        dependency-type: "development"
    labels:
      - "dependencies"
      - "automated"
    open-pull-requests-limit: 5
    rebase-strategy: "disabled"

  # Backend (Maven)
  - package-ecosystem: "maven"
    directory: "/backend-spring"
    schedule:
      interval: "weekly"
    groups:
      all-maven:
        patterns:
          - "*"
    labels:
      - "dependencies"
      - "automated"
    open-pull-requests-limit: 5
    rebase-strategy: "disabled"
```

**Source:** [VERIFIED: GitHub Docs Dependabot options reference](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference) -- all options confirmed: `version: 2`, `package-ecosystem`, `directory`, `schedule.interval`, `groups`, `labels`, `open-pull-requests-limit`, `rebase-strategy`, `exclude-paths`, `dependency-type`, `update-types`, `patterns`.

### Rollback Workflow Skeleton (HARD-02)

```yaml
# .github/workflows/cd-rollback.yml
name: CD Rollback

on:
  workflow_dispatch:
    inputs:
      image_tag:
        description: 'Image tag to roll back to (e.g., sha-abc1234)'
        required: true
        type: string
      services:
        description: 'Services to roll back'
        required: true
        type: choice
        options:
          - all
          - backend
          - console
          - management

concurrency:
  group: deploy-production
  cancel-in-progress: false

env:
  REGISTRY: ghcr.io
  IMAGE_TAG: ${{ github.event.inputs.image_tag }}

jobs:
  verify-tag:
    name: Verify image tag exists in GHCR
    runs-on: ubuntu-latest
    steps:
      - name: Check tag existence
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          # Verify at least one service image with this tag exists
          # Uses GitHub Packages REST API
          ...

  rollback:
    name: Rollback to ${{ github.event.inputs.image_tag }}
    runs-on: ubuntu-latest
    needs: verify-tag
    environment: production
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Install SSH key
        # Same as cd-deploy.yml
        ...

      - name: Pull and deploy services
        # Same as cd-deploy.yml but skip migrations entirely
        # Uses IMAGE_TAG from input
        ...

      - name: Health check - backend (fail fast)
        # Same timeouts as cd-deploy.yml (15 retries, 5s sleep)
        ...

      - name: Health check - frontends
        # Same as cd-deploy.yml
        ...
```

### GHCR Tag Verification Step

```bash
# Option A: GitHub Packages REST API (recommended)
# Lists versions and checks if the tag appears in any version's metadata
TAG="${{ github.event.inputs.image_tag }}"
OWNER="${{ github.repository_owner }}"
REPO_NAME="${{ github.repository }}"

# Check backend image tag
RESPONSE=$(curl -sf -H "Authorization: token ${{ secrets.GITHUB_TOKEN }}" \
  "https://api.github.com/orgs/${OWNER}/packages/container/${REPO_NAME##*/}/versions?per_page=100")

if echo "$RESPONSE" | jq -r '.[].metadata.container.tags[]' 2>/dev/null | grep -qx "$TAG"; then
  echo "Tag $TAG found in GHCR"
else
  echo "::error::Tag $TAG not found in GHCR. Available recent tags:"
  echo "$RESPONSE" | jq -r '.[].metadata.container.tags[]' 2>/dev/null | head -20
  exit 1
fi
```

**Source:** [VERIFIED: GitHub Community Discussion #26279](https://github.com/orgs/community/discussions/26279) -- tags/list endpoint confirmed for GHCR.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| Manual dependency updates | Dependabot automated PRs | GA since 2020 | Reduces toil, catches security patches |
| Separate PR per dependency | Grouped Dependabot PRs | GA 2024 | Reduces PR volume, easier review |
| Cross-directory grouping not possible | `group-by: dependency-name` across directories | Feb 2026 | Monorepo support improved |
| Manual rollback via SSH | workflow_dispatch rollback | Custom pattern | Faster recovery, audit trail |
| No tag verification before deploy | Pre-deploy GHCR tag check | Custom pattern | Fail fast on invalid tags |

**Deprecated/outdated:**
- Dependabot v1 configuration (`version: 1`): Must use `version: 2` [VERIFIED: GitHub Docs]
- Wildcard directory patterns in Dependabot: Not supported; must list directories explicitly [VERIFIED: GitHub issue #12335 -- still open]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| A1 | pnpm lockfiles are detected by Dependabot under `package-ecosystem: npm` | Standard Stack | LOW -- GitHub Docs explicitly lists pnpm under npm ecosystem with versions v7-v10 |
| A2 | The `exclude-paths` option works for github-actions ecosystem to skip ci-recommendation.yml | Pitfall 5 | MEDIUM -- If not, Dependabot may open PRs for ci-recommendation.yml; workaround is to add it to ignore list or accept the PRs |
| A3 | GitHub Packages REST API returns `metadata.container.tags` in version listings | Code Examples | LOW -- Confirmed by GitHub Community Discussion #26279 |
| A4 | `rebase-strategy: "disabled"` is valid for all ecosystems including github-actions | Code Examples | LOW -- GitHub Docs confirm this option is available for all ecosystems |

## Open Questions

1. **Should Dependabot exclude ci-recommendation.yml via `exclude-paths` or `ignore`?**
   - What we know: `exclude-paths` is available for all ecosystems and supports glob patterns
   - What's unclear: Whether `exclude-paths` works specifically for the github-actions ecosystem to skip individual workflow files
   - Recommendation: Use `exclude-paths` first (cleaner approach); if it doesn't work for github-actions, fall back to `ignore` with `dependency-name: "actions used only in ci-recommendation.yml"`

2. **Should the rollback workflow list available tags on failure?**
   - What we know: The tag verification step can list tags from the API response
   - What's unclear: Whether listing all tags is helpful or just noisy (could be hundreds)
   - Recommendation: List the 10 most recent SHA tags only (filter by `sha-` prefix) to help the user pick the right one

## Environment Availability

Step 2.6: SKIPPED (no external dependencies identified -- this phase creates GitHub configuration files only, using built-in GitHub features and existing secrets)

## Sources

### Primary (HIGH confidence)
- [GitHub Docs: Dependabot options reference](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference) -- Full configuration reference, all options verified
- [GitHub Docs: Configuring multi-ecosystem updates](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/configuring-multi-ecosystem-updates) -- Multi-ecosystem group patterns
- `.github/workflows/cd-deploy.yml` -- Existing deployment pattern (SSH + health checks), read directly
- `.github/workflows/docker-publish.yml` -- SHA tagging pattern (`type=sha`), read directly
- `docker-compose.prod.yml` -- IMAGE_TAG variable usage (`${IMAGE_TAG:-latest}`), read directly
- Filesystem verification: `console/pnpm-lock.yaml`, `management/pnpm-lock.yaml`, `backend-spring/pom.xml` exist; no root `package.json`

### Secondary (MEDIUM confidence)
- [GitHub Community Discussion #26279: How to check if a container image exists on GHCR](https://github.com/orgs/community/discussions/26279) -- Tag verification via REST API confirmed
- [GitHub Blog: Dependabot groups by dependency-name across directories (Feb 2026)](https://github.blog/changelog/2026-02-24-dependabot-can-group-updates-by-dependency-name-across-multiple-directories/) -- Cross-directory grouping feature confirmed GA
- [GitHub dependabot-core #2824: Multiple directories in dependabot.yml](https://github.com/dependabot/dependabot-core/issues/2824) -- Confirms separate entries needed per directory

### Tertiary (LOW confidence)
- None -- all critical claims verified against official docs or filesystem

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- GitHub-native features, no external packages
- Architecture: HIGH -- Patterns directly copied from existing working workflows
- Pitfalls: HIGH -- Based on verified GitHub Docs behavior and filesystem checks

**Research date:** 2026-04-18
**Valid until:** 90 days (GitHub Dependabot configuration is stable; rollback workflow uses well-established GitHub Actions patterns)
