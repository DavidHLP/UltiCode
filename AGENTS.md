# UltiCode Repository Guide

Last updated: 2026-07-06

This file applies to the entire repository. A nested `AGENTS.md` takes precedence
inside its directory. In particular, read `management/AGENTS.md` before changing
the management frontend.

## Project Map

| Path | Purpose |
|------|---------|
| `backend-spring/` | Spring Boot 3.2.5 API, Java 17, MyBatis-Plus |
| `console/` | Vue 3 user application on port 9002 |
| `management/` | Vue 3 administrator application on port 9003 |
| `shared/auth-core/` | Shared cookie, CSRF, auth-state, and permission code |
| `init-db/migrations/` | Canonical Flyway SQL migrations |
| `docker/` | Database, Nacos, and judge initialization assets |
| `docs/` | Architecture, operations, and security documentation |
| `.agents/skills/` | Project-specific operational and coding skills |

The backend follows:

```text
controller -> service -> mapper (MyBatis-Plus) -> entity
```

Prefer the existing module boundary under
`backend-spring/src/main/java/com/ulticode/modules/`. Do not introduce a parallel
architecture for a narrow change.

## Toolchain

- Java 17 for the backend.
- Node.js `^20.19.0 || >=22.12.0`.
- pnpm 11 for frontend and shared packages.
- MySQL 9.1, Redis 7, and Nacos 2.3.2 through Docker Compose.
- PM2 manages the three development application processes.

Use each package's own lockfile. Do not run a root-level dependency install as a
substitute for installing in `console/`, `management/`, or `shared/auth-core/`.

## Development Startup

The root `.env` is the local source of truth. Never commit it or print its secret
values. Start infrastructure with the development override so host ports bind only
to loopback:

```bash
./scripts/dev/init-env.sh
./scripts/dev/up.sh
```

Development endpoints:

| Service | Address |
|---------|---------|
| Backend | `http://localhost:9001` |
| Console | `http://localhost:9002` |
| Management | `http://localhost:9003` |
| Nacos | `http://localhost:28848/nacos` |
| MySQL | `127.0.0.1:23306` |
| Redis | `127.0.0.1:26379` |

This project does not currently expose Spring Actuator. Do not use
`/actuator/health` as a development readiness check. Verify a known public API,
the two frontend roots, PM2 status, and container health instead.

Useful operations:

```bash
./scripts/dev/up.sh --skip-install
./scripts/dev/test.sh quick
./scripts/dev/test.sh full
./scripts/dev/test.sh integration
pm2 status
pm2 logs ulticode-9001
pm2 restart ulticode-9001
docker compose --env-file .env \
  -f docker-compose.yml -f docker-compose.dev.yml ps
```

The disposable `dev` database login is `admin` / `admin123`. It is initialized by
the dev-profile-only bootstrap runner and must never be reused in production.

The judge sandbox image (`ulticode-sandbox:latest`) is **not distributed with the
repo** — build it locally before any code-execution feature (problem run/submit).
`./scripts/dev/up.sh` warns at startup if the image is missing. The full build
runbook (base image, alpine/musl quirks, proxy fallbacks) lives in
`wiki/concepts/sandbox-rebuild.md`; the operating contract is in `CLAUDE.md` §
Sandbox Harness. Symptom of a missing/broken sandbox: every judge call returns a
masked `Runtime Error` with `memory=0.0MB`.

## Verification

Run checks proportional to the touched surface. Before completing a cross-stack or
security-sensitive change, run the full relevant matrix.

### Backend

```bash
cd backend-spring
./mvnw compile -B
./mvnw test -B
./mvnw -Dtest='*IT' test -B
./mvnw verify -B
```

Surefire's default patterns do not select `*IT.java`. Run those tests explicitly
when integration behavior is affected. `verify` also generates and checks the
JaCoCo report; there is no Maven `ci` profile.

### Console

```bash
cd console
pnpm lint
pnpm type-check
pnpm test
pnpm build
pnpm audit --prod --audit-level high
```

### Management

```bash
cd management
pnpm lint
pnpm type-check
pnpm test
pnpm validate:i18n-keys
pnpm build
pnpm audit --prod --audit-level high
```

### Shared Auth

```bash
cd shared/auth-core
pnpm test
pnpm type-check
```

Console excludes the symlinked shared auth tests. Run the shared package commands
whenever `shared/auth-core/` changes.

### Migrations and Configuration

```bash
docker compose --env-file .env \
  -f docker-compose.yml -f docker-compose.dev.yml config >/dev/null
docker compose --env-file .env \
  -f docker-compose.yml -f docker-compose.prod.yml config >/dev/null
git diff --check
```

CI also validates migrations against a fresh MySQL database, scans the current tree
with Gitleaks, audits frontend production dependencies, and builds all Docker images.

## Database Rules

- `init-db/migrations/` is the only migration source.
- Use Flyway names in the form `V{timestamp}__Description.sql`.
- Never edit a migration that may already have been applied. Add a later migration.
- The repository contains historical demo seed migrations. The security migration
  `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql` must remain
  after them and must not be bypassed.
- Do not add usable default users or documented passwords to migrations.
- Provision an initial administrator only through the opt-in
  `AdminBootstrapRunner`; it must remain disabled during normal startup.

Use the `ulticode-db-migration` skill for schema work.

## Security Invariants

- Never hardcode or commit credentials. Runtime secrets come from `.env`, CI
  secrets, or the deployment secret store.
- JWT secrets must be at least 32 characters.
- Access and refresh tokens stay in HttpOnly cookies.
- Refresh tokens use the database-backed hash-only issue/rotate/revoke path. Never
  restore plaintext refresh-token storage or accept access tokens at refresh.
- OAuth state must remain bound to an HttpOnly browser cookie and atomically consumed
  from Redis.
- WebSocket authentication accepts the `access_token` cookie only. Do not add query
  token, URL token, or client-controlled STOMP token support.
- `/admin/**` and privileged controller methods require `ADMIN` or `SUPER_ADMIN`.
  Audit identities come from the authenticated principal, never request data.
- Base and production Compose files do not publish MySQL, Redis, Nacos, or backend
  ports. Only `docker-compose.dev.yml` may expose infrastructure, and only on
  `127.0.0.1`.
- Nacos authentication stays enabled and the default `nacos/nacos` account stays
  disabled.
- Keep Markdown/KaTeX output sanitized before `v-html`.

Read `docs/SECURITY_REVIEW_2026-06-06.md` and
`docs/SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md` before changing authentication,
deployment secrets, seed accounts, or network exposure. Use the `security-review`
skill for security-sensitive work.

## Frontend Conventions

- Vue 3 Composition API and TypeScript.
- Prettier uses no semicolons, single quotes, and 100-character lines.
- Reuse existing API wrappers and shared auth APIs.
- Preserve backend `Result` response handling and the repository's
  snake_case/camelCase mapping patterns.
- Add malicious-input regression tests when changing Markdown, HTML sanitization,
  URL handling, or user-generated content rendering.
- `pnpm dev` runs lint, type-check, format, and tests before Vite. For an already
  reviewed tree or PM2 startup, use the existing direct Vite configuration rather
  than triggering unrelated formatting.

Use `ulticode-api-patterns` for API integration and
`cross-stack-dto-granularity-alignment` when changing shared request/response types.

## Backend Conventions

- Validate inputs at controller/system boundaries.
- Prefer typed DTOs, MyBatis parameter binding, and existing mapper/service patterns.
- Add `@PreAuthorize` to privileged operations even when a global route rule exists.
- Use transactional, conditional updates for single-use or race-sensitive state.
- Avoid `Map.of(...)` when any value may be null; use the
  `java-map-of-null-safety` guidance.
- When adding constructor dependencies to Lombok services, update every Mockito
  `@InjectMocks` test with matching mocks.

## Git and External Actions

- The worktree may contain user changes. Do not discard or rewrite unrelated work.
- Review `git diff` and `git diff --check` before proposing a commit.
- Use conventional commits: `<type>: <description>`.
- Network tools are read-only by default.
- Require explicit user approval before pushing, merging, publishing, changing
  third-party resources, rotating remote credentials, or rewriting Git history.

## Skills and Codex

Project skills are auto-discovered from `.agents/skills/`. Use a matching skill when
the task triggers it. Important local skills include:

- `ulticode-dev-ops`
- `ulticode-db-migration`
- `ulticode-api-patterns`
- `cross-stack-dto-granularity-alignment`
- `solarized-terminal-design-style`
- `arthas-cpu-high`
- `arthas-eagleeye-traceid`
- `arthas-springcontext-issues-resolve`

Treat `.codex/config.toml` as the project MCP baseline. Preserve user MCP
configuration, credentials, and custom servers. Multi-agent roles live in
`.codex/agents/`; do not dispatch remote agents or perform external writes without
explicit approval.
