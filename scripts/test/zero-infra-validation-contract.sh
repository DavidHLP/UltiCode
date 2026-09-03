#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WRAPPER="$ROOT_DIR/scripts/dev/test.sh"

fail() {
  echo "zero-infra-validation-contract: FAIL: $*" >&2
  exit 1
}

[[ -x "$WRAPPER" ]] || fail "scripts/dev/test.sh is not executable"

TEST_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ulticode-zero-infra.XXXXXX")"
trap 'rm -rf -- "$TEST_DIR"' EXIT
DENY_BIN="$TEST_DIR/deny-bin"
DENY_LOG="$TEST_DIR/deny.log"
mkdir -p "$DENY_BIN"
: >"$DENY_LOG"

for forbidden in docker mysql redis-cli curl; do
  cat >"$DENY_BIN/$forbidden" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "${0##*/}" >> "${ULTI_DENY_LOG:?ULTI_DENY_LOG is required}"
if [[ "${ULTI_TEST_DENY:-0}" == "1" ]]; then
  echo "zero-infra deny shim rejected ${0##*/}" >&2
fi
exit 42
SHIM
  chmod +x "$DENY_BIN/$forbidden"
done

ORIGINAL_PATH="${PATH:-}"
BEFORE_DIFF="$TEST_DIR/before.diff"
AFTER_DIFF="$TEST_DIR/after.diff"
git -C "$ROOT_DIR" diff --binary -- . >"$BEFORE_DIFF"

run_static() {
  local marker="$1"
  : >"$DENY_LOG"
  PATH="$DENY_BIN:$ORIGINAL_PATH" \
    ULTI_DENY_LOG="$DENY_LOG" \
    ULTI_TEST_DENY="$marker" \
    bash "$WRAPPER" static >"$TEST_DIR/static-$marker.log" 2>&1 \
    || {
      cat "$TEST_DIR/static-$marker.log" >&2
      return 1
    }
  [[ ! -s "$DENY_LOG" ]] || {
    echo "forbidden command invoked: $(tr '\n' ' ' <"$DENY_LOG")" >&2
    return 1
  }
}

run_static 0 || fail "test.sh static used a forbidden command or failed"
git -C "$ROOT_DIR" diff --binary -- . >"$AFTER_DIFF"
cmp -s "$BEFORE_DIFF" "$AFTER_DIFF" \
  || fail "static validation modified tracked files"
printf 'test.sh static: PASS (deny-shim PATH, no Docker daemon)\n'
# Pure source/catalog children are static-safe and run inside static mode:
# api-contract-boundary, dubbo-provider-reference, and docs-contract now pass
# after the App locality migration and provider retirement. The remaining
# dynamic children (Docker/network/Maven integration shapes) must stay skipped.
for skipped_child in \
  scripts/test/redis-acl-contract.sh \
  scripts/test/audit-owner-boundary-contract.sh \
  scripts/test/owner-schema-contraction-contract.sh \
  scripts/test/admin-audit-stream-migration-contract.sh \
  scripts/test/stream-resilience-contract.sh \
  scripts/test/scale-topology-contract.sh \
  scripts/test/ha-profile-contract.sh \
  scripts/test/dubbo-mtls-contract.sh \
  scripts/test/network-reachability-contract.sh \
  scripts/test/judge-sandbox-contract.sh \
  scripts/test/owner-backup-restore-contract.sh \
  scripts/test/observability-contract.sh \
  scripts/test/scheduler-contract.sh \
  scripts/test/fenced-lease-contract.sh \
  scripts/test/graceful-drain-contract.sh \
  scripts/test/dependency-resilience-contract.sh \
  scripts/test/tls-profile-contract.sh \
  scripts/test/redis-acl-rotation-contract.sh; do
  grep -Fq "Architecture child $skipped_child: skipped in static-only mode" \
    "$TEST_DIR/static-0.log" \
    || fail "static mode unexpectedly ran deferred child: $skipped_child"
  grep -Fq "run_child $skipped_child" \
    "$ROOT_DIR/scripts/dev/architecture-contract-test.sh" \
    || fail "non-static architecture gate no longer runs deferred child: $skipped_child"
done

for static_child in \
  scripts/test/api-contract-boundary-contract.sh \
  scripts/test/dubbo-provider-reference-contract.sh \
  scripts/dev/docs-contract-test.sh; do
  grep -Fq "Architecture child $static_child: running static-safe checks" \
    "$TEST_DIR/static-0.log" \
    || fail "static mode did not run pure source child: $static_child"
done


# Unit mode (static + frontend checks + -Punit backend gate) must also run
# under the deny PATH: no docker/mysql/redis-cli/curl may be reached and the
# run must not modify tracked files. Frontend tooling must already exist.
run_unit_deny() {
  local marker="$1"
  : >"$DENY_LOG"
  PATH="$DENY_BIN:$ORIGINAL_PATH" \
    ULTI_DENY_LOG="$DENY_LOG" \
    ULTI_TEST_DENY="$marker" \
    bash "$WRAPPER" unit >"$TEST_DIR/unit-$marker.log" 2>&1 \
    || {
      cat "$TEST_DIR/unit-$marker.log" >&2
      return 1
    }
  [[ ! -s "$DENY_LOG" ]] || {
    echo "forbidden command invoked during unit: $(tr '\n' ' ' <"$DENY_LOG")" >&2
    return 1
  }
}

# Generated coverage HTML (from earlier full-local/coverage runs) is ignored
# but scanned by the design-system color contract; remove it so the unit gate
# runs against the same clean state CI would have.
rm -rf "$ROOT_DIR"/apps/*/coverage "$ROOT_DIR"/apps/*/src/coverage \
  "$ROOT_DIR"/packages/*/coverage "$ROOT_DIR"/packages/*/src/coverage

git -C "$ROOT_DIR" diff --binary -- . >"$BEFORE_DIFF"
run_unit_deny 0 || fail "test.sh unit used a forbidden command or failed"
git -C "$ROOT_DIR" diff --binary -- . >"$AFTER_DIFF"
cmp -s "$BEFORE_DIFF" "$AFTER_DIFF" \
  || fail "unit validation modified tracked files"
grep -Fq "Running backend unit tests (-Punit" "$TEST_DIR/unit-0.log" \
  || fail "unit mode did not run the -Punit backend gate"
grep -Fq -- "-Punit" "$TEST_DIR/unit-0.log" \
  || fail "unit backend command did not activate the unit profile"
if grep -Eq "Testcontainers|Ryuk|Running com\\.ulticode\\.[A-Za-z0-9_.]*(IT|IntegrationTest)\\b" \
  "$TEST_DIR/unit-0.log"; then
  fail "unit run executed an integration-flavoured suite"
fi
printf 'test.sh unit: PASS (deny-shim PATH, -Punit, no *IT, no tracked-file change)\n'

# The marker makes the same shims fail closed if a forbidden command is ever
# reached. Running static with it must still pass while the deny log stays empty.
run_static 1 || fail "ULTI_TEST_DENY=1 did not protect the static path"
set +e
PATH="$DENY_BIN:$ORIGINAL_PATH" \
  ULTI_DENY_LOG="$DENY_LOG" \
  ULTI_TEST_DENY=1 \
  docker info >"$TEST_DIR/negative.log" 2>&1
negative_status=$?
set -e
[[ "$negative_status" -eq 42 ]] \
  || fail "negative deny fixture did not reject a forbidden command"
printf 'negative deny fixture: PASS (ULTI_TEST_DENY=1 exits 42)\n'

describe_output="$(bash "$WRAPPER" --describe)"
for mode in static unit quick full-local full integration; do
  grep -Fq "${mode}|" <<<"$describe_output" \
    || fail "--describe is missing mode: $mode"
done
[[ "$(grep -Fc 'DEPRECATION: quick now means static + unit' "$WRAPPER")" -eq 1 ]] \
  || fail "quick deprecation notice is not emitted exactly once"
printf '%s\n' '--describe and quick mode-name contract: PASS'

dispatch_block="$(awk '
  /^case "\$MODE" in$/ { in_case = 1; candidate = ""; next }
  in_case {
    candidate = candidate $0 ORS
    if ($0 == "esac") {
      last_case = candidate
      in_case = 0
    }
  }
  END { printf "%s", last_case }
' "$WRAPPER")"
[[ -n "$dispatch_block" ]] || fail "could not inspect test.sh mode dispatch"
if grep -Eiq 'docker|compose|pnpm[[:space:]]+install|verify|init-env|generate-users-acl' \
  <<<"$dispatch_block"; then
  fail "static/unit/quick mode dispatch contains a forbidden command"
fi
printf 'static/unit/quick dispatch audit: PASS (forbidden heavy commands unreachable)\n'

set +e
invalid_output="$(bash "$WRAPPER" invalid 2>&1)"
invalid_status=$?
set -e
[[ "$invalid_status" -eq 2 ]] || fail "invalid mode exited $invalid_status, expected 2"
grep -Fq 'Usage:' <<<"$invalid_output" \
  || fail "invalid mode did not print usage text"
printf 'invalid mode contract: PASS (exit 2 + usage)\n'

set +e
missing_toolchain_output="$(env PATH=/usr/bin:/bin bash "$WRAPPER" static 2>&1)"
missing_toolchain_status=$?
set -e
[[ "$missing_toolchain_status" -eq 2 ]] \
  || fail "missing static toolchain exited $missing_toolchain_status, expected 2"
grep -Fq 'Usage:' <<<"$missing_toolchain_output" \
  || fail "missing static toolchain did not print usage text"
printf 'missing toolchain contract: PASS (exit 2 + usage)\n'

printf 'zero-infra-validation-contract: PASS\n'
