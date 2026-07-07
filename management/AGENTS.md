# Management Frontend AGENTS.md

> **Part of**: UltiCode (see [root AGENTS.md](../AGENTS.md) for project context)
> **Last Updated**: 2026-07-06

Admin dashboard (port 9003): user management, moderation, audit logs, system monitoring, contest/problem management.

## STRUCTURE

```
src/
├── main.ts                          # Bootstrap: theme → Pinia → i18n → auth → router → mount
├── App.vue                          # RouterView + Toaster (thinner than console)
├── router/index.ts                  # Flat child routes under MainLayout, permission-gated
├── api/admin/*.ts                   # ~22 typed API modules (xxxApi.method() pattern)
├── stores/                          # Pinia stores (auth + admin/)
├── views/
│   ├── auth/                        # LoginView
│   ├── dashboard/                   # DashboardView (analytics, charts)
│   ├── users/                       # User management (roles, ban, audit)
│   ├── problems/                    # CRUD + tabs (edit/view subdirs), components/
│   ├── contests/                    # Wizard + management
│   ├── forum/                       # Forum post management
│   ├── moderation/                  # Flag review, reports, appeals
│   ├── audit/                       # Logs + reports
│   └── system/                      # Monitoring, backup, email, settings
├── components/
│   ├── layout/                      # AppSidebar, SiteHeader, MainLayout (always visible)
│   ├── shared/                      # BaseDetailDrawer, EntityActionDialog, TagsCard
│   ├── ui/                          # shadcn-vue (sidebar/, menubar/, context-menu/, dropdown-menu/)
│   └── problem/                     # TestCasesEditor, MarkdownEditor, HiddenCasesView
├── i18n/locales/{zh-CN,en-US}/modules/  # Per-module translations (26 modules each)
├── e2e/                             # Playwright specs (problem-list-edit.spec.ts)
└── utils/request.ts                 # apiGet/apiPost factory
```

## WHERE TO LOOK

| Task | Location |
|------|----------|
| New admin API | `src/api/admin/<resource>.ts` — typed `xxxApi.method()` wrapper |
| New route | `src/router/index.ts` — flat children under MainLayout, add `permission: PERM.XXX` meta |
| New permission gate | Define in `PERM` constants, add to route meta `permission` field |
| Shared components | `src/components/shared/` (BaseDetailDrawer, EntityActionDialog) |
| Problem editor | `src/views/problems/` + `src/components/problem/` |
| Contest wizard | `src/views/contests/wizard/` |
| DataTable i18n | `src/i18n/locales/*/modules/table.ts` — both camelCase and snake_case keys |
| E2E test | `src/e2e/` — Playwright |

## CONVENTIONS

### API Pattern (Management differs from Console)
```typescript
// Management: typed wrapper functions
import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export const resourceApi = {
  async getList(params) { return apiGet('/admin/resources', { params }) },
  async getOne(id) { return apiGet(`/admin/resources/${id}`) },
  async create(data) { return apiPost('/admin/resources', data) },
  async update(id, data) { return apiPatch(`/admin/resources/${id}`, data) },
  async remove(id) { return apiDelete(`/admin/resources/${id}`) },
}
```
Console uses direct `apiGet/apiPost`; Management wraps in typed objects.

### Permission Gates
Routes use declarative permission metadata:
```typescript
{ path: 'problems', meta: { permission: PERM.PROBLEM_READ } }
```
Checked via `hasPermission(action, resource)` from `useAuthStore`.

### DataTable i18n (Critical)
`DataTable.vue` uses `t(\`table.columnNames.${column.id}\`)` where `column.id` matches the API
field name (camelCase). The i18n key file `src/i18n/locales/*/modules/table.ts` must define
**both** camelCase and snake_case keys for each column.

### ESLint
- ESLint 10.x + `eslint-plugin-vue` ~10.8.0
- **`vue/multi-word-component-names`: OFF** — single-word component names allowed
  (differs from Console which enforces it with an allowlist)

### Layout
Management uses `MainLayout.vue` with sidebar + header **always visible**.
Console conditionally wraps routes in `AppLayout.vue`.

### Density
Management defaults to `compact` (tables, audit logs). Console uses `comfortable`.

### Auth
- JWT HttpOnly cookies + CSRF `X-CSRF-Token` header
- `useAuthStore` (`src/stores/auth.ts`) with `hasPermission(action, resource)` check
- Uses `createCsrfAxiosInterceptor()` from `@/shared/auth-core/src`

## ANTI-PATTERNS

- **`pnpm dev` runs full pipeline** (lint/type-check/format/test before Vite) — use `pnpm vite` for hot-reload only
- **Don't enforce multi-word component names** — ESLint rule is OFF here
- **Use `@/shared/auth-core/src`**, not console's duplicated utils
- **Tests**: Vitest with `--passWithNoTests`; `__tests__/` co-located with source
- **Don't import management-only components into console** (e.g., `HiddenCasesView`)
- **Ghost types**: API files may define types without backend endpoints (`UserWarning` etc.) —
  treat as dead code until endpoint exists; do not delete
- **Never skip `validate:i18n-keys`** before merge — CI enforces i18n completeness

## COMMANDS

```bash
pnpm dev                    # lint + type-check + format + test + vite
pnpm vite                   # vite only (hot-reload)
pnpm build                  # type-check + vite build
pnpm type-check             # vue-tsc --build
pnpm lint                   # eslint . --fix --cache
pnpm test                   # vitest --run --passWithNoTests --exclude '**/shared/theme/**'
pnpm validate:i18n-keys     # i18n key completeness check (management-only)
pnpm check:i18n             # node --import jiti/register src/i18n/check.ts
```

## NOTES

- **39 test files** — `__tests__/` co-located, `*.spec.ts` pattern
- **1 Playwright E2E** — `src/e2e/problem-list-edit.spec.ts`
- **i18n validation**: `pnpm validate:i18n-keys` runs in CI for management (not console)
- **tsconfig**: app extends `@vue/tsconfig/tsconfig.dom.json`; node extends `@tsconfig/node24`
  (console uses `@tsconfig/node22`)
- **Shared symlink**: `src/shared → ../../shared` — import via `@/shared/auth-core/src`
- **Backend DTO enums**: backend fields use `String`, frontend uses TS enum — known mismatch,
  new code should push for backend enum-ification
