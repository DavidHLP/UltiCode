#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
for required_command in bash mktemp cat grep rm; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    printf 'FINAL_GATE: FAIL gate=configuration (required command missing: %s)\n' "$required_command" >&2
    exit 2
  fi
done
SKIP_EXPENSIVE="${FINAL_GATE_SKIP_EXPENSIVE:-0}"
FAILED_GATE=""
FAILED_RESULT=""
OUTPUT_DIR=""

case "$SKIP_EXPENSIVE" in
  0|1)
    ;;
  *)
    printf 'FINAL_GATE: FAIL gate=configuration (FINAL_GATE_SKIP_EXPENSIVE must be 0 or 1)\n' >&2
    exit 2
    ;;
esac

OUTPUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ulticode-final-integration.XXXXXX")"
cleanup() {
  if [[ -n "$OUTPUT_DIR" && -d "$OUTPUT_DIR" ]]; then
    rm -rf -- "$OUTPUT_DIR"
  fi
}
trap cleanup EXIT

run_gate() {
  local phase="$1"
  local gate="$2"
  local expensive="$3"
  shift 3

  local output_file="$OUTPUT_DIR/${phase}_${gate}.out"
  local child_status=0
  local result

  printf '\n[%s] gate=%s begin\n' "$phase" "$gate"

  if [[ "$expensive" == 1 && "$SKIP_EXPENSIVE" == 1 ]]; then
    printf '[%s] gate=%s child output (not run):\n' "$phase" "$gate"
    printf 'BLOCKED_EXTERNAL: operator requested FINAL_GATE_SKIP_EXPENSIVE=1\n'
    printf '[%s] gate=%s result=BLOCKED_EXTERNAL\n' "$phase" "$gate"
    FAILED_GATE="$phase/$gate"
    FAILED_RESULT="BLOCKED_EXTERNAL"
    return 1
  fi

  if "$@" >"$output_file" 2>&1; then
    child_status=0
  else
    child_status=$?
  fi

  # Emit the complete combined child stream before classifying its result.
  cat "$output_file"

  if grep -Fq -- 'BLOCKED_EXTERNAL' "$output_file"; then
    result="BLOCKED_EXTERNAL"
  elif (( child_status == 0 )); then
    result="PASS"
  else
    result="FAIL"
  fi

  printf '[%s] gate=%s result=%s\n' "$phase" "$gate" "$result"
  if [[ "$result" != PASS ]]; then
    FAILED_GATE="$phase/$gate"
    FAILED_RESULT="$result"
    return 1
  fi
}

run_or_stop() {
  if ! run_gate "$@"; then
    printf '\nFINAL_GATE: %s gate=%s\n' "$FAILED_RESULT" "$FAILED_GATE"
    exit 1
  fi
}

cd "$ROOT_DIR"

printf '=== P0 BASELINE ===\n'
run_or_stop P0 manifest-contract 0 bash "$ROOT_DIR/scripts/dev/devstack-manifest-test.sh"
run_or_stop P0 docs-contract 0 bash "$ROOT_DIR/scripts/dev/docs-contract-test.sh"
run_or_stop P0 api-contract 0 bash "$ROOT_DIR/scripts/test/api-contract-boundary-contract.sh"

printf '\n=== P1 APP RUNTIME BOUNDARY ===\n'
run_or_stop P1 app-judge-runtime 0 bash "$ROOT_DIR/scripts/test/app-judge-runtime-dependency-contract.sh"

printf '\n=== P2 INFRASTRUCTURE RECOVERY ===\n'
run_or_stop P2 infra-isolation 1 bash "$ROOT_DIR/scripts/test/gate-infra-isolation.sh"

printf '\n=== P3 ADMIN RPC BUDGET ===\n'
run_or_stop P3 admin-rpc-budget 1 bash "$ROOT_DIR/scripts/test/gate-admin-rpc-budget.sh"

printf '\n=== P4 LEGACY SCHEMA CONTRACTION (P4-011) ===\n'
run_or_stop P4 schema-contraction 1 bash "$ROOT_DIR/scripts/test/owner-schema-contraction-contract.sh"

printf '\n=== P5 AFFECTED MAVEN FULL SUITE ===\n'
run_or_stop P5 repository-full 1 bash -c \
  "cd \"$ROOT_DIR/services\" && mise exec java@zulu-17.68.203.0 -- bash ./mvnw -pl auth,admin,app/app-web,submission,judge,notification,search -am test -B"

printf '\n=== P6 DISPOSABLE INTEGRATION SUITE ===\n'
run_or_stop P6 schema-integration 1 bash "$ROOT_DIR/scripts/test/owner-schema-contraction-contract.sh"
run_or_stop P6 audit-stream-integration 1 bash "$ROOT_DIR/scripts/test/admin-audit-stream-migration-contract.sh"

printf '\nFINAL_GATE: PASS\n'
