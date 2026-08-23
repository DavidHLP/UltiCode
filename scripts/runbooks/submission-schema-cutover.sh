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
# Gate: this script copies data and revokes App write grants. Before cutover
# or rollback, stop and drain every process that can write either schema:
# backend-app/App PM2 (submission intake, contest/rejudge paths, local
# outbox dispatchers and lease reapers), backend-submission (owner writer,
# dispatcher and reaper), backend-judge (legacy or remote verdict/lease
# writes), and any direct admin/maintenance client. Pass the one-time
# confirmation only after all in-flight work is drained; source rows/checksums
# are rechecked before REVOKE.
#
# The actual runtime cutover (APP_SUBMISSION_ROUTING_MODE=remote) must only be
# enabled after the SPLIT-004 read-path migration. See the migration guide §8.

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

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"

load_env_file

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:?DB_PORT is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

SOURCE_SCHEMA="${SUBMISSION_SOURCE_SCHEMA:-$DB_NAME}"
TARGET_SCHEMA="${SUBMISSION_DB_NAME:-submission}"
MIGRATION_USER="${MIGRATION_DB_USER:-$DB_USER}"
MIGRATION_PASSWORD="${MIGRATION_DB_PASSWORD:-$DB_PASSWORD}"
# The generated development env already identifies the App runtime account;
# explicit cutover variables remain the override for a non-default account.
APP_DB_USER="${SUBMISSION_APP_DB_USER:-${APP_DB_USER:-}}"
APP_DB_HOST="${SUBMISSION_APP_DB_HOST:-%}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-${MIGRATION_MYSQL_CONTAINER:-}}"

for identifier in "$SOURCE_SCHEMA" "$TARGET_SCHEMA" "$MIGRATION_USER" "$APP_DB_USER"; do
  if [[ -n "$identifier" ]] && ! valid_identifier "$identifier"; then
    echo "Invalid schema/user identifier: $identifier" >&2
    exit 1
  fi
done
if [[ -n "$APP_DB_HOST" && ! "$APP_DB_HOST" =~ ^[A-Za-z0-9%._:-]+$ ]]; then
  echo "Invalid App account host: $APP_DB_HOST" >&2
  exit 1
fi
if [[ -n "$MYSQL_CONTAINER" ]]; then
  command -v docker >/dev/null 2>&1 || { echo "docker CLI is required when MYSQL_CONTAINER is set." >&2; exit 1; }
  container_running "$MYSQL_CONTAINER" \
    || { echo "MySQL container is not running: $MYSQL_CONTAINER" >&2; exit 1; }
  mysql_container_targets_configured_host "$MYSQL_CONTAINER" "${MIGRATION_MYSQL_CONTAINER_PORT:-3306}" "$DB_HOST" "$DB_PORT" \
    || { echo "Configured database target $DB_HOST:$DB_PORT is not a published endpoint of $MYSQL_CONTAINER:${MIGRATION_MYSQL_CONTAINER_PORT:-3306}" >&2; exit 1; }
fi

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

# Single-sourced connection adapter (scripts/dev/lib/sql.sh). In-container
# queries keep the socket transport unless MIGRATION_MYSQL_CONTAINER_PORT is
# explicitly set, matching the original runbook behaviour.
define_mysql_query_adapter mysql_query \
  "${MYSQL_CONTAINER:-}" "${MIGRATION_MYSQL_CONTAINER_PORT:-}" \
  "$DB_HOST" "$DB_PORT" \
  "$MIGRATION_USER" "$MIGRATION_PASSWORD" \
  "" \
  --default-character-set=utf8mb4 --batch --skip-column-names

# table_exists/column_signature/row_count/checksum_table come from
# scripts/dev/lib/common.sh (shared strict primitives over mysql_query).

source_snapshot() {
  local schema="$1" table rows table_checksum
  for table in "${TABLES[@]}"; do
    if ! rows="$(row_count "$schema" "$table")"; then
      return 1
    fi
    if ! table_checksum="$(checksum_table "$schema" "$table")"; then
      return 1
    fi
    printf '%s\t%s\t%s\n' "$table" "$rows" "$table_checksum"
  done
}

print_snapshot() {
  local schema="$1" label="$2"
  echo "[$label] schema=$schema"
  for table in "${TABLES[@]}"; do
    if table_exists "$schema" "$table"; then
      echo "  $table rows=$(row_count "$schema" "$table") checksum=$(checksum_table "$schema" "$table")"
    else
      echo "  $table MISSING"
    fi
  done
  for table in "${TARGET_ONLY_TABLES[@]}"; do
    if table_exists "$schema" "$table"; then
      echo "  $table (target-only) rows=$(row_count "$schema" "$table") checksum=$(checksum_table "$schema" "$table")"
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
  if ! require_write_confirmation "$EXECUTE" SUBMISSION_CUTOVER_CONFIRM "$expected"; then
    echo "Refusing write action. Pass --execute and SUBMISSION_CUTOVER_CONFIRM=$expected." >&2
    exit 1
  fi
}

require_quiesce() {
  local expected="$1"
  if ! gate_confirmed SUBMISSION_CUTOVER_QUIESCE_CONFIRM "$expected"; then
    echo "Refusing write action. Stop and drain backend-app/App PM2, backend-submission, backend-judge, and every direct writer or maintenance client for both schemas; then pass SUBMISSION_CUTOVER_QUIESCE_CONFIRM=$expected." >&2
    exit 1
  fi
}

copy_forward() {
  for table in "${TABLES[@]}"; do
    if ! mysql_query "INSERT INTO \`$TARGET_SCHEMA\`.\`$table\` SELECT * FROM \`$SOURCE_SCHEMA\`.\`$table\`;"; then
      echo "Copy failed for $SOURCE_SCHEMA.$table -> $TARGET_SCHEMA.$table." >&2
      return 1
    fi
  done
}

copy_back() {
  local query="START TRANSACTION;"
  local table
  for table in "${TABLES[@]}"; do
    query+=" REPLACE INTO \`$SOURCE_SCHEMA\`.\`$table\` SELECT * FROM \`$TARGET_SCHEMA\`.\`$table\`;"
  done
  query+=" COMMIT;"
  if ! mysql_query "$query"; then
    echo "Atomic rollback copy failed; the MySQL connection should roll back the transaction." >&2
    return 1
  fi
}

revoke_app_grants() {
  if [[ -z "$APP_DB_USER" || -z "$APP_DB_HOST" ]]; then
    echo "SUBMISSION_APP_DB_USER and SUBMISSION_APP_DB_HOST are required to revoke App grants." >&2
    return 1
  fi
  for table in "${TABLES[@]}"; do
    if ! mysql_query "REVOKE SELECT, INSERT, UPDATE, DELETE ON \`$SOURCE_SCHEMA\`.\`$table\` FROM '$APP_DB_USER'@'$APP_DB_HOST';"; then
      echo "Grant revocation failed for $SOURCE_SCHEMA.$table from '$APP_DB_USER'@'$APP_DB_HOST'." >&2
      return 1
    fi
  done
  if ! mysql_query "FLUSH PRIVILEGES;"; then
    echo "FLUSH PRIVILEGES failed after App grant revocation." >&2
    return 1
  fi
}

restore_app_grants() {
  if [[ -z "$APP_DB_USER" || -z "$APP_DB_HOST" ]]; then
    echo "SUBMISSION_APP_DB_USER and SUBMISSION_APP_DB_HOST are required to restore App grants." >&2
    return 1
  fi
  for table in "${TABLES[@]}"; do
    if ! mysql_query "GRANT SELECT, INSERT, UPDATE, DELETE ON \`$SOURCE_SCHEMA\`.\`$table\` TO '$APP_DB_USER'@'$APP_DB_HOST';"; then
      echo "Grant restore failed for $SOURCE_SCHEMA.$table to '$APP_DB_USER'@'$APP_DB_HOST'." >&2
      return 1
    fi
  done
  if ! mysql_query "FLUSH PRIVILEGES;"; then
    echo "FLUSH PRIVILEGES failed after App grant restore." >&2
    return 1
  fi
}

# Resolve the actual account and every role it inherits. The cutover only
# revokes grants from the exact runtime account, so ambiguity or any role edge
# must fail closed before copy; otherwise a role could remain a second writer.
role_principals_cte() {
  cat <<SQL
WITH RECURSIVE principals (principal_user, principal_host) AS (
  SELECT CAST('$APP_DB_USER' AS CHAR(255)), CAST('$APP_DB_HOST' AS CHAR(255))
  UNION DISTINCT
  SELECT CAST(edge.FROM_USER AS CHAR(255)), CAST(edge.FROM_HOST AS CHAR(255))
  FROM mysql.role_edges AS edge
  JOIN principals AS parent
    ON edge.TO_USER = parent.principal_user
   AND edge.TO_HOST = parent.principal_host
)
SQL
}

app_user_role_grant_exists() {
  local count
  if ! count="$(mysql_query "$(role_principals_cte)
      SELECT COUNT(*) - 1 FROM principals;")"; then
    return 2
  fi
  [[ "$count" =~ ^[0-9]+$ ]] || return 2
  [[ "$count" -gt 0 ]]
}

app_user_global_dml_grant_exists() {
  local mysql_count user_privilege_count
  if ! mysql_count="$(mysql_query "$(role_principals_cte)
      SELECT COUNT(*)
      FROM mysql.user AS account
      JOIN principals AS principal
        ON account.User = principal.principal_user
       AND account.Host = principal.principal_host
      WHERE account.Select_priv = 'Y'
         OR account.Insert_priv = 'Y'
         OR account.Update_priv = 'Y'
         OR account.Delete_priv = 'Y'
         OR account.Grant_priv = 'Y';")"; then
    return 2
  fi
  [[ "$mysql_count" =~ ^[0-9]+$ ]] || return 2
  if [[ "$mysql_count" -gt 0 ]]; then
    return 0
  fi
  if ! user_privilege_count="$(mysql_query "$(role_principals_cte)
      SELECT COUNT(*)
      FROM information_schema.USER_PRIVILEGES AS privilege
      JOIN principals AS principal
        ON privilege.GRANTEE = CONCAT(CHAR(39), principal.principal_user,
                                      CHAR(39), '@', CHAR(39), principal.principal_host, CHAR(39))
      WHERE privilege.PRIVILEGE_TYPE IN
        ('SELECT','INSERT','UPDATE','DELETE','ALL PRIVILEGES','GRANT OPTION')
         OR privilege.IS_GRANTABLE <> 'NO';")"; then
    return 2
  fi
  [[ "$user_privilege_count" =~ ^[0-9]+$ ]] || return 2
  [[ "$user_privilege_count" -gt 0 ]]
}

app_user_schema_dml_grant_exists() {
  local schema="$1" count
  if ! count="$(mysql_query "$(role_principals_cte)
      SELECT COUNT(*)
      FROM information_schema.SCHEMA_PRIVILEGES AS privilege
      JOIN principals AS principal
        ON privilege.GRANTEE = CONCAT(CHAR(39), principal.principal_user,
                                      CHAR(39), '@', CHAR(39), principal.principal_host, CHAR(39))
      WHERE privilege.TABLE_SCHEMA = '$schema'
        AND (privilege.PRIVILEGE_TYPE IN
          ('SELECT','INSERT','UPDATE','DELETE','ALL PRIVILEGES','GRANT OPTION')
          OR privilege.IS_GRANTABLE <> 'NO');")"; then
    return 2
  fi
  [[ "$count" =~ ^[0-9]+$ ]] || return 2
  [[ "$count" -gt 0 ]]
}

app_user_table_grant_exists() {
  local schema="$1" table="$2" count
  if ! count="$(mysql_query "SELECT COUNT(DISTINCT PRIVILEGE_TYPE)
      FROM information_schema.table_privileges
      WHERE GRANTEE = CONCAT(CHAR(39), '$APP_DB_USER', CHAR(39), '@', CHAR(39), '$APP_DB_HOST', CHAR(39))
        AND TABLE_SCHEMA = '$schema' AND TABLE_NAME = '$table'
        AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE');")"; then
    return 2
  fi
  [[ "$count" =~ ^[0-9]+$ ]] || return 2
  [[ "$count" == "4" ]]
}

app_user_table_unsafe_grant_exists() {
  local schema="$1" table="$2" count
  if ! count="$(mysql_query "SELECT COUNT(*)
      FROM information_schema.table_privileges
      WHERE GRANTEE = CONCAT(CHAR(39), '$APP_DB_USER', CHAR(39), '@', CHAR(39), '$APP_DB_HOST', CHAR(39))
        AND TABLE_SCHEMA = '$schema' AND TABLE_NAME = '$table'
        AND (PRIVILEGE_TYPE NOT IN ('SELECT','INSERT','UPDATE','DELETE')
             OR IS_GRANTABLE <> 'NO');")"; then
    return 2
  fi
  [[ "$count" =~ ^[0-9]+$ ]] || return 2
  [[ "$count" -gt 0 ]]
}

app_user_column_grant_exists() {
  local schema="$1" table="$2" count
  if ! count="$(mysql_query "SELECT COUNT(*)
      FROM information_schema.COLUMN_PRIVILEGES
      WHERE GRANTEE = CONCAT(CHAR(39), '$APP_DB_USER', CHAR(39), '@', CHAR(39), '$APP_DB_HOST', CHAR(39))
        AND TABLE_SCHEMA = '$schema' AND TABLE_NAME = '$table';")"; then
    return 2
  fi
  [[ "$count" =~ ^[0-9]+$ ]] || return 2
  [[ "$count" -gt 0 ]]
}

assert_revoke_ready() {
  if [[ -z "$APP_DB_USER" || -z "$APP_DB_HOST" ]]; then
    echo "SUBMISSION_APP_DB_USER and SUBMISSION_APP_DB_HOST are required to verify App grants." >&2
    return 1
  fi
  local account_count exact_account_count check_status
  account_count="$(mysql_query "SELECT COUNT(*) FROM mysql.user WHERE User = '$APP_DB_USER';")"
  if [[ "$account_count" != "1" ]]; then
    echo "App user '$APP_DB_USER' has $account_count MySQL account hosts; provide one exact runtime account host and remove ambiguity before cutover." >&2
    return 1
  fi
  exact_account_count="$(mysql_query "SELECT COUNT(*) FROM mysql.user WHERE User = '$APP_DB_USER' AND Host = '$APP_DB_HOST';")"
  if [[ "$exact_account_count" != "1" ]]; then
    echo "App runtime account '$APP_DB_USER'@'$APP_DB_HOST' does not exist as the sole account host; refusing cutover." >&2
    return 1
  fi
  if app_user_role_grant_exists; then
    echo "App user '$APP_DB_USER'@'$APP_DB_HOST' inherits a role; refusing cutover until all role edges are removed." >&2
    return 1
  else
    check_status=$?
    if [[ "$check_status" -eq 2 ]]; then
      echo "Unable to inspect role inheritance for '$APP_DB_USER'@'$APP_DB_HOST'; refusing cutover." >&2
      return 1
    fi
  fi
  if app_user_global_dml_grant_exists; then
    echo "App user '$APP_DB_USER'@'$APP_DB_HOST' has global DML or GRANT OPTION; refusing table-only REVOKE." >&2
    return 1
  else
    check_status=$?
    if [[ "$check_status" -eq 2 ]]; then
      echo "Unable to inspect global privileges for '$APP_DB_USER'@'$APP_DB_HOST'; refusing cutover." >&2
      return 1
    fi
  fi
  if app_user_schema_dml_grant_exists "$SOURCE_SCHEMA"; then
    echo "App user '$APP_DB_USER'@'$APP_DB_HOST' has schema-wide DML or GRANT OPTION on $SOURCE_SCHEMA; refusing table-only REVOKE." >&2
    return 1
  else
    check_status=$?
    if [[ "$check_status" -eq 2 ]]; then
      echo "Unable to inspect schema privileges for '$APP_DB_USER'@'$APP_DB_HOST'; refusing cutover." >&2
      return 1
    fi
  fi
  local missing=0
  for table in "${TABLES[@]}"; do
    if app_user_table_grant_exists "$SOURCE_SCHEMA" "$table"; then
      if app_user_table_unsafe_grant_exists "$SOURCE_SCHEMA" "$table"; then
        echo "App user '$APP_DB_USER'@'$APP_DB_HOST' has an unsafe extra/ALL table privilege on $SOURCE_SCHEMA.$table; refusing cutover." >&2
        missing=1
      else
        check_status=$?
        if [[ "$check_status" -eq 2 ]]; then
          echo "Unable to inspect table privileges on $SOURCE_SCHEMA.$table for '$APP_DB_USER'@'$APP_DB_HOST'; refusing cutover." >&2
          missing=1
        fi
      fi
    else
      check_status=$?
      if [[ "$check_status" -eq 2 ]]; then
        echo "Unable to inspect table grants on $SOURCE_SCHEMA.$table for '$APP_DB_USER'@'$APP_DB_HOST'; refusing cutover." >&2
      else
        echo "App user '$APP_DB_USER'@'$APP_DB_HOST' must have exactly table-scoped SELECT/INSERT/UPDATE/DELETE grants on $SOURCE_SCHEMA.$table before cutover." >&2
      fi
      missing=1
    fi
    if app_user_column_grant_exists "$SOURCE_SCHEMA" "$table"; then
      echo "App user '$APP_DB_USER'@'$APP_DB_HOST' has column-level privileges on $SOURCE_SCHEMA.$table; refusing table-only REVOKE." >&2
      missing=1
    else
      check_status=$?
      if [[ "$check_status" -eq 2 ]]; then
        echo "Unable to inspect column privileges on $SOURCE_SCHEMA.$table for '$APP_DB_USER'@'$APP_DB_HOST'; refusing cutover." >&2
        missing=1
      fi
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
    if ! mysql_query "DELETE FROM \`$TARGET_SCHEMA\`.\`$table\`;"; then
      echo "Cleanup failed for target table $TARGET_SCHEMA.$table." >&2
      return 1
    fi
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
    require_quiesce I_UNDERSTAND_SUBMISSION_QUIESCE_ALL_WRITERS
    if [[ "$SOURCE_SCHEMA" == "$TARGET_SCHEMA" ]]; then
      echo "Source and target are identical; refusing a no-op cutover." >&2
      exit 1
    fi
    if [[ -z "$APP_DB_USER" || -z "$APP_DB_HOST" ]]; then
      echo "Cutover requires SUBMISSION_APP_DB_USER and SUBMISSION_APP_DB_HOST so App grants can be revoked safely." >&2
      exit 1
    fi
    echo "WARNING: all App, Submission-owner, Judge, dispatcher, reaper, scheduler, and direct database writers must remain stopped and drained until the cutover completes." >&2
    assert_ready
    assert_revoke_ready || {
      echo "Refusing cutover: App grants are not revocable; run preflight first." >&2
      exit 1
    }
    if ! source_before="$(source_snapshot "$SOURCE_SCHEMA")"; then
      echo "Unable to capture source rows/checksums before copy; refusing cutover." >&2
      exit 1
    fi
    if ! copy_forward; then
      echo "Copy failed; aborting without revoking grants." >&2
      if ! cleanup_failed_cutover; then
        echo "CRITICAL: copied target cleanup failed; stop all writers and run reconciliation/rollback manually." >&2
      fi
      exit 1
    fi
    if ! source_after="$(source_snapshot "$SOURCE_SCHEMA")"; then
      echo "Unable to recheck source rows/checksums after copy; refusing to revoke grants." >&2
      if ! cleanup_failed_cutover; then
        echo "CRITICAL: copied target cleanup failed; stop all writers and run reconciliation/rollback manually." >&2
      fi
      exit 1
    fi
    if [[ "$source_before" != "$source_after" ]]; then
      echo "Source rows/checksums changed during copy; refusing to revoke grants and cleaning the target." >&2
      if ! cleanup_failed_cutover; then
        echo "CRITICAL: copied target cleanup failed; stop all writers and run reconciliation/rollback manually." >&2
      fi
      exit 1
    fi
    if ! revoke_app_grants; then
      echo "Grant revocation failed after copy; restoring App grants and rolling back copied rows." >&2
      if ! restore_app_grants; then
        echo "CRITICAL: App grant restoration failed; stop all writers and repair grants before restart." >&2
      fi
      if ! cleanup_failed_cutover; then
        echo "CRITICAL: copied target cleanup failed; stop all writers and run reconciliation/rollback manually." >&2
      fi
      exit 1
    fi
    print_snapshot "$SOURCE_SCHEMA" source-after-cutover
    print_snapshot "$TARGET_SCHEMA" target-after-cutover
    ;;
  rollback)
    require_execute I_UNDERSTAND_SUBMISSION_ROLLBACK
    require_quiesce I_UNDERSTAND_SUBMISSION_QUIESCE_ALL_WRITERS
    if [[ "$SOURCE_SCHEMA" == "$TARGET_SCHEMA" ]]; then
      echo "Source and target are identical; no rollback required." >&2
      exit 1
    fi
    if [[ -z "$APP_DB_USER" || -z "$APP_DB_HOST" ]]; then
      echo "Rollback requires SUBMISSION_APP_DB_USER and SUBMISSION_APP_DB_HOST (the same account used for cutover)." >&2
      exit 1
    fi
    if ! copy_back; then
      echo "CRITICAL: atomic rollback copy failed before COMMIT; verify source rows and keep all writers stopped before any restart." >&2
      echo "Rollback data copy failed; restoring App grants before stopping." >&2
      if ! restore_app_grants; then
        echo "CRITICAL: App grant restoration failed during rollback; stop all writers and repair grants manually." >&2
      fi
      exit 1
    fi
    if ! restore_app_grants; then
      echo "CRITICAL: rollback data copy completed but App grant restoration failed; stop all writers and repair grants manually." >&2
      exit 1
    fi
    print_snapshot "$SOURCE_SCHEMA" source-after-rollback
    print_snapshot "$TARGET_SCHEMA" target-after-rollback
    ;;
esac
