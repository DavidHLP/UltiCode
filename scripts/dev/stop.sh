#!/usr/bin/env bash
set -euo pipefail

# DevStack stop path. Mirrors scripts/dev/up.sh: PM2 is the process owner,
# so stopping means `pm2 delete` the DevStack-managed apps (and the frontend
# pair when present). Legacy logs/.pids handling is kept as a fallback for
# processes started before the DevStack-only cutover.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=scripts/dev/devstack-manifest.sh
source "$ROOT_DIR/scripts/dev/devstack-manifest.sh"

if ! command -v pm2 >/dev/null 2>&1; then
  echo "pm2 not found; nothing to stop." >&2
  exit 0
fi

stopped=()
for app in "${DEVSTACK_OWNER_APPS[@]}" "${DEVSTACK_WORKER_APPS[@]}" "${DEVSTACK_FRONTEND_APPS[@]}"; do
  # pm2 describe exits non-zero when the app is not registered; treat that
  # as "not running" rather than a stop failure.
  if pm2 describe "$app" >/dev/null 2>&1; then
    echo "  Stopping [$app]..."
    if pm2 delete "$app" >/dev/null 2>&1; then
      stopped+=("$app")
    else
      echo "    Failed to delete [$app]; check 'pm2 ls'." >&2
    fi
  fi
done

if [[ "${#stopped[@]}" -eq 0 ]]; then
  echo "No DevStack PM2 services running."
else
  echo "Stopped: ${stopped[*]}"
fi

# Fallback: PID files from the pre-DevStack launcher, if any remain.
PID_DIR="$ROOT_DIR/logs/.pids"
if [[ -d "$PID_DIR" ]]; then
  shopt -s nullglob
  for pidfile in "$PID_DIR"/*.pid; do
    name="$(basename "$pidfile" .pid)"
    pid="$(cat "$pidfile")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "  Stopping legacy PID entry [$name] (PID: $pid)..."
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$pidfile"
  done
  shopt -u nullglob
fi

echo "UltiCode stopped."
