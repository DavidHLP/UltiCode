# Contributing to UltiCode

Welcome to the UltiCode project! This guide will help you get started with development.

---

## Prerequisites

- **Java 17+** - Required for backend development
- **Node.js 20+** - Required for frontend development (engines: `^20.19.0 || >=22.12.0`)
- **pnpm 9+** - Package manager for frontends
- **Docker & Docker Compose** - For local infrastructure

---

## Development Environment Setup

### 1. Clone & Install Dependencies

```bash
git clone <repository-url>
cd UltiCode-Public-Next

# Install frontend dependencies
cd console && pnpm install && cd ..
cd management && pnpm install && cd ..
```

### 2. Configure Environment

```bash
# Generate private infrastructure credentials and documented dev users
./scripts/dev/init-env.sh
```

### 3. Start Infrastructure

```bash
# Start infrastructure, migrate, install dependencies, and launch applications
./scripts/dev/up.sh
```

### 4. Run Database Migrations

#### Flyway migrations
```bash
# View migration status
./scripts/dev/migrate.sh info

# Apply migrations (creates 67 tables from baseline)
./scripts/dev/migrate.sh migrate
```

### 5. Start Application Services

```bash
# Using PM2 (recommended for development)
pm2 start ecosystem.config.cjs

# Or run individual services:
# Backend
cd backend-spring && ./mvnw spring-boot:run

# Frontends (separate terminals)
cd console && pnpm dev
cd management && pnpm dev
```

---

## Available Commands

<!-- AUTO-GENERATED: Scripts from package.json (regenerated 2026-06-06) -->

### Console Frontend (console/)

| Command | Description |
|---------|-------------|
| `pnpm dev` | Start dev server with lint + type-check + format + test |
| `pnpm build` | Production build with type checking |
| `pnpm build-only` | Vite build only (no type check) |
| `pnpm preview` | Preview production build locally |
| `pnpm type-check` | Run vue-tsc type checker |
| `pnpm lint` | ESLint with auto-fix |
| `pnpm format` | Prettier auto-format (no semicolons, single quotes) |
| `pnpm test` | Vitest unit tests (excludes `**/auth-core/**`) |
| `pnpm test:watch` | Vitest in watch mode |
| `pnpm test:coverage` | Vitest with coverage report |
| `pnpm validate:mocks` | Validate mock data files |
| `pnpm validate:mocks:verbose` | Validate mock data (verbose output) |
| `pnpm validate:mocks:strict` | Validate mock data (strict mode) |

### Management Frontend (management/)

Same scripts as console, plus:

| Command | Description |
|---------|-------------|
| `pnpm validate:i18n-keys` | Validate i18n key consistency across locales |
| `pnpm check:i18n` | Type-aware i18n key checker |

### Backend (backend-spring/)

| Command | Description |
|---------|-------------|
| `./mvnw spring-boot:run` | Start Spring Boot application |
| `./mvnw package -DskipTests` | Build JAR |
| `./mvnw test` | Unit tests (excludes `*IT.java`) |
| `./mvnw -Dtest='*IT' test` | Integration tests with Testcontainers |
| `./mvnw verify` | Tests plus JaCoCo report/check |
| `./mvnw compile` | Compile only |

> Backend is started via PM2 using `ecosystem.config.cjs` (entries
> `ulticode-9001`, `ulticode-9002`, `ulticode-9003`, `ulticode-arthas`).

### Database Migrations (init-db/)

| Command | Description |
|---------|-------------|
| `./scripts/dev/migrate.sh info` | View migration status |
| `./scripts/dev/migrate.sh migrate` | Apply all pending migrations |
| `./scripts/dev/migrate.sh validate` | Validate applied migrations |
| `./scripts/dev/migrate.sh baseline` | Baseline an existing DB |
| `cd init-db && mvn flyway:info` | Same as above, direct Maven |
| `cd init-db && mvn flyway:migrate` | Apply migrations via Maven |
| `cd init-db && mvn flyway:baseline` | Create baseline for existing DB |

Migration naming: `V{YYYYMMDDHHMMSS}__{Description}.sql` (see
`init-db/validate-migration.sh` for naming enforcement).

### Docker

| Command | Description |
|---------|-------------|
| `./scripts/dev/up.sh` | Start infra + migrate + install + launch (all-in-one) |
| `./scripts/dev/up.sh --skip-install` | Skip pnpm install if deps unchanged |
| `./scripts/dev/test.sh quick` | Run unit tests only (skip integration) |
| `./scripts/dev/test.sh full` | Run all tests including `*IT.java` |
| `./scripts/dev/test.sh integration` | Run integration tests only |
| `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d` | Start dev infrastructure only |
| `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` | Production-mode container topology |

### Runtime Debugging

| Command | Description |
|---------|-------------|
| `pm2 start ecosystem.config.cjs` | First-time start all services |
| `pm2 start all` | Start saved process list |
| `pm2 status` / `pm2 logs` / `pm2 monit` | Inspect running services |
| `pm2 restart ulticode-9001` | Restart backend (after env/code change) |
| `pm2 save` / `pm2 resurrect` | Persist / restore process list |
| `./scripts/start-arthas.sh` | Attach Arthas MCP to Spring Boot JVM (PM2) |
| `java -jar tools/arthas-boot.jar --attach-only --http-port 8563 <PID>` | Manual Arthas attach |

<!-- AUTO-GENERATED -->

---

## Code Style

### Backend (Java)

- Follow Spring Boot conventions
- Use MyBatis-Plus for data access
- DTOs via MapStruct
- Annotations for cross-cutting concerns (`@RateLimit`, `@RequireRole`, etc.)

### Frontend (Vue 3 + TypeScript)

- **Prettier**: No semicolons, single quotes, 100 char print width
- **ESLint**: Flat config enabled
- **Components**: PascalCase naming
- **Composables**: `use` prefix
- **File organization**: By feature, not by type

### Commit Format

```
<type>: <description>

Types: feat, fix, refactor, docs, test, chore, perf, ci
```

---

## Testing

### Backend Tests

- Unit tests: `*Test.java` pattern
- Integration tests: `*IT.java` suffix
- Run unit tests: `./mvnw test`
- Run integration tests: `./mvnw -Dtest='*IT' test`

### Frontend Tests

- Unit tests: Vitest
- E2E tests: Playwright (management frontend)
- Minimum coverage: 80%

---

## Pull Request Checklist

- [ ] Code follows style guidelines
- [ ] Tests pass (unit + integration)
- [ ] No hardcoded secrets
- [ ] Migration scripts are backward-compatible
- [ ] API changes documented
- [ ] Frontend builds successfully

---

## Getting Help

- Check `docs/CODEMAPS/` for architecture documentation
- Review `.env.example` for environment variables
- See `CLAUDE.md` for project-specific guidance
