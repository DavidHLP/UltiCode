# UltiCode System Architecture

## Overview

UltiCode is an online programming platform (similar to LeetCode) built with a microservices-inspired architecture using Spring Boot 3.5 (Java 17), Vue 3 frontends, and an optional Dubbo3 + Spark recommendation service.

## Layered Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend Layer                            │
├────────────────────────┬────────────────────────────────────────┤
│  Console (9002)        │  Management (9003)                    │
│  Vue 3 + Vite          │  Vue 3 + Vite                        │
│  Tailwind CSS v4       │  Tailwind CSS v4                      │
│  User-facing app       │  Admin dashboard                      │
│  Problem solving,      │  User management, audit logs,         │
│  contests, submissions │  content moderation, analytics        │
└────────────┬───────────┴──────────────────┬─────────────────────┘
             │                              │
             └──────────────┬───────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot 9001)                   │
│                     Spring Boot 3.5 / Java 17                  │
│                  MyBatis-Plus / MySQL / Redis                   │
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

## Backend Module Structure

The backend (`backend-spring/src/main/java/com/ulticode/`) is organized into three layers:

### Common Layer (`common/`)
Shared utilities, configurations, exceptions, and annotations.

```
common/
├── annotation/       # @CurrentUser, @RequireRole, @RateLimit
├── config/          # SecurityConfig, WebConfig, RedisConfig, SwaggerConfig
├── dto/             # ApiResponse
├── exception/       # GlobalExceptionHandler, BusinessException
├── response/        # Result<T>, PageResult<T>
├── service/         # TokenBlacklistService
└── util/            # SecurityUtil
```

### Security Layer (`security/`)
JWT authentication, CSRF protection, and authorization filters.

### Modules Layer (`modules/`)
Feature modules following a consistent structure (controller/service/entity/mapper/dto):

| Module | Purpose |
|--------|---------|
| **achievement** | User achievements, points, and progress tracking |
| **admin** | Analytics, audit logs, dashboard, content moderation |
| **auth** | Authentication, login, OAuth, password management |
| **backup** | Database backup management |
| **bookmark** | User bookmarks and folders |
| **contest** | Contests, rankings, participation |
| **edgeoperations** | Real-time submission judging |
| **email** | Email notifications |
| **forum** | Discussion forum, posts, comments |
| **i18n** | Internationalization |
| **monitoring** | Health checks, metrics |
| **notification** | User notifications |
| **permission** | Role-based permissions |
| **problemlist** | Curated problem collections |
| **problem** | Problems, test cases, examples |
| **queue** | Submission queue management |
| **recommendation** | Problem recommendations via Dubbo |
| **refreshtoken** | Token refresh management |
| **search** | Full-text search |
| **solution** | User solutions and editorial |
| **submission** | Code submissions and judging |
| **subscription** | User subscriptions |
| **user** | User CRUD, profiles |
| **vote** | Upvote/downvote system |
| **websocket** | Real-time communication |

### WebSocket Layer (`websocket/`)
Real-time submission updates and notifications via STOMP over WebSocket.

## Frontend Architecture

### Console (`console/src/`)
User-facing application for problem solving.

```
console/src/
├── App.vue           # Root component
├── main.ts           # Entry point
├── style.css         # Global styles (Solarized theme)
├── pwa-register.ts   # PWA registration
├── api/              # API client modules
├── components/       # Shared Vue components
├── pages/            # Route pages
├── stores/           # Pinia state management
├── utils/            # Utilities (request.ts, etc.)
└── types/            # TypeScript type definitions
```

**Design System**: Solarized OKLCH color palette, Tailwind CSS v4 with `@theme inline`, shadcn-vue + Radix Vue + Lucide icons.

### Management (`management/src/`)
Admin dashboard for platform management.

```
management/src/
├── App.vue           # Root component
├── env.d.ts          # Type declarations
├── style.css         # Global styles
├── api/              # API client modules
├── components/       # Shared Vue components
├── pages/            # Route pages
├── stores/           # Pinia state management
└── utils/            # Utilities
```

## Recommendation Service (`recommendation/`)

Optional Dubbo3 + Spark microservices for problem recommendations.

```
recommendation/
├── recommend-api/       # Dubbo API definitions
├── recommend-core/      # Core recommendation algorithms
├── recommend-feature/   # Feature engineering
├── recommend-provider/  # Dubbo provider service (port 9004)
├── recommend-spark/     # Spark ML jobs
├── recommend-web/       # Dubbo consumer web layer (port 9005)
└── pom.xml              # Parent POM
```

**Dependencies**: Requires Nacos (28848) for service discovery. Must run `mvn install -DskipTests` before first start.

## Data Flow

### API Request Flow
```
Frontend → Spring Boot (9001) → MyBatis-Plus → MySQL
                ↓
           Redis (cache, sessions, rate limiting)
```

### Authentication Flow
```
1. User submits credentials → /api/auth/login
2. Backend validates, returns JWT (httpOnly cookie) + CSRF token
3. Frontend stores CSRF in localStorage
4. Subsequent requests include JWT cookie + X-CSRF-Token header
5. JWT filter validates token on each request
```

### Submission Judging Flow
```
1. User submits code → /api/submission
2. Submission queued (Redis queue)
3. Edge operations service polls queue
4. Code executed against test cases
5. Results stored in MySQL
6. WebSocket pushes real-time updates to frontend
```

## Database

### MySQL (23306)
Primary data store managed via Flyway migrations.

**Migration Groups**:
- V1: users, submissions, permissions
- V2: problems, tags, lists
- V3: contests, rankings
- V4: forum
- V8: collections
- V9: solutions

### Redis (26379)
- Session storage
- JWT token blacklist
- Rate limiting counters
- Submission queue
- Cache layer

## External Dependencies

| Service | Port | Purpose |
|---------|------|---------|
| Backend | 9001 | Main API server |
| Console | 9002 | User frontend |
| Management | 9003 | Admin frontend |
| Recommend-Provider | 9004 | Recommendation provider |
| Recommend-Web | 9005 | Recommendation consumer |
| MySQL | 23306 | Primary database |
| Redis | 26379 | Cache/sessions |
| Nacos | 28848 | Service discovery (optional) |

## Key Technical Patterns

### API Response Format
```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "traceId": "t-1234567890"
}
```

### Frontend API Pattern
```typescript
import { apiGet, apiPost } from "@/utils/request";

export const exampleApi = {
  async getList(): Promise<Item[]> {
    return apiGet<Item[]>("/endpoint");
  },
};
```

### Module Structure
Each backend module follows a standard pattern:
- `controller/` - REST endpoints
- `service/` - Business logic
- `entity/` - MyBatis-Plus entities
- `mapper/` - Database mappers
- `dto/` - Request/Response DTOs
