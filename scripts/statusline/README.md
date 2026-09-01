# UltiCode Statusline

Claude Code `statusLine` config for UltiCode. Shows — left to right — the current model, working directory, git branch (with dirty / ahead / behind markers), PM2 service status, Arthas MCP reachability, and a 10-char context-window progress bar with percentage.

**Source**: [`statusline.sh`](./statusline.sh). Registration is a user-level Claude Code setting; this repository ships the source but does not assume a `.claude/settings.json` file.

## Color design: "Solarized Project Sync"

The statusline uses 256-color SGR codes that map directly to the project's design tokens defined in [`GARDEN_DESIGN_SPEC.md`](../../packages/design-system/docs/GARDEN_DESIGN_SPEC.md). The statusline and the Console UI share the same color language — when you see green/amber/red here, you see the same hues in the problem list, submission status, contest state, etc.

| Token | oklch | SGR | Where it appears |
|-------|-------|-----|------------------|
| `accent-electric` | 244.9° | `38;5;33` | git branch + symbol |
| `terminal-cyan` | 187.4° | `38;5;39` | model name |
| `terminal-green` | 118.6° | `38;5;76` | PM2 `●` online · Arthas `●` online · ctx bar `< 65%` |
| `terminal-amber` | 85.7° | `38;5;214` | PM2 `◐` launching · ctx bar `65–84%` · dirty `*` · ahead `↑` |
| `terminal-red` | 27.1° | `38;5;160` | PM2 `○` stopped/errored · Arthas `○` offline · ctx bar `≥ 85%` · behind `↓` |
| `silver-*` | gray | `38;5;245` | cwd · separator `·` · field labels (`pm2:`, `arthas:`, `ctx:`) |

Honors `NO_COLOR` (https://no-color.org) — when set, all 256-color SGR sequences collapse to empty strings and the line is rendered as plain ASCII.

### Why this design over alternatives

- **vs. Nord / Tokyo Night / Mono+Signal**: those palettes are nice in isolation, but they introduce new hues (`#150` frost green, `#213` magenta, `#179` cool amber) that don't exist in the project's design system. Statusline would feel like a foreign accent in an otherwise consistent UI.
- **vs. raw 16-color ANSI** (`\033[32m` etc.): 16-color terminals are too constrained — there's no "amber" and no "dim" — and the resulting greens are harsher than `oklch(0.6444 0.1508 118.6)`. 256-color mode is supported by every modern terminal since 2015.

## Layout

```
claude-opus-4-8 ·./ ⎇ main ↑2 · pm2: 3/3 ●●● · arthas: ● · ctx: ████░░░░░░ 43%
└─ model ─┘ └─ cwd ─┘ └──── git ────┘ └───── pm2 (9001/9002/9003) ─────┘ └ arthas ┘ └──── ctx ────┘
```

`⎇` is U+2387 (alternative key symbol). `●`/`○`/`◐` are U+25CF/25CB/25D0. The progress bar uses U+2588 (filled) and U+2591 (light shade). Set `NO_COLOR=1` to disable ANSI and get plain ASCII.

## Ctx-bar thresholds

| Range | Color | Bar state |
|-------|-------|-----------|
| 0–64% | green (terminal-green) | normal |
| 65–84% | amber (terminal-amber) | warning |
| 85–100% | red (terminal-red) | critical |

## Dependencies

| Tool | Required for | Fallback |
|------|--------------|----------|
| `bash` ≥ 4 | process substitution `<(...)` | — (script aborts) |
| `jq` ≥ 1.6 | clean PM2 + JSON parsing | `grep`/`sed` regex fallback for top-level fields |
| `git` | branch / dirty / ahead-behind | segment hidden |
| `pm2` | service status | segment hidden |
| `lsof` | arthas port probe | segment hidden |
| `awk` | ahead/behind counters | upstream check hidden |

All five external tools are best-effort. The script **never aborts** on a missing dependency — segments are silently dropped, the rest renders normally.

## Updating / extending

The script is self-contained in one file. To add a new segment:

1. Add a `SEG_X=""` variable in the appropriate numbered block.
2. Append `${SEG_X:+${SEP}}${SEG_X}` to the final `LINE=` line.
3. Use ANSI color constants defined at the top (`C_DIM`, `C_RED`, etc.) for consistency.

## Design variants

[`design-demo.sh`](./design-demo.sh) renders the same statusline under 4 different color schemes for comparison. The chosen scheme is "A · Solarized Project Sync". The other three (Nord Frost, Tokyo Night, Mono + Signal) are kept in the demo as a reference — they are not active.

```bash
bash scripts/statusline/design-demo.sh           # all 4
bash scripts/statusline/design-demo.sh solarized # one
```

## Security notes

- The PM2 query is intentionally **tight**: `select(.name|test(...))` + only `.name` and `.pm2_env.status` are projected. `.pm2_env.env` (which contains `DB_PASSWORD`, `JWT_SECRET`, `NACOS_PASSWORD`, etc.) and `.pm2_env.args` are never read into shell variables. Do not weaken the projection.
- The statusline runs on every Claude Code tick. If you add any subprocess that handles untrusted input, sanitize first.

## Disabling

Disable the `statusLine` entry in your user-level Claude Code settings and restart Claude Code. The default Claude Code statusline (model only) takes over.
