#!/usr/bin/env bash
# Claude Code SessionEnd hook: 终止 Arthas MCP wrapper
#
# 设计意图:
#   - 不动 Spring Boot 进程 — 只清理 wrapper 和 (如可) arthas agent
#   - arthas agent 本身在目标 JVM 内,默认保留: 留着可以避免下次 SessionStart
#     重新 attach 时的 latency;若需彻底脱离,见下 "可选: detach agent"
#   - SIGTERM 给 wrapper 一次优雅退出的机会;超时后 SIGKILL
#
# 退出语义: 永远 0 (hook 失败不应阻塞 SessionEnd)
set -uo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
PID_DIR="${PROJECT_DIR}/.claude/.arthas"
PID_FILE="${PID_DIR}/wrapper.pid"

if [ ! -f "$PID_FILE" ]; then
  exit 0
fi

WRAPPER_PID="$(cat "$PID_FILE" 2>/dev/null || true)"
rm -f "$PID_FILE"

if [ -z "${WRAPPER_PID}" ] || ! kill -0 "${WRAPPER_PID}" 2>/dev/null; then
  exit 0
fi

# SIGTERM,等 5s;还在就 SIGKILL
kill -TERM "${WRAPPER_PID}" 2>/dev/null || true
for _ in $(seq 1 10); do
  kill -0 "${WRAPPER_PID}" 2>/dev/null || break
  sleep 0.5
done
if kill -0 "${WRAPPER_PID}" 2>/dev/null; then
  kill -KILL "${WRAPPER_PID}" 2>/dev/null || true
fi

# 兜底: 通过 pgrep 找名字像 start-arthas 的残留进程 (PID 文件丢失/被覆盖场景)
# 跳过: 静默最佳 — SessionEnd 不该是噪声源

echo "[arthas-hook] Stopped arthas wrapper (PID ${WRAPPER_PID})"
exit 0
