---
title: Frontend Apps Overview
type: overview
tags: [frontend, map, type/overview]
status: living
updated: 2026-06-21
sources:
  - console/src/
  - management/src/
  - shared/
  - .claude/rules/frontend/frontend-rules.md
  - management/AGENTS.md
---

# Frontend Apps Overview

> [!quote] Essence
> Two Vue 3 single-page apps — `console/` (users, port 9002) and `management/`
> (admins, port 9003) — plus shared libraries in `shared/`. Both are TypeScript +
> Vite + Pinia + Tailwind v4 + shadcn-vue, but they differ in **API style** and
> **audience**.

## Map

```
shared/
├── auth-core/   cookie · CSRF · auth-state · permission · refreshCoordinator
└── theme/       ThemeMode · applyThemeToDOM · typography.css (LXGW WenKai)

console/   (port 9002, users)        management/   (port 9003, admins)
├── api/         direct apiGet/apiPost   ├── api/admin/   typed *Api wrappers
├── views/       auth, problems, lists,  ├── views/       dashboard, users,
│                sets, contest, forum,   │                problems, submissions,
│                dashboard, profile,     │                contests, forum, moderation,
│                achievements, editor    │                analytics, billing, settings,
├── stores/      Pinia                  │                system, tags, solutions, audit
├── components/  + ui/ (shadcn)         ├── stores/      Pinia (auth)
├── composables/ useXxx                 ├── components/  TestCasesEditor, MarkdownEditor…
├── i18n/        vue-i18n (en/zh)       ├── i18n/        vue-i18n + wiki/i18n-design.md
└── utils/       request.ts (Axios)     └── utils/       request.ts (Axios)
```

## The two apps

| | `console/` | `management/` |
|---|---|---|
| Audience | end users | administrators |
| Port | 9002 | 9003 |
| API style | direct `apiGet`/`apiPost`/`apiPatch`/`apiDelete` | typed wrappers (`problemsApi.getAll(...)`, `moderationQueueApi(...)`) |
| Routing | auth, problems, problem-list, problem-set, contest, forum, dashboard, profile, achievements, post-editor | dashboard, users, problems, submissions, contests, forum, moderation, analytics, billing, settings, system, tags, solutions, comments, notifications, audit, account |
| E2E | Vitest | Vitest + Playwright |

**API rule** (see `.claude/rules/frontend/`): all calls go through `@/utils/request`
— never a raw Axios instance. `request.ts` owns CSRF, retry-on-network-error,
401→login redirect, and the snake_case↔camelCase mapping. See
[[concepts/result-envelope-and-case-mapping]].

## Shared layer

- **`shared/auth-core`** — the single source of cookie/CSRF/auth-state/permission
  logic both apps depend on. Changes here **must** be validated in both frontends
  (`pnpm test` + `pnpm type-check` inside the package). Console excludes the
  symlinked shared auth tests; management includes them.
- **`shared/theme`** — ThemeMode singleton + `applyThemeToDOM`. The
  `console/public/theme-bootstrap.js` and `management/public/theme-bootstrap.js`
  mirrors exist only to eliminate FOUC; do **not** re-initialize theme in
  `main.ts` or component `onMounted` — see [[concepts/theme-system]].

## Conventions (both apps)

- `<script setup lang="ts">` only; no Options API.
- `defineProps<{...}>()` / `defineEmits<{...}>()` generics.
- Prettier: no semicolons, single quotes, 100 cols.
- Page components `XxxView.vue` in `views/`; reuse in `components/`; primitives in `components/ui/`.
- All user-visible text through `t()`; add **both** `en` and `zh` keys.
- Management `DataTable.vue` column headers: `t(\`table.columnNames.${column.id}\`)`
  where `column.id` is camelCase matching the API field — define both camelCase
  and snake_case keys in `i18n/locales/*/modules/table.ts`.

## Type alignment with backend

> [!warning] Open alignment item
> Frontend uses proper TS enums; backend DTO enum fields are still raw `String`
> (e.g. `PerformModerationActionDTO.action`). Some management API files define
> "ghost" types with no backend endpoint yet (`UserWarning`, `CreateUserBanDto`) —
> treat as dead code until an endpoint appears. Aligning shared DTO/enums is an
> audited cross-stack procedure — see the `cross-stack-dto-granularity-alignment`
> skill, and [[concepts/result-envelope-and-case-mapping]].

## Toolchain pins

Node `^20.19.0 || >=22.12.0`, pnpm 10, each package with its **own** lockfile
(no root install substituting for `console/`/`management/`/`shared/auth-core/`).
Verification commands live in `AGENTS.md` § Verification Matrix.

## Links out

> [!link] Related pages
> - [[overview/architecture-overview]]
> - [[concepts/result-envelope-and-case-mapping]] · [[concepts/theme-system]]
> - [[concepts/sidebar-menu]]