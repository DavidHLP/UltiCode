#!/usr/bin/env bash
# Contract tests for scripts/dev/up.sh.
#
# `up.sh` is not sourceable without running a whole stack bring-up, so these
# assertions are file-contract checks: they pin the two properties that silently
# broke the local stack workflow.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
UP_SH="$ROOT_DIR/scripts/dev/up.sh"

failures=0

assert_not_contains() {
  local unexpected="$1" why="$2"
  if grep -F -- "$unexpected" "$UP_SH" >/dev/null; then
    echo "FAIL up.sh must not contain: $unexpected ($why)" >&2
    failures=$((failures + 1))
  else
    echo "ok   absent: $unexpected"
  fi
}

assert_contains() {
  local expected="$1" why="$2"
  if grep -F -- "$expected" "$UP_SH" >/dev/null; then
    echo "ok   present: $expected"
  else
    echo "FAIL up.sh must contain: $expected ($why)" >&2
    failures=$((failures + 1))
  fi
}

# 1. The startup banner must never echo a credential value. AGENTS.md forbids
#    printing credentials, and a banner is copied into terminal scrollback, CI
#    artifacts and transcripts.
assert_not_contains 'Password: $DEV_SEED_ADMIN_PASSWORD' \
  "credentials must not be printed to the startup banner"
assert_not_contains '$DEV_SEED_ADMIN_SECRET' \
  "no admin secret may be printed"
assert_contains 'values not printed' \
  "the banner should state that credentials are configured but withheld"

# 2. An explicit DEV_SEED_* override from the caller must survive loading .env,
#    otherwise `DEV_SEED_USERS_ENABLED=false up.sh` silently keeps the .env value.
assert_contains 'DEV_SEED_USERS_ENABLED DEV_SEED_DATA_ENABLED' \
  "seed switches must be captured so caller overrides survive load_env_file"

if ((failures > 0)); then
  echo "up-test.sh: $failures failure(s)" >&2
  exit 1
fi
echo "up-test.sh: all assertions passed"
