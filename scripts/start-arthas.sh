#!/usr/bin/env bash
# Arthas MCP 启动 + 端口存活监控 wrapper
#
# 由 Claude Code SessionStart hook (scripts/arthas-session-start.sh) 在后台拉起。
# 两段式:
#   1) 等 Spring Boot (9001) 就绪后,用 --attach-only 把 Arthas 注入目标 JVM
#   2) Arthas agent 在目标 JVM 内常驻;本脚本进入端口存活监控循环,
#      让端口 8563 关闭时及时退出 (而不是被孤儿化)
#
# 退出语义:
#   - 端口 8563 关闭 (Spring Boot 死 / arthas agent 异常) → 干净退出 0
#   - 收到 SIGTERM/SIGINT (SessionEnd hook) → 干净退出 0,agent 留在目标 JVM
#     (下次 SessionStart 检测到端口已监听,直接进入 monitor_port,不会重复 attach)
#   - 启动失败 (Spring Boot 不就绪 / MCP 端点没起) → 退出非 0
set -euo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
ARTHAS_JAR="${PROJECT_DIR}/tools/arthas-boot.jar"
ARTHAS_PORT=8563
SPRING_BOOT_PORT=9001
MAX_WAIT=120
MONITOR_INTERVAL=5

# 确保 ~/.arthas/ 目录存在,放置 arthas.properties
# Arthas 启动时会自动加载 ~/.arthas/arthas.properties
ARTHAS_HOME="${HOME}/.arthas"
mkdir -p "$ARTHAS_HOME"
if [ ! -f "${ARTHAS_HOME}/arthas.properties" ]; then
  echo "[arthas] Installing arthas.properties to ${ARTHAS_HOME}/"
  cat > "${ARTHAS_HOME}/arthas.properties" << 'EOF'
# Arthas MCP (Model Context Protocol) configuration
arthas.mcpEndpoint=/mcp
arthas.mcpProtocol=STREAMABLE
EOF
fi

# 检查 arthas-boot.jar
if [ ! -f "$ARTHAS_JAR" ]; then
  echo "[arthas] ERROR: $ARTHAS_JAR not found"
  exit 1
fi

# SIGTERM/SIGINT: 干净退出 (SessionEnd hook)
trap 'echo "[arthas] Received signal, wrapper exiting (arthas agent in target JVM stays alive)"; exit 0' INT TERM

# 端口存活监控:wrapper 仅在 MCP 端点活着期间保持运行
monitor_port() {
  echo "[arthas] Monitoring port $ARTHAS_PORT (wrapper PID $$)"
  while lsof -ti :$ARTHAS_PORT > /dev/null 2>&1; do
    sleep "$MONITOR_INTERVAL"
  done
  echo "[arthas] Port $ARTHAS_PORT closed, wrapper exiting"
  exit 0
}

# 等待 Spring Boot 就绪 (端口监听即可)
echo "[arthas] Waiting for Spring Boot on port $SPRING_BOOT_PORT ..."
elapsed=0
while [ $elapsed -lt $MAX_WAIT ]; do
  if lsof -ti :$SPRING_BOOT_PORT > /dev/null 2>&1; then
    echo "[arthas] Spring Boot is ready (port $SPRING_BOOT_PORT listening)"
    break
  fi
  sleep 2
  elapsed=$((elapsed + 2))
done

if [ $elapsed -ge $MAX_WAIT ]; then
  echo "[arthas] ERROR: Spring Boot not ready after ${MAX_WAIT}s, aborting"
  exit 1
fi

# 获取 Spring Boot PID
APP_PID=$(lsof -ti :$SPRING_BOOT_PORT 2>/dev/null | head -1)
if [ -z "$APP_PID" ]; then
  echo "[arthas] ERROR: Cannot find process on port $SPRING_BOOT_PORT"
  exit 1
fi
echo "[arthas] Found Spring Boot PID: $APP_PID"

# 检查 Arthas 是否已附加 (MCP 端口已监听)
# 已附加场景: 上一次 SessionEnd 留下的 agent 仍在目标 JVM 内活着,直接进入监控,不重复 attach
if lsof -ti :$ARTHAS_PORT > /dev/null 2>&1; then
  echo "[arthas] Arthas HTTP/MCP service already running on port $ARTHAS_PORT (skipping attach)"
  monitor_port
fi

# 启动 Arthas 并附加到 Spring Boot
# --attach-only: 附加后 launcher 退出,agent 常驻目标 JVM
# --http-port: 开启 HTTP 服务 (MCP 端点)
# MCP 配置由 ~/.arthas/arthas.properties 提供
echo "[arthas] Attaching Arthas to PID $APP_PID (MCP on port $ARTHAS_PORT) ..."
java -jar "$ARTHAS_JAR" \
  --attach-only \
  --http-port $ARTHAS_PORT \
  "$APP_PID"

EXIT_CODE=$?

# 等待 MCP 端点就绪 (Arthas agent 注入目标 JVM 后 HTTP 服务需要几秒启动)
# 用 TCP 端口探测而非 curl: MCP Streamable HTTP 端点对 GET /mcp 未必 200,
# 而且在沙箱/代理环境里 curl 可能被重定向,导致误判失败
echo "[arthas] Waiting for MCP endpoint on port $ARTHAS_PORT ..."
for i in $(seq 1 20); do
  if (echo > "/dev/tcp/127.0.0.1/${ARTHAS_PORT}") 2>/dev/null; then
    echo "[arthas] ✓ MCP endpoint ready: http://localhost:$ARTHAS_PORT/mcp"
    monitor_port
  fi
  sleep 1
done

echo "[arthas] WARNING: MCP endpoint not responding after attach (java exit code: $EXIT_CODE), wrapper exiting"
exit 1
