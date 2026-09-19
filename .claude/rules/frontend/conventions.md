---
paths:
  - "apps/console/**/*.{ts,tsx,vue,js,mjs,cjs,json,css,html}"
  - "apps/management/**/*.{ts,tsx,vue,js,mjs,cjs,json,css,html}"
  - "packages/**/*.{ts,tsx,vue,js,mjs,cjs,json,css}"
---

# Frontend changes

- Read the affected app's `AGENTS.md`; for shared code also read `packages/AGENTS.md`. Preserve each app's existing API, auth bootstrap and router conventions.
- Use the established request helper and typed public contracts. Do not bypass authentication/error handling with a component-local client or import another app's internals.
- Preserve the shared sanitization and theme ownership required by the root guide. Frontend environment variables are public; do not put secrets in bundles.
- Keep changed controls keyboard-accessible, labelled and visibly focusable. Preserve both locales and handle loading, failure and stale responses where the feature owns them.
- Test the changed behavior at its public boundary; clean up mocks, timers and listeners. Run changed-package checks because app tests exclude some shared packages; broaden consumer checks when their contracts are affected.
