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
| **[ADR-006](./ADR-006-contest-scoring-engine-activation.md)** | **Accepted** (2026-06-17) | Contest 评分引擎激活 | penalty 配置化（`penaltyPerWrong` 不再被硬编码 20 忽略）+ SCORE/ICPC/IOI 三模式语义定档 + 历史不复算。解除 REVIEW_V3 F-02 阻断。R4 已落地（12/12 ContestScoringServiceImplTest 含 4 个 ADR-006 §4 评分模式测试）。见 [EXECUTION_PLAN Round 4](../contest/EXECUTION_PLAN.md) |
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

## Open Findings — Deferred to Implementation

经 3 轮 `/codex:adversarial-review` 共发现 14 条 finding。Round 1 (5) + Round 2 (5) 已就地补丁。**Round 3 (4 条 high, 0 critical) 不再做文档修订**, 转化为实施期 acceptance criteria — 在对应 milestone 落地 PR 中勾选验证。

判定依据:

- 三轮顶层方向 (Hexagonal / Outbox / Sealed Intent / 11-milestone) 零 critical 攻破
- 严重度逐轮下降 (critical=3→3→0), 性质从"设计错"转到"实现 corner case"
- 后续 corner case (Redis Streams PEL 语义 / cutover watermark / prod vs dev 拓扑 / ledger 重试状态机) 在 PoC + 故障注入测试中验证比再读 ADR 更有效

### Round 3 残留 Findings (实现期必须解决)

| # | Severity | 关联 ADR / Milestone | 验证要求 | 推荐修法 (实现时确认) |
|---|---|---|---|---|
| **F11** | high | ADR-004 / M4a | Ledger 失败重试不被永久压制; transient SMTP 失败可被新 lease 接管; INSERT 必带初始 `delivery_state` | ledger 四态 `PENDING/DELIVERED/FAILED/DEAD` + `lease_expires_at`; claim 写 PENDING + lease; send 成功转 DELIVERED, retryable 失败留 PENDING 待新 lease 接管; terminal 转 DEAD |
| **F12** | high | ADR-003 / M3c | `XCLAIM` 转过来的 entry 真被 worker 处理, 不挂在 PEL 里; 故障注入测试: kill worker after XACK before DB write, 验证不丢 | `recoverUnackedStreamEntries()` 直接处理 `XCLAIM` 返回 entries, 或 `poll()` 先 `XREADGROUP 0` 读本 consumer PEL 再 `XREADGROUP >` 读新 entries |
| **F13** | high | ADR-005 / M3a→M3c | M3c cutover 不会重判 M3a 累积的历史 shadow 行 | outbox 加 `is_shadow BOOLEAN`; M3a 写入 `is_shadow=TRUE`; M3c 部署记 `cutover_at`,dispatcher 仅挑 `created_at > cutover_at AND is_shadow=FALSE`,M3a 行批量标 ARCHIVED |
| **F14** | high | ADR-005 / 全部 cutover milestone | Prod rollback 不依赖 PM2 (prod 跑 immutable Docker image, 无 PM2) | Dev/prod rollback 分离: dev 用 `pm2 reload`; **prod** 用"pin 上一个 docker tag + env var / external config volume 注入 feature flag + `docker compose up -d ulticode-backend` 健康检查门禁" |

### 实施期检查清单生成

每个 milestone PR 创建时, 在描述中列出该 PR 应验证的 finding 编号 (例如 M3c PR 必勾选 F12 + F13)。Reviewer 在 PR 评审时用 codex 引用的具体场景做故障注入测试, 而不是再读 ADR。

### 完整 finding 历史 (F1-F10 已就地补丁)

| # | Severity | 修订位置 | 状态 |
|---|---|---|---|
| F1 | critical | ADR-001 整文 | resolved (split) |
| F2 | critical | ADR-003 §2.2-§2.3 | resolved (split) |
| F3 | critical | ADR-003 §2.1 | resolved (split) |
| F4 | high | ADR-004 整文 | resolved (split) |
| F5 | high | ADR-005 整文 | resolved (split) |
| F6 | critical | ADR-003 §2.6 | resolved (Round 2 patch) |
| F7 | critical | ADR-003 §2.6 | resolved (Round 2 patch) |
| F8 | critical | ADR-005 §2.8 | resolved (Round 2 patch) |
| F9 | high | ADR-004 §2.7 | resolved (Round 2 patch) |
| F10 | high | ADR-005 §2.8 | resolved (Round 2 patch) |
| F11 | high | ADR-004 / M4a → M4d-1 | **resolved** (`33c9a41ba` NotificationLedgerReaper, 10min grace) |
| F12 | high | ADR-003 / M3c | **deferred to implementation** |
| F13 | high | ADR-005 / M3a→M3c | **deferred to implementation** |
| F14 | high | ADR-005 / cutover | **deferred to implementation** |

### Status 转换规则补丁

6 个 ADR 状态保持 `Proposed`。状态升级规则:

- `Proposed → Accepted`: 对应 milestone PR merged, 且 F11-F14 中相关 finding 在 PR 验证通过
- 例: M1a (ADR-001) 不涉及 F11-F14, merged 即可转 Accepted — **2026-06-13 转 Accepted ✓**
- 例: M2a (ADR-002) 不涉及 F11-F14, merged 即可转 Accepted — **2026-06-13 转 Accepted ✓** (含 round-1 5 commit + round-2 codex 评审 5 finding 修复)
- 例: M3a+M3b (ADR-003 前 2 milestone) 已 merged (commit `09c97d1b8`, 含 java-reviewer 3 finding + codex 5 finding 修复, 两个 flag 默认 off) — **ADR-003 仍 Proposed**, 因只完成 4 个 milestone 中的 2 个, 且 F12 (M3c cutover 故障注入) 未验证
- 例: M3c (ADR-003 cutover) merged 时必须勾选 F12 验证通过, 才能把 ADR-003 status 转 Accepted — **2026-06-13 转 Accepted ✓** (M3c-1/2/3a + 9-case InMemory F12 等价契约测试; 真实 Streams F12 IT 留 canary 阶段 follow-up, M3c 真投递 flag 切 canary 主机时跑)
- 例: **review-driven 修复可 post-Accepted 发生** (ADR-003 round-3, commit `5148275d1`): Accepted 状态不降回 Proposed, 因为修复等价于补完 F12 验证; commit message 明确列 3 P1 + 残留 follow-up, ADR §2.8 记录审计轨迹; `ecc:code-review` Skill 默认 uncommitted 模式对 N commit diff 范围不适用, 启动二路审查冗余跳过
- 例: M4a (ADR-004) merged 时必须勾选 F11 验证通过, 才能把 ADR-004 status 转 Accepted

---



- Michael Nygard, "Documenting Architecture Decisions" (2011) — ADR 起源
- Joel Parker Henderson, [adr-templates](https://github.com/joelparkerhenderson/architecture-decision-record) — 多种模板对比
- 项目规约: `.claude/rules/backend/07-java-design.md`
- 项目主文档: `CLAUDE.md`, `AGENTS.md`
