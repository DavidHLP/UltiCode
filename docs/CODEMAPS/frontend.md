---
title: Frontend Architecture (Vue 3.5 + Vite 8 + Pinia 3 + Tailwind 4)
tags: [reference, frontend, architecture, living]
status: living
updated: 2026-06-19
owner: frontend
generator: ecc:update-codemaps
last_manual_edit: 2026-06-19 add shared/auth-ui package row (ADR-012)
---

# Frontend Architecture (Vue 3.5 + Vite 8 + Pinia 3 + Tailwind 4)

<!-- Generated: 2026-06-19 | Console: 365 files | Management: 348 files | Token estimate: ~900 -->

## Project Layout

```
console/        (port 9002, user-facing, PWA-enabled via vite-plugin-pwa)
management/     (port 9003, admin dashboard, Playwright E2E)
shared/         (auth-core, auth-ui, theme, design-system, badge-config, sandbox-types)
```

Each package has its own `pnpm-lock.yaml` — never install from repo root.

## Console (port 9002) — User-Facing

**Top-level routes** (`console/src/router/`):

| Path                       | View                          |
| -------------------------- | ----------------------------- |
| `/`                        | `LandingView`                 |
| `/auth/{login,register,...}`| `auth/LoginView`, `auth/RegisterView`, etc. |
| `/forum`                   | `forum/ForumFeedView` (+ popular/explore/all/c/:category) |
| `/contest`                 | `contest/ContestHomeView` (+ browse/list/my/rankings/detailed/:id) |
| `/problems/*`              | `problems/code/CodeView`, `description/DescriptionView`, `test/TestCaseView`, `submissions/SubmissionsDetail`, `solutions/*` |
| `/problem-list/:id`        | `problem-list/ProblemListView` (+ analytics) |
| `/personal/*`              | `AccountView`, `BookmarksView`, `ProblemListsView`, `SolutionsView`, `SubmissionsView`, `SubscriptionView`, `ForumPostsView` |
| `/post-editor/solutions/:id` | `post-editor/solutions/SolutionsEditView` |
| `/dashboard`               | `dashboard/PersonalDashboardView` |
| `/achievements`            | (achievement views)           |

**Stores** (`console/src/stores/`): `auth`, `achievement`, `bookmark`, `contest`, `contest/rankingStore`, `contestProblemShell`, `editorSettings`, `headerStore`, `notification`, `problemEditorStore`, `userStats`.

**API surface** (`console/src/api/`, direct `apiGet/apiPost` calls per frontend rule):
`auth`, `achievement`, `bookmark`, `contest` (+ `contest.schema.ts`), `edge-operations`, `follow`, `forum`, `interaction`, `notification`, `problem` (+ `problem-detail`, `problem-list`), `search`, `solution`, `submission`, `subscription`, `topic`, `user`, `vote`.

**Key new components** (R9/typography migration):
- `problems/components/ContestProblemShell.vue` — extracted from problem view (8424883)
- `problems/components/ContestProblemDock.vue`, `ContestAnnouncementBell.vue`, `ContestReviewPanel.vue` (post-game)
- `contest/components/ContestStatusBadge.vue`, `ContestTimer.vue`, `VirtualContestTimer.vue`

## Management (port 9003) — Admin

**Top-level routes** (`management/src/router/`):

| Path                                    | View                              |
| --------------------------------------- | --------------------------------- |
| `/login`                                | `auth/LoginView`                  |
| `/`                                     | `dashboard/DashboardView`         |
| `/users`, `/users/:id`                  | `users/UsersListView`, `UserDetailDrawer` |
| `/audit`, `/audit/report`               | `audit/AuditLogsView`, `AuditReportView` |
| `/analytics`                            | `analytics/AnalyticsView`         |
| `/problems` + `/problems/:id/:tab?` + `/problems/:id/edit/:tab?` + `/problems/create` | full CRUD + tabs (Overview/TestCases/Submissions) |
| `/moderation` + tabs (queue/reports/appeals) | `moderation/ModerationDashboardView` |
| `/contests` + `/contests/wizard/*`      | contest CRUD + step wizard (StepProblems, StepReview) |
| `/forum`                                | `forum/ForumAdminView` (with CommentsTab) |
| `/comments`                             | `comments/CommentDetailView`      |
| `/solutions`, `/solutions/:id`          | `solutions/SolutionDetailView`    |
| `/submissions`                          | `submissions/SubmissionsListView` |
| `/notifications`                        | `notifications/NotificationAdminView` |
| `/billing`, `/settings`, `/system`, `/account`, `/tags` | admin tooling |

**Stores** (`management/src/stores/admin/`): `audit`, `comments`, `contests`, `dashboard`, `forum`, `moderation` (+ `moderationStore`), `notifications`, `problem-lists`, `problems`, `solutions`, `submissions`, `tags`, `users`.

**Typed API wrappers** (`management/src/api/admin/`, per frontend rule): `account`, `analytics`, `audit`, `backup`, `comments`, `contests`, `dashboard`, `email`, `forum`, `moderation`, `monitoring`, `notifications`, `problem-lists`, `problems`, `scoring-rules`, `settings`, `solutions`, `submissions`, `tags`, `test-cases`, `users`.

## Shared Packages (`shared/`)

| Package                | Owner(s)             | Purpose                                       |
| ---------------------- | -------------------- | --------------------------------------------- |
| `auth-core`            | console + management | Cookie/CSRF/auth-state composable + permission helpers + `cn` util |
| `auth-ui`              | console + management | Auth SFC primitives + `AuthLayout` / `AuthPatternBackground` view shells (ADR-012) |
| `theme`                | console + management | DOM theme tokens, typography density (`useTypographyDensity`) |
| `design-system`        | both                 | Shared CSS tokens (typography migration sync 2026-06-19) |
| `badge-config`         | console + management | Achievement/rating badge mappings             |
| `sandbox-types`        | both                 | Typed OJ sandbox DTOs (verdict, test_result)  |

**Workspace**: `pnpm-workspace.yaml` at repo root declares `shared/* + console + management`. `auth-core` and `auth-ui` are real pnpm workspace members with their own `node_modules`; the older `theme / design-system / badge-config / sandbox-types` packages are still consumed via the `console/src/shared -> ../../shared` symlink (legacy). New shared packages should follow the workspace pattern.

**Cross-cutting rule**: any change in `shared/` must verify in BOTH frontends (`pnpm test` + `pnpm type-check` in the package, then console + management).

## Conventions

- `<script setup lang="ts">` + Composition API; defineProps/defineEmits with generics
- Pinia setup-store style; `useXxxStore` exports
- Tailwind v4 via `@tailwindcss/vite`; shadcn-vue / reka-ui / Lucide icons
- i18n via `vue-i18n`; keys `module.feature.text`; column-id `table.columnNames.{id}` (camelCase)
- Prettier: no semicolons, single quotes, 100 char
- `request.ts` is the only HTTP entry — no raw axios
- `theme-bootstrap.js` (in `public/`) is the **only** place that applies theme to DOM (anti-FOUC); no duplicate logic in `main.ts`
