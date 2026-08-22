#!/usr/bin/env bash
# owner-migrate.sh — Unified Owner Schema Ownership interface
# Consolidates 5× flyway-*.conf into one deep module interface.
# Existing per-owner directories (migrations/{auth,admin,app,notification,submission}) and
# flyway-*.conf files remain immutable; this script is a fresh-install/orchestration helper.
# Usage:
#   ./init-db/scripts/owner-migrate.sh migrate [owner]   # migrate one or all owners
#   ./init-db/scripts/owner-migrate.sh validate [owner]
#   ./init-db/scripts/owner-migrate.sh info [owner]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CMD="${1:-migrate}"
OWNER="${2:-all}"
OWNERS=(auth admin app notification submission)
run_one() {
  local owner="$1"
  local conf="$ROOT/init-db/flyway-${owner}.conf"
  if [ ! -f "$conf" ]; then echo "unknown owner: $owner"; exit 1; fi
  echo "[owner-migrate] $CMD $owner via $conf"
  # Delegate to existing migrate.sh with MIGRATION_SCHEMA set, preserving privileged account contract
  MIGRATION_SCHEMA="$owner" "$ROOT/scripts/dev/migrate.sh" "$CMD"
}
if [ "$OWNER" = "all" ]; then
  for o in "${OWNERS[@]}"; do run_one "$o"; done
else
  run_one "$OWNER"
fi
