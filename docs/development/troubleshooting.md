# 排障

## 启动与环境

- **服务启动绕过策略**：不要直接使用 PM2/Maven 启动 runtime；用 `scripts/dev/up.sh --mode dev-lite|dev-full`，否则会绕过 manifest、Owner readiness、migration 和 rollback gate。
- **环境变量改动未生效**：执行 `./scripts/dev/up.sh --mode dev-lite --skip-install`，不要手工重启单个进程。
- **Java 17 cgroup-v2 异常**：优先使用 mise 管理的 Zulu 17；旧的本地 17.0.2 可能在 JVM processor discovery 阶段失败。
- **Redis/MySQL/Testcontainers 不可达**：区分宿主权限/凭据/容器状态与源码失败。记录准确错误；不能把 `BLOCKED_EXTERNAL` 写成测试通过。
- **中文乱码**：容器内 MySQL 客户端显式使用 `--default-character-set=utf8mb4`，否则可能产生双重编码。操作说明见 [`CLAUDE.md`](../../CLAUDE.md)。

## 认证与路由

- 生产 Cookie policy、JWT/JWKS、CSRF、Nacos/Dubbo identity 失败时应 fail closed。不要通过关闭 CSRF、改用 access token refresh 或放宽 `anyRequest` 修复。
- WebSocket 只读取 `access_token` cookie；不要在 URL、query 或 STOMP 客户端 token 中传递凭据。
- Admin/privileged endpoint 必须同时检查路由和方法权限；审计 actor 取 principal/delegation，不取请求字段。

## 队列、Outbox 与 Worker

- Search/Judge/Notification backlog 先检查 PM2 状态、Redis ACL、PEL、DLQ 和下游健康；不要先删除 stream、PEL、ledger 或 version hash。
- Judge/Submission 使用 generation/attempt fence；重放前确认新 generation 和 owner receipt，避免旧 verdict 覆盖新状态。
- Notification poison event 进入 DB inbox；Search/Judge DLQ 的 replay/discard 见 [`WORKER_SLO_RUNBOOK.md`](../../services/docs/WORKER_SLO_RUNBOOK.md)。
- 关闭服务时让 DrainGate 停止新 claim；保留未 ACK 的 PEL/lease 给现有 reaper 恢复。

## 迁移与部署

迁移问题先运行 preflight 和只读 parity/checksum；backfill 默认 dry-run，目标冲突停止并导出 failure artifact。生产 rollback 只能回到 schema-compatible artifact，不执行 downgrade。部署前检查 source commit、migration checksum、immutable digest manifest、Compose config 和健康 descriptor。

更详细的可执行步骤：

- [数据库迁移](../operations/database-migrations.md)
- [部署与回滚](../operations/deployment.md)
- [Services 问题注册表](../../services/docs/SERVICES_ISSUES.md)
- [Worker SLO Runbook](../../services/docs/WORKER_SLO_RUNBOOK.md)
