# Contributing to UltiCode

Welcome to the UltiCode project! This guide will help you get started with development.

---

## Prerequisites

- **Java 17+** - Required for backend development
- **Node.js 20+** - Required for frontend development
- **pnpm 9+** - Package manager for frontends
- **Docker & Docker Compose** - For local infrastructure
- **Python 3.10+** - For db-manager CLI

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
# Copy environment template
cp .env.example .env

# Edit .env and set strong passwords:
# - DB_PASSWORD (MySQL)
# - MYSQL_ROOT_PASSWORD
# - REDIS_PASSWORD
# - JWT_SECRET (min 32 chars)
```

### 3. Start Infrastructure

```bash
# Start MySQL, Redis, Nacos
docker-compose up -d

# Verify services are running
docker-compose ps
```

### 4. Run Database Migrations

```bash
# Setup db-manager
cd db-manager
python3 -m venv .venv
source .venv/bin/activate
pip install -e .

# Apply migrations
db-manager migrate

# Verify migration status
db-manager info
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
| `pnpm type-check` | Run vue-tsc type checker |
| `pnpm lint` | ESLint with auto-fix |
| `pnpm format` | Prettier auto-format (no semicolons, single quotes) |
| `pnpm test` | Vitest unit tests |
| `pnpm test:watch` | Vitest in watch mode |
| `pnpm test:coverage` | Vitest with coverage report |

### Management Frontend (management/)

Same commands as console. Additional:

| Command | Description |
|---------|-------------|
| `pnpm validate:i18n-keys` | Validate i18n key consistency |

### Backend (backend-spring/)

| Command | Description |
|---------|-------------|
| `./mvnw spring-boot:run` | Start Spring Boot application |
| `./mvnw package -DskipTests` | Build JAR |
| `./mvnw test` | Unit tests (excludes *IT.java) |
| `./mvnw verify -Pci` | Integration tests with Testcontainers |
| `./mvnw compile` | Compile only |

### Database Manager (db-manager/)

| Command | Description |
|---------|-------------|
| `db-manager migrate` | Apply pending migrations |
| `db-manager migrate --dry-run` | Preview migrations |
| `db-manager info` | Show migration status |
| `db-manager repair` | Fix metadata inconsistencies |
| `db-manager validate` | Validate migration state |
| `db-manager baseline` | Baseline existing database |
| `db-manager clean --force` | DANGER: Drop all objects |

### Docker

| Command | Description |
|---------|-------------|
| `docker-compose up -d` | Start dev infrastructure |
| `docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d` | Production mode |

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
- Run integration tests: `./mvnw verify -Pci`

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
