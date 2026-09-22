#!/usr/bin/env bash
set -euo pipefail

# Confirm that graceful Admin shutdown drained every durable backup job before
# a privileged owner migration copies legacy metadata.
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
    echo "Admin backup drain preflight failed: $variable is required" >&2
    exit 1
  }
done
[[ "$MIGRATION_DB_NAME" == ulticode ]] || {
  echo "Admin backup drain requires MIGRATION_DB_NAME=ulticode" >&2
  exit 1
}
valid_identifier "$MIGRATION_DB_USER" || {
  echo 'Admin backup drain received an invalid migration account' >&2
  exit 1
}
valid_port "$MIGRATION_DB_PORT" || {
  echo 'Admin backup drain received an invalid migration port' >&2
  exit 1
}
valid_port "$MYSQL_CONTAINER_PORT" || {
  echo 'Admin backup drain received an invalid MySQL container port' >&2
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

for schema in ulticode admin; do
  table_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$schema' AND table_name='backups' AND table_type='BASE TABLE';")"
  [[ "$table_count" =~ ^[01]$ ]] || {
    echo "Admin backup drain failed: invalid $schema.backups table probe" >&2
    exit 1
  }
  [[ "$table_count" == 1 ]] || continue
  active_count="$(mysql_query "SELECT COUNT(*) FROM \`$schema\`.\`backups\` WHERE status IN ('PENDING','IN_PROGRESS');")"
  [[ "$active_count" =~ ^[0-9]+$ ]] || {
    echo "Admin backup drain failed: invalid $schema.backups state probe" >&2
    exit 1
  }
  if [[ "$active_count" != 0 ]]; then
    echo "Admin backup drain failed: $schema.backups still has $active_count PENDING/IN_PROGRESS row(s)" >&2
    exit 1
  fi
done

echo 'ADMIN_BACKUP_DRAIN status=PASS active_rows=0'
