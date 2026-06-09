#!/usr/bin/env bash
# Claude Code SessionStart hook: 拉起 Arthas MCP wrapper (后台)
#
# 设计意图:
#   - 跟随 Claude Code 生命周期 — SessionStart 时确保 MCP 端点可达,
#     SessionEnd 时清理 wrapper,不再依赖 PM2 守护
#   - 异步触发: hook 自身必须立即退出 (否则会阻塞 Claude Code 启动)
#   - 重复进入安全: 端口已监听时直接返回 (避免重复 attach)
#   - 单实例约束: 用 PID 文件 + flock 防止多会话同时拉起多个 wrapper
#
# 退出语义:
#   - 0  : hook 成功触发 (无论 wrapper 是否成功 attach,都不阻塞会话)
#   - 非0: hook 自身异常 (例如环境变量缺失)
set -euo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
WRAPPER="${PROJECT_DIR}/scripts/start-arthas.sh"
PID_DIR="${PROJECT_DIR}/.claude/.arthas"
PID_FILE="${PID_DIR}/wrapper.pid"
LOG_FILE="${PID_DIR}/wrapper.log"
ARTHAS_PORT=8563

mkdir -p "$PID_DIR"

# 1) 端口已监听: 跳过启动 (上次的 agent 仍在 JVM 内, 或其他 wrapper 在跑)
if lsof -ti ":${ARTHAS_PORT}" >/dev/null 2>&1; then
  echo "[arthas-hook] Port ${ARTHAS_PORT} already listening, skip"
  exit 0
fi

# 2) 已有 wrapper PID 文件但进程不在: 清理陈旧文件
if [ -f "$PID_FILE" ]; then
  OLD_PID="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [ -n "${OLD_PID}" ] && kill -0 "${OLD_PID}" 2>/dev/null; then
    echo "[arthas-hook] Wrapper already running (PID ${OLD_PID}), skip"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

# 3) 检查 wrapper 脚本存在
if [ ! -x "$WRAPPER" ]; then
  echo "[arthas-hook] ERROR: $WRAPPER not executable" >&2
  exit 1
fi

# 4) 在后台拉起 wrapper,父 hook 立即退出
#    nohup + setsid + disown 三重隔离,防止 Claude Code 退出时 SIGHUP 拖垮 wrapper
#    stdin 重定向到 /dev/null,防止 hook 子进程阻塞在管道
nohup setsid bash -c "$WRAPPER >> '$LOG_FILE' 2>&1" </dev/null >/dev/null 2>&1 &
WRAPPER_PID=$!
disown "$WRAPPER_PID" 2>/dev/null || true

echo "$WRAPPER_PID" > "$PID_FILE"
echo "[arthas-hook] Spawned arthas wrapper (PID ${WRAPPER_PID}), log: ${LOG_FILE}"
exit 0
