#!/bin/bash

# ============================================================
# UltiCode Stop Script
# Usage: ./stop.sh [-y] [-f] [--skip-docker]
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source common functions
source "$SCRIPT_DIR/common.sh"

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
print_banner "Stopping Services"

cd "$PROJECT_ROOT"

# ============== Confirmation ==============
if [ "$SKIP_CONFIRM" = false ]; then
    log "${B}Services to stop:${R}"
    info "Console (port $CONSOLE_PORT)"
    info "Management (port $MANAGEMENT_PORT)"
    info "Backend (port $BACKEND_PORT)"
    info "Recommend-Web (port $RECOMMEND_WEB_PORT)"
    info "Recommend-Provider (port $RECOMMEND_PROVIDER_DUBBO_PORT)"
    [ "$SKIP_DOCKER" = false ] && { info "MySQL Docker"; info "Redis Docker"; info "Nacos Docker"; }
    echo ""
    read -p "  Confirm? (y/N): " -n 1 -r
    echo ""
    [[ ! $REPLY =~ ^[Yy]$ ]] && { info "Cancelled"; echo ""; exit 0; }
fi

STOPPED=()

# ============== Step 1: Console ==============
log ""
print_section "Console"
stop_port $CONSOLE_PORT "Console" && STOPPED+=("Console")

# ============== Step 2: Management ==============
log ""
print_section "Management"
stop_port $MANAGEMENT_PORT "Management" && STOPPED+=("Management")

# ============== Step 3: Backend (Spring Boot) ==============
log ""
print_section "Backend (Spring Boot)"

if port_used $BACKEND_PORT; then
    step "Stopping Backend..."
    pkill -9 -f "spring-boot:run" 2>/dev/null || true
    pkill -9 -f "backend-spring" 2>/dev/null || true
    pkill -9 -f "ulticode-backend" 2>/dev/null || true
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

# ============== Step 4: Recommendation Services ==============
log ""
print_section "Recommendation"

# Stop Web first (depends on Provider)
stop_port $RECOMMEND_WEB_PORT "Recommend-Web" && STOPPED+=("Recommend-Web")

log ""
stop_port $RECOMMEND_PROVIDER_DUBBO_PORT "Recommend-Provider" && STOPPED+=("Recommend-Provider")

# Kill any lingering Java processes for recommendation
pkill -9 -f "recommend-web" 2>/dev/null || true
pkill -9 -f "recommend-provider" 2>/dev/null || true

# ============== Step 5: Docker Services ==============
log ""
print_section "Docker"

if [ "$SKIP_DOCKER" = true ]; then
    info "Skipping (--skip-docker)"
elif docker ps 2>/dev/null | grep -qE "ulticode-(mysql|redis|nacos)"; then
    step "Stopping containers..."
    # Try docker compose down if compose file exists (preferred method)
    if [ -f "docker-compose.yml" ]; then
        docker compose down 2>/dev/null || true
    fi

    # Fallback: remove any remaining containers by name
    docker rm -f ulticode-mysql ulticode-redis ulticode-nacos 2>/dev/null || true

    ok "Containers stopped"
    STOPPED+=("MySQL" "Redis" "Nacos")
else
    info "No containers running"
fi

# ============== Step 6: Cleanup ==============
log ""
print_section "Cleanup"

# Kill lingering processes
pkill -9 -f "vite" 2>/dev/null || true
pkill -9 -f "pnpm.*dev" 2>/dev/null || true

# Clean log files
rm -f /tmp/ulticode-*.log 2>/dev/null
rm -f nohup.out backend-spring/nohup.out console/nohup.out management/nohup.out 2>/dev/null

ok "Cleanup complete"

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
port_used $RECOMMEND_PROVIDER_DUBBO_PORT && ((RUNNING++)) || true
docker ps 2>/dev/null | grep -q "ulticode-mysql" && ((RUNNING++)) || true
docker ps 2>/dev/null | grep -q "ulticode-redis" && ((RUNNING++)) || true
docker ps 2>/dev/null | grep -q "ulticode-nacos" && ((RUNNING++)) || true

if [ $RUNNING -gt 0 ]; then
    echo -e "  ${Y}${B}! $RUNNING service(s) still running${R}"
    log "${D}Force stop: ./shell/stop.sh -y -f${R}"
else
    echo -e "  ${GREEN_BG}${WHITE} ALL STOPPED ${R}"
fi

echo ""
