#!/usr/bin/env bash
# scripts/dev/lib/common.sh — shared shell helpers for owner migration tooling
#
# Deep helper library sourced by scripts under scripts/dev/, scripts/runbooks/
# and scripts/test/: trusted validators, .env loading with pinning,
# explicit-env preservation across the .env load, Docker container probes, and
# write-action confirmation predicates. Keep runbook-specific business logic
# (REVOKE/drain/cutover preflight wording) in the runbooks themselves; only
# generic, reused primitives belong here (see PROJECT_DOCUMENTATION.md
# slice-4 note and AGENTS.md).
#
# Sourcing contract:
#   ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
#   ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
#   # shellcheck source=scripts/dev/lib/common.sh
#   source "$ROOT_DIR/scripts/dev/lib/common.sh"
#
# Injection posture: every helper below is defined once per process and then
# frozen with `readonly -f`, BEFORE any .env is sourced. A hostile or careless
# .env therefore cannot redefine, unset, or shadow them — such attempts fail
# closed with a bash "readonly function" error instead of silently replacing
# trusted behaviour. load_env_file additionally re-pins ENV_FILE and ROOT_DIR
# after sourcing, so .env cannot redirect subsequent helper or script paths.

if ! [[ -v __ULTICODE_COMMON_SOURCED ]]; then
  declare -gr __ULTICODE_COMMON_SOURCED=1

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

  mysql_container_targets_configured_host() {
    local container="$1" container_port="$2" host="$3" port="$4"
    local endpoint published_host published_port
    while IFS= read -r endpoint; do
      published_port="${endpoint##*:}"
      [[ "$published_port" == "$port" ]] || continue
      published_host="${endpoint%:*}"
      published_host="${published_host#[}"
      published_host="${published_host%]}"
      case "$host" in
        localhost)
          [[ "$published_host" == "127.0.0.1" || "$published_host" == "0.0.0.0" \
            || "$published_host" == "::1" || "$published_host" == "::" ]] && return 0
          ;;
        127.0.0.1)
          [[ "$published_host" == "127.0.0.1" || "$published_host" == "0.0.0.0" ]] && return 0
          ;;
        ::1)
          [[ "$published_host" == "::1" || "$published_host" == "::" ]] && return 0
          ;;
      esac
    done < <(docker port "$container" "$container_port/tcp" 2>/dev/null)
    return 1
  }

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

  container_running() {
    [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null || true)" == "true" ]]
  }

  await_container_health() {
    local container="$1"
    local attempts="${2:-60}"
    local interval_seconds="${3:-2}"
    local i status
    for ((i = 1; i <= attempts; i++)); do
      status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
      if [[ "$status" == "healthy" || "$status" == "running" ]]; then
        return 0
      fi
      sleep "$interval_seconds"
    done
    echo "Container did not become healthy: $container" >&2
    docker logs --tail 100 "$container" >&2 || true
    return 1
  }

  require_write_confirmation() {
    # require_write_confirmation <EXECUTE_VALUE> <CONFIRM_VAR_NAME> <EXPECTED_TOKEN>
    # True when a write action carries both --execute and its confirmation token.
    [[ "$1" == "--execute" && "${!2:-}" == "$3" ]]
  }

  gate_confirmed() {
    # gate_confirmed <CONFIRM_VAR_NAME> <EXPECTED_TOKEN> — true when the named
    # confirmation variable holds exactly the expected token. Use this for
    # gates without an --execute flag; pair bespoke refusal messages with it.
    [[ "${!1:-}" == "$2" ]]
  }

  # Shared data-verification primitives for migration runbooks. They delegate
  # the connection to the caller-owned `mysql_query <sql>` adapter, so each
  # runbook keeps its own connection semantics while the verification logic
  # stays single-source here.
  table_exists() {
    local schema="$1" table="$2"
    [[ "$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$schema' AND table_name = '$table';")" == "1" ]]
  }

  column_signature() {
    local schema="$1" table="$2"
    mysql_query "SELECT COALESCE(GROUP_CONCAT(CONCAT_WS(':', ordinal_position, column_name, column_type, is_nullable, COALESCE(column_default, '<NULL>'), extra, COALESCE(character_set_name, ''), COALESCE(collation_name, '')) ORDER BY ordinal_position SEPARATOR '|'), '') FROM information_schema.columns WHERE table_schema = '$schema' AND table_name = '$table';"
  }

  row_count() {
    local schema="$1" table="$2" predicate="${3:-1=1}"
    mysql_query "SELECT COUNT(*) FROM \`$schema\`.\`$table\` WHERE $predicate;"
  }

  checksum_table() {
    # Strict CHECKSUM TABLE reader: refuses to return a non-numeric value so a
    # broken transport cannot silently compare empty checksums.
    local schema="$1" table="$2" result
    if ! result="$(mysql_query "CHECKSUM TABLE \`$schema\`.\`$table\`;")"; then
      return 1
    fi
    result="$(awk 'NF == 2 && $2 ~ /^[0-9]+$/ { print $2; found=1 } END { if (!found) exit 1 }' <<<"$result")" || {
      echo "Unable to read a valid checksum for $schema.$table; refusing to continue." >&2
      return 1
    }
    printf '%s\n' "$result"
  }

  # Freeze the trusted helpers before any .env can be sourced (see header).
  readonly -f owner_schema valid_identifier valid_port valid_container_ref \
    mysql_container_targets_configured_host load_env_file capture_env_vars \
    apply_env_overrides container_running await_container_health \
    require_write_confirmation gate_confirmed \
    table_exists column_signature row_count checksum_table
fi
