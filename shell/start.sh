#!/bin/bash

# ============================================================
# UltiCode Start Script
# Usage: ./start.sh [-y] [--skip-docker] [--skip-install]
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

# ============== Banner ==============
print_banner "Starting Development Environment"

cd "$PROJECT_ROOT"

# ============== Load Root Environment Variables ==============
# 导出根目录环境变量，供所有子模块使用
if [ -f "$PROJECT_ROOT/.env" ]; then
    info "Loading environment variables from root .env..."
    load_env_file "$PROJECT_ROOT/.env"
else
    info "Root .env not found, using defaults"
fi

# ============== Step 1: Check Ports ==============
print_section "Check Ports"

for svc in "Console:$CONSOLE_PORT" "Management:$MANAGEMENT_PORT" "Backend:$BACKEND_PORT" "Recommend-Web:$RECOMMEND_WEB_PORT" "Recommend-Provider:$RECOMMEND_PROVIDER_DUBBO_PORT"; do
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
log ""
print_section "Docker Services"

if [ "$SKIP_DOCKER" = true ]; then
    info "Skipping (--skip-docker)"
elif docker ps 2>/dev/null | grep -q "ulticode-mysql"; then
    info "MySQL already running"
else
    step "Starting MySQL, Redis & Nacos..."
    # Try docker compose if compose file exists, otherwise show error
    if [ -f "docker-compose.yml" ]; then
        docker compose up -d mysql redis nacos
    else
        err "docker-compose.yml not found in project root"
        err "Please create the compose file or start containers manually:"
        err "  docker run -d --name ulticode-mysql -p 23306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:9.1"
        err "  docker run -d --name ulticode-redis -p 26379:6379 redis:7-alpine"
        err "  docker run -d --name ulticode-nacos -p 28848:8848 -p 28080:8080 nacos/nacos-server:v2.3.2"
        exit 1
    fi

    spin_wait "MySQL ready" \
        "docker exec ulticode-mysql mysqladmin ping -h localhost -u root -proot --silent" \
        $MYSQL_TIMEOUT

    docker ps 2>/dev/null | grep -q "ulticode-redis" && ok "Redis running"
    docker ps 2>/dev/null | grep -q "ulticode-nacos" && ok "Nacos running"
fi

# ============== Step 3: Dependencies ==============
if [ "$SKIP_INSTALL" = false ]; then
    log ""
    print_section "Dependencies"
    if [ -d "node_modules" ]; then
        info "Already installed"
    else
        step "Installing root dependencies..."
        pnpm install && ok "Dependencies installed"
    fi
fi

# ============== Step 4: Database Initialization ==============
log ""
print_section "Database Initialization"

if [ -d "init-db" ]; then
    step "Running database migrations..."
    cd init-db
    INIT_DB_LOG="/tmp/ulticode-init-db.log"

    # Install dependencies if needed
    if [ ! -d "node_modules" ] && [ "$SKIP_INSTALL" = false ]; then
        info "Installing init-db dependencies..."
        pnpm install >>"$INIT_DB_LOG" 2>&1
    fi

    # Environment variables already loaded from root .env

    # Generate Prisma client
    step "Generating Prisma client..."
    rm -f "$INIT_DB_LOG" 2>/dev/null
    if pnpm prisma:generate >>"$INIT_DB_LOG" 2>&1; then
        ok "Prisma client generated"
    else
        err "Failed to generate Prisma client"
        info "Check: tail -f $INIT_DB_LOG"
    fi

    # Run migrations
    step "Running migrations..."
    if pnpm migration:run:all >>"$INIT_DB_LOG" 2>&1; then
        ok "Migrations completed"
    else
        info "Migrations may have warnings (some may already be applied)"
    fi

    cd ..
else
    info "init-db directory not found, skipping"
fi

# ============== Step 5: Backend (Spring Boot) ==============
log ""
print_section "Backend (Spring Boot)"

if port_used $BACKEND_PORT; then
    info "Already running on port $BACKEND_PORT"
else
    cd backend-spring
    rm -f nohup.out 2>/dev/null

    nohup ./mvnw spring-boot:run >/tmp/ulticode-backend.log 2>&1 &
    BACKEND_PID=$!
    cd ..

    spin_wait "Backend ready" "curl -sf http://localhost:$BACKEND_PORT/actuator/health" $BACKEND_TIMEOUT || {
        info "Check: tail -f /tmp/ulticode-backend.log"
    }
fi

# ============== Step 6: Console ==============
log ""
print_section "Console"

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

# ============== Step 7: Management ==============
log ""
print_section "Management"

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

# ============== Step 8: Recommendation Service ==============
log ""
print_section "Recommendation Service"

if [ ! -d "recommendation" ]; then
    info "Recommendation service not found, skipping"
elif ! command -v java &>/dev/null; then
    err "Java not found, skipping recommendation service"
elif ! command -v mvn &>/dev/null; then
    err "Maven not found, skipping recommendation service"
else
    cd recommendation

    # Start Provider first (Dubbo service)
    if port_used $RECOMMEND_PROVIDER_DUBBO_PORT; then
        info "Recommend-Provider already running"
    else
        step "Starting Recommend-Provider..."
        rm -f /tmp/ulticode-recommend-provider.log 2>/dev/null
        export NACOS_HOST=localhost
        export NACOS_PORT=$NACOS_PORT
        nohup mvn -pl recommend-provider spring-boot:run \
            -Dspring-boot.run.arguments="--dubbo.registry.address=nacos://localhost:$NACOS_PORT" \
            >/tmp/ulticode-recommend-provider.log 2>&1 &
        RECOMMEND_PROVIDER_PID=$!

        spin_wait "Provider ready" "port_used $RECOMMEND_PROVIDER_DUBBO_PORT" $RECOMMEND_TIMEOUT || {
            info "Check: tail -f /tmp/ulticode-recommend-provider.log"
        }
    fi

    # Start Web (HTTP API)
    if port_used $RECOMMEND_WEB_PORT; then
        info "Recommend-Web already running"
    else
        step "Starting Recommend-Web..."
        rm -f /tmp/ulticode-recommend-web.log 2>/dev/null
        nohup mvn -pl recommend-web spring-boot:run \
            -Dspring-boot.run.arguments="--server.port=$RECOMMEND_WEB_PORT --dubbo.registry.address=nacos://localhost:$NACOS_PORT" \
            >/tmp/ulticode-recommend-web.log 2>&1 &
        RECOMMEND_WEB_PID=$!

        spin_wait "Web ready" "port_used $RECOMMEND_WEB_PORT" $RECOMMEND_TIMEOUT || {
            info "Check: tail -f /tmp/ulticode-recommend-web.log"
        }
    fi

    cd ..
fi

# ============== Summary ==============
log ""
print_section "Status"

ALL_OK=true

# Check application services
port_used $BACKEND_PORT && ok "Backend       http://localhost:$BACKEND_PORT" || { err "Backend not responding"; ALL_OK=false; }
port_used $CONSOLE_PORT && ok "Console       http://localhost:$CONSOLE_PORT" || { err "Console not responding"; ALL_OK=false; }
port_used $MANAGEMENT_PORT && ok "Management    http://localhost:$MANAGEMENT_PORT" || { err "Management not responding"; ALL_OK=false; }
port_used $RECOMMEND_WEB_PORT && ok "Recommend-Web http://localhost:$RECOMMEND_WEB_PORT" || { err "Recommend-Web not responding"; ALL_OK=false; }
port_used $RECOMMEND_PROVIDER_DUBBO_PORT && ok "Recommend-Dubbo localhost:$RECOMMEND_PROVIDER_DUBBO_PORT" || { err "Recommend-Provider not responding"; ALL_OK=false; }

# Check Docker services
if [ "$SKIP_DOCKER" = false ]; then
    docker ps 2>/dev/null | grep -q "ulticode-mysql" && ok "MySQL         localhost:$MYSQL_PORT" || { err "MySQL not running"; ALL_OK=false; }
    docker ps 2>/dev/null | grep -q "ulticode-redis" && ok "Redis         localhost:$REDIS_PORT" || { err "Redis not running"; ALL_OK=false; }
    docker ps 2>/dev/null | grep -q "ulticode-nacos" && ok "Nacos         localhost:$NACOS_PORT (console: 28080)" || { err "Nacos not running"; ALL_OK=false; }
fi

log ""
if [ "$ALL_OK" = true ]; then
    echo -e "  ${GREEN_BG}${WHITE} RUNNING ${R}"
    log ""
    log "${D}Logs: tail -f /tmp/ulticode-*.log${R}"
    log "${D}Stop: ./shell/stop.sh${R}"
else
    echo -e "  ${Y}${B}! Some services may still be starting${R}"
    log ""
    log "${D}Check logs: tail -f /tmp/ulticode-*.log${R}"
fi

echo ""
