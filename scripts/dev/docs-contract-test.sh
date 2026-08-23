#!/usr/bin/env bash
set -euo pipefail

# scripts/dev/docs-contract-test.sh — documentation-drift guardrails.
#
# Split from architecture-contract-test.sh: these assertions only check that
# user-facing documentation still describes the shipped entry points and
# architecture. They fail on wording drift, not on behaviour regressions;
# code-seam contracts live in architecture-contract-test.sh.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "Documentation contract failed: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  grep -F -- "$text" "$ROOT_DIR/$file" >/dev/null \
    || fail "$file does not contain: $text"
}

not_contains() {
  local file="$1" text="$2"
  [[ -f "$ROOT_DIR/$file" ]] || fail "missing guarded file: $file"
  ! grep -F -- "$text" "$ROOT_DIR/$file" >/dev/null \
    || fail "$file contains stale or bypass text: $text"
}

not_contains README.md 'pm2 start ecosystem.config.cjs'
not_contains README.md 'pm2 restart ulticode-auth ulticode-admin'
contains README.md './scripts/dev/up.sh --mode dev-lite'
contains README.md './scripts/dev/up.sh --mode dev-full'
contains README.md './scripts/dev/up.sh --mode legacy-rollback'

not_contains PROJECT_DOCUMENTATION.md \
  'Admin 的查询 Seam 仍过细'
contains PROJECT_DOCUMENTATION.md \
  'Admin 查询已收敛为粗粒度 query slices'
not_contains PROJECT_DOCUMENTATION.md \
  'Submission 读侧的 facts enrichment、数据库物理隔离、App 双轨兼容、Admin Seam 聚合和运维文档仍需后续任务完成'
contains PROJECT_DOCUMENTATION.md \
  'Judge normal dev-lite/dev-full 使用 provider-owned JudgeQueue Streams'

not_contains CONTEXT.md 'the App runtime role'
contains CONTEXT.md 'User Directory View'
contains CONTEXT.md 'the worker role of the Notification'

echo "Documentation contract: PASS"
