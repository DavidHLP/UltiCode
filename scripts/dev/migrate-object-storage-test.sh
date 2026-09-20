#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -r -- "$TMP_DIR"' EXIT

BIN="$TMP_DIR/bin"
AVATARS="$TMP_DIR/avatars"
BACKUPS="$TMP_DIR/backups"
OBJECTS="$TMP_DIR/objects"
AWS_LOG="$TMP_DIR/aws.log"
AWS_ENV_LOG="$TMP_DIR/aws-env.log"
DOCKER_LOG="$TMP_DIR/docker.log"
MYSQL_LOG="$TMP_DIR/mysql.log"
MYSQL_DB_LOG="$TMP_DIR/mysql-db.log"
DB_STATE="$TMP_DIR/db-state"
ENV_FILE="$TMP_DIR/.env"
CA_FILE="$TMP_DIR/rustfs-ca.pem"
mkdir -p "$BIN" "$AVATARS" "$BACKUPS" "$OBJECTS" "$DB_STATE"
AVATAR_VOLUME="$TMP_DIR/avatar-volume"
BACKUP_VOLUME_DIR="$TMP_DIR/backup-volume"
mkdir -p "$AVATAR_VOLUME/uploads/avatars" "$BACKUP_VOLUME_DIR"
printf 'avatar-data' >"$AVATAR_VOLUME/uploads/avatars/avatar.png"
printf 'dump' >"$BACKUP_VOLUME_DIR/backup_FULL_20260920_120000.sql"
: >"$AWS_LOG"
: >"$AWS_ENV_LOG"
: >"$MYSQL_LOG"
: >"$MYSQL_DB_LOG"
: >"$DOCKER_LOG"
printf '%s\n' '-----BEGIN CERTIFICATE-----' 'fake' '-----END CERTIFICATE-----' >"$CA_FILE"
printf 'avatar-data' >"$AVATARS/avatar.png"
printf 'dump' >"$BACKUPS/backup_FULL_20260920_120000.sql"
BACKUP_SIZE="$(stat -c '%s' "$BACKUPS/backup_FULL_20260920_120000.sql")"

cat >"$ENV_FILE" <<EOF_ENV
APP_STORAGE_S3_ENDPOINT=http://127.0.0.1:9000
APP_STORAGE_S3_REGION=us-east-1
APP_STORAGE_S3_TLS_ENABLED=false
APP_STORAGE_S3_ACCESS_KEY=test-access
APP_STORAGE_S3_SECRET_KEY=test-secret
RUSTFS_BUCKET=ulticode
APP_DB_HOST=127.0.0.1
APP_DB_PORT=3306
APP_DB_NAME=app
APP_DB_USER=test
APP_DB_PASSWORD=test
ADMIN_DB_HOST=127.0.0.1
ADMIN_DB_PORT=3306
ADMIN_DB_NAME=admin
ADMIN_DB_USER=test
ADMIN_DB_PASSWORD=test
EOF_ENV

cat >"$BIN/mysql" <<'FAKE_MYSQL'
#!/usr/bin/env bash
set -euo pipefail
sql=""
database=""
while (($#)); do
  case "$1" in
    -e) sql="${2:-}"; shift 2 ;;
    -h|-P|-u) shift 2 ;;
    --protocol=tcp|--default-character-set=*|--batch|--raw|--skip-column-names) shift ;;
    *) database="${database:-$1}"; shift ;;
  esac
done
printf 'DATABASE=%s\n' "$database" >>"$FAKE_MYSQL_DB_LOG"
printf '%s\n' "$sql" >>"$FAKE_MYSQL_LOG"
if [[ "$sql" == *"UPDATE user_profiles"* ]]; then
  : >"$FAKE_DB_STATE/avatar"
  printf '1\n'
elif [[ "$sql" == *"UPDATE backups"* ]]; then
  : >"$FAKE_DB_STATE/backup"
  printf '1\n'
elif [[ "$sql" == *"information_schema.COLUMNS"* ]]; then
  # The tool preflights backups.object_key AND backups.checksum together.
  printf '2\n'
elif [[ "$sql" == *"ROW_COUNT"* ]]; then
  printf '1\n'
elif [[ "$sql" == *"FROM user_profiles"* ]]; then
  [[ -f "$FAKE_DB_STATE/avatar" ]] || printf 'acct-1\t/uploads/avatars/avatar.png\n'
elif [[ "$sql" == *"FROM backups"* ]]; then
  [[ -f "$FAKE_DB_STATE/backup" ]] || printf 'backup-1\tbackup_FULL_20260920_120000.sql\t%s\tCOMPLETED\t2026-09-20\n' "$FAKE_BACKUP_SIZE"
fi
FAKE_MYSQL
chmod +x "$BIN/mysql"

cat >"$BIN/aws" <<'FAKE_AWS'
#!/usr/bin/env bash
set -euo pipefail
if [[ -v AWS_CA_BUNDLE ]]; then
  printf 'AWS_CA_BUNDLE=%s\n' "$AWS_CA_BUNDLE" >>"$FAKE_AWS_ENV_LOG"
else
  printf 'AWS_CA_BUNDLE=UNSET\n' >>"$FAKE_AWS_ENV_LOG"
fi
printf '%q ' "$@" >>"$FAKE_AWS_LOG"
printf '\n' >>"$FAKE_AWS_LOG"
service=""
operation=""
while (($#)); do
  if [[ "$1" == s3api || "$1" == s3 ]]; then
    service="$1"; operation="${2:-}"; shift 2; break
  fi
  shift
done
key=""
body=""
while (($#)); do
  case "$1" in
    --key) key="${2:-}"; shift 2 ;;
    --body) body="${2:-}"; shift 2 ;;
    --bucket) shift 2 ;;
    --query|--output) shift 2 ;;
    --only-show-errors) shift ;;
    s3://*)
      key="${1#s3://ulticode/}"; shift ;;
    *) shift ;;
  esac
done
object="$FAKE_OBJECTS/${key//\//__}"
case "$service:$operation" in
  s3api:head-object)
    [[ -f "$object" ]] || exit 1
    size="$(stat -c '%s' "$object")"
    if [[ "${FAKE_ETAG_MODE:-}" == multipart ]]; then
      etag="$(md5sum "$object" | awk '{print $1}')-2"
    else
      etag="$(md5sum "$object" | awk '{print $1}')"
    fi
    printf '%s "%s"\n' "$size" "$etag"
    ;;
  s3api:put-object)
    mkdir -p "$FAKE_OBJECTS"
    cp -- "$body" "$object"
    ;;
  s3:cp)
    if [[ "${FAKE_VERIFY_FAIL:-0}" == 1 ]]; then
      printf 'bad-readback'
    else
      cat -- "$object"
    fi
    ;;
  *)
    echo "unexpected fake aws operation: $service:$operation" >&2
    exit 2
    ;;
esac
FAKE_AWS
chmod +x "$BIN/aws"

cat >"$BIN/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf '%q ' "$@" >>"$FAKE_DOCKER_LOG"
printf '\n' >>"$FAKE_DOCKER_LOG"
if [[ "${1:-}" == network && "${2:-}" == inspect ]]; then
  [[ "${FAKE_DOCKER_NETWORK:-}" == "${3:-}" ]] || { echo "unexpected fake docker network: ${3:-}" >&2; exit 1; }
  exit 0
fi
if [[ "${1:-}" == volume && "${2:-}" == ls ]]; then
  if [[ "${FAKE_USE_LABELS:-0}" == 1 ]]; then
    if [[ "$*" == *"label=com.docker.compose.volume=app_uploads"* ]]; then
      printf '%s\n' "$FAKE_LABELLED_AVATAR_VOLUME_NAME"
    elif [[ "$*" == *"label=com.docker.compose.volume=backup_data"* ]]; then
      printf '%s\n' "$FAKE_LABELLED_BACKUP_VOLUME_NAME"
    fi
  fi
  exit 0
fi
if [[ "${1:-}" == volume && "${2:-}" == inspect ]]; then
  volume="${!#}"
  if [[ "$volume" == "${FAKE_AVATAR_VOLUME_NAME:-}" || "$volume" == "${FAKE_LABELLED_AVATAR_VOLUME_NAME:-}" ]]; then
    [[ -n "${FAKE_AVATAR_VOLUME_MOUNTPOINT:-}" ]] || exit 1
    printf '%s\n' "$FAKE_AVATAR_VOLUME_MOUNTPOINT"
    exit 0
  fi
  if [[ "$volume" == "${FAKE_BACKUP_VOLUME_NAME:-}" || "$volume" == "${FAKE_LABELLED_BACKUP_VOLUME_NAME:-}" ]]; then
    [[ -n "${FAKE_BACKUP_VOLUME_MOUNTPOINT:-}" ]] || exit 1
    printf '%s\n' "$FAKE_BACKUP_VOLUME_MOUNTPOINT"
    exit 0
  fi
  exit 1
fi
[[ "${1:-}" == run ]] || { echo "unexpected fake docker command" >&2; exit 2; }
shift
mounts=()
while (($#)); do
  case "$1" in
    --rm) shift ;;
    --network) shift 2 ;;
    -e) export "$2"; shift 2 ;;
    -v) mounts+=("$2"); shift 2 ;;
    *) shift; break ;;
  esac
done
aws_args=()
while (($#)); do
  if [[ "$1" == --body && $# -ge 2 ]]; then
    body="$2"
    for mount in "${mounts[@]}"; do
      IFS=: read -r host_path container_path _ <<<"$mount"
      if [[ "$body" == "$container_path"/* ]]; then
        body="$host_path/${body#"$container_path"/}"
        break
      fi
    done
    aws_args+=(--body "$body")
    shift 2
  else
    aws_args+=("$1")
    shift
  fi
done
"$FAKE_AWS_BIN" "${aws_args[@]}"
FAKE_DOCKER
chmod +x "$BIN/docker"

export FAKE_DOCKER_LOG="$DOCKER_LOG" FAKE_AWS_BIN="$BIN/aws"
export FAKE_AVATAR_VOLUME_NAME=legacy-app-upload FAKE_AVATAR_VOLUME_MOUNTPOINT="$AVATAR_VOLUME"
export FAKE_BACKUP_VOLUME_NAME=legacy-backup-data FAKE_BACKUP_VOLUME_MOUNTPOINT="$BACKUP_VOLUME_DIR"
export PATH="$BIN:$PATH"
export ENV_FILE AWS_BIN="$BIN/aws"
export FAKE_AWS_LOG="$AWS_LOG" FAKE_AWS_ENV_LOG="$AWS_ENV_LOG"
export FAKE_MYSQL_LOG="$MYSQL_LOG" FAKE_MYSQL_DB_LOG="$MYSQL_DB_LOG"
export FAKE_OBJECTS="$OBJECTS" FAKE_DB_STATE="$DB_STATE" FAKE_BACKUP_SIZE="$BACKUP_SIZE"
unset AWS_CA_BUNDLE APP_STORAGE_S3_CA_CERTIFICATE RUSTFS_TLS_CA_CERT

assert_contains() {
  local haystack="$1" needle="$2"
  [[ "$haystack" == *"$needle"* ]] || { echo "missing expected text: $needle" >&2; exit 1; }
}
assert_not_contains() {
  local haystack="$1" needle="$2"
  [[ "$haystack" != *"$needle"* ]] || { echo "unexpected text: $needle" >&2; exit 1; }
}
run_migration() {
  "$ROOT_DIR/scripts/dev/migrate-object-storage.sh" \
    --legacy-avatar-dir "$AVATARS" --legacy-backup-dir "$BACKUPS" "$@"
}
run_migration_without_explicit_dirs() {
  "$ROOT_DIR/scripts/dev/migrate-object-storage.sh" "$@"
}

FALLBACK_ENV_FILE="$TMP_DIR/fallback.env"
cat >"$FALLBACK_ENV_FILE" <<EOF_FALLBACK
APP_STORAGE_S3_ENDPOINT=http://127.0.0.1:9000
APP_STORAGE_S3_REGION=us-east-1
APP_STORAGE_S3_TLS_ENABLED=false
RUSTFS_ACCESS_KEY=test-access
RUSTFS_SECRET_KEY=test-secret
RUSTFS_BUCKET=ulticode
MIGRATION_DB_HOST=127.0.0.1
MIGRATION_DB_PORT=3306
MIGRATION_DB_NAME=ulticode
MIGRATION_DB_USER=test
MIGRATION_DB_PASSWORD=test
EOF_FALLBACK
ORIGINAL_ENV_FILE="$ENV_FILE"
ENV_FILE="$FALLBACK_ENV_FILE"
: >"$MYSQL_DB_LOG"
fallback_avatar_output="$(run_migration --only avatars 2>&1)"
assert_contains "$fallback_avatar_output" "MIGRATION_SUMMARY total=1"
assert_contains "$(<"$MYSQL_DB_LOG")" "DATABASE=app"
assert_not_contains "$(<"$MYSQL_DB_LOG")" "DATABASE=ulticode"
: >"$MYSQL_DB_LOG"
fallback_backup_output="$(run_migration --only backups 2>&1)"
assert_contains "$fallback_backup_output" "MIGRATION_SUMMARY total=1"
assert_contains "$(<"$MYSQL_DB_LOG")" "DATABASE=admin"
assert_not_contains "$(<"$MYSQL_DB_LOG")" "DATABASE=ulticode"
ENV_FILE="$ORIGINAL_ENV_FILE"

# Dry-run must read the plan but never put an object or update a row.
: >"$AWS_LOG"; : >"$MYSQL_LOG"; rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
dry_run_output="$(run_migration 2>&1)"
assert_contains "$dry_run_output" "PLAN type=avatar"
assert_contains "$dry_run_output" "PLAN type=backup"
assert_contains "$dry_run_output" "MIGRATION_SUMMARY total=2 uploaded=0"
assert_not_contains "$(<"$AWS_LOG")" "put-object"
assert_not_contains "$(<"$MYSQL_LOG")" "UPDATE"
assert_contains "$(<"$AWS_ENV_LOG")" "AWS_CA_BUNDLE=UNSET"

# A configured PEM is passed to the host AWS client through AWS_CA_BUNDLE.
: >"$AWS_ENV_LOG"
export APP_STORAGE_S3_CA_CERTIFICATE="$CA_FILE"
ca_output="$(run_migration --only avatars --limit 1 2>&1)"
assert_contains "$ca_output" "MIGRATION_SUMMARY total=1"
assert_contains "$(<"$AWS_ENV_LOG")" "AWS_CA_BUNDLE=$CA_FILE"
unset APP_STORAGE_S3_CA_CERTIFICATE

# Avatar DB updates must not be declared complete until the users-index
# backfill has run through the application-owned Search outbox path.
: >"$AWS_LOG"; : >"$MYSQL_LOG"; rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
unset MIGRATION_SEARCH_BACKFILL_CONFIRMED
search_gate_output=""
search_gate_status=0
search_gate_output="$(run_migration --apply --only avatars 2>&1)" || search_gate_status=$?
[[ "$search_gate_status" -ne 0 ]] || { echo 'search backfill gate unexpectedly passed' >&2; exit 1; }
assert_contains "$search_gate_output" "search_backfill=required"
assert_contains "$search_gate_output" "users-index backfill required"
export MIGRATION_SEARCH_BACKFILL_CONFIRMED=true

# An existing object with matching size and ETag is reused; no duplicate put.
: >"$AWS_LOG"; : >"$MYSQL_LOG"; rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
key_path="$OBJECTS/app__avatars__acct-1__avatar.png"
cp -- "$AVATARS/avatar.png" "$key_path"
existing_output="$(run_migration --apply --only avatars 2>&1)"
assert_contains "$existing_output" "VERIFIED avatar account=acct-1"
assert_contains "$existing_output" "uploaded=0"
assert_not_contains "$(<"$AWS_LOG")" "put-object"
assert_contains "$(<"$MYSQL_LOG")" "UPDATE user_profiles"

# Multipart ETags are inconclusive and must trigger a repair, then SHA-256 verifies it.
: >"$AWS_LOG"; : >"$MYSQL_LOG"; rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
key_path="$OBJECTS/app__avatars__acct-1__avatar.png"
printf 'stale-data!' >"$key_path"
export FAKE_ETAG_MODE=multipart
multipart_output="$(run_migration --apply --only avatars 2>&1)"
unset FAKE_ETAG_MODE
assert_contains "$multipart_output" "VERIFIED avatar account=acct-1"
assert_contains "$multipart_output" "uploaded=1"
cmp -- "$AVATARS/avatar.png" "$key_path"


# Read-back verification failure must leave the legacy DB row untouched.
: >"$AWS_LOG"; : >"$MYSQL_LOG"; rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
export FAKE_VERIFY_FAIL=1
verification_output=""
verification_status=0
verification_output="$(run_migration --apply --only avatars 2>&1)" || verification_status=$?
unset FAKE_VERIFY_FAIL
[[ "$verification_status" -ne 0 ]] || { echo 'verification failure unexpectedly succeeded' >&2; exit 1; }
assert_contains "$verification_output" "verification failed"
assert_contains "$verification_output" "MIGRATION_SUMMARY"
assert_not_contains "$(<"$MYSQL_LOG")" "UPDATE user_profiles"

# --only and --limit select one backup row and do not even query avatars.
: >"$AWS_LOG"; : >"$MYSQL_LOG"; rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
filter_output="$(run_migration --apply --only backups --limit 1 2>&1)"
assert_contains "$filter_output" "MIGRATION_SUMMARY total=1"
assert_contains "$filter_output" "uploaded=1"
assert_not_contains "$(<"$MYSQL_LOG")" "FROM user_profiles"
assert_contains "$(<"$MYSQL_LOG")" "FROM backups"
assert_contains "$(<"$MYSQL_LOG")" "LIMIT 1"

# --only all pushes the remaining row limit into the avatar query and skips
# the backup query once the shared limit is consumed.
: >"$MYSQL_LOG"; rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
all_limit_output="$(run_migration --only all --limit 1 2>&1)"
assert_contains "$all_limit_output" "MIGRATION_SUMMARY total=1"
all_limit_mysql_log="$(<"$MYSQL_LOG")"
assert_contains "$all_limit_mysql_log" "FROM user_profiles"
assert_contains "$all_limit_mysql_log" "LIMIT 1"
assert_not_contains "$all_limit_mysql_log" "FROM backups"

# Historical production volumes resolve to host mountpoints before migration;
# app_uploads may contain uploads/avatars or avatars beneath the volume root.
: >"$AWS_LOG"; : >"$DOCKER_LOG"; : >"$MYSQL_LOG"
rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
export AVATAR_UPLOAD_VOL=legacy-app-upload BACKUP_VOLUME=legacy-backup-data
volume_output="$(run_migration_without_explicit_dirs --apply 2>&1)"
assert_contains "$volume_output" "Using legacy avatar volume source: $AVATAR_VOLUME/uploads/avatars"
assert_contains "$volume_output" "Using legacy backup volume source: $BACKUP_VOLUME_DIR"
assert_contains "$volume_output" "MIGRATION_SUMMARY total=2 uploaded=2"
volume_docker_log="$(<"$DOCKER_LOG")"
assert_contains "$volume_docker_log" "volume inspect legacy-app-upload"
assert_contains "$volume_docker_log" "volume inspect legacy-backup-data"
unset AVATAR_UPLOAD_VOL BACKUP_VOLUME

# Compose labels find project-scoped volumes even when the checkout name differs.
: >"$AWS_LOG"; : >"$DOCKER_LOG"; : >"$MYSQL_LOG"
rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
export COMPOSE_PROJECT_NAME=legacy-prod FAKE_USE_LABELS=1
export FAKE_LABELLED_AVATAR_VOLUME_NAME=legacy-prod_app_uploads
export FAKE_LABELLED_BACKUP_VOLUME_NAME=legacy-prod_backup_data
labeled_volume_output="$(run_migration_without_explicit_dirs --apply 2>&1)"
assert_contains "$labeled_volume_output" "Using legacy avatar volume source: $AVATAR_VOLUME/uploads/avatars"
assert_contains "$labeled_volume_output" "Using legacy backup volume source: $BACKUP_VOLUME_DIR"
assert_contains "$labeled_volume_output" "MIGRATION_SUMMARY total=2 uploaded=2"
labeled_volume_docker_log="$(<"$DOCKER_LOG")"
assert_contains "$labeled_volume_docker_log" "volume inspect legacy-prod_app_uploads"
assert_contains "$labeled_volume_docker_log" "volume inspect legacy-prod_backup_data"
unset COMPOSE_PROJECT_NAME FAKE_USE_LABELS FAKE_LABELLED_AVATAR_VOLUME_NAME FAKE_LABELLED_BACKUP_VOLUME_NAME

# Docker fallback mounts both legacy directories and translates body paths.
: >"$AWS_LOG"; : >"$DOCKER_LOG"; : >"$MYSQL_LOG"
rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
export AWS_BIN="$TMP_DIR/missing-aws"
fallback_output="$(run_migration --apply 2>&1)"
assert_contains "$fallback_output" "MIGRATION_SUMMARY total=2 uploaded=2"
docker_log="$(<"$DOCKER_LOG")"
assert_contains "$docker_log" "-v $AVATARS:/migration-src/avatar:ro"
assert_contains "$docker_log" "-v $BACKUPS:/migration-src/backup:ro"
assert_contains "$docker_log" "--body /migration-src/avatar/avatar.png"
assert_contains "$docker_log" "--body /migration-src/backup/backup_FULL_20260920_120000.sql"
assert_not_contains "$docker_log" "--body $AVATARS/avatar.png"
assert_not_contains "$docker_log" "--body $BACKUPS/backup_FULL_20260920_120000.sql"

# Compose-style fallback joins the project-scoped object-storage network.
: >"$DOCKER_LOG"; : >"$MYSQL_LOG"
rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
export AWS_BIN="$BIN/aws"
export APP_STORAGE_S3_ENDPOINT=https://rustfs:9000
export APP_STORAGE_S3_TLS_ENABLED=true
export COMPOSE_PROJECT_NAME=ulticode-prod
export FAKE_DOCKER_NETWORK=ulticode-prod_object-storage
unset MIGRATION_DOCKER_NETWORK
production_fallback_output="$(run_migration --apply 2>&1)"
assert_contains "$production_fallback_output" "MIGRATION_SUMMARY total=2 uploaded=2"
production_docker_log="$(<"$DOCKER_LOG")"
assert_contains "$production_docker_log" "network inspect ulticode-prod_object-storage"
assert_contains "$production_docker_log" "run --rm --network ulticode-prod_object-storage"
assert_not_contains "$production_docker_log" "--network host"

# An explicit migration network overrides the Compose project default.
: >"$DOCKER_LOG"; : >"$MYSQL_LOG"
rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
export MIGRATION_DOCKER_NETWORK=custom-object-storage
export FAKE_DOCKER_NETWORK=custom-object-storage
override_fallback_output="$(run_migration --apply 2>&1)"
assert_contains "$override_fallback_output" "MIGRATION_SUMMARY total=2 uploaded=2"
override_docker_log="$(<"$DOCKER_LOG")"
assert_contains "$override_docker_log" "network inspect custom-object-storage"
assert_contains "$override_docker_log" "run --rm --network custom-object-storage"
unset APP_STORAGE_S3_ENDPOINT APP_STORAGE_S3_TLS_ENABLED COMPOSE_PROJECT_NAME MIGRATION_DOCKER_NETWORK FAKE_DOCKER_NETWORK

echo 'migrate-object-storage-test: PASS'
