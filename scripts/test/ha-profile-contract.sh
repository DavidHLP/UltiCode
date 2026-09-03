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

external_blocked=0

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
contains docs/operations/deployment.md '本仓库不承诺 active-active HA'
contains services/docs/DEPENDENCY_RESILIENCE_RUNBOOK.md 'P5-INFRA-001'
contains services/docs/DEPENDENCY_RESILIENCE_RUNBOOK.md 'shared fault domain'
contains services/docs/DEPENDENCY_RESILIENCE_RUNBOOK.md 'not transparent failover'
contains docs/architecture/decisions/0001-deferred-platform-expansion.md 'P5-INFRA-003'
contains docs/architecture/decisions/0001-deferred-platform-expansion.md 'does not provide production failover'
contains docs/architecture/decisions/0001-deferred-platform-expansion.md 'Second host actually adopted'
contains docs/architecture/decisions/0001-deferred-platform-expansion.md 'Named operator/team and on-call'
contains docs/architecture/decisions/0001-deferred-platform-expansion.md 'Measured SLO/latency or capacity breach'
contains docs/architecture/decisions/0001-deferred-platform-expansion.md 'Real incident'
contains docs/architecture/decisions/0001-deferred-platform-expansion.md 'Explicit RTO/RPO'
contains docs/architecture/decisions/0001-deferred-platform-expansion.md 'Sustained mixed-version/independent release requirement'
contains docs/architecture/decisions/0001-deferred-platform-expansion.md 'Fault isolation requirement'
contains docs/architecture/decisions/0001-deferred-platform-expansion.md 'Evidence from deployment authority'

for owner in \
  ulticode-auth ulticode-admin ulticode-app ulticode-submission \
  ulticode-search ulticode-notification ulticode-judge ulticode-ops \
  ulticode-health; do
  contains docker/redis/generate-users-acl.sh "user $owner"
done
contains docker-compose.ha.yml '${REDIS_ACL_DIR:?REDIS_ACL_DIR is required}/users.acl'
contains docker/redis/generate-users-acl.sh 'user ulticode-replication'
contains docker/redis/generate-users-acl.sh 'user ulticode-sentinel'
contains docker/redis/generate-users-acl.sh '+psync'
contains docker/redis/generate-users-acl.sh '+replconf'
contains docker-compose.ha.yml 'redis-cli --user'
contains docs/operations/deployment.md 'masteruser ulticode-replication'
contains docs/operations/deployment.md 'sentinel auth-user'
contains docs/operations/deployment.md 'sentinel auth-user mymaster ulticode-sentinel'
contains docs/operations/deployment.md 'P3-HA-001'
contains docs/operations/deployment.md 'mysql-replica'
contains docs/operations/deployment.md 'redis-sentinel-1'
if [[ -n "${HA_COMPOSE_ENV_FILE:-}" ]]; then
  [[ -f "$HA_COMPOSE_ENV_FILE" ]] || fail "HA_COMPOSE_ENV_FILE does not exist"
  compose_files=(-f "$ROOT_DIR/docker-compose.yml")
  if [[ "${HA_COMPOSE_PROD:-1}" == "1" ]]; then
    compose_files+=(-f "$ROOT_DIR/docker-compose.prod.yml")
  else
    compose_files+=(-f "$ROOT_DIR/docker-compose.dev.yml")
  fi
  ha_config_dir="${REDIS_HA_CONFIG_DIR:-}"
  if [[ -z "$ha_config_dir" ]]; then
    ha_config_dir="$(sed -n 's/^REDIS_HA_CONFIG_DIR=//p' "$HA_COMPOSE_ENV_FILE" | sed -n '1p')"
  fi
  [[ -n "$ha_config_dir" ]] || fail "REDIS_HA_CONFIG_DIR is required for file checks"
  [[ -d "$ha_config_dir" ]] || fail "REDIS_HA_CONFIG_DIR does not exist"
  for config_file in redis-replica.conf sentinel-1.conf sentinel-2.conf sentinel-3.conf; do
    [[ -s "$ha_config_dir/$config_file" ]] || fail "missing HA config: $ha_config_dir/$config_file"
    if [[ "$config_file" == "redis-replica.conf" ]]; then
      grep -Eq '^[[:space:]]*replicaof[[:space:]]+redis[[:space:]]+6379([[:space:]]|$)' \
        "$ha_config_dir/$config_file" \
        || fail "replica config must target the redis primary"
      grep -Eq '^[[:space:]]*replica-read-only[[:space:]]+yes([[:space:]]|$)' \
        "$ha_config_dir/$config_file" \
        || fail "replica config must keep replica-read-only enabled"
      grep -Eq '^[[:space:]]*appendonly[[:space:]]+yes([[:space:]]|$)' \
        "$ha_config_dir/$config_file" \
        || fail "replica config must persist its dataset"
      grep -Eq '^[[:space:]]*masteruser[[:space:]]+ulticode-replication([[:space:]]|$)' \
        "$ha_config_dir/$config_file" \
        || fail "replica config must authenticate as ulticode-replication"
      grep -Eq '^[[:space:]]*masterauth[[:space:]]+[^[:space:]]+' \
        "$ha_config_dir/$config_file" \
        || fail "replica config must provide masterauth"
    else
      grep -Eq '^[[:space:]]*sentinel[[:space:]]+monitor[[:space:]]+mymaster[[:space:]]+redis[[:space:]]+6379[[:space:]]+2([[:space:]]|$)' \
        "$ha_config_dir/$config_file" \
        || fail "Sentinel config must monitor redis with quorum 2"
      grep -Eq '^[[:space:]]*sentinel[[:space:]]+auth-user[[:space:]]+mymaster[[:space:]]+ulticode-sentinel([[:space:]]|$)' \
        "$ha_config_dir/$config_file" \
        || fail "Sentinel config must authenticate as ulticode-sentinel"
      grep -Eq '^[[:space:]]*sentinel[[:space:]]+auth-pass[[:space:]]+mymaster[[:space:]]+[^[:space:]]+' \
        "$ha_config_dir/$config_file" \
        || fail "Sentinel config must provide auth-pass"
    fi
  done
  "$DOCKER_BIN" compose --env-file "$HA_COMPOSE_ENV_FILE" \
    "${compose_files[@]}" \
    -f "$ROOT_DIR/docker-compose.ha.yml" \
    --profile ha config >/dev/null \
    || fail "HA Compose profile does not expand"
  echo "HA Compose profile expansion: PASS"
else
  external_blocked=1
  echo "HA Compose profile expansion: BLOCKED_EXTERNAL (HA_COMPOSE_ENV_FILE unset)"
fi

if [[ "${HA_RECONNECT_DRILL:-0}" == "1" ]]; then
  if ! command -v "$DOCKER_BIN" >/dev/null 2>&1 || ! "$DOCKER_BIN" info >/dev/null 2>&1; then
    external_blocked=1
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
  external_blocked=1
  echo "Redis restart/reconnect drill: BLOCKED_EXTERNAL (set HA_RECONNECT_DRILL=1 in an authorized disposable Docker environment)"
fi

if (( external_blocked )); then
  echo "HA profile contract: PASS_WITH_EXTERNAL_BLOCKERS"
  if [[ "${HA_COMPOSE_REQUIRED:-0}" == "1" ]]; then
    fail "required external HA checks were blocked"
  fi
else
  echo "HA profile contract: PASS"
fi
