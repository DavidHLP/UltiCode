# Baseline — Fresh-install Tooling (Immutable History Preserved)

`init-db/migrations/` remains the **sole Flyway source of truth**. All 89 applied migrations are **immutable** per `AGENTS.md §Database changes` — they are never edited, moved, or squashed.

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

## Fresh-install Usage (Optional)

For a brand-new database, you may either:

1. **Standard**: Run Flyway `migrate` over the 89 migrations (always supported).
2. **Baseline-optimized**: Load `baseline.sql` then `flyway baseline` at the current version, so future increments apply normally. This path is documented but not required.

Both paths produce identical final schemas; the baseline is validated by `validate-baseline.sh`.

## AI Navigability

Instead of reading 72 root + owner files to infer the final schema, AI/tools can read `baseline.sql` for the converged shape and consult `migrations/` only for historical intent.

