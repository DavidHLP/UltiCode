---
paths:
  - "init-db/flyway*.conf"
  - "init-db/migrations/**/*.sql"
  - "init-db/baseline/**/*.sql"
  - "init-db/rollback/**/*.sql"
  - "init-db/scripts/**/*.sh"
  - "init-db/validate-migration.sh"
  - "scripts/dev/migrate.sh"
  - "scripts/dev/owner-*.sh"
  - "scripts/dev/migrate-owner-*.sh"
kind: rules
summary: 'Flyway migration rules for schema changes.'
---

# Database migration workflow

- Read the database and security sections of the root `AGENTS.md` before editing SQL.
- Inspect the migration history and trace every affected entity, mapper, query, and API contract; do not derive the current schema from one source alone.
- Determine the new filename from the repository policy and latest versions. Treat legacy naming variants as history, not templates to normalize.
- Write an expand/backfill/enforce/cleanup rollout checklist and identify which stages belong in this change.
- Estimate lock, scan, and index-build risk for populated tables and make rollback or forward-fix behavior explicit.
- Run the migration and configuration checks required by the root guide, then review the schema and application diff together.
- Owner schemas (`auth`, `admin`, `app`, `notification`, `submission`) maintain isolated Flyway migration configurations (`flyway-*.conf`).
- Migrations are immutable; fresh-install convergence uses `baseline.sql`. Never edit applied migrations.
- Dev-local owner baseline adoption requires explicit confirmation (`DEV_LOCAL_OWNER_BASELINE=true` and `DEV_LOCAL_OWNER_BASELINE_CONFIRM=I_UNDERSTAND_DEV_LOCAL_OWNER_BASELINE`).
- Owner migration credentials (`MIGRATION_DB_*`) must remain separate from runtime accounts and require direct privilege grants.
- Dev-local seed data scripts (`init-db/scripts/app-owner-seed.sh`) must be guarded by `DEV_LOCAL_SEED_DATA_ENABLED=true` and never run in production.
