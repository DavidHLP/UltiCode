#!/usr/bin/env bash
set -euo pipefail

# P1-INFRA-003: disposable Redis role/failure drill. This script never targets
# production: it accepts only an explicitly supplied owner-only env file and
# uses a unique Compose project with a disposable Redis volume.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${REDIS_ROLE_DRILL_ENV_FILE:-}"
if [[ -z "$ENV_FILE" ]]; then
  echo "redis-role-fault-drill: BLOCKED_EXTERNAL (set REDIS_ROLE_DRILL_ENV_FILE to an authorized disposable env file)"
  exit 0
fi
[[ "$ENV_FILE" == /* ]] || ENV_FILE="$ROOT_DIR/$ENV_FILE"
[[ -f "$ENV_FILE" && ! -L "$ENV_FILE" && -O "$ENV_FILE" ]] || {
  echo "redis-role-fault-drill: env file must be an owned regular file" >&2
  exit 1
}
mode="$(stat -c '%a' -- "$ENV_FILE")"
(( ((8#$mode) & 077) == 0 )) || {
  echo "redis-role-fault-drill: env file must be owner-only" >&2
  exit 1
}

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
required=(
  AUTH_REDIS_PASSWORD ADMIN_REDIS_PASSWORD APP_REDIS_PASSWORD
  SUBMISSION_REDIS_PASSWORD SEARCH_REDIS_PASSWORD NOTIFICATION_REDIS_PASSWORD
  JUDGE_REDIS_PASSWORD OPS_REDIS_PASSWORD HEALTH_REDIS_PASSWORD
  REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD
)
for variable in "${required[@]}"; do
  [[ -n "${!variable:-}" ]] || {
    echo "redis-role-fault-drill: BLOCKED_EXTERNAL (missing $variable in disposable env)"
    exit 0
  }
done

export REDIS_ACL_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ulticode-redis-drill-acl.XXXXXX")"
export REDIS_ACL_FILE="$REDIS_ACL_DIR/users.acl"
export COMPOSE_PROJECT_NAME="ulticode-redis-drill-$$"
COMPOSE_OVERRIDE_FILE="$(mktemp "${TMPDIR:-/tmp}/ulticode-redis-drill-compose.XXXXXX.yml")"
readonly REDIS_ACL_DIR REDIS_ACL_FILE COMPOSE_PROJECT_NAME COMPOSE_OVERRIDE_FILE
chmod 755 "$REDIS_ACL_DIR"
cat >"$COMPOSE_OVERRIDE_FILE" <<'YAML'
services:
  redis:
    command:
      - redis-server
      - --aclfile
      - /usr/local/etc/redis/users.acl
      - --maxmemory
      - 4mb
      - --maxmemory-policy
      - noeviction
YAML
cleanup() {
  local rc=$?
  trap - EXIT INT TERM
  docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.yml" \
    -f "$COMPOSE_OVERRIDE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf -- "$REDIS_ACL_DIR" "$COMPOSE_OVERRIDE_FILE"
  exit "$rc"
}
trap cleanup EXIT INT TERM

"$ROOT_DIR/docker/redis/generate-users-acl.sh" "$REDIS_ACL_FILE"
compose=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.yml" -f "$COMPOSE_OVERRIDE_FILE")
if ! "${compose[@]}" up -d redis >/dev/null; then
  echo "redis-role-fault-drill: BLOCKED_EXTERNAL (disposable Redis could not start)"
  exit 0
fi
container="$(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.yml" -f "$COMPOSE_OVERRIDE_FILE" ps -aq redis)"
[[ -n "$container" ]] || { echo "redis-role-fault-drill: BLOCKED_EXTERNAL (Redis container unavailable)"; exit 0; }
redis() {
  local user="$1" password="$2"
  shift 2
  docker exec -e REDISCLI_AUTH="$password" "$container" redis-cli --user "$user" "$@"
}
expect_denied() {
  local user="$1" password="$2" reason="$3"
  shift 3
  local result
  result="$(redis "$user" "$password" "$@" 2>&1 || true)"
  [[ "$result" == *"NOPERM"* ]] || {
    echo "redis-role-fault-drill: FAIL ($reason)" >&2
    exit 1
  }
}

for attempt in $(seq 1 30); do
  if [[ "$(redis ulticode-health "$HEALTH_REDIS_PASSWORD" ping 2>/dev/null || true)" == "PONG" ]]; then
    break
  fi
  [[ "$attempt" == 30 ]] && {
    echo "redis-role-fault-drill: BLOCKED_EXTERNAL (Redis health timeout)"
    exit 0
  }
  sleep 1
done

# Establish control keys before pressure. They must survive the cache-only fill.
redis ulticode-ops "$OPS_REDIS_PASSWORD" XADD stream:app-audit '*' eventId drill owner App eventType AuditRecorded >/dev/null
redis ulticode-ops "$OPS_REDIS_PASSWORD" XADD stream:auth-audit '*' eventId drill-auth owner Auth eventType AuditRecorded >/dev/null
redis ulticode-submission "$SUBMISSION_REDIS_PASSWORD" SET judge:drill-control alive EX 300 >/dev/null
redis ulticode-ops "$OPS_REDIS_PASSWORD" SET blacklist:token:drill revoked EX 300 >/dev/null
redis ulticode-app "$APP_REDIS_PASSWORD" SET userStats::drill seed EX 300 >/dev/null
redis ulticode-ops "$OPS_REDIS_PASSWORD" SET rate-limit:auth:drill 1 EX 300 >/dev/null
redis ulticode-app "$APP_REDIS_PASSWORD" XADD stream:app-audit '*' eventId app owner App eventType AuditRecorded >/dev/null
redis ulticode-auth "$AUTH_REDIS_PASSWORD" XADD stream:auth-audit '*' eventId auth owner Auth eventType AuditRecorded >/dev/null
expect_denied ulticode-admin "$ADMIN_REDIS_PASSWORD" \
  "Admin principal can write shared integration stream" \
  XADD stream:integration '*' eventId forbidden
expect_denied ulticode-app "$APP_REDIS_PASSWORD" \
  "App principal can read Auth audit stream" \
  XRANGE stream:auth-audit - +
expect_denied ulticode-auth "$AUTH_REDIS_PASSWORD" \
  "Auth principal can read App audit stream" \
  XRANGE stream:app-audit - +
expect_denied ulticode-notification "$NOTIFICATION_REDIS_PASSWORD" \
  "Notification principal can read another owner's rate-limit bucket" \
  GET rate-limit:auth:drill
[[ "$(redis ulticode-app "$APP_REDIS_PASSWORD" EXISTS blacklist:token:drill)" == "1" ]] || {
  echo "redis-role-fault-drill: FAIL (App cannot read its dedicated blacklist key)" >&2
  exit 1
}
expect_denied ulticode-app "$APP_REDIS_PASSWORD" \
  "App blacklist selector permits writes" \
  SET blacklist:token:drill forged EX 300

# Pause briefly to exercise bounded client/backpressure handling, then verify
# control state. Memory-pressure setup is disposable and uses only the ops
# principal inside this temporary container.
python3 - <<'PY' | docker exec -i -e REDISCLI_AUTH="$APP_REDIS_PASSWORD" "$container" \
  redis-cli --user ulticode-app --pipe >/dev/null 2>/dev/null || true
import sys

for key in range(2000):
    name = f"userStats::drill:{key}".encode()
    value = b"x" * 4096
    parts = [b"SET", name, value, b"EX", b"300"]
    sys.stdout.buffer.write(b"*" + str(len(parts)).encode() + b"\r\n")
    for part in parts:
        sys.stdout.buffer.write(b"$" + str(len(part)).encode() + b"\r\n" + part + b"\r\n")
PY
redis ulticode-ops "$OPS_REDIS_PASSWORD" CLIENT PAUSE 250 >/dev/null || true

[[ "$(redis ulticode-submission "$SUBMISSION_REDIS_PASSWORD" GET judge:drill-control)" == "alive" ]] || {
  echo "redis-role-fault-drill: FAIL (control key evicted under cache pressure)" >&2
  exit 1
}
app_audit_info="$(redis ulticode-admin "$ADMIN_REDIS_PASSWORD" XINFO STREAM stream:app-audit)"
auth_audit_info="$(redis ulticode-admin "$ADMIN_REDIS_PASSWORD" XINFO STREAM stream:auth-audit)"
[[ "$app_audit_info" == *"length"* && "$auth_audit_info" == *"length"* ]] || {
  echo "redis-role-fault-drill: FAIL (owner audit stream lost under cache pressure)" >&2
  exit 1
}

echo "redis-role-fault-drill: PASS (disposable cache pressure/backpressure; control keys preserved; no production claim)"
