# ADR-000: Hexagonal 化设计访谈与对抗评审记录 (Meta + Superseded)

| 字段 | 值 |
|------|-----|
| **状态 (Status)** | **Superseded by ADR-001 / ADR-002 / ADR-003 / ADR-004 / ADR-005** |
| **日期 (Date)** | 2026-06-13 |
| **作者 (Author)** | DavidHLP |
| **保留理由** | 记录访谈过程与 Codex 对抗评审发现, 防止后人重新提议被否决的方案 |
| **承接 ADR** | [ADR-001](./ADR-001-verdict-status-codec.md) · [ADR-002](./ADR-002-sandbox-hexagonal.md) · [ADR-003](./ADR-003-queue-outbox-fencing.md) · [ADR-004](./ADR-004-notification-intents.md) · [ADR-005](./ADR-005-rolling-deploy-playbook.md) |

---

## 1. 缘起

2026-06-13 通过 `/grill-me` skill 走完一次 **"OJ 设计模式现状 → 推荐改造"** 的决策树访谈, 产出**单一**大 ADR (原 ADR-001) 覆盖:

- Sandbox / Queue / Notification 三处同时 Hexagonal 化
- LanguageProfile Strategy 注册
- Verdict 字符串改 enum
- SubmissionStatus + ALLOWED transitions + CAS
- afterCommit + Reaper 替代 Outbox
- Notification 单一 Orchestrator + Channel 集合
- M1 → M2 → M3 4-6 周串行 worktree rollout

## 2. Codex Adversarial Review (摘要)

随即跑了 `/codex:adversarial-review --base main --background ...` , Codex verdict: **`needs-attention`** , 5 条 finding (3 critical + 2 high):

| # | Finding 摘要 | Severity |
|---|---|---|
| **F1** | Verdict enum 用 `AC/WA/TLE` 缩写 → 破坏现有 11 状态 + DB 持久化 + 前端 i18n; `toString()/ordinal()` 不是兼容契约; 缺 `OUTPUT_LIMIT_EXCEEDED` / `SYSTEM_ERROR` | critical |
| **F2** | 终态在 `ALLOWED` 表无后继 → admin rejudge `Accepted → Pending` 被拒; worker crash → JUDGING 永久卡死 (reaper 只扫 PENDING); 无 generation token → 旧 worker 覆盖新 rejudge 结果 | critical |
| **F3** | afterCommit 前进程崩溃 → 立即送达丢失; Redis 写超时模糊 → reaper 重复入队 (RQueue 不自动去重); `created_at` 用 Java 时钟而非 DB 时钟, 时钟偏移破坏 5min 窗口 | critical |
| **F4** | Orchestrator 必须懂每个 event; 泛型 envelope 装不下 email 模板 / contest 排名 / link / metadata; `NotificationPreference` 是 category-only 不支持 channel-level; `supports()` 声明未用; 单 channel 异常中断后续 channel | high |
| **F5** | M1→M2→M3 4-6 周新旧并存却**无 feature flag / 无 dual-read / 无 envelope versioning / 无 rollback test** ; M2 把队列表示+状态+入队时机+恢复机制打一包不可回滚 | high |

> **完整 review 文本** 见 git history `git show e34e4efbd~1..` 之后该分支的 Codex 评审 stdout (临时文件 `/tmp/claude-1000/.../tasks/byjvklvfm.output` , session 结束即销毁, 本文档为永久存证) 。

## 3. 裁决 (5 条全部采纳)

经评估, **5 条 finding 没有任何一条值得反驳** 。原 ADR-001 因下列结构性缺陷不应 approve:

- 用 "未看现有 SubmissionStatus enum / NotificationPreference 表 / RQueue codec / admin rejudge 流程" 的快速摸底就下了模式决策
- 把可靠性(F3)、可演进性(F1/F5)、并发正确性(F2)、合同明确性(F4)四件**正交关注点**打成一个大包 (违反单一原则)
- Rollout 没有 expand-contract / dual-read / feature flag / rollback drill, 让 main 长时间处于半改不可逆状态

**裁决路径**: 把原 ADR-001 拆成 5 个独立 ADR, 每个聚焦单一关注点 + 单独可评审 + 单独可回滚。本文件 (ADR-000) 保留为**溯源记录**, 防止后人重提被否决的方案 (例如再次提议 "AC/WA 缩写 enum" 或 "afterCommit + 5min reaper 替代 Outbox") 。

## 4. 5 个承接 ADR 索引

| ADR | 主题 | 解决的 Finding |
|---|---|---|
| **[ADR-001](./ADR-001-verdict-status-codec.md)** | Verdict / SubmissionStatus codec 演化 (兼容现有 11 状态) | F1 |
| **[ADR-002](./ADR-002-sandbox-hexagonal.md)** | Sandbox Port + LanguageProfile Strategy + 双 Adapter | (原 §2.1 中无问题部分) |
| **[ADR-003](./ADR-003-queue-outbox-fencing.md)** | Queue Port + Outbox + Generation Fencing + JUDGING lease/heartbeat | F2 + F3 |
| **[ADR-004](./ADR-004-notification-intents.md)** | NotificationIntent typed + per-channel projection + 失败隔离 | F4 |
| **[ADR-005](./ADR-005-rolling-deploy-playbook.md)** | 每 milestone 的 feature flag / dual-read / envelope versioning / canary / rollback drill | F5 |

## 5. 永久拒绝清单 (后续 ADR 不再重复论证)

| 拒绝项 | 拒绝理由 |
|------|---------|
| **Chain of Responsibility 用于 verdict** | Verdict 是纯归约非责任传递, 错配 |
| **GoF State Pattern (每状态一个类)** | OJ 状态无独立行为, 过度抽象 |
| **Spring StateMachine 框架** | 引入框架成本 > 收益, Map + CAS 足够 (但 CAS 需配 generation fence, 见 ADR-003) |
| **Vavr `Try` / `Either` monad** | 与 Spring `throw BusinessException` 范式硬冲突 |
| **Specification Pattern** | MyBatis-Plus `LambdaQueryWrapper` 已是 Spec-lite, 仅在跨 3+ 模块复用条件时再上 |
| **ServiceLoader / PF4J 语言插件框架** | 封闭 5 语言集 (JS/Python/Java/C/C++), Spring 集合注入足够 |
| **Remote / Firecracker / gVisor sandbox** | 当前无需求, 真有时新加 adapter 即可 (YAGNI) |
| **AC/WA/TLE 缩写 enum 重塑 verdict 名** | Codex F1 否决: 破坏 DB 持久化与前端 i18n 合同 |
| **afterCommit + 5min Reaper 替代全量 Outbox** | Codex F3 否决: commit-callback gap / Redis 模糊超时 / 时钟偏移三连问题 |
| **单 NotificationOrchestratorListener + 泛型 envelope** | Codex F4 否决: envelope 装不下渠道特化数据, 把耦合下沉一层 |
| **4-6 周 M1→M2→M3 串行 worktree 无 feature flag** | Codex F5 否决: 不可独立部署, 不可逆 |

## 6. 实施次序

依据 [ADR-005](./ADR-005-rolling-deploy-playbook.md) 的 milestone 拆分, 不再走原 "3 worktree 串行" 。

## 7. References

- 本次 `/grill-me` 决策树访谈记录 (session 2026-06-13)
- Codex Adversarial Review 输出 (verdict `needs-attention`, 5 finding)
- Commit `e34e4efbd` (原 ADR-001 单文件版本, 已被本拆分取代)
- 项目设计规约: `.claude/rules/backend/07-java-design.md` (单一原则, 依赖倒置, 开闭原则)
