# ADR 对抗性审查报告 — 2026-06-14

| 字段 | 值 |
|------|-----|
| **审查类型** | Adversarial Review — ADR 文档 vs 实际 OJ 代码 |
| **审查日期** | 2026-06-14 |
| **审查者** | Claude Code (对抗审查模式) |
| **审查范围** | `docs/adr/` 全部 8 文件(README + ADR-000 ~ ADR-005,含 rollback-drill-protocol)vs `backend-spring/`、`docker/sandbox/harness/`、`console/`、`management/`、`init-db/migrations/` |
| **审查方法** | 符号存在性(codegraph + find/grep)+ ADR Validation 断言反验 + Flyway 迁移核对 + 文档内部一致性交叉 + 核心机制"真实现 vs stub"判定 |
| **Verdict** | **ADR 工程实质扎实**(核心机制全部真实现,非纸上架构);**文档维护明显滞后**,3 个 HIGH 级文档一致性缺陷会误导后来者 |

---

## 0. 执行摘要

ADR 体系经多轮 Codex 对抗审查,F1–F14 finding 大部分真闭环。**所有声称的核心架构机制在代码里真实存在且行为正确**(见 §5 正向清单)。问题不在"架构失败",而在"文档说了一套,代码做了另一套,且没人回头同步"——幽灵符号、假声称的 Validation、编号/计数矛盾。

**关键区分**:
- 🔴 HIGH = 文档与实现**结构性不符**,或 Validation **假声称已满足**,直接误导审查者/新人
- 🟡 MEDIUM = 文档精确性问题、计数矛盾、修复未固化
- 🟢 LOW = 文档已诚实承认的残留 / 注释 rot / 伪代码细节差异

---

## 1. 🔴 HIGH 发现

### H1. ADR-002 §2.3 的 `DockerSandboxAdapter` 是幽灵架构

| 项 | 内容 |
|---|---|
| **文档声称** | ADR-002 §2.3 表:`DockerSandboxAdapter` \| 生产 — 现 `SandboxServiceImpl` 重构迁入 \| ✅ 本次落地 |
| **实际代码** | `find`/`grep` 全树搜 `DockerSandboxAdapter` —— **零命中**。docker 逻辑实际在 `backend-spring/src/main/java/com/ulticode/modules/submission/sandbox/executor/SandboxExecutorImpl.java`(`implements SandboxExecutor`,带 `@ConditionalOnProperty(sandbox.executor=docker)`,`ProcessBuilder` 在 line 389) |
| **文档内部矛盾** | §2.2 伪代码用 `SandboxExecutorImpl implements SandboxExecutor`(与实现一致);§2.3 表却列出独立的 `DockerSandboxAdapter` —— 两节自相打架 |
| **影响** | 按 §2.3 找 adapter 找不到;Hexagonal "Port + 双 Adapter" 实际是"Executor(docker) + Adapter(in-memory)"的不对称命名,新人理解成本上升 |
| **建议** | 二选一:① 改 §2.3 表,把 `DockerSandboxAdapter` 替换为 `SandboxExecutorImpl`(docker 默认实现);② 若真要 adapter 对称,把 docker 逻辑从 Executor 抽成 `DockerSandboxAdapter`。推荐①(成本低,实现已工作) |

### H2. ADR-004 §4 Validation "业务模块不再 import EmailService/RealtimeService (clean)" 是假声称

| 项 | 内容 |
|---|---|
| **文档声称** | ADR-004 §4:`[x] grep 确认业务模块不再直接 import EmailService / RealtimeService — git grep audit clean post-M4c` |
| **实际代码** | 4 个业务文件**仍 import**:`submission/service/impl/SubmissionServiceImpl.java`、`achievement/listener/AchievementNotificationListener.java`、`achievement/service/impl/AchievementTriggerServiceImpl.java`、`contest/scheduler/ContestScheduler.java` |
| **文档自相矛盾** | ADR-004 §2.8 backlog #10 自己写"`SubmissionServiceImpl` ×2 + `AchievementNotificationListener` + `FollowServiceImpl` + `ContestScheduler` 各有 ~20 行 `if (featureFlags.isUseNotificationIntent()) { typed } else { legacy }`" —— **backlog 承认残留,Validation 却标 clean** |
| **实质** | 这些是 flag-gated 双轨的 legacy 分支,flag 默认 off 时走 legacy(需 EmailService/RealtimeService);"clean" 是 M4b cleanup 后才成立,当前不成立 |
| **建议** | 把该 Validation 改为 `[ ] deferred to M4b cutover`,或注明"channel 实现外的业务模块在 flag-on 分支不再 import;legacy 分支保留至 cleanup" |

### H3. ADR-005 编号冲突,违反 README 自定规则

| 项 | 内容 |
|---|---|
| **事实** | 两个 `ADR-005`:`ADR-005-rollback-drill-protocol.md` + `ADR-005-rolling-deploy-playbook.md` |
| **违规** | README §编号规则 line 19:"ADR-001+ 按提议时间顺序编号,**不补缺,不复用**(即使被 supersede 也保留编号)" |
| **实质** | rollback-drill-protocol 文件自己声明"本协议是 ADR-005 §2.6 与 §4 #2 的执行规范" —— 它是 playbook 的**子协议**,不是同级 ADR |
| **索引缺失** | README 索引表只列 rolling-deploy-playbook,不提 rollback-drill-protocol,内部不一致 |
| **建议** | 重命名为 `ADR-005a-rollback-drill-protocol.md` 或并入 playbook 附录;README 索引补一行 |

---

## 2. 🟡 MEDIUM 发现

### M1. ADR-005 milestone 计数三处打架(5 / 10 / 11)

- §2.1 标题:"M1→M5 共 **5** 个独立可部署单元"
- §2.1 表实际列:**10** 个(M1a M1b M2a M2b M3a M3b M3c M3d M4a M4b)
- §3.2 Negative:"Milestone 从 3 个膨胀到 **11** 个 (M1a/.../M4b)" —— 数 11 但括号只列 10 个
- §3.3 / README / ADR-000 全引用"**11** 个 milestone"
- 第 11 个是**幽灵数字**(草稿遗留,从未具名)。建议统一改为 10。

### M2. ADR-005 状态停滞(Proposed)与 milestone 已 shipped 矛盾,因果未记录

- ADR-005 状态始终 `Proposed`,但 §2.1 表 M3a/M3b/M3c 都标了 shipped at + commit(`09c97d1b8` / `b34ac01be` / `3e8504f1b` / `3ec758c41`)
- M1a/M1b/M2a/M2b 的 shipped at 列仍是 `—`,尽管 ADR-001/002 已因这些 milestone 转 Accepted
- README §Status 转换规则:Proposed→Accepted 当 milestone merged —— 按此 ADR-005 早该评估升级
- 卡 Proposed 的真实原因应是 §2.6 rollback drill **3 处全 `_TBD_`(从未跑过)**,但文档没把这层因果写明,看起来像遗忘
- **建议**:要么补跑 drill 后转 Accepted,要么在状态行注明"stays Proposed pending rollback drill completion (§2.6 全 TBD)"

### M3. ADR-002 §6 bug#3 seccomp 绝对路径修复未固化到 yml 默认值

| 项 | 内容 |
|---|---|
| **文档声称** | §6 bug#3:`SANDBOX_SECCOMP_PROFILE` 相对路径 → docker volume 拒绝 → 修复为绝对路径 |
| **实际配置** | `application.yml:144` 默认值仍是**相对路径** `${SANDBOX_SECCOMP_PROFILE:docker/sandbox/seccomp-profile.json}`;`.env` 里 `SANDBOX_SECCOMP_PROFILE` 有覆盖(绝对路径)兜底 |
| **风险** | 新机器 / CI / 不读 .env 的环境会走默认值,重新触发 bug#3。文档说"修复"但修复只活在 .env,yml 默认值仍是 bug 源 —— 治标不治本 |
| **建议** | 把 yml 默认值改为绝对路径,或在启动校验里拒绝相对路径(fail-fast) |

---

## 3. 🟢 LOW 发现

### L1. ADR-003 P1#1 次路径残留(文档已诚实承认)

- `AdminSubmissionServiceImpl` 有 4 处 `queueService.enqueueJudgeJob`(line 339/502/526 + 注释 491),rejudge 路径在 flag-on 时仍走旧 RQueue
- ADR-003 §2.8 明确标"AdminSubmissionServiceImpl.rejudge 3 处 + JudgingLeaseReaper 2 处 afterCommit 路径:P1 #1 次路径...留 follow-up" —— **文档诚实承认,不算隐瞒**
- flag 默认 off 当前无害,但 cutover 时是双投递暴露面
- **附注**:§4 Validation "registerSynchronization 用于入队为零"措辞需澄清 —— `JudgingLeaseReaper:143` 的 registerSynchronization 是 §2.6 F7 **新设计**(H1 fix afterCommit enqueue),不算违规;真正残留只有 rejudge 那处。建议把该 Validation 标 `deferred to M3d` 并区分"旧 afterCommit reaper"(已废弃)vs"新 lease-reaper afterCommit"(设计内)

### L2. worker javadoc rot

- `JudgeWorkerProcessor` 类注释仍写 "Determine verdict with priority ordering (RE > MLE > TLE > WA > PE > Accepted)" —— stringly-typed 时代文案
- 实际 line 742 已改用 `verdictResolver.reduceWire(caseWireValues).wireValue()`

### L3. enum 字段名伪代码差异

- ADR-001 §2.2 伪代码 `private final String wireValue`,实际 enum 该字段名检测不到(但 `wireValue()` 方法、`@JsonValue`、`fromWire` 都在)
- 功能契约满足,仅细节差异,不影响行为

---

## 4. 已确认的文档自相矛盾点(汇总)

| # | 矛盾对 | 位置 |
|---|---|---|
| 1 | §2.2 `SandboxExecutorImpl` vs §2.3 `DockerSandboxAdapter` | ADR-002 |
| 2 | §4 Validation "import clean [x]" vs §2.8 backlog #10 "4 处 flag-gated 重复" | ADR-004 |
| 3 | §2.1 "5 个" vs 表"10 个" vs §3.2 "11 个" | ADR-005 |
| 4 | §2.1 表 M1a/M1b/M2a/M2b shipped at=`—` vs ADR-001/002 已 Accepted | ADR-005 ↔ ADR-001/002 |
| 5 | README "不复用编号" vs 两个 ADR-005 文件 | README ↔ ADR-005* |
| 6 | README 索引只列 rolling-deploy vs 实际两个 ADR-005 | README |

---

## 5. ✅ 正向确认(核心机制真实现,非 stub)

| ADR | 声称 | 代码验证 |
|---|---|---|
| 001 | enum 12 值 + Kind + @JsonValue/@JsonCreator + Codec + VerdictResolver + I18n test | `SubmissionStatus` 12 常量齐全、Kind 内嵌、注解齐全、`SubmissionStatusCodec`+`VerdictResolver`+`SubmissionStatusI18nCoverageTest` 全在 ✅ |
| 001 | `VERDICT_PRIORITY` 删除 | worker 改注 `verdictResolver` + `reduceWire()`(line 742);map 仅存于注释 ✅ |
| 001 | 前端 i18n 11 状态全覆盖 | console+management 4 文件全有 SANDBOX_ERROR/OUTPUT_LIMIT_EXCEEDED/SYSTEM_ERROR/COMPILE_ERROR ✅ |
| 002 | Port + 5 LanguageProfile + InMemoryAdapter + SandboxServiceImpl 删除 + switch(language)=0 | sandbox/ 15 文件齐全、5 profile、SandboxServiceImpl 真删、switch 零命中 ✅ |
| 002 §7 | build.sh tag :latest + harness peak_memory_bytes + isListLike | build.sh 有 docker build/tag :latest/--no-docker/exit 2;Main.java 有 `peakMemoryBytes()`+`peak_memory_bytes` envelope;Harness.java 有 `isListLike` ✅ |
| 003 | Streams adapter(XREADGROUP/XACK/XCLAIM/XPENDING) | `RedissonStreamsJudgeQueueAdapter` 全用 RStream + `ack()`/`nack()`,F6 修订真落地 ✅ |
| 003 | outbox shadow + real dispatch 双路径 + `is_shadow` fence | `JudgeOutboxDispatcher` M3a shadow path(observe only)+ M3c-2 `claimRealDispatch`(`is_shadow=0` filter)✅ |
| 003 | Submission 加 generation/current_attempt_id/judging_lease_expires_at | entity line 82/92/102 三列俱全 ✅ |
| 003 | M3d 未做(旧 enqueueJudgeJob + RQueue 保留) | `QueueService.enqueueJudgeJob` 仍在、`QueueConfig` RQueue bean 仍在 —— 与"≥2 周后 cleanup"一致 ✅ |
| 004 | sealed NotificationIntent + 6 intent + 3 channel + dispatcher + ledger reaper | `sealed interface ... permits`、6 intent record、3 channel、`NotificationLedgerReaper`、`@Deprecated` 保留(符合 M4d-1 状态)✅ |
| 005 | feature flags 默认 false + profile yml + scripts | yml `use-judge-outbox/use-generation-fence/use-notification-intent` 默认 false;`application-features-{off,on}.yml` 存在;`scripts/adr-005/create-milestone-issues.sh` 存在 ✅ |
| Flyway | outbox + lease + ledger 3 迁移 | `V20260613100000`/`110000`/`120000` 三文件齐全 ✅ |

---

## 6. 修复优先级

1. **立即修**(误导性,低成本,纯改文档文字):H1(DockerSandboxAdapter 表)、H2(假声称 Validation)、M1(计数 5/10/11)、L2(javadoc rot)
2. **本周修**:H3(编号冲突,涉及文件改名 + README)、M2(ADR-005 状态因果注明)、M3(seccomp yml 默认值)
3. **cutover 前修**:L1(P1#1 rejudge 次路径) —— flag 切 on 前必须清,否则双投递

---

## 7. 证据索引(可复验命令)

```bash
# H1: DockerSandboxAdapter ghost
find backend-spring/src/main/java -iname '*DockerSandbox*'
grep -rln 'DockerSandboxAdapter' backend-spring/src/   # both empty

# H2: business modules still import EmailService/RealtimeService
grep -rln 'EmailService\|RealtimeService' \
  backend-spring/src/main/java/com/ulticode/modules/{submission,achievement,contest,follow}

# H3: ADR-005 number collision
ls docs/adr/ | grep 'ADR-005'

# M1: milestone count
grep -oE '\bM[1-4][a-d]\b' docs/adr/ADR-005-rolling-deploy-playbook.md | sort -u

# M2: rollback drill all TBD
grep -cE '_TBD_' docs/adr/ADR-005-rolling-deploy-playbook.md   # = 3

# M3: seccomp default relative
grep -n 'seccomp' backend-spring/src/main/resources/application.yml

# L1: P1#1 residual enqueueJudgeJob
grep -nE 'enqueueJudgeJob' backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java

# Positive: Streams adapter
grep -nE 'XREADGROUP|XACK|XCLAIM|getStream' \
  backend-spring/src/main/java/com/ulticode/modules/queue/port/adapter/RedissonStreamsJudgeQueueAdapter.java

# Positive: sealed intent
grep -nE 'sealed interface NotificationIntent|permits' \
  backend-spring/src/main/java/com/ulticode/modules/notification/intent/NotificationIntent.java
```

---

## 8. References

- 审查对象:`docs/adr/README.md`、`ADR-000` ~ `ADR-005`(含 rollback-drill-protocol)
- 相关 ADR 修订记录:ADR-002 §6/§7(实战 bug + follow-up)、ADR-003 §2.8(codex round-3)、ADR-004 §2.8(M4d-1 review)
- 项目规约:`.claude/rules/backend/07-java-design.md`(单一原则、开闭原则、设计沉淀)
- 后续动作:本报告发现项可拆为 issue 跟踪,或在对应 ADR §"Review" 章节就地补丁
