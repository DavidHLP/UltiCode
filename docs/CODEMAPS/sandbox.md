# Sandbox (OJ 代码执行沙箱)

> 状态：active  
> 最后更新：2026-06-13（M2a: Hexagonal refactor）  
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
| 2026-06-13 | **M2a: Hexagonal refactor (ADR-002)** | `SandboxServiceImpl` 拆为 `SandboxExecutor` port + `LanguageProfile` strategy + 5 个 LanguageProfile + `SandboxExecutorImpl` (生产) + `InMemorySandboxAdapter` (测试)。`switch (language)` 拆点归零；`ProcessBuilder` 只在 `SandboxExecutorImpl` 内；fork-failure 静态方法保留在 executor；DTO↔port 翻译集中于 `SandboxExecutorImpl.toRunTestCase` / `toPortResult` 与 `CodeExecutionService.toSandboxTestCase` / `toDtoCaseResult`。详见下文"M2a Hexagonal Refactor"章节 |

## M2a Hexagonal Refactor（ADR-002）

M2a 把 `SandboxServiceImpl`（476 行，混合语言分发 + Docker CLI + 失败分类）拆为 Hexagonal 架构，对应 [ADR-002](../adr/ADR-002-sandbox-hexagonal.md)。

### 新 package 布局

```
com.ulticode.modules.submission.sandbox/
├── SandboxExecutor              # port interface (run / runBatch)
├── SandboxJob                   # record: runId, userId, submissionId, generation, lang, code, timeoutSeconds(per-case 上限), memoryMb (P2-1: CodeExecutionService 按 problemId 读 problem.time_limit/memory_limit 覆盖,NULL fallback 全局默认)
├── TestCase                     # port 内部 record (含 Input 子 record)
├── RunCaseResult                # record: status(SubmissionStatus) + elapsedMs + memoryBytes + elapsedUs + cpuMs + detail + score + output/expectedOutput/inputs (ADR-002 §8: elapsedUs/cpuMs 为精确测量)
├── BatchRunResult               # record: List<RunCaseResult> (1:1 input contract)
├── SandboxLimits                # record: per-language effective limits
├── UnsupportedLanguageException # 业务异常
├── LanguageProfile              # Strategy interface
├── executor/
│   └── SandboxExecutorImpl      # 默认 docker 实现 (@ConditionalOnProperty matchIfMissing=true)
├── adapter/
│   └── InMemorySandboxAdapter   # 测试桩 (@ConditionalOnProperty havingValue=inmemory)
└── profile/
    ├── JavaLanguageProfile           # 完整 (Phase 5b 行为)
    ├── PythonLanguageProfile         # 完整 (Phase 5b 行为)
    ├── JavaScriptLanguageProfile     # stub (@ConditionalOnProperty matchIfMissing=false)
    ├── CLanguageProfile              # stub
    └── CppLanguageProfile            # stub
```

### 拆点映射（ADR-002 §1.1 → M2a 落地）

| 旧位置（`SandboxServiceImpl`） | 新位置 | 备注 |
|---|---|---|
| `switch (language)` line 201（文件名） | `LanguageProfile.materializeWorkspace` | 5 个 profile 各自写 `Solution.java` / `solution.py` 等 |
| `switch (language)` line 270（docker 命令） | `LanguageProfile.dockerCommand` | 5 个 profile 各自返回 `[image, sh, -c, dispatchShell]` |
| `isJavaCompileFailure` line 133 | `JavaLanguageProfile.isCompileFailure` | 5 个 profile 各自判定（Python 无 compile 步返 false） |
| `isSandboxForkFailure` / `isDockerDaemonForkFailure` line 72/84 | **`SandboxExecutorImpl` 静态方法**（ADR-002 §2.5） | 跨语言基础设施失败，留在 executor |
| `ProcessBuilder` line 320 + line 281 docker run 拼装 | `SandboxExecutorImpl.runDProcess` + `buildDockerCommand` + `commonSecurityArgs` | ProcessBuilder 只在 `SandboxExecutorImpl` 内 |
| `executeInSandbox` / `executeBatch` 接口方法 | `SandboxExecutor.run` / `runBatch` | 入参改为 `SandboxJob` + `TestCase` (port 自有) |
| 内部 `RunResultDTO.RunCaseResult` 返回 | `RunCaseResult` record（port） | 含 `SubmissionStatus` enum（ADR-001），wire 字符串转换在 DTO 边界 |

### 资源测量与判定契约（ADR-002 §8，2026-06-16 全量修复 P0/P1/P2）

envelope per-case `results[]` 字段（向后兼容，旧 harness 不发则默认 0）：

| 字段 | 类型 | 语义 |
|------|------|------|
| `elapsed_ms` | long | wall-clock 毫秒（legacy，ms 截断） |
| `elapsed_us` | long | 精确 wall-clock 微秒（ADR-002 §8，优先用于展示，修复快题 "0ms"） |
| `cpu_ms` | long | user+sys CPU 毫秒（跨语言公平展示） |
| `peak_memory_bytes` | long | 该 case 真实峰值（Java `MemoryPoolMXBean` reset/getPeakUsage · Python `ru_maxrss` · C++ child `getrusage` 经 pipe header 回传） |
| `memory_limit_bytes`（input.json） | long | 后端下发每用例内存上限，>0 时 harness 自判 MLE |

判定逻辑：
- **TLE**：harness 软超时（`per_case_timeout_ms` = 题目每用例限制）→ `"Time Limit Exceeded"`；docker 整批硬超时 = `perCase × caseCount + compileBudget(35s for C/C++) + grace`，cap 180s（P0-1：旧公式 `timeoutSeconds+1` 不含 case 数导致多用例整批误杀）
- **MLE** 三层（P0-2）：(A) harness 自判 peak>limit → `"Memory Limit Exceeded"`；(B) `SandboxExecutorImpl.toPortResult` 兜底比对（向后兼容旧 harness）；(C) docker exit 137 + 空 stdout → `SANDBOX_ERROR`（不再伪装成 RE）
- **题目级限制**（P2-1）：`problems.time_limit` / `memory_limit`（Flyway `V20260616120000`），`CodeExecutionService.resolveTimeoutSeconds/Mb` 按 problemId 读取，NULL fallback 全局默认

详见 [ADR-002 §8](../adr/ADR-002-sandbox-hexagonal.md)。

### 新 executor 的 docker run 拼装（顺序固定）

```
docker run --rm -i
  <commonSecurityArgs>          # --network none / --cap-drop ALL / --read-only /
                                #   --user 1000:1000 / --security-opt no-new-privileges /
                                #   --security-opt seccomp=...
  --memory <effective>          # 来自 languages.<lang>.memory 或 sandbox.memory 默认
  --cpus <cpus>                 # 来自 sandbox.cpus
  --pids-limit <N>              # 来自 sandbox.pidsLimit
  --ulimit nofile=128:128
  --tmpfs /tmp:rw,exec,size=64m
  --volume <workspace>:/job:ro
  --volume <seccompDir>:/seccomp-profile:ro
  <image> sh -c <dispatchShell>  # 来自 profile.dockerCommand
```

`commonSecurityArgs()` 在 `SandboxExecutorImpl` 内部统一拼接，**profile 不能 override**（ADR-002 §3.3 风险表）。

### LanguageProfile 集合注入 + fail-fast

```java
public SandboxExecutorImpl(List<LanguageProfile> all, DockerSandboxConfig config, CodeExecutionHelper helper) {
    this.profiles = all.stream().collect(toUnmodifiableMap(
        LanguageProfile::languageId,
        p -> p,
        (a, b) -> { throw new IllegalStateException("Duplicate LanguageProfile: " + a.languageId()); }
    ));
    ...
}
```

启动期重复 `languageId` → `IllegalStateException` 立即崩，**避免运行期 fallback 暗坑**（ADR-002 §2.2 强调）。

### DTO ↔ Port 翻译（边界处集中）

| 翻译点 | 方向 | 方法 |
|---|---|---|
| `CodeExecutionService.toSandboxTestCase` | DTO → port | 把 `RunSubmissionDTO.RunTestCase` 翻译成 `sandbox.TestCase` |
| `CodeExecutionService.toDtoCaseResult` | port → DTO | 把 `sandbox.RunCaseResult` 翻译回 `RunResultDTO.RunCaseResult`（含 `SubmissionStatusCodec.toWire` 字符串化） |
| `SandboxExecutorImpl.toRunTestCase` | port → DTO | 把 `sandbox.TestCase` 翻译成 `RunSubmissionDTO.RunTestCase`（喂 `CodeExecutionHelper.buildDInputsJson`） |
| `SandboxExecutorImpl.toPortResult` | DTO → port | 把 `helper.parseDEnvelope` 返的 DTO 翻译成 `sandbox.RunCaseResult`（含 `SubmissionStatusCodec.fromWire` enum 化） |

`SubmissionStatusCodec`（ADR-001）是 enum ↔ wire 字符串唯一边界；sandbox 内部 0 处出现 `"Wrong Answer"` / `"Compile Error"` 等 wire 字符串。

### 激活切换

`application.yml`（或环境变量）：

```yaml
sandbox:
  executor: docker   # 默认；显式锁定以防 prod 误启 InMemory
  profile:
    javascript: { enabled: false }   # M2a stub 默认关
    c:         { enabled: false }   # M2a stub 默认关
    cpp:       { enabled: false }   # M2a stub 默认关
    # java + python 无开关（默认 enabled）
```

### ADR-002 §4 Validation 落地验证

| 验证项 | 命令 / 产物 | 状态 |
|---|---|---|
| `switch (language)` 在 `submission/` 子树为零 | `grep -rn 'switch\s*(\s*language\b' src/main/java/com/ulticode/modules/submission/` | ✅ 0 处 |
| `ProcessBuilder` 只在 `DockerSandboxAdapter` 内 | `grep -rln ProcessBuilder src/main/java/com/ulticode/modules/submission/` → 仅 `SandboxExecutorImpl.java` | ✅ 1 处 |
| SandboxExecutor 单元测试用 InMemoryAdapter 100% 通过 | `mvn test -Dtest=InMemorySandboxAdapterTest` 19/19 ✓ | ✅ |
| 5 个语言 profile 各自单测 | Java(5) + Python(5) + JavaScript(2) + C(3) + Cpp(3) = 18 ✓ | ✅ |
| Testcontainers IT 跑核心矩阵 | `SandboxForkE2EIT` / `SandboxNamespaceIsolationIT` pre-existing, **M2a 沿用**；未重写到新 port（保留为 follow-up） | ⏳ follow-up |
| `docs/CODEMAPS/sandbox.md` 新增 "Port / Adapter / LanguageProfile" 章节 | 本节 | ✅ |

### 后续 follow-up（M2b 候选）

- 把 `SandboxForkE2EIT` / `SandboxNamespaceIsolationIT` 迁到 `sandbox.executor=inmemory` 跑更快（不用 docker daemon）
- C/C++/JS 的 D-form harness 落地时，只需打开 `sandbox.profile.<lang>.enabled=true` + 替换对应 profile 的 `dockerCommand` + 写单测（**不动 `SandboxExecutorImpl` / `SandboxExecutor` / `CodeExecutionService`**，开闭原则 #11）
- ADR-003 generation fence 落地后，`SandboxJob.submissionGeneration` 字段已经从"0L for /run" 升级到 "读 `submissions.generation`"，本 refactor 已为此预留字段
