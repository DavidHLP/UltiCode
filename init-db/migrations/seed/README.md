# Seed Data — Isolation for New Seeds (Legacy Remains on Shared Chain)

All 14 legacy seed migrations in `migrations/V20260603*` / `V20260604*` / `V20260606*` / `V20260615*` / `V20260616*` / `V20260619*` remain **immutable** and in place per `AGENTS.md §Database changes`.
They remain on the shared chain (`flyway.conf: filesystem:migrations/*.sql`) for backward compatibility — therefore the incremental `migrate`/`validate` path still scans them (they are already applied, not re-executed).

**New seed data** (post-2026-08-22) must be placed here (`migrations/seed/`).

This seam is **executable** but only covers new seeds:

- **Fresh-install**: validated via `init-db/baseline/baseline.sql` + `./init-db/scripts/baseline-adopt.sh` (per-schema `flyway baseline` at auto-detectedmax versions; see `init-db/baseline/README.md`). Standard incremental chain remains always supported.
- **Dev/test with new seeds**: `flyway-seed.conf` → `filesystem:migrations/*.sql,filesystem:migrations/seed`

```bash
# Schema only (prod incremental, always supported)
./scripts/dev/migrate.sh migrate

# Fresh-install (validated 6-schema adoption)
./init-db/scripts/baseline-adopt.sh

# Schema + new isolated seeds (dev/test)
./init-db/scripts/migrate-seed.sh migrate
# or: DB_HOST=... DB_NAME=... mvn -f init-db/pom.xml flyway:migrate -Dflyway.configFiles=flyway-seed.conf
```

Place new seeds as `V{timestamp}__*.sql` or `R__*.sql` in this directory; they will be picked up only via the seed config. Legacy seeds cannot be moved without breaking `flyway_schema_history` on deployed databases.

## App Owner DEV-LOCAL domain seed

The supported local startup path also runs
`init-db/scripts/app-owner-seed.sh` after the App Owner migration. It executes
the immutable historical problemset, forum, contest and solution seed sources against the
`app` schema only, in separate transactions per domain. The contest transaction
also hydrates `global_rankings`, because the Contest home page reads both the
contest catalog and the global leaderboard. Each domain seeds only when its
tables are empty. Existing complete data is preserved, while partial data
causes a fail-closed abort. The legacy forum seed's one `users.admin` lookup is
mapped to the stable `forum_users` fixture locally, and the contest sources use
fixture IDs directly. The Contest transaction pins only its MySQL session to
`+08:00` so `NOW()`-relative windows match the App's `Asia/Shanghai` clock; it
does not change the database-global timezone or production configuration. The
legacy ranking seed's DiceBear placeholders are stripped by the DEV-LOCAL
adapter, and public ranking reads take avatars from App `user_profiles`; when
no real profile avatar exists, Console shows initials rather than inventing a
remote image. The new App Owner path performs no cross-Owner read.

This adapter is guarded by `DEV_LOCAL_SEED_DATA_ENABLED=true` and is invoked by
`scripts/dev/up.sh`; it is not part of the production Compose or Owner Flyway
chain. Use `./scripts/dev/up.sh --skip-seed-data` when a disposable local
database must remain empty.
