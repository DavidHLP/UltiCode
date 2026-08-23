#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
COMMAND="${1:-migrate}"
case "$COMMAND" in
  migrate|validate|info|repair|baseline)
    ;;
  *)
    echo "Unsupported Flyway command: $COMMAND" >&2
    exit 2
    ;;
esac

# shellcheck source=scripts/dev/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

# Preserve explicit caller-provided migration values while loading .env.
capture_env_vars MIGRATION_SCHEMA MIGRATION_DB_HOST MIGRATION_DB_PORT \
  MIGRATION_DB_NAME MIGRATION_DB_USER MIGRATION_DB_PASSWORD \
  MIGRATION_MYSQL_CONTAINER MIGRATION_MYSQL_CONTAINER_PORT

load_env_file

# Explicit environment values win over values sourced from .env. Owner
# migrations must never silently turn a runtime DB_* value into a migration
# connection.
apply_env_overrides
MIGRATION_MYSQL_CONTAINER_PORT="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"
fail_preflight() {
  echo "Migration preflight failed: $*" >&2
  exit 1
}

runtime_user_for_schema() {
  case "$1" in
    auth) printf '%s' "${AUTH_DB_USER:-}" ;;
    admin) printf '%s' "${ADMIN_DB_USER:-}" ;;
    app) printf '%s' "${APP_DB_USER:-}" ;;
    notification) printf '%s' "${NOTIFICATION_DB_USER:-}" ;;
    submission) printf '%s' "${SUBMISSION_DB_USER:-}" ;;
    *) printf '' ;;
  esac
}

mysql_query() {
  if [[ -n "${MIGRATION_MYSQL_CONTAINER:-}" ]]; then
    docker exec -e MYSQL_PWD="$MIGRATION_DB_PASSWORD" "$MIGRATION_MYSQL_CONTAINER" \
      mysql \
      --protocol=tcp \
      --default-character-set=utf8mb4 \
      --batch --skip-column-names \
      -h 127.0.0.1 \
      -P "$MIGRATION_MYSQL_CONTAINER_PORT" \
      -u "$MIGRATION_DB_USER" \
      "$MIGRATION_DB_NAME" \
      -e "$1"
  else
    MYSQL_PWD="$MIGRATION_DB_PASSWORD" mysql \
      --protocol=tcp \
      --default-character-set=utf8mb4 \
      --batch --skip-column-names \
      -h "$MIGRATION_DB_HOST" \
      -P "$MIGRATION_DB_PORT" \
      -u "$MIGRATION_DB_USER" \
      "$MIGRATION_DB_NAME" \
      -e "$1"
  fi
}


has_grant_privilege() {
  local grant_text="$1"
  local required="$2"
  local line prefix tokens
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    prefix="${line#GRANT }"
    prefix="${prefix%% ON *}"
    prefix="${prefix^^}"
    [[ "$prefix" == "ALL PRIVILEGES" || "$prefix" == "$required" ]] && return 0
    tokens=",${prefix//, /,},"
    [[ "$tokens" == *",$required,"* ]] && return 0
  done <<< "$grant_text"
  return 1
}

has_grant_option() {
  [[ "$1" == *"WITH GRANT OPTION"* ]]
}

has_global_owner_superset() {
  local account="$1"
  local grant_text="$2"
  local required_privilege

  if has_grant_privilege "$grant_text" "ALL PRIVILEGES"; then
    has_grant_option "$grant_text"
    return
  fi

  # MySQL 9.1 renders the local system root account as an explicit static
  # privilege list plus dynamic privileges instead of `ALL PRIVILEGES`.
  # Accept that shape only for root with direct grant option and the complete
  # owner-migration capability set; arbitrary non-root global lists remain
  # rejected so a scoped migration identity cannot silently become global.
  [[ "$account" == "root" ]] || return 1
  has_grant_option "$grant_text" || return 1
  for required_privilege in \
      SELECT INSERT UPDATE DELETE CREATE ALTER INDEX REFERENCES \
      "CREATE USER" RELOAD; do
    has_grant_privilege "$grant_text" "$required_privilege" || return 1
  done
}

has_role_grant() {
  local grant_text="$1"
  local line
  while IFS= read -r line; do
    [[ "$line" == GRANT\ * && "$line" != *" ON "* && "$line" == *" TO "* ]] && return 0
  done <<< "$grant_text"
  return 1
}

owner_preflight() {
  local runtime_user current_user current_user_name current_db schema_exists
  local grants schema_grants global_grants effective_grants line schema_marker
  for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
      MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
    [[ -n "${!variable:-}" ]] || fail_preflight "$variable is required for MIGRATION_SCHEMA=$MIGRATION_SCHEMA"
  done
  valid_identifier "$MIGRATION_SCHEMA" \
    || fail_preflight "invalid owner schema identifier: $MIGRATION_SCHEMA"
  valid_identifier "$MIGRATION_DB_NAME" \
    || fail_preflight "invalid migration database identifier: $MIGRATION_DB_NAME"
  valid_identifier "$MIGRATION_DB_USER" \
    || fail_preflight "invalid migration account identifier: $MIGRATION_DB_USER"
  valid_port "$MIGRATION_DB_PORT" \
    || fail_preflight "invalid migration database port: $MIGRATION_DB_PORT"
  [[ "$MIGRATION_DB_NAME" == "$MIGRATION_SCHEMA" ]] \
    || fail_preflight "MIGRATION_DB_NAME must equal MIGRATION_SCHEMA for owner migrations"

  runtime_user="$(runtime_user_for_schema "$MIGRATION_SCHEMA")"
  [[ -n "$runtime_user" ]] || fail_preflight "runtime owner account is not configured"
  [[ "$MIGRATION_DB_USER" != "$runtime_user" ]] \
    || fail_preflight "migration account must differ from runtime owner account '$runtime_user'"
  if [[ -n "${MIGRATION_MYSQL_CONTAINER:-}" ]]; then
    command -v docker >/dev/null 2>&1 \
      || fail_preflight "docker CLI is required when MIGRATION_MYSQL_CONTAINER is set"
    valid_container_ref "$MIGRATION_MYSQL_CONTAINER" \
      || fail_preflight "invalid migration MySQL container reference"
    valid_port "$MIGRATION_MYSQL_CONTAINER_PORT" \
      || fail_preflight "invalid migration MySQL container port: $MIGRATION_MYSQL_CONTAINER_PORT"
    [[ "$(docker inspect -f '{{.State.Running}}' "$MIGRATION_MYSQL_CONTAINER" 2>/dev/null || true)" == "true" ]] \
      || fail_preflight "migration MySQL container is not running: $MIGRATION_MYSQL_CONTAINER"
    mysql_container_targets_configured_host "$MIGRATION_MYSQL_CONTAINER" "$MIGRATION_MYSQL_CONTAINER_PORT" \
      "$MIGRATION_DB_HOST" "$MIGRATION_DB_PORT" \
      || fail_preflight "configured migration target $MIGRATION_DB_HOST:$MIGRATION_DB_PORT is not a published endpoint of $MIGRATION_MYSQL_CONTAINER:$MIGRATION_MYSQL_CONTAINER_PORT"
  else
    command -v mysql >/dev/null 2>&1 || fail_preflight "mysql CLI is required for owner migrations"
  fi

  current_user="$(mysql_query 'SELECT CURRENT_USER();')" \
    || fail_preflight "cannot connect with the explicit migration account"
  current_user_name="$(mysql_query "SELECT SUBSTRING_INDEX(CURRENT_USER(), '@', 1);")" \
    || fail_preflight "cannot resolve the effective migration account"
  [[ "$current_user_name" == "$MIGRATION_DB_USER" ]] \
    || fail_preflight "effective account '$current_user_name' does not match requested migration account"
  current_db="$(mysql_query 'SELECT DATABASE();')" \
    || fail_preflight "cannot resolve the migration database"
  [[ "$current_db" == "$MIGRATION_DB_NAME" ]] \
    || fail_preflight "connected database '$current_db' does not match '$MIGRATION_DB_NAME'"
  schema_exists="$(mysql_query "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '$MIGRATION_SCHEMA';")" \
    || fail_preflight "cannot inspect owner schema"
  [[ "$schema_exists" == "1" ]] \
    || fail_preflight "owner schema '$MIGRATION_SCHEMA' does not exist"
  grants="$(mysql_query 'SHOW GRANTS FOR CURRENT_USER();')" \
    || fail_preflight "cannot capture migration privilege snapshot"
  [[ -n "$grants" ]] || fail_preflight "migration privilege snapshot is empty"
  if has_role_grant "$grants"; then
    fail_preflight "migration account must use direct grants; role grants are not supported"
  fi


  schema_marker=" ON \`$MIGRATION_SCHEMA\`.* TO "
  legacy_schema_marker=" ON $MIGRATION_SCHEMA.* TO "
  global_marker=" ON *.* TO "
  schema_grants=""
  global_grants=""
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    if [[ "$line" == *"$schema_marker"* || "$line" == *"$legacy_schema_marker"* ]]; then
      schema_grants+="$line"$'\n'
    elif [[ "$line" == *"$global_marker"* ]]; then
      global_grants+="$line"$'\n'
    fi
  done <<< "$grants"
  effective_grants="$schema_grants"
  if has_global_owner_superset "$current_user_name" "$global_grants"; then
    # A literal global ALL is an explicit compatibility superset. Direct
    # global capability lists are intentionally not accepted as owner scope
    # unless MySQL identifies the account as the local system root; dedicated
    # migration principals must carry schema-scoped grants.
    effective_grants+=$'\n'"$global_grants"
  fi
  [[ -n "$effective_grants" ]] \
    || fail_preflight "migration account has no grants on owner schema '$MIGRATION_SCHEMA'"
  local required_privilege
  for required_privilege in CREATE ALTER SELECT INSERT UPDATE DELETE INDEX REFERENCES; do
    has_grant_privilege "$effective_grants" "$required_privilege" \
      || fail_preflight "required migration privilege missing on '$MIGRATION_SCHEMA': $required_privilege"
  done
  if ! has_grant_option "$schema_grants"; then
    if ! has_global_owner_superset "$current_user_name" "$global_grants"; then
      fail_preflight "required migration privilege missing on '$MIGRATION_SCHEMA': GRANT OPTION"
    fi
  fi
  if [[ "$MIGRATION_SCHEMA" == "notification" || "$MIGRATION_SCHEMA" == "submission" ]]; then
    has_grant_privilege "$global_grants" "CREATE USER" \
      || fail_preflight "required migration privilege missing: CREATE USER"
    has_grant_option "$global_grants" \
      || fail_preflight "required migration privilege missing: global GRANT OPTION"
  fi
  if [[ "$MIGRATION_SCHEMA" == "auth" || "$MIGRATION_SCHEMA" == "notification" || "$MIGRATION_SCHEMA" == "submission" ]]; then
    # These canonical owner migrations contain FLUSH PRIVILEGES. A direct
    # RELOAD grant is required; literal global ALL or the MySQL 9.1 root
    # capability shape is the explicit compatibility superset, while
    # arbitrary non-root global capability lists fail.
    if ! has_grant_privilege "$global_grants" "RELOAD" \
        && ! has_grant_privilege "$global_grants" "ALL PRIVILEGES"; then
      fail_preflight "required migration privilege missing: RELOAD"
    fi
  fi

  printf 'Migration preflight passed: run_id=%s schema=%s database=%s account=%s current_user=%s\n' \
    "${MIGRATION_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}" \
    "$MIGRATION_SCHEMA" "$MIGRATION_DB_NAME" "$MIGRATION_DB_USER" "$current_user"
  printf 'Migration privilege snapshot (no password):\n%s\n' "$grants"
}

owner_baseline_version() {
  case "$MIGRATION_SCHEMA" in
    auth) printf '20260729140000' ;;
    admin) printf '20260729140100' ;;
    submission) printf '20260816039900' ;;
    *) fail_preflight "DEV-LOCAL baseline is not allowed for MIGRATION_SCHEMA=$MIGRATION_SCHEMA" ;;
  esac
}

owner_baseline_preflight() {
  [[ "${DEV_LOCAL_OWNER_BASELINE:-false}" == "true" ]] \
    || fail_preflight "baseline is DEV-LOCAL only; set DEV_LOCAL_OWNER_BASELINE=true"
  [[ "${DEV_LOCAL_OWNER_BASELINE_CONFIRM:-}" == "I_UNDERSTAND_DEV_LOCAL_OWNER_BASELINE" ]] \
    || fail_preflight "baseline requires DEV_LOCAL_OWNER_BASELINE_CONFIRM=I_UNDERSTAND_DEV_LOCAL_OWNER_BASELINE"

  local expected_table expected_columns expected_snapshot history_count
  local expected_signature actual_signature actual_snapshot actual_rows
  case "$MIGRATION_SCHEMA" in
    auth)
      expected_table="search_document_changed_outbox"
      expected_columns=15
      expected_signature="1:id:varchar(40):NO|2:owner:varchar(16):NO|3:aggregate_id:varchar(120):NO|4:aggregate_version:bigint:NO|5:event_type:varchar(64):NO|6:schema_version:int:NO|7:payload:json:NO|8:state:varchar(16):NO|9:attempts:int:NO|10:last_error:text:YES|11:created_at:datetime(3):NO|12:claimed_at:datetime(3):YES|13:claim_owner:varchar(80):YES|14:delivered_at:datetime(3):YES|15:next_retry_at:datetime(3):NO"
      ;;
    admin)
      expected_table="audit_outbox"
      expected_columns=10
      expected_signature="1:id:varchar(40):NO|2:performer_id:varchar(40):NO|3:user_id:varchar(40):YES|4:action:varchar(60):NO|5:resource_type:varchar(60):NO|6:resource_id:varchar(60):YES|7:details:text:YES|8:status:enum('pending','processed','failed'):NO|9:created_at:datetime(3):NO|10:processed_at:datetime(3):YES"
      ;;
    submission)
      expected_table="submission_created_outbox"
      expected_columns=17
      expected_signature="1:id:varchar(40):NO|2:submission_id:varchar(40):NO|3:generation:bigint:NO|4:user_id:varchar(40):NO|5:problem_id:varchar(120):NO|6:contest_id:varchar(40):NO|7:virtual_session_id:varchar(40):YES|8:language:varchar(50):NO|9:occurred_at:datetime(3):NO|10:state:varchar(16):NO|11:attempts:int:NO|12:last_error:text:YES|13:created_at:datetime(3):NO|14:claimed_at:datetime(3):YES|15:claim_owner:varchar(80):YES|16:delivered_at:datetime(3):YES|17:next_retry_at:datetime(3):NO"
      ;;
    *)
      fail_preflight "DEV-LOCAL baseline has no expected bootstrap allowlist for $MIGRATION_SCHEMA"
      ;;
  esac

  history_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$MIGRATION_SCHEMA' AND table_name = 'flyway_schema_history';")" \
    || fail_preflight "cannot inspect owner Flyway history before baseline"
  [[ "$history_count" == "0" ]] \
    || fail_preflight "owner schema '$MIGRATION_SCHEMA' already has Flyway history; baseline is only for fresh adoption"

  expected_snapshot="$expected_table:$expected_columns"
  actual_snapshot="$(mysql_query "SELECT COALESCE(GROUP_CONCAT(CONCAT(table_name, ':', column_count) ORDER BY table_name SEPARATOR ','), '') FROM (SELECT table_name, COUNT(*) AS column_count FROM information_schema.columns WHERE table_schema = '$MIGRATION_SCHEMA' AND table_name <> 'flyway_schema_history' GROUP BY table_name) AS bootstrap_tables;")" \
    || fail_preflight "cannot inspect expected bootstrap tables"
  [[ "$actual_snapshot" == "$expected_snapshot" ]] \
    || fail_preflight "bootstrap table set mismatch for '$MIGRATION_SCHEMA': expected '$expected_snapshot', got '$actual_snapshot'"
  actual_signature="$(mysql_query "SELECT COALESCE(GROUP_CONCAT(CONCAT(ordinal_position, ':', column_name, ':', LOWER(column_type), ':', is_nullable) ORDER BY ordinal_position SEPARATOR '|'), '') FROM information_schema.columns WHERE table_schema = '$MIGRATION_SCHEMA' AND table_name = '$expected_table';")" \
    || fail_preflight "cannot inspect bootstrap table column signature"
  [[ "$actual_signature" == "$expected_signature" ]] \
    || fail_preflight "bootstrap column signature mismatch for '$MIGRATION_SCHEMA.$expected_table'"

  actual_rows="$(mysql_query "SELECT COUNT(*) FROM $MIGRATION_SCHEMA.$expected_table;")" \
    || fail_preflight "cannot inspect bootstrap table rows"
  [[ "$actual_rows" == "0" ]] \
    || fail_preflight "bootstrap table '$MIGRATION_SCHEMA.$expected_table' is non-empty; refusing baseline"

  MIGRATION_BASELINE_VERSION="$(owner_baseline_version)"
  printf 'DEV-LOCAL baseline preflight passed: schema=%s bootstrap=%s columns=%s baseline=%s rows=%s\n' \
    "$MIGRATION_SCHEMA" "$expected_table" "$expected_columns" "$MIGRATION_BASELINE_VERSION" "$actual_rows"
}

auth_contract_preflight() {
  [[ "$MIGRATION_SCHEMA" == "auth" && "$COMMAND" == "migrate" ]] || return 0

  local auth_users_table_count auth_profile_column_count
  auth_users_table_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'auth' AND table_name = 'users';")" \
    || fail_preflight "cannot inspect Auth users table before contract migration"
  [[ "$auth_users_table_count" == "1" ]] || return 0

  auth_profile_column_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'auth' AND table_name = 'users' AND column_name IN ('name','avatar','bio','company','github','location','twitter','website','preferred_language');")" \
    || fail_preflight "cannot inspect Auth profile columns before contract migration"
  [[ "$auth_profile_column_count" == "0" ]] && return 0
  [[ "$auth_profile_column_count" == "9" ]] || \
    fail_preflight "Auth users profile columns are not in the expected expand-phase shape: found $auth_profile_column_count"

  echo "Running Auth/Profile contract preflight before dropping Auth profile columns..."
  MIGRATION_DB_HOST="$MIGRATION_DB_HOST" \
    MIGRATION_DB_PORT="$MIGRATION_DB_PORT" \
    MIGRATION_DB_USER="$MIGRATION_DB_USER" \
    MIGRATION_DB_PASSWORD="$MIGRATION_DB_PASSWORD" \
    MIGRATION_MYSQL_CONTAINER="${MIGRATION_MYSQL_CONTAINER:-}" \
    MIGRATION_MYSQL_CONTAINER_PORT="$MIGRATION_MYSQL_CONTAINER_PORT" \
    "$ROOT_DIR/scripts/runbooks/owner-user-profile-backfill.sh" contract-preflight
}

if [[ -n "${MIGRATION_SCHEMA:-}" ]]; then
  owner_schema "$MIGRATION_SCHEMA" \
    || fail_preflight "unsupported MIGRATION_SCHEMA=$MIGRATION_SCHEMA"
  [[ -f "$ROOT_DIR/init-db/flyway-$MIGRATION_SCHEMA.conf" ]] \
    || fail_preflight "missing owner Flyway config for $MIGRATION_SCHEMA"
  owner_preflight
  auth_contract_preflight
  if [[ "$COMMAND" == "baseline" ]]; then
    owner_baseline_preflight
  fi
  export DB_HOST="$MIGRATION_DB_HOST"
  export DB_PORT="$MIGRATION_DB_PORT"
  export DB_NAME="$MIGRATION_DB_NAME"
  export DB_USER="$MIGRATION_DB_USER"
  export DB_PASSWORD="$MIGRATION_DB_PASSWORD"
else
  [[ "$COMMAND" != "baseline" ]] || fail_preflight "baseline requires MIGRATION_SCHEMA=auth|admin|submission"
  # The shared chain keeps its historical DB_* contract. Only owner-schema
  # migrations require the explicit MIGRATION_DB_* contract above.
  : "${DB_HOST:?DB_HOST is required}"
  : "${DB_PORT:?DB_PORT is required}"
  : "${DB_USER:?DB_USER is required}"
  : "${DB_PASSWORD:?DB_PASSWORD is required}"
  if [[ -n "${MIGRATION_DB_NAME:-}" ]]; then
    export DB_NAME="$MIGRATION_DB_NAME"
  fi
  : "${DB_NAME:?DB_NAME is required}"
  : "${MIGRATION_DB_USER:=$DB_USER}"
  : "${MIGRATION_DB_PASSWORD:=$DB_PASSWORD}"
  export DB_USER="$MIGRATION_DB_USER"
  export DB_PASSWORD="$MIGRATION_DB_PASSWORD"
fi

if [[ -n "${NOTIFICATION_DB_NAME:-}" && "$NOTIFICATION_DB_NAME" != "notification" ]]; then
  echo "NOTIFICATION_DB_NAME must be 'notification' with flyway-notification.conf; arbitrary owner database names are not supported by this migration set." >&2
  exit 1
fi

if [[ -n "${SUBMISSION_DB_NAME:-}" && "$SUBMISSION_DB_NAME" != "submission" ]]; then
  echo "SUBMISSION_DB_NAME must be 'submission' with flyway-submission.conf; arbitrary owner database names are not supported by this migration set." >&2
  exit 1
fi

cd "$ROOT_DIR/init-db"

run_flyway_config() {
  local config_file="$1"
  local flyway_command="$2"
  local baseline_args=()
  if [[ -n "${MIGRATION_SCHEMA:-}" && -f "flyway-${MIGRATION_SCHEMA}.conf" ]]; then
    config_file="flyway-${MIGRATION_SCHEMA}.conf"
  fi
  if [[ "$flyway_command" == "baseline" ]]; then
    baseline_args=(
      "-Dflyway.baselineVersion=$MIGRATION_BASELINE_VERSION"
      "-Dflyway.baselineDescription=DEV-LOCAL owner schema bootstrap"
    )
  fi
  mvn "flyway:$flyway_command" \
    -Dflyway.configFiles="$config_file" \
    "${baseline_args[@]}" \
    --no-transfer-progress \
    -B
}

run_flyway() {
  run_flyway_config "flyway.conf" "$1"
}

owner_baseline_if_needed() {
  local history_count bootstrap_table_count
  [[ "${DEV_LOCAL_OWNER_BASELINE:-false}" == "true" ]] || return 0
  [[ "${DEV_LOCAL_OWNER_BASELINE_CONFIRM:-}" == "I_UNDERSTAND_DEV_LOCAL_OWNER_BASELINE" ]] || return 0

  history_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$MIGRATION_SCHEMA' AND table_name = 'flyway_schema_history';")" \
    || fail_preflight "cannot inspect owner Flyway history before automatic DEV-LOCAL baseline"
  [[ "$history_count" == "0" ]] || return 0

  bootstrap_table_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$MIGRATION_SCHEMA' AND table_name <> 'flyway_schema_history';")" \
    || fail_preflight "cannot inspect owner schema before automatic DEV-LOCAL baseline"
  [[ "$bootstrap_table_count" != "0" ]] || return 0

  owner_baseline_preflight
  run_flyway baseline
}

# 仅保留既有主库迁移的自愈行为。owner schema 的历史漂移必须显式处理，
# 不能自动 repair 后继续，否则可能把版本冲突伪装成成功。
if [[ "$COMMAND" == "baseline" ]]; then
  run_flyway baseline
elif [[ "$COMMAND" == "migrate" ]]; then
  if [[ -n "${MIGRATION_SCHEMA:-}" ]]; then
    owner_baseline_if_needed
    run_flyway migrate
  elif ! run_flyway migrate; then
    echo "Flyway migrate failed; running repair then retrying..." >&2
    run_flyway repair
    run_flyway migrate
    run_flyway_config "flyway-notification.conf" migrate
  else
    run_flyway_config "flyway-notification.conf" migrate
  fi
else
  run_flyway "$COMMAND"
  if [[ -z "${MIGRATION_SCHEMA:-}" ]]; then
    run_flyway_config "flyway-notification.conf" "$COMMAND"
  fi
fi
