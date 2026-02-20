#!/bin/bash

# Get script directory (supports symlinks)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Configuration
CONSOLE_PORT=9002
MANAGEMENT_PORT=9003
BACKEND_PORT=9001
MYSQL_PORT=23306
REDIS_PORT=26379

# Colors
RESET='\033[0m'
GREEN='\033[32m'
RED='\033[31m'
YELLOW='\033[33m'
CYAN='\033[36m'
WHITE='\033[37m'
DIM='\033[2m'
BOLD='\033[1m'
GREEN_BG='\033[42m'
YELLOW_BG='\033[43m'

# Symbols
CHECK="✓"
CROSS="✗"
DOT="•"

# Print banner
print_banner() {
    echo ""
    echo -e "${CYAN}${BOLD}"
    echo "   _   _ _   _ _   _ _   _ ___ ___  ___ ___  ___  "
    echo "  | | | | | | | | | | \\ | |_ _/ _ \\/ __/ __|| _ \\ "
    echo "  | |_| | |_| | | | |  \\| || | (_) | (_| _| |   / "
    echo "   \\___/ \\___/|_| |_|_|\\_|___|\\___/ \\___|___|_|_\\ "
    echo -e "${RESET}"
    echo -e "  ${DIM}Service Status${RESET}"
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
    esac
}

# Print section header
print_section() {
    local title=$1
    echo ""
    echo -e "  ${BOLD}${WHITE}:: ${title}${RESET}"
    echo -e "  ${DIM}────────────────────────────────────────${RESET}"
}

# Port check
is_port_in_use() {
    lsof -i :$1 >/dev/null 2>&1
}

get_pid_on_port() {
    lsof -ti :$1 2>/dev/null | head -1 || echo ""
}

cd "$PROJECT_ROOT"
print_banner

# Check services
print_section "Application Services"

# Console
if is_port_in_use $CONSOLE_PORT; then
    pid=$(get_pid_on_port $CONSOLE_PORT)
    print_status "ok" "Console     running on port ${CONSOLE_PORT} (PID: ${pid})"
else
    print_status "error" "Console     not running"
fi

# Management
if is_port_in_use $MANAGEMENT_PORT; then
    pid=$(get_pid_on_port $MANAGEMENT_PORT)
    print_status "ok" "Management  running on port ${MANAGEMENT_PORT} (PID: ${pid})"
else
    print_status "error" "Management  not running"
fi

# Backend
if is_port_in_use $BACKEND_PORT; then
    pid=$(get_pid_on_port $BACKEND_PORT)
    # Check if backend is responding
    if curl -s -f "http://localhost:$BACKEND_PORT" -o /dev/null 2>&1; then
        print_status "ok" "Backend     running on port ${BACKEND_PORT} (PID: ${pid})"
    else
        print_status "warn" "Backend     port ${BACKEND_PORT} in use but not responding (PID: ${pid})"
    fi
else
    print_status "error" "Backend     not running"
fi

# Docker services
print_section "Docker Services"

# MySQL
if docker ps | grep -q "ulticode-mysql"; then
    print_status "ok" "MySQL       running on port ${MYSQL_PORT}"
else
    if docker ps -a | grep -q "ulticode-mysql"; then
        print_status "warn" "MySQL       container exists but not running"
    else
        print_status "info" "MySQL       container not created"
    fi
fi

# Redis
if docker ps | grep -q "ulticode-redis"; then
    print_status "ok" "Redis       running on port ${REDIS_PORT}"
else
    if docker ps -a | grep -q "ulticode-redis"; then
        print_status "warn" "Redis       container exists but not running"
    else
        print_status "info" "Redis       container not created"
    fi
fi

# Summary
echo ""
RUNNING_COUNT=0
is_port_in_use $BACKEND_PORT && RUNNING_COUNT=$((RUNNING_COUNT + 1))
is_port_in_use $CONSOLE_PORT && RUNNING_COUNT=$((RUNNING_COUNT + 1))
is_port_in_use $MANAGEMENT_PORT && RUNNING_COUNT=$((RUNNING_COUNT + 1))
docker ps | grep -q "ulticode-mysql" && RUNNING_COUNT=$((RUNNING_COUNT + 1))
docker ps | grep -q "ulticode-redis" && RUNNING_COUNT=$((RUNNING_COUNT + 1))

TOTAL_SERVICES=5

echo -e "  ${BOLD}Summary:${RESET} ${RUNNING_COUNT}/${TOTAL_SERVICES} services running"
echo ""

if [ $RUNNING_COUNT -eq $TOTAL_SERVICES ]; then
    echo -e "  ${GREEN_BG}${WHITE} ALL SYSTEMS GO ${RESET}"
    echo ""
    echo -e "  ${BOLD}Quick Access:${RESET}"
    echo -e "    ${CYAN}Console${RESET}     http://localhost:${CONSOLE_PORT}"
    echo -e "    ${CYAN}Management${RESET}  http://localhost:${MANAGEMENT_PORT}"
    echo -e "    ${CYAN}Backend${RESET}     http://localhost:${BACKEND_PORT}"
elif [ $RUNNING_COUNT -eq 0 ]; then
    echo -e "  ${RED}${CROSS}${RESET} ${BOLD}All services stopped${RESET}"
    echo ""
    echo -e "  ${DIM}Start: ./shell/start.sh${RESET}"
else
    echo -e "  ${YELLOW_BG} PARTIAL ${RESET}"
    echo ""
    echo -e "  ${DIM}Start all:  ./shell/start.sh${RESET}"
    echo -e "  ${DIM}Stop all:   ./shell/stop.sh${RESET}"
    echo -e "  ${DIM}Restart:    ./shell/restart.sh${RESET}"
fi

echo ""
