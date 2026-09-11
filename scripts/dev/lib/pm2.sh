#!/usr/bin/env bash
# scripts/dev/lib/pm2.sh — shared PM2 record adapter and readiness predicate.
#
# This module only reads PM2 state. Callers keep their own startup, display,
# and exit policies; they share the record format and its unknown semantics.

if ! [[ -v __ULTICODE_PM2_SOURCED ]]; then
  declare -gr __ULTICODE_PM2_SOURCED=1

  PM2_RECORDS=""

  pm2_load_records() {
    PM2_RECORDS=""
    command -v pm2 >/dev/null 2>&1 || return 0

    local raw parsed
    raw="$(pm2 jlist 2>/dev/null)" || return 0
    [[ -n "$raw" && "$raw" != "[]" ]] || return 0
    command -v node >/dev/null 2>&1 || return 0

    if ! parsed="$(printf '%s' "$raw" | node -e '
let source = "";
process.stdin.on("data", chunk => source += chunk).on("end", () => {
  let apps;
  try {
    apps = JSON.parse(source || "[]");
  } catch (_) {
    process.exit(1);
  }
  if (!Array.isArray(apps)) process.exit(1);
  for (const item of apps) {
    const entry = item && typeof item === "object" ? item : {};
    const env = entry.pm2_env && typeof entry.pm2_env === "object" ? entry.pm2_env : {};
    const name = String(entry.name || "").replaceAll("|", "_");
    const status = String(env.status || "unknown").replaceAll("|", "_");
    const restarts = Number.isFinite(Number(env.unstable_restarts)) ? Number(env.unstable_restarts) : "unknown";
    const pid = Number.isFinite(Number(entry.pid)) ? Number(entry.pid) : 0;
    if (name) console.log([name, status, restarts, pid].join("|"));
  }
});' 2>/dev/null)"; then
      return 0
    fi
    PM2_RECORDS="$parsed"
  }

  pm2_record_for() {
    local wanted="$1" name status restarts pid
    while IFS='|' read -r name status restarts pid; do
      [[ "$name" == "$wanted" ]] || continue
      printf '%s|%s|%s|%s' "$name" "$status" "$restarts" "$pid"
      return 0
    done <<< "$PM2_RECORDS"

    if command -v pm2 >/dev/null 2>&1 && pm2 describe "$wanted" >/dev/null 2>&1; then
      printf '%s|unknown|unknown|%s' "$wanted" "$(pm2 pid "$wanted" 2>/dev/null || printf '0')"
    fi
  }

  pm2_record_is_online() {
    local app="$1" record status restarts
    record="$(pm2_record_for "$app")"
    [[ -n "$record" ]] || return 1
    IFS='|' read -r _name status restarts _pid <<< "$record"
    [[ "$status" == "online" ]] || return 1
    [[ "$restarts" =~ ^[0-9]+$ && "$restarts" -lt 5 ]]
  }

  readonly -f pm2_load_records pm2_record_for pm2_record_is_online
fi
