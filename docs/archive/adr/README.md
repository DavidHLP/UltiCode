# Architecture Decision Records (ADR)

本目录记录 UltiCode 架构决策。每条决策以 `ADR-NNN-{kebab-case-title}.md` 命名, 按 Michael Nygard 的轻量 ADR 模板编写, 适配项目中文 + 表格风格。

## 索引

| ADR | 状态 | 主题 | 摘要 |
|-----|------|------|------|
| **[ADR-000](./ADR-000-hexagonal-grilling-session.md)** | Superseded → ADR-001/002/003/004/005 | Hexagonal 化设计访谈与对抗评审记录 | 记录 `/grill-me` 访谈过程 + Codex 对抗评审 5 条 finding + 永久拒绝清单 |
| **[ADR-001](./ADR-001-verdict-status-codec.md)** | **Accepted** (2026-06-13) | Verdict / SubmissionStatus codec 演化 | 在不动 DB 持久化值 + 前端 i18n key 的前提下, 把字符串 verdict 升级为强类型 enum + 三层 Codec |
| **[ADR-002](./ADR-002-sandbox-hexagonal.md)** | **Accepted** (2026-06-13) + Operational Pitfalls + Follow-up Hardening (2026-06-14) + §7.7 Post-Hardening 实战修复 (2026-06-14) | Sandbox Hexagonal Port + LanguageProfile Strategy | `SandboxExecutor` port + Docker/InMemory 双 adapter; 5 个 LanguageProfile 集合注入 fail-fast; **§6 实战教训** — 4 个 bug 叠加导致 verdict 全部退化成 "Runtime Error" 的根因链 + 修复信号; **§7 Follow-up Hardening** — Docker `latest` tag 自动重打 / harness `peak_memory_bytes` 上报 / OJ 策略 null list-like → `[]`; **§7.7 实战教训 #6** — Facade `toDtoCaseResult` 漏透传 `inputs/output/expectedOutput`(verdict 对但 UI 详情缺) + 单测 + 重构防御 (Phase 2+ 抽共用 builder); **§8 资源测量与判定契约 (2026-06-16)** — 全量修复 P0/P1/P2:超时公式按 case 数缩放(P0-1)/ MLE 三层判定(P0-2)/ `elapsed_us`+`cpu_ms` 新字段(P1-1/P1-2)/ 跨语言 true-peak 内存 Java MemoryPoolMXBean·Python ru_maxrss·C++ child getrusage(P1-3/P1-4)/ P2-1 题目级 time_limit·memory_limit(Flyway + Problem entity + CodeExecutionService 读取);核心后端测试 0 regression |
| **[ADR-003](./ADR-003-queue-outbox-fencing.md)** | **Accepted** (2026-06-13) | Queue + Outbox + Generation Fence + JUDGING Lease | 任务投递走 Outbox 表 + 唯一约束去重; submission 加 generation/lease 列防旧 worker 覆盖与 JUDGING 卡死。**M3a+M3b+M3c shipped (commits `09c97d1b8` / `b34ac01be` / `3e8504f1b` / `3ec758c41`); M3d 留 cutover 后 ≥2 周** |
| **[ADR-004](./ADR-004-notification-intents.md)** | **Accepted** (2026-06-13) + M4d-1 follow-up (2026-06-14) | NotificationIntent + Per-Channel Projection + 失败隔离 | sealed `NotificationIntent` 替代泛型 envelope; 每 channel 独立 try-catch 失败隔离; **M4a+M4b+M4c+M4d shipped** (`e38e340` / `bf02f48ec` / `9ecf10ec9` / `62a4dcabe`); **M4d-1 7-finding review shipped** (`d32882198` / `b7dc1378c` / `ce629194b` / `33c9a41ba` — NPE / WS wire-contract / silent-skip / intentId 防撞 / CONTEST 死分支 / LedgerReaper); F11 同步 resolved (Reaper 实现); channel-level preference 列入 ADR-007 候选 |
| **[ADR-005](./ADR-005-rolling-deploy-playbook.md)** | Proposed (stays Proposed pending rollback drill — 见 ADR §状态行) | 滚动部署 Playbook | 10 个独立可部署 milestone + feature flag + envelope versioning + canary gate + rollback drill |
| **[ADR-005a](./ADR-005a-rollback-drill-protocol.md)** | Proposed (ADR-005 子协议) | Rollback Drill 协议 | ADR-005 §2.6 与 §4 #2 的执行子协议 (不是同级 ADR); 编号 `005a` 标注归属, 不占用新主编号 |
| **[ADR-006](./ADR-006-contest-scoring-engine-activation.md)** | **Proposed** (待评审 + 实施验证) | Contest 评分引擎激活 | penalty 配置化（`penaltyPerWrong` 不再被硬编码 20 忽略）+ SCORE/ICPC/IOI 三模式语义定档 + 历史不复算。R4 的历史执行记录见 [EXECUTION_PLAN Round 4](../contest/EXECUTION_PLAN.md)；评审与验证状态以 ADR 正文为准。 |
| **[ADR-007](./ADR-007-virtual-contest-lifecycle-and-rating-isolation.md)** | **Accepted** (2026-06-17) | 虚拟竞赛生命周期调度与评级隔离 | auto-finish 接线（解除零调用者死代码）+ 真实赛结束全员 FINISHED + 评级查询从 `status='STARTED'` 切换到 `is_virtual=0`（消除隐式不变量）+ 虚拟开赛幂等（实际改用 DB `FOR UPDATE` 而非 Redis 锁；见 ADR-007 §6 实施偏差）+ 前端 session 持久化。四改动**原子上线**。R3 已落地。见 [EXECUTION_PLAN Round 3](../contest/EXECUTION_PLAN.md) |
| **[ADR-008](./ADR-008-websocket-auth-and-realtime-push.md)** | **Accepted** (2026-06-17) | WebSocket auth + realtime push | F-04 useContestSocket 接入 (RankingsView 实时榜) + F-13 visibilitychange (HIGH-1 修复后真正改 endsAt) + F-17 SUBSCRIBE-frame 鉴权 (`ContestSubscribeAuthInterceptor`，注册才放行) + F-18 unmount cleanup. R6.4 已落地. 见 [_archive/EXECUTION_PLAN_R6 Round 6.4](../contest/_archive/EXECUTION_PLAN_R6_2026-06-17.md) |
| **[ADR-009](./ADR-009-israted-gate-and-virtual-rating-isolation.md)** | **Accepted** (2026-06-17) | isRated gate + virtual-rating isolation | F-03 isRated 守卫（`RatingCalculationServiceImpl` 入口; 零额外查询, 复用 contestMapper）+ F-10 finishVirtual 不重算（决策记录在 ADR-007 §7）+ F-13 决议补全. R6.1 已落地. 见 [_archive/EXECUTION_PLAN_R6 Round 6.1](../contest/_archive/EXECUTION_PLAN_R6_2026-06-17.md) |
| **[ADR-010](./ADR-010-cancel-state-and-virtual-replay-boundary.md)** | **Accepted** (2026-06-17) | contest 状态机边界（F-35/F-38/F-50-52）| FINISHED 状态机边界 + CANCELLED 不允许开虚拟 + 虚拟赛数据作用域已 R3.3/R6.3 覆盖. R7.5 决策类. 见 [_archive/EXECUTION_PLAN_R7 §6](../contest/_archive/EXECUTION_PLAN_R7_2026-06-17.md) |
| **[ADR-011](./ADR-011-crit6-shadow-mode-evaluation.md)** | **Accepted** (2026-06-17) | CRIT-6 (F-ARCH-07) shadow 模式评估结论 | 不引独立 `contest.scoring.shadow` flag — 隐式灰度由 ADR-006 §2.4 覆盖. R8.5 决策类. 见 [_archive/EXECUTION_PLAN_R8 §6](../contest/_archive/EXECUTION_PLAN_R8_2026-06-17.md) |

## 编号规则

- **ADR-000** 保留给 "meta / supersede 溯源" 类记录
- **ADR-001+** 按提议时间顺序编号, **不补缺**, 不复用 (即使被 supersede 也保留编号)
- 文件名固定 `ADR-NNN-{kebab-case-title}.md` , NNN 三位补零
- **子协议例外**: 某 ADR 的执行子协议 (rollback drill / runbook appendices 等, 非同级 ADR) 用
  `ADR-NNNx` 后缀 (如 `ADR-005a`), 不占用新主编号, 文件名 `ADR-NNNx-{kebab-case-title}.md`
- 一旦 commit 进 main, **不可改名** (引用关系会失效)

## 状态流转

```
Proposed → Accepted → Implemented
        ↘ Rejected
        ↘ Superseded by ADR-XXX
```

- **Proposed**: 已写完, 待评审 (人评审 + `/codex:adversarial-review`)
- **Accepted**: 评审通过, 可执行
- **Implemented**: 实施完成 (代码已 merge + 测试通过)
- **Rejected**: 评审否决, 保留文件作为"为什么不做"的存证
- **Superseded**: 被新 ADR 取代, 头部声明 `Superseded by ADR-XXX` , 保留全文

## 评审流程

1. 在 worktree / 分支创建 ADR 文件, status = `Proposed`
2. commit + 提 PR
3. 跑 `/codex:adversarial-review --base main --background "..."` 对抗评审
4. 团队评审 PR (至少 1 reviewer approve)
5. 全部通过 → 修改 status = `Accepted` , 二次 commit, merge 进 main
6. 实施完成 → 修改 status = `Implemented` , commit
7. 后续被取代 → 头部加 `Superseded by ADR-XXX` , status = `Superseded`

## 何时写 ADR

参考 `.claude/rules/backend/07-java-design.md` #14 / #16, 下列变更**必须**走 ADR:

- 跨模块的端口 / 抽象引入 (Hexagonal port, 新 Strategy 接口)
- 持久化字段 / 表结构变更影响多模块
- 新引入框架 / 库 (Spring StateMachine, Vavr, 等)
- 部署架构变更 (新增 worker, 改部署单元)
- 安全 / 合规决策 (鉴权方式, 数据加密)

下列变更**不需要**走 ADR (走 PR review 即可):

- 单 bug fix
- 单模块内部重构, 不改对外接口
- 测试新增 / 调整
- 文档 / 注释修订
- 依赖小版本升级 (除非有 breaking change)

## 历史评审记录

三轮对抗评审的 finding 与实施条件曾在本索引中重复维护。为避免把历史台账误读为当前状态，保留原则如下：

- ADR-000 保留原始评审与拆分依据；F1–F10 的处置保留在对应 ADR。
- 仍可能影响未来 cutover/rollback 的 finding 以对应 ADR 为准：F11 见 [ADR-004](./ADR-004-notification-intents.md) §2.8，F12–F13 见 [ADR-003](./ADR-003-queue-outbox-fencing.md) §2.7/§2.8/§4，F14 见 [ADR-005](./ADR-005-rolling-deploy-playbook.md) §2.9；[ADR-005a](./ADR-005a-rollback-drill-protocol.md) 只承载回滚演练步骤。
- 本 README 只负责索引、编号和生命周期规则，不再维护“待实施 finding”或 ADR 状态转换流水账。当前未完成项以对应 ADR 和 [services issue registry](../../../services/docs/SERVICES_ISSUES.md) 为准；历史计划与证据见 [archive guide](../README.md) 及 Git 历史。

---



- Michael Nygard, "Documenting Architecture Decisions" (2011) — ADR 起源
- Joel Parker Henderson, [adr-templates](https://github.com/joelparkerhenderson/architecture-decision-record) — 多种模板对比
- 项目规约: `.claude/rules/backend/07-java-design.md`
- 项目主文档: `CLAUDE.md`, `AGENTS.md`
