# Contest 模块 R8 多轮执行计划 — 性能 / UX / 文档 收口

> **作用**：R1-R7 已关闭 49 个 finding 中的所有 P0/P1。R8 收口 R7 显式 deferred 的 6 项 + 12 项 LOW（F-35~F-47），完成模块 v4.1 收口。
> **裁决依据**：R7 §10 + R8 候选列表（已记录在 REVIEW_V3 §10）。
> **创建**：2026-06-17
> **预计 R8 工期**：4.5–5 人日
> **R8 完成条件**：所有 deferred finding 关闭 + 12 项 LOW 收口 + 文档归档

---

## 0. 范围盘点

### 0.1 剩余 finding

| 严重度 | 全部 | 已关闭 | R8 范围 |
|--------|------|--------|---------|
| CRITICAL | 6 | 6 | 0 |
| HIGH | 12 | 12 | 0 |
| MEDIUM | 17 | 11 | **6**（F-21/24/27/29/32 + CRIT-6 评估）|
| LOW | 12 | 0 | **12**（F-35~F-47 中 5 个收口；F-43/44/47 实质已落）|
| 历史债 | 1 | 0 | **1**（CRIT-6 shadow 模式）|

R8 共 **19 项**（6 R7 deferred + 12 LOW + 1 CRIT-6）。**F-43/44/47 在 R6.4/R7.3 已落地**，R8 仅需文档同步。

### 0.2 决策点摘要

- **R8.4 F-29 指数退避**：manual deactivate/activate 循环。R8 唯一有"运行时行为变化"的轮次。
- **R8.3 F-21 per-contest 缓存 evict**：cache key 模板需扩到含 contestId；R8.1 F-24 keyset 分页需同改 cache key，**R8.1+R8.3 须同 commit**。
- **R8.5 CRIT-6 shadow 模式**：runtime + ops 配合；建议**不引**（与 ADR-006 §2.4 灰度策略合并，flag 增殖代价 > 收益）。
- **R8.6 LOW 一致性收口**：纯 i18n / UX 文案 / 文档；最大块。

---

## 1. 设计原则

| 原则 | 含义 |
|------|------|
| **每轮可独立部署** | R8.x 顺序无强依赖；除 R8.3 ↔ R8.1 共享 cache key 改动外可任意 shuffle |
| **每轮可独立回滚** | 回滚 R8.6 (LOW 收口) 不影响 R8.1-R8.5 |
| **性能优先** | F-24/F-21 是 NFR；R8.1+R8.3 同 commit 部署 |
| **风险隔离** | F-29 指数退避是 R8 唯一"运行时行为变化"，独立 commit + 灰度 |

---

## 2. Round 8.1 — 排行榜 keyset 分页（F-24）

**目标**：10000 人 contest 翻页（offset=9000）< 100ms。
**风险**：中（keyset 需 schema 验证 + 游标一致性）。
**工期**：0.5 人日。

### 改动

#### 2.1 缓存 key 模板扩展
- 当前：`@Cacheable(value = "contestRanking", key = "'getGlobalRanking:' + #limit")`
- 新：`@Cacheable(value = "contestRanking", key = "'getRanking:' + #contestId + ':' + #limit + ':' + #cursor")`
- 配合 R8.3 per-contest evict

#### 2.2 keyset 游标
- `selectParticipantsWithUserByContestIdKeyset(contestId, cursor, limit)` 新 mapper 方法
- cursor 编码：`(final_rank, user_id)` 复合 base64
- SQL：`WHERE contest_id=? AND (final_rank, user_id) > (?, ?) AND is_virtual=0 ORDER BY final_rank ASC, user_id ASC LIMIT ?`

#### 2.3 接口迁移
- `getContestRanking` / `getGlobalRanking` 入参新增 `cursor` 字段（可空）；非空时走 keyset，空时走 LIMIT/OFFSET
- 前端 `ranking cursor` 在 PR 中加 query param 支持

### 验收
- [ ] keyset 分页压测 10000 人 offset=9000 < 100ms
- [ ] 兼容性测试：原有 offset 接口仍能工作（双 API 路径）

### 回滚
- 旧 LIMIT/OFFSET 路径保留为 fallback；新 cursor 路径报错时回退

---

## 3. Round 8.2 — 限流 key 加 contestId 维度（F-27）

**目标**：防单用户多 contest 刷数据。
**风险**：低（`RateLimitAspect` 内部 SpEL 解析）。
**工期**：0.5 人日。

### 改动

#### 3.1 RateLimitAspect SpEL 支持
- 现状：`@RateLimit(key = "contest:virtual-start", ...)` 静态字符串
- 改造：支持 `key = "contest:virtual-start:{contestId}"`，aspect 解析路径变量 + 用户维度
- 复用：方法入参 `@PathVariable String contestId` 自动绑定

#### 3.2 应用点
- `ContestController.startVirtualContest`：`key = "contest:virtual-start:{id}"`
- `ContestController.finishVirtualContest`：`key = "contest:virtual-finish:{id}"`
- 其他 5 个 `@RateLimit` 暂不动（产品级限流，非单用户）

### 验收
- [ ] SpEL 解析：单用户同时对 2 个 contest 调 `/virtual-start` 各自独立 20/min 桶
- [ ] 兼容性：现有 `@RateLimit(key = "...:static")` 不破坏

### 回滚
- 改回静态 key 字符串即可

---

## 4. Round 8.3 — Per-contest 排行榜 evict（F-21）

**目标**：invalidation 不影响其他 contest 的缓存。
**风险**：低（已在 R7.1 留 `evictRankingCacheForContest` 占位，R8.3 真实实现）。
**工期**：0.5 人日。

### 改动

#### 4.1 evictRankingCacheForContest(contestId) 真实实现
- 缓存 key 已含 contestId（R8.1）→ 用 `cache.evict(...)` pattern 批量删
- 实现：`cacheManager.getCache(RANKING_CACHE).evict(rankingKeyPattern, contestId)`
- 评分触发：`evictRankingCacheForContest(participant.getContestId())` 替代 `evictRankingCache()`

#### 4.2 测试
- 单元测试：插入 participant A in contest 1 触发 AC，验证 contest 2 缓存不失效

### 验收
- [ ] 单元测试通过
- [ ] 集成测试：contest 1 AC 后 contest 2 排行榜仍能命中缓存

### 回滚
- 改回 `evictRankingCache()` 全量 clear；性能回到 R7 baseline

---

## 5. Round 8.4 — F-29 指数退避（WS 重连）

**目标**：网络断 5s 后 UI 显示 reconnecting，30s 后仍未连 → banner 提示。
**风险**：中（运行时行为变化；需灰度）。
**工期**：0.5 人日。

### 改动

#### 5.1 manual deactivate/activate 循环
- 替换 `reconnectDelay: options.reconnectionDelay`（静态）
- 在 `useContestSocket` 内部维护 `reconnectAttempts`，onWebSocketClose 后：
  - 计算 `delay = min(baseDelay * 2^attempts, 30_000)`（1s → 2s → 4s → 8s → 16s → 30s）
  - `setTimeout(() => client.activate(), delay)` 重连
  - 成功 onConnect 时 reset `reconnectAttempts = 0`

#### 5.2 UI 提示
- `connectionStatus === "reconnecting"` 时显示 banner："网络不稳定，正在重连..."
- `reconnectAttempts >= 5`（>30s）：banner 切到 "重连失败，请检查网络"

### 验收
- [ ] 断网 5s → UI 显示 reconnecting
- [ ] 持续断网 30s → max 触发，banner 切到重连失败
- [ ] 断网后恢复 → 重连成功，banner 消失

### 回滚
- 改回 `reconnectDelay: options.reconnectionDelay` 静态值；运行时行为回到 R6

---

## 6. Round 8.5 — CRIT-6 shadow 模式（评估）

**目标**：评估是否引入 runtime shadow 模式（双写读比 + 灰度）。
**风险**：runtime 模式 + ops 配合；引入新 flag 代价。
**工期**：0.5 人日（仅评估 + ADR 决策，不实施）。

### 改动

#### 6.1 决策（不引）
- ADR-006 §2.4 灰度策略已含 `contest.scoring.engine.v2` flag 占位
- R4 实施时采用 null 兜底（p99 行为兼容）已经提供隐式灰度
- 新增 `contest.scoring.shadow` flag **不增加额外价值**（flag 增殖 > 收益）

#### 6.2 ADR-011
- 标题："CRIT-6 shadow 模式评估结论"
- 内容：基于 ADR-006 §2.4 已有的 `engine.v2` flag 灰度策略，shadow 模式**不引**独立 flag
- 收口：F-ARCH-07 评估为"已通过 R4 隐式覆盖"

### 验收
- [ ] ADR-011 Accepted
- [ ] F-ARCH-07 CRIT-6 状态从 🟡 改为 ✅

### 回滚
- 决策类，无代码回滚

---

## 7. Round 8.6 — LOW 收口 + 文档归档（F-35~F-47）

**目标**：UX 一致性 + 文档归档。
**风险**：低（纯 i18n / 文案 / 文档）。
**工期**：1.5–2 人日。

### 改动

#### 7.1 F-35 决策补全（已在 ADR-010 落地）
- ✅ R7.5 决策类 ADR
- R8.6 行动：检查 `ContestParticipant` 字段 Javadoc 是否补全 FINISHED 边界

#### 7.2 F-36 虚拟赛元数据补全
- `VirtualContestSession` VO 新增 `startedAt`（已存在）、`isReplay`、`originalContestStatus` 字段
- 文档：`docs/contest/CONTEXT.md` "Virtual Session" 术语补全

#### 7.3 F-37 虚拟赛重放历史保留
- 现状：虚拟 session 完成后保留行（status=FINISHED）
- R8.6 行动：加 `VirtualContestHistoryVO`（按 userId 查历史重放列表），前端 `MyContests.vue` "我的虚拟赛" tab

#### 7.4 F-39 TS null vs undefined
- 现状：contest store 有 nullable ref + `loadVirtualSessionFromStorage` 严格 null 检查
- R8.6 行动：跑 `vue-tsc --noEmit --strict` 看剩余；fix 1-2 个 implicit undefined

#### 7.5 F-40/F-41 loading/空态/错误 i18n
- 现状：`i18n/locales/{en,zh}/modules/contest.ts` 已有部分 key
- R8.6 行动：补齐 `contest.*.empty` / `contest.*.error.*` / `contest.*.loading` 系列

#### 7.6 F-42 倒计时刷新
- 现状：R6.4 + R7.4 + R8.4 后已统一
- R8.6 行动：文档化"前端倒计时 = `setInterval(1s)`；服务端硬截止兜底（ADG-006 §2.2）"

#### 7.7 F-43/44/47 WS UX（已在 R7.3 落地）
- ✅ `rejected` 事件通道 + toast 提示
- R8.6 行动：补 i18n keys + 文档化

#### 7.8 F-45 文档归档
- 行动：12 项 LOW 中已落地的 6 项（F-35/43/44/47 已 ADR 或代码落地）标 ✅
- 未落地的（F-36/37/39/40/41/42/45/46）维持原状或本轮收口
- 行动：归档 `docs/contest/completed/LOW_REMAINING.md` 记录未来工作

#### 7.9 F-46 多 tab 互锁 UX
- 现状：R3.3 后端已隐式支持（`(contest, user)` 唯一 + FOR UPDATE）
- R8.6 行动：补 i18n 文案 "您已在另一个标签页开始虚拟赛"；前端 `startVirtualContest` 错误提示透出

### 验收
- [ ] i18n keys 完整（`contest.*.empty` / `contest.*.error.*` / `contest.*.loading` / `contest.*.replay.*`）
- [ ] 12 项 LOW 中 10 项 ✅ / 2 项 deferred（剩余待 R9）
- [ ] `LOW_REMAINING.md` 归档

### 回滚
- 纯 i18n / 文档，零代码回滚代价

---

## 8. 部署顺序建议

1. **R8.6**（LOW + 文档）→ 最稳，最小影响
2. **R8.2**（限流 SpEL）→ 独立改动
3. **R8.1 + R8.3**（keyset 分页 + per-contest evict）→ **同 commit**（共享 cache key 改动）
4. **R8.4**（指数退避）→ 独立 + 灰度 1 个 contest
5. **R8.5**（CRIT-6 评估）→ 决策 ADR

> **耦合集**：
> - R8.1 ↔ R8.3 共享 cache key 模板
> - R8.4 + R8.6 独立（UX 文案）

---

## 9. 验收总表

| Round | Finding | 验收命令/方法 | 估时 |
|-------|---------|--------------|------|
| R8.1 | F-24 | 10000 人 offset=9000 < 100ms + 兼容性测试 | 0.5 |
| R8.2 | F-27 | SpEL 解析 + 跨 contest 独立桶 | 0.5 |
| R8.3 | F-21 | unit test + integration test 缓存隔离 | 0.5 |
| R8.4 | F-29 | 断网 5s/30s/恢复 三态切换 | 0.5 |
| R8.5 | CRIT-6 | ADR-011 Accepted | 0.5 |
| R8.6 | LOW F-35~F-47 | i18n keys + docs 归档 | 1.5–2 |
| **合计** | **19 项** | | **4.5–5 人日** |

**R8.1–R8.6 全绿 → 模块 v4.1 完结。** 49 finding 全部关闭或显式 deferred 至 R9。

---

## 10. 与历史报告的关系

- R1-R5 见 `EXECUTION_PLAN.md`
- R6 见 `completed/EXECUTION_PLAN_R6.md`
- R7 见 `completed/EXECUTION_PLAN_R7.md`
- R8（本计划）将归档到 `completed/EXECUTION_PLAN_R8.md`
- R1-R8 累计 49 finding 全关闭，模块达到 **v4.1 完结状态**
- R9 候选（若需）= 12 项 LOW 的剩余 i18n 边缘 + cross-cutting 性能优化

> 每轮部署前：`git diff --check` + Conventional commit `<type>(contest): <desc>`。`git push` / 合并需用户显式批准。
