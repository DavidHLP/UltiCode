<div align="center">

# UltiCode

**开源在线编程平台 · 从题目练习到竞赛评测**

**Java 17 · Spring Boot · Vue 3 · TypeScript · Docker**

[快速开始](#快速开始) · [界面预览](#界面预览) · [项目文档](docs/index.md) · [路线图](docs/project/roadmap.md) · [参与贡献](#参与贡献)

[MIT License](LICENSE) · [报告问题](https://github.com/DavidHLP/UltiCode/issues) · [CI 工作流](https://github.com/DavidHLP/UltiCode/actions/workflows/ci.yml)

</div>

---

## 项目介绍

UltiCode 是一个全栈在线编程平台，提供题库、竞赛、在线评测、题解、社区和管理后台。

## 核心能力

| 场景 | 能力 |
| --- | --- |
| 编程练习 | 题库、竞赛、代码提交与评测记录 |
| 学习与交流 | 题解、论坛、成就和个人中心 |
| 平台管理 | 题目与竞赛管理、审核、审计、通知和运营查询 |
| 代码评测 | 独立 Judge Worker 消费 Submission 的 Redis Streams，使用 Docker 沙箱执行代码 |

## 界面预览

以下为仓库维护的设计与回归参考截图，更多页面见[截图索引](assets/screenshots/README.md)。

| 用户端 · 题目详情 | 管理端 · 仪表板 |
| :---: | :---: |
| ![用户端深色主题题目详情](assets/screenshots/problem-detail-dark.png) | ![管理端深色主题仪表板](assets/screenshots/admin-dashboard-dark.png) |

## 技术栈摘要

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot、Maven、MyBatis-Plus、Flyway、Dubbo |
| 前端 | Vue 3、TypeScript、Vite、pnpm workspace |
| 基础设施 | Docker Compose、MySQL、Redis、Nacos、MeiliSearch |

## 快速开始

### 1. 获取项目

```bash
git clone https://github.com/DavidHLP/UltiCode.git
cd UltiCode
```

### 2. 准备环境

前置条件：Docker Compose v2、mise、Node.js `^20.19.0 || >=22.12.0`、pnpm 10+、PM2，以及 `curl`、`timeout`、`openssl`。后端启动由仓库内 `services/mvnw` 和 mise 管理的 Zulu Java 17 执行。

贡献者也可以直接使用 Dev Containers/Codespaces；创建容器只安装依赖，
随后默认执行 unit 验证，不自动启动全栈：

```bash
./ulticode doctor --json
```

```bash
./scripts/dev/init-env.sh
```

### 3. 完成数据库准备

> **首次启动必读**
>
> `init-env.sh` 会把 `SUBMISSION_CUTOVER_COMPLETE` 生成为 `false`。`dev-lite` 和 `dev-full` 都要求 `APP_SUBMISSION_ROUTING_MODE=remote` 与已完成的 Submission cutover marker；未满足时启动脚本会拒绝执行。
>
> 请先阅读[数据库迁移](docs/operations/database-migrations.md)，按授权的 cutover/backfill runbook 完成迁移与验证。不要仅修改标记来跳过检查。

### 4. 启动开发环境

完成上述准备后，选择适合的模式：

| 模式 | 搜索 | 前端 |
| --- | --- | --- |
| `dev-lite` | 数据库 Search，不启动 Search Worker | 默认不启动 |
| `dev-full` | 索引 Search，启用 Search Worker | 启动两个前端 |

```bash
./scripts/dev/up.sh --mode dev-lite
```

`dev-lite` 使用数据库 Search、不启动 Search worker，默认不启动两个前端；需要浏览器界面可另运行：

```bash
./scripts/dev/up.sh --frontend-only
```

`dev-full` 会启用 indexed Search、Search worker 和两个前端，但仍受同一 cutover gate 保护：

```bash
./scripts/dev/up.sh --mode dev-full
```

前端启动后访问：[Console 用户端](http://localhost:9002) · [Management 管理端](http://localhost:9003)。更多 scope、日志与排障入口见[本地开发](docs/development/local-setup.md)。

<details>
<summary>可选 Core 试点与回滚说明</summary>

Core 收敛试点使用显式 scope，保持 Judge 独立进程：

```bash
./scripts/dev/up.sh --scope core
./scripts/dev/test.sh core
```

Core parent 监听 `9108`，readiness 为 `/api/v1/core/health/ready`；该 profile
当前用于 owner assembly 和边界验证，尚未替代默认 distributed topology。

当前版本不再支持 `legacy-rollback`；`up.sh` 对该旧模式和未知 mode fail closed。生产回滚只能使用部署方保留并校验的上一份完整 release descriptor，不能通过当前二进制恢复旧实现（见[部署、发布与回滚](docs/operations/deployment.md)）。

</details>

## 项目结构

```text
UltiCode/
├── apps/
│   ├── console/       # 用户端
│   └── management/    # 管理端
├── packages/          # 前端共享包
├── services/          # 后端服务、API 契约与 Worker
├── init-db/           # 数据库初始化与 Flyway migrations
├── docker/            # 基础设施与评测沙箱
├── scripts/           # 开发、验证与运维入口
└── docs/              # 架构、开发与部署文档
```

默认采用 **distributed** 拓扑。Auth、Admin、App、Submission、Notification 分别负责自己的数据和写入边界；Judge 负责评测，Search 维护派生索引。详见[架构总览](docs/architecture/overview.md)。

## 文档入口

- [文档导航](docs/index.md)：按架构、开发、运维、API、状态和历史查找权威来源。
- [当前状态](docs/project/current-status.md)：仓库完成度、外部边界和当前验证入口。
- [架构总览](docs/architecture/overview.md)：服务边界、Owner/Worker 拓扑和关键约束。
- [本地开发与测试](docs/development/local-setup.md)、[测试与质量](docs/development/testing.md)。
- [部署、发布与回滚](docs/operations/deployment.md)、[数据库迁移](docs/operations/database-migrations.md)。
- [认证 API](docs/api/authentication.md)、[API 与内部契约](docs/api/overview.md)。

实现、配置、迁移脚本、测试和可执行 runbook 是行为真相；本仓库没有生产环境，生产部署、真实流量和外部凭据由部署方负责。

## 参与贡献

欢迎通过 [Issues](https://github.com/DavidHLP/UltiCode/issues) 报告问题、讨论改进，或提交 Pull Request。

1. 报告问题时，附上复现步骤、预期与实际行为，以及脱敏后的环境信息。
2. 开始前阅读 [`AGENTS.md`](AGENTS.md) 与相关目录指南；较大改动先通过 Issue 讨论范围。
3. 为行为变更补充相应测试，按[测试与质量指南](docs/development/testing.md)运行对应检查。
4. 在 Pull Request 中说明改动原因、验证方式与已知限制。

提交前检查 `git diff --check`，并运行与触碰面对应的 `./scripts/dev/test.sh` gate。不得提交 `.env`、凭据、私钥或生成的运行时材料。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
