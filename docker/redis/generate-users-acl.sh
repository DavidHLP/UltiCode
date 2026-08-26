#!/usr/bin/env bash
# Render docker/redis/users.acl from per-owner Redis credentials.
#
# Usage:
#   docker/redis/generate-users-acl.sh [output-file]   # defaults to stdout
#
# Reads one password per security domain from the environment:
#   AUTH_REDIS_PASSWORD APP_REDIS_PASSWORD ADMIN_REDIS_PASSWORD
#   SUBMISSION_REDIS_PASSWORD SEARCH_REDIS_PASSWORD NOTIFICATION_REDIS_PASSWORD
#   JUDGE_REDIS_PASSWORD OPS_REDIS_PASSWORD
#
# Passwords are stored as SHA-256 hashes (#<hex>), never plaintext. Rotation
# procedure: set the new values in .env / secret store, re-run this script,
# restart the redis container (`docker compose up -d --force-recreate redis`),
# then update the matching *_REDIS_PASSWORD values used by each service.
set -euo pipefail

command -v openssl >/dev/null 2>&1 || { echo "openssl is required" >&2; exit 1; }

VARS=(
  AUTH_REDIS_PASSWORD APP_REDIS_PASSWORD ADMIN_REDIS_PASSWORD
  SUBMISSION_REDIS_PASSWORD SEARCH_REDIS_PASSWORD NOTIFICATION_REDIS_PASSWORD
  JUDGE_REDIS_PASSWORD OPS_REDIS_PASSWORD
)

for v in "${VARS[@]}"; do
  if [[ -z "${!v:-}" ]]; then
    echo "Missing required env var: $v" >&2
    exit 1
  fi
done

hash_of() { printf '%s' "$1" | openssl dgst -sha256 | awk '{print $NF}'; }

DATA_GRANTS="-@all +@connection +@read +@write +@scripting"

# NOTE: redis-server ACL files allow ONLY lines that start with "user" — no
# comments, no blank lines. Keep documentation here and in
# PROJECT_DOCUMENTATION.md ("Redis per-owner ACL model"), not in the output.
#
# The anonymous/default user is disabled: every client must authenticate as a
# named ACL user mapped to exactly one security domain. Key patterns mirror
# the real key inventory in services/*/src/main/java. A service that starts
# using a new key namespace needs its pattern extended below.
cat <<ACL
user default off
user ulticode-ops on #$(hash_of "$OPS_REDIS_PASSWORD") resetkeys ~* resetchannels &* +@all
user ulticode-auth on #$(hash_of "$AUTH_REDIS_PASSWORD") resetkeys ~csrf:* ~oauth:* ~rate-limit:* ~stream:integration resetchannels &* $DATA_GRANTS
user ulticode-admin on #$(hash_of "$ADMIN_REDIS_PASSWORD") resetkeys ~rate-limit:* ~userStats:* ~contestRanking:* ~contest:* resetchannels &* $DATA_GRANTS
user ulticode-app on #$(hash_of "$APP_REDIS_PASSWORD") resetkeys ~rate-limit:* ~userStats:* ~contestRanking:* ~contest:* ~monitoring:* ~queue:* ~judge:* ~email_queue ~notification_queue resetchannels &ulticode:ws:broadcast $DATA_GRANTS +@pubsub +info
user ulticode-submission on #$(hash_of "$SUBMISSION_REDIS_PASSWORD") resetkeys ~stream:integration ~judge:* resetchannels &* $DATA_GRANTS
user ulticode-search on #$(hash_of "$SEARCH_REDIS_PASSWORD") resetkeys ~stream:integration ~search:* resetchannels &* $DATA_GRANTS
user ulticode-notification on #$(hash_of "$NOTIFICATION_REDIS_PASSWORD") resetkeys ~stream:integration ~poison:* ~notification:* resetchannels &ulticode:ws:broadcast $DATA_GRANTS +@pubsub
user ulticode-judge on #$(hash_of "$JUDGE_REDIS_PASSWORD") resetkeys ~judge_queue ~queue:* ~judge:* resetchannels &* $DATA_GRANTS
ACL
