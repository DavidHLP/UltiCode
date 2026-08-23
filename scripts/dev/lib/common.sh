#!/usr/bin/env bash
# scripts/dev/lib/common.sh — shared shell preamble helpers (non-semantic)
# Keep this file free of business logic (no REVOKE/drain/cutover, no migration-
# specific preflight wording); it only provides pure validators that many scripts
# duplicate: ROOT_DIR resolution, identifier/port/container and owner schema
# checks. Migration-specific fail_preflight stays in migrate.sh.
# Sourced idempotently: `source "$ROOT_DIR/scripts/dev/lib/common.sh"`.

# Always define validators — do not skip based on prior function existence, so a .env-injected helper cannot suppress the trusted definitions.
# Idempotence is via unconditional redefinition (overwrites any prior injected function).
# Private sentinel for external idempotence checks (set once, readonly thereafter)
if ! [[ -v __ULTICODE_COMMON_SOURCED ]]; then
  declare -g __ULTICODE_COMMON_SOURCED=1
  readonly __ULTICODE_COMMON_SOURCED
fi
# Resolve repository root immutably from this file's location (scripts/dev/lib → repo root).
# Do not allow .env to redirect helper and later $ROOT_DIR paths: always derive from BASH_SOURCE.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
export ROOT_DIR
if [[ -z "${ENV_FILE:-}" ]]; then
  ENV_FILE="$ROOT_DIR/.env"
  export ENV_FILE
fi
owner_schema() {
  case "$1" in
    auth|admin|app|notification|submission) return 0 ;;
    *) return 1 ;;
  esac
}

valid_identifier() {
  [[ "$1" =~ ^[A-Za-z0-9_]+$ ]]
}

valid_port() {
  [[ "$1" =~ ^[0-9]+$ ]] && ((1 <= 10#$1 && 10#$1 <= 65535))
}

valid_container_ref() {
  [[ "$1" =~ ^[A-Za-z0-9_.-]+$ ]]
}
