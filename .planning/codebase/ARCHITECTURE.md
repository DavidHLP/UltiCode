# Architecture

**Analysis Date:** 2026-04-22

## Pattern Overview

**Overall:** Layered Spring Boot monolith with domain-based modules

**Key Characteristics:**
- Monolithic Spring Boot 3.5 backend (Java 17) with MyBatis-Plus ORM
- Two separate Vue 3 frontend applications (Console for users, Management for admins)
- Stateless JWT authentication with CSRF validation for state-changing operations
- MySQL primary database with Redis for caching and sessions
- Optional Dubbo3-based recommendation microservice

## Layers

**Web Layer (Controllers):**
- Location: `backend-spring/src/main/java/com/ulticode/modules/*/controller/`
- Contains: REST endpoints annotated with `@RestController`
- Depends on: Service layer
- Used by: Frontend applications via HTTP

**Service Layer:**
- Location: `backend-spring/src/main/java/com/ulticode/modules/*/service/`
- Contains: Business logic, transaction management
- Depends on: Mapper layer, common utilities
- Used by: Controllers

**Data Access Layer (Mappers):**
- Location: `backend-spring/src/main/java/com/ulticode/modules/*/mapper/`
- Contains: MyBatis-Plus mapper interfaces
- Depends on: MyBatis-Plus, database
- Used by: Service layer

**Entity Layer:**
- Location: `backend-spring/src/main/java/com/ulticode/modules/*/entity/`
- Contains: MyBatis-Plus entity classes mapped to database tables
- Used by: Mapper layer

**DTO Layer:**
- Location: `backend-spring/src/main/java/com/ulticode/modules/*/dto/`
- Contains: Request/Response DTOs (records in modern style)
- Used by: Controllers and Services

**Common Layer:**
- Location: `backend-spring/src/main/java/com/ulticode/common/`
- Contains: Shared utilities, configurations, exceptions, annotations
- Sub-packages:
  - `response/` - Result wrapper, PageResult
  - `exception/` - GlobalExceptionHandler, BusinessException, ErrorCode
  - `config/` - SecurityConfig, RedisConfig, MybatisPlusConfig, CorsProperties, etc.
  - `annotation/` - Custom annotations
  - `aspect/` - AOP aspects
  - `util/` - Utility classes
  - `constants/` - Shared constants
  - `filter/` - Servlet filters
  - `service/` - Common services

**Security Layer:**
- Location: `backend-spring/src/main/java/com/ulticode/security/`
- Contains: JWT authentication, CSRF validation
- Sub-packages:
  - `jwt/` - JwtAuthenticationFilter, JwtTokenProvider, JwtProperties
  - `csrf/` - CsrfService, CsrfValidationFilter
  - `oauth/` - OAuth integration
  - `AuthenticationEntryPointImpl.java` - 401 handler

**WebSocket Layer:**
- Location: `backend-spring/src/main/java/com/ulticode/websocket/`
- Contains: WebSocket configuration and channel interceptor
- DTO: `websocket/dto/`

## Frontend Architecture

**Console Frontend (User-facing):**
- Location: `console/`
- Framework: Vue 3 + Vite + Tailwind CSS v4
- Port: 9002
- Features: Problem solving, contests, submissions, forum, user profile

**Management Frontend (Admin):**
- Location: `management/`
- Framework: Vue 3 + Vite + Tailwind CSS v4
- Port: 9003
- Features: User management, audit logs, content moderation, analytics

**Shared Code:**
- Location: `console/src/shared -> ../../shared`
- Shared utilities and types between frontends

## Service Communication

**Frontend to Backend:**
- HTTP REST API on port 9001
- JWT token in httpOnly cookies (access_token, refresh_token)
- CSRF token in `X-CSRF-Token` header for state-changing requests
- Response format: `Result<T>` wrapper (unwrapped by frontend request.ts)

**Backend to Database:**
- MySQL on port 23306 via MyBatis-Plus
- Redis on port 26379 for caching and session management

**Backend to Recommendation Service:**
- Dubbo3 RPC (optional)
- Service discovery via Nacos on port 28848
- Ports 9004 (provider) and 9005 (web)

## Security Architecture

**Authentication Flow:**
1. Login via `/auth/login` returns JWT in httpOnly cookie + CSRF token
2. Subsequent requests include JWT cookie automatically
3. State-changing requests (POST, PUT, PATCH, DELETE) require `X-CSRF-Token` header
4. JWT filter validates tokens before reaching endpoints

**Public Endpoints (no auth required):**
- Auth: `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/github`, `/auth/google`
- Problems: `/problems`, `/problems/**` (read operations)
- Contests: `/contest/**` (read operations)
- Submissions: `/submissions/statuses`
- Solutions: `/api/solutions`, `/api/solutions/**`, `/api/views/solution/**`
- Forum: `/forum/posts`, `/forum/posts/**`, `/forum/communities`, `/forum/tags`, `/forum/quick-filters`
- WebSocket: `/ws/**`
- Docs: `/swagger-ui/**`, `/api-docs/**`, `/v3/api-docs/**`
- Health: `/actuator/health`

**Security Headers (SecurityConfig):**
- HSTS with includeSubDomains and preload
- CSP: `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' https://fonts.gstatic.com`
- X-Frame-Options: DENY
- X-XSS-Protection: ENABLED_MODE_BLOCK
- Permissions-Policy: camera=(), microphone=(), geolocation=()

## Backend Modules

**Module Structure:** Each module follows the same pattern:
```
modules/<name>/
├── controller/   # REST endpoints
├── service/      # Business logic
├── entity/       # Database entities
├── mapper/       # MyBatis mappers
└── dto/          # Request/Response DTOs
```

**Implemented Modules:**
| Module | Purpose |
|--------|---------|
| `achievement` | User achievements and badges |
| `admin` | Admin operations |
| `auth` | Authentication, login, OAuth |
| `backup` | Database backup |
| `bookmark` | User bookmarks |
| `contest` | Contests and rankings |
| `edgeoperations` | Edge case operations |
| `email` | Email sending |
| `follow` | User follow relationships |
| `forum` | Forum posts, comments, communities |
| `i18n` | Internationalization |
| `moderation` | Content moderation |
| `monitoring` | System monitoring |
| `notification` | Notifications |
| `permission` | Permission management |
| `problem` | Problems, test cases |
| `problemlist` | Problem lists |
| `queue` | Submission queue |
| `recommendation` | Problem recommendations (Dubbo3 client) |
| `refreshtoken` | Token refresh |
| `search` | Search functionality |
| `solution` | Solutions |
| `submission` | Code submissions, judging |
| `subscription` | User subscriptions |
| `user` | User management |
| `vote` | Voting on posts/solutions |
| `websocket` | WebSocket communication |

## Deployment Topology

**Containers (Docker):**
- MySQL 23306 (primary database)
- Redis 26379 (caching, sessions)
- Nacos 28848 (service discovery for Dubbo3)

**Application Services (PM2):**
| Service | Port | Type |
|---------|------|------|
| ulticode-9001 | 9001 | Spring Boot (Backend) |
| ulticode-9002 | 9002 | Vite (Console) |
| ulticode-9003 | 9003 | Vite (Management) |
| ulticode-9004 | 9004 | Spring Boot (Recommend-Provider) |
| ulticode-9005 | 9005 | Spring Boot (Recommend-Web) |

---

*Architecture analysis: 2026-04-22*
