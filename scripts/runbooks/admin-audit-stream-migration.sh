#!/usr/bin/env bash
set -euo pipefail

# Migrate pre-cutover AuditRecorded entries from the former shared stream into
# owner-specific audit streams. This is an explicit operator runbook; it never
# defaults to an environment or claims production execution.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ADMIN_AUDIT_MIGRATION_ENV_FILE:-}"
CONFIRMATION="${ADMIN_AUDIT_MIGRATION_CONFIRM:-}"
QUIESCE_CONFIRM="${ADMIN_AUDIT_MIGRATION_QUIESCE_CONFIRM:-}"
if [[ -z "$ENV_FILE" \
  || "$CONFIRMATION" != "I_HAVE_VERIFIED_ADMIN_AUDIT_STREAM_MIGRATION" \
  || "$QUIESCE_CONFIRM" != "I_HAVE_QUIESCED_ADMIN_AUDIT_WRITERS" ]]; then
  echo "admin-audit-stream-migration: BLOCKED_EXTERNAL (explicit env, writer quiescence, and confirmation required)"
  exit 0
fi
[[ "$ENV_FILE" == /* ]] || ENV_FILE="$ROOT_DIR/$ENV_FILE"
[[ -f "$ENV_FILE" && ! -L "$ENV_FILE" && -O "$ENV_FILE" ]] || {
  echo "admin-audit-stream-migration: env file must be an owned regular file" >&2
  exit 1
}
mode="$(stat -c '%a' -- "$ENV_FILE")"
(( ((8#$mode) & 077) == 0 )) || {
  echo "admin-audit-stream-migration: env file must be owner-only" >&2
  exit 1
}

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
: "${REDIS_HOST:?REDIS_HOST is required}"
: "${REDIS_PORT:?REDIS_PORT is required}"
: "${REDIS_DB:?REDIS_DB is required}"
: "${OPS_REDIS_PASSWORD:?OPS_REDIS_PASSWORD is required}"

CHECKPOINT_FILE="${ADMIN_AUDIT_MIGRATION_CHECKPOINT:-}"
[[ -n "$CHECKPOINT_FILE" ]] || {
  echo "admin-audit-stream-migration: BLOCKED_EXTERNAL (deployment-scoped checkpoint path required)"
  exit 0
}
[[ "$CHECKPOINT_FILE" == /* ]] || CHECKPOINT_FILE="$ROOT_DIR/$CHECKPOINT_FILE"
LOCK_FILE="${ADMIN_AUDIT_MIGRATION_LOCK:-$CHECKPOINT_FILE.lock}"
[[ "$LOCK_FILE" == /* ]] || LOCK_FILE="$ROOT_DIR/$LOCK_FILE"
(umask 077; touch "$LOCK_FILE")
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "admin-audit-stream-migration: BLOCKED_EXTERNAL (migration lock is held)"
  exit 0
fi
if [[ -e "$CHECKPOINT_FILE" ]]; then
  [[ -f "$CHECKPOINT_FILE" && ! -L "$CHECKPOINT_FILE" && -O "$CHECKPOINT_FILE" ]] || {
    echo "admin-audit-stream-migration: checkpoint must be an owned regular file" >&2
    exit 1
  }
  checkpoint_mode="$(stat -c '%a' -- "$CHECKPOINT_FILE")"
  (( ((8#$checkpoint_mode) & 077) == 0 )) || {
    echo "admin-audit-stream-migration: checkpoint must be owner-only" >&2
    exit 1
  }
  checkpoint_content="$(tr -d '\r\n' <"$CHECKPOINT_FILE")"
else
  checkpoint_content=""
fi
if [[ -z "$checkpoint_content" ]]; then
  last_id="-"
else
  IFS='|' read -r source_field db_field stream_field id_field <<<"$checkpoint_content"
  [[ "$source_field" == "source=$REDIS_HOST:$REDIS_PORT" \
    && "$db_field" == "db=$REDIS_DB" \
    && "$stream_field" == "stream=stream:integration" \
    && "$id_field" == last_id=* ]] || {
    echo "admin-audit-stream-migration: checkpoint source/stream does not match this migration" >&2
    exit 1
  }
  last_id="${id_field#last_id=}"
fi
if [[ "$last_id" != "-" && ! "$last_id" =~ ^[0-9]+-[0-9]+$ ]]; then
  echo "admin-audit-stream-migration: invalid checkpoint id" >&2
  exit 1
fi
(umask 077; : >"$CHECKPOINT_FILE.tmp.$$"; rm -f -- "$CHECKPOINT_FILE.tmp.$$")
stream_exists="$(REDISCLI_AUTH="$OPS_REDIS_PASSWORD" redis-cli \
  -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB" --user ulticode-ops --no-auth-warning --raw \
  EXISTS stream:integration 2>&1)" || {
  echo "admin-audit-stream-migration: FAILED (legacy stream existence check failed)" >&2
  exit 1
}
if [[ "$stream_exists" == "0" ]]; then
  echo "admin-audit-stream-migration: PASS (no pre-cutover shared stream; checkpoint retained)"
  exit 0
fi
group_result="$(REDISCLI_AUTH="$OPS_REDIS_PASSWORD" redis-cli \
  -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB" --user ulticode-ops --no-auth-warning --raw \
  XGROUP CREATE stream:integration Admin-Audit 0-0 MKSTREAM 2>&1)" || {
  echo "admin-audit-stream-migration: FAILED (legacy Admin-Audit group check failed)" >&2
  exit 1
}
if [[ -n "$group_result" && "$group_result" != "OK" && "$group_result" != *"BUSYGROUP"* ]]; then
  echo "admin-audit-stream-migration: FAILED (legacy Admin-Audit group could not be verified)" >&2
  exit 1
fi

lua_script="$(cat <<'LUA'
local start = ARGV[1]
if start ~= "-" then start = "(" .. start end
local entries = redis.call("XRANGE", KEYS[1], start, "+", "COUNT", 100)
local migrated = 0
local invalid = 0
local last = ARGV[1]
for _, entry in ipairs(entries) do
  last = entry[1]
  local fields = entry[2]
  local event_type = nil
  local owner = nil
  for i = 1, #fields, 2 do
    if fields[i] == "eventType" then event_type = fields[i + 1] end
    if fields[i] == "owner" then owner = fields[i + 1] end
  end
  local target = nil
  if event_type == "AuditRecorded" and owner == "App" then target = KEYS[2] end
  if event_type == "AuditRecorded" and owner == "Auth" then target = KEYS[3] end
  if event_type == "AuditRecorded" and target == nil then invalid = invalid + 1 end
  if target ~= nil then
    redis.call("XADD", target, "*", unpack(fields))
    redis.call("XACK", KEYS[1], ARGV[2], entry[1])
    migrated = migrated + 1
  end
end
return { #entries, migrated, last, invalid }
LUA
)"

while :; do
  result="$(printf '%s' "$lua_script" | REDISCLI_AUTH="$OPS_REDIS_PASSWORD" redis-cli \
    -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB" --user ulticode-ops --no-auth-warning --raw \
    --eval /dev/stdin stream:integration stream:app-audit stream:auth-audit , "$last_id" Admin-Audit 2>&1)" || {
    echo "admin-audit-stream-migration: FAILED (Redis migration batch failed)" >&2
    exit 1
  }
  entry_count="$(printf '%s\n' "$result" | awk 'NR == 1 { print $1 }')"
  migrated="$(printf '%s\n' "$result" | awk 'NR == 2 { print $1 }')"
  next_id="$(printf '%s\n' "$result" | awk 'NR == 3 { print $1 }')"
  invalid="$(printf '%s\n' "$result" | awk 'NR == 4 { print $1 }')"
  [[ "$entry_count" =~ ^[0-9]+$ && "$migrated" =~ ^[0-9]+$ \
    && "$invalid" =~ ^[0-9]+$ \
    && ( "$next_id" == "-" || "$next_id" =~ ^[0-9]+-[0-9]+$ ) ]] || {
    echo "admin-audit-stream-migration: FAILED (unexpected Redis batch result)" >&2
    exit 1
  }
  [[ "$invalid" == "0" ]] || {
    echo "admin-audit-stream-migration: FAILED (unmapped AuditRecorded owner in legacy stream)" >&2
    exit 1
  }
  [[ "$entry_count" == "0" || "$next_id" != "$last_id" ]] || {
    echo "admin-audit-stream-migration: FAILED (migration checkpoint stalled)" >&2
    exit 1
  }
  if [[ "$next_id" != "$last_id" ]]; then
    checkpoint_tmp="$CHECKPOINT_FILE.tmp.$$"
    printf 'source=%s:%s|db=%s|stream=stream:integration|last_id=%s\n' \
      "$REDIS_HOST" "$REDIS_PORT" "$REDIS_DB" "$next_id" >"$checkpoint_tmp"
    chmod 600 "$checkpoint_tmp"
    mv -- "$checkpoint_tmp" "$CHECKPOINT_FILE"
    last_id="$next_id"
  fi
  [[ "$entry_count" == "0" ]] && break
done

pending_lua='local summary = redis.call("XPENDING", KEYS[1], ARGV[1])
return summary[1]'
pending="$(printf '%s' "$pending_lua" | REDISCLI_AUTH="$OPS_REDIS_PASSWORD" redis-cli \
  -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB" --user ulticode-ops --no-auth-warning --raw \
  --eval /dev/stdin stream:integration , Admin-Audit 2>&1)" || {
  echo "admin-audit-stream-migration: FAILED (legacy PEL verification failed)" >&2
  exit 1
}
[[ "$pending" == "0" ]] || {
  echo "admin-audit-stream-migration: FAILED (legacy Admin-Audit PEL is not empty)" >&2
  exit 1
}

echo "admin-audit-stream-migration: PASS (bounded batches through $last_id; PEL empty; checkpoint retained; old stream retained)"
