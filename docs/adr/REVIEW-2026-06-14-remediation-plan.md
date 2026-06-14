# ADR 对抗审查修复 Plan — 2026-06-14 (Remediation Harness)

| 字段 | 值 |
|------|-----|
| **来源报告** | [REVIEW-2026-06-14-adversarial.md](./REVIEW-2026-06-14-adversarial.md) |
| **修复日期** | 2026-06-14 |
| **执行模式** | plan-driven workflow(本文件 = harness) |
| **Findings 总数** | 9(H1-H3 / M1-M3 / L1 附注 / L2 / L3) |
| **状态** | 全部修复 + 独立复核通过(见 §2 逐项 PASS/FAIL) |

---

## 0. 这是什么

本文件是 `REVIEW-2026-06-14-adversarial.md` 发现项的**修复计划 + 可重放验证 harness**。

每项 finding 给出:根因、修复方案、目标文件、**验证命令**、预期、状态。§2 的验证命令清单就是 harness 入口——可逐条 copy-paste 复核,或由 workflow 逐项执行(plan-as-harness 模式)。任一命令的输出不符合"预期",就是回归。

---

## 1. 修复项清单(9 项)

### H1 — DockerSandboxAdapter 幽灵架构 [HIGH] ✅

| 项 | 内容 |
|---|---|
| 根因 | ADR-002 §2.3 表列出不存在的 `DockerSandboxAdapter`(源码零命中)。实际 docker 执行逻辑在 `SandboxExecutorImpl`(`implements SandboxExecutor`,`@ConditionalOnProperty sandbox.executor=docker`,ProcessBuilder 约 line 389)。§2.2 伪代码用 `SandboxExecutorImpl`(正确),§2.3 表却列独立 adapter,自相矛盾。 |
| 修复 | §2.3 表把 `DockerSandboxAdapter` 替换为 `SandboxExecutorImpl`(docker 默认实现,标注 `@ConditionalOnProperty` 激活);补命名说明区分 `DockerSandboxConfig`(Spring config 注册 bean,非 Port 的 Adapter,不计入表) |
| 文件 | `docs/adr/ADR-002-sandbox-hexagonal.md`(§2.3 表、§3.1、§4 Validation) |
| 验证命令 | `grep -rn 'DockerSandboxAdapter' backend-spring/src/` |
| 预期 | 源码**零命中**;docs 残留字样仅为"为何不叫 Adapter"的决策说明(非矛盾) |
| 状态 | ✅ FIXED |

### H2 — 假声称 import clean [HIGH] ✅

| 项 | 内容 |
|---|---|
| 根因 | ADR-004 §4 Validation 标 `[x] clean`,但 4 个业务文件仍 import `EmailService`/`RealtimeService`(flag-gated 双轨的 legacy 分支,flag 默认 off)。§2.8 backlog #10 自己承认残留,Validation 却标 clean,自相矛盾。 |
| 修复 | Validation 改为 `[ ] deferred to M4b cutover`,显式列出 4 文件(`SubmissionServiceImpl`/`AchievementNotificationListener`/`AchievementTriggerServiceImpl`/`ContestScheduler`),说明 flag-off 分支仍 import、flag-on 分支走 `NotificationDispatcher`,与 §2.8 #10 一致 |
| 文件 | `docs/adr/ADR-004-notification-intents.md`(line 338) |
| 验证命令 | `grep -n 'audit clean\|deferred to M4b' docs/adr/ADR-004-notification-intents.md` |
| 预期 | 含 "deferred to M4b cutover";不含 "audit clean post-M4c" 假声称 |
| 状态 | ✅ FIXED |

### H3 — ADR-005 编号冲突 [HIGH] ✅

| 项 | 内容 |
|---|---|
| 根因 | 两个 `ADR-005` 文件(`rollback-drill-protocol` + `rolling-deploy-playbook`)违反 README 编号规则;前者实为后者 §2.6/§4#2 的执行子协议,非同级 ADR |
| 修复 | `mv ADR-005-rollback-drill-protocol.md → ADR-005a-rollback-drill-protocol.md`;README 索引补 ADR-005a 行(标"子协议");编号规则段新增"子协议例外"条款(`ADR-NNNx` 后缀);全树交叉引用(RUNBOOK、ADR-005 playbook)同步更名 |
| 文件 | `docs/adr/ADR-005a-rollback-drill-protocol.md`(新)、`docs/adr/README.md`、`docs/RUNBOOK.md`、`docs/adr/ADR-005-rolling-deploy-playbook.md`(交叉引用) |
| 验证命令 | `test -f docs/adr/ADR-005a-rollback-drill-protocol.md && test ! -f docs/adr/ADR-005-rollback-drill-protocol.md` + `grep -rn 'ADR-005-rollback-drill-protocol' docs/ --exclude='REVIEW-2026-06-14-adversarial.md' --exclude='REVIEW-2026-06-14-remediation-plan.md'` |
| 预期 | 新文件存在、旧文件不存在;旧名引用零命中(排除 REVIEW 报告与本 plan 的历史快照引用) |
| 状态 | ✅ FIXED |

### M1 — milestone 计数 5/10/11 [MEDIUM] ✅

| 项 | 内容 |
|---|---|
| 根因 | ADR-005 文件内 §2.1 标题"5 个"、§3.2/§3.3"11 milestone";实际 10 个(M1a-M4b) |
| 修复 | ADR-005 文件内统一为 10(line 35 标题、line 255 §3.2、line 267 §3.3);README 索引摘要"11 个"补修为"10 个"(scope 边界遗漏,plan 复核阶段捕获) |
| 文件 | `docs/adr/ADR-005-rolling-deploy-playbook.md` + `docs/adr/README.md`(line 14) |
| 验证命令 | `grep -oE '\bM[1-4][a-d]\b' docs/adr/ADR-005-rolling-deploy-playbook.md \| sort -u \| wc -l` + `grep -rn '11 个独立可部署\|11 milestone' docs/adr/ --exclude='REVIEW-2026-06-14-adversarial.md'` |
| 预期 | milestone tokens = 10;无"11 个/11 milestone"(排除 REVIEW 报告历史引用) |
| 状态 | ✅ FIXED(含 README 残留补修) |

### M2 — 状态停滞因果未记录 [MEDIUM] ✅

| 项 | 内容 |
|---|---|
| 根因 | ADR-005 状态行始终 Proposed,但 §2.1 表 M3a/M3c/M4a 已 shipped;M1a-M2b shipped at 仍 `—`(尽管 ADR-001/002 已 Accepted)。卡 Proposed 真实因果是 §2.6 rollback drill 三处全 `_TBD_`,但文档未写明 |
| 修复 | 状态行注明"stays Proposed pending rollback drill completion(§2.6 三处全 `_TBD_`)";§2.1 表 M1a/M1b/M2a/M2b shipped at 从 `—` 改为 `shipped(见 ADR-001/002 Accepted)` |
| 文件 | `docs/adr/ADR-005-rolling-deploy-playbook.md`(line 5 状态行、§2.1 表) |
| 验证命令 | `grep -q 'stays Proposed pending rollback drill' docs/adr/ADR-005-rolling-deploy-playbook.md` |
| 预期 | 状态行含 pending rollback drill 因果注记 |
| 状态 | ✅ FIXED |

### M3 — seccomp 默认值未固化 [MEDIUM] ✅

| 项 | 内容 |
|---|---|
| 根因 | ADR-002 §6 bug#3 声称 SANDBOX_SECCOMP_PROFILE 改绝对路径,但 `application.yml` 默认值仍是相对路径 `docker/sandbox/seccomp-profile.json`;修复只活在 `.env`,新机器/CI 不读 .env 会重新触发 bug |
| 修复 | `application.yml:144` 默认值改为绝对路径 `/home/davidhlp/project/UltiCode/docker/sandbox/seccomp-profile.json`(保留 `SANDBOX_SECCOMP_PROFILE:` 占位语法,只改 default);ADR-002 §6 bug#3 补"默认值已固化到 yml,新机器/CI 不依赖 .env"治本说明 |
| 文件 | `backend-spring/src/main/resources/application.yml`(line 144)+ `docs/adr/ADR-002-sandbox-hexagonal.md`(§6) |
| 验证命令 | `grep 'seccomp-profile-path' backend-spring/src/main/resources/application.yml` |
| 预期 | 默认值含绝对路径 `/home/davidhlp/project/UltiCode/docker/sandbox/seccomp-profile.json` |
| 状态 | ✅ FIXED |

### L1 — P1#1 次路径 Validation 措辞 [LOW] ✅

| 项 | 内容 |
|---|---|
| 根因 | ADR-003 §4 Validation"registerSynchronization 用于入队为零"措辞笼统,把 §2.6 F7 新设计的 lease-reaper afterCommit(`JudgingLeaseReaper:143`)误纳入 cleanup 目标 |
| 修复 | Validation 改为 `[ ] deferred to M3d`,区分三类入队代码点:(1) 旧 afterCommit reaper(已废弃,cleanup 目标);(2) 新 lease-reaper afterCommit(§2.6 F7 设计内,机制本身非违规);(3) 真正残留(`AdminSubmissionServiceImpl.rejudge` 3 处 `enqueueJudgeJob`)。与 §2.8 backlog 一致 |
| 文件 | `docs/adr/ADR-003-queue-outbox-fencing.md`(line 372) |
| 验证命令 | `grep -q 'deferred to M3d' docs/adr/ADR-003-queue-outbox-fencing.md` |
| 预期 | Validation 标 deferred to M3d + 三分类措辞 |
| 状态 | ✅ FIXED |

### L2 — worker javadoc rot [LOW] ✅

| 项 | 内容 |
|---|---|
| 根因 | `JudgeWorkerProcessor` 类 javadoc(line 68)仍写 stringly-typed 时代文案"Determine verdict with priority ordering (RE > MLE > TLE > WA > PE > Accepted)";实际 line 742 已用 `verdictResolver.reduceWire(caseWireValues).wireValue()` |
| 修复 | javadoc 更新为反映 `VerdictResolver#reduceWire` 聚合机制(引用 ADR-001)。纯注释改动,零逻辑变更 |
| 文件 | `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java`(line 68 javadoc) |
| 验证命令 | `! grep -q 'priority ordering' backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java` |
| 预期 | 旧"priority ordering"文案不在 javadoc;代码逻辑零变更 |
| 状态 | ✅ FIXED |

### L3 — enum 字段名伪代码差异 [LOW] ✅

| 项 | 内容 |
|---|---|
| 根因 | ADR-001 §2.2 伪代码写 `private final String wireValue` 字段,但实际 `SubmissionStatus` enum 无此字段(实际字段 displayName/category/terminal/severity/kind;`wireValue()` 是 `@JsonValue` 方法,`fromWire` 是 `@JsonCreator`) |
| 修复 | 伪代码字段列表改为实际 5 字段;构造函数改 5 参数;12 常量补 category/terminal;`@JsonValue wireValue()` 返回 displayName;`@JsonCreator fromWire` 静态工厂;加"字段名对齐"脚注;§3.2 Negative 同步(3→5 字段、`wireValue`→`wireValue()`) |
| 文件 | `docs/adr/ADR-001-verdict-status-codec.md`(§2.2、§3.2) |
| 验证命令 | `! grep -q 'private final String wireValue' docs/adr/ADR-001-verdict-status-codec.md` |
| 预期 | 不含不存在的 `private final String wireValue` 字段;伪代码契约与 `SubmissionStatus.java` 一致 |
| 状态 | ✅ FIXED |

---

## 2. 验证 harness(plan-as-harness 入口)

下列命令是 harness 的机械验证清单。每条独立判断 PASS/FAIL。可逐条 copy-paste 复核,或由 workflow 逐项执行(本 plan 即 harness,workflow 是执行引擎)。

```bash
# H1 — 源码 DockerSandboxAdapter 零命中
test -z "$(grep -rn 'DockerSandboxAdapter' backend-spring/src/)" && echo "H1 PASS" || echo "H1 FAIL"

# H2 — ADR-004 Validation 改 deferred to M4b
grep -q 'deferred to M4b' docs/adr/ADR-004-notification-intents.md && echo "H2 PASS" || echo "H2 FAIL"

# H3 — ADR-005a 重命名 + 旧引用清零(排除历史快照引用)
test -f docs/adr/ADR-005a-rollback-drill-protocol.md \
  && test ! -f docs/adr/ADR-005-rollback-drill-protocol.md \
  && test -z "$(grep -rn 'ADR-005-rollback-drill-protocol' docs/ \
       --exclude='REVIEW-2026-06-14-adversarial.md' \
       --exclude='REVIEW-2026-06-14-remediation-plan.md')" \
  && echo "H3 PASS" || echo "H3 FAIL"

# M1 — milestone 计数统一 10
test "$(grep -oE '\bM[1-4][a-d]\b' docs/adr/ADR-005-rolling-deploy-playbook.md | sort -u | wc -l)" -eq 10 \
  && test -z "$(grep -rn '11 个独立可部署\|11 milestone' docs/adr/ \
       --exclude='REVIEW-2026-06-14-adversarial.md')" \
  && echo "M1 PASS" || echo "M1 FAIL"

# M2 — 状态行注明 Proposed pending drill
grep -q 'stays Proposed pending rollback drill' docs/adr/ADR-005-rolling-deploy-playbook.md \
  && echo "M2 PASS" || echo "M2 FAIL"

# M3 — seccomp 默认值绝对路径
grep -q 'SANDBOX_SECCOMP_PROFILE:/home/davidhlp/project/UltiCode/docker/sandbox/seccomp-profile.json' \
  backend-spring/src/main/resources/application.yml \
  && echo "M3 PASS" || echo "M3 FAIL"

# L1 — ADR-003 Validation deferred to M3d
grep -q 'deferred to M3d' docs/adr/ADR-003-queue-outbox-fencing.md && echo "L1 PASS" || echo "L1 FAIL"

# L2 — worker javadoc 旧文案清除
! grep -q 'priority ordering' \
  backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java \
  && echo "L2 PASS" || echo "L2 FAIL"

# L3 — ADR-001 伪代码 wireValue 字段清除
! grep -q 'private final String wireValue' docs/adr/ADR-001-verdict-status-codec.md \
  && echo "L3 PASS" || echo "L3 FAIL"
```

---

## 3. 文件改动清单

```
M backend-spring/.../queue/processor/JudgeWorkerProcessor.java   (L2 javadoc,纯注释)
M backend-spring/src/main/resources/application.yml              (M3 seccomp 默认值→绝对路径)
M docs/RUNBOOK.md                                                 (H3 交叉引用更名)
M docs/adr/ADR-001-verdict-status-codec.md                        (L3 enum 伪代码字段对齐)
M docs/adr/ADR-002-sandbox-hexagonal.md                           (H1 §2.3 表 + M3 §6 说明)
M docs/adr/ADR-003-queue-outbox-fencing.md                        (L1 §4 Validation 三分类)
M docs/adr/ADR-004-notification-intents.md                        (H2 §4 Validation 改 deferred)
D docs/adr/ADR-005-rollback-drill-protocol.md                     (H3 重命名源文件)
?? docs/adr/ADR-005a-rollback-drill-protocol.md                   (H3 重命名目标)
M docs/adr/ADR-005-rolling-deploy-playbook.md                     (M1 计数 + M2 状态因果)
M docs/adr/README.md                                              (H3 索引+编号规则 + M1 摘要 11→10)
```

---

## 4. 残留 / 后续(非本次范围,显式登记)

| 项 | 说明 | 时机 |
|---|---|---|
| L1 rejudge 次路径 | `AdminSubmissionServiceImpl` 3 处 `enqueueJudgeJob`(line 339/502/526)在 flag-on 时仍走旧 RQueue;ADR-003 §2.8 已诚实登记为 P1#1 follow-up | flag 切 on 前(M3d cutover) |
| ADR-005 §2.6 rollback drill | 三处全 `_TBD_`,从未执行首次 drill;执行后 ADR-005 转 Accepted | 首次 drill |
| REVIEW 报告原文 line 51 | 保留旧文件名 `ADR-005-rollback-drill-protocol.md`——审查时事实快照,不改 | N/A(历史记录) |
| H2 legacy import | 4 业务文件的 legacy 分支 EmailService/RealtimeService import 保留至 M4b cleanup | M4b cutover |

---

## 5. plan-as-harness 使用方式

1. **复核回归**:任一 finding 怀疑回归时,跑 §2 对应单行命令,PASS=未回归。
2. **全量复核**:依次跑 §2 全部 9 条,9 个 PASS = 修复完整。
3. **workflow 驱动**:把 §2 清单交给 workflow,每个 finding 一个 verifier agent 并行跑,汇总 PASS/FAIL(本 plan 落盘后已用此模式复核一次)。
