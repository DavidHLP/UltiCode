---
kind: rules
paths:
  - 'console/package.json'
  - 'console/*.{json,ts,js,mjs,cjs,html}'
  - 'console/src/**/*.{ts,vue,css}'
  - 'console/public/**/*.{js,css,html}'
summary: 'Console app (Vue 3 user application) rules.'
triggers:
  - 'console app'
  - 'user application'
---
# Console application workflow

- Read `console/AGENTS.md` before editing. Do not infer Console behavior from the Management application.
- Build a change map from the affected view to routing, state or composables, API code, request infrastructure, shared packages, locales, and tests.
- Compare with an existing Console feature that uses the same seam before introducing a new abstraction.
- If the change might belong in `shared/`, inspect both applications and read `shared/AGENTS.md` before deciding ownership.
- Run the checks selected from the Console and changed-package guides, then inspect any formatter or lint rewrite before keeping it.
