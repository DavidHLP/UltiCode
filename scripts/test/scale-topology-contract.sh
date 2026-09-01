#!/usr/bin/env bash
set -euo pipefail

# P3-SCALE-001 contract: the Compose topology must remain scale-safe. A
# repository-owned disposable smoke can exercise registration/removal/
# rolling-restart/failure when `DUBBO_NACOS_SMOKE_ENV_FILE` is supplied.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "scale-topology-contract: FAIL: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing source: $file"
  grep -Fq -- "$text" "$ROOT_DIR/$file" || fail "$file is missing: $text"
}

not_contains() {
  local file="$1" text="$2"
  ! grep -Fq -- "$text" "$ROOT_DIR/$file" || fail "$file must not contain: $text"
}

for compose in docker-compose.yml docker-compose.prod.yml; do
  not_contains "$compose" 'container_name:'
  not_contains "$compose" 'network_mode: host'
done

for service in backend-auth backend-admin backend-app backend-submission backend-search \
  backend-notification backend-judge; do
  contains docker-compose.prod.yml "  $service:"
done
contains services/auth/src/main/resources/application.yml 'port: ${DUBBO_PROTOCOL_PORT:20881}'

contains docker-compose.prod.yml 'DUBBO_REGISTRY_ADDRESS=nacos://nacos:8848'
contains docker-compose.prod.yml 'restart: unless-stopped'
contains docker-compose.prod.yml 'healthcheck:'
contains docker-compose.prod.yml 'expose:'
contains docker-compose.prod.yml 'deploy:'
contains docker-compose.yml 'networks:'
contains docker-compose.yml 'cache:'

printf 'production Compose scale-safe service names and discovery references: PASS\n'
printf 'production backend health/restart/resource declarations: PASS\n'

if [[ -n "${SCALE_COMPOSE_ENV_FILE:-}" ]]; then
  command -v docker >/dev/null 2>&1 || fail 'SCALE_COMPOSE_ENV_FILE requires docker'
  docker compose --env-file "$SCALE_COMPOSE_ENV_FILE" \
    -f "$ROOT_DIR/docker-compose.yml" -f "$ROOT_DIR/docker-compose.prod.yml" config >/dev/null
  printf 'production Compose merged config expansion: PASS\n'
else
  printf 'production Compose merged config expansion: BLOCKED_EXTERNAL (SCALE_COMPOSE_ENV_FILE is unset)\n'
fi

if [[ -n "${DUBBO_NACOS_SMOKE_ENV_FILE:-}" ]]; then
  [[ -f "$DUBBO_NACOS_SMOKE_ENV_FILE" ]] \
    || fail "DUBBO_NACOS_SMOKE_ENV_FILE does not exist"
  ENV_FILE="$DUBBO_NACOS_SMOKE_ENV_FILE" \
    DUBBO_NACOS_SMOKE_REPLICAS=2 \
    bash "$ROOT_DIR/scripts/test/dubbo-nacos-smoke.sh"
  printf 'two-instance registration/distribution/removal/rolling-restart/failure drill: PASS\n'
else
  printf 'two-instance registration/distribution/removal/rolling-restart/failure drill: BLOCKED_EXTERNAL (set DUBBO_NACOS_SMOKE_ENV_FILE for the disposable repository smoke)\n'
fi
printf 'scale-topology-contract: PASS\n'
