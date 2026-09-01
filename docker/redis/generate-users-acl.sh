#!/usr/bin/env bash
# Render a runtime Redis ACL file from per-owner Redis credentials.
#
# Usage:
#   docker/redis/generate-users-acl.sh [output-file]   # defaults to REDIS_ACL_FILE/stdout
#
# Reads one password per security domain from the environment:
#   AUTH_REDIS_PASSWORD APP_REDIS_PASSWORD ADMIN_REDIS_PASSWORD
#   SUBMISSION_REDIS_PASSWORD SEARCH_REDIS_PASSWORD NOTIFICATION_REDIS_PASSWORD
#   JUDGE_REDIS_PASSWORD OPS_REDIS_PASSWORD HEALTH_REDIS_PASSWORD
#   REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD
#
# Passwords are stored as SHA-256 hashes (#<hex>), never plaintext. An optional
# <PREFIX>_REDIS_PASSWORD_PREVIOUS adds a second hash for overlap rotation.
# Render to a same-filesystem temporary file, then atomically replace the
# runtime ACL file; the tracked repository contains no generated verifier.
set -euo pipefail

command -v openssl >/dev/null 2>&1 || { echo "openssl is required" >&2; exit 1; }

OUTPUT_FILE="${1:-${REDIS_ACL_FILE:-}}"
[[ $# -le 1 ]] || { echo "Usage: $0 [output-file]" >&2; exit 2; }
VARS=(
  AUTH_REDIS_PASSWORD APP_REDIS_PASSWORD ADMIN_REDIS_PASSWORD
  SUBMISSION_REDIS_PASSWORD SEARCH_REDIS_PASSWORD NOTIFICATION_REDIS_PASSWORD
  JUDGE_REDIS_PASSWORD OPS_REDIS_PASSWORD HEALTH_REDIS_PASSWORD
  REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD
)

for v in "${VARS[@]}"; do
  if [[ -z "${!v:-}" ]]; then
    echo "Missing required env var: $v" >&2
    exit 1
  fi
done

hash_of() { printf '%s' "$1" | openssl dgst -sha256 | awk '{print $NF}'; }

password_hashes() {
  local variable="$1" previous_variable="${1}_PREVIOUS"
  printf '#%s' "$(hash_of "${!variable}")"
  if [[ -n "${!previous_variable:-}" ]]; then
    printf ' #%s' "$(hash_of "${!previous_variable}")"
  fi
}

# Exact commands needed by the current Spring Data Redis, Redisson, and stream
# adapters. No command categories are used: administrative commands such as
# FLUSHDB, FLUSHALL, CONFIG, SHUTDOWN, MODULE, and DEBUG remain denied.
COMMON_COMMANDS="+auth +hello +ping +quit +select +client|id +client|setname +get +set +getdel +del +unlink +exists +expire +pexpire +expireat +pexpireat +ttl +pttl +incr +incrby +decr +decrby +incrbyfloat +hget +hset +hdel +hexists +hgetall +hkeys +hlen +hmget +hmset +hvals +hincrby +hincrbyfloat +sadd +srem +sismember +smembers +scard +srandmember +zadd +zrem +zrange +zrevrange +zrangebyscore +zrevrangebyscore +zcard +zscore +lpush +rpush +lpop +rpop +lrange +llen +ltrim +type +scan +multi +exec +discard +watch +unwatch +eval +evalsha +script|exists +script|load +xadd +xlen +xread +xreadgroup +xack +xpending +xclaim +xautoclaim +xgroup +xrange +xrevrange +xdel +xtrim +xinfo"
PUBSUB_COMMANDS="+publish +subscribe +psubscribe +unsubscribe +punsubscribe"
COMMON_COMMANDS="${COMMON_COMMANDS/ +get/ +client|setinfo +get}"

# NOTE: redis-server ACL files allow ONLY lines that start with "user" — no
# comments or blank lines. Keep the model documented in
# `docs/architecture/security.md`; keep this generator focused on emitting
# the runtime file.
#
# stream:integration is the single intentional shared event-bus key. It is not
# an owner data namespace; all participants receive only the stream commands
# above. Every other key pattern belongs to one owner.
render_acl() {
cat <<ACL
user default off
user ulticode-health on $(password_hashes HEALTH_REDIS_PASSWORD) resetkeys resetchannels -@all +ping
user ulticode-ops on $(password_hashes OPS_REDIS_PASSWORD) resetkeys ~* resetchannels &* -@all $COMMON_COMMANDS $PUBSUB_COMMANDS +info +acl|whoami +acl|load
user ulticode-auth on $(password_hashes AUTH_REDIS_PASSWORD) resetkeys ~csrf:* ~oauth:* ~rate-limit:* ~auth:* ~security:delegation:replay:* ~stream:integration resetchannels -@all $COMMON_COMMANDS
user ulticode-admin on $(password_hashes ADMIN_REDIS_PASSWORD) resetkeys ~rate-limit:* ~stream:integration ~userStats:* ~contestRanking:* ~contest:* resetchannels -@all $COMMON_COMMANDS
user ulticode-app on $(password_hashes APP_REDIS_PASSWORD) resetkeys ~rate-limit:* ~userStats:* ~contestRanking:* ~contest:* ~monitoring:* ~queue:* ~judge:* ~problem:* ~blacklist:* ~email_queue ~notification_queue ~security:delegation:replay:* ~stream:integration resetchannels &ulticode:ws:broadcast -@all $COMMON_COMMANDS $PUBSUB_COMMANDS +info
user ulticode-submission on $(password_hashes SUBMISSION_REDIS_PASSWORD) resetkeys ~stream:integration ~judge:* ~security:delegation:replay:* resetchannels -@all $COMMON_COMMANDS
user ulticode-search on $(password_hashes SEARCH_REDIS_PASSWORD) resetkeys ~stream:integration ~search:* resetchannels -@all $COMMON_COMMANDS
user ulticode-notification on $(password_hashes NOTIFICATION_REDIS_PASSWORD) resetkeys ~stream:integration ~rate-limit:* ~poison:* ~notification:* ~security:delegation:replay:* resetchannels &ulticode:ws:broadcast -@all $COMMON_COMMANDS $PUBSUB_COMMANDS
user ulticode-judge on $(password_hashes JUDGE_REDIS_PASSWORD) resetkeys ~judge_queue ~queue:* ~judge:* resetchannels -@all $COMMON_COMMANDS
user ulticode-replication on $(password_hashes REDIS_REPLICATION_PASSWORD) resetkeys ~* resetchannels -@all +auth +hello +ping +psync +replconf +role
user ulticode-sentinel on $(password_hashes REDIS_SENTINEL_PASSWORD) resetkeys ~* resetchannels -@all +auth +hello +ping +info +multi +exec +publish +subscribe +psubscribe +unsubscribe +punsubscribe +script|exists +script|load +script|kill +slaveof +replicaof +config|rewrite +client|kill +client|setname +role
ACL
}

if [[ -n "$OUTPUT_FILE" ]]; then
  output_dir="$(dirname -- "$OUTPUT_FILE")"
  mkdir -p "$output_dir"
  tmp_file="$(mktemp "$output_dir/.users.acl.XXXXXX")"
  trap 'rm -f "$tmp_file"' EXIT
  render_acl > "$tmp_file"
  # Redis runs as a non-root container user and must read the bind-mounted
  # runtime file. The file contains one-way hashes only; plaintext secrets
  # remain in the caller's environment and never enter this output.
  chmod 644 "$tmp_file"
  mv -- "$tmp_file" "$OUTPUT_FILE"
  trap - EXIT
else
  render_acl
fi
