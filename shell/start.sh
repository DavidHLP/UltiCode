#!/bin/bash

# ============================================================
# UltiCode Start Script - Simple & Reliable
# Usage: ./start.sh [-y] [--skip-docker] [--skip-install]
# ============================================================

# Don't use set -e - we handle errors explicitly for better reliability

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ============== Configuration ==============
CONSOLE_PORT=9002
MANAGEMENT_PORT=9003
BACKEND_PORT=9001
MYSQL_PORT=23306
REDIS_PORT=26379
NACOS_PORT=28848

# Timeouts (seconds)
BACKEND_TIMEOUT=60
FRONTEND_TIMEOUT=30
MYSQL_TIMEOUT=60

# ============== Colors & Symbols ==============
R='\033[0m' G='\033[32m' Y='\033[33m' C='\033[36m' D='\033[2m' B='\033[1m'
CHECK="✓" CROSS="✗" ARROW="→"

# ============== Helper Functions ==============
log() { echo -e "  $1"; }
ok() { log "${G}${CHECK}${R} $1"; }
err() { log "${Y}${CROSS}${R} $1"; }
info() { log "${D}•${R} $1"; }
step() { log "${C}${ARROW}${R} $1"; }

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

port_used() { lsof -i :$1 >/dev/null 2>&1; }
get_pid() { lsof -ti :$1 2>/dev/null || echo ""; }

# ============== Load Environment ==============
load_env() {
    local env_file="$PROJECT_ROOT/backend/.env"
    if [ -f "$env_file" ]; then
        while IFS= read -r line || [ -n "$line" ]; do
            # Skip empty lines and comments
            [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
            # Only process lines containing =
            if [[ "$line" == *"="* ]]; then
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
load_env

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

# ============== Banner ==============
echo ""
echo -e "${C}${B}"
echo "   _   _ _   _ _   _ _   _ ___ ___  ___ ___  ___  "
echo "  | | | | | | | | | | \\ | |_ _/ _ \\/ __/ __|| _ \\ "
echo "  | |_| | |_| | | | |  \\| || | (_) | (_| _| |   / "
echo "   \\___/ \\___/|_| |_|_|\\_|___|\\___/ \\___|___|_|_\\ "
echo -e "${R}"
echo -e "  ${D}Starting Development Environment${R}"
echo ""

cd "$PROJECT_ROOT"

# ============== Step 1: Check Ports ==============
log "${B}:: Check Ports${R} ${D}────────────────────────${R}"

for svc in "Console:$CONSOLE_PORT" "Management:$MANAGEMENT_PORT" "Backend:$BACKEND_PORT"; do
    name="${svc%:*}" port="${svc#*:}"
    if port_used $port; then
        pid=$(get_pid $port)
        if [ "$SKIP_CONFIRM" = true ]; then
            info "$name already running on port $port (PID: $pid)"
        else
            log "${Y}!${R} $name port $port in use (PID: $pid)"
            read -p "       Continue? (y/N): " -n 1 -r
            echo
            [[ ! $REPLY =~ ^[Yy]$ ]] && { err "Cancelled"; exit 1; }
        fi
    else
        ok "$name port $port available"
    fi
done

# ============== Step 2: Docker Services ==============
if [ "$SKIP_DOCKER" = false ]; then
    log ""
    log "${B}:: Docker Services${R} ${D}────────────────${R}"

    if docker ps 2>/dev/null | grep -q "ulticode-mysql"; then
        info "MySQL already running"
    else
        step "Starting MySQL, Redis & Nacos..."
        cd backend && docker compose up -d mysql redis nacos && cd ..

        spin_wait "MySQL ready" \
            "docker exec ulticode-mysql mysqladmin ping -h localhost -u root -proot --silent" \
            $MYSQL_TIMEOUT

        docker ps 2>/dev/null | grep -q "ulticode-redis" && ok "Redis running"
        docker ps 2>/dev/null | grep -q "ulticode-nacos" && ok "Nacos running"
    fi
else
    log ""
    log "${B}:: Docker Services${R} ${D}────────────────${R}"
    info "Skipping (--skip-docker)"
fi

# ============== Step 3: Dependencies ==============
if [ "$SKIP_INSTALL" = false ]; then
    log ""
    log "${B}:: Dependencies${R} ${D}──────────────────${R}"
    if [ -d "node_modules" ]; then
        info "Already installed"
    else
        step "Installing root dependencies..."
        pnpm install && ok "Dependencies installed"
    fi
fi

# ============== Step 4: Backend ==============
log ""
log "${B}:: Backend${R} ${D}──────────────────────${R}"

if port_used $BACKEND_PORT; then
    info "Already running on port $BACKEND_PORT"
else
    cd backend
    pnpm run prisma:generate >/dev/null 2>&1 || true
    rm -f nohup.out 2>/dev/null

    nohup npx nest start --watch >/tmp/ulticode-backend.log 2>&1 &
    BACKEND_PID=$!
    cd ..

    spin_wait "Backend ready" "curl -sf http://localhost:$BACKEND_PORT" $BACKEND_TIMEOUT || {
        info "Check: tail -f /tmp/ulticode-backend.log"
    }
fi

# ============== Step 5: Console ==============
log ""
log "${B}:: Console${R} ${D}──────────────────────${R}"

if port_used $CONSOLE_PORT; then
    info "Already running on port $CONSOLE_PORT"
else
    cd console && rm -f nohup.out 2>/dev/null
    nohup pnpm run dev >/tmp/ulticode-console.log 2>&1 &
    CONSOLE_PID=$!
    cd ..

    spin_wait "Console ready" "port_used $CONSOLE_PORT" $FRONTEND_TIMEOUT || {
        info "Check: tail -f /tmp/ulticode-console.log"
    }
fi

# ============== Step 6: Management ==============
log ""
log "${B}:: Management${R} ${D}──────────────────${R}"

if port_used $MANAGEMENT_PORT; then
    info "Already running on port $MANAGEMENT_PORT"
else
    cd management && rm -f nohup.out 2>/dev/null
    nohup pnpm run dev >/tmp/ulticode-management.log 2>&1 &
    MANAGEMENT_PID=$!
    cd ..

    spin_wait "Management ready" "port_used $MANAGEMENT_PORT" $FRONTEND_TIMEOUT || {
        info "Check: tail -f /tmp/ulticode-management.log"
    }
fi

# ============== Summary ==============
log ""
log "${B}:: Status${R} ${D}────────────────────────${R}"

ALL_OK=true
port_used $BACKEND_PORT && ok "Backend  http://localhost:$BACKEND_PORT" || { err "Backend not responding"; ALL_OK=false; }
port_used $CONSOLE_PORT && ok "Console  http://localhost:$CONSOLE_PORT" || { err "Console not responding"; ALL_OK=false; }
port_used $MANAGEMENT_PORT && ok "Management http://localhost:$MANAGEMENT_PORT" || { err "Management not responding"; ALL_OK=false; }

if [ "$SKIP_DOCKER" = false ]; then
    docker ps 2>/dev/null | grep -q "ulticode-mysql" && ok "MySQL   localhost:$MYSQL_PORT" || { err "MySQL not running"; ALL_OK=false; }
    docker ps 2>/dev/null | grep -q "ulticode-redis" && ok "Redis   localhost:$REDIS_PORT" || { err "Redis not running"; ALL_OK=false; }
    docker ps 2>/dev/null | grep -q "ulticode-nacos" && ok "Nacos   localhost:$NACOS_PORT (console: 28080)" || { err "Nacos not running"; ALL_OK=false; }
fi

log ""
if [ "$ALL_OK" = true ]; then
    echo -e "  ${G}${B}✓ All services running${R}"
    log ""
    log "${D}Logs: tail -f /tmp/ulticode-{backend,console,management}.log${R}"
    log "${D}Stop: ./shell/stop.sh${R}"
else
    echo -e "  ${Y}${B}! Some services may still be starting${R}"
    log ""
    log "${D}Check logs: tail -f /tmp/ulticode-{backend,console,management}.log${R}"
fi

echo ""
