# UltiCode 文档地图

本文是 `docs/` 的唯一导航入口；当前目录只保留六个核心文档。实现、配置、迁移脚本、测试和可执行 runbook 是行为真相，核心文档只负责解释当前边界、入口和操作顺序。

## 默认读取路径

1. 先读根目录 [`AGENTS.md`](../AGENTS.md) 和受影响目录最近的 `AGENTS.md`。
2. 再读本页，确认当前主题和核心文档职责。
3. 按任务读取一个或多个核心文档：[`PRODUCT.md`](PRODUCT.md)、[`ARCHITECTURE.md`](ARCHITECTURE.md)、[`DEVELOPMENT.md`](DEVELOPMENT.md)、[`OPERATIONS.md`](OPERATIONS.md) 或 [`REFERENCE.md`](REFERENCE.md)。
4. 回到实际代码、配置、脚本和测试，确认文档没有超出可执行事实。
5. 需要运行细节时，读取仓库外部 owner 维护的 `services/docs/`、`init-db/README.md`、`scripts/README.md` 或包内说明；这些不是 `docs/` 的第二套当前主题入口。

## 核心文档与职责

| 事实类型 | 当前权威入口 |
| --- | --- |
| 产品定位与领域术语 | [`PRODUCT.md`](PRODUCT.md) |
| 当前架构、所有权、数据流、安全 | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| 开发、测试、配置、规则、排障 | [`DEVELOPMENT.md`](DEVELOPMENT.md) |
| 部署、迁移、备份、监控、事件 | [`OPERATIONS.md`](OPERATIONS.md) |
| API、认证与内部契约 | [`REFERENCE.md`](REFERENCE.md)；`services/api/` 与各 provider/controller |
| 文档入口和维护规则 | 本页 |

## 当前核心文档

- [`PRODUCT.md`](PRODUCT.md)：定位、用户角色、核心能力、领域对象和边界。
- [`ARCHITECTURE.md`](ARCHITECTURE.md)：服务边界、Owner/Worker 拓扑、模块所有权、数据流、契约、事务和安全。
- [`DEVELOPMENT.md`](DEVELOPMENT.md)：本地启动、开发模式、测试入口、配置、编码规则和排障。
- [`OPERATIONS.md`](OPERATIONS.md)：部署发布、回滚、数据库迁移、备份恢复、监控 SLO 和事件响应。
- [`REFERENCE.md`](REFERENCE.md)：API 入口、认证流程、跨 Owner 契约、错误与兼容。

## 文档维护规则

- `docs/` 只保留以上六个核心文档；不再创建并行的 API、状态、路线图、ADR、evidence、archive 或专业主题入口。
- 不创建按任务、按会话、按日期的长期文档，不把临时验证日志写入 `docs/`。
- 当前事实必须回到代码、配置、脚本和测试核实；开放问题继续由 `services/docs/SERVICES_ISSUES.md` 维护。
- 修改命令、路径、契约、架构边界或行为时，同步更新对应核心文档，并运行适用门禁。
- 不通过 `.gitignore` 隐藏非核心文档；非核心内容从 `docs/` 中明确删除，避免被误读为当前事实。
