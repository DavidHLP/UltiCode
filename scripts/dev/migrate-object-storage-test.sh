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
DB_STATE="$TMP_DIR/db-state"
ENV_FILE="$TMP_DIR/.env"
CA_FILE="$TMP_DIR/rustfs-ca.pem"
mkdir -p "$BIN" "$AVATARS" "$BACKUPS" "$OBJECTS" "$DB_STATE"
: >"$AWS_LOG"
: >"$AWS_ENV_LOG"
: >"$MYSQL_LOG"
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

export PATH="$BIN:$PATH"
export ENV_FILE AWS_BIN="$BIN/aws"
export FAKE_AWS_LOG="$AWS_LOG" FAKE_AWS_ENV_LOG="$AWS_ENV_LOG" FAKE_MYSQL_LOG="$MYSQL_LOG"
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
