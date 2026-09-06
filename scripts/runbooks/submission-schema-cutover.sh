#!/usr/bin/env bash
set -euo pipefail

# SPLIT-003 slice-4 runbook helper: submission aggregate + judge/result outbox
# expand -> backfill -> verify -> cutover runbook. Actions are preflight,
# backfill, verify, cutover, and rollback. Backfill defaults to a read-only
# dry-run; batch writes require --execute plus two explicit confirmations.
#
# The source schema is the App schema (ulticode by default) where the
# submission aggregate and outboxes currently live; the target is the
# dedicated Submission owner schema (submission by default, created by
# MIGRATION_SCHEMA=submission ./scripts/dev/migrate.sh migrate).
#
# Backfill is checkpointed and insert-only: an existing target row with changed
# fields is exported as a failure and stops the batch; newer owner data is never
# overwritten. verify must report zero count/checksum/field/writer differences
# before cutover. Cutover only revokes the verified App table grants; it does
# not perform an implicit full-table copy.
#
# Before backfill --execute, cutover, or rollback, stop and drain every process
# that can write either schema: backend-app/App PM2, backend-submission,
# backend-judge, schedulers, dispatchers, reapers, and direct maintenance
# clients. Source rows/checksums are rechecked before REVOKE.
#
# The actual runtime cutover (APP_SUBMISSION_ROUTING_MODE=remote) must only be
# enabled after the SPLIT-004 read-path migration. See the migration guide §8.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
ACTION="${1:-preflight}"
EXECUTE="${2:-}"

case "$ACTION" in
  preflight|backfill|verify|cutover|rollback) ;;
  *)
    echo "Usage: $0 [preflight|backfill|verify|cutover|rollback] [--execute]" >&2
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
BACKFILL_BATCH_SIZE="${BACKFILL_BATCH_SIZE:-500}"
BACKFILL_AUDIT_DIR="${BACKFILL_AUDIT_DIR:-$ROOT_DIR/.local/migration-audit}"
BACKFILL_CHECKPOINT_FILE="${BACKFILL_CHECKPOINT_FILE:-$BACKFILL_AUDIT_DIR/submission-backfill.checkpoint}"
BACKFILL_FAILURE_FILE="${BACKFILL_FAILURE_FILE:-$BACKFILL_AUDIT_DIR/submission-backfill.failures.tsv}"
BACKFILL_DRY_RUN_CHECKPOINT_FILE="${BACKFILL_DRY_RUN_CHECKPOINT_FILE:-$BACKFILL_CHECKPOINT_FILE.dry-run}"

if [[ ! "$BACKFILL_BATCH_SIZE" =~ ^[1-9][0-9]{0,4}$ ]]; then
  echo "BACKFILL_BATCH_SIZE must be an integer from 1 to 99999." >&2
  exit 1
fi
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

assert_schema_ready() {
  local table
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
  done
  for table in "${TARGET_ONLY_TABLES[@]}"; do
    if ! table_exists "$TARGET_SCHEMA" "$table"; then
      echo "Target-only table missing: $TARGET_SCHEMA.$table; run submission migrations first" >&2
      return 1
    fi
  done
}

assert_target_empty() {
  local table
  for table in "${TABLES[@]}" "${TARGET_ONLY_TABLES[@]}"; do
    if [[ "$(row_count "$TARGET_SCHEMA" "$table")" != "0" ]]; then
      echo "Target table is not empty: $TARGET_SCHEMA.$table" >&2
      return 1
    fi
  done
}

assert_ready() {
  assert_schema_ready || return 1
  assert_target_empty || return 1
}

sql_quote() {
  local value="$1"
  value="$(printf '%s' "$value" | sed "s/'/''/g")"
  printf "'%s'" "$value"
}

column_names() {
  local schema="$1" table="$2"
  mysql_query "SELECT COLUMN_NAME FROM information_schema.columns
    WHERE table_schema = '$schema' AND table_name = '$table'
      AND (extra IS NULL
           OR (extra NOT LIKE '%VIRTUAL GENERATED%'
               AND extra NOT LIKE '%STORED GENERATED%'))
    ORDER BY ordinal_position;"
}

validated_columns() {
  local schema="$1" table="$2" columns column
  columns="$(column_names "$schema" "$table")" || return 1
  while IFS= read -r column; do
    [[ -n "$column" ]] || continue
    [[ "$column" =~ ^[A-Za-z0-9_]+$ ]] || {
      echo "Unsafe column name returned by metadata: $schema.$table.$column" >&2
      return 1
    }
    printf '%s\n' "$column"
  done <<< "$columns"
}

column_list() {
  local schema="$1" table="$2" columns column result=""
  columns="$(validated_columns "$schema" "$table")" || return 1
  while IFS= read -r column; do
    [[ -n "$column" ]] || continue
    [[ -n "$result" ]] && result+=","
    result+="$column"
  done <<< "$columns"
  [[ -n "$result" ]] || {
    echo "No comparable columns found: $schema.$table" >&2
    return 1
  }
  printf '%s\n' "$result"
}

field_predicate() {
  local schema="$1" table="$2" columns column result=""
  columns="$(validated_columns "$schema" "$table")" || return 1
  while IFS= read -r column; do
    [[ -n "$column" ]] || continue
    [[ -n "$result" ]] && result+=" AND "
    result+="s.$column <=> t.$column"
  done <<< "$columns"
  [[ -n "$result" ]] || return 1
  printf '%s\n' "$result"
}

batch_range_predicate() {
  local start="$1" end="$2" alias="${3:-}" column="id" result="1=1"
  [[ -n "$alias" ]] && column="$alias.id"
  if [[ -n "$start" ]]; then
    result="$column > $(sql_quote "$start")"
  fi
  if [[ -n "$end" ]]; then
    if [[ "$result" == "1=1" ]]; then
      result="$column <= $(sql_quote "$end")"
    else
      result+=" AND $column <= $(sql_quote "$end")"
    fi
  fi
  printf '%s\n' "$result"
}

checkpoint_last_id() {
  local file="$1" table="$2"
  awk -F '\t' -v table="$table" '$1 == table { print substr($0, index($0, "\t") + 1); found = 1 } END { if (!found) exit 0 }' "$file"
}

checkpoint_prepare() {
  local file="$1"
  mkdir -p "$(dirname "$file")"
  if [[ ! -f "$file" ]]; then
    {
      printf 'version=1\n'
      printf 'source_schema=%s\n' "$SOURCE_SCHEMA"
      printf 'target_schema=%s\n' "$TARGET_SCHEMA"
    } > "$file"
    return 0
  fi
  grep -Fqx 'version=1' "$file" || {
    echo "Unsupported backfill checkpoint format: $file" >&2
    return 1
  }
  grep -Fqx "source_schema=$SOURCE_SCHEMA" "$file" || {
    echo "Checkpoint source schema mismatch: $file" >&2
    return 1
  }
  grep -Fqx "target_schema=$TARGET_SCHEMA" "$file" || {
    echo "Checkpoint target schema mismatch: $file" >&2
    return 1
  }
}

checkpoint_save() {
  local file="$1" table="$2" last_id="$3" tmp
  tmp="$(mktemp "$file.tmp.XXXXXX")"
  awk -F '\t' -v table="$table" '$1 != table { print }' "$file" > "$tmp"
  printf '%s\t%s\n' "$table" "$last_id" >> "$tmp"
  mv -f -- "$tmp" "$file"
}

failure_export() {
  local table="$1" start="$2" end="$3" reason="$4" safe_reason
  mkdir -p "$(dirname "$BACKFILL_FAILURE_FILE")"
  if [[ ! -s "$BACKFILL_FAILURE_FILE" ]]; then
    printf 'timestamp\ttable\tstart_id\tend_id\treason\n' > "$BACKFILL_FAILURE_FILE"
  fi
  safe_reason="$(printf '%s' "$reason" | tr '\t\r\n' '   ')"
  printf '%s\t%s\t%s\t%s\t%s\n' \
    "$(date -Is)" "$table" "$start" "$end" "$safe_reason" >> "$BACKFILL_FAILURE_FILE"
}
failure_export_prepare() {
  mkdir -p "$(dirname "$BACKFILL_FAILURE_FILE")"
  if [[ ! -s "$BACKFILL_FAILURE_FILE" ]]; then
    printf 'timestamp\ttable\tstart_id\tend_id\treason\n' > "$BACKFILL_FAILURE_FILE"
  fi
}
require_backfill_execute() {
  if ! require_write_confirmation "$EXECUTE" SUBMISSION_BACKFILL_CONFIRM I_UNDERSTAND_SUBMISSION_BACKFILL; then
    echo "Refusing backfill write. Pass --execute and SUBMISSION_BACKFILL_CONFIRM=I_UNDERSTAND_SUBMISSION_BACKFILL." >&2
    exit 1
  fi
  if ! gate_confirmed SUBMISSION_BACKFILL_QUIESCE_CONFIRM I_UNDERSTAND_SUBMISSION_BACKFILL_QUIESCE_ALL_WRITERS; then
    echo "Refusing backfill write. Stop and drain every App, Submission, Judge, scheduler, and direct database writer; then pass SUBMISSION_BACKFILL_QUIESCE_CONFIRM=I_UNDERSTAND_SUBMISSION_BACKFILL_QUIESCE_ALL_WRITERS." >&2
    exit 1
  fi
}

next_batch_end() {
  local table="$1" start="$2" predicate
  predicate="$(batch_range_predicate "$start" "")"
  mysql_query "SELECT COALESCE(MAX(id), '') FROM (SELECT id FROM $SOURCE_SCHEMA.$table WHERE $predicate ORDER BY id LIMIT $BACKFILL_BATCH_SIZE) AS batch;"
}

insert_batch() {
  local table="$1" start="$2" end="$3" columns predicate
  columns="$(column_list "$SOURCE_SCHEMA" "$table")" || return 1
  predicate="$(batch_range_predicate "$start" "$end" s)"
  mysql_query "INSERT INTO $TARGET_SCHEMA.$table ($columns)
    SELECT $columns FROM $SOURCE_SCHEMA.$table s
    WHERE $predicate
      AND NOT EXISTS (SELECT 1 FROM $TARGET_SCHEMA.$table t WHERE t.id = s.id);" >/dev/null
}

batch_conflict_count() {
  local table="$1" start="$2" end="$3" range predicate
  range="$(batch_range_predicate "$start" "$end" s)"
  predicate="$(field_predicate "$SOURCE_SCHEMA" "$table")" || return 1
  mysql_query "SELECT COUNT(*) FROM $SOURCE_SCHEMA.$table s
    JOIN $TARGET_SCHEMA.$table t ON t.id = s.id
    WHERE $range AND NOT ($predicate);"
}

batch_missing_count() {
  local table="$1" start="$2" end="$3" range
  range="$(batch_range_predicate "$start" "$end" s)"
  mysql_query "SELECT COUNT(*) FROM $SOURCE_SCHEMA.$table s
    LEFT JOIN $TARGET_SCHEMA.$table t ON t.id = s.id
    WHERE $range AND t.id IS NULL;"
}

do_backfill() {
  local dry_run=1 checkpoint_file table last_id end conflicts missing
  if [[ -n "$EXECUTE" && "$EXECUTE" != "--dry-run" && "$EXECUTE" != "--execute" ]]; then
    echo "Usage: $0 backfill [--dry-run|--execute]" >&2
    return 2
  fi
  [[ "$EXECUTE" == "--execute" ]] && dry_run=0
  [[ "$dry_run" == "1" ]] || require_backfill_execute
  assert_schema_ready || {
    echo "BACKFILL FAILED: source/target schema shape is not ready." >&2
    return 1
  }
  checkpoint_file="$BACKFILL_DRY_RUN_CHECKPOINT_FILE"
  [[ "$dry_run" == "0" ]] && checkpoint_file="$BACKFILL_CHECKPOINT_FILE"
  checkpoint_prepare "$checkpoint_file" || return 1
  failure_export_prepare || return 1
  for table in "${TABLES[@]}"; do
    last_id="$(checkpoint_last_id "$checkpoint_file" "$table")"
    while true; do
      end="$(next_batch_end "$table" "$last_id")" || {
        failure_export "$table" "$last_id" "" "unable to determine next batch boundary"
        return 1
      }
      [[ -n "$end" ]] || break
      [[ "$end" != "$last_id" ]] || {
        failure_export "$table" "$last_id" "$end" "checkpoint did not advance"
        return 1
      }
      conflicts="$(batch_conflict_count "$table" "$last_id" "$end")" || {
        failure_export "$table" "$last_id" "$end" "unable to compare existing owner rows"
        return 1
      }
      if [[ "$conflicts" != "0" ]]; then
        failure_export "$table" "$last_id" "$end" "field conflicts=$conflicts; newer owner rows are never overwritten"
        echo "BACKFILL CONFLICT table=$table start=$last_id end=$end field_conflicts=$conflicts; see $BACKFILL_FAILURE_FILE" >&2
        return 1
      fi
      missing="$(batch_missing_count "$table" "$last_id" "$end")" || {
        failure_export "$table" "$last_id" "$end" "unable to count missing owner rows"
        return 1
      }
      if [[ "$dry_run" == "1" ]]; then
        echo "DRY-RUN table=$table start=$last_id end=$end missing=$missing action=insert-only"
      else
        if ! insert_batch "$table" "$last_id" "$end"; then
          failure_export "$table" "$last_id" "$end" "insert batch failed; checkpoint remains at $last_id"
          echo "BACKFILL FAILED table=$table start=$last_id end=$end; see $BACKFILL_FAILURE_FILE" >&2
          return 1
        fi
        echo "BACKFILL table=$table start=$last_id end=$end missing=$missing action=insert-only"
      fi
      checkpoint_save "$checkpoint_file" "$table" "$end"
      last_id="$end"
    done
  done
  echo "Backfill complete: mode=$([[ "$dry_run" == "1" ]] && printf dry-run || printf execute) checkpoint=$checkpoint_file failures=$BACKFILL_FAILURE_FILE"
}

app_grants_absent() {
  local table_privilege_count column_privilege_count check_status
  [[ -n "$APP_DB_USER" && -n "$APP_DB_HOST" ]] || return 1
  table_privilege_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.table_privileges
    WHERE GRANTEE = CONCAT(CHAR(39), '$APP_DB_USER', CHAR(39), '@', CHAR(39), '$APP_DB_HOST', CHAR(39))
      AND TABLE_SCHEMA = '$SOURCE_SCHEMA'
      AND TABLE_NAME IN ('submissions','judge_outbox','submission_result_outbox');")" || return 1
  [[ "$table_privilege_count" == "0" ]] || return 1
  column_privilege_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.COLUMN_PRIVILEGES
    WHERE GRANTEE = CONCAT(CHAR(39), '$APP_DB_USER', CHAR(39), '@', CHAR(39), '$APP_DB_HOST', CHAR(39))
      AND TABLE_SCHEMA = '$SOURCE_SCHEMA';")" || return 1
  [[ "$column_privilege_count" == "0" ]] || return 1
  if app_user_role_grant_exists; then
    return 1
  else
    check_status=$?
    [[ "$check_status" == "1" ]] || return 1
  fi
  if app_user_global_dml_grant_exists; then
    return 1
  else
    check_status=$?
    [[ "$check_status" == "1" ]] || return 1
  fi
  if app_user_schema_dml_grant_exists "$SOURCE_SCHEMA"; then
    return 1
  else
    check_status=$?
    [[ "$check_status" == "1" ]] || return 1
  fi
}

verify_writer_state() {
  [[ -n "$APP_DB_USER" && -n "$APP_DB_HOST" ]] || {
    echo "WRITER_DIFF=1 state=UNKNOWN reason=SUBMISSION_APP_DB_USER/HOST not supplied" >&2
    return 1
  }
  if assert_revoke_ready >/dev/null 2>&1; then
    echo "WRITER_DIFF=0 state=PRE_CUTOVER app_grants=exact_table_scoped"
    return 0
  fi
  if app_grants_absent; then
    echo "WRITER_DIFF=0 state=POST_CUTOVER app_grants=none"
    return 0
  fi
  echo "WRITER_DIFF=1 state=UNSAFE app_grants=unexplained" >&2
  return 1
}

verify_table_parity() {
  local table="$1" source_rows target_rows missing extra fields source_checksum target_checksum predicate
  source_rows="$(row_count "$SOURCE_SCHEMA" "$table")" || return 1
  target_rows="$(row_count "$TARGET_SCHEMA" "$table")" || return 1
  missing="$(mysql_query "SELECT COUNT(*) FROM $SOURCE_SCHEMA.$table s
    LEFT JOIN $TARGET_SCHEMA.$table t ON t.id = s.id WHERE t.id IS NULL;")" || return 1
  extra="$(mysql_query "SELECT COUNT(*) FROM $TARGET_SCHEMA.$table t
    LEFT JOIN $SOURCE_SCHEMA.$table s ON s.id = t.id WHERE s.id IS NULL;")" || return 1
  predicate="$(field_predicate "$SOURCE_SCHEMA" "$table")" || return 1
  fields="$(mysql_query "SELECT COUNT(*) FROM $SOURCE_SCHEMA.$table s
    JOIN $TARGET_SCHEMA.$table t ON t.id = s.id WHERE NOT ($predicate);")" || return 1
  source_checksum="$(checksum_table "$SOURCE_SCHEMA" "$table")" || return 1
  target_checksum="$(checksum_table "$TARGET_SCHEMA" "$table")" || return 1
  printf 'PARITY table=%s source_rows=%s target_rows=%s missing=%s extra=%s field_differences=%s source_checksum=%s target_checksum=%s\n' \
    "$table" "$source_rows" "$target_rows" "$missing" "$extra" "$fields" "$source_checksum" "$target_checksum"
  [[ "$source_rows" == "$target_rows" \
    && "$missing" == "0" \
    && "$extra" == "0" \
    && "$fields" == "0" \
    && "$source_checksum" == "$target_checksum" ]]
}

verify_submission_parity() {
  local failures=0 table
  for table in "${TABLES[@]}"; do
    if verify_table_parity "$table"; then
      echo "PARITY_OK table=$table"
    else
      echo "PARITY_FAIL table=$table" >&2
      failures=$((failures + 1))
    fi
  done
  return "$failures"
}

do_verify() {
  local failures=0
  assert_schema_ready || {
    echo "VERIFY FAILED: source/target schema shape is not ready." >&2
    return 1
  }
  verify_submission_parity || failures=$((failures + 1))
  verify_writer_state || failures=$((failures + 1))
  if [[ "$failures" == "0" ]]; then
    echo "VERIFY PASS: count/checksum/field/writer differences are zero."
  else
    echo "VERIFY FAIL: unexplained differences=$failures; cutover is blocked." >&2
  fi
  return "$failures"
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
  backfill)
    do_backfill
    ;;
  verify)
    do_verify
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
    assert_schema_ready || {
      echo "Refusing cutover: source/target schema shape is not ready." >&2
      exit 1
    }
    verify_submission_parity || {
      echo "Refusing cutover: count/checksum/field differences are not zero; run backfill/verify." >&2
      exit 1
    }
    verify_writer_state || {
      echo "Refusing cutover: writer differences are not zero." >&2
      exit 1
    }
    if ! source_before="$(source_snapshot "$SOURCE_SCHEMA")"; then
      echo "Unable to capture source rows/checksums before grant revocation; refusing cutover." >&2
      exit 1
    fi
    if ! revoke_app_grants; then
      echo "Grant revocation failed; restoring App grants before stopping." >&2
      restore_app_grants || echo "CRITICAL: App grant restoration failed; stop all writers and repair grants manually." >&2
      exit 1
    fi
    if ! app_grants_absent; then
      echo "App writer grants remain after revocation; restoring grants and refusing cutover." >&2
      restore_app_grants || echo "CRITICAL: App grant restoration failed; stop all writers and repair grants manually." >&2
      exit 1
    fi
    if ! source_after="$(source_snapshot "$SOURCE_SCHEMA")"; then
      echo "Unable to recheck source rows/checksums after grant revocation; refusing cutover." >&2
      restore_app_grants || echo "CRITICAL: App grant restoration failed; stop all writers and repair grants manually." >&2
      exit 1
    fi
    if [[ "$source_before" != "$source_after" ]]; then
      echo "Source rows/checksums changed during cutover; restoring App grants and refusing cutover." >&2
      restore_app_grants || echo "CRITICAL: App grant restoration failed; stop all writers and repair grants manually." >&2
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
