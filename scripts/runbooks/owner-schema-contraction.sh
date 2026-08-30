#!/usr/bin/env bash
set -euo pipefail

# P1-DATA-001: verify both owner cutovers, record a proof snapshot, then invoke
# the separate contraction Flyway history. The default action is read-only.
# Dropped legacy tables are recoverable only from the verified backup required
# by the migration window; this runbook never invents an in-place rollback.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
ACTION="${1:-preflight}"
EXECUTE="${2:-}"

case "$ACTION" in
  preflight|contract) ;;
  *)
    echo "Usage: $0 [preflight|contract] [--execute]" >&2
    exit 2
    ;;
esac

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"

capture_env_vars MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
  MIGRATION_DB_USER MIGRATION_DB_PASSWORD MIGRATION_MYSQL_CONTAINER \
  MIGRATION_MYSQL_CONTAINER_PORT OWNER_CONTRACTION_APP_USER OWNER_CONTRACTION_APP_HOST \
  OWNER_SCHEMA_CONTRACTION_BACKUP_CONFIRM OWNER_SCHEMA_CONTRACTION_QUIESCE_CONFIRM \
  OWNER_SCHEMA_CONTRACTION_BACKUP_REFERENCE
load_env_file
apply_env_overrides

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:?DB_PORT is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

SOURCE_SCHEMA="$DB_NAME"
TARGET_SUBMISSION_SCHEMA="submission"
TARGET_NOTIFICATION_SCHEMA="notification"
APP_DB_USER="${OWNER_CONTRACTION_APP_USER:-${SUBMISSION_APP_DB_USER:-${APP_DB_USER:-app_rw}}}"
APP_DB_HOST="${OWNER_CONTRACTION_APP_HOST:-${SUBMISSION_APP_DB_HOST:-%}}"
MIGRATION_USER="${MIGRATION_DB_USER:-$DB_USER}"
MIGRATION_PASSWORD="${MIGRATION_DB_PASSWORD:-$DB_PASSWORD}"
MIGRATION_HOST="${MIGRATION_DB_HOST:-$DB_HOST}"
MIGRATION_PORT="${MIGRATION_DB_PORT:-$DB_PORT}"
MYSQL_CONTAINER="${MIGRATION_MYSQL_CONTAINER:-}"
MYSQL_CONTAINER_PORT="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"

[[ "$SOURCE_SCHEMA" == "ulticode" ]] \
  || { echo "Owner contraction requires DB_NAME=ulticode; got $SOURCE_SCHEMA" >&2; exit 1; }
valid_identifier "$APP_DB_USER" || { echo "Invalid App account: $APP_DB_USER" >&2; exit 1; }
[[ "$APP_DB_HOST" =~ ^[A-Za-z0-9%._:-]+$ ]] \
  || { echo "Invalid App account host: $APP_DB_HOST" >&2; exit 1; }
valid_identifier "$MIGRATION_USER" || { echo "Invalid migration account: $MIGRATION_USER" >&2; exit 1; }
valid_port "$MIGRATION_PORT" || { echo "Invalid migration database port: $MIGRATION_PORT" >&2; exit 1; }
if [[ -n "$MYSQL_CONTAINER" ]]; then
  valid_container_ref "$MYSQL_CONTAINER" || {
    echo "Invalid migration MySQL container: $MYSQL_CONTAINER" >&2
    exit 1
  }
  valid_port "$MYSQL_CONTAINER_PORT" || {
    echo "Invalid migration MySQL container port: $MYSQL_CONTAINER_PORT" >&2
    exit 1
  }
  container_running "$MYSQL_CONTAINER" || {
    echo "Migration MySQL container is not running: $MYSQL_CONTAINER" >&2
    exit 1
  }
  mysql_container_targets_configured_host "$MYSQL_CONTAINER" "$MYSQL_CONTAINER_PORT" \
    "$MIGRATION_HOST" "$MIGRATION_PORT" || {
      echo "Configured database target $MIGRATION_HOST:$MIGRATION_PORT is not a published endpoint of $MYSQL_CONTAINER:$MYSQL_CONTAINER_PORT" >&2
      exit 1
    }
fi

define_mysql_query_adapter mysql_query \
  "$MYSQL_CONTAINER" "$MYSQL_CONTAINER_PORT" \
  "$MIGRATION_HOST" "$MIGRATION_PORT" \
  "$MIGRATION_USER" "$MIGRATION_PASSWORD" \
  "" \
  --default-character-set=utf8mb4 --batch --skip-column-names

SUBMISSION_TABLES=(submissions judge_outbox submission_result_outbox)
NOTIFICATION_TABLES=(notifications notification_preferences notification_delivery_ledger email_templates email_logs)
if table_exists "$SOURCE_SCHEMA" notification_command_receipt; then
  NOTIFICATION_TABLES+=(notification_command_receipt)
fi
LEGACY_TABLES=("${SUBMISSION_TABLES[@]}" "${NOTIFICATION_TABLES[@]}")

grantee_expression() {
  printf "CONCAT(CHAR(39), '%s', CHAR(39), '@', CHAR(39), '%s', CHAR(39))" \
    "$APP_DB_USER" "$APP_DB_HOST"
}

app_dml_grants() {
  local grantee
  grantee="$(grantee_expression)"
  mysql_query "SELECT (
      SELECT COUNT(*) FROM information_schema.table_privileges
      WHERE GRANTEE = $grantee AND TABLE_SCHEMA = '$SOURCE_SCHEMA'
        AND TABLE_NAME IN ('submissions','judge_outbox','submission_result_outbox',
                           'notifications','notification_preferences',
                           'notification_delivery_ledger','email_templates','email_logs',
                           'notification_command_receipt')
        AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE','GRANT OPTION')
    ) + (
      SELECT COUNT(*) FROM information_schema.column_privileges
      WHERE GRANTEE = $grantee AND TABLE_SCHEMA = '$SOURCE_SCHEMA'
    ) + (
      SELECT COUNT(*) FROM information_schema.schema_privileges
      WHERE GRANTEE = $grantee AND TABLE_SCHEMA = '$SOURCE_SCHEMA'
        AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE','GRANT OPTION')
    ) + (
      SELECT COUNT(*) FROM information_schema.user_privileges
      WHERE GRANTEE = $grantee
        AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE','ALL PRIVILEGES','GRANT OPTION')
    );"
}

app_non_table_dml_grants() {
  local grantee
  grantee="$(grantee_expression)"
  mysql_query "SELECT (
      SELECT COUNT(*) FROM information_schema.column_privileges
      WHERE GRANTEE = $grantee AND TABLE_SCHEMA = '$SOURCE_SCHEMA'
    ) + (
      SELECT COUNT(*) FROM information_schema.schema_privileges
      WHERE GRANTEE = $grantee AND TABLE_SCHEMA = '$SOURCE_SCHEMA'
        AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE','GRANT OPTION')
    ) + (
      SELECT COUNT(*) FROM information_schema.user_privileges
      WHERE GRANTEE = $grantee
        AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE','ALL PRIVILEGES','GRANT OPTION')
    );"
}

revoke_app_legacy_table_grants() {
  local table table_grants grant_option
  for table in "${LEGACY_TABLES[@]}"; do
    table_grants="$(mysql_query "SELECT COUNT(*) FROM information_schema.table_privileges
      WHERE GRANTEE = $(grantee_expression) AND TABLE_SCHEMA = '$SOURCE_SCHEMA'
        AND TABLE_NAME = '$table'
        AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE','GRANT OPTION');")"
    if [[ "$table_grants" != "0" ]]; then
      mysql_query "REVOKE ALL PRIVILEGES ON \`$SOURCE_SCHEMA\`.\`$table\` FROM '$APP_DB_USER'@'$APP_DB_HOST';"
      grant_option="$(mysql_query "SELECT COUNT(*) FROM information_schema.table_privileges
        WHERE GRANTEE = $(grantee_expression) AND TABLE_SCHEMA = '$SOURCE_SCHEMA'
          AND TABLE_NAME = '$table' AND PRIVILEGE_TYPE = 'GRANT OPTION';")"
      if [[ "$grant_option" != "0" ]]; then
        mysql_query "REVOKE GRANT OPTION ON \`$SOURCE_SCHEMA\`.\`$table\` FROM '$APP_DB_USER'@'$APP_DB_HOST';"
      fi
      printf 'REVOKED_APP_TABLE_GRANT=%s.%s\n' "$SOURCE_SCHEMA" "$table"
    fi
  done
}

table_snapshot() {
  local schema="$1" table="$2"
  if ! table_exists "$schema" "$table"; then
    printf '%s:MISSING\n' "$table"
    return 0
  fi
  printf '%s:%s:%s\n' "$table" "$(row_count "$schema" "$table")" \
    "$(checksum_table "$schema" "$table")"
}

snapshot_for() {
  local schema="$1" tables_name="$2" table
  local -n tables="$tables_name"
  for table in "${tables[@]}"; do
    table_snapshot "$schema" "$table"
  done
}

sum_rows() {
  local schema="$1" tables_name="$2" table total=0 rows
  local -n tables="$tables_name"
  for table in "${tables[@]}"; do
    if table_exists "$schema" "$table"; then
      rows="$(row_count "$schema" "$table")"
      [[ "$rows" =~ ^[0-9]+$ ]] || return 1
      total=$((total + rows))
    fi
  done
  printf '%s\n' "$total"
}

verify_owner() {
  local owner="$1" target_schema="$2" tables_name="$3" table source_rows target_rows
  local -n tables="$tables_name"
  for table in "${tables[@]}"; do
    table_exists "$target_schema" "$table" || {
      echo "Target table missing: $target_schema.$table" >&2
      return 1
    }
    table_exists "$SOURCE_SCHEMA" "$table" || {
      echo "Source table missing: $SOURCE_SCHEMA.$table" >&2
      return 1
    }
    [[ "$(column_signature "$SOURCE_SCHEMA" "$table")" == \
       "$(column_signature "$target_schema" "$table")" ]] || {
      echo "Column shape mismatch: $SOURCE_SCHEMA.$table vs $target_schema.$table" >&2
      return 1
    }
    source_rows="$(row_count "$SOURCE_SCHEMA" "$table")"
    target_rows="$(row_count "$target_schema" "$table")"
    [[ "$source_rows" == "$target_rows" ]] || {
      echo "Row-count mismatch for $owner.$table: source=$source_rows target=$target_rows" >&2
      return 1
    }
    [[ "$(checksum_table "$SOURCE_SCHEMA" "$table")" == \
       "$(checksum_table "$target_schema" "$table")" ]] || {
      echo "Checksum mismatch for $owner.$table" >&2
      return 1
    }
  done
}

verify_all() {
  local grants
  verify_owner Submission "$TARGET_SUBMISSION_SCHEMA" SUBMISSION_TABLES || return 1
  verify_owner Notification "$TARGET_NOTIFICATION_SCHEMA" NOTIFICATION_TABLES || return 1
  grants="$(app_dml_grants)"
  [[ "$grants" == "0" ]] || {
    echo "App legacy-table privileges remain: $grants" >&2
    return 1
  }
}

record_proof() {
  local owner="$1" target_schema="$2" tables_name="$3" source_snapshot target_snapshot \
    snapshot_hash source_rows target_rows account
  source_snapshot="$(snapshot_for "$SOURCE_SCHEMA" "$tables_name")"
  target_snapshot="$(snapshot_for "$target_schema" "$tables_name")"
  snapshot_hash="$(printf '%s\n--TARGET--\n%s' "$source_snapshot" "$target_snapshot" \
    | sha256sum | awk '{print $1}')"
  source_rows="$(sum_rows "$SOURCE_SCHEMA" "$tables_name")"
  target_rows="$(sum_rows "$target_schema" "$tables_name")"
  account="$APP_DB_USER@$APP_DB_HOST"
    mysql_query "INSERT INTO $SOURCE_SCHEMA.owner_contraction_proof
      (owner, source_schema, target_schema, source_rows, target_rows,
       snapshot_hash, app_account, app_dml_grants, backup_reference,
       backup_verified_at, writers_quiesced_at, verified_at, verified_by)
    VALUES ('$owner', '$SOURCE_SCHEMA', '$target_schema', $source_rows, $target_rows,
            '$snapshot_hash', '$account', 0, '$BACKUP_REFERENCE',
            CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_USER())
    ON DUPLICATE KEY UPDATE
      source_schema = VALUES(source_schema),
      target_schema = VALUES(target_schema),
      source_rows = VALUES(source_rows),
      target_rows = VALUES(target_rows),
      snapshot_hash = VALUES(snapshot_hash),
      app_account = VALUES(app_account),
      app_dml_grants = VALUES(app_dml_grants),
      backup_reference = VALUES(backup_reference),
      backup_verified_at = VALUES(backup_verified_at),
      writers_quiesced_at = VALUES(writers_quiesced_at),
      verified_at = VALUES(verified_at),
      verified_by = VALUES(verified_by);"
  printf 'PROOF owner=%s source=%s target=%s rows=%s hash=%s\n' \
    "$owner" "$source_rows" "$target_rows" "$target_rows" "$snapshot_hash"
}

print_snapshots() {
  echo "[source] schema=$SOURCE_SCHEMA"
  snapshot_for "$SOURCE_SCHEMA" SUBMISSION_TABLES
  snapshot_for "$SOURCE_SCHEMA" NOTIFICATION_TABLES
  echo "[submission-owner] schema=$TARGET_SUBMISSION_SCHEMA"
  snapshot_for "$TARGET_SUBMISSION_SCHEMA" SUBMISSION_TABLES
  echo "[notification-owner] schema=$TARGET_NOTIFICATION_SCHEMA"
  snapshot_for "$TARGET_NOTIFICATION_SCHEMA" NOTIFICATION_TABLES
  printf 'APP_LEGACY_DML_GRANTS=%s\n' "$(app_dml_grants)"
}

echo "Owner schema contraction source=$SOURCE_SCHEMA targets=$TARGET_SUBMISSION_SCHEMA,$TARGET_NOTIFICATION_SCHEMA"
case "$ACTION" in
  preflight)
    print_snapshots
    verify_all
    echo "PRECHECK PASS: owner parity, App grant absence, and contraction proof prerequisites verified."
    ;;
  contract)
    require_write_confirmation "$EXECUTE" OWNER_SCHEMA_CONTRACTION_CONFIRM \
      I_UNDERSTAND_OWNER_SCHEMA_CONTRACTION || {
        echo "Refusing write action. Pass --execute and OWNER_SCHEMA_CONTRACTION_CONFIRM=I_UNDERSTAND_OWNER_SCHEMA_CONTRACTION." >&2
        exit 1
      }
    require_write_confirmation "$EXECUTE" OWNER_SCHEMA_CONTRACTION_BACKUP_CONFIRM \
      I_HAVE_VERIFIED_OWNER_CONTRACTION_BACKUP || {
        echo "Refusing write action. Pass OWNER_SCHEMA_CONTRACTION_BACKUP_CONFIRM=I_HAVE_VERIFIED_OWNER_CONTRACTION_BACKUP." >&2
        exit 1
      }
    require_write_confirmation "$EXECUTE" OWNER_SCHEMA_CONTRACTION_QUIESCE_CONFIRM \
      I_HAVE_QUIESCED_OWNER_WRITERS || {
        echo "Refusing write action. Pass OWNER_SCHEMA_CONTRACTION_QUIESCE_CONFIRM=I_HAVE_QUIESCED_OWNER_WRITERS." >&2
        exit 1
      }
    BACKUP_REFERENCE="${OWNER_SCHEMA_CONTRACTION_BACKUP_REFERENCE:-}"
    [[ "$BACKUP_REFERENCE" =~ ^[A-Za-z0-9._:/-]{1,255}$ ]] || {
      echo "Refusing write action. OWNER_SCHEMA_CONTRACTION_BACKUP_REFERENCE must be a non-empty safe reference." >&2
      exit 1
    }
    table_exists "$SOURCE_SCHEMA" owner_contraction_proof || {
      echo "Proof table missing: run the shared migration chain before contraction." >&2
      exit 1
    }
    verify_owner Submission "$TARGET_SUBMISSION_SCHEMA" SUBMISSION_TABLES || {
      echo "Refusing contraction: Submission owner parity is not green." >&2
      exit 1
    }
    verify_owner Notification "$TARGET_NOTIFICATION_SCHEMA" NOTIFICATION_TABLES || {
      echo "Refusing contraction: Notification owner parity is not green." >&2
      exit 1
    }
    non_table_grants="$(app_non_table_dml_grants)"
    [[ "$non_table_grants" == "0" ]] || {
      echo "Refusing contraction: non-table App privileges remain: $non_table_grants" >&2
      exit 1
    }
    revoke_app_legacy_table_grants
    verify_all || {
      echo "Refusing contraction: owner parity or remaining App privileges are not green." >&2
      exit 1
    }
    record_proof Submission "$TARGET_SUBMISSION_SCHEMA" SUBMISSION_TABLES
    record_proof Notification "$TARGET_NOTIFICATION_SCHEMA" NOTIFICATION_TABLES
    ENV_FILE="$ENV_FILE" \
      MIGRATION_DB_HOST="$MIGRATION_HOST" \
      MIGRATION_DB_PORT="$MIGRATION_PORT" \
      MIGRATION_DB_NAME="$SOURCE_SCHEMA" \
      MIGRATION_DB_USER="$MIGRATION_USER" \
      MIGRATION_DB_PASSWORD="$MIGRATION_PASSWORD" \
      MIGRATION_MYSQL_CONTAINER="$MYSQL_CONTAINER" \
      MIGRATION_MYSQL_CONTAINER_PORT="$MYSQL_CONTAINER_PORT" \
      MAVEN_BIN="$ROOT_DIR/services/mvnw" \
      MAVEN_POM="$ROOT_DIR/init-db/pom.xml" \
      OWNER_SCHEMA_CONTRACTION_CONFIRM=I_UNDERSTAND_OWNER_SCHEMA_CONTRACTION \
      OWNER_SCHEMA_CONTRACTION_BACKUP_CONFIRM=I_HAVE_VERIFIED_OWNER_CONTRACTION_BACKUP \
      OWNER_SCHEMA_CONTRACTION_QUIESCE_CONFIRM=I_HAVE_QUIESCED_OWNER_WRITERS \
      OWNER_SCHEMA_CONTRACTION_BACKUP_REFERENCE="$BACKUP_REFERENCE" \
      OWNER_CONTRACTION_APP_USER="$APP_DB_USER" \
      OWNER_CONTRACTION_APP_HOST="$APP_DB_HOST" \
      bash "$ROOT_DIR/scripts/dev/migrate.sh" contract
    echo "CONTRACT PASS: legacy Submission/Notification tables and App grants were retired after verified proof."
    ;;
esac
