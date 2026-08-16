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

if [[ -n "${NOTIFICATION_DB_NAME:-}" && "$NOTIFICATION_DB_NAME" != "notification" ]]; then
  echo "NOTIFICATION_DB_NAME must be 'notification' with flyway-notification.conf; arbitrary owner database names are not supported by this migration set." >&2
  exit 1
fi

if [[ -n "${SUBMISSION_DB_NAME:-}" && "$SUBMISSION_DB_NAME" != "submission" ]]; then
  echo "SUBMISSION_DB_NAME must be 'submission' with flyway-submission.conf; arbitrary owner database names are not supported by this migration set." >&2
  exit 1
fi

cd "$ROOT_DIR/init-db"

run_flyway_config() {
  local config_file="$1"
  local flyway_command="$2"
  if [[ -n "${MIGRATION_SCHEMA:-}" && -f "flyway-${MIGRATION_SCHEMA}.conf" ]]; then
    config_file="flyway-${MIGRATION_SCHEMA}.conf"
  fi
  mvn "flyway:$flyway_command" \
    -Dflyway.configFiles="$config_file" \
    --no-transfer-progress \
    -B
}

run_flyway() {
  run_flyway_config "flyway.conf" "$1"
}

# 仅保留既有主库迁移的自愈行为。owner schema 的历史漂移必须显式处理，
# 不能自动 repair 后继续，否则可能把版本冲突伪装成成功。
if [[ "$COMMAND" == "migrate" ]]; then
  if [[ -n "${MIGRATION_SCHEMA:-}" ]]; then
    run_flyway migrate
  elif ! run_flyway migrate; then
    echo "Flyway migrate failed; running repair then retrying..." >&2
    run_flyway repair
    run_flyway migrate
    run_flyway_config "flyway-notification.conf" migrate
  else
    run_flyway_config "flyway-notification.conf" migrate
  fi
else
  run_flyway "$COMMAND"
  if [[ -z "${MIGRATION_SCHEMA:-}" ]]; then
    run_flyway_config "flyway-notification.conf" "$COMMAND"
  fi
fi
