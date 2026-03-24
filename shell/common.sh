#!/bin/bash

# ============================================================
# UltiCode Common Functions - Shared utilities for all scripts
# ============================================================

# ============== Colors & Symbols ==============
R='\033[0m' G='\033[32m' Y='\033[33m' C='\033[36m' D='\033[2m' B='\033[1m'
RED_BG='\033[41m' GREEN_BG='\033[42m' BLUE_BG='\033[44m' WHITE='\033[37m'
CHECK="✓" CROSS="✗" ARROW="→"

# ============== Service Ports ==============
CONSOLE_PORT=9002
MANAGEMENT_PORT=9003
BACKEND_PORT=9001
RECOMMEND_WEB_PORT=9004
RECOMMEND_PROVIDER_DUBBO_PORT=20881
MYSQL_PORT=23306
REDIS_PORT=26379
NACOS_PORT=28848

# ============== Timeouts (seconds) ==============
BACKEND_TIMEOUT=60
FRONTEND_TIMEOUT=30
MYSQL_TIMEOUT=60
RECOMMEND_TIMEOUT=90
PROCESS_WAIT_TIMEOUT=10

# ============== Helper Functions ==============
log() { echo -e "  $1"; }
ok() { log "${G}${CHECK}${R} $1"; }
err() { log "${Y}${CROSS}${R} $1"; }
info() { log "${D}•${R} $1"; }
step() { log "${C}${ARROW}${R} $1"; }

# Check if port is in use
port_used() { lsof -i :$1 >/dev/null 2>&1; }

# Get PID by port
get_pid() { lsof -ti :$1 2>/dev/null || echo ""; }

# Spinner wait for a condition
spin_wait() {
    local msg=$1 check_cmd=$2 timeout=$3
    local i=0 spinner='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
    printf "  ${C}${ARROW}${R} ${msg}..."
    while [ $i -lt $timeout ]; do
        if eval "$check_cmd" >/dev/null 2>&1; then
            printf "\r  ${G}${CHECK}${R} ${msg}    \n"
            return 0
        fi
        printf "\r  ${spinner:$((i % 10)):1} ${msg}... ($((i+1))s/${timeout}s)"
        sleep 1
        ((i++)) || true
    done
    printf "\r  ${Y}!${R} ${msg} timeout    \n"
    return 1
}

# Load environment variables from .env file
load_env_file() {
    local env_file=$1
    if [ -f "$env_file" ]; then
        while IFS= read -r line || [ -n "$line" ]; do
            [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
            if [[ "$line" == *"="* ]]; then
                local key="${line%%=*}"
                local value="${line#*=}"
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

# Print banner
print_banner() {
    local title=$1
    echo ""
    echo -e "${C}${B}"
    echo "   _   _ _   _ _   _ _   _ ___ ___  ___ ___  ___  "
    echo "  | | | | | | | | | | \\ | |_ _/ _ \\/ __/ __|| _ \\ "
    echo "  | |_| | |_| | | | |  \\| || | (_) | (_| _| |   / "
    echo "   \\___/ \\___/|_| |_|_|\\_|___|\\___/ \\___|___|_|_\\ "
    echo -e "${R}"
    echo -e "  ${D}${title}${R}"
    echo ""
}

# Print section header
print_section() {
    local title=$1
    local line=${2:-────────────────}
    log "${B}:: ${title}${R} ${D}${line}${R}"
}

# Stop service by port
stop_port() {
    local port=$1 name=$2
    local pid=$(get_pid $port)
    if [ -z "$pid" ]; then
        info "$name not running"
        return 2
    fi

    step "Stopping $name (PID: $pid)..."
    kill -TERM $pid 2>/dev/null || true

    # Wait up to PROCESS_WAIT_TIMEOUT seconds
    local i=0
    while [ $i -lt $PROCESS_WAIT_TIMEOUT ] && port_used $port; do
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

# Get script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
