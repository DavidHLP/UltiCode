#!/bin/bash
# Moderation API smoke test through the owner gateway.
# Tests moderation API endpoints against a running Auth/Admin/App stack.
# Requires: curl, a running gateway.
#
# Usage: ./moderation-api-smoke.sh [BASE_URL] [USERNAME] [PASSWORD]
#   Credentials default to SMOKE_USER / SMOKE_PASS env vars.
#   For dev: export SMOKE_USER=admin SMOKE_PASS=admin123

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"
ARTIFACT_ROOT=".tmp/moderation-api-smoke"
umask 077
mkdir -p "$ARTIFACT_ROOT"
ARTIFACT_DIR="$(mktemp -d "$ARTIFACT_ROOT/run.XXXXXX")"
COOKIE_JAR="$ARTIFACT_DIR/cookies.txt"
trap 'rm -rf -- "$ARTIFACT_DIR"; rmdir "$ARTIFACT_ROOT" 2>/dev/null || true' EXIT

BASE_URL="${1:-${SMOKE_BASE_URL:-http://localhost:9003/api}}"
USERNAME="${2:-${SMOKE_USER:-}}"
SMOKE_CRED="${3:-${SMOKE_PASS:-}}"

if [ -z "$USERNAME" ] || [ -z "$SMOKE_CRED" ]; then
  echo "Usage: $0 [BASE_URL] [USERNAME] [PASSWORD]"
  echo "  Or set SMOKE_USER and SMOKE_PASS environment variables."
  echo "  Example: SMOKE_USER=admin SMOKE_PASS=admin123 $0"
  exit 1
fi

PASS=0
FAIL=0
RESULTS=()

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_pass() { PASS=$((PASS + 1)); RESULTS+=("${GREEN}PASS${NC} | $1 | $2"); }
log_fail() { FAIL=$((FAIL + 1)); RESULTS+=("${RED}FAIL${NC} | $1 | $2"); }

echo "=== Moderation API Smoke Test ==="
echo "Backend: $BASE_URL"
echo ""

# Step 1: Login to get JWT cookie
echo "Logging in..."
LOGIN_RESPONSE=$(curl -s -c "$COOKIE_JAR" -w '\n%{http_code}' \
  -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${SMOKE_CRED}\"}" 2>/dev/null || echo -e "\n000")

LOGIN_STATUS=$(echo "$LOGIN_RESPONSE" | tail -1)
LOGIN_BODY=$(echo "$LOGIN_RESPONSE" | sed '$d')

if [ "$LOGIN_STATUS" != "200" ]; then
  echo "Login failed (HTTP $LOGIN_STATUS). Cannot proceed."
  echo "Response: $LOGIN_BODY"
  exit 1
fi

# Extract CSRF token from cookies
CSRF_TOKEN=$(grep -i 'XSRF-TOKEN' "$COOKIE_JAR" 2>/dev/null | awk '{print $NF}' || echo "")
if [ -n "$CSRF_TOKEN" ]; then
  echo "Login successful. CSRF token acquired."
else
  echo "Login successful. No CSRF token (POST requests may fail with 403)."
fi
echo ""

# CSRF header args for POST/PATCH/DELETE requests
CSRF_ARGS=()
if [ -n "$CSRF_TOKEN" ]; then
  CSRF_ARGS=(-H "X-XSRF-TOKEN: $CSRF_TOKEN")
fi

# Helper: GET request
get_test() {
  local label="$1" url="$2" expected="$3"
  HTTP_CODE=$(curl -s -b "$COOKIE_JAR" -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || echo "000")
  if [ "$HTTP_CODE" = "$expected" ]; then log_pass "$label" "$HTTP_CODE"; else log_fail "$label" "$HTTP_CODE (expected $expected)"; fi
}

# Helper: POST/PATCH request with body
write_test() {
  local label="$1" method="$2" url="$3" body="$4" expected="$5"
  HTTP_CODE=$(curl -s -b "$COOKIE_JAR" "${CSRF_ARGS[@]}" -o /dev/null -w '%{http_code}' \
    -X "$method" "$url" -H "Content-Type: application/json" -d "$body" 2>/dev/null || echo "000")
  if [ "$HTTP_CODE" = "$expected" ]; then log_pass "$label" "$HTTP_CODE"; else log_fail "$label" "$HTTP_CODE (expected $expected)"; fi
}

# --- Queue GET endpoints ---
get_test "GET /moderation/queue" "${BASE_URL}/moderation/queue?page=1&pageSize=10" "200"
get_test "GET /moderation/queue/stats" "${BASE_URL}/moderation/queue/stats" "200"
get_test "GET /moderation/queue/{id} (404)" "${BASE_URL}/moderation/queue/nonexistent-id" "404"
get_test "GET /moderation/queue/entity/{type}/{id}" "${BASE_URL}/moderation/queue/entity/ForumPost/test-post-id" "200"

# --- Queue POST/PATCH endpoints ---
write_test "POST /moderation/queue/{id}/claim (404)" "POST" "${BASE_URL}/moderation/queue/nonexistent-id/claim" "" "404"
write_test "POST /moderation/queue/{id}/assign (404)" "POST" "${BASE_URL}/moderation/queue/nonexistent-id/assign" '{"assignedTo":"mod-1"}' "404"
write_test "PATCH /moderation/queue/{id}/unassign (404)" "PATCH" "${BASE_URL}/moderation/queue/nonexistent-id/unassign" "" "404"
write_test "POST /moderation/queue/{id}/action (404)" "POST" "${BASE_URL}/moderation/queue/nonexistent-id/action" '{"action":"DISMISSED"}' "404"
write_test "POST /moderation/queue/batch-action (400)" "POST" "${BASE_URL}/moderation/queue/batch-action" '{"queueIds":[],"action":"DELETED"}' "400"

# --- Report endpoints ---
get_test "GET /moderation/reports" "${BASE_URL}/moderation/reports?page=1&limit=20" "200"
write_test "POST /moderation/reports" "POST" "${BASE_URL}/moderation/reports" '{"entityType":"ForumPost","entityId":"smoke-test-post","category":"SPAM","reason":"Smoke test"}' "200"
get_test "GET /moderation/reports/{id} (404)" "${BASE_URL}/moderation/reports/nonexistent-id" "404"
get_test "GET /moderation/reports/entity/{type}/{id}" "${BASE_URL}/moderation/reports/entity/ForumPost/smoke-test-post" "200"

# --- Appeal endpoints ---
get_test "GET /moderation/appeals" "${BASE_URL}/moderation/appeals?page=1&pageSize=10" "200"
get_test "GET /moderation/appeals/my" "${BASE_URL}/moderation/appeals/my" "200"
get_test "GET /moderation/appeals/{id} (404)" "${BASE_URL}/moderation/appeals/nonexistent-id" "404"
get_test "GET /moderation/appeals/stats" "${BASE_URL}/moderation/appeals/stats" "200"
write_test "POST /moderation/appeals (400)" "POST" "${BASE_URL}/moderation/appeals" '{"reason":"test"}' "400"
write_test "POST /moderation/appeals/{id}/review (404)" "POST" "${BASE_URL}/moderation/appeals/nonexistent-id/review" '{"decision":"APPROVED"}' "404"

# --- Enums ---
get_test "GET /moderation/enums" "${BASE_URL}/moderation/enums" "200"

# --- Results ---
echo "=== Results ==="
for r in "${RESULTS[@]}"; do
  echo -e "$r"
done
echo ""
echo "Total: $((PASS + FAIL)) | Pass: $PASS | Fail: $FAIL"

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
exit 0
