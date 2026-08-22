---
name: frontend-console
description: Console app (Vue 3 user application) rules.
globs:
  - apps/console/package.json
  - apps/console/*.{json,ts,js,mjs,cjs,html}
  - apps/console/src/**/*.{ts,vue,css}
  - apps/console/public/**/*.{js,css,html}
condition: ["(?i)Console"]
interruptMode: never
alwaysApply: false
---

# Console application workflow

- Read `apps/console/AGENTS.md` before editing. Do not infer Console behavior from the Management application.
- Build a change map from the affected view to routing, state or composables, API code, request infrastructure, shared packages, locales, and tests.
- Compare with an existing Console feature that uses the same seam before introducing a new abstraction.
- If the change might belong in `packages/`, inspect both applications and read `packages/AGENTS.md` before deciding ownership.
- Run the checks selected from the Console and changed-package guides, then inspect any formatter or lint rewrite before keeping it.
