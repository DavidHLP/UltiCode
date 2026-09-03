#!/usr/bin/env bash
set -euo pipefail

# DevStack stop path. PM2 is the process owner, and the manifest is the one
# source of truth for the selected scope. Shared infra is stopped only when no
# process outside the selected scope still depends on it; --all is explicit.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/dev/devstack-manifest.sh
source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"

ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
SCOPE="dev-lite"
SCOPE_EXPLICIT=false
MODE=""
ONLY=""
STOP_ALL=false

usage() {
  cat <<'EOF'
Usage: ./scripts/dev/stop.sh [options]

Stop only the PM2 and infrastructure services resolved for a scope.

Options:
  --scope <name>       named DevStack scope (default: dev-lite)
  --mode <dev-lite|dev-full>
                       deprecated compatibility alias for --scope
  --only <apps>        stop a validated subset of the selected scope
  --all                stop every known PM2 app and all local infra targets
  -h, --help           show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --scope) SCOPE="${2:-}"; SCOPE_EXPLICIT=true; shift 2 ;;
    --mode) MODE="${2:-}"; shift 2 ;;
    --only) ONLY="${2:-}"; shift 2 ;;
    --all) STOP_ALL=true; shift ;;
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

if [[ "$STOP_ALL" == true && -n "$ONLY" ]]; then
  echo "--all cannot be combined with --only." >&2
  exit 2
fi

devstack_validate_scope_name "$SCOPE"
if [[ "$STOP_ALL" == true ]]; then
  SELECTED_APPS="$(devstack_apps_csv "${DEVSTACK_ALL_APPS[@]}")"
else
  if [[ -n "$ONLY" ]]; then
    SELECTED_APPS="$(devstack_normalize_apps "$ONLY")"
  else
    SELECTED_APPS="$(devstack_scope_apps "$SCOPE")"
  fi
  devstack_validate_scope_selection "$SCOPE" "$SELECTED_APPS"
fi

contains_app() {
  [[ ",$1," == *",$2,"* ]]
}

stopped=()
if command -v pm2 >/dev/null 2>&1; then
  IFS=',' read -ra selected_array <<< "$SELECTED_APPS"
  for app in "${selected_array[@]}"; do
    # pm2 describe exits non-zero when the app is not registered; treat that
    # as "not running" rather than a stop failure.
    if pm2 describe "$app" >/dev/null 2>&1; then
      echo "  Stopping [$app]..."
      # Keep this explicit marker: the architecture contract requires PM2
      # deletion to remain the process cleanup primitive.
      if pm2 delete "$app" >/dev/null 2>&1; then
        stopped+=("$app")
      else
        echo "    Failed to delete [$app]; check 'pm2 ls'." >&2
      fi
    fi
  done
else
  echo "pm2 not found; no PM2 services to stop." >&2
fi

# Fallback: scoped PID files from the pre-DevStack launcher, if any remain.
PID_DIR="$ROOT_DIR/logs/.pids"
if [[ -d "$PID_DIR" ]]; then
  shopt -s nullglob
  for pidfile in "$PID_DIR"/*.pid; do
    name="$(basename "$pidfile" .pid)"
    normalized="$(devstack_normalize_app "$name" 2>/dev/null || true)"
    if [[ "$STOP_ALL" != true ]] && ! contains_app "$SELECTED_APPS" "$normalized"; then
      continue
    fi
    pid="$(<"$pidfile")"
    if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
      echo "  Stopping legacy PID entry [$name] (PID: $pid)..."
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$pidfile"
  done
  shopt -u nullglob
fi

# Compose infra is shared by scopes. Never stop it while another selected-out
# PM2 app is registered; --all intentionally overrides that protection.
stop_infra=false
if [[ "$STOP_ALL" == true ]]; then
  stop_infra=true
elif command -v pm2 >/dev/null 2>&1 || [[ -d "$PID_DIR" ]]; then
  stop_infra=true
  IFS=',' read -ra all_apps <<< "$(devstack_apps_csv "${DEVSTACK_ALL_APPS[@]}")"
  for app in "${all_apps[@]}"; do
    contains_app "$SELECTED_APPS" "$app" && continue
    if pm2 describe "$app" >/dev/null 2>&1; then
      stop_infra=false
      break
    fi
  done
  if [[ "$stop_infra" == true && -d "$PID_DIR" ]]; then
    for pidfile in "$PID_DIR"/*.pid; do
      [[ -e "$pidfile" ]] || continue
      name="$(basename "$pidfile" .pid)"
      normalized="$(devstack_normalize_app "$name" 2>/dev/null || true)"
      contains_app "$SELECTED_APPS" "$normalized" && continue
      pid="$(<"$pidfile")"
      if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
        stop_infra=false
        break
      fi
    done
  fi
fi

if [[ "$stop_infra" == true && -x "$(command -v docker 2>/dev/null || true)" && -f "$ENV_FILE" ]]; then
  if [[ "$STOP_ALL" == true ]]; then
    infra_targets="mysql,redis,nacos,meilisearch,otel-collector,prometheus,alertmanager,tempo,loki,grafana"
  else
    infra_targets="$(devstack_infra_for_selection "$SCOPE" "$SELECTED_APPS")"
  fi
  if [[ -n "$infra_targets" ]]; then
    compose=(docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.yml" -f "$ROOT_DIR/docker-compose.dev.yml")
    if [[ "$STOP_ALL" == true ]]; then
      compose+=(--profile observability -f "$ROOT_DIR/docker-compose.observability.yml")
    fi
    IFS=',' read -ra infra_array <<< "$infra_targets"
    echo "Stopping infrastructure targets: $infra_targets"
    "${compose[@]}" stop "${infra_array[@]}" >/dev/null 2>&1 || true
  fi
fi

if [[ "${#stopped[@]}" -eq 0 ]]; then
  echo "No selected DevStack PM2 services running (scope: $SCOPE)."
else
  echo "Stopped: ${stopped[*]}"
fi
echo "UltiCode stopped (scope: $SCOPE)."
