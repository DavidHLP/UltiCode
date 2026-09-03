#!/usr/bin/env bash
# scripts/dev/doctor.sh — manifest-driven DevStack process/port health check.
#
# Read-only: this script never starts, stops, restarts, or mutates a service.
# Scope selection, PM2 app names, infra targets, readiness kinds, and ports all
# come from scripts/dev/devstack-manifest.sh.
#
# Usage:
#   scripts/dev/doctor.sh                         # default dev-lite scope
#   scripts/dev/doctor.sh --scope full-stack      # all nine PM2 apps + Meili
#   scripts/dev/doctor.sh --scope app-journey
#   scripts/dev/doctor.sh --json --scope full-stack
#   scripts/dev/doctor.sh --ports | --pm2 | --quiet

set -u
set -o pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/dev/devstack-manifest.sh
source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"

SCOPE="dev-lite"
SCOPE_EXPLICIT=false
MODE=""
ONLY=""
INSPECT_ALL=false
OBSERVABILITY=false
JSON_ONLY=false
PORTS_ONLY=false
PM2_ONLY=false
QUIET=false

usage() {
  sed -n '1,18p' "$0"
  cat <<'EOF'

Options:
  --scope <name>       named DevStack scope (default: dev-lite)
  --mode <dev-lite|dev-full>
                       deprecated compatibility alias for --scope
  --only <apps>        inspect a validated subset of the selected scope
  --all                inspect all nine PM2 apps and all local infra
  --observability      include the explicit observability Compose profile
  --json               emit secret-free machine-readable output
  --ports              show only selected port occupancy
  --pm2                show only selected PM2 health
  --quiet, -q          suppress the recommendation section
  -h, --help           show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --scope) SCOPE="${2:-}"; SCOPE_EXPLICIT=true; shift 2 ;;
    --mode) MODE="${2:-}"; shift 2 ;;
    --only) ONLY="${2:-}"; shift 2 ;;
    --all) INSPECT_ALL=true; shift ;;
    --observability) OBSERVABILITY=true; shift ;;
    --json) JSON_ONLY=true; shift ;;
    --ports) PORTS_ONLY=true; shift ;;
    --pm2) PM2_ONLY=true; shift ;;
    --quiet|-q) QUIET=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -n "$MODE" ]]; then
  devstack_validate_mode_name "$MODE"
  if [[ "$SCOPE_EXPLICIT" == true ]]; then
    devstack_validate_scope_name "$SCOPE"
    scope_mode="$(devstack_mode_for_scope "$SCOPE")"
    [[ "$scope_mode" == "$MODE" ]] || {
      echo "--mode $MODE conflicts with --scope $SCOPE; use one selector." >&2
      exit 2
    }
  else
    scope_mode="$(devstack_scope_for_mode "$MODE")"
    SCOPE="$scope_mode"
  fi
  echo "[DEPRECATED] --mode is accepted for compatibility; prefer --scope $scope_mode." >&2
fi

if [[ "$INSPECT_ALL" == true && -n "$ONLY" ]]; then
  echo "--all cannot be combined with --only." >&2
  exit 2
fi

devstack_validate_scope_name "$SCOPE"
if [[ "$INSPECT_ALL" == true ]]; then
  SCOPE="full-stack"
  SELECTED_APPS="$(devstack_apps_csv "${DEVSTACK_ALL_APPS[@]}")"
elif [[ -n "$ONLY" ]]; then
  SELECTED_APPS="$(devstack_normalize_apps "$ONLY")"
  devstack_validate_scope_selection "$SCOPE" "$SELECTED_APPS"
else
  SELECTED_APPS="$(devstack_scope_apps "$SCOPE")"
fi

INFRA_TARGETS="$(devstack_infra_for_selection "$SCOPE" "$SELECTED_APPS" "$OBSERVABILITY")"
mapfile -t PORT_RECORDS < <(devstack_ports_for_selection "$SCOPE" "$SELECTED_APPS")

# ---------- helpers ----------

is_windows() {
  [[ "${OS:-}" == "Windows_NT" ]] || uname -s 2>/dev/null | grep -qi mingw
}

color() {
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

pid_on_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti ":$port" -sTCP:LISTEN 2>/dev/null | sed -n '1,5p'
  elif is_windows; then
    command -v netstat >/dev/null 2>&1 || return 0
    netstat -ano 2>/dev/null \
      | awk -v p=":$port" '$2 ~ p"$" && $4 == "LISTENING" {print $5}' \
      | sed -n '1,5p'
  elif command -v fuser >/dev/null 2>&1; then
    fuser -n tcp "$port" 2>/dev/null | tr -s ' ' '\n' | grep -E '^[0-9]+$' | sed -n '1,5p'
  fi
}

pid_cmdline() {
  local pid="$1"
  [[ -n "$pid" ]] || return 0
  if [[ -r "/proc/$pid/cmdline" ]]; then
    tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | cut -c1-200
  elif is_windows && command -v wmic >/dev/null 2>&1; then
    wmic process where "ProcessId=$pid" get CommandLine /value 2>/dev/null \
      | tr -d '\r' | sed -n 's/^CommandLine=//p' | cut -c1-200
  elif is_windows && command -v powershell >/dev/null 2>&1; then
    powershell -NoProfile -Command \
      "(Get-CimInstance Win32_Process -Filter 'ProcessId=$pid').CommandLine" 2>/dev/null \
      | cut -c1-200
  fi
}

pid_owner() {
  local pid="$1" cmd
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

running_compose_service_container() {
  local service="$1"
  command -v docker >/dev/null 2>&1 || return 0
  docker ps -q --filter "label=com.docker.compose.service=$service" | sed -n '1p'
}

compose_health() {
  local service="$1" container
  container="$(running_compose_service_container "$service")"
  [[ -n "$container" ]] || { printf 'absent'; return; }
  docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
    "$container" 2>/dev/null || printf 'unknown'
}

load_pm2_records() {
  PM2_RECORDS=""
  if ! command -v pm2 >/dev/null 2>&1; then
    return 0
  fi
  local raw
  raw="$(pm2 jlist 2>/dev/null || true)"
  [[ -n "$raw" && "$raw" != "[]" ]] || return 0
  if command -v node >/dev/null 2>&1; then
    PM2_RECORDS="$(printf '%s' "$raw" | node -e '
let s="";
process.stdin.on("data", d => s += d).on("end", () => {
  let apps = [];
  try { apps = JSON.parse(s || "[]"); } catch (_) { process.exit(0); }
  for (const item of apps) {
    const env = item.pm2_env || {};
    const name = String(item.name || "").replaceAll("|", "_");
    const status = String(env.status || "unknown").replaceAll("|", "_");
    const restarts = Number.isFinite(Number(env.unstable_restarts)) ? Number(env.unstable_restarts) : 0;
    const pid = Number.isFinite(Number(item.pid)) ? Number(item.pid) : 0;
    if (name) console.log([name, status, restarts, pid].join("|"));
  }
});' 2>/dev/null || true)"
  fi
}

pm2_record_for() {
  local wanted="$1" name status restarts pid
  while IFS='|' read -r name status restarts pid; do
    [[ "$name" == "$wanted" ]] || continue
    printf '%s|%s|%s|%s' "$name" "$status" "$restarts" "$pid"
    return 0
  done <<< "$PM2_RECORDS"
  if command -v pm2 >/dev/null 2>&1 && pm2 describe "$wanted" >/dev/null 2>&1; then
    printf '%s|unknown|0|%s' "$wanted" "$(pm2 pid "$wanted" 2>/dev/null || printf '0')"
  fi
}

port_status_line() {
  local record="$1" app port label pids pid owner
  IFS='|' read -r app port label <<< "$record"
  pids="$(pid_on_port "$port" | tr '\n' ',' | sed 's/,$//')"
  if [[ -z "$pids" ]]; then
    printf '  %s  %-7s %-30s  %s\n' \
      "$(color green '[ FREE ]')" "$port" "$label" '(no listener)'
    return 0
  fi
  pid="${pids%%,*}"
  owner="$(pid_owner "$pid")"
  printf '  %s  %-7s %-30s  pid=%s (%s) [+%s]\n' \
    "$(color green '[USED ]')" "$port" "$label" "$pid" "$owner" "$pids"
}

json_string() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/}"
  printf '"%s"' "$value"
}

port_status_json() {
  local first=true record app port label pids pid owner
  printf '['
  for record in "${PORT_RECORDS[@]}"; do
    IFS='|' read -r app port label <<< "$record"
    pids="$(pid_on_port "$port" | tr '\n' ',' | sed 's/,$//')"
    owner='free'
    if [[ -n "$pids" ]]; then
      pid="${pids%%,*}"
      owner="$(pid_owner "$pid")"
    fi
    [[ "$first" == true ]] || printf ','
    printf '{"app":%s,"port":%s,"label":%s,"pids":%s,"owner":%s}' \
      "$(json_string "$app")" "$port" "$(json_string "$label")" \
      "$(json_string "$pids")" "$(json_string "$owner")"
    first=false
  done
  printf ']'
}

infra_status_json() {
  local first=true service status
  printf '['
  local IFS=,
  for service in $INFRA_TARGETS; do
    status="$(compose_health "$service")"
    [[ "$first" == true ]] || printf ','
    printf '{"service":%s,"status":%s}' "$(json_string "$service")" "$(json_string "$status")"
    first=false
  done
  printf ']'
}

pm2_status_json() {
  local first=true app record name status restarts pid
  local IFS=,
  printf '['
  for app in $SELECTED_APPS; do
    record="$(pm2_record_for "$app")"
    if [[ -n "$record" ]]; then
      IFS='|' read -r name status restarts pid <<< "$record"
    else
      name="$app" status='absent' restarts=0 pid=0
    fi
    [[ "$first" == true ]] || printf ','
    printf '{"name":%s,"port":%s,"status":%s,"restarts":%s,"pid":%s}' \
      "$(json_string "$name")" "$(devstack_app_port "$app")" \
      "$(json_string "$status")" "$restarts" "$pid"
    first=false
  done
  printf ']'
}

pm2_health() {
  local app record name status restarts pid
  if ! command -v pm2 >/dev/null 2>&1; then
    printf '%s pm2 not on PATH\n' "$(color yellow '[SKIP]')"
    return 0
  fi
  load_pm2_records
  printf '%s PM2 apps (scope: %s):\n' "$(color cyan '[INFO]')" "$SCOPE"
  local IFS=,
  for app in $SELECTED_APPS; do
    record="$(pm2_record_for "$app")"
    if [[ -z "$record" ]]; then
      printf '  %-24s status=%-10s port=%s\n' "$app" 'absent' "$(devstack_app_port "$app")"
      continue
    fi
    IFS='|' read -r name status restarts pid <<< "$record"
    printf '  %-24s status=%-10s port=%-5s restarts=%4s pid=%s\n' \
      "$name" "$status" "$(devstack_app_port "$app")" "$restarts" "$pid"
  done
}

infra_health() {
  if ! command -v docker >/dev/null 2>&1; then
    printf '%s docker not on PATH\n' "$(color yellow '[SKIP]')"
    return 0
  fi
  printf '%s infrastructure services (scope: %s):\n' "$(color cyan '[INFO]')" "$SCOPE"
  local service status
  local IFS=,
  for service in $INFRA_TARGETS; do
    status="$(compose_health "$service")"
    case "$status" in
      healthy)   printf '  %s  %s (healthy)\n'   "$(color green '[ OK ]')" "$service" ;;
      running)   printf '  %s  %s (running, no healthcheck)\n' "$(color green '[ OK ]')" "$service" ;;
      starting)  printf '  %s  %s (starting)\n' "$(color yellow '[WAIT]')" "$service" ;;
      unhealthy) printf '  %s  %s (unhealthy)\n' "$(color red '[FAIL]')" "$service" ;;
      exited)    printf '  %s  %s (exited)\n' "$(color red '[FAIL]')" "$service" ;;
      absent)    printf '  %s  %s (absent)\n' "$(color yellow '[WARN]')" "$service" ;;
      *)         printf '  %s  %s (%s)\n' "$(color yellow '[WARN]')" "$service" "$status" ;;
    esac
  done
}

recommend() {
  local ports_busy=0 ports_pm2=0 ports_preview=0 pm2_has_apps=false infra_unhealthy=false
  local record app port pids pid owner service status
  for record in "${PORT_RECORDS[@]}"; do
    IFS='|' read -r app port _ <<< "$record"
    pids="$(pid_on_port "$port" | sed -n '1p')"
    [[ -z "$pids" ]] && continue
    ports_busy=$((ports_busy + 1))
    pid="$pids"
    owner="$(pid_owner "$pid")"
    [[ "$owner" == pm2 ]] && ports_pm2=$((ports_pm2 + 1))
    [[ "$owner" == preview/vite ]] && ports_preview=$((ports_preview + 1))
  done
  if command -v pm2 >/dev/null 2>&1; then
    load_pm2_records
    local -a selected_apps=()
    local IFS=','
    read -ra selected_apps <<< "$SELECTED_APPS"
    for app in "${selected_apps[@]}"; do
      [[ -n "$(pm2_record_for "$app")" ]] && pm2_has_apps=true
    done
  fi
  local IFS=,
  for service in $INFRA_TARGETS; do
    status="$(compose_health "$service")"
    [[ "$status" == unhealthy || "$status" == exited ]] && infra_unhealthy=true
  done

  printf '\n%s recommendation (scope: %s):\n' "$(color bold '>>>')" "$SCOPE"
  if [[ "$infra_unhealthy" == true ]]; then
    printf '  %s selected infrastructure is unhealthy; inspect the service above.\n' "$(color red '!!')"
  elif [[ "$pm2_has_apps" == true && "$ports_pm2" -ge 1 ]]; then
    printf '  %s selected PM2 services own at least one configured port.\n' "$(color green 'OK')"
  elif [[ "$ports_preview" -ge 1 ]]; then
    printf '  %s a Preview/Vite process owns a selected port.\n' "$(color green 'OK')"
  elif [[ "$pm2_has_apps" == false && "$ports_busy" -eq 0 ]]; then
    printf '  %s nothing is listening for this scope; run up.sh --scope %s.\n' "$(color yellow '??')" "$SCOPE"
  elif [[ "$ports_busy" -gt 0 && "$ports_pm2" -eq 0 && "$ports_preview" -eq 0 ]]; then
    printf '  %s selected ports are held by unknown processes; investigate before starting.\n' "$(color yellow 'WARN')"
  fi
}

# ---------- main ----------

if [[ "$JSON_ONLY" == true ]]; then
  load_pm2_records
  printf '{"scope":%s,"apps":%s,"ports":%s,"infra":%s,"features":%s}\n' \
    "$(json_string "$SCOPE")" "$(pm2_status_json)" "$(port_status_json)" \
    "$(infra_status_json)" "$(json_string "$(devstack_scope_features "$SCOPE")")"
  exit 0
fi

if [[ "$PM2_ONLY" != true ]]; then
  printf '%s port occupancy (scope: %s)\n' "$(color bold '===')" "$SCOPE"
  for record in "${PORT_RECORDS[@]}"; do port_status_line "$record"; done
  [[ "$PORTS_ONLY" == true ]] && exit 0
fi

if [[ "$PORTS_ONLY" != true ]]; then
  printf '\n%s PM2 health\n' "$(color bold '===')"
  pm2_health
  printf '\n%s Infrastructure\n' "$(color bold '===')"
  infra_health
fi

[[ "$QUIET" == true ]] || recommend

# Exit code: unknown ownership on a selected port is unhealthy. PM2, Java,
# Vite, free, and absent infrastructure are all observable states, not errors.
if [[ "$PORTS_ONLY" != true && "$PM2_ONLY" != true ]]; then
  for record in "${PORT_RECORDS[@]}"; do
    IFS='|' read -r _app port _label <<< "$record"
    pids="$(pid_on_port "$port" | sed -n '1p')"
    [[ -z "$pids" ]] && continue
    [[ "$(pid_owner "$pids")" == unknown ]] && exit 1
  done
fi
exit 0
