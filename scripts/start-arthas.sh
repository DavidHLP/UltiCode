#!/usr/bin/env bash
# Arthas MCP self-healing wrapper
#
# 由 Claude Code SessionStart hook (scripts/arthas-session-start.sh) 在后台拉起,
# 也建议通过 PM2 监督本脚本 (本进程崩溃时自动重启, 见 README)。
#
# 自愈 loop (修复: Claude Code 运行中 pm2 restart Spring Boot 后 arthas 断连问题):
#   1) 等 Spring Boot (9001) 就绪
#   2) 探测端口 8563 是否已监听 (已 attach 场景: 上一轮 attach 留下的 agent 仍活)
#      - 是: 直接进入端口监控
#      - 否: 调 arthas-boot attach 到 Spring Boot, 等 MCP 端点 ready
#   3) 监控端口 8563: 端口死了不退出,而是回到顶端重试 attach
#   4) SIGTERM/SIGINT: 干净退出 (SessionEnd hook 触发)
#
# 退出语义:
#   - 收到 SIGTERM/SIGINT → 0,agent 留在目标 JVM (下次 SessionStart 复用)
#   - 其他原因退出 (如 5 次连续 attach 失败) → 0,PM2 监督下自动重启
set -uo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
ARTHAS_JAR="${PROJECT_DIR}/tools/arthas-boot.jar"
ARTHAS_PORT=8563
SPRING_BOOT_PORT=9001
MAX_SB_WAIT=120
MCP_READY_TIMEOUT=20
MONITOR_INTERVAL=5
RETRY_BACKOFF=5
MAX_CONSECUTIVE_FAILURES=5

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

# SIGTERM/SIGINT: 干净退出 (SessionEnd hook 触发, 或 PM2 stop)
trap 'echo "[arthas] Received signal, wrapper exiting (arthas agent in target JVM stays alive if any)"; exit 0' INT TERM

# 端口存活监控: 不再直接 exit, 而是返回让外层 loop 决定
monitor_port() {
  echo "[arthas] Monitoring port $ARTHAS_PORT (wrapper PID $$)"
  while lsof -ti :$ARTHAS_PORT > /dev/null 2>&1; do
    sleep "$MONITOR_INTERVAL"
  done
  echo "[arthas] Port $ARTHAS_PORT closed (target JVM likely restarted) — will re-attach"
  return 0
}

# 等待 Spring Boot 就绪
wait_for_spring_boot() {
  local elapsed=0
  echo "[arthas] Waiting for Spring Boot on port $SPRING_BOOT_PORT ..."
  while [ $elapsed -lt $MAX_SB_WAIT ]; do
    if lsof -ti :$SPRING_BOOT_PORT > /dev/null 2>&1; then
      echo "[arthas] Spring Boot is ready (port $SPRING_BOOT_PORT listening)"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "[arthas] ERROR: Spring Boot not ready after ${MAX_SB_WAIT}s"
  return 1
}

# 尝试 attach 一次,返回 0 表示 MCP 端点已就绪,1 表示失败
try_attach() {
  local app_pid
  app_pid=$(lsof -ti :$SPRING_BOOT_PORT 2>/dev/null | head -1)
  if [ -z "$app_pid" ]; then
    echo "[arthas] ERROR: Cannot find process on port $SPRING_BOOT_PORT"
    return 1
  fi
  echo "[arthas] Found Spring Boot PID: $app_pid"

  # 已 attach 场景: 端口在监听
  if lsof -ti :$ARTHAS_PORT > /dev/null 2>&1; then
    echo "[arthas] Arthas HTTP/MCP service already running on port $ARTHAS_PORT (skipping attach)"
    return 0
  fi

  echo "[arthas] Attaching Arthas to PID $app_pid (MCP on port $ARTHAS_PORT) ..."
  # --attach-only: 附加后 launcher 退出,agent 常驻目标 JVM
  # --http-port: 开启 HTTP 服务 (MCP 端点)
  # MCP 配置由 ~/.arthas/arthas.properties 提供
  java -jar "$ARTHAS_JAR" \
    --attach-only \
    --http-port "$ARTHAS_PORT" \
    "$app_pid"

  local exit_code=$?
  echo "[arthas] arthas-boot exited with code $exit_code"

  # 等 MCP 端点 ready (agent 注入目标 JVM 后 HTTP 服务需要几秒启动)
  # TCP 探测而非 curl: MCP Streamable HTTP 对 GET /mcp 不一定 200
  echo "[arthas] Waiting for MCP endpoint on port $ARTHAS_PORT (up to ${MCP_READY_TIMEOUT}s) ..."
  for _ in $(seq 1 "$MCP_READY_TIMEOUT"); do
    if (echo > "/dev/tcp/127.0.0.1/${ARTHAS_PORT}") 2>/dev/null; then
      echo "[arthas] ✓ MCP endpoint ready: http://localhost:$ARTHAS_PORT/mcp"
      return 0
    fi
    sleep 1
  done
  echo "[arthas] WARNING: MCP endpoint not responding after attach (arthas-boot exit=$exit_code)"
  return 1
}

# === 主循环: 自愈 — attach 失败 / 端口死亡都回到顶端 ===
consecutive_failures=0
while true; do
  if ! wait_for_spring_boot; then
    # Spring Boot 不就绪: 短暂 sleep 后重试, 不要死循环
    sleep "$RETRY_BACKOFF"
    continue
  fi

  if try_attach; then
    consecutive_failures=0
    # 进入端口监控;端口死了 -> monitor_port 返回 -> 回到 while 顶端重试
    monitor_port
    sleep "$RETRY_BACKOFF"   # 端口刚死, 给目标 JVM 几秒重启/GC
  else
    consecutive_failures=$((consecutive_failures + 1))
    echo "[arthas] Attach attempt failed ($consecutive_failures/${MAX_CONSECUTIVE_FAILURES}); backing off ${RETRY_BACKOFF}s"
    if [ "$consecutive_failures" -ge "$MAX_CONSECUTIVE_FAILURES" ]; then
      echo "[arthas] Reached ${MAX_CONSECUTIVE_FAILURES} consecutive failures; sleeping longer (30s) before retry"
      sleep 30
    else
      sleep "$RETRY_BACKOFF"
    fi
  fi
done
