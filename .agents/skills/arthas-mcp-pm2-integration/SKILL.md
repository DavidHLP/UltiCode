---
name: arthas-mcp-pm2-integration
description: Integrate Arthas MCP (Model Context Protocol) server into Spring Boot projects managed by PM2 so Codex can call Arthas diagnostic tools directly. Trigger when adding an Arthas MCP endpoint to a PM2-managed JVM, troubleshooting arthas-boot.jar parameter parsing errors (NumberFormatException / port out of range), fixing agent lifecycle mismatch with PM2 autorestart, or resolving Spring Boot health-check false negatives (302/401 on root path).
---

# Arthas MCP Server Integration with PM2

**Extracted:** 2026-06-05
**Context:** Adding Arthas MCP (Model Context Protocol) server to a Spring Boot project managed by PM2, so Codex can call Arthas diagnostic tools directly.

## Problem

Integrating Arthas MCP into PM2-managed projects has multiple pitfalls:
- arthas-boot.jar CLI parameter parsing errors
- Process lifecycle mismatch (agent runs in target JVM, not the boot process)
- Health check false negatives (Spring Boot root path returns 302/401)
- Path resolution issues in PM2-managed scripts

## Solution

### 1. arthas-boot.jar parameter gotchas

| Parameter | Issue | Correct approach |
|-----------|-------|-----------------|
| `--properties-file` | **NOT supported** by arthas-boot.jar (it parses this as PID, causing `NumberFormatException`) | Put config in `~/.arthas/arthas.properties` — Arthas auto-loads it |
| `--telnet-port -1` | **Causes `port out of range`** exception | Omit the flag entirely; telnet is enabled by default on 3658 |
| `--http-port 8563` | ✅ Works — enables HTTP/MCP endpoint | This is the MCP port |
| `--attach-only` | ✅ Works — attaches agent then exits boot process | PM2 must use `autorestart: false` |

### 2. Startup script pattern

```bash
#!/usr/bin/env bash
set -euo pipefail

# Use CLAUDE_PROJECT_DIR, NOT script's own directory
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

# Wait for Spring Boot using PORT CHECK, NOT curl
# (curl fails because root path may return 302/401)
elapsed=0
while [ $elapsed -lt $MAX_WAIT ]; do
  if lsof -ti :$SPRING_BOOT_PORT > /dev/null 2>&1; then
    break
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

# Check if already attached (MCP port already listening)
if lsof -ti :$ARTHAS_PORT > /dev/null 2>&1; then
  exit 0
fi

APP_PID=$(lsof -ti :$SPRING_BOOT_PORT | head -1)

# --attach-only: boot process exits, but MCP agent runs in target JVM
java -jar "$ARTHAS_JAR" \
  --attach-only \
  --http-port $ARTHAS_PORT \
  "$APP_PID"
```

### 3. PM2 ecosystem config entry

```javascript
{
  name: 'ulticode-arthas',
  script: './scripts/start-arthas.sh',
  interpreter: 'bash',
  autorestart: false,  // CRITICAL: agent lives in target JVM, not boot process
  kill_timeout: 10000,
  ...logConfig('arthas'),
  env: { ...envFromFile }
}
```

### 4. `.mcp.json` for Codex

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

Use `"type": "http"` (Codex official). `"streamableHttp"` is the MCP spec name and accepted as alias, but `"http"` is canonical.

### 5. arthas.properties content

```properties
arthas.mcpEndpoint=/mcp
arthas.mcpProtocol=STREAMABLE
```

## When to Use

- Adding Arthas MCP server to any Spring Boot + PM2 project
- When `java -jar arthas-boot.jar` throws `NumberFormatException: For input string: "--properties-file"`
- When `--telnet-port -1` throws `port out of range`
- When PM2 restarts Arthas process in a loop (agent lifecycle mismatch)
- When Spring Boot health check `curl -sf` returns non-200
