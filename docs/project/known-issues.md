# 已知问题与外部门禁

## 唯一状态注册表

`services/docs/SERVICES_ISSUES.md` 是 Services Finding、状态和触发条件的唯一注册表；本文不复制其问题正文。

## 不要误判

- disposable Compose/DinD 通过 ≠ production applied。
- 删除旧文档/兼容文件 ≠ 文档中描述的生产问题已被生产修复。
- `BLOCKED_EXTERNAL` 只表示缺少外部输入或 authority；它不是可通过放宽安全门禁解决的错误。
- `BLOCKED_TOOL`（如 reviewer transport 未返回）不应写成 reviewer PASS。
- `tree_sitter_sql` 缺失只限制 Graphify 覆盖，不是 SQL 行为证明。

## 删除区（delete-zone）

以下名称已被当前体系替代，后续不得无理由重新创建：

| 旧入口/模式 | 替代 | 重开条件 |
| --- | --- | --- |
| 根 `PROJECT_DOCUMENTATION.md` 临时总文档 | `docs/` 主题文档 + `services/docs/SERVICES_ISSUES.md` | 新增文档治理 ADR |
| 根 `CODEX_HANDOFF.md` 会话 handoff | 不重建；当前状态见 `current-status.md`，历史见 `archive/`（原 `.auto-flow/HANDOFF.yaml` 已退役） | 新 harness 明确需要新 handoff 格式 |
| App Submission local writer/rejudge provider | `backend-submission` owner contract | 新版本化兼容窗口和 owner 评审 |
| App Notification persistence/reconciliation SQL | Notification owner + intent/Inbox | 新 owner 变更 ADR |
| tracked Redis ACL hash snapshot | runtime `REDIS_ACL_DIR` materialization | 不重新提交 secret-derived verifier |
| broad Submission composite contracts | 2.0.0 narrow contracts | 外部 N-1 consumer drain 证据 |

历史材料只通过 [`../archive/README.md`](../archive/README.md) 进入；当前状态以本页、Services registry 和实现为准。
