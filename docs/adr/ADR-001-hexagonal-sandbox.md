# ADR-001: Hexagonal 化判题边界 (Sandbox / Queue / Notification)

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | Proposed |
| **日期 (Date)** | 2026-06-13 |
| **作者 (Author)** | DavidHLP |
| **决策方式** | `/grill-me` 访谈式决策树, 7 个分支逐个对齐 |
| **取代 (Supersedes)** | — |
| **被取代 (Superseded by)** | — |
| **关联代码** | `backend-spring/src/main/java/com/ulticode/modules/{submission,queue,notification,websocket}/` |
| **关联文档** | `docs/CODEMAPS/sandbox.md`, `docs/CODEMAPS/architecture.md`, `CLAUDE.md` |

---

## 1. Context (上下文)

UltiCode 当前后端 (`backend-spring/`) 在 **判题主路径 (submit → enqueue → judge → verdict → notify)** 上耦合度过高,带来以下事实问题:

### 1.1 已识别的耦合点

| 模块 | 耦合形态 | 代码定位 |
|------|---------|---------|
| **Sandbox** | `SandboxServiceImpl` 同时承担**语言分发 (switch) + Docker CLI 直调 + verdict 字符串解析** 三件事, 单测必须起 Docker daemon | `submission/service/impl/SandboxServiceImpl.java:201`, `:270` (两处 `switch (language)`) |
| **Verdict** | 用 `Map<String, Integer> VERDICT_PRIORITY` 字符串作 key, `"Runtime Error"` 拼错运行时才暴露 | `queue/processor/JudgeWorkerProcessor.java:61-67` |
| **Submission 状态** | `submission.status` 是字符串, 转换散落在 `submit()` / `process()` / `updateSubmissionResult()` 三处, 无"Finished → Judging 回退"防护 | `submission/service/impl/SubmissionServiceImpl.java`, `queue/processor/JudgeWorkerProcessor.java` |
| **submit() 事务边界** | DB 写入 + Redis enqueue **非原子**: DB 成功 + Redis 超时 → 任务静默丢失 | `submission/service/impl/SubmissionServiceImpl.java#submit` |
| **Queue** | `QueueServiceImpl` 直接绑 Redisson `RQueue`, port 不存在 | `queue/service/impl/QueueServiceImpl.java` |
| **Notification** | In-App (`notification/`) / Email (`email/`) / WebSocket (`websocket/RealtimeService`) **三模块孤立**, SubmissionService 想全发要手工调 3 次 | `notification/service/NotificationDispatchService.java`, `websocket/service/RealtimeService.java`, `email/service/impl/EmailServiceImpl.java` |

### 1.2 优化目标 (访谈对齐)

**主矛盾: 降低耦合 / 提升可测试性 / 关键基础设施可替换。**

不追求一次性提升性能 / 加新业务能力,只做 **架构整顿**。

### 1.3 当前已使用且保留的模式

下列模式当前已落地,本次**不动**:

- 分层架构 (Controller → Service → Mapper → Entity)
- Spring DI / IoC + Lombok 构造注入
- DTO/VO + MapStruct 映射
- 生产者-消费者 (`QueueServiceImpl` + `JudgeWorkerProcessor`)
- 模板方法 (`JobProcessor<T>` default 方法)
- 观察者 / Pub-Sub (`AchievementTriggerServiceImpl` + `*Listener`)
- 门面 (`RealtimeService`)
- 统一响应封装 (`Result<T>` + `GlobalExceptionHandler`)
- 配置外置 (`DockerSandboxConfig`, `QueueConfig` 等 `@ConfigurationProperties`)

---

## 2. Decision (决策)

整体方向: **采用 Ports & Adapters (Hexagonal Architecture)**, 把 3 个基础设施边界 (Sandbox / Queue / Notification) 收口为领域端口, 现有实现降级为 Adapter。配套 GoF 模式仅在**消除已识别坏味**时使用,不预先抽象未来可能需求。

### 2.1 Sandbox 边界 (方案 C)

**端口**

```java
public interface SandboxExecutor {
    RunResult run(SandboxJob job);   // 单用例
    BatchRunResult runBatch(SandboxJob job, List<TestCase> cases);  // 多用例
}

public interface LanguageProfile {
    String languageId();                                  // "java" / "python" / "cpp" ...
    List<String> dockerCommand(SandboxJob job);
    Path materializeWorkspace(Path tempDir, String code);
    boolean isCompileFailure(String stdout);
}
```

**Strategy 注册机制**: `List<LanguageProfile>` 构造注入, Sandbox 内 `Collectors.toMap(LanguageProfile::languageId, ..., dup -> { throw new IllegalStateException(...) })`,启动时**重复 languageId 即崩**。

**Adapter 矩阵 (本次只做 2 个)**

| Adapter | 用途 | 本次实现 |
|---------|------|---------|
| `DockerSandboxAdapter` | 生产, 现 `SandboxServiceImpl` 迁入 | ✅ |
| `InMemorySandboxAdapter` | 单测 / 集成测试 (Stub 返回预设结果) | ✅ |
| `FakeDeterministicSandboxAdapter` (code 含关键字→固定 verdict) | JudgeWorker 流程集成测试 | 后续如需再加 |
| `RemoteJudgeSandboxAdapter` (HTTP/gRPC 远程判题集群) | 真有分布式判题需求才做 | ❌ 不预留接口 |
| `FirecrackerSandboxAdapter` / `gVisorSandboxAdapter` | 真有性能或更强隔离需求才做 | ❌ 不预留接口 |

**Verdict 形态**

```java
public enum Verdict {
    AC, PE, WA, TLE, MLE, RE, CE, SANDBOX_ERROR;
    public int severity() { return ordinal(); }  // 越大越严重, 0=AC
}

@Component
public class VerdictResolver {                  // 纯函数, 易单测
    public Verdict reduce(Collection<Verdict> caseVerdicts) { ... }
}
```

替换 `JudgeWorkerProcessor.VERDICT_PRIORITY` stringly-typed map。

### 2.2 Queue 边界

**窄端口** (基础设施特性不泄漏):

```java
public interface JudgeQueue {
    String enqueue(JudgeJob job);              // job 自带 priority/delay 字段
    Optional<JudgeJob> poll(Duration timeout);
    JobStatus getStatus(String jobId);
    QueueStats stats();
}
```

`priority` / `delay` / `deadLetterReason` 作为 `JudgeJob` 字段, 由 adapter 自行翻译 (Redis: sorted set; InMemory: `PriorityBlockingQueue`; 未来 RabbitMQ: priority exchange)。

**Adapter**

- `RedisJudgeQueueAdapter` (现 `QueueServiceImpl` 迁入)
- `InMemoryJudgeQueueAdapter` (测试)
- `RabbitMQJudgeQueueAdapter` ❌ 不预实现, 真有可靠性需求再加

### 2.3 Submission 状态机 (方案 B)

**轻量 State Machine**: 不上 GoF State Pattern (状态本身无独立行为), 不上 Spring StateMachine 框架 (杀鸡用牛刀)。

```java
public enum SubmissionStatus {
    PENDING, JUDGING, ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILE_ERROR, SANDBOX_ERROR, FAILED;

    private static final Map<SubmissionStatus, Set<SubmissionStatus>> ALLOWED = Map.of(
        PENDING, Set.of(JUDGING, FAILED),
        JUDGING, Set.of(ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED,
                        MEMORY_LIMIT_EXCEEDED, RUNTIME_ERROR,
                        COMPILE_ERROR, SANDBOX_ERROR, FAILED)
        // 终态 (AC/WA/TLE/...) 无后继, 不在 map 内即视为终态
    );

    public boolean canTransitionTo(SubmissionStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
```

配套 **CAS update** 防并发回退:

```sql
UPDATE submission
SET status = #{newStatus}, ...
WHERE id = #{id} AND status = #{expectedStatus}
```

`SubmissionMapper.updateWithStatusCheck(id, expected, next, ...)` 返回 affected rows, service 层校验 `== 1`,否则抛 `IllegalStateException`。

### 2.4 submit() 事务一致性 (方案 B, 非全量 Outbox)

```java
@Transactional
public SubmissionVO submit(...) {
    submissionMapper.insert(submission);    // PENDING

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override public void afterCommit() {
                judgeQueue.enqueue(JudgeJob.from(submission));  // 提交后才入队
            }
        }
    );
    return vo;
}
```

配套 **Reaper Worker** (`@Scheduled fixedDelay = 60_000`), 扫 `submission.status = PENDING AND created_at < now() - 5min` 重入队 + 幂等 `judgeJob.id = submission.id` 防重复执行。

> **不上全量 Outbox 表** 的理由: 普通刷题场景任务偶发丢失允许用户重试。比赛 (Contest) 场景如未来上线收费判题, 可在 ADR-XXX 中单独加 Contest Outbox, 不污染普通路径。

### 2.5 Notification 边界 (方案 C: Event + Orchestrator + Channel 集合)

```java
// 1. 业务侧只发事件
public class SubmissionServiceImpl {
    public SubmissionVO submit(...) {
        // ... 写 DB, afterCommit 入队 ...
    }
}

public class JudgeWorkerProcessor {
    public JobStatusDTO process(JudgeJob job) {
        // ... 跑沙箱 / 写结果 ...
        eventPublisher.publishEvent(new SubmissionCompletedEvent(submissionId, verdict, ...));
    }
}

// 2. 单一 Orchestrator 接住事件, 查 preference, 走 channel 列表
@Component
@RequiredArgsConstructor
public class NotificationOrchestratorListener {
    private final List<NotificationChannel> channels;       // Spring 集合注入
    private final NotificationPreferenceService preferences;

    @Async
    @EventListener
    public void onSubmissionCompleted(SubmissionCompletedEvent e) {
        var prefs = preferences.get(e.userId());
        channels.stream()
            .filter(c -> prefs.allows(c.channelId(), e.category()))
            .forEach(c -> c.send(envelope(e)));
    }
}

// 3. 三个 channel 各自实现
public interface NotificationChannel {
    String channelId();              // "in_app" / "email" / "websocket"
    void send(NotificationEnvelope envelope);
    boolean supports(NotificationType type);
}
```

业务模块 (`submission`, `achievement`, `contest`...) **不再 import** `NotificationChannel` / `EmailService` / `RealtimeService`,只 publish event。

> 该模式与项目已有的 `AchievementTriggerServiceImpl` event 风格一致, 团队已熟悉。

### 2.6 附加: Builder 在大 VO

`SubmissionVO`, `SubmissionDetailVO`, `RunResultDTO` (字段 ≥ 8) 加 Lombok `@Builder`。**仅此而已**, 不引入 Specification / Vavr Result monad。

---

## 3. 明确拒绝的方案 (Rejected Alternatives)

| 拒绝项 | 拒绝理由 |
|------|---------|
| **Chain of Responsibility (verdict)** | Verdict 是纯归约不是责任传递, 错配。每个 case status 已定, 只需找最严重那个, 不需要"handler 决定是否传递" |
| **经典 GoF State Pattern (每状态一个类)** | OJ 状态本身无独立行为 (不像订单 Cancelled 要触发退款), 5 个状态 × 1 个类 = 过度抽象 |
| **Spring StateMachine 框架** | Map + CAS update 已解决 99% 状态机需求, 引入框架成本 > 收益 |
| **Vavr `Try` / `Either` monad** | Java + Spring 生态以 `throw BusinessException` + `@ControllerAdvice` 为正统范式, monad 混入后整个团队思维模型分裂 |
| **Specification Pattern (除非真复用)** | MyBatis-Plus `LambdaQueryWrapper` 本身已是 Spec-lite; 只有出现"可见提交"这类**跨 3+ 模块复用**的条件时再抽 |
| **ServiceLoader / PF4J 语言插件框架** | 项目是封闭 5 语言集 (JavaScript / Python / Java / C / C++), 无第三方插件需求, IoC 容器集合注入完全够用 |
| **Remote / Firecracker / gVisor sandbox adapter** | 当前无需求 (YAGNI), 真需要时新增 Adapter 实现 `SandboxExecutor` 即可, 不影响其他代码 |
| **全量 Transactional Outbox 表** | 普通提交允许偶发丢失重试; 比赛场景真需要时单独加 Contest Outbox, 不污染主路径 |
| **NotificationChannel 由 SubmissionService 直接调 (方案 A)** | 仍需 import `NotificationDispatchService`, 业务-基础设施耦合未根除; event-driven 更彻底 |
| **NotificationChannel 各自 `@EventListener` 监听 (方案 B 纯 event 打散)** | "哪些 channel 在响应这个 event"需全局搜索, 调试困难; Orchestrator 收集为唯一入口更可维护 |

---

## 4. 实施路线图

**串行 3 个 worktree, 每个独立 PR, 不并行 merge** (状态机和 Queue 边界有耦合, 并行会陷 rebase 地狱)。

```
M0  [当前]            写本 ADR, 评审, 标记 Accepted
M1  hexagonal/sandbox  Sandbox port + LanguageProfile strategy + VerdictResolver + Verdict enum + Builder
M2  hexagonal/queue    JudgeQueue port + SubmissionStatus enum + ALLOWED transitions + CAS update + afterCommit + Reaper
M3  hexagonal/notification  NotificationChannel port + Orchestrator listener + 3 channel adapter
```

每个 worktree 完成时跑 **CLAUDE.md 中的 Verification Matrix** 再 merge:

```bash
cd backend-spring
./mvnw compile -B
./mvnw test -B                       # *IT 排除
./mvnw -Dtest='*IT' test -B          # 集成测试需 Docker (M1 涉及)
./mvnw verify -B
```

### 4.1 Worktree 创建模板

```bash
git fetch origin
git worktree add .claude/worktrees/hexagonal-sandbox -b refactor/hexagonal-sandbox origin/main
cd .claude/worktrees/hexagonal-sandbox
# ... 实施 + 测试 + 自测 ...
# 主 worktree 评审通过后:
git checkout main && git merge --no-ff refactor/hexagonal-sandbox
git worktree remove .claude/worktrees/hexagonal-sandbox
git branch -d refactor/hexagonal-sandbox
```

---

## 5. Consequences (后果)

### 5.1 Positive (收益)

- **Sandbox 单测无需 Docker daemon** → CI 变快, 本地开发可离线写单测
- **加新语言只需新增 1 个 `LanguageProfile` 实现**, 不触碰 `SandboxExecutor` 或 `JudgeWorkerProcessor`(开闭原则真落地)
- **Verdict 拼写错变编译错** → 减少一类长期低概率 bug
- **Submission 状态非法转换 + 并发回退被 CAS 守住** → 重复判题 / 状态错乱根除
- **submit() 任务丢失收敛至"5min 内由 Reaper 重入队"** → 用户感知近零
- **业务模块不 import 通知细节** → 加新通知渠道 (Push / SMS) 只需新 channel adapter
- **大 VO 构造可读性提升** (Builder)

### 5.2 Negative (代价)

- **代码量短期增加**: port 接口 + adapter 拆分 + 单测桩, 预估 +1500~2500 LOC
- **学习曲线**: 团队需理解 Hexagonal 的 port/adapter 边界 + Spring `List<T>` 集合注入 + `afterCommit` 钩子
- **3 个 worktree 串行实施**: 预估 4-6 周, 中间 main 上**新旧模式并存**
- **NotificationOrchestratorListener 是新单点**: 加 channel 必须改它 (但比"全局搜索哪个 channel 在监听"好得多)

### 5.3 Risks (风险) 与缓解

| 风险 | 缓解措施 |
|------|---------|
| M1 中 Sandbox 迁移破坏现有 verdict 字符串契约 (前端 i18n key) | 保留 `Verdict.toString()` 与现字符串映射兼容; 上线前跑 i18n 双端回归 (见 `docs/CODEMAPS/sandbox.md` 的 "i18n 双端对齐" 表) |
| M2 中 ALLOWED 转换表写漏一个合法迁移 → 生产判题卡 PENDING | 上线前 IT 覆盖所有现有 verdict 字符串 → 新 enum 的转换路径; Reaper Worker 兜底 |
| `afterCommit` 钩子若 `@Async` 配置漂移 → 队列阻塞 submit 线程 | 启动时校验 `@EnableAsync` + executor 配置存在; 加 micrometer 指标 `submit.enqueue.duration` 监控 |
| Event-driven 调用链路追踪难 | 在 `SubmissionCompletedEvent` 加 `traceId` 字段 (与 Arthas eagleeye-traceid skill 配套); Orchestrator listener 入口打 `log.info("dispatching {} via {} channels", event.id, channels.size())` |
| 3 worktree 间出现 merge 顺序约束 | 本 ADR § 4 强制串行; M2 完成前不开 M3 worktree |

---

## 6. Validation (验证标准)

ADR 状态从 Proposed → Accepted **之前**, 需满足:

- [ ] PR 评审通过 (至少 1 个 reviewer 在 GitHub 上 approve)
- [ ] 团队同步会上口头确认 (或异步 24h 无反对)
- [ ] 本 ADR commit 进 main 后, 才能开 `refactor/hexagonal-sandbox` worktree

ADR 状态从 Accepted → Implemented **之前**, 需:

- [ ] M1, M2, M3 三个 PR 全部 merge
- [ ] CLAUDE.md Verification Matrix 全绿
- [ ] `docs/CODEMAPS/sandbox.md` / `architecture.md` 同步更新, 反映新的 port/adapter 结构
- [ ] `JudgeWorkerProcessor.VERDICT_PRIORITY` map 及所有 `switch (language)` 在代码库 grep 不到

---

## 7. References (参考)

- 访谈记录: 本次 `/grill-me` 决策树 (2026-06-13)
- Ports & Adapters: Alistair Cockburn, "Hexagonal Architecture" (2005)
- ADR 格式: Michael Nygard, "Documenting Architecture Decisions" (2011)
- 项目相关文档:
  - [CLAUDE.md](../../CLAUDE.md) — 项目运维 + Verification Matrix
  - [docs/CODEMAPS/sandbox.md](../CODEMAPS/sandbox.md) — Sandbox 当前实现
  - [docs/CODEMAPS/architecture.md](../CODEMAPS/architecture.md) — 整体架构
  - [.claude/rules/springboot-rules.md](../../.claude/rules/springboot-rules.md) — 后端编码规则
- 相关代码:
  - `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SandboxServiceImpl.java`
  - `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java`
  - `backend-spring/src/main/java/com/ulticode/modules/queue/service/impl/QueueServiceImpl.java`
  - `backend-spring/src/main/java/com/ulticode/modules/notification/service/NotificationDispatchService.java`
  - `backend-spring/src/main/java/com/ulticode/modules/achievement/service/impl/AchievementTriggerServiceImpl.java` (event-driven 风格参考)
