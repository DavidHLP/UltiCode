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

## Migration Operational Checklist

> Added by R10.8 (2026-06-17) to close F-SEC-10 (Flyway 迁移期间 admin / 用户操作无锁). See
> [docs/contest/EXECUTION_PLAN_R10 §8 (归档)](../docs/contest/_archive/EXECUTION_PLAN_R10_2026-06-18.md) for context.

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
