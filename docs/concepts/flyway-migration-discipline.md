---
title: Flyway Migration Discipline
type: concept
tags: [database, migration]
status: living
updated: 2026-06-21
sources:
  - init-db/migrations/
  - init-db/flyway.conf
  - scripts/dev/migrate.sh
  - CLAUDE.md
aliases: [Flyway 迁移纪律]
---

# Flyway Migration Discipline

## The problem
Schema evolves over time; editing applied migrations breaks checksums and every
deploy downstream, and careless seeds leak default credentials.

## The decision
- `init-db/migrations/` is the **sole** migration source.
- Naming: `V{timestamp}__Description.sql` (timestamp strictly increasing).
- **Never edit an already-applied migration** — add a new one with a larger timestamp.
- **No usable default user or public password** in any migration.
- The real admin is created only by the opt-in `AdminBootstrapRunner`
  (dev-profile-only, off in normal startup).
- `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts` is a security
  fix that **must stay after any demo seed** — never delete or reorder it below seed.
- Apply via `./scripts/dev/migrate.sh migrate` (the wrapper; raw `flyway` is not
  on PATH). Checksum mismatch recovery: `./scripts/dev/migrate.sh repair`.

## Where it lives
- `init-db/migrations/V*.sql`, `init-db/flyway.conf`, `scripts/dev/migrate.sh`.

## Trade-offs
- Append-only history grows; accepted for auditability and zero-downtime ordering.
- Testcontainers (MySQL 9.1) replays migrations in CI — the safety net.

## Related
[[overview/database-schema-overview]] · [[concepts/refresh-token-hash-only-storage]]
