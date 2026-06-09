# Configuration reference

All statusline behavior is controlled via environment variables. The
script reads them at every tick, so changes take effect on the next
statusline refresh.

## Where to set

| Location | Scope | Use case |
|----------|-------|----------|
| `~/.claude/settings.json` under `"env"` | User-global | Personal preference (e.g. your color profile) |
| `.claude/settings.local.json` under `"env"` | Per-project | Project-specific service list, port list |
| Inline `STATUSLINE_*=... bash statusline.sh` | One-off | Quick testing, debugging |

## Variables

### `STATUSLINE_PM2_SERVICES`

Comma-separated list of pm2 service names to monitor. The script
queries `pm2 jlist` and matches by exact name. Order is preserved in
the rendered dots.

Default: `ulticode-9001,ulticode-9002,ulticode-9003`

```bash
# Example: monitor API, web, worker
export STATUSLINE_PM2_SERVICES="api,web,worker"
```

If `pm2` is not on `PATH`, this segment silently drops. If `jq` is
also missing, the PM2 segment is hidden entirely (the jq query path
requires jq).

### `STATUSLINE_PORT_PROBES`

Comma-separated list of `name:port` pairs to TCP-probe using
`lsof -ti :<port>`. Each probe renders as `<dim>name:</dim> <green>●</green>`
(if listening) or `<dim>name:</dim> <red>○</red>` (if not).

Default: `arthas:8563`

```bash
# Example: probe multiple dev services
export STATUSLINE_PORT_PROBES="redis:6379,postgres:5432,debug:5005"
```

If `lsof` is not on `PATH`, this segment silently drops.

### `STATUSLINE_CTX_WARN`

Context-window percentage at which the progress bar switches from
green to amber. Default: `65`.

### `STATUSLINE_CTX_CRIT`

Context-window percentage at which the progress bar switches from
amber to red. Default: `85`.

The bar uses these thresholds at every render; the user sees the
transition in real time as the conversation context grows.

### `STATUSLINE_COLOR_PROFILE`

One of: `solarized` (default) | `nord` | `tokyo` | `mono`. See
`color-profiles.md` for visual comparison and rationale.

### `NO_COLOR`

Per [no-color.org](https://no-color.org), any non-empty value strips
all ANSI SGR codes from the output. The statusline then renders as
plain ASCII (Unicode block characters and bullet glyphs preserved).

```bash
NO_COLOR=1 bash statusline.sh
```

## Example: full project config

`.claude/settings.local.json` for a project that monitors a Spring
backend, a Vue frontend, and a Redis instance:

```json
{
  "env": {
    "STATUSLINE_PM2_SERVICES": "backend,frontend",
    "STATUSLINE_PORT_PROBES":  "redis:6379,debug:5005",
    "STATUSLINE_CTX_WARN":     70,
    "STATUSLINE_CTX_CRIT":     90
  }
}
```

`STATUSLINE_COLOR_PROFILE` is omitted, so it falls through to the
user-global setting in `~/.claude/settings.json`, which itself
defaults to `solarized` if unset.

## Debugging

```bash
# See what the script is reading
echo '{"model":{"display_name":"test"}}' | bash statusline.sh | cat -v

# Force plain output
NO_COLOR=1 bash statusline.sh < /dev/null

# Check active env
env | grep STATUSLINE
```

If segments are missing, check tool availability:

```bash
for tool in jq pm2 git lsof; do
  command -v "$tool" >/dev/null && echo "$tool: OK" || echo "$tool: MISSING"
done
```
