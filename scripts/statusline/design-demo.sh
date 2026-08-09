#!/usr/bin/env bash
# scripts/statusline/design-demo.sh
# Render the same statusline under 4 different color designs.
# Usage: bash scripts/statusline/design-demo.sh           # all 4
#        bash scripts/statusline/design-demo.sh solarized   # one design

set -o pipefail

# Sample inputs — same data, different palettes
MODEL="claude-opus-4-8"
CWD="${DEMO_CWD:-.}"
BRANCH="main"
DIRTY=""          # set to "*" to show dirty marker
AHEAD="↑2"        # set to "" to hide
PM2_ONLINE=3
PM2_TOTAL=3       # 3/3 online; 2/3 to show mixed; 0/3 to show all stopped
ARTHAS=1          # 1 = online, 0 = offline
CTX_PCT=43

reset=$'\033[0m'

# Helper: build a 10-char progress bar
make_bar() {
  local pct="$1" color="$2" filled empty bar=""
  filled=$(( pct / 10 ))
  empty=$(( 10 - filled ))
  for _ in $(seq 1 "$filled"); do bar+="█"; done
  for _ in $(seq 1 "$empty");  do bar+="░"; done
  printf '%s%s%s' "$color" "$bar" "$reset"
}

# Helper: build a run of PM2 dots
make_pm2_dots() {
  local online_color="$1" off_color="$2" i out=""
  for i in $(seq 1 "$PM2_ONLINE"); do out+="${online_color}●${reset}"; done
  for i in $(seq $((PM2_ONLINE+1)) "$PM2_TOTAL"); do out+="${off_color}○${reset}"; done
  printf '%s' "$out"
}

design_solarized() {
  # Solarized Project Sync — match project's design-system tokens
  local cyan=$'\033[38;5;39m'        # terminal-cyan   (oklch 187.4)
  local dim=$'\033[38;5;245m'
  local blue=$'\033[38;5;33m'         # accent-electric (oklch 244.9)
  local yellow=$'\033[38;5;214m'      # terminal-amber  (oklch  85.7)
  local red=$'\033[38;5;160m'         # terminal-red    (oklch  27.1)
  local green=$'\033[38;5;76m'        # terminal-green  (oklch 118.6)

  local model="${cyan}${MODEL}${reset}"
  local cwd="${dim}${CWD}${reset}"
  local git="${blue}⎇${reset} ${blue}${BRANCH}${reset}${DIRTY:+ ${yellow}${DIRTY}${reset}}${AHEAD:+ ${yellow}${AHEAD}${reset}}"
  local pm2_label="${dim}pm2:${reset}"
  local pm2_dots
  pm2_dots="$(make_pm2_dots "$green" "$red")"
  local pm2_count="${green}${PM2_ONLINE}${reset}/${PM2_TOTAL}"
  local arthas="${dim}arthas:${reset} $([ "$ARTHAS" -eq 1 ] && printf '%s●%s' "$green" "$reset" || printf '%s○%s' "$red" "$reset")"
  local bar_color="$green"; [ "$CTX_PCT" -ge 65 ] && bar_color="$yellow"; [ "$CTX_PCT" -ge 85 ] && bar_color="$red"
  local ctx_bar; ctx_bar="$(make_bar "$CTX_PCT" "$bar_color")"
  local ctx="${dim}ctx:${reset} ${ctx_bar} ${CTX_PCT}%"

  printf '%s · %s %s · %s %s %s · %s %s\n' \
    "$model" "$cwd" "$git" "$pm2_label" "$pm2_count" "$pm2_dots" "$arthas" "$ctx"
}

design_nord() {
  # Nord Frost — all-cool palette, only errors are warm
  local model_c=$'\033[38;5;255m'      # nord6  near-white
  local dim=$'\033[38;5;244m'          # nord3  dim gray
  local frost=$'\033[38;5;111m'        # nord8  frost cyan-blue
  local frost_green=$'\033[38;5;150m'  # nord14
  local n_amber=$'\033[38;5;179m'      # nord13 (muted amber)
  local n_red=$'\033[38;5;203m'        # nord11

  local model="${model_c}${MODEL}${reset}"
  local cwd="${dim}${CWD}${reset}"
  local git="${frost}⎇${reset} ${frost}${BRANCH}${reset}${DIRTY:+ ${n_amber}${DIRTY}${reset}}${AHEAD:+ ${n_amber}${AHEAD}${reset}}"
  local pm2_label="${dim}pm2:${reset}"
  local pm2_dots; pm2_dots="$(make_pm2_dots "$frost_green" "$n_red")"
  local pm2_count="${frost_green}${PM2_ONLINE}${reset}/${PM2_TOTAL}"
  local arthas="${dim}arthas:${reset} $([ "$ARTHAS" -eq 1 ] && printf '%s●%s' "$frost_green" "$reset" || printf '%s○%s' "$n_red" "$reset")"
  local bar_color="$frost_green"; [ "$CTX_PCT" -ge 65 ] && bar_color="$n_amber"; [ "$CTX_PCT" -ge 85 ] && bar_color="$n_red"
  local ctx_bar; ctx_bar="$(make_bar "$CTX_PCT" "$bar_color")"
  local ctx="${dim}ctx:${reset} ${ctx_bar} ${CTX_PCT}%"

  printf '%s · %s %s · %s %s %s · %s %s\n' \
    "$model" "$cwd" "$git" "$pm2_label" "$pm2_count" "$pm2_dots" "$arthas" "$ctx"
}

design_tokyo() {
  # Tokyo Night — vibrant, deep purple + neon
  local purple=$'\033[38;5;141m'
  local dim=$'\033[38;5;60m'
  local blue=$'\033[38;5;111m'
  local cyan=$'\033[38;5;117m'
  local green=$'\033[38;5;114m'
  local yellow=$'\033[38;5;221m'
  local red=$'\033[38;5;203m'
  local magenta=$'\033[38;5;213m'

  local model="${purple}${MODEL}${reset}"
  local cwd="${dim}${CWD}${reset}"
  local git="${blue}⎇${reset} ${blue}${BRANCH}${reset}${DIRTY:+ ${yellow}${DIRTY}${reset}}${AHEAD:+ ${yellow}${AHEAD}${reset}}"
  local pm2_label="${dim}pm2:${reset}"
  local pm2_dots; pm2_dots="$(make_pm2_dots "$green" "$red")"
  local pm2_count="${magenta}${PM2_ONLINE}${reset}/${PM2_TOTAL}"
  local arthas="${dim}arthas:${reset} $([ "$ARTHAS" -eq 1 ] && printf '%s●%s' "$green" "$reset" || printf '%s○%s' "$red" "$reset")"
  # Bar gradient: cyan → yellow → magenta (more playful than green/yellow/red)
  local bar_color="$cyan"; [ "$CTX_PCT" -ge 65 ] && bar_color="$yellow"; [ "$CTX_PCT" -ge 85 ] && bar_color="$magenta"
  local filled; filled=$(( CTX_PCT / 10 ))
  local empty;  empty=$(( 10 - filled ))
  local bar=""
  for _ in $(seq 1 "$filled"); do bar+="▰"; done
  for _ in $(seq 1 "$empty");  do bar+="▱"; done
  local ctx="${dim}ctx:${reset} ${bar_color}${bar}${reset} ${CTX_PCT}%"

  printf '%s · %s %s · %s %s %s · %s %s\n' \
    "$model" "$cwd" "$git" "$pm2_label" "$pm2_count" "$pm2_dots" "$arthas" "$ctx"
}

design_mono() {
  # Mono + Signal — grayscale body, accent only for attention
  local model_c=$'\033[1;37m'          # bold white
  local dim=$'\033[38;5;245m'
  local white=$'\033[37m'
  local accent_yellow=$'\033[38;5;214m'  # dirty / launching only
  local accent_red=$'\033[38;5;196m'     # stopped / high ctx only

  local model="${model_c}${MODEL}${reset}"
  local cwd="${dim}${CWD}${reset}"
  local git="${white}⎇${reset} ${white}${BRANCH}${reset}${DIRTY:+ ${accent_yellow}${DIRTY}${reset}}${AHEAD:+ ${accent_yellow}${AHEAD}${reset}}"
  local pm2_label="${dim}pm2:${reset}"
  local pm2_dots; pm2_dots="$(make_pm2_dots "$white" "$dim")"
  local pm2_count="${white}${PM2_ONLINE}${reset}/${PM2_TOTAL}"
  local arthas="${dim}arthas:${reset} $([ "$ARTHAS" -eq 1 ] && printf '%s●%s' "$white" "$reset" || printf '%s○%s' "$accent_red" "$reset")"
  # Bar: white normally, only turns red at >=85%
  local bar_color="$white"; [ "$CTX_PCT" -ge 85 ] && bar_color="$accent_red"
  local ctx_bar; ctx_bar="$(make_bar "$CTX_PCT" "$bar_color")"
  local ctx="${dim}ctx:${reset} ${ctx_bar} ${CTX_PCT}%"

  printf '%s · %s %s · %s %s %s · %s %s\n' \
    "$model" "$cwd" "$git" "$pm2_label" "$pm2_count" "$pm2_dots" "$arthas" "$ctx"
}

if [ "${1:-}" = "" ]; then
  printf '\n=== A · Solarized Project Sync (matches project tokens) ===\n'
  design_solarized
  printf '\n=== B · Nord Frost (all-cool palette) ===\n'
  design_nord
  printf '\n=== C · Tokyo Night (vibrant dark) ===\n'
  design_tokyo
  printf '\n=== D · Mono + Signal (grayscale + accent on attention) ===\n'
  design_mono
  printf '\n'
else
  "design_$1" || { echo "Unknown design: $1 (try: solarized|nord|tokyo|mono)" >&2; exit 1; }
fi
