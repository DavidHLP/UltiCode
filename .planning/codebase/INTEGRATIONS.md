# External Integrations

**Analysis Date:** 2026-04-19

## APIs & External Services

**Code Execution Sandbox:**
- Docker-based code execution
- Configured via `code-execution.sandbox.*` in `application.yml`
- Environment vars: `SANDBOX_ENABLED`, `SANDBOX_IMAGE`, `SANDBOX_MEMORY`, `SANDBOX_CPUS`, `SANDBOX_TIMEOUT`

**Full-text Search:**
- MeiliSearch (optional)
- SDK: `com.meilisearch.sdk:meilisearch-java` 0.20.0
- Configured via `meilisearch.*` in `application.yml`
- Environment vars: `MEILISEARCH_ENABLED`, `MEILISEARCH_HOST`, `MEILISEARCH_API_KEY`

**Recommendation Service:**
- Dubbo3 RPC service (optional)
- Apache Spark for offline processing
- Service discovery via Nacos
- Configured via `recommendation.*` in `application.yml`
- Environment vars: `RECOMMENDATION_ENABLED`, `RECOMMENDATION_SERVICE_URL`, `RECOMMENDATION_NACOS_ENABLED`

## Data Storage

**Primary Database:**
- MySQL 9.1
- Connection: `jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:23306}/${DB_NAME:ulticode}`
- ORM: MyBatis-Plus 3.5.16
- Default credentials: `ulticode:ulticode` (dev)
- Container: `ulticode-mysql` on port 23306

**Cache & Sessions:**
- Redis 7 (Alpine)
- Connection: `redis://${REDIS_HOST:localhost}:${REDIS_PORT:26379}`
- Client: Redisson 4.3.1 (distributed locks), Lettuce (Spring Data Redis)
- Auth: `REDIS_PASSWORD` required
- Container: `ulticode-redis` on port 26379

**File Storage:**
- Local filesystem (backup directory)
- Configured via `backup.dir` in `application.yml`
- Default: `/tmp/backups`

## Authentication & Identity

**Primary Auth:**
- Custom JWT-based authentication
- Access token: 15 minutes expiry
- Refresh token: 7 days expiry
- Stored in httpOnly cookies (`access_token`, `refresh_token`)
- CSRF protection via `X-CSRF-Token` header

**OAuth 2.0 Providers:**
- GitHub OAuth
  - Config: `oauth.github.*` in `application.yml`
  - Env vars: `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`
  - Scopes: `user:email`
- Google OAuth
  - Config: `oauth.google.*` in `application.yml`
  - Env vars: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
  - Scopes: `email,profile`

## Service Discovery

**Nacos (Service Registry):**
- Nacos 2.3.2 (standalone mode)
- Container: `ulticode-nacos` on ports 8848, 9848
- Used for: Dubbo service registration/discovery
- Env vars: `NACOS_HOST`, `NACOS_PORT`, `NACOS_USERNAME`, `NACOS_PASSWORD`
- Database: `nacos_config` in MySQL

## Monitoring & Observability

**Error Tracking:**
- Not detected (no Sentry, Bugsnag, or similar)

**Logs:**
- Spring Boot logging to stdout
- Pattern: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`
- Log level: `INFO` (root), configurable per package

**Health Checks:**
- Spring Actuator: `/actuator/health`
- Docker health checks on MySQL and Redis

## CI/CD & Deployment

**Container Orchestration:**
- Docker Compose (development)
- `docker-compose.yml` - MySQL, Redis, Nacos
- `docker-compose.prod.yml` - Production stack

**Process Management:**
- PM2 (via `ecosystem.config.cjs`)
- Services: `ulticode-9001` (backend), `ulticode-9002` (console), `ulticode-9003` (management), `ulticode-9004/9005` (recommendation)

**Database Migrations:**
- Flyway (via `db-manager` CLI)
- Migrations: `db-manager/migrations/*.sql`
- Commands: `python -m db_manager.cli migrate|status|info|repair`

## Environment Configuration

**Required Environment Variables:**

| Variable | Purpose | Example |
|----------|---------|---------|
| `DB_HOST` | MySQL host | `localhost` |
| `DB_PORT` | MySQL port | `23306` |
| `DB_NAME` | Database name | `ulticode` |
| `DB_USER` | Database user | `ulticode` |
| `DB_PASSWORD` | Database password | `***` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `26379` |
| `REDIS_PASSWORD` | Redis auth | `***` |
| `JWT_SECRET` | JWT signing key | `***` |
| `NACOS_HOST` | Nacos host | `localhost` |
| `NACOS_PORT` | Nacos port | `28848` |

**Optional Environment Variables:**

| Variable | Purpose | Default |
|----------|---------|---------|
| `MEILISEARCH_ENABLED` | Enable MeiliSearch | `false` |
| `MEILISEARCH_HOST` | MeiliSearch URL | - |
| `MEILISEARCH_API_KEY` | MeiliSearch API key | - |
| `RECOMMENDATION_ENABLED` | Enable recommendation | `false` |
| `RECOMMENDATION_SERVICE_URL` | Recommendation Dubbo URL | - |
| `SMTP_HOST` | Mail server | `localhost` |
| `SMTP_PORT` | Mail port | `587` |
| `SMTP_USER` | Mail username | - |
| `SMTP_PASSWORD` | Mail password | - |
| `EMAIL_ENABLED` | Enable email | `false` |
| `GITHUB_CLIENT_ID` | GitHub OAuth ID | - |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth secret | - |
| `GOOGLE_CLIENT_ID` | Google OAuth ID | - |
| `GOOGLE_CLIENT_SECRET` | Google OAuth secret | - |
| `CORS_ALLOWED_ORIGINS` | CORS origins | `http://localhost:9002,http://localhost:9003` |
| `JWT_COOKIE_SECURE` | Secure cookies | `false` |

**Secrets Location:**
- `backend-spring/.env` - Backend environment (gitignored)
- `.env` - Root environment (gitignored)
- `.env.example` - Templates (committed, no secrets)

## Webhooks & Callbacks

**Incoming:**
- OAuth callbacks: `/auth/{provider}/callback` (GitHub, Google)

**Outgoing:**
- Not detected (no outbound webhooks)

---

*Integration audit: 2026-04-19*
