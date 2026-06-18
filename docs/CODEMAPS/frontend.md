<!-- Generated: 2026-06-18 | Console: ~351 Vue / ~217 TS | Management: ~470 Vue / ~265 TS | Token estimate: ~960 -->

# Frontend Architecture

## Console (:9002) — User-facing

### Route Tree
```
/, /login, /register, /forgot-password, /reset-password
/problems/:slug/:tab?
/problem/:id/solution/create
/problemset, /problemset/:category, /problemset/list/:id
/contest, /contest/browse, /contest/browse/past, /contest/my
/contest/rankings, /contest/:slug
/forum, /forum/popular, /forum/explore, /forum/all
/forum/c/:category, /forum/detailed/:postId
/forum/guidelines, /forum/feedback
/forum/create, /forum/edit/:postId
/post-editor/solution/create
/solutions/:id/edit
/personal, /personal/account, /personal/submissions
/personal/solution, /personal/problem-lists, /personal/bookmarks
/personal/forum-posts, /personal/notifications
/personal/achievements, /personal/dashboard, /personal/subscription
/users/:id, /profile/:username
```

### Stores (11 + contest subfolder)
```
stores/
├── auth.ts, achievement.ts, bookmark.ts, notification.ts
├── editorSettings.ts, problemEditorStore.ts
├── userStats.ts, headerStore.ts
├── contest/             (subfolder)
│   ├── index.ts
│   ├── rankingStore.ts
│   └── __tests__/
└── __tests__/  (auth.spec.ts, editorSettings.spec.ts)
```

### API Modules (21)
```
api/
├── achievement.ts, auth.ts, bookmark.ts, contest.ts, contest.schema.ts
├── edge-operations.ts, follow.ts, forum.ts, interaction.ts
├── notification.ts, problem.ts, problem-detail.ts, problem-list.ts
├── search.ts, solution.ts, submission.ts, subscription.ts
├── topic.ts, user.ts, userStats.ts, vote.ts
└── __tests__/  (auth.spec.ts, problem-detail.spec.ts)
```

### features/ (cross-view layout components)
```
features/
├── sider/   AppLayout.vue, AppSidebar.vue, SidebarNav.vue, NavUser.vue,
│            Calendars.vue + components/   [AppLayout/SidebarInset refactored 2026-06-12]
└── layout/  panels/ (LayoutPanel, PanelDropOverlay, LayoutPanelHeader,
            LayoutPanelContent) + tree/ (LayoutTree, LayoutTreeNode)
```

### Recent Console Changes (2026-06-10 → 2026-06-12)
```
M  components/common/data-table/DataTableToolbar.vue
M  components/problem/ProblemSetSidebar.vue
M  components/ui/sidebar/SidebarInset.vue
M  features/sider/AppLayout.vue
M  views/personal/components/LearningProgressChart.vue
M  views/personal/components/SkillRadarChart.vue
M  views/personal/components/SubmissionHistoryChart.vue
M  views/personal/components/UserProfileCard.vue
M  views/personal/components/UserStatsPanel.vue
M  i18n/locales/{en-US,zh-CN}/personal.ts
?? components/common/data-table/__tests__/           [new test suite]
?? components/ui/sidebar/__tests__/                  [new test suite]
```

### Key Dependencies
| Dep              | Version  | Notes                                  |
| ---------------- | -------- | -------------------------------------- |
| vue              | ^3.5.34  | Composition API + `<script setup>`     |
| vite             | ^8.0.14  | Build / dev server                     |
| pinia            | ^3.0.4   | State                                  |
| vue-router       | ^5.0.7   | Routing                                |
| tailwindcss      | ^4.3.0   | `@tailwindcss/vite` plugin             |
| vue-i18n         | ^11.4.4  | i18n                                   |
| axios            | ^1.13.2  | HTTP                                   |
| @vueuse/core     | ^14.1.0  | Composables                            |
| dompurify        | ^3.4.5   | HTML sanitization (UGC)                |
| echarts          | ^6.1.0   | Charts (analytics, contest rankings)   |
| @vue-dnd-kit/core| 1.7.0    | Drag-and-drop (problem editor)         |
| @monaco-editor/loader | ^1.7.0 | Code editor                          |
| @stomp/stompjs   | ^7.3.0   | WebSocket STOMP client                 |
| @tanstack/vue-virtual | ^3.13 | Virtualized lists                    |
| @unovis/ts+vue   | ^1.6.2   | Data viz                               |
| vite-plugin-pwa  | +workbox | PWA support                            |
| vitest           | ^4.1.7   | Unit tests                             |
| eslint           | ^10.4.0  | Lint                                   |
| typescript       | ~6.0.3   | Type check                             |

### Scripts
| Command | Purpose |
| --- | --- |
| `pnpm dev` | lint + type-check + format + test + vite |
| `pnpm verify:theme-sync` | Verify `console/public/theme-bootstrap.js` matches `shared/theme` |

---

## Management (:9003) — Admin

### Route Tree
```
/, /login, /signup, /dashboard
/users, /audit, /audit/report
/analytics, /problems, /problems/create, /problems/:id/:tab?, /problems/:id/edit/:tab?
/problem-lists, /problem-lists/create, /problem-lists/:id/edit
/solutions, /solutions/:id/:tab?, /comments
/forum, /forum/posts, /forum/posts/:id/:tab?, /forum/comments/:id
/tags, /contests, /contests/:id, /scoring-rules
/submissions, /moderation, /moderation/dashboard
/moderation/reports, /moderation/appeals
/notifications, /system, /billing, /email
/account, /settings
```

### Stores (1 + admin subfolder with 13 sub-stores)
```
stores/
├── auth.ts
└── admin/
    ├── audit.ts, comments.ts, contests.ts, dashboard.ts
    ├── forum.ts, moderation.ts, notifications.ts
    ├── problem-lists.ts, problems.ts, solutions.ts
    ├── submissions.ts, tags.ts, users.ts
    ├── moderation/
    │   ├── index.ts
    │   └── moderationStore.ts (16.7K — largest)
    └── __tests__/
```

### API Modules (22 in admin/, 1 root)
```
api/
├── auth.ts
└── admin/
    ├── account.ts, analytics.ts, audit.ts, backup.ts
    ├── comments.ts, contests.ts, dashboard.ts, email.ts
    ├── forum.ts, moderation.ts, monitoring.ts, notifications.ts
    ├── problem-lists.ts, problems.ts, scoring-rules.ts
    ├── settings.ts, solutions.ts, submissions.ts
    ├── tags.ts, test-cases.ts, users.ts
    └── __tests__/  (4 test files)
```

### Key Dependencies
| Dep              | Version  | Notes                                  |
| ---------------- | -------- | -------------------------------------- |
| vue              | ^3.5.34  |                                        |
| vite             | ^8.0.14  |                                        |
| pinia            | ^3.0.4   |                                        |
| vue-router       | ^5.0.4   |                                        |
| tailwindcss      | ^4.3.0   |                                        |
| vue-i18n         | ^11.4.4  | Plus `validate:i18n-keys` script       |
| axios            | ^1.16.1  |                                        |
| @vueuse/core     | ^14.3.0  |                                        |
| dompurify        | ^3.4.5   |                                        |
| @dnd-kit/abstract+dom+modifiers | ^0.1.21 / ^9.0 | Drag-and-drop |
| @tanstack/vue-table | ^8.21 | Data tables                          |
| @unovis/ts+vue   | ^1.6.5   |                                        |
| @vee-validate/zod| ^4.15    | Form validation (zod schema)           |
| @tabler/icons-vue| ^3.44    | Icon set                               |
| date-fns         | ^4.3     | Date utils                             |
| @mdit/plugin-katex | ^0.25  | KaTeX math rendering                   |
| vitest           | ^4.1.7   |                                        |
| eslint           | ^10.4.0  |                                        |
| typescript       | ~6.0.3   |                                        |
| Playwright       | latest   | E2E (management only)                  |

### Scripts
| Command | Purpose |
| --- | --- |
| `pnpm validate:i18n-keys` | Validate i18n key consistency across locales |
| `pnpm check:i18n` | Type-aware i18n key checker |
| `pnpm verify:theme-sync` | Verify `management/public/theme-bootstrap.js` matches `shared/theme` |

---

## Shared packages

| Package             | Purpose                                                |
| ------------------- | ------------------------------------------------------ |
| `shared/auth-core/` | Cookie utils, CSRF manager, auth state, permission check |
| `shared/badge-config/` | Achievement / badge token configuration             |
| `shared/theme/`     | Theme tokens + `applyThemeToDOM` + source of `public/theme-bootstrap.js` |
| `shared/design-system/` | Legacy: `style.css` only — consolidated under `shared/theme` |
