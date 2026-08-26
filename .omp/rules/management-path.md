---
description: Management app (Vue 3 admin application) rules.
globs:
- apps/management/package.json
- apps/management/*.{json,ts,js,mjs,cjs,html}
- apps/management/src/**/*.{ts,vue,css}
- apps/management/public/**/*.{js,css,html}
priority: 100
---

# Management application workflow

- Read `apps/management/AGENTS.md` before editing. Do not copy Console API, routing, layout, or component assumptions into the administrator application.
- Build a change map from the affected route to API code, permission metadata, layout, view or component, locales, shared packages, and tests.
- Compare with an existing Management feature that uses the same seam before introducing a new abstraction.
- If the change might belong in `packages/`, inspect both applications and read `packages/AGENTS.md` before deciding ownership.
- Run the checks selected from the Management and changed-package guides, then inspect any formatter or lint rewrite before keeping it.
- Admin API modules live under `src/api/admin/` and expose typed resource API objects over `src/utils/request.ts`.
- Administrative routes remain under `MainLayout` and declare explicit `PERM` permission metadata; UI gates do not replace backend authorization.
- Changes to routes, tables, labels, or locale files **MUST** pass `pnpm validate:i18n-keys` in `apps/management`.
- Do not expose management-only UI, hidden test cases, or privileged administration capabilities to console.
