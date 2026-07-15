---
paths:
  - "console/package.json"
  - "console/*.{json,ts,js,mjs,cjs,html}"
  - "console/src/**/*.{ts,vue,css}"
  - "console/public/**/*.{js,css,html}"
---

# Console application workflow

- Read `console/AGENTS.md` before editing. Do not infer Console behavior from the Management application.
- Trace the affected view through its router, store or composable, API module, request helper, shared package, and tests as applicable.
- Preserve the Console-specific API and authentication bootstrap patterns described by its guide; reuse an existing local pattern before creating a new abstraction.
- Check `shared/` before duplicating stable cross-application behavior, but keep Console-only behavior in Console.
- Treat visible text, route behavior, request/response types, and sanitized rendering as boundaries that require matching tests or fixtures.
- Run the relevant Console checks from its guide and the checks of every changed shared package. Inspect any formatter or lint rewrite before keeping it.
