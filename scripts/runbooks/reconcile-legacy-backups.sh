#!/usr/bin/env bash
set -euo pipefail

# Reconcile rows that an already-applied Flyway copy could not see.  The
# source table is kept for rollback evidence; this runbook never deletes it.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"
capture_env_vars MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
  MIGRATION_DB_USER MIGRATION_DB_PASSWORD MIGRATION_MYSQL_BIN \
  OWNER_MIGRATION_MYSQL_BIN MIGRATION_MYSQL_CONTAINER MIGRATION_MYSQL_CONTAINER_PORT \
  DOCKER_BIN
load_env_file
apply_env_overrides

MYSQL_BIN="${MIGRATION_MYSQL_BIN:-${OWNER_MIGRATION_MYSQL_BIN:-mysql}}"
DOCKER_BIN="${DOCKER_BIN:-docker}"
MYSQL_CONTAINER="${MIGRATION_MYSQL_CONTAINER:-}"
MYSQL_CONTAINER_PORT="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"

for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
    MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
  [[ -n "${!variable:-}" ]] || {
    echo "Legacy backup reconciliation preflight failed: $variable is required" >&2
    exit 1
  }
done
[[ "$MIGRATION_DB_NAME" == ulticode ]] || {
  echo "Legacy backup reconciliation requires MIGRATION_DB_NAME=ulticode" >&2
  exit 1
}
valid_identifier "$MIGRATION_DB_USER" || {
  echo "Legacy backup reconciliation received an invalid migration account" >&2
  exit 1
}
valid_port "$MIGRATION_DB_PORT" || {
  echo "Legacy backup reconciliation received an invalid migration port" >&2
  exit 1
}
valid_port "$MYSQL_CONTAINER_PORT" || {
  echo "Legacy backup reconciliation received an invalid MySQL container port" >&2
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

die() {
  echo "Legacy backup reconciliation failed: $1" >&2
  exit 1
}

count_table() {
  local schema="$1"
  mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$schema' AND table_name='backups' AND table_type='BASE TABLE';"
}

count_columns() {
  local schema="$1" table="$2" columns="$3"
  mysql_query "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$schema' AND table_name='$table' AND column_name IN ($columns);"
}

source_exists="$(count_table ulticode)"
[[ "$source_exists" =~ ^[0-9]+$ ]] || die "invalid source table probe result"
if [[ "$source_exists" == 0 ]]; then
  echo 'LEGACY_BACKUP_RECONCILIATION source=absent status=SKIPPED'
  exit 0
fi

target_exists="$(count_table admin)"
[[ "$target_exists" == 1 ]] || die 'admin.backups is missing; run the Admin owner migration first'

core_columns="'id','filename','size','type','status','created_by','created_at','completed_at','metadata','error'"
[[ "$(count_columns ulticode backups "$core_columns")" == 10 ]] \
  || die 'ulticode.backups is missing one or more canonical metadata columns'
[[ "$(count_columns admin backups "$core_columns")" == 10 ]] \
  || die 'admin.backups is missing one or more canonical metadata columns'
[[ "$(count_columns admin backups "'object_key','checksum'")" == 2 ]] \
  || die 'admin.backups.object_key/checksum are missing; run the Admin object-storage migration first'

source_object_key="$(mysql_query "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='ulticode' AND table_name='backups' AND column_name='object_key';")"
source_checksum="$(mysql_query "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='ulticode' AND table_name='backups' AND column_name='checksum';")"
[[ "$source_object_key" =~ ^[01]$ && "$source_checksum" =~ ^[01]$ ]] \
  || die 'invalid optional source backup column probe result'
source_object_key_expr='NULL'
source_checksum_expr='NULL'
[[ "$source_object_key" == 1 ]] && source_object_key_expr='s.`object_key`'
[[ "$source_checksum" == 1 ]] && source_checksum_expr='s.`checksum`'
source_object_key_parity='1=1'
source_checksum_parity='1=1'
[[ "$source_object_key" == 1 ]] \
  && source_object_key_parity='s.`object_key` <=> d.`object_key`'
[[ "$source_checksum" == 1 ]] \
  && source_checksum_parity='s.`checksum` <=> d.`checksum`'

marker_exists="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='admin' AND table_name='backup_cutover_state' AND table_type='BASE TABLE';")"
[[ "$marker_exists" == 1 ]] || die 'admin.backup_cutover_state is missing; apply the post-owner migration first'
tombstone_table_exists="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='admin' AND table_name='backup_deletion_tombstones' AND table_type='BASE TABLE';")"
[[ "$tombstone_table_exists" == 1 ]] || die 'admin.backup_deletion_tombstones is missing; apply the post-owner migration first'
marker_rows="$(mysql_query "SELECT COUNT(*) FROM admin.backup_cutover_state WHERE id=1;")"
[[ "$marker_rows" == 1 ]] || die 'admin.backup_cutover_state singleton row is missing'
marker_completed="$(mysql_query "SELECT COUNT(*) FROM admin.backup_cutover_state WHERE id=1 AND cutover_completed_at IS NOT NULL;")"
[[ "$marker_completed" =~ ^[01]$ ]] || die 'invalid backup cutover marker state'

metadata_parity='s.`metadata` <=> d.`metadata`'
if [[ "$marker_completed" == 1 ]]; then
  # Restore writes target-owned audit fields after cutover; compare the
  # remaining legacy metadata while treating null and an empty object alike.
  metadata_parity="COALESCE(s.metadata, JSON_OBJECT()) <=> COALESCE(JSON_REMOVE(d.metadata, '\$.lastRestoredAt', '\$.lastRestoredBy'), JSON_OBJECT())"
fi

# This INSERT is the durable late-row repair.  It intentionally updates no
# existing target row; metadata conflicts are reported by the parity query below.
mysql_query "INSERT INTO admin.backups
  (id, filename, object_key, size, checksum, type, status, created_by,
   created_at, completed_at, metadata, error)
SELECT s.id, s.filename, $source_object_key_expr, s.size, $source_checksum_expr,
       s.type, s.status, s.created_by, s.created_at, s.completed_at,
       s.metadata, s.error
  FROM ulticode.backups AS s
  LEFT JOIN admin.backups AS d ON d.id = s.id
  LEFT JOIN admin.backup_deletion_tombstones AS t
    ON t.backup_id COLLATE utf8mb4_unicode_ci = s.id COLLATE utf8mb4_unicode_ci
 WHERE d.id IS NULL
   AND t.backup_id IS NULL;" >/dev/null

source_only="$(mysql_query "SELECT COUNT(*)
  FROM ulticode.backups AS s
  LEFT JOIN admin.backups AS d ON d.id=s.id
  LEFT JOIN admin.backup_deletion_tombstones AS t
    ON t.backup_id COLLATE utf8mb4_unicode_ci = s.id COLLATE utf8mb4_unicode_ci
 WHERE d.id IS NULL AND t.backup_id IS NULL;")"
mismatch="$(mysql_query "SELECT COUNT(*)
  FROM ulticode.backups AS s
  JOIN admin.backups AS d ON d.id=s.id
  LEFT JOIN admin.backup_deletion_tombstones AS t
    ON t.backup_id COLLATE utf8mb4_unicode_ci = s.id COLLATE utf8mb4_unicode_ci
 WHERE NOT (
       s.filename <=> d.filename
   AND s.size <=> d.size
   AND s.type <=> d.type
   AND s.status <=> d.status
   AND s.created_by <=> d.created_by
   AND s.created_at <=> d.created_at
   AND s.completed_at <=> d.completed_at
   AND $metadata_parity
   AND $source_object_key_parity
   AND $source_checksum_parity
   AND s.error <=> d.error
 )
   AND t.backup_id IS NULL;")"
if [[ "$marker_completed" == 0 ]]; then
  target_extra="$(mysql_query "SELECT COUNT(*) FROM admin.backups AS d LEFT JOIN ulticode.backups AS s ON s.id=d.id WHERE s.id IS NULL;")"
else
  # Rows created by the new Admin writer after the durable cutover marker are
  # expected target-only rows.  Older target-only rows indicate a conflicting
  # pre-cutover write or source deletion and remain a hard failure.
  target_extra="$(mysql_query "SELECT COUNT(*)
    FROM admin.backups AS d
    LEFT JOIN ulticode.backups AS s ON s.id=d.id
   WHERE s.id IS NULL
     AND d.created_at <= (SELECT cutover_completed_at FROM admin.backup_cutover_state WHERE id=1);")"
fi
source_rows="$(mysql_query 'SELECT COUNT(*) FROM ulticode.backups;')"
target_rows="$(mysql_query 'SELECT COUNT(*) FROM admin.backups;')"
for value in "$source_only" "$mismatch" "$target_extra" "$source_rows" "$target_rows"; do
  [[ "$value" =~ ^[0-9]+$ ]] || die 'invalid parity query result'
done

if [[ "$source_only" != 0 || "$mismatch" != 0 || "$target_extra" != 0 ]]; then
  printf 'LEGACY_BACKUP_RECONCILIATION source_rows=%s target_rows=%s source_only=%s metadata_mismatch=%s pre_cutover_target_extra=%s status=FAIL\n' \
    "$source_rows" "$target_rows" "$source_only" "$mismatch" "$target_extra" >&2
  exit 1
fi

mysql_query "UPDATE admin.backup_cutover_state
   SET source_row_count=$source_rows,
       target_row_count=$target_rows,
       cutover_completed_at=COALESCE(cutover_completed_at, CURRENT_TIMESTAMP(3)),
       last_reconciled_at=CURRENT_TIMESTAMP(3)
 WHERE id=1;" >/dev/null
marker_rows_after="$(mysql_query "SELECT COUNT(*) FROM admin.backup_cutover_state WHERE id=1;")"
[[ "$marker_rows_after" == 1 ]] || die 'admin.backup_cutover_state singleton row disappeared during reconciliation'

printf 'LEGACY_BACKUP_RECONCILIATION source_rows=%s target_rows=%s source_only=0 metadata_mismatch=0 pre_cutover_target_extra=0 cutover_marker=%s status=PASS\n' \
  "$source_rows" "$target_rows" "$([[ "$marker_completed" == 1 ]] && echo existing || echo set)"
