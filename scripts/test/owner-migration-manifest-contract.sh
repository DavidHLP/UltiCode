#!/usr/bin/env bash
set -euo pipefail

# P2-MIG-001 fast contract: exercise manifest validation, retry reporting,
# lock contention, rollback compatibility, and secret-free reports without a
# real database or production host.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TEST_DIR"
}
trap cleanup EXIT

# P2-MIG-001: the executable migration and backup seams must keep the same
# ordered owner scope used by the local runbook and deployment action.
for expected in \
  'OWNER_MIGRATION_ORDER=(auth admin app notification submission)' \
  'flock -n' \
  'no repair is attempted' \
  'skip_migrations=true preserves schema' \
  '-baselineOnMigrate=true'; do
  grep -Fq -- "$expected" "$ROOT_DIR/scripts/runbooks/owner-migration-manifest.sh"
done
grep -Fq 'flyway.baselineOnMigrate=true' "$ROOT_DIR/init-db/flyway-post-owner.conf"
grep -Fq 'migrate-post-owner.sh' "$ROOT_DIR/scripts/dev/up.sh"
grep -Fq 'flyway-post-owner.conf' "$ROOT_DIR/scripts/dev/migrate-post-owner.sh"
grep -Fq 'INSERT INTO `admin`.`backups`' \
  "$ROOT_DIR/init-db/migrations/post-owner/V20260921120000__Copy_Legacy_Backups_To_Admin.sql"
grep -Fq 'FROM `ulticode`.`backups`' \
  "$ROOT_DIR/init-db/migrations/post-owner/V20260921120000__Copy_Legacy_Backups_To_Admin.sql"
grep -Fq 'backup_cutover_state' \
  "$ROOT_DIR/init-db/migrations/post-owner/V20260922120000__Create_Legacy_Backup_Cutover_State.sql"
grep -Fq 'reconcile-legacy-backups.sh' \
  "$ROOT_DIR/scripts/runbooks/owner-migration-manifest.sh"
grep -Fq 'assert-admin-backup-drained.sh' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'Invalid deployable service' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'phase=migration-running' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'docker start$START_ARGS' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'backend-admin remains stopped' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'phase=migration-complete' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'docker inspect -f' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'Verify Admin backup writer drained' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'backend-auth|backend-admin|backend-app|backend-submission|backend-notification|backend-search|backend-judge|console|management' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
! grep -Fq 'legacy_backups_copy_sql' \
  "$ROOT_DIR/init-db/migrations/admin/V20260920120000__Backup_Object_Storage.sql"
grep -Fq 'flyway_post_owner_history' "$ROOT_DIR/init-db/scripts/generate-baseline.sh"
grep -Fq 'generate-baseline.sh" "$TMP_DUMP"' "$ROOT_DIR/init-db/scripts/validate-baseline.sh"
grep -Fq 'OWNER_SCHEMAS=(auth admin app notification submission)' \
  "$ROOT_DIR/scripts/runbooks/owner-backup-restore.sh"
grep -Fq 'openssl enc -aes-256-cbc -salt -pbkdf2' \
  "$ROOT_DIR/scripts/runbooks/owner-backup-restore.sh"
grep -Fq 'flock -n' "$ROOT_DIR/scripts/runbooks/owner-backup-restore.sh"
grep -Fq 'rto_seconds' "$ROOT_DIR/scripts/runbooks/owner-backup-restore.sh"
grep -Fq 'owner-migration-manifest.sh migrate' "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'MIGRATION_DB_PASSWORD' "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq "inputs.skip_migrations != 'true'" "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'Quiesce legacy Admin backup writer' "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'compose_prefix="docker compose --project-directory . -f docker/docker-compose.yml -f docker/docker-compose.prod.yml"' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'remote_command+=" $EXPORTS $compose_prefix stop backend-admin"' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'remote_command+=" $REMOTE_ENV docker compose' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'DEPLOY_SERVICES: ${{ inputs.services }}' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
grep -Fq 'backend-admin must be included in services when migrations run' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml"
quiesce_line="$(grep -n 'Quiesce legacy Admin backup writer' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml" | head -1 | cut -d: -f1)"
migration_line="$(grep -n 'Run ordered owner database migrations' \
  "$ROOT_DIR/.github/actions/host-deploy/action.yml" | head -1 | cut -d: -f1)"
[[ "$quiesce_line" -lt "$migration_line" ]] \
  || { echo 'legacy backup writer is not quiesced before owner migrations' >&2; exit 1; }
grep -Fq 'migration_db_user:' "$ROOT_DIR/.github/workflows/cd-deploy.yml"
grep -Fq 'submission_migration_db_password:' "$ROOT_DIR/.github/workflows/cd-deploy.yml"
grep -Fq "skip_migrations: 'true'" "$ROOT_DIR/.github/workflows/cd-rollback.yml"

MIGRATION_PASSWORD="$(openssl rand -hex 16)"
SUBMISSION_PASSWORD="$(openssl rand -hex 16)"
touch "$TEST_DIR/empty.env"
COMMON_ENV=(ENV_FILE="$TEST_DIR/empty.env"
  MIGRATION_DB_HOST=127.0.0.1
  MIGRATION_DB_PORT=3306
  MIGRATION_DB_NAME=ulticode
  MIGRATION_DB_USER=migration_user
  MIGRATION_DB_PASSWORD="$MIGRATION_PASSWORD"
  AUTH_DB_NAME=auth
  AUTH_DB_USER=auth_rw
  ADMIN_DB_NAME=admin
  ADMIN_DB_USER=admin_rw
  APP_DB_NAME=app
  APP_DB_USER=app_rw
  NOTIFICATION_DB_NAME=notification
  NOTIFICATION_DB_USER=notification_rw
  SUBMISSION_DB_NAME=submission
  SUBMISSION_DB_USER=submission_rw
  SUBMISSION_MIGRATION_DB_USER=migration_submission
  SUBMISSION_MIGRATION_DB_PASSWORD="$SUBMISSION_PASSWORD"
)

if env "${COMMON_ENV[@]}" AUTH_DB_NAME= OWNER_MIGRATION_REPORT_DIR="$TEST_DIR/invalid" \
    bash "$ROOT_DIR/scripts/runbooks/owner-migration-manifest.sh" validate \
    >"$TEST_DIR/invalid.log" 2>&1; then
  echo 'missing preflight variable was accepted' >&2
  exit 1
fi
grep -q 'AUTH_DB_NAME is required' "$TEST_DIR/invalid.log"
printf 'manifest required-variable gate: PASS\n'

env "${COMMON_ENV[@]}" OWNER_MIGRATION_REPORT_DIR="$TEST_DIR/valid" \
  bash "$ROOT_DIR/scripts/runbooks/owner-migration-manifest.sh" validate \
  >"$TEST_DIR/valid.log"
grep -q 'status=PASS' "$TEST_DIR/valid.log"
VALID_REPORT="$(find "$TEST_DIR/valid" -name '*.json' -type f -print -quit)"
[[ -s "$VALID_REPORT" ]]
grep -q '"status": "PASS"' "$VALID_REPORT"
! grep -F "$MIGRATION_PASSWORD" "$VALID_REPORT" >/dev/null
printf 'manifest order/schema/checksum report: PASS\n'

FAKE_DOCKER="$TEST_DIR/fake-docker"
FAKE_STATE="$TEST_DIR/docker-calls"
FAKE_MYSQL="$TEST_DIR/fake-mysql"
FAKE_LEASE_STATE="$TEST_DIR/lease-owner"
cat >"$FAKE_DOCKER" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[[ "${1:-}" == run ]] || exit 1
calls=0
if [[ -f "${FAKE_DOCKER_STATE:?}" ]]; then
  calls="$(<"$FAKE_DOCKER_STATE")"
fi
calls=$((calls + 1))
printf '%s\n' "$calls" >"$FAKE_DOCKER_STATE"
[[ "$calls" != 1 ]]
EOF
chmod +x "$FAKE_DOCKER"

cat >"$FAKE_MYSQL" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
sql=""
while (($# > 0)); do
  if [[ "$1" == -e && $# -ge 2 ]]; then
    sql="$2"
    shift 2
  else
    shift
  fi
done
flat_sql="$(tr '\n' ' ' <<< "$sql")"
if [[ "$flat_sql" == *"INSERT INTO fenced_job_leases"* ]]; then
  owner="$(sed -n "s/.*VALUES[[:space:]]*('[^']*',[[:space:]]*1,[[:space:]]*'\([^']*\)'.*/\1/p" <<< "$flat_sql")"
  printf '%s\n' "$owner" >"${FAKE_LEASE_STATE:?}"
elif [[ "$flat_sql" == *"SELECT owner_token, fence_token"* ]]; then
  printf '%s\t1\n' "$(<"${FAKE_LEASE_STATE:?}")"
elif [[ "$flat_sql" == *"table_schema='ulticode'"* && "$flat_sql" == *"table_name='backups'"* ]]; then
  printf '0\n'
elif [[ "$flat_sql" == *"SELECT COUNT(*)"* ]]; then
  printf '1\n'
fi
EOF
chmod +x "$FAKE_MYSQL"

env "${COMMON_ENV[@]}" DOCKER_BIN="$FAKE_DOCKER" FAKE_DOCKER_STATE="$FAKE_STATE" \
  OWNER_MIGRATION_MYSQL_BIN="$FAKE_MYSQL" FAKE_LEASE_STATE="$FAKE_LEASE_STATE" \
  OWNER_MIGRATION_MAX_ATTEMPTS=2 OWNER_MIGRATION_REPORT_DIR="$TEST_DIR/retry" \
  bash "$ROOT_DIR/scripts/runbooks/owner-migration-manifest.sh" migrate \
  >"$TEST_DIR/retry.log"
grep -q 'retrying owner=shared' "$TEST_DIR/retry.log"
grep -q 'status=PASS' "$TEST_DIR/retry.log"
RETRY_REPORT="$(find "$TEST_DIR/retry" -name '*.json' -type f -print -quit)"
grep -q '"status": "PASS"' "$RETRY_REPORT"
[[ "$(cat "$FAKE_STATE")" == 8 ]]
grep -q 'post-owner' "$TEST_DIR/retry.log"
printf 'manifest failure retry and fresh/upgrade runner: PASS\n'

LOCK_FILE="$TEST_DIR/owner.lock"
flock "$LOCK_FILE" -c 'sleep 2' &
LOCK_HOLDER=$!
for _ in $(seq 1 20); do
  if ! flock -n "$LOCK_FILE" -c true; then
    break
  fi
  sleep 0.1
done
set +e
env "${COMMON_ENV[@]}" DOCKER_BIN="$FAKE_DOCKER" FAKE_DOCKER_STATE="$FAKE_STATE" \
  OWNER_MIGRATION_MYSQL_BIN="$FAKE_MYSQL" FAKE_LEASE_STATE="$FAKE_LEASE_STATE" \
  OWNER_MIGRATION_LOCK_FILE="$LOCK_FILE" OWNER_MIGRATION_REPORT_DIR="$TEST_DIR/busy" \
  bash "$ROOT_DIR/scripts/runbooks/owner-migration-manifest.sh" migrate \
  >"$TEST_DIR/busy.log" 2>&1
BUSY_STATUS=$?
set -e
wait "$LOCK_HOLDER"
[[ "$BUSY_STATUS" == 75 ]]
grep -q 'status=SKIPPED' "$TEST_DIR/busy.log"
BUSY_REPORT="$(find "$TEST_DIR/busy" -name '*.json' -type f -print -quit)"
grep -q '"status": "SKIPPED"' "$BUSY_REPORT"
printf 'manifest concurrency lock: PASS\n'

env "${COMMON_ENV[@]}" OWNER_MIGRATION_REPORT_DIR="$TEST_DIR/rollback" \
  bash "$ROOT_DIR/scripts/runbooks/owner-migration-manifest.sh" rollback \
  >"$TEST_DIR/rollback.log"
grep -q 'skip_migrations=true' "$TEST_DIR/rollback.log"
printf 'rollback compatibility report: PASS\n'

printf 'owner-migration-manifest-contract: PASS\n'
