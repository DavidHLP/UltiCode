#!/usr/bin/env bash
set -euo pipefail

# Cutover gate for the object-storage release. The App and Admin read legacy
# rows exclusively from the object store, so a deploy must not replace those
# containers while legacy avatar paths or legacy backup rows are still waiting
# for their object upload. The backfill is
# `scripts/dev/migrate-object-storage.sh --apply`; this gate uses the same row
# predicates as that tool, so it passes exactly when the tool has nothing left
# to migrate.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"
capture_env_vars MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
  MIGRATION_DB_USER MIGRATION_DB_PASSWORD MIGRATION_MYSQL_BIN \
  MIGRATION_MYSQL_CONTAINER MIGRATION_MYSQL_CONTAINER_PORT DOCKER_BIN
load_env_file
apply_env_overrides

MYSQL_BIN="${MIGRATION_MYSQL_BIN:-mysql}"
DOCKER_BIN="${DOCKER_BIN:-docker}"
MYSQL_CONTAINER="${MIGRATION_MYSQL_CONTAINER:-}"
MYSQL_CONTAINER_PORT="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"

for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
    MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
  [[ -n "${!variable:-}" ]] || {
    echo "Legacy object migration gate failed: $variable is required" >&2
    exit 1
  }
done
[[ "$MIGRATION_DB_NAME" == ulticode ]] || {
  echo "Legacy object migration requires MIGRATION_DB_NAME=ulticode" >&2
  exit 1
}
valid_identifier "$MIGRATION_DB_USER" || {
  echo 'Legacy object migration gate received an invalid migration account' >&2
  exit 1
}
valid_port "$MIGRATION_DB_PORT" || {
  echo 'Legacy object migration gate received an invalid migration port' >&2
  exit 1
}
valid_port "$MYSQL_CONTAINER_PORT" || {
  echo 'Legacy object migration gate received an invalid MySQL container port' >&2
  exit 1
}

mysql_query() {
  local sql="$1"
  if [[ -n "$MYSQL_CONTAINER" ]]; then
    "$DOCKER_BIN" exec -e "MYSQL_PWD=$MIGRATION_DB_PASSWORD" "$MYSQL_CONTAINER" \
      "$MYSQL_BIN" --protocol=tcp -h 127.0.0.1 -P "$MYSQL_CONTAINER_PORT" \
      --batch --skip-column-names -u "$MIGRATION_DB_USER" ulticode -e "$sql"
  else
    MYSQL_PWD="$MIGRATION_DB_PASSWORD" "$MYSQL_BIN" --protocol=tcp \
      -h "$MIGRATION_DB_HOST" -P "$MIGRATION_DB_PORT" \
      --batch --skip-column-names -u "$MIGRATION_DB_USER" ulticode -e "$sql"
  fi
}

# Prints the row count, or nothing when the probe itself is unusable; callers
# validate the output so a broken probe can never read as "nothing to migrate".
count_rows() {
  local schema="$1" table="$2" predicate="$3" table_count
  table_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$schema' AND table_name='$table' AND table_type='BASE TABLE';")"
  [[ "$table_count" =~ ^[01]$ ]] || {
    echo "Legacy object migration gate failed: invalid $schema.$table table probe" >&2
    return 1
  }
  [[ "$table_count" == 1 ]] || { echo 0; return 0; }
  mysql_query "SELECT COUNT(*) FROM \`$schema\`.\`$table\` WHERE $predicate;"
}

legacy_avatars="$(count_rows app user_profiles "avatar LIKE '/uploads/avatars/%'")"
[[ "$legacy_avatars" =~ ^[0-9]+$ ]] || {
  echo "Legacy object migration gate failed: invalid app.user_profiles row probe" >&2
  exit 1
}

legacy_backups="$(count_rows admin backups "status='COMPLETED' AND (object_key IS NULL OR object_key='')")"
[[ "$legacy_backups" =~ ^[0-9]+$ ]] || {
  echo "Legacy object migration gate failed: invalid admin.backups row probe" >&2
  exit 1
}

if [[ "$legacy_avatars" != 0 || "$legacy_backups" != 0 ]]; then
  echo "Legacy object migration gate failed: $legacy_avatars legacy avatar row(s) and $legacy_backups legacy backup row(s) still need their object upload; the new App and Admin serve these only from the object store." >&2
  echo 'Run ./scripts/dev/migrate-object-storage.sh (dry run first, then --apply) on the deploy host, complete the users-index backfill confirmation it requires, and re-run this deployment.' >&2
  exit 1
fi

echo "LEGACY_OBJECT_MIGRATION status=PASS legacy_avatars=0 legacy_backups=0"
