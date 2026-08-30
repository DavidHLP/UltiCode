# scripts/

Operational entry points for UltiCode. One page per directory; the supported
local startup/verification entry points live in `scripts/dev/`.

## scripts/dev/ — local development and verification

| Script | Purpose |
| --- | --- |
| `up.sh` |唯一本地开发启动入口 (`--mode dev-lite/dev-full/legacy-rollback`)；执行顺序与就绪面由 `devstack-manifest.sh` 声明 |
| `stop.sh` | 停止 PM2 管理的本地进程组 |
| `init-env.sh` | 生成开发者 `.env`（无可用默认凭据） |
| `migrate.sh` | Flyway 入口（shared chain + per-owner `MIGRATION_SCHEMA=`） |
| `test.sh` | 支持的验证 wrapper：`quick` / `full` / `integration`（见仓库根 AGENTS.md） |
| `doctor.sh` | 只读的端口/PM2/Docker 健康诊断，附建议命令 |

### Gates and contracts

- `architecture-contract-test.sh` — code-seam contracts (Java ports, compose
  aliases, PM2 topology) plus the DevStack manifest test.
- `docs-contract-test.sh` — documentation-drift guardrails (README /
  PROJECT_DOCUMENTATION / CONTEXT wording). Invoked by the architecture gate.
- `devstack-manifest-test.sh` — locks the manifest data consumed by
  `up.sh`/`stop.sh`.
- `migrate-owner-preflight-test.sh` — fast fake-binary suite for
  `migrate.sh`; runs in every quick/full gate.
- `owner-migration-safety-integration-test.sh` — disposable MySQL/Redis
  permission/grant isolation suite; runs in `test.sh integration`.
- shellcheck runs over `scripts/**/*.sh` when installed.

## scripts/dev/lib/ — shared shell library

`common.sh` is the single external entry point. Internals are split by concern
and frozen `readonly -f` before any `.env` is sourced:

- `env.sh` — `ROOT_DIR`/`ENV_FILE` resolution, `load_env_file`,
  `capture_env_vars`/`apply_env_overrides`
- `validate.sh` — identifier/port/schema validators
- `docker.sh` — container probes and health waits
- `confirm.sh` — write-confirmation predicates (`gate_confirmed`,
  `require_write_confirmation`)
- `sql.sh` — data-verification primitives
  (`table_exists`/`column_signature`/`row_count`/`checksum_table`) and
  `define_mysql_query_adapter`, the single-sourced factory behind every
  runbook's `mysql_query`

Do not add runbook-specific business logic here; keep REVOKE/drain/cutover
semantics in the runbooks.

## scripts/runbooks/ — one-shot data migration runbooks

Independent cutover/backfill/rehearsal procedures with explicit confirmation
tokens. Each keeps its own REVOKE/drain semantics; shared primitives come from
`scripts/dev/lib/common.sh`. The Submission runbook now sequences:
`backfill --dry-run` (default, insert-free plan), `backfill --execute`
(batch checkpoint/resume with insert-only conflict protection), `verify`
(count/checksum/field/writer parity), then `cutover --execute`.
Checkpoint and failure artifacts default under `.local/migration-audit/`; the
execute path requires explicit backfill and all-writers quiesce confirmations.
- `submission-backfill-contract.sh` — executable fake-MySQL rehearsal for dry-run checkpoint resume, failure export, and insert-free behavior.

## scripts/test/ — standalone smoke suites

Gateway baseline, moderation API, admin solutions, Dubbo/Nacos smoke. New
smokes should source `lib/smoke-common.sh` (`smoke_init`, `smoke_load_env`,
`smoke_require_credentials`, `smoke_login`). Credentials converge on
`SMOKE_USERNAME`/`SMOKE_PASSWORD`; legacy names are still accepted.

## Other

- `security/bootstrap-nacos-user.sh` — opt-in Nacos administrator and per-service registry-user provisioning.
- `pitstop-start-backend.ps1` — Windows pitstop adapter delegating to
  `scripts/dev/up.sh --no-frontend` (consumed by `pitstop.yaml`).
- `statusline/` — Claude Code statusline configuration.

Theme guard tooling (`verify-theme-sync.mjs`, `sync-theme-bootstrap.mjs`,
`verify-typography-tokens.mjs`) lives with the module it guards:
`packages/theme/scripts/`.
