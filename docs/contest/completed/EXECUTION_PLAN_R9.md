# Contest 模块 R9 多轮执行计划 — 性能缓存收口 + i18n 接入

> **作用**：R8 显式 deferred 的 4 项 + R8 review fix 衍生。完成模块 v4.2 收口。
> **裁决依据**：R8 review HIGH-1/HIGH-2/LOW-1 deferred + `LOW_REMAINING.md` 剩余 2 项。
> **创建**：2026-06-17
> **预计 R9 工期**：2.5–3.5 人日
> **R9 完成条件**：cache key 模板扩到含 contestId + keyset 分页重落地 + i18n 接入 + multi-tab 检测

---

## 0. 范围盘点

| # | Finding | 来源 | 估时 |
|---|---------|------|------|
| 1 | **F-24 keyset 分页重设计** | R8 review HIGH-1 (deleted) | 1 人日 |
| 2 | **per-contest 排行榜 evict** | R8 review HIGH-2 (deleted) | 0.5 人日 |
| 3 | **i18n 接入**（R9_PLACEHOLDER.ts 5 个 block） | R8 review MED-2 | 0.5–1 人日 |
| 4 | **F-46 multi-tab 检测**（localStorage 跨标签广播） | R8 plan 6.4 | 1 人日 |

R9 共 **4 项**。

---

## 1. 设计原则

| 原则 | 含义 |
|------|------|
| **R9.1 + R9.2 同 commit** | cache key 模板改动是 R9.1/R9.2 共享点 |
| **R9.3 / R9.4 独立** | i18n / multi-tab 与缓存无耦合 |
| **风险隔离** | R9.1 keyset 涉及 SQL 行为变化，R9.3 / R9.4 纯前端 |
| **每轮可独立回滚** | — |

---

## 2. Round 9.1 — 排行榜 keyset 分页（F-24 重设计）

**目标**：10000 人 contest 翻页（offset=9000）< 100ms；首行返回正确。
**风险**：中（SQL 行为变化 + cursor 协议）。
**工期**：1 人日。

### 改动

#### 2.1 缓存 key 模板扩展
- 当前：`@Cacheable(value = "contestRanking", key = "'getGlobalRanking:' + #limit")` / `key = "'globalPaginated:' + #page + ':' + #limit"`
- 新：`key = "'getRanking:' + #contestId + ':' + #limit + ':' + (#cursor ?: '0')"`
- 配合 R9.2 per-contest evict

#### 2.2 keyset 游标（重设计）
- `selectParticipantsKeyset(contestId, afterRank, afterUserId, limit)` mapper 方法
- **首行处理**：使用 MyBatis `<choose>` + `<when test="afterRank == null">`：
  ```xml
  <choose>
    <when test="afterRank == null">
      -- 首行：按 (final_rank ASC, user_id ASC) 直接取
    </when>
    <otherwise>
      AND (cp.final_rank > #{afterRank}
           OR (cp.final_rank = #{afterRank} AND cp.user_id > #{afterUserId}))
    </otherwise>
  </choose>
  ```
- cursor 协议：base64(`{finalRank}:{userId}`)；首页 cursor=null
- 同提交：cursor 越界（userId 不存在）→ 业务层 fallback 返回空 page

#### 2.3 接口迁移
- `getContestRanking(contestId, page, limit)` 入参新增 `cursor`（可空，向后兼容）
- 前端：cursor 透传（page param → cursor 转换）
- 旧 `getGlobalRankingsPaginated(page, limit)` 保留 1 个版本，R10 删除

### 验收
- [ ] keyset 10000 人 offset=9000 < 100ms（JMH）
- [ ] 首行 cursor=null 返回正确（之前 R8 的 bug 不能复现）
- [ ] 兼容性测试：旧 page 路径仍能工作

### 回滚
- cursor null 时回退 LIMIT/OFFSET；feature flag 控制

---

## 3. Round 9.2 — Per-contest 排行榜 evict（F-21 真正实现）

**目标**：invalidation 不影响其他 contest 的缓存。
**风险**：低（cache key 已在 R9.1 扩到含 contestId）。
**工期**：0.5 人日。

### 改动

#### 3.1 evictRankingCacheForContest 真正实现
- R9.1 后 key 模板含 contestId → 可枚举 `cache.keys()` 找匹配项
- 实现：
  ```java
  for (Object key : cache.keys()) {
    if (key.toString().contains(":" + contestId + ":")) {
      cache.evict(key);
    }
  }
  ```
- 性能：cache.keys() O(n)；单次 evict O(1)；总复杂度 O(contest 数量 × cache key 数)；**NFR-P1 触发场景下可接受**

#### 3.2 接口签名
- public method（之前 R7 留的 private）— controller 也可手动调

### 验收
- [ ] 单元测试：contest 1 AC 后 contest 2 缓存键不失效
- [ ] 集成测试：100 AC / contest → 1 cache key 失效，9 个不失效

### 回滚
- 改回 global `clear()`

---

## 4. Round 9.3 — i18n 接入（R9_PLACEHOLDER.ts → view 模板）

**目标**：5 个 i18n block 真正被 view 引用。
**风险**：低（纯 i18n）。
**工期**：0.5–1 人日。

### 改动

#### 4.1 把 R9_PLACEHOLDER keys 写回 contest.ts（en-US + zh-CN）

- `empty.contests` → `ContestBrowseView.vue` "暂无比赛" 状态
- `empty.rankings` → `ContestRankingsView.vue` "暂无排名" 状态
- `empty.virtualHistory` → 新 `MyContests.vue` "我的虚拟赛" tab
- `loading.rankings` / `loading.history` → 对应列表骨架屏
- `error.rankingsLoadFailed` → ranking 错误 toast
- `error.alreadyInVirtualContestOtherTab` → R9.4 multi-tab 检测
- `connection.reconnecting` / `connection.reconnectFailed` → WS banner
- `connection.rejected` → R7.3 rejected 事件 toast
- `replay.*` → 虚拟赛历史 tab

#### 4.2 i18n key 同步审计
- grep 所有 `t('contest.*')` 引用；确保新增 keys 都被使用
- 删 R9_PLACEHOLDER.ts

### 验收
- [ ] grep `contest.empty/loading/error/connection/replay` 全部有引用
- [ ] en-US + zh-CN 双语对齐（key 数量一致）
- [ ] R9_PLACEHOLDER.ts 删除

### 回滚
- 纯 i18n，零代码回滚

---

## 5. Round 9.4 — F-46 multi-tab 检测（localStorage 跨标签广播）

**目标**：同一用户多 tab 报同一个虚拟赛 → 第二个 tab 收到提示。
**风险**：低（仅前端 + localStorage 事件）。
**工期**：1 人日。

### 改动

#### 5.1 localStorage 事件广播
- 启动虚拟赛时：`localStorage.setItem('ulticode:virtual:active', JSON.stringify({contestId, userId, ts}))`
- 关闭虚拟赛时：`removeItem`
- 监听 `window.addEventListener('storage', ...)` 捕获其他 tab 的变更

#### 5.2 检测逻辑
- 启动虚拟赛前：
  1. 读 `ulticode:virtual:active` → 若存在同 userId + contestId
  2. 比较 `ts` 与 `now`（> 30s 视为 stale，不算冲突）
  3. 命中冲突 → 显示 i18n `error.alreadyInVirtualContestOtherTab` toast，**不** 调 `/virtual/start`
  4. 未命中 → 调 `/virtual/start`，成功后 setItem

#### 5.3 边缘
- 浏览器关闭 / 崩溃 → stale 30s 后释放（无需 heartbeat）
- 后端 R3.3 FOR UPDATE 是最后一道防线（前端检测是 UX 优化）

### 验收
- [ ] tab 1 启动虚拟赛，tab 2 启动同一竞赛 → tab 2 显示 toast
- [ ] tab 1 关闭后，tab 2 重新启动 → 成功
- [ ] 30s 后 tab 2 重新启动 → 成功（stale 释放）

### 回滚
- 注释掉 localStorage 读写；行为回到 R3 (单 tab OK，多 tab 不检测)

---

## 6. 部署顺序

1. **R9.3**（i18n）→ 最稳，先做
2. **R9.1 + R9.2**（keyset + per-contest evict）→ **同 commit**（共享 cache key 改动）
3. **R9.4**（multi-tab）→ 独立

> **耦合集**：R9.1 + R9.2 共享 cache key 模板；R9.3 / R9.4 独立

---

## 7. 验收总表

| Round | Finding | 验收命令/方法 | 估时 |
|-------|---------|--------------|------|
| R9.1 | F-24 | keyset offset=9000 < 100ms + 首行正确 | 1 |
| R9.2 | F-21 | 单 contest evict 隔离测试 | 0.5 |
| R9.3 | LOW i18n | grep `contest.empty/loading/error/connection/replay` 全有引用 | 0.5–1 |
| R9.4 | F-46 | 双 tab 冲突 toast + 30s stale 释放 | 1 |
| **合计** | **4 项** | | **3–3.5 人日** |

**R9.1–R9.4 全绿 → 模块 v4.2 完结。** 所有 PRD §1.3 finding + 12 LOW + R8 review deferred 全部关闭。

---

## 8. 与历史报告的关系

- R1-R5 见 `EXECUTION_PLAN.md`
- R6 见 `completed/EXECUTION_PLAN_R6.md`
- R7 见 `completed/EXECUTION_PLAN_R7.md`
- R8 见 `completed/EXECUTION_PLAN_R8.md`
- R9（本计划）将归档到 `completed/EXECUTION_PLAN_R9.md`
- R9 完成后：模块达到 **v4.2 完结状态**
- 累计 49 finding + 12 LOW + R8 review fixups 全部关闭

> 每轮部署前：`git diff --check` + Conventional commit `<type>(contest): <desc>`。`git push` / 合并需用户显式批准。
