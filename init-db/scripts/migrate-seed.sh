#!/usr/bin/env bash
# migrate-seed.sh — apply schema + isolated seed data (dev/test only)
# Usage: ./init-db/scripts/migrate-seed.sh [migrate|validate|info]
# Production must use ./scripts/dev/migrate.sh (schema only).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CMD="${1:-migrate}"
echo "[seed] running Flyway with seed location: migrations + migrations/seed"
# Use the seed config which includes both locations; requires DB_* env as per init-db/README.md
# Example: DB_HOST=... DB_PORT=... DB_USER=... DB_PASSWORD=... DB_NAME=ulticode ./init-db/scripts/migrate-seed.sh migrate
cd "$ROOT/init-db"
mvn flyway:"$CMD" -Dflyway.configFiles="flyway-seed.conf" --no-transfer-progress -B
