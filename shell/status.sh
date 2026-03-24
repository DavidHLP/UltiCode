#!/bin/bash

# ============================================================
# UltiCode Status Script
# Usage: ./status.sh
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source common functions
source "$SCRIPT_DIR/common.sh"

# ============== Banner ==============
print_banner "Service Status"

cd "$PROJECT_ROOT"

# ============== Helper Functions ==============
check_service() {
    local name=$1 port=$2 url=$3
    if port_used $port; then
        local pid=$(get_pid $port)
        ok "$name ${D}(PID: $pid)${R}"
        [ -n "$url" ] && info "  $url"
        return 0
    else
        err "$name ${D}(not running)${R}"
        return 1
    fi
}

check_docker() {
    local name=$1 port=$2 extra=$3
    if docker ps 2>/dev/null | grep -q "ulticode-$name"; then
        ok "$name ${D}(localhost:$port)${R} ${extra:+$extra}"
        return 0
    else
        if docker ps -a 2>/dev/null | grep -q "ulticode-$name"; then
            err "$name ${D}(stopped)${R}"
        else
            info "$name ${D}(not created)${R}"
        fi
        return 1
    fi
}

# ============== Application Services ==============
print_section "Application Services"

check_service "Backend" $BACKEND_PORT "http://localhost:$BACKEND_PORT"
check_service "Console" $CONSOLE_PORT "http://localhost:$CONSOLE_PORT"
check_service "Management" $MANAGEMENT_PORT "http://localhost:$MANAGEMENT_PORT"
check_service "Recommend-Web" $RECOMMEND_WEB_PORT "http://localhost:$RECOMMEND_WEB_PORT"
check_service "Recommend-Dubbo" $RECOMMEND_PROVIDER_DUBBO_PORT ""

# ============== Docker Services ==============
log ""
print_section "Docker Services"

check_docker "mysql" $MYSQL_PORT
check_docker "redis" $REDIS_PORT
check_docker "nacos" $NACOS_PORT "${D}(console: 28080)${R}"

# ============== Health Checks ==============
log ""
print_section "Health Checks"

if port_used $BACKEND_PORT; then
    if curl -sf "http://localhost:$BACKEND_PORT/actuator/health" >/dev/null 2>&1; then
        ok "Backend health check passed"
    else
        err "Backend health check failed"
    fi
else
    info "Backend not running, skipping"
fi

# ============== Log Files ==============
log ""
print_section "Log Files"

LOG_COUNT=0
for log in /tmp/ulticode-*.log; do
    if [ -f "$log" ]; then
        size=$(du -h "$log" 2>/dev/null | cut -f1)
        info "$(basename $log) ${D}($size)${R}"
        ((LOG_COUNT++)) || true
    fi
done

[ $LOG_COUNT -eq 0 ] && info "No log files found"

# ============== Summary ==============
echo ""
RUNNING=0
TOTAL=8

port_used $BACKEND_PORT && ((RUNNING++)) || true
port_used $CONSOLE_PORT && ((RUNNING++)) || true
port_used $MANAGEMENT_PORT && ((RUNNING++)) || true
port_used $RECOMMEND_WEB_PORT && ((RUNNING++)) || true
port_used $RECOMMEND_PROVIDER_DUBBO_PORT && ((RUNNING++)) || true
docker ps 2>/dev/null | grep -q "ulticode-mysql" && ((RUNNING++)) || true
docker ps 2>/dev/null | grep -q "ulticode-redis" && ((RUNNING++)) || true
docker ps 2>/dev/null | grep -q "ulticode-nacos" && ((RUNNING++)) || true

echo -e "  ${B}$RUNNING${R}/${TOTAL} services running"
echo ""

if [ $RUNNING -eq $TOTAL ]; then
    echo -e "  ${GREEN_BG}${WHITE} ALL SYSTEMS GO ${R}"
    echo ""
    log "${B}Quick Access:${R}"
    info "Console        http://localhost:$CONSOLE_PORT"
    info "Management     http://localhost:$MANAGEMENT_PORT"
    info "Backend        http://localhost:$BACKEND_PORT"
    info "Recommend-Web  http://localhost:$RECOMMEND_WEB_PORT"
    info "Nacos          http://localhost:28080/nacos"
elif [ $RUNNING -eq 0 ]; then
    echo -e "  ${RED_BG}${WHITE} ALL STOPPED ${R}"
    echo ""
    log "${D}Start: ./shell/start.sh${R}"
else
    echo -e "  ${Y}${B}! Partial services running${R}"
    echo ""
    log "${D}Start:   ./shell/start.sh${R}"
    log "${D}Stop:    ./shell/stop.sh${R}"
    log "${D}Restart: ./shell/restart.sh${R}"
fi

echo ""
