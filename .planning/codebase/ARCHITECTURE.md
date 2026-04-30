# UltiCode Architecture

## Overview

UltiCode is an online programming platform (LeetCode-like) with a **modular monolith backend** backed by Spring Boot 3.2, two Vue 3 frontend applications, and an optional Dubbo3+Spark recommendation microservice.

## Service Boundaries

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend Layer                           │
├────────────────────────┬────────────────────────────────────────┤
│  Console (9002)        │  Management (9003)                   │
│  User-facing app       │  Admin dashboard                      │
│  Problem solving,      │  User management, audit logs,         │
│  contests, submissions │  content moderation, analytics        │
└────────────┬───────────┴──────────────────┬─────────────────────┘
             │                              │
             └──────────────┬───────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot 9001)                    │
│  Modular monolith - all domain modules in single WAR/JAR         │
│  Dubbo3 RPC for recommendation service calls                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────────────┐
│ MySQL (23306)   │ │ Redis (26379)   │ │ Recommendation Service  │
│ Primary DB      │ │ Cache, Sessions │ │ Dubbo3 + Spark (9004)   │
│ MyBatis-Plus    │ │ Rate Limiting   │ │ Optional, Nacos (28848) │
└─────────────────┘ └─────────────────┘ └─────────────────────────┘
```

## Communication Patterns

### REST API (Frontend ↔ Backend)
- Both Vue 3 frontends communicate with the Spring Boot backend via REST APIs
- Backend runs on port **9001**
- All API responses wrapped in `Result<T>` envelope:
  ```json
  { "code": 0, "message": "success", "data": {...}, "traceId": "t-123" }
  ```
- Frontend `request.ts` unwraps responses automatically

### Session-Based Auth
- JWT tokens stored in **httpOnly cookies** (`access_token`, `refresh_token`)
- CSRF token required for state-changing requests (POST, PUT, PATCH, DELETE)
- Frontend reads CSRF from localStorage, sends as `X-CSRF-Token` header

### Dubbo3 RPC (Backend → Recommendation)
- Backend calls recommendation service via **Dubbo3** RPC over TCP
- Uses Nacos as service registry (port 28848)
- `recommend-api` module defines service interfaces

### WebSocket
- Spring WebSocket for real-time features (judging status, contest updates)

## Data Stores

| Store   | Purpose                           | Port  |
|---------|-----------------------------------|-------|
| MySQL   | Primary database (users, problems) | 23306 |
| Redis   | Sessions, cache, rate limiting     | 26379 |
| Nacos   | Service registry (Dubbo)          | 28848 |

## Backend Modules (Domain-Driven)

Located in `backend-spring/src/main/java/com/ulticode/modules/`:

| Module           | Responsibility                                      |
|------------------|----------------------------------------------------|
| `user`           | User CRUD, profiles, statistics                      |
| `problem`       | Problems, test cases, examples                      |
| `submission`    | Code submissions, judging                           |
| `contest`       | Contests, rankings                                  |
| `forum`         | Discussion forum                                   |
| `solution`      | Solution posts                                     |
| `notification`  | Notification management                             |
| `subscription`   | Problem/contest subscriptions                       |
| `moderation`    | Content moderation                                 |
| `search`        | MeiliSearch integration                            |
| `achievement`   | User achievements                                  |
| `i18n`          | Internationalization                              |
| `vote`           | Upvote/downvote system                            |
| `bookmark`       | Bookmarks                                         |
| `follow`         | User follow relationships                          |
| `auth`           | Authentication, login, OAuth                       |
| `admin`          | Admin operations                                  |
| `recommendation` | Dubbo client for recommendation service            |
| `edgeoperations` | Edge case operations                              |
| `queue`          | Judging queue management                           |
| `refreshtoken`   | Refresh token management                          |
| `websocket`       | WebSocket endpoints                                |
| `backup`         | Database backup                                   |
| `email`          | Email notifications                               |
| `monitoring`     | Health checks, metrics                            |
| `permission`     | Role-based permissions                            |
| `problemlist`    | Problem lists                                     |

## Shared Layer

Located in `shared/`:

| Package        | Purpose                                    |
|----------------|--------------------------------------------|
| `auth-core`    | JWT validation utilities for frontends      |
| `api-utils`    | Shared API utilities                       |
| `types`        | Shared TypeScript type definitions          |

## Deployment Model

### Development (PM2)
```bash
pm2 start ecosystem.config.cjs   # Starts all 5 services
```

### Production
- Frontends containerized with Docker + Nginx
- Backend containerized with Docker
- Docker Compose for local production testing
- Flyway migrations via `db-manager` Python CLI

### Ports Reference

| Service              | Port  |
|----------------------|-------|
| Backend (Spring)     | 9001  |
| Console Frontend     | 9002  |
| Management Frontend   | 9003  |
| Recommend-Provider   | 9004  |
| Recommend-Web        | 9005  |
| MySQL                | 23306 |
| Redis                | 26379 |
| Nacos                | 28848 |

## Technology Stack

| Layer          | Technology                                    |
|----------------|-----------------------------------------------|
| Backend        | Spring Boot 3.2.5, Java 17                   |
| ORM            | MyBatis-Plus 3.5.16                          |
| Auth           | JWT (jjwt 0.13.0), Spring Security           |
| Cache/Locks    | Redisson 4.3.1                               |
| Service Mesh   | Dubbo3 3.2.14, Nacos 2.3.2                  |
| Search         | MeiliSearch                                   |
| Frontends      | Vue 3, Vite, Tailwind CSS v4                 |
| Recommendation | Dubbo3 + Apache Spark (optional)              |
| Database       | MySQL 9.1, Flyway migrations                  |
| Process Mgmt   | PM2                                           |
| Containers     | Docker, Docker Compose                        |
