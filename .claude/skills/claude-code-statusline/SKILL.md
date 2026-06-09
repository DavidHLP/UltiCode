---
name: claude-code-statusline
description: >
  Set up, customize, or debug a Claude Code `statusLine`. This skill covers
  the full pattern: project-level `.claude/settings.json` (committed) vs
  `.claude/settings.local.json` (gitignored) split, the 5-segment layout
  (model · cwd · git · project-services · context bar), the `oklch` → 256-color
  SGR mapping that matches the Solarized Project Sync design system, the
  TTY-strip bug that silently removes colors when stdout is piped (the
  symptom is "my statusline has no colors"), the PM2 jq query that must
  NOT project `.pm2_env.env` (DB_PASSWORD leak vector), and the .gitignore
  contract for statusline runtime artifacts. Use when the user asks to set
  up a statusline, status bar, customize statusline colors, add a new
  segment, or debug a missing/broken statusline.
metadata:
  type: reference
---

# Claude Code Statusline

The full pattern for a portable, team-shared Claude Code statusline — works
for any project, not just UltiCode. Distilled from a real implementation
in `UltiCode/scripts/statusline/`.

## TL;DR — Install in a new project

```bash
# 1. Copy the template
cp ${SKILL_DIR}/scripts/statusline.template.sh ./scripts/statusline/statusline.sh
chmod +x ./scripts/statusline/statusline.sh

# 2. Add the entry to .claude/settings.json (committed, team-shared)
# See "settings.json contract" below

# 3. Verify
echo '{"model":{"display_name":"test"},"workspace":{"current_dir":"'"$(pwd)"'"},"context_window":{"used_percentage":50}}' \
  | bash ./scripts/statusline/statusline.sh
```

Or run the bundled installer which does steps 1–2 with sensible prompts:

```bash
bash ${SKILL_DIR}/scripts/install.sh /path/to/project
```

## Architecture: where things go

| Asset | Path | In git? | Why |
|-------|------|--------|-----|
| `statusLine` config | `<repo>/.claude/settings.json` | **YES** | Team-shared, all clones get the same statusline for free |
| Per-user overrides | `<repo>/.claude/settings.local.json` | NO | MCP / hooks / permissions / personal prefs |
| The script | `<repo>/scripts/statusline/statusline.sh` | YES | Referenced by `settings.json` via `${CLAUDE_PROJECT_DIR}` |
| Runtime PID dir | `<repo>/.claude/.statusline/` | NO (`.gitignore`) | Optional, for stateful extensions |
| User-global config | `~/.claude/settings.json` | NO | Personal preferences (theme, vim mode) |

**The `CLAUDE_PROJECT_DIR` variable** in `settings.json` is expanded by Claude Code at load time to the repo's absolute path. This is what makes the statusline portable across clones.

```jsonc
// .claude/settings.json
{
  "statusLine": {
    "type": "command",
    "command": "bash ${CLAUDE_PROJECT_DIR}/scripts/statusline/statusline.sh"
  }
}
```

## The 5-segment pattern

```
claude-opus-4-8 ·~/project/UltiCode ⎇ main* ↑2 · pm2: 3/3 ●●● · arthas: ● · ctx: ████░░░░░░ 43%
└─ model ─┘ └─ cwd ─┘ └──── git ────┘ └── project-services ────────┘  ctx  ┘
```

Each segment is **independently fault-tolerant** — if a tool is missing
(`jq`, `pm2`, `git`, `lsof`), the segment silently drops and the rest
renders normally. The script never aborts.

### Customization via env vars

| Env var | Default | Purpose |
|---------|---------|---------|
| `STATUSLINE_PM2_SERVICES` | `ulticode-9001,ulticode-9002,ulticode-9003` | Comma-separated pm2 service names to monitor |
| `STATUSLINE_PORT_PROBES` | `arthas:8563` | `name:port` pairs to TCP-probe (uses `lsof`) |
| `STATUSLINE_CTX_WARN` | `65` | Ctx % threshold for amber |
| `STATUSLINE_CTX_CRIT` | `85` | Ctx % threshold for red |
| `STATUSLINE_COLOR_PROFILE` | `solarized` | `solarized` \| `nord` \| `tokyo` \| `mono` (see `references/color-profiles.md`) |
| `NO_COLOR` | unset | Per [no-color.org](https://no-color.org), any value disables all SGR |

Set these in `~/.claude/settings.json` under `env`, or in `.claude/settings.local.json` for project-specific overrides.

## Gotcha #1: the silent color strip

**Symptom**: statusline shows in the terminal but has no colors, even though
the script emits `\033[38;5;39m` bytes. `cat -v statusline-output` shows
`^[[38;5;...` bytes in the file but no color in the terminal.

**Cause**: Claude Code invokes the statusline command via pipe, so the
script's `stdout` (file descriptor 1) is **not a TTY** from the script's
point of view. A naive guard like `if [ -t 1 ]` will then strip colors.

**Fix**: do not gate colors on TTY status. Gate only on `NO_COLOR`:

```bash
# WRONG — strips colors under Claude Code's pipe
if [ -n "${NO_COLOR:-}" ] || [ ! -t 1 ]; then ...empty... else ...colors...; fi

# RIGHT — colors always emit unless NO_COLOR is set
if [ -n "${NO_COLOR:-}" ]; then ...empty... else ...colors...; fi
```

The no-color.org spec only defines the `NO_COLOR` env var; TTY heuristics
are inappropriate for statuslines that exist to be displayed with colors.

## Gotcha #2: the PM2 credential leak

**Symptom**: someone runs `pm2 jlist > /tmp/jlist.json`, sees all
process env vars (`DB_PASSWORD`, `JWT_SECRET`, `NACOS_PASSWORD`, ...), and
realizes your statusline was reading the same blob.

**Cause**: `pm2 jlist` returns an array where each process entry contains
`.pm2_env.env` (a string-string map of all env vars) and `.pm2_env.args`
(shell command). A statusline that does `jq -r '.[] | .pm2_env.status'`
is fine, but anything that selects `.pm2_env.env` or `.pm2_env.args` is
a leak vector — those values get echoed to the terminal **on every
statusline tick**, and end up in scrollback history, screen recordings,
and shared screenshots.

**Fix**: project *only* the fields you need. Never use a generic
`pm2 jlist` projection:

```bash
# WRONG — captures env / args
echo "$PM2_JSON" | jq -r '.[] | "\(.name)\t\(.pm2_env.status)\t\(.pm2_env.env)"'

# RIGHT — narrow projection, no env/args
echo "$PM2_JSON" | jq -r '.[] | select(.name|test("^NAME1$|^NAME2$")) | "\(.name)\t\(.pm2_env.status)"'
```

Add a SECURITY comment in the script to prevent the projection from being
weakened by future refactors.

## Gotcha #3: tilde-compressed paths break `git -C`

**Symptom**: git segment never appears, even though `git status` works in
the cwd.

**Cause**: `git -C "~/project/foo"` fails because bash does not expand
`~` inside double quotes. If you tilde-compress `CWD` for display, you
must keep a separate absolute path for tool calls.

```bash
# WRONG — tilde-compressed CWD breaks git
CWD="~/project/foo"
git -C "$CWD" status  # → "fatal: cannot change to '~/project/foo'"

# RIGHT — keep display and tool paths separate
CWD_ABS="/home/user/project/foo"
SEG_CWD="${CWD_ABS/$HOME/~}"  # display only
git -C "$CWD_ABS" status
```

## Color profile: Solarized Project Sync

The recommended default. Maps the project's `oklch` design tokens to
256-color SGR so the statusline and the project's UI share the same
hues (e.g. statusline green `38;5;76` = the same green used in
problem-list `Easy` badges).

| Project token | oklch | SGR | Used for |
|---------------|-------|-----|----------|
| `accent-electric` | 244.9° | `38;5;33` | git branch + symbol |
| `terminal-cyan` | 187.4° | `38;5;39` | model name |
| `terminal-green` | 118.6° | `38;5;76` | online / ctx-low |
| `terminal-amber` | 85.7° | `38;5;214` | warning / ctx-mid / ahead |
| `terminal-red` | 27.1° | `38;5;160` | offline / ctx-high / behind |
| `silver-*` | gray | `38;5;245` | cwd, separators, labels |

Three alternative profiles (Nord / Tokyo Night / Mono+Signal) are
documented in `references/color-profiles.md` with rationale and visual
samples.

## Ctx-bar thresholds

| Range | Color | State |
|-------|-------|-------|
| 0 – `STATUSLINE_CTX_WARN - 1` | green | normal |
| `STATUSLINE_CTX_WARN` – `STATUSLINE_CTX_CRIT - 1` | amber | warning |
| `STATUSLINE_CTX_CRIT` – 100 | red | critical |

Defaults: warn at 65%, crit at 85%. These match the project's standard
warning/error escalation levels.

## Reading the session JSON from stdin

Claude Code pipes a JSON object to the statusline command on every tick.
Key fields used by the default template:

```jsonc
{
  "model":             {"display_name": "claude-opus-4-8", "id": "..."},
  "workspace":         {"current_dir": "/abs/path"},
  "context_window":    {"used_percentage": 43},
  // Older versions used .cwd and .context_window.remaining_percentage
}
```

The template includes a `jq` primary path with a `grep`/`sed` regex
fallback for the four common fields, so the script degrades gracefully
when `jq` is missing.

## Extending: adding a new segment

1. Find the numbered block (`# ---------- 6. Segment: git ----------`).
2. Add a `SEG_X=""` block in the same style — guard the segment on the
   tool that produces it, never on the whole script.
3. Append `${SEG_X:+${SEP}}${SEG_X}` to the final `LINE=` composition.
4. Reuse the existing `C_*` color constants; only add a new one if the
   segment needs a hue that isn't in the active profile.

Example — a "docker" segment that shows whether Docker daemon is up:

```bash
# ---------- N. Segment: docker daemon --------------------------------
SEG_DOCKER=""
if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    SEG_DOCKER=" ${C_DIM}docker:${C_RESET} ${C_GREEN}●${C_RESET}"
  else
    SEG_DOCKER=" ${C_DIM}docker:${C_RESET} ${C_RED}○${C_RESET}"
  fi
fi
```

## .gitignore contract

Statusline runtime artifacts that must NOT enter the repo:

```gitignore
# Claude Code
.claude/settings.local.json
.claude/.statusline/         # statusline runtime state, if any
```

Root `.claude/settings.json` (the one with `statusLine`) **is** committed.

## Verifying before commit

```bash
# 1. JSON validity of .claude/settings.json
python3 -c "import json; json.load(open('.claude/settings.json'))"

# 2. Script syntax
bash -n scripts/statusline/statusline.sh

# 3. Sample output with colors
echo '{"model":{"display_name":"x"},"workspace":{"current_dir":"/tmp"},"context_window":{"used_percentage":50}}' \
  | bash scripts/statusline/statusline.sh | cat -v | head -1
# Expect: ^[[38;5;... sequences present

# 4. Sample output with NO_COLOR
NO_COLOR=1 bash -c 'echo "..." | bash scripts/statusline/statusline.sh' | cat -v
# Expect: NO ^[[ sequences

# 5. .gitignore contract
git check-ignore -v .claude/settings.json        # should print nothing
git check-ignore -v .claude/settings.local.json  # should print a rule
```

## When to read the references

- `references/color-profiles.md` — switching palette, palette comparison
- `references/config-options.md` — full env var reference + examples
- `assets/statusline.template.sh` — the canonical template (copied into the project)
- `assets/design-demo.sh` — 4-palette visual comparison tool
- `scripts/install.sh` — one-shot installer for a new project
