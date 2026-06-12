# Sandbox (OJ 代码执行沙箱)

> 状态：active  
> 最后更新：2026-06-12  
> 维护者：submission 模块

## 概述

OJ 评测沙箱基于 Docker，每个用户提交代码在一个隔离容器内执行，由 Spring Boot `SandboxServiceImpl` 通过 `ProcessBuilder` 启动 `docker run`。**不允许外部网络**（`--network none`），**不允许新增 capability**（`--cap-drop ALL`），**只读 rootfs**（`--read-only`），**非 root user**（`--user 1000:1000`），**seccomp 自定义 profile**。

## 关键文件

| 文件 | 作用 |
|---|---|
| `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SandboxServiceImpl.java` | 沙箱执行入口；`executeBatch` (多用例) + `executeInSandbox` (单用例) |
| `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/CodeExecutionHelperImpl.java` | 多语言 wrapper 脚本生成 + 结果解析 |
| `backend-spring/src/main/java/com/ulticode/modules/submission/config/DockerSandboxConfig.java` | `@ConfigurationProperties` 绑定 `code-execution.sandbox.*` |
| `docker/sandbox/Dockerfile` | 沙箱镜像构建（基于 alpine） |
| `docker/sandbox/seccomp-profile.json` | 自定义 seccomp profile，**默认 action SCMP_ACT_ALLOW** + 显式 SCMP_ACT_ERRNO 拦截少量危险 syscall |
| `backend-spring/src/main/resources/application.yml` | 沙箱参数默认值 |

## 资源限制矩阵

### 全局默认（`application.yml` `code-execution.sandbox.*`）

| 参数 | 默认值 | 含义 |
|---|---|---|
| `enabled` | `true` | 总开关 |
| `image` | `ulticode-sandbox:latest` | Docker 镜像名 |
| `memory` | `256m` | cgroup memory limit |
| `cpus` | `1.0` | cgroup cpu shares |
| `timeout` | `10` (dev) / `30` (prod) | 单次沙箱 wall-time 秒数 |
| `pids-limit` | `128` | cgroup pids.max，容器内所有 PID 总数上限（含线程） |
| `seccomp-profile-path` | `docker/sandbox/seccomp-profile.json` | 自定义 seccomp JSON |

### Per-language override（`application.yml` `languages.<lang>`）

每种语言可以覆盖 `timeoutSeconds` / `memory` / `pidsLimit`（参考 `DockerSandboxConfig.LanguageLimit` record）。

## Fork-Failed Verdict 分类映射

`SandboxServiceImpl` 用两个静态方法识别 fork 失败（**不是用户代码 bug**）。2026-06-12 增加，区分 host/cgroup/seccomp 压力与用户程序异常。**2026-06-12 二次精修**：将 docker daemon 端 IOException 匹配从过度宽松的 `contains("pids")` 收紧为精确短语，避免配置告警误报。

### `isSandboxForkFailure(String output)` — stdout/stderr 匹配（沙箱内）

| 输出含关键字 | verdict | 说明 |
|---|---|---|
| `Cannot fork` | `Sandbox Error` | busybox sh fork 失败（PID cgroup 满、RLIMIT_NPROC） |
| `Resource temporarily unavailable` | `Sandbox Error` | glibc / dockerd EAGAIN |
| `fork: Cannot allocate memory` | `Sandbox Error` | 内核 fork 内存不足 |

### `isDockerDaemonForkFailure(String msg)` — IOException 匹配（docker daemon 端）

| msg 含关键字 | 处理 | 说明 |
|---|---|---|
| `Cannot fork` | throw `SANDBOX_ERROR` | docker daemon host-side fork 失败 |
| `fork: Cannot allocate memory` | throw `SANDBOX_ERROR` | 内存压力 |
| `pids-limit reached` | throw `SANDBOX_ERROR` | cgroup pids.max 触顶 |
| `cgroup pids limit` | throw `SANDBOX_ERROR` | cgroup 控制器限制 |
| `RLIMIT_NPROC` | throw `SANDBOX_ERROR` | kernel NPROC 限制 |

**收紧理由**:docker daemon 消息可能含 "pids" 但**与 fork 无关**（如 `WARNING: pids-limit not set`、`pids controller disabled`）。早期 `contains("pids")` 会导致运维被误导。

### 常量与日志截断

- `SANDBOX_FORK_FAILURE_VERDICT = "Sandbox Error"`（verdict 字符串常量）
- `SANDBOX_FORK_FAILURE_DETAIL_PREFIX = "Sandbox fork failed (likely PID/cgroup/seccomp pressure): "`（detail 前缀常量）
- `MAX_LOG_DETAIL_BYTES = 1024`（`truncateForLog(String)` 截断 + 长度标记，避免大字符串污染日志聚合器）

### i18n 双端对齐

| 端 | 文件 | key | en-US | zh-CN |
|---|---|---|---|---|
| console | `src/i18n/locales/{en-US,zh-CN}/submission.ts` | `status.sandboxError` | `Sandbox Error` | `沙箱错误` |
| management | `src/i18n/locales/{en-US,zh-CN}/modules/submissions.ts` | `statusLabels.SANDBOX_ERROR` | `Sandbox Error` | `沙箱错误` |

**关键决策**：verdict 字符串仍用 `"Sandbox Error"`（与 `helper.buildCaseResult()` 调用一致），前端通过 i18n key 翻译；不在 Java 端做 locale-specific verdict 字符串。

## 沙箱 wrapper 路径速查

```
submissions/run
  → ProblemSubmissionController.runCode
  → CodeExecutionService.execute
  → SandboxServiceImpl.executeBatch          # 多用例：单进程 wrapper
  → docker run --rm -i --pids-limit 128 ... sh -c 'python3 -c "<wrapper>"'
submissions
  → SubmissionServiceImpl.submit             # 异步判题，最终同一沙箱路径
```

batch wrapper 是 **single-process** Python 跑完所有用例（`totalCases` 不影响 PID 消耗）。

## 排障信号

| 信号 | 含义 | 排查方向 |
|---|---|---|
| pm2 `ulticode-9001` ↺ 增长 + 9001 端口消失 | 基础设施未就绪 | 见 `CLAUDE.md` "Startup Order" |
| 沙箱 verdict 全 `Sandbox Error` + detail `Cannot fork` | host/cgroup 压力 | 检查 `kernel.pid_max` / 容器 `pids.current` / dockerd cgroup |
| 沙箱 verdict 全 `Runtime Error` + detail `Unable to find image` | 镜像缺失 | `docker build -t ulticode-sandbox:latest -f docker/sandbox/Dockerfile docker/sandbox/` |
| 沙箱 verdict 全 `Time Limit Exceeded` | 用户代码超时 | 调整 `timeout` / per-language `timeoutSeconds` |

## 已知限制

1. **pids-limit=128 实测充分**。2026-06-12 诊断结论：用生产参数 `--pids-limit=128 --memory=256m --cpus=1.0 --user=1000:1000` 跑 PE-007 (合并 K 个有序链表) wrapper，**正常返回 JSON**。`/sys/fs/cgroup/pids.current` ≈ 4-6，远低于 128 上限。**早期"pids-limit 不足"的假设被证伪**。但用户代码若启动大量线程或多进程库（numpy/pandas import 期间）仍可能触发 cgroup `cgroup.procs` 写入失败，需持续监控。
2. **seccomp profile 只拦截少数危险 syscall**，其余放行（`defaultAction: SCMP_ACT_ALLOW`）。fork/vfork/clone/clone3 均未被显式 block。
3. **Java/C/C++ 编译阶段会多 fork**（javac/gcc），未来若用户提交复杂 C++ 模板代码可能踩 pids-limit；建议 per-language 调整（参见 plan `oj-sandbox-cannot-fork-diagnose.plan.md` Task 6.1，作为 follow-up）。
4. **没有 Spring Actuator 暴露**，无法通过 `/actuator/health` 判就绪；用 `lsof -ti :9001` + 已知公开 API 综合判断。

## 历史事件

| 日期 | 事件 | 详情 |
|---|---|---|
| 2026-06-12 | PE-007 提交报 `sh: 0: Cannot fork` | 用户提交合并 K 个有序链表，verdict 误标 `Runtime Error`。诊断结论：当前 sandbox 镜像在生产参数下可正常执行 wrapper（Task 3 复现失败），H1（pids-limit=128 不足）证伪。verdict 分类改进作为独立可观测性改进落地。完整诊断见 `.claude/PRPs/reports/oj-sandbox-cannot-fork-diagnose-report.md`（待生成） |
