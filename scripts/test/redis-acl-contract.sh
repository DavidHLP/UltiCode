#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_FILE="$(mktemp)"
trap 'rm -f "$OUTPUT_FILE"' EXIT

export AUTH_REDIS_PASSWORD=test-auth-password
export ADMIN_REDIS_PASSWORD=test-admin-password
export APP_REDIS_PASSWORD=test-app-password
export SUBMISSION_REDIS_PASSWORD=test-submission-password
export SEARCH_REDIS_PASSWORD=test-search-password
export NOTIFICATION_REDIS_PASSWORD=test-notification-password
export JUDGE_REDIS_PASSWORD=test-judge-password
export OPS_REDIS_PASSWORD=test-ops-password
export HEALTH_REDIS_PASSWORD=test-health-password

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
  [[ "$line" != *"~*"* ]] || { echo "$user has an unrestricted key pattern" >&2; exit 1; }
  [[ "$line" != *"&*"* ]] || { echo "$user has an unrestricted channel pattern" >&2; exit 1; }
  for command in flushdb flushall config shutdown module debug; do
    [[ "$line" != *"+$command"* ]] || { echo "$user allows $command" >&2; exit 1; }
  done
done

health_line="$(grep -F 'user ulticode-health ' "$OUTPUT_FILE")"
[[ "$health_line" == *"-@all +ping"* ]] || { echo "health principal is broader than PING" >&2; exit 1; }
[[ "$health_line" != *" +get"* && "$health_line" != *" +set"* ]] || {
  echo "health principal has data access" >&2
  exit 1
}

grep -Fq 'user ulticode-app ' "$OUTPUT_FILE"
grep -Fq 'user ulticode-notification ' "$OUTPUT_FILE"
grep -Fq '~stream:integration' "$OUTPUT_FILE"
grep -Fq '&ulticode:ws:broadcast' "$OUTPUT_FILE"

printf '%s\n' "Redis ACL contract: PASS"
