# UltiCode Architecture

## Overview

UltiCode is an online programming platform (LeetCode-like) with a **modular monolith backend** backed by Spring Boot 3.5, two Vue 3 frontend applications, and an optional Dubbo3+Spark recommendation microservice.

## Service Topology

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client Layer                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────┐         ┌──────────────────────┐                  │
│  │   Console (9002)     │         │  Management (9003)   │                  │
│  │   Vue 3 + Vite       │         │  Vue 3 + Vite       │                  │
│  │   User-facing App    │         │  Admin Dashboard    │                  │
│  └──────────┬───────────┘         └──────────┬───────────┘                  │
│             │                                   │                              │
│             │   HTTP/REST + WebSocket           │                              │
│             │   (withCredentials, CSRF)        │                              │
│             └───────────────┬───────────────────┘                              │
│                               │                                                │
└───────────────────────────────┼────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Backend (9001)                                     │
│                    Spring Boot 3.5 + MyBatis-Plus                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                     Security Layer                                     │    │
│  │  JWT Auth (httpOnly cookies) → SecurityContext → CSRF Validation   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                   Controller Layer                                     │    │
│  │  auth │ user │ problem │ submission │ contest │ forum │ solution   │    │
│  │  notification │ subscription │ vote │ bookmark │ recommendation  │    │
│  │  achievement │ admin │ search │ moderation │ websocket           │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                               │                                              │
│                               ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    Service Layer                                      │    │
│  │  Business logic, transaction management, caching coordination          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                               │                                              │
│                               ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                   Mapper Layer (MyBatis-Plus)                        │    │
│  │  CRUD operations, complex queries, batch operations                   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │    MySQL     │  │    Redis     │  │  WebSocket   │  │    Dubbo     │   │
│  │  (23306)     │  │  (26379)     │  │  (STOMP)     │  │   (Nacos)   │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                │
                                │ Dubbo3 RPC (optional)
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                  Recommendation Service (9004/9005)                              │
│                    Dubbo3 + Spark Microservices                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  recommend-provider (9004)    │    recommend-web (9005)                  │
│  - Spark ML processing         │    - REST API for recommendations        │
│  - Algorithm execution         │    - Nacos service discovery              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Communication Patterns

### REST API (Frontend ↔ Backend)

Both Vue 3 frontends communicate with the Spring Boot backend via REST APIs on port **9001**.

**API Response Envelope** — All responses wrapped in `Result<T>`:
```json
{ "code": 0, "message": "success", "data": {...}, "traceId": "t-1234567890" }
```

- `code: 0` = success, non-zero = error
- Frontend `request.ts` unwraps responses automatically
- CSRF token required for state-changing requests

### Session-Based Authentication

- JWT tokens stored in **httpOnly cookies** (`access_token`, `refresh_token`)
- CSRF token issued on login (response body), stored in frontend memory
- Sent via `X-CSRF-Token` header for POST/PUT/PATCH/DELETE requests
- Token validation flow:
  1. JWT filter extracts token from cookie or `Authorization: Bearer` header
  2. Token validated, user info extracted
  3. `SecurityContext` populated with `ROLE_*` authorities
  4. `@PreAuthorize` annotations enforce method-level authorization

### Dubbo3 RPC (Backend → Recommendation)

- Backend calls recommendation service via **Dubbo3** RPC over TCP
- Uses Nacos as service registry (port 28848)
- `recommend-api` module defines service interfaces
- `recommend-provider` implements Dubbo service
- `recommend-web` exposes REST API fallback

### WebSocket (Real-Time)

- Spring WebSocket with STOMP protocol
- Endpoints: `/ws/**`
- Used for: judging status push, contest live updates, notifications
- Authentication via WebSocket handshake (JWT token in params or headers)

## Data Stores

| Store   | Purpose                              | Port  |
|---------|--------------------------------------|-------|
| MySQL   | Primary DB (users, problems, submissions) | 23306 |
| Redis   | Sessions, cache, rate limiting, queues | 26379 |
| Nacos   | Service registry (Dubbo)            | 28848 |

## Backend Modules

Located in `backend-spring/src/main/java/com/ulticode/modules/`:

| Module            | Responsibility                                          |
|------------------|--------------------------------------------------------|
| `auth`           | Login, register, OAuth (GitHub/Google), token refresh |
| `user`           | User profile, stats, avatar, password management      |
| `problem`        | Problems CRUD, publishing, tags, difficulty             |
| `submission`     | Code submissions, judge queue, results, history        |
| `contest`        | Contests, rankings, participation                     |
| `forum`          | Posts, comments, communities, tags                    |
| `solution`       | Official solutions, user solutions, pinned status       |
| `notification`   | Notifications, subscriptions                           |
| `subscription`   | Problem/contest subscriptions, digest                  |
| `vote`           | Upvote/downvote for posts and solutions               |
| `bookmark`       | User bookmarks                                         |
| `achievement`    | User achievements, badges                             |
| `follow`        | User follow relationships                              |
| `recommendation` | Dubbo client for recommendation service              |
| `search`         | MeiliSearch integration                              |
| `moderation`     | Content flagging, review queue                        |
| `admin`          | Admin-only operations, analytics                       |
| `websocket`       | Real-time notifications, contest live updates         |
| `queue`          | Async job processing (judge, email, notifications)     |
| `backup`         | Database backup operations                             |
| `email`          | Email sending (password reset, notifications)         |
| `i18n`           | Internationalization                                  |
| `permission`     | Role-based access control                              |
| `refreshtoken`   | Refresh token management                             |
| `edgeoperations`  | Edge computing operations                              |
| `problemlist`    | Curated problem collections                          |

## Shared Infrastructure

Located in `backend-spring/src/main/java/com/ulticode/`:

| Path                    | Purpose                                        |
|------------------------|----------------------------------------------|
| `common/annotation/`   | @CurrentUser, @RequireRole, @RateLimit      |
| `common/config/`       | Security, Redis, CORS, MyBatis-Plus, Swagger |
| `common/exception/`   | GlobalExceptionHandler, BusinessException      |
| `common/response/`     | Result<T>, PageResult<T>                     |
| `common/util/`         | Utility classes                                |
| `security/`           | JWT filter, CSRF service, OAuth providers     |
| `websocket/`          | WebSocket config, NotificationService          |

## Data Flow for Key Operations

### User Login Flow

```
Client                    AuthController              AuthService               DB
  │                           │                          │                       │
  │──POST /auth/login────────>│                          │                       │
  │                          │──validate credentials────>│                       │
  │                          │                          │────SELECT──────────────>│
  │                          │<───user record──────────│                       │
  │                          │<───────user──────────────│                       │
  │                          │                          │                       │
  │<─200 {csrfToken}────────│                          │                       │
  │   + httpOnly cookie     │                          │                       │
```

1. Client POSTs credentials to `/auth/login`
2. `AuthController` delegates to `AuthService`
3. `AuthService` validates credentials against DB
4. On success: creates JWT tokens, stores refresh token in DB, sets httpOnly cookie
5. Response includes `csrfToken` for subsequent state-changing requests

### Problem Submission Flow

```
Client              SubmissionController        SubmissionService         Redis Queue
  │                        │                        │                        │
  │──POST /submissions───>│                        │                        │
  │                       │──submit()─────────────>│                        │
  │                       │                        │──RPUSH to queue───────>│
  │<──202 {submissionId}─│                        │                        │
  │                       │                        │          Judge Worker   │
  │                       │                        │<──LPOP from queue────│
  │                       │                        │                        │
  │                       │                        │────UPDATE status─────>│ (DB)
  │                       │                        │                        │
  │                       │<─WebSocket push─────────────────────────────────│
  │<──result─────────────│                        │                        │
```

1. Client POSTs code to `/submissions`
2. `SubmissionController` validates and creates `Pending` submission
3. Submission pushed to Redis queue via Redisson
4. Client receives submission ID for polling
5. Judge worker (async) processes submission from queue
6. Worker updates submission status in DB
7. WebSocket pushes result to authenticated client

## Caching Strategy

### Redis Usage Patterns

| Cache Type          | Key Pattern                    | TTL        | Purpose                        |
|--------------------|-------------------------------|------------|--------------------------------|
| Problem list       | `problem:list:{hash}`          | 5 min      | Paginated problem lists        |
| Problem detail    | `problem:{id}`                 | 10 min     | Full problem with description |
| User session       | `user:session:{userId}`       | 15 min     | Session data                  |
| Contest ranking    | `contest:rank:{contestId}`     | 30 sec     | Live leaderboard               |
| Submission result  | `submission:{id}`              | 1 hour     | Judge results                  |
| Daily recs        | `rec:daily:{userId}`         | 24 hours   | Pre-generated recommendations  |
| Rate limit        | `ratelimit:{key}`            | Dynamic    | @RateLimit annotation          |

### Redisson Distributed Locks

Used for:
- Contest start/end synchronization
- Rate limiting implementation
- Distributed job processing

## Authentication & Authorization

### JWT + CSRF Architecture

```
Request
    │
    ▼
┌───────────────┐
│ JWT Filter   │ Extract token from cookie or Authorization header
└───────┬───────┘
        │ Valid token?
        ▼
┌───────────────┐
│ Security     │ Set Authentication in SecurityContext
│ Context      │ (ROLE_USER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
└───────┬───────┘
        │
        ▼
┌───────────────┐
│ @PreAuthorize │ Method-level authorization
│ or permitAll()│
└───────────────┘
```

### Public Endpoints (No Auth Required)

- `GET /problems/**` — Problem reading
- `GET /contest/**` — Contest viewing
- `GET /submissions/statuses` — Status metadata
- `GET /api/solutions/**` — Solution reading
- `GET /forum/posts/**` — Forum reading
- `POST /auth/login`, `/auth/register`, `/auth/refresh`
- `WS /ws/**` — WebSocket handshake

### Role-Based Access

| Role          | Access Level                                      |
|---------------|--------------------------------------------------|
| Anonymous     | Public endpoints only                              |
| USER          | Personal data, submissions, forum posting          |
| ADMIN         | User management, problem management, moderation    |
| SUPER_ADMIN   | Full access including system configuration        |

## Real-Time Features (WebSocket)

### STOMP over WebSocket

```
Client ───CONNECT /ws───> WebSocketConfig (STOMP endpoint)
                                  │
                                  ▼ STOMP CONNECT
Client <───CONNACK────────────────│
                                  │
Client ──SUBSCRIBE───────────────>│ /user/queue/notifications
                                  │
Client <───MESSAGE────────────────│ (submission result, contest updates)
```

### Notification Events

| Event                    | Payload                                    |
|-------------------------|-------------------------------------------|
| `SUBMISSION_COMPLETED`  | Submission result (accept/reject)          |
| `CONTEST_STARTING`      | Contest ID, start time                    |
| `CONTEST_RANKING_UPDATE`| Updated rankings                           |
| `NEW_NOTIFICATION`      | Notification body                          |

## API Design Patterns

### Response Format

```java
// Success response
Result.success(data) → { code: 0, message: "success", data: {...}, traceId: "t-123456" }

// Error response
Result.error(code, message) → { code: 40001, message: "Validation failed", data: null, traceId: "t-123456" }
```

### Pagination

```java
// Request: GET /problems?page=1&pageSize=20&difficulty=Medium

// Response: PageResult<T>
{
  "items": [...],
  "total": 150,
  "page": 1,
  "pageSize": 20,
  "totalPages": 8
}
```

### Error Codes

| Range    | Category              |
|----------|----------------------|
| 0        | Success               |
| 10001+   | Authentication errors |
| 20001+   | Authorization errors |
| 30001+   | Validation errors    |
| 40001+   | Resource not found   |
| 50001+   | Internal server errors |

### RESTful Conventions

| Method | Endpoint                    | Purpose                        |
|--------|----------------------------|--------------------------------|
| GET    | `/problems`                | List problems (paginated)       |
| GET    | `/problems/{id}`           | Get problem detail            |
| POST   | `/problems`                | Create problem (admin)         |
| PUT    | `/problems/{id}`           | Update problem (admin)         |
| DELETE | `/problems/{id}`           | Soft delete problem (admin)    |
| GET    | `/submissions`             | List user's submissions        |
| POST   | `/submissions`             | Submit code                    |
| GET    | `/submissions/{id}`        | Get submission result          |

## Technology Stack

| Layer          | Technology                                    |
|----------------|-----------------------------------------------|
| Backend        | Spring Boot 3.5, Java 17                   |
| ORM            | MyBatis-Plus 3.5.x                          |
| Auth           | JWT (jjwt 0.13.x), Spring Security           |
| Cache/Locks    | Redisson 4.3.x                               |
| Service Mesh   | Dubbo3 3.3.x, Nacos 2.3.x                   |
| Search         | MeiliSearch                                   |
| Frontends      | Vue 3, Vite, Tailwind CSS v4                 |
| Recommendation | Dubbo3 + Apache Spark (optional)              |
| Database       | MySQL 9.x, Flyway migrations                  |
| Process Mgmt   | PM2                                           |
| Containers     | Docker, Docker Compose                        |

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

## Ports Reference

| Service              | Port  |
|---------------------|-------|
| Backend (Spring)     | 9001  |
| Console Frontend     | 9002  |
| Management Frontend   | 9003  |
| Recommend-Provider   | 9004  |
| Recommend-Web        | 9005  |
| MySQL                | 23306 |
| Redis                | 26379 |
| Nacos                | 28848 |
