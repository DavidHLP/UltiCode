# Shared frontend packages guide

This file supplements [`../AGENTS.md`](../AGENTS.md) for packages under `packages/`.

## Package boundary

Each package owns one focused cross-app seam. Current packages and their manifests are authoritative; `pnpm-workspace.yaml` discovers `packages/\*` automatically.

- `auth-core`, `auth-ui`: authentication state, CSRF, permissions, and reusable auth UI.
- `http-client`: shared Axios behavior, retries, request deduplication, and auth-failure coordination.
- `domain-types`, `sandbox-types`: cross-surface contracts.
- `markdown-utils`: Markdown/KaTeX rendering and sanitization.
- `theme`, `design-system`: theme state, bootstrap, tokens, and CSS primitives.
- `badge-config`, `submission-status`, `sidebar-menu`, `datetime-utils`, `locale-preference`: focused presentation and preference seams.

## Rules

- Keep public interfaces small and import through a package entry point or a declared `package.json` subpath export. Do not couple consumers to undeclared internals.
- Extend an existing package when the behavior belongs to its seam; create a package only for a durable boundary used across applications.
- When a consuming app type-checks shared source through `@/shared/<package>/src` (vite/tsconfig alias pointing to `../../packages/`), ensure its `tsconfig.app.json` includes the package.
- Preserve the full sanitization pipeline in `markdown-utils`; add malicious-input regressions for rendering changes.
- Theme initialization is owned by `packages/theme` and the generated `public/theme-bootstrap.js` copies. Change the source, run the sync/verification scripts, and do not duplicate initialization in components or `main.ts`.
- Keep `auth-core` subpath exports explicit when sibling packages need a narrow internal seam.

## Verification

Run the scripts declared by the changed package from its directory:

```bash
pnpm type-check
pnpm test
```

Not every package defines both scripts. Also run the consuming app's type-check and tests when a public contract, runtime behavior, theme bootstrap, or import path changes.
