# AGENTS.md

> **Last Updated**: 2026-05-12
> **Context**: Comprehensive project reference + database encoding issue investigation

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

### PM2 Service Management

```bash
# Start/stop individual services
pm2 start ecosystem.config.cjs --only ulticode-9001  # Backend
pm2 start ecosystem.config.cjs --only ulticode-9002  # Console
pm2 start ecosystem.config.cjs --only ulticode-9003  # Management
pm2 start ecosystem.config.cjs --only ulticode-9004  # Recommend Provider
pm2 start ecosystem.config.cjs --only ulticode-9005  # Recommend Web

# Restart after .env changes (critical — env is cached)
pm2 restart ulticode-9001 --update-env
```

### Docker Services

```bash
# Start/stop infrastructure (MySQL, Redis, Nacos)
pm2 start docker-wrapper.cjs --name docker-up
pm2 start docker-wrapper.cjs --name docker-down

# Or use docker compose directly
docker compose up -d mysql redis nacos
docker compose logs -f mysql
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

### Database Encoding — CRITICAL

**Root cause of Chinese text corruption**: The db-manager JDBC URL was missing `useUnicode=true`, causing UTF-8 Chinese text to be double-encoded when inserted via Flyway migrations.

**Fix applied**: `db-manager/src/db_manager/config.py` now includes `useUnicode=true&characterEncoding=UTF-8` in JDBC URL.

**Symptom**: API returns garbled text like `å¹¶å'ç¼–ç¨‹å…¥é—¨` instead of `并发编程入门`.

**Affected data**: V26 and V27 migrations fix `problem_lists` table. Other tables (`problem_tags`, `problems`, `forum_*`, `solutions`, `users`, etc.) have remaining corruption — 332+ cells across 24 tables.

**For new inserts**: Backend and db-manager now have proper encoding config — new data inserts correctly.

**For existing corrupted data**: `fix_utf8.py` at repo root attempts repair but detection logic is flawed. Manual table-by-table fixes using correct values from migration files may be needed.

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

### Docker Services

```bash
pm2 start docker-up      # Start MySQL, Redis, Nacos
pm2 start docker-down    # Stop containers
```

Required env vars for `docker compose up`:
- `MYSQL_ROOT_PASSWORD`
- `DB_PASSWORD`
- `REDIS_PASSWORD`

### Backend Startup Issues

- `./mvnw spring-boot:run -Dmaven.test.skip=true` — quickest way to verify backend starts
- springdoc 2.7.0 incompatible with Spring Boot 3.2.5 (LiteWebJarsResourceResolver missing); pinned to 2.6.0
- Redis password change requires `docker compose up -d redis` (container restart, not just `docker restart`)
- Flyway: db-manager does not support `-outOfOrder`; use `docker exec ulticode-mysql mysql ...` for out-of-order fixes
- Character encoding: `WebConfig.java` includes `CharacterEncodingFilter` with `forceEncoding=true` for request/response

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

### Shared Package Gotchas

- `shared/auth-core/package.json` uses TypeScript 5.9.3 (not 6.x like frontends) — has its own `type-check` script
- `shared/auth-core` tests run with `cd shared/auth-core && pnpm test` (uses vitest)
- Console imports shared code directly via path aliases; Management uses symlink
- Adding new exports to `shared/auth-core/src/index.ts` requires both frontends to be checked for import path issues

### CSRF Token Rotation

- Backend implements strict token rotation: validates token, generates new one, returns via `X-New-CSRF-Token` header
- Old token has 5-minute grace period (set via Redis TTL) for concurrent requests
- Both frontends use shared `createCsrfAxiosInterceptor()` from `@/shared/auth-core/src` for:
  - Request: attaches `X-CSRF-Token` for non-GET/HEAD/OPTIONS
  - Response: captures `X-New-CSRF-Token` from 2xx responses
  - Error: 403 CSRF errors trigger one retry with fresh token

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

## Debugging & Logs

### Viewing Logs

All services log to `/tmp/` via PM2 with timestamps:

| Service | Stdout | Stderr |
|---------|--------|--------|
| Backend (9001) | `/tmp/ulticode-9001-out.log` | `/tmp/ulticode-9001-error.log` |
| Console (9002) | `/tmp/ulticode-9002-out.log` | `/tmp/ulticode-9002-error.log` |
| Management (9003) | `/tmp/ulticode-9003-out.log` | `/tmp/ulticode-9003-error.log` |
| Recommend Provider (9004) | `/tmp/ulticode-9004-out.log` | `/tmp/ulticode-9004-error.log` |
| Recommend Web (9005) | `/tmp/ulticode-9005-out.log` | `/tmp/ulticode-9005-error.log` |

```bash
# View all logs in real-time
pm2 logs

# View specific service logs
pm2 logs ulticode-9001

# View last 100 lines
pm2 logs ulticode-9001 --lines 100

# View logs with timestamp
pm2 logs ulticode-9001 --timestamp

# Traditional tail (works for any log file)
tail -f /tmp/ulticode-9001-out.log
tail -f /tmp/ulticode-9001-error.log
```

### Health Checks & Endpoints

```bash
# Backend health
curl http://localhost:9001/actuator/health

# Swagger UI
curl http://localhost:9001/swagger-ui.html

# API docs
curl http://localhost:9001/api-docs

# Query DB directly
docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SELECT ..."
```

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

## Module-Specific AGENTS.md

| Module | Path | Focus |
|--------|------|-------|
| Backend | `backend-spring/AGENTS.md` | Spring Boot modules, testing, MyBatis-Plus |
| Console | `console/AGENTS.md` | Vue user frontend, auth flow, Solarized theme |
| Management | `management/AGENTS.md` | Vue admin dashboard, shared symlink, ESLint 10.x |
| DB Manager | `db-manager/AGENTS.md` | Flyway migrations, encoding pitfalls |
| Recommendation | `recommendation/AGENTS.md` | Dubbo3 + Spark microservices, build order |
| ECC Config | `.claude/CLAUDE.md` | ECC-specific rules, hot paths, anti-patterns |

## Additional Documentation

- `recommendation/README.md` — recommendation service setup and troubleshooting
- `db-manager/README.md` — migration CLI usage

## Security Anti-Patterns (THIS PROJECT)

### Password Reset Timing Attack Prevention
- `PasswordResetService.java`: "Do NOT reveal whether user exists" — prevents user enumeration
- Always return same timing for both existing and non-existing users

### httpOnly Cookie Enforcement
- **console**: Do NOT send Authorization header via socket (`useContestSocket.ts:198`)
- **console**: Do NOT read `access_token` from `document.cookie` — httpOnly prevents JS access (`lib/socket.ts:191`)

### XSS Prevention
- `console/src/utils/sanitize.ts` and `management/src/utils/sanitize-markdown.ts`: "Dangerous tags that should NEVER be allowed"

### Request Deduplication
- `console/src/utils/request.ts` and `management/src/utils/request.ts`: URLs in `NON_DEDUPLICABLE_URLS` must NEVER be deduplicated — auth-critical requests

## Build/CI Non-Standard Patterns

### GitHub Actions
- **Flyway workaround**: `ci.yml` downloads Flyway CLI directly and writes custom wrapper script to bypass Alpine JRE detection (lines 201-217) — non-standard but necessary
- **Action version mismatch**: ci-recommendation.yml uses v4 actions; ci.yml uses v5/v6
- **Inline health checks**: cd-deploy.yml uses inline bash loops rather than reusable workflows

### Docker
- **MySQL**: utf8mb4 charset explicitly configured
- **SSL inconsistency**: prod has `useSSL=true`, dev has `useSSL=false`
- **Missing restart policy**: recommend-provider and recommend-web in docker-compose.prod.yml lack restart policy

### Maven
- **JaCoCo thresholds**: Line 5%, branch 2% — extremely permissive
- **Extensive exclusions**: All Mapper, DTO, VO, entity classes excluded from coverage
- **Lombok version**: Pinned to 1.18.44 instead of Spring Boot managed
- **finalName = "app"**: Non-standard artifact naming

### PM2
- **Dual ecosystem config**: Root `ecosystem.config.cjs` conflicts with `backend-spring/ecosystem.config.cjs`
- **Hardcoded paths/secrets**: backend-spring-specific config has absolute paths and JWT secret — do NOT use

## Behavioral Guidelines

- **State assumptions explicitly**. If multiple interpretations exist, present them — don't pick silently.
- **Simplicity first**: No speculative features, no abstractions for single-use code, no flexibility not requested.
- **Surgical changes**: Touch only what you must. Remove imports/variables/functions that YOUR changes made unused. Don't refactor pre-existing dead code unless asked.
- **Goal-driven execution**: Define verifiable success criteria before implementing. "Add validation" → "Write tests for invalid inputs, then make them pass."
