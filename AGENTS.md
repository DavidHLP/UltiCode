# AGENTS.md

> **Last Updated**: 2026-05-04
> **Context**: Comprehensive project reference + init-deep analysis complete (6 explore agents, 200K LOC analyzed)

Compact reference for AI assistants working in the UltiCode repository.
Every line answers: "Would an agent likely miss this without help?"

## Project Overview

UltiCode is an online programming platform (similar to LeetCode) with:

- **Backend**: Spring Boot 3.2.5 (Java 17) + MyBatis-Plus + MySQL + Redis
- **Frontend Console**: Vue 3 + Vite + Tailwind CSS v4 (user-facing, port 9002)
- **Frontend Management**: Vue 3 + Vite + Tailwind CSS v4 (admin dashboard, port 9003)
- **Recommendation Service**: Dubbo3 + Spark microservices (optional, ports 9004/9005)
- **Database Migrations**: Flyway managed by `db-manager` (Python CLI)

## Quick Start

```bash
# 1. Start Docker services (MySQL, Redis, Nacos)
docker compose up -d

# 2. Install frontend deps
pnpm install

# 3. Start all services via PM2
pm2 start ecosystem.config.cjs

# Check status / stop / restart
pm2 status
pm2 stop all / pm2 restart all
```

### Individual Services

```bash
# Backend (Spring Boot) - port 9001
cd backend-spring && ./mvnw spring-boot:run

# Console Frontend - port 9002
cd console && pnpm run dev

# Management Frontend - port 9003
cd management && pnpm run dev

# Recommendation (optional) — MUST build first!
cd recommendation && mvn install -DskipTests
cd recommendation && mvn -pl recommend-provider spring-boot:run
cd recommendation && mvn -pl recommend-web spring-boot:run
```

### Database

```bash
cd db-manager
.venv/bin/python -m db_manager.cli migrate     # Run pending migrations
.venv/bin/python -m db_manager.cli info        # Check migration status
.venv/bin/python -m db_manager.cli repair      # Fix checksum issues
.venv/bin/python -m db_manager.cli validate    # Validate state
```

### Testing & Quality

```bash
# Root-level (all packages)
pnpm test       # Run all tests
pnpm lint       # Lint all code
pnpm type-check # TypeScript check all
pnpm quality    # lint + type-check + test

# Individual frontends
cd console && pnpm test
cd management && pnpm test:coverage

# Backend
cd backend-spring && ./mvnw test
```

### Build

```bash
cd console && pnpm build
cd management && pnpm build
cd backend-spring && ./mvnw package -DskipTests
```

## Architecture

```
Frontend Layer
├── Console (9002)     - User-facing: problem solving, contests, submissions
└── Management (9003)  - Admin: user management, audit logs, moderation
         │
         ▼
Backend (Spring Boot 9001)
├── Modules (26): auth, user, problem, submission, contest, forum,
│   solution, notification, subscription, moderation, search,
│   achievement, i18n, backup, email, monitoring, vote, admin,
│   bookmark, edgeoperations, permission, problemlist, queue,
│   recommendation, refreshtoken, websocket
│
└── common/ - Result wrapper, exceptions, SecurityConfig, annotations

Data Layer
├── MySQL (23306)      - Primary database, MyBatis-Plus
├── Redis (26379)      - Cache, sessions, rate limiting
└── Nacos (28848)      - Service discovery (recommendation only)
```

Each backend module follows: `controller/` → `service/` → `entity/` → `mapper/` → `dto/`

## Key Conventions

### Backend Response Format

All API responses use `Result<T>` wrapper:

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "traceId": "t-1234567890"
}
```

`code: 0` = success. Frontend `request.ts` auto-unwraps, returning `response.data` directly.

### Frontend API Client Pattern

```typescript
// console/src/api/example.ts or management/src/api/admin/example.ts
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

- JWT tokens in httpOnly cookies (`access_token`, `refresh_token`)
- CSRF token required for state-changing requests (POST, PUT, PATCH, DELETE)
- Frontend reads CSRF from localStorage, sends as `X-CSRF-Token` header
- Login returns `csrfToken` in response body (not a JWT string)

### curl with Auth

```bash
# 1. Login to get session cookie
curl -s -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/cookies.txt

# 2. Use cookie for authenticated requests
curl -s http://localhost:9001/users/u-admin-001/stats \
  -b /tmp/cookies.txt | jq .
```

- Use `-c` to save cookies, `-b` to send cookies
- jq fails? Use `python3 -m json.tool` as fallback

### Frontend Design System

- **Color space**: OKLCH only, never hex/HSL
- **Theme toggle**: `.dark` class on root element
- **CSS**: Tailwind CSS v4 with `@theme inline`, no `tailwind.config.ts`
- **UI components**: shadcn-vue (new-york style) + Radix Vue + Lucide icons
- **Radius**: `--radius: 0` (sharp corners everywhere)
- **Console-only**: KaTeX math, highlight.js Solarized syntax, chart tokens
- **Chart colors**: light/dark invariant; only grid/tooltip change between themes

### Shared Code

`shared/` at repo root contains `auth-core/` and `types/`.
- **Management** has symlink `src/shared -> ../../shared` — imports via `@/shared/auth-core/src`
- **Console** does NOT have this symlink — auth utilities are duplicated locally

### Database Migrations

- Files in `db-manager/migrations/` following `V{version}__description.sql`
- Each migration wraps in `SET FOREIGN_KEY_CHECKS=0` ... `SET FOREIGN_KEY_CHECKS=1`
- Seed data distribution: V1(users/permissions), V2(problems/tags), V3(contests), V4(forum), V8(collections), V9(solutions)
- After modifying migration files: `clean --force` then `migrate` (checksum changes)
- Use `db-manager/.venv/bin/python` — system Python will not work

## Critical Pitfalls

### Build Order

```
recommendation (mvn install -DskipTests) → backend-spring
```
Backend depends on `recommend-api`. Without it, backend compilation fails.

### Frontend `dev` Script Trap

Both `console` and `management` have:
```json
"dev": "pnpm run lint && pnpm run type-check && pnpm run format && pnpm run test && vite"
```
This runs **all checks before starting Vite** — slow for development. Use `pnpm vite` directly if you only need the dev server.

### ESLint Version Split

- **console**: ESLint 9.x + `eslint-plugin-vue` ^9.30.0 (10.x breaks due to TypeScript peer dep conflict)
- **management**: ESLint 10.x + `eslint-plugin-vue` ~10.8.0
- Both use flat config (`eslint.config.ts`). Console has extensive `vue/multi-word-component-names` whitelist; management disables the rule entirely.

### No Git Hooks

There are no pre-commit hooks (no husky, no lint-staged). Linting and type-checking run via CI or manually.

### PM2 Environment Variables

- After editing `.env`: `pm2 restart ulticode-9001 --update-env` or changes won't take effect
- `ecosystem.config.cjs` manually parses `.env` for JWT_SECRET/REDIS_PASSWORD — dotenv fallback is fragile

### Backend Startup Issues

- `./mvnw spring-boot:run -Dmaven.test.skip=true` — quickest way to verify backend starts
- springdoc 2.7.0 incompatible with Spring Boot 3.2.5 (LiteWebJarsResourceResolver missing); pinned to 2.6.0
- Redis password change requires `docker compose up -d redis` (container restart, not just `docker restart`)
- Flyway: db-manager does not support `-outOfOrder`; use `docker exec ulticode-mysql mysql ...` for out-of-order fixes

### Docker Services

```bash
pm2 start docker-up      # Start MySQL, Redis, Nacos
pm2 start docker-down    # Stop containers
```

Required env vars for `docker compose up`:
- `MYSQL_ROOT_PASSWORD`
- `DB_PASSWORD`
- `REDIS_PASSWORD`

### Testing Quirks

- **Console vitest**: Has custom plugin mocking `virtual:pwa-register`; PWA devOptions disabled for faster HMR
- **Management vitest**: Uses `--passWithNoTests` flag consistently
- **Console mock validation**: `pnpm validate:mocks` runs custom script against mock data
- **Backend tests**: CI excludes integration tests (`-Dtest='!*IT'`); uses Testcontainers for MySQL/Redis
- **Jacoco coverage**: Excludes `*Mapper.java`, `*DTO.java`, `*VO.java`, `entity/*.java`, etc.; minimum line coverage 5%, branch 2%

## Port Reference

| Service            | Port  |
| ------------------ | ----- |
| Backend (Spring)   | 9001  |
| Console            | 9002  |
| Management         | 9003  |
| Recommend-Provider | 9004  |
| Recommend-Web      | 9005  |
| MySQL              | 23306 |
| Redis              | 26379 |
| Nacos              | 28848 |

## Debugging

- Backend logs: `tail -f /tmp/ulticode-backend.log`
- Console logs: `tail -f /tmp/ulticode-console.log`
- Management logs: `tail -f /tmp/ulticode-management.log`
- Swagger UI: `http://localhost:9001/swagger-ui.html`
- Health check: `curl http://localhost:9001/actuator/health`
- API docs: `http://localhost:9001/api-docs`
- Query DB: `docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SELECT ..."`

## Environment Variables

Single source of truth: **root `.env`** (not `backend-spring/.env`, which is deprecated).

Key variables:
- `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME` — MySQL connection
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` — Redis connection
- `JWT_SECRET` — min 32 chars
- `NACOS_PORT=28848` — required by recommendation services
- `VITE_API_BASE_URL=http://localhost:9001` — frontend API base

Copy from `.env.example` and fill in real values.

## CI Pipeline

`.github/workflows/ci.yml` runs on push/PR to `main`:
1. **Path filtering**: only runs jobs for changed modules (backend, console, management, docker)
2. **Backend**: build → test (`-Dtest='!*IT'`) → validate migrations
3. **Frontend**: lint → type-check → test (matrix: console, management)
4. **Docker**: build verification (no push)

Java 17 (Temurin), Node 22.x, pnpm 10.

## Additional Documentation

- `.planning/codebase/CONVENTIONS.md` — coding conventions and patterns
- `.planning/codebase/ARCHITECTURE.md` — system architecture and data flows
- `.planning/codebase/CONCERNS.md` — security risks, technical debt, anti-patterns
- `.planning/codebase/TESTING.md` — test patterns and coverage requirements
- `recommendation/README.md` — recommendation service setup and troubleshooting
- `db-manager/README.md` — migration CLI usage

## Behavioral Guidelines

- **State assumptions explicitly**. If multiple interpretations exist, present them — don't pick silently.
- **Simplicity first**: No speculative features, no abstractions for single-use code, no flexibility not requested.
- **Surgical changes**: Touch only what you must. Remove imports/variables/functions that YOUR changes made unused. Don't refactor pre-existing dead code unless asked.
- **Goal-driven execution**: Define verifiable success criteria before implementing. "Add validation" → "Write tests for invalid inputs, then make them pass."
