# Console Frontend AGENTS.md

> **Part of**: UltiCode (see [root AGENTS.md](../AGENTS.md) for project context)
> **Last Updated**: 2026-07-06

User-facing Vue 3 application (port 9002). PWA-enabled, light/dark/system themes, i18n (zh-CN/en-US).

## STRUCTURE

```
src/
├── main.ts                # Bootstrap: theme → Pinia → i18n → auth → router → mount
├── App.vue                # RouterView + Toaster + PWAUpdatePrompt
├── router/index.ts        # 431 lines, lazy routes, auth guard, stale-session revalidation
├── api/                   # ~20 API modules — direct apiGet/apiPost calls
├── types/                 # TypeScript types mirroring backend DTOs
├── stores/                # Pinia stores (Composition API / setup store style)
├── composables/           # ~16 composables (useEditorThemes, useContestSocket, usePWA…)
├── views/                 # Route components
│   ├── auth/              # Login, Register, PasswordReset
│   ├── problems/          # ProblemList, ProblemDetail, submissions
│   ├── contests/          # Contest list, detail, standings
│   ├── forum/             # Forum platform, threads, posts
│   ├── personal/          # Dashboard, submissions, achievements
│   └── problemset/        # Problem set categories
├── components/
│   ├── layout/            # AppLayout (sider wrapper, conditional)
│   ├── ui/                # shadcn-vue primitives (sidebar/, dropdown-menu/, …)
│   └── [domain]/          # Feature-specific components
├── contexts/              # React-style context providers for auth
├── features/              # Feature flags / feature-gated modules
├── hooks/                 # Low-level hooks (distinct from composables/)
├── i18n/                  # vue-i18n config + locales/{zh-CN,en-US}/
├── utils/                 # request.ts (axios factory), helpers
├── lib/                   # Third-party wrapper configs
├── constants/             # App-wide constants
├── shared/                # → symlink to ../../shared (10 packages)
└── assets/ · ico/         # Static assets, icons
```

## WHERE TO LOOK

| Task | Location |
|------|----------|
| New API endpoint | `src/api/<resource>.ts` — use `apiGet`/`apiPost` from `@/utils/request` |
| New route | `src/router/index.ts` — lazy-loaded, add to appropriate section |
| New Pinia store | `src/stores/<name>.ts` — `camelCase.ts`, export `useXxxStore` |
| New composable | `src/composables/use<Name>.ts` |
| Shared component | Check `@/shared/` (symlink) first — extract if duplicated in management |
| Theme config | `@/shared/theme/src/` — never set `data-theme` directly in components |
| Auth flow | `src/contexts/` + `src/stores/auth.ts` + `@/shared/auth-core/src/` |
| Markdown rendering | `@/shared/markdown-utils/src/` — always `renderMarkdown()`, never raw `v-html` |
| i18n keys | `src/i18n/locales/{zh-CN,en-US}/` — must add to BOTH languages |

## CONVENTIONS

### API Pattern (Console differs from Management)
```typescript
// Console: direct apiGet/apiPost
import { apiGet, apiPost } from '@/utils/request'
const data = await apiGet('/problems', { params })
```
Management uses typed `xxxApi.method()` wrappers; Console uses direct calls.

### Component Rules
- `<script setup lang="ts">` mandatory — no Options API
- Props: `defineProps<{ title: string }>()`
- Emits: `defineEmits<{ update: [value: string] }>()`
- ESLint: `vue/multi-word-component-names` is **enforced** with a large allowlist
  (Accordion, Alert, Avatar, Badge, … ~40 UI primitives whitelisted)

### Auth Bootstrap
`main.ts` initializes auth **before** router install — eliminates router-guard race.
Sequence: `initTheme → createApp → Pinia → i18n → AuthContext.initialize() → router → mount`

### Layout Pattern
Console uses `AppLayout.vue` (sider) as a **conditional** wrapper — not every route has a sidebar.
This differs from Management which always wraps in `MainLayout.vue`.

### Density
Console defaults to `comfortable` (long-form reading surface). Management uses `compact`.

## ANTI-PATTERNS

- **Never import `HiddenCasesView`** — management-only component, leaks hidden test case data
- **Never set `data-theme` attribute directly** — only the theme system (`shared/theme`) writes it
- **Never call `markdown-it` directly or bypass DOMPurify** — use `renderMarkdown()` from shared
- **Never call `useThemeForceUpdate` in production code** — test utility only
- **Never use typed `xxxApi.method()` wrappers** — that's Management's pattern; Console uses direct calls
- **No MSW (Mock Service Worker)** — API tests mock `@/utils/request` via `vi.mock()`

## COMMANDS

```bash
pnpm dev              # lint + type-check + format + test + vite (full pipeline before dev server)
pnpm vite             # vite only (skip lint/test — use for hot-reload editing)
pnpm build            # type-check + vite build
pnpm type-check       # vue-tsc --build
pnpm lint             # eslint . --fix --cache
pnpm format           # prettier --write src/
pnpm test             # vitest --run --passWithNoTests --exclude '**/auth-core/**'
pnpm test:coverage    # vitest --coverage
```

## NOTES

- **64 test files** — `__tests__/` co-located with source, `*.spec.ts` pattern
- **Test excludes**: `**/auth-core/**` and `**/shared/theme/**` (run separately in shared packages)
- **No E2E tests** — only Management has Playwright specs
- **Shared packages** consumed via `src/shared` → `../../shared` symlink + `@/shared/*` path alias
- **tsconfig**: app extends `@vue/tsconfig/tsconfig.dom.json`; node extends `@tsconfig/node22`
- **Stale-session revalidation**: 5-min threshold on protected routes
- **Navigation abort**: monotonically increasing `pendingNavigationId` prevents race conditions
