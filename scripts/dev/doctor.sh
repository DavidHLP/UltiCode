#!/usr/bin/env bash
# scripts/dev/doctor.sh — UltiCode 进程/端口健康检查
#
# 三个用途:
#   1. 揭示端口被谁占: 是 PM2 业务进程, 还是某个孤儿 vite, 还是 Claude Preview 临时起的
#   2. 检查 PM2 健康: ↺ 飙升(env 缓存 / 基础设施未就绪)、假性 online
#   3. 给出明确建议: 现在走 PM2 (A) / Preview (B) / 修基础设施, 并附命令
#
# 跨平台: Windows (Git Bash) + Linux/macOS. 用 lsof / netstat 二选一, 没有依赖特定工具链.
# 设计上无副作用: 只读, 不 stop / start / restart 任何东西.
#
# 使用:
#   scripts/dev/doctor.sh           # 默认: 全检 + 给建议
#   scripts/dev/doctor.sh --json    # JSON 输出 (供 Claude / 其他工具消费)
#   scripts/dev/doctor.sh --ports   # 只看端口
#   scripts/dev/doctor.sh --pm2     # 只看 PM2
#   scripts/dev/doctor.sh --quiet   # 静默: 0 退出=健康, 1=有问题

set -u
set -o pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PORTS=(9101 9102 9103 9002 9003 8563)
LABELS=(
  "9101:Auth Backend"
  "9102:Admin Backend"
  "9103:App Backend"
  "9002:Console Frontend (Vite)"
  "9003:Management Frontend (Vite)"
  "8563:Arthas MCP Server"
)
PM2_APP_PATTERN='^ulticode-'
JSON_ONLY=false
PORTS_ONLY=false
PM2_ONLY=false
QUIET=false

for arg in "$@"; do
  case "$arg" in
    --json) JSON_ONLY=true ;;
    --ports) PORTS_ONLY=true ;;
    --pm2) PM2_ONLY=true ;;
    --quiet|-q) QUIET=true ;;
    -h|--help)
      sed -n '2,15p' "$0"
      exit 0
      ;;
  esac
done

# ---------- helpers ----------

is_windows() { [[ "${OS:-}" == "Windows_NT" ]] || uname -s 2>/dev/null | grep -qi mingw; }

color() {
  # arg1 = color name (red/green/yellow/cyan), arg2 = text
  local c="$1" t="$2" r
  if [[ -z "${NO_COLOR:-}" ]] && command -v tput >/dev/null 2>&1 && [[ -t 1 ]]; then
    case "$c" in
      red) r="$(tput setaf 1)" ;;
      green) r="$(tput setaf 2)" ;;
      yellow) r="$(tput setaf 3)" ;;
      cyan) r="$(tput setaf 6)" ;;
      bold) r="$(tput bold)" ;;
      reset) r="$(tput sgr0)" ;;
      *) r="" ;;
    esac
    printf '%s%s%s' "$r" "$t" "$(tput sgr0 2>/dev/null || true)"
  else
    printf '%s' "$t"
  fi
}

# Resolve PID(s) listening on a TCP port. Cross-platform.
pid_on_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti ":$port" -sTCP:LISTEN 2>/dev/null | head -5
  elif is_windows; then
    # netstat -ano output columns: Proto Local-Address Foreign-Address State PID
    # Local-Address ends with :PORT for IPv4; IPv6 uses [::]:PORT. Grep both.
    netstat -ano 2>/dev/null \
      | awk -v p=":$port" '$2 ~ p"$" && $4 == "LISTENING" {print $5}' \
      | head -5
  else
    # Linux fallback (no lsof): /proc/net/tcp* parse, but fuser is common enough
    fuser -n tcp "$port" 2>/dev/null | tr -s ' ' '\n' | grep -E '^[0-9]+$' | head -5
  fi
}

# Resolve process command line from PID. /proc on Linux, wmic on Windows.
pid_cmdline() {
  local pid="$1"
  if [[ -z "$pid" ]]; then return; fi
  if [[ -r "/proc/$pid/cmdline" ]]; then
    tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | head -c 200
  elif is_windows && command -v wmic >/dev/null 2>&1; then
    wmic process where "ProcessId=$pid" get CommandLine /value 2>/dev/null \
      | tr -d '\r' | sed -n 's/^CommandLine=//p' | head -c 200
  elif is_windows && command -v powershell >/dev/null 2>&1; then
    powershell -NoProfile -Command \
      "(Get-CimInstance Win32_Process -Filter 'ProcessId=$pid').CommandLine" 2>/dev/null \
      | head -c 200
  fi
}

# Tag a PID with a label: pm2 / preview / unknown.
pid_owner() {
  local pid="$1"
  local cmd
  cmd="$(pid_cmdline "$pid")"
  if [[ "$cmd" == *pm2* || "$cmd" == *PM2* ]]; then
    printf 'pm2'
  elif [[ "$cmd" == *vite* ]]; then
    printf 'preview/vite'
  elif [[ "$cmd" == *spring-boot* || "$cmd" == *java* ]]; then
    printf 'java/spring'
  else
    printf 'unknown'
  fi
}

# Pretty print a single port's status.
port_status_line() {
  local idx="$1"
  local port="${PORTS[$idx]}"
  local label="${LABELS[$idx]#*:}"
  local pids pid owner
  pids="$(pid_on_port "$port" | tr '\n' ',' | sed 's/,$//')"
  if [[ -z "$pids" ]]; then
    printf '  %s  %-7s %-30s  %s\n' \
      "$(color green '[ FREE ]')" "$port" "$label" "(no listener)"
    return
  fi
  # First PID is enough for the owner label; mention others.
  pid="${pids%%,*}"
  owner="$(pid_owner "$pid")"
  if [[ "$owner" == "pm2" ]]; then
    printf '  %s  %-7s %-30s  pid=%s (%s) [+%s]\n' \
      "$(color green '[USED ]')" "$port" "$label" "$pid" "$owner" "$pids"
  elif [[ "$owner" == "preview/vite" ]]; then
    printf '  %s  %-7s %-30s  pid=%s (%s) [+%s]\n' \
      "$(color cyan '[USED ]')" "$port" "$label" "$pid" "$owner" "$pids"
  elif [[ "$owner" == "java/spring" ]]; then
    printf '  %s  %-7s %-30s  pid=%s (%s) [+%s]\n' \
      "$(color yellow '[USED ]')" "$port" "$label" "$pid" "$owner" "$pids"
  else
    printf '  %s  %-7s %-30s  pid=%s (%s) [+%s]\n' \
      "$(color yellow '[USED ]')" "$port" "$label" "$pid" "$owner" "$pids"
  fi
}

# Collect port status as JSON array (one element per port).
port_status_json() {
  local first=true
  printf '['
  for idx in "${!PORTS[@]}"; do
    local port="${PORTS[$idx]}" label="${LABELS[$idx]#*:}" pids pid owner
    pids="$(pid_on_port "$port" | tr '\n' ',' | sed 's/,$//')"
    if [[ -n "$pids" ]]; then
      pid="${pids%%,*}"
      owner="$(pid_owner "$pid")"
      printf '%s{"port":%s,"label":"%s","pids":"%s","owner":"%s"}' \
        "$([[ $first == true ]] && printf '' || printf ',')" \
        "$port" "$label" "$pids" "$owner"
    else
      printf '%s{"port":%s,"label":"%s","pids":"","owner":"free"}' \
        "$([[ $first == true ]] && printf '' || printf ',')" \
        "$port" "$label"
    fi
    first=false
  done
  printf ']'
}

# PM2 health check.
pm2_health() {
  if ! command -v pm2 >/dev/null 2>&1; then
    printf '%s pm2 not on PATH\n' "$(color yellow '[SKIP]')"
    return 1
  fi

  local raw
  raw="$(pm2 jlist 2>/dev/null || true)"
  if [[ -z "$raw" || "$raw" == "[]" ]]; then
    printf '%s no PM2 apps registered\n' "$(color yellow '[WARN]')"
    return 0
  fi

  # Greppable summary: name, status, restarts, pid
  printf '%s PM2 apps:\n' "$(color cyan '[INFO]')"
  local parsed
  if command -v node >/dev/null 2>&1; then
    parsed="$(pm2 jlist 2>/dev/null | node -e "
let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
  let apps=[];
  try{apps=JSON.parse(s||'[]');}catch(e){process.exit(0);}
  for(const x of apps){
    const n=x.name||'?';
    const st=(x.pm2_env&&x.pm2_env.status)||'?';
    const r=(x.pm2_env&&x.pm2_env.restart_time)||0;
    const p=x.pid||0;
    console.log('  '+n.padEnd(24)+' status='+st.padEnd(10)+' restarts='+String(r).padStart(4)+' pid='+p);
  }
});" 2>/dev/null || true)"
    if [[ -n "$parsed" ]]; then
      printf '%s\n' "$parsed"
    else
      printf '  %s\n' '(unable to parse pm2 jlist)'
    fi
  else
    printf '  %s\n' '(node not on PATH; cannot pretty-print)'
  fi

  # Heuristic: high restart count = instability
  if command -v node >/dev/null 2>&1; then
    local warns
    warns="$(pm2 jlist 2>/dev/null | node -e "
let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
  let apps=[];
  try{apps=JSON.parse(s||'[]');}catch(e){process.exit(0);}
  for(const x of apps){
    const n=x.name||'?';
    const r=(x.pm2_env&&x.pm2_env.restart_time)||0;
    if(r>=10) console.log('  WARN '+n+' has '+r+' restarts - likely env stale or infra not ready');
  }
});" 2>/dev/null || true)"
    [[ -n "$warns" ]] && printf '%s\n' "$warns"
  fi
}

# Infrastructure (Docker) readiness summary.
running_compose_service_container() {
  local service="$1"
  docker ps -q --filter "label=com.docker.compose.service=$service" | sed -n '1p'
}

infra_health() {
  if ! command -v docker >/dev/null 2>&1; then
    printf '%s docker not on PATH\n' "$(color yellow '[SKIP]')"
    return 0
  fi
  printf '%s infrastructure services:\n' "$(color cyan '[INFO]')"
  local service container h
  for service in mysql redis nacos; do
    container="$(running_compose_service_container "$service")"
    if [[ -z "$container" ]]; then
      printf '  %s  %s (absent)\n' "$(color yellow '[WARN]')" "$service"
      continue
    fi
    h="$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || echo 'absent')"
    case "$h" in
      healthy)   printf '  %s  %s (healthy)\n'   "$(color green '[ OK ]')" "$service" ;;
      running)   printf '  %s  %s (running, no healthcheck)\n' "$(color green '[ OK ]')" "$service" ;;
      starting)  printf '  %s  %s (starting)\n' "$(color yellow '[WAIT]')" "$service" ;;
      unhealthy) printf '  %s  %s (unhealthy)\n' "$(color red '[FAIL]')" "$service" ;;
      exited)    printf '  %s  %s (exited)\n'    "$(color red '[FAIL]')" "$service" ;;
      *)         printf '  %s  %s (%s)\n'       "$(color yellow '[WARN]')" "$service" "$h" ;;
    esac
  done
}

# Heuristic recommendation.
recommend() {
  local ports_busy=0
  local ports_pm2=0
  local ports_preview=0
  local pm2_has_apps=false
  local infra_unhealthy=false
  local idx p pids pid own

  for idx in "${!PORTS[@]}"; do
    p="${PORTS[$idx]}" pids="" pid="" own=""
    pids="$(pid_on_port "$p" | tr '\n' ',' | sed 's/,$//')"
    if [[ -n "$pids" ]]; then
      ports_busy=$((ports_busy + 1))
      pid="${pids%%,*}"
      own="$(pid_owner "$pid")"
      if [[ "$own" == "pm2" ]]; then
        ports_pm2=$((ports_pm2 + 1))
      fi
      if [[ "$own" == "preview/vite" ]]; then
        ports_preview=$((ports_preview + 1))
      fi
    fi
  done

  if command -v pm2 >/dev/null 2>&1; then
    local jl
    jl="$(pm2 jlist 2>/dev/null || true)"
    if [[ -n "$jl" && "$jl" != "[]" ]]; then
      pm2_has_apps=true
    fi
  fi

  if command -v docker >/dev/null 2>&1; then
    local h container
    container="$(running_compose_service_container mysql)"
    h="$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || true)"
    if [[ "$h" == "unhealthy" || "$h" == "exited" ]]; then
      infra_unhealthy=true
    fi
  fi

  printf '\n%s recommendation:\n' "$(color bold '>>>')"

  if [[ "$infra_unhealthy" == true ]]; then
    printf '  %s infrastructure is unhealthy. Fix it first:\n' "$(color red '!!')"
    printf '      %s\n' './scripts/dev/up.sh --skip-install'
    return
  fi

  if [[ "$pm2_has_apps" == true && "$ports_pm2" -ge 1 ]]; then
    printf '  %s PM2 is owning the ports (mode A active). Use PM2:\n' "$(color green 'OK')"
    printf '      pm2 status                   # see what is up\n'
    printf '      pm2 logs ulticode-auth     # auth backend logs\n'
    printf '      pm2 logs ulticode-admin    # admin backend logs\n'
    printf '      pm2 logs ulticode-app       # app backend logs\n'
    printf '      open http://localhost:9002   # console\n'
    return
  fi

  if [[ "$ports_preview" -ge 1 ]]; then
    printf '  %s A Claude Preview process is running (mode B active). Stay in Preview:\n' "$(color green 'OK')"
    printf '      Use preview_snapshot / preview_click via MCP\n'
    return
  fi

  if [[ "$pm2_has_apps" == false && "$ports_busy" -eq 0 ]]; then
    printf '  %s nothing is running. Pick a mode:\n' "$(color yellow '??')"
    printf '      %s\n' 'A) ./scripts/dev/up.sh --mode dev-lite  # canonical long-running dev'
    printf '      %s\n' '   ./scripts/dev/up.sh --mode dev-full  # explicit Search/cutover mode'
    printf '      %s\n' 'B) preview_start ulticode-9002-console  # ephemeral UI session'
    return
  fi

  if [[ "$ports_busy" -gt 0 && "$ports_pm2" -eq 0 && "$ports_preview" -eq 0 ]]; then
    printf '  %s ports are held by %d unknown process(es). Investigate before starting more:\n' "$(color yellow 'WARN')" "$ports_busy"
    printf '      %s\n' 'lsof -i :9101   # or netstat -ano on Windows'
  fi
}

# ---------- main ----------

if $JSON_ONLY; then
  ports_json="$(port_status_json)"
  pm2_json="$(pm2 jlist 2>/dev/null || echo '[]')"
  cat <<EOF
{"ports":$ports_json,"pm2":$pm2_json}
EOF
  exit 0
fi

if ! $PM2_ONLY; then
  printf '%s port occupancy\n' "$(color bold '=== Port occupancy ===')"
  for idx in "${!PORTS[@]}"; do port_status_line "$idx"; done
  if $PORTS_ONLY; then exit 0; fi
fi

if ! $PORTS_ONLY; then
  printf '\n%s PM2 health\n' "$(color bold '=== PM2 health ===')"
  pm2_health
  printf '\n%s Infrastructure\n' "$(color bold '=== Infrastructure ===')"
  infra_health
fi

if ! $QUIET; then
  recommend
fi

# Exit code: 0 = all listeners belong to PM2 or are free; 1 = port held by unknown
if ! $PORTS_ONLY && ! $PM2_ONLY; then
  for idx in "${!PORTS[@]}"; do
    p="${PORTS[$idx]}"
    pids="$(pid_on_port "$p" | tr '\n' ',' | sed 's/,$//')"
    if [[ -n "$pids" ]]; then
      pid="${pids%%,*}"
      own="$(pid_owner "$pid")"
      if [[ "$own" == "unknown" ]]; then
        exit 1
      fi
    fi
  done
fi
exit 0
