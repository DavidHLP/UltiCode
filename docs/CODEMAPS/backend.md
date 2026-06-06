<!-- Generated: 2026-06-06 | Java files: 667 | Controllers: 43 | Modules: 26 | Token estimate: ~950 -->

# Backend Architecture

## Module Map (26 modules — recommendation removed)

```
modules/
├── achievement/      → /achievements
├── admin/            → /admin/* (15 controllers incl. audit + dashboard)
├── auth/             → /auth
├── backup/           → /admin/backups
├── bookmark/         → /bookmarks
├── contest/          → /contest, /admin/contest, /admin/scoring-rules
├── edgeoperations/   → /edge-operations
├── email/            → /email
├── follow/           → /users (follow endpoints)
├── forum/            → /forum
├── i18n/             → /i18n
├── moderation/       → /moderation
├── monitoring/       → /monitoring
├── notification/     → /notifications
├── permission/       → (entity/service only, no REST)
├── problem/          → /problems, /admin/problems (+ versions)
├── problemlist/      → /problem-lists
├── queue/            → (background job processors, no REST)
├── refreshtoken/     → (entity/service only, no REST)
├── search/           → /search
├── solution/         → /api/solutions
├── submission/       → /submissions, /problems/{id}/submissions
├── subscription/     → /subscriptions, /admin/subscriptions
├── user/             → /users
├── vote/             → /vote
└── websocket/        → (STOMP endpoints, no REST)
```

> **Removed since 2026-05-30**: `recommendation/` module + Dubbo RPC stack +
> `:20881` / `:9005` services. The frontend `recommendation` and `recommend-*`
> API directories remain in console/management but are no longer backed by
> server endpoints — flagged as **orphan / dead code** for follow-up cleanup.

## Controllers (43)

```
admin/        : 15  (Account, Analytics, Comment, Forum, Notification, Problem,
                    ProblemList, Settings, Solution, Submission, Tag, TestCase,
                    User, Audit, Dashboard)
contest/      :  3  (Contest, AdminContest, ScoringRule)
subscription/ :  2  (Subscription, UserSubscription)
submission/   :  2  (Submission, ProblemSubmission)
problem/      :  2  (Problem, AdminProblemVersion)
auth, backup, bookmark, edgeoperations, email, follow, forum, i18n,
moderation, monitoring, notification, problemlist, search, solution,
user, vote :  1 each
```

## Key API Routes

### User-facing
```
POST /auth/login, /auth/register, /auth/refresh, /auth/logout
GET  /problems, /problems/{slug}
GET  /contest, /contest/{slug}
GET  /forum, /forum/posts/{id}
GET  /api/solutions, /api/problems/{id}/solutions
GET  /submissions, /problems/{id}/submissions
POST /bookmarks, /vote, /follow
GET  /edge-operations/interactions, /edge-operations/{type}/{id}
POST /edge-operations
GET  /search
```

### Admin
```
GET/POST /admin/users, /admin/problems, /admin/contest
GET/POST /admin/problem-lists, /admin/solutions, /admin/submissions
GET/POST /admin/forum, /admin/comments, /admin/tags
GET/POST /admin/settings, /admin/notifications, /admin/backups
GET/POST /admin/analytics, /admin/audit, /admin/dashboard
GET/POST /admin/subscriptions
GET  /admin/problems/{id}/versions
GET  /admin/problems/{id}/versions/{versionId}
GET  /admin/problems/{id}/versions/{from}/diff/{to}
POST /admin/problems/{id}/versions/create-initial
POST /admin/problems/{id}/versions/{versionId}/rollback
POST /admin/scoring-rules
```

## Layering

```
Controller → Service → Mapper (MyBatis-Plus) → Entity
                ↓
            MapStruct (DTO ↔ Entity)
```

## Top-level Packages

| Package          | Contents |
| ---------------- | -------- |
| `common/`        | Annotations, aspects, config, constants, DTOs, exceptions, filters, response wrappers, services, utilities |
| `infrastructure/` | Redis service, cache constants |
| `security/`      | JWT, CSRF, OAuth, auth entry point |
| `websocket/`     | Notification WS service, auth interceptor, DTOs |

## WebSocket Endpoints

```
/ws/contest       → contest live updates
/ws/notifications → user notification push
/ws               → generic endpoint
Broker: /topic, /queue, /user
```

## Dependency Versions

| Dependency        | Version |
| ----------------- | ------- |
| Spring Boot       | 3.2.5   |
| MyBatis-Plus      | 3.5.16  |
| MapStruct         | 1.6.3   |
| jjwt              | 0.13.0  |
| Redisson          | 4.3.1   |
| SpringDoc OpenAPI | 2.6.0   |
| Hutool            | 5.8.44  |
| MeiliSearch SDK   | 0.20.0  |
| Testcontainers BOM| 1.21.4  |
