# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

UltiCode is an online programming platform (similar to LeetCode) built with:

- **Backend**: Spring Boot 3.5 (Java 17) with MyBatis-Plus, MySQL, Redis
- **Frontend Console**: Vue 3 + Vite + Tailwind CSS (user-facing application)
- **Frontend Management**: Vue 3 + Vite + Tailwind CSS (admin dashboard)
- **Recommendation Service**: Dubbo3 + Spark microservices (optional)
- **Database**: MySQL with Flyway migrations (managed by `db-manager`)

## Common Commands

### Quick Start (Recommended)

```bash
# Start Docker services (MySQL, Redis, Nacos)
docker compose up -d

# Start all services via PM2
pnpm install                 # Install frontend deps if needed
pm2 start ecosystem.config.cjs

# Check status / stop / restart
pm2 status
pm2 stop all / pm2 restart all
```

### Individual Services

```bash
# Backend (Spring Boot) - runs on port 9001
cd backend-spring && ./mvnw spring-boot:run

# Console Frontend - runs on port 9002
cd console && pnpm run dev

# Management Frontend - runs on port 9003
cd management && pnpm run dev

# Recommendation Service (optional) — MUST build first!
cd recommendation && mvn install -DskipTests             # Required first time
cd recommendation && mvn -pl recommend-provider spring-boot:run
cd recommendation && mvn -pl recommend-web spring-boot:run
```

### Database Management

```bash
# Run Flyway migrations (from project root)
cd db-manager
python -m db_manager.cli migrate     # Run all pending migrations
python -m db_manager.cli status      # Check migration status
python -m db_manager.cli info        # Detailed migration info
python -m db_manager.cli repair      # Repair Flyway schema history
```

### Testing & Quality

```bash
# Root-level (runs for all packages)
pnpm test                  # Run all tests
pnpm lint                  # Lint all code
pnpm type-check            # TypeScript check all
pnpm quality               # lint + type-check + test

# Individual frontends
cd console && pnpm test
cd management && pnpm test:coverage

# Backend (Spring Boot)
cd backend-spring && ./mvnw test
```

### Database Management Notes

- **Python 路径**: db-manager 有独立 venv，必须用 `db-manager/.venv/bin/python -m db_manager.cli`，系统 python 不可用
- **修改迁移文件后**: 已有迁移的 checksum 会变化，需先 `clean --force` 再 `migrate`
- **MySQL 访问**: 通过 Docker 容器访问，`docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode`
- **种子数据分布**: V1(users/submissions/permissions), V2(problems/tags/lists), V3(contests/rankings), V4(forum), V8(collections), V9(solutions)
- **外键**: 迁移文件以 `SET FOREIGN_KEY_CHECKS=0` 开头，`SET FOREIGN_KEY_CHECKS=1` 结尾

### Build

```bash
# Frontend builds
cd console && pnpm build
cd management && pnpm build

# Backend build
cd backend-spring && ./mvnw package -DskipTests
```

### Known Pitfalls

- **ESLint compatibility**: Console uses ESLint 9.x — `eslint-plugin-vue` must be `^9.30.0`, not 10.x (TypeScript peer dep conflict)
- **vitest setupFiles**: Do not add setup file paths that don't exist; vitest will fail to resolve them
- **pnpm build scripts**: Use `.npmrc` `onlyBuiltDependencies` field instead of `pnpm approve-builds` for CI
- **Recommendation build order**: `recommendation` module must be built (`mvn install -DskipTests`) BEFORE `backend-spring` — backend depends on `recommend-api`

## Service Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend Layer                           │
├────────────────────────┬────────────────────────────────────────┤
│  Console (9002)        │  Management (9003)                    │
│  User-facing app       │  Admin dashboard                      │
│  Problem solving,      │  User management, audit logs,         │
│  contests, submissions │  content moderation, analytics        │
└────────────┬───────────┴──────────────────┬─────────────────────┘
             │                              │
             └──────────────┬───────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot 9001)                   │
├─────────────────────────────────────────────────────────────────┤
│  Modules: auth, user, problem, submission, contest, forum,     │
│  solution, notification, subscription, moderation, search,     │
│  achievement, i18n, backup, email, monitoring, vote, admin,    │
│  bookmark, edgeoperations, permission, problemlist, queue,     │
│  recommendation, refreshtoken, websocket                       │
└────────────────────────────┬────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────────────┐
│ MySQL (23306)   │ │ Redis (26379)   │ │ Recommendation Service  │
│ Primary DB      │ │ Cache, Sessions │ │ Dubbo3 + Spark (9004)   │
│ MyBatis-Plus    │ │ Rate Limiting   │ │ Optional, Nacos (28848) │
└─────────────────┘ └─────────────────┘ └─────────────────────────┘
```

## Key Patterns

### Backend Response Format

All API responses use the `Result<T>` wrapper:

```json
{
  "code": 0,           // 0 = success, non-zero = error
  "message": "success",
  "data": { ... },     // Response payload
  "traceId": "t-1234567890"
}
```

Frontend `request.ts` automatically unwraps responses, returning `response.data` directly.

### Frontend API Client Pattern

```typescript
// management/src/api/example.ts
import { apiGet, apiPost } from "@/utils/request";

export const exampleApi = {
  async getList(): Promise<Item[]> {
    return apiGet<Item[]>("/endpoint");
  },
  async create(data: CreateDTO): Promise<Item> {
    return apiPost<Item>("/endpoint", data);
  },
};
```

### Authentication Flow

- JWT tokens stored in httpOnly cookies (access_token, refresh_token)
- CSRF token required for state-changing requests (POST, PUT, PATCH, DELETE)
- Frontend reads CSRF from localStorage and sends as `X-CSRF-Token` header
- Login response includes both `access_token` (cookie) and `csrf_token` (response body)

### Testing Authenticated APIs with curl

```bash
# 1. Login to get session cookie
curl -s -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/cookies.txt

# 2. Use cookie for authenticated requests
curl -s http://localhost:9001/users/u-admin-001/stats \
  -b /tmp/cookies.txt | jq .
```

- Login returns `csrfToken` in response body (not a JWT token)
- Authenticated endpoints require the session cookie, not Bearer token
- Use `-c /tmp/cookies.txt` to save cookie, `-b /tmp/cookies.txt` to send cookie
- JSON解析：jq解析失败时用 `curl -s ... | python3 -m json.tool` 备用

### Backend Module Structure

```
backend-spring/src/main/java/com/ulticode/
├── common/           # Shared utilities, configs, exceptions
│   ├── response/     # Result wrapper, PageResult
│   ├── exception/    # GlobalExceptionHandler, BusinessException
│   ├── config/       # SecurityConfig, WebConfig, RedisConfig
│   └── annotation/   # @CurrentUser, @RequireRole, @RateLimit
├── security/         # JWT filters, CSRF service
├── modules/          # Feature modules
│   ├── auth/         # Authentication, login, OAuth
│   ├── user/         # User CRUD, profile
│   ├── problem/      # Problems, test cases, examples
│   ├── submission/   # Code submissions, judging
│   ├── contest/      # Contests, rankings
│   └── ...           # Other domain modules
└── websocket/        # Real-time communication
```

Each module typically contains:

- `controller/` - REST endpoints
- `service/` - Business logic
- `entity/` - Database entities (MyBatis-Plus)
- `mapper/` - MyBatis mappers
- `dto/` - Request/Response DTOs

### Database Migrations

Migrations are SQL files in `db-manager/migrations/` managed by Flyway with naming convention:

```
V{version}__description.sql
```

Run via `db-manager` CLI (Python, uses Flyway under the hood).

## Environment Variables

Backend reads from `backend-spring/.env`:

```
DATABASE_URL=mysql://user:pass@localhost:23306/ulticode
DB_HOST=localhost
DB_PORT=23306
JWT_SECRET=your-secret-key
REDIS_HOST=localhost
REDIS_PORT=26379
```

Frontend uses Vite env vars (`VITE_API_BASE_URL`).

## Port Reference

| Service          | Port  |
| ---------------- | ----- |
| Backend (Spring) | 9001  |
| Console          | 9002  |
| Management       | 9003  |
| Recommend-Provider | 9004 |
| Recommend-Web    | 9005  |
| MySQL            | 23306 |
| Redis            | 26379 |
| Nacos            | 28848 | services need `NACOS_PORT=28848` env var |

## Debugging

- Backend logs: `tail -f /tmp/ulticode-backend.log`
- Console logs: `tail -f /tmp/ulticode-console.log`
- Management logs: `tail -f /tmp/ulticode-management.log`
- Swagger UI: `http://localhost:9001/swagger-ui.html`
- Health check: `curl http://localhost:9001/actuator/health`
- API docs: `http://localhost:9001/api-docs` (springdoc path: `/api-docs`)
- Backend debug: `pm2 logs ulticode-9001 --lines 50 --nostream 2>/dev/null | grep -i error`
- Query DB during API testing: `docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SELECT ..."`

## Backend Startup Issues

- `./mvnw spring-boot:run -Dmaven.test.skip=true` - 验证后端启动（跳过测试编译）
- PM2 restart 后环境变量重载：`pm2 restart ulticode-9001 --update-env`，否则 .env 修改不生效
- PM2 环境变量：ecosystem.config.cjs 的 dotenv fallback 实现需手动解析 .env，否则 JWT_SECRET/REDIS_PASSWORD 会丢失
- springdoc 版本：2.7.0 与 Spring Boot 3.2.5 不兼容（LiteWebJarsResourceResolver 缺失），降级到 2.6.0
- Flyway 迁移失败：db-manager 不支持 `-outOfOrder`，直接用 `docker exec ulticode-mysql mysql ...` 执行 SQL
- `db-manager/.venv/bin/python -m db_manager.cli repair` - 修复 Flyway checksum 不匹配
- Redis 密码变更后需 `docker compose up -d redis` 重启容器，不能只 restart 容器
- Maven Build Order: `recommend-api` 模块必须在 `backend-spring` 之前构建。执行顺序：
  1. `cd recommendation && mvn install -DskipTests` — 安装 recommend-api 到本地 Maven 仓库
  2. `cd backend-spring && ./mvnw spring-boot:run` — 启动后端（此时能找到 recommend-api）
  本地开发和 CI/CD 流水线都需要遵守此顺序

## Code Search Tools

### Grep (ripgrep)

Built-in `Grep` tool wraps ripgrep — always prefer it over `grep`/`rg` Bash commands.

```yaml
# 按内容搜索文件
pattern: "TODO|FIXME"
glob: "*.java"              # 按文件类型过滤
path: "backend-spring"      # 限定目录
output_mode: "content"      # content | files_with_matches | count
-i: true                    # 忽略大小写
context: 3                   # 上下文行数（等同 -C）

# 常用模式
pattern: "class\\s+\\w+Controller"   # 正则匹配 Controller 类
pattern: "apiGet|apiPost"            # 搜索 API 调用
glob: "*.{vue,ts}"                   # 多种扩展名
multiline: true                      # 跨行匹配
```

**经验法则**：
- 精确路径 → `Glob`（按文件名模式找文件）
- 精确内容 → `Grep`（按内容搜索）
- 模糊/多轮搜索 → Agent（`Explore` 类型，适合复杂探索）

### ast-grep (AST 模式搜索与替换)

ast-grep 基于 AST 匹配，比文本正则更精确。通过 MCP 工具 `ast_grep_search` / `ast_grep_replace` 使用。

**元变量**：`$NAME` 匹配单个节点，`$$$ARGS` 匹配多个节点。

```yaml
# 搜索模式
pattern: "console.log($MSG)"           # 找所有 console.log
pattern: "function $NAME($$$ARGS)"     # 找所有函数声明
pattern: "$X === null"                 # 找 null 相等检查
pattern: "if ($COND) { $$$BODY }"      # 找所有 if 语句
language: "typescript"                 # javascript|typescript|tsx|python|java|kotlin|go|rust|c|cpp|html|css|json|yaml
path: "console/src"                    # 限定目录
maxResults: 20                         # 限制结果数

# 替换模式（dryRun=true 默认只预览，设 false 应用）
pattern: "console.log($MSG)"
replacement: "logger.info($MSG)"
language: "typescript"
dryRun: false                          # 设为 false 实际执行

# Java 示例 — 查找 @GetMapping 方法
pattern: "@GetMapping($PATH)$$$PUBLIC $RET $NAME($$$ARGS) { $$$BODY }"
language: "java"

# Vue/TSX 示例 — 查找 v-if 指令对
pattern: "class=\"$CLS\""
replacement: "className=\"$CLS\""
language: "tsx"
```

**适用场景**：
- 精确重构（重命名模式、批量替换）→ `ast_grep_replace`
- 查找特定代码结构（所有 catch 块、所有 import）→ `ast_grep_search`
- 纯文本/关键字搜索 → `Grep` 更轻量

## Frontend Design System

Both `console/` and `management/` share a unified Solarized color palette using OKLCH color space with `--radius: 0` (sharp corners). Reference the local skills for detailed specs:
- `ulticode-solarized-colors` — Full OKLCH color token reference
- `ulticode-layout` — Layout architecture, sidebar, responsive patterns
- `ulticode-console-styles` — Code blocks, markdown, charts (console-specific)

### Design Rules
- Color space: OKLCH only, never hex/HSL
- Theme toggle: `.dark` class on root element
- CSS framework: Tailwind CSS v4 with `@theme inline`, no tailwind.config.ts
- UI components: shadcn-vue (new-york style) + Radix Vue + Lucide icons
- Console-only: KaTeX math, highlight.js Solarized syntax, chart visualization tokens
- Chart data colors are light/dark invariant; only grid/tooltip change between themes

## PM2 Services

**Prerequisite:** `npm install -g pm2` (PM2 is a global tool, not a project dependency)

### Slash Commands

`/pm2-all` `/pm2-all-stop` `/pm2-all-restart` — Batch operations
`/pm2-9001` `/pm2-9002` `/pm2-9003` — Start single service + logs
`/pm2-9001-stop` `/pm2-9002-stop` `/pm2-9003-stop` — Stop single service
`/pm2-9001-restart` `/pm2-9002-restart` `/pm2-9003-restart` — Restart single service
`/pm2-docker` `/pm2-docker-stop` — Docker container management
`/pm2-logs` `/pm2-status` — Monitoring

### Docker Services (via docker-wrapper.cjs)

| Name          | Action | Description                    |
| ------------- | ------ | ------------------------------ |
| docker-up     | up     | Start all containers (MySQL, Redis, Nacos) |
| docker-down   | down   | Stop and remove all containers |
| docker-logs   | logs   | View container logs (follow)   |
| docker-ps     | ps     | Show container status          |

### Application Services

| Port | Name          | Type                             |
| ---- | ------------- | -------------------------------- |
| 9001 | ulticode-9001 | Spring Boot (Backend)            |
| 9002 | ulticode-9002 | Vite (Console)                   |
| 9003 | ulticode-9003 | Vite (Management)                |
| 9004 | ulticode-9004 | Spring Boot (Recommend-Provider) |
| 9005 | ulticode-9005 | Spring Boot (Recommend-Web)      |

#### Recommendation Services (9004/9005) — First-Time Setup

```bash
# Step 1: Build all internal modules (REQUIRED before first start)
cd recommendation && mvn install -DskipTests

# Step 2: Ensure Nacos container running, then start with correct env
pm2 restart ulticode-9004 ulticode-9005 --update-env
```

**Troubleshooting:**
- `DependencyResolutionException`: Run `cd recommendation && mvn install -DskipTests`
- `LoggerFactory is not a Logback LoggerContext`: `slf4j-simple` conflict — removed from recommend-core
- Nacos connection refused: Verify `NACOS_PORT=28848` in env and Nacos container running
- Maven Central timeout: Ensure `~/.m2/settings.xml` has Aliyun mirror
- Dubbo WARN `empty url address list` (error 1-37): Set `enable-empty-protection: "true"` in `dubbo.registry.parameters` (NOT `empty-protection`; property has no setter in RegistryConfig 3.2.14, must use parameters map)
- Dubbo WARN `Ignore empty notify urls` (error 1-4): Expected — provider self-subscribes to empty `configurators` category, harmless

**Terminal Commands:**

```bash
# Docker management
pm2 start docker-up     # Start Docker containers (MySQL, Redis, Nacos)
pm2 start docker-down   # Stop Docker containers
pm2 logs docker-up      # View Docker logs

# Application services
pm2 start ecosystem.config.cjs   # Start all app services
pm2 start all                   # After first time
pm2 stop all / pm2 restart all
pm2 start ulticode-9001 / pm2 stop ulticode-9001

# Common
pm2 logs / pm2 status / pm2 monit
pm2 save                         # Save process list
pm2 resurrect                    # Restore saved list
```

## gstack (REQUIRED — global install)

**Before doing ANY work, verify gstack is installed:**

```bash
test -d ~/.claude/skills/gstack/bin && echo "GSTACK_OK" || echo "GSTACK_MISSING"
```

If GSTACK_MISSING: STOP. Do not proceed. Tell the user:

> gstack is required for all AI-assisted work in this repo.
> Install it:
> ```bash
> git clone --depth 1 https://github.com/garrytan/gstack.git ~/.claude/skills/gstack
> cd ~/.claude/skills/gstack && ./setup --team
> ```
> Then restart your AI coding tool.

Do not skip skills, ignore gstack errors, or work around missing gstack.

Using gstack skills: After install, skills like /qa, /ship, /review, /investigate,
and /browse are available. Use /browse for all web browsing.
Use ~/.claude/skills/gstack/... for gstack file paths (the global path).

## Behavioral Guidelines

These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

These guidelines are working if fewer unnecessary changes appear in diffs, fewer rewrites are needed due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
