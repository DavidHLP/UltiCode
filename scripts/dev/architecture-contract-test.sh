#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATIC_ONLY="${ULTI_STATIC_ONLY:-0}"

# This is the single architecture-contract entrypoint. Children own their
# assertions; this parent only selects static/full execution, preserves order,
# and aggregates the first failing result through set -e.
run_child() {
  local child="$1"
  if [[ "$STATIC_ONLY" == "1" ]]; then
    case "$child" in
      scripts/dev/devstack-manifest-test.sh \
        |scripts/test/app-judge-runtime-dependency-contract.sh \
        |scripts/test/submission-compatibility-retirement-contract.sh \
        |scripts/test/ssh-host-identity-contract.sh \
        |scripts/test/nacos-security-contract.sh \
        |scripts/test/submission-backfill-contract.sh \
        |scripts/test/supply-chain-contract.sh \
        |scripts/test/deployment-integrity-contract.sh \
        |scripts/test/tls-profile-contract.sh \
        |scripts/test/owner-migration-manifest-contract.sh \
        |scripts/test/owner-architecture-source-contract.sh \
        |scripts/test/api-contract-boundary-contract.sh \
        |scripts/test/dubbo-provider-reference-contract.sh \
        |scripts/dev/docs-contract-test.sh \
        |scripts/test/devlite-minimal-contract.sh \
        |scripts/test/core-profile-contract.sh \
        |scripts/test/devstack-control-contract.sh \
      )
        printf 'Architecture child %s: running static-safe checks\n' "$child"
        ;;
      *)
        printf 'Architecture child %s: skipped in static-only mode\n' "$child"
        return 0
        ;;
    esac
  fi
  bash "$ROOT_DIR/$child"
}

for child in \
  scripts/test/owner-architecture-source-contract.sh \
  scripts/dev/devstack-manifest-test.sh \
  scripts/test/core-profile-contract.sh \
  scripts/test/devlite-minimal-contract.sh \
  scripts/test/devstack-control-contract.sh \
  scripts/test/app-judge-runtime-dependency-contract.sh \
  scripts/test/submission-compatibility-retirement-contract.sh \
  scripts/test/deployment-integrity-contract.sh \
  scripts/test/redis-acl-contract.sh \
  scripts/test/redis-acl-rotation-contract.sh \
  scripts/test/ssh-host-identity-contract.sh \
  scripts/test/nacos-security-contract.sh \
  scripts/test/submission-backfill-contract.sh \
  scripts/test/owner-schema-contraction-contract.sh \
  scripts/test/audit-owner-boundary-contract.sh \
  scripts/test/admin-audit-stream-migration-contract.sh \
  scripts/test/stream-resilience-contract.sh \
  scripts/test/scale-topology-contract.sh \
  scripts/test/ha-profile-contract.sh \
  scripts/test/dubbo-mtls-contract.sh \
  scripts/test/network-reachability-contract.sh \
  scripts/test/judge-sandbox-contract.sh \
  scripts/test/owner-backup-restore-contract.sh \
  scripts/test/supply-chain-contract.sh \
  scripts/test/observability-contract.sh \
  scripts/test/scheduler-contract.sh \
  scripts/test/fenced-lease-contract.sh \
  scripts/test/graceful-drain-contract.sh \
  scripts/test/dependency-resilience-contract.sh \
  scripts/test/tls-profile-contract.sh \
  scripts/test/owner-migration-manifest-contract.sh \
  scripts/test/api-contract-boundary-contract.sh \
  scripts/test/dubbo-provider-reference-contract.sh \
  scripts/dev/docs-contract-test.sh; do
  run_child "$child"
done

echo "Architecture contract: PASS"
