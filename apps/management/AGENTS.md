# Management frontend guide

This file supplements [`../AGENTS.md`](../AGENTS.md) for the administrator Vue 3 application.

## Boundaries and patterns

- Use Vue 3 Composition API with `<script setup lang="ts">`, Pinia, Vue Router, and the existing i18n module layout.
- Admin API modules live under `src/api/admin/` and expose typed resource API objects over the helpers in `src/utils/request.ts`.
- Administrative routes remain under `MainLayout` and declare the appropriate `PERM` permission metadata. UI gates do not replace backend authorization.
- Add translations to both locales. Data-table column keys must cover the field identifiers actually emitted by the API; run the repository validator rather than maintaining an undocumented parallel naming rule.
- Single-word component names are permitted by this app's ESLint configuration.
- Check `packages/` before duplicating cross-app behavior. Do not expose management-only UI, hidden cases, or privileged data to console.

## Verification

Run from this directory:

```bash
pnpm lint
pnpm type-check
pnpm test
pnpm validate:i18n-keys
pnpm build
```

Run `validate:i18n-keys` whenever routes, tables, labels, or locale files change. Lint and formatting commands modify files; inspect their diff. Run a changed shared package's own checks because app tests exclude shared theme tests.
