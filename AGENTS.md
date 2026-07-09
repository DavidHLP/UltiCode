# UltiCode Repository Guide

Last updated: 2026-07-06 (init-deep sweep — root refresh + backend-spring/console/shared AGENTS.md)

This file applies to the entire repository. A nested `AGENTS.md` takes precedence
inside its directory. Read the relevant nested guide before changing that area:

- [`backend-spring/AGENTS.md`](backend-spring/AGENTS.md) — module map, JaCoCo, deep modules
- [`console/AGENTS.md`](console/AGENTS.md) — user frontend structure, API patterns, test layout
- [`management/AGENTS.md`](management/AGENTS.md) — admin frontend, i18n conventions, permission gates
- [`shared/AGENTS.md`](shared/AGENTS.md) — 10 shared packages, deep-module pattern, consumption

## Project Map

| Path | Purpose |
|------|---------|
| `backend-spring/` | Spring Boot 3.2.5 API, Java 17, MyBatis-Plus |
| `console/` | Vue 3 user application on port 9002 |
| `management/` | Vue 3 administrator application on port 9003 |
| `shared/auth-core/` | Shared cookie, CSRF, auth-state, and permission code |
| `shared/auth-ui/` | Shared login / register / password-reset Vue components |
| `shared/badge-config/` | Achievement badge configuration |
| `shared/design-system/` | Design tokens and component primitives |
| `shared/http-client/` | Shared axios factory + dedup/retry/401-handler seam |
| `shared/markdown-utils/` | Shared MarkdownIt + DOMPurify (sanitization baked in) |
| `shared/sandbox-types/` | OJ sandbox contract types (cross-language with `docker/sandbox/`) |
| `shared/sidebar-menu/` | Shared sidebar / navigation components |
| `shared/submission-status/` | Submission verdict ↔ color contract |
| `shared/theme/` | Theme system: state / tokens / primitives / bootstrap |
| `init-db/migrations/` | Canonical Flyway SQL migrations |
| `assets/` | README 截图等二进制资源 |
| `docker/` | Database init SQL, Nacos bootstrap, and D-form judge sandbox |
| `wiki/` | Obsidian vault: domain entities, overviews, daily notes, meta (no concept/ADR layer as of 2026-07-09) |
| `infrastructure/` | Project-level Arthas config (`arthas.properties`) |
| `tools/` | Java agent jars (`arthas-boot.jar`) |
| `scripts/` | Dev ops: `dev/`, `security/`, `statusline/`, `test/`, `adr-005/` |
| `.agents/skills/` | Project-specific operational and coding skills |

The backend follows:

```text
controller -> service -> mapper (MyBatis-Plus) -> entity
```

Prefer the existing module boundary under
`backend-spring/src/main/java/com/ulticode/modules/`. Do not introduce a parallel
architecture for a narrow change.

### Shared-package deep-module pattern

Shared packages under `shared/*` follow a "deep module" rule established by
ADR-0004 / ADR-0005 / ADR-0011: each one owns a single seam (auth state, HTTP
factory, markdown rendering, …) and is consumed via `@ulticode/<name>` or the
`@/shared/<name>/src` path alias. New shared packages are auto-discovered by
`pnpm-workspace.yaml`'s `shared/*` glob — drop the package in, add the glob is
already there. `shared/auth-core` also exposes subpath exports
(`@ulticode/auth-core/src/csrf`, `…/axiosCsrfInterceptor`, `…/refreshCoordinator`)
so sibling shared packages can reach specific internals without re-exporting the
full index.

When the same logic was duplicated between `console/` and `management/` (Cards 1,
2, 3, 4 of the 2026-07-06 architecture review), the chosen fix was always
"extract a shared deep module, leave each app as a thin re-export seam". Prefer
that shape over per-app adapters.

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
`CLAUDE.md` § Sandbox Harness. Symptom of a missing/broken sandbox: every judge call returns a
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

### Other Shared Packages

The same `pnpm test && pnpm type-check` pair applies to every package under
`shared/*` — most recently `shared/markdown-utils` and `shared/http-client`.
Run the corresponding commands whenever you touch one of them. Console and
management both consume these via the `@/shared/<name>/src` path alias declared
in each app's `tsconfig.app.json`; new shared packages must be added to that
`include` array to be type-checked with the app.

### Backend Deep Modules Worth Knowing

- `com.ulticode.common.audit.AuditPolicy` — single source of truth for every
  `@Audited` / `@CheckBan` annotation site across the backend. Backed by
  `AuditPolicyCoverageTest` which scans the classpath and fails CI if the
  catalog drifts from the actual annotations. Update the catalog when adding
  new audited methods.
- `com.ulticode.modules.<x>.projection.<X>Projection` — read-side deep
  modules (mirror of `ModerationProjection` / `AchievementProjection`) that
  own entity→VO shaping + cross-mapper enrichment. ADR-0011 lists which
  admin services are scheduled to land one next.

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
- Keep Markdown/KaTeX output sanitized before `v-html`. Sanitization lives
  in `shared/markdown-utils/`; use `renderMarkdown()` from there rather than
  calling `markdown-it` directly or bypassing DOMPurify.

Read the Security Invariants section of this file before changing
authentication, deployment secrets, seed accounts, or network exposure.
Use the `security-review` skill for security-sensitive
work.

> The `security-review` skill is the authoritative workflow. The wiki
> concept pages are the reference reading; the security review record
> The security review record (`docs/SECURITY_REVIEW_2026-06-06.md` and
> the remediation runbook) is preserved in the git history &mdash; see
> `git log -- docs/` for the original location before the docs-merge.

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
- Vote counts are owned by `com.ulticode.modules.vote.service.VoteService` and
  returned via `VoteResultVO`. Persist denormalized counters on the target entity
  using the values `voteService.vote()` already produced — do not re-query
  `EdgeOperationMapper.countByTargetAndOperation` from a different service.
  Denormalization is the **caller's** responsibility, applied only to entities
  that carry a denormalized counter column (currently `solution.likes` /
  `solution.dislikes`). The two legitimate routes are `VoteController`
  (`/vote` — pure vote-state mutation, no side-effects) and
  `EdgeOperationsController` (`/edge-operations` — vote + analytics + the
  `solution` denormalized counter projection). The asymmetry where only the
  latter projects onto `solution.likes` is deliberate: `VoteService` must not
  depend on `Solution`. A future `VoteResultUpdated` domain-event pattern
  would let `solution` (and any future denormalized target) listen without
  coupling. The dead `ForumVoteService` pass-through was deleted in the
  architecture-review-0012 sweep — do not resurrect it.

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
