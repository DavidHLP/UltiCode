#!/usr/bin/env bash
# UltiCode Claude Code statusline
#
# Color design: "Solarized Project Sync" — matches the project's design
# system tokens (see .claude/skills/solarized-terminal-design-style/SKILL.md).
# Mapping oklch → 256-color SGR:
#   --accent-electric  oklch(0.6149 0.1394 244.9)  → SGR 38;5;33  (electric blue)
#   --terminal-cyan    oklch(0.6437 0.1019 187.4)  → SGR 38;5;39  (info)
#   --terminal-green   oklch(0.6444 0.1508 118.6)  → SGR 38;5;76  (success)
#   --terminal-amber   oklch(0.6545 0.1340  85.7)  → SGR 38;5;214 (warning)
#   --terminal-red     oklch(0.5863 0.2064  27.1)  → SGR 38;5;160 (error)
#   --silver-*         grayscale gradient         → SGR 38;5;245 (dim)
#
# Reads JSON session info from stdin (Claude Code pipes the session payload).
# All segments are independently fault-tolerant — missing tools (jq, pm2, git,
# arthas port) produce a graceful "n/a" segment instead of failing the whole
# line. Honors NO_COLOR (https://no-color.org).
set -uo pipefail

# ---------- 1. Read stdin (Claude Code session JSON) -------------------------
STDIN_JSON="$(cat 2>/dev/null || true)"

# ---------- 2. Color constants (256-color SGR, project tokens) ----------------
# 256-color mode is universally supported by all modern terminals. The
# values map to the project's oklch design tokens — see header comment.
#
# Color is OFF only when NO_COLOR is set (per https://no-color.org).
# We deliberately do NOT check [ -t 1 ] here: Claude Code invokes the
# statusline via pipe (stdout is not a TTY from the script's POV), but the
# whole point of a statusline is to render colors. Users who want plain
# output can set NO_COLOR=1.
if [ -n "${NO_COLOR:-}" ]; then
  C_RESET=""; C_DIM=""
  C_CYAN=""; C_BLUE=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_BOLD=""
else
  C_RESET=$'\033[0m'
  C_DIM=$'\033[38;5;245m'
  C_CYAN=$'\033[38;5;39m'     # terminal-cyan    (model)
  C_BLUE=$'\033[38;5;33m'     # accent-electric  (git)
  C_GREEN=$'\033[38;5;76m'    # terminal-green   (online / ctx-low)
  C_YELLOW=$'\033[38;5;214m'  # terminal-amber   (warning / ctx-mid / ahead)
  C_RED=$'\033[38;5;160m'     # terminal-red     (offline / ctx-high)
  C_BOLD=$'\033[1m'
fi

# ---------- 3. JSON extractors (jq with grep fallback) -----------------------
get_json() {
  # $1 = key path, $2 = fallback
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

# ---------- 4. Segment: model (terminal-cyan) --------------------------------
MODEL="$(get_json '.model.display_name' '')"
if [ -z "$MODEL" ]; then
  MODEL="$(get_json '.model.id' '')"
fi
[ -z "$MODEL" ] && MODEL="claude"
SEG_MODEL="${C_CYAN}${MODEL}${C_RESET}"

# ---------- 5. Segment: cwd (dim, tilde-compressed for display only) ---------
CWD="$(get_json '.workspace.current_dir' '')"
[ -z "$CWD" ] && CWD="$(get_json '.cwd' '')"
[ -z "$CWD" ] && CWD="$(pwd)"
# Tilde-compress ONLY for display; keep absolute path for tool calls below
# (bash does not expand "~" inside double quotes — use "git -C \"relative/path\"" instead).
case "$CWD" in
  "$HOME"*) SEG_CWD="${C_DIM}~${CWD#"$HOME"}${C_RESET}" ;;
  *)         SEG_CWD="${C_DIM}${CWD}${C_RESET}" ;;
esac

# ---------- 6. Segment: git (accent-electric + amber for ahead/behind) ------
SEG_GIT=""
if command -v git >/dev/null 2>&1 && [ -d "$CWD" ]; then
  BRANCH="$(git -C "$CWD" --no-optional-locks symbolic-ref --short HEAD 2>/dev/null || true)"
  if [ -z "$BRANCH" ]; then
    BRANCH="$(git -C "$CWD" --no-optional-locks rev-parse --short HEAD 2>/dev/null || true)"
    [ -n "$BRANCH" ] && BRANCH=":${BRANCH}"
  fi
  if [ -n "$BRANCH" ]; then
    AHEAD_BEHIND=""
    if AB="$(git -C "$CWD" --no-optional-locks rev-list --left-right --count @{u}...HEAD 2>/dev/null)"; then
      AHEAD="$(echo "$AB"  | awk '{print $2}')"
      BEHIND="$(echo "$AB" | awk '{print $1}')"
      [ "${AHEAD:-0}"   -gt 0 ] && AHEAD_BEHIND+="${C_GREEN}↑${AHEAD}${C_RESET}"
      [ "${BEHIND:-0}"  -gt 0 ] && AHEAD_BEHIND+="${C_RED}↓${BEHIND}${C_RESET}"
    fi
    SEG_GIT=" ${C_BLUE}⎇${C_RESET} ${C_BLUE}${BRANCH}${C_RESET}${AHEAD_BEHIND:+ ${AHEAD_BEHIND}}"
  fi
fi

# ---------- 6b. Segment: git diffstat (added/modified/deleted/untracked) ----
# Shape of uncommitted changes at a glance: +N added · ~N modified · -N deleted
# · ?N untracked. Counts combine staged + unstaged (porcelain XY, either
# position). Drops silently when git is missing or the tree is clean.
SEG_DIFF=""
if command -v git >/dev/null 2>&1 && [ -d "$CWD" ] \
   && PORCELAIN="$(git -C "$CWD" --no-optional-locks status --porcelain 2>/dev/null)" \
   && [ -n "$PORCELAIN" ]; then
  # One awk pass → "added modified deleted untracked".
  counts=$(printf '%s\n' "$PORCELAIN" | awk '
    /^\?\?/ { u++; next }
    { x = substr($0,1,1); y = substr($0,2,1)
      if (x ~ /[ARC]/ || y ~ /[ARC]/) a++
      if (x == "M" || y == "M")       m++
      if (x == "D" || y == "D")       d++ }
    END { printf "%d %d %d %d", a+0, m+0, d+0, u+0 }')
  set -- $counts
  ds=""
  [ "$1" -gt 0 ] 2>/dev/null && ds="${ds}${C_GREEN}+$1${C_RESET} "
  [ "$2" -gt 0 ] 2>/dev/null && ds="${ds}${C_BLUE}~$2${C_RESET} "
  [ "$3" -gt 0 ] 2>/dev/null && ds="${ds}${C_RED}-$3${C_RESET} "
  [ "$4" -gt 0 ] 2>/dev/null && ds="${ds}${C_YELLOW}?$4${C_RESET}"
  ds="${ds% }"
  # Line-level insertions/deletions vs HEAD (tracked files only; untracked
  # ?? files have no diff baseline so git excludes them — the ?N file count
  # above still surfaces them). --shortstat → "N files changed, X insertions(+),
  # Y deletions(-)".
  SHORTSTAT="$(git -C "$CWD" --no-optional-locks diff --shortstat HEAD 2>/dev/null || true)"
  INS=$(printf '%s' "$SHORTSTAT" | grep -oE '[0-9]+ insertion' | grep -oE '[0-9]+')
  DEL=$(printf '%s' "$SHORTSTAT" | grep -oE '[0-9]+ deletion' | grep -oE '[0-9]+')
  ld=""
  [ "${INS:-0}" -gt 0 ] 2>/dev/null && ld="${ld}${C_GREEN}↑${INS}${C_RESET}"
  [ "${DEL:-0}" -gt 0 ] 2>/dev/null && ld="${ld}${ld:+${C_DIM}/${C_RESET}}${C_RED}↓${DEL}${C_RESET}"
  [ -n "$ld" ] && ds="${ds} ${C_DIM}|${C_RESET} ${ld}"
  [ -n "$ds" ] && SEG_DIFF=" ${C_DIM}Δ${C_RESET} ${ds}"
fi

# ---------- 7. Segment: PM2 (green/red/amber dots) ---------------------------
SEG_PM2=""
if command -v pm2 >/dev/null 2>&1; then
  PM2_JSON="$(pm2 jlist 2>/dev/null || true)"
  if [ -n "$PM2_JSON" ] && command -v jq >/dev/null 2>&1; then
    declare -A ST
    while IFS=$'\t' read -r name status; do
      [ -n "$name" ] && ST["$name"]="$status"
    done < <(echo "$PM2_JSON" | jq -r '.[] | select(.name|test("^(ulticode-auth|ulticode-admin|ulticode-app|ulticode-(9002|9003))$")) | "\(.name)\t\(.pm2_env.status)"' 2>/dev/null)
    # SECURITY: `pm2 jlist` contains .pm2_env.env (DB_PASSWORD etc.) and
    # .pm2_env.args. The jq projection above selects ONLY .name and
    # .pm2_env.status. Do not weaken the projection.
    if [ "${#ST[@]}" -gt 0 ]; then
      PM2_DOTS=""
      ONLINE=0
      TOTAL=0
      for svc in ulticode-auth ulticode-admin ulticode-app ulticode-9002 ulticode-9003; do
        TOTAL=$((TOTAL+1))
        st="${ST[$svc]:-missing}"
        case "$st" in
          online)    PM2_DOTS+="${C_GREEN}●${C_RESET}"; ONLINE=$((ONLINE+1)) ;;
          launching) PM2_DOTS+="${C_YELLOW}◐${C_RESET}" ;;
          stopped|errored) PM2_DOTS+="${C_RED}○${C_RESET}" ;;
          *)         PM2_DOTS+="${C_DIM}?${C_RESET}" ;;
        esac
      done
      SEG_PM2=" ${C_DIM}pm2:${C_RESET} ${C_GREEN}${ONLINE}${C_RESET}/${TOTAL} ${PM2_DOTS}"
    fi
  fi
fi

# ---------- 8. Segment: Arthas MCP (green/red on :8563) ---------------------
SEG_ARTHAS=""
if command -v lsof >/dev/null 2>&1; then
  if lsof -ti :8563 >/dev/null 2>&1; then
    SEG_ARTHAS=" ${C_DIM}arthas:${C_RESET} ${C_GREEN}●${C_RESET}"
  else
    SEG_ARTHAS=" ${C_DIM}arthas:${C_RESET} ${C_RED}○${C_RESET}"
  fi
fi

# ---------- 9. Segment: context % + bar (green→amber→red) -------------------
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
  [ "$PCT" -lt 0 ] 2>/dev/null && PCT=0
  [ "$PCT" -gt 100 ] 2>/dev/null && PCT=100
  FILLED=$(( PCT / 10 ))
  [ "$FILLED" -gt 10 ] && FILLED=10
  EMPTY=$(( 10 - FILLED ))
  BAR=""
  for _ in $(seq 1 "$FILLED");  do BAR+="█"; done
  for _ in $(seq 1 "$EMPTY");   do BAR+="░"; done
  # Threshold: 65%+ amber, 85%+ red (project's standard warning/error levels)
  if   [ "$PCT" -ge 85 ]; then BAR_COLOR="$C_RED"
  elif [ "$PCT" -ge 65 ]; then BAR_COLOR="$C_YELLOW"
  else                          BAR_COLOR="$C_GREEN"
  fi
  SEG_CTX=" ${C_DIM}ctx:${C_RESET} ${BAR_COLOR}${BAR}${C_RESET} ${PCT}%"
fi

# ---------- 10. Compose (dim " · " separator) -------------------------------
SEP="${C_DIM} ·${C_RESET}"
LINE="${SEG_MODEL}${SEP}${SEG_CWD}${SEG_GIT}${SEG_DIFF}${SEG_PM2:+${SEP}}${SEG_PM2}${SEG_ARTHAS:+${SEP}}${SEG_ARTHAS}${SEG_CTX:+${SEP}}${SEG_CTX}"

printf '%b\n' "$LINE"
