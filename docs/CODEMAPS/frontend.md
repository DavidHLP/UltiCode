<!-- Generated: 2026-05-23 | Files scanned: 1249 | Token estimate: ~900 -->

# Frontend Architecture

## Console (:9002) — User-facing

### Route Tree
```
/, /login, /register, /forgot-password, /reset-password
/problems/:slug/:tab?
/problem/:id/solution/create
/problemset, /problemset/:category, /problemset/list/:id
/contest, /contest/past, /contest/my
/contest/global-ranking, /contest/local-ranking, /contest/:slug
/forum, /forum/popular, /forum/explore, /forum/all
/forum/c/:category, /forum/detailed/:postId
/forum/guidelines, /forum/feedback
/forum/create, /forum/edit/:postId
/post-editor/solution/create
/solutions/:id/edit
/recommendations, /recommendations/daily
/recommendations/weak-points, /recommendations/challenge, /recommendations/similar
/personal, /personal/account, /personal/submissions
/personal/solution, /personal/problem-lists, /personal/bookmarks
/personal/forum-posts, /personal/notifications
/personal/achievements, /personal/dashboard, /personal/subscription
/users/:id, /profile/:username
```

### Stores (10)
```
stores/
├── auth.ts, achievement.ts, bookmark.ts, notification.ts
├── recommendation.ts, editorSettings.ts, problemEditorStore.ts
├── userStats.ts, headerStore.ts
└── contest/
    ├── contest.ts (primary, consolidated)
    └── rankingStore.ts
```

### API Modules (21)
```
api/
├── auth.ts, bookmark.ts, follow.ts, forum.ts, interaction.ts
├── notification.ts, problem.ts, problem-detail.ts, problem-list.ts
├── recommendation.ts, search.ts, solution.ts
├── submission.ts, subscription.ts, topic.ts, user.ts
├── userStats.ts, vote.ts, achievement.ts
├── contest.ts, edge-operations.ts
└── __tests__/ (auth.spec.ts, problem-detail.spec.ts)
```

### Key Dependencies
| Dep | Version |
|-----|---------|
| vue | ^3.5.25 |
| vite | ^8.0.8 |
| pinia | ^3.0.4 |
| vue-router | ^5.0.4 |
| tailwindcss | ^4.1.17 |
| vue-i18n | ^11.3.2 |
| vitest | ^4.1.4 |
| eslint | ^9.30.1 |
| typescript | ~6.0.3 |

---

## Management (:9003) — Admin

### Route Tree
```
/, /login, /dashboard, /users
/problems, /problems/create, /problems/:id/:tab?, /problems/:id/edit/:tab?
/problem-lists, /problem-lists/create, /problem-lists/:id/edit
/solutions, /solutions/:id/:tab?, /comments
/forum, /forum/posts, /forum/posts/:id/:tab?, /forum/comments/:id
/tags, /contests, /contests/:id, /scoring-rules
/submissions, /moderation, /moderation/dashboard
/moderation/reports, /moderation/appeals
/notifications, /audit, /audit/report
/analytics, /settings, /monitoring, /backup
/email, /account, /billing
```

### Stores (1)
```
stores/
└── auth.ts
```

### API Modules (23)
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
    └── __tests__/ (4 test files)
```

### Key Dependencies
| Dep | Version |
|-----|---------|
| vue | ^3.5.33 |
| vite | ^8.0.8 |
| pinia | ^3.0.4 |
| eslint | ^10.2.1 |
| typescript | ~6.0.3 |
| Playwright | E2E testing |

---

## Shared: auth-core

- `src/index.ts` — exports: types, cookie utils, CSRF manager, auth state machine, axios CSRF interceptor, permission checker
- TS ~5.9.3, pkg v0.0.1
