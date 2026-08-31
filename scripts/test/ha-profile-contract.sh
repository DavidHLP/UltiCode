#!/usr/bin/env bash
set -euo pipefail

# P3-HA-001: validate the optional stateful reference profile without implying
# that Compose provides transparent database or application failover.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_HA="$ROOT_DIR/docker-compose.ha.yml"
DOCKER_BIN="${DOCKER_BIN:-docker}"

fail() {
  echo "HA profile contract failed: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  grep -F -- "$text" "$ROOT_DIR/$file" >/dev/null \
    || fail "$file does not contain: $text"
}

not_contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  ! grep -F -- "$text" "$ROOT_DIR/$file" >/dev/null \
    || fail "$file contains forbidden HA shortcut: $text"
}

[[ -f "$COMPOSE_HA" ]] || fail "missing docker-compose.ha.yml"
contains docker-compose.ha.yml 'profiles: [ha]'
contains docker-compose.ha.yml 'mysql-replica:'
contains docker-compose.ha.yml 'read-only=ON'
contains docker-compose.ha.yml 'redis-replica:'
contains docker-compose.ha.yml 'redis-sentinel-1:'
contains docker-compose.ha.yml 'redis-sentinel-2:'
contains docker-compose.ha.yml 'redis-sentinel-3:'
contains docker-compose.ha.yml 'REDIS_HA_CONFIG_DIR:?REDIS_HA_CONFIG_DIR is required'
contains docker-compose.ha.yml 'nacos-2:'
contains docker-compose.ha.yml 'nacos-3:'
contains docker-compose.ha.yml 'MODE: cluster'
contains docker-compose.ha.yml 'NACOS_SERVERS: ${NACOS_SERVERS:?NACOS_SERVERS is required for HA cluster mode}'
contains docker-compose.ha.yml 'NACOS_AUTH_ENABLE: "true"'
not_contains docker-compose.ha.yml 'container_name:'
not_contains docker-compose.ha.yml 'network_mode: host'
not_contains docker-compose.ha.yml 'ports:'
contains PROJECT_DOCUMENTATION.md '本仓库不承诺 active-active HA'

for owner in \
  ulticode-auth ulticode-admin ulticode-app ulticode-submission \
  ulticode-search ulticode-notification ulticode-judge ulticode-ops \
  ulticode-health; do
  contains docker/redis/generate-users-acl.sh "user $owner"
done
contains docker-compose.ha.yml '${REDIS_ACL_DIR:?REDIS_ACL_DIR is required}/users.acl'
contains PROJECT_DOCUMENTATION.md 'P3-HA-001'
contains PROJECT_DOCUMENTATION.md 'mysql-replica'
contains PROJECT_DOCUMENTATION.md 'redis-sentinel-1'
contains PROJECT_DOCUMENTATION.md 'NACOS_SERVERS'

if [[ -n "${HA_COMPOSE_ENV_FILE:-}" ]]; then
  [[ -f "$HA_COMPOSE_ENV_FILE" ]] || fail "HA_COMPOSE_ENV_FILE does not exist"
  compose_files=(-f "$ROOT_DIR/docker-compose.yml")
  if [[ "${HA_COMPOSE_PROD:-1}" == "1" ]]; then
    compose_files+=(-f "$ROOT_DIR/docker-compose.prod.yml")
  else
    compose_files+=(-f "$ROOT_DIR/docker-compose.dev.yml")
  fi
  "$DOCKER_BIN" compose --env-file "$HA_COMPOSE_ENV_FILE" \
    "${compose_files[@]}" \
    -f "$ROOT_DIR/docker-compose.ha.yml" \
    --profile ha config >/dev/null \
    || fail "HA Compose profile does not expand"
  echo "HA Compose profile expansion: PASS"
else
  echo "HA Compose profile expansion: BLOCKED_EXTERNAL (HA_COMPOSE_ENV_FILE unset)"
fi

if [[ "${HA_RECONNECT_DRILL:-0}" == "1" ]]; then
  if ! command -v "$DOCKER_BIN" >/dev/null 2>&1 || ! "$DOCKER_BIN" info >/dev/null 2>&1; then
    echo "Redis restart/reconnect drill: BLOCKED_EXTERNAL (Docker daemon unavailable)"
  else
    container="ulticode-ha-reconnect-$$"
    drill_image="${HA_DRILL_REDIS_IMAGE:-redis:7-alpine}"
    cleanup() {
      "$DOCKER_BIN" rm -f "$container" >/dev/null 2>&1 || true
    }
    trap cleanup EXIT
    "$DOCKER_BIN" run -d --rm --name "$container" \
      "$drill_image" redis-server --appendonly yes >/dev/null
    for _ in {1..30}; do
      [[ "$("$DOCKER_BIN" exec "$container" redis-cli ping 2>/dev/null || true)" == "PONG" ]] \
        && break
      sleep 1
    done
    [[ "$("$DOCKER_BIN" exec "$container" redis-cli ping 2>/dev/null || true)" == "PONG" ]] \
      || fail "disposable Redis did not become ready"
    "$DOCKER_BIN" exec "$container" redis-cli SET ha-reconnect survived >/dev/null
    "$DOCKER_BIN" restart "$container" >/dev/null
    for _ in {1..30}; do
      [[ "$("$DOCKER_BIN" exec "$container" redis-cli ping 2>/dev/null || true)" == "PONG" ]] \
        && break
      sleep 1
    done
    [[ "$("$DOCKER_BIN" exec "$container" redis-cli GET ha-reconnect 2>/dev/null || true)" == "survived" ]] \
      || fail "Redis state did not survive the restart/reconnect check"
    echo "Redis restart/reconnect drill: PASS"
  fi
else
  echo "Redis restart/reconnect drill: BLOCKED_EXTERNAL (set HA_RECONNECT_DRILL=1 in an authorized disposable Docker environment)"
fi

echo "HA profile contract: PASS"
