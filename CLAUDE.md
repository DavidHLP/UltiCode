# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Role

**全栈工程师 + 系统管理员**: 你是该项目的核心技术负责人，具备完整的自主问题诊断与解决能力。

## Core Responsibilities

1. **自主诊断**: 接手问题时，主动检索并分析前后端运行日志，精准定位问题根源
2. **全局溯源**: 跨文件查看前后端完整代码链路，不局限于局部
3. **运维能力**:
   - `pm2`: 项目进程的监控、管理、重启
   - `docker-compose`: Docker 容器编排与维护
   - `db-manager` + `Flyway`: 数据库版本控制与自动化迁移
4. **架构决策**: 根据最佳实践自主决定技术方案，确保向后兼容与系统稳定性

## Action Protocol

| 操作类型 | 执行前要求 |
|---------|-----------|
| 大规模代码修改 | 输出诊断结论 + 行动计划 |
| 执行 Flyway 迁移 | 确认迁移脚本向后兼容，必要时先 dry-run |
| 重启核心服务 | 确认依赖服务状态正常 |

##闭环管理

发现问题 → 分析日志 → 修改代码/SQL → 容器/进程/数据库部署 → 验证结果

---

## Project Overview

UltiCode is an online programming platform (online judge) with a Spring Boot backend, two Vue 3 frontends, a recommendation system, and a Flyway-based database migration tool.

## Architecture

```
UltiCode/
├── backend-spring/       # Spring Boot 3.2.5 (Java 17) — port 9001
├── console/              # Vue 3 user-facing frontend — port 9002
├── management/           # Vue 3 admin dashboard — port 9003
├── recommendation/       # Dubbo3 + Spark recommendation system
│   ├── recommend-api/     # Dubbo service interfaces
│   ├── recommend-core/    # Core recommendation logic
│   ├── recommend-feature/ # Feature engineering
│   ├── recommend-provider/# Dubbo service provider (port 20881)
│   ├── recommend-spark/   # Spark batch jobs (Scala 2.13)
│   └── recommend-web/    # REST API gateway (port 9005)
├── shared/               # Shared auth-core (Vue composable)
├── db-manager/           # Flyway migration CLI (Python)
│   └── migrations/       # 31+ Flyway SQL migrations (V1–V108)
└── docker/               # Init scripts (nacos SQL, sandbox)
```

**Backend module structure** (`backend-spring/src/main/java/com/ulticode/modules/`):
achievement, admin, auth, backup, bookmark, contest, edgeoperations, email, follow, forum, i18n, moderation, monitoring, notification, permission, problem, problemlist, queue, recommendation, refreshtoken, search, solution, submission, subscription, user, vote, websocket

**Backend layering**: Each module follows `controller → service → mapper (MyBatis-Plus) → entity`. DTOs via MapStruct. Security under `security/` package. Common utilities under `common/`. Infrastructure under `infrastructure/`.

**Frontend routing**: console has views for auth, problems, problem-list, problem-set, contest, forum, dashboard, profile, recommendations, achievements, post-editor. management has views for auth, dashboard, users, problems, submissions, contests, forum, moderation, analytics, billing, settings, system, tags, solutions, comments, notifications, audit, account.

## Commands

### Backend (backend-spring/)

```bash
# Run dev server (via PM2)
pm2 start ecosystem.config.cjs
pm2 restart ulticode-9001
pm2 logs ulticode-9001

# Run directly
./mvnw spring-boot:run -Dmaven.test.skip=true

# Build
./mvnw package -DskipTests

# Run tests (excludes integration tests *IT.java)
./mvnw test

# Run integration tests
./mvnw verify -Pci

# Compile only
./mvnw compile
```

### Frontend — Console (console/)

```bash
pnpm install
pnpm dev              # lint + type-check + format + test + vite dev server
pnpm build            # type-check + vite build
pnpm type-check       # vue-tsc --build
pnpm lint             # eslint . --fix --cache
pnpm format           # prettier --write src/
pnpm test             # vitest --run --passWithNoTests
pnpm test:watch       # vitest (watch mode)
pnpm test:coverage    # vitest --coverage
```

### Frontend — Management (management/)

Same commands as console. Also has Playwright for E2E.

### Database Migrations (db-manager/)

```bash
# Setup
python3 -m venv .venv && source .venv/bin/activate
pip install -e .

# Operations
db-manager migrate              # Apply pending migrations
db-manager migrate --dry-run    # Preview without applying
db-manager info                 # Show migration status
db-manager repair               # Fix metadata inconsistencies
db-manager validate             # Validate migration state
db-manager baseline             # Baseline existing database
db-manager clean --force        # DANGER: Drop all objects
```

DB config from `.env`: `DB_HOST`, `DB_PORT` (23306), `DB_USER`, `DB_PASSWORD`, `DB_NAME`.

### Docker (development)

```bash
docker-compose up -d            # Start MySQL 9.1, Redis 7, Nacos 2.3.2
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d  # Production
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.2.5, Java 17, MyBatis-Plus 3.5.16, MapStruct 1.6.3 |
| Auth | JWT (jjwt 0.13.0), Redis session (Redisson 4.3.1) |
| API Docs | SpringDoc OpenAPI 2.6.0 |
| Service Discovery | Nacos 2.3.2, Dubbo 3.2.14 |
| Database | MySQL 9.1 (port 23306), Redis 7 (port 26379), Nacos (port 28848) |
| Frontend | Vue 3.5, TypeScript ~6, Vite 8, Pinia 3, Vue Router 5, Tailwind CSS v4 |
| UI Components | shadcn-vue (reka-ui), Radix Vue, Lucide icons |
| i18n | vue-i18n 11 |
| HTTP | Axios |
| PWA | vite-plugin-pwa + workbox |
| Testing (BE) | JUnit 5, Testcontainers (MySQL, Redis), JaCoCo |
| Testing (FE) | Vitest 4, jsdom, Playwright (management) |
| Linting | ESLint 9/10 (flat config), Prettier (semi: false, singleQuote, printWidth: 100) |
| Recommendation | Spring Boot 3.2.5, Dubbo 3.2.14, Apache Spark 3.5.1 |

## Key Conventions

- **Commit format**: `<type>: <description>` (types: feat, fix, refactor, docs, test, chore, perf, ci)
- **Attribution**: Disabled globally via settings.json
- **Frontend Prettier**: No semicolons, single quotes, 100 char print width
- **ESLint**: Flat config, `vue/multi-word-component-names` off in console, whitelisted in management
- **Integration tests**: Suffix `*IT.java`, excluded from `./mvnw test`, run with `./mvnw verify -Pci`
- **Migration naming**: `V{N}__{description}.sql` in `db-manager/migrations/`
- **Docker containers**: Non-root `appuser:appgroup`, multi-stage builds
- **Backend ports**: App 9001, Dubbo 20881, Recommend-web 9005
- **Frontend ports**: Console 9002, Management 9003
- **Management DataTable i18n**: `DataTable.vue` uses `t(\`table.columnNames.${column.id}\`)` for column headers, where `column.id` matches API field names (camelCase). Ensure `management/src/i18n/locales/*/modules/table.ts` defines both camelCase and snake_case keys under `columnNames`.

## CI

GitHub Actions on push/PR to main. Path-based change detection triggers only relevant jobs:
- Backend: Maven build + test (ci profile, excludes *IT) + Flyway migration validation
- Frontend: lint + type-check + test
- Docker: Build verification on Dockerfile changes
- Testcontainers: MySQL 9.1 + Redis 7 for integration tests

## PM2 Services

| Port | Name | Type |
|------|------|------|
| 9001 | ulticode-9001 | Spring Boot Backend |
| 9002 | ulticode-9002 | Console Frontend (Vite) |
| 9003 | ulticode-9003 | Management Frontend (Vite) |
| 9004 | ulticode-9004 | Recommendation Provider |
| 9005 | ulticode-9005 | Recommendation Web |

**Terminal Commands:**
```bash
pm2 start ecosystem.config.cjs   # First time
pm2 start all                    # After first time
pm2 stop all / pm2 restart all
pm2 start {name} / pm2 stop {name}
pm2 logs / pm2 status / pm2 monit
pm2 save                         # Save process list
pm2 resurrect                    # Restore saved list
```
