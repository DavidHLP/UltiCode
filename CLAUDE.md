# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
docker exec ulticode-mysql mysql --default-character-set=utf8mb4 \
  -u ulticode -p'CHANGE_ME_strong_password' ulticode

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
./mvnw verify -Pci

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
mvn flyway:migrate

# 迁移文件结构
init-db/
├── migrations/           # Flyway SQL 迁移脚本 (V*.sql)
│   ├── V20260322__Create_Email_Tables.sql
│   ├── V20260530130501__Baseline.sql
│   └── V20260530140000__Insert_Admin_User.sql
├── sql/                  # 原始 SQL dump 文件
│   └── 20260530_ulticode_dump.sql
└── flyway.conf           # Flyway CLI 配置
```

DB config from `.env`: `DB_HOST`, `DB_PORT` (23306), `DB_USER`, `DB_PASSWORD`, `DB_NAME`.

### Docker (development)

```bash
docker-compose up -d            # Start MySQL 9.1, Redis 7, Nacos 2.3.2
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d  # Production

# 直接操作 MySQL (容器内)
docker exec ulticode-mysql mysql -u ulticode -p'CHANGE_ME_strong_password' -e "USE ulticode; SQL"
docker exec ulticode-mysql mysql -u ulticode -p'CHANGE_ME_strong_password' -e "USE ulticode; SHOW TABLES;"
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.2.5, Java 17, MyBatis-Plus 3.5.16, MapStruct 1.6.3 |
| Auth | JWT (jjwt 0.13.0), Redis session (Redisson 4.3.1) |
| API Docs | SpringDoc OpenAPI 2.6.0 |
| Database | MySQL 9.1 (port 23306), Redis 7 (port 26379) |
| Frontend | Vue 3.5, TypeScript ~6, Vite 8, Pinia 3, Vue Router 5, Tailwind CSS v4 |
| UI Components | shadcn-vue (reka-ui), Radix Vue, Lucide icons |
| i18n | vue-i18n 11 |
| HTTP | Axios |
| PWA | vite-plugin-pwa + workbox |
| Testing (BE) | JUnit 5, Testcontainers (MySQL, Redis), JaCoCo |
| Testing (FE) | Vitest 4, jsdom, Playwright (management) |
| Linting | ESLint 9/10 (flat config), Prettier (semi: false, singleQuote, printWidth: 100) |

## Key Conventions

- **Commit format**: `<type>: <description>` (types: feat, fix, refactor, docs, test, chore, perf, ci)
- **Attribution**: Disabled globally via settings.json
- **Frontend Prettier**: No semicolons, single quotes, 100 char print width
- **ESLint**: Flat config, `vue/multi-word-component-names` off in console, whitelisted in management
- **Integration tests**: Suffix `*IT.java`, excluded from `./mvnw test`, run with `./mvnw verify -Pci`
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