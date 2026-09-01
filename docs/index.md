# UltiCode 文档

本文档目录是长期文档的导航入口。实现、配置、迁移脚本和测试是运行行为的最终依据；文档解释职责、边界、操作顺序和决策原因。

## 推荐阅读顺序

1. [项目当前状态](project/current-status.md)
2. [架构总览](architecture/overview.md)
3. [模块与所有权](architecture/modules.md)
4. [数据流与契约](architecture/data-flow.md)
5. [本地开发](development/local-setup.md) 与 [测试](development/testing.md)
6. [部署与回滚](operations/deployment.md)
7. 按任务需要查阅 [数据库迁移](operations/database-migrations.md)、[认证 API](api/authentication.md)、[排障](development/troubleshooting.md)

## 文档角色

| 角色 | 权威入口 | 用途 |
| --- | --- | --- |
| Constitution | [`AGENTS.md`](../AGENTS.md) 与最近的嵌套 `AGENTS.md` | 代理与贡献者必须遵守的规则 |
| Map | [`architecture/`](architecture/overview.md) | 现有模块、边界、数据流和查找入口 |
| Status | [`project/current-status.md`](project/current-status.md) | 当前完成度、验证入口和外部边界 |
| Issues | [`services/docs/SERVICES_ISSUES.md`](../services/docs/SERVICES_ISSUES.md) | Services 问题状态与外部触发条件的唯一注册表 |
| Plan | [`project/roadmap.md`](project/roadmap.md) | 尚未完成的外部采用顺序与明确延后项 |
| History | [`architecture/decisions/`](architecture/decisions/README.md) 与 [`archive/`](archive/README.md) | 架构决策、迁移记录、验证证据和有意删除项 |
| Operations | [`operations/`](operations/deployment.md) 与 [`services/docs/`](../services/docs/SERVICES_ISSUES.md) | 可执行发布、迁移、监控、恢复和问题登记 |

## 按主题查找

### 架构与契约

- [架构总览](architecture/overview.md)：五个 Data Owner、两个 Worker、运行拓扑和当前收敛状态。
- [模块与所有权](architecture/modules.md)：Owner/Worker 职责、服务边界、共享代码政策。
- [数据流与契约](architecture/data-flow.md)：请求链、Owner 数据、Dubbo、事务和异步可靠性。
- [安全架构](architecture/security.md)：Cookie、CSRF、JWT/JWKS、委托身份和 WebSocket 信任边界。
- [ADR 索引](architecture/decisions/README.md)：不可逆或需要持续记忆的设计决定。

### 开发

- [本地开发](development/local-setup.md)：依赖、启动、入口和开发模式。
- [配置](development/configuration.md)：环境变量、密钥边界和 profile 规则。
- [测试与质量](development/testing.md)：统一测试入口、分层测试和验证矩阵。
- [编码指南](development/coding-guidelines.md)：规则入口与项目约定。
- [排障](development/troubleshooting.md)：常见本地故障、日志和恢复路径。
- [Management i18n](development/i18n-design.md)：管理端翻译结构与完整性检查。
- [Garden 设计系统](development/design-system.md)：共享 token 和视觉契约入口。

### 运维

- [部署、发布与回滚](operations/deployment.md)
- [数据库迁移](operations/database-migrations.md)
- [监控与 SLO](operations/monitoring.md)
- [备份与恢复](operations/backup-and-recovery.md)
- [事件响应](operations/incident-response.md)
- [数据库脚本 README](../init-db/README.md)
- [开发与运维脚本 README](../scripts/README.md)
- [Services 问题唯一注册表](../services/docs/SERVICES_ISSUES.md)

### 项目状态与历史

- [当前状态](project/current-status.md)
- [路线图](project/roadmap.md)
- [已知问题与外部门禁](project/known-issues.md)
- [任务延续](project/task-continuation.md)
- [归档目录](archive/README.md)

## 原位保留的专业文档

以下文档由对应模块或工具维护，不复制到本目录：

- `services/docs/`：契约、韧性、租约、优雅退出、观测、调度和 Worker SLO 运行手册。
- `init-db/`：Flyway 命令、Owner migration、baseline、seed 和回滚说明。
- `scripts/`：本地启动、测试、runbook 和 smoke 入口。
- [`CONTEXT.md`](../CONTEXT.md)：供工具使用的当前领域术语表，不是任务状态或会话记录。
- [Sandbox harness](../docker/sandbox/harness/README.md) 与 [Prometheus rules](../docker/prometheus/README.md)：运行时执行和观测专业文档。
- [Statusline reference](../scripts/statusline/README.md)：开发工具状态栏的实现与设计说明。
- `packages/design-system/docs/`：设计 token 的完整规范。
- `docker/`：基础设施说明；[`assets/screenshots/README.md`](../assets/screenshots/README.md)：UI 参考截图索引。

## 历史资料

[归档目录](archive/README.md) 保留已完成 remediation wave 的任务状态、决策、证据和原始汇总文档。归档内容只用于追溯，不取代当前实现或状态文档。
