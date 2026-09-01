# 本地开发

## 前置条件

- Docker + Compose v2：MySQL、Redis、Nacos 和可选 MeiliSearch。
- Java 17（推荐 Zulu 17，经 mise 管理）；Node.js `^20.19.0 || >=22.12.0`；pnpm 10+；PM2。
- 从仓库根目录执行脚本。`.env` 由 `scripts/dev/init-env.sh` 生成，不能提交。

## 推荐启动

```bash
./scripts/dev/init-env.sh
./scripts/dev/up.sh --mode dev-lite
```

`dev-lite` 按 `auth → admin → app → notification → submission` 应用 Owner migrations，启动最小运行集并使用数据库 Search fallback；不启动 Search worker。需要 MeiliSearch indexed read 时使用：

```bash
./scripts/dev/up.sh --mode dev-full
```

正常两种模式使用 Judge Streams。只有需要验证旧路径时才使用 `./scripts/dev/up.sh --mode legacy-rollback`；该模式不启动 Judge worker。`up.sh` 消费 `scripts/dev/devstack-manifest.sh` 的 route、flag、worker、readiness 和 failure policy，不要直接用 Maven 或 PM2 启动 runtime。

常用变体：

```bash
./scripts/dev/up.sh --skip-install
./scripts/dev/up.sh --quick --mode dev-lite
./scripts/dev/up.sh --only auth,app
./scripts/dev/up.sh --only search
pm2 status
pm2 logs ulticode-auth --nostream --lines 200
```

## 访问入口

| 表面 | 地址/端口 |
| --- | --- |
| Console | <http://localhost:9002> |
| Management | <http://localhost:9003> |
| Auth/Admin/App/Notification | `9101/9102/9103/9105` |
| Submission owner | internal HTTP `9106` / Dubbo `20886` |
| Judge worker | Dubbo `20884`，无业务 HTTP |
| Search worker | dev-full 或 `--only search`，无业务 HTTP |
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

修改共享包或认证代码时，按 [测试与质量](testing.md) 在 Console、Management 和对应 package 分别验证。

## 数据库入口

```bash
./scripts/dev/migrate.sh info
./scripts/dev/migrate.sh validate
./scripts/dev/migrate.sh migrate
./scripts/runbooks/owner-schema-contraction.sh preflight
```

完整迁移顺序、回滚和破坏性操作确认见 [`../../init-db/README.md`](../../init-db/README.md) 与 [数据库迁移](../operations/database-migrations.md)。
