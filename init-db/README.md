# UltiCode Database Migrations

`init-db/migrations/` is the canonical Flyway migration source. Database credentials
are never stored in this directory; local commands load the private root `.env`.

## Local Usage

From the repository root:

```bash
./scripts/dev/init-env.sh
./scripts/dev/migrate.sh info
./scripts/dev/migrate.sh migrate
./scripts/dev/migrate.sh validate
```

`scripts/dev/up.sh` automatically runs `migrate` before starting the applications.

## Configuration

The Maven Flyway plugin reads:

```text
DB_HOST
DB_PORT
DB_USER
DB_PASSWORD
DB_NAME
```

`scripts/dev/migrate.sh` exports these values from the root `.env` and runs Maven
from this directory. CI/CD supplies the same values through its secret environment
or Flyway container arguments.

## Owner Migration Job

Owner-schema migrations must use an explicit privileged migration connection:

```bash
MIGRATION_SCHEMA=auth \
MIGRATION_DB_HOST=... \
MIGRATION_DB_PORT=3306 \
MIGRATION_DB_NAME=auth \
MIGRATION_DB_USER=... \
MIGRATION_DB_PASSWORD=... \
./scripts/dev/migrate.sh validate
```

`MIGRATION_SCHEMA` is limited to `auth`, `admin`, `app`, `notification`, or
`submission`. The script performs a read-only connection/schema/account/privilege
preflight before invoking Flyway, and refuses to use `DB_*` as an owner migration
fallback. The migration account must differ from the runtime Owner account.
The minimum capability policy is `CREATE`, `ALTER`, `SELECT`, and
`GRANT OPTION` on the owner schema; `notification` and `submission` also
require global `CREATE USER`. A global `ALL PRIVILEGES` grant is accepted as
covering these capabilities and must still be reviewed against the external
Role grants are rejected fail-closed; the migration principal must receive
direct grants so the preflight and Flyway use the same effective privileges.
This avoids depending on session default-role activation or `mysql.role_edges`
visibility; role-based migration identities need a separately scoped task.

The current App Owner compatibility repairs also require direct `DROP`,
`CREATE ROUTINE` and `ALTER ROUTINE` on `app.*` for table rebuilds and the
transient guarded DDL helper. The preflight checks these privileges only for
`MIGRATION_SCHEMA=app`; they are not granted to the App runtime account.

When the development host does not have the `mysql` CLI, set
`MIGRATION_MYSQL_CONTAINER` to the explicit local MySQL container name and
optionally `MIGRATION_MYSQL_CONTAINER_PORT` (default `3306`). The read-only
preflight then runs `mysql` inside that container while Flyway still receives
the explicit `MIGRATION_DB_*` connection contract.

The shared migration chain is the explicit schema bootstrap for a fresh local
database. Run it before owner-specific migration; owner Flyway configs set
`flyway.createSchemas=false`, and the owner preflight intentionally fails closed
when the target schema is absent.

CD uses `scripts/runbooks/owner-migration-manifest.sh migrate` on the deployment
host. It validates the fixed `auth → admin → app → notification → submission`
order, owner Flyway config/schema alignment, runtime-account separation, and
manifest checksums; a host `flock` serializes runs. Shared and owner Flyway
failures get one bounded retry without automatic `repair`, and each run writes a
machine JSON report plus a human log. Rollback passes `skip_migrations=true` so
the prior schema remains compatible; production migration is not executed by
repository verification.

`scripts/dev/up.sh` first runs the shared schema bootstrap, then applies the
deterministic Owner manifest (`auth`, `admin`, `app`, `notification`,
`submission`) through the corresponding `flyway-*.conf` files. It keeps the
shared `MIGRATION_DB_*` identity for Auth/Admin/App/Notification and passes
`SUBMISSION_MIGRATION_DB_USER/PASSWORD` to the Submission owner migration.
It then runs `flyway-post-owner.conf` with the shared privileged identity to
remove the historical cross-owner audit grants after both local outboxes exist.
Finally it provisions and probes all five local runtime accounts before PM2
starts. These identities must not be merged or silently defaulted to a runtime
account.

The Auth, Notification and Submission owner chains contain canonical
`FLUSH PRIVILEGES` statements. Their migration principal therefore needs the
direct global `RELOAD` capability (or the explicitly supported literal global
`ALL PRIVILEGES` compatibility superset); Notification and Submission also
need global `GRANT OPTION` because their owner grants include `GRANT USAGE ON
*.*`. Arbitrary non-root global capability lists do not satisfy the preflight.

## Owner backup and restore drill (P2-BACKUP-001)

The external Ops runbook `scripts/runbooks/owner-backup-restore.sh` archives the
shared control schema plus `auth`, `admin`, `app`, `notification`, and
`submission`. It requires a dedicated `BACKUP_DB_*` credential and a
32-byte `BACKUP_ENCRYPTION_KEY`; the key is passed through the OpenSSL
environment, never argv or the generated manifest. Each encrypted archive has
a secret-free manifest, dump SHA-256 list, table row/checksum snapshot, and
Flyway migration metadata. `flock` serializes backup, restore-drill, and prune;
retention deletes only matching `owner-backup-*.json`/`.tar.gz.enc` pairs.

```bash
BACKUP_DB_HOST=... BACKUP_DB_PORT=3306 BACKUP_DB_NAME=ulticode \
BACKUP_DB_USER=... BACKUP_DB_PASSWORD=... BACKUP_ENCRYPTION_KEY=... \
OWNER_BACKUP_DIR=/var/lib/ulticode/backup \
./scripts/runbooks/owner-backup-restore.sh backup
./scripts/runbooks/owner-backup-restore.sh verify
./scripts/runbooks/owner-backup-restore.sh restore-drill
```

`restore-drill` restores only into a disposable MySQL container, validates all
six Flyway histories, reconciles table row/checksum snapshots, runs a schema
count/`SELECT 1` smoke check, and writes measured `rpo_seconds` and
`rto_seconds` under the report directory. It never restores into the live
database; production backup/restore authority and off-host key/retention
storage remain external.

For a pre-created bootstrap-only table with no owner history, the only
supported adoption path is an explicit DEV-LOCAL command with
`DEV_LOCAL_OWNER_BASELINE=true` and
`DEV_LOCAL_OWNER_BASELINE_CONFIRM=I_UNDERSTAND_DEV_LOCAL_OWNER_BASELINE`.
The command checks the expected table set, ordered column signature, absent
history and zero rows before running `baseline`; ordinary `migrate` never
silently baselines an unknown schema.
The supported `scripts/dev/up.sh` path supplies that confirmation only after
the shared chain and manifest identify the canonical bootstrap shape; an
unexpected table set still fails closed.
Credentials belong in the local `.env`/CI secret store, never in this directory
or `.auto-flow`. The shared migration chain without `MIGRATION_SCHEMA` retains
the historical `DB_*` contract.

## Owner schema contraction (P1-DATA-001)

Normal `migrate` never scans `migrations/contraction/`. After App readers are
on Submission-owner facts, run the read-only proof first:

```bash
bash scripts/runbooks/owner-schema-contraction.sh preflight
```

The explicit destructive step requires owner parity/checksum proof, zero App
DML grants on the legacy tables, a verified backup, and both confirmations:

```bash
OWNER_SCHEMA_CONTRACTION_CONFIRM=I_UNDERSTAND_OWNER_SCHEMA_CONTRACTION \
OWNER_SCHEMA_CONTRACTION_BACKUP_CONFIRM=I_HAVE_VERIFIED_OWNER_CONTRACTION_BACKUP \
OWNER_SCHEMA_CONTRACTION_QUIESCE_CONFIRM=I_HAVE_QUIESCED_OWNER_WRITERS \
OWNER_SCHEMA_CONTRACTION_BACKUP_REFERENCE=verified-backup-id \
bash scripts/runbooks/owner-schema-contraction.sh contract --execute
```

The backup reference and the two confirmations are recorded in the proof table;
the runbook revokes only exact legacy-table grants and refuses schema/global
privileges or non-empty column grants. This invokes the separate
`flyway-contraction.conf` history and does not edit an applied migration. It
retires only the proven Submission/Notification legacy tables;
`consumer_inbox` and `app_command_receipt` remain. There is no in-place
rollback—the recovery authority is the verified pre-window backup.

## Creating a Migration

Use an increasing timestamp:

```bash
touch init-db/migrations/V20260606160000__Add_New_Feature.sql
```

Rules:

1. Never modify a migration that may already be applied.
2. Add a later migration for every schema or data correction.
3. Keep `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql`
   after all historical public-password seed migrations.
4. Do not add usable default accounts or plaintext credentials.
5. Use MySQL-compatible, repeatable data updates where rollback is not available.

Run `./scripts/dev/migrate.sh validate` after adding a migration and validate the
complete chain against a fresh MySQL database before release.

## Convergence — Fresh-install Baseline & Seed Isolation (2026-08-22)

Historical applied migrations remain immutable per `AGENTS.md §Database changes`. New tooling
provides **fresh-install** convergence without rewriting history:

- **Baseline**: `init-db/baseline/baseline.sql` (generated, not Flyway source) + `init-db/scripts/generate-baseline.sh` / `validate-baseline.sh`. See the [baseline README](baseline/README.md). Fresh-install via baseline avoids re-running legacy seed migrations on new databases.
- **Seed isolation (new seeds only)**: New seeds go to `init-db/migrations/seed/` (dev/test only). Legacy `V20260603*` etc. remain on the shared chain (`flyway.conf`) for backward compatibility — incremental `migrate` still scans them (already applied). See the [seed README](migrations/seed/README.md) for the seam table and for the correct per-schema baseline constraints (`MIGRATION_SCHEMA` required).
- Historical migration candidates and their non-destructive archive policy are listed in the [migration archive README](migrations/archive/README.md).
- **App Owner DEV-LOCAL seed**: `init-db/scripts/app-owner-seed.sh` reuses immutable problemset, forum, contest, global-ranking and solution seed sources after App Owner migrations, guarded by `DEV_LOCAL_SEED_DATA_ENABLED=true`; it preserves complete `app` data, maps legacy admin fixtures locally, uses fixture IDs without runtime cross-Owner reads, fails closed on partial data, and is never called by production Compose.
- **App Forum schema repair**: `app/V20260823170000__Align_Forum_Posts_With_Runtime_Contracts.sql` is an additive, baseline-compatible repair for the legacy six-column `forum_posts` table; it preserves `content`/existing rows and aligns soft-delete, sort, JSON and excerpt fields used by the runtime.
- **Owner ownership**: `init-db/scripts/owner-migrate.sh` is the deep module interface `migrate(owner)` / `validate(owner)` / `info(owner)`. The supported orchestration `scripts/dev/up.sh` delegates owner migrations through this seam. Direct `scripts/dev/migrate.sh` with `MIGRATION_SCHEMA` remains the low-level primitive.

For AI: read `baseline.sql` for the converged final schema; consult `migrations/` only for historical intent.

## Migration Operational Checklist

> Added by R10.8 (2026-06-17) to close F-SEC-10 (Flyway 迁移期间 admin / 用户操作无锁). See
> [docs/archive/contest/_archive/EXECUTION_PLAN_R10_2026-06-18.md](../docs/archive/contest/_archive/EXECUTION_PLAN_R10_2026-06-18.md) for historical context.

### Before Running

1. **Schedule maintenance window** for any DDL on `contest_submissions` /
   `contest_participants` tables (largest tables, longest lock time)
2. **Verify MySQL version ≥ 8.0** to use `ALGORITHM=INPLACE, LOCK=NONE`
3. **Backup database** (full + binlog) before any unique index / generated column add

### MySQL Session Settings (per migration run)

```sql
SET SESSION innodb_lock_wait_timeout = 10;   -- default 50s
SET SESSION lock_wait_timeout = 10;
```

These short timeouts surface lock contention early instead of silently blocking user
requests for 50 seconds.

### DDL Hints (MySQL 8.0+)

Prefer online DDL to avoid blocking writes:

```sql
ALTER TABLE contest_participants
  ADD UNIQUE KEY uk_active_global (user_id, is_active_global),
  ALGORITHM=INPLACE, LOCK=NONE;
```

Verify the DDL is truly online:

```sql
-- Before running
SET SESSION lock_wait_timeout = 1;
-- Run the DDL; if it blocks > 1s, fall back to a maintenance window
```

### Seed vs Migration Ordering

- **Schema migrations**: `V{timestamp}__*.sql` (versioned, runs once)
- **Repeatable seed / data**: `R__*.sql` (Flyway repeatable, runs on checksum change)
- **Never** mix schema changes into `R__` files (will re-run on every Flyway start, break
  production data)

When a new schema migration depends on a seed having specific shape, ensure the seed
file timestamp sorts **before** the schema migration timestamp.

### Rollback

Flyway does not auto-rollback. For each migration, prepare a reverse SQL in
`init-db/rollback/V{timestamp}__*.rollback.sql` (manual execution only, **not** run by
Flyway automatically). This is a safety net for emergency reverts; most schema changes
are forward-only by design.

### Migration Audit

For security-sensitive migrations (e.g., `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql`),
log the deployment to `.claude/migration-deployments.log` with:

```
YYYY-MM-DD HH:MM UTC  <migration-name>  deployed-by=<user>  ticket=<ref>  rollback-script=<path>
```
