#!/usr/bin/env bash
set -euo pipefail

# SPLIT-003 slice-4 runbook helper: submission aggregate + judge/result outbox
# expand -> backfill -> verify -> cutover runbook. The default action is
# read-only preflight. A cutover/rollback requires both --execute and an
# explicit confirmation token.
#
# The source schema is the App schema (`ulticode` by default) where the
# submission aggregate and outboxes currently live; the target is the
# dedicated Submission owner schema (`submission` by default, created by
# MIGRATION_SCHEMA=submission ./scripts/dev/migrate.sh migrate).
#
# Gate: this script copies data and revokes App write grants, but the actual
# runtime cutover (APP_SUBMISSION_ROUTING_MODE=remote) must only be enabled
# after the SPLIT-004 read-path migration, because App read adapters still read
# the App schema until then. The Submission provider is local-only after the
# compatibility forwarder retirement. See services/docs/MICROSERVICE_MIGRATION_GUIDE.md §8.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
ACTION="${1:-preflight}"
EXECUTE="${2:-}"

case "$ACTION" in
  preflight|cutover|rollback) ;;
  *)
    echo "Usage: $0 [preflight|cutover|rollback] [--execute]" >&2
    exit 2
    ;;
esac

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Run ./scripts/dev/init-env.sh first." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:?DB_PORT is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

SOURCE_SCHEMA="${SUBMISSION_SOURCE_SCHEMA:-$DB_NAME}"
TARGET_SCHEMA="${SUBMISSION_DB_NAME:-submission}"
MIGRATION_USER="${MIGRATION_DB_USER:-$DB_USER}"
MIGRATION_PASSWORD="${MIGRATION_DB_PASSWORD:-$DB_PASSWORD}"
APP_DB_USER="${SUBMISSION_APP_DB_USER:-}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-}"

for identifier in "$SOURCE_SCHEMA" "$TARGET_SCHEMA" "$MIGRATION_USER" "$APP_DB_USER"; do
  if [[ -n "$identifier" && ! "$identifier" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "Invalid schema/user identifier: $identifier" >&2
    exit 1
  fi
done

# Submission owner tables copied from the App compatibility schema. These move
# exclusively to Submission; the target-only created outbox is checked below
# but is never copied back to App during rollback.
TABLES=(
  submissions
  judge_outbox
  submission_result_outbox
)

TARGET_ONLY_TABLES=(
  submission_created_outbox
)

mysql_query() {
  local query="$1"
  if [[ -n "$MYSQL_CONTAINER" ]]; then
    docker exec -e MYSQL_PWD="$MIGRATION_PASSWORD" "$MYSQL_CONTAINER" \
      mysql --default-character-set=utf8mb4 -u "$MIGRATION_USER" \
      --batch --skip-column-names -e "$query"
  else
    MYSQL_PWD="$MIGRATION_PASSWORD" mysql \
      --protocol=tcp -h "$DB_HOST" -P "$DB_PORT" -u "$MIGRATION_USER" \
      --default-character-set=utf8mb4 --batch --skip-column-names -e "$query"
  fi
}

table_exists() {
  local schema="$1" table="$2"
  [[ "$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$schema' AND table_name = '$table';")" == "1" ]]
}

column_signature() {
  local schema="$1" table="$2"
  mysql_query "SELECT COALESCE(GROUP_CONCAT(CONCAT_WS(':', ordinal_position, column_name, column_type, is_nullable, COALESCE(column_default, '<NULL>'), extra, COALESCE(character_set_name, ''), COALESCE(collation_name, '')) ORDER BY ordinal_position SEPARATOR '|'), '') FROM information_schema.columns WHERE table_schema = '$schema' AND table_name = '$table';"
}

row_count() {
  local schema="$1" table="$2"
  mysql_query "SELECT COUNT(*) FROM \`$schema\`.\`$table\`;"
}

checksum() {
  local schema="$1" table="$2"
  mysql_query "CHECKSUM TABLE \`$schema\`.\`$table\`;" | awk '{print $2}'
}

print_snapshot() {
  local schema="$1" label="$2"
  echo "[$label] schema=$schema"
  for table in "${TABLES[@]}"; do
    if table_exists "$schema" "$table"; then
      echo "  $table rows=$(row_count "$schema" "$table") checksum=$(checksum "$schema" "$table")"
    else
      echo "  $table MISSING"
    fi
  done
  for table in "${TARGET_ONLY_TABLES[@]}"; do
    if table_exists "$schema" "$table"; then
      echo "  $table (target-only) rows=$(row_count "$schema" "$table") checksum=$(checksum "$schema" "$table")"
    else
      echo "  $table (target-only) MISSING"
    fi
  done
}

assert_ready() {
  for table in "${TABLES[@]}"; do
    if ! table_exists "$SOURCE_SCHEMA" "$table"; then
      echo "Source table missing: $SOURCE_SCHEMA.$table" >&2
      return 1
    fi
    if ! table_exists "$TARGET_SCHEMA" "$table"; then
      echo "Target table missing: $TARGET_SCHEMA.$table; run MIGRATION_SCHEMA=submission ./scripts/dev/migrate.sh migrate first" >&2
      return 1
    fi
    if [[ "$(column_signature "$SOURCE_SCHEMA" "$table")" != "$(column_signature "$TARGET_SCHEMA" "$table")" ]]; then
      echo "Column shape mismatch: $SOURCE_SCHEMA.$table vs $TARGET_SCHEMA.$table" >&2
      return 1
    fi
    if [[ "$(row_count "$TARGET_SCHEMA" "$table")" != "0" ]]; then
      echo "Target table is not empty: $TARGET_SCHEMA.$table" >&2
      return 1
    fi
  done
  for table in "${TARGET_ONLY_TABLES[@]}"; do
    if ! table_exists "$TARGET_SCHEMA" "$table"; then
      echo "Target-only table missing: $TARGET_SCHEMA.$table; run submission migrations first" >&2
      return 1
    fi
    if [[ "$(row_count "$TARGET_SCHEMA" "$table")" != "0" ]]; then
      echo "Target-only table is not empty: $TARGET_SCHEMA.$table" >&2
      return 1
    fi
  done
}

require_execute() {
  local expected="$1"
  if [[ "$EXECUTE" != "--execute" || "${SUBMISSION_CUTOVER_CONFIRM:-}" != "$expected" ]]; then
    echo "Refusing write action. Pass --execute and SUBMISSION_CUTOVER_CONFIRM=$expected." >&2
    exit 1
  fi
}

copy_forward() {
  for table in "${TABLES[@]}"; do
    mysql_query "INSERT INTO \`$TARGET_SCHEMA\`.\`$table\` SELECT * FROM \`$SOURCE_SCHEMA\`.\`$table\`;"
  done
}

copy_back() {
  for table in "${TABLES[@]}"; do
    mysql_query "REPLACE INTO \`$SOURCE_SCHEMA\`.\`$table\` SELECT * FROM \`$TARGET_SCHEMA\`.\`$table\`;"
  done
}

revoke_app_grants() {
  if [[ -z "$APP_DB_USER" ]]; then
    echo "SUBMISSION_APP_DB_USER is required to revoke App grants on the submission tables." >&2
    return 1
  fi
  for table in "${TABLES[@]}"; do
    mysql_query "REVOKE SELECT, INSERT, UPDATE, DELETE ON \`$SOURCE_SCHEMA\`.\`$table\` FROM '$APP_DB_USER'@'%';"
  done
  mysql_query "FLUSH PRIVILEGES;"
}

restore_app_grants() {
  if [[ -z "$APP_DB_USER" ]]; then
    echo "SUBMISSION_APP_DB_USER is required to restore App table grants." >&2
    return 1
  fi
  for table in "${TABLES[@]}"; do
    mysql_query "GRANT SELECT, INSERT, UPDATE, DELETE ON \`$SOURCE_SCHEMA\`.\`$table\` TO '$APP_DB_USER'@'%';"
  done
  mysql_query "FLUSH PRIVILEGES;"
}

# The cutover REVOKEs are table-scoped. If the App user only holds schema-wide
# grants (e.g. GRANT ... ON ulticode.*), the table-scoped REVOKE fails with
# ERROR 1147 after copy_forward has already persisted rows, leaving the target
# non-empty and blocking retries. Verify revoke capability BEFORE any copy so
# a misconfiguration aborts the runbook without side effects.
app_user_table_grant_exists() {
  local schema="$1" table="$2"
  [[ "$(mysql_query "SELECT COUNT(*) FROM information_schema.table_privileges
      WHERE GRANTEE = '\\'$APP_DB_USER\\'@\\'%\\''
        AND TABLE_SCHEMA = '$schema' AND TABLE_NAME = '$table'
        AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE');")" -ge 1 ]]
}

assert_revoke_ready() {
  if [[ -z "$APP_DB_USER" ]]; then
    echo "SUBMISSION_APP_DB_USER is required to verify App table grants." >&2
    return 1
  fi
  if [[ "$(mysql_query "SELECT COUNT(*) FROM mysql.user WHERE User = '$APP_DB_USER' AND Host = '%';")" != "1" ]]; then
    echo "App user '$APP_DB_USER'@'%' does not exist; cannot revoke grants at cutover." >&2
    return 1
  fi
  local missing=0
  for table in "${TABLES[@]}"; do
    if ! app_user_table_grant_exists "$SOURCE_SCHEMA" "$table"; then
      echo "App user '$APP_DB_USER' has no table-scoped grant on $SOURCE_SCHEMA.$table; the cutover REVOKE would fail. Grant per-table first (GRANT SELECT, INSERT, UPDATE, DELETE ON \`$SOURCE_SCHEMA\`.\`$table\` TO '$APP_DB_USER'@'%')." >&2
      missing=1
    fi
  done
  if [[ "$missing" != "0" ]]; then
    return 1
  fi
}

# Restore the pre-cutover state after a failed cutover: the target was verified
# empty by assert_ready, so deleting the copied rows is a full cleanup. Never
# touches source rows.
cleanup_failed_cutover() {
  echo "Cutover failed; cleaning copied rows from target (target was empty before copy)." >&2
  for table in "${TABLES[@]}"; do
    mysql_query "DELETE FROM \`$TARGET_SCHEMA\`.\`$table\`;"
  done
  echo "Target restored to empty; fix the cause above and re-run preflight/cutover." >&2
}

echo "Submission schema=$SOURCE_SCHEMA -> $TARGET_SCHEMA"

case "$ACTION" in
  preflight)
    print_snapshot "$SOURCE_SCHEMA" source
    print_snapshot "$TARGET_SCHEMA" target
    if [[ "$SOURCE_SCHEMA" != "$TARGET_SCHEMA" ]]; then
      assert_ready
      assert_revoke_ready || {
        echo "PRECHECK FAILED: App grants are not revocable; fix before cutover." >&2
        exit 1
      }
      echo "PRECHECK: source/target table shapes, empty target, and App grant revocability verified."
    else
      echo "PRECHECK: compatibility mode; no physical move requested."
    fi
    ;;
  cutover)
    require_execute I_UNDERSTAND_SUBMISSION_CUTOVER
    if [[ "$SOURCE_SCHEMA" == "$TARGET_SCHEMA" ]]; then
      echo "Source and target are identical; refusing a no-op cutover." >&2
      exit 1
    fi
    if [[ -z "$APP_DB_USER" ]]; then
      echo "Cutover requires SUBMISSION_APP_DB_USER=... so App grants on the submission tables can be revoked safely." >&2
      exit 1
    fi
    echo "WARNING: enable APP_SUBMISSION_ROUTING_MODE=remote only after SPLIT-004 moves App read paths off the App schema." >&2
    assert_ready
    assert_revoke_ready || {
      echo "Refusing cutover: App grants are not revocable; run preflight first." >&2
      exit 1
    }
    if ! copy_forward; then
      echo "Copy failed; aborting without revoking grants." >&2
      cleanup_failed_cutover || true
      exit 1
    fi
    if ! revoke_app_grants; then
      echo "Grant revocation failed after copy; rolling back copied rows." >&2
      cleanup_failed_cutover || true
      exit 1
    fi
    print_snapshot "$SOURCE_SCHEMA" source-after-cutover
    print_snapshot "$TARGET_SCHEMA" target-after-cutover
    ;;
  rollback)
    require_execute I_UNDERSTAND_SUBMISSION_ROLLBACK
    if [[ "$SOURCE_SCHEMA" == "$TARGET_SCHEMA" ]]; then
      echo "Source and target are identical; no rollback required." >&2
      exit 1
    fi
    if [[ -z "$APP_DB_USER" ]]; then
      echo "Rollback requires SUBMISSION_APP_DB_USER=... (the same app-schema user used for cutover)." >&2
      exit 1
    fi
    copy_back
    restore_app_grants
    print_snapshot "$SOURCE_SCHEMA" source-after-rollback
    print_snapshot "$TARGET_SCHEMA" target-after-rollback
    ;;
esac
