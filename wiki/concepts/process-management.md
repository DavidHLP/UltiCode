---
title: Process Management
type: concept
tags: [ops, dev, process, type/concept]
status: living
updated: 2026-06-22
sources:
  - ecosystem.config.cjs
  - .claude/launch.json
  - scripts/dev/up.sh
  - scripts/dev/doctor.sh
  - .claude/settings.json
  - scripts/start-arthas.sh
aliases: [进程管理, PM2 vs Preview]
---

# Process Management

## The problem

UltiCode has **two competing process managers** that can hold the same ports
(9001/9002/9003/8563) on the same dev box:

1. **PM2** (long-running daemon) — owns 9001/9002/9003/arthas via
   `ecosystem.config.cjs`. Survives shell exit, runs on SSH/CI/dev machines.
2. **Claude Code Preview MCP** (per-session) — runs Vite/Node inside the
   Claude Desktop's spawn sandbox via `.claude/launch.json`. Dies when the
   Desktop process dies.

The two can coexist on one machine, but the design assumes they **don't**:
each wants port 9002 (console Vite), 9003 (management Vite). The pain
points that surface:

- Preview "starts" but the browser panel shows "preview server stopped".
  Root cause: `pnpm dev` runs `lint && type-check && format && test && vite`
  — any step crashing kills the whole process, and the panel never recovers.
- Vite's `server.port: 9002` is hard-coded; PM2 already holds 9002, so vite
  silently falls through to 5173, but the Preview tool still believes the
  process is on 9002 and proxies the panel to a dead port.
- `ulticode-9001` shows `status=online` in `pm2 list` with `↺=45` (restart
  count) — the daemon keeps respawning it, the port is "owned" by a
  crashing process, and `lsof -ti :9001` shows the JVM rather than the PM2
  wrapper. **Online in PM2 ≠ actually serving.**
- `pm2 restart --update-env` does **not** re-read `ecosystem.config.cjs`'s
  `envFromFile` (uses daemon cache), so `.env` changes can leave
  Redis/DB password stale without any obvious error.

The fix is not to "merge the two" — they serve different lifecycles. It's
to **separate their domains** and add a doctor that tells you which mode
you're in and how to switch.

## The decision

Three rules, one tool, one source of truth.

### Rule 1 — Pick a mode, stay in it

| Mode | Who runs the stack | Trigger | Persists across shell exit | When to use |
|------|-------------------|---------|---------------------------|-------------|
| **A — PM2** | `ecosystem.config.cjs` (long-running daemon) | `pm2 start ecosystem.config.cjs` (or `scripts/dev/up.sh`) | ✅ yes | Long dev sessions, CI, SSH, machine restart |
| **B — Preview** | `.claude/launch.json` (per-session, inside Claude Desktop) | `preview_start <name>` via MCP | ❌ no (dies with Claude Desktop) | Reviewing UI, clicking through, demos |

**Mixing** mode A and mode B on the **same machine, same port** is fragile:
PM2 holds 9002, Preview tries to spawn vite on 9002, vite silently slips
to 5173, the panel never connects. If you must run both, stop PM2 first:
`pm2 stop ulticode-9002 ulticode-9003`, then `preview_start`. To go back:
`preview_stop`, then `pm2 start ecosystem.config.cjs --only ulticode-9002,ulticode-9003`.

### Rule 2 — Preview is preview-only, never business

`.claude/launch.json` runs **`pnpm exec vite`**, not `pnpm dev`. The
full `dev` script is a CI gate (lint+type-check+format+test+vite); running
it inside the Preview sandbox is wrong because any of those four pre-steps
can flake and tear down the only process the panel can talk to. The
`pnpm exec vite` shortcut is what `AGENTS.md` calls out for "PM2 start
scenarios, avoid triggering unrelated formatters."

Vite is invoked with **explicit `--port 9002 --host 127.0.0.1 --strictPort`**:

- `--port 9002` — match PM2's expected port so the rest of the stack
  (CORS allowlist, OAuth redirects, WebSocket origin checks) just works.
- `--host 127.0.0.1` — Vite's default `localhost` resolves to `::1` on
  Linux, breaking the `127.0.0.1` readiness check in `up.sh` line 135.
  Pin to IPv4 loopback to match the "infra ports bind loopback only"
  security posture.
- `--strictPort` — exit if 9002 is taken, instead of silently slipping to
  5173. This makes port conflicts **loud** rather than mysterious.

**`autoPort: true` in launch.json is aspirational, not functional.** The
Preview tool can't read the OS-assigned port from vite's stdout (vite
prints it, the tool ignores it). Until the tool grows a port-discovery
hook, expect `--port 9002 --strictPort` to be the working combination.

### Rule 3 — One doctor, one verdict

`scripts/dev/doctor.sh` is the **single source of truth** for "what is
running on what port, owned by whom, and what should I do next":

```bash
bash scripts/dev/doctor.sh          # full report + recommendation
bash scripts/dev/doctor.sh --ports  # port table only
bash scripts/dev/doctor.sh --pm2    # PM2 health only
bash scripts/dev/doctor.sh --json   # machine-readable for Claude / CI
```

It enumerates the four canonical ports (9001/9002/9003/8563), tags the
listening PID with an owner (PM2 / arthas-wrapper / preview/vite /
java/spring / unknown), prints PM2 app health (and **flags ↺ ≥ 10 as a
warn**), prints container health for the three Docker services, then
emits a recommendation:

- All ports held by PM2 + infra healthy → "**mode A active**, use PM2".
- A Preview/Vite process is on 9002/9003 → "**mode B active**, stay in
  Preview via MCP".
- Nothing running → "pick a mode".
- Unknown owner holding a port → "investigate; do not start more".

Cross-platform: uses `lsof` when available, `netstat -ano` on Windows,
`/proc/<pid>/cmdline` on Linux. No Python or other extra runtime
dependency — only `node` (already required by the frontends) for pretty
PM2 JSON parsing, with graceful fallback to "(unable to parse)".

The doctor is **read-only** — it never stops, starts, or restarts
anything. The recommendation is a sentence, the action is the human's.

## Why

- **Why split PM2 and Preview at all?** PM2 needs to survive the shell
  exiting (over SSH, in CI, after a long compile). Preview is naturally
  scoped to "I am at my desk clicking through UI." A single tool can't
  do both well — PM2's daemon model breaks the Preview panel's
  short-lived "render this URL" contract, and Preview's per-session
  lifespan breaks PM2's "restart on crash" contract.
- **Why not auto-detect and switch mode?** Both runners want port 9002
  with the same `--host 127.0.0.1` semantics. Auto-negotiating would
  require killing one to free the port for the other, which violates
  "no surprise side effects" — the user should always know which mode
  they're in. The doctor surfaces the state; the user acts.
- **Why `--strictPort` and not `--port 0`?** `--port 0` lets the OS pick
  a port (vite prints e.g. 5173), but the Preview MCP can't read that
  printed port — it uses the `port` field in `launch.json` (9002) as
  truth. The tool's `autoPort: true` field is decorative for vite. If
  the Preview tool ever learns to parse vite's port line, we can drop
  `--strictPort` and let `--port 0` do its job.
- **Why `node` for PM2 parsing, not `python3` or `jq`?** The project
  pins `node ^20.19.0 || >=22.12.0` in `console/package.json` and
  `management/package.json`; `python3` is not guaranteed (Windows lacks
  it, and the project never installs it). `jq` is similarly optional.
  `node` is the lowest-friction dep that's always present.

## Where it lives

- `ecosystem.config.cjs` — PM2 app definitions (5 apps, 4 ports), reads
  `.env` via `parseEnvFile`.
- `.claude/launch.json` — Claude Preview MCP server list (mirrors the 5
  PM2 apps, with `autoPort: true` for aspirational use).
- `scripts/dev/up.sh` — full bootstrap: docker → nacos → flyway → PM2.
- `scripts/dev/doctor.sh` — the read-only inspector + recommender.
- `.claude/settings.json` — `SessionStart` / `SessionEnd` hooks for
  arthas wrapper (separate concern, see
  [[concepts/arthas-diagnostics]]).
- `backend-spring/start.cjs` — preview-sandbox-friendly backend starter
  (resolves Git Bash on Windows, prepends coreutils to `PATH`).

## Trade-offs

- **PM2 `envFromFile` cache** — `pm2 restart --update-env` is misleading;
  it does NOT reload `.env` after edits. The fix
  (`pm2 delete && pm2 start`) is destructive (brief downtime) but
  deterministic. `doctor.sh` flags high restart count as a warning, so
  the symptom surfaces even before the user notices stale config.
- **Preview port-pin** — pinning Vite to 9002/9003 means Preview can't
  start if PM2 is on. That's deliberate, not a bug: the user must stop
  PM2 first. The doctor explains how.
- **Doctor is not a fixer** — the doctor recommends commands; it does
  not run them. Some users will want an auto-fix mode; we deliberately
  don't ship one because the wrong auto-fix (e.g. silently killing a
  PM2 app the user is about to debug) is worse than a one-line
  instruction the user can read.
- **No graceful mode-switch script** — `pm2 stop ulticode-9002 && preview_start`
  is two commands. A `scripts/dev/preview-start.sh` wrapper that does
  both atomically was considered; deferred to keep this concept page
  the only source of truth for the policy. Add it later if the friction
  bites.

## Related

- [[overview/dev-environment-overview]] — the canonical "what runs" map
  (PM2 apps, docker containers, startup order, traps)
- [[concepts/arthas-diagnostics]] — arthas MCP is its own three-way
  mutex (PM2 / Claude hook / CLI) on port 8563; the same "pick a path"
  discipline applies
- [[concepts/security-invariants]] — `127.0.0.1` binding and loopback
  posture that `--host 127.0.0.1` and `docker-compose.dev.yml` enforce
- [[overview/architecture-overview]] — request flow assumes 9001/9002/9003
  are reachable; the doctor verifies that assumption
