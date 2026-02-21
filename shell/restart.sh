#!/bin/bash

# Get script directory (supports symlinks)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Configuration
CONSOLE_PORT=9002
MANAGEMENT_PORT=9003
BACKEND_PORT=9001

# Colors
RESET='\033[0m'
GREEN='\033[32m'
RED='\033[31m'
YELLOW='\033[33m'
CYAN='\033[36m'
WHITE='\033[37m'
DIM='\033[2m'
BOLD='\033[1m'
BLUE_BG='\033[44m'

# Symbols
CHECK="✓"
CROSS="✗"
ARROW="→"
DOT="•"
SPINNER=('⠋' '⠙' '⠹' '⠸' '⠼' '⠴' '⠦' '⠧' '⠇' '⠏')

# Get spinner frame
get_spinner() {
    local frame=$((${1} % 10))
    printf "%s" "${SPINNER[$frame]}"
}

# Print banner
print_banner() {
    echo ""
    echo -e "${CYAN}${BOLD}"
    echo "   _   _ _   _ _   _ _   _ ___ ___  ___ ___  ___  "
    echo "  | | | | | | | | | | \\ | |_ _/ _ \\/ __/ __|| _ \\ "
    echo "  | |_| | |_| | | | |  \\| || | (_) | (_| _| |   / "
    echo "   \\___/ \\___/|_| |_|_|\\_|___|\\___/ \\___|___|_|_\\ "
    echo -e "${RESET}"
    echo -e "  ${DIM}Restarting Services${RESET}"
    echo ""
}

# Print status line
print_status() {
    local status=$1
    local message=$2
    case $status in
        "ok")    echo -e "  ${GREEN}${CHECK}${RESET} ${message}" ;;
        "error") echo -e "  ${RED}${CROSS}${RESET} ${message}" ;;
        "warn")  echo -e "  ${YELLOW}!${RESET} ${message}" ;;
        "info")  echo -e "  ${DIM}${DOT}${RESET} ${message}" ;;
        "step")  echo -e "  ${CYAN}${ARROW}${RESET} ${message}" ;;
    esac
}

# Print section header
print_section() {
    local title=$1
    echo ""
    echo -e "  ${BOLD}${WHITE}:: ${title}${RESET}"
    echo -e "  ${DIM}────────────────────────────────────────${RESET}"
}

# Print restart box
print_restart_box() {
    echo ""
    echo -e "  ${BLUE_BG}${WHITE} RESTARTED ${RESET}"
    echo ""
    echo -e "  ${GREEN}${CHECK}${RESET} ${BOLD}All services restarted successfully${RESET}"
}

# Port function
is_port_in_use() {
    lsof -i :$1 >/dev/null 2>&1
}

cd "$PROJECT_ROOT"
print_banner

# Parse arguments
SKIP_DOCKER=false
FORCE=false

for arg in "$@"; do
    case $arg in
        --skip-docker) SKIP_DOCKER=true; shift ;;
        -f|--force) FORCE=true; shift ;;
    esac
done

# Build stop args
STOP_ARGS="-y"
if [ "$SKIP_DOCKER" = true ]; then
    STOP_ARGS="$STOP_ARGS --skip-docker"
fi

# Build start args
START_ARGS=""
if [ "$SKIP_DOCKER" = true ]; then
    START_ARGS="$START_ARGS --skip-docker"
fi

# Step 1: Stop services
print_section "Stopping Services"
print_status "step" "Executing stop script..."
echo ""

"$SCRIPT_DIR/stop.sh" $STOP_ARGS

echo ""
print_status "ok" "Services stopped"

# Step 2: Wait
print_section "Waiting for Termination"
printf "  "
for i in {1..10}; do
    RUNNING=0
    is_port_in_use $BACKEND_PORT && RUNNING=$((RUNNING + 1))
    is_port_in_use $CONSOLE_PORT && RUNNING=$((RUNNING + 1))
    is_port_in_use $MANAGEMENT_PORT && RUNNING=$((RUNNING + 1))

    if [ $RUNNING -eq 0 ]; then
        echo -e "\r  ${GREEN}${CHECK}${RESET} All processes terminated       "
        break
    fi

    printf "\r  %s Waiting for processes... (%d/10)" "$(get_spinner $i)" "$i"
    sleep 1
done

if is_port_in_use $BACKEND_PORT || is_port_in_use $CONSOLE_PORT || is_port_in_use $MANAGEMENT_PORT; then
    echo ""
    print_status "warn" "Some processes not fully stopped"
    echo ""
    read -p "  Continue startup? (y/N): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_status "error" "Restart cancelled"
        exit 1
    fi
fi

# Step 3: Cleanup
print_section "Cleanup"
print_status "step" "Killing residual processes..."
pkill -9 -f "nest start" 2>/dev/null || true
pkill -9 -f "node.*backend" 2>/dev/null || true
pkill -9 -f "vite" 2>/dev/null || true
pkill -9 -f "pnpm.*dev" 2>/dev/null || true

print_status "step" "Cleaning log files..."
rm -f /tmp/ulticode-*.log 2>/dev/null || true
rm -f nohup.out backend/nohup.out console/nohup.out management/nohup.out 2>/dev/null || true
print_status "ok" "Cleanup complete"

# Step 4: Start
print_section "Starting Services"
if [ "$SKIP_DOCKER" = true ]; then
    print_status "info" "Skipping Docker restart"
fi

print_status "step" "Executing start script..."
echo ""

"$SCRIPT_DIR/start.sh" $START_ARGS

# Summary
echo ""
print_restart_box
echo ""
