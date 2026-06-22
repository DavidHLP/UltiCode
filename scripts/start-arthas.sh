#!/usr/bin/env bash
# Arthas MCP self-healing wrapper
#
# 由 Claude Code SessionStart hook (scripts/arthas-session-start.sh) 在后台拉起,
# 也通过 PM2 监督本脚本 (本进程崩溃时自动重启, 见 ecosystem.config.cjs)。
#
# 自愈 loop (修复: Claude Code 运行中 pm2 restart Spring Boot 后 arthas 断连问题):
#   1) 等 Spring Boot (9001) 就绪
#   2) 探测端口 8563 是否已监听 (已 attach 场景: 上一轮 attach 留下的 agent 仍活)
#      - 是: 直接进入端口监控
#      - 否: 调 arthas-boot attach 到 Spring Boot, 等 MCP 端点 ready
#   3) 监控端口 8563: 端口死了不退出,而是回到顶端重试 attach
#   4) SIGTERM/SIGINT: 干净退出 (SessionEnd hook 或 PM2 stop 触发)
#
# 退出语义:
#   - 收到 SIGTERM/SIGINT → 0,agent 留在目标 JVM (下次 SessionStart 复用)
#   - 其他原因退出 (如 5 次连续 attach 失败) → 0,PM2 监督下自动重启
#
# 互斥机制:
#   - 启动时把 wrapper PID + launcher (pm2 / hook) 写入 ${PID_DIR}/wrapper.pid
#   - Claude Code SessionStart hook 检测到 PID 文件或 8563 端口在用 → 跳过
#   - 退出时清理 PID 文件 (让 hook / CLI 知道 wrapper 已下)
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
PID_DIR="${PROJECT_DIR}/.claude/.arthas"
PID_FILE="${PID_DIR}/wrapper.pid"
# launcher: pm2 (PM2 拉起) / hook (Claude Code hook 拉起) / cli (arthas-cli.sh 拉起)
LAUNCHER="${ULTICODE_ARTHAS_LAUNCHER:-cli}"

mkdir -p "$PID_DIR"

# === 端口检测辅助函数: 优先 lsof (Linux/macOS), 回退 netstat (Windows Git Bash 缺 lsof) ===
# CLAUDE.md 约定"Spring Boot 健康检查用 lsof"; 在 lsof 存在时严格遵循, 不存在时用 netstat 兜底
# 省略 LISTENING 过滤: netstat 中 [.:]PORT<空格> 只匹配 Local-Address 列里端口处于绑定态的条目
# (ESTABLISHED/TIME_WAIT 的本地端口在同一列, 但 arthas MCP 短连接 + localhost 探测时几乎无残留)。
# 每次省 ~25ms netstat→grep pipeline (实测 Windows Git Bash 100ms→77ms),
# 5s 监控循环 24/7: 0.5% → 0.4% CPU, 看着不多但每月 = 数小时纯 grep 浪费。
port_listening() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti :"$port" > /dev/null 2>&1
    return $?
  fi
  netstat -ano 2>/dev/null | grep -qE "[:.]${port}[[:space:]]"
}

port_pid() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti :"$port" 2>/dev/null | head -1
    return
  fi
  netstat -ano 2>/dev/null | grep -E "[:.]${port}[[:space:]]" | grep -i LISTENING | awk '{print $NF}' | head -1
}

# arthas 启动时读 arthas.home/lib/<version>/arthas/arthas.properties (解压后的内嵌文件),
# **不是** ~/.arthas/arthas.properties。wrapper 在每次 attach 前 sync 项目级
# infrastructure/arthas/arthas.properties 到 arthas.home, 保证新机器 / 升级不踩 STREAMABLE
# 默认 (4.2.2 默认强制 mcp-session-id, 与 Claude Code 内置 MCP 客户端不维护 session 冲突)。
PROJECT_ARTHAS_PROPS="${PROJECT_DIR}/infrastructure/arthas/arthas.properties"
ARTHAS_HOME="${HOME}/.arthas"
mkdir -p "$ARTHAS_HOME"

# 检查 arthas-boot.jar
if [ ! -f "$ARTHAS_JAR" ]; then
  echo "[arthas] ERROR: $ARTHAS_JAR not found"
  exit 1
fi

# === PID 文件: 在最早阶段写, 让 hook 能在端口起来前就检测到本 wrapper ===
# 格式: "PID LAUNCHER" (两行: PID, launcher)
# 双重检测: hook 看 PID 文件 + lsof, 任一在用都跳过
echo $$ > "$PID_FILE"
echo "$LAUNCHER" >> "$PID_FILE"

# SIGTERM/SIGINT: 干净退出 + 清理 PID 文件
cleanup() {
  rm -f "$PID_FILE"
  echo "[arthas] Wrapper exiting (PID file cleaned)"
  exit 0
}
trap 'echo "[arthas] Received signal, wrapper exiting (arthas agent in target JVM stays alive if any)"; cleanup' INT TERM
# 兜底: 任何非信号退出也清 PID (例如连续 attach 失败 PM2 重启时)
trap 'rm -f "$PID_FILE"' EXIT

# 端口存活监控: 不再直接 exit, 而是返回让外层 loop 决定
monitor_port() {
  echo "[arthas] Monitoring port $ARTHAS_PORT (wrapper PID $$)"
  while port_listening "$ARTHAS_PORT"; do
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
    if port_listening "$SPRING_BOOT_PORT"; then
      echo "[arthas] Spring Boot is ready (port $SPRING_BOOT_PORT listening)"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "[arthas] ERROR: Spring Boot not ready after ${MAX_SB_WAIT}s"
  return 1
}

# 同步项目级 arthas.properties 到 arthas.home 下的解压目录 (arthas-agent 真正读的位置)
# 解决: 4.2.2 jar 解压出的内嵌 arthas.properties 默认 mcpProtocol=STREAMABLE, 强制
# mcp-session-id, 与 Claude Code MCP 客户端不维护 session 冲突 → 阻塞命令持续超时。
# 强制项目级 STATELESS, 避免新机器/arthas 升级后回退到 STREAMABLE。
sync_arthas_properties() {
  if [ ! -f "$PROJECT_ARTHAS_PROPS" ]; then
    echo "[arthas] WARN: $PROJECT_ARTHAS_PROPS not found, skip sync (mcpProtocol 用 jar 默认)"
    return 0
  fi
  local arthas_lib_dir="${ARTHAS_HOME}/lib"
  if [ ! -d "$arthas_lib_dir" ]; then
    echo "[arthas] WARN: $arthas_lib_dir not found, arthas 还没解压过, skip sync"
    return 0
  fi
  # 遍历所有解压版本, 同步项目级 properties 覆盖内嵌默认值
  local synced=0
  for version_dir in "$arthas_lib_dir"/*/arthas; do
    [ -d "$version_dir" ] || continue
    local target="${version_dir}/arthas.properties"
    # 用 diff 快速判断要不要写 (避免每次 attach 刷 atime, 让 arthas 不必要地 reload)
    if [ ! -f "$target" ] || ! diff -q "$PROJECT_ARTHAS_PROPS" "$target" > /dev/null 2>&1; then
      cp "$PROJECT_ARTHAS_PROPS" "$target"
      local ver
      ver=$(basename "$(dirname "$version_dir")")
      echo "[arthas] Synced project arthas.properties → $target (version $ver)"
      synced=$((synced + 1))
    fi
  done
  if [ "$synced" -eq 0 ]; then
    echo "[arthas] arthas.properties already in sync (project == deployed)"
  fi
  return 0
}

# 尝试 attach 一次,返回 0 表示 MCP 端点已就绪,1 表示失败
try_attach() {
  local app_pid
  app_pid=$(port_pid "$SPRING_BOOT_PORT")
  if [ -z "$app_pid" ]; then
    echo "[arthas] ERROR: Cannot find process on port $SPRING_BOOT_PORT"
    return 1
  fi
  echo "[arthas] Found Spring Boot PID: $app_pid"

  # 已 attach 场景: 端口在监听
  if port_listening "$ARTHAS_PORT"; then
    echo "[arthas] Arthas HTTP/MCP service already running on port $ARTHAS_PORT (skipping attach)"
    return 0
  fi

  # attach 前同步项目级配置 (新机器 / 升级后第一次 attach 必跑, 之后 diff 命中直接跳过)
  sync_arthas_properties

  echo "[arthas] Attaching Arthas to PID $app_pid (MCP on port $ARTHAS_PORT) ..."
  # --attach-only: 附加后 launcher 退出,agent 常驻目标 JVM
  # --http-port: 开启 HTTP 服务 (MCP 端点)
  # MCP 配置来自 arthas.home/lib/<version>/arthas/arthas.properties (由 sync_arthas_properties 同步)
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
    if port_listening "$ARTHAS_PORT"; then
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
