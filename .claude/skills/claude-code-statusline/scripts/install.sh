#!/usr/bin/env bash
# Install Claude Code statusline into a project.
#
# Usage:  bash install.sh [project-dir]
#         STATUSLINE_PM2_SERVICES="api,worker" \
#         STATUSLINE_PORT_PROBES="redis:6379,debug:5005" \
#         bash install.sh /path/to/project
#
# What it does:
#   1. Copies the statusline template to <project>/scripts/statusline/statusline.sh
#   2. Creates/merges <project>/.claude/settings.json with the statusLine entry
#   3. Updates <project>/.gitignore to ignore .claude/settings.local.json
#   4. Prints verification commands
#
# This is intentionally a "v1" installer — it copies a flat script and edits
# JSON with python. For complex projects with existing settings.json, hand-
# edit using the SKILL.md pattern.

set -euo pipefail

PROJECT_DIR="${1:-$(pwd)}"
SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TEMPLATE="${SCRIPT_DIR}/assets/statusline.template.sh"

if [ ! -f "$TEMPLATE" ]; then
  echo "ERROR: template not found at $TEMPLATE" >&2
  exit 1
fi

# Resolve project dir to absolute path
PROJECT_DIR="$(cd "$PROJECT_DIR" && pwd)"

# ---------- 1. Copy script -------------------------------------------------
TARGET_SCRIPT="${PROJECT_DIR}/scripts/statusline/statusline.sh"
mkdir -p "$(dirname "$TARGET_SCRIPT")"
cp "$TEMPLATE" "$TARGET_SCRIPT"
chmod +x "$TARGET_SCRIPT"
echo "✓ Copied: $TARGET_SCRIPT"

# ---------- 2. settings.json: create or merge -----------------------------
SETTINGS="${PROJECT_DIR}/.claude/settings.json"
mkdir -p "$(dirname "$SETTINGS")"

if [ ! -f "$SETTINGS" ]; then
  cat > "$SETTINGS" <<'JSON'
{
  "statusLine": {
    "type": "command",
    "command": "bash ${CLAUDE_PROJECT_DIR}/scripts/statusline/statusline.sh"
  }
}
JSON
  echo "✓ Created: $SETTINGS"
else
  # Merge with existing settings.json using python (avoids jq dep).
  python3 - <<PY
import json, sys, pathlib
p = pathlib.Path("$SETTINGS")
data = json.loads(p.read_text()) if p.stat().st_size > 0 else {}
data["statusLine"] = {
  "type": "command",
  "command": "bash \${CLAUDE_PROJECT_DIR}/scripts/statusline/statusline.sh"
}
p.write_text(json.dumps(data, indent=2) + "\n")
print("✓ Merged statusLine into existing: $SETTINGS")
PY
fi

# ---------- 3. .gitignore contract ----------------------------------------
GITIGNORE="${PROJECT_DIR}/.gitignore"
LINE1=".claude/settings.local.json"
LINE2=".claude/.statusline/"
touch "$GITIGNORE"
for entry in "$LINE1" "$LINE2"; do
  if ! grep -qxF "$entry" "$GITIGNORE" 2>/dev/null; then
    printf '%s\n' "$entry" >> "$GITIGNORE"
    echo "✓ Added '$entry' to .gitignore"
  fi
done

# ---------- 4. Verification commands --------------------------------------
cat <<'NEXT'

✓ Statusline installed. Verify with:

  # JSON validity
  python3 -c "import json; json.load(open('.claude/settings.json'))"

  # Script syntax
  bash -n scripts/statusline/statusline.sh

  # Sample render (should show ANSI escapes when piped to cat -v)
  echo '{"model":{"display_name":"test"},"workspace":{"current_dir":"'"$(pwd)"'"},"context_window":{"used_percentage":43}}' \\
    | bash scripts/statusline/statusline.sh | cat -v

  # NO_COLOR path (should be plain ASCII)
  NO_COLOR=1 bash -c 'echo "..." | bash scripts/statusline/statusline.sh' | cat -v

NEXT

# ---------- 5. Configuration reminder -------------------------------------
if [ -n "${STATUSLINE_PM2_SERVICES:-}" ] || [ -n "${STATUSLINE_PORT_PROBES:-}" ]; then
  cat <<CFG

✓ Custom configuration detected. To apply per-project, set in
  .claude/settings.local.json under "env":
    "STATUSLINE_PM2_SERVICES": "${STATUSLINE_PM2_SERVICES:-<defaults>}",
    "STATUSLINE_PORT_PROBES":  "${STATUSLINE_PORT_PROBES:-<defaults>}",
    "STATUSLINE_CTX_WARN":     "${STATUSLINE_CTX_WARN:-65}",
    "STATUSLINE_CTX_CRIT":     "${STATUSLINE_CTX_CRIT:-85}",
    "STATUSLINE_COLOR_PROFILE":"${STATUSLINE_COLOR_PROFILE:-solarized}"

CFG
fi
