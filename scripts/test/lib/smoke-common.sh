#!/usr/bin/env bash
# scripts/test/lib/smoke-common.sh — shared preamble and helpers for the
# scripts/test/* smoke suites.
#
# Source this from a smoke script, then call:
#   smoke_init <artifact-name>   # repo-root cwd + private artifact dir + cleanup trap
#   smoke_load_env               # trusted .env loading via scripts/dev/lib/common.sh
#   smoke_require_credentials [LEGACY_USER_VAR] [LEGACY_PASSWORD_VAR]
#                                # canonical SMOKE_USERNAME/SMOKE_PASSWORD,
#                                # falling back to one legacy variable pair
#   smoke_login <cookie_jar> <base_url> <username> <password> <out_body_file>
#                                # POST /auth/login; prints the HTTP status;
#                                # returns non-zero unless it is 200
#
# Credentials converge on SMOKE_USERNAME / SMOKE_PASSWORD; the legacy names
# remain accepted during the transition so existing invocations keep working.

SMOKE_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SMOKE_ROOT_DIR="$(cd "$SMOKE_LIB_DIR/../../.." && pwd)"

smoke_init() {
  local name="${1:?smoke_init requires an artifact name}"
  cd "$SMOKE_ROOT_DIR"
  SMOKE_ARTIFACT_ROOT="$SMOKE_ROOT_DIR/.tmp/$name"
  umask 077
  mkdir -p "$SMOKE_ARTIFACT_ROOT"
  SMOKE_ARTIFACT_DIR="$(mktemp -d "$SMOKE_ARTIFACT_ROOT/run.XXXXXX")"
  # Legacy alias kept so smoke bodies can keep using $ARTIFACT_DIR.
  ARTIFACT_DIR="$SMOKE_ARTIFACT_DIR"
  COOKIE_JAR="$SMOKE_ARTIFACT_DIR/cookies.txt"
  # Both variables are global so the EXIT trap can read them outside this
  # function's scope (a function-local would be unbound under set -u).
  trap 'rm -rf -- "$SMOKE_ARTIFACT_DIR"; rmdir "$SMOKE_ARTIFACT_ROOT" 2>/dev/null || true' EXIT
}

smoke_load_env() {
  # smoke_load_env [--optional] — trusted .env loading via lib/common.sh.
  # Explicitly exported values win over anything sourced from .env
  # (capture_env_vars/apply_env_overrides), matching the migrate.sh posture.
  # With --optional a missing repo .env is skipped instead of fatal, so
  # args/env-only smokes keep working on bare checkouts.
  local optional="${1:-}"
  # shellcheck source=scripts/dev/lib/common.sh
  source "$SMOKE_ROOT_DIR/scripts/dev/lib/common.sh"
  if [[ "$optional" == "--optional" && ! -f "${ENV_FILE:-$SMOKE_ROOT_DIR/.env}" ]]; then
    return 0
  fi
  capture_env_vars SMOKE_USERNAME SMOKE_PASSWORD SMOKE_BASE_URL BASE
  load_env_file
  apply_env_overrides
}

smoke_require_credentials() {
  local legacy_user_var="${1:-}" legacy_password_var="${2:-}"
  : "${SMOKE_USERNAME:=${legacy_user_var:+${!legacy_user_var:-}}}"
  : "${SMOKE_PASSWORD:=${legacy_password_var:+${!legacy_password_var:-}}}"
  if [[ -z "${SMOKE_USERNAME:-}" || -z "${SMOKE_PASSWORD:-}" ]]; then
    echo "Credentials required: set SMOKE_USERNAME and SMOKE_PASSWORD" >&2
    [[ -z "$legacy_user_var" ]] || echo "  (legacy $legacy_user_var / $legacy_password_var are still accepted)" >&2
    return 1
  fi
}

smoke_login() {
  local cookie_jar="$1" base="$2" username="$3" password="$4" out_body="$5"
  local payload status
  payload="$(SMOKE_USERNAME="$username" SMOKE_PASSWORD="$password" python3 \
    -c 'import json, os; print(json.dumps({"username": os.environ["SMOKE_USERNAME"], "password": os.environ["SMOKE_PASSWORD"]}))')"
  status="$(curl -sS -c "$cookie_jar" -o "$out_body" -w '%{http_code}' \
    -X POST "$base/auth/login" \
    -H 'Content-Type: application/json' \
    --data-binary "$payload")" || status="000"
  printf '%s\n' "$status"
  [[ "$status" == "200" ]]
}
