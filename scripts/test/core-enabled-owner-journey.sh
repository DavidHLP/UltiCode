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

CORE_REPORT="$ROOT_DIR/services/core/target/surefire-reports/com.ulticode.core.CoreEnabledOwnerJourneyIT.txt"
rm -f -- "$CORE_REPORT"
CORE_TMP_DIR="$(mktemp -d)"
trap 'rm -rf -- "$CORE_TMP_DIR"' EXIT
MAVEN_OUTPUT="$(mktemp)"
trap 'rm -f -- "$MAVEN_OUTPUT"; rm -rf -- "$CORE_TMP_DIR"' EXIT

set +e
(
  cd "$ROOT_DIR/services"
  CORE_ENABLED_OWNER_JOURNEY_TMP_DIR="$CORE_TMP_DIR" timeout 20m ./mvnw \
    -pl core -am \
    -Dtest='CoreEnabledOwnerJourneyIT' \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dcore.enabled.owner.journey=true \
    test -B
) 2>&1 | tee "$MAVEN_OUTPUT"
pipeline_status=("${PIPESTATUS[@]}")
set -e

maven_status="${pipeline_status[0]}"
tee_status="${pipeline_status[1]}"
if (( maven_status != 0 )); then
  if grep -Eq 'Could not find a valid Docker environment|ContainerLaunchException|Failed to pull image|docker daemon' \
      "$MAVEN_OUTPUT"; then
    blocked 'the disposable Testcontainers environment is unavailable'
  fi
  exit "$maven_status"
fi
(( tee_status == 0 )) || exit "$tee_status"
[[ -f "$CORE_REPORT" ]] \
  || { printf 'core-enabled-owner-journey: FAIL (CoreEnabledOwnerJourneyIT report is missing)\n' >&2; exit 1; }
grep -Eq 'Tests run: 1, Failures: 0, Errors: 0, Skipped: 0' "$CORE_REPORT" \
  || { printf 'core-enabled-owner-journey: FAIL (the named IT did not execute exactly once)\n' >&2; exit 1; }

printf 'core-enabled-owner-journey: PASS (Auth/Admin child wiring and cleanup)\n'
