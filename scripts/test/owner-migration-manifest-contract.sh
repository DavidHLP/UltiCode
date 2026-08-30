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

MIGRATION_PASSWORD="$(openssl rand -hex 16)"
SUBMISSION_PASSWORD="$(openssl rand -hex 16)"
COMMON_ENV=(
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

env "${COMMON_ENV[@]}" DOCKER_BIN="$FAKE_DOCKER" FAKE_DOCKER_STATE="$FAKE_STATE" \
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
