#!/usr/bin/env bash
set -euo pipefail

# Keep the synchronous Admin owner boundary explicit: pure one-hop references
# live in one registry, while adapters with enrichment or orchestration keep
# their own classes.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ADAPTER_DIR="services/admin/src/main/java/com/ulticode/modules/admin/port/adapter"
CONTRACT_FAILURE_PREFIX="admin-rpc-adapter-contract: FAIL"
CONTRACT_SUCCESS_MESSAGE="admin-rpc-adapter-contract: PASS"
# shellcheck source=scripts/test/lib/contract-harness.sh
source "$ROOT_DIR/scripts/test/lib/contract-harness.sh"
# shellcheck source=scripts/test/lib/assertions.sh
source "$ROOT_DIR/scripts/test/lib/assertions.sh"

mapfile -t adapters < <(
  find "$ROOT_DIR/$ADAPTER_DIR" -maxdepth 1 -type f -name 'Dubbo*.java' -printf '%f\n' | sort
)
expected_adapters=(
  DubboAdminForumReadAdapter.java
  DubboSubmissionActivityAnalyticsAdapter.java
)
[[ "${adapters[*]}" == "${expected_adapters[*]}" ]] \
  || fail "only behavior-carrying Dubbo adapters may remain: ${adapters[*]}"

for adapter in "${expected_adapters[@]}"; do
  source_file="$ADAPTER_DIR/$adapter"
  contains "$source_file" '@Primary'
  contains "$source_file" '@Component'
  contains "$source_file" '@DubboReference'
  contains "$source_file" 'RpcPolicy.'
  contains "$source_file" 'check = false'
done

registry="$ADAPTER_DIR/AdminDubboReferenceRegistry.java"
contains "$registry" '@Configuration(proxyBeanMethods = false)'
contains "$registry" '@DubboReference'
contains "$registry" '@Bean'
contains "$registry" '@Primary'
contains "$registry" 'RpcPolicy.'
contains "$registry" 'check = false'

require_count() {
  local file="$1" text="$2" expected="$3" actual
  actual="$(grep -cF -- "$text" "$ROOT_DIR/$file" || true)"
  [[ "$actual" == "$expected" ]] \
    || fail "$file must contain $expected occurrences of $text, found $actual"
}

require_count "$registry" '@DubboReference' 28
require_count "$registry" '@Bean' 28
require_count "$registry" '@Primary' 28

contract_pass
