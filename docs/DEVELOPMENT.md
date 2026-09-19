# 开发、测试与规则

本页是开发相关当前知识的唯一主题入口，合并本地启动、验证、配置、规则与排障说明。脚本、构建文件、服务配置和前端包源码仍是精确行为来源。

## 本地开发

### 前置条件

- Docker + Compose v2：MySQL、Redis、Nacos 和可选 MeiliSearch。
- mise 管理的 Zulu Java 17、Node.js `^20.19.0 || >=22.12.0`、pnpm 10+、PM2、Docker Compose v2，以及 `curl`、`timeout`、`openssl`。
- 后端使用仓库内的 `services/mvnw`；不要用裸 Maven/Java 绕过启动入口。
- 从仓库根目录执行脚本。`.env` 由 `scripts/dev/init-env.sh` 生成，不能提交。


### Dev Container / Codespaces

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

### 推荐启动

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
```

兼容命令（等价于对应 scope）保留可用：

```bash
./scripts/dev/up.sh --mode dev-lite
./scripts/dev/up.sh --mode dev-full
```

Search/Meili、Judge、observability 与前端只在被选中 scope 需要时才启动；`dev-lite` 默认不创建 Meili 容器。生命周期操作消费同一集合：`./scripts/dev/up.sh status|logs|health --scope <name>`，`./scripts/dev/stop.sh --scope <name>`（`--all` 停止全部）。`up.sh` 对已退役的 `legacy-rollback` 和未知 mode/scope fail closed。生产回滚使用部署方保留并校验的上一份完整 release descriptor，不能通过当前二进制恢复旧实现（见[部署、发布与回滚](OPERATIONS.md#部署发布与回滚)）。`up.sh` 消费 `scripts/dev/devstack-manifest.sh` 的 route、flag、worker、readiness 和 failure policy，不要直接用 Maven 或 PM2 启动 runtime。

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
smoke。Core enabled-owner wiring 另有显式 opt-in 的 disposable 门禁：
`CORE_ENABLED_OWNER_JOURNEY=1 ./scripts/test/core-enabled-owner-journey.sh`
在 disposable Testcontainers MySQL/Redis 上应用 canonical Auth/Admin
migrations，启动真实 Auth/Admin child，验证 readiness、local identity read、
合法 permission grant、missing signer fail-closed 与 cleanup；它是 bounded
wiring proof，不是四步业务 journey、生产 parity 或全量 Admin bean graph
健康证明。分布式普通用户首旅程使用 `app-journey` scope。

常用变体：

```bash
./scripts/dev/up.sh --skip-install
./scripts/dev/up.sh --quick --mode dev-lite
./scripts/dev/up.sh --only auth,app
./scripts/dev/up.sh --only search
pm2 status
pm2 logs ulticode-auth --nostream --lines 200
```

验证入口分 `static` / `unit` / `quick` / `full-local` / `full` / `integration` 六层，见本页“测试与质量”；快速只读结构检查使用 `./scripts/dev/test.sh static`，完整本地门禁使用 `./scripts/dev/test.sh full-local`。

### 访问入口

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

Base Compose 不发布基础设施或 backend 端口；开发覆盖层只绑定 loopback。生产启动、Judge remote daemon 和 TLS 证书见 [`OPERATIONS.md`](OPERATIONS.md)。

### 前端与共享包

```bash
pnpm --dir apps/console install
pnpm --dir apps/management install

pnpm --dir apps/console dev
pnpm --dir apps/management dev
pnpm --dir packages/auth-core type-check
pnpm --dir packages/auth-core test:coverage
```

### Optional external Adapters

- `FileStoragePort` 默认使用 `LocalStorage`；S3-compatible/R2 通过
  `APP_STORAGE_TYPE=s3`、endpoint、bucket 和 secret-store credentials
  开启。远程 HTTP endpoint 总是拒绝；HTTP 仅允许 loopback 本地开发，
  `APP_STORAGE_S3_TLS_ENABLED=true` 时任何 HTTP endpoint 都拒绝。
- Notification 保留 `LoggingSmtpSenderAdapter` 默认路径；真实 SMTP 只通过
  `SMTP_*`/`APP_EMAIL_ENABLED` 配置，不让业务 Module 依赖厂商 SDK。
- 本地 observability 使用 `docker/docker-compose.observability.yml`。托管 OTLP
  仅通过部署环境提供 HTTPS endpoint/header，并显式叠加
  `docker/docker-compose.observability-managed.yml`；仓库不创建外部账户或凭据。

修改共享包或认证代码时，按本页“测试与质量”在 Console、Management 和对应 package 分别验证。

### 数据库入口

```bash
./scripts/dev/migrate.sh info
./scripts/dev/migrate.sh validate
./scripts/dev/migrate.sh migrate
bash scripts/runbooks/owner-schema-contraction.sh preflight
```

完整迁移顺序、回滚和破坏性操作确认见 [`../init-db/README.md`](../init-db/README.md) 与 [`OPERATIONS.md`](OPERATIONS.md#数据库迁移与-owner-收敛)。
## 测试与质量

### 统一入口

CI 的静态任务运行 `bash scripts/test/zero-infra-validation-contract.sh --static-only`，无需 Java、mise 或前端依赖。省略 `--static-only` 会额外执行 unit deny 自证，需要预装 mise Java 17、pnpm 和前端依赖。

```bash
./scripts/dev/test.sh static       # 只读、零基础设施的结构门禁
./scripts/dev/test.sh unit         # static + 前端单测 + -Punit 后端门禁（deny 环境，无 *IT）
./scripts/dev/test.sh quick        # static + unit 兼容别名（已弃用，新代码请用全名）
./scripts/dev/test.sh full-local   # 容器 + 迁移 + Maven verify + 前端测试（原 quick 的重型语义）
./scripts/dev/test.sh full         # full-local + 前端构建、i18n 与依赖审计
./scripts/dev/test.sh integration  # full-local + Testcontainers/Sandbox/owner migration 安全演练
```

`static` 不调用 Docker、数据库、服务、Testcontainers、Maven verify 或 `pnpm install`，可用 deny-shim 自证（见 `scripts/test/zero-infra-validation-contract.sh`）；缺 shellcheck 等可选工具时跳过并提示。`full-local` 保留原 `quick` 的完整覆盖（MySQL/Redis、owner migration、Maven verify/JaCoCo、Console/Management coverage 与类型检查）。`full` 追加前端构建、i18n 与依赖审计；`integration` 追加 Testcontainers、数据库/Redis/Sandbox 相关集成测试。后端 `unit` 使用根 POM 的 `unit` profile（Surefire 排除 `*IT`/`*IntegrationTest` 含嵌套类，不传 `-Dtest` 选择器）；wrapper 剥离基础设施凭据并以 deny 环境实证（deny 运行 5786 测试零失败、零 Testcontainers、零 IT），零基础设施自证见 `scripts/test/zero-infra-validation-contract.sh`（含 unit deny 阶段）。具体阶段由 `scripts/dev/test.sh` 实现，不在本页复制脚本内部逻辑。

Shell 工具的文件断言和基线编排可单独运行 `bash scripts/test/shell-tooling-contract.sh`，也包含在 `static` 中。该测试使用临时文件和 Docker 替身检查失败传播、清理及基线不变性，不连接 Docker daemon 或真实数据库；真实迁移效果仍由基线和集成门禁验证。

owner/module 的 declarative source contract registry 由 scripts/test/owner-architecture-source-contract.rules 持有，并由 scripts/test/owner-architecture-source-contract.sh 执行；scripts/dev/architecture-contract-test.sh 仍是唯一架构门禁入口，parent 负责 static/full eligibility、执行顺序和结果汇总，并以 `--list-qualified static|dynamic` 提供逐行、无执行副作用的 CI 注册表视图。Backend CI 只消费 dynamic 视图，zero-infra 则从同一 static/dynamic 视图核对 static-only banner；child 脚本不在 workflow 中重复列出。

### Core 与 distributed 验证边界

| 层级 | 环境/副作用 | 能证明什么 | 明确不能证明 |
| --- | --- | --- | --- |
| `static` | 零基础设施、静态脚本与配置 | 依赖方向、allowlist、负向契约 | Owner 启动、数据库、消息、业务可用 |
| `unit` | deny 环境、`-Punit`、排除 `*IT` | 单元逻辑、fail-closed、生命周期交接 | 真实 Owner wiring、持久化、网络 |
| `core` | Core Maven parent/config/readiness smoke；Owner contexts disabled | Core parent、显式扫描、数据源工厂、readiness 语义 | enabled-owner bean graph、业务 HTTP/WS、数据库/Redis |
| enabled-owner wiring | disposable Owner artifacts + MySQL/Redis as required | 选定 Owner 的真实 child wiring 与 local Adapter 注入 | 完整业务旅程、生产 SLO/HA |
| distributed journey | disposable `app-journey` scope、seeded account/problem | login → Problem read → Bookmark write → ordinary-user denial | Core parity、Judge sandbox、生产流量 |
| Core journey | 显式 `CORE_ENABLED_OWNER_JOURNEY=1` disposable 门禁（Testcontainers）；默认不可执行 | Auth/Admin enabled wiring、readiness、identity/grant/fail-closed | 业务 journey 或同构结论 |

第一条代表性业务旅程固定为：`POST /auth/login`、`GET /problems/{id}`、
`POST /bookmarks/quick`、普通用户 `POST /problems` 得到 403/typed denial。
Submission→Judge→result 不属于这条首旅程。Core 的 readiness smoke 不得代替
enabled-owner wiring 或业务 journey。

### 按路径选择验证

| 变更范围 | 必需验证 |
| --- | --- |
| Core registry/assembly/lifecycle/local Adapter | `static` + `core-profile-contract.sh` + Core `-Punit`；若改变 enabled wiring，再运行 disposable wiring |
| Owner business module/endpoint | 对应 Owner `-Punit`、contract gate；涉及真实 journey 时运行命名 distributed scope |
| shared contract/platform/security | 相关 contract gate + distributed Owner tests；Core 受影响时追加 Core contract/smoke |
| 无法分类或跨边界变更 | 采用保守路径：distributed backend/contract checks + Core contract/smoke，不能只跑 static |

上述映射是验证路由，不代表每层已在本工作区执行；命令结果必须分别记录
`PASS`、`FAIL`、`NOT_RUN` 或 `BLOCKED_EXTERNAL`。

### 按触碰面验证

| 触碰面 | 最小命令 |
| --- | --- |
| Java 后端 | `(cd services && ./mvnw verify -B)` |
| 后端集成 | `(cd services && ./mvnw -Dtest='*IT' test -B)` |
| Console | `pnpm --dir apps/console lint && pnpm --dir apps/console type-check && pnpm --dir apps/console test:coverage && pnpm --dir apps/console build` |
| Management | `pnpm --dir apps/management lint && pnpm --dir apps/management type-check && pnpm --dir apps/management test:coverage && pnpm --dir apps/management validate:i18n-keys && pnpm --dir apps/management build` |
| `packages/auth-core` | `pnpm --dir packages/auth-core test:coverage && pnpm --dir packages/auth-core type-check` |
| migration/Compose | `docker compose --project-directory . --env-file .env -f docker/docker-compose.yml -f docker/docker-compose.dev.yml config >/dev/null` 与 `git diff --check` |
| 架构/文档 | `bash scripts/dev/architecture-contract-test.sh` 与 `bash scripts/dev/docs-contract-test.sh` |

### 测试约定

- `*IT.java` 是集成测试；普通 Maven test 排除它们，显式 `-Dtest='*IT'` 或 `integration` 才运行。
- 测试行为、边界、错误语义、幂等、并发和安全不变量；不要用空测试或 `@Disabled` 代替验证。
- 后端使用 JUnit 5、Mockito、Testcontainers、JaCoCo；前端使用 Vitest/V8，关键用户路径使用 Playwright。
- 共享包或 contract 变更需验证两个前端（若被双方消费）以及 API shape/兼容性门禁。
- 覆盖率报告是 CI/本地产物，不等于生产流量覆盖；阈值由 POM、package 配置和 `scripts/test/coverage-contract.sh` 维护。

### 安全与迁移门禁

架构门禁同时检查 Owner 单写者、JWT/CSRF、Redis ACL、Nacos identity、Dubbo mTLS、Compose 网络、Judge sandbox、Streams replay、migration preflight、contract compatibility 和文档关键事实。迁移脚本默认 dry-run 或 preflight；任何 backfill、contraction、rollback、credential 或生产动作都必须显式确认并保留可审计输出。

### 验证结果语义

`Repository Implemented`、`Locally Validated`、`Staging Validated`、`Production Applied` 不可混用。本仓库没有生产环境；disposable Compose/DinD 只能证明仓库侧行为。生产部署、真实流量、证书、外部 telemetry、HA failover 与远程 Judge 仍由部署方验证。
## 配置与环境变量

### 来源与边界

`.env` 是本地部署密管输入，由 `./scripts/dev/init-env.sh` 生成并被 Git 忽略；`.env.example` 与 `.env.test.example` 是可提交模板，不包含可用凭据。实现配置以各服务 `application.yml`、Compose 和 `ecosystem.config.cjs` 为准。

### 常用变量

| 变量 | 用途 | 规则 |
| --- | --- | --- |
| `DB_HOST/PORT/USER/PASSWORD/NAME` | 兼容基础 MySQL 连接 | 仅作为明确 owner 配置未提供时的 dev fallback |
| `AUTH_*`、`ADMIN_*`、`APP_*`、`SUBMISSION_*`、`NOTIFICATION_*` | Owner 数据库连接 | 生产必须显式提供 owner host、端口、库、用户和密码 |
| `REDIS_HOST/PORT/USERNAME/PASSWORD` | Redis 连接 | ACL principal 按 Owner 分开，命令/key/channel 受限 |
| `JWT_SECRET` | 仅 local compatibility profile 的 HMAC secret | 至少 32 字符；生产 access token 使用 RS256/JWKS |
| `NACOS_SERVER_ADDR` / `NACOS_SERVERS` | 服务发现 | dev 使用显式 standalone；prod/HA 使用 cluster peer list |
| `NACOS_USERNAME/PASSWORD` 及 `*_NACOS_*` | Nacos workload identity | 每个 workload 使用独立、namespace-scoped 账号；内置账号禁用 |
| `DUBBO_NAMESPACE` | Dubbo 环境隔离 | prod 必须非空，不能静默回退到 dev |
| `CORS_ALLOWED_ORIGINS` / `FRONTEND_URL` | 浏览器来源与链接 | 生产使用部署域名，不能使用 wildcard |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `Secure=false` 只允许全 local profile |
| `JUDGE_DOCKER_HOST` / `JUDGE_DOCKER_CERT_DIR` / `SANDBOX_HOST_DIR` | 生产 Judge remote/rootless Docker TLS | 证书由外部 secret store 提供；不提交到 Git |
| `REDIS_ACL_DIR` | 运行时 ACL 目录 | ignored、原子物化；不把 hash snapshot 放入 Git |
| `TLS_CERT_DIR` | 前端生产 TLS 证书挂载 | secret mount；开发 HTTP 不需要 |

正常 `dev-lite`/`dev-full`、Owner migration 与生产 Compose 都要求 `APP_SUBMISSION_ROUTING_MODE=remote` 和 `SUBMISSION_CUTOVER_COMPLETE=true`；另外还需要独立 migration principal、`DUBBO_MTLS_CERT_DIR` 和外部 OTLP collector 等变量。缺失必需变量时应 fail closed，而不是生成默认凭据。

### 安全规则

- 不在源码、文档、日志、命令输出或提交中打印 password、token、private key、certificate 内容。
- Access/refresh token 使用 HttpOnly cookie；refresh token 只存 hash。
- `JWT_COOKIE_SECURE=false` 只能在明确的 `dev`、`test`、`ci` profile；生产启动拒绝混合绕过。
- 生产 Compose 不发布 MySQL、Redis、Nacos 或 backend 端口；开发仅 loopback。
- 改 `.env` 后通过 `./scripts/dev/up.sh --mode dev-lite --skip-install` 重新加载，不直接绕过 manifest 启动服务。

完整可用字段见 [`.env.example`](../.env.example) 和本页“本地开发”；不要在本文复制模板的全部变量。
## 编码指南与规则入口

项目契约由根 [AGENTS.md](../AGENTS.md) 与最近的嵌套指南维护。本页解释规则的分工与维护方式，不另建一套编码规范。

### 三层分工

| 层 | 职责 | 不承担的职责 |
| --- | --- | --- |
| 根及嵌套 AGENTS.md | 安全不变量、模块边界、授权与验证原则 | 通用语言教材 |
| [.claude/rules](../.claude/rules/README.md) / [.omp/rules](../.omp/rules/README.md) | 修改相关路径时补充少量风险提醒 | 强制全仓探索、逐项报告、重复项目指南 |
| [.codex/rules](../.codex/rules/README.md) | 高风险命令前缀的执行策略 | 路径匹配、代码风格、理解用户授权意图 |

Claude 使用 `paths`；OMP 扩展使用 `globs`，其 `paths` 字段当前不参与匹配。两份路径规则保留各自加载格式，同一主题的正文保持一致；不增加生成器或新的运行依赖。OMP 不再叠加全栈汇总规则或用于检查规则文本的运行时中断。JVM 诊断保留很短的常驻安全提醒，仅在实际附加进程时适用。

### 保留什么

只保留有明确后果的约束：数据或用户改动丢失、凭据泄漏、鉴权绕过、契约不兼容、越过模块或数据库所有权、错误的事务与并发语义。具体项目不变量仍以 AGENTS.md 为准。

调查和验证随改动风险扩大：小改动不要求变更矩阵、全链路图、全量构建或正式多角色审查；跨服务兼容、数据迁移、安全边界改变时，再补相应证据。按本页“测试与质量”选择适用检查，说明未验证的部分。

### 格式交给配置

缩进、引号、分号、换行、导入顺序等以受影响模块的格式器和 lint 配置为准；没有配置时沿用邻近代码。不要从另一应用复制格式配置，不为风格统一批量重写，也不通过 rules 添加任意方法长度、注释数量或命名后缀要求。

需要格式检查时，使用仓库已有工具并限定到改动文件。注意应用的 `lint`、`format` 脚本会修改文件；只读检查可选 `lint:check` 或格式器的检查模式。检查具体 package.json 后再运行，避免把全目录自动修复当作例行步骤。

### 执行策略的边界

Codex 前缀规则只能识别已列出的参数排列，无法覆盖任意脚本、包装器、绝对路径或选项位置。未命中不代表授权或安全；不要通过改写命令绕过审批。普通本地操作不额外设置 `prompt`，也不添加宽泛的 `allow`。

外部发布、数据删除等保留窄范围 `prompt`。它可能在用户已授权后仍要求工具层批准；agent 不再额外重复询问。项目策略是否加载和生效取决于可信配置层、启动加载及当前执行模式，不能把它当作完整沙箱或秘密扫描器。

### 维护与检查

添加规则前先确认现有指南、工具配置和测试是否已覆盖该问题。避免重复正文、固定版本信息和普通语言常识。修改路径时检查匹配与不匹配样例；修改命令策略时运行 `codex execpolicy check`，同时验证危险命令和普通工作命令。检查 diff、链接和空白即可验证纯规则文档修改；不声称因此验证了应用运行行为或模型性能。

参考：[Codex rules](https://learn.chatgpt.com/docs/agent-configuration/rules)、
[omp-path-rules](https://github.com/DavidHLP/omp-path-rules/blob/master/README.md)。
## 排障

### 启动与环境

- **服务启动绕过策略**：不要直接使用 PM2/Maven 启动 runtime；用 `scripts/dev/up.sh --mode dev-lite|dev-full`，否则会绕过 manifest、Owner readiness、migration 和 rollback gate。
- **环境变量改动未生效**：执行 `./scripts/dev/up.sh --mode dev-lite --skip-install`，不要手工重启单个进程。
- **Java 17 cgroup-v2 异常**：优先使用 mise 管理的 Zulu 17；旧的本地 17.0.2 可能在 JVM processor discovery 阶段失败。
- **Redis/MySQL/Testcontainers 不可达**：区分宿主权限/凭据/容器状态与源码失败。记录准确错误；不能把 `BLOCKED_EXTERNAL` 写成测试通过。
- **中文乱码**：容器内 MySQL 客户端显式使用 `--default-character-set=utf8mb4`，否则可能产生双重编码；迁移入口见 [`OPERATIONS.md`](OPERATIONS.md#数据库迁移与-owner-收敛)。

### 认证与路由

- 生产 Cookie policy、JWT/JWKS、CSRF、Nacos/Dubbo identity 失败时应 fail closed。不要通过关闭 CSRF、改用 access token refresh 或放宽 `anyRequest` 修复。
- WebSocket 只读取 `access_token` cookie；不要在 URL、query 或 STOMP 客户端 token 中传递凭据。
- Admin/privileged endpoint 必须同时检查路由和方法权限；审计 actor 取 principal/delegation，不取请求字段。

### 队列、Outbox 与 Worker

- Search/Judge/Notification backlog 先检查 PM2 状态、Redis ACL、PEL、DLQ 和下游健康；不要先删除 stream、PEL、ledger 或 version hash。
- Judge/Submission 使用 generation/attempt fence；重放前确认新 generation 和 owner receipt，避免旧 verdict 覆盖新状态。
- Notification poison event 进入 DB inbox；Search/Judge DLQ 的 replay/discard 见 [`WORKER_SLO_RUNBOOK.md`](../services/docs/WORKER_SLO_RUNBOOK.md)。
- 关闭服务时让 DrainGate 停止新 claim；保留未 ACK 的 PEL/lease 给现有 reaper 恢复。

## 迁移与部署

迁移问题先运行 preflight 和只读 parity/checksum；backfill 默认 dry-run，目标冲突停止并导出 failure artifact。生产 rollback 只能回到 schema-compatible artifact，不执行 downgrade。部署前检查 source commit、migration checksum、immutable digest manifest、Compose config 和健康 descriptor。

更详细的可执行步骤：

- [数据库迁移](OPERATIONS.md#数据库迁移与-owner-收敛)
- [部署与回滚](OPERATIONS.md#部署发布与回滚)
- [Services 问题注册表](../services/docs/SERVICES_ISSUES.md)
- [Worker SLO Runbook](../services/docs/WORKER_SLO_RUNBOOK.md)
