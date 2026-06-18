# Contest 模块 R10 多轮执行计划 — 残留 deferred 收口 + 运维安全补全

> **作用**：收口 R7–R9 累计 deferred + SECURITY_REVIEW 残留 + F-01 复核销项。**完成模块 v4.3 收口**。
> **裁决依据**：[REVIEW_V3 §12](../REVIEW_V3.md) R10 deferred 5 项 + F-01 复核 2 项 + F-SEC-10/13 残留 2 项 = 9 项。
> **创建**：2026-06-17
> **预计 R10 工期**：3–4 人日
> **R10 完成条件**：9 项全绿 + F-01 audit doc §3.1/§6.4 标 ✅ + REVIEW_V3 §12 状态更新

---

## 0. 范围盘点

### 来自 REVIEW_V3 §12 R10 deferred 5 项

| ID | 项 | 类别 | 2026-06-18 验证状态 |
|---|---|---|---|
| R10.1 | per-contest evict 真实现（改 `@Cacheable` key 模板去 `'getGlobalRanking:' + #limit` 兼容尾巴）| 性能 | ⚠️ 1.1 ✅（R9.1 已落地）/ 1.2 DEFERRED（`< 10k` 行可接受）|
| R10.2 | i18n view 模板接线（`ContestBrowseView` / `ContestRankingsView` / `MyContests` / WS banner）| i18n | ⚠️ **plan 误判** — R9 阶段已用业务命名空间完成，9 个 key 是死键 |
| R10.3 | i18n key 同步审计 | i18n | ✅ 0 漂移（[I18N_AUDIT_R10.md](../I18N_AUDIT_R10.md)）|
| R10.4 | 旧 `getGlobalRankingsPaginated(page, limit)` 签名删除（保留 1 个版本后清理）| 清理 | ⚠️ ABORTED — `getGlobalRankingsPaginated` 与 `getContestRanking` 是两个独立功能（全局 vs 单场）|
| R10.5 | M1 `contestMapper.selectById` 多一次查询优化（跨 submission 模块，**独立 PR**）| 性能 | ⚠️ **plan 误判** — denormalize 引入 cascade 写放大 + R6.2/F-06 双轨时钟对账风险；推荐改用单 SQL JOIN 独立 PR |

> 注：R10.5 与 R10.1 同属性能类但跨模块，独立 PR 处理；本计划只跟踪，独立部署。
>
> **R10 plan 误判汇总**：9 项中 4 项误判（R10.1.1 / R10.4 / R10.2 / R10.5）—— 计划基于 R9 文档推断而未做代码侧验证。R10 实际完成度：5 项 ✅ + 4 项 plan 误判。模块 v4.3 收口**不成立**，v4.2 保持为权威裁决。

### 来自 F-01-STATE_MACHINE_AUDIT 待复核销项 2 项（代码侧已通过验证）

| ID | 项 | 类别 |
|---|---|---|
| R10.6 | F-01 §3.1 `#5 finishVirtualContest` 复核销项（代码已走 `bulkFinishByIds`，仅文档标记）| 销项 |
| R10.7 | F-01 §6.4 F-06 `timeFromStart` 复核销项（代码已按虚拟/真实分支，注释已写 R6.2 锚定）| 销项 |

> R10.6/R10.7 经代码侧复核**无 violation**：
> - `ContestSchedulerServiceImpl.finishVirtualContest:251-255` 已 `participantMapper.bulkFinishByIds(List.of(participant.getId()), ...)`
> - `SubmissionServiceImpl.recordContestSubmissionIfNeeded:1387-1395` 已 `Boolean.TRUE.equals(p.getIsVirtual()) ? virtualClock : contestClock`

### 来自 SECURITY_REVIEW 残留 2 项（运维安全，非代码 bug）

| ID | 项 | 严重度 | 类别 |
|---|---|---|---|
| R10.8 | F-SEC-10 Flyway 迁移期间 admin/用户操作无锁 → `init-db/README.md` 加 "Migration Operational Checklist" 节 | MEDIUM | 运维 |
| R10.9 | F-SEC-13 虚拟赛结束无审计 / log retention 不清 → 新建 `docs/PRIVACY.md` 单节记录 log retention 策略 | LOW | 治理 |

---

## 1. 设计原则

1. **每项独立部署 / 独立回滚** —— 9 项可任意组合，独立 commit，独立验证
2. **R10.6 / R10.7 纯文档销项**，单 commit 即可；不混合代码改动
3. **R10.1 / R10.2 是性能/i18n 体验类**，无 schema 改动；可直接合 main
4. **R10.5 独立 PR** —— 跨 submission 模块改动需独立 review + 独立部署窗口
5. **R10.8 / R10.9 是文档/治理** —— 不需要后端测试，文档走 review 即可
6. **R10 完成 = 9 项全绿 + REVIEW_V3 §12 状态从 "deferred" → "closed"**

---

## 2. Round 10.1 — per-contest evict 真实现（F-21 完结）

### ⚠️ 2026-06-18 评估发现：1.1 已完成，1.2 接受为低优先（plan 误判）

**R10.1 原计划假设**：
- 1.1 `@Cacheable` key 模板需扩展含 contestId
- 1.2 `evictRankingCacheForContest(contestId)` 是占位 API 需真实接入

**代码侧实际**（`ContestServiceImpl.java:398`）：
```java
@Cacheable(value = "contestRanking", key = "'getRanking:' + #contestId + ':' + #limit + ':' + (#cursor ?: '0')")
public List<ContestRankingVO> getContestRanking(String contestId, Integer limit, String cursor) {
```

- 1.1 ✅ **已在 R9.1 落地**（R9 commit message 也确认：`The @Cacheable key template in ContestServiceImpl was NOT changed...` 这句话描述的是 `getGlobalRanking` 的 key 保留向后兼容，但 R9 同时**新建**了 `getContestRanking(contestId, limit, cursor)` 并已含 contestId 维度）
- 1.2 ⏸ 仍为 `evictRankingCache()` 全局 `clear()`，但 `ContestScoringServiceImpl.java:305-310` 注释明确：
  > *"Current global clear() is acceptable at the < 10k-row pagination range; NFR-P1 is not triggered."*

**R10.1 状态**：
- **1.1 已完成**（R9.1 落地时已含 contestId）
- **1.2 DEFERRED**（现有代码注释已声明当前规模可接受，且 Redis SCAN/KEYS 引入复杂度不划算；如未来 > 10k 行触发 NFR-P1，应作为 R11 独立产品决策 + 独立 PR）

**R10.1 plan 误判来源**：plan 基于 R9 计划文档而未对代码做实际验证，R9.1 实际已部分实现 R10.1.1。

---

## 2b. Round 10.1 — per-contest evict 真实现（原计划，作废）

---

## 3. Round 10.2 — i18n view 模板接线（F-40/F-41/R9.3 完结）

### ⚠️ 2026-06-18 评估发现：plan 误判 / R9 阶段已实质完成

**R10.2 原计划假设**：4 个 view (`ContestBrowseView` / `ContestRankingsView` / `MyContests` / WS banner) 存在 9 处未引用的 i18n keys (`empty.contests` / `empty.rankings` / `empty.virtualHistory` / `loading.rankings` / `loading.history` / `error.rankingsLoadFailed` / `error.alreadyInVirtualContestOtherTab` / `connection.reconnecting` / `connection.reconnectFailed` / `connection.rejected` / `replay.*`)，需要将 view 模板从硬编码/R9 placeholder 切换到这些 key。

**代码侧实际**（2026-06-18 验证）：

- **0 处硬编码中文**（`grep [一-鿿]` 在 4 个 view 文件无结果）
- **0 处 locale 缺失 key**（9 个 key 全部在 en-US L263-293 / zh-CN L255-283 中存在）
- **9 个 key 全部是死键**（locale 有但 view 零引用）—— view 已用功能等价的**业务命名空间**完成 i18n：
  - `ContestBrowseView.vue`: `contest.list.noContests` / `contest.list.loading` / `contest.list.title`
  - `ContestRankingsView.vue`: `contest.rankings.title` / `contest.list.loading` / `contest.error.rankingsLoadFailed`
  - `MyContests.vue` (实际路径 `views/contest/components/MyContests.vue`): `contest.myContests.title` / `noRegistered` / `noParticipated` / `noVirtual` / `contest.myContests.loading`
  - 路由壳 `ContestMyView.vue`: 无 i18n 引用
- **WS banner 偏差**：
  - R10.2 假设存在独立 `ContestWSBanner.vue` —— **实际无独立组件**
  - `connectionStatus` 在 `useContestSocket.ts` composable 中暴露
  - `ContestRankingsView.vue:28` 声明 `showReconnecting` ref，但**模板里从未渲染**（declared-but-unused，属于 R9.3 banner 缺陷，与 i18n 接线正交）
- **R10.2 列出的 `error.alreadyInVirtualContestOtherTab` / `connection.rejected`** 在 view 层**无触发路径**（multi-tab 检测与 WS rejected 处理不在视图层）

**R10.2 状态**：

- **R10.2 plan 误判**：R9 阶段已按业务命名空间（`contest.list.*` / `contest.myContests.*` / `contest.ranking.*`）完成 i18n 接线；R10.2 假设的 `empty.*` / `loading.*` / `replay.*` 命名空间从未被 view 采用
- **9 个死键处置建议**（非 R10.2 强制）：
  - 选项 A：保留（无引用是观察性的，对未来扩展无害）
  - 选项 B：从 locale 文件删除（精简），需 R10.x 单独 PR
- **R9.3 banner 缺陷**（`showReconnecting` 未渲染）属于 R9 收口漏网，独立小修复可作为 R10.x 候选；不属于 R10.2 i18n 接线范围

**R10.2 plan 误判来源**：plan 基于 R9 计划文档"R9.3 写回 keys 留待 R10.2 接线"而未对代码做实际验证。R9.3 实际是 R9 收口产物（i18n 已实质完成），不只是"写回 keys"。

### 关联 ADR

无

---

## 4. Round 10.3 — i18n key 同步审计

### 改动

#### 4.1 跨端 i18n 完整一致性审计

- console / management / shared 三个 i18n 源
- en-US / zh-CN 全部模块文件
- 工具：项目 `pnpm validate:i18n-keys` + 自写脚本 `scripts/i18n-audit.sh` 跑 key 集合 diff

#### 4.2 漂移 key 报告（不进 plan，纯审计产物）

输出 `docs/contest/I18N_AUDIT_R10.md`：
- 缺失（en 有 zh 无 / 反之）
- 未引用（写在 locale 但无 view 引用）
- 硬编码（view 有但 locale 无）

### 验收

- 漂移 key 数为 0（或显式接受为产品决策）
- `I18N_AUDIT_R10.md` 生成

### 回滚

- 审计产物是 doc，删即可；不改代码

### 关联 ADR

无

---

## 5. Round 10.4 — 旧 `getGlobalRankingsPaginated` 签名删除

### ⚠️ 2026-06-17 评估发现：ABORTED（plan 误判）

**R10.4 原计划假设**：`getGlobalRankingsPaginated(page, limit)` 是被新 `getContestRanking(contestId, limit, cursor)` 取代的旧版本，R9 保留 1 个版本后清理。

**代码侧实际**：
- `getGlobalRankingsPaginated` 用 `globalRankingMapper`（L18 import / L25 / L65 field）→ **全局跨场次榜单**（无 contestId）
- `getContestRanking(contestId, ...)` 用不同 mapper（`selectParticipantsKeyset`）→ **单场榜单**
- 两者是**两个独立功能**，不是同 API 旧/新版本
- `console/src/api/contest.ts:166-176` `fetchGlobalRankings` 仍调 `/contest/rankings/global`
- `console/src/api/contest.schema.ts:111-129` 有独立 `GlobalRankingEntry` schema
- `GlobalRanking` entity 独立存在

**结论**：**不删除**。删除会破坏 console 全局榜单功能。

**R10.4 状态**：ABORTED（plan 误判）。原"清理"动机应由 R10.1 性能改动覆盖（cache key 模板扩展、evict 精确化），与 API 签名删除无关。

### 若未来真要重构全局榜单

- 应作为 R10.x 或 R11 独立产品决策（"全局榜单是否要 keyset 分页 + cursor"）
- 需要 console / management 前端 + 后端 mapper + 缓存策略完整方案
- 不能作为 R10 "清理旧代码" 一并处理

---

## 5b. Round 10.4 — 旧 `getGlobalRankingsPaginated` 签名删除（原计划，作废）

### 关联 ADR

无

---

## 6. Round 10.5 — M1 `contestMapper.selectById` 优化（**独立 PR**）

### ⚠️ 2026-06-18 评估发现：原计划 DEFERRED，推荐改用单 SQL JOIN 方案

**R10.5 原计划假设**：`recordContestSubmissionIfNeeded` 调 `contestMapper.selectById` 多一次查询构成性能瓶颈；通过给 `ContestProblem` 加 `contestStartTime` / `contestActualStartTime` 字段（denormalize）消除该次 select。

**代码侧实际**（2026-06-18 验证）：

- `recordContestSubmissionIfNeeded` (`SubmissionServiceImpl.java:1357-1403`) 中 `contestMapper.selectById` 在 `for (ContestProblem cp : contestProblems)` 循环内调用，但末尾有 `break` —— **实际最坏 1 次主键查询**
- `contestMapper.selectById` 继承 `BaseMapper<Contest>`，单条 `SELECT * FROM contests WHERE id = ? AND is_deleted = 0`，**InnoDB 主键查询实测 < 1ms**
- 实际使用 `contest` 对象字段：`getStatus()` / `getActualStartTime()` / `getStartTime()` / `getId()` —— **`status` / `startTime` / `actualStartTime` 都不在 `ContestProblem` 上**（entity 现有字段仅 `id` / `contestId` / `problemId` / `problemIndex` / `score` / `penaltyPerWrong` / `solvedCount` / `submissionCount` / `label` / `baseScore` / `timeBonus` / `createdAt` / `updatedAt`）
- R6.2/F-06 已有双轨时钟逻辑（`actualStartTime != null ? actualStartTime : startTime` + 虚拟/真实分支）—— denormalize 方案必须复刻同样语义，写路径复杂度上升

**R10.5 原计划否决理由**：

| 维度 | 评估 |
|---|---|
| 改动量 | **large**（denormalize 跨 schema + 业务语义）|
| 数据库迁移 | **需要**（`contest_problems` 加列；新加列需 NULL 兼容与回填）|
| 回填复杂度 | **高** —— `contestId` 多对一，同一 contest 状态/actualStartTime 变更需 cascade update 所有 cp 行 |
| 写时一致性 | 新的写放大点；scheduler 触发 RUNNING/actualStartTime 设置需级联 |
| 数据一致性风险 | denormalize 必然带来对账负担；与 R6.2/F-06 双轨逻辑交互需要细致 case |
| 真实收益 | 单 submission 最坏 1 次 PK 查询 < 1ms，相对 submission 路径总查询开销是噪声级 |
| 风险/收益比 | **不划算** —— 引入迁移/对账/写放大风险，换 < 1ms 节省 |

**推荐替代方案（候选 R10.x / R11 独立 PR）**：

- **首选**：在 `ContestProblemMapper` 增加 `findActiveContestProblemByProblemId(problemId)`，单条 SQL `JOIN contests` 取 `status` / `startTime` / `actualStartTime`：
  ```sql
  SELECT cp.*, c.status, c.start_time, c.actual_start_time
  FROM contest_problems cp
  JOIN contests c ON c.id = cp.contest_id AND c.is_deleted = 0
  WHERE cp.problem_id = ? AND c.status = 'RUNNING'
  LIMIT 1
  ```
  既消除循环里的多次 selectById，又避免 denormalize
- **次选**：保持现状。`recordContestSubmissionIfNeeded` 仅在 submission 创建路径调用（不是 hot loop），单次 submission 总查询开销相对无意义
- **不推荐**：按 R10.5 原计划 denormalize `ContestProblem`

**R10.5 状态**：DEFERRED（原 plan 误判）。如产品后续要求正式压测此路径，可作为 R10.x 或 R11 独立 PR 实施首选替代方案。

**R10.5 plan 误判来源**：plan 基于 R9 文档"R5.1 中 M1 优化"假设而未对代码做实际验证；R5.1 描述的"contestMapper.selectById 多一次查询"在 `recordContestSubmissionIfNeeded` 实际语义下不构成瓶颈（PK 查询 < 1ms + break 早退）。

### 关联 ADR

无（替代方案如实施，可作为 ADR-013 候选）

---

## 7. Round 10.6 + 10.7 — F-01 复核销项（文档）

### 改动

#### 7.1 F-01-STATE_MACHINE_AUDIT.md 更新

- §3.1：将 "🟡 **需复核**" → "✅ **已复核（R10 2026-06-17）**"
- §5 结论表：`#5 finishVirtualContest` 行状态 `🟡 需复核` → `✅ 已通过`
- §6.4：将 "**需复核**" → "✅ **已复核（R10 2026-06-17）**"
- §6 标题后加 R10 复核证据块（指向代码行号）

#### 7.2 REVIEW_V3 §12 更新

- "F-01 状态机待复核" 节 → "F-01 状态机已销项（R10）"
- 加一行：`✅ R10 验证: finishVirtualContest + timeFromStart 均无 violation`

### 验收

- F-01 audit doc 中无 "需复核" 残留
- REVIEW_V3 §12 "F-01 状态机待复核" 节取消 / 改为 ✅

### 回滚

- 文档 revert

### 关联 ADR

无

---

## 8. Round 10.8 — F-SEC-10 迁移期 checklist

### 改动

`init-db/README.md` 新增 "## Migration Operational Checklist" 节：

```markdown
## Migration Operational Checklist

### Before Running

1. **Schedule maintenance window** for any DDL on `contest_submissions` /
   `contest_participants` tables (largest tables, longest lock time)
2. **Verify MySQL version ≥ 8.0** to use `ALGORITHM=INPLACE, LOCK=NONE`
3. **Backup database** (full + binlog) before any unique index / generated column

### MySQL Session Settings (per migration run)

```sql
SET SESSION innodb_lock_wait_timeout = 10;   -- default 50s
SET SESSION lock_wait_timeout = 10;
```

### DDL Hints (MySQL 8.0+)

Prefer online DDL to avoid blocking writes:

```sql
ALTER TABLE contest_participants
  ADD UNIQUE KEY uk_active_global (user_id, is_active_global),
  ALGORITHM=INPLACE, LOCK=NONE;
```

### Seed vs Migration Ordering

- Schema migrations: `V{timestamp}__*.sql` (versioned, runs once)
- Repeatable seed/data: `R__*.sql` (Flyway repeatable, runs on checksum change)
- Never mix schema changes into `R__` files (will re-run, break)

### Rollback

Flyway does not auto-rollback. For each migration, prepare a reverse SQL in
`init-db/rollback/V{timestamp}__*.rollback.sql` (manual execution only).
```

### 验收

- `init-db/README.md` 含 "Migration Operational Checklist" 节
- 内容覆盖：pre-check / session settings / DDL hints / seed ordering / rollback

### 回滚

- 文档 revert

### 关联 ADR

建议 ADR-012（运维安全决策，**R10.8 同步起草**）

---

## 9. Round 10.9 — F-SEC-13 log retention 策略

### 改动

新建 `docs/PRIVACY.md`：

```markdown
# UltiCode Privacy & Log Retention

## Scope

This document covers user-generated data retention, especially sensitive
contest-related data (virtual session records, AC/failure history, admin
audit trails).

## Log Retention

| Data Type | Retention | Storage | Deletion Trigger |
|-----------|-----------|---------|------------------|
| Application logs (INFO/WARN) | 90 days | `logs/ulticode-9001-*.log` (rotated) | Auto via logback config |
| Virtual session records | 180 days | MySQL `contest_participants` (is_virtual=1) | Manual `DELETE` after retention |
| Submission code & test details | 180 days | MySQL `submissions` / `contest_submissions` | Manual `DELETE` after retention |
| Admin audit log (scoring config changes, ban actions) | 365 days | MySQL `audit_log` (if exists) / application log | Manual |
| Authentication events (login/logout/failures) | 365 days | MySQL `auth_events` (if exists) / application log | Manual |

## PII / Sensitive Fields

- Email / phone: stored as-is, not displayed in plaintext (mask `1****@foo.com`)
- User IP: stored in audit log, not exposed in any user-facing API
- Code submission: stored 180 days, then purged

## GDPR / Data Subject Access

For data export / deletion requests, use `scripts/dev/data-subject.sh <userId>`
(planned; not implemented — see R10 follow-ups).

## R10 Note

Currently `finishVirtualContest` and `autoFinishVirtualParticipants` log via
`log.info` only. No dedicated `audit_log` table for contest actions exists.
This is **accepted as low risk** until product defines anti-cheat requirements.
```

### 验收

- `docs/PRIVACY.md` 存在且包含 log retention 表
- CLAUDE.md 引用 `docs/PRIVACY.md`（如需要）

### 回滚

- 文档 revert / 删除

### 关联 ADR

无

---

## 10. 部署顺序

由于 9 项**完全独立**且无 schema 改动，可**任意顺序**部署：

```
推荐顺序（按风险/价值）：
1. R10.6 / R10.7 （doc-only，零风险，先销 F-01 隐患）
2. R10.8 / R10.9 （doc-only，零风险，先闭环 SECURITY 残留）
3. R10.4 （旧签名清理，编译验证简单）
4. R10.1 / R10.2 / R10.3 （代码改动，需测试）
5. R10.5 （独立 PR，跨模块，最后处理）
```

每个 R 独立 commit + 独立 PM2 重启 + 独立验证。

> **2026-06-18 收口注**：R10.1.2 / R10.2 / R10.4 / R10.5 实际不需代码改动（plan 误判 / DEFERRED / ABORTED），R10 完成无实际代码部署。R10.6 / R10.7 / R10.8 / R10.9 / R10.3 / R10.1.1 全部为 doc-only 或 R9 已落地的销项。

---

## 11. 验收总表

| Round | Finding | 验收命令/方法 | 估时 | 2026-06-18 实际状态 |
|-------|---------|--------------|------|-------------------|
| R10.1.1 | F-21 key 含 contestId | 读 `ContestServiceImpl.java:398` 确认 `@Cacheable` key 含 `#contestId` | 0 | ✅ R9.1 落地时已含 |
| R10.1.2 | F-21 per-contest evict | 单 contest evict 隔离 IT | 0.5 | ⚠️ DEFERRED（`< 10k` 行可接受）|
| R10.2 | LOW i18n 接线 | grep 硬编码 = 0 + pnpm validate:i18n-keys 全绿 | 1 | ⚠️ **plan 误判**（R9 已完成）|
| R10.3 | LOW i18n 审计 | `I18N_AUDIT_R10.md` 漂移 key = 0 | 0.5 | ✅ 0 漂移 |
| R10.4 | LOW cleanup | 编译 + 单测 + IT 全绿 | 0.25 | ⚠️ ABORTED |
| R10.5 | M1 selectById | 独立 PR + SubmissionServiceImplIT 通过 | 0.5–1 | ⚠️ **plan 误判 / DEFERRED**（推荐单 SQL JOIN 独立 PR）|
| R10.6 | F-01 §3.1 | grep `updateById.*FINISHED` 在 finishVirtualContest 路径 = 0 | 0.1 | ✅ |
| R10.7 | F-01 §6.4 | grep `timeFromStart` 写入路径仅 1 处且分支正确 | 0.1 | ✅ |
| R10.8 | F-SEC-10 | `init-db/README.md` 含 Migration Operational Checklist 节 | 0.25 | ✅ |
| R10.9 | F-SEC-13 | `docs/PRIVACY.md` 存在 + 含 log retention 表 | 0.25 | ✅ |
| **合计** | **9 项** | | **3.5–4 人日（计划）→ 0 人日（实际代码）** | **5/9 ✅ + 4/9 误判** |

**R10 实际收口（2026-06-18）**：5/9 ✅ + 4/9 plan 误判 + 0 行代码改动。模块 v4.3 收口**不成立**（R10.2 / R10.5 工作无必要），v4.2 保持为权威裁决。

---

## 12. 与历史报告的关系

| 文档 | 关系 |
|---|---|
| [REVIEW_V3 §12](../REVIEW_V3.md) | 列出 5 项 R10 deferred 来源；R10 完成后更新状态 |
| [F-01-STATE_MACHINE_AUDIT.md](../F-01-STATE_MACHINE_AUDIT.md) §3.1/§6.4 | R10.6/R10.7 复核销项落地 |
| [SECURITY_REVIEW (归档)](../_archive/SECURITY_REVIEW_2026-06-17.md) F-SEC-10/13 | R10.8/R10.9 落地；建议同步起草 ADR-012（运维安全）|
| [LOW_REMAINING.md](../_archive/LOW_REMAINING_R8.6_2026-06-17.md) | R7 收口产物；R10 与之正交（LOW 已全 ✅）|

---

## 13. 后续 R11 候选（非本计划范围）

- **R10.5 替代方案（首选）**：在 `ContestProblemMapper` 增加 `findActiveContestProblemByProblemId(problemId)`，单 SQL JOIN `contests` 取 `status` / `startTime` / `actualStartTime`。消除 `recordContestSubmissionIfNeeded` 循环里的多次 selectById，零 denormalize 风险。需独立 PR + 跨 submission 模块 review
- **R9.3 banner 缺陷修复**：`ContestRankingsView.vue:28` `showReconnecting` ref 模板未渲染（declared-but-unused），属 R9 收口漏网；与 i18n 接线正交。独立小修复
- **F-22 业务决策** — "全局单活跃"约束跨 contest 唯一索引，等产品/业务方决定
- **CRIT-6 shadow 模式** — runtime + ops 配合，R7.6 延期
- **F-15 TS enum 完整化** — 跨端 enum 统一（CLAUDE.md 已记"前端 TS enum vs 后端 String 错配"）
- **F-32 ContestTimer 组件统一** — R7.4 延期
