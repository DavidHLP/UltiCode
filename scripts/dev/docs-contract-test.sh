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
contains README.md './scripts/runbooks/owner-schema-contraction.sh preflight'
contains scripts/dev/up.sh 'dev-lite=remote owner reads/DB search/no Search worker'
contains init-db/README.md '## Owner schema contraction (P1-DATA-001)'
contains init-db/README.md 'flyway-contraction.conf'
contains scripts/README.md 'owner-schema-contraction.sh'
contains services/docs/SERVICES_ISSUES.md 'P1-DATA-001 also routes normal user/contest/admin/statistics/generation reads'

not_contains docs/architecture/overview.md \
  'Admin 的查询 Seam 仍过细'
contains docs/architecture/overview.md \
  'Admin 查询已收敛为粗粒度 query slices'
not_contains docs/architecture/overview.md \
  'Submission 读侧的 facts enrichment、数据库物理隔离、App 双轨兼容、Admin Seam 聚合和运维文档仍需后续任务完成'
contains docs/architecture/overview.md \
  'Judge normal dev-lite/dev-full 使用 provider-owned JudgeQueue Streams'
contains docs/operations/database-migrations.md \
  '#### Submission read owner cutover 与 schema contraction'
contains services/docs/SCHEDULER_RUNBOOK.md 'P3-SCHED-001'
contains services/docs/SCHEDULER_RUNBOOK.md 'ThreadPoolTaskScheduler'

not_contains CONTEXT.md 'the App runtime role'
contains CONTEXT.md 'User Directory View'
contains CONTEXT.md 'the worker role of the Notification'

echo "Documentation contract: PASS"
