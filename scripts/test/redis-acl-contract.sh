#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_FILE="$(mktemp)"
trap 'rm -f "$OUTPUT_FILE"' EXIT

password_prefixes=(AUTH ADMIN APP SUBMISSION SEARCH NOTIFICATION JUDGE OPS HEALTH)
for prefix in "${password_prefixes[@]}"; do
  password_var="${prefix}_REDIS_PASSWORD"
  printf -v "$password_var" '%s' "$(openssl rand -hex 24)"
  export "$password_var"
done
for password_var in REDIS_REPLICATION_PASSWORD REDIS_SENTINEL_PASSWORD; do
  printf -v "$password_var" '%s' "$(openssl rand -hex 24)"
  export "$password_var"
done
AUTH_REDIS_PASSWORD_PREVIOUS="$(openssl rand -hex 24)"
export AUTH_REDIS_PASSWORD_PREVIOUS

"$ROOT_DIR/docker/redis/generate-users-acl.sh" "$OUTPUT_FILE"
grep -Fxq 'user default off' "$OUTPUT_FILE"

business_users=(
  ulticode-auth
  ulticode-admin
  ulticode-app
  ulticode-submission
  ulticode-search
  ulticode-notification
  ulticode-judge
)
for user in "${business_users[@]}"; do
  line="$(grep -F "user $user " "$OUTPUT_FILE")"
  [[ "$line" == *"-@all"* ]] || { echo "$user is not deny-by-default" >&2; exit 1; }
  [[ "$line" != *"+@"* ]] || { echo "$user uses command categories" >&2; exit 1; }
  [[ "$line" == *"+client|setinfo"* ]] || { echo "$user lacks the required CLIENT SETINFO command" >&2; exit 1; }
  [[ "$line" == *"+xlen"* ]] || { echo "$user lacks the required XLEN stream command" >&2; exit 1; }
  [[ "$line" != *"~*"* ]] || { echo "$user has an unrestricted key pattern" >&2; exit 1; }
  [[ "$line" != *"&*"* ]] || { echo "$user has an unrestricted channel pattern" >&2; exit 1; }
  for command in flushdb flushall config shutdown module debug; do
    [[ "$line" != *"+$command"* ]] || { echo "$user allows $command" >&2; exit 1; }
  done
done

health_line="$(grep -F 'user ulticode-health ' "$OUTPUT_FILE")"
[[ "$health_line" == *"-@all +ping"* ]] || { echo "health principal is broader than PING" >&2; exit 1; }
replication_line="$(grep -F 'user ulticode-replication ' "$OUTPUT_FILE")"
[[ "$replication_line" == *"-@all"* && "$replication_line" == *"+psync"* \
  && "$replication_line" == *"+replconf"* && "$replication_line" == *"+role"* ]] \
  || { echo "replication principal lacks the required commands" >&2; exit 1; }
sentinel_line="$(grep -F 'user ulticode-sentinel ' "$OUTPUT_FILE")"
for command in ping info role subscribe script\|kill slaveof replicaof config\|rewrite; do
  [[ "$sentinel_line" == *"+$command"* ]] \
    || { echo "sentinel principal lacks +$command" >&2; exit 1; }
done
[[ "$health_line" != *" +get"* && "$health_line" != *" +set"* ]] || {
  echo "health principal has data access" >&2
  exit 1
}

app_line="$(grep -F 'user ulticode-app ' "$OUTPUT_FILE")"
[[ "$app_line" == *"~blacklist:"* ]] || { echo "app principal lacks blacklist keyspace" >&2; exit 1; }
notification_line="$(grep -F 'user ulticode-notification ' "$OUTPUT_FILE")"
[[ "$notification_line" == *"~rate-limit:"* ]] || { echo "notification principal lacks rate-limit keyspace" >&2; exit 1; }
admin_line="$(grep -F 'user ulticode-admin ' "$OUTPUT_FILE")"
[[ "$admin_line" == *"~stream:integration"* ]] || { echo "admin principal lacks shared integration stream keyspace" >&2; exit 1; }
grep -Fq '&ulticode:ws:broadcast' "$OUTPUT_FILE"
grep -Fq '+acl|load' "$OUTPUT_FILE"
auth_hash="$(printf '%s' "$AUTH_REDIS_PASSWORD" | openssl dgst -sha256 | awk '{print $NF}')"
previous_auth_hash="$(printf '%s' "$AUTH_REDIS_PASSWORD_PREVIOUS" | openssl dgst -sha256 | awk '{print $NF}')"
auth_line="$(grep -F 'user ulticode-auth ' "$OUTPUT_FILE")"
[[ "$auth_line" == *"#$auth_hash"* && "$auth_line" == *"#$previous_auth_hash"* ]]
for prefix in "${password_prefixes[@]}"; do
  password_var="${prefix}_REDIS_PASSWORD"
  ! grep -F "${!password_var}" "$OUTPUT_FILE" >/dev/null
done
[[ "$(stat -c '%a' "$OUTPUT_FILE")" == "644" ]]

printf '%s\n' "Redis ACL contract: PASS"
