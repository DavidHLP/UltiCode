---
title: Theme System
type: concept
tags: [frontend, theme, design-system, type/concept]
status: living
updated: 2026-06-21
sources:
  - shared/theme/
  - console/public/theme-bootstrap.js
  - management/public/theme-bootstrap.js
  - CLAUDE.md
aliases: [主题系统]
---

# Theme System

## The problem
Two apps (+ Monaco editor + ECharts) must share one look, switch light/dark/system
× compact/comfortable, and avoid the flash-of-unstyled-content (FOUC) on cold load.
Per-app re-implementations drift and cause hydration mismatch.

## The decision
A **4-layer** system, owned by `shared/theme`:

1. **state** — `ThemeMode` (light/dark/system) + density, a singleton.
2. **tokens** — the full Design Token set (color, spacing, radius, density).
3. **primitives** — component-level token consumers.
4. **bootstrap** — `applyThemeToDOM` runs **before** paint.

- **Project font = LXGW WenKai (楷体)**, site-wide, including Monaco and ECharts
  defaults.
- **FOUC killer**: `console/public/theme-bootstrap.js` and
  `management/public/theme-bootstrap.js` are external scripts that run before the
  app bundle; logic mirrors `shared/theme/src/applyThemeToDOM.ts`.

## Where it lives
- `shared/theme/` (ThemeMode, `applyThemeToDOM`, typography.css).
- `console|management/public/theme-bootstrap.js`.

## Trade-offs / rules
- Theme init runs in **exactly one place** (the bootstrap script). Re-implementing
  it in `main.ts` or a component `onMounted` races the singleton → hydration drift.
  Forbidden.
- Under a future strict CSP (no `'unsafe-inline'`), the `<script src>` bootstrap
  needs a nonce/hash, and `index.html` updated to match.

## Related
[[overview/frontend-apps-overview]] · [[overview/architecture-overview]]
