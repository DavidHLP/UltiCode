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
---

# Database migrations

- Follow the root database rules and `init-db/README.md`; use `docs/OPERATIONS.md#数据库迁移与-owner-收敛` for the applicable migration workflow.
- Add a new canonical migration; do not edit applied history or introduce usable seed credentials. Preserve owner schema isolation and separate migration credentials from runtime accounts.
- Keep existing opt-in guards for local baseline adoption and seed data; do not enable them implicitly or in production.
- For populated tables, check affected data and lock/backfill risk; choose compatible DDL and a recovery path. Do not assume every alteration supports online DDL.
- Run the applicable migration check. Backfill/cutover procedures apply only when the change performs those operations.
