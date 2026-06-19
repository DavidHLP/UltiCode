---
title: Arthas MCP 实战使用手册
tags: [ops, diagnostics, arthas, reference]
status: living
updated: 2026-06-18
owner: backend
---

# Arthas MCP 实战使用手册 (watch / trace / stack)

> 本文档基于 2026-06-18 真实测试(arthas 4.3.0 / STATELESS 协议 / Spring Boot 3.2.5)沉淀,目的是让任何人在 Claude Code 会话中**不再踩坑**地用上 watch/trace/stack。

---

## TL;DR — 30 秒上手

```bash
# 1. 后台触发 (run_in_background=true 保持持续打)
#    同时:
# 2. MCP 调用增强命令, 必带 -n 1 -t N (N ≤ 25)
```

**两个并发工具调用**:
- **Bash** `run_in_background: true` —— 持续 `curl` 触发目标端点
- **mcp__arthas-mcp__trace/watch/stack** —— 配 `numberOfInvocations: 1` + `timeout: 12` (秒)

**协议前提(已锁死,无需操作)**: `infrastructure/arthas/arthas.properties` 的 `arthas.mcpProtocol=STATELESS`,wrapper attach 前自动 sync 到 `~/.arthas/lib/<ver>/arthas/arthas.properties`。

---

## 命令语义对比(实际测试结果)

| 命令 | 用途 | 触发要求 | 实测耗时 | 返回内容 |
|------|------|----------|----------|----------|
| `stack <cls> <m>` | 谁调用了这个方法 | 需触发,否则阻塞 | ~1.5s(触发后)| 完整调用栈 ~110 帧 |
| `watch <cls> <m> {expr}` | 观察入参/返回值/异常 | 需触发 | ~1.5s(触发后)| params/returnObj/throwExp/target |
| `trace <cls> <m>` | 方法内部调用路径 + 耗时 | 需触发 | ~1.5s(触发后)| 树状调用图,ns 级耗时 |
| `monitor <cls> <m>` | 统计调用次数/RT/失败率 | 需触发 | 长跑(循环) | 每 N 秒输出统计 |
| `tt -t <cls> <m>` | 时空隧道,记录每次调用 | 需触发 | 阻塞,记录 N 次 | 可重放 |

**所有增强命令都需要触发**。不触发 = 阻塞到 timeout 才会返回。`version/jvm/sm/sc/ognl/getstatic/memory/thread` 是非阻塞简单命令,毫秒级返回。

---

## watch / trace / stack 真实调用示例

### 1. stack — 看谁调用了 `AuthController.login`

```python
mcp__arthas-mcp__stack(
  classPattern="com.ulticode.modules.auth.controller.AuthController",
  methodPattern="login",
  numberOfExecutions=1,  # 触发 1 次就返回
  timeout=12,            # 12s 内必须有人触发
)
```

**配套后台触发**:
```bash
for i in $(seq 1 30); do
  curl -s -o /dev/null -X POST http://localhost:9001/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}'
  sleep 0.3
done
```

**实测返回**: 触发时间 `2026-06-18 10:08:37`,线程 `http-nio-9001-exec-9`,调用链 `Tomcat → Spring Security FilterChain → DispatcherServlet → RequestMappingHandlerAdapter → AuthController.login:71`。

### 2. watch — 看入参 / 返回 / 异常

```python
mcp__arthas-mcp__watch(
  classPattern="com.ulticode.modules.auth.controller.AuthController",
  methodPattern="login",
  express="{params,returnObj,throwExp,target}",  # OGNL 表达式
  numberOfExecutions=1,
  timeout=18,
  expandLevel=2,           # 控制对象展开深度,过大易 OOM
  # beforeMethod=true,    # 方法前
  # successOnly=true,     # 只看正常返回
  # exceptionOnly=true,   # 只看异常
)
```

**OGNL 表达式**:
| 变量 | 含义 | 示例 |
|------|------|------|
| `params` | 入参数组 | `params[0]` 第一个参数 |
| `returnObj` | 返回值 | 任意 OGNL 路径 |
| `throwExp` | 异常对象 | `throwExp.message` |
| `target` | this(代理对象) | `target.authService` |
| `cost` | 方法耗时 (ms) | 由 arthas 自动注入 |

**实测返回** (RateLimit 拦截的场景):
```
accessPoint: AtExceptionExit
cost: 1.906494 ms
params[0]: LoginDTO(username=admin, password=admin123)
returnObj: null
throwExp: BusinessException: Rate limit exceeded. Please try again in 60 seconds.
target: AuthController$$SpringCGLIB$$0 (CGLIB 代理)
```

### 3. trace — 看内部调用耗时分布

```python
mcp__arthas-mcp__trace(
  classPattern="com.ulticode.modules.auth.controller.AuthController",
  methodPattern="register",
  numberOfExecutions=1,
  timeout=12,
  # condition="params[0].username.contains('admin')",  # OGNL 过滤
)
```

**实测返回** (`POST /auth/register` 一次):
```
nodeCount: 5
触发线程: http-nio-9001-exec-9
触发时间: 2026-06-18 10:09:58.282

调用树 (ns):
  AuthController$$SpringCGLIB$$0.register       125,204,999  (总耗时)
    └─ CglibAopProxy.intercept                    125,041,637
        └─ AuthController.register:71             122,719,010
            ├─ AuthService.register:86            122,623,668  ← 主要耗时(密码 hash/DB)
            └─ Result.success:87                      13,556  ← 13 μs
```

直接定位出性能瓶颈在 `AuthService.register:86` (122.6 ms,大概率 bcrypt + DB insert)。

---

## 超时根因(为什么之前总超时)

### 协议层(已修,锁死 STATELESS)

**问题**: arthas 4.2.2+ 默认 STREAMABLE 协议强制要求 `mcp-session-id` header,每次 MCP 工具调用立即返回 4.4KB "Session ID required" 错误,**看起来像持续超时**,实际是协议握手失败。

**修复**: `infrastructure/arthas/arthas.properties:54` 锁死 `arthas.mcpProtocol=STATELESS`。
- `scripts/start-arthas.sh` 的 `sync_arthas_properties()` 在每次 attach 前 diff 同步到 `~/.arthas/lib/<ver>/arthas/arthas.properties`(arthas-agent 真正读的位置,**不是** `~/.arthas/arthas.properties`)
- 改完要 `pm2 restart ulticode-9001` 触发重 attach
- 升级 arthas 后必须 diff 配置文件,新机器不会回退协议

**当前状态**: ✅ 已生效(实测 `version` 200ms 返回,无 session 错)

### 命令语义层(常见踩坑, 100% 必现)

**问题**: `monitor/trace/watch/stack/tt` 默认**阻塞等触发**,如果不配 `-n N` 限制次数,且没人在 timeout 内触发:
- arthas 端等满 timeout 才返回
- 期间 MCP 客户端再包一层 → 30s timeout
- **症状**: 工具调用挂着 30s,失败信息一片空白

**修复**: **永远**带 `-n 1`(或 ≤ 5),`timeout` 必传且 ≤ 25:
```python
numberOfExecutions=1,  # ← 关键: 触发 1 次就返回
timeout=12,            # ← 关键: 12s 内必须有人触发
```

### MCP 客户端层(已修,默认 30s)

**问题**: Claude Code MCP 客户端默认 timeout = 30s。
- arthas STATELESS 协议下, `timeout` 参数控制**服务端**等多久
- 即使 arthas 端 -t 12,MCP 客户端最坏等 30s 才超时
- **解决**: 让 arthas 端 timeout < MCP 客户端 timeout,优先返回

### 触发频率 vs 后端限流

**问题**: 后端 `@RateLimit` 是 60s 5 次窗口,调试时连续触发 login 会被 429 拦截,进不到 controller → 增强命令永远触发不到。

**修复**:
- 选没限流的端点(register、forgotPassword、resetPassword)
- 或者**间隔 ≥ 13s** 触发
- 或者**用 GET 端点**(很多 GET 不限流)

---

## 可用工作流(已成)

```
┌─────────────────────────────────────────────────────────────┐
│  工具调用 1: Bash (run_in_background=true)                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  for i in 1..N; do                                  │    │
│  │    curl ...  # 持续触发目标方法                     │    │
│  │    sleep 0.3                                        │    │
│  │  done                                               │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                          ↕ 并发
┌─────────────────────────────────────────────────────────────┐
│  工具调用 2: mcp__arthas-mcp__trace/watch/stack             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  classPattern=...                                   │    │
│  │  methodPattern=...                                  │    │
│  │  numberOfExecutions=1   ← 必带                      │    │
│  │  timeout=12            ← 必带, ≤ 25                 │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                          ↓
              触发发生时 arthas 端立刻返回
              工具调用耗时 = 触发时间 + 1~2s
```

**黄金法则**:
1. 永远带 `-n 1`(或 ≤ 5)
2. `timeout` 永远 ≤ 25(MCP 客户端 30s 上限)
3. 后台 bash 持续触发,间隔 ≤ `timeout`/2
4. 选没限流的端点(register/GET 优先)
5. 一个工具调用拿结果,**别**串行重复发

---

## 降级路径(当 MCP 持续 30s timeout 时)

按 `CLAUDE.md` 的降级顺序:

1. **首选**: `pm2 logs ulticode-9001 --nostream --lines 200`(同步拉最近 200 行)
2. **次选**: `pm2 logs ulticode-9001 --nostream --lines 200 --raw`
3. **再次**: `scripts/arthas-cli.sh` 交互式 telnet(无 30s 限制)
4. **回退**: `./mvnw -Dtest='*IT' test -B` 跑集成测试做对照
5. **最后**: `mcp__plugin_context-mode_context-mode__ctx_execute` 跑 java 反射/grep 类检查

---

## 常用 OGNL 速查

```python
# 查类字段
'@com.ulticode.common.aspect.RateLimitAspect@DEFAULT_LIMIT'

# 查 spring bean
'@org.springframework.context.support.ClassPathXmlApplicationContext@applicationContext'

# 看异常 message
'throwExp.message'

# 看目标对象
'target.authService.class.name'

# 入参过滤
'params[0].username'              # 第一个参数的用户名
'params.length > 1'               # 过滤多参方法
```

---

## 测试验证(本会话已通过)

- ✅ `stack` 触发后返回 110 帧完整调用栈(2026-06-18 10:08:37)
- ✅ `watch` 触发后返回 `LoginDTO` 字段 + `BusinessException` 全文(2026-06-18 10:09:42)
- ✅ `trace` 触发后返回 5 节点调用树,定位 `AuthService.register:86` 122.6ms 瓶颈(2026-06-18 10:09:58)
- ✅ `jvm` 200ms 返回
- ✅ `version` 200ms 返回
- ✅ `sc/sm` 300ms 返回

所有命令**没有**触发 30s MCP timeout。

---

## See also

- [`../../README.md`](../../README.md) — 仓库总入口
- [`../RUNBOOK.md §4.3`](../RUNBOOK.md#43-arthas-mcp-returns-session-id-required-for-every-command) — Arthas MCP "Session ID required" 故障排除
- [`../../CLAUDE.md` §运行时调试 (Arthas)](../../CLAUDE.md) — 协议锁死 STATELESS 与降级路径权威说明（仓库根）
- [`../../infrastructure/arthas/arthas.properties`](../../infrastructure/arthas/arthas.properties) — `arthas.mcpProtocol=STATELESS` 配置源
- 同目录其他 ops 文档：见 [`./README.md`](./README.md)
