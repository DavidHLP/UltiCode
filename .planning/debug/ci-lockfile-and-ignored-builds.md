---
name: ci-lockfile-and-ignored-builds
description: CI workflow failures - lockfile mismatch and ignored builds
type: debug
status: investigating
trigger: "CI workflow #24600204003 failed with two distinct errors: (1) Test (console) failed with ERR_PNPM_OUTDATED_LOCKFILE - vue-i18n and vue-router versions mismatch between lockfile and manifest; (2) Lint (management) failed with ERR_PNPM_IGNORED_BUILDS for esbuild, maplibre-gl, vue-demi"
created: 2026-04-18
updated: 2026-04-18
symptoms:
  expected: |
    CI workflow completes successfully. pnpm install --frozen-lockfile succeeds.
  actual: |
    Two failures:
    1. Test (console): ERR_PNPM_OUTDATED_LOCKFILE - vue-i18n (lockfile ^10.0.0 vs manifest ^11.3.2) and vue-router (lockfile ^4.6.3 vs manifest ^5.0.4)
    2. Lint (management): ERR_PNPM_IGNORED_BUILDS - esbuild@0.27.4, maplibre-gl@2.4.0, vue-demi@0.14.10
  error_messages: |
    1. "ERR_PNPM_OUTDATED_LOCKFILE Cannot install with frozen-lockfile because pnpm-lock.yaml is not up to date"
    2. "ERR_PNPM_IGNORED_BUILDS Ignored build scripts: esbuild@0.27.4, maplibre-gl@2.4.0, vue-demi@0.14.10"
  timeline: |
    Commit 87f0ef699 ("fix(tsconfig): remove unsupported ignoreDeprecations option") pushed to main triggered CI. CI has been failing.
  reproduction: |
    CI runs pnpm install --frozen-lockfile on every job. The lockfile is stale for console but up-to-date for management.
Current Focus: RESOLVED
  hypothesis: "Root cause is package version drift - console's package.json was updated to vue-i18n@11.3.2 and vue-router@5.0.4 but pnpm-lock.yaml wasn't regenerated."
  next_action: "None - fix has been applied"
  evidence:
    - "console/pnpm-lock.yaml had vue-i18n@10.0.8 but package.json required ^11.3.2"
    - "console/pnpm-lock.yaml had vue-router@4.6.3 but package.json required ^5.0.4"
    - "Ran 'pnpm install' in console directory to regenerate lockfile"
  eliminated:
    - "Management lockfile was already in sync - no changes needed"
    - "esbuild/maplibre-gl/vue-demi warnings in management were not the root cause"
---
