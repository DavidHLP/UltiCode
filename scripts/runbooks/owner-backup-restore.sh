#!/usr/bin/env bash
set -euo pipefail

# P2-BACKUP-001: external Ops backup boundary for the shared control schema
# plus the five data-owner schemas. The Admin HTTP backup API remains a
# service-local compatibility surface; this runbook is the complete restore
# artifact used for owner data and migration metadata.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
ACTION="${1:-backup}"

case "$ACTION" in
  backup|verify|restore-drill|prune) ;;
  *)
    echo "Usage: $0 {backup|verify|restore-drill|prune}" >&2
    exit 2
    ;;
esac

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"
capture_env_vars \
  BACKUP_DB_HOST BACKUP_DB_PORT BACKUP_DB_NAME BACKUP_DB_USER BACKUP_DB_PASSWORD \
  BACKUP_ENCRYPTION_KEY BACKUP_DIR OWNER_BACKUP_DIR OWNER_BACKUP_REPORT_DIR \
  OWNER_BACKUP_LOCK_FILE OWNER_BACKUP_RETENTION_DAYS BACKUP_MYSQL_CONTAINER \
  BACKUP_MYSQL_CONTAINER_PORT BACKUP_MYSQL_BIN BACKUP_MYSQLDUMP_BIN \
  BACKUP_FLYWAY_IMAGE DOCKER_BIN OWNER_BACKUP_MANIFEST
load_env_file
apply_env_overrides

OWNER_SCHEMAS=(auth admin app notification submission)
ALL_SCHEMAS=(ulticode auth admin app notification submission)
RUNTIME_USERS=("${DB_USER:-}" "${AUTH_DB_USER:-}" "${ADMIN_DB_USER:-}" "${APP_DB_USER:-}" "${NOTIFICATION_DB_USER:-}" "${SUBMISSION_DB_USER:-}")
BACKUP_DIR="${OWNER_BACKUP_DIR:-${BACKUP_DIR:-$ROOT_DIR/.local/owner-backups}}"
REPORT_DIR="${OWNER_BACKUP_REPORT_DIR:-$BACKUP_DIR/reports}"
LOCK_FILE="${OWNER_BACKUP_LOCK_FILE:-$BACKUP_DIR/owner-backup.lock}"
RETENTION_DAYS="${OWNER_BACKUP_RETENTION_DAYS:-30}"
DB_HOST="${BACKUP_DB_HOST:-127.0.0.1}"
DB_PORT="${BACKUP_DB_PORT:-3306}"
DB_NAME="${BACKUP_DB_NAME:-ulticode}"
DB_USER="${BACKUP_DB_USER:-}"
DB_PASSWORD="${BACKUP_DB_PASSWORD:-}"
MYSQL_CONTAINER="${BACKUP_MYSQL_CONTAINER:-}"
MYSQL_CONTAINER_PORT="${BACKUP_MYSQL_CONTAINER_PORT:-3306}"
MYSQL_BIN="${BACKUP_MYSQL_BIN:-mysql}"
MYSQLDUMP_BIN="${BACKUP_MYSQLDUMP_BIN:-mysqldump}"
FLYWAY_IMAGE="${BACKUP_FLYWAY_IMAGE:-flyway/flyway:10.17.0}"
DOCKER_BIN="${DOCKER_BIN:-docker}"
ENCRYPTION_KEY="${BACKUP_ENCRYPTION_KEY:-}"
MANIFEST_PATH="${OWNER_BACKUP_MANIFEST:-}"
RUNTIME_TMP=""
DRILL_CONTAINER=""

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  printf '%s' "$value"
}

die() {
  echo "[owner-backup] FAIL: $*" >&2
  exit 1
}

require_backup_inputs() {
  for variable in DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD; do
    [[ -n "${!variable:-}" ]] || die "BACKUP_$variable is required"
  done
  valid_identifier "$DB_NAME" || die "invalid BACKUP_DB_NAME"
  valid_identifier "$DB_USER" || die "invalid BACKUP_DB_USER"
  valid_port "$DB_PORT" || die "invalid BACKUP_DB_PORT"
  [[ "$DB_NAME" == "ulticode" ]] || die "BACKUP_DB_NAME must be ulticode"
  local runtime_user
  for runtime_user in "${RUNTIME_USERS[@]}"; do
    [[ -z "$runtime_user" || "$DB_USER" != "$runtime_user" ]] \
      || die "BACKUP_DB_USER must differ from every runtime owner account"
  done
  if [[ -n "$MYSQL_CONTAINER" ]]; then
    valid_container_ref "$MYSQL_CONTAINER" || die "invalid BACKUP_MYSQL_CONTAINER"
    valid_port "$MYSQL_CONTAINER_PORT" || die "invalid BACKUP_MYSQL_CONTAINER_PORT"
    command -v "$DOCKER_BIN" >/dev/null 2>&1 || die "docker is required for container-backed backup"
    container_running "$MYSQL_CONTAINER" || die "MySQL container is not running: $MYSQL_CONTAINER"
  else
    command -v "$MYSQL_BIN" >/dev/null 2>&1 || die "mysql is required for host-backed backup"
    command -v "$MYSQLDUMP_BIN" >/dev/null 2>&1 || die "mysqldump is required for host-backed backup"
  fi
}

require_encryption_key() {
  [[ "$ENCRYPTION_KEY" =~ ^[A-Fa-f0-9]{64}$ ]] \
    || die "BACKUP_ENCRYPTION_KEY must be a 64-hex-character (32-byte) key"
}

validate_retention() {
  [[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]] && (( RETENTION_DAYS >= 1 && RETENTION_DAYS <= 3650 )) \
    || die "OWNER_BACKUP_RETENTION_DAYS must be between 1 and 3650"
}

ensure_runtime_tools() {
  for command in flock openssl sha256sum tar date mktemp awk sed find sort; do
    command -v "$command" >/dev/null 2>&1 || die "required command not found: $command"
  done
  if [[ -n "$MYSQL_CONTAINER" ]]; then
    command -v "$DOCKER_BIN" >/dev/null 2>&1 || die "docker is required"
  fi
}

mysql_query_at() {
  local container="$1" container_port="$2" host="$3" port="$4"
  local user="$5" password="$6" database="$7" sql="$8"
  if [[ -n "$container" ]]; then
    local -a command=("$DOCKER_BIN" exec -e "MYSQL_PWD=$password" "$container" "$MYSQL_BIN"
      --protocol=tcp -h 127.0.0.1 -P "$container_port" --batch --skip-column-names -u "$user")
    [[ -n "$database" ]] && command+=("$database")
    command+=(-e "$sql")
    "${command[@]}"
  else
    local -a command=("$MYSQL_BIN" --protocol=tcp -h "$host" -P "$port"
      --batch --skip-column-names -u "$user")
    [[ -n "$database" ]] && command+=("$database")
    command+=(-e "$sql")
    MYSQL_PWD="$password" "${command[@]}"
  fi
}

mysql_query() {
  mysql_query_at "$MYSQL_CONTAINER" "$MYSQL_CONTAINER_PORT" "$DB_HOST" "$DB_PORT" \
    "$DB_USER" "$DB_PASSWORD" "$1" "$2"
}

mysqldump_at() {
  local container="$1" container_port="$2" host="$3" port="$4"
  local user="$5" password="$6" schema="$7"
  local -a command=("$MYSQLDUMP_BIN" --default-character-set=utf8mb4
    --single-transaction --routines --triggers --hex-blob --no-tablespaces --databases "$schema")
  if [[ -n "$container" ]]; then
    "$DOCKER_BIN" exec -e "MYSQL_PWD=$password" "$container" \
      "${command[@]}"
  else
    MYSQL_PWD="$password" "$MYSQLDUMP_BIN" \
      --host="$host" --port="$port" --user="$user" \
      --default-character-set=utf8mb4 --single-transaction --routines --triggers --no-tablespaces \
      --hex-blob --databases "$schema"
  fi
}

acquire_lock() {
  mkdir -p "$(dirname "$LOCK_FILE")"
  local lock_fd
  exec {lock_fd}>"$LOCK_FILE" || die "cannot open lock: $LOCK_FILE"
  if ! flock -n "$lock_fd"; then
    echo "[owner-backup] SKIPPED: another run holds $LOCK_FILE" >&2
    exit 75
  fi
  printf '[owner-backup] acquired lock=%s\n' "$LOCK_FILE"
}

cleanup_runtime() {
  if [[ -n "$DRILL_CONTAINER" ]]; then
    "$DOCKER_BIN" rm -f "$DRILL_CONTAINER" >/dev/null 2>&1 || true
  fi
  [[ -z "$RUNTIME_TMP" ]] || rm -rf -- "$RUNTIME_TMP"
}
trap cleanup_runtime EXIT

history_snapshot() {
  local output_file="$1" schema history table_count row_count max_rank max_version
  printf 'schema\thistory_table\trows\tmax_rank\tmax_version\n' > "$output_file"
  for schema in "${ALL_SCHEMAS[@]}"; do
    local histories=(flyway_schema_history)
    [[ "$schema" == "ulticode" ]] && histories+=(flyway_post_owner_history)
    for history in "${histories[@]}"; do
      table_count="$(mysql_query "$schema" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$schema' AND table_name='$history';")"
      if [[ "$table_count" != "1" ]]; then
        printf '%s\t%s\tMISSING\tMISSING\tMISSING\n' "$schema" "$history" >> "$output_file"
        continue
      fi
      row_count="$(mysql_query "$schema" "SELECT COUNT(*) FROM \`$history\`;")"
      max_rank="$(mysql_query "$schema" "SELECT COALESCE(MAX(installed_rank), 0) FROM \`$history\`;")"
      max_version="$(mysql_query "$schema" "SELECT COALESCE(MAX(version), '') FROM \`$history\`;")"
      printf '%s\t%s\t%s\t%s\t%s\n' "$schema" "$history" "$row_count" "$max_rank" "$max_version" >> "$output_file"
    done
  done
}

table_checksum_snapshot() {
  local output_file="$1" schema table rows raw_checksum checksum
  printf 'schema\ttable\trows\tchecksum\n' > "$output_file"
  for schema in "${ALL_SCHEMAS[@]}"; do
    while IFS= read -r table; do
      [[ -n "$table" ]] || continue
      [[ "$table" =~ ^[A-Za-z0-9_]+$ ]] || die "unsafe table name from MySQL metadata"
      rows="$(mysql_query "$schema" "SELECT COUNT(*) FROM \`$table\`;")"
      raw_checksum="$(mysql_query "$schema" "CHECKSUM TABLE \`$table\`;")" \
        || die "cannot checksum $schema.$table"
      checksum="$(awk 'NF == 2 && $2 ~ /^[0-9]+$/ { print $2; found=1 } END { if (!found) exit 1 }' <<< "$raw_checksum")" \
        || die "MySQL returned no numeric checksum for $schema.$table"
      printf '%s\t%s\t%s\t%s\n' "$schema" "$table" "$rows" "$checksum" >> "$output_file"
    done < <(mysql_query "$schema" "SELECT table_name FROM information_schema.tables WHERE table_schema='$schema' AND table_type='BASE TABLE' ORDER BY table_name;")
  done
}

write_backup_manifest() {
  local manifest_file="$1" run_id="$2" started_epoch="$3" completed_epoch="$4"
  local archive_name="$5" archive_sha="$6" archive_bytes="$7" table_count="$8" metadata_file="$9"
  local started_at completed_at
  started_at="$(date -u -d "@$started_epoch" '+%Y-%m-%dT%H:%M:%SZ')"
  completed_at="$(date -u -d "@$completed_epoch" '+%Y-%m-%dT%H:%M:%SZ')"
  {
    printf '{\n'
    printf '  "format_version": "1",\n'
    printf '  "run_id": "%s",\n' "$(json_escape "$run_id")"
    printf '  "started_at": "%s",\n' "$started_at"
    printf '  "completed_at": "%s",\n' "$completed_at"
    printf '  "captured_at_epoch": "%s",\n' "$completed_epoch"
    printf '  "archive": "%s",\n' "$(json_escape "$archive_name")"
    printf '  "archive_sha256": "%s",\n' "$archive_sha"
    printf '  "archive_bytes": "%s",\n' "$archive_bytes"
    printf '  "encryption": "openssl enc aes-256-cbc salt pbkdf2",\n'
    printf '  "retention_days": "%s",\n' "$RETENTION_DAYS"
    printf '  "owner_schemas": ["auth", "admin", "app", "notification", "submission"],\n'
    printf '  "control_schema": "ulticode",\n'
    printf '  "table_checksum_count": "%s",\n' "$table_count"
    printf '  "migration_metadata": "%s",\n' "$(json_escape "$metadata_file")"
    printf '  "backup_duration_seconds": "%s"\n' "$((completed_epoch - started_epoch))"
    printf '}\n'
  } > "$manifest_file"
  chmod 600 "$manifest_file"
}

prune_expired_locked() {
  local manifest archive age
  while IFS= read -r manifest; do
    [[ -n "$manifest" ]] || continue
    age="$(find "$manifest" -maxdepth 0 -mtime "+$RETENTION_DAYS" -print)"
    [[ -n "$age" ]] || continue
    archive="$(sed -n 's/^[[:space:]]*"archive": "\([^"]*\)".*/\1/p' "$manifest" | head -1)"
    [[ "$archive" =~ ^owner-backup-[A-Za-z0-9T_-]+\.tar\.gz\.enc$ ]] || continue
    rm -f -- "$manifest" "$BACKUP_DIR/$archive"
  done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'owner-backup-*.json' -print | sort)
}

do_backup() {
  require_backup_inputs
  require_encryption_key
  validate_retention
  ensure_runtime_tools
  acquire_lock
  mkdir -p "$BACKUP_DIR" "$REPORT_DIR"
  local run_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
  local started_epoch="$(date +%s)" completed_epoch archive_name archive_sha archive_bytes
  local work_dir archive_plain encrypted_archive manifest_file table_count
  work_dir="$(mktemp -d "$BACKUP_DIR/.owner-backup-$run_id.XXXXXX")"
  RUNTIME_TMP="$work_dir"
  mkdir -p "$work_dir/payload/dumps"
  printf '[owner-backup] run_id=%s schemas=%s\n' "$run_id" "${ALL_SCHEMAS[*]}"

  local schema dump_file digest
  : > "$work_dir/payload/checksums.sha256"
  for schema in "${ALL_SCHEMAS[@]}"; do
    dump_file="$work_dir/payload/dumps/$schema.sql"
    if ! mysqldump_at "$MYSQL_CONTAINER" "$MYSQL_CONTAINER_PORT" "$DB_HOST" "$DB_PORT" \
        "$DB_USER" "$DB_PASSWORD" "$schema" > "$dump_file"; then
      die "mysqldump failed for schema $schema"
    fi
    [[ -s "$dump_file" ]] || die "empty dump for schema $schema"
    digest="$(sha256sum "$dump_file" | awk '{print $1}')"
    printf '%s  dumps/%s.sql\n' "$digest" "$schema" >> "$work_dir/payload/checksums.sha256"
  done
  history_snapshot "$work_dir/payload/migration-metadata.tsv"
  table_checksum_snapshot "$work_dir/payload/table-checksums.tsv"
  table_count="$(awk 'NR > 1 && NF >= 4 { count++ } END { print count + 0 }' "$work_dir/payload/table-checksums.tsv")"

  archive_name="owner-backup-$run_id.tar.gz.enc"
  archive_plain="$work_dir/$run_id.tar.gz"
  encrypted_archive="$BACKUP_DIR/$archive_name"
  tar -czf "$archive_plain" -C "$work_dir/payload" .
  if ! BACKUP_ENCRYPTION_KEY="$ENCRYPTION_KEY" openssl enc -aes-256-cbc -salt -pbkdf2 \
      -iter 200000 -in "$archive_plain" -out "$encrypted_archive" \
      -pass env:BACKUP_ENCRYPTION_KEY; then
    die "backup encryption failed"
  fi
  chmod 600 "$encrypted_archive"
  archive_sha="$(sha256sum "$encrypted_archive" | awk '{print $1}')"
  archive_bytes="$(wc -c < "$encrypted_archive")"
  completed_epoch="$(date +%s)"
  manifest_file="$BACKUP_DIR/${archive_name%.tar.gz.enc}.json"
  write_backup_manifest "$manifest_file" "$run_id" "$started_epoch" "$completed_epoch" \
    "$archive_name" "$archive_sha" "$archive_bytes" "$table_count" "migration-metadata.tsv"
  printf '[owner-backup] PASS archive=%s manifest=%s tables=%s duration_seconds=%s\n' \
    "$encrypted_archive" "$manifest_file" "$table_count" "$((completed_epoch - started_epoch))"
  prune_expired_locked
}

manifest_value() {
  local key="$1" file="$2"
  sed -n "s/^[[:space:]]*\"$key\": \"\([^\"]*\)\"[,]*$/\1/p" "$file" | head -1
}

select_manifest() {
  if [[ -z "$MANIFEST_PATH" ]]; then
    MANIFEST_PATH="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'owner-backup-*.json' -print | sort | tail -1)"
  fi
  [[ -n "$MANIFEST_PATH" && -f "$MANIFEST_PATH" ]] || die "backup manifest not found"
  [[ "$(manifest_value format_version "$MANIFEST_PATH")" == "1" ]] \
    || die "unsupported backup manifest format"
}

prepare_archive() {
  local archive_name archive_sha actual_sha entry
  select_manifest
  archive_name="$(manifest_value archive "$MANIFEST_PATH")"
  archive_sha="$(manifest_value archive_sha256 "$MANIFEST_PATH")"
  [[ "$archive_name" =~ ^owner-backup-[A-Za-z0-9T_-]+\.tar\.gz\.enc$ ]] \
    || die "manifest archive name is invalid"
  [[ "$archive_sha" =~ ^[A-Fa-f0-9]{64}$ ]] || die "manifest archive checksum is invalid"
  local archive_path="$BACKUP_DIR/$archive_name"
  [[ -f "$archive_path" ]] || die "encrypted archive not found: $archive_path"
  actual_sha="$(sha256sum "$archive_path" | awk '{print $1}')"
  [[ "$actual_sha" == "$archive_sha" ]] || die "encrypted archive checksum mismatch"
  RUNTIME_TMP="$(mktemp -d "$BACKUP_DIR/.owner-restore.XXXXXX")"
  local archive_plain="$RUNTIME_TMP/archive.tar.gz"
  BACKUP_ENCRYPTION_KEY="$ENCRYPTION_KEY" openssl enc -d -aes-256-cbc -pbkdf2 \
    -iter 200000 -in "$archive_path" -out "$archive_plain" \
    -pass env:BACKUP_ENCRYPTION_KEY >/dev/null 2>&1 \
    || die "backup decryption failed"
  while IFS= read -r entry; do
    [[ "$entry" != /* && "$entry" != *..* ]] || die "unsafe archive entry: $entry"
  done < <(tar -tzf "$archive_plain")
  mkdir -p "$RUNTIME_TMP/unpacked"
  tar -xzf "$archive_plain" -C "$RUNTIME_TMP/unpacked" --no-same-owner --no-same-permissions
  [[ -f "$RUNTIME_TMP/unpacked/checksums.sha256" ]] || die "archive checksum list is missing"
  [[ -f "$RUNTIME_TMP/unpacked/table-checksums.tsv" ]] || die "table checksum snapshot is missing"
  [[ -f "$RUNTIME_TMP/unpacked/migration-metadata.tsv" ]] || die "migration metadata is missing"
  (cd "$RUNTIME_TMP/unpacked" && sha256sum --strict -c checksums.sha256) >/dev/null \
    || die "dump checksum verification failed"
  local schema
  for schema in "${ALL_SCHEMAS[@]}"; do
    [[ -s "$RUNTIME_TMP/unpacked/dumps/$schema.sql" ]] || die "archive dump missing: $schema"
  done
}

do_verify() {
  require_encryption_key
  validate_retention
  ensure_runtime_tools
  prepare_archive
  printf '[owner-backup] PASS verified manifest=%s archive=%s\n' \
    "$MANIFEST_PATH" "$(manifest_value archive "$MANIFEST_PATH")"
}

target_query() {
  mysql_query_at "$DRILL_CONTAINER" 3306 "" "" root "$DRILL_PASSWORD" "" "$1"
}

target_table_checksum() {
  local schema="$1" table="$2" raw_checksum
  raw_checksum="$(mysql_query_at "$DRILL_CONTAINER" 3306 "" "" root "$DRILL_PASSWORD" "$schema" "CHECKSUM TABLE \`$table\`;")" \
    || return 1
  awk 'NF == 2 && $2 ~ /^[0-9]+$/ { print $2; found=1 } END { if (!found) exit 1 }' <<< "$raw_checksum"
}

run_flyway_validate() {
  local schema="$1" locations="$2" extra=()
  [[ "$schema" == "ulticode" ]] || extra+=("-defaultSchema=$schema")
  [[ "$locations" == *post-owner* ]] && extra+=("-table=flyway_post_owner_history")
  local flyway_url="jdbc:mysql://127.0.0.1:3306/$schema?allowPublicKeyRetrieval=true&useSSL=false"
  FLYWAY_URL="$flyway_url" FLYWAY_USER=root FLYWAY_PASSWORD="$DRILL_PASSWORD" \
    "$DOCKER_BIN" run --rm --network "container:$DRILL_CONTAINER" \
      -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD \
      -v "$ROOT_DIR/init-db/migrations:/flyway/sql:ro" "$FLYWAY_IMAGE" \
      "-locations=filesystem:/flyway/sql/$locations" "${extra[@]}" validate >/dev/null
}

write_restore_report() {
  local report_file="$1" status="$2" run_id="$3" started_epoch="$4" completed_epoch="$5" error="$6"
  local rto_seconds=$((completed_epoch - started_epoch))
  local captured_epoch="$(manifest_value captured_at_epoch "$MANIFEST_PATH")"
  local rpo_seconds=0
  [[ "$captured_epoch" =~ ^[0-9]+$ ]] && rpo_seconds=$((started_epoch - captured_epoch))
  (( rpo_seconds < 0 )) && rpo_seconds=0
  mkdir -p "$REPORT_DIR"
  {
    printf '{\n'
    printf '  "status": "%s",\n' "$status"
    printf '  "run_id": "%s",\n' "$run_id"
    printf '  "source_manifest": "%s",\n' "$(json_escape "$MANIFEST_PATH")"
    printf '  "started_at": "%s",\n' "$(date -u -d "@$started_epoch" '+%Y-%m-%dT%H:%M:%SZ')"
    printf '  "completed_at": "%s",\n' "$(date -u -d "@$completed_epoch" '+%Y-%m-%dT%H:%M:%SZ')"
    printf '  "rpo_seconds": "%s",\n' "$rpo_seconds"
    printf '  "rto_seconds": "%s",\n' "$rto_seconds"
    printf '  "migration_validate": "%s",\n' "$([[ "$status" == PASS ]] && printf PASS || printf FAILED)"
    printf '  "checksum_reconciliation": "%s",\n' "$([[ "$status" == PASS ]] && printf PASS || printf FAILED)"
    printf '  "smoke": "%s",\n' "$([[ "$status" == PASS ]] && printf PASS || printf FAILED)"
    printf '  "error": "%s"\n' "$(json_escape "$error")"
    printf '}\n'
  } > "$report_file"
  chmod 600 "$report_file"
}

do_restore_drill() {
  require_encryption_key
  validate_retention
  ensure_runtime_tools
  command -v "$DOCKER_BIN" >/dev/null 2>&1 || die "docker is required for restore-drill"
  acquire_lock
  prepare_archive
  local run_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
  local started_epoch="$(date +%s)" completed_epoch schema expected_rows expected_checksum actual_rows actual_checksum
  local report_file="$REPORT_DIR/owner-restore-drill-$run_id.json"
  DRILL_PASSWORD="$(openssl rand -hex 32)"
  DRILL_CONTAINER="ulticode-owner-restore-drill-$run_id"
  "$DOCKER_BIN" run -d --rm --name "$DRILL_CONTAINER" \
    -e "MYSQL_ROOT_PASSWORD=$DRILL_PASSWORD" -e MYSQL_DATABASE=ulticode \
    mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null
  for _ in $(seq 1 60); do
    if target_query 'SELECT 1' >/dev/null 2>&1; then break; fi
    sleep 1
  done
  target_query 'SELECT 1' >/dev/null || die "restore drill MySQL did not become ready"
  for schema in "${ALL_SCHEMAS[@]}"; do
    "$DOCKER_BIN" exec -i -e "MYSQL_PWD=$DRILL_PASSWORD" "$DRILL_CONTAINER" "$MYSQL_BIN" \
      --default-character-set=utf8mb4 -uroot < "$RUNTIME_TMP/unpacked/dumps/$schema.sql" >/dev/null \
      || die "restore import failed for $schema"
  done

  run_flyway_validate ulticode '*.sql'
  for schema in "${OWNER_SCHEMAS[@]}"; do
    run_flyway_validate "$schema" "$schema"
  done
  run_flyway_validate ulticode post-owner

  while IFS=$'\t' read -r schema table expected_rows expected_checksum; do
    [[ "$schema" == schema ]] && continue
    [[ -n "$schema" && -n "$table" ]] || continue
    actual_rows="$(target_query "SELECT COUNT(*) FROM \`$schema\`.\`$table\`;")"
    actual_checksum="$(target_table_checksum "$schema" "$table")" \
      || die "restore checksum unavailable for $schema.$table"
    [[ "$actual_rows" == "$expected_rows" && "$actual_checksum" == "$expected_checksum" ]] \
      || die "restore checksum mismatch for $schema.$table"
  done < "$RUNTIME_TMP/unpacked/table-checksums.tsv"
  [[ "$(target_query "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('ulticode','auth','admin','app','notification','submission');")" == "6" ]] \
    || die "restore smoke schema count mismatch"
  target_query 'SELECT 1' >/dev/null || die "restore smoke query failed"
  completed_epoch="$(date +%s)"
  write_restore_report "$report_file" PASS "$run_id" "$started_epoch" "$completed_epoch" ""
  printf '[owner-backup] PASS restore-drill report=%s rpo_seconds=%s rto_seconds=%s\n' \
    "$report_file" "$(sed -n 's/^[[:space:]]*"rpo_seconds": "\([0-9]*\)".*/\1/p' "$report_file")" \
    "$(sed -n 's/^[[:space:]]*"rto_seconds": "\([0-9]*\)".*/\1/p' "$report_file")"
}

do_prune() {
  validate_retention
  ensure_runtime_tools
  mkdir -p "$BACKUP_DIR"
  acquire_lock
  prune_expired_locked
  printf '[owner-backup] PASS prune retention_days=%s directory=%s\n' "$RETENTION_DAYS" "$BACKUP_DIR"
}

case "$ACTION" in
  backup) do_backup ;;
  verify) do_verify ;;
  restore-drill) do_restore_drill ;;
  prune) do_prune ;;
esac
