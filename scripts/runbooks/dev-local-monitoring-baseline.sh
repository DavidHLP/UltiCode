#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
ACTION="${1:-baseline}"

case "$ACTION" in
  baseline|quiesce-preflight)
    ;;
  *)
    echo "Usage: $0 [baseline|quiesce-preflight]" >&2
    exit 2
    ;;
esac

load_env_file

MONITORING_DB_HOST="${MONITORING_DB_HOST:-${MIGRATION_DB_HOST:-}}"
MONITORING_DB_PORT="${MONITORING_DB_PORT:-${MIGRATION_DB_PORT:-}}"
MONITORING_DB_USER="${MONITORING_DB_USER:-${MIGRATION_DB_USER:-}}"
MONITORING_DB_PASSWORD="${MONITORING_DB_PASSWORD:-${MIGRATION_DB_PASSWORD:-}}"
: "${MONITORING_DB_HOST:?MONITORING_DB_HOST or MIGRATION_DB_HOST is required}"
: "${MONITORING_DB_PORT:?MONITORING_DB_PORT or MIGRATION_DB_PORT is required}"
: "${MONITORING_DB_USER:?MONITORING_DB_USER or MIGRATION_DB_USER is required}"
: "${MONITORING_DB_PASSWORD:?MONITORING_DB_PASSWORD or MIGRATION_DB_PASSWORD is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"
MYSQL_CONTAINER="${MIGRATION_MYSQL_CONTAINER:-${MYSQL_CONTAINER:-}}"
MYSQL_CONTAINER_PORT="${MIGRATION_MYSQL_CONTAINER_PORT:-3306}"
REDIS_CONTAINER="${REDIS_CONTAINER:-ulticode-redis}"

if [[ -n "$MYSQL_CONTAINER" ]]; then
  container_running "$MYSQL_CONTAINER" \
    || { echo "MySQL container is not running: $MYSQL_CONTAINER" >&2; exit 1; }
  mysql_container_targets_configured_host "$MYSQL_CONTAINER" "$MYSQL_CONTAINER_PORT" \
    "$MONITORING_DB_HOST" "$MONITORING_DB_PORT" || {
    echo "Configured monitoring target $MONITORING_DB_HOST:$MONITORING_DB_PORT is not a published endpoint of $MYSQL_CONTAINER:$MYSQL_CONTAINER_PORT" >&2
    exit 1
  }
fi

safe_http_code() {
  local url="$1" code
  code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$url" 2>/dev/null || true)"
  printf '%s' "${code:-000}"
}

# Single-sourced connection adapter (scripts/dev/lib/sql.sh).
define_mysql_query_adapter mysql_query \
  "$MYSQL_CONTAINER" "$MYSQL_CONTAINER_PORT" \
  "$MONITORING_DB_HOST" "$MONITORING_DB_PORT" \
  "$MONITORING_DB_USER" "$MONITORING_DB_PASSWORD" \
  "$DB_NAME" \
  --batch --skip-column-names

print_route_config() {
  printf 'ROUTE mode=%s cutover_complete=%s\n' \
    "${APP_SUBMISSION_ROUTING_MODE:-unset}" "${SUBMISSION_CUTOVER_COMPLETE:-unset}"
}

print_health_baseline() {
  local service port path
  while IFS=$'\t' read -r service port path; do
    printf 'HEALTH service=%s url=http://127.0.0.1:%s%s http=%s\n' \
      "$service" "$port" "$path" "$(safe_http_code "http://127.0.0.1:$port$path")"
  done <<'EOF'
auth	9101	/api/v1/auth/health
admin	9102	/api/v1/admin/health
app	9103	/api/v1/app/health
notification	9105	/api/v1/notification/health
EOF
}

print_runtime_inventory() {
  printf 'WRITER_INVENTORY source=ecosystem.config.cjs\n'
  node - <<'NODE'
const config = require('./ecosystem.config.cjs')
for (const app of config.apps.filter((entry) => entry.name.startsWith('ulticode-'))) {
  console.log(`WRITER name=${app.name} script=${app.script} args=${app.args}`)
}
NODE
  local pm2_output
  if pm2_output="$(pm2 list --no-color 2>/dev/null)"; then
    if [[ "$pm2_output" == *"ulticode-"* ]]; then
      printf '%s\n' "$pm2_output" | awk '/ulticode-/ {print "PM2 " $0}'
    else
      echo 'PM2 status=unavailable (no ulticode processes)'
    fi
  else
    echo 'PM2 status=unavailable (no PM2 daemon or apps)'
  fi
}

print_container_baseline() {
  docker ps --format 'CONTAINER name={{.Names}} image={{.Image}} status={{.Status}}' | sort
  local redis_ping redis_length redis_pending
  redis_ping="$(docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "$REDIS_CONTAINER" redis-cli --no-auth-warning ping 2>/dev/null || printf 'unavailable')"
  printf 'REDIS ping=%s\n' "$redis_ping"
  for stream in integration judge-stream; do
    redis_length="$(docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "$REDIS_CONTAINER" redis-cli --no-auth-warning XLEN "$stream" 2>/dev/null || printf 'unavailable')"
    redis_pending="$(docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "$REDIS_CONTAINER" redis-cli --no-auth-warning XPENDING "$stream" 2>/dev/null | awk 'NR == 1 {print $1}' || printf 'unavailable')"
    if [[ "$redis_pending" == ERR* ]]; then
      redis_pending=unavailable
    fi
    printf 'REDIS stream=%s length=%s pending=%s\n' "$stream" "$redis_length" "$redis_pending"
  done
}

print_database_baseline() {
  local schema_count output
  if ! schema_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('ulticode','auth','admin','app','notification','submission');" 2>&1)"; then
    printf 'DATABASE status=unavailable reason=%s\n' "$schema_count"
    return 0
  fi
  if [[ "$schema_count" != "6" ]]; then
    printf 'DATABASE status=unavailable reason=visible_owner_schema_count_%s\n' "$schema_count"
    return 0
  fi
  if output="$(mysql_query "SELECT table_schema,table_name,table_rows FROM information_schema.tables WHERE table_schema IN ('ulticode','auth','admin','app','notification','submission') AND table_name IN ('submissions','judge_outbox','submission_result_outbox','submission_created_outbox','search_document_changed_outbox','notification_delivery_ledger','consumer_inbox') ORDER BY table_schema,table_name;" 2>&1)"; then
    printf '%s\n' "$output"
  else
    printf 'DATABASE status=unavailable reason=%s\n' "$output"
  fi
}

print_quiesce_preflight() {
  local output status
  set +e
  output="$(SUBMISSION_APP_DB_USER="${SUBMISSION_APP_DB_USER:-$DB_USER}" \
    SUBMISSION_APP_DB_HOST="${SUBMISSION_APP_DB_HOST:-%}" \
    ENV_FILE="$ENV_FILE" "$ROOT_DIR/scripts/runbooks/submission-schema-cutover.sh" preflight 2>&1)"
  status=$?
  set -e
  if [[ "$status" == "0" ]]; then
    echo 'QUIESCE_PREFLIGHT status=PASS (read-only; no writer was stopped)'
  else
    printf 'QUIESCE_PREFLIGHT status=BLOCKED reason=%s\n' "$(printf '%s' "$output" | awk 'NR <= 3 {printf "%s ", $0}')"
  fi
}

printf 'DEV-LOCAL monitoring baseline action=%s\n' "$ACTION"
print_route_config
print_health_baseline
print_runtime_inventory
print_container_baseline
print_database_baseline
print_quiesce_preflight
printf 'DEV-LOCAL monitoring baseline complete; production stability/deployment is not inferred.\n'
