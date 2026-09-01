# Console frontend guide

This file supplements [`../../AGENTS.md`](../../AGENTS.md) for the user-facing Vue 3 application.

## Boundaries and patterns

- Use Vue 3 Composition API with `<script setup lang="ts">`, Pinia, Vue Router, and the existing i18n layout.
- API modules under `src/api/` call the helpers from `src/utils/request.ts` directly. Do not introduce management-style API objects solely for symmetry.
- Initialize authentication before installing the router; protected-route guards depend on completed auth bootstrap.
- `AppLayout.vue` is conditional. Do not assume every route has a sidebar.
- Add translation keys to both `src/i18n/locales/zh-CN/` and `src/i18n/locales/en-US/`.
- Check `packages/` before duplicating behavior used by management. Do not import management-only UI or hidden test-case views.
- Mock `src/utils/request.ts` in API unit tests using Vitest; the project does not use MSW.

## Verification

Run from this directory:

```bash
pnpm lint
pnpm type-check
pnpm test
pnpm build
```

`pnpm lint` and formatting commands modify files; inspect their diff. The app test command intentionally excludes shared auth and theme tests, so run the changed shared package's own checks as well.
