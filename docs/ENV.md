# Environment Variables

<!-- Generated: 2026-06-19 | Source: .env.example, .env.test.example, ecosystem.config.cjs, application.yml -->

> **Do not** copy `.env.example` verbatim and reuse the placeholders. Run
> `./scripts/dev/init-env.sh` to generate a private `.env` with random credentials.
> The placeholder values shown below (`GENERATED_BY_…`) are markers — `up.sh` will
> refuse to start if any required variable still holds one of them.

The root `.env` is the **single source of truth** for both PM2 processes (read
via `ecosystem.config.cjs::parseEnvFile`) and docker-compose (read via
`--env-file`). Anything you put in PM2's `env.*` block overrides `.env` —
**never** put secrets in `ecosystem.config.cjs`.

## Required (startup will fail if missing)

| Variable                  | Source              | Example                                              | Description                                                                 |
| ------------------------- | ------------------- | ---------------------------------------------------- | --------------------------------------------------------------------------- |
| `DB_USER`                 | `.env`              | `ulticode`                                           | MySQL application user (must match `^[A-Za-z0-9_]+$`)                       |
| `DB_PASSWORD`             | `.env`              | `<random 24 chars>`                                  | MySQL application user password                                             |
| `DB_NAME`                 | `.env`              | `ulticode`                                           | MySQL database name                                                         |
| `MYSQL_ROOT_PASSWORD`     | `.env`              | `<random 24 chars>`                                  | MySQL root password (also used by Nacos for its own DB)                     |
| `DB_ROOT_PASSWORD`        | `.env`              | `<random 24 chars>`                                  | Aliased root password (kept in sync with `MYSQL_ROOT_PASSWORD`)             |
| `REDIS_PASSWORD`          | `.env`              | `<random 24 chars>`                                  | Redis `requirepass`                                                         |
| `JWT_SECRET`              | `.env`              | `<random ≥ 32 chars>`                                | JWT signing secret. **Must be ≥ 32 characters** (enforced by `application.yml`) |
| `NACOS_USERNAME`          | `.env`              | `ulticode_dev_admin`                                 | Nacos admin user (bootstrapped by `scripts/security/bootstrap-nacos-user.sh`) |
| `NACOS_PASSWORD`          | `.env`              | `<random>`                                           | Nacos admin password                                                        |
| `NACOS_AUTH_TOKEN`        | `.env`              | `<random base64>`                                    | Nacos server identity token                                                 |
| `NACOS_AUTH_IDENTITY_KEY` | `.env`              | `<random string>`                                    | Nacos auth identity key (server-side)                                       |
| `NACOS_AUTH_IDENTITY_VALUE` | `.env`            | `<random string>`                                    | Nacos auth identity value (server-side)                                     |

## Runtime Topology (optional — sensible defaults)

| Variable               | Default               | Description                                                  |
| ---------------------- | --------------------- | ------------------------------------------------------------ |
| `SPRING_PROFILES_ACTIVE` | `dev`               | Spring profile (`dev` / `test` / `prod` / `features-on` / `features-off`) |
| `SERVER_PORT`          | `9001`                | Backend HTTP port                                            |
| `CONSOLE_PORT`         | `9002`                | Console Vite dev port (informational)                        |
| `MANAGEMENT_PORT`      | `9003`                | Management Vite dev port (informational)                     |
| `DB_HOST`              | `localhost`           | MySQL host (container: `mysql` in compose)                   |
| `DB_PORT`              | `23306`               | MySQL host port (dev override; container `3306`)             |
| `REDIS_HOST`           | `localhost`           | Redis host (container: `redis` in compose)                   |
| `REDIS_PORT`           | `26379`               | Redis host port (dev override; container `6379`)             |
| `REDIS_DB`             | `0`                   | Logical Redis DB index (test profile uses `15` to isolate)  |
| `FRONTEND_URL`         | `http://localhost:9002` | Used for OAuth redirect base + CORS                         |

## JWT & Browser Security (optional)

| Variable               | Default                                                              | Description |
| ---------------------- | -------------------------------------------------------------------- | ----------- |
| `JWT_COOKIE_SECURE`    | `false`                                                              | Set `true` in prod (HTTPS only) — dev runs on plain HTTP |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:9002,http://localhost:9003,...`                    | Comma-separated origin list. PM2 injects its own narrower value |
| `JWT_SECRET`           | *(required)*                                                         | HMAC-SHA256 signing key for `access_token` (jjwt 0.13) |

## Nacos (optional — defaults bind to localhost:28848)

| Variable                 | Default          | Description                                   |
| ------------------------ | ---------------- | --------------------------------------------- |
| `NACOS_SERVER_ADDR`      | `localhost:28848` | `<host>:<port>` for client bootstrap          |
| `NACOS_HOST`             | `localhost`      | Mirror of host (used in some clients)         |
| `NACOS_PORT`             | `28848`          | Mirror of port                                |
| `NACOS_GRPC_PORT`        | `29848`          | Nacos gRPC port (RAFT/cluster)                |
| `NACOS_NAMESPACE`        | `public`         | Tenant namespace                              |
| `NACOS_GROUP`            | `DEFAULT_GROUP`  | Default config group                          |

## Local Feature Toggles (optional)

| Variable                | Default                    | Description |
| ----------------------- | -------------------------- | ----------- |
| `SPRINGDOC_ENABLED`     | `true`                     | SpringDoc OpenAPI UI at `/swagger-ui.html` |
| `MEILISEARCH_ENABLED`   | `false`                    | Meilisearch full-text indexer               |
| `SANDBOX_ENABLED`       | `false`                    | Local sandbox runner toggle (prod uses remote runner) |
| `SANDBOX_IMAGE`         | `ulticode-sandbox:latest`  | Docker image for code execution              |
| `SANDBOX_MEMORY`        | `256m`                     | Per-container memory cap                     |
| `SANDBOX_CPUS`          | `1.0`                      | Per-container CPU quota (cgroup)             |
| `SANDBOX_TIMEOUT`       | `10`                       | Wall-clock timeout (seconds) per test case   |
| `SANDBOX_PIDS_LIMIT`    | `128`                      | cgroup `pids.max`                            |
| `SANDBOX_SECCOMP_PROFILE` | `docker/sandbox/seccomp-profile.json` | seccomp filter path           |
| `BACKUP_DIR`            | `/tmp/ulticode-backups`    | Where BackupService writes dumps             |
| `AUDIT_EXPORT_LIMIT`    | `10000`                    | Max rows in audit export CSV                 |

## One-Time Bootstrap (keep `false` during normal startup)

| Variable                       | Default | Description                                  |
| ------------------------------ | ------- | -------------------------------------------- |
| `APP_BOOTSTRAP_ADMIN_ENABLED`  | `false` | When `true`, `AdminBootstrapRunner` creates an initial admin |
| `APP_BOOTSTRAP_ADMIN_USERNAME`| empty   | Username for bootstrap admin                 |
| `APP_BOOTSTRAP_ADMIN_EMAIL`    | empty   | Email for bootstrap admin                    |
| `APP_BOOTSTRAP_ADMIN_PASSWORD` | empty   | Password for bootstrap admin (must be ≥ 12 chars) |

## Dev Seed Users (disposable, dev-profile only)

These are **only** honored when `SPRING_PROFILES_ACTIVE=dev`. They create a
predictable local admin so the dev loop is self-service. **Never** enable them in
`prod` profile.

| Variable                   | Default            | Description |
| -------------------------- | ------------------ | ----------- |
| `DEV_SEED_USERS_ENABLED`   | `true`             | Master switch |
| `DEV_SEED_ADMIN_USERNAME`  | `admin`            | Disposable admin username |
| `DEV_SEED_ADMIN_EMAIL`     | `admin@localhost.test` | Disposable admin email |
| `DEV_SEED_ADMIN_PASSWORD`  | `admin123`         | Disposable admin password (matches `VITE_TEST_PASSWORD`) |
| `DEV_SEED_ADMIN_ROLE`      | `ADMIN`            | Role for disposable admin |

## Optional Integrations (empty credentials = disabled)

| Variable                          | Default                                  | Description |
| --------------------------------- | ---------------------------------------- | ----------- |
| `GITHUB_CLIENT_ID` / `_SECRET`    | empty                                    | GitHub OAuth (dev only) |
| `GITHUB_REDIRECT_URI`             | `http://localhost:9001/auth/github/callback` | OAuth callback URL |
| `GOOGLE_CLIENT_ID` / `_SECRET`    | empty                                    | Google OAuth (dev only) |
| `GOOGLE_REDIRECT_URI`             | `http://localhost:9001/auth/google/callback` | OAuth callback URL |
| `SMTP_HOST` / `SMTP_PORT`         | `localhost` / `1025`                     | Mail relay (dev: MailHog) |
| `SMTP_USER` / `SMTP_PASSWORD`     | empty                                    | SMTP auth |
| `EMAIL_ENABLED`                   | `false`                                  | Master switch for outbound email |
| `EMAIL_FROM_NAME`                 | `UltiCode Dev`                           | From-header name |

## Frontend (read at Vite build/dev time — `VITE_` prefix)

| Variable                | Default                  | Description |
| ----------------------- | ------------------------ | ----------- |
| `VITE_API_BASE_URL`     | `http://localhost:9001`  | Backend origin for `apiGet/apiPost` in console + management |
| `VITE_ADMIN_BASE_URL`   | `http://localhost:9003`  | Cross-link target for "Open in Admin" |
| `VITE_TEST_USERNAME`    | `admin`                  | Test login username (E2E / dev) |
| `VITE_TEST_PASSWORD`    | `admin123`               | Test login password (E2E / dev) |

## Test-profile Overrides (`.env.test.example`)

`scripts/dev/test.sh` reads the private root `.env` for DB/Redis credentials and
**forces** the following values for the test run, isolating the test database
from dev data:

```
SPRING_PROFILES_ACTIVE=test
DB_NAME=ulticode_test
REDIS_DB=15
JWT_SECRET=test-only-jwt-signing-key-minimum-32-characters-long
JWT_COOKIE_SECURE=false
EMAIL_ENABLED=false
MEILISEARCH_ENABLED=false
SANDBOX_ENABLED=false
SPRINGDOC_ENABLED=false
```

## Secret Generation (`scripts/dev/init-env.sh`)

The init script writes the following with cryptographically random values when
creating a fresh `.env`:

- `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `MYSQL_ROOT_PASSWORD` — 24-char alphanumeric
- `REDIS_PASSWORD` — 24-char alphanumeric
- `JWT_SECRET` — base64 of 32 random bytes (≥ 32 chars after encoding)
- `NACOS_PASSWORD` — 16-char alphanumeric
- `NACOS_AUTH_TOKEN` — 32-byte base64 (used as server identity token)
- `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE` — random strings

Re-run the script any time you want a fresh local credential set (it skips vars
you have explicitly set).
