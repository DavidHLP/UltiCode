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
#   lib/docker.sh    container_running, await_container_health,
#                    mysql_container_targets_configured_host
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
  # shellcheck source=scripts/dev/lib/confirm.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/confirm.sh"
  # shellcheck source=scripts/dev/lib/sql.sh
  source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/sql.sh"
fi
