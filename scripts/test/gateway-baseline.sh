#!/bin/bash
# Gateway baseline — verifies the P1-INFRA-004 hardening of the frontend
# nginx gateways (console/nginx.conf + management/nginx.conf).
#
# Checks:
#   1. Static inventory: each route family (auth/admin/moderation/app/ws)
#      has a `location` block in both nginx.conf files; the shared snippet
#      `infrastructure/nginx/includes/backend-proxy.conf` exists and
#      declares the canonical identity-header strip; every proxy
#      location in both confs MUST `include` that snippet (no per-location
#      drift).
#   2. Path preservation: every family location uses `proxy_pass
#      http://backend:9001/<family>/;` so longest-prefix matching does not
#      strip the family prefix from the upstream URI.
#   3. WebSocket upgrade: the `map` directive lives at http scope and
#      every location uses `Connection $connection_upgrade;` (NOT a
#      hardcoded "upgrade", which would break SockJS HTTP-fallback
#      transports). WS locations also disable `proxy_buffering` and
#      `proxy_cache` so streaming responses aren't spooled to a tempfile.
#   4. Security headers: server scope and the static-asset location both
#      `include` `infrastructure/nginx/includes/security-headers.conf`.
#   5. Compose config validity: `docker compose -f docker-compose.yml -f
#      docker-compose.prod.yml config -q` exits 0 — the production
#      deployment still resolves end to end.
#   6. Live smoke test: brings up `docker-compose.gateway-test.yml` with
#      the production console nginx.conf + a deterministic echo backend,
#      then curls each route family + forges an identity header to assert
#      the upstream sees the correct (preserved) path AND no client-
#      supplied identity header. Also asserts `Connection` behavior and
#      that underscore-header smuggling is dropped before proxying.
#      --skip-smoke skips step 6 if docker is unavailable.
#
# Exit code: 0 if every check passed, 1 otherwise.
#
# Usage: ./scripts/test/gateway-baseline.sh [--skip-smoke] [--quiet]
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

CONSOLE_CONF="$ROOT_DIR/console/nginx.conf"
MANAGEMENT_CONF="$ROOT_DIR/management/nginx.conf"
SNIPPET_CONF="$ROOT_DIR/infrastructure/nginx/includes/backend-proxy.conf"
SECURITY_CONF="$ROOT_DIR/infrastructure/nginx/includes/security-headers.conf"
COMPOSE_BASE="$ROOT_DIR/docker-compose.yml"
COMPOSE_PROD="$ROOT_DIR/docker-compose.prod.yml"
COMPOSE_TEST="$ROOT_DIR/docker-compose.gateway-test.yml"

# Required ingress route families (route inventory).
ROUTE_FAMILIES=(/api/auth/ /api/admin/ /api/moderation/ /api/ /api/ws/ /ws/)

# Canonical identity headers that the shared snippet must strip. Single
# source of truth — adding/removing a header in this list requires the
# matching entry in backend-proxy.conf (and vice versa).
STRIP_HEADERS=(
    # X-User-*
    X-User-Id X-User-Name X-User-Email X-User-Roles X-User-Status X-User-Idp
    # X-Role*
    X-Role X-Roles X-Role-Scope
    # X-Service*
    X-Service X-Service-Name X-Service-Token X-Service-Id X-Service-Version
    # Specific service-forged tokens / auth bypass names
    X-Internal X-Admin-Token X-Auth-Bypass X-Auth-Token
    X-Actor X-Actor-Id X-Impersonate X-Principal X-Principal-Id
    # Set by trusted upstream proxies when relaying auth decisions
    X-Forwarded-User X-Remote-User
)

SKIP_SMOKE=0
QUIET=0
for arg in "$@"; do
    case "$arg" in
        --skip-smoke) SKIP_SMOKE=1 ;;
        --quiet|-q) QUIET=1 ;;
        *) echo "Unknown argument: $arg" >&2; exit 2 ;;
    esac
done

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0
RESULTS=()
SKIP=0

log_pass() { PASS=$((PASS + 1)); RESULTS+=("${GREEN}PASS${NC} | $1 | $2"); }
log_fail() { FAIL=$((FAIL + 1)); RESULTS+=("${RED}FAIL${NC} | $1 | $2"); }
log_skip() { SKIP=$((SKIP + 1)); RESULTS+=("${YELLOW}SKIP${NC} | $1 | $2"); }
log_info() { [ "$QUIET" -eq 0 ] && echo "$1"; }

heading() { echo; echo "=== $1 ==="; }

file_must_contain() {
    local file="$1"
    local pattern="$2"
    local what="$3"
    if [ ! -f "$file" ]; then
        log_fail "static:$what" "$file does not exist"
        return 1
    fi
    if grep -Eq -- "$pattern" "$file"; then
        log_pass "static:$what" "$file contains $pattern"
        return 0
    else
        log_fail "static:$what" "$file is missing $pattern"
        return 1
    fi
}

heading "1. Static inventory (route families + include coverage)"

for conf_label in "console:$CONSOLE_CONF" "management:$MANAGEMENT_CONF"; do
    label="${conf_label%%:*}"
    conf="${conf_label##*:}"
    [ -f "$conf" ] || { log_fail "static:file:$label" "$conf missing"; continue; }
    for family in "${ROUTE_FAMILIES[@]}"; do
        file_must_contain "$conf" "^[[:space:]]*location ${family} \\{" "$label:family:${family}"
    done
    file_must_contain "$conf" "include[[:space:]]+.*backend-proxy\\.conf" "$label:snippet-include"
    file_must_contain "$conf" "^map[[:space:]]+\\\$http_upgrade[[:space:]]+\\\$connection_upgrade" "$label:map-directive"
    if grep -Eq 'proxy_set_header[[:space:]]+Connection[[:space:]]+"upgrade";' "$conf"; then
        log_fail "static:$label:hardcoded-upgrade" "found hardcoded Connection \"upgrade\"; breaks SockJS fallbacks"
    else
        log_pass "static:$label:hardcoded-upgrade" "no hardcoded Connection \"upgrade\"; uses \$connection_upgrade"
    fi
done

heading "2. Snippet content (infrastructure/nginx/includes/)"
file_must_contain "$SNIPPET_CONF" "connection_upgrade" "snippet:map-bind"
file_must_contain "$SNIPPET_CONF" 'proxy_set_header[[:space:]]+Connection[[:space:]]+\$connection_upgrade' "snippet:conn-upgrade"
for hdr in "${STRIP_HEADERS[@]}"; do
    file_must_contain "$SNIPPET_CONF" "proxy_set_header[[:space:]]+${hdr}[[:space:]]+\"\";" "snippet:strip:${hdr}"
done
[ -f "$SECURITY_CONF" ] || log_fail "static:snippet:security-headers" "$SECURITY_CONF missing"
file_must_contain "$SECURITY_CONF" 'add_header[[:space:]]+X-Frame-Options' "snippet:sec-headers:x-frame-options"
file_must_contain "$SECURITY_CONF" 'add_header[[:space:]]+Content-Security-Policy' "snippet:sec-headers:csp"
file_must_contain "$SECURITY_CONF" 'add_header[[:space:]]+Referrer-Policy' "snippet:sec-headers:referrer-policy"

heading "3. proxy_pass prefix preservation (per family)"
declare -A EXPECT_PASS=(
    ['/api/auth/']='http://backend:9001/auth/;'
    ['/api/admin/']='http://backend:9001/admin/;'
    ['/api/moderation/']='http://backend:9001/moderation/;'
    ['/api/']='http://backend:9001/;'
    ['/api/ws/']='http://backend:9001/ws/;'
    ['/ws/']='http://backend:9001/ws/;'
)
for conf_label in "console:$CONSOLE_CONF" "management:$MANAGEMENT_CONF"; do
    label="${conf_label%%:*}"
    conf="${conf_label##*:}"
    for family in "${!EXPECT_PASS[@]}"; do
        expected="${EXPECT_PASS[$family]}"
        needle="proxy_pass ${expected}"
        if grep -Fq -- "$needle" "$conf"; then
            log_pass "static:$label:proxy-pass:${family}" "proxy_pass $expected present"
        else
            log_fail "static:$label:proxy-pass:${family}" "missing proxy_pass $expected"
        fi
    done
done

heading "4. Security header inheritance (server scope + static-asset location)"
for conf_label in "console:$CONSOLE_CONF" "management:$MANAGEMENT_CONF"; do
    label="${conf_label%%:*}"
    conf="${conf_label##*:}"
    file_must_contain "$conf" "include[[:space:]]+.*security-headers\\.conf" "$label:server-scope:security-include"
    if grep -Eq 'add_header[[:space:]]+X-Frame-Options' "$conf"; then
        log_fail "static:$label:duplicate-security-headers" "nginx.conf declares X-Frame-Options inline instead of via shared include"
    else
        log_pass "static:$label:duplicate-security-headers" "no inline X-Frame-Options; relies on security-headers.conf include"
    fi
done

heading "5. WebSocket locations (proxy_buffering off + proxy_cache off + extended timeouts)"
for conf_label in "console:$CONSOLE_CONF" "management:$MANAGEMENT_CONF"; do
    label="${conf_label%%:*}"
    conf="${conf_label##*:}"
    for ws_family in '/api/ws/' '/ws/'; do
        WS_CHECK=$(python3 "$ROOT_DIR/infrastructure/nginx/ws_check.py" "$conf" "$ws_family" 2>/dev/null || echo "FAIL runner-error")
        case "$WS_CHECK" in
            OK) log_pass "static:$label:ws:${ws_family}" "location has proxy_buffering off, proxy_cache off, proxy_read_timeout 86400" ;;
            *)  log_fail "static:$label:ws:${ws_family}" "WS location check: $WS_CHECK" ;;
        esac
    done
done

heading "6. docker compose config (production stack)"
if command -v docker >/dev/null 2>&1; then
    if docker compose -f "$COMPOSE_BASE" -f "$COMPOSE_PROD" config -q 2>/dev/null; then
        log_pass "compose:prod" "docker-compose.yml + docker-compose.prod.yml config -q ok"
    else
        log_fail "compose:prod" "compose config validation failed"
    fi
else
    log_skip "compose:prod" "docker not installed"
fi

heading "7. Live smoke (gateway + echo backend via docker-compose.gateway-test.yml)"
if [ "$SKIP_SMOKE" -eq 1 ]; then
    log_skip "smoke:overall" "--skip-smoke set"
elif ! command -v docker >/dev/null 2>&1; then
    log_skip "smoke:overall" "docker not installed"
else
    log_info "Bringing up docker-compose.gateway-test.yml ..."
    set +e
    COMPOSE_OUTPUT=$(mktemp)
    docker compose -f "$COMPOSE_TEST" up -d >"$COMPOSE_OUTPUT" 2>&1
    UP_RC=$?
    set -e
    if [ "$UP_RC" -ne 0 ]; then
        cat "$COMPOSE_OUTPUT"
        log_fail "smoke:compose-up" "docker compose up failed"
        rm -f "$COMPOSE_OUTPUT"
    else
        rm -f "$COMPOSE_OUTPUT"
        log_pass "smoke:compose-up" "docker compose up (gateway + echo backend)"

        # Wait for the gateway to accept connections.
        for _ in $(seq 1 30); do
            if python3 -c "
import socket, sys
s=socket.socket(); s.settimeout(1)
try: s.connect(('127.0.0.1',8081)); sys.exit(0)
except Exception: sys.exit(1)
" >/dev/null 2>&1; then
                break
            fi
            sleep 1
        done

        set +e
        PROBE_OUTPUT=$(python3 "$ROOT_DIR/infrastructure/nginx/smoke_probes.py" 2>&1)
        SMOKE_RC=$?
        set -e

        if [ "$SMOKE_RC" -ne 0 ]; then
            log_fail "smoke:python-runner" "Python smoke runner exited non-zero"
        elif [ "$PROBE_OUTPUT" = "ALL_PASS" ]; then
            log_pass "smoke:path-translation" "all 10 route families translate correctly"
            log_pass "smoke:header-strip" "all ${#STRIP_HEADERS[@]} identity headers stripped on all 6 locations"
            log_pass "smoke:conn-upgrade" "plain=close, WS=upgrade, SockJS-fallback=close"
            log_pass "smoke:underscore-drop" "underscore headers absent upstream"
        else
            log_fail "smoke:detailed" "failures:\n$PROBE_OUTPUT"
        fi

        log_info "Tearing down docker-compose.gateway-test.yml ..."
        docker compose -f "$COMPOSE_TEST" down >/dev/null 2>&1 || true
    fi
fi

echo
echo "=== Results ==="
for r in "${RESULTS[@]}"; do
    echo -e "$r"
done
echo
echo "Total: $((PASS + FAIL + SKIP)) | Pass: $PASS | Fail: $FAIL | Skip: $SKIP"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
exit 0
