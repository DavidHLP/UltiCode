---
paths:
  - "management/package.json"
  - "management/*.{json,ts,js,mjs,cjs,html}"
  - "management/src/**/*.{ts,vue,css}"
  - "management/public/**/*.{js,css,html}"
---

# Management application workflow

- Read `management/AGENTS.md` before editing. Do not copy Console API, routing, layout, or component assumptions into the administrator application.
- Trace a change through the admin API object, permission-aware route, view or component, locale modules, and tests as applicable.
- Preserve backend authorization as the trust boundary; UI permission gates are usability controls, not access control.
- Check `shared/` before duplicating durable cross-application behavior, while keeping privileged and Management-only behavior out of shared packages and Console.
- Treat emitted API field identifiers and locale keys as one contract. Run the repository i18n-key validator whenever the affected surface can change that mapping.
- Run the relevant Management checks from its guide and the checks of every changed shared package. Inspect any formatter or lint rewrite before keeping it.
