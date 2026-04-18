# Phase 11: Hardening - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-18
**Phase:** 11-hardening
**Areas discussed:** Dependabot Configuration, Rollback Workflow Architecture
**Mode:** Auto (non-interactive)

---

## Dependabot Configuration

| Option | Description | Selected |
|--------|-------------|----------|
| All 3 ecosystems (github-actions, npm, maven) | Full coverage matching HARD-01 requirement; weekly schedule; grouped updates | ✓ |
| GitHub Actions + npm only | Skip Maven to reduce PR volume; backend deps change less frequently | |
| GitHub Actions only | Minimal scope; only what HARD-01 explicitly names first | |

**Auto-selected:** All 3 ecosystems (matches HARD-01 requirement for "GitHub Actions version updates and npm/Maven dependency updates")

---

## Dependabot PR Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Manual review for all PRs | CI validates automatically; human reviews for safety; no auto-merge | ✓ |
| Auto-merge patch updates | Minor and patch updates auto-merge after CI passes; major updates need review | |
| Full auto-merge | All Dependabot PRs auto-merge after CI passes | |

**Auto-selected:** Manual review (safest for production; CI provides automated validation baseline)

---

## Dependabot Update Grouping

| Option | Description | Selected |
|--------|-------------|----------|
| Group by ecosystem with sub-groups | npm: prod + dev separate; Actions: all together; Maven: all together | ✓ |
| Group all updates together | Single PR per schedule with all dependency updates | |
| No grouping | One PR per dependency update | |

**Auto-selected:** Group by ecosystem with sub-groups (reduces PR volume while keeping review scope manageable)

---

## Rollback Workflow Architecture

| Option | Description | Selected |
|--------|-------------|----------|
| Separate cd-rollback.yml | Dedicated rollback workflow; clean separation from deploy; skips migrations by default | ✓ |
| Extend cd-deploy.yml | Add rollback mode to existing deploy workflow; reuse all steps | |
| Reuse cd-deploy.yml as-is | cd-deploy.yml already accepts image_tag input; rollback = deploy with old tag | |

**Auto-selected:** Separate cd-rollback.yml (cd-deploy.yml is already complex; separation of concerns)

---

## Rollback Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Docker image rollback only | Redeploy previous SHA-tagged images; no DB changes; safe for minor rollbacks | ✓ |
| Docker + DB migration rollback | Automated Flyway undo; risky for automated workflow | |
| Docker rollback + DB snapshot warning | Rollback images but warn if DB schema changed since deployed version | |

**Auto-selected:** Docker image rollback only (DB rollback is too risky for automation; manual intervention required for schema-related rollbacks)

---

## Claude's Discretion

- Exact Dependabot group naming and configuration details
- Rollback workflow error handling and notification patterns
- Health check timeout values for rollback (reuse cd-deploy.yml values)
- Whether to add a "verify tag exists" step before rollback deployment

## Deferred Ideas

None — all decisions stayed within phase scope.
