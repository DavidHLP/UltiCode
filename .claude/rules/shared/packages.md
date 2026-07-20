---
paths:
  - "shared/**/*.{ts,vue,js,mjs,cjs,json,css}"
kind: rules
summary: 'Shared monorepo packages conventions.'
---

# Shared package workflow

- Read `shared/AGENTS.md` and the changed package's manifest and public entry point before editing.
- State the package responsibility in one sentence, then compare the proposed change with that responsibility before choosing the seam.
- Use graph and import searches to inventory public entry points, declared exports, and every Console or Management consumer affected by the change.
- Build a compatibility checklist for public types, runtime behavior, initialization order, and import paths that change.
- Select package checks from its manifest and consuming-application checks from the relevant guides; do not assume shared packages expose identical scripts.
