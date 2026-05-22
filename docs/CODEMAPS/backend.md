<!-- Generated: 2026-05-23 | Files scanned: 605 | Token estimate: ~950 -->

# Backend Architecture

## Module Map (27 modules)

```
modules/
├── achievement/     → /achievements
├── admin/           → /admin/* (14 controllers)
├── auth/            → /auth
├── backup/          → /admin/backups
├── bookmark/        → /bookmarks
├── contest/         → /contest, /admin/contest, /admin/scoring-rules
├── edgeoperations/  → /edge-operations
├── email/           → /email
├── follow/          → /users (follow endpoints)
├── forum/           → /forum
├── i18n/            → /i18n
├── moderation/      → /moderation
├── monitoring/      → /monitoring
├── notification/    → /notifications
├── permission/      → (no REST API, entity/service only)
├── problem/         → /problems, /admin/problems (+ versions)
├── problemlist/     → /problem-lists
├── queue/           → (no REST API, background job processors)
├── recommendation/  → /recommendations, /recommendations/admin
├── refreshtoken/    → (no REST API, entity/service only)
├── search/          → /search
├── solution/        → /api/solutions
├── submission/      → /submissions, /problems/{id}/submissions
├── subscription/    → /subscriptions, /admin/subscriptions
├── user/            → /users
├── vote/            → /vote
└── websocket/       → (STOMP endpoints, no REST)
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
GET  /recommendations, /recommendations/daily
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

| Package | Contents |
|---------|----------|
| `common/` | Annotations, aspects, config, constants, DTOs, exceptions, filters, response wrappers, services, utilities (43 files) |
| `infrastructure/` | Redis service, cache constants (2 files) |
| `security/` | JWT, CSRF, OAuth, auth entry point (7 files) |
| `websocket/` | Notification WS service, auth interceptor, DTOs (4 files) |

## WebSocket Endpoints

```
/ws/contest       → contest live updates
/ws/notifications → user notification push
/ws               → generic endpoint
Broker: /topic, /queue, /user
```

## Dependency Versions

| Dependency | Version |
|-----------|---------|
| Spring Boot | 3.2.5 |
| MyBatis-Plus | 3.5.16 |
| MapStruct | 1.6.3 |
| jjwt | 0.13.0 |
| Redisson | 4.3.1 |
| SpringDoc OpenAPI | 2.6.0 |
| Dubbo | 3.2.14 |
| Hutool | 5.8.44 |
| MeiliSearch SDK | 0.20.0 |
