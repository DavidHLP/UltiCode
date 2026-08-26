---
description: Shared monorepo packages conventions.
globs:
- packages/**/*.{ts,vue,js,mjs,cjs,json,css}
priority: 100
---

# Shared package workflow

- Read `packages/AGENTS.md` and the changed package's manifest and public entry point before editing.
- State the package responsibility in one sentence, then compare the proposed change with that responsibility before choosing the seam.
- Use graph and import searches to inventory public entry points, declared exports, and every Console or Management consumer affected by the change.
- Build a compatibility checklist for public types, runtime behavior, initialization order, and import paths that change.
- Select package checks from its manifest and consuming-application checks from the relevant guides; do not assume shared packages expose identical scripts.
- Core shared seams include `auth-core`, `auth-ui`, `http-client`, `domain-types`, `sandbox-types`, `markdown-utils`, `theme`, `design-system`, `sidebar-menu`, `app-bootstrap`, `badge-config`, `datetime-utils`, `i18n-completeness`, `locale-preference`, and `submission-status`.
- Theme initialization and palette tokens belong to `packages/theme` and `packages/design-system`; do not duplicate theme bootstrap logic in component code.
- Preserve full DOMPurify sanitization in `packages/markdown-utils`; add regression tests for any HTML rendering changes.
