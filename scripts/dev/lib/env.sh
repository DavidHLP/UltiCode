#!/usr/bin/env bash
# scripts/dev/lib/env.sh — .env loading with pinning and explicit-env preservation.
#
# Internal module of scripts/dev/lib/common.sh; source common.sh, not this
# file. Everything here is frozen readonly -f BEFORE any .env is sourced so a
# hostile or careless .env cannot redefine, unset, or shadow it (attempts fail
# closed). load_env_file additionally re-pins ENV_FILE and ROOT_DIR after
# sourcing, so .env cannot redirect subsequent helper or script paths.

if ! [[ -v __ULTICODE_ENV_SOURCED ]]; then
  declare -gr __ULTICODE_ENV_SOURCED=1

  # Resolve repository root immutably from this file's location (scripts/dev/lib → repo root).
  # Do not allow .env to redirect helper and later $ROOT_DIR paths: always derive from BASH_SOURCE.
  ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
  export ROOT_DIR

  if [[ -z "${ENV_FILE:-}" ]]; then
    ENV_FILE="$ROOT_DIR/.env"
    export ENV_FILE
  fi

  load_env_file() {
    if [[ ! -f "$ENV_FILE" ]]; then
      echo "Missing $ENV_FILE. Run ./scripts/dev/init-env.sh first." >&2
      exit 1
    fi
    local pinned_env_file="$ENV_FILE"
    local pinned_root_dir="$ROOT_DIR"
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
    # .env must not be able to redirect ENV_FILE/ROOT_DIR or replace the
    # frozen helpers defined above (readonly -f makes that fail closed).
    ENV_FILE="$pinned_env_file"
    export ENV_FILE
    ROOT_DIR="$pinned_root_dir"
    export ROOT_DIR
  }

  capture_env_vars() {
    # capture_env_vars NAME... — remember which variables the caller set
    # explicitly (in the environment) together with their values, so they can
    # be restored after load_env_file. Must run BEFORE load_env_file.
    ULTICODE_CAPTURED_KEYS=("$@")
    ULTICODE_CAPTURED_WAS_SET=()
    ULTICODE_CAPTURED_OVERRIDE=()
    local name
    for name in "$@"; do
      ULTICODE_CAPTURED_WAS_SET+=("${!name+x}")
      ULTICODE_CAPTURED_OVERRIDE+=("${!name-}")
    done
    # Freeze the captured state: a hostile .env must not be able to clear or
    # rewrite it to defeat apply_env_overrides — tampering fails closed.
    readonly ULTICODE_CAPTURED_KEYS ULTICODE_CAPTURED_WAS_SET ULTICODE_CAPTURED_OVERRIDE
  }

  apply_env_overrides() {
    # apply_env_overrides — restore the values captured by capture_env_vars so
    # explicit caller-provided values win over values sourced from .env.
    local i name
    for i in "${!ULTICODE_CAPTURED_KEYS[@]}"; do
      name="${ULTICODE_CAPTURED_KEYS[$i]}"
      if [[ -n "${ULTICODE_CAPTURED_WAS_SET[$i]}" ]]; then
        printf -v "$name" '%s' "${ULTICODE_CAPTURED_OVERRIDE[$i]}"
      fi
    done
    return 0
  }

  # Freeze before any .env can be sourced (see header).
  readonly -f load_env_file capture_env_vars apply_env_overrides
fi
