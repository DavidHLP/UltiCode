# UltiCode

UltiCode 是一个全栈在线编程平台，提供题库、竞赛、在线评测、题解、社区和管理后台。

## 核心能力

- Console：练习、竞赛、提交、题解、论坛、成就和个人中心。
- Management：题目/竞赛管理、审核、审计、通知和运营查询。
- Judge：独立 Worker 消费 Submission 的 Redis Streams，使用 Docker 沙箱执行代码。
- Owner 服务：Auth、Admin、App、Submission、Notification 分别负责自己的数据和写入边界；Search 是派生索引 Worker。

## 技术栈摘要

- 后端：Java 17、Spring Boot 3.2.5、Maven、MyBatis-Plus、Flyway、Dubbo。
- 前端：Vue 3.5、TypeScript、Vite、pnpm；Console 与 Management 是两个独立应用。
- 基础设施：Docker Compose、MySQL 9.1、Redis 7、Nacos 2.3.2；Search 使用 MeiliSearch。

## 快速开始

前置条件：Docker Compose v2、mise、Node.js `^20.19.0 || >=22.12.0`、pnpm 10+、PM2，以及 `curl`、`timeout`、`openssl`。后端启动由仓库内 `services/mvnw` 和 mise 管理的 Zulu Java 17 执行。

```bash
./scripts/dev/init-env.sh
./scripts/dev/up.sh --mode dev-lite
```

`init-env.sh` 会把 `SUBMISSION_CUTOVER_COMPLETE` 生成为 `false`。`dev-lite` 和 `dev-full` 都要求 `APP_SUBMISSION_ROUTING_MODE=remote` 与已完成的 Submission cutover marker；未满足时 `up.sh` 会 fail closed。首次 checkout 请先阅读[数据库迁移](docs/operations/database-migrations.md)，按授权的 cutover/backfill runbook 完成门禁，再启动正常模式。

`dev-lite` 使用数据库 Search、不启动 Search worker，默认不启动两个前端；需要浏览器界面可另运行：

```bash
./scripts/dev/up.sh --frontend-only
```

`dev-full` 会启用 indexed Search、Search worker 和两个前端，但仍受同一 cutover gate 保护：

```bash
./scripts/dev/up.sh --mode dev-full
```

当前版本不再支持 `legacy-rollback`；`up.sh` 对该旧模式和未知 mode fail closed。生产回滚只能使用部署方保留并校验的上一份完整 release descriptor，不能通过当前二进制恢复旧实现（见[部署、发布与回滚](docs/operations/deployment.md)）。

## 文档入口

- [文档导航](docs/index.md)：按架构、开发、运维、API、状态和历史查找权威来源。
- [当前状态](docs/project/current-status.md)：仓库完成度、外部边界和当前验证入口。
- [架构总览](docs/architecture/overview.md)：服务边界、Owner/Worker 拓扑和关键约束。
- [本地开发与测试](docs/development/local-setup.md)、[测试与质量](docs/development/testing.md)。
- [部署、发布与回滚](docs/operations/deployment.md)、[数据库迁移](docs/operations/database-migrations.md)。
- [认证 API](docs/api/authentication.md)、[API 与内部契约](docs/api/overview.md)。

实现、配置、迁移脚本、测试和可执行 runbook 是行为真相；本仓库没有生产环境，生产部署、真实流量和外部凭据由部署方负责。

## 贡献

开始前阅读 [`AGENTS.md`](AGENTS.md) 与[文档导航](docs/index.md)。修改后运行与触碰面对应的 `./scripts/dev/test.sh` gate，并检查 `git diff --check`。不得提交 `.env`、凭据、私钥或生成的运行时材料。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
