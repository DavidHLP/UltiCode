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
RED_BG='\033[41m'
YELLOW_BG='\033[43m'

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
    echo -e "  ${DIM}Stopping Services${RESET}"
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

# Print stopped box
print_stopped_box() {
    echo ""
    echo -e "  ${RED_BG}${WHITE} STOPPED ${RESET}"
    echo ""
    for svc in "$@"; do
        echo -e "  ${DIM}${DOT}${RESET} ${svc}"
    done
}

# Port functions
is_port_in_use() {
    lsof -i :$1 >/dev/null 2>&1
}

get_pid_on_port() {
    lsof -ti :$1 2>/dev/null || echo ""
}

# Stop process function
stop_process_on_port() {
    local port=$1 name=$2 signal=$3
    local pid=$(get_pid_on_port $port)
    if [ -n "$pid" ]; then
        print_status "step" "Stopping ${name} (PID: ${pid})..."
        kill -$signal $pid 2>/dev/null || true

        local count=0
        while [ $count -lt 10 ] && is_port_in_use $port; do
            sleep 1
            count=$((count + 1))
        done

        if is_port_in_use $port; then
            return 1
        else
            return 0
        fi
    else
        return 2
    fi
}

cd "$PROJECT_ROOT"
print_banner

# Parse arguments
FORCE=false
SKIP_CONFIRM=false
SKIP_DOCKER=false

for arg in "$@"; do
    case $arg in
        -f|--force) FORCE=true; shift ;;
        -y|--yes) SKIP_CONFIRM=true; shift ;;
        --skip-docker) SKIP_DOCKER=true; shift ;;
    esac
done

# Confirmation
if [ "$SKIP_CONFIRM" = false ]; then
    echo -e "  ${BOLD}Services to stop:${RESET}"
    echo -e "    ${DIM}${DOT}${RESET} Console (port ${CONSOLE_PORT})"
    echo -e "    ${DIM}${DOT}${RESET} Management (port ${MANAGEMENT_PORT})"
    echo -e "    ${DIM}${DOT}${RESET} Backend (port ${BACKEND_PORT})"
    if [ "$SKIP_DOCKER" = false ]; then
        echo -e "    ${DIM}${DOT}${RESET} MySQL Docker"
        echo -e "    ${DIM}${DOT}${RESET} Redis Docker"
    fi
    echo ""
    read -p "  Confirm? (y/N): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_status "warn" "Operation cancelled"
        exit 0
    fi
fi

STOPPED_SERVICES=()

# Step 1: Console
print_section "Console Service"
if is_port_in_use $CONSOLE_PORT; then
    if stop_process_on_port $CONSOLE_PORT "Console" "TERM"; then
        print_status "ok" "Console stopped"
        STOPPED_SERVICES+=("Console")
    else
        print_status "warn" "Console not responding to SIGTERM, force killing..."
        if stop_process_on_port $CONSOLE_PORT "Console" "KILL"; then
            print_status "ok" "Console force stopped"
            STOPPED_SERVICES+=("Console")
        else
            print_status "error" "Failed to stop Console"
        fi
    fi
else
    print_status "info" "Console not running"
fi

# Step 2: Management
print_section "Management Service"
if is_port_in_use $MANAGEMENT_PORT; then
    if stop_process_on_port $MANAGEMENT_PORT "Management" "TERM"; then
        print_status "ok" "Management stopped"
        STOPPED_SERVICES+=("Management")
    else
        print_status "warn" "Management not responding to SIGTERM, force killing..."
        if stop_process_on_port $MANAGEMENT_PORT "Management" "KILL"; then
            print_status "ok" "Management force stopped"
            STOPPED_SERVICES+=("Management")
        else
            print_status "error" "Failed to stop Management"
        fi
    fi
else
    print_status "info" "Management not running"
fi

# Step 3: Backend
print_section "Backend Service"
if is_port_in_use $BACKEND_PORT; then
    if stop_process_on_port $BACKEND_PORT "Backend" "TERM"; then
        print_status "ok" "Backend stopped"
        STOPPED_SERVICES+=("Backend")
    else
        print_status "warn" "Backend not responding to SIGTERM, force killing..."
        # Kill any lingering node/nest processes
        pkill -9 -f "nest start" 2>/dev/null || true
        pkill -9 -f "node.*backend" 2>/dev/null || true
        sleep 2
        if is_port_in_use $BACKEND_PORT; then
            print_status "error" "Failed to stop Backend"
        else
            print_status "ok" "Backend force stopped"
            STOPPED_SERVICES+=("Backend")
        fi
    fi
else
    print_status "info" "Backend not running"
fi

# Step 4: Docker services
if [ "$SKIP_DOCKER" = false ]; then
    print_section "Docker Services"
    if docker ps | grep -q "ulticode-mysql" || docker ps | grep -q "ulticode-redis"; then
        print_status "step" "Stopping Docker containers..."
        cd backend
        if docker compose down 2>/dev/null; then
            print_status "ok" "Docker containers stopped"
            STOPPED_SERVICES+=("MySQL")
            STOPPED_SERVICES+=("Redis")
        else
            print_status "error" "Failed to stop Docker containers"
        fi
        cd ..
    else
        print_status "info" "Docker containers not running"
    fi
else
    print_section "Docker Services"
    print_status "info" "Skipping Docker services (--skip-docker)"
fi

# Step 5: Cleanup
print_section "Cleanup"
CLEANUP_FILES=(
    "/tmp/ulticode-backend.log"
    "/tmp/ulticode-console.log"
    "/tmp/ulticode-management.log"
    "nohup.out"
    "backend/nohup.out"
    "console/nohup.out"
    "management/nohup.out"
)
CLEANED=0

for file in "${CLEANUP_FILES[@]}"; do
    if [ -f "$file" ]; then
        rm -f "$file" 2>/dev/null || true
        CLEANED=$((CLEANED + 1))
    fi
done

# Kill any lingering pnpm/vite processes
pkill -9 -f "vite" 2>/dev/null || true
pkill -9 -f "pnpm.*dev" 2>/dev/null || true

print_status "ok" "Cleaned ${CLEANED} file(s) and killed residual processes"

# Summary
if [ ${#STOPPED_SERVICES[@]} -gt 0 ]; then
    print_stopped_box "${STOPPED_SERVICES[@]}"
else
    echo ""
    print_status "info" "No running services were stopped"
fi

# Final check
echo ""
RUNNING_COUNT=0
is_port_in_use $BACKEND_PORT && RUNNING_COUNT=$((RUNNING_COUNT + 1))
is_port_in_use $CONSOLE_PORT && RUNNING_COUNT=$((RUNNING_COUNT + 1))
is_port_in_use $MANAGEMENT_PORT && RUNNING_COUNT=$((RUNNING_COUNT + 1))
docker ps | grep -q "ulticode-mysql" && RUNNING_COUNT=$((RUNNING_COUNT + 1))
docker ps | grep -q "ulticode-redis" && RUNNING_COUNT=$((RUNNING_COUNT + 1))

if [ $RUNNING_COUNT -gt 0 ]; then
    echo -e "  ${YELLOW_BG} PARTIAL ${RESET} ${RUNNING_COUNT} service(s) still running"
    echo -e "  ${DIM}Force stop: ./shell/stop.sh --force${RESET}"
else
    echo -e "  ${GREEN}${CHECK}${RESET} ${BOLD}All services stopped${RESET}"
fi

echo ""
