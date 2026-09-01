# ADR-002: Sandbox Hexagonal — Port + LanguageProfile Strategy + 双 Adapter

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Accepted** (2026-06-13) + §7.7 Post-Hardening 实战修复 (2026-06-14) |
| **关闭 Milestone** | M2a (`ea6031466`..`348525e63` round-1 + `6b234771c`..`361233470` round-2) + §7.7 facade mapping 修复 |
| **日期 (Date)** | 2026-06-13 (ship), 2026-06-14 (post-hardening fix) |
| **作者 (Author)** | DavidHLP |
| **解决的 Finding** | (原 §2.1 中**未**被 Codex 否决的部分) |
| **依赖 ADR** | [ADR-001](./ADR-001-verdict-status-codec.md) (RunResult 含 SubmissionStatus 字段) |
| **关联代码** | `backend-spring/src/main/java/com/ulticode/modules/submission/sandbox/{SandboxExecutor,SandboxJob,TestCase,RunCaseResult,LanguageProfile,SandboxExecutorImpl,InMemorySandboxAdapter,profile/*}.java`,`CodeExecutionService.java`,`DockerSandboxConfig.java` |

---

## 1. Context

### 1.1 现状耦合点

`SandboxServiceImpl` 同时承担**三件事**, 违反单一原则 (项目规约 `.claude/rules/backend/07-java-design.md` #8):

1. **语言分发** — 两处 `switch (language)` (line 201, 270) 决定 Docker 命令构造
2. **Docker CLI 直调** — 直接用 `ProcessBuilder` 拉 `docker run`, 单测必须起 daemon
3. **Sandbox 失败分类** — `isSandboxForkFailure(...)` / `isDockerDaemonForkFailure(...)` 与 verdict 字符串硬编码绑死

后果:

- 单测覆盖率低 (一定要 docker)
- 加 Rust/Go 语言需改 SandboxServiceImpl + LanguageProfile + JudgeWorker 至少 3 文件 (违反开闭原则 #11)
- 未来若换 firecracker / nsjail / 远程判机, SubmissionService、JudgeWorker 都要回归测试

### 1.2 已知边界 (本 ADR 不动)

- Verdict 字符串契约 → 见 [ADR-001](./ADR-001-verdict-status-codec.md)
- 提交入队幂等性 / 状态机 → 见 [ADR-003](./ADR-003-queue-outbox-fencing.md)
- Sandbox 安全策略 (seccomp / cap-drop / read-only / pids-limit) → 见 `docs/CODEMAPS/sandbox.md` , 本 ADR 全量保留

## 2. Decision

### 2.1 Port

```java
public interface SandboxExecutor {
    RunCaseResult run(SandboxJob job, TestCase testCase);
    BatchRunResult runBatch(SandboxJob job, List<TestCase> cases);
}

public record SandboxJob(
    String runId,
    String userId,
    String submissionId,                // 关联 ADR-003 generation fence
    long submissionGeneration,
    String languageId,                  // "java" / "python" / ...
    String code,
    int timeoutSeconds,
    int memoryMb
) {}

public record RunCaseResult(
    SubmissionStatus status,            // ADR-001 enum, 非字符串
    long elapsedMs,
    long memoryBytes,
    String detail,
    double score
) {}
```

### 2.2 Strategy: LanguageProfile (Spring `List<>` 集合注入, fail-fast)

```java
public interface LanguageProfile {
    String languageId();                                       // "java" / "python" / ...
    List<String> dockerCommand(SandboxJob job, Path workspace);
    Path materializeWorkspace(Path tempDir, String code);
    boolean isCompileFailure(String stdout);
    SandboxLimits effectiveLimits(SandboxJob job);             // per-language override
}

@Service
public class SandboxExecutorImpl implements SandboxExecutor {
    private final Map<String, LanguageProfile> profiles;
    private final DockerSandboxConfig config;

    public SandboxExecutorImpl(List<LanguageProfile> all, DockerSandboxConfig config) {
        this.config = config;
        this.profiles = all.stream().collect(toUnmodifiableMap(
            LanguageProfile::languageId,
            p -> p,
            (a, b) -> { throw new IllegalStateException("Duplicate LanguageProfile: " + a.languageId()); }
        ));
    }

    @Override
    public RunCaseResult run(SandboxJob job, TestCase tc) {
        LanguageProfile profile = Optional.ofNullable(profiles.get(job.languageId()))
            .orElseThrow(() -> new UnsupportedLanguageException(job.languageId()));
        // ... materialize + buildDockerCommand + runDProcess + parse ...
    }
}
```

### 2.3 Adapter 矩阵 (本次只做 2 个)

> 命名说明: Port 是 `SandboxExecutor`(§2.1)。docker 侧实现沿用既有命名 `SandboxExecutorImpl`(与 §2.2 伪代码一致), 不是独立的 `DockerSandboxAdapter`。`DockerSandboxConfig` 是 Spring config(注册 bean), 不是 Adapter, 不计入下表。下表的"加新沙箱"行说明为何 docker 实现复用 Executor 命名而非新起 Adapter。

| Adapter / Executor | 用途 | 本次落地 |
|---|---|---|
| `SandboxExecutorImpl` | 生产 (docker 默认实现) — 现 `SandboxServiceImpl` 重构迁入, 保留全部安全策略; 由 `@ConditionalOnProperty(name="sandbox.executor", havingValue="docker")` 激活; `ProcessBuilder` 拉容器在此类内 | ✅ |
| `InMemorySandboxAdapter` | 测试 — 接收 `SandboxJob` 返回预设 `RunCaseResult` (可按 `job.code` 包含关键字路由到不同 verdict) | ✅ |
| `RemoteJudgeSandboxAdapter` | 调外部判机 (HTTP/gRPC) | ❌ 不实现, 不预留接口 (YAGNI) |
| `FirecrackerSandboxAdapter` / `gVisorSandboxAdapter` | 更轻隔离 | ❌ 不实现 |

加新语言: 只新增 `RustLanguageProfile implements LanguageProfile` , 不改 `SandboxExecutor` / `JudgeWorker` (开闭原则 #11) 。

加新沙箱: 新增 `class XxxSandboxAdapter implements SandboxExecutor` , 通过 `@ConditionalOnProperty(name="sandbox.executor", havingValue="xxx")` 切换 (依赖倒置 #10) 。docker 默认实现复用 `SandboxExecutorImpl` 命名, 没有为对称性新起 `DockerSandboxAdapter`(实现已工作, 改名只增成本不增价值)。

### 2.4 Verdict 解析下沉到 LanguageProfile

原 `SandboxServiceImpl.isJavaCompileFailure(stdout)` 这种 java-specific 检测**移到** `JavaLanguageProfile.isCompileFailure(stdout)` 。Sandbox executor 只负责"跑容器 + 收 stdout/exitCode", verdict 分类完全由 profile 自报。

### 2.5 Sandbox-level 失败 (fork failure / daemon 压力) 分类**留在** Executor

理由: 这些是**跨语言**的基础设施失败, 不属于 LanguageProfile 关注点。继续用现有 `isSandboxForkFailure` / `isDockerDaemonForkFailure` 逻辑, 但返回 `SubmissionStatus.SANDBOX_ERROR` (enum 而非字符串, 见 ADR-001) 。

## 3. Consequences

### 3.1 Positive

- **单测无需 Docker daemon** (InMemoryAdapter), CI 提速 + 离线开发可写单测
- 加新语言只动 1 个文件 (新 LanguageProfile bean), 编译期保证 (重复 languageId 启动崩)
- Verdict 字符串契约由 ADR-001 单点管理, sandbox 不再持有
- 现有 `docs/CODEMAPS/sandbox.md` 的安全矩阵零损失 (`SandboxExecutorImpl` docker 实现全量继承)

### 3.2 Negative

- 净增 ~600 LOC (port 接口 + 5 个 LanguageProfile + InMemoryAdapter + 单测桩)
- LanguageProfile 接口设计若一开始没覆盖未来需求 (例如 multi-file 代码、stdin 注入特殊处理) , 后续 breaking change 会涉及所有 profile 实现 — 缓解: 接口字段保守扩展, 加 `Map<String,Object> extraHints` 兜底
- `@ConditionalOnProperty(sandbox.executor=...)` 配置错可能让生产跑成 InMemory — 缓解: prod profile 强制 `sandbox.executor=docker`, 启动校验

### 3.3 Risks

| 风险 | 缓解 |
|---|---|
| 现有 `JudgeWorkerProcessor` 与 `SandboxService` 紧耦合 (字段名 / 异常类型) | M2 之前不改 worker, sandbox 重构在 [ADR-005](./ADR-005-rolling-deploy-playbook.md) milestone **M1b** 完成后接通 |
| InMemoryAdapter 行为漂移 (与 Docker 表现不一致 → 单测过但 prod 挂) | 加 Testcontainers 集成测试, 启动真 Docker 跑核心语言的"hello world" + "exit 1" + "infinite loop" 三类用例对照 |
| LanguageProfile 各实现持有的 docker arg 列表分散 → 安全策略漂移 | 公共安全 args (`--network none`,`--cap-drop ALL`,`--read-only`,`--user 1000:1000`,seccomp) 由 `SandboxExecutorImpl.commonSecurityArgs()` 拼接, LanguageProfile 只返回语言部分 |

## 4. Validation

- [ ] `SandboxExecutor` port 单元测试用 InMemoryAdapter 100% 通过, 不依赖 docker
- [ ] 5 个语言 profile (JS/Python/Java/C/C++) 各自单测 (compile failure / runtime error / accepted)
- [ ] Testcontainers IT 跑核心矩阵 (3 用例 × 5 语言 = 15 case), 与 InMemoryAdapter 行为对照
- [ ] grep 确认 `switch (language)` 在 `submission/` 子树为零
- [ ] grep 确认 `ProcessBuilder` 在 `submission/` 子树只出现在 `SandboxExecutorImpl` 内
- [ ] `docs/CODEMAPS/sandbox.md` 同步更新, 新增 "Port / Adapter / LanguageProfile" 章节

## 5. References

## 6. 实战教训 — 4 个 bug 都把 verdict 退化成 Runtime Error (2026-06-14)

> M2a ship 后第一次 OJ 提交 (`/problems/7/submissions/run`) 全报 `Runtime Error + "Runtime error"`,
> 但 docker 直跑 + 本地 harness 都返回正确 verdict。说明问题在 backend 配置 + dispatch shell,
> 不是用户代码。下表是 4 个**叠加**bug, 每个独立会让 sandbox 静默失败:

| # | Bug | 文件 / 配置 | 症状 | 修复 |
|---|-----|-------------|------|------|
| 1 | `SANDBOX_IMAGE=ulticode-sandbox:latest` 指向 base-17 镜像(只有 JDK,**没**装 harness) | `.env` | `javac: cannot find symbol ListNode` × 9 → docker exit 1 → empty stdout → `sanitizeSandboxOutput(null)` 返回 `"Runtime error"` → `RUNTIME_ERROR` | `.env`: `ulticode-sandbox-dform:phase2-pinned` |
| 2 | `SANDBOX_ENABLED=false` 整体禁用 docker 沙箱 | `.env` | 配置对但不调 docker, 所有提交 verdict = `Runtime Error` | `.env`: `SANDBOX_ENABLED=true` |
| 3 | `SANDBOX_SECCOMP_PROFILE=docker/sandbox/seccomp-profile.json` **相对路径**, Spring Boot cwd 是 `backend-spring/`,docker daemon 拒绝 `--volume` 含 `/` 的 host path → 立即 exit 非零 | `.env` | 同 #1 症状, 但有 `WARNING: includes invalid characters for a local volume name` | `.env`: 改为**绝对路径** `/home/davidhlp/project/UltiCode/docker/sandbox/seccomp-profile.json`。**治本 (2026-06-14 post-review)**: 该绝对路径已固化到 `application.yml` 默认值, 新机器 / CI / 不读 `.env` 的环境也直接走绝对路径, 不再依赖 `.env` 兜底即可避免此 bug |
| 4 | `JavaLanguageProfile.dockerCommand` dispatch shell 用相对路径 `Solution.java`, 但镜像 `WORKDIR=/home/sandbox`,`/job` 是 mount 卷 — javac 找不到源文件 | `backend-spring/.../JavaLanguageProfile.java:63` | `error: file not found: Solution.java\nUsage: javac <options> <source files>` | 改成 `/job/Solution.java` 绝对路径 |
| 5 | Java 17 `SecurityManager` 弃用 WARNING 行污染 stdout,**在** JSON envelope **之前**输出, Jackson 严格解析失败 | `backend-spring/.../CodeExecutionHelperImpl.java#parseDEnvelope` | `D-form envelope unparseable: WARNING: ... {valid JSON here}` → 整体判 `Runtime Error` | `parseDEnvelope` 找第一个 `{`, 取 `substring(jsonStart)` 再 parse |

### 6.1 根因 — 配置/契约 vs runtime

**Sandbox 失败有 3 层**,每一层都会回退到 "Runtime Error":

1. **镜像层** (bug #1, #2, #3): docker 容器根本跑不起来, exit ≠ 0, stdout 空 → `sanitizeSandboxOutput(null|empty)` 兜底 → `"Runtime error"`
2. **Dispatch 层** (bug #4): 容器起来了但 shell 命令本身写错 → javac/解释器失败 → exit ≠ 0 → 同样走兜底
3. **Envelope 解析层** (bug #5): 沙箱输出**本身是对的**(返回 Accepted), 但 backend `parseDEnvelope` 无法定位 JSON 起点 → 整个批次判 Runtime Error

3 层的共同特征是 **verdict 都是 "Runtime Error" + "Runtime error"**, 加上 `runtimeMs=0` 或 4-8ms 的"假短时长"(因为 docker 0 启动 + 立即 exit), 错误信号高度同质化, 排查极难。

### 6.2 防御 — 把"Runtime Error"细分

| 当前 | 建议 |
|---|---|
| `sanitizeSandboxOutput(null\|empty) → "Runtime error"` | 加长度前缀 `"harness emitted no envelope (stdout=null)"` |
| `outcome.exitCode != 0 && cause==null && !compile && !fork` → 一律 `RUNTIME_ERROR` | 区分 `compile→COMPILE_ERROR`, `dispatch-not-found→SANDBOX_ERROR`, `timeout→TLE`, 只有用户代码真抛异常才走 `RUNTIME_ERROR` |
| `parseDEnvelope` 失败 → 全批 Runtime Error | 区分 "envelope unparseable" vs "envelope missing per-case" vs "exit_code != 0"; 前两者已经是 SANDBOX_ERROR 候选 |

### 6.3 调试信号 — 怎么快速识别是 5 类中的哪一类

```bash
# 看 docker 实际有没有拉起
docker events --filter type=container --filter event=create,start &
curl -X POST /problems/7/submissions/run ...
# 看 docker ps -a --filter ancestor=<SANDBOX_IMAGE> 有没有新容器

# 看 sandbox tmpdir 有没有创建
ls /tmp/ulticode-sandbox-* | head  # 不存在 → 没进 executeDForm

# 看 runtimeMs:
#   0-10ms → 镜像层 (bug #1/#2/#3) 或解析层 (#5)
#   100-300ms → dispatch 层 (bug #4) 或真 Runtime Error

# 看 detail:
#   "Runtime error" → 镜像层 (兜底)
#   "D-form envelope unparseable: ..." → 解析层 (bug #5)
#   "D-form harness panic (exit_code=N): ..." → 镜像层但 daemon 有 stderr
#   "D-form batch dispatch timed out after Ns" → TLE
#   "[XException] message" → 真 Runtime Error (用户代码抛)
```

### 6.4 关联 PR / 变更

- `.env`: `SANDBOX_IMAGE`, `SANDBOX_ENABLED`, `SANDBOX_SECCOMP_PROFILE`
- `backend-spring/.../JavaLanguageProfile.java:63` — dispatch shell 加 `/job/` 前缀
- `backend-spring/.../CodeExecutionHelperImpl.java#parseDEnvelope` — `int jsonStart = stdout.indexOf('{')` 切前缀

### 6.5 留给后续的硬化任务 (未做)

1. **Docker `latest` tag 重打**: `docker/sandbox/Dockerfile` 自打 `ulticode-sandbox-dform:phase2-pinned`, 没顺手 `docker tag ... ulticode-sandbox:latest`。`./harness/build.sh` 应同步重打 `latest`, 否则下次回归。
2. **OJ Java harness 不写内存到 envelope**: `docker/sandbox/harness/java/src/main/java/Main.java` 加 `Runtime.totalMemory()` 上报, 后端 `parseDEnvelope` 解析后填 `memoryMb`(当前硬编码 `0.0`)。
3. **LeetCode vs OJ 风格**: 测试数据 `expected_output: "[]"` vs LeetCode 期望 `null` — 业务侧确认规范。

---

## 7. Follow-up Hardening (2026-06-14) — §6.5 三项全部 ship

> §6.5 列出的 3 项硬化在同一天晚上 ship, 闭环 ADR-002 全部已知 gap。

### 7.1 Docker `latest` tag 自动重打 (close §6.5 #1)

**Why**: `docker/sandbox/Dockerfile` 自打 `ulticode-sandbox-dform:phase2-pinned` 但 `:latest` 标签手动未更新, 跨天后 `SANDBOX_IMAGE=ulticode-sandbox:latest`(默认)指向 Form-A base-17 → 所有提交 `Runtime Error`。

**Where** (`docker/sandbox/harness/build.sh`):
- 末尾新增 `docker build -t ulticode-sandbox-dform:phase2 .` + `docker tag ulticode-sandbox-dform:phase2 ulticode-sandbox:latest`
- 新增 `--no-docker` flag: CI matrix 阶段只产 harness 二进制, 跳过 docker build
- `base-17` 缺失时改为 fail-fast (`exit 2`), 不再 print WARNING 后沉默继续

**Why both tags stay**: `phase2-pinned` 是版本锁(用户/CI 可指定), `latest` 是 rolling tag(随 build.sh 更新)。两者同时存在, default 配置 (`SANDBOX_IMAGE=ulticode-sandbox:latest`) 与版本锁路径都不回归。

### 7.2 Harness `peak_memory_bytes` 上报 (close §6.5 #2)

**Why**: OJ 旧行为硬编码 `memoryMb: 0.0`( `CodeExecutionHelperImpl.parseDEnvelope:271` ),前端展示没意义。

**Where**:
- `Main.java#peakMemoryBytes()`: `Runtime.totalMemory() - Runtime.freeMemory()`(G1GC 下 `MemoryMXBean.getHeapMemoryUsage().getUsed()` 返回 0, 不可用)
- 每个 case 在 `runCase` 结束后采样, 写入 envelope `peak_memory_bytes`
- `PerCaseResultDTO`: 加 `peakMemoryBytes` 字段 + `fromMap` 解析
- `parseDEnvelope`: 把 bytes 转 MB 替换硬编码 `0.0`, **0 字节保留**(表示 harness 旧版本无此字段)

**Limits**: 是 harness JVM heap 估算, 不是 cgroup 级别精确值。`MaxMemory=8254390272` 是 JVM 上限;实际使用 = `totalMemory() - freeMemory()`, GC 后可能回落到 baseline, **不是 true peak**。但比 `0` 有意义得多,适合前端"内存大致范围"展示。

**Frontend 显示**: `6.0MB / 7.0MB`(mergeKLists 用户代码测试结果),可读。

### 7.3 OJ 策略: null list-like 返回值 → `[]` (close §6.5 #3)

**Why**: LeetCode 风格用户代码对空输入常返回 `null`(特别是 `ListNode` / `TreeNode` / `int[]`);OJ 测试数据 `output_text` 走 `[]` 惯例(平台风格)。两者不兼容 → LeetCode 用户代码全部 Case 2 Wrong Answer。

**Where** (`Harness.java#jsonable`):
```java
if (value == null && isListLike(returnType)) {
    return new java.util.ArrayList<>();  // → toJson → "[]"
}
```

**`isListLike(t)`**:
- `t.isArray()` (一维/多维)
- `List.class.isAssignableFrom(t)` (List / ArrayList / LinkedList)
- `ListNode.class.isAssignableFrom(t)` / `TreeNode.class.isAssignableFrom(t)` (LeetCode 风格)

**仅影响 harness envelope 输出**(`actualJson`),**不影响 backend 持久化的 result 字段**(那是 `RunResultDTO.RunCaseResult.output`, 仍为 `null` → `"null"`)。前端展示时已把 `null` 显示为 "null", 现在 vs `[]` 在 OJ 比较层对齐。

**反向同步**: 如果平台未来改用 `null` 惯例 (某些 OI 平台),把 `Harness.jsonable` 里的 `isListLike` 短路即可, 一行代码。

### 7.4 Verification (post-follow-up)

```
$ ./harness/build.sh                       # 一行命令 = pre-compile + docker build + tag :latest
$ pm2 delete ulticode-9001 && pm2 start ecosystem.config.cjs --only ulticode-9001
$ curl -X POST /problems/7/submissions/run  # mergeKLists 用户代码

→ verdict=Accepted, passedCases=2, totalCases=2
   pe-007-1: status=Accepted runtime=2ms memory=6.0MB
   pe-007-2: status=Accepted runtime=0ms memory=6.0MB   # null→[] 生效
```

### 7.5 关联 PR / 变更

- `docker/sandbox/harness/build.sh` — 末尾 docker build + tag `:latest`, `--no-docker` flag, base image 缺失 fail-fast
- `docker/sandbox/harness/java/src/main/java/Main.java` — `peakMemoryBytes()` 跨 GC 实现, 写入 envelope
- `docker/sandbox/harness/java/src/main/java/Harness.java` — `isListLike()` + `jsonable` null → `[]` 策略
- `backend-spring/.../dto/PerCaseResultDTO.java` — `peakMemoryBytes` 字段
- `backend-spring/.../service/impl/CodeExecutionHelperImpl.java` — `parseDEnvelope` 写 MB
- `.env` — `SANDBOX_IMAGE=ulticode-sandbox:latest` 默认值现在能工作

### 7.6 留给后续的硬化任务 (still 未做)

1. **跨语言 memory 精度**: 当前 Java harness 用 JVM heap 估算, Python/C/C++ harness 同问题。Phase 2+ 升级到 cgroup `memory.max_usage_in_bytes` 读取(Linux only,需 SYS_ADMIN cap 或特权 proc 路径)。
2. **null→[] 策略的反向同步**: 如果有用户期望 LeetCode 原汁 (null), 当前策略会让 OJ 跟 LeetCode 行为**一致**但跟某些 OI 平台不一致 (它们期望 `null`)。需要跟业务侧确认"默认 LeetCode 还是 OI"。
3. **Adversarial envelope parse**: 现在 `parseDEnvelope` 用 `indexOf('{')` 切前缀, 如果 envelope 本身 JSON 里有嵌套 `{`, 可能误切。Phase 2+ 用 streaming parser (Jackson `JsonParser` 流式) 或要求 harness 把 envelope 写到独立 fd。

### 7.7 实战教训 #6 — Facade `toDtoCaseResult` 漏 inputs/output/expectedOutput (2026-06-14)

> §6 的 5 个 bug 都是"verdict 退化成 `Runtime Error`"。这一类新 bug 反向:
> **verdict 正确**(`Accepted`), 但 wire DTO 漏透传 user-supplied 详情, 前端 UI 显示
> "此用例未返回可展示的输入输出详情" 而 verdict/runtime/memory 都对。
> 现象不报警, 排查靠 console network panel 对比 request.body 与 response.data。

#### 症状 (用户报告: mergeKLists /run, `2026-06-14`)

请求 body(用户提交):
```json
{
  "testCases": [
    {
      "id": "pe-007-1", "label": "Case 1",
      "output": "[1,1,2,3,4,4,5,6]",
      "inputs": [{"id":"...","label":"lists","name":"lists","value":"[[1,4,5],[1,3,4],[2,6]]"}]
    },
    {"id": "pe-007-2", "label": "Case 2", "output": "[]", "inputs": []}
  ]
}
```

响应 body(`/problems/7/submissions/run` 返回):
```json
"cases": [
  {
    "id": "pe-007-1", "runId": "...", "submissionTestId": "pe-007-1", "testCaseId": "pe-007-1",
    "caseLabel": "Case 1", "status": "Accepted", "runtime": "2ms", "memory": "6.0MB",
    "runtimeMs": 2, "memoryMb": 6.0
  },
  { /* Case 2 same shape */ }
]
```

`passedCases=2, totalCases=2, verdict=Accepted` — verdict/runtime/memory 全部对。**但 `inputs / output / expectedOutput / detail` 全部缺失**。前端 `console/src/views/problems/test/TestResultsView.vue:64` 的 `hasResultDetails` 三个全 false → 显示"此用例未返回可展示的输入输出详情"(i18n key `problem.layout.noResultDetails`)。

#### 根因 — port↔wire 边界两个 builder 没同步

`/problems/{id}/submissions/run` 走 `CodeExecutionService.execute → toDtoCaseResult`, **不是** helper 里的 `parseDEnvelope → buildCaseResult`。两个 builder 都该透传 4 个字段(`detail / output / expectedOutput / inputs`), 但只有 helper 的 `buildCaseResult` 写全了, facade 的 `toDtoCaseResult` 漏了后 3 个:

```java
// CodeExecutionService.toDtoCaseResult (PRE-FIX) — 缺 3 个 builder 调用
return RunResultDTO.RunCaseResult.builder()
        .id(...) .runId(...) .submissionTestId(...) .testCaseId(...) .caseLabel(...)
        .status(...) .runtime(...) .memory(...) .runtimeMs(...) .memoryMb(...)
        .detail(port.detail())      // ← 只透传 detail
        // .output(port.output())                ← 漏
        // .expectedOutput(port.expectedOutput()) ← 漏
        // .inputs(port.inputs() → mapped)        ← 漏
        .build();
```

```java
// CodeExecutionHelperImpl.buildCaseResult (helper 路径) — 已透传 4 个
return RunResultDTO.RunCaseResult.builder()
        ...
        .detail(detail).output(output)
        .expectedOutput(testCase.getOutput())  // 注:用 request DTO, 不是 port
        .inputs(inputs)
        .build();
```

为什么 helper 路径对? 因为 helper 路径服务于 `parseDEnvelope`, D-form harness envelope 本身就只回填 `output` / `expectedOutput`, 不带 `inputs`;`inputs` 是从**请求 DTO** 的 `testCase.getInputs()` 直接 copy 过去的 — 路径一以贯之。Facade 路径是 Hexagonal 引入后的产物(`toDtoCaseResult` 是 port → wire 边界), 没复用 helper 的 builder, 写出来时漏了 3 行。

#### 信号 — 怎么快速识别是这一类(verdict 对, 详情缺)

```bash
# 1. 对比 request.body 与 response.data.cases[i] 的 keys:
#    request.body.testCases[i] 有 inputs/output/expectedOutput (如果请求 DTO 暴露)
#    response.data.cases[i] 只有 id/status/runtime/memory + 顶层 verdict/passedCases/totalCases
#    → facade 漏 mapping

# 2. 看 console 端 TestResultsView 的 hasResultDetails:
#    inputs.length=0 && output="" && expectedOutput="" → 三个 false → 走 noResultDetails 分支

# 3. (反向信号) 如果 detail="D-form envelope missing per-case result for index N",
#    那才是 §6 解析层 bug, 不是这一类
```

#### 防御 (post-fix)

1. **单测覆盖** `CodeExecutionServiceTest.execute_forwardsInputsOutputAndExpectedToWire` — 构造 port `acceptedWithOutput(...)` 模拟 D-form 成功包, 断言 wire DTO 的 4 个字段(id/inputs/output/expectedOutput/detail)全透传。位于 `backend-spring/src/test/java/com/ulticode/modules/submission/service/CodeExecutionServiceTest.java`。
2. **共用 builder / 强制 contract** — `CodeExecutionHelperImpl.buildCaseResult` 与 `CodeExecutionService.toDtoCaseResult` 当前是两套独立 builder 调用, 任何一边的字段增减都得手动同步另一条。**Phase 2+ 重构**: 抽 `RunResultDTO.RunCaseResult.builder().fromPort(port, requestDto)` 静态方法, 两个 caller 都走它, 单测覆盖 "所有 RunCaseResult 字段均被透传"。
3. **Schema 校验 (wire-side)** — 给 `RunResultDTO.RunCaseResult` 加 `@JsonInclude(NON_NULL)` 之外, Phase 2+ 在 controller 出口挂一个 assertion: `if (verdict == "Accepted" && inputs == null && expectedOutput == null && output == null) log.warn("Accepted case missing displayable details", ...)` — 防止下一个 builder 漏字段悄无声息 ship。

#### 关联 PR / 变更 (本节 ship)

- `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java#toDtoCaseResult` — builder 补 `.output(port.output()).expectedOutput(port.expectedOutput()).inputs(mappedInputs)`, 注释解释 "Bug fix: ... 否则 UI 显示 '此用例未返回可展示的输入输出详情'"
- `backend-spring/src/test/java/com/ulticode/modules/submission/service/CodeExecutionServiceTest.java` — 新增 `execute_forwardsInputsOutputAndExpectedToWire` 回归用例, 用 `RunCaseResult.acceptedWithOutput(...)` 构造带 `inputs/output/expectedOutput` 的 port, 断言 wire DTO 同步
- `RunCaseResult.InputParam` (wire DTO) vs `TestCase.Input` (port) — 字段差异: DTO 没有 `type` 字段(D-form 沙箱私有 hint), 映射时丢弃; 4 字段(id/label/name/value)对拷

#### Verification (post-fix)

```
./mvnw test -Dtest=CodeExecutionServiceTest
  → Tests run: 6, Failures: 0, Errors: 0, Skipped: 0  (含新增回归)

./mvnw test -Dtest=CodeExecutionServiceTest,CodeExecutionHelperImplTest,\
                  ProblemSubmissionControllerTest,CrossPathVerdictTest,\
                  SubmissionStatusCodecTest,SubmissionStateMachineTest
  → Tests run: 54, Failures: 0, Errors: 0  (submission 路径全过)

curl -X POST /problems/7/submissions/run -d '{"language":"java","code":"...","testCases":[…]}'
  → cases[i].inputs == [{"id":"...","label":"lists","name":"lists","value":"[[1,4,5],[1,3,4],[2,6]]"}]
  → cases[i].expectedOutput == "[1,1,2,3,4,4,5,6]"
  → cases[i].output == "[1,1,2,3,4,4,5,6]" (或 null; Accepted 时 D-form 已 echo)
  → 前端 TestResultsView 渲染 "lists = [[1,4,5],[1,3,4],[2,6]]" + "expected = [1,1,2,3,4,4,5,6]"
```

---

## 8. 资源测量与判定契约 (2026-06-16) — 全量修复 P0/P1/P2

> 本节是对沙箱「时间/内存怎么测、怎么判」的全量修复记录。源于一次专项技术评估发现
> 两个会导致**系统性误判**的 P0(per-case 超时公式不含 case 数;MLE 在生产从不判定),
> 以及多个测量不准(P1)与功能缺失(P2)。本节既是变更记录,也是新的测量契约。

### 8.1 修复的 P0(系统性误判)

| # | 问题 | 修复 |
|---|------|------|
| **P0-1** | `hardTimeoutSeconds = timeoutSeconds + 1`(整批)与 `perCaseTimeoutMs ≈ timeoutSeconds×1000`(每用例)公式冲突,多用例累计必然触发 docker SIGKILL → 整批全 TLE | 重定义语义:`job.timeoutSeconds()` = **每用例**限制;`hardTimeoutSeconds(job, caseCount) = perCase×caseCount + compileBudget + grace`(cap 180s);编译型语言(C/C++)+35s 编译预算。`SandboxExecutorImpl` runBatch 传 `cases.size()`,run/runOne 传 1 |
| **P0-2** | 生产路径从不判定 MLE,内存超限被 docker OOM kill → 整批 SIGKILL → 空 stdout → 误报 `Runtime Error`(`MEMORY_LIMIT_EXCEEDED` 是 enum 里的幽灵状态) | **三层判定**:(A) `buildDInputsJson` 把 `memory_limit_bytes` 写进 input.json,harness 自判 peak > limit → `"Memory Limit Exceeded"`;(B) `toPortResult` 兜底比对(harness 未自判时改判 MLE,向后兼容旧 harness);(C) docker exit 137 + 空 stdout → `SANDBOX_ERROR`(诚实标注 "likely cgroup OOM",不再伪装成 RE) |

### 8.2 修复的 P1(测量不准)

| # | 问题 | 修复 |
|---|------|------|
| **P1-1** | 时间用 wall-clock,含 JIT/编译/GC/harness 开销,跨语言不公平 | envelope 新增 `cpu_ms`(user+sys CPU time)。Java `ThreadMXBean.getCurrentThreadCpuTime()`(worker 线程内采样,排除 harness 反射开销);Python `resource.getrusage(RUSAGE_SELF)` 的 `ru_utime+ru_stime`;C++ child `getrusage(RUSAGE_SELF)`。**TLE 判定仍用 wall-clock**(防真实挂钟超时),CPU time 用于公平展示 |
| **P1-2** | 时间整数毫秒截断,0–999µs 显示 `0ms`(快题误导) | envelope 新增 `elapsed_us`(微秒整数);`runtime` 字符串优先 `String.format("%.2fms", us/1000.0)`;`parseRuntimeMs` 改用 `Double.parseDouble` 兼容小数 |
| **P1-3** | 跨语言内存语义不一致:Java 单点采样、Python ru_maxrss、C++ 硬编码 0 | Java 改 `MemoryPoolMXBean.resetPeakUsage()` + `getPeakUsage().getUsed()` 求和(真正的 high-water mark,非 `totalMemory()-freeMemory()` 单点采样);Python 保留 `ru_maxrss`(per-case 子进程,本就是该 case 峰值);C++ child `getrusage(RUSAGE_SELF).ru_maxrss` 经 pipe header 回传父进程(替换硬编码 0)。三种语言现在都报**真实峰值** |
| **P1-4** | C/C++ 编译时间吃掉 docker 整批硬超时 → 系统性 TLE | 随 P0-1:`hardTimeoutSeconds` 给编译型语言 +35s `COMPILE_BUDGET_SECONDS`,编译阶段不再被外层 SIGKILL |

### 8.3 修复的 P2(功能/设计)

| # | 问题 | 修复 |
|---|------|------|
| **P2-1** | Problem 表无 `time_limit`/`memory_limit`,所有提交用全局默认,OJ 无法按题配额 | Flyway `V20260616120000__Add_Problem_Resource_Limits.sql` 给 `problems` 表加两列(NULLABLE,无 backfill);`Problem` entity 加字段;`CodeExecutionService.resolveTimeoutSeconds/Mb` 按 `problemId` 读取,非空覆盖全局默认,NULL fallback |
| **P2-2** | Java harness 依赖弃用的 `SecurityManager`(JDK 21+ 默认禁用) | **本次不动**(项目锁 JDK 17,仍可用);标注为 Phase 2+ follow-up——届时按 Python 的 per-case 子进程隔离模式统一 |

### 8.4 新的 envelope 契约(向后兼容)

per-case `results[]` 每项新增两个**可选**字段,旧 harness 不发时后端默认 0 并回退:

| 字段 | 类型 | 语义 |
|------|------|------|
| `elapsed_us` | long | 精确 wall-clock 微秒(优先用于展示);不发则回退 `elapsed_ms` |
| `cpu_ms` | long | user+sys CPU 毫秒(公平性展示);不发则 0 |
| `peak_memory_bytes` | long | 该 case 真实峰值(已存在于 §7.2,语义本节升级为 true peak) |
| `memory_limit_bytes`(input.json) | long | 后端下发给 harness 的每用例内存上限;>0 时 harness 自判 MLE |

### 8.5 跨语言测量语义对照

| 语言 | peak 来源 | CPU 来源 | 备注 |
|------|-----------|----------|------|
| Java | `MemoryPoolMXBean` heap peak(reset 后 high-water) | `ThreadMXBean` worker 线程 | 同 JVM 串行跑 case,每 case `resetPeakUsage` 隔离 |
| Python | `resource.getrusage(RUSAGE_SELF).ru_maxrss` | 同上 `ru_utime+ru_stime` | per-case 子进程,ru_maxrss 本就是该 case 峰值 |
| C++ | child `getrusage(RUSAGE_SELF).ru_maxrss`(经 pipe header 回传) | 同上 | 每 case fork child + SIGKILL 隔离;父进程拆 `peakKb\ncpuMs\n<json>` header |
| C | (Phase-1 stub,无测量代码) | — | 本次不动;补全 harness 是独立 follow-up |

### 8.6 验证

- 后端 `./mvnw compile` + `test-compile` 全绿;`CodeExecutionServiceTest` / `CodeExecutionHelperImplTest` / `InMemorySandboxAdapterTest` 全绿
- Java/Python harness `py_compile` / `mvn` 语法验证通过;C++ `g++ -fsyntax-only` 通过
- 全量后端 1164 测试:本次修复引入 **0 regression**;既有 9 Failures + 2 Errors 经诊断全部位于 contest/admin 模块且不引用 `CodeExecutionService`/`Problem`(pre-existing,与本节无关)

### 8.7 关联变更

- 后端:`SandboxExecutorImpl`(超时公式 + MLE Layer A/B/C + 透传)、`CodeExecutionHelper(Impl)`(buildD 加 memoryLimitBytes + buildCaseResult 加 elapsedUs/cpuMs + parseRuntimeMs 支持小数)、`CodeExecutionService`(P2-1 读取 + 聚合精度 + 透传)、`PerCaseResultDTO`/`RunResultDTO`/`RunCaseResult`(新字段)、`Problem` entity + `V20260616120000` 迁移
- Harness:`Main.java`(Java peak/cpu/μs/MLE)、`_case_runner.py` + `main.py`(Python peak/cpu/μs/MLE)、`cpp/main.cpp`(C++ child getrusage + μs + MLE)
- 测试:`CodeExecutionServiceTest`(ProblemMapper mock + parseRuntimeMs lenient)、`CodeExecutionHelperImplTest`(buildD 加 memoryLimitBytes 参数)

---

## 9. References

- [ADR-000](./ADR-000-hexagonal-grilling-session.md) — 拆分溯源
- [ADR-001](./ADR-001-verdict-status-codec.md) — RunCaseResult 用 SubmissionStatus enum
- [ADR-005](./ADR-005-rolling-deploy-playbook.md) — M1a/M1b/M1c milestone 拆分 + sandbox image rollback workflow
- 现有代码: `backend-spring/.../submission/service/impl/SandboxServiceImpl.java` (重构源)
- 现有文档: `docs/CODEMAPS/sandbox.md` (安全矩阵 + i18n 双端对齐)
- 项目规约: `.claude/rules/backend/07-java-design.md` (#8 单一原则, #10 依赖倒置, #11 开闭原则)
