# Color profiles

Four ready-to-use palettes selectable via `STATUSLINE_COLOR_PROFILE` env
var. The default is `solarized` (matches the project's oklch design
system). The other three are kept as drop-in alternatives.

## solarized (default)

The statusline and the project's UI share the same hues. When you see
green in the problem-list "Easy" badge, the same green appears for
PM2-online and ctx-bar-low in the statusline.

| Token | oklch | SGR |
|-------|-------|-----|
| accent-electric | 244.9° | `38;5;33` |
| terminal-cyan   | 187.4° | `38;5;39` |
| terminal-green  | 118.6° | `38;5;76` |
| terminal-amber  | 85.7°  | `38;5;214` |
| terminal-red    | 27.1°  | `38;5;160` |
| silver-*        | gray   | `38;5;245` |

**Why this profile**: zero design-language drift between statusline and
the rest of the product. The downside is mid-saturation — visually
quieter than vibrant alternatives.

## nord

All-cool palette. Greens, ambers are frost-shifted (lower saturation,
cooler hue). Only red is warm. The result is calm and easy on the eyes
for long sessions; errors stand out by being the only warm element.

| Token | SGR | Description |
|-------|-----|-------------|
| model | `38;5;255` | nord6 near-white |
| git   | `38;5;111` | nord8 frost cyan-blue |
| green | `38;5;150` | nord14 |
| amber | `38;5;179` | nord13 (muted) |
| red   | `38;5;203` | nord11 |

**When to pick**: terminal-only aesthetic, no UI binding, you stare at
the statusline for hours. Status differentiation is weaker (cool
colors don't pop against each other).

## tokyo

Vibrant. Deep purple model, electric blue git, magenta for high-ctx
(instead of red — Tokyo Night's signature). Uses a thicker bar
character (▰▱) for visual weight.

| Token | SGR |
|-------|-----|
| model | `38;5;141` (purple) |
| git   | `38;5;111` (blue) |
| green | `38;5;114` |
| amber | `38;5;221` (yellow) |
| red   | `38;5;203` |
| magenta | `38;5;213` (used at ctx>=85%) |

**When to pick**: dark background, you want the statusline to be a
visible UI element rather than a passive indicator. The downside is
mismatched color language with most Solarized-based UIs.

## mono

Grayscale body, accent only when something needs attention. The whole
point is signal-to-noise: the statusline is invisible until something
is wrong. PM2 `○` is dim gray (not red), ctx bar is white until 85%+
when the whole bar turns red.

| Token | SGR |
|-------|-----|
| model | `1;37` (bold white) |
| git   | `37` (white) |
| green | `37` (white — same as git) |
| yellow | `38;5;214` (only used for `*` dirty marker) |
| red | `38;5;196` (only used for `○` stopped, `↓` behind, ctx>=85% bar) |

**When to pick**: minimal aesthetic, you want a strong "something is
wrong" alarm, you don't want color noise competing with editor
syntax highlighting. The downside is mid-state (1/3 stopped, 68% ctx)
is less visible.

## Comparing on the same data

Same input rendered under all four profiles:

```
solarized:  claude-opus-4-8 ·~/p/UltiCode ⎇ main* ↑2 · pm2: 3/3 ●●● · ctx: ████░░░░░░ 43%
nord:       claude-opus-4-8 ·~/p/UltiCode ⎇ main* ↑2 · pm2: 3/3 ●●● · ctx: ████░░░░░░ 43%
tokyo:      claude-opus-4-8 ·~/p/UltiCode ⎇ main* ↑2 · pm2: 3/3 ●●● · ctx: ▰▰▰▰▱▱▱▱▱▱ 43%
mono:       claude-opus-4-8 ·~/p/UltiCode ⎇ main* ↑2 · pm2: 3/3 ●●● · ctx: ████░░░░░░ 43%
```

(The above is plain text — actual rendering uses the SGR colors per row.
Run `bash scripts/statusline/design-demo.sh` to see all four side by
side in your terminal.)

## Switching profiles

Per-user (applies globally): set in `~/.claude/settings.json`:

```json
{ "env": { "STATUSLINE_COLOR_PROFILE": "nord" } }
```

Per-project (overrides user): set in `.claude/settings.local.json`:

```json
{ "env": { "STATUSLINE_COLOR_PROFILE": "tokyo" } }
```

The statusline script reads the env var at every tick, so changes take
effect on the next statusline refresh — no restart needed.
