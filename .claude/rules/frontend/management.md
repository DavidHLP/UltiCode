---
paths:
  - "management/package.json"
  - "management/*.{json,ts,js,mjs,cjs,html}"
  - "management/src/**/*.{ts,vue,css}"
  - "management/public/**/*.{js,css,html}"
---

# Management application workflow

- Read `management/AGENTS.md` before editing. Do not copy Console API, routing, layout, or component assumptions into the administrator application.
- Build a change map from the affected route to API code, permission metadata, layout, view or component, locales, shared packages, and tests.
- Compare with an existing Management feature that uses the same seam before introducing a new abstraction.
- If the change might belong in `shared/`, inspect both applications and read `shared/AGENTS.md` before deciding ownership.
- Run the checks selected from the Management and changed-package guides, then inspect any formatter or lint rewrite before keeping it.
