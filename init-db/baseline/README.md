# Baseline — Fresh-install Tooling (Immutable History Preserved)

`init-db/migrations/` remains the **sole Flyway source of truth**. Historical
applied migrations are **immutable** per `AGENTS.md §Database changes` — they
are never moved or squashed; later corrections use forward migrations.

This directory provides **fresh-install tooling** with **preserved immutable history**:

- `baseline.sql` — canonical schema dump generated from a fully-migrated disposable MySQL. Used only for new environments that want a single-step fresh-install baseline.
- `generate-baseline.sh` — regenerates `baseline.sql` from current migration history.
- Existing databases continue to use the incremental Flyway history (`flyway_schema_history`). Baseline is **not** a replacement for the migration chain; it is an optimization for fresh installs and for AI navigation (one file to read the final shape).

## When to Regenerate

After any schema migration is added to `migrations/`:

```bash
./init-db/scripts/generate-baseline.sh
./init-db/scripts/validate-baseline.sh
```

The generator starts a disposable MySQL, runs the full Flyway chain, dumps `--no-data` schema, and writes `baseline.sql`. No applied migration file is touched.

## Fresh-install Usage

**Standard (always supported):**
```bash
./scripts/dev/migrate.sh migrate
# plus owner migrations via ./init-db/scripts/owner-migrate.sh migrate all
# or via ./scripts/dev/up.sh (which now delegates through owner-migrate.sh)
```
This runs the full incremental migration set (`baselineOnMigrate=false`).

**Baseline-optimized (validated per-schema adoption):**
`baseline.sql` is a `--no-data` dump with `CREATE DATABASE`/`USE` for 6 schemas
(shared `ulticode` + 5 owners). Parity is validated per-schema
(`validate-baseline.sh` and `baseline-adopt.sh` both perform per-schema parity checks).

Adoption requires per-schema `flyway baseline` at the auto-detected max
versions (shared `20260822120000`, `auth` `20260821100000`,
`admin` `20260822120001`, `app` `20260811180000`,
`notification` `20260815100200`, `submission` `20260817000000`):

```bash
# Disposable validation (no env needed — starts MySQL, loads baseline, baselines 6 schemas, validates parity)
./init-db/scripts/baseline-adopt.sh

# Real DB adoption (requires DB_* + MIGRATION_* + SUBMISSION_MIGRATION_* contracts; loads baseline.sql then baselines)
DB_HOST=... DB_PORT=3306 DB_USER=... DB_PASSWORD=... \
MIGRATION_DB_HOST=... MIGRATION_DB_PORT=... MIGRATION_DB_USER=... MIGRATION_DB_PASSWORD=... \
SUBMISSION_MIGRATION_DB_USER=... SUBMISSION_MIGRATION_DB_PASSWORD=... \
./init-db/scripts/baseline-adopt.sh --real
```
The adopt script auto-detects max `V*` per directory (normalizing
`V20260602_120000` legacy naming), loads `baseline.sql`, then
`flyway baseline -baselineVersion=<max>` per schema (shared via
`flyway.conf`, owners via `flyway-*.conf` + privileged migration accounts).
After adoption, `flyway validate`/`migrate` shows no pending migrations and
future increments apply normally. Direct `scripts/dev/migrate.sh baseline`
without `MIGRATION_SCHEMA` fails (`migrate.sh:390`) and its DEV-LOCAL
bootstrap preflight (`migrate.sh:296-348`) does not cover the full dump —
use `baseline-adopt.sh` instead.

## AI Navigability

Instead of reading every root and owner migration to infer the final schema,
AI/tools can read `baseline.sql` for the converged shape and consult
`migrations/` only for historical intent.
