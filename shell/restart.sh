#!/bin/bash

# ============================================================
# UltiCode Restart Script
# Usage: ./restart.sh [-y] [--skip-docker] [--skip-install]
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source common functions
source "$SCRIPT_DIR/common.sh"

# ============== Parse Arguments ==============
SKIP_CONFIRM=false
SKIP_DOCKER=false
SKIP_INSTALL=false

for arg in "$@"; do
    case $arg in
        -y|--yes) SKIP_CONFIRM=true ;;
        --skip-docker) SKIP_DOCKER=true ;;
        --skip-install) SKIP_INSTALL=true ;;
    esac
done

# Build args for sub-scripts
STOP_ARGS=""
START_ARGS=""
[ "$SKIP_CONFIRM" = true ] && STOP_ARGS="$STOP_ARGS -y"
[ "$SKIP_DOCKER" = true ] && { STOP_ARGS="$STOP_ARGS --skip-docker"; START_ARGS="$START_ARGS --skip-docker"; }
[ "$SKIP_INSTALL" = true ] && START_ARGS="$START_ARGS --skip-install"

# ============== Banner ==============
print_banner "Restarting Services"

cd "$PROJECT_ROOT"

# ============== Step 1: Stop ==============
print_section "Stop Services"
step "Running stop script..."
echo ""

"$SCRIPT_DIR/stop.sh" $STOP_ARGS

# ============== Step 2: Wait ==============
log ""
print_section "Wait for Processes"

# Check if all processes have stopped (returns 0 = all stopped)
all_ports_free() {
    ! port_used $BACKEND_PORT && ! port_used $CONSOLE_PORT && ! port_used $MANAGEMENT_PORT && ! port_used $RECOMMEND_WEB_PORT && ! port_used $RECOMMEND_PROVIDER_DUBBO_PORT
}

spin_wait "Processes terminating" "all_ports_free" $PROCESS_WAIT_TIMEOUT || true

# ============== Step 3: Cleanup ==============
log ""
print_section "Cleanup"

pkill -9 -f "spring-boot:run" 2>/dev/null || true
pkill -9 -f "backend-spring" 2>/dev/null || true
pkill -9 -f "ulticode-backend" 2>/dev/null || true
pkill -9 -f "vite" 2>/dev/null || true
pkill -9 -f "pnpm.*dev" 2>/dev/null || true
pkill -9 -f "recommend-web" 2>/dev/null || true
pkill -9 -f "recommend-provider" 2>/dev/null || true

rm -f /tmp/ulticode-*.log 2>/dev/null
rm -f nohup.out backend-spring/nohup.out console/nohup.out management/nohup.out 2>/dev/null

ok "Cleanup complete"

# ============== Step 4: Start ==============
log ""
print_section "Start Services"
[ "$SKIP_DOCKER" = true ] && info "Docker will not be restarted"
step "Running start script..."
echo ""

"$SCRIPT_DIR/start.sh" $START_ARGS

# ============== Summary ==============
echo ""
echo -e "  ${BLUE_BG}${WHITE} RESTARTED ${R}"
echo ""
ok "All services restarted"
echo ""
