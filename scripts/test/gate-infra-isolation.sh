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
STREAM_REDIS_CONTAINER=""
SMOKE_ENV_FILE=""
cleanup() {
  local status=$?
  if [[ -n "$STREAM_REDIS_CONTAINER" ]]; then
    docker rm -f "$STREAM_REDIS_CONTAINER" >/dev/null 2>&1 || status=1
  fi
  if [[ -n "$SMOKE_ENV_FILE" ]]; then
    rm -f -- "$SMOKE_ENV_FILE" || status=1
  fi
  exit "$status"
}

SMOKE_PORT_BASE="$((39000 + (BASHPID % 500)))"
SMOKE_HTTP_PORT_BASE="$((41000 + (BASHPID % 500)))"
SMOKE_DUBBO_PORT_BASE="$((42000 + (BASHPID % 500)))"
SMOKE_ENV_FILE="$(mktemp "${TMPDIR:-/tmp}/ulticode-infra-gate-env.XXXXXX")"
cp -- "$ENV_FILE" "$SMOKE_ENV_FILE"
printf '\nDB_PORT=%s\nAUTH_DB_HOST=127.0.0.1\nAUTH_DB_PORT=%s\nREDIS_PORT=%s\nNACOS_PORT=%s\nNACOS_GRPC_PORT=%s\n' \
  "$SMOKE_PORT_BASE" "$SMOKE_PORT_BASE" "$((SMOKE_PORT_BASE + 1))" \
  "$((SMOKE_PORT_BASE + 2))" "$((SMOKE_PORT_BASE + 1002))" >>"$SMOKE_ENV_FILE"
chmod 600 "$SMOKE_ENV_FILE"
trap cleanup EXIT

run_stream_resilience() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "GATE-INFRA-ISOLATION: BLOCKED_EXTERNAL (Docker is required for disposable Redis)" >&2
    return 1
  fi
  STREAM_REDIS_CONTAINER="$(docker run --detach --publish 127.0.0.1::6379 redis:7 2>/dev/null)" || {
    echo "GATE-INFRA-ISOLATION: BLOCKED_EXTERNAL (disposable Redis could not start)" >&2
    return 1
  }
  local redis_port=""
  for _ in $(seq 1 30); do
    if docker exec "$STREAM_REDIS_CONTAINER" redis-cli ping >/dev/null 2>&1; then
      redis_port="$(docker port "$STREAM_REDIS_CONTAINER" 6379/tcp | cut -d: -f2)"
      break
    fi
    sleep 1
  done
  [[ "$redis_port" =~ ^[0-9]+$ ]] || {
    echo "GATE-INFRA-ISOLATION: FAIL (disposable Redis did not become ready)" >&2
    return 1
  }
  REDIS_HOST=127.0.0.1 REDIS_PORT="$redis_port" REDIS_DB=0 \
    REDIS_USERNAME="" REDIS_PASSWORD="" \
    bash "$ROOT_DIR/scripts/test/stream-resilience-contract.sh"
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
  scripts/test/meilisearch-recovery-contract.sh
  scripts/test/dubbo-nacos-smoke.sh
  scripts/test/nacos-security-contract.sh
  scripts/runbooks/admin-audit-stream-migration.sh
)
for file in "${required_files[@]}"; do
  [[ -f "$ROOT_DIR/$file" ]] || { echo "GATE-INFRA-ISOLATION: FAIL (missing $file)" >&2; exit 1; }
done

bash "$ROOT_DIR/scripts/test/redis-acl-contract.sh"
REDIS_ROLE_DRILL_ENV_FILE="$ENV_FILE" bash "$ROOT_DIR/scripts/test/redis-role-fault-drill.sh"
bash "$ROOT_DIR/scripts/test/admin-audit-stream-migration-contract.sh"
run_stream_resilience
bash "$ROOT_DIR/scripts/test/nacos-security-contract.sh"
DUBBO_NACOS_SMOKE_REGISTRY_DRILL=1 DUBBO_NACOS_SMOKE_REPLICAS=1 \
  DUBBO_NACOS_SMOKE_HTTP_PORT_BASE="$SMOKE_HTTP_PORT_BASE" \
  DUBBO_NACOS_SMOKE_DUBBO_PORT_BASE="$SMOKE_DUBBO_PORT_BASE" \
  ENV_FILE="$SMOKE_ENV_FILE" bash "$ROOT_DIR/scripts/test/dubbo-nacos-smoke.sh"
bash "$ROOT_DIR/scripts/test/meilisearch-recovery-contract.sh"
bash "$ROOT_DIR/scripts/test/dependency-resilience-contract.sh"

# The MySQL recovery contract is the disposable restore proof. The separate
# P1-004 matrix records the exact owner pool gaps instead of inventing values.
bash "$ROOT_DIR/scripts/test/owner-backup-restore-contract.sh"

printf 'GATE-INFRA-ISOLATION: PASS (repository/disposable scenarios; no production claim)\n'
