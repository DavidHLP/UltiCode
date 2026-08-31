#!/usr/bin/env bash
set -euo pipefail

# P2-BACKUP-001 disposable proof: build a final six-schema fixture from the
# repository baseline, establish each Flyway history, create an encrypted
# owner archive, verify it, restore it into a fresh MySQL container, and run
# migration/checksum/smoke checks with measured RPO/RTO output.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR="$(mktemp -d)"
SOURCE_CONTAINER="ulticode-owner-backup-source-$$"
ROOT_PASSWORD="$(openssl rand -hex 16)"
ENCRYPTION_KEY="$(openssl rand -hex 32)"
TEST_ENV="$TEST_DIR/test.env"
BACKUP_DIR="$TEST_DIR/backups"
LOCK_FILE="$BACKUP_DIR/owner-backup.lock"

cleanup() {
  docker rm -f "$SOURCE_CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$TEST_DIR"
}
trap cleanup EXIT
trap 'printf "owner-backup-restore-contract: FAIL line=%s\n" "$LINENO" >&2' ERR

printf '# disposable owner backup contract\n' > "$TEST_ENV"
docker run -d --rm --name "$SOURCE_CONTAINER" \
  -e "MYSQL_ROOT_PASSWORD=$ROOT_PASSWORD" -e MYSQL_DATABASE=ulticode \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null
for _ in $(seq 1 60); do
  if docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$SOURCE_CONTAINER" mysql -uroot -N -B -e 'SELECT 1' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec -e MYSQL_PWD="$ROOT_PASSWORD" "$SOURCE_CONTAINER" mysql -uroot -N -B -e 'SELECT 1' >/dev/null
docker exec -i -e MYSQL_PWD="$ROOT_PASSWORD" "$SOURCE_CONTAINER" mysql -uroot < "$ROOT_DIR/init-db/baseline/baseline.sql"

detect_version() {
  local directory="$1"
  find "$directory" -maxdepth 1 -type f -name 'V*.sql' -print \
    | sed -E 's#.*/V([0-9_]+)__.*#\1#' \
    | tr -d '_' | sort -n | tail -1
}

flyway_baseline() {
  local schema="$1" locations="$2" version="$3" table_name="${4:-flyway_schema_history}"
  local flyway_url="jdbc:mysql://127.0.0.1:3306/$schema?allowPublicKeyRetrieval=true&useSSL=false"
  FLYWAY_URL="$flyway_url" FLYWAY_USER=root FLYWAY_PASSWORD="$ROOT_PASSWORD" \
    docker run --rm --network "container:$SOURCE_CONTAINER" \
      -e FLYWAY_URL -e FLYWAY_USER -e FLYWAY_PASSWORD \
      -v "$ROOT_DIR/init-db/migrations:/flyway/sql:ro" flyway/flyway:10.17.0 \
      "-locations=filesystem:/flyway/sql/$locations" "-defaultSchema=$schema" \
      "-table=$table_name" "-baselineVersion=$version" baseline >/dev/null
}

SHARED_VERSION="$(detect_version "$ROOT_DIR/init-db/migrations")"
POST_OWNER_VERSION="$(detect_version "$ROOT_DIR/init-db/migrations/post-owner")"
flyway_baseline ulticode '*.sql' "$SHARED_VERSION"
flyway_baseline ulticode post-owner "$POST_OWNER_VERSION" flyway_post_owner_history
for schema in auth admin app notification submission; do
  flyway_baseline "$schema" "$schema" "$(detect_version "$ROOT_DIR/init-db/migrations/$schema")"
done

runbook() {
  local action="$1"
  env ENV_FILE="$TEST_ENV" \
    BACKUP_DB_HOST=127.0.0.1 BACKUP_DB_PORT=3306 BACKUP_DB_NAME=ulticode \
    BACKUP_DB_USER=root BACKUP_DB_PASSWORD="$ROOT_PASSWORD" \
    BACKUP_ENCRYPTION_KEY="$ENCRYPTION_KEY" BACKUP_MYSQL_CONTAINER="$SOURCE_CONTAINER" \
    BACKUP_MYSQL_CONTAINER_PORT=3306 OWNER_BACKUP_DIR="$BACKUP_DIR" \
    OWNER_BACKUP_LOCK_FILE="$LOCK_FILE" OWNER_BACKUP_MANIFEST="${MANIFEST:-}" \
    bash "$ROOT_DIR/scripts/runbooks/owner-backup-restore.sh" "$action"
}

runbook backup > "$TEST_DIR/backup.log"
MANIFEST="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'owner-backup-*.json' -print -quit)"
ARCHIVE="$(sed -n 's/^[[:space:]]*"archive": "\([^"]*\)".*/\1/p' "$MANIFEST")"
[[ -s "$MANIFEST" && -s "$BACKUP_DIR/$ARCHIVE" ]]
grep -Fq '"excluded_operational_tables": ["admin.fenced_job_leases"]' "$MANIFEST"
! grep -F "$ROOT_PASSWORD" "$MANIFEST" >/dev/null
! grep -F "$ENCRYPTION_KEY" "$MANIFEST" >/dev/null
printf 'encrypted five-owner archive and secret-free manifest: PASS\n'

runbook verify > "$TEST_DIR/verify.log"
grep -q 'PASS verified' "$TEST_DIR/verify.log"
printf 'archive checksum/decryption verification: PASS\n'

set +e
trap - ERR
env ENV_FILE="$TEST_ENV" BACKUP_ENCRYPTION_KEY="$(openssl rand -hex 32)" \
  OWNER_BACKUP_DIR="$BACKUP_DIR" OWNER_BACKUP_MANIFEST="$MANIFEST" \
  bash "$ROOT_DIR/scripts/runbooks/owner-backup-restore.sh" verify > "$TEST_DIR/wrong-key.log" 2>&1
WRONG_KEY_STATUS=$?
set -e
trap 'printf "owner-backup-restore-contract: FAIL line=%s\n" "$LINENO" >&2' ERR
[[ "$WRONG_KEY_STATUS" -ne 0 ]]
printf 'wrong encryption key rejection: PASS\n'

flock "$LOCK_FILE" -c 'sleep 2' &
LOCK_HOLDER=$!
for _ in $(seq 1 20); do
  if ! flock -n "$LOCK_FILE" -c true; then
    break
  fi
  sleep 0.1
done
set +e
trap - ERR
runbook restore-drill > "$TEST_DIR/busy.log" 2>&1
BUSY_STATUS=$?
set -e
trap 'printf "owner-backup-restore-contract: FAIL line=%s\n" "$LINENO" >&2' ERR
wait "$LOCK_HOLDER"
[[ "$BUSY_STATUS" == 75 ]]
grep -q 'SKIPPED' "$TEST_DIR/busy.log"
printf 'backup/restore singleton lock: PASS\n'

if ! runbook restore-drill > "$TEST_DIR/restore-drill.log" 2>&1; then
  tail -100 "$TEST_DIR/restore-drill.log" >&2
  exit 1
fi
DRILL_REPORT="$(find "$BACKUP_DIR/reports" -maxdepth 1 -type f -name 'owner-restore-drill-*.json' -print -quit)"
grep -q '"status": "PASS"' "$DRILL_REPORT"
grep -q '"migration_validate": "PASS"' "$DRILL_REPORT"
grep -q '"checksum_reconciliation": "PASS"' "$DRILL_REPORT"
grep -q '"smoke": "PASS"' "$DRILL_REPORT"
grep -Eq '"rpo_seconds": "[0-9]+"' "$DRILL_REPORT"
grep -Eq '"rto_seconds": "[0-9]+"' "$DRILL_REPORT"
printf 'temporary restore migration/checksum/reconciliation/smoke/RPO-RTO drill: PASS\n'

OLD_ARCHIVE="$BACKUP_DIR/owner-backup-old.tar.gz.enc"
OLD_MANIFEST="$BACKUP_DIR/owner-backup-old.json"
printf 'old archive\n' > "$OLD_ARCHIVE"
printf '{\n  "archive": "owner-backup-old.tar.gz.enc"\n}\n' > "$OLD_MANIFEST"
touch -d '3 days ago' "$OLD_ARCHIVE" "$OLD_MANIFEST"
OWNER_BACKUP_RETENTION_DAYS=1 runbook prune > "$TEST_DIR/prune.log"
[[ ! -e "$OLD_ARCHIVE" && ! -e "$OLD_MANIFEST" ]]
printf 'retention pruning: PASS\n'

printf 'owner-backup-restore-contract: PASS\n'
