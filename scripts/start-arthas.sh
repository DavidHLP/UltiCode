#!/usr/bin/env bash
# Arthas MCP 启动脚本
# 等 Spring Boot (9001) 就绪后，自动附加 Arthas
# Arthas 附加后 HTTP/MCP 服务运行在目标 JVM 内，启动进程本身会退出
# PM2 配置 autorestart=false，因为 MCP 服务由目标 JVM 承载
set -euo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
ARTHAS_JAR="${PROJECT_DIR}/tools/arthas-boot.jar"
ARTHAS_PORT=8563
SPRING_BOOT_PORT=9001
MAX_WAIT=120

# 确保 ~/.arthas/ 目录存在，放置 arthas.properties
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
if lsof -ti :$ARTHAS_PORT > /dev/null 2>&1; then
  echo "[arthas] Arthas HTTP/MCP service already running on port $ARTHAS_PORT"
  echo "[arthas] MCP endpoint: http://localhost:$ARTHAS_PORT/mcp"
  exit 0
fi

# 启动 Arthas 并附加到 Spring Boot
# --attach-only: 附加后退出交互式 shell (MCP agent 运行在目标 JVM 内)
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
    exit 0
  fi
  sleep 1
done

echo "[arthas] WARNING: MCP endpoint not responding yet (Arthas agent may still be initializing, exit code: $EXIT_CODE)"
exit $EXIT_CODE
