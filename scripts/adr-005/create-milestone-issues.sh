#!/usr/bin/env bash
# ADR-005 §4 #1: 10 个 milestone 录入 GitHub Issue tracker
# 用法: scripts/adr-005/create-milestone-issues.sh
# 前置: gh CLI v2.94.0+ 已认证 (DavidHLP/UltiCode)
#
# 本脚本**不会**自动跑, 留给用户在准备好时手动执行. 跑前请先看脚本
# 内容确认 milestone 列表与 body 模板符合预期.
#
# 安全: 脚本会先创建 5 个 label (idempotent, 重复跑不重复创建), 然后
# 顺序创建 10 个 issue, 每个 issue 跑通才进下一个 (set -e).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if ! command -v gh >/dev/null 2>&1; then
  echo "❌ gh CLI not found. Install: https://cli.github.com/" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "❌ gh not authenticated. Run: gh auth login" >&2
  exit 1
fi

echo "📋 Creating labels (idempotent)..."
# NAME 是除最后一段外的全部 (status:shipped 类含 :, 需用 awk 反向 split)
for LABEL in "milestone:0E8A16" "adr-005:1D76DB" "status:shipped:0E8A16" "status:pending:BFD4F2" "status:cutover-blocked:D93F0B"; do
  COLOR="${LABEL##*:}"                      # 最后一段 = 颜色
  NAME="${LABEL%:"$COLOR"}"                 # 去掉尾段 ":COLOR" = label 名
  NAME="${NAME%:}"                          # 兜底去掉残留 ":"
  gh label create "$NAME" --color "$COLOR" --description "ADR-005 milestone tracking" --force >/dev/null 2>&1 || true
done

# 10 个 milestone 定义
# 格式: ID|parent_adr|section|shipped|flag_key|env_var|default|label_override
declare -a MILESTONES=(
  "M1a|ADR-001|verdict-status-codec §2.1-§2.4|true|app.features.judge-queue.envelope-version|APP_FEATURES_JUDGE_QUEUE_ENVELOPE_VERSION|1|"
  "M1b|ADR-001|verdict-status-codec §2.5|partial|app.features.first-solve-notifications-enabled|APP_FEATURES_FIRST_SOLVE_NOTIFICATIONS_ENABLED|true|"
  "M2a|ADR-002|sandbox-hexagonal §2|true|code-execution.sandbox.d-form.enabled|SANDBOX_DFORM_ENABLED|true|"
  "M2b|ADR-002|sandbox-hexagonal §3 cutover|false||N/A|N/A|status:cutover-blocked"
  "M3a|ADR-003|queue-outbox-fencing §2.1|true|app.features.use-judge-outbox|USE_JUDGE_OUTBOX|false|"
  "M3b|ADR-003|queue-outbox-fencing §2.2-§2.3|true|app.features.use-generation-fence|USE_GENERATION_FENCE|false|"
  "M3c|ADR-003|queue-outbox-fencing §2.4|true|app.features.judge-queue.use-port|JUDGE_QUEUE_USE_PORT|false|"
  "M3d|ADR-003|queue-outbox-fencing §2.5 cutover|false||N/A|legacy|status:cutover-blocked"
  "M4a|ADR-004|notification-intents §2-§3|true|app.features.use-notification-intent|USE_NOTIFICATION_INTENT|false|"
  "M4b|ADR-004|notification-intents §4 cutover|false||N/A|legacy|status:cutover-blocked"
)

for M_DEF in "${MILESTONES[@]}"; do
  IFS='|' read -r MID PARENT SECTION SHIPPED FLAG ENV_VAR DEFAULT LABEL_OVERRIDE <<< "$M_DEF"
  if [[ -n "$LABEL_OVERRIDE" ]]; then
    STATUS_LABEL="$LABEL_OVERRIDE"
  elif [[ "$SHIPPED" == "true" ]]; then
    STATUS_LABEL="status:shipped"
  else
    STATUS_LABEL="status:pending"
  fi

  TITLE="[${MID}] ${SECTION} (${PARENT})"

  if [[ "$SHIPPED" == "true" ]]; then
    STATUS_BLOCK='- [x] 标记 shipped (ADR-005 §2.1 表已更新)'
  else
    STATUS_BLOCK='- [ ] 待开始
- [ ] 实现中
- [ ] 部署到 dev
- [ ] Canary gate 24h 通过 (见 ADR-005 §2.5)
- [ ] Rollback drill 完成 (见 ADR-005 §2.6)
- [ ] 标记 shipped (ADR-005 §2.1 表更新)'
  fi

  gh issue create \
    --title "$TITLE" \
    --label "milestone,adr-005,$STATUS_LABEL" \
    --body "$(cat <<EOF
## Milestone
**${MID}** (${PARENT} ${SECTION})

## Status
${STATUS_BLOCK}

## 改动范围
见 ${PARENT} §${SECTION} 与 ADR-005 §2.1

## Feature Flag 关联
- flag key: ${FLAG:-N/A}
- env var: ${ENV_VAR:-N/A}
- 默认值: ${DEFAULT:-N/A}

## Acceptance Criteria
1. 代码合并 + \`mvn verify\` 通过
2. CI 包含 features-on 与 features-off 两套 profile 都通过 (见 ADR-005 §4 #4)
3. 部署到 dev 环境, \`pm2 status ulticode-9001\` 24h 无 unplanned restart
4. 相关指标 (见 ADR-005 §2.5) 在阈值内
5. Rollback drill 完成, 耗时记入 ADR-005 §2.6
6. shipped 后更新 ADR-005 §2.1 表 \`shipped at\` 列

## 参考
- ${PARENT}
- ADR-005 (Playbook): see [wiki/.meta/PROJECT_STATUS_REPORT.md](../../wiki/.meta/PROJECT_STATUS_REPORT.md) for the original 10-milestone plan (now archived).
- docs/RUNBOOK.md §10 (Feature Flag 切换手册)
EOF
)"

  echo "✅ created: $TITLE"
done

echo ""
echo "🎉 10 milestone issues created. Verify with:"
echo "   gh issue list --label adr-005"
