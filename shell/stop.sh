#!/bin/bash

# ============================================================
# UltiCode Stop Script - Simple & Reliable
# Usage: ./stop.sh [-y] [-f] [--skip-docker]
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ============== Configuration ==============
CONSOLE_PORT=9002
MANAGEMENT_PORT=9003
BACKEND_PORT=9001
RECOMMEND_WEB_PORT=9004
RECOMMEND_PROVIDER_PORT=9005

# ============== Colors & Symbols ==============
R='\033[0m' G='\033[32m' Y='\033[33m' C='\033[36m' D='\033[2m' B='\033[1m' RED_BG='\033[41m' WHITE='\033[37m'
CHECK="✓" CROSS="✗" ARROW="→"

# ============== Helper Functions ==============
log() { echo -e "  $1"; }
ok() { log "${G}${CHECK}${R} $1"; }
err() { log "${Y}${CROSS}${R} $1"; }
info() { log "${D}•${R} $1"; }
step() { log "${C}${ARROW}${R} $1"; }

port_used() { lsof -i :$1 >/dev/null 2>&1; }
get_pid() { lsof -ti :$1 2>/dev/null || echo ""; }

stop_port() {
    local port=$1 name=$2
    local pid=$(get_pid $port)
    if [ -z "$pid" ]; then
        info "$name not running"
        return 2
    fi

    step "Stopping $name (PID: $pid)..."
    kill -TERM $pid 2>/dev/null || true

    # Wait up to 10 seconds
    local i=0
    while [ $i -lt 10 ] && port_used $port; do
        sleep 1
        ((i++)) || true
    done

    if port_used $port; then
        # Force kill
        kill -KILL $pid 2>/dev/null || true
        sleep 1
        if port_used $port; then
            err "Failed to stop $name"
            return 1
        fi
        ok "$name force stopped"
    else
        ok "$name stopped"
    fi
    return 0
}

# ============== Parse Arguments ==============
SKIP_CONFIRM=false
FORCE=false
SKIP_DOCKER=false

for arg in "$@"; do
    case $arg in
        -y|--yes) SKIP_CONFIRM=true ;;
        -f|--force) FORCE=true ;;
        --skip-docker) SKIP_DOCKER=true ;;
    esac
done

# ============== Banner ==============
echo ""
echo -e "${C}${B}"
echo "   _   _ _   _ _   _ _   _ ___ ___  ___ ___  ___  "
echo "  | | | | | | | | | | \\ | |_ _/ _ \\/ __/ __|| _ \\ "
echo "  | |_| | |_| | | | |  \\| || | (_) | (_| _| |   / "
echo "   \\___/ \\___/|_| |_|_|\\_|___|\\___/ \\___|___|_|_\\ "
echo -e "${R}"
echo -e "  ${D}Stopping Services${R}"
echo ""

cd "$PROJECT_ROOT"

# ============== Confirmation ==============
if [ "$SKIP_CONFIRM" = false ]; then
    log "${B}Services to stop:${R}"
    info "Console (port $CONSOLE_PORT)"
    info "Management (port $MANAGEMENT_PORT)"
    info "Backend (port $BACKEND_PORT)"
    info "Recommend-Web (port $RECOMMEND_WEB_PORT)"
    info "Recommend-Provider (port $RECOMMEND_PROVIDER_PORT)"
    [ "$SKIP_DOCKER" = false ] && { info "MySQL Docker"; info "Redis Docker"; info "Nacos Docker"; }
    echo ""
    read -p "  Confirm? (y/N): " -n 1 -r
    echo ""
    [[ ! $REPLY =~ ^[Yy]$ ]] && { info "Cancelled"; echo ""; exit 0; }
fi

STOPPED=()

# ============== Stop Services ==============
log "${B}:: Console${R} ${D}──────────────────────${R}"
stop_port $CONSOLE_PORT "Console" && STOPPED+=("Console")

log ""
log "${B}:: Management${R} ${D}──────────────────${R}"
stop_port $MANAGEMENT_PORT "Management" && STOPPED+=("Management")

log ""
log "${B}:: Backend${R} ${D}──────────────────────${R}"
if port_used $BACKEND_PORT; then
    step "Stopping Backend..."
    pkill -9 -f "nest start" 2>/dev/null || true
    pkill -9 -f "node.*backend" 2>/dev/null || true
    sleep 2
    if port_used $BACKEND_PORT; then
        err "Backend still running"
    else
        ok "Backend stopped"
        STOPPED+=("Backend")
    fi
else
    info "Backend not running"
fi

# ============== Recommendation Services ==============
log ""
log "${B}:: Recommendation${R} ${D}──────────────────${R}"

# Stop Web first (depends on Provider)
stop_port $RECOMMEND_WEB_PORT "Recommend-Web" && STOPPED+=("Recommend-Web")

log ""
stop_port $RECOMMEND_PROVIDER_PORT "Recommend-Provider" && STOPPED+=("Recommend-Provider")

# Kill any lingering Java processes for recommendation
pkill -9 -f "recommend-web" 2>/dev/null || true
pkill -9 -f "recommend-provider" 2>/dev/null || true

# ============== Docker Services ==============
log ""
log "${B}:: Docker${R} ${D}────────────────────────${R}"

if [ "$SKIP_DOCKER" = true ]; then
    info "Skipping (--skip-docker)"
elif docker ps 2>/dev/null | grep -qE "ulticode-(mysql|redis|nacos)"; then
    step "Stopping containers..."
    cd backend && docker compose down && cd ..
    ok "Containers stopped"
    STOPPED+=("MySQL" "Redis" "Nacos")
else
    info "No containers running"
fi

# ============== Cleanup ==============
log ""
log "${B}:: Cleanup${R} ${D}──────────────────────${R}"

# Kill lingering processes
pkill -9 -f "vite" 2>/dev/null || true
pkill -9 -f "pnpm.*dev" 2>/dev/null || true

# Clean log files
rm -f /tmp/ulticode-*.log 2>/dev/null
rm -f nohup.out backend/nohup.out console/nohup.out management/nohup.out 2>/dev/null

ok "Cleaned up"

# ============== Summary ==============
echo ""
if [ ${#STOPPED[@]} -gt 0 ]; then
    echo -e "  ${RED_BG}${WHITE} STOPPED ${R}"
    for svc in "${STOPPED[@]}"; do
        info "$svc"
    done
else
    info "No services were running"
fi

# ============== Final Check ==============
echo ""
RUNNING=0
port_used $BACKEND_PORT && ((RUNNING++)) || true
port_used $CONSOLE_PORT && ((RUNNING++)) || true
port_used $MANAGEMENT_PORT && ((RUNNING++)) || true
port_used $RECOMMEND_WEB_PORT && ((RUNNING++)) || true
port_used $RECOMMEND_PROVIDER_PORT && ((RUNNING++)) || true
docker ps 2>/dev/null | grep -q "ulticode-mysql" && ((RUNNING++)) || true
docker ps 2>/dev/null | grep -q "ulticode-redis" && ((RUNNING++)) || true
docker ps 2>/dev/null | grep -q "ulticode-nacos" && ((RUNNING++)) || true

if [ $RUNNING -gt 0 ]; then
    echo -e "  ${Y}${B}! $RUNNING service(s) still running${R}"
    log "${D}Force stop: ./shell/stop.sh -y -f${R}"
else
    echo -e "  ${G}${B}✓ All services stopped${R}"
fi

echo ""
