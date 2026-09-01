# Development Environment Variables

> 📌 文档总入口：[README.md](./README.md) · 文档规范：[DOCS-SPEC.md](./DOCS-SPEC.md)

UltiCode uses one private root `.env` for Docker Compose, Flyway, PM2, the Spring
backend, and both Vite applications.

Do not commit `.env`. Generate unique infrastructure credentials for each checkout:

```bash
./scripts/dev/init-env.sh
```

The command creates `.env` with mode `0600`, preserves an existing file, and never
prints generated credentials. `.env.example` documents the complete development
shape; `.env.test.example` documents test-only overrides.

## One-Command Workflow

```bash
./scripts/dev/up.sh
```

This command:

1. Generates `.env` when missing.
2. Starts MySQL, Redis, and authenticated Nacos on loopback ports.
3. Provisions the private Nacos administrator and disables the default account.
4. Applies Flyway migrations using the root database variables.
5. Creates or restores the documented local `admin` account.
6. Installs locked frontend/shared dependencies.
7. Starts backend, console, and management through PM2.
8. Verifies the three application endpoints.

Use `./scripts/dev/up.sh --skip-install` after the first successful run.

## Core Variables

| Variable | Generated/default development value | Consumer |
|----------|-------------------------------------|----------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring, PM2 |
| `SERVER_PORT` | `9001` | Backend |
| `CONSOLE_PORT` | `9002` | Documentation/runtime convention |
| `MANAGEMENT_PORT` | `9003` | Documentation/runtime convention |
| `DB_HOST` | `localhost` | Backend, Flyway |
| `DB_PORT` | `23306` | Backend, Flyway, Compose |
| `DB_USER` | `ulticode` | MySQL, Backend, Flyway |
| `DB_PASSWORD` | Random | MySQL, Backend, Flyway |
| `DB_NAME` | `ulticode` | MySQL, Backend, Flyway |
| `MYSQL_ROOT_PASSWORD` | Random | MySQL, Nacos, local provisioning |
| `DB_ROOT_PASSWORD` | Same generated root value | Production Compose compatibility |
| `REDIS_HOST` | `localhost` | Backend |
| `REDIS_PORT` | `26379` | Backend, Compose |
| `REDIS_PASSWORD` | Random | Redis, Backend |
| `REDIS_DB` | `0` | Backend |
| `JWT_SECRET` | Random 96-character hex | Backend |
| `JWT_COOKIE_SECURE` | `false` | Local HTTP cookies |
| `CORS_ALLOWED_ORIGINS` | Local console/management origins | Backend |
| `FRONTEND_URL` | `http://localhost:9002` | Password reset links |

## Frontend Variables (Vite)

Both Vue apps read the same Vite env block at build / dev time.

| Variable | Default | Consumer |
|----------|---------|----------|
| `VITE_API_BASE_URL` | `http://localhost:9001` | Console, Management |
| `VITE_ADMIN_BASE_URL` | `http://localhost:9003` | Console cross-link |
| `VITE_TEST_USERNAME` | `admin` | E2E test login (Playwright) |
| `VITE_TEST_PASSWORD` | Generated locally; not stored in documentation | E2E test login (Playwright) |

## Sandbox & Local Features

| Variable | Default | Consumer |
|----------|---------|----------|
| `SANDBOX_IMAGE` | `ulticode-sandbox:latest` | Submission sandbox |
| `SANDBOX_MEMORY` | `256m` | Submission sandbox |
| `SANDBOX_CPUS` | `1.0` | Submission sandbox |
| `SANDBOX_TIMEOUT` | `10` | Submission sandbox |
| `SANDBOX_PIDS_LIMIT` | `128` | Submission sandbox |
| `SANDBOX_SECCOMP_PROFILE` | `docker/sandbox/seccomp-profile.json` | Submission sandbox |
| `BACKUP_DIR` | `/tmp/ulticode-backups` | DB backup module |
| `AUDIT_EXPORT_LIMIT` | `10000` | Audit log export |
| `SPRINGDOC_ENABLED` | `true` | OpenAPI docs |

## Nacos Variables

| Variable | Development behavior |
|----------|----------------------|
| `NACOS_SERVER_ADDR` | `localhost:28848` (combined host:port for Spring discovery) |
| `NACOS_HOST` / `NACOS_PORT` | `localhost:28848` |
| `NACOS_GRPC_PORT` | `29848` |
| `NACOS_NAMESPACE` | `public` |
| `NACOS_GROUP` | `DEFAULT_GROUP` |
| `NACOS_USERNAME` | `ulticode_dev_admin` |
| `NACOS_PASSWORD` | Random, 16+ characters |
| `NACOS_AUTH_TOKEN` | Random Base64 server token |
| `NACOS_AUTH_IDENTITY_KEY` | Random server identity key |
| `NACOS_AUTH_IDENTITY_VALUE` | Random server identity value |

The Nacos values are local secrets. Do not reuse them in production.

## Local Development Users

The disposable development database has this documented administrator:

```text
username: <generated-local-admin>
password: <generated-local-password>
role: ADMIN
```

`up.sh` invokes a separate `DevUserBootstrapRunner` after migrations. The runner is
both `@Profile("dev")` and explicitly enabled only for that command. It creates the
account when missing and restores its password and active status when a security
migration or local change disabled it. The runner cannot load in production.

| Variable | Default |
|----------|---------|
| `DEV_SEED_USERS_ENABLED` | `true` |
| `DEV_SEED_ADMIN_USERNAME` | `admin` |
| `DEV_SEED_ADMIN_EMAIL` | `admin@localhost.test` |
| `DEV_SEED_ADMIN_PASSWORD` | Generated locally; not stored in documentation |
| `DEV_SEED_ADMIN_ROLE` | `ADMIN` |

Set `DEV_SEED_USERS_ENABLED=false` to preserve all database accounts unchanged.
These credentials are intentionally weak and must never be reused outside a local,
disposable development database.

## Optional Integrations (off by default)

These are disabled by default so a developer needs only Docker, Java, Maven, Node,
pnpm, and PM2:

| Variable | Default |
|----------|---------|
| `EMAIL_ENABLED` | `false` |
| `MEILISEARCH_ENABLED` | `false` |
| `SANDBOX_ENABLED` | `false` |
| `SPRINGDOC_ENABLED` | `true` |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | Empty |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Empty |
| `GITHUB_REDIRECT_URI` | `http://localhost:9001/auth/github/callback` |
| `GOOGLE_REDIRECT_URI` | `http://localhost:9001/auth/google/callback` |
| `SMTP_HOST` / `SMTP_PORT` | `localhost:1025` (MailHog default) |
| `SMTP_USER` / `SMTP_PASSWORD` | Empty |
| `EMAIL_FROM_NAME` | `UltiCode Dev` |

Enable the sandbox after building its image:

```bash
docker build -t ulticode-sandbox:latest docker/sandbox
sed -i 's/^SANDBOX_ENABLED=false/SANDBOX_ENABLED=true/' .env
pm2 restart ulticode-9001 --update-env
```

## Administrator Bootstrap (opt-in)

These variables are **disabled by default** in development. The bootstrap runner
is a one-time seed mechanism for the very first admin account, not a recurring
mechanism. It is intentionally separate from `DevUserBootstrapRunner`.

| Variable | Default |
|----------|---------|
| `APP_BOOTSTRAP_ADMIN_ENABLED` | `false` |
| `APP_BOOTSTRAP_ADMIN_USERNAME` | Empty |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | Empty |
| `APP_BOOTSTRAP_ADMIN_PASSWORD` | Empty |

> In production, manage the initial administrator through your secret manager and
> rotation policy. Do not enable `APP_BOOTSTRAP_ADMIN_ENABLED=true` in any
> environment that has ever run with real user data.

## Test Environment

Use the repository test wrapper instead of pointing tests at the development schema:

```bash
./scripts/dev/test.sh quick
./scripts/dev/test.sh full
./scripts/dev/test.sh integration
```

The wrapper creates and migrates `ulticode_test`, uses Redis database 15, sets a
test-only JWT secret, disables external integrations, and never drops the development
database.

## Regeneration and Cleanup

`init-env.sh` does not overwrite `.env`. Credential regeneration against an existing
MySQL volume would make the stored database users incompatible, so use a disposable
reset only when local data may be deleted:

```bash
pm2 delete ulticode-9001 ulticode-9002 ulticode-9003
docker compose --env-file .env \
  -f docker-compose.yml -f docker-compose.dev.yml down -v
rm .env
./scripts/dev/init-env.sh
./scripts/dev/up.sh
```

Never run `down -v` when local data must be preserved.

## Production Boundary

Development values are not production defaults. Production requires externally
managed secrets, `JWT_COOKIE_SECURE=true`, real TLS origins, no infrastructure host
ports, and the operational steps in [RUNBOOK.md](./RUNBOOK.md) +
`CLAUDE.md` → **Security Invariants** (the standalone remediation runbook was
consolidated into these in commit `9ce22f921`).
