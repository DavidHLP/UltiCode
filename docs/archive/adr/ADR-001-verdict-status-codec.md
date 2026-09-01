# ADR-001: Verdict / SubmissionStatus Codec 演化 (兼容现有 11 状态)

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Accepted** |
| **接受日期 (Accepted on)** | 2026-06-13 |
| **关闭 Milestone** | M1a (`bf6560679`, `789f85411`) + M1b (`774638e87`) |
| **日期 (Date)** | 2026-06-13 |
| **作者 (Author)** | DavidHLP |
| **解决的 Finding** | [ADR-000 / F1](./ADR-000-hexagonal-grilling-session.md#2-codex-adversarial-review-摘要) |
| **依赖 ADR** | — (本 ADR 是其它 ADR 的前置基础) |
| **被依赖 ADR** | ADR-002 (sandbox)、ADR-003 (queue) 都依赖 verdict 类型 |
| **关联代码** | `backend-spring/src/main/java/com/ulticode/modules/submission/enums/SubmissionStatus.java`,`backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java:61-67` |
| **关联前端** | `console/src/i18n/locales/{en-US,zh-CN}/submission.ts`,`management/src/i18n/locales/{en-US,zh-CN}/modules/submissions.ts` |

---

## 1. Context

### 1.1 现状

`JudgeWorkerProcessor` 用 stringly-typed 的优先级表决定最终 verdict:

```java
private static final Map<String, Integer> VERDICT_PRIORITY = Map.of(
    "Runtime Error", 5,
    "Memory Limit Exceeded", 4,
    "Time Limit Exceeded", 3,
    "Wrong Answer", 2,
    "Presentation Error", 1,
    "Accepted", 0
);
```

后端 `SubmissionStatus` enum 实际**有 11 个值** , 持久化到 `submission.status` 列 (varchar) , 前端 i18n key 对照这些字符串。Codex 评审 (F1) 指出:

- 漏覆盖: `OUTPUT_LIMIT_EXCEEDED`、`SYSTEM_ERROR`、`COMPILE_ERROR`、`SANDBOX_ERROR`、`PENDING`、`JUDGING` 都没有出现在 priority map 中, 命中时 `getOrDefault(0)` 静默把它们当成 "Accepted" 处理。
- 原 ADR 提议引入 `enum Verdict { AC, WA, TLE, ... }` 缩写名 + `toString()` 兼容旧字符串。这是错的: `enum.toString()` 默认返回 `name()` (`"AC"`),而 DB 现存值是 `"Accepted"` , 前端 i18n key 是 `SANDBOX_ERROR` 等 — **三套字符串各不相同**, 单一 enum 无法担任三层契约。

### 1.2 触达层

```
[Java 代码层]    SubmissionStatus 标识符 (PENDING, JUDGING, ACCEPTED, ...)
       │
       ├── [DB 层]        submission.status (varchar, 历史值 "Accepted", "Wrong Answer", ...)
       ├── [API 层]       JSON 响应 status 字段 (与 DB 同字符串)
       └── [前端 i18n]    key (SANDBOX_ERROR / ACCEPTED / ...), 与 API 值需要双向映射
```

三层各有自己的字符串/key 体系, **任何一层改名都需要全链路验证**。

## 2. Decision

### 2.1 分离 "运算" 与 "表达" 两层关注点

| 层 | 形态 | 改造 |
|---|---|---|
| **运算 (Java in-memory)** | `enum SubmissionStatus` (沿用现有 11 值) | 在 enum 上加 `int severity()` 方法承担 priority 角色 |
| **持久化 (DB)** | varchar, 当前值如 `"Accepted"` | **不动** , 历史数据不迁移 |
| **API JSON** | 同 DB | **不动** , 兼容旧客户端 |
| **前端 i18n key** | 当前如 `statusLabels.ACCEPTED` | **不动** , 仅检查覆盖率 |
| **缩写 enum (AC/WA/...)** | (原 ADR 提议) | **永久拒绝** , 见 ADR-000 §5 |

### 2.2 enum 改造

```java
public enum SubmissionStatus {
    PENDING            ("Pending",                "pending",   false, 0, Kind.IN_FLIGHT),
    JUDGING            ("Judging",                "pending",   false, 0, Kind.IN_FLIGHT),
    ACCEPTED           ("Accepted",               "accepted",  true,  0, Kind.TERMINAL_GOOD),
    PRESENTATION_ERROR ("Presentation Error",     "error",     true,  1, Kind.TERMINAL_BAD),
    WRONG_ANSWER       ("Wrong Answer",           "error",     true,  2, Kind.TERMINAL_BAD),
    TIME_LIMIT_EXCEEDED("Time Limit Exceeded",    "error",     true,  3, Kind.TERMINAL_BAD),
    MEMORY_LIMIT_EXCEEDED("Memory Limit Exceeded","error",     true,  4, Kind.TERMINAL_BAD),
    OUTPUT_LIMIT_EXCEEDED("Output Limit Exceeded","error",     true,  4, Kind.TERMINAL_BAD),
    RUNTIME_ERROR      ("Runtime Error",          "error",     true,  5, Kind.TERMINAL_BAD),
    COMPILE_ERROR      ("Compile Error",          "error",     true,  6, Kind.TERMINAL_BAD),  // 不参与 case-level reduce
    SANDBOX_ERROR      ("Sandbox Error",          "system",    true,  7, Kind.TERMINAL_INFRA),
    SYSTEM_ERROR       ("System Error",           "system",    true,  8, Kind.TERMINAL_INFRA);

    public enum Kind { IN_FLIGHT, TERMINAL_GOOD, TERMINAL_BAD, TERMINAL_INFRA }

    private final String displayName;  // ← 持久化/JSON 字符串, 永远是真相 (wire value)
    private final String category;     // ← 粗粒度过滤分类 (pending/accepted/error/system), 供 admin UI
    private final boolean terminal;    // ← 是否终态 (不再自动重判)
    private final int severity;        // ← 越大越严重, ACCEPTED=0
    private final Kind kind;

    SubmissionStatus(String displayName, String category, boolean terminal,
                     int severity, Kind kind) { ... }

    @JsonValue                                                    // Jackson 序列化用 displayName 作为 wire value
    public String wireValue() { return displayName; }

    @JsonCreator                                                  // Jackson 反序列化按 displayName 反查
    public static SubmissionStatus fromWire(String wire) { ... }

    public int severity() { return severity; }
    public Kind kind() { return kind; }
}
```

> **注 (字段名对齐)**: 实际 enum (`SubmissionStatus.java`) 无独立 `wireValue` 字段 —— 持久化/JSON 字符串直接存于 `displayName` 字段, `wireValue()` 是返回该字段的 `@JsonValue` 方法 (line 122-125), `fromWire(String)` 是 `@JsonCreator` 静态工厂 (line 136-147)。此外实际 enum 还携带 ADR 决策伪代码未展开的 `category` / `terminal` 两字段 (admin UI 过滤与终态判定)。对外契约 (wire value 序列化 / fromWire 反序列化 / severity 归约) 一致, 本节伪代码为可读性略作精简, 真值以 `SubmissionStatus.java` 为准。

**关键不变量** (这些是契约, 改动需要新 ADR):

- `displayName` (即 wire value) 字符串**永不改写**;新增状态必须在 ADR 中显式声明并配套迁移
- `name()` (即 `ACCEPTED`)、`ordinal()` 都**不是契约** , 禁止跨进程依赖
- `severity()` 仅在 JVM 内使用 (verdict 归约) , 不持久化, 不发 API

### 2.3 三层 Codec

```java
public final class SubmissionStatusCodec {

    // wireValue → enum (反序列化 / 读 DB)
    public static SubmissionStatus fromWire(String s) {
        return Optional.ofNullable(WIRE_INDEX.get(s))
            .orElseThrow(() -> new IllegalStateException("Unknown status wire value: " + s));
    }

    // enum → wireValue (序列化 / 写 DB) — 与 @JsonValue 一致
    public static String toWire(SubmissionStatus s) { return s.wireValue(); }

    // enum → i18n key (前端调用 t() 用)
    public static String toI18nKey(SubmissionStatus s) {
        return "statusLabels." + s.name();   // ACCEPTED, SANDBOX_ERROR, ...
    }

    private static final Map<String, SubmissionStatus> WIRE_INDEX =
        Arrays.stream(SubmissionStatus.values())
              .collect(toUnmodifiableMap(SubmissionStatus::wireValue, s -> s));
}
```

### 2.4 VerdictResolver 用 EnumMap, 不再 stringly-typed

```java
@Component
public class VerdictResolver {
    /**
     * 多 case 归约: 取 severity 最高的 TERMINAL_BAD/INFRA 状态;
     * 全 ACCEPTED → ACCEPTED;
     * 含 IN_FLIGHT (PENDING/JUDGING) → 抛 IllegalStateException (调用方 bug)。
     */
    public SubmissionStatus reduce(Collection<SubmissionStatus> caseResults) {
        return caseResults.stream()
            .peek(s -> { if (s.kind() == Kind.IN_FLIGHT)
                          throw new IllegalStateException("case still " + s); })
            .max(Comparator.comparingInt(SubmissionStatus::severity))
            .orElseThrow(() -> new IllegalStateException("empty case results"));
    }
}
```

`JudgeWorkerProcessor.VERDICT_PRIORITY` map **删除** 。`JudgeWorkerProcessor.determineVerdict(List<CaseResult>)` 改为 `verdictResolver.reduce(cases.stream().map(c -> SubmissionStatusCodec.fromWire(c.getStatus())).toList())`。

### 2.5 i18n 覆盖率回归测试

新增 `SubmissionStatusI18nCoverageTest` (JUnit) , 启动时:

1. 扫所有 `SubmissionStatus` 值
2. 读 `console/src/i18n/locales/en-US/submission.ts` 和 `zh-CN/submission.ts` (通过 build-time script 输出 JSON)
3. 校验每个 status 在 en + zh 都有对应 `statusLabels.${name()}` key
4. 缺 key 即 fail (compile-time 防御)

### 2.6 不在本 ADR 范围

- **新增 verdict 类型** (例如 `SECURITY_VIOLATION`) → 单独 ADR + DB 数据回填评估
- **改 wireValue 字符串大小写或拼写** → **永久禁止** (除非整库迁移 + 客户端全部升级)
- 状态机转换合法性 (PENDING→JUDGING→TERMINAL) → 见 [ADR-003](./ADR-003-queue-outbox-fencing.md)

## 3. Consequences

### 3.1 Positive

- 一处 `severity()` 改, 所有 case 归约逻辑跟随;停止 `"Runtime Errors"` 拼错回退到 `"Accepted"` 静默 bug
- JSON / DB / i18n 三层契约**显式且独立**, 跨层改名必须走 ADR
- 11 状态全覆盖, 不再静默缺失
- `Kind` 分类支持后续 contest 排名时区分 "TERMINAL_INFRA 不计入用户错误率"
- 无需 DB 迁移, 无需前端改动, 零停机

### 3.2 Negative

- enum 文件膨胀 (8 → 12 状态, 每个带 5 字段 `displayName`/`category`/`terminal`/`severity`/`kind`)
- `wireValue() ≠ name()` 对新人有学习曲线 (例 `"Accepted"` vs `"ACCEPTED"`), **README + javadoc 必须明确**
- I18n coverage test 需要前端 build script 配合输出 JSON, 增加 CI 步骤

### 3.3 Risks

| 风险 | 缓解 |
|---|---|
| 旧代码直接 `submission.status.equals("Accepted")` | grep 全库, 一次性替换为 `submission.statusEnum() == ACCEPTED` (新加 helper 方法) |
| 前端响应类型用 `string` 接 status, IDE 没补全 | TS 加 `type SubmissionStatusWire = "Accepted" \| "Wrong Answer" \| ...`,从 codegen 生成 |
| 反序列化时遇到未知 wireValue (例如其它服务投递的) | `fromWire` 抛 `IllegalStateException` , 全局 ExceptionHandler 转 `Result.error(UNKNOWN_STATUS)` |

## 4. Validation

- [ ] `VerdictResolver` 覆盖 100% case (单测含 single-case / multi-case / 全 ACCEPTED / 含 IN_FLIGHT 抛错 / TERMINAL_INFRA 优先于 TERMINAL_BAD)
- [ ] `SubmissionStatusCodec.fromWire` round-trip 测试: 对每个 enum, `fromWire(toWire(s)) == s`
- [ ] `SubmissionStatusI18nCoverageTest` 在 CI 通过
- [ ] grep 确认 `JudgeWorkerProcessor.VERDICT_PRIORITY` 在代码库消失
- [ ] grep 确认 `"Accepted".equals(...)` 类 stringly-typed 比较为零 (允许 wireValue 比较仅在 codec 内部)

## 5. References

- [ADR-000](./ADR-000-hexagonal-grilling-session.md) — 拆分溯源, F1 原文
- 现有代码: `backend-spring/.../submission/enums/SubmissionStatus.java` (11 状态枚举源)
- 现有代码: `backend-spring/.../queue/processor/JudgeWorkerProcessor.java:61-67` (待删 priority map)
- 前端 i18n: `console/src/i18n/locales/{en-US,zh-CN}/submission.ts`,`management/src/i18n/locales/{en-US,zh-CN}/modules/submissions.ts`
- 项目规约: `.claude/rules/backend/07-java-design.md` (#3 状态图、#8 单一原则)
