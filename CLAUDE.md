# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

UltiCode is an online programming platform (similar to LeetCode) built with:

- **Backend**: Spring Boot 3.5 (Java 17) with MyBatis-Plus, MySQL, Redis
- **Frontend Console**: Vue 3 + Vite + Tailwind CSS (user-facing application)
- **Frontend Management**: Vue 3 + Vite + Tailwind CSS (admin dashboard)
- **Recommendation Service**: Dubbo3 + Spark microservices (optional)
- **Database**: MySQL with Flyway migrations (managed by `db-manager`)

## Common Commands

### Quick Start (Recommended)

```bash
# Start Docker services (MySQL, Redis, Nacos)
docker compose up -d

# Start all services via PM2
pnpm install                 # Install frontend deps if needed
pm2 start ecosystem.config.cjs

# Check status / stop / restart
pm2 status
pm2 stop all / pm2 restart all
```

### Individual Services

```bash
# Backend (Spring Boot) - runs on port 9001
cd backend-spring && ./mvnw spring-boot:run

# Console Frontend - runs on port 9002
cd console && pnpm run dev

# Management Frontend - runs on port 9003
cd management && pnpm run dev

# Recommendation Service (optional)
cd recommendation && mvn -pl recommend-provider spring-boot:run
cd recommendation && mvn -pl recommend-web spring-boot:run
```

### Database Management

```bash
# Run Flyway migrations (from project root)
cd db-manager
python -m db_manager.cli migrate     # Run all pending migrations
python -m db_manager.cli status      # Check migration status
python -m db_manager.cli info        # Detailed migration info
python -m db_manager.cli repair      # Repair Flyway schema history
```

### Testing & Quality

```bash
# Root-level (runs for all packages)
pnpm test                  # Run all tests
pnpm lint                  # Lint all code
pnpm type-check            # TypeScript check all
pnpm quality               # lint + type-check + test

# Individual frontends
cd console && pnpm test
cd management && pnpm test:coverage

# Backend (Spring Boot)
cd backend-spring && ./mvnw test
```

### Database Management Notes

- **Python 路径**: db-manager 有独立 venv，必须用 `db-manager/.venv/bin/python -m db_manager.cli`，系统 python 不可用
- **修改迁移文件后**: 已有迁移的 checksum 会变化，需先 `clean --force` 再 `migrate`
- **MySQL 访问**: 通过 Docker 容器访问，`docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode`
- **种子数据分布**: V1(users/submissions/permissions), V2(problems/tags/lists), V3(contests/rankings), V4(forum), V8(collections), V9(solutions)
- **外键**: 迁移文件以 `SET FOREIGN_KEY_CHECKS=0` 开头，`SET FOREIGN_KEY_CHECKS=1` 结尾

### Build

```bash
# Frontend builds
cd console && pnpm build
cd management && pnpm build

# Backend build
cd backend-spring && ./mvnw package -DskipTests
```

## Service Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend Layer                           │
├────────────────────────┬────────────────────────────────────────┤
│  Console (9002)        │  Management (9003)                    │
│  User-facing app       │  Admin dashboard                      │
│  Problem solving,      │  User management, audit logs,         │
│  contests, submissions │  content moderation, analytics        │
└────────────┬───────────┴──────────────────┬─────────────────────┘
             │                              │
             └──────────────┬───────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot 9001)                   │
├─────────────────────────────────────────────────────────────────┤
│  Modules: auth, user, problem, submission, contest, forum,     │
│  solution, notification, subscription, moderation, search,     │
│  achievement, i18n, backup, email, monitoring, vote, admin,    │
│  bookmark, edgeoperations, permission, problemlist, queue,     │
│  recommendation, refreshtoken, websocket                       │
└────────────────────────────┬────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────────────┐
│ MySQL (23306)   │ │ Redis (26379)   │ │ Recommendation Service  │
│ Primary DB      │ │ Cache, Sessions │ │ Dubbo3 + Spark (9004)   │
│ Prisma schema   │ │ Rate Limiting   │ │ Optional, Nacos (28848) │
└─────────────────┘ └─────────────────┘ └─────────────────────────┘
```

## Key Patterns

### Backend Response Format

All API responses use the `Result<T>` wrapper:

```json
{
  "code": 0,           // 0 = success, non-zero = error
  "message": "success",
  "data": { ... },     // Response payload
  "traceId": "t-1234567890"
}
```

Frontend `request.ts` automatically unwraps responses, returning `response.data` directly.

### Frontend API Client Pattern

```typescript
// management/src/api/example.ts
import { apiGet, apiPost } from "@/utils/request";

export const exampleApi = {
  async getList(): Promise<Item[]> {
    return apiGet<Item[]>("/endpoint");
  },
  async create(data: CreateDTO): Promise<Item> {
    return apiPost<Item>("/endpoint", data);
  },
};
```

### Authentication Flow

- JWT tokens stored in httpOnly cookies (access_token, refresh_token)
- CSRF token required for state-changing requests (POST, PUT, PATCH, DELETE)
- Frontend reads CSRF from localStorage and sends as `X-CSRF-Token` header
- Login response includes both `access_token` (cookie) and `csrf_token` (response body)

### Backend Module Structure

```
backend-spring/src/main/java/com/ulticode/
├── common/           # Shared utilities, configs, exceptions
│   ├── response/     # Result wrapper, PageResult
│   ├── exception/    # GlobalExceptionHandler, BusinessException
│   ├── config/       # SecurityConfig, WebConfig, RedisConfig
│   └── annotation/   # @CurrentUser, @RequireRole, @RateLimit
├── security/         # JWT filters, CSRF service
├── modules/          # Feature modules
│   ├── auth/         # Authentication, login, OAuth
│   ├── user/         # User CRUD, profile
│   ├── problem/      # Problems, test cases, examples
│   ├── submission/   # Code submissions, judging
│   ├── contest/      # Contests, rankings
│   └── ...           # Other domain modules
└── websocket/        # Real-time communication
```

Each module typically contains:

- `controller/` - REST endpoints
- `service/` - Business logic
- `entity/` - Database entities (MyBatis-Plus)
- `mapper/` - MyBatis mappers
- `dto/` - Request/Response DTOs

### Database Migrations

Migrations are SQL files in `db-manager/migrations/` managed by Flyway with naming convention:

```
V{version}__description.sql
```

Run via `db-manager` CLI (Python, uses Flyway under the hood).

## Environment Variables

Backend reads from `backend-spring/.env`:

```
DATABASE_URL=mysql://user:pass@localhost:23306/ulticode
DB_HOST=localhost
DB_PORT=23306
JWT_SECRET=your-secret-key
REDIS_HOST=localhost
REDIS_PORT=26379
```

Frontend uses Vite env vars (`VITE_API_BASE_URL`).

## Port Reference

| Service          | Port  |
| ---------------- | ----- |
| Backend (Spring) | 9001  |
| Console          | 9002  |
| Management       | 9003  |
| Recommend-Web    | 9004  |
| MySQL            | 23306 |
| Redis            | 26379 |
| Nacos            | 28848 |

## Debugging

- Backend logs: `tail -f /tmp/ulticode-backend.log`
- Console logs: `tail -f /tmp/ulticode-console.log`
- Management logs: `tail -f /tmp/ulticode-management.log`
- Swagger UI: `http://localhost:9001/swagger-ui.html`
- Health check: `curl http://localhost:9001/actuator/health`

## Frontend Design System

Both `console/` and `management/` share a unified Solarized color palette using OKLCH color space with `--radius: 0` (sharp corners). Reference the local skills for detailed specs:
- `ulticode-solarized-colors` — Full OKLCH color token reference
- `ulticode-layout` — Layout architecture, sidebar, responsive patterns
- `ulticode-console-styles` — Code blocks, markdown, charts (console-specific)

### Design Rules
- Color space: OKLCH only, never hex/HSL
- Theme toggle: `.dark` class on root element
- CSS framework: Tailwind CSS v4 with `@theme inline`, no tailwind.config.ts
- UI components: shadcn-vue (new-york style) + Radix Vue + Lucide icons
- Console-only: KaTeX math, highlight.js Solarized syntax, chart visualization tokens
- Chart data colors are light/dark invariant; only grid/tooltip change between themes

## PM2 Services

**Prerequisite:** `npm install -g pm2` (PM2 is a global tool, not a project dependency)

### Slash Commands

`/pm2-all` `/pm2-all-stop` `/pm2-all-restart` — Batch operations
`/pm2-9001` `/pm2-9002` `/pm2-9003` — Start single service + logs
`/pm2-9001-stop` `/pm2-9002-stop` `/pm2-9003-stop` — Stop single service
`/pm2-9001-restart` `/pm2-9002-restart` `/pm2-9003-restart` — Restart single service
`/pm2-docker` `/pm2-docker-stop` — Docker container management
`/pm2-logs` `/pm2-status` — Monitoring

### Docker Services (via docker-wrapper.cjs)

| Name          | Action | Description                    |
| ------------- | ------ | ------------------------------ |
| docker-up     | up     | Start all containers (MySQL, Redis, Nacos) |
| docker-down   | down   | Stop and remove all containers |
| docker-logs   | logs   | View container logs (follow)   |
| docker-ps     | ps     | Show container status          |

### Application Services

| Port | Name          | Type                             |
| ---- | ------------- | -------------------------------- |
| 9001 | ulticode-9001 | Spring Boot (Backend)            |
| 9002 | ulticode-9002 | Vite (Console)                   |
| 9003 | ulticode-9003 | Vite (Management)                |
| 9004 | ulticode-9004 | Spring Boot (Recommend-Provider) |
| 9005 | ulticode-9005 | Spring Boot (Recommend-Web)      |

**Terminal Commands:**

```bash
# Docker management
pm2 start docker-up     # Start Docker containers (MySQL, Redis, Nacos)
pm2 start docker-down   # Stop Docker containers
pm2 logs docker-up      # View Docker logs

# Application services
pm2 start ecosystem.config.cjs   # Start all app services
pm2 start all                   # After first time
pm2 stop all / pm2 restart all
pm2 start ulticode-9001 / pm2 stop ulticode-9001

# Common
pm2 logs / pm2 status / pm2 monit
pm2 save                         # Save process list
pm2 resurrect                    # Restore saved list
```
