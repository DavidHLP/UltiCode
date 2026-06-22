#!/usr/bin/env bash
# Claude Code SessionStart hook: 拉起 Arthas MCP wrapper (后台)
#
# 设计意图:
#   - 跟随 Claude Code 生命周期 — SessionStart 时确保 MCP 端点可达
#   - SessionEnd 只清理 hook 自己拉起的 wrapper, 不会动 PM2 拉起的 (避免与 PM2 监督冲突)
#   - 异步触发: hook 自身必须立即退出 (否则会阻塞 Claude Code 启动)
#   - 重复进入安全: 端口已监听 / PID 文件已存在 → 直接返回 (避免重复 attach)
#   - 单实例约束: 用 PID 文件 + 端口双重检测
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

# 端口检测: 优先 lsof (Linux/macOS), 回退 netstat (Windows Git Bash 缺 lsof)
# 与 start-arthas.sh 保持一致, 否则本 hook 在 Windows 上会因 lsof 缺失报 'command not found'
port_listening() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti :"$port" >/dev/null 2>&1
    return $?
  fi
  netstat -ano 2>/dev/null | grep -qE "[:.]${port}[[:space:]]"
}

# 1) 端口已监听: 跳过启动
#    场景: 上一轮 attach 留下的 agent 仍活, 或 PM2 在跑 wrapper
if port_listening "$ARTHAS_PORT"; then
  echo "[arthas-hook] Port ${ARTHAS_PORT} already listening, skip"
  exit 0
fi

# 2) 已有 wrapper PID 文件: 验证进程真在 (PM2/hook/cli 任何一路拉起的)
#    PID 文件格式: "PID\nLAUNCHER" (两行)
if [ -f "$PID_FILE" ]; then
  OLD_PID="$(head -1 "$PID_FILE" 2>/dev/null || true)"
  OLD_LAUNCHER="$(tail -1 "$PID_FILE" 2>/dev/null || true)"
  if [ -n "${OLD_PID}" ] && kill -0 "${OLD_PID}" 2>/dev/null; then
    echo "[arthas-hook] Wrapper already running (PID ${OLD_PID}, launcher=${OLD_LAUNCHER}), skip"
    exit 0
  fi
  # 进程不在,清理陈旧文件
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
#    ULTICODE_ARTHAS_LAUNCHER=hook 让 wrapper 知道是被 hook 拉起的 (供 SessionEnd 互斥用)
ULTICODE_ARTHAS_LAUNCHER=hook \
  nohup setsid bash -c "ULTICODE_ARTHAS_LAUNCHER=hook '$WRAPPER' >> '$LOG_FILE' 2>&1" </dev/null >/dev/null 2>&1 &
WRAPPER_PID=$!
disown "$WRAPPER_PID" 2>/dev/null || true

# 注意: 这里不写 PID_FILE — wrapper 自己会在第一时间写 (包括 launcher=hook 标记)
# 这样可以避免 hook 写的 PID 和 wrapper 实际 PID 不一致的问题
echo "[arthas-hook] Spawned arthas wrapper (PID ${WRAPPER_PID}), log: ${LOG_FILE}"
exit 0
