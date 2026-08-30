#!/usr/bin/env bash
set -euo pipefail

# P2-MIG-001: the CD migration entry point. It is deliberately separate from
# scripts/dev/migrate.sh because production hosts run Flyway in Docker rather
# than requiring Maven/Java on the host.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMMAND="${1:-validate}"
case "$COMMAND" in
  validate|migrate|rollback)
    ;;
  *)
    echo "Usage: $0 {validate|migrate|rollback}" >&2
    exit 2
    ;;
esac

OWNER_MIGRATION_ORDER=(auth admin app notification submission)
declare -A OWNER_MIGRATION_DEPENDENCIES=(
  [auth]=""
  [admin]="auth"
  [app]="auth"
  [notification]="app"
  [submission]="app"
)
REPORT_DIR="${OWNER_MIGRATION_REPORT_DIR:-$ROOT_DIR/.local/migration-audit}"
LOCK_FILE="${OWNER_MIGRATION_LOCK_FILE:-$REPORT_DIR/owner-migrations.lock}"
DOCKER_BIN="${DOCKER_BIN:-docker}"
FLYWAY_IMAGE="${OWNER_MIGRATION_FLYWAY_IMAGE:-flyway/flyway:10.17.0}"
MAX_ATTEMPTS="${OWNER_MIGRATION_MAX_ATTEMPTS:-2}"
REPORT_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
HUMAN_REPORT="$REPORT_DIR/owner-migration-$REPORT_ID.log"
MACHINE_REPORT="$REPORT_DIR/owner-migration-$REPORT_ID.json"
STATUS="FAILED"
PHASE="bootstrap"
ERROR_MESSAGE=""
MANIFEST_CHECKSUM=""
declare -A OWNER_CHECKSUMS=()
declare -a MANIFEST_FILES=()
POST_OWNER_CHECKSUM=""

mkdir -p "$REPORT_DIR"

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  printf '%s' "$value"
}

write_report() {
  local owner_json=""
  local owner
  for owner in "${OWNER_MIGRATION_ORDER[@]}"; do
    if [[ -n "$owner_json" ]]; then
      owner_json+=","
    fi
    owner_json+="\"$owner\":\"${OWNER_CHECKSUMS[$owner]:-unverified}\""
  done
  {
    printf '{\n'
    printf '  "status": "%s",\n' "$(json_escape "$STATUS")"
    printf '  "command": "%s",\n' "$(json_escape "$COMMAND")"
    printf '  "phase": "%s",\n' "$(json_escape "$PHASE")"
    printf '  "manifest_checksum": "%s",\n' "$(json_escape "${MANIFEST_CHECKSUM:-unverified}")"
    printf '  "owner_order": ["auth", "admin", "app", "notification", "submission"],\n'
    printf '  "owner_checksums": {%s},\n' "$owner_json"
    printf '  "post_owner_checksum": "%s",\n' "$(json_escape "${POST_OWNER_CHECKSUM:-unverified}")"
    printf '  "rollback_compatibility": "skip_migrations=true preserves schema",\n'
    printf '  "error": "%s"\n' "$(json_escape "$ERROR_MESSAGE")"
    printf '}\n'
  } >"$MACHINE_REPORT"
  {
    printf '[owner-migration] status=%s command=%s phase=%s\n' "$STATUS" "$COMMAND" "$PHASE"
    printf '[owner-migration] manifest_checksum=%s\n' "${MANIFEST_CHECKSUM:-unverified}"
    [[ -n "$ERROR_MESSAGE" ]] && printf '[owner-migration] error=%s\n' "$ERROR_MESSAGE"
    printf '[owner-migration] machine_report=%s\n' "$MACHINE_REPORT"
  } >>"$HUMAN_REPORT"
}

finish() {
  local exit_code=$?
  if [[ "$exit_code" -eq 0 ]]; then
    STATUS="PASS"
  fi
  write_report
  printf '[owner-migration] final status=%s report=%s\n' "$STATUS" "$MACHINE_REPORT"
  exit "$exit_code"
}
trap finish EXIT

die() {
  ERROR_MESSAGE="$1"
  printf '[owner-migration] FAIL: %s\n' "$ERROR_MESSAGE" >&2
  exit 1
}

require_value() {
  local variable="$1"
  [[ -n "${!variable:-}" ]] || die "$variable is required"
}

valid_identifier() {
  [[ "$1" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]
}

valid_port() {
  [[ "$1" =~ ^[0-9]+$ ]] && (( 1 <= 10#$1 && 10#$1 <= 65535 ))
}

owner_prefix() {
  printf '%s' "${1^^}"
}

owner_migration_user() {
  if [[ "$1" == "submission" ]]; then
    printf '%s' "$SUBMISSION_MIGRATION_DB_USER"
  else
    printf '%s' "$MIGRATION_DB_USER"
  fi
}

owner_migration_password() {
  if [[ "$1" == "submission" ]]; then
    printf '%s' "$SUBMISSION_MIGRATION_DB_PASSWORD"
  else
    printf '%s' "$MIGRATION_DB_PASSWORD"
  fi
}

collect_manifest_files() {
  MANIFEST_FILES=("$ROOT_DIR/init-db/migrations"/V*.sql)
  local owner file
  for owner in "${OWNER_MIGRATION_ORDER[@]}"; do
    MANIFEST_FILES+=("$ROOT_DIR/init-db/migrations/$owner"/V*.sql)
  done
  MANIFEST_FILES+=("$ROOT_DIR/init-db/flyway-post-owner.conf")
  MANIFEST_FILES+=("$ROOT_DIR/init-db/migrations/post-owner"/V*.sql)
  for file in "${MANIFEST_FILES[@]}"; do
    [[ -f "$file" ]] || die "manifest file glob has no match: ${file##*/}"
  done
  MANIFEST_CHECKSUM="$(sha256sum "${MANIFEST_FILES[@]}" | sha256sum | awk '{print $1}')"
}

validate_manifest() {
  PHASE="preflight"
  for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
      MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
    require_value "$variable"
  done
  valid_identifier "$MIGRATION_DB_NAME" || die "invalid migration database name"
  [[ "$MIGRATION_DB_NAME" == "ulticode" ]] || die "MIGRATION_DB_NAME must be ulticode"
  valid_identifier "$MIGRATION_DB_USER" || die "invalid migration account name"
  valid_port "$MIGRATION_DB_PORT" || die "invalid migration database port"

  if [[ "$COMMAND" != "rollback" ]]; then
    require_value SUBMISSION_MIGRATION_DB_USER
    require_value SUBMISSION_MIGRATION_DB_PASSWORD
    valid_identifier "$SUBMISSION_MIGRATION_DB_USER" \
      || die "invalid Submission migration account name"
    [[ "$MIGRATION_DB_USER" != "$SUBMISSION_MIGRATION_DB_USER" ]] \
      || die "shared and Submission migration accounts must differ"
  fi
  [[ "$MAX_ATTEMPTS" =~ ^[1-5]$ ]] || die "OWNER_MIGRATION_MAX_ATTEMPTS must be 1..5"

  local owner prefix runtime_user_var runtime_user db_name_var db_name migration_user dependency
  local config expected_location actual_schema migration_dir file index dependency_index
  local post_owner_config post_owner_location post_owner_schema post_owner_file
  declare -A owner_indexes=()
  for index in "${!OWNER_MIGRATION_ORDER[@]}"; do
    owner_indexes["${OWNER_MIGRATION_ORDER[$index]}"]="$index"
  done
  for owner in "${OWNER_MIGRATION_ORDER[@]}"; do
    prefix="$(owner_prefix "$owner")"
    runtime_user_var="${prefix}_DB_USER"
    db_name_var="${prefix}_DB_NAME"
    require_value "$runtime_user_var"
    require_value "$db_name_var"
    runtime_user="${!runtime_user_var}"
    db_name="${!db_name_var}"
    valid_identifier "$runtime_user" || die "invalid runtime account name: $runtime_user_var"
    [[ "$db_name" == "$owner" ]] || die "$db_name_var must be $owner"
    if [[ "$COMMAND" == "rollback" && "$owner" == "submission" ]]; then
      migration_user="rollback-mode"
    else
      migration_user="$(owner_migration_user "$owner")"
    fi
    [[ "$migration_user" != "$runtime_user" ]] \
      || die "$migration_user must differ from $runtime_user_var"
    dependency="${OWNER_MIGRATION_DEPENDENCIES[$owner]}"
    if [[ -n "$dependency" ]]; then
      dependency_index="${owner_indexes[$dependency]:-}"
      [[ -n "$dependency_index" && "$dependency_index" -lt "${owner_indexes[$owner]}" ]] \
        || die "migration dependency order invalid: $dependency must precede $owner"
    fi

    config="$ROOT_DIR/init-db/flyway-$owner.conf"
    migration_dir="$ROOT_DIR/init-db/migrations/$owner"
    [[ -f "$config" ]] || die "missing Flyway config: ${config#$ROOT_DIR/}"
    [[ -d "$migration_dir" ]] || die "missing owner migration directory: ${migration_dir#$ROOT_DIR/}"
    expected_location="flyway.locations=filesystem:migrations/$owner"
    grep -Fx -- "$expected_location" "$config" >/dev/null \
      || die "Flyway location mismatch for $owner"
    actual_schema="$(sed -n 's/^flyway.defaultSchema=//p' "$config" | head -1)"
    [[ "$actual_schema" == "$owner" ]] || die "Flyway schema mismatch for $owner"
    file="$(find "$migration_dir" -maxdepth 1 -type f -name 'V*.sql' -print | sort | head -1)"
    [[ -n "$file" ]] || die "no versioned migrations for $owner"
    while IFS= read -r file; do
      [[ "${file##*/}" =~ ^V[0-9]{14}__[A-Za-z0-9_]+\.sql$ ]] \
        || die "invalid owner migration filename: ${file#$ROOT_DIR/}"
    done < <(find "$migration_dir" -maxdepth 1 -type f -name 'V*.sql' -print | sort)
    OWNER_CHECKSUMS["$owner"]="$(sha256sum "$config" "$migration_dir"/V*.sql | sha256sum | awk '{print $1}')"
    printf '[owner-migration] preflight owner=%s dependency=%s schema=%s migration_account=%s checksum=%s\n' \
      "$owner" "${dependency:-none}" "$db_name" "$migration_user" "${OWNER_CHECKSUMS[$owner]}" | tee -a "$HUMAN_REPORT"
  done

  post_owner_config="$ROOT_DIR/init-db/flyway-post-owner.conf"
  post_owner_location="flyway.locations=filesystem:migrations/post-owner"
  [[ -f "$post_owner_config" ]] || die "missing post-owner Flyway config"
  grep -Fx -- "$post_owner_location" "$post_owner_config" >/dev/null \
    || die "Flyway location mismatch for post-owner controls"
  post_owner_schema="$(sed -n 's/^flyway.defaultSchema=//p' "$post_owner_config" | head -1)"
  [[ "$post_owner_schema" == "ulticode" ]] || die "post-owner Flyway schema must be ulticode"
  post_owner_file="$(find "$ROOT_DIR/init-db/migrations/post-owner" -maxdepth 1 -type f -name 'V*.sql' -print | sort | head -1)"
  [[ -n "$post_owner_file" ]] || die "no versioned post-owner migrations"
  while IFS= read -r post_owner_file; do
    [[ "${post_owner_file##*/}" =~ ^V[0-9]{14}__[A-Za-z0-9_]+\.sql$ ]] \
      || die "invalid post-owner migration filename: ${post_owner_file#$ROOT_DIR/}"
  done < <(find "$ROOT_DIR/init-db/migrations/post-owner" -maxdepth 1 -type f -name 'V*.sql' -print | sort)
  POST_OWNER_CHECKSUM="$(sha256sum "$post_owner_config" "$ROOT_DIR/init-db/migrations/post-owner"/V*.sql | sha256sum | awk '{print $1}')"
  printf '[owner-migration] preflight phase=post-owner schema=%s checksum=%s\n' \
    "$post_owner_schema" "$POST_OWNER_CHECKSUM" | tee -a "$HUMAN_REPORT"

  collect_manifest_files
  printf '[owner-migration] preflight order=%s\n' "${OWNER_MIGRATION_ORDER[*]}" | tee -a "$HUMAN_REPORT"
  printf '[owner-migration] preflight datasource=%s:%s root_database=%s\n' \
    "$MIGRATION_DB_HOST" "$MIGRATION_DB_PORT" "$MIGRATION_DB_NAME" | tee -a "$HUMAN_REPORT"
}

run_flyway() {
  local owner="$1" user="$2" password="$3" database="$4"
  local locations="filesystem:/flyway/sql/*.sql" schema_args=()
  local flyway_url="jdbc:mysql://${MIGRATION_DB_HOST}:${MIGRATION_DB_PORT}/${database}?allowPublicKeyRetrieval=true&useSSL=true"
  case "$owner" in
    shared)
      ;;
    post-owner)
      locations="filesystem:/flyway/sql/post-owner"
      schema_args=("-defaultSchema=ulticode" "-table=flyway_post_owner_history" "-baselineOnMigrate=true" "-baselineVersion=0")
      ;;
    *)
      locations="filesystem:/flyway/sql/$owner"
      schema_args=("-defaultSchema=$owner")
      ;;
  esac
  FLYWAY_URL="$flyway_url" FLYWAY_USER="$user" FLYWAY_PASSWORD="$password" \
    "$DOCKER_BIN" run --rm --network host \
      -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD \
      -v "$ROOT_DIR/init-db/migrations:/flyway/sql:ro" \
      "$FLYWAY_IMAGE" \
      "-locations=$locations" "-connectRetries=10" "${schema_args[@]}" migrate
}

migrate_with_retry() {
  local owner="$1" user="$2" password="$3" database="$4" attempt
  for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
    PHASE="migrate:$owner:attempt-$attempt"
    printf '[owner-migration] %s attempt=%s/%s\n' "$owner" "$attempt" "$MAX_ATTEMPTS" | tee -a "$HUMAN_REPORT"
    if run_flyway "$owner" "$user" "$password" "$database" >>"$HUMAN_REPORT" 2>&1; then
      return 0
    fi
    if (( attempt < MAX_ATTEMPTS )); then
      printf '[owner-migration] retrying owner=%s after Flyway failure; no repair is attempted\n' "$owner" \
        | tee -a "$HUMAN_REPORT"
    fi
  done
  return 1
}

run_migrations() {
  local lock_fd owner migration_user migration_password
  PHASE="lock"
  exec {lock_fd}>"$LOCK_FILE" || die "cannot open migration lock: $LOCK_FILE"
  if ! flock -n "$lock_fd"; then
    STATUS="SKIPPED"
    ERROR_MESSAGE="another owner migration run holds $LOCK_FILE"
    exit 75
  fi
  printf '[owner-migration] acquired lock=%s\n' "$LOCK_FILE" | tee -a "$HUMAN_REPORT"

  migration_user="$MIGRATION_DB_USER"
  migration_password="$MIGRATION_DB_PASSWORD"
  migrate_with_retry shared "$migration_user" "$migration_password" "$MIGRATION_DB_NAME" \
    || die "shared Flyway migration failed after $MAX_ATTEMPTS attempt(s)"
  for owner in "${OWNER_MIGRATION_ORDER[@]}"; do
    migration_user="$(owner_migration_user "$owner")"
    migration_password="$(owner_migration_password "$owner")"
    migrate_with_retry "$owner" "$migration_user" "$migration_password" "$owner" \
      || die "$owner Flyway migration failed after $MAX_ATTEMPTS attempt(s)"
  done
  migrate_with_retry post-owner "$MIGRATION_DB_USER" "$MIGRATION_DB_PASSWORD" "$MIGRATION_DB_NAME" \
    || die "post-owner Flyway migration failed after $MAX_ATTEMPTS attempt(s)"
  PHASE="complete"
}

validate_manifest
case "$COMMAND" in
  validate)
    PHASE="validated"
    ;;
  rollback)
    PHASE="rollback-compatibility"
    printf '[owner-migration] rollback compatibility requires host-deploy skip_migrations=true; schema is unchanged\n' \
      | tee -a "$HUMAN_REPORT"
    ;;
  migrate)
    run_migrations
    ;;
esac

exit 0
