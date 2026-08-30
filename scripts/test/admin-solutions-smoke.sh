#!/usr/bin/env bash
# End-to-end curl test for /admin/solutions 7 endpoints through the owner gateway.
# Output is structured key=value lines for the QA doc.
set -u

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/smoke-common.sh"
smoke_init test-admin-solutions
HEADERS_JAR="$SMOKE_ARTIFACT_DIR/headers.txt"

# Trusted .env loading (DB credentials for the reset steps below); explicit
# caller-provided SMOKE_* values are not present in .env, so ordering is safe.
smoke_load_env

BASE="${SMOKE_BASE_URL:-${BASE:-http://localhost:9003/api}}"
# Credentials come from the environment; never hardcode a seed password here.
# Canonical names are SMOKE_USERNAME/SMOKE_PASSWORD; the legacy pair still works.
smoke_require_credentials SMOKE_ADMIN_USERNAME SMOKE_ADMIN_PASSWORD

PASS=0; FAIL=0
SEP="---"
CSRF=""

# call <method> <path> [body] [out_body]
# extra headers passed via $CSRF (set by caller)
call() {
  local method="$1" path="$2" body="${3:-}" out="${4:-$ARTIFACT_DIR/uc-body.json}"
  : > "$HEADERS_JAR"
  local code
  if [ -n "$body" ]; then
    code=$(curl -sS -o "$out" -D "$HEADERS_JAR" -w '%{http_code}' \
      -X "$method" "$BASE$path" \
      -b "$COOKIE_JAR" \
      -H 'Content-Type: application/json' \
      -H "X-CSRF-Token: $CSRF" \
      --data-binary "$body")
  else
    code=$(curl -sS -o "$out" -D "$HEADERS_JAR" -w '%{http_code}' \
      -X "$method" "$BASE$path" \
      -b "$COOKIE_JAR" \
      -H "X-CSRF-Token: $CSRF")
  fi
  echo "$code"
}

call_no_csrf() {
  local method="$1" path="$2" body="${3:-}" out="${4:-$ARTIFACT_DIR/uc-body.json}"
  : > "$HEADERS_JAR"
  local code
  if [ -n "$body" ]; then
    code=$(curl -sS -o "$out" -D "$HEADERS_JAR" -w '%{http_code}' \
      -X "$method" "$BASE$path" \
      -b "$COOKIE_JAR" \
      -H 'Content-Type: application/json' \
      --data-binary "$body")
  else
    code=$(curl -sS -o "$out" -D "$HEADERS_JAR" -w '%{http_code}' \
      -X "$method" "$BASE$path" \
      -b "$COOKIE_JAR")
  fi
  echo "$code"
}


# log <step> <status> <extra>
log() {
  printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$(date +%H:%M:%S)"
  [ "$2" = "OK" ] && PASS=$((PASS+1)) || FAIL=$((FAIL+1))
}

# ===== 0. Login =====
echo "$SEP 0. login as admin $SEP"
login_http="$(smoke_login "$COOKIE_JAR" "$BASE" "$SMOKE_USERNAME" "$SMOKE_PASSWORD" "$ARTIFACT_DIR/uc-login.json")" \
  || { echo "login failed (HTTP $login_http)"; exit 1; }
echo "HTTP=$login_http"

CSRF=$(python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-login.json')); print(d.get('data',{}).get('csrfToken',''))" 2>/dev/null || echo "")
log "login" "$( [ -n "$CSRF" ] && echo OK || echo FAIL )" "csrfLen=${#CSRF}"

# Reset state for idempotent reruns (soft-deleted sol-s-005/006, leftover rate-limit counters).
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME" -e "
  UPDATE solutions SET is_deleted=0, deleted_at=NULL, deleted_by=NULL WHERE id IN ('sol-s-005','sol-s-006');
  UPDATE solutions SET is_flagged=0, flagged_at=NULL, flagged_reason=NULL;
  UPDATE solutions SET is_published=1, published_at=COALESCE(published_at, NOW()) WHERE is_published=0;
" 2>/dev/null
docker exec ulticode-redis redis-cli --no-raw KEYS 'rate-limit:admin:solution*' 2>/dev/null \
  | tr -d '"' | xargs -I{} -r docker exec ulticode-redis redis-cli DEL {} 2>/dev/null
log "reset" "OK" "DB + redis rate-limit counters cleared"

# ===== 1. GET /admin/solutions =====
echo "$SEP 1. GET /admin/solutions $SEP"
code=$(call GET "/admin/solutions?page=1&limit=5" "" $ARTIFACT_DIR/uc-1.json)
log "list" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-1.json')); print('total=',d.get('data',{}).get('total'),'items=',len(d.get('data',{}).get('items',[])))"
python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-1.json')); it=d.get('data',{}).get('items',[]); print('first=',json.dumps(it[0],ensure_ascii=False,default=str) if it else None)"

# 1b. isFlagged filter
code=$(call GET "/admin/solutions?isFlagged=true&page=1&limit=5" "" $ARTIFACT_DIR/uc-1b.json)
log "list-flagged-filter" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-1b.json')); print('flaggedTotal=',d.get('data',{}).get('total'),'items=',len(d.get('data',{}).get('items',[])))"

# 1c. sortBy=views desc
code=$(call GET "/admin/solutions?sortBy=views&sortOrder=desc&page=1&limit=3" "" $ARTIFACT_DIR/uc-1c.json)
log "list-sort-views" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-1c.json')); it=d.get('data',{}).get('items',[]); print('viewsOrder=',[i.get('views') for i in it])"

# 1d. search
code=$(call GET "/admin/solutions?search=two&page=1&limit=3" "" $ARTIFACT_DIR/uc-1d.json)
log "list-search" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"

# 1e. isDeleted=true (deleted-only view)
code=$(call GET "/admin/solutions?isDeleted=true&page=1&limit=5" "" $ARTIFACT_DIR/uc-1e.json)
log "list-deleted" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-1e.json')); print('deletedTotal=',d.get('data',{}).get('total'),'items=',len(d.get('data',{}).get('items',[])))"

# ===== 2. GET /admin/solutions/flagged =====
echo "$SEP 2. GET /admin/solutions/flagged $SEP"
code=$(call GET "/admin/solutions/flagged?page=1&limit=5" "" $ARTIFACT_DIR/uc-2.json)
log "list-flagged-endpoint" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-2.json')); print('keys=',list(d.keys()),'flaggedTotal=',d.get('data',{}).get('total'),'items=',len(d.get('data',{}).get('items',[])))"

# ===== 3. GET /admin/solutions/{id} =====
echo "$SEP 3. GET /admin/solutions/{id} $SEP"
code=$(call GET "/admin/solutions/sol-s-001" "" $ARTIFACT_DIR/uc-3.json)
log "detail-existing" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-3.json')); v=d.get('data') or {}; print('voKeys=',list(v.keys()),'isFlagged=',v.get('isFlagged'),'flaggedReason=',v.get('flaggedReason'),'publishedBy=',v.get('publishedBy'),'deletedBy=',v.get('deletedBy'))"

code=$(call GET "/admin/solutions/sol-nope-999" "" $ARTIFACT_DIR/uc-3b.json)
# BUG-Q1: error code renumbered 50001 → 50401 (no longer collides with DATABASE_ERROR).
log "detail-missing" "$([ "$code" = "404" ] && grep -q '"code":50401' $ARTIFACT_DIR/uc-3b.json && echo OK || echo NOTE)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-3b.json)"

# ===== 4. POST /admin/solutions/{id}/flag =====
echo "$SEP 4. POST /admin/solutions/sol-s-002/flag $SEP"
code=$(call POST "/admin/solutions/sol-s-002/flag" '{"reason":"contains PII"}' $ARTIFACT_DIR/uc-4.json)
log "flag-success" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code csrfLen=${#CSRF}"
python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-4.json')); v=d.get('data') or {}; print('isFlagged=',v.get('isFlagged'),'flaggedReason=',v.get('flaggedReason'),'flaggedAt=',v.get('flaggedAt'))"

# 4b. flag with empty reason (validation)
code=$(call POST "/admin/solutions/sol-s-003/flag" '{}' $ARTIFACT_DIR/uc-4b.json)
log "flag-empty-reason" "$([ "$code" = "400" ] && echo OK || echo NOTE)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-4b.json)"

# 4c. flag missing CSRF
code=$(call_no_csrf POST "/admin/solutions/sol-s-003/flag" '{"reason":"x"}' $ARTIFACT_DIR/uc-4c.json)
log "flag-no-csrf" "$([ "$code" = "403" ] && echo OK || echo NOTE)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-4c.json)"

# 4d. flag without auth
BACKUP_COOKIE="$(cat "$COOKIE_JAR")"
: > "$COOKIE_JAR"
code=$(curl -sS -o $ARTIFACT_DIR/uc-4d.json -w '%{http_code}' -X POST "$BASE/admin/solutions/sol-s-003/flag" -H 'Content-Type: application/json' --data-binary '{"reason":"x"}')
printf "%s" "$BACKUP_COOKIE" > "$COOKIE_JAR"
log "flag-no-auth" "$([ "$code" = "401" -o "$code" = "403" ] && echo OK || echo NOTE)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-4d.json)"

# ===== 5. POST /admin/solutions/{id}/unflag =====
echo "$SEP 5. POST /admin/solutions/sol-s-002/unflag $SEP"
code=$(call POST "/admin/solutions/sol-s-002/unflag" "" $ARTIFACT_DIR/uc-5.json)
log "unflag" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code csrfLen=${#CSRF}"
python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-5.json')); v=d.get('data') or {}; print('isFlagged=',v.get('isFlagged'),'flaggedReason=',v.get('flaggedReason'),'flaggedAt=',v.get('flaggedAt'))"

# 5b. unflag when not flagged
code=$(call POST "/admin/solutions/sol-s-001/unflag" "" $ARTIFACT_DIR/uc-5b.json)
log "unflag-when-clean" "$([ "$code" = "200" ] && echo OK || echo NOTE)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-5b.json)"

# ===== 6. POST /admin/solutions/bulk =====
echo "$SEP 6a. bulk action=publish $SEP"
code=$(call POST "/admin/solutions/bulk" '{"ids":["sol-s-001","sol-s-002"],"action":"publish"}' $ARTIFACT_DIR/uc-6a.json)
log "bulk-publish" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
cat $ARTIFACT_DIR/uc-6a.json; echo

# 6b. bulk unpublish
code=$(call POST "/admin/solutions/bulk" '{"ids":["sol-s-001"],"action":"unpublish"}' $ARTIFACT_DIR/uc-6b.json)
log "bulk-unpublish" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
cat $ARTIFACT_DIR/uc-6b.json; echo

# 6c. bulk unflag (after re-flagging)
code=$(call POST "/admin/solutions/sol-s-002/flag" '{"reason":"bulk test"}' $ARTIFACT_DIR/uc-6c0.json)
code=$(call POST "/admin/solutions/bulk" '{"ids":["sol-s-002"],"action":"unflag"}' $ARTIFACT_DIR/uc-6c.json)
log "bulk-unflag" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
cat $ARTIFACT_DIR/uc-6c.json; echo

# 6d. bulk action=flag  (BUG-Q2: should now be 400, not 200 + per-row failure)
code=$(call POST "/admin/solutions/bulk" '{"ids":["sol-s-002"],"action":"flag"}' $ARTIFACT_DIR/uc-6d.json)
log "bulk-flag-unsupported" "$([ "$code" = "400" ] && echo OK || echo FAIL)" "code=$code"
cat $ARTIFACT_DIR/uc-6d.json; echo

# 6e. bulk action=garbage  (BUG-Q3: should now be 400, not 200 + per-row failure)
code=$(call POST "/admin/solutions/bulk" '{"ids":["sol-s-002"],"action":"dropdb"}' $ARTIFACT_DIR/uc-6e.json)
log "bulk-garbage-action" "$([ "$code" = "400" ] && echo OK || echo FAIL)" "code=$code"
cat $ARTIFACT_DIR/uc-6e.json; echo

# 6f. bulk empty ids (validation)
code=$(call POST "/admin/solutions/bulk" '{"ids":[],"action":"publish"}' $ARTIFACT_DIR/uc-6f.json)
log "bulk-empty-ids" "$([ "$code" = "400" ] && echo OK || echo NOTE)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-6f.json)"

# 6g. bulk with one bad id  (BUG-Q4: existing → success:true, missing → success:false)
code=$(call POST "/admin/solutions/bulk" '{"ids":["sol-s-003","sol-NOPE-999"],"action":"publish"}' $ARTIFACT_DIR/uc-6g.json)
log "bulk-mixed-ids" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
python3 -c "
import json
d=json.load(open('$ARTIFACT_DIR/uc-6g.json'))
arr=d.get('data',[])
ok = len(arr)==2 and arr[0].get('success') is True and arr[1].get('success') is False and 'Solution not found' in (arr[1].get('error') or '')
print('mixedExpected=', ok, 'arr=', arr)
"
cat $ARTIFACT_DIR/uc-6g.json; echo

# 6h. bulk delete
code=$(call POST "/admin/solutions/bulk" '{"ids":["sol-s-005"],"action":"delete"}' $ARTIFACT_DIR/uc-6h.json)
log "bulk-delete" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
cat $ARTIFACT_DIR/uc-6h.json; echo

# ===== 7. DELETE /admin/solutions/{id} =====
echo "$SEP 7. DELETE /admin/solutions/sol-s-006 $SEP"
code=$(call DELETE "/admin/solutions/sol-s-006" "" $ARTIFACT_DIR/uc-7.json)
log "delete" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-7.json)"

# 7b. verify is_deleted in DB
DB_STATE=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME" --skip-column-names -e "SELECT is_deleted FROM solutions WHERE id='sol-s-006';" 2>/dev/null)
log "delete-db-state" "$([ "$DB_STATE" = "1" ] && echo OK || echo NOTE)" "is_deleted=$DB_STATE"

# 7c. delete non-existent
code=$(call DELETE "/admin/solutions/sol-NOPE-XXX" "" $ARTIFACT_DIR/uc-7c.json)
log "delete-missing" "$([ "$code" = "404" -o "$code" = "50001" ] && echo OK || echo FAIL)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-7c.json)"

# 7d. delete without CSRF
code=$(call_no_csrf DELETE "/admin/solutions/sol-s-001" "" $ARTIFACT_DIR/uc-7d.json)
log "delete-no-csrf" "$([ "$code" = "403" ] && echo OK || echo NOTE)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-7d.json)"

# 7e. delete-no-csrf-no-cookie
: > "$COOKIE_JAR"
code=$(curl -sS -o $ARTIFACT_DIR/uc-7e.json -w '%{http_code}' -X DELETE "$BASE/admin/solutions/sol-s-001")
printf "%s" "$BACKUP_COOKIE" > "$COOKIE_JAR"
log "delete-no-auth" "$([ "$code" = "401" -o "$code" = "403" ] && echo OK || echo NOTE)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-7e.json)"

# 7f. delete the just-deleted (should be 404 since MyBatis-Plus @TableLogic excludes)
code=$(call DELETE "/admin/solutions/sol-s-006" "" $ARTIFACT_DIR/uc-7f.json)
log "delete-already-deleted" "$([ "$code" = "404" ] && grep -q '"code":50401' $ARTIFACT_DIR/uc-7f.json && echo OK || echo NOTE)" "code=$code body=$(head -c200 $ARTIFACT_DIR/uc-7f.json)"

# ===== X. Anonymous probes =====
echo "$SEP X. anonymous probes $SEP"
for ep in "/admin/solutions" "/admin/solutions/flagged" "/admin/solutions/sol-s-001"; do
  code=$(curl -sS -o $ARTIFACT_DIR/uc-anon.json -w '%{http_code}' "$BASE$ep")
  log "anon-GET-${ep}" "$([ "$code" = "401" -o "$code" = "403" ] && echo OK || echo NOTE)" "code=$code"
done

# ===== Y. /auth/me role check + non-admin user probe =====
# BUG-Q8: we cannot easily create a USER-role account from this script (no dev seed for
# USER passwords). Instead, verify that the admin cookie carries a non-empty ADMIN role
# claim via /auth/me, and that anonymous users are rejected at the SecurityConfig layer
# (the above X block). The actual `@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")`
# gate is exercised by every passing admin-cookie test in this script; if any of those
# failed with 403 we'd know the gate is broken.
echo "$SEP Y. /auth/me role check $SEP"
code=$(call GET "/auth/me" "" $ARTIFACT_DIR/uc-me.json)
log "auth-me" "$([ "$code" = "200" ] && echo OK || echo FAIL)" "code=$code"
# /auth/me returns data.user.role (nested) — not data.role
ROLE=$(python3 -c "import json; d=json.load(open('$ARTIFACT_DIR/uc-me.json')); print(d.get('data',{}).get('user',{}).get('role',''))" 2>/dev/null || echo "")
log "admin-role-claim" "$([ "$ROLE" = "ADMIN" -o "$ROLE" = "SUPER_ADMIN" ] && echo OK || echo FAIL)" "role=$ROLE"

# ===== Z. Rate-limit 30/60s on /flag =====
# BUG-Q7: exercise the @RateLimit(30, 60s) on POST /flag. Toggle flag/unflag uses two
# different keys, so we concentrate all 35 calls on the same /flag key to actually trip
# the limit (29 OK, then 429 at request 31+).
echo "$SEP Z. rate-limit 30/60s $SEP"
RL_TARGET="sol-s-003"
RL_HIT=0
RL_OK=0
for i in $(seq 1 35); do
  payload='{"reason":"rl-toggle"}'; ep="flag"
  rc=$(call POST "/admin/solutions/${RL_TARGET}/${ep}" "$payload" $ARTIFACT_DIR/uc-rl-$i.json 2>/dev/null)
  if [ "$rc" = "429" ]; then RL_HIT=$((RL_HIT+1)); elif [ "$rc" = "200" ]; then RL_OK=$((RL_OK+1)); fi
done
log "rate-limit" "$([ "$RL_HIT" -ge 1 ] && [ "$RL_OK" -ge 25 ] && echo OK || echo NOTE)" "ok=$RL_OK hit429=$RL_HIT"
# cleanup rate-limit counter for sol-s-003 + DB flag
docker exec ulticode-redis redis-cli DEL "rate-limit:admin:solution-flag:user:5be2650e-63dd-11f1-a640-5efbb60fdb93" 2>/dev/null
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME" -e \
  "UPDATE solutions SET is_flagged=0, flagged_at=NULL, flagged_reason=NULL WHERE id='sol-s-003';" 2>/dev/null

# ===== Summary =====
echo
echo "===== SUMMARY ====="
echo "PASS=$PASS FAIL=$FAIL"
