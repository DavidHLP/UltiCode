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
MYSQL_LOG="$TMP_DIR/mysql.log"
DB_STATE="$TMP_DIR/db-state"
ENV_FILE="$TMP_DIR/.env"
mkdir -p "$BIN" "$AVATARS" "$BACKUPS" "$OBJECTS" "$DB_STATE"
: >"$AWS_LOG"
: >"$MYSQL_LOG"

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
while (($#)); do
  if [[ "$1" == "-e" ]]; then sql="${2:-}"; break; fi
  shift
done
printf '%s\n' "$sql" >>"$FAKE_MYSQL_LOG"
if [[ "$sql" == *"UPDATE user_profiles"* ]]; then
  : >"$FAKE_DB_STATE/avatar"
  printf '1\n'
elif [[ "$sql" == *"UPDATE backups"* ]]; then
  : >"$FAKE_DB_STATE/backup"
  printf '1\n'
elif [[ "$sql" == *"information_schema.COLUMNS"* ]]; then
  printf '1\n'
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
    etag="$(md5sum "$object" | awk '{print $1}')"
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

export PATH="$BIN:$PATH"
export ENV_FILE AWS_BIN="$BIN/aws"
export FAKE_AWS_LOG="$AWS_LOG" FAKE_MYSQL_LOG="$MYSQL_LOG"
export FAKE_OBJECTS="$OBJECTS" FAKE_DB_STATE="$DB_STATE" FAKE_BACKUP_SIZE="$BACKUP_SIZE"

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

# Dry-run must read the plan but never put an object or update a row.
: >"$AWS_LOG"; : >"$MYSQL_LOG"; rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
dry_run_output="$(run_migration 2>&1)"
assert_contains "$dry_run_output" "PLAN type=avatar"
assert_contains "$dry_run_output" "PLAN type=backup"
assert_contains "$dry_run_output" "MIGRATION_SUMMARY total=2 uploaded=0"
assert_not_contains "$(<"$AWS_LOG")" "put-object"
assert_not_contains "$(<"$MYSQL_LOG")" "UPDATE"

# An existing object with matching size and ETag is reused; no duplicate put.
: >"$AWS_LOG"; : >"$MYSQL_LOG"; rm -f -- "$DB_STATE/avatar" "$DB_STATE/backup" "$OBJECTS"/*
key_path="$OBJECTS/app__avatars__acct-1__avatar.png"
cp -- "$AVATARS/avatar.png" "$key_path"
existing_output="$(run_migration --apply --only avatars 2>&1)"
assert_contains "$existing_output" "VERIFIED avatar account=acct-1"
assert_contains "$existing_output" "uploaded=0"
assert_not_contains "$(<"$AWS_LOG")" "put-object"
assert_contains "$(<"$MYSQL_LOG")" "UPDATE user_profiles"

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

echo 'migrate-object-storage-test: PASS'
