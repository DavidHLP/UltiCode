# UltiCode development guide

This is the repository-wide source of truth for AI coding agents. A nested `AGENTS.md` adds rules for its subtree and must not repeat this file. `CLAUDE.md` is only a compatibility entry.

## Project and boundaries

UltiCode is an online-judge platform with these main surfaces:

| Path | Responsibility |
| --- | --- |
| `services/auth/` | Auth owner service: credentials, OAuth, sessions, JWT, RBAC |
| `services/admin/` | Admin owner service: governance, audit, settings, monitoring, backup |
| `services/app/` | App owner service: OJ and general user business (parent of `app-web/` boot shell + `modules/` private domains) |
| `services/` | Java 17 / Spring Boot 3.2.5 Maven parent/reactor; `platform/` (common, web-security), `api/` (Dubbo contracts), and three owner services |
| `apps/console/` | Vue 3 user application |
| `apps/management/` | Vue 3 administrator application |
| `packages/` | Focused frontend packages shared by both applications |
| `init-db/migrations/` | Canonical Flyway migrations |
| `docker/` | Runtime infrastructure and judge sandbox |
| `scripts/dev/` | Supported local startup, migration, and verification entry points |
| `wiki/` | Architecture and domain reference; implementation and configuration remain authoritative |

Read the nearest guide before editing `services/`, `apps/console/`, `apps/management/`, or `packages/`.

- Preserve the backend flow `controller -> service -> mapper -> entity` and existing domain-module boundaries. Do not introduce a parallel architecture for a local change.
- Shared frontend code should own one coherent seam. If stable behavior is duplicated across both apps, extract or extend a focused package under `packages/`; do not force app-specific behavior into a shared abstraction.
- Keep request/response contracts aligned across backend, shared types, and both frontends. Preserve the existing `Result` envelope and established field-name mappings.

## Working rules

- Inspect the implementation, configuration, tests, and local guide before changing behavior. Treat code and executable configuration as authoritative when documentation disagrees.
- Keep changes scoped. Preserve unrelated work in a dirty worktree and do not rewrite generated or historical files without a task-specific reason.
- Validate inputs at system boundaries, use typed DTOs and parameterized database access, and follow existing error-handling patterns.
- Add or update tests for changed behavior and important failure paths. Security-sensitive rendering and URL handling require malicious-input regressions.
- Only `packages/theme` may write the `data-theme` attribute; `useThemeForceUpdate` is test-only.
- Use existing project skills when the task matches them, especially database migration, API-contract, cross-stack DTO, operations, and security workflows.
- Before review or completion, inspect the diff for correctness, security, concurrency/resource handling, error paths, compatibility, performance, coverage, unrelated changes, and documentation drift. Use the available `code-review` skill for a formal review.

## Security invariants

- Never commit, print, or hardcode credentials. Runtime secrets belong in `.env`, CI secrets, or the deployment secret store; JWT secrets must be at least 32 characters.
- Access and refresh tokens remain in HttpOnly cookies. Refresh tokens use the database-backed hash-only issue/rotate/revoke flow; never store plaintext refresh tokens or accept an access token as a refresh credential.
- OAuth state remains bound to an HttpOnly cookie and is consumed atomically from Redis.
- WebSocket authentication accepts only the `access_token` cookie, never query, URL, or client-controlled STOMP tokens.
- `/admin/**` and privileged methods require `ADMIN` or `SUPER_ADMIN`. Audit identity comes from the authenticated principal, not request data.
- Markdown and KaTeX HTML must pass through `packages/markdown-utils`; do not bypass DOMPurify or send unsanitized output to `v-html`.
- Base and production Compose configurations must not publish MySQL, Redis, Nacos, or backend ports. Development exposure belongs only in `docker-compose.dev.yml` and must bind to loopback. Keep Nacos authentication enabled and its default account disabled.
- Do not add usable default users or passwords to migrations. Initial administrator provisioning remains opt-in.

## Database changes

- `init-db/migrations/` is the only migration source. Use `V{timestamp}__Description.sql`.
- Never edit an applied migration; add a later, backward-compatible migration.
- Do not bypass `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql` or reintroduce usable seed credentials.
- Use the `ulticode-db-migration` skill when available.

## Verification

Run checks proportional to the changed surface. Prefer the supported wrapper for broad verification:

```bash
./scripts/dev/test.sh quick
./scripts/dev/test.sh full
./scripts/dev/test.sh integration
```

Targeted checks:

```bash
# services/ Maven reactor; run from repository root
(cd services && ./mvnw compile -B)
(cd services && ./mvnw test -B)
(cd services && ./mvnw -Dtest='*IT' test -B)   # Surefire excludes *IT by default
(cd services && ./mvnw verify -B)              # includes JaCoCo report and thresholds

# apps/console/
(cd apps/console && pnpm lint)
(cd apps/console && pnpm type-check)
(cd apps/console && pnpm test)
(cd apps/console && pnpm build)

# apps/management/
(cd apps/management && pnpm lint)
(cd apps/management && pnpm type-check)
(cd apps/management && pnpm test)
(cd apps/management && pnpm build)

# apps/management/ when translations change
(cd apps/management && pnpm validate:i18n-keys)

# a changed shared package, when the scripts exist in its package.json
pnpm --dir packages/<package> type-check
pnpm --dir packages/<package> test
```

For Compose or migration changes, also validate both configurations and whitespace:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml config >/dev/null
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml config >/dev/null
git diff --check
```

Do not use `/actuator/health` as a readiness check; Actuator is not exposed. Use the existing public API, frontend roots, PM2 state, and container health checks.

## Git and external actions

- Review `git diff` and `git diff --check` before completion. Use conventional commit subjects: `<type>: <description>`.
- Do not discard user changes or use destructive Git commands unless explicitly requested.
- Get explicit approval before pushing, merging, publishing, changing third-party resources, rotating remote credentials, or rewriting history.

## Documentation

- Keep repository-wide agent rules only in this file. Nested guides contain only durable, subtree-specific constraints; `CLAUDE.md` remains a short pointer.
- Do not record volatile counts, file lengths, temporary review findings, planned architecture, or facts directly inferable from package/build configuration.
- Update documentation in the same change when behavior, commands, paths, contracts, or architecture boundaries change. After changing wiki content, run `scripts/dev/wiki-manifest.sh`.

## Completion criteria

A task is complete when the requested behavior is implemented, relevant tests and static checks pass (or failures are reported with evidence), the diff contains no unintended changes, security and compatibility constraints are preserved, and affected documentation is current.
