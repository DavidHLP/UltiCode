#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATIC_ONLY="${ULTI_STATIC_ONLY:-0}"

# This is the single architecture-contract entrypoint. Children own their
# assertions; this registry owns execution order and static/full eligibility,
# and set -e aggregates the first failing result.
ARCHITECTURE_CHILDREN=(
  static:scripts/test/owner-architecture-source-contract.sh
  static:scripts/dev/devstack-manifest-test.sh
  static:scripts/test/core-profile-contract.sh
  static:scripts/test/devlite-minimal-contract.sh
  static:scripts/test/devstack-control-contract.sh
  static:scripts/test/app-judge-runtime-dependency-contract.sh
  static:scripts/test/submission-compatibility-retirement-contract.sh
  static:scripts/test/deployment-integrity-contract.sh
  dynamic:scripts/test/redis-acl-contract.sh
  dynamic:scripts/test/redis-acl-rotation-contract.sh
  static:scripts/test/ssh-host-identity-contract.sh
  static:scripts/test/nacos-security-contract.sh
  static:scripts/test/submission-backfill-contract.sh
  dynamic:scripts/test/owner-schema-contraction-contract.sh
  dynamic:scripts/test/audit-owner-boundary-contract.sh
  dynamic:scripts/test/admin-audit-stream-migration-contract.sh
  dynamic:scripts/test/stream-resilience-contract.sh
  dynamic:scripts/test/scale-topology-contract.sh
  dynamic:scripts/test/ha-profile-contract.sh
  dynamic:scripts/test/dubbo-mtls-contract.sh
  dynamic:scripts/test/network-reachability-contract.sh
  dynamic:scripts/test/judge-sandbox-contract.sh
  dynamic:scripts/test/owner-backup-restore-contract.sh
  static:scripts/test/supply-chain-contract.sh
  dynamic:scripts/test/observability-contract.sh
  dynamic:scripts/test/scheduler-contract.sh
  dynamic:scripts/test/fenced-lease-contract.sh
  dynamic:scripts/test/graceful-drain-contract.sh
  dynamic:scripts/test/dependency-resilience-contract.sh
  static:scripts/test/tls-profile-contract.sh
  static:scripts/test/owner-migration-manifest-contract.sh
  static:scripts/test/api-contract-boundary-contract.sh
  static:scripts/test/dubbo-provider-reference-contract.sh
  static:scripts/dev/docs-contract-test.sh
)

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

for child in "${ARCHITECTURE_CHILDREN[@]}"; do
  run_child "$child"
done

echo "Architecture contract: PASS"
