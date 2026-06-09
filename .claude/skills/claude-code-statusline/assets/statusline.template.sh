#!/usr/bin/env bash
# Claude Code statusline — portable template
# ==========================================
#
# Reads JSON session info from stdin (Claude Code pipes the session payload).
# Renders 5 segments: model · cwd · git · project-services · context bar.
# Each segment is independently fault-tolerant — missing tools (jq, pm2,
# git, lsof) silently drop that segment, the rest renders normally.
#
# COLOR PROFILE: "solarized" (default)
#   oklch → 256-color SGR mapping matches the Solarized Project Sync design
#   system. Override with STATUSLINE_COLOR_PROFILE=nord|tokyo|mono.
#
# GOTCHA #1 — TTY strip: do NOT gate on `[ ! -t 1 ]`. Claude Code invokes
# this via pipe, so stdout is not a TTY. Only NO_COLOR disables colors.
#
# GOTCHA #2 — PM2 leak: the jq query below selects ONLY .name and
# .pm2_env.status. NEVER widen the projection to include .pm2_env.env
# (.env contains DB_PASSWORD etc.) or .pm2_env.args. The SECURITY comment
# is there to prevent future refactors from weakening this.
#
# GOTCHA #3 — tilde paths: bash does not expand "~" inside double quotes.
# Use the absolute $CWD for tool calls and a separate $SEG_CWD for display.
#
# CONFIGURATION (env vars):
#   STATUSLINE_PM2_SERVICES  default "ulticode-9001,ulticode-9002,ulticode-9003"
#   STATUSLINE_PORT_PROBES   default "arthas:8563"  (name:port, comma-separated)
#   STATUSLINE_CTX_WARN      default 65
#   STATUSLINE_CTX_CRIT      default 85
#   STATUSLINE_COLOR_PROFILE default "solarized"   (solarized|nord|tokyo|mono)
#   NO_COLOR                 any value → strip all SGR
set -o pipefail

# ---------- 1. Read stdin ------------------------------------------------
STDIN_JSON="$(cat 2>/dev/null || true)"

# ---------- 2. Configuration with UltiCode-shaped defaults ---------------
PM2_SERVICES_CSV="${STATUSLINE_PM2_SERVICES:-ulticode-9001,ulticode-9002,ulticode-9003}"
PORT_PROBES_CSV="${STATUSLINE_PORT_PROBES:-arthas:8563}"
CTX_WARN="${STATUSLINE_CTX_WARN:-65}"
CTX_CRIT="${STATUSLINE_CTX_CRIT:-85}"
COLOR_PROFILE="${STATUSLINE_COLOR_PROFILE:-solarized}"

# ---------- 3. Color constants (256-color SGR) ---------------------------
# Only NO_COLOR strips SGR — do not add TTY checks (see GOTCHA #1).
if [ -n "${NO_COLOR:-}" ]; then
  C_RESET=""; C_DIM=""
  C_CYAN=""; C_BLUE=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_BOLD=""
else
  case "$COLOR_PROFILE" in
    solarized)
      # Solarized Project Sync — matches the project's oklch design tokens
      C_RESET=$'\033[0m'
      C_DIM=$'\033[38;5;245m'
      C_CYAN=$'\033[38;5;39m'     # terminal-cyan   (model)
      C_BLUE=$'\033[38;5;33m'     # accent-electric (git)
      C_GREEN=$'\033[38;5;76m'    # terminal-green  (online / ctx-low)
      C_YELLOW=$'\033[38;5;214m'  # terminal-amber  (warning / ctx-mid)
      C_RED=$'\033[38;5;160m'     # terminal-red    (offline / ctx-high)
      C_BOLD=$'\033[1m'
      ;;
    nord)
      C_RESET=$'\033[0m'
      C_DIM=$'\033[38;5;244m'
      C_CYAN=$'\033[38;5;255m'
      C_BLUE=$'\033[38;5;111m'    # nord8 frost
      C_GREEN=$'\033[38;5;150m'  # nord14
      C_YELLOW=$'\033[38;5;179m' # nord13 muted amber
      C_RED=$'\033[38;5;203m'    # nord11
      C_BOLD=$'\033[1m'
      ;;
    tokyo)
      C_RESET=$'\033[0m'
      C_DIM=$'\033[38;5;60m'
      C_CYAN=$'\033[38;5;117m'
      C_BLUE=$'\033[38;5;111m'
      C_GREEN=$'\033[38;5;114m'
      C_YELLOW=$'\033[38;5;221m'
      C_RED=$'\033[38;5;203m'
      C_MAGENTA=$'\033[38;5;213m'
      C_BOLD=$'\033[1m'
      ;;
    mono)
      C_RESET=$'\033[0m'
      C_DIM=$'\033[38;5;245m'
      C_CYAN=$'\033[1;37m'        # bold white
      C_BLUE=$'\033[37m'          # white
      C_GREEN=$'\033[37m'         # white (no semantic color)
      C_YELLOW=$'\033[38;5;214m'  # only used for dirty marker
      C_RED=$'\033[38;5;196m'     # only used for stopped / ctx>=crit
      C_BOLD=$'\033[1m'
      ;;
    *)
      echo "Unknown STATUSLINE_COLOR_PROFILE: $COLOR_PROFILE" >&2
      exit 1
      ;;
  esac
fi

# ---------- 4. JSON extractors (jq with grep fallback) -------------------
get_json() {
  # $1 = jq key path, $2 = fallback. Handles .model.display_name,
  # .context_window.used_percentage, .workspace.current_dir.
  if command -v jq >/dev/null 2>&1; then
    echo "$STDIN_JSON" | jq -r "$1 // empty" 2>/dev/null || echo "$2"
  else
    case "$1" in
      .model.display_name|.model.id)
        echo "$STDIN_JSON" | grep -oE '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/'
        ;;
      .context_window.used_percentage|.context_window.remaining_percentage)
        local k="${1##*.}"
        echo "$STDIN_JSON" | grep -oE "\"$k\"[[:space:]]*:[[:space:]]*[0-9.]+" | head -1 | grep -oE '[0-9.]+$'
        ;;
      .workspace.current_dir|.cwd)
        echo "$STDIN_JSON" | grep -oE '"current_dir"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"current_dir"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/'
        ;;
      *) echo "$2" ;;
    esac
  fi
}

# ---------- 5. Segment: model --------------------------------------------
MODEL="$(get_json '.model.display_name' '')"
[ -z "$MODEL" ] && MODEL="$(get_json '.model.id' '')"
[ -z "$MODEL" ] && MODEL="claude"
SEG_MODEL="${C_CYAN}${MODEL}${C_RESET}"

# ---------- 6. Segment: cwd (display vs tool path kept separate) ---------
CWD="$(get_json '.workspace.current_dir' '')"
[ -z "$CWD" ] && CWD="$(get_json '.cwd' '')"
[ -z "$CWD" ] && CWD="$(pwd)"
case "$CWD" in
  "$HOME"*) SEG_CWD="${C_DIM}~${CWD#"$HOME"}${C_RESET}" ;;
  *)         SEG_CWD="${C_DIM}${CWD}${C_RESET}" ;;
esac

# ---------- 7. Segment: git ---------------------------------------------
SEG_GIT=""
if command -v git >/dev/null 2>&1 && [ -d "$CWD" ]; then
  BRANCH="$(git -C "$CWD" --no-optional-locks symbolic-ref --short HEAD 2>/dev/null || true)"
  if [ -z "$BRANCH" ]; then
    BRANCH="$(git -C "$CWD" --no-optional-locks rev-parse --short HEAD 2>/dev/null || true)"
    [ -n "$BRANCH" ] && BRANCH=":${BRANCH}"
  fi
  if [ -n "$BRANCH" ]; then
    DIRTY=""
    if ! git -C "$CWD" --no-optional-locks diff --no-color --quiet HEAD -- 2>/dev/null; then
      DIRTY="${C_YELLOW}*${C_RESET}"
    fi
    AHEAD_BEHIND=""
    if AB="$(git -C "$CWD" --no-optional-locks rev-list --left-right --count @{u}...HEAD 2>/dev/null)"; then
      AHEAD="$(echo "$AB"  | awk '{print $2}')"
      BEHIND="$(echo "$AB" | awk '{print $1}')"
      [ "${AHEAD:-0}"   -gt 0 ] && AHEAD_BEHIND+="${C_GREEN}↑${AHEAD}${C_RESET}"
      [ "${BEHIND:-0}"  -gt 0 ] && AHEAD_BEHIND+="${C_RED}↓${BEHIND}${C_RESET}"
    fi
    SEG_GIT=" ${C_BLUE}⎇${C_RESET} ${C_BLUE}${BRANCH}${C_RESET}${DIRTY}${AHEAD_BEHIND:+ ${AHEAD_BEHIND}}"
  fi
fi

# ---------- 8. Segment: project services (PM2 + port probes) --------------
# Build the PM2 jq filter from the comma-separated service list.
# SECURITY: only .name and .pm2_env.status are selected. .pm2_env.env
# contains DB_PASSWORD / JWT_SECRET / etc. and MUST NOT be projected.
SEG_SERVICES=""
PM2_FILTER=""
IFS=',' read -ra SVC_LIST <<< "$PM2_SERVICES_CSV"
for svc in "${SVC_LIST[@]}"; do
  svc="$(echo "$svc" | xargs)"  # trim whitespace
  [ -z "$svc" ] && continue
  [ -n "$PM2_FILTER" ] && PM2_FILTER+="|^"
  PM2_FILTER+="^${svc}$"
done

if [ -n "$PM2_FILTER" ] && command -v pm2 >/dev/null 2>&1; then
  PM2_JSON="$(pm2 jlist 2>/dev/null || true)"
  if [ -n "$PM2_JSON" ] && command -v jq >/dev/null 2>&1; then
    # Declare before use; stays empty (not "unbound") if no rows match.
    declare -A ST=()
    while IFS=$'\t' read -r name status; do
      [ -n "$name" ] && ST["$name"]="${status:-}"
    done < <(echo "$PM2_JSON" | jq -r ".[] | select(.name|test(\"${PM2_FILTER}\")) | \"\\(.name)\\t\\(.pm2_env.status)\"" 2>/dev/null)
    if [ "${#ST[@]}" -gt 0 ]; then
      PM2_DOTS=""
      ONLINE=0
      TOTAL=0
      for svc in "${SVC_LIST[@]}"; do
        svc="$(echo "$svc" | xargs)"
        [ -z "$svc" ] && continue
        TOTAL=$((TOTAL+1))
        st="${ST[$svc]:-missing}"
        case "$st" in
          online)    PM2_DOTS+="${C_GREEN}●${C_RESET}"; ONLINE=$((ONLINE+1)) ;;
          launching) PM2_DOTS+="${C_YELLOW}◐${C_RESET}" ;;
          stopped|errored) PM2_DOTS+="${C_RED}○${C_RESET}" ;;
          *)         PM2_DOTS+="${C_DIM}?${C_RESET}" ;;
        esac
      done
      SEG_SERVICES+=" ${C_DIM}pm2:${C_RESET} ${C_GREEN}${ONLINE}${C_RESET}/${TOTAL} ${PM2_DOTS}"
    fi
  fi
fi

# Port probes: each "name:port" becomes a dot after the PM2 segment
if command -v lsof >/dev/null 2>&1; then
  IFS=',' read -ra PROBE_LIST <<< "$PORT_PROBES_CSV"
  for probe in "${PROBE_LIST[@]}"; do
    name="${probe%%:*}"
    port="${probe##*:}"
    [ -z "$name" ] || [ -z "$port" ] && continue
    if lsof -ti ":${port}" >/dev/null 2>&1; then
      SEG_SERVICES+=" ${C_DIM}${name}:${C_RESET} ${C_GREEN}●${C_RESET}"
    else
      SEG_SERVICES+=" ${C_DIM}${name}:${C_RESET} ${C_RED}○${C_RESET}"
    fi
  done
fi

# ---------- 9. Segment: context % + bar ----------------------------------
SEG_CTX=""
USED="$(get_json '.context_window.used_percentage' '')"
if [ -z "$USED" ]; then
  USED="$(get_json '.context_window.remaining_percentage' '')"
  if [ -n "$USED" ]; then
    USED="$(awk -v r="$USED" 'BEGIN{printf "%.0f", 100-r}')"
  fi
fi
if [ -n "$USED" ]; then
  PCT="${USED%.*}"
  [ "$PCT" -lt 0 ]   2>/dev/null && PCT=0
  [ "$PCT" -gt 100 ] 2>/dev/null && PCT=100
  FILLED=$(( PCT / 10 ))
  [ "$FILLED" -gt 10 ] && FILLED=10
  EMPTY=$(( 10 - FILLED ))
  BAR=""
  for _ in $(seq 1 "$FILLED");  do BAR+="█"; done
  for _ in $(seq 1 "$EMPTY");   do BAR+="░"; done
  if   [ "$PCT" -ge "$CTX_CRIT" ]; then BAR_COLOR="$C_RED"
  elif [ "$PCT" -ge "$CTX_WARN" ]; then BAR_COLOR="$C_YELLOW"
  else                                  BAR_COLOR="$C_GREEN"
  fi
  SEG_CTX=" ${C_DIM}ctx:${C_RESET} ${BAR_COLOR}${BAR}${C_RESET} ${PCT}%"
fi

# ---------- 10. Compose (dim " · " separator) ---------------------------
SEP="${C_DIM} ·${C_RESET}"
LINE="${SEG_MODEL}${SEP}${SEG_CWD}${SEG_GIT}${SEG_SERVICES:+${SEP}}${SEG_SERVICES}${SEG_CTX:+${SEP}}${SEG_CTX}"
printf '%b\n' "$LINE"
