# Console Frontend — AGENTS.md

> User-facing frontend (port 9002) for problem solving, contests, forum, and submissions.

## OVERVIEW

Vue 3 + Vite + Tailwind CSS v4 SPA. Uses shadcn-vue (new-york style) + Radix Vue + Lucide icons. Console-specific design: Solarized theme, KaTeX math, highlight.js Solarized syntax, chart tokens, sharp corners (`--radius: 0`).

## STRUCTURE

```
src/
├── api/              # 21 API modules (problem.ts, contest.ts, forum.ts, etc.)
├── components/ui/    # shadcn-vue components
├── composables/      # Shared composition functions (useCodeTemplates, usePWA, etc.)
├── contexts/         # AuthContext.ts — session expiration callback hub
├── hooks/            # problem-hooks.ts, hookHub.ts
├── i18n/             # en-US + zh-CN locales
├── stores/           # Pinia stores (auth.ts, contest.ts, problemEditorStore.ts, etc.)
├── types/            # auth.ts, shared types
├── utils/            # request.ts (apiGet/apiPost), csrf.ts, markdown.ts, submitQueue.ts
├── views/            # Route-level views organized by feature
│   ├── auth/         # Login, Register, ForgotPassword, ResetPassword
│   ├── contest/      # ContestList, ContestDetail, components/
│   ├── forum/        # ForumFeed, ForumThread, ForumEditor
│   ├── personal/     # Profile, Submissions, Bookmarks, etc.
│   ├── problems/     # ProblemDetail, CodeView, TestResults, submissions/
│   ├── problem-list/ # ProblemListView, ProblemListAnalytics
│   └── recommendations/ # Recommendation views
├── features/sider/    # AppLayout.vue — main layout wrapper
├── pwa-register.ts   # PWA service worker registration
├── main.ts           # Bootstrap: auth init BEFORE router install
└── router/index.ts   # Route definitions + navigation guards
```

## WHERE TO LOOK

| Need | Location |
|------|----------|
| API client | `src/utils/request.ts` — apiGet/apiPost, interceptors, retry logic |
| Auth store | `src/stores/auth.ts` — status machine (idle→loading→ready), httpOnly cookie flow |
| CSRF handling | `src/utils/csrf.ts` — token stored in memory, falls back to cookie on refresh |
| Session expired | `src/contexts/AuthContext.ts` — callback hub, request.ts interceptor triggers redirect |
| API modules | `src/api/*.ts` — one file per domain |
| Router guards | `src/router/index.ts` — auth check, stale session revalidation (5min threshold) |
| Code editor | `src/views/problems/code/CodeView.vue` — Monaco, Solarized theme |
| Markdown render | `src/utils/markdown.ts` + `src/assets/markdown.css` — KaTeX, highlight.js |
| Chart tokens | `src/style.css` — `--chart-status-solved`, `--chart-difficulty-*` |

## CONVENTIONS

- **Auth flow**: httpOnly cookies (access_token), CSRF token in `csrf_token` cookie → read into memory. NOT stored in localStorage. `createCsrfAxiosInterceptor` from `@/shared/auth-core/src` handles token rotation.
- **Bootstrap order**: `auth context init` → `auth store initialize()` → `router install`. This eliminates router guard race conditions.
- **API response unwrap**: `request.ts` auto-unwraps `Result<T>` — returns `response.data` directly. API files return `Promise<T>`.
- **ESLint multi-word rule**: Has whitelist for shadcn components (Accordion, Alert, Avatar, etc.). Does NOT disable the rule globally (unlike management).
- **Dev script trap**: `"dev": "pnpm run lint && pnpm run type-check && pnpm run format && pnpm run test && vite"` — use `pnpm vite` directly.
- **Mock validation**: `pnpm validate:mocks` — runs `scripts/validate-mock-data.mjs` (if script exists) against mock data.
- **Request deduplication**: URLs in `NON_DEDUPLICABLE_URLS` Set (`/auth/me`, `/auth/login`, etc.) skip deduplication.
- **Test co-location**: `__tests__/` folders next to source files, e.g., `utils/__tests__/submitQueue.spec.ts`.
- **CSS**: OKLCH colors only via `oklch()`. Theme via `.dark` class on root. No hex/HSL anywhere in CSS.
- **Charts**: Use chart tokens (`--chart-1` through `--chart-5`, `--chart-status-*`, `--chart-difficulty-*`).

## ANTI-PATTERNS

- **Do NOT import `@/shared/auth-core/src` directly** — console duplicates auth utilities locally in `src/utils/csrf.ts` and `src/stores/auth.ts`.
- **Do NOT assume router guard will wait for auth** — use `authStore.initializationPromise` if needed during navigation.
- **Do NOT use `vue/multi-word-component-names` disabled globally** — it has an explicit whitelist in `eslint.config.ts`. Add components to the whitelist if needed.
- **Do NOT use TypeScript ~6.x** — package.json pins `typescript: ~6.0.3`. ESLint 9.x + `eslint-plugin-vue` ^9.30.0 is required (10.x breaks).
