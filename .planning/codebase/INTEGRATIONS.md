# External Integrations

**Analysis Date:** 2026-04-22

## APIs & External Services

**Code Execution Sandbox:**
- Docker-based code execution
- Configured via `code-execution.sandbox.*` in `application.yml`
- Environment vars: `JUDGE_CONTAINER_ENABLED`, `JUDGE_CONTAINER_IMAGE`, `JUDGE_CONTAINER_POOL_SIZE`
- Uses Docker socket: `/var/run/docker.sock`

**Full-text Search:**
- MeiliSearch
  - SDK: `com.meilisearch.sdk:meilisearch-java:0.20.0`
  - Env vars: `MEILISEARCH_ENABLED`, `MEILISEARCH_HOST` (optional)
  - Used for: Problem search, user search

**Recommendation Service:**
- Dubbo3 RPC - Internal microservices
  - Service: `recommend-web` (port 9005)
  - Registry: Nacos (localhost:28848)
  - Protocol: dubbo (native)
  - Implementation: `com.ulticode:recommend-api:1.0.0`
  - Fallback URL: `http://localhost:28081`

**Payment (Optional):**
- Stripe - Payment processing
  - Env vars: `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`
  - Products: `STRIPE_PRICE_PREMIUM_MONTHLY`, `STRIPE_PRICE_PREMIUM_YEARLY`
  - Status: Configured but disabled

**OAuth (Optional):**
- GitHub OAuth
  - Env vars: `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`
  - Callback: `http://localhost:9001/auth/github/callback`
- Google OAuth
  - Env vars: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
  - Callback: `http://localhost:9001/auth/google/callback`

**Email (Optional):**
- SMTP - Email delivery
  - Env vars: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`
  - Status: Configured but disabled (`EMAIL_ENABLED=false`)

## Data Storage

**Database:**
- MySQL 9.1
  - Host: localhost:23306
  - Connection: `mysql://ulticode:*@localhost:23306/ulticode`
  - ORM: MyBatis-Plus 3.5.16
  - Migration: Flyway (via db-manager CLI)

**Cache:**
- Redis 7 (Alpine)
  - Host: localhost:26379
  - Auth: `REDIS_PASSWORD` required
  - Uses: Session store, rate limiting, caching

**File Storage:**
- Local filesystem via Docker volumes
  - `mysql_data` volume for MySQL data
  - `nacos_logs` volume for Nacos logs
  - `redis_data` volume for Redis persistence

## Authentication & Identity

**Auth Provider:**
- Custom JWT-based authentication
  - Access token: httpOnly cookie (15 min expiry)
  - Refresh token: httpOnly cookie (7 day expiry)
  - CSRF protection via `X-CSRF-Token` header
  - Implementation: `com.ulticode.backend.security` module
  - Cookie secure flag: `JWT_COOKIE_SECURE` (default false for dev)

**Session Management:**
- Redis-backed sessions via Redisson
- Cookie-based JWT storage

## Service Discovery

**Nacos (Service Registry):**
- Nacos 2.3.2 (standalone mode)
- Container: `ulticode-nacos` on ports 8848, 9848
- Used for: Dubbo service registration/discovery
- Database: `nacos_config` in MySQL
- Env vars: `NACOS_HOST`, `NACOS_PORT`, `NACOS_NAMESPACE`, `NACOS_GROUP`

## Monitoring & Observability

**Error Tracking:**
- Not explicitly configured

**Logs:**
- Spring Boot logging to stdout
- PM2 process logs
- Nacos embedded logging

**Health Checks:**
- Spring Actuator: `http://localhost:9001/actuator/health`
- Swagger UI: `http://localhost:9001/swagger-ui.html`
- API docs: `http://localhost:9001/api-docs`
- Docker healthchecks for MySQL, Redis, Nacos

## CI/CD & Deployment

**Hosting:**
- Self-hosted via Docker and PM2
- No cloud platform specified

**Container Orchestration:**
- Docker Compose (development)
  - `docker-compose.yml` - MySQL, Redis, Nacos
  - `docker-compose.prod.yml` - Production stack

**Process Management:**
- PM2 (via `ecosystem.config.cjs`)
  - ulticode-9001 (backend), ulticode-9002 (console), ulticode-9003 (management)
  - ulticode-9004, ulticode-9005 (recommendation, optional)

**Database Migrations:**
- Flyway (via `db-manager` CLI)
- Migrations: `db-manager/migrations/*.sql`
- Commands: `db-manager/.venv/bin/python -m db_manager.cli migrate|status|info|repair`

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
| `JWT_SECRET` | JWT signing key (min 32 chars) | `***` |
| `NACOS_HOST` | Nacos host | `localhost` |
| `NACOS_PORT` | Nacos port | `28848` |

**Optional Environment Variables:**

| Variable | Purpose | Default |
|----------|---------|---------|
| `MEILISEARCH_ENABLED` | Enable MeiliSearch | `false` |
| `MEILISEARCH_HOST` | MeiliSearch URL | - |
| `RECOMMENDATION_ENABLED` | Enable recommendation | `true` |
| `RECOMMENDATION_SERVICE_NAME` | Dubbo service name | `recommend-web` |
| `RECOMMENDATION_TIMEOUT` | RPC timeout (ms) | `5000` |
| `RECOMMENDATION_FALLBACK_URL` | Fallback URL | `http://localhost:28081` |
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
| `JUDGE_CONTAINER_ENABLED` | Enable Docker sandbox | `true` |
| `JUDGE_CONTAINER_IMAGE` | Sandbox image | `ulticode-judge:latest` |

**Secrets Location:**
- `backend-spring/.env` - Backend environment (gitignored)
- `.env` - Root environment (gitignored)
- `.env.example` - Templates (committed, no secrets)

## Webhooks & Callbacks

**Incoming:**
- OAuth callbacks: `/auth/{provider}/callback` (GitHub, Google)
- Stripe webhooks (configured but not enabled)

**Outgoing:**
- Nacos service registration (Dubbo)

---

*Integration audit: 2026-04-22*
