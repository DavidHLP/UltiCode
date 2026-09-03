#!/usr/bin/env bash
set -euo pipefail

# P2-DEVLITE-003/005 + P7-GATE-001: prove the scope graph selects explicit
# Compose targets without starting Docker. The fake CLI records argv only.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/dev/devstack-manifest.sh
source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
FAKE_BIN="$TMP_DIR/bin"
CAPTURE="$TMP_DIR/docker.args"
mkdir -p "$FAKE_BIN"

cat > "$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$DEVSTACK_DOCKER_CAPTURE"
exit 0
EOF
chmod +x "$FAKE_BIN/docker"
export DEVSTACK_DOCKER_CAPTURE="$CAPTURE"

fail() {
  echo "devlite minimal contract failed: $*" >&2
  exit 1
}

capture_compose_up() {
  local scope="$1" observability="${2:-false}" targets selected
  selected="$(devstack_scope_apps "$scope")"
  targets="$(devstack_infra_for_selection "$scope" "$selected" "$observability")"
  [[ -n "$targets" ]] || fail "scope $scope resolved no infra targets"
  local -a compose_targets=(
    docker compose
    -f "$ROOT_DIR/docker-compose.yml"
    -f "$ROOT_DIR/docker-compose.dev.yml"
  )
  [[ ",$selected," == *,ulticode-judge,* ]] \
    && compose_targets+=(-f "$ROOT_DIR/docker-compose.judge-dev.yml" --profile judge-socket)
  [[ "$observability" == true ]] \
    && compose_targets+=(-f "$ROOT_DIR/docker-compose.observability.yml" --profile observability)
  local -a infra_array=()
  local IFS=,
  read -ra infra_array <<< "$targets"
  PATH="$FAKE_BIN:$PATH" "${compose_targets[@]}" up -d "${infra_array[@]}"
}

assert_capture_contains() {
  local expected="$1"
  grep -F -- "$expected" "$CAPTURE" >/dev/null \
    || fail "fake Compose argv missing: $expected"
}

assert_capture_not_contains() {
  local unexpected="$1"
  ! grep -F -- "$unexpected" "$CAPTURE" >/dev/null \
    || fail "fake Compose argv unexpectedly contains: $unexpected"
}


: > "$CAPTURE"
capture_compose_up submission-judge
assert_capture_contains 'judge-dev.yml --profile judge-socket up -d mysql redis nacos'
assert_capture_not_contains 'meilisearch'
assert_capture_not_contains 'observability.yml'

: > "$CAPTURE"
capture_compose_up admin
assert_capture_not_contains 'judge-dev.yml'
assert_capture_not_contains 'profile judge-socket'
: > "$CAPTURE"
capture_compose_up dev-lite
assert_capture_contains 'up -d mysql redis nacos'
assert_capture_not_contains meilisearch
assert_capture_not_contains otel-collector

: > "$CAPTURE"
capture_compose_up search
assert_capture_contains 'up -d mysql redis nacos meilisearch'

: > "$CAPTURE"
capture_compose_up dev-lite true
assert_capture_contains 'up -d mysql redis nacos otel-collector prometheus alertmanager tempo loki grafana'

# Search, Judge, and observability are opt-in capabilities, not accidental
# dependencies of the compatibility default.
if devstack_scope_feature_enabled dev-lite search; then fail 'dev-lite Search feature must be enabled'; fi
if devstack_scope_feature_enabled admin judge; then fail 'admin Judge feature must be enabled'; fi
if ! devstack_scope_feature_enabled submission-judge judge; then fail 'submission-judge Judge feature must be enabled'; fi
if ! devstack_scope_feature_enabled full-stack search; then fail 'full-stack Search feature must be enabled'; fi

# Illegal and unknown combinations fail closed before Compose can be called.
if devstack_validate_scope_selection dev-lite ulticode-search >/dev/null 2>&1; then
  fail 'dev-lite + Search worker was accepted'
fi
if devstack_validate_scope_selection admin ulticode-judge >/dev/null 2>&1; then
  fail 'admin + Judge worker was accepted'
fi
! grep -F -- './scripts/dev/up.sh --mode legacy-rollback' "$ROOT_DIR/docs/development/local-setup.md" >/dev/null \
  || fail 'local setup advertises retired legacy command'
if devstack_infra_for_selection dev-lite ulticode-auth,ulticode-search >/dev/null 2>&1; then
  fail 'Search-disabled selection resolved infra'
fi

# Matrix and executable entry points stay aligned without adding a second docs
# table. local-setup.md already documents the compatibility mode contract.
for scope in "${DEVSTACK_SCOPES[@]}"; do
  devstack_resolve_scope "$scope" >/dev/null \
    || fail "resolver rejected documented scope: $scope"
done
grep -F -- './scripts/dev/up.sh --mode dev-lite' "$ROOT_DIR/docs/development/local-setup.md" >/dev/null \
  || fail 'local setup lost dev-lite compatibility command'
grep -F -- './scripts/dev/up.sh --mode dev-full' "$ROOT_DIR/docs/development/local-setup.md" >/dev/null \
  || fail 'local setup lost dev-full compatibility command'
! grep -F -- './scripts/dev/up.sh --mode legacy-rollback' "$ROOT_DIR/docs/development/local-setup.md" >/dev/null \
  || fail 'local setup advertises retired legacy command'
grep -F -- '"${COMPOSE_TARGETS[@]}"' "$ROOT_DIR/scripts/dev/up.sh" >/dev/null \
  || fail 'up.sh does not pass explicit Compose target array'
grep -F -- 'source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"' "$ROOT_DIR/scripts/dev/up.sh" >/dev/null \
  || fail 'up.sh bypasses the manifest'

printf 'DevStack minimal contract: PASS\n'
