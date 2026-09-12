#!/usr/bin/env bash
# scripts/dev/lib/common.sh — shared shell helpers for dev tooling.
#
# Deep helper library sourced by scripts under scripts/dev/, scripts/runbooks/
# and scripts/test/: trusted validators, .env loading with pinning, explicit-env
# preservation across the .env load, Docker container probes, write-action
# confirmation predicates, SQL data-verification primitives, and a single-
# sourced mysql_query adapter factory (scripts/dev/lib/sql.sh). Keep
# runbook-specific business logic (REVOKE/drain/cutover preflight wording) in
# the runbooks themselves; only generic, reused primitives belong here.
#
# The library is internally split by concern — each submodule owns its guard
# and freezes its own helpers readonly -f BEFORE any .env is sourced, so a
# hostile or careless .env cannot redefine, unset, or shadow trusted behaviour
# (attempts fail closed). This file stays the one external entry point:
#
#   lib/env.sh       ROOT_DIR/ENV_FILE resolution, load_env_file,
#                    capture_env_vars/apply_env_overrides
#   lib/validate.sh  owner_schema, valid_identifier/port/container_ref
#   lib/docker.sh    compose_service_container, running_compose_service_container,
#                    container_running, container_health_status, await_container_health,
#                    mysql_container_targets_configured_host
#   lib/compose.sh   devstack_compose_args
#   materialize_redis_acl  resolve, export, permission and materialize the
#                          runtime Redis ACL file
#   lib/confirm.sh   require_write_confirmation, gate_confirmed
#   lib/sql.sh       table_exists/column_signature/row_count/checksum_table,
#                    define_mysql_query_adapter
#
# Sourcing contract (unchanged for callers):
#   ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
#   ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
#   # shellcheck source=scripts/dev/lib/common.sh
#   source "$ROOT_DIR/scripts/dev/lib/common.sh"

if ! [[ -v __ULTICODE_COMMON_SOURCED ]]; then
  declare -gr __ULTICODE_COMMON_SOURCED=1

  # shellcheck source=scripts/dev/lib/env.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.sh"
  # shellcheck source=scripts/dev/lib/validate.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/validate.sh"
  # shellcheck source=scripts/dev/lib/docker.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/docker.sh"
  # shellcheck source=scripts/dev/lib/compose.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/compose.sh"
  # shellcheck source=scripts/dev/lib/confirm.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/confirm.sh"
  # shellcheck source=scripts/dev/lib/sql.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/sql.sh"

  materialize_redis_acl() {
    if (($# < 1 || $# > 2)); then
      echo "Usage: materialize_redis_acl <default-dir> [--warn-missing]" >&2
      return 2
    fi

    local default_dir="$1" warn_missing=false
    if (($# == 2)); then
      [[ "$2" == "--warn-missing" ]] || {
        echo "Unknown Redis ACL option: $2" >&2
        return 2
      }
      warn_missing=true
    fi
    [[ -n "$default_dir" ]] || {
      echo "materialize_redis_acl requires a default directory" >&2
      return 2
    }

    local acl_dir="${REDIS_ACL_DIR:-$default_dir}"
    local acl_file="${REDIS_ACL_FILE:-}"
    [[ "$acl_dir" == /* ]] || acl_dir="$ROOT_DIR/$acl_dir"
    if [[ -z "$acl_file" ]]; then
      acl_file="$acl_dir/users.acl"
    else
      [[ "$acl_file" == /* ]] || acl_file="$ROOT_DIR/$acl_file"
    fi

    REDIS_ACL_DIR="$acl_dir"
    REDIS_ACL_FILE="$acl_file"
    export REDIS_ACL_DIR REDIS_ACL_FILE
    REDIS_ACL_MATERIALIZED=false

    mkdir -p "$REDIS_ACL_DIR" || return $?
    chmod 755 "$REDIS_ACL_DIR" || return $?

    local generator="$ROOT_DIR/docker/redis/generate-users-acl.sh"
    if [[ ! -f "$generator" || ! -x "$generator" ]]; then
      if [[ "$warn_missing" == true ]]; then
        echo "WARNING: docker/redis/generate-users-acl.sh not found; runtime Redis ACL was not materialized." >&2
        return 0
      fi
      echo "Missing Redis ACL generator: docker/redis/generate-users-acl.sh" >&2
      return 1
    fi

    "$generator" "$REDIS_ACL_FILE" || return "$?"
    REDIS_ACL_MATERIALIZED=true
  }

  readonly -f materialize_redis_acl
fi
