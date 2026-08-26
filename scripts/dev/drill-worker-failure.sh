#!/usr/bin/env bash
set -euo pipefail

# drill-worker-failure.sh — Search / Notification worker failure drill (dev PM2 stack).
#
# Flow (never destructive in --dry-run):
#   baseline (XLEN / XINFO GROUPS / XPENDING via redis-cli) → pm2 stop → observe stall → pm2 restart → observe recovery
# Actuator curl is not used — the workers expose no HTTP surface in dev; Redis stream ops are the source of truth.
#
# Usage:
#   ./scripts/dev/drill-worker-failure.sh [--dry-run] [--app ulticode-search|ulticode-notification] [--yes]
#   Default is --dry-run (prints actions only). Pass --yes to actually stop/restart the PM2 app.

APP="ulticode-search"
DRY_RUN=true
ASSUME_YES=false

usage() {
  cat <<'USAGE'
Usage: drill-worker-failure.sh [--app ulticode-search|ulticode-notification] [--dry-run] [--yes]

Options:
  --app <name>   PM2 app to drill. Allowed: ulticode-search (default), ulticode-notification.
  --dry-run      Only print actions (default).
  --yes          Actually execute pm2 stop/restart. Without this, the script stays dry.
  -h, --help     Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --app)
      APP="${2:-}"; shift 2 ;;
    --dry-run)
      DRY_RUN=true; shift ;;
    --yes)
      ASSUME_YES=true; DRY_RUN=false; shift ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ "$APP" != "ulticode-search" && "$APP" != "ulticode-notification" ]]; then
  echo "Invalid --app: $APP (allowed: ulticode-search, ulticode-notification)" >&2
  exit 2
fi

# Resolve repo root from this script's location even when invoked via symlink.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Worktree may not carry .env (git worktree shares .git but not untracked files).
# Probe in order: $ENV_FILE, $ROOT_DIR/.env, git common dir's worktree parent, main checkout.
if [[ -z "${ENV_FILE:-}" ]]; then
  if [[ -f "$ROOT_DIR/.env" ]]; then
    ENV_FILE="$ROOT_DIR/.env"
    export ENV_FILE
  else
    GIT_COMMON="$(git rev-parse --git-common-dir 2>/dev/null || true)"
    if [[ -n "$GIT_COMMON" ]]; then
      GIT_COMMON_DIR="$(cd "$GIT_COMMON" 2>/dev/null && pwd || true)"
      PARENT_CANDIDATE="$(cd "$GIT_COMMON_DIR/.." 2>/dev/null && pwd || true)"
      if [[ -n "$PARENT_CANDIDATE" && -f "$PARENT_CANDIDATE/.env" ]]; then
        ENV_FILE="$PARENT_CANDIDATE/.env"
        export ENV_FILE
      fi
    fi
  fi
fi

# Load REDIS_* and per-owner passwords the same way up.sh does — via the frozen
# helpers in scripts/dev/lib/env.sh (no hardcoded creds). If .env is absent,
# fall back to env already exported (e.g. CI).
if [[ -f "$ROOT_DIR/scripts/dev/lib/common.sh" ]]; then
  # shellcheck source=scripts/dev/lib/common.sh
  source "$ROOT_DIR/scripts/dev/lib/common.sh"
  # common.sh's env.sh may have reset ENV_FILE to ROOT_DIR/.env (non-existent in worktree).
  # If that file does not exist but the main checkout's .env does, pin ENV_FILE to it before loading.
  if [[ ! -f "${ENV_FILE:-}" ]]; then
    GIT_COMMON2="$(git rev-parse --git-common-dir 2>/dev/null || true)"
    if [[ -n "$GIT_COMMON2" ]]; then
      GIT_COMMON_DIR2="$(cd "$GIT_COMMON2" 2>/dev/null && pwd || true)"
      PARENT_CAND2="$(cd "$GIT_COMMON_DIR2/.." 2>/dev/null && pwd || true)"
      if [[ -n "$PARENT_CAND2" && -f "$PARENT_CAND2/.env" ]]; then
        ENV_FILE="$PARENT_CAND2/.env"
        export ENV_FILE
      fi
    fi
  fi
  if [[ -f "${ENV_FILE:-}" ]]; then
    load_env_file || true
  fi
elif [[ -f "${ENV_FILE:-}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-26379}"

# Per-app Redis ACL mapping — mirrors ecosystem.config.cjs
if [[ "$APP" == "ulticode-search" ]]; then
  REDIS_USER="${REDIS_USERNAME:-ulticode-search}"
  REDIS_PASS="${SEARCH_REDIS_PASSWORD:-${REDIS_PASSWORD:-}}"
  STREAM_KEY="${SEARCH_WORKER_STREAM_KEY:-stream:integration}"
  GROUP_NAME="${SEARCH_WORKER_GROUP:-search-worker}"
else
  REDIS_USER="${REDIS_USERNAME:-ulticode-notification}"
  REDIS_PASS="${NOTIFICATION_REDIS_PASSWORD:-${REDIS_PASSWORD:-}}"
  STREAM_KEY="stream:integration"
  GROUP_NAME="App-Notification"
fi

need_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    return 1
  fi
}

# Dependency checks — fail fast with actionable message.
missing=0
need_cmd pm2 || missing=1
# redis-cli may be via docker exec fallback later; check that path too
if ! command -v redis-cli >/dev/null 2>&1 && ! command -v docker >/dev/null 2>&1; then
  echo "Missing redis-cli and docker (need one to query Redis)" >&2
  missing=1
fi
if [[ $missing -ne 0 ]]; then
  exit 1
fi

# Build a redis call. Prefer native redis-cli; fallback to docker exec <redis>.
# We never echo the password on stdout; it is passed via env/arg in a single exec.
redis_exec() {
  if command -v redis-cli >/dev/null 2>&1; then
    local args=(-h "$REDIS_HOST" -p "$REDIS_PORT")
    if [[ -n "${REDIS_USER:-}" ]]; then
      args+=(--user "$REDIS_USER")
    fi
    if [[ -n "${REDIS_PASS:-}" ]]; then
      args+=(-a "$REDIS_PASS")
    fi
    args+=(--no-auth-warning)
    redis-cli "${args[@]}" "$@"
  else
    # Fallback: try the compose redis service (best-effort)
    local cname
    cname="$(docker ps --filter name=redis --format '{{.Names}}' | head -n1 || true)"
    if [[ -z "$cname" ]]; then
      echo "No redis container found for docker fallback" >&2
      return 1
    fi
    if [[ -n "${REDIS_USER:-}" && -n "${REDIS_PASS:-}" ]]; then
      docker exec "$cname" redis-cli --user "$REDIS_USER" -a "$REDIS_PASS" --no-auth-warning "$@"
    elif [[ -n "${REDIS_PASS:-}" ]]; then
      docker exec "$cname" redis-cli -a "$REDIS_PASS" --no-auth-warning "$@"
    else
      docker exec "$cname" redis-cli "$@"
    fi
  fi
}

observe() {
  local label="$1"
  echo "== [$label] app=$APP stream=$STREAM_KEY group=$GROUP_NAME =="
  echo "-- XLEN --"
  redis_exec XLEN "$STREAM_KEY" || echo "(XLEN unavailable)"
  echo "-- XINFO GROUPS --"
  redis_exec XINFO GROUPS "$STREAM_KEY" || echo "(XINFO GROUPS unavailable)"
  echo "-- XPENDING summary --"
  redis_exec XPENDING "$STREAM_KEY" "$GROUP_NAME" || echo "(XPENDING unavailable)"
  echo "-- PEL oldest (XPENDING range 1) --"
  # Show oldest pending entry id if any (portable across redis-cli versions)
  redis_exec XPENDING "$STREAM_KEY" "$GROUP_NAME" - + 1 || true
  echo
}

describe_pm2() {
  echo "== pm2 describe $APP =="
  pm2 describe "$APP" 2>&1 | head -n 120 || pm2 status 2>&1 | head -n 40
  echo
}

run_or_echo() {
  if [[ "$DRY_RUN" == true ]]; then
    echo "[dry-run] would run: $*"
  else
    echo "+ $*"
    "$@"
  fi
}

echo "Worker failure drill — app=$APP dry-run=$DRY_RUN"
echo "Redis: $REDIS_HOST:$REDIS_PORT user=${REDIS_USER:-<none>} stream=$STREAM_KEY group=$GROUP_NAME"
echo

if [[ "$DRY_RUN" == true ]]; then
  echo "DRY RUN — no service will be stopped. Pass --yes to execute."
  echo
fi

# 1. Baseline
echo "--- Step 1: baseline ---"
describe_pm2
observe "baseline"

# 2. Stop
echo "--- Step 2: stop worker ---"
if [[ "$DRY_RUN" == true ]]; then
  run_or_echo pm2 stop "$APP"
else
  if [[ "$ASSUME_YES" != true ]]; then
    echo "Refusing to stop without --yes" >&2; exit 2
  fi
  run_or_echo pm2 stop "$APP"
  echo "Waiting 3s for PM2 to settle..."
  sleep 3
  describe_pm2
fi

# 3. Observe stall (lag should not drain while stopped)
echo "--- Step 3: observe stall (worker stopped) ---"
observe "stalled"

if [[ "$DRY_RUN" == false ]]; then
  echo "Tip: publish a test event via the App outbox or check existing lag growth for ~10s."
  sleep 2
  observe "stalled+2s"
fi

# 4. Restart
echo "--- Step 4: restart worker ---"
run_or_echo pm2 restart "$APP"

if [[ "$DRY_RUN" == false ]]; then
  echo "Waiting 8s for worker to reclaim (search claim 30s, notification reclaim 30s, judge reaper 30m — observe PEL drain)..."
  sleep 8
  describe_pm2
fi

# 5. Observe recovery
echo "--- Step 5: observe recovery ---"
observe "recovered"

if [[ "$DRY_RUN" == true ]]; then
  echo "Dry-run complete. Re-run with --yes to perform the stop/restart drill."
  echo "Inspect manually: pm2 logs $APP --lines 50"
else
  echo "Drill complete for $APP. Verify: lag draining, PEL oldest age decreasing, last_success advancing."
  echo "Metrics: curl -s http://localhost:9107/actuator/prometheus | grep -E 'search_worker|notification_inbox' (search) or check PM2 logs."
fi
