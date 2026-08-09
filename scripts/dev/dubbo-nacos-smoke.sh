#!/usr/bin/env bash
#
# scripts/dev/dubbo-nacos-smoke.sh
# P1-INFRA-003: Real-Dubbo registration smoke.
#
# Spins up the dev infrastructure (MySQL + Redis + Nacos) via docker compose,
# runs Flyway migrations, then starts backend-auth (Dubbo 3.3.6 Triple +
# Nacos registry, dev namespace) long enough for the provider to register
# itself with Nacos. Queries the Nacos instance list and asserts that the
# service name `backend-auth` is present in the `DEFAULT_GROUP` of the
# `dev` namespace. Tears everything down on exit.
#
# This is the live acceptance check for P1-INFRA-003 acceptance criteria
# "service registers successfully with dev namespace". The unit test
# `DubboBootstrapConfigTest` only verifies that the configuration binds;
# this script proves the registration actually happens at runtime.
#
# Usage:
#   ./scripts/dev/dubbo-nacos-smoke.sh
#
# Exit codes:
#   0  Nacos registry contains the backend-auth instance
#

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
LOG_DIR="$ROOT_DIR/.dubbo-smoke"
mkdir -p "$LOG_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo ".env not found at $ENV_FILE. Run scripts/dev/init-env.sh first." >&2
  exit 1
fi

# shellcheck disable=SC1090
set +u
source "$ENV_FILE"
set -u

# Defensive: explicit exports keep the variables visible to child processes.
export MYSQL_ROOT_PASSWORD DB_ROOT_PASSWORD DB_HOST DB_PORT DB_USER DB_PASSWORD DB_NAME
export REDIS_HOST REDIS_PORT REDIS_PASSWORD REDIS_DB
export NACOS_HOST NACOS_PORT NACOS_NAMESPACE NACOS_GROUP NACOS_USERNAME NACOS_PASSWORD
export NACOS_AUTH_TOKEN NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE
export NACOS_SERVER_ADDR NACOS_GRPC_PORT
export JWT_SECRET
# Nacos Server runs with NACOS_AUTH_ENABLE=true; the dev admin account
# is created by scripts/security/bootstrap-nacos-user.sh into
# nacos_config.users. The Dubbo Nacos registry client must carry those
# same credentials or the register call is rejected (HTTP 403) and the
# instance never lands. application.yml reads DUBBO_REGISTRY_USERNAME /
# DUBBO_REGISTRY_PASSWORD; alias them to the .env values here.
export DUBBO_REGISTRY_USERNAME="$NACOS_USERNAME"
export DUBBO_REGISTRY_PASSWORD="$NACOS_PASSWORD"

SERVICE_NAME="${DUBBO_APPLICATION_NAME:-backend-auth}"
NACOS_BASE="${NACOS_BASE:-http://${NACOS_HOST:-127.0.0.1}:${NACOS_PORT:-28848}}"
NACOS_NAMESPACE="${DUBBO_NAMESPACE:-dev}"
NACOS_GROUP="DEFAULT_GROUP"
NACOS_INSTANCE_LIST_URL="${NACOS_BASE}/nacos/v1/ns/instance/list?serviceName=${SERVICE_NAME}&groupName=${NACOS_GROUP}&namespaceId=${NACOS_NAMESPACE}"

compose=(docker compose --env-file "$ENV_FILE"
         -f "$ROOT_DIR/docker-compose.yml"
         -f "$ROOT_DIR/docker-compose.dev.yml")

cleanup() {
  local rc=$?
  echo
  echo "--- Cleanup (rc=$rc) ---"
  if [[ -n "${BACKEND_PID:-}" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "Stopping backend-auth (pid=$BACKEND_PID)..."
    for _ in 1 2 3 4 5; do
      kill -0 "$BACKEND_PID" 2>/dev/null || break
      sleep 2
    done
    kill -KILL "$BACKEND_PID" 2>/dev/null || true
  fi
  echo "Stopping dev infrastructure..."
  "${compose[@]}" down 2>&1 | tail -5 || true
  exit $rc
}
trap cleanup EXIT INT TERM

wait_for_container_health() {
  local container="$1"
  local attempts="${2:-60}"
  for ((i = 1; i <= attempts; i++)); do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      echo "  $container: $status (after ${i} probes)"
      return 0
    fi
    sleep 2
  done
  echo "Container $container did not become healthy after $((attempts * 2))s." >&2
  docker logs --tail 100 "$container" 2>&1 || true
  return 1
}

echo "--- 1. Starting MySQL + Redis + Nacos ---"
# Drop the MySQL volume to start from a clean DB. The smoke environment is
# disposable; the Phase 0 migrations leave a few `flyway_schema_history`
# entries behind on an existing local volume that would otherwise cause
# "Table 'oauth_provider_identities' already exists" on the first migrate.
docker volume rm ulticode_mysql_data 2>/dev/null || true
"${compose[@]}" up -d --force-recreate 2>&1 | tail -10
wait_for_container_health ulticode-mysql
wait_for_container_health ulticode-redis
wait_for_container_health ulticode-nacos

# Wait for Nacos Server to finish its own MySQL schema bootstrap
# (initdb/01-nacos-init.sql runs the first time MySQL starts up; the
# nacos_config database and its tables only land after MySQL is
# healthy AND the init container has finished). The container health
# check above only proves the JVM is up. Without this sleep, the
# bootstrap-nacos-user.sh INSERTs would fail with "Table
# 'nacos_config.users' doesn't exist".
echo "Waiting for Nacos schema to initialise (up to 30 s)..."
schema_ok=0
for i in $(seq 1 15); do
  if docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" ulticode-mysql mysql -uroot nacos_config -e "SHOW TABLES LIKE 'users';" 2>/dev/null | grep -q users; then
    schema_ok=1
    break
  fi
  sleep 2
done
if [[ "$schema_ok" -ne 1 ]]; then
  echo "nacos_config schema did not initialise in 30 s; aborting." >&2
  exit 1
fi
echo "  nacos_config.users present."

echo "--- 2. Provisioning Nacos administrator ---"
# Fail fast: if the admin account never lands, every subsequent
# DUBBO_REGISTRY_USERNAME/PASSWORD auth attempt to Nacos Server is
# rejected (HTTP 403) and the register call silently fails — the
# smoke would then time out at 220 s without anything to show. So
# surface the error here and abort.
"$ROOT_DIR/scripts/security/bootstrap-nacos-user.sh"

echo "--- 3. Running Flyway migrations ---"
set +e
"$ROOT_DIR/scripts/dev/migrate.sh" migrate 2>&1 | tail -25
migrate_rc=${PIPESTATUS[0]}
set -e
if [[ $migrate_rc -ne 0 ]]; then
  echo "Flyway migrate failed (rc=$migrate_rc); aborting smoke." >&2
  exit 1
fi

echo "--- 3a. Installing backend-common into the local repo ---"
(
  cd "$ROOT_DIR/services"
  ./mvnw -pl platform/common -am -DskipTests -B install \
    >"$LOG_DIR/backend-common-install.log" 2>&1 \
    || { echo "backend-common install failed (see $LOG_DIR/backend-common-install.log)" >&2; tail -50 "$LOG_DIR/backend-common-install.log" >&2; exit 1; }
)

echo "--- 4. Starting backend-auth (Dubbo Triple + Nacos registry) ---"
(
  cd "$ROOT_DIR/services"
  SERVER_PORT=9101 \
    SPRING_PROFILES_ACTIVE=dev \
      timeout --kill-after=15 240 ./mvnw -pl auth -am \
        -Dspring-boot.run.profiles=dev \
        -Dmaven.test.skip=true \
        -Dspring-boot.run.fork=false \
        -B spring-boot:run >"$LOG_DIR/backend-auth.log" 2>&1 &
  echo $!
) >"$LOG_DIR/backend.pid"
BACKEND_PID="$(cat "$LOG_DIR/backend.pid")"
echo "  backend-auth pid=$BACKEND_PID (logs: $LOG_DIR/backend-auth.log)"

# Nacos 2.x with auth enabled requires a JWT accessToken for Open API calls.
# The registry client (Dubbo Nacos client) already performs login internally;
# this script mirrors the same flow so the smoke test can verify the instance
# list without relying on basic auth, which Nacos does not accept.
echo "--- 5. Obtaining Nacos access token ---"
NACOS_TOKEN=""
for attempt in $(seq 1 10); do
  token_response="$(curl -fsS -X POST \
      -d "username=${NACOS_USERNAME}" \
      -d "password=${NACOS_PASSWORD}" \
      "${NACOS_BASE}/nacos/v1/auth/users/login" 2>/dev/null || true)"
  if [[ -n "$token_response" ]] && [[ "$token_response" == *"accessToken"* ]]; then
    NACOS_TOKEN="$(echo "$token_response" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null || true)"
    if [[ -n "$NACOS_TOKEN" ]]; then
      echo "  Nacos token acquired."
      break
    fi
  fi
  sleep 2
done
if [[ -z "$NACOS_TOKEN" ]]; then
  echo "Failed to obtain Nacos access token; see Nacos log." >&2
  exit 1
fi

echo "--- 6. Waiting for backend-auth to register with Nacos ---"
REGISTERED=0
response=""
NACOS_INSTANCE_LIST_URL_WITH_TOKEN="${NACOS_INSTANCE_LIST_URL}&accessToken=${NACOS_TOKEN}"
for attempt in $(seq 1 44); do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "backend-auth exited before registering (see $LOG_DIR/backend-auth.log)." >&2
    tail -120 "$LOG_DIR/backend-auth.log" >&2
    exit 1
  fi
  response="$(curl -fsS "${NACOS_INSTANCE_LIST_URL_WITH_TOKEN}" 2>/dev/null || true)"
  if [[ -n "$response" ]] && [[ "$response" != *"\"hosts\":[]"* ]] \
      && [[ "$response" == *'"ip"'* ]]; then
    echo "  registered after ${attempt} probes (5s each):"
    echo "  $response" | head -c 400
    echo
    REGISTERED=1
    break
  fi
  sleep 5
done

if [[ "$REGISTERED" -ne 1 ]]; then
  echo "backend-auth did not register with Nacos in time." >&2
  echo "Last Nacos response: $response" >&2
  echo "--- backend-auth log tail ---" >&2
  tail -120 "$LOG_DIR/backend-auth.log" >&2
  exit 1
fi

echo "--- 7. Verifying instance metadata ---"
ip_count="$(echo "$response" | grep -o '"ip":"[^"]*"' | wc -l | tr -d ' ')"
if [[ "$ip_count" -lt 1 ]]; then
  echo "Instance list returned but no 'ip' field found." >&2
  echo "$response" >&2
  exit 1
fi
echo "  Nacos reports $ip_count live instance(s) for ${SERVICE_NAME}."

echo
echo "P1-INFRA-003 smoke: PASS"
echo "  service: ${SERVICE_NAME}"
echo "  group:   ${NACOS_GROUP}"
echo "  ns:      ${NACOS_NAMESPACE}"
echo "  url:     ${NACOS_INSTANCE_LIST_URL}"
