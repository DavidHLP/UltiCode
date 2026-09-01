#!/usr/bin/env bash
set -euo pipefail

# GATE-INFRA-ISOLATION: repository/disposable checks only. Production failover,
# capacity, and long-running SLO evidence are explicitly outside this gate.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${INFRA_GATE_ENV_FILE:-$ROOT_DIR/.env}"
[[ -f "$ENV_FILE" && ! -L "$ENV_FILE" && -O "$ENV_FILE" ]] || {
  echo "GATE-INFRA-ISOLATION: BLOCKED_EXTERNAL (owner-only disposable env required)" >&2
  exit 1
}
mode="$(stat -c '%a' -- "$ENV_FILE")"
(( ((8#$mode) & 077) == 0 )) || {
  echo "GATE-INFRA-ISOLATION: FAIL (disposable env is not owner-only)" >&2
  exit 1
}

required_files=(
  docs/architecture/evidence/P1-INFRA-001-redis-role-decision.md
  docs/architecture/evidence/P1-INFRA-002-redis-role-seam.md
  docs/architecture/evidence/P1-INFRA-003-redis-fault-drill.md
  docs/architecture/evidence/P1-INFRA-004-mysql-owner-matrix.md
  docs/architecture/evidence/P1-INFRA-005-search-recovery-contract.md
  docs/architecture/evidence/P1-INFRA-006-nacos-failure-contract.md
  scripts/test/redis-role-fault-drill.sh
  scripts/test/admin-audit-stream-migration-contract.sh
  scripts/runbooks/admin-audit-stream-migration.sh
)
for file in "${required_files[@]}"; do
  [[ -f "$ROOT_DIR/$file" ]] || { echo "GATE-INFRA-ISOLATION: FAIL (missing $file)" >&2; exit 1; }
done

bash "$ROOT_DIR/scripts/test/redis-acl-contract.sh"
REDIS_ROLE_DRILL_ENV_FILE="$ENV_FILE" bash "$ROOT_DIR/scripts/test/redis-role-fault-drill.sh"
bash "$ROOT_DIR/scripts/test/admin-audit-stream-migration-contract.sh"
bash "$ROOT_DIR/scripts/test/stream-resilience-contract.sh"
bash "$ROOT_DIR/scripts/test/dependency-resilience-contract.sh"
bash "$ROOT_DIR/scripts/test/nacos-security-contract.sh"

# The MySQL recovery contract is the disposable restore proof. The separate
# P1-004 matrix records the exact owner pool gaps instead of inventing values.
bash "$ROOT_DIR/scripts/test/owner-backup-restore-contract.sh"

printf 'GATE-INFRA-ISOLATION: PASS (repository/disposable scenarios; no production claim)\n'
