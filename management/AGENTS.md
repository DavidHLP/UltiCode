# Management Frontend AGENTS.md

> **Last Updated**: 2026-05-07
> **Part of**: UltiCode (see root AGENTS.md for project context)

Admin dashboard (port 9003): user management, moderation, audit logs, system monitoring.

## STRUCTURE

```
src/
├── api/admin/*.ts       # Admin API endpoints
├── components/
│   ├── layout/          # AppSidebar, SiteHeader, MainLayout
│   ├── shared/          # BaseDetailDrawer, EntityActionDialog, TagsCard
│   └── problem/         # TestCasesEditor, MarkdownEditor
├── stores/              # Pinia stores (auth)
├── views/               # Route components
│   ├── auth/            # LoginView
│   ├── dashboard/       # DashboardView
│   ├── users/           # User management
│   ├── problems/        # CRUD + tabs (edit/view subdirs)
│   ├── contests/        # Wizard + management
│   ├── forum/           # Forum post management
│   ├── moderation/      # Flag review
│   ├── audit/           # Logs + reports
│   └── system/          # Monitoring, backup, email
├── i18n/locales/{zh-CN,en-US}/modules/  # Per-module translations
├── router/              # Vue Router config
└── utils/request.ts     # apiGet/apiPost
```

## WHERE TO LOOK

| Task | Location |
|------|----------|
| New API | `src/api/admin/{resource}.ts` |
| New route | `src/router/index.ts` |
| Shared components | `src/components/shared/` |
| Problem editor | `src/views/problems/` |
| Contest wizard | `src/views/contests/wizard/` |

## CONVENTIONS

### ESLint
- ESLint 10.x + `eslint-plugin-vue` ~10.8.0
- **`vue/multi-word-component-names`: OFF** — single-word component names allowed

### Shared Code
- Symlink `src/shared -> ../../shared` — import via `@/shared/auth-core/src`
- Uses `createCsrfAxiosInterceptor()` from shared auth-core

### API Pattern
```typescript
import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export const resourceApi = {
  async getList(params) { return apiGet('/admin/resources', { params }) },
  async getOne(id) { return apiGet(`/admin/resources/${id}`) },
  async create(data) { return apiPost('/admin/resources', data) },
  async update(id, data) { return apiPatch(`/admin/resources/${id}`, data) },
  async remove(id) { return apiDelete(`/admin/resources/${id}`) },
}
```

### Auth
- JWT httpOnly cookies + CSRF `X-CSRF-Token` header
- `useAuthStore` (`src/stores/auth.ts`) with `hasPermission()` check

## ANTI-PATTERNS

- **Dev server**: `pnpm dev` runs lint/type-check/format/test first — use `pnpm vite` directly
- **Component names**: Don't enforce multi-word rule — it's disabled here
- **Shared imports**: Use `@/shared/auth-core/src`, not console's duplicated utils
- **Tests**: Vitest with `--passWithNoTests`; `__tests__/` co-located with source
