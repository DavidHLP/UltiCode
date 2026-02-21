#!/bin/bash

set -e

# Get script directory (supports symlinks)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load environment variables from .env file (safe for special characters)
load_env_file() {
    local env_file=$1
    if [ -f "$env_file" ]; then
        while IFS= read -r line || [ -n "$line" ]; do
            # Skip empty lines and comments
            [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
            # Only process lines containing =
            if [[ "$line" == *"="* ]]; then
                # Extract key and value
                local key="${line%%=*}"
                local value="${line#*=}"
                # Remove surrounding quotes if present
                if [[ "$value" =~ ^\"(.*)\"$ ]]; then
                    value="${BASH_REMATCH[1]}"
                elif [[ "$value" =~ ^\'(.*)\'$ ]]; then
                    value="${BASH_REMATCH[1]}"
                fi
                export "$key=$value"
            fi
        done < "$env_file"
    fi
}
load_env_file "$PROJECT_ROOT/backend/.env"

# Configuration
CONSOLE_PORT=9002
MANAGEMENT_PORT=9003
BACKEND_PORT=9001
MYSQL_PORT=23306
REDIS_PORT=26379

HEALTH_CHECK_MAX_RETRIES=30
HEALTH_CHECK_INTERVAL=2

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
    echo -e "  ${DIM}UltiCode Development Environment${RESET}"
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

# Print service box
print_service_box() {
    echo ""
    echo -e "  ${GREEN_BG}${WHITE} RUNNING ${RESET}"
    echo ""
    echo -e "  ${BOLD}Console${RESET}     http://localhost:${CONSOLE_PORT}"
    echo -e "  ${BOLD}Management${RESET}  http://localhost:${MANAGEMENT_PORT}"
    echo -e "  ${BOLD}Backend${RESET}     http://localhost:${BACKEND_PORT}"
    echo -e "  ${BOLD}MySQL${RESET}       localhost:${MYSQL_PORT}"
    echo -e "  ${BOLD}Redis${RESET}       localhost:${REDIS_PORT}"
    echo ""
}

# Port check
is_port_in_use() {
    lsof -i :$1 >/dev/null 2>&1
}

get_pid_on_port() {
    lsof -ti :$1 2>/dev/null || echo ""
}

# Main
cd "$PROJECT_ROOT"
print_banner

# Parse arguments
SKIP_DOCKER=false
SKIP_INSTALL=false

for arg in "$@"; do
    case $arg in
        --skip-docker) SKIP_DOCKER=true; shift ;;
        --skip-install) SKIP_INSTALL=true; shift ;;
    esac
done

# Step 1: Check ports
print_section "Checking Ports"
check_port() {
    local port=$1 name=$2
    if is_port_in_use $port; then
        local pid=$(get_pid_on_port $port)
        print_status "warn" "${name} port ${port} is in use (PID: ${pid})"
        read -p "       Continue? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_status "error" "Startup cancelled"
            exit 1
        fi
    else
        print_status "ok" "${name} port ${port} available"
    fi
}
check_port $CONSOLE_PORT "Console"
check_port $MANAGEMENT_PORT "Management"
check_port $BACKEND_PORT "Backend"

# Step 2: Docker services
if [ "$SKIP_DOCKER" = false ]; then
    print_section "Docker Services (MySQL & Redis)"

    if docker ps | grep -q "ulticode-mysql"; then
        print_status "info" "MySQL container already running"
    else
        print_status "step" "Starting Docker containers..."
        cd backend
        docker compose up -d mysql redis
        cd ..

        printf "  "
        for i in {1..30}; do
            if docker exec ulticode-mysql mysqladmin ping -h localhost -u root -proot --silent 2>/dev/null; then
                echo -e "\r  ${GREEN}${CHECK}${RESET} MySQL started successfully    "
                break
            fi
            if [ $i -eq 30 ]; then
                echo -e "\r  ${RED}${CROSS}${RESET} MySQL startup timeout (30s)    "
                exit 1
            fi
            printf "\r  %s Waiting for MySQL... (%ds/30s)" "$(get_spinner $i)" "$i"
            sleep 2
        done
    fi

    if docker ps | grep -q "ulticode-redis"; then
        print_status "ok" "Redis running"
    else
        print_status "warn" "Redis not running"
    fi
else
    print_section "Docker Services"
    print_status "info" "Skipping Docker services (--skip-docker)"
fi

# Step 3: Install dependencies
if [ "$SKIP_INSTALL" = false ]; then
    print_section "Dependencies"
    if [ ! -d "node_modules" ]; then
        print_status "step" "Installing root dependencies..."
        pnpm install
        print_status "ok" "Root dependencies installed"
    else
        print_status "info" "Root dependencies already installed"
    fi
fi

# Step 4: Backend
print_section "Backend Service"
if is_port_in_use $BACKEND_PORT; then
    print_status "info" "Backend already running on port ${BACKEND_PORT}"
else
    cd backend

    # Generate Prisma client
    print_status "step" "Generating Prisma client..."
    pnpm run prisma:generate >/dev/null 2>&1 || true

    # Rotate logs
    if [ -f "/tmp/ulticode-backend.log" ] && [ $(stat -c%s /tmp/ulticode-backend.log 2>/dev/null || echo 0) -gt 10485760 ]; then
        mv /tmp/ulticode-backend.log /tmp/ulticode-backend.log.old
    fi

    # Start backend with watch mode (skip lint, test, db:reset for faster startup)
    nohup npx nest start --watch >/tmp/ulticode-backend.log 2>&1 &
    BACKEND_PID=$!
    cd ..

    print_status "step" "Starting backend (PID: ${BACKEND_PID})"

    printf "  "
    health_retries=0
    while [ $health_retries -lt $HEALTH_CHECK_MAX_RETRIES ]; do
        if curl -s -f "http://localhost:$BACKEND_PORT" -o /dev/null 2>&1; then
            echo -e "\r  ${GREEN}${CHECK}${RESET} Backend ready                 "
            break
        fi
        health_retries=$((health_retries + 1))
        printf "\r  %s Health check... (%ds)" "$(get_spinner $health_retries)" "$((health_retries * HEALTH_CHECK_INTERVAL))"
        sleep $HEALTH_CHECK_INTERVAL
    done

    if [ $health_retries -ge $HEALTH_CHECK_MAX_RETRIES ]; then
        print_status "warn" "Health check timeout, process may still be starting"
        echo -e "       ${DIM}tail -f /tmp/ulticode-backend.log${RESET}"
    fi
fi

# Step 5: Console
print_section "Console Service"
if is_port_in_use $CONSOLE_PORT; then
    print_status "info" "Console already running on port ${CONSOLE_PORT}"
else
    cd console

    # Rotate logs
    if [ -f "/tmp/ulticode-console.log" ] && [ $(stat -c%s /tmp/ulticode-console.log 2>/dev/null || echo 0) -gt 10485760 ]; then
        mv /tmp/ulticode-console.log /tmp/ulticode-console.log.old
    fi

    nohup pnpm run dev >/tmp/ulticode-console.log 2>&1 &
    CONSOLE_PID=$!
    cd ..

    print_status "step" "Starting console (PID: ${CONSOLE_PID})"

    printf "  "
    for i in {1..15}; do
        if is_port_in_use $CONSOLE_PORT; then
            echo -e "\r  ${GREEN}${CHECK}${RESET} Console ready                "
            break
        fi
        printf "\r  %s Waiting for console... (%ds/15s)" "$(get_spinner $i)" "$i"
        sleep 1
    done

    if ! is_port_in_use $CONSOLE_PORT; then
        print_status "warn" "Console may still be starting"
        echo -e "       ${DIM}tail -f /tmp/ulticode-console.log${RESET}"
    fi
fi

# Step 6: Management
print_section "Management Service"
if is_port_in_use $MANAGEMENT_PORT; then
    print_status "info" "Management already running on port ${MANAGEMENT_PORT}"
else
    cd management

    # Rotate logs
    if [ -f "/tmp/ulticode-management.log" ] && [ $(stat -c%s /tmp/ulticode-management.log 2>/dev/null || echo 0) -gt 10485760 ]; then
        mv /tmp/ulticode-management.log /tmp/ulticode-management.log.old
    fi

    nohup pnpm run dev >/tmp/ulticode-management.log 2>&1 &
    MANAGEMENT_PID=$!
    cd ..

    print_status "step" "Starting management (PID: ${MANAGEMENT_PID})"

    printf "  "
    for i in {1..15}; do
        if is_port_in_use $MANAGEMENT_PORT; then
            echo -e "\r  ${GREEN}${CHECK}${RESET} Management ready             "
            break
        fi
        printf "\r  %s Waiting for management... (%ds/15s)" "$(get_spinner $i)" "$i"
        sleep 1
    done

    if ! is_port_in_use $MANAGEMENT_PORT; then
        print_status "warn" "Management may still be starting"
        echo -e "       ${DIM}tail -f /tmp/ulticode-management.log${RESET}"
    fi
fi

# Step 7: Verification
print_section "Status Verification"
ALL_READY=true

if is_port_in_use $BACKEND_PORT; then
    print_status "ok" "Backend running on port ${BACKEND_PORT}"
else
    print_status "error" "Backend not responding"
    ALL_READY=false
fi

if is_port_in_use $CONSOLE_PORT; then
    print_status "ok" "Console running on port ${CONSOLE_PORT}"
else
    print_status "error" "Console not responding"
    ALL_READY=false
fi

if is_port_in_use $MANAGEMENT_PORT; then
    print_status "ok" "Management running on port ${MANAGEMENT_PORT}"
else
    print_status "error" "Management not responding"
    ALL_READY=false
fi

if [ "$SKIP_DOCKER" = false ]; then
    if docker ps | grep -q "ulticode-mysql"; then
        print_status "ok" "MySQL running"
    else
        print_status "error" "MySQL not running"
        ALL_READY=false
    fi

    if docker ps | grep -q "ulticode-redis"; then
        print_status "ok" "Redis running"
    else
        print_status "error" "Redis not running"
        ALL_READY=false
    fi
fi

# Summary
if [ "$ALL_READY" = true ]; then
    print_service_box
    echo -e "  ${DIM}Logs:${RESET}"
    echo -e "    ${DIM}tail -f /tmp/ulticode-backend.log${RESET}"
    echo -e "    ${DIM}tail -f /tmp/ulticode-console.log${RESET}"
    echo -e "    ${DIM}tail -f /tmp/ulticode-management.log${RESET}"
    echo ""
    echo -e "  ${DIM}Stop:  ./shell/stop.sh${RESET}"
else
    echo ""
    echo -e "  ${YELLOW_BG} PARTIAL START ${RESET}"
    echo ""
    echo -e "  Some services failed to start. Check logs:"
    echo -e "    ${DIM}tail -f /tmp/ulticode-backend.log${RESET}"
    echo -e "    ${DIM}tail -f /tmp/ulticode-console.log${RESET}"
    echo -e "    ${DIM}tail -f /tmp/ulticode-management.log${RESET}"
fi

echo ""
