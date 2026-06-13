# ADR-002: Sandbox Hexagonal — Port + LanguageProfile Strategy + 双 Adapter

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Accepted** (2026-06-13) |
| **关闭 Milestone** | M2a (`ea6031466`..`348525e63` round-1 + `6b234771c`..`361233470` round-2) |
| **日期 (Date)** | 2026-06-13 |
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

| Adapter | 用途 | 本次落地 |
|---|---|---|
| `DockerSandboxAdapter` | 生产 — 现 `SandboxServiceImpl` 重构迁入, 保留全部安全策略 | ✅ |
| `InMemorySandboxAdapter` | 测试 — 接收 `SandboxJob` 返回预设 `RunCaseResult` (可按 `job.code` 包含关键字路由到不同 verdict) | ✅ |
| `RemoteJudgeSandboxAdapter` | 调外部判机 (HTTP/gRPC) | ❌ 不实现, 不预留接口 (YAGNI) |
| `FirecrackerSandboxAdapter` / `gVisorSandboxAdapter` | 更轻隔离 | ❌ 不实现 |

加新语言: 只新增 `RustLanguageProfile implements LanguageProfile` , 不改 `SandboxExecutor` / `JudgeWorker` (开闭原则 #11) 。

加新沙箱: 只新增 `class XxxSandboxAdapter implements SandboxExecutor` , 通过 `@ConditionalOnProperty(name="sandbox.executor",havingValue="xxx")` 切换 (依赖倒置 #10) 。

### 2.4 Verdict 解析下沉到 LanguageProfile

原 `SandboxServiceImpl.isJavaCompileFailure(stdout)` 这种 java-specific 检测**移到** `JavaLanguageProfile.isCompileFailure(stdout)` 。Sandbox executor 只负责"跑容器 + 收 stdout/exitCode", verdict 分类完全由 profile 自报。

### 2.5 Sandbox-level 失败 (fork failure / daemon 压力) 分类**留在** Executor

理由: 这些是**跨语言**的基础设施失败, 不属于 LanguageProfile 关注点。继续用现有 `isSandboxForkFailure` / `isDockerDaemonForkFailure` 逻辑, 但返回 `SubmissionStatus.SANDBOX_ERROR` (enum 而非字符串, 见 ADR-001) 。

## 3. Consequences

### 3.1 Positive

- **单测无需 Docker daemon** (InMemoryAdapter), CI 提速 + 离线开发可写单测
- 加新语言只动 1 个文件 (新 LanguageProfile bean), 编译期保证 (重复 languageId 启动崩)
- Verdict 字符串契约由 ADR-001 单点管理, sandbox 不再持有
- 现有 `docs/CODEMAPS/sandbox.md` 的安全矩阵零损失 (`DockerSandboxAdapter` 全量继承)

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
- [ ] grep 确认 `ProcessBuilder` 在 `submission/` 子树只出现在 `DockerSandboxAdapter` 内
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
| 3 | `SANDBOX_SECCOMP_PROFILE=docker/sandbox/seccomp-profile.json` **相对路径**, Spring Boot cwd 是 `backend-spring/`,docker daemon 拒绝 `--volume` 含 `/` 的 host path → 立即 exit 非零 | `.env` | 同 #1 症状, 但有 `WARNING: includes invalid characters for a local volume name` | `.env`: 改为**绝对路径** `/home/davidhlp/project/UltiCode/docker/sandbox/seccomp-profile.json` |
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

---

## 8. References

- [ADR-000](./ADR-000-hexagonal-grilling-session.md) — 拆分溯源
- [ADR-001](./ADR-001-verdict-status-codec.md) — RunCaseResult 用 SubmissionStatus enum
- [ADR-005](./ADR-005-rolling-deploy-playbook.md) — M1a/M1b/M1c milestone 拆分 + sandbox image rollback workflow
- 现有代码: `backend-spring/.../submission/service/impl/SandboxServiceImpl.java` (重构源)
- 现有文档: `docs/CODEMAPS/sandbox.md` (安全矩阵 + i18n 双端对齐)
- 项目规约: `.claude/rules/backend/07-java-design.md` (#8 单一原则, #10 依赖倒置, #11 开闭原则)
