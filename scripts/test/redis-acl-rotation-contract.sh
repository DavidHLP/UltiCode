#!/usr/bin/env bash
set -euo pipefail

# P2-REDIS-001 disposable proof: materialize an ACL outside Git, reload Redis
# with next+current overlap, finalize, roll back, detect drift, and enforce the
# rotation lock without printing any credential.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR="$(mktemp -d)"
ACL_DIR="$TEST_DIR/redis-acl"
ACL_FILE="$ACL_DIR/users.acl"
STATE_FILE="$ACL_DIR/rotation.state"
LOCK_FILE="$ACL_DIR/rotation.lock"
REPORT_FILE="$ACL_DIR/rotation.log"
ENV_FILE="$TEST_DIR/test.env"
REDIS_CONTAINER="ulticode-acl-rotation-$$"
RELOAD_PASSWORD=""
PASSWORD_PREFIXES=(AUTH ADMIN APP SUBMISSION SEARCH NOTIFICATION JUDGE OPS HEALTH)

cleanup() {
  docker rm -f "$REDIS_CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$TEST_DIR"
}
trap cleanup EXIT
trap 'printf "redis-acl-rotation-contract: FAIL line=%s\n" "$LINENO" >&2' ERR

printf '# disposable Redis ACL rotation contract\n' > "$ENV_FILE"
mkdir -p "$ACL_DIR"
for prefix in "${PASSWORD_PREFIXES[@]}"; do
  current_var="${prefix}_REDIS_PASSWORD"
  next_var="${current_var}_NEXT"
  printf -v "$current_var" '%s' "$(openssl rand -hex 24)"
  printf -v "$next_var" '%s' "$(openssl rand -hex 24)"
  export "$current_var" "$next_var"
done
for current_var in REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD; do
  next_var="${current_var}_NEXT"
  printf -v "$current_var" '%s' "$(openssl rand -hex 24)"
  printf -v "$next_var" '%s' "$(openssl rand -hex 24)"
  export "$current_var" "$next_var"
done

run_rotation() {
  local action="$1" expected_phase="${2:-}"
  env ENV_FILE="$ENV_FILE" REDIS_ACL_DIR="$ACL_DIR" REDIS_ACL_FILE="$ACL_FILE" \
    REDIS_ACL_STATE_FILE="$STATE_FILE" REDIS_ACL_LOCK_FILE="$LOCK_FILE" \
    REDIS_ACL_REPORT_FILE="$REPORT_FILE" REDIS_ACL_REDIS_CONTAINER="${REDIS_ACL_REDIS_CONTAINER:-}" \
    REDIS_ACL_RELOAD_USER=ulticode-ops REDIS_ACL_RELOAD_PASSWORD="$RELOAD_PASSWORD" \
    REDIS_ACL_EXPECTED_PHASE="$expected_phase" \
    bash "$ROOT_DIR/scripts/runbooks/redis-acl-rotation.sh" "$action"
}

REDIS_ACL_REDIS_CONTAINER=""
run_rotation materialize > "$TEST_DIR/materialize.log"
grep -q 'phase=current' "$TEST_DIR/materialize.log"
grep -q '"phase": "current"' "$STATE_FILE"
printf 'runtime ACL materialization and state: PASS\n'

docker run -d --rm --name "$REDIS_CONTAINER" \
  -v "$ACL_DIR:/usr/local/etc/redis:ro" redis:7-alpine \
  redis-server --aclfile /usr/local/etc/redis/users.acl >/dev/null
REDIS_ACL_REDIS_CONTAINER="$REDIS_CONTAINER"

redis_ping() {
  local user="$1" password="$2"
  docker exec -e "REDISCLI_AUTH=$password" "$REDIS_CONTAINER" \
    redis-cli --user "$user" ping | grep -Fxq PONG
}

for _ in $(seq 1 30); do
  if redis_ping ulticode-ops "$OPS_REDIS_PASSWORD"; then
    break
  fi
  sleep 0.1
done
if ! redis_ping ulticode-ops "$OPS_REDIS_PASSWORD"; then
  expected_ops_hash="$(printf '%s' "$OPS_REDIS_PASSWORD" | openssl dgst -sha256 | awk '{print $NF}')"
  printf 'ops hash match=%s\n' "$(grep -F "#$expected_ops_hash" "$ACL_FILE" >/dev/null && printf yes || printf no)" >&2
  docker ps -a --filter "name=$REDIS_CONTAINER" --format 'status={{.Status}}' >&2
  docker logs "$REDIS_CONTAINER" 2>&1 | tail -20 >&2 || true
  exit 1
fi

RELOAD_PASSWORD="$OPS_REDIS_PASSWORD"
if ! run_rotation prepare > "$TEST_DIR/prepare.log" 2>&1; then
  cat "$TEST_DIR/prepare.log" >&2
  docker ps -a --filter "name=$REDIS_CONTAINER" --format 'status={{.Status}}' >&2
  docker logs "$REDIS_CONTAINER" 2>&1 | tail -20 >&2 || true
  exit 1
fi
grep -q 'phase=next-overlap-current' "$TEST_DIR/prepare.log"
redis_ping ulticode-auth "$AUTH_REDIS_PASSWORD"
redis_ping ulticode-auth "$AUTH_REDIS_PASSWORD_NEXT"
run_rotation drift-check next-overlap-current > "$TEST_DIR/overlap-check.log"
printf 'dual-credential overlap and drift-check: PASS\n'

RELOAD_PASSWORD="$OPS_REDIS_PASSWORD_NEXT"
run_rotation finalize > "$TEST_DIR/finalize.log"
grep -q 'phase=next' "$TEST_DIR/finalize.log"
redis_ping ulticode-auth "$AUTH_REDIS_PASSWORD_NEXT"
if redis_ping ulticode-auth "$AUTH_REDIS_PASSWORD" >/dev/null 2>&1; then
  echo 'old ACL credential remained after finalize' >&2
  exit 1
fi
run_rotation drift-check next > "$TEST_DIR/final-check.log"
printf 'finalize and old-credential retirement: PASS\n'

RELOAD_PASSWORD="$OPS_REDIS_PASSWORD_NEXT"
run_rotation rollback > "$TEST_DIR/rollback.log"
grep -q 'phase=current-overlap-next' "$TEST_DIR/rollback.log"
redis_ping ulticode-auth "$AUTH_REDIS_PASSWORD"
redis_ping ulticode-auth "$AUTH_REDIS_PASSWORD_NEXT"
run_rotation drift-check current-overlap-next > "$TEST_DIR/rollback-check.log"
printf 'rollback overlap and drift-check: PASS\n'

printf 'tampered ACL\n' > "$ACL_FILE"
if run_rotation drift-check current-overlap-next > "$TEST_DIR/drift-fail.log" 2>&1; then
  echo 'tampered ACL passed drift-check' >&2
  exit 1
fi
grep -q 'drift detected' "$TEST_DIR/drift-fail.log"
RELOAD_PASSWORD="$OPS_REDIS_PASSWORD_NEXT"
run_rotation rollback > "$TEST_DIR/restore.log"
printf 'ACL drift rejection: PASS\n'

flock "$LOCK_FILE" -c 'sleep 2' &
LOCK_HOLDER=$!
for _ in $(seq 1 20); do
  if ! flock -n "$LOCK_FILE" -c true; then
    break
  fi
  sleep 0.1
done
RELOAD_PASSWORD="$OPS_REDIS_PASSWORD_NEXT"
if run_rotation materialize > "$TEST_DIR/busy.log" 2>&1; then
  echo 'rotation lock contention unexpectedly passed' >&2
  exit 1
else
  BUSY_STATUS=$?
fi
wait "$LOCK_HOLDER"
[[ "$BUSY_STATUS" == 75 ]]
grep -q 'SKIPPED' "$TEST_DIR/busy.log"
printf 'rotation singleton lock: PASS\n'

for prefix in "${PASSWORD_PREFIXES[@]}"; do
  current_var="${prefix}_REDIS_PASSWORD"
  next_var="${current_var}_NEXT"
  ! grep -F "${!current_var}" "$ACL_FILE" "$STATE_FILE" "$REPORT_FILE" >/dev/null
  ! grep -F "${!next_var}" "$ACL_FILE" "$STATE_FILE" "$REPORT_FILE" >/dev/null
done
for current_var in REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD; do
  next_var="${current_var}_NEXT"
  ! grep -F "${!current_var}" "$ACL_FILE" "$STATE_FILE" "$REPORT_FILE" >/dev/null
  ! grep -F "${!next_var}" "$ACL_FILE" "$STATE_FILE" "$REPORT_FILE" >/dev/null
done
printf 'no plaintext credentials in ACL/state/report: PASS\n'
printf 'redis-acl-rotation-contract: PASS\n'
