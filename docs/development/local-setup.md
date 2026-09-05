# 本地开发

## 前置条件

- Docker + Compose v2：MySQL、Redis、Nacos 和可选 MeiliSearch。
- mise 管理的 Zulu Java 17、Node.js `^20.19.0 || >=22.12.0`、pnpm 10+、PM2、Docker Compose v2，以及 `curl`、`timeout`、`openssl`。
- 后端使用仓库内的 `services/mvnw`；不要用裸 Maven/Java 绕过启动入口。
- 从仓库根目录执行脚本。`.env` 由 `scripts/dev/init-env.sh` 生成，不能提交。


## Dev Container / Codespaces

仓库提供 `.devcontainer/devcontainer.json`，固定 Java 17、Node 22、pnpm、
mise、PM2、Maven wrapper 和 Docker-in-Docker。创建容器时只安装依赖和
Maven 离线缓存，不启动全栈；容器启动后的默认检查仅执行
`./scripts/dev/test.sh unit`。运行真实 journey 必须显式选择 scope。

统一入口是根目录的薄脚本：

```bash
./ulticode dev
./ulticode host --config ulticode.yml
./ulticode doctor --json
```

`host` 配置只接受 `scope` 和 `observability` 两个无密钥键；启动和
生命周期逻辑仍由 `scripts/dev/*` 与 `devstack-manifest.sh` 所有。

## 推荐启动

```bash
./scripts/dev/init-env.sh
./scripts/dev/up.sh --mode dev-lite
```

`dev-lite` 是兼容默认：按 `auth → admin → app → notification → submission` 应用 Owner migrations，启动六个后端（含 Judge）并使用数据库 Search；不启动 Search worker，默认也不启动两个前端。`dev-lite` 与 `dev-full` 都要求 `APP_SUBMISSION_ROUTING_MODE=remote` 和 `SUBMISSION_CUTOVER_COMPLETE=true`；marker 未完成时 `up.sh` 会 fail closed。

按开发场景选择最小服务集合（`up.sh`/`stop.sh`/`doctor.sh` 共用同一 resolver，见 `scripts/dev/devstack-manifest.sh`）：

```bash
./scripts/dev/up.sh --scope app-journey      # 普通用户旅程：auth/app/notification/submission/judge + console
./scripts/dev/up.sh --scope admin            # 管理：auth/admin/app/notification/submission + management
./scripts/dev/up.sh --scope submission-judge # judge 路径：app/submission/judge，无 Search
./scripts/dev/up.sh --scope search           # indexed Search：auth/app/search + console + meili
./scripts/dev/up.sh --scope core             # Core parent 9108 + independent Judge; Auth/Admin contexts only when explicitly enabled
./scripts/dev/up.sh --scope full-stack       # 显式全量进程集
./scripts/dev/up.sh --scope full-stack --observability  # 显式选择 observability overlay

兼容命令（等价于对应 scope）保留可用：

```bash
./scripts/dev/up.sh --mode dev-lite
./scripts/dev/up.sh --mode dev-full
```

Search/Meili、Judge、observability 与前端只在被选中 scope 需要时才启动；`dev-lite` 默认不创建 Meili 容器。生命周期操作消费同一集合：`./scripts/dev/up.sh status|logs|health --scope <name>`，`./scripts/dev/stop.sh --scope <name>`（`--all` 停止全部）。`up.sh` 对已退役的 `legacy-rollback` 和未知 mode/scope fail closed。生产回滚使用部署方保留并校验的上一份完整 release descriptor，不能通过当前二进制恢复旧实现（见[部署、发布与回滚](../operations/deployment.md)）。`up.sh` 消费 `scripts/dev/devstack-manifest.sh` 的 route、flag、worker、readiness 和 failure policy，不要直接用 Maven 或 PM2 启动 runtime。

`core` scope 会启动 `ulticode-core`（9108）和独立 `ulticode-judge`；
通用配置与 PM2 默认不启动 Owner contexts，named `core` scope 才显式启用
Auth/Admin，并将 Judge readiness 设为 optional。Core parent 没有业务
HTTP/WS 聚合路由，readiness 不是业务可用性证明。启用 Owner 需要
disposable MySQL/Redis、Owner artifacts 和完整凭据；缺少这些输入时
必须 fail closed，不能把 parent smoke 当成 enabled-owner wiring。Core
专用门禁：

```bash
./scripts/dev/test.sh core
./ulticode doctor --scope core --json
```

其中 `test.sh core` 只运行 contexts disabled 的 parent/config/readiness
smoke；Core enabled-owner wiring 和四步业务 journey 当前未验证。分布式
普通用户首旅程使用 `app-journey` scope。

常用变体：

```bash
./scripts/dev/up.sh --skip-install
./scripts/dev/up.sh --quick --mode dev-lite
./scripts/dev/up.sh --only auth,app
./scripts/dev/up.sh --only search
pm2 status
pm2 logs ulticode-auth --nostream --lines 200
```

验证入口分 `static` / `unit` / `quick` / `full-local` / `full` / `integration` 六层，见[测试与质量](testing.md)；快速只读结构检查使用 `./scripts/dev/test.sh static`，完整本地门禁使用 `./scripts/dev/test.sh full-local`。

## 访问入口

| 表面 | 地址/端口 |
| --- | --- |
| Core parent | `9108`，`/api/v1/core/health/ready`，不直接持有业务表 |
| Console | <http://localhost:9002> |
| Management | <http://localhost:9003> |
| Auth/Admin/App/Notification | `9101/9102/9103/9105` |
| Submission owner | internal HTTP `9106` / Dubbo `20886` |
| Judge worker | Dubbo `20884`，无业务 HTTP |
| Search worker | `--mode dev-full` 或 `--mode dev-full --only search`，无业务 HTTP |
| Nacos | <http://localhost:28848/nacos> |

Base Compose 不发布基础设施或 backend 端口；开发覆盖层只绑定 loopback。生产启动、Judge remote daemon 和 TLS 证书见 [部署与回滚](../operations/deployment.md)。

## 前端与共享包

```bash
pnpm --dir apps/console install
pnpm --dir apps/management install

pnpm --dir apps/console dev
pnpm --dir apps/management dev
pnpm --dir packages/auth-core type-check
pnpm --dir packages/auth-core test:coverage
```

## Optional external Adapters

- `FileStoragePort` 默认使用 `LocalStorage`；S3-compatible/R2 通过
  `APP_STORAGE_TYPE=s3`、endpoint、bucket 和 secret-store credentials
  开启。远程 HTTP endpoint 总是拒绝；HTTP 仅允许 loopback 本地开发，
  `APP_STORAGE_S3_TLS_ENABLED=true` 时任何 HTTP endpoint 都拒绝。
- Notification 保留 `LoggingSmtpSenderAdapter` 默认路径；真实 SMTP 只通过
  `SMTP_*`/`APP_EMAIL_ENABLED` 配置，不让业务 Module 依赖厂商 SDK。
- 本地 observability 使用 `docker-compose.observability.yml`。托管 OTLP
  仅通过部署环境提供 HTTPS endpoint/header，并显式叠加
  `docker-compose.observability-managed.yml`；仓库不创建外部账户或凭据。

修改共享包或认证代码时，按 [测试与质量](testing.md) 在 Console、Management 和对应 package 分别验证。

## 数据库入口

```bash
./scripts/dev/migrate.sh info
./scripts/dev/migrate.sh validate
./scripts/dev/migrate.sh migrate
bash scripts/runbooks/owner-schema-contraction.sh preflight
```

完整迁移顺序、回滚和破坏性操作确认见 [`../../init-db/README.md`](../../init-db/README.md) 与 [数据库迁移](../operations/database-migrations.md)。
