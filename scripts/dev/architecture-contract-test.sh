#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATIC_ONLY="${ULTI_STATIC_ONLY:-0}"
RULES_FILE="$ROOT_DIR/scripts/dev/architecture-contract-test.rules"

usage() {
  printf 'Usage: %s [--list-qualified static|dynamic]\n' "${0##*/}" >&2
}

LIST_QUALIFICATION=""
case "$#" in
  0)
    ;;
  2)
    [[ "$1" == '--list-qualified' ]] || { usage; exit 2; }
    case "$2" in
      static|dynamic) LIST_QUALIFICATION="$2" ;;
      *) usage; exit 2 ;;
    esac
    ;;
  *)
    usage
    exit 2
    ;;
esac

# This is the single architecture-contract entrypoint. Children own their
# assertions; the adjacent registry owns execution order and static/full
# eligibility, and set -e aggregates the first failing result.
CONTRACT_FAILURE_PREFIX="Architecture contract: FAIL"
CONTRACT_SUCCESS_MESSAGE="Architecture contract: PASS"
# shellcheck source=scripts/test/lib/contract-harness.sh
source "$ROOT_DIR/scripts/test/lib/contract-harness.sh"

REGISTRY_ROWS=0
run_child() {
  local qualification="$1" child="$2"
  if [[ "$STATIC_ONLY" == "1" && "$qualification" != "static" ]]; then
    printf 'Architecture child %s: skipped in static-only mode\n' "$child"
    return 0
  fi
  # Compatibility retirement is a release-only proof. Keep its row in the
  # registry, but let the contract workflow select it through its existing
  # breaking-release output instead of duplicating the child path in CI.
  if [[ "$child" == "scripts/test/submission-compatibility-retirement-contract.sh" \
    && "${ARCHITECTURE_BREAKING_RELEASE:-true}" != "true" ]]; then
    printf 'Architecture child %s: skipped outside breaking-release mode\n' "$child"
    return 0
  fi
  if [[ "$STATIC_ONLY" == "1" ]]; then
    printf 'Architecture child %s: running static-safe checks\n' "$child"
  fi
  bash "$ROOT_DIR/$child"
}

[[ -f "$RULES_FILE" ]] || fail "missing contract registry: $RULES_FILE"
read_registry() {
  local qualification child extra
  while IFS=$'\t' read -r qualification child extra; do
    [[ -z "$qualification$child$extra" || "$qualification" == \#* ]] && continue
    [[ -n "$qualification" && -n "$child" && -z "$extra" ]] \
      || fail "malformed contract registry row: ${qualification}${child}${extra}"
    [[ "$qualification" == static || "$qualification" == dynamic ]] \
      || fail "unknown contract qualification: $qualification"
    [[ -f "$ROOT_DIR/$child" ]] \
      || fail "registered contract child is missing: $child"
    REGISTRY_ROWS=$((REGISTRY_ROWS + 1))
    if [[ -n "$LIST_QUALIFICATION" ]]; then
      [[ "$qualification" == "$LIST_QUALIFICATION" ]] || continue
      printf '%s\n' "$child"
    else
      run_child "$qualification" "$child"
    fi
  done < "$RULES_FILE"
}

read_registry
(( REGISTRY_ROWS > 0 )) || fail "contract registry is empty: $RULES_FILE"
[[ -z "$LIST_QUALIFICATION" ]] || exit 0

contract_pass
