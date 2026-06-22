#!/usr/bin/env bash
# Arthas MCP 通用 CLI — 不依赖 Claude Code / PM2
#
# 用法:
#   scripts/arthas-cli.sh start    # 后台拉起 wrapper (互斥: 已在跑就跳过)
#   scripts/arthas-cli.sh stop     # 停 wrapper (只停 cli 自己拉起的, 不动 PM2)
#   scripts/arthas-cli.sh restart  # stop + start
#   scripts/arthas-cli.sh status   # 端口 / PID 文件 / MCP 端点 / launcher 全景
#   scripts/arthas-cli.sh logs     # tail wrapper 日志
#
# 三种启动路径 (按优先级, 后启动的会跳过已在跑的):
#   1) PM2:           `pm2 start ecosystem.config.cjs`  (推荐: 跟随项目整体)
#   2) Claude Code:   SessionStart hook 自动拉起
#   3) CLI:           `scripts/arthas-cli.sh start`      (本脚本, 手动/SSH/调试)
#
# 互斥机制: 任何一路写 PID_FILE (含 launcher 标记), 其他路检测到就跳过
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
WRAPPER="${PROJECT_DIR}/scripts/start-arthas.sh"
PID_DIR="${PROJECT_DIR}/.claude/.arthas"
PID_FILE="${PID_DIR}/wrapper.pid"
LOG_FILE="${PID_DIR}/wrapper.log"
ARTHAS_PORT=8563
SPRING_BOOT_PORT=9001

mkdir -p "$PID_DIR"

# 读 PID 文件, 返回 "PID LAUNCHER" 字符串 (可能为空)
read_pid_file() {
  if [ ! -f "$PID_FILE" ]; then
    return 1
  fi
  local pid launcher
  pid="$(head -1 "$PID_FILE" 2>/dev/null || true)"
  launcher="$(tail -1 "$PID_FILE" 2>/dev/null || true)"
  if [ -z "$pid" ]; then
    return 1
  fi
  echo "$pid $launcher"
  return 0
}

# 检查端口是否监听 (优先 lsof, 回退 netstat — 与 start-arthas.sh 一致)
# 缺了 netstat 兜底在 Windows Git Bash (lsof 不存在) 会全部误报 'not listening'
port_listening() {
  local port="${1:-$ARTHAS_PORT}"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti :"$port" >/dev/null 2>&1
    return $?
  fi
  netstat -ano 2>/dev/null | grep -qE "[:.]${port}[[:space:]]"
}

# 检查 Spring Boot 是否在跑
spring_boot_running() {
  port_listening "$SPRING_BOOT_PORT"
}

# MCP 端点 TCP 可达性 — 用 port_listening 而不是 /dev/tcp/ (后者在 MSYS 上不可靠)
mcp_reachable() {
  port_listening "$ARTHAS_PORT"
}

# 启动 wrapper
cmd_start() {
  if port_listening; then
    echo "✓ Arthas MCP already listening on :${ARTHAS_PORT} (skip)"
    cmd_status
    return 0
  fi
  if [ -f "$PID_FILE" ]; then
    read_pid_file | while read -r pid launcher; do
      if kill -0 "$pid" 2>/dev/null; then
        echo "✓ Arthas wrapper already running (PID $pid, launcher=$launcher) (skip)"
        return 0
      fi
    done
    rm -f "$PID_FILE"
  fi
  if [ ! -x "$WRAPPER" ]; then
    echo "✗ Wrapper not executable: $WRAPPER" >&2
    return 1
  fi
  if ! spring_boot_running; then
    echo "⚠ Spring Boot not running on :${SPRING_BOOT_PORT}"
    echo "  wrapper will keep retrying every ${RETRY_BACKOFF:-5}s after start"
  fi

  echo "→ Starting arthas wrapper (background) ..."
  ULTICODE_ARTHAS_LAUNCHER=cli \
    nohup setsid bash -c "ULTICODE_ARTHAS_LAUNCHER=cli '$WRAPPER' >> '$LOG_FILE' 2>&1" </dev/null >/dev/null 2>&1 &
  local pid=$!
  disown "$pid" 2>/dev/null || true
  sleep 1
  echo "✓ Spawned (PID $pid), log: $LOG_FILE"
  echo "  (MCP attach 通常需要 5-30s 等 Spring Boot 就绪)"
  return 0
}

# 停 wrapper
cmd_stop() {
  if [ ! -f "$PID_FILE" ]; then
    echo "○ No PID file; nothing to stop"
    return 0
  fi
  local pid launcher
  pid="$(head -1 "$PID_FILE" 2>/dev/null || true)"
  launcher="$(tail -1 "$PID_FILE" 2>/dev/null || true)"
  if [ "$launcher" != "cli" ] && [ -n "$launcher" ]; then
    echo "✗ Wrapper managed by '$launcher', not cli. Use:"
    case "$launcher" in
      pm2) echo "    pm2 stop ulticode-arthas" ;;
      hook) echo "    (wait for SessionEnd, or kill PID $pid manually)" ;;
    esac
    return 1
  fi
  if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
    echo "○ Wrapper not running (stale PID file cleaned)"
    rm -f "$PID_FILE"
    return 0
  fi
  echo "→ Stopping wrapper (PID $pid) ..."
  kill -TERM "$pid" 2>/dev/null || true
  for _ in $(seq 1 10); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.5
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo "  Still alive, sending SIGKILL"
    kill -KILL "$pid" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
  echo "✓ Stopped"
  return 0
}

# 状态总览
cmd_status() {
  echo "=== Arthas MCP status ==="
  echo "Project:        $PROJECT_DIR"
  echo "Port:           $ARTHAS_PORT (MCP: /mcp, STREAMABLE)"
  echo "Spring Boot:    $(spring_boot_running && echo "✓ running on :$SPRING_BOOT_PORT" || echo "✗ not on :$SPRING_BOOT_PORT")"
  echo "Port :$ARTHAS_PORT:    $(port_listening && echo "✓ listening" || echo "○ not listening")"
  if mcp_reachable; then
    echo "MCP endpoint:   ✓ http://localhost:$ARTHAS_PORT/mcp (TCP OK)"
  else
    echo "MCP endpoint:   ○ not reachable"
  fi
  if [ -f "$PID_FILE" ]; then
    local pid launcher
    pid="$(head -1 "$PID_FILE" 2>/dev/null || true)"
    launcher="$(tail -1 "$PID_FILE" 2>/dev/null || true)"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      echo "Wrapper:        ✓ PID $pid, launcher=$launcher"
    else
      echo "Wrapper:        ✗ PID $pid (stale, would be cleaned on next start)"
    fi
  else
    echo "Wrapper:        ○ not started by this project's PID file"
  fi
  if command -v pm2 >/dev/null 2>&1; then
    local pm2_status
    if command -v jq >/dev/null 2>&1; then
      pm2_status="$(pm2 jlist 2>/dev/null | jq -r '.[] | select(.name=="ulticode-arthas") | .pm2_env.status' 2>/dev/null | head -1 || true)"
    else
      pm2_status="$(pm2 jlist 2>/dev/null | python3 -c 'import json,sys
for a in json.load(sys.stdin):
    if a.get("name")=="ulticode-arthas":
        print(a.get("pm2_env",{}).get("status",""))' 2>/dev/null | head -1 || true)"
    fi
    if [ -n "$pm2_status" ]; then
      echo "PM2:            ✓ ulticode-arthas $pm2_status"
    else
      echo "PM2:            ○ ulticode-arthas not in PM2"
    fi
  fi
  echo "Log:            $LOG_FILE"
  return 0
}

# 重启
cmd_restart() {
  cmd_stop
  sleep 1
  cmd_start
}

# 日志
cmd_logs() {
  if [ ! -f "$LOG_FILE" ]; then
    echo "○ No log file yet: $LOG_FILE"
    return 0
  fi
  exec tail -F "$LOG_FILE"
}

# 帮助
cmd_help() {
  sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
}

case "${1:-help}" in
  start)   cmd_start ;;
  stop)    cmd_stop ;;
  restart) cmd_restart ;;
  status)  cmd_status ;;
  logs)    cmd_logs ;;
  help|-h|--help) cmd_help ;;
  *)
    echo "Unknown command: $1" >&2
    cmd_help >&2
    exit 1
    ;;
esac
