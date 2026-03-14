#!/bin/bash

# ============================================================
# UltiCode Restart Script - Simple & Reliable
# Usage: ./restart.sh [-y] [--skip-docker]
# ============================================================

# Don't use set -e - we handle errors explicitly for better reliability

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ============== Configuration ==============
CONSOLE_PORT=9002
MANAGEMENT_PORT=9003
BACKEND_PORT=9001

# ============== Colors & Symbols ==============
R='\033[0m' G='\033[32m' Y='\033[33m' C='\033[36m' D='\033[2m' B='\033[1m' BLUE_BG='\033[44m' WHITE='\033[37m'
CHECK="✓" CROSS="✗" ARROW="→"

# ============== Helper Functions ==============
log() { echo -e "  $1"; }
ok() { log "${G}${CHECK}${R} $1"; }
info() { log "${D}•${R} $1"; }
step() { log "${C}${ARROW}${R} $1"; }

port_used() { lsof -i :$1 >/dev/null 2>&1; }

spin_wait_stop() {
    local msg=$1 timeout=$2
    local i=0 spinner='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
    printf "  ${C}${ARROW}${R} ${msg}..."
    while [ $i -lt $timeout ]; do
        if ! port_used $BACKEND_PORT && ! port_used $CONSOLE_PORT && ! port_used $MANAGEMENT_PORT; then
            printf "\r  ${G}${CHECK}${R} ${msg}    \n"
            return 0
        fi
        printf "\r  ${spinner:$((i % 10)):1} ${msg}... ($((i+1))s/${timeout}s)"
        sleep 1
        ((i++)) || true
    done
    printf "\r  ${Y}!${R} Some processes still running    \n"
    return 1
}

# ============== Parse Arguments ==============
SKIP_CONFIRM=false
SKIP_DOCKER=false

for arg in "$@"; do
    case $arg in
        -y|--yes) SKIP_CONFIRM=true ;;
        --skip-docker) SKIP_DOCKER=true ;;
    esac
done

# Build args for sub-scripts
STOP_ARGS=""
START_ARGS=""
[ "$SKIP_CONFIRM" = true ] && STOP_ARGS="$STOP_ARGS -y"
[ "$SKIP_DOCKER" = true ] && { STOP_ARGS="$STOP_ARGS --skip-docker"; START_ARGS="$START_ARGS --skip-docker"; }

# ============== Banner ==============
echo ""
echo -e "${C}${B}"
echo "   _   _ _   _ _   _ _   _ ___ ___  ___ ___  ___  "
echo "  | | | | | | | | | | \\ | |_ _/ _ \\/ __/ __|| _ \\ "
echo "  | |_| | |_| | | | |  \\| || | (_) | (_| _| |   / "
echo "   \\___/ \\___/|_| |_|_|\\_|___|\\___/ \\___|___|_|_\\ "
echo -e "${R}"
echo -e "  ${D}Restarting Services${R}"
echo ""

cd "$PROJECT_ROOT"

# ============== Step 1: Stop ==============
log "${B}:: Stopping Services${R} ${D}──────────────${R}"
step "Running stop script..."
echo ""

"$SCRIPT_DIR/stop.sh" $STOP_ARGS

echo ""
ok "Services stopped"

# ============== Step 2: Wait ==============
log ""
log "${B}:: Waiting${R} ${D}────────────────────────${R}"

spin_wait_stop "Processes terminating" 10

# ============== Step 3: Cleanup ==============
log ""
log "${B}:: Cleanup${R} ${D}──────────────────────${R}"

pkill -9 -f "nest start" 2>/dev/null || true
pkill -9 -f "node.*backend" 2>/dev/null || true
pkill -9 -f "vite" 2>/dev/null || true
pkill -9 -f "pnpm.*dev" 2>/dev/null || true

rm -f /tmp/ulticode-*.log 2>/dev/null
rm -f nohup.out backend/nohup.out console/nohup.out management/nohup.out 2>/dev/null

ok "Cleanup complete"

# ============== Step 4: Start ==============
log ""
log "${B}:: Starting Services${R} ${D}─────────────${R}"
[ "$SKIP_DOCKER" = true ] && info "Skipping Docker restart"
step "Running start script..."
echo ""

"$SCRIPT_DIR/start.sh" $START_ARGS

# ============== Summary ==============
echo ""
echo -e "  ${BLUE_BG}${WHITE} RESTARTED ${R}"
echo ""
ok "All services restarted"
echo ""
