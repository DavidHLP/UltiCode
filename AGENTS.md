# UltiCode development guide

This is the repository-wide source of truth for AI coding agents. A nested `AGENTS.md` adds rules for its subtree and must not repeat this file. `CLAUDE.md` is only a compatibility entry.

## Task scope and evidence

- Apply only the sections relevant to the task. A documentation edit does not require backend builds, deployment checks, or architecture exploration.
- Start with the requested outcome and the affected files. Expand discovery when a dependency, caller, or concrete uncertainty requires it; do not audit the whole repository by default.
- Use current source, executable configuration, and observed results to establish facts. Memory, documentation, and graph results are navigation aids; check their project and freshness before relying on them. Do not invent missing APIs, commands, files, or test results.
- Choose the simplest change that satisfies the request and preserves existing contracts. Use a plan, skill, or delegated review when it adds value to the task, rather than as a mandatory ceremony for every edit.
- Resolve routine, reversible choices using existing patterns. Ask when missing information materially affects correctness, scope, authorization, or an irreversible action; do not ask again for authorization already given for that action.
- Stop exploring when the affected behavior is understood and the relevant checks answer the remaining risks. Revisit only when new evidence, failures, or changes justify it. If blocked, report the specific limit and continue independent work; do not repeat an unchanged failing approach.
- Report what changed, what was actually verified, and any remaining limitations. Distinguish inference, static inspection, runtime verification, and remote delivery; a failed or skipped check is not a pass.

## Project and boundaries

UltiCode is an online-judge platform with these main surfaces:

| Path | Responsibility |
| --- | --- |
|`services/auth/`|Auth owner service: credentials, OAuth, sessions, JWT, RBAC|
|`services/admin/`|Admin owner service: governance, audit, settings, monitoring, backup|
|`services/app/`|App owner service: OJ and general user business (parent of `app-web/` boot shell + `modules/` private domains)|
|`services/judge/`|Independent Judge worker service: separate from `app-web`, reuses the storage-free `backend-judge-runtime` plus owner APIs; Redis Streams consumer + Docker sandbox, Dubbo remote adapters, no HTTP and no business tables|
|`services/`|Java / Spring Boot Maven parent/reactor; `platform/` (common, web-security), `api/` (Dubbo contracts), five owner services, two independent workers, and the shared judge runtime|
| `apps/console/` | Vue 3 user application |
| `apps/management/` | Vue 3 administrator application |
| `packages/` | Focused frontend packages shared by both applications |
| `init-db/migrations/` | Canonical Flyway migrations |
| `docker/` | Runtime infrastructure and judge sandbox |
| `scripts/dev/` | Supported local startup, migration, and verification entry points |
| `docs/` | 长期主题文档、架构地图、操作入口、项目状态与 ADR；从 [`docs/index.md`](docs/index.md) 开始 |

Read the nearest guide before editing `services/`, `apps/console/`, `apps/management/`, or `packages/`.

- Preserve the backend flow `controller -> service -> mapper -> entity` and existing domain-module boundaries. Do not introduce a parallel architecture for a local change.
- Reuse focused packages under `packages/` for shared frontend behavior. Extract duplicated behavior when needed by the task; do not force app-specific behavior into a shared abstraction.
- Keep request/response contracts aligned across backend, shared types, and both frontends. Preserve the existing `Result` envelope and established field-name mappings.

## Working rules

- Inspect the implementation, configuration, tests, and local guide before changing behavior. Treat code and executable configuration as authoritative when documentation disagrees.
- Keep changes scoped. Preserve unrelated work in a dirty worktree and do not rewrite generated or historical files without a task-specific reason.
- Formatting follows the affected module's formatter/linter configuration; where none exists, match nearby code. Do not impose extra line-length, naming, comment, import-order, or syntax preferences through agent rules.
- Format changed files or ranges only. Do not run whole-tree auto-fixes or add formatting tools unless the task calls for them; inspect any tool-generated diff.
- Validate inputs at system boundaries, use typed DTOs and parameterized database access, and follow existing error-handling patterns.
- Add or update tests for changed behavior and important failure paths. Security-sensitive rendering and URL handling require malicious-input regressions.
- Only `packages/theme` may write the `data-theme` attribute; `useThemeForceUpdate` is test-only.
- Use relevant project skills when explicitly requested or when their workflow helps the task. Missing optional tools or skills are not blockers if direct inspection and supported checks provide the needed evidence.
- Before completion, review the diff against the request and applicable security, compatibility, and failure-path concerns. Use a formal review workflow for substantial or high-risk changes, or when requested; a focused self-review is sufficient for small, low-risk edits.

## Security invariants

- Never commit, print, or hardcode credentials. Runtime secrets belong in `.env`, CI secrets, or the deployment secret store; JWT secrets must be at least 32 characters.
- Access and refresh tokens remain in HttpOnly cookies. Refresh tokens use the database-backed hash-only issue/rotate/revoke flow; never store plaintext refresh tokens or accept an access token as a refresh credential.
- OAuth state remains bound to an HttpOnly cookie and is consumed atomically from Redis.
- WebSocket authentication accepts only the `access_token` cookie, never query, URL, or client-controlled STOMP tokens.
- `/admin/**` and privileged methods require `ADMIN` or `SUPER_ADMIN`. Audit identity comes from the authenticated principal, not request data.
- Markdown and KaTeX HTML must pass through `packages/markdown-utils`; do not bypass DOMPurify or send unsanitized output to `v-html`.
- Base and production Compose configurations must not publish MySQL, Redis, Nacos, or backend ports. Development exposure belongs only in `docker/docker-compose.dev.yml` and must bind to loopback. Keep Nacos authentication enabled and its default account disabled.
- Do not add usable default users or passwords to migrations. Initial administrator provisioning remains opt-in.

## Database changes

- `init-db/migrations/` is the only migration source. Use `V{timestamp}__Description.sql`.
- Never edit an applied migration; add a later, backward-compatible migration.
- Do not bypass `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql` or reintroduce usable seed credentials.
- Use the `ulticode-db-migration` skill when available.

## Verification

Select checks proportional to the changed surface and risk; the commands below are alternatives, not a mandatory sequence. Documentation-only edits normally need a diff, link/path checks where relevant, and whitespace validation. For behavior changes, run focused regression checks first; broaden for cross-module effects or unresolved risks. Do not repeat equivalent successful checks without a reason.

Prefer the supported wrapper when broad verification is needed:

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

For Compose changes, validate affected development and production combinations. For migrations, run the applicable migration checks; Compose validation is additionally needed when deployment configuration changes. Commands for both Compose combinations:

```bash
docker compose --project-directory . --env-file .env -f docker/docker-compose.yml -f docker/docker-compose.dev.yml config >/dev/null
docker compose --project-directory . --env-file .env -f docker/docker-compose.yml -f docker/docker-compose.prod.yml config >/dev/null
git diff --check
```

Do not use `/actuator/health` as a readiness check; Actuator is not exposed. Use the existing public API, frontend roots, PM2 state, and container health checks.

## Test deployment and remote access

- Apply this section only when the task explicitly requests remote testing, deployment, or tunnel access. Resolve the remote host, checkout, branch, and ports from the current environment; otherwise follow the repository's normal local entry points.
- Before remote execution, read the relevant sections of [`docs/development/local-setup.md`](docs/development/local-setup.md), [`docs/development/testing.md`](docs/development/testing.md), and [`docs/operations/deployment.md`](docs/operations/deployment.md); use their supported `scripts/dev/*` and manifest entry points.
- For data backfill or cutover runbooks, perform the source/target, checksum, outbox, and writer checks required by that runbook; only for Submission cutover update its marker after verification passes. Pure schema migrations follow their migration gate and do not require a cutover marker.
- For personal local access to a remote test stack, prefer SSH local port forwarding. Use a Cloudflare Quick Tunnel only when public or cross-device access is explicitly required; scope it to frontend entries, protect administrative surfaces, and remove it after testing.
- Treat explicit exit codes, readiness responses, parsed PM2 state, and Compose health as separate evidence. Process `online` or container `healthy` alone is not a complete deployment proof.
- Keep dynamic URLs, container names, test counts, commit IDs, temporary failures, and runtime logs out of this file; record current evidence only in the task report or the canonical document that owns it.

## Git and external actions

- Review `git diff` and `git diff --check` before completion. Use conventional commit subjects: `<type>: <description>`.
- Do not discard user changes or use destructive Git commands unless explicitly requested.
- Pushing, merging, publishing, changing third-party resources, rotating remote credentials, and rewriting history require explicit user authorization for the action. An explicit request to perform that action is authorization within its stated scope; do not require a second confirmation unless the scope or risk changes.

## Documentation

- Keep repository-wide agent rules only in this file. Nested guides contain only durable, subtree-specific constraints; `CLAUDE.md` remains a short pointer.
- Do not record volatile counts, file lengths, temporary review findings, planned architecture, or facts directly inferable from package/build configuration.
- Update the affected canonical document under `docs/` in the same change when behavior, commands, paths, contracts, or architecture boundaries change. Keep implementation and executable configuration authoritative.
- Agent runtime state is not project documentation: handoff, resume, worklog, task ledgers, and raw verification evidence stay in ignored local directories and are not committed. Architecture decisions go to `docs/architecture/decisions/`, current state to `docs/project/current-status.md`, open issues to `services/docs/SERVICES_ISSUES.md`; each fact has one authoritative location.

## Completion criteria

A task is complete when the requested behavior is implemented, relevant tests and static checks pass (or failures are reported with evidence), the diff contains no unintended changes, security and compatibility constraints are preserved, and affected documentation is current.

## Code discovery tools

- Use `docs/index.md` for broad documentation navigation. For code discovery, use an available, relevant graph to narrow the search, then inspect the source needed for the claim or edit. Avoid duplicating the same lookup across graph tools.
- When graphify is useful and `graphify-out/graph.json` exists, use `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"`. The full report is for broad architecture questions, not routine edits.
- If a tool is unavailable or its index is stale, incomplete, or for another project, use targeted source reads and searches. An empty graph result does not prove absence; scope negative claims to what was checked.
- Refresh a graph when the task needs updated relationships or the user requests it. Documentation-only edits do not require graph updates; preserve unrelated generated changes.
- When the user explicitly invokes `/graphify`, follow the installed graphify skill.
