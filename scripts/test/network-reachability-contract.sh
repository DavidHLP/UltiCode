#!/usr/bin/env bash
set -euo pipefail

# P3-NET-001: keep production service networks aligned with the real call graph.
# Static membership is authoritative for the repository topology. The optional
# disposable drill proves that an allowed Docker network path works while an
# isolated network path cannot resolve the target. It does not prove production
# firewall or host policy.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROD_COMPOSE="$ROOT_DIR/docker-compose.prod.yml"
BASE_COMPOSE="$ROOT_DIR/docker-compose.yml"
OBS_COMPOSE="$ROOT_DIR/docker-compose.observability.yml"
DOCKER_BIN="${DOCKER_BIN:-docker}"

fail() {
  echo "network-reachability-contract: FAIL: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing source: $file"
  grep -Fq -- "$text" "$ROOT_DIR/$file" || fail "$file is missing: $text"
}

not_contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing source: $file"
  ! grep -Fq -- "$text" "$ROOT_DIR/$file" || fail "$file contains forbidden value: $text"
}

service_networks() {
  local file="$1" service="$2"
  awk -v target="  $service:" '
    $0 == target { inside = 1; next }
    inside && /^  [^ ]/ { exit }
    inside && /^    networks:$/ { in_networks = 1; next }
    inside && in_networks && /^    [^ ]/ { exit }
    inside && in_networks && /^      - / { print $2 }
  ' "$file"
}

assert_networks() {
  local file="$1" service="$2"; shift 2
  local actual expected
  actual="$(service_networks "$file" "$service" | sort)"
  expected="$(printf '%s\n' "$@" | sort)"
  [[ "$actual" == "$expected" ]] || fail "$service networks expected [$*], got [$actual]"
}

assert_no_service_key() {
  local file="$1" service="$2" key="$3" source
  source="$file"
  [[ "$source" == /* ]] || source="$ROOT_DIR/$source"
  [[ -f "$source" ]] || fail "missing source: $source"
  if awk -v target="  $service:" -v key="$key" '
    $0 == target { inside = 1; next }
    inside && /^  [^ ]/ { exit }
    inside && $0 ~ "^    " key ":" { found = 1 }
    END { exit found ? 0 : 1 }
  ' "$source"; then
    fail "$service must not declare $key"
  fi
}
assert_network_property() {
  local file="$1" network="$2" key="$3" expected="$4"
  awk -v target="  $network:" -v key="$key" -v expected="$expected" '
    $0 == target { inside = 1; next }
    inside && /^  [^ ]/ { exit }
    inside && $1 == key ":" { found = ($2 == expected); exit }
    END { exit found ? 0 : 1 }
  ' "$file" || fail "$file $network must set $key=$expected"
}
assert_loopback_ports() {
  local file="$1" service="$2"
  if awk -v target="  $service:" '
    $0 == target { inside = 1; next }
    inside && /^  [^ ]/ { exit }
    inside && /^    ports:$/ { in_ports = 1; next }
    inside && in_ports && /^    [^ ]/ { exit }
    inside && in_ports && /^      - / && $0 !~ /127[.]0[.]0[.]1:/ { bad = 1 }
    END { exit bad ? 0 : 1 }
  ' "$file"; then
    fail "$service ports must bind to loopback"
  fi
}



for network in edge sql cache registry rpc-auth rpc-app rpc-submission \
  rpc-notification rpc-judge search observability; do
  contains docker-compose.yml "  $network:"
  assert_network_property "$BASE_COMPOSE" "$network" internal true
done
for network in egress-auth egress-admin egress-app egress-submission egress-search \
  egress-notification egress-judge egress-nacos; do
  contains docker-compose.yml "  $network:"
  assert_network_property "$BASE_COMPOSE" "$network" internal false
done
assert_networks "$BASE_COMPOSE" mysql sql
assert_networks "$BASE_COMPOSE" redis cache
assert_networks "$BASE_COMPOSE" nacos sql registry egress-nacos
assert_networks "$BASE_COMPOSE" meilisearch search

for network in sql cache registry search; do
  contains docker-compose.dev.yml "  $network:"
  assert_network_property "$ROOT_DIR/docker-compose.dev.yml" "$network" internal false
done
for network in edge rpc-auth rpc-app rpc-submission rpc-notification rpc-judge observability; do
  not_contains docker-compose.dev.yml "  $network:"
done
assert_networks "$PROD_COMPOSE" backend-auth edge sql cache registry rpc-auth observability egress-auth
assert_networks "$PROD_COMPOSE" backend-admin edge sql cache registry rpc-auth rpc-app rpc-submission rpc-notification observability egress-admin
assert_networks "$PROD_COMPOSE" backend-app edge sql cache registry rpc-auth rpc-app rpc-submission rpc-judge search observability egress-app
assert_networks "$PROD_COMPOSE" backend-submission sql cache registry rpc-auth rpc-app rpc-submission observability egress-submission
assert_networks "$PROD_COMPOSE" backend-search cache search observability egress-search
assert_networks "$PROD_COMPOSE" backend-notification edge sql cache registry rpc-auth rpc-notification observability egress-notification
assert_networks "$PROD_COMPOSE" backend-judge cache registry rpc-app rpc-submission rpc-judge observability egress-judge
assert_networks "$PROD_COMPOSE" console edge
assert_networks "$PROD_COMPOSE" management edge

for service in backend-auth backend-admin backend-app backend-submission backend-search \
  backend-notification backend-judge; do
  assert_no_service_key "$PROD_COMPOSE" "$service" ports
done

# Infrastructure and worker-plane isolation: these services must not inherit a
# broad default/infrastructure network or an ingress edge network accidentally.
for file in docker-compose.yml docker-compose.prod.yml docker-compose.ha.yml; do
  not_contains "$file" '      - default'
  not_contains "$file" '      - infrastructure'
  for service in mysql redis nacos meilisearch backend-auth backend-admin backend-app \
    backend-submission backend-search backend-notification backend-judge console management \
    mysql-replica redis-replica redis-sentinel-1 redis-sentinel-2 redis-sentinel-3 nacos-2 nacos-3; do
    assert_no_service_key "$file" "$service" network_mode
  done
done
for file in "$BASE_COMPOSE" "$PROD_COMPOSE"; do
  for service in mysql redis nacos meilisearch; do
    assert_no_service_key "$file" "$service" ports
  done
done
assert_loopback_ports "$PROD_COMPOSE" console
assert_loopback_ports "$PROD_COMPOSE" management
assert_networks "$ROOT_DIR/docker-compose.ha.yml" mysql-replica sql
assert_networks "$ROOT_DIR/docker-compose.ha.yml" redis-replica cache
assert_networks "$ROOT_DIR/docker-compose.ha.yml" redis-sentinel-1 cache
assert_networks "$ROOT_DIR/docker-compose.ha.yml" redis-sentinel-2 cache
assert_networks "$ROOT_DIR/docker-compose.ha.yml" redis-sentinel-3 cache
assert_networks "$ROOT_DIR/docker-compose.ha.yml" nacos-2 sql registry egress-nacos
assert_networks "$ROOT_DIR/docker-compose.ha.yml" nacos-3 sql registry egress-nacos

# Optional observability reaches only the dedicated telemetry network.
for service in otel-collector prometheus alertmanager tempo loki grafana; do
  assert_networks "$OBS_COMPOSE" "$service" observability
done

printf 'service network membership and call-graph contract: PASS\n'
printf 'ingress/data/search/judge/observability isolation: PASS\n'

if [[ -n "${NET_COMPOSE_ENV_FILE:-}" ]]; then
  [[ -f "$NET_COMPOSE_ENV_FILE" ]] || fail "NET_COMPOSE_ENV_FILE does not exist"
  "$DOCKER_BIN" compose --env-file "$NET_COMPOSE_ENV_FILE" \
    -f "$BASE_COMPOSE" -f "$ROOT_DIR/docker-compose.prod.yml" config >/dev/null \
    || fail 'base/prod Compose network expansion failed'
  printf 'production Compose network expansion: PASS\n'
  "$DOCKER_BIN" compose --env-file "$NET_COMPOSE_ENV_FILE" \
    -f "$BASE_COMPOSE" -f "$ROOT_DIR/docker-compose.dev.yml" config >/dev/null \
    || fail 'base/dev Compose network expansion failed'
  printf 'base/dev Compose network expansion: PASS\n'
else
  printf 'production Compose expansion: BLOCKED_EXTERNAL (NET_COMPOSE_ENV_FILE unset; required production secrets are not fabricated)\n'
fi

runtime_blocked=0
if ! command -v "$DOCKER_BIN" >/dev/null 2>&1 || ! "$DOCKER_BIN" info >/dev/null 2>&1; then
  runtime_blocked=1
  printf 'allowed/forbidden Docker reachability drill: BLOCKED_EXTERNAL (Docker daemon unavailable)\n'
else
  suffix="${RANDOM}_$$"
  allowed_network="ulticode-net-allowed-$suffix"
  forbidden_network="ulticode-net-forbidden-$suffix"
  target="ulticode-net-target-$suffix"
  cleanup() {
    "$DOCKER_BIN" rm -f "$target" >/dev/null 2>&1 || true
    "$DOCKER_BIN" network rm "$allowed_network" "$forbidden_network" >/dev/null 2>&1 || true
  }
  trap cleanup EXIT
  "$DOCKER_BIN" network create --internal "$allowed_network" >/dev/null
  "$DOCKER_BIN" network create --internal "$forbidden_network" >/dev/null
  "$DOCKER_BIN" run -d --rm --name "$target" --network "$allowed_network" \
    redis:7-alpine redis-server --save '' --appendonly no >/dev/null
  for _ in {1..30}; do
    [[ "$("$DOCKER_BIN" exec "$target" redis-cli ping 2>/dev/null || true)" == "PONG" ]] && break
    sleep 1
  done
  [[ "$("$DOCKER_BIN" exec "$target" redis-cli ping 2>/dev/null || true)" == "PONG" ]] \
    || fail 'disposable network target did not become ready'
  "$DOCKER_BIN" run --rm --network "$allowed_network" redis:7-alpine \
    redis-cli -h "$target" ping | grep -Fxq PONG \
    || fail 'allowed network path did not reach target'
  if "$DOCKER_BIN" run --rm --network "$forbidden_network" redis:7-alpine \
    redis-cli -h "$target" ping >/dev/null 2>&1; then
    fail 'forbidden network path reached target'
  fi
  printf 'allowed/forbidden Docker reachability drill: PASS\n'
fi

if (( runtime_blocked )); then
  printf 'network-reachability-contract: PASS_WITH_EXTERNAL_BLOCKERS\n'
else
  printf 'network-reachability-contract: PASS\n'
fi
