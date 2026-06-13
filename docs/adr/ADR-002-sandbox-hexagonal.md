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

- [ADR-000](./ADR-000-hexagonal-grilling-session.md) — 拆分溯源
- [ADR-001](./ADR-001-verdict-status-codec.md) — RunCaseResult 用 SubmissionStatus enum
- [ADR-005](./ADR-005-rolling-deploy-playbook.md) — M1a/M1b/M1c milestone 拆分
- 现有代码: `backend-spring/.../submission/service/impl/SandboxServiceImpl.java` (重构源)
- 现有文档: `docs/CODEMAPS/sandbox.md` (安全矩阵 + i18n 双端对齐)
- 项目规约: `.claude/rules/backend/07-java-design.md` (#8 单一原则, #10 依赖倒置, #11 开闭原则)
