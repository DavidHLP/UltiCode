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

<!-- AUTO-GENERATED: Scripts from package.json -->

### Console Frontend (console/)

| Command | Description |
|---------|-------------|
| `pnpm dev` | Start dev server with lint + type-check + format + test |
| `pnpm build` | Production build with type checking |
| `pnpm preview` | Preview production build locally |
| `pnpm type-check` | Run vue-tsc type checker |
| `pnpm lint` | ESLint with auto-fix |
| `pnpm format` | Prettier auto-format (no semicolons, single quotes) |
| `pnpm test` | Vitest unit tests |
| `pnpm test:watch` | Vitest in watch mode |
| `pnpm test:coverage` | Vitest with coverage report |
| `pnpm validate:mocks` | Validate mock data files |
| `pnpm validate:mocks:verbose` | Validate mock data (verbose output) |
| `pnpm validate:mocks:strict` | Validate mock data (strict mode) |

### Management Frontend (management/)

Same commands as console, plus:

| Command | Description |
|---------|-------------|
| `pnpm validate:i18n-keys` | Validate i18n key consistency |

### Backend (backend-spring/)

| Command | Description |
|---------|-------------|
| `./mvnw spring-boot:run` | Start Spring Boot application |
| `./mvnw package -DskipTests` | Build JAR |
| `./mvnw test` | Unit tests (excludes `*IT.java`) |
| `./mvnw -Dtest='*IT' test` | Integration tests with Testcontainers |
| `./mvnw verify` | Tests plus JaCoCo report/check |
| `./mvnw compile` | Compile only |

> Note: Backend is started via PM2 using `start.cjs` wrapper (see `ecosystem.config.cjs`). |

### Database Migrations

| Command | Description |
|---------|-------------|
| `cd init-db && mvn flyway:info` | View migration status |
| `cd init-db && mvn flyway:migrate` | Apply migrations |
| `cd init-db && mvn flyway:baseline` | Create baseline for existing DB |

Migration naming: `V{YYYYMMDDHHMMSS}__{Description}.sql`

### Docker

| Command | Description |
|---------|-------------|
| `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d` | Start dev infrastructure |
| `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` | Production mode |

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
