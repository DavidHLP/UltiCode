# Secrets and Configuration Mapping

This document cross-references all configuration sources across the UltiCode project. Use it for onboarding new contributors and setting up CI/CD pipelines.

## Configuration Sources

| # | Source | File(s) | Purpose |
|---|--------|---------|---------|
| 1 | GitHub Actions Secrets | Repository Settings > Secrets and variables > Actions | Secure values injected into CI workflows (not stored in repo) |
| 2 | Docker Compose | `docker-compose.yml` | Local development infrastructure (MySQL, Redis, Nacos) |
| 3 | Spring Boot Profiles | `backend-spring/src/main/resources/application-{dev,prod,ci,example}.yml` | Backend runtime configuration per environment |
| 4 | Vite Environment Variables | `console/.env*`, `management/.env*` | Frontend build-time configuration (`VITE_*` prefix) |
| 5 | PM2 Ecosystem Config | `ecosystem.config.cjs` | Local development process manager environment |
| 6 | Backend `.env` | `backend-spring/.env` | Local development overrides for Spring Boot |

## Variable Mapping Table

### Database

| Variable | GitHub Secret | Spring Profile | Docker Compose | PM2 | Vite | Backend .env |
|----------|---------------|----------------|----------------|-----|------|--------------|
| `DB_HOST` | N/A (CI uses default: `localhost`) | `application-dev.yml`, `application-ci.yml`, `application.yml` (default: `localhost`) | N/A (hardcoded as `mysql` host internally) | N/A | N/A | required |
| `DB_PORT` | N/A (CI uses default: `23306`) | `application-dev.yml`, `application-ci.yml`, `application.yml` (default: `23306`) | N/A (hardcoded as `3306` internally) | N/A | N/A | required |
| `DB_USER` | N/A (CI uses default: `ulticode`) | `application-dev.yml`, `application-ci.yml`, `application.yml` (default: `ulticode`) | `MYSQL_USER` (default: `ulticode`) | N/A | N/A | required |
| `DB_PASSWORD` | N/A (CI uses default: `ulticode`) | `application-dev.yml`, `application-ci.yml`, `application.yml` (default: `ulticode`) | `MYSQL_PASSWORD` (required) | N/A | N/A | required |
| `DB_NAME` | N/A (CI uses default: `ulticode_test`) | `application-dev.yml` (default: `ulticode`), `application-ci.yml` (default: `ulticode_test`) | `MYSQL_DATABASE` (default: `ulticode`) | N/A | N/A | required |
| `MYSQL_ROOT_PASSWORD` | `MYSQL_ROOT_PASSWORD` | N/A | `MYSQL_ROOT_PASSWORD` (required) | N/A | N/A | N/A |

### Redis

| Variable | GitHub Secret | Spring Profile | Docker Compose | PM2 | Vite | Backend .env |
|----------|---------------|----------------|----------------|-----|------|--------------|
| `REDIS_HOST` | N/A (CI uses default: `localhost`) | `application-dev.yml`, `application-ci.yml`, `application.yml` (default: `localhost`) | N/A (hardcoded as `redis` host internally) | N/A | N/A | required |
| `REDIS_PORT` | N/A (CI uses default: `26379`) | `application-dev.yml`, `application-ci.yml`, `application.yml` (default: `26379`) | `REDIS_PORT` (default: `26379`, maps to container `6379`) | N/A | N/A | required |
| `REDIS_PASSWORD` | N/A (CI uses default: empty) | `application-dev.yml` (default: empty), `application-ci.yml` (default: empty), `application.yml` (default: empty) | `REDIS_PASSWORD` (required) | `REDIS_PASSWORD` (from `.env`) | N/A | required |
| `REDIS_DB` | N/A | `application.yml` (default: `0`) | N/A | N/A | N/A | N/A |

### JWT

| Variable | GitHub Secret | Spring Profile | Docker Compose | PM2 | Vite | Backend .env |
|----------|---------------|----------------|----------------|-----|------|--------------|
| `JWT_SECRET` | `JWT_SECRET` | `application-dev.yml` (required), `application-ci.yml` (default: test value), `application.yml` (default: empty) | N/A | N/A | N/A | required |
| `JWT_COOKIE_SECURE` | N/A | `application-prod.yml` (hardcoded: `true`), `application.yml` (default: `false`) | N/A | N/A | N/A | N/A |

### Nacos

| Variable | GitHub Secret | Spring Profile | Docker Compose | PM2 | Vite | Backend .env |
|----------|---------------|----------------|----------------|-----|------|--------------|
| `NACOS_HOST` | N/A | `application.yml` (default: `localhost`) | N/A | N/A | N/A | N/A |
| `NACOS_PORT` | N/A | `application.yml` (default: `28848`) | `NACOS_PORT` (default: `28848`) | `NACOS_PORT` (from `.env`, default: `28848`) | N/A | required |
| `NACOS_USERNAME` | N/A | `application.yml` (default: `nacos`) | N/A | `NACOS_USERNAME` (from `.env`) | N/A | required |
| `NACOS_PASSWORD` | N/A | `application.yml` (default: `nacos`) | N/A | `NACOS_PASSWORD` (from `.env`) | N/A | required |

### Server

| Variable | GitHub Secret | Spring Profile | Docker Compose | PM2 | Vite | Backend .env |
|----------|---------------|----------------|----------------|-----|------|--------------|
| `SERVER_PORT` | N/A | `application-dev.yml` (default: `9001`), `application-ci.yml` (`0`), `application.yml` (default: `9001`) | N/A | N/A | N/A | N/A |
| `CORS_ALLOWED_ORIGINS` | `CORS_ALLOWED_ORIGINS` | `application-prod.yml` (required), `application.yml` (default: `http://localhost:9002,...`) | N/A | N/A | N/A | N/A |

### OAuth

| Variable | GitHub Secret | Spring Profile | Docker Compose | PM2 | Vite | Backend .env |
|----------|---------------|----------------|----------------|-----|------|--------------|
| `GITHUB_CLIENT_ID` | `GITHUB_CLIENT_ID` | `application.yml` (default: empty) | N/A | N/A | N/A | required |
| `GITHUB_CLIENT_SECRET` | `GITHUB_CLIENT_SECRET` | `application.yml` (default: empty) | N/A | N/A | N/A | required |
| `GOOGLE_CLIENT_ID` | `GOOGLE_CLIENT_ID` | `application.yml` (default: empty) | N/A | N/A | N/A | required |
| `GOOGLE_CLIENT_SECRET` | `GOOGLE_CLIENT_SECRET` | `application.yml` (default: empty) | N/A | N/A | N/A | required |

### Frontend

| Variable | GitHub Secret | Spring Profile | Docker Compose | PM2 | Vite | Backend .env |
|----------|---------------|----------------|----------------|-----|------|--------------|
| `VITE_API_BASE_URL` | N/A | N/A | N/A | N/A | `console/.env`, `management/.env` | N/A |

### Other

| Variable | GitHub Secret | Spring Profile | Docker Compose | PM2 | Vite | Backend .env |
|----------|---------------|----------------|----------------|-----|------|--------------|
| `SPRING_PROFILES_ACTIVE` | N/A | N/A | N/A | `dev` (hardcoded) | N/A | N/A |
| `RECOMMENDATION_ENABLED` | N/A | `application.yml` (default: `false`) | N/A | `true` (hardcoded) | N/A | N/A |
| `SPRINGDOC_ENABLED` | N/A | `application.yml` (default: `false`), `application-dev.yml` (`true`) | N/A | N/A | N/A | N/A |
| `SMTP_HOST` | `SMTP_HOST` | `application.yml` (default: `localhost`) | N/A | N/A | N/A | required |
| `SMTP_PORT` | `SMTP_PORT` | `application.yml` (default: `587`) | N/A | N/A | N/A | required |
| `SMTP_USER` | `SMTP_USER` | `application.yml` (default: empty) | N/A | N/A | N/A | required |
| `SMTP_PASSWORD` | `SMTP_PASSWORD` | `application.yml` (default: empty) | N/A | N/A | N/A | required |
| `MEILISEARCH_API_KEY` | `MEILISEARCH_API_KEY` | `application.yml` (default: empty) | N/A | N/A | N/A | required |

## GitHub Secrets Setup Checklist

The following secrets must be configured in **GitHub repository Settings > Secrets and variables > Actions** for CI/CD workflows to function:

### Required for CI Tests (ci-backend.yml)

- `JWT_SECRET` -- Backend test JWT secret (CI uses a built-in default if not set)

### Required for Production Deployment

- `MYSQL_ROOT_PASSWORD` -- Root password for MySQL container
- `DB_PASSWORD` -- Application database user password
- `REDIS_PASSWORD` -- Redis authentication password
- `CORS_ALLOWED_ORIGINS` -- Allowed CORS origins for production API
- `JWT_SECRET` -- Production JWT signing key
- `GITHUB_CLIENT_ID` -- GitHub OAuth app client ID
- `GITHUB_CLIENT_SECRET` -- GitHub OAuth app client secret
- `GOOGLE_CLIENT_ID` -- Google OAuth app client ID
- `GOOGLE_CLIENT_SECRET` -- Google OAuth app client secret
- `SMTP_HOST` -- SMTP server hostname for email
- `SMTP_PORT` -- SMTP server port
- `SMTP_USER` -- SMTP authentication username
- `SMTP_PASSWORD` -- SMTP authentication password
- `MEILISEARCH_API_KEY` -- MeiliSearch API key (if search is enabled)

> **Note:** CI test workflows (`ci-backend.yml`, `ci-frontend.yml`) use built-in defaults and service containers. Most GitHub Secrets are only needed for production deployment workflows.

## Local Development Setup

For local development, configuration is primarily driven by:

1. **`docker-compose.yml`** -- Start infrastructure services (MySQL, Redis, Nacos). Required env vars: `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, `REDIS_PASSWORD`.
2. **`backend-spring/.env`** -- Backend Spring Boot overrides. Required env vars: `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `JWT_SECRET`.
3. **`ecosystem.config.cjs`** -- PM2 process manager. Loads `.env` via `dotenv`. The `SPRING_PROFILES_ACTIVE=dev` profile is hardcoded for local development.

Typical local `.env` (root of project, not committed):

```env
MYSQL_ROOT_PASSWORD=root
DB_PASSWORD=ulticode
REDIS_PASSWORD=your-redis-password
JWT_SECRET=your-local-dev-secret-key-at-least-32-chars
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos
```
