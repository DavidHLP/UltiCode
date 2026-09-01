# 归档资料

本目录保存已完成阶段的审计、迁移和决策材料。归档文档用于追溯，不是当前实现、状态或操作命令的唯一依据。

## Architecture remediation 2026-08

- [`PROJECT_DOCUMENTATION-2026-08-30.md`](architecture-remediation-2026-08/PROJECT_DOCUMENTATION-2026-08-30.md)：原临时统一文档完整快照；其当前主题已拆分到 `docs/architecture/`、`docs/development/`、`docs/operations/`、`docs/project/` 和 `docs/api/`。
- [`remediation-traceability.md`](architecture-remediation-2026-08/remediation-traceability.md)：Finding → task → implementation → evidence → commit 的完整映射。

## 2026-09-01：`.auto-flow/` 已退役

原 `.auto-flow/` 运行台账与证据目录（`TASKS.yaml`、`DECISIONS.md`、`EVIDENCE.md`、`COVERAGE.md`、`RESUME.md`、`HANDOFF.yaml`、`WORKLOG.md` 与 `evidence/architecture-remediation-20260830/`）已于 2026-09-01 文档治理任务中删除；其内容保留在本分支 Git 历史（flow checkpoints `cef925a`、`fc31cf35`、`1e69b5b5` 等），不再作为当前工作树证据。当前状态见 [`../project/current-status.md`](../project/current-status.md)，精简 closure 见 [`../project/history/architecture-remediation-20260830.md`](../project/history/architecture-remediation-20260830.md)。

## 已删除的中间产物

`.reports/docs-update.txt` 与 `.reports/codemap-diff.txt` 是 2026-06-19 的生成报告，记录一次已被后续 docs 变更 supersede 的旧目录重建；无当前引用。唯一仍有价值的历史事实（旧 docs tree 曾被删除、随后重建）保留在 Git 历史中，本次不再让报告文件充当文档入口。

## 其他历史审计

- [`contest/REVIEW_V3.md`](contest/REVIEW_V3.md)、[`contest/I18N_AUDIT_R10.md`](contest/I18N_AUDIT_R10.md) 和 [`contest/F-01-STATE_MACHINE_AUDIT.md`](contest/F-01-STATE_MACHINE_AUDIT.md)：2026-06 R10 评审与状态机/i18n 证据。
- [`contest/_archive/SECURITY_REVIEW_2026-06-17.md`](contest/_archive/SECURITY_REVIEW_2026-06-17.md) 与 [`contest/_archive/LOW_REMAINING_R8.6_2026-06-17.md`](contest/_archive/LOW_REMAINING_R8.6_2026-06-17.md)：已完成阶段的安全/低优先级审计记录。
