---
kind: rules
paths:
  - 'init-db/flyway.conf'
  - 'init-db/migrations/**/*.sql'
  - 'scripts/dev/migrate.sh'
summary: 'Flyway migration rules for schema changes.'
triggers:
  - 'migration'
  - 'flyway'
  - 'schema change'
  - 'sql migration'
---
# Database migration workflow

- Read the database and security sections of the root `AGENTS.md` before editing SQL.
- Inspect the migration history and trace every affected entity, mapper, query, and API contract; do not derive the current schema from one source alone.
- Determine the new filename from the repository policy and latest versions. Treat legacy naming variants as history, not templates to normalize.
- Write an expand/backfill/enforce/cleanup rollout checklist and identify which stages belong in this change.
- Estimate lock, scan, and index-build risk for populated tables and make rollback or forward-fix behavior explicit.
- Run the migration and configuration checks required by the root guide, then review the schema and application diff together.
