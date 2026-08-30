#!/usr/bin/env bash
# Render docker/redis/users.acl from per-owner Redis credentials.
#
# Usage:
#   docker/redis/generate-users-acl.sh [output-file]   # defaults to stdout
#
# Reads one password per security domain from the environment:
#   AUTH_REDIS_PASSWORD APP_REDIS_PASSWORD ADMIN_REDIS_PASSWORD
#   SUBMISSION_REDIS_PASSWORD SEARCH_REDIS_PASSWORD NOTIFICATION_REDIS_PASSWORD
#   JUDGE_REDIS_PASSWORD OPS_REDIS_PASSWORD HEALTH_REDIS_PASSWORD
#
# Passwords are stored as SHA-256 hashes (#<hex>), never plaintext. Rotation
# procedure: set new values in the secret store, render to a same-filesystem
# temporary file, then atomically replace the mounted ACL file.
set -euo pipefail

command -v openssl >/dev/null 2>&1 || { echo "openssl is required" >&2; exit 1; }

OUTPUT_FILE="${1:-}"
[[ $# -le 1 ]] || { echo "Usage: $0 [output-file]" >&2; exit 2; }

VARS=(
  AUTH_REDIS_PASSWORD APP_REDIS_PASSWORD ADMIN_REDIS_PASSWORD
  SUBMISSION_REDIS_PASSWORD SEARCH_REDIS_PASSWORD NOTIFICATION_REDIS_PASSWORD
  JUDGE_REDIS_PASSWORD OPS_REDIS_PASSWORD HEALTH_REDIS_PASSWORD
)

for v in "${VARS[@]}"; do
  if [[ -z "${!v:-}" ]]; then
    echo "Missing required env var: $v" >&2
    exit 1
  fi
done

hash_of() { printf '%s' "$1" | openssl dgst -sha256 | awk '{print $NF}'; }

# Exact commands needed by the current Spring Data Redis, Redisson, and stream
# adapters. No command categories are used: administrative commands such as
# FLUSHDB, FLUSHALL, CONFIG, SHUTDOWN, MODULE, and DEBUG remain denied.
COMMON_COMMANDS="+auth +hello +ping +quit +select +client|id +client|setname +get +set +getdel +del +unlink +exists +expire +pexpire +expireat +pexpireat +ttl +pttl +incr +incrby +decr +decrby +incrbyfloat +hget +hset +hdel +hexists +hgetall +hkeys +hlen +hmget +hmset +hvals +hincrby +hincrbyfloat +sadd +srem +sismember +smembers +scard +srandmember +zadd +zrem +zrange +zrevrange +zrangebyscore +zrevrangebyscore +zcard +zscore +lpush +rpush +lpop +rpop +lrange +llen +ltrim +type +scan +multi +exec +discard +watch +unwatch +eval +evalsha +script|exists +script|load +xadd +xread +xreadgroup +xack +xpending +xclaim +xautoclaim +xgroup +xrange +xrevrange +xdel +xtrim +xinfo"
PUBSUB_COMMANDS="+publish +subscribe +psubscribe +unsubscribe +punsubscribe"

# NOTE: redis-server ACL files allow ONLY lines that start with "user" — no
# comments or blank lines. Keep documentation here and in
# PROJECT_DOCUMENTATION.md ("Redis per-owner ACL model"), not in the output.
#
# stream:integration is the single intentional shared event-bus key. It is not
# an owner data namespace; all participants receive only the stream commands
# above. Every other key pattern belongs to one owner.
render_acl() {
cat <<ACL
user default off
user ulticode-health on #$(hash_of "$HEALTH_REDIS_PASSWORD") resetkeys resetchannels -@all +ping
user ulticode-ops on #$(hash_of "$OPS_REDIS_PASSWORD") resetkeys ~* resetchannels &* -@all $COMMON_COMMANDS $PUBSUB_COMMANDS +info +acl|whoami
user ulticode-auth on #$(hash_of "$AUTH_REDIS_PASSWORD") resetkeys ~csrf:* ~oauth:* ~rate-limit:* ~auth:* ~security:delegation:replay:* ~stream:integration resetchannels -@all $COMMON_COMMANDS
user ulticode-admin on #$(hash_of "$ADMIN_REDIS_PASSWORD") resetkeys ~rate-limit:* ~userStats:* ~contestRanking:* ~contest:* resetchannels -@all $COMMON_COMMANDS
user ulticode-app on #$(hash_of "$APP_REDIS_PASSWORD") resetkeys ~rate-limit:* ~userStats:* ~contestRanking:* ~contest:* ~monitoring:* ~queue:* ~judge:* ~problem:* ~email_queue ~notification_queue ~security:delegation:replay:* ~stream:integration resetchannels &ulticode:ws:broadcast -@all $COMMON_COMMANDS $PUBSUB_COMMANDS +info
user ulticode-submission on #$(hash_of "$SUBMISSION_REDIS_PASSWORD") resetkeys ~stream:integration ~judge:* ~security:delegation:replay:* resetchannels -@all $COMMON_COMMANDS
user ulticode-search on #$(hash_of "$SEARCH_REDIS_PASSWORD") resetkeys ~stream:integration ~search:* resetchannels -@all $COMMON_COMMANDS
user ulticode-notification on #$(hash_of "$NOTIFICATION_REDIS_PASSWORD") resetkeys ~stream:integration ~poison:* ~notification:* ~security:delegation:replay:* resetchannels &ulticode:ws:broadcast -@all $COMMON_COMMANDS $PUBSUB_COMMANDS
user ulticode-judge on #$(hash_of "$JUDGE_REDIS_PASSWORD") resetkeys ~judge_queue ~queue:* ~judge:* resetchannels -@all $COMMON_COMMANDS
ACL
}

if [[ -n "$OUTPUT_FILE" ]]; then
  output_dir="$(dirname -- "$OUTPUT_FILE")"
  tmp_file="$(mktemp "$output_dir/.users.acl.XXXXXX")"
  trap 'rm -f "$tmp_file"' EXIT
  render_acl > "$tmp_file"
  chmod 644 "$tmp_file"
  mv -- "$tmp_file" "$OUTPUT_FILE"
  trap - EXIT
else
  render_acl
fi
