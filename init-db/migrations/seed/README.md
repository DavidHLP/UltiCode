# Seed Data — Isolated from Schema History (Executable)

All 15 existing seed migrations in `migrations/V20260603*` remain **immutable** and in place per `AGENTS.md §Database changes`.
They will continue to be applied via the shared Flyway chain for backward compatibility.

**New seed data** (post-2026-08-22) should be placed here (`migrations/seed/`).

This seam is now **executable**, not documentation-only:

- **Production** (default): `flyway.conf` → `filesystem:migrations` (seed excluded, no scan of 300k INSERTs)
- **Dev/test with seed**: `flyway-seed.conf` → `filesystem:migrations,filesystem:migrations/seed`

```bash
# Schema only (prod path, always supported)
./scripts/dev/migrate.sh migrate

# Schema + isolated seed (dev/test, new seeds)
./init-db/scripts/migrate-seed.sh migrate
# or: DB_HOST=... DB_NAME=... mvn -f init-db/pom.xml flyway:migrate -Dflyway.configFiles=flyway-seed.conf
```

Place new seeds as `V{timestamp}__*.sql` or `R__*.sql` in this directory; they will be picked up only via the seed config.

