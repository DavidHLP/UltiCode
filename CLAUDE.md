# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **职责分工**:本文件**只**承载 Claude Code 在本项目工作所需的**角色定位 + 项目特有陷阱 + 运维速查 + 工具参考指针**。仓库结构、工具链版本、Verification Matrix、Database Rules、Security Invariants、Frontend/Backend Conventions 的**权威定义在 [AGENTS.md](./AGENTS.md)**;面向人类的介绍在 [README.md](./README.md)。本文件不重复二者已有内容,触及对应主题时按下方表格跳转。

---

## Related Documentation

| 文件 | 作用 | 何时查阅 |
|------|------|----------|
| **[AGENTS.md](./AGENTS.md)** | 仓库级权威指南:Project Map、Toolchain、Development Startup、Verification、Database Rules、Security Invariants、Frontend/Backend Conventions | 进入仓库、跨模块协作、提交前自检、需要权威约定时**优先**查此 |
| **[README.md](./README.md)** | 面向人类的项目介绍、架构概览、快速开始、技术栈、CI/CD | 新人 onboarding、对外展示 |
| **[.claude/rules/](./.claude/rules/)** | Claude Code 按 `paths:` 触发的子规则 (backend/ frontend/ database/) | 触及对应路径时自动加载,无需手动查 |
| **[.cursor/rules/](./.cursor/rules/)** | Cursor IDE 按 globs 触发的 `.mdc` 规则 | Cursor 环境中触及 `console/`/`management/`/`backend-spring/` 时 |
| **[.claude/agents/](./.claude/agents/)** | 可调用的领域子代理 | 复杂规划、代码审查、安全审查、TDD、构建修复等 |
| **[wiki/](./wiki/)** | LLM Wiki 活知识库(仅 `entities/` + `overview/` + 元数据,2026-07-09 起不再托管 ADR/概念层)。顶层:`README.md`(着陆)· [`SCHEMA.md`](./wiki/SCHEMA.md)(三层/三动作/写作规范 + §10 daily-notes + §12 manifest)· `index.md`(内容目录)· `log.md`(维护时间线);子目录:`overview/`·`entities/`·`templates/`·`daily-notes/` | 查架构/部署/合规走 `wiki/index.md` 的"先读这份"表;**改 wiki 内容后跑 `scripts/dev/wiki-manifest.sh` 刷新 `wiki/.meta/manifest.json`** |
| **[shared/theme/](./shared/theme/)** | 前端主题系统。**项目字体 = LXGW WenKai 楷体,全站统一(含 Monaco 编辑器 / ECharts 默认字体)** | 改前端颜色/字体/密度/动效/组件样式时先读 `shared/theme/src/applyThemeToDOM.ts` |
| **[wiki/.meta/rtk-reference.md](./wiki/.meta/rtk-reference.md)** | RTK (Rust Token Killer) token 优化命令完整参考 | 查 rtk 命令节省 token 的具体用法 |

子代理简表(详见 `.claude/agents/<name>.md`):
`planner` · `architect` · `code-reviewer` · `java-reviewer` · `security-reviewer` · `tdd-guide` · `build-error-resolver` · `refactor-cleaner` · `doc-updater` · `e2e-runner` · `rust-reviewer`

修改 `shared/` 跨端 DTO/enum 字段时,遵循 `cross-stack-dto-granularity-alignment` skill 的审计流程。

---

## Project Role

**全栈工程师 + 系统管理员**:你是该项目的核心技术负责人,具备完整的自主问题诊断与解决能力。

1. **自主诊断**:接手问题时主动检索并分析前后端运行日志,精准定位问题根源
2. **全局溯源**:跨文件查看前后端完整代码链路,不局限于局部
3. **运维能力**:`pm2`(进程监控/重启) · `docker-compose`(容器编排) · `Flyway`(数据库迁移,位于 `init-db/`)
4. **架构决策**:根据最佳实践自主决定技术方案,确保向后兼容与系统稳定性

**闭环管理**:发现问题 → 分析日志 → 修改代码/SQL → 容器/进程/数据库部署 → 验证结果

| 操作类型 | 执行前要求 |
|---------|-----------|
| 大规模代码修改 | 输出诊断结论 + 行动计划 |
| 执行 Flyway 迁移 | 确认迁移脚本向后兼容,必要时先 dry-run |
| 重启核心服务 | 确认依赖服务状态正常 |

> **Toolchain / Verification Matrix / Database Rules / Security Invariants / Frontend/Backend Conventions**:权威定义在 [AGENTS.md](./AGENTS.md),此处不再复述。下文仅记录 AGENTS.md 未覆盖的**项目特有陷阱**与**运维速查**。

---

## 项目特有陷阱

### MySQL 容器化操作(字符集双重编码)

⚠️ `ulticode-mysql` 容器默认 `character_set_client=latin1`,直接 `docker exec mysql -e "INSERT 中文..."` 会导致**双重 UTF-8 编码**(应用读取后显示为 `æžå¨œ` 这类乱码)。

**正确做法**:在 mysql 命令加 `--default-character-set=utf8mb4`,或在 SQL 开头执行 `SET NAMES utf8mb4;`

```bash
# ✅ 正确
set -a; source .env; set +a
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME"

# ❌ 错误(中文会被双重编码)
docker exec ulticode-mysql mysql -u ulticode -p'...' -e "INSERT INTO t (name) VALUES ('王明')"

# 验证存储字节(标准 UTF-8):王明 应为 E78E8BE6988E (12 字节)
docker exec ulticode-mysql mysql --default-character-set=utf8mb4 -u ulticode -p'...' \
  ulticode -e "SELECT HEX(name) FROM users WHERE username='xxx'"
```

**注意**:后端 JDBC URL 已含 `useUnicode=true&characterEncoding=UTF-8`,走 Spring Boot/Flyway 的应用连接字符正常,**只有手工 `docker exec mysql` 路径**有该问题。修复已双重编码的数据:`SET NAMES utf8mb4; UPDATE users SET name='正确的姓名' WHERE id='...';`

### 运行时调试(Arthas MCP)

`arthas-boot.jar` (4.2.2) 在 `tools/`;配置 `infrastructure/arthas/arthas.properties` 项目级维护,wrapper attach 时自动同步到 `~/.arthas/lib/<version>/arthas/arthas.properties` (arthas-agent 真正读的位置)。MCP 端点 `http://localhost:8563/mcp` (STATELESS)。

**三路启动(自动互斥,基于端口 `:8563` + PID 文件双重检测)**:

| 路径 | 触发方式 | 推荐场景 |
|------|---------|---------|
| **PM2**(主) | `pm2 start ecosystem.config.cjs` 启动 `ulticode-arthas` | 本地/SSH/CI/跟随 `pm2 save` 自启 |
| **Claude Code hook** | `.claude/settings.json` 的 `SessionStart/SessionEnd` | Claude Code 会话内,无 PM2 时 |
| **CLI**(兜底) | `scripts/arthas-cli.sh start` | 任意环境手动/调试 |

互斥:任一路检测到 `:8563` 在监听或 PID 文件存在 → 跳过;PID 文件格式 `PID\nLAUNCHER`;SessionEnd 只停 `launcher=hook` 的,`pm2`/`cli` 的各自管理。CLI:`scripts/arthas-cli.sh {start\|stop\|restart\|status\|logs}`。手动 attach:`java -jar tools/arthas-boot.jar --attach-only --http-port 8563 <PID>`。

**关键约束(踩坑沉淀)**:
- **协议 STATELESS(强制项目级约定)**:4.2.2 默认 STREAMABLE 会强制要求 `mcp-session-id` header,Claude Code 内置 MCP 客户端不维护 session → 阻塞命令(dashboard/thread/monitor 等)持续收到 "Session ID required"。`infrastructure/arthas/arthas.properties` 锁死 `arthas.mcpProtocol=STATELESS`,wrapper attach 前 sync。**改协议需 `pm2 restart ulticode-9001` 触发重 attach**。不要降级到 4.1.9。
- **配置实际位置**:arthas-agent 读 `~/.arthas/lib/<version>/arthas/arthas.properties`(不是 `~/.arthas/arthas.properties`);项目级文件由 `sync_arthas_properties()` 在每次 attach 前 diff 同步。
- Spring Boot 健康检查用 `lsof -ti :9001` 而非 `curl`(根路径可能 302/401);脚本用 `$CLAUDE_PROJECT_DIR` 解析根目录。
- **【强制】阻塞命令降级路径**:`dashboard`/`trace`/`watch`/`monitor`/`tt` 在 Claude Code 同步 MCP 上下文里**固定 30s 超时,不要重复重试**,按序降级:
  1. `pm2 logs ulticode-9001 --nostream --lines 200`(同步拉最近 200 行,绝大多数问题能在日志定位)
  2. `pm2 logs ulticode-9001 --nostream --lines 200 --raw`(含未格式化堆栈)
  3. `scripts/arthas-cli.sh` 进交互式 telnet(不受 MCP 30s 限制),执行 `dashboard -n 1`/`thread -n 3`/`trace <Class> <method> -n 3`
  4. `./mvnw -Dtest='*IT' test -B` 跑问题单测做对照
  5. 同步 MCP 阻塞时,用 `mcp__plugin_context-mode_context-mode__ctx_execute` 跑 java 反射/grep(后台子进程,无 MCP 超时)
- **增强命令并发模式**:后台 bash `run_in_background=true` 持续 curl 触发目标端点(选没限流的 register/GET,login 是 60s/5 次窗口会被拦截),同步发 `mcp__arthas-mcp__trace/watch/stack` 配 `numberOfExecutions=1` + `timeout=12`;arthas 端 timeout 永远 ≤ 25。
- 阻塞命令必须带 `-n N` (N ≤ 5) 限制执行次数(见 `.claude/rules/backend/09-java-runtime-diagnostics.md`)。
- **完整实战手册**:本文件 § Arthas MCP(本节) + `.claude/rules/backend/09-java-runtime-diagnostics.md` — STATELESS pin、三路互斥、watch/trace/tt 真实调用样本、OGNL 速查、降级路径。

**Arthas 命令速查**:

| 命令 | 用途 | 典型问题 |
|------|------|---------|
| `dashboard` | 系统运行状态总览 | 内存/CPU 问题 |
| `thread -n 5` / `thread -b` | 最忙线程 / 死锁 | 线程死锁、CPU 飙高 |
| `jad <class>` | 反编译类 | 类加载、源码核对 |
| `watch <class> <method> <expr>` | 观察方法调用(参数/返回值/异常) | 接口参数异常 |
| `trace <class> <method>` | 方法内部调用链路耗时 | 方法耗时过长 |
| `stack <class> <method>` | 方法调用堆栈 | 定位调用方 |
| `ognl '<expr>'` | 执行 OGNL 表达式 | 动态求值 |
| `sc -d <class>` | 搜索类详细信息 | 类加载问题 |

### CSRF 机制测试

Redis-backed CSRF token,格式 `tokenId:tokenValue`,POST/PUT/DELETE/PATCH 需要 `X-CSRF-Token` 头。

```bash
# 1. 登录获取 Token(保存 Cookie)
curl -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c cookies.txt | jq '.data.csrfToken'

CSRF_TOKEN=$(grep csrf_token cookies.txt | awk '{print $NF}')

# 2. 带 CSRF Token 的 POST 请求
curl -X POST http://localhost:9001/problems \
  -H "X-CSRF-Token: $CSRF_TOKEN" -b cookies.txt \
  -D headers.txt -d '{"title":"Test"}'

# 3. 获取轮换后的新 Token
NEW_CSRF=$(grep -i "x-new-csrf-token" headers.txt | awk '{print $2}' | tr -d '\r')

# 4. 刷新失效 Token(GET 不需要 CSRF)
curl -X GET http://localhost:9001/auth/me -b cookies.txt | jq '.data.csrfToken'

# Redis 查看存储的 Token
redis-cli KEYS "csrf:*"
redis-cli GET "csrf:{userId}:{tokenId}"
```

**注意**:GET/HEAD/OPTIONS 不需要 CSRF;匿名用户不需要 CSRF;Token 24h TTL + 5m 宽限期。完整机制见本节(CSRF)+ `backend-spring/src/main/java/com/ulticode/security/`。

### Sandbox Harness(D-form 代码执行沙箱)

D-form 沙箱在 `docker/sandbox/`,**源 → staging → 镜像**三层。**镜像 `ulticode-sandbox:latest` 不随仓库分发,必须本地构建**;缺失或损坏时所有判题返回笼统 `Runtime Error`。完整诊断与重建手册见本节(下方"Sandbox Harness")。

- 源 `docker/sandbox/harness/{python,c,cpp,java}/` → `build.sh` 预编译到 `docker/sandbox/harness-staging/` → Dockerfile COPY staging 到镜像 `/opt/harness/{lang}/`(**镜像打的是 staging,不是源**)
- **【诊断指纹】镜像缺失/启动失败的判题症状**:`verdict=Runtime Error` + `memory=0.0MB` + `detail="Runtime error"`(笼统,无具体异常/堆栈)。机理:`SandboxExecutorImpl` 把"非 0 退出 + 非编译错误"映射为 `RUNTIME_ERROR`,且 `CodeExecutionHelperImpl.sanitizeSandboxOutput` 会**过滤含 `docker`/`OCI runtime` 的行**,空结果回退为 "Runtime error" —— docker 层错误被完全掩盖。看到此指纹 → 先 `docker images | grep ulticode-sandbox` 确认镜像存在,再看下一条 seccomp 路径。
- **【seccomp 路径相对后端 cwd】**:`SANDBOX_SECCOMP_PROFILE` 由后端 JVM 解析,**后端 cwd = `backend-spring/`**(PM2 ecosystem 设定)。故 `.env` 必须用 `../docker/sandbox/seccomp-profile.json`(相对 backend-spring),**不能**用 `docker/sandbox/...`(相对 repo root)。后者使 `docker run --security-opt seccomp=<不存在>` 启动失败 → 走上面"笼统 RE"路径。**镜像建好后仍报笼统 RE → 99% 是此路径错**。`init-env.sh` 与 `.env.example` 已默认带 `../` 前缀,手改 `.env` 时勿删。
- **【假开关】`SANDBOX_ENABLED=false` 不影响判题**:执行器激活条件是 `@ConditionalOnProperty("sandbox.executor")`(默认 `docker`),与 `code-execution.sandbox.enabled` 无关。判题始终走 docker 沙箱;该 flag 是历史占位,保留 false 即可。
- 改 harness 源后必须重建:`./docker/sandbox/harness/build.sh`(刷新 staging + 重建 `ulticode-sandbox-dform:phase2` + tag `:latest`);`--no-docker` 只刷 staging;`build.sh <lang>` 只构建指定语言。线上 `SANDBOX_IMAGE=ulticode-sandbox:latest`,重建后**新提交即时生效**(历史提交记录不变)。
- **【base-17 前置 + alpine/musl/代理坑】** `Dockerfile` FROM `ulticode-sandbox:base-17`(本地一次构建,极少变;`build.sh` 启动前检查)。两个高频陷阱(完整复现见 wiki sandbox-rebuild):
  1. **base 是 alpine 3.19(musl),非 Debian**:host(Red Hat/Fedora glibc)直接编译的 `c-sandbox`/`cpp-sandbox` 在镜像里跑不了(`ld-linux` vs `ld-musl` 不兼容);且 `build.sh` 的 cpp `-static` 会因 host 缺 `libstdc++-static`/`glibc-static` 直接失败。**正确做法:用 base-17 容器编译 c/cpp harness**(产物即 musl 二进制):
     ```
     docker run --rm -u "$(id -u):$(id -g)" \
       -v "$PWD/docker/sandbox/harness/c:/src:ro" \
       -v "$PWD/docker/sandbox/harness-staging/c:/out" \
       ulticode-sandbox:base-17 gcc -O2 -Wall -Wextra -o /out/c-sandbox /src/main.c
     # cpp 同理:g++ -std=c++17 -O2 -Wall -Wextra -static -o /out/cpp-sandbox \
     #   /src/main.cpp /src/json.cpp /src/serializer.cpp /src/solution_parser.cpp \
     #   && cp /src/*.hpp /src/*.cpp /out/
     ```
     java(class 字节码)/python(`.py` 源码)跨平台,host 编译无碍(主机 3.14 编译的 `.pyc` 在镜像 3.11 因 magic number 失效会回退 `.py`,正常)。
  2. **代理环境**:host 若配 HTTP 代理(`~/.docker/config.json` 的 `proxies` 段会注入所有 build/run 容器),bridge 模式容器内 `127.0.0.1` 指向容器自己 → 代理连不上 → apk 拉取失败。解法:`docker build --network=host` 让代理可达;若代理对 `dl-cdn.alpinelinux.org` 返 502(而对 `mirrors.aliyun.com`/`mirrors.tuna.tsinghua.edu.cn` 返 200),临时把 Dockerfile 的 apk 源换为 aliyun。
- `build.sh` 用**固定文件清单** copy:新增 harness 模块(如 `_case_runner.py`)必须同时加进 `build_<lang>()` 的 cp 清单 + .pyc 循环,否则镜像缺文件 → 每个用例 RE
- **Python 版本陷阱**:镜像 base(alpine 3.19)= Python **3.11.14**,类型注解**即时求值**;主机可能是 3.14(PEP 649 惰性求值),本地 `pytest` 可能"假通过"。改注解/preamble 后必须用 `docker run` 在镜像(3.11)里端到端验证:`docker run --rm -e SOLUTION_DIR=/job -v "$TMP":/job ulticode-sandbox:latest python3 /opt/harness/python/main.py /job/input.json`(用户代码文件名是小写 `solution.py`,harness `import solution` 大小写敏感)
- **Python preamble 契约**:用户代码**零 import**。`build_solution_preamble()` 预注入 `typing.__all__` + 纯计算标准库 + collections 高频符号 + `ListNode`/`TreeNode`。**绝不注入** `os`/`sys`/`subprocess`/`socket`/`shutil`/`ctypes`/`multiprocessing`(exit guard 只拦 `_exit`/`sys.exit`,放行这些会破坏沙箱隔离)
- 链表/树问题返回 `None`(空输入)会被 `normalize_return_value()` 规范化为 `[]`(LeetCode 约定),比较时不要当 `'null'` 处理

### 主题 Bootstrap 单例约束

`console/public/theme-bootstrap.js` 与 `management/public/theme-bootstrap.js` 是为消除 FOUC 引入的外置脚本,逻辑与 `shared/theme/src/applyThemeToDOM.ts` 一致。**禁止**在 `main.ts` 内联、组件 `onMounted` 等位置重新写一份 theme 初始化逻辑(会与 `shared/theme` 单例产生 hydration 不一致)。引入严格 CSP(无 `'unsafe-inline'`)时,需为 `<script src="/theme-bootstrap.js">` 加 nonce/hash 并同步更新 `index.html`。

---

## 运维速查

### Dev 账号与启动

- 一次性 dev 数据库登录 `admin` / `admin123`,由 dev-profile-only bootstrap runner 初始化;**生产环境禁用**
- **本项目当前未暴露 Spring Actuator**:不要用 `/actuator/health` 判就绪;改用已知公开 API + 两个前端根路径 + PM2 状态 + 容器健康检查
- 完整启动流程见 [AGENTS.md](./AGENTS.md) Development Startup 段;核心顺序:Docker 基础设施 healthy → `ulticode-init-db` → `ulticode-9001`

### PM2 Services

**PM2 管理后端 + 前端(9001/9002/9003 + arthas 全在 `ecosystem.config.cjs`)**

| Port | Name | Type |
|------|------|------|
| 9001 | ulticode-9001 | Spring Boot Backend |
| 9002 | ulticode-9002 | Console (Vite, `--host 127.0.0.1`) |
| 9003 | ulticode-9003 | Management (Vite, `--host 127.0.0.1`) |
| 8563 | ulticode-arthas | Arthas MCP Server (PM2 主, hook/cli 兜底, 三路互斥) |
| - | ulticode-init-db | 数据库迁移服务(一次性任务) |
| 28848 | (nacos container) | Nacos 控制台 `/nacos`(默认账号 nacos/nacos) |

**⚠️ Vite `--host 127.0.0.1` 不可省**:Vite v8 默认绑 IPv6 `[::1]`,`up.sh` 就绪检查用 `127.0.0.1` 探测会假阴性(exit 1)。ecosystem 的 9002/9003 显式 `--host 127.0.0.1` 绕过——**改 ecosystem 勿删这两个 app 定义**(c26f45889 曾误删致回归)。前端单跑调试用 `cd console && pnpm exec vite`(绑 ::1,用 `curl localhost` 验证)。

```bash
pm2 start ecosystem.config.cjs   # 首次
pm2 start all                    # 之后
pm2 stop all / pm2 restart all
pm2 logs / pm2 status / pm2 monit
pm2 save / pm2 resurrect
```

### Startup Order(重要)

1. Docker 基础设施(`ulticode-mysql/redis/nacos`)必须先 Up/Healthy,再 `pm2 start`
2. 容器都 Exited 时直接 `pm2 start ecosystem.config.cjs` → init-db 报"连接被拒绝" → 9001 反复崩溃 → 8563 永远空
3. 一键启动:`./scripts/dev/up.sh`(全量:基础设施 → Nacos → 迁移 → dev-admin → install → PM2)。参数化:`--quick`(改代码后热重启)、`--only <apps>`(如 `--only 9001`)、`--no-frontend`/`--frontend-only`、`--skip-infra`/`--skip-migrate`/`--skip-bootstrap`/`--skip-install`、`-h` 看帮助
4. 手动按序:`docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis nacos` → `pm2 restart ulticode-init-db` → `pm2 restart ulticode-9001`

### 故障诊断信号

- `pm2 list` 中 `ulticode-9001` 的 ↺(restart count)快速增长 + `lsof -ti :9001` 为空 → 基础设施未就绪
- `9001` 与 `8563` 共享 PID 是**预期**的(Arthas agent 跑在目标 JVM 内)
- `ulticode-init-db` 跑完进入 `stopped` 是**预期**的(one-shot Flyway 任务);校验成功标志:`pm2 logs ulticode-init-db --nostream | grep "BUILD SUCCESS"`
- 容器健康检查:`docker inspect --format='{{.State.Health.Status}}' ulticode-{mysql,nacos}`
- **冷启动 up.sh 预期停留**:`up.sh` 的 dev-admin bootstrap 步骤(`spring-boot:run --web-application-type=none`)因非 daemon 线程(Redisson netty/调度器)会卡 ~105s 才被 `timeout` 兜底收尾(日志 `Bootstrap JVM did not self-exit... continuing`),属**预期**,勿干预;真正异常信号 = 后台 up.sh 的 output 文件 mtime 长期停滞 + PM2 空 + 端口 FREE
- **pm2 env 缓存 → 认证失败**:`pm2 restart --update-env` 不重读 `ecosystem.config.cjs` 的 `envFromFile`(用 daemon 缓存)。改 `.env` 后若 `9001` 报 `RedisWrongPasswordException`/DB 认证错且 ↺ 飙升,用 `pm2 delete ulticode-9001 && pm2 start ecosystem.config.cjs --only ulticode-9001` 强制重读。查进程实际 env 用 `tr '\0' '\n' < /proc/$(pm2 pid ulticode-9001)/environ | grep <VAR>`(`pm2 env <id>` 显示 stale,不可信)

---

## 工作位置优先级(main 优先)

**默认在 main 分支上直接工作**,与全局 `~/.claude/CLAUDE.md` 偏好一致。优先级:

1. **main(默认首选)**:多文件 / 多 commit / 跨模块的非平凡改动也直接在 main 上做
2. **新建分支(次之)**:用户显式要求"建分支" / "切到 X 分支"时
3. **worktree(最末)**:仅当用户**显式**说"用 worktree" / "建 worktree" / "在隔离环境改" / "不要污染 main" 时才用

**触发 worktree / 切分支的唯一信号是用户的显式指令**;不因"改动规模大 / 跨模块 / 会话长 / 工作树有未提交改动"而主动切 worktree。若发现自己误开了 worktree 而用户未要求,应回到 main。

> Git/外部操作护栏、提交格式、危险操作批准要求等权威约定见 [AGENTS.md](./AGENTS.md) Git and External Actions 段。

---

## Key Conventions(项目特有,AGENTS.md 未覆盖)

- **Attribution**:Disabled globally via settings.json
- **Integration tests**:Suffix `*IT.java`,从 `./mvnw test` 排除;用 `./mvnw -Dtest='*IT' test` 或 `./scripts/dev/test.sh integration`(test.sh 支持 `quick`/`full` 模式,`quick` 跳过集成测试)
- **Backend ports**:App 9001;**Frontend ports**:Console 9002, Management 9003
- **Management DataTable i18n**:`DataTable.vue` uses `t(\`table.columnNames.${column.id}\`)`,`column.id` 匹配 API 字段名(camelCase)。`management/src/i18n/locales/*/modules/table.ts` 需同时定义 camelCase 和 snake_case keys
- **Backend DTO enums**:后端 DTO 字段仍用原始 `String`(前端用 TS enum),已知错配;新代码优先推进后端 enum 化
- **Frontend API patterns**:Management 用 typed API 函数封装;Console 用直接 `apiPost/apiGet`。新增 API 时为 management 定义 typed 函数
- **Frontend ghost types**:Management API 文件可能定义无后端 endpoint 的类型(`UserWarning` 等),按未来使用预留,endpoint 出现前视为 dead code
- **Cross-stack DTO alignment**:新增/修改 shared DTO/endpoint/enum 时,合并前审计双前端 + 后端的字段/类型/enum 对齐。不删 ghost 类型、不留 orphan endpoint — 见 `cross-stack-dto-granularity-alignment` skill
- **Analysis docs**:跨模块分析报告放 `wiki/`(如 `wiki/moderation-api-granularity-analysis.md`)

> Tech Stack、CI、Toolchain 硬约束、Verification Matrix 等通用约定见 [AGENTS.md](./AGENTS.md) 与 [README.md](./README.md)。

---

## 工具参考

- **RTK(Rust Token Killer)**:命令前缀 `rtk` 可节省 60-90% token。**完整命令参考**见 [wiki/.meta/rtk-reference.md](./wiki/.meta/rtk-reference.md)(Build/Test/Git/GitHub/pnpm/Files/Docker/Network 全分类速查 + 节省率表)。核心铁律:`rtk git add . && rtk git commit -m "msg"`(链式命令每段都要 `rtk` 前缀)。
- **MCP 配置(`.mcp.json`)**:HTTP 远程服务器用 `type: "http"`(官方写法;`streamableHttp` 是规范别名);项目级 `.mcp.json` 首次使用需在 `/mcp` 审批;Arthas MCP: `{"type":"http","url":"http://localhost:8563/mcp"}`。

<!-- headroom:learn:start -->
## Headroom Learned Patterns(本会话工作模式沉淀)

> 自动生成 + 人工压缩。每条是高频踩坑的一句话核心,详细背景见 git log / 历史会话。

- **PM2 cwd 锁定 main worktree**:`pm2 start/restart ulticode-9001` 跑的是主 worktree 的 `target/app.jar`,feature worktree 里构建的修复必须 `cp` 回主 worktree 再 restart,否则 curl 测的是 stale 代码。
- **Arthas STATELESS 已项目级 pin**:4.2.2 默认 STREAMABLE 致阻塞命令 30s 超时;fix 在 `infrastructure/arthas/arthas.properties`(`arthas.mcpProtocol=STATELESS`),改后需 `pm2 restart ulticode-9001` 重 attach。
- **Flyway checksum mismatch 恢复**:编辑已应用迁移 → 下次 migrate 报 checksum mismatch。修复用 `./scripts/dev/migrate.sh repair`(非 raw `flyway`,不在 PATH),再 `migrate`。
- **构建/测试工具用 ctx_execute**:`./mvnw`、`pnpm type-check/lint/test`、`curl`、`WebFetch` 走 `mcp__plugin_context-mode_context-mode__ctx_execute` 避免 Bash 重定向错误往返;`mvn spring-boot:run` 是长进程,用子进程并快速返回。
- **CodeGraph 非自动初始化**:任务不需要索引时直接 `Bash`+`grep`/`find`+`Read`;需要时用户须显式 `codegraph init`,不要重试空返回。
- **Grep/Glob 工具不可用**:用 `Bash` 的 `grep`/`rg`/`find`;多条件 `find` 用原生(`rtk find` 不支持 `-not`/`-exec`/`-or`)。
- **阻塞 Bash 模式**:`sleep N && <cmd>` (N≥5) 被 hook 拦截;用 `until <cond>; do sleep 2; done` 循环或一次性执行后下轮回查。
- **PRP 产物位置**:plans→`.claude/PRPs/plans/{name}.plan.md`,reports→`.claude/PRPs/reports/{name}.report.md`,reviews→`.claude/reviews/{name}-review.md`;该树 gitignored,需提交用 `git add -f` 或移到 `wiki/`。
- **Arthas MCP 生命周期由 hooks 管**(非 PM2):SessionStart/SessionEnd hooks 绑定;断连查 `scripts/start-arthas.sh` 和端口 8563,不要 `pm2 restart ulticode-arthas`(该 app 已不存在)。
- **项目启动工作流**:"启动这个项目" → `./scripts/dev/up.sh`(或 `--skip-install`);顺序:容器 healthy → ulticode-init-db → ulticode-9001。
<!-- headroom:learn:end -->
