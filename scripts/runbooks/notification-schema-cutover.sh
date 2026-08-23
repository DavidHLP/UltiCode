#!/usr/bin/env bash
set -euo pipefail

# NOTIFY-006 runbook helper. The default action is read-only preflight.
# A cutover/rollback requires both --execute and an explicit confirmation token.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"
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

load_env_file

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:?DB_PORT is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

SOURCE_SCHEMA="${NOTIFICATION_SOURCE_SCHEMA:-$DB_NAME}"
TARGET_SCHEMA="${NOTIFICATION_DB_NAME:-notification}"
MIGRATION_USER="${MIGRATION_DB_USER:-$DB_USER}"
MIGRATION_PASSWORD="${MIGRATION_DB_PASSWORD:-$DB_PASSWORD}"
APP_DB_USER="${NOTIFICATION_APP_DB_USER:-}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-}"
MYSQL_CONTAINER_PORT="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"

for identifier in "$SOURCE_SCHEMA" "$TARGET_SCHEMA" "$MIGRATION_USER" "$APP_DB_USER"; do
  if [[ -n "$identifier" ]] && ! valid_identifier "$identifier"; then
    echo "Invalid schema/user identifier: $identifier" >&2
    exit 1
  fi
done
if [[ -n "$MYSQL_CONTAINER" ]]; then
  container_running "$MYSQL_CONTAINER" \
    || { echo "MySQL container is not running: $MYSQL_CONTAINER" >&2; exit 1; }
  mysql_container_targets_configured_host "$MYSQL_CONTAINER" "$MYSQL_CONTAINER_PORT" "$DB_HOST" "$DB_PORT" \
    || { echo "Configured database target $DB_HOST:$DB_PORT is not a published endpoint of $MYSQL_CONTAINER:$MYSQL_CONTAINER_PORT" >&2; exit 1; }
fi

TABLES=(
  notifications
  notification_preferences
  notification_delivery_ledger
  email_templates
  email_logs
  consumer_inbox
)

# Tables owned exclusively by the notification runtime after cutover.
# consumer_inbox stays shared: backend-app still stages App-Achievement /
# App-WebSocket / App-Contest bindings through the same table, so its grants
# are never revoked.
NOTIFICATION_ONLY_TABLES=()
for table in "${TABLES[@]}"; do
  if [[ "$table" != "consumer_inbox" ]]; then
    NOTIFICATION_ONLY_TABLES+=("$table")
  fi
done

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
  local schema="$1" table="$2" predicate="${3:-1=1}"
  mysql_query "SELECT COUNT(*) FROM \`$schema\`.\`$table\` WHERE $predicate;"
}

checksum() {
  local schema="$1" table="$2"
  mysql_query "CHECKSUM TABLE \`$schema\`.\`$table\`;" | awk '{print $2}'
}

receipt_source_table() {
  if table_exists "$SOURCE_SCHEMA" notification_command_receipt; then
    echo notification_command_receipt
  elif table_exists "$SOURCE_SCHEMA" app_command_receipt; then
    echo app_command_receipt
  else
    echo ""
  fi
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
  local receipt
  receipt="$(receipt_source_table)"
  if [[ "$schema" == "$SOURCE_SCHEMA" && -n "$receipt" ]]; then
    echo "  $receipt rows=$(row_count "$schema" "$receipt" "service = 'NotificationAdministrationService'") checksum=$(checksum "$schema" "$receipt")"
  elif table_exists "$schema" notification_command_receipt; then
    echo "  notification_command_receipt rows=$(row_count "$schema" notification_command_receipt) checksum=$(checksum "$schema" notification_command_receipt)"
  else
    echo "  notification_command_receipt MISSING"
  fi
}

assert_ready() {
  local receipt
  for table in "${TABLES[@]}"; do
    if ! table_exists "$SOURCE_SCHEMA" "$table"; then
      echo "Source table missing: $SOURCE_SCHEMA.$table" >&2
      return 1
    fi
    if ! table_exists "$TARGET_SCHEMA" "$table"; then
      echo "Target table missing: $TARGET_SCHEMA.$table; run MIGRATION_SCHEMA=notification ./scripts/dev/migrate.sh migrate first" >&2
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
  receipt="$(receipt_source_table)"
  if [[ -z "$receipt" ]]; then
    echo "Source command receipt table missing; deploy the additive receipt migration first." >&2
    return 1
  fi
  if [[ "$(column_signature "$SOURCE_SCHEMA" "$receipt")" != "$(column_signature "$TARGET_SCHEMA" notification_command_receipt)" ]]; then
    echo "Column shape mismatch for command receipt source $receipt" >&2
    return 1
  fi
}

require_execute() {
  local expected="$1"
  if ! require_write_confirmation "$EXECUTE" NOTIFICATION_CUTOVER_CONFIRM "$expected"; then
    echo "Refusing write action. Pass --execute and NOTIFICATION_CUTOVER_CONFIRM=$expected." >&2
    exit 1
  fi
}

copy_forward() {
  for table in "${TABLES[@]}"; do
    if [[ "$table" == "consumer_inbox" ]]; then
      mysql_query "INSERT INTO \`$TARGET_SCHEMA\`.\`consumer_inbox\` SELECT * FROM \`$SOURCE_SCHEMA\`.\`consumer_inbox\` WHERE consumer = 'App-Notification';"
    else
      mysql_query "INSERT INTO \`$TARGET_SCHEMA\`.\`$table\` SELECT * FROM \`$SOURCE_SCHEMA\`.\`$table\`;"
    fi
  done
  local receipt
  receipt="$(receipt_source_table)"
  if [[ "$receipt" == "notification_command_receipt" ]]; then
    # Compat-mode table exists: copy it fully, then also carry over legacy
    # App-side receipts for the notification service that were written to
    # app_command_receipt before the cutover. INSERT IGNORE keeps the fresher
    # compat row when the same (service, operation, idempotency_key) exists in
    # both tables.
    mysql_query "INSERT IGNORE INTO \`$TARGET_SCHEMA\`.\`notification_command_receipt\` SELECT * FROM \`$SOURCE_SCHEMA\`.\`$receipt\`;"
    if table_exists "$SOURCE_SCHEMA" app_command_receipt; then
      mysql_query "INSERT IGNORE INTO \`$TARGET_SCHEMA\`.\`notification_command_receipt\` SELECT * FROM \`$SOURCE_SCHEMA\`.\`app_command_receipt\` WHERE service = 'NotificationAdministrationService';"
    fi
  else
    mysql_query "INSERT INTO \`$TARGET_SCHEMA\`.\`notification_command_receipt\` SELECT * FROM \`$SOURCE_SCHEMA\`.\`$receipt\` WHERE service = 'NotificationAdministrationService';"
  fi
}

copy_back() {
  for table in "${TABLES[@]}"; do
    if [[ "$table" == "consumer_inbox" ]]; then
      mysql_query "REPLACE INTO \`$SOURCE_SCHEMA\`.\`consumer_inbox\` SELECT * FROM \`$TARGET_SCHEMA\`.\`consumer_inbox\` WHERE consumer = 'App-Notification';"
    else
      mysql_query "REPLACE INTO \`$SOURCE_SCHEMA\`.\`$table\` SELECT * FROM \`$TARGET_SCHEMA\`.\`$table\`;"
    fi
  done
  local receipt
  receipt="$(receipt_source_table)"
  if [[ "$receipt" == "notification_command_receipt" ]]; then
    mysql_query "REPLACE INTO \`$SOURCE_SCHEMA\`.\`$receipt\` SELECT * FROM \`$TARGET_SCHEMA\`.\`notification_command_receipt\`;"
  else
    mysql_query "REPLACE INTO \`$SOURCE_SCHEMA\`.\`$receipt\` SELECT * FROM \`$TARGET_SCHEMA\`.\`notification_command_receipt\`;"
  fi
}

restore_app_grants() {
  if [[ -z "$APP_DB_USER" ]]; then
    echo "NOTIFICATION_APP_DB_USER is required to restore App table grants." >&2
    return 1
  fi
  for table in "${TABLES[@]}"; do
    mysql_query "GRANT SELECT, INSERT, UPDATE, DELETE ON \`$SOURCE_SCHEMA\`.\`$table\` TO '$APP_DB_USER'@'%';"
  done
  mysql_query "GRANT SELECT, INSERT, UPDATE, DELETE ON \`$SOURCE_SCHEMA\`.\`app_command_receipt\` TO '$APP_DB_USER'@'%';" || true
  mysql_query "FLUSH PRIVILEGES;"
}

echo "Notification schema=$SOURCE_SCHEMA -> $TARGET_SCHEMA"

case "$ACTION" in
  preflight)
    print_snapshot "$SOURCE_SCHEMA" source
    print_snapshot "$TARGET_SCHEMA" target
    if [[ "$SOURCE_SCHEMA" != "$TARGET_SCHEMA" ]]; then
      assert_ready
      echo "PRECHECK: source/target table shapes and empty target verified."
    else
      echo "PRECHECK: compatibility mode; no physical move requested."
    fi
    ;;
  cutover)
    require_execute I_UNDERSTAND_NOTIFICATION_CUTOVER
    if [[ "$SOURCE_SCHEMA" == "$TARGET_SCHEMA" ]]; then
      echo "Source and target are identical; refusing a no-op cutover." >&2
      exit 1
    fi
    if [[ -z "$APP_DB_USER" ]]; then
      echo "Cutover requires NOTIFICATION_APP_DB_USER=... so App grants on the notification-only tables can be revoked safely." >&2
      exit 1
    fi
    assert_ready
    copy_forward
    for table in "${NOTIFICATION_ONLY_TABLES[@]}"; do
      mysql_query "REVOKE SELECT, INSERT, UPDATE, DELETE ON \`$SOURCE_SCHEMA\`.\`$table\` FROM '$APP_DB_USER'@'%';"
    done
    mysql_query "FLUSH PRIVILEGES;"
    print_snapshot "$SOURCE_SCHEMA" source-after-cutover
    print_snapshot "$TARGET_SCHEMA" target-after-cutover
    ;;
  rollback)
    require_execute I_UNDERSTAND_NOTIFICATION_ROLLBACK
    if [[ "$SOURCE_SCHEMA" == "$TARGET_SCHEMA" ]]; then
      echo "Source and target are identical; no rollback required." >&2
      exit 1
    fi
    if [[ -z "$APP_DB_USER" ]]; then
      echo "Rollback requires NOTIFICATION_APP_DB_USER=... (the same app-schema user used for cutover)." >&2
      exit 1
    fi
    copy_back
    restore_app_grants
    print_snapshot "$SOURCE_SCHEMA" source-after-rollback
    print_snapshot "$TARGET_SCHEMA" target-after-rollback
    ;;
esac
