#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
COMMAND="${1:-migrate}"
DB_NAME_OVERRIDE="${MIGRATION_DB_NAME:-}"

case "$COMMAND" in
  migrate|validate|info|repair)
    ;;
  *)
    echo "Unsupported Flyway command: $COMMAND" >&2
    exit 2
    ;;
esac

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Run ./scripts/dev/init-env.sh first." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [[ -n "$DB_NAME_OVERRIDE" ]]; then
  export DB_NAME="$DB_NAME_OVERRIDE"
fi

# Optional privileged migration account override.
# The per-owner shadow-user migrations (CREATE USER, cross-schema GRANT) require
# DBA-level privileges that the runtime application account must NOT hold.
# MICROSERVICE_MIGRATION_GUIDE.md documents a dedicated root migration account for
# the Phase 5-6 transition. Callers already running with sufficient privileges (or
# older migration chains that need none) leave MIGRATION_DB_USER unset and get the
# default DB_USER from .env, preserving existing behavior.
: "${MIGRATION_DB_USER:=$DB_USER}"
: "${MIGRATION_DB_PASSWORD:=$DB_PASSWORD}"
export DB_USER="$MIGRATION_DB_USER"
export DB_PASSWORD="$MIGRATION_DB_PASSWORD"

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:?DB_PORT is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${DB_NAME:?DB_NAME is required}"

cd "$ROOT_DIR/init-db"

run_flyway() {
  local config_file="flyway.conf"
  if [[ -n "${MIGRATION_SCHEMA:-}" && -f "flyway-${MIGRATION_SCHEMA}.conf" ]]; then
    config_file="flyway-${MIGRATION_SCHEMA}.conf"
  fi
  mvn "flyway:$1" \
    -Dflyway.configFiles="$config_file" \
    --no-transfer-progress \
    -B
}

# 自愈: migrate 失败(checksum 漂移 / failed migration 记录残留 / 历史遗留 baseline-增量冲突)时,
# repair 清理 flyway_schema_history 中的失败记录后重试一次。根因(如迁移文件本身冲突)仍需人工修。
if [[ "$COMMAND" == "migrate" ]]; then
  if ! run_flyway migrate; then
    echo "Flyway migrate failed; running repair then retrying..." >&2
    run_flyway repair
    run_flyway migrate
  fi
else
  run_flyway "$COMMAND"
fi
