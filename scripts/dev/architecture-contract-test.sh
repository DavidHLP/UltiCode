#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATIC_ONLY="${ULTI_STATIC_ONLY:-0}"
RULES_FILE="$ROOT_DIR/scripts/dev/architecture-contract-test.rules"

# This is the single architecture-contract entrypoint. Children own their
# assertions; the adjacent registry owns execution order and static/full
# eligibility, and set -e aggregates the first failing result.
CONTRACT_FAILURE_PREFIX="Architecture contract: FAIL"
CONTRACT_SUCCESS_MESSAGE="Architecture contract: PASS"
# shellcheck source=scripts/test/lib/contract-harness.sh
source "$ROOT_DIR/scripts/test/lib/contract-harness.sh"

run_child() {
  local entry="$1"
  local qualification="${entry%%:*}"
  local child="${entry#*:}"
  if [[ "$STATIC_ONLY" == "1" && "$qualification" != "static" ]]; then
    printf 'Architecture child %s: skipped in static-only mode\n' "$child"
    return 0
  fi
  if [[ "$STATIC_ONLY" == "1" ]]; then
    printf 'Architecture child %s: running static-safe checks\n' "$child"
  fi
  bash "$ROOT_DIR/$child"
}

[[ -f "$RULES_FILE" ]] || fail "missing contract registry: $RULES_FILE"
while IFS=$'\t' read -r qualification child extra; do
  [[ -z "$qualification$child$extra" || "$qualification" == \#* ]] && continue
  [[ -n "$qualification" && -n "$child" && -z "$extra" ]] \
    || fail "malformed contract registry row: ${qualification}${child}${extra}"
  [[ "$qualification" == static || "$qualification" == dynamic ]] \
    || fail "unknown contract qualification: $qualification"
  run_child "$qualification:$child"
done < "$RULES_FILE"

contract_pass
