#!/usr/bin/env bash
set -euo pipefail

# P2-REDIS-001: atomically materialize the runtime ACL and perform a
# dual-password rotation. Passwords are supplied by the secret store/environment;
# state and reports contain only phase and file hashes.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
ACTION="${1:-drift-check}"
case "$ACTION" in
  materialize|prepare|finalize|rollback|drift-check|check) ;;
  *)
    echo "Usage: $0 {materialize|prepare|finalize|rollback|drift-check}" >&2
    exit 2
    ;;
esac

# shellcheck source=scripts/dev/lib/common.sh
source "$ROOT_DIR/scripts/dev/lib/common.sh"
PASSWORD_VARS=(
  AUTH_REDIS_PASSWORD ADMIN_REDIS_PASSWORD APP_REDIS_PASSWORD
  SUBMISSION_REDIS_PASSWORD SEARCH_REDIS_PASSWORD NOTIFICATION_REDIS_PASSWORD
  JUDGE_REDIS_PASSWORD OPS_REDIS_PASSWORD HEALTH_REDIS_PASSWORD
  REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD
)
NEXT_PASSWORD_VARS=()
for password_var in "${PASSWORD_VARS[@]}"; do
  NEXT_PASSWORD_VARS+=("${password_var}_NEXT")
done
capture_env_vars REDIS_ACL_DIR REDIS_ACL_FILE REDIS_ACL_STATE_FILE \
  REDIS_ACL_LOCK_FILE REDIS_ACL_REPORT_FILE REDIS_ACL_EXPECTED_PHASE \
  REDIS_ACL_REDIS_CONTAINER REDIS_ACL_RELOAD_USER REDIS_ACL_RELOAD_PASSWORD \
  DOCKER_BIN "${PASSWORD_VARS[@]}" "${NEXT_PASSWORD_VARS[@]}"
load_env_file
apply_env_overrides

ACL_FILE="${REDIS_ACL_FILE:-}"
ACL_DIR="${REDIS_ACL_DIR:-}"
if [[ -z "$ACL_DIR" ]]; then
  if [[ -n "$ACL_FILE" ]]; then
    ACL_DIR="$(dirname -- "$ACL_FILE")"
  else
    ACL_DIR="$ROOT_DIR/.local/redis"
  fi
fi
[[ "$ACL_DIR" == /* ]] || ACL_DIR="$ROOT_DIR/$ACL_DIR"
if [[ -z "$ACL_FILE" ]]; then
  ACL_FILE="$ACL_DIR/users.acl"
else
  [[ "$ACL_FILE" == /* ]] || ACL_FILE="$ROOT_DIR/$ACL_FILE"
fi
[[ "$(dirname -- "$ACL_FILE")" == "$ACL_DIR" ]] \
  || { echo "REDIS_ACL_FILE must be inside REDIS_ACL_DIR" >&2; exit 1; }
STATE_FILE="${REDIS_ACL_STATE_FILE:-$ACL_DIR/rotation.state}"
LOCK_FILE="${REDIS_ACL_LOCK_FILE:-$ACL_DIR/rotation.lock}"
REPORT_FILE="${REDIS_ACL_REPORT_FILE:-$ACL_DIR/rotation.log}"
REDIS_CONTAINER="${REDIS_ACL_REDIS_CONTAINER:-}"
RELOAD_USER="${REDIS_ACL_RELOAD_USER:-ulticode-ops}"
RELOAD_PASSWORD="${REDIS_ACL_RELOAD_PASSWORD:-}"
DOCKER_BIN="${DOCKER_BIN:-docker}"

die() {
  echo "[redis-acl] FAIL: $*" >&2
  exit 1
}

valid_redis_user() {
  [[ "$1" =~ ^[A-Za-z0-9_-]+$ ]]
}

require_commands() {
  for command in flock cmp sha256sum date mktemp dirname; do
    command -v "$command" >/dev/null 2>&1 || die "required command not found: $command"
  done
  if [[ -n "$REDIS_CONTAINER" ]]; then
    command -v "$DOCKER_BIN" >/dev/null 2>&1 || die "docker is required for ACL reload"
    valid_container_ref "$REDIS_CONTAINER" || die "invalid REDIS_ACL_REDIS_CONTAINER"
    valid_redis_user "$RELOAD_USER" || die "invalid REDIS_ACL_RELOAD_USER"
    [[ -n "$RELOAD_PASSWORD" ]] || die "REDIS_ACL_RELOAD_PASSWORD is required for ACL reload"
  fi
}

require_primary() {
  local variable
  for variable in "${PASSWORD_VARS[@]}"; do
    [[ -n "${!variable:-}" ]] || die "$variable is required"
  done
}

require_next() {
  local variable
  for variable in "${NEXT_PASSWORD_VARS[@]}"; do
    [[ -n "${!variable:-}" ]] || die "$variable is required for rotation"
  done
}

acquire_lock() {
  mkdir -p "$ACL_DIR"
  chmod 755 "$ACL_DIR"
  local lock_fd
  exec {lock_fd}>"$LOCK_FILE" || die "cannot open lock: $LOCK_FILE"
  if ! flock -n "$lock_fd"; then
    echo "[redis-acl] SKIPPED: another rotation holds $LOCK_FILE" >&2
    exit 75
  fi
}

render_phase() {
  local phase="$1" output_file="$2" primary_var next_var previous_var current_value next_value
  for primary_var in "${PASSWORD_VARS[@]}"; do
    next_var="${primary_var}_NEXT"
    previous_var="${primary_var}_PREVIOUS"
    current_value="${!primary_var}"
    next_value="${!next_var:-}"
    case "$phase" in
      current)
        unset "$previous_var"
        ;;
      next)
        printf -v "$primary_var" '%s' "$next_value"
        unset "$previous_var"
        ;;
      next-overlap-current)
        printf -v "$primary_var" '%s' "$next_value"
        printf -v "$previous_var" '%s' "$current_value"
        export "$previous_var"
        ;;
      current-overlap-next)
        printf -v "$primary_var" '%s' "$current_value"
        printf -v "$previous_var" '%s' "$next_value"
        export "$previous_var"
        ;;
      *) die "unknown ACL render phase: $phase" ;;
    esac
  done
  REDIS_ACL_FILE="$output_file" "$ROOT_DIR/docker/redis/generate-users-acl.sh" "$output_file"
}

reload_redis_acl() {
  [[ -n "$REDIS_CONTAINER" ]] || return 0
  "$DOCKER_BIN" exec -e "REDISCLI_AUTH=$RELOAD_PASSWORD" \
    "$REDIS_CONTAINER" redis-cli --user "$RELOAD_USER" ACL LOAD >/dev/null \
    || die "Redis ACL LOAD failed"
}

write_state() {
  local phase="$1" digest now
  digest="$(sha256sum "$ACL_FILE" | awk '{print $1}')"
  now="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  mkdir -p "$(dirname -- "$STATE_FILE")" "$(dirname -- "$REPORT_FILE")"
  {
    printf '{\n'
    printf '  "phase": "%s",\n' "$phase"
    printf '  "acl_sha256": "%s",\n' "$digest"
    printf '  "updated_at": "%s"\n' "$now"
    printf '}\n'
  } > "$STATE_FILE"
  chmod 600 "$STATE_FILE"
  printf '%s phase=%s acl_sha256=%s file=%s\n' "$now" "$phase" "$digest" "$ACL_FILE" >> "$REPORT_FILE"
  chmod 600 "$REPORT_FILE"
}

apply_phase() {
  local phase="$1"
  acquire_lock
  render_phase "$phase" "$ACL_FILE"
  reload_redis_acl
  write_state "$phase"
  printf '[redis-acl] PASS phase=%s file=%s\n' "$phase" "$ACL_FILE"
}

drift_check() {
  local phase="${REDIS_ACL_EXPECTED_PHASE:-current}" expected_file
  case "$phase" in
    current)
      require_primary
      ;;
    next)
      require_next
      ;;
    next-overlap-current|current-overlap-next)
      require_primary
      require_next
      ;;
    *) die "invalid REDIS_ACL_EXPECTED_PHASE: $phase" ;;
  esac
  acquire_lock
  expected_file="$(mktemp "$ACL_DIR/.users.acl.check.XXXXXX")"
  trap 'rm -f "$expected_file"' RETURN
  render_phase "$phase" "$expected_file"
  cmp -s "$expected_file" "$ACL_FILE" \
    || die "runtime ACL drift detected for phase=$phase"
  printf '[redis-acl] PASS drift-check phase=%s file=%s\n' "$phase" "$ACL_FILE"
}

require_commands
case "$ACTION" in
  materialize)
    require_primary
    apply_phase current
    ;;
  prepare)
    require_primary
    require_next
    apply_phase next-overlap-current
    ;;
  finalize)
    require_next
    apply_phase next
    ;;
  rollback)
    require_primary
    require_next
    apply_phase current-overlap-next
    ;;
  drift-check|check)
    drift_check
    ;;
esac
