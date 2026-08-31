#!/usr/bin/env bash
#
# scripts/test/dubbo-nacos-smoke.sh
# P1-INFRA-003: Real-Dubbo registration smoke.
#
# Spins up the dev infrastructure (MySQL + Redis + Nacos) via docker compose,
# runs Flyway migrations, then starts backend-auth (Dubbo 3.3.6 Triple +
# Nacos registry, dev namespace) long enough for the provider to register
# itself with Nacos. Queries the Nacos instance list and asserts that the
# application-level service `backend-auth` is present in the `DEFAULT_GROUP`
# of the `dev` namespace. Tears everything down on exit.
#
# This is the live acceptance check for P1-INFRA-003 acceptance criteria
# "service registers successfully with dev namespace". The unit test
# `DubboBootstrapConfigTest` only verifies that the configuration binds;
# this script proves the registration actually happens at runtime.
#
# Usage:
#   ./scripts/test/dubbo-nacos-smoke.sh
#
# Exit codes:
#   0  Nacos registry contains the backend-auth instance
#

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
[[ "$ENV_FILE" == /* ]] || ENV_FILE="$ROOT_DIR/$ENV_FILE"
[[ -f "$ENV_FILE" && ! -L "$ENV_FILE" && -O "$ENV_FILE" ]] || {
  echo "DUBBO smoke ENV_FILE must be an owned regular file: $ENV_FILE" >&2
  exit 1
}
if ! env_mode="$(stat -c '%a' -- "$ENV_FILE")"; then
  echo "Unable to inspect DUBBO smoke ENV_FILE permissions: $ENV_FILE" >&2
  exit 1
fi
if (( 8#$env_mode & 077 )); then
  echo "DUBBO smoke ENV_FILE must be owner-only (mode 600 or stricter): $ENV_FILE" >&2
  exit 1
fi
umask 077
LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ulticode-dubbo-smoke.XXXXXX")"
readonly LOG_DIR
cleanup_early() {
  local rc=$?
  if [[ -n "${LOG_DIR:-}" ]] && [[ -d "$LOG_DIR" ]] && ! rm -rf -- "$LOG_DIR"; then
    echo "Failed to remove disposable smoke log directory" >&2
    ((rc == 0)) && rc=1
  fi
  exit "$rc"
}
trap cleanup_early EXIT

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"

readonly SMOKE_PATH="${PATH:-}"
readonly SMOKE_HOME="${HOME:-}"
readonly SMOKE_DOCKER_HOST_SET="${DOCKER_HOST+x}"
readonly SMOKE_DOCKER_HOST_VALUE="${DOCKER_HOST-}"
readonly SMOKE_DOCKER_CONTEXT_SET="${DOCKER_CONTEXT+x}"
readonly SMOKE_DOCKER_CONTEXT_VALUE="${DOCKER_CONTEXT-}"
readonly SMOKE_DOCKER_TLS_VERIFY_SET="${DOCKER_TLS_VERIFY+x}"
readonly SMOKE_DOCKER_TLS_VERIFY_VALUE="${DOCKER_TLS_VERIFY-}"
readonly SMOKE_DOCKER_CERT_PATH_SET="${DOCKER_CERT_PATH+x}"
readonly SMOKE_DOCKER_CERT_PATH_VALUE="${DOCKER_CERT_PATH-}"

set +u
load_env_file
set -u

# The env file is a data source for the disposable stack, not authority to
# replace the caller's executable path or Docker endpoint.
PATH="$SMOKE_PATH"
export PATH
if [[ -n "$SMOKE_DOCKER_HOST_SET" ]]; then export DOCKER_HOST="$SMOKE_DOCKER_HOST_VALUE"; else unset DOCKER_HOST; fi
if [[ -n "$SMOKE_DOCKER_CONTEXT_SET" ]]; then export DOCKER_CONTEXT="$SMOKE_DOCKER_CONTEXT_VALUE"; else unset DOCKER_CONTEXT; fi
if [[ -n "$SMOKE_DOCKER_TLS_VERIFY_SET" ]]; then export DOCKER_TLS_VERIFY="$SMOKE_DOCKER_TLS_VERIFY_VALUE"; else unset DOCKER_TLS_VERIFY; fi
if [[ -n "$SMOKE_DOCKER_CERT_PATH_SET" ]]; then export DOCKER_CERT_PATH="$SMOKE_DOCKER_CERT_PATH_VALUE"; else unset DOCKER_CERT_PATH; fi

if ! command -v mise >/dev/null 2>&1; then
  echo "mise is required for the Java 17 Dubbo smoke" >&2
  exit 1
fi
MAVEN=(mise exec java@zulu-17.68.203.0 -- ./mvnw)

# Keep cleanup-owned state out of the sourced env namespace.
BACKEND_PID=""

# The smoke is local-only. Generate missing disposable principals in memory so
# an older .env can exercise the authenticated path without being rewritten.
for ephemeral_redis_var in HEALTH_REDIS_PASSWORD REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD; do
  if [[ -z "${!ephemeral_redis_var:-}" ]]; then
    printf -v "$ephemeral_redis_var" '%s' "$(openssl rand -hex 32)"
    export "$ephemeral_redis_var"
  fi
done
for nacos_prefix in AUTH ADMIN APP SUBMISSION NOTIFICATION JUDGE; do
  nacos_user_var="${nacos_prefix}_NACOS_USERNAME"
  nacos_password_var="${nacos_prefix}_NACOS_PASSWORD"
  if [[ -z "${!nacos_user_var:-}" ]]; then
    printf -v "$nacos_user_var" 'ulticode-%s' "${nacos_prefix,,}"
    export "$nacos_user_var"
  fi
  if [[ -z "${!nacos_password_var:-}" ]]; then
    printf -v "$nacos_password_var" '%s' "$(openssl rand -hex 32)"
    export "$nacos_password_var"
  fi
done

# Defensive: explicit exports keep the variables visible to child processes.
export MYSQL_ROOT_PASSWORD DB_ROOT_PASSWORD DB_HOST DB_PORT DB_USER DB_PASSWORD DB_NAME
export REDIS_HOST REDIS_PORT REDIS_DB
export NACOS_HOST NACOS_PORT NACOS_NAMESPACE NACOS_GROUP NACOS_USERNAME NACOS_PASSWORD
export NACOS_AUTH_TOKEN NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE
export NACOS_SERVER_ADDR NACOS_GRPC_PORT
export JWT_SECRET
DUBBO_NAMESPACE="${DUBBO_NAMESPACE:-dev}"
DUBBO_APPLICATION_NAME="${DUBBO_APPLICATION_NAME:-backend-auth}"
export DUBBO_NAMESPACE DUBBO_APPLICATION_NAME
export AUTH_NACOS_USERNAME AUTH_NACOS_PASSWORD ADMIN_NACOS_USERNAME ADMIN_NACOS_PASSWORD
export APP_NACOS_USERNAME APP_NACOS_PASSWORD SUBMISSION_NACOS_USERNAME SUBMISSION_NACOS_PASSWORD
export NOTIFICATION_NACOS_USERNAME NOTIFICATION_NACOS_PASSWORD JUDGE_NACOS_USERNAME JUDGE_NACOS_PASSWORD
# backend-auth must use its deny-by-default Redis principal; the shared
# REDIS_PASSWORD fallback is intentionally not used by this authenticated smoke.
export REDIS_USERNAME=ulticode-auth REDIS_PASSWORD="$AUTH_REDIS_PASSWORD"
# The smoke exercises backend-auth with its own least-privilege registry user;
# bootstrap-nacos-user.sh provisions the same six service users before startup.
export DUBBO_REGISTRY_USERNAME="$AUTH_NACOS_USERNAME"
export DUBBO_REGISTRY_PASSWORD="$AUTH_NACOS_PASSWORD"

[[ "${AUTH_DB_USER:-}" =~ ^[A-Za-z0-9_]+$ ]] || {
  echo "AUTH_DB_USER must contain only letters, digits, or underscore" >&2
  exit 1
}
[[ -n "${AUTH_DB_PASSWORD:-}" ]] || {
  echo "AUTH_DB_PASSWORD is required for the authenticated backend smoke" >&2
  exit 1
}

SERVICE_NAME="${DUBBO_APPLICATION_NAME:-backend-auth}"
NACOS_BASE="${NACOS_BASE:-http://${NACOS_HOST:-127.0.0.1}:${NACOS_PORT:-28848}}"
NACOS_NAMESPACE="${DUBBO_NAMESPACE:-dev}"
NACOS_GROUP="DEFAULT_GROUP"
[[ "$SERVICE_NAME" =~ ^[A-Za-z0-9._-]{1,128}$ ]] || {
  echo "DUBBO_APPLICATION_NAME must be a safe service name" >&2
  exit 1
}
[[ "$NACOS_NAMESPACE" =~ ^[A-Za-z0-9._-]{1,128}$ ]] || {
  echo "DUBBO_NAMESPACE must be a safe namespace id" >&2
  exit 1
}
[[ "$NACOS_BASE" =~ ^http://(localhost|127\.0\.0\.1):[0-9]{1,5}$ ]] || {
  echo "DUBBO smoke only permits a loopback HTTP Nacos endpoint" >&2
  exit 1
}
NACOS_INSTANCE_LIST_URL="${NACOS_BASE}/nacos/v1/ns/instance/list?serviceName=${SERVICE_NAME}&groupName=${NACOS_GROUP}&namespaceId=${NACOS_NAMESPACE}"
NACOS_REQUEST_CONFIG="$LOG_DIR/nacos-request.conf"
REDIS_ACL_DIR="$LOG_DIR/redis-acl"
REDIS_ACL_FILE="$REDIS_ACL_DIR/users.acl"
BACKEND_ENV_FILE="$LOG_DIR/backend-auth.env"
readonly BACKEND_ENV_FILE
MIGRATION_ENV_FILE="$LOG_DIR/migration.env"
readonly MIGRATION_ENV_FILE
mkdir -p "$REDIS_ACL_DIR"
chmod 755 "$REDIS_ACL_DIR"
[[ -x "$ROOT_DIR/docker/redis/generate-users-acl.sh" ]] || {
  echo "Missing Redis ACL generator: docker/redis/generate-users-acl.sh" >&2
  exit 1
}
"$ROOT_DIR/docker/redis/generate-users-acl.sh" "$REDIS_ACL_FILE"
export REDIS_ACL_DIR REDIS_ACL_FILE

nacos_login() {
  local username="$1" password="$2" endpoint="$3"
  local encoded_username encoded_password
  encoded_username="$(printf '%s' "$username" | python3 -c \
    'import sys, urllib.parse; print(urllib.parse.quote_plus(sys.stdin.read()), end="")')"
  encoded_password="$(printf '%s' "$password" | python3 -c \
    'import sys, urllib.parse; print(urllib.parse.quote_plus(sys.stdin.read()), end="")')"
  printf 'username=%s&password=%s' "$encoded_username" "$encoded_password" \
    | curl --connect-timeout 2 --max-time 5 -fsS -X POST --data-binary @- "$endpoint"
}

nacos_request() {
  local endpoint="$1"
  printf 'url = "%s"\nheader = "Authorization: Bearer %s"\n' \
    "$endpoint" "$NACOS_TOKEN" > "$NACOS_REQUEST_CONFIG"
  curl --config "$NACOS_REQUEST_CONFIG" --connect-timeout 2 --max-time 5 -fsS
}

redact_smoke_output() {
  python3 -c '
import os
import re
import sys

value = sys.stdin.read()
for key, secret in os.environ.items():
    if secret and any(marker in key for marker in ("PASSWORD", "SECRET", "TOKEN", "PRIVATE_KEY")):
        value = value.replace(secret, "[REDACTED]")
value = re.sub(
    r"""(?i)(authorization\s*:\s*bearer\s+)[^\s,;}]+""",
    r"""\1[REDACTED]""",
    value,
)
value = re.sub(
    r"""(?i)(\"?(?:password|token|secret|private[-_]key)\"?\s*[:=]\s*)\"?[^,\s}\"]+""",
    r"""\1[REDACTED]""",
    value,
)
sys.stdout.write(value)
'
}

export COMPOSE_PROJECT_NAME="ulticode-dubbo-smoke-$$"
export NACOS_EXPECTED_DOCKER_PROJECT="$COMPOSE_PROJECT_NAME"
compose=(docker compose --env-file "$ENV_FILE"
         -f "$ROOT_DIR/docker-compose.yml"
         -f "$ROOT_DIR/docker-compose.dev.yml")

cleanup() {
  local rc=$?
  trap - EXIT INT TERM
  echo
  echo "--- Cleanup (rc=$rc) ---"
  if [[ -n "${BACKEND_PID:-}" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "Stopping backend-auth (pid=$BACKEND_PID)..."
    kill -TERM "$BACKEND_PID" 2>/dev/null || true
    for _ in $(seq 1 30); do
      kill -0 "$BACKEND_PID" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "$BACKEND_PID" 2>/dev/null; then
      echo "backend-auth did not stop gracefully; forcing termination" >&2
      kill -KILL "$BACKEND_PID" 2>/dev/null || true
    fi
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  echo "Stopping dev infrastructure..."
  if ! "${compose[@]}" down -v >"$LOG_DIR/compose-down.log" 2>&1; then
    echo "Failed to clean disposable Compose project $COMPOSE_PROJECT_NAME" >&2
    redact_smoke_output <"$LOG_DIR/compose-down.log" | tail -20 >&2 || true
    ((rc == 0)) && rc=1
  fi
  if ! rm -rf -- "$LOG_DIR"; then
    echo "Failed to remove disposable smoke log directory" >&2
    ((rc == 0)) && rc=1
  fi
  exit $rc
}
trap cleanup EXIT INT TERM

wait_for_container_health() {
  local service="$1"
  local container
  container="$(compose_service_container compose "$service")"
  local attempts="${2:-60}" interval_seconds="${3:-2}" status
  for ((health_attempt = 1; health_attempt <= attempts; health_attempt++)); do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
      "$container" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      return 0
    fi
    sleep "$interval_seconds"
  done
  echo "Container did not become healthy: $container" >&2
  redact_smoke_output < <(docker logs --tail 100 "$container" 2>&1) >&2 || true
  return 1
}

echo "--- 1. Starting MySQL + Redis + Nacos ---"
# The smoke environment is disposable and has its own Compose project, so
# remove only its project volumes before starting from a clean database.
if ! "${compose[@]}" down -v >"$LOG_DIR/compose-down-before.log" 2>&1; then
  echo "Failed to clear disposable Compose project $COMPOSE_PROJECT_NAME" >&2
  redact_smoke_output <"$LOG_DIR/compose-down-before.log" | tail -20 >&2 || true
  exit 1
fi
if ! "${compose[@]}" up -d --force-recreate >"$LOG_DIR/compose-up.log" 2>&1; then
  echo "Failed to start disposable Compose project $COMPOSE_PROJECT_NAME" >&2
  redact_smoke_output <"$LOG_DIR/compose-up.log" | tail -40 >&2 || true
  exit 1
fi
redact_smoke_output <"$LOG_DIR/compose-up.log" | tail -10
MYSQL_CONTAINER="$(compose_service_container compose mysql)"
export MYSQL_CONTAINER
wait_for_container_health mysql
wait_for_container_health redis
wait_for_container_health nacos
# (initdb/01-nacos-init.sql runs the first time MySQL starts up; the
# nacos_config database and its tables only land after MySQL is
# healthy AND the init container has finished). The container health
# check above only proves the JVM is up. Without this sleep, the
# bootstrap-nacos-user.sh INSERTs would fail with "Table
# 'nacos_config.users' doesn't exist".
echo "Waiting for Nacos schema to initialise (up to 30 s)..."
mysql_nacos() {
  {
    printf '%s\n' "$MYSQL_ROOT_PASSWORD"
    cat
  } | docker exec -i "$MYSQL_CONTAINER" \
    sh -c 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -uroot nacos_config'
}
mysql_root() {
  {
    printf '%s\n' "$MYSQL_ROOT_PASSWORD"
    cat
  } | docker exec -i "$MYSQL_CONTAINER" \
    sh -c 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -uroot'
}
provision_auth_owner_account() {
  local escaped_password="$AUTH_DB_PASSWORD"
  escaped_password="${escaped_password//\\/\\\\}"
  escaped_password="${escaped_password//\'/\'\'}"
  mysql_root <<SQL
ALTER USER '$AUTH_DB_USER'@'%' IDENTIFIED BY '$escaped_password';
ALTER USER '$AUTH_DB_USER'@'%' ACCOUNT UNLOCK;
SQL
}
schema_ok=0
for i in $(seq 1 15); do
  if mysql_nacos <<'SQL' 2>/dev/null | grep -q '^users$'; then
SHOW TABLES LIKE 'users';
SQL
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
if ! valid_port "$DB_PORT"; then
  echo "DB_PORT must be a valid local Compose port for the disposable smoke." >&2
  exit 1
fi
if ! {
  printf 'DB_HOST=%q\n' '127.0.0.1'
  printf 'DB_PORT=%q\n' "$DB_PORT"
  printf 'DB_NAME=%q\n' 'ulticode'
  printf 'DB_USER=%q\n' 'root'
  printf 'DB_PASSWORD=%q\n' "$MYSQL_ROOT_PASSWORD"
} >"$MIGRATION_ENV_FILE" && chmod 600 "$MIGRATION_ENV_FILE"; then
  echo "Failed to create protected migration environment file." >&2
  exit 1
fi
set +e
ENV_FILE="$MIGRATION_ENV_FILE" MIGRATION_DB_USER=root MIGRATION_DB_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  MAVEN_BIN="$ROOT_DIR/services/mvnw" \
  "$ROOT_DIR/scripts/dev/migrate.sh" migrate 2>&1 | redact_smoke_output | tail -25
migrate_rc=${PIPESTATUS[0]}
set -e
if [[ $migrate_rc -ne 0 ]]; then
  echo "Flyway migrate failed (rc=$migrate_rc); aborting smoke." >&2
  exit 1
fi
echo "--- 3c. Provisioning backend-auth owner account ---"
provision_auth_owner_account

echo "--- 3b. Waiting for the authenticated Nacos API ---"
nacos_auth_ready=0
for attempt in $(seq 1 30); do
  if nacos_login "$NACOS_USERNAME" "$NACOS_PASSWORD" \
      "${NACOS_BASE}/nacos/v1/auth/users/login" >/dev/null 2>&1; then
    nacos_auth_ready=1
    break
  fi
  sleep 2
done
if [[ "$nacos_auth_ready" -ne 1 ]]; then
  echo "Nacos authenticated API did not become ready; aborting smoke." >&2
  exit 1
fi

echo "--- 3a. Installing backend-common into the local repo ---"
(
  cd "$ROOT_DIR/services"
  "${MAVEN[@]}" -pl platform/common -am -DskipTests -B install \
    >"$LOG_DIR/backend-common-install.log" 2>&1 \
    || { echo "backend-common install failed (see $LOG_DIR/backend-common-install.log)" >&2; redact_smoke_output <"$LOG_DIR/backend-common-install.log" | tail -50 >&2; exit 1; }
)

write_backend_env() {
  if ! {
    printf 'PATH=%q\n' "$SMOKE_PATH"
    printf 'HOME=%q\n' "$SMOKE_HOME"
    printf 'AUTH_DB_HOST=%q\n' "$AUTH_DB_HOST"
    printf 'AUTH_DB_PORT=%q\n' "$AUTH_DB_PORT"
    printf 'AUTH_DB_NAME=%q\n' "$AUTH_DB_NAME"
    printf 'AUTH_DB_USER=%q\n' "$AUTH_DB_USER"
    printf 'AUTH_DB_PASSWORD=%q\n' "$AUTH_DB_PASSWORD"
    printf 'REDIS_HOST=%q\n' "$REDIS_HOST"
    printf 'REDIS_PORT=%q\n' "$REDIS_PORT"
    printf 'REDIS_DB=%q\n' "$REDIS_DB"
    printf 'REDIS_USERNAME=%q\n' 'ulticode-auth'
    printf 'REDIS_PASSWORD=%q\n' "$AUTH_REDIS_PASSWORD"
    printf 'JWT_SECRET=%q\n' "$JWT_SECRET"
    printf 'JWT_RSA_ENABLED=%q\n' "${JWT_RSA_ENABLED:-false}"
    printf 'JWT_RSA_PRIVATE_KEY=%q\n' "${JWT_RSA_PRIVATE_KEY:-}"
    printf 'INTERNAL_DELEGATION_PUBLIC_KEY=%q\n' "${INTERNAL_DELEGATION_PUBLIC_KEY:-}"
    printf 'INTERNAL_DELEGATION_KEY_ID=%q\n' "${INTERNAL_DELEGATION_KEY_ID:-}"
    printf 'BOOTSTRAP_DELEGATION_PUBLIC_KEY=%q\n' "${BOOTSTRAP_DELEGATION_PUBLIC_KEY:-}"
    printf 'BOOTSTRAP_DELEGATION_KEY_ID=%q\n' "${BOOTSTRAP_DELEGATION_KEY_ID:-}"
    printf 'DUBBO_APPLICATION_NAME=%q\n' 'backend-auth'
    printf 'DUBBO_NAMESPACE=%q\n' "$DUBBO_NAMESPACE"
    printf 'DUBBO_REGISTRY_ADDRESS=%q\n' "nacos://127.0.0.1:${NACOS_PORT}?namespace=${DUBBO_NAMESPACE}"
    printf 'DUBBO_REGISTRY_USERNAME=%q\n' "$AUTH_NACOS_USERNAME"
    printf 'DUBBO_REGISTRY_PASSWORD=%q\n' "$AUTH_NACOS_PASSWORD"
    printf 'SERVER_PORT=%q\n' '9101'
    printf 'SPRING_PROFILES_ACTIVE=%q\n' 'dev'
  } >"$BACKEND_ENV_FILE" && chmod 600 "$BACKEND_ENV_FILE"; then
    echo "Failed to create protected backend-auth environment file." >&2
    exit 1
  fi
}

echo "--- 4. Starting backend-auth (Dubbo Triple + Nacos registry) ---"
write_backend_env
(
  cd "$ROOT_DIR/services"
  env -i PATH="$SMOKE_PATH" HOME="$SMOKE_HOME" BACKEND_ENV_FILE="$BACKEND_ENV_FILE" \
    bash -c '
      set -a
      source "$BACKEND_ENV_FILE"
      set +a
      exec timeout --foreground --kill-after=15 240 \
        mise exec java@zulu-17.68.203.0 -- ./mvnw -f auth/pom.xml \
          -Dspring-boot.run.profiles=dev \
          -Dmaven.test.skip=true \
          -Dspring-boot.run.fork=false \
          -B spring-boot:run
    ' >"$LOG_DIR/backend-auth.log" 2>&1 &
  echo $!
) >"$LOG_DIR/backend.pid"
BACKEND_PID="$(cat "$LOG_DIR/backend.pid")"
echo "  backend-auth pid=$BACKEND_PID (logs: $LOG_DIR/backend-auth.log)"

echo "--- 4a. Waiting for backend-auth readiness ---"
auth_ready=0
for attempt in $(seq 1 45); do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "backend-auth exited before readiness (see backend log)." >&2
    redact_smoke_output <"$LOG_DIR/backend-auth.log" | tail -120 >&2
    exit 1
  fi
  readiness_code="$(curl -sS -o "$LOG_DIR/backend-ready.body" -w '%{http_code}' \
    --connect-timeout 2 --max-time 5 http://127.0.0.1:9101/api/v1/auth/health/ready \
    2>/dev/null || true)"
  if [[ "$readiness_code" == "200" ]]; then
    auth_ready=1
    break
  fi
  sleep 2
done
if [[ "$auth_ready" -ne 1 ]]; then
  echo "backend-auth did not become ready in time; refusing to treat registry-only startup as success." >&2
  redact_smoke_output <"$LOG_DIR/backend-auth.log" | tail -120 >&2
  exit 1
fi
echo "  backend-auth readiness: PASS"

# Nacos 2.x with auth enabled requires a JWT accessToken for Open API calls.
# The registry client (Dubbo Nacos client) already performs login internally;
# this script mirrors the same flow so the smoke test can verify the instance
# list without relying on basic auth, which Nacos does not accept.
echo "--- 5. Obtaining Nacos access token ---"
NACOS_TOKEN=""
for attempt in $(seq 1 10); do
  token_response="$(nacos_login "$NACOS_USERNAME" "$NACOS_PASSWORD" \
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
if [[ ! "$NACOS_TOKEN" =~ ^[A-Za-z0-9._~+/=-]+$ ]]; then
  echo "Nacos access token contains unsupported curl-config characters." >&2
  exit 1
fi

echo "--- 6. Waiting for backend-auth to register with Nacos ---"
REGISTERED=0
response=""
for attempt in $(seq 1 44); do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "backend-auth exited before registering (see $LOG_DIR/backend-auth.log)." >&2
    redact_smoke_output <"$LOG_DIR/backend-auth.log" | tail -120 >&2
    exit 1
  fi
  response="$(nacos_request "$NACOS_INSTANCE_LIST_URL" 2>/dev/null || true)"
  if [[ -n "$response" ]] && [[ "$response" != *"\"hosts\":[]"* ]] \
      && [[ "$response" == *'"ip"'* ]]; then
    echo "  registered after ${attempt} probes (5s each):"
    printf '  %s\n' "$response" | redact_smoke_output | head -c 400
    echo
    REGISTERED=1
    break
  fi
  sleep 5
done

if [[ "$REGISTERED" -ne 1 ]]; then
  echo "backend-auth did not register with Nacos in time." >&2
  echo "Last Nacos response:" >&2
  printf '%s\n' "$response" | redact_smoke_output >&2
  echo "--- backend-auth log tail ---" >&2
  redact_smoke_output <"$LOG_DIR/backend-auth.log" | tail -120 >&2
  exit 1
fi

echo "--- 7. Verifying instance metadata ---"
ip_count="$(echo "$response" | grep -o '"ip":"[^"]*"' | wc -l | tr -d ' ')"
if [[ "$ip_count" -lt 1 ]]; then
  echo "Instance list returned but no 'ip' field found." >&2
  printf '%s\n' "$response" | redact_smoke_output >&2
  exit 1
fi
echo "  Nacos reports $ip_count live instance(s) for ${SERVICE_NAME}."

metadata_ok="$(printf '%s' "$response" | python3 -c '
import json
import sys

payload = json.load(sys.stdin)
hosts = payload.get("hosts") or []
print("1" if any((host.get("metadata") or {}).get("dubbo.metadata-service.url-params") for host in hosts) else "0")
' 2>/dev/null || printf '0')"
if [[ "$metadata_ok" != "1" ]]; then
  echo "Nacos application instance is missing Dubbo metadata-service registration." >&2
  printf '%s\n' "$response" | redact_smoke_output >&2
  exit 1
fi
echo "  Dubbo application metadata: PASS"

echo
echo "P1-INFRA-003 smoke: PASS"
echo "  service: ${SERVICE_NAME}"
echo "  group:   ${NACOS_GROUP}"
echo "  ns:      ${NACOS_NAMESPACE}"
echo "  url:     ${NACOS_INSTANCE_LIST_URL}"
