#!/usr/bin/env bash
# Arthas MCP 启动 + 端口存活监控脚本 (统一 PM2 生命周期管理)
#
# 两段式:
#   1) 等 Spring Boot (9001) 就绪后,用 --attach-only 把 Arthas 注入目标 JVM
#   2) Arthas agent 在目标 JVM 内常驻;本脚本进入端口存活监控循环,
#      让 PM2 始终看到 online (而不是 attach-only 退出后的 stopped)
#
# 退出语义:
#   - 端口 8563 关闭 (Spring Boot 死/arthas agent 异常) → 干净退出 0,PM2 切到 stopped
#   - 收到 SIGTERM/SIGINT (pm2 stop) → 干净退出 0,agent 留在目标 JVM (用户需自行处理)
#   - 启动失败 (Spring Boot 不就绪 / MCP 端点没起) → 退出非 0,PM2 标记错误
#
# PM2 配置 autorestart=false 仍然正确:
#   wrapper 退出表示 Spring Boot 已不在,盲目重 attach 会失败/无意义
#   用户在 Spring Boot 恢复后手动 `pm2 start ulticode-arthas` 即可
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

# SIGTERM/SIGINT: 干净退出 (PM2 stop)
trap 'echo "[arthas] Received signal, wrapper exiting (arthas agent in target JVM stays alive)"; exit 0' INT TERM

# 端口存活监控:让 PM2 看到 online 而不是 stopped
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
# 已附加场景: agent 还在目标 JVM 内活着,直接进入监控,不重复 attach
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
echo "[arthas] Waiting for MCP endpoint ..."
for i in $(seq 1 20); do
  if curl -sf http://localhost:$ARTHAS_PORT/mcp > /dev/null 2>&1; then
    echo "[arthas] ✓ MCP endpoint ready: http://localhost:$ARTHAS_PORT/mcp"
    monitor_port
  fi
  sleep 1
done

echo "[arthas] WARNING: MCP endpoint not responding after attach (java exit code: $EXIT_CODE), wrapper exiting"
exit 1
