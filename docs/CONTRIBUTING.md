# Contributing to UltiCode

<!-- Generated: 2026-06-19 | Source: package.json (console/management/shared/auth-core), scripts/dev/, .claude/rules/, AGENTS.md, CLAUDE.md -->

Welcome to UltiCode — an online judge platform. This guide walks you through
the dev loop, code style, and review process. For project-specific rules and
"gotchas", also read `AGENTS.md` and `CLAUDE.md` in the repo root.

## 1. Prerequisites

- **Java 17** (managed via vfox — see `.vfox.toml`)
- **Node.js** `^20.19.0 || >=22.12.0` (Vite 8 / pnpm 10 baseline)
- **pnpm 10** — install via `npm i -g pnpm`
- **Docker + Docker Compose v2** — for MySQL 9.1 / Redis 7 / Nacos 2.3.2
- **PM2** — `npm i -g pm2` (manages 4 long-lived + 1 one-shot dev process)
- **Arthas 4.2.2** — auto-downloaded to `tools/arthas-boot.jar` on first use
  (gitignored; `pm2 start ulticode-arthas` will fetch it)

## 2. First-Time Setup

```bash
git clone <repo>
cd UltiCode
./scripts/dev/init-env.sh   # generates .env with random infra creds
./scripts/dev/up.sh         # docker compose up + Flyway migrate + pm2 start
```

`up.sh` is idempotent. Use `--skip-install` when dependencies are already in
place. End state:

| Service     | URL                                |
| ----------- | ---------------------------------- |
| Backend     | http://localhost:9001              |
| Console     | http://localhost:9002              |
| Management  | http://localhost:9003              |
| Nacos       | http://localhost:28848/nacos       |
| MySQL       | 127.0.0.1:23306 (user: `ulticode`) |
| Redis       | 127.0.0.1:26379                    |

Dev login (after first boot): `admin` / `admin123` (seeded by dev profile).

> **Spring Boot does NOT expose `/actuator/health` in this project.** Don't use
> it for readiness — verify with PM2 status, container health, and known public
> API endpoints instead.

## 3. Repository Layout

```
backend-spring/   Spring Boot 3.2.5 (Java 17) → port 9001
console/          Vue 3 user-facing app → port 9002
management/       Vue 3 admin dashboard → port 9003
shared/           auth-core / theme / design-system / badge-config / sandbox-types
init-db/          Flyway migrations (canonical schema source)
docker/           init scripts + sandbox harness source
docs/             CODEMAPS, ENV, RUNBOOK, adr/
scripts/          dev / test / security / arthas wrappers
```

**Module boundary** (backend): every business module lives under
`backend-spring/src/main/java/com/ulticode/modules/<name>/` with the
`controller → service → mapper(MyBatis-Plus) → entity` layering. Don't
introduce a parallel architecture for a narrow change.

## 4. Per-Package Setup

Use **each package's own lockfile** — never run `pnpm install` from the repo
root as a substitute for installing in the package directory.

```bash
cd console && pnpm install
cd management && pnpm install
cd shared/auth-core && pnpm install
cd backend-spring && ./mvnw -B dependency:go-offline
```

## 5. Available Scripts

<!-- AUTO-GENERATED:START scripts-table -->
### Console (`console/`)

| Command                            | Description |
| ---------------------------------- | ----------- |
| `pnpm dev`                         | lint → type-check → format → test → Vite dev |
| `pnpm build`                       | `run-p type-check "build-only {@}"` (parallel) |
| `pnpm build-only`                  | `vite build` only (skip type-check; only after a fresh `pnpm type-check`) |
| `pnpm type-check`                  | `vue-tsc --build` |
| `pnpm lint`                        | `eslint . --fix --cache` |
| `pnpm format`                      | `prettier --write src/` |
| `pnpm test`                        | `vitest --run --passWithNoTests` (excludes `auth-core` and `shared/theme`) |
| `pnpm test:watch`                  | Vitest watch mode |
| `pnpm test:coverage`               | Vitest with V8 coverage |
| `pnpm validate:mocks`              | Validate mock-data files |
| `pnpm validate:mocks:verbose`      | Validate mock-data with verbose output |
| `pnpm validate:mocks:strict`       | Validate mock-data strictly (fails on warnings) |
| `pnpm verify:theme-sync`           | Verify shared theme tokens in sync across FE |
| `pnpm verify:typography-tokens`    | Verify typography tokens migrated |

### Management (`management/`)

| Command                            | Description |
| ---------------------------------- | ----------- |
| `pnpm dev`                         | lint → type-check → format → test → Vite dev |
| `pnpm build`                       | `run-p type-check "build-only {@}"` (parallel) |
| `pnpm build-only`                  | `vite build` only |
| `pnpm type-check`                  | `vue-tsc --build` |
| `pnpm lint`                        | `eslint . --fix --cache` |
| `pnpm format`                      | `prettier --write src/` |
| `pnpm test`                        | `vitest --run --passWithNoTests` (excludes `shared/theme`) |
| `pnpm test:watch`                  | Vitest watch mode (passWithNoTests) |
| `pnpm test:coverage`               | Vitest with V8 coverage |
| `pnpm validate:i18n-keys`          | Validate i18n key parity (camelCase + snake_case for `table.columnNames`) |
| `pnpm check:i18n`                  | `ts-node` i18n key reference check |
| `pnpm verify:theme-sync`           | Verify shared theme tokens in sync across FE |
| `pnpm verify:typography-tokens`    | Verify typography tokens migrated |

### Shared auth-core (`shared/auth-core/`)

| Command        | Description |
| -------------- | ----------- |
| `pnpm test`    | `vitest --run` |
| `pnpm test:watch` | Vitest watch |
| `pnpm type-check` | `tsc --noEmit` |

### Backend (`backend-spring/`)

| Command                            | Description |
| ---------------------------------- | ----------- |
| `./mvnw spring-boot:run -Dmaven.test.skip=true` | Run dev server (or via PM2 `ulticode-9001`) |
| `./mvnw package -DskipTests`       | Build fat jar |
| `./mvnw compile -B`                | Compile only |
| `./mvnw test -B`                   | Unit tests (Surefire — excludes `*IT.java`) |
| `./mvnw -Dtest='*IT' test -B`      | Integration tests (Testcontainers MySQL 9.1 + Redis 7) |
| `./mvnw verify -B`                 | Full verify incl. JaCoCo coverage |

### Repo-root helpers

| Command                                 | Description |
| --------------------------------------- | ----------- |
| `./scripts/dev/init-env.sh`             | Generate `.env` with random infra creds |
| `./scripts/dev/up.sh [--skip-install]`  | docker compose up + Flyway + PM2 start (in order) |
| `./scripts/dev/up.sh --skip-install`    | Reuse existing deps; just re-orchestrate |
| `./scripts/dev/migrate.sh {migrate\|validate\|info\|repair}` | Flyway wrapper (CLI not on PATH) |
| `./scripts/dev/test.sh {quick\|full\|integration}` | Backend test runner (`integration` enables `*IT.java`) |
| `./scripts/dev/typography-guard.sh`     | Guard against typography-token regressions |
| `pm2 start ecosystem.config.cjs`        | First-time PM2 start (5 apps) |
| `pm2 start all`                         | Subsequent start |
| `pm2 status / logs / monit`             | Process state / log tail / live monitor |
| `pm2 restart ulticode-9001 --update-env` | Restart backend (NOTE: `--update-env` does NOT re-read `envFromFile`; use `pm2 delete && pm2 start ... --only` for `.env` changes) |
| `pm2 save`                              | Persist process list |
| `pm2 resurrect`                         | Restore on next boot |
| `scripts/arthas-cli.sh {start\|stop\|status\|logs\|restart}` | Arthas MCP wrapper (CLI fallback) |
<!-- AUTO-GENERATED:END scripts-table -->

## 6. Code Style

### Frontend
- Vue 3 Composition API + `<script setup lang="ts">` (no Options API, no `defineComponent`)
- TypeScript everywhere; `defineProps<{ … }>()` and `defineEmits<{ … }>()` with generics
- Pinia setup-store style (`useXxxStore`); state = `ref`, getters = `computed`, actions = plain functions
- Tailwind v4 (no scoped CSS unless justified); shadcn-vue / reka-ui + Lucide icons
- `t('module.feature.text')` for all user-visible strings; column-id `table.columnNames.{id}` (camelCase) — `validate:i18n-keys` enforces both forms
- HTTP: only `request.ts` (auto CSRF, 401 → login redirect) — no raw axios
- **`theme-bootstrap.js` is the only place that applies theme to DOM** (no duplicate in `main.ts` or `onMounted`)
- **Prettier**: no semicolons, single quotes, 100-char print width
- **ESLint 10/flat config** — `eslint . --fix --cache` is the source of truth

### Backend
- One `controller → service → mapper → entity` chain per module; DTOs via MapStruct
- All API responses wrap in `Result<T>` (`{ code, message, data, traceId }`)
- Business errors throw `BusinessException(ErrorCode.XXX)` — do NOT try-catch in controller
- `Map.of(...)` is **disallowed** when any value may be null (`java-map-of-null-safety`)
- All services constructor-inject; if you add a field, update every Mockito `@InjectMocks` test
- DTO enum fields are still raw `String` (TS enum mismatch known; prefer backend enum migration when touching DTOs)
- Privileged methods **always** carry `@PreAuthorize` even if a global rule exists
- Single/race-sensitive state updates **must** be transactional conditional updates

### Shared (`shared/`)
- Any change to `shared/auth-core`, `shared/theme`, `shared/design-system`,
  `shared/badge-config`, or `shared/sandbox-types` **must** be verified in BOTH
  frontends: run `pnpm test` + `pnpm type-check` in the package, then both
  `console` and `management`.

## 7. Database & Migrations

- `init-db/migrations/` is the **single source of truth**. `init-db/flyway.conf` + `./scripts/dev/migrate.sh` apply it.
- Migration naming: `V{timestamp}__Description.sql` (e.g. `V20260617140000__Contest_Real_Unique_And_Session_Length.sql`)
- **Never edit an applied migration** — add a new one with a later timestamp
- Migrations **must not** contain default users or public passwords
- Initial admin: only via opt-in `AdminBootstrapRunner` (`APP_BOOTSTRAP_ADMIN_ENABLED=true` once)
- **MySQL 容器化字符集陷阱** — `docker exec mysql` defaults to `latin1`; always pass
  `--default-character-set=utf8mb4` (see CLAUDE.md)

## 8. Testing

### Frontend
```bash
cd console && pnpm test           # vitest --run
cd management && pnpm test
cd shared/auth-core && pnpm test
cd management && pnpm exec playwright test   # E2E
```

### Backend
```bash
cd backend-spring
./mvnw test                       # unit tests only
./scripts/dev/test.sh quick       # same as `mvnw test`
./scripts/dev/test.sh full        # unit + verify (JaCoCo)
./scripts/dev/test.sh integration # adds `*IT.java` (Testcontainers)
```

New tests: unit + integration. **No PR should reduce coverage.** Aim for 80%+
on touched packages.

## 9. CI

GitHub Actions path-based detection (`.github/workflows/ci.yml`):

| Changed paths                          | Jobs run |
| -------------------------------------- | -------- |
| `backend-spring/**`, `init-db/**`      | Maven ci + Flyway + Gitleaks |
| `console/**`, `shared/**`              | Console lint + type-check + test + prod-dep audit |
| `management/**`, `shared/**`           | Management lint + type-check + test + i18n + prod-dep audit |
| `docker/**`, `Dockerfile*`             | Docker build verification |
| Migration under `init-db/migrations/`  | Fresh MySQL 9.1 runs `mvn flyway:migrate` |

Other workflows: `cd-deploy.yml`, `cd-rollback.yml`, `docker-publish.yml`.

## 10. PR Submission Checklist

- [ ] `./mvnw compile -B` clean (backend)
- [ ] `./mvnw test -B` green (or `./scripts/dev/test.sh quick`)
- [ ] `cd console && pnpm lint && pnpm type-check && pnpm test` all green
- [ ] `cd management && pnpm lint && pnpm type-check && pnpm test && pnpm validate:i18n-keys` all green
- [ ] `cd shared/auth-core && pnpm test && pnpm type-check` green
- [ ] If you touched `shared/*`, smoke-tested in **both** frontends
- [ ] If you added a new env var, added it to `.env.example` and `docs/ENV.md`
- [ ] If you added a new API endpoint, updated `docs/CODEMAPS/backend.md`
- [ ] If you added a new shared package or dependency, updated `docs/CODEMAPS/dependencies.md`
- [ ] If you added/changed a DTO field, ran `cross-stack-dto-granularity-alignment` skill audit
- [ ] If you added a new module, created `controller/service/mapper/entity/dto` per layer rules
- [ ] If you added a new migration, verified on a fresh MySQL 9.1 container
- [ ] No secrets in code, configs, or commit messages (`gitleaks` enforces)
- [ ] Commit format: `<type>: <description>` (feat / fix / refactor / docs / test / chore / perf / ci)
- [ ] `git diff --check` clean

## 11. Code Review Etiquette

- Tag the relevant domain reviewer (backend: `java-reviewer`; security-sensitive:
  `security-reviewer`; refactor: `code-reviewer`; large plan: `planner` or `architect`)
- Address every review comment — mark "won't fix" with a one-line rationale
- Force-push only on your own feature branch (never on `main` or shared branches)
- After approval, **squash-merge** with the original PR title

## 12. Where to Get Help

- `AGENTS.md` — repo-level authoritative guide (project map, toolchain, startup)
- `CLAUDE.md` — project-specific traps (Arthas, MySQL charset, PM2 quirks)
- `.claude/rules/` — path-scoped rules (load on demand)
- `docs/CODEMAPS/` — architecture, backend, frontend, data, dependencies, sandbox
- `docs/adr/` — Architecture Decision Records (rationale, not just "what")
- `.agents/skills/` — operational skills (`ulticode-dev-ops`, `arthas-cpu-high`, …)
