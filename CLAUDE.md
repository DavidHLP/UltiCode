# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Related Documentation

项目文档分层管理，按需查阅：

| 文件 | 作用 | 何时查阅 |
|------|------|----------|
| **[AGENTS.md](./AGENTS.md)** | 仓库级权威指南：项目地图、工具链、启动流程、运维命令 | 进入仓库、跨模块协作、提交前自检 |
| **[.claude/rules/](./.claude/rules/)** | Claude Code 按 paths 触发的子规则 (backend/, frontend/, database/) | 触及对应路径时自动加载 |
| **[.cursor/rules/](./.cursor/rules/)** | Cursor IDE 按 globs 触发的 `.mdc` 规则（codegraph、frontend-rules、springboot-rules） | Cursor 环境中触及 `console/`/`management/`/`backend-spring/` 时 |
| **[.claude/agents/](./.claude/agents/)** | 可调用的领域子代理 | 复杂规划、代码审查、安全审查、TDD、构建修复等场景 |
| **[docs/](./docs/)** | 架构、运维、安全专题文档（`CODEMAPS/`、`ENV.md`、`CONTRIBUTING.md`、安全审查报告） | 架构梳理、运维排障、安全合规 |

子代理简表（详见 `.claude/agents/<name>.md`）：
`planner` · `architect` · `code-reviewer` · `java-reviewer` · `security-reviewer` · `tdd-guide` · `build-error-resolver` · `refactor-cleaner` · `doc-updater` · `e2e-runner` · `rust-reviewer`

**职责分工**：CLAUDE.md 聚焦"如何在本项目跑起来 + 项目特有陷阱"（字符集、Arthas、约定）；AGENTS.md 负责"仓库结构 + 工具链 + 启动流程"。本文件不重复 AGENTS.md 已有的内容。

修改 `shared/` 跨端 DTO/enum 字段时，遵循 `cross-stack-dto-granularity-alignment` skill 的审计流程。

---

## Project Role

**全栈工程师 + 系统管理员**: 你是该项目的核心技术负责人，具备完整的自主问题诊断与解决能力。

## Core Responsibilities

1. **自主诊断**: 接手问题时，主动检索并分析前后端运行日志，精准定位问题根源
2. **全局溯源**: 跨文件查看前后端完整代码链路，不局限于局部
3. **运维能力**:
   - `pm2`: 项目进程的监控、管理、重启
   - `docker-compose`: Docker 容器编排与维护
   - `Flyway`: 数据库版本控制与自动化迁移 (位于 `init-db/`)
4. **架构决策**: 根据最佳实践自主决定技术方案，确保向后兼容与系统稳定性

## Action Protocol

| 操作类型 | 执行前要求 |
|---------|-----------|
| 大规模代码修改 | 输出诊断结论 + 行动计划 |
| 执行 Flyway 迁移 | 确认迁移脚本向后兼容，必要时先 dry-run |
| 重启核心服务 | 确认依赖服务状态正常 |

##闭环管理

发现问题 → 分析日志 → 修改代码/SQL → 容器/进程/数据库部署 → 验证结果

## MySQL 容器化操作 (字符集)

⚠️ **docker exec mysql 必须主动设置正确编码**

`ulticode-mysql` 容器默认 `character_set_client=latin1`,直接 `docker exec mysql -e "INSERT 中文..."` 会导致双重 UTF-8 编码(应用读取后显示为 `æžå¨œ` 这类乱码)。

**正确做法**:在 mysql 命令加 `--default-character-set=utf8mb4`,或在 SQL 开头执行 `SET NAMES utf8mb4;`

```bash
# ✅ 正确
set -a; source .env; set +a
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME"

# ❌ 错误 (中文会被双重编码)
docker exec ulticode-mysql mysql -u ulticode -p'...' -e "INSERT INTO t (name) VALUES ('王明')"

# 验证存储字节是否正确 (标准 UTF-8)
docker exec ulticode-mysql mysql --default-character-set=utf8mb4 -u ulticode -p'...' \
  ulticode -e "SELECT HEX(name) FROM users WHERE username='xxx'"
# 王明 应为 E78E8BE6988E (12 字节)
```

**注意**:后端 JDBC URL 已包含 `useUnicode=true&characterEncoding=UTF-8`,走 Spring Boot/Flyway 的应用连接字符正常,只有手工 `docker exec mysql` 路径有该问题。

**修复已双重编码的数据**:
```sql
SET NAMES utf8mb4;
UPDATE users SET name = '正确的姓名' WHERE id = '...';
```

## 运行时调试 (Arthas)

项目已安装 `arthas-boot.jar` (4.1.9) 于 `tools/` 目录,用于 Java 运行时诊断。

**Arthas MCP 服务**: 项目已配置 Arthas MCP (Model Context Protocol) 端点,Claude Code 可直接调用 Arthas 诊断工具。

| 项目 | 值 |
|------|-----|
| MCP 端点 | `http://localhost:8563/mcp` |
| PM2 进程名 | `ulticode-arthas` |
| 启动脚本 | `scripts/start-arthas.sh` |
| 配置文件 | `~/.arthas/arthas.properties` |

```bash
# 启动 Arthas MCP (通过 PM2, 自动附加到 Spring Boot)
pm2 start ecosystem.config.cjs --only ulticode-arthas

# 手动启动 Arthas 并附加到指定进程
java -jar tools/arthas-boot.jar --attach-only --http-port 8563 <PID>

# 验证 MCP 端点
curl -s -X POST http://localhost:8563/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'

# Arthas 交互式模式 (手动调试)
java -jar tools/arthas-boot.jar <PID>
```

### Arthas MCP 注意事项
- `arthas-boot.jar` 不支持 `--properties-file` 参数; 配置文件放 `~/.arthas/arthas.properties` 自动加载
- `--telnet-port -1` 不被支持 (port out of range); 如需禁用 telnet 不传该参数即可
- `--attach-only` 模式: 启动进程退出但 HTTP/MCP agent 运行在目标 JVM 内, PM2 必须 `autorestart: false`
- Spring Boot 健康检查用 `lsof -ti :9001` 而非 `curl` (根路径可能返回 302/401)
- 脚本中用 `$CLAUDE_PROJECT_DIR` 解析项目根目录, 非 `$SCRIPT_DIR`
- Java 版本不一致 (arthas-boot Java 21 vs 目标 JVM Java 17) 会 WARN 但不影响功能

### MCP 配置 (.mcp.json)
- HTTP 远程服务器用 `type: "http"` (Claude Code 官方写法; `streamableHttp` 是 MCP 规范别名, 也接受)
- 项目级 `.mcp.json` 首次使用需在 `/mcp` 中审批
- Arthas MCP: `{"type":"http","url":"http://localhost:8563/mcp"}`

# 常用命令
dashboard          # 系统运行状态总览
thread -n 5       # 查看最忙的 5 个线程
jad <class>       # 反编译类查看源码
watch <class> <method> <expr>  # 观察方法调用
trace <class> <method>          # 方法内部调用路径
stack <class> <method>          # 查看方法调用堆栈
ognl '<expr>'                   # 执行 OGNL 表达式
sc -d <class>                   # 搜索类详细信息
```

**调试典型问题:**
- 方法耗时过长 → `trace` + `monitor`
- 接口参数/返回值异常 → `watch`
- 类加载问题 → `sc` + `jad`
- 线程死锁 → `thread -b`
- 内存问题 → `dashboard` + `heapdump`

---

## Project Overview

UltiCode is an online programming platform (online judge) with a Spring Boot backend, two Vue 3 frontends, and a Flyway-based database migration tool.

## Architecture

```
UltiCode/
├── backend-spring/       # Spring Boot 3.2.5 (Java 17) — port 9001
├── console/              # Vue 3 user-facing frontend — port 9002
├── management/           # Vue 3 admin dashboard — port 9003
├── shared/               # Shared auth-core (Vue composable)
├── init-db/               # Flyway 迁移管理 (migrations/, sql/, flyway.conf)
└── docker/               # Init scripts (nacos SQL, sandbox)
```

**Backend module structure** (`backend-spring/src/main/java/com/ulticode/modules/`):
achievement, admin, auth, backup, bookmark, contest, edgeoperations, email, follow, forum, i18n, moderation, monitoring, notification, permission, problem, problemlist, queue, refreshtoken, search, solution, submission, subscription, user, vote, websocket

**Backend layering**: Each module follows `controller → service → mapper (MyBatis-Plus) → entity`. DTOs via MapStruct. Security under `security/` package. Common utilities under `common/`. Infrastructure under `infrastructure/`.

**Frontend routing**: console has views for auth, problems, problem-list, problem-set, contest, forum, dashboard, profile, achievements, post-editor. management has views for auth, dashboard, users, problems, submissions, contests, forum, moderation, analytics, billing, settings, system, tags, solutions, comments, notifications, audit, account.

## Commands

### Backend (backend-spring/)

```bash
# Run dev server (via PM2)
pm2 start ecosystem.config.cjs
pm2 restart ulticode-9001
pm2 logs ulticode-9001

# Run directly
./mvnw spring-boot:run -Dmaven.test.skip=true

# Build
./mvnw package -DskipTests

# Run tests (excludes integration tests *IT.java)
./mvnw test

# Run integration tests
./mvnw -Dtest='*IT' test

# Compile only
./mvnw compile
```

### Frontend — Console (console/)

```bash
pnpm install
pnpm dev              # lint + type-check + format + test + vite dev server
pnpm build            # type-check + vite build
pnpm type-check       # vue-tsc --build
pnpm lint             # eslint . --fix --cache
pnpm format           # prettier --write src/
pnpm test             # vitest --run --passWithNoTests
pnpm test:watch       # vitest (watch mode)
pnpm test:coverage    # vitest --coverage
```

### Frontend — Management (management/)

Same commands as console. Also has Playwright for E2E.

### Database Migrations (init-db/)

Flyway 迁移脚本统一管理在 `init-db/migrations/` 目录：

```bash
# 独立运行迁移 (不依赖 backend-spring)
./scripts/dev/migrate.sh migrate

# 迁移文件结构
init-db/
├── migrations/           # Flyway SQL 迁移脚本 (V*.sql)
│   ├── V20260322__Create_Email_Tables.sql
│   ├── V20260530130501__Baseline.sql
│   └── V20260530140000__Insert_Admin_User.sql
└── flyway.conf           # Flyway CLI 配置
```

DB config from `.env`: `DB_HOST`, `DB_PORT` (23306), `DB_USER`, `DB_PASSWORD`, `DB_NAME`.

### Docker (development)

```bash
./scripts/dev/init-env.sh        # First run: generate private .env
./scripts/dev/up.sh              # Start infrastructure, migrate, and run all apps

# 直接操作 MySQL (容器内)
set -a; source .env; set +a
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME" -e "SHOW TABLES;"
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.2.5, Java 17, MyBatis-Plus 3.5.16, MapStruct 1.6.3 |
| Auth | JWT (jjwt 0.13.0), Redis session (Redisson 4.3.1) |
| API Docs | SpringDoc OpenAPI 2.6.0 |
| Database | MySQL 9.1 (port 23306), Redis 7 (port 26379) |
| Service Discovery | Nacos 2.3.2 (port 28848, console at `/nacos`) |
| Frontend | Vue 3.5, TypeScript ~6, Vite 8, Pinia 3, Vue Router 5, Tailwind CSS v4 |
| UI Components | shadcn-vue (reka-ui), Radix Vue, Lucide icons |
| i18n | vue-i18n 11 |
| HTTP | Axios |
| PWA | vite-plugin-pwa + workbox |
| Testing (BE) | JUnit 5, Testcontainers (MySQL, Redis), JaCoCo |
| Testing (FE) | Vitest 4, jsdom, Playwright (management) |
| Linting | ESLint 9/10 (flat config), Prettier (semi: false, singleQuote, printWidth: 100) |

## Repository Conventions

> 以下规则从 [AGENTS.md](./AGENTS.md) 提取的仓库级权威约定；触及对应主题时**优先查阅 AGENTS.md 原文**。

### Toolchain 硬约束

- **Java 17** for backend
- **Node.js `^20.19.0 || >=22.12.0`** — 版本不匹配会导致 Vite/pnpm 工具链异常
- **pnpm 10** for frontend and shared packages
- 每个包用**自己的 lockfile**；**禁止用根目录 install 替代** `console/`、`management/`、`shared/auth-core/` 各自的 install
- MySQL 9.1 / Redis 7 / Nacos 2.3.2 通过 Docker Compose 提供
- PM2 管理三个开发进程

### 共享代码（必须双端验证）

`shared/auth-core/` 包含 cookie、CSRF、auth-state、permission 逻辑，console 和 management 都依赖。Console 排除 symlink 的 shared auth 测试；**`shared/auth-core/` 改动必须在该包内跑 `pnpm test` + `pnpm type-check`**，并在两个前端验证。

### Verification Matrix（按触碰面跑对应检查）

提交前按修改面跑对应检查；跨端/安全敏感变更跑完整矩阵。

```bash
# Backend
cd backend-spring
./mvnw compile -B
./mvnw test -B                  # 排除 *IT.java
./mvnw -Dtest='*IT' test -B     # 集成测试须显式指定
./mvnw verify -B                # 含 JaCoCo 校验；项目无 Maven ci profile

# Console
cd console && pnpm lint && pnpm type-check && pnpm test && pnpm build && pnpm audit --prod --audit-level high

# Management
cd management && pnpm lint && pnpm type-check && pnpm test \
  && pnpm validate:i18n-keys && pnpm build && pnpm audit --prod --audit-level high

# Shared auth
cd shared/auth-core && pnpm test && pnpm type-check

# Migration / 配置校验
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml config >/dev/null
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml config >/dev/null
git diff --check
```

> CI 同时验证：迁移在新 MySQL 上跑通、当前树 Gitleaks 扫描、前端 prod 依赖审计、全部 Docker 镜像构建。

### Database Rules（Flyway）

- `init-db/migrations/` 是**唯一**迁移源
- 命名格式：`V{timestamp}__Description.sql`（如 `V20260606130000__xxx.sql`）
- **绝不再编辑可能已被应用的迁移**——必须新增一个时间戳更大的迁移
- `V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql` 是安全修复迁移，**必须保留**在历史 demo seed 之后
- 迁移里**禁止**写可用默认用户或公开的密码
- 初始管理员只通过 opt-in `AdminBootstrapRunner` 创建（正常启动保持禁用）
- schema 工作用 `ulticode-db-migration` skill

### Security Invariants

变更认证/部署密钥/seed 账号/网络暴露前**先读** `docs/SECURITY_REVIEW_2026-06-06.md` + `docs/SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md`，用 `security-review` skill。

- 凭据永不硬编码/提交；运行时密钥来自 `.env` / CI secrets / 部署密钥库
- **JWT secret ≥ 32 字符**
- Access + refresh token 都在 HttpOnly cookie
- Refresh token 走**数据库 hash-only** 的 issue/rotate/revoke 路径；**不可恢复**明文存储；refresh 接口**不接收** access token
- OAuth state 绑定 HttpOnly 浏览器 cookie，Redis 原子消费
- WebSocket 鉴权**只接受** `access_token` cookie；**禁止** query token / URL token / 客户端 STOMP token
- `/admin/**` 与特权方法需 `ADMIN` 或 `SUPER_ADMIN`；审计身份取自认证 principal，**不取自请求体**
- Base / production compose **不发布** MySQL、Redis、Nacos、backend 端口；只有 `docker-compose.dev.yml` 可暴露基础设施，且**只 bind `127.0.0.1`**
- Nacos 鉴权保持启用；默认 `nacos/nacos` 账号保持禁用
- Markdown / KaTeX 输出在 `v-html` 前**必须**先 sanitize
- **主题 bootstrap 脚本**: `console/public/theme-bootstrap.js` 与 `management/public/theme-bootstrap.js` 是为消除 FOUC 而引入的外置脚本，逻辑与 `shared/theme/src/applyThemeToDOM.ts` 一致。当未来引入严格 CSP（无 `'unsafe-inline'`）时，需为 `<script src="/theme-bootstrap.js">` 加 nonce 或 hash，再相应更新 `index.html`。**禁止** 在其他位置（`main.ts` 内联、组件 `onMounted` 等）重新写一份 theme 初始化逻辑 —— 重复实现会与 `shared/theme` 单例产生 hydration 不一致。

### Frontend Conventions

- Vue 3 Composition API + TypeScript
- Prettier：无分号、单引号、100 字符
- 复用现有 API 封装和 shared auth API
- 保留后端 `Result` 响应处理 + snake_case/camelCase 映射模式
- 变更 Markdown / HTML sanitize / URL 处理 / UGC 渲染时**必须**加恶意输入回归测试
- `pnpm dev` 跑 lint+type-check+format+test 再启 Vite；已评审过的树或 PM2 启动场景**直接用 Vite 配置**，避免触发无关格式化
- API 集成用 `ulticode-api-patterns`；改 shared request/response 类型用 `cross-stack-dto-granularity-alignment`

### Backend Conventions

- 控制器 / 系统边界做输入校验
- 优先 typed DTO + MyBatis 参数绑定 + 现有 mapper/service 模式
- 特权操作**即使有全局路由规则也要加** `@PreAuthorize`
- 单次/竞态敏感状态用**事务性条件更新**
- `Map.of(...)` 任何 value 可能为 null 时**禁用**——遵循 `java-map-of-null-safety` 指引
- Lombok service 加构造依赖时，**所有** Mockito `@InjectMocks` 测试必须补齐 mock
- 后端 DTO enum 字段仍用原始 `String`（与前端 TS enum 错配已知；优先推进后端 enum 化）

### Dev 账号与启动

- 一次性 dev 数据库登录 `admin` / `admin123`，由 dev-profile-only bootstrap runner 初始化；**生产环境禁用**
- 启动基础设施用 dev override（host 端口只 bind loopback）：
  ```bash
  ./scripts/dev/init-env.sh   # 首次：生成随机凭据写入 .env
  ./scripts/dev/up.sh         # 启基础设施、配置 Nacos、迁移、安装依赖、启动应用
  ./scripts/dev/up.sh --skip-install   # 依赖未变时复用
  docker compose --env-file .env \
    -f docker-compose.yml -f docker-compose.dev.yml ps
  ```
- **本项目当前未暴露 Spring Actuator**：不要用 `/actuator/health` 判就绪；改用已知公开 API + 两个前端根路径 + PM2 状态 + 容器健康检查

### Project Skills（自动发现）

`.agents/skills/` 内项目级 skills 触发即用：

- `ulticode-dev-ops` · `ulticode-db-migration` · `ulticode-api-patterns`
- `cross-stack-dto-granularity-alignment` · `solarized-terminal-design-style`
- `arthas-cpu-high` · `arthas-eagleeye-traceid` · `arthas-springcontext-issues-resolve`

`.codex/config.toml` 是项目 MCP baseline；**保留**用户 MCP 配置、凭据、自定义 server。`.codex/agents/` 内多代理角色——**未经显式批准不得调度远程代理或对外写入**。

### Git / 外部操作护栏

- 工作树可能含用户改动；**不丢弃、不改写**无关工作
- 提交前必看 `git diff` + `git diff --check`
- Conventional commits：`<type>: <description>`
- 网络工具**默认只读**
- **需用户显式批准**方可：push、merge、publish、改第三方资源、轮换远程凭据、改写 git 历史

## Key Conventions

- **Commit format**: `<type>: <description>` (types: feat, fix, refactor, docs, test, chore, perf, ci)
- **Attribution**: Disabled globally via settings.json
- **Frontend Prettier**: No semicolons, single quotes, 100 char print width
- **ESLint**: Flat config, `vue/multi-word-component-names` off in console, whitelisted in management
- **Integration tests**: Suffix `*IT.java`, excluded from normal Surefire runs; use `./scripts/dev/test.sh integration` (test.sh 同样支持 `quick` / `full` 模式，`quick` 跳过集成测试)
- **Migration naming**: `V{N}__{description}.sql` in `init-db/migrations/`
- **Docker containers**: Non-root `appuser:appgroup`, multi-stage builds
- **Backend ports**: App 9001
- **Frontend ports**: Console 9002, Management 9003
- **Management DataTable i18n**: `DataTable.vue` uses `t(\`table.columnNames.${column.id}\`)` for column headers, where `column.id` matches API field names (camelCase). Ensure `management/src/i18n/locales/*/modules/table.ts` defines both camelCase and snake_case keys under `columnNames`.
- **Backend DTO enums**: Backend DTO fields use raw `String` for enum values (e.g., `PerformModerationActionDTO.action`). Frontend types use proper TS enums. When aligning types, note this mismatch and prefer backend enum adoption.
- **Frontend API patterns**: Management uses typed API functions (`moderation.ts` with `moderationQueueApi`, `reportsApi`, `appealsApi`). Console uses direct `apiPost/apiGet` calls without typed wrappers. When adding new APIs, define typed functions for management; console may use direct calls.
- **Frontend ghost types**: Management API files (e.g., `moderation.ts`) may define types with no backend endpoint (e.g., `UserWarning`, `UserBan`, `CreateUserBanDto`). These are pre-defined for future use — treat as dead code until an endpoint exists.
- **Cross-stack DTO alignment**: When adding or modifying a shared DTO, API endpoint, or enum value, audit both frontends (`console/` + `management/`) and the backend for field, type, and enum alignment before merging. Do not delete "ghost" frontend types (types defined with no backend endpoint yet) or leave "orphan" backend endpoints (no frontend caller) without confirming with the team — see the `cross-stack-dto-granularity-alignment` skill for the audit procedure.
- **Analysis docs**: Cross-module analysis reports go in `docs/` at project root (e.g., `docs/moderation-api-granularity-analysis.md`).

## CI

GitHub Actions on push/PR to main. Path-based change detection triggers only relevant jobs:
- Backend: Maven build + test (ci profile, excludes *IT) + Flyway migration validation
- Frontend: lint + type-check + test
- Docker: Build verification on Dockerfile changes
- Testcontainers: MySQL 9.1 + Redis 7 for integration tests

## PM2 Services

| Port | Name | Type |
|------|------|------|
| 9001 | ulticode-9001 | Spring Boot Backend |
| 9002 | ulticode-9002 | Console Frontend (Vite) |
| 9003 | ulticode-9003 | Management Frontend (Vite) |
| 8563 | ulticode-arthas | Arthas MCP Server (HTTP/MCP, agent runs in target JVM) |
| 28848 | (nacos container) | Nacos 控制台 `/nacos` (默认账号 nacos/nacos) |

**Terminal Commands:**
```bash
pm2 start ecosystem.config.cjs   # First time
pm2 start all                    # After first time
pm2 stop all / pm2 restart all
pm2 start {name} / pm2 stop {name}
pm2 logs / pm2 status / pm2 monit
pm2 save                         # Save process list
pm2 resurrect                    # Restore saved list
```

<!-- rtk-instructions v2 -->
# RTK (Rust Token Killer) - Token-Optimized Commands

## Golden Rule

**Always prefix commands with `rtk`**. If RTK has a dedicated filter, it uses it. If not, it passes through unchanged. This means RTK is always safe to use.

**Important**: Even in command chains with `&&`, use `rtk`:
```bash
# ❌ Wrong
git add . && git commit -m "msg" && git push

# ✅ Correct
rtk git add . && rtk git commit -m "msg" && rtk git push
```

## RTK Commands by Workflow

### Build & Compile (80-90% savings)
```bash
rtk cargo build         # Cargo build output
rtk cargo check         # Cargo check output
rtk cargo clippy        # Clippy warnings grouped by file (80%)
rtk tsc                 # TypeScript errors grouped by file/code (83%)
rtk lint                # ESLint/Biome violations grouped (84%)
rtk prettier --check    # Files needing format only (70%)
rtk next build          # Next.js build with route metrics (87%)
```

### Test (60-99% savings)
```bash
rtk cargo test          # Cargo test failures only (90%)
rtk go test             # Go test failures only (90%)
rtk jest                # Jest failures only (99.5%)
rtk vitest              # Vitest failures only (99.5%)
rtk playwright test     # Playwright failures only (94%)
rtk pytest              # Python test failures only (90%)
rtk rake test           # Ruby test failures only (90%)
rtk rspec               # RSpec test failures only (60%)
rtk test <cmd>          # Generic test wrapper - failures only
```

### Git (59-80% savings)
```bash
rtk git status          # Compact status
rtk git log             # Compact log (works with all git flags)
rtk git diff            # Compact diff (80%)
rtk git show            # Compact show (80%)
rtk git add             # Ultra-compact confirmations (59%)
rtk git commit          # Ultra-compact confirmations (59%)
rtk git push            # Ultra-compact confirmations
rtk git pull            # Ultra-compact confirmations
rtk git branch          # Compact branch list
rtk git fetch           # Compact fetch
rtk git stash           # Compact stash
rtk git worktree        # Compact worktree
```

Note: Git passthrough works for ALL subcommands, even those not explicitly listed.

### GitHub (26-87% savings)
```bash
rtk gh pr view <num>    # Compact PR view (87%)
rtk gh pr checks        # Compact PR checks (79%)
rtk gh run list         # Compact workflow runs (82%)
rtk gh issue list       # Compact issue list (80%)
rtk gh api              # Compact API responses (26%)
```

### JavaScript/TypeScript Tooling (70-90% savings)
```bash
rtk pnpm list           # Compact dependency tree (70%)
rtk pnpm outdated       # Compact outdated packages (80%)
rtk pnpm install        # Compact install output (90%)
rtk npm run <script>    # Compact npm script output
rtk npx <cmd>           # Compact npx command output
rtk prisma              # Prisma without ASCII art (88%)
```

### Files & Search (60-75% savings)
```bash
rtk ls <path>           # Tree format, compact (65%)
rtk read <file>         # Code reading with filtering (60%)
rtk grep <pattern>      # Search grouped by file (75%). Format flags (-c, -l, -L, -o, -Z) run raw.
rtk find <pattern>      # Find grouped by directory (70%)
```

### Analysis & Debug (70-90% savings)
```bash
rtk err <cmd>           # Filter errors only from any command
rtk log <file>          # Deduplicated logs with counts
rtk json <file>         # JSON structure without values
rtk deps                # Dependency overview
rtk env                 # Environment variables compact
rtk summary <cmd>       # Smart summary of command output
rtk diff                # Ultra-compact diffs
```

### Infrastructure (85% savings)
```bash
rtk docker ps           # Compact container list
rtk docker images       # Compact image list
rtk docker logs <c>     # Deduplicated logs
rtk kubectl get         # Compact resource list
rtk kubectl logs        # Deduplicated pod logs
```

### Network (65-70% savings)
```bash
rtk curl <url>          # Compact HTTP responses (70%)
rtk wget <url>          # Compact download output (65%)
```

### Meta Commands
```bash
rtk gain                # View token savings statistics
rtk gain --history      # View command history with savings
rtk discover            # Analyze Claude Code sessions for missed RTK usage
rtk proxy <cmd>         # Run command without filtering (for debugging)
rtk init                # Add RTK instructions to CLAUDE.md
rtk init --global       # Add RTK to ~/.claude/CLAUDE.md
```

## Token Savings Overview

| Category | Commands | Typical Savings |
|----------|----------|-----------------|
| Tests | vitest, playwright, cargo test | 90-99% |
| Build | next, tsc, lint, prettier | 70-87% |
| Git | status, log, diff, add, commit | 59-80% |
| GitHub | gh pr, gh run, gh issue | 26-87% |
| Package Managers | pnpm, npm, npx | 70-90% |
| Files | ls, read, grep, find | 60-75% |
| Infrastructure | docker, kubectl | 85% |
| Network | curl, wget | 65-70% |

Overall average: **60-90% token reduction** on common development operations.
<!-- /rtk-instructions -->
