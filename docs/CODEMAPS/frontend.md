<!-- Generated: 2026-05-19 | Files scanned: 1257 | Token estimate: ~950 -->

# Frontend Architecture

## Console (User SPA, :9002)

### Page Tree

```
/ → /forum (home)
├── /login, /register, /forgot-password, /reset-password
├── /forum
│   ├── /popular, /explore, /all, /c/:category
│   ├── /detailed/:postId (thread)
│   ├── /create, /edit/:postId
│   ├── /guidelines, /feedback
├── /problemset
│   ├── /:category
│   └── /list/:id (problem-list detail)
├── /problems/:slug/:tab?
│   ├── description, code, test, solutions, submissions
├── /contest
│   ├── /past, /my, /global-ranking, /local-ranking
│   └── /:slug (contest detail + ranking)
├── /personal
│   ├── /account, /submissions, /solutions, /problem-lists
│   ├── /bookmarks, /forum-posts, /notifications
│   ├── /achievements, /dashboard, /subscription
├── /recommendations
│   ├── /daily, /weak-points, /challenge, /similar
├── /users/:id, /profile/:username
├── /problem/:id/solution/create, /solutions/:id/edit
```

### State Management (Pinia)

| Store | Domain |
|-------|--------|
| auth | User auth state, token, permissions |
| contest | Contest list + detail, ranking |
| notification | Real-time notifications |
| achievement | Achievement progress |
| recommendation | Recommended problems |
| bookmark | Folders, items |
| editorSettings | Code editor preferences |
| problemEditorStore | Problem editor state |
| userStats | User statistics |
| headerStore | Header UI state |

### Key Composables

`useBreakpoints`, `useRetry`, `useLoading`, `useSocket`, `useFollowStatus`, `useLocale`, `useSearch`, `useCodeCache`, `useCodeTemplates`, `usePWA`, `useErrorHandler`, `useNetworkStatus`, `useGlobalShortcuts`, `useAvatar`, `useContestSocket`

### API Layer

`console/src/api/` — one module per domain: auth, problem, problem-detail, contest, forum, solution, submission, user, vote, search, bookmark, notification, achievement, recommendation, follow, subscription, edge-operations, interaction, topic, userStats

---

## Management (Admin SPA, :9003)

### Page Tree

```
/ → /dashboard
├── /login, /signup
├── /users, /users/create, /users/:id
├── /problems, /problems/create, /problems/:id/:tab?, /problems/:id/edit/:tab?
├── /problem-lists, /problem-lists/:id/edit
├── /solutions, /solutions/:id/:tab?
├── /comments, /forum/comments/:id
├── /forum/posts, /forum/posts/:id/:tab?
├── /contests, /contests/:id, /scoring-rules
├── /submissions
├── /moderation, /moderation/dashboard, /moderation/reports, /moderation/appeals
├── /notifications, /notifications/create
├── /tags
├── /audit, /audit/report
├── /analytics
├── /settings
├── /monitoring, /backup, /email
├── /account, /billing
```

### Admin Stores

`stores/admin/` — dashboard, users, problems, submissions, contests, solutions, forum, comments, notifications, moderation, problem-lists, tags, audit

### Permission System

`constants/permissions.ts` defines `PERM` object. Routes use `meta.permission` for access control.

---

## Shared: `shared/auth-core`

Vue composable library: auth state management, CSRF token handling (double-submit cookie), permission checking, cookie utilities. Published as internal package, consumed by both frontends.
