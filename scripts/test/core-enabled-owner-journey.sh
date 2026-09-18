#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKER_BIN="${DOCKER_BIN:-docker}"

blocked() {
  printf 'core-enabled-owner-journey: BLOCKED_EXTERNAL (%s)\n' "$1" >&2
  exit 1
}

if [[ "${CORE_ENABLED_OWNER_JOURNEY:-0}" != "1" ]]; then
  blocked 'set CORE_ENABLED_OWNER_JOURNEY=1 to request the disposable gate'
fi

command -v "$DOCKER_BIN" >/dev/null 2>&1 \
  || blocked 'Docker CLI is unavailable'
command -v timeout >/dev/null 2>&1 \
  || blocked 'timeout is required for bounded Docker preflight'
timeout 15s "$DOCKER_BIN" info >/dev/null 2>&1 \
  || blocked 'Docker daemon is unavailable'

for required in \
  "$ROOT_DIR/services/mvnw" \
  "$ROOT_DIR/services/core/pom.xml" \
  "$ROOT_DIR/services/auth/pom.xml" \
  "$ROOT_DIR/services/admin/pom.xml" \
  "$ROOT_DIR/services/core/src/main/java/com/ulticode/core/CoreOwnerContextManager.java" \
  "$ROOT_DIR/services/core/src/test/java/com/ulticode/core/CoreEnabledOwnerJourneyIT.java" \
  "$ROOT_DIR/init-db/flyway-auth.conf" \
  "$ROOT_DIR/init-db/flyway-admin.conf"; do
  [[ -f "$required" ]] || blocked "required repository input is unavailable: $required"
done

for owner in auth admin; do
  migration_dir="$ROOT_DIR/init-db/migrations/$owner"
  [[ -d "$migration_dir" ]] || blocked "required migration directory is unavailable: $migration_dir"
  shopt -s nullglob
  migrations=("$migration_dir"/*.sql)
  shopt -u nullglob
  (( ${#migrations[@]} > 0 )) || blocked "no canonical $owner migration files are available"
done

(
  cd "$ROOT_DIR/services"
  ./mvnw \
    -pl core -am \
    -Dtest='CoreEnabledOwnerJourneyIT' \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dcore.enabled.owner.journey=true \
    test -B
)
printf 'core-enabled-owner-journey: PASS (Auth/Admin child wiring and cleanup)\n'
