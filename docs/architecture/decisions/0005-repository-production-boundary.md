# ADR-0005：Repository 与 production authority 边界

- Status: Accepted
- Date: 2026-08-30

## Decision

源码、配置、迁移、测试、CI/CD、runbooks 和短时 disposable Compose/DinD 是 repository scope 的证据。它们不得被描述为 production deployment、traffic drain、credential rotation、HA failover、真实 SLO、registry promotion、remote Judge 或生产 migration 已发生。

可选脚本缺少真实外部输入时必须输出 `BLOCKED_EXTERNAL` 并 fail closed；reviewer transport 缺失记录为 `BLOCKED_TOOL`。生产采用需要部署方提供环境、secret/certificate authority、数据库/网络/流量权限和独立 approval。

## Consequences

状态文档可以清晰区分 `Repository Implemented`、`Locally Validated`、`Staging Validated`、`Production Applied` 和 `External Execution Required`。删除历史记录或通过 disposable rehearsal 不能改写生产状态。
