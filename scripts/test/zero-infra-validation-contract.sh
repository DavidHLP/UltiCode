#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WRAPPER="$ROOT_DIR/scripts/dev/test.sh"
ARCHITECTURE_GATE="$ROOT_DIR/scripts/dev/architecture-contract-test.sh"

case "${1:-}" in
  ''|--static-only) ;;
  *) echo 'Usage: zero-infra-validation-contract.sh [--static-only]' >&2; exit 2 ;;
esac
[[ $# -le 1 ]] || { echo 'Expected at most one argument' >&2; exit 2; }

CONTRACT_FAILURE_PREFIX="zero-infra-validation-contract: FAIL"
# shellcheck source=scripts/test/lib/contract-harness.sh
source "$ROOT_DIR/scripts/test/lib/contract-harness.sh"

[[ -x "$WRAPPER" ]] || fail "scripts/dev/test.sh is not executable"

for workflow in _backend.yml _frontend.yml; do
  grep -Fqx '        run: bash scripts/test/zero-infra-validation-contract.sh --static-only' \
    "$ROOT_DIR/.github/workflows/$workflow" \
    || fail "$workflow must select --static-only for its lightweight CI job"
done
grep -Fq 'bash scripts/dev/architecture-contract-test.sh --list-qualified dynamic' \
  "$ROOT_DIR/.github/workflows/_backend.yml" \
  || fail '_backend.yml must enumerate dynamic architecture contracts from the registry'

TEST_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ulticode-zero-infra.XXXXXX")"
trap 'rm -rf -- "$TEST_DIR"' EXIT
DENY_BIN="$TEST_DIR/deny-bin"
STATIC_DENY_BIN="$TEST_DIR/static-deny-bin"
DENY_LOG="$TEST_DIR/deny.log"
mkdir -p "$DENY_BIN" "$STATIC_DENY_BIN"
: >"$DENY_LOG"

write_deny_shim() {
  local directory="$1" command="$2"
  cat >"$directory/$command" <<'SHIM'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "${0##*/}" >> "${ULTI_DENY_LOG:?ULTI_DENY_LOG is required}"
if [[ "${ULTI_TEST_DENY:-0}" == "1" ]]; then
  echo "zero-infra deny shim rejected ${0##*/}" >&2
fi
exit 42
SHIM
  chmod +x "$directory/$command"
}

for forbidden in docker mysql redis-cli curl; do
  write_deny_shim "$DENY_BIN" "$forbidden"
  write_deny_shim "$STATIC_DENY_BIN" "$forbidden"
done
for forbidden in mvn mvnw pnpm; do
  write_deny_shim "$STATIC_DENY_BIN" "$forbidden"
done

ORIGINAL_PATH="${PATH:-}"
BEFORE_DIFF="$TEST_DIR/before.diff"
AFTER_DIFF="$TEST_DIR/after.diff"
git -C "$ROOT_DIR" diff --binary -- . >"$BEFORE_DIFF"

run_static() {
  local marker="$1"
  : >"$DENY_LOG"
  PATH="$STATIC_DENY_BIN:$ORIGINAL_PATH" \
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

collect_qualified() {
  local qualification="$1" output error_file
  error_file="$TEST_DIR/list-$qualification.error"
  if output="$(bash "$ARCHITECTURE_GATE" --list-qualified "$qualification" 2>"$error_file")"; then
    :
  else
    cat "$error_file" >&2
    fail "architecture registry list failed for qualification: $qualification"
  fi
  [[ "$output" != *'Architecture child '* ]] \
    || fail "qualified list emitted execution banners: $qualification"
  [[ "$output" != *'Architecture contract: '* ]] \
    || fail "qualified list emitted a contract result: $qualification"
  printf '%s\n' "$output"
}

assert_invalid_qualified_list() {
  local kind="$1" output status
  if [[ "$kind" == missing ]]; then
    if output="$(bash "$ARCHITECTURE_GATE" --list-qualified 2>&1)"; then
      status=0
    else
      status=$?
    fi
  else
    if output="$(bash "$ARCHITECTURE_GATE" --list-qualified invalid 2>&1)"; then
      status=0
    else
      status=$?
    fi
  fi
  [[ "$status" -eq 2 ]] || fail "qualified list $kind filter exited $status, expected 2"
  grep -Fq 'Usage:' <<<"$output" \
    || fail "qualified list $kind filter did not print usage text"
}

static_children="$(collect_qualified static)"
dynamic_children="$(collect_qualified dynamic)"
[[ -n "$static_children" ]] || fail 'architecture registry has no static children'
[[ -n "$dynamic_children" ]] || fail 'architecture registry has no dynamic children'
assert_invalid_qualified_list missing
assert_invalid_qualified_list invalid

declare -A registry_qualification=()
declare -A seen_banner=()
register_qualified_children() {
  local qualification="$1" children="$2" child
  while IFS= read -r child; do
    [[ -n "$child" ]] || continue
    [[ -z "${registry_qualification[$child]+present}" ]] \
      || fail "architecture registry listed child more than once: $child"
    registry_qualification["$child"]="$qualification"
  done <<<"$children"
}
register_qualified_children static "$static_children"
register_qualified_children dynamic "$dynamic_children"

assert_workflow_does_not_hand_list() {
  local workflow="$1" child
  while IFS= read -r child; do
    [[ -n "$child" ]] || continue
    ! grep -Fq "$child" "$ROOT_DIR/.github/workflows/$workflow" \
      || fail "$workflow hand-lists registered architecture child: $child"
  done < <(printf '%s\n%s\n' "$static_children" "$dynamic_children")
}
assert_workflow_does_not_hand_list _backend.yml
assert_workflow_does_not_hand_list _frontend.yml

assert_architecture_banners() {
  local line child expected kind
  while IFS= read -r line; do
    if [[ "$line" =~ ^Architecture\ child\ (.+):\ running\ static-safe\ checks$ ]]; then
      child="${BASH_REMATCH[1]}"
      kind=running
    elif [[ "$line" =~ ^Architecture\ child\ (.+):\ skipped\ in\ static-only\ mode$ ]]; then
      child="${BASH_REMATCH[1]}"
      kind=skipped
    else
      continue
    fi
    expected="${registry_qualification[$child]-}"
    [[ -n "$expected" ]] \
      || fail "static log emitted a banner for an unregistered child: $child"
    if [[ "$kind" == running ]]; then
      [[ "$expected" == static ]] \
        || fail "dynamic child was marked static-running: $child"
    else
      [[ "$expected" == dynamic ]] \
        || fail "static child was marked skipped: $child"
    fi
    [[ -z "${seen_banner[$child]+present}" ]] \
      || fail "static log emitted duplicate architecture banners: $child"
    seen_banner["$child"]="$kind"
  done < "$TEST_DIR/static-0.log"

  for child in "${!registry_qualification[@]}"; do
    expected="${registry_qualification[$child]}"
    if [[ "$expected" == static ]]; then
      kind=running
    else
      kind=skipped
    fi
    [[ "${seen_banner[$child]-}" == "$kind" ]] \
      || fail "static log banner mismatch for $expected child: $child"
  done
}
assert_architecture_banners


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
if [[ "${1:-}" != --static-only ]]; then
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
else
  printf 'test.sh unit: skipped (--static-only; run without arguments for unit deny proof)\n'
fi

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
printf '%s\n' '--describe and quick mode-name contract: PASS'

if [[ "${1:-}" == --static-only ]]; then
  printf 'static behavior contract: PASS (CLI execution stayed inside the deny environment)\n'
else
  printf 'static/unit behavior contract: PASS (CLI execution stayed inside the deny environment)\n'
fi

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
