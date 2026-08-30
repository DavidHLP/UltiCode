#!/usr/bin/env bash
set -euo pipefail

# Apply the small privileged chain that must run after every owner schema.
# Owner-scoped Flyway accounts cannot revoke grants in another owner schema.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"

if ! java -version >/dev/null 2>&1 && command -v mise >/dev/null 2>&1; then
  exec mise exec java@zulu-17.68.203.0 -- bash "$0" "$@"
fi

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"
capture_env_vars MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
  MIGRATION_DB_USER MIGRATION_DB_PASSWORD MAVEN_BIN MAVEN_POM
load_env_file
apply_env_overrides

MAVEN_BIN="${MAVEN_BIN:-$ROOT_DIR/services/mvnw}"
MAVEN_POM="${MAVEN_POM:-$ROOT_DIR/init-db/pom.xml}"
for variable in MIGRATION_DB_HOST MIGRATION_DB_PORT MIGRATION_DB_NAME \
    MIGRATION_DB_USER MIGRATION_DB_PASSWORD; do
  [[ -n "${!variable:-}" ]] || {
    echo "Post-owner migration preflight failed: $variable is required" >&2
    exit 1
  }
done
valid_identifier "$MIGRATION_DB_NAME" || {
  echo "Post-owner migration preflight failed: invalid database name" >&2
  exit 1
}
valid_identifier "$MIGRATION_DB_USER" || {
  echo "Post-owner migration preflight failed: invalid migration account name" >&2
  exit 1
}
valid_port "$MIGRATION_DB_PORT" || {
  echo "Post-owner migration preflight failed: invalid database port" >&2
  exit 1
}
[[ "$MIGRATION_DB_NAME" == "ulticode" ]] || {
  echo "Post-owner migration preflight failed: MIGRATION_DB_NAME must be ulticode" >&2
  exit 1
}
if [[ -n "${DB_USER:-}" && "$MIGRATION_DB_USER" == "$DB_USER" ]]; then
  echo "Post-owner migration preflight failed: migration account must differ from runtime account" >&2
  exit 1
fi

cd "$ROOT_DIR/init-db"
DB_HOST="$MIGRATION_DB_HOST" \
  DB_PORT="$MIGRATION_DB_PORT" \
  DB_NAME="$MIGRATION_DB_NAME" \
  DB_USER="$MIGRATION_DB_USER" \
  DB_PASSWORD="$MIGRATION_DB_PASSWORD" \
  "$MAVEN_BIN" -f "$MAVEN_POM" flyway:migrate \
  -Dflyway.configFiles="$ROOT_DIR/init-db/flyway-post-owner.conf" \
  --no-transfer-progress -B
