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
| **[wiki/](./wiki/)** | 项目工程文档（LLM Wiki 活知识库，2026-06-21 重建为纯理念版）。顶层：`README.md`（着陆）· `SCHEMA.md`（三层/三动作/写作规范 + §10 daily-notes 工作流）· `index.md`（内容目录）· `log.md`（维护时间线）；子目录：`overview/`（综合页）· `entities/`（实体页）· `concepts/`（概念页）· `templates/`（Obsidian ingest 模板：entity/concept/overview/daily-note）· `daily-notes/`（每日 ingest 日记） | 查架构/部署/合规走 [`wiki/index.md`](./wiki/index.md) 的"先读这份"表；维护规则见 [`wiki/SCHEMA.md`](./wiki/SCHEMA.md)；运维命令见 `AGENTS.md`；**改 wiki 内容后跑 `scripts/dev/wiki-manifest.sh` 刷新 `wiki/.meta/manifest.json`（每页→最近 commit 溯源 + body hash，见 SCHEMA §12）** |
| **[shared/theme/](./shared/theme/)** | 前端主题系统（2026-06-19 落地）。4 层分层（state / tokens / primitives / bootstrap）、Design Token 全集、light/dark/system + compact/comfortable 切换、组件原语清单。**项目字体 = LXGW WenKai 楷体，全站统一（包括 Monaco 编辑器 / ECharts 默认字体）**。Wiki 知识页：[[wiki/concepts/theme-system]] | 改前端颜色 / 字体 / 密度 / 动效 / 组件样式时先读 `shared/theme/src/applyThemeToDOM.ts` + wiki 概念页 |

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

项目已安装 `arthas-boot.jar` (4.2.2) 于 `tools/` 目录,用于 Java 运行时诊断。配置文件 `infrastructure/arthas/arthas.properties` 项目级维护, wrapper attach 时自动同步到 `~/.arthas/lib/<version>/arthas/arthas.properties` (arthas-agent 真正读的位置)。

**Arthas MCP 服务**: 项目已配置 Arthas MCP (Model Context Protocol) 端点,Claude Code 可直接调用 Arthas 诊断工具。

### 启动路径 (三选一, 自动互斥)

Arthas wrapper (`scripts/start-arthas.sh`) 由 **三路** 都能拉起, **任何一路先起来, 其他路都会跳过** (基于端口 `:8563` + PID 文件双重检测):

| 路径 | 触发方式 | 推荐场景 |
|------|---------|---------|
| **PM2** (主) | `pm2 start ecosystem.config.cjs` 启动 `ulticode-arthas` app | 本地开发、SSH 远程、CI、跟随 `pm2 save` 自启 |
| **Claude Code hook** | `.claude/settings.json` 注册的 `SessionStart` / `SessionEnd` | Claude Code 会话内, 无 PM2 时 |
| **CLI** (兜底) | `scripts/arthas-cli.sh start` | 任意环境手动/调试, 跨平台 |

**互斥原则** (PID 文件 + 端口双重检测):
- 任何一路检测到 `:8563` 已在监听 或 PID 文件存在 → 跳过
- PID 文件格式: `PID\nLAUNCHER` (`pm2` / `hook` / `cli`)
- SessionEnd 只停 launcher=hook 的 wrapper;`pm2` / `cli` 拉起的留给它们自己管理
- `arthas-cli.sh stop` 只停 launcher=cli 的;`pm2` 拉起的提示用 `pm2 stop ulticode-arthas`

| 项目 | 值 |
|------|-----|
| MCP 端点 | `http://localhost:8563/mcp` (STATELESS) |
| Wrapper | `scripts/start-arthas.sh` (自愈: 端口死了自动重 attach) |
| SessionStart hook | `scripts/arthas-session-start.sh` |
| SessionEnd hook | `scripts/arthas-session-end.sh` |
| Hook 注册 | `.claude/settings.json` `hooks.SessionStart / SessionEnd` (已提交) |
| PM2 app | `ecosystem.config.cjs` 中 `ulticode-arthas` |
| 通用 CLI | `scripts/arthas-cli.sh {start\|stop\|restart\|status\|logs}` |
| PID 文件 | `.claude/.arthas/wrapper.pid` |
| Wrapper 日志 | `.claude/.arthas/wrapper.log` |
| 项目级配置 | `infrastructure/arthas/arthas.properties` (wrapper 自动 sync) |
| 实际生效位置 | `~/.arthas/lib/<version>/arthas/arthas.properties` |

```bash
# === 推荐: PM2 一键全起 (含 9001/arthas) ===
pm2 start ecosystem.config.cjs
pm2 status                 # 看 ulticode-arthas 状态
pm2 logs ulticode-arthas   # 看 wrapper 日志

# === Claude Code 会话内 (无 PM2) ===
# SessionStart hook 自动拉起 wrapper, 无需手动
# 端口已监听时 hook 直接 noop, 不会重复 attach

# === 任意环境手动控制 (CLI 兜底) ===
scripts/arthas-cli.sh start     # 后台拉起 wrapper
scripts/arthas-cli.sh status    # 端口/PID/MCP/launcher 全景
scripts/arthas-cli.sh stop      # 停 cli 自己拉起的
scripts/arthas-cli.sh logs      # tail wrapper 日志
scripts/arthas-cli.sh restart   # stop + start

# === 手动 attach 到指定进程 (绕过 wrapper 等待 Spring Boot) ===
java -jar tools/arthas-boot.jar --attach-only --http-port 8563 <PID>

# === 验证 MCP 端点 ===
curl -s -X POST http://localhost:8563/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'

# === Arthas 交互式模式 (手动调试) ===
java -jar tools/arthas-boot.jar <PID>
```

### Arthas MCP 注意事项
- **三路互斥**: PM2 / hook / cli 任何一路拉起 wrapper 后, 其他路检测到 PID 文件或端口在用都会跳过;SessionEnd 只清理 hook 自己拉起的, 不会动 PM2/cli 的
- **自愈 loop**: wrapper 持续监控 `:8563`, 端口死了 (例如 `pm2 restart 9001`) 会自动重新 attach
- **PID 文件格式**: `PID\nLAUNCHER` 两行, 供互斥判断;wrapper 启动时立即写, 退出时清理
- **协议选 STATELESS (强制项目级约定)**: 4.2.2 默认是 STREAMABLE,会强制要求 `mcp-session-id` header。Claude Code 内置 MCP 客户端 (`mcp__arthas-mcp__*`) 不维护 session → 阻塞命令 (dashboard/thread/monitor 等) 持续收到 4.4KB "Session ID required" 错误堆栈,看起来"持续超时"。`infrastructure/arthas/arthas.properties` 锁死 `arthas.mcpProtocol=STATELESS`,wrapper attach 前 sync,新机器/升级都不回退。改协议需要 `pm2 restart ulticode-9001` 触发重 attach。
- **配置实际位置**: arthas-agent 读 `~/.arthas/lib/<version>/arthas/arthas.properties` (arthas.home 下的解压文件),**不是** `~/.arthas/arthas.properties`。项目级 `infrastructure/arthas/arthas.properties` 由 `sync_arthas_properties()` 在每次 attach 前 diff 同步(内容一致就跳过,改后才写)。
- **协议/类对应**: STATELESS 走 `McpStatelessHttpRequestHandler`;STREAMABLE 走 `McpStreamableHttpRequestHandler`(需 session)。简单命令 (dashboard/thread/jvm/sc/sysprop/ognl) 秒回;增强命令 (monitor/trace/watch) 需要在 `numberOfInvocations` 内实际触发目标方法,否则 30s timeout (arthas 行为,与协议无关)。
- `--telnet-port -1` 不被支持 (port out of range); 如需禁用 telnet 不传该参数即可
- `--attach-only` 模式: 启动进程退出但 HTTP/MCP agent 运行在目标 JVM 内
- Spring Boot 健康检查用 `lsof -ti :9001` 而非 `curl` (根路径可能返回 302/401)
- 脚本中用 `$CLAUDE_PROJECT_DIR` 解析项目根目录, 非 `$SCRIPT_DIR`
- Java 版本不一致 (arthas-boot Java 21 vs 目标 JVM Java 17) 会 WARN 但不影响功能
- **【强制】降级路径**：`dashboard` / `trace` / `watch` / `monitor` / `tt` 等阻塞型 Arthas 命令在 Claude Code 同步 MCP 上下文里固定 30s 超时；遇到时**不要重复重试**，按以下顺序降级：
  1. **首选**：`pm2 logs ulticode-9001 --nostream --lines 200`（同步拉最近 200 行应用日志，绝大多数性能/异常问题能在日志中定位）
  2. **次选**：`pm2 logs ulticode-9001 --nostream --lines 200 --raw`（含未格式化堆栈，便于与 `jad` 反编译后的类匹配）
  3. **再次**：`scripts/arthas-cli.sh` 进入交互式 telnet（不受 MCP 30s 限制），执行 `dashboard -n 1`、`thread -n 3`、`trace <Class> <method> -n 3` 等
  4. **回退**：`./mvnw -Dtest='*IT' test -B` 跑问题单测/集成测试做对照（验证 N+1 / 重复添加逻辑时直接走这条）
  5. 同步 MCP 阻塞 30s 时，**用 `mcp__plugin_context-mode_context-mode__ctx_execute` 跑 java 反射/grep 类检查**（后台子进程，无 MCP 超时）
- 见 `.claude/rules/backend/09-java-runtime-diagnostics.md` 的强制约束：阻塞命令必须带 `-n N` (N ≤ 5) 限制执行次数
- **Arthas MCP 实战手册**: wiki 概念页 [[wiki/concepts/arthas-diagnostics]] — 含 STATELESS pin 协议、三路互斥、watch/trace/tt 真实调用样本(本会话 2026-06-18 验证)、OGNL 速查、降级路径。`scripts/arthas-cli.sh` 是交互式入口。
- **增强命令并发模式**: 后台 bash `run_in_background=true` 持续 curl 触发目标端点(选没限流的 register/GET,login 是 60s/5 次窗口会被拦截),同步发 `mcp__arthas-mcp__trace/watch/stack` 配 `numberOfExecutions=1` + `timeout=12`;MCP 客户端 timeout 30s,所以 arthas 端 timeout 永远 ≤ 25

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

## CSRF 机制测试

Redis-backed CSRF token，格式 `tokenId:tokenValue`，POST/PUT/DELETE/PATCH 需要 `X-CSRF-Token` 头。

```bash
# 1. 登录获取 Token（保存 Cookie）
curl -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c cookies.txt | jq '.data.csrfToken'

CSRF_TOKEN=$(grep csrf_token cookies.txt | awk '{print $NF}')

# 2. 带 CSRF Token 的 POST 请求
curl -X POST http://localhost:9001/problems \
  -H "X-CSRF-Token: $CSRF_TOKEN" \
  -b cookies.txt \
  -D headers.txt -d '{"title":"Test"}'

# 3. 获取轮换后的新 Token
NEW_CSRF=$(grep -i "x-new-csrf-token" headers.txt | awk '{print $2}' | tr -d '\r')

# 4. 刷新失效 Token（GET 不需要 CSRF）
curl -X GET http://localhost:9001/auth/me -b cookies.txt | jq '.data.csrfToken'

# Redis 查看存储的 Token
redis-cli KEYS "csrf:*"
redis-cli GET "csrf:{userId}:{tokenId}"
```

**注意**: GET/HEAD/OPTIONS 不需要 CSRF；匿名用户不需要 CSRF；Token 24h TTL + 5m 宽限期。

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

### Sandbox Harness（代码执行沙箱 / D-form）

D-form 沙箱在 `docker/sandbox/`，源 → staging → 镜像三层：

- 源 `docker/sandbox/harness/{python,c,cpp,java}/` → `docker/sandbox/harness/build.sh` 预编译到 `docker/sandbox/harness-staging/` → Dockerfile COPY staging 到镜像 `/opt/harness/{lang}/`（**镜像打的是 staging，不是源**）
- 改 harness 源后必须重建：`./docker/sandbox/harness/build.sh python`（刷新 staging + 重建 `ulticode-sandbox-dform:phase2` + tag `:latest`）；`--no-docker` 只刷 staging。线上 `SANDBOX_IMAGE=ulticode-sandbox:latest`，重建后**新提交即时生效**（历史提交记录不变）
- `build.sh` 用**固定文件清单** copy：新增 harness 模块（如 `_case_runner.py`）必须同时加进 `build_<lang>()` 的 cp 清单 + .pyc 循环，否则镜像缺文件 → 每个用例 RE
- **Python 版本陷阱**：镜像 base（Debian bookworm）= Python 3.11，类型注解**即时求值**；主机可能是 3.14（PEP 649 惰性求值），本地 `pytest` 可能"假通过"。改注解/preamble 逻辑后必须用 `docker run` 在镜像（3.11）里端到端验证：

  ```bash
  docker run --rm -e SOLUTION_DIR=/job -v "$TMP":/job ulticode-sandbox:latest \
    python3 /opt/harness/python/main.py /job/input.json
  ```

- **Python preamble 契约**：用户代码**零 import**。`harness.py` 的 `build_solution_preamble()` 预注入 `typing.__all__` + 纯计算标准库（heapq/math/bisect/itertools/functools/operator/string/fractions/decimal/statistics/re/collections）+ collections 高频符号（deque/Counter/defaultdict/OrderedDict/namedtuple）+ `ListNode`/`TreeNode`。**绝不注入** `os`/`sys`/`subprocess`/`socket`/`shutil`/`ctypes`/`multiprocessing`（exit guard 只拦 `_exit`/`sys.exit`，放行这些模块会破坏沙箱隔离）
- 链表/树问题返回 `None`（空输入）会被 `normalize_return_value()` 规范化为 `[]`（LeetCode 约定），比较时不要当 `'null'` 处理

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
- **pnpm 11** for frontend and shared packages
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

变更认证/部署密钥/seed 账号/网络暴露前**先读** wiki 概念页 [[wiki/concepts/security-invariants]] + 关联的 [[concepts/refresh-token-hash-only-storage]] / [[concepts/csrf-mechanism]] / [[concepts/sandbox-security-contract]]，用 `security-review` skill。

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

### 工作位置优先级（main 优先）

**默认在 main 分支上直接工作**，与全局 `~/.claude/CLAUDE.md` 偏好一致。优先级：

1. **main（默认首选）**：多文件 / 多 commit / 跨模块的非平凡改动也直接在 main 上做
2. **新建分支（次之）**：用户显式要求"建分支" / "切到 X 分支" 时
3. **worktree（最末）**：仅当用户**显式**说"用 worktree" / "建 worktree" / "在隔离环境改" / "不要污染 main" 时才用

**触发 worktree / 切分支的唯一信号是用户的显式指令**；不因"改动规模大 / 跨模块 / 会话长 / 工作树有未提交改动"而主动切 worktree。若发现自己误开了 worktree 而用户未要求，应回到 main。

**护栏（不变）**：
- 提交前看 `git diff` + `git diff --check`
- Conventional commits：`<type>: <description>`
- `git push` / `merge` / `publish` / 改第三方资源 / 改写 git 历史 仍需用户**显式批准**
- 工作树可能含用户改动；不丢弃、不改写无关工作

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
- **Analysis docs**: Cross-module analysis reports go in `wiki/` at project root (e.g., `wiki/moderation-api-granularity-analysis.md`).

## CI

GitHub Actions on push/PR to main. Path-based change detection triggers only relevant jobs:
- Backend: Maven build + test (ci profile, excludes *IT) + Flyway migration validation
- Frontend: lint + type-check + test
- Docker: Build verification on Dockerfile changes
- Testcontainers: MySQL 9.1 + Redis 7 for integration tests

## PM2 Services

**PM2 管理后端 + 前端 (9001/9002/9003 全在 `ecosystem.config.cjs`)**

| Port | Name | Type |
|------|------|------|
| 9001 | ulticode-9001 | Spring Boot Backend |
| 9002 | ulticode-9002 | Console (Vite, `--host 127.0.0.1`) |
| 9003 | ulticode-9003 | Management (Vite, `--host 127.0.0.1`) |
| 8563 | ulticode-arthas | Arthas MCP Server (PM2 主, hook/cli 兜底, 三路互斥) |
| - | ulticode-init-db | 数据库迁移服务 (一次性任务) |
| 28848 | (nacos container) | Nacos 控制台 `/nacos` (默认账号 nacos/nacos) |

**⚠️ Vite `--host 127.0.0.1` 不可省**:Vite v8 默认绑 IPv6 `[::1]`,up.sh 就绪检查用 `127.0.0.1` 探测会假阴性(exit 1)。ecosystem 的 9002/9003 显式 `--host 127.0.0.1` 绕过——**改 ecosystem 勿删这两个 app 定义**(c26f45889 曾误删致回归)。前端单跑调试用 `cd console && pnpm exec vite`(绑 ::1,用 `curl localhost` 验证)。

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

### Startup Order (重要)

1. Docker 基础设施 (`ulticode-mysql/redis/nacos`) 必须先 Up/Healthy,再 `pm2 start`
2. 容器都 Exited 时直接 `pm2 start ecosystem.config.cjs` → init-db 报 `连接被拒绝` → 9001 反复崩溃 → 8563 永远空
3. 一键启动: `./scripts/dev/up.sh` (全量: 基础设施 → Nacos → 迁移 → dev-admin → install → PM2)。参数化: `--quick`(改代码后热重启,跳过 infra/迁移/admin/install 只重启 PM2)、`--only <apps>`(如 `--only 9001`)、`--no-frontend`/`--frontend-only`、`--skip-infra`/`--skip-migrate`/`--skip-bootstrap`/`--skip-install`、`-h` 看帮助
4. 手动按序: `docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis nacos` → `pm2 restart ulticode-init-db` → `pm2 restart ulticode-9001`

### 故障诊断信号

- `pm2 list` 中 `ulticode-9001` 的 ↺ (restart count) 快速增长 + `lsof -ti :9001` 为空 → 基础设施未就绪
- `9001` 与 `8563` 共享 PID 是**预期**的 (Arthas agent 跑在目标 JVM 内)
- `ulticode-init-db` 跑完进入 `stopped` 是**预期**的 (one-shot Flyway 任务);校验成功标志:`pm2 logs ulticode-init-db --nostream | grep "BUILD SUCCESS"`
- 容器健康检查:`docker inspect --format='{{.State.Health.Status}}' ulticode-{mysql,nacos}`
- **冷启动 up.sh 预期停留**:`up.sh` 的 dev-admin bootstrap 步骤(`spring-boot:run --web-application-type=none`)因非 daemon 线程(Redisson netty/调度器)会卡 ~105s 才被 `timeout` 兜底收尾(日志 `Bootstrap JVM did not self-exit... continuing`),属**预期**,勿干预;真正异常信号 = 后台 up.sh 的 output 文件 mtime 长期停滞 + PM2 空 + 端口 FREE(该步 mtime 会持续更新到 timeout 触发)
- **pm2 env 缓存 → 认证失败**:`pm2 restart --update-env` 不重读 `ecosystem.config.cjs` 的 `envFromFile`(用 daemon 缓存)。改 `.env` 后若 `9001` 报 `RedisWrongPasswordException`/DB 认证错且 ↺ 飙升,用 `pm2 delete ulticode-9001 && pm2 start ecosystem.config.cjs --only ulticode-9001` 强制重读。查进程实际 env 用 `tr '\0' '\n' < /proc/$(pm2 pid ulticode-9001)/environ | grep <VAR>`(`pm2 env <id>` 显示 stale,不可信)

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

<!-- headroom:learn:start -->
## Headroom Learned Patterns
*Auto-generated by `headroom learn` on 2026-06-13 — do not edit manually*

### PM2 + Worktree Workflow
*~1,200 tokens/session saved*
**PM2 cwd is locked to main worktree.** `pm2 start/restart ulticode-9001` runs the JAR from `/home/davidhlp/project/UltiCode/backend-spring/target/app.jar` (main worktree) — NOT from `.claude/worktrees/<branch>/...`. When you build a fix in a feature worktree, you MUST also `cp` the source files (or the rebuilt `target/app.jar`) into the main worktree before `pm2 restart`, otherwise `curl` tests run against stale code. Typical pattern: build in worktree → `cp -v <worktree>/backend-spring/src/main/java/.../* <main>/...` → `pm2 restart ulticode-9001 --update-env` → verify with curl. Revert main-worktree dirty copies with `git checkout -- <path>` after testing.

### Arthas MCP
*~800 tokens/session saved*
**Arthas STATELESS protocol is project-pinned** (added 2026-06-08). arthas-boot 4.2.2 default STREAMABLE requires `mcp-session-id` header that Claude Code's MCP client doesn't send → all blocking commands (dashboard/trace/watch/monitor) timeout 30s with 'Session ID required'. Fix lives in `infrastructure/arthas/arthas.properties` (`arthas.mcpProtocol=STATELESS`); `scripts/start-arthas.sh` syncs it to the **actually-effective** `~/.arthas/lib/4.2.2/arthas/arthas.properties` on every attach. After any properties change you MUST `pm2 restart ulticode-9001` to re-attach. Don't downgrade to 4.1.9 — STATELESS works on 4.2.2.

### Database / Flyway
*~400 tokens/session saved*
**Flyway checksum mismatch recovery**: if you edit a previously-applied migration, or pull changes that drift from `flyway_schema_history`, the next `mvn flyway:migrate` aborts with 'Migration checksum mismatch'. Fix: `./scripts/dev/migrate.sh repair` (NOT raw `flyway` — it's not on PATH; use the wrapper). The `flyway` CLI is only available via the wrapper script. After repair, re-run `./scripts/dev/migrate.sh migrate`.

### Build/Test Tool Execution
*~1,000 tokens/session saved*
Use `mcp__plugin_context-mode_context-mode__ctx_execute` for `./mvnw`, `pnpm` (type-check/lint/test), `mvn spring-boot:run`, `curl`/`wget` HTTP calls, and `WebFetch` — Bash redirects them anyway (~50+ occurrences in the data set). Direct ctx_execute saves the redirect-error round-trip. Note: `mvn spring-boot:run` is long-running; use the ctx_execute subprocess and return quickly, do not wait for full startup in the same call.

### CodeGraph Initialization
*~500 tokens/session saved*
CodeGraph is NOT auto-initialized at session start. Sessions 59695762, 261af84a, 6bad518f, 40545a8d, 20611b82, 96847c04, 2c3ad522, c2f5000f each hit 1-3 `codegraph_context/search` calls returning "No CodeGraph project is loaded". When the task does NOT need the index, skip codegraph and go straight to `Bash` with `grep`/`find` + `Read`. When the task does need it, the user must explicitly run `codegraph init` first; do not retry codegraph calls hoping they will succeed.

### Tool Availability
*~350 tokens/session saved*
The dedicated `Grep` and `Glob` tools are NOT available in this environment (sessions c2f5000f, 20611b82, 545777a4, 35bfe466, 796d878c each lost 1-2 calls to "No such tool available: Grep/Glob"). Use `Bash` with `grep` / `rg` / `find` directly. For multi-condition `find` use plain `find` — `rtk find` does not support `-not`/`-exec`/`-or`.

### Blocking Bash Patterns
*~250 tokens/session saved*
`sleep N && <command>` (N ≥ 5) is rejected by the hook as blocking — use `Monitor` with an `until <condition>; do sleep 2; done` loop instead. Hit in sessions 30e60dd3, 545777a4, 70c7770b, 96847c04, 261af84a. For one-shot waits, run the command then return and re-check in a follow-up turn — do not chain `sleep 25 && pm2 status`.

### PRP Output Locations
*~200 tokens/session saved*
PRP (ecc:prp-plan / -implement / -commit / -code-review) artifacts have a fixed layout: plans go in `.claude/PRPs/plans/{name}.plan.md`, reports in `.claude/PRPs/reports/{name}.report.md`, reviews in `.claude/reviews/{name}-review.md`. Move completed plans to `.claude/PRPs/plans/completed/`. The whole `.claude/PRPs/` tree is gitignored (see existing rule), so use `git add -f` or move to `wiki/` if you need to commit the artifacts.

### Arthas MCP Lifecycle
*~600 tokens/session saved*
Arthas MCP is managed by Claude Code SessionStart/SessionEnd hooks (lifecycle-bound), NOT PM2. If it disconnects mid-session, check `scripts/start-arthas.sh` and port 8563 — do not `pm2 restart ulticode-arthas` (that app no longer exists).

### Project Startup Workflow
*~500 tokens/session saved*
User says "启动这个项目" → run `./scripts/dev/up.sh` (or `--skip-install` when deps unchanged). It sequences: docker compose up → Nacos bootstrap → Flyway migrate → PM2 start. Required order: containers healthy → ulticode-init-db → ulticode-9001.

### rtk Command Limitations
*~400 tokens/session saved*
`rtk find` does NOT support compound predicates (`-not`, `-exec`, `-or`) — fall back to plain `find` for multi-condition searches. `rtk` also warns "untrusted project filters" on every call until you run `rtk trust` once.

### PRP Artifacts Gitignore
*~300 tokens/session saved*
`.claude/PRPs/{plans,reports,reviews}` is gitignored. To commit PRP artifacts use `git add -f` or move them to a tracked path like `wiki/`.

<!-- headroom:learn:end -->
