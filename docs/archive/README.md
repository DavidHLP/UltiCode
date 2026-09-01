# 归档资料

本目录保存已完成阶段的审计、迁移和决策材料。归档文档用于追溯，不是当前实现、状态或操作命令的唯一依据。

## Architecture remediation 2026-08

- [`PROJECT_DOCUMENTATION-2026-08-30.md`](architecture-remediation-2026-08/PROJECT_DOCUMENTATION-2026-08-30.md)：原临时统一文档完整快照；其当前主题已拆分到 `docs/architecture/`、`docs/development/`、`docs/operations/`、`docs/project/` 和 `docs/api/`。
- [`CODEX_HANDOFF-2026-09-01.md`](architecture-remediation-2026-08/CODEX_HANDOFF-2026-09-01.md)：已闭环 remediation 的 Codex handoff；当前延续入口是 [`../project/task-continuation.md`](../project/task-continuation.md)。
- [`remediation-traceability.md`](architecture-remediation-2026-08/remediation-traceability.md)：Finding → task → implementation → evidence → commit 的完整映射。

## 仍由 flow 工具维护的证据

`.auto-flow/` 保留在仓库原位：`HANDOFF.yaml` 与 `TASKS.yaml` 是机器可读的当前控制状态，`evidence/` 是已提交的验证原件；`DECISIONS.md`、`EVIDENCE.md`、`COVERAGE.md`、`RESUME.md` 和 `WORKLOG.md` 仍是该工具的历史/证据表面。它们没有被删除或移动，避免破坏恢复、链接和审计路径。面向新成员的精简入口见 [`../project/current-status.md`](../project/current-status.md)。

## 已删除的中间产物

`.reports/docs-update.txt` 与 `.reports/codemap-diff.txt` 是 2026-06-19 的生成报告，记录一次已被后续 docs 变更 supersede 的旧目录重建；无当前引用。唯一仍有价值的历史事实（旧 docs tree 曾被删除、随后重建）保留在 Git 历史中，本次不再让报告文件充当文档入口。

## 其他历史审计

- [`contest/REVIEW_V3.md`](contest/REVIEW_V3.md)、[`contest/I18N_AUDIT_R10.md`](contest/I18N_AUDIT_R10.md) 和 [`contest/F-01-STATE_MACHINE_AUDIT.md`](contest/F-01-STATE_MACHINE_AUDIT.md)：2026-06 R10 评审与状态机/i18n 证据。
- [`contest/_archive/SECURITY_REVIEW_2026-06-17.md`](contest/_archive/SECURITY_REVIEW_2026-06-17.md) 与 [`contest/_archive/LOW_REMAINING_R8.6_2026-06-17.md`](contest/_archive/LOW_REMAINING_R8.6_2026-06-17.md)：已完成阶段的安全/低优先级审计记录。
