#!/usr/bin/env bash
# Literal source/config assertions. Callers own ROOT_DIR and source the shared
# contract harness (or provide an equivalent fail()). Sourcing this file has no
# runtime setup.

contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  grep -F -- "$text" "$ROOT_DIR/$file" >/dev/null \
    || fail "$file does not contain: $text (or could not be read)"
}

not_contains() {
  local file="$1" text="$2" status=0
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  # Only grep's no-match status proves absence; an I/O error must fail closed.
  grep -F -- "$text" "$ROOT_DIR/$file" >/dev/null || status=$?
  case "$status" in
    1) return 0 ;;
    0) fail "$file contains forbidden text: $text" ;;
    *) fail "could not inspect $file while checking for: $text (grep exit $status)" ;;
  esac
}
