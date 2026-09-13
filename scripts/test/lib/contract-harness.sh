#!/usr/bin/env bash
# Shared failure/reporting vocabulary for source and zero-infrastructure
# contracts. Infrastructure smoke suites keep their own cleanup because the
# resources they own have different lifecycles.

CONTRACT_FAILURE_PREFIX="${CONTRACT_FAILURE_PREFIX:-contract: FAIL}"

fail() {
  printf '%s: %s\n' "$CONTRACT_FAILURE_PREFIX" "$*" >&2
  exit 1
}

contract_pass() {
  printf '%s\n' "${CONTRACT_SUCCESS_MESSAGE:-contract: PASS}"
}
