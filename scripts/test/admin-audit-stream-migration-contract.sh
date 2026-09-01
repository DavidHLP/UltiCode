#!/usr/bin/env bash
set -euo pipefail

# Disposable contract test for admin-audit-stream-migration.sh. It creates one
# isolated Redis container, seeds a pending legacy AuditRecorded entry, runs the
# migration, verifies the owner stream and old PEL, then destroys everything.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
grep -Fq 'flock -n 9' "$ROOT_DIR/scripts/runbooks/admin-audit-stream-migration.sh"
grep -Fq 'REDIS_DB' "$ROOT_DIR/scripts/runbooks/admin-audit-stream-migration.sh"
command -v docker >/dev/null 2>&1 || {
  echo "admin-audit-stream-migration-contract: BLOCKED_EXTERNAL (docker unavailable)"
  exit 0
}

PROJECT="ulticode-audit-migration-contract-$$"
ACL_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ulticode-audit-migration-acl.XXXXXX")"
ENV_FILE="$(mktemp "${TMPDIR:-/tmp}/ulticode-audit-migration-env.XXXXXX")"
CHECKPOINT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/ulticode-audit-migration-checkpoint.XXXXXX")"
chmod 755 "$ACL_DIR" "$CHECKPOINT_DIR"
chmod 600 "$ENV_FILE"
readonly PROJECT ACL_DIR ENV_FILE CHECKPOINT_DIR
cleanup() {
  local rc=$?
  trap - EXIT INT TERM
  docker rm -f "$PROJECT-redis" >/dev/null 2>&1 || true
  rm -rf -- "$ACL_DIR" "$ENV_FILE" "$CHECKPOINT_DIR"
  exit "$rc"
}
trap cleanup EXIT INT TERM
REDIS_DB=7
for prefix in AUTH ADMIN APP SUBMISSION SEARCH NOTIFICATION JUDGE OPS HEALTH; do
  variable="${prefix}_REDIS_PASSWORD"
  printf -v "$variable" '%s' "contract-${prefix,,}-password-$(openssl rand -hex 8)"
  export "$variable"
done
REDIS_REPLICATION_PASSWORD="contract-replication-$(openssl rand -hex 8)"
REDIS_SENTINEL_PASSWORD="contract-sentinel-$(openssl rand -hex 8)"
export REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD
"$ROOT_DIR/docker/redis/generate-users-acl.sh" "$ACL_DIR/users.acl"

container_id="$(docker run -d --rm --name "$PROJECT-redis" -p 127.0.0.1::6379 \
  -v "$ACL_DIR:/usr/local/etc/redis:ro" redis:7-alpine \
  redis-server --aclfile /usr/local/etc/redis/users.acl --save '' --appendonly no)"
port="$(docker port "$container_id" 6379/tcp 2>/dev/null | awk -F: 'NR == 1 { print $NF }')"
if [[ -z "$port" ]]; then
  echo "admin-audit-stream-migration-contract: BLOCKED_EXTERNAL (ephemeral Redis port unavailable)"
  exit 0
fi
printf 'REDIS_HOST=127.0.0.1\nREDIS_PORT=%s\nREDIS_DB=%s\nOPS_REDIS_PASSWORD=%s\n' "$port" "$REDIS_DB" "$OPS_REDIS_PASSWORD" >"$ENV_FILE"

redis() {
  local command_user="$1" command_password="$2"
  shift 2
  docker run --rm --network host redis:7 redis-cli \
    -h 127.0.0.1 -p "$port" -n "$REDIS_DB" --user "$command_user" --pass "$command_password" \
    --no-auth-warning "$@"
}
for attempt in $(seq 1 20); do
  [[ "$(redis ulticode-health "$HEALTH_REDIS_PASSWORD" ping 2>/dev/null || true)" == "PONG" ]] && break
  [[ "$attempt" == 20 ]] && { echo "admin-audit-stream-migration-contract: BLOCKED_EXTERNAL (Redis startup timeout)"; exit 0; }
  sleep 1
done
legacy_id="$(redis ulticode-ops "$OPS_REDIS_PASSWORD" XADD stream:integration '*' eventId contract-audit owner App eventType AuditRecorded schemaVersion 1 aggregateId contract-audit aggregateVersion 0 payload '{}' | tr -d '\r')"
redis ulticode-ops "$OPS_REDIS_PASSWORD" XGROUP CREATE stream:integration Admin-Audit 0-0 MKSTREAM >/dev/null 2>&1 || true
redis ulticode-ops "$OPS_REDIS_PASSWORD" XREADGROUP GROUP Admin-Audit contract-migrator COUNT 1 STREAMS stream:integration '>' >/dev/null
run_migration() {
  docker run --rm --network host --user "$(id -u):$(id -g)" \
    -v "$ROOT_DIR:/repo:ro" -v "$ENV_FILE:/tmp/admin-audit-migration.env:ro" \
    -v "$CHECKPOINT_DIR:/tmp/admin-audit-checkpoints" \
    redis:7 sh -c \
    'ADMIN_AUDIT_MIGRATION_ENV_FILE=/tmp/admin-audit-migration.env ADMIN_AUDIT_MIGRATION_CHECKPOINT=/tmp/admin-audit-checkpoints/checkpoint ADMIN_AUDIT_MIGRATION_CONFIRM=I_HAVE_VERIFIED_ADMIN_AUDIT_STREAM_MIGRATION ADMIN_AUDIT_MIGRATION_QUIESCE_CONFIRM=I_HAVE_QUIESCED_ADMIN_AUDIT_WRITERS /repo/scripts/runbooks/admin-audit-stream-migration.sh'
}
run_migration
legacy_id_2="$(redis ulticode-ops "$OPS_REDIS_PASSWORD" XADD stream:integration '*' eventId contract-audit-2 owner Auth eventType AuditRecorded schemaVersion 1 aggregateId contract-audit-2 aggregateVersion 0 payload '{}' | tr -d '\r')"
redis ulticode-ops "$OPS_REDIS_PASSWORD" XREADGROUP GROUP Admin-Audit contract-migrator COUNT 1 STREAMS stream:integration '>' >/dev/null
run_migration

app_event="$(redis ulticode-ops "$OPS_REDIS_PASSWORD" XRANGE stream:app-audit - + | tr -d '\r')"
[[ "$app_event" == *"contract-audit"* && "$app_event" != *"contract-audit-2"* ]] || {
  echo "admin-audit-stream-migration-contract: FAIL (App owner event missing or duplicated)" >&2
  exit 1
}
auth_event="$(redis ulticode-ops "$OPS_REDIS_PASSWORD" XRANGE stream:auth-audit - + | tr -d '\r')"
[[ "$auth_event" == *"contract-audit-2"* ]] || {
  echo "admin-audit-stream-migration-contract: FAIL (checkpoint-resumed Auth event missing)" >&2
  exit 1
}
pending="$(redis ulticode-ops "$OPS_REDIS_PASSWORD" XPENDING stream:integration Admin-Audit | awk 'NR == 1 { print $1 }')"
[[ "$pending" == "0" ]] || {
  echo "admin-audit-stream-migration-contract: FAIL (legacy PEL not acknowledged for $legacy_id)" >&2
  exit 1
}
echo "admin-audit-stream-migration-contract: PASS (migrate, preserve event id, ACK legacy PEL)"
