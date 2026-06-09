# Arthas MCP Server Integration with Claude Code Lifecycle

**Extracted:** 2026-06-05
**Updated:** 2026-06-09 — Switched from PM2 daemon to Claude Code SessionStart/SessionEnd hooks
**Context:** Adding Arthas MCP (Model Context Protocol) server to a Spring Boot project, so Claude Code can call Arthas diagnostic tools directly. Lifecycle is owned by the Claude Code session, not by PM2 or systemd.

## Problem

Integrating Arthas MCP into a Spring Boot + Claude Code project has multiple pitfalls:
- arthas-boot.jar CLI parameter parsing errors
- Process lifecycle mismatch (agent runs in target JVM, not the boot process)
- Health check false negatives (Spring Boot root path returns 302/401)
- Path resolution issues in scripts spawned by hooks (must use `$CLAUDE_PROJECT_DIR`, not `$0`/`$SCRIPT_DIR`)
- Deciding who owns the wrapper lifecycle (PM2 vs Claude Code session)

## Solution

### 1. arthas-boot.jar parameter gotchas

| Parameter | Issue | Correct approach |
|-----------|-------|-----------------|
| `--properties-file` | **NOT supported** by arthas-boot.jar (it parses this as PID, causing `NumberFormatException`) | Put config in `~/.arthas/arthas.properties` — Arthas auto-loads it |
| `--telnet-port -1` | **Causes `port out of range`** exception | Omit the flag entirely; telnet is enabled by default on 3658 |
| `--http-port 8563` | ✅ Works — enables HTTP/MCP endpoint | This is the MCP port |
| `--attach-only` | ✅ Works — attaches agent then exits boot process | Wrapper is short-lived by design; agent stays in target JVM |

### 2. Wrapper startup script (lifecycle-agnostic)

```bash
#!/usr/bin/env bash
# scripts/start-arthas.sh — long-lived wrapper that:
#   1) waits for Spring Boot on :9001 (port check, NOT curl — root may 302/401)
#   2) attaches Arthas via --attach-only (boot process exits, agent stays in JVM)
#   3) monitors :8563 and exits when MCP endpoint closes (Spring Boot down / agent crashed)
set -euo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
ARTHAS_JAR="${PROJECT_DIR}/tools/arthas-boot.jar"
ARTHAS_PORT=8563
SPRING_BOOT_PORT=9001
MAX_WAIT=120

# Install arthas.properties to ~/.arthas/ (auto-loaded by Arthas)
ARTHAS_HOME="${HOME}/.arthas"
mkdir -p "$ARTHAS_HOME"
if [ ! -f "${ARTHAS_HOME}/arthas.properties" ]; then
  cat > "${ARTHAS_HOME}/arthas.properties" << 'EOF'
arthas.mcpEndpoint=/mcp
arthas.mcpProtocol=STREAMABLE
EOF
fi

trap 'exit 0' INT TERM

monitor_port() {
  while lsof -ti :$ARTHAS_PORT > /dev/null 2>&1; do sleep 5; done
  exit 0
}

# Wait for Spring Boot (port check, NOT curl)
elapsed=0
while [ $elapsed -lt $MAX_WAIT ]; do
  lsof -ti :$SPRING_BOOT_PORT > /dev/null 2>&1 && break
  sleep 2; elapsed=$((elapsed + 2))
done
[ $elapsed -ge $MAX_WAIT ] && { echo "Spring Boot not ready"; exit 1; }

# Idempotent: skip if already attached (next session / leftover agent)
lsof -ti :$ARTHAS_PORT > /dev/null 2>&1 && monitor_port

APP_PID=$(lsof -ti :$SPRING_BOOT_PORT | head -1)
java -jar "$ARTHAS_JAR" --attach-only --http-port $ARTHAS_PORT "$APP_PID"
```

### 3. Claude Code SessionStart hook (background spawn)

```bash
#!/usr/bin/env bash
# scripts/arthas-session-start.sh
# MUST return immediately — don't block session startup.
# Uses nohup + setsid + disown to fully detach from Claude Code's process group.
set -euo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
WRAPPER="${PROJECT_DIR}/scripts/start-arthas.sh"
PID_DIR="${PROJECT_DIR}/.claude/.arthas"
PID_FILE="${PID_DIR}/wrapper.pid"
LOG_FILE="${PID_DIR}/wrapper.log"
ARTHAS_PORT=8563

mkdir -p "$PID_DIR"

# 1) Port already listening → noop
lsof -ti ":${ARTHAS_PORT}" >/dev/null 2>&1 && exit 0

# 2) Stale PID file → clean up
if [ -f "$PID_FILE" ]; then
  OLD="$(cat "$PID_FILE" 2>/dev/null || true)"
  [ -n "$OLD" ] && kill -0 "$OLD" 2>/dev/null && exit 0
  rm -f "$PID_FILE"
fi

# 3) Detached spawn (parent exits immediately)
nohup setsid bash -c "$WRAPPER >> '$LOG_FILE' 2>&1" </dev/null >/dev/null 2>&1 &
disown $! 2>/dev/null || true
echo $! > "$PID_FILE"
```

```json
// .claude/settings.local.json
{
  "hooks": {
    "SessionStart": [{
      "matcher": "",
      "hooks": [{"type": "command", "command": "bash \"$CLAUDE_PROJECT_DIR/scripts/arthas-session-start.sh\""}]
    }],
    "SessionEnd": [{
      "matcher": "",
      "hooks": [{"type": "command", "command": "bash \"$CLAUDE_PROJECT_DIR/scripts/arthas-session-end.sh\""}]
    }]
  }
}
```

### 4. SessionEnd hook (graceful stop)

```bash
#!/usr/bin/env bash
# scripts/arthas-session-end.sh
# SIGTERM wrapper, then SIGKILL after 5s if still alive.
# Does NOT touch the Spring Boot process or the in-JVM arthas agent —
# leaving the agent in place makes the next SessionStart instantaneous
# (port-already-listening branch in start-arthas.sh).
set -uo pipefail
PID_FILE="${CLAUDE_PROJECT_DIR:-$(pwd)}/.claude/.arthas/wrapper.pid"
[ ! -f "$PID_FILE" ] && exit 0
PID="$(cat "$PID_FILE" 2>/dev/null || true)"
rm -f "$PID_FILE"
[ -z "$PID" ] || ! kill -0 "$PID" 2>/dev/null && exit 0
kill -TERM "$PID" 2>/dev/null || true
for _ in $(seq 1 10); do
  kill -0 "$PID" 2>/dev/null || break
  sleep 0.5
done
kill -0 "$PID" 2>/dev/null && kill -KILL "$PID" 2>/dev/null || true
```

### 5. `.mcp.json` for Claude Code

```json
{
  "mcpServers": {
    "arthas-mcp": {
      "type": "http",
      "url": "http://localhost:8563/mcp"
    }
  }
}
```

Use `"type": "http"` (Claude Code official). `"streamableHttp"` is the MCP spec name and accepted as alias, but `"http"` is canonical.

### 6. arthas.properties content

```properties
arthas.mcpEndpoint=/mcp
arthas.mcpProtocol=STREAMABLE
```

## Why Claude Code hooks, not PM2

| Concern | PM2 daemon | Claude Code hook |
|---------|-----------|------------------|
| Wrapper starts on dev-machine boot | ✅ (PM2 resurrect) | ❌ (only on session start — usually what you want) |
| Wrapper auto-restarts on crash | ✅ | ❌ — port closes → wrapper exits → next session's hook re-spawns |
| Lifecycle matches Claude Code session | ❌ — outlives the session | ✅ — SessionStart/SessionEnd bracket |
| Survives `pm2 stop all` | ❌ — needs opt-out config | ✅ — orthogonal to PM2 |
| Works in dev containers / WSL / CI | ⚠️ needs PM2 install | ✅ — bash + arthas-boot.jar only |

For a debugging tool that only matters when a Claude Code session is active, the hook model is the right fit. Use PM2 only if you need arthas available outside Claude Code (e.g. long-running perf capture overnight).

## When to Use

- Adding Arthas MCP server to any Spring Boot + Claude Code project
- When `java -jar arthas-boot.jar` throws `NumberFormatException: For input string: "--properties-file"`
- When `--telnet-port -1` throws `port out of range`
- When PM2 restarts the arthas process in a loop (lifecycle mismatch) — switch to hook model
- When Spring Boot health check `curl -sf` returns non-200 — use `lsof -ti :9001` instead
- When SessionStart hook blocks the session — make sure spawn uses `nohup setsid` + `disown`
