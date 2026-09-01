# 已知问题与外部门禁

## 唯一状态注册表

`services/docs/SERVICES_ISSUES.md` 是 Services Finding、状态和触发条件的唯一注册表；本文不复制其问题正文。

当前没有 repository-actionable `OPEN` 项。以下类型仍是 `DEFERRED`，需要真实环境、指标或授权：

- **SVC-006**：Admin 用户跨 Owner 同步聚合只有在延迟/可用性指标证明事件读模型必要时才事件化。
- **SVC-007**：生产多主机 HA 需要真实节点和明确 availability SLO；HA Compose 只是 reference profile。
- **SVC-008**：Judge remote/rootless daemon、证书、workspace、节点故障和生产 smoke 需要部署方。
- **SVC-009**：真实 telemetry receiver、阈值调优、on-call webhook、SLO 报表和恢复演练需要生产流量。
- **SVC-010**：真实 mixed-version 并存和独立 rollback 历史随部署自然积累。

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
| 根 `CODEX_HANDOFF.md` 会话 handoff | `project/task-continuation.md` + `.auto-flow/HANDOFF.yaml` | 新 harness 明确需要新 handoff 格式 |
| App Submission local writer/rejudge provider | `backend-submission` owner contract | 新版本化兼容窗口和 owner 评审 |
| App Notification persistence/reconciliation SQL | Notification owner + intent/Inbox | 新 owner 变更 ADR |
| tracked Redis ACL hash snapshot | runtime `REDIS_ACL_DIR` materialization | 不重新提交 secret-derived verifier |
| broad Submission composite contracts | 2.0.0 narrow contracts | 外部 N-1 consumer drain 证据 |

历史材料只通过 [`../archive/README.md`](../archive/README.md) 进入；当前状态以本页、Services registry 和实现为准。
