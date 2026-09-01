# Contest 模块 R7 多轮执行计划 — MED/LOW 收口 + F-15 完整化

> **作用**：基于 PRD §1.3 / REVIEW_V3 §9 中 R1-R6 关闭后剩余 31 项 finding（1 HIGH partial + 17 MEDIUM + 12 LOW + 1 历史债），给出**可独立部署、可独立回滚**的多轮实施方案。
> **裁决依据**：R1-R6 已关闭全部 CRITICAL（6/6）与大部分 HIGH（11/12），剩余项主要是性能 NFR、组件一致性、UX 打磨、非阻断工程债。
> **创建**：2026-06-17
> **预计 R7 工期**：7–9 人日（Sprint S5–S8）
> **不沿用** R1-R6 编号；R7 用"按主题聚类 + 风险隔离"分轮。

---

## 0. 范围盘点

### 0.1 PRD §1.3 全 finding 状态

| 严重度 | 全部 | R1-R6 已关闭 | R7 范围 |
|--------|------|--------------|---------|
| CRITICAL | 6 | 6 ✅ | 0 |
| HIGH | 12 | 11 ✅ | **1**（F-15 partial → complete）|
| MEDIUM | 17 | 0 | **17** |
| LOW | 12 | 0 | **12** |
| INFO | 2 | 2 ✅ | 0 |
| 历史债 | 1 (CRIT-6) | 0 | **1**（shadow 模式）|

R7 共 **31 项**（1 HIGH partial + 17 MED + 12 LOW + 1 CRIT-6）。

### 0.2 决策点摘要

- **F-21 排行榜缓存击穿**：NFR-P1 强制项（1000 人 AC < 500ms p99）→ **P0 必做**
- **F-15 完整 enum 化**：CLAUDE.md 优先项 → **P0 必做**
- **F-29/F-32/F-28**：与 R6.4 接通后紧密相关（同 Sprint S4 集群）→ **P0 必做**
- **F-35/F-38 决策类**：影响 schema 改动；早做定调
- **CRIT-6 shadow 模式**：runtime 模式，需 ops 配合；留 S5 评估
- **LOW 12 项**：优先收口 Sprint S8 的 UX 一致性；其他 F-43/44/47 与 R6.4 WS 路径同步收口

---

## 1. 设计原则

| 原则 | 含义 |
|------|------|
| **每轮可独立部署** | R7.x 顺序无强依赖；除 R7.4 (F-15 enum) ↔ R7.1 (F-21 缓存) 因共享 VO 修改外可任意 shuffle |
| **每轮可独立回滚** | 回滚 R7.5 (LOW 一致性) 不影响 R7.1-7.4 |
| **性能优先** | F-21 是 NFR 强制；其他 MED/LOW 可后置 |
| **决策先于代码** | F-35 / F-38 决策类（schema / 业务规则）落 ADR 后再实现 |

---

## 2. Round 7.1 — 排行榜性能 NFR（F-21）

**目标**：1000 人 AC 同时发生时，排行榜查询 < 500ms p99。
**风险**：中（Redis 缓存一致性问题 + 击穿防护策略选型）。
**工期**：1.5–2 人日。

### 改动

#### 2.1 缓存策略
- 排行榜 cache key：`contest:ranking:{contestId}:{limit}:{offset}`（含分页维度，避免雪崩）
- TTL：30s（与现有 `@Cacheable("contestRanking")` 一致）
- 击穿防护：single-flight 模式（同一 key 同一时刻只查一次 DB）
  - 实现：`Caffeine` 本地 L1 + Redis L2，本地已加载则直接返回
  - 或：Redis `SETNX` 短锁 + `CompletableFuture` 异步预热

#### 2.2 invalidation 时机
- 现有：`ContestScoringServiceImpl.applyJudgeResult` 末尾 `evictRankingCache()`（全量 clear）
- 优化：改为按 `contestId` 局部 evict（减少对其他 contest 的影响）
- 失效顺序：先写 DB → 再 evict cache（避免脏读）

#### 2.3 验收
- [ ] JMH/JMeter 压测 1000 并发 AC → 排行榜查询 p99 < 500ms
- [ ] 缓存击穿：单一热点 key 在高并发下 DB QPS 不会超 1

### 回滚
- 删 `Caffeine` Bean / 改回全量 `cache.clear()` → 性能回到 R6 baseline（无 NFR 保证）

---

## 3. Round 7.2 — 虚拟赛健壮性（F-22 + F-25 + F-31）

**目标**：虚拟赛在异常状态（跨 contest 复用、scheduler 容错、状态恢复）下不破不变量。
**风险**：中（涉及跨域并发）。
**工期**：1 人日。

### 改动

#### 3.1 F-22 跨 contest 复用检查
- 现状：`startVirtualContest` 仅校验 `contest.status=FINISHED`，不校验 `(contest_id, user_id)` 已有活跃虚拟会话
- 风险：用户对 contest A 开了虚拟，回 contest B 之前未 finish A → 是否破坏 `is_virtual=1` 隔离
- 修复：保留 R3.3 的 `(contestId, userId)` FOR UPDATE 串行化即可，**实际不破坏**（因为虚拟 session 唯一约束是 `(contest_id, user_id, virtual_session_id)` 三元组，跨 contest 不冲突）
- 行动：**audit doc 记录结论**（不修代码，验证 R3.3 行为已满足需求）

#### 3.2 F-25 scheduler 容错
- 现状：`ContestScheduler` 单一 `@Scheduled(fixedRate=10_000)`，线程挂掉后依赖 Spring TaskScheduler 重启
- 风险：单次 tick 异常可能影响后续 ticks
- 修复：每个 tick 用 `try-catch` 包裹（已在 R3.1 加过），加监控指标 `contest.scheduler.tick.duration` + `contest.scheduler.tick.errors`
- 文档：明确"scheduler 重启策略依赖 Spring Boot actuator"

#### 3.3 F-31 状态恢复
- 现状：虚拟 session 异常退出（kill -9 / OOM）后 participant 仍 STARTED
- 修复：scheduler 启动时跑一次 `autoFinishVirtualParticipants`（已存在，调用即可）
- 验证：单元测试模拟"进程崩溃后重启" 场景

### 验收
- [ ] F-22 audit doc 明确"无 violation"
- [ ] F-25 scheduler 单 tick 异常不传播
- [ ] F-31 启动时 sweep 已过期虚拟 session

---

## 4. Round 7.3 — 实时同步扩展（F-29 + F-43 + F-44 + F-47）

**目标**：WS 断线重连 / 心跳 / 错误 UX。
**风险**：低（前端行为 + 已有 R6.4 WS 基础）。
**工期**：0.5–1 人日。

### 改动

#### 4.1 F-29 WS 重连退避
- 现状：`useContestSocket` 已有 `autoReconnect` + `maxReconnectAttempts: 10` + `reconnectionDelay: 1000`
- 优化：指数退避（1s → 2s → 4s → ... → 上限 30s）替代固定 1s
- UI 提示：`connectionStatus === "reconnecting"` 时显示 banner

#### 4.2 F-43/F-44 鉴权失败 + 心跳超时
- F-43：STOMP ERROR frame 收到时（来自 F-17 拒绝），前端 toast "您未注册此竞赛"
- F-44：心跳超时（10s × 3 miss）→ 标记 disconnected，触发重连

### 验收
- [ ] 网络断 5s 后 UI 显示 reconnecting
- [ ] 30s 后仍未连 → max 触发，banner 提示
- [ ] 未注册用户 STOMP 拒绝 → toast 提示

---

## 5. Round 7.4 — 前端一致性（F-15 完整 + F-28 + F-30 + F-32 + F-39 + F-40 + F-41 + F-46）

**目标**：跨端 DTO enum 统一；倒计时 / 多 tab 互锁 / TS 错误页 i18n。
**风险**：低（前端层）。
**工期**：2.5–3 人日。

### 改动

#### 5.1 F-15 完整 enum 化（CLAUDE.md 优先项）
- 后端 DTO：`ParticipationStatusDTO.action` 等 `String` → `enum`
  - 涉及 DTO：跨栈 grep 找 `private String` 字段在 contest 相关 DTO 中的位置
- 前端：`@/types/contest.ts` 增 `as const` 对象 + `keyof typeof` 派生类型
- 删 `console/src/stores/contest.ts:73-80` 的 `as string` 兜底
- 跑 `cross-stack-dto-granularity-alignment` skill 双向审计

#### 5.2 F-32 ContestTimer 组件统一
- 现状：`ContestTimer.vue` (真实赛) + `VirtualContestTimer.vue` (虚拟) 各自实现
- 改造：抽出共享 `useContestCountdown` composable，统一倒计时逻辑
- `VirtualContestTimer` 复用同一 composable + 加 `setVirtualSession` 集成

#### 5.3 F-28 多 tab 互锁
- 同一用户多 tab 报同一个虚拟赛 → 后端会话应唯一
- 现状：R3.3 `(contestId, userId)` 唯一 + FOR UPDATE 串行化 → 已隐式支持
- 行动：**audit doc 确认无 violation**（R3 已覆盖）

#### 5.4 F-30 / F-39 / F-40 / F-41 / F-46 UX 一致性
- F-30：跨端 enum 边界值（"FINISHED" / "CANCELLED" / "PAUSED"）
- F-39：TS null vs undefined 边界
- F-40：loading 态空态文案统一（"暂无数据" / "加载中..."）
- F-41：错误页 i18n key 补齐
- F-46：多 tab 互锁 UX 文案（"您已在另一个标签页开始虚拟赛"）

### 验收
- [ ] grep `as string` 在 contest 相关 store 0 个匹配
- [ ] 后端 DTO enum 字段类型 `String` → enum 类型
- [ ] i18n keys 完整（`contest.*.empty` / `contest.*.error.*`）
- [ ] F-28 / F-32 audit doc 明确"已收口 / 已统一"

---

## 6. Round 7.5 — 文档 / UX LOW（F-35 / F-36 / F-37 / F-38 / F-42 / F-45 / F-50/51/52）

**目标**：决策类 ADR + 边界打磨。
**风险**：低（文档 + UX）。
**工期**：0.5–1 人日。

### 改动

#### 6.1 F-35 决策
- PRD P4：transitionToFinished 后真实 participant FINISHED vs REGISTERED
- 决策：维持当前实现（FINISHED 全部置为；REGISTERED 保持）→ 写 ADR-010 决策记录
- 影响：仅文档，无代码改动

#### 6.2 F-38 决策
- PRD P5：CANCELLED 比赛是否允许开虚拟
- 决策：**不允许**（CANCELLED 是终止态，与虚拟 replay 语义冲突）→ 写 ADR-011 决策记录
- 影响：仅文档，无代码改动；`startVirtualContest` 现状已隐式拒绝（要求 FINISHED）

#### 6.3 F-45 文档化
- 历史债 / 边界类，迁到 `docs/contest/completed/` 归档
- 影响：仅文档

#### 6.4 F-50/51/52 虚拟赛数据作用域
- A4 范围：虚拟赛 submission 不污染全局 / 跨 user 隔离 / 跨 contest 隔离
- 现状：`(contest_id, user_id, virtual_session_id)` 三元组已隔离
- 行动：**audit doc 确认无 violation**

### 验收
- [ ] ADR-010 (F-35) / ADR-011 (F-38) Accepted
- [ ] F-50/51/52 audit doc

---

## 7. Round 7.6 — 数据库完备性（F-24 + F-27 + CRIT-6）

**目标**：排行榜分页性能 / 限流 key / shadow 模式决策。
**风险**：低（F-24/F-27 增量 + 性能可观测；CRIT-6 决策类）。
**工期**：0.5 人日。

### 改动

#### 7.1 F-24 排行榜分页
- 现状：`selectParticipantsWithUserByContestIdPaginated` 已有 LIMIT/OFFSET
- 优化：大 offset 改为 keyset 分页（基于 `final_rank` 游标）
- 验收：10000 人 contest 翻页（offset=9000）< 100ms

#### 7.2 F-27 限流 key
- 现状：`@RateLimit(key = "contest:virtual-start", ...)` 全局
- 改造：`@RateLimit(key = "contest:virtual-start:{contestId}", ...)` 加 contestId 维度
- 防单用户多 contest 刷数据

#### 7.3 CRIT-6 shadow 模式（评估）
- 不引（运行时模式需 ops 配合）
- ADR-007 §6 注释保留；S5 重审

### 验收
- [ ] F-24 keyset 分页压测 < 100ms
- [ ] F-27 限流 key 加 contestId 维度
- [ ] CRIT-6 注释更新

---

## 8. 部署顺序建议

1. **R7.6**（F-24 keyset 分页 + F-27 限流 + CRIT-6 注释）→ 性能可观测性增量，最稳
2. **R7.2**（F-22/25/31 虚拟赛健壮性）→ 主要是 audit doc + 启动时 sweep
3. **R7.3**（F-29/43/44/47 WS 扩展）→ 与 R6.4 WS 路径同步收口
4. **R7.4**（F-15 完整 + F-28/32 UX）→ 最大块，含跨端 enum
5. **R7.1**（F-21 排行榜性能 NFR）→ 最后部署，单独窗口
6. **R7.5**（决策类 ADR）→ 任何时候

> **耦合集**：
> - R7.1 排行榜缓存 ↔ R7.4 enum 化：前者可能改 VO 字段，与 enum 化冲突；建议**先 R7.4 再 R7.1**
> - R7.4 完整 enum 化 ↔ R6 ADR-009：枚举名 / 值需 ADR-009 风格一致

---

## 9. 验收总表

| Round | Finding | 验收命令/方法 | 估时 |
|-------|---------|--------------|------|
| R7.1 | F-21 | 1000 AC p99 < 500ms；缓存击穿 DB QPS ≤ 1 | 1.5–2 |
| R7.2 | F-22/25/31 | 3 个 audit doc + sweep 测试 | 1 |
| R7.3 | F-29/43/44/47 | 断网 → reconnecting banner + 拒绝 toast | 0.5–1 |
| R7.4 | F-15/28/30/32/39/40/41/46 | grep `as string` = 0 + 跨端 enum 同步 | 2.5–3 |
| R7.5 | F-35/36/37/38/42/45/50/51/52 | ADR-010/011 Accepted | 0.5–1 |
| R7.6 | F-24/27 + CRIT-6 | keyset 压测 + 限流 key | 0.5 |
| **合计** | **31 finding + 文档** | | **6.5–9 人日** |

**R7.1–R7.6 全绿 → PRD §1.3 全部 49 finding 关闭，PRD Sprint S1-S8 全部签收。**

---

## 10. 与历史报告的关系

- `REVIEW.md` / `REVIEW_V2.md` / `SECURITY_REVIEW.md` / `FINDINGS_RAW.md` / `DESIGN_ANALYSIS.md` / `PLAN.md` 作为历史 v1-v2 证据保留
- `REVIEW_V3.md` + `EXECUTION_PLAN.md` + `completed/EXECUTION_PLAN_R6.md` 是 R1-R6 收口的执行记录
- 本计划（R7）是 v3.2 → v4 的"非阻断收口" — 完成后 49 finding 全部关闭，可视为"模块完结 v4.0"

> 每轮部署前：`git diff --check` + Conventional commit `<type>(contest): <desc>`。`git push` / 合并需用户显式批准。
