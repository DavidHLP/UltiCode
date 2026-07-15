---
paths:
  - "init-db/flyway.conf"
  - "init-db/migrations/**/*.sql"
  - "scripts/dev/migrate.sh"
---

# Database migration workflow

- Apply the database and security sections of the root `AGENTS.md`; `init-db/migrations/` is the only migration source.
- Inspect the latest migrations plus every affected entity, mapper, query, and API contract before writing SQL.
- Add a new migration named `V{timestamp}__Description.sql`. Do not rename legacy files to normalize their historical naming styles, and never edit an applied migration.
- Make rollout behavior explicit: preserve old readers and writers as needed, backfill before tightening constraints, and consider lock duration and index-build cost on populated tables.
- Use deterministic, rerunnable data transformations where practical. Never add usable default credentials or plaintext secrets.
- Verify the migration through the supported development scripts, validate both development and production Compose configurations when relevant, and run `git diff --check`.
